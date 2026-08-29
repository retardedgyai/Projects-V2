---
type: system
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
approved_by:
approved_at:
priority: NEXT
design_clarity: yellow
backend_complexity: green
minecraft_limitation: yellow
ui_dependency: green
content_requirement: red
testing: green
user_decision_needed: true
last_reviewed: 2026-08-29
---

# Skill Tree

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 取り込み済み仕様候補

- Character LevelのPassive PointはGlobal Passive Treeへ使う方向。
- Global Level/Passive PointとTwin Blades固有specializationを同一currency/systemとして雑に混ぜない。
- Passive TreeはUI Art Direction v1へ合わせる。
- Serverがnode取得、prerequisite、point、revisionを判定。

## 実装済み v0

6 nodes / 3 branches:

- Force → Overpower: direct damage +15%ずつ
- Tempo → Flow: normal attack speed +15%、cooldown recovery +20%
- Vitality → Guard: max HP +4、PvE incoming damage -10%

さらに:

- prerequisite/cost/spent/granted/revision validation。
- Server effects→Combat/HP/Cooldown。
- Client Tree layout/screen。
- persistence/protocol/tests。

## 未決定

- PoE風Global Treeの最終規模/形。
- Keystoneの意味と数。
- Respec cost/rule。
- Nodeごとの最終balance。
- Global TreeとClass/Weapon specializationの画面/通貨/進行分離。
- Lv45までのnode density。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | Global方針はあるがfinal topology未固定 |
| Backend complexity | 🟢 | 6-node state/effects/revisionは完成 |
| Minecraft limitation | 🟡 | 大規模Treeのnavigation/可読性は未検証 |
| UI dependency | 🟢 | v0 Screenあり、Art Direction承認済み |
| Content requirement | 🔴 | 6 prototype nodes以外未設計 |
| Testing | 🟢 | state/protocol/layout testsあり |

## User Decision Needed

- Global Passive TreeとTwin Blades specializationを、Playerがどう見分けるか。
- 次にnodeを増やす前に、6-node v0がCombatで意味を感じるかPlaytestする。

## Related

[[Director/10 Systems/Progression|Progression]] · [[Director/10 Systems/Mana and Classes|Mana & Classes]] · [UI Art Direction #114](https://github.com/retardedgyai/Projects-V2/issues/114)
