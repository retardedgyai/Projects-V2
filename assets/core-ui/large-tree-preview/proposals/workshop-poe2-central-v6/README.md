# ProjectS — PoE2中央開始部の配置案 V6

中央の余白を囲む5起点を同じ半径・72度間隔に置き、各起点から短い2出口を出す。3つ目の育成選択は初手の先で分岐する。隣の開始部へ回る接続を持ち、外側は47領域の異なる形・大きさ・枝を保持した。採用前の配置・操作プレビューで、nativeゲームへは反映していない。

## 開くファイル

| ファイル | 確認する内容 |
| --- | --- |
| `ProjectS_PoE2_Central_V6_Reference_Comparison.png` | 修正前・実PoE2・V6を、起点の円周サイズを揃えて比較 |
| `ProjectS_PoE2_Central_V6_Whole_And_Regions.png` | 全体と、同倍率で拡大した3つの外側領域 |
| `ProjectS_PoE2_Central_V6.html` | 実データ645点を操作する、単体で開けるプレビュー |
| `ProjectS_PoE2_V6_Mobile_Center.png` | スマホの中央拡大 |

HTMLを開き、「5起点」で中央へ移動する。点を選ぶと効果・入力条件・到達経路・費用が出る。「目標まで配分」で学び、取得済みの点を選んで払い戻す。職業・技能編成・所有MODは元の入力プロフィールを使い、配分の保存コードはこのプレビューだけに適用する。

同じメイジ・技能編成・所有MOD・48pt予算で、MPへ進む例は17pt、氷と障壁へ進む例は25pt。育成する目標と残るポイントが変わる。血の代償は別の効果抑制例として保持し、取得済みのMP等の効果を無効にしても点と接続を残す。払い戻しで効果が戻る。最終DPSや最終消費量の計算は含まない。

防具の全職自由混合・artifact別series候補、任意の装備MOD変性や専用MODの検討とは別に、現在の手持ち入力へ育成ポイントを配分する。ツリーで新しい技能・MOD・異系統武器の使用許可・不足入力を付与しない。

## 実参照と変更の根拠

[GGG公式PoE2データ](https://github.com/grindinggear/poe2-skilltree-export)と、[操作したPoE2ビューア](https://cvenzin.github.io/poe2-skilltree/)を確認した。保存したビューア画像は0.5.1。公式exportは別バージョンの可能性を残し、両方の版が同一とは扱っていない。

公式データの開始6位置は半径1443〜1491、角度はほぼ60度間隔。各位置を2クラスが共有し、ascendancyを除く通常ツリーの初手は各2本だった。実画面では中央の肖像領域、その縁の起点、短い分岐、外側の閉路・梯子・菱形・三角形・扇状の小枝を見た。職業切替・拡大と、レンジャーの初手を選んで0→1pt、払い戻して1→0ptになる操作も実施した。

ProjectSの5職では、共通の中央領域を囲む72度間隔の配置へ適応した。PoE2の効果やポイント数はコピーしていない。中央を不規則な網で埋めた未共有V5は参照との相違を示す比較材料としてのみ残した。

比較は実ブラウザ画像の切り出しと表示サイズの調整で作成した。配線やノード位置を比較画像上で描き直していない。中央比較の不規則な旧案は平均起点距離、参照は保存画像で確認した円周を基準にした。外側3領域は同じ画面寸法と倍率0.55で撮影した。

## 検証結果

- 645点のID・効果・費用・条件は元データと一致（配置XYだけ変更）。47領域、5起点、15の末端Key、784接続。
- 起点は半径900・72度間隔。中央の表示領域は半径858。中央内を通る配線なし。
- 実ポリライン8760区間の交差・重なり・他の点への近接は0。47領域の形は異なる。連続する次数2のRoadは最大2点。
- 120入力条件で有効点と入力のあるKeyへ到達。別職の起点やKeyを通路に要求しない。
- PC/スマホ22画像・94領域の表示、マウスとタッチ、保存復元、Keyの代償と払い戻し、競合、不適合武器の拒否を確認。
- 最終の中央円とラベル変更後は94ラベル確認・22画像を更新してPASS。2026-10-03の短い再起動確認もPASS。

元のグラフとV4以前の成果は保持。native画面・実戦効果・packet・ゲーム保存は未実装。compile、ゲーム起動、重いレンダリング、main変更、merge、push、installは実施していない。

## 実装・再現・調査の入口

最重要ファイルは`candidate-graph.json`と`ProjectS_PoE2_Central_V6.html`。生成はrepoの`scripts/`で、`build-skill-tree-poe2-central-v6.py` → `complete-skill-tree-poe2-central-road-v6.py` → `repair-skill-tree-poe2-central-inputs-v6.py` → `finish-skill-tree-poe2-central-topology-v6.py` → `publish-skill-tree-poe2-central-v6.py`の順。

崩れたときは`geometry-verification.json`、到達や入力条件は`input-reachability-verification.json`、画面や操作は`browser-verification.json`と`resumed-smoke-verification.json`から見る。参照の実測・実操作記録は`reference-study.json`。Road移動・開始部の編集記録は`central-authoring.json`。

ブランチは`play/gyai/skill-tree-data-preview`。今回の保存はこのブランチのローカルcommitまで。人間Creatorの採用判断とゲーム内Manual Smokeは未実施。

PoE2のデータ・参照画像の権利はGrinding Gear Gamesに帰属する。保存画像は配置比較の参照に使用し、ProjectSのゲーム用アセットへは取り込んでいない。
