# Quest Map Core Playground

Branch: `play/gyai/quest-map-core`

This playground tests one concrete replacement for the current flat hunt space. It does not reuse the Tidebreak experiment and does not introduce a generic quest engine.

## Player-facing slice

- `/questmap` enters one prewarmed, party-sized generated map.
- `/questmap return` returns to the existing ProjectS hub and destroys that temporary instance.
- `/questmap status` displays ready, preparing, and active map counts.
- `/questmap seed <long>` prepares and enters one exact seed for manual regression checks.
- The generated extent is 224×224 blocks so it aligns to 14×14 Minecraft chunks.
- A low, irregular coastline and one-chunk outer sea hide the square edge; an invisible final edge prevents escape.
- The main road always connects the camp to the boss arena, but its topology is selected from Meander, Ridge Pass, Horseshoe, or Diagonal and then rotated or reflected.
- Main roads are broad and readable while optional side trails are deliberately narrower and use different materials.
- The current authored rhythm is three combat landmarks, four gathering branches, three discoveries, and one boss arena.
- Seed and terrain style are shown on entry so bad generations can be reproduced.

## Generation pipeline

1. Select one of Verdant, Highlands, or Saltmarsh from the seed.
2. Generate layered deterministic terrain height.
3. Select one of four route topologies, rotate or reflect it, and curve it into a walkable route.
4. Add side trails for gathering and discovery content.
5. Blend terrain toward the road and force every road step to remain walkable.
6. Blend the start, encounters, gathering pockets, discoveries, and boss arena into their local landforms without breaking the road ramp.
7. For Saltmarsh, carve bounded wetland basins while keeping the entire main road above water.
8. Reject maps that fail reachability, density, spacing, elevation, coastline, water, or content-count rules.
9. Load the 196 playable chunks plus a one-chunk sea buffer, add grove-weighted vegetation, ProjectS structure assets, and authored landmarks, then place the result in the ready pool.

## No-wait rule

Two maps are fully generated before the server begins accepting players. Entering a quest consumes a ready map and starts a background replacement. The command never performs terrain generation synchronously. If the pool is unexpectedly empty, entry is rejected instead of freezing the player.

Automated planning budget: 20 complete plans in under four seconds on the development host. Runtime chunk preparation is reported per map in milliseconds and must be measured again on the production host.

## Automated acceptance

- Same seed produces the same map fingerprint.
- Different seeds alter the fingerprint.
- 120 representative seeds pass the complete quality gate.
- Every accepted road reaches the boss with no step higher than one block.
- Terrain elevation range is at least eighteen blocks.
- At least three direct sightline samples must hide the boss landmark from the start.
- The walked route must be at least 1.18 times the direct start-to-boss distance.
- The route contains no content gap greater than 115 road steps.
- All four route topologies occur in the representative seed suite.
- Required content counts and minimum separation are enforced.
- Saltmarsh water coverage is bounded and its main road cannot be submerged.
- The failed manual-smoke seed `1788101320652` is a permanent regression case for flooding and perimeter walls.
- JVM 25 production sources compile with the current Minestom dependency.

## Creator manual smoke

1. Start the ProjectS server and connect normally.
2. Run `/questmap status`; at least two maps must be ready.
3. Run `/questmap` and record the reported transfer time, seed, and style.
   To reproduce the failed Saltmarsh screenshot exactly, use `/questmap seed 1788101320652` instead.
4. Confirm that no Rift Executioner boss bar or Hub combat telegraph remains after transfer.
5. Follow only the broad main road. It must lead from the camp through three stone combat rings to the Lodestone boss arena without jumping or building.
6. Inspect every narrow side trail. Four end at ore clusters and two end at stone/amethyst arches; the final main-road arch foreshadows the boss.
7. Verify that vegetation does not block the road or content landmarks.
8. From the actual spawn height, verify that terrain and vegetation hide the boss arena and most later encounters.
9. Compare at least one seed from every reported route layout; the route must not merely be the same west-to-east S-curve.
   Compact comparison seeds are `/questmap seed 0` (Ridge Pass), `1` (Meander), `2` (Diagonal), and `3` (Horseshoe).
10. Verify that the coastline and outer sea hide the square edge during normal play and the invisible boundary cannot be crossed.
11. Run `/questmap return`; the player must return to the hub and the active count must decrease.
12. Repeat at least 20 entries. The command-to-transfer P95 target is below one second.

## Explicitly not complete in this playground checkpoint

- Combat activation and boss behavior.
- Gathering interaction and rewards.
- Party ownership beyond one player per temporary map.
- Quest-item affixes, persistence, Terrain Bank, Tectonic/Terralith import, and Polar storage.
- Production Issue, Sol Review, Creator feel verdict, and main merge.

Those features should be added only after the generated field itself passes the Creator smoke. If walking the road is visually weak or repetitive, the terrain approach should be replaced before combat and reward systems are attached.
