# Source Register

情報の種類ごとに正本を分ける。すべてを1本の順位へ混ぜない。

## Product Truth — 何を作るか

1. `design_status: FIXED` かつ `approved_by: Director` の個別仕様
2. Directorが承認したDecision
3. Vision / System Principles
4. `NEEDS_REVIEW` の取り込み候補
5. Design Bank、Research、過去Roadmap

FIXED同士が矛盾した場合は、新しい日付を機械的に採用せずDecisionを作る。

## Implementation Truth — 今何が動くか

1. merge済みcode + tests
2. 現在の作業branch + tests
3. Build Snapshot
4. Roadmapや古い実装メモ

## Delivery Contract — 今回どこまで作るか

1. 対象branchのGitHub Issue
2. Acceptance Criteria / Test / Manual Smoke

IssueはFIXED仕様を参照する。Issue内の記述とFIXED仕様が衝突したら、Issueを正本として仕様を書き換えずDirectorへ差分を返す。

## Existing Source Map

| Source | Role | Authority |
| --- | --- | --- |
| [[00-product-vision|Product Vision]] | Project identity、初期Scope | 既存基準。Director Consoleへ取り込み後に承認 |
| [[decisions/2026-08-19-current-decisions|Current Decisions]] | 決定・仮決定・未決定 | 既存基準。個別仕様へ分解して再確認 |
| [[game/combat-principles|Combat Principles]] | Combatの共通原則 | System Principles候補 |
| [[game/mana-and-class-resources|Mana and Class Resources]] | Mana/Cooldown/固有ゲージ | 個別仕様の入力 |
| [[game/twin-rods-aerial-combat|Twin Rods Aerial Combat]] | 空中Combat Contract | 実装済み仕様の入力 |
| [[architecture/hunt-session-lifecycle|Hunt Session Lifecycle]] | Hunt所有権とcleanup | Architecture Contract |
| [#127 Combat Build Slice](https://github.com/retardedgyai/Projects-V2/issues/127) | 現在のDelivery Contract | 仕様の正本ではない |
| [#112 Legacy Design Bank](https://github.com/retardedgyai/Projects-V2/issues/112) | アイデア候補 | 丸ごと採用しない |

## Historical / Research

- Roadmap、Day 1 plans、Research、Futureは意図や候補を知る資料。
- 現在仕様として使う場合は個別ページへ取り込み、Director Reviewを通す。
