"""マイクラのブロック（名前, 性質）を、プレビューの vox の dict に直す。手本の地図（kit/mca_read.py で読む）を viewer で見るため。
形は vox にあるもの（full・stair・slab・trap・fence・wall・pane・door・cross・carpet・lantern・chain・water）に寄せ、
無い物（看板・旗・頭・チェストなど）は落とす。字の名前は、マイクラの jar にある絵の名前に合わせる。
"""
import zipfile
import vox

SKIP = {'air', 'cave_air', 'void_air', 'barrier', 'light', 'structure_void', 'moving_piston', 'bubble_column'}
F = {'north': 'n', 'south': 's', 'east': 'e', 'west': 'w'}
OPP = {'n': 's', 's': 'n', 'e': 'w', 'w': 'e'}
SPECIAL = {'quartz': 'quartz_block_side', 'smooth_quartz': 'quartz_block_bottom', 'smooth_sandstone': 'sandstone_top', 'smooth_red_sandstone': 'red_sandstone_top',
           'petrified_oak': 'oak_planks', 'purpur': 'purpur_block', 'smooth_stone': 'smooth_stone', 'cut_sandstone': 'cut_sandstone',
           'bamboo_mosaic': 'bamboo_mosaic', 'snow': 'snow', 'dried_kelp_block': 'dried_kelp_side', 'grass_block': 'grass_side'}
CROSS_HINT = ('grass', 'fern', 'sapling', 'flower', 'tulip', 'orchid', 'allium', 'bluet', 'daisy', 'poppy', 'dandelion', 'cornflower', 'lily_of_the_valley',
              'rose', 'bush', 'mushroom', 'dead_bush', 'sugar_cane', 'roots', 'sprouts', 'torchflower', 'pitcher', 'wheat', 'carrots', 'potatoes', 'beetroots',
              'cobweb', 'seagrass', 'kelp', 'vines', 'lilac', 'peony', 'sunflower', 'pink_petals', 'fungus', 'eyeblossom', 'firefly_bush', 'leaf_litter', 'wildflowers')

_TEX = None
def textures():
    global _TEX
    if _TEX is None:
        z = zipfile.ZipFile(vox.JAR); p = 'assets/minecraft/textures/block/'
        _TEX = {n[len(p):-4] for n in z.namelist() if n.startswith(p) and n.endswith('.png')}
    return _TEX


def tex_of(base):
    """ブロック（または階段・ハーフの元）の名前 -> 横の面の絵の名前。"""
    T = textures()
    if base in SPECIAL: return SPECIAL[base]
    for c in (base, base + 's', base + '_planks', base.replace('brick', 'bricks'), base.replace('tile', 'tiles'), base + '_side', base + '_block', base + '_block_side',
              base.replace('_block', ''), base + '_top', base + '_front'):
        if c in T: return c
    if base.endswith('_wood') or base.endswith('_hyphae'):
        c = base.replace('_wood', '_log').replace('_hyphae', '_stem')
        if c in T: return c
    return None


def convert(name, pr):
    """(名前, 性質) -> vox の dict か None。"""
    if name in SKIP: return None
    T = textures()
    if name == 'water': return vox.water()
    if name == 'lava': return vox.full('lava_still')
    if name == 'grass_block': return vox.grass()
    if name == 'dirt_path': return vox.path()
    if pr.get('waterlogged') == 'true' and name in ('seagrass', 'tall_seagrass', 'kelp', 'kelp_plant'): return vox.water()
    for suf in ('_stairs', '_slab', '_fence_gate', '_fence', '_wall', '_trapdoor', '_door', '_carpet', '_pane', '_button', '_pressure_plate', '_sign', '_wall_sign', '_hanging_sign', '_banner', '_wall_banner'):
        if name.endswith(suf): break
    else: suf = ''
    base = name[:-len(suf)] if suf else name
    if suf in ('_button', '_pressure_plate', '_sign', '_wall_sign', '_hanging_sign', '_banner', '_wall_banner'): return None
    if suf == '_stairs':
        t = tex_of(base); return t and vox.stair(t, F.get(pr.get('facing'), 'n'), pr.get('half', 'bottom'))
    if suf == '_slab':
        t = tex_of(base)
        if not t: return None
        return vox.full(t) if pr.get('type') == 'double' else vox.slab(t, pr.get('type', 'bottom'))
    if suf == '_fence':
        t = tex_of(base); return t and vox.fence(t)
    if suf == '_fence_gate':
        t = tex_of(base); return t and {'s': 'gate', 't': t, 'f': F.get(pr.get('facing'), 'n')}
    if suf == '_wall':
        t = tex_of(base); return t and {'s': 'wall', 't': t}
    if suf == '_trapdoor':
        t = name if name in T else None
        if not t: return None
        if pr.get('open') == 'true': return vox.trap(t, OPP[F.get(pr.get('facing'), 'n')])   # 開いた戸は、向きと反対の縁に立つ
        return vox.trap(t, 'top' if pr.get('half') == 'top' else 'bottom')
    if suf == '_door':
        part = 'top' if pr.get('half') == 'upper' else 'bottom'
        t = name + '_' + part
        if t not in T: return None
        return {'s': 'door', 't': t, 'f': OPP[F.get(pr.get('facing'), 'n')]}
    if suf == '_carpet':
        t = tex_of(base.replace('moss', 'moss_block')) or tex_of(base + '_wool'); return t and {'s': 'carpet', 't': t}
    if suf == '_pane' or name == 'iron_bars':
        t = tex_of(base) if suf else 'iron_bars'
        if not t: return None
        return {'s': 'pane', 't': t, 'ax': 'x' if pr.get('east') == 'true' or pr.get('west') == 'true' else 'z'}
    if name in ('lantern', 'soul_lantern'): return {'s': 'lantern', 't': name, 'hang': pr.get('hanging') == 'true'}
    if name in ('chain', 'iron_chain'): return {'s': 'chain', 't': 'iron_chain' if 'iron_chain' in T else 'chain'}
    if name == 'vine': return vox.vine(next((F[k] for k in F if pr.get(k) == 'true'), 'n'))
    if name in ('torch', 'wall_torch', 'soul_torch', 'soul_wall_torch', 'redstone_torch'): return vox.cross('torch' if 'soul' not in name else 'soul_torch')
    if name in ('tall_grass', 'large_fern', 'tall_seagrass'):
        t = name + ('_top' if pr.get('half') == 'upper' else '_bottom'); return vox.cross(t) if t in T else None
    if name in ('lilac', 'peony', 'rose_bush', 'sunflower', 'pitcher_plant'):
        t = name + ('_top' if pr.get('half') == 'upper' else '_bottom'); return vox.cross(t) if t in T else None
    if any(h in name for h in CROSS_HINT) and not name.endswith('_block'):
        t = name if name in T else name + '_stage3' if name + '_stage3' in T else name + '_stage7' if name + '_stage7' in T else None
        if t: return vox.cross(t)
    if name.endswith('_log') or name.endswith('_stem') and name + '_top' in T or name in ('basalt', 'polished_basalt', 'bone_block', 'hay_block', 'quartz_pillar', 'purpur_pillar'):
        side = name if name in T else name + '_side'
        top = name + '_top'
        if side in T and top in T:
            ax = pr.get('axis', 'y'); b = vox.log(name, ax) if side == name else {'s': 'full', 't': side, 'top': top, 'bot': top, 'axis': ax}
            return b
    if name.endswith('_leaves'): return vox.full(name) if name in T else None
    t = tex_of(name)
    if not t: return None
    top = name + '_top' if name + '_top' in T else None
    bot = name + '_bottom' if name + '_bottom' in T else top
    if t == name + '_front' or t == name + '_side' or top: return vox.full(t, top or t, bot or top or t)
    return vox.full(t)
