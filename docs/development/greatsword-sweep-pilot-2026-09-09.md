# 踏み込み斬り：形が変わる軌跡の試作

## 参照の取り違え防止

Creatorが指定したのは **SamusDev / Minecraft RPG Class Awakened | Dragon Warrior**。
https://www.youtube.com/watch?v=WR6NQB7NmgY

Kolord StudiOのDragon Knightではない。最初に開いた通常Warriorも今回の主参照から外す。

2026-09-09、公開プレーヤーをミュート・一時停止し、Dragon Comboの32秒台から34.5秒付近をコマ送りで部分確認した。細い紫の筋、大きな斜めの紫の面、その内外の濃淡、対象付近の金色の火花、武器自体の明部が分離して見える。コンボ中の異なる段を含む区間であり、一つの斬撃の正確な寿命・ヒット数・内部モデル方式を測定したとはしない。シーク直後は映像のデコードが遅れる場合があり、currentTimeだけを観察完了の根拠にしない。

Crimson Moonは以前の記録に作者の赤月動画があるが、今回はBilibiliで再生不可と表示された。
https://www.bilibili.com/video/BV1FBBABBENx/

代替として https://www.youtube.com/watch?v=ZrR6sFb6Dlg の14/22–23/29/44秒付近を確認。赤い鎌とボスの青い斬撃が同時に映るため、青い弧を赤月の技と誤認しない。この映像から赤月の複雑な斬撃の全構造を把握したとはしない。今回の直接の主参照は上記Dragon Warrior。全編視聴・音声精査・参照アセットの取得は行っていない。

## 前checkpointとの違い

旧 `directional_cut` は完成済みPNGの可視範囲を変える方式だった。
今回は **dash（踏み込み斬り）のみ**を別経路へ切り替える。

- 7個の刃先位置キーに不均等な間隔を設け、遅い出始め・速い中央・減速を表現。
- 過去の刃先位置を48区間でサンプルし、各区間の経過時間と速度から幅と輪郭を構成する。
- 各コマで新しい形が生まれ、以前の形が消える。固定した完成絵のクロップではない。
- 64段階のピクセル格子に輪郭を合わせ、厚みゼロの両面ポリゴンへ変換。浅い折れと高さの差を持つ薄い膜であり、立方体の積み上げではない。
- 白い外縁・中間色の面・暗い内縁。既存PNGから使うのは3色の不透明なインクの画素だけで、元の三日月の絵や透明輪郭は使用しない。新しいラスタ原画の生成・編集は行っていない。
- 残光は別モデル。古い区間から外へずれ、浮き、決めた切れ目で分かれる。主刃画像を遅れてもう一枚出す方式ではない。
- 命中時だけ別の閃光が破片へ変わる。空振りでは発生しない。
- スローな最初の3コマは実際のPREPARE時間に使う。PULSE開始でコマ3へ進み、命中後に振り始めをやり直さない。
- 既存の音・ダメージ・攻撃回数・開始時間・スキル範囲は変更しない。

## 実装の流れ・主なファイル

`CoreSkillChoreography` → `CoreGreatswordSweepChoreography` がPREPARE/PULSE/CONTACTを各層へ割り当てる。
`CoreCombatMeshes` は既存のItemDisplay配信をそのまま使う。
`scripts/build_greatsword_sweep.py` が35モデルと35アイテム定義を生成し、通常の `build_core_combat_models.py` にも接続。
`CoreSceneParticles` は変形モデルの静止した原点に不要な粒の雲を出さない。

形・幅が悪ければ `build_greatsword_sweep.py`、タイミング・向きは `CoreGreatswordSweepChoreography.kt`、テクスチャ欠落は `core-ui-pack/index.txt` とモデルの参照を最初に確認する。

## 検証と限界

- 最終検証: 関連Kotlinテスト99件、Pythonテスト5件が成功（失敗・エラー0）。
- Python: 動く輪郭、加速、独立した残光、格子、余白、厚みゼロ、面数上限を検査。
- Kotlin: 予備動作から発動への連続コマ、空振りとCONTACTの分離、全8方向の実頂点・リーチ・寿命・参照資源を検査。
- Vanilla 26.2の実CuboidModelパーサーで35モデルを受理。異常回転軸の負例は拒否。
- `.tools/greatsword-sweep-timeline.json` はKotlinから実際の演出ポーズを出力する。命中位置は比較用に1.5m前方へ置いた例であり、ゲームの命中を記録したものではない。
- GIFは配布モデルのUV・色・形をそのまま投影する。ブルームや架空の光を後加工しない。ゲームの録画・音声・GPU描画順・パケット遅延の検証ではない。
- 初稿で主面が細いことを確認し、幅と面の傾きを一度調整した。無制限に静的プレビューだけで微修正しない。
- 正面寄りのプレビューでは主面を確認できるが、斜め上から面を横切る角度では細く見える。全視点での見栄えを保証する段階ではない。
- 参照動画と同等の品質に達した、全技の演出が改善した、とは主張しない。今回は動きの作り方を変えた一技の試作で、参考の大きな紫の渦の再現ではない。
- プレイヤーの全身骨格アニメーションは追加していない。クライアントMOD・シェーダー・共通Particle Frameworkの変更なし。
- ゲーム起動・操作・稼働中パックの差し替えは行わない。`.tools/` はcommitしない。

プレビュー再生成:

```powershell
python scripts/preview_skill_choreography.py --ids dash_miss,dash_hit --timeline .tools/greatsword-sweep-timeline.json --prefix greatsword-sweep-v2
python scripts/preview_skill_choreography.py --ids dash_miss,dash_hit --timeline .tools/greatsword-sweep-timeline.json --prefix greatsword-sweep-eye-v2 --view eye
```
