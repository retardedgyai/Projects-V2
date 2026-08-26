package dev.projects.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VfxEditor2ProtocolTest {
    @Test
    fun `checkpoint A messages round trip`() {
        listOf<ProtocolMessage>(
            VfxEditor2Open("Ronin Q"),
            VfxEditor2PreviewStart(17L),
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
