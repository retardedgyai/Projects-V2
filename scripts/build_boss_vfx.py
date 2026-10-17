"""黒翳竜 shader VFX experiment (2026-10-17, the Creator asked to try core shaders "as far as vanilla goes").

Writes into the core-ui-pack (generated, git-ignored):
- assets/minecraft/shaders/core/item.{vsh,fsh} from assets/bosses/shaders/ — vanilla 26.2 core/item plus a branch
  for items whose tint is a VFX marker (blue 77, green = kind 1..5); every other item renders as vanilla.
- assets/projects/{textures/item,models,items}/boss_vfx/<name> — data maps (R mask, G flow, B across) on flat
  (ground) or upright quads, tinted through custom_model_data so the server drives the parameters.

    python scripts/build_boss_vfx.py        # gen_resource_pack.py runs it
"""
import json
import math
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
SHADERS = ROOT / "assets/bosses/shaders"
SHADER_FILES = ("item.vsh", "item.fsh")


def data_map(w, h, fn):
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            m, f, a = fn((x + .5) / w, (y + .5) / h)
            if m > 0.004:
                im.putpixel((x, y), (round(255 * min(1, m)), round(255 * min(1, max(0, f))), round(255 * min(1, max(0, a))), 255))
    return im


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def slash(u, v):
    """Flat crescent around the centre, ±90° about +Z (v grows toward +Z), flow 0 at the right (-X) end."""
    x, z = 2 * u - 1, 2 * v - 1
    r, a = math.hypot(x, z), math.atan2(x, z)
    half = math.radians(90)
    if abs(a) > half or not .36 < r <= 1:
        return 0, 0, 0
    band = 1 - abs(r - .78) / .42
    ends = smooth(0, .12, (half - abs(a)) / (2 * half))
    return max(0, band) ** .8 * ends, (a + half) / (2 * half), (r - .36) / .64


def ring(u, v):
    x, z = 2 * u - 1, 2 * v - 1
    r = math.hypot(x, z)
    if not .74 < r <= 1:
        return 0, 0, 0
    return max(0, 1 - abs(r - .9) / .16), (math.atan2(x, z) / (2 * math.pi)) % 1, (r - .74) / .26


def beam(u, v):
    """Along v (toward +Z on a flat quad), soft across u, tapered at both ends."""
    across = abs(2 * u - 1)
    return (1 - across ** 1.6) * smooth(0, .06, v) * smooth(0, .1, 1 - v), v, across


def puff(u, v):
    x, y = 2 * u - 1, 2 * v - 1
    r = math.hypot(x, y)
    a = math.atan2(y, x)
    edge = .78 + .12 * math.sin(a * 5 + 1.3) + .06 * math.sin(a * 11)
    return max(0, 1 - r / edge) ** .7, r, (a / (2 * math.pi)) % 1


def curtain(u, v):
    """Upright: across 0 at the bottom (v=1) to 1 at the top."""
    return smooth(0, .18, u) * smooth(0, .18, 1 - u), u, 1 - v


SPRITES = {"slash": (128, 128, slash, "flat"), "ring": (64, 64, ring, "flat"), "beam": (32, 128, beam, "flat"),
           "beam_up": (128, 32, lambda u, v: beam(v, u), "upright"), "ring_up": (64, 64, ring, "upright"), "puff": (32, 32, puff, "upright"), "smoke": (32, 32, puff, "upright"),
           "curtain": (32, 64, curtain, "upright")}


def model(name, kind):
    face = {"texture": "#fx", "uv": [0, 0, 16, 16], "tintindex": 0}
    if kind == "flat":
        element = {"from": [0, 8, 0], "to": [16, 8, 16], "faces": {"up": face}}
    else:
        element = {"from": [0, 0, 8], "to": [16, 16, 8], "faces": {"north": face, "south": face}}
    element.update(light_emission=15, shade=False)
    return {"ambientocclusion": False, "elements": [element],
            "textures": {"fx": {"sprite": f"projects:item/boss_vfx/{name}", "force_translucent": True},
                         "particle": f"projects:item/boss_vfx/{name}"}}


def build(pack=PACK):
    for name, (w, h, fn, kind) in SPRITES.items():
        tex = pack / f"assets/projects/textures/item/boss_vfx/{name}.png"   # item/ is in the vanilla items atlas
        tex.parent.mkdir(parents=True, exist_ok=True)
        data_map(w, h, fn).save(tex)
        for sub, value in (("models", model(name, kind)),
                           ("items", {"model": {"type": "minecraft:model", "model": f"projects:boss_vfx/{name}",
                                                "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}})):
            path = pack / f"assets/projects/{sub}/boss_vfx/{name}.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value, indent=1) + "\n", encoding="utf-8")
    core = pack / "assets/minecraft/shaders/core"
    core.mkdir(parents=True, exist_ok=True)
    for f in SHADER_FILES:
        shutil.copyfile(SHADERS / f, core / f)


def shader_overrides():
    return {f"assets/minecraft/shaders/core/{f}" for f in SHADER_FILES}


if __name__ == "__main__":
    build()
    print(f"boss_vfx: {len(SPRITES)} sprites, shaders {', '.join(SHADER_FILES)}")
