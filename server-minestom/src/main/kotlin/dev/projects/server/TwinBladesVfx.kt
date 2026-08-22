package dev.projects.server

import kotlin.math.max

internal data class TwinBladesHitVfxPlan(val presets: List<String>)

internal fun nextTwinBladesSwingAngle(previous: Double?): Double = -(previous ?: -35.0)

internal fun twinBladesHitVfxPlan(weapon: WeaponType, confirmed: Boolean, weakpoint: Boolean): TwinBladesHitVfxPlan {
    if (weapon != WeaponType.TWIN_RODS || !confirmed) return TwinBladesHitVfxPlan(emptyList())
    return TwinBladesHitVfxPlan(
        listOf("projects:class/twin_blades/aa_hit") +
            if (weakpoint) listOf("projects:class/twin_blades/weakpoint_hit") else emptyList(),
    )
}

internal fun twinBladesVisualScale(width: Double, height: Double): Double =
    max(width, height * 0.55).coerceIn(1.0, 1.5)
