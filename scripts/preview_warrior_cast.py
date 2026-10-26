"""Player + skill preview: a warrior actually casting, as recorded on the server.

Run CorePlayerCombatTest's `warrior casts export ...` test first: it casts each skill in the real combat harness and
records the player position and every ItemDisplay the cast spawned, tick by tick (.tools/warrior-cast-frames.json).
This script draws a vanilla-proportioned player (armour colours are placeholders) holding the shipped greatsword_t3
model in its real third-person hand transform and weapon pose clip, plus the recorded displays, from two cameras.
Still a CPU projection, not the game client: no fog, no bloom, simple face shading on the player only.

Usage: python3 scripts/preview_warrior_cast.py [--ids slam,whirl] [--out .tools/cast]
"""
import argparse
import json
import math
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
FONT = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 15, index=0)
SMALL = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', 12, index=0)
W, H = 480, 320
FOCAL = 300
BG = (17, 21, 27)
WEAPON = 'weapons/greatsword_t3'

# ---------- matrices (MC conventions: right-handed, y up; a player at yaw 0 faces +z and its right hand is at -x) ----------
def T(x, y, z): m = np.eye(4); m[:3, 3] = (x, y, z); return m
def S(x, y, z): return np.diag((x, y, z, 1.0))
def Rx(a): c, s = math.cos(a), math.sin(a); return np.array(((1, 0, 0, 0), (0, c, -s, 0), (0, s, c, 0), (0, 0, 0, 1)))
def Ry(a): c, s = math.cos(a), math.sin(a); return np.array(((c, 0, s, 0), (0, 1, 0, 0), (-s, 0, c, 0), (0, 0, 0, 1)))
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array(((c, -s, 0, 0), (s, c, 0, 0), (0, 0, 1, 0), (0, 0, 0, 1)))
def Rq(q):
    x, y, z, w = q
    return np.array(((1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0),
                     (2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0),
                     (2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0), (0, 0, 0, 1)))


# ---------- pack models ----------
@lru_cache(maxsize=4096)
def item_model(key, cmd=None):
    item = json.loads((PACK / f'items/{key}.json').read_text())['model']
    if item.get('type') == 'minecraft:select':  # held weapons: hand contexts fall back to the custom_model_data dispatch
        item = item['fallback']
    if item.get('type') == 'minecraft:range_dispatch':
        chosen = item['fallback']
        for entry in item['entries']:
            if cmd is not None and cmd >= entry['threshold']: chosen = entry['model']
        item = chosen
    return model_json(item['model'].split(':')[1]), tuple(t.get('value', t.get('default', 0xffffff)) for t in item.get('tints', [{'value': 0xffffff}]))


@lru_cache(maxsize=4096)
def model_json(path):
    m = json.loads((PACK / f'models/{path}.json').read_text())
    if 'parent' in m and 'elements' not in m and m['parent'].startswith('projects:'):
        parent = model_json(m['parent'].split(':')[1])
        m = {**parent, **{k: v for k, v in m.items() if k != 'parent'}, 'textures': {**parent.get('textures', {}), **m.get('textures', {})}}
    if m.get('parent') == 'minecraft:item/generated' and 'elements' not in m:
        # A flat sprite item (the plain weapons): drawn as vanilla extrudes it, a 1 px thick slab of the sprite.
        m = {**m, 'elements': [{'from': [0, 0, 7.5], 'to': [16, 16, 8.5], 'faces': {
            'south': {'texture': '#layer0', 'uv': [0, 0, 16, 16]}, 'north': {'texture': '#layer0', 'uv': [16, 0, 0, 16]}}}]}
    return m


@lru_cache(maxsize=512)
def texture(name, tint):
    t = np.array(Image.open(PACK / ('textures/' + name.split(':')[1] + '.png')).convert('RGBA'))
    t = t[:t.shape[1]] if t.shape[0] > t.shape[1] else t  # animated strips: first frame
    c = np.array([(tint >> 16) & 255, (tint >> 8) & 255, tint & 255])
    t[:, :, :3] = (t[:, :, :3].astype(float) * c / 255).astype('uint8')
    return Image.fromarray(t)


FACES = (('north', (3, 2, 0, 1)), ('south', (6, 7, 5, 4)), ('down', (4, 5, 1, 0)), ('up', (2, 3, 7, 6)),
         ('west', (2, 6, 4, 0)), ('east', (7, 3, 1, 5)))


def element_rotation(e):
    r = e.get('rotation')
    if not r: return np.eye(4)
    a = math.radians(r['angle']); o = r['origin']
    rot = {'x': Rx, 'y': Ry, 'z': Rz}[r['axis']](a)
    return T(*o) @ rot @ T(-o[0], -o[1], -o[2])


# ---------- scripts/shaders/item.fsh, the heat cut (kinds 7..9), redone in numpy for the preview ----------
def _fract(v):
    return v - np.floor(v)


def _hash(x, y):
    x, y = _fract(x * 123.34), _fract(y * 456.21)
    d = x * (x + 45.32) + y * (y + 45.32)
    return _fract((x + d) * (y + d))


def _noise(x, y):
    ix, iy, fx, fy = np.floor(x), np.floor(y), x - np.floor(x), y - np.floor(y)
    ux, uy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = _hash(ix, iy), _hash(ix + 1, iy), _hash(ix, iy + 1), _hash(ix + 1, iy + 1)
    return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy


def _fbm(x, y):
    return .55 * _noise(x, y) + .3 * _noise(x * 2.1 + 7.3, y * 2.1 + 7.3) + .15 * _noise(x * 4.3 + 1.7, y * 4.3 + 1.7)


def _smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def _mix(a, b, t):
    return a + (np.asarray(b) - a) * t[..., None]


def _forge(h):
    c = _mix(np.broadcast_to(np.array((.07, .03, .03)), h.shape + (3,)), (.58, .05, .04), _smooth(0, .3, h))
    c = _mix(c, (1.0, .42, .08), _smooth(.3, .6, h))
    c = _mix(c, (1.0, .80, .36), _smooth(.6, .82, h))
    return _mix(c, (1.0, .97, .88), _smooth(.82, 1.0, h))


def heat_cut(data, kind, p, t):
    """scripts/shaders/item.fsh heatCut, line for line."""
    d = np.asarray(data).astype(float) / 255
    field, flow, across = d[..., 0], d[..., 1], d[..., 2]
    hot = 1.0 if kind == 7 else .82 if kind == 8 else .64
    head = 1.3 * (1 - (1 - p) ** 5)
    behind = head - flow
    lit = (behind >= 0).astype(float)
    trail = np.clip(1 - behind, 0, 1) * lit
    fade = 1 - _smooth(.55, 1.0, np.array(p))
    core = _smooth(.6, .74, field)
    ember = _fbm(flow * 14 - t * 2.2, across * 9 + t * .6)
    sparks = _smooth(.72, .9, _fbm(flow * 40 - t * 6, across * 30))
    blade = core * lit * _smooth(.15, .5, ember * .8 + trail * .9) * fade
    head_flash = _smooth(.86, 1.0, trail) * lit
    heat = np.clip((.25 + .7 * trail + .3 * head_flash) * hot + sparks * .3 * (1 - trail), 0, 1)
    blade_color = _forge(heat) * (1 + .35 * head_flash)[..., None]
    glow = field * field * (.35 + .65 * trail) * lit * fade * (.7 + .3 * ember)
    glow_color = _forge(.42 + .4 * trail * hot)
    glow_alpha = np.clip(glow * (.55 + .6 * head_flash), 0, .85)
    alpha = blade + glow_alpha * (1 - blade)
    safe = np.maximum(alpha, 1e-4)[..., None]
    rgb = np.minimum((blade_color * blade[..., None] + glow_color * (glow_alpha * (1 - blade))[..., None]) / safe, 1)
    rgba = np.dstack((rgb, np.where(alpha < .004, 0, alpha))) * 255
    return Image.fromarray(rgba.astype('uint8'))


SHADER_TIME = [0.0]


def model_faces(model, tints, matrix, tint=None):
    """Faces of a block/item model (pixel units) placed by matrix: (world corners, texture image or rgba)."""
    out = []
    for e in model.get('elements', []):
        lo, hi = e['from'], e['to']
        m = matrix @ element_rotation(e)
        corners = [(m @ np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)] + [1.0]))[:3] for i in range(8)]
        for name, ids in FACES:
            face = e['faces'].get(name)
            if not face: continue
            tex_name = model['textures'][face['texture'][1:]]
            if isinstance(tex_name, dict): tex_name = tex_name['sprite']
            while tex_name.startswith('#'): tex_name = model['textures'][tex_name[1:]]
            if tint is not None and tint & 255 == 77 and 7 <= (tint >> 8) & 255 <= 9:
                img = heat_cut(texture(tex_name, 0xffffff), (tint >> 8) & 255, ((tint >> 16) & 255) / 255, SHADER_TIME[0])
                out.append(([corners[i] for i in ids], img))
                continue
            tint = tints[face.get('tintindex', 0)] if 'tintindex' in face else 0xffffff
            img = texture(tex_name, tint)
            uv = face.get('uv', [0, 0, 16, 16])
            box = (round(min(uv[0], uv[2]) / 16 * img.width), round(min(uv[1], uv[3]) / 16 * img.height),
                   round(max(uv[0], uv[2]) / 16 * img.width), round(max(uv[1], uv[3]) / 16 * img.height))
            if box[2] <= box[0]: box = (box[0], box[1], box[0] + 1, box[3])
            if box[3] <= box[1]: box = (box[0], box[1], box[2], box[1] + 1)
            crop = img.crop(box)
            if uv[1] > uv[3]: crop = crop.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            if uv[0] > uv[2]: crop = crop.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            if face.get('rotation'): crop = crop.rotate(-face['rotation'], expand=True, resample=Image.Resampling.NEAREST)
            if e.get('shade', True) and model.get('_shade'):
                # Minecraft's face shade (up 1, down .5, z sides .8, x sides .6), on the face's world normal.
                c0, c1, c2 = (corners[i] for i in ids[:3])
                n = np.cross(c1 - c0, c2 - c0); n = n / (np.linalg.norm(n) + 1e-9)
                k = n[1] ** 2 * (1.0 if n[1] > 0 else .5) + n[0] ** 2 * .6 + n[2] ** 2 * .8
                arr = np.array(crop.convert('RGBA')).astype(float); arr[..., :3] *= k
                crop = Image.fromarray(arr.astype('uint8'))
            if crop.size == (1, 1):
                ink = crop.getpixel((0, 0))
                if ink[3] == 0: continue
                if ink[3] == 255: crop = ink
            out.append(([corners[i] for i in ids], crop))
    return out


# ---------- the player (vanilla PlayerModel boxes, rendered the way LivingEntityRenderer does) ----------
SKIN = {'head': (150, 154, 162), 'visor': (40, 36, 36), 'body': (122, 126, 134), 'tabard': (176, 54, 34),
        'arm': (110, 114, 122), 'leg': (86, 90, 98)}
PARTS = {  # name: pivot (px, model space, y down from the neck), box from, size
    'head': ((0, 0, 0), (-4, -8, -4), (8, 8, 8)), 'body': ((0, 0, 0), (-4, 0, -2), (8, 12, 4)),
    'right_arm': ((-5, 2, 0), (-3, -2, -2), (4, 12, 4)), 'left_arm': ((5, 2, 0), (-1, -2, -2), (4, 12, 4)),
    'right_leg': ((-1.9, 12, 0), (-2, 0, -2), (4, 12, 4)), 'left_leg': ((1.9, 12, 0), (-2, 0, -2), (4, 12, 4))}


def root_matrix(pos, yaw_deg):
    # LivingEntityRenderer: rotate (180 - bodyYaw), flip, player scale .9375, translate down 1.501.
    return T(*pos) @ Ry(math.radians(180 - yaw_deg)) @ S(-1, -1, 1) @ S(.9375, .9375, .9375) @ T(0, -1.501, 0)


def arm_pose(swing):
    """HumanoidModel: holding an item (ArmPose.ITEM) plus setupAttackAnimation at attackTime=swing (None = no swing)."""
    x, y, z = -math.pi / 10, 0.0, 0.0
    body_y = 0.0
    if swing is not None:
        f = swing
        body_y = math.sin(math.sqrt(f) * math.pi * 2) * .2
        f1 = 1 - (1 - f) ** 4
        x -= math.sin(f1 * math.pi) * 1.2 + math.sin(f * math.pi) * .7 * .75
        y += body_y * 2
        z += math.sin(f * math.pi) * -.4
    return (x, y, z), body_y


def player_faces(pos, yaw_deg, swing):
    root = root_matrix(pos, yaw_deg)
    (ax, ay, az), body_y = arm_pose(swing)
    faces = []
    for name, (pivot, frm, size) in PARTS.items():
        px, py, pz = pivot
        rot = np.eye(4)
        if name == 'body': rot = Ry(body_y)
        if name == 'right_arm':
            px, pz = -math.cos(body_y) * 5, math.sin(body_y) * 5
            rot = Rz(az) @ Ry(ay) @ Rx(ax)
        if name == 'left_arm': px, pz = math.cos(body_y) * 5, -math.sin(body_y) * 5; rot = Ry(body_y)
        m = root @ S(1 / 16, 1 / 16, 1 / 16) @ T(px, py, pz) @ rot
        lo = frm; hi = [frm[i] + size[i] for i in range(3)]
        corners = [(m @ np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)] + [1.0]))[:3] for i in range(8)]
        for face, ids in FACES:
            color = SKIN['arm' if 'arm' in name else 'leg' if 'leg' in name else name]
            if name == 'head' and face == 'north': color = SKIN['visor']
            if name == 'body' and face in ('north', 'south'): color = SKIN['tabard']
            faces.append(([corners[i] for i in ids], ('shade', color)))
        if name == 'right_arm':
            hand = root @ T(px / 16, py / 16, pz / 16) @ rot
    return faces, hand


def weapon_faces(hand, pose):
    model, tints = item_model(WEAPON, float(pose))
    d = model['display']['thirdperson_righthand']
    t = [min(max(v, -80), 80) / 16 for v in d.get('translation', [0, 0, 0])]
    r = [math.radians(v) for v in d.get('rotation', [0, 0, 0])]
    s = d.get('scale', [1, 1, 1])
    # ItemInHandLayer then ItemTransform.apply then the item renderer's -0.5 recentre.
    m = (hand @ Rx(-math.pi / 2) @ Ry(math.pi) @ T(1 / 16, .125, -.625) @ T(*t) @ Rx(r[0]) @ Ry(r[1]) @ Rz(r[2]) @ S(*s)
         @ T(-.5, -.5, -.5) @ S(1 / 16, 1 / 16, 1 / 16))
    return model_faces(model, tints, m)


def display_faces(d, camera=None):
    if d['model'].startswith('vanilla:'):
        # A vanilla block item (the ground torn up): drawn as the turf cube, at the half size FIXED gives block items.
        d = {**d, 'model': 'combat_vfx/warrior_ref/gblock', 'scale': [v * .5 for v in d['scale']]}
    model, tints = item_model(d['model'])
    model = {**model, '_shade': 'warrior_ref/gblock' in d['model'] or 'warrior_ref/turf' in d['model'] or 'warrior_ref/clod' in d['model'] or 'warrior_ref/finger' in d['model'] or 'warrior_ref/lump' in d['model'] or 'warrior_ref/petal' in d['model'] or 'warrior_ref/gslab' in d['model'] or 'warrior_ref/chunk' in d['model']}
    rot = Rq(d['quaternion'])
    if d.get('billboard') and camera is not None:
        # Billboard CENTER: the display faces the camera; its own rotation then turns it in the screen plane.
        back = -camera.f
        basis = np.eye(4); basis[:3, 0] = -camera.r; basis[:3, 1] = camera.u; basis[:3, 2] = -back
        rot = basis @ rot
    m = T(*d['offset']) @ rot @ S(*d['scale']) @ T(-.5, -.5, -.5) @ S(1 / 16, 1 / 16, 1 / 16)
    return model_faces(model, tints, m, d.get('tint') if d.get('tint', -1) >= 0 else None)


def weapon_pose(scene, tick):
    """CoreArmamentPresentation clips: PREPARE from the cast for the startup, RELEASE from the pulse for 12 ticks."""
    cast, startup = scene['castTick'], scene['startup']
    sid = scene['id'].removesuffix('_shader')
    if sid == 'war_guard': return (tick // 2) % 12  # guard motif keeps the idle frames
    if cast <= tick < cast + startup and startup > 1:
        return 12 + (tick - cast) * 5 // max(startup - 1, 1)
    if sid == 'war_ult':  # every one of the three falls is its own 8-tick release
        for k in range(scene.get('pulses', 1)):
            start = cast + startup + 8 * k
            if start <= tick < start + 8: return 18 + (tick - start) * 5 // 7
    elif cast + startup <= tick < cast + startup + 12:
        return 18 + (tick - cast - startup) * 5 // 11
    return (tick // 2) % 12


# ---------- camera ----------
class Camera:
    def __init__(self, eye, target, focal=None):
        self.eye = np.array(eye, float)
        self.focal = focal or FOCAL
        f = np.array(target, float) - self.eye; self.f = f / np.linalg.norm(f)
        r = np.cross(self.f, (0, 1, 0)); self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)

    def cam(self, p):
        d = np.asarray(p) - self.eye
        return np.array((d @ self.r, d @ self.u, d @ self.f))

    def screen(self, c):
        return (W / 2 + c[0] / c[2] * self.focal, H / 2 - c[1] / c[2] * self.focal)


def clip(poly, near=.15):
    out = []
    for a, b in zip(poly, poly[1:] + poly[:1]):
        if a[2] >= near: out.append(a)
        if (a[2] >= near) != (b[2] >= near):
            t = (near - a[2]) / (b[2] - a[2]); out.append(a + (b - a) * t)
    return out


def draw_faces(image, camera, faces):
    items = []
    for corners, content in faces:
        cam = [camera.cam(c) for c in corners]
        if max(c[2] for c in cam) < .15: continue
        full = [camera.screen(c) if c[2] > 1e-3 else None for c in cam]
        vis = [camera.screen(c) for c in clip(cam)]
        if len(vis) < 3: continue
        area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(vis, vis[1:] + vis[:1]))
        if area <= 1e-7: continue  # back face
        depth = sum(c[2] for c in cam) / len(cam)
        items.append((depth, vis, full, content, cam))
    for depth, vis, full, content, cam in sorted(items, key=lambda i: -i[0]):
        if isinstance(content, tuple) and content[0] == 'shade':
            n = np.cross(cam[1] - cam[0], cam[2] - cam[0]); n = n / (np.linalg.norm(n) + 1e-9)
            lit = .55 + .45 * max(0.0, float(n @ camera.cam_dir_light))
            ImageDraw.Draw(image).polygon(vis, fill=tuple(int(v * lit) for v in content[1]))
        elif isinstance(content, tuple):
            ImageDraw.Draw(image).polygon(vis, fill=content)
        else:
            if any(p is None for p in full): continue
            x0, y0 = max(0, int(min(p[0] for p in vis))), max(0, int(min(p[1] for p in vis)))
            x1, y1 = min(W, int(max(p[0] for p in vis)) + 1), min(H, int(max(p[1] for p in vis)) + 1)
            if x1 <= x0 or y1 <= y0: continue
            tw, th = content.size
            matrix, result = [], []
            for (x, y), (u, v) in zip(full, ((0, 0), (tw, 0), (tw, th), (0, th))):
                x -= x0; y -= y0
                matrix.extend(((x, y, 1, 0, 0, 0, -u * x, -u * y), (0, 0, 0, x, y, 1, -v * x, -v * y))); result.extend((u, v))
            try: coeff = np.linalg.solve(matrix, result)
            except np.linalg.LinAlgError: continue
            warped = content.transform((x1 - x0, y1 - y0), Image.Transform.PERSPECTIVE, tuple(coeff), Image.Resampling.NEAREST)
            mask = Image.new('L', warped.size, 0)
            ImageDraw.Draw(mask).polygon([(p[0] - x0, p[1] - y0) for p in vis], fill=255)
            alpha = np.minimum(np.array(warped.getchannel('A')), np.array(mask))
            warped.putalpha(Image.fromarray(alpha))
            image.alpha_composite(warped, (x0, y0))


def grid(image, camera):
    draw = ImageDraw.Draw(image)
    for n in range(-8, 9):
        for a, b in (((n, 0, -6), (n, 0, 10)), ((-8, 0, n + 2), (8, 0, n + 2))):
            pa, pb = camera.cam(np.array(a, float)), camera.cam(np.array(b, float))
            seg = clip([pa, pb]) if False else None
            if pa[2] < .2 and pb[2] < .2: continue
            if pa[2] < .2: pa = pa + (pb - pa) * ((.2 - pa[2]) / (pb[2] - pa[2]))
            if pb[2] < .2: pb = pb + (pa - pb) * ((.2 - pb[2]) / (pa[2] - pb[2]))
            draw.line([camera.screen(pa), camera.screen(pb)], fill=(34, 42, 52))


def target_faces(p):
    """The training boss is drawn as a plain 0.9 x 2.2 block so the reach reads; it is not its model."""
    lo, hi = (p[0] - .45, p[1], p[2] - .45), (p[0] + .45, p[1] + 2.2, p[2] + .45)
    corners = [np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)]) for i in range(8)]
    return [([corners[i] for i in ids], ('shade', (92, 70, 66))) for _, ids in FACES]


# F5: the camera sits 4 blocks behind the eye (1.62 m) along the look vector; here looking 20 degrees down.
VIEWS = {'behind': ('背後（F5視点）', (0.0, 3.0, -3.76), (0.0, .25, 4.0)), 'side': ('横から', (-5.2, 1.9, 1.6), (0.0, 1.0, 1.9)),
         # Ground skills (地割り etc.) are read from above: a high three-quarter view over the wielder's shoulder.
         'high': ('斜め上から', (-4.0, 6.5, -1.5), (0.0, 0.0, 3.5)),
         # First person: the camera rides the recorded body (eye height, its yaw, looking a little down) with
         # Minecraft's default 70 degree vertical FOV. The held sword is drawn by the item shader in game and is
         # not reproduced here; this view shows the body's motion, the world pieces and the enemies.
         'fp': ('一人称（手の剣は描いていない）', None, None)}
FP_FOCAL = (H / 2) / math.tan(math.radians(35))


GRASS_FLOOR = [None]


def grass_floor():
    """A grass floor (2-block tiles) so ground effects are judged on the ground they sit on."""
    tile = Image.open(PACK / 'textures/combat_vfx/warrior_ref/preview_grass.png').convert('RGBA')
    big = Image.new('RGBA', (32, 32))
    for i in range(2):
        for j in range(2): big.paste(tile, (16 * i, 16 * j))
    faces = []
    for x in range(-8, 8, 2):
        for z in range(-6, 12, 2):
            faces.append(([np.array((x + 2.0, 0, z + 2.0)), np.array((x + 0.0, 0, z + 2.0)), np.array((x + 0.0, 0, z + 0.0)), np.array((x + 2.0, 0, z + 0.0))], big))
    return faces


def render(scene, tick, view):
    frame = scene['frames'][tick]
    SHADER_TIME[0] = tick / 20
    origin = np.array(scene['frames'][0]['player'][:3])
    label, eye, target = VIEWS[view]
    if view == 'fp':
        px, py, pz, yaw = frame['player']
        at = np.array((px, py, pz)) - origin + (0.0, 1.62, 0.0)
        a = math.radians(yaw)
        camera = Camera(at, at + np.array((-math.sin(a), -.16, math.cos(a))), FP_FOCAL)
    else:
        camera = Camera(eye, target)
    camera.cam_dir_light = camera.cam(camera.eye + np.array((.3, 1.0, -.5)) / np.linalg.norm((.3, 1.0, -.5)))
    image = Image.new('RGBA', (W, H), BG + (255,))
    if GRASS_FLOOR[0] is not None:
        draw_faces(image, camera, GRASS_FLOOR[0])
    else:
        grid(image, camera)
    px, py, pz, yaw = frame['player']
    pos = np.array((px, py, pz)) - origin
    cast = scene['castTick']
    swing = (tick - cast) / 6 if 0 <= tick - cast < 6 else None
    faces, hand = player_faces(pos, yaw, swing)
    if view == 'fp': faces = []          # the own body is not seen in first person
    # 引き摺り: the held sword is hidden while the dragged one is drawn (CoreArmamentPresentation.hide).
    dragging = scene['id'].startswith('war_cry') and scene['castTick'] + scene['startup'] <= tick < scene['castTick'] + scene['startup'] + 42
    if not dragging and view != 'fp': faces += weapon_faces(hand, weapon_pose(scene, tick))
    for t in frame['targets']: faces += target_faces(np.array(t) - origin)
    for d in frame['displays']:
        faces += display_faces({**d, 'offset': list(np.array(d['offset']) - origin)}, camera)
    draw_faces(image, camera, faces)
    draw = ImageDraw.Draw(image)
    draw.text((10, 6), f"{scene['name']}　{label}", font=FONT, fill=(236, 226, 206))
    phase = '待機' if tick < cast else '構え' if tick < cast + scene['startup'] else '発動'
    draw.text((10, 28), f"{(tick - cast) / 20:+.2f}秒　{phase}", font=SMALL, fill=(170, 164, 154))
    return image.convert('RGB')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ids', default='dash,slam,whirl,war_wound,war_guard,war_ult,slam_shader,whirl_shader,war_ult_shader')
    parser.add_argument('--out', default='.tools/cast')
    parser.add_argument('--frames', default='.tools/warrior-cast-frames.json')
    parser.add_argument('--wide', action='store_true', help='pull the high view back to see a long reach (about 10 m)')
    parser.add_argument('--grass', action='store_true', help='draw a grass floor instead of the grid')
    args = parser.parse_args()
    if args.grass: GRASS_FLOOR[0] = grass_floor()
    if args.wide:
        VIEWS['high'] = ('斜め上から（広く）', (-6.5, 10.0, 0.5), (0.0, 0.0, 5.8))
    scenes = {s['id']: s for s in json.loads((ROOT / args.frames).read_text())}
    out = ROOT / args.out; out.mkdir(parents=True, exist_ok=True)
    for sid in args.ids.split(','):
        scene = scenes[sid]
        last = max(i for i, f in enumerate(scene['frames']) if f['displays'] or i <= scene['castTick'] + scene['startup'])
        ticks = range(0, min(len(scene['frames']), last + 8))
        frames = []
        for tick in ticks:
            sheet = Image.new('RGB', (W * 3, H))
            for k, view in enumerate(('behind', 'side', 'high')): sheet.paste(render(scene, tick, view), (W * k, 0))
            frames.append(sheet)
        for name, ms in (('', 50), ('-slow', 150)):
            frames[0].save(out / f'{sid}{name}.gif', save_all=True, append_images=frames[1:], duration=ms, loop=0)
        frames[scene['castTick'] + scene['startup'] + 2].save(out / f'{sid}-hit.png')
        print(sid, len(frames), 'frames')


if __name__ == '__main__':
    main()
