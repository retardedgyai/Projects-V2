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
    const val CURRENT = 1

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

object ProtocolCodec {
    private const val MAX_PACKET_SIZE = 1024
    private const val HELLO = 1
    private const val HELLO_ACK = 2
    private const val ATTACK_INPUT = 10
    private const val ATTACK_STARTED = 11
    private const val ATTACK_HIT_CONFIRMED = 12
    private const val DODGE_INPUT = 13

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
            else -> throw IllegalArgumentException("Unknown ProjectS message type: $type")
        }
        require(input.available() == 0) { "Unexpected trailing ProjectS protocol data" }
        return message
    }
}
