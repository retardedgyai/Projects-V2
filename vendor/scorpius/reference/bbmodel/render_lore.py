#!/usr/bin/env python3
"""`loreDump` が吐いた lore を、**配布中のフォント定義で 1 グリフずつ描き直す**。

=== なぜ要るか ===

lore の見え方は「Scala が組んだ Component」×「パックのフォント定義」で決まるので、
片方だけ見ても分からない。手で組み直したモックは実装の思い込みをそのまま再現するだけで、
「アイコンを別フォントで書いていて豆腐になる」たぐいの食い違いを素通しする。

ここでは実データ（build/lore.json）を実定義（resourcepack/assets/**/font/*.json）で
描くので、**実機で起きることがそのまま出る**。ズレも豆腐も、目で見る前に数字で出る。

=== Minecraft に合わせている点 ===

  - provider は **後ろが優先**（JSON の並びの逆順に探す）
  - bitmap の advance … `(int)(0.5 + 幅 × height/セル高) + 1`、太字なら +1
  - space の advance … 表の値そのまま（太字でも変わらない）
  - グリフは**テクスチャの解像度のまま**描く。16px セルを height 8 で使っても
    画素は落ちない（Minecraft は oversample として扱い、GUI 1px を 2 画素で描く）。
    ここでも全体を 2 倍で描いてから縮めずに出す
  - 位置は**実機のスクリーンショットを 1 画素まで戻して測った値**を使う:
    枠から本文まで上下左右 12px / アイテム名のベースライン 19 / lore i 行目 31 + 10i
  - 影は右下 1px に色 ×0.25
  - **lore の行は色を指定しなければ紫（DARK_PURPLE）＋斜体**。バニラがそう当てる。
    白のつもりで描くと、焼き込み彩色のグリフが紫に染まる食い違いを見落とす

使い方:
  ./gradlew loreDump
  python3 bbmodel/render_lore.py [build/lore.json] [出力ディレクトリ]
"""

import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack/assets"

NAMED = {
    "black": (0, 0, 0), "dark_blue": (0, 0, 170), "dark_green": (0, 170, 0),
    "dark_aqua": (0, 170, 170), "dark_red": (170, 0, 0), "dark_purple": (170, 0, 170),
    "gold": (255, 170, 0), "gray": (170, 170, 170), "dark_gray": (85, 85, 85),
    "blue": (85, 85, 255), "green": (85, 255, 85), "aqua": (85, 255, 255),
    "red": (255, 85, 85), "light_purple": (255, 85, 255), "yellow": (255, 255, 85),
    "white": (255, 255, 255),
}


def parse_color(v):
    if v is None:
        return None
    if isinstance(v, str) and v.startswith("#"):
        return tuple(int(v[i:i + 2], 16) for i in (1, 3, 5))
    return NAMED.get(v)


class FontSet:
    """フォントキー -> {文字: グリフ}。Minecraft と同じく provider を逆順に見る。"""

    def __init__(self):
        self._cache = {}

    def get(self, key):
        if key in self._cache:
            return self._cache[key]
        ns, name = key.split(":") if ":" in key else ("minecraft", key)
        path = PACK / ns / "font" / f"{name}.json"
        table = {}
        if path.exists():
            for prov in json.loads(path.read_text(encoding="utf-8"))["providers"]:
                self._add(table, prov)
        if key == "minecraft:default":
            # パックが default を差し替えていない部分はクライアント jar の中身が出る。
            # ここでは幅と絵を自前シートで代用する（アイテム名の見え方の目安になる）
            base = dict(self.get("scorpius:hud_row0"))
            base.update(table)
            table = base
        self._cache[key] = table
        return table

    def _add(self, table, prov):
        kind = prov.get("type")
        if kind == "space":
            for ch, v in prov["advances"].items():
                table[ch] = ("space", float(v))          # 後勝ちなので上書きでよい
        elif kind == "bitmap":
            # **ascent > height はフォント定義ごとロード失敗** = その font の全グリフが豆腐。
            # 描けてしまうと実機との食い違いに気づけないので、ここで落とす
            assert prov["ascent"] <= prov["height"], (
                f"{prov['file']}: ascent {prov['ascent']} > height {prov['height']} "
                "→ クライアントはこの font をロードできず全部豆腐になる")
            ns, rel = prov["file"].split(":")
            im = Image.open(PACK / ns / "textures" / rel).convert("RGBA")
            rows = prov["chars"]
            cw = im.width // max(len(r) for r in rows)
            chh = im.height // len(rows)
            scale = prov["height"] / chh
            alpha = im.getchannel("A")
            for r, row in enumerate(rows):
                for c, ch in enumerate(row):
                    if ch == chr(0):
                        continue
                    ox, oy = c * cw, r * chh
                    right = max((x - ox for x in range(ox, ox + cw)
                                 for y in range(oy, oy + chh)
                                 if alpha.getpixel((x, y)) > 0), default=None)
                    w = 0 if right is None else right + 1
                    adv = int(0.5 + w * scale) + 1
                    table[ch] = ("bitmap", adv, im.crop((ox, oy, ox + cw, oy + chh)),
                                 scale, prov["ascent"], prov["height"])
        elif kind == "reference":
            for prov2 in json.loads(
                    (PACK / prov["id"].split(":")[0] / "font"
                     / f"{prov['id'].split(':')[1]}.json").read_text(encoding="utf-8"))["providers"]:
                self._add(table, prov2)


FONTS = FontSet()

# 全体をこの倍率で描く。**16px セルを height 8 で使うグリフの画素を落とさないため**で、
# 実機の見え方（GUI 1px = テクスチャ 2 画素）に一致する。1 未満のスケールを持つ
# provider があるならその逆数以上にすること
OVERSAMPLE = 2

# 実機で測った位置（tooltip の矩形の左上を 0,0 とした GUI px）。
# **Minecraft は枠から本文まで上下左右とも 12px 取る**。この余白は動かせないので、
# 枠と地のテクスチャ側で外周を透明にして見た目を詰めている（gen_lore_frame.py の PAD）
PAD = 12
NAME_BASELINE = 19
LORE_BASELINE = 31
LINE_HEIGHT = 10


def runs(node, style=None):
    """Component ツリーを描画順に (文字列, スタイル) へ潰す。"""
    style = dict(style or {"font": "minecraft:default", "color": None, "bold": False})
    if isinstance(node, str):
        yield node, style
        return
    for k, sk in (("font", "font"), ("color", "color"), ("bold", "bold"), ("shadow_color", "shadow_color")):
        if node.get(k) is not None:
            style[sk] = node[k]
    text = node.get("text", "")
    if text:
        yield text, style
    for child in node.get("extra", []):
        yield from runs(child, dict(style))


def draw_line(canvas, x0, baseline, node, missing, style=None):
    x = float(x0)
    for text, st in runs(node, style):
        table = FONTS.get(st.get("font") or "minecraft:default")
        color = parse_color(st.get("color")) or (255, 255, 255)
        bold = bool(st.get("bold"))
        for ch in text:
            g = table.get(ch)
            if g is None:
                missing.append((ch, st.get("font")))
                x += 8
                continue
            if g[0] == "space":
                x += g[1]
                continue
            _, adv, cell, scale, ascent, height = g
            # セルを OVERSAMPLE 倍の画面画素に写す。scale が 0.5 のグリフ（16px セルを
            # height 8 で使うもの）はここで等倍になり、画素が落ちない
            img = cell.resize((max(1, round(cell.width * scale * OVERSAMPLE)),
                               max(1, round(height * OVERSAMPLE))), Image.NEAREST)
            # Minecraft は頂点色との**乗算**で色を付ける。塗り潰すと色付きテクスチャが白く出る
            r, g, b, a = img.split()
            tint = Image.merge("RGBA", (
                r.point(lambda v: v * color[0] // 255),
                g.point(lambda v: v * color[1] // 255),
                b.point(lambda v: v * color[2] // 255), a))
            top = (int(baseline) - ascent) * OVERSAMPLE
            shadow = Image.merge("RGBA", (
                r.point(lambda v: v * color[0] // 1020),
                g.point(lambda v: v * color[1] // 1020),
                b.point(lambda v: v * color[2] // 1020), a))
            sx = int(x) * OVERSAMPLE
            canvas.alpha_composite(shadow, (sx + OVERSAMPLE, top + OVERSAMPLE))
            canvas.alpha_composite(tint, (sx, top))
            if bold:
                canvas.alpha_composite(tint, (sx + OVERSAMPLE, top))
            x += adv + (1 if bold else 0)
    return x


def line_advance(node, style=None):
    """行の advance 合計と、途中のカーソル最大値（＝インクの右端）。"""
    x = peak = 0.0
    for text, st in runs(node, style):
        table = FONTS.get(st.get("font") or "minecraft:default")
        bold = bool(st.get("bold"))
        for ch in text:
            g = table.get(ch)
            if g is None:
                x += 8
            elif g[0] == "space":
                x += g[1]
            else:
                x += g[1] + (1 if bold else 0)
            peak = max(peak, x)
    return x, peak


def nine_slice(path, w, h):
    """mcmeta の nine_slice に従って sprite を w x h に伸ばす（角は 1:1、辺は伸ばす）。"""
    im = Image.open(path).convert("RGBA")
    meta = json.load(open(str(path) + ".mcmeta", encoding="utf-8"))["gui"]["scaling"]
    b = meta.get("border", 0)
    if isinstance(b, int):
        b = {"left": b, "right": b, "top": b, "bottom": b}
    L, R, T, B = b["left"], b["right"], b["top"], b["bottom"]
    sw, sh = meta.get("width", im.width), meta.get("height", im.height)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def blit(sx, sy, sw_, sh_, dx, dy, dw, dh):
        if sw_ <= 0 or sh_ <= 0 or dw <= 0 or dh <= 0:
            return
        out.alpha_composite(im.crop((sx, sy, sx + sw_, sy + sh_))
                            .resize((dw, dh), Image.NEAREST), (dx, dy))

    mw, mh = sw - L - R, sh - T - B
    dw, dh = w - L - R, h - T - B
    blit(0, 0, L, T, 0, 0, L, T)
    blit(sw - R, 0, R, T, w - R, 0, R, T)
    blit(0, sh - B, L, B, 0, h - B, L, B)
    blit(sw - R, sh - B, R, B, w - R, h - B, R, B)
    blit(L, 0, mw, T, L, 0, dw, T)
    blit(L, sh - B, mw, B, L, h - B, dw, B)
    blit(0, T, L, mh, 0, T, L, dh)
    blit(sw - R, T, R, mh, w - R, T, R, dh)
    blit(L, T, mw, mh, L, T, dw, dh)
    return out


# バニラが lore の行に当てる既定のスタイル。色を指定していない部分はこれになる
LORE_STYLE = {"font": "minecraft:default", "color": "dark_purple", "bold": False}


def render(entry, out_dir, scale=2):
    lore = entry["lore"]
    widths = [line_advance(l) for l in lore]
    name_w = line_advance(entry["name"])[0]
    text_w = int(max([name_w] + [p for _, p in widths]))
    width = text_w + 2 * PAD
    height = LORE_BASELINE + LINE_HEIGHT * (len(lore) - 1) + 1 + PAD

    tw, th = width, height
    O = OVERSAMPLE
    canvas = Image.new("RGBA", ((tw + 16) * O, (th + 16) * O), (26, 20, 30, 255))
    tip = Image.new("RGBA", (tw * O, th * O), (0, 0, 0, 0))
    bg = PACK / "minecraft/textures/gui/sprites/tooltip/rarity_background.png"
    fr = PACK / "minecraft/textures/gui/sprites/tooltip/rarity_frame.png"
    if bg.exists():
        tip.alpha_composite(nine_slice(bg, tw, th).resize((tw * O, th * O), Image.NEAREST))
    if fr.exists():
        tip.alpha_composite(nine_slice(fr, tw, th).resize((tw * O, th * O), Image.NEAREST))
    missing = []
    draw_line(tip, PAD, NAME_BASELINE, entry["name"], missing)
    for i, l in enumerate(lore):
        draw_line(tip, PAD, LORE_BASELINE + LINE_HEIGHT * i, l, missing, dict(LORE_STYLE))
    canvas.alpha_composite(tip, (8 * O, 8 * O))

    label = "".join(c if c.isalnum() else "_" for c in entry["label"])
    path = out_dir / f"lore_{label}.png"
    canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST).save(path)
    return path, width, widths, missing


def main():
    argv = [a for a in sys.argv[1:] if not a.startswith("-")]
    src = Path(argv[0]) if len(argv) > 0 else ROOT / "build/lore.json"
    out_dir = Path(argv[1]) if len(argv) > 1 else ROOT / "build/lore"
    out_dir.mkdir(parents=True, exist_ok=True)
    data = json.load(open(src, encoding="utf-8"))
    bad = 0
    for entry in data:
        path, width, widths, missing = render(entry, out_dir)
        label = entry["label"]
        print(f"{label:24} tooltip 幅 {width - 8:3d}px / {len(widths)} 行  → {path.name}")
        for ch, font in dict.fromkeys(missing):
            print(f"  **豆腐** U+{ord(ch):04X}({ch!r}) が {font} に無い")
            bad += 1
        if "-v" in sys.argv:
            for i, l in enumerate(entry["lore"]):
                txt = "".join(t for t, _ in runs(l))
                vis = "".join(c for c in txt if not (0xF800 <= ord(c) <= 0xF83F))
                print(f"    {i + 1:2d} |{vis}|")
        odd = [(i + 1, a, p) for i, (a, p) in enumerate(widths) if abs(a - p) > 0.01]
        for i, a, p in odd[:3]:
            print(f"  {i} 行目: advance 合計 {a:.0f}px だがインクは {p:.0f}px まで伸びる")
    print("\n" + ("豆腐なし" if bad == 0 else f"**{bad} 種の文字が豆腐**"))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
