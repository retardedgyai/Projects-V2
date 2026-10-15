# 戦闘 HUD（ドット絵版） — Vanilla 26.2

サーバーから送る `CoreHudState` と、任意適用のリソースパックだけで表示する。クライアント MOD・protocol・
Minecraft の標準フォントは変更しない。パック非適用時は従来どおり日本語テキスト HUD（action bar）に戻る。

## 規則

- **1テクセル＝1GUIピクセル。** HUD に出す絵・数字・文字はすべて最終サイズで描き、拡大縮小した素材を混ぜない。
  `scripts/verify_core_ui_assets.py` が「シートのセル高＝provider の height」を全 HUD フォントで検査する。
- 方向性は「近未来MMO × マイクラ」。暗いガンメタルの板、金の細い縁、ティールの差し色、4px 刻みのセル模様のゲージ。
- 文字は MaruMinya 12px をネイティブの格子のまま（1px の暗い縁取り付き）。小さい数字は 3×5、CD 秒数とダメージは 5×7。

## 配置（GUI 座標。C=画面中央、H=GUI の高さ）

| 部品 | X | 上端 | 大きさ |
|---|---|---|---|
| 案内欄（戦闘の合図・通知） | 中央寄せ | H−113 | 高さ16の暗い帯＋12px文字 |
| 技能カード×5 | C−94 / −56 / −18 / 20 / 58 | H−96 | 36×36（原画32pxを等倍） |
| バフ（右から左へ最大4つ） | C−110 から 14px 間隔 | H−79 | 12×12＋下に秒数 |
| バニラの選択アイテム名 | 中央 | H−59 | 空けておく |
| 状態の板 | C−91 | H−47 | 182×24（ホットバーと同じ幅、ホットバーの真上） |

板の中身：左に HP（76px、1px 単位）、中央に Lv の紋章、右に MP。下段に障壁の数値・職業資源（88px）・星織りの蓄積ピップ、最下段に経験値の 1px 線。

- HP バーには「直前に削られた分」の淡い帯（0.45 秒止まってから減る）と、上3行に重なる障壁の層がある。
- 技能カード：使用可＝金の枠と角飾り、CD＝時計回りに明ける暗い覆い＋ティールの線（24段）＋中央の秒数、
  マナ/資源不足＝青、未解放＝暗転＋錠。右下はホットバーのキー番号。
- 通知（「あと3秒」「マナが足りません」等）は action bar を上書きせず案内欄に1.6秒出す。戦闘の合図より優先。

action bar は `(C, H−68)` に描かれ文字の y は H−72。ビットマップ glyph の上端は `H−65−ascent`。
各 glyph の縦位置は provider の ascent に焼き込み、サーバーは x だけを決める（`CoreHudLine`）。
全体の advance は常に 0 なので、桁数や GUI scale が変わっても中央がずれない。

## 消したバニラ部品

`CoreUiPackPolicy.vanillaOverrides`（= `build_combat_hud.vanilla_overrides()`）だけを透明にする。

- 心臓・満腹度（従来どおり）
- 防具ポイントの列（`armor_empty/half/full`）
- 経験値バー（`experience_bar_background/progress`）。パック適用中はサーバーが Lv を 0 にして数字も消し、Lv と進捗は板に出す。
- ボスバーの **pink** だけ（`boss_bar/pink_background/progress`）。パック適用中のボスバーは pink にして、
  その名前コンポーネントでドット絵のボスバー（名前・182px のゲージ・削れた分の帯・数値）を描く。非適用時は赤の標準バー。

残すもの：ホットバー、選択アイテム名、酸素、乗り物の心臓（水中では酸素の泡が板の上端に少し重なる）。

## ほかの表示面

- **雑魚の名札**（`CoreMobNameplate`）：名前（攻撃の溜め中は技名を赤で）＋下に `Lv` と 40px の HP ゲージ（通常＝赤、エリート＝紫、ボス＝橙）＋弱点の印（炎/氷/雷）。
  advance 0 なので名札の背景の黒帯は出ない。パック適用時だけ（`QuestEncounterCombat.pixelNameplates`）。
- **ダメージ数字**（`CoreDamageNumbers`）：5×7 のドット数字。会心は金＋星、弱点は水色＋▼。大きく出て縮み、上へ流れる（表示エンティティの補間）。

## 作り直しと確認

- 生成：`python scripts/gen_resource_pack.py`（Gradle の `generateResourcePack` も同じ）。HUD は `scripts/build_combat_hud.py`。
  技能原画 `assets/core-ui/skills/*.png` は 32×32 のまま使う（大きさが違うと生成が止まる）。
- 検査：`python scripts/verify_core_ui_assets.py`（Windows では `PYTHONUTF8=1`）。
- 見た目の確認：`CoreHudLayoutTest` が実際のコンポーネントの配置を `build/hud-preview/snapshots/` に書き出し、
  `python scripts/render_combat_hud_preview.py` が 1倍・2倍・3倍・8倍の PNG を `build/hud-preview/` に作る。
  ローカルに 26.2 のクライアント jar があればホットバーと照準を文脈として描く（リポジトリには入れない）。
  これは配置確認図で、実ゲームのスクリーンショットではない。GUI scale ごとの見え方と手触りの判断は Creator が行う。
