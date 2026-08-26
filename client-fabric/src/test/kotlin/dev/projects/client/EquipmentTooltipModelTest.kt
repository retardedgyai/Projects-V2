package dev.projects.client

import dev.projects.protocol.EquipmentPresentationMod
import dev.projects.protocol.EquipmentPresentationSnapshot
import dev.projects.protocol.EquipmentPresentationStat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquipmentTooltipModelTest {
    private val snapshot = EquipmentPresentationSnapshot(
        itemId = "projects:twin_blades",
        displayName = "Twin Blades",
        category = "weapon",
        slot = "main_hand",
        tier = "I",
        itemLevel = 5,
        rarity = "uncommon",
        baseStats = listOf(
            EquipmentPresentationStat("projects:physical-attack", 12.5),
            EquipmentPresentationStat("projects:unknown-stat", 2.0),
        ),
        installedMods = listOf(
            EquipmentPresentationMod(0, "projects:keen-edge", 1, 2.5, "projects:physical-attack", "base_flat"),
        ),
    )

    @Test
    fun formatsKnownAndUnknownStats() {
        val lines = EquipmentTooltipModel(snapshot).lines().map { it.string }

        assertEquals("Twin Blades", lines[0])
        assertEquals("UNCOMMON  •  TIER I", lines[1])
        assertTrue(lines.contains("12.5 PHYSICAL ATTACK"))
        assertTrue(lines.contains("+2 Unknown stat"))
        assertTrue(lines.contains("Keen Edge I  +2.5 Physical Attack"))
    }

    @Test
    fun formatsWholeValuesWithoutDecimalNoise() {
        assertEquals("12", EquipmentTooltipModel.formatValue(12.0))
        assertEquals("12.5", EquipmentTooltipModel.formatValue(12.5))
    }
}
