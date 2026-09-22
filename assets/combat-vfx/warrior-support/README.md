# 戦士の補助技能：布の原画

- `standard-cloth-v1.png`: built-in `image_gen` で生成したオリジナル原画。2026-09-11。
- 生成結果をworkspaceへコピー。参照先をCodex個人フォルダーへ依存させない。
- 使用先: `scripts/build_warrior_support_art.py` → RP `textures/combat_vfx/warrior_support/cloth.png`。
- 原画は保持。ゲーム用テクスチャは可視alphaの外接矩形を48×80へnearest-neighbor変換。
  色の塗り直しやブラーは行わない。V字の切れ込みと透明背景を保持する。
- 布は12列×20行の薄い両面パネル。上端固定、16個の風姿勢、裾からの消散。
  原画全体を回すアニメーションではない。旗竿だけ細い立体部品。
- 大剣はこの原画から生成しない。受け流しには承認済み `greatsword-material-v02.png`
  の既存変換結果をそのまま使い、握り位置も同じ画素 (10.5, 63.5) に合わせる。
- 原画生成・投影・テストはCreatorのゲーム内承認を意味しない。

## 最終生成プロンプト（built-in、CLI/API fallbackなし）

Use case: stylized-concept. Asset type: production pixel-art texture for one Minecraft RPG warrior battle standard, NOT a UI icon or a scene. Primary request: a single flat hanging swallowtail cloth banner, front orthographic view, no perspective, perfectly straight horizontal top edge, broad rectangular upper cloth tapering into two short pointed tails separated by a V notch. Cloth fills most of square image with transparent padding. Burgundy crimson dyed cloth, charcoal reinforced edge, narrow ivory stitching and steel-grey accents. Center emblem: one broad ivory greatsword silhouette with angular crossguard, blade pointing down, readable large shape; no letters. High quality deliberate square pixel clusters on a strict approximately 64x64 logical pixel grid, enlarged with nearest-neighbor edges, about 12 restrained colors; visible coarse pixel steps, no antialias, no smooth gradients, no glows, no painterly noise. Material is conveyed by 3-4 large angular fold/shadow planes, subtle worn hems, not granular texture. Match hand-authored Minecraft fantasy item texture language. Main cloth should remain broad simple dark red fields so emblem reads from distance. Constraints: cloth ONLY, no pole, no crossbar, no rings, no background, no cast shadow, no text, no UI frame, no surrounding sparks, no models. Genuine transparent background and transparent V notch. This image is the undeformed source texture; wind/depth will be added by native geometry, do not paint a waving silhouette.
