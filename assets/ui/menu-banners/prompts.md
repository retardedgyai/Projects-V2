# 手帳の道の帯：画像生成の頼み方

鍛冶（`assets/core-ui/forge-vignette-master.png`）と魔術（`assets/first-magic/observatory-workshop-concept.png`）の帯は、Codex の画像生成（image_gen）で作った一枚絵。戦闘と採取も同じ作風にそろえるため、同じ道具で作る。

Codex のコマンド版（`codex exec`）には image_gen が無いので、Codex アプリで頼む。上の2枚を添付して、下の文をそのまま貼る。できた絵はこのフォルダに、書いてある名前で置く。

---

You are making illustration art for an original Minecraft RPG server called ProjectS. Use the built-in image_gen tool, one call per image. The two attached images are the APPROVED style: detailed pixel-art illustration, Minecraft-like blocky forms, warm rich lighting, deep shadows, high detail, 1536x1024 landscape. Match that style, rendering quality and colour richness exactly. Original work only. No text, no letters, no UI, no watermark, no characters.

These pictures are cropped into a WIDE banner strip (about 5:1). Keep the main subject inside the vertical middle third, centre-right. Keep the LEFT 35% calmer and darker, because text sits there.

1. combat_a.png: a weathered red shrine torii gate on a hill at dusk with sakura petals blowing. Beyond it, the dark looming silhouette of a huge horned monster among the trees, with embers and a faint purple glow. Tense, about-to-fight mood.
2. combat_b.png: the mouth of a ruined stone arena or dungeon at night, with torches burning. Broken weapons and shields lie on the ground and claw marks scar the stone. A red glow and a pair of glowing eyes wait deep inside the darkness.
3. gather_a.png: a lush grassy hillside meadow in golden morning light, with huge old tree roots, wildflowers and herbs. An exposed glowing ore vein runs through a rock, and a woven basket of harvested herbs and a pickaxe lean against it. Calm, abundant mood.

Copy each finished image from `$CODEX_HOME/generated_images/` into `assets/ui/menu-banners/` with exactly these names.
