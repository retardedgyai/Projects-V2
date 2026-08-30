# Wave 2 / Lane F — Minestom Feasibility and Lane Seams

Inputs: Constitution, run manifest, environment audit, and current build/server source. Existing content was excluded; no files were edited.

Verdict: the minimum slice is feasible inside `server-minestom` with one generated `InstanceContainer`, fixed coordinates, Vanilla events, three isolated packages, and per-player persistence. `protocol/`, `client-fabric/`, imported worlds, and a resource pack are not required.

Proposed Vanilla event surface:

- `AsyncPlayerConfigurationEvent`, `PlayerSpawnEvent`, and `PlayerDisconnectEvent` for instance/state lifecycle.
- `EntityAttackEvent` for targeted left-click intent.
- `PlayerEntityInteractEvent` for NPC right-click, main-hand filtered and debounced.
- `PlayerBlockInteractEvent` for nodes/forge/quest objects.
- `InstanceTickEvent` for enemy/boss FSM and reset.
- `ItemDropEvent` to protect managed equipment.
- Custom defeat handling, with death event only as fallback.
- `PlayerUseItemEvent` only if the frozen contract keeps Brace; its item-specific packet behavior must be compile and Vanilla-smoke verified.

Server attack validation must recheck zone, target registration, signed main-hand item, alive state, cooldown/action identity, eye-to-bounds distance, facing, sampled line of sight, and one hit per action. Events are intent, not authority.

World generation is a pure `blockAt(x,y,z)` fixed layout in a bounded 64–96 block outpost. Actors use experiment tags and layout constants. No Anvil loader, schematic, noise world, or save import. Boss block mutations are small and reset from an original map.

Three implementation ownership seams:

1. `experiment/swarm/world`: pure layout/generator, actor creation/tags, interaction target IDs; no business rules.
2. `experiment/swarm/combat`: combat model/geometry, ordinary enemy and boss FSM/runtime; reward callbacks only, no quest/economy knowledge.
3. `experiment/swarm/loop`: player state, item presentation, quest/reward/currency/recipe/upgrade service, atomic repository.

Serial integration alone owns `SwarmSliceServer`, wiring/event registration, Gradle run entry, journey test, Windows startup probe, and manual smoke document. This avoids concurrent edits to the existing 2312-line main.

Persistence uses a new experiment-only path and stores only schema, quest step, resource counts, currency, selected/crafted upgrade, and boss claim. It uses strict bounds, temp-write plus atomic move, and invalid-file overwrite protection. Inventory is reconstructed as presentation.

Key API spikes: exact target-version interaction signatures, whether `EntityAttackEvent` has any default damage side effect, hand duplication, instance/spawn ordering, item-stack remainder semantics, custom defeat stability, chosen entity movement/collision, and Windows launcher PID ownership.

Hard cuts: Fabric, pack, custom keys, imported/save world, multiple instances, matchmaking, generic frameworks, auction/dynamic prices, custom inventory GUI, classes/skills/elements/levels, extra weapons/enemies/bosses, generic pathfinding, external DB, dialogue trees, editors, and automated Minecraft GUI.
