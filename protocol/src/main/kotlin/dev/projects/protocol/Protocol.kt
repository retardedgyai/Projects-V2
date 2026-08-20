package dev.projects.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.abs

const val PROJECTS_CHANNEL = "projects:protocol"

object ProtocolVersion {
    const val CURRENT = 4

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
    val skill3CooldownTicks: Int,
    val skill3CooldownMaxTicks: Int,
) : ProtocolMessage {
    init {
        require(maxMana > 0 && skill3CooldownMaxTicks > 0) { "Resource maximums must be positive" }
        require(mana in 0..maxMana) { "Mana is out of range" }
        require(skill3CooldownTicks in 0..skill3CooldownMaxTicks) { "Skill3 cooldown is out of range" }
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
                    data.writeInt(message.skill3CooldownTicks)
                    data.writeInt(message.skill3CooldownMaxTicks)
                }
            }
        }
        return output.toByteArray()
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
            )
            else -> throw IllegalArgumentException("Unknown ProjectS message type: $type")
        }
        require(input.available() == 0) { "Unexpected trailing ProjectS protocol data" }
        return message
    }
}
