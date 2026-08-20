# Twin Rods Aerial Combat

Twin Rods identity is an aerial melee loop: jump from the ground, keep landing
normal attacks, use aerial dodges to reposition, and continue attacking while
the target is in reach.

## Current Foundation

- Aerial sustain depends on a server-confirmed normal-attack hit.
- Only Twin Rods can sustain aerial movement or use aerial dodge.
- Each airborne sequence starts with 2 aerial dodge charges.
- An aerial dodge consumes 1 charge when the dodge actually starts.
- A Twin Rods aerial hit restores 1 charge, up to 2 charges.
- Multiple targets hit by one attack execution restore at most 1 charge.
- Stopping attacks lets the sustain window expire, after which normal gravity
  takes over. Sustain only suppresses downward velocity; it is not flight.

The future center of this weapon is following weak positions such as a large
enemy's head, back, wings, or arms while dodging in the air. The weakpoint
system itself is a later step and is not part of this foundation.

The initial values are provisional and must be tuned through Manual Smoke:
the sustain window is 10 ticks and the aerial dodge capacity is 2 charges.
