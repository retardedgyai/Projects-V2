# Quest Map Structure Asset Knowledge

This note defines how ProjectS accumulates reusable terrain-decoration knowledge without quietly importing incompatible or unlicensed Minecraft builds.

## Current ProjectS-owned catalog

`QuestMapStructureAssets` is the single placement boundary for reusable quest-map structures.

- Verdant, Highlands, and Saltmarsh trees use a parametric grammar with fourteen profile tendencies.
- Height, lean, multiple stems, root form, branch count, branch direction, branch length, crown count, crown radius, leaf holes, dead growth, and four rotations are seed-driven.
- The parameter space contains well over one hundred thousand deterministic combinations; this is a diversity budget, not a claim that every combination is perceptually unique.
- Boulders combine eighteen tendencies with independent footprint, height, palette, erosion holes, spikes, moss, burial, and four rotations.
- Boulders are embedded per footprint cell and fallen logs follow the sampled ground line rather than using one origin height.
- Placement is rejected when the required footprint is too steep, close to a road, submerged, or inside content clearance.
- Fallen logs are reusable grounded structures rather than one-off decoration code.
- Grove-weighted placement creates forests and clearings instead of uniform random noise.

The catalog is authored in this repository and therefore has an unambiguous ownership and review path. Terrain planning does not know how an asset is stored; a future file-backed catalog can replace the Kotlin-authored voxels without changing route or terrain generation.

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

## File-backed direction

Sponge `.schem` is the preferred interchange format because modern WorldEdit uses it for current block-state data and preserves a relative origin. ProjectS still needs a small importer that converts the palette and relative block positions into the existing placement boundary; Minestom's generator should remain responsible for terrain, while structures are placed after the quest Instance chunks are ready.

Relevant primary documentation:

- Minestom generation and cross-unit placement: https://minestom.net/docs/world/generation
- Minestom 26.2 `GenerationUnit` API: https://javadoc.minestom.net/net.minestom.server/net/minestom/server/instance/generator/GenerationUnit.html
- WorldEdit clipboard and Sponge schematic behavior: https://worldedit.enginehub.org/en/latest/usage/clipboard/

## Performance rule

Quest entry consumes a fully prepared Instance from the ready pool. The current Creator-approved preparation budget is five seconds per map; command-to-transfer P95 remains below one second because it must consume a ready Instance. Planning time, chunk generation time, decoration time, and transfer time should be logged separately before production merge.
