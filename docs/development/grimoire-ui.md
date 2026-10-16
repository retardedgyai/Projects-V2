# 魔術書 (grimoire) screen

The magic path's quest book, in the FTB Quests / Thaumonomicon role: opened to check what to do next,
not a place to play. It uses the same camera display as the forge and the 開拓大陸 map, so the layout is
free (no chest slot grid), the mouse moves the pointer, left click selects and Shift closes.

Open it in the harbor with `/grimoire`. It needs the ProjectS UI pack.

## What it shows

It follows the forge and the map: a header with the title, the branch tabs in the middle and the
wallet pills (ink, silver) on the right, rounded panels, M PLUS 2 text and the key hints bottom right. The magic is
in the colour: a starry night sky, ring medallions for entries (gold done, cyan workable
and breathing, violet side entries, grey locked, `?` not visible yet), a red `!` for an unclaimed reward
and a cyan dot for the pinned entry. The right panel shows the selected entry: illustration, text,
goals, unlocks, reward, and the claim or pin button. Branch tabs show their sealed state.

Hovering a medallion shows its name, state and what is missing (`「相を溜める」を終えると開く`); locked
entries are named on the map too, only `?` entries stay unnamed. Goals each get a check, counted goals a
number and a bar. Claiming a reward outlines the slot in gold, hops five coins (or ink bottles) to their wallet pill
and counts the number up (about 1.5 s); it uses no glow sprites, which render as flat blobs in the client. The trunk count leaves out side entries; chapters are written 序章 / 1章 / … / 終章.

## Content and progress

- `GrimoireBook` — the content: entries, goals, rewards, branches; chapter headings come from the entries.
- `GrimoireProgress` — one player's progress: finished entries, claims, pin, goal counts, wallet. Ink is
  kept per aspect, as the research economy keeps it (`FirstMagicState.researchInk`); the pill shows the total.
- `GrimoireSample` — `midway()` is the state the screen was designed with and what a player first sees;
  `start()` is the very beginning.

`CoreLoopServer` keeps progress per player in memory, so closing the book keeps it; nothing is saved yet.
`/grimoire reset` starts the book from the very beginning. Completion will come from the world (FTB Quests
style), so the panel only has a `試し：ワールドで達成させる` shortcut for trying the unlock animation.

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

To look at the screen without a client, run `GrimoireSceneGoldenTest` and render one of its scenes:

```powershell
python scripts/render_grimoire_preview.py . server-minestom/build/grimoire-golden/0.json grimoire.png
```

The test pins one hash per scene of a fixed click script; after a deliberate visual change copy
`build/grimoire-golden/hashes.txt` into its `GOLDEN` list.

When you add text to `GrimoireFlow.kt`, rerun the two font commands; `GrimoireFlowTest` fails on any
missing glyph.

In the client the display's fills and sprites are not drawn back to front, so anything translucent can be
drawn before what is behind it and show the world's sky. Fills are therefore pre-blended over the colour
under them and drawn opaque (`GrimoireFlow.solid`). The client also draws every glyph texel above its 0.1
alpha cutoff fully opaque: soft glow sprites become flat discs (the book uses none) and anti-aliased small
text thickens. The book therefore has its own crisp text pages (`gmv3m15` …, alpha snapped at 0.5), built
by `build_grimoire_assets.py`; `render_grimoire_preview.py` draws text and sprites the same way.
