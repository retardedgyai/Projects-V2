# First Magic v0 — original art direction

The first workshop is an **old observatory that has become a practical workbench**. Its visual rule is dark wood for structure, stone for the yard, brass for measuring and joining, paper for knowledge, and clear glass for stored results. A small amount of cold light identifies active magic. The four aspects use ember orange, tide turquoise, gale pale blue, and stone pearl.

## References and interpretation

- [Thaumcraft by Azanor](https://www.curseforge.com/minecraft/mc-mods/thaumcraft): extracting Essentia from ordinary things, physical research tools, tubes and jars. The workshop takes that tactile research loop, not its existing textures or meshes.
- [Botania Lexica](https://botaniamod.net/lexicon.html): a single clear apparatus can teach an entire new system; a visible resource should be legible in the world. This informed the four distinct Jar contents and the small furnace to receiving tube chain.
- [Ars Nouveau Source Jar](https://dev.arsnouveau.wiki/category/source/entry/source_jar): stored magical material is a visible object in the world, with the neighboring machine as its consumer.
- [Monumenta](https://www.playmonumenta.com/): a server can treat custom items and authored models as a coherent part of Minecraft rather than relying on vanilla stand-ins. This is a broad presentation reference, not an asset source.

The image generated for exploration is in `assets/first-magic/observatory-workshop-concept.png`. It is the composition, material and atmosphere target. The first simple geometry pass did not match it: furniture read as generic cubes, the side panels were flat, and abstract aspect rings overwhelmed the material iconography. The second pass uses original pictorial sprite masters in `assets/first-magic/source-sprites/`, detailed authored cuboid models, dedicated engraved textures and a new wood/cloth/brass GUI. No art was copied from another mod or server.

The icon masters were created with the built-in ImageGen tool, using the workshop concept as a style reference. The shared prompt called for a **single original Minecraft inventory item**, transparent true-alpha background, hand-pixelled clustered squares, a strong dark silhouette and readability at 16 px. The per-icon subjects were: physical ember flame; turquoise tide drop; pale wind ribbon; broken pearl geode; Moonbell flower; ember moss; hollow sea-glass crystal; warm veined ore; Tidewing feather; withered root core; open codex and brass astrolabe; copper boiler with connected receiver; labeled glass Jar; open star-map journal; lantern at the return threshold; and carved sealed-door fragment. The master is cropped and reduced to 32 px by `scripts/build_first_magic_assets.py`, and the exact shipped size is reviewed in `assets/first-magic/icon-sheet.png` and `workshop-screen-preview.png`.

`scripts/first_magic_art_v2.py` builds the models and GUI. The desk has a full codex, detailed observation dial, small armillary, drawers, quill and lantern. The distiller has separate furnace, shouldered copper boiler, connected pipe and glass receiver. The shelf carries books and an instrument box; its four Jars are separate saved-state models. The dial, pages and chart use their own illustrated face textures. `scripts/preview_first_magic_models.py` maps actual textures onto visible faces and composites four Jar models on the shelf for offline review, but final scale, orientation and transparency must be judged in Minecraft.

## Shipped set

- 3D research desk, with a dormant and an active observation disk.
- 3D crude distiller with copper body, furnace window, transparent retort, tube and receiver.
- 3D shelf and four individually rendered jars, each with empty, low and high fill stages. These models are driven by saved amounts.
- 3D wall star chart as a restrained promise of larger magic.
- 16 individually authored first-magic inventory icons: four aspects, six anomalous materials, desk, distiller, jar, journal, return and sealed area.
- Dedicated 384 × 222 pixel menu frame with an oak chassis, inlaid navy cloth, brass edges, engraved side dials and page-specific apparatus illustrations. It uses the already accepted Japanese menu font and native slot layout; the four workshop screens have their own labels and guidance.

The world models are displayed only for players who accepted the optional pack. Vanilla blocks remain as fallback interaction targets when the pack is unavailable. The custom items are namespaced `projects:first_magic/*`; no vanilla item texture or default font is replaced.

## Visual check at game size

Inspect the four aspects and six material icons at 16 px in the inventory, not only enlarged. In the colony, verify that the desk book and brass disk read from the entry path, the distiller's furnace is visually separate from the glass receiver, and all four Jar contents are identifiable without a floating label. Check the title and side-panel text against the new frame for collisions. The first visit, first analysis, first extraction, and a reload with stored Essentia are the four required states. The Creator performs the final manual gameplay and feel check under `AGENTS.md`.
