package dev.projects.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val PROJECTS_CHANNEL = "projects:protocol"

object ProtocolVersion {
    const val CURRENT = 18

    fun requireCompatible(version: Int) {
        if (version != CURRENT) {
            throw ProtocolVersionMismatchException(CURRENT, version)
        }
    }
}

class ProtocolVersionMismatchException(expected: Int, actual: Int) :
    IllegalArgumentException("ProjectS protocol version mismatch: expected $expected, received $actual")

class ProtocolDecodeException(
    val packetId: Int?,
    val packetName: String,
    val reason: String,
    cause: Throwable,
) : IllegalArgumentException("Malformed ProjectS protocol packet", cause)

const val VFX_EDITOR_2_MAX_EFFECTS = 8
const val VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT = 512
const val VFX_EDITOR_2_MAX_TOTAL_SAMPLES = 4096
const val VFX_EDITOR_2_DEFAULT_TIMELINE_LENGTH_TICKS = 40
const val VFX_EDITOR_2_MAX_TIMELINE_LENGTH_TICKS = 200
const val VFX_EDITOR_2_MAX_EFFECT_START_TICKS = 199
const val VFX_EDITOR_2_MAX_EFFECT_DURATION_TICKS = 200
const val VFX_EDITOR_2_DEFAULT_EFFECT_DURATION_TICKS = 12
const val VFX_EDITOR_2_MAX_COMPOSITION_NAME_LENGTH = 48
const val VFX_EDITOR_2_MAX_SAVED_COMPOSITIONS = 128

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
    const val MAX_EFFECTS = VFX_EDITOR_2_MAX_EFFECTS
    const val MAX_EFFECT_NAME_LENGTH = 32
    const val MAX_COMPOSITION_NAME_LENGTH = VFX_EDITOR_2_MAX_COMPOSITION_NAME_LENGTH
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
    const val MAX_COUNT = 256
    const val MAX_WAVES = 8
    const val MAX_TURNS = 8.0
    const val MAX_JITTER = 2.0
    const val MAX_HOPS = 32
    const val MAX_GRID_ROWS = 32
    const val MAX_POINTS = 12
    const val MAX_SPEED = 3.0
    const val MAX_SHARPNESS = 2.0
    const val MAX_PHASE = 360.0
    const val MAX_ANGLE = 360.0
}

enum class VfxEditor2EffectType {
    ARC_SLASH,
    STRAIGHT_SLASH,
    RING,
    BURST,
    BEZIER,
    WAVE,
    LIGHTNING,
    SPIRAL,
    HELIX,
    DISK,
    SECTOR,
    GRID,
    SPHERE,
    ORB,
    DOME,
    CYLINDER,
    CONE,
    BOX,
    TORUS,
    STAR_FLOWER,
    CROSS,
    SHOCKWAVE,
    VORTEX,
    TORNADO,
    FOUNTAIN,
    SPHERE_BURST,
    CONE_BURST,
}

enum class VfxEditor2Direction { UP, DOWN }

enum class VfxEditor2BoxMode { EDGES, FACES }

enum class VfxEditor2ParticleType { DUST }

fun isVfxEditor2Instant(type: VfxEditor2EffectType): Boolean = type in setOf(
    VfxEditor2EffectType.BURST,
    VfxEditor2EffectType.SPHERE_BURST,
    VfxEditor2EffectType.CONE_BURST,
)

private val SAFE_VFX_EDITOR_2_COMPOSITION_NAME = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,47}")

fun isSafeVfxEditor2CompositionName(name: String): Boolean =
    name.length in 1..VFX_EDITOR_2_MAX_COMPOSITION_NAME_LENGTH &&
        SAFE_VFX_EDITOR_2_COMPOSITION_NAME.matches(name)

private fun requireFinite(label: String, vararg values: Double) {
    require(values.all { it.isFinite() }) { "VFX Editor 2 $label values must be finite" }
}

private fun clampFinite(value: Double, minimum: Double, maximum: Double, label: String): Double {
    require(value.isFinite()) { "VFX Editor 2 $label must be finite" }
    return value.coerceIn(minimum, maximum)
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
            return VfxEditor2Transform(
                clampFinite(forward, VfxEditor2ProtocolLimits.MIN_FORWARD, VfxEditor2ProtocolLimits.MAX_FORWARD, "forward"),
                clampFinite(side, VfxEditor2ProtocolLimits.MIN_SIDE, VfxEditor2ProtocolLimits.MAX_SIDE, "side"),
                clampFinite(height, VfxEditor2ProtocolLimits.MIN_HEIGHT, VfxEditor2ProtocolLimits.MAX_HEIGHT, "height"),
                clampFinite(yaw, VfxEditor2ProtocolLimits.MIN_ROTATION, VfxEditor2ProtocolLimits.MAX_ROTATION, "yaw"),
                clampFinite(pitch, VfxEditor2ProtocolLimits.MIN_ROTATION, VfxEditor2ProtocolLimits.MAX_ROTATION, "pitch"),
                clampFinite(roll, VfxEditor2ProtocolLimits.MIN_ROTATION, VfxEditor2ProtocolLimits.MAX_ROTATION, "roll"),
            )
        }
    }
}

data class VfxEditor2Appearance(
    val color: Int = 0xffffff,
    val particleSize: Double = 0.45,
    val density: Double = 1.0,
    val particleType: VfxEditor2ParticleType = VfxEditor2ParticleType.DUST,
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
        fun clamped(
            color: Int,
            particleSize: Double,
            density: Double,
            particleType: VfxEditor2ParticleType = VfxEditor2ParticleType.DUST,
        ): VfxEditor2Appearance {
            return VfxEditor2Appearance(
                color.coerceIn(0, 0xffffff),
                clampFinite(particleSize, VfxEditor2ProtocolLimits.MIN_PARTICLE_SIZE, VfxEditor2ProtocolLimits.MAX_PARTICLE_SIZE, "particle size"),
                clampFinite(density, VfxEditor2ProtocolLimits.MIN_DENSITY, VfxEditor2ProtocolLimits.MAX_DENSITY, "density"),
                particleType,
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
                return ArcSlash(
                    clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "arc length"),
                    clampFinite(arcDegrees, VfxEditor2ProtocolLimits.MIN_ARC_DEGREES, VfxEditor2ProtocolLimits.MAX_ARC_DEGREES, "arc span"),
                    clampFinite(curvature, VfxEditor2ProtocolLimits.MIN_CURVATURE, VfxEditor2ProtocolLimits.MAX_CURVATURE, "curvature"),
                    clampFinite(thickness, VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS, "arc thickness"),
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
                return StraightSlash(
                    clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "straight slash length"),
                    clampFinite(thickness, VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS, "straight slash thickness"),
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
                return Ring(
                    clampFinite(radius, VfxEditor2ProtocolLimits.MIN_RADIUS, VfxEditor2ProtocolLimits.MAX_RADIUS, "ring radius"),
                    clampFinite(arcDegrees, VfxEditor2ProtocolLimits.MIN_ARC_DEGREES, 360.0, "ring arc"),
                    clampFinite(thickness, VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS, "ring thickness"),
                )
            }
        }
    }

    data class Burst(
        val radius: Double = 1.2,
        val count: Int = 20,
        val spread: Double = 45.0,
        val speed: Double = 0.45,
        val seed: Long = 42L,
    ) : VfxEditor2Shape {
        override val type: VfxEditor2EffectType get() = VfxEditor2EffectType.BURST

        init {
            requireFinite("burst", radius, spread, speed)
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
            fun clamped(radius: Double, count: Int, spread: Double, speed: Double, seed: Long = 42L): Burst {
                return Burst(
                    clampFinite(radius, VfxEditor2ProtocolLimits.MIN_RADIUS, VfxEditor2ProtocolLimits.MAX_RADIUS, "burst radius"),
                    count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_BURST_COUNT),
                    clampFinite(spread, 0.0, VfxEditor2ProtocolLimits.MAX_BURST_SPREAD, "burst spread"),
                    clampFinite(speed, 0.0, VfxEditor2ProtocolLimits.MAX_BURST_SPEED, "burst speed"),
                    seed,
                )
            }
        }
    }

    data class Bezier(
        val length: Double = 4.5,
        val controlForward: Double = 2.0,
        val controlSide: Double = 1.2,
        val controlHeight: Double = 0.8,
        val endSide: Double = 0.0,
        val endHeight: Double = 0.0,
        val thickness: Double = 0.18,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.BEZIER
        init {
            requireFinite("bezier", length, controlForward, controlSide, controlHeight, endSide, endHeight, thickness)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(controlForward in VfxEditor2ProtocolLimits.MIN_FORWARD..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(controlSide in VfxEditor2ProtocolLimits.MIN_SIDE..VfxEditor2ProtocolLimits.MAX_SIDE)
            require(controlHeight in VfxEditor2ProtocolLimits.MIN_HEIGHT..VfxEditor2ProtocolLimits.MAX_HEIGHT)
            require(endSide in VfxEditor2ProtocolLimits.MIN_SIDE..VfxEditor2ProtocolLimits.MAX_SIDE)
            require(endHeight in VfxEditor2ProtocolLimits.MIN_HEIGHT..VfxEditor2ProtocolLimits.MAX_HEIGHT)
            require(thickness in VfxEditor2ProtocolLimits.MIN_THICKNESS..VfxEditor2ProtocolLimits.MAX_THICKNESS)
        }
        companion object {
            fun clamped(length: Double, controlForward: Double, controlSide: Double, controlHeight: Double, endSide: Double, endHeight: Double, thickness: Double): Bezier = Bezier(
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "bezier length"),
                clampFinite(controlForward, VfxEditor2ProtocolLimits.MIN_FORWARD, VfxEditor2ProtocolLimits.MAX_LENGTH, "bezier control forward"),
                clampFinite(controlSide, VfxEditor2ProtocolLimits.MIN_SIDE, VfxEditor2ProtocolLimits.MAX_SIDE, "bezier control side"),
                clampFinite(controlHeight, VfxEditor2ProtocolLimits.MIN_HEIGHT, VfxEditor2ProtocolLimits.MAX_HEIGHT, "bezier control height"),
                clampFinite(endSide, VfxEditor2ProtocolLimits.MIN_SIDE, VfxEditor2ProtocolLimits.MAX_SIDE, "bezier end side"),
                clampFinite(endHeight, VfxEditor2ProtocolLimits.MIN_HEIGHT, VfxEditor2ProtocolLimits.MAX_HEIGHT, "bezier end height"),
                clampFinite(thickness, VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS, "bezier thickness"),
            )
        }
    }

    data class Wave(
        val length: Double = 4.0,
        val amplitude: Double = 0.7,
        val waves: Int = 2,
        val phaseDegrees: Double = 0.0,
        val thickness: Double = 0.18,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.WAVE
        init {
            requireFinite("wave", length, amplitude, phaseDegrees, thickness)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(amplitude in 0.0..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(waves in 1..VfxEditor2ProtocolLimits.MAX_WAVES)
            require(phaseDegrees in -VfxEditor2ProtocolLimits.MAX_PHASE..VfxEditor2ProtocolLimits.MAX_PHASE)
            require(thickness in VfxEditor2ProtocolLimits.MIN_THICKNESS..VfxEditor2ProtocolLimits.MAX_THICKNESS)
        }
        companion object {
            fun clamped(length: Double, amplitude: Double, waves: Int, phaseDegrees: Double, thickness: Double): Wave = Wave(
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "wave length"),
                clampFinite(amplitude, 0.0, VfxEditor2ProtocolLimits.MAX_RADIUS, "wave amplitude"),
                waves.coerceIn(1, VfxEditor2ProtocolLimits.MAX_WAVES),
                clampFinite(phaseDegrees, -VfxEditor2ProtocolLimits.MAX_PHASE, VfxEditor2ProtocolLimits.MAX_PHASE, "wave phase"),
                clampFinite(thickness, VfxEditor2ProtocolLimits.MIN_THICKNESS, VfxEditor2ProtocolLimits.MAX_THICKNESS, "wave thickness"),
            )
        }
    }

    data class Lightning(
        val length: Double = 5.0,
        val jitter: Double = 0.35,
        val hops: Int = 10,
        val seed: Long = 42L,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.LIGHTNING
        init {
            requireFinite("lightning", length, jitter)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(jitter in 0.0..VfxEditor2ProtocolLimits.MAX_JITTER)
            require(hops in 1..VfxEditor2ProtocolLimits.MAX_HOPS)
        }
        companion object {
            fun clamped(length: Double, jitter: Double, hops: Int, seed: Long): Lightning = Lightning(
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "lightning length"),
                clampFinite(jitter, 0.0, VfxEditor2ProtocolLimits.MAX_JITTER, "lightning jitter"),
                hops.coerceIn(1, VfxEditor2ProtocolLimits.MAX_HOPS),
                seed,
            )
        }
    }

    data class Spiral(
        val radius: Double = 1.6,
        val length: Double = 3.0,
        val turns: Double = 2.0,
        val angleOffsetDegrees: Double = 0.0,
        val reverse: Boolean = false,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.SPIRAL
        init {
            requireFinite("spiral", radius, length, turns, angleOffsetDegrees)
            require(radius in VfxEditor2ProtocolLimits.MIN_RADIUS..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(turns in 0.25..VfxEditor2ProtocolLimits.MAX_TURNS)
            require(angleOffsetDegrees in -VfxEditor2ProtocolLimits.MAX_PHASE..VfxEditor2ProtocolLimits.MAX_PHASE)
        }
        companion object {
            fun clamped(radius: Double, length: Double, turns: Double, angleOffsetDegrees: Double, reverse: Boolean): Spiral = Spiral(
                clampFinite(radius, 0.0, VfxEditor2ProtocolLimits.MAX_RADIUS, "spiral radius"),
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "spiral length"),
                clampFinite(turns, 0.25, VfxEditor2ProtocolLimits.MAX_TURNS, "spiral turns"),
                clampFinite(angleOffsetDegrees, -VfxEditor2ProtocolLimits.MAX_PHASE, VfxEditor2ProtocolLimits.MAX_PHASE, "spiral angle offset"),
                reverse,
            )
        }
    }

    data class Helix(
        val radius: Double = 1.2,
        val length: Double = 3.0,
        val turns: Double = 2.0,
        val phaseDegrees: Double = 0.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.HELIX
        init {
            requireFinite("helix", radius, length, turns, phaseDegrees)
            require(radius in VfxEditor2ProtocolLimits.MIN_RADIUS..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(turns in 0.25..VfxEditor2ProtocolLimits.MAX_TURNS)
            require(phaseDegrees in -VfxEditor2ProtocolLimits.MAX_PHASE..VfxEditor2ProtocolLimits.MAX_PHASE)
        }
        companion object {
            fun clamped(radius: Double, length: Double, turns: Double, phaseDegrees: Double): Helix = Helix(
                clampFinite(radius, 0.0, VfxEditor2ProtocolLimits.MAX_RADIUS, "helix radius"),
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "helix length"),
                clampFinite(turns, 0.25, VfxEditor2ProtocolLimits.MAX_TURNS, "helix turns"),
                clampFinite(phaseDegrees, -VfxEditor2ProtocolLimits.MAX_PHASE, VfxEditor2ProtocolLimits.MAX_PHASE, "helix phase"),
            )
        }
    }

    data class Disk(
        val radius: Double = 2.0,
        val innerRadius: Double = 0.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.DISK
        init {
            requireFinite("disk", radius, innerRadius)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(innerRadius in 0.0..radius)
        }
        companion object {
            fun clamped(radius: Double, innerRadius: Double): Disk {
                val safeRadius = clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "disk radius")
                return Disk(safeRadius, clampFinite(innerRadius, 0.0, safeRadius, "disk inner radius"))
            }
        }
    }

    data class Sector(
        val radius: Double = 2.0,
        val angleDegrees: Double = 90.0,
        val innerRadius: Double = 0.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.SECTOR
        init {
            requireFinite("sector", radius, angleDegrees, innerRadius)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(angleDegrees in 1.0..VfxEditor2ProtocolLimits.MAX_ANGLE)
            require(innerRadius in 0.0..radius)
        }
        companion object {
            fun clamped(radius: Double, angleDegrees: Double, innerRadius: Double): Sector {
                val safeRadius = clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "sector radius")
                return Sector(
                    safeRadius,
                    clampFinite(angleDegrees, 1.0, VfxEditor2ProtocolLimits.MAX_ANGLE, "sector angle"),
                    clampFinite(innerRadius, 0.0, safeRadius, "sector inner radius"),
                )
            }
        }
    }

    data class Grid(
        val width: Double = 3.0,
        val height: Double = 2.0,
        val rows: Int = 6,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.GRID
        init {
            requireFinite("grid", width, height)
            require(width in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(height in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(rows in 1..VfxEditor2ProtocolLimits.MAX_GRID_ROWS)
        }
        companion object {
            fun clamped(width: Double, height: Double, rows: Int): Grid = Grid(
                clampFinite(width, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "grid width"),
                clampFinite(height, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "grid height"),
                rows.coerceIn(1, VfxEditor2ProtocolLimits.MAX_GRID_ROWS),
            )
        }
    }

    data class Sphere(
        val radius: Double = 2.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.SPHERE
        init {
            requireFinite("sphere", radius)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
        }
        companion object {
            fun clamped(radius: Double): Sphere = Sphere(clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "sphere radius"))
        }
    }

    data class Orb(
        val radius: Double = 1.5,
        val count: Int = 64,
        val seed: Long = 42L,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.ORB
        init {
            requireFinite("orb", radius)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(count in 1..VfxEditor2ProtocolLimits.MAX_COUNT)
        }
        companion object {
            fun clamped(radius: Double, count: Int, seed: Long): Orb = Orb(
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "orb radius"),
                count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_COUNT),
                seed,
            )
        }
    }

    data class Dome(
        val radius: Double = 2.0,
        val direction: VfxEditor2Direction = VfxEditor2Direction.UP,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.DOME
        init {
            requireFinite("dome", radius)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
        }
        companion object {
            fun clamped(radius: Double, direction: VfxEditor2Direction): Dome = Dome(
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "dome radius"),
                direction,
            )
        }
    }

    data class Cylinder(
        val radius: Double = 0.8,
        val height: Double = 3.0,
        val count: Int = 64,
        val shell: Boolean = false,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.CYLINDER
        init {
            requireFinite("cylinder", radius, height)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(height in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(count in 1..VfxEditor2ProtocolLimits.MAX_COUNT)
        }
        companion object {
            fun clamped(radius: Double, height: Double, count: Int, shell: Boolean): Cylinder = Cylinder(
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "cylinder radius"),
                clampFinite(height, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "cylinder height"),
                count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_COUNT),
                shell,
            )
        }
    }

    data class Cone(
        val length: Double = 3.0,
        val radius: Double = 1.4,
        val angleDegrees: Double = 32.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.CONE
        init {
            requireFinite("cone", length, radius, angleDegrees)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(angleDegrees in 1.0..VfxEditor2ProtocolLimits.MAX_BURST_SPREAD)
        }
        companion object {
            fun clamped(length: Double, radius: Double, angleDegrees: Double): Cone = Cone(
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "cone length"),
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "cone radius"),
                clampFinite(angleDegrees, 1.0, VfxEditor2ProtocolLimits.MAX_BURST_SPREAD, "cone angle"),
            )
        }
    }

    data class Box(
        val width: Double = 2.0,
        val height: Double = 2.0,
        val depth: Double = 2.0,
        val mode: VfxEditor2BoxMode = VfxEditor2BoxMode.EDGES,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.BOX
        init {
            requireFinite("box", width, height, depth)
            require(width in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(height in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(depth in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
        }
        companion object {
            fun clamped(width: Double, height: Double, depth: Double, mode: VfxEditor2BoxMode): Box = Box(
                clampFinite(width, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "box width"),
                clampFinite(height, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "box height"),
                clampFinite(depth, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "box depth"),
                mode,
            )
        }
    }

    data class Torus(
        val majorRadius: Double = 2.0,
        val tubeRadius: Double = 0.35,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.TORUS
        init {
            requireFinite("torus", majorRadius, tubeRadius)
            require(majorRadius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(tubeRadius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(tubeRadius <= majorRadius)
        }
        companion object {
            fun clamped(majorRadius: Double, tubeRadius: Double): Torus {
                val safeMajor = clampFinite(majorRadius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "torus major radius")
                return Torus(safeMajor, clampFinite(tubeRadius, 0.05, safeMajor, "torus tube radius"))
            }
        }
    }

    data class Star(
        val points: Int = 5,
        val radius: Double = 2.0,
        val innerRadius: Double = 0.8,
        val sharpness: Double = 1.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.STAR_FLOWER
        init {
            requireFinite("star", radius, innerRadius, sharpness)
            require(points in 2..VfxEditor2ProtocolLimits.MAX_POINTS)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(innerRadius in 0.0..radius)
            require(sharpness in 0.0..VfxEditor2ProtocolLimits.MAX_SHARPNESS)
        }
        companion object {
            fun clamped(points: Int, radius: Double, innerRadius: Double, sharpness: Double): Star {
                val safeRadius = clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "star radius")
                return Star(
                    points.coerceIn(2, VfxEditor2ProtocolLimits.MAX_POINTS),
                    safeRadius,
                    clampFinite(innerRadius, 0.0, safeRadius, "star inner radius"),
                    clampFinite(sharpness, 0.0, VfxEditor2ProtocolLimits.MAX_SHARPNESS, "star sharpness"),
                )
            }
        }
    }

    data class Cross(
        val size: Double = 2.0,
        val angleDegrees: Double = 45.0,
        val thickness: Double = 0.14,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.CROSS
        init {
            requireFinite("cross", size, angleDegrees, thickness)
            require(size in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(angleDegrees in -VfxEditor2ProtocolLimits.MAX_PHASE..VfxEditor2ProtocolLimits.MAX_PHASE)
            require(thickness in VfxEditor2ProtocolLimits.MIN_THICKNESS..VfxEditor2ProtocolLimits.MAX_THICKNESS)
        }
        companion object {
            fun clamped(size: Double, angleDegrees: Double, thickness: Double): Cross = Cross(
                clampFinite(size, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "cross size"),
                clampFinite(angleDegrees, -VfxEditor2ProtocolLimits.MAX_PHASE, VfxEditor2ProtocolLimits.MAX_PHASE, "cross angle"),
                clampFinite(thickness, 0.0, VfxEditor2ProtocolLimits.MAX_THICKNESS, "cross thickness"),
            )
        }
    }

    data class Shockwave(
        val startRadius: Double = 0.3,
        val endRadius: Double = 3.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.SHOCKWAVE
        init {
            requireFinite("shockwave", startRadius, endRadius)
            require(startRadius in 0.0..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(endRadius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
        }
        companion object {
            fun clamped(startRadius: Double, endRadius: Double): Shockwave = Shockwave(
                clampFinite(startRadius, 0.0, VfxEditor2ProtocolLimits.MAX_RADIUS, "shockwave start radius"),
                clampFinite(endRadius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "shockwave end radius"),
            )
        }
    }

    data class Vortex(
        val radius: Double = 1.3,
        val height: Double = 2.5,
        val turns: Double = 2.0,
        val direction: VfxEditor2Direction = VfxEditor2Direction.UP,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.VORTEX
        init {
            requireFinite("vortex", radius, height, turns)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(height in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(turns in 0.25..VfxEditor2ProtocolLimits.MAX_TURNS)
        }
        companion object {
            fun clamped(radius: Double, height: Double, turns: Double, direction: VfxEditor2Direction): Vortex = Vortex(
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "vortex radius"),
                clampFinite(height, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "vortex height"),
                clampFinite(turns, 0.25, VfxEditor2ProtocolLimits.MAX_TURNS, "vortex turns"),
                direction,
            )
        }
    }

    data class Tornado(
        val bottomRadius: Double = 0.5,
        val topRadius: Double = 2.0,
        val height: Double = 3.5,
        val turns: Double = 2.0,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.TORNADO
        init {
            requireFinite("tornado", bottomRadius, topRadius, height, turns)
            require(bottomRadius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(topRadius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(height in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(turns in 0.25..VfxEditor2ProtocolLimits.MAX_TURNS)
        }
        companion object {
            fun clamped(bottomRadius: Double, topRadius: Double, height: Double, turns: Double): Tornado = Tornado(
                clampFinite(bottomRadius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "tornado bottom radius"),
                clampFinite(topRadius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "tornado top radius"),
                clampFinite(height, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "tornado height"),
                clampFinite(turns, 0.25, VfxEditor2ProtocolLimits.MAX_TURNS, "tornado turns"),
            )
        }
    }

    data class Fountain(
        val radius: Double = 1.2,
        val height: Double = 3.0,
        val spreadDegrees: Double = 25.0,
        val count: Int = 64,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.FOUNTAIN
        init {
            requireFinite("fountain", radius, height, spreadDegrees)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(height in 0.25..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(spreadDegrees in 0.0..VfxEditor2ProtocolLimits.MAX_BURST_SPREAD)
            require(count in 1..VfxEditor2ProtocolLimits.MAX_COUNT)
        }
        companion object {
            fun clamped(radius: Double, height: Double, spreadDegrees: Double, count: Int): Fountain = Fountain(
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "fountain radius"),
                clampFinite(height, 0.25, VfxEditor2ProtocolLimits.MAX_LENGTH, "fountain height"),
                clampFinite(spreadDegrees, 0.0, VfxEditor2ProtocolLimits.MAX_BURST_SPREAD, "fountain spread"),
                count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_COUNT),
            )
        }
    }

    data class SphereBurst(
        val spawnRadius: Double = 0.3,
        val count: Int = 32,
        val speed: Double = 0.6,
        val variance: Double = 0.15,
        val seed: Long = 42L,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.SPHERE_BURST
        init {
            requireFinite("sphere burst", spawnRadius, speed, variance)
            require(spawnRadius in 0.0..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(count in 1..VfxEditor2ProtocolLimits.MAX_COUNT)
            require(speed in 0.0..VfxEditor2ProtocolLimits.MAX_SPEED)
            require(variance in 0.0..VfxEditor2ProtocolLimits.MAX_SPEED)
        }
        companion object {
            fun clamped(spawnRadius: Double, count: Int, speed: Double, variance: Double, seed: Long): SphereBurst = SphereBurst(
                clampFinite(spawnRadius, 0.0, VfxEditor2ProtocolLimits.MAX_RADIUS, "sphere burst spawn radius"),
                count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_COUNT),
                clampFinite(speed, 0.0, VfxEditor2ProtocolLimits.MAX_SPEED, "sphere burst speed"),
                clampFinite(variance, 0.0, VfxEditor2ProtocolLimits.MAX_SPEED, "sphere burst variance"),
                seed,
            )
        }
    }

    data class ConeBurst(
        val length: Double = 2.5,
        val radius: Double = 1.0,
        val angleDegrees: Double = 25.0,
        val count: Int = 32,
        val speed: Double = 0.6,
        val seed: Long = 42L,
    ) : VfxEditor2Shape {
        override val type get() = VfxEditor2EffectType.CONE_BURST
        init {
            requireFinite("cone burst", length, radius, angleDegrees, speed)
            require(length in VfxEditor2ProtocolLimits.MIN_LENGTH..VfxEditor2ProtocolLimits.MAX_LENGTH)
            require(radius in 0.05..VfxEditor2ProtocolLimits.MAX_RADIUS)
            require(angleDegrees in 1.0..VfxEditor2ProtocolLimits.MAX_BURST_SPREAD)
            require(count in 1..VfxEditor2ProtocolLimits.MAX_COUNT)
            require(speed in 0.0..VfxEditor2ProtocolLimits.MAX_SPEED)
        }
        companion object {
            fun clamped(length: Double, radius: Double, angleDegrees: Double, count: Int, speed: Double, seed: Long): ConeBurst = ConeBurst(
                clampFinite(length, VfxEditor2ProtocolLimits.MIN_LENGTH, VfxEditor2ProtocolLimits.MAX_LENGTH, "cone burst length"),
                clampFinite(radius, 0.05, VfxEditor2ProtocolLimits.MAX_RADIUS, "cone burst radius"),
                clampFinite(angleDegrees, 1.0, VfxEditor2ProtocolLimits.MAX_BURST_SPREAD, "cone burst angle"),
                count.coerceIn(1, VfxEditor2ProtocolLimits.MAX_COUNT),
                clampFinite(speed, 0.0, VfxEditor2ProtocolLimits.MAX_SPEED, "cone burst speed"),
                seed,
            )
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
    val startTick: Int = 0,
    val durationTicks: Int = VFX_EDITOR_2_DEFAULT_EFFECT_DURATION_TICKS,
) {
    init {
        require(id >= 0L) { "VFX Editor 2 effect id is invalid" }
        require(name.isNotBlank() && name.length <= VfxEditor2ProtocolLimits.MAX_EFFECT_NAME_LENGTH) {
            "VFX Editor 2 effect name is invalid"
        }
        require(shape.type == type) { "VFX Editor 2 effect type and shape do not match" }
        require(startTick in 0..VFX_EDITOR_2_MAX_EFFECT_START_TICKS) {
            "VFX Editor 2 effect start tick is out of range"
        }
        require(durationTicks in 1..VFX_EDITOR_2_MAX_EFFECT_DURATION_TICKS) {
            "VFX Editor 2 effect duration is out of range"
        }
        require(!isVfxEditor2Instant(type) || durationTicks == 1) {
            "VFX Editor 2 instant effects must have a one-tick duration"
        }
    }

    val endTick: Int get() = startTick + durationTicks

    val animationDurationTicks: Int get() = if (isVfxEditor2Instant(type)) 1 else durationTicks
}

data class VfxEditor2Composition(
    val effects: List<VfxEditor2Effect>,
    val name: String = "untitled",
    val timelineLengthTicks: Int = VFX_EDITOR_2_DEFAULT_TIMELINE_LENGTH_TICKS,
) {
    init {
        require(effects.size <= VfxEditor2ProtocolLimits.MAX_EFFECTS) {
            "VFX Editor 2 has too many effects"
        }
        require(effects.map { it.id }.toSet().size == effects.size) {
            "VFX Editor 2 effect ids must be unique"
        }
        require(isSafeVfxEditor2CompositionName(name)) {
            "VFX Editor 2 composition name is invalid"
        }
        require(timelineLengthTicks in 1..VFX_EDITOR_2_MAX_TIMELINE_LENGTH_TICKS) {
            "VFX Editor 2 timeline length is out of range"
        }
        require(effects.all { it.endTick <= VFX_EDITOR_2_MAX_TIMELINE_LENGTH_TICKS }) {
            "VFX Editor 2 effect exceeds the maximum timeline length"
        }
        require(timelineLengthTicks >= effects.maxOfOrNull { it.endTick } ?: 0) {
            "VFX Editor 2 timeline length is shorter than an effect"
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

    fun maxEndTick(): Int = effects.maxOfOrNull { it.endTick } ?: 0

    fun withoutSolo(): VfxEditor2Composition = copy(effects = effects.map { it.copy(solo = false) })

    fun normalizedForStorage(): VfxEditor2Composition = clamped(
        name = name,
        timelineLengthTicks = timelineLengthTicks,
        effects = effects.map { it.copy(solo = false) },
    )

    fun estimatedSampleCount(): Int = visibleEffects().sumOf { estimateVfxEditor2Samples(it.shape, it.appearance.density) }
        .coerceAtMost(VFX_EDITOR_2_MAX_TOTAL_SAMPLES)

    companion object {
        fun clamped(
            name: String,
            timelineLengthTicks: Int,
            effects: List<VfxEditor2Effect>,
        ): VfxEditor2Composition {
            require(isSafeVfxEditor2CompositionName(name)) {
                "VFX Editor 2 composition name is invalid"
            }
            val boundedEffects = effects.take(VfxEditor2ProtocolLimits.MAX_EFFECTS).map { effect ->
                val start = effect.startTick.coerceIn(0, VFX_EDITOR_2_MAX_EFFECT_START_TICKS)
                val maxDuration = (VFX_EDITOR_2_MAX_TIMELINE_LENGTH_TICKS - start).coerceAtLeast(1)
                val duration = if (isVfxEditor2Instant(effect.type)) {
                    1
                } else {
                    effect.durationTicks.coerceIn(1, maxDuration)
                }
                effect.copy(startTick = start, durationTicks = duration)
            }
            val maximumEnd = boundedEffects.maxOfOrNull { it.endTick } ?: 1
            val boundedTimeline = timelineLengthTicks
                .coerceIn(1, VFX_EDITOR_2_MAX_TIMELINE_LENGTH_TICKS)
                .coerceAtLeast(maximumEnd)
                .coerceAtMost(VFX_EDITOR_2_MAX_TIMELINE_LENGTH_TICKS)
            return VfxEditor2Composition(boundedEffects, name, boundedTimeline)
        }
    }
}

fun defaultVfxEditor2Shape(type: VfxEditor2EffectType): VfxEditor2Shape = when (type) {
    VfxEditor2EffectType.ARC_SLASH -> VfxEditor2Shape.ArcSlash()
    VfxEditor2EffectType.STRAIGHT_SLASH -> VfxEditor2Shape.StraightSlash()
    VfxEditor2EffectType.RING -> VfxEditor2Shape.Ring()
    VfxEditor2EffectType.BURST -> VfxEditor2Shape.Burst()
    VfxEditor2EffectType.BEZIER -> VfxEditor2Shape.Bezier()
    VfxEditor2EffectType.WAVE -> VfxEditor2Shape.Wave()
    VfxEditor2EffectType.LIGHTNING -> VfxEditor2Shape.Lightning()
    VfxEditor2EffectType.SPIRAL -> VfxEditor2Shape.Spiral()
    VfxEditor2EffectType.HELIX -> VfxEditor2Shape.Helix()
    VfxEditor2EffectType.DISK -> VfxEditor2Shape.Disk()
    VfxEditor2EffectType.SECTOR -> VfxEditor2Shape.Sector()
    VfxEditor2EffectType.GRID -> VfxEditor2Shape.Grid()
    VfxEditor2EffectType.SPHERE -> VfxEditor2Shape.Sphere()
    VfxEditor2EffectType.ORB -> VfxEditor2Shape.Orb()
    VfxEditor2EffectType.DOME -> VfxEditor2Shape.Dome()
    VfxEditor2EffectType.CYLINDER -> VfxEditor2Shape.Cylinder()
    VfxEditor2EffectType.CONE -> VfxEditor2Shape.Cone()
    VfxEditor2EffectType.BOX -> VfxEditor2Shape.Box()
    VfxEditor2EffectType.TORUS -> VfxEditor2Shape.Torus()
    VfxEditor2EffectType.STAR_FLOWER -> VfxEditor2Shape.Star()
    VfxEditor2EffectType.CROSS -> VfxEditor2Shape.Cross()
    VfxEditor2EffectType.SHOCKWAVE -> VfxEditor2Shape.Shockwave()
    VfxEditor2EffectType.VORTEX -> VfxEditor2Shape.Vortex()
    VfxEditor2EffectType.TORNADO -> VfxEditor2Shape.Tornado()
    VfxEditor2EffectType.FOUNTAIN -> VfxEditor2Shape.Fountain()
    VfxEditor2EffectType.SPHERE_BURST -> VfxEditor2Shape.SphereBurst()
    VfxEditor2EffectType.CONE_BURST -> VfxEditor2Shape.ConeBurst()
}

fun defaultVfxEditor2Effect(type: VfxEditor2EffectType, id: Long): VfxEditor2Effect = VfxEditor2Effect(
    id = id,
    name = vfxEditor2DisplayName(type),
    type = type,
    shape = defaultVfxEditor2Shape(type),
    durationTicks = if (isVfxEditor2Instant(type)) 1 else VFX_EDITOR_2_DEFAULT_EFFECT_DURATION_TICKS,
)

fun vfxEditor2DisplayName(type: VfxEditor2EffectType): String = when (type) {
    VfxEditor2EffectType.ARC_SLASH -> "Arc Slash"
    VfxEditor2EffectType.STRAIGHT_SLASH -> "Straight Slash"
    VfxEditor2EffectType.BEZIER -> "Bezier Curve"
    VfxEditor2EffectType.WAVE -> "Wave"
    VfxEditor2EffectType.LIGHTNING -> "Lightning"
    VfxEditor2EffectType.SPIRAL -> "Spiral"
    VfxEditor2EffectType.HELIX -> "Helix"
    VfxEditor2EffectType.RING -> "Ring"
    VfxEditor2EffectType.DISK -> "Disk"
    VfxEditor2EffectType.SECTOR -> "Sector / Fan"
    VfxEditor2EffectType.GRID -> "Grid / Plane"
    VfxEditor2EffectType.SPHERE -> "Sphere"
    VfxEditor2EffectType.ORB -> "Orb / Filled Sphere"
    VfxEditor2EffectType.DOME -> "Hemisphere / Dome"
    VfxEditor2EffectType.CYLINDER -> "Cylinder / Pillar"
    VfxEditor2EffectType.CONE -> "Cone"
    VfxEditor2EffectType.BOX -> "Box / Cuboid"
    VfxEditor2EffectType.TORUS -> "Torus / Donut"
    VfxEditor2EffectType.STAR_FLOWER -> "Star / Flower"
    VfxEditor2EffectType.CROSS -> "Cross / X"
    VfxEditor2EffectType.SHOCKWAVE -> "Shockwave"
    VfxEditor2EffectType.VORTEX -> "Vortex"
    VfxEditor2EffectType.TORNADO -> "Tornado"
    VfxEditor2EffectType.FOUNTAIN -> "Fountain"
    VfxEditor2EffectType.BURST -> "Burst"
    VfxEditor2EffectType.SPHERE_BURST -> "Sphere Burst"
    VfxEditor2EffectType.CONE_BURST -> "Cone Burst"
}

fun estimateVfxEditor2Samples(shape: VfxEditor2Shape, density: Double = 1.0): Int {
    val safeDensity = density.coerceIn(0.25, 4.0)
    val estimate = when (shape) {
        is VfxEditor2Shape.ArcSlash -> shape.length * 18.0
        is VfxEditor2Shape.StraightSlash -> shape.length * 10.0
        is VfxEditor2Shape.Bezier -> shape.length * 16.0
        is VfxEditor2Shape.Wave -> shape.length * 16.0
        is VfxEditor2Shape.Lightning -> (shape.hops + 1) * 4.0
        is VfxEditor2Shape.Spiral -> shape.length * shape.turns * 12.0
        is VfxEditor2Shape.Helix -> shape.length * shape.turns * 12.0
        is VfxEditor2Shape.Ring -> shape.radius * 2.0 * PI * shape.arcDegrees / 360.0 * 8.0
        is VfxEditor2Shape.Disk -> shape.radius * shape.radius * 12.0
        is VfxEditor2Shape.Sector -> shape.radius * shape.radius * shape.angleDegrees / 360.0 * 12.0
        is VfxEditor2Shape.Grid -> (shape.rows + 1.0) * (shape.rows + 1.0)
        is VfxEditor2Shape.Sphere -> shape.radius * shape.radius * 20.0
        is VfxEditor2Shape.Orb -> shape.count.toDouble()
        is VfxEditor2Shape.Dome -> shape.radius * shape.radius * 12.0
        is VfxEditor2Shape.Cylinder -> shape.count.toDouble()
        is VfxEditor2Shape.Cone -> shape.length * shape.radius * 16.0
        is VfxEditor2Shape.Box -> shape.width * shape.height * shape.depth * 3.0
        is VfxEditor2Shape.Torus -> shape.majorRadius * shape.tubeRadius * 64.0
        is VfxEditor2Shape.Star -> shape.points * 16.0
        is VfxEditor2Shape.Cross -> shape.size * 20.0
        is VfxEditor2Shape.Shockwave -> shape.endRadius * 2.0 * PI * 8.0
        is VfxEditor2Shape.Vortex -> shape.radius * shape.height * shape.turns * 12.0
        is VfxEditor2Shape.Tornado -> (shape.bottomRadius + shape.topRadius) * shape.height * shape.turns * 8.0
        is VfxEditor2Shape.Fountain -> shape.count.toDouble()
        is VfxEditor2Shape.Burst -> shape.count.toDouble()
        is VfxEditor2Shape.SphereBurst -> shape.count.toDouble()
        is VfxEditor2Shape.ConeBurst -> shape.count.toDouble()
    }
    return (estimate * safeDensity).roundToInt().coerceIn(1, VFX_EDITOR_2_MAX_SAMPLES_PER_EFFECT)
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

data class VfxEditor2SaveRequest(
    val composition: VfxEditor2Composition,
) : ProtocolMessage

data class VfxEditor2SaveResult(
    val name: String,
    val success: Boolean,
    val overwritten: Boolean,
    val message: String,
) : ProtocolMessage {
    init {
        require(isSafeVfxEditor2CompositionName(name)) { "VFX Editor 2 save result name is invalid" }
        require(message.length <= VfxEditor2ProtocolLimits.MAX_STATUS_LENGTH) {
            "VFX Editor 2 save result is too long"
        }
    }
}

object VfxEditor2ListRequest : ProtocolMessage

data class VfxEditor2ListResponse(
    val names: List<String>,
) : ProtocolMessage {
    init {
        require(names.size <= VFX_EDITOR_2_MAX_SAVED_COMPOSITIONS) {
            "VFX Editor 2 saved composition list is too large"
        }
        require(names.all(::isSafeVfxEditor2CompositionName)) {
            "VFX Editor 2 saved composition name is invalid"
        }
    }
}

data class VfxEditor2LoadRequest(
    val name: String,
) : ProtocolMessage {
    init {
        require(isSafeVfxEditor2CompositionName(name)) { "VFX Editor 2 load name is invalid" }
    }
}

data class VfxEditor2LoadResponse(
    val name: String,
    val composition: VfxEditor2Composition?,
    val message: String,
) : ProtocolMessage {
    init {
        require(isSafeVfxEditor2CompositionName(name)) { "VFX Editor 2 load response name is invalid" }
        require(message.length <= VfxEditor2ProtocolLimits.MAX_STATUS_LENGTH) {
            "VFX Editor 2 load response is too long"
        }
        require(composition == null || composition.name == name) {
            "VFX Editor 2 load response name does not match composition"
        }
    }
}

object ProtocolCodec {
    private const val MAX_PACKET_SIZE = 8192
    private const val MAX_STRING_BYTES = 768
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
    private const val VFX_EDITOR_2_SAVE_REQUEST = 44
    private const val VFX_EDITOR_2_SAVE_RESULT = 45
    private const val VFX_EDITOR_2_LIST_REQUEST = 46
    private const val VFX_EDITOR_2_LIST_RESPONSE = 47
    private const val VFX_EDITOR_2_LOAD_REQUEST = 48
    private const val VFX_EDITOR_2_LOAD_RESPONSE = 49

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
                is VfxEditor2SaveRequest -> {
                    data.writeByte(VFX_EDITOR_2_SAVE_REQUEST)
                    writeVfxEditor2Composition(data, message.composition)
                }
                is VfxEditor2SaveResult -> {
                    data.writeByte(VFX_EDITOR_2_SAVE_RESULT)
                    writeString(data, message.name)
                    data.writeBoolean(message.success)
                    data.writeBoolean(message.overwritten)
                    writeString(data, message.message)
                }
                VfxEditor2ListRequest -> data.writeByte(VFX_EDITOR_2_LIST_REQUEST)
                is VfxEditor2ListResponse -> {
                    data.writeByte(VFX_EDITOR_2_LIST_RESPONSE)
                    data.writeByte(message.names.size)
                    message.names.forEach { writeString(data, it) }
                }
                is VfxEditor2LoadRequest -> {
                    data.writeByte(VFX_EDITOR_2_LOAD_REQUEST)
                    writeString(data, message.name)
                }
                is VfxEditor2LoadResponse -> {
                    data.writeByte(VFX_EDITOR_2_LOAD_RESPONSE)
                    writeString(data, message.name)
                    data.writeBoolean(message.composition != null)
                    message.composition?.let { writeVfxEditor2Composition(data, it) }
                    writeString(data, message.message)
                }
            }
        }
        return output.toByteArray().also {
            require(it.size <= MAX_PACKET_SIZE) { "ProjectS protocol packet exceeds $MAX_PACKET_SIZE bytes" }
        }
    }

    fun decode(bytes: ByteArray): ProtocolMessage {
        val packetId = bytes.firstOrNull()?.toInt()?.and(0xff)
        return try {
            require(bytes.isNotEmpty()) { "ProjectS protocol packet is empty" }
            require(bytes.size <= MAX_PACKET_SIZE) { "ProjectS protocol packet exceeds $MAX_PACKET_SIZE bytes" }
            decodePacket(bytes)
        } catch (error: ProtocolDecodeException) {
            throw error
        } catch (error: IOException) {
            throw malformed(packetId, error)
        } catch (error: IllegalArgumentException) {
            throw malformed(packetId, error)
        }
    }

    private fun malformed(packetId: Int?, error: Throwable): ProtocolDecodeException = ProtocolDecodeException(
        packetId = packetId,
        packetName = packetName(packetId),
        reason = conciseReason(error),
        cause = error,
    )

    private fun conciseReason(error: Throwable): String = when (error) {
        is java.io.EOFException -> "unexpected end of packet"
        else -> (error.message ?: error.javaClass.simpleName)
            .replace(Regex("\\s+"), " ")
            .take(160)
    }

    private fun packetName(packetId: Int?): String = when (packetId) {
        HELLO -> "ProtocolHello"
        HELLO_ACK -> "ProtocolHelloAck"
        ATTACK_INPUT -> "AttackInput"
        ATTACK_STARTED -> "AttackStarted"
        ATTACK_HIT_CONFIRMED -> "AttackHitConfirmed"
        DODGE_INPUT -> "DodgeInput"
        AIR_JUMP_INPUT -> "AirJumpInput"
        CLASS_SKILL_INPUT -> "ClassSkillInput"
        CLASS_RESOURCE_SNAPSHOT -> "ClassResourceSnapshot"
        ATTACK_DEBUG_SHAPE -> "AttackDebugShape"
        GROUND_TELEGRAPH_START -> "GroundTelegraphStart"
        GROUND_TELEGRAPH_REMOVE -> "GroundTelegraphRemove"
        VFX_EDITOR_OPEN -> "VfxEditorOpen"
        VFX_SLASH_PREVIEW_REQUEST -> "VfxSlashPreviewRequest"
        VFX_SLASH_SAVE_REQUEST -> "VfxSlashSaveRequest"
        VFX_SLASH_DRAFT_LIST -> "VfxSlashDraftList"
        VFX_SLASH_DRAFT_LOAD_REQUEST -> "VfxSlashDraftLoadRequest"
        VFX_SLASH_DRAFT -> "VfxSlashDraft"
        VFX_EDITOR_NOTICE -> "VfxEditorNotice"
        VFX_SLASH_PREVIEW_CANCEL -> "VfxSlashPreviewCancel"
        VFX_SLASH_APPLY_SKILL3 -> "VfxSlashApplySkill3"
        STARWEAVER_HUD_SNAPSHOT -> "StarweaverHudSnapshot"
        RONIN_HUD_SNAPSHOT -> "RoninHudSnapshot"
        VFX_EDITOR_2_OPEN -> "VfxEditor2Open"
        VFX_EDITOR_2_PREVIEW_START -> "VfxEditor2PreviewStart"
        VFX_EDITOR_2_PREVIEW_STOP -> "VfxEditor2PreviewStop"
        VFX_EDITOR_2_STATUS -> "VfxEditor2Status"
        VFX_EDITOR_2_SAVE_REQUEST -> "VfxEditor2SaveRequest"
        VFX_EDITOR_2_SAVE_RESULT -> "VfxEditor2SaveResult"
        VFX_EDITOR_2_LIST_REQUEST -> "VfxEditor2ListRequest"
        VFX_EDITOR_2_LIST_RESPONSE -> "VfxEditor2ListResponse"
        VFX_EDITOR_2_LOAD_REQUEST -> "VfxEditor2LoadRequest"
        VFX_EDITOR_2_LOAD_RESPONSE -> "VfxEditor2LoadResponse"
        null -> "none"
        else -> "unknown"
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
            VFX_EDITOR_2_SAVE_REQUEST -> VfxEditor2SaveRequest(readVfxEditor2Composition(input))
            VFX_EDITOR_2_SAVE_RESULT -> VfxEditor2SaveResult(
                name = readString(input),
                success = input.readBoolean(),
                overwritten = input.readBoolean(),
                message = readString(input),
            )
            VFX_EDITOR_2_LIST_REQUEST -> VfxEditor2ListRequest
            VFX_EDITOR_2_LIST_RESPONSE -> {
                val count = input.readUnsignedByte()
                require(count <= VFX_EDITOR_2_MAX_SAVED_COMPOSITIONS) {
                    "VFX Editor 2 saved composition list is too large"
                }
                VfxEditor2ListResponse(List(count) { readString(input) })
            }
            VFX_EDITOR_2_LOAD_REQUEST -> VfxEditor2LoadRequest(readString(input))
            VFX_EDITOR_2_LOAD_RESPONSE -> {
                val name = readString(input)
                val hasComposition = input.readBoolean()
                val composition = if (hasComposition) readVfxEditor2Composition(input) else null
                VfxEditor2LoadResponse(name, composition, readString(input))
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
        writeString(data, composition.name)
        data.writeInt(composition.timelineLengthTicks)
        data.writeByte(composition.effects.size)
        composition.effects.forEach { effect ->
            data.writeLong(effect.id)
            writeString(data, effect.name)
            data.writeByte(effect.type.ordinal)
            data.writeBoolean(effect.enabled)
            data.writeBoolean(effect.solo)
            data.writeInt(effect.startTick)
            data.writeInt(effect.durationTicks)
            data.writeDouble(effect.transform.forward)
            data.writeDouble(effect.transform.side)
            data.writeDouble(effect.transform.height)
            data.writeDouble(effect.transform.yaw)
            data.writeDouble(effect.transform.pitch)
            data.writeDouble(effect.transform.roll)
            data.writeInt(effect.appearance.color)
            data.writeDouble(effect.appearance.particleSize)
            data.writeDouble(effect.appearance.density)
            data.writeByte(effect.appearance.particleType.ordinal)
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
                    data.writeLong(shape.seed)
                }
                is VfxEditor2Shape.Bezier -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.controlForward)
                    data.writeDouble(shape.controlSide)
                    data.writeDouble(shape.controlHeight)
                    data.writeDouble(shape.endSide)
                    data.writeDouble(shape.endHeight)
                    data.writeDouble(shape.thickness)
                }
                is VfxEditor2Shape.Wave -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.amplitude)
                    data.writeInt(shape.waves)
                    data.writeDouble(shape.phaseDegrees)
                    data.writeDouble(shape.thickness)
                }
                is VfxEditor2Shape.Lightning -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.jitter)
                    data.writeInt(shape.hops)
                    data.writeLong(shape.seed)
                }
                is VfxEditor2Shape.Spiral -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.turns)
                    data.writeDouble(shape.angleOffsetDegrees)
                    data.writeBoolean(shape.reverse)
                }
                is VfxEditor2Shape.Helix -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.turns)
                    data.writeDouble(shape.phaseDegrees)
                }
                is VfxEditor2Shape.Disk -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.innerRadius)
                }
                is VfxEditor2Shape.Sector -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.angleDegrees)
                    data.writeDouble(shape.innerRadius)
                }
                is VfxEditor2Shape.Grid -> {
                    data.writeDouble(shape.width)
                    data.writeDouble(shape.height)
                    data.writeInt(shape.rows)
                }
                is VfxEditor2Shape.Sphere -> data.writeDouble(shape.radius)
                is VfxEditor2Shape.Orb -> {
                    data.writeDouble(shape.radius)
                    data.writeInt(shape.count)
                    data.writeLong(shape.seed)
                }
                is VfxEditor2Shape.Dome -> {
                    data.writeDouble(shape.radius)
                    data.writeByte(shape.direction.ordinal)
                }
                is VfxEditor2Shape.Cylinder -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.height)
                    data.writeInt(shape.count)
                    data.writeBoolean(shape.shell)
                }
                is VfxEditor2Shape.Cone -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.angleDegrees)
                }
                is VfxEditor2Shape.Box -> {
                    data.writeDouble(shape.width)
                    data.writeDouble(shape.height)
                    data.writeDouble(shape.depth)
                    data.writeByte(shape.mode.ordinal)
                }
                is VfxEditor2Shape.Torus -> {
                    data.writeDouble(shape.majorRadius)
                    data.writeDouble(shape.tubeRadius)
                }
                is VfxEditor2Shape.Star -> {
                    data.writeInt(shape.points)
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.innerRadius)
                    data.writeDouble(shape.sharpness)
                }
                is VfxEditor2Shape.Cross -> {
                    data.writeDouble(shape.size)
                    data.writeDouble(shape.angleDegrees)
                    data.writeDouble(shape.thickness)
                }
                is VfxEditor2Shape.Shockwave -> {
                    data.writeDouble(shape.startRadius)
                    data.writeDouble(shape.endRadius)
                }
                is VfxEditor2Shape.Vortex -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.height)
                    data.writeDouble(shape.turns)
                    data.writeByte(shape.direction.ordinal)
                }
                is VfxEditor2Shape.Tornado -> {
                    data.writeDouble(shape.bottomRadius)
                    data.writeDouble(shape.topRadius)
                    data.writeDouble(shape.height)
                    data.writeDouble(shape.turns)
                }
                is VfxEditor2Shape.Fountain -> {
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.height)
                    data.writeDouble(shape.spreadDegrees)
                    data.writeInt(shape.count)
                }
                is VfxEditor2Shape.SphereBurst -> {
                    data.writeDouble(shape.spawnRadius)
                    data.writeInt(shape.count)
                    data.writeDouble(shape.speed)
                    data.writeDouble(shape.variance)
                    data.writeLong(shape.seed)
                }
                is VfxEditor2Shape.ConeBurst -> {
                    data.writeDouble(shape.length)
                    data.writeDouble(shape.radius)
                    data.writeDouble(shape.angleDegrees)
                    data.writeInt(shape.count)
                    data.writeDouble(shape.speed)
                    data.writeLong(shape.seed)
                }
            }
        }
    }

    private fun readVfxEditor2Composition(input: DataInputStream): VfxEditor2Composition {
        val name = readString(input)
        require(isSafeVfxEditor2CompositionName(name)) {
            "VFX Editor 2 composition name is invalid"
        }
        val timelineLengthTicks = input.readInt()
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
            val startTick = input.readInt()
            val durationTicks = input.readInt()
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
                particleType = VfxEditor2ParticleType.entries.getOrNull(input.readUnsignedByte())
                    ?: throw IllegalArgumentException("Unknown VFX Editor 2 particle type"),
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
                    seed = input.readLong(),
                )
                VfxEditor2EffectType.BEZIER -> VfxEditor2Shape.Bezier.clamped(
                    length = input.readDouble(),
                    controlForward = input.readDouble(),
                    controlSide = input.readDouble(),
                    controlHeight = input.readDouble(),
                    endSide = input.readDouble(),
                    endHeight = input.readDouble(),
                    thickness = input.readDouble(),
                )
                VfxEditor2EffectType.WAVE -> VfxEditor2Shape.Wave.clamped(
                    length = input.readDouble(),
                    amplitude = input.readDouble(),
                    waves = input.readInt(),
                    phaseDegrees = input.readDouble(),
                    thickness = input.readDouble(),
                )
                VfxEditor2EffectType.LIGHTNING -> VfxEditor2Shape.Lightning.clamped(
                    length = input.readDouble(),
                    jitter = input.readDouble(),
                    hops = input.readInt(),
                    seed = input.readLong(),
                )
                VfxEditor2EffectType.SPIRAL -> VfxEditor2Shape.Spiral.clamped(
                    radius = input.readDouble(),
                    length = input.readDouble(),
                    turns = input.readDouble(),
                    angleOffsetDegrees = input.readDouble(),
                    reverse = input.readBoolean(),
                )
                VfxEditor2EffectType.HELIX -> VfxEditor2Shape.Helix.clamped(
                    radius = input.readDouble(),
                    length = input.readDouble(),
                    turns = input.readDouble(),
                    phaseDegrees = input.readDouble(),
                )
                VfxEditor2EffectType.DISK -> VfxEditor2Shape.Disk.clamped(
                    radius = input.readDouble(),
                    innerRadius = input.readDouble(),
                )
                VfxEditor2EffectType.SECTOR -> VfxEditor2Shape.Sector.clamped(
                    radius = input.readDouble(),
                    angleDegrees = input.readDouble(),
                    innerRadius = input.readDouble(),
                )
                VfxEditor2EffectType.GRID -> VfxEditor2Shape.Grid.clamped(
                    width = input.readDouble(),
                    height = input.readDouble(),
                    rows = input.readInt(),
                )
                VfxEditor2EffectType.SPHERE -> VfxEditor2Shape.Sphere.clamped(input.readDouble())
                VfxEditor2EffectType.ORB -> VfxEditor2Shape.Orb.clamped(
                    radius = input.readDouble(),
                    count = input.readInt(),
                    seed = input.readLong(),
                )
                VfxEditor2EffectType.DOME -> VfxEditor2Shape.Dome.clamped(
                    radius = input.readDouble(),
                    direction = VfxEditor2Direction.entries.getOrNull(input.readUnsignedByte())
                        ?: throw IllegalArgumentException("Unknown VFX Editor 2 dome direction"),
                )
                VfxEditor2EffectType.CYLINDER -> VfxEditor2Shape.Cylinder.clamped(
                    radius = input.readDouble(),
                    height = input.readDouble(),
                    count = input.readInt(),
                    shell = input.readBoolean(),
                )
                VfxEditor2EffectType.CONE -> VfxEditor2Shape.Cone.clamped(
                    length = input.readDouble(),
                    radius = input.readDouble(),
                    angleDegrees = input.readDouble(),
                )
                VfxEditor2EffectType.BOX -> VfxEditor2Shape.Box.clamped(
                    width = input.readDouble(),
                    height = input.readDouble(),
                    depth = input.readDouble(),
                    mode = VfxEditor2BoxMode.entries.getOrNull(input.readUnsignedByte())
                        ?: throw IllegalArgumentException("Unknown VFX Editor 2 box mode"),
                )
                VfxEditor2EffectType.TORUS -> VfxEditor2Shape.Torus.clamped(
                    majorRadius = input.readDouble(),
                    tubeRadius = input.readDouble(),
                )
                VfxEditor2EffectType.STAR_FLOWER -> VfxEditor2Shape.Star.clamped(
                    points = input.readInt(),
                    radius = input.readDouble(),
                    innerRadius = input.readDouble(),
                    sharpness = input.readDouble(),
                )
                VfxEditor2EffectType.CROSS -> VfxEditor2Shape.Cross.clamped(
                    size = input.readDouble(),
                    angleDegrees = input.readDouble(),
                    thickness = input.readDouble(),
                )
                VfxEditor2EffectType.SHOCKWAVE -> VfxEditor2Shape.Shockwave.clamped(
                    startRadius = input.readDouble(),
                    endRadius = input.readDouble(),
                )
                VfxEditor2EffectType.VORTEX -> VfxEditor2Shape.Vortex.clamped(
                    radius = input.readDouble(),
                    height = input.readDouble(),
                    turns = input.readDouble(),
                    direction = VfxEditor2Direction.entries.getOrNull(input.readUnsignedByte())
                        ?: throw IllegalArgumentException("Unknown VFX Editor 2 vortex direction"),
                )
                VfxEditor2EffectType.TORNADO -> VfxEditor2Shape.Tornado.clamped(
                    bottomRadius = input.readDouble(),
                    topRadius = input.readDouble(),
                    height = input.readDouble(),
                    turns = input.readDouble(),
                )
                VfxEditor2EffectType.FOUNTAIN -> VfxEditor2Shape.Fountain.clamped(
                    radius = input.readDouble(),
                    height = input.readDouble(),
                    spreadDegrees = input.readDouble(),
                    count = input.readInt(),
                )
                VfxEditor2EffectType.SPHERE_BURST -> VfxEditor2Shape.SphereBurst.clamped(
                    spawnRadius = input.readDouble(),
                    count = input.readInt(),
                    speed = input.readDouble(),
                    variance = input.readDouble(),
                    seed = input.readLong(),
                )
                VfxEditor2EffectType.CONE_BURST -> VfxEditor2Shape.ConeBurst.clamped(
                    length = input.readDouble(),
                    radius = input.readDouble(),
                    angleDegrees = input.readDouble(),
                    count = input.readInt(),
                    speed = input.readDouble(),
                    seed = input.readLong(),
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
                startTick = startTick.coerceIn(0, VFX_EDITOR_2_MAX_EFFECT_START_TICKS),
                durationTicks = durationTicks.coerceIn(1, VFX_EDITOR_2_MAX_EFFECT_DURATION_TICKS),
            )
        }
        return VfxEditor2Composition.clamped(name, timelineLengthTicks, effects)
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
        require(bytes.size <= MAX_STRING_BYTES) { "ProjectS string is too long" }
        data.writeShort(bytes.size)
        data.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readUnsignedShort()
        require(size <= MAX_STRING_BYTES) { "ProjectS string is too long" }
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
    }
}
