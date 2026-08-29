---
type: implementation-snapshot
main_commit: 8636d0f55eb614b1e984e7bf60daef9884822608
date: 2026-08-29
---

# Build Snapshot

> [!warning] これは仕様ではなく、2026-08-29時点の実装Evidence
> branchやmainが進んだら古くなる。正確な現在地が必要な時はAIに更新を頼み、Git/code/testsと突合する。

## main

- Commit: `8636d0f` — Merge PR #125 PixelLab MCP Pipeline v0
- Modules: `server-minestom`, `client-fabric`, `protocol`
- Runtime: Kotlin-first / Java 25 / Minecraft 26.2 / Minestom + Fabric

## mainにあるもの

- Server-authoritative Combat foundation
- Heavy Blade / Twin Blades(TWIN_RODS internal name) normal attacks
- Twin Blades aerial jump/dodge loop
- Twin Blades Skill 1 / 2 / 3 + VFX/Sound
- Mana/Cooldown/class resource snapshot
- Dodge
- Rift Executioner prototype Boss
- Weakpoint / break / phases / rifts / final struggle
- Damage Core
- Equipment/MOD pure domain
- Equipment ItemStack presentation bridge
- Global Player Progression v0 (Lv1〜45, XP, Passive Point)
- 6-node Global Passive Tree + effects
- Progression file persistence
- Combat HUD / Inventory Character Screen
- Approved UI asset kit / stat icons
- Particle runtime/presets and development editors
- PixelLab generation/adoption pipeline

## mainにないもの

- Fire/Ice runtimeとEquipment/MOD→Combat接続（#127 branchで作業中）
- Lightning gameplay
- Twin Blades one-item ownership fix
- 完成HuntSession orchestration
- 港町/Quest/Map/報酬/帰還の一本化
- Boss素材→Craft/強化
- Beta economy/market
- 完成Equipment Tooltip
- AI Boss model pipelineの正式採用

## Last observed working branch

`combat/element-mod-build-slice-v0`

- Fire/Ice、CombatBuild resolver、ElementalState、tests、Client Optional policy docを追加中
- ここに書かれた進捗は2026-08-29時点。現在状態の正本ではない。

## Evidence

Git history、main source/tests、[Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127)、GitHub compare結果を突合したsnapshot。
