# スキルアイコンと職業フレーム

2026-09-08。Creatorがv3の斬撃・火球・盾を承認し、職業に合わせたフレームも依頼。

## 変更内容

- 全70スキルの旧図形アイコンを、承認されたピクセル画の方向で差し替え。
  承認済み3枚を採用し、残り67枚を内蔵image_genで1枚ずつ作成した。
- 7職業それぞれのフレームも内蔵image_genで1枚ずつ制作。
  戦士は鋼鉄と鋲、ハンターは木と蔦、メイジは魔晶と青銅、Starweaverは星銀、
  アサシンは黒鉄と紫、テンプラーは聖堂風の鋼と金、ヒーラーは白金と葉。
- スキル編成・奥義・ツリー等が参照するメニュー画像とHUDの画像を同じ原画から合成。
- 使用可能・20段階のクールダウン・資源不足・未解放を維持。
  クールダウンによる減光は内側だけで、外周の職業識別は全状態で保つ。
- 日本語フォント、UI配置、ゲームのスキル定義、セーブ、クライアント処理は未変更。
  共用メニューのARROW / ARCANEも既存の原画参照に従い新しい絵へ更新される。

## 原画・参照元

画風の参照はMonumentaの実際の能力spriteとHUD表示例。
https://github.com/Njol/UnofficialMonumentaMod
素材を直接転載せず、承認されたオリジナル3枚を量産時の画風参照に使用した。
不採用の旧70枚と最初の2つの生成案は再利用していない。

- `assets/core-ui/skill-art-prompts.json`：67スキル＋7フレームの完全な生成指示。
- `assets/core-ui/proposals/2026-09-08-v3/prompts.json`：承認済み3枚の生成指示。
- `assets/core-ui/skills/*.png`：70枚の32pxピクセル原画。通常のpack buildはこちらが正本。
- `assets/core-ui/skill-frames/source/*.png`：7種類の未加工フレーム原画。
- `assets/core-ui/skill-frames/*.png`：実表示用32pxフレーム。
- `assets/core-ui/skill-art-manifest.json`：原画と32px版のSHA256、寸法、承認済み区分。

生成時の出力パス解釈に不備があり最初のコピー処理が失敗したが、全原画は保持されていた。
正しい既存パスから74枚をコピーして復旧。画像の再生成や旧画像へのフォールバックはしていない。
スキルの大型生成原本はローカルの`.tools/skill-art-generation/skills/`にも保管し、commit対象外。

## 処理の流れ・調整箇所

1. `prepare_core_skill_art.py`：全入力の存在を先に検証し、スキルを最近傍32pxへ変換。
   フレームは角4点と辺4本を切り分け、各部分の透明余白を除いて面積縮小。
   アルファを二値化し、4pxの外周へ合成する。図形から絵を描く処理はない。
2. `build_core_hud_assets.py`：原画を24pxの内側へ合わせ、32pxの職業フレームを重ねる。
   右下のキー領域を確保し、23状態を従来どおり4列×6行のシートへ配置。
3. `build_core_ui_assets.py`：同じ合成からキー領域なしのメニュー画像を作り、item atlasへコピー。
4. サーバーは従来のglyph / model IDを参照。32px HUDの33px advance、既存座標は変えない。

最重要ファイルは`build_core_hud_assets.py`。絵が違う場合はmanifestと`skills/`、
枠が欠ける場合は`prepare_core_skill_art.py`の8分割合成、位置がずれる場合は
`test_core_skill_art.py`と`verify_core_ui_assets.py`から確認する。

## 検証と残る確認

- 70枚の16pxメニュー / 32px HUDを一覧で目視確認。
- `test_core_skill_art.py`：3テスト。70原画・7フレームの一意性とSHA、全70×24セルの一致、
  各状態での外周不変、内側の空白、クラス対応を検証。
- `verify_core_ui_assets.py`：680 assets / 50,675 PUA glyphs / 50 scoped vanilla HUD overrides。
- サーバーの職業UI関連56テスト成功（CoreClassBuildTest 20、CoreLoopMenusTest 27、CoreUiTest 9）。
  失敗0・エラー0、distZip成功。今回は全646件ではなく変更に対応する対象テストを実施。
- 旧checkpointとの比較で全70枚の原画差し替えを確認。`git diff --check`成功。
- 日本語本文・強調文字atlasに変更なし。Kotlin / Gradle設定 / protocol / client-fabricに変更なし。
- `skill-art-preview.png` / `skill-frame-preview.png`は実配布PNGからの比較画像。
  Minecraftのスクリーンショットではなく、実プレイの視認性判断は未実施。
- 32pxという制約上、原画フレームの細密装飾は整理される。職業の材質・配色・角の違いを優先。
- この作業ではServer / Clientを再起動していない。起動中のゲームへ新packが反映済みとは扱わない。

Creatorが把握する点は、全70枚と7フレームが対象、既存の配置・フォントは維持、
実プレイ確認は再起動後、の3点。
