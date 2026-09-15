# 火炎弾：板状の炎から、厚い弾頭と細い尾へ（未合格）

## 範囲と現時点の判定

Mage 10技を作り直す目標の一部。Garden の未達点を消したり、
火炎弾一つを全体の完成条件に置き換えない。全10技とも視覚的な受け入れは未完了。
今回の成果は火炎弾の形・構成・作画・動作・命中確認を分離した実装 checkpoint。
ゲーム起動、操作、共有描画基盤、クライアントコード、判定、威力、消費、CDは変更しない。

## 最新変更：主弾の縮小残りをなくす

R09本人動画を再度コマ送りし、9.083333秒では短い主弾があり、
9.133333 / 9.183333秒ではその主形がなく小片が残ることを readyState=4 で実見した。
この区間に大きく開く爆発は確認できないため、そのような演出は足さない。
作者内部のモデル構成や全フレームの滑らかさを推測で確定しない。

- 主弾は一つのまとまった体積に変更。2tick保持し、次の1tick補間で終端へ。
  前版の長い「同じ弾頭を小さくする」区間をなくした。
- 周辺2片は主弾の縮小コピーではない別形状。同じ場所へ縮むのではなく、
  射線に直交する別方向へ離れ、主弾より後まで残る。判定後の小片3個は別のCONTACT処理のまま。
- 放出5体＋命中3体、同時表示上限8を維持。準備を含む全体の生成数は空振り6体／命中9体。
- 各細面に絵全体を反復していたUVを修正し、外周を通して使う構成へ。
  元画像は変更せず、白黄の広い面と橙の縁が読めるようにした。
- 参照より輪郭が均等というレビューを受け、前側の層をモデル座標で少しずらす。
  大きさや周囲の爆発は増やさない。

独立の読み取り専用レビューでは主面と小片への切替を改善と判定。
修正後の `solar-bolt-layered-iso-18.png` / `solar-bolt-layered-eye-18.png` / `solar-bolt-layered-iso-30.png`
も主担当が実見した。主弾後の小型コピーはなくなったが、これで全体の品質を合格とはしない。
主観・側方での見え方の差、実ゲーム背景での読みやすさ、連続動作の最終視覚確認は残る。
全10技・全参照との最終比較も未完了。次の確認では同じ数値の微修正を反復せず、
残る差を実画面／適切な参照で特定する。

新しいbitmap生成なし。変更元は引き続き `build_mage_solar_bolt.py` と
`CoreMageFireboltChoreography`。以下の実装・検証節は前回checkpointの記録。

今回の検証：生成側7件 PASS、Kotlin対象38件 PASS（下記と同じ内訳、旧frost test 1件を含むがcommit対象外）。
Vanilla実パーサー7モデル受理、負例拒否。実metadataをVanilla Displayでreplayし、
空振り6体／命中9体の生成・補間・終端を確認。同時8体以下、除去前ゼロスケール、残留なし。
`solar-bolt-fringe-native.json` / `solar-bolt-fringe-hit-native.json` を使用し、
最終モデルの全52フレーム投影は `solar-bolt-layered-motion.gif` / `solar-bolt-layered-hit.gif`。
これらはGPU・地形遮蔽・ネットワークを含まない確認用出力。ゲーム起動・操作は行っていない。

## 作る前に再確認した根拠（前回checkpoint）

既存の [31件の参照台帳](mage-reference-census-2026-09-15.md) を前提に、
R09 [MatE本人の Solar Scepter 動画](https://www.youtube.com/watch?v=RQkbCenYNQc) を再観察。
広告終了後に消音・停止、UIキー操作で移動し、次の両フレームを readyState=4 で実見した。

- 8.866664秒：短く厚い角形の黄白の弾頭。後ろの橙線はずっと細い。
- 9.233330秒：主弾と長い尾が消え、対象付近の少数の小片が残る。
- 掲載画像 `.tools/mate-solar-gallery-reference.png`：白黄の広い面を桃橙が包む。
  杖自体、宣伝文字、23秒台の大型ビームは通常弾へ転用しない。

この2フレームから全動作、内部モデル、シェーダー、滑らかさは証明できない。
新規件数に重複計上しない。既存の瞬間判定を遅い飛翔体へ変える根拠にも使わない。

## 実装（前回checkpoint）

- `CoreMageFireboltChoreography` が火炎弾のみ担当。
- 準備：小さな熱塊。既存の準備時間を使用し、1tickの境界条件も閉じる。
- 放出：短い白熱芯、後方の外殻、主線、途切れた短片の4 Display。
  放出時点で既存の確定射線末端にある。偽の遅延命中を追加しない。
- 芯と外殻は同じ弾頭の異なる部分。八角断面と段差のある閉じた体積。
  芯を先に消し、外殻は2tick長く残す。別の完成PNGを繰り返し出さない。
- 尾は後端が前端へ追いつきながら細くなる。長軸は常に確定射線内。
- 命中：本当の CONTACT のみ、異なる方向へ小片3個。空振り末端に爆発を置かない。
- 全部1tick補間・同じモデル/同じDisplayを保持。除去前にゼロスケールへ到達する。
- 新しい作画は byte-identical な元画像をそのままUV参照。Pythonでbitmapを書き換えない。

重要な処理の流れ：`CoreMageChoreography` → `CoreMageFireboltChoreography` →
既存 `CoreCombatMeshes`。壊れた場合はまず専用クラスの `parts` / `pose` と
`scripts/build_mage_solar_bolt.py` のモデル登録・UVを見る。
元の `build_mage_fire.py` と、既存の未整理のv4資産には触れない。

## 視覚レビューと修正（前回checkpoint）

最初の native metadata 投影では、参照より赤い結晶弾に寄っていた。
独立の読み取り専用レビューでも「長い・暗赤の本体が強い・尾がレーザー的」と指摘。
次の大きな修正を適用：

1. 弾頭長を1.25→0.85 block、断面の実幅を約0.54→0.73 blockへ変更。
2. 側面UVの暗赤の比率を下げ、後端にも白熱芯を露出。術者から赤い蓋だけに見えないよう変更。
3. 主線は一本維持し、もう一本の線は大小4つの途切れた短片へ変更。

最終投影を主担当と読み取り専用レビューで比較し、短く太い頭、後面の芯、尾の短片化は改善。
ただし内部の細かな溝と明暗がまだ強く、参照のまとまった白黄の主面より「燃える固形物」に見える。
末期に同じ塊が小さく残るため、次は面がほどけて消える構造を作る必要がある。
その動作に精度の高い参照が足りない場合は再調査する。縮小値の微調整だけを繰り返さない。
今回も **未合格**。最終3静止画だけから滑らかさを確定していない。
実ゲームの地形・背景・遮蔽・ネットワークを含む確認、および全31参照との最終比較は未完了。

## 検証結果（前回checkpoint）

- `scripts/test_mage_solar_bolt.py`：5件 PASS。元画像同一性、モデル再現、UV範囲、軸方向の形状範囲、登録。
- 対象Kotlinテスト：38件 PASS（CoreCombatMeshTest 20、CoreMageChoreographyTest 11、
  CoreMageFireboltChoreographyTest 6、既存ray範囲回帰1）。現在worktree上の実行であり、
  うち旧frost test 1件とその旧変更はこのcommitへ混ぜない。
- 短い準備時間1tickのゼロスケール終端不備を検出して修正。
  旧「全Mage CONTACTは1個」のテストは火炎弾だけ小片3個に合わせ、他9技の制約を保持。
- Vanilla 26.2実パーサー：6モデル受理、invalid-axis負例は拒否。
- Vanilla実atlas探索：参照combat texture149枚を発見。GPU描画の検証ではない。
- 実サーバーmetadata→変更していないVanilla Display補間のreplay：空振り5体、命中8体。
  モデル差替えなし、除去前ゼロスケール、残留Displayなし。
- コード上、HP/威力/命中/MP/CD/入力・射線確定・共有描画基盤は変更なし。

QA出力（生成物なので `.tools/`、commit対象外）：

- `solar-bolt-final-native.json`、`solar-bolt-hit-final-native.json`：実metadata由来の60fps補間データ。
- `solar-bolt-final-iso-18.png`、`solar-bolt-final-eye-18.png`、`solar-bolt-final-iso-33.png`：実見した修正後静止画。
- `solar-bolt-final-motion.gif`、`solar-bolt-hit-final.gif`：全52投影フレームの確認用出力。
  生成済みであること自体は全動作を目視合格した証拠ではない。

## 作画の生成記録

imagegen スキルの built-in tool を使用。CLI/API fallback は使っていない。
入力画像は上記R09の掲載ページ表示記録で、画風・材質の参照のみ。編集対象ではない。
保存先：`assets/combat-vfx/mage-v5/sources/solar-bolt-faces-v01.png`。
同一画像をRPの `assets/projects/textures/combat_vfx/mage_material/solar_bolt_faces_v01.png` へコピー。
最終プロンプト：

```text
Use case: stylized-concept. Asset type: production pixel-art UV texture atlas for an ORIGINAL compact Minecraft fire spell projectile, not an illustration. The reference image is style/material reference only: large coherent ivory-hot planes, crisp blocky peach/gold edges, restrained brick-rose shadow; do not copy the weapon, words, layout or symbols. Output one completely opaque square atlas filling the entire image edge to edge, divided exactly into FOUR equal square quadrants with no gutters or borders between the quadrants. Each quadrant is flat orthographic texture painting, visibly coarse 16 by 16 logical pixels enlarged with hard nearest-neighbor square edges, no tiny noise or antialiasing. Top LEFT quadrant: projectile side surface, dark muted brick-rose at left edge, flowing broad coral and amber angular bands leading toward a large ivory-yellow hot region at right edge; three blocky notches give the energy a forward direction. Top RIGHT: different side surface, mostly warm peach and gold with one connected pale ivory region on the right half, sparse dark rose lower edge; no repeated identical shapes. Bottom LEFT: front impact face, large asymmetrical pale ivory connected center covering 60 percent with thick angular yellow and peach perimeter and a few deliberate darker coral pixel notches. Bottom RIGHT: rear face, subdued coral/rose and orange with a small amber inner region, clearly darker than the other faces. These are textures for a faceted three-dimensional solid, NOT four finished icons. Every single pixel belongs to painted hot material; no transparency, checkerboard, background, frames, lettering, weapon, 3D render, halo, bloom, smooth gradient or fine-resolution detail. Keep the main illuminated regions broad and calm, Minecraft-native chunky pixel clusters.
```
