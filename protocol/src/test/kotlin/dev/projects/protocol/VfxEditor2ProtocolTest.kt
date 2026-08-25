package dev.projects.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VfxEditor2ProtocolTest {
    @Test
    fun `editor 2 messages round trip with multiple layers`() {
        val composition = VfxEditor2Composition(
            name = "ronin_q",
            durationTicks = 24,
            layers = listOf(
                VfxEditor2Layer(
                    id = 1,
                    name = "Core Ribbon",
                    color = 0xffffff,
                    shapeParameters = VfxEditor2ShapeParameters(
                        widthCurve = VfxEditor2WidthCurve.THIN_THICK_THIN,
                        laneCount = 3,
                    ),
                ),
                VfxEditor2Layer(
                    id = 2,
                    name = "Fragments",
                    shapeType = VfxEditor2Shape.BURST,
                    particleType = VfxEditor2Particle.ELECTRIC_SPARK,
                    startTick = 4,
                    durationTicks = 1,
                ),
            ),
        )
        listOf<ProtocolMessage>(
            VfxEditor2Open(composition),
            VfxEditor2PreviewRequest(17L, composition, loop = true),
            VfxEditor2PreviewCancel,
            VfxEditor2SaveRequest(composition),
            VfxEditor2LoadRequest("ronin_q"),
            VfxEditor2Draft(composition),
            VfxEditor2DraftList(listOf("ronin_q")),
            VfxEditor2ApplyRequest("ronin.q", composition),
            VfxEditor2Notice("saved"),
        ).forEach { message -> assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message))) }
    }

    @Test
    fun `editor 2 clamps safe numeric bounds and rejects non finite values`() {
        val layer = VfxEditor2Layer.clamped(
            id = 1,
            name = "Layer",
            enabled = true,
            solo = false,
            shapeType = VfxEditor2Shape.RIBBON,
            particleType = VfxEditor2Particle.DUST,
            color = -10,
            size = 100.0,
            density = 100.0,
            offset = VfxEditor2Offset.clamped(-100.0, 100.0, 100.0),
            rotation = VfxEditor2Rotation.clamped(-1000.0, 1000.0, 1000.0),
            startTick = 1000,
            durationTicks = 1000,
            shapeParameters = VfxEditor2ShapeParameters.clamped(
                length = 100.0, arcSpan = 1000.0, curvature = 100.0, width = 100.0, sampleDensity = 100.0,
                laneCount = 100, laneSpacing = 100.0, reverse = false, widthCurve = VfxEditor2WidthCurve.CONSTANT,
                lineLength = 100.0, lineSpacing = 100.0, circleRadius = 100.0, circleArcDegrees = 1000.0,
                circleSpacing = 100.0, burstRadius = 100.0, burstCount = 1000, burstSpread = 100.0, burstSpeed = 100.0,
            ),
        )
        assertEquals(2.0, layer.size)
        assertEquals(32.0, layer.density)
        assertEquals(4, layer.shapeParameters.laneCount)
        assertEquals(64, layer.shapeParameters.burstCount)
        assertEquals(200, layer.startTick)
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2ShapeParameters.clamped(
                length = Double.NaN, arcSpan = 1.0, curvature = 0.0, width = 0.0, sampleDensity = 1.0,
                laneCount = 1, laneSpacing = 0.0, reverse = false, widthCurve = VfxEditor2WidthCurve.CONSTANT,
                lineLength = 1.0, lineSpacing = 0.1, circleRadius = 1.0, circleArcDegrees = 1.0,
                circleSpacing = 0.1, burstRadius = 1.0, burstCount = 1, burstSpread = 0.0, burstSpeed = 0.0,
            )
        }
    }

    @Test
    fun `editor 2 layer and draft limits are enforced`() {
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Composition(layers = (1..17).map { VfxEditor2Layer(id = it, name = "Layer $it") })
        }
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2DraftList((1..33).map { "draft-$it" })
        }
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2ApplyRequest("ronin/q", VfxEditor2Composition())
        }
    }

    @Test
    fun `maximum legal composition fits the bounded packet`() {
        val composition = VfxEditor2Composition(
            durationTicks = 200,
            layers = (1..16).map { index ->
                VfxEditor2Layer(
                    id = index,
                    name = ("L$index" + "x".repeat(30)).take(32),
                    shapeType = VfxEditor2Shape.entries[(index - 1) % VfxEditor2Shape.entries.size],
                    particleType = VfxEditor2Particle.entries[(index - 1) % VfxEditor2Particle.entries.size],
                    startTick = index,
                    durationTicks = 8,
                )
            },
        )
        val packet = ProtocolCodec.encode(VfxEditor2PreviewRequest(1L, composition, loop = false))
        assertTrue(packet.size < 8192)
        assertEquals(composition, ProtocolCodec.decode(packet).let { (it as VfxEditor2PreviewRequest).composition })
    }
}
