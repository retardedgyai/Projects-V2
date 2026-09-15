# 霜の波紋：長軸を揃えた氷束へ変更（FIX-FIRST）

前記録 `mage-frost-rooted-rework-2026-09-15.md` は前checkpointの記録。
本記録がその後の変更。MatE／Wynncraft水準到達、Mage全技完成の報告ではない。

## 参照に戻って変えた点

R04の作者掲載GIFをブラウザーで再実見。
https://api.mcmodels.net/storage/product-images/17407/01M0T4TH5WTEXCAKZQNJ9EH2WK.gif

- 参照は太い青い根から複数の白水色の片が同方向へ伸びる。独立した放射針とは違う。
- 一度追加した八角形の根元塊は実投影で台座に見えたため採用しなかった。
- 外側は4本の広い主片と3本の細い片をそれぞれ構成。内側は短い3片。
- 片の長軸を束ごとに揃え、長い胴から短い先端へ折れる形に変更。
- 片を長軸周りに傾けて広い面を重ねる。根と先端を固定し、モデル全体は回転させない。
- 3群は異なる長さ・角度・面の傾き。短い枝の一つは主片の肩の内部から発生。
- 内／外のモデル原点をそれぞれの根へ移し、既存の接地処理が各束の下を調べるように変更。
  モデル側の逆移動と表示側の移動は同じ native units / 16。平地で二重移動させない。

## 作画の出所

前の作業で built-in image_gen による新規生成を行ったv02を使用。
R04のブラウザー画像を画風参照として渡した。作者の画像をpackへ入れていない。

- 原画: `assets/combat-vfx/mage-v5/sources/rime-faces-v02.png`
- 全文プロンプト・生成モード: 同じ場所の `rime-faces-v02.prompt.txt`
- 配布画像: `server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/combat_vfx/mage_material/rime_faces_v02.png`
- 原画と配布画像はbyte一致。今回Pythonで絵を描き直したり加工していない。
- 生成された微細階調は残っている。指定したピクセル面を完全達成したとはしない。

## 評価用プレビューの訂正

旧投影は面ごとの平均深度による描画順と、UV領域を整数画素へ丸めて切り出していた。
薄い輪郭面の組み合わせで、交差する面の前後や各段の作画が乱れるため、
その乱れをそのままゲーム内の造形不良と判定してはいけない。

`preview_skill_choreography.py --per-pixel-depth` は実面を三角形へ分解し、
各画素の深度と元画像のUVを直接サンプリングする。既存モードは残す。
検証用の正面投影と大きいcanvasも追加。小さい画像の拡大や演出の描き足しではない。
opaque/cutout用で、半透明ブレンド、GPU、照明、地形遮蔽、通信遅延は再現しない。
灰枠は身長1.8m。isoで奥の束が高く見える分には、奥行き投影が含まれる。
目線投影では、カメラ後方の面を透視除算前にUVごと切断する必要があった。
初回 `rime-crossed-eye-60.png` はこの処理が欠け、画面を覆う誤表示なので評価対象外。
修正後 `rime-crossed-eye-clipped-60.png` を実見。氷は画面下側に収まり、
誤表示を根拠にゲーム側のサイズを縮小する変更はしていない。

## 検証と限界

- Pythonの今回対象: rime 9件、投影4件成功（交差面の提出順非依存、UV、透明cutout、手前切断）。
- 実Vanilla 26.2で7モデルを読み、36,480頂点の回転と投影側の回転が一致。
- native atlasで参照150 combat sprites検出。stock-directory負例は0。
- サーバー出力metadataの8表示をVanilla Displayへ再生。削除前ゼロ・残存なし確認。
- Kotlin対象4クラスのXMLを確認: 20 + 11 + 6 + 4 = 41件、失敗0。
  最終モデル生成後も対象4クラスを再実行、processResourcesを含めBUILD SUCCESSFUL。
- 広範囲Python43件は失敗2。`pyre` frame 0が1,446 elementsで上限1,000超過。
  これは今回のrimeとは別の未完了炎素材。テストの上限を緩めて隠さない。
  もう1件は長い広範囲テスト中にrimeを再生成したため、import済み旧generatorとの不一致。
  その後、変更を止めたrime単独9件を再実行して成功。広範囲全成功とは報告しない。

## 実見の評価：まだ未達

`.tools/rime-crossed-front-60.png` は最新モデルの正面投影。
途中比較は `rime-solid-bundles-iso-45.png`（台座・不採用）、
`rime-layered-depth-60.png`（長軸の修正後、面の傾きを加える前）。
いずれもゲーム映像ではない。

読み取り専用レビューでも、参照より独立した氷束・青い刃の印象が強いと指摘。
正面では放射の構図は読めるが、短い割れ片、不均等な肩の厚み、面の粗い描き分けが不足。
高コントラストの長い筋に寄りすぎている。微細な数値差を品質到達と扱わない。
CONTACTの旧素材、消散全体、地形上の表示、全技の比較も未完了。

## 変更範囲と処理

最重要: `CoreMageFrostChoreography.kt` と `scripts/build_mage_rime.py`。
Mageの振付 → 根の位置決定 → 既存接地処理 → 不変モデルの高さ補間。
描画の確認だけがPython。クライアント、共有renderer、攻撃判定、半径、MP/CD、
入力、承認済みWarrior、UI、mainは変更していない。ゲームの起動・操作なし。
不具合時はまず配布packのrimeモデル・原画・index、次にMageローカル振付を確認。
既存の大量dirty差分は保全し、今回のcheckpointに混ぜない。
