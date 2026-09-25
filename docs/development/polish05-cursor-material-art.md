# Polish05 cursor and material art pass

Production task: [#142](https://github.com/retardedgyai/Projects-V2/issues/142).

## Visual diagnosis and references

The smith UI previously assigned imported browser-kit thumbnails by approximate color:
for example, an ingot used a cyan mineral cluster, processed stone used purple ore,
leather used a helmet, and cloth used a shard. Their silhouettes disagreed with
the material labels, and the 16–24 px assets had dense highlights that became
noise at the display's small material-row size. The core storage screen used a
different icon family for the same materials.

The creator's [X reference list](https://x.com/i/lists/2097375828187496504)
is recorded in [the earlier reference review](slash-reference-finish-2026-09-09.md).
The list and linked posts currently reject direct unauthenticated access, so
this pass used that review's documented observations about large light/dark
planes and a bright edge. The materials themselves come from ProjectS's own
`assets/core-ui/pixel-relic/source/materials.png`, which already has appropriate
silhouette, shading, and material pairs. The new `affix_dust.png` is an original
image-generated source in that family, with loose grains rather than a crystal.
The [official Minecraft texture discussion](https://www.minecraft.net/fr-fr/article/caves---cliffs--part-i--dev-q-a)
was used as additional pixel-art context. No external artist asset is in the pack.

Every shown material is now a 32×32 sprite with binary alpha and no runtime
downscaling source above 32 px. The pairings are wood/boards, ore/ingot,
stone/cut stone, hide/leather, and fiber/cloth. The production Polish05 panel,
core menu glyph atlas, and packed storage item models use the same sprite pixels.
The approved sword, composition, Glow, and sound assets remain as they were.
A contact sheet at `assets/core-ui/previews/forge-materials-v2.png` shows the
compiled 32 px sprites enlarged with nearest-neighbor pixels for inspection.

## Cursor diagnosis

The Vanilla client reports look input once per client tick while viewing the
camera display. Previously the pointer moved three TextDisplay entities by
instant position packets. On a 20 TPS production server, that produced visible
steps. It now updates their display transforms with one-tick interpolation;
hit testing and clicks continue to use the latest logical pointer position.
The lab smoke test verifies the direction and that cursor movement emits no
entity teleports.

## Rebuild and verification

Run from the repository root with Python/Pillow and JDK 25:

```powershell
python scripts/build_core_menu_art.py
python scripts/build_polish05_lab_pack.py
Copy-Item web-ui-lab/ui/polish05-font-map.json server-minestom/src/main/resources/polish05/font-map.json -Force
Copy-Item web-ui-lab/build/polish05/polish05-lab.zip server-minestom/src/main/resources/polish05/pack.zip -Force
python scripts/test_forge_material_pack.py
python scripts/verify_core_ui_assets.py
.\gradlew.bat :web-ui-lab:test :web-ui-lab:uiSmoke :web-ui-lab:polish05Smoke :server-minestom:test :server-minestom:installDist
```

For a fresh, separate server configuration, follow
[the smith UI smoke launch guide](polish05-smith-ui.md). The creator makes the
final call on cursor feel and the artwork in the real client before merge.
