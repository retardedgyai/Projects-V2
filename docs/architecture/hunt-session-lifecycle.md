# Hunt Session Lifecycle

A hunt session is the ProjectS gameplay unit for one party running one hunt.

## Core rule

Every temporary hunt resource must have a clear owner and a clear destruction path.

A hunt session owns the lifetime of:

- the Minestom instance used for the hunt,
- party membership in that hunt,
- boss and mob entities,
- hunt-local combat runtime/state,
- hunt-local timers/scheduled work,
- quest objectives/state,
- temporary hazards and attack volumes,
- reward resolution state,
- hunt-local event handlers.

When the hunt session closes, all of the above must be cleaned up or become unreachable.

## Desired flow

1. Party selects a hunt in the harbor.
2. HuntSessionFactory receives the hunt definition and party.
3. A map template is loaded into a fresh Minestom instance.
4. HuntSession is created around that instance.
5. Players are transferred into the session.
6. Mob/boss content starts.
7. Combat and hunt rules run using hunt-local lifetime/scheduling.
8. Boss is defeated or hunt fails/expires.
9. Rewards are computed server-side.
10. Persistent rewards/progression are committed.
11. Players are returned to the harbor.
12. HuntSession closes.
13. Entities, tasks, events and transient state are removed.
14. The Minestom instance is unregistered/destroyed.

## Architecture lesson from Hollow Cube

Hollow Cube's Map Maker treats an active map as a runtime object with an explicit load/unload lifecycle and keeps game servers largely stateless beyond active maps. ProjectS should preserve this principle, not copy their implementation directly.

Useful lessons:

- Explicit instance registration/unregistration.
- Map templates/runtime data are separate concepts.
- Avoid keeping a second unnecessary full copy of world data once loaded.
- World/session-local event and scheduling scope is preferable to uncontrolled global tasks.
- Persistent player data should not live only inside the temporary world process/state.

Reference project:
- `hollow-cube/mapmaker`
- Important reference file at research time: `modules/map-core/.../MapInstance.java`

## Development vs future production

During early development, one process can run:

- harbor,
- hunts,
- persistence adapters,
- all gameplay.

Do not introduce Redis, Velocity or service orchestration just to imitate a large network.

If population later requires it, the same conceptual boundary can become:

- harbor server role,
- hunt server role(s),
- separate persistent/social services.

That split is a future operational decision, not a requirement for the first playable game.
