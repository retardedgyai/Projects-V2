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
既存ProjectS repoの既存remoteへの通常pushは許可済みです。意味のある変更ごとにcommitし、作業branchへこまめにpushします。main統合、force push、本番deploy、公開設定変更はこの許可に含みません。
2026-10-03のCreatorによる通常自動pushの指示は、以前のTask文書やcheckpointに残る通常push停止指定を更新します。その後の明示的な停止指示があれば従い、過去の記録からmain等の別境界の許可を推測しません。

- branch作成直後は、変更がまだ無くてもoriginへpushして作業場所を見えるようにする。
- 意味のあるcheckpointごとに必要なtargeted Testを行い、Agent自身がcommitして通常pushする。commit/pushの許可は毎回聞かない。
- checkpointの例: 最小prototypeが動く、1つのSkillが完成、重要なTestを追加、review fixが完了、Manual Smokeへ渡す直前、Task完了。
- 数行ごとの細切れcommitは避け、GitHub上で「何が進んだか」が分かる単位にする。
- 保存点には検証済み・未検証・未完成の範囲を明記する。commit/pushは進捗保存であり、品質合格・Task完了・本人採用を意味しない。
- 明らかにcompile不能・壊れた一時状態は通常checkpointとしてcommitしない。作業中断などで必要なら`wip:`と明示したTask branch上のcommitは可。
- secrets、local config、生成物などrepoへ入れるべきでないものはcommitしない。
- `main`へは直接pushせず、force pushもしない。

最初の実装checkpointをpushした後、GitHub CLI `gh`が利用可能かつ認証済みで、そのbranchのPRがまだ無ければ、進捗確認用のDraft PRを`main`向けに自動作成してよいです。PlaygroundのDraft PRは本編採用を意味しません。`gh`が無い、未認証、またはPR作成に失敗した場合は開発を止めず、branchとpushed commitsで可視化を続けます。

PlaygroundのDraft PRをReady化・mergeするのは、Productionへ昇格して必要なReview / Manual Smoke / Testを通した後です。

## 作業開始時

1. `git status`と現在Branchを確認する。
2. ProductionならIssue本文と明示されたDocsを読む。PlaygroundならCreatorの目的と必要なDocsを読む。
3. 変更範囲と受け入れ条件を短く確認して、そのまま実装へ進む。
4. 別repo・worktree・独立制作フォルダを扱う時や再開時は、[起動・再開時の指示範囲](docs/development/agent-startup-and-resume.md)を確認する。現在ターンへ後からファイルを書いただけで自動反映されるとは扱わない。

## 制作品質

- 「これを作って」ではAgentが用途に合う複数の類似実参照を選び、実物を分析して品質基準を決める。参照や要素の選定を毎回Creatorへ戻さない。[Research-first](docs/development/research-first-development.md)を必要な範囲で使う。
- 実物分析 → 代表部分の制作 → 同条件・通常表示で実参照と比較 → 厳しい反証レビュー → 最大の差を修正する。代表試作で早く判断し、展開後は全体・全動作も確認する。
- 失敗が重なるほど、参照不足・理解違い・実装不足・比較漏れを分けて深掘りする。拒否された解釈と旧判定を見直し、同じ誤読への追認を重ねない。
- 前版より改善した、Test/Buildが通った、書類やチェック項目を満たしたことだけでは品質合格にしない。技術検証・制作品質・実ゲーム・本人採用を区別する。
- Agent自身の比較・修正を完了してから、必要な採用判断とCreator Manual Smokeへ渡す。内部試作ごとの承認待ちや一律の書類ゲートで、許可済み制作を止めない。

## 実装中

- Protocol等、固定された共有境界を変更したくなった場合は独断で変更せず、理由を報告してその変更だけ保留する。依存しない分析・独立試作・修正は継続する。
- 不要な抽象化、将来用API、汎用Registryを追加しない。
- 迷った場合は最小の実装を選ぶ。
- checkpointに到達したら自動でcommit + pushし、GitHub上の進捗を更新する。

## 完了時

IssueまたはTaskで指定されたTest / Buildを実行します。

完了checkpointも自動でcommit + pushします。ProductionではSol Review、必要なManual Smoke、PRを経てmainへ統合します。

報告は日本語で、成果と変更File、実施した検証と未確認、残る最大の差・次の一手、commit SHA / push先branchを簡潔に示す。制作物は品質比較の根拠も示す。処理の流れ・重要なClass・修理の入口は理解や保守に必要な時だけ追加し、過去の証拠や説明を毎回繰り返さない。
