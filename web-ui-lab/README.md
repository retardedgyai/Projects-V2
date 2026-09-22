# Web UI Lab — ProjectS Playground

動画のBlissMenuに着想を得た、**独立Minestomサーバーで動くUI試作**。
非公開のBlissMenuコードは使っていません。本編UI・セーブ・経済・client-fabricは変更しません。

## 何を試すか

- チェスト枠外の、装備モデル＋素材＋実行ボタンの自由配置。
- HTMLとCSSをAI/人間が編集し、再コンパイルなしで反映。
- 固定カメラとサーバー側カーソル。見た目とクリック判定に同じレイアウトを使用。
- 無効状態・素材不足・タブ・ページ送り・追加応答遅延・表示サイズ。
- 購入/強化はメモリ内のダミー値のみ。課金・本編装備・所持金・保存は一切触らない。

## 起動

Java25と既存Gradle cache/patch環境を使う。

```powershell
.\gradlew.bat :web-ui-lab:test :web-ui-lab:uiSmoke :web-ui-lab:installDist --offline --no-daemon "-Pkotlin.compiler.execution.strategy=in-process"
.\scripts\start-web-ui-lab.ps1
```

- Minecraft Vanilla 26.2: `127.0.0.1:25570`（本編25565とは別）。
- ブラウザ: `http://127.0.0.1:18090/`。
- コンパスを右クリック、または `/ui`。マウス移動でカーソル、**左クリック**で決定。
- 素材見本は画面内の「前へ・次へ」。Shift / 「閉じる」/ `/uiclose` で終了。
- UI中だけクライアント表示をspectatorにして手・通常HUDを隠す。サーバーのAdventure権限/所持品は変更しない。
- この表示モードでは空中右クリックは届かず、ホイール/数字キーはバニラ観戦メニュー側に処理される。UIページ操作には使わない。
- Escはバニラの一時停止画面になる。独自のEscキー検出はしていない。
- ゲーム操作・feel評価はCreatorが行う。本編サーバーを停止しない。
- 許可された表示確認のみ `start-web-ui-lab.ps1 -OpenOnJoin` で参加後にUIを自動表示できる。クリック・マウス操作はしない。

## AIにUIを編集させる場所

**`ui/forge.html`だけをまず編集する。**
本物のWebView/Chromium、React、Tailwind本体は組み込まない。
ブラウザで読めるHTMLのうちXMLとして整った小さなサブセットを、サーバーで解析する。
CSSは`.class`セレクターとinline style。サポート外は黙って無視せずエラーにする。

対応: `display:flex`、`flex-direction:row|column`、`flex-grow:1`、px単位のwidth/height/padding/gap、
background-color/color（6桁HEX）、font-size、font-weight、text-align。
デフォルトは縦並び。box-sizingはborder-box、子は縮まない。主軸は明示サイズかflex-grow必須。
ブラウザ全文互換ではない。grid、position:absolute、overflow、任意JS、CSS変数、外部URLは非対応。

- `data-action="forge"`: Kotlin側の固定allowlistアクション。コマンド文字列を実行しない。
- `{{ore}}`: サーバー状態のテキストbinding。
- `data-enabled="affordable"`: 不足時は灰色表示＋サーバーが実行拒否。
- `data-if="catalog"`: タブ/ページに応じた表示。
- `data-item="minecraft:iron_sword"`: バニラのItemDisplay。ブラウザでは子テキストの仮記号を表示。

保存 → ブラウザ更新で確認 → ゲームの「HTMLを再読込」。無効なHTMLは前のゲーム画面を維持する。
本編資産を取り込む前の操作性試験なので、今回は追加RPを必要としないバニラモデル/フォントを使用。
画面設計が合格してから既存ProjectSパックのアートを接続する。動画のデザインや第三者アセットはコピーしない。

## 確認する順番

1. 素材18個から強化して6個に減り、次の強化は灰色で拒否される。
2. 補充・タブ・ページ送り・閉じる→再度開く。
3. 追加応答遅延0/50/100/200msで操作感を比較（実RTTの再現ではなく入力処理への追加遅延）。
4. FOV/ウィンドウ比率を変え、表示サイズ100/80/65%で画面内に収まるか確認。
5. HTMLの色や間隔を変えて再読込。本編の所持品が変わらないことを確認。

## 現時点の制約

- ブラウザは配置/色/状態の参考。Minecraftのフォント幅・アイテム3D・遠近法まで同一ではない。
- サーバーはOSマウス座標や解像度/FOVを直接取得できない。隠れた視線角の差分を独自カーソルへ変換。
- OSカーソルでも、Webのネイティブ入力でもない。マウス感度とネットワーク遅延の影響がある。
- 画面は800×480の固定論理canvas。自動レスポンシブ、任意ドラッグ/長押し、IME入力は未実装。
- コード/自動試験の合格と、実機での操作感・視覚の合格は別。CreatorのManual Smoke前に本編採用しない。

## 構造 / 故障時

`forge.html → UiDocument.layout → UiScene → UiRenderer / hit test → ForgeDemo`。
パケット入力とカメラ復帰は`UiSessions`。見た目なら`UiRenderer`と`UiGeometry`、レイアウトerrorなら`UiDocument`、
起動ログは`.tools/web-ui-lab/`。接続ログは`UI_LAB_PLAYER_CONNECTED`。
UI定義は96KB/200 DOM/180描画node/深さ12まで。XML外部実体と外部アクセスを禁止。
サンプル要求8件、遅延入力64件で上限を設け、無応答5秒で解除する。

## 技術参考

- 作者提供動画/会話: BlissMenuのserver＋resource pack方式（ソース非公開）。
- [Minestom API](https://javadoc.minestom.net/): CameraPacket / display entities / player packets。
- [ArcMenu](https://github.com/FENTAIIII/ArcMenu): 固定カメラ時に回転パケットが自然送信されなくなる点と、
  相対座標同期で回転をサンプリングする技法を参照。独自のMinestom実装で、Paperコードの移植ではない。

研究用クローンは`.tools/`のみ。配布物へ第三者コードは混入しない。

## 2026-09-22 入力・描画修正

初回Creatorテストで遅延と背景の位置ずれが判明。初回のbrowser/smoke PASSは実機品質の保証にはならなかった。

- 26.2 vanillaクライアントのTextDisplay描画処理を確認。空白背景のローカル境界は `x=[-.05,.075], y=[0,.25]`。
  背景を上へ二重にずらしていた式を修正し、文字の原点も基準線へ補正。全layerで遠近差を補償する。
- entityは固定位置で一度spawn。以降は変更されたmetadataだけを送信。cursorだけ1tick transform補間。
  初回spawnは補間なし、静止中は再送なし。hoverは旧/新ボタンの背景色だけ変更。
- 追加遅延0では受信した回転・クリックを即処理し、次のInstanceTickまで持ち越さない。
  OSカーソルと同じゼロ遅延にはならない。実RTTや受信処理・描画フレーム待ちは残る。
- 終了時にcamera/client mode/slot/位置を復帰。旧sampleのACKだけをIDで消費する。
  IDのないPosRotを推測で消費しない。復帰teleport完了まではMinestom自身のACK gateが移動を保護。
- 画面サイズを拡大。低FOV時は「表示サイズ」で縮小する。FOVをサーバーから自動取得はできない。
- 平地の独立labではUIカメラを目線より4block上に置き、拡大した画面下端が地面で隠れないようにする。
  本編の任意地形で使う場合の壁・天井遮蔽までは保証しない。

回帰検証: Kotlin 6件（native背景の四隅とhit領域の一致を全depth/zoomで検査）。
Native smokeは5周、遅延0の即時click、静止metadata=0、移動metadata<=5、第三者非表示、mode/slot/camera復帰、旧ACK処理と通常移動非干渉、残留0を検査。
実機でpanel/text/itemの整列と手・HUD非表示を受動撮影で確認。操作感・高遅延回線・異なるFOVの最終判定はCreatorの手動テストが必要。

## カーソル低遅延経路（上記の1tick transform補間を置き換え）

- 独立UI-labプロセスだけ60 TPSへ変更。視線取得要求とサーバーの受信queue処理の間隔を50msから約16.7msへ短縮。
  本編のTPS、ダメージ/移動/スキル時間には適用しない。比較起動は `-Dprojects.ui.tps=20`。
- カーソルの形と大きさは固定metadata。移動だけ3個のprivate entityの絶対座標同期へ切り替える。
  native26.2の `ClientPacketListener.handleEntityPositionSync → InterpolationHandler.interpolateTo(steps=0) → snapTo` を利用。
  `Display.tick` でのtransform更新待ちと、その後の1tick追従を通さない。サーバーのentity位置も同時に更新する。
- 画面・文字・hover・クリック判定の座標と見た目は変更しない。推測座標/先読みを入れないため、停止時の行き過ぎを作らない。
- 止まっているカーソルは送信0、移動は3 position packets、hoverだけ最大2 metadata。回帰smokeで検査。
- 60 FPS表示やゼロ遅延を保証するものではない。通信jitterやclient FPSによっては段付きが残るのでCreatorが比較する。
  `SetTickStatePacket(60)` は使わない。26.2 clientのゲームtickは速い側が20Hzに制限されており、この問題の解決にならない。
