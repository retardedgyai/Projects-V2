# Hollow Vow — reference and quality gate

Status: **visual study only; rejected as a production model**. The art sheets below are targets, not screenshots of a working Minecraft boss. The current `scripts/build_hollow_vow.py` cube experiment and its CPU preview do not pass this gate.

## Sources inspected

- [ModelFoundry's full Demon Reaper video](https://www.youtube.com/watch?v=ePJyUkTysB0), watched through from the ordinary figure at the opening to the final arena attack. The [creator's product page](https://mcmodels.net/products/8863/demon-reaper) has eight gallery images and lists 12 models, 2 minions and 17 animations. Those counts describe the reference asset, not ours.
- [Gecco's licensed Artorias figure](https://geccodirect-intl.ocnk.net/product/146) and the [FromSoftware Artorias of the Abyss product page](https://www.fromsoftware.jp/jp/detail.html?csm=090). Use the figure for stance, hanging wounded arm, cowl mass and damaged hand-forged armor; do not copy the face, armor pattern or exact sword.
- The existing [Ashen Knight art audit](ASHEN_KNIGHT_ART_DIRECTION.md) and its Gecco front, side and back references remain relevant for shape comparisons.

## What the video actually does

| Beat | Visible staging | What must carry into the successor |
| --- | --- | --- |
| Opening | A plain, quiet humanoid changes through white glints and smoke into the boss. | The reveal needs a readable *before* and *after* silhouette. |
| Middle | An oversized double-ended weapon drives fast, broad close passes. Small red followers enter the fight. | The weapon and body must move together. Secondary enemies need their own shapes and timing. |
| Finale | Red paths and fire mark the floor; a line of bone-like spikes advances toward the player. | Attack warnings need a visible start, direction, delay, impact and aftermath. |

The rejected Ashen Knight/Hollow Vow previews are mostly static cuboids with a long weapon. They lack the continuous mantle mass, expressive asymmetry, transformation, motion arcs, companion silhouettes and arena effects. A painted concept sheet or a passing `.bbmodel` parser cannot close that gap.

## Original successor: Hollow Vow / 虚ろの誓い

A silent pilgrim knight kneels with a sealed execution sword. The seal burns away; a cracked wolf-like iron mask appears from inside a cloth cowl. The wounded arm stays loose, while the other shoulder pulls the sword and the entire torso into each strike. Abandoned armor in the arena briefly stands as pale **Vow Echoes**. A narrow ember seam in the blade draws a line on the floor before old sword fragments erupt along it. The source video supplies the presentation grammar; the knight, oath theme, echoes, weapon and attack silhouettes are new.

- [Four-view form reference](references/hollow_vow_concept_v1.png): front, three-quarter, side and back of the same original design.
- [Three-beat fight storyboard](references/hollow_vow_storyboard_v1.png): reveal, pursuit with echoes, warned ground eruption.

## Gate before ProjectS production integration

1. **Shape:** the head/cowl, left wounded arm, right weapon arm, waist and torn cape read separately in front, three-quarter, side and back views at normal combat camera distance. No rectangular shoulder shelf, cylindrical skirt, rod horns or bright boot blocks.
2. **Material:** wrought iron, woven dark cloth, mail, bandage and leather show distinct wear at native Minecraft resolution. Large flat faces cannot be rescued only by projecting concept art onto them.
3. **Motion:** reveal, idle, pursuit, three sword attacks, recovery, damage reaction and death all have body weight and cape follow-through. The hurt arm keeps its character. Sword placement remains attached to the hand through the full motion.
4. **Fight staging:** Vow Echoes and the floor rupture have their own model/VFX layers, synchronized server hit windows and readable warnings. Effects do not hide the boss's strike direction.
5. **Verification:** inspect the actual Blockbench model and in-game client in four views, then at combat distance and under arena lighting. CPU orthographic previews are diagnostic only. The human Creator's manual smoke/feel check remains required by `AGENTS.md`.

Do not replace `ashen_knight.bbmodel`, enter the production asset set, or claim reference-level quality until these gates pass. Current result: **FAIL**, chiefly on silhouette, material integration and animation scope.
