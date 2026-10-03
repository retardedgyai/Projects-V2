# Sol Review Protocol

この手順はChatGPT/Sol、Codex経由、その他の利用環境で共通に使います。Reviewは読み取り専用で行い、コードを変更しません。

## Review input

次を既存Task・repoからAgentが集めます。取得できずReviewに必須の情報だけCreatorへ確認します。

- Repository
- branch
- base commit/ref
- head commit
- 元の目的 / Issue（あれば）
- tests/build結果
- Manual Smoke前か後か

## Review procedure

1. `AGENTS.md`とこの文書を読む。
2. `git status`で作業ツリーを確認する。
3. base/headを確認する。
4. `git diff --stat <base>...HEAD`を確認する。
5. `git diff <base>...HEAD`を確認する。
6. 必要なら該当file全文を読む。
7. testsが変更範囲を実際にカバーしているか確認する。

重点は、実際に壊れるbug、lifecycle / cleanup、server/client authority、protocol compatibility、concurrency/state、scope逸脱、regression、tests不足、不要なframeworkです。細かいstyle好みだけで`FIX-FIRST`にしません。Manual Visual / Feelの最終authorityは人間Creatorです。

制作物のReviewではAGENTSの制作品質を適用し、実参照と同条件・通常表示で比較して、用途に対する最大の差と反証を示します。Test成功や前版比改善だけで美的PASSを出さず、反復失敗時は原因別に参照・解釈・実装・比較を見直します。コードのPASS、制作品質、実ゲーム、本人採用は区別します。Creatorの最終authorityはAgent自身の品質比較・修正を省く理由にはしません。

## Verdict

Productionは正式に次の3つへ統一します。

- `PASS`
- `FIX-FIRST`
- `BLOCKED`

Playgroundでは追加で`DROP`を使えます。

## Ready-to-copy prompt

```text
ProjectSのSol Reviewをしてください。
最初にAGENTS.mdとdocs/development/sol-review.mdを読み、現在branchをbaseからreviewしてください。
コード編集は禁止。重大な問題を優先し、PASS / FIX-FIRST / BLOCKEDで判定してください。
```
