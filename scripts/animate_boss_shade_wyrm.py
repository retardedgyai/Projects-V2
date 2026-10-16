"""Motion for the 黒翳竜 (shade wyrm): writes the walk clips into assets/bosses/shade_wyrm.bbmodel.

    python scripts/animate_boss_shade_wyrm.py   # run after build_boss_shade_wyrm.py (the build calls it)

The walk is the "noble" prowl the Creator chose (2026-10-16): a slow lateral-sequence walk (left hind,
left fore, right hind, right fore), the feet planted on the ground by a two-bone IK in each leg's side
plane, the front paws lifted high with the wrist folded under and set down softly, the neck arched and
the head carried high and still, the body barely moving, the tail swaying late. walk_enraged is the
same gait in the enraged "C" stance (crouched, wing-arms swung forward, the cloak over the head).

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
    r0, p0 = pose.get(bone, (np.zeros(3), np.zeros(3)))
    pose[bone] = (r0 + np.array(rot, float), p0 + np.array(pos, float))


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


def mirror(pose, bone, rot, pos=(0, 0, 0)):
    add(pose, bone, rot, pos)
    add(pose, bone.replace("_l", "_r"), (rot[0], -rot[1], -rot[2]), (-pos[0], pos[1], pos[2]))


def enraged_stance(pose):
    """The enraged "C" stance: crouched, the wing-arms swung forward and the cloak held over the head."""
    add(pose, "body", (6, 0, 0), (0, -5, 0))
    add(pose, "neck", (14, 0, 0))
    mirror(pose, "wing_l", (-15, -55, 35))
    mirror(pose, "wing_l_membrane", (-35, -35, 10))


def walk_pose(sk, rest_feet, t, enraged=False):
    duty, stride, lift = .7, 18, 11
    ph = {"hl": 0, "fl": .25, "hr": .5, "fr": .75}
    roll, bob, sway = 1.5, .35, .6
    s = math.sin(2 * math.pi * t)
    p = {}
    if enraged:
        enraged_stance(p)
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
        keys = []
        if any(np.abs(r).max() > 1e-3 for r in rot):
            for i, r in enumerate(rot):
                keys.append(("rotation", i, (-r[0], r[1], r[2])))
        if any(np.abs(q).max() > 1e-3 for q in pos):
            for i, q in enumerate(pos):
                keys.append(("position", i, (q[0], q[1], q[2])))
        if keys:
            animators[sk.bones[b]["uuid"]] = {"name": b, "type": "bone", "keyframes": [
                {"channel": ch, "data_points": [{"x": round(float(v[0]), 3), "y": round(float(v[1]), 3), "z": round(float(v[2]), 3)}],
                 "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/boss/shade_wyrm/{name}/{b}/{ch}/{i}")),
                 "time": round(length * i / frames, 4), "color": -1, "interpolation": "linear"} for ch, i, v in keys]}
    return {"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/boss/shade_wyrm/{name}")), "name": name, "loop": loop,
            "override": False, "length": length, "snapping": 20, "selected": False, "anim_time_update": "",
            "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}


def main(path=MODEL):
    data = json.loads(path.read_text(encoding="utf-8"))
    sk = Skeleton(data)
    rest_feet = {k: sk.point(v[2]) for k, v in LEGS.items()}
    frames, length = 16, 2.4
    new = {}
    for name, enraged in (("walk", False), ("walk_enraged", True)):
        poses = [walk_pose(sk, rest_feet, i / frames, enraged) for i in range(frames + 1)]
        new[name] = clip(sk, name, length, frames, poses, "loop")
    data["animations"] = [new.get(a["name"], a) for a in data["animations"]]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{path.relative_to(ROOT)}: wrote {', '.join(new)}")


if __name__ == "__main__":
    main()
