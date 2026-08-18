# Rewrite Rules

## Legacy code policy

The old ProjectS repositories remain available as history/reference.

New v2 implementation should not default to copying or mechanically translating old code.

Carry forward:

- proven gameplay specifications,
- important design decisions,
- protocol lessons,
- known edge cases,
- valuable tests/acceptance lessons,
- research notes,
- world/building references,
- bugs worth preventing from returning.

Do not carry forward automatically:

- old class structure,
- old editor platform architecture,
- Java code just translated line-by-line into Kotlin,
- abstractions created for systems that no longer exist.

## Feature-first rule

Do not build a general framework before a real gameplay feature needs it.

Examples:

- Implement one real hunt before a universal Quest Engine.
- Implement the primitives needed for the first slash before a universal VFX Engine.
- Implement one boss lifecycle before a generic boss platform.
- Do not build a launcher before the vertical slice is fun.

Extract reusable structure after at least one concrete feature proves what needs to be reusable.

## Language/runtime direction

- Kotlin-first.
- Gradle Kotlin DSL preferred.
- Java is allowed at thin platform/Mixin/interoperability boundaries when it is materially safer or simpler.
- Minestom is the preferred server candidate, pending spike validation.
- Fabric is the client runtime layer.
- Content edited by tools should use versioned data where appropriate instead of forcing recompilation.

## Editor policy

Human-facing authoring tools retained in v2:

- Mob Editor only.

Explicitly out of scope:

- Skill Editor,
- VFX Editor,
- ProjectS Studio,
- VFX World Builder,
- Balance Editor,
- generic node/timeline/editor platform work.

VFX stays a game/runtime capability authored through code/data/AI, not a general visual editor.

## Architecture restraint

Do not build generic abstractions solely to make Minestom/Paper/Fabric interchangeable.

Avoid `GenericServer`, `GenericEntity`, `GenericWorld`, or similar universal wrappers unless a concrete gameplay requirement proves their value.

Pure domain logic is appropriate where it is genuinely platform-independent, for example:

- damage calculation,
- equipment rules,
- loot/reward calculation,
- quest rules,
- boss phase state.

World/entity/instance/scheduler integration can remain Minestom-specific in the server runtime.

## Scope rule

The rewrite succeeds when the smallest complete game loop is playable, not when every planned system has an architecture.
