"""First-person greatsword swings (2026-10-25, Creator: 「一人称で遊ぶから実際に剣を一人称で触れさえすればいい」).

Better Combat in first person moves only the held weapon, no arm, and that reads best. A server cannot move the
camera-space item freely, but it can swap the held model every tick: each frame is a child of the weapon model that
only overrides its first-person display transform. The item's own attack animation is off (attack_animation none), so
vanilla does not shake it on top.

Poses are authored the way the hand would hold the sword, in view space (x right, y up, z forward, blocks, from the
eye): the grip point, the blade's direction and the way its flat faces. solve() turns one into the display transform
that ItemInHandRenderer applies: arm offset (0.56, -0.52, -0.72), then translation / rotationXYZ / scale, then the
model recentred by -0.5 and drawn in 1/16 block pixels.
"""
import math
import numpy as np

ARM = np.array([0.56, -0.52, -0.72])          # applyItemArmTransform, right hand, no equip offset (view space, -z ahead)
GRIP = np.array([8.0, 4.5, 8.0])              # the hand on the leather handle, model pixels
SCALE = .72                                   # as the authored idle display
# Every model the greatsword is drawn with: model, the hand on its handle (pixels), the first-person scale it has.
# T1 is the plain sprite (build_plain_weapons.py: 80 px over 16, handle rows 59..72, the blade on column 40).
MODELS = {f'greatsword_t{t}': (f'projects:item/weapons/greatsword_t{t}', (8.0, 4.5, 8.0), .72) for t in (2, 3, 4)}
MODELS['greatsword_t1'] = ('projects:item/weapons/plain/plain_greatsword', (8.1, 3.0, 8.0), 1.133)
# Each model's own resting first-person display (the swing ends there, so the held item takes over without a jump).
RESTS = {name: {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, -1.5], 'scale': [.72] * 3} for name in MODELS}
RESTS['greatsword_t1'] = {'rotation': [0, -90, -20], 'translation': [1.13, 3.2, 1.13], 'scale': [1.133] * 3}


def _view(v):
    """Authoring space (z ahead) to Minecraft view space (-z ahead)."""
    return np.array([v[0], v[1], -v[2]], float)


def _unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def rotation_xyz(r):
    """JOML rotationXYZ: Rx(a) Ry(b) Rz(c), degrees."""
    a, b, c = (math.radians(x) for x in r)
    rx = np.array(((1, 0, 0), (0, math.cos(a), -math.sin(a)), (0, math.sin(a), math.cos(a))))
    ry = np.array(((math.cos(b), 0, math.sin(b)), (0, 1, 0), (-math.sin(b), 0, math.cos(b))))
    rz = np.array(((math.cos(c), -math.sin(c), 0), (math.sin(c), math.cos(c), 0), (0, 0, 1)))
    return rx @ ry @ rz


def euler_xyz(m):
    b = math.asin(max(-1.0, min(1.0, m[0, 2])))
    if abs(m[0, 2]) < .9999:
        a = math.atan2(-m[1, 2], m[2, 2]); c = math.atan2(-m[0, 1], m[0, 0])
    else:
        a = math.atan2(m[2, 1], m[1, 1]); c = 0.0
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def place(display, grip_px=GRIP):
    """Display transform -> (grip point, blade direction, flat normal) in authoring space. For checking the solver."""
    t = np.array(display['translation'], float) / 16
    r = rotation_xyz(display['rotation']); s = display['scale'][0]
    grip = ARM + t + r @ (s * (np.asarray(grip_px) / 16 - .5))
    flip = np.array([1, 1, -1])
    return grip * flip, (r @ [0, 1, 0]) * flip, (r @ [0, 0, 1]) * flip


def solve(grip, blade, flat, scale=SCALE, grip_px=GRIP):
    """The firstperson_righthand display for a grip point, blade direction and flat normal (authoring space)."""
    y = _unit(_view(blade))
    z = _view(flat); z = _unit(z - y * (z @ y))
    x = np.cross(y, z)
    r = np.column_stack((x, y, z))
    rot = euler_xyz(r)
    t = _view(grip) - ARM - r @ (scale * (np.asarray(grip_px) / 16 - .5))
    tr = [round(float(v) * 16, 3) for v in t]
    assert all(-80 <= v <= 80 for v in tr), tr
    return {'rotation': [round(v, 2) for v in rot], 'translation': tr, 'scale': [scale] * 3}


def _plane(a, b):
    return _unit(np.cross(_unit(a), _unit(b)))


IDLE = place({'rotation': [0, -90, 25], 'translation': [1.13, 3.2, -1.5], 'scale': [SCALE] * 3})
IDLE = {'grip': tuple(IDLE[0]), 'blade': tuple(IDLE[1]), 'flat': tuple(IDLE[2])}

# The heavy greatsword, read off Guts / Dragon's Dogma 2 / Monster Hunter (~/ProjectS/refs/greatsword): diagonal cuts,
# the hands lead and the blade whips after them, a dead stop on contact (hitstop), the ground stops the blade, then the
# weight is hauled into the next cut. The warrior hits on the press, so each beat starts from the pose the last one
# was hauled into. Keys: (tick, ease, pose); a pose names grip / blade / flat, missing ones carry over.
def on_screen(sx, sy, z):
    """A grip point from where it shows: sx, sy in -1..1 across the 16:9 screen at the hand FOV, z ahead (blocks)."""
    return (round(sx * 1.24 * z, 3), round(sy * .7 * z, 3), z)


B1_FROM, B1_TO = (-0.3, 0.75, -0.6), (-0.85, -0.35, 0.4)
B2_FROM, B2_TO = (-0.85, -0.4, 0.35), (0.75, 0.5, 0.42)
B3_FROM, B3_TO = (0.05, 0.7, -0.7), (0.03, -0.86, 0.5)
LOAD1 = dict(grip=on_screen(.75, -.3, .7), blade=B1_FROM, flat=tuple(_plane(B1_FROM, B1_TO)))
LOAD2 = dict(grip=on_screen(-.45, -.8, .65), blade=B2_FROM, flat=tuple(_plane(B2_FROM, B2_TO)))
LOAD3 = dict(grip=on_screen(.1, .6, .6), blade=B3_FROM, flat=tuple(_plane(B3_FROM, B3_TO)))
BEATS = {
    # 1: 袈裟, from over the right shoulder down across to the ground on the left
    1: [(0, None, LOAD1),
        (1, 'linear', dict(grip=on_screen(.35, -.5, .75), blade=(0.05, 0.97, 0.2))),
        (2, 'linear', dict(grip=on_screen(.1, -.6, .8), blade=(-0.72, 0.45, 0.52))),
        (3, 'linear', dict(grip=on_screen(-.05, -.7, .8), blade=B1_TO)),
        (5, None, 'hold'),
        (8, 'out', dict(grip=on_screen(-.25, -.85, .7), blade=(-0.7, -0.6, 0.38))),
        (11, None, 'hold'),
        (19, 'inout', LOAD2)],
    # 2: 逆袈裟, from the ground on the left up across to the right, carried on over the head
    2: [(0, None, LOAD2),
        (1, 'linear', dict(grip=on_screen(-.2, -.8, .75), blade=(-0.85, -0.45, 0.28))),
        (2, 'linear', dict(grip=on_screen(0, -.65, .8), blade=(0.0, 0.0, 1.0))),
        (3, 'linear', dict(grip=on_screen(.2, -.5, .8), blade=B2_TO)),
        (5, None, 'hold'),
        (9, 'out', dict(grip=on_screen(.5, -.2, .7), blade=(0.5, 0.82, -0.1))),
        (12, None, 'hold'),
        (21, 'inout', LOAD3)],
    # 3: 叩き込み, from over the head straight down into the ground ahead; it stays, then is heaved back to rest
    3: [(0, None, LOAD3),
        (1, 'linear', dict(grip=on_screen(.08, .45, .7), blade=(0.03, 0.97, 0.2))),
        (2, 'linear', dict(grip=on_screen(.05, 0, .85), blade=(0.02, 0.45, 0.89))),
        (3, 'linear', dict(grip=on_screen(.03, -.1, .92), blade=(0.02, -0.3, 0.95))),
        (4, 'linear', dict(grip=on_screen(.03, -.25, .95), blade=B3_TO)),
        (7, None, 'hold'),
        (14, 'out', dict(grip=on_screen(.03, -.35, .92), blade=(0.03, -0.9, 0.43))),
        (20, 'inout', dict(grip=on_screen(.5, -.4, .7), blade=(-0.1, 0.85, 0.5), flat=(-1, 0, 0))),
        (29, 'out', IDLE)],
}
# When no next press comes, the hauled pose settles back to rest.
RETURN_TICKS = 8
RETURNS = {b: [(0, None, BEATS[b][-1][2]), (RETURN_TICKS - 1, 'inout', IDLE)] for b in (1, 2)}


def _ease(kind, u):
    if kind == 'in': return u ** 3
    if kind == 'out': return 1 - (1 - u) ** 3
    if kind == 'inout': return u * u * (3 - 2 * u)
    return u


def _slerp(a, b, w):
    a, b = _unit(a), _unit(b)
    ang = math.acos(max(-1.0, min(1.0, float(a @ b))))
    if ang < 1e-5: return a
    return (math.sin((1 - w) * ang) * a + math.sin(w * ang) * b) / math.sin(ang)


def frames(keys):
    """Per-tick (grip, blade, flat) from keys; the flat keeps one sign so it never flips through the blade."""
    full, last = [], {}
    for t, ease, pose in keys:
        last = dict(last) if pose == 'hold' else {**last, **pose}
        full.append((t, ease, last))
    out = []
    for tick in range(full[-1][0] + 1):
        for (ta, _, pa), (tb, eb, pb) in zip(full, full[1:]):
            if ta <= tick <= tb: break
        u = 0.0 if tb == ta else _ease(eb, (tick - ta) / (tb - ta))
        fa, fb = np.array(pa['flat'], float), np.array(pb['flat'], float)
        if fa @ fb < 0: fb = -fb
        out.append((np.array(pa['grip']) * (1 - u) + np.array(pb['grip']) * u, _slerp(pa['blade'], pb['blade'], u),
                    _slerp(fa, fb, u)))
    return out


def clips(name='greatsword_t3'):
    """name -> list of firstperson_righthand displays, one per tick, for one of MODELS."""
    _, grip_px, scale = MODELS[name]
    g, b, f = place(RESTS[name], grip_px)
    rest = {'grip': tuple(g), 'blade': tuple(b), 'flat': tuple(f)}
    keys = {f'swing{b}': k for b, k in BEATS.items()} | {f'return{b}': k for b, k in RETURNS.items()}
    keys = {clip: [(t, e, rest if p is IDLE else p) for t, e, p in k] for clip, k in keys.items()}
    return {clip: [solve(*f, scale=scale, grip_px=grip_px) for f in frames(k)] for clip, k in keys.items()}


def frame_model(name, display):
    """A frame: the weapon model with only its first-person transforms replaced (left hand mirrored)."""
    r, t = display['rotation'], display['translation']
    return {'parent': MODELS[name][0], 'display': {
        'firstperson_righthand': display,
        'firstperson_lefthand': {'rotation': [r[0], -r[1], -r[2]], 'translation': [-t[0], t[1], t[2]], 'scale': display['scale']}}}


# custom_model_data[0] values: idle frames 0..11, prepare 12..17, release 18..23 (class_armament_geometry), then these.
# CoreArmamentPresentation mirrors CLIP_ORDER / the lengths; CoreArmamentPresentationTest checks them against the pack.
CLIP_ORDER = ('swing1', 'swing2', 'swing3', 'return1', 'return2')
FIRST_OFFSET = 24
HIDDEN = 112  # after the last clip (return2 ends at 111)


def offsets():
    out, at, lengths = {}, FIRST_OFFSET, {k: len(v) for k, v in clips().items()}
    for name in CLIP_ORDER:
        out[name] = at
        at += lengths[name]
    return out


def entries(name):
    """The custom_model_data dispatch entries for the swing frames of one of MODELS."""
    frames_of = clips(name)
    return [{'threshold': offset + i, 'model': {'type': 'minecraft:model', 'model': f'projects:item/weapons/{name}_{clip}_{i:02d}'}}
            for clip, offset in offsets().items() for i in range(len(frames_of[clip]))] + \
        [{'threshold': HIDDEN, 'model': {'type': 'minecraft:model', 'model': f'projects:item/weapons/{name}_fphidden'}},
         {'threshold': ANIM_POSE, 'model': {'type': 'minecraft:model', 'model': f'projects:item/weapons/{name}_anim',
          'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 16777215}]}}]


def files(name):
    """relative path -> json of every swing frame model of one of MODELS."""
    out = {f'models/item/weapons/{name}_{clip}_{i:02d}.json': frame_model(name, d)
           for clip, displays in clips(name).items() for i, d in enumerate(displays)}
    # While the viewmodel swings, the held sword is hidden in first person only (others and F5 still see it).
    out[f'models/item/weapons/{name}_fphidden.json'] = {'parent': MODELS[name][0], 'display': {
        'firstperson_righthand': {'scale': [0, 0, 0]}, 'firstperson_lefthand': {'scale': [0, 0, 0]}}}
    # The viewmodel's item: drawn in the fixed context with the hand on the handle at the display's origin, so the
    # display turns the sword about the hands (a swing), not about the middle of the blade.
    g = MODELS[name][1]
    out[f'models/item/weapons/{name}_vm.json'] = {'parent': MODELS[name][0], 'display': {
        'fixed': {'rotation': [0, 0, 0], 'translation': [round(8 - g[0], 3), round(8 - g[1], 3), round(8 - g[2], 3)], 'scale': [1, 1, 1]}}}
    out[f'models/item/weapons/{name}_anim.json'] = anim_model(name)
    out[f'items/weapons/{name}_vm.json'] = {'model': {'type': 'minecraft:model', 'model': f'projects:item/weapons/{name}_vm'},
                                            'hand_animation_on_swap': False}
    return out


# ---- the viewmodel (2026-10-25): the same poses drawn by an item display that rides the player, billboarded to the
# camera, so the client interpolates between ticks (a held item can only snap once a tick). Its local space is the
# view space above (x right, y up, z towards the eye), so a frame is translation (blocks, from the eye) + rotation +
# scale; the server subtracts where the display sits relative to the eye.
def _quaternion(m):
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = math.sqrt(t + 1) * 2; q = ((m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, s / 4)
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2; q = (s / 4, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s, (m[2, 1] - m[1, 2]) / s)
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2; q = ((m[0, 1] + m[1, 0]) / s, s / 4, (m[1, 2] + m[2, 1]) / s, (m[0, 2] - m[2, 0]) / s)
    else:
        s = math.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2; q = ((m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, s / 4, (m[1, 0] - m[0, 1]) / s)
    return q


def viewmodel_frame(display):
    # ItemDisplayRenderer turns the item half a turn about y before drawing it: undo that inside the rotation.
    r = rotation_xyz(display['rotation']) @ np.diag([-1.0, 1.0, -1.0])
    t = ARM + np.array(display['translation']) / 16
    return [round(float(v), 4) for v in (*t, *_quaternion(r))] + [display['scale'][0]]


def viewmodel():
    """model -> clip -> keys [u, tx, ty, tz, qx, qy, qz, qw, scale] (see VM_BEATS); 'idle' is the resting key."""
    out = {}
    for name in MODELS:
        out[f'projects:weapons/{name}'] = {f'swing{b}': vm_keys(name, b) for b in VM_BEATS} | {'idle': [vm_key(0.0, vm_rest(name), name)]}
    return out


# ---- Better Combat cadence (2026-10-25, Creator: 「手触りが全くBetter combatと一緒になるまで」). Read off Better
# Combat's claymore in first person (its own client, recorded at 60 fps; rules only, none of its data): the weapon alone
# moves, from the ordinary held pose on the right; the blade lands a quarter of the way into the swing, crosses the
# screen in two or three frames, is held past the target (off screen for the sweep), then comes back to rest. The three
# attacks are a sweep from the right, a thrust, an overhead slam. Keys are (u, ease, pose) over the whole swing (u = 1
# is the cooldown's end, impact at u = .25); 'size' scales the sword up while it is in front of the camera.
BC_STEPS = 48
# Third pass (2026-10-25, Creator: 「もっさり」「剣を振ってるというよりただ傾いてるだけ」). A swing is an arc: the blade
# points out from the chest and the whole sword goes round it, so the tip crosses the whole screen; with the hands
# pinned to the screen edge it only leaned. The pivot sits under the eye (the chest) and the hands ride the arc a
# forearm out from it, below the screen, so only the blade shows. Timing: the wind-up eases in, the cut is a few
# ticks of constant, fast turning, the follow-through slows to a stop past the target, then the sword comes back.
# The hands ride low (the pivot is the chest, well under the eye) so they stay under the screen; the cut passes
# through the blade pointing up and out, never straight into the screen, where it would shrink to a stub.
CHEST = (0.0, -0.72, 0.1)
REACH = .5


def _at(d, reach=REACH, pivot=None):
    pivot = CHEST if pivot is None else pivot
    return tuple(np.array(pivot) + np.asarray(d) * reach)


def _path(points, t0, t1, flat, size, first_ease='in', per=2, pivot=None):
    """Keys along a chain of directions, [per] keys a leg, evenly in time between t0 and t1."""
    legs = len(points) - 1
    keys = []
    for leg in range(legs):
        for i in range(1, per + 1):
            w = (leg + i / per) / legs
            d = _slerp(points[leg], points[leg + 1], i / per)
            keys.append((round(t0 + (t1 - t0) * w, 4), first_ease if not keys else 'linear',
                         dict(grip=_at(d, pivot=pivot), blade=tuple(d), flat=flat, size=size)))
    return keys


# Fourth pass (2026-10-25, Creator: 「ただゆっくり動いてるだけ」「突きはよくわからない」): the sword snaps into its
# cocked pose in two ticks and stays coiled there, then the whole cut crosses the screen in two ticks, decelerating
# through the follow-through; the time is spent holding still, not drifting. The second attack is a rising cut back.
# Keys are in u of the whole swing (20 ticks at normal speed: .05 a tick); the hit lands at u = .25, mid-cut.
def _cut(points, t0, t1, flat, size, pivot=None, per=3):
    return _path(points, t0, t1, flat, size, first_ease='linear', per=per, pivot=pivot)


def _pose(d, flat, size, pivot=None):
    return dict(grip=_at(d, pivot=pivot), blade=tuple(d), flat=flat, size=size)


# The cocked pose is on screen (the cut must start from something the eye already holds), and the cut spends its two
# and a half ticks where the blade shows, leaving the screen only in the follow-through.
_S = [_unit(v) for v in ((0.62, 0.62, 0.48), (0.25, 0.72, 0.65), (-0.2, 0.62, 0.76), (-0.62, 0.38, 0.69))]
_S_COCK, _S_OUT = _unit((0.72, 0.62, 0.32)), _unit((-1.0, 0.05, -0.1))
_SF = tuple(_unit(np.cross(_S[0], _S[3])))
_R = [_unit(v) for v in ((-0.62, 0.22, 0.75), (-0.22, 0.48, 0.85), (0.22, 0.66, 0.72), (0.55, 0.74, 0.39))]
_R_COCK, _R_OUT = _unit((-0.75, 0.12, 0.65)), _unit((0.62, 0.72, -0.3))
_RF = tuple(_unit(np.cross(_R[0], _R[3])))
_V = [_unit(v) for v in ((0.1, 0.97, 0.2), (0.05, 0.8, 0.6), (0.0, 0.48, 0.88), (-0.03, 0.15, 0.99))]
_V_COCK, _V_OUT = _unit((0.12, 0.95, -0.05)), _unit((-0.05, -0.35, 0.94))
SLAM_CHEST = (0.12, -0.92, 0.5)
_LOW = dict(grip=(0.32, -1.45, 0.35), blade=tuple(_unit((0.45, 0.0, 0.9))))
_RETURN = [(.62, None, 'hold'), (.8, 'inout', _LOW), (1.0, 'out', 'rest')]
BC_BEATS = {
    # sweep: up past the right shoulder in two ticks, coiled there, then right to left across the screen
    1: [(0.0, None, 'rest'),
        (.1, 'out', _pose(_S[0], _SF, 1.45)),
        (.2, 'inout', _pose(_S_COCK, _SF, 1.45)),
        *_cut([_S_COCK] + _S, .2, .32, _SF, 1.45),
        (.4, 'out', _pose(_S_OUT, _SF, 1.45)),
        *_RETURN],
    # rising cut: dropped to the left, then up and across to the upper right
    2: [(0.0, None, 'rest'),
        # dropped under the screen and brought up on the left, not slid across it (that read as slow drifting)
        (.06, 'in', dict(grip=(-0.1, -1.5, 0.35), blade=tuple(_unit((-0.4, -0.1, 0.9))), flat=_RF, size=1.45)),
        (.12, 'out', _pose(_R[0], _RF, 1.45)),
        (.2, 'inout', _pose(_R_COCK, _RF, 1.45)),
        *_cut([_R_COCK] + _R, .2, .32, _RF, 1.45),
        (.4, 'out', _pose(_R_OUT, _RF, 1.45)),
        *_RETURN],
    # slam: raised straight up, then over and down in front
    3: [(0.0, None, 'rest'),
        (.1, 'out', _pose(_V[0], (1, 0, 0), 1.3, SLAM_CHEST)),
        (.2, 'inout', _pose(_V_COCK, (1, 0, 0), 1.3, SLAM_CHEST)),
        *_cut([_V_COCK] + _V, .2, .31, (1, 0, 0), 1.3, SLAM_CHEST),
        (.42, 'out', _pose(_V_OUT, (1, 0, 0), 1.3, SLAM_CHEST)),
        (.66, None, 'hold'), (.84, 'inout', _LOW), (1.0, 'out', 'rest')],
}


def bc_clips(name='greatsword_t3'):
    """beat -> BC_STEPS frames (viewmodel_frame lists) spread over the whole swing, for one of MODELS."""
    _, grip_px, scale = MODELS[name]
    g, b, f = place(RESTS[name], grip_px)
    rest = {'grip': tuple(g), 'blade': tuple(b), 'flat': tuple(f), 'size': 1.0}
    out = {}
    for beat, keys in BC_BEATS.items():
        ticks = [(round(u * (BC_STEPS - 1)), e, rest if p == 'rest' else p) for u, e, p in keys]
        sizes = [(t, (rest if p == 'hold' else ({**rest, **p} if isinstance(p, dict) else rest)).get('size', 1.0)) for t, _, p in ticks]
        # 'hold' keeps the last size; resolve sizes in order
        resolved, last = [], 1.0
        for (t, _, p), (_, s) in zip(ticks, sizes):
            last = last if p == 'hold' else (p.get('size', last) if isinstance(p, dict) and p is not rest else s)
            resolved.append((t, last))
        poses = [(t, e, p if p == 'hold' else {k: v for k, v in p.items() if k != 'size'}) for t, e, p in ticks]
        clip = []
        for i, (grip, blade, flat) in enumerate(frames(poses)):
            for (ta, sa), (tb, sb) in zip(resolved, resolved[1:]):
                if ta <= i <= tb: break
            size = sa if tb == ta else sa + (sb - sa) * (i - ta) / (tb - ta)
            clip.append(viewmodel_frame(solve(grip, blade, flat, scale=round(scale * size, 4), grip_px=grip_px)))
        out[f'swing{beat}'] = clip
    return out





# ---- Fifth pass (2026-10-25, Creator: 「本当にこれでいいと思ってるのか」). Side by side with Better Combat at 30 fps:
# its sweep is the hilt travelling right across the bottom of the screen while the blade turns from pointing ahead to
# lying flat out to the left, all in about 200 ms, the hand on the handle in sight throughout. The earlier passes sent a
# pose every tick: the frames arriving unevenly made the sword stall and jump, and the white slash showed before the
# blade had moved. Now the server sends only the key poses, each with how many ticks the client is to take getting
# there, so the client draws the whole move smoothly; and the item turns about the hand (its _vm model puts the handle
# at the display's origin), so a key is just where the hand is and which way the blade points.
#
# Keys: (u of the whole swing, hand on screen (sx, sy in -1..1, z ahead), blade direction, size). u = .25 is when the
# server's hit lands: the blade must be crossing the middle by then. Each beat turns the blade in one plane.
VM_BEATS = {
    # sweep: hilt from bottom right to off the left, the blade from ahead-and-up to flat out to the left
    1: [(.1, (.62, -.72, .72), (0.62, 0.55, 0.56), 1.0),
        (.16, (.68, -.76, .7), (0.78, 0.5, 0.38), 1.0),
        (.21, (.18, -.8, .78), (0.05, 0.52, 0.85), 1.0),
        (.26, (-.42, -.84, .75), (-0.78, 0.28, 0.56), 1.0),
        (.31, (-1.05, -.95, .62), (-0.97, 0.0, 0.24), 1.0),
        (.4, (-1.5, -1.25, .5), (-0.9, -0.2, -0.35), 1.0)],
    # rising cut: hilt from bottom left up to off the right, the blade from low-left to high-right
    2: [(.1, (-.62, -.92, .72), (-0.82, -0.08, 0.56), 1.0),
        (.16, (-.7, -.98, .7), (-0.9, -0.15, 0.4), 1.0),
        (.21, (-.2, -.88, .78), (-0.35, 0.45, 0.82), 1.0),
        (.26, (.32, -.72, .78), (0.42, 0.75, 0.51), 1.0),
        (.31, (.85, -.5, .68), (0.62, 0.78, 0.05), 1.0),
        (.4, (1.35, -.3, .55), (0.55, 0.65, -0.5), 1.0)],
    # slam: hilt up in front of the face with the blade back over the head, then straight down through the middle
    3: [(.1, (.22, -.42, .7), (0.12, 0.9, -0.42), 1.0),
        (.17, (.18, -.32, .68), (0.1, 0.78, -0.62), 1.0),
        (.21, (.1, -.45, .78), (0.04, 0.92, 0.38), 1.0),
        (.25, (.04, -.78, .85), (0.0, 0.28, 0.96), 1.0),
        (.29, (.0, -1.0, .82), (-0.02, -0.3, 0.95), 1.0),
        (.4, (-.02, -1.12, .8), (-0.03, -0.42, 0.91), 1.0)],
}
VM_SIZE = 1.3          # times the model's held scale: in front of the camera the held size reads too small
VM_HOLD = {1: .62, 2: .62, 3: .66}   # held there until u, then dropped under the screen and back up into the guard


def vm_rest(name):
    """The held sword's resting first-person pose, as (hand, blade, flat)."""
    return place(RESTS[name], MODELS[name][1])


def vm_key(u, pose, name, size=None):
    """[u, translation (the hand, view space, from the eye), rotation quaternion, scale] for the display."""
    grip, blade, flat = pose
    y = _unit(_view(blade)); z = _view(flat); z = _unit(z - y * (z @ y)); x = np.cross(y, z)
    # ItemDisplayRenderer turns the item half a turn about y before drawing it: undo that inside the rotation.
    r = np.column_stack((x, y, z)) @ np.diag([-1.0, 1.0, -1.0])
    scale = MODELS[name][2] * (size if size is not None else 1.0)
    return [round(float(v), 4) for v in (u, *_view(grip), *_quaternion(r), scale)]


def vm_keys(name, beat):
    keys = VM_BEATS[beat]
    first, last = _unit(keys[0][2]), _unit(keys[-2][2])
    flat = tuple(_unit(np.cross(first, last)))
    rest = vm_rest(name)
    out = [vm_key(0.0, rest, name)]
    for u, hand, blade, size in keys:
        out.append(vm_key(u, (on_screen(*hand), _unit(blade), flat), name, size * VM_SIZE))
    hold = VM_HOLD[beat]
    out.append([hold] + out[-1][1:])
    low = (np.array((0.32, -1.45, 0.35)), _unit((0.45, 0.0, 0.9)), flat)
    out.append(vm_key(round(hold + (1 - hold) * .5, 3), low, name, VM_SIZE))
    out.append(vm_key(1.0, rest, name))
    return out



# ---- The held sword, moved by the item shader (2026-10-25, Creator chose it over the display in front of the eye: that
# one sank into walls, showed in F5 and could only be moved in straight lines). The server marks the held sword with a
# tint (custom_model_data colour 0): blue 84 + beat - 1 (+3 for the T1 sprite), red the swing's length in ticks, green
# the world tick it started on (mod 256). scripts/shaders/item.vsh, in the first-person hand only, takes each vertex
# back to the sword's own space through the held resting pose, then places it by the keys below at the frame's time
# (GameTime carries the part of the tick), so the swing is as smooth as the screen and eases where the keys say.
SHADER_EASE = {'linear': 0, 'in': 1, 'out': 2, 'inout': 3}
ANIM_POSE = 120         # custom_model_data[0] of the tinted, shader-moved held sword
SWING_MARKER = 84


SHADER_LIFT = .22    # screen heights the swing is raised in the hand (shader only)
SHADER_SIZE = 1.25   # times the held scale while swinging (shader only)
SHADER_LEAD = .05   # the blade crosses the middle a tick before the server's hit (and its slash) lands: cause first


def shader_keys(name, beat):
    """[u, ease, hand (view space), rotation quaternion of the sword's axes, scale] for one beat of one of MODELS."""
    rest = vm_rest(name)
    keys = VM_BEATS[beat]
    flat = tuple(_unit(np.cross(_unit(keys[0][2]), _unit(keys[-2][2]))))
    poses = [(0.0, None, rest, 1.0)]
    eases = ['out', 'inout', 'in', 'linear', 'out', 'out']
    for i, ((u, hand, blade, size), e) in enumerate(zip(keys, eases)):
        lead = SHADER_LEAD if 1 <= i <= 4 else 0.0
        # In the hand the sword read small and low on the right: brought up and nearer, and drawn larger.
        sx, sy, z = hand
        poses.append((round(u - lead, 3), e, (on_screen(sx * .9, sy + SHADER_LIFT, z * .88), _unit(blade), flat), size * SHADER_SIZE))
    hold = VM_HOLD[beat]
    poses.append((hold, 'linear', poses[-1][2], poses[-1][3]))
    poses.append((round(hold + (1 - hold) * .5, 3), 'inout', ((0.32, -1.45, 0.35), _unit((0.45, 0.0, 0.9)), flat), VM_SIZE))
    poses.append((1.0, 'out', rest, 1.0))
    out = []
    for u, e, (grip, blade, fl), size in poses:
        y = _unit(_view(blade)); z = _view(fl); z = _unit(z - y * (z @ y)); x = np.cross(y, z)
        q = _quaternion(np.column_stack((x, y, z)))
        # The flat T1 sprite is already drawn large in the hand; it is not scaled up further while swinging.
        grow = size if name != 'greatsword_t1' else 1.0
        out.append([u, SHADER_EASE[e or 'linear'], *_view(grip), *q, MODELS[name][2] * grow])
    return out


def shader_glsl():
    """The include item.vsh reads: the held resting pose of each sword kind and the keys of the three beats."""
    def vec(v): return 'vec3(' + ', '.join(f'{float(x):.5f}' for x in v) + ')'
    lines = ['// Generated by scripts/class_armament_swing.py (shader_glsl). Do not edit by hand.',
             '#ifndef PROJECTS_GREATSWORD_SWING_GLSL', '#define PROJECTS_GREATSWORD_SWING_GLSL', '']
    kinds = [('greatsword_t3', 'BIG'), ('greatsword_t1', 'PLAIN')]
    for name, tag in kinds:
        d = RESTS[name]
        r = rotation_xyz(d['rotation'])
        lines += [f'const vec3 SWING_{tag}_T = {vec(np.array(d["translation"]) / 16)};',
                  f'const mat3 SWING_{tag}_R = mat3({vec(r[:, 0])}, {vec(r[:, 1])}, {vec(r[:, 2])});',
                  f'const float SWING_{tag}_S = {d["scale"][0]:.5f};',
                  f'const vec3 SWING_{tag}_GRIP = {vec(np.array(MODELS[name][1]) / 16)};']
    lines += [f'const vec3 SWING_ARM = {vec(ARM)};', '']
    n = max(len(shader_keys(name, b)) for name, _ in kinds for b in VM_BEATS)
    lines.append(f'const int SWING_KEYS = {n};')
    for name, tag in kinds:
        for b in VM_BEATS:
            keys = shader_keys(name, b)
            keys += [keys[-1]] * (n - len(keys))
            assert all(len(k) == 10 for k in keys)
            lines.append(f'const float SWING_{tag}_{b}[{n * 10}] = float[{n * 10}](' + ', '.join(f'{float(v):.5f}' for k in keys for v in k) + ');')
    lines += ['', '#endif', '']
    return '\n'.join(lines)


def anim_model(name):
    """The shader-moved held sword: the weapon's model with every face tinted (the tint carries the swing)."""
    import json
    from pathlib import Path
    base = MODELS[name][0]
    if 'plain' in base:   # a generated sprite item: its layer is tinted as tintindex 0 already
        return {'parent': base}
    path = Path(__file__).resolve().parents[1] / 'server-minestom/src/main/resources/core-ui-pack/assets/projects/models' / (base.split(':')[1].replace('item/', 'item/', 1) + '.json')
    model = json.loads(path.read_text())
    for e in model.get('elements', []):
        for f in e['faces'].values(): f['tintindex'] = 0
    return model



# ---- Swing planes (2026-10-25, Creator: 「刃の角度とか動き方が剣を振ってる感じではない」). Keyed hand positions and
# blade directions made the blade lean on the spot, mostly pointing into the screen. A real two-handed cut turns the
# whole sword in one plane about the shoulders: from cocked behind the head, through the front where it strikes, on
# past it; the hands ride the same arc a forearm out, so the tip sweeps across the screen far more than the hilt; the
# edge leads, so the flat faces along the plane's normal. Each beat is that: cock (eased), a held coil, the cut
# accelerating into the strike and decelerating past it, a held follow-through, then back to rest.
# (pivot, cocked direction, strike direction, degrees on past the strike, hand reach) in authoring view space.
SWING_PLANES = {
    # 袈裟: down from over the right shoulder, across, to the lower left
    1: ((0.14, -0.62, 0.1), (0.42, 0.8, -0.42), (-0.12, 0.12, 1.0), 75.0, .5),
    # 逆袈裟: up from low behind the left hip, across, to over the right shoulder
    2: ((-0.04, -0.72, 0.1), (-0.72, -0.5, -0.3), (0.16, 0.14, 1.0), 75.0, .5),
    # 叩きつけ: over the head from behind and straight down in front
    3: ((0.1, -0.58, 0.12), (0.06, 0.62, -0.78), (0.0, 0.08, 1.0), 42.0, .5),
}
PLANE_TIMING = dict(cock=.13, coil=.17, strike=.21, through=.33, hold=.6, low=.8)


def _rot(axis, deg, v):
    a = math.radians(deg); k = _unit(axis); v = np.asarray(v, float)
    return v * math.cos(a) + np.cross(k, v) * math.sin(a) + k * (k @ v) * (1 - math.cos(a))


def plane_poses(beat):
    """[(u, ease, (hand, blade, flat), size)] for one beat, densely keyed through the cut."""
    pivot, cock, strike, past, reach = SWING_PLANES[beat]
    cock, strike = _unit(cock), _unit(strike)
    n = _unit(np.cross(cock, strike))
    to_strike = math.degrees(math.acos(max(-1.0, min(1.0, float(cock @ strike)))))
    total = to_strike + past
    pivot = np.array(pivot)
    def at(theta):
        d = _rot(n, theta, cock)
        return (tuple(pivot + d * reach), tuple(d), tuple(n))
    t = PLANE_TIMING
    out = [(t['cock'], 'out', at(-8.0), SHADER_SIZE), (t['coil'], 'linear', at(0.0), SHADER_SIZE)]
    # The cut: speed rises to its peak at the strike, then falls away through the follow-through (keys every ~12°).
    steps = 12
    for i in range(1, steps + 1):
        w = i / steps
        if w <= .6:   # accelerating to the strike
            f = (w / .6) ** 2 * to_strike
            u = t['coil'] + (t['strike'] - t['coil']) * (w / .6)
        else:         # decelerating past it
            g = (w - .6) / .4
            f = to_strike + past * (1 - (1 - g) ** 2)
            u = t['strike'] + (t['through'] - t['strike']) * g
        out.append((round(u, 4), 'linear', at(f), SHADER_SIZE))
    return out, n


def shader_keys(name, beat):
    """[u, ease, hand (view space), rotation quaternion of the sword's axes, scale] for one beat of one of MODELS."""
    rest = vm_rest(name)
    cut, n = plane_poses(beat)
    t = PLANE_TIMING
    poses = [(0.0, None, rest, 1.0)] + cut
    poses.append((t['hold'], 'linear', poses[-1][2], poses[-1][3]))
    poses.append((t['low'], 'inout', ((0.32, -1.45, 0.35), _unit((0.45, 0.0, 0.9)), tuple(n)), SHADER_SIZE))
    poses.append((1.0, 'out', rest, 1.0))
    out = []
    for u, e, (grip, blade, fl), size in poses:
        y = _unit(_view(blade)); z = _view(fl); z = _unit(z - y * (z @ y)); x = np.cross(y, z)
        q = _quaternion(np.column_stack((x, y, z)))
        grow = size if name != 'greatsword_t1' else 1.0
        out.append([u, SHADER_EASE[e or 'linear'], *_view(grip), *q, MODELS[name][2] * grow])
    return out



# ---- From the body (2026-10-25): the planes above, seen from the eye, still pointed the blade into the screen. The
# third-person combo (build_weapon_combo_motions.GS_KEYS, read off Guts / Dragon's Dogma 2 / Monster Hunter) already
# has where the hands and blade are for a heavy 袈裟, 逆袈裟 and 叩きつけ; first person is that seen from the player's
# eye: the character's root-space grip and blade, mirrored to screen right, the eye 1.62 above the feet and the root
# 0.70. Each beat takes the combo from its strike's coiled pose for 0.6 s, so the swing is the body's own.
EYE_ABOVE_ROOT = 1.62 - 12 * (.9375 / 16)


def combo_poses(beat):
    import build_weapon_combo_motions as B
    hit = B.GREATSWORD['hits'][beat - 1]
    full, last = [], {}
    for t, e, p in B.GS_KEYS:
        last = dict(last) if p == 'hold' else {**last, **p}
        full.append((t, e, last))
    out = []
    for t, e, p in full:
        if t < hit - 1e-6 or t > hit + .6: continue
        g, b, f = p['grip'], p['blade'], p['flat']
        hand = (-g[0], g[1] - EYE_ABOVE_ROOT, g[2])
        out.append((round(.17 + (t - hit), 4), 'linear' if e in (None, 'linear') else e,
                    (hand, tuple(_unit((-b[0], b[1], b[2]))), tuple(_unit((-f[0], f[1], f[2])))), SHADER_SIZE))
    return out


def shader_keys(name, beat):
    """[u, ease, hand (view space), rotation quaternion of the sword's axes, scale] for one beat of one of MODELS."""
    rest = vm_rest(name)
    cut = combo_poses(beat)
    t = PLANE_TIMING
    poses = [(0.0, None, rest, 1.0), (.13, 'out', cut[0][2], cut[0][3])] + cut
    if poses[-1][0] < t['hold']: poses.append((t['hold'], 'linear', poses[-1][2], poses[-1][3]))
    poses.append((t['low'], 'inout', ((0.32, -1.45, 0.35), _unit((0.45, 0.0, 0.9)), cut[-1][2][2]), SHADER_SIZE))
    poses.append((1.0, 'out', rest, 1.0))
    out = []
    for u, e, (grip, blade, fl), size in poses:
        y = _unit(_view(blade)); z = _view(fl); z = _unit(z - y * (z @ y)); x = np.cross(y, z)
        q = _quaternion(np.column_stack((x, y, z)))
        grow = size if name != 'greatsword_t1' else 1.0
        out.append([u, SHADER_EASE[e or 'linear'], *_view(grip), *q, MODELS[name][2] * grow])
    return out



# ---- Staged for the screen (2026-10-25). The body's own swing, seen from the eye, sends the sword off screen or into
# the camera. First-person melee games stage it (read off Mordhau's zweihander in first person): the hands stay low in
# the middle of the screen, the long blade stands up through the middle to the top edge in the guard, and a cut is
# the whole blade sweeping across the screen like a clock hand about the hands, in a plane leaning a little away
# from the eye, the hands sliding the other way under it. (pivot, plane's second axis, degrees: guard, coil, strike,
# end, hand reach) per beat; 0 degrees points to screen right, 90 straight up.
SCREEN_SWINGS = {
    # 袈裟: from up over the right shoulder, down across the middle to the lower left
    1: ((0.06, -0.62, 0.62), (0.0, 1.0, 0.45), 72.0, 40.0, 150.0, 215.0, .16),
    # 逆袈裟: from down at the lower left, up across the middle to the upper right
    2: ((-0.04, -0.66, 0.62), (0.0, 1.0, 0.45), 200.0, 228.0, 110.0, 20.0, .16),
    # 叩きつけ: raised high (the hands up at the top of the screen, the blade standing up behind them), then the hands
    # driven down through the middle to the bottom while the blade falls forward: it sweeps down the whole screen
    3: ((0.1, -0.6, 0.66), (-0.5, 0.0, 1.0), -18.0, -30.0, 62.0, 104.0, .16),
}


def screen_poses(beat):
    pivot, second, guard, coil, strike, end, reach = SCREEN_SWINGS[beat]
    pivot = np.array(pivot)
    if beat == 3:   # the slam turns in the vertical plane through the eye: up (0,1,0) .. ahead (0,0,1)
        a, b = np.array((0.0, 1.0, 0.0)), _unit(second)   # up .. ahead, leaning to the left so the fall reads
        dir_of = lambda deg: _unit(math.cos(math.radians(deg)) * a + math.sin(math.radians(deg)) * b)
        flat = tuple(_unit(np.cross(a, b)))
    else:
        x = np.array((1.0, 0.0, 0.0)); y = _unit(second)
        dir_of = lambda deg: _unit(math.cos(math.radians(deg)) * x + math.sin(math.radians(deg)) * y)
        flat = tuple(_unit(np.cross(x, y)))
    def at(deg):
        d = dir_of(deg)
        if beat == 3:   # the hands come down from the top of the screen as the blade falls
            w = min(1.0, max(0.0, (deg - coil) / (end - coil)))
            hands = np.array((0.05, 0.02, 0.5)) * (1 - w) + pivot * w
            return (tuple(hands), tuple(d), flat)
        # the hands slide opposite the tip, under the blade
        return (tuple(pivot - d * reach * .35 + d * reach), tuple(d), flat)
    t = PLANE_TIMING
    out = [(.12, 'out', at(guard), SHADER_SIZE), (t['coil'], 'inout', at(coil), SHADER_SIZE)]
    steps = 10
    for i in range(1, steps + 1):
        w = i / steps
        if w <= .55:
            deg = coil + (strike - coil) * (w / .55) ** 2
            u = t['coil'] + (t['strike'] - t['coil']) * (w / .55)
        else:
            g = (w - .55) / .45
            deg = strike + (end - strike) * (1 - (1 - g) ** 2)
            u = t['strike'] + (t['through'] - t['strike']) * g
        out.append((round(u, 4), 'linear', at(deg), SHADER_SIZE))
    return out


def shader_keys(name, beat):
    """[u, ease, hand (view space), rotation quaternion of the sword's axes, scale] for one beat of one of MODELS."""
    rest = vm_rest(name)
    cut = screen_poses(beat)
    t = PLANE_TIMING
    poses = [(0.0, None, rest, 1.0)] + cut
    poses.append((t['hold'], 'linear', poses[-1][2], poses[-1][3]))
    poses.append((t['low'], 'inout', ((0.32, -1.45, 0.35), _unit((0.45, 0.0, 0.9)), cut[-1][2][2]), SHADER_SIZE))
    poses.append((1.0, 'out', rest, 1.0))
    out = []
    for u, e, (grip, blade, fl), size in poses:
        y = _unit(_view(blade)); z = _view(fl); z = _unit(z - y * (z @ y)); x = np.cross(y, z)
        q = _quaternion(np.column_stack((x, y, z)))
        grow = size if name != 'greatsword_t1' else 1.0
        out.append([u, SHADER_EASE[e or 'linear'], *_view(grip), *q, MODELS[name][2] * grow])
    return out



# ---- Edge-on (2026-10-25, Creator: 「剣をふるときに剣がちゃんと平面に見えてしまうのがおかしい」). Mordhau's zweihander
# in first person, frame by frame side by side with ours: the blade is always seen on its edge, a thin line from the
# hands (low, a little right of centre) up to the top edge; through a cut it barely moves on the screen, it turns in
# depth, because the cut's plane passes through the eye (the camera turns with the body and the world smears past).
# Ours swept a flat blade across the screen like a clock hand, its broad face to the camera. Now each cut turns the
# blade in a plane through the eye and the hands, tilted only a little so the tip still travels: the flat is the plane's
# normal (the edge leads) and the eye sees the blade on its edge. The slash effect carries the sweep across the screen.
# (hands at the guard, hands at the strike, screen direction of the cut's line, tilt degrees, blade angles: cocked,
# strike, end; 0 = along the line, 90 = straight ahead into the scene)
EDGE_SWINGS = {
    # 袈裟: the line runs from upper right to lower left; the sword slides down it, turning from up-back to down-ahead
    1: ((0.14, -0.5, 0.8), (-0.22, -0.7, 0.82), (0.62, 1.0), 16.0, 22.0, 55.0, 98.0),
    # 逆袈裟: from lower left up to upper right
    2: ((-0.2, -0.7, 0.82), (0.16, -0.48, 0.8), (-0.75, -1.0), 16.0, 28.0, 58.0, 100.0),
    # 叩きつけ: from high in the middle straight down
    3: ((0.06, -0.24, 0.78), (0.02, -0.66, 0.84), (0.1, 1.0), 10.0, 18.0, 55.0, 92.0),
}
EDGE_SIZE = 1.6


def edge_poses(beat):
    g0, g1, line, tilt, cock, strike, end = EDGE_SWINGS[beat]
    g0, g1 = np.array(g0), np.array(g1)
    sdir = _unit((line[0], line[1], 0.0))
    t = PLANE_TIMING
    def at(theta, w):
        hand = g0 * (1 - w) + g1 * w
        # the plane through the eye and the hands holding the screen line: its in-depth axis
        ahead = _unit(hand - sdir * (hand @ sdir))
        ahead = _rot(sdir, tilt, ahead)
        d = _unit(math.cos(math.radians(theta)) * sdir + math.sin(math.radians(theta)) * ahead)
        flat = _unit(np.cross(sdir, ahead))
        return (tuple(hand), tuple(d), tuple(flat))
    out = [(.12, 'out', at(cock * .8, 0.0), EDGE_SIZE), (t['coil'], 'inout', at(cock, 0.0), EDGE_SIZE)]
    steps = 10
    for i in range(1, steps + 1):
        w = i / steps
        if w <= .55:
            th = cock + (strike - cock) * (w / .55) ** 2
            u = t['coil'] + (t['strike'] - t['coil']) * (w / .55)
        else:
            gg = (w - .55) / .45
            th = strike + (end - strike) * (1 - (1 - gg) ** 2)
            u = t['strike'] + (t['through'] - t['strike']) * gg
        out.append((round(u, 4), 'linear', at(th, min(1.0, w / .55)), EDGE_SIZE))
    return out


def shader_keys(name, beat):
    """[u, ease, hand (view space), rotation quaternion of the sword's axes, scale] for one beat of one of MODELS."""
    rest = vm_rest(name)
    cut = edge_poses(beat)
    t = PLANE_TIMING
    poses = [(0.0, None, rest, 1.0)] + cut
    poses.append((t['hold'], 'linear', poses[-1][2], poses[-1][3]))
    poses.append((t['low'], 'inout', ((0.32, -1.45, 0.35), _unit((0.45, 0.0, 0.9)), cut[-1][2][2]), SHADER_SIZE))
    poses.append((1.0, 'out', rest, 1.0))
    out = []
    for u, e, (grip, blade, fl), size in poses:
        y = _unit(_view(blade)); z = _view(fl); z = _unit(z - y * (z @ y)); x = np.cross(y, z)
        q = _quaternion(np.column_stack((x, y, z)))
        grow = size if name != 'greatsword_t1' else 1.0
        out.append([u, SHADER_EASE[e or 'linear'], *_view(grip), *q, MODELS[name][2] * grow])
    return out


if __name__ == '__main__':
    import json
    from pathlib import Path
    path = Path(__file__).resolve().parents[1] / 'server-minestom/src/main/resources/player-motions/greatsword_viewmodel.json'
    path.write_text(json.dumps(viewmodel(), separators=(',', ':')) + '\n', encoding='utf-8')
    glsl = Path(__file__).resolve().parent / 'shaders/greatsword_swing.glsl'
    glsl.write_text(shader_glsl(), encoding='utf-8')
    print('wrote', path, glsl)
