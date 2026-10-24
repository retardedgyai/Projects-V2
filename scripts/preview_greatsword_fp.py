"""First-person preview of the greatsword swing frames (scripts/class_armament_swing.py), as the client draws a held
item: view space, the hand projection's fixed 70 degree vertical field of view, no arm, no view bobbing.

    python3 scripts/preview_greatsword_fp.py --sheet out.png       # every tick of every clip
    python3 scripts/preview_greatsword_fp.py --gif out.gif         # the three beats in a row at 20 fps
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import class_armament_swing as S
import preview_warrior_cast as R

R.W, R.H = 640, 360
R.FOCAL = (R.H / 2) / math.tan(math.radians(35))
MODEL = R.model_json('item/weapons/greatsword_t3')
CAMERA = R.Camera((0, 0, 0), (0, 0, -1))


def faces(display):
    t = np.array(display['translation']) / 16
    m = np.eye(4); m[:3, :3] = S.rotation_xyz(display['rotation'])
    return R.model_faces(MODEL, (0xffffff,), R.T(*S.ARM) @ R.T(*t) @ m @ R.S(*display['scale']) @ R.T(-.5, -.5, -.5)
                         @ R.S(1 / 16, 1 / 16, 1 / 16))


def scene_faces():
    # the ground 1.62 below the eye and a dummy 3 blocks ahead, so height and reach read
    out = []
    lo, hi = (-.45, -1.62, -3.5), (.45, .4, -2.6)
    c = [np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)]) for i in range(8)]
    out += [([c[i] for i in ids], ('shade', (92, 70, 66))) for _, ids in R.FACES]
    return out


def render(display, label=''):
    image = Image.new('RGBA', (R.W, R.H), R.BG + (255,))
    d = ImageDraw.Draw(image)
    for z in np.arange(1.0, 12, 1.0):  # ground lines
        y = R.H / 2 + 1.62 / z * R.FOCAL
        d.line([(0, y), (R.W, y)], fill=(40, 46, 56))
    CAMERA.cam_dir_light = np.array((.3, .8, .5)) / np.linalg.norm((.3, .8, .5))
    R.draw_faces(image, CAMERA, scene_faces() + faces(display))
    d.line([(R.W / 2 - 5, R.H / 2), (R.W / 2 + 5, R.H / 2)], fill=(230, 230, 230)); d.line([(R.W / 2, R.H / 2 - 5), (R.W / 2, R.H / 2 + 5)], fill=(230, 230, 230))
    if label: d.text((8, 6), label, fill=(255, 220, 80), font=R.FONT)
    return image.convert('RGB')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--sheet'); ap.add_argument('--gif'); ap.add_argument('--clips', default='swing1,swing2,swing3,return1,return2')
    a = ap.parse_args()
    clips = S.clips()
    if a.sheet:
        cells = [render(d, f'{name} {i}') for name in a.clips.split(',') for i, d in enumerate(clips[name])]
        cols = 6; w, h = R.W // 2, R.H // 2
        sheet = Image.new('RGB', (w * cols, h * ((len(cells) + cols - 1) // cols)))
        for j, c in enumerate(cells): sheet.paste(c.resize((w, h)), ((j % cols) * w, (j // cols) * h))
        sheet.save(a.sheet)
    if a.gif:
        idle = {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, -1.5], 'scale': [S.SCALE] * 3}
        seq = [('idle', idle)] * 10 + [(f'1撃目 {i}', d) for i, d in enumerate(clips['swing1'])] + \
              [(f'2撃目 {i}', d) for i, d in enumerate(clips['swing2'])] + [(f'3撃目 {i}', d) for i, d in enumerate(clips['swing3'])] + [('idle', idle)] * 12
        frames = [render(d, l) for l, d in seq]
        frames[0].save(a.gif, save_all=True, append_images=frames[1:], duration=50, loop=0)


if __name__ == '__main__':
    main()


def viewmodel_faces(f):
    """A viewmodel frame [tx, ty, tz, qx, qy, qz, qw, scale] as the item display draws it (with its half turn)."""
    x, y, z, w = f[3:7]
    r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    m = np.eye(4); m[:3, :3] = r @ np.diag([-1.0, 1.0, -1.0])
    return R.model_faces(MODEL, (0xffffff,), R.T(*f[:3]) @ m @ R.S(f[7], f[7], f[7]) @ R.T(-.5, -.5, -.5)
                         @ R.S(1 / 16, 1 / 16, 1 / 16))


def render_vm(f, label=''):
    image = Image.new('RGBA', (R.W, R.H), R.BG + (255,))
    d = ImageDraw.Draw(image)
    for z in np.arange(1.0, 12, 1.0):
        y = R.H / 2 + 1.62 / z * R.FOCAL
        d.line([(0, y), (R.W, y)], fill=(40, 46, 56))
    CAMERA.cam_dir_light = np.array((.3, .8, .5)) / np.linalg.norm((.3, .8, .5))
    R.draw_faces(image, CAMERA, scene_faces() + viewmodel_faces(f))
    if label: d.text((8, 6), label, fill=(255, 220, 80), font=R.FONT)
    return image.convert('RGB')


def bc_sheet(out, ticks=20):
    """The three Better Combat swings as the server plays them at [ticks] a swing: one cell per tick."""
    clips = S.bc_clips('greatsword_t3')
    cells = []
    for beat in (1, 2, 3):
        c = clips[f'swing{beat}']
        for t in range(ticks):
            cells.append(render_vm(c[int(t * (len(c) - 1) / (ticks - 1))], f'{beat}撃目 {t}'))
    cols = 10; w, h = R.W // 2, R.H // 2
    sheet = Image.new('RGB', (w * cols, h * ((len(cells) + cols - 1) // cols)))
    for j, c in enumerate(cells): sheet.paste(c.resize((w, h)), ((j % cols) * w, (j // cols) * h))
    sheet.save(out)


def _interp(a, b, w):
    """What the client draws between two ticks: translation and scale lerp, rotation slerp."""
    qa, qb = np.array(a[3:7]), np.array(b[3:7])
    if qa @ qb < 0: qb = -qb
    ang = math.acos(min(1.0, abs(float(qa @ qb))))
    q = qa if ang < 1e-5 else (math.sin((1 - w) * ang) * qa + math.sin(w * ang) * qb) / math.sin(ang)
    return [a[i] + (b[i] - a[i]) * w for i in range(3)] + list(q / np.linalg.norm(q)) + [a[7] + (b[7] - a[7]) * w]


def bc_gif(out, ticks=(20, 22, 30), fps=30, scale=.5):
    """The three swings back to back, as the client interpolates the server's per-tick frames."""
    clips = S.bc_clips('greatsword_t3')
    frames = []
    for beat, n in zip((1, 2, 3), ticks):
        c = clips[f'swing{beat}']
        per_tick = [c[int(t * (len(c) - 1) / (n - 1))] for t in range(n)]
        steps = int(n / 20 * fps)
        for k in range(steps):
            t = k * 20 / fps
            i = min(int(t), n - 2)
            img = render_vm(_interp(per_tick[i], per_tick[i + 1], t - i), f'{beat}撃目')
            frames.append(img.resize((int(R.W * scale), int(R.H * scale))))
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=round(1000 / fps), loop=0)
    return frames


def _qmat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def shader_pose(keys, u):
    """What scripts/shaders/item.vsh computes at swing progress u (same key search, easing and slerp)."""
    k = 0
    for i in range(len(keys) - 1):
        if keys[i + 1][0] <= u: k = i + 1
    n = min(k + 1, len(keys) - 1)
    u0, u1 = keys[k][0], keys[n][0]
    w = 1.0 if n == k or u1 <= u0 else min(1.0, max(0.0, (u - u0) / (u1 - u0)))
    e = int(keys[n][1])
    w = w * w if e == 1 else 1 - (1 - w) ** 2 if e == 2 else w * w * (3 - 2 * w) if e == 3 else w
    a, b = np.array(keys[k], float), np.array(keys[n], float)
    q0, q1 = a[5:9], b[5:9]
    if q0 @ q1 < 0: q1 = -q1
    c = min(1.0, float(q0 @ q1))
    q = q0 + (q1 - q0) * w if c > .9995 else (math.sin((1 - w) * math.acos(c)) * q0 + math.sin(w * math.acos(c)) * q1) / math.sin(math.acos(c))
    return a[2:5] + (b[2:5] - a[2:5]) * w, q / np.linalg.norm(q), a[9] + (b[9] - a[9]) * w


def render_shader(keys, u, name='greatsword_t3', label=''):
    h, q, s = shader_pose(keys, u)
    grip = np.array(S.MODELS[name][1]) / 16
    m = np.eye(4); m[:3, :3] = _qmat(q)
    faces = R.model_faces(MODEL, (0xffffff,), R.T(*h) @ m @ R.S(s, s, s) @ R.T(*(-grip)) @ R.S(1 / 16, 1 / 16, 1 / 16))
    image = Image.new('RGBA', (R.W, R.H), R.BG + (255,))
    d = ImageDraw.Draw(image)
    for z in np.arange(1.0, 12, 1.0):
        y = R.H / 2 + 1.62 / z * R.FOCAL
        d.line([(0, y), (R.W, y)], fill=(40, 46, 56))
    CAMERA.cam_dir_light = np.array((.3, .8, .5)) / np.linalg.norm((.3, .8, .5))
    R.draw_faces(image, CAMERA, scene_faces() + faces)
    if label: d.text((8, 6), label, fill=(255, 220, 80), font=R.FONT)
    return image.convert('RGB')


def shader_sheet(out, ticks=20, fps=20, until=.5):
    cells = []
    for beat in (1, 2, 3):
        keys = S.shader_keys('greatsword_t3', beat)
        n = int(ticks / 20 * fps * until) + 1
        for i in range(n):
            u = i / (ticks / 20 * fps)
            cells.append(render_shader(keys, u, label=f'{beat} u{u:.2f}'))
    cols = 10; w, h = R.W // 2, R.H // 2
    sheet = Image.new('RGB', (w * cols, h * ((len(cells) + cols - 1) // cols)))
    for j, c in enumerate(cells): sheet.paste(c.resize((w, h)), ((j % cols) * w, (j // cols) * h))
    sheet.save(out)
