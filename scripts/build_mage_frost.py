"""The mage's frost nova (2026-10-28, sample version 12, the Creator: "氷は一旦これでOK";
https://claude.ai/artifact/4C3q1PWvZQhzfeTNFsxJCJ, ~/ProjectS/refs/mage-vfx/preview/src_frost.js). Drawn the way
wolf awwent's Goosezilla draws ice, 16 px to a block:

- col_<k><i>_<s>: an ice column, a stack of tapering blocks with a stepped point, every face the Goosezilla ice block
  (2 px white rim, pale band, lavender, two round violet blots). s = how many pieces have grown (one a tick).
  k = ring 0..2 (outer rings taller), i = 0 the thick middle one of a clump, 1..2 the thinner ones round it.
- sprout_<n>_<s>: the same, small, growing out of a frozen enemy's body.
- patch_0..3: the frost patch spreading over the ground (four reach steps), a flat decal.
- dash_0..5: the thin cyan ring racing out at knee height, breaking into dashes.
- halo_<k>_<l>: the three tilted cyan rings rising round the mage, one pixel wide, thinning as they grow.
- pillar_core / pillar_rim: the strike, a white column with a cyan rim.
- flake: a 5 x 5 snowflake. bit: a small ice block thrown out when the ice breaks.
Models in block space (16 units = 1 block); the column's base is the model's bottom (-16), so CoreMageFrost stands an
entity 1.5 blocks above where the ice grows from.
"""
import json, math, sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
DIR = 'combat_vfx/mage_frost'
CYAN = (96, 240, 240, 255)


def lcg(seed):
    s = (seed * 2654435761 + 12345) & 0xffffffff
    def r():
        nonlocal s
        s = (s * 1664525 + 1013904223) & 0xffffffff
        return s / 4294967296
    for _ in range(3): r()
    return r


def ice_block():
    """Goosezilla's ice block, 16 x 26: a 2 px white rim, a 1 px pale band, lavender, two round violet blots with a
    lighter edge and a white glint."""
    img = Image.new('RGBA', (16, 26))
    for y in range(26):
        for x in range(16):
            e = min(x, y, 15 - x, 25 - y)
            c = (234, 247, 252) if e < 2 else (205, 226, 246) if e < 3 else None
            if c is None:
                for cx, cy, r in ((6, 8, 3.4), (10, 18, 3)):
                    d = math.hypot(x + .5 - cx, (y + .5 - cy) * .62)
                    if d < r - 1.2: c = (214, 206, 250) if (x + y) % 9 == 0 else (112, 82, 200); break
                    if d < r: c = (150, 128, 222); break
            img.putpixel((x, y), (c or (186, 190, 240)) + (255,))
    return img


def column(w0, nseg, shrink=.74):
    """The pieces of one column as boxes in px (16 = 1 block), base on y = 0: the segments, then a stepped point."""
    boxes, y, w = [], 0.0, w0 * 16
    for _ in range(nseg):
        h = w * 1.62; boxes.append((w, y, y + h)); y += h * .94; w *= shrink
    for step in (.7, .42, .18):  # the point, three shrinking blocks
        h = w * 1.0; boxes.append((w * step, y, y + h)); y += h * .92
    return boxes


def box_model(boxes, tex, base=-16):
    face = {'texture': '#0', 'uv': [0, 0, 16, 16]}
    els = []
    for w, y0, y1 in boxes:
        y0, y1 = min(32, y0 + base), min(32, y1 + base)
        if y1 <= y0: continue
        els.append({'from': [8 - w / 2, y0, 8 - w / 2], 'to': [8 + w / 2, y1, 8 + w / 2], 'shade': False,
                    'faces': {d: dict(face) for d in ('north', 'south', 'east', 'west', 'up', 'down')}})
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': els}


def flat_model(tex):
    """A square lying flat, seen from above and below."""
    face = lambda uv: {'texture': '#0', 'uv': uv}
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 8, 0], 'to': [16, 8, 16], 'shade': False, 'faces': {'up': face([0, 0, 16, 16]), 'down': face([0, 16, 16, 0])}}]}


def sprite_model(tex):
    face = lambda uv: {'texture': '#0', 'uv': uv}
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False, 'faces': {'north': face([0, 0, 16, 16]), 'south': face([16, 0, 0, 16])}}]}


def patch(reach):
    n = 96; R = lcg(41); edge = [.82 + R() * .18 for _ in range(48)]
    img = Image.new('RGBA', (n, n))
    for y in range(n):
        for x in range(n):
            dx, dy = x + .5 - n / 2, y + .5 - n / 2; r = math.hypot(dx, dy) / (n / 2)
            k = int((math.atan2(dy, dx) / 6.283 + .5) * 48) % 48; e = edge[k] * reach
            if r > e: continue
            if r > e - .05: img.putpixel((x, y), (234, 247, 252, 235)); continue
            v = lcg((x // 4) * 31 + (y // 4) * 17)()
            img.putpixel((x, y), (166, 227, 240, 150) if v < .2 else (226, 251, 255, 120))
    return img


def ring(radius, width_px, keep):
    """A ring of [radius] blocks drawn at 16 px to a block, [width_px] wide; keep(segment) says which of its 56
    segments stay (the dashes)."""
    n = int(math.ceil(radius * 32)) + 4; c = n / 2
    img = Image.new('RGBA', (n, n))
    for y in range(n):
        for x in range(n):
            dx, dy = x + .5 - c, y + .5 - c; r = math.hypot(dx, dy)
            if abs(r - radius * 16) > width_px / 2: continue
            seg = int((math.atan2(dy, dx) / 6.283 + .5) * 56) % 56
            if keep(seg): img.putpixel((x, y), CYAN)
    return img


DASH_R = [1 + 4.6 * (1 - .55 ** (a + 1)) for a in range(6)]
HALO_R = lambda k, l: 1.5 + l * .5 + k * .6
COLUMNS = {f'{k}{i}': (((.42 if i == 0 else .3 - i * .03) + k * .04), (2, 3, 4)[k] + (1 if i == 0 else 0)) for k in range(3) for i in range(3)}
SPROUTS = {'a': (.34, 3), 'b': (.24, 2), 'c': (.2, 3), 'd': (.16, 2)}


def flake():
    img = Image.new('RGBA', (5, 5))
    for y in range(5):
        for x in range(5):
            dx, dy = abs(x - 2), abs(y - 2)
            if dx == 0 and dy == 0: img.putpixel((x, y), (160, 240, 250, 255))
            elif dx == 0 or dy == 0 or dx == dy: img.putpixel((x, y), (255, 255, 255, 255))
    return img


def build(assets, write):
    def tex(name, img):
        path = assets / f'textures/{DIR}/{name}.png'; path.parent.mkdir(parents=True, exist_ok=True); img.save(path)
        return f'projects:{DIR}/{name}'
    def item(name, model):
        write(assets / f'models/{DIR}/{name}.json', model)
        write(assets / f'items/{DIR}/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{DIR}/{name}'}})
    ice = tex('ice', ice_block())
    for name, (w0, nseg) in list(COLUMNS.items()):
        boxes = column(w0, nseg)
        for s in range(1, len(boxes) + 1):
            item(f'col_{name}_{s}', box_model(boxes[:s], ice))
    for name, (w0, nseg) in SPROUTS.items():
        boxes = column(w0, nseg, .72)
        for s in range(1, len(boxes) + 1):
            item(f'sprout_{name}_{s}', box_model(boxes[:s], ice, base=0))
    for k, reach in enumerate((.3, .55, .8, 1)):
        item(f'patch_{k}', flat_model(tex(f'patch_{k}', patch(reach))))
    for a, r in enumerate(DASH_R):
        keep = (lambda s: True) if a < 3 else (lambda s: s % 2 == 0) if a < 5 else (lambda s: s % 4 == 0)
        item(f'dash_{a}', flat_model(tex(f'dash_{a}', ring(r, 2, keep))))
    for k in range(3):
        for l in range(9):
            keep = (lambda s, k=k: True) if l <= 5 else (lambda s, k=k: (s // 4 + k) % 3 != 0)
            item(f'halo_{k}_{l}', flat_model(tex(f'halo_{k}_{l}', ring(HALO_R(k, l), 2 if l < 4 else 1, keep))))
    core = Image.new('RGBA', (4, 4), (255, 255, 255, 255)); rim = Image.new('RGBA', (4, 4), (96, 240, 240, 115))
    item('pillar_core', box_model([(16, 0, 16)], tex('white', core), base=0))
    item('pillar_rim', box_model([(16, 0, 16)], tex('cyan', rim), base=0))
    item('flake', sprite_model(tex('flake', flake())))
    item('bit', box_model([(16, 0, 16)], ice, base=0))


def model_names():
    """Every item this builds (for tests and CoreMageFrost)."""
    out = []
    for name, (w0, nseg) in COLUMNS.items(): out += [f'col_{name}_{s}' for s in range(1, nseg + 4)]
    for name, (w0, nseg) in SPROUTS.items(): out += [f'sprout_{name}_{s}' for s in range(1, nseg + 4)]
    return out


if __name__ == '__main__':
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, separators=(',', ':')) + '\n', encoding='utf-8')
    build(PACK / 'assets/projects', write)
