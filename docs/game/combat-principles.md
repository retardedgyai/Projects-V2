# Combat Principles

This document records the current v2 combat direction. Values/timings are not final until playtested.

## 1. ProjectS owns normal attacks

Vanilla Minecraft's normal attack rules are not the universal rule for every ProjectS weapon.

A normal-attack input means: **perform this weapon family's basic combat action**.

Examples:

- Sword: alternating slashes/basic chain.
- Greatsword/heavy blade: slow, committed heavy swings.
- Tonfa-like twin rods: fast close-range repeated strikes.
- Spear: narrow long thrusts.
- Bow: may preserve Minecraft's intuitive hold-to-draw/release-to-fire behavior.
- Staff: basic magic projectile or another weapon-specific action.

Normal attacks do not have to share identical timings, shapes or attack-speed behavior.

## 2. Attack-speed builds must improve weapon feel, not demand faster clicking

Avoid making high attack speed mean "the player must physically spam clicks faster".

Candidate behavior:

- Holding the normal-attack input may continue the weapon's basic sequence.
- One press may still produce one action when precise stopping is desired.
- Attack speed changes the weapon's action tempo.
- Attack speed does not necessarily scale every phase of every weapon equally.

Examples:

- Heavy blade: retain swing weight; reduce recovery/transition time more strongly than the visible heavy swing itself.
- Twin rods: strike cadence can scale strongly.
- Bow: draw/ready time can become faster.

Exact formulas are intentionally deferred until the combat spike is played.

## 3. Damage uses explicit spatial attack shapes

Do not rely on Vanilla melee-hit behavior as the main combat system.

Each attack owns an explicit spatial definition such as:

- sweeping arc,
- narrow thrust,
- close-range strike,
- impact circle,
- moving charge volume,
- projectile trajectory.

The visual attack trail and the server-side damage region should correspond closely enough that players can learn the geometry by sight.

For early prototypes a simplified sector/capsule/box is acceptable. More precise moving sweep tests can be added only if they improve feel enough to justify the complexity.

## 4. The server is authoritative

The client can show swing VFX immediately so latency does not make input feel delayed.

The server decides:

- whether the attack was legal,
- where the attacker/target were,
- whether the attack shape intersected the target,
- final damage,
- weak-point/part hits,
- rewards.

The client must not be trusted when it claims that a target was hit.

## 5. One attack does not hit the same target repeatedly by accident

Each attack execution tracks which targets it has already hit. A moving sweep overlapping the same enemy for several ticks still produces the intended number of hits.

True multi-hit attacks define multiple intentional hit moments/actions rather than exploiting repeated overlap.

## 6. Mob damage follows the same explicit-attack rule

Default contact damage is rejected.

Being close to a mob is not itself a damage event. The mob must perform an attack with a readable attack definition and the player must intersect that attack.

Enemy "weapons" are broader than inventory weapons and can include:

- claws,
- teeth,
- tail,
- horn,
- fist,
- magic,
- a charge/ram body volume.

A player who remains close to a boss but correctly avoids every attack is allowed to keep attacking.

## 7. Body collision and damage are separate concepts

Large enemies may physically block/push players so players cannot stand inside their body.

That body collision does not automatically deal damage.

A charge attack, fiery aura or similar hazard must be represented as an explicit attack/hazard with its own readable rules.

## 8. Avoidance is about avoiding the attack, not escaping the enemy

The desired mastery curve is:

- beginner: back away when the boss starts moving,
- experienced: recognize the attack and step outside its region,
- expert: use minimum movement to avoid the hit while preserving attack uptime.

No lock-on and no automatic facing/magnetism are currently planned for the combat spike. Attacks go where the player aims, with deliberately forgiving geometry where needed for Minecraft controls.

## 9. Hit feedback does not freeze the server/world

Do not implement true server/world hitstop.

Weight on confirmed hits comes from client presentation:

- contact sparks/burst,
- impact sound,
- small camera impulse,
- damage number,
- brief visual emphasis on heavy impacts.

World simulation, bosses and other players continue normally.

## 10. Combat presentation does not depend on handcrafted character animations

Initial combat can work with no bespoke attack animation system.

The attack itself is communicated primarily by:

- clear slash/thrust/impact VFX shapes,
- sound,
- contact feedback,
- weapon-specific rhythm.

Do not build an animation editor/engine before the combat proves it needs one.
