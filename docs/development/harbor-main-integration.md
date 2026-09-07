# 港町の本編適用

Production contract: [Issue #133](https://github.com/retardedgyai/Projects-V2/issues/133)。
Creator承認: 2026-09-07「これ適用してマージ　そこから何作るか決める」。

## 適用範囲

- main `bc81a03` を基準に、建築完成版 `df69ef6` の港町関連ファイルだけを抽出。
- `HarborScene`、`HarborDistrictArchitecture`、`HarborBackdrop`、そのテストを適用。
- 独立プレビューとサブモニター起動・撮影スクリプト、建築実景と制作記録も保持。
- 元ブランチに含まれる未統合の職業・成長・保存v8・技能UIは適用しない。
- client-fabric、protocol、リソースパック、CoreLoopServer、保存処理に差分なし。

## 本編への接続

既存の `CoreLoopServer.start` → `HarborScene.build` → 建築・海岸生成。
従来の5施設の座標と操作対象、ゲーム側スポーン位置は変更していない。
新しい本編ビルドを起動するとこの港町になる。起動済みのインスタンスを途中で書き換えない。

`HarborPreviewServer` は別ポート25575の建築専用実行ファイル。
`start-harbor-preview.ps1 -Walk` は視点固定なしのADVENTUREで到着桟橋に配置する。
本編の起動経路へ撮影用の移動キャンセル・ウィンドウ操作を登録しない。

## 確認の境界

Creatorは歩行用プレビュー提示後に適用・マージを承認した。
Agentはゲーム操作を代行しておらず、未報告のゲームプレイ検査結果を推測しない。
制作履歴内の588/589テストは元ブランチ時点の記録。main統合版の検証結果はPRに記録する。
建築や移動の問題は `HarborSceneTest`、起動の問題はサーバーログを最初に確認する。

## 統合検証結果

- Java 25、offline、worker 1で `:server-minestom:test :server-minestom:distZip` 成功。
- main基準の全572テスト成功、失敗0・エラー0・skip0。
- Sol Review: PASS。既存施設/スポーン/落下復帰との互換、建築専用scope、独立プレビュー経路を確認。
- 承認元df69ef6と本番建築5ファイル・94実景ファイルの一致を確認。
- 起動中の本編/プレビューは再起動しない。次回の統合版起動から適用される。
