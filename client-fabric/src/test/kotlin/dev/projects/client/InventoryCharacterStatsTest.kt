package dev.projects.client

import dev.projects.protocol.ProgressionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class InventoryCharacterStatsTest {
    @Test
    fun `inventory presentation uses ProjectS progression and mana values`() {
        val progression = ProgressionSnapshot(4L, 8, 37, 100, 3, 1, emptyList())
        val presentation = InventoryCharacterPresentationSnapshot(progression, 64, 120)

        assertEquals(8, presentation.progression.level)
        assertEquals("経験値 37 / 100", inventoryCharacterXpText(presentation.progression))
        assertEquals(64, presentation.mana)
        assertEquals(120, presentation.maxMana)
    }

    @Test
    fun `max level progression uses max label`() {
        val progression = ProgressionSnapshot(5L, 45, 0, 0, 10, 10, emptyList())

        assertEquals("経験値 MAX", inventoryCharacterXpText(progression))
    }
}
