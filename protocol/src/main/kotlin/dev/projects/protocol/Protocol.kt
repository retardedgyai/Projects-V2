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
    const val CURRENT = 10

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

object ProtocolCodec {
    private const val MAX_PACKET_SIZE = 1024
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
