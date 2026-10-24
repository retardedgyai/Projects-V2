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
