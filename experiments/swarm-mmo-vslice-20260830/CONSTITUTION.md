# ProjectS Swarm Vertical Slice Constitution v1

Status: FROZEN for this experiment.

This document is the only game-design source shared with independent design lanes. Repo rules and technical safety constraints still apply, but existing ProjectS content details are not design requirements.

1. The result is an Action MMORPG vertical slice running on Minecraft Java Edition 26.2 with Minestom.
2. Minecraft is not hidden to imitate another game. Blocks, terrain, verticality, water, caves, structures, gathering, and spatial navigation may be part of the game itself.
3. Combat, exploration, gathering, crafting, trade/economy, and life activity must not become isolated minigames. Each implemented system must participate in one coherent world loop.
4. The design must not force every player into one optimal repeated route. Combat-first, gathering-first, and crafting/trade-first participation should each have a meaningful role, even if the slice is small.
5. Power must not come from level alone. Equipment or MOD choices, build decisions, knowledge, player skill, and economic activity must matter.
6. Boss combat must reward observation, learning, positioning, timing, and mastery. The boss must be readable without requiring a Fabric client.
7. Prefer a small dense place over a large empty world. Every included location must have a gameplay role.
8. Enemies must not mass-drop finished best equipment. Combat rewards should feed gathering, crafting, upgrading, or trade.
9. The primary loop must be playable with a Vanilla Minecraft 26.2 client. A Fabric client may enhance presentation but is never required for core input, state comprehension, or progression.
10. A resource pack may be used when it materially improves the player experience, but the slice must remain testable if the pack is declined unless the Current Contract explicitly defines a safe fallback.
11. Technology exists only to serve player experience. Do not add editor technology, generic frameworks, or showcase systems without a concrete slice need.
12. A new system may be accepted only if it connects to at least two other included systems.
13. Implementation feasibility matters, but the experiment does not need to conform to current ProjectS content classes, numbers, names, weapon identity, boss identity, or progression design.

## Minimum playable completion

- A startable Minestom 26.2 server.
- One compact port/outpost world or a clearly bounded region of a supplied world.
- Multiple NPCs and at least one interaction-driven quest.
- Server-authoritative combat usable from a Vanilla 26.2 client.
- At least one weapon, one ordinary enemy family, and one boss.
- Rewards/loot feeding an equipment or MOD update.
- A small but real crafting/economy connection.
- One complete playable loop from arrival through boss reward and upgrade.
- Automated test/build evidence and server startup evidence.
- A shortest-path manual smoke guide for the human Creator.

## Explicit non-inheritance

Independent design lanes must not assume or copy the existing Twin Blades, Rift Executioner, current skill slots, current progression values, existing boss arena, or current README loop. They may be reused later as technical code only if the frozen Current Contract independently arrives at a compatible need.
