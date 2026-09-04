package dev.projects.server.mob

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobGroundTelegraphTest {
    @Test
    fun `all content shapes rasterize under budget with their centers inside hit shape`() {
        val shapes = listOf(MobAttackShape.Sweep(4.2), MobAttackShape.Slam(15.0, 1.0),
            MobAttackShape.Ring(6.0, 2.0), MobAttackShape.Ring(4.3))
        for (shape in shapes) for (yaw in 0..345 step 15) {
            val frame = frame(shape).copy(facing = Pos.ZERO.withYaw(yaw.toFloat()).direction())
            val tiles = MobGroundRaster.tiles(frame) { 40.0 }
            assertTrue(tiles.isNotEmpty())
            assertTrue(tiles.size <= 64)
            assertTrue(tiles.all { shape.contains(frame.origin, frame.facing, it.center) })
            assertTrue(tiles.all { it.depth == 0.5 }, "Flat terrain must retain fine half-block rows")
        }
    }

    @Test
    fun `ring warning leaves safe center unpainted`() {
        val tiles = MobGroundRaster.tiles(frame(MobAttackShape.Ring(6.0, 2.3))) { 40.0 }
        assertFalse(tiles.any { tile ->
            abs(tile.center.x() - 8.5) < tile.width / 2.0 && abs(tile.center.z() - 8.5) < tile.depth / 2.0
        })
    }

    @Test
    fun `terrain height discontinuity splits surfaces without bridging stone ledge`() {
        val tiles = MobGroundRaster.tiles(frame(MobAttackShape.Slam(6.0, 2.0))) { if (it.x() < 8.0) 40.0 else 41.0 }
        assertEquals(setOf(40.0, 41.0), tiles.map { it.center.y() }.toSet())
        assertTrue(tiles.all { it.center.x() + it.width / 2.0 <= 8.0 || it.center.x() - it.width / 2.0 >= 8.0 })
        assertTrue(MobGroundRaster.tiles(frame(MobAttackShape.Ring(3.0))) { null }.isEmpty())
    }

    @Test
    fun `vanilla backgrounds are red translucent upward fixed surfaces without visible glyph`() = world { instance ->
        val telegraph = MobGroundTelegraph(instance)
        val owner = UUID.randomUUID()
        try {
            assertTrue(telegraph.show(owner, frame(MobAttackShape.Slam(6.0, 1.0)), false, 0))
            assertTrue(telegraph.displayCount > 0)
            val display = instance.entities.first { it.entityType == EntityType.TEXT_DISPLAY }
            val meta = display.entityMeta as TextDisplayMeta
            assertEquals(MobGroundTelegraph.TRACKING_COLOR, meta.backgroundColor)
            assertEquals(0x48, meta.backgroundColor ushr 24)
            assertEquals(AbstractDisplayMeta.BillboardConstraints.FIXED, meta.billboardRenderConstraints)
            assertEquals(0, meta.textOpacity.toInt())
            assertFalse(meta.isSeeThrough)
            assertTrue(meta.leftRotation[0] < -0.7f && meta.leftRotation[3] > 0.7f)
            assertEquals(0.045, meta.translation.y(), 0.000001)
            assertTrue(meta.scale.x() > 0 && meta.scale.y() > 0)
            assertEquals(40.0, display.position.y())
        } finally { telegraph.dispose() }
        assertTrue(instance.entities.none { it.entityType == EntityType.TEXT_DISPLAY })
    }

    @Test
    fun `tracking reuses entities lock recolors and impact expires after one short flash`() = world { instance ->
        val telegraph = MobGroundTelegraph(instance)
        val owner = UUID.randomUUID()
        val initial = frame(MobAttackShape.Ring(3.0))
        try {
            telegraph.show(owner, initial, false, 0)
            val initialIds = instance.entities.map { it.uuid }.toSet()
            telegraph.show(owner, initial, false, 180)
            assertEquals(initialIds, instance.entities.map { it.uuid }.toSet())
            telegraph.show(owner, initial.copy(phase = MobAbilityPhase.LOCKED), false, 400)
            assertTrue(instance.entities.all { (it.entityMeta as TextDisplayMeta).backgroundColor == MobGroundTelegraph.LOCKED_COLOR })
            telegraph.show(owner, initial.copy(phase = MobAbilityPhase.LOCKED), true, 1000)
            assertTrue(instance.entities.all { (it.entityMeta as TextDisplayMeta).backgroundColor == MobGroundTelegraph.IMPACT_COLOR })
            telegraph.tick(1179)
            assertTrue(telegraph.displayCount > 0)
            telegraph.tick(1180)
            assertEquals(0, telegraph.displayCount)
            assertTrue(instance.entities.isEmpty())
        } finally { telegraph.dispose() }
    }

    @Test
    fun `map display cap defers seventh attack and releasing a slot allows it`() = world { instance ->
        val telegraph = MobGroundTelegraph(instance)
        val owners = List(7) { UUID.randomUUID() }
        try {
            owners.take(6).forEach { assertTrue(telegraph.show(it, frame(MobAttackShape.Ring(6.0, 2.0)), false, 0)) }
            assertEquals(6, telegraph.activeCount)
            assertTrue(telegraph.displayCount <= MobGroundTelegraph.MAX_DISPLAYS)
            assertFalse(telegraph.canStart(owners.last()))
            assertFalse(telegraph.show(owners.last(), frame(MobAttackShape.Ring(6.0)), false, 0))
            telegraph.clear(owners.first())
            assertTrue(telegraph.show(owners.last(), frame(MobAttackShape.Ring(6.0)), false, 0))
            telegraph.dispose()
            telegraph.dispose()
            assertEquals(0, telegraph.displayCount)
            assertTrue(instance.entities.isEmpty())
            assertFalse(telegraph.canStart(owners.first()))
        } finally { telegraph.dispose() }
    }

    @Test
    fun `ground projection skips cliffs outside vertical damage reach and nonexistent chunks`() = world { instance ->
        val telegraph = MobGroundTelegraph(instance)
        assertEquals(40.0, telegraph.groundHeight(Pos(8.0, 40.0, 8.0)))
        assertEquals(null, telegraph.groundHeight(Pos(8.0, 44.0, 8.0)))
        assertEquals(null, telegraph.groundHeight(Pos(1000.0, 40.0, 1000.0)))
        telegraph.dispose()
    }

    private fun frame(shape: MobAttackShape) = MobAbilityFrame(
        MobAbility("test", "試験", shape, 20.0, 10.0, 1000, 400, 500, 1000),
        Pos(8.5, 40.0, 8.5), Vec(0.0, 0.0, 1.0), MobAbilityPhase.TRACKING, 0,
    )

    private fun world(test: (InstanceContainer) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        for (x in -1..1) for (z in -1..1) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        try { test(instance) } finally { MinecraftServer.getInstanceManager().unregisterInstance(instance) }
    }
}
