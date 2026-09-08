# 影煙のピクセル連続原画

`shadow-smoke-pixel-v1.png` は imagegen スキルの built-in image_gen モードで生成・修正した独自原画。
外部ゲームのテクスチャは使用していない。用途は `ass_escape` と `ass_guard` のワールドVFXのみ。
毒・刺突・斬撃やスキルUIへの流用はしない。

採用: `exec-c4115b71-0630-4701-9201-9a26b15c3c1f.png`。
初稿 `exec-e1260fb5-8603-4253-b100-d0bb0b3a98cd.png` は細かいにじみとコマ境界の問題があり不採用。
採用原画はこのディレクトリへコピー済み。既定の生成フォルダには依存しない。

4×4の16コマ。`build_magic_frames` で各コマをNEARESTで56pxへ読み込み、
64pxタイル内に4pxの安全余白を置く。黒は透明、4種類の灰色は不透明とする。
原画は上書きしない。ぼかしやアンチエイリアスは追加しない。
`shadow/smoke_0..15` と、紫に着色する `shadow/smoke_shadow_0..15` を配布する。

## 初稿プロンプト

Use case: stylized-concept. Asset type: production pixel-art VFX flipbook sprite atlas for a Minecraft-style action RPG, not a UI icon and not concept art. Create ONE SQUARE 4 by 4 equally spaced grid of 16 sequential animation frames on pure flat black (#000000). No grid lines, text, numbers, borders, labels or characters. Subject: a dense shadow-smoke plume with hooked, swirling lobes and ragged torn tips. It starts as a compressed puff, quickly curls upward and opens into two asymmetrical smoke tongues, then separates into a few large curling fragments and dissipates. Frames read left-to-right then top-to-bottom; each frame different, coherent evolving motion, fixed origin at the center of each cell. Frame 0 already a visible compact puff, frames 2-5 strong full body, frames 6-10 open curling cloud with black negative spaces, frames 11-15 a few dwindling hooked fragments. TRUE pixel art designed on a 64x64 logical pixel grid PER FRAME, visibly chunky crisp square pixel clusters, carefully stepped curves, no antialiasing. Restrict art to four flat grayscale inks: #404040, #808080, #c0c0c0, #ffffff. Black is empty background. Bright broad white/light-gray curl edges and medium-gray cloud masses remain clearly visible when tinted violet in game. Use big deliberate pixel clusters, no one-pixel noise, no dithering, no fine linear filigree. Every frame entirely within the central 75 percent of its equal-sized cell; at least 12.5 percent empty black padding on ALL four sides of every cell, including every detached tip. No clipped marks across cell boundaries. Not a ring, portal, magic sigil, slash, explosion star, realistic fog or photoreal smoke. No soft glow, bloom, gradients, brush texture or blurry edges. Front orthographic view, original game-ready sprite art, no perspective scene or mockup.

## 採用稿への修正プロンプト

Use case: precise-object-edit. This is an edit of the supplied 4x4 smoke flipbook atlas. Preserve the 16-frame evolving smoke sequence and four grayscale-ink style. Fix its production defect: every frame must be smaller and cleanly isolated in its own EXACTLY EQUAL quarter-width/quarter-height tile. Redraw each smoke shape at 65 percent of the tile's width and height, centered, leaving entirely pure black empty gutters of at least 15 percent on ALL sides. No art may touch a tile edge or neighboring tile. Remove ALL tiny noisy specks, all fuzzy glow, all stippling and dither. Use ONLY large, hard-edged pixel clusters as if each frame was drawn at 64x64 pixels and enlarged with nearest-neighbor sampling. Four completely FLAT opaque shades #404040, #808080, #c0c0c0 and #ffffff on solid #000000. No antialiasing or partially transparent pixels. Retain big white/light-gray curl edges and darker interior shapes, not outlines alone. First two rows form and curl, third row tears into big fragments, last row diminishes to a few fading fragments. Do not add lettering, cell borders, numerals, extra objects or a background scene. This is a clean isolated pixel sprite atlas, NOT painted smoke.
