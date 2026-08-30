# Tidebreak Quality Amendment — 2026-08-30

This amendment does not change the ProjectS Constitution. It closes implementation gaps found in the first integration review.

## Fixed acceptance rules

- Every route must record three server-confirmed Brineclaw defeats before Lookout admission. Hunter's four-Cord route naturally satisfies this; Gatherer and Supplier must also learn the combat vocabulary.
- Gatherer progress requires four distinct registered Ore nodes. Repeating one node may yield Ore after cooldown but cannot finish the route objective.
- Cairnback's closed shell takes only chip damage. A closed-shell solo brute force requires more time than the five-minute hard timeout; lit-pillar Charge collision is therefore the practical victory path.
- Exposed damage is the dominant window. Barbed Point and Guard Ring multipliers are applied exactly once by the combat authority.
- Charge visuals advance over the active window instead of teleporting to their endpoint. Collision and damage geometry advance with the same distance.
- Sweep and Charge have refreshed particle geometry in addition to text, sound, and entity facing.
- The practice post is a visible 3×3×4 collision landmark.

## Persistence compatibility

Snapshot schema v2 adds:

- `brineclawTrainingDefeats`
- `gathererOreNodeMask`

Valid schema-v1 profiles are migrated in memory. Completed legacy profiles retain completed training; Coupler-only legacy profiles must perform the missing lesson. Unknown, malformed, and oversized files remain blocked and are never overwritten.

## Required evidence

- All three routes reach `COMPLETE`, reload, and preserve training/MOD state.
- Same-node Gatherer repetition does not complete the route.
- Closed-shell Cairnback cannot be killed inside the hard timeout at maximum legal Thrust cadence.
- A powered-pillar exposure produces an eligible authoritative victory.
- Twenty paired block/use callbacks produce twenty actions, not forty.
- Production and test sources compile for JVM 25.
- Full Gradle build, distribution startup probe, and Creator-operated Vanilla 26.2 smoke remain mandatory before release verdict.
