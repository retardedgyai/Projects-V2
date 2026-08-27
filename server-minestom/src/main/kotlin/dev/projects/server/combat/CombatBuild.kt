package dev.projects.server.combat

import dev.projects.server.combat.damage.DamageCalculator
import dev.projects.server.combat.damage.DamageMode
import dev.projects.server.combat.damage.DamageResult
import dev.projects.server.combat.damage.DamageType
import dev.projects.server.combat.damage.StatCalculator
import dev.projects.server.equipment.EquipmentItem
import dev.projects.server.mod.AttackTag
import dev.projects.server.mod.ModEffect
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModStackingLayer
import dev.projects.server.mod.ModValidation

enum class Element {
    FIRE,
    ICE,
    LIGHTNING,
}

object CombatStatIds {
    /** Existing #95 base stat ID. It is a flat attack-power contribution here. */
    const val PHYSICAL_ATTACK = "projects:physical-attack"
    const val FLAT_ATTACK = "projects:flat-attack"
    const val ATTACK_POWER = "projects:attack-power"
    const val INCREASED_DAMAGE = "projects:increased-damage"
    const val PHYSICAL_PENETRATION = "projects:physical-penetration"
    const val MAGICAL_PENETRATION = "projects:magical-penetration"
    const val LIFE_STEAL = "projects:life-steal"
    const val ATTACK_SPEED = "projects:attack-speed"
    const val ELEMENT_APPLICATION = "projects:element-application"
}

fun Element.attackTag(): AttackTag = when (this) {
    Element.FIRE -> AttackTag.FIRE
    Element.ICE -> AttackTag.ICE
    Element.LIGHTNING -> AttackTag.LIGHTNING
}

fun Set<AttackTag>.elements(): Set<Element> = buildSet {
    if (AttackTag.FIRE in this@elements) add(Element.FIRE)
    if (AttackTag.ICE in this@elements) add(Element.ICE)
    if (AttackTag.LIGHTNING in this@elements) add(Element.LIGHTNING)
}

data class ElementApplication(
    val element: Element,
    val power: Double,
    val sourceId: String,
) {
    init {
        require(power.isFinite() && power >= 0.0) { "element application power must be finite and non-negative" }
        require(sourceId.isNotBlank()) { "element application source is required" }
    }
}

data class ResolvedCombatStats(
    val baseAttackPower: Double = 0.0,
    val flatAttackPower: Double = 0.0,
    val attackPowerPercent: Double = 0.0,
    val damageIncreasePercent: Double = 0.0,
    val finalDamageMultiplier: Double = 1.0,
    val physicalPenetrationPercent: Double = 0.0,
    val magicalPenetrationPercent: Double = 0.0,
    val lifeStealPercent: Double = 0.0,
    val attackSpeedMultiplier: Double = 1.0,
) {
    val attackPower: Double
        get() = StatCalculator.attackPower(baseAttackPower, flatAttackPower, attackPowerPercent)

    init {
        require(listOf(
            baseAttackPower,
            flatAttackPower,
            attackPowerPercent,
            damageIncreasePercent,
            finalDamageMultiplier,
            physicalPenetrationPercent,
            magicalPenetrationPercent,
            lifeStealPercent,
            attackSpeedMultiplier,
        ).all(Double::isFinite)) { "resolved combat stats must be finite" }
    }
}

data class CombatBuildSnapshot(
    val equipmentId: String?,
    val attackTags: Set<AttackTag>,
    val stats: ResolvedCombatStats,
    val elementApplications: List<ElementApplication>,
    val appliedModIds: List<String>,
    val ignoredModIds: List<String>,
    val ignoredStatIds: List<String>,
) {
    fun elementPower(element: Element): Double =
        elementApplications.filter { it.element == element }.sumOf { it.power }

    fun elementSource(element: Element): String? =
        elementApplications.lastOrNull { it.element == element }?.sourceId

    companion object {
        fun empty(attackTags: Set<AttackTag> = emptySet(), fallbackAttackPower: Double = 0.0): CombatBuildSnapshot =
            CombatBuildSnapshot(
                equipmentId = null,
                attackTags = attackTags.toSet(),
                stats = ResolvedCombatStats(baseAttackPower = fallbackAttackPower),
                elementApplications = emptyList(),
                appliedModIds = emptyList(),
                ignoredModIds = emptyList(),
                ignoredStatIds = emptyList(),
            )
    }
}

object CombatBuildResolver {
    fun resolve(
        equipment: EquipmentItem?,
        definitions: Map<String, ModDefinition>,
        attackTags: Set<AttackTag>,
        fallbackAttackPower: Double = 0.0,
    ): CombatBuildSnapshot {
        var baseAttackPower = if (equipment == null) fallbackAttackPower else 0.0
        var flatAttackPower = 0.0
        var attackPowerPercent = 0.0
        var damageIncreasePercent = 0.0
        var finalDamageMultiplier = 1.0
        var physicalPenetrationPercent = 0.0
        var magicalPenetrationPercent = 0.0
        var lifeStealPercent = 0.0
        var attackSpeedMultiplier = 1.0
        val elementApplications = mutableListOf<ElementApplication>()
        val appliedModIds = mutableListOf<String>()
        val ignoredModIds = mutableListOf<String>()
        val ignoredStatIds = mutableListOf<String>()

        fun addStat(statId: String, value: Double, layer: ModStackingLayer): Boolean {
            return when (statId) {
                CombatStatIds.PHYSICAL_ATTACK,
                CombatStatIds.FLAT_ATTACK,
                CombatStatIds.ATTACK_POWER,
                -> when (layer) {
                    ModStackingLayer.BASE_FLAT -> {
                        flatAttackPower = StatCalculator.saturatedAdd(flatAttackPower, value)
                        true
                    }
                    ModStackingLayer.BASE_PERCENT -> {
                        attackPowerPercent = StatCalculator.saturatedAdd(attackPowerPercent, value)
                        true
                    }
                    ModStackingLayer.INCREASED,
                    ModStackingLayer.CONDITIONAL,
                    -> {
                        damageIncreasePercent = StatCalculator.saturatedAdd(damageIncreasePercent, value)
                        true
                    }
                    ModStackingLayer.FINAL -> {
                        finalDamageMultiplier = StatCalculator.saturatedAdd(finalDamageMultiplier, value)
                        true
                    }
                }
                CombatStatIds.INCREASED_DAMAGE -> when (layer) {
                    ModStackingLayer.FINAL -> {
                        finalDamageMultiplier = StatCalculator.saturatedAdd(finalDamageMultiplier, value)
                        true
                    }
                    else -> {
                        damageIncreasePercent = StatCalculator.saturatedAdd(damageIncreasePercent, value)
                        true
                    }
                }
                CombatStatIds.PHYSICAL_PENETRATION -> {
                    physicalPenetrationPercent = StatCalculator.saturatedAdd(physicalPenetrationPercent, value)
                    true
                }
                CombatStatIds.MAGICAL_PENETRATION -> {
                    magicalPenetrationPercent = StatCalculator.saturatedAdd(magicalPenetrationPercent, value)
                    true
                }
                CombatStatIds.LIFE_STEAL -> {
                    lifeStealPercent = StatCalculator.saturatedAdd(lifeStealPercent, value)
                    true
                }
                CombatStatIds.ATTACK_SPEED -> {
                    attackSpeedMultiplier = StatCalculator.saturatedAdd(attackSpeedMultiplier, value)
                    true
                }
                CombatStatIds.ELEMENT_APPLICATION -> {
                    ignoredStatIds += statId
                    false
                }
                else -> {
                    ignoredStatIds += statId
                    false
                }
            }
        }

        equipment?.baseStatRolls?.forEach { roll ->
            when (roll.statId) {
                CombatStatIds.PHYSICAL_ATTACK,
                CombatStatIds.FLAT_ATTACK,
                CombatStatIds.ATTACK_POWER,
                -> baseAttackPower = StatCalculator.saturatedAdd(baseAttackPower, roll.value)
                CombatStatIds.INCREASED_DAMAGE -> damageIncreasePercent =
                    StatCalculator.saturatedAdd(damageIncreasePercent, roll.value)
                CombatStatIds.PHYSICAL_PENETRATION -> physicalPenetrationPercent =
                    StatCalculator.saturatedAdd(physicalPenetrationPercent, roll.value)
                CombatStatIds.MAGICAL_PENETRATION -> magicalPenetrationPercent =
                    StatCalculator.saturatedAdd(magicalPenetrationPercent, roll.value)
                CombatStatIds.LIFE_STEAL -> lifeStealPercent = StatCalculator.saturatedAdd(lifeStealPercent, roll.value)
                CombatStatIds.ATTACK_SPEED -> attackSpeedMultiplier =
                    StatCalculator.saturatedAdd(attackSpeedMultiplier, roll.value)
                else -> ignoredStatIds += roll.statId
            }
        }

        equipment?.modSlots?.sortedBy { it.index }?.forEach { slot ->
            val entry = slot.entry
            if (entry !is ModEntry) {
                if (entry != null) ignoredModIds += "slot:${slot.index}"
                return@forEach
            }
            val definition = definitions[entry.modId]
            if (definition == null || !definition.acceptsAttackTags(attackTags)) {
                ignoredModIds += entry.modId
                return@forEach
            }
            val validation = ModValidation.validate(entry, definition, equipment.slot)
            if (!validation.valid) {
                ignoredModIds += entry.modId
                return@forEach
            }
            when (val effect = validation.effect?.effect) {
                is ModEffect.ElementApplication -> {
                    elementApplications += ElementApplication(
                        element = effect.element,
                        power = validation.effect.value,
                        sourceId = validation.effect.sourceId,
                    )
                    appliedModIds += entry.modId
                }
                null -> {
                    val contribution = validation.contribution
                    if (contribution == null) {
                        ignoredModIds += entry.modId
                    } else if (addStat(contribution.statId, contribution.value, definition.stackingLayer)) {
                        appliedModIds += entry.modId
                    } else {
                        ignoredModIds += entry.modId
                    }
                }
            }
        }

        return CombatBuildSnapshot(
            equipmentId = equipment?.itemId,
            attackTags = attackTags.toSet(),
            stats = ResolvedCombatStats(
                baseAttackPower = baseAttackPower,
                flatAttackPower = flatAttackPower,
                attackPowerPercent = attackPowerPercent,
                damageIncreasePercent = damageIncreasePercent,
                finalDamageMultiplier = finalDamageMultiplier,
                physicalPenetrationPercent = physicalPenetrationPercent,
                magicalPenetrationPercent = magicalPenetrationPercent,
                lifeStealPercent = lifeStealPercent,
                attackSpeedMultiplier = attackSpeedMultiplier,
            ),
            elementApplications = elementApplications.toList(),
            appliedModIds = appliedModIds.toList(),
            ignoredModIds = ignoredModIds.toList(),
            ignoredStatIds = ignoredStatIds.distinct(),
        )
    }
}

data class NormalAttackDamageResolution(
    val direct: DamageResult,
    val preCritical: DamageResult,
)

object NormalAttackDamageResolver {
    fun resolve(
        build: CombatBuildSnapshot,
        damageType: DamageType = DamageType.PHYSICAL,
        defense: Double = 0.0,
        mode: DamageMode = DamageMode.PVE,
        modeMultiplier: Double = 1.0,
        critical: Boolean = false,
        criticalMultiplier: Double = mode.baseCriticalMultiplier,
    ): NormalAttackDamageResolution {
        fun calculate(isCritical: Boolean): DamageResult = DamageCalculator.calculate(
            DamageCalculator.Input(
                damageType = damageType,
                mode = mode,
                damageKind = dev.projects.server.combat.damage.DamageKind.NORMAL_ATTACK,
                attackPower = build.stats.attackPower,
                coefficient = 1.0,
                damageIncreasePercent = build.stats.damageIncreasePercent,
                critical = isCritical,
                criticalMultiplier = criticalMultiplier,
                defense = defense,
                penetrationPercent = when (damageType) {
                    DamageType.PHYSICAL -> build.stats.physicalPenetrationPercent
                    DamageType.MAGICAL -> build.stats.magicalPenetrationPercent
                    DamageType.TRUE -> 0.0
                },
                lifeStealPercent = build.stats.lifeStealPercent,
                lifeStealEfficiency = 1.0,
                modeMultiplier = StatCalculator.saturatedMultiply(modeMultiplier, build.stats.finalDamageMultiplier),
            ),
        )

        return NormalAttackDamageResolution(
            direct = calculate(critical),
            preCritical = calculate(false),
        )
    }
}
