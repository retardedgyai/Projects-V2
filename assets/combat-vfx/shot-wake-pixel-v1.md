# 射撃残光・ピクセル連番 v1

2026-09-08。内蔵 image_gen で新規生成し、1回のスタイル修正後の原画を採用。
外部ゲームの画像は取り込んでいない。原画は `shot-wake-pixel-v1.png`。
採用元: `exec-a1ab14bd-a301-4344-9890-c5eaaa99d2f7.png`。
初稿 `exec-985dfeeb-e508-4729-b80c-05651f0c7ee5.png` は滑らかな縁・ノイズ・枠のため不採用。

## 使用範囲

レンジャーの6射撃技と霜矢の扇の残光のみ。矢雨、罠、命中印へは転用しない。
通常射撃は黄緑、霜矢は氷色。ゲーム判定や命中時刻は変更しない。

`build_shot_wake_frames` が4×4の原画を読み、各コマを28×14へ最近傍で取り込み、
2倍の整数拡大と余白追加で64×32にする。背景以外を3階調・不透明にする。
回転後の配布画像は32×64で、色は2×2画素の塊になる。
長い1枚を引き伸ばすのでなく、約2m以下の区間ごとに2枚の面を±45度で交差させる。
区間境界は重ね、端のUVを切って壁で切られた射線の外へ出さない。
左右反転を交互にし、手元側から先に崩れる位相差を付ける。
1射線につき表示エンティティ1体を維持。16コマで芯→分裂→破片の順に変化。
通常18〜22tick、終の一矢28tick、霜矢24tick。静止したPNGの保持時間を伸ばしていない。

これは射撃の残光の改修であり、全スキルのピクセル化・実機品質保証ではない。
長射程での繰り返し感、一人称、地形との交差、多人数時の描画コストは実機で未確認。

## 最終プロンプト一式（内蔵ツール）

### 生成

```text
Use case: stylized-concept
Asset type: production pixel-art VFX animation sprite sheet for a Minecraft server resource pack.
Primary request: an original 16-frame arrow's magical slipstream dissipating after an instantaneous shot. NOT a physical arrow, NOT a missile, NOT an icon. Broad torn ribbons of kinetic energy curling around a bright central streak, then breaking into chunky feather-like shards flowing RIGHT.
Composition: a 1536x768 wide canvas, exactly 4 columns x 4 rows of equal 384x192 rectangular cells. Read frames left-to-right then top-to-bottom. Every cell has the SAME fixed canvas and centerline. The actual art is authored at a logical 64x32 pixel grid per cell, enlarged 6x with nearest-neighbor. Leave 4 logical pixels of pure black margin on every cell boundary. No borders or labels.
Animation: frames 1-3 strong white forked core with thick layered ribbons, frames 4-6 core splits into two curling bands, frames 7-10 bands peel into angular feather-shaped chunks, frames 11-13 fragments drift right and separate, frames 14-16 only a few diminishing chunks remain. Motion must be continuous, not sixteen unrelated designs. No left-to-right travel of the entire sprite: this is a wake already left along the shot path.
Style: genuinely hand-authored SNES action game pixel effects. Large intentional connected pixel clusters. Stair-step contours, hard square pixels, flat stepped shading. ONLY pure black background plus #555555, #aaaaaa, #ffffff inks. No blur, no glow gradients, no antialiasing, no dither noise, no 3D rendering, no smooth vector curves, no tiny scattered dust. Dynamic asymmetrical silhouette with negative-space tears. No text, watermark, UI, weapons, circles, stars, ground or characters.
```

### 採用版への編集

```text
Edit this sprite sheet. Keep its 4 by 4 layout, monochrome palette, and the progression of the 16 silhouettes. Change ONLY the rendering into literal coarse pixel art with large uniform square pixels: each rectangular cell has a 64 by 32 pixel logical grid, the WHOLE sheet is a 256 by 128 pixel drawing enlarged 6x NEAREST NEIGHBOR. Every pixel is a solid flat square, no noise, no edge highlights smaller than a pixel, no antialiasing or smooth gradients. Use ONLY black #000000 and three solid inks #555555 #aaaaaa #ffffff. Shrink each silhouette inside its cell to preserve an entirely black 4-logical-pixel margin on every side; remove all cell grid lines and frame borders. Keep everything else the same, including the diminishing fragments of the last row. Do not add text.
```
