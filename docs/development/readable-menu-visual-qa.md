# 読みやすいメニューの表示確認

リソースパック適用済みのメニューは、中央の操作と左右の情報欄を合わせて幅384pxです。
MinecraftのGUI倍率適用後の横幅が **400px以上** ある設定を推奨します。
左右が見切れたら、中央上部の「?」を開くか、ビデオ設定のGUI倍率を1段下げてください。
例として横1600pxのウィンドウ・GUI倍率4なら、表示領域は400pxになります。
クライアントMODは不要です。サーバーはクライアントのGUI倍率を読み取っていません。

## 実際のメニュー構成をオフライン確認する

`CoreMenuCanvas.snapshot()` は構築済みメニューのタイトル、左右の情報、ボタン、文字位置を
通常のUnicodeとRGB数値で返します。メニューテストからGson等でJSON出力し、次を実行します。

```powershell
python scripts/render_core_menu_preview.py .tools/menu-qa/forge.json --output .tools/menu-qa/forge.png --strict
python scripts/render_core_menu_preview.py .tools/menu-qa/forge.json --output .tools/menu-qa/forge-small.png --scaled-width 320
```

描画には、実際のパック内の背景、フォントPNG、文字幅、ボタン状態を使用します。
石板ピクセルUIでは、カードの全占有スロット・素材アイコン・装備の挿絵も実snapshotから描画します。
日本語は2倍密度の原画を先に合成してから最終倍率へ変換し、先に1pxへ潰しません。
炭火工房UIではBODY/EMPHASISの別字形・別advanceと、中央の装備・台座・キャプションも
実snapshotから描画します。台座とカード／ボタンの描画順も実装と一致させます。
不足字形・省略された文字列は併出力の `.audit.json` に記録します。
`--strict` はこれらが1件でもあると終了コード2を返します。
別worktreeのパックを確認する場合は `--repo <repoの絶対パス>` を指定できます。

これはMinecraftを操作しない **タイトル描画レイヤーの確認** です。
実際のアイテムモデル、所持品、ホバー、ツールチップ、バニラのインベントリ見出しは描きません。
必要なら `--show-icon-slots` で省略したアイコンの位置を診断用の枠として表示できます。
完成形のスクリーンショットや手動テスト済みという意味ではありません。

旧 `assets/core-ui/readable-menu-canvas-preview*.png` は描画部品の例示用fixtureであり、
実際のアカウントや現在のメニューの状態ではありません。

## 文字追加時

新しい日本語をソースに追加したら `scripts/build_core_ui_assets.py` を実行して字形を再収録し、
`scripts/verify_core_ui_assets.py` とメニューテストを実行してください。
通常のGradleビルドは生成済みリソースを使います。バニラのフォントは変更しません。
パック未適用時の情報は `fallbackLines()` から、PUAや省略前のUnicodeで取得できます。
