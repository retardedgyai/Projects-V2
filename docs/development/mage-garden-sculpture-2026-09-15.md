# 氷の庭・立体造形の再実装

## 状態と範囲

前回 v2 の10技プレビューは Creator が「クオリティが著しく低い」と不合格にした。
前回の独立レビューも相対評価が甘かった。テスト成功やレビュー実施を見た目の合格としない。
今回は `mage_garden` 一技だけを改修する。ほかの9技へ展開しない。
入力・ダメージ・PULSE回数・CD・Warrior・UI・クライアント処理は変更しない。
ゲームは起動せず、実モデルの主観／側面／俯瞰投影を確認する。

## 造形と演出

- 既存原画の一律押し出しを庭では使用しない。
- 結晶は八角断面を持つ長い立体で、軸の途中から細まり、短い結晶には欠けた肩を持たせる。
- 広い青い内部、狭い明るい稜線、暗い根元を、立体の面に沿って配置。
- 外周は角のある割れた氷床。違う高さの破面が同じ発生地点として根元をつなぐ。
- 初回だけ氷床と主結晶が形成され、短い結晶が少し遅れて成長。
- 実際の後続PULSEで、根元の亀裂から結晶へ短い明るい筋が進む。本体を再生し直さない。
- 終盤は斜めの断面から塊が離れ、沈下して消える。最後のフレーム切替で大きい塊を消さない。
- 飛ぶ破片は独自のnative model。バニラの粒子を重ねない。

輪郭は意図した0.5モデル単位の段差を持つ。体積を細かいブロックの集合として配信するのではなく、
外側の同色面をまとめたItemDisplay用モデルとして配信する。内部面は作らない。
PNGを新たに生成・編集せず、モデルの形と色面をコードで制作した。

## 参考・レビュー

- 承認済み大剣原画は広い明暗面と輪郭整理の基準。蛇行する剣形を氷へ移さない。
- [MatE / Cryomancer](https://www.youtube.com/watch?v=LztyUoK2KGk) の前回保存した24／38／50秒の
  ブラウザー画面を再比較した。今回の動画Web取得は失敗しており、新たに全編を観察したとはしない。
- 読み取り専用の別レビュー担当に造形単体、次に庭全体を比較させた。
  厚板感の改善に加え、水晶状の短い先端、広すぎる白面、柔らかい氷床外周、維持中の作動情報不足を修正。
- ゲームの照明・地形遮蔽・ネットワーク・音・実際の操作感はこの投影では検証できない。
  参考作品と同水準、またはCreator合格とは宣言しない。

## 主な処理とファイル

1. 既存の戦闘処理が確定したフェーズを `CoreMageChoreography` へ渡す。
2. 庭のみ `garden_*` 素材を選び、初回本体／後続の短い局所演出に分ける。
3. `CoreCombatMeshes` の既存表示・人数制限・掃除で動作する。
4. `scripts/build_mage_garden.py` が結晶面・床・破片・光の進行を生成。
   `build_mage_materials.py` 経由でRPのモデル／item／indexへ反映。

最重要は `CoreMageChoreography.kt`（配置と時間）と `build_mage_garden.py`（造形と色）。
向き・位置がおかしい場合は前者、欠けや素材欠落は後者とRP indexを最初に確認する。

## 検証

```text
python scripts/build_mage_materials.py
python -m unittest discover -s scripts -p test_mage*.py
python scripts/verify_core_ui_assets.py
gradlew :server-minestom:test --tests *CoreMageChoreographyTest --tests *CoreCombatMeshTest --tests *CoreSkillChoreographyTest :server-minestom:distZip --no-daemon --offline -Pkotlin.compiler.execution.strategy=in-process --max-workers=2
python scripts/preview_skill_choreography.py --timeline .tools/mage-material-timeline.json --ids mage_garden --prefix garden-v3-eye --view eye
python scripts/preview_skill_choreography.py --timeline .tools/mage-material-timeline.json --ids mage_garden --prefix garden-v3-iso --world-scale 28
```

投影データは隔離Minestomテストの実ItemDisplay metadata。
形状検証には表面積保存、体積と先細り、消散の終端、nativeモデル上限・再現性を含む。
`.tools/`／`.kotlin/` はコミット対象外。branchは `play/gyai/mage-visual-rework`、mainは変更しない。

### 今回の結果

- PythonのMage素材／立体検証：6件成功。
- Kotlin関連3クラス：52件、失敗0／エラー0。全855件の再実行ではない。
- 上記targeted testと `distZip` 成功（1分17秒）。
- 配布ZIP内server JARとbuild/libsのJARが一致。
  同梱RPのindexおよび全16,175項目も現在のソースとバイト単位で一致。
- 新規240モデル＋240item定義。既存9技の素材ファイルの変更なし。
- RP content SHA256：`76351a7126a6c1d351e1b001324443b441e903cebfbac0cca4172f8383dee90f`
- 配布ZIP SHA256：`ad7c3ad785a1860e4422053ed9d8d9650aba40dda06dceceb7ecd69dfab310aa`
- 途中で旧形状の終盤が1,000要素を超えたため、面結合と形状整理を行った。
  最終版は既存上限を緩和せずに全素材検証成功。
- 最終プレビューは `.tools/garden-v3-eye.gif`／`.tools/garden-v3-iso.gif`。
  ゲーム画面ではなく実データの投影。末尾の空白を含む64フレーム、20Hz。
- 最終の独立した静止フレーム比較では、角のある氷床・局所変化・消散の改善を確認。
  氷床の広い面の単色感と、主観での局所発光の控えめさは残る。
  このcheckpointを他技へ品質保証付きで展開する根拠にはしない。
