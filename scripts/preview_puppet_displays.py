"""Draws the puppet exactly as the server sends it: the generated pack part models (one tinted face per skin pixel)
and the weapon item models, placed by the exported item display transforms (context NONE: the model is centred,
then scale, left rotation and translation). Run PlayerPuppetTest's export first. Not the game client.

Usage: python3 scripts/preview_puppet_displays.py [motion]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import preview_render as R
from preview_render import T, S, Rq

ROOT = Path(__file__).resolve().parents[1]


def faces_for(d):
    key = d['model'].split(':', 1)[1]
    if key.startswith('combat_vfx/puppet/'):
        model, tints = R.model_json(key), tuple(d['colors'])
    else:
        model, tints = R.item_model(key)
    m = T(*(np.array(d['pos']) + np.array(d['translation']))) @ Rq(d['rotation']) @ S(d['scale'], d['scale'], d['scale']) @ T(-.5, -.5, -.5) @ S(1 / 16, 1 / 16, 1 / 16)
    out = []
    for corners, content in R.model_faces(model, tints, m):
        if isinstance(content, Image.Image) and key.startswith('combat_vfx/puppet/'):
            c = content.getpixel((0, 0)); content = ('shade', c[:3])
        out.append((corners, content))
    return out


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else 'greatsword_charge'
    scene = next(s for s in json.loads((ROOT / '.tools/puppet-displays.json').read_text()) if s['name'] == name)
    views = {'横から': ((-3.6, 1.5, 1.6), (0, .9, .9)), '背後（F5）': ((0.0, 3.0, -3.76), (0, .25, 4.0))}
    frames = []
    for i, displays in enumerate(scene['frames']):
        sheet = Image.new('RGB', (R.W * 2, R.H))
        for k, (label, (eye, target)) in enumerate(views.items()):
            cam = R.Camera(eye, target)
            img = Image.new('RGBA', (R.W, R.H), R.BG + (255,)); R.grid(img, cam)
            faces = [f for d in displays for f in faces_for(d)]
            R.draw_faces(img, cam, faces)
            ImageDraw.Draw(img).text((10, 6), f'{label}（サーバーが送る値をパックのモデルで描画）', font=R.SMALL, fill=(236, 226, 206))
            ImageDraw.Draw(img).text((10, 24), f'{i / 20:.2f}秒', font=R.SMALL, fill=(190, 182, 170))
            sheet.paste(img.convert('RGB'), (k * R.W, 0))
        frames.append(sheet)
    out = ROOT / f'.tools/motion/{name}-server.gif'
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=50, loop=0)
    print(out, len(frames))


if __name__ == '__main__':
    main()
