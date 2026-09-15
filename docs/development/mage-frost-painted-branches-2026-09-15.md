# 霜の波紋：原画の輪郭から組む氷片（未達・継続）

## 変更

前checkpointは幾何学的な刃の束に寄っていた。R04 MatE足元氷の作者掲載GIFを
再実見し、短い割れ、途中の段差、幅のある白水色面を原画から作り直した。
Wynncraft R11 Ice Snakeの保存フレームも比較。色を合わせるだけでなく、
明るい面と暗い側面がそれぞれ大きいまとまりとして読めることを確認した。

- 独自原画3種類の輪郭に沿った薄い立体面。画像を四角い板へ丸ごと貼らない。
- 外側は異なる向きの2面、内側は1面と短い立体芯。固定7モデルの構成は維持。
- 長い幾何学的な白い芯は光線に見えたため、根元の短い暗色芯へ抑えた。
- 根元の細い線も、原画の短い片が部分的に重なる構成に変更。
- 外側28tick、内側34tick、低い根40tick。大きい尖りから先に消え、根が後に残る。
  発生の遅延と標準補間を維持し、毎tick画像を切り替える方式へ戻さない。
- 攻撃は既存の瞬間判定のまま。余韻の延長で追加ダメージや再判定を発生させない。

## imagegenと原画

imagegenスキルのbuilt-inモードを使用。R04のブラウザー表示を画風・形の参照として
新規生成。作者画像そのものはリソースパックへ取り込んでいない。

- 原画: `assets/combat-vfx/mage-v5/sources/rime-branches-v01.png`
- 全文プロンプト・モード・背景の注意: 同ディレクトリ `rime-branches-v01.prompt.txt`
- 配布画像: `server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/combat_vfx/mage_material/rime_branches_v01.png`
- 原画と配布PNGはbyte一致。Pythonで画像の描き直し、背景除去、再保存をしていない。

透明背景の要求に対して、imagegenは市松模様が焼き込まれたRGBを返した。
背景のみの編集をbuilt-inで1回試したが、そちらもRGBであり不採用。
実際のalphaがあると偽らず、元画像の青い領域にだけnative geometryを置く方法を採用。
8pxセルの全画素が青い領域だけを面にし、同じ幅の隣接領域をまとめている。
背景と氷の隙間には面がない。各面のUVは元の位置を参照し、段ごとに全画像を反復しない。
全使用領域が背景画素を含まないことをテスト。GPUのmipmapによる遠景の縁は未確認。

## 実見レビュー

新原画を使った正面投影について、読み取り専用レビューでも短い枝・欠けた輪郭は
以前より氷らしく読めると評価。一方で長い光線状の先端と細い根の接続を指摘された。
その後、立体芯の長さ・明部を抑え、根を短い描画片へ変更した。

最終の簡易正面投影:

- `.tools/rime-painted-final-front-60.png`: 1.00秒、全体の白水色面と短片。
- `.tools/rime-painted-final-front-108.png`: 1.80秒、高い片が低くなり始めている。
- `.tools/rime-painted-final-front-138.png`: 2.30秒、低い根だけが残る。

時間は予備動作を含むQA時計。実際のサーバーmetadataをVanilla Displayへ再生した
60fpsの標準補間データから投影。ゲーム映像／GPU描画／ネットワーク検証ではない。
参照より長く細い先端と低い肩の印象はまだ残る。形の改善は全技の品質達成ではない。
R04本編で確認済みの「高い尖りが減って低い青い残片になる」順序には近づけたが、
作者の全フレーム、ゲームでの手触り、CONTACT、全31参照との最終比較は未完了。

## 検証

- Python: rime 10件、投影5件成功。
- Kotlin: CoreMageChoreographyTest 11、Frost 5、Firebolt 6、CoreCombatMesh 20、計42件成功。
- 最終7モデル／15,936頂点をVanilla 26.2がパースし、標準回転とQA投影の回転が一致。
- native atlas: 151参照spriteを検出。stock-directory負例は0。
- 実表示metadataの8表示をVanilla Displayへ再生し、終了前ゼロ・残存なし確認。
- 最終生成モデルを含め `:server-minestom:jar` 成功。
- 既知の別炎素材 `pyre` の要素数超過は未修正。広範囲Mageテスト全成功とはしない。

## 確認画像の生成時間

全動作投影が遅い原因を、実行中プロセスとローカルPillow実装で確認。
`np.asarray(PIL.Image)` は `Image.__array_interface__` 内の `tobytes()` を毎回呼んでいた。
原画全体を小面ごとにコピーしていたため、配列を素材ごとに1回作って再利用するよう修正。
画像入力と配列入力の画素一致をテスト。描画の省略、フレーム間引き、別の絵への差し替えではない。
遅い旧生成プロセスは、そのコマンドとPIDを確認して停止し、修正版で再生成した。

## 処理と範囲

主なファイルは `scripts/build_mage_rime.py` と `CoreMageFrostChoreography.kt`。
原画を読む → 氷領域のgeometry/UVを組む → 各束の接地点で固定表示 →
高さの標準補間 → 大きい氷から順に終了。共有rendererやprotocolは変更しない。
不具合時は配布packのrimeモデル／UV／原画、次にMageローカル振付を確認する。
クライアント、ゲーム入力、MP/CD、攻撃判定、Warrior、UI、mainは変更なし。
ゲームの起動・操作なし。旧dirty差分をcheckpointへ混ぜない。
