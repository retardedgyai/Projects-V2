---
type: system
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
approved_by:
approved_at:
priority: NEXT
design_clarity: yellow
backend_complexity: green
minecraft_limitation: green
ui_dependency: green
content_requirement: red
testing: green
user_decision_needed: true
last_reviewed: 2026-08-29
---

# Progression

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 取り込み済み仕様候補

- Character Level capは45。
- Level up自体でHP/Attackを自動増加させない。
- Level upでGlobal Passive Pointを得る。
- `available = granted - spent`。unspent単独を唯一のsourceにしない。
- XPはMobだけでなくQuest/地域目標/Boss/探索等から得る方向。
- Lv45だけでEndgame unlockせず、First Island Final Bossも必要というDesign Bank。
- Cooldown/buff/open UI等のtransient stateを保存しない。

## 実装済み v0

- Server-owned `ProgressionState`。
- Lv1〜45、XP remainder、1 levelにつき1 Passive Point。
- Current prototype XP formula: `100 + (level - 1) × 50`。
- revision付きspend request、stale revision reject。
- file persistence。
- Protocol snapshot/XP gained/node spend。
- CombatへDamage/Attack Speed/Cooldown/HP/Defense効果を接続。
- Inventory/HUDとPassive Tree UI。

## 仮実装 / 未検証

- XP formulaは実装用prototype。Design BankのLv45約25時間targetへ未調整。
- XP sourceは現在のBoss/test loop中心で、正式Journeyへ未接続。
- low-level farm XP decay未実装。
- final boss kill gate未実装。

## 未決定

- XP curveと各level milestone。
- Quest/探索/Boss/採取のXP配分。
- 死亡やHunt失敗時のXP扱い。
- Class選択/Profession/Quest state等をProgression aggregateへいつ追加するか。
- Respec policy。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | Cap/Pointは明確、curve/source/respec未固定 |
| Backend complexity | 🟢 | state/revision/persistence v0がmerge済み |
| Minecraft limitation | 🟢 | Protocol/UI接続済み |
| UI dependency | 🟢 | HUD/Tree screenあり |
| Content requirement | 🔴 | 正式XP sourceとjourneyがない |
| Testing | 🟢 | state/persistence/protocol/layout testsあり |

## User Decision Needed

First Huntが一本化された後、実プレイ時間を見てXP curve/sourceを決める。今数値だけを先に詰めない。

## Related

[[Director/10 Systems/Skill Tree|Skill Tree]] · [[Director/01 Vision/Player Journey|Player Journey]] · [Design Bank #112](https://github.com/retardedgyai/Projects-V2/issues/112)
