# 再開メモ：星織り師の削除（play/gyai/remove-starweaver）

- 済み：星織り師をコード・アセット・パック生成スクリプト・テスト・docs から全削除（6d042d4f）。`:server-minestom:test` 全件通過、`scripts/gen_resource_pack.py` 成功。
- 保存形式：旧セーブの `journey STARWEAVER` はメイジとして読み込み（保存済みメイジ構成→なければ初期構成）、STARWEAVER の構成行は捨てる。テストは `CoreRetirementCompatibilityTest`。公転の刻印石は定義なしの無効石として残る。
- 次の一手：藍の確認後に main 向け PR を作り、OK ならマージ。
- 注意：7職リワーク（play/gyai/class-rework、未push）と CoreSkillCatalog / CorePlayerCombat / CoreJourney / メニュー / テスト / docs でぶつかる。合流時は「星織りを消す側」で解消する（藍の決定）。HUD のスキル絵は 6職×10＝60 枠になった。
