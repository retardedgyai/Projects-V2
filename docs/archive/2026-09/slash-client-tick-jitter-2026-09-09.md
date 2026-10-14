# 斬撃のパラパラ感：実クライアント補間の検証と対策

## 判定

FIX-FIRST（実機feelの再確認待ち）。以前のGIFを根拠に滑らかさを保証した判断は撤回。
作者のフィードバックは「ゲーム内ではGIFと違い、パラパラ漫画のようにガタガタ」。
今回は再現可能な補間切れへの対策であり、そのプレイ時の根本原因を特定したという意味ではない。

## 調べたこと

- 使用中のVanilla 26.2 `Display.tick`, `calculateInterpolationProgress`, `SynchedEntityData.assignValues` の実bytecodeを確認。
- metadata受信でフラグを立て、client tickで新しい補間区間を作る。始点は前の描画で使った `lastProgress` に依存する。単純に前後の目標姿勢を線形・球面補間する自作GIFとは違う。
- 同じ補間開始値0の再通知はMinestom側もVanilla受信側も処理する。「同値だから無視される」は今回の原因ではない。
- 従来は1tickで目標へ到達するため、1tick更新が抜けると区間を使い切って静止する。実Vanillaクラスへ受信値を渡すヘッドレス検証で再現した。
- 60FPS相当の3描画サンプル、一定速度の目標値、4tick目の配送を省く条件で、従来は `3.000 / 3.000 / 3.000`。2tick補間では `1.944 / 2.296 / 2.648` と動きが続く。数値の遅れも実際にあり、無料で滑らかになるわけではない。
- 背景調査では [Mojangの既存補間ずれ報告 MC-261600](https://bugs-legacy.mojang.com/browse/MC-261600) も参照。ただし古い報告を26.2の根拠にはせず、上記のローカル実クラスで確認した。

## 変更範囲

戦士 `dash / war_wound / war_counter / slam / whirl`、アサシン `ass_execute / ass_fan` の **flow主斬撃・残光だけ**。

1. 変形の補間時間を1→2tickにし、1tick届かない間も動きを継続する。
2. 最後の幅0の姿勢を送った後、2tickは変形を再送せず消え終わるのを待って削除する。他プレイヤーの可視性もこの終了待ちを含める。
3. PREPAREは既存の1tick・既存の寿命を維持。非ゼロの予備動作末尾を延長するとPULSEと二重に残るため、この変更では延ばさない。
4. 元の自作60Hz出力を `flow-slash-ideal-authoring-60fps.json` へ改名し、authoring用と明記。旧 `.tools/smooth-slash-final.gif` は実機評価用として使用しない。
5. `-Dprojects.vfx.traceTiming=true` で主斬撃の先頭節だけ `CORE_VFX_TIMING` を出す。更新数・最大サーバー更新間隔・補間設定を記録する。デフォルト無効。クライアントFPSやネットワーク到着時刻を測る機能ではない。

原画・テクスチャ・モデル・大剣本体・通常攻撃・他スキル・ヒット判定・音・CDは変更しない。
クライアントのコード、共有protocol、Particle Framework core、Class runtime共通基盤も変更しない。
変更先のCoreCombatMeshesは既存の具体的なscene消費側。mainへは反映しない。

## 処理と重要ファイル

既存の技イベント → `CoreFlowSlashChoreography.pose` → `CoreCombatMeshes` が毎tick目標を送信 → Vanillaが2tick補間 → 終端の幅0へ補間完了後に削除。

- 最重要: `server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreCombatMeshes.kt`。補間時間・終了待ち・任意の更新間隔ログ。
- `CoreFlowSlashChoreography.kt`: 実装の説明を更新。経路自体は変更なし。
- `CoreCombatMeshTest.kt`: 実Minestom displayの補間値、幅0送信後も生存すること、PREPAREの寿命不変、最終削除を検証。
- `CoreFlowSlashChoreographyTest.kt`: 隠しspawn期間から終了待ちまでのサーバー目標値を `.tools/flow-display-contract.json` に出力。理想GIFとは別。
- `scripts/CheckNativeDisplayInterpolation.java`: 使用中のVanillaクラスを呼ぶ検証。Minecraft起動・ネットワーク接続・ゲーム入力・クライアント改変は行わない。

ヘッドレス検証では未初期化ClientLevelをテスト用に用意し、Display.tickが参照するisClientSideだけを設定する。Display本体は通常のコンストラクタとmetadata受信処理・補間を使う。ワールド/GPU/通信は再現していない。

## 検証

- 関連Kotlin: `*ChoreographyTest`, `*CoreCombatMeshTest`, `*CorePlayerCombatTest`, `*CoreSkillEffectTest`（179件）。
- 実Vanilla: 従来の1tick補間を負例として静止を検出、2tickなら欠落中も動くこと、終了待ちでscale0まで到達することを検証。
- 実Vanillaへ7技・72節のサーバー目標データを渡し、通常配送／1tick欠落の144ストリームで有限の変形と終端の縮退を検証。144件すべてが見た目の品質検査という意味ではない。
- 形状を変えていないため資源生成は不要。前回のGIFは今回の合格根拠に含めない。

再実行は既存Java25/Gradleパッチ環境で上記Kotlinテストを実行後、Java25で次を実行（cwdは `.tools`。ローカルログをrepo直下へ作らないため）。

```text
java -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8 -cp "<Vanilla 26.2 minecraft-client.jar>;<既存Vanillaライブラリディレクトリ>/*" ../scripts/CheckNativeDisplayInterpolation.java flow-display-contract.json
```

## 残る懸念・次の確認

- 実機プレイ時の更新欠落・クライアントFPS・描画負荷は未計測。今回の症状をこれだけで完全解決したとはしない。
- 補間時間を伸ばす分、表示が目標姿勢を追う遅れが増える。判定やCDは遅らせていない。速い曲線や細い接合部の見た目はCreatorの実機確認が必要。
- 2tick以上の停滞、低FPS、モデル交換型の別スキルにはこの対策だけでは不十分。終了待ちによる一時的なdisplay重複もあり、既存owner48/scene384/observer8制限は維持。
- 再発時はまず対象技が上記7技か、`CORE_VFX_TIMING` の最大間隔が50msを大きく超えていないかを確認。その後に実録画とクライアントFPSを照合する。再度自作GIFだけで完了にしない。
- 今回は稼働中ゲームを操作・再起動しない。次の手動テスト準備時にinstallDist→停止済みサーバー用確認パック再構築→再起動が必要。

checkpointは `play/gyai/class-armament-art` へcommit・通常push。`.tools/`、`.kotlin/`、ローカルログはcommit対象外。
