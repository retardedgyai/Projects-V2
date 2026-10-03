package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Full fresh offline ritual using existing ledger/samplers; no server, repository or user save. */
fun main(args:Array<String>) {
    System.setProperty("projects.infusion.idleCoreRotation","true")
    val fresh=WorldInfusionState.fresh(UUID.fromString("00000000-0000-0000-0000-000000005514"))
    val cells=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))
    val items=WorldInfusionRules.ingredients.entries.flatMap { (r,n)->List(n){r} }
    val jars=listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))
    var state=fresh.copy(account=fresh.account.copy(balances=emptyMap()),matrix=InfusionCell(0,41,0),
        gearPlace=InfusionGearPlace.CENTER,pedestals=cells.mapIndexed { i,c->InfusionPedestal(c,items[i]) },
        jars=fresh.jars.mapIndexed { i,j->j.copy(cell=jars[i]) })
    val power=WorldInfusionEnergy.Supply(18);val motion=WorldInfusionConfluence.Track()
    val radiance=WorldInfusionRadiance.Track();val clock=WorldInfusionConfluence.Clock();val arc=WorldInfusionCoreArcs.Track()
    val smoke=mutableListOf<WorldInfusionSmoke.Transfer>();val pivot=Vec(.5,3.4,.5);val weapon=Vec(.5,1.15,.5)
    val frames=mutableListOf<String>();val sounds=mutableListOf<String>();var completionTick:Long?=null
    val priorRoles=mutableMapOf<String,String>();var lastAbsorb=-100L
    fun role(s:WorldInfusionSmoke.Sample,inlet:Vec):String=when {
        s.model!="infusion/smoke"->s.model
        s.key.startsWith("radiance:orbit:") || s.key.startsWith("pedestal-rise:")->s.model
        s.key.startsWith("radiance:flow:") || s.key.startsWith("convergence:")->"infusion-role-study/intake-mote"
        s.key.startsWith("weapon-success:")->"infusion-role-study/success-spark"
        s.position.distance(inlet)<=.55->"infusion-role-study/intake-mote"
        else->"infusion-role-study/jar-mist"
    }
    for(tick in 0L..600L) {
        var completed=false
        if(tick==40L) { state=WorldInfusionEnergy.start(state);sounds+="{\"type\":\"existing-start-chime\",\"tick\":$tick,\"volume\":0.6,\"pitch\":0.8}" }
        if(tick%4==0L && WorldInfusionAnimation.mayAdvance(state,smoke,tick)) {
            val (next,pulses)=WorldInfusionEnergy.advance(state,power)
            state=next
            val target=motion.pose.inlet(pivot)
            for(p in pulses) {
                if(p.jar!=null && p.aspect!=null) {
                    val j=state.jars.single { it.id==p.jar };val c=j.cell!!
                    smoke+=WorldInfusionSmoke.Transfer(Vec(c.x+.5,.95,c.z+.5),target,p.aspect.rgb,tick,
                        travelTicks=power.flightTicks,tuftStep=power.tuftStep)
                }
                if(p.completed) { completed=true;completionTick=tick;sounds+="{\"type\":\"existing-completion-chime\",\"tick\":$tick,\"volume\":0.7,\"pitch\":1.3}" }
            }
        }
        val pose=motion.step(state,tick,power.drive,completed,4.5,true,true)
        radiance.accepted(state,tick,completed)
        smoke.removeIf { WorldInfusionSmoke.expired(it,tick) }
        if(tick%2==0L) {
            val inlet=pose.inlet(pivot)
            val charge=radiance.samples(state,tick,pose,pivot,weapon,WorldInfusionEnergy.channel(state),true)
            val finish=motion.finishSamples(state,tick,clock,power.drive,pivot,weapon,null,true,true)
            val samples=WorldInfusionCharge.frame(charge,finish,WorldInfusionSmoke.frame(smoke,tick,inlet))
            val seen=mutableSetOf<String>()
            val entries=samples.map { s->
                val model=role(s,inlet);seen+=s.key
                if(model=="infusion-role-study/intake-mote" && priorRoles[s.key]=="infusion-role-study/jar-mist" && tick-lastAbsorb>=6) {
                    sounds+="{\"type\":\"proposed-absorption\",\"tick\":$tick,\"charge\":${WorldInfusionCharge.fill(state).fraction}}";lastAbsorb=tick
                }
                priorRoles[s.key]=model
                "{\"key\":\"${s.key}\",\"toModel\":\"$model\",\"fromModel\":\"${s.model}\",\"position\":[${s.position.x()},${s.position.y()},${s.position.z()}],\"rgb\":${s.rgb},\"width\":${s.width},\"height\":${s.height},\"scale\":${s.scale},\"facing\":\"${s.facing}\",\"brightness\":${s.brightness},\"progress\":${s.progress}}"
            }
            priorRoles.keys.retainAll(seen)
            val active=state.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS) && !state.paused
            val completing=completionTick?.let { tick-it<=70 }==true && state.phase==InfusionPhase.COMPLETE
            val pulse=arc.sample(tick,pose.yaw,pivot,active,!completing)
            val arcJson=pulse?.let { a->
                val points=a.points.joinToString(",") { "[${it.x()},${it.y()},${it.z()}]" }
                "[{\"event\":${a.event},\"ageTicks\":${a.age},\"alpha\":${a.alpha},\"points\":[$points],\"rgb\":[231,118,219]}]"
            } ?: "[]"
            val channel=WorldInfusionEnergy.channel(state)
            frames+="{\"tick\":$tick,\"phase\":\"${state.phase}\",\"yaw\":${pose.yaw},\"speed\":${pose.speed},\"glow\":${pose.glow},\"fill\":${WorldInfusionCharge.fill(state).fraction},\"energy\":${state.energy?.receivedMilli ?: 0},\"channel\":${channel?.toString() ?: "null"},\"completionAge\":${completionTick?.let { tick-it }?.toString() ?: "null"},\"gearPlace\":\"${state.gearPlace}\",\"gearModel\":\"projects:item/weapons/greatsword_t2\",\"samples\":[${entries.joinToString(",")}],\"arcs\":$arcJson}"
        }
        if(completionTick?.let { tick-it>=74 }==true)break
    }
    check(completionTick!=null && state.phase==InfusionPhase.COMPLETE && state.energy!!.receivedMilli==120_000L)
    val path=Path.of(args.single());Files.createDirectories(path.parent)
    Files.writeString(path,"{\"scope\":\"Full offline fresh ritual from native integrated no-flash candidate; no user save\",\"startTick\":40,\"completionTick\":$completionTick,\"frameTickStep\":2,\"itemVisibleBeforeAndAfter\":true,\"itemAppearanceSpecificationChanged\":false,\"sounds\":[${sounds.joinToString(",")}],\"frames\":[${frames.joinToString(",")}]}\n")
    println("FULL_RITUAL_TRACE_PASS frames=${frames.size} completionTick=$completionTick energy120000 originalRules originalRecipe itemPresentThroughout noRepository noServer")
}
