"""ブロックを置いて house.js と atlas.png に書き出す共通部品（木組みの家の習作から切り出し）。"""
import json, math, os, zipfile, io
from PIL import Image

JAR = os.path.expanduser('~/.minecraft/versions/26.3/26.3.jar')

def _h(ix, iz, seed):
    n = (ix * 374761393 + iz * 668265263 + seed * 2147483647) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0

def vnoise(x, z, scale, seed):
    x /= scale; z /= scale
    ix, iz = math.floor(x), math.floor(z)
    fx, fz = x - ix, z - iz
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a = _h(ix, iz, seed); b = _h(ix + 1, iz, seed)
    c = _h(ix, iz + 1, seed); d = _h(ix + 1, iz + 1, seed)
    return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz

W = {}
def put(x, y, z, b):
    if b is None: W.pop((x, y, z), None)
    else: W[(x, y, z)] = b
def get(x, y, z): return W.get((x, y, z))

def full(tex, top=None, bottom=None): return {'s': 'full', 't': tex, 'top': top or tex, 'bot': bottom or top or tex}
def log(tex, axis='y'): return {'s': 'full', 't': tex, 'top': tex + '_top', 'bot': tex + '_top', 'axis': axis}
def stair(tex, f, half='bottom'): return {'s': 'stair', 't': tex, 'f': f, 'h': half}
def slab(tex, half='bottom'): return {'s': 'slab', 't': tex, 'h': half}
def trap(tex, face): return {'s': 'trap', 't': tex, 'f': face}
def cross(tex): return {'s': 'cross', 't': tex}
def fence(tex): return {'s': 'fence', 't': tex}
def door(part, face, wood='spruce'): return {'s': 'door', 't': wood + '_door_' + part, 'f': face}
def vine(face): return {'s': 'vine', 't': 'vine', 'f': face}
LANTERN = {'s': 'lantern', 't': 'lantern'}
CHAIN = {'s': 'chain', 't': 'iron_chain'}
def water(): return {'s': 'water', 't': 'water_still'}
def path(): return {'s': 'path', 't': 'dirt_path_side', 'top': 'dirt_path_top', 'bot': 'dirt'}
def grass(): return {'s': 'full', 't': 'grass_side', 'top': 'grass_top', 'bot': 'dirt'}

TEX_FILES = {'grass_top': 'grass_block_top', 'grass_side': 'grass_block_side'}
GRASS = (0x91, 0xBD, 0x59); FOLIAGE = (0x77, 0xAB, 0x2F); SPRUCE = (0x61, 0x99, 0x61)
WATER = (0x3F, 0x76, 0xE4)
TINT = {'water_still': WATER, 'sugar_cane': GRASS, 'lily_pad': (0x20, 0x80, 0x30), 'oak_leaves': FOLIAGE, 'dark_oak_leaves': FOLIAGE, 'vine': FOLIAGE, 'spruce_leaves': SPRUCE,
        'short_grass': GRASS, 'tall_grass_bottom': GRASS, 'tall_grass_top': GRASS, 'fern': GRASS, 'grass_top': GRASS,
        'birch_leaves': (0x80, 0xA7, 0x55), 'bush': FOLIAGE}

def _tint(im, rgb):
    px = im.load(); out = im.copy(); po = out.load()
    for yy in range(im.height):
        for xx in range(im.width):
            r, g, b, a = px[xx, yy]
            po[xx, yy] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    return out

def write(out_dir, extra=None):
    os.makedirs(out_dir, exist_ok=True)
    used = set()
    for b in W.values():
        for k in ('t', 'top', 'bot', 'fr', 'plant'):
            if k in b: used.add(b[k])
        if b.get('hang'): used.add('iron_chain')
    z = zipfile.ZipFile(JAR)
    def load(name):
        fn = TEX_FILES.get(name, name)
        try: im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/%s.png' % fn))).convert('RGBA')
        except KeyError:
            print('missing', fn); return Image.new('RGBA', (16, 16), (255, 0, 255, 255))
        return im.crop((0, 0, 16, 16))
    names = sorted(used); cols = 8; rows = (len(names) + cols - 1) // cols
    atlas = Image.new('RGBA', (cols * 16, rows * 16), (0, 0, 0, 0)); index = {}
    for i, n in enumerate(names):
        im = load(n)
        if n == 'grass_side': im = Image.alpha_composite(im, _tint(load('grass_block_side_overlay'), GRASS))
        elif n in TINT: im = _tint(im, TINT[n])
        atlas.paste(im, ((i % cols) * 16, (i // cols) * 16)); index[n] = i
    atlas.save(os.path.join(out_dir, 'atlas.png'))
    pal, palidx, blocks = [], {}, []
    def solid(b): return b is not None and b['s'] == 'full' and not any(k in b.get('t', '') for k in ('leaves', 'glass', 'ice', 'barrier'))
    for (x, y, zz), b in W.items():
        if solid(b) and all(solid(W.get(p)) for p in ((x + 1, y, zz), (x - 1, y, zz), (x, y + 1, zz), (x, y - 1, zz), (x, y, zz + 1), (x, y, zz - 1))):
            continue                                                       # 6面とも囲まれて見えない中身は書き出さない（ページが重くて固まった）
        key = json.dumps(b, sort_keys=True)
        if key not in palidx: palidx[key] = len(pal); pal.append(b)
        blocks.append([x, y, zz, palidx[key]])
    data = {'cols': cols, 'rows': rows, 'tex': index, 'pal': pal, 'blocks': blocks}
    data.update(extra or {})
    with open(os.path.join(out_dir, 'house.js'), 'w') as f:
        f.write('window.HOUSE=' + json.dumps(data, separators=(',', ':')) + ';')
    print('blocks', len(blocks), 'palette', len(pal), 'textures', len(names))
    return len(blocks), len(pal), len(names)
