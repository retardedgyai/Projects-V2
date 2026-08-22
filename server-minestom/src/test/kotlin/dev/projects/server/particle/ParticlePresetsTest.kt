package dev.projects.server.particle

import net.minestom.server.particle.Particle
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ParticlePresetsTest {
    private val context = ParticlePresetParameters.at(Pos.ZERO, Vec(0.3, 0.4, 0.8))

    @Test
    fun `catalogue has unique ids and the complete 35 preset arsenal`() {
        assertTrue(ParticlePresetRegistry.all.size >= 35)
        assertEquals(ParticlePresetRegistry.all.size, ParticlePresetRegistry.all.map { it.id }.toSet().size)
        assertTrue(ParticlePresetRegistry.all.all { it.id.startsWith("projects:") })
        assertTrue(ParticlePresetRegistry.list("combat").size >= 15)
    }

    @Test
    fun `every preset instantiates and emits finite geometry`() {
        ParticlePresetRegistry.all.forEach { preset ->
            val effect = preset.create(context)
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }
            assertTrue(effect.durationTicks >= 1, preset.id)
            assertTrue(sink.spawns.isNotEmpty(), preset.id)
            assertTrue(sink.spawns.all { spawn ->
                spawn.position.x().isFinite() && spawn.position.y().isFinite() && spawn.position.z().isFinite() &&
                    spawn.offset.x().isFinite() && spawn.offset.y().isFinite() && spawn.offset.z().isFinite() &&
                    spawn.speed.isFinite()
            }, preset.id)
        }
    }

    @Test
    fun `numeric values clamp and invalid values fall back safely`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/slash_light"])
        val effect = preset.create(context.with("length", Double.NaN).with("duration", 9999.0).with("colorPrimary", 0x1ffffff))
        assertTrue(effect.durationTicks <= 40)
        val parsed = parseParticlePresetOverrides(preset, arrayOf("length=999", "colorPrimary=#00ff00"))
        assertEquals(null, parsed.error)
        val sink = RecordingParticleSink()
        effect.emit(0, sink)
        assertTrue(sink.spawns.isNotEmpty())
    }

    @Test
    fun `same seed produces the same preset output`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/lightning_strike"])
        fun emit(): List<ParticleSpawn> {
            val sink = RecordingParticleSink()
            preset.create(context.with("seed", 77.0)).emit(0, sink)
            return sink.spawns
        }
        assertEquals(emit(), emit())
    }

    @Test
    fun `twin blades swing emits a finite ribbon with visible width`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])
        val sink = RecordingParticleSink()
        preset.create(context.with("angle", 35.0).with("length", 2.4).with("duration", 3.0)).emit(0, sink)

        val (_, right, up) = basis(context.direction)
        val angle = Math.toRadians(35.0)
        val slashDirection = right.mul(cos(angle)).add(up.mul(sin(angle)))
        val distances = sink.spawns.map { spawn ->
            val relative = Vec(spawn.position.x(), spawn.position.y(), spawn.position.z())
            relative.sub(slashDirection.mul(relative.dot(slashDirection))).length()
        }
        assertTrue(distances.maxOrNull()!! >= 0.12)
    }

    @Test
    fun `twin blades swing advances along a crescent instead of replaying one line`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])
        val effect = preset.create(context.with("angle", 35.0).with("duration", 3.0))
        val first = RecordingParticleSink().also { effect.emit(0, it) }.spawns.map { it.position }
        val last = RecordingParticleSink().also { effect.emit(2, it) }.spawns.map { it.position }
        fun centroid(points: List<net.minestom.server.coordinate.Point>) = points.reduce { a, b ->
            a.add(b.x(), b.y(), b.z())
        }.let { sum -> sum.div(points.size.toDouble()) }
        assertTrue(centroid(first).distance(centroid(last)) > 0.1)
    }

    @Test
    fun `twin blades authored swing has a readable reach`() {
        val start = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.9, 0.0, radiusFactor = 0.55)
        val end = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.9, 1.0, radiusFactor = 0.55)
        assertTrue(start.distance(end) > 2.1)
    }

    @Test
    fun `twin blades swing mirrors its hand-side origin`() {
        val positive = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.1, 0.0)
        val negative = twinBladesArcPoint(Pos.ZERO, context.direction, -35.0, 2.1, 0.0)
        assertTrue(positive.distance(negative) > 0.3)
    }

    @Test
    fun `twin blades combo steps use different geometry families`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])

        fun emittedPositions(step: Int): List<net.minestom.server.coordinate.Point> {
            val effect = preset.create(context.with("step", step).with("duration", 3.0))
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }
            return sink.spawns.map { it.position }
        }

        val quickDraw = emittedPositions(1)
        val reverseHook = emittedPositions(2)
        val scissorCross = emittedPositions(3)
        assertNotEquals(quickDraw, reverseHook)
        assertNotEquals(reverseHook, scissorCross)
        assertNotEquals(quickDraw, scissorCross)
    }

    @Test
    fun `twin blades reverse hook endpoint accent matches hook endpoint`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])
        val length = 2.9
        val angle = 35.0
        val expected = twinBladesReverseHookEndpoint(Pos.ZERO, context.direction, angle, length)
        val effect = preset.create(context.with("step", 2).with("angle", angle).with("length", length).with("duration", 3.0))
        val sink = RecordingParticleSink()
        effect.emit(effect.durationTicks - 1, sink)

        assertTrue(sink.spawns.any { it.particle == Particle.END_ROD && it.position.distance(expected) < 0.000001 })
    }

    @Test
    fun `twin blades finisher delays its second stroke and accent`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/aa_swing"])
        val effect = preset.create(context.with("step", 3).with("duration", 3.0))
        val ticks = (0 until effect.durationTicks).map { tick ->
            RecordingParticleSink().also { effect.emit(tick, it) }.spawns
        }

        assertEquals(3, ticks.size)
        assertTrue(ticks.all { it.isNotEmpty() })
        assertNotEquals(ticks[0], ticks[1])
        assertTrue(ticks[2].size > ticks[0].size)
    }

    @Test
    fun `blade storm pulses use distinct geometry and finisher avoids smoke`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/skill2_pulse"])
        fun emitted(pulse: Int): List<ParticleSpawn> {
            val effect = preset.create(context.with("pulse", pulse).with("duration", 2.0))
            return buildList {
                repeat(effect.durationTicks) { tick ->
                    addAll(RecordingParticleSink().also { effect.emit(tick, it) }.spawns)
                }
            }
        }

        val pulses = (1..4).map(::emitted)
        assertTrue(pulses.all { it.isNotEmpty() })
        assertTrue(pulses.zipWithNext().all { (first, second) -> first != second })

        val finisher = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/skill2_finisher"])
        val finisherEffect = finisher.create(context.with("duration", 6.0))
        val sink = RecordingParticleSink()
        repeat(finisherEffect.durationTicks) { tick -> finisherEffect.emit(tick, sink) }
        assertTrue(sink.spawns.isNotEmpty())
        assertTrue(sink.spawns.none { it.particle == Particle.CLOUD })
    }

    @Test
    fun `skill3 pulses use distinct slash grammar and finisher stays oversized`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/skill3_pulse"])
        fun emitted(pulse: Int): List<ParticleSpawn> {
            val effect = preset.create(context.with("pulse", pulse).with("duration", 2.0))
            return buildList {
                repeat(effect.durationTicks) { tick ->
                    addAll(RecordingParticleSink().also { effect.emit(tick, it) }.spawns)
                }
            }
        }

        val pulses = (1..4).map(::emitted)
        assertTrue(pulses.all { it.isNotEmpty() })
        assertTrue(pulses.zipWithNext().all { (first, second) -> first != second })

        val finisher = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/skill3_finisher"])
        val effect = finisher.create(context)
        val sink = RecordingParticleSink()
        repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }
        assertEquals(6, effect.durationTicks)
        assertTrue(sink.spawns.isNotEmpty())
        assertTrue(sink.spawns.none { it.particle == Particle.CLOUD })
    }

    @Test
    fun `skill3 hit presets dispatch through scheduler and particle manager`() {
        val presetIds = listOf(
            "projects:class/twin_blades/skill3_hit",
            "projects:class/twin_blades/skill3_pulse",
            "projects:class/twin_blades/skill3_finisher",
        )

        presetIds.forEach { id ->
            val preset = requireNotNull(ParticlePresetRegistry[id])
            val scheduler = ParticleAnimationScheduler()
            val manager = ParticleManager()
            val sink = RecordingParticleSink()
            val viewer = ParticleViewer(Pos.ZERO)
            val effectSink = manager.sink(viewer, sink, "preset:$id")
            val effect = preset.create(context)

            scheduler.start(effect, effectSink, id = id)
            repeat(effect.durationTicks) {
                manager.beginTick()
                scheduler.tick()
                manager.flush()
            }

            assertTrue(sink.spawns.isNotEmpty(), id)
            assertTrue(manager.counters.dispatched > 0, id)
            assertEquals(0, scheduler.activeAnimationCount, id)
        }
    }

    @Test
    fun `twin blades arc has visible curvature and uses its angle`() {
        val start = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.1, 0.0)
        val middle = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.1, 0.5)
        val end = twinBladesArcPoint(Pos.ZERO, context.direction, 35.0, 2.1, 1.0)
        val chordMidpoint = start.add((end.x() - start.x()) * 0.5, (end.y() - start.y()) * 0.5, (end.z() - start.z()) * 0.5)
        assertTrue(middle.distance(chordMidpoint) >= 0.15)

        val differentAngle = twinBladesArcPoint(Pos.ZERO, context.direction, 70.0, 2.1, 0.5)
        assertTrue(middle.distance(differentAngle) > 0.1)
    }

    @Test
    fun `skill1 travel stomp and escape presets emit finite feedback`() {
        listOf(
            "projects:class/twin_blades/skill1_travel",
            "projects:class/twin_blades/skill1_stomp",
            "projects:class/twin_blades/skill1_escape",
        ).forEach { id ->
            val preset = requireNotNull(ParticlePresetRegistry[id])
            val effect = preset.create(context.with("seed", 62.0))
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { tick -> effect.emit(tick, sink) }

            assertTrue(sink.spawns.isNotEmpty(), id)
            assertTrue(sink.spawns.all { spawn ->
                listOf(spawn.position.x(), spawn.position.y(), spawn.position.z(), spawn.offset.x(), spawn.offset.y(), spawn.offset.z(), spawn.speed.toDouble())
                    .all { it.isFinite() }
            }, id)
        }
    }

    @Test
    fun `skill1 stomp compresses before its outward burst`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/skill1_stomp"])
        val effect = preset.create(context)
        val compression = RecordingParticleSink().also { effect.emit(0, it) }.spawns
        val burst = RecordingParticleSink().also { effect.emit(2, it) }.spawns

        assertTrue(compression.isNotEmpty())
        assertTrue(burst.isNotEmpty())
        assertTrue(burst.size > compression.size)
        assertEquals(5, effect.durationTicks)
    }

    @Test
    fun `skill1 stomp scheduler has no residual animation after completion`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:class/twin_blades/skill1_stomp"])
        val effect = preset.create(context)
        val scheduler = ParticleAnimationScheduler()

        scheduler.start(effect, RecordingParticleSink())
        repeat(effect.durationTicks) { scheduler.tick() }

        assertEquals(0, scheduler.activeAnimationCount)
    }

    @Test
    fun `override parser rejects unknown and malformed parameters`() {
        val preset = requireNotNull(ParticlePresetRegistry["projects:combat/slash_light"])
        assertNotNull(parseParticlePresetOverrides(preset, arrayOf("length=2")))
        assertTrue(parseParticlePresetOverrides(preset, arrayOf("unknown=2")).error != null)
        assertTrue(parseParticlePresetOverrides(preset, arrayOf("length=NaN")).error != null)
    }
}
