# 戦闘 HUD — Vanilla 26.2（2026-10-16 版「近代MMO × マイクラ」）

サーバーから送る `CoreHudState` と、任意適用のリソースパックだけで表示する。クライアント MOD・protocol・
Minecraft の標準フォントは変更しない。パック非適用時は従来どおり日本語テキスト HUD（action bar）に戻る。
方向と参考の記録は `assets/core-ui/proposals/2026-10-16-combat-hud/README.md`。

## 規則

- マイクラの GUI の 1px にドットを揃える。例外は技能原画だけで、32px の原画を 16px のホットバーのスロットに
  重ねる（アイテムのテクスチャと同じ密度）。`verify_core_ui_assets.py` が「セル高＝provider の height」
  （技能だけ 2 倍）と、サーバーの advance 表＝クライアントの advance を全 HUD フォントで検査する。
- 数字はマイクラ標準フォントそのもの：`minecraft:font/ascii.png` を `projects:hud_num_*` から参照し、
  U+E600+n（n は ascii のマス番号）で描く。白＋バニラの影。パックに Mojang の画像は入れない。
- 通知とボス名はバニラのフォント（unifont）。幅は `CoreUiComponents.width`（漢字・かな 9px）で見積もる。
- 絵は `scripts/combat_hud_art.py`、グリフ化は `scripts/build_combat_hud.py`。

## 配置（GUI 座標。x は画面中央から、top は画面下端から）

| 部品 | x | top | 大きさ |
|---|---|---|---|
| ホットバー（バニラのスプライトを差し替え） | -91 | 22 | 182×22（選択枠 24×23） |
| 技能原画（キー2〜6 = スロット1〜5） | -88+20i | 19 | 16×16（原画 32px） |
| 職業資源の20マス | -89 から 9px 間隔 | 25 | 8×2 |
| HP / MP の板（console） | -92 | 40 | 184×19（資源の帯を含む） |
| HP ゲージ / MP ゲージ | -78 / +24 | 38 | 54 / 55 × 9 |
| Lvの菱形（翼・冠・銘板込み、職業別） | -28 | 57 | 56×38、菱形の中心は下から 34 |
| バフの札 | -92 から右へ | 55 | 高さ 13 |
| 通知・戦闘の合図 | 中央寄せ | action bar の文字行 | バニラのフォント |

- HP ゲージ：体力 → 吸収（バニラの金）→ 直前の被ダメージの淡い帯（0.45 秒止まってから減る）。最大値の 1/6 ごとに目盛り。
- Lvの菱形：縁の 2px の帯が経験値（24 段、上から時計回り）。Lv は銘板の中央に金の数字。
- 技能：使用可＝原画そのまま、CD＝暗転＋中央に秒数、マナ/資源不足＝青み、未解放＝暗転＋錠。
- 通知（「マナが足りません」等）は action bar を上書きせず HUD の文字行に 1.6 秒。戦闘の合図より優先。

action bar は `(C, H−68)` に描かれ文字の y は H−72。ビットマップ glyph の上端は `H−65−ascent`。
各 glyph の縦位置は provider の ascent に焼き込み、サーバーは x だけを決める（`CoreHudLine`）。
全体の advance は常に 0 なので、桁数や GUI scale が変わっても中央がずれない。

## ボスバー

pink のボスバーのスプライトを透明にし、名前コンポーネントで描く。板 x=-115（幅 230、上から 2）、
赤い宝石、名前（バニラのフォント、x=-99、ボスバーの文字行）、右端に残り％、ゲージ x=-110 幅 220（上から 17、5 行）
と 4 分割の目盛り、削れた分の淡い帯。非適用時は赤の標準バー。

## 消した・差し替えたバニラ部品

`CoreUiPackPolicy.vanillaOverrides`（= `build_combat_hud.vanilla_overrides()` + `vanilla_reskins()`）だけ。

- 透明：心臓・満腹度・防具ポイントの列・経験値バー（パック適用中はサーバーが Lv を 0 にして数字も消す）・pink のボスバー。
- 差し替え：`hud/hotbar.png`（炭色の板とスロット）と `hud/hotbar_selection.png`（鉄の枠）。同じ大きさ。

残すもの：選択アイテム名、酸素、乗り物の心臓、オフハンド枠。

## ほかの表示面（今回は変更なし）

- 雑魚の名札（`CoreMobNameplate`）とダメージ数字（`CoreDamageNumbers`）は従来どおり（MaruMinya と 5×7 数字）。

## 作り直しと確認

- 生成：`python scripts/gen_resource_pack.py`（Gradle の `generateResourcePack` も同じ）。
- 検査：`python scripts/verify_core_ui_assets.py`（Windows では `PYTHONUTF8=1`）。
- 見た目：`CoreHudLayoutTest` が実際のコンポーネントの配置を `build/hud-preview/snapshots/` に書き出し、
  `python scripts/render_combat_hud_preview.py` が画面の 2倍・4倍、HUD の 8倍、6職の菱形、ボスバーの PNG を作る。
  ローカルの 26.2 クライアント jar から数字とホットバーのアイテムを文脈として読む（リポジトリには入れない）。
  これは配置確認図で、実ゲームのスクリーンショットではない。GUI scale ごとの見え方と手触りの判断は Creator が行う。
