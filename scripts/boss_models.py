"""Blockbench boss models: the small authoring helper and the validator the build relies on.

The committed `.bbmodel` files under assets/bosses/ are the source of truth (decision D12).
A boss script (e.g. build_boss_shade_wyrm.py) may write the first version; after that, a human
may edit the file in Blockbench and the script is not re-run blindly.

    python scripts/boss_models.py validate            # every assets/bosses/*.bbmodel
    python scripts/boss_models.py preview shade_wyrm  # build/boss-previews/shade_wyrm.png (CPU, NOT Minecraft)

Server conventions checked here (see docs/development/boss-model-pipeline.md):
- front faces +Z, 16 px = 1 block, model_format "bedrock"; scripts write format_version 4.10
  (legacy animation axes: a raw positive X rotation lifts the +Z end in game);
- bone names are unique; no bone is named "head" or contains "hitbox"/"seat"/"nametag"
  (WSEE gives those names special behavior; the server owns hitboxes);
- animations `idle` and `walk` loop; every ability animation is `once`.
"""
import base64
import json
import math
import struct
import sys
import uuid
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/bosses"
RESERVED = ("hitbox", "seat", "nametag", "tag_name")
REQUIRED_LOOPS = ("idle", "walk")


def png(pixels):
    height, width = len(pixels), len(pixels[0])

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    raw = b"".join(b"\0" + bytes(c for p in row for c in p) for row in pixels)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


class Model:
    """Per-face pixel UVs on one atlas, bones as nested groups, keyframed animations."""

    def __init__(self, name, size=256):
        self.name, self.size = name, size
        self.serial = 0
        self.elements, self.animations = [], []
        self.bones = {}
        self.pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]
        self.uv_cache = {}
        self.pen_x = self.pen_y = 1
        self.row_height = 0

    def uid(self):
        self.serial += 1
        return str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/boss/{self.name}/{self.serial}"))

    def patch(self, w, h, paint, key):
        if key in self.uv_cache:
            return self.uv_cache[key]
        if self.pen_x + w + 1 > self.size:
            self.pen_x, self.pen_y, self.row_height = 1, self.pen_y + self.row_height + 1, 0
        if self.pen_y + h + 1 > self.size:
            raise ValueError(f"{self.name}: UV atlas full at {w}x{h}")
        x0, y0 = self.pen_x, self.pen_y
        for y in range(h):
            for x in range(w):
                self.pixels[y0 + y][x0 + x] = paint(x, y, w, h)
        self.uv_cache[key] = [x0, y0, x0 + w, y0 + h]
        self.pen_x += w + 1
        self.row_height = max(self.row_height, h)
        return self.uv_cache[key]

    def cube(self, name, start, end, bucket, painter, rotation=None, origin=None):
        """painter(side, w, h) -> (paint, cache_key). Faces of equal size and material share pixels."""
        ident = self.uid()
        dx, dy, dz = (b - a for a, b in zip(start, end))
        faces = {}
        for side, w, h in (("north", dx, dy), ("south", dx, dy), ("east", dz, dy),
                           ("west", dz, dy), ("up", dx, dz), ("down", dx, dz)):
            pw, ph = max(1, round(w)), max(1, round(h))
            paint, key = painter(side, pw, ph)
            faces[side] = {"uv": self.patch(pw, ph, paint, key), "texture": 0}
        element = {"name": name, "box_uv": False, "rescale": False, "locked": False,
                   "from": list(start), "to": list(end), "autouv": 0, "color": 0,
                   "origin": list(origin or start), "faces": faces, "type": "cube", "uuid": ident}
        if rotation:
            element["rotation"] = list(rotation)
        self.elements.append(element)
        bucket.append(ident)

    def bone(self, name, pivot, children, rotation=(0, 0, 0)):
        ident = self.uid()
        self.bones[name] = ident
        return {"name": name, "origin": list(pivot), "rotation": list(rotation), "color": 0,
                "uuid": ident, "export": True, "isOpen": True, "locked": False, "visibility": True,
                "autouv": 0, "children": children}

    def anim(self, name, length, tracks, loop="once"):
        """tracks: {bone: [(time, (x, y, z), channel?), ...]}; channel defaults to rotation."""
        animators = {}
        for bone, frames in tracks.items():
            keys = []
            for time, xyz, *channel in frames:
                keys.append({"channel": channel[0] if channel else "rotation",
                             "data_points": [{"x": xyz[0], "y": xyz[1], "z": xyz[2]}],
                             "uuid": self.uid(), "time": round(time, 4), "color": -1,
                             "interpolation": "linear"})
            animators[self.bones[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
        self.animations.append({"uuid": self.uid(), "name": name, "loop": loop, "override": False,
                                "length": round(length, 4), "snapping": 20, "selected": False,
                                "anim_time_update": "", "blend_weight": "", "start_delay": "",
                                "loop_delay": "", "animators": animators})

    def write(self, path, outliner):
        data = {
            "meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": False},
            "name": self.name, "model_identifier": self.name, "visible_box": [4, 4, 0],
            "variable_placeholders": "", "variable_placeholder_buttons": [],
            "timeline_setups": [], "unhandled_root_fields": {},
            "resolution": {"width": self.size, "height": self.size},
            "elements": self.elements, "outliner": outliner,
            "textures": [{"path": "", "name": f"{self.name}.png", "folder": "", "namespace": "",
                          "id": "0", "particle": False, "render_mode": "default", "render_sides": "auto",
                          "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
                          "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                          "uuid": self.uid(), "width": self.size, "height": self.size,
                          "uv_width": self.size, "uv_height": self.size,
                          "source": "data:image/png;base64," + base64.b64encode(png(self.pixels)).decode()}],
            "animations": self.animations,
        }
        validate_model(data)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        return data


def validate_model(data):
    """Returns (cubes, bones, animations). Raises ValueError for anything the server pipeline rejects."""
    meta = data.get("meta", {})
    if meta.get("model_format") != "bedrock":
        raise ValueError("model_format must be bedrock (WSEE)")
    # 4.x: what the scripts write (legacy Bedrock axes). 5.x: what Blockbench 5 saves; WSEE mirrors it back.
    if str(meta.get("format_version", "")).split(".")[0] not in ("4", "5"):
        raise ValueError("format_version must be 4.x or 5.x; see the pipeline doc")
    if not data.get("textures") or not all(t.get("source", "").startswith("data:image/png;base64,") for t in data["textures"]):
        raise ValueError("textures must be embedded PNGs")
    elements = {e["uuid"]: e for e in data["elements"]}
    if len(elements) != len(data["elements"]):
        raise ValueError("duplicate element UUID")
    bones, assigned, names = {}, set(), set()

    def walk(node):
        if node["uuid"] in bones:
            raise ValueError("duplicate bone UUID")
        name = node["name"]
        if name in names:
            raise ValueError(f"duplicate bone name: {name}")
        if name == "head" or any(word in name for word in RESERVED):
            raise ValueError(f"reserved WSEE bone name: {name}")
        names.add(name)
        bones[node["uuid"]] = name
        for child in node.get("children", []):
            if isinstance(child, dict):
                walk(child)
            else:
                if child not in elements or child in assigned:
                    raise ValueError(f"invalid or duplicate element reference: {child}")
                assigned.add(child)

    for root in data["outliner"]:
        if not isinstance(root, dict):
            raise ValueError("all elements must belong to a bone")
        walk(root)
    if assigned != set(elements):
        raise ValueError("unparented elements")
    animations = {}
    for animation in data.get("animations", []):
        if animation["name"] in animations:
            raise ValueError("duplicate animation name")
        animations[animation["name"]] = animation
        length = animation["length"]
        if not math.isfinite(length) or length <= 0:
            raise ValueError("invalid animation length")
        for key, track in animation.get("animators", {}).items():
            if track.get("type", "bone") == "bone" and (key not in bones or track.get("name") != bones[key]):
                raise ValueError(f"missing animation bone: {track.get('name', key)}")
            for frame in track.get("keyframes", []):
                if not 0 <= frame["time"] <= length + 1e-6:
                    raise ValueError("keyframe outside animation")
                if not all(isinstance(v, (int, float)) and math.isfinite(v) for v in frame["data_points"][0].values()):
                    raise ValueError("non-finite keyframe")
    for name in REQUIRED_LOOPS:
        if animations.get(name, {}).get("loop") != "loop":
            raise ValueError(f"missing looping animation: {name}")
    for element in elements.values():
        if not all(math.isfinite(v) for v in element["from"] + element["to"]):
            raise ValueError("non-finite geometry")
        if any(a > b for a, b in zip(element["from"], element["to"])):
            raise ValueError("inverted cube")
    return len(elements), len(bones), len(animations)


def validate_directory(path=SOURCE):
    files = sorted(path.glob("*.bbmodel"))
    if not files:
        raise ValueError(f"no bbmodels in {path}")
    for file in files:
        cubes, bones, animations = validate_model(json.loads(file.read_text(encoding="utf-8")))
        print(f"{file.name}: {cubes} cubes / {bones} bones / {animations} animations")


def preview(name, scale=4):
    """Rest-pose orthographic sheet (side / front / top) from the real cubes. Not a Minecraft render."""
    import io
    from PIL import Image, ImageDraw
    data = json.loads((SOURCE / f"{name}.bbmodel").read_text(encoding="utf-8"))
    atlas = Image.open(io.BytesIO(base64.b64decode(data["textures"][0]["source"].split(",", 1)[1]))).convert("RGBA")
    # (label, horizontal axis, vertical axis, face seen, flip horizontal, flip vertical)
    views = (("side: +Z right", 2, 1, "east", False, True), ("front", 0, 1, "south", True, True),
             ("top: +Z right", 2, 0, "up", False, False))
    elements = data["elements"]
    tiles = []
    for label, ax, ay, face, flip_x, flip_y in views:
        depth = ({0, 1, 2} - {ax, ay}).pop()
        xs = [v for e in elements for v in (e["from"][ax], e["to"][ax])]
        ys = [v for e in elements for v in (e["from"][ay], e["to"][ay])]
        width, height = int((max(xs) - min(xs)) * scale) + 20, int((max(ys) - min(ys)) * scale) + 40
        tile = Image.new("RGBA", (width, height), (34, 32, 40, 255))
        nearest_last = sorted(elements, key=lambda e: e["to"][depth] if face in ("east", "south", "up") else -e["from"][depth])
        for element in nearest_last:
            lo, hi = element["from"], element["to"]
            u = element["faces"][face]["uv"]
            texture = atlas.crop((int(u[0]), int(u[1]), max(int(u[0]) + 1, int(u[2])), max(int(u[1]) + 1, int(u[3]))))
            x0 = (max(xs) - hi[ax]) if flip_x else (lo[ax] - min(xs))
            y0 = (max(ys) - hi[ay]) if flip_y else (lo[ay] - min(ys))
            w = max(1, int((hi[ax] - lo[ax]) * scale)); h = max(1, int((hi[ay] - lo[ay]) * scale))
            tile.alpha_composite(texture.resize((w, h), Image.NEAREST), (10 + int(x0 * scale), 30 + int(y0 * scale)))
        ImageDraw.Draw(tile).text((6, 6), label, fill=(230, 220, 240, 255))
        tiles.append(tile)
    sheet = Image.new("RGBA", (sum(t.width for t in tiles), max(t.height for t in tiles) + 18), (24, 22, 30, 255))
    x = 0
    for tile in tiles:
        sheet.alpha_composite(tile, (x, 0)); x += tile.width
    ImageDraw.Draw(sheet).text((6, sheet.height - 15), f"{name}: CPU rest-pose preview, NOT a Minecraft screenshot", fill=(200, 180, 160, 255))
    out = ROOT / "build/boss-previews" / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "validate":
        validate_directory()
    elif len(sys.argv) == 3 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)
