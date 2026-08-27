# ProjectS Client Optional Policy v1

Status: CURRENT DIRECTION — 2026-08-27

## Principle
ProjectS gameplay is server-first. The Fabric client mod is an optional enhancement layer, not a prerequisite for the core game loop.

A player without the ProjectS client mod should still be able to connect and meaningfully play the core game: move, fight, take damage, defeat enemies/bosses, receive/equip gear, understand basic equipment effects, progress, and participate in the main PvE loop.

The client mod may improve controls and presentation, including dedicated keybinds, cooldown/resource HUD, richer telegraphs, custom menus, VFX/presentation, and development/editor tooling.

## Hard invariant
No core gameplay mechanic may have a client-only custom packet as its only usable entry point.

Server authority remains unchanged. Optional client packets may request an action, but the action must resolve through the same server-owned gameplay path as any vanilla-compatible fallback.

## Current known over-dependencies
1. Normal attack is currently initiated through `AttackInput` from Fabric.
2. Dodge / Air Jump / class Skill inputs currently enter through Fabric custom packets.
3. Some boss ground telegraphs (notably sector/rift overlays) depend on `GroundTelegraphStart` client rendering.
4. Passive Tree allocation currently depends on the Fabric screen/request path.
5. Equipment presentation data is stored in ItemStack custom data, but without the custom client only the basic item name is player-readable.

## Target split
### Must work without client mod
- connect / spawn / reconnect
- normal combat baseline
- server-authoritative damage and hit resolution
- vanilla movement-based avoidance
- boss attack readability sufficient to evade attacks
- boss HP / victory / defeat
- EquipmentItem ownership and equip/use
- minimal readable equipment stats/MOD information through vanilla-compatible presentation when those systems become player-facing
- progression gain/persistence

### Optional client enhancement
- Z/X/C/V or other dedicated skill keybinds
- dedicated dodge/air-movement keybinds if desired
- rich skill cooldown / mana / resource HUD
- custom Inventory / Character screens
- Passive Tree custom screen
- higher-quality ground telegraph overlay
- richer hit/swing feedback
- VFX/editor/development screens

## Near-term implementation direction
Before making more gameplay depend on Fabric, add a small vanilla-compatible input/presentation boundary.

- Normal attack: accept a vanilla server-observable attack/swing path and feed the existing `CombatState`; Fabric `AttackInput` becomes an enhanced input route, not the sole route.
- Skills: when skills become required for ordinary play, provide at least one vanilla-native activation route that invokes the same server skill action. Exact UX can be chosen per concrete slice; do not build a generic input framework.
- Boss telegraphs: every damaging boss attack must have a server/vanilla-visible particle/sound/readability fallback. Fabric overlay may enhance it.
- Equipment: when MOD/build choice becomes player-facing, provide compact vanilla-readable lore/text from authoritative server data; custom tooltip remains optional polish.
- Cooldowns/resources: gameplay state remains server-owned. Client HUD is optional presentation.

## Do not do
- do not remove Fabric support
- do not fork separate gameplay implementations for modded vs vanilla players
- do not trust client-calculated damage/cooldowns/stats
- do not build a large generic compatibility framework
- do not resume custom UI work merely to satisfy vanilla compatibility

## Success definition
The server can host both:

`Vanilla client -> core ProjectS gameplay`

and

`ProjectS Fabric client -> same gameplay + better controls/presentation`

without changing authoritative game rules.
