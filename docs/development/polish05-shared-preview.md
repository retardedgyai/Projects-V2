# Polish05の光を共有する実装用プレビュー

2026-09-30 / `play/gyai/polish05-design-review`

## 何ができるか

**光の形・色・勾配・静止時の見た目を、同じ画像でゲームとブラウザへ渡せる。**
HTMLのCSSをMinecraft向けに手で描き直す工程を減らせる。
今回の [共有素材プレビュー](../../assets/ui/polish05-design-review/shared-preview/index.html) は、
実際のリソースパックPNGをCanvasで読み込んで合成する。
工房本体の光・文字にHTML/CSSの描画を使っていない。

ただし、これは**Minecraftの最終描画と完全一致したという検証ではない**。
ブラウザに同じ画像を表示することと、MinecraftのGPU・フォント表示・3D描画を
ここでそのまま動かすことは別である。

| 対象 | 今回の状態 | 実機の確認 |
|---|---|---|
| 金色の光、選択行の勾配、素材の緑・赤の光 | 背景と合成済みの同じRGBA画像を共有 | 拡大率、タイル境界、色の見え方 |
| 固定文字・数字 | 見本値も画像へ焼き込み、同じ画素を共有 | 実際の文字表示倍率 |
| 任意のプレイヤーの値 | この試作は24通りの固定状態 | 本編対応にはlive値と表示を分離する必要あり |
| 装備プレビュー | 今回は装備も静止画像へ含めた | 本編のItemDisplayの回転・材質・照明とは別 |
| 漂う粒子・鍛造アニメーション | 今回の校正画像では停止 | 同じ時間・位置の定義を別の動的レイヤーへ渡す |
| カーソル | ブラウザの通常カーソル | Vanillaの入力・通信・補間は実機で判断 |
| ワールドをリアルタイムにぼかす | 対象外。HTMLの既存背景画像を使用 | 現行方式はCSSのbackdrop-filterを実行しない |

## なぜ光を背景と一緒にするか

現行 `UiRenderer` は、PNGをbitmap fontのglyphとしてTextDisplayへ描く。
HTMLの透明レイヤーを別々に渡すと、文字描画の透明度・重なり順・サンプリングの影響を
追加で受ける。暗い背景と光を先に合成し、完全に不透明な画像へすれば、
「柔らかい光をどう合成したか」は画像のRGBに確定する。

今回のPNGは全画素のalpha=255を検証した。
PNGは256×256以下のタイルに切り、同じ内容のタイルを共有。
元HTMLを2倍密度（3400×1864）で描画し、各タイルは加工・減色・低解像度化をせずパックへ入れた。
装備と素材は元HTMLのピクセルアート用の表示設定を保ち、文字と光は高精細な画像へ合成する。
端のタイルは実際の幅・高さを使う。

**注意:** 背景も焼き込まれるので、同じ光を別の背景へそのまま移す素材ではない。
背景が動く領域、3D装備、粒子は本編対応時に別レイヤーへ分ける。

## 追加したもの

### ブラウザ

- `shared-preview/index.html`: パックの画像を読み、座標に沿ってCanvasへ合成。
- `manifest.json`: 24状態、1067種類のglyph、各タイルの座標とクリック領域。
- 「共通PNG」: 2倍密度の校正画像を再合成し、表示サイズと画面の密度に合わせて描画。
- 「元HTMLとの比較」: 胴・通常の元画像へ切り替える。
- 「ゲームの配置」: Minecraftの `UiScene` と同じ座標へ配置する近似表示。
  GPUや画面投影の再現ではなく、論理座標・余白の確認用。
- 「原寸で見る」: 1700px幅で文字を縮めず表示。小さい画面ではプレビュー内を横にスクロールする。
- 部位・触媒・確認のクリックは事前に作った状態への切替。素材の消費や抽選は行わない。

校正には工房の高さを固定し、focus ringと粒子を除いた。
24状態は武器・4部位の通常/触媒、素材不足、高段階、最大強化と、強化可能な状態の確認画面。
ブラウザ外の比較用設定やマストヘッドはパックへ含めていない。

### Minecraft側の独立試作

- `ForgeRasterCalibration`: manifestを読んで、既存 `UiRenderer` が受け取る `UiScene` を作る。
  画像とクリック領域に同じ座標変換を使う。
- `ForgeRasterCalibrationFlow`: 校正画像を選ぶ表示専用のflow。経済・保存・強化callbackを持たない。
- `WebUiLab`: JVM property `projects.ui.rasterManifest` で独立試作を選べる。
  live Polish05と同時には指定しない。
- packのnamespaceは `projects_forge_calibration`。本編パックへ上書きしていない。

独立ラボを使うときは、既存の起動方法のJVM optionsへ以下を渡す。
Gradle自体への `-D` ではなく、起動するJavaアプリへ渡す必要がある。

```text
-Dprojects.ui.rasterManifest=<absolute path>/shared-preview/manifest.json
-Dprojects.ui.rasterFrame=chest
-Dprojects.ui.pack=<absolute path>/shared-preview/Forge_Calibration_26_2.zip
```

`rasterFrame` は `risk` や `confirm-weapon-focused` などmanifestのIDを指定できる。
ラボの `/ui` を開くと既存のTextDisplay方式で112タイルを表示する。
この経路の最終GPU表示はまだCreatorの実機確認をしていない。
UIラボへのhook追加は任意指定時だけで、今回サーバーやMinecraftを起動していない。

## 検証

1. `build_shared_preview.cjs` でEdgeを使い、デザイン案の24状態を校正画像へ出力。
2. `package_shared_preview.py` でglyphとresource packを生成。
3. **出荷するパック画像を読み戻し**、元画像とのRGBAバイトを比較した。
   24状態すべて変更画素0。
4. ブラウザがパックを読み込んで組み立てた3400×1864の元画像も比較した。胴・通常の変更画素0。
5. `verify_shared_preview.cjs` で状態選択、部位/触媒、確認/キャンセル、座標モード、
   原寸表示、小幅画面、ブラウザエラーを確認して成功。
   390/900/1304/1920px幅・DPR 1/2・通常/ゲーム配置の16条件で、
   Canvasの描画画素数が実際の表示サイズ×DPRを満たすことを確認。
   1304px幅のDPR 1/2では、表示後のパック合成と高精細な元画像も変更画素0。
6. `ForgeRasterCalibrationTest` 3件が成功。実際のpackのfont providersと
   `UiScene` のglyph・サイズの一致、hit座標、表示専用flowを確認。

Kotlin compile/testは成功。GPUレンダリング、Minecraftによるパック読み込み、
ライブ口座表示、アニメーション、入力の体感はこの結果に含めない。
色や影の完全一致を判断するときは、校正表示を実機で撮影して比較する必要がある。

## ぼやける表示の修正（2026-09-30）

初版の「変更画素0」は**合成元のバイト一致**だけを示していた。
実際の表示での読みやすさや描画解像度を確認するには不十分だった。
実際に開かれていた画面は1249px幅、DPR 1。1700pxの1倍画像を縮小し、
画面全体へ `image-rendering: pixelated` がかかっていた。
文字にもピクセルアート用の拡縮が適用され、輪郭が崩れていた。
またゲーム配置では、一度800×480の画像へ落としてから拡大する問題があった。

修正後の流れ:

1. 元HTMLを2倍密度の3400×1864へ書き出す。配置とクリック領域は1700×932の論理座標。
2. 元画像を256px以下のタイルで保存。manifest schema 2の `rasterScale: 2` で密度を明記。
3. ブラウザでは元の全画素を組み立て、全体を一度だけ高品質で拡縮する。
   文字と光を含む合成画像にはnearest-neighbourを使わない。
4. Canvasは表示サイズ×DPRの画素数で描く。
   800×480はゲーム配置とhit判定の座標にだけ使い、画像の画素数には使わない。
5. Kotlin側はタイルの物理画素座標を `rasterScale` で割って配置する。
   bitmap glyphには高精細な元タイルを渡す。hitは論理座標のまま。

これによりブラウザでの低解像度化を修正した。
狭い画面に全体を収めれば文字自体は小さくなるため、原寸表示も用意した。
元の装備・素材の低解像度ピクセルアートや意図的な背景のぼかしは変えていない。

独立ラボの表示数は28から112タイルへ増え、ZIPは約13.5MB。
Minecraftのサンプリング結果と112個のTextDisplayによる実機負荷は未確認。
本編のUI・カーソル操作はこの修正では変更していない。

### 主に見るファイル

- `shared-preview/index.html`: `present()` が表示解像度を決める。文字がぼやけたら、表示サイズ・DPR・Canvas画素数を最初に確認。
- `build_shared_preview.cjs` / `package_shared_preview.py`: 高精細素材とpackを生成。
- `ForgeRasterCalibration.kt`: packの物理画素座標を論理配置へ変換。
- `verify_shared_preview.cjs`: 合成元の一致に加え、表示密度・縮小後の一致とクリック位置を確認。

## 本編へ反映するなら

1. 光・背景・固定枠を今回と同じ素材として切り出す。
2. 任意の所持数や装備名・性能を、サーバーのlive値から表示する。
   今回のように口座全体を画像へ焼き込む方式は使わない。
3. 実際のItemDisplayと動く粒子を同じ配置定義へ接続する。
4. 実機F2と画像を比較し、倍率・glyphの位置・透明度を合わせる。

ここまで進めれば「HTMLとゲームで素材と配置を共有し、実機画像で差を測る」工程を作れる。
VanillaクライアントでHTMLの実行・3D描画エンジンまで同じにしたとは扱わない。

## 保存・再生成

保存先はDドライブの `assets/ui/polish05-design-review/shared-preview/`。
パックZIPは生成物のためGitへ含めず、ローカルに保存している。

```text
node assets/ui/polish05-design-review/build_shared_preview.cjs
python assets/ui/polish05-design-review/package_shared_preview.py
node assets/ui/polish05-design-review/verify_shared_preview.cjs
gradlew :web-ui-lab:test --tests '*ForgeRasterCalibrationTest' --offline
```

browser検証は既定で127.0.0.1:8155のrepo用HTTPサーバーを使う。
別のURLなら `FORGE_PREVIEW_URL` で変更。
検証画像は `.tools/shared-forge-preview/`、元の校正画像は `.tools/forge-shared-capture/`。
表示が壊れたときはmanifest→tileのURL→packのfont providerの順で確認する。
