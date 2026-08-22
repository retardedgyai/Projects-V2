# Creator Onboarding

## Prerequisites

- Git
- JDK 25
- GitHub access
- Codex（またはOpenCode等の自分が選んだAI開発環境）

## Clone and verify

```bash
git clone <repository-url>
cd Projects-V2
./gradlew build
```

WindowsではGradle wrapperを`gradlew.bat`として実行します。PowerShellでは`./gradlew.bat build`、Linuxでは`./gradlew build`を使います。

## Creator名を一度だけ登録

このcloneで使う短いCreator名を一度だけ設定します。branch名に使うので、小文字英数字と`-`だけの短い名前が分かりやすいです。

```bash
git config projects.creator <name>
```

例:

```bash
git config projects.creator mei
```

確認:

```bash
git config --get projects.creator
```

この設定はそのPC/cloneだけに保存され、repoへcommitされません。未設定でも、Playgroundを始める時にAgentが一度だけCreator名を聞けます。空白や`_`等を含む名前を設定した場合も、Agentがbranch用に安全な小文字slugへ正規化します。

## Codexを起動

repo rootでCodexを起動し、最初に`AGENTS.md`と`docs/development/creator-workflow.md`を読ませます。

```text
AGENTS.md と docs/development/creator-workflow.md を読んでください。
私はProjectSのCreatorとして参加します。
以後、具体的に何かを作りたいと言ったらPlaygroundの自動開始ルールとcheckpoint commit/pushルールに従ってください。
```

特定のownerの許可を待つ必要はありません。mainへ直接pushはしません。

## Run the game

Server:

```bash
./gradlew :server-minestom:run
```

Client（別ターミナル）:

```bash
./gradlew :client-fabric:runClient
```

## First Playground

今後はCreator自身がbranchコマンドを毎回打つ必要はありません。Codexへ普通に作りたいものを言います。

例:

```text
鎌を使う新クラス作りたい。
既存の戦闘システムとParticle Frameworkを使って、まず遊べる最小prototypeから試したい。
```

Agentは安全を確認した上で自動的に:

```text
mainを最新化
→ play/<creator>/reaper-class のようなbranchを作成
→ originへpush
→ 実装開始
```

まで進めます。

「なんかやりたい」「何か作りたい」だけなら、Agentが候補を出すか何を作るか短く聞きます。対象が決まってからbranchを作ります。

## GitHubで進捗確認

Agentは意味のある作業checkpointごとに自動でcommit + pushします。そのためCreatorは毎回「commitして」「pushして」と頼む必要はありません。

GitHubのBranchesまたはDraft PRを見ると、現在の作業branchと途中のcommitsを確認できます。GitHub CLI `gh`が利用可能なら、最初の実装checkpoint後に進捗確認用Draft PRが作られる場合があります。

明らかに壊れた一時状態や数行単位の細切れ変更は通常commitせず、最小prototype完成、Skill完成、fix完了、Manual Smoke前などの分かりやすい単位で公開します。

実装後は`docs/development/sol-review.md`のpromptで自分のSol Reviewを行い、`PASS`ならManual Smoke、`FIX-FIRST`なら修正、`DROP`ならbranchを破棄します。Gameplayの操作とfeel判定はCreator本人が行います。

## Mainline feature

本編へ入れる場合は、Playgroundの成果または新しいGitHub IssueをProduction workflowへ昇格し、Issueのacceptance、Test、Sol Review、必要なManual Smoke、PRを通します。
