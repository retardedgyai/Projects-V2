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

Writes docs/proposals/skill-tree-2026-10/passive-tree.json and passive-tree.html.
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/proposals/skill-tree-2026-10"

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
    "FIRE": ("火属性値", 6, 15), "ICE": ("氷属性値", 6, 15), "LIGHTNING": ("雷属性値", 6, 15),
    "MAGICAL_PEN_PERCENT": ("魔法貫通", 4, 10), "PHYSICAL_PEN_PERCENT": ("物理貫通", 4, 10),
    "INCOMING_HEALING": ("被回復量", 6, 15),
}

# Six classes clockwise from the top, so neighbours share a hybrid wedge:
# 力 (warrior) - 力魔 (templar) - 魔 (mage) - 魔技 (healer) - 技 (ranger) - 技力 (assassin).
CLASSES = [
    ("WARRIOR", "戦士", -90),
    ("TEMPLAR", "テンプラー", -30),
    ("MAGE", "メイジ", 30),
    ("HEALER", "ヒーラー", 90),
    ("RANGER", "レンジャー", 150),
    ("ASSASSIN", "アサシン", 210),
]

# Each region: travel stats for small nodes, then notables (name, stats) inner -> outer.
THEMES = {
    "WARRIOR": {
        "small": ["MELEE_DAMAGE", "HEALTH_PERCENT", "AD_PERCENT", "PHYSICAL_DAMAGE"],
        "notables": [
            ("剛力", ["AD_PERCENT", "MELEE_DAMAGE"]), ("鋼の体", ["HEALTH_PERCENT", "AR_PERCENT"]),
            ("大振り", ["MELEE_DAMAGE", "SKILL_DAMAGE"]), ("不屈", ["HEALTH_PERCENT", "CC_RESIST"]),
            ("血の渇き", ["LIFESTEAL", "MELEE_DAMAGE"]), ("重い一撃", ["PHYSICAL_DAMAGE", "CRIT_MULTIPLIER"]),
            ("戦場の心得", ["DAMAGE", "MITIGATION"]), ("鉄塊の主", ["AD_PERCENT", "PHYSICAL_PEN_PERCENT"]),
        ]},
    "TEMPLAR": {
        "small": ["AR_PERCENT", "SHIELD_POWER", "MR_PERCENT", "HEALTH_PERCENT"],
        "notables": [
            ("城壁", ["AR_PERCENT", "MR_PERCENT"]), ("誓いの盾", ["SHIELD_POWER", "CC_RESIST"]),
            ("聖なる守り", ["MR_PERCENT", "MITIGATION"]), ("不動の心", ["CC_RESIST", "HEALTH_PERCENT"]),
            ("守りの光", ["SHIELD_POWER", "OUTGOING_HEALING"]), ("堅牢", ["AR_PERCENT", "MITIGATION"]),
            ("裁きの槍", ["MELEE_DAMAGE", "AP_PERCENT"]), ("揺るがぬ誓い", ["MITIGATION", "SHIELD_POWER"]),
        ]},
    "MAGE": {
        "small": ["AP_PERCENT", "MAGICAL_DAMAGE", "CAST_REDUCTION", "MANA_PERCENT"],
        "notables": [
            ("深淵の知", ["AP_PERCENT", "MAGICAL_DAMAGE"]), ("早口の詠唱", ["CAST_REDUCTION", "COOLDOWN_REDUCTION"]),
            ("火の心", ["FIRE", "MAGICAL_DAMAGE"]), ("氷の心", ["ICE", "MAGICAL_DAMAGE"]),
            ("雷の心", ["LIGHTNING", "MAGICAL_DAMAGE"]), ("満ちる魔力", ["MANA_PERCENT", "MANA_REGEN"]),
            ("魔弾の奥義", ["SKILL_DAMAGE", "AP_PERCENT"]), ("術の貫き", ["MAGICAL_PEN_PERCENT", "AP_PERCENT"]),
        ]},
    "HEALER": {
        "small": ["HEALING_PERCENT", "OUTGOING_HEALING", "MANA_REGEN", "MR_PERCENT"],
        "notables": [
            ("慈雨", ["HEALING_PERCENT", "OUTGOING_HEALING"]), ("祈りの泉", ["MANA_REGEN", "MR_PERCENT"]),
            ("命の灯", ["INCOMING_HEALING", "HEALTH_PERCENT"]), ("巡る祈り", ["COOLDOWN_REDUCTION", "MANA_REGEN"]),
            ("癒しの術理", ["HEALING_PERCENT", "AP_PERCENT"]), ("光の歩み", ["MOVE_SPEED", "OUTGOING_HEALING"]),
            ("救いの手", ["OUTGOING_HEALING", "SHIELD_POWER"]), ("聖なる泉", ["MANA_PERCENT", "HEALING_PERCENT"]),
        ]},
    "RANGER": {
        "small": ["PROJECTILE_DAMAGE", "ATTACK_SPEED", "CRIT_MULTIPLIER", "MOVE_SPEED"],
        "notables": [
            ("鷹の目", ["PROJECTILE_DAMAGE", "CRIT_MULTIPLIER"]), ("速射", ["ATTACK_SPEED", "NORMAL_DAMAGE"]),
            ("狩人の構え", ["PROJECTILE_DAMAGE", "DAMAGE"]), ("風読み", ["MOVE_SPEED", "ATTACK_SPEED"]),
            ("急所射ち", ["CRIT_CHANCE_INCREASED", "PROJECTILE_DAMAGE"]), ("連なる矢", ["NORMAL_DAMAGE", "ATTACK_SPEED"]),
            ("遠い的", ["PROJECTILE_DAMAGE", "PHYSICAL_PEN_PERCENT"]), ("野の知恵", ["HEALTH_PERCENT", "MOVE_SPEED"]),
        ]},
    "ASSASSIN": {
        "small": ["CRIT_CHANCE_INCREASED", "MOVE_SPEED", "LIFESTEAL", "NORMAL_DAMAGE"],
        "notables": [
            ("急所狙い", ["CRIT_CHANCE_INCREASED", "CRIT_MULTIPLIER"]), ("影の足", ["MOVE_SPEED", "LIFESTEAL"]),
            ("刃の嵐", ["ATTACK_SPEED", "NORMAL_DAMAGE"]), ("毒の刃", ["PHYSICAL_DAMAGE", "CRIT_CHANCE_INCREASED"]),
            ("一瞬の隙", ["CRIT_MULTIPLIER", "SKILL_DAMAGE"]), ("夜の衣", ["MITIGATION", "MOVE_SPEED"]),
            ("血を吸う刃", ["LIFESTEAL", "CRIT_MULTIPLIER"]), ("処刑の作法", ["DAMAGE", "CRIT_CHANCE_INCREASED"]),
        ]},
}
# Between neighbouring classes (index i and i+1): their blend.
HYBRIDS = [
    [("武人の心得", ["MELEE_DAMAGE", "AR_PERCENT"]), ("戦う聖者", ["HEALTH_PERCENT", "SHIELD_POWER"]), ("鉄壁の構え", ["AR_PERCENT", "CC_RESIST"])],
    [("聖なる炎", ["FIRE", "MR_PERCENT"]), ("祈る術士", ["AP_PERCENT", "SHIELD_POWER"]), ("守る魔力", ["MANA_PERCENT", "MITIGATION"])],
    [("命の術式", ["AP_PERCENT", "HEALING_PERCENT"]), ("静かな水面", ["MANA_REGEN", "CAST_REDUCTION"]), ("雨の詠唱", ["ICE", "OUTGOING_HEALING"])],
    [("森の癒し", ["MOVE_SPEED", "HEALING_PERCENT"]), ("風の祈り", ["ATTACK_SPEED", "MANA_REGEN"]), ("見守る目", ["PROJECTILE_DAMAGE", "OUTGOING_HEALING"])],
    [("影の狩人", ["PROJECTILE_DAMAGE", "CRIT_CHANCE_INCREASED"]), ("闇夜の弓", ["CRIT_MULTIPLIER", "ATTACK_SPEED"]), ("素早い手", ["ATTACK_SPEED", "MOVE_SPEED"])],
    [("血の刃", ["MELEE_DAMAGE", "LIFESTEAL"]), ("戦場の影", ["CRIT_CHANCE_INCREASED", "HEALTH_PERCENT"]), ("斬り込み", ["MOVE_SPEED", "MELEE_DAMAGE"])],
]
# Masteries sit in the middle of a wheel: once a notable of that wheel is taken, one point buys
# one of these effects (PoE's masteries). Drafts per region.
MASTERIES = {
    "WARRIOR": ["敵を倒すとHPを3%回復", "獣が溜まる速さ+25%", "HPが半分以下のとき被ダメージ-10%"],
    "TEMPLAR": ["シールドが切れた瞬間、周りの敵を1秒怯ませる", "CCを受けると2秒間被ダメージ-20%", "近くの味方1人ごとに物理防御+5%"],
    "MAGE": ["違う属性の技を続けて当てると与ダメージ+10%", "マナが満タンのとき詠唱速度+15%", "凍った敵への会心率+50%"],
    "HEALER": ["回復しすぎた分の半分がシールドになる", "味方を回復すると2秒間移動速度+10%", "HPが一番低い味方への回復量+20%"],
    "RANGER": ["遠い敵ほど与ダメージ増加（最大+15%）", "回避の後、次の通常攻撃が2発になる", "同じ敵を撃ち続けると会心倍率が上がる"],
    "ASSASSIN": ["背後からの攻撃の与ダメージ+20%", "会心で倒すと回避のCDが戻る", "移動中の被ダメージ-8%"],
    "HYBRID": ["最大HP+5%・最大マナ+5%", "全ての技のCD回復速度+5%", "ボスへの与ダメージ+8%"],
}
CENTRE_NOTABLES = [("原点の力", ["DAMAGE", "HEALTH_PERCENT"]), ("巡る力", ["COOLDOWN_REDUCTION", "MANA_REGEN"]),
                   ("均衡", ["AD_PERCENT", "AP_PERCENT"])]

# Keystones change a rule and cost something. Placed at the rim; drafts until the Creator picks.
KEYSTONES = [
    ("WARRIOR", 0, "狂戦士の血", "HPが減るほど与ダメージが増える（最大+40%）。受ける回復-30%。"),
    ("WARRIOR", 1, "鉄の意志", "回避できなくなる。被ダメージ-20%、CC無効。"),
    ("TEMPLAR", 0, "聖域", "自分のシールドの半分が近くの味方にも付く。自分の与ダメージ-15%。"),
    ("TEMPLAR", 1, "魔力の盾", "受けるダメージの30%をHPの代わりにマナで受ける。"),
    ("MAGE", 0, "元素の同盟", "火・氷・雷の属性値を、一番高い属性に全部まとめる。"),
    ("MAGE", 1, "硝子の大砲", "最大HP-30%。与ダメージ+35%。"),
    ("HEALER", 0, "命の循環", "味方を回復すると、その半分だけ自分も回復する。与ダメージ-10%。"),
    ("HEALER", 1, "血の契約", "技がマナの代わりにHPを使う。マナ回復は止まる。"),
    ("RANGER", 0, "狩人の忍耐", "2秒動かないと会心率が2倍。動くと解除。"),
    ("RANGER", 1, "終わりなき狩り", "敵を倒すたび全ての技のCDが1秒縮む。最大HP-15%。"),
    ("ASSASSIN", 0, "影の歩み", "回避の後2秒、与ダメージ+30%。被ダメージ+10%。"),
    ("ASSASSIN", 1, "一撃の美学", "会心しなくなる。代わりに与ダメージ+30%、必ず当たる。"),
    ("HYBRID", 0, "崩しの達人", "ボスの崩しゲージが50%多く溜まる。ボス以外への与ダメージ-15%。"),
    ("HYBRID", 2, "孤高", "パーティにいないとき与ダメージ+20%・被ダメージ-10%。"),
    ("HYBRID", 4, "循環する魔力", "CD回復速度+30%。最大マナ-40%。"),
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
    return notable(name, pair, 1.0 + 0.25 * depth)  # deeper notables are stronger


def small_stats(theme, idx):
    if theme == "HYBRID":
        return [THEMES[CLASSES[idx][0]]["small"][0], THEMES[CLASSES[(idx + 1) % 6][0]]["small"][0]]
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
BANDS = [950, 1320, 1690, 2060]
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

KEY_R = 2450
class_index = {c[0]: k for k, c in enumerate(CLASSES)}
for owner, which, name, text in KEYSTONES:
    if owner == "HYBRID":
        li = which * 3 + 2
        deg = LANES[li] + 10
    else:
        li = class_index[owner] * 3 + (0 if which == 0 else 2)
        deg = LANES[li]
    x, y = polar(KEY_R, deg)
    key = free_node(x, y, "KEYSTONE", name, {}, text=text)
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


def describe(n):
    if n["kind"] == "START":
        return "ここから伸ばす"
    if n["kind"] == "KEYSTONE":
        return n["text"]
    if n["kind"] == "MASTERY":
        return "この輪の主要ノードを取ると、1点で次から1つ選べる：" + "／".join(n["choices"])
    return " / ".join(f"{STATS[s][0]}+{v}%" for s, v in n["stats"].items())


close = check()
OUT.mkdir(parents=True, exist_ok=True)
data = {"orbitRadii": ORBIT_RADII, "orbitSlots": ORBIT_SLOTS, "classes": [[c[0], c[1]] for c in CLASSES],
        "groups": groups, "nodes": [dict(n, text=describe(n)) for n in nodes],
        "edges": [[a, b, int(arc)] for (a, b), arc in sorted(edges.items())]}
(OUT / "passive-tree.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
template = (ROOT / "scripts/passive_tree_preview.html").read_text(encoding="utf-8")
(OUT / "passive-tree.html").write_text(template.replace("/*DATA*/null", json.dumps(data, ensure_ascii=False)), encoding="utf-8")
kinds: dict[str, int] = {}
for n in nodes:
    kinds[n["kind"]] = kinds.get(n["kind"], 0) + 1
print(f"{len(nodes)} nodes {kinds}, {len(edges)} edges, {close} node pairs closer than 45")
