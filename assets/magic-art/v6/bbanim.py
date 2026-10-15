"""Blockbench exporter with per-face painted textures and texture animation.

Each element face is painted at its real texel size (no stretching), all faces are packed
into one atlas per render mode (body / glow), and the atlas is repeated over F frames
stacked vertically (Minecraft animated texture, 1 frame per tick). Materials are painters:
    mat(W, H, face, frame, F) -> PIL RGBA image of W x H
Elements: dict(fr, to, mat, emissive=False, rot=None | (axis, angle, origin))."""
import io
import json
import math
import base64
import uuid
from PIL import Image

FACES = ('north', 'east', 'south', 'west', 'up', 'down')


def face_size(fr, to, face):
    dx, dy, dz = (to[i] - fr[i] for i in range(3))
    if face in ('north', 'south'):
        return dx, dy
    if face in ('east', 'west'):
        return dz, dy
    return dx, dz


def pack(cells, width):
    pos, x, y, rowh = {}, 0, 0, 0
    for key, w, h in sorted(cells, key=lambda c: -c[2]):
        if x + w > width:
            x, y, rowh = 0, y + rowh, 0
        pos[key] = (x, y)
        x += w
        rowh = max(rowh, h)
    return pos, y + rowh


def export(elements, F, title, path, png_dir=None):
    cells = {False: [], True: []}
    sizes = {}
    for i, el in enumerate(elements):
        for face in FACES:
            w, h = face_size(el['fr'], el['to'], face)
            W, H = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))
            sizes[(i, face)] = (w, h, W, H)
            cells[bool(el.get('emissive'))].append(((i, face), W, H))
    lay = {}
    for k, c in cells.items():
        S = 16
        while True:
            pos, hh = pack(c, S)
            if hh <= S:
                break
            S *= 2
        lay[k] = (pos, S)
    atlases = {}
    for k, (pos, S) in lay.items():
        if not cells[k]:
            continue
        frames = F if k else 1
        img = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
        for i, el in enumerate(elements):
            if bool(el.get('emissive')) != k:
                continue
            for face in FACES:
                w, h, W, H = sizes[(i, face)]
                x, y = pos[(i, face)]
                for f in range(frames):
                    t = el['mat'](W, H, face, f, F)
                    img.paste(t, (x, y + f * S))
        atlases[k] = (img, S, frames)
    texs, tid = [], {}
    for k, (img, S, frames) in atlases.items():
        buf = io.BytesIO()
        img.save(buf, 'PNG')
        name = f"{title}{'_glow' if k else ''}.png"
        if png_dir:
            img.save(f"{png_dir}/{name}")
        tid[k] = len(texs)
        texs.append({
            "name": name, "folder": "", "namespace": "", "id": str(len(texs)),
            "width": S, "height": S * frames, "uv_width": S, "uv_height": S,
            "particle": not k, "render_mode": "emissive" if k else "default", "render_sides": "auto",
            "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
            "visible": True, "internal": True, "saved": True, "uuid": str(uuid.uuid4()),
            "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()})
    out = []
    for i, el in enumerate(elements):
        k = bool(el.get('emissive'))
        pos, S = lay[k]
        rot, origin = [0, 0, 0], [8, 8, 8]
        if el.get('rot'):
            ax, ang, o = el['rot']
            rot['xyz'.index(ax)] = ang
            origin = list(o)
        fj = {}
        for face in FACES:
            w, h, W, H = sizes[(i, face)]
            x, y = pos[(i, face)]
            fj[face] = {"uv": [x, y, x + w, y + h], "texture": tid[k]}
        out.append({
            "name": el.get('name', f'part{i}'), "box_uv": False, "rescale": False, "locked": False,
            "light_emission": 15 if k else 0, "render_order": "default",
            "from": [round(v, 4) for v in el['fr']], "to": [round(v, 4) for v in el['to']],
            "autouv": 0, "color": i % 8, "origin": [round(v, 4) for v in origin],
            "rotation": [round(v, 4) for v in rot], "faces": fj, "type": "cube", "uuid": str(uuid.uuid4())})
    bb = {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": title, "model_identifier": "", "visible_box": [1, 1, 0],
        "resolution": {"width": lay[False][1], "height": lay[False][1]},
        "elements": out, "outliner": [e["uuid"] for e in out], "textures": texs,
    }
    with open(path, 'w', encoding='utf-8') as fp:
        json.dump(bb, fp, ensure_ascii=False)
    return len(out), {k: (v[1], v[2]) for k, v in atlases.items()}
