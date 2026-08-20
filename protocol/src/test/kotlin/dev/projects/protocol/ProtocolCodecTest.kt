package dev.projects.protocol

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolCodecTest {
    @Test
    fun `current protocol version is accepted`() {
        ProtocolVersion.requireCompatible(ProtocolVersion.CURRENT)
    }

    @Test
    fun `mismatched protocol version is rejected`() {
        assertFailsWith<ProtocolVersionMismatchException> {
            ProtocolVersion.requireCompatible(ProtocolVersion.CURRENT + 1)
        }
    }

    @Test
    fun `handshake messages round trip`() {
        assertRoundTrip(ProtocolHello(ProtocolVersion.CURRENT))
        assertRoundTrip(ProtocolHelloAck(ProtocolVersion.CURRENT))
    }

    @Test
    fun `unknown protocol data fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(byteArrayOf(127))
        }
    }

    @Test
    fun `truncated protocol data fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(byteArrayOf(1))
        }
    }

    @Test
    fun `attack input round trips`() {
        assertRoundTrip(AttackInput(AttackInputState.PRESS, 42))
        assertRoundTrip(AttackInput(AttackInputState.RELEASE, 43))
    }

    @Test
    fun `attack input represents press and release state changes`() {
        assertEquals(listOf(AttackInputState.PRESS, AttackInputState.RELEASE), AttackInputState.entries)
        val transitions = listOf(
            AttackInput(AttackInputState.PRESS, 42),
            AttackInput(AttackInputState.RELEASE, 43),
        )
        assertEquals(AttackInputState.PRESS, transitions.first().state)
        assertEquals(AttackInputState.RELEASE, transitions.last().state)
        assertEquals(listOf(42L, 43L), transitions.map { it.sequence })
    }

    @Test
    fun `attack started round trips`() {
        assertRoundTrip(AttackStarted(9001))
    }

    @Test
    fun `attack hit confirmed round trips`() {
        assertRoundTrip(AttackHitConfirmed(9001, UUID.fromString("58e6f12d-cf60-4cb2-9147-6f503fe24098")))
    }

    @Test
    fun `dodge input round trips`() {
        assertRoundTrip(DodgeInput(-1.0, 1.0))
        assertRoundTrip(DodgeInput(0.0, 0.0))
    }

    @Test
    fun `invalid dodge direction is rejected`() {
        assertFailsWith<IllegalArgumentException> { DodgeInput(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { DodgeInput(1.1, 0.0) }

        val malformed = ProtocolCodec.encode(DodgeInput(0.0, 0.0)).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 1, Double.SIZE_BYTES).putDouble(Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    @Test
    fun `air jump input round trips`() {
        assertRoundTrip(AirJumpInput(-1.0, 1.0))
        assertRoundTrip(AirJumpInput(0.0, 0.0))
    }

    @Test
    fun `invalid air jump direction is rejected`() {
        assertFailsWith<IllegalArgumentException> { AirJumpInput(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { AirJumpInput(1.1, 0.0) }

        val malformed = ProtocolCodec.encode(AirJumpInput(0.0, 0.0)).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 1, Double.SIZE_BYTES).putDouble(Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    private fun assertRoundTrip(message: ProtocolMessage) {
        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
    }
}
