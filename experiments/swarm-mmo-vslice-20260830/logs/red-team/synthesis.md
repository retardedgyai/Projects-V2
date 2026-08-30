# Red-Team Synthesis and Freeze Decision

All P0 findings are incorporated into `CURRENT_CONTRACT.md`.

## Accepted decisions

- One identity vocabulary: Tidebreak Anchorage / Tidehook / Brineclaw / Cairnback / Signal Coupler / Pressure Pearl.
- One generated 96×96 port with Market, Tidal Flat, Quarry/Cave, and Breakwater.
- Three NPCs: Warden, Broker-Smith, Lookout.
- Three selected procurement routes, each solo-safe; source items remain economically interchangeable.
- Fixed per-player fallback exchange, no shared stock or P2P market subsystem.
- One ordinary gathered resource, one ordinary combat resource, one repair craft, one catalyst, two mutually exclusive MOD outcomes.
- Targeted Thrust and right-click Brace only. Brace must pass a Vanilla input smoke gate; Fabric is not an allowed fallback.
- Brineclaw and Cairnback share Sweep and Charge. No third attack, dynamic water, Break meter, or Drive.
- Crash-pillar collision creates boss vulnerability. Brace only reduces failed-positioning punishment.
- Personal profile owns readiness; physical repair is visible interaction, not global authority.
- Frozen encounter roster; no late join or offline reward.
- One atomic profile snapshot; exactly-once first rewards.
- Three parallel editing lanes plus one serial integration lane.

## Deliberate integration choice

Red-Team disagreed on session-only versus restart persistence. The integration decision is to keep small restart persistence because the implementation already requires exactly-once profile transactions and the MMO completion claim benefits materially from surviving restart. No shared/global market persistence is added.

## Freeze verdict

**PASS TO FREEZE**, conditional only on implementation honoring the explicit file boundaries and deletion list. Input reliability is an acceptance test, not permission to add Fabric or a replacement combat framework.
