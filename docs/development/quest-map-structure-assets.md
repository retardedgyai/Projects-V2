# Quest Map Structure Asset Knowledge

This note defines how ProjectS accumulates reusable terrain-decoration knowledge without quietly importing incompatible or unlicensed Minecraft builds.

## Current ProjectS-owned catalog

`QuestMapStructureAssets` is the single placement boundary for reusable quest-map structures.

- Verdant, Highlands, and Saltmarsh each have six tree silhouettes.
- Every tree can be rotated in four directions.
- Tree silhouettes include asymmetric crowns, split or leaning trunks, roots, and style-specific proportions.
- Six boulder silhouettes combine style-specific stone palettes with four rotations.
- Fallen logs are reusable structures rather than one-off decoration code.
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

Quest entry consumes a fully prepared Instance from the ready pool. Larger maps and richer assets are allowed only while two Instances can still be prepared in the background and the command-to-transfer P95 remains below one second. Planning time, chunk generation time, decoration time, and transfer time should be logged separately before production merge.
