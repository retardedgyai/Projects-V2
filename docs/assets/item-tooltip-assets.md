# Item Tooltip Assets v0

ProjectS equipment Tooltip用に、Minecraft 26.2の標準Tooltip Styleが参照する8枚のPNGを新規authoringした。

## Source / redistribution

- Source: OpenAI built-in ImageGenで2026-09-04にProjectS専用として生成。
- Authoring reference: repo既存の`projects_ui_preview.png`をpaletteとpixel densityの参考にのみ使用。
- External assets: 不使用。Last Travelerおよび他ゲームのassetは使用していない。
- Redistribution: 第三者配布asset由来ではなく、このrepo用の新規生成物。採用PNGにはProjectS repoの利用条件を適用する。
- Runtime generation: なし。実行時はresources内の確定PNGだけをMinecraftが読む。

## Adopted masters and processing

| Rarity | ImageGen output ID | Visual intent |
| --- | --- | --- |
| COMMON | `exec-5fd74e4c-09c2-4e47-a25d-70bc435fa870` | Vanilla寄りの一段stone edgeと最小corner notch |
| UNCOMMON | `exec-e5b0803d-3af0-4591-bcc9-83bbc9825a63` | clipped corner、teal inlay、ivory pin |
| RARE | `exec-fc6bde67-7cdc-40f3-bc8f-54a8747da14a` | double steel edge、cyan inner tick、corner bracket |
| EPIC | `exec-8f57f028-65f5-43b6-91b4-72d5bc4a37ca` | three-step edge、faceted amethyst corner、rune tick |

生成masterから左右のbackground/frameをcropし、nearest-neighborで100x100へ縮小した。frameは半透明の生成ノイズをalpha thresholdで除去し、backgroundは文字可読性のためtexture contrastを抑えた。枠やcorner ornamentそのものをprocedural描画していない。

最初に生成されたCOMMON候補`exec-4c98d238-d928-434a-badf-9ea237a0944f`は、透過checkerboardが画像へ焼き込まれていたため不採用。

## Minecraft resource paths

各style IDは`projects:item_<rarity>`。Minecraft 26.2はそこから次を解決する。

- `assets/projects/textures/gui/sprites/tooltip/item_<rarity>_background.png`
- `assets/projects/textures/gui/sprites/tooltip/item_<rarity>_frame.png`

backgroundはVanilla同様border 9。COMMON/UNCOMMON frameはborder 10、より大きいcorner detailを持つRARE/EPIC frameはborder 12の9-slice metadataを持つ。cornerは伸長領域から外し、中央は透過している。

## Review sheet

同じ装備情報を4種類の実assetで9-slice描画した比較画像:

`docs/assets/item-tooltip-rarity-preview.png`
