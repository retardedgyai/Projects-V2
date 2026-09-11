package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.*
import kotlin.test.*

class CoreWarriorParticlesTest {
    private fun effect(id:String,phase:CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE,pulse:Int=0,direction:Vec=Vec(0.0,0.0,1.0),radius:Double?=null):CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon==id }
        return CoreSkillEffect(CoreClass.WARRIOR,if(radius==null)skill else skill.copy(radius=radius),Vec.ZERO,direction,phase,pulse)
            .also { it.solidCompanion=true }
    }
    private fun frame(e:CoreSkillEffect,tick:Int)=RecordingParticleSink().also { e.emit(tick,it) }.spawns
    @Test fun `all ten skills have distinct finite bounded companions without editing terrain`() {
        val signatures=CoreSkillCatalog.skills(CoreClass.WARRIOR).map { s ->
            val e=effect(s.icon)
            val frames=(0 until e.durationTicks).map { t -> frame(e,t).also { particles ->
                assertTrue(particles.size<=60,"${s.icon} $t ${particles.size}")
                assertTrue(particles.all { listOf(it.position.x(),it.position.y(),it.position.z()).all(Double::isFinite) })
            } }
            assertTrue(frames.sumOf { it.size }>30,s.icon)
            assertTrue(frame(e,-1).isEmpty());assertTrue(frame(e,e.durationTicks).isEmpty())
            frames.map { it.map { p -> p.position } }
        }
        // Dash/breach deliberately share their displacement language; guard/voice/ground/turn do not.
        assertTrue(signatures.distinct().size>=7)
    }
    @Test fun `flag range is exact at grant and never persists as a fake aura`() {
        for(radius in listOf(3.0,7.5,10.5)) {
            val e=effect("war_banner",radius=radius)
            val boundary=frame(e,0)
            assertEquals(40,boundary.size)
            assertTrue(boundary.all { abs(hypot(it.position.x(),it.position.z())-radius)<1e-8 })
            assertTrue(boundary.all { it.importance==ParticleImportance.COMBAT_FEEDBACK })
            assertTrue(frame(e,12).isEmpty())
            assertTrue(frame(effect("war_banner",CoreSkillVisualPhase.CONTACT),0).isEmpty())
        }
    }
    @Test fun `ground uses falling stone and thrust uses directional air rather than a second slash`() {
        assertTrue(frame(effect("slam"),4).any { it.particle is Particle.Block })
        assertTrue(frame(effect("war_breach"),4).any { it.particle==Particle.CLOUD })
        val forward=frame(effect("war_breach"),6).map { it.position }
        val east=frame(effect("war_breach",direction=Vec(1.0,0.0,0.0)),6).map { it.position }
        forward.zip(east).forEach { (a,b) -> assertEquals(a.z(),b.x(),1e-8);assertEquals(-a.x(),b.z(),1e-8) }
        assertTrue(forward.all { it.z()>=0 })
        assertFalse(frame(effect("whirl",pulse=0),4).map { it.position }==frame(effect("whirl",pulse=2),4).map { it.position })
    }
    @Test fun `mark countdown is derived from actual state and ends on consume reset and expiry`() {
        val state=CoreClassState();val id=java.util.UUID.randomUUID()
        assertEquals(0L,state.markRemaining(id,0));state.mark(id,10)
        assertEquals(120L,state.markRemaining(id,10));assertEquals(1L,state.markRemaining(id,130))
        assertEquals(0L,state.markRemaining(id,131));state.mark(id,140);state.consumeMark(id,141)
        assertEquals(0L,state.markRemaining(id,141));state.mark(id,150);state.reset()
        assertEquals(0L,state.markRemaining(id,150))
    }
}
