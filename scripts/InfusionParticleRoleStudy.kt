package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Offline export only: invokes installed game samplers; never starts a server or reads user saves. */
fun main(args:Array<String>) {
    val target=Path.of(args.single())
    Files.createDirectories(target.parent)
    val fresh=WorldInfusionState.fresh(UUID.fromString("00000000-0000-0000-0000-000000005512"))
    val matrix=InfusionCell(0,41,0)
    val working=fresh.copy(matrix=matrix,phase=InfusionPhase.ESSENTIA,gearPlace=InfusionGearPlace.CENTER,
        supplied=InfusionAspect.entries.associateWith { 1 })
    val charged=working.copy(phase=InfusionPhase.INGREDIENTS,supplied=WorldInfusionRules.cost,
        consumed=WorldInfusionRules.ingredients)
    val complete=charged.copy(phase=InfusionPhase.COMPLETE,gearPlace=InfusionGearPlace.OUTPUT)
    val pivot=Vec(.5,3.4,.5);val weapon=Vec(.5,1.15,.5)
    val supply=WorldInfusionEnergy.Supply(18)
    val frames=mutableListOf<String>()
    var changes=0;var preservedLarge=0
    val roleCounts=mutableMapOf<String,Int>()
    fun role(s:WorldInfusionSmoke.Sample,inlet:Vec?):String=when {
        s.model!="infusion/smoke"->"protected-large-effect"
        s.key.startsWith("radiance:orbit:")->"protected-core-orbit"
        s.key.startsWith("radiance:flow:") || s.key.startsWith("convergence:")->"intake-mote"
        s.key.startsWith("weapon-success:")->"success-spark"
        s.key.startsWith("pedestal-rise:")->"protected-pedestal-rise"
        else->if(inlet!=null && s.position.distance(inlet)<=.55)"intake-mote" else "jar-mist"
    }
    fun frame(scene:String,tick:Long,samples:List<WorldInfusionSmoke.Sample>,inlet:Vec?=null) {
        val entries=samples.map { original->
            val r=role(original,inlet)
            roleCounts[r]=roleCounts.getOrDefault(r,0)+1
            val updated=if(r in setOf("jar-mist","intake-mote","success-spark"))
                original.copy(model="infusion-role-study/$r") else original
            // No sample field other than its model reference may change.
            check(updated.copy(model=original.model)==original)
            if(updated.model!=original.model)changes++ else if(r=="protected-large-effect")preservedLarge++
            val p=original.position
            "{\"key\":\"${original.key}\",\"role\":\"$r\",\"fromModel\":\"${original.model}\",\"toModel\":\"${updated.model}\",\"position\":[${p.x()},${p.y()},${p.z()}],\"rgb\":${original.rgb},\"scale\":${original.scale},\"progress\":${original.progress},\"width\":${original.width},\"height\":${original.height},\"facing\":\"${original.facing}\",\"brightness\":${original.brightness}}"
        }
        check(samples.size==entries.size)
        val inletJson=inlet?.let { "[${it.x()},${it.y()},${it.z()}]" } ?: "null"
        frames+="{\"scene\":\"$scene\",\"tick\":$tick,\"count\":${samples.size},\"movingInlet\":$inletJson,\"samples\":[${entries.joinToString(",")}] }"
    }
    val jarRadiance=WorldInfusionRadiance.Track()
    val transfer=WorldInfusionSmoke.Transfer(Vec(-3.5,.95,2.5),pivot,InfusionAspect.TIDE.rgb,0,
        travelTicks=supply.flightTicks,tuftStep=supply.tuftStep)
    for(tick in 0L..24L step 2) {
        val pose=WorldInfusionAnimation.Pose(tick*4.5,4.5,.88)
        val charge=jarRadiance.samples(working,tick,pose,pivot,weapon,null,true)
        val inlet=pose.inlet(pivot)
        frame("jar-emission-and-intake",tick,WorldInfusionCharge.frame(charge,emptyList(),WorldInfusionSmoke.frame(listOf(transfer),tick,inlet)),inlet)
    }
    val radiance=WorldInfusionRadiance.Track();val confluence=WorldInfusionConfluence.Track();val clock=WorldInfusionConfluence.Clock()
    for(tick in 0L..18L step 2) {
        val pose=WorldInfusionAnimation.Pose(tick*4.5,4.5,.94)
        val channel=(.08+tick*18.0/20/20).coerceAtMost(.99)
        frame("core-to-item-injection",tick,WorldInfusionCharge.frame(
            radiance.samples(charged,tick,pose,pivot,weapon,channel,true),
            confluence.finishSamples(charged,tick,clock,supply.drive,pivot,weapon,channel,true,true),emptyList()))
    }
    radiance.accepted(complete,0,true)
    confluence.step(complete,0,supply.drive,true)
    for(tick in 0L..34L step 2) {
        val pose=WorldInfusionAnimation.Pose(0.0,0.0,.9)
        frame("completion",tick,WorldInfusionCharge.frame(
            radiance.samples(complete,tick,pose,pivot,weapon,null,true),
            confluence.finishSamples(complete,tick,clock,supply.drive,pivot,weapon,null,true,true),emptyList()))
    }
    check(changes>0 && preservedLarge>0)
    for(r in listOf("jar-mist","intake-mote","success-spark"))check(roleCounts.getValue(r)>0)
    val counts=roleCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
    Files.writeString(target,"{\"scope\":\"offline role mapping aligned with isolated native candidate; not a game capture\",\"jarIntakeDistanceBoundary\":0.55,\"boundaryTarget\":\"moving pose.inlet(pivot)\",\"onlyModelReferencesChanged\":true,\"changedSamples\":$changes,\"preservedLargeEffectSamples\":$preservedLarge,\"roleCounts\":{$counts},\"frames\":[${frames.joinToString(",")}] }\n")
    println("ROLE_STUDY_EXPORT_PASS frames=${frames.size} changedModels=$changes protectedLarge=$preservedLarge sampleCountPositionScaleTintFacingBrightnessProgressUnchanged noServer noSave")
}
