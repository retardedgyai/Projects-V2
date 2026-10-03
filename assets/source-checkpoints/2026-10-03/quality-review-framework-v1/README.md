# ProjectS 制作・実物レビュー 最小版

このフォルダは独立した手順・ローカル支援。既存5制作のファイル、起動中の処理、親のタスク管理、スケジュール、別環境には触れない。制作はこの仕組みの完成を待たず進めてよい。

## 現場の6手順

1. **意図と保護を読む。** 依頼から用途・形・材質・動作・制約を自分で整理する。過去の「承認」「不採用」「変更禁止」は別欄に残す。承認は対象部分だけに有効。「そのまま進めて」は全品質の合格ではない。必須権限か重大な意図不明だけ本人に質問し、独立して進められる部分は継続する。
2. **複数の実参照から基準を決める。** 既存の参照記録を先に再利用する。品質を左右する用途に合う候補を選び、原URL・作者・投稿者・実見範囲・理由を残す。転記された名前やOCRは実見ではない。作者不明は不明と記録し、作者と投稿者を同一視しない。原URL未取得は保留。回転模型は形・材質用であり、戦闘動作の根拠にしない。動画の一場面から形成→保持→接触→復帰の成立を推測しない。
3. **代表部分を先に作る。** 頭襟胸、翼腕の1払い、主結晶の形成から接触、主要分岐の1ビルドなど、最大の品質差が見える小さい試作を保存する。既存資料で基準が決まるなら直ちに制作へ。差が大きければ全尺・全装備・梱包へ進まず、その差だけ修正する。
4. **通常表示で実物を並べる。** 同じカメラ、画面上の大きさ、照明、状態、通常表示で参照と現物を見る。動画は同じ意味の時点と全短動作を確認する。ツリーは同じゲーム版・レベル・予算・装備・目的・遭遇条件でビルド結果を見る。合わせられない場合は比較不能として条件を取り直す。拡大表示は補助。架空の数値閾値、前版比改善、テスト通過を美的合格理由にしない。
5. **厳しく判定し、最大差だけ直す。** 用途ごとの基準に対して観察した差・証拠・判定者を残す。最も大きい差を次の一手にし、装飾や細部追加の反復を避ける。修正後は変更範囲と影響した動作・材質・接続だけ再比較する。全体へ影響する変更なら関連基準を再確認する。代表試作で十分な実物比較が成立した後だけ拡張する。
6. **別々の状態で引き渡す。** 技術／美的品質／実ゲーム／本人採用をそれぞれ記録。保存差分と現物版に一致する最新検証を進捗証拠にする。原本は保護。main・公開・導入・権限拡張は別途本人の明示承認が必要。この仕組みから自動実行しない。

## 使い方

Node.js 18以降の標準機能だけを使う。追加インストール、外部サービス、監視プロセスは不要。PowerShellから実行できる。この環境でPowerShellとNodeの利用を確認済み。

```powershell
# 名前と未確認を記録する段階（実物品質の合格を意味しない）
node scripts/check.mjs examples/armor.json --mode record
# 自分の作業用コピーを作り、同じフォルダの配下に証拠を置く
New-Item -ItemType Directory -Path runs/armor -Force
Copy-Item templates/packet.json runs/armor/packet.json
# 制作済み代表部分を実見レビューする前後の確認
node scripts/check.mjs runs/armor/packet.json --mode review
# 全尺・全装備への拡張に必要な記録の確認
node scripts/check.mjs runs/armor/packet.json --mode expand
# 内部引き渡しの確認（公開・導入を許可するコマンドではない）
node scripts/check.mjs runs/armor/packet.json --mode handoff
node --test tests/check.test.mjs
```

`record` は記録形式を確認し、合格を記載した状態にはその証拠も要求。`review` は実物・差分・最新技術検証・参照・同条件比較の不足を検出。`expand` はそれに加え代表部分の美的レビュー記録と重大問題を確認。`handoff` は実使用での全体・全動作統合確認と、実ゲームが必須ならその記録も確認する。終了コード0は**当該モードの記録確認のみ**、1は不足、2は読込・引数エラー。出力に美しさの点数はない。実物を見て判断するのは制作担当であり、スクリプトは画像の内容や判断の真偽を理解しない。MatE同等の保証や本人採用の代行もしない。

記録は `templates/packet.json` の1ファイルだけ。`task`／`references`／`evidence`／`decision` を同居させ、書式を増やさない。原URLと識別子は別。未取得は `null`、未実見は `unconfirmed`。証拠の `path` は記録ファイルのフォルダ配下の相対パス、`sha256` は保存済みバイトのハッシュ。PowerShellでは `Get-FileHash -Algorithm SHA256 <path>` で取得できる。証拠の改変・版ずれ・外へのパス参照を検出する。スクリーンショットの自己申告が正しいことまでは証明できない。

配列へ追記する最小項目は次の形（これは書式例であり実証拠ではない）。`artifact` 以外の自作証拠は `artifact_sha256` で保存現物に結びつける。参照証拠の `revision` は参照の `source_version`、それ以外は `task.revision`。既存データをローカルコピーして使い、参照元のファイルを変更しない。

```json
{
  "evidence": [{"id":"view-1","kind":"candidate_view","path":"evidence/view.png","sha256":"保存バイトのSHA256","revision":"prototype-1","artifact_sha256":"保存現物のSHA256","captured_at":"実際の取得日時ISO8601"}],
  "comparison": {"criterion_id":"main-shape","reference_id":"reference-1","candidate_evidence_id":"view-1","reference_evidence_id":"ref-view-1","candidate_conditions":{"camera":"front","size":"same framing","lighting":"matched","state":"idle","display":"normal"},"reference_conditions":{"camera":"front","size":"same framing","lighting":"matched","state":"idle","display":"normal"},"difference":"実見した最大差","verdict":"gap","reviewer":"実見した担当"}
}
```

`comparison` は `decision.comparisons` の1行として入れる。ツリー等の `system` 比較だけ、両条件に `game_version/level/budget/equipment/objective/encounter` を追加する。代表部分だけの判定を全体合格へ持ち上げず、`decision.holistic` で反例が現物に残るか確認し、最後は `decision.integration` に全体・全動作の実使用証拠を付ける。

## 遅くしない運用

参照の原URL、版、実見範囲、観察した特徴は使い回す。変わっていない参照の再取得・長い説明は不要。毎回更新するのは現物版、保存差分、最新検証、影響した比較、最大差と次の一手だけ。証拠の全履歴を毎回ハッシュし直さず、今の比較が使う証拠だけを確認する。`review` は美的合格前にも使える。既存継続承認のある元ドラゴンの隔離試験を新しい `expand` 待ちに戻さない。

任意の `effort_minutes` に調査・管理・制作・検証の実測時間を合算する。時間上限や比率を強制しない。管理負荷が目立つなら、重複説明・影響外のレビュー・参照再取得から省く。省いたものは `simplification` の一文に残す。証拠と重大問題は省かない。効果は保存済みの実成果、修正回数と時間、本人の採用で確かめる。

失敗したら `decision.failure` の原因と次の対応だけ更新する。参照不足→適合する別の実作品・動画を追加。理解違い→元資料の別角度・全動作へ戻り仮説を修正。実装不足→調査を増やすより短い試作。比較漏れ→漏れた条件を追加。原因未判明は合格にせず最大差から調べる。本人の明確な拒否で該当解釈と旧判定を即再開し、`rejected_revision/rejected_interpretation_id/invalidated_epoch` を残す。修正後に `revision/interpretation_id/review_epoch` を更新し、選択参照の `analysis_epoch` も更新して該当解釈を読み直す。参照バイトの再取得は不要。反復失敗・重大失敗時だけ、別視点の担当が最強の反証を探し `independent_counterargument/independent_reviewer` に残す。チェックリストの同意を品質点数にしない。独立レビューが必要でも、影響しない制作まで待たせない。

少数の実案件で、同じ失敗の再発、有用な候補までの実測時間、本人の追加修正負担を `failure.response/simplification` に一文で残し、手順を見直す。現時点では仕組みの有効性は未実証。調査期限や書式完了をPASSへ変換しない。

取得・描画待ちは `waits` に1行。試行期限、最大試行回数、具体的な回復手段を決める。失敗したら同じ試行を無制限に繰り返さず、既存キャッシュ／別の公開一次資料／代表部分の縮小／停止して権限確認へ切り替える。アクセス拒否は迂回しない。期限や回数は案件に合わせて担当が決める。

既存5件への今すぐの適用は [APPLY_NOW.md](APPLY_NOW.md)。例の美的・技術状態は未確認であり、過去の実物レビューを捏造していない。
