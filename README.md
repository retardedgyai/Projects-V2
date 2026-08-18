# ProjectS v2

ProjectS v2 is a clean rewrite of ProjectS as a Minecraft-protocol action MMO built around a shared harbor hub and 1–4 player boss-hunt instances.

This repository is the new source of truth for the rewrite. Legacy ProjectS repositories remain reference/history only; new implementation should not copy the old architecture by default.

## Product direction

Core loop:

`Harbor → choose hunt → enter party hunt space → fight boss → receive personal materials → craft/upgrade gear → attempt harder hunts`

Open world is deferred until this loop is fun and stable.

## Technical direction

- Kotlin-first codebase.
- Minestom is the preferred server runtime, pending the rewrite technical spike.
- Fabric is the client runtime layer; the eventual product should feel like a dedicated ProjectS client rather than “install a Fabric mod”.
- Server remains authoritative for damage, hits, rewards and progression.
- Human-facing authoring tools are intentionally limited to the Mob Editor. Skill/VFX/Studio/World Builder/Balance editors are not part of v2.
- VFX is authored through code/data/AI and rendered by the client, not through a general VFX editor.

## First milestone

The first playable vertical slice is intentionally tiny:

`Launch → harbor → take one hunt → enter one hunt map → kill one boss → return with one material → craft/upgrade one weapon → repeat, with persistence.`

Before that full loop, development starts with a one-day combat spike whose only question is: **is ProjectS combat actually fun?**

## Documentation

- `docs/00-product-vision.md` — current game vision and scope boundaries.
- `docs/game/` — gameplay rules and combat principles.
- `docs/architecture/` — server/client/runtime boundaries and hunt lifecycle.
- `docs/research/` — external projects studied and lessons worth carrying forward.
- `docs/development/` — rewrite rules and first-day spike.
- `legacy/README.md` — how old ProjectS code should be treated.

## Rewrite principle

Do not build a framework before a feature. Implement one real piece of gameplay first, then extract reusable structure from what the game actually needs.
