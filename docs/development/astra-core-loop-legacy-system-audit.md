# Astra core loop — 旧ProjectS実装資料の監査

監査日: 2026-09-05。旧ProjectSのローカル監査cloneにある資料を読み、コードとの対応を確認した。`docs/design/**`と`docs/research/**`は別担当が確認。本書の担当範囲は54文書すべてを通読した。旧repositoryの`AGENTS.md`も通読した。

## 今回つなぐべき体験

2026-08-18の`docs/implementation/revised-development-plan.md`は、VFX EditorとWorld Builderを凍結し、プレイヤーが開始、武器を試す、Mobを倒す、素材を得る、装備を更新する、さらに強い敵へ進む流れを優先している。`docs/beta/beta-full-build-master-plan.md`も、クラスや基盤の存在ではなく実際に遊べるループを完成条件としている。

旧計画のLv1〜45、パーティー、職業、市場全体は今回の完了条件へ自動的に持ち込まない。ユーザーの現在の要求である、自由探索・ボス直行可能な生成マップ、素材の使い道、T1〜T4、サーバーだけで完結するUIを優先する。戦闘のみで遊ぶ人に採取を強制しないという旧設計の原則は、戦利品券から素材への交換で満たす。

## 実装の実態

- A〜Hの文書でいう「foundation実装済み」は、ほとんどが純粋モデル・境界・テストまで。ゲームプレイ接続は別工程で、既定フラグはすべてfalseだった。
- Activation Wave 1にも8モジュールの登録と診断はあるが、OFFの既定状態と、実producer不足を拒否するdescriptorが残っている。
- `DataManager.java`と`QuestManager.java`は空のclassだった。
- `StagingEconomyCatalog.java`と`StagingEconomyService.java`には、2鉱石→1インゴット、3インゴット→T1試験武器、T1武器+2インゴット→T2試験武器という具体的操作がある。ただしstaging専用で、通常UIや本番プレイヤー経済へ接続済みとは言えない。
- `TransactionEngine.java`はreserve/consume/produce/persist/commit、exact terminal replay、入力競合、rollback、commit uncertaintyを実装している。実アイテム・保存への接続はparticipant側の責任。
- `RewardClaimService.java`はexclusiveなdurable storeとdelivery portを利用する。portの存在自体は報酬の本番配線を意味しない。
- `StagingTransactionRecoveryService.java`は中断レコードを分類する。CONSUMED等はRECOVERY_REQUIRED、COMMIT_UNCERTAINは隔離となり、自動的に交易や報酬を再実行しない。
- `FilePlayerProgressRepository.java`はrevision照合、未知version隔離、atomic save等を持つ。しかし旧ゲーム内PlayerManagerを置換した完成ループではない。

## 引き継ぐ不変条件

- 素材の支払い、完成品、装備Tier、報酬受取記録を同じ永続化単位で確定する。
- 同じrequest IDは同じ結果を返し、異なる内容へのID再利用を拒否する。
- 同じrun/node/bossの報酬を、新しいrequest IDで再送しても再付与しない。
- 保存失敗時はメモリと材料を更新しない。atomic置換後に失敗が報告された場合は、正確な保存内容を確認して結果を解決する。
- ファイル欠損と破損を区別する。破損や未知versionを新規アカウントで上書きしない。
- 一時的な攻撃、クールダウン、標的、Minecraft entity、UIは保存しない。
- 旧データ形式を移行せず、今回のPlayground用core-loop保管領域を独立させる。
- 離脱や死亡後にも再出発可能にする。T1地図を無料供給し、獲得済み素材は失わない。

## 通読した文書の全一覧

### docs/beta 直下（17）

- `acceptance-matrix.md`
- `activation-wave-1-integration-report.md`
- `beta-full-build-master-plan.md`
- `canonical-ids.md`
- `dependency-graph.md`
- `track-b-equipment-item-mods-implementation.md`
- `track-c-wave-1-combat-elements.md`
- `track-e-foundation-report.md`
- `track-f-party-quest-reward-foundation.md`
- `track-g-mob-editor-v2-foundation.md`
- `track-h-server-foundation.md`
- `wave-1-integration-report.md`
- `wave-1-owner-decisions.md`
- `wave-2-integration-report.md`
- `wave-2-owner-decisions.md`
- `wave-3-integration-report.md`
- `wave-3-owner-decisions.md`

### docs/beta/activation（11）

- `activation-master-plan.md`
- `capability-handshake-preflight.md`
- `rollback-runbook.md`
- `runtime-kernel.md`
- `staging-fixture-ids.md`
- `staging-gates.md`
- `track-1-persistence-equipment-adapter.md`
- `track-2-fire-ice-training-dummy-runtime.md`
- `track-3-staging-economy-runtime.md`
- `track-4-party-content-protocol-report.md`
- `wave-1-owner-decisions.md`

### docs/beta/contracts（6）

- `client-protocol-contract.md`
- `item-metadata-contract.md`
- `mob-editor-v2-contract.md`
- `mod-contract.md`
- `player-data-contract.md`
- `recipe-transaction-contract.md`

### docs/beta/tracks（8）

- `track-a-player-progression-persistence.md`
- `track-b-equipment-item-mods.md`
- `track-c-combat-elements-classes.md`
- `track-d-gathering-refining-crafting.md`
- `track-e-enhancement-tier-repair.md`
- `track-f-party-quest-rewards.md`
- `track-g-mob-editor-content.md`
- `track-h-client-ui-protocol.md`

### docs/beta/implementation（1）

- `track-a-player-progression-persistence.md`

### docs/implementation（8）

- `combat-foundation-gap-analysis.md`
- `current-code-audit.md`
- `implementation-roadmap.md`
- `revised-development-plan.md`
- `spec-coverage-matrix.md`
- `starter-sword-limited-cutover.md`
- `starter-sword-runtime-validation.md`
- `warrior-spin-slash-shadow-validation.md`

### その他（3）

- `docs/ai/CODEX_TASK_TEMPLATE.md`
- `docs/protocol/beta-protocol-v1.json`
- `docs/vfx-motion-foundation.md`

beta直下は17文書、beta全体は43文書、担当範囲総数は54文書。画像・旧デザイン資料は別の監査担当に含まれる。
