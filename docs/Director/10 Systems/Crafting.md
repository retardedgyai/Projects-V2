---
type: system
design_status: NEEDS_REVIEW
implementation_status: NOT_PLANNED
approved_by:
approved_at:
priority: NEXT
design_clarity: red
backend_complexity: red
minecraft_limitation: yellow
ui_dependency: yellow
content_requirement: red
testing: unknown
user_decision_needed: true
last_reviewed: 2026-08-29
---

# Crafting

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 方向として残っているもの

- Boss素材からCraft/強化して装備を更新するCore Loop。
- 完成装備の主供給をPlayer crafterに寄せるDesign Bank。
- Gathererがbase material、Combat playerがmonster/MOD/enhance/repair materialを供給する役割分担。
- NPC/starter gearはbound safety net候補。
- Crafted gearへcrafter identity/reputationを残す案。
- 価値あるmutationは `validate → reserve → consume → produce → persist → commit`。

これらはIssue #112のDesign Bank。具体sliceで現行v2へ再調整するまで一括FIXEDではない。

## 実装状況

- Crafting runtimeなし。
- Recipe、material、transaction、inventory-full failure、idempotency未実装。
- Equipment/MOD domainは土台として利用可能。

## 最初にFIXすべき最小Contract

`Rift Executioner撃破 → 個人素材1つ → レシピ1つ → 既存Twin Bladesへ1つの装備更新 → 保存 → 再挑戦で差を確認`

完成Crafting frameworkやMarketを先に作らない。

## 未決定

- 最初の素材、レシピ、出力。
- Base gearを作るか、既存装備を強化するか、MODを作るか。
- 確定結果/roll/失敗のどれをv0に含めるか。
- Craft station/UI。
- Material消費とinventory full/reconnectのtransaction rule。
- Crafter identity、quality、masteryをいつ入れるか。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🔴 | Economy方向はあるが最初のfinished experience未固定 |
| Backend complexity | 🔴 | inventory/persistence/idempotent transactionが必要 |
| Minecraft limitation | 🟡 | station/menu/ItemStack interaction |
| UI dependency | 🟡 | 最小Vanilla UIで開始可能 |
| Content requirement | 🔴 | material/recipe/outputが未定 |
| Testing | ⚪ | runtime未実装 |

## User Decision Needed

Issue #127後、最初のCraft sliceを選ぶ時に以下を1つ決める。

1. Boss素材→Ember/Frost MOD craft
2. Boss素材→Twin Blades base upgrade
3. Boss素材→新しい1装備

## Related

[[Director/10 Systems/Equipment|Equipment]] · [[Director/10 Systems/MOD|MOD]] · [[Director/10 Systems/Economy|Economy]] · [[Director/20 Content/First Hunt and Harbor|First Hunt]] · [Design Bank #112](https://github.com/retardedgyai/Projects-V2/issues/112)
