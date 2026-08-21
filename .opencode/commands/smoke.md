---
description: Start Manual Smoke processes for an existing ProjectS issue worktree
agent: build
---

ProjectS v2 の GitHub Issue #$1 用に、既存worktreeのManual Smokeを準備してください。

1. `AGENTS.md` を読み、`gh issue view $1 --repo retardedgyai/Projects-V2` でIssue本文を取得する。
2. Issue本文の作業Branchを解決し、`git worktree list --porcelain` から対応する既存worktreeを探す。worktreeが無い場合やBranchが曖昧な場合は停止して報告する。
3. このオーケストレーター側worktreeの `scripts/manual-smoke-launch.sh --worktree <解決した対象worktree>` を実行する。target worktree内にlauncher scriptが存在することは要求しない。launcherは渡されたtarget worktreeからServerとFabric Clientをbuild/runする。
4. Minecraft GUIやゲーム内操作は行わない。起動失敗は `Manual Smoke launch: BLOCKED`、理由、log pathを報告する。
