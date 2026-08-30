# Red-Team — Vanilla Combat and New-Player Experience

Verdict before cuts: the design stacked input, water, quest, and economy complexity before proving enjoyable combat.

P0:

- Prove right-click Brace with the exact Vanilla item across air, block targeting, shallow water, and offhand conditions. Main-hand filter, cancellation, and debounce are required. More than 1 missing/duplicate/default-use result per 20 attempts is a failed input contract.
- Resolve Thrust at `EntityAttackEvent` receipt after server validation. Do not delay the raycast by four ticks; express commitment as recovery. Delete Drive.
- Cut the tutorial to three NPCs, two raw inputs, one pre-boss craft, one post-boss update, fixed fallback exchange, and four player-visible objectives.
- Ordinary Brineclaw and Cairnback share exactly Sweep and Charge. Phase 2 recombines them; no unbraceable ring or new flood attack.
- Use a dry/readable arena for the first combat proof. Static shallow water may shape routes, but no pull, current, dynamic flood, or underwater combat.

P1:

- Sidebar owns non-combat objective; BossBar owns boss; ActionBar owns combat state/errors; Chat owns stage transitions.
- Particle semantics are limited and duplicated by sound/geometry.
- Start Brace forgiving: roughly 10–12 active ticks after a clear telegraph of at least 16 ticks.
- Boss target duration 4–6 minutes and retry in 15–30 seconds.
- Entry authority is personal readiness, not global block state.
- No dynamic economy, disconnected reward eligibility, or complex contribution score.

Falsification path:

1. Input matrix on dummy.
2. Three ordinary enemies; player must verbalize Sweep/Charge and intentionally avoid most attacks by the third.
3. Boss direct grant; second attempt should visibly improve and retry must be fast.
4. Fresh loop: spawn → NPC → one gather input → one enemy input → craft → boss → catalyst → upgrade, with no 30-second uncertainty.

Final qualitative gate: would the Creator immediately fight the same boss again without a reward? If no, add no world/economy scope; return to combat.
