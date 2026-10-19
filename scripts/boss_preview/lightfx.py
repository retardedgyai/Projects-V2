"""CPU emulation of 翳の巣's lighting and the shade_glow post effect, for fightclip.py's lit modes (NOT Minecraft).

- Block light: the arena's crying obsidian grid and pillar-top froglights (BossArenaFactory) plus the server's light blocks from the dump (ShadeWyrmLights),
  spread as vanilla does through open air (level minus the block distance), sampled per pixel at the surface.
- lightmap.fsh's maths: ambient + sky light (night) + block light tinted by BLOCK_LIGHT_TINT, mixed toward white
  near levels 0 and 15. "before" uses the overworld's values, "violet" the enraged values; rage_look() blends them as the shade_rage timeline does.
- Bloom: shade_glow_extract.fsh, two box_blur passes per size, shade_glow_composite.fsh, with the radii scaled from a
  1920 px wide screen to the preview's width.
"""
import json
import math
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
C = np.array([32.5, 40.0, 32.5])                      # BossArena.bossSpawn: the dump's coordinates are relative to it


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], float) / 255


LOOK = {
    # (block light tint, ambient, sky light colour); sky light at night is dimmed by SKY_FACTOR
    "before": (rgb(0xffd88c), rgb(0x0a0a0a), rgb(0x4a5a8a)),
    "violet": (rgb(0xb34cff), rgb(0x1c1128), rgb(0x3a2660)),
}
SKY_FACTOR = .22


def rage_look(f):
    """The shade_rage timeline at f (0 = the calm night, 1 = enraged): tint and ambient blend, sky light multiplied."""
    e = min(max(f, 0), 1)                                     # LINEAR, like the wave
    (t0, a0, s0), (t1, a1, _) = LOOK["before"], LOOK["violet"]
    return t0 + (t1 - t0) * e, a0 + (a1 - a0) * e, s0 * (1 + (rgb(0xa070ff) - 1) * e)
GLOW = json.loads((ROOT / "scripts/shaders/post/shade_glow.json").read_text())


def arena_sources():
    out = []
    for x in range(0, 65):
        for z in range(0, 65):
            dx, dz = x - 32.0, z - 32.0
            d = math.hypot(dx, dz)
            if d < 22 and x % 8 == 0 and z % 8 == 0:
                out.append((x, 39, z, 10))                   # crying obsidian
            if 19.0 <= d <= 20.5 and (abs(dx) < 1 or abs(dz) < 1):
                out.append((x, 45, z, 15))
    return out


ARENA = arena_sources()


def floor_albedo(bx, bz):
    """The nest's floor blocks (BossArenaFactory's generator), as flat colours."""
    dx, dz = bx - 32.0, bz - 32.0
    dist = np.hypot(dx, dz)
    col = np.empty(bx.shape + (3,)); col[:] = (98, 104, 97)                    # polished tuff
    ob = np.mod(bx * 7 + bz * 13, 9) == 0
    col[ob] = (133, 97, 191)                                                   # amethyst
    sc = (dist < 3.5) | (np.mod(bx * 31 + bz * 17, 11) == 0)
    col[sc] = (14, 40, 52)                                                     # sculk
    fl = (np.mod(bx, 8) == 0) & (np.mod(bz, 8) == 0)
    col[fl] = (40, 12, 70)                                                     # crying obsidian
    return col / 255, np.zeros(bx.shape, bool)


def block_light(P, lamps):
    """P: (..., 3) world positions. Max over sources of level minus the Manhattan distance from the source block."""
    best = np.zeros(P.shape[:-1])
    for x, y, z, l in list(ARENA) + list(lamps):
        d = np.abs(P[..., 0] - (x + .5)) + np.abs(P[..., 1] - (y + .5)) + np.abs(P[..., 2] - (z + .5))
        np.maximum(best, l - d, out=best)
    return np.clip(best, 0, 15)


def lightmap(level, look):
    tint, amb, sky = LOOK[look] if isinstance(look, str) else look
    bl = level / 15
    bright = bl / (4 - 3 * bl)
    mixf = .9 * (2 * bl - 1) ** 2
    col = amb + sky * SKY_FACTOR + (tint * (1 - mixf[..., None]) + mixf[..., None]) * bright[..., None]
    col = np.clip(col, 0, 1)
    m = np.maximum(col.max(-1, keepdims=True), 1e-6)
    ng = col * ((1 - (1 - m) ** 4) / m)
    return col * .5 + ng * .5                                                  # BrightnessFactor 0.5 (the default)


def light(arr, zb, dyn, look, cam, right, up, ox, oy, zoom, self_lit=None):
    """Relights the rendered frame in place: the wyrm by the light at each pixel, the empty pixels as the floor.
    self_lit: a mask of wyrm pixels whose texel is self-lit (alpha 251, the second form's veins): left unlit."""
    H, W = zb.shape
    X, Y = np.meshgrid(np.arange(W) + .5, np.arange(H) + .5)
    a, b = (X - ox) / zoom, (oy - Y) / zoom
    model = zb > -1e8
    s = np.where(model, zb, -(a * right[1] + b * up[1]) / cam[1])
    p = a[..., None] * right + b[..., None] * up + s[..., None] * cam          # model px (X mirrored, 16 px a block)
    world = np.stack([C[0] - p[..., 0] / 16, C[1] + p[..., 1] / 16, C[2] + p[..., 2] / 16], -1)
    lamps = [(int(C[0]) + x, int(C[1]) + y, int(C[2]) + z, l) for x, y, z, l in dyn]
    surf = world.copy(); surf[..., 1] = np.where(model, surf[..., 1], 40.5)   # the floor takes the light above it
    lm = lightmap(block_light(surf, lamps), look)
    alb, emissive = floor_albedo(np.floor(world[..., 0]), np.floor(world[..., 2]))
    inside = np.hypot(world[..., 0] - 32, world[..., 2] - 32) < 22
    floor = np.where(emissive[..., None], alb, alb * lm) * 255
    floor = np.where(inside[..., None], floor, (8, 6, 12))
    wyrm = arr / 255 * lm * 1.25 * 255
    if self_lit is not None:
        wyrm = np.where(self_lit[..., None], arr * 1.15, wyrm)
    arr[:] = np.where(model[..., None], wyrm, floor)


def _box(img, r, axis):
    if r < 1:
        return img
    k = 2 * r + 1
    pad = [(0, 0)] * img.ndim; pad[axis] = (r, r)
    c = np.cumsum(np.pad(img, pad, mode="edge"), axis=axis)
    c = np.concatenate([np.zeros_like(np.take(c, [0], axis=axis)), c], axis=axis)
    n = img.shape[axis]
    return (np.take(c, range(k, k + n), axis=axis) - np.take(c, range(0, n), axis=axis)) / k


def bloom(arr):
    """shade_glow.json on the frame (0..255 floats), in place."""
    passes = GLOW["passes"]
    u = {v["name"]: v["value"] for v in passes[0]["uniforms"]["GlowConfig"]}
    comp = {v["name"]: v["value"] for v in passes[5]["uniforms"]["CompositeConfig"]}
    rs = [next(v["value"] for v in p["uniforms"]["BlurConfig"] if v["name"] == "Radius") for p in passes[1:5]]
    scale = arr.shape[1] / 1920
    c = arr / 255
    lum = c @ np.array([.299, .587, .114])
    violet = np.clip((c[..., 2] + c[..., 0] * .6 - c[..., 1] * 1.4) * 2, 0, 1)
    t = np.clip((lum - u["Threshold"]) / .25, 0, 1); k = t * t * (3 - 2 * t) * violet
    bright = c * (k * u["Strength"])[..., None]
    small = _box(_box(bright, round(rs[0] * scale), 1), round(rs[1] * scale), 0)
    wide = _box(_box(small, round(rs[2] * scale), 1), round(rs[3] * scale), 0)
    glow = np.clip(small * comp["SmallIntensity"] + wide * comp["WideIntensity"], 0, 1)
    arr[:] = (1 - (1 - c) * (1 - glow)) * 255
