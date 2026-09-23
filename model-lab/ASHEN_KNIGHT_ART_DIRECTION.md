# 灰淵の騎士: 造形レビュー

このボスはオリジナルキャラクターとして制作する。アルトリウスのデザインをそのまま複製せず、下記の造形原則を読み取り、ProjectSの灰と深淵を主題に再構成する。

## 参照資料

フロム・ソフトウェアのデザインチームが監修したGeccoの立体資料を形状の一次資料として採用する。写真は転載せず、リンクを残す。

| 視点・用途 | 資料 |
| --- | --- |
| 正面・脚の開き・剣の長さ | [Gecco 01](https://gecco.co.jp/wp-content/uploads/artorias-1-2-1024x1024.jpg) |
| 正面斜め・布と肩の重なり | [Gecco 02](https://gecco.co.jp/wp-content/uploads/artorias-2-2-1024x1024.jpg) |
| 横・前屈と後方の布 | [Gecco 03](https://gecco.co.jp/wp-content/uploads/artorias-3-1-1024x1024.jpg) |
| 背面斜め・裾の段差 | [Gecco 04](https://gecco.co.jp/wp-content/uploads/artorias-4-1-1024x1024.jpg) |
| 背面・腰の鎖帷子と脚の間 | [Gecco 05](https://gecco.co.jp/wp-content/uploads/artorias-5-1-1024x1024.jpg) |
| 背面斜め・剣と外套の重なり | [Gecco 06](https://gecco.co.jp/wp-content/uploads/artorias-6-1-1024x1024.jpg) |
| 右側・武器を持つ腕と背中 | [Gecco 07](https://gecco.co.jp/wp-content/uploads/artorias-7-1-1024x1024.jpg) |
| 左側・傷ついた腕 | [Gecco 08](https://gecco.co.jp/wp-content/uploads/artorias-8-1-1024x1024.jpg) |
| 左前・腕の垂れ方 | [Gecco 09](https://gecco.co.jp/wp-content/uploads/artorias-9-1-1024x1024.jpg) |
| 仮面・首布・肩当て接写 | [Gecco 10](https://gecco.co.jp/wp-content/uploads/artorias-10-1024x1024.jpg) |
| 仮面・片肩・剣の柄接写 | [Gecco 11](https://gecco.co.jp/wp-content/uploads/artorias-11-1024x1024.jpg) |
| 負傷した腕と鎖帷子接写 | [Gecco 12](https://gecco.co.jp/wp-content/uploads/artorias-12-1024x1024.jpg) |
| 布・剣・甲冑の傷接写 | [Gecco 13](https://gecco.co.jp/wp-content/uploads/artorias-13-1024x1024.jpg) |
| 背面の布の層と刀身接写 | [Gecco 14](https://gecco.co.jp/wp-content/uploads/artorias-14-1024x1024.jpg) |
| 横から見た髪束と首布 | [Gecco 15](https://gecco.co.jp/wp-content/uploads/artorias-15-1024x1024.jpg) |
| 設定と造形意図 | [Gecco商品ページ](https://gecco.co.jp/en/product/artorias-the-abysswalker/)、[造形の構造](https://gecco.co.jp/en/uncategorized-ja/dark-souls-artorias-statue-structure/) |
| 兜・肩・首布の別造形 | [First 4 Figures公式の胸像](https://first4figures.com/collections/dark-souls-busts-1)、[公式展示写真](https://www.first4figures.com/blog/first-4-figures-mimic-statue-and-artorias-the-abysswalker-bust-%40-tokyo-comic-con-2019.html) |
| 元ゲームの動き | [Bandai Namco Europe公式トレーラー](https://www.youtube.com/watch?v=QiBjgvFYmb0) |
| 比較するMinecraftボスの演出水準 | [ModelFoundry Demon Reaper](https://mcmodels.net/products/8863/demon-reaper)、[実演動画](https://www.youtube.com/watch?v=ePJyUkTysB0) |

## 写真から読み取った造形原則

1. **動作が止まっていても動きを感じる輪郭**: 頭と胸を低く前に出し、脚を広く開く。剣は体を超えて伸びる。左右対称の立ち姿にしない。
2. **顔の固有性**: 顔を暗い穴にし、鼻先が細く長い獣面の兜と、後方に流れる黒い飾りを主役にする。光る双眼は参照像の要素ではない。
3. **青い布が第一の色面**: 首から胸へ多層に巻き、背中から腰下へ破れた布が続く。装甲面積は布を隠さない。
4. **傷を負った片腕**: 非武装腕は肩から下げ、肘から先を脱力させる。逆側の腕のみ大剣を支える。
5. **装甲は薄い層の集合**: 肩・胸・腰・腿・膝に大小の異なる板が重なる。隙間には暗い鎖帷子を見せる。全身を明るい平滑な箱で覆わない。
6. **布と金属の質感差**: 布には長い皺、裂け、擦り切れ。金属には細い明部、暗い傷、縁の銀色。発光箇所は面積を小さく限定する。

## 2026-09-23 時点の現行プレビュー評価

CPU正投影プレビュー `model-lab/build/previews/ashen_knight_detail.png` と上記写真を並べて判定。各項目は5点満点。これは実機描画の代用ではない。

| 項目 | 現状 | 必要な修正 |
| --- | ---: | --- |
| 遠景で分かる輪郭 | 1/5 | 前屈、脚の開き、布の面積と長さ |
| 頭部の固有性 | 1/5 | 獣面、細い目の穴、後方の飾り |
| 腕と剣の非対称性 | 2/5 | 片腕の脱力と柄の保持を明確化 |
| 装甲と布の重なり | 2/5 | 首布・肩・腰・背中の奥行き |
| テクスチャの意図 | 2/5 | 灰色一色に見える面を抑え、素材ごとの規則を強くする |
| アニメーション・攻撃演出 | 未採点 | Minecraftクライアントでモーションと警告表示を確認 |

**結論: 基準未達。** bbmodel検証やheadless smokeの成功は見た目の合格を意味しない。モデルを再造形し、正面・側面・背面・アニメ中の比較画像を更新してから再採点する。ModelFoundryの動画並みと判断するには、ゲーム内でシルエット、テクスチャ、演出、動きの確認が必要。

## 参照照合後の再評価

`scripts/render_ashen_review.py` で正面・側面・上面・斬撃中を大きく描画した。首布、剣を肩越しに構える腕、負傷腕、獣面の兜、外套の面積、灰銀色の装甲を追加した。静止画での再評価は、輪郭3/5、頭部2/5、非対称性3/5、装甲と布2/5、テクスチャ2/5。**依然として基準未達**。とくに横から見た顔の厚み、布の裾の自然さ、肩当ての装飾密度が不足し、動きとエフェクトも実機では未判定。数値は制作側の暫定評価であり、完成宣言には使わない。

## 2026-09-24: メカ感を指摘された後の作り直し

直立した四角い装甲、左右の肩突起、脚の反復する金属帯、横へ張った外套、厚い直方体の剣がメカらしさの主因と判断した。鎖帷子と革を土台にし、傷んだ片側の肩当て・巻き布・胴に沿う外套・欠けた薄い大剣に造形し直した。兜の左右差と後方に流れる飾り、膝の曲げを追加した。正面・側面・四分の三方向と6つの動作姿勢を `scripts/render_ashen_review.py` で出力する。

斬撃の刃先が地面を5モデル単位ほど貫いていたため、姿勢を再調整した。叩きつけは柄と刃先のワールド座標を確認して、接地時の刃先を地面付近へ合わせた。これは見た目の当たりと攻撃判定の一致を保証するものではない。

新しい静止画の暫定評価は、輪郭3/5、頭部3/5、非対称性4/5、装甲と布3/5、テクスチャ3/5。**完成基準には達していない。** 横からの顔面装甲の厚み、外套の立体的な皺、Minecraft内での色・透過・照明、攻撃エフェクトとヒットタイミングの照合を残す。最終feel判定はCreatorのゲーム内Manual Smokeで行う。
