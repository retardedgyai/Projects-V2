---
description: Read-only pre-review of the current ProjectS branch
agent: plan
---

現在のBranchの変更を読み取り専用でレビューしてください。ファイルは変更しないでください。

最初に `AGENTS.md` を読む。
その後 `git status`、`git diff --stat`、`git diff`、必要なら `git log` / `git show` を使って確認する。

重点:
- GitHub Issueの受け入れ条件を満たしているか
- Scope外の変更がないか
- 不要なGeneric Framework/抽象化が増えていないか
- Server/Client責務を破っていないか
- Protocol変更の衝突や互換性問題がないか
- lifecycle / cleanup漏れがないか
- Test不足や明確なedge caseがないか
- ProjectSの現在の設計判断と矛盾していないか

細かい好みより、実際に壊れる問題・手戻りになる問題を優先する。

報告は日本語で、重大度順に:
1. BLOCKER
2. 直した方がいい
3. 後回しでよい
4. 問題なしだった重要点

コード変更はしない。
