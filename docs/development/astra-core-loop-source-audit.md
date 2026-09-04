# Astra core loop — source audit / design intent

Audit date: 2026-09-05. Legacy snapshot: `retardedgyai/ProjectS@6f8d223ca1ec60f292176412b2ea019833cbadba`.
V2 starting point: `origin/main@0554f70dcef344525854bc10229e45a42a7ffa32`.

## What the director is seeking

The old `game-loop.md` and `beta-system-decisions.md` repeatedly describe a shared world in which
different serious playstyles support each other: gatherers supply materials, combat players obtain
monster/boss/MOD materials, crafters produce equipment bases, and users finish their own builds.
The desired feeling came from friends pursuing different goals while sharing one home.

V2 deliberately reduces the first playable delivery to a complete loop:

`Harbor → choose expedition → explore/fight/gather → boss → personal materials → return → equipment update → saved progress → another expedition`.

The reward has to change the next run in an observable way. An isolated boss, a recipe usable only
through developer commands, or inventory items with no use do not complete that loop. Gathering must
have useful destinations; combat-only progression should remain possible, since the legacy design
explicitly makes life professions optional. A party is beneficial but not a requirement for solo play.

## Current conversation takes precedence

- Vanilla Minecraft 26.2 and server-side UI are sufficient and required for this delivery. Old client-only
  editor/UI plans are historical references, not dependencies.
- The generated 448-block quest map is the main content. Free exploration, boss-direct routing,
  walkable roads and gathering detours supersede the older small fixed field/island progression plans.
- Gathering clusters and map modifiers let players deliberately seek a material, quality of region,
  or map tier. The conversation does not establish a useful role for a separate random rare-material layer.
- The first human-shaped mob uses explicit sweep/slam attacks and a small AbilityManager. Contact
  damage and a huge editor/framework before working combat conflict with the accepted direction.
- The user judges gameplay by manual testing. Starting the client is preparation; moving/playing for
  the user is not part of implementation verification.
- The first loop is not permission to resurrect every old Beta feature: Lv45/25-hour leveling, 55–70
  MOD families, markets, city management, full classes and multiple editors remain future context.

## Useful retained constraints

- Stable IDs separate definitions from live instances, rolled items and UI labels.
- Server decides hits, damage, rewards, spending and progression. UI rendering must not award anything.
- The same attack geometry should generate both telegraph and damage shape. One attack hits a target
  once unless an explicit multihit is authored. Death/return/session close cancels pending damage.
- A hunt owns temporary entities, timers, objectives and reward-resolution state; closing the hunt
  removes them. Persistent materials/equipment do not live only inside the temporary instance.
- Crafting/upgrading must validate available resources, reject duplicate submissions, preserve
  inventory on failure and avoid losing rewards when inventory is full.
- Data-driven definitions are useful after real examples. Historical eight-kernel research is not an
  instruction to build all eight kernels before a player can complete a run.

## Harbor and Minecraft-native interaction

`first-harbor-town-v1.md` is an accepted bright, crowded frontier-port design. It calls for short
repeatable walking routes and facilities whose appearance communicates their role: furnace/smoke/anvil
for crafting, crates for storage, a board/table for expeditions, provisions under canvas. Building a
large empty city or relying on giant floating instructions would lose that intent.

The benchmark hub therefore uses a fixed compact procedural template of ordinary Minecraft blocks:
an arrival path beside a wood pier, a small open expedition hall, smithy, storehouse, provisions stall
and a book-lined mastery shelter. Five one-line Japanese labels support the physical landmarks.
Inventory menus can open by right-clicking their workstations; runtime UI owns actions and spending.

`HarborScene.build(instance)` builds once before player admission and returns:

| Kind | Main interaction block (x, y, z) | Visible purpose |
|---|---|---|
| EXPEDITIONS | 0, 41, -15 | Cartography tables under the expedition hall |
| WORKSHOP | -16, 41, -6 | Smithing table with anvil, furnace and crafting bench |
| STORAGE | -15, 41, 10 | Barrels under the blue canvas awning |
| SUPPLIES | 16, 41, 9 | Provision stall with smoker and bundled goods |
| MASTERY | 16, 41, -9 | Lectern and shelves |

The returned spawn is `(0.5, 41, 7.5)` facing the expedition hall. Floor blocks are at y40, usable
facilities are within 25 blocks, paths are level and at least three blocks wide. Sea continues beyond
the hub instead of exposing a void. Labels are returned as owned entities for eventual hub disposal.

## Full read manifest for this audit lane

Legacy root/overview documents:

- `AGENTS.md`
- `docs/vfx-motion-foundation.md`

Every Markdown document under legacy `docs/design/` (18 files):

- `README.md`
- `game-loop.md`
- `beta-system-decisions.md`
- `decision-status.md`
- `equipment-system.md`
- `mod-system.md`
- `magic-system.md`
- `status-effects.md`
- `ice-system.md`
- `combat-shape-v1.md`
- `ability-runtime-v1.md`
- `ability-visual-v1.md`
- `ability-vfx-appearance-runtime-v01.md`
- `kotlin-authoring-v1.md`
- `skill-vfx-editor-v1.md`
- `visual-output-guidelines.md`
- `world/first-harbor-town-v1.md`
- `assets/first-harbor-town/README.md`

Every Markdown document under legacy `docs/research/` (7 files):

- `README.md`
- `harvest-template.md`
- `adoption-matrix.md`
- `implementation-plan.md`
- `sources/wynncraft.md`
- `sources/monumenta.md`
- `sources/lepinoid.md`

Current V2 documents read in this lane:

- `AGENTS.md`
- `docs/00-product-vision.md`
- `docs/decisions/2026-08-19-current-decisions.md`
- `docs/game/combat-principles.md`
- `docs/game/mana-and-class-resources.md`
- `docs/game/twin-rods-aerial-combat.md`
- `docs/architecture/hunt-session-lifecycle.md`
- `docs/development/post-day-1-roadmap.md`
- `docs/development/quest-map-core-playground.md`

No assigned Markdown document remains unread. All four legacy harbor PNG references were also visually
inspected: `primary-reference.png`, `design-board-reference.png`, `layout-board-reference.png`, and
`aerial-board-reference.png`. Their compact functional grouping, readable workstation landmarks,
canvas/wood/stone palette and return-to-port feeling inform the smaller benchmark hub. Legacy
`docs/beta/**`, implementation reports, current Obsidian specifications and actual V2 gameplay code
are audited by the other core-loop lanes, not claimed as read here. External research findings above
are historical source documents; this task does not claim to have revalidated all external repositories.
