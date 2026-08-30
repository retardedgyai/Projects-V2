# Environment and Repository Audit — 2026-08-30

## Confirmed

- Candidate repo: `C:\Users\xgaiz\Documents\Codex\Projects-V2`.
- Existing worktree branch: `play/gyai/obsidian-director-console` at `5b97350`.
- Existing worktree is dirty due to Obsidian/docs changes and is excluded from this experiment.
- Cached local `main` and `origin/main`: `8636d0f`.
- Repo rules require one editing agent per branch/worktree, server authority, no main push, no force push, and no destructive Git operations.
- Repo targets Minecraft 26.2, Minestom `2026.08.16-26.2`, Kotlin `2.4.10`, Gradle `9.5.1`, and Java toolchain 25.
- No `.mca`, `level.dat`, schematic, NBT, world archive, or `pack.mcmeta` exists in the repo.
- The present server creates a generated flat instance. A supplied world loader does not exist.
- Existing combat content depends on Fabric plugin-message input and therefore is not a valid Vanilla-client core loop.
- NPC, quest, crafting/economy loop, hunt lifecycle, and world import are not currently implemented as a playable whole.

## Orchestration status

- No Conductor implementation or configuration was found.
- OpenCode orchestration prompts exist under `.opencode/commands`, but no `opencode` executable is available in this Windows session.
- `gh` is unavailable and WSL is not installed.
- The existing `/parallel` flow is GitHub-Issue-oriented and cannot directly run this contract-first swarm experiment.
- Bash/Linux manual-smoke scripts are not executable as-is on this Windows-only host.

## Toolchain blockers

- Current `JAVA_HOME` is Temurin 17; JDK 25 is missing.
- Java reports `user.home=C:\`, causing the default Gradle wrapper cache to target unwritable `C:\.gradle`.
- An explicit writable Gradle home is required.
- The prior task's Gradle wrapper download is incomplete (`.part` and `.lck` only).
- The bundled Git can edit local history/worktrees but lacks `git-remote-https`, so fetch/push currently fail.

## Additional input

A prebuilt Minecraft world is optional, not blocking. Without one, the slice will generate a compact port/outpost directly in Minestom. If the Creator later supplies a world, it must include the world directory/archive and either target coordinates/region bounds or permission for the integration lane to select a bounded area.
