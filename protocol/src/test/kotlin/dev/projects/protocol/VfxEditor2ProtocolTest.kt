package dev.projects.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VfxEditor2ProtocolTest {
    @Test
    fun `editor 2 messages round trip`() {
        listOf<ProtocolMessage>(
            VfxEditor2Open("Ronin Q", defaultVfxEditor2Composition()),
            VfxEditor2PreviewStart(17L, defaultVfxEditor2Composition()),
            VfxEditor2PreviewStop,
            VfxEditor2Status(VfxEditor2StatusKind.PLAYING, "Playing"),
            VfxEditor2Status(VfxEditor2StatusKind.ERROR, "Preview failed"),
        ).forEach { message ->
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        }
    }

    @Test
    fun `checkpoint A protocol validates bounded fields`() {
        assertFailsWith<IllegalArgumentException> { VfxEditor2Open(" ") }
        assertFailsWith<IllegalArgumentException> { VfxEditor2PreviewStart(-1L) }
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Status(VfxEditor2StatusKind.ERROR, "x".repeat(161))
        }
    }

    @Test
    fun `checkpoint B composition round trips every effect type`() {
        val composition = VfxEditor2Composition(
            listOf(
                VfxEditor2Effect(1L, "Arc", VfxEditor2EffectType.ARC_SLASH, VfxEditor2Shape.ArcSlash()),
                VfxEditor2Effect(2L, "Line", VfxEditor2EffectType.STRAIGHT_SLASH, VfxEditor2Shape.StraightSlash()),
                VfxEditor2Effect(3L, "Ring", VfxEditor2EffectType.RING, VfxEditor2Shape.Ring()),
                VfxEditor2Effect(4L, "Burst", VfxEditor2EffectType.BURST, VfxEditor2Shape.Burst()),
            ),
        )
        val message = VfxEditor2PreviewStart(42L, composition)

        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        assertTrue(ProtocolCodec.encode(message).size < 8192)
    }

    @Test
    fun `default composition round trips through open and preview start`() {
        val composition = defaultVfxEditor2Composition()

        assertEquals(
            VfxEditor2Open("Ronin Q", composition),
            ProtocolCodec.decode(ProtocolCodec.encode(VfxEditor2Open("Ronin Q", composition))),
        )
        assertEquals(
            VfxEditor2PreviewStart(99L, composition),
            ProtocolCodec.decode(ProtocolCodec.encode(VfxEditor2PreviewStart(99L, composition))),
        )
    }

    @Test
    fun `maximum legal composition round trips`() {
        val composition = VfxEditor2Composition(
            (0 until 8).map { index ->
                val id = index.toLong() + 1L
                when (index % 4) {
                    0 -> VfxEditor2Effect(
                        id = id,
                        name = "Arc $id",
                        type = VfxEditor2EffectType.ARC_SLASH,
                        shape = VfxEditor2Shape.ArcSlash(10.0, 300.0, 2.0, 1.5),
                        transform = VfxEditor2Transform(8.0, -5.0, 5.0, 180.0, -180.0, 180.0),
                        appearance = VfxEditor2Appearance(0xffffff, 1.5, 4.0),
                    )
                    1 -> VfxEditor2Effect(
                        id = id,
                        name = "Line $id",
                        type = VfxEditor2EffectType.STRAIGHT_SLASH,
                        shape = VfxEditor2Shape.StraightSlash(10.0, 1.5),
                        transform = VfxEditor2Transform(8.0, -5.0, 5.0, 180.0, -180.0, 180.0),
                        appearance = VfxEditor2Appearance(0xffffff, 1.5, 4.0),
                    )
                    2 -> VfxEditor2Effect(
                        id = id,
                        name = "Ring $id",
                        type = VfxEditor2EffectType.RING,
                        shape = VfxEditor2Shape.Ring(8.0, 360.0, 1.5),
                        transform = VfxEditor2Transform(8.0, -5.0, 5.0, 180.0, -180.0, 180.0),
                        appearance = VfxEditor2Appearance(0xffffff, 1.5, 4.0),
                    )
                    else -> VfxEditor2Effect(
                        id = id,
                        name = "Burst $id",
                        type = VfxEditor2EffectType.BURST,
                        shape = VfxEditor2Shape.Burst(8.0, 64, 89.0, 3.0),
                        transform = VfxEditor2Transform(8.0, -5.0, 5.0, 180.0, -180.0, 180.0),
                        appearance = VfxEditor2Appearance(0xffffff, 1.5, 4.0),
                    )
                }
            },
        )
        val message = VfxEditor2PreviewStart(Long.MAX_VALUE, composition)

        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        assertTrue(ProtocolCodec.encode(message).size <= 8192)
    }

    @Test
    fun `checkpoint B model validates visibility and clamps authored values`() {
        val defaults = defaultVfxEditor2Composition()
        assertEquals(2, defaults.visibleEffects().size)
        val clampedTransform = VfxEditor2Transform.clamped(99.0, -99.0, 99.0, 999.0, -999.0, 999.0)
        assertEquals(8.0, clampedTransform.forward)
        assertEquals(-5.0, clampedTransform.side)
        assertEquals(5.0, clampedTransform.height)
        assertEquals(64, VfxEditor2Shape.Burst.clamped(99.0, 999, 999.0, 999.0).count)

        val hidden = defaults.effects[1].copy(enabled = false)
        assertEquals(listOf(defaults.effects[0]), defaults.copy(effects = listOf(defaults.effects[0], hidden)).visibleEffects())

        val solo = defaults.effects[0].copy(solo = true)
        assertEquals(listOf(solo), defaults.copy(effects = listOf(solo, defaults.effects[1])).visibleEffects())
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Composition(listOf(defaults.effects[0], defaults.effects[0]))
        }
    }

    @Test
    fun `checkpoint B composition editing supports add duplicate and delete`() {
        val start = VfxEditor2Composition(emptyList())
        val arc = VfxEditor2Effect(10L, "Arc", VfxEditor2EffectType.ARC_SLASH, VfxEditor2Shape.ArcSlash())
        val withArc = start.add(arc)!!
        val withCopy = withArc.duplicate(10L, 11L, "Arc Copy")!!
        val withoutArc = withCopy.remove(10L)

        assertEquals(listOf(arc), withArc.effects)
        assertEquals(listOf(arc, arc.copy(id = 11L, name = "Arc Copy")), withCopy.effects)
        assertEquals(listOf(arc.copy(id = 11L, name = "Arc Copy")), withoutArc.effects)
    }

    @Test
    fun `checkpoint B rejects truncated composition packet`() {
        val encoded = ProtocolCodec.encode(VfxEditor2PreviewStart(1L, defaultVfxEditor2Composition()))
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun `checkpoint A hello is rejected as a version mismatch`() {
        val checkpointAHello = byteArrayOf(1, 0, 0, 0, 15)
        val hello = ProtocolCodec.decode(checkpointAHello) as ProtocolHello

        val error = assertFailsWith<ProtocolVersionMismatchException> {
            ProtocolVersion.requireCompatible(hello.version)
        }

        assertEquals("ProjectS protocol version mismatch: expected 16, received 15", error.message)
    }

    @Test
    fun `checkpoint A editor open packet reports its packet context`() {
        val checkpointAOpen = byteArrayOf(40, 0, 7) + "Ronin Q".toByteArray(Charsets.UTF_8)

        val error = assertFailsWith<ProtocolDecodeException> {
            ProtocolCodec.decode(checkpointAOpen)
        }

        assertEquals(40, error.packetId)
        assertEquals("VfxEditor2Open", error.packetName)
        assertEquals("unexpected end of packet", error.reason)
        assertEquals("Malformed ProjectS protocol packet", error.message)
    }

    @Test
    fun `old slash editor messages still round trip`() {
        listOf<ProtocolMessage>(
            VfxEditorOpen(),
            VfxSlashPreviewRequest(3L, SlashEditorParameters()),
            VfxSlashPreviewCancel,
        ).forEach { message ->
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        }
    }
}
