"""ProjectS original ice-fang: texture-shaped thin relief, native bone animation.

Uses Scorpius's bbmodel authoring convention, not its boss art. Deterministic
source asset generation; no live pack is edited. 16x32 painted facet footprint,
opaque pixels only, no gradients, no image-plane spinning or animation frames.
"""
import base64
import json
from pathlib import Path
import struct
import uuid
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "model-lab/models/ice_fang.bbmodel"


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "projects:ice-fang/" + name))


def atlas():
    # Hand-authored pixel silhouette: broken side spur, pointed crown, broad root.
    spans = [(11, 12), (11, 12), (10, 12), (10, 12), (9, 12), (9, 12),
             (8, 12), (8, 12), (7, 12), (7, 12), (6, 12), (6, 12),
             (5, 12), (5, 12), (4, 12), (4, 12), (4, 13), (3, 13),
             (3, 13), (3, 13), (1, 13), (1, 13), (2, 13), (2, 14),
             (2, 14), (2, 14), (2, 14), (1, 14), (1, 14), (1, 15),
             (0, 15), (0, 15)]
    palette = [(25, 48, 91, 255), (41, 87, 131, 255), (62, 139, 174, 255),
               (107, 195, 207, 255), (183, 236, 230, 255), (240, 255, 237, 255)]
    pixels = [[(0, 0, 0, 0)] * 32 for _ in range(32)]
    for y, (left, right) in enumerate(spans):
        ridge = 11 - y // 7
        for x in range(left, right):
            color = 4 if x <= ridge else 2
            if x == left or x == ridge: color = 5
            if x >= right - 2: color = 1
            if y > 24 and x < ridge: color = 3
            if (y in (14, 15) and x > ridge) or (y == 25 and x < ridge): color = 0
            pixels[y][x] = palette[color]
    for y in range(32):
        for x in range(16, 32):
            pixels[y][x] = palette[min(5, (x - 16) // 3)]
    raw = b"".join(b"\0" + bytes(c for pixel in row for c in pixel) for row in pixels)
    def chunk(kind, value):
        return struct.pack(">I", len(value)) + kind + value + struct.pack(">I", zlib.crc32(kind + value) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    return spans, png


def build():
    spans, png = atlas()
    elements, bones, tracks = [], [], {}
    def cube(name, lo, hi, uv, side=(19, 0, 22, 4)):
        ident = uid(name)
        faces = {f: {"uv": list(uv if f in ("north", "south") else side), "texture": 0}
                 for f in ("north", "south", "east", "west", "up", "down")}
        elements.append(dict(name=name, uuid=ident, type="cube", **{"from": lo, "to": hi},
                             origin=[0, 0, 0], rescale=False, autouv=0, faces=faces))
        return ident
    def bone(name, children, rotation, positions, rotations=None):
        ident = uid(name)
        bones.append(dict(name=name, uuid=ident, origin=[0, 0, 0], rotation=rotation,
                          export=True, visibility=True, children=children))
        keys = []
        for channel, sequence in (("position", positions), ("rotation", rotations or [])):
            for t, v in sequence:
                keys.append(dict(channel=channel, time=t, interpolation="linear", uuid=uid(f"{name}/{channel}/{t}"),
                                 data_points=[dict(zip("xyz", v))]))
        tracks[ident] = dict(name=name, type="bone", keyframes=keys)
    # Each painted row has real thickness; silhouettes remain pointed from oblique views.
    for index, (factor, lean, yaw, xoff, zoff) in enumerate([
        (1.12, -12, 8, 0, 0), (.78, 32, -24, -4, 1), (.64, -40, 30, 4, 1)]):
        children = []
        for y, (left, right) in enumerate(spans):
            depth = (.65 + y / 32 * 1.3) * factor
            children.append(cube(f"fang{index}_row{y}", [(left - 8)*factor+xoff, (31-y)*factor, zoff-depth],
                                 [(right - 8)*factor+xoff, (32-y)*factor, zoff+depth], [left, y, right, y+1]))
        delay = index * .035
        bone(f"fang{index}", children, [0, yaw, lean],
             [(0, [0, -42, 0]), (.06+delay, [0, -42, 0]), (.20+delay, [0, 0, 0]),
              (.32+delay, [0, -.7, 0]), (.75, [0, -.7, 0]), (.95, [index*2-2, 1, 0]),
              (1.22, [index*3-3, -14, 0]), (1.5, [index*3-3, -46, 0])],
             [(0, [0, 0, 0]), (.75, [0, 0, 0]), (1.1, [0, 0, (index-1)*12]), (1.5, [0, 0, (index-1)*18])])
    # Small solid splinters detach once at fracture; no full-size duplicate crystal replay.
    for i in range(4):
        fragment = []
        for row, (l, r) in enumerate(spans[:10]):
            fragment.append(cube(f"chip{i}_{row}", [(l-11)*.5, (9-row)*.6, -.3],
                                 [(r-11)*.5, (10-row)*.6, .3], [l, row, r, row+1]))
        side = -1 if i % 2 == 0 else 1
        bone(f"chip{i}", fragment, [0, i*42, side*22],
             [(0, [0, -12, 0]), (.76, [0, -12, 0]), (.82, [side*2, 7+i*2, 0]),
              (1.00, [side*(9+i), 17+i, i-2]), (1.18, [side*(13+i), 9, i-2]),
              (1.4, [side*(15+i), -10, i-2]), (1.5, [side*(15+i), -16, i-2])],
             [(0, [0, 0, 0]), (.8, [0, 0, 0]), (1.5, [20, side*35, side*55])])
    cracks = []
    for i in range(11):
        x = (i % 3 - 1)*1.3
        cracks.append(cube(f"crack{i}", [x-.6, 0, i*1.5-9], [x+.6, .12, i*1.5-7.2],
                           [27, 0, 30, 3], side=(27, 0, 30, 3)))
    bone("root_crack", cracks, [0, 0, 0], [(0, [0, -.4, 0]), (.04, [0, .08, 0]),
                                             (.95, [0, .08, 0]), (1.2, [0, -.4, 0]), (1.5, [0, -.4, 0])])
    return dict(meta=dict(format_version="4.0", model_format="free", box_uv=False),
                name="ice_fang", resolution=dict(width=32, height=32), elements=elements,
                outliner=[dict(name="model", uuid=uid("model"), origin=[0, 0, 0], children=bones)],
                textures=[dict(name="ice_fang.png", id="0", uuid=uid("texture"), width=32, height=32,
                               uv_width=32, uv_height=32, source="data:image/png;base64,"+base64.b64encode(png).decode())],
                animations=[dict(name="erupt", uuid=uid("erupt"), loop="once", override=True,
                                 length=1.5, snapping=20, animators=tracks)])


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(build(), separators=(",", ":")), encoding="utf-8")
    print(OUT)
