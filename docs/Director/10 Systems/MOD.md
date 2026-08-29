---
type: system
design_status: NEEDS_REVIEW
implementation_status: IMPLEMENTING
approved_by:
approved_at:
priority: NOW
design_clarity: green
backend_complexity: yellow
minecraft_limitation: green
ui_dependency: yellow
content_requirement: yellow
testing: green
user_decision_needed: false
last_reviewed: 2026-08-29
---

# MOD

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 目的

装備へ付ける小さな変更で、同じ武器でもstatだけでなくCombat behaviorを変える。

## 取り込み済み仕様候補

- data-driven definition。
- stable ID、Rank 1〜3、allowed slots。
- required/excluded AttackTag。
- finite roll range、definition revision。
- Stacking layers: Base Flat / Base Percent / Increased / Conditional / Final。
- static numeric modifierとreactive/element/proc behaviorは分離可能にする。
- display weapon nameではなくattack tagsでapplicabilityを判定。
- unknown/invalid MODは安全にreject/preserveし、別statへ読み替えない。
- Generic scripting/effect engineを先に作らない。

## 実装済み

- `ModDefinition` / `ModEntry` / validation。
- RankとEquipment Tier整合。
- slot/tag/revision/rolled value validation。
- EquipmentStatContribution。
- tests。

## 実装中（#127）

- Keen Edge等のnumeric offensive MOD。
- Ember/Frost typed effect。
- Equipment snapshotからCombatへ解決。
- valid hitでFire/Ice contributionを1回だけ適用。

## 未決定

- Betaの正式MOD poolとdrop source。
- MODの装着/取り外しcost。
- 同一MOD重複、上限、rare behavior。
- reactive MODの最小typed modelをどこまで一般化するか。
- Crafting/Marketとの取引単位。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟢 | Core contractと#127の最小effectは明確 |
| Backend complexity | 🟡 | static/reactive resolution順とattack snapshot |
| Minecraft limitation | 🟢 | pure domain中心 |
| UI dependency | 🟡 | #127はUIなし。後でtooltip/compareが必要 |
| Content requirement | 🟡 | prototype数件のみ。正式poolは未設計 |
| Testing | 🟢 | foundation + #127 branch tests |

## User Decision Needed

今はなし。#127ではEmber/Frostと少数numeric MODだけ。正式poolはCraft/Economy sliceで決める。

## Related

[[Director/10 Systems/Equipment|Equipment]] · [[Director/10 Systems/Elements|Elements]] · [[Director/10 Systems/Crafting|Crafting]] · [Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127)
