#!/usr/bin/env python3
"""Collect player-visible strings and flag English leftovers, name variants, typos and overflow.

Usage: python3 scripts/wording_audit.py [--out docs/development/wording-audit.md] [--tsv out.tsv]

Sources
  * Kotlin string literals under server-minestom/src/main/kotlin (comments skipped,
    ${...} templates shown as {…}). Debug-only sites (require/check/error/throw/println/
    logger/System.err/assert) are excluded.
  * Text fields in server-minestom/src/main/resources: passive-tree.tsv names,
    JSON string values that contain Japanese (dungeon / lobby rooms). polish05/forge_initial.json
    (a DOM capture of the web mock) and player-motions/*.json (animation notes) are not shown
    to players and are skipped.

A literal counts as player-visible when it has Japanese, or when it is ASCII with letters
and sits on a line that sends it to a player (chat, title, action bar, boss bar, item
name/lore, menu label/text, dialog).

Width ("全角幅") uses the vanilla advance table copied from CoreUiComponents.width
(full-width glyph = 9 px, so 全角 = px / 9). Overflow is judged only where the code states
a limit; see LIMITS below. Everything else is listed with its measured width only.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "server-minestom/src/main/kotlin"
RES = ROOT / "server-minestom/src/main/resources"

JP = re.compile(r"[぀-ヿ㐀-鿿！-｠]")
LATIN_WORD = re.compile(r"[A-Za-z]{2,}")
DEBUG_LINE = re.compile(r"\b(require|requireNotNull|check|checkNotNull|error|assert)\s*\(|throw\s|println|System\.(err|out)|logger\.|LOGGER\.|log\.(info|warn|debug|error)|IllegalStateException|IllegalArgumentException")
SINK = re.compile(
    r"Component\.text|\.text\(|sendMessage|sendActionBar|showTitle|Title\.title|BossBar\.bossBar|bossBar|"
    r"customName|withCustomName|displayName|itemName|withLore|lore|label|card\(|button|Dialog|caption|subtitle|"
    r"title\s*=|name\s*=|hint|setTitle|sidebar|Sign|sign\(")


def adv(c: str, bold: bool = False) -> int:
    """CoreUiComponents.width advance (vanilla default font)."""
    code = ord(c)
    if c == " ":
        a = 4
    elif c in "i!.,:;|'":
        a = 2
    elif c == "l":
        a = 3
    elif c in "I[]t":
        a = 4
    elif c in "fk<>(){}":
        a = 5
    elif c in "@~":
        a = 7
    else:
        a = 6 if 0x21 <= code <= 0x7E else 9
    return a + (1 if bold and c != " " else 0)


def width(s: str) -> int:
    return sum(adv(c) for c in s)


# ---------------------------------------------------------------- Kotlin lexer
def kotlin_literals(text: str):
    """Yield (line, literal_text_with_placeholders) for every string literal, skipping comments."""
    i, n, line = 0, len(text), 1
    out = []

    def read_string(i: int, line: int, raw: bool):
        """i points after the opening quote(s). Returns (end_index, line, text)."""
        buf = []
        while i < n:
            if raw and text.startswith('"""', i):
                return i + 3, line, "".join(buf)
            c = text[i]
            if not raw and c == '"':
                return i + 1, line, "".join(buf)
            if c == "\n":
                line += 1
                buf.append("\n")
                i += 1
                continue
            if not raw and c == "\\":
                nxt = text[i + 1] if i + 1 < n else ""
                buf.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'"}.get(nxt, "\\" + nxt))
                if nxt == "u":
                    buf[-1] = chr(int(text[i + 2:i + 6], 16)); i += 6; continue
                i += 2
                continue
            if c == "$" and i + 1 < n and text[i + 1] == "{":
                # template expression: skip balanced braces, collecting nested literals
                depth, j = 1, i + 2
                start_line = line
                while j < n and depth:
                    ch = text[j]
                    if ch == "{": depth += 1; j += 1
                    elif ch == "}": depth -= 1; j += 1
                    elif ch == '"':
                        is_raw = text.startswith('"""', j)
                        j, line, inner = read_string(j + (3 if is_raw else 1), line, is_raw)
                        out.append((start_line, inner))
                    elif ch == "\n": line += 1; j += 1
                    else: j += 1
                buf.append("{…}")
                i = j
                continue
            if c == "$" and i + 1 < n and (text[i + 1].isalpha() or text[i + 1] == "_"):
                j = i + 1
                while j < n and (text[j].isalnum() or text[j] == "_"): j += 1
                buf.append("{…}")
                i = j
                continue
            buf.append(c)
            i += 1
        return i, line, "".join(buf)

    while i < n:
        c = text[i]
        if c == "\n":
            line += 1; i += 1
        elif text.startswith("//", i):
            j = text.find("\n", i); i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2); j = n if j < 0 else j + 2
            line += text.count("\n", i, j); i = j
        elif c == "'":
            # char literal
            j = i + 1
            if j < n and text[j] == "\\": j += 2
            else: j += 1
            while j < n and text[j] != "'" and j - i < 10: j += 1
            i = j + 1
        elif c == '"':
            raw = text.startswith('"""', i)
            start = line
            i, line, s = read_string(i + (3 if raw else 1), line, raw)
            out.append((start, s))
        else:
            i += 1
    return out


def collect():
    rows = []  # dict(file, line, text, kind, context)
    for path in sorted(SRC.rglob("*.kt")):
        rel = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        lines = text.split("\n")
        for ln, lit in kotlin_literals(text):
            src_line = lines[ln - 1] if ln - 1 < len(lines) else ""
            ctx = src_line.strip()
            prev = lines[ln - 2] if ln >= 2 else ""
            if DEBUG_LINE.search(src_line) or re.search(r"\b(require|check)\(.*\{\s*$", prev):
                continue
            stripped = lit.replace("{…}", "").strip()
            if not stripped:
                continue
            if JP.search(lit):
                rows.append(dict(file=rel, line=ln, text=lit, kind="jp", context=ctx))
            elif LATIN_WORD.search(stripped) and SINK.search(src_line):
                rows.append(dict(file=rel, line=ln, text=lit, kind="ascii", context=ctx))

    # resources
    tsv = RES / "passive-tree.tsv"
    for ln, row in enumerate(tsv.read_text(encoding="utf-8").split("\n"), 1):
        if not row or row.startswith("#"): continue
        f = row.split("\t")
        if len(f) > 4 and f[4] != "-":
            rows.append(dict(file=tsv.relative_to(ROOT).as_posix(), line=ln, text=f[4], kind="jp" if JP.search(f[4]) else "ascii", context="passive node name"))
        if len(f) > 9 and f[9] != "-" and JP.search(f[9]):
            for part in f[9].split("|"):
                rows.append(dict(file=tsv.relative_to(ROOT).as_posix(), line=ln, text=part, kind="jp", context="passive mastery option"))
    for jf in sorted(list((RES / "dungeons").rglob("*.json")) + list((RES / "lobby").rglob("*.json"))):
        raw = jf.read_text(encoding="utf-8")
        for ln, l in enumerate(raw.split("\n"), 1):
            for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', l):
                s = json.loads('"' + m.group(1) + '"')
                if JP.search(s) and "generated by" not in s:
                    rows.append(dict(file=jf.relative_to(ROOT).as_posix(), line=ln, text=s, kind="jp", context=l.strip()[:80]))
    return rows


# ---------------------------------------------------------------- checks
ENGLISH_ALLOW = set("""HP MP XP CD DPS PvP PvE Lv OK UI NPC ID LV DPS Hit Shift F Q E R Tab ESC Esc
""".split())


def english_leftovers(rows):
    """Visible strings carrying latin words (outside abbreviations like HP/MP/Lv)."""
    hits = []
    for r in rows:
        words = [w for w in LATIN_WORD.findall(r["text"].replace("{…}", "")) if w not in ENGLISH_ALLOW]
        if not words: continue
        # ASCII-only identifier-ish strings (snake/camel/keys) are not prose
        t = r["text"].replace("{…}", "").strip()
        if r["kind"] == "ascii" and re.fullmatch(r"[a-z0-9_./:\-#]+", t): continue
        if r["kind"] == "ascii" and re.fullmatch(r"[A-Z0-9_]+", t): continue
        if r["kind"] == "ascii" and re.fullmatch(r"#[0-9A-Fa-f]{6}|[a-z]+[A-Z]\w*", t): continue
        hits.append((r, sorted(set(words))))
    return hits


# known typo patterns: doubled kana/kanji particles, mixed-width punctuation
TYPO_PATTERNS = [
    (re.compile(r"([をにがはでとへのも])\1(?!\1)"), "助詞の重複"),
    (re.compile(r"([。、])\1"), "句読点の重複"),
    (re.compile(r"(?<![A-Za-z0-9])([一-鿿]{2,})\1(?![一-鿿])"), "語の重複"),
    (re.compile(r"[ァ-ヴ][ぁ-ゔ]ー|[ぁ-ゔ]ー[ぁ-ゔ]"), "ひらがなに長音"),
    (re.compile(r"ヶ月|ヵ"), None),
    (re.compile(r"\s{2,}[。、]|[、。]\s+[、。]"), "空白と句読点"),
]


def typo_candidates(rows):
    hits = []
    for r in rows:
        if r["kind"] != "jp": continue
        t = r["text"]
        for pat, label in TYPO_PATTERNS:
            if label is None: continue
            for m in pat.finditer(t):
                hits.append((r, label, m.group(0)))
    return hits


# ---------------------------------------------------------------- overflow with a stated limit
MENU_METRICS_CANDIDATES = [
    RES / "core-ui-pack/assets/projects/menu",  # generated by gen_resource_pack.py (not committed)
    ROOT.parent.parent.parent / "server-minestom/src/main/resources/core-ui-pack/assets/projects/menu",  # main checkout
]


def load_menu_metrics():
    for d in MENU_METRICS_CANDIDATES:
        body, emph = d / "glyphs.tsv", d / "glyphs-emphasis.tsv"
        if body.exists() and emph.exists():
            def parse(p):
                m = {}
                for l in p.read_text(encoding="utf-8").split("\n"):
                    if not l.strip() or l.startswith("#"): continue
                    cp, _, a = l.split("\t")
                    m[int(cp, 16)] = int(a)
                return m
            return {"BODY": parse(body), "EMPHASIS": parse(emph)}, d
    return None, None


STR = r'"((?:[^"\\$]|\\.)*)"'  # plain literal without templates


def overflow(rows_by_file):
    """Literal strings whose measured width exceeds a limit written in code. Each hit is visibly cut."""
    metrics, where = load_menu_metrics()
    hits, measured = [], []

    def mw(s, style):
        if metrics is None: return None
        m = metrics[style]
        return sum(m.get(ord(c), m.get(ord("□"), 9)) for c in s)

    rules = [
        # (regex, limit fn(match)->(limit, style, sink))
        (re.compile(r"\btile\(v,\s*\d+,\s*(\d+),\s*" + STR + r"(.*)"),
         lambda m: (int(m.group(1)) * 18 - 2 - (18 if "icon = true" in m.group(3) else 0), "EMPHASIS", "メニューのボタン（span×18−2）")),
        (re.compile(r"\bcard\(v,\s*\d+,\s*(\d+),\s*(\d+),\s*" + STR),
         lambda m: (int(m.group(1)) * 18 - 2 - (18 if m.group(2) == "1" else 0), "EMPHASIS", "メニューのカード（columns×18−2）")),
        (re.compile(r"\bview\(player,\s*" + STR),
         lambda m: (160, "EMPHASIS", "メニューの題（160）")),
        (re.compile(r"CoreMenuCanvas\(" + STR),
         lambda m: (160, "EMPHASIS", "メニューの題（160）")),
        (re.compile(r"\bLine\(" + STR + r"(.*)"),
         lambda m: (88 - (18 if "art" in m.group(2) and "CoreMenuArt" in m.group(2) else 0),
                    "EMPHASIS" if "EMPHASIS" in m.group(2) else "BODY", "メニュー左右の行（PANEL_WIDTH 88）")),
        (re.compile(r"\.focus\([^,]+,\s*" + STR),
         lambda m: (106, "EMPHASIS", "装備の題（106）")),
    ]
    for rel, text in rows_by_file.items():
        for ln, line in enumerate(text.split("\n"), 1):
            for rx, fn in rules:
                for m in rx.finditer(line):
                    lit_idx = 2 if rx.pattern.startswith(r"\btile") else (3 if rx.pattern.startswith(r"\bcard") else 1)
                    s = m.group(lit_idx)
                    if not s: continue
                    limit, style, sink = fn(m)
                    w = mw(s, style)
                    if w is None: continue
                    rec = dict(file=rel, line=ln, text=s, sink=sink, px=w, limit=limit, zen=round(w / 9, 1))
                    measured.append(rec)
                    if w > limit: hits.append(rec)
            # HUD notice on the action bar: CoreHudLayout.MAX_CAPTION_WIDTH = 300, vanilla widths
            for m in re.finditer(r"\bnotice\(\s*\"((?:[^\"\\]|\\.)*)\"", line):
                s = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_]\w*", "", m.group(1))
                w = width(s)
                rec = dict(file=rel, line=ln, text=m.group(1), sink="HUDのお知らせ（300、可変部は除いて測定）", px=w, limit=300, zen=round(w / 9, 1))
                measured.append(rec)
                if w > 300: hits.append(rec)
    return hits, measured, where


def unbounded(rows):
    """Sinks without a limit in code: measure only (vanilla px, 全角 = px/9)."""
    sinks = {
        "アクションバー": re.compile(r"sendActionBar"),
        "タイトル": re.compile(r"showTitle|Title\.title"),
        "ボスバー": re.compile(r"BossBar\.bossBar|bossBar|\.name\("),
        "チャット": re.compile(r"sendMessage"),
    }
    out = collections.defaultdict(list)
    for r in rows:
        for name, rx in sinks.items():
            if rx.search(r["context"]):
                out[name].append(r)
    return out


def term_variants(rows):
    """Short names / 「quoted」 terms that collapse to the same key after normalising notation."""
    terms = collections.defaultdict(lambda: collections.defaultdict(list))
    def key(t):
        t = unicodedata.normalize("NFKC", t)
        t = t.replace("ー", "").replace("・", "").replace(" ", "").replace("の", "").replace("ノ", "")
        t = t.replace("熔", "溶").replace("燿", "耀")
        t = re.sub(r"[ぁ-ゖ]+$", "", t)  # trailing okurigana
        return t
    for r in rows:
        if r["kind"] != "jp": continue
        cands = re.findall(r"「([^」]{2,12})」", r["text"])
        t = r["text"].strip()
        if 2 <= len(t) <= 10 and "{…}" not in t and not re.search(r"[。、！？：:]", t):
            cands.append(t)
        for c in cands:
            k = key(c)
            if len(k) >= 2:
                terms[k][c].append(f"{r['file'].split('/')[-1]}:{r['line']}")
    return {k: v for k, v in terms.items() if len(v) > 1}


def md_cell(t: str) -> str:
    return t.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "⏎")


def short(path: str) -> str:
    return path.replace("server-minestom/src/main/kotlin/dev/projects/server/", "").replace("server-minestom/src/main/resources/", "res/")


def write_markdown(path: Path, rows, eng, typos, variants, over_hits, measured, metrics_dir, sinks):
    o = []
    o.append("# 文言の一覧（自動生成）\n")
    o.append("`python3 scripts/wording_audit.py --md docs/development/wording-audit.md` で作り直せます。"
             "幅は px。全角幅はバニラ文字なら px÷9、メニュー文字（core_menu）でも漢字・かなは 9 px なので同じく px÷9 を目安に書いています。\n")
    o.append(f"- 集めた文字列：{len(rows)}（日本語を含む {sum(r['kind']=='jp' for r in rows)}、英字だけ {sum(r['kind']=='ascii' for r in rows)}）")
    o.append(f"- 英語が残っているもの：{len(eng)}")
    o.append(f"- 書き方だけが違う名前の組（自動）：{len(variants)}")
    o.append(f"- 誤字の候補（自動の型）：{len(typos)}")
    o.append(f"- はみ出し（コードに上限がある所）：{len(over_hits)}／測った {len(measured)}")
    o.append(f"- メニュー文字の幅表：`{metrics_dir}`\n")

    o.append("## はみ出し（上限がコードにある所）\n")
    o.append("| 場所 | 表示先 | 文字列 | 幅 | 上限 |\n|---|---|---|---|---|")
    for h in over_hits:
        o.append(f"| {short(h['file'])}:{h['line']} | {h['sink']} | {md_cell(h['text'])} | {h['px']} | {h['limit']} |")
    if not over_hits: o.append("| （なし） | | | | |")
    o.append("\n### 上限に近いもの（上限の 90% 以上）\n")
    o.append("| 場所 | 表示先 | 文字列 | 幅 | 上限 |\n|---|---|---|---|---|")
    for h in sorted((m for m in measured if m['px'] * 10 >= m['limit'] * 9 and m['px'] <= m['limit']), key=lambda m: -m['px'] / m['limit']):
        o.append(f"| {short(h['file'])}:{h['line']} | {h['sink']} | {md_cell(h['text'])} | {h['px']} | {h['limit']} |")

    o.append("\n## 上限がコードにない表示先（測っただけ）\n")
    o.append("バニラの文字で測った幅（px、全角＝px÷9）。長い順に 15 件ずつ。\n")
    for name, rs in sinks.items():
        o.append(f"### {name}（{len(rs)} 件）\n")
        o.append("| 場所 | 文字列 | 幅 | 全角 |\n|---|---|---|---|")
        for r in sorted(rs, key=lambda r: -r['px'])[:15]:
            o.append(f"| {short(r['file'])}:{r['line']} | {md_cell(r['text'])} | {r['px']} | {r['zen']} |")
        o.append("")

    o.append("## 英語が残っているもの\n")
    c = collections.Counter(w for _, ws in eng for w in ws)
    o.append("語ごとの数：" + "、".join(f"{w} {n}" for w, n in c.most_common()) + "\n")
    o.append("| 場所 | 英語 | 文字列 |\n|---|---|---|")
    for r, ws in eng:
        o.append(f"| {short(r['file'])}:{r['line']} | {' '.join(ws)} | {md_cell(r['text'][:90])} |")

    o.append("\n## 書き方だけが違う名前（自動で拾った組）\n")
    o.append("「の」・長音・中黒・送り仮名・熔/溶 を無視すると同じになる語。動詞の形の違いだけのもの（帰還／帰還する など）も混ざっています。\n")
    o.append("| 揃えた形 | 出てくる書き方（回数） | 場所の例 |\n|---|---|---|")
    for k, d in sorted(variants.items()):
        o.append(f"| {md_cell(k)} | {' / '.join(f'{md_cell(s)}（{len(l)}）' for s, l in d.items())} | {' '.join(l[0] for l in d.values())} |")

    o.append("\n## 誤字の候補（自動の型）\n")
    o.append("| 場所 | 型 | 該当 | 文字列 |\n|---|---|---|---|")
    for r, label, m in typos:
        o.append(f"| {short(r['file'])}:{r['line']} | {label} | {md_cell(m)} | {md_cell(r['text'][:80])} |")

    o.append("\n## 全部の文字列\n")
    o.append("| 場所 | 幅 | 文字列 |\n|---|---|---|")
    for r in rows:
        o.append(f"| {short(r['file'])}:{r['line']} | {r['px']} | {md_cell(r['text'])} |")
    path.write_text("\n".join(o) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv")
    ap.add_argument("--json")
    ap.add_argument("--md")
    a = ap.parse_args()
    rows = collect()
    for r in rows:
        r["px"] = max(width(part) for part in r["text"].split("\n"))
        r["zen"] = round(r["px"] / 9, 1)
    if a.tsv:
        with open(a.tsv, "w", encoding="utf-8") as f:
            f.write("file\tline\tkind\tpx\tzen\ttext\n")
            for r in rows:
                f.write(f"{r['file']}\t{r['line']}\t{r['kind']}\t{r['px']}\t{r['zen']}\t{r['text']!r}\n")
    eng = english_leftovers(rows)
    typos = typo_candidates(rows)
    variants = term_variants(rows)
    files = {p.relative_to(ROOT).as_posix(): p.read_text(encoding="utf-8") for p in sorted(SRC.rglob("*.kt"))}
    over_hits, measured, metrics_dir = overflow(files)
    sinks = unbounded(rows)
    if a.json:
        Path(a.json).write_text(json.dumps(dict(rows=rows, english=[(r, w) for r, w in eng], typos=typos, overflow=over_hits),
                                           ensure_ascii=False, indent=1), encoding="utf-8")
    if a.md:
        md_dir = metrics_dir.relative_to(ROOT.parent.parent.parent) if metrics_dir and ROOT.parent.parent.parent in metrics_dir.parents else metrics_dir
        write_markdown(Path(a.md), rows, eng, typos, variants, over_hits, measured, md_dir, sinks)
    print(f"strings={len(rows)} jp={sum(r['kind']=='jp' for r in rows)} ascii={sum(r['kind']=='ascii' for r in rows)} "
          f"english={len(eng)} variants={len(variants)} typo_candidates={len(typos)} overflow={len(over_hits)}/{len(measured)}")


if __name__ == "__main__":
    main()
