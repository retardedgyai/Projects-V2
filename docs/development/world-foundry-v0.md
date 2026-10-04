# ProjectS ワールド鋳造試作 v0

入口は `-Dprojects.worldFoundry=true`。基点は `b51a177f`（現ワールド魔術設備checkout）、専用branchは `play/gyai/world-foundry-v0`。Minestom 26.2 / Kotlin / JDK25。本編への採用・共有工房・本番保存の移行は今回含まない。

炭床の上に耐火るつぼ、隣に出湯口、その真下に砂床鋳造台をプレイヤーが置く。材料は既存 `CoreMaterial(INGOT,2)` と同じT2インゴット。炉に3本分まで投入でき、炭1個で1本を6秒かけて溶かす（開始は次のサーバー秒）。1本を工房湯4単位と定義し、芯棒は2単位、補強板は4単位。これはProjectSの試作値であり原版のmBを移植したものではない。

鋳造台へ砂、木製原型を押す、空手で原型を外す、出湯口から注湯する、2秒の注湯と5秒の冷却を待つ、空手で部品を回収する。原型は再使用でき、砂は鋳造1回で消費する。未使用の砂型はしゃがみ＋空手で回収可能。連打による進捗加速はない。製造メニューやFabric modは必要ない。

## 保存・権限

所有者UUIDごとのprivate islandと `.foundry` 保存を持つ。初回のみT2インゴット8、炭8、砂8、設備各1、原型各1の隔離した試作資源を用意する。本編アカウントは読み書きせず、既存アカウントcodecを封筒内に利用する。共有工房の権限ルールは未定。本編でこのprivate islandを共有設備へ流用しない。

投入・燃料・砂型・溶湯・鋳造台内金属・出力は単一のchecksum付き封筒に入る。保存は世代照合、ファイルロック、tempのforce、atomic renameの順で確定し、その後にinventoryと表示を更新する。注湯クリック時に必要金属全部を鋳造台へ予約移動するため、描画の流れが消えても金属が消えず、連打しても予約を重ねない。保存失敗・保存競合ではそのsessionの操作を停止し再接続を促す。壊れたchecksumは新規資源へ置換しない。

設備解体は空手＋しゃがみ。中身のある炉・台、稼働中設備は拒否する。未使用炭と未溶解インゴットは空手で回収でき、湯4単位はしゃがみ＋冷却回収棒でインゴット1本に戻す。残り2単位は炉に保持して次の芯棒鋳造に使う。外部処理で設備blockが消えるとその秒の進行を止め、保存済みの設備を復元する。離脱中は進行しない。再接続・再起動は保存した残り秒と同じ状態で再開する。

## 描画と統合版の境界

`WorldFoundryRules` が状態を進め、`WorldFoundryPresentation.frame` が読み取り専用のmodel/position/yawを出す。Minestom adapterは通常のblock interactionを処理する。カスタム描画は任意で、pack未適用時は通常blockの炉・溶岩大釜・鉄柵・砂床/橙色/鉄blockへ切り替える。Java item displayが無くても投入・原型取り外し・注湯・回収の操作は可能。fallbackでは細かな形と流れの表現は失われる。

現checkoutの `server-minestom/`、`client-fabric/`、`protocol/`、`docs/` にはGeyser/Floodgate導入、Bedrock pack変換、Bedrock表示adapterの実装は見つからない。`model-lab/build.gradle.kts` の `org.geysermc.mcprotocollib` はJava protocol loadtest用で、Bedrock接続の実装ではない。今回の独自pixelモデルはJava用JSON・PNGと `ITEM_DISPLAY` を使う。WSEEはこの試作には依存しない。

Bedrockの最小smokeに不足しているものは、対象runtimeへの接続ブリッジとUUID/所有権の対応、Bedrockモデル/texture/animationのpack、display代替adapterとmapping、統合版クライアント環境。そこで炉・注湯表示、touch/controllerの選択・interaction、原型と部品回収、disconnect復帰を確認する必要がある。現時点で「全部きれいに対応済み」とは言えないが、domainと操作をJava専用UIに固定してはいない。これらの新規導入・購入・認証変更・既存サーバー変更は今回行っていない。

HTMLは持ち出せるレビュー用PreviewでありゲームUIではない。Kotlinの実鋳造ルールと表示関数からexportしたtrace、実装model JSON/UV/PNGをCPU投影して埋め込む。ゲーム動画やMinecraft screenshotとは区別する。

## 検証とPreview

```powershell
$env:GRADLE_USER_HOME='C:\Users\xgaiz\.gradle'
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
.\gradlew.bat :server-minestom:test --tests '*WorldFoundry*' --tests '*CoreUiPack*' --tests '*CoreAccountTest' --tests '*CoreUiTest' :server-minestom:installDist --offline --no-daemon --max-workers=2
```

`scripts/build-world-foundry.py` は独自assetsを生成する。testが `server-minestom/build/reports/world-foundry/preview-trace.json` を出力してから、`scripts/preview-world-foundry.py` で `.tools/world-foundry-preview/` のHTML/GIF/静止画を生成する。`scripts/verify-world-foundry-preview.cjs` は所有したheadless Edgeだけでdesktop/mobileと操作を検証し閉じる。既存ゲーム・サーバー・GUIは終了も切替もしない。

code試験は金属保存則、両形状、設備位置、原型回収、解体保護、秒ごとの保存復帰、競合保存、保存失敗、checksum/owner保護を扱う。実Minestomのfake connection試験は配置・手持ち操作・native fallback・独自model metadata・表示消去・復帰を扱う。TCPクライアントや描画FPSや本物のMinecraft操作を検証したとは扱わない。最終の実機操作・視認性・ゲームの手触り・Bedrockは未確認。

Preview確認後に必要なら `scripts/start-world-foundry.ps1` で隔離したloopback serverを起動できる。今回の作業では実ゲームserverは起動していない。

## 製作時に実見した参考

- [Puny Smelting 1.20 Gallery](https://slimeknights.github.io/docs/books/1.20/puny-smelting/gallery/): 公式静止教本図。画像page 2（教本4–5頁）の小型炉、page 3（6–7頁）の3入力枠と単一溶湯、page 9（18–19頁）の砂型の台、page 10（20–21頁）の原型押し込み、page 11（22–23頁）の金型説明を実見。採用は上の湯面、下の熱源、横出湯口と脚付き台、原型の取り外し、一回消費の砂型。金型・合金は未実装。原版の名前・texture・model・音・sourceはコピーしていない。
- [Casting Recipes](https://slimeknights.github.io/docs/json/recipes/casting/): 仕様本文を読む。型消費、液体量、冷却時間の区分を参考にする。数値はProjectSの試作値。
- 親研究から引き継いだ [Tinkers Construct 3 gameplay](https://slimeknights.github.io/docs/gameplay/tinkers-construct-3/) の確認結果も参照。今回このページの動画を見たとは扱わない。YouTube1.16.5動画は読み込みが止まっており未視聴。

初回Previewでは耐火壁が木材に見えやすいことを確認し、縦目模様をやめて土の粒と鉄枠に修正した。立った視点で湯面を見やすくするため、るつぼは半block高にした。浅い上部の湯面、木製原型と暗い空型、橙色→鈍い金属色の工程差を再比較する。実機での湯面の可視範囲はまだ確定していない。
