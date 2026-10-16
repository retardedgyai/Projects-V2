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


def idle_enraged_pose(sk, rest_feet, theta, t):
    p = {}
    enraged_stance(sk, p, theta)
    add(p, "body", (0, 0, 0), (0, -.6 * math.sin(math.pi * t) ** 2, 0))
    solve_legs(sk, p, rest_feet)
    add(p, "neck", (-1.5 * math.sin(2 * math.pi * t), 0, 0))
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
    new["idle_enraged"] = clip(sk, "idle_enraged", 2.0, 8, [idle_enraged_pose(sk, rest_feet, theta, i / 8) for i in range(9)], "loop")
    n = int(ENRAGE_SECONDS * 20)
    new["enrage"] = clip(sk, "enrage", ENRAGE_SECONDS, n,
                         [enrage_pose(sk, rest_feet, theta, ENRAGE_SECONDS * i / n) for i in range(n + 1)], "once")
    data["animations"] = [new.get(a["name"], a) for a in data["animations"]]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{path.relative_to(ROOT)}: wrote {', '.join(new)}")


if __name__ == "__main__":
    main()
