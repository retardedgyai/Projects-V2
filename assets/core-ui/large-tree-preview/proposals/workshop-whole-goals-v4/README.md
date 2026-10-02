# ProjectS — 645点の全体配置・配分比較 V4

全645点・47領域・5職業・15 Key・776接続を配置した、実データを使う全体試作。以前の197点の局所案を全体へ拡張した。全15 Keyは任意の末端で、取得を通過条件にしない。

- [全体と3組の配分比較](ProjectS_Whole_645_V4_Comparison.png)
- [操作できる全645点の画面](ProjectS_Whole_Goals_V4.html)
- [全体のブラウザ画面](ProjectS_Whole_V4_Overview.png)
- [全645点の配置・接続データ](candidate-graph.json)

HTMLをローカルブラウザで開く。全体→拡大、ドラッグ、点の選択→目標までの配分、払い戻し、職業／技能編成／所有MODの変更、予算48／64／80pt、通路の恩恵、配分コードの保存／復元を操作できる。職業・技能・MODの入力を変えると配分をリセットする。

## 全体の作り方

原データの351 Small・77 Notable・197通路・15 Key・5起点を一つも増減せず、全ノードのID・名称・費用・効果・条件・リスク・sourceRefを保持。座標と接続を提案内で再構成した。元の `graph.json` と過去V1～V3、旧全体V1～V4のファイルは変更していない。

47領域はサイズと枝の長さが異なる明示的な配置。47の座標形がすべて異なる。境界の共有入口と混合Notableの迂回を設け、HPを取るための吸収、氷を取るための障壁、炎を取るためのAPなどを強制しない。使える入力は実在する60標準適性の装備構成と、所有する吸命MODの明示的な比較条件から判定する。標準適性外の武器は有効化しない。

左右の点数は全体外接枠の中心で333／312。鏡写しを目的にせず、目標ごとに大小を変えた。分岐のない通路だけの連続は最大2点。全645点の構造が一つにつながり、他職業の起点やKeyを通過しなくても、現在の入力で使える育成点とKeyに届く。

## 比較例

各例の予算は48pt。表の数値はこの再配置で必要な新規取得費用で、実戦の強さではない。

|選択|使用pt|残りpt|育成の違い|
|---|---:|---:|---|
|会心へ投資|21|27|会心・倍率を育て、弱点混合点の会心側にも届く|
|貫通から静かな刃|18|30|会心を失う代償の原案を選ぶ|
|生命と装甲|16|32|装甲を拾ってHPの目標へ回る|
|障壁と仲間|8|40|自分の持つ仲間向け障壁を育てる原案|
|MPへ投資|12|36|MPの器・効率へ配分|
|MPから血の代価|15|33|同じ取得済みのMP経路に3ptのKeyを追加|
|氷と障壁|19|29|手持ちの氷・障壁から攻撃機会の原案へ|
|会心と資源|17|31|レンジャーの印入力から機会の返還へ|
|手数と範囲|18|30|アサシンの手数・範囲と会心側を選ぶ|
|既存の雷を単体へ|16|32|所有する雷MODの連鎖から単体向け原案へ|

会心無効化と血の代価は、取得費用と接続を保持したまま対応する育成値を非表示にし、Key払い戻しで復活する。血の代価の技能表は原データの基礎MP消費を同量のHPへ置き換えた比較例。効率適用後の最終消費、HP自然回復停止、直接ヒットの乗算、最終DPSを実装済み扱いにしない。

Key08／Key09の元の競合を配分と保存復元で検査する。出血・HEAL回復・弱点判定・異系統武器入力は現行catalogから供給されない。吸収は既に所有する吸命MODの比較条件のみ。防具の自由混合・artifactシリーズ、任意の魔術MODの入手や変性は、このツリーの育成と別の担当として保持する。

## 検証

- 原645点の座標以外の全フィールドが一致、元 `graph.json` がHTML内に完全一致で埋め込まれる。
- 実際の接続の交差0・重複線0・他点への65world未満の接近0。交差を隠す線の切り欠きなし。
- 60装備構成×吸命MOD有無の120条件で、有効な育成点とKeyへの到達を検査。条件を緩めず、他職業の起点を通過しない。
- PC1920×1200／スマホ430×932で全47領域、計94拡大画面の描画・クリック範囲・ラベルを検査。縮小全体と10配分例・3組の同視点比較を含む20実画面を保存。
- 実マウスによる選択・取得・払い戻し、タッチの移動・ピンチ、会心／MP育成無効の保存復元・払い戻し、競合Key、異職起点／不適性武器／不正予算の復元拒否を確認。
- ブラウザ内のネットワーク要求0・実行例外0。ローカルに埋め込んだ既存の承認済みpixel素材と日本語フォントを使用。

根拠は `geometry-verification.json`、`input-reachability-verification.json`、`topology-verification.json`、`browser-verification.json`。最小描画余白は全体表示でPC約2.14px、スマホ約0.78px。拡大して配分する画面として操作を用意した。

## 完成範囲と未実施

全645点の配置と配分UIは今回の範囲で完成。実ゲームのnative画面、戦闘効果、packet、ゲームの保存／リセットへ反映していない。15 Keyの係数・代償・共通実装は原案のまま。予算は比較用で、ゲームの習得ポイント付与規則を変更しない。main変更・merge・push・install・native compileは行っていない。

参照した一次資料：[GGG公式tree export](https://github.com/grindinggear/skilltree-export)、[GGGのパッシブ改訂説明](https://www.pathofexile.com/forum/view-thread/3186390)、[ユーザー指定のRinaorc動画](https://x.com/Rinaorc/status/2105607235602899422?s=20)。動画の短い再生と複数時点の実画面を確認し、中央の共有樹・拡大・経路強調・取得費用の表示を参照した。これらの資料・動画自体は成果物に含めない。

## 再生成

このworktreeのルートでPython（NumPy／Pillowが既存環境に必要）を使う。生成先はこのV4フォルダのみ。旧案は書き換えない。

```text
python scripts/build-skill-tree-whole-goals-v4.py
python scripts/complete-skill-tree-whole-access-v4.py
python scripts/repair-skill-tree-whole-gateways-v4.py
python scripts/summarize-skill-tree-whole-topology-v4.py
python scripts/audit-skill-tree-whole-goals-v4.py
python scripts/audit-skill-tree-whole-inputs-v4.py
python scripts/publish-skill-tree-whole-goals-v4.py
```

最後の画面作成だけなら `publish-skill-tree-whole-goals-v4.py` で足りる。接続補修は直前の生成段階を入力にするため、完成済みデータへ単独で再実行しない。既存のプライベートChrome検証セッションをポート18126で開始した後、`node scripts/verify-skill-tree-whole-goals-v4.cjs`、`python scripts/make-skill-tree-whole-v4-comparison.py` で実画面の検証と比較画像を作る。検証用ブラウザは本人の通常Chromeと別プロファイル。
