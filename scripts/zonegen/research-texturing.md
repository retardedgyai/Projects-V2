# Terrain texturing research — how skilled terraformers texture ground and cliffs (block level)

Date: 2026-10-20. Target: Project S zonegen (Java 26.x blocks). Scope: meadow ground, forest floor, paths, shores, scree, cliffs, light/shadow, amateur tells, landform.

Method: (1) written builder guides (kette442 / DurinsBuilders PMC series, Conquest Reforged gradient guide, Lord of the Craft WE ridge guide, minecraft.net/BlueNerd, 4netplayers, blockblend / minecraftgradient recipe sites, WorldPainter source code); (2) screenshots viewed at 1:1 (Wynncraft wiki in-game shots: Nivla Woods, Llevigar Plains, Maltic Plains; PMC map renders: Dovaxios 2025, Terra Celestis, Nordia); (3) **measured** mean luminance of every relevant block texture from the local `26.3.jar` (plains tint applied to grass/plants) to get a real value ladder instead of guesses.

---

## 0. The three findings that matter most

1. **Pros do NOT speckle the meadow.** In the Wynncraft plains shots, the walkable meadow is ~95 % plain grass block. The richness comes from (a) landform (1-block terraces whose dirt sides draw contour lines every 4–12 blocks), (b) clustered ground cover (short grass in drifts, one-species flower drifts), (c) a few readable objects (a 3–5 block boulder, a bush, a pond with a gravel/clay rim, a path), (d) trees whose root ring is brown. kette442 (DurinsBuilders) states it explicitly: *"Usually I do not like to add more blocks to the grass since the other blocks are not similar enough and grass in general already has more change to it than other blocks because of the side of the block."* Mixing coarse dirt/podzol into a sunny meadow at random is the classic amateur tell; coarse dirt belongs where there is a REASON (bank erosion, trampled ground, tree bases, path edges — minecraft.net/BlueNerd: *"swap out some of that pristine grass for coarse dirt and paths"* around river banks).
2. **Cliff value must be driven by form, in big plates, with a measured ladder.** Measured luminance (0–255) of the stone family: calcite 224 · diorite 189 · smooth stone 159 · light-grey concrete powder 154 · andesite 136 · gravel 128 · cobblestone 127 · **stone 126** · mossy cobble 115 · tuff 108 · polished tuff 102 · deepslate (side) 80 · cobbled deepslate 78 · polished deepslate 73 · blackstone 37. Note: **stone, cobblestone, gravel and andesite are all within 10 units of each other** — a "gradient" built from them has no value gradient at all, only texture noise. That is why cliffs made from stone/andesite/cobble look flat: there is no light/shadow in the palette. A readable face needs ≥ 3 real value steps (e.g. 189 → 136 → 108 → 80) placed by form, and the game's own face shading (top ×1.0, N/S sides ×0.8, E/W sides ×0.6, underside ×0.5) stacked on top.
3. **Patch scale:** secondary blocks go in clusters of 2–6 blocks with gaps (Conquest "clustered gradient": vary cluster size, shape and position, never rows/columns); random single-block splatter "rarely looks good" and is reserved for areas nobody looks at. On cliffs, patches are **vertical/irregular, never horizontal stripes**, and one block (stone) runs through every band so it reads as one rock.

---

## 1. Meadow ground (sunny, walkable)

### What the references actually show
- Wynncraft Maltic/Llevigar plains: top layer ≈ plain grass; short grass tufts at roughly 1 per 6–12 blocks, in loose drifts, denser near walls, trees and water, almost none on the walked centre; flowers as one-species drifts of 4–12 plants (white/blue), not confetti; 1-block terraces with long wandering contours; a 3–5 block boulder of stone + andesite + cobblestone (values 126/136/127 with the engine's side shading giving the depth); 1–2 bushes (leaf blocks) per 30×30; a pond with a 1-wide rim of gravel/clay/sand and a few lily pads.
- Dovaxios / Terra Celestis aerial renders: meadow reads as green with **sparse dark dots** (bushes, single trees, 2–4 block brown patches) at ~1 per 15–25 blocks — never a uniform brown speckle.

### Recipe
- **Top block:** grass_block 94–97 %. Remaining 3–6 % only as *motivated* patches:
  - coarse_dirt (L 91) in 2–5 block patches on the UPSLOPE side of boulders, at tree bases, along path edges and bank tops (erosion);
  - dirt_path (L 123, lighter and warmer than grass 99) only as trampled ground in front of doors, gates, around wells;
  - moss_block (L 101, same value as grass, cooler/greener hue) in 3–8 block carpets ONLY in shade (north side of boulders/walls, under trees, by water). In the sun it reads as a wrong-coloured grass patch.
  - podzol: not in a sunny meadow (it is the spruce-forest block).
- **Grass tufts:** short_grass density 8–15 % of blocks, placed by low-frequency noise so it forms drifts (patches ~6–10 blocks across with empty ground between), thinner (2–4 %) on walked/central areas, denser (25–40 %) in the 2–3 blocks beside walls, hedges, water, boulders. tall_grass ≤ 1/5 of short grass (kette442: *"keep the ratio from tall grass to double tall grass clearly on side of the normal tall grass"*; *"less grass near civilisations and places that may be used from time to time"*). Ferns only in shade/forest.
- **Flowers:** choose 1 main species per area (kette442 used all-white: oxeye daisy / lily of the valley / azure bluet) in drifts of 4–12, plus a few scattered single accents of a second colour (poppy/cornflower) at ≤ 1/5 of the main. Never 6 species mixed.
- **Objects per ~40×40:** 1 boulder cluster (3–7 blocks, stone+andesite, one cobblestone/mossy cobble on the shaded side, a moss carpet on top in shade), 1–2 bushes (oak/azalea leaves 1–3 blocks on a 1-block dirt/rooted dirt base), occasional leaf_litter under a lone tree (L 171 — very light, 1–3 pieces, only directly under canopy).
- **Side faces:** every 1-block terrace shows a dirt side (dirt L 102 vs grass 99, but warm brown) — this is the "free" texture of grass; design terraces (height steps every 4–12 blocks, wandering) rather than smooth 1:1 slopes, which hide the sides.
- **What makes vanilla grass look unfinished** (sources + observed): uniform height with no terraces → no dirt contour lines; even-spaced tufts (looks like a datapack); random coarse-dirt/podzol speckle; flat colour with nothing to break scale (no boulder/bush/pond); straight runs > 7 blocks (BlueNerd: *"never placing more than seven blocks in a straight line"*).

## 2. Forest floor, paths, shores, scree, under-tree shade

### Forest floor (deciduous)
- Values: grass 99, moss 101, rooted_dirt 110, dirt 102, coarse_dirt 91, podzol_top 66 (much darker — spruce/old forest only), mud 58.
- Nivla Woods (Wynncraft): path floor of stone-family blocks with moss creeping in from the edges; between trees: grass with high tuft density, moss_carpet patches, rooted dirt / coarse dirt in 2–4 block patches at trunk bases, bushes (leaf blocks at ground level) every 4–8 blocks, flowers (pink/white) in small drifts, dead bushes rare.
- Recipe: grass 55–65 % · moss_block 15–25 % (clusters 4–12, cores under the densest canopy) · rooted_dirt 8–12 % (ring 1–2 wide around trunks, plus trailing patches) · coarse_dirt 5–8 % (as 2–4 block patches linking the rooted dirt) · podzol only for conifer stands (then podzol 50–60 % replaces moss). Cover: short grass 10 %, fern 10 % (ferns in the darker inner zones), large fern 2 %, leaf_litter 1–3 pieces per tree directly under canopy, brown mushroom 1–2 per 20×20 on podzol/moss, moss_carpet on the shaded top of roots/boulders. A bush (2–4 leaf blocks on the ground) every 5–10 blocks.

### Path / road (centre → edge gradient)
- 4netplayers overgrown trail: coarse_dirt base laid "somewhat randomly" first, then rooted_dirt, granite, packed_mud, dripstone_block cycled with no identical neighbours; mossy_cobblestone + gravel only where stone "pokes through"; granite slabs on height steps; grass/ferns placed ON the trail; buttons/pressure plates as pebbles at edges.
- kette442: animal track = coarse dirt only; human path = coarse dirt + andesite + gravel; near ruins add cobblestone / mossy cobble / stone bricks.
- Conquest gradient guide (roads): *"On plains grass, darker tread lines in the middle usually work… Transition darker dirt to lighter dirt, then add variation."*
- blockblend path-edge recipe: edge band 60 % coarse dirt / 40 % dirt; next band 40 % coarse dirt / 40 % gravel / 20 % cobblestone; centre 80 % cobblestone / 20 % gravel (this is for a stone road; invert for a dirt track).
- **Recipe (dirt track, 3–5 wide):** centre line (1–2 wide): dirt_path 60 % / coarse_dirt 30 % / gravel 10 % (wheel/foot tread; value 123/91/128 gives a light-dark-light stripe if you use two tread lines on a cart road); shoulders: coarse_dirt 50 % / grass 35 % / dirt_path 15 %; outer feather 1–2 wide: grass 75 % / coarse_dirt 25 % as 1–3 block fingers, never a straight edge; a short grass tuft or fern on 10 % of shoulder blocks; pebbles (stone button / andesite slab) 1 per 8 edge blocks. Width should breathe (3→5→4). In towns swap to cobblestone/andesite/gravel/stone-brick with moss_carpet at the edges.

### Shore / bank
- WorldPainter BEACHES terrain (source): clay patches (SMALL_BLOBS ≈ 16-block noise, rare), sand where a large-scale noise (HUGE ≈ 131 + ½ SMALL ≈ 16) is high, gravel where it is low, grass otherwise → beaches are **long sand/gravel alternations at ~100-block scale with 16-block edge wobble**, not per-block mixes.
- minecraft.net/BlueNerd river: replace the vanilla clay/gravel/sand clutter in the bed with dirt, texture with granite + seagrass; erode banks with coarse dirt and dirt_path; foliage on banks.
- kette442 river: underwater floor = coarse dirt, banks = grass up to the water; extra tall grass and flowers at lake edges.
- minecraftgradient riverbank order: sand → clay → gravel → mud → rooted dirt → grass (clay/mud/rooted dirt closest to water); rocky shore: sand → gravel → cobblestone → andesite → dirt → grass.
- **Recipe (lowland stream/pond):** grass runs to the water on 60–70 % of the edge; the rest gets a 1–2 wide eroded lip of coarse_dirt 50 / dirt_path 20 / gravel 20 / mud 10 in 3–8 block runs (outer bends of a stream); bed: dirt 40 / gravel 30 / clay 20 / sand 10 in 3–6 block blobs, with 1–2 granite or stone blocks as "river rocks" per 10 blocks of bed; seagrass on 20 % of bed; reeds/tall grass drifts on 30 % of bank; lily pads in still water. A sand stripe along the whole waterline is an amateur tell.

### Scree / talus at a cliff foot
- blockblend "rubble" band: 5 % andesite / 70 % cobblestone / 25 % gravel; LotC ridge guide: cobblestone slabs/stairs on curved and ending edges.
- **Recipe:** an apron 2–5 blocks wide (wider under taller faces, interrupted — not continuous): gravel 40 / cobblestone 25 / andesite 15 / stone 15 / mossy_cobblestone 5, as a 1-block layer sloping 25–35°, with stone/andesite/cobblestone **slabs and stairs** on 30 % of its outer edge and 2–3 full-block "fallen boulders" (2–3 blocks each, stone/andesite) per 20 blocks of face; grass + short grass + ferns reclaiming the outer 1–2 blocks (fern 15 %). Values 128/127/136/126 are flat, so the apron's contrast comes from slabs (half-height shading) and the darker face above it, not from block colour.

### Under-tree shading
- Pros rely on the engine (leaf shadow) plus a hue shift to cooler/darker ground: moss_block (cooler green, same value) and rooted_dirt under the canopy, podzol under spruce; ferns instead of grass tufts; a moss_carpet on top of the root blocks. 1 block of rooted dirt or coarse dirt per trunk side is enough; a full brown disc looks like a planter.

## 3. Cliffs / rock faces

### Measured value ladder (mean luminance, 26.3 textures, higher = lighter)
| block | L | note |
|---|---|---|
| calcite | 224 | brightest natural rock; highlights/rims only ("reserve calcite for the brightest edges") |
| diorite | 189 | light; sd 35 = very noisy, use in 2–4 block clusters |
| smooth_stone | 159 | flat, clean |
| light_gray_concrete_powder | 154 | WorldPainter-style highlight filler |
| andesite | 136 | the "bridge" between stone and cobble; smooth texture |
| gravel | 128 | same value as stone, soft texture |
| cobblestone | 127 | same value as stone, very noisy (sd 29) |
| **stone** | **126** | base |
| stone_bricks | 122 | |
| mossy_cobblestone | 115 | greenish |
| dripstone_block | 112 | warm brown-grey, light-ish |
| granite | 112 | warm |
| tuff | 108 | neutral grey-green, one real step darker than stone |
| polished_tuff | 102 | |
| deepslate (side) | 80 | the dark end (top face 87) |
| cobbled_deepslate | 78 | |
| polished_deepslate | 73 | |
| basalt / smooth_basalt | 73 | |
| deepslate_tiles | 55 | |
| blackstone | 37 | deepest cracks only |
Engine face multipliers on top: top 1.0, N/S 0.8, E/W 0.6, bottom 0.5. So a stone block's E/W side shows ≈ 76 (as dark as a deepslate top), and its underside ≈ 63. Use this: on a face that runs N–S the E/W sides already read 40 % darker; add palette darkness mainly on the top-lit and N/S faces that stay bright where you want shadow.

### Palettes by face role
- **Lit face (sun side, upper 2/3):** stone 60 / andesite 25 / diorite 10 (in 2–4 clusters, only on bumps) / calcite 5 (rim and the single brightest buttress edge). No cobblestone here (it adds noise, not light).
- **Shaded face (away from sun, bays, lower 1/3):** stone 35 / tuff 35 / cobbled_deepslate 15 / deepslate 10 / mossy_cobblestone 5 (only at the foot and in seeps).
- **Overhang underside / cracks / under ledges:** deepslate 50 / cobbled_deepslate 25 / tuff 20 / blackstone 5 (blackstone only in the innermost 1 block of a crack); the ×0.5 bottom multiplier does most of the work — a stone underside already reads 63, so keep the underside palette only one step darker than the face above it or it goes black.
- **Ledge tops:** stone 50 / andesite 30 / gravel 10 / moss_block or moss_carpet 10 (shaded ledges); grass only on treads ≥ 2 wide; a 1-wide dirt lip under grass at the rim is correct geology (kette442: remove grass "on the really steep parts… and everywhere under an overhanging part", keep grass connected in runs, "do not just randomly remove 1 layer of grass").
- kette442's base template: stone + cobblestone + andesite is "one of the most used block templates", and he uses **clay (L 166, bluish) to show the shadow under overhangs** — i.e. a flat, cool, distinctly different block in the shadow zone, because stone/cobble/andesite cannot make a shadow by themselves. Modern equivalents: tuff/deepslate for dark shadow, or a cool light block (calcite/diorite) for reflected light.
- Sequences from the gradient sites (all agree, light → dark): stone → andesite → tuff → cobblestone → deepslate (cliff); stone → andesite → gravel → tuff → cobblestone → deepslate ("irregular vertical patches, not straight stripes"); white cliff: andesite 50 / diorite 30 / stone 20 base → diorite 60 / calcite 25 / quartz 15 mid → calcite 40 / snow 35 / diorite 15 top.
- Ratio rules: 60 % main / 30 % transition / 10 % accent; carry one block (stone) through every band; 6–8 bands on a tall face, otherwise it stripes; never stone straight to deepslate (ΔL 46) — tuff between (ΔL 18 + 28).

### Patch size and "big plates"
- Observed on Dovaxios/Terra Celestis white cliffs: the face is **one light material** (calcite/diorite family) in plates 6–20 blocks wide, cut by dark horizontal grooves 1–2 blocks high and vertical ribs; the dark blocks sit ONLY in grooves/recesses. That is why it reads as rock, not confetti: value changes happen at form boundaries, and inside a plate the material is nearly uniform (≤ 15 % secondary block in 2–4 clusters).
- Rule set: (1) pick the plate material from the face role (lit/shaded/underside) with a coarse noise (period 12–24 blocks) so plates are large; (2) inside a plate, a secondary block one ladder step away at 10–20 %, in clusters of 2–6 with gaps; (3) a third block only at form edges (rim highlight, groove shadow), ≤ 5 %; (4) no horizontal stripes unless you are building strata, in which case beds are 3–8 blocks thick, each bed ONE material, boundaries wavy by ±1–2, and the darker beds are set back 1 block (eaten-out) so the bed line is a real shadow.
- Stairs/slabs/walls: on the outer edge of ledges and the foot of buttresses (LotC: "cobblestone slabs and stairs on curved or ending edges"); stairs facing out on 20–30 % of a ledge's edge blocks, slabs on the rest of the broken edge; andesite/stone/cobblestone walls as 1-block "fins" standing on ledges. Tuff has no stairs/slabs in vanilla (polished tuff does), so use polished_tuff stairs/slabs for the dark edges.

## 4. Light and shadow with block value

- Engine: top ×1.0, N/S ×0.8, E/W ×0.6, bottom ×0.5, plus smooth-lighting AO that darkens any face touching an inside corner. Builders exploit this: an overhang 1 block deep is already a shadow line; a 2-deep groove goes very dark. They *add* palette value on top so the effect survives at distance and at noon.
- Sun side vs shade side: choose a sun direction (the camera direction players mostly look). Lit faces: palette L 126–189 (stone/andesite/diorite); shaded faces: 80–126 (tuff/deepslate/stone). Difference between the two should be ≥ 2 ladder steps (≈ 30–50 L) at the macro scale, applied as whole plates, not per block.
- Height gradient: darker at the foot (talus, damp, moss), lighter at the rim — Conquest: "darkest blocks at the bottom, midtones in the center, light at the top"; the gradient sites say the same for walls; kette442 clay-in-the-shadow is the same idea inverted (a different block where the shadow is).
- Ambient occlusion in creases: the inner block of every crease (re-entrant corner, groove floor, underside of a ledge) one step darker than its neighbours; a buttress' outer edge one step lighter (calcite/diorite) — Conquest: "a lighter edge around an arch helps define a window's shape… selective drip gradienting brings out crenellations and changes in depth. Clustered gradients don't work for this, because form depends on direct value or hue transitions."
- Numbers to implement: with the measured ladder, aim for lit plate mean ≈ 135–150, shaded plate ≈ 95–110, crease/underside ≈ 75–85, rim highlight ≈ 160–220 at ≤ 5 % coverage. Conquest's test: squint (or blur the screenshot to 1/8) — the big light/dark shapes must still read as the cliff's form; if the blur is uniform grey, the texturing is noise.

## 5. Amateur tells → fix
| tell | fix |
|---|---|
| grass/coarse-dirt/podzol random speckle on a sunny meadow | plain grass; brown only where motivated (tree base, path edge, bank, boulder upslope); add terraces, drifts, 1 boulder, 1 bush per area |
| even-spaced tufts/flowers, 5 flower species | tufts in noise drifts 8–15 % with empty ground; 1 species per drift, 1 accent colour |
| cliff of stone+cobble+andesite+gravel "gradient" (all L 126–136) | real value steps: diorite/calcite ↔ stone/andesite ↔ tuff ↔ deepslate, placed by form |
| horizontal stripes / banding | vertical/irregular plates; if strata, set darker beds back 1 block and make bed lines wavy |
| single-block confetti of 6 stone types | plates 6–20 wide of one material + 10–20 % of ONE neighbour-step block in 2–6 clusters |
| hard line between materials (grass→stone, dirt→stone, sand stripe at the water) | 1–3 block fingers, coarse dirt/gravel as the bridge, grass to the water on most of the edge |
| stone straight to deepslate / blackstone everywhere | tuff/cobbled deepslate between; blackstone only innermost crack block |
| moss everywhere | moss only in shade, grooves, under ledges, by water; "putting moss everywhere erases structure" |
| straight runs > 7 blocks on anything organic | wander every 3–7 blocks (BlueNerd) |
| uniform height steps (+3,+3,+3) and same outline on every layer | vary rises (+2,+3,+2,+4), each contour resembles but differs from the one below (kette442) |
| smoothed sides on a ridge (becomes a hill) | smooth only the top; faces stay steep; foot flares with grass (LotC) |
| full brown disc under trees, sand stripe along water, a flat stone cliff with grass stopping at a straight line | 1-block rooted-dirt ring; eroded lips in runs; grass in connected runs that thin where steep, dirt lip under the rim |

## 6. Landform (brief)
- kette442: terrain has primary (block-scale variation: no repeated step rhythm, contours that echo but differ), secondary ("flow" lines — drainage grooves, ridges that run through the face like river-building in rock; canal-like gullies) and tertiary (mountain-scale: rise gentle at the foot, steepest in the middle, flattening at the top; "smooth" vs "in-depth" styles; in-depth faces have many ledges/gullies and are hard to build on).
- Hills: 1-block terraces with wandering outlines, height change every 4–12 blocks on gentle ground, every 1–2 on steep; no outline longer than ~7 blocks straight; a hill's each layer "roughly follows the shape of the layer below" but not exactly.
- Cliffs: built as steep stepped faces (2–4 block risers, 1–3 block treads), overhangs cut into the underside (shadow), buttresses that bulge, gullies cut back; foot flares into talus and grass so the face never meets the ground at 90° (LotC). Dovaxios/Terra Celestis faces: tall vertical ribs with horizontal grooves at 3–8 block spacing; rim capped with grass that overhangs by a block in places.
- Valleys/water: WorldPainter-scale noise — large (≈130-block) features with 16-block edge wobble; rivers bend with eroded outer banks and gravel inner bars; a lake rim mostly grass with a few eroded lips.

## 7. Direct implications for zonegen (terrainmat.py / cliffs.py)
- `MEADOW = grass only` is right; keep it. Add motivated coarse-dirt patches only via objects (tree base ring, boulder upslope, path shoulders, bank lips), and terrace the heightmap so dirt sides appear.
- `HILLS` (grass 6 / stone 2 / andesite 1) → prefer grass with stone outcrops as 3–7 block plates on the convex noses, not a 3-way mix.
- `ROCKY` mix of stone 65 / andesite 10 / cobble / powder / gravel: all L 126–154 → no value. Replace by role-driven plates: lit = stone/andesite/diorite, shaded = stone/tuff, crease = deepslate family.
- `cliffs.PAL` (deepslate, cobbled_deepslate, tuff, cobblestone, andesite, stone, andesite, stone, stone): the upper half is flat (127/136/126). Make the ladder deepslate 80 → cobbled_deepslate 78 → tuff 108 → stone 126 → andesite 136 → diorite 189 → calcite 224 (last two ≤ 15 % / 5 %), drop cobblestone from the face (keep for talus), and make the form term dominate the noise term (plates of 12–24 blocks, noise nudge ≤ 1 step in 2–6 clusters).
- Add stairs/slabs (stone, andesite, cobblestone, polished tuff) on 20–30 % of ledge edges; moss only in grooves/under ledges; calcite/diorite only on rims and buttress edges.

## Sources
- kette442 (DurinsBuilders), Someone's Guide to Terraforming: 1 Variation https://www.planetminecraft.com/blog/someones-guide-to-terraforming-1-basic-techniques/ · 5 Block Template https://www.planetminecraft.com/blog/someones-guide-to-terraforming-5-building-block-template/ · 6 Detailing https://www.planetminecraft.com/blog/someones-guide-to-terraforming-6-building-detailing/ · 8 River https://www.planetminecraft.com/blog/someones-guide-to-terraforming-8-acvanced-techniques-river/ · 9 Mountain https://www.planetminecraft.com/blog/someones-guide-to-terraforming-9-macro-structures-mountain/
- Conquest Reforged, Gradienting your Builds https://www.conquestreforged.com/guides/gradients
- Lord of the Craft, Building guide: WE terraforming ridges https://www.lordofthecraft.net/forums/topic/155828-building-guidewe-terraforming-ridges/
- minecraft.net (Kate Gray / BlueNerd), Tips for landscaping and terraforming https://www.minecraft.net/en-us/article/tutorial--tips-for-landscaping-and-terraforming
- 4netplayers, paths/roads tutorial https://www.4netplayers.com/en-au/blog/minecraft/minecraft-ways-build-paths-roads-tutorial/ · terraforming guide https://www.4netplayers.com/en-au/blog/minecraft/minecraft-terraforming-guide/
- ManaCube, General building tips https://forums.manacube.com/threads/general-building-tips-and-tricks.18429/ · WE terraforming for beginners https://forums.manacube.com/threads/terraforming-with-worldedit-for-beginners.18061/
- Cephyr-Casey, Hand Terraforming (stone wall 68/21/11 mix) https://www.planetminecraft.com/blog/hand-terraforming-a-guide-to-success/
- blockblend: terrain gradients https://blockblend.app/guides/minecraft-terrain-gradients · stone gradient https://blockblend.app/guides/stone-gradient-guide · deepslate gradient https://blockblend.app/guides/deepslate-gradient-guide
- minecraftgradient.blog: deepslate→stone https://minecraftgradient.blog/deepslate-to-stone-gradient/ · wall gradients https://minecraftgradient.blog/minecraft-wall-gradient/ · sand→grass https://minecraftgradient.blog/sand-to-grass-gradient/
- craftdex stone palette guide https://craftdex.net/articles/stone-variant-palette-guide
- WorldPainter source (Terrain.java, Constants.java: blob sizes TINY 4.1 / SMALL 16.4 / MEDIUM 32.8 / LARGE 65.5 / HUGE 131 / GIGANTIC 524; GRASS, STONE_MIX, BEACHES, ROCK definitions) https://github.com/Captain-Chaos/WorldPainter · terrain types https://www.worldpainter.net/trac/wiki/Scripting/TerrainTypeValues
- Screenshots viewed: Wynncraft wiki Nivla Woods https://wynncraft.wiki.gg/wiki/Nivla_Woods · Llevigar Plains https://wynncraft.wiki.gg/wiki/Llevigar_Plains · Maltic Plains https://wynncraft.wiki.gg/wiki/Maltic_Plains ; PMC Dovaxios https://www.planetminecraft.com/project/dovaxios/ · Terra Celestis https://www.planetminecraft.com/project/terra-celestis-5120-x-5120-multibiome/ · Nordia https://www.planetminecraft.com/project/nordia-rivenflow-mountain-valley-w-dense-forest-amp-creeks-1000x1000/
- Block luminance: measured from `~/.minecraft/versions/26.3/26.3.jar` block textures (Rec.709 luma, plains tint 0x91BD59 on grass/plants); script in the session scratchpad (`lum.py`).
