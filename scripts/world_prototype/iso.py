"""Tiny isometric voxel renderer for build previews (viewer at south-east, x=east, y=up, z=south)."""
from pathlib import Path
from PIL import Image, ImageEnhance, ImageDraw
import numpy as np

TEX = Path(__file__).parent / "tex"
TINT = {"grass_block_top": (124, 189, 80), "oak_leaves": (89, 150, 52), "spruce_leaves": (97, 140, 86),
        "birch_leaves": (128, 167, 85), "glow_lichen": (255,255,255), "tall_grass_top": (124, 189, 80), "tall_grass_bottom": (124, 189, 80), "short_grass": (124, 189, 80), "large_fern_top": (100, 160, 70), "pink_petals_stem": (255,255,255), "dark_oak_leaves": (70, 118, 40), "fern": (100, 160, 70), "vine": (80, 140, 50), "lily_pad": (60, 150, 60), "vine": (80, 140, 50), "jungle_leaves": (70, 160, 40), "moss_carpet": (255, 255, 255),
        "water_still": (63, 118, 228), "vine": (89, 150, 52), "short_grass": (124, 189, 80)}
_cache = {}


def tex(name):
    if name == "smooth_quartz": name = "quartz_block_bottom"
    if name.endswith("_wall_banner"): name = name.replace("_wall_banner", "_wool")
    if not (TEX / f"{name}.png").exists():
        for suf in ("_side", "_log", "_top"):
            if (TEX / f"{name}{suf}.png").exists(): name = name + suf; break
    if name.endswith("_wood") and not (TEX / f"{name}.png").exists(): name = name[:-5] + "_log"
    if name not in _cache:
        im = Image.open(TEX / f"{name}.png").convert("RGBA")
        im = im.crop((0, 0, im.width, im.width))
        if name == "glass":  # read as a pane in front of a dark room: bluish sheen, keep the frame
            a = np.asarray(im).astype(float)
            clear = a[..., 3] < 128
            a[clear] = (150, 185, 205, 90)
            im = Image.fromarray(a.astype("uint8"))
        if name in TINT:
            a = np.asarray(im).astype(float)
            a[..., :3] *= np.array(TINT[name]) / 255.0 * (1.25 if name != "water_still" else 1.0)
            if name == "water_still": a[..., 3] = np.maximum(a[..., 3], 215)
            im = Image.fromarray(a.clip(0, 255).astype("uint8"))
        _cache[name] = im
    return _cache[name]


class World:
    def __init__(self):
        self.b = {}  # (x,y,z) -> (shape, textures)

    def set(self, x, y, z, top, side=None, shape="full"):
        self.b[(x, y, z)] = (shape, top, side or top)

    def fill(self, x0, y0, z0, x1, y1, z1, top, side=None, shape="full"):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, top, side, shape)

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def full(self, p):
        v = self.b.get(p)
        return v is not None and v[0] == "full" and v[1] not in ("glass", "water_still", "oak_leaves", "spruce_leaves")


THIN = ("shut_n", "shut_w", "deer", "beetle", "sprite", "boss", "cloth_e", "cloth_w", "rabbit", "boar", "frog", "wolf", "sheep_x", "sheep_z", "crop", "plant", "fire", "arm_z", "arm_x", "npc_low", "npc_high", "chain", "lantern", "post", "carpet", "shut_s", "shut_e", "pane_x", "pane_z", "water", "thinlog", "ceil")


def solid(world, p):
    v = world.b.get(p)
    return v is not None and not v[0].startswith("fence") and v[0] not in THIN and v[1] != "glass"


AO_LEVEL = (0.50, 0.68, 0.84, 1.0)


def ao_map(world, p, n, tu, tv, cw, ch):
    """Minecraft-style smooth lighting: each face corner darkens with the solid blocks around it."""
    add = lambda a, b: (a[0] + b[0], a[1] + b[1], a[2] + b[2])
    mul = lambda a, k: (a[0] * k, a[1] * k, a[2] * k)
    base = add(p, n)
    corners = {}
    for su in (-1, 1):
        for sv in (-1, 1):
            s1 = solid(world, add(base, mul(tu, su)))
            s2 = solid(world, add(base, mul(tv, sv)))
            c = solid(world, add(add(base, mul(tu, su)), mul(tv, sv)))
            corners[(su, sv)] = AO_LEVEL[0 if s1 and s2 else 3 - (s1 + s2 + c)]
    u = np.linspace(0, 1, cw)[None, :]
    v = np.linspace(0, 1, ch)[:, None]
    return (corners[(-1, -1)] * (1 - u) * (1 - v) + corners[(1, -1)] * u * (1 - v)
            + corners[(-1, 1)] * (1 - u) * v + corners[(1, 1)] * u * v)


def boxes(shape):
    """Sub-boxes in block units (x0,y0,z0,x1,y1,z1)."""
    if shape == "full": return [(0, 0, 0, 1, 1, 1)]
    if shape == "slab": return [(0, 0, 0, 1, .5, 1)]
    if shape == "slab_t": return [(0, .5, 0, 1, 1, 1)]
    if shape == "water": return [(0, 0, 0, 1, .875, 1)]
    if shape == "shut_s": return [(0, 0, 0, 1, 1, .1875)]
    if shape == "shut_n": return [(0, 0, .8125, 1, 1, 1)]
    if shape == "shut_w": return [(.8125, 0, 0, 1, 1, 1)]
    if shape == "shut_e": return [(0, 0, 0, .1875, 1, 1)]
    if shape == "sheep_x":   # body along x, white wool; head and legs darker
        return [(.08, .35, .22, .86, .9, .78), (.82, .5, .32, 1.0, .85, .68, "white_terracotta"),
                (.15, 0, .28, .27, .35, .4, "white_terracotta"), (.15, 0, .6, .27, .35, .72, "white_terracotta"),
                (.67, 0, .28, .79, .35, .4, "white_terracotta"), (.67, 0, .6, .79, .35, .72, "white_terracotta")]
    if shape == "sheep_z":
        return [(.22, .35, .08, .78, .9, .86), (.32, .5, .82, .68, .85, 1.0, "white_terracotta"),
                (.28, 0, .15, .4, .35, .27, "white_terracotta"), (.6, 0, .15, .72, .35, .27, "white_terracotta"),
                (.28, 0, .67, .4, .35, .79, "white_terracotta"), (.6, 0, .67, .72, .35, .79, "white_terracotta")]
    if shape == "cloth_e":   # thin sloped tent sheet rising toward +x (two half steps inside the block)
        return [(0, 0, 0, .5, .55, 1), (.5, .45, 0, 1, 1, 1)]
    if shape == "cloth_w":
        return [(.5, 0, 0, 1, .55, 1), (0, .45, 0, .5, 1, 1)]
    if shape == "rabbit": return [(.3, 0, .35, .7, .35, .65), (.35, .3, .55, .65, .5, .75), (.4, .5, .6, .47, .7, .66), (.53, .5, .6, .6, .7, .66)]
    if shape == "boar":
        return [(.05, .25, .2, .85, .8, .8), (.8, .3, .3, 1.0, .7, .7, "dark_oak_planks"), (.95, .4, .4, 1.0, .5, .6, "white_terracotta"),
                (.15, 0, .25, .3, .25, .4), (.15, 0, .6, .3, .25, .75), (.6, 0, .25, .75, .25, .4), (.6, 0, .6, .75, .25, .75)]
    if shape == "frog": return [(.2, 0, .2, .8, .3, .8), (.25, .3, .3, .4, .42, .45, "white_terracotta"), (.6, .3, .3, .75, .42, .45, "white_terracotta")]
    if shape == "wolf":
        return [(.1, .3, .3, .8, .7, .7), (.75, .35, .32, 1.0, .75, .68), (.9, .7, .35, .98, .9, .45), (.9, .7, .55, .98, .9, .65),
                (0, .55, .45, .12, .65, .55), (.15, 0, .32, .27, .3, .44), (.15, 0, .56, .27, .3, .68), (.62, 0, .32, .74, .3, .44), (.62, 0, .56, .74, .3, .68)]
    if shape == "deer":
        return [(.1, .4, .3, .8, .8, .7), (.75, .6, .38, .95, .95, .62), (.82, .95, .3, .86, 1.0, .36, "dark_oak_planks"), (.82, .95, .64, .86, 1.0, .7, "dark_oak_planks"),
                (.15, 0, .33, .25, .4, .43), (.15, 0, .57, .25, .4, .67), (.62, 0, .33, .72, .4, .43), (.62, 0, .57, .72, .4, .67)]
    if shape == "beetle": return [(.2, 0, .25, .8, .35, .75), (.75, .05, .38, .95, .25, .62, "black_concrete")]
    if shape == "sprite": return [(.35, .2, .35, .65, .75, .65), (.25, .75, .25, .75, 1.0, .75, "flowering_azalea_leaves")]
    if shape == "boss":
        return [(-.6, 0, -.6, 1.6, 2.2, 1.6), (-.2, 2.2, -.2, 1.2, 3.0, 1.2, "flowering_azalea_leaves")]
    if shape == "crop": return [(0, 0, 0, 1, .8, 1)]
    if shape == "plant": return [(.15, 0, .15, .85, .7, .85)]
    if shape == "fire": return [(.1, 0, .1, .9, .4, .9)]
    if shape == "arm_z": return [(.44, .45, 0, .56, .85, 1)]
    if shape == "arm_x": return [(0, .45, .44, 1, .85, .56)]
    if shape == "npc_low": return [(.3, 0, .38, .48, .8, .62), (.52, 0, .38, .7, .8, .62), (.25, .8, .32, .75, 1, .68)]
    if shape == "npc_high": return [(.25, 0, .32, .75, .6, .68), (.3, .6, .3, .7, 1, .7, "white_terracotta")]
    if shape == "chain": return [(.4375, 0, .4375, .5625, 1, .5625)]
    if shape == "pane_x": return [(0, 0, .4375, 1, 1, .5625)]
    if shape == "pane_z": return [(.4375, 0, 0, .5625, 1, 1)]
    if shape == "wall": return [(.25, 0, .25, .75, 1, .75)]
    if shape == "post": return [(.375, 0, .375, .625, 1, .625)]
    if shape == "thinlog": return [(.25, 0, .25, .75, 1, .75)]
    if shape == "carpet": return [(0, 0, 0, 1, .0625, 1)]
    if shape == "ceil": return [(0, .9375, 0, 1, 1, 1)]
    if shape == "lantern": return [(.3125, 0, .3125, .6875, .5625, .6875)]
    if shape.startswith("fence"):  # fence_nesw flags e.g. fence_ns
        out = [(.375, 0, .375, .625, 1, .625)]
        f = shape[6:] if len(shape) > 5 else ""
        if "n" in f: out.append((.4375, .4, 0, .5625, .9, .375))
        if "s" in f: out.append((.4375, .4, .625, .5625, .9, 1))
        if "w" in f: out.append((0, .4, .4375, .375, .9, .5625))
        if "e" in f: out.append((.625, .4, .4375, 1, .9, .5625))
        return out
    if shape.startswith("stair"):  # stair_<facing>[_t]: facing = side the tall part is on
        facing, top = shape.split("_")[1], shape.endswith("_t")
        base = (0, .5, 0, 1, 1, 1) if top else (0, 0, 0, 1, .5, 1)
        y0, y1 = (0, .5) if top else (.5, 1)
        half = {"n": (0, y0, 0, 1, y1, .5), "s": (0, y0, .5, 1, y1, 1),
                "w": (0, y0, 0, .5, y1, 1), "e": (.5, y0, 0, 1, y1, 1)}[facing]
        return [base, half]
    raise ValueError(shape)


def render(world, W=32, pad=40, bg=(18, 20, 32, 255), outline=False):
    VY = W * 0.61  # screen height of one block edge
    P = lambda x, y, z: ((x - z) * W / 2, (x + z) * W / 4 - y * VY)
    items = []
    for (x, y, z), (shape, top, side) in world.b.items():
        sh = "full" if shape == "water" and world.b.get((x, y + 1, z), ("",))[0] == "water" else shape
        for bx in boxes(sh):
            t2, s2 = (bx[6], bx[6]) if len(bx) > 6 else (top, side)
            bx = bx[:6]
            x0, y0, z0, x1, y1, z1 = bx
            items.append(((x + (x0 + x1) / 2) + (z + (z0 + z1) / 2) + (y + (y0 + y1) / 2) * 1.0,
                          (x, y, z), shape, t2, s2, bx))
    items.sort(key=lambda t: t[0])
    pts = [P(x + a, y + b, z + c) for _, (x, y, z), *_ in items for a in (0, 1) for b in (0, 1) for c in (0, 1)]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    ox, oy = -min(xs) + pad, -min(ys) + pad
    img = Image.new("RGBA", (int(max(xs) - min(xs) + 2 * pad), int(max(ys) - min(ys) + 2 * pad)), bg)

    def face(t, p0, p1, p2, crop, shade, ao=None):
        # t cropped to crop (u0,v0,u1,v1 in 0..1); p0 = tex top-left, p1 = top-right, p2 = bottom-left
        n = t.width
        c = t.crop((int(crop[0] * n), int(crop[1] * n), max(int(crop[0] * n) + 1, int(round(crop[2] * n))),
                    max(int(crop[1] * n) + 1, int(round(crop[3] * n)))))
        a = np.asarray(c).astype(float)
        m = shade * (ao(c.width, c.height) if ao else 1.0)
        a[..., :3] *= np.asarray(m)[..., None] if ao else m
        c = Image.fromarray(a.clip(0, 255).astype("uint8"))
        q = [(p[0] + ox, p[1] + oy) for p in (p0, p1, p2)]
        q3 = (q[1][0] + q[2][0] - q[0][0], q[1][1] + q[2][1] - q[0][1])
        bx0, by0 = int(min(p[0] for p in q + [q3])) - 1, int(min(p[1] for p in q + [q3])) - 1
        bx1, by1 = int(max(p[0] for p in q + [q3])) + 2, int(max(p[1] for p in q + [q3])) + 2
        ux, uy = q[1][0] - q[0][0], q[1][1] - q[0][1]
        vx, vy = q[2][0] - q[0][0], q[2][1] - q[0][1]
        det = ux * vy - uy * vx
        if abs(det) < 1e-6: return
        # out (X,Y) -> (s,t) in 0..1 -> pixel coords
        cw, ch = c.size
        ia, ib = vy / det, -vx / det
        ic, id_ = -uy / det, ux / det
        X0, Y0 = bx0 - q[0][0], by0 - q[0][1]
        data = (ia * cw, ib * cw, (ia * X0 + ib * Y0) * cw, ic * ch, id_ * ch, (ic * X0 + id_ * Y0) * ch)
        out = c.transform((bx1 - bx0, by1 - by0), Image.AFFINE, data, resample=Image.NEAREST)
        img.alpha_composite(out, (bx0, by0))
        if outline:
            ImageDraw.Draw(img, "RGBA").line([tuple(q[0]), tuple(q[1]), q3, tuple(q[2]), tuple(q[0])], fill=(0, 0, 0, 38), width=1)

    for _, (x, y, z), shape, top, side, (x0, y0, z0, x1, y1, z1) in items:
        X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        cube = shape in ("full", "water")
        wat = shape == "water"
        hid = lambda p: world.full(p) or (wat and world.b.get(p, ("",))[0] == "water")
        # top (+y)
        if not (cube and hid((x, y + 1, z))) or (y1 < 1 and not (wat and (x, y + 1, z) in world.b)):
            face(tex(top), P(X0, Y1, Z0), P(X1, Y1, Z0), P(X0, Y1, Z1), (x0, z0, x1, z1), 1.0,
                 lambda cw, ch: ao_map(world, (x, y, z), (0, 1, 0), (1, 0, 0), (0, 0, 1), cw, ch) if y1 >= 1 else 1.0)
        # south (+z): left side on screen; u along +x, v down
        if not (cube and hid((x, y, z + 1))) or (z1 < 1 and not cube):
            face(tex(side), P(X0, Y1, Z1), P(X1, Y1, Z1), P(X0, Y0, Z1), (x0, 1 - y1, x1, 1 - y0), 0.82,
                 lambda cw, ch: ao_map(world, (x, y, z), (0, 0, 1), (1, 0, 0), (0, -1, 0), cw, ch) if z1 >= 1 else 1.0)
        # east (+x): right side; u along -z
        if not (cube and hid((x + 1, y, z))) or (x1 < 1 and not cube):
            face(tex(side), P(X1, Y1, Z1), P(X1, Y1, Z0), P(X1, Y0, Z1), (1 - z1, 1 - y1, 1 - z0, 1 - y0), 0.64,
                 lambda cw, ch: ao_map(world, (x, y, z), (1, 0, 0), (0, 0, -1), (0, -1, 0), cw, ch) if x1 >= 1 else 1.0)
    return img
