# 全元素同時注入・補助供給・中央武器への収束（隔離別版）

本人の「Essentiaは一斉に」「エネルギーが少なければ遅く、多ければ速く」「注入が終わった中央武器に成立演出」を扱う。承認済み人工封印核v5/v7、紫灰台座v6、直線支柱v4、Jar v3のJSON/PNG/形状/大きさは変更しない。前版GIFも保持。既存の逐次注入は既定のまま、別版は起動設定で選ぶ。

`WorldInfusionConfluence` が実world presenterと比較traceの共通計算。元素の総量と通常素材の必要条件は従来どおり。補助エネルギーは速度の入力だけで、Jarを代替したり完成条件になったりしない。名前・生成手段・燃料・貯蔵上限・経済数値は未決定。新規アイテム/通貨/専用素材・強制日課・維持費は追加していない。

| 比較仮値 | LOW | HIGH |
| --- | --- | --- |
| 補助供給のデモ入力 | 0（ゼロでも継続） | 1 |
| 吸収波・素材吸収の間隔 | 48tick | 16tick |
| 新規Essentiaの移動時間 | 42tick | 14tick |
| 核の定常回転 | 30度/秒 | 90度/秒 |
| 中央武器へ収束する時間 | 36tick | 16tick |

0〜1の中間供給にも連続的な速度設定がある。回転は加速/減速で馴染ませ、刻印の紫は供給と注入進捗に応じ強くなる。途中で供給を変えた場合、新しい吸収波へ現在値を使い、放出済みの霧は元の寿命で到達する。ここでの比率・刻みはProjectSの比較用仮値であり、TC/Botaniaの確定仕様ではない。

各波はまだ必要な全元素を1単位ずつ同じtransactionで消費する。今回の既存費用は火3/水2/風2なので、最初の2波は3元素、必要量を満たした水/風は止まり、最後は残る火1単位。全元素が同時に始まり、既に満たした元素を余分に消費しない。未設置/遠い/空のJarは対象外。どれかが不足するとその波は全て消費せず停止する。補助供給の少なさによる停止ではない。

消費は既存repositoryへのdurable saveが成功した後だけ霧を出す。1波の全Jarとsuppliedが一度に保存され、途中の半分だけ確定する状態はない。取消し時は既存仕様のMatrix reservoirと通常素材返却を維持、Jarへ二重返却しない。再読込で装備UUID・他MOD・投入済み量を保存し、予約済み元素を再びJarから取らない。保存形式にenergy残高や新工程は追加しない。

工程は (1) 全元素同時注入、(2) 最後の霧が核に到達、(3) 周囲の通常素材を順に吸収、(4) 核の紫から中央武器へ小さな紫の結び目が収束、(5) 固定1枠の属性変性を一度だけ確定、(6) 武器の周りへ小さな成功の光、核の一度の脈動と減速。白く画面を覆わず、武器を残して見せる。武器は収束中には変性しない。取消しには成功の光/変性を出さない。中断時は新規吸収を止め、既に放出された霧だけ到達して消える。

色付き煙・収束・武器の成功光は同じItemDisplay billboard経路/共有192capで表示し、1儀式は64sampleまで。専用クライアントや新shaderpackは不要。native emission面の色だけを変え、モデル材質・UVは保護。Bloomや周辺へ色の付いた実照明は追加していない。クライアントFPS、透過順序、他shaderとの見た目は未測定。GUIクライアントや実サーバーの起動はこの更新では行っていない。

比較GIFは共通Kotlin traceから作る独立レンダー。projection/照明/液体透過は近似、中央武器は既存の仮表示アイコン。霧の座標/色/寿命、核角度/色、Jar実量、素材、変性の瞬間は同じ計算。Minecraft録画や本番統合済みと扱わない。

再現: 指定のGradle回帰test → `scripts/render-infusion-confluence.py` → `scripts/verify-infusion-confluence.py`。traceは `server-minestom/.tools/world-infusion-evidence/confluence-animation-trace.json`、画像/GIFは `.tools/world-model-preview/ProjectS-Infusion-Confluence-*`。

隔離ゲームの別版を後で動かす場合は既存のopt-in `-Dprojects.worldInfusion=true` に `-Dprojects.infusion.concurrent=true -Dprojects.infusion.energy=0` または `=1` を追加する。`-Dprojects.infusion.save=...` はこの比較専用の新しい保存先にする。通常main/本番save/公開serverへは適用しない。起動を本人へ要求するものではない。

既存launcherでは `scripts/start-world-infusion.ps1 -ConcurrentInjection -AuxiliarySupply 0 -JavaHome <Java25>`（HIGHは `-AuxiliarySupply 1`）で設定できる。別の `run/world-infusion-confluence` とプロセス記録・保存先を使い、旧試作のsaveを開かない。停止も同じ `-ConcurrentInjection -Stop` を指定する。このturnではlauncher実行はしていない。
