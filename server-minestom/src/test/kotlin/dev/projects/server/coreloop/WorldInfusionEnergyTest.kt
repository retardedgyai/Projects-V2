package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WorldInfusionEnergyTest {
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
    @Test fun `zero supply freezes energy and all resources then low and high use exactly same required E`() {
        val active=WorldInfusionEnergy.start(ready())
        repeat(100) { assertSame(active,WorldInfusionEnergy.advance(active,WorldInfusionEnergy.Supply(0)).first) }
        assertEquals(0,active.energy!!.receivedMilli);assertEquals(12,active.jars.sumOf { it.amount })
        val counts=mutableListOf<Int>()
        for(p in listOf(6,18)) {
            var s=active;var steps=0;var completions=0
            while(s.phase!=InfusionPhase.COMPLETE && steps<200) {
                val (next,pulses)=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(p));s=next;steps++
                completions+=pulses.count { it.completed }
            }
            assertEquals(1,completions);assertEquals(120_000,s.energy!!.receivedMilli)
            assertEquals(5,s.jars.sumOf { it.amount });assertEquals(active.gear.identity,s.gear.identity)
            assertEquals(active.gear.affixes[1],s.gear.affixes[1]);counts+=steps
        }
        assertTrue(counts[1]<counts[0])
    }
    @Test fun `atomic v2 energy save failure reload corruption bounds and old v1 envelopes stay consistent`() {
        val old=ready();val oldText=WorldInfusionCodec.encode(old)
        assertTrue(oldText.startsWith("PROJECTS_WORLD_INFUSION\t1\t"));assertEquals(oldText,WorldInfusionCodec.encode(WorldInfusionCodec.decode(oldText,owner)))
        val dir=Files.createTempDirectory("infusion-energy-save-");val repo=WorldInfusionRepository(dir)
        var s=repo.save(0,WorldInfusionEnergy.start(old))
        repeat(8) { s=repo.save(s.account.revision,WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first) }
        val before=WorldInfusionCodec.encode(s);assertTrue(before.startsWith("PROJECTS_WORLD_INFUSION\t2\t"))
        val fail=WorldInfusionRepository(dir) { _,_->error("injected before rename") }
        assertFails { fail.save(s.account.revision,WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first) }
        assertEquals(before,WorldInfusionCodec.encode(repo.load(owner)!!))
        s=repo.load(owner)!!;val frozen=s
        repeat(20) { s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(0)).first }
        assertEquals(before,WorldInfusionCodec.encode(s));assertEquals(frozen.energy,s.energy)
        while(s.phase!=InfusionPhase.COMPLETE)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        assertEquals(5,s.jars.sumOf { it.amount });assertEquals(120_000,s.energy!!.receivedMilli)
        assertFails { InfusionEnergyLedger(receivedMilli=-1) };assertFails { InfusionEnergyLedger(requiredMilli=1) }
        assertFails { WorldInfusionCodec.decode(before.replace("120000","1"),owner) }
    }
    @Test fun `channel cancellation preserves equipment and materials but retires old energy receipt for retry`() {
        var s=WorldInfusionEnergy.start(ready());val initial=s.gear
        while(s.consumed!=WorldInfusionRules.ingredients)s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first
        repeat(2) { s=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18)).first }
        assertEquals(107_200,s.energy!!.receivedMilli);assertEquals(initial.identity,s.gear.identity)
        s=WorldInfusionRules.cancel(s)
        assertEquals(12,s.jars.sumOf { it.amount }+s.reservoir.values.sum());assertEquals(initial.affixes,s.gear.affixes)
        assertEquals(107_200,s.energy!!.receivedMilli)
        for(p in s.pedestals) {
            val have=s.pedestals.mapNotNull { it.item }.groupingBy { it }.eachCount()
            val r=WorldInfusionRules.ingredients.keys.first { have.getOrDefault(it,0)<WorldInfusionRules.ingredients.getValue(it) }
            s=WorldInfusionRules.material(s,p.cell,r)
        }
        s=WorldInfusionEnergy.start(s);assertEquals(0,s.energy!!.receivedMilli)
        var extraDrains=0
        while(s.phase!=InfusionPhase.COMPLETE) {
            val (next,p)=WorldInfusionEnergy.advance(s,WorldInfusionEnergy.Supply(18));s=next;extraDrains+=p.count { it.jar!=null }
        }
        assertEquals(0,extraDrains);assertEquals(5,s.jars.sumOf { it.amount });assertEquals(120_000,s.energy!!.receivedMilli)
    }
    @Test fun `export zero low high interruption reload and cancel with energy meter and success pedestal only once`() {
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
                val pose=track.step(visual,tick,p.drive,complete,p.perSecond/18.0*4.5)
                val channel=if(p.perSecond>0)WorldInfusionEnergy.channel(s) else null
                val fx=track.finishSamples(s,tick,clock,p.drive,Vec(.5,44.4,.5),Vec(.5,42.15,.5),channel,true,true)
                val seal=track.pedestalGlow(s,tick)
                val pts=(WorldInfusionSmoke.frame(transfers,tick,pose.inlet(Vec(.5,44.4,.5)))+fx).take(64);peak=maxOf(peak,pts.size+if(seal>.02)1 else 0)
                if(name in listOf("zero","cancel","shortage"))assertEquals(0.0,seal)
                if(tick%3==0L) {
                    val samples=pts.joinToString(",") { "[${it.position.x()},${it.position.y()-41},${it.position.z()},${it.rgb},${it.scale},${it.progress}]" }
                    val items=s.pedestals.joinToString(",") { if(it.item==null)"null" else "\"${it.item}\"" }
                    frames+="{\"tick\":$tick,\"phase\":\"${s.phase}\",\"paused\":${s.paused},\"power\":${p.perSecond},\"required\":120.0,\"received\":${(s.energy?.receivedMilli ?: 0)/1000.0},\"remaining\":${(s.energy?.remainingMilli ?: 120000)/1000.0},\"yaw\":${pose.yaw},\"speed\":${pose.speed},\"glow\":${pose.glow},\"channel\":${channel ?: "null"},\"pedestalGlow\":$seal,\"weaponMod\":\"${s.gear.affixes[0].stone.modId}\",\"amounts\":[${s.jars.joinToString(","){it.amount.toString()}}],\"items\":[$items],\"samples\":[$samples]}"
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
        Files.writeString(dir.resolve("energy-animation-trace.json"),"{\"frameTicks\":3,\"unitIsDemo\":true,\"requiredRecipeEnergy\":120,\"powerInputBoundsDemo\":[0,18],\"kernels\":[\"WorldInfusionEnergy\",\"WorldInfusionConfluence\",\"WorldInfusionSmoke\"],\"scenes\":[${scenes.joinToString(",")}]}\n")
    }
}
