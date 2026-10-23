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

    Creator 2026-10-23: the clean, symmetric helm with a horizontal eye band and a centre antenna read as a Kamen
    Rider. So: nothing stands up at the front; a scowling brow pulled down to the middle, horns from it swept
    back; small slanted eyes deep in the dark; a maw of fangs below; a ragged mane of spikes at the back, one broken."""
    e = []
    e += [el("skull", (3.6, 4.2, 3.6), (12.4, 11.2, 12.4), "iron", light=-1)]
    for i, (inset, y0, y1) in enumerate(((0.0, 11.0, 12.2), (1.0, 12.2, 13.0))):
        e += [el("dome", (3.6 + inset, y0, 3.8 + inset * .6), (12.4 - inset, y1, 12.4 - inset * .3), "iron", light=-i % 2)]
    # The scowl: each half of the brow steps down toward the middle, and juts forward.
    for s in (-1, 1):
        for i in range(3):
            x0, x1 = (3.4 + 1.6 * i, 5.2 + 1.6 * i) if s < 0 else (10.8 - 1.6 * i, 12.6 - 1.6 * i)
            e += [el("brow", (x0, 10.0 - .9 * i, 2.4 - .5 * i), (x1, 11.2 - .8 * i, 3.8), "iron", light=1)]
    # Horns from the outer ends of the brow, swept back and up past the crown; the right one broken short.
    for s, length in ((-1, 1.0), (1, .55)):
        x = 8 + s * 4.6
        for i in range(3):
            xi = x + s * .5 * i
            z1 = 5.4 + (5.0 + 3.0 * i) * length + 2.0
            e += [el("horn", (xi - .45 + .12 * i, 9.2 + .5 * i, 5.4 + 2.6 * i * length), (xi + .45 - .12 * i, 10.4 + .5 * i - .2 * i, z1), "iron",
                     ("x", -11.25, (xi, 10.0, 5.4)), light=1 if i < 2 else 2)]
    # A ragged mane of spikes off the back of the crown, uneven, one snapped off.
    for i, (x, length, lift) in enumerate(((5.0, 5.0, -.6), (6.5, 7.5, -.2), (8.0, 3.0, .0), (9.5, 8.5, -.2), (11.0, 5.5, -.6))):
        e += [el("mane", (x - .45, 11.4 + lift, 10.4), (x + .45, 12.4 + lift, 10.4 + length), "iron", ("x", -11.25, (x, 11.9, 10.4)),
                 light=1 if i % 2 else 0)]
    # The dark under the brow, the eyes small, slanted and set far back in it.
    e += [el("eye shadow", (4.2, 5.8, 3.2), (11.8, 9.4, 4.0), "dark")]
    for s in (-1, 1):
        x = 8 + s * 1.9
        e += [el("eye", (x - .7, 7.4, 3.0), (x + .7, 7.9, 3.2), "eye"),
              el("eye", (x + s * .2 - .5, 7.9, 3.0), (x + s * .2 + .5, 8.3, 3.2), "eye")]
    # The maw: a dark mouth with fangs hanging into it and rising from the jaw, the jaw stepping down to a point.
    e += [el("muzzle plate", (4.4, 5.6, 2.4), (11.6, 6.4, 3.4), "iron"),
          el("maw", (5.0, 4.0, 2.6), (11.0, 5.6, 3.4), "dark")]
    for i, (x, top_len) in enumerate(((5.6, .9), (7.0, 1.3), (9.0, 1.3), (10.4, .9))):
        e += [el("fang", (x - .25, 5.6 - top_len, 2.4), (x + .25, 5.7, 2.8), "iron", light=2)]
    for x in (6.3, 8.0, 9.7):
        e += [el("lower fang", (x - .2, 4.0, 2.5), (x + .2, 4.7, 2.9), "iron", light=2)]
    for i, (x0, y0, y1) in enumerate(((4.4, 3.2, 4.2), (5.6, 2.6, 3.2), (6.8, 2.0, 2.6))):
        e += [el("jaw", (x0, y0, 2.2 + .1 * i), (16 - x0, y1, 3.6), "iron", light=0 if i == 0 else 1)]
    # Spurs off the jaw corners, pointing back.
    for s in (-1, 1):
        x = 8 + s * 4.3
        e += [el("jaw spur", (x - .35, 3.4, 3.0), (x + .35, 4.2, 7.0), "iron", ("x", -22.5, (x, 3.8, 3.0)), light=1)]
    # Torn crimson strips hanging from the back of the crown.
    for i, (x, length) in enumerate(((5.6, 7.0), (7.0, 10.0), (8.6, 5.0), (10.0, 8.0))):
        e += [el("helm strip", (x, 12.0 - length, 12.6), (x + 1.1, 12.0, 13.3), "cloth", ("x", -22.5, (8.0, 12.0, 13.0)),
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
        # Spikes of different lengths jutting up and out of the top plate.
        for k, (dz, length) in enumerate(((5.8, 3.4 + big * 2), (8.0, 2.0), (10.2, 2.8 + big))):
            e += [el("pauldron spike", (cx - .45, 25.0 + big * .5, dz - .45), (cx + .45, 25.0 + big * .5 + length, dz + .45), "iron",
                     ("z", -s * 45.0, (cx, 25.4 + big * .5, dz)), light=1 + (k == 0))]
    # The hood: crimson cloth piled round the neck, heavier at the back.
    e += [el("hood front", (4.6, 22.6, 4.8), (11.4, 24.8, 6.4), "cloth", ("x", -22.5, (8.0, 23.6, 5.6)), light=1),
          el("hood back", (3.8, 21.4, 9.6), (12.2, 26.0, 12.0), "cloth"),
          el("hood side", (3.4, 22.4, 5.6), (4.8, 25.0, 10.4), "cloth", light=-1),
          el("hood side", (11.2, 22.4, 5.6), (12.6, 25.0, 10.4), "cloth", light=-1)]
    # The cape to the knee, its hem torn into strips of different lengths.
    e += [el("cape", (3.4, 9.0, 10.6), (12.6, 22.0, 11.4), "cloth")]
    for i, (x, bottom) in enumerate(((3.4, 3.0), (4.6, 6.8), (5.6, 1.2), (6.8, 5.0), (7.8, 7.6), (8.8, 2.2), (10.0, 6.0), (11.2, 4.0))):
        e += [el("cape strip", (x, bottom, 10.7 + .1 * (i % 2)), (x + 1.3, 9.2, 11.3 + .1 * (i % 2)), "cloth", light=-(i % 2))]
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
