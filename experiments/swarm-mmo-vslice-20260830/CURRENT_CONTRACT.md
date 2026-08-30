# Current Contract — Tidebreak Anchorage Vertical Slice

Status: **FROZEN FOR IMPLEMENTATION**

This is the only product/technical contract implementation lanes may follow. Existing ProjectS content names, balance, input protocol, boss, weapon, skill, progression, and world details are not requirements.

## 1. Player fantasy and complete loop

The player is a contractor helping reopen Tidebreak Anchorage, a small port blocked by the shell beast Cairnback.

Complete loop:

`arrive → speak to Warden → select Hunter/Gatherer/Supplier procurement route → create or buy missing Ore and Cord → craft and physically install one Signal Coupler → learn Sweep and Charge from Brineclaw → enter breakwater encounter → induce Cairnback to Charge a powered crash pillar → defeat it → receive personal Pressure Pearl state → choose and install one Tidehook MOD → report completion`

Fresh solo target: 15–30 minutes. Boss target: 4–6 minutes. Wipe-to-retry target: 15–30 seconds.

## 2. World

- Generate one bounded approximately 96×96 block port directly in Minestom.
- No imported world, Anvil loader, schematic, world save, procedural terrain framework, or resource pack.
- Adventure mode; no required Fabric client.
- Locations:
  - Harbor Market: spawn/respawn, three NPCs, exchange, forge, Coupler rack.
  - Tidal Flat: registered Ore nodes and a low-risk route.
  - Quarry/Cave: Brineclaw area, Ore route, and a short alternate path.
  - Breakwater: staging point, dry readable arena, static shallow edge water, raised blocks, and large crash pillars.
- Every feature is reachable from Market within 45 seconds. Boss retry from staging takes at most 30 seconds.
- Water is static scenery/route shaping only. No current, pull, underwater combat, flood, or runtime fluid mutation.

## 3. NPC and quest interaction

Exactly three functional NPCs:

- Warden: arrival, route choice, current objective recap, final report.
- Broker-Smith: fixed exchange, Coupler craft, Pressure Pearl MOD installation.
- Lookout: ordinary-enemy/Boss teaching, prepared-player encounter entry, retry.

Vanilla interaction contract:

- Static visible NPC name and role.
- Right-click entity interaction; main hand only and debounced.
- Chat exchanges are at most two short lines per stage.
- One Sidebar main objective outside combat.
- ActionBar shows immediate progress, combat state, or rejected-action reason.
- Chat records stage transition and next named destination.
- A chest GUI may be used only for route choice and two-MOD choice.
- Resource pack, color, sound, animation, or particle is never the sole information channel.

Persistent player stages:

`ROUTE_SELECTED → COUPLER_INSTALLED → BOSS_CLEARED → MOD_INSTALLED → COMPLETE`

Arrival/briefing and active encounter are transient. Other players never advance a player's stage.

## 4. Procurement, craft, and economy

Authoritative per-player profile fields:

- Scrip: 0..999.
- Ore: 0..99.
- Cord: 0..99.
- Selected route: HUNTER / GATHERER / SUPPLIER.
- Supplier commission claimed: boolean.
- Fitting state: NONE / PREPARED / CATALYST_READY / MOD_BARBED / MOD_GUARD.
- Quest stage and first-clear claim.

Fresh profile receives 12 Scrip exactly once.

Raw sources:

- Registered Ore node interaction gives 1 Ore after server distance, line-of-sight, target ID, and cooldown validation.
- Server-confirmed Brineclaw defeat participation gives 1 Cord.
- No finished equipment or physical authoritative currency/resource drops.

Fixed Broker exchange, no stock:

- Buy 1 Ore or Cord for 6 Scrip.
- Sell 1 Ore or Cord for 2 Scrip.
- Buy/sell round-trip always loses 4 Scrip.

Route objectives:

- Hunter: earn four Cord; keep two, sell two, buy two Ore.
- Gatherer: earn four Ore; keep two, sell two, buy two Cord.
- Supplier: inspect the two marked supply records at Tidal Flat and Quarry, report once to Broker-Smith, receive 16 Scrip, then buy two Ore and two Cord.
- Input source is unrestricted after selection, but the selected route objective must be completed before Coupler crafting.

Signal Coupler transaction:

- Cost: Ore×2 + Cord×2 + 4 Scrip.
- Crafting and installing occurs as one explicit confirmed interaction at the Market rack.
- Success changes fitting state to PREPARED and player stage to COUPLER_INSTALLED.
- No physical intermediate item and no global gate authority.
- Repeated clicks are idempotent and never consume twice.

Boss reward and MOD:

- Eligible victory changes PREPARED to CATALYST_READY and stage to BOSS_CLEARED.
- Pressure Pearl is a personal monotonic state, not an authoritative ground item.
- Broker-Smith offers exactly two permanent choices with no extra ordinary-material cost:
  - Barbed Point: +20% damage during exposed windows; Brace active window is shorter only on replay.
  - Guard Ring: longer Brace window and less Charge knockback; Thrust damage -10%.
- Choice changes state to MOD_BARBED or MOD_GUARD and stage to MOD_INSTALLED.
- Warden report changes stage to COMPLETE.
- No MOD respec, repeat catalyst reward, mastery commission, clear-count economy, shared stock, dynamic pricing, auction, consignment, mail, or P2P ledger transfer.

## 5. Tidehook combat

Tidehook is a server-tagged, named Vanilla Trident in a fixed hotbar slot. The server suppresses managed-item drop, Vanilla damage, and Trident throwing. Profile state, not item name/model, owns its effect.

Inputs:

- Left-click a registered entity: Thrust intent via `EntityAttackEvent`.
- Right-click/use Tidehook: Brace intent via the target-version use-item event.
- No Drive, custom key, skill slot, Fabric payload, shield, or second weapon.

Thrust:

- Validate at event receipt: correct item/zone/target, alive state, cooldown, 3.5-block eye-to-bounds range, facing, sampled line of sight, and duplicate action identity.
- If valid, resolve one server-owned hit immediately and enter recovery.
- Invalid intent deals no damage and reports a concise reason where useful.

Brace:

- Main-hand only, Vanilla use cancelled, offhand duplicate ignored, debounced.
- Start with a readable windup and forgiving 10–12 tick active window; recovery prevents spam.
- Correct timing substantially reduces Charge damage/knockback.
- Brace never exposes or independently staggers Cairnback.
- Acceptance requires air/block/shallow-water/offhand input matrix with at most 1 missing or duplicate/default-use result per 20 attempts. Failing this is FIX-FIRST; Fabric is not a fallback.

All damage, cooldowns, positions, rewards, and quest progression are server-authoritative.

## 6. Ordinary enemy and boss

Brineclaw is the only ordinary enemy family. It teaches exactly:

- Sweep: clear arc/pose plus sound, then an area attack; leave the arc.
- Charge: clear line/facing plus sound; sidestep or Brace. Collision with a large practice post exposes it briefly.

Cairnback is the only boss.

- Attack vocabulary is exactly Sweep and Charge.
- Charge collision with a powered crash pillar is the only way to create the main exposed damage window.
- Phase 1 uses separated Sweep and Charge.
- Phase 2 below 50% health uses a fixed, faster recombination of the same attacks; no new mechanic.
- No Break meter, Undertow, Rising Wash, dynamic water, third phase, add wave, or surprise final attack.
- Tells use at least two of text/geometry, sound, particle shape, and entity facing. Color alone is not semantic.
- BossBar shows health/phase. Sidebar hides during encounter. ActionBar is combat/error only.

Encounter:

- Lookout admits PREPARED players and freezes a 1–4-player roster at pull. No late join.
- Health may scale simply with roster size; tell timing and attack speed do not.
- Eligible first-clear reward requires roster membership, online/present in arena at death, and at least three server-confirmed Tidehook hits.
- Disconnect loses current encounter eligibility, not preparation.
- A roster wipe/exit/disconnect for five seconds, or a five-minute hard timeout, resets encounter.
- Every scheduled action carries an encounter generation ID; stale generations are no-ops.
- Reset clears health, phase, vulnerability, attack/recovery, entities/hazards, and roster.

## 7. Persistence and transaction rules

- Use a new experiment-only player-data path; never read/write the existing ProjectS progression files.
- One bounded player snapshot contains all quest/economy/fitting/reward fields.
- Temporary write plus atomic replace.
- Unknown keys, invalid ranges, oversized data, or malformed files are rejected and not overwritten.
- Every accepted transaction computes the complete delta first. Persistence failure rolls memory back and shows failure.
- Initial grant, Supplier commission, Coupler craft/install, boss claim, MOD choice, and completion are exactly-once/idempotent.
- Inventory is presentation reconstructed from profile on spawn/reconnect.
- Server restart persistence is required; no global/shared economy persistence exists.

## 8. Implementation ownership

Three editing lanes start from this same Contract commit.

### Lane A — World and loop

Owns only new files under:

- `server-minestom/src/main/kotlin/dev/projects/server/experiment/swarm/world/`
- `server-minestom/src/main/kotlin/dev/projects/server/experiment/swarm/loop/`
- matching tests in the same package roots.

Owns layout/generator/actor specifications, NPC/target IDs, player state, economy/craft/MOD transactions, and repository. Does not register global events or implement enemy/Boss combat.

### Lane B — Combat and encounter

Owns only new files under:

- `server-minestom/src/main/kotlin/dev/projects/server/experiment/swarm/combat/`
- matching tests.

Owns Tidehook action states/geometry, Brineclaw, Cairnback, roster, generation/reset, and abstract reward event. Does not know recipes, currency, quest text, or persistence.

### Lane C — Verification and startup assets

Owns only:

- `server-minestom/src/test/kotlin/dev/projects/server/experiment/swarm/contract/`
- `scripts/swarm-startup-check.ps1`
- `experiments/swarm-mmo-vslice-20260830/MANUAL_SMOKE.md`

Creates contract fixtures/tests/checklist/probe against frozen public boundaries. It does not edit production code, build files, or invent replacement APIs.

### Serial integration lane

Only integration owns:

- `server-minestom/src/main/kotlin/dev/projects/server/experiment/swarm/SwarmSliceServer.kt`
- `server-minestom/src/main/kotlin/dev/projects/server/experiment/swarm/SwarmSliceWiring.kt`
- `server-minestom/build.gradle.kts`
- cross-lane wiring and journey test adjustments.

Integration may adapt lane APIs narrowly but may not add cut systems.

## 9. Automated completion evidence

- World bounds, safe spawn, travel-target geometry, facility separation.
- Route arithmetic and solo completion for Hunter/Gatherer/Supplier.
- Negative buy/sell arbitrage.
- Supplier commission, Coupler, boss claim, MOD, and completion idempotency.
- Save/load, malformed/oversized rejection, atomic-save failure rollback.
- Thrust range/facing/line-of-sight/cooldown/duplicate-hit validation.
- Brace state/recovery and input adapter compile coverage.
- Brineclaw telegraph/active/recovery/exposure reset.
- Cairnback phase, pillar collision, vulnerability, roster eligibility, generation invalidation, wipe/hard-timeout reset.
- Two-player personal quest isolation.
- Complete journey domain test through save/reload.
- `:server-minestom:test`, root `build`, install distribution.
- Windows-native startup probe confirms ready log, TCP listen, process alive, and stops only its own PID.

## 10. Manual smoke pass/fail

Minecraft GUI is operated only by the human Creator.

Required path:

1. Pack-declined Vanilla 26.2 connect.
2. Validate Thrust and Brace input matrix.
3. Defeat three Brineclaws and identify Sweep/Charge by the third.
4. Complete one fresh route, exchange, Coupler install, Boss, Pressure Pearl, MOD, and final report.
5. Wipe once and retry within 30 seconds without repeating preparation.
6. Reconnect and confirm persistent objective/profile.
7. Answer: “Would I immediately fight this boss again without another reward?”

Input failure, unexplained 30-second objective stalls, stale encounter state, duplicate reward, or inability to finish without Fabric is `FIX-FIRST`.

## 11. Explicitly out of scope

Fabric/resource-pack dependency; imported/save world; open world; additional weapons/enemy families/bosses; classes/levels/skills/elements/tree; Drive; dynamic water; third boss phase; Break meter; shared stock; dynamic pricing; auction/P2P transfer; offline rewards; late join; formal parties/matchmaking; repeat reward/mastery; MOD respec; generic quest/craft/economy/AI/editor frameworks; external DB/cloud; housing/guild/pet/mount/fishing; automated Minecraft GUI.
