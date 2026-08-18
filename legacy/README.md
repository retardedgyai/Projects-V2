# Legacy ProjectS

The pre-v2 ProjectS repositories are preserved as reference/history and are not the implementation base for this rewrite.

Use legacy code for:

- recovering specifications and decisions,
- identifying old bugs/edge cases,
- extracting useful regression tests or acceptance criteria,
- protocol archaeology,
- understanding prior experiments,
- preserving research and design references.

Do not assume old code should be copied into v2.

The rewrite should be Kotlin-native and feature-first. Old Java architecture, editor-platform abstractions and abandoned tool systems are not requirements.

Important superseding v2 decisions include:

- Mob Editor is the only retained human-facing authoring tool.
- Skill Editor, VFX Editor, ProjectS Studio, VFX World Builder and Balance Editor are dropped.
- Open world is deferred behind the hunt-loop vertical slice.
- Minestom is the preferred server runtime candidate.
- Fabric remains the client runtime layer.
- Combat is ProjectS-owned rather than Vanilla-owned.
