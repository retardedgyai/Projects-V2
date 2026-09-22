# 浄化の光・ピクセル連番 v1

2026-09-08。内蔵 image_gen で新規生成し、背景と輪郭を1回修正した採用版。
採用原画: `purifying-light-pixel-v1.png`。
採用元: `exec-36550809-49b9-4a91-9b45-71e4fedb13f4.png`。
初稿 `exec-0e8de5fc-3105-4e14-a655-37f222783d2f.png` は背景のぼかしのため不採用。
外部ゲームのビットマップは取り込んでいない。

## 使用箇所と取り込み

裁きの光・導きの印の光線、断罪の柱の上昇光、灯火の炎と照射、最後の審判の着地光。
回復の花弁・祈りの翼・光の歩みの羽根には使わない。

`build_healer_prayers` が4×4の原画を分割し、各コマを12×28へ最近傍で取り込んで
整数2倍拡大と余白を付け、32×64のテクスチャにする。背景透明・他は不透明の3階調。
光柱は下端固定の交差面。光線は短い区間に分け、壁で切られた端のUVを切り詰める。
最初の4tickで光が立ち上がり、その後に羽根状の光が裂けて散る。
祈灯の炎だけは実際の8tick周期に合わせて中盤のコマを繰り返し、最後に消散する。

最終の実機品質は未確認。画像が生成できたこと・自動テスト合格を品質達成の代わりにしない。

## 最終プロンプト一式（内蔵ツール）

### 新規生成

```text
Use case: stylized-concept
Asset type: original pixel-art animation sprite sheet, divine purifying light VFX for a Minecraft-compatible fantasy game, NOT a UI icon.
Canvas: portrait 768x1536. Exactly 4 columns and 4 rows, 16 equally sized TALL cells, each cell 192x384. Each cell is literally a 32x64 pixel drawing enlarged 6x nearest neighbor. All pixels must be large uniform solid squares, no smaller details.
Subject: a narrow vertical fountain of sacred light rises from its bottom root, blooms into three flowing, feather-like tongues, splits lengthwise, then releases ascending ribbons and angular feather fragments. No physical bird or feather quill. This is radiant energy, not fire, smoke, lightning, a star or an explosion.
Animation read order left-to-right then top-to-bottom: frames1-2 a strong rising white shaft; frames3-5 peak with tall white core and two curling side tongues; frames6-9 long ribbons split with large negative-space gaps; frames10-13 detached ascending fragments; frames14-16 a few tiny diminishing fragments. Keep a fixed bottom root, fixed canvas, consistent fluid upward motion. The peak is early, the breakup is longer.
Pure black background and ONLY three flat inks #555555 #aaaaaa #ffffff. High contrast large connected pixel clusters, crisp stair-step silhouette, distinct flat light/shadow planes. Fully black margin of at least 4 logical pixels on EVERY cell edge. No grid lines, frame outlines, lettering, numbers, logos, watermark, stars, circles, lens effects, soft glow, gradients, antialiasing, dither or noise. No smooth illustration pretending to be pixels.
```

### 採用版への編集

```text
Edit target: this 16-frame pixel sprite sheet. Preserve the exact 4x4 layout, shapes, positions, pixel sizes and animation progression. Change ONLY its rendering/palette: completely remove ALL gray haze, gradients, bloom, antialiasing and noise. Everything outside the hard-edged sprite shapes must become perfectly uniform solid #000000 black. Inside the shapes use ONLY solid square pixels in #555555, #aaaaaa, #ffffff with no intermediate values. All 16 cells must have at least 4 logical black pixels of margin around the complete effect; preserve empty cells' margins. No borders, labels, added shapes or lighting. This is a flat unlit spritesheet for engine tinting, not a glowing preview.
```
