# Web UI Lab — ProjectS Playground

動画のBlissMenuに着想を得た、**独立Minestomサーバーで動くUI試作**。
非公開のBlissMenuコードは使っていません。本編UI・セーブ・経済・client-fabricは変更しません。

## 何を試すか

- チェスト枠外の、装備モデル＋素材＋実行ボタンの自由配置。
- HTMLとCSSをAI/人間が編集し、再コンパイルなしで反映。
- 固定カメラとサーバー側カーソル。見た目とクリック判定に同じレイアウトを使用。
- 無効状態・素材不足・タブ・ページ送り・ホイール・追加応答遅延・表示サイズ。
- 購入/強化はメモリ内のダミー値のみ。課金・本編装備・所持金・保存は一切触らない。

## 起動

Java25と既存Gradle cache/patch環境を使う。

```powershell
.\gradlew.bat :web-ui-lab:test :web-ui-lab:uiSmoke :web-ui-lab:installDist --offline --no-daemon "-Pkotlin.compiler.execution.strategy=in-process"
.\scripts\start-web-ui-lab.ps1
```

- Minecraft Vanilla 26.2: `127.0.0.1:25570`（本編25565とは別）。
- ブラウザ: `http://127.0.0.1:18090/`。
- コンパスを右クリック、または `/ui`。マウス移動でカーソル、左右クリックで決定。
- 素材見本ではホイール/数字キーもページ送り。Shift / 「閉じる」/ `/uiclose` で終了。
- Escはバニラの一時停止画面になる。独自のEscキー検出はしていない。
- ゲーム起動・ゲーム操作は自動化しない。本編サーバーを停止しない。

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
パケット入力とカメラ復帰は`UiSessions`。見た目なら`UiRenderer`、レイアウトerrorなら`UiDocument`、
起動ログは`.tools/web-ui-lab/`。接続ログは`UI_LAB_PLAYER_CONNECTED`。
UI定義は96KB/200 DOM/180描画node/深さ12まで。XML外部実体と外部アクセスを禁止。
サンプル要求8件、遅延入力64件で上限を設け、無応答5秒で解除する。

## 技術参考

- 作者提供動画/会話: BlissMenuのserver＋resource pack方式（ソース非公開）。
- [Minestom API](https://javadoc.minestom.net/): CameraPacket / display entities / player packets。
- [ArcMenu](https://github.com/FENTAIIII/ArcMenu): 固定カメラ時に回転パケットが自然送信されなくなる点と、
  相対座標同期で回転をサンプリングする技法を参照。独自のMinestom実装で、Paperコードの移植ではない。

研究用クローンは`.tools/`のみ。配布物へ第三者コードは混入しない。

## 今回の検証結果

- Kotlin tests: 5件合格（全画面状態、hit領域、二重強化拒否、不正HTML/CSS/未知item、pointer境界）。
- Native Minestom smoke: カメラ開始→回転パケット→強化click→slot復帰→終了を5周、第三者へのentity非表示と残留0を確認。
- ブラウザ: 強化/素材不足/素材2ページの4画面、枠外はみ出し0、無効ボタン、タブ→次ページ遷移を確認。
- Sol read-only review: 指摘2件（未知itemのreload、終了時slot同期）修正後PASS。
- localhostサーバーとHTTP 200を確認。本物のMinecraftクライアントによる表示・操作感は未検証、Creator確認待ち。
