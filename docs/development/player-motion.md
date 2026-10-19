# プレイヤーの人形モーション（試作）

技の間だけプレイヤーを隠し、本人のスキンを貼った人形（ディスプレイエンティティ）を動かす案。2026-10-19：サーバーで人形を出す仕組みまで実装（開発用コマンド `/motion` で再生）。技への組み込み・斬撃や揺れの演出はまだ。

## 動きの正本は Blockbench

```text
assets/player-motions/<武器>.bbmodel   ← 正本（Blockbenchで直接編集する）
   greatsword.bbmodel   … アニメ greatsword_charge（大剣・溜め斬り）
   dual_blades.bbmodel  … アニメ dual_blades_dance（双剣・乱舞）
server-minestom/src/main/resources/player-motions/<技>.json
   … 技の付帯情報（"blockbench": "<モデル>#<アニメ>"、武器、ヒットストップ等のイベント、段階名）
```

### 編集の手順

1. `~/Applications/Blockbench_5.2.2.AppImage` で `assets/player-motions/greatsword.bbmodel`（または dual_blades）を開く。
2. Animate タブでアニメを選び、骨を回して保存する（Ctrl+S、同じファイルに）。
3. `python3 scripts/preview_player_motion.py greatsword_charge` → `.tools/motion/greatsword_charge.gif`（横・背後・一人称）。

### 決まり

- 骨：`root`（腰）＞`torso`＞`head`／`arm_r_up`＞`arm_r_low`＞`weapon_r`（左も同じ）、`root`＞`leg_r_up`＞`leg_r_low`（左も同じ）。名前と親子は変えない（スキンの貼り方とサーバーが名前で読む）。
- 前は +Z、16px＝1ブロック、足は y=0。人形の右手は -X 側。
- 腕と脚は自分で回す（FK）。Blockbench で見えた通りに動く。両手持ちの大剣は、左手を柄に合わせるのも手で付ける。
- `weapon_r` / `weapon_l` の中の箱は編集用の目印。プレビューとゲームでは本物の武器モデルが、骨の位置と向き（刃は +Z、刃の幅は Y）に描かれる。
- 動かす位置は `root` だけ、それも y（しゃがみ）と z（踏み込み）だけにする（X は Bedrock 形式で符号が反転するため避ける）。終わりは 0 に戻す（人形を消してプレイヤーに戻すときにずれない）。
- 補間は linear / catmullrom / step を読む。

### たたき台について

最初のアニメは、以前の数値キー（握りの位置＋刃の向き、腕と脚はIK）から 0.05秒ごとに角度を焼き付けたもの。キーが多いので、作り直すときは間引いてよい。
`python3 scripts/player_rig.py build <技>` で作り直せるが、既にファイルがあると止まる（`--force` で上書き。Blockbenchでの編集が消える）。

## スキン

ゲームでは、肌の1ピクセルごとに小さな面を1枚作り、サーバーが本人のスキン画像から面ごとの色を custom_model_data で送る（シェーダー不要、26.3で可）。Paper用 BetterModel / DynamicUV（MIT）と同じ考え方で、コードは写さず自前で作る。repo の .bbmodel に貼ってあるのは自作のマネキン肌（Mojangの絵は入れない）。プレビューは手元のクライアントから取り出した標準スキン（`.tools/skin`、コミットしない）を使う。

## 一人称

自分のカメラは人形についていけないので、自分には人形を見せない。大剣は手に持った剣のポーズ（`fp_grip`/`fp_blade`、カメラ基準）で、双剣は両手に持った刃で振りを見せ、溜めは画面のふちの光、当たりは画面の揺れ・白い光・一拍止めで出す。

## サーバー側（2026-10-19）

- `puppet/PlayerMotion`：`player-motions/<技>.json` と、それが指す `.bbmodel`（ビルドが `assets/player-motions/` から resources へコピー）を読み、骨の位置と向きを計算する（`scripts/player_rig.py` と同じ式。テストで数値が一致することを確認）。
- `puppet/PuppetSkin`：プレイヤーのプロフィールのスキンURL（Mojangのテクスチャサーバー）から画像を1回だけ取り、部品ごとの色を作る。届くまで・スキンが無いときはマネキン。今は肌の1層目だけ（帽子・上着などの2層目は未対応）、細腕（slim）は太腕として読む。
- `puppet/PlayerPuppet`：本人を透明にし、他の人には装備も空に見せ、人形（体10部品＋武器）を本人以外に表示して毎tick動かす。終わったら元に戻す。人形は動き始めの向きのまま、本人の位置に立つ。
- 部品モデル：`scripts/build_player_puppet.py`（`gen_resource_pack.py` の一部）が `combat_vfx/puppet/*` を生成し、色の番号とスキンのピクセルの対応を `player-motions/puppet-layout.json` に書く。
- 確かめ方：`python3 scripts/preview_puppet_displays.py greatsword_charge` は、サーバーが送る値をパックのモデルでそのまま描く（`PlayerPuppetTest` の書き出しを先に実行）。

### ゲーム内で見るとき

`./gradlew :server-minestom:run` で起動し、`/motion greatsword_charge` か `/motion dual_blades_dance`（`/motion stop` で止める）。自分には人形が見えない作りなので、1人で確かめるときは `/motion greatsword_charge self`（開発用：自分にも人形を見せる）でF5にして見る。一人称では人形がカメラを囲むので、本番の見え方ではない。
