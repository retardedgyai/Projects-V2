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
    fun `attack started round trips`() {
        assertRoundTrip(AttackStarted(9001))
    }

    @Test
    fun `attack hit confirmed round trips`() {
        assertRoundTrip(AttackHitConfirmed(9001, UUID.fromString("58e6f12d-cf60-4cb2-9147-6f503fe24098")))
    }

    private fun assertRoundTrip(message: ProtocolMessage) {
        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
    }
}
