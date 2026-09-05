package dev.projects.server.mob

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobAbilityManagerTest {
    private val origin = Pos(10.0, 40.0, 10.0)
    private val target = origin.add(0.0, 0.0, 2.0)
    private val forward = Vec(0.0, 0.0, 1.0)

    @Test
    fun `sweep uses its radius angle vertical reach and facing`() {
        val shape = MobAttackShape.Sweep(3.0, 60.0)
        assertTrue(shape.contains(origin, forward, target))
        assertTrue(shape.contains(origin, forward, origin.add(0.0, 0.0, 3.0)))
        assertFalse(shape.contains(origin, forward, origin.add(0.0, 0.0, 3.01)))
        assertFalse(shape.contains(origin, forward, origin.add(0.0, 0.0, -1.0)))
        assertTrue(shape.contains(origin, forward, origin.add(sin(Math.PI / 3) * 3, 0.0, cos(Math.PI / 3) * 3)))
        assertFalse(shape.contains(origin, forward, origin.add(2.7, 0.0, 1.2)))
        assertFalse(shape.contains(origin, forward, target.add(0.0, 2.3, 0.0)))
        assertTrue(shape.contains(origin, Vec(1.0, 0.0, 0.0), origin.add(2.0, 0.0, 0.0)))
    }

    @Test
    fun `slam rectangle excludes behind and narrow side misses`() {
        val shape = MobAttackShape.Slam(5.0, 1.0)
        assertTrue(shape.contains(origin, forward, origin.add(1.0, 0.0, 5.0)))
        assertFalse(shape.contains(origin, forward, origin.add(1.01, 0.0, 3.0)))
        assertFalse(shape.contains(origin, forward, origin.add(0.0, 0.0, -0.01)))
        assertFalse(shape.contains(origin, forward, origin.add(0.0, 0.0, 5.01)))
        assertFalse(shape.contains(origin, forward, target.add(0.0, -2.3, 0.0)))
        assertTrue(shape.contains(origin, Vec(1.0, 0.0, 0.0), origin.add(5.0, 0.0, 1.0)))
    }

    @Test
    fun `every warning outline sample is on the corresponding damage shape`() {
        for (shape in listOf(MobAttackShape.Sweep(), MobAttackShape.Slam())) {
            for (direction in listOf(forward, Vec(-0.4, 0.0, 0.8))) {
                assertTrue(shape.outline(origin, direction).all { shape.contains(origin, direction, it) })
            }
        }
    }

    @Test
    fun `manager permits only one ability and rejects targets outside usable distance`() {
        val manager = MobAbilityManager(listOf(ability("sweep")))
        assertNull(manager.tryStart(0, origin, origin.add(0.0, 0.0, 8.0)))
        assertNull(manager.tryStart(0, origin, target.add(0.0, 3.0, 0.0)))
        assertNotNull(manager.tryStart(0, origin, target))
        assertNull(manager.tryStart(0, origin, target))
    }

    @Test
    fun `early target motion tracks but motion after lock cannot turn attack`() {
        val manager = MobAbilityManager(listOf(ability("sweep")))
        manager.tryStart(0, origin, target)
        manager.tick(100, origin.add(2.0, 0.0, 0.0))
        assertEquals(Vec(1.0, 0.0, 0.0), manager.current!!.facing)
        val locked = manager.tick(400, origin.add(-2.0, 0.0, 0.0)).single() as MobAbilityEvent.Locked
        assertEquals(Vec(1.0, 0.0, 0.0), locked.frame.facing)
        val hit = manager.tick(1000, origin.add(-2.0, 0.0, 0.0)).single() as MobAbilityEvent.Hit
        assertEquals(Vec(1.0, 0.0, 0.0), hit.frame.facing)
        assertEquals(origin, hit.frame.origin)
    }

    @Test
    fun `active hit occurs once even with late or repeated ticks`() {
        val manager = MobAbilityManager(listOf(ability("sweep")))
        manager.tryStart(0, origin, target)
        val lateEvents = manager.tick(2000, target)
        assertEquals(1, lateEvents.filterIsInstance<MobAbilityEvent.Hit>().size)
        assertEquals(1, lateEvents.filterIsInstance<MobAbilityEvent.Finished>().size)
        assertTrue(manager.tick(2000, target).isEmpty())
        assertTrue(manager.tick(3000, target).isEmpty())
    }

    @Test
    fun `cancel during telegraph or locked stage leaves no future hit`() {
        for (cancelAt in listOf(0L, 500L)) {
            val manager = MobAbilityManager(listOf(ability("sweep")))
            manager.tryStart(0, origin, target)
            manager.tick(cancelAt, target)
            manager.cancel()
            assertNull(manager.current)
            assertTrue(manager.tick(10_000, target).isEmpty())
        }
    }

    @Test
    fun `global and individual cooldowns prevent immediate repeats`() {
        val manager = MobAbilityManager(listOf(ability("sweep", cooldown = 2000)), globalCooldownMillis = 300)
        manager.tryStart(0, origin, target)
        manager.tick(1000, target)
        manager.tick(1500, target)
        assertNull(manager.tryStart(1501, origin, target))
        assertNull(manager.tryStart(1800, origin, target))
        assertNotNull(manager.tryStart(3000, origin, target))
    }

    @Test
    fun `selection avoids repeated attack whenever another available attack exists`() {
        val manager = MobAbilityManager(listOf(ability("sweep", 0), ability("slam", 0)), Random(13), 0)
        var previous: String? = null
        repeat(10) { index ->
            val started = assertNotNull(manager.tryStart(index * 2000L, origin, target))
            assertNotEquals(previous, started.frame.ability.id)
            previous = started.frame.ability.id
            manager.tick(index * 2000L + 1600L, target)
        }
    }

    @Test
    fun `reset clears dead encounter cooldown and selection state`() {
        val manager = MobAbilityManager(listOf(ability("sweep", 90_000)))
        manager.tryStart(0, origin, target)
        manager.tick(1000, target)
        manager.reset()
        assertNotNull(manager.tryStart(1001, origin, target))
    }

    private fun ability(id: String, cooldown: Long = 2000) = MobAbility(
        id, id, MobAttackShape.Sweep(), 4.0, 10.0,
        telegraphMillis = 1000, trackingMillis = 400, recoveryMillis = 500, cooldownMillis = cooldown,
    )
}
