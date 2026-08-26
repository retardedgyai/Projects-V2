package dev.projects.server.equipment

import dev.projects.server.mod.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LootDropTest {
    private val definitions = listOf(
        "keen-edge" to "projects:physical-attack",
        "critical-focus" to "projects:critical-chance",
        "skill-drive" to "projects:skill-power",
        "execution-mark" to "projects:boss-damage",
        "steady-hand" to "projects:attack-speed",
        "shatter-point" to "projects:shatter-damage",
        "frostbite" to "projects:ice-damage",
        "last-stand" to "projects:conditional-damage",
    ).map { (id, stat) -> ModDefinition("projects:$id", ModRank.RANK_1, setOf(EquipmentSlot.WEAPON), emptySet(), emptySet(), stat, 1.0, 3.0, ModStackingLayer.BASE_FLAT, 1) } +
        listOf(
            ModDefinition("projects:keen-edge-2", ModRank.RANK_2, setOf(EquipmentSlot.WEAPON), emptySet(), emptySet(), "projects:physical-attack-2", 2.0, 5.0, ModStackingLayer.BASE_FLAT, 1),
            ModDefinition("projects:critical-focus-2", ModRank.RANK_2, setOf(EquipmentSlot.WEAPON), emptySet(), emptySet(), "projects:critical-chance-2", 2.0, 5.0, ModStackingLayer.INCREASED, 1),
            ModDefinition("projects:skill-drive-2", ModRank.RANK_2, setOf(EquipmentSlot.WEAPON), emptySet(), emptySet(), "projects:skill-power-2", 2.0, 5.0, ModStackingLayer.INCREASED, 1),
            ModDefinition("projects:execution-mark-2", ModRank.RANK_2, setOf(EquipmentSlot.WEAPON), emptySet(), emptySet(), "projects:boss-damage-2", 2.0, 5.0, ModStackingLayer.CONDITIONAL, 1),
            ModDefinition("projects:steady-hand-2", ModRank.RANK_2, setOf(EquipmentSlot.WEAPON), emptySet(), emptySet(), "projects:attack-speed-2", 2.0, 5.0, ModStackingLayer.INCREASED, 1),
        )

    @Test
    fun `same seed produces same valid loot`() {
        val generator = LootGenerator(definitions)
        val first = generator.generate(103L, V0_LOOT_PROFILES.getValue(LootSource.NORMAL_ENEMY))
        val second = generator.generate(103L, V0_LOOT_PROFILES.getValue(LootSource.NORMAL_ENEMY))
        assertEquals(first.displayName, second.displayName)
        assertEquals(first.source, second.source)
        assertEquals(first.item.itemId, second.item.itemId)
        assertEquals(first.item.rarity, second.item.rarity)
        assertEquals(first.item.baseStatRolls, second.item.baseStatRolls)
        assertEquals(first.item.modSlots, second.item.modSlots)
        assertEquals(first.item.rarity.modCapacity, first.item.modSlots.size)
        first.item.modSlots.forEach { slot ->
            val entry = slot.entry as ModEntry
            val definition = definitions.single { it.modId == entry.modId }
            assertTrue(ModValidation.validate(entry, definition, EquipmentSlot.WEAPON).valid)
            assertTrue(entry.rolledValue in definition.minimumValue..definition.maximumValue)
        }
    }

    @Test
    fun `boss profile is strictly better than normal profile`() {
        val normal = V0_LOOT_PROFILES.getValue(LootSource.NORMAL_ENEMY)
        val boss = V0_LOOT_PROFILES.getValue(LootSource.RIFT_EXECUTIONER)
        assertTrue(boss.tier.ordinal > normal.tier.ordinal)
        assertTrue(boss.minimumBaseAttack > normal.minimumBaseAttack)
        assertTrue(boss.qualityScore().second > normal.qualityScore().second)
    }
}
