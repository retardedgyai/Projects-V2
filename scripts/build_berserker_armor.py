"""狂戦士の甲冑 (warrior ultimate, Creator 2026-10-23): the armor the warrior turns into for ten seconds.

Built on its own. The Creator pointed at the Knight Artorias boss of a Minecraft mod (~/ProjectS/refs/artorias) and
asked for black and red. What was read off it (rules, nothing traced):
- the helm is tall and faceted: a pointed brow-visor jutting forward and down over a dark eye slit, the face
  narrowing to a V, blades standing on the crown.
- shading is the planes, not paint: every face is one flat value (lit tops, mid fronts, dark sides) with a single
  lighter top edge. No speckle, no long gradients.
- one strong cloth colour over the metal (theirs blue, ours crimson): a hood round the neck, a cape, torn strips
  that trail. Big chunky plates on the shoulders, stacked.
- the body under the plates is near-black, so the plates and cloth read against it.

Vanilla armor can only paint the body boxes; only the helm can be a real model. The shoulders and the mantle are
built here as their own model ("berserker_mantle", in body space) for a display that follows the body; the
helm, the body/leg layers and the equipment asset are what the armor swap uses today.

Run: python3 scripts/build_berserker_armor.py
"""
from pathlib import Path
import json
import math

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
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


def el(name, lo, hi, mat, rot=None, light=0):
    """One box; rot = (axis, angle, origin). light nudges the whole box brighter or darker."""
    e = {"name": name, "from": list(lo), "to": list(hi), "mat": mat, "light": light}
    if rot:
        e["rotation"] = {"origin": list(rot[2]), "axis": rot[0], "angle": rot[1], "rescale": False}
    return e


# ----------------------------------------------------------------------------------------------- the helm

def helm():
    """Front is north (low z). The head is x,y,z 4..12. Points are built as narrowing steps (each step's top or
    front catches the light) rather than turned plates: the facets read as planes at any angle."""
    e = []
    e += [el("skull", (3.6, 4.2, 3.6), (12.4, 11.2, 12.4), "iron", light=-1)]
    # A stepped dome: each layer narrower, its top lit.
    for i, (inset, y0, y1) in enumerate(((0.0, 11.0, 12.4), (0.9, 12.4, 13.4), (2.0, 13.4, 14.2))):
        e += [el("dome", (3.6 + inset, y0, 3.8 + inset * .6), (12.4 - inset, y1, 12.4 - inset * .3), "iron", light=-i % 2)]
    # The fin along the crown, stepping back as it rises.
    for i, (y0, z0, z1) in enumerate(((14.2, 4.0, 11.6), (15.2, 6.0, 12.4), (16.2, 8.2, 13.0))):
        e += [el("crest", (7.5 + .1 * i, y0, z0), (8.5 - .1 * i, y0 + 1.0, z1), "iron", light=1)]
    # Sides as overlapping bands, each lower one standing out further.
    for s in (-1, 1):
        for i, (out, y0, y1, z0) in enumerate(((0.4, 9.6, 11.2, 4.0), (0.8, 7.4, 9.6, 4.4), (0.5, 4.8, 7.4, 5.0))):
            x0, x1 = (3.6 - out, 4.2) if s < 0 else (11.8, 12.4 + out)
            e += [el("side band", (x0, y0, z0), (x1, y1, 12.6), "iron", light=1 - i)]
    # The brow-visor jutting forward to a point in steps over the slit.
    for i, (x0, z0) in enumerate(((3.6, 2.6), (4.8, 1.6), (6.2, 0.6), (7.3, -0.4))):
        e += [el("brow", (x0, 9.0 + .15 * i, z0), (16 - x0, 10.8, z0 + 1.2), "iron", light=1)]
    # The slit under it, dark, the red eyes back in it.
    e += [el("eye slit", (4.4, 7.2, 3.0), (11.6, 9.0, 3.8), "dark")]
    for s in (-1, 1):
        x = 8 + s * 2.0
        e += [el("eye", (x - .8, 7.6, 2.9), (x + .8, 8.2, 3.1), "eye")]
    # The face narrows down to a point at the chin, in steps.
    for i, (x0, y0, y1) in enumerate(((4.2, 4.0, 7.2), (5.4, 3.2, 4.0), (6.8, 2.6, 3.2))):
        e += [el("face", (x0, y0, 2.4 + .1 * i), (16 - x0, y1, 3.6), "iron", light=0 if i == 0 else 1)]
    e += [el("face ridge", (7.4, 4.0, 2.0), (8.6, 7.2, 2.6), "iron", light=1)]
    # Torn crimson strips hanging from the back of the crown, flaring out.
    for i, (x, length) in enumerate(((5.8, 7.0), (7.4, 9.0), (9.0, 6.0))):
        e += [el("helm strip", (x, 12.0 - length, 12.8), (x + 1.3, 12.0, 13.5), "cloth", ("x", -22.5, (8.0, 12.0, 13.0)),
                 light=i % 2)]
    return e


# --------------------------------------------------------------- shoulders and mantle (body space, for a display)

def mantle():
    """Body space of the preview: body x4..12, y12..24, z6..10, arms x0..4 / x12..16, front is low z."""
    e = []
    # Stacked pauldrons, three plates each, wider at the top; the left one (+x) bigger, Guts's iron arm side.
    for s, big in ((-1, 0.0), (1, 0.6)):
        cx = 8 + s * 6.0
        for i in range(3):
            w = 3.6 + big - .5 * i
            y1 = 25.4 + big * .5 - 2.2 * i
            e += [el("pauldron", (cx - w, y1 - 2.6, 5.0 - .2 * i), (cx + w, y1, 11.0 + .2 * i), "iron",
                     ("z", -s * 22.5, (cx, y1 - 1.3, 8.0)), light=1 - i)]
        e += [el("pauldron spike", (cx - .4, 25.4 + big * .5, 6.6), (cx + .4, 27.8 + big, 9.4), "iron",
                 ("z", -s * 22.5, (cx, 25.6, 8.0)), light=1)]
    # The hood: crimson cloth piled round the neck, heavier at the back.
    e += [el("hood front", (4.6, 22.6, 4.8), (11.4, 24.8, 6.4), "cloth", ("x", -22.5, (8.0, 23.6, 5.6)), light=1),
          el("hood back", (3.8, 21.4, 9.6), (12.2, 26.0, 12.0), "cloth"),
          el("hood side", (3.4, 22.4, 5.6), (4.8, 25.0, 10.4), "cloth", light=-1),
          el("hood side", (11.2, 22.4, 5.6), (12.6, 25.0, 10.4), "cloth", light=-1)]
    # A sash over the left shoulder, one band crossing the chest to the right hip (and the back).
    e += [el("sash", (-0.2, 17.4, 4.5), (16.2, 19.8, 5.3), "cloth", ("z", 45, (8.0, 18.6, 4.9)), light=1),
          el("sash back", (-0.2, 17.4, 10.3), (16.2, 19.8, 11.1), "cloth", ("z", 45, (8.0, 18.6, 10.7)), light=-1)]
    # The cape to the knee, its hem torn into strips of different lengths.
    e += [el("cape", (3.4, 9.0, 10.6), (12.6, 22.0, 11.4), "cloth")]
    for i, (x, bottom) in enumerate(((3.4, 4.0), (5.2, 6.4), (7.0, 2.8), (8.8, 5.6), (10.6, 3.6))):
        e += [el("cape strip", (x, bottom, 10.7), (x + 2.0, 9.2, 11.3), "cloth", light=-(i % 2))]
    # A torn scarf trailing back from the right shoulder.
    for i in range(3):
        e += [el("scarf", (2.4 + .6 * i, 23.0 - .8 * i, 11.0), (3.4 + .6 * i, 24.0 - .8 * i, 17.0 - 1.4 * i), "cloth",
                 ("x", 22.5, (3.0, 23.5, 11.0)), light=1 - i % 2)]
    return e


def face_size(e, face):
    dx, dy, dz = (e["to"][i] - e["from"][i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def paint_face(img, x0, y0, w, h, mat, level):
    """One flat value per face, a lighter top edge, a darker bottom edge: the planes do the shading."""
    tones = MATERIALS[mat]
    px = img.load()
    for y in range(h):
        for x in range(w):
            v = level + (2 if y == 0 and level >= 3 else 1 if y == 0 else -1 if y == h - 1 else 0)
            if mat == "eye":
                v = 3 + 2 * (1 - abs(2 * (x + .5) / w - 1))
            px[x0 + x, y0 + y] = tones[max(0, min(5, round(v)))] + (255,)


def model(elements, size=256, density=3):
    """Pack every face into its own painted rectangle on one atlas (3 px per model unit)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cx, cy, row = 0, 0, 0
    out = []
    for e in elements:
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            fw, fh = face_size(e, face)
            w, h = max(2, math.ceil(fw * density)), max(2, math.ceil(fh * density))
            if cx + w > size: cx, cy, row = 0, cy + row, 0
            assert cy + h <= size, "berserker atlas is full"
            paint_face(img, cx, cy, w, h, e["mat"], FACE_LIGHT[face] + e["light"])
            faces[face] = {"uv": [cx * 16 / size, cy * 16 / size, (cx + w) * 16 / size, (cy + h) * 16 / size], "texture": "#atlas"}
            cx += w; row = max(row, h)
        box = {k: v for k, v in e.items() if k not in ("mat", "light")}
        box["faces"] = faces
        out.append(box)
    return img, out


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


def main():
    eq = ASSETS / "textures/entity/equipment"
    outer, inner = body_layers()
    save(outer, eq / f"humanoid/armor/{NAME}.png")
    save(inner, eq / f"humanoid_leggings/armor/{NAME}.png")
    write(ASSETS / f"equipment/armor/{NAME}.json",
          {"layers": {"humanoid": [{"texture": f"projects:armor/{NAME}"}], "humanoid_leggings": [{"texture": f"projects:armor/{NAME}"}]}})
    for part, elements, display in (("helmet", helm(), DISPLAY), ("mantle", mantle(), {})):
        atlas, boxes = model(elements)
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
