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
    const val CURRENT = 14

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

enum class VfxEditor2Shape {
    RIBBON,
    LINE,
    CIRCLE,
    BURST,
}

enum class VfxEditor2Particle {
    DUST,
    END_ROD,
    ELECTRIC_SPARK,
    CRIT,
    ENCHANT,
    SOUL_FIRE_FLAME,
    FLAME,
}

enum class VfxEditor2WidthCurve {
    CONSTANT,
    THIN_THICK_THIN,
}

private object VfxEditor2Limits {
    const val MAX_LAYERS = 16
    const val MAX_DRAFTS = 32
    const val MAX_NAME_LENGTH = 32
    const val MAX_DURATION_TICKS = 200
    const val MAX_START_TICK = 200
    const val MAX_LAYER_DURATION_TICKS = 200
    const val MAX_LENGTH = 16.0
    const val MAX_ARC_SPAN = 360.0
    const val MAX_CURVATURE = 4.0
    const val MAX_WIDTH = 2.0
    const val MAX_SAMPLE_DENSITY = 32.0
    const val MAX_LANE_COUNT = 4
    const val MAX_LANE_SPACING = 3.0
    const val MAX_LINE_SPACING = 3.0
    const val MAX_CIRCLE_RADIUS = 12.0
    const val MAX_CIRCLE_SPACING = 3.0
    const val MAX_BURST_RADIUS = 8.0
    const val MAX_BURST_COUNT = 64
    const val MAX_BURST_SPREAD = 8.0
    const val MAX_BURST_SPEED = 2.0
    const val MAX_OFFSET = 8.0
    const val MAX_ROTATION = 180.0
    const val MAX_SIZE = 2.0
    const val MAX_DENSITY = 32.0
}

private fun requireFiniteVfx2(values: List<Double>, label: String) {
    require(values.all(Double::isFinite)) { "$label must be finite" }
}

private val VFX_EDITOR_2_NAME = Regex("[A-Za-z0-9][A-Za-z0-9 _-]{0,31}")
private val VFX_EDITOR_2_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,31}")

data class VfxEditor2Offset(
    val forward: Double = 0.0,
    val right: Double = 0.0,
    val up: Double = 0.0,
) {
    init {
        requireFiniteVfx2(listOf(forward, right, up), "VFX Editor 2 offset")
        require(listOf(forward, right, up).all { it in -VfxEditor2Limits.MAX_OFFSET..VfxEditor2Limits.MAX_OFFSET }) {
            "VFX Editor 2 offset is out of range"
        }
    }

    companion object {
        fun clamped(forward: Double, right: Double, up: Double): VfxEditor2Offset {
            requireFiniteVfx2(listOf(forward, right, up), "VFX Editor 2 offset")
            return VfxEditor2Offset(
                forward.coerceIn(-VfxEditor2Limits.MAX_OFFSET, VfxEditor2Limits.MAX_OFFSET),
                right.coerceIn(-VfxEditor2Limits.MAX_OFFSET, VfxEditor2Limits.MAX_OFFSET),
                up.coerceIn(-VfxEditor2Limits.MAX_OFFSET, VfxEditor2Limits.MAX_OFFSET),
            )
        }
    }
}

data class VfxEditor2Rotation(
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
) {
    init {
        requireFiniteVfx2(listOf(yaw, pitch, roll), "VFX Editor 2 rotation")
        require(listOf(yaw, pitch, roll).all { it in -VfxEditor2Limits.MAX_ROTATION..VfxEditor2Limits.MAX_ROTATION }) {
            "VFX Editor 2 rotation is out of range"
        }
    }

    companion object {
        fun clamped(yaw: Double, pitch: Double, roll: Double): VfxEditor2Rotation {
            requireFiniteVfx2(listOf(yaw, pitch, roll), "VFX Editor 2 rotation")
            return VfxEditor2Rotation(
                yaw.coerceIn(-VfxEditor2Limits.MAX_ROTATION, VfxEditor2Limits.MAX_ROTATION),
                pitch.coerceIn(-VfxEditor2Limits.MAX_ROTATION, VfxEditor2Limits.MAX_ROTATION),
                roll.coerceIn(-VfxEditor2Limits.MAX_ROTATION, VfxEditor2Limits.MAX_ROTATION),
            )
        }
    }
}

data class VfxEditor2ShapeParameters(
    val length: Double = 4.5,
    val arcSpan: Double = 120.0,
    val curvature: Double = 0.25,
    val width: Double = 0.45,
    val sampleDensity: Double = 12.0,
    val laneCount: Int = 1,
    val laneSpacing: Double = 0.18,
    val reverse: Boolean = false,
    val widthCurve: VfxEditor2WidthCurve = VfxEditor2WidthCurve.CONSTANT,
    val lineLength: Double = 4.0,
    val lineSpacing: Double = 0.22,
    val circleRadius: Double = 2.0,
    val circleArcDegrees: Double = 360.0,
    val circleSpacing: Double = 0.22,
    val burstRadius: Double = 1.0,
    val burstCount: Int = 18,
    val burstSpread: Double = 1.0,
    val burstSpeed: Double = 0.18,
) {
    init {
        requireFiniteVfx2(
            listOf(
                length, arcSpan, curvature, width, sampleDensity, laneSpacing,
                lineLength, lineSpacing, circleRadius, circleArcDegrees, circleSpacing,
                burstRadius, burstSpread, burstSpeed,
            ),
            "VFX Editor 2 shape parameters",
        )
        require(laneCount in 1..VfxEditor2Limits.MAX_LANE_COUNT) { "VFX Editor 2 lane count is out of range" }
        require(burstCount in 0..VfxEditor2Limits.MAX_BURST_COUNT) { "VFX Editor 2 burst count is out of range" }
        require(length in 0.1..VfxEditor2Limits.MAX_LENGTH && arcSpan in 1.0..VfxEditor2Limits.MAX_ARC_SPAN) { "VFX Editor 2 ribbon geometry is out of range" }
        require(curvature in 0.0..VfxEditor2Limits.MAX_CURVATURE && width in 0.0..VfxEditor2Limits.MAX_WIDTH) { "VFX Editor 2 ribbon width is out of range" }
        require(sampleDensity in 1.0..VfxEditor2Limits.MAX_SAMPLE_DENSITY && laneSpacing in 0.0..VfxEditor2Limits.MAX_LANE_SPACING) { "VFX Editor 2 ribbon sampling is out of range" }
        require(lineLength in 0.1..VfxEditor2Limits.MAX_LENGTH && lineSpacing in 0.05..VfxEditor2Limits.MAX_LINE_SPACING) { "VFX Editor 2 line geometry is out of range" }
        require(circleRadius in 0.0..VfxEditor2Limits.MAX_CIRCLE_RADIUS && circleArcDegrees in 1.0..VfxEditor2Limits.MAX_ARC_SPAN && circleSpacing in 0.05..VfxEditor2Limits.MAX_CIRCLE_SPACING) { "VFX Editor 2 circle geometry is out of range" }
        require(burstRadius in 0.0..VfxEditor2Limits.MAX_BURST_RADIUS && burstSpread in 0.0..VfxEditor2Limits.MAX_BURST_SPREAD && burstSpeed in 0.0..VfxEditor2Limits.MAX_BURST_SPEED) { "VFX Editor 2 burst geometry is out of range" }
    }

    companion object {
        fun clamped(
            length: Double,
            arcSpan: Double,
            curvature: Double,
            width: Double,
            sampleDensity: Double,
            laneCount: Int,
            laneSpacing: Double,
            reverse: Boolean,
            widthCurve: VfxEditor2WidthCurve,
            lineLength: Double,
            lineSpacing: Double,
            circleRadius: Double,
            circleArcDegrees: Double,
            circleSpacing: Double,
            burstRadius: Double,
            burstCount: Int,
            burstSpread: Double,
            burstSpeed: Double,
        ): VfxEditor2ShapeParameters {
            requireFiniteVfx2(
                listOf(
                    length, arcSpan, curvature, width, sampleDensity, laneSpacing,
                    lineLength, lineSpacing, circleRadius, circleArcDegrees, circleSpacing,
                    burstRadius, burstSpread, burstSpeed,
                ),
                "VFX Editor 2 shape parameters",
            )
            return VfxEditor2ShapeParameters(
                length.coerceIn(0.1, VfxEditor2Limits.MAX_LENGTH),
                arcSpan.coerceIn(1.0, VfxEditor2Limits.MAX_ARC_SPAN),
                curvature.coerceIn(0.0, VfxEditor2Limits.MAX_CURVATURE),
                width.coerceIn(0.0, VfxEditor2Limits.MAX_WIDTH),
                sampleDensity.coerceIn(1.0, VfxEditor2Limits.MAX_SAMPLE_DENSITY),
                laneCount.coerceIn(1, VfxEditor2Limits.MAX_LANE_COUNT),
                laneSpacing.coerceIn(0.0, VfxEditor2Limits.MAX_LANE_SPACING),
                reverse,
                widthCurve,
                lineLength.coerceIn(0.1, VfxEditor2Limits.MAX_LENGTH),
                lineSpacing.coerceIn(0.05, VfxEditor2Limits.MAX_LINE_SPACING),
                circleRadius.coerceIn(0.0, VfxEditor2Limits.MAX_CIRCLE_RADIUS),
                circleArcDegrees.coerceIn(1.0, VfxEditor2Limits.MAX_ARC_SPAN),
                circleSpacing.coerceIn(0.05, VfxEditor2Limits.MAX_CIRCLE_SPACING),
                burstRadius.coerceIn(0.0, VfxEditor2Limits.MAX_BURST_RADIUS),
                burstCount.coerceIn(0, VfxEditor2Limits.MAX_BURST_COUNT),
                burstSpread.coerceIn(0.0, VfxEditor2Limits.MAX_BURST_SPREAD),
                burstSpeed.coerceIn(0.0, VfxEditor2Limits.MAX_BURST_SPEED),
            )
        }
    }
}

data class VfxEditor2Layer(
    val id: Int = 0,
    val name: String = "Layer",
    val enabled: Boolean = true,
    val solo: Boolean = false,
    val shapeType: VfxEditor2Shape = VfxEditor2Shape.RIBBON,
    val particleType: VfxEditor2Particle = VfxEditor2Particle.DUST,
    val color: Int = 0xffffff,
    val size: Double = 0.28,
    val density: Double = 1.0,
    val offset: VfxEditor2Offset = VfxEditor2Offset(),
    val rotation: VfxEditor2Rotation = VfxEditor2Rotation(),
    val startTick: Int = 0,
    val durationTicks: Int = 8,
    val shapeParameters: VfxEditor2ShapeParameters = VfxEditor2ShapeParameters(),
) {
    init {
        require(name.matches(VFX_EDITOR_2_NAME)) { "VFX Editor 2 layer name is invalid" }
        require(color in 0..0xffffff) { "VFX Editor 2 color is out of range" }
        require(size.isFinite() && density.isFinite()) { "VFX Editor 2 appearance values must be finite" }
        require(size in 0.01..VfxEditor2Limits.MAX_SIZE && density in 0.05..VfxEditor2Limits.MAX_DENSITY) { "VFX Editor 2 appearance values are out of range" }
        require(startTick in 0..VfxEditor2Limits.MAX_START_TICK) { "VFX Editor 2 start tick is out of range" }
        require(durationTicks in 1..VfxEditor2Limits.MAX_LAYER_DURATION_TICKS) { "VFX Editor 2 layer duration is out of range" }
    }

    companion object {
        fun clamped(
            id: Int,
            name: String,
            enabled: Boolean,
            solo: Boolean,
            shapeType: VfxEditor2Shape,
            particleType: VfxEditor2Particle,
            color: Int,
            size: Double,
            density: Double,
            offset: VfxEditor2Offset,
            rotation: VfxEditor2Rotation,
            startTick: Int,
            durationTicks: Int,
            shapeParameters: VfxEditor2ShapeParameters,
        ): VfxEditor2Layer {
            require(name.matches(VFX_EDITOR_2_NAME)) { "VFX Editor 2 layer name is invalid" }
            requireFiniteVfx2(listOf(size, density), "VFX Editor 2 appearance values")
            return VfxEditor2Layer(
                id = id,
                name = name,
                enabled = enabled,
                solo = solo,
                shapeType = shapeType,
                particleType = particleType,
                color = color.coerceIn(0, 0xffffff),
                size = size.coerceIn(0.01, VfxEditor2Limits.MAX_SIZE),
                density = density.coerceIn(0.05, VfxEditor2Limits.MAX_DENSITY),
                offset = offset,
                rotation = rotation,
                startTick = startTick.coerceIn(0, VfxEditor2Limits.MAX_START_TICK),
                durationTicks = durationTicks.coerceIn(1, VfxEditor2Limits.MAX_LAYER_DURATION_TICKS),
                shapeParameters = shapeParameters,
            )
        }
    }
}

data class VfxEditor2Composition(
    val name: String = "ronin_q",
    val durationTicks: Int = 16,
    val layers: List<VfxEditor2Layer> = defaultLayers(),
) {
    init {
        require(name.matches(VFX_EDITOR_2_NAME)) { "VFX Editor 2 composition name is invalid" }
        require(durationTicks in 1..VfxEditor2Limits.MAX_DURATION_TICKS) { "VFX Editor 2 composition duration is out of range" }
        require(layers.size <= VfxEditor2Limits.MAX_LAYERS) { "Too many VFX Editor 2 layers" }
        require(layers.map { it.id }.toSet().size == layers.size) { "VFX Editor 2 layer ids must be unique" }
    }

    companion object {
        fun clamped(name: String, durationTicks: Int, layers: List<VfxEditor2Layer>): VfxEditor2Composition {
            require(name.matches(VFX_EDITOR_2_NAME)) { "VFX Editor 2 composition name is invalid" }
            require(layers.size <= VfxEditor2Limits.MAX_LAYERS) { "Too many VFX Editor 2 layers" }
            val safeDuration = durationTicks.coerceIn(1, VfxEditor2Limits.MAX_DURATION_TICKS)
            val safeLayers = layers.map { layer ->
                val safeStart = layer.startTick.coerceIn(0, safeDuration - 1)
                val maxDuration = (safeDuration - safeStart).coerceAtLeast(1)
                layer.copy(
                    startTick = safeStart,
                    durationTicks = layer.durationTicks.coerceIn(1, maxDuration),
                )
            }
            return VfxEditor2Composition(name, safeDuration, safeLayers)
        }

        fun defaultLayers(): List<VfxEditor2Layer> = listOf(
            VfxEditor2Layer(
                id = 1,
                name = "Core Ribbon",
                color = 0xffffff,
                size = 0.32,
                density = 1.0,
                shapeParameters = VfxEditor2ShapeParameters(
                    length = 4.8,
                    arcSpan = 110.0,
                    width = 0.24,
                    sampleDensity = 14.0,
                    widthCurve = VfxEditor2WidthCurve.THIN_THICK_THIN,
                ),
            ),
            VfxEditor2Layer(
                id = 2,
                name = "Crimson Body",
                color = 0xd21f3c,
                size = 0.46,
                density = 1.0,
                shapeParameters = VfxEditor2ShapeParameters(
                    length = 4.7,
                    arcSpan = 110.0,
                    width = 0.46,
                    sampleDensity = 12.0,
                    widthCurve = VfxEditor2WidthCurve.THIN_THICK_THIN,
                ),
            ),
            VfxEditor2Layer(
                id = 3,
                name = "Fragments",
                color = 0x321b2d,
                size = 0.24,
                particleType = VfxEditor2Particle.ELECTRIC_SPARK,
                shapeType = VfxEditor2Shape.BURST,
                shapeParameters = VfxEditor2ShapeParameters(
                    burstRadius = 0.9,
                    burstCount = 12,
                    burstSpread = 1.4,
                    burstSpeed = 0.16,
                ),
                startTick = 2,
                durationTicks = 1,
            ),
        )
    }
}

data class VfxEditor2Open(val composition: VfxEditor2Composition) : ProtocolMessage

data class VfxEditor2PreviewRequest(
    val requestId: Long,
    val composition: VfxEditor2Composition,
    val loop: Boolean,
) : ProtocolMessage

object VfxEditor2PreviewCancel : ProtocolMessage

data class VfxEditor2SaveRequest(val composition: VfxEditor2Composition) : ProtocolMessage

data class VfxEditor2LoadRequest(val name: String) : ProtocolMessage {
    init { require(name.matches(VFX_EDITOR_2_NAME)) { "VFX Editor 2 draft name is invalid" } }
}

data class VfxEditor2Draft(val composition: VfxEditor2Composition) : ProtocolMessage

data class VfxEditor2DraftList(val names: List<String>) : ProtocolMessage {
    init {
        require(names.size <= VfxEditor2Limits.MAX_DRAFTS) { "Too many VFX Editor 2 drafts" }
        require(names.all { it.matches(VFX_EDITOR_2_NAME) }) { "VFX Editor 2 draft name is invalid" }
    }
}

data class VfxEditor2ApplyRequest(
    val runtimeVfxId: String,
    val composition: VfxEditor2Composition,
) : ProtocolMessage {
    init { require(runtimeVfxId.matches(VFX_EDITOR_2_ID)) { "VFX Editor 2 runtime id is invalid" } }
}

data class VfxEditor2Notice(val text: String) : ProtocolMessage {
    init { require(text.length <= 160) { "VFX Editor 2 notice is too long" } }
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
    private const val VFX_EDITOR_2_PREVIEW_REQUEST = 41
    private const val VFX_EDITOR_2_PREVIEW_CANCEL = 42
    private const val VFX_EDITOR_2_SAVE_REQUEST = 43
    private const val VFX_EDITOR_2_LOAD_REQUEST = 44
    private const val VFX_EDITOR_2_DRAFT = 45
    private const val VFX_EDITOR_2_DRAFT_LIST = 46
    private const val VFX_EDITOR_2_APPLY_REQUEST = 47
    private const val VFX_EDITOR_2_NOTICE = 48

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
                    writeVfxEditor2Composition(data, message.composition)
                }
                is VfxEditor2PreviewRequest -> {
                    data.writeByte(VFX_EDITOR_2_PREVIEW_REQUEST)
                    data.writeLong(message.requestId)
                    data.writeBoolean(message.loop)
                    writeVfxEditor2Composition(data, message.composition)
                }
                VfxEditor2PreviewCancel -> data.writeByte(VFX_EDITOR_2_PREVIEW_CANCEL)
                is VfxEditor2SaveRequest -> {
                    data.writeByte(VFX_EDITOR_2_SAVE_REQUEST)
                    writeVfxEditor2Composition(data, message.composition)
                }
                is VfxEditor2LoadRequest -> {
                    data.writeByte(VFX_EDITOR_2_LOAD_REQUEST)
                    writeString(data, message.name)
                }
                is VfxEditor2Draft -> {
                    data.writeByte(VFX_EDITOR_2_DRAFT)
                    writeVfxEditor2Composition(data, message.composition)
                }
                is VfxEditor2DraftList -> {
                    data.writeByte(VFX_EDITOR_2_DRAFT_LIST)
                    data.writeByte(message.names.size)
                    message.names.forEach { writeString(data, it) }
                }
                is VfxEditor2ApplyRequest -> {
                    data.writeByte(VFX_EDITOR_2_APPLY_REQUEST)
                    writeString(data, message.runtimeVfxId)
                    writeVfxEditor2Composition(data, message.composition)
                }
                is VfxEditor2Notice -> {
                    data.writeByte(VFX_EDITOR_2_NOTICE)
                    writeString(data, message.text)
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
            VFX_EDITOR_2_OPEN -> VfxEditor2Open(readVfxEditor2Composition(input))
            VFX_EDITOR_2_PREVIEW_REQUEST -> VfxEditor2PreviewRequest(
                requestId = input.readLong(),
                loop = input.readBoolean(),
                composition = readVfxEditor2Composition(input),
            )
            VFX_EDITOR_2_PREVIEW_CANCEL -> VfxEditor2PreviewCancel
            VFX_EDITOR_2_SAVE_REQUEST -> VfxEditor2SaveRequest(readVfxEditor2Composition(input))
            VFX_EDITOR_2_LOAD_REQUEST -> VfxEditor2LoadRequest(readString(input))
            VFX_EDITOR_2_DRAFT -> VfxEditor2Draft(readVfxEditor2Composition(input))
            VFX_EDITOR_2_DRAFT_LIST -> {
                val count = input.readUnsignedByte()
                require(count <= VfxEditor2Limits.MAX_DRAFTS) { "Too many VFX Editor 2 drafts" }
                VfxEditor2DraftList(List(count) { readString(input) })
            }
            VFX_EDITOR_2_APPLY_REQUEST -> VfxEditor2ApplyRequest(
                runtimeVfxId = readString(input),
                composition = readVfxEditor2Composition(input),
            )
            VFX_EDITOR_2_NOTICE -> VfxEditor2Notice(readString(input))
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

    private fun writeVfxEditor2Composition(data: DataOutputStream, composition: VfxEditor2Composition) {
        writeString(data, composition.name)
        data.writeShort(composition.durationTicks)
        data.writeByte(composition.layers.size)
        composition.layers.forEach { layer ->
            data.writeInt(layer.id)
            writeString(data, layer.name)
            data.writeBoolean(layer.enabled)
            data.writeBoolean(layer.solo)
            data.writeByte(layer.shapeType.ordinal)
            data.writeByte(layer.particleType.ordinal)
            data.writeInt(layer.color)
            data.writeDouble(layer.size)
            data.writeDouble(layer.density)
            data.writeDouble(layer.offset.forward)
            data.writeDouble(layer.offset.right)
            data.writeDouble(layer.offset.up)
            data.writeDouble(layer.rotation.yaw)
            data.writeDouble(layer.rotation.pitch)
            data.writeDouble(layer.rotation.roll)
            data.writeShort(layer.startTick)
            data.writeShort(layer.durationTicks)
            val shape = layer.shapeParameters
            data.writeDouble(shape.length)
            data.writeDouble(shape.arcSpan)
            data.writeDouble(shape.curvature)
            data.writeDouble(shape.width)
            data.writeDouble(shape.sampleDensity)
            data.writeByte(shape.laneCount)
            data.writeDouble(shape.laneSpacing)
            data.writeBoolean(shape.reverse)
            data.writeByte(shape.widthCurve.ordinal)
            data.writeDouble(shape.lineLength)
            data.writeDouble(shape.lineSpacing)
            data.writeDouble(shape.circleRadius)
            data.writeDouble(shape.circleArcDegrees)
            data.writeDouble(shape.circleSpacing)
            data.writeDouble(shape.burstRadius)
            data.writeByte(shape.burstCount)
            data.writeDouble(shape.burstSpread)
            data.writeDouble(shape.burstSpeed)
        }
    }

    private fun readVfxEditor2Composition(input: DataInputStream): VfxEditor2Composition {
        val name = readString(input)
        val durationTicks = input.readUnsignedShort()
        val layerCount = input.readUnsignedByte()
        require(layerCount <= VfxEditor2Limits.MAX_LAYERS) { "Too many VFX Editor 2 layers" }
        val layers = List(layerCount) {
            val layer = VfxEditor2Layer.clamped(
                id = input.readInt(),
                name = readString(input),
                enabled = input.readBoolean(),
                solo = input.readBoolean(),
                shapeType = VfxEditor2Shape.entries.getOrNull(input.readUnsignedByte())
                    ?: throw IllegalArgumentException("Unknown VFX Editor 2 shape"),
                particleType = VfxEditor2Particle.entries.getOrNull(input.readUnsignedByte())
                    ?: throw IllegalArgumentException("Unknown VFX Editor 2 particle"),
                color = input.readInt(),
                size = input.readDouble(),
                density = input.readDouble(),
                offset = VfxEditor2Offset.clamped(
                    input.readDouble(), input.readDouble(), input.readDouble(),
                ),
                rotation = VfxEditor2Rotation.clamped(
                    input.readDouble(), input.readDouble(), input.readDouble(),
                ),
                startTick = input.readUnsignedShort(),
                durationTicks = input.readUnsignedShort(),
                shapeParameters = VfxEditor2ShapeParameters.clamped(
                    length = input.readDouble(),
                    arcSpan = input.readDouble(),
                    curvature = input.readDouble(),
                    width = input.readDouble(),
                    sampleDensity = input.readDouble(),
                    laneCount = input.readUnsignedByte(),
                    laneSpacing = input.readDouble(),
                    reverse = input.readBoolean(),
                    widthCurve = VfxEditor2WidthCurve.entries.getOrNull(input.readUnsignedByte())
                        ?: throw IllegalArgumentException("Unknown VFX Editor 2 width curve"),
                    lineLength = input.readDouble(),
                    lineSpacing = input.readDouble(),
                    circleRadius = input.readDouble(),
                    circleArcDegrees = input.readDouble(),
                    circleSpacing = input.readDouble(),
                    burstRadius = input.readDouble(),
                    burstCount = input.readUnsignedByte(),
                    burstSpread = input.readDouble(),
                    burstSpeed = input.readDouble(),
                ),
            )
            layer
        }
        return VfxEditor2Composition.clamped(name, durationTicks, layers)
    }

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
