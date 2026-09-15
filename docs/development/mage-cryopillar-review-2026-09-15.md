# 氷の庭：主柱・根・持続動作の再制作（未達／WIP）

## 今回の範囲

Mage 全10技の再制作の途中。R01を基に氷の庭の本体を交換した。
テクスチャのみの差し替えではなく、台座と3組の結晶群をやめ、
主柱1・副柱1・低い青い根4の立体、形成順、保持、退場を変更した。
**MatE／Wynncraft 水準への到達を意味しない。全10技とも最終受け入れは未完了。**
ゲーム起動、クライアントコード、Warrior、UI、判定、MP、CD、pulse数は変更していない。

## 参照と実見の区別

[MatE本人の Cryomancer 動画](https://www.youtube.com/watch?v=LztyUoK2KGk) の R01。
既存31件のうち1件の追加観察で、別の参照として件数に加算しない。
既存静止資料は `.tools/reference-cryo-sequence-01.png` 左上。

前の観察で確認した形成:

- 60.3986–60.4986秒: 大きな氷柱はまだない。
- 60.5986–60.6986秒: 低い青い尖りが先に見える。
- 60.7986–60.9986秒: 高い主柱が現れ、低い副柱と根が残る。
- 61.2986–63.4986秒: 広い明るい上面・青い根元を持つ立体が持続する。
  視点変化を、周期的な縮尺アニメーションの証拠として扱わない。

この変更時に本編を停止・消音・コマ送りし、退場を再実見:

- 70.523704秒: 高い主柱と青い根・副柱が見える。
- 70.823703秒: 高い主柱がなくなり、地面近くに明るい短い先端が残る。
- 71.023702秒: 同じ場所にさらに低い明るい先端が残る。

作者内部の実装は不明。下へ退く表現を採用する根拠は上記の見える順番であり、
作者が同じ変形・描画コードを使っているという主張ではない。

## 変更した実装

- `scripts/build_mage_cryopillar.py`: 正面／側面の画面内輪郭から幅と厚みを取り、
  面取りした8面の柱を作る。単一平面や交差する2枚絵ではない。
- 柱と根を各140 native elementsで構成。初稿の48段・672 elementsは
  小断面が多く、投影で細かなノイズを作るため採用しなかった。
- RGB原画の背景は透過ではなくチェッカーが焼き込まれている。
  画像加工はせず、UVを各行の塗られた区間内に制限。
  内部の白い面は抜かず、出荷テクスチャは原画とSHA256一致。
- `CoreMageChoreography`: 根が0/1 tick、主柱3 tick、副柱5 tickで開始。
  同一モデル・同一Displayのscaleを1 tick補間し、完成後は保持。
  最後の8 ticksで下へ退く。全体の寿命48 ticks、後続pulseの判定時刻は従来通り。
- 地面の高さは既存 `CoreCombatMeshes` が解決。氷の根をitem原点に合わせ、
  最後は先端まで地面より下へ移す。地面のdepth testで隠れる前提。
  斜面・段差・透ける床・足元の空洞について実ゲームで未検証。
- 原画／立体を共有するが主柱・副柱・根の役割を分ける。
  旧 `garden_charge` と後続pulseの `garden_beat` は未刷新。完成扱いにしない。

## 原画の来歴

組み込み imagegen による新規制作。参照は上記R01の静止観察画像。
作者の動画／配布モデル／テクスチャを出荷RPへ取り込んでいない。

原出力:
`C:/Users/xgaiz/.codex/generated_images/01a060a6-6e32-7e13-a4cd-2730ddc429e9/exec-58bce261-e25e-4e00-803c-f2f1ecd0ea52.png`

保存先: `assets/combat-vfx/mage-v5/sources/cryopillar-faces-v01.png`

使用したプロンプト:

> Use case: stylized-concept. Asset type: production pixel-art texture for ONE original Minecraft ice pillar, not an icon, screenshot or mockup. Image 1 is a visual style reference ONLY: top-left tall crystal, specifically its long quiet luminous upper faces and layered dark blue roots. Create a square texture sheet with exactly two equal vertical panels, separated only by background: left a FRONT orthographic flat painted face of one tall slim ice blade, right its matching SIDE face at the same height. Both straight upright, no perspective or 3D turntable, no cluster. Each pillar face fills about 75% of its panel width and 90% height, width to height about 1:3. Long nearly parallel shaft, slightly uneven chisel-shaped sharp tip, dark grounded lower third with 3-4 thin rising blue slivers. Upper half mostly a broad pale icy cyan plane, one narrower cool cyan plane and an extremely narrow white edge; lower half deep blue, a few long angular fractures, not lots of small noise. Side panel a quieter darker matching facet, NOT a second crystal illustration. Left/right anchors and tip heights aligned. Pixel clusters designed on a roughly 32 by 96 logical grid for each panel, crisp stepped edges and long connected value shapes, no antialias, blur, gradients, noise or dithering. Real production RGBA transparent background, no checkerboard. Empty margins and empty space outside each face. No floating pieces, sparks, rays, snowflakes, ground, pedestal, UI, lettering, watermark or border. The texture will be mapped onto actual faceted 3D geometry; don't paint a fake isometric volume, rim-lit gemstone or a full crystal cluster into it.

指示通りのRGBAにはなっていない。成功した透過素材として報告しない。

## 確認結果と限界

- Python構造テスト4件合格: 原画同一性、座標・回転・UV、出力再現、pack登録。
- Kotlin対象3クラス・作業ツリー32テスト合格（既存の未commit frostテスト1件を含む）。保持中の不動、先端が地面より下へ退いてから削除、
  主副柱の高さ差、根が先に出ること、既存全10技のclock／observer制限を確認。
- Vanilla 26.2 `CuboidModel` が新規2モデルを受理。不正axisの負例も拒否。
- Vanillaのatlas列挙で新規テクスチャを含む148参照を解決。GPUでの描画確認とは別。
- 実際のサーバーDisplay metadataを未変更のVanilla `Display`へ再生し、
  6個の氷Displayの補間と削除前の地下退場を確認。
- `.tools/garden-cryopillar-native.json` は60fps補間の実装データ。
- `.tools/garden-cryopillar-review-54.png` などは実装モデルを投影した確認画像。
  **ゲーム画面ではない。地面の遮蔽、GPUのdepth test、通信、照明を再現しない。**
  地面下へ退いた体がこのプレビューに見える点は、実ゲームの描画判定の証拠ではない。

初稿は細い二本の柱に見え、根が隠れていた。面を整理し、主柱の幅と根の配置を変更した。
読み取り専用の独立レビューでは、その後も「根が柵」「上下の明暗が分断」
「主副柱が直立・平行すぎる」の3点が指摘された。根を太く斜めに張り出す
破片へ変更し、付け根を主副柱の根に寄せた。主副柱には逆向きの僅かな傾きを付けた。
再レビューでは、柵状の分離と平行な二本柱は改善し、一つの結晶群として読めると確認。
一方で下部が細長いV字形に寄り、参照の低く広い足元は不足している。
一部の根を低く外へ寝かせる構成、明暗の接続、副柱の固有の破断形は残件。
色だけを再調整して完了とはしない。最終静止確認は `.tools/garden-cryopillar-cluster-54.png`。
最終品質の証明はまだ不足。参照の斜めに張り出す輪郭・白い面のまとまり・
根元との一体感をさらに比較する。既存31件すべて／全10技の最終レビューも残る。

## 確認・不具合調査の入口

造形: `scripts/build_mage_cryopillar.py` → nativeモデル／item／texture／index。
動作: `CoreMageChoreography.parts/pose` → 既存Display更新 → 地面遮蔽。
欠損テクスチャならindexとmodel UV、浮くならground解決とmodel原点を先に確認する。
この作業は `play/gyai/mage-visual-rework` のWIPであり、本編へのmerge判断ではない。
