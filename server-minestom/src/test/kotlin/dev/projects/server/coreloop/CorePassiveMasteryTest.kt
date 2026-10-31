package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorePassiveMasteryTest {
    private val tree = CorePassiveTree
    private val job = CoreClass.MAGE
    private fun notables(mastery: Int) = tree.nodes.filter { it.kind == CorePassiveKind.NOTABLE && it.group == tree.nodes[mastery].group }
    /** The allocation that reaches a notable of [mastery]'s wheel by the shortest path, on top of [taken]. */
    private fun open(mastery: Int, taken: Set<Int> = emptySet()): Set<Int> {
        val notable = notables(mastery).minBy { tree.pathTo(job, taken, it.id)?.size ?: 999 }
        return tree.take(job, taken, notable.id, 999)
    }
    private fun nearestMastery() = tree.nodes.filter { it.kind == CorePassiveKind.MASTERY }
        .minBy { m -> notables(m.id).minOf { tree.pathTo(job, emptySet(), it.id)?.size ?: 999 } }.id

    @Test fun `rows parse conditions, keystone codes, mastery choices and empty links`() {
        val mastery = tree.parse("78\t-896\t-2660\tMASTERY\t極意\t-\t-\t\t54\tHEALTH_PERCENT=5,MANA_PERCENT=5|COOLDOWN_REDUCTION=5|DAMAGE@VS_BOSS=8")
        assertEquals(emptyList(), mastery.links)
        assertEquals(54, mastery.group)
        assertEquals(3, mastery.choices.size)
        assertEquals(mapOf(CoreAffixStat.HEALTH_PERCENT to 5.0, CoreAffixStat.MANA_PERCENT to 5.0), mastery.choices[0].effect.stats)
        assertEquals(mapOf(CorePassiveCondition.VS_BOSS to mapOf(CoreAffixStat.DAMAGE to 8.0)), mastery.choices[2].effect.conditional)
        val keystone = tree.parse("687\t-1091\t2998\tKEYSTONE\t硝子の大砲\t-\t-\t689\t355\tGLASS_CANNON")
        assertEquals(CoreKeystone.GLASS_CANNON, keystone.keystone)
        assertTrue(keystone.ready)
        val notable = tree.parse("5\t0\t0\tNOTABLE\t試し\t-\tDAMAGE=4,AR_PERCENT@STILL=10\t1,2\t3\t-")
        assertEquals(mapOf(CoreAffixStat.DAMAGE to 4.0), notable.stats)
        assertEquals(mapOf(CorePassiveCondition.STILL to mapOf(CoreAffixStat.AR_PERCENT to 10.0)), notable.effect.conditional)
        assertTrue(notable.description.contains("2秒動いていないとき"))
        assertEquals(15, tree.nodes.count { it.keystone != null })
        assertTrue(tree.nodes.filter { it.kind == CorePassiveKind.MASTERY }.all { it.choices.size == 3 })
    }

    @Test fun `a mastery needs a notable of its wheel, costs a point and is taken once`() {
        val m = nearestMastery()
        assertFailsWith<IllegalArgumentException> { tree.takeMastery(emptySet(), emptyMap(), m, 0, 999) }
        assertFailsWith<IllegalArgumentException> { tree.take(job, emptySet(), m, 999) }
        val taken = open(m)
        assertFailsWith<IllegalArgumentException> { tree.takeMastery(taken, emptyMap(), m, 0, taken.size) }
        assertFailsWith<IllegalArgumentException> { tree.takeMastery(taken, emptyMap(), m, 5, 999) }
        val (withMastery, choices) = tree.takeMastery(taken, emptyMap(), m, 1, 999)
        assertEquals(taken + m, withMastery)
        assertEquals(mapOf(m to 1), choices)
        assertTrue(tree.valid(job, withMastery, choices))
        assertFalse(tree.valid(job, withMastery))
        assertFailsWith<IllegalArgumentException> { tree.takeMastery(withMastery, choices, m, 2, 999) }
        val effect = tree.effects(job, withMastery, choices)
        val option = tree.nodes[m].choices[1].effect
        option.stats.forEach { (stat, v) -> assertTrue((effect.stats[stat] ?: 0.0) >= v) }
    }

    @Test fun `the same option cannot be held in two wheels`() {
        // Wheels that offer identical options (same table text).
        val pair = tree.nodes.filter { it.kind == CorePassiveKind.MASTERY }.groupBy { it.choices.map { c -> c.code } }.values.first { it.size >= 2 }
        val (a, b) = pair.take(2).map { it.id }
        val taken = open(b, open(a))
        val (t1, c1) = tree.takeMastery(taken, emptyMap(), a, 0, 999)
        assertTrue(tree.choiceTaken(c1, b, 0))
        assertFailsWith<IllegalArgumentException> { tree.takeMastery(t1, c1, b, 0, 999) }
        val (t2, c2) = tree.takeMastery(t1, c1, b, 1, 999)
        assertTrue(tree.valid(job, t2, c2))
        assertFalse(tree.valid(job, t2, c2 + (b to 0)))
    }

    @Test fun `refunding the last notable of a wheel refunds its mastery`() {
        val m = nearestMastery()
        val taken = open(m)
        val (t, c) = tree.takeMastery(taken, emptyMap(), m, 0, 999)
        val notable = notables(m).single { it.id in t }.id
        val kept = tree.refund(job, t, notable)
        assertFalse(m in kept)
        assertEquals(t - m - notable, kept)
        assertEquals(t - m, tree.refund(job, t, m))
        assertTrue(tree.valid(job, kept, c.filterKeys { it in kept }))
    }

    @Test fun `masteries go through the service, cost silver to refund and survive a save`() {
        val dir = Files.createTempDirectory("mastery-"); val id = UUID.randomUUID()
        val m = nearestMastery()
        val start = CoreAccount(id, silver = 100_000, journey = CoreJourney.legacy(job))
        Files.writeString(dir.resolve("$id.account"), CoreAccountCodec.encode(start))
        val service = CoreAccountService(CoreAccountRepository(dir))
        var revision = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account.revision
        fun run(action: CoreAction) = service.transact(id, CoreOperation(UUID.randomUUID(), revision, action)).also { r -> r.account?.let { revision = it.revision } }
        assertEquals(CoreTransactionStatus.REJECTED, run(CoreAction.TakeMastery(m, 0)).status)
        val path = open(m)
        assertEquals(CoreTransactionStatus.COMMITTED, run(CoreAction.TakePassive(notables(m).single { it.id in path }.id)).status)
        val took = run(CoreAction.TakeMastery(m, 2))
        assertEquals(CoreTransactionStatus.COMMITTED, took.status, took.message)
        val account = took.account!!
        assertEquals(mapOf(m to 2), account.journey.build.masteries)
        val text = CoreAccountCodec.encode(account)
        assertTrue(text.contains("$m.2"))
        assertEquals(account.journey, CoreAccountCodec.decode(text, id).journey)
        // Refunding the wheel's notable takes the mastery with it and charges for both points.
        val notable = notables(m).single { it.id in account.journey.build.passives }.id
        val before = account.silver
        val refunded = run(CoreAction.RefundPassive(notable)).account!!
        assertFalse(m in refunded.journey.build.passives)
        assertEquals(emptyMap(), refunded.journey.build.masteries)
        val points = account.journey.build.passives.size - refunded.journey.build.passives.size
        assertEquals(before - CorePassiveTree.refundPrice(account.journey) * points, refunded.silver)
    }

    @Test fun `old plain passive rows still read`() {
        assertEquals(setOf(1, 2) to emptyMap(), decodePassives("1,2"))
        assertEquals(setOf(1, 78) to mapOf(78 to 2), decodePassives("1,78.2"))
        assertEquals(emptySet<Int>() to emptyMap(), decodePassives(""))
    }

    @Test fun `keystones are taken like any node and reach the stats`() {
        val keystone = tree.nodes.filter { it.keystone != null }.minBy { tree.pathTo(job, emptySet(), it.id)!!.size }
        val taken = tree.take(job, emptySet(), keystone.id, 999)
        assertTrue(taken.size <= 69, "the nearest keystone is within a full budget")
        val a = CoreAccount(UUID.randomUUID(), journey = CoreJourney.legacy(job, build = CoreClassBuild(passives = taken)))
        assertEquals(setOf(keystone.keystone!!), CoreAffixCatalog.stats(a).keystones)
        val cycle = tree.nodes.first { it.keystone == CoreKeystone.MANA_CYCLE }.id
        val cycleStats = CoreAffixCatalog.capped(emptyMap(), emptyMap(), setOf(CoreKeystone.MANA_CYCLE))
        assertEquals(0.0, cycleStats.cooldownReductionPercent)  // the +30 is added where the tree is summed (stats(account))
        val far = tree.take(job, emptySet(), cycle, 999)
        if (far.size > 69) return
        val stats = CoreAffixCatalog.stats(CoreAccount(UUID.randomUUID(), journey = CoreJourney.legacy(job, build = CoreClassBuild(passives = far))))
        assertTrue(CoreKeystone.MANA_CYCLE in stats.keystones)
        assertTrue(stats.cooldownReductionPercent >= CoreKeystone.MANA_CYCLE_COOLDOWN)
        assertEquals(CoreCombatSheet.from(CoreCombatGear(), CoreAffixStats()).mana * .6,
            CoreCombatSheet.from(CoreCombatGear(), CoreAffixStats(keystones = setOf(CoreKeystone.MANA_CYCLE))).mana, 1e-9)
    }

    @Test fun `bonus points come from distinct bosses beaten`() {
        val fresh = CoreAccount(UUID.randomUUID())
        assertEquals(0, tree.bonusPoints(fresh))
        // 26 and 13 both end in the executioner, 27 in the iron warden: two distinct bosses.
        val cleared = CoreAccount(UUID.randomUUID(), journey = CoreJourney(job, chosen = true),
            zoneRecords = mapOf(26 to CoreZoneRecord(1, 1), 13 to CoreZoneRecord(2, 11), 27 to CoreZoneRecord(1, 1)))
        assertEquals(2, tree.bonusPoints(cleared))
        assertEquals(2, tree.budget(cleared))
        assertEquals(0, tree.budget(cleared.journey))
        val first = tree.nodes[tree.start(job)].links.first { tree.nodes[it].ready }
        val second = tree.nodes[first].links.first { tree.canTake(job, setOf(first), it, 99) }
        val two = cleared.copy(journey = cleared.journey.copy(build = CoreClassBuild(passives = setOf(first, second))))
        assertEquals(2, two.journey.build.passives.size)
        assertFailsWith<IllegalArgumentException> { fresh.copy(journey = two.journey) }
        assertEquals(two.journey, CoreAccountCodec.decode(CoreAccountCodec.encode(two), two.playerId).journey)
        assertNull(CoreFrontierZones.expedition(33))
    }
}
