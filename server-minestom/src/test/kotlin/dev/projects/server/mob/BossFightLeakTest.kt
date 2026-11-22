package dev.projects.server.mob

import dev.projects.server.coreloop.adventure.BossArenaFactory
import dev.projects.server.coreloop.adventure.BossArenaLeakTest
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import kotlin.test.Test

/**
 * A boss fight's Blockbench model runs its animation as a scheduler task; disposing the fight and its arena must let
 * both go without a running server too (2026-11-08: ~0.9 MB kept per fight). Here beside the boss tests, not in
 * coreloop: the first fight with a model loads ~270 MB that the JVM keeps (model data), and loading that before the
 * coreloop tests ran the default 512 MB test heap out.
 */
class BossFightLeakTest {
    @Test
    fun `disposed boss fights are released`() {
        MinecraftServer.init(Auth.Offline())
        // before the fix: +~90 MB, all 100 reachable
        BossArenaLeakTest().assertReleased("fight", 10, 100, 16) { i ->
            val arena = BossArenaFactory.create(if (i % 2 == 0) "shade" else "sakura", 1)
            val combat = QuestEncounterCombat(arena.instance, 1, emptyList(), arena.bossSpawn, { _, _ -> }, { _, _ -> },
                explicitBossArchetype = arena.archetype)
            for (t in 1..5) combat.tick(t * 50L)
            combat.dispose(); arena.dispose()
            arena.instance
        }
    }
}
