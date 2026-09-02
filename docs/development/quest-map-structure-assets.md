# Quest Map Structure Asset Knowledge

This note defines how ProjectS accumulates reusable terrain-decoration knowledge without quietly importing incompatible or unlicensed Minecraft builds.

## Current production catalog

`QuestMapStructureAssets` is the single placement boundary for reusable quest-map structures.

- Production runtime does not select downloaded schematics.
- Verdant uses broad veteran trees; Highlands uses layered conifers; Clifflands uses asymmetric windswept pines; Saltmarsh uses stilt-root mangroves; Sakura uses split-crown cherry trees; Infernal uses warped or crimson wind-shaped forms.
- Trees are assembled from grounded roots, structural trunks, connected branch lines, multiple offset crowns, and ecology-specific litter. Tree footprints span at least three scales per ecology.
- Rock masses follow local terrain per column, combine several overlapping lobes, and use style-aware strata. Scenic rocks are grouped into stained outcrops instead of placed as identical loose props.
- Placement is rejected when the required footprint is too steep, close to a road, submerged, or inside content clearance.
- Fallen logs are reusable grounded structures rather than one-off decoration code.
- Clifflands no longer uses procedural leaf shrubs as rock-scene filler. Other ecologies retain low shrub clusters only as supporting detail.
- Road guidance uses only low cairns, grounded rest benches, half-buried milestones, and collapsed road-edge fragments. Freestanding posts and unexplained arches are forbidden.
- Camps, combat landmarks, gathering cuts, discoveries, and the boss arena each have a readable purpose, broad silhouette, route-aligned framing, and a clear playable center.
- Grove-weighted placement creates forests and clearings instead of uniform random noise.
- Thirty-four mutually separated scenic anchors compose trees, grounded rock outcrops, fallen wood, shrubs, and ground transitions as middle-distance regions; each composition is independently rotated and mirrored, while ordinary scatter maintains clearance around it.

The placement code remains behind one boundary, so terrain planning does not know whether a reviewed asset is file-backed or authored in Kotlin.

## External asset intake contract

An external tree, rock, ruin, or landmark is not accepted until the following metadata is recorded:

1. Original author and canonical source URL.
2. Explicit license and whether modification and redistribution are permitted.
3. Minecraft data version used to save it.
4. Bounds, origin, ground anchor, allowed rotations, and optional mirror support.
5. Collision and road-clearance radius.
6. Allowed terrain styles and palette substitutions.
7. Maximum block count and measured placement cost.
8. A rendered review image and at least one in-game smoke seed.

Assets with missing redistribution terms are reference material only and must not be copied into ProjectS. Production sources are limited to CC0, CC-BY, MIT, or explicit written author permission covering modification and redistribution.

## Archived migration input

The previous MIT-licensed WorldPainter collection remains attributed and decodable only so old seeds and migration tools do not fail. It is not part of production runtime selection. Source: https://github.com/sijmenvb/worldpainter-trees

## File-backed implementation

`QuestMapStructureAssets` directly authors production block structures after the quest Instance chunks are ready. `SpongeSchematicAsset` is retained only as an archive/migration reader.

Relevant primary documentation:

- Minestom generation and cross-unit placement: https://minestom.net/docs/world/generation
- Minestom 26.2 `GenerationUnit` API: https://javadoc.minestom.net/net.minestom.server/net/minestom/server/instance/generator/GenerationUnit.html
- WorldEdit clipboard and Sponge schematic behavior: https://worldedit.enginehub.org/en/latest/usage/clipboard/

## Performance rule

Quest entry consumes a fully prepared Instance from the ready pool. The current preparation budget is ten seconds per 448×448 map; command-to-transfer P95 remains below one second because it must consume a ready Instance. Planning time, chunk generation time, decoration time, and transfer time should be logged separately before production merge.
