# Twin Rods Aerial Combat

Twin Rods identity is an aerial melee loop: jump from the ground, land a hit,
gain another air action, then use the player's own input to maintain height and
position while dodging and attacking again.

## Current Foundation

- Automatic aerial hover and sustain are removed. Gravity always applies after a Twin Rods hit.
- A server-confirmed Twin Rods aerial normal-attack hit grants 1 Air Jump charge, up to 1 charge.
- A ground jump starts with 0 Air Jump charges; landing resets the charge to 0.
- A new Air Jump requires a release and press of Vanilla Space while airborne.
- Air Jump is consumed immediately by the server and may be used during an attack without cancelling it.
- Removing Twin Rods clears an unused Air Jump charge; re-equipping does not restore it.
- Twin Rods has up to 2 Air Dodge charges. Starting an aerial dodge consumes 1 charge.
- A Twin Rods aerial hit restores 1 Air Dodge charge, up to 2 charges.
- Multiple targets hit by one attack execution restore Air Dodge and grant Air Jump at most once.

The intended loop is: **Hit -> gain an air action -> create height and position by
yourself**. The future center of this weapon is following weak positions such as
a large enemy's head, back, wings, or arms while dodging in the air. The
Weakpoint system itself is a later step and is not part of this foundation.

Air Jump's initial candidate values are provisional and must be tuned through
Manual Smoke: approximately 8.4 blocks/sec vertical speed and 5.0 blocks/sec
horizontal push when directional input is held. Air Dodge capacity is 2 charges.
