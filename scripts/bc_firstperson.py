"""Better Combat's first-person swing, worked out exactly (2026-10-25, Creator: 「Better combat のソースコードかなんか見てどうやってるか解剖して」).

Better Combat (3.x, with the player animation library) draws first person by rendering the player's own third-person
model from the eye (FirstPersonMode.THIRD_PERSON_MODEL: the camera is marked detached for the pass, the head and body
hidden). So the sword on screen is the full-body animation seen from the eye. This evaluates that chain on the
vanilla player model, the way the library and the game do it (read from their sources, ~/ProjectS/refs/bettercombat):

- animation JSON (emotecraft format, `degrees: false` = radians): per part x/y/z (absolute model position, px) and
  pitch/yaw/roll, each axis keyed on its own; between two keys the value is lerped by the earlier key's easing
  (KeyframeAnimationPlayer.getValueFromKeyframes); before the first key it eases from the pose the player was in.
- ModelPart.translateAndRotate: translate(x, y, z / 16), then rotation Z * Y * X (rotationZYX(roll, yaw, pitch)).
- ItemInHandLayer: arm transform, rotate X -90, rotate Y 180, translate(1/16, 0.125, -0.625); then the library's
  item offset (rightItem: translate(pos / 16), rotate Z roll, Y yaw, X pitch); then the item's thirdperson_righthand
  display (translation / 16, rotation X Y Z, scale), centred by -0.5.
- LivingEntityRenderer: rotate Y (180 - body yaw), the library's whole-body turn ('torso' keys), scale (-1, -1, 1),
  scale 0.9375 (player), translate (0, -1.501, 0).
- the camera: 1.62 above the feet, looking along the body (+z at yaw 0), its view space x right, y up, -z ahead.

Only data (the animation files' numbers) is read, as a reference; none of it ships.

    python3 scripts/bc_firstperson.py   # prints the hand and tip path of each beat
"""
import json
import math
from pathlib import Path

import numpy as np

ANIMS = Path.home() / 'ProjectS/refs/bettercombat/assets/bettercombat/player_animations'


def ease(name, t):
    t = min(1.0, max(0.0, t))
    n = (name or 'LINEAR').upper()
    if n.endswith('INOUTQUAD'): return 2 * t * t if t < .5 else 1 - (-2 * t + 2) ** 2 / 2
    if n.endswith('INQUAD'): return t * t
    if n.endswith('OUTQUAD'): return 1 - (1 - t) * (1 - t)
    if n.endswith('INOUTCUBIC'): return 4 * t ** 3 if t < .5 else 1 - (-2 * t + 2) ** 3 / 2
    if n.endswith('INCUBIC'): return t ** 3
    if n.endswith('OUTCUBIC'): return 1 - (1 - t) ** 3
    if n.endswith('INOUTSINE'): return -(math.cos(math.pi * t) - 1) / 2
    if n.endswith('INSINE'): return 1 - math.cos(t * math.pi / 2)
    if n.endswith('OUTSINE'): return math.sin(t * math.pi / 2)
    return t


class Animation:
    def __init__(self, name):
        data = json.loads((ANIMS / f'{name}.json').read_text())['emote']
        self.begin, self.end, self.stop = data.get('beginTick', 0), data.get('endTick', 20), data.get('stopTick', 30)
        self.tracks = {}
        for m in data['moves']:
            for part, axes in m.items():
                if not isinstance(axes, dict): continue
                for axis, v in axes.items():
                    self.tracks.setdefault((part, axis), []).append((m['tick'], float(v), m.get('easing', 'LINEAR')))
        for k in self.tracks: self.tracks[k].sort(key=lambda e: e[0])

    def value(self, part, axis, tick, base):
        keys = self.tracks.get((part, axis))
        if not keys: return base
        if tick <= keys[0][0]:
            return base + (keys[0][1] - base) * ease('LINEAR', tick / max(keys[0][0], 1e-9))
        for (t0, v0, e0), (t1, v1, _) in zip(keys, keys[1:]):
            if tick <= t1: return v0 + (v1 - v0) * ease(e0, (tick - t0) / max(t1 - t0, 1e-9))
        if tick <= self.end: return keys[-1][1]
        # past endTick the part eases back to the pose under it, reaching it at stopTick
        return keys[-1][1] + (base - keys[-1][1]) * min(1.0, (tick - self.end) / max(self.stop - self.end, 1e-9))


def T(x, y, z):
    m = np.eye(4); m[:3, 3] = (x, y, z); return m


def S(x, y, z): return np.diag((x, y, z, 1.0))


def Rx(a): c, s = math.cos(a), math.sin(a); return np.array(((1, 0, 0, 0), (0, c, -s, 0), (0, s, c, 0), (0, 0, 0, 1)))


def Ry(a): c, s = math.cos(a), math.sin(a); return np.array(((c, 0, s, 0), (0, 1, 0, 0), (-s, 0, c, 0), (0, 0, 0, 1)))


def Rz(a): c, s = math.cos(a), math.sin(a); return np.array(((c, -s, 0, 0), (s, c, 0, 0), (0, 0, 1, 0), (0, 0, 0, 1)))


POSE = 'pose_two_handed_sword'
DEFAULTS = {'rightArm': (-5.0, 2.0, 0.0), 'leftArm': (5.0, 2.0, 0.0)}


def arm_matrix(anim, tick, side, pose=None):
    """World matrix (feet at the origin, facing +z) of an arm's own pixel space ('rightArm' or 'leftArm')."""
    def part(name, axis, base):
        b = pose.value(name, axis, 1.0, base) if pose else base
        return anim.value(name, axis, tick, b) if anim else b
    # files older than version 3 name the whole body 'torso': the library turns the entire model by it, about a point
    # 0.7 above the feet, right after the body's yaw (PlayerRendererMixin.applyBodyTransforms)
    body = [part('torso', a, 0.0) for a in ('x', 'y', 'z', 'pitch', 'yaw', 'roll')]
    turn = T(body[0], body[1] + .7, body[2]) @ Rz(body[5]) @ Ry(body[4]) @ Rx(body[3]) @ T(0, -.7, 0)
    root = Ry(math.pi) @ turn @ S(-1, -1, 1) @ S(.9375, .9375, .9375) @ T(0, -1.501, 0)
    dx, dy, dz = DEFAULTS[side]
    arm = [part(side, a, d) for a, d in (('x', dx), ('y', dy), ('z', dz), ('pitch', 0.0), ('yaw', 0.0), ('roll', 0.0))]
    return root @ T(arm[0] / 16, arm[1] / 16, arm[2] / 16) @ Rz(arm[5]) @ Ry(arm[4]) @ Rx(arm[3])


# The animations were made for a handheld sprite: blade on the diagonal, the hands round pixel (4.5, 4.5), drawn with
# the vanilla handheld third-person display. A 3D sword (blade up its own +y) is laid on that diagonal, its grip on
# the sprite's, so it sits in the hands the way the animations expect.
HANDHELD = {'rotation': [0, -90, 55], 'translation': [0, 4, .5], 'scale': [.85] * 3}
SPRITE_GRIP = (4.5, 4.5, 8.0)


def on_diagonal(grip_px):
    return T(*SPRITE_GRIP) @ Rz(-math.pi / 4) @ T(*(-np.asarray(grip_px, float)))


def item_matrix(anim, tick, display, pose=None):
    """World matrix (feet at the origin, facing +z) of the item model's pixel space, at animation [tick]."""
    def part(name, axis, base):
        b = pose.value(name, axis, 1.0, base) if pose else base
        return anim.value(name, axis, tick, b) if anim else b
    item = [part('rightItem', a, 0.0) for a in ('x', 'y', 'z', 'pitch', 'yaw', 'roll')]
    hand = Rx(-math.pi / 2) @ Ry(math.pi) @ T(1 / 16, .125, -.625)
    lib = T(item[0] / 16, item[1] / 16, item[2] / 16) @ Rz(item[5]) @ Ry(item[4]) @ Rx(item[3])
    t = [v / 16 for v in display.get('translation', (0, 0, 0))]
    r = [math.radians(v) for v in display.get('rotation', (0, 0, 0))]
    s = display.get('scale', (1, 1, 1))
    disp = T(*t) @ Rx(r[0]) @ Ry(r[1]) @ Rz(r[2]) @ S(*s) @ T(-.5, -.5, -.5) @ S(1 / 16, 1 / 16, 1 / 16)
    return arm_matrix(anim, tick, 'rightArm', pose) @ hand @ lib @ disp


EYE = 1.62
TO_VIEW = np.diag((-1.0, 1.0, -1.0, 1.0)) @ T(0, -EYE, 0)   # camera facing +z: view x = -world x, view -z = ahead


def view_pose(anim, tick, display, grip_px, pose=None):
    """(hand position in view space, 3x3 rotation of the model's axes, uniform scale) at [tick]."""
    m = TO_VIEW @ item_matrix(anim, tick, display, pose)
    lin = m[:3, :3] * 16
    scale = float(np.cbrt(np.linalg.det(lin)))
    rot = lin / scale
    hand = (m @ np.append(np.asarray(grip_px, float), 1.0))[:3]
    return hand, rot, scale


BEATS = {1: 'two_handed_slash_horizontal_right', 2: 'two_handed_slash_horizontal_left', 3: 'two_handed_slam'}
UPSWING = .25   # the claymore's upswing .5 x upswing_multiplier .5: the hit lands a quarter of the way in


def anim_tick(u, end=20.0, upswing=UPSWING, multiplier=.5):
    """Where Better Combat's animation is at [u] of the attack (AbstractClientPlayerEntityMixin.playAttackAnimation):
    played at endTick / length, sped up by multiplier / upswing until the hit, then slowed to (1 - upswing)."""
    up = end * multiplier            # (end / length) * (multiplier / upswing) * (length * upswing)
    if u <= upswing: return up * u / upswing
    return up + end * (1 - upswing) * (u - upswing)


PITCH = math.radians(12)   # the camera looks a little down, as in the reference footage (horizon above the middle)


def sword_pose(anim, tick, grip_px, pose):
    """(hand, rotation, scale) of a 3D sword with its hands at [grip_px], in the view of a camera pitched PITCH down."""
    m = Rx(PITCH) @ TO_VIEW @ item_matrix(anim, tick, HANDHELD, pose) @ on_diagonal(grip_px)
    lin = m[:3, :3] * 16
    scale = float(np.cbrt(np.linalg.det(lin)))
    return (m @ np.append(np.asarray(grip_px, float), 1.0))[:3], lin / scale, scale


def arm_pose(anim, tick, side, pose):
    """(shoulder, rotation, scale) of an arm in the view: its pixel c lands at shoulder + rotation @ c * scale / 16."""
    m = Rx(PITCH) @ TO_VIEW @ arm_matrix(anim, tick, side, pose)
    lin = m[:3, :3]   # the arm's space is in pixels already scaled by 1/16 in its translations: x 16 / 16
    scale = float(np.cbrt(np.linalg.det(lin)))
    return m[:3, 3], lin / scale, scale


def u_of(tick, end=20.0, upswing=UPSWING, multiplier=.5):
    up = end * multiplier
    return tick / up * upswing if tick <= up else upswing + (tick - up) / (end * (1 - upswing))


# The fast part of each cut (the blade coming round in front) is drawn to end on the hit, so the hit and its slash
# land as the blade arrives: the cause first. The first cut comes round a tick after Better Combat's hit, so it is
# drawn a tick early; the slam lands most of a tick before its hit, so it is drawn that much late.
LEAD_TICKS = {1: 1.0, 2: 0.0, 3: -0.8}
LENGTHS = {1: 20, 2: 22, 3: 30}   # the beats' swing lengths at attack speed 1 (CoreWeaponAttacks)


def shader_u(beat, tick):
    return max(0.0, u_of(tick) - LEAD_TICKS[beat] / LENGTHS[beat])


SAMPLES = [t / 2 for t in range(2, 41)]   # animation ticks the shader keys are taken at (it slerps between them)
BACK = .82                                # from here the sword goes back to the held pose, there at the swing's end


def swing(beat, grip_px):
    """[(u, hand, rotation, scale)] of one beat, from Better Combat's animation, timed as Better Combat plays it."""
    anim, pose = Animation(BEATS[beat]), Animation(POSE)
    return [(round(shader_u(beat, t), 4), *sword_pose(anim, t, grip_px, pose)) for t in SAMPLES if 0 < shader_u(beat, t) < BACK]


def arm_swing(beat, side):
    """[(u, shoulder, rotation, scale)] of one arm through one beat, sampled as swing() is."""
    anim, pose = Animation(BEATS[beat]), Animation(POSE)
    return [(round(shader_u(beat, t), 4), *arm_pose(anim, t, side, pose)) for t in SAMPLES if 0 < shader_u(beat, t) < BACK]


if __name__ == '__main__':
    for beat, name in BEATS.items():
        print(name)
        for u, h, r, s in swing(beat, (8, 4.5, 8)):
            tip = h + r @ np.array((0, 25.4 / 16, 0)) * s
            print(f'  u {u:.3f} hand {np.round(h, 2)} tip {np.round(tip, 2)}')
