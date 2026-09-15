# 流星：作画主導の着弾と残り火（2026-09-15）

Verdict: **FIX-FIRST / WIP**。全10技の目標は維持。Meteorの完成、MatE／Wynncraft水準の達成、他9技の品質達成を意味しない。

## 変更

- `scripts/build_mage_meteor.py`: 凸の爆風破片を撤去。途中の「同じ巻いた角が並ぶ」案も却下した。
- R12の着弾を元サイズで再実見。R09の広い色面を再確認し、built-in imagegenで元の炎原画を生成。
- 原画は1774×887 RGB。透過要求は二度とも満たされず、市松が焼き込まれていた。透過済みと誤認せず、背景位置に面を作らないnative geometryへ変更。画像編集・切り抜き・縮小はしていない。
- 48×48主面と32×32直交補助面。色のまとまりをUVサンプルで束ね、既存1000 elements上限内へ整理。原画は無加工で配布PNGへコピー。
- 原画の前半5枚を一度だけ使用。後半は同じ炎塊の連結成分を上昇・外向き移動・縮小・冷却し、灰の破片へ移行する。灰色背景を煙として推測していない。
- 床の鮮烈な赤い二重円が主役として残る状態を変更。輪郭を少し崩し、着弾直後から灰色へ冷え、18frameまでに消散。
- `scripts/preview_skill_choreography.py`: native Y回転の投影対応、細いUVの最近傍サンプル、投影領域の局所化。プレビューに専用の炎やbloomを描き足していない。

Damage / Hit / 入力 / CD / MP / Kotlin / Warrior / UI / main / クライアント処理は変更していない。ゲーム起動・操作なし。

## 実見レビュー

参照: R12 `https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s`（155.70–156.70）、R09 `https://mcmodels.net/products/16393/mates-mythic-weapons-solar-scepter`。

- 主担当は着弾・アーチ・分離・冷却の実モデル投影を確認。側面でも炎の形が読める。
- 読み取り専用レビューでも、半球より炎の塊の分離へ改善、市松の大きな残留は見えないとの判断。
- **未達1: tick 13→14で、左右の噴き上がりからアーチへ主要な塊の形が飛ぶ。** 原画の空間的対応を描き直す必要があり、20 Hzで再生するだけでは解消しない。
- **未達2（改善済みだが最終合格でない）: 元版は炎から小さい点へ急減した。** 連結成分の冷却へ変更後、16の炎塊が18・19の暗い塊へ残り、21で少数片へ減ることを双方で確認。
- 新たな懸念: 暗い背景で冷却後の平坦な暗色は追いにくい。冷却前後の面の明暗は要調整。
- 交差面の継ぎ目が移動視点で目立たないか、実ゲーム照明、ネットワーク、手触りは未検証。

全31参照の制作後最終レビューは未完了。このターンで全31を再実見したとは扱わない。他9技へ未達の作りを展開しない。

## 検証

- 専用Python 5 tests成功。72モデルの再現一致、座標・UV・tint・既存budget、原画と配布PNGのbyte一致、背景をUV参照しないこと、正面/側面、冷却して終了することを検証。
- 最大elements: eruption 814 / meteor 529 / meteor_ring 79。上限変更なし。
- 全pack構造検証成功: 16230 assets / 50695 glyphs / 50 HUD。
- pack SHA256: `9e223ee56ee6789ff3f0526140a8230564ce3a762ac66ddbaccd0cfc7140aec1`。
- 新原画SHA256: `f7bd51648f32f5bfeb328e55d97a6b88742ce558fc37605f267f9aa0c0ca353d`。
- `.tools/meteor-painted-04-eye.gif`: 実Kotlin出力の62tickを配布モデルで投影。側面静止群も生成・確認。ゲーム画面ではない。
- Kotlinを変えていないため、このcheckpointでGradleは再実行していない。既存の実表示メタデータを使用。
- 旧v4の無関係な約2000差分を保全。pyreの既知のbudget超過を今回に混ぜず、全Mage suite成功とは扱わない。
- commit対象をindexから隔離したツリーでも専用5 tests成功（12.962s）。未採用v4のhelper変更に依存せず再現できることを確認。

## 原画・実装の場所と再現

- 原画・正確な二つのprompt: `assets/combat-vfx/mage-v5/sources/meteor-impact-atlas-v01.md` / 同名PNG。
- 主処理: `impact_plane()` が原画上の炎から面を作る → `pressure_burst()` が二方向へ構成 → 後半は連結成分を冷却 → nativeモデルへ出力。
- 重要ファイル: `scripts/build_mage_meteor.py`。
- 再現: `python scripts/build_mage_meteor.py` → `python -m unittest discover -s scripts -p test_mage_meteor.py`。
- 壊れた場合: 配布JSONの#2テクスチャ参照・原画byte一致・対象clip/frameを先に確認。その後、実表示時刻を使った投影を確認。
- 次は未達1の主要な塊のつながりを原画から修正。全10技の完成ゲートは閉じない。

Branch: `play/gyai/mage-visual-rework`。この文書を含むWIP checkpointを通常push。mainへのmergeなし。
