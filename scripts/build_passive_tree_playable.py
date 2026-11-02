#!/usr/bin/env python3
"""A touchable browser copy of the in-game passive tree screen (PassiveTreeFlow), built from the game's baked art.

Reads server-minestom/src/main/resources/polish05/passive-tree.json + passive-tree.zip (the sprites the game draws)
and passive-tree.tsv (links, wheels, starts, mastery codes), and writes into one folder:

  index.html        the page; the layout and rules are a JS port of PassiveTreeFlow.scene() / CorePassiveTree
  tiles_<k>.png     the 272 baked 256 px tiles stitched into 2x2 quadrants
  atlas_<k>.png     every other sprite, trimmed of transparent borders and shelf-packed (max 4096x4096)

Usage: python3 scripts/build_passive_tree_playable.py [out_dir]
"""
from __future__ import annotations

import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "server-minestom/src/main/resources"
DEFAULT_OUT = Path("/home/gyai/.config/Claude/scratch-workspaces/68773564-11bc-4020-9570-1d769b977380/"
                   "5155e154-43ce-4628-9926-60d16311db75/scratch-2026-10-17-3ad4d0/tree-play")
PREFIX = "assets/projects_ui_polish05/textures/passive_tree/"
ATLAS = 4096
PAD = 2
TILE = 256


def load_tsv():
    rows = []
    for line in (RES / "passive-tree.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        r = line.split("\t")
        assert len(r) == 10, line
        rows.append(r)
    assert all(int(r[0]) == i for i, r in enumerate(rows))
    return rows


def pack(items):
    """Shelf packing, tallest first. items: [(name, image)] -> [(atlas, name, x, y)]."""
    order = sorted(items, key=lambda t: (-t[1].height, -t[1].width, t[0]))
    placed, atlas, x, y, shelf = [], 0, 0, 0, 0
    for name, im in order:
        w, h = im.width + PAD, im.height + PAD
        if x + w > ATLAS:
            x, y, shelf = 0, y + shelf, 0
        if y + h > ATLAS:
            atlas, x, y, shelf = atlas + 1, 0, 0, 0
        placed.append((atlas, name, x, y))
        x += w
        shelf = max(shelf, h)
    return placed


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    out.mkdir(parents=True, exist_ok=True)
    for old in list(out.glob("tiles_*.png")) + list(out.glob("atlas_*.png")):
        old.unlink()
    art = json.loads((RES / "polish05/passive-tree.json").read_text(encoding="utf-8"))
    tsv = load_tsv()
    assert len(tsv) == len(art["nodes"]), "passive-tree.json と passive-tree.tsv のノード数が違います"
    z = zipfile.ZipFile(RES / "polish05/passive-tree.zip")

    def png(name):
        return Image.open(io.BytesIO(z.read(PREFIX + name.lower() + ".png"))).convert("RGBA")

    # Tiles -> 2x2 quadrants (design units = tile pixels at zoom 1).
    tiles = art["tiles"]
    cols = max(t["x"] for t in tiles) // TILE + 1
    rows = max(t["y"] for t in tiles) // TILE + 1
    splits_x = [0, (cols + 1) // 2, cols]
    splits_y = [0, (rows + 1) // 2, rows]
    by_pos = {(t["x"] // TILE, t["y"] // TILE): t["name"] for t in tiles}
    quads = []
    for qy in range(2):
        for qx in range(2):
            c0, c1, r0, r1 = splits_x[qx], splits_x[qx + 1], splits_y[qy], splits_y[qy + 1]
            im = Image.new("RGBA", ((c1 - c0) * TILE, (r1 - r0) * TILE))
            for c in range(c0, c1):
                for r in range(r0, r1):
                    if (c, r) in by_pos:
                        im.paste(png(by_pos[(c, r)]), ((c - c0) * TILE, (r - r0) * TILE))
            file = f"tiles_{len(quads)}.png"
            im.save(out / file, optimize=True)
            quads.append([file, c0 * TILE, r0 * TILE, im.width, im.height])

    # Every other sprite, trimmed and packed.
    items, trims = [], {}
    for name, s in art["sprites"].items():
        if name.startswith("tile_"):
            continue
        im = png(name)
        assert im.size == (s["width"], s["height"]), name
        box = im.getchannel("A").getbbox() or (0, 0, 1, 1)
        trims[name] = (box[0], box[1], s["width"], s["height"])
        items.append((name, im.crop(box)))
    images = dict(items)
    placed = pack(items)
    count = max(p[0] for p in placed) + 1
    sheets = []
    for a in range(count):
        mine = [p for p in placed if p[0] == a]
        w = max(x + images[n].width for _, n, x, _ in mine)
        h = max(y + images[n].height for _, n, _, y in mine)
        sheets.append(Image.new("RGBA", (w, h)))
    spr = {}
    for a, name, x, y in placed:
        im = images[name]
        sheets[a].paste(im, (x, y))
        ox, oy, fw, fh = trims[name]
        spr[name] = [a, x, y, im.width, im.height, ox, oy, fw, fh]
    atlases = []
    for a, sheet in enumerate(sheets):
        file = f"atlas_{a}.png"
        sheet.save(out / file, optimize=True)
        atlases.append(file)

    def parts(lst, key="sprite"):
        return [[p[key], p["x"], p["y"]] for p in lst]

    nodes = []
    for n, r in zip(art["nodes"], tsv):
        assert int(r[0]) == n["id"] and r[3] == n["kind"]
        codes = r[9].split("|") if r[3] == "MASTERY" else []
        assert len(codes) == len(n["choiceLabels"])
        nodes.append([n["x"], n["y"], n["kind"], n["icon"], n["text"], n["choiceTexts"], n["choiceLabels"],
                      [int(v) for v in r[7].split(",") if v], int(r[8]), "" if r[5] == "-" else r[5], codes])
    classes = [[r[5], r[4].removesuffix("の起点"), int(r[0])] for r in tsv if r[5] != "-"]
    order = ["WARRIOR", "TEMPLAR", "HEALER", "MAGE", "ASSASSIN", "RANGER"]
    classes.sort(key=lambda c: order.index(c[0]) if c[0] in order else 99)
    data = {
        "W": art["width"], "H": art["height"], "quads": quads, "atlases": atlases, "spr": spr, "nodes": nodes,
        "edges": [[e["a"], e["b"], parts(e["parts"])] for e in art["edges"]],
        "paths": [[e["a"], e["b"], parts(e["parts"])] for e in art["paths"]],
        "infos": {k: parts(v) for k, v in art["infos"].items()},
        "labels": {k: parts(v) for k, v in art["labels"].items()},
        "statLabels": art["statLabels"], "classes": classes,
    }
    blob = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    (out / "index.html").write_text(PAGE.replace("/*DATA*/null", blob), encoding="utf-8")
    total = 0
    for f in sorted(out.iterdir()):
        total += f.stat().st_size
        print(f"{f.name}\t{f.stat().st_size:,}")
    print(f"total\t{total:,}\t-> {out}")


PAGE = r"""<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>パッシブツリー（ゲーム画面）</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=DotGothic16&family=M+PLUS+2:wght@400;500;700&display=swap" rel="stylesheet">
<style>
:root { --bg:#f4f3f0; --fg:#1c1c1f; --muted:#5d5b57; --line:#d6d3cc; --card:#ffffff; --accent:#c45f1c; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --bg:#0b0c0f; --fg:#ece6dc; --muted:#a29e97; --line:#25262a; --card:#16171b; --accent:#f07a2a; } }
:root[data-theme="dark"] { --bg:#0b0c0f; --fg:#ece6dc; --muted:#a29e97; --line:#25262a; --card:#16171b; --accent:#f07a2a; }
* { box-sizing:border-box; }
html, body { margin:0; }
body { background:var(--bg); color:var(--fg); font-family:"M PLUS 2", system-ui, sans-serif; overflow-x:hidden; }
main { max-width:1680px; margin:0 auto; padding:20px 16px 32px; }
h1 { font-size:22px; font-weight:700; margin:0 0 6px; }
p.lead { margin:0 0 14px; color:var(--muted); font-size:14px; line-height:1.6; }
.bar { display:flex; flex-wrap:wrap; gap:10px 16px; align-items:center; margin:0 0 12px; font-size:14px; }
.bar select, .bar button { font:inherit; color:var(--fg); background:var(--card); border:1px solid var(--line); border-radius:8px; padding:5px 10px; }
.bar button { cursor:pointer; }
.note { color:var(--muted); font-size:13px; min-height:1.4em; }
.stage { position:relative; width:100%; aspect-ratio:16/9; background:#101116; border-radius:10px; overflow:hidden; border:1px solid var(--line); }
.stage canvas { display:block; width:100%; height:100%; touch-action:none; cursor:default; }
.stage canvas.grab { cursor:grabbing; }
.loading { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:#a29e97; font-size:14px; }
</style>
</head>
<body>
<main>
<h1>パッシブツリー（ゲーム画面）</h1>
<p class="lead">ゲームが描くのと同じ素材と配置で、パッシブツリーの画面を動かせます。ノードを押すと選び、もう一度押すとそこまでの道を取ります。ドラッグで移動、ホイールか右下の＋−で拡大縮小。Lv70（69点）、銀貨 48,300 から始まります。</p>
<div class="bar">
  <label>職業 <select id="job"></select></label>
  <button id="restart" type="button">最初から</button>
  <span class="note" id="note"></span>
</div>
<div class="stage"><canvas id="cv" width="1920" height="1080"></canvas><div class="loading" id="loading">読み込み中…</div></div>
</main>
<script>
"use strict";
const D = /*DATA*/null;

// ---------------------------------------------------------------- constants (PassiveTreeFlow companion)
const DOT = 3, HEADER = 100, LEFT_X = 56, LEFT_W = 380, RIGHT_W = 460, RIGHT_X = 1920 - 56 - RIGHT_W;
const PANEL_TOP = 110, PANEL_BOTTOM = 1018, VIEW_L = LEFT_X + LEFT_W + 4, VIEW_R = RIGHT_X;
const VIEW_CX = (VIEW_L + VIEW_R) / 2, VIEW_CY = 580, ROW = 27, MARGIN = 200, LINE = "#25262a", PANEL = "#101114";
const STAT = /^(.*)\+(\d+)%$/;
const PICK = { SMALL: 6, NOTABLE: 9, KEYSTONE: 14, START: 11, MASTERY: 6 };
const ZOOMS = [0.5, 0.75, 1.0];
const LEVEL = 70, BUDGET = LEVEL - 1, PRICE = 10 * LEVEL, SILVER = 48300;

// ---------------------------------------------------------------- rules (CorePassiveTree)
const N = D.nodes.map(([x, y, kind, icon, text, choiceTexts, choiceLabels, links, group, start, codes], id) =>
  ({ id, x, y, kind, icon, text, choiceTexts, choiceLabels, links, group, start, codes }));
const ready = i => N[i].kind !== "START" && N[i].kind !== "MASTERY";
const isMastery = i => N[i].kind === "MASTERY";
const startOf = job => N.findIndex(n => n.start === job);
function pathTo(job, taken, id) {
  if (id == null || !ready(id) || taken.has(id)) return null;
  const roots = new Set([...taken].filter(i => !isMastery(i)));
  roots.add(startOf(job));
  const seen = new Set(roots), from = new Map(), todo = [...roots];
  for (let h = 0; h < todo.length; h++) {
    const u = todo[h];
    for (const v of N[u].links) {
      if (seen.has(v) || !ready(v)) continue;
      seen.add(v); from.set(v, u);
      if (v === id) {
        const seq = [v];
        for (let p = from.get(v); p !== undefined && !roots.has(p); p = from.get(p)) seq.push(p);
        return seq.reverse();
      }
      todo.push(v);
    }
  }
  return null;
}
const wheelOpen = (taken, m) => [...taken].some(t => N[t].kind === "NOTABLE" && N[t].group === N[m].group);
function choiceTaken(masteries, id, choice) {
  const code = N[id].codes[choice];
  if (code === undefined) return false;
  for (const [m, c] of masteries) if (m !== id && N[m].codes[c] === code) return true;
  return false;
}
function reachable(job, taken) {
  const seen = new Set(), todo = [startOf(job)];
  for (let h = 0; h < todo.length; h++) for (const v of N[todo[h]].links) if (taken.has(v) && !seen.has(v)) { seen.add(v); todo.push(v); }
  return seen;
}
function refund(job, taken, id) {
  if (isMastery(id)) { const s = new Set(taken); s.delete(id); return s; }
  const rest = new Set([...taken].filter(i => !isMastery(i) && i !== id));
  const path = reachable(job, rest);
  for (const m of taken) if (isMastery(m) && wheelOpen(path, m)) path.add(m);
  return path;
}

// ---------------------------------------------------------------- the account (stands in for the server)
const A = { job: "WARRIOR", taken: new Set(), masteries: new Map(), silver: SILVER };
const budget = () => BUDGET;
function masteryChoosable(id, choice) {
  return isMastery(id) && !A.taken.has(id) && choice >= 0 && choice < N[id].codes.length &&
    wheelOpen(A.taken, id) && !choiceTaken(A.masteries, id, choice) && A.taken.size < budget();
}
/** What the account service would do; false = rejected (the screen shows st_failed). */
function serve(op) {
  const t = A.taken;
  if (op.type === "take") {
    if (isMastery(op.id)) return false;
    const p = pathTo(A.job, t, op.id);
    if (!p || t.size + p.length > budget()) return false;
    A.taken = new Set([...t, ...p]); return true;
  }
  if (op.type === "refund") {
    if (!t.has(op.id)) return false;
    const next = refund(A.job, t, op.id), cost = PRICE * (t.size - next.size);
    if (A.silver < cost) return false;
    A.silver -= cost; A.taken = next;
    for (const m of [...A.masteries.keys()]) if (!next.has(m)) A.masteries.delete(m);
    return true;
  }
  if (op.type === "mastery") {
    if (!masteryChoosable(op.id, op.choice)) return false;
    A.taken = new Set([...t, op.id]); A.masteries.set(op.id, op.choice); return true;
  }
  if (op.type === "reset") {
    const cost = PRICE * t.size;
    if (!t.size || A.silver < cost) return false;
    A.silver -= cost; A.taken = new Set(); A.masteries = new Map(); return true;
  }
  return false;
}

// ---------------------------------------------------------------- the screen state (PassiveTreeFlow)
let zoomIndex = ZOOMS.length - 1, panX = 0, panY = 0, pointerX = -1, pointerY = -1, selected = null, failed = false;
const zoom = () => ZOOMS[zoomIndex];
const minX = () => Math.min(0, 1920 - D.W * zoom()) - MARGIN;
const minY = () => Math.min(0, 1080 - D.H * zoom()) - MARGIN;
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
function centreOn(x, y) { panX = clamp(VIEW_CX - x * zoom(), minX(), MARGIN); panY = clamp(VIEW_CY - y * zoom(), minY(), MARGIN); }
function centreOnStart() { const s = N[startOf(A.job)]; centreOn(s.x, s.y); }
const overChrome = (x, y) => y < HEADER ||
  (y >= PANEL_TOP && y <= PANEL_BOTTOM && ((x >= LEFT_X && x <= LEFT_X + LEFT_W) || (x >= RIGHT_X && x <= RIGHT_X + RIGHT_W)));
function nodeAt(x, y) {
  if (x < 0 || y < 0 || overChrome(x, y)) return null;
  const z = zoom(), tx = (x - panX) / z, ty = (y - panY) / z;
  let best = null, bd = Infinity;
  for (const n of N) { const d = (n.x - tx) ** 2 + (n.y - ty) ** 2; if (d < bd) { bd = d; best = n.id; } }
  if (best == null) return null;
  const reach = Math.max(14, (PICK[N[best].kind] * DOT + 6) * z);
  return Math.hypot(N[best].x - tx, N[best].y - ty) * z <= reach ? best : null;
}
function zoomBy(step) {
  const next = clamp(zoomIndex + step, 0, ZOOMS.length - 1);
  if (next === zoomIndex) return;
  const cx = (VIEW_CX - panX) / zoom(), cy = (VIEW_CY - panY) / zoom();
  zoomIndex = next; centreOn(cx, cy);
}
function send(op) { failed = !serve(op); }
function confirmSel() {
  if (selected == null) return false;
  send(A.taken.has(selected) ? { type: "refund", id: selected } : { type: "take", id: selected });
  return true;
}
function action(act) {
  const taken = A.taken;
  switch (act) {
    case "tree": {
      const id = nodeAt(pointerX, pointerY);
      if (id == null) { if (selected == null) return false; selected = null; return true; }
      if (id === selected && !taken.has(id) && pathTo(A.job, taken, id) != null) return confirmSel();
      selected = id; failed = false; return true;
    }
    case "confirm": return confirmSel();
    case "choice:0": case "choice:1": case "choice:2": {
      const id = selected, choice = +act.slice(7);
      if (id == null || !masteryChoosable(id, choice)) return false;
      send({ type: "mastery", id, choice }); return true;
    }
    case "reset": if (!taken.size) return false; send({ type: "reset" }); return true;
    case "centre": centreOnStart(); return true;
    case "zoom:in": zoomBy(+1); return true;
    case "zoom:out": zoomBy(-1); return true;
    case "close": note("ゲームではここで画面を閉じます。"); return true;
  }
  return false;
}

// ---------------------------------------------------------------- scene building (design units, depth like UiRenderer)
// Fills sit at depth*0.005, sprites and words at depth*0.005+0.025 (UiRenderer), so z keys are depth*2 and depth*2+10.
let ops = [], hits = [], seq = 0;
const cv = document.getElementById("cv"), ctx = cv.getContext("2d");
function rect(id, x, y, w, h, color, depth, act, hover) {
  const op = { z: depth * 2, i: seq++, t: "f", x, y, w, h, c: color };
  ops.push(op);
  if (act) hits.push({ id, x, y, w, h, depth, act, hover, op });
}
function sprite(name, x, y, w, h, depth, tint) { if (D.spr[name]) ops.push({ z: depth * 2 + 10, i: seq++, t: "s", n: name, x, y, w, h, tint }); }
const full = name => { const s = D.spr[name]; return s ? [s[7], s[8]] : [0, 0]; };
function block(parts, x, y, depth, tint) { if (parts) for (const [s, px, py] of parts) { const [w, h] = full(s); sprite(s, x + px, y + py, w, h, depth, tint); } }
const width = parts => parts ? parts.reduce((a, p) => a + full(p[0])[0], 0) : 0;
const height = parts => parts ? parts.reduce((a, p) => Math.max(a, full(p[0])[1]), 0) : 0;
const label = (key, x, y, depth, tint) => block(D.labels[key], x, y, depth, tint);
function fontOf(family, size) {
  if (family === "v3num") return `${size}px "DotGothic16", monospace`;
  return `${family === "v3b" ? 700 : family === "v3m" ? 500 : 400} ${size}px "M PLUS 2", sans-serif`;
}
function text(x, y, w, value, size, color, depth, align = "left", family = "v3num") {
  ops.push({ z: depth * 2 + 10, i: seq++, t: "t", x, y, w, v: value, size, c: color, align, family });
}
function measure(value, family, size) { ctx.font = fontOf(family, size); return ctx.measureText(value).width; }
function round(x, y, w, h, r, color, depth) {
  const argb = "#ff" + color.slice(1);
  rect(null, x + r, y, w - 2 * r, h, argb, depth);
  rect(null, x, y + r, r, h - 2 * r, argb, depth);
  rect(null, x + w - r, y + r, r, h - 2 * r, argb, depth);
  for (const [k, cx, cy] of [["tl", x, y], ["tr", x + w - r, y], ["bl", x, y + h - r], ["br", x + w - r, y + h - r]])
    sprite("round_" + k, cx, cy, r, r, depth, color);
}
function framed(x, y, w, h, r, fill, depth, border = LINE) { round(x, y, w, h, r, border, depth); round(x + 1.5, y + 1.5, w - 3, h - 3, r - 1.5, fill, depth + 1); }
const alpha = a => "#" + clamp(Math.trunc(a * 255), 0, 255).toString(16).padStart(2, "0") + "121316";
const fmt = n => n.toLocaleString("en-US");

function scene() {
  ops = []; hits = []; seq = 0;
  const job = A.job, taken = A.taken, start = startOf(job);
  const lit = new Set([...taken, start]);
  const bud = budget();
  const hovered = nodeAt(pointerX, pointerY);
  const shownId = hovered ?? selected;
  const path = new Set(shownId != null ? (pathTo(job, taken, shownId) || []) : []);
  const z = zoom();

  rect(null, 0, 0, 1920, 1080, "#ff101116", 0);
  ops.push({ z: 1 * 2 + 10, i: seq++, t: "T" });
  const lines = (parts, depth) => {
    for (const [s, px, py] of parts) {
      const [w0, h0] = full(s), x = panX + px * z, y = panY + py * z, w = w0 * z, h = h0 * z;
      if (x > 1920 || y > 1080 || x + w < 0 || y + h < 0) continue;
      sprite(s, x, y, w, h, depth);
    }
  };
  for (const [a, b, parts] of D.edges) if (lit.has(a) && lit.has(b)) lines(parts, 2);
  if (path.size) for (const [p, q, parts] of D.paths) {
    if (lit.has(p) && lit.has(q)) continue;
    if ((path.has(p) || lit.has(p)) && (path.has(q) || lit.has(q))) lines(parts, 3);
  }
  const mark = (name, node, depth) => {
    if (!D.spr[name]) return;
    const [w0, h0] = full(name), x = panX + N[node].x * z, y = panY + N[node].y * z;
    if (x < -60 || y < -60 || x > 1980 || y > 1140) return;
    sprite(name, x - w0 * z / 2, y - h0 * z / 2, w0 * z, h0 * z, depth);
  };
  for (const i of lit) mark(`lit_${N[i].kind}_${N[i].icon}`, i, 4);
  const reach = new Set();
  if (taken.size < bud) for (const i of lit) for (const m of N[i].links) if (!lit.has(m) && ready(m)) reach.add(m);
  const open = taken.size < bud ? N.filter(n => n.kind === "MASTERY" && !taken.has(n.id) && wheelOpen(taken, n.id)).map(n => n.id) : [];
  const ringed = new Set([...reach, ...path, ...open]);
  if (hovered != null) ringed.add(hovered);
  for (const i of ringed) {
    if (i === selected) continue;
    const ring = i === hovered ? "hover" : reach.has(i) ? "reach" : path.has(i) ? "path" : "reach";
    mark(`ring_${ring}_${N[i].kind}`, i, 5);
  }
  if (selected != null) mark(`ring_sel_${N[selected].kind}`, selected, 6);
  rect("tree", VIEW_L, HEADER, VIEW_R - VIEW_L, 1080 - HEADER, "#00000000", 7, "tree", "#00000000");

  rect(null, 0, 0, 1920, 52, "#ff121316", 8);
  for (let k = 0; k < 6; k++) rect(null, 0, 52 + k * 16.4, 1920, 16.4, alpha(1 - (k + 0.5) / 6), 8);
  for (let k = 0; k < 3; k++) rect(null, 0, 990 + k * 9, 1920, 9, alpha((k + 0.5) / 3), 8);
  rect(null, 0, 1017, 1920, 63, "#ff121316", 8);
  label("legend", 470, 116, 9);

  header(taken, bud);
  totals(taken);
  panel(job, taken, bud, shownId, path);
  sprite("zoom_in", 460, 964, 44, 44, 10);
  rect("zoom-in-hit", 460, 964, 44, 44, "#00000000", 11, "zoom:in", "#30ffffff");
  sprite("zoom_out", 512, 964, 44, 44, 10);
  rect("zoom-out-hit", 512, 964, 44, 44, "#00000000", 11, "zoom:out", "#30ffffff");
  label("footer", LEFT_X, 1030, 10);
}

function header(taken, bud) {
  sprite("brand_slot", LEFT_X, 34, 52, 52, 12);
  label("brand", LEFT_X + 66, 30, 12);
  label("brand_sub", LEFT_X + 66, 64, 12);
  const tabs = ["tab_passive", "tab_skills", "tab_stats"];
  let tx = 960 - (tabs.reduce((s, k) => s + width(D.labels[k]), 0) + 32 * (tabs.length - 1)) / 2;
  for (const key of tabs) {
    const w = width(D.labels[key]);
    label(key, tx, 77 - height(D.labels[key]), 12);
    if (key === "tab_passive") rect(null, tx + w / 2 - 13, 87, 26, 3, "#fff07a2a", 12);
    tx += w + 32;
  }
  let right = 1920 - LEFT_X;
  const chip = (key, value, color) => {
    const lw = width(D.labels[key]), nw = measure(value, "v3num", 20), w = 18 + lw + 10 + nw + 18, x = right - w;
    framed(x, 39, w, 42, 21, PANEL, 12);
    label(key, x + 18, 60 - height(D.labels[key]) / 2, 14);
    text(x + 18 + lw + 10, 47, nw + 4, value, 20, color, 14);
    right = x - 10;
  };
  chip("chip_silver", fmt(A.silver), "#ece6dc");
  chip("chip_points", `${bud - taken.size} / ${bud}`, "#e8c878");
}

function totals(taken) {
  framed(LEFT_X, PANEL_TOP, LEFT_W, PANEL_BOTTOM - PANEL_TOP, 14, PANEL, 10);
  const ix = LEFT_X + 22, iw = LEFT_W - 44;
  label("totals", ix, 128, 12);
  text(ix, 134, iw, `${taken.size} 点`, 13, "#a29e97", 12, "right", "v3m");
  rect(null, ix, 168, iw, 1.5, "#ff" + LINE.slice(1), 12);
  const sum = new Map();
  for (const i of taken) {
    const n = N[i];
    const t = n.kind === "MASTERY" ? (A.masteries.has(i) ? (n.choiceTexts[A.masteries.get(i)] ?? "") : "")
      : (n.kind === "KEYSTONE" || n.kind === "START") ? "" : n.text;
    for (const part of t.split(" / ")) { const m = STAT.exec(part); if (m) sum.set(m[1], (sum.get(m[1]) || 0) + +m[2]); }
  }
  const keysTop = 860;
  const rows = [...sum.entries()].sort((a, b) => b[1] - a[1]);
  if (!rows.length) label("totals_empty", ix, 180, 12);
  const fit = Math.trunc((keysTop - 64 - 180) / ROW);
  rows.slice(0, fit).forEach(([name, value], k) => {
    const y = 180 + k * ROW;
    if (D.statLabels[name]) label(D.statLabels[name], ix, y, 12);
    text(ix, y + 4, iw, `+${value}%`, 15, "#ece6dc", 12, "right");
  });
  rect(null, ix, keysTop - 52, iw, 1.5, "#ff" + LINE.slice(1), 12);
  label("keystones", ix, keysTop - 42, 12);
  label("keystones_sub", ix + iw - width(D.labels["keystones_sub"]), keysTop - 37, 12);
  N.filter(n => n.kind === "KEYSTONE").forEach((n, k) =>
    sprite(taken.has(n.id) ? "key_on" : "key_off", ix + (k % 7) * 48, keysTop + Math.trunc(k / 7) * 48, 38, 38, 12));
}

function panel(job, taken, bud, shown, path) {
  framed(RIGHT_X, PANEL_TOP, RIGHT_W, PANEL_BOTTOM - PANEL_TOP, 14, PANEL, 10);
  const ix = RIGHT_X + 26, iw = RIGHT_W - 52;
  const node = shown != null ? N[shown] : null;
  const head = (node && D.infos[String(node.id)]) || D.infos.none;
  block(head, ix, PANEL_TOP + 24, 12);
  if (node) {
    const mastery = node.kind === "MASTERY";
    const sy = PANEL_TOP + 24 + height(head) + 10;
    const status = node.kind === "START" ? (node.start === job ? "here" : "other")
      : taken.has(node.id) ? "taken"
      : mastery && !wheelOpen(taken, node.id) ? "locked"
      : mastery && taken.size >= bud ? "nopoint"
      : mastery ? "pick"
      : !ready(node.id) ? "soon"
      : path.size === 0 ? "far"
      : taken.size + path.size > bud ? "short"
      : "need";
    if (status === "need") {
      label("st_need_a", ix, sy, 12);
      const nx = ix + width(D.labels["st_need_a"]) + 4, n = `${path.size}`;
      text(nx, sy + 3, 60, n, 19, "#9af0a0", 12);
      label("st_need_b", nx + measure(n, "v3num", 19) + 6, sy, 12);
    } else if (status === "short") {
      const n = `${path.size}`;
      text(ix, sy + 3, 60, n, 19, "#e58b80", 12);
      label("st_short", ix + measure(n, "v3num", 19) + 6, sy, 12);
    } else label("st_" + status, ix, sy, 12);
    if (mastery) masteryChoices(node, sy + 36);
  }
  const sel = selected != null ? N[selected] : null;
  const owned = sel != null && taken.has(sel.id);
  const refundCount = owned ? taken.size - refund(job, taken, sel.id).size : 0;
  const cost = PRICE * refundCount;
  const toTake = sel && !owned ? pathTo(job, taken, sel.id) : null;
  const isReady = sel != null && (owned ? A.silver >= cost : toTake != null && taken.size + toTake.length <= bud);
  if (owned) {
    label("cost", ix, 830, 12);
    const unit = width(D.labels["cost_unit"]);
    label("cost_unit", ix + iw - unit, 830, 12);
    text(ix, 829, iw - unit - 6, fmt(cost), 18, A.silver >= cost ? "#ece6dc" : "#e58b80", 12, "right");
  }
  if (failed) label("st_failed", ix, owned ? 796 : 826, 12);
  const by = 862;
  const state = !isReady ? "off" : owned ? "refund" : "take";
  for (let k = 0; k < 2; k++) { const nm = `go_${state}_${k}`; if (D.spr[nm]) sprite(nm, ix + k * 256, by, full(nm)[0], 72, 12); }
  if (isReady) rect("go", ix, by, iw, 72, "#00000000", 13, "confirm", "#30ffffff");
  const goText = owned ? (refundCount > 1 ? `戻す（先の ${refundCount - 1} 個も）` : "戻す")
    : toTake != null ? `取る（${toTake.length} 点）` : "取る";
  text(ix, by + 21, iw, goText, 24, state === "take" ? "#2a1406" : state === "refund" ? "#e58b80" : "#5e5a54", 14, "center", "v3b");
  const resetOk = taken.size > 0 && A.silver >= PRICE * taken.size;
  const bw = (iw - 20) / 3;
  [["reset", "全部戻す", resetOk], ["centre", "起点へ戻る", true], ["close", "閉じる", true]].forEach(([act, word, on], k) => {
    const x = ix + k * (bw + 10);
    framed(x, 946, bw, 48, 10, "#1b1c20", 12);
    if (on) rect("b-" + act, x, 946, bw, 48, "#00000000", 14, act, "#30ffffff");
    text(x, 960, bw, word, 16, on ? "#ece6dc" : "#5e5a54", 15, "center", "v3m");
  });
}

function masteryChoices(node, top) {
  const ix = RIGHT_X + 26, w = RIGHT_W - 52;
  const chosen = A.masteries.has(node.id) ? A.masteries.get(node.id) : null;
  let y = top;
  node.choiceLabels.forEach((key, k) => {
    const open = node.id === selected && masteryChoosable(node.id, k);
    const dup = chosen == null && choiceTaken(A.masteries, node.id, k);
    const h = dup ? 62 : 46;
    if (chosen === k) framed(ix, y, w, h, 10, "#2b1e17", 12, "#f07a2a"); else framed(ix, y, w, h, 10, "#17181b", 12);
    if (open) rect("ch" + k, ix, y, w, h, "#00000000", 14, "choice:" + k, "#30ffffff");
    label(key, ix + 12, y + 9, 15, open || chosen === k ? null : "#6b6b6b");
    if (dup) label("dup", ix + 12, y + 37, 15);
    y += h + 8;
  });
}

// ---------------------------------------------------------------- drawing
const atlasImg = [], quadImg = [];
const PIXEL = /^(lit_|key_|brand_slot|zoom_)/;
const tintCache = new Map();
function css(c) {
  if (c.length === 9) { const a = parseInt(c.slice(1, 3), 16) / 255; return `rgba(${parseInt(c.slice(3, 5), 16)},${parseInt(c.slice(5, 7), 16)},${parseInt(c.slice(7, 9), 16)},${a})`; }
  return c;
}
function tinted(name, tint) {
  const key = name + tint;
  let c = tintCache.get(key);
  if (c) return c;
  const [a, sx, sy, w, h] = D.spr[name];
  c = document.createElement("canvas"); c.width = w; c.height = h;
  const g = c.getContext("2d");
  g.drawImage(atlasImg[a], sx, sy, w, h, 0, 0, w, h);
  g.globalCompositeOperation = "multiply"; g.fillStyle = tint; g.fillRect(0, 0, w, h);
  g.globalCompositeOperation = "destination-in"; g.drawImage(atlasImg[a], sx, sy, w, h, 0, 0, w, h);
  tintCache.set(key, c);
  return c;
}
function drawSprite(op) {
  const [a, sx, sy, w, h, ox, oy, fw, fh] = D.spr[op.n];
  const kx = op.w / fw, ky = op.h / fh;
  ctx.imageSmoothingEnabled = !PIXEL.test(op.n);
  const dx = op.x + ox * kx, dy = op.y + oy * ky, dw = w * kx, dh = h * ky;
  if (op.tint) ctx.drawImage(tinted(op.n, op.tint), 0, 0, w, h, dx, dy, dw, dh);
  else ctx.drawImage(atlasImg[a], sx, sy, w, h, dx, dy, dw, dh);
}
let hoverHit = null;
function draw() {
  scene();
  // Hover colour on the topmost button under the pointer, as UiRenderer.background does.
  hoverHit = topHit(pointerX, pointerY);
  if (hoverHit && hoverHit.hover) hoverHit.op.c = hoverHit.hover;
  ops.sort((p, q) => p.z - q.z || p.i - q.i);
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, 1920, 1080);
  const z = zoom();
  for (const op of ops) {
    if (op.t === "f") { ctx.fillStyle = css(op.c); ctx.fillRect(op.x, op.y, op.w, op.h); }
    else if (op.t === "s") drawSprite(op);
    else if (op.t === "T") {
      ctx.imageSmoothingEnabled = true;
      D.quads.forEach(([, qx, qy, qw, qh], k) => ctx.drawImage(quadImg[k], panX + qx * z, panY + qy * z, qw * z, qh * z));
    } else if (op.t === "t") {
      ctx.font = fontOf(op.family, op.size); ctx.fillStyle = op.c; ctx.textBaseline = "alphabetic";
      ctx.textAlign = op.align;
      const x = op.align === "center" ? op.x + op.w / 2 : op.align === "right" ? op.x + op.w : op.x;
      ctx.fillText(op.v, x, op.y + op.size * 1.0);
    }
  }
  cv.style.cursor = hoverHit && hoverHit.act !== "tree" ? "pointer" : nodeAt(pointerX, pointerY) != null ? "pointer" : "";
}
function topHit(x, y) {
  let best = null;
  for (const h of hits) if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h && (!best || h.depth >= best.depth)) best = h;
  return best;
}
let queued = false;
function redraw() { if (!queued && loaded) { queued = true; requestAnimationFrame(() => { queued = false; draw(); }); } }

// ---------------------------------------------------------------- input
function design(e) { const r = cv.getBoundingClientRect(); return [(e.clientX - r.left) / r.width * 1920, (e.clientY - r.top) / r.height * 1080]; }
let down = null;
cv.addEventListener("pointerdown", e => {
  const [x, y] = design(e);
  pointerX = x; pointerY = y;
  const h = topHit(x, y);
  down = { x, y, panX, panY, drag: false, canPan: !overChrome(x, y) && (!h || h.act === "tree") };
  try { cv.setPointerCapture(e.pointerId); } catch (_) {}
  redraw();
});
cv.addEventListener("pointermove", e => {
  const [x, y] = design(e);
  if (down) {
    if (!down.drag && down.canPan && Math.hypot(x - down.x, y - down.y) > 10) { down.drag = true; cv.classList.add("grab"); }
    if (down.drag) {
      panX = clamp(down.panX + x - down.x, minX(), MARGIN);
      panY = clamp(down.panY + y - down.y, minY(), MARGIN);
    }
  }
  if (e.pointerType === "mouse" || !down || !down.drag) { pointerX = x; pointerY = y; }
  redraw();
});
cv.addEventListener("pointerup", e => {
  const [x, y] = design(e);
  if (down && !down.drag) {
    pointerX = x; pointerY = y;
    scene();
    const h = topHit(x, y);
    if (h) action(h.act);
  }
  down = null; cv.classList.remove("grab");
  if (e.pointerType !== "mouse") { pointerX = -1; pointerY = -1; }
  redraw();
});
cv.addEventListener("pointercancel", () => { down = null; cv.classList.remove("grab"); pointerX = pointerY = -1; redraw(); });
cv.addEventListener("pointerleave", e => { if (e.pointerType === "mouse" && !down) { pointerX = pointerY = -1; redraw(); } });
let wheelAcc = 0, wheelAt = 0;
cv.addEventListener("wheel", e => {
  e.preventDefault();
  const now = performance.now();
  if (now - wheelAt > 250) wheelAcc = 0;
  wheelAt = now; wheelAcc += e.deltaY;
  if (Math.abs(wheelAcc) >= 60) { zoomBy(wheelAcc < 0 ? +1 : -1); wheelAcc = 0; wheelAt = now + 150; redraw(); }
}, { passive: false });

// ---------------------------------------------------------------- page controls
const jobSel = document.getElementById("job");
for (const [code, name] of D.classes) { const o = document.createElement("option"); o.value = code; o.textContent = name; jobSel.appendChild(o); }
jobSel.value = A.job;
function restart() {
  A.job = jobSel.value; A.taken = new Set(); A.masteries = new Map(); A.silver = SILVER;
  selected = null; failed = false; zoomIndex = ZOOMS.length - 1; centreOnStart(); note(""); redraw();
}
jobSel.addEventListener("change", restart);
document.getElementById("restart").addEventListener("click", restart);
let noteTimer = 0;
function note(msg) {
  const el = document.getElementById("note"); el.textContent = msg;
  clearTimeout(noteTimer); if (msg) noteTimer = setTimeout(() => { el.textContent = ""; }, 2500);
}

// ---------------------------------------------------------------- loading
let loaded = false;
const loadImg = src => new Promise((ok, bad) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => bad(src); i.src = src; });
const fontsReady = (document.fonts ? Promise.all([
  document.fonts.load('20px "DotGothic16"'), document.fonts.load('500 16px "M PLUS 2"'), document.fonts.load('700 24px "M PLUS 2"'),
]).catch(() => null) : Promise.resolve());
centreOnStart();
Promise.all([
  Promise.all(D.atlases.map(loadImg)).then(a => atlasImg.push(...a)),
  Promise.all(D.quads.map(q => loadImg(q[0]))).then(a => quadImg.push(...a)),
]).then(() => Promise.race([fontsReady, new Promise(r => setTimeout(r, 2500))])).then(() => {
  loaded = true; document.getElementById("loading").remove(); draw();
  if (document.fonts) document.fonts.ready.then(redraw);
}).catch(src => { document.getElementById("loading").textContent = "画像を読み込めませんでした: " + src; });
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
