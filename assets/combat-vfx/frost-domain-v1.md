# 絶対零界 — frost domain v1

2026-09-09 / development branch checkpoint, **not Creator-approved art and not applied to the running game**.

## Direct reference and intended use

[MatE: Ice Storm VFX](https://x.com/MatE312001/status/2087161158096609503) was inspected through the logged-in browser, not inferred from a thumbnail. The visible floor swirl, raised angular ribbon and independent crystals informed these layers. Exact source timing/mesh counts were not measured and are not claimed to match. Research: `docs/research/ice-storm-direct-reference-2026-09-09.md`.

Only `mage_zero` / 絶対零界 PULSE changes: textured floor vortex, four raised open ribbon segments, four optional crossed snowflakes. Each authoritative pulse produces its own eight-tick phrase; the fifth has a sixteen-tick aftermath. No duplicate damage, radius change, cast/hold timing change, new gameplay timer or client modification. Preparation/contact, 霜の波紋, 氷の庭, HUD/fonts and all weapon models are preserved. The sword's separate red blade-ember experiment is not replaced by this work.

No opaque blue base, additive glow, smooth alpha, concrete voxel pile or full-height circular wall. The floor has an empty centre and under 40% painted pixels. Ribbon and flake geometry has independent orientation and motion. Standard item displays and native textured planes only; reduced detail hides the optional flakes. The five pulse envelopes peak at nine visible parts, below the existing owner limit of 48 (asynchronous spawn/cleanup may briefly retain more records).

## Source and exact generation prompt

Built-in `image_gen`, generate mode, one direct-reference browser screenshot (`num_last_images_to_include: 1`), no old ProjectS artwork supplied. Original saved at:

`C:/Users/xgaiz/.codex/generated_images/01a060a6-6e32-7e13-a4cd-2730ddc429e9/exec-d1414ce8-28d9-4f69-9e38-83675e4e1c27.png`

Committed unchanged as `frost-domain-source-v1.png`, SHA-256 `b951e927436532d1553cd52f0351204900ab1fa12f8a5a74217ffb68ac9626bd`.

```text
Use case: stylized-concept. Asset type: ONE top-down pixel-art frost vortex texture for a vanilla Minecraft world-space skill effect, not a UI icon. Input image 1 is a DIRECT reference screenshot of MatE's Ice Storm GIF; use only the pale cyan floor swirling bands as material and silhouette reference, ignore all website UI and character. Generate an original circular frost swirl seen exactly from above, orthographic, centered, filling 85% of square canvas with margins. Two broad angular tapered spiral arms and a broken outer rim, several intentional empty gaps and a clear open centre. Crisp chunky square-pixel clusters at an effective 96x96 grid; cyan/ice-blue shadow planes, pale icy cyan broad planes, small white edge accents, no subpixel noise. The swirls are airy cutout ribbons with at least 65% empty space, NOT a solid disk. No opaque blue base, no glow, blur, smooth gradients, snowflakes, text, characters, weapons, borders, UI, lighting effects, 3D cubes, or perspective. Genuine transparent alpha background; empty inside and outside the ribbons must be transparent. This is just the floor layer; raised ribbons and snowflakes will be separate geometry at runtime.
```

The output painted a gray checker instead of usable transparency. Authorized Python cleanup extracts cyan, retains alpha where present, reduces to a 96×96 logical grid and four deliberate cyan inks. A circular alpha boundary and four-pixel gutter prevent clipping past the radius. This processing is explicit; the original is not claimed to be a ready-to-ship pixel texture. Ribbon and snowflake are small deterministic native pixel shapes in `scripts/build_frost_domain.py`, extending this palette, not further image-generation outputs.

## Rebuild / inspection

- `python scripts/build_frost_domain.py` rebuilds ONLY the 24 frost models, 24 item definitions, 24 textures and pack index. The normal combat art builder also includes this leaf.
- `python -m unittest discover -s scripts -p test_frost_domain.py` checks binary alpha, palette/gutters, open silhouette, monotonic pixel erosion, reproducibility, native plane shape and index dependencies.
- `CoreFrostChoreographyTest` verifies lifetime, pulse budget, invalid/other-skill isolation and exports all five actual Kotlin pulse timelines to `.tools/frost-domain-frames.json`.
- `python scripts/preview_skill_choreography.py --ids mage_zero --timeline .tools/frost-domain-frames.json --prefix frost-domain --world-scale 19` creates model-derived QA, **not a Minecraft screenshot**. `--view eye` is approximate perspective, not a shader/occlusion/client certification.

Most important implementation: `CoreFrostChoreography.kt`; first debug the timeline and corresponding `combat_vfx/frost/*` resources if timing/placement looks wrong. Native loading and live-game feel are different checks; only the Creator may approve the latter.

## Checkpoint verification

- 119 Kotlin tests passed: CoreFrostChoreography (4), CoreSkillChoreography (32), CoreSkillEffect (8), CoreSkillSceneGeometry (7), CoreCombatMesh (5), CoreCompositeVfxReview (1), CorePlayerCombat (62). This is the targeted combat suite, not the whole repository suite.
- 3 Python export/material tests passed.
- Actual Vanilla 26.2 `CuboidModel` accepted all 24 exported models; deliberately invalid rotation-axis control was rejected.
- Model-derived full five-pulse timeline and four approximate eye-level snapshots inspected. No Minecraft process started, no gameplay input sent, no installed server JAR or live pack replaced.
- Existing `min(gameplay radius, scene reach)` visual cap is preserved (mage_zero scene reach 5.0). This ornamental swirl is not a replacement for authoritative hit range/telegraph data.
- Pixel source generation and gameplay compatibility are separate from subjective material approval. No claim of exact reference equivalence or completion of every weapon/skill.
