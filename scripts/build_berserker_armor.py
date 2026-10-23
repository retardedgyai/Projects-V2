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


def from_bbmodel(path: Path):
    """The cubes of a Blockbench model as item model elements, and its texture."""
    bb = json.loads(path.read_text(encoding="utf-8"))
    tex = bb["textures"][0]
    img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
    uw, uh = tex.get("uv_width", img.width), tex.get("uv_height", img.height)
    elements = []
    for c in bb["elements"]:
        if c.get("type", "cube") != "cube" or c.get("visibility") is False:
            continue
        e = {"name": c["name"], "from": c["from"], "to": c["to"],
             "faces": {f: {"uv": [v["uv"][0] * 16 / uw, v["uv"][1] * 16 / uh, v["uv"][2] * 16 / uw, v["uv"][3] * 16 / uh],
                           "texture": "#atlas", **({"rotation": v["rotation"]} if v.get("rotation") else {})}
                       for f, v in c["faces"].items() if v.get("texture") is not None}}
        r = c.get("rotation", [0, 0, 0])
        if any(r):
            e["rotation"] = {"origin": c["origin"], "x": r[0], "y": r[1], "z": r[2]}
        elements.append(e)
    return elements, img


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
