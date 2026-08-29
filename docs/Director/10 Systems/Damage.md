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
ui_dependency: green
content_requirement: yellow
testing: green
user_decision_needed: false
last_reviewed: 2026-08-29
---

# Damage

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 取り込み済み仕様候補

- Damage lineageとElementは別軸。
- Lineage: `PHYSICAL / MAGICAL`。既存coreにTRUEはlegacy parityとして残るが拡張しない。
- Element: `FIRE / ICE / LIGHTNING`。
- Fire swordはPhysical defense、Fire magicはMagic defenseを見る。
- Attackは複数tagを持てる。MOD applicabilityは表示名ではなくtag。
- Crit、defense、penetration、reduction、shield、lifestealを明示順序で解決。
- Server authoritative。

## 実装済み

- Pure Kotlin `DamageCalculator` / `StatCalculator`。
- Physical/Magical/True、Normal/Direct/DoT等のkind。
- PvE/PvP mode、defense cap、crit、shield、lifesteal。
- Equipment/MOD pure coreとは別module内で存在。
- deterministic tests。

## 実装中

- Equipment base stat + MOD contributionをresolved Combat snapshotへ変換。
- 実attack executionからDamage Coreを呼ぶboundary。
- Element stateとの決定的な処理順。

## 未決定 / 要整合

- Current coreのPvE base crit multiplierは1.75、Design Bank targetは1.50。現行Contractで再決定が必要。
- Current coreにはflat penetration/TRUEがあるが、Design Bank canonical Betaはそれらを主軸にしない。
- Player-facing stat名と最終tooltip表示。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟢 | 計算責務とLineage/Element分離は明確 |
| Backend complexity | 🟡 | Core自体よりruntime adapterが未完 |
| Minecraft limitation | 🟢 | Pure domainでplatform依存が小さい |
| UI dependency | 🟢 | 新UIなしでruntime完成可能 |
| Content requirement | 🟡 | 実装備/敵defense fixturesが必要 |
| Testing | 🟢 | DamageCalculator testsがmerge済み |

## User Decision Needed

今はなし。#127では既存#94 orderingを保存する。Crit/penetration整合は別Current Contractで決める。

## Sources

[Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127) · [Design Bank #112](https://github.com/retardedgyai/Projects-V2/issues/112) · `server-minestom/.../combat/damage/`
