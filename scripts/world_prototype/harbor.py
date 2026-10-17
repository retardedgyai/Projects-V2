"""開拓港 prototype: harbor master's house + quay + pier. Preview only."""
import random
import iso

R = random.Random(7)
w = iso.World()
G = 5  # ground surface block y
SEA = 4  # water surface block y

GRASS = ("grass_block_top", "grass_block_side")
STONE_MIX = ["stone_bricks"] * 5 + ["mossy_stone_bricks", "cracked_stone_bricks", "andesite"]
COBBLE_MIX = ["cobblestone"] * 4 + ["mossy_cobblestone", "stone", "andesite"]

# --- terrain: land north of the quay, sea to the south, a sand beach on the east ---
for x in range(0, 38):
    for z in range(0, 30):
        shore = 17 + max(0, x - 28) // 2  # the beach bends south-east past the quay
        if z <= 17 and x <= 28 or (x > 28 and z < shore - 3):
            h = G + (1 if z <= 2 and x <= 20 else 0)
            w.fill(x, 0, z, x, h - 1, z, "dirt")
            w.set(x, h, z, *GRASS)
        elif x > 28 and z <= shore + 2:  # sand beach: 5, 5, 4, 4, then under water
            h = G - (z - (shore - 3)) // 2
            w.fill(x, 0, z, x, h, z, "sand")
            if h < SEA:
                w.fill(x, h + 1, z, x, SEA, z, "water_still", shape="water")
        else:
            floor = 0 if z > 22 else 1
            w.fill(x, 0, z, x, floor, z, R.choice(["sand", "sand", "gravel"]))
            w.fill(x, floor + 1, z, x, SEA, z, "water_still", shape="water")

# --- quay wall (z 18..19) with cap and bollards ---
for x in range(0, 29):
    for z in (18, 19):
        for y in range(0, G):
            w.set(x, y, z, R.choice(STONE_MIX))
        w.set(x, G, z, "polished_andesite")
    if x % 6 == 2 and not 19 <= x <= 23:
        w.set(x, G + 1, 19, "dark_oak_log_top", "dark_oak_log", shape="thinlog")

# --- pier (x 20..22, z 20..31) on spruce posts ---
for z in range(20, 29):
    for x in range(20, 23):
        w.set(x, G, z, "spruce_planks")
    if z % 4 == 3 or z == 28:
        for x in (20, 22):
            w.fill(x, 0, z, x, G - 1, z, "spruce_log_top", "spruce_log")
            w.set(x, G + 1, z, "spruce_log_top", "spruce_log", shape="post")
    else:
        w.set(20, G + 1, z, "spruce_planks", shape="fence_ns")
w.set(22, G + 2, 28, "lantern", shape="lantern")
w.set(20, G + 2, 28, "lantern", shape="lantern")

# --- path from the door to the pier ---
for z in range(13, 18):
    for x in (10, 11):
        w.set(x, G, z, "dirt_path_top", "dirt_path_side")
for x in range(11, 21):
    w.set(x, G, 17, "dirt_path_top", "dirt_path_side")
    if R.random() < .4: w.set(x, G, 16, "coarse_dirt")

# --- harbor master's house ---
X0, X1, Z0, Z1 = 5, 15, 5, 12  # ground floor footprint
# plinth (one course wider, slightly sunk) and stone ground floor
w.fill(X0 - 1, G, Z0 - 1, X1 + 1, G, Z1 + 1, "cobblestone")
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        edge = x in (X0, X1) or z in (Z0, Z1)
        for y in range(G + 1, G + 5):
            if edge: w.set(x, y, z, R.choice(COBBLE_MIX) if y == G + 1 else R.choice(STONE_MIX))
# corner quoins
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    for y in range(G + 1, G + 5):
        w.set(x, y, z, "chiseled_stone_bricks" if y == G + 4 else "polished_andesite")

# ground floor windows (recessed one block) with sills
def window(cells, inward):
    """Recess each cell one block and put a dark room behind the glass so it reads as a window."""
    for (x, y, z) in cells:
        dx, dz = inward
        w.clear(x, y, z); w.set(x + dx, y, z + dz, "glass"); w.set(x + 2 * dx, y, z + 2 * dz, "dark_oak_planks")
        # shutters on the outer face of the neighbouring wall cells
        for s in (-1, 1):
            nx, nz = (x + s, z - dz) if dz else (x - dx, z + s)
            if (nx, y, nz) not in w.b:
                w.set(nx, y, nz, "spruce_trapdoor", shape="shut_s" if dz else "shut_e")


for wx in (7, 8, 13):
    window([(wx, G + 2, Z1), (wx, G + 3, Z1)], (0, -1))
for wz in (7, 9):
    window([(X1, G + 2, wz), (X1, G + 3, wz)], (-1, 0))
# door, recessed, with stone lintel
for y in (G + 1, G + 2):
    w.clear(10, y, Z1); w.clear(11, y, Z1)
w.set(10, G + 1, Z1 - 1, "spruce_planks", "spruce_door_bottom"); w.set(10, G + 2, Z1 - 1, "spruce_planks", "spruce_door_top")
w.set(11, G + 1, Z1 - 1, "spruce_planks", "spruce_door_bottom"); w.set(11, G + 2, Z1 - 1, "spruce_planks", "spruce_door_top")
w.set(10, G + 3, Z1, "stone_bricks", shape="stair_e_t"); w.set(11, G + 3, Z1, "stone_bricks", shape="stair_w_t")
w.set(10, G + 4, Z1 + 1, "spruce_planks", shape="slab")  # small door canopy
w.set(11, G + 4, Z1 + 1, "spruce_planks", shape="slab")
# lamp by the door
w.set(13, G + 1, Z1 + 2, "spruce_planks", shape="fence"); w.set(13, G + 2, Z1 + 2, "spruce_planks", shape="fence")
w.set(13, G + 3, Z1 + 2, "spruce_planks", shape="fence"); w.set(13, G + 4, Z1 + 2, "lantern", shape="lantern")

# upper floor: timber frame, jettied out one block south and east
U0, U1, V0, V1 = X0, X1 + 1, Z0, Z1 + 1
YB = G + 5  # floor beam
for x in range(U0, U1 + 1):
    for z in range(V0, V1 + 1):
        if not (x in (U0, U1) or z in (V0, V1)): continue
        corner = x in (U0, U1) and z in (V0, V1)
        post = corner or (z == V1 and (x - U0) % 3 == 0) or (x == U1 and (z - V0) % 3 == 0)
        w.set(x, YB, z, "dark_oak_log_top", "dark_oak_log")
        for y in (YB + 1, YB + 2):
            w.set(x, y, z, *(("dark_oak_log_top", "dark_oak_log") if post else ("calcite",)))
        w.set(x, YB + 3, z, "dark_oak_log_top", "dark_oak_log")
# corbels under the jetty
for x in range(U0, U1 + 1):
    if (x - U0) % 3 == 0: w.set(x, YB - 1, V1, "dark_oak_planks", shape="stair_n_t")
for z in range(V0, V1 + 1):
    if (z - V0) % 3 == 0: w.set(U1, YB - 1, z, "dark_oak_planks", shape="stair_w_t")
# upper windows (recessed)
for wx in (7, 13):
    window([(wx, YB + 1, V1), (wx, YB + 2, V1)], (0, -1))
window([(U1, YB + 1, 10), (U1, YB + 2, 10)], (-1, 0))

# gable roof, ridge along x, one block overhang on every side
RX0, RX1 = U0 - 1, U1 + 1
RZ0, RZ1 = V0 - 1, V1 + 1
mid = (RZ0 + RZ1) / 2
YR = YB + 3
for z in range(RZ0, RZ1 + 1):
    step = int(min(z - RZ0, RZ1 - z))
    y = YR + step
    for x in range(RX0, RX1 + 1):
        trim = x in (RX0, RX1)
        mat = "spruce_planks" if trim else "mangrove_planks"
        if abs(z - mid) < .6:
            w.set(x, y, z, "deepslate_tiles" if not trim else "spruce_planks", shape="slab")
        else:
            w.set(x, y, z, mat, shape="stair_n" if z > mid else "stair_s")
        # fill below the roof surface inside the walls
        if U0 <= x <= U1 and V0 <= z <= V1:
            for yy in range(YR + 1, y):
                inner = x in (U0, U1)
                w.set(x, yy, z, "spruce_planks" if inner else "mangrove_planks")
# small round window in the east gable
w.set(U1, YR + 2, int(mid), "spruce_trapdoor")

# dormer on the south slope (x 9..13), front flush with the upper wall
DX0, DX1, DZ = 9, 13, V1
for x in range(DX0 - 1, DX1 + 2):
    for z in range(int(mid) + 1, RZ1 + 1):
        for y in range(YR, YR + 8):
            if (x, y, z) in w.b and w.b[(x, y, z)][1] == "mangrove_planks" and z > mid: w.clear(x, y, z)
for x in range(DX0, DX1 + 1):
    for z in range(int(mid) + 1, DZ + 1):
        for y in range(YR, YR + 2):
            side = x in (DX0, DX1)
            if z == DZ or side:
                w.set(x, y, z, *(("dark_oak_log_top", "dark_oak_log") if side else ("spruce_planks",)))
window([(11, YR, DZ), (11, YR + 1, DZ)], (0, -1))
for x, y in ((10, YR + 2), (11, YR + 2), (12, YR + 2), (11, YR + 3)):
    w.set(x, y, DZ, "spruce_planks")
for z in range(int(mid), RZ1 + 1):
    for x, y, f in ((DX0 - 1, YR + 2, "e"), (DX0, YR + 3, "e"), (DX0 + 1, YR + 4, "e"),
                    (DX1 + 1, YR + 2, "w"), (DX1, YR + 3, "w"), (DX1 - 1, YR + 4, "w")):
        w.set(x, y, z, "spruce_planks" if z == RZ1 else "mangrove_planks", shape=f"stair_{f}")
    w.set(11, YR + 4, z, "deepslate_tiles" if z != RZ1 else "spruce_planks", shape="slab")
    for x in range(DX0 + 1, DX1):
        for y in range(YR + 2, YR + 4):
            if z < RZ1 and (x, y, z) not in w.b: w.set(x, y, z, "mangrove_planks")
# remaining main-roof steps beside the dormer at the eave
for x in (DX0 - 1, DX1 + 1):
    for z in range(int(mid) + 1, RZ1 + 1):
        step = int(min(z - RZ0, RZ1 - z))
        if YR + step < YR + 2: w.set(x, YR + step, z, "mangrove_planks", shape="stair_n")

# chimney through the north slope
for y in range(G + 1, YR + 8):
    for x, z in ((13, 6), (14, 6)):
        w.set(x, y, z, R.choice(COBBLE_MIX))
w.set(13, YR + 8, 6, "stone_bricks", shape="slab"); w.set(14, YR + 8, 6, "stone_bricks", shape="slab")

# --- props: barrels and crates on the quay, a tree behind the house ---
for x, z, n in ((16, 15, 2), (17, 15, 1), (16, 14, 1), (24, 18, 2), (25, 18, 1)):
    for k in range(n):
        w.set(x, G + 1 + k, z, "barrel_top", "barrel_side")
for x, z in ((3, 15), (3, 16), (4, 16)):
    w.set(x, G + 1, z, "spruce_planks", "spruce_trapdoor")


def tree(tx, tz, base, height):
    for y in range(base + 1, base + height + 1):
        w.set(tx, y, tz, "oak_log_top", "oak_log")
    top = base + height
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                r = dx * dx + dz * dz + (dy * 1.6) ** 2
                if r <= 9.5 + R.random() * 2 and (dx, dz) != (0, 0) or (dx, dz) == (0, 0) and dy > 0:
                    p = (tx + dx, top + dy, tz + dz)
                    if p not in w.b: w.set(*p, "oak_leaves")


# bushes along the house and a few dirt patches in the grass
for x, z in ((4, 13), (4, 12), (16, 6), (17, 7), (16, 13), (6, 14), (15, 14)):
    if (x, G + 1, z) not in w.b: w.set(x, G + 1, z, "oak_leaves")
for _ in range(40):
    x, z = R.randrange(0, 28), R.randrange(0, 17)
    if w.b.get((x, G, z), ("", ""))[1] == "grass_block_top": w.set(x, G, z, R.choice(["coarse_dirt", "podzol_top"]), "dirt" if True else None)
# fence around a small yard to the east
for z in range(4, 15):
    if (24, G + 1, z) not in w.b: w.set(24, G + 1, z, "spruce_planks", shape="fence_ns")
tree(20, 4, G, 6)
tree(25, 8, G, 5)
tree(2, 3, G + 1, 5)

if __name__ == "__main__":
    img = iso.render(w, W=44)
    img.save("harbor.png")
    # close-up on the house
    W, H = img.size
    img.crop((int(W * .22), 0, int(W * .72), int(H * .55))).resize((int(W * .5) * 2, int(H * .55) * 2), 0).save("harbor-house.png")
