# Mage material rework v2 — 2026-09-15

> この版の見た目は、その後Creatorが不合格とした。下記の「制作プレビュー可」は
> 過去のレビュー記録であり、現在の品質合格を意味しない。
> [氷の庭だけを作り直す後続作業](mage-garden-sculpture-2026-09-15.md)へ移行した。

## 範囲と状態

ユーザーが停止した最初の幾何学的な v1 は不採用。三角形の内側を縮めて塗る
共通 `plate()` と、その全スキルへの展開は削除した。
この版は Mage 全10技の再実装。Warrior の承認済み AA / 踏み込み斬り、大剣、
UI・フォント・スキル係数・入力・ヒット・CD・音設定・Starweaver は変更しない。
サーバーの表示処理とリソースパックだけで動作する。クライアント改造はない。

**技術テストと見た目の合否は別。MatE / Wynncraft と同水準になった、と保証した版ではない。**
途中レビューで火炎弾・流星雨・庭は制作プレビュー可、残りは FIX-FIRST と判定し、
門柱・盾アイテム・タイル・横長板の修正を実施した。
最終的なゲーム内の光・距離・手触りは Creator の手動確認が残る。
稼働中のゲーム／パックは更新せず、起動も行わない。

## 実際に観察した参考

- [MatE's RPG Classes — Cryomancer](https://www.youtube.com/watch?v=LztyUoK2KGk)
  作者チャンネルから確認。24／38／50秒付近の青い氷晶群などをブラウザー表示で観察。
  大小の氷のまとまり、濃い側面、明るい稜線、地面との接触を参考にした。
  疎な静止観察から正確なフレーム配列や作者の内部実装を推定したとはしない。
  これがユーザーの言う特定の氷魔法かは確認質問中。
- [Celestial Sorcery — THE ULTIMATE MAGE GUIDE 2026](https://www.youtube.com/watch?v=bi4Jj5iFzks)
  Wynncraft プレイヤーのゲーム映像。作者自身の公式映像ではない。
  読み取り専用の別レビュー担当が 3:35 付近の Ice Snake の低い波と不均一な氷晶を直接確認。
  古いキャッシュの町の静止画は Mage 戦闘の証拠として使用していない。
- 第一の作画基準はリポジトリ内の承認済み大剣：
  `assets/class-armaments/texture-first/sources/greatsword-material-v02.png`。
  赤色や蛇行する剣形を全てへ使うのではなく、大きい色面・輪郭・役割ごとの厚みを採る。

外部作品のモデル・テクスチャはダウンロード／抽出していない。

## 実装した区別

| 技 | 主役 | 時間・位置 |
| --- | --- | --- |
| 火炎弾 | 原画に基づく非対称な熱塊＋別形状の短い熱流 | 受理された射線内。芯が先に燃え、上下の尾が別の寿命で剥がれる |
| 霜の波紋 | 低い厚みのある波頭3群、床の細い亀裂 | 18tickで外へ進行。通過した根から崩れ、棚を置きっぱなしにしない |
| 流星雨 | 炭化した立体の岩→根の熱い噴炎 | 各実PULSEへ落下と着地を対応。交差する同一噴炎2枚は撤去 |
| 閃光歩 | 上下を切り離した不揃いな魔力片 | 出発は内へ、到着は外へ。長い門柱なし。各確定地点に固定 |
| 雷の刻印 | 太い導線＋異なる距離の短い非対称分岐 | 射線のZ範囲を厳守。発動時の通電と短い余韻 |
| 氷の庭 | 原画の結晶・低い氷片・小群を非対称に配置 | FIELD固定。初回に形成、実PULSEは亀裂のみ、最後に根から崩れる |
| 術式起爆 | 中心から裂ける不揃いな放電 | 四角い床タイルは撤去。低い中心と広がる枝を分離 |
| 魔力障壁 | 隙間を多く持つ3片の魔力殻 | 本人追従。盾の完成輪郭・紋章・大きい塗りつぶしを撤去 |
| 天火の大術式 | 頭上で割れる灼熱の核＋下向きの放出 | 本体は初回だけ。実PULSEごとに短い放出。巨大な四方の炎壁なし |
| 絶対零界 | 大小の傾斜した立体氷面・低い破片 | 一度形成した48tickの本体が崩壊。実PULSEは内部亀裂のみ |

零界の氷は、同じ頂点へ集まる4つの傾斜面を持つ閉じた立体。
ネイティブで許される22.5／45度の面をピクセル幅の帯で構成し、
薄い絵を押し出した看板だけにはしない。サイズと高さは不均一。
庭などの絵を用いた薄い断片とは用途を分ける。
最終レビューで指摘された零界の単色面には庭と同じ原画の明暗を割り当てた。
隣接面をピクセル段で重ね、頂点付近の細かな隙間を塞いだ。
1モデル1,000要素以内を維持するため、24段・16列の色面に整理した。

接触は受理された CONTACT だけで小さく表示する。空振りに命中爆発を足さない。
長い本体を後続PULSEで再生し直さない。追加PULSE数でも副演出は実イベントから発生。
`控えめ` は主役を維持し、装飾の小片は省略。既存の人数別表示上限と掃除を維持。

## 原画と再現性

内蔵 image_gen を使用。選択した原画は以下の3点。

- `assets/combat-vfx/mage-v2/sources/firebolt-v02.png`
- `assets/combat-vfx/mage-v2/sources/ice-forms-v01.png`
- `assets/combat-vfx/mage-v2/sources/meteor-eruption-v01.png`

最終プロンプト・入力画像・用途は同ディレクトリの `provenance.json`。
透明背景を要求したが、出力は不透明なチェック柄を含んでいた。
元PNGは変更せず、描かれた色だけを形状・マテリアルへ変換する。
チェック柄を含む画像面をゲームへ貼ってはいない。新しいPNGの塗り直しをPythonで代用していない。
最初の丸い炎原稿は不採用としてローカルの `.tools` へ退避し、配布しない。

## 処理とファイル

1. 変更していない `CorePlayerCombat` が確定した各フェーズ・原点・射線を渡す。
2. `CoreMageChoreography` が技ごとの素材・寿命・配置を決める。
3. `CoreCombatMeshes` が既存の ItemDisplay、所有者／観客LOD、破棄を管理。
4. `build_mage_materials.py` が次の具体的な素材コードからRPのモデルとitem定義を生成する。
   `build_mage_fire.py`／`build_mage_ice.py`／`build_mage_arcane.py`／`build_mage_cataclysm.py`。

最重要クラスは `CoreMageChoreography`。位置・方向・時間の不具合はここ、
欠落テクスチャはRPの `index.txt` とモデル／item定義、色面・崩れ方は各素材生成ファイルを見る。
通常の `build_core_combat_models.py` にもMage生成を接続したので、全体再生成で取り残さない。

## レビュー方法・限界

`.tools/mage-material-timeline.json` は隔離したMinestomテストから書き出した実ItemDisplay metadata。
主観投影と斜め投影、20Hz動画でPREPARE・全PULSE・接触素材・転移の両端・消散を見る。
ゲーム画面ではなく、実ライト・通信到達時刻・地形遮蔽・フレームレートを再現しない。
転移の投影カメラは自動でプレイヤーを移動しないため、端点付近の見え方を分けて判断する。

斜め投影の旧式は地面を下から見る座標系だったため、面の奥行き評価に不適切だった。
上から見る座標系へ修正。これは検証用カメラだけの修正で、ゲーム内のエフェクトは別途実データで確認する。
絵の押し出しの内部面も減らした。零界は正面を本人に向ける回帰テストを追加した。

読み取り専用の独立レビューで全10技を個別に確認。最後に残った零界の材質と
面の隙間も再確認し、制作プレビュー／手動確認へ出せると判定した。
これは静止した連続フレームの判定であり、実機の動き・音・参考作品との同等性の判定ではない。
残る確認点は、零界の裏面の単色感と消散時の横切れ、障壁が防御領域に見えるか、
天火の中心の暗い継ぎ目、火炎弾の主観での形の読み取り。

## 再生成・検証

```text
python scripts/build_mage_materials.py
python -m unittest discover -s scripts -p test_mage*.py
python -m unittest discover -s scripts -p test_warrior*.py
python -m unittest discover -s scripts -p test_approved*.py
python scripts/verify_core_ui_assets.py
gradlew :server-minestom:test :server-minestom:distZip --no-daemon --offline -Pkotlin.compiler.execution.strategy=in-process --max-workers=2
python scripts/preview_skill_choreography.py --timeline .tools/mage-material-timeline.json --ids firebolt,frost_nova,meteor,mage_blink,mage_mark,mage_garden,mage_burst,mage_ward,mage_ult,mage_zero --prefix mage-final-eye --view eye
```

Java25・sandbox patch・Gradle cache は既存環境を使用。
`.tools/` と `.kotlin/` はcommitしない。main は変更しない。

## 最終検証結果

2026-09-15、零界の最後のマテリアル修正後に実行：

- Mage 素材の再現性・native model・元絵保全：3件成功。
- Warrior 作画／アニメーション：28件、承認済み斬撃：4件成功。
- サーバー全体：855件、失敗0／エラー0。
- `:server-minestom:test :server-minestom:distZip` 成功（3分38秒）。
- 配布ZIP内のserver JARがbuild/libsのJARと一致。
  内包RPのindexと全15,695項目も、現在のソースとバイト単位で一致。
- Pack検証：15,695 assets／50,695 private glyphs／50 scoped HUD sprites。
  global font overrideなし。
- Pack content SHA256：`3b4449663416226868f90db62df5874ce4c8040922fc1cf27385129d4ce93063`
- ZIP SHA256：`ecd7d109f76b16c4de60c50974285aa8971155f7a75f6bbd014fd5a84d9e5904`
- 最終プレビュー：`.tools/mage-final-eye.gif` と `.tools/mage-final-overhead.gif`。
  各80フレーム、20Hz。データ末尾の長い空白だけを短縮し、実演出の時間は保持。

途中の追加検証は `--tests` をdistZipへ渡すコマンド指定ミスで一度停止した。
最終的には上記の全テストを再実行し成功。ゲーム起動・実機手動操作は実施していない。
checkpoint先：`play/gyai/mage-visual-rework`。mainへのmergeは行わない。
