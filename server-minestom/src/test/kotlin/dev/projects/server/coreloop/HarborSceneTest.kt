package dev.projects.server.coreloop

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block

class HarborSceneTest {
    @Test
    fun `headlands never generate over the town or close the shipping approach`() {
        for (x in -55..55) for (z in -62..64) assertEquals(null,HarborBackdrop.height(x,z))
        for (x in -100..100) for (z in 15..100) assertEquals(null,HarborBackdrop.height(x,z))
        assertTrue(requireNotNull(HarborBackdrop.height(-72,-45)) > 50)
        assertTrue(requireNotNull(HarborBackdrop.height(78,-55)) > 60)
        assertTrue(requireNotNull(HarborBackdrop.height(-8,-93)) > 65)
    }

    @Test
    fun `arrival and every facility connect through level unobstructed walking space`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val scene = HarborScene.build(instance)
        try {
            val spawn = scene.spawn
            assertEquals(41.0, spawn.y())
            assertTrue(instance.getBlock(spawn.blockX(), 40, spawn.blockZ()).isSolid)
            assertEquals(Block.AIR, instance.getBlock(spawn))
            assertEquals(5, scene.facilities.size)
            assertEquals(HarborFacilityKind.entries.toSet(), scene.facilities.map { it.kind }.toSet())
            assertEquals(5, scene.labels.size)

            fun walkable(x: Int, z: Int): Boolean =
                x in -36..36 && z in -28..56 && instance.getBlock(x, 40, z).isSolid &&
                    instance.getBlock(x, 41, z).isAir && instance.getBlock(x, 42, z).isAir

            val reached = mutableSetOf(spawn.blockX() to spawn.blockZ())
            val pending = ArrayDeque(reached)
            val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            while (pending.isNotEmpty()) {
                val (x, z) = pending.removeFirst()
                directions.forEach { (dx, dz) ->
                    val next = x + dx to z + dz
                    if (walkable(next.first, next.second) && reached.add(next)) pending.add(next)
                }
            }
            scene.facilities.forEach { facility ->
                assertTrue(facility.position.distance(spawn) < 25.0, "${facility.kind} is too far from arrival")
                facility.blockPositions.forEach { block ->
                    assertFalse(instance.getBlock(block).isAir, "${facility.kind} exposes an empty interaction block")
                }
                assertTrue(
                    facility.blockPositions.any { block ->
                        directions.any { (dx, dz) -> block.blockX() + dx to block.blockZ() + dz in reached }
                    },
                    "No two-block-high, level path reaches ${facility.kind}",
                )
            }
            assertTrue(0 to 33 in reached, "The pier cannot be reached from arrival")
            assertTrue(-28 to 40 in reached && 29 to 35 in reached, "Both ship decks must connect to the public pier")
            for(xs in listOf(-25..-13,14..27)) for(x in xs) for(z in 33..35)
                assertTrue(walkable(x,z), "Three-block boarding route blocked $x,$z")
            for(z in 21..24) assertTrue(walkable(-29,z), "Cargo bow still overlaps the shore quay $z")
            assertEquals(Block.DARK_OAK_PLANKS,instance.getBlock(-32,44,51), "Cargo lantern floats above a bottom slab")
            assertEquals(Block.WAXED_WEATHERED_CUT_COPPER,instance.getBlock(28,43,41), "Cutter lantern floats above a bottom slab")
            assertTrue(10 to -14 in reached, "The upper hall stair foot must connect to the public loop")
            for (x in -2..2) for (z in -11..37) {
                assertTrue(walkable(x, z), "Five-block arrival spine obstructed at $x,$z")
            }
            // Preserve the old direct approaches, not merely a circuitous alternative around a new wall.
            for (x in -17..-15) for (z in 3..8) assertTrue(walkable(x,z), "Storage approach $x,$z")
            for (x in 15..17) for (z in 3..8) assertTrue(walkable(x,z), "Supplies approach $x,$z")
            assertEquals(Block.WATER, instance.getBlock(50, 38, 50), "Outside the harbor should be sea, not void")
            for (step in 0..11) {
                val z=-15-step; val y=41+step
                for (x in 8..12) {
                    assertTrue(instance.getBlock(x,y,z).name().contains("stairs"), "Missing upper hall step $step at $x")
                    assertEquals("north", instance.getBlock(x,y,z).getProperty("facing"))
                    assertTrue(instance.getBlock(x,y+1,z).isAir && instance.getBlock(x,y+2,z).isAir, "Upper hall stair headroom $step at $x")
                    assertTrue(instance.getBlock(x,y-1,z).isSolid, "Stair has no foundation $step at $x")
                }
            }
            for (z in -29..-27) assertTrue(instance.getBlock(8,53,z).isAir && instance.getBlock(8,54,z).isAir, "Hall entry blocked at $z")
            fun upperWalkable(x: Int, z: Int) = x in -15..15 && z in -49..-27 &&
                instance.getBlock(x,52,z).isSolid && instance.getBlock(x,53,z).isAir && instance.getBlock(x,54,z).isAir
            val upperReached = mutableSetOf(10 to -27)
            val upperPending = ArrayDeque(upperReached)
            while (upperPending.isNotEmpty()) {
                val (x,z) = upperPending.removeFirst()
                directions.forEach { (dx,dz) ->
                    val next = x+dx to z+dz
                    if (upperWalkable(next.first,next.second) && upperReached.add(next)) upperPending.add(next)
                }
            }
            assertTrue(0 to -40 in upperReached, "The gallery stairs must actually reach the hall interior")
            for(x in -2..2) for(z in -48..-31)
                assertTrue(upperWalkable(x,z), "Hall central aisle obstructed $x,$z")
            for(z in listOf(-46,-40,-34)) for(x in listOf(-11,11)) for(y in 52..61)
                assertTrue(instance.getBlock(x,y,z).isSolid, "Hall frame is unsupported $x,$y,$z")
            for(z in listOf(-43,-37)) {
                assertEquals("minecraft:lantern",instance.getBlock(0,59,z).name())
                assertTrue(instance.getBlock(0,60,z).isSolid, "Hall light has no suspension frame")
                for(y in 61..63) assertEquals("minecraft:iron_chain",instance.getBlock(0,y,z).name())
                assertTrue(instance.getBlock(0,64,z).isSolid, "Chandelier chain has no roof beam")
            }
            for (x in -12..12) for (z in -48..-31)
                assertTrue(instance.getBlock(x,51,z).isSolid, "The great hall floats above an unsupported floor $x,$z")
            for (x in -22..-12) {
                val rail = instance.getBlock(x,47,16)
                assertTrue(rail.name().endsWith("_fence"), "Warehouse gallery has a missing railing at $x")
                assertEquals("true",rail.getProperty("east"), "Disconnected east rail at $x")
                assertEquals("true",rail.getProperty("west"), "Disconnected west rail at $x")
            }
            for (step in 0..5) for ((x,z,facing) in listOf(Triple(-23+step,7,"east"),Triple(23-step,6,"west"))) {
                val y=41+step
                for (dz in 0..1) {
                    assertEquals("minecraft:spruce_stairs",instance.getBlock(x,y,z+dz).name())
                    assertEquals(facing,instance.getBlock(x,y,z+dz).getProperty("facing"))
                    assertTrue(instance.getBlock(x,y+1,z+dz).isAir && instance.getBlock(x,y+2,z+dz).isAir,
                        "Occupied storey stair headroom $x,$y,${z+dz}")
                }
            }
            // A bottom stair's high edge is y+1. Final stair and full landing must share the same block y;
            // placing the landing a block above the final stair would require jumping despite an air-only path.
            assertEquals("minecraft:stone_brick_stairs",instance.getBlock(10,52,-26).name())
            assertEquals(Block.SPRUCE_PLANKS,instance.getBlock(10,52,-27))
            assertEquals("minecraft:stone_brick_stairs",instance.getBlock(31,44,-14).name())
            assertEquals(Block.SMOOTH_SANDSTONE,instance.getBlock(31,44,-15))
            assertEquals("minecraft:stone_brick_stairs",instance.getBlock(-33,42,-8).name())
            for (z in -10..-9) assertEquals(Block.SMOOTH_SANDSTONE,instance.getBlock(-33,42,z))
            for ((x,z,dx) in listOf(Triple(-18,7,1),Triple(18,6,-1))) {
                assertEquals("minecraft:spruce_stairs",instance.getBlock(x,46,z).name())
                assertEquals(Block.SPRUCE_PLANKS,instance.getBlock(x+dx,46,z))
            }
            assertTrue(-25 to 7 in reached && 25 to 6 in reached, "Merchant stair alley approaches must be reachable")
            for ((x,z) in listOf(-24 to 7,24 to 6)) assertTrue(walkable(x,z), "Merchant stair door $x,$z")
            for (x in listOf(-18,-17,16,17)) for (z in 14..15) {
                assertTrue(instance.getBlock(x,46,z).isSolid, "Unsupported merchant balcony doorway $x,$z")
                assertTrue(instance.getBlock(x,47,z).isAir && instance.getBlock(x,48,z).isAir, "Blocked merchant balcony doorway $x,$z")
            }
            for (x in listOf(-12,-6,6,12)) {
                assertEquals("minecraft:red_wall_banner",instance.getBlock(x,58,-30).name())
                assertTrue(instance.getBlock(x,58,-31).isSolid, "A wall banner has no wall behind it")
            }
            // The repair shed must read as an open working building, with supported frames and clear side aisles.
            for (z in listOf(-3,3,9)) for (x in listOf(29,40)) {
                assertTrue(instance.getBlock(x,40,z).isSolid)
                assertEquals(Block.STRIPPED_SPRUCE_LOG,instance.getBlock(x,47,z))
            }
            for (z in -2..10) for (x in listOf(29,39)) {
                if (x == 29 && z in listOf(3,9)) continue
                assertTrue(instance.getBlock(x,41,z).isAir && instance.getBlock(x,42,z).isAir,
                    "Repair shed aisle obstructed $x,$z")
            }
            assertEquals(Block.STRIPPED_SPRUCE_LOG,instance.getBlock(30,55,34), "Cutter mast missing")
            assertEquals(Block.AIR,instance.getBlock(30,54,40), "Cutter must not inherit a second cargo-ship mast")
            assertEquals(Block.WATER,instance.getBlock(30,38,47), "Cutter must have a shorter hull than the cargo ship")
            // Foundry stays open at bench level, with two real stone arches instead of another merchant facade.
            for (x in (-24..-19) + (-17..-12)) {
                assertTrue(walkable(x,-3), "Foundry arcade obstructed $x")
                assertTrue(instance.getBlock(x,46,-3).isSolid, "Foundry arch crown missing $x")
            }
            for (x in listOf(-25,-18,-11)) for (y in 40..46)
                assertTrue(instance.getBlock(x,y,-3).isSolid, "Foundry arch pier unsupported $x,$y")
            assertTrue(instance.getBlock(-18,50,-5).isAir, "Old domestic upper floor remains above the foundry forecourt")
            for (direction in listOf("north","south"))
                assertEquals("true",instance.getBlock(-11,45,-13).getProperty(direction), "Foundry glazing has disconnected iron bars")
            for(x in -2..2) assertTrue(walkable(x,-12), "Cartography loggia entry narrowed $x")
            assertTrue(instance.getBlock(0,51,-12).isAir, "Reception gable still obscures the raised hall")
            for(x in listOf(-7,-3,3,7)) for(y in 40..45)
                assertTrue(instance.getBlock(x,y,-12).isSolid, "Loggia front post unsupported $x,$y")
            for(x in 15..18) assertTrue(walkable(x,-5), "Lecture room entrance obstructed $x")
            assertTrue(19 to -16 in reached && 23 to -16 in reached, "The octagonal reading room must be enterable")
            assertEquals(Block.AIR,instance.getBlock(23,50,-16), "Reading room should be a tall room, not a solid tower")
            assertEquals(Block.LIGHT_BLUE_STAINED_GLASS,instance.getBlock(14,45,-8), "Lecture room street wall lost its reading-light window")
            assertEquals(Block.WAXED_WEATHERED_CUT_COPPER,instance.getBlock(23,64,-16), "Tower finial has no full-block support")
            assertTrue(instance.getBlock(42,39,-24).isSolid, "East retaining wall lacks its natural rock apron")
            assertTrue(instance.getBlock(-42,39,-22).isSolid, "West retaining wall lacks its natural rock apron")
            assertEquals(Block.WATER,instance.getBlock(20,38,38), "Shore rocks intrude into the shipping channel")
            for(x in 20..22) for(z in 14..16) {
                assertEquals(Block.SPRUCE_PLANKS,instance.getBlock(x,46,z), "Trading bay floor unsupported")
                assertTrue(instance.getBlock(x,47,z).isAir && instance.getBlock(x,48,z).isAir, "Trading bay is a sealed facade")
            }
            for(x in listOf(19,23)) for(z in 14..17)
                assertTrue(instance.getBlock(x,45,z).isSolid, "Trading bay has a disconnected cantilever")
            // Export actual generated blocks, not an aspirational concept illustration.
            val target = java.nio.file.Path.of("build/reports/harbor-blocks.tsv")
            java.nio.file.Files.createDirectories(target.parent)
            java.nio.file.Files.newBufferedWriter(target).use { out ->
                for (x in -55..55) for (z in -62..58) for (y in 34..86) {
                    val b = instance.getBlock(x,y,z)
                    if (!b.isAir && b != Block.WATER && listOf(Triple(1,0,0),Triple(-1,0,0),Triple(0,1,0),Triple(0,-1,0),Triple(0,0,1),Triple(0,0,-1)).any { (dx,dy,dz) -> instance.getBlock(x+dx,y+dy,z+dz).let { it.isAir || it == Block.WATER } })
                        out.appendLine("$x\t$y\t$z\t${b.name()}")
                }
            }
        } finally {
            scene.labels.forEach { it.remove() }
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }
}
