package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.abs

fun main() {
    System.setProperty("projects.infusion.idleCoreRotation","true")
    val initial=WorldInfusionState.fresh(UUID.fromString("00000000-0000-0000-0000-000000005513"))
    val matrix=InfusionCell(0,41,0)
    val idle=initial.copy(matrix=matrix)
    val drive=WorldInfusionConfluence.Drive(1.0)
    val motion=WorldInfusionConfluence.Track()
    repeat(240) { motion.step(idle,it.toLong(),drive) }
    check(abs(motion.pose.yaw-36.0)<1e-8 && abs(motion.pose.speed-.15)<1e-8)
    val working=idle.copy(phase=InfusionPhase.ESSENTIA,gearPlace=InfusionGearPlace.CENTER,supplied=InfusionAspect.entries.associateWith { 1 })
    repeat(60) { motion.step(working,(240+it).toLong(),drive) }
    check(abs(motion.pose.speed-4.5)<1e-8)
    val complete=working.copy(phase=InfusionPhase.COMPLETE,gearPlace=InfusionGearPlace.OUTPUT,
        supplied=WorldInfusionRules.cost,consumed=WorldInfusionRules.ingredients)
    motion.step(complete,300,drive,true)
    repeat(90) { motion.step(complete,(301+it).toLong(),drive) }
    check(abs(motion.pose.speed-.15)<1e-8)
    val radiance=WorldInfusionRadiance.Track();radiance.accepted(complete,0,true)
    val pivot=Vec(.5,3.4,.5);val weapon=Vec(.5,1.15,.5)
    val completion=(0L..34L).flatMap { radiance.samples(complete,it,motion.pose,pivot,weapon,null,true) }
    check(completion.none { it.key=="radiance:flash" })
    check(completion.any { it.key.startsWith("radiance:wave:") })
    val ritual=radiance.samples(complete.copy(phase=InfusionPhase.INGREDIENTS,gearPlace=InfusionGearPlace.CENTER),4,motion.pose,pivot,weapon,.5,true)
    check(ritual.none { it.model=="infusion-radiance/halo" })
    check(ritual.any { it.key.startsWith("radiance:orbit:") } && ritual.any { it.key=="radiance:column" })
    val confluence=WorldInfusionConfluence.Track();confluence.step(complete,0,drive,true)
    val sparks=confluence.finishSamples(complete,12,WorldInfusionConfluence.Clock(),drive,pivot,weapon,null,false,true)
    check(sparks.count { it.key.startsWith("weapon-success:") }==8)
    val arcs=WorldInfusionCoreArcs.Track()
    val pulse=(0L..24L).map { tick->arcs.sample(tick,0.0,pivot,tick>=8,true) }
    check(pulse.subList(6,12).all { it?.event==0L }) // Phase change does not reset an existing pulse.
    check(pulse[12]==null && pulse[13]==null)
    val a=WorldInfusionCoreArcs.sample(8,27.0,pivot,false)!!
    for((p,q) in a.points.zipWithNext()) {
        val delta=q.sub(p);val r=WorldInfusionCoreArcs.alignZ(delta)
        val x=r[0].toDouble();val y=r[1].toDouble();val z=r[2].toDouble();val w=r[3].toDouble()
        val transformed=Vec(2*(x*z+w*y),2*(y*z-w*x),1-2*(x*x+y*y))
        check(transformed.distance(delta.normalize())<1e-6)
        check(delta.length() in .05..1.0)
    }
    check(WorldInfusionCoreArcs.sample(12,0.0,pivot,false)==null)
    println("INTEGRATED_CANDIDATE_AUDIT_PASS idle3degPerSec active90degPerSec completionReturnsToIdle flashAbsent waveAndEightSuccessSparksKept noBroadHalo pulseSurvivesPhaseChange segmentEndpointsMatchQuaternion")
}
