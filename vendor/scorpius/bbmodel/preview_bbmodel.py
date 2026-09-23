#!/usr/bin/env python3
"""bbmodel を正投影で PNG に描く（造形・アニメ姿勢の目視確認用。実機の代わりにはならない）。

python3 bbmodel/preview_bbmodel.py <model.bbmodel> <out.png> [anim@sec ...] [--track 名前 ...] [--hide ボーン ...]
姿勢を省くと静止形を前・横・上から描く。anim@sec を並べると、その姿勢の前・横を格子に並べる。
--track を付けると、その名前のキューブ中心のワールド座標を姿勢ごとに表示する（刃の位置確認など）。
--hide はそのボーン直下のキューブを描かない（setBoneVisible で剥がれた姿の確認）。
回転の向きは WSEE 実機と同じ（Blockbench 旧形式: アニメの x,y は符号反転、静止回転はそのまま、順序 ZYX）。
前は -z。前面図は +x（モデルの右手）が画像の左に来る。
"""
import base64
import io
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

SHADE = {(1, 1): 1.0, (1, -1): .45, (2, -1): .82, (2, 1): .72, (0, 1): .6, (0, -1): .6}
BLOCK_UNITS = 16 / 3  # ModelScale 3 での 1 ブロック
FACES = {
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0), (0, 0, -1)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1), (0, 0, 1)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1), (1, 0, 0)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0), (-1, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1), (0, 1, 0)),
    "down": ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1), (0, -1, 0)),
}
VIEWS = {  # (u 軸, v 軸, 奥行き軸) を [係数, 成分] で。奥行きは小さいほど手前。
    "front": ((-1, 0), (-1, 1), (1, 2)),
    "side": ((-1, 2), (-1, 1), (-1, 0)),
    "top": ((1, 0), (1, 2), (-1, 1)),
}


def load(path, hidden=()):
    data = json.load(open(path))
    source = data["textures"][0]["source"].split(",", 1)[1]
    atlas = np.array(Image.open(io.BytesIO(base64.b64decode(source))).convert("RGBA"))
    skip = set()
    def walk(node):
        for child in node.get("children", []):
            if isinstance(child, dict):
                walk(child)
            elif node["name"] in hidden:
                skip.add(child)
    walk(data["outliner"][0])
    elements = {e["uuid"]: e for e in data["elements"] if e["uuid"] not in skip}
    anims = {a["name"]: a for a in data.get("animations", [])}
    return data, elements, atlas, anims


def rot(euler):
    x, y, z = (math.radians(v) for v in euler)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def channel(anim, bone_uuid, name, t):
    animator = (anim or {}).get("animators", {}).get(bone_uuid)
    if not animator:
        return (0, 0, 0)
    keys = sorted(((k["time"], k["data_points"][0]) for k in animator["keyframes"] if k["channel"] == name),
                  key=lambda k: k[0])
    if not keys:
        return (0, 0, 0)
    def val(p):
        return tuple(float(p[a]) for a in "xyz")
    if t <= keys[0][0]:
        return val(keys[0][1])
    for (t0, p0), (t1, p1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            a = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            v0, v1 = val(p0), val(p1)
            return tuple(v0[i] + (v1[i] - v0[i]) * a for i in range(3))
    return val(keys[-1][1])


def transforms(node, anim, t, parent, out):
    """各ボーンの 4x4 ワールド変換。子キューブは親ボーンの変換を継ぐ。"""
    pivot = np.array(node.get("origin", [0, 0, 0]), dtype=float)
    static = node.get("rotation", [0, 0, 0])
    ax, ay, az = channel(anim, node["uuid"], "rotation", t)
    px, py, pz = channel(anim, node["uuid"], "position", t)
    r = rot((static[0] - ax, static[1] - ay, static[2] + az))
    local = np.eye(4)
    local[:3, :3] = r
    local[:3, 3] = pivot - r @ pivot + np.array([-px, py, pz])
    m = parent @ local
    for child in node.get("children", []):
        if isinstance(child, dict):
            transforms(child, anim, t, m, out)
        else:
            out[child] = m
    return out


def project(points, view):
    (su, au), (sv, av), (sd, ad) = VIEWS[view]
    return np.stack([su * points[:, au], sv * points[:, av], sd * points[:, ad]], axis=1)


def render(data, elements, atlas, anim, t, view, px, canvas=None, origin=None):
    """canvas=(幅,高さ), origin=(u0,v0) を与えると同じ枡で描く。無ければ自動で収める。"""
    world = transforms(data["outliner"][0], anim, t, np.eye(4), {})
    quads = []
    for uuid, el in elements.items():
        m = world.get(uuid, np.eye(4))
        lo, hi = np.array(el["from"], dtype=float), np.array(el["to"], dtype=float)
        element_rotation = rot(el.get("rotation", [0, 0, 0]))
        element_pivot = np.array(el.get("origin", [0, 0, 0]), dtype=float)
        for face, (*corners, normal) in FACES.items():
            uv = el["faces"][face]["uv"]
            if uv == [0, 0, 1, 1]:
                continue
            pts = np.array([[lo[i] if c[i] == 0 else hi[i] for i in range(3)] for c in corners])
            pts = (element_rotation @ (pts - element_pivot).T).T + element_pivot
            pts = (m[:3, :3] @ pts.T).T + m[:3, 3]
            n = m[:3, :3] @ element_rotation @ np.array(normal, dtype=float)
            quads.append((project(pts, view), n, uv, el["name"]))
    allp = np.concatenate([q[0] for q in quads])
    if canvas is None:
        lo, hi = allp.min(axis=0), allp.max(axis=0)
        margin = 2
        origin = (lo[0] - margin, lo[1] - margin)
        canvas = (int((hi[0] - lo[0] + 2 * margin) * px) + 1, int((hi[1] - lo[1] + 2 * margin) * px) + 1)
    w, h = canvas
    img = np.zeros((h, w, 4), dtype=np.uint8)
    img[..., :3] = (36, 32, 40)
    img[..., 3] = 255
    depth = np.full((h, w), np.inf)
    ys, xs = np.mgrid[0:h, 0:w]
    ucoord = origin[0] + (xs + .5) / px
    vcoord = origin[1] + (ys + .5) / px
    for pts, n, uv, _ in quads:
        e1, e2 = pts[1] - pts[0], pts[3] - pts[0]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        x0, x1 = max(0, int((pts[:, 0].min() - origin[0]) * px) - 1), min(w, int((pts[:, 0].max() - origin[0]) * px) + 2)
        y0, y1 = max(0, int((pts[:, 1].min() - origin[1]) * px) - 1), min(h, int((pts[:, 1].max() - origin[1]) * px) + 2)
        if x0 >= x1 or y0 >= y1:
            continue
        du, dv = ucoord[y0:y1, x0:x1] - pts[0][0], vcoord[y0:y1, x0:x1] - pts[0][1]
        s = (du * e2[1] - dv * e2[0]) / det
        tt = (e1[0] * dv - e1[1] * du) / det
        inside = (s >= 0) & (s <= 1) & (tt >= 0) & (tt <= 1)
        d = pts[0][2] + s * (pts[1][2] - pts[0][2]) + tt * (pts[3][2] - pts[0][2])
        tx = np.clip(np.floor(uv[0] + s * (uv[2] - uv[0])).astype(int), min(uv[0], uv[2]), max(uv[0], uv[2]) - 1)
        ty = np.clip(np.floor(uv[1] + tt * (uv[3] - uv[1])).astype(int), min(uv[1], uv[3]), max(uv[1], uv[3]) - 1)
        texel = atlas[ty, tx]
        axis = int(np.argmax(np.abs(n)))
        shade = SHADE[(axis, 1 if n[axis] >= 0 else -1)]
        win = depth[y0:y1, x0:x1]
        hit = inside & (texel[..., 3] > 0) & (d < win)
        win[hit] = d[hit]
        img[y0:y1, x0:x1][hit, :3] = np.clip(texel[hit][:, :3] * shade, 0, 255).astype(np.uint8)
    out = Image.fromarray(img)
    draw = ImageDraw.Draw(out)
    if view != "top":
        for k in range(0, 8):
            y = int((-k * BLOCK_UNITS - origin[1]) * px)
            if 0 <= y < h:
                draw.line([(0, y), (w, y)], fill=(70, 64, 80, 255))
        ground = int((0 - origin[1]) * px)
        draw.line([(0, ground), (w, ground)], fill=(150, 120, 60, 255))
    ucenter = int((0 - origin[0]) * px)
    draw.line([(ucenter, 0), (ucenter, h)], fill=(70, 64, 80, 255))
    return out


def track(data, elements, anim, t, names):
    world = transforms(data["outliner"][0], anim, t, np.eye(4), {})
    lines = []
    for uuid, el in elements.items():
        if el["name"] in names:
            m = world.get(uuid, np.eye(4))
            c = (np.array(el["from"]) + np.array(el["to"])) / 2
            p = m[:3, :3] @ c + m[:3, 3]
            lines.append(f"    {el['name']}: x={p[0]:6.1f} y={p[1]:6.1f} z={p[2]:6.1f}")
    return lines


def main():
    args = [a for a in sys.argv[1:]]
    tracked, hidden = [], []
    for flag, into in (("--track", tracked), ("--hide", hidden)):
        while flag in args:
            i = args.index(flag)
            into.append(args[i + 1])
            del args[i:i + 2]
    path, out, poses = args[0], args[1], args[2:]
    data, elements, atlas, anims = load(path, hidden)
    if not poses:
        views = [render(data, elements, atlas, None, 0, v, 6) for v in ("front", "side", "top")]
        sheet = Image.new("RGBA", (sum(v.width for v in views) + 8 * (len(views) - 1), max(v.height for v in views)), (20, 18, 24, 255))
        x = 0
        for v in views:
            sheet.paste(v, (x, sheet.height - v.height))
            x += v.width + 8
    else:
        cells = []
        for pose in poses:
            name, _, at = pose.partition("@")
            t = float(at or 0)
            anim = anims[name]
            cell = [render(data, elements, atlas, anim, t, v, 4) for v in ("front", "side")]
            label = f"{name}@{t:g}"
            cells.append((label, cell))
            if tracked:
                print(label)
                print("\n".join(track(data, elements, anim, t, tracked)))
        cw = max(a.width + b.width + 4 for _, (a, b) in cells)
        ch = max(max(a.height, b.height) for _, (a, b) in cells) + 14
        cols = min(4, len(cells))
        rows = math.ceil(len(cells) / cols)
        sheet = Image.new("RGBA", (cols * (cw + 6), rows * (ch + 6)), (20, 18, 24, 255))
        draw = ImageDraw.Draw(sheet)
        for i, (label, (a, b)) in enumerate(cells):
            x, y = (i % cols) * (cw + 6), (i // cols) * (ch + 6)
            draw.text((x + 2, y), label, fill=(230, 220, 200, 255))
            sheet.paste(a, (x, y + 14 + (ch - 14 - a.height)))
            sheet.paste(b, (x + a.width + 4, y + 14 + (ch - 14 - b.height)))
    sheet.save(out)
    print(f"{out}: {sheet.width}x{sheet.height}")


if __name__ == "__main__":
    main()
