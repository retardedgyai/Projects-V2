# 魔術書 (grimoire) screen

The magic path's quest book, in the FTB Quests / Thaumonomicon role: opened to check what to do next,
not a place to play. It uses the same camera display as the forge and the 開拓大陸 map, so the layout is
free (no chest slot grid), the mouse moves the pointer, left click selects and Shift closes.

Open it in the harbor with `/grimoire`. It needs the ProjectS UI pack.

## What it shows

It follows the forge and the map: a header with the title, the branch tabs in the middle and the
wallet pills on the right, rounded panels, M PLUS 2 text and the key hints bottom right. The magic is
in the colour: a night sky with soft cyan clouds, ring medallions for entries (gold done, cyan workable
and breathing, violet side entries, grey locked, `?` not visible yet), a red `!` for an unclaimed reward
and a cyan dot for the pinned entry. The right panel shows the selected entry: illustration, text,
goals, unlocks, reward, and the claim or pin button. Branch tabs show their sealed state.

Quest progress is sample data for now. Completion will come from the world (FTB Quests style), so the
panel only has a `試し：ワールドで達成させる` shortcut for trying the unlock animation.

## Art and rebuild

Only item icons and illustrations are pixel art (`assets/ui/grimoire/source`), as in the forge. Panels,
rings and glows reuse the forge's shared shapes (`v3_round_*`, `v3_ring*`, `v3_glow` from
`polish05/font-map.json`). Fills are drawn 5 depth steps behind glyphs by UiRenderer, so the flow lifts
every fill by that much to keep one depth per layer. Bitmap glyph advances stop at the last non-clear
column, so the sprite builder keeps one alpha-1 pixel in a clear right column.

```powershell
python scripts/build_grimoire_assets.py          # icons -> polish05/grimoire.zip + grimoire.json
python scripts/build_forge_v3_assets.py fonts --source <dir with MPLUS2.ttf and DotGothic16-Regular.ttf>
python scripts/build_forge_v3_assets.py pack     # glyph pages now include GrimoireFlow.kt's text
```

When you add text to `GrimoireFlow.kt`, rerun the two font commands; `GrimoireFlowTest` fails on any
missing glyph.
