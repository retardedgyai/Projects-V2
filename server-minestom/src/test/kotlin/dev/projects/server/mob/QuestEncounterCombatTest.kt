package dev.projects.server.mob

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.block.Block
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestEncounterCombatTest {
    @Test
    fun `real entity groups spawn without vanilla attacks and dispose with their map`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.STONE) }
        instance.loadChunk(0, 0).get(10, TimeUnit.SECONDS)
        val combat = QuestEncounterCombat(
            instance, 1,
            listOf(QuestCombatEncounter(listOf(Pos(2.0, 40.0, 2.0), Pos(5.0, 40.0, 2.0), Pos(8.0, 40.0, 2.0)))),
            Pos(12.0, 40.0, 12.0),
            onMobDefeated = { _, _ -> error("Creating and disposing a map must never grant rewards") },
            damagePlayer = { _, _ -> error("An empty map cannot attack players") },
        )
        try {
            assertEquals(4, combat.entities().size)
            assertEquals(4, combat.combatTargets().size)
            assertTrue(combat.entities().all { it.entityType == EntityType.VINDICATOR && it.aiGroups.isEmpty() })
            assertEquals(1, combat.entities().count { combat.isBoss(it.uuid) })
            assertEquals(300.0, combat.bossHealth())
            assertEquals(0, combat.clearedEncounterCount)
            assertFalse(combat.bossDefeated)
            combat.tick(1000L)
            combat.tick(5000L)
            val owned = combat.entities()
            combat.dispose()
            combat.dispose()
            combat.tick(6000L)
            assertTrue(combat.combatTargets().isEmpty())
            assertTrue(combat.entities().isEmpty())
            assertTrue(owned.all { it.isRemoved })
        } finally {
            combat.dispose()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }
}
