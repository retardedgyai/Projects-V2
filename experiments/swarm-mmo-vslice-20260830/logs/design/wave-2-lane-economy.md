# Wave 2 / Lane E — Economy, Transactions, and Exploits

Isolation: this lane read only `CONSTITUTION.md` and did not reuse Wave 1 proposals.

The lane deliberately constrained the economy to six server-ledger values: currency, one gathered resource, one enemy resource, one crafted intermediate, one boss credit, and one weapon MOD state. World actions generate value, but authoritative amounts live in a bounded per-player ledger rather than physical drops, preventing inventory-full, drop, rename, pickup-race, and death duplication.

Three one-time permit routes were made numerically solo-safe:

- Combat: participate in four ordinary kills, consume two enemy resources, retain enough for the final MOD.
- Gathering: gather six nodes, consume four ore, retain enough for the final MOD.
- Craft/trade: start with limited currency, buy two ore and one enemy input, pay a fee, craft and deliver one socket, then retain enough currency for the final MOD.

The lane proposed an NPC stock with strict caps, buy price at twice sell price, small emergency floors, and lifetime sale quotas. It explicitly removed auction, escrow, mail, dynamic pricing, crafting levels/quality, affixes, durability, multiple currencies, rarity tiers, recipe discovery, rerolls, repeat boss-catalyst farming, physical economic drops, seasons, and dailies.

Boss credit uses an encounter/player unique claim and only advances `NONE → CREDIT_AVAILABLE → one of two MOD states`. The boss never drops the finished weapon. Final MOD recipes consume the credit, both ordinary resources, and currency. Weapon appearance is presentation; the server profile owns cooldown and MOD effect.

Every mutation runs as one atomic server transaction: validate all prerequisites and caps, calculate the complete delta, apply in memory, persist, then show success; persistence failure rolls back. One-time arrival, contract, permit, boss claim, credit consumption, and MOD selection are flags. Replayed UI packets return the prior result rather than re-executing.

The lane enumerated forgery, repeated clicks, double death callbacks, tag-and-run boss credit, arbitrage, stock griefing, node spoofing, environmental kills, profile restart, crafting disconnect, and overflow.

Red-team pressure: the numerical safety is strong, but stock floors, quotas, UI replay caching, global stock persistence, boss reconnect claims, and six-value ledger may exceed the minimum slice. The next gate must delete any mechanism not needed to prove meaningful specialization and one complete upgrade.
