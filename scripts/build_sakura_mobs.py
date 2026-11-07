"""散り桜の社's common enemies (雑魚): sakura creatures in the grammar of Minecraft's own mobs.

    python scripts/build_sakura_mobs.py                    # the ones the dungeon uses -> assets/bosses/<id>.bbmodel
    python scripts/build_sakura_mobs.py --out DIR [ids..]  # any of the proposals (all of them by default) -> DIR

藍 (2026-11-07):「桜コンセプトのマインクラフトのデフォルトにいるモブみたいなの、ちゃんとアニメーションもあるタイプ」, then
「Runeforgeのモブみたいにもっと細部作り込んで」. So each one keeps a vanilla mob's body plan (zombie, spider, creeper,
skeleton, wolf, vex, vindicator, a stump) at 16 px a block and one texel a pixel, built the way RuneForge's own common
enemies are (counted from its pack, nothing copied): 25-50 cubes on 6-12 bones; limbs 3 px thick with a joint knob at
shoulder, elbow and knee; ribs and rings as separate bars; bands, cuffs and straps as boxes 0.3 px proud of the part
under them; flat details (petals, paper streamers, fletching) as planes. Every face is painted as RuneForge's are (measured
2026-11-07: no dark rim, half the neighbouring pixels differ): a volume ramp, light at the top and in the middle, dark
toward the bottom, with a grain of the neighbouring tones; colours low in saturation, sockets dark with one lit pixel. The sakura is in the shape (bark with cherry lenticels,
five-petal flowers, a bud in green sepals, a petal mane, a blossom shield), with the shrine's rope and paper (注連縄・紙垂)
as the second material.

Same pipeline as the mini-bosses (build_boss_hanamori.py: Builder, bake). Front is +Z, 16 px = 1 block, every bone
rests at rotation 0. Bone names carry the mob's own prefix. Clips: idle, walk, hurt (a flinch), death and one once clip
per skill id, as long as its telegraph plus recovery, the blow landing at the telegraph's end.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_boss_hanamori as hm  # noqa: E402
from build_boss_hanamori import Builder, merged, wave, PINK, SELF_LIT, _h, add  # noqa: E402
from boss_models import ROOT  # noqa: E402

# --- palette: four steps a material (rim, mid, light, highlight) ------------------------------------
P = {
    "bark": ((30, 18, 22), (58, 34, 38), (82, 52, 50), (110, 74, 64), (140, 104, 88)),
    "wood": ((74, 48, 30), (108, 72, 44), (140, 100, 62), (172, 130, 84), (204, 164, 112)),
    "bone": ((128, 112, 96), (176, 160, 138), (210, 198, 176), (232, 224, 204), (248, 244, 230)),
    "sepal": ((24, 48, 26), (42, 76, 38), (62, 104, 50), (90, 134, 64), (124, 164, 84)),
    "bud": ((112, 52, 72), (158, 84, 106), (196, 118, 138), (222, 156, 170), (240, 196, 204)),
    "kstone": ((62, 64, 72), (96, 98, 106), (128, 130, 136), (160, 160, 164), (192, 190, 188)),
    "moss": ((32, 56, 30), (52, 84, 40), (74, 110, 50), (100, 138, 62), (132, 166, 80)),
    "cloth": ((56, 18, 28), (96, 30, 42), (134, 44, 56), (166, 64, 72), (196, 96, 100)),
    "lacq": ((16, 14, 20), (30, 26, 36), (46, 40, 54), (70, 62, 80), (100, 92, 112)),
    "white": ((150, 146, 156), (196, 192, 200), (222, 220, 228), (240, 238, 244), (252, 252, 254)),
    "straw": ((92, 70, 36), (134, 104, 56), (170, 136, 76), (200, 168, 100), (226, 200, 134)),
    "brass": ((90, 60, 22), (150, 108, 40), (196, 152, 64), (228, 196, 100), (250, 232, 160)),
    "red": ((78, 30, 32), (116, 46, 46), (146, 64, 60), (172, 90, 80), (196, 122, 108)),
    "black": ((10, 10, 14), (20, 20, 26), (32, 32, 40), (48, 48, 58), (66, 66, 78)),
}
hm.RAMPS.update(P)
# The petals (hm.petal paints from hm.PINK) quieter here than the mini-bosses': on a common enemy the sakura is an
# accent, and RuneForge keeps its common enemies low in saturation.
hm.PINK = ((128, 64, 86), (176, 104, 124), (210, 146, 160), (232, 186, 194), (246, 222, 224))
EYE = (255, 168, 196)       # a self-lit pin of pink in the socket
EYE_DIM = (70, 34, 46)      # the socket round it, dark
CORE = (255, 228, 150)      # a flower's middle


def rgba(mat, lvl, a=255):
    return (*P[mat][max(0, min(4, int(round(lvl))))], a)


def _grain(x, y, salt):
    """A fixed pixel pattern in [0, 1): the painted grain of a face (the same face size paints the same)."""
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h = (h ^ (h >> 13)) * 1274126177 & 0xffffffff
    return (h & 0xffff) / 65536.0


_PANELS = [0]


def panel(mat, face=None, lenticels=False, bands=0, key=""):
    """A face painted the way RuneForge's common enemies are (measured 2026-11-07 against its pack: no dark rim — a
    face's edge is as bright as its middle — and half the neighbouring pixels differ): a volume ramp, lighter at the
    top and in the middle, darker toward the bottom where it meets the next part; a grain of the neighbouring tones
    over it. The up face lighter, the down face darker. face(x, y, w, h) -> rgba | None draws on the front (south).
    lenticels: cherry bark's short pale dashes; bands: a darker groove every `bands` rows (joints, rope twists)."""
    _PANELS[0] += 1
    part = _PANELS[0]       # each part its own grain (RuneForge paints every part; none shares a face)

    def painter(side, w, h):
        salt = sum(map(ord, mat + side + key)) + part * 131
        def paint(x, y, w_, hh):
            if side == "south" and face is not None:
                out = face(x, y, w_, hh)
                if out is not None:
                    return out
            base = {"up": 2.9, "down": 1.2}.get(side, 2.2 if side in ("south", "north") else 1.9)
            if min(w_, hh) <= 1:
                return rgba(mat, base)
            # the volume: down the face it darkens (the occlusion where it meets the part below), and it bulges
            # lighter in the middle across it
            t = y / max(1, hh - 1) if side not in ("up", "down") else .5
            across = 1 - abs(2 * x / max(1, w_ - 1) - 1)
            lvl = base + .55 - 1.25 * t * t + .35 * across
            if side == "up":
                lvl = base + .3 * across + .3 * (1 - abs(2 * y / max(1, hh - 1) - 1)) - .2
            # the grain: patches of 2 px a step up or down (worn spots, not single-pixel noise), a few single pixels
            g = _grain(x // 2, (y + (x // 2) % 2) // 2, salt)
            lvl += .85 if g < .13 else -.85 if g > .85 else 0
            g1 = _grain(x, y, salt + 7)
            lvl += .7 if g1 < .04 else -.7 if g1 > .96 else 0
            if bands and y % bands == bands - 1:
                lvl -= 1.1
            if lenticels and side not in ("up", "down"):
                row = y % 4
                if row == 2 and (x + (y // 4) * 3) % 6 in (1, 2):
                    lvl = 3.6
                elif row == 3 and (x + (y // 4) * 3) % 6 in (1, 2):
                    lvl = .6
            # the hue: patches lean warm or cool (judge of 2026-11-07: RuneForge's brown and green waver between warm
            # and cool tones, ours read as one colour)
            r, g_, bl, a = rgba(mat, lvl)
            hue = _grain(x // 3, y // 2, salt + 31)
            if hue < .22:
                r, g_, bl = min(255, r + 10), min(255, g_ + 3), max(0, bl - 8)
            elif hue > .78:
                r, g_, bl = max(0, r - 8), g_, min(255, bl + 10)
            return (r, g_, bl, a)
        return paint, ("panel2", mat, key, part, lenticels, bands, side, w, h)
    return painter


def rings(mat="wood"):
    """A sawn end: growth rings on the up face, bark round the sides."""
    def painter(side, w, h):
        def paint(x, y, w_, hh):
            if side == "up":
                d = max(abs(x - (w_ - 1) / 2), abs(y - (hh - 1) / 2))
                if d >= min(w_, hh) / 2 - 1:
                    return rgba("bark", 1)
                return rgba(mat, 3 if int(d) % 2 else 2)
            return panel("bark", lenticels=True)(side, w, h)[0](x, y, w_, hh)
        return paint, ("rings", mat, side, w, h)
    return painter


def eyes(*boxes, mouth=(), dark="black"):
    """Front-face features: each eye box a dark socket (deepest at the bottom) with one lit pixel; mouths dark."""
    def face(x, y, w, h):
        for x0, y0, x1, y1 in boxes:
            if x0 <= x < x1 and y0 <= y < y1:
                if x == (x0 + x1 - 1) // 2 and y == y1 - 1:
                    return (*EYE, SELF_LIT)
                return rgba(dark, 1 if y == y0 else 0) if y > y0 or x1 - x0 <= 2 else (*EYE_DIM, 255)
        for x0, y0, x1, y1 in mouth:
            if x0 <= x < x1 and y0 <= y < y1:
                return rgba(dark, 0 if y > y0 else 1)
        return None
    return face


def socket_face(x, y, w, h):
    """The skull's front, painted in shapes: a lit brow, two sockets 3 px wide (rim mid-dark, the hollow darkest,
    narrowing at the bottom) each with a pin of pink, a dark nose notch, the cheekbones lit."""
    left, right = range(1, 4), range(w - 4, w - 1)
    if y == 1 and 1 <= x <= w - 2:
        return rgba("bone", 3.6)
    for xs in (left, right):
        mid = xs[1]
        if y == 2 and x in xs:
            return rgba("bone", .4)
        if y == 3 and x in xs:
            return (*EYE, SELF_LIT) if x == mid else rgba("black", 1)
        if y == 4 and x == mid:
            return rgba("black", 2)
        if y == 4 and x in xs:
            return rgba("bone", .8)
    if y == 5 and x in (w // 2 - 1, w // 2):
        return rgba("bone", 0)
    if y == 5 and x in (1, w - 2):
        return rgba("bone", 3.4)
    return None


def teeth_face(x, y, w, h):
    return rgba("bone", 0) if y == 0 and x % 2 == 1 else None


def shide():
    """紙垂: a white paper streamer, cut in a zigzag (a plane, drawn on its two big faces)."""
    def painter(side, w, h):
        def paint(x, y, w_, hh):
            if side not in ("north", "south"):
                return (0, 0, 0, 0)
            # three steps down, each shifted half the width: the folded zigzag of a real 紙垂
            step = max(2, hh // 3)
            k = min(2, y // step)
            left = (k % 2) * (w_ // 3)
            if not (left <= x < left + w_ - w_ // 3):
                return (0, 0, 0, 0)
            return rgba("white", 3 if y % step == 0 else 2)
        return paint, ("shide", side, w, h)
    return painter


def leaf(mat, key="leaf"):
    """A pointed sepal / leaf plate with a midrib, tip at texture row 0."""
    def painter(side, w, h):
        def paint(x, y, w_, hh):
            if side not in ("north", "south"):
                return (0, 0, 0, 0)
            u = (x + .5) / w_ * 2 - 1
            v = 1 - (y + .5) / hh
            half = math.sin(math.pi * min(1, v * .95 + .05)) ** .7
            if abs(u) > half:
                return (0, 0, 0, 0)
            if abs(u) < 1.0 / w_ + .02:
                return rgba(mat, 4)
            return rgba(mat, 1 if abs(u) > half - 2.0 / w_ else 2 + (1 if u < 0 else 0))
        return paint, (key, mat, side, w, h)
    return painter


def lit(mat_rgb):
    return mat_rgb   # a tuple: self-lit flat colour (Builder.glow)


def builder(name, k):
    """hm.Builder on a 256 px atlas (a common enemy needs no more; the mini-bosses' 1024 is mostly empty)."""
    b = Builder(name, k)
    b.m = hm.Model(name, size=256)
    return b


# --- parts ------------------------------------------------------------------------------------------
def box(b, bone, lo, hi, mat, **kw):
    b.box(bone, lo, hi, mat if not isinstance(mat, str) else panel(mat, **kw))


def cbox(b, bone, c, size, mat, rot=None, **kw):
    b.cbox(bone, c, size, mat if not isinstance(mat, str) else panel(mat, **kw), rot=rot)


def seg(b, bone, a, c, w, mat, d=None, over=.4, **kw):
    b.seg(bone, a, c, w, d or w, mat if not isinstance(mat, str) else panel(mat, **kw), over=over)


def proud(lo, hi, by=.3):
    """A band's box: the part's box grown by `by` on the sides (straps, cuffs, collars)."""
    return [v - by for v in lo], [v + by for v in hi]


def flower(b, bone, c, normal, size, spin=0.0, kind="petal", core=True, up=(0, 1, 0), layers=1):
    """Five notched petals round c across normal and the yellow middle; layers=2 adds an inner, paler five, turned."""
    for k in range(5):
        b.petal(bone, c, normal, spin + k * 72, size, size * .8, kind=kind, up_hint=up, r0=size * .12)
    if layers > 1:
        n = hm.norm(normal)
        for k in range(5):
            b.petal(bone, add(c, n, .3), normal, spin + 36 + k * 72, size * .6, size * .5, kind="pale", up_hint=up, r0=size * .08)
    if core:
        n = hm.norm(normal)
        b.cbox(bone, add(c, n, .5), (size * .36, size * .36, size * .36), CORE, name="core")


def buds(b, bone, points, size=1.4):
    """Closed buds: small pink cubes on short stalks."""
    for p in points:
        b.cbox(bone, p, (size, size * 1.3, size), panel("bud"), rot=(_h(*p, 1) * 30, _h(*p, 2) * 60, 0))


def sprig(b, bone, a, tip, w=1.4, flowers=2, size=2.6, seed=0, bud=1):
    """A twig from a to tip, kinked once, with blossoms at and near its tip and a bud or two."""
    mid = add(hm.lerp(a, tip, .5), ((_h(seed, 1) - .5) * 2, 0, (_h(seed, 2) - .5) * 2))
    seg(b, bone, a, mid, w, "bark")
    seg(b, bone, mid, tip, w * .8, "bark")
    for j in range(flowers):
        p = hm.lerp(mid, tip, 1 - j * .45)
        p = add(p, ((_h(j, seed, 1) - .5) * 2, (_h(j, seed, 2) - .2) * 1.5, (_h(j, seed, 3) - .5) * 2))
        flower(b, bone, p, hm.norm(((_h(j, seed, 4) - .5), .8, (_h(j, seed, 5) - .2))), size * (1 - .2 * j), spin=j * 31 + seed * 17,
               kind="pale" if j % 2 else "petal")
    buds(b, bone, [add(tip, ((_h(seed, k, 7) - .5) * 3, 1 + _h(seed, k, 8), (_h(seed, k, 9) - .5) * 3)) for k in range(bud)])


def shimenawa(b, bone, c, half_w, half_d, y, thick=1.6, tails=3, at=None):
    """注連縄: a twisted straw rope round a body (four bars, banded), with paper streamers hanging off the front."""
    t = thick
    box(b, bone, (c[0] - half_w - t / 2, y, c[2] + half_d - t / 2), (c[0] + half_w + t / 2, y + t, c[2] + half_d + t / 2), "straw", bands=2)
    box(b, bone, (c[0] - half_w - t / 2, y, c[2] - half_d - t / 2), (c[0] + half_w + t / 2, y + t, c[2] - half_d + t / 2), "straw", bands=2)
    for s in (1, -1):
        box(b, bone, (c[0] + s * half_w - t / 2, y, c[2] - half_d), (c[0] + s * half_w + t / 2, y + t, c[2] + half_d), "straw", bands=2)
    for x in at or [c[0] + (k - (tails - 1) / 2) * (2 * half_w / max(1, tails)) for k in range(tails)]:
        b.plate(bone, (x, y - 2.8, c[2] + half_d + t / 2 + .1), (0, 0, 1), (0, 1, 0), (1.8, 5.4), shide(), thick=.2, name="shide")


def hurt_clip(b, keys):
    """被弾: a quick flinch back and settle (0.35 s), played over the loop when hit."""
    bake(b, "hurt", .35, [(0, {}, "lin"), (.08, keys, "out"), (.35, {}, "smooth")])


def bake(b, name, length, keys, loop="once", overlay=None, springs=None):
    """hm.bake, with every channel any key uses present from the first frame (at 0 where the first key leaves it
    out): a track that only starts mid-clip would otherwise hold its later value from the clip's start."""
    used = {k for _, pose, _ in keys for k in pose}
    keys = [(keys[0][0], {**{k: (0, 0, 0) for k in used}, **keys[0][1]}, keys[0][2])] + list(keys[1:])
    hm.bake(b, name, length, keys, loop, overlay, springs)


def limb(b, bone, top, length, w, mat, knob=None, knob_mat=None, cuff=None, cuff_mat="straw", **kw):
    """A limb hanging from top (x, y, z): the shaft w thick, a knob at the joint, a band (cuff) near its end."""
    x, y, z = top
    box(b, bone, (x - w / 2, y - length, z - w / 2), (x + w / 2, y, z + w / 2), mat, **kw)
    if knob:
        cbox(b, bone, (x, y - knob[0], z), (knob[1], knob[1], knob[1]), knob_mat or mat)
    if cuff:
        lo, hi = proud((x - w / 2, y - cuff[0] - cuff[1], z - w / 2), (x + w / 2, y - cuff[0], z + w / 2))
        box(b, bone, lo, hi, cuff_mat, bands=2)


# ======================================================================================================
# 1 桜木の亡者 (sakura_husk) — zombie: a dead cherry trunk that walks, arms held out, its crown in bloom
# ======================================================================================================
HUSK = {"husk-grab": (.75, .55), "husk-swipe": (.6, .5)}


def build_husk():
    b = builder("sakura_husk", 1.0)
    body = b.bone("hk_body", None, (0, 12, 0))
    box(b, body, (-4, 12, -2), (4, 24, 2), "bark", lenticels=True)
    # the trunk splits at the chest: two burls and a hollow where the heart would be, lit pink
    cbox(b, body, (-2, 19.5, 2.2), (3, 3, 1.2), "bark")
    cbox(b, body, (2.4, 15, 2.1), (2.4, 2.4, 1), "bark")
    b.cbox(body, (-.2, 21.5, 2.05), (1.6, 2, .2), EYE, name="glow_heart")
    shimenawa(b, body, (0, 0, 0), 4, 2, 12.6, thick=1.5, tails=2)
    # a broken branch out of the left shoulder, in flower
    sprig(b, body, (3, 23, 0), (8.5, 30, -1.5), w=1.8, flowers=2, size=3.2, seed=4, bud=2)
    top = b.bone("hk_top", body, (0, 24, 0))
    box(b, top, (-4, 24, -4), (4, 31, 4), "bark", lenticels=True,
        face=eyes((1, 2, 3, 4), (5, 2, 7, 4), mouth=((2, 5, 6, 6), (3, 6, 5, 7))), key="hkface")
    # the head is a sawn stump: rings on top, a lip of bark round the cut, blossoms bursting out of the middle
    box(b, top, (-4.3, 30.4, -4.3), (4.3, 31.8, 4.3), rings())
    box(b, top, (-1.6, 31.8, -1.6), (1.6, 32.6, 1.6), "bark")
    flower(b, top, (-1.4, 33.4, .6), (0, 1, .25), 3.8, spin=10, layers=2)
    flower(b, top, (2.6, 33.2, -1.6), (.45, 1, -.2), 3.0, spin=40, kind="pale")
    flower(b, top, (-2.8, 32.6, -2.6), (-.5, 1, -.4), 2.6, spin=70, kind="deep")
    buds(b, top, [(1.2, 33.6, 2.4), (-3.2, 33, 1.8), (3.4, 32.4, 1.6)])
    seg(b, top, (1, 32, 1), (2.6, 36.5, 2.2), 1, "bark")
    for s, side in ((1, "l"), (-1, "r")):
        arm = b.bone(f"hk_arm_{side}", body, (5 * s, 22, 0))
        # held out in front, as a zombie's: the upper arm, a knot at the elbow, the forearm, twig fingers
        box(b, arm, (4 * s, 20, -2), (8 * s, 24, 4), "bark", lenticels=True)
        cbox(b, arm, (6 * s, 22, 4.4), (4.6, 4.6, 2.2), "bark")
        box(b, arm, (4.4 * s, 20.4, 4.6), (7.6 * s, 23.6, 10), "bark", lenticels=True)
        lo, hi = proud((4.4 * s, 20.4, 8), (7.6 * s, 23.6, 9.4))
        box(b, arm, lo, hi, "straw", bands=2)
        for k, dy in ((-1, .4), (0, 1.6), (1, .6)):
            seg(b, arm, (6 * s + k * 1.1, 21 + dy, 10), (6 * s + k * 1.7, 21 + dy - .5, 13), .9, "bark", over=.3)
        leg = b.bone(f"hk_leg_{side}", body, (2 * s, 12, 0))
        box(b, leg, (0, 1, -2), (4 * s, 12, 2), "bark", lenticels=True)
        cbox(b, leg, (2 * s, 6.5, 2.2), (3, 3, 1), "bark")
        # the foot is a spread of roots
        box(b, leg, (-.2 * s, 0, -2.2), (4.2 * s, 1.4, 3.4), "bark")
        for k in (-1, 1):
            seg(b, leg, (2 * s + k * 1.2, .7, 3), (2 * s + k * 2.4, 0, 5.2), 1.2, "bark", over=.3)
    b.tree = b.outliner()

    def shamble(t):
        s = math.sin(2 * math.pi * t / 1.2)
        return {"hk_leg_l": (30 * s, 0, 0), "hk_leg_r": (-30 * s, 0, 0), "hk_arm_l": (6 * s, 0, 0), "hk_arm_r": (-6 * s, 0, 0),
                "hk_body": (4, 0, 2.5 * s), "hk_top": (0, 6 * s, -3 * s), "hk_body@pos": (0, .4 * abs(s), 0)}
    bake(b, "walk", 1.2, [(0, {}, "lin"), (1.2, {}, "lin")], "loop", overlay=shamble)

    def sway(t):
        return {"hk_arm_l": (3 * wave(t, 3.0), 0, 0), "hk_arm_r": (3 * wave(t, 3.0, .5), 0, 0), "hk_top": (0, 0, 4 * wave(t, 3.0, .2)),
                "hk_body": (2, 0, 1.5 * wave(t, 3.0))}
    bake(b, "idle", 3.0, [(0, {}, "lin"), (3.0, {}, "lin")], "loop", overlay=sway)

    # 枝の掴み: arms raised high and the crown thrown back, then both arms crash down onto whoever is in front.
    tl, rc = HUSK["husk-grab"]
    up = {"hk_arm_l": (-70, 0, -10), "hk_arm_r": (-70, 0, 10), "hk_top": (-18, 0, 0), "hk_body": (-8, 0, 0)}
    down = {"hk_arm_l": (35, 0, 0), "hk_arm_r": (35, 0, 0), "hk_top": (14, 0, 0), "hk_body": (16, 0, 0), "hk_body@pos": (0, -1, 2)}
    bake(b, "husk-grab", tl + rc, [(0, {}, "lin"), (tl * .7, up, "smooth"), (tl, down, "in"), (tl + .15, merged(down, {"hk_arm_l": (-6, 0, 0)}), "out"),
                                   (tl + rc, {}, "smooth")])
    # 薙ぎ払い: the trunk twists away with the right arm drawn back wide, then the arm sweeps across in front.
    tl, rc = HUSK["husk-swipe"]
    wind = {"hk_body": (0, 35, 0), "hk_arm_r": (-20, 55, 0), "hk_arm_l": (10, 0, 0), "hk_top": (0, -15, 0)}
    sweep = {"hk_body": (6, -35, 0), "hk_arm_r": (-5, -70, 0), "hk_arm_l": (15, 0, 0), "hk_top": (0, 20, 0), "hk_body@pos": (0, 0, 1.5)}
    bake(b, "husk-swipe", tl + rc, [(0, {}, "lin"), (tl * .75, wind, "smooth"), (tl, sweep, "in"), (tl + .15, merged(sweep, {"hk_arm_r": (0, -8, 0)}), "out"),
                                    (tl + rc, {}, "smooth")])
    hurt_clip(b, {"hk_body": (-10, 0, 0), "hk_top": (-12, 0, 0), "hk_arm_l": (-15, 0, 0), "hk_arm_r": (-15, 0, 0)})
    # 倒れる: sags at the knees and topples on its side.
    sag = {"hk_body": (10, 0, 0), "hk_body@pos": (0, -3, 0), "hk_arm_l": (40, 0, 0), "hk_arm_r": (40, 0, 0), "hk_top": (20, 0, 0)}
    fall = {"hk_body": (0, 0, 88), "hk_body@pos": (-4, -9, 0), "hk_arm_l": (20, 0, 0), "hk_arm_r": (50, 0, 0), "hk_top": (10, 0, 0)}
    bake(b, "death", 1.2, [(0, {}, "lin"), (.35, sag, "out"), (.9, fall, "in"), (1.2, merged(fall, {"hk_body@pos": (0, .6, 0)}), "out")])
    return b


# ======================================================================================================
# 2 花ぐも (blossom_spider) — spider: the abdomen a closed bloom, eight jointed legs of twig
# ======================================================================================================
SPIDER = {"spider-leap": (.7, .6)}


def build_spider():
    b = builder("blossom_spider", 1.0)
    body = b.bone("sp_body", None, (0, 7, 0))
    box(b, body, (-3, 4.5, -3), (3, 10.5, 3), "bark", lenticels=True)
    lo, hi = proud((-3, 6.5, -3), (3, 8, 3))
    box(b, body, lo, hi, "straw", bands=2)
    hd = b.bone("sp_face", body, (0, 7.5, 3))
    box(b, hd, (-4, 4, 3), (4, 11, 9), "bark",
        face=eyes((1, 1, 3, 3), (5, 1, 7, 3), (0, 4, 1, 5), (7, 4, 8, 5), (2, 4, 3, 5), (5, 4, 6, 5)), key="spface")
    box(b, hd, (-4.3, 10.4, 2.7), (4.3, 11.6, 9.3), "bark")
    for s in (1, -1):
        # fangs: short twig mandibles tipped with a petal
        seg(b, hd, (1.8 * s, 4.6, 8.6), (1.2 * s, 2, 10.4), 1.2, "bark")
        b.petal(hd, (1.2 * s, 2.2, 10.4), (0, 0, 1), 180, 2, 1.6, kind="deep")
    # the abdomen: a closed bud of five big petals in two layers, green sepals where it joins, a bloom at its tip
    ab = b.bone("sp_bloom", body, (0, 8, -3))
    box(b, ab, (-4, 4.5, -13), (4, 11.5, -3), "bud")
    for k in range(5):
        a = math.radians(k * 72 + 36)
        n = (math.cos(a), math.sin(a), 0)
        b.plate(ab, (math.cos(a) * 4.3, 8 + math.sin(a) * 4.3, -8.5), n, (0, 0, -1), (7, 11), hm.petal("petal"), thick=1, name="petal")
        a2 = a + math.radians(36)
        b.plate(ab, (math.cos(a2) * 3.4, 8 + math.sin(a2) * 3.4, -9), (math.cos(a2), math.sin(a2), 0), (0, 0, -1), (5, 9), hm.petal("deep"), thick=1, name="petal")
        a3 = math.radians(k * 72)
        b.plate(ab, (math.cos(a3) * 4.2, 8 + math.sin(a3) * 4.2, -3.6), (math.cos(a3), math.sin(a3), -.6), (0, 0, -1), (3.4, 5), leaf("sepal"), thick=1, name="sepal")
    flower(b, ab, (0, 8, -13.6), (0, 0, -1), 3.2, spin=36, kind="pale")
    for s, side in ((1, "l"), (-1, "r")):
        for i, z in enumerate((2, .5, -1, -2.5)):
            leg = b.bone(f"sp_leg_{side}{i}", body, (3 * s, 8, z))
            a = math.radians((i - 1.5) * 22)
            hip = (3.4 * s, 8, z)
            knee = (3 * s + 7 * s * math.cos(a), 12.5, z + 7 * math.sin(a))
            foot = (3 * s + 12 * s * math.cos(a), 0, z + 12 * math.sin(a))
            seg(b, leg, hip, knee, 1.6, "bark")
            cbox(b, leg, knee, (2.4, 2.4, 2.4), "bark")
            seg(b, leg, knee, foot, 1.2, "bark")
            b.cbox(leg, hm.lerp(knee, foot, .55), (1.6, 1.0, 1.6), panel("straw", bands=2))
            if i in (0, 2):
                b.petal(leg, add(knee, (0, 1, 0)), (0, 1, 0), 20 * s, 2.2, 2, kind="pale", up_hint=(0, 0, 1))
    b.tree = b.outliner()

    def scuttle(t, period=.5, amp=18):
        out = {"sp_body@pos": (0, .5 * abs(wave(t, period / 2)), 0), "sp_bloom": (0, 4 * wave(t, period), 0)}
        for s in "lr":
            for i in range(4):
                ph = (i % 2) * .5 + (0 if s == "l" else .5)
                sgn = 1 if s == "l" else -1
                out[f"sp_leg_{s}{i}"] = (0, amp * wave(t, period, ph), sgn * 10 * max(0.0, wave(t, period, ph + .25)))
        return out
    bake(b, "walk", .5, [(0, {}, "lin"), (.5, {}, "lin")], "loop", overlay=scuttle)

    def breathe(t):
        return {"sp_bloom@scale": (.04 * wave(t, 2.0), .04 * wave(t, 2.0), 0), "sp_face": (3 * wave(t, 2.0), 0, 0),
                "sp_leg_l0": (0, 4 * wave(t, 2.0, .3), 0), "sp_leg_r0": (0, -4 * wave(t, 2.0, .3), 0)}
    bake(b, "idle", 2.0, [(0, {}, "lin"), (2.0, {}, "lin")], "loop", overlay=breathe)

    # 飛びかかり: crouches with the front legs raised and the bloom shivering open, then springs at the prey.
    tl, rc = SPIDER["spider-leap"]
    crouch = {"sp_body": (-22, 0, 0), "sp_body@pos": (0, -2, -1), "sp_leg_l0": (0, 0, 55), "sp_leg_r0": (0, 0, -55),
              "sp_leg_l1": (0, 0, 30), "sp_leg_r1": (0, 0, -30), "sp_bloom@scale": (.18, .18, -.05)}
    leap = {"sp_body": (14, 0, 0), "sp_body@pos": (0, 5, 10), "sp_leg_l0": (0, -30, -10), "sp_leg_r0": (0, 30, 10),
            "sp_leg_l1": (0, -20, -10), "sp_leg_r1": (0, 20, 10), "sp_bloom@scale": (.08, .08, 0)}
    land = {"sp_body": (6, 0, 0), "sp_body@pos": (0, -1, 12)}
    bake(b, "spider-leap", tl + rc, [(0, {}, "lin"), (tl * .75, crouch, "smooth"), (tl, leap, "in"), (tl + .2, land, "out"),
                                     (tl + rc, {}, "smooth")])
    hurt_clip(b, {"sp_body": (-12, 0, 0), "sp_body@pos": (0, 1, -1), "sp_bloom@scale": (-.1, -.1, 0)})
    curl = {f"sp_leg_{s}{i}": (0, 0, (-60 if s == "l" else 60)) for s in "lr" for i in range(4)}
    flip = merged(curl, {"sp_body": (0, 0, 180), "sp_body@pos": (0, 6, 0), "sp_bloom@scale": (-.25, -.25, -.1)})
    bake(b, "death", 1.0, [(0, {}, "lin"), (.25, merged(curl, {"sp_body@pos": (0, 5, 0)}), "out"), (.7, flip, "in"), (1.0, merged(flip, {"sp_body@pos": (0, -1, 0)}), "out")])
    return b


# ======================================================================================================
# 3 つぼみ (bud_creeper) — creeper: a tall shut bud in green sepals; it swells and bursts open into a flower
# ======================================================================================================
BUD = {"bud-burst": (1.5, .3), "bud-pollen": (.8, .5)}


def build_bud():
    b = builder("bud_creeper", 1.0)
    body = b.bone("bd_body", None, (0, 6, 0))
    # the stem, its creeper face under the bud; two ridges down its sides and a leaf on a stalk
    box(b, body, (-4, 6, -2.5), (4, 18, 2.5), "sepal",
        face=eyes((1, 1, 3, 3), (5, 1, 7, 3), mouth=((3, 3, 5, 6), (2, 4, 3, 7), (5, 4, 6, 7))), key="bdface")
    for s in (1, -1):
        box(b, body, (4 * s - .3 * s, 7, -1), (4 * s + .7 * s, 17, 1), "sepal")
    seg(b, body, (-4.5, 11, 0), (-7.5, 13.5, .5), 1, "sepal")
    b.plate(body, (-9, 15, .5), (0, 0, 1), (-.4, 1, 0), (4, 6), leaf("sepal"), thick=.6, name="leaf")
    lo, hi = proud((-4, 16.6, -2.5), (4, 18, 2.5))
    box(b, body, lo, hi, "sepal", bands=2)
    bud = b.bone("bd_bud", body, (0, 18, 0))
    box(b, bud, (-4, 18, -4), (4, 26, 4), "bud")
    # the tip twisted shut: three smaller boxes, each turned further
    for k, (y, s) in enumerate(((26, 6), (28, 4.2), (29.6, 2.4))):
        cbox(b, bud, (0, y + .9, 0), (s, 1.8, s), "bud", rot=(0, 15 * (k + 1), 0))
    for k in range(5):
        a = math.radians(k * 72 + 126)   # no petal straight in front
        p = b.bone(f"bd_petal{k}", bud, (math.cos(a) * 3.6, 19, math.sin(a) * 3.6))
        b.plate(p, (math.cos(a) * 4.4, 24, math.sin(a) * 4.4), (math.cos(a), 0, math.sin(a)), (0, 1, 0), (6, 13), hm.petal("petal"), thick=1, name="petal")
        b.plate(p, (math.cos(a) * 4.0, 23, math.sin(a) * 4.0), (math.cos(a), 0, math.sin(a)), (0, 1, 0), (3, 9), hm.petal("deep"), thick=.4, name="vein")
    sep = b.bone("bd_sepals", body, (0, 18, 0))
    for k in range(5):
        a = math.radians(k * 72 + 90)
        b.plate(sep, (math.cos(a) * 4.6, 20, math.sin(a) * 4.6), (math.cos(a), -.25, math.sin(a)), (0, 1, 0), (4.5, 8), leaf("sepal"), thick=1, name="sepal")
    for i, (x, z) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        leg = b.bone(f"bd_leg{i}", body, (2 * x, 6, 2 * z))
        z0, z1 = (2 * z, 2 * z + 4) if z > 0 else (2 * z - 4, 2 * z)
        box(b, leg, (2 * x - 2, 1, z0), (2 * x + 2, 6, z1), "sepal")
        box(b, leg, (2 * x - 2.3, 0, z0 - .3 + (1 if z > 0 else 0)), (2 * x + 2.3, 1.4, z1 + .3 + (1 if z > 0 else 0)), "bark")
    b.tree = b.outliner()

    def pad(t):
        s = math.sin(2 * math.pi * t / .8)
        return {"bd_leg0": (25 * s, 0, 0), "bd_leg3": (25 * s, 0, 0), "bd_leg1": (-25 * s, 0, 0), "bd_leg2": (-25 * s, 0, 0),
                "bd_bud": (0, 0, 3 * s), "bd_body@pos": (0, .3 * abs(s), 0)}
    bake(b, "walk", .8, [(0, {}, "lin"), (.8, {}, "lin")], "loop", overlay=pad)

    def still(t):
        return {"bd_bud": (2 * wave(t, 2.6), 0, 2 * wave(t, 2.6, .25)), "bd_sepals@scale": (.03 * wave(t, 2.6), 0, .03 * wave(t, 2.6))}
    bake(b, "idle", 2.6, [(0, {}, "lin"), (2.6, {}, "lin")], "loop", overlay=still)

    # 開花（自爆）: it stops, swells in pulses with the sepals peeling back, then bursts — every petal flung open flat.
    tl, rc = BUD["bud-burst"]

    def throb(t):
        f = min(1.0, t / tl)
        p = (.5 + .5 * math.sin(t * (10 + 18 * f))) * f
        return {"bd_bud@scale": (.22 * p, .12 * p, .22 * p), "bd_body@scale": (.08 * p, 0, .08 * p)} if t < tl else {}
    peel = merged({"bd_sepals@scale": (.4, -.2, .4)}, {f"bd_petal{k}@scale": (.15, .1, .15) for k in range(5)})
    burst = {"bd_sepals@scale": (.6, -.5, .6), "bd_bud@scale": (.5, -.35, .5), "bd_body@pos": (0, -1.5, 0)}
    for k in range(5):
        a = k * 72 + 126
        burst[f"bd_petal{k}"] = (-80 * math.sin(math.radians(a)), 0, 80 * math.cos(math.radians(a)))
        burst[f"bd_petal{k}@scale"] = (.6, .5, .6)
    bake(b, "bud-burst", tl + rc, [(0, {}, "lin"), (tl * .6, peel, "smooth"), (tl - .05, peel, "lin"), (tl + .12, burst, "out"), (tl + rc, burst, "hold")],
         overlay=throb)
    # 花粉: the bud rears back and swells a little, then nods sharply forward and puffs pollen ahead (petals part).
    tl2, rc2 = BUD["bud-pollen"]
    rear = merged({"bd_bud": (-28, 0, 0), "bd_bud@scale": (.1, .08, .1), "bd_body": (-6, 0, 0)}, {f"bd_petal{k}@scale": (.08, 0, .08) for k in range(5)})
    nod = merged({"bd_bud": (32, 0, 0), "bd_bud@scale": (-.04, -.04, -.04), "bd_body": (8, 0, 0)}, {f"bd_petal{k}@scale": (.2, .1, .2) for k in range(5)})
    bake(b, "bud-pollen", tl2 + rc2, [(0, {}, "lin"), (tl2 * .75, rear, "smooth"), (tl2, nod, "in"), (tl2 + .15, merged(nod, {"bd_bud": (-6, 0, 0)}), "out"),
                                      (tl2 + rc2, {}, "smooth")])
    # 散る (after the burst, played by the server as its last clip): the open flower lies a moment, then fades away.
    gone = merged(burst, {"bd_body@scale": (-.97, -.97, -.97)})
    bake(b, "spent", .9, [(0, burst, "lin"), (.3, burst, "hold"), (.9, gone, "in")])
    hurt_clip(b, {"bd_bud": (-14, 0, 0), "bd_bud@scale": (-.08, -.08, -.08)})
    wilt = merged({"bd_bud": (40, 0, 10), "bd_body@pos": (0, -2, 0), "bd_sepals@scale": (.3, -.3, .3)},
                  {f"bd_petal{k}@scale": (-.5, -.5, -.5) for k in range(5)})
    bake(b, "death", 1.0, [(0, {}, "lin"), (.6, wilt, "smooth"), (1.0, merged(wilt, {"bd_body": (0, 0, 80), "bd_body@pos": (3, -3, 0)}), "in")])
    return b


# ======================================================================================================
# 4 枝弓の骸 (branch_archer) — skeleton: separate ribs and spine, a bow of bent cherry branch blooming at both tips
# ======================================================================================================
ARCHER = {"archer-shot": (1.0, .5), "archer-bash": (.6, .6)}


def build_archer():
    b = builder("branch_archer", 1.0)
    body = b.bone("ar_body", None, (0, 12, 0))
    # spine, four ribs (each a bar, the gaps open), the collarbone, the pelvis
    box(b, body, (-1, 12, -1.6), (1, 24, .4), "bone", bands=3)
    for k, y in enumerate((21.5, 18.8, 16.1)):
        w = 4 - k * .4
        box(b, body, (-w, y, -1.8), (w, y + 1.8, .2), "bone")
        for s in (1, -1):
            box(b, body, (s * w - (1.8 if s > 0 else 0), y, .2), (s * w + (0 if s > 0 else 1.8), y + 1.8, 2), "bone")
        box(b, body, (-w + 1.8, y, .2), (w - 1.8, y + 1.2, 2), "bone")
    box(b, body, (-4.4, 23.2, -2), (4.4, 24.6, 1), "bone")
    box(b, body, (-3.6, 11.6, -2), (3.6, 13.6, 2), "bone")
    # a leather strap across the chest with a brass buckle; the quiver on the back
    b.seg(body, (-3.8, 13.5, 2.3), (3.6, 23.6, 2.3), 1.4, .6, panel("wood"), over=.4)
    b.seg(body, (-3.8, 13.5, -2.3), (3.6, 23.6, -2.3), 1.4, .6, panel("wood"), over=.4)
    cbox(b, body, (0, 18.6, 2.7), (1.8, 1.8, .8), "brass")
    seg(b, body, (-2, 13, -3.6), (3, 24.5, -4.2), 3.2, "wood", bands=4)
    lo, hi = proud((-1.5, 23, -5.6), (1.5, 24.4, -2.6))
    for k in range(3):
        # twig arrows fletched with petals
        a = (-1.3 + k * 1.5, 24, -3.6 - k * .3)
        tip = (-1.6 + k * 2, 28.4 + k * .5, -4.4)
        seg(b, body, a, tip, .7, "bark")
        b.petal(body, tip, (1, 0, 0), 0, 2.2, 1.6, kind="pale" if k % 2 else "petal", up_hint=(0, 1, 0))
    top = b.bone("ar_top", body, (0, 24, 0))
    box(b, top, (-4, 26, -3.5), (4, 32, 3.5), "bone", face=socket_face, key="skull")
    box(b, top, (-3, 24.6, -2.5), (3, 26.4, 3.2), "bone", face=teeth_face, key="jaw")
    box(b, top, (-1, 24, -1), (1, 25, 1), "bone")
    # a red hachimaki round the brow, its tails out behind; a blossom grown from a crack in the crown
    lo, hi = proud((-4, 29.4, -3.5), (4, 30.6, 3.5))
    box(b, top, lo, hi, "red", bands=0)
    for k in (-1, 1):
        b.plate(top, (k * 1, 29, -4.4), (0, -.3, -1), (k * .5, -1, 0), (1.4, 4.4), panel("red"), thick=.3, name="tail")
    flower(b, top, (2, 32.6, .8), (.3, 1, .2), 3.2, spin=20, layers=2)
    seg(b, top, (2, 31.5, .8), (2.3, 32.8, 1), .8, "bark")
    buds(b, top, [(-1.6, 32.6, -1.4)], 1.2)
    for s, side in ((1, "l"), (-1, "r")):
        arm = b.bone(f"ar_arm_{side}", body, (5 * s, 22, 0))
        cbox(b, arm, (5 * s, 22.5, -.6), (3.4, 3.4, 3.4), "bone")
        limb(b, arm, (5 * s, 22, -.6), 11, 3.0, "bone", knob=(6, 3.6), cuff=(8.4, 1.4), cuff_mat="wood")
        leg = b.bone(f"ar_leg_{side}", body, (2 * s, 12, 0))
        limb(b, leg, (2 * s, 12, -.4), 12, 3.0, "bone", knob=(6, 3.6))
        box(b, leg, (2 * s - 1.6, 0, -1.8), (2 * s + 1.6, 1.4, 2.2), "bone")
    # the bow, held in the left hand: a curved branch with blossoms at its tips and a pale string
    bow = b.bone("ar_bow", "ar_arm_l", (5, 12, 0))
    pts = [(5, 12 + 9.5, 1), (5, 12 + 5, 3.2), (5, 12, 3.8), (5, 12 - 5, 3.2), (5, 12 - 9.5, 1)]
    for a, c in zip(pts, pts[1:]):
        seg(b, bow, a, c, 1.4, "bark", over=.6)
    cbox(b, bow, pts[2], (2, 2.6, 2), "straw", bands=2)
    for p in (pts[0], pts[-1]):
        flower(b, bow, p, (0, 0, 1), 2.6, spin=11, kind="pale")
    string = b.bone("ar_string", bow, (5, 12, 0))
    b.seg(string, pts[0], pts[-1], .4, .4, panel("white"))
    b.tree = b.outliner()
    aim = {"ar_arm_l": (-80, 10, 0), "ar_arm_r": (-80, -20, 0), "ar_bow": (80, 0, 0)}

    def clatter(t):
        s = math.sin(2 * math.pi * t / 1.0)
        return {"ar_leg_l": (28 * s, 0, 0), "ar_leg_r": (-28 * s, 0, 0), "ar_body": (6, 4 * s, 0), "ar_top": (0, -4 * s, 2 * s),
                "ar_body@pos": (0, .3 * abs(s), 0)}
    bake(b, "walk", 1.0, [(0, aim, "lin"), (1.0, aim, "lin")], "loop", overlay=clatter)

    def jitter(t):
        return {"ar_top": (0, 6 * wave(t, 2.0), 0), "ar_arm_r": (3 * wave(t, 2.0, .3), 0, 0)}
    bake(b, "idle", 2.0, [(0, aim, "lin"), (2.0, aim, "lin")], "loop", overlay=jitter)

    # 枝矢: draws the string to its jaw (the bow bends, the tip blossoms shiver), holds, and looses with a recoil.
    tl, rc = ARCHER["archer-shot"]
    draw = merged(aim, {"ar_arm_r": (0, -55, 0), "ar_body": (0, -20, 0), "ar_string@pos": (0, 0, -4)})
    loose = merged(aim, {"ar_arm_r": (-10, -90, 0), "ar_body": (-6, -8, 0), "ar_top": (-6, 0, 0)})
    bake(b, "archer-shot", tl + rc, [(0, aim, "lin"), (tl * .55, draw, "smooth"), (tl - .02, merged(draw, {"ar_body": (0, -4, 0)}), "lin"),
                                     (tl + .06, loose, "out"), (tl + rc, aim, "smooth")])
    # 弓打ち: someone too close — it swings the bow round like a club, the blossoms at its tips leading.
    tl, rc = ARCHER["archer-bash"]
    back = merged(aim, {"ar_body": (0, 40, 0), "ar_arm_l": (10, 60, 0), "ar_top": (0, -20, 0)})
    swat = merged(aim, {"ar_body": (8, -40, 0), "ar_arm_l": (20, -60, 0), "ar_top": (0, 15, 0), "ar_body@pos": (0, 0, 1.5)})
    bake(b, "archer-bash", tl + rc, [(0, aim, "lin"), (tl * .75, back, "smooth"), (tl, swat, "in"), (tl + .15, merged(swat, {"ar_arm_l": (0, -8, 0)}), "out"),
                                     (tl + rc, aim, "smooth")])
    hurt_clip(b, merged(aim, {"ar_body": (-10, 0, 0), "ar_top": (-15, 0, 0)}))
    # 崩れる: the bones fall apart — the skull drops and rolls, the limbs scatter, the bow clatters down.
    apart = {"ar_top@pos": (3, -20, 4), "ar_top": (30, 40, 70), "ar_arm_l@pos": (2, -10, 3), "ar_arm_l": (-90, 0, 40),
             "ar_arm_r@pos": (-2, -11, 2), "ar_arm_r": (-80, 0, -50), "ar_body@pos": (0, -9, 0), "ar_body": (60, 0, 10),
             "ar_leg_l": (-80, 0, 30), "ar_leg_r": (-70, 0, -40)}
    bake(b, "death", 1.2, [(0, aim, "lin"), (.15, merged(aim, {"ar_top": (-20, 0, 0)}), "out"), (.8, apart, "in"), (1.2, merged(apart, {"ar_top": (30, 60, 80)}), "out")])
    return b


# ======================================================================================================
# 5 花狛 (komainu_pup) — wolf: a stone guardian dog, a mane of petals, a red bib with a bell, its tail a bloom
# ======================================================================================================
KOMA = {"koma-bite": (.55, .45)}


def build_koma():
    b = builder("komainu_pup", 1.0)
    body = b.bone("km_body", None, (0, 10, 0))
    box(b, body, (-3, 7, -8), (3, 13, 2), "kstone")
    lo, hi = proud((-3, 12.4, -8), (3, 13, 2))
    box(b, body, lo, hi, "moss")
    box(b, body, (-2, 13, -6), (2, 13.6, -2), "kstone", bands=2)
    mane = b.bone("km_mane", body, (0, 11, 2))
    box(b, mane, (-4, 6, 0), (4, 14, 5), "kstone")
    for ring, (rr, z) in enumerate(((5.2, 2.6), (4.4, .6))):
        for k in range(8):
            a = math.radians(k * 45 + ring * 22)
            c = (math.cos(a) * rr, 10 + math.sin(a) * rr, z)
            b.petal(mane, c, (0, 0, 1), k * 45 + ring * 22 - 90, 4.2, 3.4, kind="pale" if (k + ring) % 3 == 0 else "petal", up_hint=(0, 1, 0))
    # the red bib (前掛け) and its brass bell
    b.plate(mane, (0, 6.6, 5.2), (0, -.35, 1), (0, 1, 0), (6, 4), panel("red"), thick=.4, name="bib")
    cbox(b, mane, (0, 5.2, 5.8), (1.8, 1.8, 1.8), "brass")
    face = b.bone("km_face", mane, (0, 11, 4))
    box(b, face, (-3, 9, 4), (3, 14.5, 8.5), "kstone", face=eyes((1, 1, 2, 3), (4, 1, 5, 3)), key="kmface")
    # the brows curl as petals; the snout over an open jaw with fangs
    box(b, face, (-1.8, 10.2, 8.5), (1.8, 12.6, 11.4), "kstone")
    cbox(b, face, (0, 12.4, 11.6), (1.4, 1, 1), "black")
    jaw = b.bone("km_jaw", face, (0, 9.6, 6))
    box(b, jaw, (-1.6, 8.4, 6.5), (1.6, 9.8, 11), "kstone", face=teeth_face, key="kmjaw")
    for s in (1, -1):
        cbox(b, jaw, (1 * s, 10.2, 10.4), (.6, 1, .6), "white")
        box(b, face, (1.2 * s, 14.5, 5), (3.2 * s, 17, 7), "kstone")
        b.petal(face, (2.2 * s, 14.6, 8.6), (0, .3, 1), -40 * s, 2.6, 2.1, kind="deep")
        b.petal(face, (3.4 * s, 11.4, 6.6), (s, 0, .4), 90 * s, 3, 2.4, kind="pale")
    for i, (x, z) in enumerate(((1, 0), (-1, 0), (1, -7), (-1, -7))):
        leg = b.bone(f"km_leg{i}", body, (2 * x, 7, z))
        box(b, leg, (2 * x - 1.2, 1, z - 1.2), (2 * x + 1.2, 8, z + 1.2), "kstone")
        box(b, leg, (2 * x - 1.5, 0, z - 1.2), (2 * x + 1.5, 1.4, z + 2), "kstone")
        b.petal(leg, (2 * x + 1.2 * x, 5.6, z - .6), (x, 0, -.4), 140 * x, 2.4, 2, kind="petal")
    tail = b.bone("km_tail", body, (0, 12, -8))
    seg(b, tail, (0, 12, -8), (0, 16, -10.5), 2.2, "kstone")
    seg(b, tail, (0, 16, -10.5), (0, 18, -9), 1.6, "kstone")
    flower(b, tail, (0, 18.5, -10.5), (0, .6, -1), 4.0, spin=0, layers=2)
    b.tree = b.outliner()

    def trot(t):
        s = math.sin(2 * math.pi * t / .6)
        return {"km_leg0": (35 * s, 0, 0), "km_leg3": (35 * s, 0, 0), "km_leg1": (-35 * s, 0, 0), "km_leg2": (-35 * s, 0, 0),
                "km_body@pos": (0, .6 * abs(s), 0), "km_face": (4 * s, 0, 0), "km_tail": (0, 20 * s, 0)}
    bake(b, "walk", .6, [(0, {}, "lin"), (.6, {}, "lin")], "loop", overlay=trot)

    def pant(t):
        return {"km_face": (3 * wave(t, .8), 6 * wave(t, 4.0), 0), "km_jaw": (6 + 6 * wave(t, .8), 0, 0), "km_tail": (0, 25 * wave(t, 1.0), 0),
                "km_mane@scale": (.03 * wave(t, .8), .03 * wave(t, .8), 0)}
    bake(b, "idle", 4.0, [(0, {}, "lin"), (4.0, {}, "lin")], "loop", overlay=pant)

    # 噛みつき: crouches low, the mane bristling and the jaw wide, then lunges with the head thrust forward.
    tl, rc = KOMA["koma-bite"]
    crouch = {"km_body": (8, 0, 0), "km_body@pos": (0, -2, -2), "km_mane@scale": (.25, .25, 0), "km_face": (14, 0, 0), "km_tail": (-30, 0, 0),
              "km_jaw": (35, 0, 0)}
    lunge = {"km_body": (-12, 0, 0), "km_body@pos": (0, 3, 9), "km_face": (-20, 0, 0), "km_jaw": (0, 0, 0), "km_leg0": (-60, 0, 0), "km_leg1": (-60, 0, 0),
             "km_leg2": (40, 0, 0), "km_leg3": (40, 0, 0)}
    bake(b, "koma-bite", tl + rc, [(0, {}, "lin"), (tl * .7, crouch, "smooth"), (tl, lunge, "in"), (tl + .15, merged(lunge, {"km_face": (25, 0, 0)}), "out"),
                                   (tl + rc, {}, "smooth")])
    hurt_clip(b, {"km_body": (-8, 0, 0), "km_face": (-18, 12, 0), "km_jaw": (20, 0, 0)})
    lie = {"km_body": (0, 0, 90), "km_body@pos": (5, -4, 0), "km_leg0": (0, 0, -20), "km_leg1": (0, 0, 20), "km_face": (0, 0, -20),
           "km_mane@scale": (-.2, -.2, 0), "km_tail": (40, 0, 0), "km_jaw": (15, 0, 0)}
    bake(b, "death", 1.0, [(0, {}, "lin"), (.3, {"km_body": (-15, 0, 0), "km_body@pos": (0, 1, 0)}, "out"), (.8, lie, "in"), (1.0, lie, "hold")])
    return b


# ======================================================================================================
# 6 舞い花 (petal_wisp) — vex: a flying blossom, a small lit body in the middle, its petals the wings
# ======================================================================================================
WISP = {"wisp-dart": (.9, .5)}


def build_wisp():
    b = builder("petal_wisp", 1.4)
    body = b.bone("ws_body", None, (0, 14, 0))
    box(b, body, (-2.5, 11.5, -2), (2.5, 16.5, 2), "white", face=eyes((1, 2, 2, 3), (3, 2, 4, 3)), key="wsface")
    lo, hi = proud((-2.5, 11.5, -2), (2.5, 12.6, 2))
    box(b, body, lo, hi, "red")
    cbox(b, body, (0, 10, 0), (2.4, 2.4, 2.4), "white")
    cbox(b, body, (0, 8.2, 0), (1.4, 1.6, 1.4), "white")
    cbox(b, body, (0, 6.8, 0), (.8, 1.2, .8), "white")
    # a calyx cap: small green sepals over the head
    for k in range(5):
        a = math.radians(k * 72)
        b.plate(body, (math.cos(a) * 1.6, 17.2, math.sin(a) * 1.6), (math.cos(a), .9, math.sin(a)), (0, 1, 0), (1.8, 2.8), leaf("sepal"), thick=.4, name="cap")
    ring = b.bone("ws_ring", body, (0, 14, -1))
    for k in range(5):
        b.petal(ring, (0, 14, -2.6), (0, 0, 1), k * 72, 7.5, 6, kind="pale" if k % 2 else "petal", r0=1.2)
        b.petal(ring, (0, 14, -2.2), (0, 0, 1), k * 72 + 36, 4.4, 3.6, kind="deep", r0=.8)
    # stamens: thin filaments with yellow anthers
    for k in range(6):
        a = math.radians(k * 60 + 15)
        tip = (math.cos(a) * 3.2, 14 + math.sin(a) * 3.2, -2.9)
        b.seg(ring, (0, 14, -2.9), tip, .3, .3, panel("white"))
        b.cbox(ring, tip, (.7, .7, .7), CORE, name="anther")
    for s, side in ((1, "l"), (-1, "r")):
        arm = b.bone(f"ws_arm_{side}", body, (2.6 * s, 15, 0))
        box(b, arm, (2.5 * s, 12, -.8), (3.8 * s, 15.5, .8), "white")
        lo, hi = proud((2.5 * s, 12, -.8), (3.8 * s, 12.8, .8), .2)
        box(b, arm, lo, hi, "red")
    b.tree = b.outliner()

    def hover(t):
        return {"ws_body@pos": (0, 1.5 * wave(t, 1.6), 0), "ws_ring": (0, 0, 360 * t / 1.6), "ws_body": (0, 0, 5 * wave(t, 1.6, .25)),
                "ws_arm_l": (20 * wave(t, .8), 0, 0), "ws_arm_r": (20 * wave(t, .8, .5), 0, 0)}
    bake(b, "idle", 1.6, [(0, {}, "lin"), (1.6, {}, "lin")], "loop", overlay=hover)

    def glide(t):
        return {"ws_body@pos": (0, 1.2 * wave(t, .8), 0), "ws_ring": (0, 0, 360 * t / .8), "ws_body": (18, 0, 0),
                "ws_arm_l": (40, 0, 0), "ws_arm_r": (40, 0, 0)}
    bake(b, "walk", .8, [(0, {}, "lin"), (.8, {}, "lin")], "loop", overlay=glide)

    tl, rc = WISP["wisp-dart"]

    def spin(t):
        f = min(1, t / tl)
        return {"ws_ring": (0, 0, 360 * (t / .8 + 2.5 * f * f))}
    gather = {"ws_body@pos": (0, 4, -3), "ws_ring@scale": (-.35, -.35, 0), "ws_body": (-25, 0, 0), "ws_arm_l": (-120, 0, 20), "ws_arm_r": (-120, 0, -20)}
    throw = {"ws_body@pos": (0, 1, 4), "ws_ring@scale": (.2, .2, 0), "ws_body": (30, 0, 0), "ws_arm_l": (-60, 0, 0), "ws_arm_r": (-60, 0, 0)}
    bake(b, "wisp-dart", tl + rc, [(0, {}, "lin"), (tl * .8, gather, "smooth"), (tl, throw, "in"), (tl + rc, {}, "smooth")], overlay=spin)
    hurt_clip(b, {"ws_body": (-25, 0, 15), "ws_body@pos": (0, 1, -2)})
    fall = {"ws_ring@scale": (.5, .5, 0), "ws_ring": (60, 0, 200), "ws_body@pos": (0, -9, 0), "ws_body": (80, 0, 40)}
    bake(b, "death", 1.0, [(0, {}, "lin"), (.3, {"ws_ring@scale": (.25, .25, 0), "ws_body@pos": (0, 2, 0)}, "out"), (1.0, fall, "in")])
    return b


# ======================================================================================================
# 7 花盾の衛士 (blossom_guard) — vindicator: a shrine guard in a straw hat and lacquer shoulder plates,
#   his shield a great five-petal bloom
# ======================================================================================================
WARDEN = {"warden-bash": (.9, .7)}


def build_warden():
    b = builder("blossom_guard", 1.0)
    body = b.bone("gd_body", None, (0, 12, 0))
    box(b, body, (-4, 12, -2.5), (4, 24, 2.5), "cloth")
    # white collar crossing at the chest, a black sash with a brass knot
    b.seg(body, (-3, 24, 2.7), (1.5, 16.5, 2.7), 1.6, .4, panel("white"), over=.2)
    b.seg(body, (3, 24, 2.7), (-1.5, 16.5, 2.7), 1.6, .4, panel("white"), over=.2)
    lo, hi = proud((-4, 14.4, -2.5), (4, 16.4, 2.5))
    box(b, body, lo, hi, "black")
    cbox(b, body, (1.6, 15.4, 3), (1.6, 1.6, .8), "brass")
    top = b.bone("gd_top", body, (0, 24, 0))
    box(b, top, (-3.6, 24, -3.6), (3.6, 32, 3.6), "bone", face=eyes((1, 3, 3, 4), (4, 3, 6, 4), mouth=((2, 6, 5, 7),)), key="gdface")
    cbox(b, top, (0, 28.6, 3.9), (1.6, 2.6, 1), "bone")
    # the straw hat: a wide low cone of three tiers, a red cord under the chin, a petal caught on the brim
    box(b, top, (-8, 31, -8), (8, 32.2, 8), "straw", bands=2)
    box(b, top, (-5.5, 32.2, -5.5), (5.5, 33.6, 5.5), "straw", bands=2)
    box(b, top, (-3, 33.6, -3), (3, 35, 3), "straw")
    cbox(b, top, (0, 35.4, 0), (1.4, 1, 1.4), "red")
    for s in (1, -1):
        box(b, top, (3.6 * s, 25, -.4), (3.9 * s, 31, .4), "red")
    b.petal(top, (5, 32.4, 4.6), (0, 1, 0), 30, 2.8, 2.4, kind="pale", up_hint=(0, 0, 1))
    for s, side in ((1, "l"), (-1, "r")):
        arm = b.bone(f"gd_arm_{side}", body, (5 * s, 22, 0))
        box(b, arm, (4 * s, 13, -2), (7.6 * s, 24, 2), "cloth")
        lo, hi = proud((4 * s, 13, -2), (7.6 * s, 15, 2))
        box(b, arm, lo, hi, "white")
        cbox(b, arm, (5.8 * s, 12, 0), (3, 2.4, 3), "bone")
        # lacquer shoulder plates (袖), two lames with a red cord
        b.plate(arm, (6.8 * s, 23, 0), (s, .4, 0), (0, 1, 0), (5.4, 3.4), panel("lacq"), thick=.8, name="sode")
        b.plate(arm, (7.2 * s, 20.6, 0), (s, .25, 0), (0, 1, 0), (5.4, 3.0), panel("lacq"), thick=.8, name="sode")
        cbox(b, arm, (7.5 * s, 21.8, 2.6), (.6, 3, .6), "red")
        leg = b.bone(f"gd_leg_{side}", body, (2 * s, 12, 0))
        # wide hakama, a shin guard, a straw sandal
        box(b, leg, (-.2 * s, 3, -2.6), (4.4 * s, 12, 2.6), "lacq")
        box(b, leg, (.4 * s, 0, -2), (3.6 * s, 3, 2), "bone")
        lo, hi = proud((.4 * s, 1, -2), (3.6 * s, 3, 2))
        box(b, leg, lo, hi, "black", bands=2)
        box(b, leg, (.2 * s, 0, -2.2), (3.8 * s, .6, 2.8), "straw")
    # the shield on the left arm: a wooden boss in a brass ring, five broad petals each its own bone, five behind
    sh = b.bone("gd_shield", "gd_arm_l", (7, 15, 5))
    cbox(b, sh, (7, 16, 6.6), (3.6, 3.6, 1.8), "wood")
    cbox(b, sh, (7, 16, 6.3), (4.6, 4.6, 1), "brass")
    for k in range(5):
        p = b.bone(f"gd_shield{k}", sh, (7, 16, 6))
        b.petal(p, (7, 16, 6), (0, 0, 1), k * 72, 9, 8, kind="pale" if k % 2 else "petal", r0=.6)
        b.petal(p, (7, 16, 5.6), (0, 0, 1), k * 72 + 36, 7, 5.6, kind="deep", r0=.6)
    # a short staff of cherry wood in the right hand, a brass ferrule and a sprig at its end
    seg(b, "gd_arm_r", (-6, 12, -4), (-6, 13, 12), 1.6, "bark", lenticels=True)
    cbox(b, "gd_arm_r", (-6, 12.1, -4), (2, 2, 2), "brass")
    sprig(b, "gd_arm_r", (-6, 13, 11), (-6, 15.5, 14), w=1, flowers=1, size=2.6, seed=61)
    b.tree = b.outliner()
    guard = {"gd_arm_l": (-55, -25, 0), "gd_arm_r": (-25, 0, 0)}

    def march(t):
        s = math.sin(2 * math.pi * t / 1.1)
        return {"gd_leg_l": (26 * s, 0, 0), "gd_leg_r": (-26 * s, 0, 0), "gd_arm_r": (-12 * s, 0, 0), "gd_body": (0, 3 * s, 0),
                "gd_body@pos": (0, .3 * abs(s), 0)}
    bake(b, "walk", 1.1, [(0, guard, "lin"), (1.1, guard, "lin")], "loop", overlay=march)

    def stand(t):
        return {"gd_top": (0, 8 * wave(t, 3.2), 0), "gd_arm_r": (2 * wave(t, 3.2, .4), 0, 0)}
    bake(b, "idle", 3.2, [(0, guard, "lin"), (3.2, guard, "lin")], "loop", overlay=stand)

    tl, rc = WARDEN["warden-bash"]
    shut = merged(guard, {f"gd_shield{k}@scale": (-.45, -.45, 0) for k in range(5)}, {"gd_body": (0, 25, 0), "gd_body@pos": (0, -1, -1), "gd_arm_l": (-10, 10, 0)})
    ram = merged(guard, {f"gd_shield{k}@scale": (.2, .2, 0) for k in range(5)}, {"gd_body": (8, -15, 0), "gd_body@pos": (0, 0, 6), "gd_arm_l": (-35, 0, 0)})
    bake(b, "warden-bash", tl + rc, [(0, guard, "lin"), (tl * .75, shut, "smooth"), (tl, ram, "in"), (tl + .25, merged(ram, {"gd_body@pos": (0, 0, -1)}), "out"),
                                     (tl + rc, guard, "smooth")])
    hurt_clip(b, merged(guard, {"gd_body": (-8, 0, 0), "gd_top": (-12, 0, 0)}))
    kneel = merged(guard, {"gd_body@pos": (0, -5, 0), "gd_leg_l": (-80, 0, 0), "gd_leg_r": (-10, 0, 0), "gd_body": (12, 0, 0), "gd_top": (20, 0, 0)})
    down = merged(kneel, {"gd_body": (85, 0, 0), "gd_body@pos": (0, -10, 5), "gd_arm_l": (-10, 0, 40), "gd_arm_r": (10, 0, -30)},
                  {f"gd_shield{k}@scale": (-.6, -.6, 0) for k in range(5)})
    bake(b, "death", 1.4, [(0, guard, "lin"), (.5, kneel, "out"), (1.15, down, "in"), (1.4, down, "hold")])
    return b


# ======================================================================================================
# 8 根っこの切り株 (root_stump) — a sawn cherry stump, a sacred rope round it; roots burst up under its prey
# ======================================================================================================
STUMP = {"stump-roots": (1.2, .8)}


def build_stump():
    b = builder("root_stump", 1.0)
    body = b.bone("st_body", None, (0, 0, 0))
    box(b, body, (-6, 0, -6), (6, 10, 6), "bark", lenticels=True,
        face=eyes((3, 3, 5, 5), (7, 3, 9, 5), mouth=((4, 6, 8, 7), (5, 7, 7, 8))), key="stface")
    box(b, body, (-6.3, 9.4, -6.3), (6.3, 10.8, 6.3), rings())
    # burls, moss on one shoulder, the rope and its paper
    cbox(b, body, (-6.1, 4, 2), (1.8, 3, 3), "bark")
    cbox(b, body, (5.8, 6.5, -2.6), (1.6, 2.6, 2.6), "bark")
    box(b, body, (-6.4, 7.6, -6.4), (-2, 9.6, -1), "moss")
    box(b, body, (2, 9.8, 1.5), (5.4, 11.2, 5.4), "moss")
    shimenawa(b, body, (0, 0, 0), 6.2, 6.2, 7.8, thick=1.6, at=(-5, 5))   # streamers at the corners, clear of the face
    sprig(b, body, (2, 10.8, -1), (4.4, 17.5, -2.4), w=1.6, flowers=3, size=3.0, seed=72, bud=2)
    sprig(b, body, (-2, 10.8, 2), (-4.8, 14.5, 3.6), w=1.2, flowers=1, size=2.6, seed=73)
    for k in range(6):
        a = math.radians(k * 60 + 15)
        c0 = (math.cos(a) * 5.4, 2.6, math.sin(a) * 5.4)
        c1 = (math.cos(a) * 8.5, .9, math.sin(a) * 8.5)
        c2 = (math.cos(a + .3) * 11, 0, math.sin(a + .3) * 11)
        seg(b, body, c0, c1, 2.4, "bark", over=.8)
        seg(b, body, c1, c2, 1.6, "bark", over=.6)
    for i in range(3):
        sp = b.bone(f"st_spike{i}", None, (0, -12, 10 + i * 8))
        z = 10 + i * 8
        seg(b, sp, (0, -12, z), (.5, 0, z), 3.4, "bark", lenticels=True)
        seg(b, sp, (.5, 0, z), (-.5, 5, z + .5), 2, "bark")
        seg(b, sp, (-.5, 5, z + .5), (0, 7.4, z + .8), 1.1, "bark")
        flower(b, sp, (-.3, 5.8, z + .9), (0, 1, .4), 2.6, spin=i * 30, kind="pale")
        b.plate(sp, (.4, 2.4, z + 1.8), (0, 0, 1), (0, 1, 0), (1.8, 3.6), shide(), thick=.2, name="shide")
    b.tree = b.outliner()
    hidden = merged({f"st_spike{i}@pos": (0, -8, 0) for i in range(3)}, {f"st_spike{i}@scale": (-.95, -.95, -.95) for i in range(3)})

    def breathe(t):
        return merged(hidden, {"st_body@scale": (.03 * wave(t, 3.0), .05 * wave(t, 3.0), .03 * wave(t, 3.0))})
    bake(b, "idle", 3.0, [(0, {}, "lin"), (3.0, {}, "lin")], "loop", overlay=breathe)

    def creep(t):
        s = math.sin(2 * math.pi * t / 1.4)
        return merged(hidden, {"st_body": (0, 0, 5 * s), "st_body@pos": (0, .6 * abs(s), 0)})
    bake(b, "walk", 1.4, [(0, {}, "lin"), (1.4, {}, "lin")], "loop", overlay=creep)

    # 根の槍: it sinks, shivering; then the three spikes burst up in a line ahead, one after another.
    tl, rc = STUMP["stump-roots"]
    sink = merged(hidden, {"st_body@pos": (0, -2.5, 0), "st_body@scale": (.1, -.12, .1)})
    keys = [(0, hidden, "lin"), (tl * .8, sink, "smooth")]
    up = dict(sink)
    for i in range(3):
        up = merged(up, {f"st_spike{i}@pos": (0, 9, 0), f"st_spike{i}@scale": (.95, .95, .95)})
        keys.append((tl + i * .08, dict(up), "out"))
    keys += [(tl + .55, up, "hold"), (tl + rc, hidden, "smooth")]

    def shiver(t):
        return {"st_body": (1.5 * math.sin(t * 60), 0, 1.5 * math.sin(t * 47))} if tl * .3 < t < tl else {}
    bake(b, "stump-roots", tl + rc, keys, overlay=shiver)
    hurt_clip(b, merged(hidden, {"st_body": (-8, 0, 0), "st_body@scale": (.06, -.06, .06)}))
    rot = merged(hidden, {"st_body@pos": (0, -6, 0), "st_body": (12, 0, 14)})
    bake(b, "death", 1.2, [(0, hidden, "lin"), (.3, merged(hidden, {"st_body@pos": (0, 1, 0)}), "out"), (1.2, rot, "in")])
    return b


MOBS = {
    "husk": build_husk, "spider": build_spider, "bud": build_bud, "archer": build_archer,
    "koma": build_koma, "wisp": build_wisp, "guard": build_warden, "stump": build_stump,
}
# The ones the dungeon uses (QuestMobContent): written to assets/bosses/.
IN_GAME = ("husk", "archer", "bud")


def write(b, folder):
    path = Path(folder) / f"{b.m.name}.bbmodel"
    b.m.write(path, b.tree)
    print(path, len(b.m.elements), "cubes", len(b.bones), "bones")
    return path


if __name__ == "__main__":
    args = sys.argv[1:]
    if args[:1] == ["--out"]:
        out, ids = args[1], args[2:] or list(MOBS)
    else:
        out, ids = ROOT / "assets/bosses", list(IN_GAME)
    for i in ids:
        write(MOBS[i](), out)
