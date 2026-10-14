package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WorldInfusionAnimationTest {
    private fun fixture():WorldInfusionState {
        var s=WorldInfusionState.fresh(UUID.fromString("19191919-1111-2222-3333-444444444444"))
        s=WorldInfusionRules.placeMatrix(s,InfusionCell(0,41,0))
        for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
            val c=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))[i]
            s=WorldInfusionRules.placePedestal(s,c);s=WorldInfusionRules.material(s,c,r)
        }
        for((i,j) in s.jars.withIndex())s=WorldInfusionRules.placeJar(s,j.id,listOf(InfusionCell(-4,41,2),InfusionCell(4,41,2),InfusionCell(0,41,5))[i])
        return WorldInfusionRules.placeGear(s,s.gear.identity.id)
    }
    @Test fun `rotation accelerates smoothly and shortage cancel settle without completion flash`() {
        val idle=fixture();val active=WorldInfusionRules.start(idle)
        for(stopped in listOf(active.copy(paused=true),WorldInfusionRules.cancel(active))) {
            val track=WorldInfusionAnimation.Track()
            repeat(40) { track.step(active,it.toLong()) }
            assertEquals(WorldInfusionAnimation.MAX_SPEED,track.pose.speed)
            val before=track.pose.yaw;val stoppedFrames=(40L..120L).map { track.step(stopped,it) }
            assertTrue(stoppedFrames.first().yaw>before)
            assertTrue(stoppedFrames.all { it.glow<.9 })
            assertEquals(0.0,stoppedFrames.last().speed);assertEquals(WorldInfusionAnimation.IDLE_GLOW,stoppedFrames.last().glow)
            assertTrue(stoppedFrames.zipWithNext().all { (a,b)->b.speed<=a.speed })
        }
    }
    @Test fun `moving inlet rotates with intact core and emitted smoke reaches it`() {
        val pivot=Vec(.5,44.4,.5)
        val t=WorldInfusionSmoke.Transfer(Vec(-3.5,41.95,2.5),pivot,InfusionAspect.EMBER.rgb,0)
        for(yaw in listOf(0.0,90.0,180.0,270.0)) {
            val pose=WorldInfusionAnimation.Pose(yaw,4.5,.7);val inlet=pose.inlet(pivot)
            assertTrue(inlet.distance(pivot)<.25)
            assertTrue(WorldInfusionSmoke.sample(t,41,inlet).take(3).all { it.position.distance(inlet)<.25 })
            assertTrue(pose.rotation().all { it.isFinite() })
        }
    }
    @Test fun `export complete shortage and cancel timelines from shared runtime kernels`() {
        val dir=Path.of(".tools/world-infusion-evidence");Files.createDirectories(dir)
        val scenes=mutableListOf<String>()
        for(scenario in listOf("complete","shortage","cancel")) {
            var s=fixture();val initial=s.gear;val track=WorldInfusionAnimation.Track()
            val smoke=mutableListOf<WorldInfusionSmoke.Transfer>();val events=mutableListOf<String>();val frames=mutableListOf<String>()
            var stopped:Long?=null;var completed:Long?=null;var firstIngredient:Long?=null
            for(tick in 0L..350L) {
                if(tick==20L)s=WorldInfusionRules.start(s)
                if(tick==90L && scenario=="cancel")s=WorldInfusionRules.cancel(s)
                if(tick==90L && scenario=="shortage")s=WorldInfusionRules.removeJar(s,s.jars.single { it.aspect==InfusionAspect.GALE }.id)
                var pulse:InfusionPulse?=null
                if(tick>20 && tick%18==0L && WorldInfusionAnimation.mayAdvance(s,smoke,tick)) {
                    val pair=WorldInfusionRules.tick(s);s=pair.first;pulse=pair.second
                    if(pulse?.jar!=null) {
                        val jar=s.jars.single { it.id==pulse.jar };val c=jar.cell!!
                        smoke+=WorldInfusionSmoke.Transfer(Vec(c.x+.5,41.95,c.z+.5),track.pose.inlet(Vec(.5,44.4,.5)),pulse.aspect!!.rgb,tick)
                        events+="{\"tick\":$tick,\"kind\":\"jar\",\"id\":\"${jar.id}\",\"aspect\":\"${jar.aspect}\"}"
                    }
                    if(pulse?.ingredient!=null) {
                        assertTrue(smoke.all { WorldInfusionSmoke.expired(it,tick) },"Items wait for all committed smoke to arrive")
                        if(firstIngredient==null)firstIngredient=tick
                        events+="{\"tick\":$tick,\"kind\":\"ingredient\",\"x\":${pulse.ingredient!!.x},\"z\":${pulse.ingredient!!.z}}"
                    }
                    if(pulse?.completed==true) { completed=tick;events+="{\"tick\":$tick,\"kind\":\"complete\"}" }
                }
                if(s.paused || (s.phase==InfusionPhase.READY && tick>=20)) {
                    if(stopped==null)stopped=tick
                    for(i in smoke.indices)smoke[i]=smoke[i].copy(releaseUntil=minOf(smoke[i].releaseUntil,tick))
                }
                val pose=track.step(s,tick,pulse?.completed==true)
                if(tick%2==0L) {
                    val inlet=pose.inlet(Vec(.5,44.4,.5))
                    val points=WorldInfusionSmoke.frame(smoke,tick,inlet).joinToString(",") { "[${it.position.x()},${it.position.y()-41},${it.position.z()},${it.rgb},${it.scale},${it.progress}]" }
                    val items=s.pedestals.joinToString(",") { if(it.item==null)"null" else "\"${it.item}\"" }
                    frames+="{\"tick\":$tick,\"phase\":\"${s.phase}\",\"paused\":${s.paused},\"yaw\":${pose.yaw},\"speed\":${pose.speed},\"glow\":${pose.glow},\"amounts\":[${s.jars.joinToString(","){it.amount.toString()}}],\"items\":[$items],\"samples\":[$points]}"
                }
            }
            assertEquals(0.0,track.pose.speed);assertEquals(WorldInfusionAnimation.IDLE_GLOW,track.pose.glow)
            assertTrue(WorldInfusionSmoke.frame(smoke,350).isEmpty())
            if(scenario=="complete") {
                assertEquals(InfusionPhase.COMPLETE,s.phase);assertNotNull(completed);assertNotNull(firstIngredient)
                assertEquals(initial.identity,s.gear.identity);assertEquals(initial.affixes[1],s.gear.affixes[1])
                assertEquals(5,s.jars.sumOf { it.amount });assertEquals(7,events.count { "\"jar\"" in it })
            } else {
                assertNull(completed);assertEquals(initial,s.gear);assertNotNull(stopped)
                assertTrue(events.none { "\"ingredient\"" in it })
            }
            scenes+="{\"scenario\":\"$scenario\",\"completedTick\":${completed ?: "null"},\"stoppedTick\":${stopped ?: "null"},\"firstIngredientTick\":${firstIngredient ?: "null"},\"events\":[${events.joinToString(",")}],\"frames\":[${frames.joinToString(",")}] }"
        }
        Files.writeString(dir.resolve("ritual-animation-trace.json"),"{\"fps\":10,\"kernels\":[\"WorldInfusionRules\",\"WorldInfusionSmoke\",\"WorldInfusionAnimation\"],\"scenes\":[${scenes.joinToString(",")}]}\n")
    }
}
