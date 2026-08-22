# Sol Review Protocol

この手順はChatGPT/Sol、Codex経由、その他の利用環境で共通に使います。Reviewは読み取り専用で行い、コードを変更しません。

## Review input

最低限、次をCreatorから受け取ります。

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
