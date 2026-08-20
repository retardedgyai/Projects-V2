# ProjectS v2 — OpenCode Rules

OpenCodeはこのRepositoryでは「実装端末」として使う。全体設計や優先順位をOpenCode内で再構築しない。

## 最優先

- GitHub Issueをその作業のsource of truthにする。
- Issue外へScopeを広げない。
- 実際の機能より先にGeneric Frameworkを作らない。
- 1 worktree / 1 branchにつき、編集するAgentは1つだけ。
- Subagentは調査・探索・読み取り専用Reviewに限定し、別実装を並列で書かせない。
- Subagentの入れ子を作らない。
- Kotlin-first。
- ServerがDamage / Hit / Reward / Progressionの最終判定を持つ。
- Clientから「Hitした対象」を信用しない。
- GUIやMinecraft内Manual SmokeはUserが行う。Agentはコード、静的確認、自動Test、Buildまで。
- Task完了後は、そのTask専用の作業branchへの通常`git push`まで行ってよい。
- `main`への直接pushは禁止。
- force push（`--force` / `--force-with-lease`）は禁止。
- 他Agentが作業中のbranchへpushしない。
- 破壊的なGit操作や大量削除を独断でしない。

## 作業開始時

1. `git status` と現在Branchを確認する。
2. Issue本文を読む。
3. Issueに明示されたDocsだけを読む。
4. 必要なら以下を追加で読む。ただし全部を毎回読み込まない。
   - `docs/development/rewrite-rules.md`
   - `docs/development/learning-while-building.md`
   - `docs/development/codex-day-1-task-split.md`
   - `docs/decisions/2026-08-19-current-decisions.md`
5. 変更範囲と受け入れ条件を短く確認して、そのまま実装へ進む。

## 実装中

- 小さなタスクでPlan Agent → Architect → Implementerのような多段オーケストレーションを作らない。
- Build Agent自身が実装する。
- Explore / Scoutは「場所を探す」「公式APIを確認する」など明確な調査だけに使う。
- Protocol等、Issueで固定された共有境界を変更したくなった場合は独断で変更せず、理由を報告して止める。
- 不要な抽象化、将来用API、汎用Registryを追加しない。
- 迷った場合は最小の実装を選ぶ。

## 完了時

Issueで指定されたTest / Buildを実行する。

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

Issueがcommitを要求している場合はcommitまで行う。Task専用作業branchへの通常pushも行ってよい。`main`への直接pushやforce pushは行わない。
