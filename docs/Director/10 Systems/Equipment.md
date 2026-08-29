---
type: system
design_status: NEEDS_REVIEW
implementation_status: IMPLEMENTING
approved_by:
approved_at:
priority: NOW
design_clarity: yellow
backend_complexity: yellow
minecraft_limitation: yellow
ui_dependency: yellow
content_requirement: yellow
testing: green
user_decision_needed: true
last_reviewed: 2026-08-29
---

# Equipment

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 取り込み済み仕様候補 / merge済みEvidence

- 8 slots: Weapon / Head / Chest / Legs / Boots / Necklace / Ring 1 / Ring 2。
- Category: Weapon / Armor / Accessory。
- Tier: T1 Lv1〜15 / T2 Lv16〜30 / T3 Lv31〜45。
- Rarityは直接Power tierではなくMOD capacity: Common 1 / Uncommon 2 / Rare 3 / Epic 4。
- Base stat rollとMOD slotを持つ。
- stable namespaced IDs。
- Definition / Instance / Combat Snapshotを分離する方向。
- Lore/displayはauthorityにしない。

## 実装済み

- Pure Kotlin `EquipmentItem` validation。
- Slot/category/tier/itemLevel/rarity/base roll/MOD slot。
- Equipment→ItemStack presentation bridge。
- Inventory Character Screen上のauthoritative stats表示。
- malformed/unknown presentation fallback tests。

## 実装中

- Equipment/MODからCombat用resolved snapshotを生成。
- Ember/Frost Twin Blades prototype gear。
- in-progress attack時のsnapshot固定。

## 既知のSystem Bug

Twin Bladesのsecondary/offhandが独立ItemStackならdupe可能。

固定invariant:

`1 authoritative owned Twin Blades item → 2 rendered blade visuals → transferable stackは1つ`

Issue: [#122](https://github.com/retardedgyai/Projects-V2/issues/122)

## 未決定

- Qualityの意味。現行codeは`UNSPECIFIED`のみ。
- Enhancement 0〜30 / broken / repair / Tier promotionをv2でどう採用するか。
- Bind/trade/drop/death/reconnect policy。
- Light/Medium/Heavy armor混在案の正式採用。
- Lootで完成品をどこまで出すか。
- Instance UUIDをいつ付与するか。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | Core fieldsは明確、ownership/quality/enhance未確定 |
| Backend complexity | 🟡 | snapshot・保存・ownershipの境界が重要 |
| Minecraft limitation | 🟡 | ItemStack/hand/offhandとdual weapon表現 |
| UI dependency | 🟡 | Inventoryはある。Tooltip/compareはHOLD |
| Content requirement | 🟡 | 実装備poolとdrop/craft sourceがない |
| Testing | 🟢 | foundation/presentation testsあり |

## User Decision Needed

- #122着手時、secondary bladeをどの見せ方で許容するか。1 owned item / 2 visualsは既存Issueからの要件候補。
- Crafting slice前に、最初の装備更新がbase gear / MOD / enhanceのどれかを決める。

## Related

[[Director/10 Systems/MOD|MOD]] · [[Director/10 Systems/Crafting|Crafting]] · [[Director/10 Systems/Economy|Economy]] · [Issue #122](https://github.com/retardedgyai/Projects-V2/issues/122)
