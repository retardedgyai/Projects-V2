# 戦士AAを承認済みの踏み込み斬りへ統一

9月11日追記：作画は維持し、AAの準備時間と不可視待機は撤去。
現在の入力タイミングは [即時AA修正](warrior-aa-immediate-2026-09-11.md) を参照。以下は9月9日時点の記録。

## Creator要求と範囲

「AAもこの踏み込み斬りと同じ感じ。消え方、作ったときのロジック、作画も同じ」。
戦士の通常攻撃3段だけを対象とする。承認済み `CoreApprovedDashV3` とその生成原稿は変更しない。
職業基盤、命中判定、ダメージ、AS、コンボ受付・回復時間、他職業、他スキル、大剣本体は変更しない。

## 何を揃えたか

- 旧AAの8本の短い帯を使わず、承認版の移動する刃先から作った湾曲ピクセル面を再利用。
- 刃7tick・残像13tick。各区間の経過時間で幅が減り、切れ目から分離して消える元のロジックを維持。
- 表示物全体の回転、時間による拡縮、逆再生は行わない。モデル変形補間0も元の方式を継承。
- 白い刃・鋼色の面・青灰色の残像・小さい暖色の命中光を維持。新たな色や絵柄を足さない。
- 1段目は承認版と同じ向き。2段目はモデルのX座標だけを反転し、返し斬りにする。
- 3段目は同じ原稿を固定の深い斜め角度で表示。モデルが毎tick回転するわけではない。
- 各段の既存の表示距離2.1/2.2/2.7mへ寸法を合わせる。実際の通常攻撃判定4.5mは変更しない。
- 準備0〜2コマは実コンボの `impactTick - 1` に合わせる。命中タイミングのreleaseは3コマ目から。
- 命中光はサーバーが受理した敵の位置のみ。多数の敵では1tick最大3個、空振りに命中光は出さない。
- パック表示時は旧WINDUP/HITの粒子を重ねない。パック未読み込み時の既存フォールバックと音は維持。

## 処理と主なファイル

既存のコンボ受付・命中 → `GreatswordVfx.normalPrepare/play/normalContact` →
`CoreApprovedNormalV3.parts` → 固定版 `CoreApprovedDashV3.parts/pose` → 既存ItemDisplay。
2段目のみ `CoreApprovedNormalV3.pose` が反転版モデルIDへ送る。

- `CoreApprovedNormalV3.kt`：最重要。AA3段への薄い適用層。位置、倍率、固定角度、反転の指定。
- `GreatswordVfx.kt` / `CorePlayerCombat.kt`：既存の準備と受理済み命中の通知先だけを接続。
- `CoreSkillChoreography.kt` / `CoreSceneParticles.kt`：AAの専用ルートと旧粒子の二重表示防止。
- `scripts/build_approved_aa_v3.py`：固定版の出力を反転するだけ。26モデル＋26item定義。
- `combat_vfx/approved_aa_reverse_v3/`：2段目専用。1・3段目と命中光は元の資源をそのまま参照。

## 確認

- サーバー全814テスト成功（失敗・エラー・skipなし）。Gradle offline testビルド成功。
- 固定版の71資源のGit blob一致を含むPython4件成功。
- 無改造Vanilla 26.2のCuboidModelパーサーで反転26＋元35＝61モデルを受理。不正軸の負例は拒否。
- Kotlin：3段のコマ順、実ASによる準備、消滅、8方位の回転後の全頂点、地面・前方・距離を検査。
- 実Minestom ItemDisplayで全phase／全コマのモデルID、座標、scale、左右rotation、補間0、除去を検査。
- 実際のAA描画入口から準備・各段・命中を呼び、承認モデルが出て後片付けされることを検査。
- `.tools/approved-aa-v3-timeline.json` は現行ルートから出力したもの。
  `.tools/approved-aa-v3.gif` はそれを元版のレンダラーで投影した3段比較。ゲーム録画ではない。

## 手動確認と再開点

ゲーム操作と最終feelはCreatorが行う。今回、起動済みのサーバー／クライアントは停止・操作しない。
反映には次回 `installDist` と `build_material_playtest_pack.py` の再生成、サーバー／クライアントの再起動が必要。
大剣の承認済み本体を保つため、引き続き `start-core-loop.ps1 -WeaponMaterialReview` を使う。

踏み込み斬りの基準原稿やパックが壊れた場合は `test_approved_dash_v3.py`、
AAだけ方向が変なら `CoreApprovedNormalV3`、反転資源が欠ける場合は `test_approved_aa_v3.py` とpack indexを見る。
ゲーム内のみ問題があれば `CoreCombatMeshTest` とサーバーのパック読み込みログから調べる。

`play/gyai/class-armament-art` にcommit・通常push。main、`.tools/`、`.kotlin/`、認証引数は含めない。
