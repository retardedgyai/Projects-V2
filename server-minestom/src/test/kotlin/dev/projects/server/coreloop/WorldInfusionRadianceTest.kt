package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WorldInfusionRadianceTest {
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
    @Test fun `bold native shapes preserve item space zero stop saved fill and success event authority`() {
        var s=WorldInfusionEnergy.start(ready());val track=WorldInfusionRadiance.Track()
        val pivot=Vec(.5,44.4,.5);val weapon=Vec(.5,42.15,.5);val pose=WorldInfusionAnimation.Pose(0.0,0.0,.22)
        assertTrue(track.samples(s,10000,pose,pivot,weapon,null,true).isEmpty())
        while(s.supplied.values.sum()<3)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        val mid=track.samples(s,0,pose,pivot,weapon,null,true).single { it.key=="radiance:halo" }
        while(s.supplied.values.sum()<7)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        val full=track.samples(s,0,pose,pivot,weapon,null,true).single { it.key=="radiance:halo" }
        assertTrue(full.width>mid.width*1.3);assertTrue((full.rgb and 255)>(mid.rgb and 255))
        val frozen=WorldInfusionCodec.encode(s)
        for(t in listOf(0L,100L,10000L)) {
            s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(0)).first
            val paused=track.samples(s,t,pose,pivot,weapon,null,false)
            assertTrue(paused.all { it.key in setOf("radiance:halo","radiance:outer") })
            assertEquals(frozen,WorldInfusionCodec.encode(s));assertEquals(full.width,paused.first().width)
        }
        while(s.consumed!=WorldInfusionRules.ingredients)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        repeat(2) { s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first }
        val flow=track.samples(s,10,pose,pivot,weapon,WorldInfusionEnergy.channel(s),true)
        val column=flow.single { it.key=="radiance:column" }
        assertEquals(WorldInfusionSmoke.Facing.VERTICAL,column.facing);assertTrue(column.width>=.6f)
        assertEquals(pivot.y()-.49,column.position.y()+column.height/2,1e-6)
        assertEquals(weapon.y()+.14,column.position.y()-column.height/2,1e-6)
        assertEquals(6,flow.count { it.key.startsWith("radiance:flow:") })
        val cancelled=WorldInfusionRules.cancel(s);track.accepted(cancelled,11,false)
        assertTrue(track.samples(cancelled,11,pose,pivot,weapon,null,true).isEmpty())
        while(s.phase!=InfusionPhase.COMPLETE)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        val reload=WorldInfusionCodec.decode(WorldInfusionCodec.encode(s),owner)
        val cold=WorldInfusionRadiance.Track();cold.accepted(reload,20,false)
        assertTrue(cold.samples(reload,20,pose,pivot,weapon,null,true).isEmpty())
        track.accepted(s,20,true)
        val success=track.samples(s,26,pose,pivot,weapon,null,true)
        assertEquals(2,success.count { it.key.startsWith("radiance:wave:") });assertTrue(success.any { it.key=="radiance:flash" })
        assertTrue(success.filter { it.key.startsWith("radiance:wave:") }.all { it.facing==WorldInfusionSmoke.Facing.HORIZONTAL })
        assertTrue(track.samples(s,60,pose,pivot,weapon,null,true).isEmpty())
    }
    @Test fun `export bold native radiance energy freeze true fill continuous column and success only waves`() {
        val dir=Path.of(".tools/world-infusion-evidence");Files.createDirectories(dir)
        val scenes=mutableListOf<String>();val ends=mutableMapOf<String,Long>()
        for(name in listOf("zero","low","high","interrupt","cancel","shortage")) {
            val repo=WorldInfusionRepository(Files.createTempDirectory("energy-trace-"))
            var s=repo.save(0,ready());val initial=s.gear
            val radiance=WorldInfusionRadiance.Track();val clock=WorldInfusionConfluence.Clock();val track=WorldInfusionConfluence.Track()
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
                radiance.accepted(s,tick,complete)
                val visual=if(p.perSecond==0 && s.phase in setOf(InfusionPhase.ESSENTIA,InfusionPhase.INGREDIENTS))s.copy(paused=true) else s
                val pose=track.step(visual,tick,p.drive,complete,p.perSecond/18.0*4.5,chargedCore=true,boldCore=true)
                val channel=if(p.perSecond>0)WorldInfusionEnergy.channel(s) else null
                val fx=track.finishSamples(s,tick,clock,p.drive,Vec(.5,44.4,.5),Vec(.5,42.15,.5),null,true,true)
                val seal=track.pedestalGlow(s,tick)
                val charge=radiance.samples(s,tick,pose,Vec(.5,44.4,.5),Vec(.5,42.15,.5),channel,p.perSecond>0)
                if(s.phase==InfusionPhase.READY)assertTrue(charge.isEmpty())
                if(s.paused || p.perSecond==0)assertTrue(charge.all { it.key in setOf("radiance:halo","radiance:outer") })
                val pts=WorldInfusionCharge.frame(charge,fx,WorldInfusionSmoke.frame(transfers,tick,pose.inlet(Vec(.5,44.4,.5))));peak=maxOf(peak,pts.size+if(seal>.02)1 else 0)
                if(name in listOf("zero","cancel","shortage"))assertEquals(0.0,seal)
                if(tick%3==0L) {
                    val samples=pts.joinToString(",") { "[${it.position.x()},${it.position.y()-41},${it.position.z()},${it.rgb},${it.scale},${it.progress},\"${it.model}\",${it.width},${it.height},\"${it.facing}\",${it.brightness}]" }
                    val items=s.pedestals.joinToString(",") { if(it.item==null)"null" else "\"${it.item}\"" }
                    frames+="{\"tick\":$tick,\"phase\":\"${s.phase}\",\"paused\":${s.paused},\"power\":${p.perSecond},\"required\":120.0,\"received\":${(s.energy?.receivedMilli ?: 0)/1000.0},\"remaining\":${(s.energy?.remainingMilli ?: 120000)/1000.0},\"yaw\":${pose.yaw},\"speed\":${pose.speed},\"glow\":${pose.glow},\"channel\":${channel ?: "null"},\"radianceSprites\":${charge.size},\"columnVisible\":${charge.any { it.key=="radiance:column" }},\"waveCount\":${charge.count { it.key.startsWith("radiance:wave:") }},\"flash\":${charge.any { it.key=="radiance:flash" }},\"chargeFraction\":${WorldInfusionCharge.fill(s).fraction},\"chargeMotes\":${charge.count { it.key.startsWith("core-charge:") }},\"injectionMotes\":${charge.count { it.key.startsWith("core-injection:") }},\"pedestalGlow\":$seal,\"weaponMod\":\"${s.gear.affixes[0].stone.modId}\",\"amounts\":[${s.jars.joinToString(","){it.amount.toString()}}],\"items\":[$items],\"samples\":[$samples]}"
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
        Files.writeString(dir.resolve("radiance-animation-trace.json"),"{\"frameTicks\":3,\"unitIsDemo\":true,\"requiredRecipeEnergy\":120,\"powerInputBoundsDemo\":[0,18],\"kernels\":[\"WorldInfusionRadiance\",\"WorldInfusionCharge\",\"WorldInfusionEnergy\",\"WorldInfusionConfluence\",\"WorldInfusionSmoke\"],\"scenes\":[${scenes.joinToString(",")}]}\n")
    }
}
