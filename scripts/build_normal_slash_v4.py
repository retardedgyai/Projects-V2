"""The greatsword's normal-attack slashes, drawn along the swing itself (2026-10-25, Creator: 「エフェクトもクラスの
斬撃とアニメーションに合わせるような感じで完成させる」).

The approved slash (scripts/build_approved_dash_v3.py) is a ribbon built from the path of a blade's tip: short
sections, each born where the tip was, widest while the tip moves fastest, a bright rim on the cutting side over a
mid-grey body and a dark inside, which then detach into a wake that drifts outward, splits along seams and erodes.
Its tip followed an invented arc. Here the same recipe follows the real one: the tip of the greatsword as the first
person swing draws it (Better Combat's chain, scripts/bc_firstperson.py), tick by tick after the hit, so the trail is
always behind the blade. The ribbon is the band the blade itself sweeps, from the tip in toward the hands, in 3D:
26.3 turns model elements freely on all three axes, so each section is a flat piece lying where the blade passed.

One model per beat, layer (blade, wake) and tick after the hit. The models are in the player's own frame (feet at the
origin, facing +z, the camera looking PITCH down, as the swing is drawn), a quarter scale: the display (scale 4)
stands at the player's feet turned to the player's facing and, round the eye, to the player's pitch.

    python3 scripts/build_normal_slash_v4.py --paths   # re-sample the swing (after changing it), then build
    python3 scripts/build_normal_slash_v4.py           # build from scripts/data/normal_slash_paths.json (Gradle does)
"""
import json
import math
from pathlib import Path

import numpy as np

import bc_firstperson as B   # numbers only (LENGTHS, UPSWING, LEAD_TICKS); the swing is read only with --paths
from build_approved_dash_v3 import PACK, ink_uvs

ASSETS = PACK / 'assets/projects'
FOLDER = 'combat_vfx/normal_slash_v4'
GRIP = np.array((8.0, 4.5, 8.0))
TIP = np.array((8.0, 29.9, 8.0))
EYE = np.array((0.0, 1.62, 0.0))
INNER = .45            # the ribbon's inner edge: this far along the blade from the hands (the tip is 1)
SCALE = 4.0            # display scale: model pixels are a quarter block
BLADE_TICKS = 7        # as the approved blade and wake
WAKE_TICKS = 13
STEP = 1 / 8           # section length, ticks
CUTTING = 1.2         # blocks a tick: slower, the blade is winding up or settling, not cutting (no trail)
BEFORE = 2.4           # the cut can be under way this long before the hit (the slam lands early): drawn at once
COLORS = {'blade': 0xeaf4ff, 'wake': 0x9dc4e3}   # the approved steel-white edge and blue-grey wake


def impact(beat):
    """GreatswordCombo: the hit lands round(length x upswing) ticks in (Kotlin rounds half to even)."""
    return int(round(B.LENGTHS[beat] * B.UPSWING))


PATHS = Path(__file__).resolve().parent / 'data/normal_slash_paths.json'
RESOLUTION = 40        # path samples a tick
SPAN = (-2.75, 4.5)    # ticks after the hit the path is kept for


def sample_paths():
    """The blade's tip and inner point round each hit, from the swing (needs the Better Combat reference animations,
    ~/ProjectS/refs/bettercombat); written to PATHS so the pack builds without them."""
    out = {}
    for beat in (1, 2, 3):
        anim, pose, hit, L = B.Animation(B.BEATS[beat]), B.Animation(B.POSE), impact(beat), B.LENGTHS[beat]
        rows = []
        for n in range(int(SPAN[0] * RESOLUTION), int(SPAN[1] * RESOLUTION) + 1):
            tick = B.anim_tick(min(1.0, max(0.0, (hit + n / RESOLUTION + B.LEAD_TICKS[beat]) / L)))
            m = B.item_matrix(anim, tick, B.HANDHELD, pose) @ B.on_diagonal(GRIP)
            tip = (m @ np.append(TIP, 1.0))[:3]
            hand = (m @ np.append(GRIP, 1.0))[:3]
            rows.append([round(float(v), 5) for v in (*tip, *(hand + (tip - hand) * INNER))])
        out[str(beat)] = rows
    PATHS.parent.mkdir(parents=True, exist_ok=True)
    PATHS.write_text(json.dumps(out, separators=(',', ':')) + '\n', encoding='utf-8')


class Path3:
    """The blade's tip and inner point, [tau] ticks after the hit, in the player's frame."""
    _data = None

    def __init__(self, beat):
        if Path3._data is None: Path3._data = json.loads(PATHS.read_text(encoding='utf-8'))
        self.rows = np.array(Path3._data[str(beat)])

    def at(self, tau):
        x = min(max((tau - SPAN[0]) * RESOLUTION, 0.0), len(self.rows) - 1.0)
        i = min(int(x), len(self.rows) - 2); w = x - i
        row = self.rows[i] + (self.rows[i + 1] - self.rows[i]) * w
        return row[:3], row[3:]

    def speed(self, tau):
        return float(np.linalg.norm(self.at(tau + .1)[0] - self.at(tau - .1)[0]) / .2)

    def band(self, tau):
        """The tip, and the ribbon's full width from it: as long as the swept band of the blade (tip to INNER), but
        turned to face the eye. The band the blade sweeps lies nearly edge-on to the eye (a level cut passes just under
        it, the slam goes straight through it), so it is laid across the tip's path as the eye sees it, on the hands'
        side."""
        p, q = self.at(tau)
        motion = self.at(tau + .05)[0] - self.at(tau - .05)[0]
        across = np.cross(motion, p - EYE)
        if np.linalg.norm(across) < 1e-6: return p, q - p
        across /= np.linalg.norm(across)
        if across @ (q - p) < 0: across = -across
        return p, across * np.linalg.norm(q - p)


def rotation_euler(m):
    """Angles (x, y, z) in degrees with m = Rz Ry Rx (the 26.3 free element rotation)."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(math.cos(y)) > 1e-6:
        x, z = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:
        x, z = math.atan2(-m[1][2], m[1][1]), 0.0
    return [round(math.degrees(a), 3) for a in (x, y, z)]


def to_model(p):
    """The player's frame (blocks) to model pixels: a quarter scale, and the display's own half turn undone."""
    return np.array((-p[0], p[1], -p[2])) * 16 / SCALE + 8


def piece(a0, a1, b0, b1, ink, inks):
    """A flat element covering the quad a0 a1 (outer edge) b1 b0 (inner edge), given in the player's frame."""
    a0, a1, b0, b1 = (to_model(p) for p in (a0, a1, b0, b1))
    u = (a1 + b1 - a0 - b0) / 2
    length = np.linalg.norm(u)
    if length < 1e-4: return None
    u /= length
    across = (b0 + b1 - a0 - a1) / 2
    v = across - u * (across @ u)
    height = np.linalg.norm(v)
    if height < 1e-4: return None
    v /= height
    n = np.cross(u, v)
    centre = (a0 + a1 + b0 + b1) / 4
    m = np.column_stack((u, v, -n))
    uv0, vv0, uv1, vv1 = inks[ink]
    face = {'texture': '#0', 'uv': [uv0, vv0, uv1, vv1], 'tintindex': 0}
    return {'from': [round(c, 4) for c in centre - (length / 2 + .02, height / 2, 0)],
            'to': [round(c, 4) for c in centre + (length / 2 + .02, height / 2, 0)],
            'shade': False, 'faces': {'north': face, 'south': face},
            'rotation': {'origin': [round(c, 4) for c in centre], **dict(zip('xyz', rotation_euler(m)))}}


def blade(path, now, inks):
    """The live cutting edge at [now] ticks after the hit: sections born up to a tick before, while young."""
    out = []
    for i in range(int(-BEFORE / STEP), int(now / STEP) + 1):
        t0, t1 = i * STEP, (i + 1) * STEP
        age = now - t0
        if not 0 <= age < 2.4 or t1 > now + 1e-6: continue
        # Fastest is widest (the approved recipe's speed term), swelling in and thinning out with age; where the
        # blade rests there is no cut.
        if path.speed(t0) < CUTTING: continue
        fast = min(1.0, path.speed(t0) / 3)
        width = fast * math.sin(math.pi * (age + .12) / 2.65) ** .6
        width *= .78 + .22 * math.sin(i * STEP * 8 * 2.1 / 8) ** 2
        (p0, d0), (p1, d1) = path.band(t0), path.band(t1)
        # A bright rim on the cutting side, a mid body, a dark inside.
        for lo, hi, ink in ((0, .28, 3), (.28, .66, 2), (.66, 1.0, 1)):
            e = piece(p0 + d0 * width * lo, p1 + d1 * width * lo, p0 + d0 * width * hi, p1 + d1 * width * hi, ink, inks)
            if e: out.append(e)
    # The leading head: a short bright sliver at the tip, where the blade is now.
    if 0 <= now <= 4:
        p, d = path.band(now); pb, db = path.band(now - .15)
        e = piece(pb, p, pb + db * .12, p + d * .12, 3, inks)
        if e: out.append(e)
    return out


def wake(path, now, inks):
    """Sections past their edge: drifting outward, splitting along seams, eroding to nothing."""
    out = []
    for i in range(int(-BEFORE / STEP), int(4 / STEP)):
        t0, t1 = i * STEP, (i + 1) * STEP
        age = now - t0
        if not 1.5 < age < 8.0 or path.speed(t0) < CUTTING: continue
        life = (age - 1.5) / 6.5
        if life > .4 and i % 8 in (0, 1, 7): continue
        if life > .72 and i % 8 not in (3, 4): continue
        width = .55 * (1 - life) ** .7
        drift = .12 * life + .28 * life * life
        ink = 2 if life < .5 else 1
        (p0, d0), (p1, d1) = path.band(t0), path.band(t1)
        o0 = -d0 / max(1e-6, np.linalg.norm(d0)) * drift
        o1 = -d1 / max(1e-6, np.linalg.norm(d1)) * drift
        e = piece(p0 + o0, p1 + o1, p0 + o0 + d0 * width, p1 + o1 + d1 * width, ink, inks)
        if e: out.append(e)
    return out


def models(beat, inks):
    path = Path3(beat)
    out = {}
    for k in range(BLADE_TICKS): out[f'b{beat}_blade_{k}'] = ('blade', blade(path, float(k), inks))
    for k in range(WAKE_TICKS): out[f'b{beat}_wake_{k}'] = ('wake', wake(path, float(k), inks))
    return out


def build(write):
    inks = ink_uvs(ASSETS)
    for beat in (1, 2, 3):
        for name, (layer, elements) in models(beat, inks).items():
            write(ASSETS / f'models/{FOLDER}/{name}.json', {'ambientocclusion': False,
                'textures': {'0': 'projects:combat_vfx/ribbon/slash_5'}, 'elements': elements})
            write(ASSETS / f'items/{FOLDER}/{name}.json', {'model': {'type': 'minecraft:model',
                'model': f'projects:{FOLDER}/{name}', 'tints': [{'type': 'minecraft:constant', 'value': COLORS[layer]}]}})


def slash_faces(beat, age):
    """The blade and wake [age] ticks after the hit, as faces in the first-person view (preview)."""
    import preview_warrior_cast as R
    world = B.Ry(math.pi) @ B.S(SCALE, SCALE, SCALE) @ B.T(-.5, -.5, -.5) @ B.S(1 / 16, 1 / 16, 1 / 16)
    view = B.Rx(B.PITCH) @ B.TO_VIEW @ world
    out = []
    for layer, count in (('blade', BLADE_TICKS), ('wake', WAKE_TICKS)):
        if 0 <= age < count:
            model, tints = R.item_model(f'{FOLDER}/b{beat}_{layer}_{age}')
            out += R.model_faces(model, tints, view)
    return out


def sheet(path, beats=(1, 2, 3), before=2, after=10, scale=2):
    """The swing (sword, arms) and its slash in first person, tick by tick round the hit."""
    import class_armament_swing as S
    import preview_greatsword_fp as P
    from PIL import Image
    cells = []
    for beat in beats:
        keys = S.shader_keys('greatsword_t3', beat); hit = impact(beat)
        for g in range(hit - before, hit + after):
            cells.append(P.render_shader(keys, g / B.LENGTHS[beat], beat=beat, label=f'{beat}段目 {g - hit:+d}',
                                         extra=slash_faces(beat, g - hit)))
    cols = before + after; w, h = P.R.W // scale, P.R.H // scale
    out = Image.new('RGB', (w * cols, h * len(beats)))
    for j, c in enumerate(cells): out.paste(c.resize((w, h)), ((j % cols) * w, (j // cols) * h))
    out.save(path)


if __name__ == '__main__':
    import sys
    if '--paths' in sys.argv: sample_paths()
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    build(write)
    print('wrote', ASSETS / 'models' / FOLDER)
