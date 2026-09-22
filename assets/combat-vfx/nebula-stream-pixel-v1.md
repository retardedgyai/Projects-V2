# 星雲の流れ・氷の庭 v1

2026-09-08 / `play/gyai/class-combat-rework`。全技の品質目標は引き続き未達。

## 素材と用途

星雲原画 `nebula-stream-pixel-v1.png` は内蔵 `image_gen` の新規生成→明暗修正モード。
採用出力 `exec-f664a0fb-bbd2-4bbd-9c92-596f9185f06e.png` をコピーして保存。
初稿 `exec-b82d4c8f-b982-4e42-b97e-b1b3ada53a79.png` は暗すぎたため不採用。
RGB 1254×1254・黒背景の16セル。黒を透明として64×64・4色＋透明に正規化する。
旧来の細い曲線メッシュではなく、太さの変わる二筋の雲が絡み、ほどける連番。
最終書き出しにぼかしは入れない。外部ゲームの画像は使用していない。

使用先は `star_cloud` の攻撃波のみ。落下・爆発・星環・転移・防御へ流用しない。
6本の雲筋は3段の高さ、0/3/6tickの遅延、異なる向きで流れる。
3つの小さな星節が内側を動く。最低3本の主層を保ち、簡易表示でも単なる点にしない。
一波36tick以内で終了。サーバーが発生させる実際の攻撃波で更新し、
別の判定・フィールド・無期限タイマーは作らない。ダメージと持続時間は変更しない。

氷の庭は8か所の氷晶群へ変更。`ice_growth_ice` はコードで作った
24個の段状の立体要素からなるMinecraftモデルで、交差した画像ではない。
底を支点として成長し、最後に低くなる。4段階の遅延で発生し、その場から回転移動しない。
既存の地面高さ補正を使い、地面から浮く中心支点の拡大を避ける。
これは世界へのブロック配置ではなく一時的なItemDisplayなので、移動や採取を妨げない。

## 生成プロンプト

```text
Use case: stylized-concept. Production game asset, a Minecraft pixel-art nebula stream animation mask, NOT an icon or illustration. Exactly FOUR COLUMNS by FOUR ROWS, sixteen equally sized square cells in a perfectly aligned grid. A single HORIZONTAL STREAM of star-cloud energy across the middle of EACH cell, only about 60 percent of cell width and 30 percent of cell height. Wide uniform pure BLACK margins in every cell. The art must NEVER touch any cell border. Four flat grayscale ink values on solid pure BLACK background. True chunky 64x64 pixel art per cell, deliberate staircase contours and connected square pixel clusters, no antialias, no soft gradients, no bloom, no blur, no photographed nebula, no fine noise. The subject is a low drifting magical cloud current: two interwoven irregular vapor ribbons, bright small embedded star grains, darker trailing clumps, an open jagged space between the ribbons. Not a circle, not a spiral logo, not a sword slash, no explosion, no four-point central nucleus. Sixteen chronological frames read left to right top to bottom: 1-3 a few wisps appear at left and gather into two streams; 4-10 the two irregular horizontal streams stretch and curl past one another, with brightness traveling LEFT TO RIGHT through chunky cloud clumps; 11-13 the trailing stream breaks into pixel wisps; 14-16 the last small clumps diminish, last cell almost empty. Same origin and fixed scale in all sixteen frames. Every cell must stay sparse, mostly black, with generous padding. No visible grid, no frames, no letters, no numbers, no stars outside the stream, no other objects.
```

## 処理・調査箇所

### 明暗修正プロンプト

```text
Edit only the INK VALUES and CLEANLINESS of this 4x4 sixteen-frame Minecraft PIXEL ART cloud-stream sheet. Preserve exact cell layout, all sixteen shapes, horizontal movement, animation chronology, fixed centers, generous black margins and breakup. Background must remain pure black #000000. The cloud bodies are much too dark: use only FOUR flat bright gray inks: #808080, #B0B0B0, #E0E0E0, #FFFFFF. Broad main cloud bodies should be #B0B0B0 or #E0E0E0, bright flowing cores #FFFFFF, only trailing clumps #808080. Remove all faint speckled outline noise. Crisp 64x64 pixel clusters and staircase edges, no gradients, NO blur or glow, no soft pixels. Do not thicken the occupied silhouette or fill its negative spaces. No new stars, no central flash, no explosion, no rings, no letters, no grid lines. Keep every frame completely inside its padded cell.
```

### 実装

`build_core_combat_models.py` →配布JSON/PNG/index →
`CoreSkillChoreography.nebulaField` / `iceGarden` → `pose` → `CoreCombatMeshes`。
新しい画像系統は排他的な `CoreMeshAtlas` で選択し、斬撃と破裂の指定が同時に立たないようにした。

最初に確認する場所は `CoreSkillChoreography.kt` の上記2メソッド。
原画の枠が見える場合は生成スクリプトの透明余白検査、氷が浮く場合は
`build_ice_growth` の底支点と `CoreCombatMeshes` の地面高さ補正を確認する。

ゲーム内の遮蔽・連打時の密度・実FPSと最終的な見た目は未確認。
自動投影は実ポーズと配布モデルから作るが、Minecraftの画面ではない。
