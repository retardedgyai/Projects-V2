# 承認済み成果の統合とブランチ整理 — Issue #138

## 採用範囲

- 基準: `origin/main@16e51ea6`。
- `warrior-design-rework@53d0ee8d` の職業・AD/AP計算・技能ツリー・HUD/アイコン、戦士AA/演出、インスタンス照明修正。
- mainの採用済み港町を維持。競合した撮影用配置スクリプトは、Javaプロセス名と明示した引数ファイルを照合する版を採用。
- `mage-ice-fang@e6366eae` の氷牙・日本語テストメニュー・モデル工房を統合。
- 承認済み大剣v02の32資産とT1参照を通常パックに格納。原画・形状・持ち方・刃のアニメーションは変更しない。

## 通常起動

`gradlew.bat :server-minestom:installDist` 後、`scripts/start-core-loop.ps1 -JavaHome <Java25>`。
承認済みT1大剣に `-WeaponMaterialReview` は不要。T2〜T4の既存デザインは変更しない。
再生成は `scripts/promote_approved_greatsword.py`。旧確認パック生成も同一資産の再適用なら許可するが、混在・欠損は拒否する。
稼働中のテストサーバーをこの作業で再起動・切替しない。保存データを移動・上書きしない。

## 統合しないもの

- 未承認のMage v4/v5改修・最新の氷形状試作。枝を閉じても復元用タグと元の作業ファイルを残す。
- 別制作のAshen Warden、撤回済みtooltip/旧UI案。採用済み成果へ上書きしない。
- Scorpiusの戦闘AI・経済・保存形式。本編のボス戦へ勝手に導入しない。
- **氷牙・Scorpiusモデルの本編戦闘への接続は未完了。Issue #137で追跡する。工房のmergeを本編接続済みとは扱わない。**

## ブランチ整理・復元

対象は今回の自分たちの `play/gyai/*`、`integrate/gyai/*`、`experiment/gyai/*` と `ui/item-tooltip-assets-v0`。
別Creatorの `play/mare/*`、その他の従来開発ブランチ、mainは削除しない。

削除前に `archive/2026-09-22/<元ブランチ名>` をoriginへ公開してSHAを照合する。
ローカル先端とリモート先端が異なる場合は `archive/2026-09-22/remote/<元ブランチ名>` も保持。
未コミットのソース・アセットは `archive/2026-09-22/snapshots/<元ブランチ名>` に別保存する。
これらは不採用・制作途中の内容を含み、main採用や品質合格を意味しない。

元worktreeは同じHEADでdetachedにし、ファイル・indexの変更状態を維持する。worktree自体は削除しない。
Obsidianの個人メモ・ローカル設定・セーブ・ログ・`.tools/`・`.kotlin/` はこの退避commitに入れず、元のディスクに残す。
復元時は該当タグから新しい作業ブランチを作成する。古いworktreeをmainとして再利用しない。

## 検証

- Solのread-only review: committed gameplay統合と大剣通常パック化の両方で重大blockerなし。
- Creatorは統合元の職業/戦士/工房を実機で試用済み。今回新規アートやAIゲーム操作は行わない。
- full server tests / installDist、model-lab tests / native model smoke / ice fang smoke、通常パックの資産検証と大剣回帰テストをmerge前に実行する。

不具合時: 通常大剣なら `test_default_greatsword.py` と `core-ui-pack/index.txt`、工房の紫黒なら `ModelBundle` の世代照合、保存なら `CoreAccountRepository` とv1〜v9移行バックアップを確認する。
