---
type: system-map
design_status: NEEDS_REVIEW
implementation_status: IMPLEMENTING
approved_by:
approved_at:
priority: NOW
last_reviewed: 2026-08-29
---

# Elements

> [!warning] Director Review待ち
> これはElements全体の地図。正式仕様は各Elementページを個別に確認してFIXEDにする。

## 頭の中へ戻すための要約

ElementはDamageの色替えではなく、同じ武器やHit loopでもPlayerの狙いと戦い方を変えるBuild layer。

## 絶対に守りたい原則候補

- ElementとPhysical/Magical lineageは別軸。
- Elementごとに、Playerが狙う瞬間と結果が異なる。
- 新しいElementを追加するためだけに先に巨大な汎用Status Engineを作らない。
- Playerが説明を読まなくても、feedbackから状態変化を理解できる。

## Individual Specifications

| Spec | Design | Implementation | 一言 |
| --- | --- | --- | --- |
| [[Director/10 Systems/Elements/Fire|Fire]] | NEEDS_REVIEW | IMPLEMENTING | 蓄積して爆発を狙う |
| [[Director/10 Systems/Elements/Ice|Ice]] | NEEDS_REVIEW | IMPLEMENTING | 凍結を作り、次Hitで砕く |
| [[Director/10 Systems/Elements/Lightning|Lightning]] | IDEA | NOT_PLANNED | Gameplay identity未決定 |

## 共通仕様として未決定

- Boss/EliteごとのElement resistanceを持たせるか。
- 装備、武器、MODのどこがElement contributionを所有するか。
- Feedbackの最低要件を共通化するか。

## Implementation Evidence

- Delivery Contract: [Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127)
- mainにはElementのAttackTagがある。
- Fire/Ice runtimeとEquipment/MOD接続は作業branchで実装中。

## Related

[[Director/10 Systems/Combat|Combat]] · [[Director/10 Systems/Damage|Damage]] · [[Director/10 Systems/Equipment|Equipment]] · [[Director/10 Systems/MOD|MOD]]
