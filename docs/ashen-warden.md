# 夜葬の番人 — Minestom / Vanilla client vertical slice

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
- `/move dash` / `/move spin_slash` / `/move spiral_combo` / `/move vault_slam`：戦闘をリセットし、指定した技を1回だけ実演。終了後は停止し、`/fight`で通常戦闘へ戻る。`slash_01`と`heavy_slash`も指定可能。
- 終了：サーバーウィンドウでCtrl+C。

接続先とResource PackのHTTP配信はローカル限定。既存の25565/25566とは重複しない。
ポート変更は `projects.warden.port` / `projects.warden.packPort`。

## 制作物

MatE「The Lord of Night Boss」の実際の回転動画を確認し、外観を作り直した版。
縦スリットの兜、折れ曲がった黒い双角、刃状の肩鎧、胸の白い光、紫の裂け布、淡紫の大剣を参照。
ユーザー指定に合わせ、足で歩く二足の下半身を制作した。
形状・32px模様・動作データはBlenderで新規制作。参照モデルのデータ自体は取得・流用していない。
見た目は参照に基づく再制作であり、参照作品との同一性やユーザーの最終承認は未確認。

- Blender 4.5.3 LTSのArmature：31 bone、親子関係あり。
- 1695 rigid cuboid / 20340 triangle（内部面を含む）。薄い板のピクセル輪郭をvanilla cuboidへ分割し、27個のItemDisplayで描画。
- 32×32 PNGを7枚。Closest / nearest。diffuseと光部分のemissionのみ、smooth shading・subdivision・写実PBRなし。
- Minecraft 1.21.11以降のelement XYZ回転を使用。Blenderとpackは同じcuboidと回転値を使用。
- `weapon_root`、`weapon_tip`、`vfx_blade`、`vfx_chest`、`vfx_ground`。
- `.blend` に11個の名前付きAction。BlenderのAction Editorで切り替えて編集可能。

| Action | 長さ | ダメージ区間 | 内容 |
|---|---:|---:|---|
| idle | 60 tick loop | なし | 前傾・非対称構え・呼吸 |
| walk | 32 tick loop | なし | 2骨IK、接地中の足を固定、移動0.04 block/tick |
| slash_01 | 48 tick | 17–21 | 柄を引いて保持→踏み込み横薙ぎ→足を運び構え直す |
| heavy_slash | 62 tick | 28–32 | 頭上に担いで保持→叩き斬り→膝を曲げて受け止める |
| dash | 44 tick | 17–21 | 低く沈む→後ろ足で押す→大きく踏み込んで横斬り→足を寄せて回復 |
| spin_slash | 56 tick | 19–31 | 上体を巻き、腕を伸ばして全身で一回転する大薙ぎ |
| spiral_combo | 78 tick | 18–29 / 41–52 | 前進しながら二回転。間に切り返しがあり、各斬撃に独立した判定 |
| vault_slam | 72 tick | 41–46 | 低い溜め→膝を畳んだ前方宙返り→着地の叩き斬り |
| hurt | 16 tick | なし | 肩と頭の反動 |
| phase_transition | 64 tick | なし | HP50%以下、攻撃終了を待って封印崩壊 |
| death | 60 tick | なし | 崩れ落ち、剣を地面へ |

ボスHP360。第2形態は攻撃後の待機が8→4tick、与ダメージが1.15倍。近距離では大回転・連続回転を多く使い、遠距離では踏み込み・宙返りで距離を詰める。攻撃中の向きは固定し、回避できる予備動作と後隙を残す。
既存本編の報酬・進行へ接続する前の独立した戦闘スライス。

## 判定と表示の関係

### 全体リワーク

前版は開いた指の外で剣を独立回転させており、ユーザーから外観・全体の動作とも不一致と指摘された。
現在は右手を閉じ、手に対する剣の位置を(0, -0.08, -0.06)、回転を固定した。
肩・肘・手首を2骨IKでつなぎ、上腕0.5074・前腕0.4838 blockの長さを保つ。
手首のひねりは1tickあたり6度まで。既存8動作の組み直しに続き、踏み込み斬りを地上の低い斬撃へ変更し、大回転・連続回転・前方宙返りの3動作を追加。柄の引き・保持・刃の通過・立て直し、前足と後ろ足の運び、腰と胸の時間差を使用。
切り替え時は6tickで関節のローカル姿勢を補間してから親子関係を評価し、剣と手・腕の接続を保つ。
横斬り0.55、重斬り0.43、踏み込み1.70、大回転0.80、連続回転1.70、宙返り2.40 blockの水平移動をBlenderデータから読み、終了後のサーバー位置に残す。宙返りは骨盤を中心に回り、rootの跳躍高度は最大2.75 block。
全clipの握り、腕の接続、接地、6攻撃の軌道、連続回転の別々の命中区間、踏み込みの非浮遊、宙返りの反転・着地、単発実演、移動の連続性、切り替え、vanilla描画時の回転補正を含む20件のボス専用テストを実行。
実際に確認したDark Souls / Nightreign / Gael映像とGDC資料、反映内容は `night-lord-rework-study.md` に記録した。
見た目・重量感・新しい攻撃の戦闘感覚についてユーザーの最終評価は未確認。

Blenderで評価したboneの位置・quaternionを20Hzで書き出し、表示と判定で同一データを読む。
26.2のItemDisplayRendererがモデル空間に加えるY軸180度回転は、ItemDisplayのright_rotationで打ち消す。
この補正がなかった前版は各部品が骨原点を中心に裏返り、剣と判定・trailの方向も異なっていた。
ユーザーのF2画像7枚を確認して発見し、実際の26.2クライアントの描画処理を調べて修正した。
軌跡は上昇して長く残る炎から、小さな淡紫色のdustへ変更。開始タイトルは最初の攻撃前に消す。
剣元から0.44〜2.64 blockの刃を半径0.25のcapsuleとして、前tickと現tickの間をslerpする。
サンプル間隔は最大0.06 blockで、その半分を半径に加え、区間の抜けを防ぐ。
capsuleとplayer AABBの距離はbox面で区間分割して計算し、拡張AABBの角による誤判定を避ける。

ItemDisplayは1tick補間。前tickに送った表示区間が終わるタイミングで、その区間の当たり判定とtrailを処理する。
サーバー権威の位置・動作区間は共通だが、通信遅延やクライアント描画の完全な時間一致を保証する方式ではない。
単純な前方扇形によるボス攻撃ではない。同じダメージ区間では同じプレイヤーに一回だけ命中する。連続回転斬りは二つの区間を持ち、それぞれ一回ずつ命中しうる。切り返し中は判定もtrailも出さない。
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
造形は `night_lord_geometry.py`、動作とIKは `night_lord_motion.py`。旧 `night_lord_model.py` は過去版で現在の生成には使用しない。
`animate_warden.py` の歩行プレビューだけは、runtimeと同じ毎tick 0.04 blockの前進を加える。攻撃の前進はAction自体に入っている。
バイナリは `AWR2` magic、bone階層、各clipの複数ダメージ区間・位置・quaternion。新しいruntimeライブラリは追加していない。
`review_fierce.py`は今回変更した4攻撃を固定カメラ・20fpsで実レンダーする。プレビューは250フレーム・12.5秒で、ゲーム側のtrailは含まない。

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
- 参照作品: https://x.com/MatE312001/status/2091137786736738493
- Native XYZ element rotation: https://www.minecraft.net/pl-pl/article/minecraft-java-edition-1-21-11
