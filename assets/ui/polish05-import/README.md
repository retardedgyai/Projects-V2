# ProjectS Polish05 → Minecraft 移植キット

これは**ローカルCodexへ渡す実装素材・接続指示**です。Minecraftへ導入済みのMODや完成プラグインではありません。
`CODEX_START.md` を読ませてネイティブ接続を進めてください。

## 置き方

ZIPを展開して得られる `polish05-import` フォルダを、ProjectS作業用cloneの `assets/ui/` に置くか、Codexへその展開先を指定してください。
既存のHTMLや本編コードを上書きする必要はありません。

Codexへの開始メッセージ：

```text
assets/ui/polish05-import/CODEX_START.md を読んで、Polish05をMinecraft内へ移植して。
デザイン変更ではなく、このHTMLと画像を正本にした忠実な移植です。
右側の配置は変更しない。大剣の大きさ・Glow・左の素材補充・エンチャント＋音を維持。
既存web-ui-labの入力と配信パックを調べ、独立Playgroundで実機描画から進めてください。
このキットは移植済みアプリではなく、資産と状態モデルです。ネイティブ描画と入力接続を実装してください。
本編のセーブ・通貨・確率・既存サーバーには触れず、test/buildと起動手順まで用意。
静止画像だけ表示して完了にせず、選択→確認→強化→素材消費→結果更新→補充精錬が動くところまで。
Minecraftの手動操作・最終feel判定は俺がやります。
```

## 内容

- `reference/ProjectS_UI_Polish05_Workbench.html`：承認された元ファイルを変更せず収録。
- `reference/screens/`：14状態のブラウザ基準画像。Minecraftの撮影画像ではありません。
- `assets/images/`：26枚の元画像をbyte-identicalで抽出。
- `assets/audio/`：10個の埋込Oggをそのまま抽出。
- `assets/layers/`：独立Glowと実測済み部品画像。用途をlayout.jsonで区別。
- `assets/plates/`：炉・Glow合成面・枠。reference_plateは校正用で、完成した操作画面の代用ではありません。
- `layout/`：14状態の位置・可視文字・状態・操作領域、音とglyphの定義。
- `resourcepack/`、`Polish05_Assets_26_2.zip`：独立namespaceのアセットパック。assets/minecraftを上書きしません。これだけではUIは出ません。
- `native/`：Kotlinの独立サンプル状態モデルと座標変換。Minestom renderer/input/serverへの接続は未実装。
- `verification/`：実行した検証の結果。実機合格を表すものではありません。
- `tools/`：再抽出・パック生成・検証スクリプト。

## 検証と未確認

実施：埋込画像・音声の抽出、14状態のHTML描画、座標抽出、画像寸法・ハッシュの照合、音声cue生成、Kotlin状態／座標28項目。
未実施：Minestomプロジェクトへ組み込み、Java25/26.2でのビルド、Minecraftパック読み込み、実機の字体・Glow・FOV・cursor・音量・操作感。

ゲーム向けpack formatは、調査したProjectSの26.2向けpack.mcmetaの `[88,0]` を利用。ローカル実行版が異なるならそのバージョンの値を確認してください。

## 再生成

```powershell
python -m pip install -r tools/requirements.txt
python tools/export_polish05.py --html reference/ProjectS_UI_Polish05_Workbench.html --output . --chromium "C:\Program Files\Google\Chrome\Application\chrome.exe"
python tools/build_resource_pack.py --root .
python tools/verify_import.py --root .
```

Chromeのパスは実際に存在するものを指定。Playwright同梱Chromiumを利用する場合は事前にそのbrowserを用意してください。
ゲーム用sound cueの再生成にはローカルのffmpegが必要です。通常は生成済みファイルをそのまま使えます。

`native` の独立テストはKotlin compilerで `src/main` と `src/test` の3ファイルをコンパイルし `Polish05ChecksKt` を実行。
この確認はMinestom互換性のテストではありません。

## 出典と扱い

原本はこの会話で承認されたPolish05。画像・音の個別ハッシュは `assets/manifest.json`。
一部素材は過去の生成コンセプト由来、大剣等はProjectS内アセット由来、音は元HTMLに含まれるMinecraft音源と加工済みcueです。
新しく描き直した絵やフォントファイルは含みません。ゲーム用bitmap font JSONは抽出した原画の表示定義です。
このキットは私的な試作・移植用です。第三者由来素材やMinecraft音源の一般公開／再配布の権利が確認済みであるとは主張しません。
