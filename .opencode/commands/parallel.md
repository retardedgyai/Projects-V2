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
7. Issue本文の推奨Model / 推論強度を**実行条件として必ず解決する**。
   - `opencode models --verbose` を1回だけ実行し、利用可能な正確なModel IDを確認する。
   - ProjectS Issueの表記例 `Luna Max / High` は、利用可能なら `openai/gpt-5.6-luna` + variant `high` として実行する。
   - `Luna Max / Medium` は `openai/gpt-5.6-luna` + variant `medium` として実行する。
   - 今後IssueがTerra/Sol等を指定した場合も、利用可能Model一覧から対応する正確なModel IDを解決し、Issue指定の推論強度を小文字variantへ対応させる。
   - OpenCodeの非対話実行では `--model <provider/model>` と `--variant <variant>` を明示する。
   - Issueに推論強度が指定されているのにvariant無しで実行してはいけない。
   - 指定Modelまたはvariantが利用不可・曖昧な場合は、default Modelへ黙ってfallbackしない。そのIssueだけ `Blocked` として理由を報告し、他Issueは継続する。
8. 各Issueを別々の `opencode run` プロセスとして、同時に開始する。
   - `--dir <worktree>` を必ず使う。
   - `--command issue` を使い、Issue番号を引数として渡す。
   - 7で解決した `--model` と `--variant` を必ず付ける。
   - 非対話実行なので `--auto` を使ってよい。ただし設定でdenyされている操作は迂回しない。
   - 各プロセスのstdout/stderrは `/tmp/projects-v2-opencode/issue-<番号>.log` へ分離する。
   - 1つのIssueが失敗しても他Issueは止めない。
9. 子プロセスをbackgroundで開始した後、短い間隔で状態を確認する。1つの長時間blocking shellに依存しない。
10. 全Issue終了後、各worktreeについて確認する。
   - `git status`
   - 最新commit SHA
   - remoteへ通常push済みか
   - Test / Build結果が子Task報告に含まれているか
   - 実際に使用したModel / variantがIssue指定と一致しているか
11. merge / cherry-pick / main更新はしない。統合はSol Review後。

並列実行の原則:
- 1 Branch = 1 worktree = 1 editing OpenCode process。
- 同じworktreeを2つのediting processで触らない。
- 子OpenCode同士に会話や会議をさせない。
- 親オーケストレーターはコードを書かない。
- 子Taskは既存 `/issue` のScope、Test、commit、作業Branchへの通常push規則に従う。
- `main` へのpush、force push、mergeは禁止。
- GUI Manual SmokeはUserが行う。
- 推奨Model / 推論強度を指定したIssueでは、Modelまたはvariantの黙示fallbackを禁止する。

最後に日本語で、Issueごとに以下だけをまとめてください。
- Issue番号 / Branch / worktree
- Issue指定Model / 推論強度
- 実際に使用したModel / variant
- 成功 / 失敗 / Blocked
- commit SHA
- push済みか
- Test / Build結果の要約
- 残った懸念
- 詳細log path

IssueにModel / 推論強度指定があるのに実際のvariantが `未指定` の場合、そのIssueを成功扱いにしないでください。

最後に `次にSol Reviewへ渡せるIssue` を列挙してください。
