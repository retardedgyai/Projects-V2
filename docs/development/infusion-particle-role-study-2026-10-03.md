# 魔術小粒の役割別設計比較 — 2026-10-03

現在の状態は「役割別の独立比較まで」。稼働serverの画像選択は変更していない。単に同じ16px画像を置換しただけでゲーム側の役割分離が済んだとは扱わない。

## 実際にできた差分

`assets/model-lab/infusion-particle-role-study` に、異なる3つのsprite/model/item識別子と比較PNG/GIFを作成した。ゲーム本体のKotlinファイル、active packのindex/atlas、クライアント設定に差分はない。

| 役割 | 独立比較用画像 | 画像を選ぶ条件 |
|---|---|---|
| Jarから出るもやと前半の運搬 | `infusion-role-study/jar-mist` | Jar transferのprogressが `.65` 未満 |
| 核吸入口付近へ集まる小粒 | `infusion-role-study/intake-mote` | 同progressが `.65` 以上 |
| 核から装備への注入・集束の小粒 | 同 `intake-mote` | `radiance:flow` / `convergence` のkey |
| 完成時の装備周辺の小火花 | `infusion-role-study/success-spark` | `weapon-success` のkey |

前2画像は前回のB/Aをそれぞれ役割用に分け、完成小火花には短い不均一な輝点の新しい16pxピクセル画像を用意した。billboard geometry、両面UV 0..16、tint定義は元のsmokeモデルと同じである。識別子が分かれているため、次の修正でJarだけ・吸入だけ・完成だけを変更できる。

画像選択は**独立exporterだけに存在する提案**であり、現在のserverには接続していない。`.65` という切替境界はProjectS用の仮案。TC6の実仕様や本人が承認した境界ではない。境界で絵柄が切り替わる見え方は実機未検証である。

## 同じ数値条件での検証

`scripts/InfusionParticleRoleStudy.kt` から既存のinstalled server JAR内の `WorldInfusionSmoke` / `WorldInfusionCharge` / `WorldInfusionRadiance` / `WorldInfusionConfluence` を数値呼出しした。server初期化、ネットワーク待受、ユーザーsaveの読書きは行っていない。状態は比較用に段階別に作るfixtureであり、実際の儀式を最初から最後まで通した記録ではない。

Jarの放出/集束、核→装備の注入、完成の3clip、合計41フレームを出力。各sampleは画像参照以外のposition、count、scale、width/height、rgb、progress、facing、brightnessが同一であることをassertした。

- 画像参照の変更: Jarもや90、吸入/集束143、完成小火花80 sample。
- 保護したsample: 大きな核のhalo/柱/wave/flash84、核周回小粒99、台座上昇粒50。
- 大きな核の4種類のPNGは稼働packから同じbytesを両比較へ使用し、hashも記録。
- 数・位置・軌道・更新時刻・元素色・通信制限・saveを変更する修正は含めていない。PNGの面積/alphaが違うため、見かけの密度まで同じとは保証しない。
- Vanilla DUSTの素材吸収軌跡は今回変更していない。本人の指摘対象がこちらだった可能性は未解消。

結果: `ROLE_STUDY_EXPORT_PASS frames=41 changedModels=313 protectedLarge=84 sampleCountPositionScaleTintFacingBrightnessProgressUnchanged noServer noSave`。

比較画像は粒だけのorthographic描画である。3Dモデルの遮蔽、クライアント照明、補間、実際のカメラ/FPSは含めず、Minecraft実画面の証拠としない。全体図は大きな核エフェクトも残し、`role-particle-details.png` は対象小粒だけを同じ範囲から切り出してnearest-neighborで拡大した。

## 実TC6参照と推測の境界

実見した資料は [TC6 1.12.2チュートリアル54:24の静止画](https://jaguidesminecraft.com/wp-content/uploads/2026/07/Thaumcraft-6-Full-Tutorial-1.12.2_at_54-24.png.webp)、掲載元は [チュートリアル記事](https://jaguidesminecraft.com/thaumcraft-6-full-tutorial-1-12-2/)。

台座付近の青/シアンの点に、小さな明るい芯と薄く不均一な外縁、点ごとの見かけの大きさ/濃さの違いを確認した。画像はローカル参照用だけに取得し、自作spritesへ転載していない。

この1枚から発生源、吸入中/残留等の役割、集束や完成の時系列、実atlasは判別できない。今回のJar/吸入/完成という割当てはProjectSの既存keyとprogressに基づく設計案である。TC6そのものの役割別演出を再現したとは称さない。TC7の現行releaseや完成サイクルの根拠としても使わない。

## 残りと待ち

実機確認は既存ワールドへの入場・元の表示修正まで。これらの役割候補は未反映・実機未確認である。本人のplay終了は未確認のため、restartやlive pack更新は行わない。

次に確認するのは、本人が指摘した粒の具体的な場面、`.65` の境界での絵柄変化、元素色と照明での輪郭、量を変えずに読みやすくなったか、主核の印象が保たれるか。必要なら境界や画像の濃さを候補folderで調整する。適用前に、live側へ最小の役割参照差分をどう接続するかを明示する。

GitHub pushは自動承認審査に拒否されたまま。追加再試行や別経路は使わず、この保存点は同じ独立branchへローカルcommitのみを行う。

再生成: `scripts/export-infusion-particle-role-study.ps1` → `scripts/render-infusion-particle-role-study.py`。数値traceと検証JSONは画像と同じ候補folderに保存する。
