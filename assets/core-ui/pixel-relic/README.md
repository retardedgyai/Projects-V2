# Pixel Relic menu artwork

ProjectS original menu-only artwork, generated with the built-in image generation tool
on 2026-09-05. No Hypixel or Wynncraft image is included in this pack. The selected
direction is an ivory stone frame, flat blue-ink reading surfaces, restrained teal
selection marks, and Minecraft-friendly object silhouettes. These are game assets,
not the earlier high-resolution UI concept screenshots.

## Source / reproducibility

- `source/materials.png`: built-in generated 4x4 source sheet.
- `source/symbols.png`: built-in generated 4x4 source sheet.
- `atlas.json`: exact source SHA256, cell mapping and rendered advances.
- `prompts.md`: source art generation instructions.
- `../../../../scripts/build_core_menu_art.py`: deterministic resource-pack compiler.
- Actual player texture: `assets/projects/textures/gui/core/menu_art.png` (256x128).

The source sheets remain in the repository for provenance and later art iteration;
players receive only the small compiled 32px atlas. Compilation uses regular cell
slicing, nearest-neighbor sampling, binary alpha and at most 24 colors per sprite.
Raw/processed pairs deliberately have different silhouettes. The GUI renders 16px
inline icons or 32px feature icons and never changes a real item's model.

Source generation is not deterministic; compilation of the checked-in source is.
The generated weapon illustration is an equipment-category icon, not a live render
of arbitrary equipped gear. The existing in-world greatsword model is unchanged.

## Typography and menu geometry

The existing OFL Noto Sans JP source is rasterized at 20px, displayed at the same
10px logical text size, with binary alpha. This preserves Japanese inner spaces
without introducing soft antialiasing over the pixel UI. Widths are remeasured from
the actual bitmap using Vanilla's rounding formula. Global fonts are untouched.
The 384x222 canvas, 54 central action slots and 36 player slots are unchanged.
Frame/card bevels are native code-produced pixel geometry, not generated images.
