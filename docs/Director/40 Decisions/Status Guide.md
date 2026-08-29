# Status Guide

ProjectSでは「仕様が決まったか」と「実装が進んだか」を別々に管理する。

## Design Status — 頭の中から仕様へ

`IDEA → DESIGNING → NEEDS_DECISION → NEEDS_REVIEW → FIXED`

| Status | 意味 | 次へ進む条件 |
| --- | --- | --- |
| IDEA | 思いつきを外へ出した状態。正本ではない | 作りたい感覚と検討する理由が書かれた |
| DESIGNING | ChatGPT等と体験・ルール・数値を詰めている | 未決定と選択肢が見える |
| NEEDS_DECISION | Directorの判断を待っている | 選択と理由をDecisionへ記録した |
| NEEDS_REVIEW | 既存資料やAIから取り込んだ候補。未承認 | Directorが全文を読み、修正または承認した |
| FIXED | Directorが現在の正式仕様として承認済み | 変更時はDecisionを追加して再承認する |

## Implementation Status — 仕様からゲームへ

`NOT_PLANNED → ISSUE_READY → IMPLEMENTING → PLAYTEST → DONE`

| Status | 意味 | 次へ進む条件 |
| --- | --- | --- |
| NOT_PLANNED | 実装Taskへ切り出していない | FIXED仕様から実装範囲を選ぶ |
| ISSUE_READY | IssueにScope、Acceptance、参照仕様がある | 作業laneへ入れる |
| IMPLEMENTING | branchで実装中 | Test後にMinecraftで触れられる |
| PLAYTEST | Directorが体験を評価中 | PASS / FIX / DROPを判断する |
| DONE | 現在の仕様を満たしている | 変更は新Decisionとして扱う |

`design_status: FIXED` のまま `implementation_status: IMPLEMENTING` になってよい。実装上の都合で仕様を変える必要が出た場合だけ、Design Statusを戻す。

## FIXED Checklist

- [ ] 「頭の中にあるもの」と「絶対に残したい感覚」が文章になっている
- [ ] Playerが何をして、何を判断し、何を感じるか決まっている
- [ ] 主要ルール、数値、条件、失敗、例外が決まっている
- [ ] 他Systemとのinput/outputが決まっている
- [ ] ScopeとNon-Goalsが決まっている
- [ ] 未決定事項が今回の仕様を曖昧にしていない
- [ ] Director本人が全文を読み `approved_by: Director` を記録した

## ISSUE READY Checklist

- [ ] 参照するFIXED仕様が明記されている
- [ ] 今回実装するScopeとNon-Goalsがある
- [ ] Acceptance Criteria、Test、Manual Smokeがある
- [ ] Issue本文に仕様を丸ごと複製していない

## Implementation Health

Healthは仕様のStatusではない。実装を阻む具体的な要因を `🟢 / 🟡 / 🔴 / ⚪` で補助表示する。色だけでなく必ず理由を書く。
