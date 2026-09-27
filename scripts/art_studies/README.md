# ProjectS armor art studies

## Current direction: plate first, 2026-09-28

The creator stopped the Elven reproduction study and requested three visual
families: plate, cloth, and leather. Only the first plate set is developed here.
This is an art direction change, not a gameplay/stat or class-lock change.

```powershell
python scripts/art_studies/plate_guard_study.py --reference-pack <path-to-isles.zip>
```

The refined plate candidate is saved to `.tools/armor-review/plate-guard-polished`.
The positively received initial preview is preserved locally in both
`plate-guard` and `plate-guard-approved`, and its source is commit `e29ea5b1`.
The refinement keeps its palette, chest painting, limb shapes, and material
language. Pass `--compare-to .tools/armor-review/plate-guard-approved` to produce
`plate-before-after.png` from both versions' actual models and textures.

Outputs:

- `plate-overview.png`: assembled art mockup, front/rear and small display,
  plus four independent item models.
- `plate-angles.png`: front obliques, side, and back for silhouette review.
- `painted-volume.png`: the same model with and without face-light multiplication.
  This checks whether painted shading conveys volume without renderer lighting.
- `plate-reference-comparison.png`: optional read-only local reference comparison.
- `plate_guard.png`: atlas; individual face islands are roughly one pixel per
  model unit. The 128-square sheet holds separate surfaces, not high-density art.
- `plate_guard_{helmet,chestplate,leggings,boots}.json`: native cuboid item sources.
- `assembled-preview.json`: assembled visual study, not an equipment asset.

See [PLATE_REVIEW.md](PLATE_REVIEW.md) for decisions, revisions, and limitations.
No pack generation, game startup, gameplay changes, or replacement of the live
armor is performed by either art study. Generated reference images stay ignored.

### UI integration

The creator subsequently requested UI implementation. `../plate_armor_ui.py`
now imports this exact plate geometry/painting into `../build_class_armor_assets.py`.
The main asset build ships the warrior T1–T4 GUI icons and workshop FIXED models.
The native worn models/layers remain on their original fallback route.
Run `python scripts/preview_plate_ui.py` to inspect the shipped assets and their
actual item-context selection. See `docs/development/plate-armor-ui.md`.

## Earlier forge helm study — paused

Preview-only original ProjectS model and manually authored pixel texture. These
scripts do not update the server resource pack or the equipped armor.

The target is the readable, low-resolution modeling and material treatment in
the user-supplied Isles Elven reference. The comparison command reads the local
reference pack without modifying it. Reference geometry and pixels are not
included in the repository or used in the ProjectS export.

```powershell
python scripts/art_studies/preview_forge_helm_study.py --reference-pack <path-to-isles.zip>
```

The default output directory is `.tools/armor-review/forge-helm-reviewed`:

- `comparison.png`: same camera and rendering for the reference and study,
  including a directly rendered small item view.
- `angles.png`: left, right, side, and rear inspection of the study.
- `forge-helm-model.json`: native cuboid item model.
- `forge-helm-atlas.png`: 64×64 RGBA texture with hard pixel boundaries.

## Review and revision, 2026-09-27

The rejected prototype hid the face behind a thick brow, used narrow disconnected
pieces for its central ornament, and gave its wings broad brown faces. More color
adjustments did not fix those structural problems.

This study rebuilds the assembly around a visible visor and cheek guards. The
brow is two units high and has a transparent center on its upper/lower surfaces,
so it wraps the shell instead of covering the crown. Wing roots reach the brow
and use mirrored front/back UVs. A contiguous kite-shaped gold frame contains a
blue inlay and one red gem; its end pieces overlap rather than hang below it.

The main shell and brow texture spans follow their physical dimensions. Broad
steel planes use three values, gold faces use a bright upper edge and copper
front, and red shadows sit under the brow. Highlights connect to edges rather
than appearing as isolated squares. A second review removed a rectangular blue
patch on the rear and reduced competing highlights inside the crest.

The native export checks element bounds, UV bounds, names and legal cuboid
rotations. The reference and original model were inspected at front, both oblique
angles, side, rear, and small display size. This remains an art candidate:
resource-pack integration, equipped fit, and creator visual acceptance are still
required before replacing the game assets.
