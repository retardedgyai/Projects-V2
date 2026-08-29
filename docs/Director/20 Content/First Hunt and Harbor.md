---
type: content
design_status: NEEDS_REVIEW
implementation_status: NOT_PLANNED
approved_by:
approved_at:
priority: NEXT
design_clarity: yellow
backend_complexity: red
minecraft_limitation: yellow
ui_dependency: yellow
content_requirement: red
testing: unknown
user_decision_needed: true
last_reviewed: 2026-08-29
---

# First Hunt and Harbor

> [!warning] Director Review待ち
> 既存資料をAIが整理した候補。君が全文を確認するまでFIXED仕様ではない。

## 取り込み済み仕様候補

- 共有港町をSocial Hubにする。
- 1〜4人Partyで専用Hunt空間へ出発。
- 個人Loot。
- Huntは一時runtime、装備/素材/進行は永続。
- `create → map load → party transfer → combat → reward → return → cleanup → instance destroy`。
- 初期は1 process。Redis/Microservice/Orchestratorなし。
- Harborは使い捨てstarter villageではなく長く帰るhome cityというDesign Bank。
- first visual validationはMinecraft graybox/player routing。

## First HarborのDesign Bank

- bright / dense / messy ocean frontier harbor。
- small dense > giant decorative。
- Harbor / Warehouse-Bank / Market / Workshop / Guild Hall-Tavern / Expedition-Training。
- 3 elevation bands。
- key facilitiesは約15〜30秒。
- South harbor → dry coast/farm → river greenland → deep forest → limestone mountain/cave → clouded highlands。

この内容は現行First Hunt sliceで再調整してからFIXする。

## 実装状況

- `HuntSession` production runtimeはまだない。
- Serverに1 instance上のRift Executioner prototypeはある。
- Progression/Persistenceはある。
- Quest/Party/Map template/Reward transaction/Return flowは未実装。

## 未決定

- First Huntの所要時間、Map、探索量、雑魚/採取の有無。
- Quest受注UI/入口。
- Party join/leave/leader rule。
- 死亡、蘇生、wipe、timeout、途中退出、reconnect。
- 報酬claimとinventory full。
- Harborの最初に作る範囲と施設。
- First Hunt完了後のCraft/強化先。

## 最小次Slice

`仮Harbor spawn → 1 Quest start → fresh instance → Rift Executioner → reward token → Harbor return → cleanup`

Art/Market/Craftingを同時に完成させず、Hunt lifecycleを最初に一本化する候補。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | lifecycle候補はあるが、player rule/rewardとDirector承認が未完 |
| Backend complexity | 🔴 | instance/party/reward/cleanup/reconnect |
| Minecraft limitation | 🟡 | map template/transfer/return |
| UI dependency | 🟡 | 最初はsimple interactionで可能 |
| Content requirement | 🔴 | Harbor graybox、Map、Quest、reward必要 |
| Testing | ⚪ | production Hunt runtimeなし |

## User Decision Needed

- 死亡/復活/失敗/途中退出の最小ルール。
- First Huntで雑魚/採取を入れるか、Boss直行にするか。
- Rift ExecutionerをそのHuntの正式Bossにするか。

## Sources

[[00-product-vision|Product Vision]] · [[architecture/hunt-session-lifecycle|Hunt Session Lifecycle]] · [Design Bank #112](https://github.com/retardedgyai/Projects-V2/issues/112)
