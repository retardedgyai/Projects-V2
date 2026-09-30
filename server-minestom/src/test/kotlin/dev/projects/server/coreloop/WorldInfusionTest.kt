package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class WorldInfusionTest {
    private val owner=UUID.fromString("10101010-1111-2222-3333-444444444444")
    private fun ready():WorldInfusionState {
        var s=WorldInfusionState.fresh(owner)
        s=WorldInfusionRules.placeMatrix(s,InfusionCell(0,41,0))
        for((i,r) in listOf(CoreResource.INGOT,CoreResource.CLOTH,CoreResource.INGOT,CoreResource.STONE_BLOCK).withIndex()) {
            val c=listOf(InfusionCell(3,41,0),InfusionCell(0,41,3),InfusionCell(-3,41,0),InfusionCell(0,41,-3))[i]
            s=WorldInfusionRules.placePedestal(s,c);s=WorldInfusionRules.material(s,c,r)
        }
        for((i,j) in s.jars.withIndex()) s=WorldInfusionRules.placeJar(s,j.id,InfusionCell(-4+i*4,41,4))
        return WorldInfusionRules.placeGear(s,s.gear.identity.id)
    }
    @Test fun `physical placement requires ownership free cells correct material and exact UUID`() {
        val s=ready();assertNull(WorldInfusionRules.missing(s))
        assertFails { WorldInfusionRules.placeGear(s,UUID.randomUUID()) }
        assertFails { WorldInfusionRules.placeJar(s,s.jars[0].id,InfusionCell(0,41,0)) }
        assertFails { WorldInfusionRules.material(s,s.pedestals[0].cell,CoreResource.INGOT) }
        val duplicate=s.jars+s.jars[0];assertFails { s.copy(jars=duplicate) }
        assertFails { s.copy(pedestals=s.pedestals+s.pedestals[0]) }
    }
    @Test fun `only placed nearby Jar content supplies and all essence precedes any side consumption`() {
        var s=WorldInfusionRules.start(ready());val total=s.jars.sumOf { it.amount }
        repeat(7) {
            s=WorldInfusionRules.tick(s).first
            assertEquals(4,s.pedestals.count { it.item!=null });assertTrue(s.consumed.isEmpty())
        }
        assertEquals(total-7,s.jars.sumOf { it.amount });assertEquals(WorldInfusionRules.cost,s.supplied)
        s=WorldInfusionRules.tick(s).first;assertEquals(InfusionPhase.INGREDIENTS,s.phase)
        s=WorldInfusionRules.tick(s).first;assertEquals(3,s.pedestals.count { it.item!=null })
        var absent=ready().copy(jars=ready().jars.map { it.copy(cell=null) })
        assertNotNull(WorldInfusionRules.missing(absent));assertFails { WorldInfusionRules.start(absent) }
        absent=ready().copy(jars=ready().jars.map { it.copy(cell=InfusionCell(15,41,15)) }.take(1))
        assertFails { WorldInfusionRules.start(absent) }
    }
    @Test fun `world removal pauses remaining drain survives save and resumes without double consumption`() {
        var s=WorldInfusionRules.start(ready());s=WorldInfusionRules.tick(s).first
        val id=s.jars.first { it.aspect==InfusionAspect.EMBER }.id
        val c=s.jars.single { it.id==id }.cell!!
        s=WorldInfusionRules.removeJar(s,id);val amount=s.jars.sumOf { it.amount }
        s=WorldInfusionRules.tick(s).first;assertTrue(s.paused);assertEquals(amount,s.jars.sumOf { it.amount })
        s=WorldInfusionCodec.decode(WorldInfusionCodec.encode(s),owner)
        s=WorldInfusionRules.placeJar(s,id,c);s=WorldInfusionRules.start(s)
        repeat(20) { s=WorldInfusionRules.tick(s).first }
        assertEquals(InfusionPhase.COMPLETE,s.phase);assertEquals(12-7,s.jars.sumOf { it.amount })
    }
    @Test fun `fixed slot conversion preserves instance provenance enhancement rolls and other MOD and adds no damage`() {
        var s=WorldInfusionRules.start(ready());val g=s.gear
        repeat(13) { s=WorldInfusionRules.tick(s).first }
        assertEquals(InfusionPhase.COMPLETE,s.phase)
        assertEquals(g.identity,s.gear.identity);assertEquals(g.tier,s.gear.tier);assertEquals(g.rarity,s.gear.rarity)
        assertEquals(g.enhancement,s.gear.enhancement);assertEquals(g.affixes[1],s.gear.affixes[1])
        assertEquals(g.affixes[0].stone.id,s.gear.affixes[0].stone.id)
        assertEquals(g.affixes[0].stone.value,s.gear.affixes[0].stone.value)
        assertEquals(WorldInfusionRules.MOD,s.gear.affixes[0].stone.modId)
        val before=CoreAffixCatalog.stats(g.project(s.account));val after=CoreAffixCatalog.stats(s.gear.project(s.account))
        assertEquals(before.fireFlat,after.iceFlat);assertEquals(0.0,after.fireFlat)
        assertEquals(0,CoreAffixCatalog.definition(s.gear.affixes[0].stone)!!.weight)
        s=WorldInfusionRules.takeGear(s);assertEquals(InfusionGearPlace.INVENTORY,s.gearPlace)
        assertFails { WorldInfusionRules.takeGear(s) };assertFails { WorldInfusionRules.placeGear(s,s.gear.identity.id) }
    }
    @Test fun `cancel before during and after ingredient drain conserves ordinary materials and essence`() {
        for(steps in listOf(0,3,9,12)) {
            var s=WorldInfusionRules.start(ready());repeat(steps) { s=WorldInfusionRules.tick(s).first }
            s=WorldInfusionRules.cancel(s)
            assertEquals(InfusionPhase.READY,s.phase)
            for((r,n) in WorldInfusionRules.ingredients)assertEquals(n.toLong(),s.account.amount(r,2)+s.pedestals.count { it.item==r })
            assertEquals(12,s.jars.sumOf { it.amount }+s.reservoir.values.sum())
            assertFails { WorldInfusionRules.cancel(s) }
            for(p in s.pedestals.filter { it.item==null }) {
                val have=s.pedestals.mapNotNull { it.item }.groupingBy { it }.eachCount()
                val r=WorldInfusionRules.ingredients.keys.first { have.getOrDefault(it,0)<WorldInfusionRules.ingredients.getValue(it) }
                s=WorldInfusionRules.material(s,p.cell,r)
            }
            s=WorldInfusionRules.start(s);repeat(20) { s=WorldInfusionRules.tick(s).first }
            assertEquals(5,s.jars.sumOf { it.amount });assertTrue(s.reservoir.isEmpty())
            assertFails { WorldInfusionRules.cancel(s) }
        }
    }
    @Test fun `atomic save roundtrip conflicts corruption and save failure never manufacture progress`() {
        val dir=Files.createTempDirectory("world-infusion-test-");val repo=WorldInfusionRepository(dir)
        var s=repo.save(0,ready());assertEquals(1,s.account.revision)
        assertEquals(WorldInfusionCodec.encode(s),WorldInfusionCodec.encode(repo.load(owner)!!))
        assertFails { repo.save(0,ready()) }
        val before=Files.readString(dir.resolve("$owner.infusion"))
        val failure=WorldInfusionRepository(dir) { _,_->error("injected failure") }
        assertFails { failure.save(1,WorldInfusionRules.start(s)) }
        assertEquals(before,Files.readString(dir.resolve("$owner.infusion")))
        assertFails { WorldInfusionCodec.decode(before.replace("READY","COMPLETE"),owner) }
        assertFails { WorldInfusionCodec.decode(before,UUID.randomUUID()) }
        val afterRename=WorldInfusionRepository(dir) { a,b->Files.move(a,b,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);error("reported after rename") }
        s=afterRename.save(1,WorldInfusionRules.start(s));assertEquals(2,s.account.revision)
        assertEquals(InfusionPhase.ESSENTIA,repo.load(owner)!!.phase)
    }
}
