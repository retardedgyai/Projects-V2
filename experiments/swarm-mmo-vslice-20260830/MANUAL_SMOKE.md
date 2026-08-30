# Tidebreak Anchorage — 最短Manual Smoke

Minecraft GUI操作と最終feel判定はCreatorが行う。FabricとResource Packは使わず、Vanilla 26.2で実施する。

## 準備

1. integration worktreeでinstall distributionを作る。
2. `scripts/swarm-startup-check.ps1`でready log、TCP listen、process aliveを確認する。
3. 本番smoke用Serverを起動し、Vanilla 26.2から接続する。Resource Pack提示があれば拒否する。
4. 新規Player profileを使い、開始時刻を記録する。Server commandや管理者による進行補助は禁止。

Startup probe例:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\swarm-startup-check.ps1 `
  -TargetWorktree "C:\path\to\integration-worktree" `
  -Launcher "gradlew.bat" `
  -LauncherArguments "--no-daemon",":server-minestom:run" `
  -Port 25565
```

## 1. 接続と入力matrix

Tidehookを固定hotbar slotから動かさず、各条件を20回試す。

| 入力 | 条件 | PASS |
|---|---|---|
| Thrust | 正面3.5 block以内の登録敵を左click | 1 inputにつきserver hitが最大1回。Vanilla damageは出ない |
| Thrust | 範囲外、背面、遮蔽物越し、cooldown中 | damageなし。必要時は短い拒否理由が出る |
| Brace | 空中を右click | Windup → active → recoveryが1回だけ始まる。Tridentを投げない |
| Brace | Blockを見て右click | 空中と同じ結果。Block固有動作を起こさない |
| Brace | 浅瀬で右click | 空中と同じ結果。水流・pull・水中戦闘はない |
| Brace | offhand使用を混ぜる | main-hand intentだけを受理し、二重発火しない |
| Drop | Tidehookのdropを試す | managed Tidehookは失われない |

Braceは各条件でmissing、duplicate、default-useの合計が20回中1回以下ならPASS。2回以上は即`FIX-FIRST`。

## 2. 通常敵がBossを教えるか

1. Lookoutの説明を読む。Chatは各stage 2行以内で、次の固有名詞付き目的地が残ること。
2. Brineclawを3体倒す。
3. 3体目までに、Sweepはarc外へ離れる、ChargeはsidestepまたはBrace、practice post衝突でexposeする、と外部説明なしで識別する。

新しい攻撃語彙、色だけのtell、原因を説明できないdamage、SidebarとActionBarの競合があれば`FIX-FIRST`。

## 3. Fresh solo complete journey

次を順に行う。30秒以上「次に何をするか」が分からなくなった時点と場所を記録する。

1. Wardenと話し、Hunter / Gatherer / Supplierの1 routeを選ぶ。開始Scripは12。
2. 選択routeの目的を完了する。不足するOre/CordはBroker-Smithの固定交換で揃える。
3. Ore 2 + Cord 2 + Scrip 4をMarket rackで一度だけ支払い、Signal Couplerをcraft/installする。連打して二重消費しないこと。
4. LookoutからBreakwaterへ入る。
5. CairnbackのSweepとChargeだけを読み、Chargeをpowered crash pillarへ誘導してexposed window中に攻撃する。
6. 故意に一度wipeする。準備をやり直さず15–30秒で再戦し、古いBoss/action/rosterが残らないこと。
7. 撃破時にroster内、arena内、Tidehook hit 3回以上であることを確認する。Pressure Pearl stateは一度だけ得る。
8. Broker-SmithでBarbed PointまたはGuard Ringを一度だけ選ぶ。追加素材なし、再選択なし。
9. Wardenへ報告し、`COMPLETE`相当の表示を確認する。

Fresh solo全体15–30分、Boss 4–6分が目標。Fabric、Resource Pack、ground currency/resource drop、未定義commandが必要ならFAIL。

## 4. Reconnect / restart

1. Boss前またはBoss後で一度切断し、再接続する。現在objective、Scrip、Ore、Cord、route、fitting、stageが復元されること。
2. Serverを正常停止・再起動し、もう一度接続する。同じprofileが復元され、初期12 Scrip、commission、Coupler、Boss reward、MOD、completionが再支給・再消費されないこと。
3. 2人目の新規Playerを接続し、1人目のCoupler/Boss進行で2人目のstageが進まないこと。

## Verdict

次のどれか1つでもあれば`FIX-FIRST`:

- Input matrix違反、Fabric必須、またはTrident default use。
- 30秒以上の説明不能なobjective停止。
- wipe後のstale action/entity/roster、またはretry 30秒超。
- 二重消費、二重reward、他Playerによる個人進行。
- reconnect/restartでprofileを失う。
- 通常敵にないBoss mechanic、または色だけでしか読めないtell。

最後に報酬を無視して答える:

> 今すぐ同じBossをもう一度戦いたいか？

`No`なら自動Testが通ってもPlayground verdictは`FIX-FIRST`または`DROP`。
