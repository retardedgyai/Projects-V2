# Plate family / original ProjectS art candidate

## Request, 2026-09-28

The creator's key observation is that Isles textures describe volume from a
distance, even on flat faces, and those painted forms meet modeled shapes
naturally. Mage cloth is a positive material reference. Stop reproducing the
Elven helmet; organize the art into plate, cloth and leather, starting with one
plate set. Do not batch the other families before this direction is established.

## Design proposal

The harbor-to-boss-hunt loop in `docs/00-product-vision.md` is the setting for a
practical expedition plate set, provisionally named 港の遠征鎧. Its silhouette is
heavy at the shoulders and protected at the knees and shins. Dark red padded
joints allow the moving parts to read separately. This visual role is a proposal;
no new defense values, equipment rules or class restrictions are introduced.

Four independent slots follow `docs/development/armor-four-slots.md`.

## Reference observations

Viewed the local Isles Fighter, Elite warrior and Elite mage item models with
their real textures under the same camera and face shading as the candidate.
The reference pack is read-only and no pixels or geometry are copied into the
ProjectS export.

- Broad coherent value clusters describe a rolled edge or a bulging plane.
- Under an overlapping plate, a dark band has a structural reason.
- Bright trim turns to warm brown on its underside.
- Cloth folds use long connected highlights interrupted by folds/contact,
  rather than shiny metal-like speckles.
- Most complexity lives in painted surfaces; geometry is reserved for large
  forms and supported trim. More cubes do not automatically create more quality.

## Self-review and revisions

1. The initial sallet face plate occluded the eye slit. Its top edge was lowered,
   then the slit and brow were raised together to reduce the excessive forehead.
   A recessed dark backing keeps the slit readable. This is an actual recess.
2. Round centered highlight patches repeated across shoulders, arms and knees,
   making every piece look like the same padded material. Shoulder highlights
   now follow a folded upper edge; vambraces and greaves have longitudinal
   highlights; the breastplate retains one broad convex surface.
3. The thigh plates hid the belt. Their upper edge was lowered so waist and
   separate legs can be read, and the knee overlap was reduced.
4. Small raised brass shoulder lips touch the shoulders and share their pose.
   The collar meets the cuirass. There is no floating chest badge or gem.
5. The rear thigh padding is painted with long red folds, with low brightness
   relative to steel highlights. Back plates use broad planes rather than
   isolated decorative squares.

Reviewed front, both obliques, side, rear, standalone pieces, small assembled
view, and a view with renderer face-light multiplication disabled. The latter
is a texture-readability check, not a physically accurate lighting simulation.

## Current assessment and limits

The creator responded positively to the initial plate set ("上手！") and then
requested the proposed continuation. Treat that version as the visual baseline;
the follow-up is a small finish pass, not a new design direction.

### Follow-up finish pass

- Preserved the initial actual model/atlas/preview at `plate-guard-approved`
  before making changes; committed source remains available at `e29ea5b1`.
- Inset the top three units of the helmet by 0.45 units on each side. Its UVs
  sample the corresponding original rows rather than stretching the whole
  helmet texture onto each of the two new shell sections.
- Tilted the lower face plate inward by 22.5 degrees around its upper edge,
  preserving the eye opening. Its upper surface is dark beneath the brow.
  Rebalanced the two breathing slots so they sit either side of the center.
- Added the collar's two side returns so the front band has a connected shape
  in standalone chest views. No chest ornament was added.
- Removed an obsolete gold strip on the thigh plate side which no longer
  matched the front after the original plate review. Replaced the stretched
  crown artwork on the neck guard top with a shallow plate-specific surface.
- Rechecked front, left/right obliques, side, back, small size and unlit painting.
  The before/after board uses exported model data under identical cameras.
- Compared exported surface islands by name: only `visor_front` and
  `tasset_side` changed among existing paintings; all other old surfaces and
  the palette are identical. Existing torso/arm/leg/boot geometry is identical;
  the collar returns are additions. Native cuboid, UV and rotation checks pass.

The follow-up is still a preview candidate; the creator has praised the baseline,
not yet evaluated these later changes.

The set reads as plate at small size, the visor opens, and materials have
different highlight structures. The result is a restrained original candidate.
It is not yet evidence of matching Isles' final quality: the helmet silhouette
is still simple and the broad breastplate could support a more distinctive
construction once the overall direction is accepted.

The assembled image is an art mockup made from the exported cubes, not a
Minecraft screenshot. Worn body layers, player fit, animated joint clearance,
equipment/item parity and in-game mipmapping remain unverified. The 3D shoulder
and body details in this preview do not claim to work with the current native
equipment layer pipeline. All four parts remain preview-only.

## Validation / reproducibility

The build checks rectangular pixel grids, atlas bounds, non-inverted native
item cuboids and UV limits; it writes four native item models and the actual
texture used for every preview. Fourteen opaque colors, hard alpha, no blur,
resampling, random noise, bloom, or inferred AI detail are used.

Run `python scripts/art_studies/plate_guard_study.py`. The optional
`--reference-pack` adds a same-camera comparison from the creator's local pack.
All output defaults to this D-drive worktree's ignored `.tools` directory.
