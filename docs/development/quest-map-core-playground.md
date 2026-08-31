# Quest Map Core Playground

Branch: `play/gyai/quest-map-core`

This playground tests one concrete replacement for the current flat hunt space. It does not reuse the Tidebreak experiment and does not introduce a generic quest engine.

## Player-facing slice

- `/questmap` enters one prewarmed, party-sized generated map.
- `/questmap return` returns to the existing ProjectS hub and destroys that temporary instance.
- `/questmap status` displays ready, preparing, and active map counts.
- `/questmap seed <long>` prepares and enters one exact seed for manual regression checks.
- The generated extent is 224×224 blocks so it aligns to 14×14 Minecraft chunks.
- A low, irregular coastline and a full client-view-distance outer sea hide the square edge; an invisible final edge prevents escape.
- The main road always connects the camp to the boss arena, but its topology is selected from Meander, Ridge Pass, Horseshoe, or Diagonal and then rotated or reflected.
- Terrain independently selects Rolling, Ridged, Terraced, Basin, or Broken Hills, so route and landform silhouettes do not collapse into one combination.
- Surface ecology is derived from water distance, slope, elevation, moisture, and canopy fields rather than one style-wide top block.
- Main roads are broad and readable while optional side trails are deliberately narrower and use different materials.
- The current authored rhythm is three combat landmarks, four gathering branches, three discoveries, and one boss arena.
- Seed and terrain style are shown on entry so bad generations can be reproduced.

## Generation pipeline

1. Select one of Verdant, Highlands, Saltmarsh, Clifflands, Sakura Grove, or Infernal from the seed. Each style owns biome rendering, terrain shape, surface, vegetation, liquid, road, and scene palettes.
2. Generate layered deterministic terrain height with one of five macro terrain profiles.
3. Select one of four route topologies, rotate or reflect it, and curve it into a walkable route.
4. Add side trails for gathering and discovery content.
5. Reserve a broad exploration corridor around the road before decoration, reconcile nearby hairpins, and force every road step to remain walkable.
6. Blend the start, encounters, gathering pockets, discoveries, and boss arena into their local landforms without breaking the road ramp.
7. For Saltmarsh, carve warped wetland basins with variable depth, irregular shorelines, inlet fingers, and relaxed banks while keeping the entire main road above water.
8. Classify every surface cell as Meadow, Forest Floor, Shore, Rocky, Heath, or Peat and apply contiguous block palettes.
9. Reject maps that fail reachability, density, spacing, elevation, sightline, road-grade, shoulder-relief, explorable-corridor, ecology, coastline, water, or content-count rules.
10. Load the 196 playable chunks plus the server's complete client-view-distance buffer (900 chunks at distance eight), verify every requested chunk exists, add ecology-weighted reviewed schematics and route-aligned authored scenes, then place the result in the ready pool. A player is never transferred to partial coverage.

## No-wait rule

Two maps are fully generated before the server begins accepting players. Entering a quest consumes a ready map and starts a background replacement. The command never performs terrain generation synchronously. If the pool is unexpectedly empty, entry is rejected instead of freezing the player.

Automated planning budget: 20 complete plans in under four seconds on the development host. The Creator-approved runtime preparation budget is now five seconds per 224×224 map; transfer still consumes a ready map and should not wait for generation.

## Automated acceptance

- Same seed produces the same map fingerprint.
- Different seeds alter the fingerprint.
- 120 representative seeds pass the complete quality gate.
- Every accepted road reaches the boss with no step higher than one block.
- Terrain elevation range is 16–48 blocks.
- At least three direct sightline samples must hide the boss landmark from the start.
- The walked route must be 1.06–1.58 times the direct start-to-boss distance: readable, but neither straight nor an artificial giant S-curve.
- A twelve-step road window may rise at most five blocks, and terrain within six blocks of the road may differ by at most four blocks.
- At least 82% of the dry 22-block quest corridor must be reachable from the road with one-block steps.
- The route contains no content gap greater than 115 road steps.
- All four route topologies occur in the representative seed suite.
- All five macro terrain profiles occur in the representative seed suite.
- All six terrain concepts occur in the representative seed suite and exact seeds `0` through `5` expose one of each.
- The prewarm range covers every map chunk plus the full configured view distance on all sides.
- Every accepted plan contains at least four ground-cover ecologies.
- Required content counts and minimum separation are enforced.
- Saltmarsh water coverage is bounded and its main road cannot be submerged.
- Interior Saltmarsh shorelines cannot jump more than four blocks from water bed to adjacent land.
- The failed manual-smoke seed `1788101320652` is a permanent regression case for flooding and perimeter walls.
- The failed Highlands seed `1788109725769` is a permanent regression case for mountainside roads, extreme relief, and poor exploration access.
- JVM 25 production sources compile with the current Minestom dependency.

## Creator manual smoke

1. Start the ProjectS server and connect normally.
2. Run `/questmap status`; at least two maps must be ready.
3. Run `/questmap` and record the reported transfer time, seed, and style.
   To reproduce the failed Saltmarsh screenshot exactly, use `/questmap seed 1788101320652` instead.
   To reproduce the failed mountainside-road screenshot, use `/questmap seed 1788109725769`.
4. Confirm that no Rift Executioner boss bar or Hub combat telegraph remains after transfer.
5. Follow only the broad main road. It must lead from the field-work camp through three distinct, playable combat clearings to the Lodestone boss arena without jumping or building.
6. Inspect every narrow side trail. Gathering sites must read as exposed cuts/outcrops rather than loose ore piles; discoveries must read as a spring, collapsed shrine, or rooted stone seat rather than unexplained pillars.
7. Verify that vegetation does not block the road or content landmarks.
8. From the actual spawn height, verify that terrain and vegetation hide the boss arena and most later encounters.
9. Compare at least one seed from every reported route layout; the route must not merely be the same west-to-east S-curve.
   Compact comparison seeds are `/questmap seed 0` (Ridge Pass), `1` (Meander), `2` (Diagonal), and `3` (Horseshoe).
   The same seeds `0` through `5` also expose Verdant, Highlands, Saltmarsh, Clifflands, Sakura Grove, and Infernal respectively.
10. Verify that the coastline and outer sea hide the square edge during normal play and the invisible boundary cannot be crossed.
11. Run `/questmap return`; the player must return to the hub and the active count must decrease.
12. Repeat at least 20 entries. The command-to-transfer P95 target is below one second.
13. Reject the seed if a tree or rock floats, a roadside cue reads as a random structure, the boss shrine is visible from spawn, or a scene lacks a clear entrance, focal point, and playable floor.

## Explicitly not complete in this playground checkpoint

- Combat activation and boss behavior.
- Gathering interaction and rewards.
- Party ownership beyond one player per temporary map.
- Quest-item affixes, persistence, Terrain Bank, Tectonic/Terralith import, and Polar storage.
- Production Issue, Sol Review, Creator feel verdict, and main merge.

Those features should be added only after the generated field itself passes the Creator smoke. If walking the road is visually weak or repetitive, the terrain approach should be replaced before combat and reward systems are attached.
