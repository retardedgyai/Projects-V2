# 流星：一つの噴き上がりが割れて消える構成への変更

Verdict: **FIX-FIRST / WIP**。全10技の再制作、全31参照への最終照合、参考水準の品質達成は未完了。

## 前の作り方で外していたこと

- R12 [Wynncraft Mage Meteor](https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s) の保存した実表示を再確認。155.70秒は厚い接触の噴き上がり、155.84秒は空隙のある炎、155.99秒以降は消散。最初から空洞のアーチを育てる動きではない。
- R09 MatE Solar の保存表示も再確認。大きな明部とまとまった橙の面を、細かな色差で分断しない。Solarの静止画からアニメーションを確認済みとはしない。
- 元の実装は全Mageが補間0、毎tickモデルを交換。中間原画だけを増やしても、大きな輪郭のつながりは保証されない。
- 最初の連続変形試作 `meteor-native-flow-01` は、消散原画の四つの破片を主役へ流用してしまった。独立レビューは「旧版より質量感が弱い」「着弾後に小さくなってからアーチが育つ」。
- `03` は初期サイズ・形成時間を変えたが、独立レビューでも「太くしたアーチ」「消散の輪郭は主塊にならない」。この倍率調整を最終解として採用しない。

## 現在の変更

1. **噴き上がり原画を使う。** 既存の自作 `meteor-impact-atlas-v01.png` のdrawing 1を四つのつながった領域へ分割。元PNGは編集せず、ネイティブモデルの面と色を定義する。細い余分な点は主形から除外。
2. 領域は素材の四段階の明暗に沿って分割。初期状態では四つが隙間なく同じ噴き上がりを構成する。重複する四枚の画像ではない。
3. 原画の位置に対応するpivotをKotlin側の初期位置へ反映し、その後に四つが異なる方向へ離れる。主要なモデル形状は交換せず、移動・拡大・縮小を1tickの表示補間でつなぐ。
4. 交差した同じ絵の複製をやめ、原画の輪郭に沿う浅い厚みを持たせる。色は大きな四段階の面へ整理し、温度変化でも同じ形を維持する。
5. 低い接触の閃光は別の短命な表示。`eruption` には地面の接触・二tickの残熱・小破片だけを残し、元の爆発全体を二重に出さない。
6. 旧Mageの固定フレーム表現やWarriorは補間設定を変えない。新しい流星の五つの表示だけが対象。最大6 primary / 1 secondary、owner48 / scene384 / observer8は維持。

変更はMage専用の構成・モデル生成と、既存CoreLoopの具体的な表示消費側の限定分岐。
共有Protocol、Particle Framework core、Class runtime共通基盤、クライアント、ダメージ・命中・入力・MP・CD、承認済みWarrior/UI、mainは変更しない。
ゲームの起動・操作・接続も行わない。

## 確認方法

- Kotlinの実Minestom ITEM_DISPLAY出力にentity IDと補間時間、終端のscale0を記録する。
- `ExportMageDisplayTimeline.java` は既存の未改変Vanilla 26.2 Displayクラスへその値を入力し、1tick中に3回の変形を取り出す。独自の理想的な線形補間へ置き換えない。
- テスト用のClientLevelは描画補間に必要な最小状態のみ。Minecraftウィンドウ、GPU、通信、ワールドの照明を再現しない。公開クライアントの変更やゲーム起動ではない。
- `preview_skill_choreography.py` はその変形・実モデル・UV・tintを投影する。見栄え専用の加筆やbloomは入れない。

## 処理と重要ファイル

`CoreMageChoreography.parts/pose` → `CoreCombatMeshes` のITEM_DISPLAY → Vanillaの補間 → 終端縮小・消去。

- 形が違う場合: `scripts/build_mage_meteor.py` の `flow_grid/flow_mesh`、対応する原画セルとpivot。
- 位置や時間が違う場合: `CoreMageChoreography.kt` の流星の初期位置／分離先、`pose`。
- 補間・消去がおかしい場合: `CoreCombatMeshes.kt`、実出力 `.tools/mage-material-timeline.json`、`ExportMageDisplayTimeline.java`。
- 再生成: `python scripts/build_mage_meteor.py`。生成物だけを手編集しない。

旧v4の約2000差分は保全。Kotlinの既存frost_wave変更と追加テスト、旧v4の四つのtexture index行を今回のcommitに混ぜない。
`.tools/` と `.kotlin/` はcommitしない。Task branchへWIPとして通常pushし、本編採用を意味するmergeはしない。

## 最新04の視覚レビューと残り

確認: `.tools/meteor-native-fracture-04-eye.gif`、eye/sideの36・39・42・45・48・51・54・57・60・63番。60Hz相当なので36番は着弾0.60秒。

- 主担当と読み取り専用レビューの両方で、0.60〜0.70秒の厚い噴き上がり・つながった明部は03から改善と判断。小さなアーチが遅れて育つ構成を脱する方向の進歩。
- **未達:** 0.80／0.90秒で、分割した部品の内側の垂直線と水平な底辺が露出し、切り抜き片に見える。側面では板の積層に潰れる。R12のように分離後の各炎塊が完結した輪郭と側面を持っていない。
- 次は分割線をギザギザにするだけでは足りない。開始の噴き上がりと連続変形は残し、離脱する部品の厚み・側面・内側の形を作り直す。倍率・寿命・補間調整を繰り返して合格扱いしない。
- 岩・燃焼尾・短い縦の残熱も最終品質合格ではない。今回の修正を全Meteorの完成や、他9技の完成へ拡大解釈しない。
- 全31参照への制作後の最終照合は未実施。今回再確認したのはR12/R09であり、新しい参照件数を水増ししない。
- ゲームの照明・通信・実機feelは未確認。今すぐゲームテストへ渡す完成版ではない。

## 検証結果

- 最新Kotlin: `CoreMageChoreographyTest` 8件、`CoreCombatMeshTest` 19件、計27件成功。旧v4の独立したfrost_waveの未commitテスト1件もこの作業ツリーの8件に含まれる。commitへは混ぜない。
- 同turnの前段で、補間の限定分岐に対する既存 `CoreFlowSlashChoreographyTest` 5件も成功。最後の実行ではフィルターに誤記があり含まれていないため、最新27件を32件と報告しない。その後に既存Flowの処理は変更していない。
- 専用Python8件: 72既存モデル＋15個の温度別固定モデル、再生成一致・予算・座標・原画byte一致、四領域の非重複・一つにつながる初期形・pivot、温度変化時の形状維持、残熱寿命を確認。使わなくなった旧 `pressure_burst` の生成関数を除去し、旧未使用アニメーションのテストを現行の形状検証へ置き換えた。
- commit対象のindexを隔離したツリーでも専用Pythonテストを実行。旧v4の未採用ヘルパー変更への依存がないことを確認。
- pack構造: 16262 assets成功。SHA256 `18b5707837980f37e76589c6b5bfb0837f88c7c23fa674ffcae6dc15e9231be6`。これは旧v4差分も存在する作業ツリーの構造検証であり、全Mageの品質や全Python suiteの成功ではない。
- 使用中の未改変Vanillaクラスによる再生: 3着弾×5表示=15ストリーム成功。有限の変形と削除前のscale0目標を検証。滑らかなGPU描画・ネットワーク欠落への耐性を証明するものではない。
- 最大elements: `meteor`635、`eruption`234。構造上限に余裕があっても視覚的な合格にはならない。

主な再確認手順: 既存Java25/Gradleパッチ環境で上記Kotlinテスト → `scripts/CheckNativeDisplayInterpolation.java` と `scripts/ExportMageDisplayTimeline.java` を既存Vanilla classpathでコンパイル → `.tools` 内で `ExportMageDisplayTimeline mage-material-timeline.json mage-native-fracture-04-60fps.json` → `preview_skill_choreography.py --timeline .tools/mage-native-fracture-04-60fps.json --ids meteor --prefix meteor-native-fracture-04-eye --view eye --fps 60`。
