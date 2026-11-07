# 散り桜の社の雑魚

藍（2026-11-07）「桜コンセプトのマインクラフトのデフォルトにいるモブみたいなの作りたいな　ちゃんとアニメーションもあるタイプ」、
続けて「Runeforgeのモブみたいにもっと細部作り込んでほしい」。

案は8体（案のページ https://claude.ai/artifact/JhTaFqfk2QfeQ4UUSG3rH7 、☆で選ぶ）。いまゲームに入っているのは3体。

| 敵 | 手本の体 | 役 | スキル | モデル |
|---|---|---|---|---|
| 桜木の亡者 `SAKURA_HUSK` | ゾンビ | 近接で寄ってくる（追放兵の代わり） | 枝の掴み（両腕を振り上げて叩きつける）／薙ぎ払い（体をひねって片腕で払う） | `sakura_husk` |
| 枝弓の骸 `SAKURA_ARCHER` | スケルトン | 遠くから撃つ（術師の代わり）。寄られると下がる | 枝矢（弦を引いて溜め、本物の矢が飛ぶ。横に動けばよけられる）／弓打ち（寄られた時、弓を棍棒のように振る、扇） | `branch_archer` |
| つぼみ `SAKURA_BUD` | クリーパー | 自爆 | 開花（脈打って膨らみ、花が開いて弾ける。輪。撃った後は自分も散る）／花粉（寄る途中、遅い花粉の玉を吹く。飛ぶ） | `bud_creeper` |

残りの案（花ぐも・花狛・舞い花・花盾の衛士・根っこの切り株）は `scripts/build_sakura_mobs.py --out DIR` で描けるが、ゲームにはまだない。

## 作り方

- `python scripts/build_sakura_mobs.py` が、ゲームで使う3体を `assets/bosses/<モデル>.bbmodel` に書く（ミニボスと同じ Builder と bake）。
  `--out DIR [id..]` で8案のどれでも。見本の絵と動き：`python scripts/boss_preview/sakura_mobs_preview.py DIR OUT`。
- 作りの決まり（RuneForge の録画と、その雑魚の部品の数を見て決めた。写していない）：
  - 体の骨組みはバニラのモブ（ゾンビ・スケルトン・クリーパー）、16px＝1ブロック、1テクセル＝1px。
  - 部品は 30〜80 個、骨は 6〜12 本。肋骨は1本ずつ、手足は 2.4〜4px、肩・肘・膝に関節の玉。
  - 帯・腕輪・襟は下の部品より 0.3px ふくらませた箱を重ねる。花びら・紙垂・羽は板。
  - 面はノイズなしで塗る：暗い縁1px、中の色、上と左の明るい縁（`panel`）。桜の幹は短い横すじ（皮目）。
  - 桜は形で出す（花・つぼみ・萼・枝・幹の皮目）。社の小物（注連縄・紙垂・鉢巻）を二つめの素材に。
- 動きの名前はサーバーとの約束：`idle` `walk`（ループ）、スキルごとに1本（長さ＝予兆＋戻り、予兆の終わりで当たる）、
  `hurt`（被弾のひるみ、スキル中でない時に `BossModelView.hurt` が流す）、`death`。つぼみだけ `spent`（開いた花が消える。自爆の後に流す）。

## サーバー

- 殴りは範囲（前の帯・扇・輪）で、地面の予兆を出す（藍 2026-11-07「範囲でいい」）。矢・花粉は `MobAttackShape.Shot`（`QuestEncounterCombat.loose` で放ち、`tickShots` が毎tick進めて最初に触れた人に当てる）で、地面は塗らない。
- 予兆の見た目（全部の敵に共通、`MobGroundTelegraph`）：くすんだ赤の塗りに、縁は明るい赤の5段のトゲ（PROJECT Spellforged の範囲予告の縁、藍 2026-11-07「パーティクル消そう」）。縁の赤い粒（DUST）はやめた。

- 定義は `QuestMobContent`（等級 NORMAL、普通の敵と同じく乱数で精鋭などになる）。描画は `ModelCarrier` と `BossModelView`（白蛇・石灯籠と同じ）。
- 当たると花びらが散る（`QuestEncounterCombat.SAKURA_COMMON` → `sakuraBurst`）。
- つぼみの開花（`SELF_BURST`）が当たったら、つぼみは体力を失って `defeat(mob, null)`：部屋の数には入る、倒した人はいない（報酬の主もいない）。
- 散り桜の社の部屋（`InstanceDungeonFactory`）：回廊・散華の間の波、穢れ場の湧き、入口の群れ、段階が上がった時のミニボスの供が、この3体になった。
  盾持ちと精鋭（追放兵）はまだそのまま。

## 確かめる

- `/mobtest husk|archer|bud`（開発コマンド）：花守の試し場に1体だけ出る。
- 撮影：サーバーの作業フォルダに `claude-mobfilm.txt`（中身 `husk` など）を置くと、テスト用クライアントがその試し場で7ブロック前に立ち、
  敵が寄って撃つのを待ち、7秒と7.8秒に軽く当て（ひるみ）、10秒で倒す。`scripts/dev_film_mob.sh <名前>` が、`scripts/dev_film.sh` と同じ隠れた画面で録る。
  - 隠れた画面の窓が小さい時（FILM 出力に並ぶと 924x468）：`hyprctl dispatch 'hl.dsp.window.float({action="enable", window="address:<窓>"})'` と
    `hl.dsp.window.resize({x=1600, y=900, ...})`、クライアントの窓を広げるのは `python3 scripts/xkey.py :5 fit:<窓ID>`（`xwininfo -display :5 -root -children`）。
  - 初回の画面設定・職業選びの窓、三人称（F5）、試し場から出る `/hub` も `scripts/xkey.py :5 click:x,y F5 t slash h u b Return` で送る（隠れた画面だけ）。
- テスト：`SakuraMobsTest`（3体の定義と動きの長さ、つぼみが弾けて散る）。
