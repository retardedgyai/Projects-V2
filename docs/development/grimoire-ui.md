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

The trunk map's glow is therefore drawn in opaque pixel steps: `build_grimoire_assets.py` generates a dithered
nebula (`trunk_nebula_*`, one texel = 4 px, 4 colour steps plus the map fill left clear) and stepped halos
(`halo_gold` / `cyan` / `violet` / `red`, three 2-texel rings, the outer one a checker). GrimoireFlow draws them on
layers 2 and 3 above the map fill and lifts the rest of the trunk map by 3 (`lift`), so nothing shares their depth.
Halos follow the medallion state: done gold, workable cyan, side violet, the locked trunk goal red.

## 再開メモ（2026-10-17）

- 藍の承認済み: 見本①〜⑧（docs/proposals/grimoire-ui-2026-10/mock-1-approved.png）、受け取り演出A（claim-abc-chosen-A.png）、
  Rank表示なし、文字は魔術書だけくっきり化（text-crisp-before-after.png）。503c48ed まで藍は実機で未確認。
- 地図の章の見せ方は B（章の境に細い縦線＋章名の下線）に決定（2026-10-17、map-chapters-abc-linux.png）。
  切替と一時ダンプは削除し、`GOLDEN` も B で更新済み。共通の直し（整数倍ドット・「この先」・封印の枝の?地図）も入っている。
- 描画は Linux では `python3`、比較用ラベルの日本語フォントは Noto Sans CJK（`/usr/share/fonts/opentype/noto/`）。
- 空気感は②「星雲＋後光」に決定・実装（2026-10-17、見本 https://claude.ai/artifact/Ub7KNcn4aZG2Hd43HamvUx ）。
- 細い字（2026-10-17、実機で確認）: 14px 以下は1段太く（Regular→Medium、Medium→Bold、`GrimoireFlow.page`）。
  くっきり文字のページは Linux で作り直した（Windows 版は小さい字で「イ」「し」が欠けていた）。以後は Linux で作る。
- 欠けの本当の原因: サイズ N の文字ページはセル ceil(N×1.25) テクセルを 40 単位で宣言するので、N のまま描くと
  13px なら 17 テクセルを 16.25px に縮めて描き、行か列が抜ける。`GrimoireFlow.drawn` で ceil(N×1.25)/1.25 の大きさで
  描き、1080p で1テクセル＝1画素にした（a460f2ab、実機で確認）。フォージなど共通の v3* ページを使う画面も同じ問題を持つはず（未対応）。
- 残り: 503c48ed 以降を藍が実機で確認すること。
- テスト: Java 25、`:server-minestom:test --tests '*Grimoire*'`（maxHeap 1g・ワーカー1）。サーバーは `-Dprojects.port=25567` で別立てしていた。
- 絵の確認は `scripts/render_grimoire_preview.py`（実機の文字・スプライトの描き方を再現）。実機SSはクライアントの screenshots/ に出る。
