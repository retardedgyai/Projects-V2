# Loot + MOD Drop v0

## Decisions

- Normal rewards use T1, item level 5, and rarity weights Common 60 / Uncommon 30 / Rare 9 / Epic 1.
- Rift Executioner rewards use T2, item level 20, and rarity weights Common 15 / Uncommon 35 / Rare 35 / Epic 15.
- Base attack rolls are 8.0..16.0 for normal rewards and 14.0..28.0 for boss rewards.
- Each generated item fills every rarity MOD slot. The pool has eight weapon MODs covering flat damage, crit, skill, and conditional build directions.
- A generated MOD is selected without replacement and its value is rolled inclusively within its definition range.
- The generator uses `java.util.Random(seed)` so fixtures and tests can reproduce a drop exactly.

These are v0 balance values only. Persistence, economy, crafting, and tooltip presentation remain outside this task.
