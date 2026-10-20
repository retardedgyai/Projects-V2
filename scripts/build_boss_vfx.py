"""黒翳竜 shader VFX experiment (2026-10-17, the Creator asked to try core shaders "as far as vanilla goes").

Writes into the core-ui-pack (generated, git-ignored):
- assets/minecraft/shaders/core/item.{vsh,fsh} from scripts/shaders/ (shared with the warrior iron strikes) — vanilla
  26.2 core/item plus a branch for items whose tint is a VFX marker (blue 77, green = kind 1..6).
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
SHADERS = ROOT / "scripts/shaders"  # one core/item for every combat VFX marker
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


def war_slash(u, v):
    """Warrior heat cut (2026-10-19): the 黒翳竜 crescent made heavy, ±100° about +Z, flow 0 at the -X end.
    R is a soft proximity field around the blade's path (1 on the path, falling to 0 well outside it), so the shader
    can draw a hard white core and a soft glow from the same channel. The band thickens toward the head (flow 1)."""
    x, z = 2 * u - 1, 2 * v - 1
    r, a = math.hypot(x, z), math.atan2(x, z)
    half = math.radians(100)
    over = math.radians(18)                       # the glow runs a little past both ends
    if abs(a) > half + over or not .12 < r <= 1:
        return 0, 0, 0
    flow = min(1.0, max(0.0, (a + half) / (2 * half)))
    width = .12 + .18 * flow
    field = max(0.0, 1 - abs(r - .72) / (width * 2.8))
    ends = smooth(0, 1, (half + over - abs(a)) / (over * 2.2))
    return field * ends, flow, (r - .12) / .88


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


def streak(u, v):
    """桜牙獣 petal with its light ribbon (scripts/build_boss_sakura_fang.py): the notched petal at the top (v < .2),
    the ribbon fading down to the bottom. G = along (0 head .. 1 ribbon end), B = across."""
    across = abs(2 * u - 1)
    if v < .2:
        w = v / .2                                       # 0 tip .. 1 base of the petal
        half = (.35 + .65 * math.sin(math.pi * min(1, .15 + w * .85)) ** .7) * (1 - .5 * w * w)
        if across > half or (w < .16 and across < .22):
            return 0, 0, 0
        return 1, v, across
    return (1 - across ** 1.3) * (1 - (v - .2) / .8) ** .4, v, across


# --- 桜牙獣's sakura sprites: the same data-map contract (R mask, G flow, B across), drawn as blossoms and petals ---
def petal_at(x, y, cx, cy, ang, size, notch=True):
    """Mask of one notched sakura petal centred at (cx, cy), its tip pointing along ang (radians); 0 outside."""
    dx, dy = x - cx, y - cy
    ax, ay = math.sin(ang), math.cos(ang)                             # the tip direction, same convention as the callers
    v, u = (dx * ax + dy * ay) / size, (dx * ay - dy * ax) / size     # v along the petal (-1 base .. 1 tip), u across
    w = (v + 1) / 2
    if not 0 <= w <= 1:
        return 0.0
    half = (.22 + .78 * min(1, w / .7) ** .6) * math.sqrt(max(0, 1 - max(0, (w - .72) / .3) ** 2)) * .55
    if abs(u) > half or (notch and w > 1 - .2 * max(0, 1 - abs(u) / .17)):
        return 0.0
    edge = 1 - abs(u) / max(half, 1e-6)
    return .55 + .45 * min(1, edge * 3) * (.6 + .4 * w)


def crest(u, v):
    """A five-petal sakura crest lying on the ground: flow = angle round it, across = distance from the centre."""
    x, z = 2 * u - 1, 2 * v - 1
    r, a = math.hypot(x, z), math.atan2(x, z)
    m = 0.0
    for k in range(5):
        ang = k * 2 * math.pi / 5
        m = max(m, petal_at(x, z, math.sin(ang) * .5, math.cos(ang) * .5, ang, .5))
    if r < .12:
        m = max(m, 1.0)
    return m, (a / (2 * math.pi)) % 1, min(1, r)


def petal_ring(u, v):
    """A shock ring made of petals, all pointing outward, round the rim."""
    x, z = 2 * u - 1, 2 * v - 1
    r, a = math.hypot(x, z), math.atan2(x, z)
    m = 0.0
    n = 18
    k = round(a / (2 * math.pi) * n)
    for j in (k - 1, k, k + 1):
        ang = j * 2 * math.pi / n
        m = max(m, petal_at(x, z, math.sin(ang) * .84, math.cos(ang) * .84, ang, .15))
    return m, (a / (2 * math.pi)) % 1, min(1, max(0, (r - .6) / .4))


def petal_slash(u, v):
    """A crescent (like slash) whose band is a stream of petals laid along the arc, tips pointing the way it swings."""
    x, z = 2 * u - 1, 2 * v - 1
    r, a = math.hypot(x, z), math.atan2(x, z)
    half = math.radians(90)
    if abs(a) > half or not .4 < r <= 1:
        return 0, 0, 0
    m = 0.0
    for lane, rr in enumerate((.58, .78, .94)):
        n = (9, 12, 14)[lane]
        k = round((a + half) / (2 * half) * (n - 1) + lane * .37)
        for j in (k - 1, k, k + 1):
            aa = -half + (j - lane * .37) * 2 * half / (n - 1)
            if abs(aa) > half:
                continue
            m = max(m, petal_at(x, z, math.sin(aa) * rr, math.cos(aa) * rr, aa - math.pi / 2, .1 + .02 * lane))
    ends = smooth(0, .1, (half - abs(a)) / (2 * half))
    return m * ends, (a + half) / (2 * half), (r - .4) / .6


def petal_trail(u, v):
    """A strip of petals in single file along v (toward +Z), each pointing the way it is thrown."""
    x, z = 2 * u - 1, v
    m = 0.0
    n = 9
    k = round(z * (n - 1))
    for j in (k - 1, k, k + 1):
        if 0 <= j < n:
            cz = j / (n - 1)
            m = max(m, petal_at(x, z * 4, .25 * math.sin(j * 2.1), cz * 4, 0.0, .55))
    return m * smooth(0, .05, v) * smooth(0, .08, 1 - v), v, abs(2 * u - 1)


def blossom(u, v):
    """An upright blossom: five open petals round a bright heart (a billboard burst)."""
    x, y = 2 * u - 1, 2 * v - 1
    r, a = math.hypot(x, y), math.atan2(y, x)
    m = 0.0
    for k in range(5):
        ang = k * 2 * math.pi / 5 + .3
        m = max(m, petal_at(x, y, math.sin(ang) * .48, math.cos(ang) * .48, ang, .5))
    if r < .14:
        m = 1.0
    return m, r, (a / (2 * math.pi)) % 1


def petal_curtain(u, v):
    """Upright: a wall of petals blown upward, thinning toward the top (across 0 bottom .. 1 top)."""
    m = 0.0
    for j in range(7):
        cx, cy = (j * .37 + .1) % 1, (j * .53 + .2) % 1
        m = max(m, petal_at(u * 2, v * 2, cx * 2, cy * 2, math.pi + (j - 3) * .25, .28))
    return m * smooth(0, .15, u) * smooth(0, .15, 1 - u), u, 1 - v


SPRITES = {"slash": (128, 128, slash, "flat"), "war_slash": (128, 128, war_slash, "flat"), "ring": (64, 64, ring, "flat"), "beam": (32, 128, beam, "flat"),
           "beam_up": (128, 32, lambda u, v: beam(v, u), "upright"), "ring_up": (64, 64, ring, "upright"), "puff": (32, 32, puff, "upright"), "smoke": (32, 32, puff, "upright"), "disc": (32, 32, puff, "flat"),
           "curtain": (32, 64, curtain, "upright"),
           "sakura_streak": (16, 80, streak, "cross"),
           "sakura_crest": (128, 128, crest, "flat"), "sakura_ring": (128, 128, petal_ring, "flat"),
           "sakura_slash": (128, 128, petal_slash, "flat"), "sakura_trail": (32, 128, petal_trail, "flat"),
           "sakura_blossom": (64, 64, blossom, "upright"), "sakura_curtain": (64, 64, petal_curtain, "upright")}


def model(name, kind):
    face = {"texture": "#fx", "uv": [0, 0, 16, 16], "tintindex": 0}
    if kind == "cross":                                   # two crossed upright quads: never seen edge-on
        elements = [{"from": [0, 0, 8], "to": [16, 16, 8], "faces": {"north": face, "south": face}},
                     {"from": [8, 0, 0], "to": [8, 16, 16], "faces": {"east": face, "west": face}}]
        for e in elements:
            e.update(light_emission=15, shade=False)
        return {"ambientocclusion": False, "elements": elements,
                "textures": {"fx": {"sprite": f"projects:item/boss_vfx/{name}", "force_translucent": True},
                             "particle": f"projects:item/boss_vfx/{name}"}}
    if kind == "flat":
        element = {"from": [0, 8, 0], "to": [16, 8, 16], "faces": {"up": face}}
    else:
        element = {"from": [0, 0, 8], "to": [16, 16, 8], "faces": {"north": face, "south": face}}
    element.update(light_emission=15, shade=False)
    return {"ambientocclusion": False, "elements": [element],
            "textures": {"fx": {"sprite": f"projects:item/boss_vfx/{name}", "force_translucent": True},
                         "particle": f"projects:item/boss_vfx/{name}"}}


def light_probe(pack):
    """The point-light probe (SakuraLight.kt): a tiny two-sided quad in the check colour item.fsh looks for. item.vsh
    moves it onto the screen's data row, so it is never seen where it stands."""
    tex = pack / "assets/projects/textures/item/boss_vfx/light_probe.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (4, 4), (83, 76, 49, 255)).save(tex)
    face = {"texture": "#probe", "uv": [0, 0, 16, 16], "tintindex": 0}
    files = {"models": {"ambientocclusion": False, "textures": {"probe": "projects:item/boss_vfx/light_probe",
                                                                "particle": "projects:item/boss_vfx/light_probe"},
                        "elements": [{"from": [7.9, 7.9, 8], "to": [8.1, 8.1, 8], "shade": False,
                                      "faces": {"north": face, "south": face}}]},
             "items": {"model": {"type": "minecraft:model", "model": "projects:boss_vfx/light_probe",
                                 "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}}}
    for sub, value in files.items():
        path = pack / f"assets/projects/{sub}/boss_vfx/light_probe.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=1) + "\n", encoding="utf-8")


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
    light_probe(pack)
    # The arena glow (26.3 post effect, turned on per player by the server): our own namespace, no vanilla override.
    post = pack / "assets/projects/shaders/post"
    post.mkdir(parents=True, exist_ok=True)
    for f in ("shade_glow_extract.fsh", "shade_glow_composite.fsh", "sakura_light.fsh", "sakura_light_memory.fsh"):
        shutil.copyfile(SHADERS / "post" / f, post / f)
    for name in ("shade_glow", "sakura_light"):
        effect = pack / f"assets/projects/post_effect/{name}.json"
        effect.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(SHADERS / "post" / f"{name}.json", effect)
    core = pack / "assets/minecraft/shaders/core"
    core.mkdir(parents=True, exist_ok=True)
    for f in SHADER_FILES:
        shutil.copyfile(SHADERS / f, core / f)


def shader_overrides():
    return {f"assets/minecraft/shaders/core/{f}" for f in SHADER_FILES}


if __name__ == "__main__":
    build()
    print(f"boss_vfx: {len(SPRITES)} sprites, shaders {', '.join(SHADER_FILES)}")
