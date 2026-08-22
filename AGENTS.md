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

## Playgroundの自動開始

Creatorが「これを作りたい」「これ試したい」「実装しよう」のように、具体的な制作対象と開始意思を示したら、Agentはbranch作成の許可を毎回聞かずにPlaygroundを開始します。

1. `git status`と現在Branchを確認する。
2. Creator slugを`git config --get projects.creator`で読む。
3. 未設定なら一度だけ短いCreator名を聞き、`git config projects.creator <slug>`でこのcloneに保存する。
4. Creator slugとTask slugはbranch用に小文字ASCIIへ正規化し、基本形を`[a-z0-9][a-z0-9-]*`にする。空白、`_`、その他の区切りは`-`へ寄せ、無効/空になる場合だけCreatorへ短く確認する。
5. 現在が`main`で作業ツリーがcleanなら`main`を`git pull --ff-only`で最新化する。
6. 内容から短いTask slugを決め、`play/<creator>/<slug>`を作成して移動する。
7. 作成直後に`git push -u origin HEAD`まで行い、GitHubから現在の作業branchが見える状態にする。
8. そのまま調査・実装を開始する。

Creatorが「なんかやりたい」「何か作りたい」のように対象をまだ決めていない場合は、Agentが短く候補を出すか何を作りたいか聞きます。対象が決まるまではbranchを作りません。

単なる相談、設計検討、アイデア出しだけではbranchを作りません。既存の作業branchで同じTaskを続けている場合も新しいbranchを作りません。別Taskの変更を既存branchへ混ぜないでください。

作業ツリーに未整理の変更がある、他Agentの作業branchにいる、または安全にbranchを切り替えられない場合は、変更を壊さずに別worktree/branchへ分離するか、必要な時だけCreatorへ状況を確認します。

Shared Coreを本編へ変更する作業は、Playgroundの自動branch作成ではなくProduction workflowを使います。

## GitHub checkpoint publishing

Creatorが毎回Git操作を指示しなくても、AgentはTask branch上の進捗をGitHubへ継続的に公開します。

- branch作成直後は、変更がまだ無くてもoriginへpushして作業場所を見えるようにする。
- 意味のあるcheckpointごとに必要なtargeted Testを行い、Agent自身がcommitして通常pushする。commit/pushの許可は毎回聞かない。
- checkpointの例: 最小prototypeが動く、1つのSkillが完成、重要なTestを追加、review fixが完了、Manual Smokeへ渡す直前、Task完了。
- 数行ごとの細切れcommitは避け、GitHub上で「何が進んだか」が分かる単位にする。
- 明らかにcompile不能・壊れた一時状態は通常checkpointとしてcommitしない。作業中断などで必要なら`wip:`と明示したTask branch上のcommitは可。
- secrets、local config、生成物などrepoへ入れるべきでないものはcommitしない。
- `main`へは直接pushせず、force pushもしない。

最初の実装checkpointをpushした後、GitHub CLI `gh`が利用可能かつ認証済みで、そのbranchのPRがまだ無ければ、進捗確認用のDraft PRを`main`向けに自動作成してよいです。PlaygroundのDraft PRは本編採用を意味しません。`gh`が無い、未認証、またはPR作成に失敗した場合は開発を止めず、branchとpushed commitsで可視化を続けます。

PlaygroundのDraft PRをReady化・mergeするのは、Productionへ昇格して必要なReview / Manual Smoke / Testを通した後です。

## 作業開始時

1. `git status`と現在Branchを確認する。
2. ProductionならIssue本文と明示されたDocsを読む。PlaygroundならCreatorの目的と必要なDocsを読む。
3. 変更範囲と受け入れ条件を短く確認して、そのまま実装へ進む。

## 実装中

- Protocol等、固定された共有境界を変更したくなった場合は独断で変更せず、理由を報告して止める。
- 不要な抽象化、将来用API、汎用Registryを追加しない。
- 迷った場合は最小の実装を選ぶ。
- checkpointに到達したら自動でcommit + pushし、GitHub上の進捗を更新する。

## 完了時

IssueまたはTaskで指定されたTest / Buildを実行します。

完了checkpointも自動でcommit + pushします。ProductionではSol Review、必要なManual Smoke、PRを経てmainへ統合します。

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
