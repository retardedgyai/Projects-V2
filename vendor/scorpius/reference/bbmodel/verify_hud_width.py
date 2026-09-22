#!/usr/bin/env python3
"""配布中のフォント定義から HUD の幅を**実際に足し合わせて**検算する。

Scala 側は `src/main/resources/glyph_widths.json`（生成物）を信じて
「置いた要素の幅ぶんカーソルを戻す」ので、**幅表とフォント定義が食い違うと
HUD 全体が横に滑る**。ここはその一致を、フォント JSON と PNG から独立に確かめる。

  [0]  フォント定義の構造（ascent <= height / セル割り切れ / chars 行長）
  [1]  幅表 == フォント定義から計算した advance
  [2]  space フォント（shift が px そのもの / marker と打ち消しが 0 になる）
  [3]  グリッド一致（同じシートを参照するフォントの chars が揃っている）
  [4]  シェーダーの #define が生成器の定数と一致している

使い方: python3 bbmodel/verify_hud_width.py
"""

import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack/assets"
# text.vsh の正本。buildBossPack がここから resourcepack/ へ写す
TEMPLATE = ROOT / "resourcepack_template/assets"
WIDTHS = ROOT / "src/main/resources/glyph_widths.json"

# text/SpaceFont.scala・text.vsh・gen_space_font.py と一致させること
SHIFT_RANGE = 4096
SHIFT_BASE = 0xD0000
MARKER_BASE = 1 << 18
STRIDE = 2048
LINES = 3
ANCHORS = 8
ROWS = 256
ROW_BIAS = 128
MARKER_CP = 0xD4000
UNMARKER_CP = 0xD6000

failures = []


def fail(tag, msg):
    failures.append(f"[{tag}] {msg}")


def resolve(file_ref):
    ns, path = file_ref.split(":", 1) if ":" in file_ref else ("minecraft", file_ref)
    return PACK / ns / "textures" / path


def provider_advances(pr):
    """bitmap provider の各文字の advance を、テクスチャから実測して出す。"""
    path = resolve(pr["file"])
    if not path.exists():
        fail("0", f"テクスチャが無い: {pr['file']}")
        return {}
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    rows = pr["chars"]
    cols = max(len(r) for r in rows)
    if len({len(r) for r in rows}) != 1:
        fail("0", f"{pr['file']}: chars の行の長さが揃っていない")
    if w % cols or h % len(rows):
        fail("0", f"{pr['file']}: テクスチャ {w}x{h} が chars {cols}x{len(rows)} で割り切れない")
        return {}
    if pr["ascent"] > pr["height"]:
        fail("0", f"{pr['file']}: ascent {pr['ascent']} > height {pr['height']}"
                  "（font 定義ごとロードに失敗して全グリフが豆腐になる）")
    cw, ch = w // cols, h // len(rows)
    scale = pr["height"] / ch
    alpha = im.getchannel("A").tobytes()
    out = {}
    for r, row in enumerate(rows):
        for c, char in enumerate(row):
            if not char.strip() or ord(char) == 0:
                continue
            ox, oy = c * cw, r * ch
            right = -1
            for y in range(oy, oy + ch):
                base = y * w
                for x in range(cw - 1, right, -1):
                    if alpha[base + ox + x]:
                        right = x
                        break
            out[ord(char)] = int(0.5 + (right + 1) * scale) + 1
    return out


def check_font_widths():
    """[1] 幅表が**すべての自前フォント**の定義と一致するか。

    hud.json だけを見ていたせいで、スロット枠（scorpius:hud_slot）の advance が
    幅表に無いことを見逃した。`GlyphWidths.advance` が 0 を返し、枠より後ろの要素が
    23px 右へずれ、name の合計幅が 0 でなくなってチャンネル全体が 11px 左へ滑った。
    **HUD が描きうるフォントは全部ここを通す。**
    """
    widths = {int(k, 16): v for k, v in json.loads(WIDTHS.read_text()).items()}
    font_dir = PACK / "scorpius/font"
    total, bad, missing = 0, [], []
    for path in sorted(font_dir.glob("*.json")):
        seen, spaces = {}, {}
        for pr in json.loads(path.read_text())["providers"]:
            if pr.get("type") == "space":
                spaces.update({ord(k): v for k, v in pr["advances"].items()})
            elif pr.get("type") == "bitmap":
                seen.update(provider_advances(pr))
        seen.update(spaces)   # space provider は後ろにあるほど優先
        total += len(seen)
        for cp, adv in seen.items():
            if cp not in widths:
                missing.append((path.name, cp))
            elif widths[cp] != adv:
                bad.append((path.name, cp, widths[cp], adv))
    if missing:
        fail("1", f"フォントにあるのに幅表に無い: {len(missing)} 字 "
                  + " ".join(f"{n}:U+{c:04X}" for n, c in missing[:6]))
    if bad:
        fail("1", f"幅表とフォント定義が食い違う: {len(bad)} 字 "
                  + " ".join(f"{n}:U+{c:04X}({w}!={a})" for n, c, w, a in bad[:6]))
    print(f"  [1] 幅表 {len(widths)} 字 / 自前フォント延べ {total} 字 — "
          f"欠け {len(missing)} / 不一致 {len(bad)}")


def check_space_font():
    """[2] shift と marker の算術。"""
    font = json.loads((PACK / "space/font/default.json").read_text())
    adv = {}
    for pr in font["providers"]:
        adv.update({ord(k): v for k, v in pr["advances"].items()})

    for px in (-SHIFT_RANGE, -101, -1, 1, 101, SHIFT_RANGE):
        cp = SHIFT_BASE + px + SHIFT_RANGE
        if adv.get(cp) != px:
            fail("2", f"shift({px}) が {adv.get(cp)}")

    half = STRIDE // 2
    top = MARKER_BASE + (LINES * ANCHORS * ROWS - 1) * STRIDE
    if top >= (1 << 24):
        fail("2", f"marker の最大値 {top} が float の整数精度（2^24）を超える")
    for i in (0, 1, LINES * ANCHORS * ROWS - 1):
        m, u = adv.get(MARKER_CP + i), adv.get(UNMARKER_CP + i)
        if m is None or u is None or m + u != 0:
            fail("2", f"marker {i} が打ち消せていない: {m} + {u}")
            continue
        if m != MARKER_BASE + i * STRIDE:
            fail("2", f"marker {i} の値が {m}（期待 {MARKER_BASE + i * STRIDE}）")
        # シェーダーと同じ式で id が戻るか（要素内の文字送り ±STRIDE/2 を含む）
        for drift in (-half + 1, 0, half - 1):
            got = (m + drift - MARKER_BASE + half) // STRIDE
            if got != i:
                fail("2", f"marker {i} が drift {drift} で id {got} に化ける")
    print(f"  [2] space フォント {len(adv)} 文字 — shift / marker ok")


def check_shader_constants():
    """[4] シェーダーの #define が生成器と一致しているか。

    ここが食い違うと、幅の検算は全部通るのに**実機だけ位置がずれる**。
    実際に MARKER_BASE と STRIDE を片側だけ変えて踏んだ。
    """
    import re
    src = (TEMPLATE / "minecraft/shaders/core/text.vsh").read_text(encoding="utf-8")
    got = {}
    for m in re.finditer(r"#define\s+(\w+)\s+([0-9.]+)", src):
        got[m.group(1)] = float(m.group(2))
    want = {"MARKER_BASE": MARKER_BASE, "STRIDE": STRIDE, "ANCHORS": ANCHORS,
            "ROWS": ROWS, "ROW_BIAS": ROW_BIAS}
    for k, v in want.items():
        if k not in got:
            fail("4", f"text.vsh に #define {k} が無い")
        elif got[k] != v:
            fail("4", f"text.vsh の {k} が {got[k]:.0f}（生成器は {v}）")
    print(f"  [4] シェーダー定数 {len(want)} 件 — 一致")


def check_grid():
    """[3] 同じシートを参照するフォントの chars グリッドが揃っているか。

    グリッドは**コピーして持つ**ので、シートを焼き直して片方だけ更新すると
    文字が丸ごと別のグリフに化ける。
    """
    base = json.loads((PACK / "scorpius/textures/font/vanilla/providers.json").read_text())
    ref = {p["file"]: p["chars"] for p in base if p["type"] == "bitmap"}
    for name in ("hud", "sidebar", "hud_slot_label", "lore"):
        path = PACK / "scorpius/font" / f"{name}.json"
        if not path.exists():
            continue
        for pr in json.loads(path.read_text())["providers"]:
            if pr.get("type") != "bitmap":
                continue
            want = ref.get(pr["file"])
            if want is not None and pr["chars"] != want:
                fail("3", f"{name}.json の {pr['file']} のグリッドがシートとずれている"
                          "（gen_vanilla_font.py から下流を焼き直すこと）")
    print("  [3] グリッド一致 ok")


def main():
    print("HUD 幅の検算")
    check_font_widths()
    check_space_font()
    check_shader_constants()
    check_grid()
    if failures:
        print("\nNG:")
        for f in failures:
            print(" ", f)
        sys.exit(1)
    print("\nすべて ok")


if __name__ == "__main__":
    main()
