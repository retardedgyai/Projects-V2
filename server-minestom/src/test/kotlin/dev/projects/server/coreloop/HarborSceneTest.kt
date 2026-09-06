package dev.projects.server.coreloop

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.BlockDisplayMeta

class HarborSceneTest {
    @Test
    fun `headlands never generate over the town or close the shipping approach`() {
        for (x in -55..55) for (z in -62..64) assertEquals(null,HarborBackdrop.height(x,z))
        for (x in -100..100) for (z in 15..100) assertEquals(null,HarborBackdrop.height(x,z))
        assertTrue(requireNotNull(HarborBackdrop.height(-72,-45)) > 50)
        assertTrue(requireNotNull(HarborBackdrop.height(78,-55)) > 60)
        assertTrue(requireNotNull(HarborBackdrop.height(-8,-93)) > 65)
        // The rear lawn is y51: no straight quarry wall may begin at the generator boundary.
        for(x in -18..18) {
            assertTrue(requireNotNull(HarborBackdrop.height(x,-63)) in 50..52, "Rear ground jumps at $x")
            for(z in -79..-63) {
                val here=requireNotNull(HarborBackdrop.height(x,z))
                val north=requireNotNull(HarborBackdrop.height(x,z-1))
                assertTrue(kotlin.math.abs(north-here)<=2, "Cut face in rear foothill $x,$z")
            }
        }
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
            assertEquals(493,scene.scenery.size, "Unexpected growth in static harbor geometry")
            scene.scenery.forEach { detail ->
                assertEquals(EntityType.BLOCK_DISPLAY,detail.entityType)
                assertEquals(instance,detail.instance)
            }
            val fabric=scene.scenery.filter {
                val name=(it.entityMeta as BlockDisplayMeta).blockStateId.name()
                name.endsWith("_wool") || name.endsWith("_terracotta")
            }
            assertEquals(488,fabric.size)
            fabric.forEach { cloth ->
                assertEquals(EntityType.BLOCK_DISPLAY,cloth.entityType)
                assertEquals(instance,cloth.instance)
                val meta=cloth.entityMeta as BlockDisplayMeta
                assertTrue(minOf(meta.scale.x(),meta.scale.y(),meta.scale.z())<=.15, "Sail returned to metre-thick wool")
                assertTrue(kotlin.math.abs(cloth.position.x())>=4 && cloth.position.y()>=44,
                    "Cloth intrudes into the walking spine or headroom")
            }
            val sail=fabric.filter { it.position.x() in 4.0..10.0 && it.position.z()>=6 }
            assertEquals(42,sail.size)
            sail.forEach { cloth ->
                assertEquals(45.4+.5*(cloth.position.x()-4)-.15*(cloth.position.z()-11),
                    cloth.position.y(),1e-6, "Sail panels do not share the same inclined plane")
            }
            val quayCloth=fabric.filter { kotlin.math.abs(it.position.x())>=11 && it.position.z()<20 }
            assertEquals(104,quayCloth.size)
            quayCloth.forEach { cloth ->
                assertEquals(45.8-.4*(cloth.position.z()-16),cloth.position.y(),1e-6)
                if(cloth.position.z()==16.0) assertTrue(cloth.position.y()+.15<46,
                    "Cloth intersects the occupied balcony floor")
            }
            val stallCloth=fabric.filter { it.position.z()<0 }
            assertEquals(54,stallCloth.size)
            stallCloth.forEach { cloth ->
                val z=cloth.position.z()
                assertEquals(45.6+.35*minOf(z+8,-2-z),cloth.position.y(),1e-6, "Split market tent ridge")
            }
            for(x in listOf(-8,-4,4,7)) for(z in listOf(-8,-3)) {
                assertTrue(instance.getBlock(x,40,z).isSolid, "Tent post has no foundation")
                for(y in 41..45) assertEquals("minecraft:dark_oak_fence",instance.getBlock(x,y,z).name())
            }
            val cargoCloth=fabric.filter { it.position.x()< -24 && it.position.z()>25 }
            assertEquals(136,cargoCloth.size)
            for((mastZ,top) in listOf(38 to 59,46 to 55)) {
                val panels=cargoCloth.filter { it.position.z() in mastZ+.5..mastZ+2.5 }
                assertEquals(68,panels.size)
                for(panel in panels) {
                    val row=panel.position.y()-(top-8)
                    assertTrue(row in 0.0..7.0)
                    assertEquals(mastZ+.6+1.8*kotlin.math.sin(Math.PI*row/8),panel.position.z(),1e-6)
                    assertTrue((panel.entityMeta as BlockDisplayMeta).scale.z()<=.1)
                }
                for(y in 40..top) assertTrue(instance.getBlock(-29,y,mastZ).isSolid, "Cargo mast has a gap")
            }
            val cutterCloth=fabric.filter { it.position.x()>29 && it.position.z()>25 }
            assertEquals(152,cutterCloth.size)
            assertTrue(cutterCloth.all { (it.entityMeta as BlockDisplayMeta).scale.x()<=.1 })
            assertTrue(cutterCloth.all { (it.entityMeta as BlockDisplayMeta).scale.z()<=1 }, "Cutter fabric texels stretched across the sail")
            for(x in -33..-25) for(y in 47..58) for(z in 38..49)
                assertFalse(instance.getBlock(x,y,z).name().endsWith("_wool"), "Old solid cargo sail remains")
            assertEquals("minecraft:spruce_stairs",instance.getBlock(-29,41,32).name(), "Forecastle access missing")
            assertTrue(instance.getBlock(-29,40,32).isSolid, "Forecastle step is unsupported")
            for(xs in listOf(-23..-11,11..23)) for(x in xs) for(z in 15..16)
                assertTrue(instance.getBlock(x,46,z).isSolid, "Fabric replaced the balcony floor $x,$z")
            for(x in listOf(-23,-17,-11,11,17,23)) {
                assertEquals("minecraft:lantern",instance.getBlock(x,44,16).name())
                assertTrue(instance.getBlock(x,45,16).isSolid, "Quay lantern lost its hanger")
            }
            for((x,z) in listOf(9 to 6,9 to 17,4 to 11)) {
                assertTrue(instance.getBlock(x,40,z).isSolid, "Sail mast has no foundation")
                assertEquals("minecraft:cobblestone_wall",instance.getBlock(x,41,z).name())
                val roof=45+(x-4)/2+kotlin.math.abs(z-11)/4
                for(y in 42..roof) assertEquals(
                    if(x==9 && y==47) "minecraft:dark_oak_log" else "minecraft:dark_oak_fence",
                    instance.getBlock(x,y,z).name())
            }

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
            for(z in -2..14) assertTrue(-26 to z in reached, "West alley disconnected at $z")
            for(x in -7..-5) for(z in 4..16)
                assertTrue(walkable(x,z), "Warehouse arcade lost its three-block aisle $x,$z")
            for(x in listOf(-10,-9,9,10)) for(z in 10..12)
                assertTrue(walkable(x,z) && x to z in reached, "Covered market side door blocked $x,$z")
            for(z in listOf(4,10,16)) {
                for(y in 40..45) assertTrue(instance.getBlock(-4,y,z).isSolid, "Arcade post is unsupported $y,$z")
                assertEquals("minecraft:lantern",instance.getBlock(-6,45,z).name())
                assertTrue(instance.getBlock(-6,46,z).isSolid, "Arcade lantern is not suspended from a rafter")
            }
            for(x in -22..23) for(z in -1..3)
                assertTrue(walkable(x,z), "Cross-street narrowed by market extension $x,$z")
            assertTrue(-28 to 40 in reached && 29 to 35 in reached, "Both ship decks must connect to the public pier")
            for(xs in listOf(-25..-13,14..27)) for(x in xs) for(z in 33..35)
                assertTrue(walkable(x,z), "Three-block boarding route blocked $x,$z")
            for((x,z) in listOf(0 to 29,-18 to 34,18 to 34)) {
                assertEquals("minecraft:spruce_slab",instance.getBlock(x,40,z).name(), "Pier returned to solid block plating")
                assertEquals("top",instance.getBlock(x,40,z).getProperty("type"), "Pier walking height changed")
                assertEquals(Block.AIR,instance.getBlock(x,39,z), "Open bay under pier was filled in")
                assertTrue(walkable(x,z))
            }
            for(z in listOf(21,27,33,38)) {
                for(x in -4..4) for(y in 39..40)
                    assertEquals("minecraft:dark_oak_log",instance.getBlock(x,y,z).name(), "Arrival bearer disconnected")
                for(x in listOf(-4,4)) for(y in 34..38)
                    assertTrue(instance.getBlock(x,y,z).isSolid, "Arrival frame lost its pile")
            }
            for(x in listOf(-24,-20,-14,14,20,26)) for(z in listOf(32,36))
                for(y in 34..40) assertTrue(instance.getBlock(x,y,z).isSolid, "Boarding bearer has no pile")
            for(z in 25..31) for(x in listOf(-4,4))
                assertTrue(instance.getBlock(x,41,z).isSolid, "Arrival edge has a gap")
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
            for(xs in listOf(-11..-5,5..11)) for(x in xs) for(z in -32..-28)
                assertTrue(upperWalkable(x,z), "Seaward hall bay is not connected to the veranda $x,$z")
            for(x in listOf(-14,-7,-3,3,7,14)) {
                assertEquals(Block.CHISELED_STONE_BRICKS,instance.getBlock(x,53,-27), "Veranda post lost its plinth")
                for(y in 54..59) assertTrue(instance.getBlock(x,y,-27).isSolid, "Veranda post has a gap $x,$y")
            }
            for(x in -16..16) if(x !in 8..12 && x !in listOf(-14,-7,-3,3,7,14))
                assertEquals("minecraft:dark_oak_fence",instance.getBlock(x,53,-27).name(), "Unguarded podium edge $x")
            for(x in 8..12) assertTrue(upperWalkable(x,-27), "Balustrade closed the actual hall stairs $x")
            assertTrue(instance.getBlock(2,68,-31).isAir, "Great hall still has an opaque domestic gable")
            assertTrue(instance.getBlock(0,62,-31).isSolid, "Open gable king post has no bearing")
            for(x in listOf(-2,2)) {
                assertEquals("minecraft:lantern",instance.getBlock(x,65,-31).name())
                assertTrue(instance.getBlock(x,66,-31).isSolid, "Gable lantern does not hang from the fan truss")
            }
            for(side in listOf(-1,1)) for(z in -48..-32 step 4) {
                assertTrue(instance.getBlock(side*15,60,z).isSolid, "Swept eave has no gallery column")
                for(i in 1..3) assertTrue(instance.getBlock(side*(15+i),60+i,z).isSolid, "Disconnected eave bracket")
            }
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
            val coastReached=mutableSetOf(31 to -15)
            val coastPending=ArrayDeque(coastReached)
            while(coastPending.isNotEmpty()) {
                val (x,z)=coastPending.removeFirst()
                for((dx,dz) in directions) {
                    val nx=x+dx; val nz=z+dz
                    if(nx !in 28..43 || nz !in -30..-14) continue
                    if(instance.getBlock(nx,44,nz).isSolid && instance.getBlock(nx,45,nz).isAir &&
                        instance.getBlock(nx,46,nz).isAir && coastReached.add(nx to nz)) coastPending.add(nx to nz)
                }
            }
            for(x in 41..42) for(z in -27..-18)
                assertTrue(x to z in coastReached, "Seaward terrace is not connected to the existing stair $x,$z")
            for(point in listOf(30 to -17,31 to -17,33 to -26,39 to -26,40 to -25))
                assertTrue(point in coastReached, "Lodging common room/stair/terrace doorway disconnected: $point")
            for(step in 0..5) for(x in 30..31) {
                val y=45+step; val z=-18-step
                assertEquals("minecraft:spruce_stairs",instance.getBlock(x,y,z).name())
                assertEquals("north",instance.getBlock(x,y,z).getProperty("facing"))
                assertTrue(instance.getBlock(x,y-1,z).isSolid, "Lodging stair is unsupported")
                assertTrue(instance.getBlock(x,y+1,z).isAir && instance.getBlock(x,y+2,z).isAir, "Lodging stair headroom blocked")
            }
            assertTrue(instance.getBlock(30,50,-24).isSolid && instance.getBlock(30,51,-24).isAir &&
                instance.getBlock(30,52,-24).isAir, "Lodging stair has no usable upper landing")
            val lodgingReached=mutableSetOf(30 to -24)
            val lodgingPending=ArrayDeque(lodgingReached)
            while(lodgingPending.isNotEmpty()) {
                val (x,z)=lodgingPending.removeFirst()
                for((dx,dz) in directions) {
                    val nx=x+dx; val nz=z+dz
                    if(nx !in 30..38 || nz !in -28..-15) continue
                    if(instance.getBlock(nx,50,nz).isSolid && instance.getBlock(nx,51,nz).isAir &&
                        instance.getBlock(nx,52,nz).isAir && lodgingReached.add(nx to nz)) lodgingPending.add(nx to nz)
                }
            }
            for(point in listOf(36 to -26,36 to -22,36 to -18,33 to -15,34 to -15))
                assertTrue(point in lodgingReached, "Lodging upper landing does not reach room/balcony: $point")
            for(z in listOf(-27,-23,-19)) {
                assertEquals("foot",instance.getBlock(37,51,z).getProperty("part"))
                assertEquals("head",instance.getBlock(38,51,z).getProperty("part"))
                assertEquals("east",instance.getBlock(38,51,z).getProperty("facing"))
                assertTrue(instance.getBlock(38,51,z-1).isSolid, "Bedside light has no cabinet")
            }
            for(z in listOf(-25,-21)) {
                assertEquals("minecraft:lantern",instance.getBlock(33,54,z).name())
                assertTrue(instance.getBlock(33,55,z).isSolid, "Lodging passage light lost its beam")
            }
            for(z in -30..-15) {
                val edge=if(z in -27..-18) 43 else 42
                assertEquals("minecraft:stone_brick_wall",instance.getBlock(edge,45,z).name(), "Seaward terrace lost its edge guard")
                assertTrue(instance.getBlock(edge,44,z).isSolid)
            }
            for(z in listOf(-29,-25,-21,-17)) {
                val edge=if(z in -27..-18) 43 else 42
                for(x in 40..edge) for(y in 35..44)
                    assertTrue(instance.getBlock(x,y,z).isSolid, "Cliff buttress is not continuous $x,$y,$z")
            }
            assertEquals(Block.WATER,instance.getBlock(20,38,38), "Shore rocks intrude into the shipping channel")
            for(x in 20..22) for(z in 14..16) {
                assertEquals(Block.SPRUCE_PLANKS,instance.getBlock(x,46,z), "Trading bay floor unsupported")
                assertTrue(instance.getBlock(x,47,z).isAir && instance.getBlock(x,48,z).isAir, "Trading bay is a sealed facade")
            }
            for(x in listOf(19,23)) for(z in 14..17)
                assertTrue(instance.getBlock(x,45,z).isSolid, "Trading bay has a disconnected cantilever")
            // Export actual terrain blocks. Thin native cloth displays are verified above
            // and in Vanilla photographs, not represented by this block-only diagnostic file.
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
            scene.scenery.forEach { it.remove() }
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }
}
