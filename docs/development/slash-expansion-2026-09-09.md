# 斬撃の展開：戦士5技・アサシン2技

## 範囲と受け入れ条件

Creatorの「ある程度のところまで広げる」に対し、既存の踏み込み斬りに6技を追加。
全職業・全攻撃の一括変更ではない。通常攻撃、天断、無明連刃、刺突、毒、支援技、Mage Zeroは今回変更しない。
主参照は前試作と同じ SamusDev Dragon Warrior（https://www.youtube.com/watch?v=WR6NQB7NmgY）。
新たな外部アセットの取得はなく、固定PNGの回転・切り抜きには戻さない。

| 技 | 構成 | 1回のサーバー判定との関係 |
|---|---|---|
| 踏み込み斬り | 既存の湾曲した横薙ぎ | 前checkpointを維持 |
| 裂傷斬り | 細く短い斜め斬り、短い残光 | 主刃1＋残光1 |
| 返し刃 | 逆方向へ加速する厚い金色の返し | 主刃1＋残光1。単なるUV反転ではない |
| 地砕き | 頭上から前方へ振り下ろす湾曲面 | 主刃1＋残光1。ダメージや地形破壊は追加しない |
| 旋風斬り | 刃先が一周する面を毎コマ構成、次撃は始点を変える | 判定ごとに主刃1＋残光1。標準3判定は8tick間隔 |
| 断命 | 異なる二つの刃先経路が同時に交差 | 主刃2＋残光2で一判定。二度目の命中演出にはしない |
| 刃の輪 | 隙間のある短剣状の弧。二撃目は逆方向 | 判定ごとに主刃1＋残光1。標準2判定は8tick間隔 |

## 描画と割当

- `CorePlayerCombat` の既存PREPARE/PULSE/CONTACT → `CoreSkillChoreography` → `CoreExpandedSlashChoreography` → 既存ItemDisplay配信。
- `CoreExpandedSlashChoreography.kt` が今回の最重要ファイル。対象6技、各層、方向、倍率、寿命を明示する。
- PREPAREは実際の予備動作時間にモデル0〜2を使い、PULSEは3から始める。PULSE自体に将来の攻撃を予約しない。主刃は5〜7tickで終わり、次の8tickの判定をまたがない。残光は9〜13tick。
- 各PULSEの形は実際のpulse番号で選ぶ。中断した未発生PULSEが演出だけで出ることはない。
- CONTACTのみ、サーバーが受理した対象位置に独立した閃光を出す。空振りのPULSEには命中光を付けない。
- `build_expanded_slashes.py` に8つの具体的な刃先経路を記述。角度、半径、楕円率、幅、欠け方、残光を時間から生成する。280モデル＋280アイテム定義を追加。
- 既存の `build_greatsword_sweep.py` から通常ビルドにも接続。カラーは既存グレースケールの不透明3画素のみ参照し、元の三日月PNGの輪郭は使わない。ラスタ原画の生成・編集ではなくnative面の生成。
- `CoreSceneParticles` はモデル原点に無関係な点群を出さない。既存の命中粒子と範囲マーカーは残す。
- 新しい共通Framework、クライアントコード、ダメージ係数・判定範囲・クールダウン・音の変更なし。

## 検証・制約

- 最終結果：関連Kotlin 174件、Python 9件成功（失敗・エラー0）。変更後の地砕きの正面プレビューも確認した。
- 追加モデルは最大199面片／モデル、JSON本体の非圧縮合計約1.68MB。1PULSEあたり表示2体（断命4体）、CONTACTは別に1体。連撃では前撃の残光だけが短く重なり、CONTACTなしの今回の全体timelineで最大6体。実際の描画負荷はゲーム内の多人数検証を行っていない。
- 追加Pythonテスト：全経路の加速・移動・消滅、逆方向、一周の角度、キャンバス内、面数予算。既存の湾曲面・斬撃テストも実行。
- 追加Kotlinテスト：主刃と残光の個数、固定姿勢、全コマ資源参照、確定命中のみの光、PREPARE接続、全8方向の実回転後8頂点の地面・前方・リーチ、連撃timeline。
- 地砕きの縦面が正面視点で線に見えたため、一定の斜角0.4radを付けて修正。実面積で重み付けした正面への投影率をテストし、単なる最大面一枚の確認で済ませない。
- 既存の演出・メッシュ・アトラス・スキル粒子・プレイヤー戦闘テストも実行。旧「四枚の固定セクター」「反転UV」という実装依存のassertは、新しい主刃1＋残光1・専用経路のassertに更新。
- Vanilla 26.2実CuboidModelパーサーは追加280＋既存35＝315モデルを受理。異常回転軸の負例を拒否。
- プレビューはサーバーから出力したPREPAREとPULSEのtimeline。命中は仮定せず、CONTACTなし。3連撃をGIF編集で増やすのではなく、既存コードと同じ8tick間隔で出力する。
- 簡易投影であり、GPU、実際の移動に伴う原点変化、通信遅延、音、操作感の確認ではない。ゲームの起動・操作や稼働中パックの差し替えはしない。
- 参照作品と同等の品質・全視点の見やすさ・全スキルの完成は主張しない。操作感の判定はCreatorのManual Smokeで行う。

## 壊れた時に最初に見る場所

形状：`scripts/build_expanded_slashes.py`。方向・タイミング：`CoreExpandedSlashChoreography.kt`。
紫黒表示：`core-ui-pack/index.txt`、`items/combat_vfx/sweeps/`、`models/combat_vfx/sweeps/` の参照。
多段数：`CorePlayerCombat.kt` の8tick PULSEと上記割当（今回は戦闘コードを変更していない）。

プレビュー再生成（関連Kotlinテストの実行後）:

```powershell
python scripts/preview_skill_choreography.py --ids war_wound,war_counter,slam,whirl,ass_execute,ass_fan --timeline .tools/expanded-slash-timelines.json --prefix expanded-slashes-iso --world-scale 48
python scripts/preview_skill_choreography.py --ids war_wound,war_counter,slam,ass_execute --timeline .tools/expanded-slash-timelines.json --prefix expanded-slashes-eye --view eye
```

`.tools/` はcommit対象外。mainは変更せず、`play/gyai/class-armament-art` にcheckpointを公開する。
