# Quest Map Terrain Language

This document records the terrain principles used for ProjectS quest fields. It is a design translation from public world-generation references, not a copy of their data packs or assets.

## Reference lessons

- Tectonic treats mountain ranges, valleys, wetlands, canyons, dunes, plateaus, and underground rivers as distinct terrain shapes. ProjectS therefore chooses a macro terrain profile separately from biome palette and quest route. Source: https://modrinth.com/datapack/tectonic
- Terralith combines many biome identities with structures and features instead of relying on height alone. ProjectS therefore separates macro landform, surface ecology, vegetation grammar, and content landmarks. Source: https://www.stardustlabs.net/datapacks
- ReTerraForged continues TerraForged's terrain-first approach and provides an MIT-licensed architectural reference. ProjectS adopts the conceptual separation of terrain fields, but does not import its implementation. Source: https://github.com/racoonman2/ReTerraForged
- William Wythers' Overhauled Overworld focuses on biome and sub-biome differentiation with vanilla materials. Its CC-BY-NC-ND license means ProjectS may study public presentation but must not copy or modify its assets. Source: https://modrinth.com/mod/wwoo
- Shotbow's Cherokee description establishes a useful vertical gameplay grammar: a base above a valley, resources on multiple elevation layers, and a plateau broken by smaller hills. ProjectS adopts that relationship for Clifflands without copying Cherokee's map or culturally specific decoration. Source: https://wiki.shotbow.net/Special%3AMyLanguage/Cherokee

## ProjectS generation axes

One seed selects independent axes so visible variety is multiplicative rather than a long flat list of presets.

1. Quest route: Meander, Ridge Pass, Horseshoe, or Diagonal, followed by rotation or reflection.
2. Macro terrain: Rolling, Ridged, Terraced, Basin, or Broken Hills.
3. Palette style: Verdant, Highlands, Saltmarsh, Clifflands, Sakura Grove, or Infernal.
4. Surface ecology: Meadow, Forest Floor, Shore, Rocky, Heath, or Peat.
5. Structure grammar: tree, boulder, fallen log, undergrowth, water-edge, and authored quest landmark parameters.

## Route-first exploration contract

The quest route is not paint applied to a finished heightmap. ProjectS reserves the playable corridor first and then constrains nearby terrain around it.

- Route templates use moderate turns and independent rotation/reflection rather than one forced S-curve.
- Consecutive road steps and spatially adjacent hairpins are reconciled together.
- The inner road shoulder is protected after all landform and sightline passes, so a late mountain cannot cut back through the path.
- A broad corridor is relaxed outward from roads and side trails; accepted maps must prove that players can leave the road and explore most nearby dry terrain.
- Boss occlusion prefers an off-road landform. If no such placement exists, it uses a broad pass with road clearance rather than a narrow wall.
- Total relief is capped after authored landforms so a rare overlap cannot create an exploration-hostile spike.

This follows the general constraint-driven PCG lesson that designer intent must be encoded as explicit constraints rather than inferred from attractive noise alone. Reference: https://ojs.aaai.org/index.php/AIIDE/article/view/12592

## Water language

A pond is not a filled ellipse. Each Saltmarsh water system uses:

- rotated basins with different aspect ratios;
- two noise scales that warp the shoreline;
- multiple depth shelves;
- shallow banks blended into the source terrain;
- narrow inlet or overflow fingers;
- bank relaxation that caps abrupt local height changes;
- connected expansion when a seed produces too little water;
- Shore and Peat ecology bands for material and decoration transitions.

Infernal uses the same authored-edge rule for lava, but replaces wetland plants with basalt shelves, blackstone, magma, roots, fungi, and fire. It never places overworld lily pads or ferns around lava.

## Concept languages

- Clifflands: elevated shelves and valley views, restrained terracing, stone/calcite outcrops, spruce and deadwood, and readable passes. Dramatic relief stays outside the protected exploration corridor.
- Sakura Grove: gentler hollows, cherry-grove biome color, cherry timber, mossy stone, pink petals, dense mixed crowns, and small purposeful roadside shelters. “Japanese” is expressed through composition and material discipline, not pasted ornamental stereotypes.
- Infernal: fractured ridges, lava lowlands, basalt and blackstone faces, nylium ecology, crimson/warped vegetation, soul-lit landmarks, and blackstone quest architecture. It is a complete scene grammar rather than an overworld heightmap with red blocks.

## Surface language

The top block is selected from contiguous low-frequency patches constrained by ecology. Local variation is secondary and cannot turn the terrain into random confetti.

- Meadow: grass, moss pockets, rooted soil, and rare coarse clearings.
- Forest Floor: podzol, rooted dirt, moss mats, mushrooms, ferns, and fallen wood.
- Shore: mud, clay, sand, gravel, roots, reeds, and lily pads according to water depth.
- Rocky: stone, andesite, tuff, cobble, exposed faces, embedded boulders, and sparse scrub.
- Heath: podzol, coarse soil, grass, moss, berry scrub, and dead growth.
- Peat: mud, packed mud, moss, rooted soil, mangrove roots, and wet undergrowth.

## Acceptance meaning

Automated gates can reject repetition, broken reachability, extreme or flat relief, short-span road grades, abrupt shoulders, inaccessible exploration corridors, missing sight occlusion, insufficient water, ecological monotony, and floating-prone footprints. They cannot certify beauty. The Creator manual smoke remains the final visual verdict, and rejected screenshots become permanent regression seeds.
