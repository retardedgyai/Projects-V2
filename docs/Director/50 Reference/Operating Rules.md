# Operating Rules

## このVaultの役割

このrepoの `docs` は、ProjectSについてDirectorが覚え続けなくてよいようにする外部脳であり、FIXEDになったゲーム仕様の正本。

- 別VaultへProjectS仕様を複製しない。
- Obsidianには「何を、なぜ、どんな体験として作るか」を保存する。
- GitHub Issueには「FIXED仕様のどこを今回どう実装するか」を書く。
- code/testsは「現在どこまで動くか」の証拠であり、仕様を自動で変更しない。
- AIが整理した文章は、Directorが承認するまで `NEEDS_REVIEW`。

## Capture → Grind → Fix

1. **Capture:** 思いつきを `30 Current/Inbox` またはIdea Noteへ雑に出す。
2. **Grind:** 必要ならChatGPTと、体験・ルール・数値・例外・接続を詰める。
3. **Decide:** AIに決めさせたくない点だけDecisionへ分離する。
4. **Review:** Directorが完成したページを最初から最後まで読む。
5. **Fix:** 納得したら `design_status: FIXED`、`approved_by: Director`、承認日を記録する。
6. **Implement:** FIXED仕様をリンクしたGitHub Issueを作る。Issueへ仕様をコピーしない。
7. **Playtest:** 実物が仕様と意図した感覚を満たすかDirectorが判定する。
8. **Learn:** 感覚が違えばDecisionを残して仕様を更新する。コードだけを正解にしない。

## Page Granularity

- `Combat`、`Elements`、`Bosses`のような大分類ページはIndex / Mapとして使う。
- 完成させる仕様は `Fire`、`Ice`、`Rift Executioner`のように単独で読める粒度へ分ける。
- 1ページを読めば、その仕様を頭の中へ復元できる状態を目指す。
- 空のページを先回りして大量作成せず、考え始めた時に増やす。

## Truth Model

### Product Truth

Director承認済みの `design_status: FIXED` ページ。何を作るかについて最優先。

### Implementation Truth

merge済みcode/testsと現在branch。今何が動くかについて最優先。

### Delivery Contract

GitHub Issue。FIXED仕様の一部を今回どこまで実装するか定める。Product Truthを上書きしない。

矛盾を見つけたら勝手に片方へ合わせず、差分を `NEEDS_DECISION` としてDirectorへ返す。

## WIP Rules

- Quality featureのNOWは原則1件。
- 未確定を実装AIへ勝手に決めさせない。
- 今回不要なFrameworkを先に作らない。
- Branch名やコミット数など時間で古くなる情報を仕様ページへ埋め込まない。
- `main`へ直接pushしない。
