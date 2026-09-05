package dev.projects.server.mob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestMobLifeTest {
    @Test
    fun `lethal damage terminal gate accepts only the first kill`() {
        val life = QuestMobLife(44.0)
        assertTrue(life.damage(30.0))
        assertEquals(14.0, life.health)
        assertTrue(life.damage(50.0))
        assertEquals(0.0, life.health)
        assertEquals(QuestMobPhase.DEAD, life.phase)
        assertFalse(life.damage(50.0))
        life.finishReturn()
        assertEquals(QuestMobPhase.DEAD, life.phase)
        assertEquals(0.0, life.health)
    }

    @Test
    fun `returning mob cannot be farmed and heals only on return completion`() {
        val life = QuestMobLife(44.0)
        life.damage(30.0)
        life.phase = QuestMobPhase.RETURNING
        assertFalse(life.damage(100.0))
        assertEquals(14.0, life.health)
        life.finishReturn()
        assertEquals(44.0, life.health)
        assertTrue(life.damage(10.0))
    }

    @Test
    fun `disposal and invalid numeric input cannot create rewards or corrupt health`() {
        val life = QuestMobLife(44.0)
        for (amount in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) assertFalse(life.damage(amount))
        assertEquals(44.0, life.health)
        life.phase = QuestMobPhase.DISPOSED
        assertFalse(life.damage(100.0))
        life.finishReturn()
        assertEquals(QuestMobPhase.DISPOSED, life.phase)
    }
}
