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
HILLS = [("grass_block_top", 3), ("coarse_dirt", 3), ("stone", 3), ("andesite", 2), ("gravel", 1)]
MEADOW = [("grass_block_top", 14), ("coarse_dirt", 1), ("moss_block", 1)]
FOREST = [("podzol_top", 3), ("coarse_dirt", 3), ("grass_block_top", 3), ("moss_block", 2)]
DAMP = [("moss_block", 3), ("grass_block_top", 3), ("mud", 1), ("coarse_dirt", 1)]
BED = [("gravel", 3), ("clay", 2), ("dirt", 1), ("sand", 1)]
SIDE = {"grass_block_top": "grass_block_side", "podzol_top": "podzol_side", "dirt_path_top": "dirt_path_side"}
# cliff gradient, dark -> light
CLIFF = ["deepslate", "cobbled_deepslate", "tuff", "tuff", "cobblestone", "gravel", "andesite", "andesite", "stone", "stone"]


def pick_mix(mix, x, z, seed, blob=4.0):
    """Choose from a weighted mix in blobs: the noise value (smooth, blob-sized) picks the material, a small per-block
    dither feathers the borders."""
    tot = sum(wt for _, wt in mix)
    v = noise3(x, 0, z, seed, blob) * .85 + _h(x, 7, z, seed) * .15
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
        name = pick_mix(HILLS, x, z, seed + 2, 3.0)
    elif damp > .5:
        name = pick_mix(DAMP, x, z, seed + 3, 3.0)
    elif forest > .45:
        name = pick_mix(FOREST, x, z, seed + 4, 3.5)
    else:
        name = pick_mix(MEADOW, x, z, seed + 5, 5.0)
    return (name, SIDE.get(name, name))


def cliff_block(x, y, z, frac, recess, seed=311):
    """A block inside a rock face. frac: 0 at the foot, 1 at the top of the face. recess: 0 open face .. 1 deep crack.
    Vertical patches come from noise stretched along y."""
    v = noise3(x * 1.0, y * .2, z * 1.0, seed, 2.6)                     # tall streaks
    w2 = noise3(x, y, z, seed + 1, 1.3)
    t = .08 + frac * .78 + (v - .5) * .9 + (w2 - .5) * .22 - recess * .55
    i = int(max(0, min(len(CLIFF) - 1, t * len(CLIFF))))
    return CLIFF[i]


def bed_block(x, z):
    return pick_mix(BED, x, z, 321, 2.5)


def moss_streak(x, y, z, top_y, seed=351):
    """Moss runs down a face from the top in streaks (where water would trickle), thinning with depth."""
    depth = top_y - y
    v = noise3(x, y * .07, z, seed, 1.1)
    return v > .6 + depth * .02
