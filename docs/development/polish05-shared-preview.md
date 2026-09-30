# Polish05共有素材・Minecraft実描画プレビュー

2026-09-30 / `play/gyai/polish05-design-review`

## 変更内容と今回の範囲

元HTMLの暗い金色の雰囲気を保持し、武器・防具の浮遊、漂う金色の5粒子、
中央の光の緩やかな明滅を復元した。確認画面では背景の動きを止める。
文字・固定背景は3400×1864の2倍密度を維持する。

今回の対象はデザイン案と独立した表示ラボ。24種類の固定状態を切り替える。
本編の口座・素材消費・強化抽選には接続していない。
既存のカーソル入力・先読み・補間設定は変更していない。

| 表示 | 描画元 | 確認できること |
|---|---|---|
| 元HTML | ブラウザのCSS | デザイン、回転、デモの状態遷移 |
| 共有素材プレビュー | 出荷するパックPNGをCanvasで組み立て | 画像、時間定義、配置、表示密度、固定状態のクリック |
| Minecraft実描画プレビュー | 専用Vanillaクライアントのウィンドウ画像、またはF2のPNG | Minecraft本体が実際に描いた結果 |

共有素材プレビュー自体はMinecraftのGPUをブラウザで実行していない。
実描画プレビューはMinecraftを別ウィンドウで動かし、その画面をここへ渡す。
ゲーム操作はMinecraft側で行う。入力の最終feel判定はCreatorが行う。

## 動く部分の処理

1. HTMLから固定背景と透明な装備画像を分けて書き出す。
2. 光は、元CSSの勾配を固定の工房背景へ合成した12段階の画像にする。
3. schema 3のmanifestに配置と周期を保存する。
4. ブラウザと `ForgeRasterCalibration` が同じ周期から動く位置・光の段階を計算する。
5. `UiSessions` は校正flowにだけ動的更新を加える。
   `UiRenderer` は同じIDの表示を保持し、変わった部分だけ更新する。

| 動き | 定義 |
|---|---|
| 装備の浮遊 | 5.6秒周期、上下3 CSS px |
| 光の明滅 | 6.4秒周期、元の86〜100%の強さ、位置・大きさは一定 |
| 粒子 | 5粒、6秒周期、右へ9px・上へ75px、薄く現れて消える |
| 確認画面 | 動的レイヤーを停止、固定の確認画像を表示 |

ブラウザには「動きを止める」を用意した。動きの軽減設定も尊重する。
この停止時には装備を中央の静止位置で表示し、粒子を隠す。
HTMLの回転中は浮遊を止める。校正ラボは装備の固定角度画像であり、3D回転は含まない。

### 透明な光で丸い輪郭が出た問題

最初の動的試作では光を透明PNGとしてTextDisplayへ重ねた。
Minecraft 26.2本体の `assets/minecraft/shaders/core/text.fsh` は
`color.a < 0.1` の画素をdiscardする。
そのため薄い周辺が切れ、明滅に合わせて丸い境界が目立つ。

修正版は、光を背景と合成してalpha=255にした段階画像を使う。
光の段階が変わっても全タイルの位置・寸法は同じ。
文字、装備、光の画像を低解像度化する工程は入れない。

装備だけは透明glyphへ分割する。Minecraftのbitmap providerが
非ゼロalphaから文字幅を決めるため、透明タイル右下にalpha=1の幅保持画素を置く。
これはMinecraftのtext shaderでは描かれない。
静的背景の「変更画素0」と、この幅保持画素のある装備画像を混同しない。

## ファイルと入口

| ファイル / Class | 役割 |
|---|---|
| `assets/ui/polish05-design-review/index.html` | 元のデザイン案とCSSの動き |
| `build_shared_preview.cjs` | 背景、装備、元CSSの光を分離して書き出す |
| `package_shared_preview.py` | 密度を維持してPNG・font・manifest・ZIPを生成 |
| `shared-preview/index.html` | 同じパック画像で動くブラウザ表示 |
| **`ForgeRasterCalibration`** | 最重要。manifestからMinecraftの配置と動的レイヤーを作る |
| `UiSessions` | 校正flowの時間更新。既存カーソル処理は維持 |
| `NativeEnginePreview` | 元PNGを無加工で配信するローカル専用サーバー |
| `capture_native_window.py` | 選択されたMinecraftウィンドウだけを取得 |
| `start_native_preview.ps1` | 専用クライアント・表示ラボ・配信の起動と停止 |

生成画像と大きな作業データはDドライブのrepoおよび `.tools/` に保存。
ZIPと個人のクライアント引数・ログ・セッションファイルはGitへ含めない。

## 検証

- 24状態すべて、**出荷する固定背景のパックPNGを読み戻して**元の背景とRGBAを比較。変更画素0。
- ブラウザ合成元も固定背景の比較で変更画素0。
- 動作中は異なる時刻で画素が変わることを確認。
- 390/900/1304/1920px幅、DPR 1/2、通常/ゲーム配置で表示密度を確認。
- 停止時の共有PNGと比較元の表示結果がDPR 1/2で一致。
- 状態切替、確認/キャンセル、クリック座標、原寸表示、横溢れ、ページエラーを確認。
- `ForgeRasterCalibrationTest`: 背景とhitの不変性、浮遊、5粒子、周期の巻き戻り、
  光の固定寸法、確認画面の停止、font providers、24状態を確認。
- `NativeEnginePreviewTest`: HTTPで受け取るPNGが元バイトと一致し、更新・F2分離・
  不完全なファイル・範囲外リクエストを処理することを確認。
- Kotlin targeted test 5件 / installDist 成功。
- Minecraft 26.2の修正版を直接起動して確認。実描画PNGを無加工で配信できた。
  1.4秒差の画像で中央は235,926画素変化、右の固定表示は変更画素0。
  光の輪郭が切れた丸になる問題が解消したことを実描画で確認。
  検証記録は `.tools/shared-forge-preview/native-motion-verification.json`。

これらは全Minecraft表示の画素一致を証明しない。
Minecraftのパック読み込み・透明な装備の境界・実描画負荷は実機画像でも確認する。
最終的な動きの好みとゲーム操作はCreatorの確認が必要。

## 実描画プレビューの使い方

専用Vanillaクライアントを `127.0.0.1:25623` へ直接起動する。
resource packは専用gameDirへ最初から適用する。既存クライアントの設定・セーブは変更しない。
ウィンドウ表示にして、ブラウザへ切り替えた時の全画面最小化を避ける。

```powershell
.\gradlew.bat :web-ui-lab:installDist --offline
.\assets\ui\polish05-design-review\start_native_preview.ps1
# 開いたMinecraftの正確なタイトルを確認して指定する。
.\assets\ui\polish05-design-review\start_native_preview.ps1 -CaptureOnly -WindowTitle '<実際のMinecraftウィンドウタイトル>'
# このセッションの引数を持つプロセスだけを停止する。
.\assets\ui\polish05-design-review\start_native_preview.ps1 -StopSession
```

配信ページは `http://127.0.0.1:18100/`。
ウィンドウの画像を最大10回/秒で取得し、PNGを拡大加工せず配信する。
これは60fps動画配信ではなく、カーソルの反応を測るための画面でもない。
原寸は表示密度を考慮した元画素の表示。「画面に合わせる」は縮小表示になる。
F2モードは専用client/screenshotsの原PNGをそのまま配信する。

PNG読取中にWindowsが置換を拒んだ場合、前の完全な画像を保持して次のフレームで再試行。
取得を止めた時や最小化で描画が止まった時は、最終画像と取得時刻が残る。
デスクトップ全体や他アプリは取得しない。配信先は127.0.0.1だけ。

## 壊れた時に最初に見る場所

- 光の輪郭が丸く切れる: 最新ZIPが適用されているか、光のパックPNGがalpha=255か。
- 動かない: manifest schema 3のdynamicレイヤー、校正flowのtick、確認画面かどうか。
- 文字がぼやける: `shared-preview/index.html` の表示サイズ×DPRとCanvasの実画素数。
- 実描画プレビューが止まる: `.tools/native-engine-preview/capture.err.log` と取得時刻。
- glyphが欠ける: manifest → tile URL → pack/font/plates.json → 適用ZIPの順。

## 本編への反映と残る懸念

現段階はPlaygroundの表示ラボ。本編へは、任意の装備名・所持数・強化性能を
live値のレイヤーへ分け、実際のItemDisplayと同じ配置に接続する必要がある。
固定口座の画像をそのまま本編へ採用しない。
動く装備の透明glyphにはMinecraft固有のサンプリングがあり、HTMLと同じ画素とは限らない。
生成パックは1567 glyph、ZIP約26.6MBでDドライブに保存。
光は35タイル、固定背景は112タイル。段階が変わる時だけ光の内容を更新するが、
本編の負荷・最終feel gateは別途行う。

## 再生成

```text
node assets/ui/polish05-design-review/build_shared_preview.cjs
python assets/ui/polish05-design-review/package_shared_preview.py
node assets/ui/polish05-design-review/verify_shared_preview.cjs
gradlew :web-ui-lab:test --tests '*ForgeRasterCalibrationTest' --tests '*NativeEnginePreviewTest' --offline
```

browser検証は127.0.0.1:8155のrepo HTTPサーバーを使う。
検証画像は `.tools/shared-forge-preview/`、書き出し元は `.tools/forge-shared-capture/`。

## 公開先

Task branch: `play/gyai/polish05-design-review`。
完了checkpointのcommit SHAは最終回答に記載する。本編へのmergeは今回の範囲に含めない。
