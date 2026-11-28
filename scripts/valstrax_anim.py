"""Animations of the Valstrax study: idle (folded wings), walk, turn, wing_sweep, jet_stance.

Poses are functions of time, baked every 0.05 s. What makes them read as smooth (see docs/development/valstrax-study.md):
curves through the keys instead of straight lines, a wind-up before each move and a long settle after it, and parts
that trail the body - each tail bone follows the body's turn a little later than the one before, the neck leads it.
"""
import math

import numpy as np

STEP = .05
TAILS = [f"tail{i + 1}" for i in range(6)]


def RX(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def RY(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def RZ(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def axis_angle(axis, deg):
    x, y, z = np.array(axis, float) / (np.linalg.norm(axis) or 1)
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    K = np.array([[0, -z, y], [z, 0, -x], [-y, x, 0]])
    return np.eye(3) + s * K + (1 - c) * (K @ K)


def E(R):
    y = math.asin(max(-1.0, min(1.0, -R[2][0])))
    return (math.degrees(math.atan2(R[2][1], R[2][2])), math.degrees(y), math.degrees(math.atan2(R[1][0], R[0][0])))


def curve(keys):
    """A smooth curve through (time, value) keys: Catmull-Rom tangents, flat at both ends."""
    ts, vs = [k[0] for k in keys], [k[1] for k in keys]
    m = [0.0] * len(keys)
    for i in range(1, len(keys) - 1):
        m[i] = (vs[i + 1] - vs[i - 1]) / (ts[i + 1] - ts[i - 1])

    def f(t):
        if t <= ts[0]:
            return vs[0]
        if t >= ts[-1]:
            return vs[-1]
        i = max(j for j in range(len(ts) - 1) if ts[j] <= t)
        h = ts[i + 1] - ts[i]
        u = (t - ts[i]) / h
        return ((2 * u ** 3 - 3 * u ** 2 + 1) * vs[i] + (u ** 3 - 2 * u ** 2 + u) * h * m[i]
                + (-2 * u ** 3 + 3 * u ** 2) * vs[i + 1] + (u ** 3 - u ** 2) * h * m[i + 1])
    return f


def wing(side, dirs, lift=0.0, yaw=0.0, close=0.0, arm=(0, 0, 0), fore=(0, 0, 0), roll=0.0):
    """One wing. lift raises the blade tips, yaw swings them out and forward, close 1 lays the three blades into
    one, close < 0 fans them apart. Written for the left wing; the right is its mirror."""
    pose = {"wing_arm_l": arm, "wing_fore_l": fore, "wing_hand_l": E(RY(-yaw) @ RX(lift) @ RZ(roll))}
    d1 = dirs[0]
    for k in (1, 2):
        ax = np.cross(dirs[k], d1)
        ang = math.degrees(math.acos(max(-1, min(1, float(np.dot(dirs[k], d1))))))
        pose[f"wing_f{k + 1}_l"] = E(axis_angle(ax, ang * close))
    if side == "r":
        pose = {n[:-2] + "_r": (v[0], -v[1], -v[2]) for n, v in pose.items()}
    return pose


def gait(ph, amp=1.0):
    """Diagonal walk: front-left with hind-right. ph in radians."""
    pose = {}
    for name, off in (("fl", 0.0), ("hl", math.pi)):
        for side, so in (("l", 0.0), ("r", math.pi)):
            a = ph + off + so
            sw, lift = math.sin(a), max(0.0, math.cos(a))
            if name == "fl":
                pose[f"fl_upper_{side}"] = (amp * (-17 * sw - 5 * lift), 0, 0)
                pose[f"fl_lower_{side}"] = (amp * (9 * sw + 20 * lift), 0, 0)
                pose[f"fl_paw_{side}"] = (amp * (8 * sw - 14 * lift), 0, 0)
            else:
                pose[f"hl_thigh_{side}"] = (amp * (-15 * sw - 6 * lift), 0, 0)
                pose[f"hl_shin_{side}"] = (amp * (10 * sw + 18 * lift), 0, 0)
                pose[f"hl_foot_{side}"] = (amp * (5 * sw - 12 * lift), 0, 0)
    return pose


def trail(pose, yaw, t, gain=.6, lag=.09, neck=.0):
    """The tail follows the body's yaw late, bone after bone; the neck (neck > 0) looks ahead of it."""
    for i, name in enumerate(TAILS):
        o = pose.get(name, (0, 0, 0))
        pose[name] = (o[0], o[1] - gain * (yaw(t) - yaw(t - lag * (i + 1))) / (1 + .25 * i) * (1 + .3 * i), o[2])
    if neck:
        for name, k in (("neck1", .3), ("neck2", .3), ("neck3", .2), ("skull", .35)):
            o = pose.get(name, (0, 0, 0))
            pose[name] = (o[0], o[1] + neck * k * (yaw(t + .28) - yaw(t)), o[2])
    return pose


def add(*poses):
    out = {}
    for p in poses:
        for k, v in p.items():
            o = out.get(k, (0, 0, 0))
            out[k] = tuple(o[i] + v[i] for i in range(3))
    return out


def breathe(t, L=4.0, k=1.0):
    w = math.tau * t / L
    pose = {"body@pos": (0, .5 * k * math.sin(w), 0), "body": (.6 * k * math.sin(w), 0, 0),
            "neck1": (1.4 * k * math.sin(w - .4), 2.0 * k * math.sin(w), 0), "neck2": (1.2 * k * math.sin(w - .8), 2.0 * k * math.sin(w - .3), 0),
            "skull": (2.0 * k * math.sin(w - 1.2), 3.0 * k * math.sin(w - .7), 0), "jaw": (-1.5 * k - 1.5 * k * math.sin(2 * w), 0, 0)}
    for i, name in enumerate(TAILS):
        pose[name] = (.9 * k * math.sin(2 * w - .5 * i), (2.4 + .9 * i) * k * math.sin(w - .55 * i), 0)
    return pose


def make(dirs):
    def both(**kw):
        return add(wing("l", dirs, **kw), wing("r", dirs, **kw))

    def idle(t):
        w = math.tau * t / 4.0
        return add(breathe(t), both(lift=1.6 * math.sin(w - .3) + 1, close=-.05 - .05 * math.sin(w - .9), arm=(0, 0, -1 * math.sin(w))))

    def walk(t):
        ph = math.tau * t / 1.2
        pose = add(gait(ph), {"body@pos": (0, .8 * math.sin(2 * ph), 0), "body": (.8 * math.sin(2 * ph + .6), 2.2 * math.sin(ph), 1.8 * math.sin(ph + .5)),
                              "neck1": (0, -1.4 * math.sin(ph), 0), "neck2": (1.2 * math.sin(2 * ph), -1.2 * math.sin(ph), 0),
                              "skull": (-1.5 * math.sin(2 * ph - .4), -1.0 * math.sin(ph), -1.5 * math.sin(ph + .5))},
                   both(lift=2 + 1.6 * math.sin(2 * ph - .7), close=-.04 * math.sin(2 * ph - 1.2)))
        for i, name in enumerate(TAILS):
            pose[name] = (1.0 * math.sin(2 * ph - .5 * i), (3 + 1.1 * i) * math.sin(ph - .6 * i - .6), 0)
        return pose

    turn_yaw = curve([(-1, 0), (0, 0), (.3, -5), (1.0, 52), (1.6, 86), (2.0, 91.5), (2.6, 90), (4, 90)])
    turn_env = curve([(0, 0), (.3, .25), (1.0, 1), (1.6, .7), (2.1, .2), (2.6, 0)])

    def turn(t):
        e = turn_env(t)
        pose = add(breathe(t, k=.4), gait(math.tau * (t - .25) / .8, amp=.85 * e),
                   {"root": (0, turn_yaw(t), 0), "body": (-2.5 * e, 0, -5 * e), "body@pos": (0, -1.2 * e, 0)},
                   wing("l", dirs, lift=11 * e, yaw=5 * e, close=-.22 * e, arm=(0, 0, -5 * e)),
                   wing("r", dirs, lift=8 * turn_env(t - .12), yaw=3 * e, close=-.16 * turn_env(t - .12), arm=(0, 0, -3 * e)))
        return trail(pose, turn_yaw, t, gain=.55, neck=1.0)

    # wing_sweep: the left wing rises, the three blades close into one, and the body's spin carries it round flat
    sw_body = curve([(-1, 0), (0, 0), (.55, -24), (.75, -26), (.98, 58), (1.25, 72), (1.9, 30), (2.6, 0), (4, 0)])
    sw_lift = curve([(0, 0), (.5, 62), (.74, 66), (.94, 6), (1.3, 2), (1.9, 16), (2.6, 0)])
    sw_yaw = curve([(0, 0), (.5, 26), (.74, 34), (.96, 128), (1.25, 150), (1.8, 70), (2.6, 0)])
    sw_close = curve([(0, 0), (.45, .9), (.7, 1), (1.4, 1), (2.0, .3), (2.6, 0)])
    sw_reach = curve([(0, 0), (.5, .4), (.74, .5), (.95, 1), (1.3, 1), (1.9, .4), (2.6, 0)])
    sw_crouch = curve([(0, 0), (.6, 1), (.8, 1), (1.0, -.3), (1.4, .3), (2.6, 0)])

    def wing_sweep(t):
        r, c = sw_reach(t), sw_crouch(t)
        pose = add(breathe(t, k=.3),
                   {"body": (2.5 * c, sw_body(t), -7 * r + 3 * c), "body@pos": (0, -2.2 * c, 0),
                    "neck1": (0, -.3 * sw_body(t), 0), "neck2": (-3 * c, -.25 * sw_body(t), 0), "skull": (4 * c, -.2 * sw_body(t), 0),
                    "jaw": (-12 * max(0, sw_reach(t - .1) - .5) * 2, 0, 0),
                    "fl_upper_l": (0, 0, 6 * c), "fl_upper_r": (0, 0, -6 * c), "hl_thigh_l": (-5 * c, 0, 0), "hl_thigh_r": (-5 * c, 0, 0)},
                   wing("l", dirs, lift=sw_lift(t), yaw=sw_yaw(t), close=sw_close(t), arm=(0, -22 * r, -30 * r), fore=(0, -30 * r, 0)),
                   wing("r", dirs, lift=14 * sw_reach(t - .15), yaw=-6 * r, close=-.2 * r, arm=(0, 0, -4 * r)))
        return trail(pose, sw_body, t, gain=.7, lag=.07)

    # jet_stance: both wings swing back and up and fan open, the body drops, held with a tremble
    jet = curve([(0, 0), (.5, .2), (1.0, 1), (2.2, 1), (2.6, .7), (3.2, 0)])

    def jet_stance(t):
        j = jet(t)
        shake = math.sin(t * 46) * .8 * max(0, j - .7) / .3
        pose = add(breathe(t, k=.3), {"body": (5 * j, 0, 0), "body@pos": (0, -3 * j, 0), "neck1": (-6 * j, 0, 0), "neck2": (-5 * j, 0, 0),
                                      "skull": (14 * j, 0, 0), "jaw": (-16 * max(0, jet(t - .1) - .4), 0, 0),
                                      "fl_upper_l": (-8 * j, 0, 0), "fl_upper_r": (-8 * j, 0, 0), "fl_lower_l": (10 * j, 0, 0), "fl_lower_r": (10 * j, 0, 0)},
                   both(lift=48 * j + shake, yaw=16 * jet(t - .08), close=-.7 * jet(t - .16), arm=(0, 0, -12 * j)))
        for i, name in enumerate(TAILS):
            o = pose.get(name, (0, 0, 0))
            pose[name] = (o[0] - (3 + i) * jet(t - .06 * i), o[1], o[2])
        return pose

    return (("idle", 4.0, idle, "loop"), ("walk", 1.2, walk, "loop"), ("turn", 2.6, turn, "once"),
            ("wing_sweep", 2.6, wing_sweep, "once"), ("jet_stance", 3.2, jet_stance, "once"))


def bake(b, name, length, fn, loop):
    n = round(length / STEP)
    tracks = {}
    for i in range(n + 1):
        t = i * STEP
        for bone, v in fn(t).items():
            if bone.endswith("@pos"):
                tracks.setdefault(bone[:-4], []).append((t, tuple(round(c, 3) for c in v), "position"))
            else:
                tracks.setdefault(bone, []).append((t, (round(-v[0], 3), round(v[1], 3), round(v[2], 3))))   # stored with x negated
    b.m.anim(name, length, tracks, loop)


def write(b):
    import build_valstrax_study as S
    dirs = [S.unit(d) for _, _, d, *_ in S.FINGERS]
    for name, length, fn, loop in make(dirs):
        bake(b, name, length, fn, loop)
