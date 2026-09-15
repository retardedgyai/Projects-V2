# 流星：冷却・破断の再構成（WIP）

Verdict: **FIX-FIRST**。Mage全10技、全参照に対する品質承認は未達。
直前の応答は説明だけなのでno progress。既存HEAD `9e9e002f` と1935件の旧tracked差分を再確認して継続した。

## 実見した違い

[R12 Wynncraft Mage](https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s) の
保存済み原寸キャプチャ155.843045 / 155.9908 / 156.121486を確認。
橙のアーチのあとに、薄い灰色の裂けた膜、曲がった黒い片、白灰色の片が上へ離れる。
従来の「橙のアーチ→小さい暗褐色の点」ではこの段階が欠けていた。
既存31資料の同じR12を細かく観察したもので、件数へ重複加算しない。
作者の内部モデル構造やalpha方式はこの観察だけでは断定しない。

## 変更

- 3状態を6状態へ。燃焼、裂けた炎、灰色の膜、裂けた帯、分離した片、最後の片。
- persistent Displayの同じ中心で形を交換し、Transformの補間を維持。
- 冷却開始後に不均等な外向き速度・上向き速度と落下加速度を加える。
  全体が同じアーチの位置で小さくなるだけの残り方を避ける。
- 新しい専用原画のRGBは改変せずnative UVへ使用。青背景に対応する面は作らない。
- 攻撃の命中・ダメージ・MP・CD・入力、Warrior、UI、Client、mainは変更しない。

## 新素材と限界

`assets/combat-vfx/mage-v5/sources/meteor-cooling-v01.png`
はbuilt-in imagegenで新規制作した2×2 atlas。自前の炎原画とR12観察シートを参照した。
生成元 `exec-f2ff68b0-aaf8-4a84-bc71-cc090c6774ac.png` をbyte同一で保存。
これはRGB画像。**半透明素材とは言わない。** 背景の青をgeometryから除くだけ。

主担当が原画と原寸参考を比較した時点では、薄い膜が参考より厚く不透明な課題がある。
このため原画単体を完成／参考水準と判定せず、短い位相として実モデル投影で再確認する。
既存のAssassin用shadow smokeは密な煙の塊で用途も違うため流用しない。

### 生成に使用したprompt（built-in）

> Use case: stylized-concept. Asset type: original production pixel-art VFX cooling/debris sprite atlas for our Minecraft RPG Meteor, NOT a scene or UI icon. Image 1 is a VISUAL REFERENCE: specifically the stage AFTER the orange explosion, where a thin pale-gray ruptured pressure membrane, curved charcoal fragments and a few white-gray chips remain. Do NOT copy the world, HUD, logo or screenshots. Image 2 is our own existing FIRE ART, a SHAPE/PIXEL-STYLE reference; the new art follows its broad asymmetrical arch and rough painted pixel-cluster curves, but depicts cooling, NOT orange fire. Create one square atlas with exactly 2 by 2 equal cells, four sequential cooling phases read left-to-right then top-to-bottom, a common ground anchor near the bottom of each cell, identical viewpoint and scale. First cell: already cooled broken arch, large empty lower center; a THIN ripped light slate-gray membrane linking a handful of hooked charcoal rims, most interior gone. Second: the membrane tears into unequal curled strips and a few light-gray flakes moving up/out; no longer a connected arch. Third: 8-12 separate charcoal curved slivers and larger pale gray chips, different directions and sizes, spread upwards and outwards, not arranged on a circle. Fourth: only 4-6 smaller remaining unequal drifting chips. No orange, no yellow, no magical blue glow, no thick smoke balls, no broad opaque cloud, no star-shaped plus-sign sparkles, no regular ring, no repeated circular puffs. Dark charcoal through pale neutral-gray ink, sharply cut irregular shapes with broad value groups, crisp pixel-art clusters designed on about a 96x96 logical grid per cell. No blur, no bloom, no grain, no dithering, no realistic fog, no smooth vector curves. Use a perfectly flat saturated dark blue (#000060) background throughout including every internal hole, NOT a checkerboard. Background is a geometry exclusion key and will never be displayed. Avoid blue in any of the gray artwork. Leave at least 8% empty padding around every cell. No cell borders, text, numbers, labels, scenery, perspective floor, logos or watermarks. This must look like the sparse cold ruptured material in reference 1, not a decorative grayscale fire symbol. Every cell stays fully inside its own equal quadrant.

## 検証

構造テスト成功を見た目の合格とは扱わない。

- Python対象9件成功（115.136秒）。stage済み対象だけを別の検証ディレクトリへ取り出した再現検査も9件成功（80.900秒）。
- Kotlinは11件のchoreography＋19件のmesh検査成功（最終run 1分7秒）。最初のrunは2つ目のfilterにslashを誤記したため、両filterを正して再実行した。
- 最終Kotlinの `.tools/mage-material-timeline.json` を未改変Vanilla Displayへ入力し、21ストリームの補間とscale zero後の削除を確認。
- `.tools/mage-native-cooling-01-60fps.json` から実RPモデルを投影。`meteor-cooling-01-motion.gif` は166 review ticks。
  主担当はeye51/54/57/60、side54/60、wide-side48/51/54/57を実見した。
- 冷却が橙の点ではなく灰色の破断として読め、後半に上へ片が離れるようになった。
  一方、灰色の帯はR12の透けた膜より硬い板のように見える。全10技へ展開できる品質とはまだ判定しない。
- MatE Solar Scepterの保存済み30.40794/30.81963も原寸で再確認。
  小さな命中の光片は見えるが、今回の灰膜の材質を決める根拠には足りないのでR12を主参照とする。
- `.tools/`、`.kotlin/`、1935件の旧tracked差分はstageから除外。旧frost変更も残したまま保存対象から除外。

### 独立レビューと02

読み取り専用レビュアーは新旧GIFの同時刻・途中フレームと側面を比較し、
膜→帯→片の段階と拡散は改善、単に同じアーチを縮めるだけではなくなったと判定。
ただし最大の未達を、(1)50→51で全体が同時に灰色の別形状へ交換されること、
(2)膜と固形片が両方とも硬い不透明な白灰片に見えること、とした。

02で左下→右側→上部→奥の上部へ0–1.5tickの冷却時差を設けた。
初期の連結・既存の表示補間・判定は変えない。5tick時点で冷えた部分と熱い部分が
併存するテストを追加。材質差はこの修正では解決せず、未達として残す。

02の対象30テスト成功（2分6秒）。最終の実表示データを再度Vanilla Displayに通し、
21ストリームの補間とscale zero後の消去を確認。
`.tools/mage-native-cooling-02-60fps.json` → `meteor-cooling-02-motion.gif`（166 review ticks）。
主担当はeye51/54/57を実見。51には橙と灰色が併存し、後段で灰色の破片になる。
原画・geometryは01のPython/隔離テスト時点と同一で、変更はKotlinの局所的な冷却時計だけ。

R12の155.88秒前後を追加確認するため本編を開き直したが、広告のスキップ後の取得は
ブラウザーツールのtimeout/kernel resetで終了した。この追加区間は実見扱いにせず、
新しい参照件数や冷却時差の作者実装を証明する証拠には使わない。
冷却時差はレビューで見えた同時交換を減らすProjectS側の実装判断であり、作者方式の断定ではない。

02の独立再レビュー: 51–53は炎と灰が共存し、全体同時の交換は弱まった。
ただし53→54で左右の大きな炎がまとめて灰へ替わるため、主要部分の画像交換感は残る。
**改善は部分的で、滑らかな冷却の達成ではない。** 次に必要なのは単なる時間差の再調整ではなく、
燃焼側から冷却側へ引き継ぐ輪郭の整合と、膜／固形片の材質の分離。
同じ小調整を繰り返して全技へ広げない。全10技と全参照の最終比較は引き続き未完了。

処理: 原画 → native geometry/UV → RPモデル → `CoreMageChoreography.pose` → Vanilla Display補間 → 投影。
形や青背景の混入は `scripts/build_mage_meteor_surface.py`、タイミングは `CoreMageChoreography.kt` を最初に確認。
ゲーム起動・操作は行わない。GPUの描画、通信遅延、音とfeelはこの検証には含まれない。
