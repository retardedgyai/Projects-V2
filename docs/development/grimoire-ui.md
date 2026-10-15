# 魔術書 (grimoire) screen

The magic path's quest book, in the FTB Quests / Thaumonomicon role: opened to check what to do next,
not a place to play. It uses the same camera display as the forge and the 開拓大陸 map, so the layout is
free (no chest slot grid), the mouse moves the pointer, left click selects and Shift closes.

Open it in the harbor with `/grimoire`. It needs the ProjectS UI pack.

## What it shows

- **Tabs** (left): the trunk and the six specialist branches. Branch tabs show their own night-sky
  colour and a wax seal; the branches open after the trunk's goal (帰還の巻物).
- **Quest map** (middle): entries on a nebula, joined by dotted light. Gold is done, cyan pulses when it
  can be worked on, violet is a side entry, grey is locked, `?` is not visible yet. A red `!` marks a
  reward that has not been claimed; a blue pin marks the pinned entry.
- **Page** (right): the selected entry on pinned parchment — illustration, text, goals, what it unlocks,
  the reward, and the actions (claim the reward, pin it).

Quest progress is sample data for now. Completion will come from the world (FTB Quests style), so the
page only has a `試し：ワールドで達成させる` shortcut for trying the ink-spread unlock.

## Art and rebuild

The art is the approved v7 "magic window" set in `assets/ui/grimoire/source`, drawn for a 1280×720
frame. `GrimoireFlow` places it at 1.5× in the 1920×1080 design space so every sprite shares one texel
size. Translucent pixels are snapped to opaque or clear because a translucent glyph hides what is drawn
after it.

```powershell
python scripts/build_grimoire_assets.py          # sprites -> polish05/grimoire.zip + grimoire.json
python scripts/build_forge_v3_assets.py fonts --source <dir with MPLUS2.ttf and DotGothic16-Regular.ttf>
python scripts/build_forge_v3_assets.py pack     # glyph pages now include GrimoireFlow.kt's text
```

Text uses the DotGothic16 pages (`v3num*`), the font the v7 art was drawn against. When you add text to
`GrimoireFlow.kt`, rerun the two font commands; `GrimoireFlowTest` fails on any missing glyph.
