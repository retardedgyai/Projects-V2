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

repo rootでCodexを起動し、最初に`AGENTS.md`を読ませます。特定のownerの許可を待つ必要はありません。自分のbranch / worktreeで作業し、mainへ直接pushしないでください。

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

```bash
git switch -c play/<name>/first-class
codex
```

Codexへ次のように伝えます。

```text
AGENTS.mdを読んでください。
ProjectSのPlaygroundとして、既存Frameworkを使いながら自分の新クラスを自由に試作したいです。
まずrepoを調べて最小の試作を作ってください。
```

実装後は`docs/development/sol-review.md`のpromptで自分のSol Reviewを行い、`PASS`ならManual Smoke、`FIX-FIRST`なら修正、`DROP`ならbranchを破棄します。Gameplayの操作とfeel判定はCreator本人が行います。

## Mainline feature

本編へ入れる場合は、Playgroundの成果または新しいGitHub IssueをProduction workflowへ昇格し、Issueのacceptance、Test、Sol Review、必要なManual Smoke、PRを通します。
