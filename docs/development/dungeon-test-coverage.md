# ダンジョン（散り桜の社）の試験の一覧

2026-11-08 夜に作った。ダンジョンの分岐ごとに、どの試験で押さえているかを書く。

- 「前から」は、この一覧を作る前からあった試験。「今回」は、このとき足した試験。
- 試験のファイルは次の5つ。
  - `InstanceDungeonTest`（adventure）：地図と、部屋を順に通す流れ。
  - `DungeonRunBranchesTest`（adventure、今回）：DungeonRun の分岐。
  - `CoreDungeonLedgerTest`（coreloop、今回）：台帳の側（報酬、クリア数、帰還）。
  - `CoreCraftingCurrencyTest`（coreloop）：入場と報酬の基本。
  - `CoreDungeonStageTest`（coreloop）：段階の式と保存形式。
  - そのほか、門の画面は `DungeonGateDialogTest` が押さえる。
- 分岐は 41。押さえたのは 37 で、前からだけが 14、前からに今回足したものが 4、今回はじめて押さえたものが 19。
- まだ押さえていない 4 つは、すべて `CoreLoopServer` の中にある（一番下の表）。`CoreLoopServer` は台帳、画面、接続をまとめて持っていて、試験の中で組み立てられない。本体のコードは変えない決まりなので、今回は台帳の側と DungeonRun の側から押さえた。

## 部屋の進行

| # | 分岐 | 試験 | 前から／今回 |
|---|---|---|---|
| 1 | 入口から部屋を順に進む。門が開くまで、先の部屋にも庭にも歩いて行けない | `InstanceDungeonTest` › the map leads from the entrance through each room into the glade only as each gate opens | 前から |
| 2 | 部屋に入ると、後ろの門が閉まって戦いが始まる（3種類の部屋すべて） | `InstanceDungeonTest` › each kind of room holds its gate shut until it is cleared and walking into the glade wakes 桜牙獣 | 前から |
| 3 | 波の部屋では、波が全滅するまで次の波が来ない。全滅したら次の波が来る | `DungeonRunBranchesTest` › a party member left behind is brought into the room, and one who falls leaves the room to the rest | 今回 |
| 4 | ミニボスの部屋は、ミニボスを倒すと開く | `InstanceDungeonTest` › each kind of room … | 前から |
| 5 | 穢れ場は上限まで湧く。目標の数と最後の1体を倒すと開く | `InstanceDungeonTest` › each kind of room … | 前から |
| 6 | 穢れ場を最後まで飛ばすと ELITE_BRUTE が最後に出て、倒すと開く | `DungeonRunBranchesTest` › a 穢れ場 skipped to its end raises ELITE_BRUTE last, and dispose clears every enemy | 今回 |
| 7 | 飛ばす操作（skipToLast）は、穢れ場の戦い以外では何もしない | `DungeonRunBranchesTest` › skipToLast does nothing outside a 穢れ場's fight | 今回 |
| 8 | 部屋を制圧すると次の門が開き、封印に灯がともる | `InstanceDungeonTest` › each kind of room … | 前から |
| 9 | 部屋を飛ばして庭に入っても、桜牙獣は起きず、庭の門も開かない | `DungeonRunBranchesTest` › the glade does not wake 桜牙獣 before the rooms are cleared | 今回 |
| 10 | 部屋をすべて制圧してから庭に入ると、門が閉まって桜牙獣が起きる | `InstanceDungeonTest` › each kind of room … | 前から |
| 11 | 入口の群れ（3＋人数）は最初の tick で出る | `InstanceDungeonTest` › each kind of room … | 前から |

## クリア数と段階

| # | 分岐 | 試験 | 前から／今回 |
|---|---|---|---|
| 12 | 段階はクリア数＋1で、5で止まる | `CoreDungeonStageTest` › each clear cuts the next run one 段階 harder, up to five | 前から |
| 13 | 段階ごとの敵の体力と攻撃の倍率 | `CoreDungeonStageTest` › each clear cuts … | 前から |
| 14 | 段階2では、波の部屋に1体足し、ミニボスに護衛を付け、穢れ場の数を増やす | `InstanceDungeonTest` › from 段階2 every waves room sends one more, … | 前から |
| 15 | 段階1〜5のすべてで、波は4体まで、護衛は2体まで、穢れ場は1段階ごとに＋3（1〜4人） | `DungeonRunBranchesTest` › the higher 段階 stop growing where they should | 今回 |
| 16 | クリア数は v14 から保存され、v13 のファイルはクリア数0で読める | `CoreDungeonStageTest` › dungeon clears are saved from v14, … | 前から |
| 17 | クリア数は討伐報酬のときだけ増える。死ぬ、落ちる、抜ける、ログアウトでは増えない。5回のクリアで段階5になる | `CoreDungeonLedgerTest` › only a boss reward counts a clear - dying, falling, leaving or logging out do not | 今回 |
| 18 | クリア数は上限で止まり、段階は5のまま | `CoreDungeonLedgerTest` › the clear count stops at its cap and the stage stays 5 | 今回 |
| 19 | ダンジョンの id（arena.bossId）がクリア数の鍵と同じ。作れる Tier は1〜4だけ | `DungeonRunBranchesTest` › the dungeon is keyed by its clears' id and only tiers 1 to 4 are built | 今回 |

## 報酬

| # | 分岐 | 試験 | 前から／今回 |
|---|---|---|---|
| 20 | 入場は無料で、何度でも入れる。知らない id や開いていない Tier は断る | `CoreCraftingCurrencyTest` › the instance dungeon is free repeatable survives a reload and pays exalted and a trial fragment | 前から |
| 21 | 討伐報酬は、高揚1個、試練の欠片1個、討伐証 | `CoreCraftingCurrencyTest` › the instance dungeon is free … | 前から |
| 22 | 討伐証と戦利品券12枚は、その回の Tier で出る。冒険の経験値も入る。地図、地図の Tier、地域の記録は変わらない | `CoreDungeonLedgerTest` › the boss pays once at the run's tier, adds 冒険 xp, and leaves maps, map tier and zone records alone | 今回 |
| 23 | 討伐報酬は二重に出ない（読み直したあとも） | `CoreCraftingCurrencyTest`（同じ回）＋ `CoreDungeonLedgerTest` › the boss pays once …（読み直し） | 前から＋今回 |
| 24 | 地図の保管庫が満杯でも、ダンジョンの討伐報酬は出る | `CoreDungeonLedgerTest` › a full map vault does not hold back the dungeon's boss reward | 今回 |
| 25 | 道中の戦利品は、同じ敵からは二度出ない。ボスの戦利品は、討伐報酬のあとに一度だけ出る | `CoreDungeonLedgerTest` › loot picked up in the dungeon is kept … ／ the boss's loot is paid once after the boss reward | 今回 |
| 26 | 探索イベントの報酬はダンジョンでは出ない。別の回の報酬も出ない | `CoreDungeonLedgerTest` › nothing is paid into a run that has ended or into another run | 今回 |
| 27 | 敵のレベルは冒険 Lv に合わせ、Tier の範囲に収める | `CoreDungeonLedgerTest` › the dungeon's enemy level follows 冒険 Lv inside the tier's band | 今回 |

## 町へ戻る

| # | 分岐 | 試験 | 前から／今回 |
|---|---|---|---|
| 28 | FinishRun で回が終わる。二度は帰れない。帰ったあとは報酬も戦利品も出ない。同じ回の番号では入り直せない | `CoreCraftingCurrencyTest` ＋ `CoreDungeonLedgerTest` › nothing is paid into a run that has ended … | 前から＋今回 |
| 29 | ダンジョンの中では港の操作（入り直す、地図を受け取る）ができない | `CoreCraftingCurrencyTest`（入り直す）＋ `CoreDungeonLedgerTest` › nothing is paid …（地図） | 前から＋今回 |
| 30 | 準備に失敗したとき（AbortRun）は、無料でそのまま戻る。戦利品を得たあとは AbortRun できない | `CoreCraftingCurrencyTest` ＋ `CoreDungeonLedgerTest` › loot picked up … | 前から＋今回 |
| 31 | 回を片付けると（dispose）、その回が出した敵がすべて消える | `DungeonRunBranchesTest` › a 穢れ場 skipped to its end … | 今回 |

## 途中で抜ける・落ちる・死ぬ

| # | 分岐 | 試験 | 前から／今回 |
|---|---|---|---|
| 32 | 門の画面がどの GUI の大きさでも組み立てられる | `DungeonGateDialogTest` › the gate builds at every GUI scale | 前から |
| 33 | ログアウト中に回が残っていても、読み直しと FinishRun で終わる（ログインのときの回復と同じ手順）。クリア数は増えない | `CoreDungeonLedgerTest` › only a boss reward counts a clear …（`reload` のあとの FinishRun） | 今回 |
| 34 | 落ちる：プレイヤーや敵を置く場所はすべて、奈落の線（y -20）より4ブロック以上高い | `DungeonRunBranchesTest` › every place the run puts a player or an enemy stands well above the dungeon's void line | 今回 |
| 35 | ひとりで倒れたとき：部屋は止まる（敵は増えず、門は閉じたまま）。誰もいない間は入口の群れも出ない | `DungeonRunBranchesTest` › with nobody standing the run waits, and the entrance pack rises only once someone is back | 今回 |
| 36 | ひとりで倒れて帰ったとき：拾った戦利品は残り、クリア数は増えない | `CoreDungeonLedgerTest` › loot picked up in the dungeon is kept when the run ends by death, … | 今回 |
| 37 | パーティーのとき：遅れた仲間は部屋の中へ引き込まれる。一人が倒れても、残りの人で部屋を進められる | `DungeonRunBranchesTest` › a party member left behind is brought into the room, … | 今回 |

37 と 3 は DungeonRun の中だけの試験。いまのダンジョンはひとり用で、`CoreLoopServer` は持ち主一人だけを DungeonRun に渡している。

## まだ押さえていない分岐（すべて CoreLoopServer の中）

| # | 分岐 | 場所 |
|---|---|---|
| 38 | クリア数から段階を読み、敵の倍率と DungeonRun に渡す | `CoreLoopServer.attachTrial` |
| 39 | ボスを倒したあと、討伐報酬の保存を5秒ごとに再試行する。保存できたら一定時間あとに港へ自動で戻す | `CoreLoopServer.tickSession` / `awardBoss` |
| 40 | `/return`・羅針盤・帰還の画面から港へ戻る（戦利品を集める → 報酬を流す → FinishRun → 港へ移す → 闘技場を片付ける） | `CoreLoopServer.returnToHarbor` |
| 41 | 本当に落ちたとき（y < -20）と、本当に倒れたとき（「力尽きた…」）に港へ戻す。ログアウトのときに闘技場を片付ける | `CoreLoopServer` の移動の処理、`tickSession`、`disconnect` |

