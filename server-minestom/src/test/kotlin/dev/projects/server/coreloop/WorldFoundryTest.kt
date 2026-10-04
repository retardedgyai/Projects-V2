package dev.projects.server.coreloop

import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.*

class WorldFoundryTest {
    private val owner=UUID.fromString("fa3c19e0-7928-404f-aa64-726316544cdd")
    private fun assembled():WorldFoundryState {
        var s=WorldFoundryState.fresh(owner)
        for((k,c) in mapOf(FoundryEquipment.HEATER to FoundryCell(0,41,0),FoundryEquipment.MELTER to FoundryCell(0,42,0),
            FoundryEquipment.FAUCET to FoundryCell(1,42,0),FoundryEquipment.TABLE to FoundryCell(1,41,0)))s=WorldFoundryRules.place(s,k,c)
        return s
    }
    private fun melted():WorldFoundryState {
        var s=WorldFoundryRules.insert(WorldFoundryRules.fuel(assembled()))
        repeat(7) { s=WorldFoundryRules.tick(s) }
        assertEquals(4,s.liquid);assertEquals(0,s.input);return s
    }
    private fun mold(shape:FoundryShape)=WorldFoundryRules.takePattern(WorldFoundryRules.imprint(WorldFoundryRules.sand(melted()),shape))
    @Test fun `both shapes conserve metal and spend exactly one sand charge`() {
        for(shape in FoundryShape.entries) {
            var s=WorldFoundryRules.pour(mold(shape));val units=WorldFoundryRules.metalUnits(s)
            assertEquals(shape.units,s.castUnits);assertEquals(4-shape.units,s.liquid)
            assertFailsWith<IllegalArgumentException> { WorldFoundryRules.pour(s) }
            assertFailsWith<IllegalArgumentException> { WorldFoundryRules.takePart(s) }
            repeat(7) { s=WorldFoundryRules.tick(s);assertEquals(units,WorldFoundryRules.metalUnits(s)) }
            assertEquals(FoundryPhase.OUTPUT,s.phase)
            s=WorldFoundryRules.takePart(s)
            assertEquals(1,s.parts[shape]);assertEquals(7,s.sand);assertEquals(FoundryPhase.EMPTY,s.phase)
            assertEquals(32,WorldFoundryRules.metalUnits(s))
            assertFailsWith<IllegalArgumentException> { WorldFoundryRules.takePart(s) }
        }
    }
    @Test fun `layout matters and a missing heater suspends melting`() {
        var s=assembled();assertTrue(WorldFoundryRules.linked(s))
        s=WorldFoundryRules.remove(s,FoundryEquipment.FAUCET)
        s=WorldFoundryRules.place(s,FoundryEquipment.FAUCET,FoundryCell(2,42,0))
        assertFalse(WorldFoundryRules.linked(s))
        s=WorldFoundryRules.takePattern(WorldFoundryRules.imprint(WorldFoundryRules.sand(s),FoundryShape.ROD)).copy(liquid=4)
        assertFailsWith<IllegalArgumentException> { WorldFoundryRules.pour(s) }
        var cold=WorldFoundryRules.insert(WorldFoundryRules.remove(assembled(),FoundryEquipment.HEATER))
        repeat(20) { cold=WorldFoundryRules.tick(cold) }
        assertEquals(1,cold.input);assertEquals(0,cold.liquid)
    }
    @Test fun `prototype must be removed and unpoured sand is reclaimable`() {
        val pattern=WorldFoundryRules.imprint(WorldFoundryRules.sand(melted()),FoundryShape.ROD)
        assertFailsWith<IllegalArgumentException> { WorldFoundryRules.pour(pattern) }
        assertFailsWith<IllegalArgumentException> { WorldFoundryRules.imprint(pattern,FoundryShape.PLATE) }
        val cleared=WorldFoundryRules.clearMold(WorldFoundryRules.takePattern(pattern))
        assertEquals(8,cleared.sand);assertNull(cleared.shape)
        assertEquals(4,cleared.liquid)
    }
    @Test fun `breaks preserve loaded machines and casting escrow`() {
        val s=WorldFoundryRules.pour(mold(FoundryShape.ROD))
        FoundryEquipment.entries.forEach { assertFailsWith<IllegalArgumentException> { WorldFoundryRules.remove(s,it) } }
        assertFailsWith<IllegalArgumentException> { WorldFoundryRules.remove(melted(),FoundryEquipment.MELTER) }
        assertEquals(32,WorldFoundryRules.metalUnits(WorldFoundryRules.reclaim(melted())))
    }
    @Test fun `restart at every active second resumes exactly once`() {
        val repository=WorldFoundryRepository(Files.createTempDirectory("foundry-resume"))
        var s=repository.save(0,WorldFoundryRules.pour(mold(FoundryShape.PLATE)))
        repeat(7) {
            // Each new repository simulates a new process; no game session or inventory carries state.
            s=WorldFoundryCodec.decode(WorldFoundryCodec.encode(s),owner)
            val unchanged=repository.load(owner)!!
            assertEquals(s.seconds,unchanged.seconds);assertEquals(s.phase,unchanged.phase)
            s=repository.save(s.account.revision,WorldFoundryRules.tick(s))
        }
        assertEquals(FoundryPhase.OUTPUT,s.phase)
        s=repository.save(s.account.revision,WorldFoundryRules.takePart(s))
        assertEquals(1,repository.load(owner)!!.parts[FoundryShape.PLATE])
        assertFailsWith<IllegalArgumentException> { WorldFoundryRules.takePart(repository.load(owner)!!) }
    }
    @Test fun `failed replacement retains old resources and ambiguous success is acknowledged`() {
        val directory=Files.createTempDirectory("foundry-atomic")
        val good=WorldFoundryRepository(directory);val initial=good.save(0,assembled())
        val fails=WorldFoundryRepository(directory) { _,_->throw java.io.IOException("simulated pre-rename interruption") }
        assertFailsWith<java.io.IOException> { fails.save(initial.account.revision,WorldFoundryRules.insert(initial)) }
        assertEquals(8,good.load(owner)!!.account.amount(WorldFoundryRules.metal))
        val succeeds=WorldFoundryRepository(directory) { a,b->Files.move(a,b,ATOMIC_MOVE,REPLACE_EXISTING);throw java.io.IOException("simulated post-rename interruption") }
        val next=succeeds.save(initial.account.revision,WorldFoundryRules.insert(initial))
        assertEquals(7,next.account.amount(WorldFoundryRules.metal));assertEquals(1,good.load(owner)!!.input)
        assertFailsWith<IllegalArgumentException> { good.save(initial.account.revision,WorldFoundryRules.insert(initial)) }
    }
    @Test fun `simultaneous repeated pours commit at most once`() {
        val repository=WorldFoundryRepository(Files.createTempDirectory("foundry-race"))
        val before=repository.save(0,mold(FoundryShape.ROD))
        val pool=Executors.newFixedThreadPool(4)
        try {
            val results=pool.invokeAll(List(12) { java.util.concurrent.Callable { runCatching { repository.save(before.account.revision,WorldFoundryRules.pour(before)) }.isSuccess } })
            assertEquals(1,results.count { it.get() })
            val after=repository.load(owner)!!;assertEquals(2,after.liquid);assertEquals(2,after.castUnits)
            assertEquals(32,WorldFoundryRules.metalUnits(after))
        } finally { pool.shutdownNow() }
    }
    @Test fun `checksums owner and shape bounds protect saves`() {
        val text=WorldFoundryCodec.encode(mold(FoundryShape.ROD))
        assertFailsWith<IllegalArgumentException> { WorldFoundryCodec.decode(text.replace("MOLD","OUTPUT"),owner) }
        assertFailsWith<IllegalArgumentException> { WorldFoundryCodec.decode(text,UUID.randomUUID()) }
        assertFailsWith<IllegalArgumentException> { WorldFoundryState.fresh(owner).copy(liquid=4) }
        assertFailsWith<IllegalArgumentException> { WorldFoundryRules.place(assembled(),FoundryEquipment.TABLE,FoundryCell(2,41,0)) }
    }
    @Test fun `preview trace uses the same authoritative rules and presentation as the game`() {
        val traces=FoundryShape.entries.associate { shape->
            val states=mutableListOf(assembled())
            var s=WorldFoundryRules.fuel(WorldFoundryRules.fuel(assembled()))
            repeat(2) { s=WorldFoundryRules.insert(s) };s=WorldFoundryRules.tick(s);states+=s
            while(s.input>0)s=WorldFoundryRules.tick(s)
            states+=s;s=WorldFoundryRules.sand(s);states+=s
            s=WorldFoundryRules.imprint(s,shape);states+=s
            s=WorldFoundryRules.takePattern(s);states+=s
            s=WorldFoundryRules.pour(s);states+=s
            repeat(2) { s=WorldFoundryRules.tick(s) }
            repeat(5) { states+=s;s=WorldFoundryRules.tick(s) }
            states+=s;states+=WorldFoundryRules.takePart(s)
            assertEquals(14,states.size)
            assertTrue(states.all { WorldFoundryRules.metalUnits(it)==32L })
            val frames=states.map { state->
                val visuals=WorldFoundryPresentation.frame(state)
                visuals.forEach { assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/models/foundry/${it.model}.json")) }
                "{\"phase\":\"${state.phase}\",\"liquid\":${state.liquid},\"castUnits\":${state.castUnits},\"seconds\":${state.seconds},\"visuals\":["+
                    visuals.joinToString(",") { v->"{\"key\":\"${v.key}\",\"model\":\"${v.model}\",\"x\":${v.cell.x},\"y\":${v.cell.y-41},\"z\":${v.cell.z},\"yaw\":${v.yaw}}" }+"]}"
            }
            shape.name.lowercase() to "[${frames.joinToString(",")}]"
        }
        val output=java.nio.file.Path.of("build/reports/world-foundry/preview-trace.json")
        Files.createDirectories(output.parent)
        Files.writeString(output,traces.entries.joinToString(",",prefix="{",postfix="}") { "\"${it.key}\":${it.value}" })
        // Four orientations must preserve the physical outlet and a directly-below table.
        for((x,z,yaw) in listOf(Triple(1,0,-90f),Triple(-1,0,90f),Triple(0,1,180f),Triple(0,-1,0f))) {
            val s=assembled().copy(equipment=assembled().equipment+(FoundryEquipment.FAUCET to FoundryCell(x,42,z))+(FoundryEquipment.TABLE to FoundryCell(x,41,z)))
            assertTrue(WorldFoundryRules.linked(s))
            assertEquals(yaw,WorldFoundryPresentation.frame(s).single { it.key=="faucet" }.yaw)
        }
    }
}
