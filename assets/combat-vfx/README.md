# Combat VFX source art

World-space skill artwork, **not** the approved UI skill icons. Those icons and
Japanese fonts are untouched.

## Active: shot-wake-pixel-v1.png

レンジャー6射撃と霜矢の扇専用の16コマ。細い立体線を置換し、短い交差面を
重ねて手元から崩す。[原画、生成・修正プロンプト、使用範囲](shot-wake-pixel-v1.md)。

## Active: nebula-stream-pixel-v1.png / ice_growth_ice

星雲専用の流れるピクセル連番と、氷の庭の底支点・立体氷晶モデル。
[用途、生成プロンプト、処理と確認箇所](nebula-stream-pixel-v1.md)。

## Active: stellar-burst-pixel-v1.png

星術の着弾・確定命中専用の16枚ピクセル連番。
[用途、生成と修正の完全なプロンプト、再生成手順](stellar-burst-pixel-v1.md)。
星雲・防護・転移には流用しない。

## Active: slash-pixel-atlas-v1.png

- Original pixel-styled artwork generated with Codex built-in `image_gen`, text-to-image mode, 2026-09-08.
- Selected source: `exec-e67b97ab-d4a7-475d-805f-6967c314c42f.png` in this thread's generated-image output.
- RGB 1254 × 1254 black-background master, not transparent RGBA. No external game artwork is embedded.
- This replaces the smooth luminance atlas below after the Creator's pixel-art correction.
- Final pack: sixteen **64 × 64** frames, four non-black grayscale inks, binary alpha,
  nearest-neighbour sampling, at least four transparent pixels on every edge.
- Generated row spacing is not exactly uniform. `slash-pixel-atlas-v1.layout.json`
  records inspected source rectangles; do not revert to blind quarter cropping.

### Generation prompt

```text
Use case: stylized-concept. Production asset: a true low-resolution PIXEL ART slash-animation sprite sheet for Minecraft world-space combat effects. Exactly FOUR COLUMNS by FOUR ROWS of equal square cells, 16 chronological animation frames read left to right then top to bottom. Each frame should look authored on a 64x64 pixel grid, enlarged with nearest-neighbor: large crisp square pixels, deliberate staircase edges, chunky connected pixel clusters. Only FOUR flat grayscale ink values (white, light gray, medium gray, dark gray) on completely uniform pure BLACK (#000000). Hard pixel edges, no antialias, NO blur, NO bloom, NO smooth gradients, NO smoky photographic textures, NO wispy hairlines, NO glitter noise. Shape: one powerful crescent sword trail, viewed straight down, convex leading edge facing image top, hollow side facing bottom. A solid white cutting rim, a broad connected light-gray blade body, two dark trailing broken strips, a few chunky square sparks. Strong tapered left and right tips, asymmetrical leading tip on the right. Animation progression: row 1 draws the short blade from right toward left and widens it; row 2 reaches a strong sweeping crescent then starts breaking into substantial angular pieces; row 3 the arc breaks apart into separate pixel shards moving outward; row 4 those shards diminish, last cell almost empty. All sixteen images share a fixed coordinate origin and scale, not sixteen unrelated logo variants. Every cell has a wide empty black gutter at least 10 pixels on all sides at the logical 64px grid. Nothing touches cell boundaries. No surrounding scene, no weapon object, no person, no lettering, no cell numbers, no drawn grid, no frame, no checkerboard. This is an opaque RGB luminance-mask sprite atlas for game import, not a transparency preview, not a UI icon. The visible pixel blocks and limited flat palette are crucial.
```

### Reproduction

`scripts/build_core_combat_models.py` reads the committed master and explicit frame
rectangles, resamples with nearest-neighbour and quantizes to 0/64/128/192/255.
Black becomes transparent, other inks opaque. Border assertions reject clipped
tiles. Vanilla constant tints supply eleven palettes; reverse strokes share the
textures through mirrored UV models. No blur or procedural noise is added.

The master and shipped textures are versioned. `.tools` QA outputs are not.
Generation need not run during normal builds. This is a pixel-style checkpoint,
not Creator approval of all effects or proof of in-game appearance.

## Shadow smoke

The newer shadow-smoke source and its complete generation/edit prompts are documented
in [shadow-smoke-pixel-v1.md](shadow-smoke-pixel-v1.md). It is a separate 16-frame
pixel animation for Assassin departure and afterimage only; it is not a replacement
for slashes, poison or UI icons. Integration details: `docs/development/assassin-phrase-vfx.md`.

## Deprecated: slash-luminance-atlas.png

**Not consumed by the current exporter.** Retained as historical source, not a
style reference: the Creator rejected its smooth, wispy non-pixel rendering.

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

### Previous import (superseded)

The previous exporter divided the source by fractional quarter
bounds, resampled cells to 128 × 128, converted luminance to alpha, and emitted
sixteen reusable textures. Vanilla item constant tints supply eleven palettes.
Mirrored return strokes use separate UV models, sharing the same textures.
Native meshes also get seven progressive cuboid-removal stages.

Do not restore that smooth import path for the current pixel-art direction.
