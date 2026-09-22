# Mage v4 原画記録

Tool: built-in image_gen（CLI/API fallbackは使用しない）。2026-09-15。
PNGは生成物のバイトをそのままコピー。UVによって面と画面上の模様の密度を指定する。
32×32 logical pixel scaleを依頼したが、実出力の物理サイズ／細部が正確に32pxという意味ではない。
原画単体で品質合格にはしない。実モデル上で確認する。

## glacial-strata-v01.png

入力1: `.tools/mate-ice-gallery-new.png`（作者の掲載画像をブラウザーで表示したSS。材質の参考のみ）。
入力2: `assets/class-armaments/texture-first/sources/greatsword-material-v02.png`（承認済み作画の参考のみ）。
生成元: `exec-895d95f3-eb3d-4da8-9e66-af6e6657cbfb.png`。

> Use case: stylized-concept. Create ONE ORIGINAL production Minecraft ICE MATERIAL TEXTURE, a square flat UV texture image filling the canvas edge to edge. Input1 is a visual reference for pixel material quality, NOT an image to copy: notice the long ice streaks, stepped pale reflections and cobalt interior. Input2 is the project's approved art style reference, NOT the sword shape. This is NOT a 3D render, not a crystal illustration, not an icon, no background or object silhouette, no framing. Subject is the flat surface of luminous magical ice. Paint long irregular vertical crystalline striations, interlocking medium-sized pixel clusters of blue, cool azure and pale cyan, sparse broken ivory-blue reflection slivers, a few deeper cobalt cracks. Much of the image should be readable middle blue; narrow bright veins and darker deeper channels. 8-12 discrete intentional colours, visibly low-resolution texture at EXACTLY 32 by 32 logical pixel scale, enlarged only with nearest-neighbour. All edges follow the same visible pixel grid. No smooth gradients, no grain, no random checker dithering, no tiny speckles, no black outlines. Broad quiet patches alternating with long textured striations, not uniform diagonal stripes and not a horizontal gradient. Strong variation in length and width of reflection clusters. Top and bottom should join without a border. Entire square is opaque ice material: no transparency, no checkerboard, no margins, no text, no logos, no watermarks, no diagrams or divided atlas panels. It must look like hand-painted low-resolution high-quality Minecraft RPG ice material when wrapped onto a solid pointed model.

## frozen-fracture-v01.png

入力画像なし。生成元: `exec-78ee1f37-b62f-45e7-96b5-f4857c824f0e.png`。

> Use case: stylized-concept. Production flat UV material texture for Minecraft fantasy RPG magical frozen ground, square full bleed. Original opaque low-resolution pixel art, not a scene or an icon. The ENTIRE image is the top surface of thick glacier ice: irregular large angular interlocking plates of muted blue and blue-gray, a few medium length dark cobalt fractures between plates, very selective pale cyan frozen veins inside the plates. Unequal plate sizes with sharp broken corners, no cobblestone pattern. Most of the surface is middle blue with quiet broad frosty patches, NOT white snow. Deliberate hand-painted pixel clusters on one consistent 32x32 logical pixel grid enlarged nearest-neighbor; 8-12 flat stepped colors. No smooth gradients, no tiny grain, no checker dithering, no glow or bloom. The cracks should be thin and branching, not thick black outlines or an even grid. Clearly different from long vertical crystal streaks: this is a top-down broken ice SHEET material. No literal ice block object, no perspective, no camera lighting, no margin, no background, no transparency, no checkerboard, no borders, no words or logos. Entire square filled edge to edge with usable ice surface texture.
## 2026-09-15 炎・魔力の面テクスチャ追加

内蔵 image_gen を使用。生成PNGをそのまま `solar-flow-v01.png` / `lunar-veins-v01.png` にコピー。
原寸PNGを低解像度へ変換したとは主張しない。プロンプト内32pxは狙う見かけの粒度。
作者の表示画像は参照のみで、配布物に収録しない。

### Solar flow

入力: `.tools/mate-solar-gallery-reference.png`（作者掲載画像の表示SS）と承認済み大剣原画。
出力元: `exec-7e80ab41-d92c-4eae-86f3-34f8b31292d8.png`。

Use case: stylized-concept. Asset type: an original game VFX UV material texture, NOT a whole spell sprite. References: Image 1 is the artist's Solar Scepter promotional screenshot, ONLY reference for chunky painted clusters and layered peach/apricot/pale yellow flame colour relationships, do not copy staff/logo/layout; Image 2 is our approved greatsword, reference for restrained handpainted pixel clusters and quiet large planes, not its silhouette or red hue. Create ONE square full-bleed fully opaque texture of flowing magical flame. Flat front view, no objects/background/grid/border/text. Large elongated upward-flowing cream-yellow hot bands, nested pale gold and soft peach bands, reddish-mauve narrow deep separations. Around 32 by 32 logical pixels visibly enlarged with hard nearest-neighbor stepped contours. Broad quiet areas occupy most of texture, only a few shorter inner streaks. 7 restrained flat colours, no smooth gradients, no bloom, no noise/dithering, no realistic lava/cracked rocks. Variation is authored nested long tapered tongues, not stripes of equal widths, not scattered confetti. Paint to all four edges. This will be cropped over solid moving flame geometry, so no transparent silhouette or checkerboard. The painted light must already be inside the texture.

### Lunar veins

入力: `.tools/mate-moonlight-gallery-reference.png`（作者掲載画像の表示SS）。
出力元: `exec-433034d4-3666-44d5-9122-66920de6c273.png`。

Use case: stylized-concept. Asset type: original UV material for native Minecraft magical rift surfaces, not a concept render or whole spell. Image 1 Moonlight Staff is ONLY a reference for crisp low-resolution handpainted light streaks, lavender/white/cold purple palette and high/low density contrast. Do not copy weapon, logo or image layout. Create a square opaque full-bleed flat texture, about 32x32 logical pixels visibly enlarged. Long broken ice-white and pale lilac veins flow vertically through broad quieter desaturated lavender surfaces, with very selective indigo/purple creases. Fewer than eight flat colour bands, high contrast concentrated into two thin longitudinal light seams and several shorter irregular notches, at least half calm midtone. Strong authored clusters, hard stepped edges, long quiet planes, no fine noise, no random flecks, no glow or blur, no smooth gradients, no glitter, no checkerboard/transparency, no object silhouette, no background, no lettering. A UV surface painted all the way to every edge, not a framed illustration. Used in small crops on thin curved dimensional spell ribbons; don't fill it with complicated detail.
