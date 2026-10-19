"""Shared CPU renderer for previews (no game client): item/block models, textured quads, a perspective camera.
Moved out of the warrior cast preview so the player-motion preview can use it. Not Minecraft's renderer: no fog,
lighting only on flat-shaded boxes, painter's ordering."""
import json
import math
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
FONT = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 15, index=0)
SMALL = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', 12, index=0)
W, H = 480, 320
FOCAL = 300
BG = (17, 21, 27)

def T(x, y, z): m = np.eye(4); m[:3, 3] = (x, y, z); return m
def S(x, y, z): return np.diag((x, y, z, 1.0))
def Rx(a): c, s = math.cos(a), math.sin(a); return np.array(((1, 0, 0, 0), (0, c, -s, 0), (0, s, c, 0), (0, 0, 0, 1)))
def Ry(a): c, s = math.cos(a), math.sin(a); return np.array(((c, 0, s, 0), (0, 1, 0, 0), (-s, 0, c, 0), (0, 0, 0, 1)))
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array(((c, -s, 0, 0), (s, c, 0, 0), (0, 0, 1, 0), (0, 0, 0, 1)))
def Rq(q):
    x, y, z, w = q
    return np.array(((1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0),
                     (2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0),
                     (2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0), (0, 0, 0, 1)))


# ---------- pack models ----------
@lru_cache(maxsize=4096)
def item_model(key, cmd=None):
    item = json.loads((PACK / f'items/{key}.json').read_text())['model']
    if item.get('type') == 'minecraft:select':  # held weapons: hand contexts fall back to the custom_model_data dispatch
        item = item['fallback']
    if item.get('type') == 'minecraft:range_dispatch':
        chosen = item['fallback']
        for entry in item['entries']:
            if cmd is not None and cmd >= entry['threshold']: chosen = entry['model']
        item = chosen
    return model_json(item['model'].split(':')[1]), tuple(t.get('value', t.get('default', 0xffffff)) for t in item.get('tints', [{'value': 0xffffff}]))


@lru_cache(maxsize=4096)
def model_json(path):
    m = json.loads((PACK / f'models/{path}.json').read_text())
    if 'parent' in m and 'elements' not in m and m['parent'].startswith('projects:'):
        parent = model_json(m['parent'].split(':')[1])
        m = {**parent, **{k: v for k, v in m.items() if k != 'parent'}, 'textures': {**parent.get('textures', {}), **m.get('textures', {})}}
    return m


@lru_cache(maxsize=512)
def texture(name, tint):
    t = np.array(Image.open(PACK / ('textures/' + name.split(':')[1] + '.png')).convert('RGBA'))
    t = t[:t.shape[1]] if t.shape[0] > t.shape[1] else t  # animated strips: first frame
    c = np.array([(tint >> 16) & 255, (tint >> 8) & 255, tint & 255])
    t[:, :, :3] = (t[:, :, :3].astype(float) * c / 255).astype('uint8')
    return Image.fromarray(t)



FACES = (('north', (3, 2, 0, 1)), ('south', (6, 7, 5, 4)), ('down', (4, 5, 1, 0)), ('up', (2, 3, 7, 6)),
         ('west', (2, 6, 4, 0)), ('east', (7, 3, 1, 5)))


def element_rotation(e):
    r = e.get('rotation')
    if not r: return np.eye(4)
    a = math.radians(r['angle']); o = r['origin']
    rot = {'x': Rx, 'y': Ry, 'z': Rz}[r['axis']](a)
    return T(*o) @ rot @ T(-o[0], -o[1], -o[2])


# ---------- scripts/shaders/item.fsh, the heat cut (kinds 7..9), redone in numpy for the preview ----------

def model_faces(model, tints, matrix):
    """Faces of a block/item model (pixel units) placed by matrix: (world corners, texture image or rgba)."""
    out = []
    for e in model.get('elements', []):
        lo, hi = e['from'], e['to']
        m = matrix @ element_rotation(e)
        corners = [(m @ np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)] + [1.0]))[:3] for i in range(8)]
        for name, ids in FACES:
            face = e['faces'].get(name)
            if not face: continue
            tex_name = model['textures'][face['texture'][1:]]
            if isinstance(tex_name, dict): tex_name = tex_name['sprite']
            while tex_name.startswith('#'): tex_name = model['textures'][tex_name[1:]]
            tint = tints[face.get('tintindex', 0)] if 'tintindex' in face else 0xffffff
            img = texture(tex_name, tint)
            uv = face.get('uv', [0, 0, 16, 16])
            box = (round(min(uv[0], uv[2]) / 16 * img.width), round(min(uv[1], uv[3]) / 16 * img.height),
                   round(max(uv[0], uv[2]) / 16 * img.width), round(max(uv[1], uv[3]) / 16 * img.height))
            if box[2] <= box[0]: box = (box[0], box[1], box[0] + 1, box[3])
            if box[3] <= box[1]: box = (box[0], box[1], box[2], box[1] + 1)
            crop = img.crop(box)
            if uv[1] > uv[3]: crop = crop.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            if uv[0] > uv[2]: crop = crop.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            if face.get('rotation'): crop = crop.rotate(-face['rotation'], expand=True, resample=Image.Resampling.NEAREST)
            if crop.size == (1, 1):
                ink = crop.getpixel((0, 0))
                if ink[3] == 0: continue
                if ink[3] == 255: crop = ink
            out.append(([corners[i] for i in ids], crop))
    return out


class Camera:
    def __init__(self, eye, target):
        self.eye = np.array(eye, float)
        f = np.array(target, float) - self.eye; self.f = f / np.linalg.norm(f)
        r = np.cross(self.f, (0, 1, 0)); self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)
        light = np.array((.3, 1.0, -.5)) / np.linalg.norm((.3, 1.0, -.5))
        self.cam_dir_light = np.array((light @ self.r, light @ self.u, light @ self.f))

    def cam(self, p):
        d = np.asarray(p) - self.eye
        return np.array((d @ self.r, d @ self.u, d @ self.f))

    def screen(self, c):
        return (W / 2 + c[0] / c[2] * FOCAL, H / 2 - c[1] / c[2] * FOCAL)


def clip(poly, near=.15):
    out = []
    for a, b in zip(poly, poly[1:] + poly[:1]):
        if a[2] >= near: out.append(a)
        if (a[2] >= near) != (b[2] >= near):
            t = (near - a[2]) / (b[2] - a[2]); out.append(a + (b - a) * t)
    return out


def corners_of(cam, camera):
    """Back from camera space to world space (for re-drawing a face after recolouring it)."""
    return [camera.eye + c[0] * camera.r + c[1] * camera.u + c[2] * camera.f for c in cam]


def draw_faces(image, camera, faces):
    items = []
    for corners, content in faces:
        cam = [camera.cam(c) for c in corners]
        if max(c[2] for c in cam) < .15: continue
        full = [camera.screen(c) if c[2] > 1e-3 else None for c in cam]
        vis = [camera.screen(c) for c in clip(cam)]
        if len(vis) < 3: continue
        area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(vis, vis[1:] + vis[:1]))
        if area <= 1e-7: continue  # back face
        depth = sum(c[2] for c in cam) / len(cam)
        items.append((depth, vis, full, content, cam))
    for depth, vis, full, content, cam in sorted(items, key=lambda i: -i[0]):
        if isinstance(content, tuple) and content[0] == 'shade':
            n = np.cross(cam[1] - cam[0], cam[2] - cam[0]); n = n / (np.linalg.norm(n) + 1e-9)
            lit = .55 + .45 * max(0.0, float(n @ camera.cam_dir_light))
            ImageDraw.Draw(image).polygon(vis, fill=tuple(int(v * lit) for v in content[1]))
        elif isinstance(content, tuple) and content[0] == 'shadeimg':
            # A textured face lit like the shaded boxes: (shadeimg, image, extra glow rgb or None).
            n = np.cross(cam[1] - cam[0], cam[2] - cam[0]); n = n / (np.linalg.norm(n) + 1e-9)
            lit = .55 + .45 * max(0.0, float(n @ camera.cam_dir_light))
            arr = np.array(content[1].convert('RGBA')).astype(float)
            arr[..., :3] *= lit
            if content[2] is not None: arr[..., :3] = arr[..., :3] + np.array(content[2])
            draw_faces(image, camera, [(corners_of(cam, camera), Image.fromarray(np.clip(arr, 0, 255).astype('uint8')))])
        elif isinstance(content, tuple):
            ImageDraw.Draw(image).polygon(vis, fill=content)
        else:
            if any(p is None for p in full): continue
            x0, y0 = max(0, int(min(p[0] for p in vis))), max(0, int(min(p[1] for p in vis)))
            x1, y1 = min(W, int(max(p[0] for p in vis)) + 1), min(H, int(max(p[1] for p in vis)) + 1)
            if x1 <= x0 or y1 <= y0: continue
            tw, th = content.size
            matrix, result = [], []
            for (x, y), (u, v) in zip(full, ((0, 0), (tw, 0), (tw, th), (0, th))):
                x -= x0; y -= y0
                matrix.extend(((x, y, 1, 0, 0, 0, -u * x, -u * y), (0, 0, 0, x, y, 1, -v * x, -v * y))); result.extend((u, v))
            try: coeff = np.linalg.solve(matrix, result)
            except np.linalg.LinAlgError: continue
            warped = content.transform((x1 - x0, y1 - y0), Image.Transform.PERSPECTIVE, tuple(coeff), Image.Resampling.NEAREST)
            mask = Image.new('L', warped.size, 0)
            ImageDraw.Draw(mask).polygon([(p[0] - x0, p[1] - y0) for p in vis], fill=255)
            alpha = np.minimum(np.array(warped.getchannel('A')), np.array(mask))
            warped.putalpha(Image.fromarray(alpha))
            image.alpha_composite(warped, (x0, y0))


def grid(image, camera):
    draw = ImageDraw.Draw(image)
    for n in range(-8, 9):
        for a, b in (((n, 0, -6), (n, 0, 10)), ((-8, 0, n + 2), (8, 0, n + 2))):
            pa, pb = camera.cam(np.array(a, float)), camera.cam(np.array(b, float))
            if pa[2] < .2 and pb[2] < .2: continue
            if pa[2] < .2: pa = pa + (pb - pa) * ((.2 - pa[2]) / (pb[2] - pa[2]))
            if pb[2] < .2: pb = pb + (pa - pb) * ((.2 - pb[2]) / (pa[2] - pb[2]))
            draw.line([camera.screen(pa), camera.screen(pb)], fill=(34, 42, 52))


