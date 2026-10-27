#!/usr/bin/env python3
"""Lay out the shared passive tree on a grid and write the server table plus a preview page.

Every edge runs along one of the 8 grid directions, so the in-game chest view can draw it
with path cells. Run from the repository root:

    python3 scripts/build_passive_tree.py

Writes server-minestom/src/main/resources/passive-tree.tsv and
docs/proposals/skill-tree-2026-10/passive-tree.html.
"""
from __future__ import annotations

import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TSV = ROOT / "server-minestom/src/main/resources/passive-tree.tsv"
PAGE = ROOT / "docs/proposals/skill-tree-2026-10/passive-tree.html"

W, H = 45, 39
CENTER = (22, 19)

# Stat name (CoreAffixStat), short label, small value, notable value.
STATS = {
    "DAMAGE": ("与ダメージ", 4, 10), "SKILL_DAMAGE": ("スキルダメージ", 4, 10),
    "NORMAL_DAMAGE": ("通常攻撃ダメージ", 5, 12), "MELEE_DAMAGE": ("近接ダメージ", 5, 12),
    "PROJECTILE_DAMAGE": ("投射物ダメージ", 5, 12), "MAGICAL_DAMAGE": ("魔法ダメージ", 5, 12),
    "PHYSICAL_DAMAGE": ("物理ダメージ", 5, 12), "AD_PERCENT": ("物理攻撃力 AD", 5, 12),
    "AP_PERCENT": ("魔法攻撃力 AP", 5, 12), "ATTACK_SPEED": ("攻撃速度", 3, 8),
    "COOLDOWN_REDUCTION": ("CD回復速度", 3, 8), "CAST_REDUCTION": ("詠唱速度", 3, 8),
    "CRIT_CHANCE_INCREASED": ("クリティカル率増加", 8, 20), "CRIT_MULTIPLIER": ("クリティカル倍率", 6, 15),
    "HEALTH_PERCENT": ("最大HP", 4, 10), "AR_PERCENT": ("物理防御 AR", 6, 15),
    "MR_PERCENT": ("魔法防御 MR", 6, 15), "MITIGATION": ("被ダメージ軽減", 2, 5),
    "SHIELD_POWER": ("シールド量", 6, 15), "HEALING_PERCENT": ("回復力", 5, 12),
    "OUTGOING_HEALING": ("与回復量", 5, 12), "MANA_PERCENT": ("最大マナ", 5, 12),
    "MANA_REGEN": ("マナ回復速度", 6, 15), "MOVE_SPEED": ("移動速度", 2, 5),
    "LIFESTEAL": ("ライフスティール", 1, 3), "CC_RESIST": ("CC耐性", 5, 12),
}

# Clockwise from the top. Neighbours share a ring, so a melee start sits next to melee.
CLASSES = [
    ("WARRIOR", "戦士", (22, 5), ["MELEE_DAMAGE", "AD_PERCENT", "HEALTH_PERCENT"]),
    ("TEMPLAR", "テンプラー", (38, 11), ["AR_PERCENT", "SHIELD_POWER", "CC_RESIST"]),
    ("HEALER", "ヒーラー", (38, 27), ["HEALING_PERCENT", "OUTGOING_HEALING", "MR_PERCENT"]),
    ("MAGE", "メイジ", (22, 33), ["AP_PERCENT", "MAGICAL_DAMAGE", "CAST_REDUCTION"]),
    ("RANGER", "レンジャー", (6, 27), ["PROJECTILE_DAMAGE", "ATTACK_SPEED", "CRIT_MULTIPLIER"]),
    ("ASSASSIN", "アサシン", (6, 11), ["CRIT_CHANCE_INCREASED", "MOVE_SPEED", "LIFESTEAL"]),
]
GENERIC = ["DAMAGE", "HEALTH_PERCENT", "COOLDOWN_REDUCTION", "SKILL_DAMAGE", "MANA_REGEN", "MITIGATION"]

# Notable names per class region: (name, two stats). Ring and spoke notables are generic.
NOTABLES = {
    "WARRIOR": [("剛力", ["MELEE_DAMAGE", "AD_PERCENT"]), ("鋼の体", ["HEALTH_PERCENT", "MITIGATION"])],
    "TEMPLAR": [("城壁", ["AR_PERCENT", "MR_PERCENT"]), ("誓いの盾", ["SHIELD_POWER", "CC_RESIST"])],
    "HEALER": [("慈雨", ["HEALING_PERCENT", "OUTGOING_HEALING"]), ("祈りの泉", ["MANA_REGEN", "MR_PERCENT"])],
    "MAGE": [("深淵の知", ["AP_PERCENT", "MAGICAL_DAMAGE"]), ("早口の詠唱", ["CAST_REDUCTION", "COOLDOWN_REDUCTION"])],
    "RANGER": [("鷹の目", ["PROJECTILE_DAMAGE", "CRIT_MULTIPLIER"]), ("速射", ["ATTACK_SPEED", "NORMAL_DAMAGE"])],
    "ASSASSIN": [("急所狙い", ["CRIT_CHANCE_INCREASED", "CRIT_MULTIPLIER"]), ("影の足", ["MOVE_SPEED", "LIFESTEAL"])],
}
RING_NOTABLES = ["武人の心得", "鉄壁の祈り", "癒しの術理", "魔弾の奥義", "狩人の構え", "刃の嵐"]
SPOKE_NOTABLES = ["鋭い一撃", "不屈", "巡る力", "技の切れ", "満ちる魔力", "不動の心"]
CENTER_NAME = "原点"

nodes: list[dict] = []
edges: set[tuple[int, int]] = set()
at: dict[tuple[int, int], int] = {}


def node(pos, kind, name, stats, start=None, group=None):
    if pos in at:
        return at[pos]
    assert 0 <= pos[0] < W and 0 <= pos[1] < H, pos
    i = len(nodes)
    nodes.append({"id": i, "x": pos[0], "y": pos[1], "kind": kind, "name": name, "stats": stats,
                  "start": start, "group": group})
    at[pos] = i
    return i


def link(a, b):
    ax, ay = nodes[a]["x"], nodes[a]["y"]
    bx, by = nodes[b]["x"], nodes[b]["y"]
    dx, dy = bx - ax, by - ay
    assert dx == 0 or dy == 0 or abs(dx) == abs(dy), (nodes[a]["name"], nodes[b]["name"])
    edges.add((min(a, b), max(a, b)))


def small(stat, group):
    label, value, _ = STATS[stat]
    return {stat: value}, f"{label}+{value}%"


def notable_stats(pair):
    return {s: STATS[s][2] for s in pair}


def walk(path, stats_cycle, group, notable=None, step=2):
    """Place small nodes every `step` cells along a polyline; the last point gets the notable."""
    ids = [at[path[0]]] if path[0] in at else []
    points = []
    for (x0, y0), (x1, y1) in zip(path, path[1:]):
        dx, dy = x1 - x0, y1 - y0
        n = max(abs(dx), abs(dy))
        sx, sy = (dx > 0) - (dx < 0), (dy > 0) - (dy < 0)
        for k in range(step, n + 1, step):
            points.append((x0 + sx * k, y0 + sy * k))
        if n % step:
            points.append((x1, y1))
    prev = at[path[0]]
    for j, p in enumerate(points):
        last = j == len(points) - 1
        if p in at:
            i = at[p]
        elif last and notable:
            name, pair = notable
            i = node(p, "NOTABLE", name, notable_stats(pair), group=group)
        else:
            stat = stats_cycle[j % len(stats_cycle)]
            i = node(p, "SMALL", STATS[stat][0], small(stat, group)[0], group=group)
        link(prev, i)
        prev = i
        ids.append(i)
    return ids


center = node(CENTER, "NOTABLE", CENTER_NAME, {"DAMAGE": 6, "HEALTH_PERCENT": 6})

for k, (job, label, pos, flavour) in enumerate(CLASSES):
    node(pos, "START", f"{label}の起点", {}, start=job, group=job)

# Ring: start -> start through a bend, alternating the two neighbours' flavours.
RING = [
    [(22, 5), (28, 5), (34, 11), (38, 11)],
    [(38, 11), (40, 13), (40, 25), (38, 27)],
    [(38, 27), (34, 27), (28, 33), (22, 33)],
    [(22, 33), (16, 33), (10, 27), (6, 27)],
    [(6, 27), (4, 25), (4, 13), (6, 11)],
    [(6, 11), (10, 11), (16, 5), (22, 5)],
]
ring_mid = []
for k, path in enumerate(RING):
    a, b = CLASSES[k], CLASSES[(k + 1) % 6]
    mix = [a[3][0], b[3][0], a[3][1], b[3][1]]
    ids = walk(path, mix, f"ring{k}")
    mid = ids[len(ids) // 2]
    nodes[mid].update(kind="NOTABLE", name=RING_NOTABLES[k], stats=notable_stats([a[3][0], b[3][0]]))
    ring_mid.append(mid)

# Spokes: start -> centre, generic stats, one notable halfway.
SPOKES = [
    [(22, 5), (22, 19)],
    [(38, 11), (32, 11), (24, 19), (22, 19)],
    [(38, 27), (32, 27), (24, 19)],
    [(22, 33), (22, 19)],
    [(6, 27), (12, 27), (20, 19), (22, 19)],
    [(6, 11), (12, 11), (20, 19)],
]
for k, path in enumerate(SPOKES):
    ids = walk(path, [GENERIC[k], GENERIC[(k + 1) % 6], CLASSES[k][3][2]], f"spoke{k}")
    halfway = ids[len(ids) // 2]
    if nodes[halfway]["kind"] == "SMALL":
        nodes[halfway].update(kind="NOTABLE", name=SPOKE_NOTABLES[k],
                              stats=notable_stats([GENERIC[k], GENERIC[(k + 1) % 6]]))

# Class clusters: two short branches outward from each start, each ending in the class notable.
OUT = {"WARRIOR": [(-1, -1), (1, -1)], "TEMPLAR": [(0, -1), (1, -1)], "HEALER": [(1, 1), (0, 1)],
       "MAGE": [(1, 1), (-1, 1)], "RANGER": [(0, 1), (-1, 1)], "ASSASSIN": [(-1, -1), (0, -1)]}
for job, label, (sx, sy), flavour in CLASSES:
    for b, (dx, dy) in enumerate(OUT[job]):
        end = (sx + dx * 4, sy + dy * 4)
        walk([(sx, sy), end], flavour[b:] + flavour[:b], job, NOTABLES[job][b])

# Keystones: a dead-end pocket off each ring notable, pointing inward; placeholders until designed.
for k, mid in enumerate(ring_mid):
    mx, my = nodes[mid]["x"], nodes[mid]["y"]
    vx, vy = CENTER[0] - mx, CENTER[1] - my
    sx, sy = (vx > 0) - (vx < 0), (vy > 0) - (vy < 0)
    p1 = (mx + sx * 2, my + sy * 2)
    a = node(p1, "SMALL", "通り道", {"DAMAGE": 2, "HEALTH_PERCENT": 2}, group=f"key{k}") if p1 not in at else at[p1]
    link(mid, a)
    p2 = (mx + sx * 4, my + sy * 4)
    key = node(p2, "KEYSTONE", f"キーストーン{k + 1}", {}, group=f"key{k}")
    link(a, key)


def check():
    # Connected from the centre, no duplicated cells, every edge 8-directional, no edge passes through a node.
    adj = {n["id"]: set() for n in nodes}
    for a, b in edges:
        adj[a].add(b); adj[b].add(a)
    seen, todo = {center}, [center]
    while todo:
        for nxt in adj[todo.pop()] - seen:
            seen.add(nxt); todo.append(nxt)
    assert len(seen) == len(nodes), "tree is not connected"
    for a, b in edges:
        ax, ay, bx, by = nodes[a]["x"], nodes[a]["y"], nodes[b]["x"], nodes[b]["y"]
        n = max(abs(bx - ax), abs(by - ay))
        sx, sy = (bx > ax) - (bx < ax), (by > ay) - (by < ay)
        for t in range(1, n):
            assert (ax + sx * t, ay + sy * t) not in at, f"edge {nodes[a]['name']}-{nodes[b]['name']} crosses a node"


def check_glyphs():
    # Node names are menu titles: every character needs a menu glyph and the title is at most 88 px wide.
    menu = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects/menu"
    def advances(name):
        rows = [l.split("\t") for l in (menu / name).read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
        return {int(r[0], 16): int(r[2]) for r in rows}
    body, emphasis = advances("glyphs.tsv"), advances("glyphs-emphasis.tsv")
    for n in nodes:
        missing = [c for c in n["name"] if ord(c) not in body]
        assert not missing, f"{n['name']}: no menu glyph for {missing}"
        assert sum(emphasis[ord(c)] for c in n["name"]) <= 88, f"{n['name']} is too wide for a menu title"


def describe(n):
    if n["kind"] == "START":
        return "ここから伸ばす"
    if n["kind"] == "KEYSTONE":
        return "中身は藍と決める"
    return " / ".join(f"{STATS[s][0]}+{v}%" for s, v in n["stats"].items())


def write_tsv():
    lines = ["# id\tx\ty\tkind\tname\tstart\tstats\tlinks  (generated by scripts/build_passive_tree.py)"]
    adj = {n["id"]: [] for n in nodes}
    for a, b in sorted(edges):
        adj[a].append(b); adj[b].append(a)
    for n in nodes:
        stats = ",".join(f"{s}={v}" for s, v in n["stats"].items())
        lines.append("\t".join(map(str, [n["id"], n["x"], n["y"], n["kind"], n["name"], n["start"] or "-",
                                         stats or "-", ",".join(map(str, sorted(adj[n["id"]])))])))
    TSV.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_page():
    data = {"w": W, "h": H, "nodes": [dict(n, text=describe(n)) for n in nodes], "edges": sorted(edges),
            "classes": [[c[0], c[1]] for c in CLASSES]}
    PAGE.parent.mkdir(parents=True, exist_ok=True)
    template = (ROOT / "scripts/passive_tree_preview.html").read_text(encoding="utf-8")
    PAGE.write_text(template.replace("/*DATA*/null", json.dumps(data, ensure_ascii=False)), encoding="utf-8")


check()
check_glyphs()
write_tsv()
write_page()
kinds = {}
for n in nodes:
    kinds[n["kind"]] = kinds.get(n["kind"], 0) + 1
print(f"{len(nodes)} nodes {kinds}, {len(edges)} edges -> {TSV.relative_to(ROOT)}, {PAGE.relative_to(ROOT)}")
