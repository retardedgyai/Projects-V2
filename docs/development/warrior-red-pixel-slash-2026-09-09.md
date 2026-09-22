# 戦士：F2フィードバックを受けた幅広い斬撃・赤アクセント・音

## 受け入れた方向

Creatorは、最初に提示した「踏み込み斬りだけの白い幅広い斬撃」を基準にすることを確認した。
現行の短い白棒に見える演出、滑らかすぎる輪郭、黄色い戦士テーマは不採用。
承認済み大剣の本体・赤い刃のエフェクト、他職業、戦闘数値、命中判定、クライアントコードは変更しない。

参照：[SamusDev / Minecraft RPG Class Awakened | Dragon Warrior](https://www.youtube.com/watch?v=WR6NQB7NmgY)。
ブラウザー上で23・25・28・30秒付近の連続したコマ、50秒付近の刺突を直接確認。
30秒付近は幅のある裂けた紫の斬撃面と、その外へ離れた角張った破片が見える。
今回参照したのは面の幅・先細り・角張った色面・主役と破片の強弱。色はCreator指定の白／鋼＋赤とする。
動画の全編・音声・厳密なフレームレートを検証したとは扱わない。外部モデルや動画ファイルの取り込みなし。

## F2と音の調査

`client-fabric/run/screenshots/2026-09-09_07.57.32.png`を確認。
三人称の画面でプレイヤー手前に短い細い白い片が見える。静止画単体から技や前後の動きを断定しない。
前版は8区間を順に出し、各区間が最大幅になるのは約1tick。幅も通常0.36〜0.56blockで、
一片だけが強く見える構成になっていた。

通常攻撃の `GreatswordVfx.play(visual, origin, direction)` はメッシュだけを出し、
`CoreSkillAudio.play` を呼んでいなかった。スキルの `playSkill` は音を呼んでいた。
保存されたクライアント設定はmaster約7.6％、player 100％。設定の書換・ゲーム内の操作はしない。
これだけで全スキルの聞こえなさを説明できるとは断定しない。

## 変更

- 通常3段・攻撃スキルの面幅と出現の重なりを増やした。山場に6区間以上が共存する。
- 一つの完成PNGを回転させない。区間の座標・姿勢・モデルIDを固定し、出現と消滅の幅を2tick補間する既存方式を維持。
- 専用の粗いnativeモデル3種（中間・先端・後端）。32×8の色面区画、外側の白い縁、鋼の面、赤い内側の筋。
  元のグレースケール画像の白画素をインクとして使うが、旧flowの滑らかな輪郭は使用しない。bitmapの拡大やぼかしなし。
- 逆斬りでも白い縁が外側に向くように向きの基底を修正。
- 太くした面の四隅で地面とリーチを検査し、表示のみを範囲内に収める。判定を見た目に合わせて拡大しない。
- 命中破片を赤系へ。咆哮・旗から流れる帯の黄色も赤へ。旗本体の赤布と小さい金属装飾、既存HUDは維持。
- 通常攻撃にも音を接続。風切り＋投擲音、三段目の低い打撃音、確定命中の金属的な音を区別。
  実際のPULSE／CONTACTに同期し、見せかけの命中や独立した音タイマーを増やさない。
- 新しい音源は追加していない。Vanilla音の構成変更であり、独自収録音と呼ばない。

## 処理・重要ファイル

通常攻撃／Skill PULSE → `GreatswordVfx` → `CoreWarriorBladeChoreography.parts/pose` →
既存 `CoreCombatMeshes` → Vanilla ItemDisplay。音は同じイベントから `CoreSkillAudio`。

- 最重要：`server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreWarriorBladeChoreography.kt`
- 面と色：`scripts/build_warrior_blade_art.py`（通常の `build_core_combat_models.py` からも呼ぶ）
- 音の割当：`CoreSkillAudio.kt`、呼出漏れは `GreatswordVfx.kt`
- 資源：`core-ui-pack/assets/projects/{models,items}/combat_vfx/warrior_blade/` と赤い支援帯の定義

紫黒ならitem/model/textureとindexの対応、白棒なら区間の幅と同時表示時間、無音なら
上記呼出経路とプレイヤー音カテゴリを確認する。音量設定を無断で増やす対処はしない。

## 検証と残る懸念

- 最終 `:server-minestom:test`：805件、失敗・エラー・skip 0。音の通常攻撃経路は実際のSoundEffectPacket数を検証。
- nativeモデル：Vanilla 26.2の実CuboidModelでcombat_vfx全3,562モデルを受理。不正軸の負例は拒否。
- 最終の実装目標を使うnative Display検査：224ストリーム（通常配信と1更新欠落）、終了時の縮退と補間を検証。GPU/ネットワーク実測ではない。
- Python：専用色面・生成再現・index検査2件、既存expanded 3件、directional 2件が成功。
- 斬撃面の境界検査で一度検出した地面への埋まり・表示リーチ超過は、中心線でなく面の四隅を使って修正。
- 既存8方位検査・他職業のroute・多段の数・表示体数上限・モデルID固定・消滅ドレインの回帰テストを維持。
- `git diff --check`成功。新規配信資源50ファイル。ソースのRPのみ更新し、稼働中の配信RPは未更新。

確認画像 `.tools/warrior-red-final-eye-05.png` と `warrior-red-final-iso-05.png` は実装モデルの投影QA。
ゲーム画面の録画ではなく、照明・背景・遮蔽・GPU・ネットワーク・実機音量の検証を代替しない。
参照動画同等のアニメーション／全身動作／破片の豊かさに達したとは宣言しない。
特に旋風の輪郭のつなぎ目と、前景以外からの面の見え方は実機で判断が必要。
今回、稼働中のサーバー・配信パック・Minecraftは差し替えない。反映は次の再起動時。

mainは変更せず `play/gyai/class-armament-art` にcommit・通常push。
`.tools/`、`.kotlin/`、F2、認証情報、ローカル設定はcommit対象外。
