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
今回はサーバー/クライアントを起動しておらず、実機確認は未実施。

### 実施結果

- 防具の全28セット・112アイテムと、新しいGUI/FIXED接続の検証成功。
- パック全体の検証成功：14,137 assets / 52,039 private glyphs。
- `CoreArmorPresentationTest` 4件、`CorePolish05ForgeFlowTest` 3件が成功。失敗・エラー・skipなし。
- `distZip` 成功。配布ZIP内のサーバーJARを開き、プレートatlasのバイト一致と
  4部位のFIXED接続が含まれることも確認した。
- 配布物：このDドライブの作業ツリー内 `server-minestom/build/distributions/server-minestom-0.1.0-SNAPSHOT.zip`。

壊れた場合はまず `items/armor/warrior_t*` のGUI/FIXED振り分けと、
`models/item/armor/ui/`、`textures/item/armor/ui/plate_guard.png`、pack索引を確認する。
