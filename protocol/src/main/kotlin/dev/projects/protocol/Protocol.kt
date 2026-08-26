package dev.projects.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

const val PROJECTS_CHANNEL = "projects:protocol"

object ProtocolVersion {
    const val CURRENT = 15

    fun requireCompatible(version: Int) {
        if (version != CURRENT) {
            throw ProtocolVersionMismatchException(CURRENT, version)
        }
    }
}

class ProtocolVersionMismatchException(expected: Int, actual: Int) :
    IllegalArgumentException("ProjectS protocol version mismatch: expected $expected, received $actual")

sealed interface ProtocolMessage

data class ProtocolHello(val version: Int) : ProtocolMessage

data class ProtocolHelloAck(val version: Int) : ProtocolMessage

enum class AttackInputState {
    PRESS,
    RELEASE,
}

data class AttackInput(val state: AttackInputState, val sequence: Long) : ProtocolMessage

data class AttackStarted(val attackExecutionId: Long) : ProtocolMessage

data class AttackHitConfirmed(val attackExecutionId: Long, val targetId: UUID) : ProtocolMessage

enum class AttackDebugShapeKind {
    TWIN_RODS,
    HEAVY_BLADE,
}

data class AttackDebugShape(
    val kind: AttackDebugShapeKind,
    val originX: Double,
    val originY: Double,
    val originZ: Double,
    val directionX: Double,
    val directionY: Double,
    val directionZ: Double,
    val range: Double,
    val minForwardDot: Double,
    val verticalRange: Double,
) : ProtocolMessage {
    init {
        require(
            originX.isFinite() && originY.isFinite() && originZ.isFinite() &&
                directionX.isFinite() && directionY.isFinite() && directionZ.isFinite(),
        ) { "Attack debug shape coordinates must be finite" }
        require(range > 0.0 && range.isFinite()) { "Attack debug shape range must be positive" }
        require(minForwardDot in -1.0..1.0 && minForwardDot.isFinite()) {
            "Attack debug shape forward dot must be between -1 and 1"
        }
        require(verticalRange >= 0.0 && verticalRange.isFinite()) {
            "Attack debug shape vertical range must be non-negative"
        }
        require(sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ) > 0.0) {
            "Attack debug shape direction must not be zero"
        }
    }
}

data class DodgeInput(val directionX: Double, val directionZ: Double) : ProtocolMessage {
    init {
        require(directionX.isFinite() && directionZ.isFinite()) { "Dodge direction must be finite" }
        require(abs(directionX) <= 1.0 && abs(directionZ) <= 1.0) { "Dodge direction is out of range" }
    }
}

data class AirJumpInput(val directionX: Double, val directionZ: Double) : ProtocolMessage {
    init {
        require(directionX.isFinite() && directionZ.isFinite()) { "Air Jump direction must be finite" }
        require(abs(directionX) <= 1.0 && abs(directionZ) <= 1.0) { "Air Jump direction is out of range" }
    }
}

enum class ClassSkillSlot {
    SKILL_1,
    SKILL_2,
    SKILL_3,
    ULTIMATE,
}

data class ClassSkillInput(
    val slot: ClassSkillSlot,
    val directionX: Double,
    val directionZ: Double,
) : ProtocolMessage {
    init {
        require(directionX.isFinite() && directionZ.isFinite()) { "Skill direction must be finite" }
        require(abs(directionX) <= 1.0 && abs(directionZ) <= 1.0) { "Skill direction is out of range" }
    }
}

data class ClassResourceSnapshot(
    val mana: Int,
    val maxMana: Int,
    val skill1CooldownTicks: Int,
    val skill1CooldownMaxTicks: Int,
    val skill2CooldownTicks: Int,
    val skill2CooldownMaxTicks: Int,
    val skill3CooldownTicks: Int,
    val skill3CooldownMaxTicks: Int,
) : ProtocolMessage {
    init {
        require(
            maxMana > 0 && skill1CooldownMaxTicks > 0 && skill2CooldownMaxTicks > 0 &&
                skill3CooldownMaxTicks > 0,
        ) { "Resource maximums must be positive" }
        require(mana in 0..maxMana) { "Mana is out of range" }
        require(skill1CooldownTicks in 0..skill1CooldownMaxTicks) { "Skill1 cooldown is out of range" }
        require(skill2CooldownTicks in 0..skill2CooldownMaxTicks) { "Skill2 cooldown is out of range" }
        require(skill3CooldownTicks in 0..skill3CooldownMaxTicks) { "Skill3 cooldown is out of range" }
    }
}

private object StarweaverHudLimits {
    const val MAX_QUEUE_SIZE = 5
    const val MAX_RELOAD_TICKS = 1200
}

enum class StarweaverHudCelestial {
    SUN,
    MOON,
    STAR,
}

data class StarweaverHudSnapshot(
    val selected: Boolean,
    val queue: List<StarweaverHudCelestial>,
    val stored: StarweaverHudCelestial,
    val conjunctionAvailable: Boolean,
    val conjunctionUsed: Boolean,
    val reloadTicksRemaining: Int,
) : ProtocolMessage {
    init {
        require(queue.size <= StarweaverHudLimits.MAX_QUEUE_SIZE) {
            "Starweaver HUD queue is too large"
        }
        require(reloadTicksRemaining in 0..StarweaverHudLimits.MAX_RELOAD_TICKS) {
            "Starweaver HUD reload ticks are out of range"
        }
        require(!(conjunctionAvailable && conjunctionUsed)) {
            "Starweaver conjunction cannot be available after being used"
        }
        require(!conjunctionAvailable || (queue.size >= 2 && queue[0] == queue[1])) {
            "Starweaver conjunction must match the first two queue marks"
        }
    }
}

private object RoninHudLimits {
    const val MAX_Q_COOLDOWN_TICKS = 160
    const val MAX_E_COOLDOWN_TICKS = 300
    const val MAX_R_COOLDOWN_TICKS = 600
    const val MAX_LOCK_TICKS = 40
}

data class RoninHudSnapshot(
    val selected: Boolean,
    val iaido: Int,
    val qCooldownTicks: Int,
    val eCooldownTicks: Int,
    val rCooldownTicks: Int,
    val movementLockTicksRemaining: Int,
    /** 0 = unavailable, 1 = Wound, 2 = Crosscut, 3 = Tempest. */
    val wVariant: Int,
) : ProtocolMessage {
    init {
        require(iaido in 0..3) { "Ronin Iaido is out of range" }
        require(qCooldownTicks in 0..RoninHudLimits.MAX_Q_COOLDOWN_TICKS) {
            "Ronin Q cooldown is out of range"
        }
        require(eCooldownTicks in 0..RoninHudLimits.MAX_E_COOLDOWN_TICKS) {
            "Ronin E cooldown is out of range"
        }
        require(rCooldownTicks in 0..RoninHudLimits.MAX_R_COOLDOWN_TICKS) {
            "Ronin R cooldown is out of range"
        }
        require(movementLockTicksRemaining in 0..RoninHudLimits.MAX_LOCK_TICKS) {
            "Ronin movement lock is out of range"
        }
        require(wVariant in 0..3) { "Ronin W variant is out of range" }
    }
}

private object GroundTelegraphLimits {
    const val MAX_RADIUS = 64.0
    const val MIN_ANGLE_DEGREES = 1.0
    const val MAX_ANGLE_DEGREES = 360.0
    const val MAX_DURATION_TICKS = 1200
}

data class GroundTelegraphStart(
    val telegraphId: Long,
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val facingX: Double,
    val facingZ: Double,
    val radius: Double,
    val angleDegrees: Double,
    val durationTicks: Int,
) : ProtocolMessage {
    init {
        require(telegraphId >= 0L) { "Ground telegraph id must not be negative" }
        require(
            centerX.isFinite() && centerY.isFinite() && centerZ.isFinite() &&
                facingX.isFinite() && facingZ.isFinite(),
        ) { "Ground telegraph coordinates and facing must be finite" }
        require(radius.isFinite() && radius in 0.0..GroundTelegraphLimits.MAX_RADIUS) {
            "Ground telegraph radius is out of range"
        }
        require(angleDegrees.isFinite() && angleDegrees in GroundTelegraphLimits.MIN_ANGLE_DEGREES..GroundTelegraphLimits.MAX_ANGLE_DEGREES) {
            "Ground telegraph angle is out of range"
        }
        require(durationTicks in 1..GroundTelegraphLimits.MAX_DURATION_TICKS) {
            "Ground telegraph duration is out of range"
        }
    }

    companion object {
        fun clamped(
            telegraphId: Long,
            centerX: Double,
            centerY: Double,
            centerZ: Double,
            facingX: Double,
            facingZ: Double,
            radius: Double,
            angleDegrees: Double,
            durationTicks: Int,
        ): GroundTelegraphStart {
            require(
                centerX.isFinite() && centerY.isFinite() && centerZ.isFinite() &&
                    facingX.isFinite() && facingZ.isFinite() && radius.isFinite() && angleDegrees.isFinite(),
            ) { "Ground telegraph values must be finite" }
            return GroundTelegraphStart(
                telegraphId = telegraphId,
                centerX = centerX,
                centerY = centerY,
                centerZ = centerZ,
                facingX = facingX,
                facingZ = facingZ,
                radius = radius.coerceIn(0.0, GroundTelegraphLimits.MAX_RADIUS),
                angleDegrees = angleDegrees.coerceIn(
                    GroundTelegraphLimits.MIN_ANGLE_DEGREES,
                    GroundTelegraphLimits.MAX_ANGLE_DEGREES,
                ),
                durationTicks = durationTicks.coerceIn(1, GroundTelegraphLimits.MAX_DURATION_TICKS),
            )
        }
    }
}

data class GroundTelegraphRemove(val telegraphId: Long) : ProtocolMessage {
    init {
        require(telegraphId >= 0L) { "Ground telegraph id must not be negative" }
    }
}

private object SlashEditorLimits {
    const val MAX_NAME_LENGTH = 32
    const val MAX_DRAFTS = 16
    const val MIN_ORIGIN_Y = -4.0
    const val MAX_ORIGIN_Y = 8.0
    const val MIN_FORWARD_OFFSET = 0.0
    const val MAX_FORWARD_OFFSET = 8.0
    const val MIN_LENGTH = 0.5
    const val MAX_LENGTH = 12.0
    const val MIN_ARC_SPAN = 10.0
    const val MAX_ARC_SPAN = 350.0
    const val MIN_CURVATURE = 0.0
    const val MAX_CURVATURE = 4.0
    const val MIN_TILT = -90.0
    const val MAX_TILT = 90.0
    const val MIN_YAW = -180.0
    const val MAX_YAW = 180.0
    const val MIN_WIDTH = 0.0
    const val MAX_WIDTH = 1.5
    const val MIN_LANE_COUNT = 1
    const val MAX_LANE_COUNT = 3
    const val MIN_LANE_SPACING = 0.05
    const val MAX_LANE_SPACING = 2.0
    const val MIN_PARTICLE_SIZE = 0.05
    const val MAX_PARTICLE_SIZE = 2.0
    const val MIN_SPACING = 0.05
    const val MAX_SPACING = 2.0
    const val MIN_DURATION_TICKS = 1
    const val MAX_DURATION_TICKS = 80
    const val MAX_TARGET_DISTANCE = 32.0
}

data class SlashEditorParameters(
    val originY: Double = 1.2,
    val forwardOffset: Double = 1.7,
    val length: Double = 5.0,
    val arcSpan: Double = 160.0,
    val curvature: Double = 0.35,
    val tilt: Double = 12.0,
    val yaw: Double = 0.0,
    val width: Double = 0.28,
    val laneCount: Int = 1,
    val laneSpacing: Double = 0.18,
    val particleSize: Double = 0.28,
    val spacing: Double = 0.18,
    val durationTicks: Int = 8,
    val color: Int = 0x123bdb,
    val targetDistance: Double = 5.0,
) {
    init {
        require(listOf(originY, forwardOffset, length, arcSpan, curvature, tilt, yaw, width, laneSpacing, particleSize, spacing, targetDistance).all { it.isFinite() }) {
            "Slash editor values must be finite"
        }
        require(laneCount in SlashEditorLimits.MIN_LANE_COUNT..SlashEditorLimits.MAX_LANE_COUNT) { "Slash editor lane count is out of range" }
        require(durationTicks in SlashEditorLimits.MIN_DURATION_TICKS..SlashEditorLimits.MAX_DURATION_TICKS) {
            "Slash editor duration is out of range"
        }
        require(color in 0..0xffffff) { "Slash editor color is out of range" }
    }

    companion object {
        fun clamped(
            originY: Double,
            forwardOffset: Double,
            length: Double,
            arcSpan: Double,
            curvature: Double,
            tilt: Double,
            yaw: Double,
            width: Double,
            laneCount: Int,
            laneSpacing: Double,
            particleSize: Double,
            spacing: Double,
            durationTicks: Int,
            color: Int,
            targetDistance: Double,
        ): SlashEditorParameters {
            require(listOf(originY, forwardOffset, length, arcSpan, curvature, tilt, yaw, width, laneSpacing, particleSize, spacing, targetDistance).all { it.isFinite() }) {
                "Slash editor values must be finite"
            }
            return SlashEditorParameters(
                originY.coerceIn(SlashEditorLimits.MIN_ORIGIN_Y, SlashEditorLimits.MAX_ORIGIN_Y),
                forwardOffset.coerceIn(SlashEditorLimits.MIN_FORWARD_OFFSET, SlashEditorLimits.MAX_FORWARD_OFFSET),
                length.coerceIn(SlashEditorLimits.MIN_LENGTH, SlashEditorLimits.MAX_LENGTH),
                arcSpan.coerceIn(SlashEditorLimits.MIN_ARC_SPAN, SlashEditorLimits.MAX_ARC_SPAN),
                curvature.coerceIn(SlashEditorLimits.MIN_CURVATURE, SlashEditorLimits.MAX_CURVATURE),
                tilt.coerceIn(SlashEditorLimits.MIN_TILT, SlashEditorLimits.MAX_TILT),
                yaw.coerceIn(SlashEditorLimits.MIN_YAW, SlashEditorLimits.MAX_YAW),
                width.coerceIn(SlashEditorLimits.MIN_WIDTH, SlashEditorLimits.MAX_WIDTH),
                laneCount.coerceIn(SlashEditorLimits.MIN_LANE_COUNT, SlashEditorLimits.MAX_LANE_COUNT),
                laneSpacing.coerceIn(SlashEditorLimits.MIN_LANE_SPACING, SlashEditorLimits.MAX_LANE_SPACING),
                particleSize.coerceIn(SlashEditorLimits.MIN_PARTICLE_SIZE, SlashEditorLimits.MAX_PARTICLE_SIZE),
                spacing.coerceIn(SlashEditorLimits.MIN_SPACING, SlashEditorLimits.MAX_SPACING),
                durationTicks.coerceIn(SlashEditorLimits.MIN_DURATION_TICKS, SlashEditorLimits.MAX_DURATION_TICKS),
                color.coerceIn(0, 0xffffff),
                targetDistance.coerceIn(SlashEditorLimits.MIN_FORWARD_OFFSET, SlashEditorLimits.MAX_TARGET_DISTANCE),
            )
        }
    }
}

data class VfxEditorOpen(val parameters: SlashEditorParameters = SlashEditorParameters()) : ProtocolMessage

data class VfxSlashPreviewRequest(
    val requestId: Long,
    val parameters: SlashEditorParameters,
) : ProtocolMessage

data class VfxSlashApplySkill3(val parameters: SlashEditorParameters) : ProtocolMessage

object VfxSlashPreviewCancel : ProtocolMessage

data class VfxSlashSaveRequest(
    val name: String,
    val parameters: SlashEditorParameters,
) : ProtocolMessage {
    init {
        require(name.isNotBlank() && name.length <= SlashEditorLimits.MAX_NAME_LENGTH) {
            "Slash draft name is invalid"
        }
    }
}

data class VfxSlashDraftList(val names: List<String>) : ProtocolMessage {
    init {
        require(names.size <= SlashEditorLimits.MAX_DRAFTS) { "Too many slash drafts" }
        require(names.all { it.isNotBlank() && it.length <= SlashEditorLimits.MAX_NAME_LENGTH }) {
            "Slash draft name is invalid"
        }
    }
}

data class VfxSlashDraftLoadRequest(val name: String) : ProtocolMessage {
    init {
        require(name.isNotBlank() && name.length <= SlashEditorLimits.MAX_NAME_LENGTH) {
            "Slash draft name is invalid"
        }
    }
}

data class VfxSlashDraft(val name: String, val parameters: SlashEditorParameters) : ProtocolMessage {
    init {
        require(name.isNotBlank() && name.length <= SlashEditorLimits.MAX_NAME_LENGTH) {
            "Slash draft name is invalid"
        }
    }
}

data class VfxEditorNotice(val text: String) : ProtocolMessage {
    init {
        require(text.length <= 160) { "Editor notice is too long" }
    }
}

private object VfxEditor2ProtocolLimits {
    const val MAX_TARGET_LENGTH = 32
    const val MAX_STATUS_LENGTH = 160
    const val MAX_EFFECTS = 8
    const val MAX_EFFECT_NAME_LENGTH = 32
    const val MIN_FORWARD = -1.0
    const val MAX_FORWARD = 8.0
    const val MIN_SIDE = -5.0
    const val MAX_SIDE = 5.0
    const val MIN_HEIGHT = -2.0
    const val MAX_HEIGHT = 5.0
    const val MIN_ROTATION = -180.0
    const val MAX_ROTATION = 180.0
    const val MIN_LENGTH = 0.5
    const val MAX_LENGTH = 10.0
    const val MIN_ARC_DEGREES = 10.0
    const val MAX_ARC_DEGREES = 300.0
    const val MIN_CURVATURE = 0.0
    const val MAX_CURVATURE = 2.0
    const val MIN_THICKNESS = 0.0
    const val MAX_THICKNESS = 1.5
    const val MIN_RADIUS = 0.0
    const val MAX_RADIUS = 8.0
    const val MIN_DENSITY = 0.25
    const val MAX_DENSITY = 4.0
    const val MIN_PARTICLE_SIZE = 0.05
    const val MAX_PARTICLE_SIZE = 1.5
    const val MAX_BURST_COUNT = 64
    const val MAX_BURST_SPREAD = 89.0
    const val MAX_BURST_SPEED = 3.0
}

enum class VfxEditor2EffectType {
    ARC_SLASH,
    STRAIGHT_SLASH,
    RING,
    BURST,
}

data class VfxEditor2Transform(
    val forward: Double = 0.0,
    val side: Double = 0.0,
    val height: Double = 0.0,
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
) {
    init {
        require(listOf(forward, side, height, yaw, pitch, roll).all { it.isFinite() }) {
            "VFX Editor 2 transform values must be finite"
        }
        require(forward in VfxEditor2ProtocolLimits.MIN_FORWARD..VfxEditor2ProtocolLimits.MAX_FORWARD) {
            "VFX Editor 2 forward position is out of range"
        }
        require(side in VfxEditor2ProtocolLimits.MIN_SIDE..VfxEditor2ProtocolLimits.MAX_SIDE) {
            "VFX Editor 2 side position is out of range"
        }
        require(height in VfxEditor2ProtocolLimits.MIN_HEIGHT..VfxEditor2ProtocolLimits.MAX_HEIGHT) {
            "VFX Editor 2 height is out of range"
        }
        require(listOf(yaw, pitch, roll).all { it in VfxEditor2ProtocolLimits.MIN_ROTATION..VfxEditor2ProtocolLimits.MAX_ROTATION }) {
            "VFX Editor 2 rotation is out of range"
        }
    }

    companion object {
        fun clamped(
            forward: Double,
            side: Double,
            height: Double,
            yaw: Double,
            pitch: Double,
            roll: Double,
        ): VfxEditor2Transform {
            require(listOf(forward, side, height, yaw, pitch, roll).all { it.isFinite() }) {
                "VFX Editor 2 transform values must be finite"
            }
            return VfxEditor2Transform(
                forward.coerceIn(VfxEditor2ProtocolLimits.MIN_FORWARD, VfxEditor2ProtocolLimits.MAX_FORWARD),
                side.coerceIn(VfxEditor2ProtocolLimits.MIN_SIDE, VfxEditor2ProtocolLimits.MAX_SIDE),
                height.coerceIn(VfxEditor2ProtocolLimits.MIN_HEIGHT, VfxEditor2ProtocolLimits.MAX_HEIGHT),
                yaw.coerceIn(VfxEditor2ProtocolLimits.MIN_ROTATION, VfxEditor2ProtocolLimits.MAX_ROTATION),
                pitch.coerceIn(VfxEditor2ProtocolLimits.MIN_ROTATION, VfxEditor2ProtocolLimits.MAX_ROTATION),
                roll.coerceIn(VfxEditor2ProtocolLimits.MIN_ROTATION, VfxEditor2ProtocolLimits.MAX_ROTATION),
            )
        }
    }
}

data class VfxEditor2Appearance(
    val color: Int = 0xffffff,
    val particleSize: Double = 0.45,
    val density: Double = 1.0,
) {
    init {
        require(color in 0..0xffffff) { "VFX Editor 2 color is out of range" }
        require(particleSize.isFinite() && particleSize in VfxEditor2ProtocolLimits.MIN_PARTICLE_SIZE..VfxEditor2ProtocolLimits.MAX_PARTICLE_SIZE) {
            "VFX Editor 2 particle size is out of range"
        }
        require(density.isFinite() && density in VfxEditor2ProtocolLimits.MIN_DENSITY..VfxEditor2ProtocolLimits.MAX_DENSITY) {
            "VFX Editor 2 density is out of range"
        }
    }

    companion object {
        fun clamped(color: Int, particleSize: Double, density: Double): VfxEditor2Appearance {
            require(particleSize.isFinite() && density.isFinite()) {
                "VFX Editor 2 appearance values must be finite"
            }
            return VfxEditor2Appearance(
                color.coerceIn(0, 0xffffff),
                particleSize.coerceIn(VfxEditor2ProtocolLimits.MIN_PARTICLE_SIZE, VfxEditor2ProtocolLimits.MAX_PARTICLE_SIZE),
                density.coerceIn(VfxEditor2ProtocolLimits.MIN_DENSITY, VfxEditor2ProtocolLimits.MAX_DENSITY),
            )
        }
    }
}

sealed interface VfxEditor2Shape {
    val type: VfxEditor2EffectType

    data class ArcSlash(
        val length: Double = 5.0,
        val arcDegrees: Double = 170.0,
        val curvature: Double = 0.55,
        val thickness: Double = 0.35,
    ) : VfxEditor2Shape {
        override val type: VfxEditor2EffectType get() = VfxEditor2EffectType.ARC_SLASH

        init {
            require(listOf(length, arcDegrees, curvature, thickness).all { it.isFinite() }) {
                "VFX Editor 2 arc values must be finite"
            }
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH) {
                "VFX Editor 2 arc length is out of range"
            }
            require(arcDegrees in VfxEditor2ProtocolLimits.MIN_ARC_DEGREES..VfxEditor2ProtocolLimits.MAX_ARC_DEGREES) {
                "VFX Editor 2 arc span is out of range"
            }
            require(curvature in VfxEditor2ProtocolLimits.MIN_CURVATURE..VfxEditor2ProtocolLimits.MAX_CURVATURE) {
                "VFX Editor 2 curvature is out of range"
            }
            require(thickness in VfxEditor2ProtocolLimits.MIN_THICKNESS..VfxEditor2ProtocolLimits.MAX_THICKNESS) {
                "VFX Editor 2 arc thickness is out of range"
            }
        }

        companion object {
            fun clamped(length: Double, arcDegrees: Double, curvature: Double, thickness: Double): ArcSlash {
                require(listOf(length, arcDegrees, curvature, thickness).all { it.isFinite() }) {
                    "VFX Editor 2 arc values must be finite"
                }
                return ArcSlash(
                    length.coerceIn(VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH),
                    arcDegrees.coerceIn(VfxEditor2ProtocolLimits.MIN_ARC_DEGREES, VfxEditor2ProtocolLimits.MAX_ARC_DEGREES),
                    curvature.coerceIn(VfxEditor2ProtocolLimits.MIN_CURVATURE, VfxEditor2ProtocolLimits.MAX_CURVATURE),
                    thickness.coerceIn(VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS),
                )
            }
        }
    }

    data class StraightSlash(
        val length: Double = 5.0,
        val thickness: Double = 0.25,
    ) : VfxEditor2Shape {
        override val type: VfxEditor2EffectType get() = VfxEditor2EffectType.STRAIGHT_SLASH

        init {
            require(listOf(length, thickness).all { it.isFinite() }) {
                "VFX Editor 2 straight slash values must be finite"
            }
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH) {
                "VFX Editor 2 straight slash length is out of range"
            }
            require(thickness in VfxEditor2ProtocolLimits.MIN_THICKNESS..VfxEditor2ProtocolLimits.MAX_THICKNESS) {
                "VFX Editor 2 straight slash thickness is out of range"
            }
        }

        companion object {
            fun clamped(length: Double, thickness: Double): StraightSlash {
                require(listOf(length, thickness).all { it.isFinite() }) {
                    "VFX Editor 2 straight slash values must be finite"
                }
                return StraightSlash(
                    length.coerceIn(VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH),
                    thickness.coerceIn(VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS),
                )
            }
        }
    }

    data class Ring(
        val radius: Double = 1.5,
        val arcDegrees: Double = 360.0,
        val thickness: Double = 0.12,
    ) : VfxEditor2Shape {
        override val type: VfxEditor2EffectType get() = VfxEditor2EffectType.RING

        init {
            require(listOf(radius, arcDegrees, thickness).all { it.isFinite() }) {
                "VFX Editor 2 ring values must be finite"
            }
            require(radius in VfxEditor2ProtocolLimits.MIN_RADIUS..VfxEditor2ProtocolLimits.MAX_RADIUS) {
                "VFX Editor 2 ring radius is out of range"
            }
            require(arcDegrees in VfxEditor2ProtocolLimits.MIN_ARC_DEGREES..360.0) {
                "VFX Editor 2 ring arc is out of range"
            }
            require(thickness in VfxEditor2ProtocolLimits.MIN_THICKNESS..VfxEditor2ProtocolLimits.MAX_THICKNESS) {
                "VFX Editor 2 ring thickness is out of range"
            }
        }

        companion object {
            fun clamped(radius: Double, arcDegrees: Double, thickness: Double): Ring {
                require(listOf(radius, arcDegrees, thickness).all { it.isFinite() }) {
                    "VFX Editor 2 ring values must be finite"
                }
                return Ring(
                    radius.coerceIn(VfxEditor2ProtocolLimits.MIN_RADIUS, VfxEditor2ProtocolLimits.MAX_RADIUS),
                    arcDegrees.coerceIn(VfxEditor2ProtocolLimits.MIN_ARC_DEGREES, 360.0),
                    thickness.coerceIn(VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS),
                )
            }
        }
    }

    data class Burst(
        val radius: Double = 1.2,
        val count: Int = 20,
        val spread: Double = 45.0,
        val speed: Double = 0.45,
    ) : VfxEditor2Shape {
        override val type: VfxEditor2EffectType get() = VfxEditor2EffectType.BURST

        init {
            require(listOf(radius, spread, speed).all { it.isFinite() }) {
                "VFX Editor 2 burst values must be finite"
            }
            require(radius in VfxEditor2ProtocolLimits.MIN_RADIUS..VfxEditor2ProtocolLimits.MAX_RADIUS) {
                "VFX Editor 2 burst radius is out of range"
            }
            require(count in 1..VfxEditor2ProtocolLimits.MAX_BURST_COUNT) {
                "VFX Editor 2 burst count is out of range"
            }
            require(spread in 0.0..VfxEditor2ProtocolLimits.MAX_BURST_SPREAD) {
                "VFX Editor 2 burst spread is out of range"
            }
            require(speed in 0.0..VfxEditor2ProtocolLimits.MAX_BURST_SPEED) {
                "VFX Editor 2 burst speed is out of range"
            }
        }

        companion object {
            fun clamped(radius: Double, count: Int, spread: Double, speed: Double): Burst {
                require(listOf(radius, spread, speed).all { it.isFinite() }) {
                    "VFX Editor 2 burst values must be finite"
                }
                return Burst(
                    radius.coerceIn(VfxEditor2ProtocolLimits.MIN_RADIUS, VfxEditor2ProtocolLimits.MAX_RADIUS),
                    count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_BURST_COUNT),
                    spread.coerceIn(0.0, VfxEditor2ProtocolLimits.MAX_BURST_SPREAD),
                    speed.coerceIn(0.0, VfxEditor2ProtocolLimits.MAX_BURST_SPEED),
                )
            }
        }
    }
}

data class VfxEditor2Effect(
    val id: Long,
    val name: String,
    val type: VfxEditor2EffectType,
    val shape: VfxEditor2Shape,
    val transform: VfxEditor2Transform = VfxEditor2Transform(),
    val appearance: VfxEditor2Appearance = VfxEditor2Appearance(),
    val enabled: Boolean = true,
    val solo: Boolean = false,
) {
    init {
        require(id >= 0L) { "VFX Editor 2 effect id is invalid" }
        require(name.isNotBlank() && name.length <= VfxEditor2ProtocolLimits.MAX_EFFECT_NAME_LENGTH) {
            "VFX Editor 2 effect name is invalid"
        }
        require(shape.type == type) { "VFX Editor 2 effect type and shape do not match" }
    }
}

data class VfxEditor2Composition(val effects: List<VfxEditor2Effect>) {
    init {
        require(effects.size <= VfxEditor2ProtocolLimits.MAX_EFFECTS) {
            "VFX Editor 2 has too many effects"
        }
        require(effects.map { it.id }.toSet().size == effects.size) {
            "VFX Editor 2 effect ids must be unique"
        }
    }

    fun visibleEffects(): List<VfxEditor2Effect> {
        val hasSolo = effects.any { it.solo }
        return effects.filter { it.enabled && (!hasSolo || it.solo) }
    }

    fun add(effect: VfxEditor2Effect): VfxEditor2Composition? {
        if (effects.size >= VfxEditor2ProtocolLimits.MAX_EFFECTS || effects.any { it.id == effect.id }) return null
        return copy(effects = effects + effect)
    }

    fun duplicate(effectId: Long, newId: Long, newName: String): VfxEditor2Composition? {
        val source = effects.firstOrNull { it.id == effectId } ?: return null
        if (effects.size >= VfxEditor2ProtocolLimits.MAX_EFFECTS || effects.any { it.id == newId }) return null
        val duplicate = source.copy(id = newId, name = newName.take(VfxEditor2ProtocolLimits.MAX_EFFECT_NAME_LENGTH))
        return copy(effects = effects + duplicate)
    }

    fun remove(effectId: Long): VfxEditor2Composition = copy(effects = effects.filterNot { it.id == effectId })

    fun update(effectId: Long, transform: (VfxEditor2Effect) -> VfxEditor2Effect): VfxEditor2Composition =
        copy(effects = effects.map { if (it.id == effectId) transform(it) else it })
}

fun defaultVfxEditor2Composition(): VfxEditor2Composition = VfxEditor2Composition(
    listOf(
        VfxEditor2Effect(
            id = 1L,
            name = "Crimson Slash",
            type = VfxEditor2EffectType.ARC_SLASH,
            shape = VfxEditor2Shape.ArcSlash(length = 5.0, arcDegrees = 170.0, curvature = 0.55, thickness = 0.35),
            appearance = VfxEditor2Appearance(color = 0xc51f3a, particleSize = 0.58, density = 1.3),
        ),
        VfxEditor2Effect(
            id = 2L,
            name = "White Core",
            type = VfxEditor2EffectType.ARC_SLASH,
            shape = VfxEditor2Shape.ArcSlash(length = 5.0, arcDegrees = 170.0, curvature = 0.55, thickness = 0.12),
            appearance = VfxEditor2Appearance(color = 0xffffff, particleSize = 0.34, density = 1.7),
        ),
    ),
)

data class VfxEditor2Open(
    val targetLabel: String = "Ronin Q",
    val composition: VfxEditor2Composition = defaultVfxEditor2Composition(),
) : ProtocolMessage {
    init {
        require(targetLabel.isNotBlank() && targetLabel.length <= VfxEditor2ProtocolLimits.MAX_TARGET_LENGTH) {
            "VFX Editor 2 target label is invalid"
        }
    }
}

data class VfxEditor2PreviewStart(
    val requestId: Long,
    val composition: VfxEditor2Composition = defaultVfxEditor2Composition(),
) : ProtocolMessage {
    init { require(requestId >= 0L) { "VFX Editor 2 request id is invalid" } }
}

object VfxEditor2PreviewStop : ProtocolMessage

enum class VfxEditor2StatusKind {
    READY,
    PREVIEW_REQUESTED,
    PLAYING,
    STOPPED,
    ERROR,
}

data class VfxEditor2Status(
    val kind: VfxEditor2StatusKind,
    val message: String,
) : ProtocolMessage {
    init { require(message.length <= VfxEditor2ProtocolLimits.MAX_STATUS_LENGTH) { "VFX Editor 2 status is too long" } }
}

object ProtocolCodec {
    private const val MAX_PACKET_SIZE = 8192
    private const val HELLO = 1
    private const val HELLO_ACK = 2
    private const val ATTACK_INPUT = 10
    private const val ATTACK_STARTED = 11
    private const val ATTACK_HIT_CONFIRMED = 12
    private const val DODGE_INPUT = 13
    private const val AIR_JUMP_INPUT = 14
    private const val CLASS_SKILL_INPUT = 15
    private const val CLASS_RESOURCE_SNAPSHOT = 17
    private const val ATTACK_DEBUG_SHAPE = 18
    private const val GROUND_TELEGRAPH_START = 19
    private const val GROUND_TELEGRAPH_REMOVE = 20
    private const val VFX_EDITOR_OPEN = 21
    private const val VFX_SLASH_PREVIEW_REQUEST = 22
    private const val VFX_SLASH_SAVE_REQUEST = 23
    private const val VFX_SLASH_DRAFT_LIST = 24
    private const val VFX_SLASH_DRAFT_LOAD_REQUEST = 25
    private const val VFX_SLASH_DRAFT = 26
    private const val VFX_EDITOR_NOTICE = 27
    private const val VFX_SLASH_PREVIEW_CANCEL = 28
    private const val VFX_SLASH_APPLY_SKILL3 = 29
    private const val STARWEAVER_HUD_SNAPSHOT = 30
    private const val RONIN_HUD_SNAPSHOT = 31
    private const val VFX_EDITOR_2_OPEN = 40
    private const val VFX_EDITOR_2_PREVIEW_START = 41
    private const val VFX_EDITOR_2_PREVIEW_STOP = 42
    private const val VFX_EDITOR_2_STATUS = 43

    fun encode(message: ProtocolMessage): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            when (message) {
                is ProtocolHello -> {
                    data.writeByte(HELLO)
                    data.writeInt(message.version)
                }
                is ProtocolHelloAck -> {
                    data.writeByte(HELLO_ACK)
                    data.writeInt(message.version)
                }
                is AttackInput -> {
                    data.writeByte(ATTACK_INPUT)
                    data.writeByte(message.state.ordinal)
                    data.writeLong(message.sequence)
                }
                is AttackStarted -> {
                    data.writeByte(ATTACK_STARTED)
                    data.writeLong(message.attackExecutionId)
                }
                is AttackHitConfirmed -> {
                    data.writeByte(ATTACK_HIT_CONFIRMED)
                    data.writeLong(message.attackExecutionId)
                    data.writeLong(message.targetId.mostSignificantBits)
                    data.writeLong(message.targetId.leastSignificantBits)
                }
                is AttackDebugShape -> {
                    data.writeByte(ATTACK_DEBUG_SHAPE)
                    data.writeByte(message.kind.ordinal)
                    data.writeDouble(message.originX)
                    data.writeDouble(message.originY)
                    data.writeDouble(message.originZ)
                    data.writeDouble(message.directionX)
                    data.writeDouble(message.directionY)
                    data.writeDouble(message.directionZ)
                    data.writeDouble(message.range)
                    data.writeDouble(message.minForwardDot)
                    data.writeDouble(message.verticalRange)
                }
                is DodgeInput -> {
                    data.writeByte(DODGE_INPUT)
                    data.writeDouble(message.directionX)
                    data.writeDouble(message.directionZ)
                }
                is AirJumpInput -> {
                    data.writeByte(AIR_JUMP_INPUT)
                    data.writeDouble(message.directionX)
                    data.writeDouble(message.directionZ)
                }
                is ClassSkillInput -> {
                    data.writeByte(CLASS_SKILL_INPUT)
                    data.writeByte(message.slot.ordinal)
                    data.writeDouble(message.directionX)
                    data.writeDouble(message.directionZ)
                }
                is ClassResourceSnapshot -> {
                    data.writeByte(CLASS_RESOURCE_SNAPSHOT)
                    data.writeInt(message.mana)
                    data.writeInt(message.maxMana)
                    data.writeInt(message.skill1CooldownTicks)
                    data.writeInt(message.skill1CooldownMaxTicks)
                    data.writeInt(message.skill2CooldownTicks)
                    data.writeInt(message.skill2CooldownMaxTicks)
                    data.writeInt(message.skill3CooldownTicks)
                    data.writeInt(message.skill3CooldownMaxTicks)
                }
                is StarweaverHudSnapshot -> {
                    data.writeByte(STARWEAVER_HUD_SNAPSHOT)
                    data.writeBoolean(message.selected)
                    data.writeByte(message.queue.size)
                    message.queue.forEach { data.writeByte(it.ordinal) }
                    data.writeByte(message.stored.ordinal)
                    data.writeBoolean(message.conjunctionAvailable)
                    data.writeBoolean(message.conjunctionUsed)
                    data.writeInt(message.reloadTicksRemaining)
                }
                is RoninHudSnapshot -> {
                    data.writeByte(RONIN_HUD_SNAPSHOT)
                    data.writeBoolean(message.selected)
                    data.writeByte(message.iaido)
                    data.writeInt(message.qCooldownTicks)
                    data.writeInt(message.eCooldownTicks)
                    data.writeInt(message.rCooldownTicks)
                    data.writeInt(message.movementLockTicksRemaining)
                    data.writeByte(message.wVariant)
                }
                is GroundTelegraphStart -> {
                    data.writeByte(GROUND_TELEGRAPH_START)
                    data.writeLong(message.telegraphId)
                    data.writeDouble(message.centerX)
                    data.writeDouble(message.centerY)
                    data.writeDouble(message.centerZ)
                    data.writeDouble(message.facingX)
                    data.writeDouble(message.facingZ)
                    data.writeDouble(message.radius)
                    data.writeDouble(message.angleDegrees)
                    data.writeInt(message.durationTicks)
                }
                is GroundTelegraphRemove -> {
                    data.writeByte(GROUND_TELEGRAPH_REMOVE)
                    data.writeLong(message.telegraphId)
                }
                is VfxEditorOpen -> {
                    data.writeByte(VFX_EDITOR_OPEN)
                    writeSlashParameters(data, message.parameters)
                }
                is VfxSlashPreviewRequest -> {
                    data.writeByte(VFX_SLASH_PREVIEW_REQUEST)
                    data.writeLong(message.requestId)
                    writeSlashParameters(data, message.parameters)
                }
                is VfxSlashApplySkill3 -> {
                    data.writeByte(VFX_SLASH_APPLY_SKILL3)
                    writeSlashParameters(data, message.parameters)
                }
                VfxSlashPreviewCancel -> data.writeByte(VFX_SLASH_PREVIEW_CANCEL)
                is VfxSlashSaveRequest -> {
                    data.writeByte(VFX_SLASH_SAVE_REQUEST)
                    writeString(data, message.name)
                    writeSlashParameters(data, message.parameters)
                }
                is VfxSlashDraftList -> {
                    data.writeByte(VFX_SLASH_DRAFT_LIST)
                    data.writeByte(message.names.size)
                    message.names.forEach { writeString(data, it) }
                }
                is VfxSlashDraftLoadRequest -> {
                    data.writeByte(VFX_SLASH_DRAFT_LOAD_REQUEST)
                    writeString(data, message.name)
                }
                is VfxSlashDraft -> {
                    data.writeByte(VFX_SLASH_DRAFT)
                    writeString(data, message.name)
                    writeSlashParameters(data, message.parameters)
                }
                is VfxEditorNotice -> {
                    data.writeByte(VFX_EDITOR_NOTICE)
                    writeString(data, message.text)
                }
                is VfxEditor2Open -> {
                    data.writeByte(VFX_EDITOR_2_OPEN)
                    writeString(data, message.targetLabel)
                    writeVfxEditor2Composition(data, message.composition)
                }
                is VfxEditor2PreviewStart -> {
                    data.writeByte(VFX_EDITOR_2_PREVIEW_START)
                    data.writeLong(message.requestId)
                    writeVfxEditor2Composition(data, message.composition)
                }
                VfxEditor2PreviewStop -> data.writeByte(VFX_EDITOR_2_PREVIEW_STOP)
                is VfxEditor2Status -> {
                    data.writeByte(VFX_EDITOR_2_STATUS)
                    data.writeByte(message.kind.ordinal)
                    writeString(data, message.message)
                }
            }
        }
        return output.toByteArray().also {
            require(it.size <= MAX_PACKET_SIZE) { "ProjectS protocol packet exceeds $MAX_PACKET_SIZE bytes" }
        }
    }

    fun decode(bytes: ByteArray): ProtocolMessage {
        require(bytes.isNotEmpty()) { "ProjectS protocol packet is empty" }
        require(bytes.size <= MAX_PACKET_SIZE) { "ProjectS protocol packet exceeds $MAX_PACKET_SIZE bytes" }

        return try {
            decodePacket(bytes)
        } catch (error: IOException) {
            throw IllegalArgumentException("Malformed ProjectS protocol packet", error)
        }
    }

    private fun decodePacket(bytes: ByteArray): ProtocolMessage {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val message = when (val type = input.readUnsignedByte()) {
            HELLO -> ProtocolHello(input.readInt())
            HELLO_ACK -> ProtocolHelloAck(input.readInt())
            ATTACK_INPUT -> {
                val stateId = input.readUnsignedByte()
                val state = AttackInputState.entries.getOrNull(stateId)
                    ?: throw IllegalArgumentException("Unknown AttackInput state: $stateId")
                AttackInput(state, input.readLong())
            }
            ATTACK_STARTED -> AttackStarted(input.readLong())
            ATTACK_HIT_CONFIRMED -> AttackHitConfirmed(
                input.readLong(),
                UUID(input.readLong(), input.readLong()),
            )
            ATTACK_DEBUG_SHAPE -> {
                val kindId = input.readUnsignedByte()
                val kind = AttackDebugShapeKind.entries.getOrNull(kindId)
                    ?: throw IllegalArgumentException("Unknown AttackDebugShape kind: $kindId")
                AttackDebugShape(
                    kind,
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                )
            }
            DODGE_INPUT -> DodgeInput(input.readDouble(), input.readDouble())
            AIR_JUMP_INPUT -> AirJumpInput(input.readDouble(), input.readDouble())
            CLASS_SKILL_INPUT -> {
                val slotId = input.readUnsignedByte()
                val slot = ClassSkillSlot.entries.getOrNull(slotId)
                    ?: throw IllegalArgumentException("Unknown ClassSkillInput slot: $slotId")
                ClassSkillInput(slot, input.readDouble(), input.readDouble())
            }
            CLASS_RESOURCE_SNAPSHOT -> ClassResourceSnapshot(
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
            )
            STARWEAVER_HUD_SNAPSHOT -> {
                val selected = input.readBoolean()
                val queueSize = input.readUnsignedByte()
                require(queueSize <= StarweaverHudLimits.MAX_QUEUE_SIZE) {
                    "Starweaver HUD queue is too large"
                }
                val queue = List(queueSize) {
                    val markId = input.readUnsignedByte()
                    StarweaverHudCelestial.entries.getOrNull(markId)
                        ?: throw IllegalArgumentException("Unknown Starweaver HUD celestial: $markId")
                }
                val storedId = input.readUnsignedByte()
                val stored = StarweaverHudCelestial.entries.getOrNull(storedId)
                    ?: throw IllegalArgumentException("Unknown stored Starweaver HUD celestial: $storedId")
                StarweaverHudSnapshot(
                    selected = selected,
                    queue = queue,
                    stored = stored,
                    conjunctionAvailable = input.readBoolean(),
                    conjunctionUsed = input.readBoolean(),
                    reloadTicksRemaining = input.readInt(),
                )
            }
            RONIN_HUD_SNAPSHOT -> RoninHudSnapshot(
                selected = input.readBoolean(),
                iaido = input.readUnsignedByte(),
                qCooldownTicks = input.readInt(),
                eCooldownTicks = input.readInt(),
                rCooldownTicks = input.readInt(),
                movementLockTicksRemaining = input.readInt(),
                wVariant = input.readUnsignedByte(),
            )
            GROUND_TELEGRAPH_START -> GroundTelegraphStart.clamped(
                telegraphId = input.readLong(),
                centerX = input.readDouble(),
                centerY = input.readDouble(),
                centerZ = input.readDouble(),
                facingX = input.readDouble(),
                facingZ = input.readDouble(),
                radius = input.readDouble(),
                angleDegrees = input.readDouble(),
                durationTicks = input.readInt(),
            )
            GROUND_TELEGRAPH_REMOVE -> GroundTelegraphRemove(input.readLong())
            VFX_EDITOR_OPEN -> VfxEditorOpen(readSlashParameters(input))
            VFX_SLASH_PREVIEW_REQUEST -> VfxSlashPreviewRequest(input.readLong(), readSlashParameters(input))
            VFX_SLASH_APPLY_SKILL3 -> VfxSlashApplySkill3(readSlashParameters(input))
            VFX_SLASH_PREVIEW_CANCEL -> VfxSlashPreviewCancel
            VFX_SLASH_SAVE_REQUEST -> VfxSlashSaveRequest(readString(input), readSlashParameters(input))
            VFX_SLASH_DRAFT_LIST -> {
                val count = input.readUnsignedByte()
                require(count <= SlashEditorLimits.MAX_DRAFTS) { "Too many slash drafts" }
                VfxSlashDraftList(List(count) { readString(input) })
            }
            VFX_SLASH_DRAFT_LOAD_REQUEST -> VfxSlashDraftLoadRequest(readString(input))
            VFX_SLASH_DRAFT -> VfxSlashDraft(readString(input), readSlashParameters(input))
            VFX_EDITOR_NOTICE -> VfxEditorNotice(readString(input))
            VFX_EDITOR_2_OPEN -> VfxEditor2Open(readString(input), readVfxEditor2Composition(input))
            VFX_EDITOR_2_PREVIEW_START -> VfxEditor2PreviewStart(input.readLong(), readVfxEditor2Composition(input))
            VFX_EDITOR_2_PREVIEW_STOP -> VfxEditor2PreviewStop
            VFX_EDITOR_2_STATUS -> {
                val kind = VfxEditor2StatusKind.entries.getOrNull(input.readUnsignedByte())
                    ?: throw IllegalArgumentException("Unknown VFX Editor 2 status")
                VfxEditor2Status(kind, readString(input))
            }
            else -> throw IllegalArgumentException("Unknown ProjectS message type: $type")
        }
        require(input.available() == 0) { "Unexpected trailing ProjectS protocol data" }
        return message
    }

    private fun writeSlashParameters(data: DataOutputStream, parameters: SlashEditorParameters) {
        data.writeDouble(parameters.originY)
        data.writeDouble(parameters.forwardOffset)
        data.writeDouble(parameters.length)
        data.writeDouble(parameters.arcSpan)
        data.writeDouble(parameters.curvature)
        data.writeDouble(parameters.tilt)
        data.writeDouble(parameters.yaw)
        data.writeDouble(parameters.width)
        data.writeInt(parameters.laneCount)
        data.writeDouble(parameters.laneSpacing)
        data.writeDouble(parameters.particleSize)
        data.writeDouble(parameters.spacing)
        data.writeInt(parameters.durationTicks)
        data.writeInt(parameters.color)
        data.writeDouble(parameters.targetDistance)
    }

    private fun writeVfxEditor2Composition(data: DataOutputStream, composition: VfxEditor2Composition) {
        data.writeByte(composition.effects.size)
        composition.effects.forEach { effect ->
            data.writeLong(effect.id)
            writeString(data, effect.name)
            data.writeByte(effect.type.ordinal)
            data.writeBoolean(effect.enabled)
            data.writeBoolean(effect.solo)
            data.writeDouble(effect.transform.forward)
            data.writeDouble(effect.transform.side)
            data.writeDouble(effect.transform.height)
            data.writeDouble(effect.transform.yaw)
            data.writeDouble(effect.transform.pitch)
            data.writeDouble(effect.transform.roll)
            data.writeInt(effect.appearance.color)
            data.writeDouble(effect.appearance.particleSize)
            data.writeDouble(effect.appearance.density)
            when (val shape = effect.shape) {
                is VfxEditor2Shape.ArcSlash -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.arcDegrees)
                    data.writeDouble(shape.curvature)
                    data.writeDouble(shape.thickness)
                }
                is VfxEditor2Shape.StraightSlash -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.thickness)
                }
                is VfxEditor2Shape.Ring -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.arcDegrees)
                    data.writeDouble(shape.thickness)
                }
                is VfxEditor2Shape.Burst -> {
                    data.writeDouble(shape.radius)
                    data.writeInt(shape.count)
                    data.writeDouble(shape.spread)
                    data.writeDouble(shape.speed)
                }
            }
        }
    }

    private fun readVfxEditor2Composition(input: DataInputStream): VfxEditor2Composition {
        val count = input.readUnsignedByte()
        require(count <= VfxEditor2ProtocolLimits.MAX_EFFECTS) {
            "VFX Editor 2 has too many effects"
        }
        val effects = List(count) {
            val id = input.readLong()
            val name = readString(input)
            val typeId = input.readUnsignedByte()
            val type = VfxEditor2EffectType.entries.getOrNull(typeId)
                ?: throw IllegalArgumentException("Unknown VFX Editor 2 effect type: $typeId")
            val enabled = input.readBoolean()
            val solo = input.readBoolean()
            val transform = VfxEditor2Transform.clamped(
                forward = input.readDouble(),
                side = input.readDouble(),
                height = input.readDouble(),
                yaw = input.readDouble(),
                pitch = input.readDouble(),
                roll = input.readDouble(),
            )
            val appearance = VfxEditor2Appearance.clamped(
                color = input.readInt(),
                particleSize = input.readDouble(),
                density = input.readDouble(),
            )
            val shape = when (type) {
                VfxEditor2EffectType.ARC_SLASH -> VfxEditor2Shape.ArcSlash.clamped(
                    length = input.readDouble(),
                    arcDegrees = input.readDouble(),
                    curvature = input.readDouble(),
                    thickness = input.readDouble(),
                )
                VfxEditor2EffectType.STRAIGHT_SLASH -> VfxEditor2Shape.StraightSlash.clamped(
                    length = input.readDouble(),
                    thickness = input.readDouble(),
                )
                VfxEditor2EffectType.RING -> VfxEditor2Shape.Ring.clamped(
                    radius = input.readDouble(),
                    arcDegrees = input.readDouble(),
                    thickness = input.readDouble(),
                )
                VfxEditor2EffectType.BURST -> VfxEditor2Shape.Burst.clamped(
                    radius = input.readDouble(),
                    count = input.readInt(),
                    spread = input.readDouble(),
                    speed = input.readDouble(),
                )
            }
            VfxEditor2Effect(
                id = id,
                name = name,
                type = type,
                shape = shape,
                transform = transform,
                appearance = appearance,
                enabled = enabled,
                solo = solo,
            )
        }
        return VfxEditor2Composition(effects)
    }

    private fun readSlashParameters(input: DataInputStream): SlashEditorParameters = SlashEditorParameters.clamped(
        originY = input.readDouble(),
        forwardOffset = input.readDouble(),
        length = input.readDouble(),
        arcSpan = input.readDouble(),
        curvature = input.readDouble(),
        tilt = input.readDouble(),
        yaw = input.readDouble(),
        width = input.readDouble(),
        laneCount = input.readInt(),
        laneSpacing = input.readDouble(),
        particleSize = input.readDouble(),
        spacing = input.readDouble(),
        durationTicks = input.readInt(),
        color = input.readInt(),
        targetDistance = input.readDouble(),
    )

    private fun writeString(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= SlashEditorLimits.MAX_NAME_LENGTH * 4) { "ProjectS string is too long" }
        data.writeShort(bytes.size)
        data.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readUnsignedShort()
        require(size <= SlashEditorLimits.MAX_NAME_LENGTH * 4) { "ProjectS string is too long" }
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
    }
}
