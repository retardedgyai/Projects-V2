# 影の針・裂ける残光 v1

2026-09-08。内蔵 image_gen による新規生成と1回の編集。CLI/APIキーは使用していない。
採用原画: `needle-rift-pixel-v1.png`。
採用元: `exec-45d18742-da9f-4f08-9bee-609f2c36efd0.png`。
初稿 `exec-7f95d204-8f4c-4f73-9769-d23c444809e5.png` は強いぼかしのため不採用。

## 使用範囲

アサシンの影の針の射線と、確定命中時の短い裂け目だけで使う。レンジャーや他職業の素材へ一括適用しない。
4×4を切り出して40×24へ最近傍縮小、48×32の黒いキャンバスの中央に置き整数2倍で96×64へ。
背景は透明、残りは不透明の3階調。2×2画素のまとまりと四辺8画素以上の空白を持つ16コマ。
短い区間に割り当てた交差面で射線を構成し、端をUVで切り詰める。全射程へ1枚を引き伸ばさない。
画像生成・自動テスト・投影の成功は、Minecraft内の品質承認ではない。

## 完全なプロンプト（内蔵ツール）

### 新規生成

```text
Use case: stylized-concept
Asset type: original pixel-art animation sprite sheet for a Minecraft assassin's shadow-needle VFX, NOT a skill icon.
Scene/backdrop: perfectly uniform solid black #000000, unlit.
Composition: 1536x1024 landscape canvas, exactly 4 columns by 4 rows of equal 384x256 cells. Each cell is literally a 48x32 pixel drawing enlarged 8x nearest-neighbor: hard square pixels only.
Subject: a very narrow HORIZONTAL slit of shadow cut by a piercing needle, pointing right. This is the torn wake, not a physical weapon. Two or three sharp ribbon-like splinters run lengthwise, a white hairline within grey shadow tissue; the ends are pointed, the middle has elongated black gaps. No feather, arrowhead, smoke puff or ring.
Animation: 16 distinct frames, left-to-right then top-to-bottom. Frames 1-3: tight bright piercing streak opens into two torn strips. Frames 4-7: strips shear apart lengthwise, still narrow and fast. Frames 8-12: large gaps, separated pointed fragments drifting apart. Frames 13-16: progressively fewer tiny sharp fragments. Fixed axis and canvas; do not turn the wake into a cloud or an explosion.
Palette: ONLY pure black background and three flat inks #555555 #aaaaaa #ffffff. Strong connected pixel clusters, deliberate stair steps, no isolated noise. At least four logical pixels of solid black padding on EVERY side of EVERY cell. No borders, grid lines, text, numbers or watermark. No gradients, bloom, haze, soft glow, antialiasing, smooth curves or painterly illustration.
```

### 採用版への編集

```text
Edit target: this 4x4 animation sprite sheet. Keep the exact 16 cell layout, each fragment's placement, shape, direction and animation progression. Change ONLY rendering: remove ALL soft glow, grey fog, halo, bloom, gradients and antialiasing. Every background pixel must be pure uniform #000000 black. Draw the fragments with ONLY three flat solid inks #555555, #aaaaaa, #ffffff. Render as literal coarse square pixels: each 384x256 cell is a 48x32 low-resolution pixel drawing enlarged 8 times NEAREST NEIGHBOR. No smaller details or diagonal smooth edges. The final image must look like an unlit pixel sprite sheet, never a luminous preview. Keep at least 4 logical pixels of black border on all four edges of every cell. No grid, words, numbers or new objects.
```
