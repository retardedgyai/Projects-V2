# 流星：同一輪郭を引き継ぐ冷却（WIP）

Verdict: **FIX-FIRST**。Meteor単体も、Mage全10技も参考水準に達したとは判定しない。
前回 `f3d1a08d` は冷却時計と運動の実装・検証・pushがありprogress。
今回も追加実見とnative geometry/materialの変更がありprogressだが、完成ではない。

## 追加実見と判断の変更

[R12 Wynncraft Mage](https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s) を
非表示ブラウザで開き、停止・消音してコマ送りした。
155.813935 / 155.863933 / 155.913931 / 155.963929 / 156.013927秒を確認。
橙の炎のカールが同じ位置関係を保って赤黒い縁になり、その内部が抜け、最後に薄く消える。
単に「灰色の別形状を次に出す」構成ではこのつながりを再現できない。
既存31資料の再観察であり、資料件数には加算しない。作者の内部実装は断定しない。

## 変更

- `build_mage_meteor_surface.py`: mainの6状態が共通の炎原画・座標・pivotを使う。
  別cold atlasへの交換をやめ、原画の内部を抜き、裂けた縁と独立片をnative面として残す。
- 発光した原画、赤い熾火、暗い固形片、半透明の縁を別材質にした。
- 半透明の縁は既存 `warrior_support/cloth.png` の1画素 (24,61)、
  RGBA=(37,37,41,68) を一定UVで参照する。旗の絵・形・動きを流用したものではない。
  この既存PNGおよびWarriorの実装には変更なし。bitmapの編集も行っていない。
- flow itemだけ9番目の熾火tintを追加。固定Meteor/Ring/Eruptionの8色は変更なし。
- previewに明るい背景を選べる `--background` を追加。デフォルトは従来どおり。
  明背景の01で広い灰色の板を発見したため、02では半透明面も縁に限定して内部を除いた。
- flowモデルの構造検査上限を500から1000 elementsへ変更した。
  材質境界の分割が増え、現在の最大は733。Display数・observer上限8・共有rendererは変更なし。
  GPU負荷が無条件に安全という主張ではない。
- gameplay判定・damage・MP・CD・入力・Kotlin・Client・UI・mainは今回変更していない。

## 採用しなかった生成結果

built-in imagegenで旧cooling atlasの透明度修正を試したが、出力はRGB 1254×1254で
checkerboardが焼き付いていた。半透明PNGとして採用せず、repoにも取り込んでいない。
生成ファイル: `exec-56fd0f4d-20dd-4d3c-9276-2c1987f44528.png`。

使用prompt:

> Use case: precise-object-edit. Image 1 is our EDIT TARGET: a 2x2 sequential pixel-art Meteor cooling atlas. Image 2 is the VISUAL REFERENCE, specifically 155.99s and 156.12s: the thin gray pressure membrane is translucent, while independent charcoal slivers and pale chips remain crisp. Change ONLY material and transparency in Image 1. Preserve the same four equal cells, positions, anchors, silhouettes, viewing angle and spacing of every main curl and chip. Remove the blue background to GENUINE transparent alpha, not a painted checkerboard or white backdrop. In the first two cells, turn the broad chalk-white connecting ribbons into very thin pale slate-gray vapor membranes at about 20-35 percent opacity; no thick white beveled edges, no bone or rock texture. Keep torn pixel-cluster boundaries. Leave the separate dark charcoal fragments and scattered small pale chips opaque with simpler broad value groups, not noisy beveled stone. Last two cells stay sparse individual fragments in their original locations. No extra particles, no orange, no added glow, no blur or realistic cloudy smoke. Crisp authored pixel shapes and broad flat value groups; keep the pixel-art construction. Deliver an actual RGBA PNG with transparent empty areas and partially transparent vapor; do NOT flatten onto a background. No text, grid, labels or watermarks. This is a production texture edit, not a screenshot/mockup.

## 検証と限界

- Python対象10件成功（30.811秒）。stage済みファイルとHEADの依存だけを隔離して取り出した
  再現テストも10件成功（31.783秒）。旧dirtyなgarden/fireを必要変更として紛れ込ませていない。
- Kotlin対象30件成功（choreography 11 + mesh 19、1分）。
- `CheckMageMaterialAlpha.java` が実RPの6104 face UVを読み、未改変Vanilla 26.2の
  NativeImageとSpriteContentsの分類で部分透明が残ることを確認。
  GPU描画、blend順序、ゲーム内照明、通信、音、手触りを検証したものではない。
- `.tools/mage-native-cooling-02-60fps.json` は前回のVanilla Display補間出力。
  今回Kotlin poseは変更していないためそのまま使い、今回の実RPモデルを投影した。
- `.tools/meteor-registered-02-motion.gif` とlight 51/54/57/60を読取専用レビュー。
  主担当は明暗の正面およびside 54/60を実見。側面には依然として面状に読める箇所があり、
  正面の改善だけで全方位の立体感を合格とはしない。
- 独立レビュー: 炎のカールと裂け目を冷却後まで追え、別の灰アーチへの画像交換感は明確に改善。
  明背景で大きな灰色板の残留は見られない。ただし50→51の赤化、53→54の暗い縁への変化が
  広範囲でまとまって起き、**材質変化の段差は残る**。

次の修正対象は同じ原画内で熱い部分・赤い部分・抜けた部分が共存する冷却の進み方。
今回の輪郭を引き継ぐ構造を保存し、また別原画への交換に戻さない。
技術テストは見た目の承認ではない。全10技と全参照の最終比較は未完了。

処理: 原画 → native被覆・材質 → RP → 既存Displayの運動・補間 → 実モデル投影。
形・材質の問題は `build_mage_meteor_surface.py`、partial alphaの退行は
`CheckMageMaterialAlpha.java` を最初に確認する。ゲームは起動・操作していない。
旧1935 tracked差分、旧untracked、`.tools/` と `.kotlin/` はcommit対象外。
