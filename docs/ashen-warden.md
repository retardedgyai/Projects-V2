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
- `/move dash` / `/move spin_slash` / `/move spiral_combo` / `/move vault_slam`：戦闘をリセットし、指定した技を1回だけ実演。終了後は停止し、`/fight`で通常戦闘へ戻る。`slash_01`、`heavy_slash`、`rush_combo`（三連追撃）、`onslaught`（四連猛攻）も指定可能。
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
- 1179 rigid cuboid / 14148 triangle（内部面を含む）。薄い板のピクセル輪郭をvanilla cuboidへ分割し、27個のItemDisplayで描画。
- 32×32 PNGを7枚。Closest / nearest。限定3〜5色のsRGBパレット。制作プレビューはtexel色をEmissionで表示、smooth shading・subdivision・写実PBRなし。
- Minecraft 1.21.11以降のelement XYZ回転を使用。Blenderとpackは同じcuboidと回転値を使用。
- `weapon_root`、`weapon_tip`、`vfx_blade`、`vfx_chest`、`vfx_ground`。
- `.blend` に13個の名前付きAction。BlenderのAction Editorで切り替えて編集可能。

| Action | 長さ | ダメージ区間 | 内容 |
|---|---:|---:|---|
| idle | 60 tick loop | なし | 前傾・非対称構え・呼吸 |
| walk | 32 tick loop | なし | 2骨IK、接地中の足を固定、移動0.04 block/tick |
| slash_01 | 48 tick | 17–21 | 柄を引いて保持→踏み込み横薙ぎ→足を運び構え直す |
| heavy_slash | 62 tick | 28–32 | 頭上に担いで保持→叩き斬り→膝を曲げて受け止める |
| dash | 44 tick | 17–21 | 低く沈む→後ろ足で押す→大きく踏み込んで横斬り→足を寄せて回復 |
| spin_slash | 56 tick | 19–31 | 上体を巻き、腕を伸ばして全身で一回転する大薙ぎ |
| spiral_combo | 78 tick | 18–29 / 41–52 | 前進しながら二回転。間に切り返しがあり、各斬撃に独立した判定 |
| vault_slam | 60 tick | 34–37 | 低い溜め→連続した放物線移動・非対称に畳む前転→着地と布の遅れ |
| rush_combo | 74 tick | 14–18 / 29–32 / 43–47 | 踏み足を替える三連追撃 |
| onslaught | 96 tick | 14–18 / 29–32 / 43–47 / 60–64 | 三連追撃から頭上の重い振り下ろし |
| hurt | 16 tick | なし | 肩と頭の反動 |
| phase_transition | 64 tick | なし | HP50%以下、攻撃終了を待って封印崩壊 |
| death | 60 tick | なし | 崩れ落ち、剣を地面へ |

ボスHP360。第2形態は攻撃後の待機が5→2tick、与ダメージが1.15倍。近距離は三連撃、四連撃、大回転、連続回転を使い、遠距離は踏み込みと宙返りで詰める。
左右への追従を改善し、射程内で向きを合わせられなくても8tick以上攻撃開始を待ち続けない。連撃中は斬撃間に最大0.18rad/tickで向き直り、次の命中区間の4tick前から向きを固定する。斬撃中と単発実演中は追尾しない。
既存本編の報酬・進行へ接続する前の独立した戦闘スライス。

## 判定と表示の関係

### 外観・動作の再制作

青灰色の大きな面、段階の少ないピクセル陰影、粗い輪郭へ再制作した。部品番号ごとに変わるUVをやめ、骨の空間で隣の板と模様をつなぐ。肩の突起と輪郭の分割を減らした。
以前のPNGはsRGB値をさらに線形値へ変換して暗く保存していた。現在は指定したsRGBパレット値をそのまま保存する。packはshade:false、light_emission:15、ambientocclusion:false、表示の明るさ15/15。BlenderプレビューとMinecraftの描画全体が完全一致するという意味ではない。

剣の刃を1.35倍の長さ、1.4倍の幅に拡大。weapon_tipは3.4135 block。native element範囲に収めるため6 model pixels/blockで書き出し、ItemDisplayのscaleを8/3として同じ大きさへ戻す。
右手に対する剣の位置(0,-0.08,-0.06)と回転は固定。肩・肘・手首を2骨IKでつなぎ、上腕0.5074・前腕0.4838 blockの長さを保つ。
肩を引く→肘を畳む→踏み足と腰が先行→胸・上腕が追う→肘を伸ばして振り抜く→足を寄せて回復する。左腕は控えめに遅れ、頭と布にも小さな追従を付ける。

刀身のローカルYが刃幅、Xが平面の法線、Zが長さ。横方向の刃速度へY軸を合わせる。連撃の間は平行移動した回転基準を使って刃の向きをつなぎ、肘の伸展による手首の急反転を防ぐ。連撃後も開始時の握りまで連続して補間し、戻しで急反転しないことと最終姿勢の一致を検査する。剣だけを手から独立回転させない。
宙返りはrootの放物線を18〜37tickで連続して上昇・下降させる。最大高度約2.99 block、前進3.20 block。膝を左右非対称に畳み、下降中に開き、着地では胴体と布が遅れて落ち着く。頂点に静止区間はない。
三連撃は前進1.8 block、四連撃は2.4 block。切り返しでも構えに戻らず、次の予備動作へ移る。切り替えは6tickで関節のローカル姿勢を補間し、親子階層を評価して接続を保つ。

自動検査は左右旋回で攻撃が止まらないこと、3/4回の独立命中、空中軌道、パレット値、全攻撃の刃の向き、腕と握りの接続、胸・頭・腹の中心領域との交差、接地、表示座標と判定座標などを対象とする。中心領域の検査は鎧の突起と布を含む完全な衝突検査ではない。
実映像からの観察は `night-lord-rework-study.md` に記録。造形の一致度、重量感、難しさの最終評価はユーザーの実戦確認が必要。

Blenderで評価したboneの位置・quaternionを20Hzで書き出し、表示と判定で同一データを読む。
26.2のItemDisplayRendererがモデル空間に加えるY軸180度回転は、ItemDisplayのright_rotationで打ち消す。
この補正がなかった前版は各部品が骨原点を中心に裏返り、剣と判定・trailの方向も異なっていた。
ユーザーのF2画像7枚を確認して発見し、実際の26.2クライアントの描画処理を調べて修正した。
軌跡は上昇して長く残る炎から、小さな淡紫色のdustへ変更。開始タイトルは最初の攻撃前に消す。
剣元から0.44〜3.4135 blockの刃を半径0.33のcapsuleとして、前tickと現tickの間をslerpする。
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
造形は `night_lord_geometry.py`、32px模様とUVは `night_lord_palette.py`、動作とIKは `night_lord_motion.py`。旧 `night_lord_model.py` は過去版で現在の生成には使用しない。
`animate_warden.py` の歩行プレビューだけは、runtimeと同じ毎tick 0.04 blockの前進を加える。攻撃の前進はAction自体に入っている。
バイナリは `AWR2` magic、bone階層、各clipの複数ダメージ区間・位置・quaternion。新しいruntimeライブラリは追加していない。
`review_sword.py`で全8攻撃を固定カメラから518フレーム・25.9秒で実レンダーする。各技の全フレームのメッシュ頂点から画角を計算し、大剣と跳躍の軌道を収める。対応GPUがあればOptiX、なければCPUを使用する。引数が出力ディレクトリだけなら、剣側と側面から主要姿勢をレンダーする。プレビューにゲーム内trail・音は含まない。

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
