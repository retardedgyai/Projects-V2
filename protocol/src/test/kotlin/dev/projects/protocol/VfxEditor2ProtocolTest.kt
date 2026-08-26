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
            VfxEditor2TargetCatalog(
                listOf(
                    VfxEditor2TargetDescriptor("ronin.q.main", "ronin", "Ronin", "Q"),
                    VfxEditor2TargetDescriptor("starweaver.q.sun.impact", "starweaver", "Starweaver", "Q Sun - Impact"),
                ),
            ),
            VfxEditor2BindingSnapshot(mapOf("ronin.q.main" to "ronin_q_red")),
            VfxEditor2ApplyBindingRequest("ronin.q.main", "ronin_q_red"),
            VfxEditor2BindingResult("ronin.q.main", "ronin_q_red", true, "Applied"),
            VfxEditor2BindingResult("ronin.q.main", null, false, "Unknown target"),
            VfxEditor2ClearBindingRequest("ronin.q.main"),
        ).forEach { message ->
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        }
    }

    @Test
    fun `checkpoint C1 save list and load messages round trip`() {
        val composition = defaultVfxEditor2Composition().copy(
            name = "timeline_01",
            timelineLengthTicks = 60,
            effects = listOf(
                defaultVfxEditor2Effect(VfxEditor2EffectType.ARC_SLASH, 1L).copy(
                    startTick = 4,
                    durationTicks = 20,
                ),
                defaultVfxEditor2Effect(VfxEditor2EffectType.BURST, 2L).copy(startTick = 32),
            ),
        )
        listOf<ProtocolMessage>(
            VfxEditor2SaveRequest(composition),
            VfxEditor2SaveResult("timeline_01", success = true, overwritten = true, message = "Saved"),
            VfxEditor2ListRequest,
            VfxEditor2ListResponse(listOf("timeline_01", "second")),
            VfxEditor2LoadRequest("timeline_01"),
            VfxEditor2LoadResponse("timeline_01", composition, "Loaded"),
            VfxEditor2LoadResponse("missing", null, "Composition not found"),
        ).forEach { message ->
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        }
    }

    @Test
    fun `checkpoint C1 timing clamps to the two hundred tick composition limit`() {
        val authored = defaultVfxEditor2Effect(VfxEditor2EffectType.ARC_SLASH, 1L).copy(
            startTick = 199,
            durationTicks = 200,
        )
        val clamped = VfxEditor2Composition.clamped("timing", 40, listOf(authored))
        assertEquals(199, clamped.effects.single().startTick)
        assertEquals(1, clamped.effects.single().durationTicks)
        assertEquals(200, clamped.timelineLengthTicks)
        assertEquals(1, defaultVfxEditor2Effect(VfxEditor2EffectType.BURST, 2L).durationTicks)
        assertFailsWith<IllegalArgumentException> {
            defaultVfxEditor2Effect(VfxEditor2EffectType.BURST, 2L).copy(durationTicks = 2)
        }
    }

    @Test
    fun `editor 2 round trips every shape type`() {
        VfxEditor2EffectType.entries.forEachIndexed { index, type ->
            val effect = defaultVfxEditor2Effect(type, index.toLong() + 1L)
            val message = VfxEditor2PreviewStart(index.toLong(), VfxEditor2Composition(listOf(effect)))
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)), type.name)
        }
    }

    @Test
    fun `two effect composition round trips`() {
        val composition = VfxEditor2Composition(
            listOf(
                defaultVfxEditor2Effect(VfxEditor2EffectType.ARC_SLASH, 1L),
                defaultVfxEditor2Effect(VfxEditor2EffectType.BEZIER, 2L),
            ),
        )
        val message = VfxEditor2PreviewStart(42L, composition)

        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        assertTrue(ProtocolCodec.encode(message).size < 8192)
    }

    @Test
    fun `checkpoint B1 protocol validates bounded fields`() {
        assertFailsWith<IllegalArgumentException> { VfxEditor2Open(" ") }
        assertFailsWith<IllegalArgumentException> { VfxEditor2PreviewStart(-1L) }
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Status(VfxEditor2StatusKind.ERROR, "x".repeat(161))
        }
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Shape.Sphere(Double.NaN)
        }
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
            VfxEditor2EffectType.entries.take(VFX_EDITOR_2_MAX_EFFECTS).mapIndexed { index, type ->
                defaultVfxEditor2Effect(type, index.toLong() + 1L).copy(
                    name = "Effect ${index + 1}",
                    transform = VfxEditor2Transform(8.0, -5.0, 5.0, 180.0, -180.0, 180.0),
                    appearance = VfxEditor2Appearance(0xffffff, 1.5, 4.0),
                )
            },
        )
        val message = VfxEditor2PreviewStart(Long.MAX_VALUE, composition)

        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        assertTrue(ProtocolCodec.encode(message).size <= 8192)
        assertTrue(composition.estimatedSampleCount() <= VFX_EDITOR_2_MAX_TOTAL_SAMPLES)
    }

    @Test
    fun `checkpoint B1 model validates visibility and clamps authored values`() {
        val defaults = defaultVfxEditor2Composition()
        assertEquals(2, defaults.visibleEffects().size)
        val clampedTransform = VfxEditor2Transform.clamped(99.0, -99.0, 99.0, 999.0, -999.0, 999.0)
        assertEquals(8.0, clampedTransform.forward)
        assertEquals(-5.0, clampedTransform.side)
        assertEquals(5.0, clampedTransform.height)
        assertEquals(64, VfxEditor2Shape.Burst.clamped(99.0, 999, 999.0, 999.0).count)
        assertEquals(256, VfxEditor2Shape.Orb.clamped(99.0, 999, 42L).count)

        val hidden = defaults.effects[1].copy(enabled = false)
        assertEquals(listOf(defaults.effects[0]), defaults.copy(effects = listOf(defaults.effects[0], hidden)).visibleEffects())

        val solo = defaults.effects[0].copy(solo = true)
        assertEquals(listOf(solo), defaults.copy(effects = listOf(solo, defaults.effects[1])).visibleEffects())
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Composition(listOf(defaults.effects[0], defaults.effects[0]))
        }
    }

    @Test
    fun `checkpoint B1 composition editing supports add duplicate and delete`() {
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
    fun `checkpoint B1 rejects truncated and trailing composition packets`() {
        val encoded = ProtocolCodec.encode(VfxEditor2PreviewStart(1L, defaultVfxEditor2Composition()))
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(encoded + byteArrayOf(0))
        }
    }

    @Test
    fun `checkpoint B version is rejected as a version mismatch`() {
        val checkpointBHello = ProtocolCodec.decode(ProtocolCodec.encode(ProtocolHello(18))) as ProtocolHello

        val error = assertFailsWith<ProtocolVersionMismatchException> {
            ProtocolVersion.requireCompatible(checkpointBHello.version)
        }

        assertEquals("ProjectS protocol version mismatch: expected 19, received 18", error.message)
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
