# 添付GIFの踏み込み斬りを、そのまま復元

## 採用物を固定

Creator提供 `C:/Users/xgaiz/Downloads/download.gif` は、以前の `.tools/greatsword-sweep-eye-v3.gif` と
SHA-256が完全一致した。対象は「踏み込み斬り／空振りと命中」の2列のプレビュー。

`497a6e17ec93f83f8aca8851d6d0e6919fea1c0be6ea1ca21177643a6de31828`

元実装は `c34c624`。似た輪郭を再制作する、短い棒へ分割する、勝手に太くする、
配色を赤くする、といった解釈を加えない。今回の採用版では白・鋼色と小さい暖色の命中光も元のまま。
他の戦士スキル、他職業、大剣本体、既存の音修正、ダメージ・命中数・CD・職業基盤・クライアントは変更しない。

## 復元したもの

- `CoreApprovedDashV3.kt`：当時の位置、大きさ、角度、PREPARE/PULSE/CONTACTのコマ順。
- `build_approved_dash_v3.py`：当時の輪郭生成処理を独立した固定版として保存。
- `combat_vfx/approved_dash_v3/`：35モデル＋35item定義。元の35モデルを別の作画へ置き換えない。
- `approved_dash_v3_manifest.json`：当時の70定義と参照テクスチャのGit blob ID、承認GIFのSHA-256。
- `CoreSkillChoreography`：戦士の `dash` は新しい8区間版より先に、この固定版へ送る。
- `CoreCombatMeshes`：この固定版だけ変形補間0。コマの中身が変わる固定姿勢の20Hzアニメーションに、
  表示開始時のゼロscale→全体scale補間を重ねない。他のflow補間は従来のまま。
- `CoreSceneParticles`：採用GIFに無い追加の粒子を、この技へ重ねない。

元版は、薄い湾曲面の輪郭を1tickごとに別のnativeモデルとして表示するもの。
完成PNG全体を回転するものでも、現在の細い区間を順に太らせるものでもない。
刀身7tick／残光13tick、命中光は実際に受理されたヒットの位置だけ。実際の準備時間は既存のAS処理に従う。

## 一致の検証

1. 元Git blobと比較して、名前空間の置換を除く70定義と参照PNGの完全一致を確認。
2. 現在のruntime routeから、準備→斬撃→消滅と命中例の全コマをKotlinで出力。
3. 当時のレンダラーでその出力と復元モデルを描画。
4. 出力 `.tools/approved-dash-v3-restored.gif` のSHA-256がCreator添付と完全一致。

同じGIFをコピーした結果ではなく、復元した実装から再生成した一致。
この検査は元の画像・タイミングへ戻ったことの証拠であり、実機GPUやネットワーク遅延の証拠ではない。

## テスト／ビルド

- Kotlin関連124件成功。復元した元の8方位・実頂点検査を含む。
- Minestomの実ItemDisplayで全phase／全コマのモデルID、scale、座標、左右rotation、補間0、終了除去を確認。
- Python固定版検査2件成功：全71資源の一致、生成の再現、配信index。
- Vanilla 26.2実CuboidModelパーサーで35モデルを受理。不正回転軸の負例は拒否。
- native Display側でも補間0の最初の描画が、縮小・移動を挟まず指定の変形になるかを検査。
- 今回のテストではMinecraftのゲーム操作は行っていない。最終feelはCreatorの手動確認。

## 今回知っておくこと／再開点

- まず戦士の「踏み込み斬り」だけを採用GIFに一致させた。他技へ勝手に複製していない。
- 正確に復元する指示を優先。以前の赤い再デザインとは独立した名前空間に固定した。
- ソースRPとserver JARは更新済み。稼働中のサーバー／配信RP／Minecraftは触らず、反映は次の再起動時。

処理は、既存Skillイベント → `CoreApprovedDashV3.parts/pose` → `CoreCombatMeshes` → Vanilla ItemDisplay。
形が変わったらmanifest検査、順序が違えば `CoreApprovedDashV3.pose`、ゲーム内だけ縮むなら
`CoreCombatMeshes.interpolationTicks` と実ItemDisplay回帰テストを最初に見る。
共通の生成スクリプトを修正する際にも、固定版の一致検査を通すこと。

mainは変更しない。`play/gyai/class-armament-art` にcheckpointをcommit・通常pushする。
`.tools/`、`.kotlin/`、Downloadsの原本GIF、認証情報はcommitしない。
