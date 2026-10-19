"""Player puppet motion preview (2026-10-19): a skinned 11-part puppet playing a motion from
server-minestom/src/main/resources/player-motions/*.json, from the side, from behind (F5) and in first person.

The puppet is what the server will show while a skill plays: the player hidden, a puppet of item displays wearing
their skin (one tinted face per skin pixel, after toxicity188/DynamicUV's idea). Parts: head, torso, upper/lower
arms, upper/lower legs, and the greatsword. Only root, torso, head, right arm, sword and feet are keyed; the left
hand reaches the grip and the legs reach the feet by two-bone IK, so a crouch is just the root going down.
First person shows the held greatsword (fp_grip / fp_blade, camera space) instead of the puppet, an edge glow per charge
level, a narrowing view while charging and a shake at the impact.

Not the game: CPU projection, no fog, flat lighting. The skin is a local copy of the default skin
(.tools/skin, from the client jar, not committed). Usage: python3 scripts/preview_player_motion.py [motion] [--skin png]
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import preview_render as R
from preview_render import T, Rx, Ry, Rz, S

ROOT = Path(__file__).resolve().parents[1]
MOTIONS = ROOT / 'server-minestom/src/main/resources/player-motions'
K = .9375 / 16                      # one skin pixel in blocks (the player model's scale)
SWORD = 'weapons/greatsword_t3'
SWORD_SCALE = .95                   # the held greatsword is ~1.8 m: a hunter's blade, not a vanilla sword
DEFAULT_WEAPON = {'model': SWORD, 'scale': SWORD_SCALE, 'grip': 4, 'hands': 'two'}
EYE = 1.62
FPS = 30


# ---------------------------------------------------------------- animation
def ease(kind, u):
    if kind == 'in': return u * u * u
    if kind == 'out': return 1 - (1 - u) ** 3
    if kind == 'inout': return u * u * (3 - 2 * u)
    if kind == 'hold': return 0.0 if u < 1 else 1.0
    return u


def slerp(a, b, w):
    """Directions turn along the great circle at an even rate; a straight blend would snap through zero."""
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    angle = math.acos(float(np.clip(a @ b, -1, 1)))
    if angle < 1e-5: return a
    return (math.sin((1 - w) * angle) * a + math.sin(w * angle) * b) / math.sin(angle)


def sample(track, t, field):
    keys = [k for k in track if field in k]
    if t <= keys[0]['t']: return np.array(keys[0][field], float)
    for a, b in zip(keys, keys[1:]):
        if t <= b['t']:
            u = (t - a['t']) / max(b['t'] - a['t'], 1e-9)
            w = ease(b.get('ease', 'linear'), u)
            va, vb = np.array(a[field], float), np.array(b[field], float)
            return slerp(va, vb, w) if field == 'dir' else va * (1 - w) + vb * w
    return np.array(keys[-1][field], float)


def shake_amp(motion, t):
    return float(sample(motion['shake'], t, 'amp'))


def rot(pitch, yaw, roll, hangs=False):
    """pitch + = the part's tip forward. Hanging limbs point down, so their forward swing is the opposite sign."""
    p = math.radians(-pitch if hangs else pitch)
    return Ry(math.radians(yaw)) @ Rx(p) @ Rz(math.radians(roll))


# ---------------------------------------------------------------- IK
def two_bone(start, target, l1, l2, pole):
    """Shoulder/hip at start, hand/foot toward target. Returns (joint, end) positions, bending toward pole."""
    d = target - start
    dist = np.linalg.norm(d)
    dist = min(max(dist, abs(l1 - l2) + 1e-4), l1 + l2 - 1e-4)
    dirn = d / (np.linalg.norm(d) + 1e-9)
    a = (l1 * l1 + dist * dist - l2 * l2) / (2 * dist)
    h = math.sqrt(max(l1 * l1 - a * a, 0.0))
    side = pole - dirn * (pole @ dirn)
    side = side / (np.linalg.norm(side) + 1e-9)
    joint = start + dirn * a + side * h
    return joint, start + dirn * dist


def frame_along(origin, down, side_hint):
    """A 4x4 frame at origin whose -y axis points along down, x from side_hint."""
    y = -down / (np.linalg.norm(down) + 1e-9)
    x = side_hint - y * (side_hint @ y)
    x = x / (np.linalg.norm(x) + 1e-9)
    z = np.cross(x, y)
    m = np.eye(4); m[:3, 0], m[:3, 1], m[:3, 2], m[:3, 3] = x, y, z, origin
    return m


def blade_frame(track, root, t, grip):
    """Blade along its keyed direction; its width (edge to edge) lies along the way the blade is moving, so the
    edge leads the cut. While the blade is still, the nearest motion within 0.25 s decides; failing that, the
    flat faces sideways."""
    z = (root @ np.append(sample(track, t, 'dir'), 0))[:3]
    z = z / np.linalg.norm(z)
    width = None
    for dt in (.01, .03, .06, .1, .16, .25):
        for sgn in (1, -1):
            a = sample(track, t + sgn * dt, 'dir'); b = sample(track, t + sgn * dt + .01, 'dir')
            v = (root @ np.append(b - a, 0))[:3]
            v = v - z * (v @ z)
            if np.linalg.norm(v) > 1e-4:
                width = v / np.linalg.norm(v); break
        if width is not None: break
    if width is None:
        x = np.cross((0, 1, 0), z); x = x / (np.linalg.norm(x) + 1e-9); width = np.cross(z, x)
    # Edge-to-edge is a line, not a direction: keep one consistent sign (toward world up, else the root's forward)
    # so a cut that reverses does not spin the blade half a turn about its own axis in one tick.
    ref = np.array([0.0, 1.0, 0.0]) - z * z[1]
    if np.linalg.norm(ref) < .2: ref = (root @ np.array([0, 0, 1, 0]))[:3]
    if width @ ref < 0: width = -width
    x = np.cross(width, z)
    m = np.eye(4); m[:3, 0], m[:3, 1], m[:3, 2], m[:3, 3] = x, width, z, grip
    return m


def pose(motion, t, base_yaw=0.0):
    """World matrices for every part at time t (blocks; y up; the puppet faces +z before base_yaw)."""
    tr = motion['tracks']
    amp = shake_amp(motion, t)
    jitter = lambda seed: amp * (math.sin(t * 61.0 + seed) * .6 + math.sin(t * 97.0 + seed * 2.3) * .4)
    rp = sample(tr['root'], t, 'pos'); rr = sample(tr['root'], t, 'rot')
    world = Ry(math.radians(base_yaw))
    root = world @ T(rp[0], 12 * K + rp[1], rp[2]) @ rot(*rr)
    tor = sample(tr['torso'], t, 'rot') + [jitter(1), jitter(2) * .5, jitter(3) * .5]
    torso = root @ rot(*tor)
    head = torso @ T(0, 12 * K, 0) @ rot(*sample(tr['head'], t, 'rot'))
    weapon = motion.get('weapon', DEFAULT_WEAPON)
    side = (torso @ np.array([1, 0, 0, 0]))[:3]
    arms, weapons, targets = {}, {}, {}
    if weapon['hands'] == 'dual':
        for hand in ('r', 'l'):
            g = (root @ np.append(sample(tr[f'grip_{hand}'], t, 'pos'), 1))[:3]
            weapons[f'blade_{hand}'] = blade_frame(tr[f'blade_{hand}'], root, t, g)
            targets[hand] = g
    else:
        g = (root @ np.append(sample(tr['grip'], t, 'pos'), 1))[:3]
        w = blade_frame(tr['blade'], root, t, g)
        weapons['sword'] = w
        targets = {'r': g, 'l': g - w[:3, 2] * weapon.get('second', 3.2) * K}
    for name, sx in (('r', -5), ('l', 5)):
        sh = (torso @ np.array([sx * K, 10 * K, 0, 1]))[:3]
        pole = (torso @ np.array([np.sign(sx), -1, -.6, 0]))[:3]
        elbow, hand = two_bone(sh, targets[name], 4 * K + 1e-3, 5.5 * K, pole)
        arms[f'arm_{name}_up'] = frame_along(sh, elbow - sh, side)
        arms[f'arm_{name}_low'] = frame_along(elbow, hand - elbow, side)
    parts = {'torso': torso, 'head': head, **arms, **weapons}
    fwd = (root @ np.array([0, 0, 1, 0]))[:3]
    for side, sx in (('r', -1.9), ('l', 1.9)):
        hip = (root @ np.array([sx * K, 0, 0, 1]))[:3]
        f = sample(tr[f'foot_{side}'], t, 'pos')
        foot = (world @ np.array([f[0], 0, f[2], 1]))[:3] + np.array([0, 0, 0])
        out = (root @ np.array([np.sign(sx), 0, 0, 0]))[:3]
        knee, ankle = two_bone(hip, foot, 6 * K, 6 * K, fwd + out * .55 + np.array([0, .2, 0]))  # knees forward and out
        hint = (root @ np.array([1, 0, 0, 0]))[:3]
        parts[f'leg_{side}_up'] = frame_along(hip, knee - hip, hint)
        parts[f'leg_{side}_low'] = frame_along(knee, ankle - knee, hint)
    return parts


BB_SOURCE = {}


def motion_pose(motion, t):
    """The saved Blockbench animation when the motion names one (the source of truth), else the old IK keys."""
    src = motion.get('blockbench')
    if not src: return pose(motion, t)
    import player_rig
    if src not in BB_SOURCE:
        model_name, anim_name = src.split('#')
        model = player_rig.load(model_name)
        BB_SOURCE[src] = (model, player_rig.animation(model, anim_name))
    model, anim = BB_SOURCE[src]
    frames = player_rig.pose(model, anim, t)
    rename = {'weapon_r': 'sword' if motion.get('weapon', DEFAULT_WEAPON)['hands'] != 'dual' else 'blade_r', 'weapon_l': 'blade_l'}
    out = {}
    for name, m in frames.items():
        if name == 'root': continue
        if name == 'weapon_l' and motion.get('weapon', DEFAULT_WEAPON)['hands'] != 'dual': continue
        out[rename.get(name, name)] = m
    return out


# ---------------------------------------------------------------- skin
# Box UV origins on a 64x64 skin (wide arms): base layer, overlay layer, size (w, h, d) in px.
SKIN_BOX = {'head': ((0, 0), (32, 0), (8, 8, 8)), 'torso': ((16, 16), (16, 32), (8, 12, 4)),
            'arm_r': ((40, 16), (40, 32), (4, 12, 4)), 'arm_l': ((32, 48), (48, 48), (4, 12, 4)),
            'leg_r': ((0, 16), (0, 32), (4, 12, 4)), 'leg_l': ((16, 48), (0, 48), (4, 12, 4))}
# Part -> (skin box, rows of the box's sides it shows, box from (px, part frame), size)
PART_BOX = {
    'head': ('head', (0, 8), (-4, 0, -4), (8, 8, 8)), 'torso': ('torso', (0, 12), (-4, 0, -2), (8, 12, 4)),
    'arm_r_up': ('arm_r', (0, 6), (-3, -4, -2), (4, 6, 4)), 'arm_r_low': ('arm_r', (6, 12), (-2, -6, -2), (4, 6, 4)),
    'arm_l_up': ('arm_l', (0, 6), (-1, -4, -2), (4, 6, 4)), 'arm_l_low': ('arm_l', (6, 12), (-2, -6, -2), (4, 6, 4)),
    'leg_r_up': ('leg_r', (0, 6), (-2, -6, -2), (4, 6, 4)), 'leg_r_low': ('leg_r', (6, 12), (-2, -6, -2), (4, 6, 4)),
    'leg_l_up': ('leg_l', (0, 6), (-2, -6, -2), (4, 6, 4)), 'leg_l_low': ('leg_l', (6, 12), (-2, -6, -2), (4, 6, 4)),
}


def skin_regions(skin, uv, size, rows, top, bottom):
    (u, v), (w, h, d) = uv, size
    r0, r1 = rows
    crop = lambda x, y, cw, ch: skin.crop((x, y, x + cw, y + ch))
    faces = {'west': crop(u, v + d + r0, d, r1 - r0), 'south': crop(u + d, v + d + r0, w, r1 - r0),
             'east': crop(u + d + w, v + d + r0, d, r1 - r0), 'north': crop(u + 2 * d + w, v + d + r0, w, r1 - r0)}
    if top: faces['up'] = crop(u + d, v, w, d)
    if bottom: faces['down'] = crop(u + d + w, v, w, d)
    return faces


def part_faces(name, m, skin, glow=None):
    box, rows, frm, size = PART_BOX[name]
    base_uv, over_uv, full = SKIN_BOX[box]
    top, bottom = rows[0] == 0, rows[1] == full[1]
    out = []
    for uv, grow in ((base_uv, 0.0), (over_uv, .5 if box == 'head' else .25)):
        lo = [frm[i] - grow for i in range(3)]; hi = [frm[i] + size[i] + grow for i in range(3)]
        if not top: hi[1] = frm[1] + size[1]
        if not bottom: lo[1] = frm[1]
        corners = [(m @ np.array([(hi[j] if i & (1 << j) else lo[j]) * K for j in range(3)] + [1.0]))[:3] for i in range(8)]
        regions = skin_regions(skin, uv, full, rows, top, bottom)
        for face, ids in R.FACES:
            img = regions.get(face)
            if img is None or np.array(img)[..., 3].max() == 0: continue
            out.append(([corners[i] for i in ids], ('shadeimg', img, glow)))
    return out


def sword_faces(m, glow, weapon=DEFAULT_WEAPON):
    model, tints = R.item_model(weapon['model'], 0.0)
    # Model space: grip around y=4, blade toward +y, its width (edge to edge) along x. Put the grip at the fist, the
    # blade along +z and the width along the frame's y, i.e. in the swing plane: the edge leads the cut and the flat
    # faces sideways. (The first version laid the width across the swing, so it struck with the flat.)
    sc = weapon['scale']
    place = (m @ S(sc, sc, sc) @ Rx(math.pi / 2) @ Ry(math.pi / 2)
             @ T(-.5, -weapon['grip'] / 16, -.5) @ S(1 / 16, 1 / 16, 1 / 16))
    faces = R.model_faces(model, tints, place)
    if glow is None: return faces
    out = []
    for corners, content in faces:
        if isinstance(content, Image.Image): out.append((corners, ('shadeimg', content, glow)))
        elif isinstance(content, tuple) and len(content) in (3, 4): out.append((corners, ('shadeimg', Image.new('RGBA', (1, 1), tuple(content[:3]) + (255,)), glow)))
        else: out.append((corners, content))
    return out


# ---------------------------------------------------------------- effects
def charge_glow(motion, t):
    """Sword glow from the charge levels: a flash at each level that settles to a held glow, red at full charge."""
    level, since = 0, None
    for e in motion['events']:
        if e['type'] == 'charge' and t >= e['t']: level, since = e['level'], t - e['t']
    impact = impact_at(motion)
    if level == 0 or t >= impact['t'] + .05: return None, 0
    held = {1: (25, 18, 4), 2: (45, 28, 6), 3: (70, 18, 4)}[level]
    flash = max(0.0, 1 - since / .18)
    return tuple(h + 90 * flash for h in held), flash


def impact_at(motion):
    return next((e for e in motion['events'] if e['type'] == 'impact'), {'t': 1e9, 'hitstop': 0})


REACH = {}


def reach_outline(label, attack):
    """The server's reach area of one attack (CoreWeaponAttacksTest export) as a ground polygon around the player."""
    if label not in REACH:
        REACH[label] = {w['name']: w for w in json.loads((ROOT / '.tools/weapon-reach.json').read_text())}[label]
    cells = REACH[label]['attacks'][attack]['cells']
    pts = np.array(cells, float) / 10
    poly = []
    for deg in range(-180, 180, 4):
        a = math.radians(deg)
        d = np.array([math.sin(a), math.cos(a)])
        proj = pts @ d
        lateral = np.abs(pts[:, 0] * d[1] - pts[:, 1] * d[0])
        near = proj[lateral < .08]
        r = near.max() if len(near) and near.max() > 0 else 0.0
        poly.append((d[0] * r, d[1] * r))
    return poly


VIEWS = {
    'side': ('横から（三人称）', lambda yaw: ((-3.6, 1.5, 1.6), (0, .9, .9))),
    'behind': ('背後（F5）', lambda yaw: ((0.0, 3.0, -3.76), (0, .25, 4.0))),
    'first': ('一人称（自分の目）', lambda yaw: ((0.0, EYE, 0.0), (0, EYE - .7, 3.0))),
}


def render(motion, skin, t, view):
    label, cam_fn = VIEWS[view]
    eye, target = cam_fn(0)
    imp = impact_at(motion)
    since_imp = t - imp['t']
    eye = np.array(eye, float)
    focal = R.FOCAL
    if view == 'first' and 'fp_grip' not in motion['tracks']:
        focal = (R.H / 2) / math.tan(math.radians(35))  # Minecraft's default 70 degree vertical field of view
        eye = np.array((0.0, EYE, 0.0)); target = (0, EYE - .55, 3.0)
    if view == 'first' and 'fp_grip' in motion['tracks']:
        # The charge narrows the view (in game: the movement-speed FOV), the impact snaps it back.
        charge_t = min(1.0, max(0.0, (t - .7) / 2.0)) if t < imp['t'] else max(0.0, 1 - since_imp / .2)
        focal = R.FOCAL * (1 + .18 * charge_t)
    if 0 <= since_imp < .35:  # impact shake (in game: the hurt-tilt the client plays for its own player)
        k = (1 - since_imp / .35) * .07
        eye = eye + np.array([math.sin(since_imp * 90) * k, math.sin(since_imp * 70 + 1) * k, 0])
    camera = R.Camera(eye, target)
    old = R.FOCAL; R.FOCAL = focal
    image = Image.new('RGBA', (R.W, R.H), R.BG + (255,))
    R.grid(image, camera)
    parts = motion_pose(motion, t)
    glow, flash = charge_glow(motion, t)
    faces = []
    weapon = motion.get('weapon', DEFAULT_WEAPON)
    is_weapon = lambda n: n == 'sword' or n.startswith('blade_')
    if view == 'first' and 'fp_grip' in motion['tracks']:
        # The owner never sees their puppet (it would sit around the camera): the held greatsword swings instead,
        # in camera space, with the same charge glow.
        tr = motion['tracks']
        g, b = sample(tr['fp_grip'], t, 'pos'), sample(tr['fp_blade'], t, 'dir')
        amp = shake_amp(motion, t) * .004
        g = g + np.array([math.sin(t * 61) * amp, math.sin(t * 97 + 1) * amp, 0])
        to_world = lambda v, w: camera.eye * w + v[0] * camera.r + v[1] * camera.u + v[2] * camera.f
        z = to_world(b, 0); z = z / np.linalg.norm(z)
        x = np.cross(camera.u, z); x = x / (np.linalg.norm(x) + 1e-9)
        held = np.eye(4); held[:3, 0], held[:3, 1], held[:3, 2], held[:3, 3] = x, np.cross(z, x), z, to_world(g, 1)
        faces += sword_faces(held @ S(.55, .55, .55), glow, weapon)
    elif view == 'first':
        # First person, as Better Combat shows it: the held weapons sweep through the lower half of the screen, in
        # front of a camera that never moves. The game can only move the held item (no arm), so: the weapons alone,
        # their root-space grip/blade carried into camera space around where Minecraft draws a held item.
        tr = motion['tracks']
        hands = [('r', 'grip_r', 'blade_r'), ('l', 'grip_l', 'blade_l')] if weapon['hands'] == 'dual' else [('r', 'grip', 'blade')]
        to_world = lambda v, w: camera.eye * w + v[0] * camera.r + v[1] * camera.u + v[2] * camera.f
        to_cam = lambda v: np.array([-v[0], v[1], v[2]])  # character right (-x) is screen right
        for side, gk, bk in hands:
            g = sample(tr[gk], t, 'pos')
            # fists ~0.8 m below the eye, pushed out in front of the camera
            gc = np.array([-g[0] * 1.1, g[1] - .78, g[2] * .9 + .55])
            # Same blade frame as third person (edge leads the cut), built in camera space and then placed.
            frame = blade_frame(tr[bk], np.diag([-1.0, 1.0, 1.0, 1.0]), t, np.zeros(3))
            cam_basis = np.column_stack((camera.r, camera.u, camera.f))
            held = np.eye(4)
            held[:3, :3] = cam_basis @ frame[:3, :3]
            if np.linalg.det(held[:3, :3]) < 0: held[:3, 0] = -held[:3, 0]  # the mirror flips handedness back
            held[:3, 3] = to_world(gc, 1)
            faces += sword_faces(held @ S(.7, .7, .7), glow, weapon)
    else:
        for name, m in parts.items():
            faces += sword_faces(m, glow, weapon) if is_weapon(name) else part_faces(name, m, skin)
    # A plain dummy 3 m ahead so the reach reads.
    lo, hi = (-.45, 0, 2.6), (.45, 2.0, 3.5)
    corners = [np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)]) for i in range(8)]
    faces += [([corners[i] for i in ids], ('shade', (92, 70, 66))) for _, ids in R.FACES]
    # The server's hit area flashes on the ground for a few frames after each hit.
    for e in motion['events']:
        if e['type'] == 'hit' and 0 <= t - e['t'] < .2 and motion.get('reach'):
            fade = 1 - (t - e['t']) / .2
            poly = [camera.cam(np.array((x, .02, z))) for x, z in reach_outline(motion['reach'], e['attack'])]
            poly = R.clip(poly)
            if len(poly) >= 3:
                layer = Image.new('RGBA', image.size, (0, 0, 0, 0))
                ImageDraw.Draw(layer).polygon([camera.screen(c) for c in poly], fill=(249, 115, 22, int(110 * fade)))
                image.alpha_composite(layer)
    R.draw_faces(image, camera, faces)
    R.FOCAL = old
    draw = ImageDraw.Draw(image)
    if 0 <= since_imp < .1:
        image.alpha_composite(Image.new('RGBA', image.size, (255, 244, 220, int(60 * (1 - since_imp / .1)))))
    level_now = max([e['level'] for e in motion['events'] if e['type'] == 'charge' and t >= e['t']] + [0])
    if view == 'first' and level_now and t < imp['t']:
        # First person cannot see the shouldered blade: the charge shows as a warm edge glow that steps up per level
        # and flashes at each step (in game: a full-screen font overlay).
        strength = {1: .25, 2: .45, 3: .7}[level_now] + .3 * flash
        colour = (255, 150, 40) if level_now < 3 else (255, 70, 30)
        yy, xx = np.mgrid[0:R.H, 0:R.W]
        d = np.minimum(np.minimum(xx, R.W - 1 - xx) / R.W, np.minimum(yy, R.H - 1 - yy) / R.H)
        a = np.clip((.16 - d) / .16, 0, 1) ** 1.6 * strength * 255
        image.alpha_composite(Image.fromarray(np.dstack([np.full_like(a, c) for c in colour] + [a]).astype('uint8')))
    level = max([e['level'] for e in motion['events'] if e['type'] == 'charge' and t >= e['t']] + [0])
    phase = next((p['name'] for p in reversed(motion.get('phases', [])) if t >= p['t']), '')
    if 0 <= since_imp < imp['hitstop']: phase = 'ヒットストップ'
    draw.text((10, 6), label, font=R.FONT, fill=(236, 226, 206))
    draw.text((10, 28), f'{t:.2f}秒　{phase}' + (f'　溜め{level}' if level and t < imp['t'] else ''), font=R.SMALL, fill=(190, 182, 170))
    return image.convert('RGB')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('motion', nargs='?', default='greatsword_charge')
    parser.add_argument('--skin', default=str(ROOT / '.tools/skin/assets/minecraft/textures/entity/player/wide/steve.png'))
    parser.add_argument('--out', default=str(ROOT / '.tools/motion'))
    args = parser.parse_args()
    motion = json.loads((MOTIONS / f'{args.motion}.json').read_text())
    skin = Image.open(args.skin).convert('RGBA')
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    frames = []
    n = int(motion['length'] * FPS) + 8
    for i in range(n):
        t = min(i / FPS, motion['length'])
        sheet = Image.new('RGB', (R.W * 3, R.H))
        for k, view in enumerate(('side', 'behind', 'first')):
            sheet.paste(render(motion, skin, t, view), (k * R.W, 0))
        frames.append(sheet)
    for name, ms in (('', round(1000 / FPS)), ('-slow', round(3000 / FPS))):
        frames[0].save(out / f'{args.motion}{name}.gif', save_all=True, append_images=frames[1:], duration=ms, loop=0)
    for t in motion.get('stills', []):
        frames[min(int(round(t * FPS)), len(frames) - 1)].save(out / f'{args.motion}-{t:.2f}.png')
    print(args.motion, len(frames), 'frames ->', out)


if __name__ == '__main__':
    main()
