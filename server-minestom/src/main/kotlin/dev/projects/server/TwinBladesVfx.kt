package dev.projects.server

import kotlin.math.max

internal data class TwinBladesHitVfxPlan(val presets: List<String>)

internal data class TwinBladesHitVisualDimensions(val length: Double, val radius: Double)

internal const val TWIN_BLADES_COMBO_RESET_TICKS = 12

internal data class TwinBladesComboVisual(
    val step: Int,
    val swingLength: Double,
    val swingDuration: Int,
    val swingPrimary: Int,
    val swingSecondary: Int,
    val hitLength: Double,
    val hitDuration: Int,
    val hitPrimary: Int,
    val hitSecondary: Int,
    val weakpointDuration: Int,
    val weakpointPrimary: Int,
    val weakpointSecondary: Int,
)

internal class TwinBladesComboState {
    private var nextStep = 1
    private var idleTicks = TWIN_BLADES_COMBO_RESET_TICKS

    fun tick() {
        idleTicks = (idleTicks + 1).coerceAtMost(TWIN_BLADES_COMBO_RESET_TICKS)
    }

    fun start(): Int {
        if (idleTicks >= TWIN_BLADES_COMBO_RESET_TICKS) nextStep = 1
        val startedStep = nextStep
        nextStep = if (startedStep == 3) 1 else startedStep + 1
        idleTicks = 0
        return startedStep
    }

    fun reset() {
        nextStep = 1
        idleTicks = TWIN_BLADES_COMBO_RESET_TICKS
    }
}

internal fun nextTwinBladesSwingAngle(previous: Double?): Double = -(previous ?: -35.0)

internal fun twinBladesComboVisual(step: Int): TwinBladesComboVisual = when (step.coerceIn(1, 3)) {
    1 -> TwinBladesComboVisual(
        step = 1,
        swingLength = 1.9,
        swingDuration = 2,
        swingPrimary = 0xa8ffff,
        swingSecondary = 0x168cff,
        hitLength = 2.7,
        hitDuration = 3,
        hitPrimary = 0x8fffff,
        hitSecondary = 0x168cff,
        weakpointDuration = 4,
        weakpointPrimary = 0xc8ffff,
        weakpointSecondary = 0x25d9e8,
    )
    2 -> TwinBladesComboVisual(
        step = 2,
        swingLength = 2.1,
        swingDuration = 3,
        swingPrimary = 0x70e9ff,
        swingSecondary = 0x126bff,
        hitLength = 3.0,
        hitDuration = 4,
        hitPrimary = 0x70e9ff,
        hitSecondary = 0x126bff,
        weakpointDuration = 5,
        weakpointPrimary = 0xe0ffff,
        weakpointSecondary = 0x168cff,
    )
    else -> TwinBladesComboVisual(
        step = 3,
        swingLength = 2.3,
        swingDuration = 3,
        swingPrimary = 0xe8fdff,
        swingSecondary = 0x168cff,
        hitLength = 3.2,
        hitDuration = 4,
        hitPrimary = 0xe8fdff,
        hitSecondary = 0x126bff,
        weakpointDuration = 6,
        weakpointPrimary = 0xffffff,
        weakpointSecondary = 0x70e9ff,
    )
}

internal fun twinBladesHitVfxPlan(weapon: WeaponType, confirmed: Boolean, weakpoint: Boolean): TwinBladesHitVfxPlan {
    if (weapon != WeaponType.TWIN_RODS || !confirmed) return TwinBladesHitVfxPlan(emptyList())
    return TwinBladesHitVfxPlan(
        listOf("projects:class/twin_blades/aa_hit") +
            if (weakpoint) listOf("projects:class/twin_blades/weakpoint_hit") else emptyList(),
    )
}

internal fun twinBladesVisualScale(width: Double, height: Double): Double =
    max(width, height * 0.55).coerceIn(1.0, 1.5)

internal fun twinBladesWeakpointRadius(visualScale: Double): Double =
    (1.35 * visualScale).coerceIn(0.0, 2.0)

internal fun twinBladesHitVisualDimensions(visual: TwinBladesComboVisual): TwinBladesHitVisualDimensions =
    TwinBladesHitVisualDimensions(length = visual.hitLength, radius = 1.1)
