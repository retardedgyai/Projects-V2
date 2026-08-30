# Red-Team — Economy and Multiplayer State

Verdict: the product loop is strong, but the proposed shared economy and reconnect state were too large.

P0 deletions/fixes:

- Delete shared NPC stock. It allows buyout griefing, floor-generated supply that erases professions, and cross-file crash inconsistency.
- Do not use a shared physical gate as personal progression authority. Arena entry checks the player profile.
- Delete offline/disconnected victor reward reservation. Only online eligible encounter members receive the result.
- Freeze encounter roster, use a generation ID to invalidate scheduled actions, reset after roster defeat/exit/disconnect or hard timeout.
- Store all player quest, currency, resources, fitting, catalyst, and MOD state in one atomic profile snapshot. Remove global economy state and physical catalyst authority.

P1 cuts:

- No three-stamp all-activities checklist.
- Supplier is a one-time procurement commission, not a simulated trader career.
- No first-slice P2P ledger transfer/escrow.
- No late entry, support scoring, or offline claim.
- Boss catalyst immediately masterworks the already-prepared fitting; no post-victory low-tier regrind.
- No repeat reward, clear count, mastery commission, or MOD respec.

Recommended minimum economy:

- Per-player ledger: Scrip, Ore, Cord, commission flag, fitting state, quest stage.
- Initial 12 Scrip once.
- Ore and Cord enter the ledger from registered world node and server-confirmed enemy reward.
- Fixed broker: buy either raw input for 6, sell for 2; no stock.
- Signal Coupler/Field Fitting: Ore×2 + Cord×2 + 4 Scrip.
- Hunter gets surplus Cord, Gatherer gets surplus Ore, Supplier gets a one-time 16-Scrip procurement commission.
- Boss changes PREPARED → CATALYST_READY; Smith changes that to one of two MOD states without extra ordinary inputs.
- Exactly-once, atomic, bounded mutations.
