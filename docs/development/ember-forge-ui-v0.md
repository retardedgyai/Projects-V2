# 炭火工房 UI v0

## 目的と変更範囲

2026-09-05、`play/gyai/ember-forge-ui`。石板ピクセルUIに対する「業務的・アイコンを載せただけ」
というCreatorの評価を受け、暖かい炭色と古い金属、対象装備を中心にした構図へ変更する。
単なる配色変更に留めず、強化対象・結果・方法・消費素材の優先順位を作る。

- 均等な三分割線、明るい二重枠、青灰色の大きな面を外す。
- 背景は低コントラストの炭色の石、外周だけに摩耗した真鍮の留め具。
- 通常操作は控えめな刻み、選択中は金色の縁、確定操作は暖色の面を使う。
- 中央の装備と強化段階を一組にし、通常／触媒の選択は脇へ移す。
- 常設のシステム説明を減らす。重要な消費・失敗・取り消しの注意は実行前に残す。
- 本文、性能の変化、見出し・操作名を別の文字スタイルにする。

戦闘、採取、経済、強化確率、MOD抽選、セーブ形式は変更しない。
大剣の3Dモデル、HUD、装備Tooltipの詳細情報、バニラの言語・フォントも維持。
Vanilla 26.2 + Minestom + サーバー配信リソースパックのみ。

## 操作と描画

中央54スロットとプレイヤー所持品36スロットの位置はバニラのまま。
工房のタブ、対象装備、素材入手先への寄り道、元の状態への復帰、確定時revision検証を維持する。
中央の装備にも従来の装備Tooltipと詳細画面への操作を用意する。
絵だけを大きくしてクリックできる範囲を小さくしない。

強化画面では詳細15、通常24、触媒33、素材42、確定51を先頭slotとする各3枠。
戻るは45の1枠に「←」と遷移先Tooltipを置き、＋29 → ＋30の表示と分離。
装備の18枠は18〜23 / 27〜32 / 36〜41。台座上10pxは透明、装備画像はy54、
実際の不透明画素はy102まで、強化値の文字画素はy102以降として、対象選択と重ねない。

`CoreLoopMenus` → `CoreMenuCanvas` → `CoreMenuInventory` → 既存`CoreMenuHost`/ledger。

- `CoreLoopMenus.kt`: 強化対象と結果の構図、所持数・必要数、原文Tooltip、操作と戻り先。
- `CoreMenuCanvas.kt`: BODY/EMPHASIS、実測幅、装備の焦点領域と矩形の重複禁止。
- `CoreMenuArt.kt`: 16/32/48 GUI pxの用途と配信アトラスadvanceの照合。
- `scripts/build_core_menu_assets.py`: 実寸の枠・台座・文字。背景は既存コードの改修で制作。
- `scripts/render_core_menu_preview.py`: 実builder snapshotを配信画像・文字幅で描画する。
- `scripts/verify_core_ui_assets.py`: glyph、台座、全フォント役割、標準上書き範囲を検証する。

## 字形とアート

本文は[Fontworks DotGothic16](https://github.com/fontworks-fonts/DotGothic16)の16px原画を
8 GUI pxで描画。16×16ビットマップを基礎に設計された字形を利用する。
見出し・操作・強調数値は既存Noto Sans JPのweight 500、20px原画 / 10 GUI px。
両方とも28pxセル / 14 GUI px、二値alpha、実bitmap右端からadvanceを算出する。
別々のメトリクスを使い、長い数値も役割に対応した幅で測定する。

DotGothic16 source commit: `14517183ab2f75e8bccafc5a0bff6685d268c687`。
TTF SHA256: `155da8f318553c11d9dffc2affbc7c2114c6a46f9740bcf639ed5568af92be71`。
SIL OFL 1.1を原文のまま配信。全TTFは配信せず必要文字だけをprivate-use glyphへ収録する。

アイコンは前回独自制作した原画を継続使用。今回のための画像生成はしていない。
中央の装備はカテゴリの挿絵であり、任意装備やMOD外見を映すリアルタイム3D表示ではない。
台座と金属の縁はネイティブのピクセル描画。外部ゲームのパックを取り込まない。

## 検証と残件

2026-09-05 21:37 JST（空の保管庫の文言修正後に再実行）:

- `:server-minestom:test :server-minestom:distZip --offline --no-daemon --max-workers=1 "-Pkotlin.compiler.execution.strategy=in-process"`: BUILD SUCCESSFUL、1m 40s。直前の統合時も2m 1sで成功。
- 61 suites / 514 tests、失敗・error・skipは0。個別検証はCanvas21件、メニューと工房の合計63件も通過。
- パック検証: 287 assets / 39,531 glyph-font pairs、全字形・二種類の実測advance・標準フォント上書きなしを確認。
- リソース内容SHA256: `0761b8f9bb98efb8a80d638560a29da4158abf65531a0c8dbf9c07f6a1aeca77`。
- [13実データプレビュー](../../assets/core-ui/ember/previews/): `--strict`の欠字・省略warning 0。武器、防具、素材不足、失敗蓄積、成功保証、+29→+30、+30上限、精製、制作、刻印、手帳、保管庫、空の保管庫。
- 全18装備枠のTooltip/操作、packed/unpacked、消費なし選択、条件不一致、遅延callback、素材への寄り道と復帰を検査。

オフラインプレビューは実装のタイトルレイヤー。実ゲームの所持品モデル・hover・Tooltipを代替しない。
GUI倍率での見え方と最終的な雰囲気はCreatorのManual Smokeで判断する。
特にGUI倍率1では半GUIpxの本文画素が縮小されるため、現在のGUI倍率2以上での確認を推奨。
Minecraftの設定や操作はAgentから変更していない。実装checkpoint時は旧版を中断せず、後のCreatorの指示で再起動した。

### 起動確認（2026-09-05 22:05 JST）

- 旧Minecraftを正常終了し、接続なし・保存checksum正常・activeRunなしを確認して旧サーバーを停止。
- `installDist`成功、サーバーPID 29868 / `127.0.0.1:25565`。
- Vanilla 26.2、PID 26708。対話デスクトップ上でVISIBLE / 非最小化 / TASKBAR_ELIGIBLE / FOREGROUNDを確認。
- 新パックSHA1 `481f3d72cde98f1237d22e10c4d8ebed03e0f885`、376,932 bytes。
- `server-minestom/run/core-loop-20260905-220437.log`でPlayer662接続と`SUCCESSFULLY_LOADED customGlyphs=true`を確認。
- ゲーム内の移動・メニュー操作・F2は行っていない。新UIのManual SmokeはCreatorが行う。

文字化けはパックの`SUCCESSFULLY_LOADED customGlyphs=true`と`CORE_MENU_MISSING_GLYPH`を先に確認。
操作ずれはCanvasの矩形検査、結果や消費の不一致は既存の見積り・ledgerを確認する。
