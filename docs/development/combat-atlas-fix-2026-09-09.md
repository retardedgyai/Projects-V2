# スキルが紫黒の四角になる不具合

ユーザーの指示で武器制作を中断し、テスト再開の前に修正した。

## 原因と修正

自作のスキル画像は `textures/combat_vfx/` にあり、モデルからも同じIDを参照していた。
しかし26.2の標準 `items` アトラスはこのディレクトリを列挙しない。
画像の同梱とモデル構文の検査だけでは、missing textureを検出できていなかった。

- `assets/minecraft/atlases/items.json` に `combat_vfx` のdirectory sourceを追加。
  既存のバニラ画像を消すfilterや差し替えはない。
- パック配信の許可リストにはこのアトラス1件だけを追加。フォント等の禁止は維持。
- 戦闘モデル生成スクリプトにも同じ出力を追加し、再生成後も修正を維持する。
- `CoreCombatAtlasTest` は全戦闘モデルの自作画像参照・索引・PNG・アトラス登録を検査。
  修正前はアトラス未登録で失敗することを確認済み。

## 検証

- 関連Kotlinテスト109件成功、武器確認パックのPythonテスト6件成功。
- 変更していない26.2クライアントJARの `SpriteSources.FILE_CODEC` と実際の
  `DirectoryLister` / `MultiPackResourceManager` を使ったheadless検証で、
  自作の参照画像136枚をすべて列挙できた。標準itemディレクトリだけでは0枚。
  ソースパックと実際に起動する確認用パックの両方で成功。
- 戦闘モデル3192件を26.2の `CuboidModel` パーサーが受理。
- installDist成功。大剣T1のみ赤い刃アニメーション試作へ替えた確認用パックを再構築。
  ほかの装備/UIは通常版。作りかけの新メイス・中止した防具は追加していない。
- 2026-09-09 03:49 JST、通常プレイヤー接続と `SUCCESSFULLY_LOADED` をサーバーログで確認。
  配信SHA-1: `b88fcc6d03df532e287203a36e42242c2d9d400a`。
- Minecraft 26.2のウィンドウ表示・非最小化・タスクバー対象を確認し、
  サブモニターへ配置。ゲーム入力やスキルの代行操作はしていない。

## 残る確認

headless検証はGPU描画や実戦の見え方の合格判定ではない。
全スキルの実際の表示・向き・サイズ・動きの最終確認はユーザーの手動テスト。
バグが残る場合は `CoreCombatAtlasTest`、配信zip内のアトラス、
`CheckNativeCombatAtlas.java` の順で参照解決を確認する。

`place-harbor-preview.ps1 -CorePlaytest` は、このworktreeの既知の引数ファイルで起動した
Javaクライアントだけをサブモニターへ動かす。既定の港プレビュー用制限も維持する。
