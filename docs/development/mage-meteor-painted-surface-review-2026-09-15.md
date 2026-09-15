# 流星：原画を保持する薄い曲面への方式変更

Verdict: **FIX-FIRST / WIP**。Mage全10技と最終参照照合は未完了。

## 前のturnと判断

直前の応答は説明のみで、実装上の進捗なしと分類した。現行HEADとdirty状態を再確認。
旧v4の未整理差分、Warrior、UI、ゲーム判定、Client、mainは変更しない。

旧03の側面を主担当が再表示すると、褐色の大きい壁と階段状の突起に見える。
解像度・厚みの値を足すだけの修正は打ち切る。

## 今回実見した参照と限界

- [R12 Wynncraft Mage実プレイ](https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s)
  の保存済み `wynn-meteor-slow-review.png` を再表示。155.55の燃焼の尾、155.70の
  黄白の接触、155.84の中央が抜けた橙の前縁、155.99以降の小片を確認。
  **この正面映像から作者のメッシュ構造やbillboard方式は特定できない。**
- [MatE Solar Scepter作者動画](https://www.youtube.com/watch?v=RQkbCenYNQc)
  の保存済み13.03–14.16、27.35–33.74、30.08–32.32の観察シートを再表示。
  小さい発射から命中周囲の光片への変化は読めるが、主役の炎の側面を判断するには遠すぎる。
  流星の奥行きの決定根拠には使わない。
- [Wynncraft公式Frumaページ](https://wynncraft.com/fruma) は独自のVFX animation導入を説明。
  これは流星の内部実装の証拠ではない。公式大技動画は今回0.69秒で停止したままの複数キャプチャとなり、
  連続動作の参照として不採用。公式Mage紹介ページにも追加の流星動画は見つからなかった。
- 既存31件の再照合と不足確認。重複フレームや停止画を件数へ加算しない。

## 01制作上の変更

参考で見える炎の面と空隙を、原画から四つの単色へ置き換え、厚い殻へ押し出したことが
主な誤りだった。新方式は作者の実装の模倣を主張せず、上記の**見える特徴**を実装する試作。

1. 原画 `meteor-impact-atlas-v01.png` のRGBをそのままUVとして使用。
2. 市松背景にはモデル面を作らない。原画像の各texelを検査して、暖色領域の矩形だけを使用する。
   元PNGの書換え・色量子化・背景を煙として扱うことはしない。
3. nativeの0/22.5/45度の薄い面を繋ぎ、一つの曲がった圧力面を形成。
   表裏に同じ描画があり、厚い側面や褐色の蓋は存在しない。
4. 不均等な三領域は同じ原画座標で配置され、既存のpersistent Displayの補間で展開・分離。
   熱い形→裂けた形→小さい燃え残りは別の描画を使い、完成した炎全体を茶色にする処理を撤去。
5. 旧 `blast_volume / lobe_volume / lobe_partitions / blast_surface` と単色殻の生成処理を削除。

厚みを増やす方向から変えたが、薄い曲面でも斜め・側面で板として見える可能性はある。
原画UVを保持しただけでWynncraft/MatE水準と判定しない。

## imagegenの試行

imagegenスキルを使い、既存原画の背景だけを透過する編集を一回実施した。
結果は1774×887のRGB画像でalphaがなく、市松模様が残ったため**不採用**。
RPへ入れず、元原画のbyteも変更していない。

Prompt（built-in tool）:

> Use case: background-extraction. Image 1 is the EDIT TARGET, our own meteor-impact sprite atlas, not a style reference. Remove ONLY the baked grey checkerboard from every part of the image, including the holes between flames and the empty space between cells. Output an RGBA PNG with a GENUINELY TRANSPARENT background, not a painted checkerboard. Preserve all orange/yellow/brown flame and debris pixels and their positions, preserve the exact 4-column by 2-row eight-frame atlas layout, the original aspect ratio, the jagged pixel-art edges, the flame silhouettes, palette and shading. Do not redesign, thicken, smooth, add glow, add outlines, add a grid, add words, add labels, or shift/crop/scale individual frames. Keep the entire original canvas. Intended use: the exact artwork will be UV-mapped onto thin curved native Minecraft effect surfaces; alpha must work correctly.

## 検証と残課題

- Python対象8件成功（44.902秒、旧殻生成の削除前のrun）。削除後の最終runと描画確認は追記する。
- 主面3領域はhotで328 / 478 / 296 elements、他4つも既存500未満。
- 新テストは全UV矩形内の背景除外、20色超の元絵の色保持、面厚、native回転値、
  出力再現、3領域の被覆、Kotlinの実pivotとの一致を確認。旧「殻の深さ」テストは要件ではないため撤去。
- これらは素材や配信構造の検査であり、美術・動作の合格ではない。

最重要ファイル: `scripts/build_mage_meteor_surface.py`。
原画 → 背景を除くnative面/UV → RPモデル → Kotlinの表示transform → Vanillaの補間 → 投影レビュー。
画像なら `surface / drawing`、継ぎ目なら `centers / meteorFragmentCenters`、動きなら
`CoreMageChoreography.pose` を最初に見る。実ゲームの光・GPU・通信・feelは投影では保証しない。

## 01の描画レビューと02の修正

01はPython8件成功（削除後36.147秒）、Kotlin対象29件成功（3分50秒）。
未改変Vanilla Displayによる21ストリームの補間/scale 0後の削除を確認。
主担当はeye42/48/54、side48を実見し、読み取り専用レビュアーも同じ出力とR12を実見した。

- 正面の原画の明暗・炎塊の輪郭は旧03より明確に改善。単色殻へ戻さない。
- **側面は不合格**。主形が細い縦線として失われる。
- **消散は不合格**。暗い描画まで背景判定で落とし、さらに二重の縮小で橙の小さい点になっていた。

02では単なる厚み追加ではなく、前後の曲面を組み合わせて標的を囲う圧力面へ変更。
元の追加の左右の炎と浮遊片を、低めの後面三領域へ置き換えた。表示数は同じ
primary 8＋secondary 1、moving Display 7で、共有rendererや描画方式のglobal設定は変更しない。
前後の面は外端で接し、不透明な蓋はない。左右、前後、上の部分がそれぞれ外へほどける。

背景除外は「明るい赤だけ」という条件をやめ、無彩色の市松と暗い暖色の描画を区別。
元絵の2×2 texel全てが描画である場合だけnative面を置く。これは輪郭のgeometry判定であり、
PNGの再描画・縮小・色量子化ではない。拡大原画の16 texel未満の孤立領域は面にしない。
前後の主要モデルはそれぞれ256 / 331 / 218 elements、接触284、全て既存500未満。

冷却片は6tickまで輪郭の大きさを保ち、その後に消す。断片の中心が全体中心へ縮んで
吸い込まれる挙動をやめ、各中心の位置を保って残った描画だけを縮小する。
向きを変えたときは曲面の奥行きと分離方向も同じだけ回り、命中位置/判定/MP/CDは不変。

02のPython9件成功（53.610秒）。前後のgeometryの対応と、元絵の暗色部分が冷却時も
含まれることを追加検証。最終の投影・隔離検証結果は以下に追記する。

## 03–04：側面の主形と冷却段階

02の投影では、左右の立ち上がりは増したが、上部の面が横から消えていた。
03で後面中央の領域を、奥行き方向に向いた固定の曲面の塊へ変更した。
これは全爆発の絵を回転再生する処理ではない。モデルの頂点と面を組み替え、
nativeで有効な回転だけで組み立てた一つの構成部分。Display数は増やさない。

主担当と読み取り専用レビュアーが03のeye48/54/60、side48/54を実見。
正面の描画を保ち、側面にも左右の立ち上がり・中央の抜け・上部の炎塊が見えることを確認。
ただし消散で橙の破片が長く残り、元のアーチの位置関係も強く残ると判定した。

04は消散素材の選択を、まだ橙の炎が大きい原画frame 5から、実際の燃え残りのframe 6へ変更。
命中約0.3秒後に切り替え、全画面へ色フィルターをかけたり、原画を灰色に塗り替えたりしない。
主担当が04のeye54/60、side54を実見。橙の大きい残片から暗い小片へ変わった。
一方、灰色の裂けた面の量、部分ごとの離散、3Dの見え方と全動作にはまだ比較の余地がある。
**全参照水準の達成は未証明。FIX-FIRSTを維持する。**

## 検証範囲の区別

- Kotlin対象29件成功（02、3分46秒）。旧frostテスト1件は作業ツリーに含まれるがcommitから除外。
- 最終Kotlinの表示データを未改変Vanilla Displayで再生し、21ストリームの補間と消去を確認。
  `.tools/mage-native-painted-surface-02-60fps.json`。03/04はRPのモデル/素材選択だけの変更で
  Kotlinの表示transformは同一のため、このデータとその時点の実モデルから再描画している。
- 03のPython9件成功（41.049秒）。stageした追跡ファイルだけの隔離検証も9件成功（38.129秒）。
  02の最初の隔離runはコピー対象から `eruption_*.json` が漏れて失敗。追跡済みの対象を補い9件成功（44.508秒）。
  元repoのコードやテストをこのコピー漏れに合わせて変更していない。
- 04の最終Python/隔離検証結果は下記。投影は `.tools/meteor-painted-surface-04-*`。
- ゲーム起動・ユーザー操作・GPU/照明/通信・音の検証はしていない。構造検査を美術の合格にしない。

### 最終保存時の証拠

- 04 Python9件成功（35.906秒）。最終stageだけを `.tools/stage-painted-surface-04/tree` へ
  取り出した再現テストも9件成功（35.974秒）。旧v4のuntracked helperへの依存なし。
- 最終GIFは360×286、166 frames、2770ms。実表示データとRPモデルの投影であり、
  映像へ光や絵を後付けしていない。`.tools/meteor-painted-surface-04-motion.gif`。
- `git diff --cached --check` 成功。27ファイルを限定stage。`.tools/`、`.kotlin/`、
  旧frost変更、その他の旧v4差分は混ぜない。main変更・merge・ゲーム起動なし。
- 最優先の未達確認は、燃焼→黒い片への切替が速すぎないか、灰色の残りの不足、
  破片が独立して崩れる動作、別角度と背景での読みやすさ。全10技は引き続き未完了。

再生成: `python scripts/build_mage_meteor.py`。
対象検査: `python -m unittest discover -s scripts -p test_mage_meteor.py` と
`:server-minestom:test --tests dev.projects.server.coreloop.CoreMageChoreographyTest --tests dev.projects.server.coreloop.CoreCombatMeshTest`。
