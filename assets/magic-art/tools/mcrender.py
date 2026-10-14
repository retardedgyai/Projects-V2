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


def render(model, textures, size=128, rot_x=30, rot_y=225, scale=0.625, pad=0.0, shade=True, light=None, center=(8, 8, 8),
           ao=False, ao_skip=(), cool_shadow=False, gradient=False, bevel=False, ao_k=0.11):
    """model: dict with 'elements'; textures: name -> PIL RGBA 16x16 (or any square)."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    zbuf = [[-1e9] * size for _ in range(size)]
    px = img.load()
    ry, rx = math.radians(rot_y), math.radians(rot_x)

    def tf(p):
        x, y, z = p[0] - center[0], p[1] - center[1], p[2] - center[2]
        # rotate around Y
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        # rotate around X (tilt camera down)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        k = size / 16.0 * scale / 0.625 * 0.62
        return (size / 2 + x * k, size / 2 - y * k + pad, z)

    solids = []
    for el in model['elements']:
        texs = {f['texture'].lstrip('#') for f in el.get('faces', {}).values()}
        if not (texs & set(ao_skip)):
            solids.append((el['from'], el['to']))

    def occ(p):
        for (f0, t0) in solids:
            if f0[0] < p[0] < t0[0] and f0[1] < p[1] < t0[1] and f0[2] < p[2] < t0[2]:
                return True
        return False

    NRM = {'up': (0, 1, 0), 'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0)}
    trans = []
    for el in model['elements']:
        fr, to = el['from'], el['to']
        for face, fd in el.get('faces', {}).items():
            tex = textures[fd['texture'].lstrip('#')]
            tw, th = tex.size
            tp = tex.load()
            uv = fd.get('uv') or default_uv(fr, to, face)
            qa, qb, qc = face_quad(fr, to, face)
            rot = el.get('rotation')
            if rot:
                ang = math.radians(rot['angle']); o = rot['origin']; ax = rot['axis']
                def rp(p, ang=ang, o=o, ax=ax):
                    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
                    if ax == 'x':
                        y, z = y * math.cos(ang) - z * math.sin(ang), y * math.sin(ang) + z * math.cos(ang)
                    elif ax == 'y':
                        x, z = x * math.cos(ang) + z * math.sin(ang), -x * math.sin(ang) + z * math.cos(ang)
                    else:
                        x, y = x * math.cos(ang) - y * math.sin(ang), x * math.sin(ang) + y * math.cos(ang)
                    return (x + o[0], y + o[1], z + o[2])
                qa, qb, qc = rp(qa), rp(qb), rp(qc)
            a, b, c = tf(qa), tf(qb), tf(qc)
            nrm = NRM[face]
            tang = [d for d in ((1, 0, 0), (0, 1, 0), (0, 0, 1)) if abs(d[0] * nrm[0] + d[1] * nrm[1] + d[2] * nrm[2]) == 0]
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
                    emis = light and fd.get('emissive')
                    sh = 1.0 if emis else fshade
                    if gradient and not emis and face in ('north', 'south', 'east', 'west'):
                        sh *= 1.06 - 0.16 * t
                    if ao and not emis:
                        P = [qa[k] + (qb[k] - qa[k]) * s + (qc[k] - qa[k]) * t for k in range(3)]
                        base = [P[k] + nrm[k] * 0.45 for k in range(3)]
                        hits = 0
                        for d in tang:
                            for sg in (1, -1):
                                q = [base[k] + d[k] * sg * 0.9 for k in range(3)]
                                if occ(q):
                                    hits += 1
                        if occ([P[k] + nrm[k] * 0.9 for k in range(3)]):
                            hits += 2
                        sh *= 1.0 - min(hits, 4) * ao_k
                    if bevel and not emis:
                        # world-space edge distance on this face (units of model px)
                        L1 = math.dist(qa, qb); L2 = math.dist(qa, qc)
                        e_top = t * L2; e_bot = (1 - t) * L2; e_l = s * L1; e_r = (1 - s) * L1
                        if face == 'up':
                            if min(e_top, e_l) < 0.55: sh *= 1.22
                            elif min(e_bot, e_r) < 0.55: sh *= 0.86
                        elif face in ('north', 'south', 'east', 'west'):
                            if e_top < 0.55: sh *= 1.18
                            elif e_bot < 0.55: sh *= 0.8
                            elif e_r < 0.45: sh *= 0.9
                    r_, g_, b_ = col[0] * sh, col[1] * sh, col[2] * sh
                    if cool_shadow and not emis and sh < 0.98:
                        k = (1.0 - sh)
                        r_ += k * 6; g_ += k * 4; b_ += k * 22
                    px[ix, iy] = (min(255, int(r_)), min(255, int(g_)), min(255, int(b_)), 255)
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


def rotated(el, axis, angle, origin):
    el = dict(el)
    el['rotation'] = {'axis': axis, 'angle': angle, 'origin': list(origin)}
    return el


def outline(img, color=(255, 255, 255, 255), width=1):
    """Draw a 1px coloured rim around the silhouette (Anz-style presentation)."""
    from PIL import Image
    w, h = img.size
    src = img.load()
    out = img.copy()
    op = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and src[xx, yy][3] > 0:
                        op[x, y] = color
                        break
    return out
