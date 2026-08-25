package dev.projects.server.equipment

import dev.projects.server.mod.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EquipmentAndModFoundationTest {
    @Test
    fun equipmentContractAndImmutability() {
        assertEquals(8, EquipmentSlot.entries.size)
        assertEquals(listOf(1, 2, 3, 4), EquipmentRarity.entries.map { it.modCapacity })
        for (level in 1..45) assertEquals(1, EquipmentTier.entries.count { it.contains(level) })
        assertEquals(EquipmentQuality.UNSPECIFIED, EquipmentQuality.entries.single())

        val rolls = mutableListOf(BaseStatRoll("projects:physical-attack", 12.5))
        val slots = mutableListOf(EquipmentModSlot.empty(0), EquipmentModSlot.empty(1))
        val item = EquipmentItem("starter_sword", EquipmentCategory.WEAPON, EquipmentSlot.WEAPON,
            EquipmentTier.T1, 1, EquipmentRarity.UNCOMMON, rolls, slots)
        rolls.clear(); slots.clear()
        assertEquals(1, item.baseStatRolls.size)
        assertEquals(2, item.modSlots.size)
        assertFailsWith<UnsupportedOperationException> { (item.baseStatRolls as MutableList).add(BaseStatRoll("projects:defense", 1.0)) }
    }

    @Test
    fun invalidEquipmentAndModsAreRejected() {
        assertFailsWith<IllegalArgumentException> { BaseStatRoll("projects:x", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { EquipmentModSlot.empty(4) }
        assertFailsWith<IllegalArgumentException> {
            EquipmentItem("bad", EquipmentCategory.WEAPON, EquipmentSlot.WEAPON, EquipmentTier.T1, 1,
                EquipmentRarity.UNCOMMON, emptyList(), listOf(EquipmentModSlot.empty(0), EquipmentModSlot.empty(0)))
        }
        assertFailsWith<IllegalArgumentException> {
            ModDefinition("projects:bad", ModRank.RANK_1, setOf(EquipmentSlot.WEAPON),
                setOf(AttackTag.MELEE), setOf(AttackTag.MELEE), "projects:attack", 1.0, 2.0,
                ModStackingLayer.BASE_FLAT, 0)
        }
    }

    @Test
    fun validationProducesExpectedContribution() {
        val definition = ModDefinition("projects:keen-edge", ModRank.RANK_1, setOf(EquipmentSlot.WEAPON),
            setOf(AttackTag.MELEE), setOf(AttackTag.MAGIC), "projects:physical-attack", 1.0, 3.0,
            ModStackingLayer.BASE_FLAT, 7)
        assertTrue(definition.acceptsAttackTags(setOf(AttackTag.MELEE)))
        assertFalse(definition.acceptsAttackTags(setOf(AttackTag.MELEE, AttackTag.PHYSICAL)))
        val result = ModValidation.validate(ModEntry("projects:keen-edge", ModRank.RANK_1, 2.5, 0, 7), definition, EquipmentSlot.WEAPON)
        assertTrue(result.valid)
        assertEquals(2.5, result.contribution!!.value)
        assertFalse(ModValidation.validate(ModEntry("projects:keen-edge", ModRank.RANK_1, 2.5, 0, 7), definition, EquipmentSlot.HEAD).valid)
    }

    @Test
    fun modTagMatchPolicyControlsRequiredTagMatching() {
        fun definition(policy: ModTagMatchPolicy) = ModDefinition(
            "projects:tagged", ModRank.RANK_1, setOf(EquipmentSlot.WEAPON),
            setOf(AttackTag.MELEE), emptySet(), "projects:attack", 1.0, 2.0,
            ModStackingLayer.BASE_FLAT, 0L, policy
        )
        val exact = definition(ModTagMatchPolicy.EXACT)
        val allRequired = definition(ModTagMatchPolicy.ALL_REQUIRED)
        val actualTags = setOf(AttackTag.MELEE, AttackTag.PHYSICAL)
        assertFalse(exact.acceptsAttackTags(actualTags))
        assertTrue(allRequired.acceptsAttackTags(actualTags))
    }
}
