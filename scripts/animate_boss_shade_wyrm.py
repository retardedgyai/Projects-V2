"""Motion for the 黒翳竜 (shade wyrm): writes the walk, enraged and enrage clips into assets/bosses/shade_wyrm.bbmodel.

    python scripts/animate_boss_shade_wyrm.py   # run after build_boss_shade_wyrm.py (the build calls it)

The walk is the "noble" prowl the Creator chose (2026-10-16): a slow lateral-sequence walk (left hind,
left fore, right hind, right fore), the feet planted on the ground by a two-bone IK in each leg's side
plane, the front paws lifted high with the wrist folded under and set down softly, the neck arched and
the head carried high and still, the body barely moving, the tail swaying late. walk_enraged is the
same gait in the enraged crouch.

The enraged crouch: the body pitches front-down about the hips until the planted wing-arms' carpals reach
the ground (the hind legs stay put), the neck lifts against it. enrage is the moment it takes that stance,
after the Wilds ecology footage the Creator picked (2026-10-16): it crouches and wraps itself in its folded
wings, trembling; rears up on the hind legs with the wing-arms thrown overhead as the fur fans burst open and
the horns grow, roaring; swings the fans out and down; falls forward and slams the wing-arms into the ground.
The fans and horns grow by scale keys (the server shows those bones partway in), the folded wings shrink
away (the server hides them when the clip ends).

Angles here are Blockbench editor angles (R = Rz Ry Rx, right-handed: +x lowers the +Z end, +z raises
the +X side). Keyframes are written in the Bedrock 4.x animation convention the server checks
(ShadeWyrmBossTest): raw = (-x, y, z).
"""
import json
import math
import uuid
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "assets/bosses/shade_wyrm.bbmodel"
LEGS = {"fl": ("leg_fl", "leg_fl_fore", "leg_fl_paw"), "fr": ("leg_fr", "leg_fr_fore", "leg_fr_paw"),
        "hl": ("leg_hl", "leg_hl_shin", "leg_hl_foot"), "hr": ("leg_hr", "leg_hr_shin", "leg_hr_foot")}
O3 = np.ones(3)


def _rx(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler(v):
    r = np.radians(v); return _rz(r[2]) @ _ry(r[1]) @ _rx(r[0])


class Skeleton:
    def __init__(self, data):
        self.bones, self.order = {}, []

        def walk(n, parent):
            self.bones[n["name"]] = dict(pivot=np.array(n["origin"], float), rot=np.array(n.get("rotation", [0, 0, 0]), float),
                                         parent=parent, uuid=n["uuid"])
            self.order.append(n["name"])
            for c in n["children"]:
                if isinstance(c, dict):
                    walk(c, n["name"])
        for n in data["outliner"]:
            walk(n, None)

    def mats(self, pose):
        out = {}
        for name in self.order:
            b = self.bones[name]
            r, pos, *_ = pose.get(name, (np.zeros(3), np.zeros(3)))
            R = euler(b["rot"] + r)
            L = np.eye(4); L[:3, :3] = R; L[:3, 3] = b["pivot"] + pos - R @ b["pivot"]
            out[name] = out[b["parent"]] @ L if b["parent"] else L
        return out

    def point(self, bone, pose=None):
        M = self.mats(pose or {})[bone]
        return M[:3, :3] @ self.bones[bone]["pivot"] + M[:3, 3]


def add(pose, bone, rot=(0, 0, 0), pos=(0, 0, 0)):
    r0, p0, *sc = pose.get(bone, (np.zeros(3), np.zeros(3)))
    pose[bone] = (r0 + np.array(rot, float), p0 + np.array(pos, float), *sc)


def scale(pose, bone, k):
    r0, p0, *_ = pose.get(bone, (np.zeros(3), np.zeros(3)))
    pose[bone] = (r0, p0, np.ones(3) * max(.01, k))


def foot_offset(phase, duty, stride, lift, high):
    """(dy, dz) of a foot target from rest. Stance: slides back with the ground; swing: arcs forward.
    high: the paw rises early, reaches, and is set down softly at the end."""
    if phase < duty:
        return 0.0, stride * (.5 - phase / duty)
    f = (phase - duty) / (1 - duty)
    if high:
        y = lift * math.sin(math.pi * min(1, f * 1.25)) if f < .8 else lift * .3 * (1 - f) / .2
        return y, stride * (-.5 * math.cos(math.pi * min(1, f * 1.1)))
    return lift * math.sin(math.pi * f), stride * (-.5 * math.cos(math.pi * f))


def swing_curl(phase, duty):
    if phase < duty:
        return 0.0
    f = (phase - duty) / (1 - duty)
    return math.sin(math.pi * min(1, f * 1.4)) if f < .72 else 0.0


def solve_legs(sk, pose, targets, lateral=False):
    """Two-bone IK in each leg's y-z plane; the paw counter-rotates to stay flat.
    lateral: first roll the whole leg about its root (z) so the foot also reaches a sideways target exactly."""
    M = sk.mats(pose)
    for key, (r, m, e) in LEGS.items():
        R0, j1, j2 = sk.bones[r]["pivot"], sk.bones[m]["pivot"], sk.bones[e]["pivot"]
        L1 = np.linalg.norm((j1 - R0)[1:]); L2 = np.linalg.norm((j2 - j1)[1:])
        P = M[sk.bones[r]["parent"]]
        d = np.linalg.inv(P[:3, :3]) @ (targets[key] - P[:3, 3]) - R0
        roll = turn = 0.0
        if lateral:
            r0 = j2 - R0
            if key.startswith("f"):           # fore feet hang below the shoulder: roll the leg sideways (about z)
                roll = math.atan2(d[0], -d[1]) - math.atan2(r0[0], -r0[1])
                c, s_ = math.cos(-roll), math.sin(-roll)
                d = np.array([d[0] * c - d[1] * s_, d[0] * s_ + d[1] * c, d[2]])
            else:                             # hind feet trail far behind the hip: swing the leg about y instead
                turn = math.atan2(d[0], d[2]) - math.atan2(r0[0], r0[2])
                c, s_ = math.cos(-turn), math.sin(-turn)
                d = np.array([d[0] * c + d[2] * s_, d[1], -d[0] * s_ + d[2] * c])
        dist = max(1e-3, min(L1 + L2 - .01, math.hypot(d[1], d[2])))
        base = math.degrees(math.atan2(d[2], d[1]))
        a1 = math.degrees(math.acos(max(-1, min(1, (L1 ** 2 + dist ** 2 - L2 ** 2) / (2 * L1 * dist)))))
        a2 = math.degrees(math.acos(max(-1, min(1, (L1 ** 2 + L2 ** 2 - dist ** 2) / (2 * L1 * L2)))))
        ang = lambda v: math.degrees(math.atan2(v[2], v[1]))
        bend = 1 if key.startswith("f") else -1          # front elbows point back, hind knees forward
        seg1 = base + bend * a1
        seg2 = seg1 - bend * (180 - a2)
        q1 = seg1 - ang(j1 - R0)
        q2 = (seg2 - ang(j2 - j1)) - q1
        add(pose, r, (q1, math.degrees(turn), math.degrees(roll))); add(pose, m, (q2, 0, 0))
        add(pose, e, (-(q1 + q2), -math.degrees(turn), -math.degrees(roll)))


def solve_legs_exact(sk, pose, targets, rounds=4):
    """solve_legs with lateral reach, re-aimed a few times so the planar approximation's error (a few px) vanishes."""
    base = {k: tuple(np.array(v, float).copy() for v in val) for k, val in pose.items()}
    aim = {k: np.array(v, float) for k, v in targets.items()}
    for _ in range(rounds):
        trial = {k: tuple(v.copy() for v in val) for k, val in base.items()}
        solve_legs(sk, trial, aim, lateral=True)
        for k, (r, m, e) in LEGS.items():
            aim[k] = aim[k] - (sk.point(e, trial) - targets[k])
    pose.clear(); pose.update(trial)


HIPS = np.array([0, 30.0, -36.0])        # the crouch pivots here so the hind legs keep their stance


def pitch_about_hips(sk, theta):
    """Body translation that turns a body pitch of theta degrees about its pivot into one about the hips."""
    p0 = sk.bones["body"]["pivot"]
    return euler(np.array([theta, 0, 0])) @ (p0 - HIPS) + HIPS - p0


def crouch_angle(data, drop):
    """The body pitch that brings the planted wing-arm's carpal (built `drop` px above the ground) down."""
    carpal = next(np.array(e["origin"], float) for e in data["elements"] if e["name"] == "cloak_l_carpal")
    for th in np.linspace(0, 25, 251):
        if (euler(np.array([th, 0, 0])) @ (carpal - HIPS) + HIPS)[1] <= carpal[1] - drop:
            return float(th)
    return 25.0


def enraged_stance(sk, pose, theta):
    add(pose, "body", (theta, 0, 0), pitch_about_hips(sk, theta))
    add(pose, "neck", (-14 - theta, 0, 0))


def smooth(t, a, b):
    x = (t - a) / (b - a)
    return 0.0 if x <= 0 else 1.0 if x >= 1 else x * x * (3 - 2 * x)


def bump(t, a, m, b):
    return smooth(t, a, m) * (1 - smooth(t, m, b))


ENRAGE_SECONDS = 5.0
WRAP_HINGES = {"hinge02": 1.0, "hinge09": 1.0, "hinge10": .5, "hinge14": .5}   # membrane folds that drape the wing down


def enrage_pose(sk, rest_feet, theta, t):
    """The enrage clip at t seconds (see the module doc); ends exactly in the enraged stance."""
    u = t - .9                                            # timeline after the wrap
    crouch = smooth(t, .05, .55) * (1 - smooth(t, 1.85, 2.2))
    wrap = smooth(t, .6, 1.2) * (1 - smooth(t, 1.85, 2.2))
    trem = math.sin(t * 2 * math.pi * 7) * smooth(t, 1.2, 1.35) * (1 - smooth(t, 1.7, 1.85))
    rear = smooth(u, 1.0, 1.4) * (1 - smooth(u, 2.1, 2.55))
    fall = bump(u, 2.3, 2.62, 2.95)
    down = smooth(u, 2.45, 2.85)
    impact = bump(u, 2.8, 2.86, 3.05)
    th = 8 * crouch - 38 * rear + 7 * fall + theta * down
    p = {}
    add(p, "body", (th, 0, .8 * trem), pitch_about_hips(sk, th) + np.array([0, -9 * crouch - 3 * impact, 0]))
    add(p, "neck", (28 * crouch + 12 * wrap - 4 * rear + 10 * fall + (-14 - theta) * down - 6 * bump(u, 3.0, 3.3, 3.9), 15 * wrap, 0))
    add(p, "skull", (12 * crouch + 8 * wrap - 22 * bump(u, 1.4, 1.6, 2.2) - 12 * bump(u, 3.0, 3.3, 3.9), 10 * wrap, 0))
    add(p, "jaw", (34 * bump(u, 1.45, 1.6, 2.2) + 30 * bump(u, 3.05, 3.3, 3.9), 0, 0))
    add(p, "tail", (-10 * rear + 8 * crouch, 35 * wrap, 0)); add(p, "tail_2", (-8 * rear, 35 * wrap, 0))
    add(p, "tail_mid", (0, 35 * wrap, 0)); add(p, "tail_end", (0, 30 * wrap, 0))
    lift = smooth(u, 1.0, 1.35) * (1 - smooth(u, 2.35, 2.75))     # front paws off the ground while reared
    targets = {k: rest_feet[k] + (np.array([0, 34 * lift, 6 * lift]) if k.startswith("f") else 0) for k in LEGS}
    solve_legs(sk, p, targets)
    for k in ("fl", "fr"):
        add(p, LEGS[k][2], (50 * lift, 0, 0))
    thrown = smooth(u, 1.0, 1.35)
    folded = 1 - smooth(u, 1.3, 1.5)
    grow = smooth(u, 1.25, 1.6)
    swing = bump(u, 1.95, 2.3, 2.75)
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"wing_{side}", (10 * wrap, -30 * wrap * sg, (-20 * wrap + 2 * trem + 70 * thrown) * sg))
        add(p, f"wing_{side}_fore", (0, -20 * wrap * sg, -15 * wrap * sg))
        for h, k in WRAP_HINGES.items():
            add(p, f"wing_{side}_{h}", (0, 0, -25 * wrap * k * sg))
        for b in sk.order:
            if b == f"wing_{side}" or b.startswith(f"wing_{side}_"):
                scale(p, b, folded)
        add(p, f"cloak_{side}", (-20 * swing, 0, (25 * (1 - smooth(u, 1.6, 1.95)) * thrown - 55 * swing) * sg))
        scale(p, f"cloak_{side}", (.35 + .65 * grow) * (1 + .12 * bump(u, 1.5, 1.7, 2.0)) if u >= 1.25 else 0)
        scale(p, f"horn_{side}", smooth(u, 1.45, 1.9))
    return p


# The walk is timed to the server's ground speed so the planted feet do not skate: a stance foot slides back one
# stride over duty * cycle seconds, which must equal the carrier's speed (0.13 blocks/tick = 41.6 px/s, x1.2 enraged).
WALK = dict(stride=34, duty=.7, enraged_stride=36)
WALK_SPEED = 41.6                                # px/s


def walk_cycle(enraged):
    stride = WALK["enraged_stride"] if enraged else WALK["stride"]
    return stride / (WALK["duty"] * WALK_SPEED * (1.2 if enraged else 1.0))


def walk_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    duty, stride, lift = WALK["duty"], WALK["stride"], 11
    ph = {"hl": 0, "fl": .25, "hr": .5, "fr": .75}
    roll, bob, sway = 1.5, .35, .6
    s = math.sin(2 * math.pi * t)
    p = {}
    if enraged:
        enraged_stance(sk, p, theta)
        stride, lift = WALK["enraged_stride"], 8
    add(p, "body", (0, sway * s, roll * math.sin(2 * math.pi * (t + .25))), (0, -bob * abs(s), 0))
    targets = {}
    for k in LEGS:
        front = k.startswith("f")
        dy, dz = foot_offset((t + ph[k]) % 1, duty, stride, lift if front else lift * .45, front)
        targets[k] = rest_feet[k] + np.array([0, dy, dz])
    solve_legs(sk, p, targets)
    for k in ("fl", "fr"):                   # the wrist folds under while lifted
        add(p, LEGS[k][2], (55 * swing_curl((t + ph[k]) % 1, duty), 0, 0))
    add(p, "neck", (0, -sway * 1.3 * s, -roll * .6 * math.sin(2 * math.pi * (t + .25))))
    if not enraged:                          # head carried high and still, neck arched
        add(p, "neck", (-10, 0, 0)); add(p, "skull", (8, 0, 0))
    for k, b in enumerate(("tail", "tail_2", "tail_mid", "tail_end")):
        add(p, b, (0, 5 * (1 + .5 * k) * math.sin(2 * math.pi * (t - .15 * (k + 1))), 0))
    for side, sfx in ((1, "l"), (-1, "r")):
        add(p, f"wing_{sfx}", (2 * math.sin(2 * math.pi * (t + (0 if side > 0 else .5))), 0, 0))
    return p


# ---- moving round the player (draft 2, 2026-10-17) ----
# Read from the Wilds moveset (youtube.com/watch?v=RS93ksVExN8): Gore never bounces. It stalks low on wide-planted
# wing-arms with the head down and locked on its prey (Bite, 39-42 s), whips round on the spot and darts along the
# ground stretched long and low (Double Dart, 49-51 s), and repositions with one heavy wingbeat (Side Dive, 77-82 s).
# The server moves the carrier; the clips keep the body over it (no horizontal travel).
STRAFE = dict(stride=24, duty=.75, speed=25.6)    # px, -, px/s: 1.6 blocks/s


def strafe_cycle():
    return STRAFE["stride"] / (STRAFE["duty"] * STRAFE["speed"])


def strafe_pose(sk, rest_feet, t, sign, enraged=False, theta=0.0):
    """横移動: a low, deliberate sidestep round the player (sign +1 toward model +X). Weight shifts onto each planted
    foot, the head stays low and locked on, the tail drags. Feet are placed exactly (lateral IK), so nothing skates."""
    duty, stride = STRAFE["duty"], STRAFE["stride"]
    ph = {"fl": 0, "hl": .25, "fr": .5, "hr": .75} if sign > 0 else {"fr": 0, "hr": .25, "fl": .5, "hl": .75}
    p = {}
    if enraged:
        enraged_stance(sk, p, theta)
    w = 2 * math.pi * t
    body(sk, p, (3, 4 * sign, -1.5 * sign + 1.2 * math.sin(2 * w)), (1.5 * math.sin(2 * w) * sign, -6 - .5 * math.sin(2 * w) ** 2, 0))
    targets = {}
    for k in LEGS:
        lift = 7 if k.startswith("f") else 4
        dy, dz = foot_offset((t + ph[k]) % 1, duty, stride, lift, False)
        targets[k] = rest_feet[k] + np.array([dz * sign, dy, 0])   # stance: the foot drifts back against the travel
    solve_legs_exact(sk, p, targets)
    add(p, "neck", (8 if not enraged else 4, -8 * sign, 0)); add(p, "skull", (6 if not enraged else 0, -6 * sign, 0))
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{side}", (0, 0, (6 if not enraged else 2) * sg))
    for k, b in enumerate(TAIL):
        add(p, b, (4, (5 + 2 * k) * sign + 2 * math.sin(w - .6 * (k + 1)), 0))
    return p


def hop_back_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    """跳び退き, 1.1 s: sinks low with the wings unfurling overhead; one heavy downbeat lifts it off and back
    (the server carries it ~4 blocks between 0.28 s and 0.68 s); glides with the wings spread, the head still down on
    the player; lands hind feet first, the wing-arms slam down, and it rises slowly as the wings settle."""
    p = {}
    y = K(t, [(0, 0), (.25, -8), (.33, 6), (.5, 9), (.64, 3), (.72, -7), (.85, -4), (1.1, 0)])
    pitch = K(t, [(0, 0), (.25, 6), (.34, -8), (.55, -5), (.66, 2), (.74, 5), (1.1, 0)])
    if enraged:
        enraged_stance(sk, p, theta)
    body(sk, p, (pitch, 0, 0), (0, y, 0))
    air = smooth(t, .3, .36) * (1 - smooth(t, .62, .72))
    fore = (0, y + 10 * air, 8 * air); hind = (0, y + 4 * air * (1 - smooth(t, .58, .64)), -4 * air)
    solve_legs(sk, p, feet_with(rest_feet, fl=fore, fr=fore, hl=hind, hr=hind))
    beat = K(t, [(0, 0), (.25, 64), (.31, 68), (.36, -12), (.44, 26), (.6, 30), (.7, 8), (.8, -4), (1.1, 0)])
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        if enraged:
            add(p, f"{arm}_{side}", (K(t, [(0, 0), (.25, -10), (.36, 8), (.6, 0)]), 0, beat * .55 * sg))
        else:                                       # the membrane opens for the downbeat and the glide
            open_wing(p, side, smooth(t, .12, .26) * (1 - smooth(t, .7, .95)),
                      K(t, [(0, 0), (.25, 1), (.31, 1), (.36, -1), (.44, .2), (.6, .3), (.7, 0)]))
    add(p, "neck", (K(t, [(0, 0), (.25, 14), (.4, 8), (.72, 16), (1.1, 0)]) + (-10 if not enraged else 0), 0, 0))
    if not enraged:
        add(p, "skull", (8, 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (.3, 12), (.5, 4), (.75, 14), (1.0, 0)]), 0, 0))
    add(p, "tail", (K(t, [(0, 0), (.3, -8), (.55, 6), (.75, -4), (1.0, 0)]), 0, 0))
    return p


def dash_side_pose(sk, rest_feet, t, sign, enraged=False, theta=0.0):
    """回り込み (dart), 0.9 s: whips its body toward the side and crouches; darts along the ground stretched long
    and low with the wings swept back like a spearhead (the server carries it ~5 blocks aside between 0.2 s and
    0.5 s); skids to a stop and whips back round past the player, then settles facing it."""
    p = {}
    yaw = K(t, [(0, 0), (.18, 55), (.5, 62), (.62, -18), (.75, 6), (.9, 0)]) * sign
    y = K(t, [(0, 0), (.18, -8), (.3, -6), (.48, -7), (.58, -9), (.72, -2), (.9, 0)])
    pitch = K(t, [(0, 0), (.18, 7), (.35, 5), (.55, 8), (.7, 2), (.9, 0)])
    if enraged:
        enraged_stance(sk, p, theta)
    body(sk, p, (pitch, yaw, -6 * sign * smooth(t, .5, .58) * (1 - smooth(t, .62, .8))), (0, y, 0))
    run = smooth(t, .2, .26) * (1 - smooth(t, .48, .56))
    stride = math.sin(2 * math.pi * (t - .2) / .15)                 # quick strides while darting
    fore = (0, y + 8 * run * max(0.0, stride), 10 * run); hind = (0, y + 5 * run * max(0.0, -stride), -6 * run)
    solve_legs(sk, p, feet_with(rest_feet, fl=fore, fr=fore, hl=hind, hr=hind))
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{side}", (K(t, [(0, 0), (.2, 10), (.5, 12), (.65, 0)]), 0,
                                 K(t, [(0, 0), (.2, -12), (.5, -14), (.62, 16), (.9, 0)]) * (.5 if enraged else 1) * sg))
    add(p, "neck", (K(t, [(0, 0), (.18, 10), (.5, 14), (.62, 4), (.9, 0)]) + (-10 if not enraged else 0),
                    K(t, [(0, 0), (.18, -20), (.5, -16), (.62, 14), (.8, 0)]) * sign, 0))
    if not enraged:
        add(p, "skull", (8, 0, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (-4 * run, K(t, [(0, 0), (.2, -10), (.5, -8), (.62, 18), (.8, -4), (.9, 0)]) * sign * (1 + .3 * k), 0))
    return p


# ---- abilities, idle and death: keyed beats + secondary motion ----
# Read from the Wilds tempered Gore Magala moveset (youtube.com/watch?v=RS93ksVExN8, 2026-10-17): the body moves
# first and everything hanging off it follows late (neck -> skull -> antennae, shoulder -> elbow -> wrist ->
# membrane, hips -> tail), it leaves the ground for small hops and lands soft (forelimbs first, then the hind),
# the blind head keeps sensing (slow sway, antennae twitch), wind-ups are held and trembling, strikes travel on
# arcs, and every stop settles with a damped overshoot. The beats are keyed per channel (Catmull-Rom between
# keys) and a spring pass (SPRINGS) adds the follow-through on the hanging chains.
# Each clip is telegraph + recovery long and hits on the telegraph time (the server's contract). The four phase-one
# abilities also get an `<id>_enraged` twin in the crouch with the fans, which the server plays once enraged.
ABILITIES = {"shade-sweep": (1.1, 2.0), "shade-pounce": (1.4, 2.6), "shade-dust": (1.7, 2.6),
             "shade-pulse": (1.2, 2.3), "shade-breath": (1.6, 2.6), "shade-storm": (2.2, 3.5),
             "shade-turnsweep": (.9, 1.8), "shade-dive": (2.4, 3.8),
             # 2026-10-19 (藍が選んだ行動): the bite's hit is its last snap (the earlier two at 0.7 / 1.15 s)
             "shade-bite": (1.6, 2.4), "shade-tailspin": (1.25, 2.4), "shade-horn": (1.0, 2.0),
             "shade-tailslap": (.7, 1.5), "shade-orb": (1.2, 2.2),
             # MMO gimmicks (2026-10-19): the cast to interrupt (second form on), the shell to break (enraged)
             "shade-channel": (6.0, 7.2), "shade-shell": (9.0, 10.2),
             # 2026-10-19 (A: attacks move the body): the charge across the arena
             "shade-charge": (1.4, 3.0)}
PHASE_ONE = ("shade-sweep", "shade-pounce", "shade-dust", "shade-pulse", "shade-turnsweep", "shade-dive",
             "shade-bite", "shade-tailspin", "shade-horn", "shade-tailslap", "shade-orb", "shade-channel", "shade-charge")
TAIL = ("tail", "tail_2", "tail_mid", "tail_end")


def K(t, keys):
    """Catmull-Rom through (time, value) keys, held flat before the first and after the last key."""
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = next(i for i in range(len(keys) - 1) if keys[i][0] <= t <= keys[i + 1][0])
    (t1, p1), (t2, p2) = keys[i], keys[i + 1]
    t0, p0 = keys[i - 1] if i > 0 else (t1 - (t2 - t1), p1)
    t3, p3 = keys[i + 2] if i + 2 < len(keys) else (t2 + (t2 - t1), p2)
    u = (t - t1) / (t2 - t1)
    m1 = (p2 - p0) / (t2 - t0) * (t2 - t1)
    m2 = (p3 - p1) / (t3 - t1) * (t2 - t1)
    return ((2 * u ** 3 - 3 * u ** 2 + 1) * p1 + (u ** 3 - 2 * u ** 2 + u) * m1
            + (-2 * u ** 3 + 3 * u ** 2) * p2 + (u ** 3 - u ** 2) * m2)


def turn_about_hips(sk, rot):
    """Body translation that turns a body rotation about its pivot into one about the hips."""
    p0 = sk.bones["body"]["pivot"]
    return euler(np.array(rot, float)) @ (p0 - HIPS) + HIPS - p0


def body(sk, p, rot=(0, 0, 0), pos=(0, 0, 0)):
    add(p, "body", rot, turn_about_hips(sk, rot) + np.array(pos, float))


def shiver(t, a, b, hz=11.0):
    return math.sin(t * 2 * math.pi * hz) * smooth(t, a, a + .2) * (1 - smooth(t, b - .1, b))


def open_wing(p, sd, a, beat=0.0):
    """The folded wing opened by a (0..1), membrane spread: the root swung out and back, the forearm opened (the forearm
    folding the other way is what made the wings stay shut in the air before the 2026-10-19 review); beat (-1..1)
    flaps it."""
    sg = 1 if sd == "l" else -1
    add(p, f"wing_{sd}", (0, -30 * a * sg, (50 * a + 16 * beat) * sg))
    add(p, f"wing_{sd}_fore", (0, 0, (25 * a + 14 * beat) * sg))
    for h in ("hinge02", "hinge09", "hinge10", "hinge14"):
        add(p, f"wing_{sd}_{h}", (0, 0, 14 * a * sg))


def open_wings(p, a, beat=0.0):
    for sd in ("l", "r"):
        open_wing(p, sd, a, beat)


# Wind (review 2026-10-19, 藍「羽は風で揺られるべき」): every clip gets a slow ripple through the wing membranes
# (hinge by hinge, outward) and the fan rays (one after another), swelling and easing like gusts.
WING_CHAIN = ("membrane", "hinge02", "hinge03", "hinge00", "hinge01", "hinge09", "hinge07", "hinge08", "hinge12",
              "hinge13", "hinge14", "hinge16", "hinge10", "hinge11", "hinge15", "hand")


def add_wind(p, t, period=None):
    """period: a loop's length, so the ripple repeats exactly over it."""
    def hz(f):
        return f if period is None else max(1, round(period * f)) / period
    w1, w2 = 2 * math.pi * hz(.55), 2 * math.pi * hz(.2)
    gust = 1.0 + .45 * math.sin(w2 * t)
    for sd, sg in (("l", 1), ("r", -1)):
        ph = 0 if sd == "l" else 1.3
        for k, h in enumerate(WING_CHAIN):
            add(p, f"wing_{sd}_{h}", (1.1 * gust * math.sin(w1 * t - .45 * k + ph), 0, 3.0 * gust * math.sin(w1 * t - .5 * k + ph) * sg))
        add(p, f"cloak_{sd}", (0, 0, 1.2 * gust * math.sin(w1 * t + ph) * sg))
        for j in range(4):
            add(p, f"cloak_{sd}_ray{j}", (3.5 * gust * math.sin(w1 * t - .8 * j + ph), 0, 6.0 * gust * math.sin(w1 * t - .7 * j + ph + .6) * sg))
    return p


def feet_with(rest_feet, **offsets):
    return {k: rest_feet[k] + np.array(offsets.get(k, (0, 0, 0)), float) for k in LEGS}


def sweep_pose(sk, rest_feet, theta, t, enraged):
    """翼脚薙ぎ: weight onto the far side, the near wing-arm drawn up and back with the body coiled away and the head
    held on the target; the shoulder whips across, the elbow and wrist trail and snap; the body overshoots, steps."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.8, 3), (1.05, 5), (1.2, 6), (1.6, 0)]),
                 K(t, [(0, 0), (.35, 10), (.8, 20), (1.0, 22), (1.1, -24), (1.25, -32), (1.5, -10), (1.8, 2), (2.0, 0)]),
                 K(t, [(0, 0), (.8, 6), (1.1, -6), (1.3, -8), (1.7, 0)])),
         (K(t, [(0, 0), (.8, -2), (1.2, 3), (1.8, 0)]),
          K(t, [(0, 0), (.3, -2), (.8, -3), (1.0, -1), (1.15, -4), (1.4, -2), (2.0, 0)]), 0))
    add(p, "neck", (K(t, [(0, 0), (.8, -6), (1.1, 6), (1.4, 2), (2.0, 0)]),
                    K(t, [(0, 0), (.8, -14), (1.1, 8), (1.3, 12), (1.7, -2), (2.0, 0)]), 0))
    add(p, "skull", (K(t, [(0, 0), (.8, -6), (1.1, 4), (1.6, 0)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (.9, 5), (1.1, 22), (1.4, 10), (1.7, 0)]), 0, 0))
    arm = "cloak" if enraged else "wing"
    lift = .75 if enraged else 1.0
    add(p, f"{arm}_l", (0, K(t, [(0, 0), (.6, 20), (.95, 34), (1.1, -50), (1.25, -62), (1.5, -30), (1.9, 0)]),
                        lift * K(t, [(0, 0), (.5, 30), (.9, 55), (1.0, 58), (1.1, 12), (1.25, 4), (1.6, 4), (2.0, 0)])))
    add(p, f"{arm}_r", (0, K(t, [(0, 0), (.8, 6), (1.2, -4), (1.7, 0)]), K(t, [(0, 0), (.8, -8), (1.2, 6), (1.7, 0)])))
    if not enraged:                                 # the swinging wing half opens, its membrane trailing the arc
        open_wing(p, "l", .6 * smooth(t, .4, .9) * (1 - smooth(t, 1.4, 1.9)), K(t, [(0, 0), (.95, -.3), (1.15, .8), (1.4, 0)]))
    add(p, "tail", (0, K(t, [(0, 0), (.8, -25), (1.1, 15), (1.35, 30), (1.8, -5), (2.0, 0)]), 0))
    fl = (0, K(t, [(0, 0), (.6, 5), (.85, 0)]), K(t, [(0, 0), (.6, -3), (.85, -6), (1.6, -6), (2.0, 0)]))
    fr = (0, K(t, [(0, 0), (1.2, 0), (1.32, 4), (1.48, 0)]), K(t, [(0, 0), (1.2, 0), (1.48, 5), (2.0, 0)]))
    return p, feet_with(rest_feet, fl=fl, fr=fr)


def pounce_pose(sk, rest_feet, theta, t, enraged):
    """飛びかかり: sinks onto the hind legs, springs up and forward with the wings flared, reaches with the forelimbs
    and lands on them nose-down (hit), the hind feet landing after; then hops lightly back to where it stood."""
    p = {}
    z = K(t, [(0, 0), (.9, -6), (1.12, -8), (1.28, 14), (1.4, 30), (1.55, 32), (1.8, 28), (2.05, 12), (2.3, 2), (2.6, 0)])
    y = K(t, [(0, 0), (.5, -5), (.9, -9), (1.1, -10), (1.18, 2), (1.28, 16), (1.4, 0), (1.47, -6), (1.65, -2),
              (1.85, -1), (1.98, 6), (2.15, -3), (2.35, 0), (2.6, 0)])
    body(sk, p, (K(t, [(0, 0), (.9, 4), (1.12, 6), (1.2, -14), (1.32, 4), (1.4, 12), (1.5, 14), (1.75, 4), (1.98, -6),
                       (2.15, 3), (2.4, 0)]), 0, 0), (0, y, z))
    add(p, "neck", (K(t, [(0, 0), (.9, 10), (1.2, -6), (1.4, 8), (1.55, 12), (1.9, 0)]), 0, 0))
    add(p, "skull", (K(t, [(0, 0), (.9, -12), (1.4, -4), (1.8, 0)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (1.2, 10), (1.38, 36), (1.55, 30), (1.8, 0)]), 0, 0))
    add(p, "tail", (K(t, [(0, 0), (.9, 10), (1.25, -10), (1.45, 12), (1.7, 0)]), 0, 0))
    arm = "cloak" if enraged else "wing"
    spread = K(t, [(0, 0), (.9, 8), (1.15, 20), (1.28, 55), (1.4, 25), (1.5, 10), (1.98, 30), (2.2, 5), (2.6, 0)])
    for side, sg in (("l", 1), ("r", -1)):
        if enraged:
            add(p, f"{arm}_{side}", (0, -10 * smooth(t, 1.1, 1.28) * (1 - smooth(t, 1.4, 1.6)) * sg, .6 * spread * sg))
        else:                                       # the wings flare open through the leap
            open_wing(p, side, .9 * bump(t, 1.05, 1.28, 1.7) + .4 * bump(t, 1.85, 2.0, 2.3))
    front = (0, K(t, [(0, 0), (1.12, 0), (1.25, 14), (1.4, 0), (1.85, 0), (1.98, 8), (2.1, 0)]),
             K(t, [(0, 0), (1.1, 0), (1.28, 22), (1.4, 30), (1.8, 30), (1.98, 20), (2.2, 4), (2.6, 0)]))
    hind = (0, K(t, [(0, 0), (1.15, 0), (1.25, 6), (1.35, 10), (1.48, 0), (1.92, 0), (2.0, 6), (2.12, 0)]),
            K(t, [(0, 0), (1.15, 0), (1.3, 10), (1.48, 24), (1.8, 26), (1.98, 16), (2.15, 4), (2.6, 0)]))
    return p, feet_with(rest_feet, fl=front, fr=front, hl=hind, hr=hind)


def dust_pose(sk, rest_feet, theta, t, enraged):
    """鱗粉炸裂: rears a little and lifts the wings high, shivering them from root to tip; one heavy downbeat that
    lifts the body off its feet for a moment (hit), the membrane cracking like a whip; settles with a bob."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.6, -6), (1.3, -10), (1.55, -12), (1.7, 4), (1.85, 6), (2.2, 0)]), 0, 1.2 * shiver(t, .9, 1.62)),
         (0, K(t, [(0, 0), (.6, 3), (1.3, 5), (1.55, 7), (1.62, 9), (1.7, -3), (1.85, -4), (2.2, 0)]), 0))
    add(p, "neck", (K(t, [(0, 0), (.6, -8), (1.4, -14), (1.7, 14), (1.9, 10), (2.3, 0)]), 0, 0))
    add(p, "skull", (K(t, [(0, 0), (1.4, -8), (1.7, 10), (2.2, 0)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (1.6, 10), (1.75, 24), (2.0, 0)]), 0, 0))
    add(p, "tail", (K(t, [(0, 0), (.6, 6), (1.7, -8), (2.2, 0)]), 0, 0))
    arm = "cloak" if enraged else "wing"
    raise_ = K(t, [(0, 0), (.5, 20), (1.1, 48), (1.5, 62), (1.6, 66), (1.7, -8), (1.8, -12), (2.0, 0), (2.6, 0)])
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{side}", (0, K(t, [(0, 0), (1.1, -12), (1.7, 6), (2.1, 0)]) * sg,
                                 ((.7 if enraged else 1) * raise_ + 4 * shiver(t + .03 * sg, .9, 1.62)) * sg))
        if not enraged:
            add(p, f"wing_{side}_fore", (0, 0, K(t, [(0, 0), (.8, -15), (1.5, -25), (1.65, -30), (1.75, 35), (1.95, 5), (2.3, 0)]) * sg))
            for k, h in enumerate(("hinge02", "hinge09", "hinge10", "hinge14")):
                add(p, f"wing_{side}_{h}", (0, 0, 8 * shiver(t - .04 * k, .9, 1.62) * sg))
    return p, dict(rest_feet)


def pulse_pose(sk, rest_feet, theta, t, enraged):
    """触角の共鳴: breathes in and lifts the head high on an S-curved neck, sensing side to side, the antennae
    spreading and quivering; thrusts the head down roaring (hit), the wings flung out, a shudder through the body."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.7, -14), (1.05, -18), (1.2, 6), (1.5, 3), (2.0, 0)]), 0, .6 * shiver(t, 1.2, 1.6, 13)),
         (0, K(t, [(0, 0), (.7, 8), (1.05, 10), (1.2, -4), (1.35, -2), (1.8, 0)]), 0))
    trem = shiver(t, .7, 1.2, 14)
    add(p, "neck", (K(t, [(0, 0), (.4, -16), (.9, -34), (1.08, -38), (1.2, 18), (1.35, 20), (1.8, 3), (2.3, 0)]),
                    K(t, [(0, 0), (.4, 6), (.7, -6), (1.0, 4), (1.2, 0)]), 0))
    add(p, "skull", (K(t, [(0, 0), (.9, -6), (1.1, -7), (1.2, 5), (1.4, 3), (2.0, 0)]) + 1.5 * trem,
                     K(t, [(0, 0), (.5, -8), (.8, 8), (1.1, 0)]), 1.5 * trem))
    add(p, "jaw", (K(t, [(0, 0), (.9, 8), (1.15, 12), (1.22, 40), (1.6, 30), (1.9, 0)]), 0, 0))
    spread = K(t, [(0, 0), (.6, 40), (1.1, 52), (1.2, -6), (1.5, 8), (2.0, 0)])
    grow = 1 + .7 * smooth(t, .3, .8) * (1 - smooth(t, 1.3, 1.8))   # the antennae fan out large while it listens
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"crown_{side}", (-.4 * spread, (spread + 3 * trem) * sg, 0))
        scale(p, f"crown_{side}", grow)
        if enraged:
            add(p, f"horn_{side}", (-.5 * spread, (.6 * spread + 3 * trem) * sg, 0))
        if enraged:
            add(p, f"{arm}_{side}", (0, 0, K(t, [(0, 0), (.7, -6), (1.2, 30), (1.4, 22), (1.9, 0)]) * .6 * sg))
        else:                                       # the wings flung open on the roar
            open_wing(p, side, .9 * bump(t, 1.1, 1.25, 1.9))
    add(p, "tail", (K(t, [(0, 0), (.7, -6), (1.2, 10), (1.8, 0)]), 0, 0))
    return p, dict(rest_feet)


def breath_pose(sk, rest_feet, theta, t, enraged):
    """翳の吐息: draws the head back and to the side with the body turning after it, the wing-arms braced, the jaw
    opening; whips the head forward (neck first, skull snapping after) and fires (hit), recoiling back a step."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.8, -4), (1.45, -6), (1.6, 4), (1.7, -2), (2.1, 0)]),
                 K(t, [(0, 0), (.8, 10), (1.45, 14), (1.6, -4), (1.8, -2), (2.3, 0)]), 0),
         (0, K(t, [(0, 0), (.8, 2), (1.45, 3), (1.6, -2), (2.0, 0)]),
          K(t, [(0, 0), (.8, -4), (1.45, -7), (1.6, 3), (1.68, -4), (1.9, -2), (2.4, 0)])))
    add(p, "neck", (K(t, [(0, 0), (.8, -18), (1.45, -26), (1.6, 14), (1.75, 9), (2.0, 6), (2.5, 0)]) + 1.5 * shiver(t, 1.1, 1.5, 12),
                    K(t, [(0, 0), (.8, 24), (1.45, 30), (1.6, -6), (1.8, 0)]), 0))
    add(p, "skull", (K(t, [(0, 0), (.8, -3), (1.45, -4), (1.6, 4), (1.7, 6), (2.1, 0)]),
                     K(t, [(0, 0), (1.45, 12), (1.6, -8), (1.9, 0)]), 0))
    add(p, "jaw", (K(t, [(0, 0), (.8, 6), (1.4, 18), (1.6, 44), (2.1, 38), (2.3, 0)]), 0, 0))
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"cloak_{side}", (0, 0, K(t, [(0, 0), (1.45, -4), (1.6, 6), (2.0, 0)]) * sg))
    add(p, "tail", (K(t, [(0, 0), (1.45, -8), (1.65, 10), (2.1, 0)]), K(t, [(0, 0), (.8, -12), (1.6, 8), (2.2, 0)]), 0))
    return p, dict(rest_feet)


def storm_pose(sk, rest_feet, theta, t, enraged):
    """鱗粉嵐 (the Power Smash read): rears onto the hind legs with the fans swept up and back, head tucked, hanging
    there with a floating bob; the fans come over in a big arc as the body falls forward and slam the ground (hit)."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.4, -6), (1.0, -34), (1.5, -40), (1.9, -42), (2.05, -30), (2.2, 6), (2.35, 8), (2.8, 2), (3.5, 0)]), 0, 0),
         (0, K(t, [(0, 0), (.4, -3), (1.0, 6), (1.3, 9), (1.6, 7), (1.9, 10), (2.2, -4), (2.35, -6), (2.8, -1), (3.5, 0)]),
          K(t, [(0, 0), (1.0, -6), (1.9, -8), (2.2, 6), (2.6, 4), (3.5, 0)])))
    add(p, "neck", (K(t, [(0, 0), (1.0, -4), (1.6, 10), (2.0, -12), (2.2, 18), (2.5, 10), (3.2, 0)]), 0, 0))
    add(p, "skull", (K(t, [(0, 0), (1.6, 8), (2.2, 10), (2.8, 0)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (1.2, 20), (1.7, 30), (2.05, 10), (2.2, 30), (2.6, 0)]), 0, 0))
    add(p, "tail", (K(t, [(0, 0), (1.0, 16), (2.2, -10), (2.8, 0)]), 0, 0))
    x = K(t, [(0, 0), (1.0, -20), (1.9, -28), (2.05, -10), (2.2, -14), (2.6, -6), (3.5, 0)])
    zz = K(t, [(0, 0), (.4, 10), (1.0, 55), (1.6, 72), (1.9, 76), (2.05, 50), (2.2, -18), (2.35, -24), (2.8, -6), (3.5, 0)])
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"cloak_{side}", (x, 0, (zz + 3 * shiver(t + .05 * sg, 1.0, 1.95, 9)) * sg))
        add(p, f"horn_{side}", (0, 2 * shiver(t, 1.0, 2.0, 15) * sg, 0))
    front = (0, K(t, [(0, 0), (.5, 0), (1.0, 30), (1.9, 34), (2.1, 20), (2.2, 0)]),
             K(t, [(0, 0), (1.0, 8), (2.2, 10), (2.8, 8), (3.5, 0)]))
    return p, feet_with(rest_feet, fl=front, fr=front)


def rotated_feet(rest_feet, yaw_deg, pivot):
    R = euler(np.array([0, yaw_deg, 0]))
    return {k: R @ (v - pivot) + pivot for k, v in rest_feet.items()}


def turnsweep_pose(sk, rest_feet, theta, t, enraged):
    """振り向き薙ぎ (Back Sweep): it has its back to the player; it coils, then whips its whole body round on the spot
    (the feet stepping round under it) and the wing-arm rakes across behind it on the hit (0.9 s)."""
    p = {}
    yaw = K(t, [(0, 180), (.3, 172), (.62, 150), (.9, -18), (1.05, -30), (1.35, -8), (1.8, 0)])
    pivot = sk.bones["body"]["pivot"]
    add(p, "body", (K(t, [(0, 0), (.6, 6), (.9, 4), (1.4, 0)]), yaw, K(t, [(0, 0), (.6, 8), (.95, -10), (1.3, 0)])),
        (0, K(t, [(0, 0), (.6, -5), (.95, -2), (1.4, 0)]), 0))
    if enraged:
        enraged_stance(sk, p, theta)
    step = bump(t, .66, .76, .9)
    feet = rotated_feet(rest_feet, yaw, pivot)
    for k in feet:
        feet[k] = feet[k] + np.array([0, 6 * step * (1 if k.startswith("f") else .6), 0])
    solve_legs(sk, p, feet)
    arm = "cloak" if enraged else "wing"
    add(p, f"{arm}_l", (0, K(t, [(0, 0), (.6, 26), (.85, 30), (.92, -56), (1.1, -64), (1.4, -24), (1.8, 0)]),
                        (.75 if enraged else 1) * K(t, [(0, 0), (.5, 40), (.85, 52), (.92, 10), (1.2, 4), (1.8, 0)])))
    add(p, "neck", (K(t, [(0, 0), (.6, 8), (.9, -4), (1.4, 0)]) + (-10 if not enraged else 0), K(t, [(0, 0), (.62, 30), (.9, -16), (1.2, 4), (1.6, 0)]), 0))
    if not enraged:
        add(p, "skull", (8, 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (.88, 6), (.95, 24), (1.3, 0)]), 0, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (0, K(t, [(0, 0), (.6, -30), (.95, 40), (1.2, 20), (1.6, 0)]) * (1 + .25 * k), 0))
    return p, None


def dive_pose(sk, rest_feet, theta, t, enraged):
    """飛び上がり急降下 (Dive), hit at 2.4 s: crouches with the wings raised, beats up off the ground, hangs high with
    heavy wingbeats while the server carries it over the target, folds and plunges nose-first onto it, wings flung out
    flat on impact, then pushes itself up."""
    p = {}
    y = K(t, [(0, 0), (.45, -8), (.7, 20), (1.0, 52), (1.4, 64), (1.9, 66), (2.15, 58), (2.4, -6), (2.55, -9), (3.0, -3), (3.8, 0)])
    hover = smooth(t, .6, .8) * (1 - smooth(t, 2.0, 2.15))
    y += 3 * math.sin(2 * math.pi * 1.6 * t) * hover
    pitch = K(t, [(0, 0), (.45, 6), (.7, -16), (1.1, -6), (1.9, 4), (2.15, 24), (2.4, 14), (2.6, 8), (3.2, 0)])
    if enraged:
        enraged_stance(sk, p, theta * (1 - hover))
    body(sk, p, (pitch, 0, 0), (0, y, 0))
    air = smooth(t, .65, .75) * (1 - smooth(t, 2.3, 2.4))
    tuck = np.array([0, y + 6 * air, 4 * air])
    feet = {k: rest_feet[k] + (tuck if k.startswith("f") else np.array([0, y + 2 * air, -6 * air])) for k in rest_feet}
    solve_legs(sk, p, feet)
    beat = math.sin(2 * math.pi * 1.6 * t) * hover
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        if enraged:
            z = K(t, [(0, 0), (.45, 60), (.6, 70), (.72, -10), (.9, 40)]) * (1 - hover) + (30 + 40 * beat) * hover
            z = z * (1 - smooth(t, 2.0, 2.15)) + K(t, [(2.0, 0), (2.15, -24), (2.38, -24), (2.45, 30), (2.7, 10), (3.4, 0)]) * smooth(t, 2.0, 2.15)
            add(p, f"{arm}_{side}", (0, 0, z * .6 * sg))
        else:                                       # open and beating in the air, swept back for the plunge, flung out on impact
            open_wing(p, side, smooth(t, .4, .7) * (1 - smooth(t, 2.0, 2.2)) + .8 * bump(t, 2.35, 2.45, 3.2), beat)
    add(p, "neck", (K(t, [(0, 0), (.45, 10), (.8, -10), (1.9, 8), (2.2, 16), (2.45, 4), (3.2, 0)]) + (-10 if not enraged else 0), 0, 0))
    if not enraged:
        add(p, "skull", (8, 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (2.1, 10), (2.42, 36), (2.8, 10), (3.2, 0)]), 0, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (K(t, [(0, 0), (.8, 18), (1.9, 12), (2.3, -14), (2.6, 6), (3.2, 0)]) * (1 + .2 * k), 2 * beat, 0))
    return p, None


def bite_pose(sk, rest_feet, theta, t, enraged):
    """噛みつき3連 (2026-10-19): lowers the head and gathers; three snapping lunges, the head swinging left, right,
    then straight, the body driving after each and the forepaws stepping in; the last bite the deepest; settles."""
    p = {}
    hits = (.7, 1.15, 1.6)
    lunge = sum(bump(t, h - .18, h, h + .2) * (1.4 if k == 2 else 1.0) for k, h in enumerate(hits))
    # the tell: the head weaves side to side, jaws parting, before the first snap (the horn instead drops to the ground)
    side = K(t, [(0, 0), (.15, 12), (.32, -12), (.5, 6), (.7, 14), (.95, 6), (1.15, -14), (1.4, -4), (1.6, 0), (2.4, 0)])
    body(sk, p, (K(t, [(0, 0), (.5, 6), (1.6, 8), (2.0, 2), (2.4, 0)]) + 3 * lunge, .5 * side, 0),
         (0, K(t, [(0, 0), (.5, -4), (1.8, -4), (2.4, 0)]), 10 * lunge + K(t, [(0, 0), (.5, -4), (.7, 4), (1.6, 12), (2.0, 6), (2.4, 0)])))
    add(p, "neck", (K(t, [(0, 0), (.5, 12), (1.8, 10), (2.4, 0)]) - 8 * lunge, side, 0))
    add(p, "skull", (-6 * lunge, .6 * side, -.3 * side))
    jaw = sum(bump(t, h - .22, h - .06, h + .02) for h in hits)
    add(p, "jaw", (36 * jaw + 18 * bump(t, .05, .3, .55), 0, 0))
    arm = "cloak" if enraged else "wing"
    for sd, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{sd}", (0, -6 * lunge * sg, (.6 if enraged else 1) * (K(t, [(0, 0), (.5, 14), (1.8, 14), (2.4, 0)]) + 6 * lunge) * sg))
    for k, b in enumerate(TAIL):
        add(p, b, (0, -.8 * side * (1 + .3 * k), 0))
    step = lambda h, d: (0, 8 * bump(t, h - .2, h - .1, h), d * smooth(t, h - .2, h))
    fl = np.array(step(.7, 6)) + np.array(step(1.6, 8)); fr = np.array(step(1.15, 7))
    return p, feet_with(rest_feet, fl=tuple(fl * (1 - smooth(t, 1.9, 2.4))), fr=tuple(fr * (1 - smooth(t, 1.9, 2.4))))


def tailspin_pose(sk, rest_feet, theta, t, enraged):
    """しっぽ回転 (2026-10-19): coils low with the tail drawn round to one side, then spins a full turn on the spot,
    the tail swung out flat and long (hit at 1.25 s, everything round it); staggers a little out of the turn."""
    p = {}
    yaw = K(t, [(0, 0), (.6, 28), (.95, 36), (1.05, 10), (1.25, -150), (1.45, -300), (1.6, -350), (1.8, -366), (2.2, -360)]) + 360 * smooth(t, 2.2, 2.21)
    pivot = sk.bones["body"]["pivot"]
    add(p, "body", (K(t, [(0, 0), (.6, 8), (1.0, 6), (1.6, 2), (2.2, 0)]), yaw, K(t, [(0, 0), (.9, -6), (1.25, 10), (1.6, -4), (2.0, 0)])),
        (0, K(t, [(0, 0), (.6, -6), (1.0, -5), (1.6, -2), (2.2, 0)]), 0))
    if enraged:
        enraged_stance(sk, p, theta)
    feet = rotated_feet(rest_feet, yaw, pivot)
    spin = smooth(t, 1.0, 1.1) * (1 - smooth(t, 1.6, 1.75))
    for k in feet:
        feet[k] = feet[k] + np.array([0, 5 * spin * (1 if k.startswith("f") else .5), 0])
    solve_legs(sk, p, feet)
    arm = "cloak" if enraged else "wing"
    for sd, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{sd}", (0, 0, (.6 if enraged else 1) * (10 + 20 * spin) * sg))
    add(p, "neck", (K(t, [(0, 0), (.6, 10), (1.6, 6), (2.2, 0)]) + (-10 if not enraged else 0), K(t, [(0, 0), (.6, -18), (1.0, -24), (1.25, 20), (1.7, 6), (2.2, 0)]), 0))
    if not enraged:
        add(p, "skull", (8, 0, 0))
    swing = K(t, [(0, 0), (.6, 30), (.95, 38), (1.15, -10), (1.3, -32), (1.6, -20), (1.9, 6), (2.3, 0)])
    for k, b in enumerate(TAIL):
        add(p, b, (K(t, [(0, 0), (.9, -6), (1.25, -10), (1.7, 0)]), swing * (1 + .2 * k), 0))
    return p, None


def horn_pose(sk, rest_feet, theta, t, enraged):
    """角の突き上げ (2026-10-19): drops the head to the ground, antennae/horns forward, the hind legs bunching; heaves
    head and shoulders up and forward in one thrust (hit at 1.0 s), rearing a little after it; comes back down."""
    p = {}
    # the tell: a deep crouch, rump high and hind legs bunched, the crown scraping the ground (no jaw, no weave)
    body(sk, p, (K(t, [(0, 0), (.4, 16), (.85, 20), (1.0, -10), (1.2, -16), (1.5, -6), (2.0, 0)]), 0, 0),
         (0, K(t, [(0, 0), (.4, -12), (.85, -14), (1.0, 4), (1.2, 8), (1.6, 0)]), K(t, [(0, 0), (.85, -6), (1.0, 10), (1.2, 12), (1.7, 2), (2.0, 0)])))
    add(p, "neck", (K(t, [(0, 0), (.5, 26), (.85, 32), (1.0, -10), (1.2, -24), (1.6, -6), (2.0, 0)]), 0, 0))
    add(p, "skull", (K(t, [(0, 0), (.5, 20), (.85, 24), (1.0, -14), (1.2, -20), (1.7, 0)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (1.0, 4), (1.2, 18), (1.6, 0)]), 0, 0))
    arm = "cloak" if enraged else "wing"
    for sd, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{sd}", (0, 0, (.6 if enraged else 1) * K(t, [(0, 0), (.85, -6), (1.0, 30), (1.3, 20), (2.0, 0)]) * sg))
    for k, b in enumerate(TAIL):
        add(p, b, (K(t, [(0, 0), (.85, -14), (1.05, 12), (1.5, 2), (2.0, 0)]) * (1 + .2 * k), 0, 0))
    fore = (0, K(t, [(0, 0), (.95, 0), (1.05, 10), (1.25, 12), (1.6, 0)]), K(t, [(0, 0), (.95, 0), (1.15, 6), (1.7, 0)]))
    return p, feet_with(rest_feet, fl=fore, fr=fore)


def tailslap_pose(sk, rest_feet, theta, t, enraged):
    """背後への尾打ち (2026-10-19): someone is behind it; without turning, it lifts the tail high over its back and
    whips it down hard behind (hit at 0.7 s), the hips kicking up; the tail bounces once."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.45, 6), (.65, -4), (.72, -8), (1.0, -2), (1.5, 0)]), 0, 0),
         (0, K(t, [(0, 0), (.45, -3), (.7, 2), (1.0, 0)]), K(t, [(0, 0), (.45, 3), (.7, -2), (1.1, 0)])))
    # +x lifts the tail here (it was the wrong way round before the 2026-10-19 review): high over the back, then down hard
    lift = K(t, [(0, 0), (.3, 30), (.5, 50), (.62, 44), (.7, -24), (.8, -30), (.95, -10), (1.1, -16), (1.5, 0)])
    for k, b in enumerate(TAIL):
        add(p, b, (lift * (1 + .15 * k), 0, 0))
    add(p, "neck", (K(t, [(0, 0), (.5, -6), (.72, 6), (1.2, 0)]), K(t, [(0, 0), (.5, 16), (1.2, 4), (1.5, 0)]), 0))
    arm = "cloak" if enraged else "wing"
    for sd, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{sd}", (0, 0, (.6 if enraged else 1) * K(t, [(0, 0), (.5, 12), (.72, 24), (1.2, 0)]) * sg))
    hind = (0, K(t, [(0, 0), (.62, 0), (.72, 8), (.9, 0)]), 0)
    return p, feet_with(rest_feet, hl=hind, hr=hind)


def orb_pose(sk, rest_feet, theta, t, enraged):
    """鱗粉弾 (2026-10-19): rears and draws the head back over its shoulder, the wings shivering as dust gathers in
    the jaws; whips the head forward and flings the clot of dust in an arc (release at 1.2 s); follows through low."""
    p = {}
    body(sk, p, (K(t, [(0, 0), (.6, -8), (1.05, -12), (1.2, 6), (1.4, 10), (1.9, 0)]), K(t, [(0, 0), (.6, 8), (1.05, 12), (1.2, -6), (1.6, 0)]), 1.2 * shiver(t, .5, 1.1)),
         (0, K(t, [(0, 0), (.6, 2), (1.05, 3), (1.25, -4), (1.8, 0)]), K(t, [(0, 0), (1.05, -4), (1.25, 6), (1.8, 0)])))
    add(p, "neck", (K(t, [(0, 0), (.6, -20), (1.05, -28), (1.2, 14), (1.35, 22), (1.9, 0)]), K(t, [(0, 0), (.6, 20), (1.05, 26), (1.2, -8), (1.6, 0)]), 0))
    add(p, "skull", (K(t, [(0, 0), (1.05, -14), (1.2, 10), (1.5, 6), (1.9, 0)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (.9, 10), (1.1, 20), (1.2, 40), (1.4, 24), (1.8, 0)]), 0, 0))
    arm = "cloak" if enraged else "wing"
    for sd, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{sd}", (0, 0, (.6 if enraged else 1) * (K(t, [(0, 0), (.6, 30), (1.05, 36), (1.25, 10), (1.9, 0)]) + 4 * shiver(t, .5, 1.1)) * sg))
    for k, b in enumerate(TAIL):
        add(p, b, (K(t, [(0, 0), (1.05, 10), (1.3, -8), (1.8, 0)]) * (1 + .2 * k), K(t, [(0, 0), (1.05, -12), (1.4, 8), (1.9, 0)]), 0))
    fore = (0, K(t, [(0, 0), (1.15, 0), (1.25, 6), (1.4, 0)]), K(t, [(0, 0), (1.15, 0), (1.35, 6), (1.9, 0)]))
    return p, feet_with(rest_feet, fl=fore, fr=fore)


def relocate_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    """飛び移り (2026-10-19), 3.0 s: crouches and beats up off the ground; glides low across the arena with long, slow
    wingbeats while the server carries it (0.7-2.3 s); flares the wings and lands on the forelimbs, then settles."""
    p = {}
    y = K(t, [(0, 0), (.4, -8), (.65, 18), (.9, 40), (1.5, 46), (2.0, 42), (2.3, 18), (2.45, -6), (2.6, -8), (3.0, 0)])
    air = smooth(t, .55, .7) * (1 - smooth(t, 2.3, 2.45))
    beat = math.sin(2 * math.pi * 1.3 * (t - .6)) * air
    y += 3 * beat
    pitch = K(t, [(0, 0), (.4, 6), (.65, -14), (1.0, 4), (2.0, 4), (2.25, -12), (2.45, 10), (2.7, 4), (3.0, 0)])
    if enraged:
        enraged_stance(sk, p, theta * (1 - air))
    body(sk, p, (pitch, 0, 4 * beat), (0, y, 0))
    tuck = np.array([0, y + 8 * air, 6 * air])
    feet = {k: rest_feet[k] + (tuck if k.startswith("f") else np.array([0, y + 3 * air, -8 * air])) for k in rest_feet}
    solve_legs(sk, p, feet)
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        if enraged:
            ground = K(t, [(0, 0), (.4, 55), (.6, -10), (.75, 30)]) * (1 - air) + K(t, [(2.3, 50), (2.5, 60), (2.7, 20), (3.0, 0)]) * smooth(t, 2.25, 2.35)
            add(p, f"{arm}_{side}", (0, 0, (ground + (25 + 35 * beat) * air) * .6 * sg))
        else:                                       # wide open for the glide, a flare on landing
            open_wing(p, side, smooth(t, .3, .6) * (1 - smooth(t, 2.6, 3.0)) + .3 * bump(t, 2.2, 2.4, 2.8), beat)
    add(p, "neck", (K(t, [(0, 0), (.4, 10), (.8, -8), (2.0, -4), (2.4, 10), (3.0, 0)]) + (-10 if not enraged else 0), 0, 0))
    if not enraged:
        add(p, "skull", (8, 0, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (K(t, [(0, 0), (.8, 14), (2.0, 10), (2.45, -10), (2.8, 4), (3.0, 0)]) * (1 + .2 * k), 3 * beat, 0))
    return p


AWAKEN_SECONDS = 3.0


def awaken_pose(sk, rest_feet, t):
    """第2形態への目覚め (2026-10-19, HP70 %), 3.0 s, wings still folded-arm form: shudders low with the wings clamped
    while the shade starts to glow through it, rears onto the hind legs with the wings thrown wide and roars at the
    sky (1.3-2.3 s), and drops back onto the wing-arms. The server switches the glowing texture in at the roar."""
    p = {}
    rear = smooth(t, .9, 1.3) * (1 - smooth(t, 2.3, 2.6))
    trem = math.sin(t * 2 * math.pi * 9) * smooth(t, .1, .3) * (1 - smooth(t, .8, .95))
    roar = math.sin(t * 2 * math.pi * 7) * smooth(t, 1.3, 1.45) * (1 - smooth(t, 2.1, 2.3))
    body(sk, p, (K(t, [(0, 0), (.4, 6), (.9, 8), (1.3, -30), (2.2, -28), (2.6, 6), (3.0, 0)]), 2 * trem, 2 * trem + 1.5 * roar),
         (0, K(t, [(0, 0), (.4, -6), (.9, -7), (1.3, 10), (2.2, 9), (2.55, -6), (3.0, 0)]), K(t, [(0, 0), (1.3, -8), (2.3, -8), (2.6, 2), (3.0, 0)])))
    feet = {k: rest_feet[k] + (np.array([0, 30 * rear, -10 * rear]) if k.startswith("f") else np.zeros(3)) for k in rest_feet}
    solve_legs(sk, p, feet)
    add(p, "neck", (K(t, [(0, 0), (.9, 6), (1.3, -30), (2.2, -28), (2.6, -6), (3.0, -10)]), 3 * roar, 0))
    add(p, "skull", (K(t, [(0, 0), (1.3, -14), (2.2, -14), (2.6, 8), (3.0, 8)]), 0, 2 * roar))
    add(p, "jaw", (K(t, [(0, 0), (1.2, 6), (1.4, 42), (2.1, 40), (2.4, 6), (3.0, 0)]), 0, 0))
    for sd, sg in (("l", 1), ("r", -1)):
        open_wing(p, sd, 1.15 * rear, .15 * trem + .25 * roar)
        add(p, f"crown_{sd}", (-10 * rear, 12 * roar * sg, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (K(t, [(0, 0), (1.3, 16), (2.2, 14), (2.6, -6), (3.0, 0)]) * (1 + .2 * k), 4 * trem, 0))
    return p

def channel_pose(sk, rest_feet, theta, t, enraged):
    """翳の大詠唱 (2026-10-19, MMO gimmick: interrupt it), 7.2 s, impact at 6.0 s. After Shagaru Magala's nova: beats up
    off the ground and hangs in the air, body arched back, head thrown up and the wings (fans) spread wide like a canopy,
    slow heavy wingbeats while the dust gathers (it rises a little more as the cast fills); if nobody breaks it, it
    folds, plunges nose-first and slams the wings into the ground at 6.0 s (the arena-wide blast), then rises."""
    p = {}
    air = smooth(t, .45, .8) * (1 - smooth(t, 5.75, 5.98))
    y = K(t, [(0, 0), (.4, -8), (.8, 34), (1.4, 44), (3.5, 50), (5.6, 56), (5.8, 60), (6.0, -8), (6.15, -10), (6.8, -3), (7.2, 0)])
    beat = math.sin(2 * math.pi * 1.1 * (t - .8)) * smooth(t, .8, 1.2) * (1 - smooth(t, 5.6, 5.8))
    y += 4 * beat
    arch = K(t, [(0, 0), (.4, 8), (.8, -6), (1.4, -12), (5.5, -14), (5.8, 18), (6.0, 14), (6.4, 6), (7.2, 0)])
    if enraged:
        enraged_stance(sk, p, theta * (1 - air))
    body(sk, p, (arch, 0, 2 * beat * .5), (0, y, 0))
    tuck = np.array([0, y + 6 * air, 6 * air])
    feet = {k: rest_feet[k] + (tuck if k.startswith("f") else np.array([0, y + 12 * air, 4 * air])) for k in rest_feet}
    solve_legs(sk, p, feet)
    arm = "cloak" if enraged else "wing"
    slam = bump(t, 5.85, 6.02, 6.7)
    for sd, sg in (("l", 1), ("r", -1)):
        # wings: wide canopy (z +50, y -30, forearm +25 opens the membrane); fans: raised tall (z +30)
        spread = (50 if not enraged else 30) * air + (16 if not enraged else 10) * beat
        z = K(t, [(0, 0), (.4, 30), (.6, -10)]) * (1 - smooth(t, .6, .8)) + spread - (40 if not enraged else 25) * slam
        add(p, f"{arm}_{sd}", (0, (-30 * air if not enraged else -10 * air) * sg, z * sg))
        if not enraged:
            add(p, f"wing_{sd}_fore", (0, 0, (25 * air + 14 * beat - 20 * slam) * sg))
            for h in ("hinge02", "hinge09", "hinge10", "hinge14"):
                add(p, f"wing_{sd}_{h}", (0, 0, 14 * air * sg))
        add(p, f"crown_{sd}", (-14 * air, 14 * math.sin(t * 2 * math.pi * 5) * air * sg, 0))
    add(p, "neck", (-34 * air + 30 * slam + (-10 if not enraged else 0), 0, 0))
    add(p, "skull", (-20 * air + 14 * slam + (8 if not enraged else 0), 0, 0))
    add(p, "jaw", (24 * air + 36 * bump(t, 5.95, 6.05, 6.6), 0, 0))
    for k, b in enumerate(TAIL):                  # the tail curls under as a counterweight
        add(p, b, ((-10 * air - 16 * slam) * (1 + .3 * k), 6 * beat, 0))
    return p, None


def shell_pose(sk, rest_feet, theta, t, enraged):
    """鱗粉の殻 (2026-10-19, MMO gimmick: break the shell in time), enraged, 10.2 s, burst at 9.0 s (review 2026-10-19: 12 s was too long): curls up tight,
    head tucked under and the tail drawn round to it, and folds the fans in over itself like a cocoon (they draw in to
    0.6 size and wrap round the front; the dust hardens into a shell round it, a server display); breathes slowly inside,
    trembling more as the shell nears bursting; if it holds to 9.0 s, the fans fly open and it rears with a roar."""
    p = {}
    wrap = smooth(t, .2, 1.2) * (1 - smooth(t, 8.75, 9.0))
    breathe = math.sin(t * 2 * math.pi * .45) * wrap
    trem = math.sin(t * 2 * math.pi * 10) * smooth(t, 5.8, 7.6) * (1 - smooth(t, 8.7, 8.9))
    burst = bump(t, 8.85, 9.05, 9.9)
    enraged_stance(sk, p, theta * (1 - wrap))
    body(sk, p, (10 * wrap - 30 * burst, 6 * wrap, .8 * trem), (0, -14 * wrap + 2 * breathe + 10 * burst, -4 * wrap))
    feet = {k: v + np.array([0, 0, (4 if k.startswith("h") else -2) * wrap]) for k, v in rest_feet.items()}
    solve_legs(sk, p, feet)
    add(p, "neck", (38 * wrap - 30 * burst, 22 * wrap, 0))
    add(p, "skull", (24 * wrap - 16 * burst, 18 * wrap, 0))
    add(p, "jaw", (40 * burst, 0, 0))
    k = (1 - .4 * wrap) * (1 + .15 * burst)
    for sd, sg in (("l", 1), ("r", -1)):
        add(p, f"cloak_{sd}", (10 * wrap, (-80 * wrap + 30 * burst) * sg, (35 * wrap + 3 * breathe + 2 * trem + 40 * burst) * sg))
        scale(p, f"cloak_{sd}", k)
    for i, b in enumerate(TAIL):
        add(p, b, (6 * wrap, 34 * wrap * (1 + .15 * i), 0))
    return p, None


def down_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    """ダウン (2026-10-19: the cast broken or the shell shattered), 4.0 s: knocked off its feet, it rolls onto its side
    (the springs make the wings/fans and tail flop), the legs paddle and the head thrashes on the ground; at 2.6 s it
    rolls back, plants the wing-arms and heaves itself up."""
    p = {}
    roll = K(t, [(0, 0), (.15, 20), (.45, 64), (.6, 58), (.75, 62), (2.5, 60), (3.0, 20), (3.3, -6), (3.6, 2), (4.0, 0)])
    drop = smooth(t, 0, .45) * (1 - smooth(t, 2.8, 3.6))
    thrash = math.sin(t * 2 * math.pi * 2.6) * smooth(t, .7, .9) * (1 - smooth(t, 2.3, 2.6))
    if enraged:
        enraged_stance(sk, p, theta * (1 - drop))
    body(sk, p, (6 * drop, 6 * thrash, roll), (0, -18 * drop, 0))
    feet = {k: v + np.array([0, -4 * drop, 0]) for k, v in rest_feet.items()}
    solve_legs(sk, p, feet)
    for k_, (r, m, e) in LEGS.items():            # the legs paddle in the air while it lies on its side
        f = roll / 62
        paddle = math.sin(t * 2 * math.pi * 3 + (0 if k_ in ("fl", "hr") else math.pi)) * f * (1 - smooth(t, 2.3, 2.6))
        add(p, r, ((-30 if k_.startswith("f") else 20) * f + 25 * paddle, 0, (-25 if k_.endswith("l") else 15) * f))
    add(p, "neck", (24 * drop, 16 * thrash, -18 * roll / 62)); add(p, "skull", (12 * drop, 14 * thrash, 0))
    add(p, "jaw", (26 * bump(t, .1, .3, 1.0) + 20 * abs(thrash), 0, 0))
    arm = "cloak" if enraged else "wing"
    for sd, sg in (("l", 1), ("r", -1)):
        flop = K(t, [(0, 0), (.2, 30), (.6, -10), (2.6, -10), (3.0, 40), (3.4, 10), (4.0, 0)]) - (35 * drop if enraged else 0)
        add(p, f"{arm}_{sd}", (0, (12 * drop * sg if enraged else 0), flop * sg + (30 if sd == "r" else -10) * roll / 62))
    for i, b in enumerate(TAIL):
        add(p, b, (-6 * drop, 12 * thrash * (1 + .3 * i), 0))
    return p

def charge_pose(sk, rest_feet, theta, t, enraged):
    """突進 (2026-10-19, 藍「動き回らない・離れては殴るだけ」), 3.0 s, hit at 1.4 s: digs in low with the head down and
    the wing-arms drawn back; bounds across the arena on all fours, body stretched long and low, the wings half open and
    trailing (the server carries it ~14 blocks between 0.8 and 2.0 s); skids round side-on to a stop and shakes itself."""
    run = smooth(t, .75, .9) * (1 - smooth(t, 1.95, 2.15))
    phase = (max(0.0, t - .8) * 3.2) % 1.0
    p = walk_pose(sk, rest_feet, phase, enraged, theta) if run > .01 else {}
    if run <= .01 and enraged:
        enraged_stance(sk, p, theta)
    if run <= .01:
        solve_legs(sk, p, rest_feet)
    dig = smooth(t, 0, .5) * (1 - smooth(t, .75, .9))
    skid = bump(t, 1.95, 2.2, 2.7)
    gallop = math.sin(phase * 2 * math.pi) * run
    body(sk, p, (10 * dig + 6 * run + 4 * gallop, 35 * skid, -10 * skid), (0, -6 * dig - 4 * run + 3 * abs(gallop), 0))
    add(p, "neck", (18 * dig + 10 * run - 6 * skid, -14 * skid, 0))
    add(p, "skull", (10 * dig + 6 * run, -10 * skid, 0))
    add(p, "jaw", (20 * run + 16 * bump(t, 2.2, 2.4, 2.8), 0, 0))
    for sd, sg in (("l", 1), ("r", -1)):
        if enraged:
            add(p, f"cloak_{sd}", (0, (14 * run) * sg, (-12 * dig + 10 * run + 6 * gallop) * sg))
        else:
            open_wing(p, sd, .45 * run + .2 * skid, .5 * gallop)
    for k, b in enumerate(TAIL):
        add(p, b, (-6 * run, (8 * gallop - 30 * skid) * (1 + .2 * k), 0))
    return p, None


def flinch_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    """怯み (a part broke), 1.4 s: snaps back and up, shakes its head, the fans jerk out, then gathers itself."""
    p = {}
    if enraged:
        enraged_stance(sk, p, theta)
    snap = K(t, [(0, 0), (.1, 1), (.5, .7), (1.4, 0)])
    shake = math.sin(t * 2 * math.pi * 6) * smooth(t, .1, .2) * (1 - smooth(t, .6, .9))
    body(sk, p, (-10 * snap, 4 * shake, 3 * shake), (0, 2 * snap, -3 * snap))
    solve_legs(sk, p, dict(rest_feet))
    add(p, "neck", (-24 * snap, 14 * shake, 0)); add(p, "skull", (-18 * snap, 10 * shake, 6 * shake))
    add(p, "jaw", (30 * K(t, [(0, 0), (.1, 1), (.6, .6), (1.1, 0)]), 0, 0))
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{side}", (0, 0, 16 * bump(t, 0, .12, .7) * sg))
    for k, b in enumerate(TAIL):
        add(p, b, (-10 * snap, 8 * shake * (1 + .3 * k), 0))
    return p


SEARCH = dict(stride=18, duty=.72, cycle=2.0)


def search_pose(sk, rest_feet, t):
    """探りの歩み (loop): lost its prey, it slows to a creeping walk, head low and sweeping side to side, the antennae
    flicking; every other step it stops a beat to listen."""
    duty, stride = SEARCH["duty"], SEARCH["stride"]
    ph = {"hl": 0, "fl": .25, "hr": .5, "fr": .75}
    w = 2 * math.pi * t
    p = {}
    body(sk, p, (4, 3 * math.sin(w), 0), (0, -4, 0))
    targets = {k: rest_feet[k] + np.array([0, *foot_offset((t + ph[k]) % 1, duty, stride, 7 if k.startswith("f") else 3, k.startswith("f"))]) for k in LEGS}
    solve_legs(sk, p, targets)
    for k in ("fl", "fr"):
        add(p, LEGS[k][2], (45 * swing_curl((t + ph[k]) % 1, duty), 0, 0))
    sweep = math.sin(w) * 28
    add(p, "neck", (12, sweep * .6, 0)); add(p, "skull", (6 + 4 * math.sin(2 * w), sweep * .5, 6 * math.sin(w)))
    flick = bump(t, .2, .24, .32) + bump(t, .62, .66, .74)
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"crown_{side}", (-10 * flick, 14 * flick * sg, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (3, 4 * (1 + .4 * k) * math.sin(w - .7 * (k + 1)), 0))
    return p


POSES = {"shade-sweep": sweep_pose, "shade-pounce": pounce_pose, "shade-dust": dust_pose,
         "shade-pulse": pulse_pose, "shade-breath": breath_pose, "shade-storm": storm_pose,
         "shade-turnsweep": turnsweep_pose, "shade-dive": dive_pose, "shade-bite": bite_pose,
         "shade-tailspin": tailspin_pose, "shade-horn": horn_pose, "shade-tailslap": tailslap_pose, "shade-orb": orb_pose,
         "shade-channel": channel_pose, "shade-shell": shell_pose, "shade-charge": charge_pose}


def ability_pose(sk, rest_feet, theta, name, t, enraged):
    p, feet = POSES[name](sk, rest_feet, theta, t, enraged)
    if feet is None:                               # the pose placed its own stance and feet
        return p
    if enraged:
        enraged_stance(sk, p, theta)
    solve_legs(sk, p, feet)
    for k in ("fl", "fr"):                         # a lifted forepaw folds its wrist under
        add(p, LEGS[k][2], (40 * min(1.0, max(0.0, feet[k][1] - rest_feet[k][1]) / 20), 0, 0))
    return p


def idle_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    """4 s loop: breathing, the blind head sensing on a slow figure-eight, the antennae twitching twice, a weight
    shift from side to side, the tail swaying late."""
    w = 2 * math.pi * t
    p = {}
    if enraged:
        enraged_stance(sk, p, theta)
    body(sk, p, (.8 * math.sin(2 * w), 1.2 * math.sin(w), .8 * math.sin(w + .6)),
         (.8 * math.sin(w), -.7 * math.sin(2 * w) ** 2, 0))
    solve_legs(sk, p, rest_feet)
    if not enraged:
        add(p, "neck", (-10, 0, 0)); add(p, "skull", (8, 0, 0))
    add(p, "neck", (-2 * math.sin(2 * w), 5 * math.sin(w + .3), 0))
    add(p, "skull", (2.5 * math.sin(2 * w + 1.2), 7 * math.sin(w + .9), 2 * math.sin(w)))
    twitch = bump(t, .3, .33, .4) - .6 * bump(t, .74, .77, .86)
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"crown_{side}", (-6 * twitch, 10 * twitch * sg, 0))
        if enraged:
            add(p, f"horn_{side}", (-3 * twitch, 6 * twitch * sg, 0))
            add(p, f"cloak_{side}", (0, 0, 2 * math.sin(2 * w + (0 if sg > 0 else .5)) * sg))
        else:
            add(p, f"wing_{side}", (0, 0, 1.2 * math.sin(2 * w + .4) * sg))
    add(p, "jaw", (4 * bump(t, .55, .6, .7), 0, 0))
    for k, b in enumerate(TAIL):
        add(p, b, (1.5 * math.sin(2 * w - .5 * k), 4 * (1 + .4 * k) * math.sin(w - .7 * (k + 1)), 0))
    return p


def death_pose(sk, rest_feet, theta, t, enraged):
    """Staggers back with the head thrown up, the legs give out, it rolls onto its side and goes still (the clip
    holds its last frame); the springs make the neck, tail and wings flop."""
    p = {}
    if enraged:
        enraged_stance(sk, p, theta * (1 - smooth(t, .2, .8)))
    arm = "cloak" if enraged else "wing"
    roll = K(t, [(0, 0), (1.0, 0), (1.7, 62), (1.85, 58), (2.0, 62)])
    body(sk, p, (K(t, [(0, 0), (.25, -8), (.7, -2), (1.3, 6)]), K(t, [(0, 0), (.3, -6), (.9, 4), (1.4, 0)]), roll),
         (0, K(t, [(0, 0), (.25, 3), (.5, 1), (1.3, -16), (1.7, -20), (1.75, -22), (1.9, -20)]),
          K(t, [(0, 0), (.3, -4), (.9, -2)])))
    add(p, "neck", (K(t, [(0, 0), (.25, -22), (.6, -10), (1.3, 30)]), 0, K(t, [(0, 0), (1.0, 0), (1.7, -20)])))
    add(p, "skull", (K(t, [(0, 0), (.25, -16), (1.3, 12)]), 0, 0))
    add(p, "jaw", (K(t, [(0, 0), (.3, 32), (.9, 8), (1.7, 14)]), 0, 0))
    for b in TAIL:
        add(p, b, (K(t, [(0, 0), (1.3, 6)]), K(t, [(0, 0), (1.0, 0), (1.7, -10)]), 0))
    feet = {k: v + np.array([0, -4 * smooth(t, .5, 1.3), 0]) for k, v in rest_feet.items()}
    solve_legs(sk, p, feet)
    for k, (r, m, e) in LEGS.items():             # limp: the legs fold toward the belly as it rolls
        f = roll / 62
        add(p, r, (-30 * f if k.startswith("f") else 20 * f, 0, (-25 if k.endswith("l") else 15) * f))
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"{arm}_{side}", (0, 0, K(t, [(0, 0), (.25, 30), (.7, 10), (1.3, -10)]) * sg + (40 if side == "r" else -10) * roll / 62))
    return p


# Secondary motion: each listed bone lags the motion of the bones above it (a damped spring driven by their
# angular acceleration, and by the body's vertical acceleration), overshoots and settles.
# (bone, drivers, gain per axis, frequency Hz, damping ratio, gain on body height for its x/z)
SPRINGS = [("neck", ("body",), (.15, .3, .25), 2.2, .55, (-.8, 0)),
           ("skull", ("body", "neck"), (.15, .4, .3), 2.8, .55, (-.4, 0)),
           ("jaw", ("neck", "skull"), (.3, 0, 0), 4.0, .4, (-.6, 0)),
           ("tail", ("body",), (.35, .5, .2), 2.0, .4, (1.0, 0)),
           ("tail_2", ("body", "tail"), (.45, .6, .2), 2.2, .4, (.8, 0)),
           ("tail_mid", ("body", "tail", "tail_2"), (.5, .7, .2), 2.5, .38, (.6, 0)),
           ("tail_end", ("body", "tail", "tail_2", "tail_mid"), (.55, .8, .2), 2.8, .35, (.5, 0))]
for _s, _g in (("l", 1), ("r", -1)):
    SPRINGS += [(f"crown_{_s}", ("neck", "skull"), (.5, .5, .3), 4.5, .3, (-.6, 0)),
                (f"horn_{_s}", ("neck", "skull"), (.4, .4, .3), 4.0, .3, (-.5, 0)),
                (f"cloak_{_s}", ("body",), (.12, .12, .2), 2.4, .5, (0, .5 * _g)),
                (f"wing_{_s}_fore", ("body", f"wing_{_s}"), (.3, .3, .4), 2.5, .45, (0, .6 * _g)),
                (f"wing_{_s}_hand", ("body", f"wing_{_s}", f"wing_{_s}_fore"), (.35, .35, .45), 3.0, .4, (0, .5 * _g))]
    SPRINGS += [(f"wing_{_s}_{h}", ("body", f"wing_{_s}", f"wing_{_s}_fore"), (.3, .3, .5), 3.2 + .3 * k, .35, (0, .8 * _g))
                for k, h in enumerate(("hinge02", "hinge09", "hinge10", "hinge14"))]


def springs(sk, poses, dt, loop):
    """Adds the spring lag to `poses` sampled every dt seconds (for a loop, run three cycles and keep the last)."""
    n = len(poses)
    rot = lambda pose, b: pose.get(b, (np.zeros(3), np.zeros(3)))[0]
    height = np.array([pose.get("body", (np.zeros(3), np.zeros(3)))[1][1] for pose in poses])
    out = [[] for _ in range(n)]
    for bone, drivers, gain, hz, zeta, hgain in SPRINGS:
        if bone not in sk.bones:
            continue
        drive = np.array([sum((rot(pose, d) for d in drivers), np.zeros(3)) for pose in poses])
        drive[:, 0] += hgain[0] * height; drive[:, 2] += hgain[1] * height
        if loop:
            acc = (np.roll(drive, -1, 0) - 2 * drive + np.roll(drive, 1, 0)) / dt ** 2
        else:
            acc = np.zeros_like(drive); acc[1:-1] = (drive[2:] - 2 * drive[1:-1] + drive[:-2]) / dt ** 2
        w = 2 * math.pi * hz
        x, v = np.zeros(3), np.zeros(3)
        res = np.zeros((n, 3))
        for cycle in range(3 if loop else 1):
            for i in range(n):
                for _ in range(4):
                    a = -w * w * x - 2 * zeta * w * v - np.array(gain) * acc[i]
                    v = v + a * dt / 4; x = x + v * dt / 4
                res[i] = x
        limit = 8 if bone in ("neck", "skull") else 35      # more and the neck plates cut through the face
        out_bone = np.clip(res, -limit, limit)
        for i in range(n):
            out[i].append((bone, out_bone[i]))
    for i, pose in enumerate(poses):
        fade = 1.0 if loop else 1 - smooth(i / (n - 1), .88, 1.0)
        for bone, x in out[i]:
            add(pose, bone, tuple(x * fade))
    return poses


def baked(sk, name, length, fn, loop, fps=20, sub=3):
    """Samples fn(t) at fps*sub, adds the springs, keeps every sub-th frame as a keyframe."""
    n = int(round(length * fps)) * sub
    poses = [add_wind(fn(length * i / n), length * i / n, length if loop else None) for i in range(n + (0 if loop else 1))]
    poses = springs(sk, poses, length / n, loop)
    if loop:
        poses.append(poses[0])
    return clip(sk, name, length, n // sub, poses[::sub], "loop" if loop else "once")


def thin(values, tol):
    """(index, value) keyframes that rebuild values within tol by linear interpolation (first and last kept): the wind
    put every wing and fan bone in every clip, and a key on every frame made the model too big to load."""
    v = [np.asarray(x, float) for x in values]
    keep = [0]
    for i in range(1, len(v) - 1):
        a = keep[-1]
        # can the segment from the last kept key reach i + 1 without passing tol at any frame in between?
        b = i + 1
        ok = all(np.abs(v[a] + (v[b] - v[a]) * (j - a) / (b - a) - v[j]).max() <= tol for j in range(a + 1, b))
        if not ok:
            keep.append(i)
    keep.append(len(v) - 1)
    return [(i, v[i]) for i in sorted(set(keep))]


def clip(sk, name, length, frames, poses, loop):
    animators = {}
    for b in sk.order:
        rot = [pose.get(b, (np.zeros(3), np.zeros(3)))[0] for pose in poses]
        pos = [pose.get(b, (np.zeros(3), np.zeros(3)))[1] for pose in poses]
        scl = [(pose.get(b, (0, 0)) + (O3,))[2] for pose in poses]
        keys = []
        if any(np.abs(r).max() > 1e-3 for r in rot):
            keys += [("rotation", i, (-r[0], r[1], r[2])) for i, r in thin(rot, .25)]
        if any(np.abs(q).max() > 1e-3 for q in pos):
            keys += [("position", i, (q[0], q[1], q[2])) for i, q in thin(pos, .08)]
        if any(np.abs(k - 1).max() > 1e-3 for k in scl):
            keys += [("scale", i, (k[0], k[1], k[2])) for i, k in thin(scl, .005)]
        if keys:
            animators[sk.bones[b]["uuid"]] = {"name": b, "type": "bone", "keyframes": [
                {"channel": ch, "data_points": [{"x": round(float(v[0]), 3), "y": round(float(v[1]), 3), "z": round(float(v[2]), 3)}],
                 "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/boss/shade_wyrm/{name}/{b}/{ch}/{i}")),
                 "time": round(length * i / frames, 4), "color": -1, "interpolation": "linear"} for ch, i, v in keys]}
    return {"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/boss/shade_wyrm/{name}")), "name": name, "loop": loop,
            "override": False, "length": length, "snapping": 20, "selected": False, "anim_time_update": "",
            "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}


def main(path=MODEL, carpal_drop=12.0):
    """carpal_drop: how far above the ground the build places the planted wing-arm's carpal (FAN_WING drop)."""
    data = json.loads(path.read_text(encoding="utf-8"))
    sk = Skeleton(data)
    rest_feet = {k: sk.point(v[2]) for k, v in LEGS.items()}
    theta = crouch_angle(data, carpal_drop)
    new = {}
    for name, enraged in (("walk", False), ("walk_enraged", True)):
        new[name] = baked(sk, name, round(walk_cycle(enraged), 2), lambda t, e=enraged: walk_pose(sk, rest_feet, t / round(walk_cycle(e), 2), e, theta), True)
    for suffix, enraged in (("", False), ("_enraged", True)):
        for side, sign in (("l", 1), ("r", -1)):
            T = round(strafe_cycle(), 2)
            new[f"strafe_{side}{suffix}"] = baked(sk, f"strafe_{side}{suffix}", T,
                                                  lambda t, sg=sign, e=enraged: strafe_pose(sk, rest_feet, t / T, sg, e, theta), True)
            new[f"dash_{side}{suffix}"] = baked(sk, f"dash_{side}{suffix}", .9,
                                                lambda t, sg=sign, e=enraged: dash_side_pose(sk, rest_feet, t, sg, e, theta), False)
    new["search"] = baked(sk, "search", SEARCH["cycle"], lambda t: search_pose(sk, rest_feet, t / SEARCH["cycle"]), True)
    for suffix, enraged in (("", False), ("_enraged", True)):
        new[f"flinch{suffix}"] = baked(sk, f"flinch{suffix}", 1.4, lambda t, e=enraged: flinch_pose(sk, rest_feet, t, e, theta), False)
    for suffix, enraged in (("", False), ("_enraged", True)):
        new[f"hop_back{suffix}"] = baked(sk, f"hop_back{suffix}", 1.1, lambda t, e=enraged: hop_back_pose(sk, rest_feet, t, e, theta), False)
    for suffix, enraged in (("", False), ("_enraged", True)):
        new[f"relocate{suffix}"] = baked(sk, f"relocate{suffix}", 3.0, lambda t, e=enraged: relocate_pose(sk, rest_feet, t, e, theta), False)
    for suffix, enraged in (("", False), ("_enraged", True)):
        new[f"down{suffix}"] = baked(sk, f"down{suffix}", 4.0, lambda t, e=enraged: down_pose(sk, rest_feet, t, e, theta), False)
    new["awaken"] = baked(sk, "awaken", AWAKEN_SECONDS, lambda t: awaken_pose(sk, rest_feet, t), False)
    n = int(ENRAGE_SECONDS * 20)
    new["enrage"] = clip(sk, "enrage", ENRAGE_SECONDS, n,
                         [enrage_pose(sk, rest_feet, theta, ENRAGE_SECONDS * i / n) for i in range(n + 1)], "once")
    new["idle"] = baked(sk, "idle", 4.0, lambda t: idle_pose(sk, rest_feet, t / 4.0), True)
    new["idle_enraged"] = baked(sk, "idle_enraged", 4.0, lambda t: idle_pose(sk, rest_feet, t / 4.0, True, theta), True)
    for name, (hit, length) in ABILITIES.items():
        for enraged in ((False, True) if name in PHASE_ONE else (True,)):
            key = name + ("_enraged" if enraged and name in PHASE_ONE else "")
            new[key] = baked(sk, key, length, lambda t: ability_pose(sk, rest_feet, theta, name, t, enraged), False)
    for key, enraged in (("death", False), ("death_enraged", True)):
        new[key] = baked(sk, key, 2.4, lambda t: death_pose(sk, rest_feet, theta, t, enraged), False)
    names = [a["name"] for a in data["animations"]]
    data["animations"] = [new.get(a["name"], a) for a in data["animations"]] + [c for k, c in new.items() if k not in names]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{path.relative_to(ROOT)}: wrote {', '.join(new)}")


if __name__ == "__main__":
    main()
