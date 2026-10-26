"""The mage's normal attack: a visible bolt (2026-10-27, plan https://claude.ai/artifact/PpG325i91uFiJXZkzsR6xx, look "ア").

Camera-facing sprites only (CoreMageBolt flies them): the bolt (white core, violet glow, four rays that twinkle),
the diamond sparkles it leaves behind, and the burst where it hits. Nothing fades by alpha: every sprite loses
its dots in a fixed 4x4 order (dither), the way the reference art disappears. Glow pixels are half transparent
around an opaque white core.
"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
DIR = 'combat_vfx/mage_bolt'

CORE = (255, 246, 232, 255)
LIGHT = (201, 191, 255, 255)
VIOLET = (143, 123, 255, 220)
DEEP = (75, 60, 158, 150)
HALO = (75, 60, 158, 90)
BAYER = (0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

BOLT_FRAMES = 4
SPARK_FRAMES = 4
BURST_FRAMES = 6


def kept(x, y, loss):
    """Ordered dither: at loss 0 every dot stays, at loss 1 none does."""
    return BAYER[(y & 3) * 4 + (x & 3)] / 16 >= loss


def put(img, x, y, color, loss=0.0):
    w, h = img.size
    if 0 <= x < w and 0 <= y < h and kept(x, y, loss):
        img.putpixel((x, y), color)


def bolt(frame):
    """16 px: a round glow, a 4 px white core and rays that alternate between + and x (the twinkle)."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5)) + .5 * min(abs(x - 7.5), abs(y - 7.5))
            if d <= 2.6:
                put(img, x, y, CORE)
            elif d <= 3.6:
                put(img, x, y, LIGHT)
            elif d <= 4.8:
                put(img, x, y, VIOLET)
            elif d <= 5.9:
                put(img, x, y, DEEP)
            elif d <= 6.6 and (x + y + frame) % 2 == 0:
                put(img, x, y, HALO)
    long = 7 if frame % 2 == 0 else 6
    for i in range(4, long + 1):
        ray = LIGHT if i < 6 else VIOLET
        for x, y in ((7, 7 - i), (8, 7 - i), (7, 8 + i), (8, 8 + i), (7 - i, 7), (7 - i, 8), (8 + i, 7), (8 + i, 8)):
            if frame % 2 == 0:
                put(img, x, y, ray)
    if frame % 2 == 1:
        for i in range(3, 7):
            for x, y in ((7 - i, 7 - i), (8 + i, 7 - i), (7 - i, 8 + i), (8 + i, 8 + i)):
                put(img, x, y, LIGHT if i < 5 else VIOLET)
    return img


def spark(stage):
    """16 px: a small four-pointed diamond star left in the air, losing its dots."""
    img = Image.new('RGBA', (16, 16))
    loss = stage / SPARK_FRAMES
    for y in range(16):
        for x in range(16):
            ax, ay = abs(x - 7.5), abs(y - 7.5)
            # A star: thin along the axes, fat in the middle.
            if ax + ay <= 2.0:
                put(img, x, y, CORE if stage < 2 else LIGHT, loss)
            elif (ax < 1 and ay <= 6.5 - stage) or (ay < 1 and ax <= 6.5 - stage):
                put(img, x, y, LIGHT if max(ax, ay) < 4 else VIOLET, loss)
            elif ax + ay <= 3.0:
                put(img, x, y, VIOLET, loss)
    return img


def burst(stage):
    """32 px: the hit. A big + star at once, a ring of dots running out, then everything drops its dots."""
    img = Image.new('RGBA', (32, 32))
    loss = max(0.0, (stage - 2) / (BURST_FRAMES - 1))
    arm = (9, 14, 15, 14, 12, 10)[stage]
    core = (3, 4, 3, 2, 2, 1)[stage]
    for y in range(32):
        for x in range(32):
            ax, ay = abs(x - 15.5), abs(y - 15.5)
            r = (ax * ax + ay * ay) ** .5
            if ax + ay <= core:
                put(img, x, y, CORE, loss)
            elif (ax < 1 and ay <= arm) or (ay < 1 and ax <= arm):
                put(img, x, y, CORE if max(ax, ay) < arm * .45 else LIGHT, loss)
            elif (ax < 2 and ay <= arm * .55) or (ay < 2 and ax <= arm * .55):
                put(img, x, y, VIOLET, loss)
            elif abs(ax - ay) < 1 and ax + ay <= arm * .7:
                put(img, x, y, DEEP, loss)
            ring = 4 + stage * 2.2
            if stage >= 1 and abs(r - ring) < .9 and (x * 3 + y) % 4 != 0:
                put(img, x, y, LIGHT if stage < 3 else VIOLET, loss * .8)
    return img


def centred_model(tex):
    # A two-sided square centred on the model origin (camera-facing sprites), as build_warrior_ref_vfx.
    face = lambda uv: {'texture': '#0', 'uv': uv}
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False,
         'faces': {'north': face([0, 0, 16, 16]), 'south': face([16, 0, 0, 16])}}]}


def build(assets, write):
    def sprite(name, img):
        path = assets / f'textures/{DIR}/{name}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        write(assets / f'models/{DIR}/{name}.json', centred_model(f'projects:{DIR}/{name}'))
        write(assets / f'items/{DIR}/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{DIR}/{name}'}})
    for f in range(BOLT_FRAMES):
        sprite(f'bolt_{f}', bolt(f))
    for s in range(SPARK_FRAMES):
        sprite(f'spark_{s}', spark(s))
    for s in range(BURST_FRAMES):
        sprite(f'burst_{s}', burst(s))


def sheet(out):
    """A preview strip at 8x: bolt frames, spark stages, burst stages."""
    tiles = [bolt(f).resize((32, 32), Image.NEAREST) for f in range(BOLT_FRAMES)]
    tiles += [spark(s).resize((32, 32), Image.NEAREST) for s in range(SPARK_FRAMES)]
    tiles += [burst(s) for s in range(BURST_FRAMES)]
    img = Image.new('RGBA', (len(tiles) * 34, 34), (18, 16, 25, 255))
    for i, t in enumerate(tiles):
        img.alpha_composite(t, (i * 34 + 1, 1))
    img.resize((img.width * 4, img.height * 4), Image.NEAREST).save(out)


if __name__ == '__main__':
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == '--sheet':
        sheet(sys.argv[2])
        raise SystemExit
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    build(PACK / 'assets/projects', write)
