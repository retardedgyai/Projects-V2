# Meteor impact atlas v01 — original generation / native use

Status: **FIX-FIRST**, not final visual approval. Built-in imagegen was used (no fallback CLI).

## Saved source

- `assets/combat-vfx/mage-v5/sources/meteor-impact-atlas-v01.png`
- Selected generated output: `exec-24fa0a91-4048-409a-b0ae-575b7986ff6f.png`.
- Original is copied unchanged to `textures/combat_vfx/mage_material/meteor_impact_atlas_v01.png`.
- Actual output is **1774×887 RGB**, not the requested 1024×512 RGBA.
- Both generation and a transparency edit returned a painted checkerboard. This is NOT a valid transparent sprite sheet, and is never mapped onto an opaque full rectangle.
- No raster cleanup/resizing/repainting was performed. Native geometry samples warm foreground colours from the unchanged source at logical 48×48 and 32×32 grids. No surface is created for backdrop positions. Colours are clustered for the existing element budget.
- Original first five drawings establish contact, rise, arch and detached flame pieces. The last three source drawings are not used at runtime: their mass dropped too abruptly. Instead, the connected pieces of drawing 5 move upward/outward, shrink and cool into native charcoal/ash. Grey image pixels are never interpreted as smoke.
- These are two unequal crossed shallow surfaces, not a full volumetric fluid. Moving-angle seams and in-game lighting remain unverified.

## Source reference roles

- R12 Wynncraft Meteor: `https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s`, observed 155.70–155.84s and dissipation through 156.70s. Broad rising flame arch, holes and unequal detached masses.
- R09 MatE Solar Scepter: `https://mcmodels.net/products/16393/mates-mythic-weapons-solar-scepter`. Broad cream/peach/orange value hierarchy. Not a source for rocks or perfect spirals.
- Browser screenshots were reference inputs only. No author artwork was copied into the resource pack.

## Exact generation prompt

Use case: stylized-concept. Asset type: original Minecraft resource-pack VFX animation atlas, not a presentation. Generate exactly eight consecutive hand-drawn frames of ONE meteor ground-impact flame burst in a perfectly regular 4-column by 2-row sheet. Transparent RGBA background, no painted checkerboard, no text or grid lines. Canvas 1024 by 512, each cell 256 by 256; render each frame with a coarse consistent 64-by-64 logical pixel grid (4px square clusters), hard nearest-neighbour edges, no antialiasing or gradients. Every cell has the same bottom-centre anchor and camera: orthographic frontal view, baseline at y=224 inside its cell, enough transparent padding. Image 1 is a motion/silhouette reference only: the Wynncraft contact at 155.70 and raised breaking fire at 155.84, NOT the UI, player, terrain or text. Image 2 is a palette/shape-hierarchy reference only: broad pale yellow core, peach/orange slabs and a few thin highlights; NOT the staff, typography, geometrical jewels or logo. Draw original flames, never paste screenshots. Animation from left to right, then second row: 1 short forceful ivory impact pushing two unequal wide wings sideways; 2 those wings expand upward into a broad peach/orange rising front; 3 peak tall arch of irregular rounded flame lobes with a large real transparent opening underneath and several smaller holes within flames; 4 the SAME arch rolls upward and breaks into uneven orange flame clumps and hollow lips; 5 larger gaps, distinct pieces separating along the preceding outward/upward trajectories; 6 dark burnt-orange edges and a few pale slate-grey smoke curls with ember remnants; 7 sparse smaller smoke and charcoal fragments moving along those trajectories; 8 only a few dying sparse fragments, mostly transparent. Keep corresponding masses spatially coherent between frames. Broad solid orange bodies with select pale edges, not uniform outlined thin ribbons. Asymmetry in height, density and size. Bold readable pixel clusters, irregular flame silhouettes, purposeful negative spaces. No identical mirrored ram horns, no ornamental scrollwork, no perfect rings, no hot rocks, no faceted crystals, no separate floating fireballs, no thin spaghetti, no soft blur/bloom/photographic lighting, no black outlines everywhere, no terrain, no meteor rock, no whole falling tail in this atlas. This is the impact burst only. Match the coarse pixel contour and broad colour masses of the references.

## Exact transparency edit prompt

Use case: background-extraction. Edit target: the supplied original 4x2 meteor impact animation atlas. Remove the entire grey checkerboard backdrop, including every checkerboard pixel inside the holes of flames and between fragments. Deliver a genuinely transparent RGBA PNG with actual alpha=0 there. Do NOT draw a transparency checkerboard or any solid backdrop. Keep all eight flame frames, their precise positions, scale, art, colours, edge pixels and 4x2 layout unchanged; do not add objects, shadows, labels or extra frames. The alpha channel itself must carry transparency; this is a production sprite atlas, not a visualization of an atlas.

## Remaining art issue

The transition from drawing 2 to drawing 3 still changes the identities of the largest flame masses too abruptly. A 20 Hz clip and resource-pack validity do not prove motion quality. This atlas is not the final Mage style approval.
