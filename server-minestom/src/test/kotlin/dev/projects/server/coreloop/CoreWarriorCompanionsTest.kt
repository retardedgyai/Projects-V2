package dev.projects.server.coreloop

import dev.projects.server.CombatTarget
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.test.*
import kotlin.math.*

class CoreWarriorCompanionsTest {
    @Test fun `force surfaces change geometry without rotating a completed image and expire`() {
        for(s in CoreSkillCatalog.skills(CoreClass.WARRIOR)) for(pulse in 0 until s.pulses) {
            val e=CoreSkillEffect(CoreClass.WARRIOR,s,Vec.ZERO,Vec(0.0,0.0,1.0),pulse=pulse)
            for(p in CoreWarriorFlourish.parts(e)) {
                val poses=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,it.toDouble()) }
                assertEquals(1,poses.map { listOf(it.yaw,it.pitch,it.roll) }.toSet().size)
                assertEquals(1,poses.map { it.offset }.toSet().size)
                assertEquals(1,poses.map { it.scale }.toSet().size)
                assertTrue(poses.map { it.model }.toSet().size>=8)
                assertTrue(poses.last().model.endsWith("_19"))
                assertFalse(CoreSkillChoreography.pose(p,p.durationTicks.toDouble()).visible)
            }
        }
    }
    @Test fun `force placement follows attack direction and spin stages are different`() {
        val skills=CoreSkillCatalog.skills(CoreClass.WARRIOR)
        for(s in skills) {
            val a=CoreWarriorFlourish.parts(CoreSkillEffect(CoreClass.WARRIOR,s,Vec.ZERO,Vec(0.0,0.0,1.0)))
            val b=CoreWarriorFlourish.parts(CoreSkillEffect(CoreClass.WARRIOR,s,Vec.ZERO,Vec(1.0,0.0,0.0)))
            for((p,q) in a.zip(b)) {
                assertEquals(p.offset.z(),q.offset.x(),1e-8)
                assertEquals(-p.offset.x(),q.offset.z(),1e-8)
                assertEquals(p.offset.y(),q.offset.y(),1e-8)
                assertEquals(PI/2,q.yaw-p.yaw,1e-8)
            }
        }
        val spin=skills.first { it.icon=="whirl" }
        val stages=(0..2).map { pulse -> CoreWarriorFlourish.parts(
            CoreSkillEffect(CoreClass.WARRIOR,spin,Vec.ZERO,Vec(0.0,0.0,1.0),pulse=pulse)).first().shape }
        assertEquals(3,stages.toSet().size)
    }
    @Test fun `warrior emits no vanilla companions and every authored model resolves`() {
        for(s in CoreSkillCatalog.skills(CoreClass.WARRIOR)) for(phase in CoreSkillVisualPhase.entries) {
            val e=CoreSkillEffect(CoreClass.WARRIOR,s,Vec.ZERO,Vec(0.0,0.0,1.0),phase).also { it.solidCompanion=true }
            val sink=RecordingParticleSink()
            repeat(e.durationTicks) { e.emit(it,sink) };assertTrue(sink.spawns.isEmpty())
            val parts=CoreWarriorCompanions.parts(e)
            assertTrue(parts.size<=10)
            for(p in parts) repeat(p.durationTicks+p.delayTicks) { t ->
                val pose=CoreSkillChoreography.pose(p,t.toDouble())
                assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"),pose.model)
                assertTrue(listOf(pose.offset.x(),pose.offset.y(),pose.offset.z()).all(Double::isFinite))
            }
        }
    }
    @Test fun `boundary radius remains fixed only for the instantaneous grant`() {
        val s=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon=="war_banner" }
        for(r in listOf(3.0,7.5,10.5)) {
            val e=CoreSkillEffect(CoreClass.WARRIOR,s.copy(radius=r),Vec.ZERO,Vec(0.0,0.0,1.0))
            val p=CoreWarriorCompanions.parts(e).single()
            repeat(12) { assertEquals(r,CoreSkillChoreography.pose(p,it.toDouble()).scale.x()*7/16,1e-8) }
            assertFalse(CoreSkillChoreography.pose(p,12.0).visible)
        }
    }
    @Test fun `mark row is screen separated from names for close tall and low targets`() {
        for(height in listOf(.9,1.8,3.6)) for(distance in listOf(.3,1.0,8.0)) {
            val target=CombatTarget(UUID.randomUUID(),Vec(0.0,height,distance),Vec(.4,height,.4))
            val eye=Vec(0.0,1.62,0.0)
            val name=target.position.add(0.0,height+.5,0.0)
            val label=CoreWarriorMarkDisplay.position(target,eye)
            val delta=label.sub(name).asVec();val view=name.sub(eye).asVec().normalize()
            assertEquals(.7,delta.length(),1e-8)
            assertEquals(0.0,delta.dot(view),1e-8)
            assertTrue(delta.y()>0)
        }
    }
    @Test fun `mark countdown uses the real state including consumption`() {
        val state=CoreClassState();val id=UUID.randomUUID()
        state.mark(id,10);assertEquals(120L,state.markRemaining(id,10))
        assertEquals(1L,state.markRemaining(id,130));assertEquals(0L,state.markRemaining(id,131))
        state.mark(id,140);state.consumeMark(id,141);assertEquals(0L,state.markRemaining(id,141))
    }
}
