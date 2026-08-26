# Boss Mechanic Pack v0

The v0 pack stays inside `RiftExecutionerController`. It adds no generic encounter
DSL and no protocol message; all hit decisions remain server-owned.

## Selected mechanics

| Mechanic | Decision | Telegraph and resolve | Phase |
| --- | --- | --- | --- |
| Rift Sector | Move outside the cone and manage persistent space | Ground sector telegraph, then a single cone hit and a persistent rift | Duel / Rift Pressure |
| Chain Dash | React to the line tell and keep moving or bait a miss | Dash trail and five active ticks; a miss creates a break | All phases |
| Vertical Crush | Jump or leave the small landing zone | Full-circle ground telegraph; resolve requires horizontal range and <= 0.75 vertical separation | Execution |

The deterministic attack cycle is sector, slam, dash, and vertical crush. The first
two mechanics reuse existing telegraph paths. Vertical Crush reuses the existing
ground telegraph protocol with a full circle and adds only a concrete server-side
vertical boundary.

Damage remains conservative: sector 6, dash 5, and crush 7; the existing slam is 8.
Every attack has telegraph, resolve, and recovery. `reset`, defeat, victory, and
encounter finish clear the controller and its rifts.
