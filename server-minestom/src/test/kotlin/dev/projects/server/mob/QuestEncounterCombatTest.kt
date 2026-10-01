package dev.projects.server.mob

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.instance.block.Block
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestEncounterCombatTest {
    @Test fun `slow sources cannot borrow strength or expiry and removing one garden preserves the rest`() {
        MinecraftServer.init(Auth.Offline())
        val instance=MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0,40,Block.STONE) }
        instance.loadChunk(0,0).get(10,TimeUnit.SECONDS)
        val combat=QuestEncounterCombat(instance,1,listOf(QuestCombatEncounter(listOf(Pos(2.0,40.0,2.0)))),Pos(12.0,40.0,12.0),{_,_->},{_,_->})
        try {
            val mob=combat.entities().first { !combat.isBoss(it.uuid) }
            val garden=UUID.randomUUID();val otherGarden=UUID.randomUUID()
            combat.tick(1000);val base=mob.getAttribute(Attribute.MOVEMENT_SPEED).baseValue
            combat.applySlow(mob.uuid,.2,3000) // Long ice MOD / another weaker effect.
            combat.applySlow(mob.uuid,.6,1000) // Strong effect expires at 2000.
            combat.applySlow(mob.uuid,.4,350,garden)
            assertEquals(base*.4,mob.getAttribute(Attribute.MOVEMENT_SPEED).baseValue,.00001)
            combat.tick(1900);combat.applySlow(mob.uuid,.4,350,garden)
            combat.tick(2001)
            assertEquals(base*.6,mob.getAttribute(Attribute.MOVEMENT_SPEED).baseValue,.00001,"The strong slow must expire while the garden remains occupied")
            combat.applySlow(mob.uuid,.5,500,otherGarden)
            combat.clearSlowSource(garden)
            assertEquals(base*.5,mob.getAttribute(Attribute.MOVEMENT_SPEED).baseValue,.00001)
            combat.tick(2502)
            assertEquals(base*.8,mob.getAttribute(Attribute.MOVEMENT_SPEED).baseValue,.00001,"Only the long weaker effect remains")
            combat.tick(4001);assertEquals(base,mob.getAttribute(Attribute.MOVEMENT_SPEED).baseValue,.00001)
        } finally { combat.dispose();MinecraftServer.getInstanceManager().unregisterInstance(instance) }
    }
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
            assertTrue(combat.entities().all { it.aiGroups.isEmpty() })
            assertEquals(setOf(EntityType.VINDICATOR, EntityType.HUSK, EntityType.EVOKER), combat.entities().map { it.entityType }.toSet())
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
