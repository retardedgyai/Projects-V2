package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WorldInfusionChargeTest {
    private val owner=UUID.fromString("39393939-1111-2222-3333-444444444444")
    private fun ready():WorldInfusionState {
        var s=WorldInfusionRules.placeMatrix(WorldInfusionState.fresh(owner),InfusionCell(0,41,0))
        for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
            val c=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))[i]
            s=WorldInfusionRules.placePedestal(s,c);s=WorldInfusionRules.material(s,c,r)
        }
        for((i,j) in s.jars.withIndex())s=WorldInfusionRules.placeJar(s,j.id,listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))[i])
        return WorldInfusionRules.placeGear(s,s.gear.identity.id)
    }
    @Test fun `charge brightness density and descending injection are owned by accepted Essentia not elapsed time`() {
        var s=WorldInfusionEnergy.start(ready())
        val track=WorldInfusionConfluence.Track();val pivot=Vec(.5,44.4,.5);val weapon=Vec(.5,42.15,.5)
        val zero=WorldInfusionEnergy.Supply(0)
        repeat(100) { tick->
            s=WorldInfusionEnergy.advance(s,zero).first
            assertEquals(0.0,WorldInfusionCharge.fill(s).fraction)
            assertTrue(WorldInfusionCharge.samples(s,tick.toLong(),track.pose,pivot,weapon,null,false).isEmpty())
        }
        val fills=mutableListOf<WorldInfusionCharge.Fill>()
        for(total in listOf(3,6,7)) {
            while(s.supplied.values.sum()<total)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
            val fill=WorldInfusionCharge.fill(s);fills+=fill
            assertEquals(total/7.0,fill.fraction);assertEquals((16*total/7.0).toInt(),fill.motes)
            val frozen=WorldInfusionCodec.encode(s)
            for(t in listOf(0L,100L,10000L)) {
                s=WorldInfusionEnergy.advance(s,zero).first
                assertEquals(frozen,WorldInfusionCodec.encode(s))
                val pose=track.step(s.copy(paused=true),t,zero.drive,chargedCore=true)
                assertEquals(fill.baseline,pose.glow)
                assertTrue(WorldInfusionCharge.samples(s,t,pose,pivot,weapon,null,false).isEmpty())
            }
        }
        assertTrue(fills.zipWithNext().all { (a,b)->a.baseline+a.pulseAmplitude < b.baseline-b.pulseAmplitude })
        while(s.consumed!=WorldInfusionRules.ingredients)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        repeat(3) { s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first }
        val before=WorldInfusionCodec.encode(s);val u=WorldInfusionEnergy.channel(s)!!
        val fx=WorldInfusionCharge.samples(s,99,track.pose,pivot,weapon,u,true)
        assertEquals(16,fx.count { it.key.startsWith("core-charge:") })
        val stream=fx.filter { it.key.startsWith("core-injection:") }
        assertEquals(24,stream.size);assertTrue(stream.first().position.y()>stream.last().position.y())
        assertTrue(stream.all { it.rgb!=0xffffff && it.scale<=1.1f && it.position.y() in 42.15..44.0 })
        assertEquals(before,WorldInfusionCodec.encode(s))
        assertTrue(WorldInfusionCharge.samples(WorldInfusionRules.cancel(s),100,track.pose,pivot,weapon,u,true).isEmpty())
    }
    @Test fun `export actual Essentia charge stages injection stop resume and cancellation within existing cap`() {
        val dir=Path.of(".tools/world-infusion-evidence");Files.createDirectories(dir)
        val scenes=mutableListOf<String>();val ends=mutableMapOf<String,Long>()
        for(name in listOf("zero","low","high","interrupt","cancel","shortage")) {
            val repo=WorldInfusionRepository(Files.createTempDirectory("energy-trace-"))
            var s=repo.save(0,ready());val initial=s.gear
            val clock=WorldInfusionConfluence.Clock();val track=WorldInfusionConfluence.Track()
            val transfers=mutableListOf<WorldInfusionSmoke.Transfer>();val frames=mutableListOf<String>();val events=mutableListOf<String>()
            var completed:Long?=null;var stopped:Long?=null;var completions=0;var peak=0;var frozen:String?=null
            for(tick in 0L..620L) {
                if(tick==20L)s=repo.save(s.account.revision,WorldInfusionEnergy.start(s))
                val p=WorldInfusionEnergy.Supply(when { name=="zero"->0;name=="low"->6;name=="interrupt" && tick in 56..159->0;else->18 })
                if(name=="cancel" && tick==166L) { s=repo.save(s.account.revision,WorldInfusionRules.cancel(s));stopped=tick }
                if(name=="shortage" && tick==56L)s=repo.save(s.account.revision,WorldInfusionRules.removeJar(s,s.jars[2].id))
                var complete=false
                if(tick>20 && tick%4==0L && WorldInfusionAnimation.mayAdvance(s,transfers,tick)) {
                    val (next,pulses)=WorldInfusionEnergy.advance(s,p)
                    if(next!==s) {
                        s=repo.save(s.account.revision,next)
                        for(v in pulses) {
                            if(v.jar!=null) {
                                val j=s.jars.single { it.id==v.jar };val c=j.cell!!
                                transfers+=WorldInfusionSmoke.Transfer(Vec(c.x+.5,41.95,c.z+.5),track.pose.inlet(Vec(.5,44.4,.5)),v.aspect!!.rgb,tick,travelTicks=p.flightTicks,tuftStep=p.tuftStep)
                                events+="{\"tick\":$tick,\"kind\":\"jar\",\"aspect\":\"${j.aspect}\"}"
                            }
                            if(v.ingredient!=null)events+="{\"tick\":$tick,\"kind\":\"ingredient\",\"x\":${v.ingredient!!.x},\"z\":${v.ingredient!!.z}}"
                            if(v.completed) { complete=true;completed=tick;completions++;events+="{\"tick\":$tick,\"kind\":\"complete\"}" }
                        }
                    }
                }
                if(s.paused && stopped==null)stopped=tick
                if(p.perSecond==0 || s.paused || name=="cancel" && stopped!=null)for(i in transfers.indices)transfers[i]=transfers[i].copy(releaseUntil=minOf(transfers[i].releaseUntil,tick))
                if(name=="interrupt" && tick==56L)frozen=WorldInfusionCodec.encode(s)
                if(name=="interrupt" && tick in 56..159)assertEquals(frozen,WorldInfusionCodec.encode(s))
                if(name=="interrupt" && tick==100L) { s=repo.load(owner)!!;assertEquals(frozen,WorldInfusionCodec.encode(s)) }
                val visual=if(p.perSecond==0 && s.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS))s.copy(paused=true) else s
                val pose=track.step(visual,tick,p.drive,complete,p.perSecond/18.0*4.5,chargedCore=true)
                val channel=if(p.perSecond>0)WorldInfusionEnergy.channel(s) else null
                val fx=track.finishSamples(s,tick,clock,p.drive,Vec(.5,44.4,.5),Vec(.5,42.15,.5),null,true,true)
                val seal=track.pedestalGlow(s,tick)
                val charge=WorldInfusionCharge.samples(s,tick,pose,Vec(.5,44.4,.5),Vec(.5,42.15,.5),channel,p.perSecond>0)
                if(s.paused || p.perSecond==0 || s.phase in setOf(InfusionPhase.READY,InfusionPhase.COMPLETE))assertTrue(charge.isEmpty())
                val pts=WorldInfusionCharge.frame(charge,fx,WorldInfusionSmoke.frame(transfers,tick,pose.inlet(Vec(.5,44.4,.5))));peak=maxOf(peak,pts.size+if(seal>.02)1 else 0)
                if(name in listOf("zero","cancel","shortage"))assertEquals(0.0,seal)
                if(tick%3==0L) {
                    val samples=pts.joinToString(",") { "[${it.position.x()},${it.position.y()-41},${it.position.z()},${it.rgb},${it.scale},${it.progress}]" }
                    val items=s.pedestals.joinToString(",") { if(it.item==null)"null" else "\"${it.item}\"" }
                    frames+="{\"tick\":$tick,\"phase\":\"${s.phase}\",\"paused\":${s.paused},\"power\":${p.perSecond},\"required\":120.0,\"received\":${(s.energy?.receivedMilli ?: 0)/1000.0},\"remaining\":${(s.energy?.remainingMilli ?: 120000)/1000.0},\"yaw\":${pose.yaw},\"speed\":${pose.speed},\"glow\":${pose.glow},\"channel\":${channel ?: "null"},\"chargeFraction\":${WorldInfusionCharge.fill(s).fraction},\"chargeMotes\":${charge.count { it.key.startsWith("core-charge:") }},\"injectionMotes\":${charge.count { it.key.startsWith("core-injection:") }},\"pedestalGlow\":$seal,\"weaponMod\":\"${s.gear.affixes[0].stone.modId}\",\"amounts\":[${s.jars.joinToString(","){it.amount.toString()}}],\"items\":[$items],\"samples\":[$samples]}"
                }
            }
            assertTrue(peak<=65);assertEquals(0.0,track.pose.speed)
            assertTrue(WorldInfusionSmoke.frame(transfers,620).isEmpty())
            if(name in setOf("low","high","interrupt")) {
                assertEquals(1,completions);assertEquals(120_000,s.energy!!.receivedMilli);assertEquals(5,s.jars.sumOf { it.amount })
                assertEquals(initial.identity,s.gear.identity);assertEquals(initial.affixes[1],s.gear.affixes[1]);ends[name]=completed!!
                val first=transfers.minOf { it.started };assertEquals(3,transfers.count { it.started==first })
            } else {
                assertEquals(0,completions);assertEquals(initial.identity,s.gear.identity);assertEquals(initial.affixes,s.gear.affixes)
                if(name=="zero") { assertEquals(0,s.energy!!.receivedMilli);assertTrue(transfers.isEmpty());assertEquals(12,s.jars.sumOf { it.amount }) }
            }
            scenes+="{\"scenario\":\"$name\",\"completedTick\":${completed ?: "null"},\"stoppedTick\":${stopped ?: "null"},\"peakSamples\":$peak,\"events\":[${events.joinToString(",")}],\"frames\":[${frames.joinToString(",")}] }"
        }
        assertTrue(ends.getValue("high")<ends.getValue("low"));assertTrue(ends.getValue("interrupt")>ends.getValue("high"))
        Files.writeString(dir.resolve("charge-animation-trace.json"),"{\"frameTicks\":3,\"unitIsDemo\":true,\"requiredRecipeEnergy\":120,\"powerInputBoundsDemo\":[0,18],\"kernels\":[\"WorldInfusionCharge\",\"WorldInfusionEnergy\",\"WorldInfusionConfluence\",\"WorldInfusionSmoke\"],\"scenes\":[${scenes.joinToString(",")}]}\n")
    }
}
