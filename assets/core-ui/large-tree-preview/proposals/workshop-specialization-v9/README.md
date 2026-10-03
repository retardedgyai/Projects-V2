# V9：始点の得意を育てる、小さなデータ比較

入口は `ProjectS_Specialization_V9.html`。V8を残した別候補で、ゲーム本編・packet・本編保存には接続していない。中央の60点と既存全点の座標、既存947辺の描画・接続を保持し、初期45点の能力・名称、5つの二方向専門帯、Tankの独立出口を比較する。789点から850点、62領域から67領域。全点の倍増ではない。

## 変えた選択

| 始点 | 方向A | 方向B |
|---|---|---|
| Warrior | 一撃と再使用 | 通常攻撃と資源獲得 |
| Tank / Templar | 装甲と継戦 | 守りの再使用 |
| Mage | MPを蓄え、術を重く | 資源獲得と再使用 |
| Ranger | 同じ標的への集中 | 移動と設置の回転 |
| Assassin | 印を消費する決め手 | 接近離脱と資源回収 |

各職の2例は同じsupport編成、追加MODなし、吸命なし、通路HP、使用12pt、未消費0pt。二つの初期枝の計6ptと、一方向の専門帯6ptを取得する。専門帯は途中の要点と深掘りがあり、反対側の終端まで取得するには追加投資が必要。取得順、追加能力値、どの既存技能を重視するか、装備MODの選好を「始点と2方向」で比較できる。

能力値は未採用の追加パッシブ案。職業・技能・装備にある能力源を育てる。専用MODの追加発動・毒・吸引などを重複付与しない。障壁や魔法の入力がない場合はその恩恵を加算しない。必須経路には有効な他の恩恵を残し、GUARD・SHIELD・元素MODの強制、防具の職業制限、強制魔術、出血、自身低HP型は追加しない。

## 始点差と越境

固定Warrior-support入力による実JS比較では、打撃の深掘りはWarrior9pt/Tank14pt、防御の深掘りはTank9pt/Warrior12pt。10の新しい深掘り目標すべてで所属始点が最安9ptになり、他始点からも取得できる。

既存外側の通常Notableのうち、同じ入力でV8とV9のWarrior/Tank両方から到達する92目標を比較する。個々の目標へ別々に到達する最小費用であり、同時取得可能な集合ではない。

- V8：Warriorが安い90 / Tankが安い2 / 同費用0。
- V9：Warriorが安い54 / Tankが安い21 / 同費用17。

Tankの装甲側から既存の一般経路へ、HPなどを選べる1つの有料Roadを追加した。共通道路全体を延長する変更はない。古い装甲目標 `g6n0` はWarrior30/Tank31のままで、全外側の配置・重複した数値領域を再設計した候補ではない。

Warrior＋Tank、Mage＋Rangerの越境例もある。各20pt使用 / 上限24pt / 未消費4ptであり、同消費24ptの例とは区別する。12/24ptは比較用で、本編の成長予算に採用していない。

## 検証と限界

`browser-verification.json` はこのHTMLのSHA256を持ち、12例の実JS再構築・合計値・取得順・保存復元・旧版コード拒否・失敗時復帰、120入力ケース、固定/各職入力の費用表を検証する。新しい必須経路はGUARD/SHIELD/AP/元素入力を外した感度ケースでも到達を確認する。生成前にWarrior/Tankの代表ケースを確認してから他3始点へ展開した。

`geometry-verification.json` は旧点の座標・旧辺の完全保持、新規点/線の干渉チェックを記録する。技術検証と静止画比較は、実戦DPS・本人の操作感・本人採用を証明しない。現行ゲームの18点カタログ、ポイント獲得、技能選択、保存形式は未変更。

## 実際に見た参照

直接の制作参照は保存済みV8。埋め込みsprite・日本語フォント・データ・描画コードを制作中に読み、中央配置と緑/金の既存UIを保持した。

- [V8原HTML・Library版5](https://chatgpt.com/api/library/files/libfile_4957d37ab2d88191abcc3c87da682ed2/download)：データ・コード参照。中央配置、既存入力条件、保存・配分の処理、承認済みフォントとspriteを引き継いだ。
- [V8原静止画・Library版5](https://chatgpt.com/api/library/files/libfile_1385f84844b48191b39da38c269a545b/download)：2026-10-03 18:03 UTCの比較評価で実見。上段のPC全体と中段の同消費43ptの二配分を見て、既存の階層・配色と同消費比較の見せ方を確認した。動画の時刻指定はない。

V8埋め込みspriteも一覧で実見した。`shield` / `war_guard` は剣のfallbackだったため、新しいTankの装甲には実物が盾の `ward`、守りの回転には守護像の `temp_sanctuary` を選んだ。sprite自体の再描画・V8原本の変更はない。

静止画を見た時点は比較評価であり、最初からこの静止画を見て作ったという記録ではない。今回の新規外部動画参照はなく、既存職業効果の説明は `CoreClassState.kt` / `CorePlayerCombat.kt` / `CoreSkillCatalog.kt` / `CoreAffixCatalog.kt` を読んで確認した。

再生成：`build-skill-tree-specialization-v9.py` → `publish-skill-tree-specialization-v9.py`。実ブラウザ検証：専用headless Chromeの18129ポートを使う `verify-skill-tree-specialization-v9.cjs`。検証後はそのブラウザを終了する。
