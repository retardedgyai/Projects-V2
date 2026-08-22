# ProjectS v2 — Shared Creator / Agent Rules

このrepoは、人間CreatorとCodex / OpenCode等のAgentが同じ自由度で制作する共有環境です。Agentはrepoのルールと対象Taskの契約に従い、全体設計や優先順位を独断で再構築しません。

## 全Creator共通のルール

- PlaygroundではGitHub Issueは必須ではなく、Creatorが目的と範囲を決める。ProductionではGitHub Issueをtask contractにする。
- 合意したScope外へ広げない。
- 実際の機能より先にGeneric Frameworkを作らない。
- 1 worktree / 1 branchにつき、編集するAgentは1つだけ。
- Kotlin-first。
- ServerがDamage / Hit / Reward / Progressionの最終判定を持つ。
- Clientから「Hitした対象」を信用しない。
- Protocol/Core共有境界を無断で壊さない。
- main直接push禁止。
- force push（`--force` / `--force-with-lease`）禁止。
- 他人/他Agentが作業中のbranchへpushしない。
- 破壊的なGit操作や大量削除を独断でしない。
- Manual Smokeのゲーム操作と最終feel判定は、そのTaskを作っている人間Creatorが行う。準備のServer/Client起動・停止はAgentが行ってよい。

repo ownerだけを唯一の設計者/承認者として扱いません。各Creatorは自分のPlaygroundで自由に設計し、自分のSol ReviewとManual Smokeを完結できます。

## Shared Core

次の変更はPlaygroundで試すこと自体はできますが、本編へ入れる前にProduction Issue化し、Sol Reviewを通します。

- `protocol/`
- networking / handshake
- persistence format
- Particle Framework core
- Class runtime共通基盤
- build / CI
- shared world/save format

## Development modes

- `play/<creator>/<slug>`はPlayground / Labs用。Issueなしでprototypeを作り、面白くなければbranchごとDROPしてよい。
- Production branchは本編へ入れるTask用。Issue、acceptance、Test、Sol Review、Creator Manual Smoke、PRを通す。
- PlaygroundのVerdictは`PASS` / `FIX-FIRST` / `DROP`。Productionの正式Verdictは`PASS` / `FIX-FIRST` / `BLOCKED`。

## 作業開始時

1. `git status`と現在Branchを確認する。
2. ProductionならIssue本文と明示されたDocsを読む。PlaygroundならCreatorの目的と必要なDocsを読む。
3. 変更範囲と受け入れ条件を短く確認して、そのまま実装へ進む。

## 実装中

- Protocol等、固定された共有境界を変更したくなった場合は独断で変更せず、理由を報告して止める。
- 不要な抽象化、将来用API、汎用Registryを追加しない。
- 迷った場合は最小の実装を選ぶ。

## 完了時

IssueまたはTaskで指定されたTest / Buildを実行します。

報告は日本語で:

1. 何を変更したか
2. 変更File
3. Test / Build結果
4. 残っている懸念
5. Userが今回理解しておくべきこと 1〜3個
6. 主な処理の流れ
7. 一番重要なFile / Class
8. 壊れた時に最初に見る場所
9. commit SHA / push先branch
