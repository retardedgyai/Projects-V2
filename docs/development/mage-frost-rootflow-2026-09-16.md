# 霜の波紋：枝の構成・低い根の流れ・収束（継続）

## 変更と実見

前版の「平行な長板を束ねた翼」を残したまま雷へ展開しない。
MatE R04 の[掲載GIF](https://api.mcmodels.net/storage/product-images/17407/01M0T4TH5WTEXCAKZQNJ9EH2WK.gif)
と[作者動画](https://www.youtube.com/watch?v=LztyUoK2KGk&t=37s)を再確認した。
正確な時刻と観察限界は `mage-reference-census-2026-09-15.md` 冒頭。

- 全6束を個別構成。主片の縮小コピーを平行に足す方式を外した。
  主片途中の肩へ短片を接ぎ、根元・接続高さ・向き・幅を別々に指定する。
- 低い内側の氷から外側の厚い根へ延びる3本の低い片を追加。
  環状の台座や円盤で地面を埋めず、中央を空けて部分的に重なる。
- 新しい面用原画を使用。白い細線・縞の代わりに大きな白水色／青の領域。
- 高さのみの縮小を外し、各束の接地点を中心に縦横比を保って収束。
  外側の終了をずらし、内側、最後に低い根が消える。
  動画で確認できなかった派手な破片飛散は追加しない。
- 不採用の槍・原画輪郭抽出・交差面生成コードをこの生成スクリプトから除去。
  過去の原画ファイルは削除していない。旧試作のテストではなく現行の束を検査する。

読み取り専用レビューでは、長板の翼がかなり解消し、短い枝・厚い肩・氷自体の面が
読めると評価。一方、束が独立した景物に見えるとの指摘を受け、低い根の接続を追加。
レビューは静止画で、動作の合格を意味しない。

## 原画・imagegen

imagegenスキル、built-in生成。MatE R04のブラウザー表示を素材の画風参照として
新規原画を生成し、作者画像そのものは取り込んでいない。

- 原画: `assets/combat-vfx/mage-v5/sources/rime-calm-faces-v01.png`
- 全文プロンプト・モード: 同フォルダー `rime-calm-faces-v01.prompt.txt`
- 配布: `server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/combat_vfx/mage_material/rime_calm_faces_v01.png`
- 元出力: `exec-ef4f9520-d80b-4bca-a07c-eeeb99a82b95.png`

原画と配布PNGはbyte一致。透明化・縮小・描き直し等の画像後処理なし。
大きいピクセル領域は得られたが、領域内の弱い濃淡は残っており、完全な単色16px原画
という要求に厳密一致したとはしない。面のUVで元画像を直接参照する。

## 検証と残る仕事

Python対象11件、Kotlin対象43件成功。最終モデル7個・26,080頂点をVanilla 26.2が
パースし、簡易投影との回転一致を確認。実サーバーmetadataの8表示を標準Displayへ
再生し、ゼロスケールでの終了・残存なしを確認。native atlasは150参照素材を発見。
stock-directory負例は0。これらはGPU描画や美術的な合格を証明しない。

`.tools/rime-rootflow-native.json` は今回のmetadataと標準補間の出力。
`.tools/rime-rootflow-final-phases-{33,39,45,60,102,120,138}.png` は実モデルの
簡易投影。0.55秒の直前、0.75秒の形成、1秒の保持、1.7秒以降の段階的な収束を確認。
ゲーム映像ではなく、実ゲームの地形・遮蔽・画面密度・ネットワークは未確認。

霜の波紋のCONTACTは旧 `ice_hit` のまま。全31参照との最終照合、全10 Mage技の
再制作と品質達成も未完了。今回の束の改善だけを最終達成としない。
ゲーム起動・操作なし。ダメージ、判定、半径、MP/CD、入力、client、共有renderer、
Warrior/UI、mainは変更なし。旧dirty差分をstageしない。

主要ファイル: `build_mage_rime.py` の `BUNDLES` / `broken_plate`、
`CoreMageFrostChoreography`。生成した固定7モデルを各地面へ配置し、標準補間で
発生・保持・収束する。表示不良時は先にpackの新原画とindex、次に面UVと束の配置を見る。
