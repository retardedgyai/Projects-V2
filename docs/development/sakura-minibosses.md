# 散り桜の社のミニボス — 2体に共通の土台

散り桜の社の道順は 入口の間 → 花守の回廊 → **ミニボス1** → 散華の間 → **ミニボス2** → 散り桜の庭（桜牙獣）。
どの2体にするかは藍が案のページ（https://claude.ai/artifact/WYmpr7wcDMcFv7L31w1Cbb 、14案から☆で2体）で選ぶ。
ここに書くのは、どの2体になっても使う部分だけ。姿・スキル・動きは選ばれてから作る。

## 部屋からの呼び方

- 部屋の波に `QuestMobArchetype.SAKURA_MINIBOSS_1`（ミニボス1）か `SAKURA_MINIBOSS_2`（ミニボス2）を1体入れる。
  取り巻きを足すなら同じ波に3体まで（`DungeonRoom` の 1..4）。`spawnEncounter` と「全滅で門が開く」はそのまま。
- 新しい地図（#223）の `rooms.json` では、`flow` の `kind: "miniboss"` の部屋が2つある：`MB1`（ミニボス1の間）に `SAKURA_MINIBOSS_1`、
  `MB2`（ミニボス2の間）に `SAKURA_MINIBOSS_2` を、その部屋の `miniboss_spawn` に出す。
- 種類の等級は `QuestMobRarity.MINIBOSS`。`BOSS` ではないので、波に入れられる（`QuestCombatEncounter` の決まり）。
- 今の中身は仮（`仮の花守・一`：ヴィンディケーターの体、`仮の花守・二`：エヴォーカーの体、どちらもスキル3つ）。
  藍が選んだら、同じ名前のまま中身（名前・モデル・スキル）を差し替える。部屋の側は変えない。

## 共通の決まり

| 何 | どうなるか | どこ |
|---|---|---|
| 等級 | いつも 希少（MOD なし、体力×2.1）。乱数で変わらない | `MobGrades.MINIBOSS` |
| 体力 | 桜牙獣の4割ほど（Tier1 で 225 と 200） | `QuestMobContent` の定義 |
| 体力の板 | 生きている間、2本目のボスバー。パックありはピクセルの板で、札が「ミニボス」（幅の広い札 `boss_plate_<等級>_mini`）。パックなしは赤いバーに「ミニボス 名前 HP」 | `CoreMiniBossBar`、`CoreBossBarHud`、`scripts/boss_bar_art.py` |
| 頭上の名前 | ピクセルの板は出さない（ボスバーが出す）。パックなしの名前は「［ミニボス］」 | `QuestEncounterCombat.showHud / updateName` |
| 予兆 | どのスキルも、形に合わせて桜で描く：円は花びらの輪（印の下に桜紋）、帯は根元から先へ桜紋が咲き並ぶ、扇は縁に沿って中から外へ、十字は両方の筋。赤い床の予兆はそのまま。パックありの人だけ（シェーダーの部品）。敵ごとの印で消すので、取り巻きの攻撃では消えない | `SakuraStrikeFx.warn` |
| 当たった時 | 当たった形の上に花びらが散る（桜牙獣と同じ、パックなしでも見える） | `QuestEncounterCombat.sakuraBurst` |
| 倒した時 | 部屋の波としての撃破（`onMobDefeated(boss = false)`）。戦利品は精鋭と同じ種類。ファンファーレ、桜の葉の粒、桜紋が咲く、タイトル「〜を討った／ミニボス撃破」 | `QuestEncounterCombat.miniBossDown` |

## 確かめる

- `/minibosstest 1` か `/minibosstest 2`（開発コマンド）：ボス試験場「花守の試し場」（散り桜の庭の床）に、その部屋のミニボスだけが出る。報酬・保存なし。体力の板も出る。
- テスト：`SakuraMiniBossTest`（定義・波での体力の板と撃破・札の幅）。体力の板の見本は `CoreHudLayoutTest` の書き出し（boss-miniboss）。

## まだ

- 個別の姿（Blockbench）・スキル・動き・桜牙獣の予告になる仕掛けは、2体が決まってから。
- 予兆の桜の部品は、実機ではまだ見ていない。
