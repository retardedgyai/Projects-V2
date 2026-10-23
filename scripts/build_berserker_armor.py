"""狂戦士の甲冑 (warrior ultimate, Creator 2026-10-23): the armor the warrior turns into for ten seconds.

The helm and the mantle (shoulders, skirt, cape: for a display that follows the body) are Blockbench models,
assets/armor/berserker_{helmet,mantle}.bbmodel (first drafts from scripts/seed_berserker_bbmodel.py). This packs
them into item models: each cube keeps its rotation on all three axes ({"x", "y", "z"}, which 26.3 reads at any
angle), its texture is copied out of the .bbmodel and its UVs are scaled to 0..16. The body and leg layers are
64x32 equipment textures painted here: a near-black body with plates and a crimson tabard (black and red).

Run: python3 scripts/build_berserker_armor.py
"""
from pathlib import Path
import base64
import io
import json
import math

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
SOURCE = ROOT / "assets/armor"
NAME = "berserker"


def ramp(*hexes):
    return [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes]


# Six steps, darkest first.
MATERIALS = {
    "iron": ramp("#100e12", "#211e26", "#36323d", "#524d5c", "#7f798b", "#b9b3c4"),
    "dark": ramp("#050406", "#0b0a0d", "#121015", "#1a171e", "#242029", "#2f2a35"),
    "cloth": ramp("#240506", "#430a0b", "#6e1011", "#981a17", "#c22a21", "#e0503a"),
    "eye": ramp("#6e0e07", "#a8180c", "#e8261a", "#ff4a2c", "#ff8a52", "#ffd6a8"),
}
# How lit each face is (0..5): tops catch the light, fronts are mid, sides dark.
FACE_LIGHT = {"up": 4, "north": 3, "west": 2, "east": 2, "south": 1, "down": 0}


def paint_face(img, x0, y0, w, h, mat, level, flat=False):
    """One flat value per face, a lighter top edge, a darker bottom edge: the planes do the shading."""
    tones = MATERIALS[mat]
    px = img.load()
    for y in range(h):
        for x in range(w):
            v = level if flat else level + (2 if y == 0 and level >= 3 else 1 if y == 0 else -1 if y == h - 1 else 0)
            if mat == "eye":
                v = 3 + 2 * (1 - abs(2 * (x + .5) / w - 1))
            px[x0 + x, y0 + y] = tones[max(0, min(5, round(v)))] + (255,)



# ------------------------------------------------------------------------------------- body and leg layers

# A letter is a material and a step: K D P M L H iron 0..5, k dark, C c X cloth 2/1/4, '.' empty.
KEY = {"K": ("iron", 0), "D": ("iron", 1), "P": ("iron", 2), "M": ("iron", 3), "L": ("iron", 4), "H": ("iron", 5),
       "k": ("dark", 2), "C": ("cloth", 2), "c": ("cloth", 1), "X": ("cloth", 4)}

# A near-black body; a gorget and a breastplate on top, a crimson tabard down the middle under a plated belt.
CHEST = ("DLHHHHLD", "kMLLLLMk", "kPMMMMPk", "kDPCXPDk", "kkDCCDkk", "kKkCCkKk",
         "kKkCCkKk", "DLDCCDLD", "kkcCXckk", "kkcCCckk", "kkcCCckk", "kkkcckkk")
BACK = ("DLLLLLLD", "kMMMMMMk", "kPPPPPPk", "kDDDDDDk", "kkkkkkkk", "kKkkkkKk",
        "kKkkkkKk", "DLDDDDLD", "kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "kkkkkkkk")
TORSO_SIDE = ("DLLD", "kMMk", "kPPk", "kDDk", "kkkk", "kKKk", "kKKk", "DLLD", "kkkk", "kkkk", "kkkk", "kkkk")
# Arms: dark mail to the elbow (the pauldron covers the top), a heavy gauntlet below.
ARM = ("kkkk", "kKKk", "kkkk", "kKKk", "DLLD", "kPPk", "kkkk", "MLLM", "LHHL", "MLLM", "PMMP", "DkDk")
ARM_BACK = ("kkkk", "kKKk", "kkkk", "kKKk", "DMMD", "kPPk", "kkkk", "PMMP", "MLLM", "PMMP", "DPPD", "DkDk")
BOOT = ("....",) * 6 + ("MLLM", "PMMP", "DPPD", "kDDk", "PMMP", "kDDk")
BOOT_BACK = ("....",) * 6 + ("PMMP", "DPPD", "DPPD", "kDDk", "DPPD", "kDDk")
LEG = ("kkkk", "kKKk", "kkkk", "kKKk", "DMMD", "MLLM", "PMMP", "kPPk", "kkkk", "kkkk", "kkkk", "kkkk")
LEG_BACK = ("kkkk", "kKKk", "kkkk", "kKKk", "kkkk", "DPPD", "kDDk", "kkkk", "kkkk", "kkkk", "kkkk", "kkkk")
WAIST = ("kkkkkkkk",) * 9 + ("DLDCCDLD", "kkcCCckk", "kkkcckkk")


def top(w, d, row):
    return tuple(row * w for _ in range(d))


def unwrap(img, origin, w, h, d, front, back, side, up, down):
    """Vanilla cube layout: +X, -X / front / back on the second band, top and bottom above."""
    u, v = origin
    px = img.load()

    def put(x0, y0, rows):
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c == ".": continue
                mat, step = KEY[c]
                px[x0 + x, y0 + y] = MATERIALS[mat][step] + (255,)

    put(u, v + d, side); put(u + d + w, v + d, side)
    put(u + d, v + d, front); put(u + 2 * d + w, v + d, back)
    put(u + d, v, up); put(u + d + w, v, down)


def body_layers():
    outer = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    unwrap(outer, (16, 16), 8, 12, 4, CHEST, BACK, TORSO_SIDE, ("DLLLLLLD", "LHHHHHHL", "LHHHHHHL", "DLLLLLLD"), top(8, 4, "k"))
    unwrap(outer, (40, 16), 4, 12, 4, ARM, ARM_BACK, ARM_BACK, ("kKKk",) * 4, ("DkkD",) * 4)
    unwrap(outer, (0, 16), 4, 12, 4, BOOT, BOOT_BACK, BOOT_BACK, top(4, 4, "."), ("kkkk",) * 4)
    inner = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    unwrap(inner, (16, 16), 8, 12, 4, WAIST, WAIST, tuple(r[:4] for r in WAIST), top(8, 4, "k"), top(8, 4, "k"))
    unwrap(inner, (0, 16), 4, 12, 4, LEG, LEG_BACK, LEG_BACK, ("kkkk",) * 4, ("kkkk",) * 4)
    return outer, inner


# ------------------------------------------------------------------------------------------------- write

def write(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")


def save(img, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


DISPLAY = {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
           "gui": {"rotation": [15, -25, 0], "translation": [0, -1, 0], "scale": [0.65, 0.65, 0.65]},
           "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
           "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.65, 0.65, 0.65]}}


LIGHT = (-.4, .75, -.55)     # baked light: from above, front and left (the game's own face shading is off)
DENSITY = 6                  # texture pixels per model unit for mesh triangles


def _norm(v):
    length = math.sqrt(sum(c * c for c in v))
    return [c / length for c in v]


def _sub(a, b): return [a[i] - b[i] for i in range(3)]
def _add(a, b): return [a[i] + b[i] for i in range(3)]
def _mul(a, k): return [c * k for c in a]
def _dot(a, b): return sum(a[i] * b[i] for i in range(3))
def _cross(a, b): return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _rotation(x, y, z):
    """Rz·Ry·Rx (degrees), right-handed, as the game and Blockbench apply them."""
    x, y, z = (math.radians(a) for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return [[cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
            [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
            [-sy, cy * sx, cy * cx]]


def _euler(m):
    """Angles (x, y, z) in degrees with m = Rz·Ry·Rx."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(math.cos(y)) > 1e-6:
        x, z = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:
        x, z = math.atan2(-m[1][2], m[1][1]), 0.0
    return [round(math.degrees(a), 4) for a in (x, y, z)]


def _swatch(tex_img):
    """A material's colour: the mean of the opaque pixels of its Blockbench texture."""
    px = [p for p in tex_img.getdata() if p[3] > 0] or [(128, 128, 128, 255)]
    return [sum(p[i] for p in px) / len(px) for i in range(3)]


def mesh_triangles(c, swatches):
    """A Blockbench mesh as world-space triangles (A, B, C, colour), wound so (B-A)x(C-A) faces out."""
    rot, origin = _rotation(*c.get("rotation", [0, 0, 0])), c.get("origin", [0, 0, 0])
    world = {k: _add([sum(rot[i][j] * v[j] for j in range(3)) for i in range(3)], origin) for k, v in c["vertices"].items()}
    tris = []
    for f in c["faces"].values():
        if f.get("texture") is None:
            continue
        pts = [world[k] for k in f["vertices"]]
        colour = swatches[int(f["texture"])]
        if len(pts) == 4:
            # Blockbench keeps a quad's corners unordered after the first three: order them round the centroid.
            n = _cross(_sub(pts[1], pts[0]), _sub(pts[2], pts[0]))
            mid = _mul(_add(_add(pts[0], pts[1]), _add(pts[2], pts[3])), .25)
            ref = _sub(pts[0], mid)
            side = _cross(n, ref)
            pts.sort(key=lambda p: math.atan2(_dot(_sub(p, mid), side), _dot(_sub(p, mid), ref)))
            tris += [(pts[0], pts[1], pts[2], colour), (pts[0], pts[2], pts[3], colour)]
        elif len(pts) == 3:
            tris.append((pts[0], pts[1], pts[2], colour))
    return tris


def triangle_element(a, b, c, colour, atlas, alloc):
    """One triangle as a flat element: a rectangle lying in the triangle's plane (its one face looking out), and
    the triangle painted on its texture with the outside transparent, so the game draws only the triangle."""
    u = _norm(_sub(b, a)); n = _norm(_cross(_sub(b, a), _sub(c, a))); v = _cross(u, n)
    st = [(_dot(_sub(p, a), u), _dot(_sub(p, a), v)) for p in (a, b, c)]
    pad = 1.5 / DENSITY
    s0, s1 = min(p[0] for p in st) - pad, max(p[0] for p in st) + pad
    t0, t1 = min(p[1] for p in st) - pad, max(p[1] for p in st) + pad
    w, h = s1 - s0, t1 - t0
    centre = _add(a, _add(_mul(u, (s0 + s1) / 2), _mul(v, (t0 + t1) / 2)))
    pw, ph = max(1, math.ceil(w * DENSITY)), max(1, math.ceil(h * DENSITY))
    x0, y0 = alloc(pw, ph)
    lit = .25 + .95 * max(0.0, _dot(n, _norm(LIGHT))) ** 1.3
    rgb = tuple(max(0, min(255, round(ch * lit))) for ch in colour) + (255,)
    px = atlas.load()
    (sa, ta), (sb, tb), (sc, tc) = st
    det = (tb - tc) * (sa - sc) + (sc - sb) * (ta - tc)
    grow = 1.3 / DENSITY  # a little past the edges so neighbouring triangles leave no crack
    for j in range(ph):
        for i in range(pw):
            # The north face runs its texture from +x (left) to -x and from +y (top) down.
            s = (s0 + s1) / 2 + w / 2 - (i + .5) * w / pw
            t = (t0 + t1) / 2 + h / 2 - (j + .5) * h / ph
            l1 = ((tb - tc) * (s - sc) + (sc - sb) * (t - tc)) / det
            l2 = ((tc - ta) * (s - sc) + (sa - sc) * (t - tc)) / det
            l3 = 1 - l1 - l2
            edge = min(l1, l2, l3)
            if edge >= 0 or _edge_distance((s, t), st) <= grow:
                px[x0 + i, y0 + j] = rgb
    m = [[u[i], v[i], -n[i]] for i in range(3)]
    return {"from": [centre[0] - w / 2, centre[1] - h / 2, centre[2]], "to": [centre[0] + w / 2, centre[1] + h / 2, centre[2]],
            "rotation": dict(zip(("x", "y", "z"), _euler(m)), origin=[round(x, 4) for x in centre]),
            "shade": False, "faces": {"north": {"uv": [x0, y0, x0 + pw, y0 + ph], "texture": "#atlas"}}}


def _edge_distance(p, tri):
    best = 1e9
    for k in range(3):
        (ax, ay), (bx, by) = tri[k], tri[(k + 1) % 3]
        dx, dy = bx - ax, by - ay
        f = max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy or 1)))
        best = min(best, math.hypot(p[0] - ax - f * dx, p[1] - ay - f * dy))
    return best


def from_bbmodel(path: Path):
    """A Blockbench model as item model elements and one texture. Cubes keep their own UVs on texture 0; mesh
    faces become flat triangles (triangle_element) packed below it, lit by their real facing."""
    bb = json.loads(path.read_text(encoding="utf-8"))
    images = [Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1])))
              .convert("RGBA") for t in bb["textures"]]
    tex = bb["textures"][0]; base = images[0]
    uw, uh = tex.get("uv_width", base.width), tex.get("uv_height", base.height)
    swatches = [_swatch(im) for im in images]
    visible = [c for c in bb["elements"] if c.get("visibility") is not False]
    tris = [t for c in visible if c.get("type") == "mesh" for t in mesh_triangles(c, swatches)]
    cubes = [c for c in visible if c.get("type", "cube") == "cube"]
    width = max(512, base.width if cubes else 0)
    top = base.height if cubes else 0
    shelf = {"x": 0, "y": top, "row": 0, "rects": []}

    def alloc(w, h):
        if shelf["x"] + w > width:
            shelf["x"], shelf["y"], shelf["row"] = 0, shelf["y"] + shelf["row"], 0
        at = (shelf["x"], shelf["y"]); shelf["x"] += w; shelf["row"] = max(shelf["row"], h)
        return at
    scratch = Image.new("RGBA", (width, top + 8192), (0, 0, 0, 0))
    if cubes:
        scratch.paste(base, (0, 0))
    meshes = [triangle_element(a, b, c, col, scratch, alloc) for a, b, c, col in tris]
    height = shelf["y"] + shelf["row"]
    height = max(16, -(-height // 16) * 16)
    atlas = scratch.crop((0, 0, width, height))
    sx, sy = 16 / width, 16 / height
    elements = []
    for c in cubes:
        e = {"name": c["name"], "from": c["from"], "to": c["to"],
             "faces": {f: {"uv": [v["uv"][0] * base.width / uw * sx, v["uv"][1] * base.height / uh * sy,
                                  v["uv"][2] * base.width / uw * sx, v["uv"][3] * base.height / uh * sy],
                           "texture": "#atlas", **({"rotation": v["rotation"]} if v.get("rotation") else {})}
                       for f, v in c["faces"].items() if v.get("texture") is not None}}
        if c.get("shade") is False:
            e["shade"] = False
        r = c.get("rotation", [0, 0, 0])
        if any(r):
            e["rotation"] = {"origin": c["origin"], "x": r[0], "y": r[1], "z": r[2]}
        elements.append(e)
    for e in meshes:
        e["faces"]["north"]["uv"] = [round(q * (sx if k % 2 == 0 else sy), 5) for k, q in enumerate(e["faces"]["north"]["uv"])]
        e["from"] = [round(x, 4) for x in e["from"]]; e["to"] = [round(x, 4) for x in e["to"]]
        elements.append(e)
    return elements, atlas


def main():
    eq = ASSETS / "textures/entity/equipment"
    outer, inner = body_layers()
    save(outer, eq / f"humanoid/armor/{NAME}.png")
    save(inner, eq / f"humanoid_leggings/armor/{NAME}.png")
    write(ASSETS / f"equipment/armor/{NAME}.json",
          {"layers": {"humanoid": [{"texture": f"projects:armor/{NAME}"}], "humanoid_leggings": [{"texture": f"projects:armor/{NAME}"}]}})
    for part, display in (("helmet", DISPLAY), ("mantle", {})):
        boxes, atlas = from_bbmodel(SOURCE / f"{NAME}_{part}.bbmodel")
        save(atlas, ASSETS / f"textures/item/armor/{NAME}_{part}.png")
        write(ASSETS / f"models/item/armor/{NAME}_{part}.json", {
            "credit": f"Project S: berserker {part} (scripts/build_berserker_armor.py)",
            "gui_light": "front", "ambientocclusion": False,
            "textures": {"atlas": f"projects:item/armor/{NAME}_{part}", "particle": f"projects:item/armor/{NAME}_{part}"},
            "elements": boxes, **({"display": display} if display else {})})
        write(ASSETS / f"items/armor/{NAME}_{part}.json", {"model": {"type": "minecraft:model", "model": f"projects:item/armor/{NAME}_{part}"}})
    (ASSETS / f"textures/item/armor/{NAME}_materials.png").unlink(missing_ok=True)


if __name__ == "__main__":
    main()
