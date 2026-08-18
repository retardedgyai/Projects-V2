# Minestom Case Studies

These projects are references for design ideas, not code to copy blindly.

## Hollow Cube — `hollow-cube/mapmaker`

Why it matters to ProjectS:

- Minestom-based production server code.
- Separate hub / multi-map / isolated-map runtime shapes.
- Active game maps treated as temporary runtime state.
- Persistent processing/storage delegated outside the active map runtime.
- Explicit map instance load/unload lifecycle.
- World-local event/scheduler/collision responsibilities.

What ProjectS should borrow conceptually:

- A first-class HuntSession with explicit lifetime.
- Template map → runtime instance → destroy after hunt.
- Keep hunt-local work local to the hunt.
- Persistence separate from temporary hunt state.
- Development can be monolithic while production can split later.

What not to copy blindly:

- Their exact Java architecture.
- Their editor/UGC product model.
- Native-image or deployment complexity before ProjectS needs it.

## Hypixel Recreation — `Swofty-Developments/HypixelRecreation`

Why it matters:

- Large Minestom codebase modeling many server roles.
- Server roles can load different game/location logic from one larger codebase.
- Multiple instances of a role can be routed for scaling.
- Separate services exist for Party, Guild, Auction, Replay, etc.
- Redis/MongoDB/Velocity are used to coordinate the network.

What ProjectS should borrow conceptually:

- Keep the option for future server roles such as `development`, `hub`, and `hunt`.
- Make party/hunt assignment an explicit concept that can later move across processes.
- Keep boundaries clean enough that future scaling is possible.

What ProjectS should NOT do now:

- Do not start with microservices.
- Do not require Redis/MongoDB/Velocity for the first playable loop.
- Do not split Party/Quest/Loot into separate processes before real load requires it.
- Do not build an orchestrator before there is something worth orchestrating.

## WynnLab-Minestom — `WynnLab/WynnLab-Minestom`

Why it matters:

- Experimental Wynncraft/WynnLab reimplementation on Minestom.
- Kotlin-based examples of custom click input, spell scheduling and damage flow.
- Demonstrates that an MMO-like custom combat layer on Minestom is practical in principle.

Useful ideas:

- Own the game's combat/damage rules rather than assuming Vanilla damage is the game.
- Kotlin + Minestom is a viable development combination.

Things to avoid carrying forward:

- Gameplay/domain definitions directly coupled everywhere to Minestom `Player`.
- Individual skills owning uncontrolled global scheduler tasks.
- Large amounts of game state hidden in platform tags without clear domain ownership.
- Treating an old experimental codebase as a current Minestom best-practices reference.

The repository's visible recent code is from 2022, so it is an archaeology/reference source, not a modern architecture template.

## Current ProjectS conclusion

Minestom is preferred not because Paper cannot implement custom combat, but because ProjectS is increasingly replacing Vanilla systems anyway:

- custom normal attacks,
- custom mob attacks,
- custom damage geometry,
- custom boss logic,
- custom hunt instances,
- custom equipment/progression,
- dedicated Fabric client presentation.

The rewrite should still run a technical spike before treating the decision as irreversible.
