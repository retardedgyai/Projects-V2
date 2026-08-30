package dev.projects.server.experiment.swarm.world

import net.minestom.server.instance.block.Block
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TidebreakWorldSpecTest {
    private val plan = TidebreakBlockPlan()

    @Test
    fun `world is fixed at 96 by 96 with safe spawn and three actors`() {
        assertEquals(96, TidebreakWorldSpec.worldBounds.width)
        assertEquals(96, TidebreakWorldSpec.worldBounds.depth)
        assertEquals(3, TidebreakWorldSpec.actors.size)
        assertEquals(3, TidebreakWorldSpec.actors.map { it.id }.distinct().size)

        val spawn = TidebreakWorldSpec.spawn
        assertEquals(Block.STONE_BRICKS, plan.blockAt(spawn.blockX(), TidebreakWorldSpec.GROUND_Y, spawn.blockZ()))
        assertEquals(Block.AIR, plan.blockAt(spawn.blockX(), spawn.blockY(), spawn.blockZ()))
        assertEquals(Block.AIR, plan.blockAt(spawn.blockX(), spawn.blockY() + 1, spawn.blockZ()))
    }

    @Test
    fun `all facilities and targets fit the travel budget and keep unique ids`() {
        val origin = TidebreakWorldSpec.spawn
        val destinations = TidebreakWorldSpec.actors.map { it.position } +
            TidebreakWorldSpec.targets.map { it.position } +
            TidebreakWorldSpec.stagingSpawn + TidebreakWorldSpec.bossSpawn

        destinations.forEach { destination ->
            val dx = destination.x() - origin.x()
            val dz = destination.z() - origin.z()
            assertTrue(sqrt(dx * dx + dz * dz) <= 90.0, "$destination exceeds compact-world travel geometry")
            assertTrue(TidebreakWorldSpec.worldBounds.contains(destination))
        }
        assertEquals(TidebreakWorldSpec.targets.size, TidebreakWorldSpec.targetsById.size)
        assertTrue(TidebreakWorldSpec.oreNodes.all { it.kind == TidebreakTargetKind.ORE_NODE })
    }

    @Test
    fun `registered targets have visible fixed blocks and arena remains dry`() {
        TidebreakWorldSpec.oreNodes.forEach { node ->
            assertEquals(Block.RAW_IRON_BLOCK, plan.blockAt(node.position.blockX(), node.position.blockY(), node.position.blockZ()))
        }
        TidebreakWorldSpec.supplierRecords.forEach { record ->
            assertEquals(Block.LECTERN, plan.blockAt(record.position.blockX(), record.position.blockY(), record.position.blockZ()))
        }
        assertEquals(
            Block.COPPER_BLOCK,
            plan.blockAt(
                TidebreakWorldSpec.couplerRack.position.blockX(),
                TidebreakWorldSpec.couplerRack.position.blockY(),
                TidebreakWorldSpec.couplerRack.position.blockZ(),
            ),
        )
        for (x in TidebreakWorldSpec.arenaBounds.minX..TidebreakWorldSpec.arenaBounds.maxX) {
            for (z in -10..10) {
                assertNotEquals(Block.WATER, plan.blockAt(x, TidebreakWorldSpec.GROUND_Y, z))
            }
        }
    }

    @Test
    fun `crash pillars are large seven-block-high collision landmarks`() {
        TidebreakWorldSpec.crashPillars.forEach { pillar ->
            val x = pillar.position.blockX()
            val z = pillar.position.blockZ()
            for (px in x - 1..x + 1) {
                for (pz in z - 1..z + 1) {
                    assertEquals(Block.REINFORCED_DEEPSLATE, plan.blockAt(px, 41, pz))
                    assertEquals(Block.SEA_LANTERN, plan.blockAt(px, 47, pz))
                }
            }
        }
    }

    @Test
    fun `practice post is a readable three-by-three four-block-high landmark`() {
        val post = TidebreakWorldSpec.practicePost.position
        for (x in post.blockX() - 1..post.blockX() + 1) {
            for (z in post.blockZ() - 1..post.blockZ() + 1) {
                assertEquals(Block.CUT_COPPER, plan.blockAt(x, 42, z))
                assertEquals(Block.LIGHTNING_ROD, plan.blockAt(x, 44, z))
            }
        }
    }
}
