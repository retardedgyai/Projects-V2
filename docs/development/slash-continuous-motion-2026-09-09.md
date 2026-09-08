# 斬撃のガタつき：フレーム交換から補間可能な面へ

> 実機再テストで「パラパラ漫画のようにガタつく」と報告されたため、滑らかさは未達。
> このページの60Hz GIFは理想的な目標姿勢間の補間であり、実機の滑らかさの証拠には使わない。
> 続きは [実クライアント補間の検証と対策](slash-client-tick-jitter-2026-09-09.md)。

## 原因

Creatorの実機フィードバックを受けて確認。前回の確認は輪郭・色面・位置に偏り、時間方向の欠点を見落としていた。

- 旧方式は50msごとに別の形のitem modelへ交換していた。ItemDisplayの変形補間はモデルの頂点間を補間しない。
- 短い技では `3,4,6,7,9` とコマを飛ばす。旋回の角速度が大きい部分ほど変化が飛ぶ。
- 元の経路も区分線形だったため、キー境界で速度が変わる。ピクセル原画をぼかす問題ではない。

## リファレンス

[Crimson Moon 作者動画](https://www.bilibili.com/video/BV1FBBABBENx/)を再訪。今回は自動連続再生・シークの不安定さがあり、精密なFPS測定はできていない。以前保存していた同動画の12秒・12.2秒・12.4秒の連続コマも直接見直した。刃の動きに沿う細長い先端、連続した弧、短く消える残像を参照。第三者の素材自体は取り込まない。キャラクターのMODアニメーションをVanillaで再現したとは主張しない。

## 変更

- 戦士5技・アサシン2技に限定。`CoreFlowSlashChoreography.kt` を追加。
- 一振りの主面を4節、円形技は8節にする。完成した三日月全体を回すのではなく、各節の両端を刃先経路の別時刻へ置く。
- 各節のモデルIDは一生変えない。位置・角度・長さ・幅だけを更新し、既存の1tick ItemDisplay補間が実際に働く方式にする。
- 主面の先頭と末尾は専用の先細り形状。中間面は接合部を少し重ねる。残光も4節で同じ軌道を追い、薄くなって消える。
- 経路は単調三次補間。角度・半径をオーバーシュートさせず速度の折れを抑える。幅も連続した包絡線にする。
- PREPARE末尾とPULSE先頭の位置・姿勢・形状を一致させる。主面は8tick以内で終わり、多段の次判定を先行生成しない。
- 元の白画素＋独立3色tintを使い、ぼかし・高解像度化をしない。12個の小さなnativeモデルと12個のitem定義を追加。原画のビットマップ編集なし。
- 通常攻撃・未対象スキル・CONTACTの既存短い閃光・大剣本体・ダメージ・クールダウン・音は変更しない。

## 処理・ファイル

`CorePlayerCombat`のPREPARE/PULSE → 既存の技割当 → `CoreFlowSlashChoreography.parts/pose` → 既存`CoreCombatMeshes` → Vanilla ItemDisplay変形補間。

最重要ファイルは `server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreFlowSlashChoreography.kt`。原画形状は `scripts/build_flow_slashes.py`。既存の生成入口 `build_greatsword_sweep.py` に接続済み。

Particle Framework core、Class runtime共有基盤、通信プロトコル、クライアントコードは変更していない。具体的なVFX割当・粒子重複の抑止だけを変更した。

## 検証

最終結果：関連Kotlin 178件、Python 14件、失敗・エラー0。Vanilla 26.2実CuboidModelパーサーは新規12＋既存315＝327モデルを受理し、不正回転軸の負例を拒否。

- 同じモデルIDのまま小数tickでも位置が変化すること、位置・幅・姿勢の急変上限、終了前の縮退。
- 実頂点を8方位で確認し、地面・前方・リーチを守る。正面への投影率、PREPARE接続、資源参照も継続検証。
- 使用中のMinestom 2026.08.16-26.2実装では、補間開始値0の再設定は同値でも通知が省略されないことをbytecodeと回帰テストで確認。架空の補間設定には依存しない。
- 主面は最大8体で既存の他プレイヤー表示枠に収まる。多段を含むこの7技のtimelineは最大16体。owner 48／scene 384の既存制限を変更しない。
- 面片数はtip/tail各96、middle40、wake11。通常一振り316面片、円形476面片、交差632面片。描画entity数は旧版より増えるため、多人数FPSの保証ではない。
- `CoreFlowSlashChoreographyTest` のプレビュー出力は、20Hzで送る位置・scaleの線形補間とquaternionの最短球面補間を60Hzでサンプリングしたもの。サーバーが60Hzでモデル交換するという意味ではない。GIFの10ms単位丸めで再生が早まらないよう時間も分配。
- 確認用：`.tools/smooth-slash-final.gif`、`.tools/smooth-area-final-*.png`。描画用スクリプトは `preview_skill_choreography.py --fps 60`。

## 残る確認・再開点

実機GPU描画・遅延・低FPS・複数人時の体感は未確認。今回の作業中、稼働中サーバー／リソースパックを更新せず、Minecraftの操作・再起動もしない。実機反映には次の起動時にinstallDistと既存の大剣確認パックを再生成する。

再発時はまず `CoreFlowSlashChoreography.pose` の節位置とscale、次に `CoreCombatMeshes` の1tick変形補間と更新間隔を見る。紫黒なら新しいflowモデルが配信パックのindexに入っているか確認する。

mainは変更せず、`play/gyai/class-armament-art` にcheckpointをcommit・通常pushする。`.tools/`・`.kotlin/` はcommit対象外。
