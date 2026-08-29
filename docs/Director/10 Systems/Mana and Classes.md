---
type: system
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
approved_by:
approved_at:
priority: NEXT
design_clarity: yellow
backend_complexity: yellow
minecraft_limitation: yellow
ui_dependency: green
content_requirement: red
testing: green
user_decision_needed: true
last_reviewed: 2026-08-29
---

# Mana and Classes

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 取り込み済み仕様候補

- 全Playerが共通Manaを持つ。
- Skill 1〜3は原則Mana消費。通常攻撃/Dodgeは消費しない。
- 戦闘中も自然回復、戦闘外は高速回復。
- Cooldownは同じ技の頻度、Manaは長期Skill燃料。
- ClassによりMana重要度を大きく変えてよい。
- 固有ゲージはclassらしく上手く戦った報酬。Manaとの二重課税にしない。
- 固有ゲージの増やし方はclass identity、変換先はBuildで変化可能。
- 最初から汎用Class/Gauge frameworkを作らない。
- Weaponはnormal attack/range/tempo/weight、ClassはSkill 1〜3/Ult/Mana/Gaugeを担当。

## 現在のPrototype

- Twin Blades(Twin Rods internal)が第一class/weapon experienceの役割を持つ。
- Normal attack、aerial hit loop、Air Jump、Air Dodge。
- Skill 1〜3、Mana、Cooldown、HUD、Boss combat。
- `ClassResourceState`とprotocol snapshot/tests。

## 未決定

- 第一Classの正式名称/identity。
- Twin BladesをWeaponとClassのどちらに置くかの最終整理。
- Skill 1〜3の最終Player-facing名称/数値。
- Ultの獲得/消費/内容。
- 第一Class固有ゲージの増加条件/使い道。
- 3 classes × 3 weapons案の正式採用。
- Classごとの武器制限。
- Mana buildをEquipment/MODへどうつなぐか。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | 共通原則候補はあるが、第一Class identity/Ult/GaugeとDirector承認が未完 |
| Backend complexity | 🟡 | prototype stateはある。正式class boundaryは未抽出 |
| Minecraft limitation | 🟡 | input/aerial movement/first-person feedback |
| UI dependency | 🟢 | Mana/Cooldown HUDあり |
| Content requirement | 🔴 | 1 prototype以外のclass/skill contentなし |
| Testing | 🟢 | skill/resource/aerial testsあり |

## User Decision Needed

Issue #127後、第一Classを正式に完成させるlaneへ戻る時に、identity / Ult / 最小GaugeをFIXする。

## Related

[[game/mana-and-class-resources|Canonical Mana Design]] · [[game/twin-rods-aerial-combat|Aerial Contract]] · [[Director/10 Systems/Combat|Combat]] · [[Director/10 Systems/Skill Tree|Skill Tree]]
