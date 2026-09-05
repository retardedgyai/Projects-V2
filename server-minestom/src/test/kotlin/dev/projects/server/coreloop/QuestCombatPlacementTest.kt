package dev.projects.server.coreloop

import dev.projects.server.questmap.QuestMapContentKind
import dev.projects.server.questmap.VerdantRoadQuestRuntime
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestCombatPlacementTest {
    private val desired = Pos(8.5, 40.0, 8.5)

    @Test
    fun `clear floor keeps enemy at desired ground level and centers its block`() = withArena { arena ->
        val resolved = QuestCombatPlacement.resolve(arena, Pos(8.2, 40.6, 8.9))
        assertEquals(desired, resolved)
        assertSupportedAndClear(arena, resolved)
    }

    @Test
    fun `boss lodestone marker moves spawn sideways instead of into marker or onto its top`() = withArena { arena ->
        arena.setBlock(8, 40, 8, Block.LODESTONE)
        assertFalse(QuestCombatPlacement.clear(arena, desired))
        val resolved = QuestCombatPlacement.resolve(arena, desired)
        assertEquals(40.0, resolved.y())
        assertFalse(resolved.sameBlock(desired))
        assertSupportedAndClear(arena, resolved)
    }

    @Test
    fun `tree trunks and ruined pillars do not lift enemies onto decorative assets`() = withArena { arena ->
        for (y in 40..47) arena.setBlock(8, y, 8, Block.OAK_LOG)
        for (y in 40..45) arena.setBlock(7, y, 8, Block.STONE_BRICKS)
        val resolved = QuestCombatPlacement.resolve(arena, desired)
        assertEquals(40.0, resolved.y())
        assertSupportedAndClear(arena, resolved)
    }

    @Test
    fun `two block high ruin gap cannot contain the enlarged boss`() = withArena { arena ->
        // The boss is a Vindicator with scale 1.35, taller than the normal two-block opening.
        arena.setBlock(8, 42, 8, Block.STONE_BRICKS)
        assertFalse(QuestCombatPlacement.clear(arena, desired))
        val resolved = QuestCombatPlacement.resolve(arena, desired)
        assertFalse(resolved.sameBlock(desired))
        assertSupportedAndClear(arena, resolved)
    }

    @Test
    fun `water and lava at feet or head cannot be selected for combat spawns`() = withArena { arena ->
        for (liquid in listOf(Block.WATER, Block.LAVA)) {
            for (y in listOf(40, 41, 42)) {
                arena.setBlock(8, y, 8, liquid)
                assertFalse(QuestCombatPlacement.clear(arena, desired))
                val resolved = QuestCombatPlacement.resolve(arena, desired)
                assertSupportedAndClear(arena, resolved)
                arena.setBlock(8, y, 8, Block.AIR)
            }
        }
    }

    @Test
    fun `unsupported void is skipped rather than yielding a falling spawn`() = withArena { arena ->
        for (y in 0 until 40) arena.setBlock(8, y, 8, Block.AIR)
        assertFalse(QuestCombatPlacement.clear(arena, desired))
        val resolved = QuestCombatPlacement.resolve(arena, desired)
        assertFalse(resolved.sameBlock(desired))
        assertSupportedAndClear(arena, resolved)
    }

    @Test
    fun `resolver can use a nearby raised terrain floor when original level is fully obstructed`() = withArena { arena ->
        for (x in 1..15) for (z in 1..15) arena.setBlock(x, 40, z, Block.STONE)
        val resolved = QuestCombatPlacement.resolve(arena, desired)
        assertEquals(Pos(8.5, 41.0, 8.5), resolved)
        assertSupportedAndClear(arena, resolved)
    }

    @Test
    fun `no safe position fails explicitly instead of embedding or silently dropping an enemy`() = withArena { arena ->
        for (x in 1..15) for (z in 1..15) for (y in 38..46) arena.setBlock(x, y, z, Block.STONE)
        assertFailsWith<IllegalStateException> { QuestCombatPlacement.resolve(arena, desired) }
    }

    @Test
    fun `decorated regression map resolves every normal group and the boss on real walkable blocks`() {
        MinecraftServer.init(Auth.Offline())
        val runtime = VerdantRoadQuestRuntime.prepare(1_788_168_623_401L, candidateCount = 1)
            .get(30, TimeUnit.SECONDS)
        try {
            val plan = runtime.plan
            val requests = plan.contents.filter { it.kind == QuestMapContentKind.COMBAT }.flatMap { content ->
                val center = Pos(content.position.x + 0.5, plan.heightAt(content.position) + 1.0, content.position.z + 0.5)
                listOf(center, center.add(2.0, 0.0, 1.0), center.add(-2.0, 0.0, 1.0))
            } + Pos(plan.boss.x + 0.5, plan.heightAt(plan.boss) + 1.0, plan.boss.z + 0.5)
            assertEquals(22, requests.size)
            requests.forEach { requested ->
                val resolved = QuestCombatPlacement.resolve(runtime.instance, requested)
                assertTrue(abs(resolved.x() - requested.x()) <= 7.0)
                assertTrue(abs(resolved.z() - requested.z()) <= 7.0)
                assertTrue(abs(resolved.y() - requested.y()) <= 2.0)
                assertSupportedAndClear(runtime.instance, resolved)
            }
        } finally {
            runtime.close()
        }
    }

    private fun assertSupportedAndClear(arena: InstanceContainer, point: Pos) {
        assertTrue(arena.getBlock(point.sub(0.0, 0.1, 0.0)).isSolid, "Unsupported combat spawn: $point")
        // Independently inspect the full enlarged boss height, not the production helper's sample list.
        for (sample in 0..26) {
            val block = arena.getBlock(point.add(0.0, 0.05 + sample * 0.1, 0.0))
            assertFalse(block.isSolid || block.isLiquid, "Combat body intersects ${block.name()} at $point (sample $sample)")
        }
    }

    private fun withArena(test: (InstanceContainer) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val arena = MinecraftServer.getInstanceManager().createInstanceContainer()
        arena.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.STONE) }
        arena.loadChunk(0, 0).get(10, TimeUnit.SECONDS)
        try {
            test(arena)
        } finally {
            MinecraftServer.getInstanceManager().unregisterInstance(arena)
        }
    }
}
