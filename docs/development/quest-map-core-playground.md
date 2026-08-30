# Quest Map Core Playground

Branch: `play/gyai/quest-map-core`

This playground tests one concrete replacement for the current flat hunt space. It does not reuse the Tidebreak experiment and does not introduce a generic quest engine.

## Player-facing slice

- `/questmap` enters one prewarmed, party-sized generated map.
- `/questmap return` returns to the existing ProjectS hub and destroys that temporary instance.
- `/questmap status` displays ready, preparing, and active map counts.
- The generated extent is 160×160 blocks so it aligns to 10×10 Minecraft chunks.
- A raised outer terrain rim and an invisible final edge prevent accidental escape.
- The main road always connects the camp to the boss arena.
- The current authored rhythm is three combat landmarks, four gathering branches, three discoveries, and one boss arena.
- Seed and terrain style are shown on entry so bad generations can be reproduced.

## Generation pipeline

1. Select one of Verdant, Highlands, or Saltmarsh from the seed.
2. Generate layered deterministic terrain height.
3. Build a long, monotonic west-to-east quest route through alternating terrain bands.
4. Add side trails for gathering and discovery content.
5. Blend terrain toward the road and force every road step to remain walkable.
6. Flatten the start camp and boss arena without breaking the road ramp.
7. Reject maps that fail reachability, density, spacing, elevation, or content-count rules.
8. Load all 100 chunks, add trees and authored landmarks, and place the result in the ready pool.

## No-wait rule

Two maps are fully generated before the server begins accepting players. Entering a quest consumes a ready map and starts a background replacement. The command never performs terrain generation synchronously. If the pool is unexpectedly empty, entry is rejected instead of freezing the player.

Automated planning budget: 20 complete plans in under four seconds on the development host. Runtime chunk preparation is reported per map in milliseconds and must be measured again on the production host.

## Automated acceptance

- Same seed produces the same map fingerprint.
- Different seeds alter the fingerprint.
- 120 representative seeds pass the complete quality gate.
- Every accepted road reaches the boss with no step higher than one block.
- Terrain elevation range is at least eight blocks.
- The route contains no content gap greater than 75 road steps.
- Required content counts and minimum separation are enforced.
- JVM 25 production sources compile with the current Minestom dependency.

## Creator manual smoke

1. Start the ProjectS server and connect normally.
2. Run `/questmap status`; at least two maps must be ready.
3. Run `/questmap` and record the reported transfer time, seed, and style.
4. Follow only the tan road. It must lead from the camp through three stone combat rings to the Lodestone boss arena without jumping or building.
5. Inspect every side trail. Four end at ore clusters and two end at stone/amethyst arches; the final main-road arch foreshadows the boss.
6. Verify that trees do not block the road or content landmarks.
7. Verify that the raised outer terrain hides the square edge during normal play and the invisible boundary cannot be crossed.
8. Run `/questmap return`; the player must return to the hub and the active count must decrease.
9. Repeat at least 20 entries. The command-to-transfer P95 target is below one second.

## Explicitly not complete in this playground checkpoint

- Combat activation and boss behavior.
- Gathering interaction and rewards.
- Party ownership beyond one player per temporary map.
- Quest-item affixes, persistence, Terrain Bank, Tectonic/Terralith import, and Polar storage.
- Production Issue, Sol Review, Creator feel verdict, and main merge.

Those features should be added only after the generated field itself passes the Creator smoke. If walking the road is visually weak or repetitive, the terrain approach should be replaced before combat and reward systems are attached.
