# 流星の独立した炎形状・原画

Built-in imagegenで生成したオリジナル原画。参照の画像そのものをpackへ取り込んでいない。
PNGのbytesは生成出力からそのままコピーし、ラスターの後加工はしていない。
暗紺の背景は透過ではないため、ゲームへ板として貼らない。

- `meteor-lobe-orthographic-v01.png`: 膨らんだ炎塊の正面・側面。
- `meteor-pressure-orthographic-v01.png`: 横へ開く炎の正面・側面。
- 消費先: `scripts/build_mage_meteor.py` の `lobe_views / lobe_volume / lobe_surface`。
- 参照: R12 [Wynncraft Mage Meteor](https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s) の155.70秒の接触面、155.84秒の巻き込む塊。R09 [MatE Solar Scepter](https://mcmodels.net/products/16393/mates-mythic-weapons-solar-scepter) の大きな明部と橙の面。
- 参考画像は観察用の `.tools/wynn-meteor-slow-review.png`。原作者の3D実装方式を推定したものではない。

14セルの意図的なgeometry解像度で暖色領域と四段階の明暗を読み、正面／側面が交わる厚みを丸めた外面へ変換。
微小な非接続先端だけ除外し、2%以上が非接続なら生成を失敗させる。内部面は出さない。
PNG自体はpackの使用textureではなく、再生成に必要なsource。温度別モデルは同じ面境界を保つ。
この原画と構造の採用は、全スキルの視覚品質合格を意味しない。

## Lobe prompt

Use case: stylized-concept. Asset type: orthographic game texture atlas. Reference image 1: style and flame material reference only, not a composition to copy. Draw ONE small self-contained explosion lobe, like ONE orange rolled puff on the left shoulder of the reference's 155.84s explosion. Not the whole explosion. Show this same single lobe twice: FRONT on left half, RIGHT SIDE on right half, on a wide 2:1 canvas. Equal height and baseline, each centered in its square half with ample empty margins. Form: asymmetrical fat comma with a broad creamy curled belly, a thick orange turning edge, rusty underside and a single short torn tail curling back. One solid compact body per view, no hole through the center, no arch, no multiple branches, no legs, no torch/flame rising vertically. Side view is about 70% as wide as front, substantial and rounded with its own matching creamy highlight. Minecraft coarse pixel art, roughly 24 logical pixels in height per object, large deliberate clusters of 4-5 solid colors, visibly stepped edges. No outlines, dither, stipple, gradients, lighting effects, ground, sparks, smoke, labels or text. Flat uniform #101820 navy background. Entire outer contour is naturally rounded/tapered including bottom, no straight crop seams. This is one volumetric hot puff used inside a larger explosion, not an entire spell icon. Pair's top/bottom and bulges must match in 3D.

## Pressure tongue prompt

Use case: stylized-concept. Asset type: paired orthographic texture atlas for one 3D Minecraft explosion pressure tongue. Image 1 is material/motion reference only: the broad cream-yellow outward blast wings at 155.70s, NOT the later arch. Create ORIGINAL coarse pixel artwork. Wide 2:1 canvas with TWO equal square cells, FRONT left and RIGHT SIDE right of the same horizontal hot jet. This is NOT a round fireball or curled puff. One tapered root at left opens into a broad swept triangular flame fan heading right and slightly upward, with 3 unequal long torn points at its far edge, and a lower hooked trailing edge. Pale cream inner wedge takes most of the width, two large peach-orange edge regions, small rusty underside. Right-side orthographic view shows a thick central rib and narrower lateral fins, not a duplicate front silhouette; same top and bottom and matching volume. One connected complete self-contained shape in each view; no disconnected particles, full blast, arch, floor, labels or text. Coarse deliberate Minecraft pixel clusters about 24 logical pixels across the shape, 4-5 broad solid colors, hard stepped outline, no smooth gradients, noise, dithering, black outline, glow, perspective or image lighting. Flat uniform #101820 navy background. Each whole shape fully inside its half with generous margins. No straight clipped base or crop seams. A directional expanding pressure tongue, not a flame icon standing vertically.

## 棄却した候補

前の2×2候補 `exec-2f130781-7081-4dcf-bf37-f94692696131.png` は一つの炎塊ではなく爆発全体のアーチを再描画していたため、採用もモデル化もしていない。
