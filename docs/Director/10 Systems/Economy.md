---
type: system
design_status: NEEDS_REVIEW
implementation_status: NOT_PLANNED
approved_by:
approved_at:
priority: LATER
design_clarity: yellow
backend_complexity: red
minecraft_limitation: yellow
ui_dependency: yellow
content_requirement: red
testing: unknown
user_decision_needed: true
last_reviewed: 2026-08-29
---

# Economy

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## Product方向

ProjectSはCombat/Farming/Gathering/Crafting/Tradingを別々の孤立したminigameにせず、素材・装備供給・生活圏で接続する「共存型MMO」を目指すDesign Bankがある。

通常Mob/Bossの完成装備direct dropを主軸にせず:

`Gatherer → base material`

`Combat player → monster / MOD / enhance / repair material`

`Crafter → equipment base`

`Player → MOD / enhance / repairで仕上げ`

`Market → roleを接続`

という方向。

## 現在の正式Scope

- 個人Lootを初期方針候補としている。
- Boss素材→Craft/強化→次のHuntはCore Loop。
- Marketplace/大規模Economyは初期Scope外。
- したがって最初に必要なのはMarketではなく、1 Hunt内のsource/sink。

## 実装状況

- Economy runtimeなし。
- Currency/market/trade/gathering/crafting未実装。
- Equipment/MOD/Progressionは将来のsink/価値先として存在。

## 未決定

- Betaで使う通貨数。
- Item/materialのsource/sink表。
- Player trade/marketをBetaへ入れるか。
- Bind/repair/durability/tax/fee。
- Combat-only playerがCraft/Gatheringをしなくても成立する入手経路。
- inflation/dupe/idempotency対策。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | 長期方向はあるがBeta最小経済が未固定 |
| Backend complexity | 🔴 | transaction/persistence/dupe/marketは重い |
| Minecraft limitation | 🟡 | Inventoryとtrade UI |
| UI dependency | 🟡 | 最初はVanilla menuで可能 |
| Content requirement | 🔴 | material/recipe/reward tableが必要 |
| Testing | ⚪ | runtimeなし |

## User Decision Needed

Crafting slice前に、Beta最小loopで使うmaterial/currency/source/sinkだけ決める。Market全体は決めない。

## Related

[[Director/10 Systems/Crafting|Crafting]] · [[Director/10 Systems/Equipment|Equipment]] · [[Director/20 Content/First Hunt and Harbor|First Hunt]] · [Design Bank #112](https://github.com/retardedgyai/Projects-V2/issues/112)
