# Scorpius import provenance

Source: user-supplied `scorpius-main.zip`, supplied as a friend's project and explicitly authorized for incorporation in this conversation on 2026-09-22.

Archive SHA-256: `60247b82d882bd9d80bf2abd90568103d92e47d02f9a9ec1a00155b37888b7c5`.

Original package/author identifier: `com.yuuki14202028`. Preserve these source credits when adapting or redistributing the imported work.

The supplied README describes the project as MIT licensed, but this import has not established a separate complete license grant for every asset. This notice records provenance and the user's incorporation request; it does not invent a license or claim ownership. Preserve upstream notices and confirm redistribution terms before an external asset release.

## Executable imported material

- `bbmodel/`: four boss generators, the original bbmodel preview renderer, and their 15 checked-in model assets. They are authoring inputs/fixtures, not a replacement for ProjectS's approved art direction.
- The original `GenerateWseeAssets.java` is retained at `model-lab/src/generator/java/com/yuuki14202028/generator/` and run during model-lab compilation.
- `reference/bbmodel/render_lore.py` is used through ProjectS's `scripts/preview_pack_components.py` adapter with ProjectS's pack and Components. The original HUD-specific renderer is retained for reference only.
  Local adaptation: font JSON reads use `Path.read_text` to close file handles immediately during batch verification.

## Reference imports (not on any server classpath)

`reference/` retains the original GUI/deposit lifecycle, WSEE following/pitch helpers, metrics, restart handling, map/architecture preview, encounter tests and protocol bots. These depend on Scorpius game state and must not be represented as already integrated with ProjectS economy/combat/persistence.

ProjectS Kotlin ports are explicitly limited to model generation/loading/playback, the laboratory menu, bounded tick metrics and opt-in laboratory load bots. Direct integration of the reference-only files is not enabled.

No accounts, saves, secrets, deployment configuration, agent instructions or client modifications have been imported.

WSEE is a separate dependency (`net.worldseed.multipart:WorldSeedEntityEngine:13.0.0`), upstream https://github.com/AtlasEngineCa/WorldSeedEntityEngine (Apache-2.0). It is not authored by Scorpius or ProjectS.
