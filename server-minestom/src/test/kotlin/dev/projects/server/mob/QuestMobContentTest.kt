package dev.projects.server.mob

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestMobContentTest {
    @Test
    fun `seeds select reproducible mixed groups and three distinct bosses`() {
        val groups = (0..3).map { QuestMobContent.composition(30, it, 3) }
        assertEquals(groups, (0..3).map { QuestMobContent.composition(30, it, 3) })
        assertEquals(setOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.SHIELD_GUARD,
            QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.ELITE_BRUTE), groups.flatten().toSet())
        assertTrue(groups.all { it.distinct().size >= 2 })
        assertEquals(3, (0L..2L).map(QuestMobContent::boss).distinct().size)
        assertEquals(QuestMobArchetype.EXECUTIONER, QuestMobContent.boss(0))
        assertTrue(QuestMobContent.boss(Long.MIN_VALUE).rarity == QuestMobRarity.BOSS)
    }

    @Test
    fun `all content has actual distinct equipment abilities weaknesses and tier scaling`() {
        val definitions = QuestMobArchetype.entries.map { QuestMobContent.definition(1, it) }
        assertEquals(13, definitions.size)
        assertEquals(5, definitions.map { it.entityType }.distinct().size)
        assertEquals(setOf("fire", "ice", "lightning"), definitions.map { it.archetype.weakness }.toSet())
        for (definition in definitions) {
            val tier4 = QuestMobContent.definition(4, definition.archetype)
            assertEquals(definition.maximumHealth * 1.65.pow(3), tier4.maximumHealth, 0.0001)
            assertTrue(definition.abilities.size >= 2)
            definition.abilities.zip(tier4.abilities).forEach { (one, four) ->
                assertEquals(one.damage * 1.65.pow(3), four.damage, 0.0001)
                assertTrue(one.trackingMillis < one.telegraphMillis)
            }
        }
        assertTrue(QuestMobContent.definition(1, QuestMobArchetype.SHIELD_GUARD).frontalDamageMultiplier < 1)
        assertTrue(QuestMobContent.definition(1, QuestMobArchetype.RIFT_CASTER).preferredDistance >= 8)
        assertTrue(QuestMobContent.definition(1, QuestMobArchetype.RIFT_ORACLE).abilities.any { it.anchor == MobAbilityAnchor.TARGET })
        assertTrue(QuestMobContent.definition(1, QuestMobArchetype.EXECUTIONER).abilities.any { it.maximumHealthRatio < 1 })
    }

    @Test
    fun `ring shares outer and inner boundaries with damage and preserves safe center`() {
        val ring = MobAttackShape.Ring(6.0, 2.0)
        val origin = Pos(0.0, 40.0, 0.0)
        val forward = Vec(0.0, 0.0, 1.0)
        assertFalse(ring.contains(origin, forward, origin))
        assertFalse(ring.contains(origin, forward, origin.add(1.99, 0.0, 0.0)))
        assertTrue(ring.contains(origin, forward, origin.add(2.0, 0.0, 0.0)))
        assertTrue(ring.contains(origin, forward, origin.add(6.0, 0.0, 0.0)))
        assertFalse(ring.contains(origin, forward, origin.add(6.01, 0.0, 0.0)))
        assertFalse(ring.contains(origin, forward, origin.add(3.0, 2.3, 0.0)))
        assertTrue(ring.outline(origin, forward).all { ring.contains(origin, forward, it) })
    }

    @Test
    fun `target anchored spell tracks early then locks its position and damages once`() {
        val ability = QuestMobContent.definition(1, QuestMobArchetype.RIFT_ORACLE).abilities.first()
        val manager = MobAbilityManager(listOf(ability), Random(1))
        val origin = Pos(0.0, 40.0, 0.0)
        val first = origin.add(0.0, 0.0, 10.0)
        assertEquals(first, manager.tryStart(0, origin, first)?.frame?.origin)
        val tracked = first.add(1.0, 0.0, 0.0)
        manager.tick(400, tracked)
        assertEquals(tracked, manager.current?.origin)
        manager.tick(500, first.add(10.0, 0.0, 0.0))
        assertEquals(tracked, manager.current?.origin)
        assertEquals(MobAbilityPhase.LOCKED, manager.current?.phase)
        val hit = manager.tick(1500, first.add(20.0, 0.0, 0.0)).filterIsInstance<MobAbilityEvent.Hit>().single()
        assertEquals(tracked, hit.frame.origin)
        assertTrue(hit.frame.ability.shape.contains(hit.frame.origin, hit.frame.facing, tracked))
        assertFalse(hit.frame.ability.shape.contains(hit.frame.origin, hit.frame.facing, first.add(20.0, 0.0, 0.0)))
        assertTrue(manager.tick(1600, first).none { it is MobAbilityEvent.Hit })
        manager.cancel()
        assertTrue(manager.tick(10_000, first).isEmpty())
    }

    @Test
    fun `minimum range and wounded-only abilities are selection gates`() {
        val origin = Pos(0.0, 40.0, 0.0)
        val bolt = QuestMobContent.definition(1, QuestMobArchetype.RIFT_CASTER).abilities.first()
        val ranged = MobAbilityManager(listOf(bolt))
        assertNull(ranged.tryStart(0, origin, origin.add(0.0, 0.0, 2.9)))
        assertNotNull(ranged.tryStart(0, origin, origin.add(0.0, 0.0, 10.0)))
        val execution = QuestMobContent.definition(1, QuestMobArchetype.EXECUTIONER).abilities.last()
        val wounded = MobAbilityManager(listOf(execution))
        assertNull(wounded.tryStart(0, origin, origin.add(0.0, 0.0, 4.0), 0.51))
        assertNotNull(wounded.tryStart(0, origin, origin.add(0.0, 0.0, 4.0), 0.5))
    }
}
