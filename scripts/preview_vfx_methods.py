"""Drawing-method comparison for skill visuals (2026-10-20, Creator: 「何個か見せてHTML方式で」).

Takes the recorded 地割り (slam_shader: board + hand-drawn art + the shader glow, the current base) from
preview_warrior_cast and adds one more method on top, so each GIF differs only by that method:

  flip   D  a camera-facing flipbook sprite at the impact (a text display with font frames in game)
  trail  G  the blade's path left as a fading ribbon
  impact H  screen effects on the hit: a two-frame black-and-white impact frame, then a red edge and a shake
  ground I  a telegraph on the ground during the wind-up, then the crack left on the ground
  light  J  the impact lights up its surroundings for a moment
  all       everything above together

Still the CPU projection of preview_warrior_cast, not the game client.

Usage: python3 scripts/preview_vfx_methods.py [--out .tools/vpage]
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import preview_warrior_cast as pc

ROOT = Path(__file__).resolve().parents[1]
FIRE = [(255, 250, 226), (255, 214, 120), (255, 150, 48), (230, 74, 30), (150, 30, 24), (70, 24, 26)]


def _h(x, y, s=0):
    v = math.sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453
    return v - math.floor(v)


# ---------------------------------------------------------------- textures
def flip_frames(n=8, size=32):
    """A burst drawn in steps: a white-hot flash, a ragged ring that widens and cools, then broken embers."""
    frames = []
    c = (size - 1) / 2
    for k in range(n):
        t = k / (n - 1)
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        outer = 4 + t * 12
        inner = max(0.0, outer - (6 - 4 * t))
        for y in range(size):
            for x in range(size):
                a = math.atan2(y - c, x - c)
                d = math.hypot(x - c, y - c)
                rag = 1.6 * math.sin(a * 7 + k) + 1.2 * _h(round(a * 6), k)
                if d > outer + rag or d < inner + rag * .5:
                    continue
                if t > .55 and _h(x // 2, y // 2, k) < (t - .55) * 1.9:
                    continue  # the ring breaks up into embers
                edge = (d - inner) / max(outer - inner, 1)
                heat = (1 - t) * 4 + edge * 2.2
                idx = min(len(FIRE) - 1, max(0, int(len(FIRE) - 1 - heat)))
                img.putpixel((x, y), FIRE[idx] + (255,))
        frames.append(img)
    return frames


def telegraph(w=12, h=36, alpha=110):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            border = x in (0, w - 1) or y in (0, h - 1)
            stripe = (x + y) % 6 < 2
            if border: img.putpixel((x, y), (255, 120, 60, 230))
            elif stripe: img.putpixel((x, y), (255, 96, 40, alpha))
    return img


def crack(w=12, h=36, glow=1.0):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    x = w // 2
    for y in range(h):
        x = min(w - 3, max(2, x + (-1 if _h(y, 3) < .33 else 1 if _h(y, 3) > .66 else 0)))
        core = FIRE[1] if glow > .5 else FIRE[3]
        img.putpixel((x, y), core + (int(255 * min(1, glow + .3)),))
        for dx in (-1, 1):
            img.putpixel((x + dx, y), FIRE[4] + (int(200 * min(1, glow + .2)),))
        if _h(y, 9) > .82:  # side branches
            for j in range(1, 3):
                xx = x + j * (1 if _h(y, 4) > .5 else -1)
                if 0 <= xx < w: img.putpixel((xx, min(h - 1, y + j)), FIRE[4] + (int(180 * glow),))
    return img


# ---------------------------------------------------------------- scene helpers
def forward(yaw_deg):
    a = math.radians(yaw_deg)
    return np.array((-math.sin(a), 0.0, math.cos(a)))


def ground_quad(center, fwd, width, length, y=.02):
    right = np.cross(fwd, (0, 1, 0)); right /= np.linalg.norm(right)
    near, far = center, center + fwd * length
    return [far - right * width / 2 + (0, y, 0), far + right * width / 2 + (0, y, 0),
            near + right * width / 2 + (0, y, 0), near - right * width / 2 + (0, y, 0)]


def billboard(camera, center, r):
    return [center - camera.r * r + camera.u * r, center + camera.r * r + camera.u * r,
            center + camera.r * r - camera.u * r, center - camera.r * r - camera.u * r]


def blade_points(hand, pose):
    corners = np.array([c for corners, _ in pc.weapon_faces(hand, pose) for c in corners])
    grip = (hand @ np.array((0, 0, 0, 1.0)))[:3]
    d = np.linalg.norm(corners - grip, axis=1)
    tip = corners[int(np.argmax(d))]
    return grip + (tip - grip) * .2, tip


# ---------------------------------------------------------------- render one frame with extras
def render(scene, tick, view, method, history):
    frame = scene['frames'][tick]
    pc.SHADER_TIME[0] = tick / 20
    origin = np.array(scene['frames'][0]['player'][:3])
    label, eye, target = pc.VIEWS[view]
    camera = pc.Camera(eye, target)
    camera.cam_dir_light = camera.cam(camera.eye + np.array((.3, 1.0, -.5)) / np.linalg.norm((.3, 1.0, -.5)))
    impact = scene['castTick'] + scene['startup']
    since = tick - impact
    shake = np.zeros(3)
    if method in ('impact', 'all') and 0 <= since < 5:
        shake = camera.r * (.06 * (1 - since / 5) * (1 if since % 2 else -1)) + camera.u * (.03 * (since % 2))
        camera = pc.Camera(np.array(eye) + shake, np.array(target) + shake)
        camera.cam_dir_light = camera.cam(camera.eye + np.array((.3, 1.0, -.5)) / np.linalg.norm((.3, 1.0, -.5)))
    image = Image.new('RGBA', (pc.W, pc.H), pc.BG + (255,))
    pc.grid(image, camera)
    px, py, pz, yaw = frame['player']
    pos = np.array((px, py, pz)) - origin
    cast = scene['castTick']
    swing = (tick - cast) / 6 if 0 <= tick - cast < 6 else None
    faces, hand = pc.player_faces(pos, yaw, swing)
    pose = pc.weapon_pose(scene, tick)
    faces += pc.weapon_faces(hand, pose)
    for t in frame['targets']: faces += pc.target_faces(np.array(t) - origin)
    for d in frame['displays']:
        faces += pc.display_faces({**d, 'offset': list(np.array(d['offset']) - origin)})
    fwd = forward(yaw)
    hit = pos + fwd * 2.4
    if method in ('ground', 'all'):
        if cast <= tick < impact:
            faces.append((ground_quad(pos + fwd * .6, fwd, 2.0, 4.2), telegraph()))
        elif 0 <= since < 24:
            faces.append((ground_quad(pos + fwd * .6, fwd, 2.0, 4.2, y=.03), crack(glow=1 - since / 24)))
    if method in ('flip', 'all') and 0 <= since < 8:
        faces.append((billboard(camera, hit + (0, 1.3, 0), 2.2 + since * .12), FLIP[since]))
    pc.draw_faces(image, camera, faces)

    mid, tip = blade_points(hand, pose)
    history.append((mid, tip))
    if method in ('trail', 'all') and -4 <= since < 4:
        draw_trail(image, camera, history[-(since + 5):])
    if method in ('light', 'all') and 0 <= since < 8:
        light(image, camera, hit + (0, .4, 0), 1 - since / 8)
    if method in ('impact', 'all'):
        if 0 <= since < 2:
            g = np.array(image.convert('L'))
            bw = np.where(g > 60, 0, 255).astype(np.uint8) if since == 0 else np.where(g > 60, 255, 0).astype(np.uint8)
            image = Image.fromarray(bw).convert('RGBA')
        elif 2 <= since < 9:
            vignette(image, 1 - (since - 2) / 7)
    draw = ImageDraw.Draw(image)
    draw.text((10, 6), f"地割り　{label}", font=pc.FONT, fill=(236, 226, 206))
    phase = '待機' if tick < cast else '構え' if tick < impact else '発動'
    draw.text((10, 28), f"{(tick - cast) / 20:+.2f}秒　{phase}", font=pc.SMALL, fill=(170, 164, 154))
    return image.convert('RGB')


def draw_trail(image, camera, history, keep=6):
    layer = Image.new('RGBA', image.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    pts = history[-keep:]
    for i in range(len(pts) - 1):
        (m0, t0), (m1, t1) = pts[i], pts[i + 1]
        if np.linalg.norm(t1 - t0) < .08: continue  # the blade is still: no trail
        quad = [camera.cam(p) for p in (m0, t0, t1, m1)]
        if min(q[2] for q in quad) < .2: continue
        k = (i + 1) / (len(pts) - 1)
        col = FIRE[1] if k > .7 else FIRE[2] if k > .4 else FIRE[3]
        d.polygon([camera.screen(q) for q in quad], fill=col + (int(220 * k),))
    image.alpha_composite(layer)


def light(image, camera, p, strength):
    c = camera.cam(p)
    if c[2] < .2: return
    sx, sy = camera.screen(c)
    yy, xx = np.mgrid[0:pc.H, 0:pc.W]
    r = 150 / max(c[2], 1)
    fall = np.clip(1 - np.hypot(xx - sx, (yy - sy) * 1.4) / (r * 2.2), 0, 1) ** 2 * strength
    arr = np.array(image).astype(np.float32)
    warm = np.array((255, 150, 70), np.float32)
    arr[..., :3] = np.minimum(255, arr[..., :3] * (1 + 1.6 * fall[..., None]) + warm * .45 * fall[..., None])
    image.paste(Image.fromarray(arr.astype(np.uint8)))


def vignette(image, strength):
    yy, xx = np.mgrid[0:pc.H, 0:pc.W]
    edge = np.clip((np.hypot((xx - pc.W / 2) / (pc.W / 2), (yy - pc.H / 2) / (pc.H / 2)) - .55) / .6, 0, 1) * strength
    arr = np.array(image).astype(np.float32)
    red = np.array((150, 10, 16), np.float32)
    arr[..., :3] = arr[..., :3] * (1 - edge[..., None] * .8) + red * edge[..., None] * .8
    image.paste(Image.fromarray(arr.astype(np.uint8)))


FLIP = flip_frames()
METHODS = ('base', 'flip', 'trail', 'impact', 'ground', 'light', 'all')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', default='.tools/vpage')
    parser.add_argument('--frames', default='.tools/warrior-cast-frames.json')
    args = parser.parse_args()
    scene = next(s for s in json.loads((ROOT / args.frames).read_text()) if s['id'] == 'slam_shader')
    out = ROOT / args.out; out.mkdir(parents=True, exist_ok=True)
    last = max(i for i, f in enumerate(scene['frames']) if f['displays'] or i <= scene['castTick'] + scene['startup'])
    ticks = range(0, min(len(scene['frames']), last + 8))
    for method in METHODS:
        frames, hb, hs = [], [], []
        for tick in ticks:
            sheet = Image.new('RGB', (pc.W * 2, pc.H))
            sheet.paste(render(scene, tick, 'behind', method, hb), (0, 0))
            sheet.paste(render(scene, tick, 'side', method, hs), (pc.W, 0))
            frames.append(sheet)
        for name, ms in (('', 50), ('-slow', 150)):
            frames[0].save(out / f'm-{method}{name}.gif', save_all=True, append_images=frames[1:], duration=ms, loop=0)
        print(method, len(frames), 'frames')


if __name__ == '__main__':
    main()
