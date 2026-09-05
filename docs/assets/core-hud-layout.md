# 戦闘 HUD — Vanilla 26.2

サーバーから送る既存 `CoreHudState` / `CoreHudSkill` と、任意適用のリソースパックだけで表示する。
クライアント MOD・protocol・Minecraft の標準フォントを変更しない。

## 表示

- 左の心臓欄を HP、右の満腹度欄をマナの細いバーに置き換える。数値はバー内部に表示する。
- 上には 32×32 GUI px の技能を3つ表示する。既存画像の透明余白を除き、絵を26px以内に拡大する。
- 使用可能: 明るい絵と金枠。クールダウン: 時計回りに解除される暗い覆いと中央の残り秒数。
- マナ不足: 青く暗い絵・青枠・中央 `MP`。クールダウンが残っている場合は秒数の方を優先する。
- 右下の小さい数字は従来のホットバーキー。能力・マナ消費・入力方法そのものは変更しない。
- 防具、経験値、酸素、乗り物の心臓、ホットバー、選択アイテム名は残す。
- パック拒否・適用失敗・破棄時は、通常の日本語テキスト HUD へ戻る。

## GUI 座標の根拠

インストール済み Vanilla 26.2 の `Hud.extractOverlayMessage` / `extractPlayerHealth` /
`extractArmor` / `extractSelectedItemName` と `GuiGraphicsExtractor.textWithBackdrop` を確認した。
GUI 高さを H、整数の中央 X を C とする。

| 部分 | X | 上端 Y | 大きさ |
|---|---|---|---|
| HP | C − 91 | H − 39 | 81×9 |
| マナ | C + 10 | H − 39 | 81×9 |
| 技能1・2・3 | C − 52 / C − 16 / C + 20 | H − 94 | 各32×32 |
| 既存選択アイテム名 | 中央寄せ | H − 59 | バニラのまま |
| 既存防具 | C − 91 | 通常 H − 49 | バニラのまま |

action bar は `(C, H−68)` に移動して文字を Y=−4 に描くため、基準 Y は H−72。
bitmap glyph の上端は `H−65−ascent` になる。HP/マナの ascent は −26、技能は29。
各 layer は負の space glyph で X=0 に戻り、コンポーネント全体の advance は常に0。
数値の桁・残り秒数・ウィンドウ幅が変わっても中心が動かず、GUI scale に追従する。
幅0でも文字を描画することは実クライアントの `textWithBackdrop` で確認済み。

標準心臓・満腹度の透明化は `CoreUiPackPolicy.vanillaOverrides` の50ファイルだけを許す。
心臓の点滅・状態異常・hardcoreの差分も含むが、乗り物の心臓3種は含まない。
すべて9×9の完全透明PNG。標準フォント、GUI全体、アイテム用アトラスには上書きを加えない。

## 資産の作り直し

`python scripts/build_core_ui_assets.py` で生成し、`python scripts/verify_core_ui_assets.py` で検証する。
Python 3 + Pillow を使う。各 bitmap cell は256px以下、advanceはPNGの実際の不透明範囲から検査する。
技能は ready / CD20段階 / MP不足の22状態。秒数は別の輪郭付き数字glyphで中央に重ねる。

新しい技能原画は `assets/core-ui/skills/dash.png`、`slam.png`、`whirl.png` へ置けば同じ処理で採用できる。
元画像は無加工で GUI / item atlas 用にもコピーし、HUD用だけ余白調整・最近傍拡大・状態合成を行う。
採用時は [出典](core-ui-pack.md) と配布パックの `CREDITS.txt` を更新すること。
現時点では既存 Monumenta 出典の3原画を再利用しており、新しい PixelLab 原画を生成済みとは扱わない。

`assets/core-ui/hud-layout-preview.png` は生成時の配置確認図。実ゲームのスクリーンショットではない。
最終的な GUI scale / 解像度 / CD推移 / パック拒否時の確認と遊び心地の判断は Creator が行う。

## 連携と注意

呼出 API は `CoreUiComponents.hud(state, packed)` のまま。`packed` はパックの
`SUCCESSFULLY_LOADED` 確認後だけ true にする。数値・CDはサーバーの最新状態を送る。
action bar を別メッセージで上書きするとHUDはその間消えるため、採取進行や説明は従来どおり
オブジェクト表示、チャット、ボスバーなど別の表示先を使う。通常のHUD送信は継続する。
rootのゲーム状態やメニュー・アイテム操作はこの実装から変更しない。

構造・状態テストは実行可能だが、実画面での適用完了や手動テスト成功をこの資料では主張しない。
