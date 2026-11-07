package dev.projects.server.coreloop.adventure

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Disposing an arena must let it go even in a JVM that never starts the server (tests, tools): 2026-11-08 every
 * disposed arena stayed reachable through Minestom's dispatcher and scheduler queues (~0.5 MB per arena, ~8 MB per
 * instance dungeon, ~0.9 MB per boss fight; the fight is checked in mob.BossFightLeakTest), 6 GB ran out after ~750 dungeons.
 */
class BossArenaLeakTest {
    internal fun usedMb(): Long {
        repeat(3) { System.gc(); Thread.sleep(30) }
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
    }

    /** Runs [cycle] [warmup] + [count] times; the heap after GC may grow at most [maxGrowthMb] after the warmup. */
    internal fun assertReleased(name: String, warmup: Int, count: Int, maxGrowthMb: Long, cycle: (Int) -> Instance) {
        repeat(warmup) { cycle(it) }
        val before = usedMb()
        val disposed = (0 until count).map { WeakReference(cycle(warmup + it)) }
        val after = usedMb()
        val alive = disposed.count { it.get() != null }
        assertTrue(alive <= 1, "$name: $alive of $count disposed instances still reachable")
        assertTrue(after - before <= maxGrowthMb, "$name: heap $before MB -> $after MB over $count cycles")
    }

    @Test
    fun `disposed arenas are released`() {
        MinecraftServer.init(Auth.Offline())
        val ids = listOf("rift", "ritual", "trial", "shade", "sakura", "miniboss1")
        // before the fix: +~140 MB, all 300 reachable
        assertReleased("arena", 20, 300, 16) { i -> BossArenaFactory.create(ids[i % ids.size], 1).also { it.dispose() }.instance }
    }

    @Test
    fun `disposed instance dungeons are released`() {
        MinecraftServer.init(Auth.Offline())
        // before the fix: +~240 MB
        assertReleased("dungeon", 3, 30, 16) { InstanceDungeonFactory.create(1).arena.also { it.dispose() }.instance }
    }
}
