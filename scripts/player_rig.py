"""The player puppet as a Blockbench model (2026-10-19, Creator: animate the motions by hand in Blockbench).

    python scripts/player_rig.py build greatsword_charge dual_blades_dance   # write/refresh the .bbmodel drafts
    python scripts/preview_player_motion.py greatsword_charge                 # GIF from the saved .bbmodel

assets/player-motions/<weapon>.bbmodel is the source of truth once someone edits it in Blockbench: `build` writes a
first draft whose animations are baked from the old IK keyframes, and refuses to overwrite a model that already
exists unless --force is given. The skin on the model is a plain mannequin drawn here (no Mojang art in the repo);
the game puts the player's own skin on the same boxes.

Rig (Bedrock format, 16 px = 1 block, front +Z, feet at y=0, the vanilla 32 px player before its 0.9375 scale):
root (hips) > torso > head / arm_*_up > arm_*_low > weapon_*, and root > leg_*_up > leg_*_low. Pure FK: what
Blockbench shows is what plays. Rotations follow Blockbench's Bedrock convention, applied Z then Y then X with X
and Y mirrored (a positive X lifts the +Z end, as the boss pipeline measured). Keep root positions to y and z.
"""
import argparse
import json
import math
import uuid
from pathlib import Path

import numpy as np

import boss_models

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets/player-motions'
K = .9375 / 16

# bone: (parent, pivot px, cube from, cube to, skin box, rows of the box's sides)
BONES = {
    'root': (None, (0, 12, 0), None, None, None, None),
    'torso': ('root', (0, 12, 0), (-4, 12, -2), (4, 24, 2), 'torso', (0, 12)),
    'head': ('torso', (0, 24, 0), (-4, 24, -4), (4, 32, 4), 'head', (0, 8)),
    'arm_r_up': ('torso', (-5, 22, 0), (-8, 18, -2), (-4, 24, 2), 'arm_r', (0, 6)),
    'arm_r_low': ('arm_r_up', (-6, 18, 0), (-8, 12, -2), (-4, 18, 2), 'arm_r', (6, 12)),
    'weapon_r': ('arm_r_low', (-6, 12.5, 0), None, None, None, None),
    'arm_l_up': ('torso', (5, 22, 0), (4, 18, -2), (8, 24, 2), 'arm_l', (0, 6)),
    'arm_l_low': ('arm_l_up', (6, 18, 0), (4, 12, -2), (8, 18, 2), 'arm_l', (6, 12)),
    'weapon_l': ('arm_l_low', (6, 12.5, 0), None, None, None, None),
    'leg_r_up': ('root', (-2, 12, 0), (-4, 6, -2), (0, 12, 2), 'leg_r', (0, 6)),
    'leg_r_low': ('leg_r_up', (-2, 6, 0), (-4, 0, -2), (0, 6, 2), 'leg_r', (6, 12)),
    'leg_l_up': ('root', (2, 12, 0), (0, 6, -2), (4, 12, 2), 'leg_l', (0, 6)),
    'leg_l_low': ('leg_l_up', (2, 6, 0), (0, 0, -2), (4, 6, 2), 'leg_l', (6, 12)),
}
# Placeholder weapons for the editor only (the preview and the game draw the real item model at weapon_*).
WEAPON_CUBES = {
    'greatsword': [('handle', (-0.6, -0.6, -3.5), (0.6, 0.6, 3.5)), ('guard', (-0.9, -3.2, 3.5), (0.9, 3.2, 5)),
                   ('blade', (-0.8, -2.3, 5), (0.8, 2.3, 24))],
    'dagger': [('handle', (-0.6, -0.6, -2.5), (0.6, 0.6, 2.5)), ('blade', (-0.6, -1.9, 3), (0.6, 1.9, 17))],
}
WEAPONS = {'greatsword_charge': ('greatsword', ['weapon_r']), 'greatsword_combo': ('greatsword', ['weapon_r']),
           'dual_blades_dance': ('dual_blades', ['weapon_r', 'weapon_l'])}
MODEL_WEAPON = {'greatsword': 'greatsword', 'dual_blades': 'dagger'}


# ---------------------------------------------------------------- Blockbench rotation convention
def R(a, b, c):
    """Bone rotation [x, y, z] in degrees -> 3x3: Rz(c) Ry(-b) Rx(-a)."""
    a, b, c = (math.radians(v) for v in (-a, -b, c))
    ca, sa, cb, sb, cc, sc = math.cos(a), math.sin(a), math.cos(b), math.sin(b), math.cos(c), math.sin(c)
    rx = np.array(((1, 0, 0), (0, ca, -sa), (0, sa, ca)))
    ry = np.array(((cb, 0, sb), (0, 1, 0), (-sb, 0, cb)))
    rz = np.array(((cc, -sc, 0), (sc, cc, 0), (0, 0, 1)))
    return rz @ ry @ rx


def euler(m, near=None):
    """Inverse of R, choosing the solution (and 360 degree wraps) closest to the previous key."""
    b1 = math.asin(max(-1.0, min(1.0, -m[2, 0])))
    sols = []
    for b in (b1, math.pi - b1):
        cb = math.cos(b)
        if abs(cb) < 1e-6: continue
        a = math.atan2(m[2, 1] / cb, m[2, 2] / cb)
        c = math.atan2(m[1, 0] / cb, m[0, 0] / cb)
        sols.append(np.degrees((-a, -b, c)))
    if near is None: return sols[0]
    best = None
    for s in sols:
        s = np.array([v + 360 * round((n - v) / 360) for v, n in zip(s, near)])
        if best is None or np.abs(s - near).sum() < np.abs(best - near).sum(): best = s
    return best


# ---------------------------------------------------------------- sampling a saved model
def load(name):
    return json.loads((SOURCE / f'{name}.bbmodel').read_text())


def animation(model, name):
    for a in model['animations']:
        if a['name'] == name: return a
    raise KeyError(name)


def _channel(keys, t):
    keys = sorted(keys, key=lambda k: k['time'])
    val = lambda k: np.array([float(k['data_points'][0][c]) for c in 'xyz'])
    if t <= keys[0]['time']: return val(keys[0])
    for i, (a, b) in enumerate(zip(keys, keys[1:])):
        if t <= b['time']:
            u = (t - a['time']) / max(b['time'] - a['time'], 1e-9)
            mode = b.get('interpolation', 'linear') if a.get('interpolation') != 'step' else 'step'
            if mode == 'step': return val(a)
            if mode == 'catmullrom':
                p0 = val(keys[i - 1]) if i > 0 else val(a)
                p3 = val(keys[i + 2]) if i + 2 < len(keys) else val(b)
                p1, p2 = val(a), val(b)
                return .5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3)
            return val(a) * (1 - u) + val(b) * u
    return val(keys[-1])


def pose(model, anim, t):
    """World frames (4x4, blocks) at each bone's pivot, like preview_player_motion.pose()."""
    animators = {a['name']: a for a in anim['animators'].values()}
    px = {}
    for name, (parent, pivot, *_) in BONES.items():
        rot, pos = np.zeros(3), np.zeros(3)
        if name in animators:
            keys = animators[name]['keyframes']
            r = [k for k in keys if k['channel'] == 'rotation']; p = [k for k in keys if k['channel'] == 'position']
            if r: rot = _channel(r, t)
            if p: pos = _channel(p, t)
        m = np.eye(4); m[:3, :3] = R(*rot)
        local = np.eye(4); local[:3, 3] = np.array(pivot, float) + pos
        local = local @ m
        if parent is None:
            px[name] = local
        else:
            pp = np.array(BONES[parent][1], float)
            off = np.eye(4); off[:3, 3] = -pp
            px[name] = px[parent] @ off @ local
    out = {}
    for name, m in px.items():
        w = m.copy(); w[:3, 3] *= K
        out[name] = w
    return out


# ---------------------------------------------------------------- building a draft
def mannequin_skin():
    """A plain 64x64 player-layout skin: grey body, darker legs, a visor band, so front and back read in Blockbench."""
    px = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
    def fill(x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w): px[y][x] = c + (255,)
    def box(u, v, w, h, d, side, top, front=None):
        fill(u + d, v, w, d, top); fill(u + d + w, v, w, d, top)
        fill(u, v + d, d, h, side); fill(u + d, v + d, w, h, front or side)
        fill(u + d + w, v + d, d, h, side); fill(u + 2 * d + w, v + d, w, h, side)
    box(0, 0, 8, 8, 8, (176, 168, 156), (196, 188, 176), (176, 168, 156)); fill(8, 11, 8, 2, (60, 52, 50))
    box(16, 16, 8, 12, 4, (140, 120, 104), (160, 140, 124), (150, 72, 52))
    for u, v in ((40, 16), (32, 48)): box(u, v, 4, 12, 4, (164, 150, 136), (180, 166, 150))
    for u, v in ((0, 16), (16, 48)): box(u, v, 4, 12, 4, (92, 86, 96), (110, 104, 114))
    return px


def face_uvs(skin_box, rows, size):
    (u, v), (w, h, d) = {'head': ((0, 0), (8, 8, 8)), 'torso': ((16, 16), (8, 12, 4)), 'arm_r': ((40, 16), (4, 12, 4)),
                         'arm_l': ((32, 48), (4, 12, 4)), 'leg_r': ((0, 16), (4, 12, 4)), 'leg_l': ((16, 48), (4, 12, 4))}[skin_box]
    r0, r1 = rows
    faces = {'east': [u + d + w, v + d + r0, u + 2 * d + w, v + d + r1], 'south': [u + d, v + d + r0, u + d + w, v + d + r1],
             'west': [u, v + d + r0, u + d, v + d + r1], 'north': [u + 2 * d + w, v + d + r0, u + 2 * d + 2 * w, v + d + r1],
             'up': [u + d, v, u + d + w, v + d], 'down': [u + d + w, v + d, u + d + 2 * w, v]}
    return {k: {'uv': f, 'texture': 0} for k, f in faces.items()}


def bake(motion_name, skip=0.05):
    """FK keys from the old IK keyframes at every original key time and every server tick (0.05 s) between, so the
    eased IK motion survives the per-key interpolation. Thin them out in Blockbench as you rework a move."""
    import preview_player_motion as P
    motion = json.loads((P.MOTIONS / f'{motion_name}.json').read_text())
    times = sorted({round(k['t'], 3) for tr in motion['tracks'].values() for k in tr} | {0.0, motion['length']})
    dense = []
    for a, b in zip(times, times[1:]):
        n = max(1, math.ceil((b - a) / skip))
        dense += [round(a + (b - a) * i / n, 3) for i in range(n)]
    dense.append(times[-1])
    weapon_of = {'sword': 'weapon_r', 'blade_r': 'weapon_r', 'blade_l': 'weapon_l'}
    tracks, prev = {}, {}
    for t in dense:
        world = P.pose(motion, t)
        world = {weapon_of.get(k, k): v for k, v in world.items()}
        world['root'] = world['torso'].copy()  # placeholder, fixed below
        # The IK rig's root is its own frame: recover it from the torso's parent chain.
        rp = P.sample(motion['tracks']['root'], t, 'pos'); rr = P.sample(motion['tracks']['root'], t, 'rot')
        world['root'] = P.T(rp[0], 12 * K + rp[1], rp[2]) @ P.rot(*rr)
        for name, (parent, pivot, *_) in BONES.items():
            if name not in world: continue
            local = world[name][:3, :3] if parent is None else world[parent][:3, :3].T @ world[name][:3, :3]
            e = euler(local, prev.get(name))
            prev[name] = e
            tracks.setdefault(name, []).append((t, [round(float(v), 2) for v in e]))
            if name == 'root':
                tracks.setdefault('root_pos', []).append((t, [0.0, round(rp[1] / K, 2), round(rp[2] / K, 2)]))
    return motion, tracks


def build(name, motions, force=False):
    path = SOURCE / f'{name}.bbmodel'
    if path.exists() and not force:
        raise SystemExit(f'{path} exists (edited in Blockbench?). Use --force to overwrite it.')
    model = boss_models.Model(name, size=64)
    model.pixels = mannequin_skin()
    weapon = MODEL_WEAPON[name]
    groups = {}
    for bone, (parent, pivot, frm, to, skin_box, rows) in BONES.items():
        children = []
        if frm:
            ident = model.uid()
            model.elements.append({'name': bone, 'box_uv': False, 'rescale': False, 'locked': False, 'from': list(frm),
                'to': list(to), 'autouv': 0, 'color': 0, 'origin': list(pivot), 'faces': face_uvs(skin_box, rows, 64),
                'type': 'cube', 'uuid': ident})
            children.append(ident)
        if bone.startswith('weapon') and (bone == 'weapon_r' or weapon == 'dagger'):
            for part, lo, hi in WEAPON_CUBES[weapon]:
                ident = model.uid()
                lo = [pivot[i] + lo[i] for i in range(3)]; hi = [pivot[i] + hi[i] for i in range(3)]
                model.elements.append({'name': f'{bone}_{part}', 'box_uv': False, 'rescale': False, 'locked': False,
                    'from': lo, 'to': hi, 'autouv': 0, 'color': 1, 'origin': list(pivot),
                    'faces': {s: {'uv': [56, 56, 58, 58], 'texture': 0} for s in ('north', 'south', 'east', 'west', 'up', 'down')},
                    'type': 'cube', 'uuid': ident})
                children.append(ident)
        groups[bone] = model.bone(bone, pivot, children)
    for bone, (parent, *_) in BONES.items():
        if parent: groups[parent]['children'].append(groups[bone])
    for mname in motions:
        motion, tracks = bake(mname)
        frames = {bone: [(t, xyz) for t, xyz in keys] for bone, keys in tracks.items() if bone != 'root_pos'}
        frames['root'] = frames['root'] + [(t, xyz, 'position') for t, xyz in tracks['root_pos']]
        model.anim(mname, motion['length'], frames)
    # A light dark-grey for the placeholder weapon pixels.
    for y in range(56, 58):
        for x in range(56, 58): model.pixels[y][x] = (120, 126, 136, 255)
    # Written directly, not through boss_models.Model.write: the boss validator rejects a bone named "head" (WSEE
    # treats it specially) and wants idle/walk loops; this rig is not played by WSEE.
    data = {'meta': {'format_version': '4.10', 'model_format': 'bedrock', 'box_uv': False},
            'name': name, 'model_identifier': name, 'visible_box': [2, 3, 0],
            'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [],
            'unhandled_root_fields': {}, 'resolution': {'width': 64, 'height': 64},
            'elements': model.elements, 'outliner': [groups['root']],
            'textures': [model.texture_entry(f'{name}_mannequin.png', '0', model.pixels)], 'animations': model.animations}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return path, data


def add(name, motion_name):
    """Bake one motion into an existing .bbmodel as an animation of its own (replacing one of the same name), leaving
    the model and its other, perhaps hand-edited, animations as they are."""
    path = SOURCE / f'{name}.bbmodel'
    data = json.loads(path.read_text())
    uuids = {}
    def walk(node):
        if isinstance(node, dict):
            uuids[node['name']] = node['uuid']
            for c in node.get('children', []): walk(c)
    for n in data['outliner']: walk(n)
    model = boss_models.Model(name, size=64)
    model.bones = uuids
    motion, tracks = bake(motion_name)
    frames = {bone: [(t, xyz) for t, xyz in keys] for bone, keys in tracks.items() if bone != 'root_pos'}
    frames['root'] = frames['root'] + [(t, xyz, 'position') for t, xyz in tracks['root_pos']]
    model.anim(motion_name, motion['length'], frames)
    data['animations'] = [a for a in data['animations'] if a['name'] != motion_name] + model.animations
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('cmd', choices=['build', 'add'])
    ap.add_argument('motions', nargs='+')
    ap.add_argument('--force', action='store_true')
    args = ap.parse_args()
    if args.cmd == 'add':
        for m in args.motions: print('added to', add(WEAPONS[m][0], m).relative_to(ROOT), m)
        return
    by_model = {}
    for m in args.motions: by_model.setdefault(WEAPONS[m][0], []).append(m)
    for name, motions in by_model.items():
        path, _ = build(name, motions, args.force)
        print('wrote', path.relative_to(ROOT), motions)


if __name__ == '__main__':
    main()
