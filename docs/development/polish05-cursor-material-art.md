# Polish05 cursor and material art pass

Production task: [#142](https://github.com/retardedgyai/Projects-V2/issues/142).

## Visual diagnosis and references

The smith UI used small browser-kit thumbnails as substitutes for production
materials. An ingot looked like a cyan mineral cluster, processed stone like
purple ore, leather like a helmet, and cloth like a shard. A later attempt
used ProjectS's 32 px pixel-relic sheet but remained too detailed at the
material-row size. Direct Vanilla items were legible but lacked ProjectS's
identity. Both rejected concepts remain in Git history or the D drive archive.

The creator's [X reference list](https://x.com/i/lists/2097375828187496504)
is recorded in [the earlier reference review](slash-reference-finish-2026-09-09.md).
The list and linked posts currently reject direct unauthenticated access; do
not claim this pass directly inspected those posts. The first 16 px candidate
downsampled ProjectS's existing relic sheet. The user's screenshot confirmed
that pack loaded, but the result looked too close to the old sprites to register
as a redesign. The current candidate redraws all 11 material subjects in a
shared gold, umber, ash, and violet palette, using the approved HTML's material
art as style references. Raw and processed states have distinct silhouettes.
The generated high-resolution work files are archived on the D drive under
`material-art-v4-source-ai`; the rejected transparent 16×16 sprites remain
at [`forge-v4/source`](../../assets/core-ui/forge-v4/source). The current
32×32 visual candidate is at [`forge-v6/source`](../../assets/core-ui/forge-v6/source).
It keeps the smith glyphs at the existing 16 px display size while providing
more texture detail at source resolution. For v6, the Creator selected the
open raw-hide variant A and the flat cut-leather variant from two visual
comparisons. This pair still needs the Creator's judgment in Minecraft.
The same exact 11
sprites feed the smith, storage item models, and core menu glyphs. The approved
sword, layout, glow, and sound stay as they were. A nearest-neighbor
[preview](../../assets/core-ui/forge-v6/preview.png) shows each icon at
inspection size. Live account values are unchanged.

## Cursor diagnosis

The Vanilla client reports look input once per client tick while viewing the
camera display. The original pointer jumped via three entity teleports.
Interpolating all three display parts looked smoother but felt delayed in the
real client. The bright cursor tip now follows the latest input immediately;
only its subdued shadow interpolates one tick. Click hit testing always uses
the latest logical pointer position. The lab smoke test checks direction,
immediate input handling, one interpolated shadow, and no entity teleports.

## Font coverage

The first production screenshot revealed missing glyphs in live item names:
`ゴ`, `板`, and `粉` were absent from the subset font. The generator now scans
the production account, smith flow, and enhancement catalog strings in
addition to the approved UI source. `test_polish05_font_coverage.py` checks
that both sans and serif glyph pages contain every required character.

## Rebuild and verification

Run from the repository root with Python/Pillow/fonttools and JDK 25:

```powershell
$env:JAVA_HOME='D:\Documents\Codex\minecraft-runtime\temurin-25\jdk-25.0.4.1+1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
python scripts/build_polish05_fonts.py
python scripts/build_core_menu_art.py
python scripts/build_polish05_lab_pack.py
Copy-Item web-ui-lab/ui/polish05-font-map.json server-minestom/src/main/resources/polish05/font-map.json -Force
Copy-Item web-ui-lab/build/polish05/polish05-lab.zip server-minestom/src/main/resources/polish05/pack.zip -Force
python scripts/test_polish05_font_coverage.py
python scripts/test_forge_material_pack.py
python scripts/verify_core_ui_assets.py
.\gradlew.bat :web-ui-lab:test :web-ui-lab:uiSmoke :web-ui-lab:polish05Smoke :server-minestom:test :server-minestom:installDist
```

For a fresh, separate server configuration, follow
[the smith UI smoke launch guide](polish05-smith-ui.md). The creator makes the
final call on cursor feel and the artwork in the real client before merge.
