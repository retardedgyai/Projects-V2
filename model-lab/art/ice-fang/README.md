# Reference art provenance

These are existing ProjectS original AI-authored paintings, reused without raster edits.
They are not textures extracted from MatE, Wynncraft, Scorpius or other creators.

The creator supplied `codex-clipboard-c7d51aa2-095b-4687-a1bb-a5db9f102cab.gif` as
the minimum quality reference. Its SHA-256 matches the old ProjectS
`.tools/rime-painted-final-motion.gif` exactly:
`99a6244157b23dc8d46755e7a6552cd246b43730521cff562647c3a1f3ef17bb`.

The corresponding source geometry is `scripts/build_mage_rime.py` at commit
`59d6d2e5`, preserved as the pure-geometry module `scripts/reference_rime_geometry.py`.
Source images came from `assets/combat-vfx/mage-v5/sources/` in that project:

| File | SHA-256 |
|---|---|
| rime-faces-v02.png | 456549430da2bdaf9bd48ae2898533b3e7e8b31268099ebb47648945c7accd83 |
| rime-branches-v01.png | d21fcade73f0b75bfcaecb0403df8e619d5e6afb53a3730b4338af368f44a1e2 |

The branch painting has a baked checkerboard background, **not an alpha channel**.
The historical geometry selects blue interior cells and follows the painted silhouette;
do not use the entire sheet as a billboard. UVs, facet rotations and original pixel bytes
are retained by the lab converter and checked by `scripts/test_ice_fang.py`.

This establishes provenance and geometric equivalence, not a claim of client-rendered
visual parity. The original was a radial nova; this skill is a forward three-stage chain.
