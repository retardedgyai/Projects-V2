# プレート防具のUI反映（2026-09-28）

## 対象

Creatorが評価したプレートの仕上げ版を、戦士T1〜T4の頭・胴・脚・足に適用する。
工房左の装備アイコン、中央の3D表示、インベントリなどGUI表示が対象。
布・皮・他職の新しい外観や、プレートという新しい能力値分類は追加しない。
現段階の各Tierは同じプレート外観を使用し、Tierの数値や強化値は既存UIが表示する。

## 表示の接続

1. `CorePolish05ForgeFlow` は既存の `projects:armor/warrior_t{tier}_{slot}` を選ぶ。
2. アイテムの `minecraft:display_context` により、GUIでは新しい64pxアイコンへ進む。
3. 工房中央が指定するFIXEDでは、独立したプレートUIモデルへ進む。
4. それ以外は既存モデルへ進む。装着中の兜・胴・脚・足の表示、能力値、部位、セーブ形式は変更しない。

`scripts/art_studies/plate_guard_study.py` の実モデル・実テクスチャを共通原稿とし、
`scripts/plate_armor_ui.py` でGUI/FIXEDの向きと倍率を設定する。
アイコンは同じモデルから透明背景へ描画する。透明度は実モデルの描画範囲から生成し、
背景に似た暗い色を抜く処理はしない。既存のプレビューレンダラの通常RGB出力は維持する。

本体の生成入口は `scripts/build_class_armor_assets.py`。
16個のFIXEDモデル、1枚の共有テクスチャ、16個のアイコンを追加・更新する。
参照元Islesのモデルや画像はパックに含めない。

## 確認手順

- `python scripts/build_class_armor_assets.py`
- `python scripts/verify_class_armor_assets.py`
- `python scripts/preview_plate_ui.py`
- `gradlew :server-minestom:test --tests '*CoreArmorPresentationTest' --tests '*CorePolish05ForgeFlowTest' :server-minestom:distZip --offline`

検証ではGUI/FIXEDの参照先、全4部位×4Tierの索引、モデルの座標・UV、
アイコンの透過・枠内への収まり、装着用fallbackの維持を確認する。
プレビューは `.tools/armor-review/plate-ui-integrated/plate-ui.png`。
実機画面ではなく、パックから参照をたどって描画した確認画像。

ゲームでの最終確認は更新パックを読み込んだ戦士で鍛冶師を開き、
頭・胴・脚・足を選択して左の絵と中央表示、確認画面を確認する。
UI反映後、Vanilla 26.2を直接起動して25622へ接続し、パック読み込み成功を確認。
Creatorの確認を受け、次の拡大・回転プレビューを追加した。

### 実施結果

- 防具の全28セット・112アイテムと、新しいGUI/FIXED接続の検証成功。
- パック全体の検証成功：14,137 assets / 52,039 private glyphs。
- `CoreArmorPresentationTest` 4件、`CorePolish05ForgeFlowTest` 3件が成功。失敗・エラー・skipなし。
- `distZip` 成功。配布ZIP内のサーバーJARを開き、プレートatlasのバイト一致と
  4部位のFIXED接続が含まれることも確認した。
- 配布物：このDドライブの作業ツリー内 `server-minestom/build/distributions/server-minestom-0.1.0-SNAPSHOT.zip`。

壊れた場合はまず `items/armor/warrior_t*` のGUI/FIXED振り分けと、
`models/item/armor/ui/`、`textures/item/armor/ui/plate_guard.png`、pack索引を確認する。

## 中央の拡大・回転プレビュー

- 中央の防具枠を120×164から260×260へ拡大し、中央の展示領域に配置。
- 武器の回転中は装備中の武器ファミリー・Tierに対応する実モデルをFIXEDで表示。
  左の行アイコンと確認画面は既存の表示を使用する。
- 通常時の中央武器は承認済みの軽い画像を使い、回転開始時にだけ実モデルへ切り替える。
  初期表示から高密度の武器モデルを常時描くとカーソルの遅延を感じるため。
- 回転の操作説明文は表示しない。
- Vanillaのspectator用クリックはボタン解放を送信しないため、Creator合意により
  「中央をクリックで回転開始→マウス移動→もう一度クリックで固定」を採用。
- 固定するクリックは他のボタン上でも消費する。次のクリックから通常操作に戻る。
- 上下は±65度、左右は一周可能。装備選択・確認画面・終了で回転をリセット。
- `ForgePreview` はセッションだけの表示状態。能力値・保存・強化判定には渡さない。
- `UiSessions` がクリック位置と表示状態を扱い、`UiRenderer` が回転を描画する。
  回転機能の初版では既存のカーソル入力取得・平滑化を維持した。
- その後の操作確認で回転前のカーソル遅延が報告されたため、軽量な武器画像へ戻し、
  入力取得を60回/秒から120回/秒へ上げて体感を再確認する。
  Vanillaクライアントのネットワーク処理より短い遅延は保証できない。
- 回転はインスタンススレッドで更新し、1tick補間。奥行きを圧縮して背景とカーソルの間に収める。
  プレートFIXEDモデルの奥行き方向の中心も補正し、回した時の公転を防ぐ。

検証：`web-ui-lab:test`、`uiSmoke`（回転・固定・クリック漏れ・既存カーソル）、
`polish05Smoke`（強化確認・実行）、`CorePolish05ForgeFlowTest`、防具アセット検証。
最終的な大きさ・回転方向・操作感はCreatorのMinecraft確認待ち。
