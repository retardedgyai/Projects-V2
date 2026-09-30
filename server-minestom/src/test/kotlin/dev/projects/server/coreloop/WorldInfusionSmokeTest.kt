package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class WorldInfusionSmokeTest {
    @Test fun `smoke has bounded packets finite points varied size and ends at focus`() {
        val t=WorldInfusionSmoke.Transfer(Vec(-3.5,41.95,-2.5),Vec(.5,44.3,.5),InfusionAspect.EMBER.rgb,0)
        val samples=(0L..53L).flatMap { WorldInfusionSmoke.sample(t,it) }
        assertTrue(samples.map { it.scale }.distinct().size>10)
        assertTrue(samples.map { it.rgb }.distinct().size>10)
        assertTrue(samples.all { it.scale>0 && it.position.x().isFinite() && it.position.y().isFinite() })
        assertTrue(WorldInfusionSmoke.frame(List(20){t},20).size<=WorldInfusionSmoke.MAX_SAMPLES)
        assertTrue(WorldInfusionSmoke.sample(t,50).all { it.position.distance(t.to)<.6 })
        assertTrue(WorldInfusionSmoke.frame(listOf(t),54).isEmpty())
    }
    @Test fun `stopping release leaves already emitted tufts travelling but never creates another tuft`() {
        val t=WorldInfusionSmoke.Transfer(Vec.ZERO,Vec(3.0,3.0,0.0),0x84b6bd,0,releaseUntil=4)
        assertEquals(6,WorldInfusionSmoke.sample(t,8).size) // Only births 0 and 2, three lobes each.
        assertTrue(WorldInfusionSmoke.sample(t,32).isNotEmpty())
        assertTrue(WorldInfusionSmoke.sample(t,44).isEmpty())
    }
    @Test fun `export same game smoke samples from successful physical Jar drains then shortage pause`() {
        var s=WorldInfusionState.fresh(UUID.fromString("10101010-1111-2222-3333-444444444444"))
        s=WorldInfusionRules.placeMatrix(s,InfusionCell(0,41,0))
        val cells=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))
        for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
            s=WorldInfusionRules.placePedestal(s,cells[i]);s=WorldInfusionRules.material(s,cells[i],r)
        }
        val jarCells=listOf(InfusionCell(-4,41,-3),InfusionCell(4,41,-3),InfusionCell(0,41,5))
        for((i,j) in s.jars.withIndex())s=WorldInfusionRules.placeJar(s,j.id,jarCells[i])
        s=WorldInfusionRules.placeGear(s,s.gear.identity.id);s=WorldInfusionRules.start(s)
        // Playback fixture: withdraw wind Jar after start. Fire/tide still drain, then a genuine shortage pauses.
        s=WorldInfusionRules.removeJar(s,s.jars.single { it.aspect==InfusionAspect.GALE }.id)
        val transfers=mutableListOf<WorldInfusionSmoke.Transfer>();val frames=mutableListOf<String>();var stop=-1L
        for(tick in 0L..170L) {
            if(tick%18==0L) {
                val (next,pulse)=WorldInfusionRules.tick(s);s=next
                if(pulse?.jar!=null) {
                    val j=s.jars.single { it.id==pulse.jar };val c=j.cell!!
                    transfers+=WorldInfusionSmoke.Transfer(Vec(c.x+.5,41.95,c.z+.5),Vec(.5,44.3,.5),pulse.aspect!!.rgb,tick)
                }
                if(s.paused && stop<0) { stop=tick;for(i in transfers.indices)transfers[i]=transfers[i].copy(releaseUntil=tick) }
            }
            if(tick%2==0L) {
                val pts=WorldInfusionSmoke.frame(transfers,tick).joinToString(",") {
                    "[${it.position.x()},${it.position.y()-41},${it.position.z()},${it.rgb},${it.scale},${it.progress}]"
                }
                val amounts=s.jars.filter { it.aspect!=InfusionAspect.GALE }.joinToString(",") { it.amount.toString() }
                frames+="{\"tick\":$tick,\"paused\":${s.paused},\"amounts\":[$amounts],\"samples\":[$pts]}"
            }
        }
        assertEquals(90L,stop);assertEquals(7,s.jars.sumOf { it.amount })
        assertEquals(5,transfers.size);assertTrue(transfers.any { it.rgb==InfusionAspect.EMBER.rgb })
        assertTrue(transfers.any { it.rgb==InfusionAspect.TIDE.rgb });assertTrue(WorldInfusionSmoke.frame(transfers,130).isEmpty())
        assertEquals(4,s.pedestals.count { it.item!=null })
        val dir=Path.of(".tools/world-infusion-evidence");Files.createDirectories(dir)
        Files.writeString(dir.resolve("smoke-preview-trace.json"),"{\"fps\":10,\"stopTick\":$stop,\"frames\":[${frames.joinToString(",")}]}\n")
    }
}
