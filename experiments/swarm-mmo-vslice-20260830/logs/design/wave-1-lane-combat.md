# Wave 1 / Lane C — Vanilla Combat and Boss

Isolation: the lane read only `CONSTITUTION.md`; it did not inspect the repo or other proposals.

Proposal: **Tidehook Loop**.

One named Vanilla item, Tidehook, is the only valid weapon in the bounded slice.

- Left-click entity: Thrust with 4-tick commitment, server raycast, 3.4-block range, and recovery.
- Sprint + left-click: Drive with longer range, stronger stagger, and longer commitment.
- Right-click: Brace with raise, seven-tick parry window, movement penalty, and recovery.

The server treats packets/events as intent and validates signed item, zone/instance, alive state, cooldown, state, range, eye-to-bounds facing, line of sight, server position, and one-hit action identity. Vanilla damage is cancelled and resolved by the server.

One Saltworn family has Hook Cut (arc) and Dragline (line/pull). Both teach the boss vocabulary and drop only Salt-Knot Cord plus occasional currency.

The Breakwater Colossus has three phases:

1. Sweep and Chain Rush; correctly braced Rush builds Break.
2. Flooded low lanes plus an unbraceable Undertow Ring while Rush remains braceable.
3. A fixed learned sequence: Sweep, Undertow, two Rushes; the second Rush is faster but rewards mastery with extra Break.

No surprise final mechanic is added. BossBar, actionbar, particle geometry, sound category, entity facing, knockback, and block layout provide Vanilla-readable feedback; color alone is never semantic.

Wipe handling preserves inventory/quest, returns players to the outpost, and atomically resets health, Break, attack state, water, and hazards after the arena empties. Rewards are idempotent and reserved if inventory is full.

Blue Iron and Reed Fiber gathering plus Salt-Knot Cord make one of two trade-off MODs: damage with a tighter Brace, or a longer Brace with less stagger-window damage. A personal Pressure Heart masterworks the installed MOD. A bounded-stock broker links combat, gathering, and crafting roles.

Primary risks: input startup feeling laggy, suppressing default hoe interaction, water becoming friction, particle clutter, market deadlock/manipulation, parry tuning, and reset/reward idempotency.

Key acceptance checks: standard-input Vanilla completion; invalid range/walls/spam produce no damage; distinguishable arc/line/ring; complete wipe reset; exactly-once rewards; either MOD craftable; three economic roles produce value; automated state/raycast/parry/phase/reset/reward/stock tests.
