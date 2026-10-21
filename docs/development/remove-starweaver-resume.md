# 再開メモ：星織り師の削除（play/gyai/remove-starweaver）

- 済み：星織り師をコード・アセット・パック生成スクリプト・テスト・docs から全削除（6d042d4f）。`:server-minestom:test` 全件通過、`scripts/gen_resource_pack.py` 成功。
- 保存形式：旧セーブの `journey STARWEAVER` はメイジとして読み込み（保存済みメイジ構成→なければ初期構成）、STARWEAVER の構成行は捨てる。テストは `CoreRetirementCompatibilityTest`。公転の刻印石は定義なしの無効石として残る。
- 2026-10-21：最新 main（785f484c）を合流。衝突11件は main 側の中身を残し星織り師の分岐だけ消して解消。main で増えたテストの数（技70→60、刻印49→48、HUD絵70→60）を合わせた。`:server-minestom:test` 全件通過。
- 次の一手：main 向け PR をマージ（司令塔）。
- 注意：7職リワーク（play/gyai/class-rework、未push）と CoreSkillCatalog / CorePlayerCombat / CoreJourney / メニュー / テスト / docs でぶつかる。合流時は「星織りを消す側」で解消する（藍の決定）。HUD のスキル絵は 6職×10＝60 枠になった。
