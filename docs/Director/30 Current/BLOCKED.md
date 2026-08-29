---
type: current-view
lane: BLOCKED
last_reviewed: 2026-08-29
---

# BLOCKED

## Current Pain Map

| Area | Health | 何がしんどいか | 解除条件 |
| --- | --- | --- | --- |
| Equipment → Combat | 🟡 | Pure domainはあるが実戦値へ未接続 | #127でsnapshot/resolverを接続 |
| Elements | 🟡 | Fire/Ice Contract候補はあるがDirector Reviewとruntime/feedbackが未完 | [[Director/10 Systems/Elements/Fire|Fire]] / [[Director/10 Systems/Elements/Ice|Ice]]のReview + #127 Smoke |
| Twin Blades ownership | 🔴 | offhand copyが独立ItemStackならdupe risk | #122の1 item / 2 visuals invariantを実装 |
| Full Hunt Loop | 🔴 | Boss prototypeはあるが港町→Hunt→報酬→帰還が未接続 | 最小HuntSession thin slice |
| Crafting | 🔴 | 経済の方向はあるが最初のレシピ/取引Contractがない | 1素材→1更新のCurrent Contract |
| Economy | 🟡 | Player-crafted中心案と大規模経済後回しはある | Betaのsource/sinkだけ固定 |
| Progression balance | 🟡 | Lv45/保存はあるがXPカーブはprototype | 実Playtime targetに合わせる |
| Boss content | 🟡 | Rift Executionerは実装済み、正式Map/報酬/死亡規則がない | First Hunt contract |

## 分類

- Design: 体験、数値、境界が未確定
- Backend: data/lifecycle/runtime接続が難しい
- Minecraft: Vanilla/Minestom/Fabric制約
- UI: 見せ方、入力、理解可能性
- Content: Boss、Map、素材、演出の制作量
- Testing: 再現、負荷、Manual Smoke
- Dependency: 別Issue/素材/判断待ち

「難しい」だけで終わらせず、理由と解除条件を必ず書く。
