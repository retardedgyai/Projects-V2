# 星術の破裂連番 v1

2026-09-08。内蔵 `image_gen` の新規生成→局所修正モード。外部ゲームの画像は使用していない。
採用原画: `stellar-burst-pixel-v1.png`（RGB 1254×1254、黒背景）。生成出力
`exec-3ed1f725-2fdc-43c7-9dc2-68a208a220fe.png` をコピーし、元ファイルも残している。

初稿 `exec-58026ab7-cbbd-4cb9-80fb-b43c47a5336a.png` は余白不足・細かなノイズのため不採用。
採用版は16セルを切り出し、最近傍64×64・4色＋透明に正規化する。
全辺4px透明を生成スクリプトとテストで検査。画像としての輪郭を維持し、ぼかしは加えない。

## 用途

- 星降る夜／天球崩壊: 予備動作の落下核が着弾した後の破裂。核の静止表示と追加の星環図形を置き換える。
- 星砕き: 確定命中点で大きく分裂。空振りの光線先端には出さない。
- 星糸／星の針: 小さな命中閃光。星の針の固有の印は残す。
- 星雲・星衣・星座の守り・星渡り・星環: この破裂素材は使わない。技の意味が異なる。

主面は紫、交差する残光は青。二面は90度の角度差、2tickの時間差を持ち、
破片化と面内の回転・少量の上昇を組み合わせる。カメラ追従の板ではない。
星の着弾には地面側にも分解する光を置き、旧来の静止した枝状の幾何学模様を置き換える。
最も明るいフレームは2tick、後半は中空の渦と分解した破片。判定時刻・ダメージは変更しない。
本人体数48／シーン384など既存上限を維持する。

## 新規生成プロンプト

```text
Use case: stylized-concept. Asset type: original Minecraft world-space spell VFX animation sprite atlas, NOT an interface icon. A square sheet with exactly 4 columns and 4 rows of equal square cells, sixteen sequential frames read left to right then top to bottom. True low-resolution PIXEL ART, each cell designed on a 64x64 logical grid enlarged with nearest neighbour. Bold deliberate square pixel clusters, staircase contours, four flat grayscale inks on uniform pure BLACK RGB background (black will be keyed out). Subject: a magical STELLAR DETONATION seen face on. Frame 1 a small four-point luminous star nucleus, frames 2-4 the nucleus compresses and then explodes into a brilliant asymmetrical eight-point impact, frames 5-8 broad swirling broken ribbons of energy surge outward from the hollow center with several substantial star fragments, frames 9-12 the ribbons tear into angular pixel embers and short outward streaks, frames 13-16 only decreasing scattered embers, last frame almost empty. Fixed center and canvas scale throughout, not sixteen independent logos. Not a concentric ring, no circular outline, no magic diagram, no text, no frame, no grid lines, no weapon, no scenery. Connected white highlights and light-gray chunky bodies with medium-gray/dark-gray trailing clusters. NO smooth gradients, NO blur, NO bloom, NO antialiasing, no hairlike filaments, no photographic space nebula, no noise speckle. Wide completely black empty border of at least 8 logical pixels in EACH cell, nothing crosses or touches cell edges. All sixteen frames perfectly aligned to the same 4x4 grid. This is a flat grayscale cutout animation mask with obvious blocky pixels, for tinting with violet/blue in the game, not a high-resolution illustration.
```

## 修正プロンプト

```text
Edit this sixteen-frame PIXEL ART sprite sheet for production. Keep the same 4x4 layout, frame chronology and shapes: star ignition -> explosive star -> broken swirl -> dwindling embers. Crucial correction: redraw every frame at only 65 percent of its present size, around exactly the CENTER OF ITS OWN CELL; apply the SAME SCALE to ALL sixteen frames, including tiny early and late ones. Every 4x4 cell must now have a completely empty BLACK border, wide enough that NONE of the ink ever touches the cell edges. Keep background solid pure black. Remove ALL speckled gray noise, glow, soft pixels and outlines. Each shape uses only FOUR SOLID INK VALUES: white, light gray, middle gray, dark gray. Hard chunky square pixels at a 64x64 logical grid per frame, big connected clusters, staircase edges. No smoothing, no bloom, no gradients. Do not add frames, text, visible grid, or any new art. Preserve the sixteen distinct animation phases and fixed centers. This is original Minecraft VFX cutout source, not a rendered glow preview.
```

## 再生成・確認

`scripts/build_core_combat_models.py` →配布テクスチャ／モデル／アイテムJSON／index。
`CoreSkillChoreography.stellarBurst` →時刻ごとのモデル・座標→ `CoreCombatMeshes` のItemDisplay。
壊れた場合は同メソッドと `pose` のフレーム選択を最初に確認する。

この原画だけで全星術の完成とはしない。ゲーム内での連続攻撃・遮蔽・音との一致は
Creatorの手動確認が必要。星雲や防護など別の技群は引き続き改修対象。
