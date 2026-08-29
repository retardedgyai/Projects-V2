---
type: current-view
lane: NOW
last_reviewed: 2026-08-29
---

# NOW

> [!important] WIP limit: 1 quality feature
> Product/Gameplay/UIのediting laneは原則1本。現在はIssue #127。

## Combat Build Slice v0 — Equipment MODs + Fire/Ice runtime

- Status: `IMPLEMENTING`
- Issue: [#127](https://github.com/retardedgyai/Projects-V2/issues/127)
- Branch: `combat/element-mod-build-slice-v0`
- 実装の正確な現在地はGitHub branchとtestsで確認する。

### Finished Experience

`Ember Twin Bladesを装備 → Hit → Burn蓄積 → Detonate`

`Frost Twin Bladesへ変更 → Cold蓄積 → Frozen → 次HitでShatter`

同じ武器でもMOD/Elementにより戦闘結果が明確に変わる。

### 今回つなぐもの

- merge済みDamage Core
- merge済みEquipment/MOD Core
- merge済みItemStack presentation bridge
- server-authoritative Combat hit
- Fire/Ice target state
- Rift Executioner / concrete test target

### 今回やらないもの

- 新UI、Tooltip、Icon、Inventory redesign
- Lightning gameplay
- Generic status/stat/effect framework
- Crafting、完成Loot/Economy、DB

### Completion

`real EquipmentItem/MOD → combat resolution → Fire/Ice state → visible consequence`

がMinecraft内で説明なしに確認できること。
