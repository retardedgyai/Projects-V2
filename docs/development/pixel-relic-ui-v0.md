# 石板ピクセルUI v0

## 今回の目的

C案の石板・青墨・淡い魔力を、Minecraftのアイテムに馴染むピクセル表現へ寄せる。
写実的な石肌や滑らかな絵をそのままメニューに貼らず、段差のある縁、限定色のアイコン、
静かな本文面に分ける。情報は「絵で対象を識別、数字で判断、短い日本語で操作」。

既存の経済、強化確率、MOD抽選、制作、装備、採取、戦闘、セーブ形式は変更しない。
大剣モデル、HUD、装備Tooltipも維持する。バニラ26.2＋Minestom＋リソースパックのみ。

## メニュー

- 手帳は遠征・工房・保管庫の大きな絵付き入口と、小さな補助操作へ整理。
- 工房は対象装備や完成素材の絵、素材別アイコン、所持／必要数を表示。
- 保管庫は所持品だけを絵と数量で並べ、選択品の正確な所持数は常時表示。
- 地図・試練・採取育成・補給にも同じ図像と状態表現を使用。
- 選択だけでは消費しない。消費は確認した条件と描画時revisionでサーバーが検証。
- 素材不足から精製・補給へ進み、元の装備／レシピ／Tier／数量へ戻る動線を維持。
- 取り消し不可、全MOD消去、数値低下などの重要な注意は絵だけに置き換えない。

## 描画と制約

背景は384×222 GUI px、押せる場所は既存の中央54スロット。
2〜3行のカードは矩形内の全スロットを同じ操作へ結び、画像だけ大きくして判定が小さい状態を作らない。
左右の絵は表示専用。下段9列×3行とホットバー9枠はバニラの位置を維持。
カード選択で表示を切り替えるが、マウス位置が必要なカード全面hover演出は実装しない。

原木／板材、鉱石／金属材、原石／加工石、獣皮／革、草／布を別シルエットにした
独自32pxアトラスを使用する。主入口は32px、素材や補助操作は16px。
これは装備カテゴリの挿絵であり、任意装備を回転できるリアルタイム3D表示ではない。

日本語は従来の10px原画から20px原画へ変更し、表示寸法は据え置く。
不透明／透明の二値で輪郭を作り、文字の内側の隙間を保つ。
通常のチャットや英語・日本語の標準フォントは上書きしない。
外周の石はGUI1px単位、本文は半GUIpx単位。低密度文字の拡大とは区別する。

GUI倍率による表示限界は残る。幅400 GUI px以上を推奨し、自動で設定を書き換えない。
パック未適用時は通常アイテムと省略前の日本語Tooltipへ戻す。

## 実装の入口

`CoreLoopMenus`（画面・操作）→ `CoreMenuCanvas`（カード・絵・文字の配置）→
`CoreMenuInventory`（クリック／画面世代）→ 既存`CoreMenuHost`／ledger（実行）。

- `CoreMenuArt.kt`: 29用途の絵と実アトラスのadvanceを照合。
- `scripts/build_core_menu_art.py`: 元絵のセル分割・32px化・フォント定義。
- `scripts/build_core_menu_assets.py`: 石板の枠・ボタン・カード・日本語アトラス。
- `scripts/verify_core_ui_assets.py`: 標準上書きの範囲、位置、透明度、glyph幅、素材識別を検証。
- `scripts/render_core_menu_preview.py`: 実builderのsnapshotからプレビュー。

壊れた時は`CORE_MENU_MISSING_GLYPH`、リソースパック応答、上記検証器と
`CoreLoopMenusTest`の実メニュー表示検査を見る。

## アートの由来と参考

使用アートはbuilt-in image generationで独自制作した2枚のソースから29用途へ割り当て。
元絵・生成指示・配信アトラス対応表は`assets/core-ui/pixel-relic/`へ保存した。
他サーバーのパックやロゴは取り込んでいない。

参考資料は[Hypixel Ravengard開発記事](https://hypixel.net/threads/dev-blog-13-a-look-at-ravengard.6023553/)
と[Wynncraft 2.1公式変更履歴](https://forums.wynncraft.com/threads/2-1-rekindled-world-changelog.316880/)。
前者は専用UI制作、後者はInventory／Blacksmith／Bank／Content Bookの刷新を紹介している。
今回のツールでは記事内画像の視認までできなかったため、細部を採寸して再現したとは扱わない。
ユーザー指定のMinecraftとの整合性に基づき、独自のピクセル表現として実装する。

## 検証記録

2026-09-05、`play/gyai/pixel-relic-ui`で検証。mainは変更しない。

- `:server-minestom:test :server-minestom:distZip --offline --no-daemon --max-workers=1 "-Pkotlin.compiler.execution.strategy=in-process"`: BUILD SUCCESSFUL。61 suite / 505 tests、失敗・エラー・skipは0。
- `scripts/verify_core_ui_assets.py`: PASS。238 assets / 20,608 glyph-font pairs、標準フォント上書きなし。既存の限定HUD sprite 50枚以外へ上書きを拡大しない。
- リソース内容SHA256: `c63aac03aea18a08ddbabdb327cd937cc1df862096d48428a3829248fce58086`。
- 実際のメニュービルダーが出力した9画面を`--strict`で描画し、文字欠落・省略・重なりのaudit warningは0。手帳、強化、精製、制作、保管庫、刻印に加え、素材不足、空の保管庫、強化+30上限も確認。
- [実データプレビュー](../../assets/core-ui/pixel-relic/previews/)に画像とauditを保存。日本語の原画密度を落とさず、2倍の描画面からプレビューへ変換する。

プレビューは実装済みのタイトル描画レイヤーの検査であり、実ゲームのスクリーンショットではない。
バニラの所持アイテムモデル、インベントリ名、hoverとTooltipは描画対象外。
Minecraftの移動やメニュー操作、最終的な見やすさの判断はCreatorが行う。
実装完了checkpointでは旧版を中断せず、後のCreatorの起動指示で再起動した。

2026-09-05 19:37 JST、`installDist`成功後に起動確認:

- サーバー `127.0.0.1:25565`、PID 12128。`Player connected`を確認。
- Vanilla 26.2、PID 21260。対話デスクトップ上のウィンドウがVISIBLE、非最小化、TASKBAR_ELIGIBLEであることを確認。確認時は前面ではなく、前面化やゲーム操作はしていない。
- 新パックSHA1 `5e146c56de78a4043f2d8cc0a1000afafb6d81ac`、277,505 bytes。サーバーログの`SUCCESSFULLY_LOADED customGlyphs=true`で受信・読み込み完了を確認。
- 起動ログ: `server-minestom/run/core-loop-20260905-193641.log`。新UIのゲーム内メニュー操作によるManual SmokeはCreatorが行う。

## 確認時に知っておくこと

1. 図像と短いラベルは入口の識別用。正確な数値、装備のロール範囲などの詳細、重要な注意は残している。
2. 保管庫は所持品のみ8項目ずつ表示。万以上の一覧は概数で、選択品の右側とTooltipは正確な個数。
3. 大きなカードは中央のバニラスロット全体で反応する。左右パネルは説明専用で、独立した操作領域ではない。
