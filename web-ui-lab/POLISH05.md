# Polish05 / Minecraft Vanilla 26.2 Playground

承認済み素材の原本は `../assets/ui/polish05-import/`。本編サーバー、セーブ、CoreAccount、実通貨、確率、client-fabricを使用しない独立Minestomラボです。ブラウザのHTMLをMinecraftで実行するのではなく、原画とレイアウト値からDisplay Entity画面を構成します。

## 準備・テスト

Java 25、Python 3、Pillowが必要です。PowerShellでrepo rootから実行します。

```powershell
$env:JAVA_HOME='C:\Users\xgaiz\Documents\Codex\minecraft-runtime\temurin-25\jdk-25.0.4.1+1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
python -m pip install 'Pillow>=11,<13' 'soundfile>=0.13,<1'
python assets/ui/polish05-import/tools/verify_import.py --root assets/ui/polish05-import
python scripts/build_polish05_lab_pack.py
.\gradlew.bat :web-ui-lab:test :web-ui-lab:uiSmoke :web-ui-lab:polish05Smoke :web-ui-lab:installDist --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
```

`web-ui-lab/ui/polish05-effects/` には承認済みHTMLから切り出した通常以外のGlowと、Georgiaの黄色いぼかし付き強化後数値を同梱しています。元HTMLや原画は編集していません。再生成する場合はChromeとPlaywrightを用意して `node scripts/export_polish05_effects.cjs` を実行し、その後パックを再ビルドします。日本語と英数字はNoto Sans/Serif JPの許諾済みsubsetから32px bitmap atlasを生成し、Polish05専用フォントとして配信します。通常のパックビルドにはfontToolsは不要です。subsetを作り直す場合だけ `scripts/build_polish05_fonts.py` とfontToolsが必要です。

`polish05Smoke` はネイティブの表示体、bitmap font、private viewer、カメラ復帰と破棄をパケット単位で確認します。Minecraftの画素や音の聞こえ方までは判定しません。

## 起動と手動操作

```powershell
.\scripts\start-web-ui-lab.ps1 -Polish05 -Port 25571 -PreviewPort 18092 -PackPort 18093
```

ログの `POLISH05_PACK_READY` と `UI_LAB_READY` を確認します。Vanilla 26.2で `127.0.0.1:25571` に接続し、配信パックを承認します。`POLISH05_PACK_LOADED` の表示後、コンパス右クリックまたは `/ui` で開きます。マウス移動でカーソル、**左クリック**で決定、Shiftで閉じます。EscはVanillaの一時停止です。ブラウザの I/R/M/Enter キー表示はMinecraft内では使いません。画面内の各ボタンをクリックします。

このPCの専用Vanillaクライアントは `D:\CodexArchive\Polish05-Minecraft-Import-2026-09-25\client-26.2-polish05\start-visible-client.ps1` から直接起動できます。引数には同ディレクトリの `direct-vanilla.args` とgame directory、Java25の `javaw.exe` を指定します。起動中のPIDとcommand lineを確認してから再起動し、本編用クライアントは操作しません。

確認する順序：装備を選択 → 装備中と比較 → 工房へ戻る → 強化の確認 → 確定 → 0.72秒後の結果と素材・銀貨の更新 → 左の素材補充 → 精錬数量と原石・銀貨の上限 → 精錬確認 → 0.90秒後の素材更新 → 同じ装備へ戻る。触媒は別途100%の表示と消費を確認します。表示値・80%・成功例はラボ専用fixtureです。

光と動きの確認：剣の後ろに通常のGlow、刃の周囲に小さな黄橙色の粒子がゆっくり漂うこと。強化を確定した直後の0.72秒は元HTMLの `striking`、成功直後の1.10秒は `result-warm` のGlowと粒子の増加を表示します。右の強化後数値には元CSSの黄色い15pxぼかしを保持します。F2静止画だけでは短い状態遷移を判定できないため、実機では連続表示も確認してください。

開始ポートが使用中なら `-Port`、`-PreviewPort`、`-PackPort` を空きポートへ変更します。本編25565、モデル工房25566は使用しません。終了するときは起動メッセージのPIDだけを停止します。

## 実機判定が必要な差

- 元画面1440×920を800×480へ同じ倍率で縮小してletterboxします。MinecraftのFOV・画面比率・GUI設定で見かけの大きさが変わります。
- 原画・剣・炉・枠・効果音は付属パックを使用します。日本語はHTMLと同じNoto系の輪郭を専用bitmap atlasに変換します。Minecraftでの縮小サンプリングと字間に微差があります。modalのブラウザblurは半透明の暗幕です。
- bitmap glyphのbaseline、タイル境界、Glowのalpha、剣276/240px比率、右の文字揃え、エンチャント＋音の音量はMinecraft実機で確認してください。見た目が承認済み画面から外れる場合は **FIX-FIRST** です。

## HTMLを正本として再利用する範囲

このVanilla＋配信パック経路ではHTML/CSS/JavaScriptそのものは実行されません。HTMLを制作元にして、ブラウザで描いた装飾・フォント・Glowの画像、クリック領域、アニメーションの各状態を資産として書き出し、Minestomが現在値と操作を同期する方式です。`scripts/export_polish05_effects.cjs` はその一部を自動化しています。固定の解像度・状態でブラウザと同じ画素を持ち込めますが、Minecraftのフォント、拡大率、alpha合成、フレーム更新の差まで含めた全状態の誤差ゼロは保証できません。HTMLを実行して毎フレームそのまま表示する方式にはクライアント側のWebView等が必要になり、現在のVanilla第一経路とは別の実装です。

## 追跡する場所

`scripts/build_polish05_lab_pack.py` が原画packを作り、`Polish05Scene` が画面とクリック領域を同じ座標変換で組み立てます。`Polish05Flow` は各プレイヤーのダミー状態、引用・確定・消費・結果・精錬を扱います。`UiSessions` がcameraと入力、`UiRenderer` がprivate display、`Polish05Pack` がパック配信です。起動障害は `.tools/web-ui-lab/*.err.log`、素材読込は `POLISH05_PACK_*` のログを最初に確認します。
