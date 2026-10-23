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
    front catches the light) rather than turned plates. Model rotations: -x tips a part's back end up.

    Creator 2026-10-23: it must read as armor (甲冑), not a monster mask, so the menace is in the forged shape:
    a visor that comes forward as a prow of upright plates to a keel, one sight slit across it, pivots and rivets, a bevor and gorget lames, a low comb, horns swept back (the right one broken) and torn crimson
    hanging behind. The red behind the slit is only a glint."""
    e = []
    # The skull: a stepped dome, a low comb along the top, a rivet band round it.
    # Rounded in plan: each layer is two crossed boxes, so its corners are cut.
    def round_box(name, lo, hi, cut, light):
        return [el(name, (lo[0] + cut, lo[1], lo[2]), (hi[0] - cut, hi[1], hi[2]), "iron", light=light),
                el(name, (lo[0], lo[1], lo[2] + cut), (hi[0], hi[1], hi[2] - cut), "iron", light=light)]
    e += round_box("skull", (3.2, 4.2, 3.4), (12.8, 11.2, 12.8), .6, -1)
    for i, (inset, y0, y1) in enumerate(((0.0, 11.0, 12.3), (1.1, 12.3, 13.3), (2.2, 13.3, 14.1))):
        e += round_box("dome", (3.2 + inset, y0, 3.4 + inset), (12.8 - inset, y1, 12.8 - inset), .8, -i % 2)
    e += [el("comb", (7.5, 13.6, 4.6), (8.5, 14.8, 11.6), "iron", light=1),
          el("brim band", (3.4, 10.2, 3.6), (12.6, 11.0, 12.6), "iron")]
    for z in (5.4, 7.6, 9.8, 12.0):
        for x0 in (3.2, 12.6):
            e += [el("rivet", (x0, 10.4, z - .2), (x0 + .2, 10.8, z + .2), "iron", light=2)]
    # The visor: a prow. Each step keeps the full height and only narrows as it comes forward, so from the front
    # it reads as nested upright plates meeting at a keel, not as a face. One sight slit runs straight across.
    for i, (x0, z0) in enumerate(((3.6, 2.4), (4.9, 1.3), (6.1, 0.2), (7.1, -0.9))):
        y0, y1 = 4.0 + .3 * i, 10.4 - .2 * i
        e += [el("visor", (x0, y0, z0), (16 - x0, y1, z0 + 1.2), "iron", light=1 if i % 2 == 0 else 0),
              el("sight", (x0 + .25, 8.1, z0 - .06), (16 - x0 - .25, 8.6, z0 + .1), "dark")]
    e += [el("keel", (7.6, 4.6, -1.3), (8.4, 10.4, 2.6), "iron", light=2),
          el("visor rim", (3.4, 4.0, 2.2), (12.6, 4.6, 3.8), "iron", light=1)]
    for s in (-1, 1):
        x = 8 + s * 2.2
        e += [el("glint", (x - .5, 8.2, 1.18), (x + .5, 8.5, 1.26), "eye")]
    # The pivots the visor hangs on, one each side.
    for s in (-1, 1):
        x0, x1 = (3.0, 3.8) if s < 0 else (12.2, 13.0)
        e += [el("pivot", (x0 - .2 * (s < 0) + .0, 7.6, 6.0), (x1, 9.4, 7.8), "iron", light=1),
              el("pivot rivet", (x0 - .2 * (s < 0), 8.2, 6.6), (x1 + .2 * (s > 0), 8.8, 7.2), "iron", light=2)]
    # The bevor under the visor and the gorget lames stepping out round the neck.
    e += [el("bevor", (4.2, 3.0, 2.8), (11.8, 4.8, 9.0), "iron")]
    for i, (out, y0, y1) in enumerate(((0.0, 2.4, 3.4), (.4, 1.6, 2.6))):
        e += [el("gorget", (3.8 - out, y0, 4.2 - out), (12.2 + out, y1, 12.4 + out), "iron", light=1 - i)]
    # The plume: one mass of crimson streaming back off the crown and falling, thick strips lying on each other,
    # their torn ends at different lengths.
    for i, (x0, x1, y0, length) in enumerate(((6.0, 8.4, 12.6, 11.0), (7.6, 10.0, 12.4, 13.5), (6.8, 9.2, 13.6, 9.0), (7.4, 8.8, 11.8, 15.5))):
        e += [el("plume", (x0, y0, 9.4), (x1, y0 + 2.0, 9.4 + length), "cloth", ("x", 33.75, (8.0, 13.6, 9.4)), light=1 - i % 2)]
    # Torn crimson hanging from the back of the crown.
    for i, (x, length) in enumerate(((5.6, 7.0), (7.0, 10.0), (8.6, 5.0), (10.0, 8.0))):
        e += [el("helm strip", (x, 12.0 - length, 12.6), (x + 1.1, 12.0, 13.3), "cloth", ("x", -22.5, (8.0, 12.0, 13.0)),
                 light=i % 2)]
    return e


# --------------------------------------------------------------- shoulders and mantle (body space, for a display)

def mantle():
    """Body space of the preview: body x4..12, y12..24, z6..10, arms x0..4 / x12..16, front is low z. Model
    rotations: +x swings a part's lower end forward, -z swings its lower end out to -x.

    Creator 2026-10-23: the silhouette was too far from Artorias. His reads as an inverted triangle (pauldrons
    stacked and falling away outward, narrow waist), a skirt of cloth flaring from the belt to the knee (half his
    outline), cloth over the shoulders, a small tall helm with a plume streaming back."""
    e = []
    # Pauldrons: a round cop over the shoulder and three lames below it, each further out, the whole falling away
    # from the neck. The left one (+x) bigger, Guts's iron arm side.
    for s, big in ((-1, 0.0), (1, 0.5)):
        cx = 8 + s * 6.2
        fall = ("z", -s * 22.5, (cx, 24.6, 8.0))
        e += [el("pauldron cop", (cx - 3.0 - big, 23.4, 4.2 - big * .4), (cx + 3.0 + big, 25.8 + big * .4, 11.8 + big * .4), "iron", fall, light=1),
              el("pauldron cop", (cx - 2.2 - big, 25.6 + big * .4, 5.0), (cx + 2.2 + big, 26.6 + big * .6, 11.0), "iron", fall, light=1),
              el("pauldron ridge", (cx - .4, 26.4 + big * .6, 5.4), (cx + .4, 27.4 + big * .8, 10.6), "iron", fall, light=2)]
        for i in range(3):
            y1 = 23.6 - 1.3 * i
            out = s * .45 * (i + 1)
            e += [el("pauldron lame", (cx - 2.8 - big + out, y1 - 1.6, 4.4 + .2 * i), (cx + 2.8 + big + out, y1, 11.6 - .2 * i), "iron", fall,
                     light=0 if i % 2 else -1)]
    # The belt and the skirt: crimson flaps flaring from the belt to the knee, metal tassets over the front, torn hem.
    e += [el("belt", (3.0, 11.8, 4.6), (13.0, 13.0, 11.4), "iron", light=1)]
    front, back = ("x", 11.25, (8.0, 12.6, 4.7)), ("x", -11.25, (8.0, 12.6, 11.3))
    e += [el("skirt front", (2.8, 4.2, 4.4), (13.2, 12.6, 5.0), "cloth", front, light=1),
          el("skirt back", (2.8, 3.8, 11.0), (13.2, 12.6, 11.6), "cloth", back)]
    for s in (-1, 1):
        x0, x1 = (2.2, 3.0) if s < 0 else (13.0, 13.8)
        side = ("z", s * 22.5, ((x0 + x1) / 2, 12.6, 8.0))
        e += [el("skirt side", (x0, 4.6, 4.8), (x1, 12.6, 11.2), "cloth", side, light=-1)]
        for k, bottom in enumerate((2.6, 3.8, 2.0)):
            e += [el("hem", (x0, bottom, 5.0 + 2.0 * k), (x1, 4.8, 6.9 + 2.0 * k), "cloth", side, light=-1 - k % 2)]
        e += [el("tasset", (8 + s * 3.0 - 2.0, 9.4, 4.0), (8 + s * 3.0 + 2.0, 12.6, 4.5), "iron", front, light=1)]
    for i, (x, bottom) in enumerate(((2.8, 2.0), (4.6, 3.4), (6.2, 1.2), (7.8, 2.8), (9.4, 1.6), (11.2, 3.0))):
        e += [el("hem", (x, bottom, 4.45), (x + 1.8, 4.4, 4.95), "cloth", front, light=i % 2)]
        e += [el("hem", (x, bottom - .6, 11.05), (x + 1.8, 4.0, 11.55), "cloth", back, light=-(i % 2))]
    # Cloth over the shoulders and down the back, under the pauldrons.
    e += [el("hood front", (4.6, 22.6, 4.8), (11.4, 24.8, 6.4), "cloth", ("x", -22.5, (8.0, 23.6, 5.6)), light=1),
          el("hood back", (3.4, 20.6, 9.6), (12.6, 25.4, 12.0), "cloth"),
          el("shoulder drape", (11.6, 18.0, 4.8), (14.0, 24.6, 11.6), "cloth", ("z", -22.5, (12.8, 24.0, 8.0)), light=-1)]
    e += [el("cape", (3.6, 9.0, 11.6), (12.4, 21.0, 12.4), "cloth")]
    for i, (x, bottom) in enumerate(((3.6, 3.0), (4.8, 6.8), (5.8, 1.2), (7.0, 5.0), (8.0, 7.6), (9.0, 2.2), (10.2, 6.0), (11.2, 4.0))):
        e += [el("cape strip", (x, bottom, 11.7 + .1 * (i % 2)), (x + 1.2, 9.2, 12.3 + .1 * (i % 2)), "cloth", light=-(i % 2))]
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


def model(elements, size=512, density=3):
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
