package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.minestom.server.coordinate.Vec
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.*

class CoreSkillEffectTest {
    private val forward = Vec(0.0, 0.0, 1.0)
    private fun skill(job: CoreClass, id: String) = CoreSkillCatalog.skills(job).single { it.icon == id }
    private fun frame(effect: ParticleEffect, tick: Int = 0) = RecordingParticleSink().also { effect.emit(tick, it) }.spawns.toList()

    @Test fun `all seventy skills have explicit visual motifs and bounded finite frames`() {
        assertEquals(CoreSkillCatalog.artNames.toSet(), CoreSkillArt.motifs.keys)
        assertEquals(70, CoreSkillArt.motifs.size)
        for (job in CoreClass.entries) for (s in CoreSkillCatalog.skills(job)) {
            for (phase in CoreSkillVisualPhase.entries) for (pulse in 0..7) {
                val effect = CoreSkillEffect(job, s.copy(radius = 10.5), Vec.ZERO, forward, phase, pulse, rayLength = 24.0)
                repeat(effect.durationTicks) { tick ->
                    val particles = frame(effect, tick)
                    assertTrue(particles.size <= 110, "${s.icon} $phase $pulse $tick: ${particles.size}")
                    assertTrue(particles.isNotEmpty(), "${s.icon} $phase empty")
                    assertTrue(particles.all { listOf(it.position.x(), it.position.y(), it.position.z()).all(Double::isFinite) })
                }
                assertTrue(frame(effect, -1).isEmpty())
                assertTrue(frame(effect, effect.durationTicks).isEmpty())
            }
        }
    }

    @Test fun `multihit blades have distinct cuts with readable overlapping afterglow`() {
        for ((job, id) in listOf(CoreClass.ASSASSIN to "ass_ult", CoreClass.ASSASSIN to "ass_fan", CoreClass.WARRIOR to "whirl")) {
            val s = skill(job, id)
            val cuts = (0 until s.pulses).map { pulse ->
                val effect = CoreSkillEffect(job, s, Vec.ZERO, forward, pulse = pulse)
                assertTrue(effect.durationTicks in 16..40)
                val points = frame(effect)
                assertTrue(points.any { it.importance == ParticleImportance.COMBAT_FEEDBACK })
                assertTrue(points.any { it.position.x() > s.radius * .8 })
                assertTrue(points.any { it.position.x() < -s.radius * .8 })
                points.map { it.position }
            }
            assertEquals(s.pulses, cuts.distinct().size, id)
        }
    }

    @Test fun `venom is green and poison has a distinct attached damage tick`() {
        val s = skill(CoreClass.ASSASSIN, "ass_poison")
        assertEquals(0x92db43, CoreSkillArt.color(CoreClass.ASSASSIN, s))
        val at = Vec(13.0, 40.0, -4.0)
        val ambient = frame(CorePoisonEffect(at, 4, false))
        val damage = frame(CorePoisonEffect(at, 20, true))
        assertEquals(8, ambient.size)
        assertEquals(14, damage.size)
        assertTrue(ambient.all { it.position.distance(at) < 1.5 })
        assertTrue(frame(CorePoisonEffect(at, 4, false), 1).isEmpty())
    }

    @Test fun `ranged hit is visible on first frame and never extends beyond clipped ray length`() {
        for (job in CoreClass.entries) for (s in CoreSkillCatalog.skills(job).filter { it.motion == CoreSkillMotion.RAY }) {
            val effect = CoreSkillEffect(job, s, Vec.ZERO, forward, rayLength = 3.25)
            val hit = frame(effect)
            assertTrue(hit.any { abs(it.position.z() - 3.25) < .00001 }, s.icon)
            repeat(effect.durationTicks) { tick ->
                assertTrue(frame(effect, tick).all { it.position.z() in -.00001..3.25001 }, s.icon)
            }
        }
    }

    @Test fun `cleaves mark actual modified cone range not fixed old sword length`() {
        val s = skill(CoreClass.WARRIOR, "slam").copy(radius = 9.0)
        val ground = frame(CoreSkillEffect(CoreClass.WARRIOR, s, Vec.ZERO, forward)).map { it.position }
            .filter { abs(it.y() - .12) < .00001 && hypot(it.x(), it.z()) > 8.999 }
        assertTrue(ground.size >= 21)
        assertTrue(ground.all { abs(hypot(it.x(), it.z()) - 9.0) < .00001 && it.z() / 9.0 >= .349999 })
    }

    @Test fun `meteor approaches ground before pulse while frost grows upward after hit`() {
        val meteor = CoreSkillEffect(CoreClass.MAGE, skill(CoreClass.MAGE, "meteor"), Vec.ZERO, forward, CoreSkillVisualPhase.PREPARE, prepareTicks = 8)
        assertTrue(frame(meteor, 0).maxOf { it.position.y() } > frame(meteor, 7).maxOf { it.position.y() } + 3)
        val frost = frame(CoreSkillEffect(CoreClass.MAGE, skill(CoreClass.MAGE, "frost_nova"), Vec.ZERO, forward))
        assertTrue(frost.any { it.position.y() > 1.2 })
        assertTrue(frost.any { abs(hypot(it.position.x(), it.position.z()) - 4.5) < .00001 })
    }

    @Test fun `invalid origin or radius cannot produce corrupt packets`() {
        val s = skill(CoreClass.WARRIOR, "slam")
        assertTrue(frame(CoreSkillEffect(CoreClass.WARRIOR, s, Vec(Double.NaN, 0.0, 0.0), forward)).isEmpty())
        assertTrue(frame(CoreSkillEffect(CoreClass.WARRIOR, s.copy(radius = Double.NaN), Vec.ZERO, forward)).isEmpty())
    }

    @Test fun `pulse plus three contacts and poison fits owner budget without dropping hit silhouette`() {
        val sink = RecordingParticleSink()
        CoreSkillEffect(CoreClass.MAGE, skill(CoreClass.MAGE, "mage_ult"), Vec.ZERO, forward).emit(0, sink)
        repeat(3) { CoreSkillEffect(CoreClass.MAGE, skill(CoreClass.MAGE, "mage_ult"), Vec.ZERO, forward, CoreSkillVisualPhase.CONTACT).emit(0, sink) }
        CorePoisonEffect(Vec.ZERO, 20, true).emit(0, sink)
        val accepted = RecordingParticleSink()
        val manager = ParticleManager(ParticleQuality(distanceFalloffStart = 12.0, distanceFalloffEnd = 32.0),
            ParticleBudget(GreatswordVfx.MAX_PARTICLES_PER_VIEWER_TICK))
        manager.beginTick(); manager.dispatchAll(ParticleViewer(Vec.ZERO), sink.spawns, accepted)
        assertTrue(accepted.spawns.sumOf { it.count } <= 180)
        val important = sink.spawns.filter { it.importance == ParticleImportance.COMBAT_FEEDBACK }
        assertTrue(accepted.spawns.containsAll(important))
    }
}
