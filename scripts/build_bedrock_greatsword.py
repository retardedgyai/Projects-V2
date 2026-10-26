"""The greatsword's first-person swing for Bedrock players (through Geyser), without shaders (2026-10-25, Creator:
「シェーダーじゃない統合版でも動く方で作ってみてくれん」「試せなくていいから、プレビューで合わせられる所まで作っておいて」).

Bedrock draws a held item with an attachable: a model and animations of its own, played by the client every frame,
so the swing is as smooth as the Java shader's. This writes a Bedrock resource pack and a Geyser mapping:

- one attachable per sword tier (T2..T4) at rest, and per beat while swinging. The server already marks the held
  sword while it swings (custom_model_data 120 + beat, CoreArmamentPresentation.shaderSwing); Geyser maps each mark to
  its own Bedrock item, so each beat is a new attachable whose animation starts from 0 when it appears.
- the first-person sword and arms hang from the body bone. In first person Bedrock turns the body with the look
  (animation.player.first_person.base_pose), so the body's space is the camera's: the keys the Java shader plays
  (class_armament_swing.shader_keys / arm_keys, in Minecraft view space) go in nearly as they are.
- in third person the same sword hangs from the right arm, placed by the Java third-person display.

Coordinates follow Blockbench (what the game shows): "internal" space is right-handed, y up, the player facing -z
with the right arm on +x, which is Minecraft's view space in pixels. A Bedrock file stores x mirrored: positions and
pivots with x negated, rotations as (-x, -y, z) of an internal Z·Y·X euler.

Not checked on a device (there is none yet). What a device must confirm is collected in ASSUMED below.

    python3 scripts/build_bedrock_greatsword.py            # writes server-minestom/build/bedrock (pack, mappings)
    python3 scripts/build_bedrock_greatsword.py --preview X  # Java and Bedrock first person side by side, frame by frame
"""
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np

import class_armament_swing as S

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
OUT = ROOT / 'server-minestom/build/bedrock'
TIERS = (2, 3, 4)
JAVA_ITEMS = {1: 'minecraft:stone_sword', 2: 'minecraft:iron_sword', 3: 'minecraft:diamond_sword', 4: 'minecraft:netherite_sword'}
LENGTHS = {1: 20, 2: 22, 3: 30}   # the beats' swing lengths in ticks at attack speed 1 (CoreWeaponAttacks)
LENGTHS.update({S.CHARGE_BEAT: S.CHARGE_TICKS, S.IMPACT_BEAT: S.IMPACT_TICKS})   # 体当たり's charge and impact, fixed lengths
ANIM_POSE = S.ANIM_POSE           # custom_model_data 120 + beat while swinging
FPS = 40                          # keys a second (more where the bone turns fast, see MAX_STEP)
MAX_STEP = 6.0                    # degrees a key may turn from the last

# What a device must confirm (Bedrock's first-person camera is not in the samples; these follow Blockbench's guides):
ASSUMED = {
    'entity_scale': .9375,        # player.entity "scale": the attachable, hung on the skeleton, is drawn at it too
    'eye_px': 1.62 / .9375 * 16,  # the camera above the feet, in model pixels
    'body_is_camera': True,       # first person: the body bone turns with the look, so its space is the camera's
}
BODY_PIVOT = (0.0, 24.0, 0.0)
RIGHT_ARM_PIVOT = (5.0, 22.0, 0.0)   # internal (the file says -5)
SLEEVE_ROWS = 16                     # texture rows added under the sword atlas for the arms


# ---------------------------------------------------------------- conventions
def Rx(a): c, s = math.cos(a), math.sin(a); return np.array(((1, 0, 0), (0, c, -s), (0, s, c)))
def Ry(a): c, s = math.cos(a), math.sin(a); return np.array(((c, 0, s), (0, 1, 0), (-s, 0, c)))
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array(((c, -s, 0), (s, c, 0), (0, 0, 1)))


def euler_zyx(m):
    """Internal euler (x, y, z) degrees with m = Rz Ry Rx."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(math.cos(y)) > 1e-6:
        x, z = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:
        x, z = math.atan2(-m[1][2], m[1][1]), 0.0
    return np.degrees((x, y, z))


def matrix_zyx(e):
    x, y, z = np.radians(e)
    return Rz(z) @ Ry(y) @ Rx(x)


def file_rot(e): return [round(-e[0], 4), round(-e[1], 4), round(e[2], 4)]
def file_pos(p): return [round(-p[0], 4), round(p[1], 4), round(p[2], 4)]
def internal_rot(f): return np.array((-f[0], -f[1], f[2]))
def internal_pos(f): return np.array((-f[0], f[1], f[2]))


# ---------------------------------------------------------------- geometry
def java_model(tier):
    return json.loads((PACK / f'models/item/weapons/greatsword_t{tier}.json').read_text())


def element_rotation(e):
    r = e.get('rotation')
    if not r: return np.eye(3), np.zeros(3)
    a = math.radians(r['angle'])
    return {'x': Rx, 'y': Ry, 'z': Rz}[r['axis']](a), np.array(r['origin'], float)


FACE_NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
MIRRORED_FACE = {'east': 'west', 'west': 'east'}


def cubes(model, place, scale):
    """Bedrock cubes for a Java model's elements, each placed by [place] (3x4: Java model pixels to internal pixels,
    a rotation times [scale] and a shift). A cube keeps its box in its own axes and turns about its rotation origin."""
    rot, shift = place[:, :3] / scale, place[:, 3]
    out = []
    tex_w = 64.0
    for e in model['elements']:
        re, origin = element_rotation(e)
        if not e.get('rotation'): origin = (np.array(e['from']) + np.array(e['to'])) / 2
        pivot = rot @ origin * scale + shift
        lo = pivot + (np.array(e['from']) - origin) * scale
        hi = pivot + (np.array(e['to']) - origin) * scale
        uv = {}
        for name, face in e['faces'].items():
            u0, v0, u1, v1 = (c * tex_w / 16 for c in face.get('uv', (0, 0, 16, 16)))
            # The file mirrors x: the east and west faces swap, and faces across x read their texture the other way.
            fname = MIRRORED_FACE.get(name, name)
            if name in ('north', 'south', 'up', 'down'): u0, u1 = u1, u0
            uv[fname] = {'uv': [round(u0, 4), round(v0, 4)], 'uv_size': [round(u1 - u0, 4), round(v1 - v0, 4)]}
        flo = file_pos(lo); fhi = file_pos(hi)
        out.append({'origin': [round(min(flo[i], fhi[i]), 4) for i in range(3)],
                    'size': [round(abs(fhi[i] - flo[i]), 4) for i in range(3)],
                    'pivot': file_pos(pivot), 'rotation': file_rot(euler_zyx(rot @ re)), 'uv': uv})
    return out


def arm_cubes(lo, hi, eye):
    """An arm as the Java swing draws it: its own pixel space, shoulder at the origin (y down to the hand), the
    shoulder sitting at the eye until the animation moves it. Sleeve over the arm, the hand at the far end."""
    a, b = eye + np.array(lo, float), eye + np.array(hi, float)
    flo, fhi = file_pos(a), file_pos(b)
    side = {'uv': [0, 64], 'uv_size': [4, 12]}
    return [{'origin': [min(flo[i], fhi[i]) for i in range(3)], 'size': [abs(fhi[i] - flo[i]) for i in range(3)],
             'uv': {'north': side, 'south': side, 'east': side, 'west': side,
                    'up': {'uv': [4, 64], 'uv_size': [4, 4]}, 'down': {'uv': [4, 68], 'uv_size': [4, 4]}}}]


def third_person_place(tier):
    """Java model pixels to internal pixels in the right arm's space (ItemInHandLayer, the third-person display)."""
    import bc_firstperson as B
    disp = java_model(tier)['display']['thirdperson_righthand']
    t = [v / 16 for v in disp['translation']]
    r = [math.radians(v) for v in disp['rotation']]
    s = disp['scale'][0]
    hand = B.Rx(-math.pi / 2) @ B.Ry(math.pi) @ B.T(1 / 16, .125, -.625)
    m = hand @ B.T(*t) @ B.Rx(r[0]) @ B.Ry(r[1]) @ B.Rz(r[2]) @ B.S(s, s, s) @ B.T(-.5, -.5, -.5) @ B.S(1 / 16, 1 / 16, 1 / 16)
    # arm space (Java model, blocks, y down) to internal pixels about the arm pivot: (x, y, z) -> (-x, -y, z) x 16
    flip = np.diag((-16.0, -16.0, 16.0))
    lin = flip @ m[:3, :3]
    return np.column_stack((lin, flip @ m[:3, 3] + np.array(RIGHT_ARM_PIVOT))), s


def geometry(tier):
    model = java_model(tier)
    eye = np.array((0.0, ASSUMED['eye_px'], 0.0))
    grip = np.array(S.MODELS[f'greatsword_t{tier}'][1])
    fp_place = np.column_stack((np.eye(3), eye - grip))
    tp_place, tp_scale = third_person_place(tier)
    bones = [
        {'name': 'body', 'pivot': file_pos(BODY_PIVOT)},
        {'name': 'projects_fp', 'parent': 'body', 'pivot': file_pos(eye)},
        {'name': 'fp_sword', 'parent': 'projects_fp', 'pivot': file_pos(eye), 'cubes': cubes(model, fp_place, 1.0)},
        {'name': 'fp_arm_r', 'parent': 'projects_fp', 'pivot': file_pos(eye), 'cubes': arm_cubes(*S.ARM_BOXES['rightArm'], eye)},
        {'name': 'fp_arm_l', 'parent': 'projects_fp', 'pivot': file_pos(eye), 'cubes': arm_cubes(*S.ARM_BOXES['leftArm'], eye)},
        {'name': 'rightArm', 'pivot': file_pos(RIGHT_ARM_PIVOT)},
        {'name': 'tp_sword', 'parent': 'rightArm', 'pivot': file_pos(RIGHT_ARM_PIVOT), 'cubes': cubes(model, tp_place, tp_scale)},
    ]
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.projects.greatsword_t{tier}', 'texture_width': 64, 'texture_height': 64 + SLEEVE_ROWS,
                        'visible_bounds_width': 6, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


# ---------------------------------------------------------------- animations
def sample(keys, u):
    import preview_greatsword_fp as P
    h, q, s = P.shader_pose(keys, u)
    return h, P._qmat(q), s


def bone_track(keys, length_s, scale_of, origin):
    """Bedrock keyframes (seconds) for one bone from Java swing keys: position (the pivot's move, internal pixels to
    file), rotation (unwrapped so a linear blend never takes the long way round), scale."""
    k = 16 / ASSUMED['entity_scale']
    n = max(1, int(round(length_s * FPS)))
    # Where the bone turns fast, keys are added until no step turns more than MAX_STEP degrees: Bedrock blends the
    # euler angles one by one, which strays from the shader's slerp over a big step.
    times = [0.0]
    for i in range(1, n + 1):
        a, b = (i - 1) / n, i / n
        _, ma, _ = sample(keys, a); _, mb, _ = sample(keys, b)
        turn = math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(ma.T @ mb) - 1) / 2))))
        parts = max(1, math.ceil(turn / MAX_STEP))
        times += [a + (b - a) * j / parts for j in range(1, parts + 1)]
    pos, rot, scl, last = {}, {}, {}, None
    for u in times:
        h, m, s = sample(keys, u)
        e = euler_zyx(m)
        if last is not None: e = last + (e - last + 180) % 360 - 180
        last = e
        t = f'{round(u * length_s, 5)}'
        pos[t] = file_pos(h * k)
        rot[t] = file_rot(e)
        sc = round(scale_of(s), 5)
        scl[t] = [sc, sc, sc]
    return {'position': pos, 'rotation': rot, 'scale': scl}


def animations(tier):
    name = f'greatsword_t{tier}'
    out = {}
    grow = lambda s: s / ASSUMED['entity_scale']
    for beat in S.SHADER_BEATS:
        L = LENGTHS[beat] / 20
        out[f'animation.projects.{name}.swing_{beat}'] = {'loop': 'hold_on_last_frame', 'animation_length': L, 'bones': {
            'fp_sword': bone_track(S.shader_keys(name, beat), L, grow, None),
            'fp_arm_r': bone_track(S.arm_keys('rightArm', beat), L, grow, None),
            'fp_arm_l': bone_track(S.arm_keys('leftArm', beat), L, grow, None)}}
    rest = S.shader_keys(name, 1)[:1] * 2
    k = 16 / ASSUMED['entity_scale']
    h, m, s = sample(rest, 0.0)
    out[f'animation.projects.{name}.rest'] = {'loop': True, 'bones': {
        'fp_sword': {'position': file_pos(h * k), 'rotation': file_rot(euler_zyx(m)), 'scale': grow(s)},
        'fp_arm_r': {'scale': 0}, 'fp_arm_l': {'scale': 0}}}
    return out


# ---------------------------------------------------------------- pack and mapping
def identifier(tier, beat=None):
    return f'projects:greatsword_t{tier}' + (f'_swing_{beat}' if beat else '')


def attachable(tier, beat=None):
    name = f'greatsword_t{tier}'
    anim = f'animation.projects.{name}.' + (f'swing_{beat}' if beat else 'rest')
    return {'format_version': '1.10.0', 'minecraft:attachable': {'description': {
        'identifier': identifier(tier, beat),
        'materials': {'default': 'entity_alphatest', 'enchanted': 'entity_alphatest_glint'},
        'textures': {'default': f'textures/projects/{name}', 'enchanted': 'textures/misc/enchanted_item_glint'},
        'geometry': {'default': f'geometry.projects.{name}'},
        'animations': {'first_person': anim},
        'scripts': {'animate': [{'first_person': 'c.is_first_person'}]},
        'render_controllers': ['controller.render.projects.greatsword']}}}


RENDER_CONTROLLER = {'format_version': '1.10.0', 'render_controllers': {'controller.render.projects.greatsword': {
    'geometry': 'Geometry.default', 'materials': [{'*': 'Material.default'}], 'textures': ['Texture.default'],
    # The first-person bones only in first person, the hand-held one only in third person.
    'part_visibility': [{'projects_fp': 'c.is_first_person'}, {'rightArm': '!c.is_first_person'}]}}}


def texture(tier):
    """The sword atlas (64 x 64, all used) with the sleeve and hand added below it (the arms' cubes read it there)."""
    from PIL import Image
    atlas = Image.open(PACK / 'textures/item/weapons/materials.png').convert('RGBA')
    sleeve = Image.open(PACK / 'textures/item/weapons/fp_sleeve.png').convert('RGBA')
    out = Image.new('RGBA', (64, 64 + SLEEVE_ROWS), (0, 0, 0, 0))
    out.paste(atlas, (0, 0))
    out.paste(sleeve.crop((0, 0, 8, 12)), (0, 64))
    return out


def mapping():
    items = {}
    for tier in TIERS:
        model = f'projects:weapons/greatsword_t{tier}'
        defs = [{'type': 'definition', 'model': model, 'bedrock_identifier': identifier(tier), 'display_name': '大剣',
                 'bedrock_options': {'icon': f'projects_greatsword_t{tier}', 'allow_offhand': False, 'display_handheld': True}}]
        for beat in S.SHADER_BEATS:
            defs.append({'type': 'definition', 'model': model, 'bedrock_identifier': identifier(tier, beat),
                         'display_name': '大剣', 'priority': beat,
                         'bedrock_options': {'icon': f'projects_greatsword_t{tier}', 'allow_offhand': False, 'display_handheld': True},
                         'predicate': {'type': 'range_dispatch', 'property': 'custom_model_data', 'index': 0,
                                       'threshold': ANIM_POSE + beat}})
        items.setdefault(JAVA_ITEMS[tier], []).extend(defs)
    return {'format_version': 2, 'items': items}


def build():
    shutil.rmtree(OUT, ignore_errors=True)
    rp = OUT / 'pack'
    def write(rel, value):
        p = rp / rel; p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(value, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    write('manifest.json', {'format_version': 2, 'header': {'name': 'ProjectS greatsword', 'description': 'ProjectS',
        'uuid': '6c1f0e0a-3b0d-4c8e-9a7e-1f2b3c4d5e60', 'version': [1, 0, 0], 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': '6c1f0e0a-3b0d-4c8e-9a7e-1f2b3c4d5e61', 'version': [1, 0, 0]}]})
    write('render_controllers/projects_greatsword.render_controllers.json', RENDER_CONTROLLER)
    icons = {}
    for tier in TIERS:
        name = f'greatsword_t{tier}'
        write(f'models/entity/projects_{name}.geo.json', geometry(tier))
        write(f'animations/projects_{name}.animation.json', {'format_version': '1.8.0', 'animations': animations(tier)})
        write(f'attachables/projects_{name}.json', attachable(tier))
        for beat in S.SHADER_BEATS: write(f'attachables/projects_{name}_swing_{beat}.json', attachable(tier, beat))
        (rp / 'textures/projects').mkdir(parents=True, exist_ok=True)
        texture(tier).save(rp / f'textures/projects/{name}.png')
        icons[f'projects_{name}'] = {'textures': 'textures/projects/greatsword_icon'}
    shutil.copy(PACK / 'textures/item/weapons/plain/plain_greatsword.png', rp / 'textures/projects/greatsword_icon.png')
    write('textures/item_texture.json', {'resource_pack_name': 'projects', 'texture_name': 'atlas.items', 'texture_data': icons})
    (OUT / 'custom_mappings').mkdir(parents=True, exist_ok=True)
    (OUT / 'custom_mappings/projects_greatsword.json').write_text(json.dumps(mapping(), ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    shutil.make_archive(str(OUT / 'projects_greatsword'), 'zip', rp)
    (OUT / 'projects_greatsword.zip').rename(OUT / 'projects_greatsword.mcpack')
    print('wrote', OUT)


if __name__ == '__main__' and '--preview' not in sys.argv:
    build()


# ---------------------------------------------------------------- preview: the written files, read back as Bedrock would
def _keyframes(channel, t):
    """A Bedrock channel at time t: a constant, or keyframes blended linearly (rotations component by component)."""
    if not isinstance(channel, dict): return np.array(channel if isinstance(channel, list) else [channel] * 3, float)
    times = sorted(channel, key=float)
    if t <= float(times[0]): return np.array(channel[times[0]], float)
    for a, b in zip(times, times[1:]):
        if t <= float(b):
            w = (t - float(a)) / (float(b) - float(a))
            return np.array(channel[a], float) * (1 - w) + np.array(channel[b], float) * w
    return np.array(channel[times[-1]], float)


def bone_matrix(tier, anim, t, name):
    """A bone's matrix (internal pixels) at time t, read from the written geometry and animation."""
    geo = json.loads((OUT / f'pack/models/entity/projects_greatsword_t{tier}.geo.json').read_text())['minecraft:geometry'][0]
    tracks = json.loads((OUT / f'pack/animations/projects_greatsword_t{tier}.animation.json').read_text())['animations'][anim]['bones']
    bones = {b['name']: b for b in geo['bones']}
    def world(name):
        b = bones[name]
        p = internal_pos(b['pivot'])
        tr = tracks.get(name, {})
        d = internal_pos(_keyframes(tr.get('position', [0, 0, 0]), t))
        e = internal_rot(_keyframes(tr.get('rotation', [0, 0, 0]), t))
        s = _keyframes(tr.get('scale', [1, 1, 1]), t)
        m = np.eye(4); m[:3, :3] = matrix_zyx(e) @ np.diag(s); m[:3, 3] = p + d - m[:3, :3] @ p
        return (world(b['parent']) if 'parent' in b else np.eye(4)) @ m
    return world(name), bones


def bedrock_faces(tier, anim, t):
    """Faces of the first-person bones at time t, in Minecraft view space (the camera at the eye, body = camera)."""
    import preview_warrior_cast as R
    eye = np.array((0.0, ASSUMED['eye_px'], 0.0))
    to_view = np.eye(4) * ASSUMED['entity_scale'] / 16; to_view[3, 3] = 1; to_view[:3, 3] = -eye * ASSUMED['entity_scale'] / 16
    faces = []
    for name in ('fp_sword', 'fp_arm_r', 'fp_arm_l'):
        w, bones = bone_matrix(tier, anim, t, name)
        m = to_view @ w
        elements = []
        for c in bones[name].get('cubes', []):
            o, size = internal_pos(c['origin']), np.array(c['size'], float)
            lo = np.array((o[0] - size[0], o[1], o[2])); hi = lo + size
            sleeve = any(f['uv'][1] >= 64 for f in c['uv'].values())
            uv = {}
            for fname, f in c['uv'].items():
                jname = MIRRORED_FACE.get(fname, fname)
                (u0, v0), (du, dv) = f['uv'], f['uv_size']
                u1 = u0 + du; v1 = v0 + dv
                if jname in ('north', 'south', 'up', 'down'): u0, u1 = u1, u0
                if sleeve: uv[jname] = {'texture': '#s', 'uv': [u0, v0 - 64, u1, v1 - 64]}
                else: uv[jname] = {'texture': '#a', 'uv': [u0 / 4, v0 / 4, u1 / 4, v1 / 4]}
            e = {'from': list(lo), 'to': list(hi), 'faces': uv}
            if 'pivot' in c:
                r = internal_rot(c.get('rotation', [0, 0, 0]))
                e['rotation'] = {'origin': list(internal_pos(c['pivot'])), 'x': r[0], 'y': r[1], 'z': r[2]}
            elements.append(e)
        model = {'textures': {'a': 'projects:item/weapons/materials', 's': 'projects:item/weapons/fp_sleeve'}, 'elements': elements}
        faces += R.model_faces(model, (0xffffff,), m)
    return faces


def preview(path, tier=3, fps=10):
    """Rows: per beat, Java (the shader's keys) above, Bedrock (the written files read back) below, same moments."""
    import preview_greatsword_fp as P
    from PIL import Image, ImageDraw
    rows = []
    for beat in S.SHADER_BEATS:
        L = LENGTHS[beat] / 20
        n = int(L * fps) + 1
        keys = S.shader_keys(f'greatsword_t{tier}', beat)
        java, bed = [], []
        for i in range(n):
            u = i / (n - 1)
            java.append(P.render_shader(keys, u, beat=beat, label=f'Java {beat} {u * L:.2f}s'))
            img = Image.new('RGBA', (P.R.W, P.R.H), P.R.BG + (255,))
            P.R.draw_faces(img, P.CAMERA, bedrock_faces(tier, f'animation.projects.greatsword_t{tier}.swing_{beat}', u * L))
            ImageDraw.Draw(img).text((8, 6), f'Bedrock {beat} {u * L:.2f}s', fill=(120, 220, 255), font=P.R.FONT)
            bed.append(img.convert('RGB'))
        rows += [java, bed]
    w, h = P.R.W // 3, P.R.H // 3
    cols = max(len(r) for r in rows)
    out = Image.new('RGB', (w * cols, h * len(rows)))
    for y, r in enumerate(rows):
        for x, c in enumerate(r): out.paste(c.resize((w, h)), (x * w, y * h))
    out.save(path)


if __name__ == '__main__' and '--preview' in sys.argv:
    preview(sys.argv[sys.argv.index('--preview') + 1])
