---
type: research
design_status: NEEDS_REVIEW
implementation_status: NOT_PLANNED
approved_by:
approved_at:
priority: LATER
design_clarity: yellow
backend_complexity: red
minecraft_limitation: red
ui_dependency: yellow
content_requirement: red
testing: unknown
user_decision_needed: false
last_reviewed: 2026-08-29
---

# Boss Asset Pipeline

> [!warning] Director Review待ち
> 既存資料をAIが整理した候補。君が全文を確認するまでFIXED仕様ではない。

## 状態

**未採用・研究継続。** First playable Bossの必須条件ではない。

## 候補

`Meshy / Tripo / Sloyd → GLB → ProjectS Asset Compiler → Client mesh/animation + Server simplified hit data`

## 固定したい原則

- MeshそのものをHitboxにしない。Sphere/Box/Capsuleをbonesへ追従。
- Serverが本体位置/向きを決め、Client animationはpresentation。
- root motionをauthorityにしない。
- Skill release timing/origin boneを別定義。
- 騎士/馬/武器等は部品分割を優先。
- Human-facing generic Model/Animation Editorを先に作らない。

## 採用Gate

- Low-poly Boss生成、Rig、Idle/Walk/Attack、GLB出力。
- Fabric描画とMinestom animation sync。
- Server debug hitと見た目が一致。
- Playerが攻撃軌跡を避けられる。
- hand/mouthからProjectile/VFX。
- 特殊体型/長いtailでも成立。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟡 | Pipeline候補とGateはあるが正式採用前 |
| Backend complexity | 🔴 | compiler、bone data、sync、hit exportが必要 |
| Minecraft limitation | 🔴 | Fabric custom mesh/animationとfirst-person可読性 |
| UI dependency | 🟡 | Debug hit/animation確認手段が必要 |
| Content requirement | 🔴 | 複数体型の実assetで検証が必要 |
| Testing | ⚪ | 技術試験未実施 |

## User Decision Needed

今はなし。Vanilla/Player NPCでFirst Huntを完成させた後、正式production artが必要になった時に試験する。

## Source

[[research/ai-boss-model-pipeline|AI Boss Model Pipeline Research]]
