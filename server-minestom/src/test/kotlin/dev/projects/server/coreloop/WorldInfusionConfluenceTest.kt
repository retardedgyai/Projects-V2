package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WorldInfusionConfluenceTest {
    private val owner=UUID.fromString("28282828-1111-2222-3333-444444444444")
    private fun ready():WorldInfusionState {
        var s=WorldInfusionRules.placeMatrix(WorldInfusionState.fresh(owner),InfusionCell(0,41,0))
        for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
            val c=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))[i]
            s=WorldInfusionRules.placePedestal(s,c);s=WorldInfusionRules.material(s,c,r)
        }
        for((i,j) in s.jars.withIndex())s=WorldInfusionRules.placeJar(s,j.id,listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))[i])
        return WorldInfusionRules.placeGear(s,s.gear.identity.id)
    }
    @Test fun `all remaining aspects drain atomically and missing or removed Jar drains none`() {
        val s=WorldInfusionRules.start(ready());val (next,pulses)=WorldInfusionConfluence.tick(s)
        assertEquals(InfusionAspect.entries.toSet(),pulses.map { it.aspect }.toSet())
        assertEquals(listOf(3,3,3),next.jars.map { it.amount });assertEquals(3,next.supplied.values.sum())
        val removed=WorldInfusionRules.removeJar(next,next.jars[2].id)
        val (pause,none)=WorldInfusionConfluence.tick(removed)
        assertTrue(pause.paused);assertTrue(none.isEmpty());assertEquals(removed.jars,pause.jars)
        assertEquals(removed.supplied,pause.supplied)
        val restored=WorldInfusionRules.start(WorldInfusionRules.placeJar(pause,pause.jars[2].id,next.jars[2].cell!!))
        val second=WorldInfusionConfluence.tick(restored).first
        val third=WorldInfusionConfluence.tick(second).first
        assertEquals(InfusionPhase.INGREDIENTS,third.phase);assertEquals(WorldInfusionRules.cost,third.supplied)
        assertEquals(5,third.jars.sumOf { it.amount });assertEquals(4,third.pedestals.count { it.item!=null })
    }
    @Test fun `cancel during weapon channel reload and retry conserve essence gear and ordinary items`() {
        var s=WorldInfusionRules.start(ready());val original=s.gear
        repeat(7) { s=WorldInfusionConfluence.tick(s).first }
        assertEquals(WorldInfusionRules.ingredients,s.consumed);assertEquals(original,s.gear)
        s=WorldInfusionRules.cancel(s);assertEquals(12,s.jars.sumOf { it.amount }+s.reservoir.values.sum())
        assertEquals(original,s.gear)
        s=WorldInfusionCodec.decode(WorldInfusionCodec.encode(s),owner)
        for(p in s.pedestals) {
            val have=s.pedestals.mapNotNull { it.item }.groupingBy { it }.eachCount()
            val r=WorldInfusionRules.ingredients.keys.first { have.getOrDefault(it,0)<WorldInfusionRules.ingredients.getValue(it) }
            s=WorldInfusionRules.material(s,p.cell,r)
        }
        s=WorldInfusionRules.start(s)
        repeat(3) {
            val (next,pulses)=WorldInfusionConfluence.tick(s);s=next
            assertTrue(pulses.none { it.jar!=null },"Saved Matrix essence must not cause another Jar drain")
        }
        repeat(5) { s=WorldInfusionConfluence.tick(s).first }
        assertEquals(InfusionPhase.COMPLETE,s.phase);assertTrue(s.reservoir.isEmpty())
        assertEquals(5,s.jars.sumOf { it.amount });assertEquals(original.identity,s.gear.identity)
        assertEquals(original.affixes[1],s.gear.affixes[1])
    }
    @Test fun `zero auxiliary supply still progresses and more supply changes only cadence motion and flight`() {
        val active=WorldInfusionRules.start(ready())
        for(e in listOf(0.0,.25,.5,.75,1.0)) {
            val d=WorldInfusionConfluence.Drive(e);val t=WorldInfusionConfluence.Track();val c=WorldInfusionConfluence.Clock()
            assertFalse(c.due(active,0,d,emptyList()));assertTrue(c.due(active,d.period.toLong(),d,emptyList()))
            repeat(40) { t.step(active,it.toLong(),d) }
            assertEquals(d.rotationSpeed,t.pose.speed)
            assertEquals(3,WorldInfusionConfluence.tick(active).second.size)
        }
        assertFails { WorldInfusionConfluence.Drive(Double.NaN) };assertFails { WorldInfusionConfluence.Drive(-1.0) }
        val lo=WorldInfusionConfluence.Drive(0.0);val hi=WorldInfusionConfluence.Drive(1.0)
        assertEquals(lo.period,hi.period*3);assertEquals(lo.travelTicks,hi.travelTicks*3)
    }
    @Test fun `export simultaneous low high shortage and cancel from durably accepted shared kernels`() {
        val dir=Path.of(".tools/world-infusion-evidence");Files.createDirectories(dir)
        val scenes=mutableListOf<String>();val ends=mutableMapOf<String,Long>();var lowCosts:Map<InfusionAspect,Int>?=null
        for(name in listOf("low","high","cancel-low","cancel-high","shortage-high")) {
            val supply=if(name.endsWith("low"))0.0 else 1.0;val drive=WorldInfusionConfluence.Drive(supply)
            val repo=WorldInfusionRepository(Files.createTempDirectory("confluence-trace-"))
            var s=repo.save(0,ready());val initial=s.gear
            val clock=WorldInfusionConfluence.Clock();val track=WorldInfusionConfluence.Track()
            val transfers=mutableListOf<WorldInfusionSmoke.Transfer>();val frames=mutableListOf<String>();val events=mutableListOf<String>()
            var completed:Long?=null;var stopped:Long?=null;var firstItem:Long?=null;var peak=0;var completions=0
            for(tick in 0L..480L) {
                if(tick==20L)s=repo.save(s.account.revision,WorldInfusionRules.start(s))
                if(name.startsWith("cancel") && tick==if(supply==0.0)100L else 60L) {
                    s=repo.save(s.account.revision,WorldInfusionRules.cancel(s));stopped=tick
                }
                if(name=="shortage-high" && tick==48L)s=repo.save(s.account.revision,WorldInfusionRules.removeJar(s,s.jars[2].id))
                var complete=false
                if(clock.due(s,tick,drive,transfers)) {
                    val (next,pulses)=WorldInfusionConfluence.tick(s)
                    if(next!==s) {
                        s=repo.save(s.account.revision,next);clock.accepted(s,tick)
                        for(p in pulses) {
                            if(p.jar!=null) {
                                val j=s.jars.single { it.id==p.jar };val c=j.cell!!
                                transfers+=WorldInfusionSmoke.Transfer(Vec(c.x+.5,41.95,c.z+.5),track.pose.inlet(Vec(.5,44.4,.5)),p.aspect!!.rgb,tick,travelTicks=drive.travelTicks,tuftStep=drive.tuftStep)
                                events+="{\"tick\":$tick,\"kind\":\"jar\",\"id\":\"${j.id}\",\"aspect\":\"${j.aspect}\"}"
                            }
                            if(p.ingredient!=null) {
                                assertTrue(transfers.all { WorldInfusionSmoke.expired(it,tick) })
                                if(firstItem==null)firstItem=tick
                                events+="{\"tick\":$tick,\"kind\":\"ingredient\",\"x\":${p.ingredient!!.x},\"z\":${p.ingredient!!.z}}"
                            }
                            if(p.completed) { complete=true;completed=tick;completions++;events+="{\"tick\":$tick,\"kind\":\"complete\"}" }
                        }
                    }
                }
                if(s.paused || name.startsWith("cancel") && stopped!=null) {
                    if(stopped==null)stopped=tick
                    for(i in transfers.indices)transfers[i]=transfers[i].copy(releaseUntil=minOf(transfers[i].releaseUntil,tick))
                }
                if(tick==110L)s=repo.load(owner)!! // Resource save reload cannot mint or double-spend.
                val pose=track.step(s,tick,drive,complete)
                val fx=track.finishSamples(s,tick,clock,drive,Vec(.5,44.4,.5),Vec(.5,42.15,.5))
                val points=(WorldInfusionSmoke.frame(transfers,tick,pose.inlet(Vec(.5,44.4,.5)))+fx).take(64)
                peak=maxOf(peak,points.size)
                if(tick%3==0L) {
                    val samples=points.joinToString(",") { "[${it.position.x()},${it.position.y()-41},${it.position.z()},${it.rgb},${it.scale},${it.progress}]" }
                    val items=s.pedestals.joinToString(",") { if(it.item==null)"null" else "\"${it.item}\"" }
                    val channel=clock.channel(s,tick,drive)
                    frames+="{\"tick\":$tick,\"phase\":\"${s.phase}\",\"paused\":${s.paused},\"yaw\":${pose.yaw},\"speed\":${pose.speed},\"glow\":${pose.glow},\"channel\":${channel ?: "null"},\"weaponMod\":\"${s.gear.affixes[0].stone.modId}\",\"amounts\":[${s.jars.joinToString(","){it.amount.toString()}}],\"items\":[$items],\"samples\":[$samples]}"
                }
            }
            assertTrue(peak<=64);assertEquals(0.0,track.pose.speed);assertEquals(.22,track.pose.glow)
            assertTrue(WorldInfusionSmoke.frame(transfers,480).isEmpty())
            val first=transfers.minOf { it.started }
            assertEquals(3,transfers.count { it.started==first },"Every required element starts together")
            if(name in setOf("low","high")) {
                assertEquals(1,completions);assertEquals(InfusionPhase.COMPLETE,s.phase)
                assertEquals(5,s.jars.sumOf { it.amount });assertEquals(initial.identity,s.gear.identity)
                assertEquals(initial.affixes[1],s.gear.affixes[1]);assertNotNull(firstItem)
                ends[name]=completed!!
                if(name=="low")lowCosts=s.supplied else assertEquals(lowCosts,s.supplied)
            } else {
                assertEquals(0,completions);assertNotNull(stopped)
                assertEquals(initial.identity,s.gear.identity,name);assertEquals(initial.affixes,s.gear.affixes,name)
                assertEquals(initial.enhancement,s.gear.enhancement);assertEquals(initial.tier,s.gear.tier)
                assertEquals(initial.rarity,s.gear.rarity);assertEquals(initial.slot,s.gear.slot)
                assertTrue(transfers.all { it.started<stopped!! });assertTrue(events.none { "\"ingredient\"" in it })
                if(name.startsWith("cancel"))assertEquals(12,s.jars.sumOf { it.amount }+s.reservoir.values.sum())
            }
            scenes+="{\"scenario\":\"$name\",\"supply\":$supply,\"period\":${drive.period},\"travelTicks\":${drive.travelTicks},\"channelTicks\":${drive.channelTicks},\"completedTick\":${completed ?: "null"},\"stoppedTick\":${stopped ?: "null"},\"firstIngredientTick\":${firstItem ?: "null"},\"peakSamples\":$peak,\"events\":[${events.joinToString(",")}],\"frames\":[${frames.joinToString(",")}] }"
        }
        assertTrue(ends.getValue("high")<ends.getValue("low"))
        Files.writeString(dir.resolve("confluence-animation-trace.json"),"{\"frameTicks\":3,\"auxiliarySupplyIsDemoInput\":true,\"kernels\":[\"WorldInfusionConfluence\",\"WorldInfusionSmoke\"],\"scenes\":[${scenes.joinToString(",")}]}\n")
    }
}
