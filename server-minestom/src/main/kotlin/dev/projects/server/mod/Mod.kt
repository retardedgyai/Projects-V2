package dev.projects.server.mod

import dev.projects.server.equipment.EquipmentSlot
import dev.projects.server.equipment.EquipmentTier
import dev.projects.server.equipment.EquipmentStatContribution
import dev.projects.server.equipment.requireCanonicalId
import dev.projects.server.combat.Element

enum class AttackTag { MELEE, PROJECTILE, MAGIC, PHYSICAL, NORMAL_ATTACK, SKILL, SHATTER, FIRE, ICE, LIGHTNING }

enum class ModRank(val value: Int, val tier: EquipmentTier) {
    RANK_1(1, EquipmentTier.T1), RANK_2(2, EquipmentTier.T2), RANK_3(3, EquipmentTier.T3)
}

enum class ModStackingLayer { BASE_FLAT, BASE_PERCENT, INCREASED, CONDITIONAL, FINAL }
enum class ModTagMatchPolicy { ALL_REQUIRED, EXACT }

sealed interface ModEffect {
    data class ElementApplication(val element: Element) : ModEffect
}

data class ModEffectContribution(
    val effect: ModEffect,
    val value: Double,
    val sourceId: String,
) {
    init {
        require(value.isFinite() && value >= 0.0) { "effect value must be finite and non-negative" }
        requireCanonicalId("sourceId", sourceId)
    }
}

class ModDefinition(
    val modId: String,
    val rank: ModRank,
    allowedSlots: Set<EquipmentSlot>,
    requiredTags: Set<AttackTag>,
    excludedTags: Set<AttackTag>,
    val statId: String,
    val minimumValue: Double,
    val maximumValue: Double,
    val stackingLayer: ModStackingLayer,
    val definitionRevision: Long,
    val tagMatchPolicy: ModTagMatchPolicy = ModTagMatchPolicy.EXACT,
    val effect: ModEffect? = null,
) {
    val allowedSlots: Set<EquipmentSlot> = allowedSlots.toSet()
    val requiredTags: Set<AttackTag> = requiredTags.toSet()
    val excludedTags: Set<AttackTag> = excludedTags.toSet()

    init {
        requireCanonicalId("modId", modId)
        require(allowedSlots.isNotEmpty()) { "allowedSlots must not be empty" }
        require(requiredTags.intersect(excludedTags).isEmpty()) { "required/excluded tags overlap" }
        requireCanonicalId("statId", statId)
        require(minimumValue.isFinite() && maximumValue.isFinite() && minimumValue <= maximumValue) {
            "MOD value range must be finite and ordered"
        }
        require(definitionRevision >= 0) { "definitionRevision must be non-negative" }
    }

    fun acceptsAttackTags(actualTags: Set<AttackTag>): Boolean {
        if (actualTags.any { it in excludedTags }) return false
        return when (tagMatchPolicy) {
            ModTagMatchPolicy.EXACT -> actualTags == requiredTags
            ModTagMatchPolicy.ALL_REQUIRED -> actualTags.containsAll(requiredTags)
        }
    }
}

data class ModEntry(
    val modId: String,
    override val rank: ModRank,
    val rolledValue: Double,
    override val slotIndex: Int,
    val definitionRevision: Long = 0
) : ModSlotEntry {
    init {
        requireCanonicalId("modId", modId)
        require(rolledValue.isFinite()) { "rolledValue must be finite" }
        require(slotIndex in 0..3) { "slotIndex must be 0..3" }
        require(definitionRevision >= 0) { "definitionRevision must be non-negative" }
    }
}

interface ModSlotEntry {
    val slotIndex: Int
    val rank: ModRank
}

data class ModValidation(
    val valid: Boolean,
    val issues: List<String>,
    val contribution: EquipmentStatContribution? = null,
    val effect: ModEffectContribution? = null,
) {
    companion object {
        fun validate(entry: ModSlotEntry?, definition: ModDefinition?, equipmentSlot: EquipmentSlot): ModValidation {
            if (entry !is ModEntry) return ModValidation(false, listOf("unsupported MOD entry"))
            if (definition == null) return ModValidation(false, listOf("unknown MOD definition"))
            val issues = buildList {
                if (entry.modId != definition.modId) add("modId")
                if (entry.rank != definition.rank) add("rank")
                if (equipmentSlot !in definition.allowedSlots) add("equipmentSlot")
                if (entry.definitionRevision != definition.definitionRevision) add("definitionRevision")
                if (entry.rolledValue !in definition.minimumValue..definition.maximumValue) add("rolledValue")
                if (definition.effect != null && entry.rolledValue < 0.0) add("effectValue")
            }
            return if (issues.isEmpty()) {
                ModValidation(
                    valid = true,
                    issues = emptyList(),
                    contribution = if (definition.effect == null) {
                        EquipmentStatContribution(definition.statId, entry.rolledValue, entry.modId)
                    } else {
                        null
                    },
                    effect = definition.effect?.let { ModEffectContribution(it, entry.rolledValue, entry.modId) },
                )
            } else {
                ModValidation(false, issues)
            }
        }
    }
}
