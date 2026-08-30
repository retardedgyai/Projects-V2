# Swarm MMO Vertical Slice Run Manifest

## Goal

Test whether an AI group can independently design, criticize, converge, implement, integrate, build, and start a small end-to-end ProjectS MMORPG vertical slice without changing `main` or disturbing the existing ProjectS worktree.

## Git isolation

- Local base observed at start: `8636d0f55eb614b1e984e7bf60daef9884822608` (`main` and cached `origin/main`).
- Remote freshness: not yet verified because the bundled Git lacks the HTTPS remote helper.
- Integration branch: `experiment/gyai/swarm-mmo-vslice-20260830/integration`.
- Integration worktree: `C:\Users\xgaiz\Documents\Codex\Projects-V2-exp-swarm-mmo-20260830-integration`.
- Existing dirty worktree `C:\Users\xgaiz\Documents\Codex\Projects-V2` remains untouched.
- Future rule: one branch = one worktree = one editing process. Git metadata operations and integration are serialized by one coordinator.

## Actual concurrency available

- This Codex session exposes four active-agent slots total: one coordinator plus at most three child agents at once.
- Many design and red-team roles will therefore run in waves of three, not as hundreds of simultaneous processes.
- The repo's current machine budget is approximately 16 logical CPUs, 17 GB available memory, and 49 GB free on C:.
- With Gradle configured for `-Xmx2G`, eight simultaneous build lanes are unsafe.
- Safe initial implementation topology: three editing lanes plus one serial integration lane. A fourth implementation lane may be trialed only with staggered builds and measured memory use.

## Gates

1. Environment/repo audit.
2. Constitution freeze.
3. Independent design/research waves.
4. Independent red-team waves.
5. Current Contract freeze.
6. Implementation branches/worktrees created from the same Contract commit.
7. Independent implementation with no shared-worktree editing.
8. Serial integration, tests, build, and server startup.
9. Human manual smoke handoff.

No implementation lane may start before gate 5.

## Existing-detail boundary

Design lanes receive only `CONSTITUTION.md`. Audit lanes may inspect the repo. After the Current Contract is frozen, implementation lanes may inspect existing code for technical reuse, but compatibility with old content is not a design goal.
