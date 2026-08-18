# ProjectS v2 — Product Vision

## What ProjectS is

ProjectS v2 is an online action MMO built on the Minecraft protocol/client ecosystem, but its combat, progression and hunt structure are ProjectS-owned rather than Vanilla-owned.

The initial game is not an open-world MMO. It is a dense hunt-focused game with a shared harbor/social hub.

## Initial player loop

1. Spawn in the harbor.
2. Choose a hunt/quest.
3. Prepare party and equipment.
4. Enter a dedicated 1–4 player hunt space.
5. Explore a compact map, fight minor enemies/gather optional resources, then fight the boss.
6. Receive personal rewards/materials.
7. Return to the harbor.
8. Craft, modify or enhance equipment.
9. Attempt harder hunts.

## Scope boundaries

### In v2 initial scope

- One harbor/social hub.
- Party size 1–4.
- Personal loot.
- Compact reusable hunt maps.
- Boss-centered progression.
- Weapon identity and build progression.
- Crafting/upgrading from boss materials.
- Dedicated client presentation through Fabric.
- Minestom server candidate.
- Mob Editor only.

### Deferred

- Large open world.
- Many towns.
- Guild warfare/GvG.
- Full market/economy services.
- Large server microservice split.
- Launcher before the game loop is fun.
- General-purpose editors.

## First complete slice

The first complete slice is:

`Launch ProjectS → take one quest in the harbor → enter one hunt → kill one boss → return with one material → craft/upgrade one weapon → fight again, with persistence.`

No broader system earns priority over completing this loop.

## Design principle

ProjectS should feel like its own game using Minecraft as the protocol/world/client foundation, not like a Paper plugin trying to overwrite Vanilla behavior one event at a time.
