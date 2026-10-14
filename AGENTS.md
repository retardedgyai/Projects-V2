# ProjectS v2 — Shared Creator / Agent Rules

このrepoは、人間CreatorとCodex / Claude Code / OpenCode等のAgentが同じ自由度で制作する共有環境です。Agentはrepoのルールと、Creatorが決めた目的・範囲に従います。全体設計や優先順位を独断で組み替えません。

## 全Creator共通のルール

- 作業の目的と範囲はCreatorが決める。GitHub IssueやReviewの手続きは必須ではない。
- 合意したScope外へ広げない。
- 実際の機能より先にGeneric Frameworkを作らない。
- 1 worktree / 1 branchにつき、編集するAgentは1つだけ。
- Kotlin-first。
- ServerがDamage / Hit / Reward / Progressionの最終判定を持つ。
- Clientから「Hitした対象」を信用しない。
- main直接push禁止。
- force push（`--force` / `--force-with-lease`）禁止。
- 他人/他Agentが作業中のbranchへpushしない。
- 破壊的なGit操作や大量削除を独断でしない。
- ゲーム内の操作と最終的な手触りの判定は人間Creatorが行う。Server/Clientの起動・停止はAgentが行ってよい。

## 共有の土台に触れる変更

次の変更は禁止ではありませんが、壊すと他の作業や既存のセーブに響きます。触れた場合は、完了報告で必ず明記します。

- `protocol/`、networking / handshake
- 保存形式（account / world / save format）
- Particle Framework core、Class runtime共通基盤
- build / CI

保存形式を変える場合は、既存データの移行処理と、その移行のTestを同じbranchに入れます。

## Branchの作り方

Creatorが「これを作りたい」「実装しよう」のように、具体的な制作対象と開始意思を示したら、Agentはbranch作成の許可を毎回聞かずに始めます。

1. `git status`と現在Branchを確認する。
2. Creator slugを`git config --get projects.creator`で読む。
3. 未設定なら一度だけ短いCreator名を聞き、`git config projects.creator <slug>`でこのcloneに保存する。
4. Creator slugとTask slugはbranch用に小文字ASCIIへ正規化し、基本形を`[a-z0-9][a-z0-9-]*`にする。
5. 最新の`origin/main`から`play/<creator>/<slug>`を作る。別Agentや未整理の変更がある場合は、別worktreeに分ける。
6. 作成直後に`git push -u origin HEAD`まで行う。
7. そのまま調査・実装を開始する。

対象がまだ決まっていない相談、設計検討、アイデア出しだけではbranchを作りません。既存の作業branchで同じTaskを続けている場合も新しいbranchを作りません。別Taskの変更を既存branchへ混ぜないでください。

面白くなければbranchごと捨てて構いません。

## 進捗の公開

Creatorが毎回Git操作を指示しなくても、AgentはTask branch上の進捗をGitHubへ継続的に公開します。

- 意味のあるcheckpointごとに必要なTestを行い、Agent自身がcommitして通常pushする。commit/pushの許可は毎回聞かない。
- checkpointの例：最小の形が動く、1つの機能が完成、重要なTestを追加、Creatorの指摘を直した、ゲーム内で試せる状態になった、Task完了。
- 数行ごとの細切れcommitは避け、GitHub上で「何が進んだか」が分かる単位にする。
- 明らかにcompile不能・壊れた一時状態は通常checkpointとしてcommitしない。中断などで必要なら`wip:`と明示する。
- secrets、local config、生成物などrepoへ入れるべきでないものはcommitしない。
- 最初の実装checkpoint後、`gh`が使えればDraft PRを`main`向けに作ってよい。

## mainへの統合

- Creatorが「マージしていい」と言ったら、AgentはTestが通っていることを確認してPRをReady化し、mergeしてよい。
- Creatorの指示なしにmergeしない。
- mergeしたbranchの後片付け（worktree削除など）は、Creatorに確認してから行う。

## 実装中

- 不要な抽象化、将来用API、汎用Registryを追加しない。
- 迷った場合は最小の実装を選ぶ。
- 共有の土台に触れる必要が出たら、そのまま進めてよいが、理由を報告に残す。

## 完了時

変更範囲に合ったTest / Buildを実行し、完了checkpointをcommit + pushします。

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
