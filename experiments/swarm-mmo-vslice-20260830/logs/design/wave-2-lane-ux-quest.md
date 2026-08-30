# Wave 2 / Lane D — UX, NPC Interaction, and Quest State

Isolation: this lane read only `CONSTITUTION.md` and did not reuse Wave 1 proposals.

The lane proposed a Vanilla-first interaction language:

- Looking at an NPC shows `Right-click: name — role` in the ActionBar.
- Right-click starts a maximum-three-line chat exchange.
- A chest GUI is used only for meaningful choices or confirmed consumption.
- Exactly one main objective appears in the Sidebar.
- ActionBar reports immediate progress or rejected-action reasons.
- Chat preserves stage completion and next-destination history.
- `/quest` restores current objective, progress, target NPC, and a short hint.
- Symbols plus text carry state; color, sound, animation, or a resource pack are never the only channel.

Minimum NPC roles: Coordinator, Field Guide, Artisan-Broker, and Gatekeeper. Repeated interaction answers the current need first instead of replaying old dialogue.

Minimal persistent state:

`ARRIVED → BRIEFED → ROUTE_SELECTED → CONTRIBUTION_READY → PREPARATION_COMPLETE → BOSS_AVAILABLE → BOSS_CLEARED → UPGRADE_READY → COMPLETE`

Stored adjuncts are selected route, contribution counts, preparation flag, clear count, reward claim, upgrade choice, and last safe location. Active encounter membership remains ephemeral. Every transition and material/reward operation must be idempotent.

Quest state is personal while the world and encounter are shared. Other players may supply materials, but another player's gate/boss progression never skips personal stages. Boss rewards are personal and require confirmed participation plus combat or arena-objective contribution, not last hit.

Reconnect checkpoints occur at stage change, delivery, trade/craft, reward reservation, upgrade, and clean disconnect. Boss contribution may be retained briefly across reconnect; eligible disconnected victors get an exactly-once reserved reward. Death preserves quest work. A wipe atomically resets encounter state and allows retry within 60 seconds without repeating preparation.

Completion is deliberately delayed: boss clear → claim personal catalyst → craft/install equipment or MOD update → report to Coordinator → COMPLETE. This guarantees the boss reward connects to craft/economy and power change.

Hard scope cuts include branching story, reputation, cinematics, quest-journal GUI, multiple tracked quests, NPC schedules, procedural quests, formal party/matchmaking, guild/mail, offline auction, quest inventory, corpse recovery, generic dialogue/quest editors, dailies, and full localization.

Acceptance emphasis: discovery within 60 seconds; current objective recoverable in three actions; all three routes finish in 20–40 minutes; simultaneous-player state isolation; repeated clicks never duplicate consumption/progress/reward; reconnect at every stage; complete wipe reset; chat-hidden and pack-declined completion; only one Sidebar main objective.
