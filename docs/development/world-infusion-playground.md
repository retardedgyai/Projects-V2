# ワールド祭壇の独立試作

基準は remote main と確認された `af39acdd0c6451ac98f4d229b4053ff82ca5418d`。
ローカル branch `play/gyai/world-infusion-altar-current`。main / remote の変更、既存branchの削除は行わない。
HTMLの祭壇は過去の比較・取引検証記録として保護する。今回の成果はMinecraftの世界内で操作するKotlin実装。

## 範囲

`-Dprojects.worldInfusion=true` で本体の起動入口から独立ワールドサーバーを選ぶ。
通常core-loopの起動、core-loop/旧魔術の保存ファイル、既存プレイヤーの持ち物は開かない。
プレイヤーごとの私有インスタンス内に、持ち物から祭壇キット・外側台座・Jarを床へ右クリックして設置する。
メニューを開いてクラフトせず、中心装備と周囲素材を手に持ち、実物の台座を右クリックする。

中心台座の頭上にMatrix、四隅に支柱、外側に副素材台座。Matrixを筆記杖でクリックすると起動音と世界粒子が出る。
設置された近くのJarの実残量が減り、そのJarからMatrixへ元素色の霧が流れる。
元素を全量吸収 → 副素材を一つずつ吸収 → 中心の同じ装備の0枠のみ変性 → 空手の右クリックで回収。
不足情報はActionBarの補助だけ。祭壇の位置・工程の主表現はワールド実物。

## 仮レシピ一件：霜転の調律

- 中心：現行 `CoreStoredGear` / `CoreGearIdentity` を使うT2 MAGIC武器。固定index 0が `projects:flame`。
- 周囲：T2の通常鉄インゴット×2、布×1、加工石×1。方角指定なし。
- 物理Jar：火3 / 水2 / 風2。
- 結果：index 0だけ `projects:glacial-attunement`（仮）へ。火属性値6 → 氷属性値6。追加の火力予算なし。
- UUID、製作者、Tier、品質、装備Lv、強化+4、枠数、元MODのUUID/roll/Tier、他のMODと破損状態は保持。
- 通常抽選の `definitions` へ加えず、祭壇限定定義として既存ICE statへ接続。通常ドロップ/再抽選候補は増やさない。
- 調律済み装備へ再適用や別枠の調律は不可。万能reroll装置にはしない。
- 専用魔術素材・星環/深殿/ASTRAL費用・パズル研究gateは不要。
- MAGIC限定性質の最終デザインやバランスは未確定。これは実装確認用の属性交換一件。

最初の装備と材料、充填Jar三つは新規試験worldのfixtureであり、無償供給を本番の経済仕様にしない。
Jarは容量とIDを個別に持ち、型ごとの1瓶制限ではない。試験kitに三瓶あることは本番表示数/容量上限ではない。
設置されていないJarの残量は使わない。範囲6ブロック・同一階層・18tick/一段階は仮チューニング。

## ローカル起動

Java25を指定する。buildは1workerで行う。

```powershell
$env:JAVA_HOME = 'C:\Users\xgaiz\Documents\Codex\minecraft-runtime\temurin-25\jdk-25.0.4.1+1'
.\gradlew.bat :server-minestom:installDist --offline --no-daemon --max-workers=1 --no-parallel '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2' '-Pkotlin.compiler.execution.strategy=in-process'
.\scripts\start-world-infusion.ps1 -JavaHome $env:JAVA_HOME
```

Vanilla **26.2** のクライアントから `127.0.0.1:25585` へ接続する。pack配布は別port25586。
既存サーバーを止めたり、既存クライアントの接続を切り替えたりはしない。
resourcepackを受け入れると原寸16pxの手描きtextureと独自cuboidモデル。拒否してもnative block/ItemDisplayで操作できる。
新規試験saveは `server-minestom/run/world-infusion/config/projects/world-infusion-lab`。

停止は `scripts/start-world-infusion.ps1 -Stop`。記録した自分のPID・開始時刻・Javaパスが一致するときだけ止める。

## 一周の操作

1. 空の床へ祭壇キットを右クリック。床中心 `(0,40,0)` を使うなら、中心台座は `(0,41,0)`、Matrixは `(0,44,0)`。
2. 外側台座を四個設置。例：床の `(3,40,0)` / `(0,40,3)` / `(-3,40,0)` / `(0,40,-3)`。
3. 手持ちの鉄×2・布・加工石を台座へ各一つ、武器を中心へ載せる。
4. 火・水・風Jarを近くの床に置く。例：`(-4,40,4)` / `(0,40,4)` / `(4,40,4)`。遠いJarは利用不可。
5. 筆記杖で頭上のMatrixをクリック。Jarの液面低下と粒子を見回す。副素材は元素が全部入るまで残る。
6. 副素材吸収と中心品の変性後、空手で中心台座へ右クリック。回収した装備のtooltipでUUIDとMODを比較する。

空手で素材を回収。しゃがみ＋空手で空台座/Jarを撤去。稼働中の素材・中心品は取り出せない。
稼働中もJarだけ撤去できる。必要供給がなくなれば安全停止し、置き直してMatrixへ筆記杖で再開。
しゃがみ＋筆記杖で取消し。消費済み通常素材は試験inventoryへ返却、吸収済み元素は物理Matrixのreservoirへ残る。
残った素材・中心品は台座に保持。取消し後は不足した台座へ素材を載せ直す。完成後の取消しは不可。

## 保存・整合

一つのチェックサム付き原子的envelopeに、現行 `CoreAccountCodec` の装備/通常素材と、台座escrow、Jar ID/座標/量、供給済み量、工程を保存する。
保存成功後だけ、ワールドの見た目とinventory投影を更新する。故障時に古いデータをfixtureで上書きしない。
nonce付きクライアントAPIや共有protocolは増やさない。接続中の私有worldの実ブロック座標、MAIN hand、6block操作距離をserverが検査する。
再読込は同じ状態から復元。中心/出力/手持ちの装備所有位置は一つ。回収の連打では二個目を作れない。
切断時はその私有instanceを破棄するが、設置と残量はsaveから復元する。公開サーバーの自動生産ではない。

取消しreservoir、事故なしの補充待ち、Jar優先順、距離/時間/予算はProjectS試作案で、TC6の正確な取消し/距離仕様とは断定しない。
TC6参照の確定部分は、中央品＋周囲副素材＋近くのEssentia容器、Matrixをcasting toolで起動、**Essentia全部を先に吸収してから副素材**。
作者資料：[Infusion](https://github.com/Azanor/thaumcraft-beta/blob/master/en_us.lang#L1474-L1492)、[InfusionRecipe API](https://github.com/Azanor/thaumcraft-api/blob/master/crafting/InfusionRecipe.java)。

## 統合担当へ

主処理：`WorldInfusionState` / `WorldInfusionRules` → `WorldInfusionRepository` → `WorldInfusionServer.WorldInfusionGame`。
起動入口だけ `ProjectSServer.kt` にopt-in分岐。`CoreAffixCatalog.kt` は通常抽選リスト外の仮定義一件。
`CoreAccountService` / `CoreLoopMenus` / 星環退役処理 / protocol / Particle Framework は変更しない。
resourcepack追加は `assets/projects/{items,models,textures}/infusion` と `index.txt` の列挙のみ。承認済み工房美術は改変しない。

旧 `aspect-research-slice` にはowner+instanceが検査される設置Jar機構があるが、kind-keyで1瓶/型、資源はplayerごとの旧stock。
今回の主基準mainにはその機構が無いため、一括cherry-pickや旧save移植はしない。
本編への次段階：現core装備/通常素材の台座escrowを同じcore ledgerにtransactionとして組み込み、owner/access/rangeを共有ワールドに接続する。
その時に今回の独立world envelopeを本番saveとして採用しない。恒久知識とノート予算は別のまま、祭壇gateにしない。
今回のCoreStoredGearを本編に持ち込む/既存装備をこの試験worldへ移す機能や市場/取引の調整は追加していない。
深殿とASTRAL退役に独立。星織り師のコンテンツは触らない。

## 検証段階

コンパイル、6件のモデル/保存edge-case、新規実Minestomワールドruntime、既存MOD/クラフト/UI回帰とローカル起動を検証する。
詳細は同梱の検証記録。GUIクライアント操作・実画面の最終feelは別段階で、人間CreatorのManual Smoke。
実クライアント未確認の段階ではpixel texture画像やサーバー状態をMinecraftスクリーンショットと呼ばない。

2026-09-30 UTCの結果：`compileKotlin` / `installDist` 成功、**51 tests / 0 failures**。
内訳は新規7件（保存6＋実Minestomの手持ち/ブロック/粒子/回収1）、既存MOD13、クラフト12、UI19。
ローカルport25585で26.2/protocol776のstatus応答、port25586でpack HTTP/CRC、5モデルと4枚16px textureを確認。
512MB heap上限、自分の起動server PID36792はスモーク後に専用停止手順で停止済み。RAM約307MB。
GUIクライアントの実画面・pack適用後の描画・実プレイの手触りは未確認。実Minecraftスクリーンショットはまだ無い。

## 霧の独立プレビューと造形比較（追加）

初稿の祭壇・台座は本人から不採用の評価。旧モデルとゲーム動作は保護し、旧モデルは煙の比較用にだけ仮置きする。
`assets/model-lab/infusion-v2` のA/Bは独自の灰色モデル形状案で、runtime / pack index / 構造判定には未接続。詳細材質・完成アートではない。
Aは幅広く凹んだ受け鉢、穴のある二分割冠、細首と受け皿の周辺台座。Bは低い盤、中心受け口へ向かう溝、低い供物トレーと稼働時に上がる焦点断片。
灰色の中央品/素材マーカーは位置を示す仮モデル。実アイテムの見た目や完成品デザインを示さない。
新造形を採用する際には現在の四支柱のブロック・構造占有/当たり判定・対象品高さ・起動ヒット・Jar煙の到着点をまとめて変更する。形だけ差し替えて不整合にしない。

煙は `WorldInfusionSmoke` の成功消費イベントからのみ発生し、六房×三ローブの斑を曲線に沿って送る。元素色・太さ・横揺れを同じKotlin計算で生成。
packありでは専用の手描き/コード編集16px半透明PNGを、標準 `ItemDisplay` のcamera-facing billboard / 色component / 2tick補間で描画する。
標準Dustは粗い粒に見えたため、pack未適用時のfallbackにだけ残す。既存Particle Frameworkや新規クライアントmodは変更/追加しない。
同時表示は最大64。2tick更新。消費停止/取消しは新しい房の放出だけを止め、既に出た房は焦点へ移動して縮み、Entityを削除する。
実消費が保存できない時、reservoirの再利用、単なるActionBar表示から煙を発生させない。描画は資源transactionを変更しない。

独立GIFは実 `WorldInfusionRules` の火3/潮2消費から得た座標と色、同じPNG/scaleを使う。開始後に風Jarを撤去し、tick90で不足停止するfixture。供給する火と潮の二瓶だけを映す。
8.6秒/10fps/約5MB。標準クライアント録画ではなく、透過ソート・照明・時間補間はPythonの仮描画。実クライアントでの最終透過品質や負荷は未確認。
TC6静止画の弧状・太さが変わる流れを参考にしたProjectS独自案。TC6/7の速度・方向・消失挙動を忠実再現したと断定しない。decompileコードは使用しない。

追試結果：**54 tests / 0 failures**、`compileKotlin` / `installDist`成功。新3件は煙の有界性・停止と残りの到達・実資源消費からのpreview trace。
実Minestomの既存runtime testにも、色つき煙ItemDisplayの生成と終了後の消去を追加し成功。今回サーバーやGUIクライアントを再起動していない。
以前のローカルhandshake/pack HTTP smokeは初稿段階の結果で、新しい半透明材質の実クライアント検証とは別。

参考：

- [Botania公式Runic Altarモデル](https://github.com/VazkiiMods/Botania/blob/1.20.x/Xplat/src/main/resources/assets/botania/models/block/runic_altar.json)：広い天板・細首・足の比率。モデル形状をコピーせず役割の読みやすさを参照。
- [Botania実画像](https://user-images.githubusercontent.com/9543430/113498185-a1015d00-94d8-11eb-9d89-cd9f31520714.png)、[Ars実画像](https://i.mcmod.cn/editor/upload/20230226/1677341693_316522_RHFq.webp)をローカルで目視。Arsは受け皿の上に素材が見え、中央機構と外周台座の役割が形で異なる。
- [TC画像](https://ftbwiki.org/images/thumb/0/0c/Infusion_5.png/500px-Infusion_5.png)：親調査で目視済み。こちらの取得ファイルは欠損し再取得も418だったため、こちらで完全な画像を見たとはしない。
- [Minecraft公式のCustom Model Data色tint](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)：標準resourcepackの色指定。26.2のcached native assetsで半透明texture参照の形式も確認。

参考画像のバイトはソース/成果ZIPに含めない。

## 浮遊刻印石の材質付き初稿と霧の安全性（最新）

本人の追加要望でA/Bの単純な皿・冠を仕上げる方針を止め、`assets/model-lab/infusion-v3` に1方向の専用一式を作成。厚い傾いた刻印石、内側へ傾く支柱、凹みのある中央台、細い首の材料皿、同じ材質のJar。バニラ構成ブロックは画像の一式に含まない。旧モデルとA/Bは保護。

[TC6の実スクリーンショット](https://github.com/xiaoschannel/Minecraft-Guides-Thaumcraft6/blob/master/pages/05.auromancy/23.advanced-foci-basic-infusion/focus%20infusion%20process.jpg)をこの段階で取得して目視した。厚い斜めの核、刻印面、対象品のための余白を参照。ルール根拠ではなく形の参考とし、写真・元モデル・decompileコードを成果へコピーしない。

5種のnative JSONモデル、48cuboids（回転12）、6種の手描き16px材質を検証。オフラインrendererはelement rotationと表示yawも処理する。正面・斜めの単体と一式、材質の原寸／拡大PNGを出力。**まだ旧ゲーム構造への接続前のモデル初稿**。当たり判定、専用配置、支柱の向き、アイテム高さ、Jar口と核の霧受取点は採用時にまとめて合わせる。

霧は別に全private worlds共有の装飾Entity上限192を追加。closeの冪等化、world移動・disconnect・instance unload・Matrix外部除去のcleanupを追加。正常完走、取消、不足、保存、共有lease上限を実Minestom仮接続で検証。

最新結果：**60 tests / 0 failures**、`compileKotlin` / `installDist`成功。霧ピーク45、プレイヤー込みsceneピーク63、生成126／除去126。位置API2520、metadata2646。詳細・複数祭壇の算出・packetとAPIの区別は `world-infusion-smoke-lifecycle.md`。新しいserver/clientを起動しておらず、実client FPS・最終画質は未測定。
