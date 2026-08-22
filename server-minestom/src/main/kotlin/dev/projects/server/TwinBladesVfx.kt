package dev.projects.server

import dev.projects.server.particle.ParticleTransform
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import kotlin.math.abs
import kotlin.math.max

internal data class TwinBladesHitVfxPlan(val presets: List<String>)

internal data class TwinBladesHitVisualDimensions(val length: Double, val radius: Double)

internal data class TwinBladesSoundCue(val key: String, val volume: Float, val pitch: Float)

internal data class TwinBladesSoundPlan(
    val swing: List<TwinBladesSoundCue>,
    val contact: List<TwinBladesSoundCue>,
    val weakpointAccent: List<TwinBladesSoundCue>,
)

internal fun twinBladesSkill2PulseSoundPlan(pulseIndex: Int): List<TwinBladesSoundCue> = when (pulseIndex.coerceIn(1, 4)) {
    1 -> listOf(TwinBladesSoundCue("item.trident.throw", 0.18f, 0.92f))
    2 -> listOf(TwinBladesSoundCue("item.trident.throw", 0.20f, 1.06f))
    3 -> listOf(
        TwinBladesSoundCue("item.trident.throw", 0.24f, 0.82f),
        TwinBladesSoundCue("item.axe.scrape", 0.13f, 1.12f),
    )
    else -> listOf(
        TwinBladesSoundCue("item.trident.throw", 0.28f, 0.70f),
        TwinBladesSoundCue("item.axe.scrape", 0.18f, 0.96f),
        TwinBladesSoundCue("item.trident.riptide_1", 0.12f, 1.34f),
    )
}

internal fun twinBladesSkill2LandingSoundPlan(): List<TwinBladesSoundCue> = listOf(
    TwinBladesSoundCue("item.trident.throw", 0.42f, 0.52f),
    TwinBladesSoundCue("item.trident.throw", 0.30f, 1.18f),
    TwinBladesSoundCue("item.trident.hit", 0.52f, 0.74f),
    TwinBladesSoundCue("item.trident.riptide_1", 0.24f, 0.96f),
    TwinBladesSoundCue("item.axe.scrape", 0.16f, 0.72f),
)

internal data class TwinBladesSkill3Visual(
    val primaryLength: Double = 5.8,
    val primaryDuration: Int = 4,
    val aftercutLength: Double = 3.1,
    val aftercutDuration: Int = 2,
    val recoilDuration: Int = 4,
)

internal data class TwinBladesSkill3SoundPlan(
    val travel: List<TwinBladesSoundCue>,
    val confirmedHit: List<TwinBladesSoundCue>,
    val bounce: List<TwinBladesSoundCue>,
)

internal const val TWIN_BLADES_COMBO_RESET_TICKS = 12
internal const val TWIN_BLADES_SWING_FORWARD_OFFSET = 1.25
internal const val TWIN_BLADES_SKILL3_MIN_CONTACT_DISTANCE = 0.6

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

internal fun twinBladesSwingOrigin(position: Point, eyeHeight: Double, direction: Vec, angleDegrees: Double): Point {
    val eye = position.add(0.0, eyeHeight, 0.0)
    val transform = ParticleTransform.fromDirection(eye, direction)
    val handSide = if (angleDegrees >= 0.0) 1.0 else -1.0
    return transform.localPoint(Vec(handSide * 0.22, -0.38, TWIN_BLADES_SWING_FORWARD_OFFSET))
}

internal fun twinBladesComboVisual(step: Int): TwinBladesComboVisual = when (step.coerceIn(1, 3)) {
    1 -> TwinBladesComboVisual(
        step = 1,
        swingLength = 3.2,
        swingDuration = 3,
        swingPrimary = 0x1677ff,
        swingSecondary = 0x071525,
        hitLength = 3.0,
        hitDuration = 3,
        hitPrimary = 0x8fffff,
        hitSecondary = 0x168cff,
        weakpointDuration = 4,
        weakpointPrimary = 0xc8ffff,
        weakpointSecondary = 0x25d9e8,
    )
    2 -> TwinBladesComboVisual(
        step = 2,
        swingLength = 3.7,
        swingDuration = 3,
        swingPrimary = 0x1259d8,
        swingSecondary = 0x0a1c34,
        hitLength = 3.4,
        hitDuration = 4,
        hitPrimary = 0x70e9ff,
        hitSecondary = 0x126bff,
        weakpointDuration = 5,
        weakpointPrimary = 0xe0ffff,
        weakpointSecondary = 0x168cff,
    )
    else -> TwinBladesComboVisual(
        step = 3,
        swingLength = 4.3,
        swingDuration = 3,
        swingPrimary = 0x46dfff,
        swingSecondary = 0x050a14,
        hitLength = 4.0,
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

internal fun twinBladesSkill3ContactPoint(
    dashOrigin: Point,
    dashDirection: Vec,
    targetCenter: Point,
    targetHalfExtent: Vec,
): Point {
    require(
        listOf(
            dashOrigin.x(), dashOrigin.y(), dashOrigin.z(),
            targetCenter.x(), targetCenter.y(), targetCenter.z(),
            targetHalfExtent.x(), targetHalfExtent.y(), targetHalfExtent.z(),
        ).all { it.isFinite() } &&
            targetHalfExtent.x() >= 0.0 && targetHalfExtent.y() >= 0.0 && targetHalfExtent.z() >= 0.0,
    ) { "Skill3 contact geometry must be finite and non-negative" }
    val directionLength = dashDirection.length()
    val forward = if (directionLength.isFinite() && directionLength > 1.0e-9) {
        dashDirection.mul(1.0 / directionLength)
    } else {
        Vec(0.0, 0.0, 1.0)
    }
    val delta = Vec(
        targetCenter.x() - dashOrigin.x(),
        targetCenter.y() - dashOrigin.y(),
        targetCenter.z() - dashOrigin.z(),
    )
    val projectedDistance = delta.dot(forward)
    val targetSurfaceExtent = abs(forward.x()) * targetHalfExtent.x() +
        abs(forward.y()) * targetHalfExtent.y() + abs(forward.z()) * targetHalfExtent.z()
    val surfaceDistance = (projectedDistance - targetSurfaceExtent).coerceAtLeast(TWIN_BLADES_SKILL3_MIN_CONTACT_DISTANCE)
    return dashOrigin.add(
        forward.x() * surfaceDistance,
        forward.y() * surfaceDistance,
        forward.z() * surfaceDistance,
    )
}

internal fun twinBladesSkill3SoundPlan(): TwinBladesSkill3SoundPlan = TwinBladesSkill3SoundPlan(
    travel = listOf(TwinBladesSoundCue("item.trident.throw", 0.28f, 0.78f)),
    confirmedHit = listOf(
        TwinBladesSoundCue("item.trident.throw", 0.52f, 0.62f),
        TwinBladesSoundCue("item.trident.throw", 0.34f, 1.28f),
        TwinBladesSoundCue("item.axe.scrape", 0.34f, 0.92f),
        TwinBladesSoundCue("entity.player.attack.strong", 0.65f, 0.88f),
        TwinBladesSoundCue("item.trident.hit", 0.42f, 1.02f),
    ),
    bounce = listOf(TwinBladesSoundCue("item.trident.riptide_1", 0.22f, 1.22f)),
)

internal fun twinBladesSoundPlan(
    weapon: WeaponType,
    step: Int,
    confirmed: Boolean,
    weakpoint: Boolean,
): TwinBladesSoundPlan {
    if (weapon != WeaponType.TWIN_RODS) return TwinBladesSoundPlan(emptyList(), emptyList(), emptyList())

    val swing = when (step.coerceIn(1, 3)) {
        1 -> listOf(
            TwinBladesSoundCue("item.trident.throw", 0.36f, 0.92f),
            TwinBladesSoundCue("item.trident.throw", 0.18f, 1.75f),
            TwinBladesSoundCue("item.axe.scrape", 0.10f, 1.40f),
        )
        2 -> listOf(
            TwinBladesSoundCue("item.trident.throw", 0.45f, 0.72f),
            TwinBladesSoundCue("item.trident.throw", 0.21f, 1.55f),
            TwinBladesSoundCue("item.axe.scrape", 0.15f, 1.22f),
            TwinBladesSoundCue("entity.player.attack.sweep", 0.14f, 0.95f),
        )
        else -> listOf(
            TwinBladesSoundCue("item.trident.throw", 0.55f, 0.58f),
            TwinBladesSoundCue("item.trident.throw", 0.26f, 1.35f),
            TwinBladesSoundCue("item.trident.riptide_1", 0.20f, 1.68f),
            TwinBladesSoundCue("item.axe.scrape", 0.20f, 1.00f),
        )
    }
    if (!confirmed) return TwinBladesSoundPlan(swing, emptyList(), emptyList())

    return TwinBladesSoundPlan(
        swing = swing,
        contact = listOf(
            TwinBladesSoundCue("item.trident.hit", 0.32f, 1.0f),
            TwinBladesSoundCue("item.axe.scrape", 0.18f, 0.82f),
        ),
        weakpointAccent = if (weakpoint) {
            listOf(TwinBladesSoundCue("block.note_block.chime", 0.24f, 1.90f))
        } else {
            emptyList()
        },
    )
}
