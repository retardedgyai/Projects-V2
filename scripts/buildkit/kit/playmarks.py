"""野外の遊びの印：採取点・狩り場・ダンジョンの入口・復帰点の候補（~/ProjectS/night/refs/openworld/projects-systems.md の決まり）。

採取点は、ゲームの採取点のブロックをそのまま見せる（皮剥ぎ＝骨ブロック、伐採＝オークの原木、採石＝安山岩、採鉱＝銅鉱石、植物＝苔）。
1 点ずつ、遠くから形でわかる小さな塊にする。置いた物は PLAY に記録し、save_play で out/<名前>/play.json に書く（遊びの地図に使う）。
"""
import json, math, os, random
from vox import put, get, full, log, slab, stair, fence, cross, LANTERN

PLAY = []          # (種類, x, z, 説明)
KINDS = {'skin': '皮剥ぎ', 'wood': '伐採', 'stone': '採石', 'ore': '採鉱', 'herb': '植物採取'}


def gather(kind, x, z, g, rng):
    """採取点を 1 つ。形は種類ごとに決める。"""
    if kind == 'wood':                                                     # 切り株と倒れた古木：オークの原木
        put(x, g + 1, z, log('oak_log')); put(x, g + 2, z, log('oak_log'))
        dx, dz = rng.choice(((1, 0), (0, 1)))
        for k in range(1, 4): put(x + dx * (k + 1), g + 1, z + dz * (k + 1), log('oak_log', 'x' if dx else 'z'))
        put(x, g + 3, z, cross('brown_mushroom'))
    elif kind == 'ore':                                                    # 地面から突き出た岩に銅の鉱脈
        for dx, dz, h in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (-1, 0, 1), (1, 1, 1)):
            for y in range(g + 1, g + 1 + h):
                put(x + dx, y, z + dz, full('copper_ore' if (dx + dz + y) % 2 == 0 or y == g + h else 'stone'))
    elif kind == 'stone':                                                  # 安山岩の大岩、割れた欠片
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                h = 2 if (dx, dz) == (0, 0) else 1 if abs(dx) + abs(dz) == 1 else 0
                for y in range(g + 1, g + 1 + h): put(x + dx, y, z + dz, full('andesite' if (dx * 3 + dz + y) % 3 else 'polished_andesite'))
        put(x + 2, g + 1, z, slab('andesite'))
    elif kind == 'skin':                                                   # 獣の骨の山：骨ブロックと肋骨の柵
        put(x, g + 1, z, {'s': 'full', 't': 'bone_block_side', 'top': 'bone_block_top', 'bot': 'bone_block_top'})
        put(x + 1, g + 1, z, {'s': 'full', 't': 'bone_block_side', 'top': 'bone_block_top', 'bot': 'bone_block_top'})
        put(x, g + 2, z, {'s': 'full', 't': 'bone_block_side', 'top': 'bone_block_top', 'bot': 'bone_block_top'})
        for k in (-1, 2): put(x + k, g + 1, z + 1, fence('birch_planks'))
    elif kind == 'herb':                                                   # 苔の塊に花と草
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) < 2 or rng.random() < 0.5:
                    put(x + dx, g, z + dz, full('moss_block'))
                    if get(x + dx, g + 1, z + dz) is None: put(x + dx, g + 1, z + dz, cross(rng.choice(('fern', 'allium', 'azure_bluet', 'short_grass'))))
        put(x, g + 1, z, full('moss_block'))
    PLAY.append((kind, x, z, KINDS[kind]))


def den(x, z, g, rng, label='狩り場'):
    """狩り場の目印：踏み荒らした土の輪と、骨と爪痕の岩（湧く場所の真ん中）。"""
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if 2.5 < math.hypot(dx, dz) < 4.5 and rng.random() < 0.5 and get(x + dx, g, z + dz) is not None:
                put(x + dx, g, z + dz, full('coarse_dirt'))
    for dx, dz in ((0, 0), (1, 0)):
        put(x + dx, g + 1, z + dz, full('mossy_cobblestone'))
    put(x, g + 2, z, {'s': 'full', 't': 'bone_block_side', 'top': 'bone_block_top', 'bot': 'bone_block_top'})
    PLAY.append(('hunt', x, z, label))


def waystone(x, z, g, label='復帰点の候補'):
    """復帰点・ワープの候補：石の柱に灯り（今は飾り。ゲームの仕組みはまだ無い）。"""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1): put(x + dx, g, z + dz, full('stone_bricks' if (dx + dz) % 2 else 'chiseled_stone_bricks'))
    for y in range(g + 1, g + 4): put(x, y, z, full('polished_andesite' if y < g + 3 else 'chiseled_stone_bricks'))
    put(x, g + 4, z, LANTERN)
    for dx, dz, f in ((1, 0, 'w'), (-1, 0, 'e'), (0, 1, 'n'), (0, -1, 's')): put(x + dx, g + 1, z + dz, stair('stone_bricks', f))
    PLAY.append(('way', x, z, label))


def sign_board(x, z, g, f='s', label='入口の目安の札'):
    """入口の目安の札（攻撃・HP／今のあなた、を出す板の場所）。"""
    for y in (g + 1, g + 2): put(x, y, z, fence('dark_oak_planks')); put(x + 2, y, z, fence('dark_oak_planks'))
    for k in range(3): put(x + k, g + 3, z, full('dark_oak_planks')); put(x + k, g + 4, z, slab('dark_oak_planks'))
    PLAY.append(('sign', x + 1, z, label))


def mark(kind, x, z, label):
    """ブロックは置かず、遊びの地図にだけ載せる印（町・入口・見晴らし・ボスの床）。"""
    PLAY.append((kind, x, z, label))


def save_play(out_dir, route=None, notes=None):
    os.makedirs(out_dir, exist_ok=True)
    json.dump({'marks': PLAY, 'route': route or [], 'notes': notes or {}}, open(os.path.join(out_dir, 'play.json'), 'w'), ensure_ascii=False)


def spot(L, x, z, r=7):
    """(x, z) の近くで、平らで空いた自然の地面を探す（渦を巻いて外へ）。"""
    for d in range(0, r + 1):
        for dx in range(-d, d + 1):
            for dz in range(-d, d + 1):
                if max(abs(dx), abs(dz)) != d: continue
                X, Z = x + dx, z + dz
                if not L.inb(X, Z) or not L.inb(X + 2, Z + 2) or not L.inb(X - 2, Z - 2): continue
                if L.slope_ok(X, Z, 2) and get(X, L.h(X, Z) + 1, Z) is None: return X, Z, L.h(X, Z)
    return None


def lay(L, rng, plan):
    """plan = [(種類, x, z, 説明)]。種類は gather の 5 種・'hunt'・'way'・'sign'・ほかは印だけ。"""
    for kind, x, z, label in plan:
        if kind in KINDS:
            s = spot(L, x, z)
            if s: gather(kind, s[0], s[1], s[2], rng)
        elif kind == 'hunt':
            s = spot(L, x, z)
            if s: den(s[0], s[1], s[2], rng, label)
        elif kind == 'way':
            s = spot(L, x, z)
            if s: waystone(s[0], s[1], s[2], label)
        elif kind == 'sign':
            s = spot(L, x, z)
            if s: sign_board(s[0], s[1], s[2], label=label)
        else: mark(kind, x, z, label)
