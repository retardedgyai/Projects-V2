# Obsidian Guide

## 初回

1. Obsidianを開く。
2. **Open folder as vault** を選ぶ。
3. `C:\Users\xgaiz\Documents\Codex\Projects-V2\docs` を選ぶ。
4. `00 Dashboard.md` を開いてPinする。

TemplateとDaily Notesの保存先は設定済み。

## 頭の中を出す

1. [[Director/30 Current/Inbox/README|Inbox]]を開く。
2. [[Director/80 Templates/Idea Note|Idea Note]]を使う。
3. 文法や順番を気にせず、自分の言葉で「一番ほしい感覚」を書く。
4. まだ決めていないものを決定済みのように書かない。

## ChatGPTと仕様をGrindする

1. Idea Noteまたは対象仕様をChatGPTへ渡す。
2. 「決めつけず、未決定・選択肢・矛盾を出して」と頼む。
3. AIの提案から採用したいものを自分で選ぶ。
4. System SpecまたはContent Specへ整理する。
5. AIが整理した状態は `NEEDS_REVIEW` のままにする。

## 仕様をFIXEDにする

1. ページを最初から最後まで読む。
2. 頭の中のイメージと違う部分を直す。
3. [[Director/40 Decisions/Status Guide#FIXED Checklist|FIXED Checklist]]を通す。
4. `design_status: FIXED`、`approved_by: Director`、承認日を書く。
5. その後でGitHub Issueへ実装範囲を切り出す。

## 毎朝

1. 今日のDaily Noteを開く。
2. [[Director/30 Current/NOW|NOW]]を見る。
3. 「今日終われば勝ち」を1つ書く。
4. 自分が決めること、ChatGPTと詰めること、実装待ちを分ける。

## 終了時

- Dailyだけに残った正式仕様を個別ページへ戻す。
- 判断理由をDecisionへ残す。
- 明日の最初の一手を1行書く。

Plugin、Theme、Graph Viewは必要になるまで増やさない。最初はMarkdown、リンク、検索、Template、Daily Notesだけで運用する。
