# 灰燼の番人 — Minestom / Vanilla client vertical slice

対象は **ProjectS v2 / Minestom 2026.08.16-26.2 / Minecraft Java 26.2 / Java 25**。
`-Dprojects.warden=true` で専用アリーナを起動する。本編の保存データ、経済、ネットワーク契約は使用しない。
通常起動の港・本編モードは変更していない。クライアントMODもBetterModelも不要。

## 起動と操作

配布物の `START-WARDEN.cmd` を開き、Minecraft Java **26.2** のマルチプレイから
`127.0.0.1:25575` へ接続。Resource Packを許可し、読み込み完了後に `/fight`。

- 左クリック：予備動作5tickの剣攻撃。再使用14tick、通常16ダメージ、ボスの後隙中は22。
- Shiftを押す：向いている方向に回避。無敵7tick、再使用28tick。
- `/mend`：体力35回復、再使用20秒。
- `/fight`：初回開始、全滅後・討伐後の再戦。交戦中のリセットは受け付けない。
- 終了：サーバーウィンドウでCtrl+C。

接続先とResource PackのHTTP配信はローカル限定。既存の25565/25566とは重複しない。
ポート変更は `projects.warden.port` / `projects.warden.packPort`。

## 制作物

独自デザインの煤色の鎧、片側の骨装甲、青緑の亀裂、巨大な断頭剣。
MatE作品やArtoriasのモデル・テクスチャ・動作データは流用していない。
参考X投稿は取得制限により直接確認できず、ユーザーのlow-poly/pixel-art指定を制作基準にした。

- Blender 4.5.3 LTSのArmature：31 bone、親子関係あり。
- 71 rigid cuboid / 852 triangle。26個のItemDisplayで描画。
- 32×32 PNGを7枚。Closest / nearest。diffuseのみ、smooth shading・subdivision・PBRなし。
- `weapon_root`、`weapon_tip`、`vfx_blade`、`vfx_chest`、`vfx_ground`。
- `.blend` に8個の名前付きAction。BlenderのAction Editorで切り替えて編集可能。

| Action | 長さ | ダメージ区間 | 内容 |
|---|---:|---:|---|
| idle | 60 tick loop | なし | 前傾・非対称構え・呼吸 |
| walk | 32 tick loop | なし | 2骨IK、接地中の足を固定、移動0.04 block/tick |
| slash_01 | 34 tick | 13–18 | 腰・胸を捻る横薙ぎ、長い後隙 |
| heavy_slash | 48 tick | 23–27 | 頭上に担ぐ→高速叩き斬り |
| dash | 40 tick | 18–22 | 溜め→接近→斬撃、方向固定 |
| hurt | 12 tick | なし | 肩と頭の反動 |
| phase_transition | 64 tick | なし | HP50%以下、攻撃終了を待って封印崩壊 |
| death | 60 tick | なし | 崩れ落ち、剣を地面へ |

ボスHP360。第2形態は攻撃後の待機が18→10tick、与ダメージが1.15倍。
既存本編の報酬・進行へ接続する前の独立した戦闘スライス。

## 判定と表示の関係

Blenderで評価したboneの位置・quaternionを20Hzで書き出し、表示と判定で同一データを読む。
剣元から0.44〜2.64 blockの刃を半径0.25のcapsuleとして、前tickと現tickの間をslerpする。
サンプル間隔は最大0.06 blockで、その半分を半径に加え、区間の抜けを防ぐ。
capsuleとplayer AABBの距離はbox面で区間分割して計算し、拡張AABBの角による誤判定を避ける。

ItemDisplayは1tick補間。前tickに送った表示区間が終わるタイミングで、その区間の当たり判定とtrailを処理する。
サーバー権威の位置・動作区間は共通だが、通信遅延やクライアント描画の完全な時間一致を保証する方式ではない。
単純な前方扇形によるボス攻撃ではない。攻撃一回につき同じプレイヤーに一回だけ命中する。
プレイヤーから届いた対象entity IDを命中判定に使わず、サーバー側の距離・視線方向・cooldownで判定する。

## 再ビルド

```powershell
$env:JAVA_HOME = '<Java 25 JDK>'
.\gradlew.bat :server-minestom:check :server-minestom:build :server-minestom:installDist
$env:JAVA_OPTS = '-Dprojects.warden=true'
.\server-minestom\build\install\server-minestom\bin\server-minestom.bat
```

制作元の再生成：

```text
blender -b --python tools/ashen-warden/create_warden.py -- <asset-output-directory>
python tools/ashen-warden/verify_assets.py <asset-output-directory>
```

`create_warden.py` はBlenderのmesh/Armature/Actionを作成・評価した後に、Resource Packとbinaryを出力する。
バイナリは `AW R1` magic、bone階層、各clipの位置とquaternion。新しいruntimeライブラリは追加していない。

## 実装の入口

- `WardenArena.kt`：専用アリーナ、vanilla入力、表示、Resource Pack、プレイヤーHP、VFX。
- `WardenFight.kt`：ボスAI、HP、phase、attack timeline、重複ヒット管理。
- `WardenAsset.kt`：骨データ、quaternion補間、swept bladeとboxの判定。
- `WardenTest.kt`：軌道、足接地、phase、death、リセット、packの検証。

表示がおかしい時はpackロード状態とasset-validation.json、攻撃がおかしい時はWardenFightとWardenTestを確認。
headless確認は `tools/ashen-warden/headless_smoke.py`。ログの `WARDEN_READY` と `WARDEN_SERVER_SMOKE_PASS` が正常終了の目印。

## 残る手動スモークは1件

Minecraft 26.2のvanillaクライアントでResource Packを適用し、`/fight` から第2形態・討伐・再戦まで一戦通す。
その一戦でピクセル表現、接地、予備動作、剣とtrail・被弾の見た目、回避、後隙の手触りを判定する。
これはrepoのAGENTS.mdが人間Creatorに割り当てるゲーム操作・最終feel確認。

## 互換性・権利

実行時のモデルエンジン依存はなし。Minestomは既存のApache-2.0依存。
Blenderは制作ツールとしてのみ使用し、Blender本体を配布物に含めない。
モデル・ピクセル模様・アニメーションはこのタスクで新規制作。

- Minestom公式: https://github.com/Minestom/Minestom
- Minestom ItemDisplayMeta: https://javadoc.minestom.net/net.minestom.server/net/minestom/server/entity/metadata/display/ItemDisplayMeta.html
- Minecraft 26.2 resource pack 88.0: https://feedback.minecraft.net/hc/en-us/articles/46690753273997-Minecraft-Java-Edition-26-2
