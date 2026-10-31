#!/usr/bin/env python3
"""Generate the shared passive tree the way Path of Exile builds its own.

PoE places *groups* on a plane. Each node sits on an *orbit* (a ring of fixed radius) around its
group centre at one of a fixed number of evenly spaced slots, and two nodes of the same group on the
same orbit are joined by an arc; every other connection is a straight line. We do the same:

* the six class starts sit on an inner ring around a central hub, so every class begins near the
  middle and can cross the hub to reach any other region;
* outward from each start run three lanes; groups sit where lanes meet radius bands, and rungs join
  neighbouring lanes, so the tree is a web with several routes to most places;
* a class's direction decides its themes; the wedge between two classes blends both (hybrid area);
* the deeper a group lies, the stronger its notables; keystones sit at the rim.

Run from the repository root:

    python3 scripts/build_passive_tree.py

Writes the server table server-minestom/src/main/resources/passive-tree.tsv, the art source
assets/ui/passive-tree/tree.json (scripts/build_passive_tree_assets.py bakes the in-game screen from it)
and the preview docs/proposals/skill-tree-2026-10/passive-tree.html.
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/proposals/skill-tree-2026-10"
TSV = ROOT / "server-minestom/src/main/resources/passive-tree.tsv"
ART = ROOT / "assets/ui/passive-tree/tree.json"

# PoE's own orbit radii and slot counts for the first five orbits.
ORBIT_RADII = [0, 82, 162, 335, 493]
ORBIT_SLOTS = [1, 6, 16, 16, 40]

rng = random.Random(20261028)

# Stats (CoreAffixStat): label, small value, notable value. Small nodes are worth taking alone.
STATS = {
    "DAMAGE": ("与ダメージ", 4, 10), "SKILL_DAMAGE": ("スキルダメージ", 5, 12),
    "NORMAL_DAMAGE": ("通常攻撃ダメージ", 6, 15), "MELEE_DAMAGE": ("近接ダメージ", 6, 15),
    "PROJECTILE_DAMAGE": ("投射物ダメージ", 6, 15), "MAGICAL_DAMAGE": ("魔法ダメージ", 6, 15),
    "PHYSICAL_DAMAGE": ("物理ダメージ", 6, 15), "AD_PERCENT": ("物理攻撃力", 5, 12),
    "AP_PERCENT": ("魔法攻撃力", 5, 12), "ATTACK_SPEED": ("攻撃速度", 3, 8),
    "COOLDOWN_REDUCTION": ("CD回復速度", 3, 8), "CAST_REDUCTION": ("詠唱速度", 3, 8),
    "CRIT_CHANCE_INCREASED": ("会心率", 10, 25), "CRIT_MULTIPLIER": ("会心倍率", 8, 20),
    "HEALTH_PERCENT": ("最大HP", 4, 10), "AR_PERCENT": ("物理防御", 6, 15),
    "MR_PERCENT": ("魔法防御", 6, 15), "MITIGATION": ("被ダメージ軽減", 2, 5),
    "SHIELD_POWER": ("シールド量", 8, 20), "HEALING_PERCENT": ("回復力", 6, 15),
    "OUTGOING_HEALING": ("与回復量", 6, 15), "MANA_PERCENT": ("最大マナ", 6, 15),
    "MANA_REGEN": ("マナ回復速度", 8, 20), "MOVE_SPEED": ("移動速度", 2, 5),
    "LIFESTEAL": ("ライフスティール", 1, 3), "CC_RESIST": ("CC耐性", 6, 15),
    "CC_DURATION": ("CC時間", 6, 15), "BREAK_POWER": ("ブレイク力", 6, 15),
    "DOT_DAMAGE": ("継続ダメージ", 8, 20), "DODGE_RECOVERY": ("回避の再使用速度", 4, 10),
    "HEAL_ON_KILL": ("撃破時に最大HPの回復", 1, 3), "OVERHEAL_SHIELD": ("溢れた回復をシールドに", 10, 25),
    "MAGICAL_PEN_PERCENT": ("魔法貫通", 4, 10), "PHYSICAL_PEN_PERCENT": ("物理貫通", 4, 10),
    "INCOMING_HEALING": ("被回復量", 6, 15),
}

# Conditions a notable or mastery value can hang on (CorePassiveCondition); "STAT@COND=value" in the table.
CONDITIONS = {
    "RECENT_KILL": "敵を倒して4秒以内", "LOW_LIFE": "HPが半分以下のとき", "FULL_LIFE": "HPが満タンのとき",
    "AFTER_DODGE": "回避して2秒以内", "STILL": "2秒動いていないとき", "MOVING": "移動中",
    "VS_BOSS": "ボスに対して", "VS_CONTROLLED": "CC中・減速中の敵に対して",
}
TARGET_CONDITIONS = {"VS_BOSS", "VS_CONTROLLED"}
OFFENSE = {"DAMAGE", "SKILL_DAMAGE", "NORMAL_DAMAGE", "MELEE_DAMAGE", "PROJECTILE_DAMAGE", "MAGICAL_DAMAGE", "PHYSICAL_DAMAGE",
           "AD_PERCENT", "AP_PERCENT", "CRIT_CHANCE_INCREASED", "CRIT_MULTIPLIER", "MAGICAL_PEN_PERCENT", "PHYSICAL_PEN_PERCENT",
           "DOT_DAMAGE", "CC_DURATION", "BREAK_POWER", "LIFESTEAL"}
# Which conditions each region's deeper notables use (PoE: "if you've killed recently", "on low life"...).
THEME_CONDITIONS = {
    "WARRIOR": ["LOW_LIFE", "RECENT_KILL"], "TEMPLAR": ["STILL", "FULL_LIFE"], "HEALER": ["FULL_LIFE", "STILL"],
    "MAGE": ["VS_CONTROLLED", "FULL_LIFE"], "ASSASSIN": ["AFTER_DODGE", "MOVING"], "RANGER": ["STILL", "AFTER_DODGE"],
    "HYBRID": ["VS_BOSS", "RECENT_KILL"],
}

# Six classes clockwise from the top, so neighbours share a hybrid wedge. As in PoE (the witch sits next
# to the shadow), every class has an offensive neighbour: the mage borders the assassin (crit, damage over time),
# never only the two support classes (2026-10-31, the mage looked starved between templar and healer).
CLASSES = [
    ("WARRIOR", "戦士", -90),
    ("TEMPLAR", "テンプラー", -30),
    ("HEALER", "ヒーラー", 30),
    ("MAGE", "メイジ", 90),
    ("ASSASSIN", "アサシン", 150),
    ("RANGER", "レンジャー", 210),
]

# Each region: travel stats for small nodes, then notables (name, stats) inner -> outer.
THEMES = {
    "WARRIOR": {
        "small": ["MELEE_DAMAGE", "HEALTH_PERCENT", "AD_PERCENT", "PHYSICAL_DAMAGE"],
        "notables": [
            ("剛力", ["AD_PERCENT", "MELEE_DAMAGE"]), ("鋼の体", ["HEALTH_PERCENT", "AR_PERCENT"]),
            ("大振り", ["MELEE_DAMAGE", "BREAK_POWER"]), ("不屈", ["HEALTH_PERCENT", "CC_RESIST"]),
            ("血の渇き", ["LIFESTEAL", "MELEE_DAMAGE"]), ("重い一撃", ["BREAK_POWER", "CRIT_MULTIPLIER"]),
            ("戦場の心得", ["DAMAGE", "MITIGATION"]), ("鉄塊の主", ["AD_PERCENT", "PHYSICAL_PEN_PERCENT"]),
        ]},
    "TEMPLAR": {
        "small": ["AR_PERCENT", "SHIELD_POWER", "MR_PERCENT", "HEALTH_PERCENT"],
        "notables": [
            ("城壁", ["AR_PERCENT", "MR_PERCENT"]), ("誓いの盾", ["SHIELD_POWER", "CC_RESIST"]),
            ("聖なる守り", ["MR_PERCENT", "MITIGATION"]), ("不動の心", ["CC_RESIST", "HEALTH_PERCENT"]),
            ("守りの光", ["SHIELD_POWER", "OUTGOING_HEALING"]), ("堅牢", ["AR_PERCENT", "MITIGATION"]),
            ("裁きの槍", ["CC_DURATION", "MELEE_DAMAGE"]), ("揺るがぬ誓い", ["MITIGATION", "SHIELD_POWER"]),
        ]},
    "MAGE": {
        # Like the witch's spell-crit wheels: the mage's own damage and its own crit sit right by the start.
        "small": ["AP_PERCENT", "MAGICAL_DAMAGE", "SKILL_DAMAGE", "CRIT_CHANCE_INCREASED"],
        "notables": [
            ("深淵の知", ["AP_PERCENT", "MAGICAL_DAMAGE"]), ("術の急所", ["CRIT_CHANCE_INCREASED", "MAGICAL_DAMAGE"]),
            ("魔力の奔流", ["SKILL_DAMAGE", "CRIT_MULTIPLIER"]), ("貫く魔弾", ["MAGICAL_PEN_PERCENT", "MAGICAL_DAMAGE"]),
            ("燃え残る術", ["DOT_DAMAGE", "MAGICAL_DAMAGE"]), ("早口の詠唱", ["CAST_REDUCTION", "COOLDOWN_REDUCTION"]),
            ("魔弾の奥義", ["SKILL_DAMAGE", "AP_PERCENT"]), ("縛る術", ["CC_DURATION", "AP_PERCENT"]),
        ]},
    "HEALER": {
        "small": ["HEALING_PERCENT", "OUTGOING_HEALING", "MANA_REGEN", "MR_PERCENT"],
        "notables": [
            ("慈雨", ["HEALING_PERCENT", "OUTGOING_HEALING"]), ("祈りの泉", ["MANA_REGEN", "MR_PERCENT"]),
            ("命の灯", ["HEALING_PERCENT", "LIFESTEAL"]), ("巡る祈り", ["COOLDOWN_REDUCTION", "MANA_REGEN"]),
            ("癒しの術理", ["HEALING_PERCENT", "AP_PERCENT"]), ("光の歩み", ["MOVE_SPEED", "OUTGOING_HEALING"]),
            ("救いの手", ["OUTGOING_HEALING", "SHIELD_POWER"]), ("聖なる泉", ["MANA_PERCENT", "HEALING_PERCENT"]),
        ]},
    "RANGER": {
        "small": ["PROJECTILE_DAMAGE", "ATTACK_SPEED", "CRIT_MULTIPLIER", "MOVE_SPEED"],
        "notables": [
            ("鷹の目", ["PROJECTILE_DAMAGE", "CRIT_MULTIPLIER"]), ("速射", ["ATTACK_SPEED", "NORMAL_DAMAGE"]),
            ("狩人の構え", ["PROJECTILE_DAMAGE", "DAMAGE"]), ("風読み", ["MOVE_SPEED", "DODGE_RECOVERY"]),
            ("急所射ち", ["CRIT_CHANCE_INCREASED", "PROJECTILE_DAMAGE"]), ("連なる矢", ["NORMAL_DAMAGE", "ATTACK_SPEED"]),
            ("遠い的", ["PROJECTILE_DAMAGE", "PHYSICAL_PEN_PERCENT"]), ("野の知恵", ["HEALTH_PERCENT", "MOVE_SPEED"]),
        ]},
    "ASSASSIN": {
        "small": ["CRIT_CHANCE_INCREASED", "MOVE_SPEED", "LIFESTEAL", "NORMAL_DAMAGE"],
        "notables": [
            ("急所狙い", ["CRIT_CHANCE_INCREASED", "CRIT_MULTIPLIER"]), ("影の足", ["DODGE_RECOVERY", "MOVE_SPEED"]),
            ("刃の嵐", ["ATTACK_SPEED", "NORMAL_DAMAGE"]), ("毒の刃", ["DOT_DAMAGE", "PHYSICAL_DAMAGE"]),
            ("一瞬の隙", ["CRIT_MULTIPLIER", "SKILL_DAMAGE"]), ("夜の衣", ["MITIGATION", "MOVE_SPEED"]),
            ("血を吸う刃", ["LIFESTEAL", "CRIT_MULTIPLIER"]), ("処刑の作法", ["DAMAGE", "CRIT_CHANCE_INCREASED"]),
        ]},
}
# Between neighbouring classes (index i and i+1): their blend.
HYBRIDS = [
    [("武人の心得", ["MELEE_DAMAGE", "AR_PERCENT"]), ("戦う聖者", ["HEALTH_PERCENT", "SHIELD_POWER"]), ("鉄壁の構え", ["AR_PERCENT", "CC_RESIST"])],
    [("祈る盾", ["SHIELD_POWER", "HEALING_PERCENT"]), ("聖なる守り手", ["MR_PERCENT", "OUTGOING_HEALING"]), ("揺るがぬ祈り", ["CC_RESIST", "MANA_REGEN"])],
    [("命の術式", ["AP_PERCENT", "HEALING_PERCENT"]), ("静かな水面", ["MANA_REGEN", "CAST_REDUCTION"]), ("雨の詠唱", ["CC_DURATION", "OUTGOING_HEALING"])],
    [("呪いの刃", ["DOT_DAMAGE", "CRIT_CHANCE_INCREASED"]), ("影の術式", ["MAGICAL_PEN_PERCENT", "CRIT_MULTIPLIER"]), ("闇の詠唱", ["CRIT_CHANCE_INCREASED", "MAGICAL_DAMAGE"])],
    [("影の狩人", ["PROJECTILE_DAMAGE", "CRIT_CHANCE_INCREASED"]), ("闇夜の弓", ["CRIT_MULTIPLIER", "ATTACK_SPEED"]), ("素早い手", ["ATTACK_SPEED", "DODGE_RECOVERY"])],
    [("斬り込み", ["MOVE_SPEED", "MELEE_DAMAGE"]), ("武芸の才", ["ATTACK_SPEED", "PHYSICAL_DAMAGE"]), ("戦場の目", ["CRIT_CHANCE_INCREASED", "HEALTH_PERCENT"])],
]
# Masteries sit in the middle of a wheel: once a notable of that wheel is taken, one point buys
# one of these effects (PoE's masteries). Drafts per region.
# Masteries sit in the middle of a wheel: once a notable of that wheel is taken, one point buys one of these
# (PoE's masteries); one choice per wheel, and the same choice only once in the whole tree. Each choice is stats.
MASTERIES = {
    "WARRIOR": [{"HEAL_ON_KILL": 3}, {"BREAK_POWER": 15}, {"MITIGATION@LOW_LIFE": 10}],
    "TEMPLAR": [{"SHIELD_POWER": 15}, {"CC_RESIST": 20}, {"AR_PERCENT@STILL": 25}],
    "MAGE": [{"CRIT_CHANCE_INCREASED@VS_CONTROLLED": 50}, {"CAST_REDUCTION@FULL_LIFE": 15}, {"MAGICAL_PEN_PERCENT": 8}],
    "HEALER": [{"OVERHEAL_SHIELD": 50}, {"OUTGOING_HEALING": 10}, {"INCOMING_HEALING": 15}],
    "RANGER": [{"DAMAGE@STILL": 12}, {"NORMAL_DAMAGE@AFTER_DODGE": 30}, {"CRIT_MULTIPLIER@FULL_LIFE": 20}],
    "ASSASSIN": [{"DAMAGE@AFTER_DODGE": 15}, {"DODGE_RECOVERY@RECENT_KILL": 30}, {"MITIGATION@MOVING": 8}],
    "HYBRID": [{"HEALTH_PERCENT": 5, "MANA_PERCENT": 5}, {"COOLDOWN_REDUCTION": 5}, {"DAMAGE@VS_BOSS": 8}],
}
CENTRE_NOTABLES = [("原点の力", ["DAMAGE", "HEALTH_PERCENT"]), ("巡る力", ["COOLDOWN_REDUCTION", "MANA_REGEN"]),
                   ("均衡", ["DAMAGE", "CRIT_MULTIPLIER"])]

# Keystones change a rule and cost something. Placed at the rim; drafts until the Creator picks.
# Keystones change a rule and cost something (CoreKeystone, by code). Placed at the rim. Open to every class.
KEYSTONES = [
    ("WARRIOR", 0, "BERSERKER_BLOOD", "狂戦士の血", "HPが減るほど与ダメージが増える（最大+40%）。受ける回復-30%。"),
    ("WARRIOR", 1, "IRON_WILL", "鉄の意志", "回避できなくなる。被ダメージ-20%、CCを受けない。"),
    ("TEMPLAR", 0, "SANCTUARY", "聖域", "自分が得たシールドの半分が近くの味方にも付く。自分の与ダメージ-15%。"),
    ("TEMPLAR", 1, "MANA_SHIELD", "魔力の盾", "受けるダメージの30%を、HPの代わりにマナで受ける。"),
    ("MAGE", 0, "CREEPING_ROT", "蝕む術理", "継続ダメージも会心する。直接ダメージ-20%。"),
    ("MAGE", 1, "GLASS_CANNON", "硝子の大砲", "最大HP-30%。与ダメージ+35%。"),
    ("HEALER", 0, "LIFE_CYCLE", "命の循環", "味方を回復すると、その半分だけ自分も回復する。与ダメージ-10%。"),
    ("HEALER", 1, "BLOOD_PACT", "血の契約", "技がマナの代わりにHPを使う。マナは回復しない。"),
    ("RANGER", 0, "HUNTER_PATIENCE", "狩人の忍耐", "2秒動かないと会心率が2倍。動くと解除。"),
    ("RANGER", 1, "ENDLESS_HUNT", "終わりなき狩り", "敵を倒すたび全ての技のCDが1秒縮む。最大HP-15%。"),
    ("ASSASSIN", 0, "SHADOW_STEP", "影の歩み", "回避の後2秒、与ダメージ+30%。被ダメージ+10%。"),
    ("ASSASSIN", 1, "ONE_STRIKE", "一撃の美学", "会心しなくなる。代わりに与ダメージ+30%。"),
    ("HYBRID", 0, "BREAKER", "崩しの達人", "ボスのブレイクゲージが50%多く溜まる。ボス以外への与ダメージ-15%。"),
    ("HYBRID", 2, "LONE_WOLF", "孤高", "パーティにいないとき与ダメージ+20%・被ダメージ-10%。"),
    ("HYBRID", 4, "MANA_CYCLE", "循環する魔力", "CD回復速度+30%。最大マナ-40%。"),
]

nodes: list[dict] = []
edges: dict[tuple[int, int], bool] = {}
groups: list[dict] = []


def polar(r, deg):
    a = math.radians(deg)
    return r * math.cos(a), r * math.sin(a)


def add_group(x, y):
    groups.append({"id": len(groups), "x": round(x, 1), "y": round(y, 1)})
    return groups[-1]["id"]


def add_node(group, orbit, slot, kind, name, stats, **extra):
    g = groups[group]
    angle = 2 * math.pi * slot / ORBIT_SLOTS[orbit] - math.pi / 2
    nodes.append({"id": len(nodes), "group": group, "orbit": orbit, "slot": slot,
                  "x": round(g["x"] + ORBIT_RADII[orbit] * math.cos(angle), 1),
                  "y": round(g["y"] + ORBIT_RADII[orbit] * math.sin(angle), 1),
                  "kind": kind, "name": name, "stats": stats, **extra})
    return len(nodes) - 1


def free_node(x, y, kind, name, stats, **extra):
    """A node alone in its own group (travel nodes along a path)."""
    return add_node(add_group(x, y), 0, 0, kind, name, stats, **extra)


def link(a, b):
    if a != b:
        na, nb = nodes[a], nodes[b]
        edges[(min(a, b), max(a, b))] = na["group"] == nb["group"] and na["orbit"] == nb["orbit"] and na["orbit"] > 0


def small(stat):
    label, value, _ = STATS[stat]
    return "SMALL", label, {stat: value}


def notable(name, pair, scale=1.0):
    return "NOTABLE", name, {s: round(STATS[s][2] * scale) for s in pair}


def nearest(ids, x, y):
    return min(ids, key=lambda i: (nodes[i]["x"] - x) ** 2 + (nodes[i]["y"] - y) ** 2)


def centre(ids):
    return sum(nodes[i]["x"] for i in ids) / len(ids), sum(nodes[i]["y"] for i in ids) / len(ids)


def path(a, b, stats, spacing=165):
    """Join two nodes with evenly spaced small travel nodes on the straight line between them."""
    ax, ay, bx, by = nodes[a]["x"], nodes[a]["y"], nodes[b]["x"], nodes[b]["y"]
    count = max(0, round(math.hypot(bx - ax, by - ay) / spacing) - 1)
    prev = a
    for k in range(1, count + 1):
        t = k / (count + 1)
        i = free_node(ax + (bx - ax) * t, ay + (by - ay) * t, *small(stats[(k - 1) % len(stats)]))
        link(prev, i)
        prev = i
    link(prev, b)


def theme_at(deg):
    """Class region, or the hybrid wedge between class i and i+1."""
    rel = (deg - CLASSES[0][2]) % 360
    i, frac = int(rel // 60), (rel % 60) / 60
    if 0.3 <= frac <= 0.7:
        return "HYBRID", i
    k = (i + (1 if frac > 0.7 else 0)) % 6
    return CLASSES[k][0], k


used_notables: dict[str, int] = {}


def next_notable(theme, idx, depth):
    pool = HYBRIDS[idx] if theme == "HYBRID" else THEMES[theme]["notables"]
    key = f"{theme}{idx}"
    k = used_notables.get(key, 0)
    used_notables[key] = k + 1
    name, pair = pool[k % len(pool)]
    if k >= len(pool):
        name += "・改" if k < 2 * len(pool) else "・極"
    kind, label, stats = notable(name, pair, 1.0 + 0.25 * depth)  # deeper notables are stronger
    if depth >= 2:
        # Deeper notables hang their second value on a condition of the region, at twice the size.
        second = pair[1]
        cond = THEME_CONDITIONS[theme][k % 2]
        if cond in TARGET_CONDITIONS and second not in OFFENSE:
            # "against a boss" only makes sense for what you deal; defence takes the region's other condition.
            cond = next((c for c in THEME_CONDITIONS[theme] if c not in TARGET_CONDITIONS), "RECENT_KILL")
        stats = {stats_key: v for stats_key, v in stats.items() if stats_key != second}
        stats[f"{second}@{cond}"] = round(STATS[second][2] * (1.0 + 0.25 * depth) * 2)
    return kind, label, stats


CASTERS = {"MAGE", "HEALER"}  # AP only exists on their staff/tome (CoreCombatSheet), so AP nodes belong to them alone


def small_stats(theme, idx):
    if theme == "HYBRID":
        a, b = CLASSES[idx][0], CLASSES[(idx + 1) % 6][0]
        both = [THEMES[a]["small"][0], THEMES[b]["small"][0]]
        # A wedge shared with a non-caster must not make that class walk through AP it can never use.
        return ["SKILL_DAMAGE" if s == "AP_PERCENT" and not {a, b} <= CASTERS else s for s in both]
    return THEMES[theme]["small"]


def wheel(x, y, theme, idx, depth, notables_count=3):
    """PoE's notable wheel: a horseshoe on orbit 2 with notables spaced among small nodes."""
    g = add_group(x, y)
    slots = [0, 2, 4, 6, 8, 10, 12, 14]
    nset = set(slots[1::len(slots) // notables_count][:notables_count])
    stats = small_stats(theme, idx)
    ids = [add_node(g, 2, s, *(next_notable(theme, idx, depth) if s in nset else small(stats[k % len(stats)])))
           for k, s in enumerate(slots)]
    for a, b in zip(ids, ids[1:]):
        link(a, b)
    add_node(g, 0, 0, "MASTERY", "極意", {}, choices=MASTERIES[theme])
    return ids


def cluster(x, y, theme, idx, depth):
    """A small triangle on orbit 1 with one notable at its tip."""
    g = add_group(x, y)
    stats = small_stats(theme, idx)
    a = add_node(g, 1, 0, *small(stats[0]))
    b = add_node(g, 1, 2, *small(stats[1 % len(stats)]))
    c = add_node(g, 1, 4, *next_notable(theme, idx, depth))
    link(a, b); link(b, c); link(c, a)
    return [a, b, c]


def junction(x, y, theme, idx):
    return [free_node(x, y, *small(rng.choice(small_stats(theme, idx))))]


# --- build ----------------------------------------------------------------------------------
hub = add_group(0, 0)
hub_ring = [add_node(hub, 3, s, *small("DAMAGE" if s % 4 == 0 else "HEALTH_PERCENT")) for s in range(0, 16, 2)]
for a, b in zip(hub_ring, hub_ring[1:] + hub_ring[:1]):
    link(a, b)
for k, (n, p) in enumerate(CENTRE_NOTABLES):
    c = add_node(hub, 1, k * 2, *notable(n, p))
    link(c, nearest(hub_ring, nodes[c]["x"] * 4, nodes[c]["y"] * 4))

START_R = 640
starts = []
for job, label, deg in CLASSES:
    x, y = polar(START_R, deg)
    i = free_node(x, y, "START", f"{label}の起点", {}, start=job)
    starts.append(i)
    path(i, nearest(hub_ring, x, y), ["DAMAGE"], spacing=110)
# The inner ring between neighbouring starts: short hybrid walks around the middle.
for k in range(6):
    mx, my = polar(START_R + 60, CLASSES[k][2] + 30)
    m = free_node(mx, my, *next_notable("HYBRID", k, 0))
    path(starts[k], m, small_stats("HYBRID", k), spacing=120)
    path(m, starts[(k + 1) % 6], small_stats("HYBRID", k), spacing=120)

# Lanes: three per class, 20 degrees apart; groups where they cross the radius bands.
BANDS = [950, 1320, 1690, 2060, 2430, 2800]  # six bands since the cap went to Lv70 (69 points)
LANES = [CLASSES[k][2] + off for k in range(6) for off in (-20, 0, 20)]
grid: dict[tuple[int, int], list[int]] = {}
for li, deg in enumerate(LANES):
    theme, idx = theme_at(deg)
    for bi, r in enumerate(BANDS):
        x, y = polar(r + (130 if li % 2 else 0) + rng.uniform(-40, 40), deg + rng.uniform(-2.5, 2.5))
        pattern = (li * 7 + bi * 3) % 5
        if bi == 0:
            grid[(li, bi)] = cluster(x, y, theme, idx, bi)
        elif li % 3 == 1 and bi in (2, 4) or pattern == 0:
            grid[(li, bi)] = wheel(x, y, theme, idx, bi)
        elif pattern in (1, 3):
            grid[(li, bi)] = cluster(x, y, theme, idx, bi)
        else:
            grid[(li, bi)] = junction(x, y, theme, idx)

for k in range(6):
    for off in range(3):
        s = starts[k]
        path(s, nearest(grid[(k * 3 + off, 0)], nodes[s]["x"], nodes[s]["y"]), THEMES[CLASSES[k][0]]["small"])
for li in range(len(LANES)):
    theme, idx = theme_at(LANES[li])
    for bi in range(len(BANDS) - 1):
        a = nearest(grid[(li, bi)], *centre(grid[(li, bi + 1)]))
        path(a, nearest(grid[(li, bi + 1)], nodes[a]["x"], nodes[a]["y"]), small_stats(theme, idx))
# Rungs between neighbouring lanes on alternate bands, so routes loop and regions blend.
for li in range(len(LANES)):
    lj = (li + 1) % len(LANES)
    for bi in range(len(BANDS)):
        if (li + bi) % 2:
            continue
        a = nearest(grid[(li, bi)], *centre(grid[(lj, bi)]))
        theme, idx = theme_at(LANES[li] + 10)
        path(a, nearest(grid[(lj, bi)], nodes[a]["x"], nodes[a]["y"]), small_stats(theme, idx), spacing=170)

KEY_R = 3190
class_index = {c[0]: k for k, c in enumerate(CLASSES)}
for owner, which, code, name, text in KEYSTONES:
    if owner == "HYBRID":
        li = which * 3 + 2
        deg = LANES[li] + 10
    else:
        li = class_index[owner] * 3 + (0 if which == 0 else 2)
        deg = LANES[li]
    x, y = polar(KEY_R, deg)
    key = free_node(x, y, "KEYSTONE", name, {}, text=text, code=code)
    path(nearest(grid[(li, len(BANDS) - 1)], x, y), key, ["DAMAGE", "HEALTH_PERCENT"], spacing=110)


def check():
    adj = {n["id"]: set() for n in nodes}
    for a, b in edges:
        adj[a].add(b); adj[b].add(a)
    seen, todo = {starts[0]}, [starts[0]]
    while todo:
        for n in adj[todo.pop()] - seen:
            seen.add(n); todo.append(n)
    assert len(seen) == sum(1 for n in nodes if n["kind"] != "MASTERY"), "some nodes are cut off"
    return sum(1 for i, a in enumerate(nodes) for b in nodes[i + 1:] if math.hypot(a["x"] - b["x"], a["y"] - b["y"]) < 45)


def stat_text(stats):
    parts = []
    for key, v in stats.items():
        stat, _, cond = key.partition("@")
        parts.append((CONDITIONS[cond] + "、" if cond else "") + f"{STATS[stat][0]}+{v}%")
    return " / ".join(parts)


def describe(n):
    if n["kind"] == "START":
        return "ここから伸ばす"
    if n["kind"] == "KEYSTONE":
        return n["text"]
    if n["kind"] == "MASTERY":
        return "この輪の主要ノードを取ると、1点で次から1つ選べる：" + "／".join(stat_text(c) for c in n["choices"])
    return stat_text(n["stats"])


close = check()
OUT.mkdir(parents=True, exist_ok=True)
data = {"orbitRadii": ORBIT_RADII, "orbitSlots": ORBIT_SLOTS, "classes": [[c[0], c[1]] for c in CLASSES],
        "groups": groups, "conditions": CONDITIONS,
        "nodes": [dict(n, text=describe(n), **({"choiceTexts": [stat_text(c) for c in n["choices"]]} if n["kind"] == "MASTERY" else {}))
                  for n in nodes],
        "edges": [[a, b, int(arc)] for (a, b), arc in sorted(edges.items())]}
ART.parent.mkdir(parents=True, exist_ok=True)
ART.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
(OUT / "passive-tree.json").unlink(missing_ok=True)
adjacency = {n["id"]: [] for n in nodes}
for a, b in sorted(edges):
    adjacency[a].append(b); adjacency[b].append(a)
rows = ["# id\tx\ty\tkind\tname\tstart\tstats\tlinks\tgroup\textra  (generated by scripts/build_passive_tree.py; x/y in tree "
        "units; stats are STAT=v or STAT@CONDITION=v; extra is the keystone code, or the mastery choices as stat lists split by |)"]
for n in nodes:
    stats = ",".join(f"{k}={v}" for k, v in n["stats"].items())
    extra = n.get("code") or "|".join(",".join(f"{k}={v}" for k, v in c.items()) for c in n.get("choices", [])) or "-"
    rows.append("\t".join(map(str, [n["id"], round(n["x"]), round(n["y"]), n["kind"], n["name"], n.get("start") or "-",
                                     stats or "-", ",".join(map(str, sorted(adjacency[n["id"]]))), n["group"], extra])))
TSV.write_text("\n".join(rows) + "\n", encoding="utf-8")
template = (ROOT / "scripts/passive_tree_preview.html").read_text(encoding="utf-8")
(OUT / "passive-tree.html").write_text(template.replace("/*DATA*/null", json.dumps(data, ensure_ascii=False)), encoding="utf-8")
kinds: dict[str, int] = {}
for n in nodes:
    kinds[n["kind"]] = kinds.get(n["kind"], 0) + 1
print(f"{len(nodes)} nodes {kinds}, {len(edges)} edges, {close} node pairs closer than 45")
