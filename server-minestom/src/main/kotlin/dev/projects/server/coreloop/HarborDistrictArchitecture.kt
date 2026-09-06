package dev.projects.server.coreloop

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan
import kotlin.math.sqrt

/** A built-up working waterfront below a stone acropolis. Existing facility coordinates are contracts. */
internal class HarborDistrictArchitecture(private val instance: InstanceContainer) {
    private val scenery = mutableListOf<Entity>()
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

    fun build(): List<Entity> {
        coastline()
        terraces()
        coastalRockAprons()
        docks()
        expeditionLoggia()
        academy()
        merchantHouse(-24, -10, 5, 14, 40, 2, false, true)
        merchantHouse(10, 24, 5, 14, 40, 2, true)
        grandHall()
        merchantHouse(-39, -29, -24, -11, 42, 2, false)
        merchantHouse(29, 39, -29, -16, 44, 2, true)
        merchantHouse(-39, -29, -4, 7, 40, 1, true, true)
        shipwright()
        upperStairs()
        foundry()
        waterfront()
        occupiedStoreys()
        tradingOriel()
        marketStreet()
        streetFurniture()
        planting()
        ship(-29, 42, Block.RED_WOOL, 1)
        fishingCutter(30, 36)
        boardingPiers()
        connectDetails()
        return scenery.toList()
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

    /** Rock shelves lap around the outer retaining walls, not across docks, facilities or street edges. */
    private fun coastalRockAprons() {
        val shelves = listOf(
            listOf(-43,-22,6,10),listOf(-44,-2,7,8),listOf(-42,12,5,7),
            listOf(43,-26,6,10),listOf(44,-5,7,9),listOf(42,12,5,7))
        for((cx,cz,rx,rz) in shelves) for(x in cx-rx..cx+rx) for(z in cz-rz..cz+rz) {
            if(abs(x)<41 || abs(x)>54 || z>19) continue
            val dx=(x-cx).toDouble()/rx; val dz=(z-cz).toDouble()/rz
            val contour=dx*dx+dz*dz+sin(x*.7+z*.23)*.1
            if(contour>1.0) continue
            val top=38+((1.0-contour)*5).toInt().coerceIn(0,5)
            for(y in 34..top) put(x,y,z,when {
                y==top && top>=41 && contour<.55 -> Block.GRASS_BLOCK
                y==top-1 && top>=41 && contour<.55 -> Block.DIRT
                (y+z/4)%4==0 -> Block.ANDESITE
                else -> Block.STONE
            })
            if(top>=41 && contour<.45 && (x+z)%5==0)
                put(x,top+1,z,Block.FLOWERING_AZALEA_LEAVES.withProperty("persistent","true"))
        }
    }

    /** A low public loggia leaves the raised great hall as the only central gable in the market vista. */
    private fun expeditionLoggia() {
        box(-7,7,40,40,-24,-12,Block.SPRUCE_PLANKS)
        box(-7,7,41,45,-24,-24,plaster)
        for(x in listOf(-7,7)) {
            box(x,x,41,43,-24,-12,plaster)
            box(x,x,41,41,-24,-12,Block.STONE_BRICKS)
            for(z in listOf(-24,-18,-12)) {
                box(x,x,41,41,z,z,Block.CHISELED_STONE_BRICKS)
                box(x,x,42,45,z,z,timber)
            }
            for(z in listOf(-22,-16)) box(x,x,43,44,z,z+2,Block.LIGHT_BLUE_STAINED_GLASS)
            box(x,x,45,45,-24,-12,logZ())
        }
        for(z in listOf(-24,-18,-12)) box(-7,7,45,45,z,z,logX())
        for(x in listOf(-3,3)) {
            put(x,41,-12,Block.CHISELED_STONE_BRICKS)
            box(x,x,42,45,-12,-12,timber)
            put(x + if(x<0) 1 else -1,44,-12,
                stair(Block.DARK_OAK_STAIRS,if(x<0) "west" else "east",true))
            put(x,44,-14,Block.LANTERN.withProperty("hanging","true"))
            box(x,x,45,45,-14,-12,logZ())
        }
        // A continuous half-block hipped slope, without a front dormer competing with the great hall.
        for(x in -8..8) for(z in -25..-10) {
            val inset = minOf(x+8,8-x,z+25,-10-z)
            val slab = if(inset==0) Block.DARK_OAK_SLAB else Block.CUT_COPPER_SLAB
            put(x,46+inset/2,z,slab.withProperty("type",if(inset%2==0) "bottom" else "top"))
        }
        box(-1,1,50,50,-18,-17,Block.DARK_OAK_SLAB)
        // The cartography counter is furnished later at its unchanged y41 / z-15 contract.
        for(x in listOf(-3,3)) box(x,x,41,43,-22,-22,timber)
    }

    /** An open lecture room joins a tall octagonal book room; neither is an inaccessible painted upper floor. */
    private fun academy() {
        box(14,22,40,40,-19,-5,Block.SPRUCE_PLANKS)
        for(x in listOf(14,22)) {
            box(x,x,41,49,-19,-5,plaster)
            box(x,x,41,42,-19,-5,Block.STONE_BRICKS)
            for(z in listOf(-19,-12,-5)) box(x,x,43,50,z,z,timber)
            box(x,x,49,49,-19,-5,logZ())
        }
        box(14,22,41,49,-19,-19,plaster)
        box(14,22,41,49,-5,-5,plaster)
        for(x in listOf(14,19,22)) box(x,x,41,50,-5,-5,timber)
        box(15,18,41,45,-5,-5,Block.AIR)
        box(15,18,46,46,-5,-5,logX())
        put(15,45,-5,stair(Block.DARK_OAK_STAIRS,"west",true))
        put(18,45,-5,stair(Block.DARK_OAK_STAIRS,"east",true))
        box(20,21,43,47,-5,-5,Block.LIGHT_BLUE_STAINED_GLASS)
        box(20,21,42,42,-4,-4,Block.DARK_OAK_SLAB)
        box(14,22,49,49,-5,-5,logX())
        // Large reading-light windows subdivide the exposed street wall; no shop balcony is needed here.
        for(z in listOf(-17,-10)) {
            box(14,14,44,47,z,z+3,Block.LIGHT_BLUE_STAINED_GLASS)
            box(14,14,44,47,z+1,z+1,Block.DARK_OAK_FENCE)
            box(14,14,46,46,z,z+3,Block.DARK_OAK_FENCE)
            box(14,14,48,48,z,z+3,logZ())
        }
        // Only the front sill projects: the rear window meets the protected great-hall stair wall.
        box(13,13,43,43,-10,-7,Block.DARK_OAK_SLAB)
        for(x in 14..22) for(y in 50..50+roofRise(5,abs(x-18)))
            put(x,y,-5,if(x==18 || y==50) beam else plaster)
        gableRoof(13,23,-20,-4,50,false,false)
        // The faceted room is hollow at player level and entered from the adjoining lecture room.
        val cx=23; val cz=-16
        for(dx in -4..4) for(dz in -4..4) {
            if(abs(dx)+abs(dz)>6) continue
            val rim=abs(dx)==4 || abs(dz)==4 || abs(dx)+abs(dz)==6
            box(cx+dx,cx+dx,35,40,cz+dz,cz+dz,Block.STONE_BRICKS)
            for(y in 41..57) put(cx+dx,y,cz+dz,when {
                !rim -> Block.AIR
                y<=43 -> stone(cx+dx,y,cz+dz)
                abs(dx)==3 && abs(dz)==3 -> timber
                y==49 || y==55 -> Block.DARK_OAK_PLANKS
                else -> plaster
            })
        }
        box(19,19,41,45,-17,-15,Block.AIR)
        box(19,19,46,46,-17,-15,logZ())
        for(d in listOf(-1,1)) {
            box(cx+d*4,cx+d*4,46,53,cz-1,cz+1,Block.LIGHT_BLUE_STAINED_GLASS)
            box(cx-1,cx+1,46,53,cz+d*4,cz+d*4,Block.LIGHT_BLUE_STAINED_GLASS)
            box(cx+d*4,cx+d*4,46,53,cz,cz,Block.DARK_OAK_FENCE)
            box(cx,cx,46,53,cz+d*4,cz+d*4,Block.DARK_OAK_FENCE)
        }
        box(20,26,56,56,cz,cz,logX())
        box(cx,cx,56,56,-19,-13,logZ())
        put(cx,55,cz,Block.LANTERN.withProperty("hanging","true"))
        // Small weathered-copper cap is secondary to the red great hall, but distinct in the skyline.
        for(level in 0..6) {
            val r=6-level
            for(dx in -r..r) for(dz in -r..r) if(abs(dx)+abs(dz)<=r+r/2) {
                val edge=abs(dx)==r || abs(dz)==r || abs(dx)+abs(dz)==r+r/2
                put(cx+dx,58+level,cz+dz,
                    if(edge) Block.WAXED_WEATHERED_CUT_COPPER_SLAB else Block.WAXED_WEATHERED_CUT_COPPER)
            }
        }
        put(cx,64,cz,Block.WAXED_WEATHERED_CUT_COPPER)
        box(cx,cx,65,67,cz,cz,Block.DARK_OAK_FENCE)
        box(20,25,41,43,-18,-18,Block.BOOKSHELF)
        box(25,25,41,43,-17,-14,Block.BOOKSHELF)
        put(22,41,-14,Block.SPRUCE_FENCE)
        put(22,42,-14,Block.SPRUCE_SLAB)
        put(22,43,-14,Block.LANTERN)
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
            if(z==z1) for (x in listOf(-10, -6, 5, 9)) frontWindow(x, 56, z)
            for (y in listOf(59, 62)) box(-13, 13, y, y, z, z, logX())
            box(-2, 2, 53, 58, z, z, Block.AIR)
            for (x in -13..13) for (y in 63..63 + roofRise(15, abs(x)))
                put(x, y, z, if(z==z2) Block.AIR else if (x == 0 || y == 65 || y == 69 || abs(x) % 5 == 0) beam else plaster)
        }
        gableRoof(-16, 16, -51, -28, 63, true, true)
        hallSeawardFront()
        // The old central porch opening led off the podium, not to stairs. Only the actual
        // east stair landing is an opening in the seaward balustrade.
        for (x in -16..16) if (x !in 8..12 && x !in listOf(-14,-7,-3,3,7,14))
            put(x, 53, -27, Block.DARK_OAK_FENCE)
        for (z in -49..-28) for (x in listOf(-16, 16)) put(x, 53, z, Block.DARK_OAK_FENCE)
        for (x in listOf(-12, -6, 6, 12)) put(x, 58, -30, Block.RED_WALL_BANNER.withProperty("facing", "south"))
        for (x in -16..16) put(x,53,-50,Block.DARK_OAK_FENCE)
        bellTower(-22, -39)
        for (x in listOf(-7, 7)) for (z in listOf(-43, -37)) {
            box(x - 1, x + 1, 53, 53, z, z + 1, Block.SPRUCE_FENCE)
            box(x - 1, x + 1, 54, 54, z, z + 1, Block.SPRUCE_SLAB)
            for (dx in listOf(-2, 2)) box(x + dx, x + dx, 53, 53, z, z + 1,
                stair(Block.SPRUCE_STAIRS, if (dx < 0) "east" else "west"))
        }
        hallInterior()
    }

    /** Open seaward gathering veranda and exposed fan truss, unlike the enclosed merchant houses. */
    private fun hallSeawardFront() {
        // Open side bays join the dining room to the existing gallery. Retain the central
        // five-block doorway and banner-bearing lintels, with no floating domestic shutters.
        for(xs in listOf(-11..-5,5..11)) {
            box(xs.first,xs.last,53,57,-31,-31,Block.AIR)
            box(xs.first,xs.last,58,58,-31,-31,logX())
            put(xs.first,57,-31,stair(Block.DARK_OAK_STAIRS,"west",true))
            put(xs.last,57,-31,stair(Block.DARK_OAK_STAIRS,"east",true))
        }
        // Front gable is a deep, open roof structure, not a larger version of the house windows.
        box(0,0,63,73,-31,-31,timber)
        for(side in listOf(-1,1)) for((endX,endY) in listOf(8 to 64,4 to 68)) {
            val steps=maxOf(endX,endY-63)
            for(i in 0..steps) {
                val x=side*(endX*i/steps); val y=63+(endY-63)*i/steps
                box(x,x,y,y+1,-31,-31,if(endX==8) Block.DARK_OAK_PLANKS else Block.STRIPPED_SPRUCE_WOOD)
            }
        }
        for(x in listOf(-2,2)) put(x,65,-31,Block.LANTERN.withProperty("hanging","true"))
        // Swept outer eaves widen the red roof silhouette. Brackets carry them from the
        // existing side-gallery columns, leaving the actual walking decks untouched.
        for(side in listOf(-1,1)) {
            for(z in -51..-28) {
                val rim=if(z in listOf(-51,-28)) Block.DARK_OAK_SLAB else Block.CUT_COPPER_SLAB
                put(side*17,63,z,rim.withProperty("type","top"))
                put(side*18,64,z,rim.withProperty("type","bottom"))
            }
            box(side*18,side*18,63,63,-49,-30,logZ())
            for(z in -48..-32 step 4) for(i in 1..3)
                box(side*(15+i),side*(15+i),59+i,60+i,z,z,Block.DARK_OAK_PLANKS)
        }
        // One broad shallow copper canopy replaces the little domestic entrance gable.
        // The side hips and half-block fall keep it subordinate to the great red roof.
        for(x in -16..16) for(z in -30..-25) {
            val inset=minOf(-25-z,16-abs(x))
            val roof=if(z==-25 || abs(x)==16) Block.DARK_OAK_SLAB else Block.CUT_COPPER_SLAB
            put(x,59+inset/2,z,roof.withProperty("type",if(inset%2==0) "bottom" else "top"))
        }
        box(-14,14,59,59,-27,-27,logX())
        for(x in listOf(-14,-7,-3,3,7,14)) {
            put(x,53,-27,Block.CHISELED_STONE_BRICKS)
            box(x,x,54,59,-27,-27,timber)
            for(z in -30..-25) {
                val y=59+minOf(-25-z,16-abs(x))/2
                put(x,y-1,z,Block.DARK_OAK_SLAB.withProperty("type","top"))
            }
            for(dx in listOf(-1,1)) if(abs(x+dx)<=14)
                put(x+dx,58,-27,stair(Block.DARK_OAK_STAIRS,if(dx<0) "east" else "west",true))
        }
        for(x in listOf(-5,5)) put(x,58,-27,Block.LANTERN.withProperty("hanging","true"))
    }

    private fun hallInterior() {
        // Three supported transverse frames, with continuous braces instead of diagonal floating blocks.
        for(z in listOf(-46,-40,-34)) {
            for(side in listOf(-1,1)) {
                put(side*11,53,z,Block.CHISELED_STONE_BRICKS)
                box(side*11,side*11,54,61,z,z,timber)
                for(i in 0..5) {
                    val x=side*(11-i)
                    box(x,x,59+i,60+i,z,z,Block.DARK_OAK_PLANKS)
                }
                box(minOf(side*11,side*13),maxOf(side*11,side*13),62,62,z,z,logX())
                put(side*10,58,z,logX())
                put(side*10,57,z,Block.LANTERN.withProperty("hanging","true"))
            }
            box(-6,6,64,64,z,z,logX())
            box(0,0,65,73,z,z,timber)
            // Rafters follow the underside of the built roof and meet the side-wall plate.
            for(x in -13..13) {
                val underside=62+roofRise(16,abs(x))
                box(x,x,underside-1,underside,z,z,if(x==0) timber else logX())
            }
        }
        box(0,0,64,64,-46,-34,logZ())
        for(z in listOf(-43,-37)) {
            box(0,0,61,63,z,z,Block.IRON_CHAIN)
            box(-2,2,60,60,z,z,Block.IRON_BARS)
            for(x in listOf(-2,2)) {
                box(x,x,60,60,z-1,z+1,Block.IRON_BARS)
                for(dz in listOf(-1,1)) put(x,59,z+dz,Block.LANTERN.withProperty("hanging","true"))
            }
            put(0,59,z,Block.LANTERN.withProperty("hanging","true"))
        }
        // Masonry hearth occupies the wall bay between the frames, separated from the dining tables.
        box(-12,-10,53,53,-43,-41,Block.DEEPSLATE_BRICKS)
        box(-12,-12,54,57,-43,-41,Block.STONE_BRICKS)
        for(z in listOf(-43,-41)) box(-11,-10,54,56,z,z,Block.STONE_BRICKS)
        put(-11,54,-42,Block.CAMPFIRE)
        box(-12,-10,57,58,-43,-41,Block.STONE_BRICKS)
        box(-12,-11,59,66,-43,-41,Block.STONE_BRICKS)
        box(-13,-10,67,67,-44,-40,Block.STONE_BRICK_SLAB)
        for(x in listOf(-4,4)) put(x,58,-47,Block.RED_WALL_BANNER.withProperty("facing","south"))
        // A planted strip beyond the rear balustrade replaces the bare cliff as the door's immediate view.
        box(-4,4,52,52,-51,-51,Block.DIRT)
        box(-4,4,53,55,-51,-51,leaves)
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
        // One high working room, not a merchant's balcony and repeated domestic windows.
        box(-25, -11, 40, 40, -17, -3, Block.POLISHED_ANDESITE)
        for (x in listOf(-25, -11)) for (z in -17..-7) for (y in 41..49)
            put(x, y, z, if (y < 45) stone(x,y,z) else plaster)
        for (x in -25..-11) for (y in 41..49)
            put(x,y,-17,if(y<45) stone(x,y,-17) else plaster)
        for (x in listOf(-25,-11)) {
            for (z in listOf(-17,-7)) {
                box(x,x,41,44,z,z,Block.STONE_BRICKS)
                box(x,x,45,50,z,z,timber)
            }
            box(x,x,44,47,-14,-11,Block.ORANGE_STAINED_GLASS)
            box(x,x,44,47,-13,-13,Block.IRON_BARS)
            box(x,x,46,46,-14,-11,Block.IRON_BARS)
            box(x,x,48,48,-14,-11,logZ())
        }
        box(-25,-11,49,49,-17,-17,logX())
        box(-25,-11,49,49,-7,-7,logX())
        // Transverse low roof: the broad stone arcade remains the dominant street-facing shape.
        for (z in -17..-7) for (y in 50..50+roofRise(6,abs(z+12)))
            for (x in listOf(-25,-11)) put(x,y,z,if(z==-12 || y==50) beam else plaster)
        gableRoof(-18,-6,-26,-10,50,false,false,true)
        for (x in -24..-12) {
            put(x,48,-7,Block.DARK_OAK_FENCE)
            put(x,47,-7,logX())
        }
        // Two six-block-wide masonry openings, with the workbenches behind, not inside, the piers.
        for (x in listOf(-25,-18,-11)) {
            box(x,x,41,44,-3,-3,Block.STONE_BRICKS)
            put(x,45,-3,Block.CHISELED_STONE_BRICKS)
            for (dx in listOf(-1,1)) if(x+dx in -25..-11)
                put(x+dx,45,-3,stair(Block.STONE_BRICK_STAIRS,if(dx>0) "west" else "east",true))
        }
        box(-25,-11,46,46,-3,-3,Block.STONE_BRICKS)
        for (cx in listOf(-22,-15)) {
            box(cx,cx+1,46,46,-2,-2,Block.CHISELED_STONE_BRICKS)
            put(cx,45,-5,Block.LANTERN.withProperty("hanging","true"))
            box(cx,cx,46,46,-7,-4,logZ())
        }
        // A shallow tiled fore-roof joins the main roof; half-block rises avoid a repeated sawtooth eave.
        for (z in -6..-1) for (x in -26..-10) {
            val rise = -1-z
            val material = if(x in listOf(-26,-10) || z==-1) Block.DARK_OAK_SLAB else Block.BRICK_SLAB
            put(x,47+rise/2,z,material.withProperty("type",if(rise%2==0) "bottom" else "top"))
        }
        // The forge hood and flue connect physically all the way to the chimney crown.
        box(-24,-21,41,42,-12,-9,Block.DEEPSLATE_BRICKS)
        box(-24,-21,43,44,-12,-12,Block.BRICKS)
        put(-23,43,-11,Block.CAMPFIRE)
        box(-24,-21,45,45,-12,-9,Block.BRICKS)
        box(-24,-22,46,47,-14,-10,Block.BRICKS)
        box(-26,-24,36,61,-17,-14,Block.BRICKS)
        for (y in listOf(43,50,57,61))
            box(-27,-23,y,y,-18,-13,Block.POLISHED_ANDESITE)
        box(-25,-25,47,61,-16,-15,Block.AIR)
        put(-25,61,-15,Block.CAMPFIRE)
        for (x in listOf(-27,-23)) for(z in listOf(-18,-13))
            put(x,62,z,Block.STONE_BRICK_WALL)
    }

    /** An open repair shed: its visible transverse frames carry a long, low clerestory roof. */
    private fun shipwright() {
        box(29, 40, 40, 40, -3, 11, Block.SPRUCE_PLANKS)
        for (z in listOf(-3, 3, 9)) {
            for (x in listOf(29, 40)) {
                box(x, x, 35, 41, z, z, Block.STONE_BRICKS)
                box(x, x, 42, 48, z, z, timber)
                val inward = if (x == 29) 1 else -1
                for (i in 1..3) put(x + inward * i, 44 + i, z,
                    stair(Block.DARK_OAK_STAIRS, if (inward > 0) "west" else "east", true))
            }
            box(29, 40, 48, 48, z, z, logX())
            box(34, 35, 49, 52, z, z, timber)
        }
        for (x in listOf(29, 40)) box(x, x, 48, 48, -4, 12, logZ())
        // Two shallow outer roof planes and a raised ventilation lantern instead of another house gable.
        for (x in 28..41) for (z in -4..12) {
            if (x in 33..36) continue
            val inset = minOf(x - 28, 41 - x)
            val y = 49 + inset / 2
            put(x, y, z, (if (z == -4 || z == 12) Block.DARK_OAK_SLAB else Block.BRICK_SLAB)
                .withProperty("type", if (inset % 2 == 0) "bottom" else "top"))
            put(x, y - 1, z, Block.SPRUCE_SLAB.withProperty("type", "top"))
        }
        for (x in listOf(33, 36)) {
            box(x, x, 51, 51, -3, 11, Block.DARK_OAK_FENCE)
            box(x, x, 52, 52, -4, 12, logZ())
        }
        for (x in 32..37) for (z in -5..13) {
            val inset = minOf(x - 32, 37 - x)
            put(x, 53 + inset / 2, z,
                (if (z == -5 || z == 13) Block.DARK_OAK_SLAB else Block.CUT_COPPER_SLAB)
                    .withProperty("type", if (inset % 2 == 0) "bottom" else "top"))
        }
        // A boat under construction, held off the floor by trestles; the side aisles stay open.
        box(34, 34, 41, 41, -1, 8, logZ())
        for (z in listOf(0, 3, 6)) {
            box(31, 37, 41, 41, z, z, logX())
            for (x in listOf(32, 36)) put(x, 42, z, timber)
            for (x in listOf(31, 37)) put(x, 43, z,
                stair(Block.SPRUCE_STAIRS, if (x < 34) "east" else "west"))
            for (x in listOf(30, 38)) put(x, 44, z, timber)
        }
        box(34, 34, 42, 44, -1, -1, timber)
        box(33, 35, 42, 43, 8, 8, Block.SPRUCE_PLANKS)
        // A supported gantry reaches the open seaward end, with its lifting tackle above the hull.
        box(34, 34, 49, 49, -3, 14, logZ())
        box(34, 34, 45, 48, 10, 10, Block.IRON_CHAIN)
        put(34, 44, 10, Block.GRINDSTONE.withProperty("face", "ceiling"))
        for (x in listOf(29, 40)) {
            box(x, x, 35, 48, 13, 13, timber)
            put(x, 46, 12, Block.LANTERN.withProperty("hanging", "true"))
        }
        box(29, 40, 48, 48, 13, 13, logX())
        // Slipway extends toward the quay, leaving the public quay and central arrival route untouched.
        box(29, 40, 40, 40, 12, 19, Block.SPRUCE_PLANKS)
        for (x in listOf(32, 36)) box(x, x, 40, 40, 10, 19, logZ())
        for (z in listOf(14, 18)) for (x in listOf(29, 40)) pile(x, z, false)
    }

    /** A short single-masted working cutter, deliberately unlike the large square-rigged cargo ship. */
    private fun fishingCutter(cx: Int, cz: Int) {
        for (dz in -8..8) {
            val half = when (abs(dz)) { 8 -> 0; 7 -> 1; 6 -> 2; else -> 3 }
            for (y in 37..40) {
                val width = (half - if (y == 37) 1 else 0).coerceAtLeast(0)
                for (dx in -width..width) put(cx + dx, y, cz + dz,
                    if (y == 40 && abs(dx) < width) Block.SPRUCE_PLANKS else Block.DARK_OAK_PLANKS)
            }
            for (dx in listOf(-half, half).distinct()) put(cx + dx, 41, cz + dz,
                stair(Block.SPRUCE_STAIRS, if (dx < 0) "east" else "west"))
        }
        // A low aft wheelhouse leaves most of the hull as an exposed working deck.
        box(cx - 2, cx + 2, 41, 42, cz + 3, cz + 6, Block.STRIPPED_SPRUCE_LOG)
        box(cx - 1, cx + 1, 41, 42, cz + 3, cz + 5, Block.AIR)
        box(cx - 1, cx + 1, 42, 42, cz + 6, cz + 6, Block.LIGHT_BLUE_STAINED_GLASS)
        box(cx - 2, cx + 2, 43, 43, cz + 3, cz + 6, Block.WAXED_WEATHERED_CUT_COPPER)
        box(cx, cx, 40, 55, cz - 2, cz - 2, timber)
        box(cx, cx, 45, 45, cz - 2, cz + 5, logZ())
        // Fore-and-aft triangular sail: stretched along the hull, not a reduced copy of a square sail.
        for (y in 46..54) {
            val length = ((55 - y) * 7 / 9).coerceAtLeast(1)
            for (dz in 0..length) put(cx + 1, y, cz - 2 + dz,
                if (y <= 47) Block.CYAN_WOOL else Block.WHITE_WOOL)
        }
        // A smaller forward jib, with a continuous stay meeting the bowsprit.
        for (y in 44..52) {
            val endZ = cz - 10 + (y - 44) * 8 / 9
            for (z in endZ + 1 until cz - 2) put(cx, y, z, Block.WHITE_WOOL)
            put(cx, y, endZ, Block.DARK_OAK_FENCE)
            if (y < 52) put(cx, y + 1, endZ, Block.DARK_OAK_FENCE)
        }
        box(cx, cx, 42, 42, cz - 11, cz - 7, logZ())
        box(cx, cx, 43, 44, cz - 10, cz - 10, Block.DARK_OAK_FENCE)
        put(cx - 2, 44, cz + 5, Block.LANTERN)
        for (z in cz..cz + 1) put(cx - 2, 41, z, Block.BARREL)
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
    /** Real boarding routes meet openings in the bulwarks; the cargo bow no longer intersects the shore quay. */
    private fun boardingPiers() {
        deck(-24,-13,32,36)
        deck(14,26,32,36)
        for(xs in listOf(-24..-14,14..26)) for(x in xs) for(z in listOf(32,36))
            put(x,41,z,Block.DARK_OAK_FENCE)
        for(x in listOf(-20,20)) for(z in listOf(32,36)) pile(x,z,false)
        box(-25,-25,41,42,33,35,Block.AIR)
        box(27,27,41,42,33,35,Block.AIR)
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

    /** A built street edge: warehouse arcade on one side, a three-point sail canopy on the other. */
    private fun marketStreet() {
        // Keep the five-block spine and the entire east/west cross-street open. The arcade
        // attaches to the warehouse, rather than standing as another small freestanding stall.
        box(-9,-4,40,40,4,16,Block.SPRUCE_PLANKS)
        for(x in -9..-3) for(z in 4..17) {
            val inset=-3-x
            put(x,45+inset/2,z,
                (if(z==4 || z==17 || x == -3) Block.DARK_OAK_SLAB else Block.SPRUCE_SLAB)
                    .withProperty("type",if(inset%2==0) "bottom" else "top"))
        }
        box(-10,-10,47,47,4,16,logZ())
        box(-4,-4,44,44,4,16,logZ())
        for(z in listOf(4,10,16)) {
            put(-4,41,z,Block.CHISELED_STONE_BRICKS)
            box(-4,-4,42,45,z,z,timber)
            // Continuous stepped rafters bear on both the street posts and warehouse ledger.
            for(x in -9..-5) {
                val y=45+(-3-x)/2
                put(x,y-1,z,Block.DARK_OAK_SLAB.withProperty("type","top"))
                put(x,y,z,Block.DARK_OAK_PLANKS)
            }
            put(-5,44,z,stair(Block.DARK_OAK_STAIRS,"east",true))
            put(-6,45,z,Block.LANTERN.withProperty("hanging","true"))
        }
        // Actual side doors connect both covered spaces to the existing facilities; no new
        // interaction blocks, invisible menu copies, or blocked north entrances are introduced.
        for(x in listOf(-10,10)) {
            box(x,x,41,44,10,12,Block.AIR)
            box(x,x,45,45,10,12,logZ())
        }
        for(x in listOf(-9,9)) box(x,x,41,44,10,12,Block.AIR)
        // A fixed built-in packing bench leaves a three-block aisle and the side doorway clear.
        box(-9,-8,41,41,5,7,Block.SPRUCE_PLANKS)

        // Triangular cloth is taut at three vertices, droops toward the street, and is neither
        // a mirrored timber arcade nor a repeated striped box. All poles remain outside x±2.
        val tilt=atan(.5)
        // Pitch the seaward edge down too: a cross-slope-only sheet appeared edge-on at arrival.
        // Rz(tilt) * Rx(pitch), with matching tile origins, keeps every seam on one continuous plane.
        val pitch=atan(.15*cos(tilt))
        for(z in 6..17) {
            val halfSpan=if(z<=11) 5.0 else 6.0
            val inner=4+(abs(z-11)/halfSpan*5).toInt()
            for(x in inner..9) {
                // Standard Vanilla display entities retain native texels, but let cloth be
                // ten centimetres thick instead of a one-metre staircase of wool cubes.
                // Tiles share exact edges on one plane; no coplanar overlap or per-tick animation.
                val cloth=Entity(EntityType.BLOCK_DISPLAY)
                cloth.setNoGravity(true)
                cloth.setHasPhysics(false)
                cloth.editEntityMeta(BlockDisplayMeta::class.java) { meta ->
                    meta.setBlockState(if(z==6 || z==17 || x==inner) Block.YELLOW_TERRACOTTA else Block.WHITE_WOOL)
                    meta.setScale(Vec(sqrt(1.25),.1,1.0/cos(pitch)))
                    meta.setLeftRotation(floatArrayOf(
                        (cos(tilt/2)*sin(pitch/2)).toFloat(),
                        (sin(tilt/2)*sin(pitch/2)).toFloat(),
                        (sin(tilt/2)*cos(pitch/2)).toFloat(),
                        (cos(tilt/2)*cos(pitch/2)).toFloat()))
                    meta.setViewRange(2f)
                    meta.setShadowRadius(0f)
                }
                cloth.setInstance(instance,Pos(x+.06*(z-11),45.4+(x-4)*.5-.12*(z-11),z.toDouble())).join()
                scenery += cloth
            }
        }
        for((x,z) in listOf(9 to 6,9 to 17,4 to 11)) {
            val roof=45+(x-4)/2+abs(z-11)/4
            put(x,41,z,Block.COBBLESTONE_WALL)
            box(x,x,42,roof,z,z,Block.DARK_OAK_FENCE)
        }
        // Short ledger arms visibly fasten the two high corners to the trading-house frame.
        for(z in listOf(6,17)) box(9,10,47,47,z,z,logX())
        box(10,10,47,47,14,17,logZ())
    }
    private fun streetFurniture() {
        for ((x, z) in listOf(8 to 5, -27 to 1, 27 to 1)) {
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
    /** The counting room overlooks the quay through a supported, enterable bay, offset from the public balcony door. */
    private fun tradingOriel() {
        box(19,23,46,46,14,17,Block.SPRUCE_PLANKS)
        for(x in listOf(19,23)) {
            box(x,x,45,45,14,17,logZ())
            put(x,44,15,stair(Block.DARK_OAK_STAIRS,"north",true))
            box(x,x,47,50,14,17,plaster)
            box(x,x,48,49,15,16,Block.ORANGE_STAINED_GLASS)
            box(x,x,47,51,17,17,timber)
        }
        box(20,22,47,47,17,17,plaster)
        box(20,22,48,50,17,17,Block.ORANGE_STAINED_GLASS)
        box(20,22,51,51,17,17,logX())
        box(20,22,47,50,14,16,Block.AIR)
        for(x in 19..23) for(y in 52..52+roofRise(3,abs(x-21)))
            put(x,y,17,if(x==21) beam else plaster)
        gableRoof(18,24,14,18,52,true,false)
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
        box(cx - 3, cx + 3, 44, 44, cz + 6, cz + 10, Block.DARK_OAK_PLANKS)
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
        for (x in -55..55) for (z in -62..58) for (y in 38..85) {
            var b = instance.getBlock(x,y,z)
            val wall = b.name().endsWith("_wall")
            if (!wall && !b.name().endsWith("_fence") && !b.name().endsWith("_pane") && b.name() != "minecraft:iron_bars") continue
            for ((name,dx,dz) in directions) {
                val adjacent = instance.getBlock(x+dx,y,z+dz)
                val connected = adjacent.isSolid && !adjacent.name().contains("leaves")
                b = b.withProperty(name,if(wall) { if(connected) "low" else "none" } else connected.toString())
            }
            put(x,y,z,b)
        }
    }
}
