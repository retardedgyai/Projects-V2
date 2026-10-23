"""狂戦士の甲冑 (warrior ultimate, Creator 2026-10-23): the armor the warrior turns into for ten seconds.

Built on its own, not a recolour of another set. The rules, read from the Creator's references (@AnzSamaEr's
Blockbench pieces) and the Berserk armor as a character idea (nothing traced):
- one hero: the helm is a beast's skull, a muzzle pushed forward with teeth and slanted red eyes under a heavy
  brow, fins swept back. Body and legs stay quieter so the head reads first.
- big colour masses, few materials: near-black iron (a cool violet-grey ramp), bone teeth, rust-red straps, red eyes.
- every face is painted for itself: a smooth light-to-dark ramp down the face, a bright bevel on the top and left
  edges, a dark one on the bottom and right. No random speckle.
- parts overlap in depth (brow over eyes, muzzle over jaw, lames over lames) so it changes as it turns.

Writes the body/leg equipment layers (64x32, painted per face), the equipment asset, and the helm model + item
on its own painted atlas.

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
    "iron": ramp("#1a1820", "#2b2833", "#403b4b", "#585266", "#7b748a", "#aaa3b8"),
    "dark": ramp("#060508", "#0c0a0e", "#141117", "#1c1820", "#26212b", "#322c38"),
    "bone": ramp("#57503f", "#7d735f", "#a3977e", "#c4b89d", "#ddd2b8", "#f0e9d6"),
    "rust": ramp("#2a0a09", "#43100d", "#631812", "#86221a", "#a83122", "#c9452f"),
    "eye": ramp("#6e0e07", "#a8180c", "#e8261a", "#ff4a2c", "#ff8a52", "#ffd6a8"),
}
# How lit each face is before its own ramp (0..5).
FACE_LIGHT = {"up": 4, "north": 3, "west": 2, "east": 2, "south": 2, "down": 0}


# ----------------------------------------------------------------------------------------------- the helm

def el(name, lo, hi, mat, rot=None, light=0):
    """One box; rot = (axis, angle, origin). light nudges the whole box brighter or darker."""
    e = {"name": name, "from": list(lo), "to": list(hi), "mat": mat, "light": light}
    if rot:
        e["rotation"] = {"origin": list(rot[2]), "axis": rot[0], "angle": rot[1], "rescale": False}
    return e


def helm():
    """Front is north (low z). The head is x,y,z 4..12; the shell sits from 3.4 to 12.6."""
    e = []
    # The skull: a domed cap, sides, and a neck guard of two overlapping lames.
    e += [el("skull cap", (3.4, 8.6, 3.6), (12.6, 12.0, 12.6), "iron"),
          el("dome", (4.0, 11.8, 4.2), (12.0, 12.9, 12.4), "iron", light=1),
          el("crown ridge", (7.1, 12.7, 3.6), (8.9, 13.7, 12.8), "iron", light=1),
          el("crown ridge back", (7.3, 12.9, 12.4), (8.7, 13.5, 14.2), "iron", ("x", 22.5, (8.0, 13.2, 12.4)), light=1),
          el("left side", (3.2, 3.6, 4.4), (4.2, 8.8, 12.6), "iron", light=-1),
          el("right side", (11.8, 3.6, 4.4), (12.8, 8.8, 12.6), "iron", light=-1),
          el("neck lame upper", (3.6, 4.2, 12.0), (12.4, 8.8, 13.0), "iron"),
          el("neck lame lower", (3.9, 2.4, 12.4), (12.1, 4.6, 13.6), "iron", light=-1)]
    # The face behind the muzzle is dark: what shows between the parts is shadow, not skin.
    e += [el("face shadow", (4.2, 3.4, 3.5), (11.8, 8.6, 4.4), "dark")]
    # Heavy brow in a V, angled down to the middle: the scowl.
    for s in (-1, 1):
        x0, x1 = (3.0, 8.3) if s < 0 else (7.7, 13.0)
        e += [el("brow plate", (x0, 8.2, 2.4), (x1, 9.8, 3.8), "iron", ("z", s * 22.5, (8 + s * 2.6, 9.0, 3.1)), light=1)]
    # Slanted eyes under the brow, set into the dark.
    for s in (-1, 1):
        x0 = 4.7 if s < 0 else 8.9
        e += [el("eye", (x0, 7.0, 3.1), (x0 + 2.4, 7.8, 3.6), "eye", ("z", s * 22.5, (x0 + 1.2, 7.4, 3.3)))]
    # The muzzle, the hero: three steps narrowing forward to a blunt nose, a ridge along the top.
    e += [el("snout base", (5.8, 4.8, 0.0), (10.2, 8.0, 3.8), "iron", light=1),
          el("snout", (6.4, 5.0, -2.0), (9.6, 7.4, 0.2), "iron", light=1),
          el("nose", (6.9, 5.2, -3.6), (9.1, 6.8, -1.8), "iron"),
          el("snout ridge", (7.4, 7.8, -1.6), (8.6, 8.7, 3.9), "iron", light=2),
          el("nose ridge", (7.5, 6.6, -3.4), (8.5, 7.5, -1.4), "iron", light=2)]
    # The open maw under it: dark inside, bone fangs at the front, smaller teeth down the sides.
    e += [el("maw", (6.2, 3.6, -1.6), (9.8, 5.0, 3.6), "dark"),
          el("jaw", (6.0, 2.6, -1.2), (10.0, 3.8, 3.8), "iron"),
          el("jaw hinge", (4.2, 2.8, 2.6), (11.8, 4.6, 4.4), "iron", light=-1)]
    for s in (-1, 1):
        x = 8 + s * 1.15
        e += [el("upper fang", (x - .35, 3.6, -2.0), (x + .35, 5.1, -1.3), "bone", light=1),
              el("lower fang", (x - .3 - s * .4, 3.7, -1.0), (x + .3 - s * .4, 4.6, -0.4), "bone")]
        for z in (-0.4, 1.0, 2.2):
            xs = 8 + s * 1.95
            e += [el("side tooth", (xs - .3, 4.1, z), (xs + .3, 5.0, z + .6), "bone")]
    # Cheek guards flaring out beside the jaw, and two fins swept back from the temples like laid-back ears.
    for s in (-1, 1):
        x = 8 + s * 4.6
        e += [el("cheek guard", (x - .7, 3.0, 3.4), (x + .7, 7.4, 8.2), "iron", ("z", -s * 22.5, (x, 5.2, 5.8)))]
        # The swept horn: a staircase of plates from the temple back and up, each shorter, the way an ear lies flat.
        for i in range(4):
            lo, hi = (x - .55 + s * .15 * i, 9.0 + .9 * i, 6.0 + 1.5 * i), (x + .55 + s * .15 * i, 10.6 + .9 * i, 11.8 + 1.1 * i)
            e += [el("swept horn", lo, hi, "iron", ("z", -s * 22.5, (x, 10.5 + .9 * i, 9.0)), light=min(2, i))]
    # A strap of rust round the back of the skull, holding it shut.
    e += [el("strap", (3.3, 8.6, 9.2), (12.7, 9.4, 10.2), "rust")]
    return e


def face_size(e, face):
    dx, dy, dz = (e["to"][i] - e["from"][i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def paint_face(img, x0, y0, w, h, mat, level):
    """A smooth ramp down the face with a bevel: light on top/left, dark on bottom/right."""
    tones = MATERIALS[mat]
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (y + .5) / h
            v = level + .8 - 1.6 * t                      # lighter at the top, darker at the bottom
            if y == 0 or x == 0: v += 1.2
            if y == h - 1 or x == w - 1: v -= 1.2
            if mat == "eye":                              # a hot core
                v = 3 + 2 * (1 - abs(2 * (x + .5) / w - 1)) - (y == h - 1)
            px[x0 + x, y0 + y] = tones[max(0, min(5, round(v)))] + (255,)


def helm_model(size=256, density=3):
    """Pack every face into its own painted rectangle on one atlas (3 px per model unit)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cx, cy, row = 0, 0, 0
    out = []
    for e in helm():
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            fw, fh = face_size(e, face)
            w, h = max(2, math.ceil(fw * density)), max(2, math.ceil(fh * density))
            if cx + w > size: cx, cy, row = 0, cy + row, 0
            assert cy + h <= size, "berserker helm atlas is full"
            paint_face(img, cx, cy, w, h, e["mat"], FACE_LIGHT[face] + e["light"])
            faces[face] = {"uv": [cx * 16 / size, cy * 16 / size, (cx + w) * 16 / size, (cy + h) * 16 / size], "texture": "#atlas"}
            cx += w; row = max(row, h)
        box = {k: v for k, v in e.items() if k not in ("mat", "light")}
        box["faces"] = faces
        out.append(box)
    return img, out


# ------------------------------------------------------------------------------------- body and leg layers

# A letter is a material and a step: K D P M L H iron 0..5, k dark, R r rust 2/3, B b bone 3/1, '.' empty.
KEY = {"K": ("iron", 0), "D": ("iron", 1), "P": ("iron", 2), "M": ("iron", 3), "L": ("iron", 4), "H": ("iron", 5),
       "k": ("dark", 1), "R": ("rust", 2), "r": ("rust", 4), "B": ("bone", 4), "b": ("bone", 2)}

# Two big breast plates split down the middle, one fauld plate, a belt with a bone buckle.
CHEST = ("MLLLLLLM", "DPMMMMPD", "LHHMMHHL", "MLLPPLLM", "MMLkkLMM", "PMMkkMMP",
         "DPPkkPPD", "kDDDDDDk", "MLLLLLLM", "PMMMMMMP", "DPPPPPPD", "RrRBbRrR")
BACK = ("MLLLLLLM", "DPMMMMPD", "PMLLLLMP", "PMLMMLMP", "PMMMMMMP", "DPPMMPPD",
        "DPPPPPPD", "kDDDDDDk", "MLLLLLLM", "PMMMMMMP", "DPPPPPPD", "RrRRRRrR")
TORSO_SIDE = ("MLLM", "DMMD", "PMMP", "PMMP", "PMMP", "PPPP", "DPPD", "kDDk", "MLLM", "PMMP", "DPPD", "RrrR")
# Arms: a pauldron over the shoulder, a lame, a strap, and a clawed gauntlet.
ARM = ("HHHH", "LLLL", "MMMM", "DLLD", "kkkk", "MLLM", "PMMP", "DDDD", "LHHL", "MLLM", "PMMP", "LkLk")
ARM_BACK = ("LLLL", "MMMM", "PMMP", "DLLD", "kkkk", "MLLM", "PMMP", "DDDD", "MLLM", "PMMP", "DPPD", "DkDk")
BOOT = ("....",) * 6 + ("LHHL", "MLLM", "PMMP", "DPPD", "LLLL", "kDDk")
BOOT_BACK = ("....",) * 6 + ("MLLM", "PMMP", "DPPD", "DPPD", "PMMP", "kDDk")
# Leggings: tassets over the thigh, a knee cop, the shin under the boots.
LEG = ("MLLM", "PMMP", "PMMP", "DPPD", "kDDk", "LHHL", "MLLM", "kDDk", "PMMP", "PMMP", "DPPD", "kDDk")
LEG_BACK = ("PMMP", "PMMP", "PMMP", "DPPD", "DPPD", "PMMP", "PMMP", "DPPD", "PMMP", "PMMP", "DPPD", "kDDk")
WAIST = ("kkkkkkkk",) * 9 + ("RrRbBRrR", "DPPPPPPD", "kDDDDDDk")


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
    unwrap(outer, (16, 16), 8, 12, 4, CHEST, BACK, TORSO_SIDE, ("DMMMMMMD", "MLLLLLLM", "MLLLLLLM", "DMMMMMMD"), top(8, 4, "k"))
    unwrap(outer, (40, 16), 4, 12, 4, ARM, ARM_BACK, ARM, ("LHHL", "HHHH", "LHHL", "MLLM"), top(4, 4, "k"))
    unwrap(outer, (0, 16), 4, 12, 4, BOOT, BOOT_BACK, BOOT_BACK, top(4, 4, "."), ("kkkk",) * 4)
    inner = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    unwrap(inner, (16, 16), 8, 12, 4, WAIST, WAIST, tuple(r[:4] for r in WAIST), top(8, 4, "k"), top(8, 4, "k"))
    unwrap(inner, (0, 16), 4, 12, 4, LEG, LEG_BACK, LEG_BACK, ("DPPD",) * 4, ("kkkk",) * 4)
    return outer, inner


# ------------------------------------------------------------------------------------------------- write

def write(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")


def save(img, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def main():
    eq = ASSETS / "textures/entity/equipment"
    outer, inner = body_layers()
    save(outer, eq / f"humanoid/armor/{NAME}.png")
    save(inner, eq / f"humanoid_leggings/armor/{NAME}.png")
    write(ASSETS / f"equipment/armor/{NAME}.json",
          {"layers": {"humanoid": [{"texture": f"projects:armor/{NAME}"}], "humanoid_leggings": [{"texture": f"projects:armor/{NAME}"}]}})
    atlas, elements = helm_model()
    save(atlas, ASSETS / f"textures/item/armor/{NAME}_materials.png")
    write(ASSETS / f"models/item/armor/{NAME}_helmet.json", {
        "credit": "Project S: berserker helm (scripts/build_berserker_armor.py)",
        "gui_light": "front", "ambientocclusion": False,
        "textures": {"atlas": f"projects:item/armor/{NAME}_materials", "particle": f"projects:item/armor/{NAME}_materials"},
        "elements": elements,
        # At head transform scale 1.6 cancels the vanilla 0.625 head-layer scale (as the class helmets do).
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]},
                    "gui": {"rotation": [15, -25, 0], "translation": [0, -1, 0], "scale": [0.65, 0.65, 0.65]},
                    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.65, 0.65, 0.65]}}})
    write(ASSETS / f"items/armor/{NAME}_helmet.json", {"model": {"type": "minecraft:model", "model": f"projects:item/armor/{NAME}_helmet"}})


if __name__ == "__main__":
    main()
