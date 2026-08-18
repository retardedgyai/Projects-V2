# Day 1 Combat Spike

## Purpose

This is a one-day experiment, not production combat.

The question is not "can we build a combat framework?" The question is:

> Is ProjectS normal-attack combat more fun when weapons own their own attack rhythm and geometry instead of inheriting Vanilla Minecraft melee behavior?

## Test weapons

### Heavy blade

Goal: commitment, weight, deliberate openings.

Prototype features:

- basic heavy normal attack sequence,
- large/forgiving forward attack shape,
- slower cadence,
- attack-speed variants,
- clear slash VFX,
- impact sound/sparks/camera impulse.

### Tonfa-like twin rods

Goal: fast close-range pressure and satisfying attack-speed scaling.

Prototype features:

- fast repeated normal strikes,
- smaller/closer attack geometry,
- stronger attack-speed scaling,
- optional step-follow-up only if time permits,
- clear alternating strike VFX.

These are experiments, not guaranteed launch weapons.

## Required comparison

Both weapons must be testable at several attack-speed levels, for example:

- baseline,
- clearly faster,
- extreme test value.

The player should not need to click faster just because attack speed increased. Holding the attack input may continue the basic sequence for the prototype.

## Damage geometry

Start simple.

Heavy blade:
- broad forward sector/capsule-like melee region.

Twin rods:
- shorter and narrower close-range strike region.

Server decides hits.

Do not implement precise moving blade simulation unless the simple shapes make the prototype misleading.

## Enemy prototype

If time allows, include one basic attack enemy in addition to a dummy.

It should have only two readable attacks, for example:

- side sweep,
- forward slam.

Critical rule: touching/standing next to the enemy does not deal damage. Damage occurs only when its explicit attack region intersects the player.

This is used to test the desired play pattern:

`stay close → read enemy attack → move only enough to avoid the attack → continue attacking`

## Presentation

No bespoke character animation system.

Attack readability comes from:

- slash/strike shape,
- timing,
- sound,
- contact sparks,
- small camera impulse,
- different empty-swing vs confirmed-hit feedback.

Do not freeze the server/world for hitstop.

## Success criteria

At the end of the day, answer:

1. Is ProjectS-owned normal attack feel better than Vanilla-style melee for this game?
2. Does holding attack feel good or too automatic?
3. Does attack speed create a fun build fantasy rather than input spam?
4. Do Heavy Blade and Twin Rods feel meaningfully different with the same high-level combat rules?
5. Is VFX-led combat readable without handcrafted animations?
6. Is staying close and dodging explicit mob attacks fun?

If the answer is no, discard/change the approach instead of polishing it for another week.

## Not part of this spike

- full class system,
- final skills,
- full equipment system,
- final stamina/dodge rules,
- guard system,
- boss production framework,
- advanced animation,
- launcher,
- generic combat editor.
