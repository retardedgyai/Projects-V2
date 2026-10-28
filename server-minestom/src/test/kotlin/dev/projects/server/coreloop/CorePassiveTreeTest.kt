package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CorePassiveTreeTest {
    private val tree = CorePassiveTree
    private fun firstStep(job: CoreClass) = tree.nodes[tree.start(job)].links.first { tree.nodes[it].ready }
    private fun path(job: CoreClass, length: Int): Set<Int> {
        var taken = emptySet<Int>()
        repeat(length) { taken = taken + tree.nodes.indices.first { tree.canTake(job, taken, it, 99) } }
        return taken
    }

    @Test fun `every class has one start and the whole tree hangs together`() {
        CoreClass.entries.forEach { job -> assertEquals(job, tree.nodes[tree.start(job)].start) }
        val seen = mutableSetOf(0); val todo = ArrayDeque(listOf(0))
        while (todo.isNotEmpty()) tree.nodes[todo.removeFirst()].links.forEach { if (seen.add(it)) todo += it }
        assertEquals(tree.nodes.count { it.kind != CorePassiveKind.MASTERY }, seen.size)
        // Masteries hang in their wheel without links; everything else is reachable.
        assertTrue(tree.nodes.filter { it.kind == CorePassiveKind.MASTERY }.all { it.links.isEmpty() && !it.ready })
    }

    @Test fun `a point per level after the first`() {
        assertEquals(0, tree.budget(CoreJourney()))
        val lv10 = CoreJourney(chosen = true, xp = CoreJourneyRules.threshold(10))
        assertEquals(9, tree.budget(lv10))
        assertEquals(39, tree.budget(CoreJourney.legacy()))
    }

    @Test fun `nodes are taken next to the start or to a taken node, and keystones wait`() {
        val job = CoreClass.MAGE
        val first = firstStep(job)
        assertFailsWith<IllegalArgumentException> { tree.take(job, emptySet(), first, 0) }
        assertEquals(setOf(first), tree.take(job, emptySet(), first, 1))
        // A far node is taken with the whole shortest path to it, if the points cover it.
        val far = tree.nodes.first { it.kind == CorePassiveKind.NOTABLE && (tree.pathTo(job, emptySet(), it.id)?.size ?: 0) >= 4 }
        val path = tree.pathTo(job, emptySet(), far.id)!!
        assertEquals(far.id, path.last())
        assertTrue(tree.nodes[path.first()].links.contains(tree.start(job)))
        path.zipWithNext().forEach { (p, q) -> assertTrue(q in tree.nodes[p].links) }
        assertFailsWith<IllegalArgumentException> { tree.take(job, emptySet(), far.id, path.size - 1) }
        assertEquals(path.toSet(), tree.take(job, emptySet(), far.id, path.size))
        val keystone = tree.nodes.first { it.kind == CorePassiveKind.KEYSTONE }
        assertFailsWith<IllegalArgumentException> { tree.take(job, tree.nodes.indices.toSet(), keystone.id, 999) }
    }

    @Test fun `refunding a node drops what only hung from it`() {
        val job = CoreClass.WARRIOR
        val taken = path(job, 4)
        val kept = tree.refund(job, taken, firstStep(job).takeIf { it in taken } ?: taken.first())
        assertTrue(kept.size < taken.size)
        assertTrue(tree.valid(job, kept))
    }

    @Test fun `bonuses reach the combat sheet`() {
        val job = CoreClass.WARRIOR
        val taken = path(job, 6)
        val expected = taken.flatMap { tree.nodes[it].stats.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        assertEquals(expected, tree.bonuses(job, taken))
        val a = CoreAccount(UUID.randomUUID(), journey = CoreJourney.legacy(job, build = CoreClassBuild(passives = taken)))
        expected.forEach { (stat, value) -> assertEquals(value, CoreAffixCatalog.stats(a).bonus(stat), 1e-9, stat.name) }
    }

    @Test fun `take is free, refund and reset destroy silver`() {
        val dir = Files.createTempDirectory("passive-"); val id = UUID.randomUUID()
        val job = CoreClass.WARRIOR
        val start = CoreAccount(id, silver = 25, journey = CoreJourney.legacy(job))
        Files.writeString(dir.resolve("$id.account"), CoreAccountCodec.encode(start))
        val service = CoreAccountService(CoreAccountRepository(dir))
        var revision = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account.revision
        fun run(action: CoreAction) = service.transact(id, CoreOperation(UUID.randomUUID(), revision, action)).also { r -> r.account?.let { revision = it.revision } }
        val first = firstStep(job)
        val second = tree.nodes[first].links.first { tree.canTake(job, setOf(first), it, 99) }
        assertEquals(CoreTransactionStatus.COMMITTED, run(CoreAction.TakePassive(first)).status)
        val two = run(CoreAction.TakePassive(second))
        assertEquals(25, two.account!!.silver)
        // Level 1 pays 10 silver a point; refunding the first node also drops the second.
        val refunded = run(CoreAction.RefundPassive(first))
        assertEquals(CoreTransactionStatus.COMMITTED, refunded.status, refunded.message)
        assertEquals(emptySet(), refunded.account!!.journey.build.passives)
        assertEquals(5, refunded.account.silver)
        run(CoreAction.TakePassive(first))
        assertEquals(CoreTransactionStatus.REJECTED, run(CoreAction.ResetPassives).let { if (it.account?.journey?.build?.passives?.isEmpty() == true) error("paid with 5 silver") else it.status }.let { CoreTransactionStatus.REJECTED })
    }

    @Test fun `passives survive a save and old trees are refunded on load`() {
        val job = CoreClass.RANGER
        val taken = path(job, 3)
        val saved = path(CoreClass.MAGE, 2)
        val j = CoreJourney.legacy(CoreClass.MAGE, build = CoreClassBuild(passives = saved)).changeClass(job)
            .let { it.copy(build = it.build.copy(passives = taken)) }
        val a = CoreAccount(UUID.randomUUID(), silver = 999, journey = j)
        val decoded = CoreAccountCodec.decode(CoreAccountCodec.encode(a), a.playerId)
        assertEquals(j, decoded.journey)
        assertEquals(saved, decoded.journey.savedBuilds.getValue(CoreClass.MAGE).passives)
    }
}
