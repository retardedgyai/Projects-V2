# 桜牙獣（sakura fang）— 2体目のBlockbenchボス

制作手順は [boss-model-pipeline.md](boss-model-pipeline.md)。モデルとアニメは `python scripts/build_boss_sakura_fang.py` が `assets/bosses/sakura_fang.bbmodel` に書く。

## 姿

- ジンオウガ型の骨格（低く構える、巨大な前腕、短い後脚、背へ反り上がる太い尾）。骨格の比率は公式レンダーから読んだだけで、形・名前・技は独自。
- 顔になるモチーフは「桜の花びらの甲殻」。背から尾の外側のふちへ、二重の花びらが1列に重なる。ふだんは閉じ、怒ると左右に開く（`shell_*` 骨）。
- たてがみの毛先と角の先が桃色。前腕と肩は重ねた甲殻に光る縁、脇腹と尾に光る筋。

## 技（Tier1、溜め→決め→余韻）

| id | 名前・避け方 | 形 | 予兆→硬直(ms) | 種別 | 段階 |
|---|---|---|---|---|---|
| sakura-claw | 桜爪の叩きつけ・横へ | 前方帯 6×4.8 | 1200→1000 | 物理 | 1〜 |
| sakura-tail | 花尾の振り下ろし・横へ | 前方帯 9×3.6（尾が背を越えて前へ） | 1500→1100 | 物理 | 1〜 |
| sakura-scatter | 散華・扇の外へ | 扇 半径12・80° | 1700→900 | 魔法 | 1〜 |
| sakura-bloom | 花嵐・獣から離れろ | 円 r8 | 2000→1200 | 魔法 | 怒り後 |

- HP 560、弱点 炎、移動 0.14（怒り後×1.2）。体は不可視のRavager（音もravager）。
- 怒り（HP50%で1回）：5秒の咆哮。2.5秒で立ち上がって花びらが一斉に開き、桜の葉の粒が闘技場に広がる。以後は開いたまま。
- 当たった瞬間に、技の形の上へ桜の葉と桃色の粒（`QuestEncounterCombat.sakuraBurst`）。シェーダー演出はまだ無い。

## 確認

- `/bosstest sakura` で「散り桜の庭」（昼、苔と草に花びら色の床、桜の葉の壁）に出る。dev専用、報酬なし。
- プレビュー（CPU、Minecraftの画面ではない）：`python scripts/boss_preview/sakura_sheet.py`（姿）、`python scripts/boss_preview/sakura_clips.py`（技の4コマとGIF）。
- 黒翳竜専用の演出（光・紫の波・部位破壊・ギミック）は `Mob.shade` で黒翳竜だけに絞った。

## 未決

- 本編の入口と報酬（黒翳竜と同じく保存形式に関わる）。
- 技の演出をシェーダーで作るか。
