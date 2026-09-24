# ProjectS / Polish05 — ネイティブ移植の開始指示

## 目的

ユーザーが承認した `reference/ProjectS_UI_Polish05_Workbench.html` を Minecraft内へ移植する。
**これはデザイン依頼ではない。似たUIを作り直さず、Polish05を見た目と操作の正本にする。**

成功条件は、同じ装備・同じ状態を開いた時に「今までブラウザで触っていた、あの工房がMinecraftに入った」と分かること。

## このキットの到達点を取り違えない

含まれるもの：元HTML、原画26点、元の音声10点、ゲーム用音声cue、画像パック、14状態のブラウザ比較画像、実測レイアウト／操作領域、Kotlinの独立サンプル状態モデルと座標変換。

**まだ存在しないもの：このキットを描画するMinestomアダプタ、パック配信との接続、ゲーム内の入力・動的文字・状態同期、Minecraft実機検証。**
パックを読み込んだだけでは工房は開かない。参考用の画面一枚絵を貼って「操作まで移植済み」としてはいけない。

## 0. 最初にすること

1. このローカルcloneの `AGENTS.md` と対象ディレクトリのルール、`git status`、現在branch、worktree一覧、`git config --get projects.creator` を確認。
2. 他の作業を壊さず、独立Playgroundを作る。既存の同一タスクbranchがあれば継続する。新規名の候補は `play/<creator>/polish05-native-ui`。
3. キットの `tools/verify_import.py --root <キットの場所>` を実行して原本と資産を照合する。
4. ブラウザで正本HTMLを開く。文章だけで画風を判断しない。右側、剣の大きさ、光、左の補充欄、音のタイミングを見る。
5. 以下は調査時点の参照であって、ローカルに最新の未push変更があるなら優先して保護する。

確認したmain：`f534f8465657c8e4e4e08a80aa7a1f659b207598`。
参照branch：`play/gyai/web-ui-lab`、`play/gyai/server-pack-ui-smoke-20260923`。
古いbranchを本編へ丸ごとmergeしない。現在作業中のbranchへ無断pushしない。

## 1. 守るもの

- バニラクライアント＋Minestom＋サーバー配信Resource Packを第一経路とする。必須Fabric/MCEF/WebViewへの変更はしない。
- 既存 `web-ui-lab` は本物のブラウザではない。640KB級の元HTMLを `ui/forge.html` へ置いても動かない。限定HTMLパーサーへ再記述して見た目を落とすのも不可。
- 初回は独立ラボのダミーデータで確認する。ゲーム本編のセーブ、CoreAccount、実通貨、確率、アイテムを変更しない。
- 本編サーバーを止めない。必要ならラボの空きポートを確認する。既存ラボ25570を他タスクが使用中なら別ポートへ分離する。
- right/result-column はユーザーが「完璧」とした領域。配置・横幅・文字階層・消費表示・成功率・触媒・確定ボタンを再設計しない。
- 「全部をピクセルにする」ではない。原画をシャープに保ち、Glow・影は別レイヤーで馴染ませる。枠と文字は主役を奪わない。
- HTMLの見本値を本編仕様として採用しない。強化+30上限、80%、触媒100%、成功例再生はラボのfixture。
- main直接push、force push、他人の変更消去、勝手なPR merge／本番配備は禁止。

## 2. 実装経路

既存ラボで参照する箇所：

- `web-ui-lab/src/main/kotlin/dev/projects/webui/UiSessions.kt`：専用camera・private entity・入力・復帰。
- `.../UiRenderer.kt`：retained描画、初期metadata設定後のspawn、owner限定viewer。
- `.../UiGeometry.kt`：奥行きによる遠近差補償。現行800×480。
- `.../ForgeDemo.kt`：既存ダミー操作の入口。Polish05モデルとは混同しない。
- `.../WebUiLab.kt`、`scripts/start-web-ui-lab.ps1`：独立起動。
- `server-minestom/.../coreloop/ui/CoreUiPackServer` 周辺：パック配信の既存実装を読み、ラボ側に必要な最小部分だけ使う。本編パックを上書きしない。

### 実装順序

A. **一画面の実機描画を先に通す。** 元のフレーム、左、中央、右を正しい比率で表示し、同じ大剣原画・炉・Glowを置く。この段階の静止校正画像はあくまで校正であり、操作完了ではない。
B. **実測座標と同じ操作判定を接続。** gear選択、比較、触媒、確認／取消、確定を実装。見た目とclick領域の別管理は禁止。
C. **状態を更新。** 成功／失敗、素材消費、左の記録、次の強化、欠品、最大強化。
D. **補充→精錬→同じ装備に復帰。** 原石・銀貨の両方で最大数を制限する。無料補充や自動確定は禁止。
E. **装備庫と音。** 装備選択を引き継ぐ。成功音は状態commitが成功したときだけ。先行再生して結果を示唆しない。

### 描画

- 1440×920が正本の論理canvas。`layout/forge_initial.json` 等はこの座標系。
- 既存800×480へ入れる場合は `Polish05ScreenSpace` のように **同じ倍率** でletterboxする。横と縦を別倍率にしない。pointer座標は同じ変換の逆写像で戻す。
- 背景・枠・装飾は元画像と元CSSからのテクスチャとして描画。大剣をSVGの剣やminecraft:iron_swordへ置き換えない。
- 2種の大剣は別画像で、HTMLに実寸指定がある：熾火276px、灰燼240px。元PNGの縦横比を変えない。
- `assets/layers/weapon_glow_isolated.png` は独立RGBA。通常の文字描画経路では弱いalphaが消える可能性があるため、**実クライアントの描画結果を確認**する。
- alpha経路がGlowを再現できない場合は `assets/plates/forge_environment_with_glow.png` のような背景合成を使い、刃本体は別描画。必要な発光状態を事前焼き込みして切り替える。勝手に巨大なMinecraftの輪郭Glowや別の強いBloomへ置換しない。
- `resourcepack/assets/projects_ui_polish05/font/sprites.json` は原画用bitmap provider。日本語の字形／字幅を実装済みだと解釈しない。フォントファイルは含めていない。ローカルで許諾のある既存フォントから必要文字をラスタ化するか、既存ProjectSのフォントが同じ表示になるか確認する。
- 静的パネルの余白・罫線は画像化できるが、通貨・個数・装備値・hover・不足は動的に保つ。全状態を一枚絵で固定しない。
- TextDisplayのatlas/glyphサイズ制限、alpha、scale、UV、baselineを実機で検証。単純な「画像サイズ＝glyph advance」の仮定をしない。マニフェストはexpected値であり実機保証ではない。
- パック読み込み成功前にcustom glyphを表示しない。拒否・失敗なら通常メニュー／安全に閉じる操作を残す。
- private表示、全表示体の破棄、カメラ・ゲームモード・ホットバー等の復帰を必ずテストする。

### 入力の正直さ

VanillaではブラウザのI/R/M/EnterやIMEをそのまま検出できない。既存ラボは左クリック・Shift・閉じるで操作する。EscはVanillaの一時停止である。
実際に使えないキーを「使える」と表示しない。**この入力ラベル差分だけは見た目の必要な例外として報告**し、クリックで全操作できるようにする。
画面の形やスロット配置をチェスト54枠へ戻す理由にしない。FOV・解像度を自動取得できると仮定しない。

## 3. ロジック

`native/src/main/.../Polish05PreviewModel.kt` はこのキットでコンパイル・テストした**依存なしのサンプル状態モデル**。
ラボ内で一人一インスタンス。実サービスへは未接続。表示用fixtureと明示して使うか、既存ラボの状態管理へ移植する。

処理は quote → confirm/begin → サーバーscheduler → finish → receipt/render/sound。
選択変更でquote失効、busy中の変更禁止、残高再検証、同一確定の二重消費防止、結果の二重適用防止を備える。
`finish`や成功指定をクライアント入力へ公開してはいけない。音の変更を理由に支払いタイミングや本編確率を変更しない。

`layout/*.json` の操作領域には `dataset`、`disabled`、`receivesPointer`、`modalOpen` がある。
**modal中に背面ボタンを反応させない。** `receivesPointer=false` の領域を有効ターゲットとして取り込まない。
受信するのはallowlistの意味的actionとserver発行quoteIdだけ。任意JSやコマンド文字列を実行しない。

## 4. 音

標準は `projects_ui_polish05:ui.enhance_success`。エンチャント＋の音声を含む。
元の音程・短い立上がり・余韻を保ち、金床＋XPの旧成功音に戻さない。
`layout/audio_cues.json` のgainは**ゲーム用cueでは焼き込み済み**。二重にgainを掛けない。
初期マスター音量35%。UI音は当人だけに鳴らす。hover無音、クリック70ms抑制、最大4voice、ミュート・閉じる・切断で停止。
準備音は静かに、0.72秒後の結果commitと同時に成功音。失敗に成功音を鳴らさない。音は表示より先に結果を確定させない。
ブラウザのcompressorとゲームの音量経路は同一でないため、音量・距離減衰・stereo/非定位は実機比較する。

## 5. 判定と最終報告

まずtargeted Test / Build、次にCreatorが実機操作してfeelを判定する。自動検査と実機品質合格を混同しない。
ブラウザ画像は `reference/screens/`。再出力は `tools/export_polish05.py`。同じPCのブラウザとMinecraftを、同じ表示サイズに揃えて比較する。
差分を字体・サイズ・光・色・操作・環境に分けて報告し、右の配置を差分吸収のために変えない。

最終報告には：変更箇所、起動コマンド／ポート、Test/Build、実機未確認項目、変更branch/commit、既知の視覚差・キー差を記載。
「動く」と「ユーザーが好きだった見た目のまま」を別々に判定し、後者が崩れるならFIX-FIRST。
