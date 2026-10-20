"""Surface and rock materials, following what WorldPainter map makers and terraformers do.

Rules (sources: WorldPainter manual / polystruct custom terrain guide / Hypixel advanced terraforming guide /
minecraft.net landscaping tips / terrain-gradient guides):
  1. Material follows slope. Flat: grass mixes. 25-45 deg: "hills" mix (grass, coarse dirt, stone, andesite, gravel 3:3:3:2:1).
     Over 45 deg: "rocky" mix (stone 65, andesite 10, cobble 5.5, light-grey powder 5, gravel 4, dirt 2, a little ore and mossy cobble).
  2. Mix in patches ("blobs"), not single-block speckle: each mix is chosen by low-frequency noise, then dithered at the edges
     so two terrains feather into each other instead of meeting on a line.
  3. Forest floor is its own mix: podzol, coarse dirt, grass, moss in blobs; rooted dirt at tree bases.
  4. Cliffs: light blocks high and on the outer faces (stone, andesite), transition blocks mid-face (gravel, tuff, cobble),
     the darkest (deepslate) low and in recesses. Irregular VERTICAL patches, never horizontal stripes. Ledges grow moss/grass.
  5. Water edges stay natural: grass runs to the water, coarse dirt/path where it erodes, clay and gravel on the bed
     instead of a sand stripe at the waterline. Moss and mud in damp ground.
  6. Ground cover is clustered and low-intensity: grass tufts in clumps, flowers in single-species drifts, ferns in shade,
     a few mossy boulders on hills.
"""
import math
from greattree import noise3, _h

ROCKY = [("stone", 65), ("andesite", 10), ("cobblestone", 5.5), ("light_gray_concrete_powder", 5), ("gravel", 4),
         ("dirt", 2), ("coal_ore", .5), ("iron_ore", .5), ("mossy_cobblestone", 2)]
HILLS = [("grass_block_top", 6), ("stone", 2), ("andesite", 1)]
MEADOW = [("grass_block_top", 1)]
FOREST = [("grass_block_top", 60), ("moss_block", 20), ("rooted_dirt", 12), ("coarse_dirt", 8)]
DAMP = [("moss_block", 3), ("grass_block_top", 4), ("mud", 1)]
BED = [("gravel", 3), ("clay", 2), ("dirt", 1), ("sand", 1)]
SIDE = {"grass_block_top": "grass_block_side", "podzol_top": "podzol_side", "dirt_path_top": "dirt_path_side"}
# cliff gradient, dark -> light
CLIFF = ["cobbled_deepslate", "tuff", "tuff", "cobblestone", "andesite", "andesite", "stone", "stone"]     # ordered dark -> light, no skipped steps


def pick_mix(mix, x, z, seed, blob=4.0):
    """Choose from a weighted mix in blobs: the noise value (smooth, blob-sized) picks the material, a small per-block
    dither feathers the borders."""
    tot = sum(wt for _, wt in mix)
    v = noise3(x, 0, z, seed, blob) * .96 + _h(x, 7, z, seed) * .04
    acc = 0
    for name, wt in mix:
        acc += wt / tot
        if v <= acc: return name
    return mix[-1][0]


def surface(x, z, slope, forest, damp, seed=301):
    """Top block for walkable ground. slope is blocks up per block across (1.0 = 45 deg)."""
    if slope > 1.0:
        name = pick_mix(ROCKY, x, z, seed + 1, 2.5)
    elif slope > .47:
        name = pick_mix(HILLS, x, z, seed + 2, 7.0)
    elif damp > .5:
        name = pick_mix(DAMP, x, z, seed + 3, 6.0)
    elif forest > .45:
        name = pick_mix(FOREST, x, z, seed + 4, 9.0)
    else:
        name = "grass_block_top"
    return (name, SIDE.get(name, name))


def cliff_block(x, y, z, frac, recess, expo=0.0, seed=311):
    """A block inside a rock face, shaded by its form (terraformers' rule: value follows light, not noise).
    frac: 0 at the foot .. 1 at the top of the face. recess: 0 open .. 1 deep hollow. expo: -1 faces away from the sun .. 1 faces it.
    The noise only nudges by one palette step, in 2-3 block clusters, so neighbours never skip a step."""
    t = .12 + frac * .5 + expo * .28 - recess * .55
    c = noise3(x, y * .7, z, seed, 1.6)
    t += (c - .5) * .22
    i = int(max(0, min(len(CLIFF) - 1, t * len(CLIFF))))
    return CLIFF[i]


def bed_block(x, z):
    return pick_mix(BED, x, z, 321, 2.5)


def moss_streak(x, y, z, top_y, seed=351):
    """Moss runs down a face from the top in streaks (where water would trickle), thinning with depth."""
    depth = top_y - y
    v = noise3(x, y * .07, z, seed, 1.1)
    return v > .6 + depth * .02


BEDS = [("stone", 4), ("andesite", 3), ("tuff", 2), ("stone", 3), ("andesite", 4), ("dripstone_block", 2),
        ("stone", 5), ("tuff", 3), ("andesite", 2)]
HARD = {"stone": .07, "andesite": .04, "tuff": -.06, "dripstone_block": -.05}


def bed_at(x, y, z, seed=361):
    yy = y + (noise3(x, 0, z, seed, 9.0) - .5) * 3
    tot = sum(t for _, t in BEDS); k = yy % tot
    for i, (name, t) in enumerate(BEDS):
        if k < t: return i
        k -= t
    return 0


def ledge(x, y, z):
    """Hard beds stand out of a weathered face, soft ones are eaten back: radius factor for a pillar at this height."""
    return HARD[BEDS[bed_at(x, y, z)][0]]


def strata(x, y, z, seed=361):
    """Rock of a mesa pillar: horizontal beds of different thickness, each one material, the bed lines wavy by a block or so,
    with a little weathering where a bed's colour bleeds into the next. Mesas are layered; random blotches are not."""
    i = bed_at(x, y, z, seed)
    if _h(x, y, z, seed) < .08: return BEDS[(i + 1) % len(BEDS)][0]
    return BEDS[i][0]
