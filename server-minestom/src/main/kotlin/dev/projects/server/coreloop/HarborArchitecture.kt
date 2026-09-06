package dev.projects.server.coreloop

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.math.abs
import kotlin.math.sin

/** Authored procedural architecture: public courtyards remain on y40; silhouette grows behind them. */
internal class HarborArchitecture(private val instance: InstanceContainer) {
    private fun put(x: Int, y: Int, z: Int, b: Block) = instance.setBlock(x, y, z, b)
    private fun box(x1: Int, x2: Int, y1: Int, y2: Int, z1: Int, z2: Int, b: Block) {
        for (x in x1..x2) for (z in z1..z2) for (y in y1..y2) put(x, y, z, b)
    }
    fun build() {
        // Worn working courtyards, not a grid of buildings on a lawn.
        for (x in -28..28) for (z in -26..17) {
            if (abs(x) < 24 || z > -8) put(x, 40, z,
                if (Math.floorMod(x * 13 + z * 7, 17) < 3) Block.GRAVEL else if (Math.floorMod(x + z * 3, 11) < 3) Block.COBBLESTONE else Block.STONE_BRICKS)
        }
        hall(-7, 7, -24, -12, Block.MUD_BRICKS, Block.BRICKS, 7)
        hall(-25, -11, -17, -3, Block.SANDSTONE, Block.DARK_OAK_PLANKS, 5)
        hall(11, 25, -19, -5, Block.CALCITE, Block.CUT_COPPER, 6)
        canopy(-23, -10, 5, 13, Block.CYAN_WOOL)
        canopy(10, 24, 5, 13, Block.ORANGE_WOOL)
        // Foundry tower and visible hot hearth, distinct from the timber meeting hall.
        box(-25, -22, 41, 55, -17, -14, Block.STONE_BRICKS)
        box(-25, -22, 53, 53, -17, -14, Block.POLISHED_ANDESITE)
        put(-24, 56, -16, Block.CAMPFIRE)
        box(-24, -22, 41, 41, -9, -7, Block.DEEPSLATE_BRICKS)
        put(-23, 42, -8, Block.CAMPFIRE)
        // North cliff district gives the town depth, rather than five isolated shop sheds.
        for (x in -43..43) for (z in -55..-28) {
            val edge = x * x / (44.0 * 44.0) + (z + 35) * (z + 35) / (23.0 * 23.0)
            if (edge > 1.0 + sin(x * .6 + z) * .035) continue
            val top = 40 + ((-z - 27) / 4 + (abs(x) / 12)).coerceAtMost(9)
            for (y in 34..top) put(x, y, z, if (y == top) Block.MOSS_BLOCK else if ((x + y + z) % 7 == 0) Block.ANDESITE else Block.STONE)
        }
        // A grand ribbed roof is the principal landmark above the expedition entrance.
        for (z in -43..-27) for (x in -12..12) {
            val roofY = 64 - abs(x) / 2
            put(x, roofY, z, if (z % 4 == 0) Block.DARK_OAK_LOG else Block.BRICKS)
            if (abs(x) == 12) put(x, roofY - 1, z, Block.DARK_OAK_STAIRS)
        }
        for (x in listOf(-11, 11)) for (z in listOf(-42, -35, -28)) box(x, x, 44, 58, z, z, Block.STRIPPED_DARK_OAK_LOG)
        for (x in -10..7) put(x, 54, -28, Block.SPRUCE_FENCE)
        box(-11, 11, 52, 52, -42, -28, Block.SPRUCE_PLANKS)
        box(-10, 10, 53, 57, -42, -42, Block.SANDSTONE)
        for (x in listOf(-11,-6,0,6,11)) box(x,x,41,51,-28,-28,Block.STONE_BRICKS)
        box(-11,11,50,51,-28,-28,Block.STONE_BRICKS)
        for (x in listOf(-10,-6,6,10)) {
            box(x,x,55,57,-27,-27,Block.RED_WOOL)
            put(x,54,-27,Block.YELLOW_WOOL)
        }
        for (x in listOf(-7, -3, 3, 7)) box(x, x + 1, 54, 56, -42, -42, Block.ORANGE_STAINED_GLASS)
        // Real stair access at the sides; no jump-only climb is used by the main facilities.
        for (step in 0..11) for (x in 8..10) {
            val z = -15 - step
            put(x, 40 + step, z, Block.SPRUCE_STAIRS.withProperty("facing", "north"))
            for (y in 41 + step..43 + step) put(x, y, z, Block.AIR)
        }
        box(8, 10, 52, 52, -28, -27, Block.SPRUCE_PLANKS)
        // Small attached workshops and balconies at the perimeter, with a clear central loop.
        for ((x, z) in listOf(-35 to -23, 33 to -28, -35 to 0, 34 to 1)) {
            for (dx in -5..5) for (dz in -5..5) box(x + dx, x + dx, 35, 40, z + dz, z + dz, Block.STONE_BRICKS)
            hall(x - 4, x + 4, z - 5, z + 4, Block.PACKED_MUD, if (x < 0) Block.DARK_OAK_PLANKS else Block.BRICKS, 4)
        }
        for (x in listOf(-28,28)) for (z in -25..15) put(x,40,z,Block.SPRUCE_PLANKS)
        // Cargo, workshop lean-tos and planted pockets live at edges, away from facility approaches.
        for ((x,z) in listOf(-27 to 3,-26 to 14,26 to 14,27 to -1,-8 to -24,8 to -24,-31 to 17,31 to 17)) {
            box(x,x+1,41,41,z,z+1,Block.BARREL); put(x,42,z,Block.BARREL)
            put(x+1,42,z+1,Block.FLOWER_POT)
        }
        canopy(-32,-27,-15,-7,Block.RED_WOOL)
        canopy(27,32,-19,-12,Block.CYAN_WOOL)
        for ((x,z) in listOf(-30 to -30,30 to -36,-40 to -13,40 to -12)) {
            box(x,x,41,47,z,z,Block.JUNGLE_LOG)
            for (dx in -3..3) for (dz in -3..3) if (abs(dx)+abs(dz)<5) put(x+dx,48-abs(dx)/2,z+dz,Block.JUNGLE_LEAVES.withProperty("persistent","true"))
        }
        rigging()
        ship(-32, 32, Block.RED_WOOL)
        ship(30, 33, Block.BLUE_WOOL)
    }
    private fun hall(x1: Int, x2: Int, z1: Int, z2: Int, wall: Block, roof: Block, rise: Int) {
        box(x1, x2, 40, 40, z1, z2, Block.SPRUCE_PLANKS)
        box(x1, x2, 41, 46, z1, z1, wall)
        box(x1, x1, 41, 46, z1, z2, wall); box(x2, x2, 41, 46, z1, z2, wall)
        for (x in listOf(x1, x2)) for (z in z1..z2 step 4) box(x, x, 41, 48, z, z, Block.STRIPPED_SPRUCE_LOG)
        for (x in listOf(x1,x2)) for (z in z1+2 until z2 step 4) {
            box(x,x,43,44,z,z+1,Block.LIGHT_BLUE_STAINED_GLASS)
            put(x,42,z,Block.SPRUCE_TRAPDOOR)
        }
        for (x in x1..x2) put(x, 46, z1, Block.DARK_OAK_LOG)
        for (x in x1 + 2 until x2 step 4) box(x, x + 1, 43, 44, z1, z1, Block.LIGHT_BLUE_STAINED_GLASS)
        val mid = (x1 + x2) / 2
        for (x in x1 - 1..x2 + 1) for (z in z1 - 1..z2 + 1) {
            val y = 47 + (rise - abs(x - mid)).coerceAtLeast(0)
            put(x, y, z, if (z == z1 - 1 || z == z2 + 1 || (z - z1) % 5 == 0) Block.DARK_OAK_LOG else roof)
            if (abs(x - mid) > rise) put(x, y - 1, z, Block.SPRUCE_TRAPDOOR)
        }
        for (x in listOf(x1 + 1, x2 - 1)) {
            box(x, x, 41, 46, z2, z2, Block.SPRUCE_FENCE)
            put(x, 45, z2 - 1, Block.LANTERN)
        }
        for (x in x1+2 until x2-1) if (abs(x-mid)>2) box(x,x,41,43,z2,z2,wall)
        // Five-block main entrance; the shops still have real front walls and glazed sides.
    }
    private fun canopy(x1: Int, x2: Int, z1: Int, z2: Int, cloth: Block) {
        for (x in x1..x2) for (z in z1..z2) put(x, 46 + if (z == z1 || z == z2) 1 else 0, z, if ((x - x1) % 4 < 2) cloth else Block.WHITE_WOOL)
        for (x in listOf(x1, x2)) for (z in listOf(z1, z2)) box(x, x, 41, 47, z, z, Block.SPRUCE_FENCE)
        for (x in x1 + 1 until x2 step 3) put(x, 45, z1, Block.SPRUCE_TRAPDOOR)
    }
    private fun rigging() {
        for (x in listOf(-28, 28)) {
            box(x, x, 35, 49, 17, 17, Block.STRIPPED_SPRUCE_LOG)
            put(x, 50, 17, Block.LANTERN)
        }
        for (i in 0..56) {
            val y = 49 - (5 * sin(i * Math.PI / 56)).toInt()
            put(i - 28, y, 17, if (i % 8 == 0) Block.LANTERN else Block.IRON_CHAIN)
        }
        // Promenade branches and mooring posts read as built, occupied working docks.
        for (x in -40..40) for (z in 20..23) put(x, 40, z, Block.SPRUCE_PLANKS)
        for (x in -40..40 step 6) { box(x, x, 35, 41, 23, 23, Block.STRIPPED_SPRUCE_LOG); put(x, 42, 23, Block.LANTERN) }
    }
    private fun ship(cx: Int, cz: Int, sail: Block) {
        for (z in -11..11) {
            val half = if (abs(z) > 8) 2 else 4
            box(cx - half, cx + half, 38, 39, cz + z, cz + z, Block.DARK_OAK_PLANKS)
            for (x in listOf(cx - half, cx + half)) box(x, x, 40, 41, cz + z, cz + z, Block.SPRUCE_PLANKS)
        }
        box(cx, cx, 40, 57, cz, cz, Block.STRIPPED_SPRUCE_LOG)
        for (y in 47..55) for (x in -5..5) put(cx + x, y, cz + if (abs(x) < 3) 1 else 0, if ((x + 5) % 4 < 2) sail else Block.WHITE_WOOL)
        for (x in -6..6) put(cx + x, 56, cz, Block.DARK_OAK_FENCE)
    }
}
