# Combat VFX source art

World-space skill artwork, **not** the approved UI skill icons. Those icons and
Japanese fonts are untouched.

## slash-luminance-atlas.png

- Original artwork generated using Codex's built-in `image_gen` tool, 2026-09-08.
- Source: `exec-56553b4d-ce62-4ea8-8ec9-9c4f43038491.png` in the thread's generated-image output.
- Tool mode: image generation, followed by image edit to neutralize the warm palette.
- No Crimson Moon, Nightfall, Monumenta or Wynncraft bitmap is included.
- The returned master is **RGB, 1254 × 1254**, not transparent RGBA. It is consumed
  as a luminance/emission plate. Do not describe the source as a transparent asset.

### Prompt brief used for generation and edit

1. Production VFX spritesheet, not an interface icon: a 4 × 4 atlas of sixteen
   sequential frames of a warm gold/white energy crescent. Top-down view, forward
   at the image top, concave edge toward the bottom. Frames 1–3 leading tip;
   4–7 broad curved trail; 8–11 broken wake; 12–16 separated dissipating fragments.
   Transparent background requested. No lettering, frame or UI ornament.
2. Preserve the sixteen-frame layout and shape sequence; neutral white/silver
   luminance instead of warm colours, for class palette tinting in Minecraft.

This is the production brief, not a claim that the image generator met every
instruction: the returned background was black, so the exporter derives alpha.

### Reproduction

`scripts/build_core_combat_models.py` divides the source by fractional quarter
bounds, resamples cells to 128 × 128, converts luminance to alpha, and emits
sixteen reusable textures. Vanilla item constant tints supply eleven palettes.
Mirrored return strokes use separate UV models, sharing the same textures.
Native meshes also get seven progressive cuboid-removal stages.

The exporter is deterministic given this committed master. AI regeneration is
not required for a normal build. Source and resulting pack assets are versioned;
QA GIFs and temporary reference screenshots under `.tools` are not.
