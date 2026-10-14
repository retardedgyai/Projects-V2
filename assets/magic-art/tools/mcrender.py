"""Tiny Minecraft-style model renderer: axis-aligned elements, 16px textures,
GUI-like orthographic view with vanilla face shading. Used to turn block models
into item icons and placement previews."""
import json
import math
from PIL import Image

SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def face_quad(fr, to, face):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    # corners ordered so (u,v) = (0,0) top-left, (1,0) top-right, (0,1) bottom-left
    if face == 'north':   # -z, looking from north: left is +x
        return [(x1, y1, z0), (x0, y1, z0), (x1, y0, z0)]
    if face == 'south':   # +z
        return [(x0, y1, z1), (x1, y1, z1), (x0, y0, z1)]
    if face == 'west':    # -x
        return [(x0, y1, z0), (x0, y1, z1), (x0, y0, z0)]
    if face == 'east':    # +x
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z1)]
    if face == 'up':
        return [(x0, y1, z0), (x1, y1, z0), (x0, y1, z1)]
    if face == 'down':
        return [(x0, y0, z1), (x1, y0, z1), (x0, y0, z0)]


def default_uv(fr, to, face):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    if face in ('north', 'south'):
        return [x0 if face == 'south' else 16 - x1, 16 - y1, x1 if face == 'south' else 16 - x0, 16 - y0]
    if face in ('east', 'west'):
        return [z0 if face == 'west' else 16 - z1, 16 - y1, z1 if face == 'west' else 16 - z0, 16 - y0]
    return [x0, z0, x1, z1]


def render(model, textures, size=128, rot_x=30, rot_y=225, scale=0.625, pad=0.0, shade=True, light=None):
    """model: dict with 'elements'; textures: name -> PIL RGBA 16x16 (or any square)."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    zbuf = [[-1e9] * size for _ in range(size)]
    px = img.load()
    ry, rx = math.radians(rot_y), math.radians(rot_x)

    def tf(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        # rotate around Y
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        # rotate around X (tilt camera down)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        k = size / 16.0 * scale / 0.625 * 0.62
        return (size / 2 + x * k, size / 2 - y * k + pad, z)

    trans = []
    for el in model['elements']:
        fr, to = el['from'], el['to']
        for face, fd in el.get('faces', {}).items():
            tex = textures[fd['texture'].lstrip('#')]
            tw, th = tex.size
            tp = tex.load()
            uv = fd.get('uv') or default_uv(fr, to, face)
            a, b, c = [tf(p) for p in face_quad(fr, to, face)]
            # back-face culling via 2D cross product
            ux, uy = b[0] - a[0], b[1] - a[1]
            vx, vy = c[0] - a[0], c[1] - a[1]
            if ux * vy - uy * vx <= 0:
                continue
            fshade = SHADE[face] if shade else 1.0
            span = max(abs(uv[2] - uv[0]), abs(uv[3] - uv[1]), 1)
            steps = int(max(math.hypot(ux, uy), math.hypot(vx, vy)) * 2.2) + 2
            for i in range(steps):
                s = (i + 0.5) / steps
                for j in range(steps):
                    t = (j + 0.5) / steps
                    X = a[0] + ux * s + vx * t
                    Y = a[1] + uy * s + vy * t
                    Z = a[2] + (b[2] - a[2]) * s + (c[2] - a[2]) * t
                    ix, iy = int(X), int(Y)
                    if not (0 <= ix < size and 0 <= iy < size):
                        continue
                    if Z < zbuf[iy][ix]:
                        continue
                    u = uv[0] + (uv[2] - uv[0]) * s
                    v = uv[1] + (uv[3] - uv[1]) * t
                    tx = min(tw - 1, max(0, int(u / 16 * tw)))
                    ty = min(th - 1, max(0, int(v / 16 * th)))
                    col = tp[tx, ty]
                    if col[3] == 0:
                        continue
                    if col[3] < 255:
                        trans.append((Z, ix, iy, col, fshade))
                        continue
                    zbuf[iy][ix] = Z
                    if light and fd.get('emissive'):
                        sh = 1.0
                    else:
                        sh = fshade
                    px[ix, iy] = (int(col[0] * sh), int(col[1] * sh), int(col[2] * sh), 255)
    seen = set()
    for Z, ix, iy, col, sh in sorted(trans, key=lambda t: t[0]):
        if Z < zbuf[iy][ix] - 1e-6:
            continue
        key = (ix, iy, round(Z, 1))
        if key in seen:
            continue
        seen.add(key)
        a = col[3] / 255.0
        bg = px[ix, iy]
        if bg[3] == 0:
            px[ix, iy] = (int(col[0] * sh), int(col[1] * sh), int(col[2] * sh), int(col[3]))
        else:
            px[ix, iy] = (int(bg[0] * (1 - a) + col[0] * sh * a), int(bg[1] * (1 - a) + col[1] * sh * a), int(bg[2] * (1 - a) + col[2] * sh * a), 255)
    return img


def box(fr, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), uv=None, emissive=False):
    f = {}
    for k in faces:
        t = tex[k] if isinstance(tex, dict) else tex
        d = {'texture': '#' + t}
        if uv and k in uv:
            d['uv'] = uv[k]
        if emissive:
            d['emissive'] = True
        f[k] = d
    return {'from': list(fr), 'to': list(to), 'faces': f}
