# ダンジョン・専用ボス会場の暗転修正

## 原因と対応

港と通常遠征はLightingChunkを使用していたが、DungeonWorldとBossArenaFactoryは
既定のチャンクのままで光の計算が欠けていた。両方とも生成前にLightingChunkを設定する。
既存の昼夜の意図は維持し、時刻を固定・晴天にする。

屋根付きダンジョンは壁灯と中央の灯りだけでは戦闘域全体を照らせないため、
テーマ別の発光ブロックを8ブロック間隔で床に埋め込む。通路や戦闘判定用の空間は塞がない。
クライアントのガンマ・暗視・リソースパックによる全画面明度変更には依存しない。

## 検証

- DungeonTest: 実生成の各部屋をrelightし、戦闘域35×35の頭の高さでblock light >= 5を検証。
- BossArenaFactoryTest: 3会場がLightingChunkであることと、入口/ボス地点の光量を検証。
  夜の祭壇はblock light、昼の2会場はsky lightを検査。
- 既存の床・頭上空間・施設処理は維持。見た目と遊び心地の最終判定はCreatorが行う。

実行元は既存プレイ環境を維持するためastra-core-loop worktreeの
play/gyai/instance-lighting。main未統合の職業/成長/保存v8を含む元ブランチを継承するため、
将来mainへ入れる際はこの修正だけを抽出する。今回はマージしない。

不具合時はDungeonWorld/BossArenaFactoryのchunk supplierと、上記2テストを最初に確認。
処理順はinstance作成→照明対応chunk supplier→地形生成→クライアント向け光データ計算。
