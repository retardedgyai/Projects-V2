# Quest Map Structure Asset Knowledge

This note defines how ProjectS accumulates reusable terrain-decoration knowledge without quietly importing incompatible or unlicensed Minecraft builds.

## Current reviewed catalog

`QuestMapStructureAssets` is the single placement boundary for reusable quest-map structures.

- Trees now use 25 reviewed Sponge-v2 schematics grouped into lush oak, spruce, swamp, old-growth, and dead-tree ecologies.
- Rocks use 11 reviewed schematics. Four rotations and style-aware block palettes provide variety without deforming the source silhouettes.
- Every imported file is parsed and validated in tests; every selected palette and rotation must resolve to a Minecraft 26.2 block state.
- Tree anchors are calculated from the lowest trunk mass. Root/trunk columns are extended only where local ground would otherwise leave a visible gap.
- Rock anchors are buried by one block and any exposed lower mass is filled down to terrain, preventing floating boulders.
- Placement is rejected when the required footprint is too steep, close to a road, submerged, or inside content clearance.
- Fallen logs are reusable grounded structures rather than one-off decoration code.
- Shrub clusters combine stems, multiple low crowns, undergrowth, and ground litter as micro-scenes instead of isolated leaf cubes.
- Road guidance uses only low cairns, grounded rest benches, half-buried milestones, and collapsed road-edge fragments. Freestanding posts and unexplained arches are forbidden.
- Camps, combat landmarks, gathering cuts, discoveries, and the boss arena each have a readable purpose, broad silhouette, route-aligned framing, and a clear playable center.
- Grove-weighted placement creates forests and clearings instead of uniform random noise.

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

Assets with missing redistribution terms are reference material only and must not be copied into ProjectS.

## Reference study applied in this revision

The MIT-licensed WorldPainter tree collection provides the current tree and rock schematics. ProjectS preserves its upstream license beside the resources, records the canonical source, and adds its own deterministic grounding, rotation, palette, and placement rules. Source: https://github.com/sijmenvb/worldpainter-trees

## File-backed implementation

`SpongeSchematicAsset` reads Sponge `.schem` v2 palettes and varint block data directly. Minestom remains responsible for terrain; reviewed structures are placed after the quest Instance chunks are ready.

Relevant primary documentation:

- Minestom generation and cross-unit placement: https://minestom.net/docs/world/generation
- Minestom 26.2 `GenerationUnit` API: https://javadoc.minestom.net/net.minestom.server/net/minestom/server/instance/generator/GenerationUnit.html
- WorldEdit clipboard and Sponge schematic behavior: https://worldedit.enginehub.org/en/latest/usage/clipboard/

## Performance rule

Quest entry consumes a fully prepared Instance from the ready pool. The current Creator-approved preparation budget is five seconds per map; command-to-transfer P95 remains below one second because it must consume a ready Instance. Planning time, chunk generation time, decoration time, and transfer time should be logged separately before production merge.
