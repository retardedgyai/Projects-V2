---
description: Run multiple ProjectS issues in parallel across isolated worktrees
agent: build
---

ProjectS v2 の GitHub Issues `$ARGUMENTS` を、Userが追加操作しなくても独立worktreeで並列実装してください。

このコマンド自身はオーケストレーター専用です。製品コードは編集しません。

必ず最初に `AGENTS.md` を読み、以下の順で進めてください。

1. `git status` を確認し、現在のオーケストレーション用worktreeがcleanであることを確認する。dirtyなら停止して報告する。
2. `git fetch origin` を実行する。
3. 引数で渡されたIssue番号をすべて取得する。例: `/parallel 2 3 4`。
4. 各Issueについて、Issue本文または明示参照された `docs/development/codex-day-1-task-split.md` から指定Branchを解決する。
5. `git worktree list --porcelain` で、そのBranchに対応する既存worktreeを探す。
   - 既存worktreeがあればそれを使う。
   - 無ければ `~/Projects-V2-issue-<Issue番号>` に `origin/<branch>` を追跡するworktreeを作る。
6. 各worktreeについて次を確認する。
   - 正しいBranch
   - worktree clean
   - `origin/<branch>` を追跡
   - Issueが要求する共有境界/ProtocolのBaseを含む
7. Issue本文の推奨Model / 推論強度を確認する。
   - `opencode models` で利用可能Modelを1回だけ確認してよい。
   - 推奨Modelを一意に解決できる場合のみ `--model` を付ける。
   - 推論強度/variantを安全に一意解決できる場合のみ `--variant` を付ける。
   - 解決が曖昧な場合は、Model選択のために全体を止めず、その子TaskはOpenCodeの現在/default Modelで実行し、最終報告に明記する。
8. 各Issueを別々の `opencode run` プロセスとして、同時に開始する。子Taskには必ず `PROJECTS_V2_SUPPRESS_MANUAL_SMOKE=1` を明示的に設定し、子 `/issue` が自動起動せず親だけが起動を担当する。
   - `--dir <worktree>` を必ず使う。
   - `PROJECTS_V2_SUPPRESS_MANUAL_SMOKE=1 opencode run --dir <worktree> --command issue <Issue番号>` の形で起動し、Issue番号を引数として渡す。
   - 非対話実行なので `--auto` を使ってよい。ただし設定でdenyされている操作は迂回しない。
   - 各プロセスのstdout/stderrは `/tmp/projects-v2-opencode/issue-<番号>.log` へ分離する。
   - 1つのIssueが失敗しても他Issueは止めない。
9. 子プロセスをbackgroundで開始した後、短い間隔で状態を確認する。1つの長時間blocking shellに依存しない。
10. 全Issue終了後、各worktreeについて確認する。
   - `git status`
   - 最新commit SHA
   - remoteへ通常push済みか
   - Test / Build結果が子Task報告に含まれているか
11. merge / cherry-pick / main更新はしない。統合はSol Review後。
12. 全子Taskの終了後、引数が1つだけで、Issue本文にManual Smoke対象があり、子Taskの実装、Test、Build、commit、pushが成功した場合に限り、成功した子Taskのworktree pathを解決し、**このオーケストレーター側worktreeの** `scripts/manual-smoke-launch.sh --worktree <成功した子Taskのworktree>` を1回だけ実行する。target worktree内のlauncher scriptは参照・要求しない。Server/Clientのbuild/runはlauncherに渡したtarget worktreeから行う。起動失敗は実装commitの失敗にはせず、`Manual Smoke launch: BLOCKED` と理由・log pathを報告する。複数IssueではMinecraft Clientを自動起動せず、起動試行は0回とする。

並列実行の原則:
- 1 Branch = 1 worktree = 1 editing OpenCode process。
- 同じworktreeを2つのediting processで触らない。
- 子OpenCode同士に会話や会議をさせない。
- 親オーケストレーターはコードを書かない。
- 子Taskは既存 `/issue` のScope、Test、commit、作業Branchへの通常push規則に従う。
- `main` へのpush、force push、mergeは禁止。
- GUIやゲーム内Manual SmokeはUserが行う。Server/Clientプロセスの起動・停止はManual Smoke準備として許可される。

最後に日本語で、Issueごとに以下だけをまとめてください。
- Issue番号 / Branch / worktree
- 使用Model / variant（分かる場合）
- 成功 / 失敗 / Blocked
- commit SHA
- push済みか
- Test / Build結果の要約
- 残った懸念
- 詳細log path

最後に `次にSol Reviewへ渡せるIssue` を列挙してください。
