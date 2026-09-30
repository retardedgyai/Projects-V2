# 核の最新比較：人工の封印装置と紫光

本人の最新版指定に合わせて、造形は自然な割岩から意図して加工・拘束した石の装置へ変更。左右対称の重い石殻、精密な貫通孔、規則的な彫刻、四隅の金属拘束具、開口内の封印体を組み合わせた。核の大きさと鋭い直線支柱を維持する。変更する物体は核のみ。支柱はv4、中央台・材料台・Jarはv3を保持する。

実装は [infusion-v5](../../assets/model-lab/infusion-v5/README.md) に隔離。3つのnativeモデル/3つのitem定義/手描き16px材質/発光アニメーションを含む。ゲームruntimeの構造判定・装備・Jar取引・霧・保存は変更しない。**新モデルのワールド表示は未接続**。先にモデルを本人へ見せる段階であり、ゲーム操作確認は依頼しない。

| 表現 | 今回の実アセット | 未確認・制約 |
|---|---|---|
| 石の通常照明 | emission=0、標準26.2モデル | offlineの環境光係数は近似 |
| 紫の局所自発光 | 刻印/内面だけ emission=15、shade=false | 実GPUの最終明るさ未確認 |
| 作動時の脈動 | native PNG animation、24frame/2tick | 儀式状態とのidle/active切替未接続 |
| 開口内の柔らかい光 | native透過sprite、モデル内の両面quad | native透過ソート/他shaderpack互換未確認 |
| 全体Bloom | 未実装 | 既存clientにBloomパスなし。新shaderpackを入れない |
| 床/壁の紫照明 | 未実装 | 自発光や固定brightnessと異なる機能 |
| 発光なしfallback | 同じ形の `core_unlit` | pack無しでは専用形状は出せない |

核は1つのItemDisplayで表現でき、pulseはtexture animationなので追加entity・毎フレームのserver metadata送信は不要。状態切替時に既存 `withItemModel` でitem modelを選ぶ契約。これは必要entity数の設計値であり、今回の新モデルを実clientでFPS計測した結果ではない。

標準26.2 shaderとnative parserをread-onlyで確認。現在のFabric clientはMinecraft26.2、Java25、loader0.19.3、API0.157.0+26.2。専用Bloom renderer/Iris依存はない。core shaderをpack全体へ差し替えないため、この試作自体は全体描画の互換を変更しない。第三者shaderがnative emission・animated alphaをどう扱うかは未検証。26.2以外への互換も未検証。

検証：未改変26.2のparserで3モデル135elementsを読み込み、28発光elementsの値とshade=false、石の0、不正な-1/16拒否を確認。native animationのフレーム・透過範囲・周期、全状態同形、実貫通孔、旧版28ファイルのSHA-256保護、GIF24frames/2.4秒/446,074bytesを確認した。旧v3/v4モデルの検証も再実行して成功。

画像は実JSON・UV・PNGを読むoffline rendererで、光の全体Bloomや周囲照明をCPU画像へ焼き込んでいない。実GPUのスクリーンショットではなく、暗所の照明は近似。GIFはPNG内の実24frameを100ms刻みで表示するためnative interpolateの中間フレームは省略している。

以前のKotlin runtime checkpoint `0ce8647d9640445cfe117ed5abf9773f9821dba9` の60 tests/0 failures、compileKotlin/installDistの結果を保持する。今回はKotlin/active packを変更していないので、その60件を新規実行済みとは報告しない。新しい検証はモデル/発光経路/アニメーション/原本保護に対するもの。サーバー・GUIclient・公開環境を起動/変更しない。

再現はv5 READMEのPython/Java手順。画像・GIF・provenance・native parser出力は `.tools/world-model-preview/`。ソースZIPへv5と検証記録を含め、旧版の比較も残す。TC写真や外部モデルのバイトは再配布しない。
