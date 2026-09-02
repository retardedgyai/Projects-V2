# Quest Map Visual Quality Contract

This contract replaces “more random blocks” as the definition of quality for the ProjectS quest-map playground. A seed may pass topology and reachability tests and still fail this contract.

## Reference findings

### Wynncraft

The official Wynncraft map shows that a readable RPG landscape is a network of authored places, not uniformly decorated noise. Roads split and merge around lakes, towns, ruins, forest edges, fields, cliffs, and major silhouettes. Biome transitions are staged over distance, while local scenes use a dominant landmark plus supporting terrain and vegetation.

- Official map: https://map.wynncraft.com/
- Official rendered map tile studied for road, lake, settlement, and biome composition: https://cdn.wynncraft.com/nextgen/renders/2.0.4/0/3/3/3/0.png
- Crystal Cave example studied for layered paths, water, lighting, framing, and a destination beyond the foreground: https://forums.wynncraft.com/threads/crystal-cave.142106/

### Monumenta

Monumenta's public dungeon gallery demonstrates room-level authorship. Each room has a clear activity, a dominant silhouette, a controlled palette, a navigable floor, strong walls or background framing, and a small number of accents. Detail is clustered around the intended subject instead of distributed uniformly.

Celestial Zenith also fixes progression rhythm at the layout level: nine rooms are followed by a guaranteed boss as the tenth room. ProjectS does not copy its rooms, but adopts the principle that procedural order selects authored gameplay scenes rather than generating anonymous decoration.

- Official gallery: https://www.playmonumenta.com/
- Celestial Zenith room/floor rules: https://monumenta.wiki.gg/wiki/Celestial_Zenith

### Hypixel

Hypixel hub and island builds reinforce a gameplay constraint missing from showcase-only terrain: courtyards, paths, cover, water, and elevation must preserve movement and combat space. A beautiful cliff that makes ordinary traversal unpleasant fails this quest-map contract.

- Hypixel community hub rebuild discussion used as a gameplay-space comparison: https://hypixel.net/threads/i-rebuilt-the-ruined-castle-in-the-hub.4084476/

### Shotbow Annihilation: Cherokee

Cherokee's useful lesson is vertical organization: an elevated destination, a valley below it, resources distributed across height layers, and smaller hills breaking up a central plateau. ProjectS uses that spatial grammar for Clifflands while keeping its own routes, scenes, assets, and visual identity.

- Official map description: https://wiki.shotbow.net/Special%3AMyLanguage/Cherokee

### Archived structure source

The earlier prototype imported `sijmenvb/worldpainter-trees` under its MIT license. Those files remain traceable for migration compatibility, but production generation no longer selects any of its trees or rocks.

- Canonical source: https://github.com/sijmenvb/worldpainter-trees
- License: https://github.com/sijmenvb/worldpainter-trees/blob/main/LICENSE

Production trees and rocks are now ProjectS-authored parametric structures. Each ecology owns its
trunk architecture, branching grammar, crown placement, roots, scale range, material palette, and
ground-contact behavior. Rocks are constructed as terrain-following masses and composed outcrops,
not schematic stamps.

Production intake is restricted to CC0, CC-BY, MIT, or explicit written author permission that allows
modification and redistribution. Download counts, “free to use”, a marketplace listing, or a gallery
image are not sufficient. High-detail commercial and community packs may be studied, but their files
stay out of the repository until their exact terms pass this allowlist.

## Non-negotiable scene grammar

Every major scene must contain all five layers:

1. **Purpose** — the player can tell whether this is travel, combat, gathering, discovery, rest, or boss space.
2. **Primary silhouette** — one dominant form readable before its small details.
3. **Framing** — terrain, canopy, ruins, water, or walls shape the approach without blocking it.
4. **Ground transition** — the scene blends into adjacent ecology over multiple blocks; it is never pasted onto one flat disk.
5. **Supporting detail** — two or three coherent accents explain use and age. Random pillars, isolated fences, and freestanding block stacks are forbidden.

Between major scenes, the field must also contain composed middle-distance regions. A scenic region owns one ground transition, one dominant natural form, a related asset family, and negative space. Increasing isolated-prop count does not satisfy this requirement.

## Landscape scene graph

The generator now plans nine broad landscape scenes before it decorates the Minestom instance.
These are not prop spawn points. Each scene owns a 29–36 block radius, a main-road approach,
orientation, negative space, a contiguous surface field, and a role-specific landmark composition.
The current grammar includes sheltered groves, ridge gates, heath vistas, headwaters, ruined
terraces, ore cuts, and infernal rift gardens. Every terrain concept selects a deliberate five-role
sequence rather than drawing unrelated scene types from one global bag.

Candidate placement measures local relief, approach roughness, distance from quest events, and
separation from earlier scenes. The quality gate rejects a map unless it contains all nine scenes,
at least five landscape roles, valid road approaches, legal radii, and forty blocks of center
separation. A safe straight approach is preferred; when terrain would produce an abrupt painted
slope, bounded pathfinding routes the branch around it. Every stored access path is contiguous and
limited to two blocks of vertical change per step. Ambient trees and small detail are then excluded from non-forest scene interiors;
the scene grammar, not scatter density, controls the middle distance.

## Route contract

- The main road is an authored traversal corridor, not paint on a heightmap.
- A player standing on the road should normally see the current scene and a hint of the next, not the boss and every encounter.
- Long straight visibility is interrupted by bends, tree masses, rock shoulders, shallow cuts, bridges, or gateways.
- The road may narrow briefly for drama but must reopen before combat and gathering scenes.
- Stairs, slabs, retaining edges, bridges, and switchbacks must communicate why the road crosses its terrain.
- Road materials form contiguous wear patterns. Single-block confetti is rejected.
- After every structure and encounter has been placed, the complete Minestom instance is scanned.
  The full five-block main-road radius and two-block side-trail radius must remain free of solid
  decoration for eight blocks above the local surface. Any solid decoration inside that protected
  volume is removed, and a remaining obstruction prevents the map from entering the ready pool.

## Natural asset contract

- Tree families must mix at least three scales and multiple crown silhouettes per ecology.
- The lowest trunk/root mass must intersect terrain. No visible air gap is permitted below a tree or boulder footprint.
- Forests contain edge, interior, veteran, deadwood, shrub, and clearing roles instead of one uniformly scattered tree.
- Rocks are partially buried and occur as outcrops or related satellites, never as identical surface props.
- Water has inflow/outflow logic, variable shelf depth, distinct bank ecologies, and at least one composed focal edge.

## Landmark contract

- Road markers are low, believable navigation cues such as cairns, worn milestones, collapsed wall fragments, or a lamp attached to an actual rest scene.
- Tall freestanding posts, unexplained arches, floating rubble, and isolated decorative block stacks are prohibited.
- Combat spaces require a readable entry, a clear playable center, two or more edge features, and an exit orientation.
- Gathering spaces expose material through terrain: a seam, cut, fallen tree, bank, or excavation. Loose ore blocks piled on grass are prohibited.
- The boss approach must have at least two reveals: distant foreshadowing and an arena threshold.

## Automated rejection signals

Automation cannot certify beauty, but it must reject common failures:

- asset footprint exceeds its permitted terrain range;
- a placed asset has a solid block above air within its ground-contact footprint;
- repeated major asset family appears in adjacent scene slots;
- major scene lacks approach and exit clearance;
- main-road visibility exposes the boss too early;
- content-free travel span or uniform-detail span exceeds the authored budget;
- trees occupy the road or combat center;
- biome surface coverage collapses to one dominant material;
- preparation exceeds the ten-second 448×448 map budget.
- any chunk inside the configured client render horizon is absent when the map enters the ready pool.

## Manual review gate

Review at least twelve deterministic seeds spanning all four route layouts and all six terrain concepts, including every permanent regression seed. Capture spawn, first bend, one combat scene, one gathering branch, one discovery, boss threshold, liquid edge, forest interior, and a high overview.

A seed is rejected when any reviewer cannot answer “what is this place for?”, sees a repeated generator trick, notices a floating/pasted asset, or must fight the terrain to explore. Passing metrics is necessary but never sufficient.

## Generate, judge, discard

The live ready pool does not accept the first valid heightfield. For each requested ecology it now
generates four deterministic candidates, measures them, and loads only the best candidate into a
Minestom instance. Candidate seed spacing preserves the requested ecology, so selection cannot
silently turn a cherry-valley request into a nether or cliff concept.

The scenic score rewards height-band range, ecological ground transitions, varied local relief,
off-road landmark potential, a useful route detour, and delayed boss visibility. It penalizes the
share of sampled terrain that is too steep for ordinary exploration. The lower-scoring candidates
are discarded before chunk generation and decoration, and the two winning maps are prepared in
the background ready pool. Manual smoke seeds intentionally use one exact candidate so a reported
seed always reproduces the same failure.

`QuestMapPlanProvider` is the import boundary for a future external terrain source. Terra's current
Minestom platform targets Minecraft 1.21.8 rather than ProjectS's 26.2 protocol, so it is not linked
directly into the live server. The compatible path is an offline provider that generates Anvil
regions, validates and converts them into a `QuestMapPlan`, then reuses the same quest, route,
encounter, gathering, boss, and ready-pool layers. External terrain must pass this contract; its
brand name never exempts it from automated or manual review.
