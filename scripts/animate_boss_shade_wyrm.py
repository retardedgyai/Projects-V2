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
            r, pos = pose.get(name, (np.zeros(3), np.zeros(3)))
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


def solve_legs(sk, pose, targets):
    """Two-bone IK in each leg's y-z plane; the paw counter-rotates to stay flat."""
    M = sk.mats(pose)
    for key, (r, m, e) in LEGS.items():
        R0, j1, j2 = sk.bones[r]["pivot"], sk.bones[m]["pivot"], sk.bones[e]["pivot"]
        L1 = np.linalg.norm((j1 - R0)[1:]); L2 = np.linalg.norm((j2 - j1)[1:])
        P = M[sk.bones[r]["parent"]]
        d = np.linalg.inv(P[:3, :3]) @ (targets[key] - P[:3, 3]) - R0
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
        add(pose, r, (q1, 0, 0)); add(pose, m, (q2, 0, 0)); add(pose, e, (-(q1 + q2), 0, 0))


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


def walk_pose(sk, rest_feet, t, enraged=False, theta=0.0):
    duty, stride, lift = .7, 18, 11
    ph = {"hl": 0, "fl": .25, "hr": .5, "fr": .75}
    roll, bob, sway = 1.5, .35, .6
    s = math.sin(2 * math.pi * t)
    p = {}
    if enraged:
        enraged_stance(sk, p, theta)
        stride, lift = 20, 8
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
             "shade-pulse": (1.2, 2.3), "shade-breath": (1.6, 2.6), "shade-storm": (2.2, 3.5)}
PHASE_ONE = ("shade-sweep", "shade-pounce", "shade-dust", "shade-pulse")
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
    if not enraged:
        add(p, "wing_l_fore", (0, 0, K(t, [(0, 0), (.9, -20), (1.05, -30), (1.15, 25), (1.3, 10), (1.7, 0)])))
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
        add(p, f"{arm}_{side}", (0, -10 * smooth(t, 1.1, 1.28) * (1 - smooth(t, 1.4, 1.6)) * sg, (.6 if enraged else 1) * spread * sg))
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
    body(sk, p, (K(t, [(0, 0), (.7, -6), (1.05, -8), (1.2, 4), (1.5, 2), (2.0, 0)]), 0, .6 * shiver(t, 1.2, 1.6, 13)),
         (0, K(t, [(0, 0), (.7, 4), (1.05, 5), (1.2, -3), (1.35, -2), (1.8, 0)]), 0))
    trem = shiver(t, .7, 1.2, 14)
    add(p, "neck", (K(t, [(0, 0), (.4, -15), (.9, -38), (1.08, -42), (1.2, 20), (1.35, 24), (1.8, 4), (2.3, 0)]),
                    K(t, [(0, 0), (.4, 6), (.7, -6), (1.0, 4), (1.2, 0)]), 0))
    add(p, "skull", (K(t, [(0, 0), (.9, -18), (1.1, -20), (1.2, 12), (1.4, 8), (2.0, 0)]) + 1.5 * trem,
                     K(t, [(0, 0), (.5, -8), (.8, 8), (1.1, 0)]), 1.5 * trem))
    add(p, "jaw", (K(t, [(0, 0), (.9, 8), (1.15, 12), (1.22, 40), (1.6, 30), (1.9, 0)]), 0, 0))
    spread = K(t, [(0, 0), (.6, 20), (1.1, 28), (1.2, -6), (1.5, 6), (2.0, 0)])
    arm = "cloak" if enraged else "wing"
    for side, sg in (("l", 1), ("r", -1)):
        add(p, f"crown_{side}", (-.4 * spread, (spread + 3 * trem) * sg, 0))
        if enraged:
            add(p, f"horn_{side}", (-.5 * spread, (.6 * spread + 3 * trem) * sg, 0))
        add(p, f"{arm}_{side}", (0, 0, K(t, [(0, 0), (.7, -6), (1.2, 30), (1.4, 22), (1.9, 0)]) * (.6 if enraged else 1) * sg))
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
    add(p, "neck", (K(t, [(0, 0), (.8, -30), (1.45, -44), (1.6, 20), (1.75, 12), (2.0, 8), (2.5, 0)]) + 1.5 * shiver(t, 1.1, 1.5, 12),
                    K(t, [(0, 0), (.8, 24), (1.45, 30), (1.6, -6), (1.8, 0)]), 0))
    add(p, "skull", (K(t, [(0, 0), (.8, -10), (1.45, -14), (1.6, 6), (1.7, 12), (2.1, 0)]),
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


POSES = {"shade-sweep": sweep_pose, "shade-pounce": pounce_pose, "shade-dust": dust_pose,
         "shade-pulse": pulse_pose, "shade-breath": breath_pose, "shade-storm": storm_pose}


def ability_pose(sk, rest_feet, theta, name, t, enraged):
    p, feet = POSES[name](sk, rest_feet, theta, t, enraged)
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
SPRINGS = [("neck", ("body",), (.25, .3, .25), 2.2, .5, (-1.2, 0)),
           ("skull", ("body", "neck"), (.35, .4, .3), 2.8, .45, (-.8, 0)),
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
        out_bone = np.clip(res, -35, 35)
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
    poses = [fn(length * i / n) for i in range(n + (0 if loop else 1))]
    poses = springs(sk, poses, length / n, loop)
    if loop:
        poses.append(poses[0])
    return clip(sk, name, length, n // sub, poses[::sub], "loop" if loop else "once")


def clip(sk, name, length, frames, poses, loop):
    animators = {}
    for b in sk.order:
        rot = [pose.get(b, (np.zeros(3), np.zeros(3)))[0] for pose in poses]
        pos = [pose.get(b, (np.zeros(3), np.zeros(3)))[1] for pose in poses]
        scl = [(pose.get(b, (0, 0)) + (O3,))[2] for pose in poses]
        keys = []
        if any(np.abs(r).max() > 1e-3 for r in rot):
            for i, r in enumerate(rot):
                keys.append(("rotation", i, (-r[0], r[1], r[2])))
        if any(np.abs(q).max() > 1e-3 for q in pos):
            for i, q in enumerate(pos):
                keys.append(("position", i, (q[0], q[1], q[2])))
        if any(np.abs(k - 1).max() > 1e-3 for k in scl):
            for i, k in enumerate(scl):
                keys.append(("scale", i, (k[0], k[1], k[2])))
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
    frames, length = 16, 2.4
    new = {}
    for name, enraged in (("walk", False), ("walk_enraged", True)):
        poses = [walk_pose(sk, rest_feet, i / frames, enraged, theta) for i in range(frames + 1)]
        new[name] = clip(sk, name, length, frames, poses, "loop")
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
