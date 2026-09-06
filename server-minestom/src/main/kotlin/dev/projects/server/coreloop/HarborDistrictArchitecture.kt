package dev.projects.server.coreloop

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

/** A built-up working waterfront below a stone acropolis. Existing facility coordinates are contracts. */
internal class HarborDistrictArchitecture(private val instance: InstanceContainer) {
    private val timber = Block.STRIPPED_SPRUCE_LOG
    private val beam = Block.DARK_OAK_LOG
    private val plaster = Block.SMOOTH_SANDSTONE
    private val leaves = Block.JUNGLE_LEAVES.withProperty("persistent", "true")
    private fun put(x: Int, y: Int, z: Int, b: Block) = instance.setBlock(x, y, z, b)
    private fun box(x1: Int, x2: Int, y1: Int, y2: Int, z1: Int, z2: Int, b: Block) {
        for (x in x1..x2) for (z in z1..z2) for (y in y1..y2) put(x, y, z, b)
    }
    private fun stair(b: Block, facing: String, top: Boolean = false) =
        b.withProperty("facing", facing).withProperty("half", if (top) "top" else "bottom")
    private fun logX() = beam.withProperty("axis", "x")
    private fun logZ() = beam.withProperty("axis", "z")
    private fun stone(x: Int, y: Int, z: Int) = when (Math.floorMod(x * 3 + y * 11 + z * 7, 23)) {
        0, 1 -> Block.MOSSY_STONE_BRICKS
        2, 3 -> Block.CRACKED_STONE_BRICKS
        else -> Block.STONE_BRICKS
    }

    fun build() {
        coastline()
        terraces()
        docks()
        merchantHouse(-7, 7, -24, -12, 40, 1, false)
        merchantHouse(-25, -11, -17, -3, 40, 2, false)
        merchantHouse(14, 25, -19, -5, 40, 2, true)
        merchantHouse(-24, -10, 5, 14, 40, 2, false, true)
        merchantHouse(10, 24, 5, 14, 40, 2, true)
        grandHall()
        merchantHouse(-39, -29, -24, -11, 42, 2, false)
        merchantHouse(29, 39, -29, -16, 44, 2, true)
        merchantHouse(-39, -29, -4, 7, 40, 1, true, true)
        merchantHouse(29, 39, -3, 9, 40, 1, false)
        upperStairs()
        foundry()
        waterfront()
        occupiedStoreys()
        streetFurniture()
        planting()
        ship(-29, 34, Block.RED_WOOL, 1)
        ship(30, 36, Block.CYAN_WOOL, -1)
        connectDetails()
    }

    private fun coastline() {
        for (x in -55..55) for (z in -62..22) {
            val width = 47.0 + 3 * sin(z * .19) - if (z > 10) (z - 10) * 1.05 else 0.0
            val shape = x * x / (width * width) + (z + 18.0) * (z + 18.0) / (49.0 * 49.0)
            if (shape > 1.02 + sin(x * .51 + z * .17) * .035) continue
            val top = when {
                z < -29 -> 44 + ((-z - 29) / 4).coerceAtMost(7)
                abs(x) > 40 -> 39 + ((42 - abs(x)) / 2).coerceAtLeast(-2)
                else -> 40
            }
            for (y in 34..top) put(x, y, z, when {
                y == top && (abs(x) > 39 || z < -48) -> Block.GRASS_BLOCK
                y == top && abs(x) < 28 && z > -27 -> Block.STONE_BRICKS
                Math.floorMod(x / 3 + y / 2 + z / 4, 9) < 2 -> Block.ANDESITE
                else -> Block.STONE
            })
        }
        for (x in -29..29) for (z in -27..17) {
            for (y in 35..39) put(x, y, z, stone(x, y, z))
            put(x, 40, z, if (Math.floorMod(x * 3 + z * 5, 19) < 3) Block.ANDESITE else Block.STONE_BRICKS)
        }
        box(-29, 29, 37, 39, 18, 19, Block.STONE_BRICKS)
        box(-29, 29, 40, 40, 18, 19, Block.SMOOTH_SANDSTONE)
        for (x in -28..28 step 7) {
            box(x, x + 1, 35, 39, 20, 20, Block.CHISELED_STONE_BRICKS)
            if (abs(x) > 4) box(x, x + 1, 41, 41, 19, 19, Block.SANDSTONE_WALL)
        }
    }

    private fun terraces() {
        // Every square of the great hall bears on a full-depth stone podium.
        for (x in -16..16) for (z in -50..-28) for (y in 36..51) put(x, y, z, stone(x, y, z))
        box(-17, 17, 52, 52, -51, -27, Block.SMOOTH_SANDSTONE)
        for (x in -16..16 step 4) {
            box(x, x + 1, 40, 50, -27, -26, Block.STONE_BRICKS)
            box(x, x + 1, 50, 51, -27, -26, Block.CHISELED_STONE_BRICKS)
        }
        for (x in -14..14 step 4) {
            box(x, x + 1, 44, 48, -28, -28, Block.DEEPSLATE_BRICKS)
            box(x, x + 1, 49, 49, -28, -28, stair(Block.STONE_BRICK_STAIRS, "north"))
        }
        box(-17, 17, 50, 50, -28, -27, Block.POLISHED_ANDESITE)
        for ((x1, x2, z1, z2, y) in listOf(
            listOf(-40, -28, -25, -10, 42), listOf(28, 40, -30, -15, 44),
            listOf(-40, -28, -5, 9, 40), listOf(28, 40, -4, 10, 40))) {
            box(x1, x2, 35, y - 1, z1, z2, Block.STONE_BRICKS)
            box(x1, x2, y, y, z1, z2, Block.SMOOTH_SANDSTONE)
        }
    }

    /** Open shop arcade, inset glazed upper floor, projecting joists, close-grained timber frame. */
    private fun merchantHouse(x1: Int, x2: Int, z1: Int, z2: Int, floor: Int, storeys: Int, copper: Boolean, transverse: Boolean = false) {
        val mid = (x1 + x2) / 2
        val eave = floor + if (storeys == 2) 11 else 6
        box(x1, x2, floor, floor, z1, z2, Block.SPRUCE_PLANKS)
        for (x in listOf(x1, x2)) {
            box(x, x, floor + 1, eave - 1, z1, z2, plaster)
            box(x, x, floor + 1, floor + 1, z1, z2, Block.STONE_BRICKS)
            for (z in z1..z2 step 4) {
                box(x, x, floor + 1, eave, z, z, timber)
                val outX = x + if (x == x1) -1 else 1
                put(outX, eave - 1, z, stair(Block.DARK_OAK_STAIRS, if (x == x1) "east" else "west", true))
            }
            for (z in z1 + 2 until z2 - 1 step 4) {
                for (wy in listOf(floor + 3) + if (storeys == 2) listOf(floor + 8) else emptyList()) {
                    box(x, x, wy, wy + 1, z, z + 1, Block.LIGHT_BLUE_STAINED_GLASS)
                    val outX = x + if (x == x1) -1 else 1
                    box(outX, outX, wy - 1, wy - 1, z, z + 1, Block.DARK_OAK_SLAB)
                    put(x, wy + 2, z, logZ())
                }
            }
        }
        box(x1, x2, floor + 1, eave - 1, z1, z1, plaster)
        for (x in x1..x2 step 4) box(x, x, floor + 1, eave, z1, z1, timber)
        // Keep the original north-side storage/supplies approaches as well as the new quay arcade.
        if (floor == 40 && z1 == 5) box(mid - 2, mid + 2, 41, 44, z1, z1, Block.AIR)
        for (y in listOf(floor + 5, eave)) {
            for (z in listOf(z1, z2)) box(x1, x2, y, y, z, z, logX())
            for (x in listOf(x1, x2)) box(x, x, y, y, z1, z2, logZ())
        }
        for (x in listOf(x1, x1 + 4, x2 - 4, x2).distinct()) {
            box(x, x, floor + 1, floor + 1, z2, z2, Block.STONE_BRICKS)
            box(x, x, floor + 2, floor + 5, z2, z2, timber)
            for (dx in listOf(-1, 1)) if (x + dx in x1..x2)
                put(x + dx, floor + 4, z2, stair(Block.DARK_OAK_STAIRS, if (dx < 0) "east" else "west", true))
        }
        if (storeys == 2) {
            box(x1 + 1, x2 - 1, floor + 6, floor + 6, z1 + 1, z2 + 1, Block.SPRUCE_PLANKS)
            box(x1, x2, floor + 7, eave - 1, z2, z2, plaster)
            for (x in x1..x2 step 4) box(x, x, floor + 7, eave, z2, z2, timber)
            for (x in x1 + 2 until x2 - 1 step 4) frontWindow(x, floor + 8, z2)
            box(x1 + 1, x2 - 1, floor + 6, floor + 6, z2 + 1, z2 + 2, Block.SPRUCE_PLANKS)
            for (x in x1 + 1 until x2 step 3)
                put(x, floor + 5, z2 + 1, stair(Block.DARK_OAK_STAIRS, "north", true))
            for (x in x1 + 1 until x2) put(x, floor + 7, z2 + 2, Block.DARK_OAK_FENCE)
        }
        if (transverse) {
            // Warehouse ridge runs along the waterfront, breaking the paired shop-gable symmetry.
            val mz = (z1 + z2) / 2; val half = (z2 - z1) / 2 + 1
            for (z in z1..z2) for (y in eave + 1..eave + roofRise(half, abs(z - mz))) {
                val b = if (z == mz || y == eave + 1) beam else plaster
                put(x1,y,z,b); put(x2,y,z,b)
            }
            gableRoof(z1 - 1, z2 + 1, x1 - 1, x2 + 1, eave + 1, copper, true, true)
            // Small loading dormer, deliberately offset from the street centre.
            val dormer = x1 + 4
            box(dormer - 2, dormer + 2, eave, eave + 3, z2, z2 + 1, plaster)
            for (x in listOf(dormer - 2,dormer + 2)) box(x,x,eave,eave+4,z2+1,z2+1,timber)
            frontWindow(dormer - 1, eave + 1, z2 + 1)
            gableRoof(dormer - 3,dormer + 3,z2-2,z2+2,eave+4,copper,false)
        } else {
            val half = (x2 - x1) / 2 + 1
            for (x in x1..x2) for (y in eave + 1..eave + roofRise(half, abs(x - mid))) {
                val b = if (x == mid || y == eave + 1 || abs(x - mid) % 4 == 0) beam else plaster
                put(x, y, z2, b); put(x, y, z1, b)
            }
            frontWindow(mid - 1, eave + 2, z2)
            gableRoof(x1 - 1, x2 + 1, z1 - 1, z2 + 2, eave + 1, copper, true)
        }
        box(x1 + 1, x1 + 1, floor + 4, floor + 4, z2 + 1, z2 + 2, logZ())
        put(x1 + 1, floor + 3, z2 + 2, Block.LANTERN.withProperty("hanging", "true"))
    }

    private fun frontWindow(x: Int, y: Int, z: Int) {
        box(x, x + 1, y, y + 1, z, z, Block.LIGHT_BLUE_STAINED_GLASS)
        box(x, x + 1, y - 1, y - 1, z + 1, z + 1, Block.DARK_OAK_SLAB)
        for (sx in listOf(x - 1, x + 2)) box(sx, sx, y, y + 1, z + 1, z + 1,
            Block.SPRUCE_TRAPDOOR.withProperty("facing", "south").withProperty("open", "true"))
    }

    private fun roofRise(half: Int, distance: Int): Int {
        val inset = (half - distance).coerceAtLeast(0)
        if (half >= 12) return when {
            inset <= 2 -> 0
            inset <= 8 -> (inset - 1) / 2
            else -> inset - 5
        }
        return if (inset <= 2) inset / 2 else inset - 1
    }
    private fun gableRoof(x1: Int, x2: Int, z1: Int, z2: Int, base: Int, copper: Boolean, ribs: Boolean, transpose: Boolean = false) {
        fun roofPut(x: Int,y: Int,z: Int,b: Block) = if (transpose) put(z,y,x,b) else put(x,y,z,b)
        val mid = (x1 + x2) / 2; val half = (x2 - x1) / 2
        for (x in x1..x2) for (z in z1..z2) {
            val y = base + roofRise(half, abs(x - mid))
            val edge = z == z1 || z == z2
            val facing = if (transpose) { if (x < mid) "south" else "north" } else if (x < mid) "east" else "west"
            roofPut(x, y, z, when {
                x == mid -> if (edge) Block.DARK_OAK_PLANKS else Block.CUT_COPPER
                edge || (ribs && (z - z1) % 7 == 0) -> stair(Block.DARK_OAK_STAIRS, facing)
                copper -> stair(Block.CUT_COPPER_STAIRS, facing)
                else -> stair(Block.BRICK_STAIRS, facing)
            })
            roofPut(x, y - 1, z, if (edge) Block.DARK_OAK_PLANKS else if (copper) Block.CUT_COPPER else Block.BRICKS)
            if (abs(x - mid) == half) roofPut(x, y - 1, z, stair(Block.DARK_OAK_STAIRS, facing, true))
        }
        val ridge = base + roofRise(half,0) + 1
        for (z in z1 - 1..z2 + 1) roofPut(mid,ridge,z,if (transpose) logX() else logZ())
        for (z in listOf(z1 - 1,z2 + 1)) for (y in ridge + 1..ridge + 2) roofPut(mid,y,z,Block.DARK_OAK_FENCE)
    }

    private fun grandHall() {
        val x1 = -13; val x2 = 13; val z1 = -48; val z2 = -31
        box(-15, 15, 52, 52, -49, -28, Block.SPRUCE_PLANKS)
        for (x in listOf(x1, x2)) {
            box(x, x, 53, 61, z1, z2, plaster)
            for (z in z1..z2 step 4) {
                box(x, x, 53, 63, z, z, timber)
                val outer = x + if (x < 0) -2 else 2
                box(outer, outer, 53, 62, z, z, timber)
            }
            for (z in z1 + 1 until z2 - 1 step 4) box(x, x, 55, 58, z, z + 1, Block.ORANGE_STAINED_GLASS)
            for (y in listOf(59, 62)) box(x, x, y, y, z1, z2, logZ())
        }
        for (z in listOf(z1, z2)) {
            box(x1, x2, 53, 62, z, z, plaster)
            for (x in -12..12 step 4) box(x, x, 53, 63, z, z, timber)
            for (x in listOf(-10, -6, 5, 9)) frontWindow(x, 56, z)
            for (y in listOf(59, 62)) box(-13, 13, y, y, z, z, logX())
            box(-2, 2, 53, 58, z, z, Block.AIR)
            for (x in -13..13) for (y in 63..63 + roofRise(15, abs(x)))
                put(x, y, z, if (x == 0 || y == 65 || y == 69 || abs(x) % 5 == 0) beam else plaster)
        }
        gableRoof(-16, 16, -51, -28, 63, true, true)
        for (x in -3..3) for (y in 65..71) if (abs(x) + abs(y - 68) <= 4)
            put(x, y, z2, if (x == 0 || y == 68) beam else Block.ORANGE_STAINED_GLASS)
        for (x in listOf(-4, 4)) {
            put(x, 53, -27, Block.CHISELED_STONE_BRICKS)
            box(x, x, 54, 59, -27, -27, timber)
            box(x, x, 59, 59, -31, -27, logZ())
        }
        gableRoof(-5, 5, -31, -26, 60, false, false)
        box(-3, 3, 59, 59, -27, -27, logX())
        for (x in listOf(-3, 3)) put(x, 58, -28, Block.LANTERN.withProperty("hanging", "true"))
        for (x in -16..16) if (x !in -4..4 && x !in 8..12) put(x, 53, -27, Block.DARK_OAK_FENCE)
        for (z in -49..-28) for (x in listOf(-16, 16)) put(x, 53, z, Block.DARK_OAK_FENCE)
        for (x in listOf(-12, -6, 6, 12)) put(x, 58, -30, Block.RED_WALL_BANNER.withProperty("facing", "south"))
        for (x in -16..16) put(x,53,-50,Block.DARK_OAK_FENCE)
        bellTower(-22, -39)
        for (x in listOf(-9, 9)) for (z in listOf(-43, -37)) {
            box(x - 1, x + 1, 53, 53, z, z + 1, Block.SPRUCE_FENCE)
            box(x - 1, x + 1, 54, 54, z, z + 1, Block.SPRUCE_SLAB)
            for (dx in listOf(-2, 2)) box(x + dx, x + dx, 53, 53, z, z + 1,
                stair(Block.SPRUCE_STAIRS, if (dx < 0) "east" else "west"))
        }
    }

    private fun bellTower(cx: Int, cz: Int) {
        box(cx - 4, cx + 4, 36, 53, cz - 4, cz + 4, Block.STONE_BRICKS)
        box(cx - 3, cx + 3, 54, 68, cz - 3, cz + 3, plaster)
        box(cx - 2, cx + 2, 54, 73, cz - 2, cz + 2, Block.AIR)
        for (x in listOf(cx - 3, cx + 3)) for (z in listOf(cz - 3, cz + 3)) box(x, x, 54, 74, z, z, timber)
        for (y in listOf(59, 65, 69, 74)) {
            for (z in listOf(cz - 3, cz + 3)) box(cx - 4, cx + 4, y, y, z, z, logX())
            for (x in listOf(cx - 3, cx + 3)) box(x, x, y, y, cz - 4, cz + 4, logZ())
        }
        frontWindow(cx - 1, 61, cz + 3)
        box(cx - 1, cx + 1, 71, 72, cz, cz, Block.GOLD_BLOCK)
        box(cx, cx, 73, 74, cz, cz, Block.IRON_CHAIN)
        for (level in 0..5) {
            val half = (5 - level).coerceAtLeast(1)
            box(cx - half, cx + half, 75 + level, 75 + level, cz - half, cz + half, Block.CUT_COPPER)
        }
        box(cx, cx, 81, 84, cz, cz, Block.DARK_OAK_FENCE)
    }

    private fun upperStairs() {
        for (step in 0..11) {
            val z = -15 - step; val y = 41 + step
            box(8, 12, 35, y - 1, z, z, Block.STONE_BRICKS)
            box(8, 12, y, y, z, z, stair(Block.STONE_BRICK_STAIRS, "north"))
            box(8, 12, y + 1, y + 4, z, z, Block.AIR)
            for (x in listOf(7,13)) {
                box(x,x,35,y,z,z,Block.STONE_BRICKS)
                put(x,y+1,z,Block.STONE_BRICK_WALL)
            }
        }
        box(8, 12, 52, 52, -30, -27, Block.SPRUCE_PLANKS)
        box(8, 12, 53, 55, -30, -27, Block.AIR)
        for (step in 0..3) {
            box(29, 33, 35, 40 + step, -11 - step, -11 - step, Block.STONE_BRICKS)
            box(29, 33, 41 + step, 41 + step, -11 - step, -11 - step, stair(Block.STONE_BRICK_STAIRS, "north"))
            box(29, 33, 42 + step, 45 + step, -11 - step, -11 - step, Block.AIR)
        }
        for (step in 0..1) {
            box(-35, -31, 35, 40 + step, -7 - step, -7 - step, Block.STONE_BRICKS)
            box(-35, -31, 41 + step, 41 + step, -7 - step, -7 - step, stair(Block.STONE_BRICK_STAIRS, "north"))
            box(-35, -31, 42 + step, 45 + step, -7 - step, -7 - step, Block.AIR)
        }
        box(-35,-31,35,41,-9,-9,Block.STONE_BRICKS)
        box(-35,-31,42,42,-9,-9,Block.SMOOTH_SANDSTONE)
        box(-35,-31,43,45,-9,-9,Block.AIR)
    }

    private fun foundry() {
        box(-27, -24, 36, 62, -17, -14, Block.STONE_BRICKS)
        for (y in listOf(43, 51, 59, 62)) box(-28, -23, y, y, -18, -13, Block.POLISHED_ANDESITE)
        box(-26, -25, 60, 63, -16, -15, Block.AIR)
        for (x in -26..-25) put(x, 62, -15, Block.CAMPFIRE)
        box(-24, -21, 41, 42, -11, -9, Block.DEEPSLATE_BRICKS)
        box(-24, -21, 43, 44, -11, -11, Block.BRICKS)
        put(-23, 43, -10, Block.CAMPFIRE)
        box(-24, -21, 45, 45, -11, -9, Block.BRICK_SLAB)
    }

    private fun docks() {
        deck(-4, 4, 18, 39); deck(-13, 13, 32, 37); deck(-39, 39, 21, 24)
        for (z in listOf(21, 27, 33, 38)) for (x in listOf(-4, 4)) pile(x, z, z == 21 || z == 38)
        for (x in -38..38 step 8) if (abs(x) > 4) { pile(x, 24, false); pile(x, 21, false) }
        for (x in listOf(-13, 13)) for (z in listOf(32, 37)) pile(x, z, true)
        for (z in 23..37 step 2) for (x in listOf(-4, 4)) put(x, 41, z, Block.SPRUCE_SLAB)
        // Braced cargo crane and a continuous vertical chain. No unsupported diagonal chain pixels.
        box(-12, -11, 35, 49, 23, 24, timber)
        box(-12, -11, 49, 49, 22, 30, logZ())
        for (i in 0..4) box(-12, -11, 44 + i, 44 + i, 23 + i, 24 + i, Block.DARK_OAK_PLANKS)
        box(-11, -11, 43, 48, 30, 30, Block.IRON_CHAIN)
        put(-11, 42, 30, Block.IRON_BLOCK)
        box(-19, -16, 41, 41, 21, 23, Block.BARREL)
        box(-18, -17, 42, 42, 22, 23, Block.BARREL)
    }
    private fun deck(x1: Int, x2: Int, z1: Int, z2: Int) {
        for (x in x1..x2) for (z in z1..z2) put(x, 40, z,
            if (x == x1 || x == x2) logZ() else if (z == z1 || z == z2) logX() else Block.SPRUCE_PLANKS)
        for (z in z1..z2 step 5) box(x1, x2, 39, 39, z, z, logX())
    }
    private fun pile(x: Int, z: Int, light: Boolean) {
        box(x, x, 34, 41, z, z, timber); put(x, 38, z, Block.DARK_OAK_LOG)
        if (light) put(x, 42, z, Block.LANTERN)
    }

    private fun waterfront() {
        awning(-23, -11, 15, 17, 45, Block.RED_WOOL)
        awning(11, 23, 15, 17, 45, Block.ORANGE_WOOL)
        for (x in listOf(-23, -17, -11, 11, 17, 23)) {
            box(x, x, 41, 45, 17, 17, timber)
            put(x, 44, 16, Block.LANTERN.withProperty("hanging", "true"))
        }
        awning(-8, -4, -8, -3, 45, Block.WHITE_WOOL)
        awning(4, 7, -8, -3, 45, Block.RED_WOOL)
        for (x in listOf(-8, -4, 4, 7)) for (z in listOf(-8, -3)) box(x, x, 41, 45, z, z, timber)
    }
    private fun awning(x1: Int, x2: Int, z1: Int, z2: Int, y: Int, cloth: Block) {
        for (x in x1..x2) for (z in z1..z2) put(x, y + if (z == z1) 1 else 0, z,
            if (Math.floorMod(x - x1, 4) < 2) cloth else Block.WHITE_WOOL)
        box(x1, x2, y - 1, y - 1, z2, z2, logX())
    }
    private fun streetFurniture() {
        for ((x, z) in listOf(-8 to 5, 8 to 5, -27 to 1, 27 to 1)) {
            put(x, 41, z, Block.CHISELED_STONE_BRICKS)
            box(x, x, 42, 45, z, z, timber); put(x, 46, z, Block.DARK_OAK_SLAB)
            val dx = if (x < 0) 1 else -1
            put(x + dx, 45, z, logX()); put(x + dx, 44, z, Block.LANTERN.withProperty("hanging", "true"))
        }
        for ((x, z) in listOf(-22 to 15, 20 to 16, -30 to 6, 32 to 7)) {
            box(x, x + 1, 41, 41, z, z + 1, Block.BARREL); put(x, 42, z, Block.BARREL)
        }
    }

    /** The front merchants are buildings to enter, not hollow upper facades. Both balconies are walkable. */
    private fun occupiedStoreys() {
        for (step in 0..5) {
            val y = 41 + step
            for ((x,z,facing) in listOf(Triple(-23+step,7,"east"),Triple(23-step,6,"west"))) {
                box(x,x,40,y-1,z,z+1,Block.SPRUCE_PLANKS)
                box(x,x,y,y,z,z+1,stair(Block.SPRUCE_STAIRS,facing))
                box(x,x,y+1,y+3,z,z+1,Block.AIR)
            }
        }
        // Side-alley doors reach the low end of the stairs without putting a stair foundation in the old north aisle.
        for ((x,z,dx) in listOf(Triple(-24,7,-1),Triple(24,6,1))) {
            box(x,x,41,43,z,z+1,Block.AIR)
            box(x+dx,x+dx,41,43,z,z+1,Block.AIR)
            box(x,x,44,44,z,z+1,logZ())
        }
        // Doorways replace complete window bays, preserving the external frame and balcony joists.
        for (x in listOf(-18,16)) {
            box(x,x+1,47,49,14,14,Block.AIR)
            for (post in listOf(x-1,x+2)) box(post,post,47,50,14,14,timber)
            box(x,x+1,50,50,14,14,logX())
            box(x,x+1,47,49,15,15,Block.AIR)
        }
        // A warehouse clerk's office and a merchant counting room have distinct useful-looking interiors.
        box(-13,-12,47,48,6,6,Block.BOOKSHELF)
        box(-13,-12,47,47,8,8,Block.SPRUCE_FENCE)
        box(-13,-12,48,48,8,8,Block.SPRUCE_SLAB)
        put(-13,49,8,Block.LANTERN)
        box(11,13,47,47,7,7,Block.BARREL)
        put(11,48,7,Block.BOOKSHELF)
        put(13,48,7,Block.LANTERN)
        // Stocked little stalls frame the public spine, without covering or narrowing it.
        for (x in listOf(-7,5)) {
            box(x,x+1,41,41,-6,-5,Block.BARREL)
            put(x,42,-6,if(x<0) Block.MELON else Block.HAY_BLOCK)
            put(x+1,42,-6,Block.FLOWER_POT)
        }
    }
    private fun planting() {
        for ((x, y, z, lean) in listOf(
            listOf(-37, 43, -32, -1), listOf(36, 46, -39, 1), listOf(-42, 40, 0, -1),
            listOf(43, 40, -8, 1), listOf(15, 50, -54, 1), listOf(-9, 50, -55, -1))) {
            box(x, x, y - 4, y + 6, z, z, Block.JUNGLE_LOG)
            for (i in 0..5) put(x + lean * (i / 2), y + 6 + i / 2, z - i / 3, Block.JUNGLE_LOG)
            for (dx in -5..5) for (dz in -4..4) for (dy in -1..3) {
                if (dx * dx / 25.0 + dz * dz / 18.0 + dy * dy / 8.0 < 1 + sin(dx * 2.1 + dz * 3.2) * .16)
                    put(x + lean * 2 + dx, y + 9 + dy, z - 1 + dz, leaves)
            }
        }
        for (x in listOf(-18, 18)) for (z in -46..-33 step 3) {
            box(x,x,36,51,z,z,Block.STONE_BRICKS)
            put(x, 52, z, Block.MOSS_BLOCK)
            put(x, 53, z, Block.FLOWERING_AZALEA_LEAVES.withProperty("persistent", "true"))
        }
    }

    private fun ship(cx: Int, cz: Int, sail: Block, side: Int) {
        for (dz in -13..13) {
            val half = when (abs(dz)) { 13 -> 0; 12 -> 1; 11 -> 2; 10 -> 3; else -> 4 }
            for (y in 36..40) {
                val width = (half - (39 - y).coerceAtLeast(0) / 2).coerceAtLeast(0)
                for (dx in -width..width) put(cx + dx, y, cz + dz,
                    if (y == 40 && abs(dx) < width) Block.SPRUCE_PLANKS else Block.DARK_OAK_PLANKS)
            }
            for (dx in listOf(-half, half)) {
                put(cx + dx, 41, cz + dz, stair(Block.SPRUCE_STAIRS, if (dx < 0) "east" else "west"))
                if (abs(dz) > 9) put(cx + dx, 42, cz + dz, Block.SPRUCE_SLAB)
            }
        }
        box(cx - 3, cx + 3, 41, 43, cz + 6, cz + 10, Block.SPRUCE_PLANKS)
        box(cx - 2, cx + 2, 41, 42, cz + 6, cz + 9, Block.AIR)
        box(cx - 2, cx + 2, 42, 42, cz + 10, cz + 10, Block.ORANGE_STAINED_GLASS)
        box(cx - 3, cx + 3, 44, 44, cz + 6, cz + 10, Block.DARK_OAK_SLAB)
        for (mastZ in listOf(cz - 4, cz + 4)) {
            val top = if (mastZ < cz) 59 else 55
            box(cx, cx, 40, top + 1, mastZ, mastZ, timber)
            box(cx - 5, cx + 5, top, top, mastZ, mastZ, logX())
            for (dy in 1..8) for (dx in -4..4) {
                if (dy > 6 && abs(dx) > 3) continue
                val bulge = (2 * sin(dy * Math.PI / 9) * cos(dx * .22)).toInt()
                put(cx + dx, top - dy, mastZ + side * (1 + bulge), if (abs(dx) < 2) sail else Block.WHITE_WOOL)
            }
            val endZ = if (mastZ < cz) cz - 11 else cz + 11
            var previousZ = mastZ
            for (y in top downTo 41) {
                val targetZ = mastZ + ((endZ - mastZ) * (top - y).toDouble() / (top - 41)).toInt()
                for (z in minOf(previousZ,targetZ)..maxOf(previousZ,targetZ)) put(cx,y,z,Block.DARK_OAK_FENCE)
                previousZ = targetZ
            }
            for (x in cx - 3..cx + 3) put(x,top-9,mastZ + side,Block.DARK_OAK_FENCE)
        }
        box(cx, cx, 42, 42, cz - 17, cz - 12, logZ())
        for (x in listOf(cx - 3, cx + 3)) put(x, 45, cz + 9, Block.LANTERN)
    }

    /** Minestom placement does not run Vanilla neighbour updates: author all rail connections explicitly. */
    private fun connectDetails() {
        val directions = listOf(Triple("north",0,-1),Triple("south",0,1),Triple("east",1,0),Triple("west",-1,0))
        for (x in -55..55) for (z in -62..50) for (y in 38..85) {
            var b = instance.getBlock(x,y,z)
            val wall = b.name().endsWith("_wall")
            if (!wall && !b.name().endsWith("_fence") && !b.name().endsWith("_pane")) continue
            for ((name,dx,dz) in directions) {
                val adjacent = instance.getBlock(x+dx,y,z+dz)
                val connected = adjacent.isSolid && !adjacent.name().contains("leaves")
                b = b.withProperty(name,if(wall) { if(connected) "low" else "none" } else connected.toString())
            }
            put(x,y,z,b)
        }
    }
}
