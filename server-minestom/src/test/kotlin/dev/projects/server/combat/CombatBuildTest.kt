package dev.projects.server.combat

import dev.projects.server.combat.damage.DamageType
import dev.projects.server.equipment.BaseStatRoll
import dev.projects.server.equipment.EquipmentCategory
import dev.projects.server.equipment.EquipmentItem
import dev.projects.server.equipment.EquipmentModSlot
import dev.projects.server.equipment.EquipmentRarity
import dev.projects.server.equipment.EquipmentSlot
import dev.projects.server.equipment.EquipmentTier
import dev.projects.server.mod.AttackTag
import dev.projects.server.mod.ModEffect
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModRank
import dev.projects.server.mod.ModStackingLayer
import dev.projects.server.mod.ModTagMatchPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatBuildTest {
    private val normalTags = setOf(AttackTag.MELEE, AttackTag.PHYSICAL, AttackTag.NORMAL_ATTACK)

    @Test
    fun `resolver combines base stats and valid mods by stacking layer`() {
        val keen = numericDefinition(
            id = "projects:keen-edge",
            statId = CombatStatIds.PHYSICAL_ATTACK,
            minimum = 1.0,
            maximum = 3.0,
            layer = ModStackingLayer.BASE_FLAT,
        )
        val executioner = numericDefinition(
            id = "projects:executioner",
            statId = CombatStatIds.INCREASED_DAMAGE,
            minimum = 0.1,
            maximum = 0.1,
            layer = ModStackingLayer.INCREASED,
        )
        val item = equipment(
            EquipmentModSlot(0, ModEntry(keen.modId, keen.rank, 2.5, 0, keen.definitionRevision)),
            EquipmentModSlot(1, ModEntry(executioner.modId, executioner.rank, 0.1, 1, executioner.definitionRevision)),
        )

        val resolved = CombatBuildResolver.resolve(
            equipment = item,
            definitions = mapOf(keen.modId to keen, executioner.modId to executioner),
            attackTags = normalTags,
        )

        assertEquals(12.5, resolved.stats.baseAttackPower)
        assertEquals(2.5, resolved.stats.flatAttackPower)
        assertEquals(15.0, resolved.stats.attackPower)
        assertEquals(0.1, resolved.stats.damageIncreasePercent)
        assertEquals(listOf("projects:keen-edge", "projects:executioner"), resolved.appliedModIds)
        assertTrue(resolved.ignoredModIds.isEmpty())
    }

    @Test
    fun `element mods are typed and tag applicability is server resolved`() {
        val ember = elementDefinition("projects:ember", Element.FIRE)
        val frost = elementDefinition("projects:frost", Element.ICE)
        val item = equipment(
            EquipmentModSlot(0, ModEntry(ember.modId, ember.rank, 2.0, 0, ember.definitionRevision)),
            EquipmentModSlot(1, ModEntry(frost.modId, frost.rank, 1.0, 1, frost.definitionRevision)),
        )

        val resolved = CombatBuildResolver.resolve(
            equipment = item,
            definitions = mapOf(ember.modId to ember, frost.modId to frost),
            attackTags = normalTags,
        )

        assertEquals(2.0, resolved.elementPower(Element.FIRE))
        assertEquals(1.0, resolved.elementPower(Element.ICE))
        assertEquals(listOf("projects:ember", "projects:frost"), resolved.appliedModIds)
        val validation = dev.projects.server.mod.ModValidation.validate(
            ModEntry(ember.modId, ember.rank, 2.0, 0, ember.definitionRevision),
            ember,
            EquipmentSlot.WEAPON,
        )
        assertFalse(validation.contribution != null)
        assertTrue(validation.effect != null)
    }

    @Test
    fun `wrong tags invalid rolls and unknown stats are ignored without reinterpretation`() {
        val magicOnly = ModDefinition(
            modId = "projects:magic-only",
            rank = ModRank.RANK_1,
            allowedSlots = setOf(EquipmentSlot.WEAPON),
            requiredTags = setOf(AttackTag.MAGIC),
            excludedTags = emptySet(),
            statId = CombatStatIds.PHYSICAL_ATTACK,
            minimumValue = 1.0,
            maximumValue = 2.0,
            stackingLayer = ModStackingLayer.BASE_FLAT,
            definitionRevision = 1,
            tagMatchPolicy = ModTagMatchPolicy.ALL_REQUIRED,
        )
        val unknown = numericDefinition(
            id = "projects:unknown-stat-mod",
            statId = "projects:not-supported",
            minimum = 1.0,
            maximum = 2.0,
            layer = ModStackingLayer.BASE_FLAT,
        )
        val item = equipment(
            EquipmentModSlot(0, ModEntry(magicOnly.modId, magicOnly.rank, 1.0, 0, magicOnly.definitionRevision)),
            EquipmentModSlot(1, ModEntry(unknown.modId, unknown.rank, 1.0, 1, unknown.definitionRevision)),
        )

        val resolved = CombatBuildResolver.resolve(
            equipment = item,
            definitions = mapOf(magicOnly.modId to magicOnly, unknown.modId to unknown),
            attackTags = normalTags,
        )

        assertTrue(resolved.appliedModIds.isEmpty())
        assertEquals(listOf(magicOnly.modId, unknown.modId), resolved.ignoredModIds)
        assertEquals(listOf(unknown.statId), resolved.ignoredStatIds)
        assertEquals(12.5, resolved.stats.attackPower)
    }

    @Test
    fun `damage resolver keeps physical lineage separate from element application`() {
        val ember = elementDefinition("projects:ember", Element.FIRE)
        val item = equipment(
            EquipmentModSlot(0, ModEntry(ember.modId, ember.rank, 2.0, 0, ember.definitionRevision)),
            EquipmentModSlot.empty(1),
        )
        val build = CombatBuildResolver.resolve(item, mapOf(ember.modId to ember), normalTags)

        val resolved = NormalAttackDamageResolver.resolve(
            build = build,
            damageType = DamageType.PHYSICAL,
            defense = 300.0,
        )

        assertEquals(6.25, resolved.direct.finalRoundedDamage)
        assertEquals(2.0, build.elementPower(Element.FIRE))
    }

    private fun equipment(vararg slots: EquipmentModSlot): EquipmentItem = EquipmentItem(
        itemId = "projects:test-twin-blades",
        category = EquipmentCategory.WEAPON,
        slot = EquipmentSlot.WEAPON,
        tier = EquipmentTier.T1,
        itemLevel = 5,
        rarity = EquipmentRarity.UNCOMMON,
        baseStatRolls = listOf(BaseStatRoll(CombatStatIds.PHYSICAL_ATTACK, 12.5)),
        modSlots = slots.toList(),
    )

    private fun numericDefinition(
        id: String,
        statId: String,
        minimum: Double,
        maximum: Double,
        layer: ModStackingLayer,
    ): ModDefinition = ModDefinition(
        modId = id,
        rank = ModRank.RANK_1,
        allowedSlots = setOf(EquipmentSlot.WEAPON),
        requiredTags = setOf(AttackTag.MELEE),
        excludedTags = setOf(AttackTag.MAGIC),
        statId = statId,
        minimumValue = minimum,
        maximumValue = maximum,
        stackingLayer = layer,
        definitionRevision = 1,
        tagMatchPolicy = ModTagMatchPolicy.ALL_REQUIRED,
    )

    private fun elementDefinition(id: String, element: Element): ModDefinition = ModDefinition(
        modId = id,
        rank = ModRank.RANK_1,
        allowedSlots = setOf(EquipmentSlot.WEAPON),
        requiredTags = setOf(AttackTag.MELEE),
        excludedTags = setOf(AttackTag.MAGIC),
        statId = CombatStatIds.ELEMENT_APPLICATION,
        minimumValue = 1.0,
        maximumValue = 2.0,
        stackingLayer = ModStackingLayer.BASE_FLAT,
        definitionRevision = 1,
        tagMatchPolicy = ModTagMatchPolicy.ALL_REQUIRED,
        effect = ModEffect.ElementApplication(element),
    )
}
