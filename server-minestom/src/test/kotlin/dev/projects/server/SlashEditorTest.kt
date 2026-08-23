package dev.projects.server

import dev.projects.protocol.SlashEditorParameters
import dev.projects.server.particle.RecordingParticleSink
import dev.projects.server.particle.ParticleTransform
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SlashEditorTest {
    @Test
    fun `drafts survive a new store and reject unsafe names`() {
        val directory = Files.createTempDirectory("slash-editor-test")
        val file = directory.resolve("drafts.json")
        val parameters = SlashEditorParameters(color = 0x123456)

        assertTrue(SlashDraftStore(file).save("deep blue", parameters))
        val reloaded = SlashDraftStore(file)
        assertEquals(parameters, reloaded.load("deep blue"))
        assertFalse(reloaded.save("../escape", parameters))
        assertFalse(reloaded.save("", parameters))
    }

    @Test
    fun `legacy drafts load with safe lane defaults`() {
        val file = Files.createTempFile("slash-editor-legacy", ".json")
        Files.writeString(file, """
            {"schemaVersion":1,"drafts":[{"name":"legacy","parameters":{"originY":1.2,"forwardOffset":1.7,"length":5.0,"arcSpan":160.0,"curvature":0.35,"tilt":12.0,"yaw":0.0,"width":0.8,"particleSize":0.28,"spacing":0.18,"durationTicks":8,"color":1190875,"targetDistance":5.0}}]}
        """.trimIndent())
        val loaded = SlashDraftStore(file).load("legacy")
        assertEquals(1, loaded?.laneCount)
        assertEquals(0.18, loaded?.laneSpacing)
    }

    @Test
    fun `draft store enforces the maximum number of drafts`() {
        val file = Files.createTempFile("slash-editor-test", ".json")
        val store = SlashDraftStore(file)
        repeat(16) { index -> assertTrue(store.save("draft-$index", SlashEditorParameters())) }
        assertFalse(store.save("draft-16", SlashEditorParameters()))
        assertTrue(store.save("draft-0", SlashEditorParameters(color = 0xabcdef)))
    }

    @Test
    fun `skill3 binding survives reload and malformed binding falls back`() {
        val directory = Files.createTempDirectory("skill3-binding-test")
        val file = directory.resolve("binding.json")
        val parameters = SlashEditorParameters(laneCount = 3, laneSpacing = 0.9, yaw = 42.0, forwardOffset = 3.2)

        assertTrue(Skill3SlashBindingStore(file).save(parameters))
        assertEquals(parameters, Skill3SlashBindingStore(file).load())

        Files.writeString(file, "not-json")
        assertEquals(null, Skill3SlashBindingStore(file).load())
    }

    @Test
    fun `pulse and finisher bindings are independent`() {
        val directory = Files.createTempDirectory("skill3-binding-independent-test")
        val pulse = SlashEditorParameters(color = 0x112233)
        val finisher = SlashEditorParameters(color = 0xaabbcc, length = 9.0)
        assertTrue(Skill3SlashBindingStore(directory.resolve("pulse.json")).save(pulse))
        assertTrue(Skill3SlashBindingStore(directory.resolve("finisher.json")).save(finisher))
        assertEquals(pulse, Skill3SlashBindingStore(directory.resolve("pulse.json")).load())
        assertEquals(finisher, Skill3SlashBindingStore(directory.resolve("finisher.json")).load())
    }

    @Test
    fun `preview emits finite positions with the requested color`() {
        val color = 0x123456
        val effect = SlashEditorPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0), SlashEditorParameters(color = color))
        val sink = RecordingParticleSink()
        repeat(effect.durationTicks) { effect.emit(it, sink) }

        assertTrue(sink.spawns.isNotEmpty())
        assertTrue(sink.spawns.all { spawn ->
            spawn.position.x().isFinite() && spawn.position.y().isFinite() && spawn.position.z().isFinite()
        })
        val dustColors = sink.spawns.mapNotNull { spawn ->
            (spawn.particle as? Particle.Dust)?.color()?.let { textColor ->
                textColor.red() shl 16 or (textColor.green() shl 8) or textColor.blue()
            }
        }.toSet()
        assertEquals(setOf(color), dustColors)
    }

    @Test
    fun `lane count creates distinct trajectories and spacing separates them`() {
        fun positions(spacing: Double): List<Double> {
            val effect = SlashEditorPreview.create(
                Pos.ZERO,
                Vec(0.0, 0.0, 1.0),
                SlashEditorParameters(laneCount = 3, laneSpacing = spacing, arcSpan = 10.0, durationTicks = 1),
            )
            val sink = RecordingParticleSink()
            effect.emit(0, sink)
            return sink.spawns.map { it.position.x() }
        }

        val narrow = positions(0.1)
        val wide = positions(0.5)
        assertTrue(narrow.toSet().size >= 3)
        assertTrue(wide.max() - wide.min() > narrow.max() - narrow.min())
        assertNotEquals(narrow, wide)
    }

    @Test
    fun `runtime slash origin preserves authored anchor values`() {
        val parameters = SlashEditorParameters(originY = 2.4, forwardOffset = 3.6, yaw = 27.0)
        val origin = slashOrigin(Pos(10.0, 20.0, 30.0), Vec(0.0, 0.0, 1.0), parameters)

        assertEquals(10.0, origin.x())
        assertEquals(22.4, origin.y())
        assertEquals(33.6, origin.z())
    }

    @Test
    fun `slash origin and geometry rotate with locked skill3 direction`() {
        val parameters = SlashEditorParameters(
            originY = 1.2,
            forwardOffset = 2.5,
            arcSpan = 96.0,
            tilt = 11.0,
            yaw = 23.0,
            durationTicks = 1,
        )
        val position = Pos(4.0, 8.0, -3.0)
        val forward = Vec(0.0, 0.0, 1.0)
        val right = Vec(1.0, 0.0, 0.0)
        val forwardOrigin = slashOrigin(position, forward, parameters)
        val rightOrigin = slashOrigin(position, right, parameters)

        assertEquals(position.x(), forwardOrigin.x(), absoluteTolerance = 0.000001)
        assertEquals(position.z() + parameters.forwardOffset, forwardOrigin.z(), absoluteTolerance = 0.000001)
        assertEquals(position.x() + parameters.forwardOffset, rightOrigin.x(), absoluteTolerance = 0.000001)
        assertEquals(position.z(), rightOrigin.z(), absoluteTolerance = 0.000001)

        fun emitted(direction: Vec, origin: Point): List<Point> {
            val sink = RecordingParticleSink()
            SlashEditorPreview.create(origin, direction, parameters).emit(0, sink)
            return sink.spawns.map { it.position }
        }

        val forwardPositions = emitted(forward, forwardOrigin)
        val rightPositions = emitted(right, rightOrigin)
        assertEquals(forwardPositions.size, rightPositions.size)
        forwardPositions.zip(rightPositions).forEach { (zFacing, xFacing) ->
            assertEquals(zFacing.z() - forwardOrigin.z() + rightOrigin.x(), xFacing.x(), absoluteTolerance = 0.000001)
            assertEquals(zFacing.y(), xFacing.y(), absoluteTolerance = 0.000001)
            assertEquals(-(zFacing.x() - forwardOrigin.x()) + rightOrigin.z(), xFacing.z(), absoluteTolerance = 0.000001)
        }
    }

    @Test
    fun `skill3 slash origin follows full 3d direction`() {
        val parameters = SlashEditorParameters(originY = 1.25, forwardOffset = 4.0)
        val position = Pos(2.0, 10.0, -3.0)

        val diagonal = skill3SlashOrigin(position, Vec(1.0, 2.0, 2.0), parameters)
        assertEquals(2.0 + 4.0 / 3.0, diagonal.x(), absoluteTolerance = 0.000001)
        assertEquals(10.0 + 1.25 + 8.0 / 3.0, diagonal.y(), absoluteTolerance = 0.000001)
        assertEquals(-3.0 + 8.0 / 3.0, diagonal.z(), absoluteTolerance = 0.000001)

        val upward = skill3SlashOrigin(position, Vec(0.0, 1.0, 0.0), parameters)
        assertEquals(position.x(), upward.x(), absoluteTolerance = 0.000001)
        assertEquals(position.y() + 1.25 + 4.0, upward.y(), absoluteTolerance = 0.000001)
        assertEquals(position.z(), upward.z(), absoluteTolerance = 0.000001)

        val downward = skill3SlashOrigin(position, Vec(0.0, -1.0, 0.0), parameters)
        assertEquals(position.y() + 1.25 - 4.0, downward.y(), absoluteTolerance = 0.000001)
    }

    @Test
    fun `skill3 slash origin uses positive z fallback for degenerate direction`() {
        val parameters = SlashEditorParameters(originY = 0.5, forwardOffset = 2.0)
        val origin = skill3SlashOrigin(Pos.ZERO, Vec.ZERO, parameters)

        assertEquals(0.0, origin.x(), absoluteTolerance = 0.000001)
        assertEquals(0.5, origin.y(), absoluteTolerance = 0.000001)
        assertEquals(2.0, origin.z(), absoluteTolerance = 0.000001)
    }

    @Test
    fun `skill3 slash visual direction follows full 3d direction`() {
        val forward = skill3SlashVisualDirection(Vec(0.0, 0.0, 1.0))
        assertEquals(0.0, forward.x(), absoluteTolerance = 0.000001)
        assertEquals(0.0, forward.y(), absoluteTolerance = 0.000001)
        assertEquals(1.0, forward.z(), absoluteTolerance = 0.000001)
        val right = skill3SlashVisualDirection(Vec(1.0, 0.0, 0.0))
        assertEquals(1.0, right.x(), absoluteTolerance = 0.000001)
        assertEquals(0.0, right.y(), absoluteTolerance = 0.000001)
        assertEquals(0.0, right.z(), absoluteTolerance = 0.000001)

        val diagonal = skill3SlashVisualDirection(Vec(0.0, 1.0, 1.0))
        assertEquals(1.0 / Math.sqrt(2.0), diagonal.y(), absoluteTolerance = 0.000001)
        assertEquals(1.0 / Math.sqrt(2.0), diagonal.z(), absoluteTolerance = 0.000001)
    }

    @Test
    fun `skill3 slash visual direction preserves vertical pitch while origin keeps full 3d direction`() {
        val upward = skill3SlashVisualDirection(Vec(0.0, 1.0, 0.0))
        assertEquals(0.0, upward.x(), absoluteTolerance = 0.000001)
        assertEquals(1.0, upward.y(), absoluteTolerance = 0.000001)
        assertEquals(0.0, upward.z(), absoluteTolerance = 0.000001)

        val downward = skill3SlashVisualDirection(Vec(0.0, -1.0, 0.0))
        assertEquals(0.0, downward.x(), absoluteTolerance = 0.000001)
        assertEquals(-1.0, downward.y(), absoluteTolerance = 0.000001)
        assertEquals(0.0, downward.z(), absoluteTolerance = 0.000001)

        val direction = Vec(0.0, 10.0, 1.0)
        val visual = skill3SlashVisualDirection(direction)
        assertEquals(10.0 / Math.sqrt(101.0), visual.y(), absoluteTolerance = 0.000001)
        assertEquals(1.0 / Math.sqrt(101.0), visual.z(), absoluteTolerance = 0.000001)

        val origin = skill3SlashOrigin(Pos.ZERO, direction, SlashEditorParameters(forwardOffset = 2.0))
        assertEquals(1.2 + 20.0 / Math.sqrt(101.0), origin.y(), absoluteTolerance = 0.000001)
    }

    @Test
    fun `skill3 slash visual direction uses positive z fallback for invalid direction`() {
        val visual = skill3SlashVisualDirection(Vec(Double.NaN, 0.0, 1.0))

        assertEquals(0.0, visual.x(), absoluteTolerance = 0.000001)
        assertEquals(0.0, visual.y(), absoluteTolerance = 0.000001)
        assertEquals(1.0, visual.z(), absoluteTolerance = 0.000001)
    }

    @Test
    fun `zero authored yaw follows locked direction while yaw remains local`() {
        val zeroYaw = SlashEditorParameters(yaw = 0.0, tilt = 0.0, arcSpan = 0.0, durationTicks = 1)
        val localYaw = SlashEditorParameters(yaw = 35.0, tilt = 0.0, arcSpan = 0.0, durationTicks = 1)

        fun firstOffset(direction: Vec, parameters: SlashEditorParameters): Vec {
            val origin = slashOrigin(Pos.ZERO, direction, parameters)
            val sink = RecordingParticleSink()
            SlashEditorPreview.create(origin, direction, parameters).emit(0, sink)
            val point = sink.spawns.first().position
            return Vec(point.x() - origin.x(), point.y() - origin.y(), point.z() - origin.z())
        }

        val zeroZ = firstOffset(Vec(0.0, 0.0, 1.0), zeroYaw)
        val zeroX = firstOffset(Vec(1.0, 0.0, 0.0), zeroYaw)
        assertTrue(zeroZ.x() < 0.0)
        assertEquals(zeroZ.z(), zeroX.x(), absoluteTolerance = 0.000001)
        assertEquals(-zeroZ.x(), zeroX.z(), absoluteTolerance = 0.000001)

        val localZ = firstOffset(Vec(0.0, 0.0, 1.0), localYaw)
        val localX = firstOffset(Vec(1.0, 0.0, 0.0), localYaw)
        assertEquals(localZ.z(), localX.x(), absoluteTolerance = 0.000001)
        assertEquals(-localZ.x(), localX.z(), absoluteTolerance = 0.000001)
        assertNotEquals(zeroZ, localZ)
    }

    @Test
    fun `skill3 pulse choreography varies orientation without changing authored parameters`() {
        val authored = SlashEditorParameters(laneCount = 3, laneSpacing = 0.9, yaw = 7.0, tilt = 4.0, originY = 1.5, durationTicks = 8)
        val authoredBefore = authored
        val specs = (1..4).map { skill3SlashPulseSpec(authored, it) }

        assertEquals(authoredBefore, authored)
        assertTrue(specs.all { it.parameters.durationTicks == Skill3State.PULSE_INTERVAL_TICKS })
        assertEquals(listOf(false, true, false, true), specs.map { it.reverseDraw })
        assertEquals(4, specs.map { Triple(it.parameters.yaw, it.parameters.tilt, it.parameters.originY) }.toSet().size)
        assertTrue(specs.all { it.parameters.laneCount == authored.laneCount && it.parameters.laneSpacing == authored.laneSpacing })
        assertTrue(specs.all { it.parameters.length == authored.length && it.parameters.arcSpan == authored.arcSpan })
        assertTrue(specs.all { it.parameters.width == authored.width && it.parameters.particleSize == authored.particleSize })
        assertTrue(specs.all { it.parameters.spacing == authored.spacing && it.parameters.color == authored.color })
    }

    @Test
    fun `reverse choreography swaps slash traversal direction`() {
        val authored = SlashEditorParameters(arcSpan = 80.0, durationTicks = 4)
        val forward = SlashEditorPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0), authored)
        val reverse = SlashEditorPreview.create(Pos.ZERO, Vec(0.0, 0.0, 1.0), authored, reverseDraw = true)
        val forwardSink = RecordingParticleSink()
        val reverseSink = RecordingParticleSink()

        forward.emit(0, forwardSink)
        reverse.emit(0, reverseSink)

        assertNotEquals(forwardSink.spawns.map { it.position }, reverseSink.spawns.map { it.position })
    }

    @Test
    fun `skill3 authored slash transforms its complete pulse and finisher geometry locally`() {
        val origin = Pos(8.0, 12.0, -4.0)
        val authored = SlashEditorParameters(
            originY = 1.3,
            forwardOffset = 2.1,
            arcSpan = 84.0,
            tilt = 17.0,
            yaw = 23.0,
            laneCount = 3,
            laneSpacing = 0.7,
            durationTicks = 2,
        )
        val specs = (1..4).map { skill3SlashPulseSpec(authored, it) } + Skill3SlashPulseSpec(authored, false)
        val directions = listOf(
            Vec(0.0, 0.0, 1.0),
            Vec(0.0, 1.0, 0.0),
            Vec(0.0, -1.0, 0.0),
            Vec(1.0, 0.0, 0.0),
            Vec(1.0, 2.0, 3.0),
        )

        fun positions(direction: Vec, spec: Skill3SlashPulseSpec): List<Point> {
            val effect = SlashEditorPreview.createSkill3(origin, direction, spec.parameters, spec.reverseDraw)
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { effect.emit(it, sink) }
            return sink.spawns.map { it.position }
        }

        specs.forEach { spec ->
            val baseline = positions(directions.first(), spec)
            val baselineFrame = ParticleTransform.fromDirection(origin, directions.first())
            directions.drop(1).forEach { direction ->
                val transform = ParticleTransform.fromDirection(origin, direction)
                val actual = positions(direction, spec)
                assertEquals(baseline.size, actual.size)
                baseline.zip(actual).forEach { (localPoint, worldPoint) ->
                    val expected = transform.localPoint(baselineFrame.worldPoint(localPoint))
                    assertEquals(expected.x(), worldPoint.x(), absoluteTolerance = 0.000001)
                    assertEquals(expected.y(), worldPoint.y(), absoluteTolerance = 0.000001)
                    assertEquals(expected.z(), worldPoint.z(), absoluteTolerance = 0.000001)
                }
            }
        }

        val fallback = positions(Vec(Double.NaN, 0.0, 1.0), specs.last())
        assertEquals(positions(directions.first(), specs.last()), fallback)
    }
}
