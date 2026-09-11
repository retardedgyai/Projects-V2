package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Fine escaping pieces plus middle-scale force surfaces; no vanilla particle styling. */
internal object CoreWarriorCompanions {
    fun owns(p:CoreCombatMeshPart)=p.shape.startsWith("war_mote_") || CoreWarriorFlourish.owns(p)
    fun boundary(p:CoreCombatMeshPart)=p.shape=="war_mote_boundary"
    fun parts(e:CoreSkillEffect):List<CoreCombatMeshPart> {
        return fineParts(e)+CoreWarriorFlourish.parts(e)
    }
    private fun fineParts(e:CoreSkillEffect):List<CoreCombatMeshPart> {
        if(e.job!=CoreClass.WARRIOR || !e.valid || e.phase==CoreSkillVisualPhase.PREPARE) return emptyList()
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x:Double,y:Double,z:Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val support=e.sceneId in CoreWarriorSupportChoreography.sceneIds
        if(e.phase==CoreSkillVisualPhase.CONTACT && support && e.sceneId!="war_guard") return emptyList()
        if(e.phase==CoreSkillVisualPhase.PULSE && e.sceneId in setOf("war_banner","war_cry"))
            return listOf(CoreCombatMeshPart("war_mote_boundary","steel",Vec(0.0,.12,0.0),
                Vec(e.radius*16/7,1.0,e.radius*16/7),durationTicks=12,
                startSize=1.0,endSize=1.0,erode=true,ground=true))
        val contact=e.phase==CoreSkillVisualPhase.CONTACT
        val ground=e.sceneId=="slam" || e.sceneId=="war_ult" && e.pulse%3==2
        val spin=e.sceneId=="whirl" && e.skill.motion==CoreSkillMotion.SPIN
        val dash=e.sceneId in setOf("dash","war_breach")
        val count=when { contact->5; support->2; ground->6; spin->6; else->4 }
        return (0 until count).map { i ->
            val a=i*2.399+e.pulse*.6
            val shape=when { contact||support->"spark"; ground->"chip"; else->"wind" }
            val origin=when {
                contact->local(0.0,1.0,0.0)
                support->local(.5,1.25,.7)
                ground->local(sin(a)*.45,.16,e.radius*.5+cos(a)*.4)
                spin->local(sin(a)*e.radius*.6,.3+(i%3)*.24,cos(a)*e.radius*.6)
                else->local((i%2*2-1)*.35,.8+i*.15,.5+i*.25)
            }
            val travel=when {
                contact->local(cos(a)*.9,sin(a)*.7,.12*i)
                support->local(0.0,.65,.2)
                ground->local(sin(a)*1.2,0.0,cos(a)*1.1)
                spin->local(sin(a+.65)*.9,.1,cos(a+.65)*.9)
                dash->local((i%2*2-1)*.65,.05,min(e.radius,CoreSkillScenes.get(e.sceneId).reach)*.55)
                else->local((i%2*2-1)*1.35,.18,e.radius*.65)
            }
            CoreCombatMeshPart("war_mote_$shape",if(i==0)"warred" else "steel",origin,
                if(shape=="wind")Vec(.35,.35,1.35) else Vec(.32,.32,.5),
                yaw=if(contact)yaw+a else yaw,pitch=if(ground)-.4 else -PI/2,
                roll=if(ground)a else 0.0,travel=travel,
                bend=if(ground)Vec(0.0,.7+i*.08,0.0) else Vec.ZERO,
                durationTicks=if(contact)8 else 10,delayTicks=if(contact)0 else i%3,
                startSize=1.0,endSize=.45,erode=true,secondary=true)
        }
    }
}
