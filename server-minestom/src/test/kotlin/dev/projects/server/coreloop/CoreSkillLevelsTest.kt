package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreSkillLevelsTest {
    private val levels = CoreSkillLevels
    private fun account(journey: CoreJourney, silver: Long = 0) = CoreAccount(UUID.randomUUID(), silver = silver, journey = journey)
    private fun atLevel(level: Int, job: CoreClass = CoreClass.WARRIOR) = CoreJourney(job, chosen = true, xp = CoreJourneyRules.threshold(level))

    @Test fun `points come in like passive points and are spent per skill up to the cap`() {
        val fresh = account(atLevel(1))
        assertEquals(0, levels.available(fresh))
        assertFailsWith<IllegalArgumentException> { levels.raise(fresh, "dash") }
        val lv20 = account(atLevel(20))
        assertEquals(CorePassiveTree.budget(lv20), levels.budget(lv20))
        assertEquals(19, levels.available(lv20))
        var a = lv20
        repeat(CoreSkillLevels.MAX_BASE - 1) { a = a.copy(journey = a.journey.copy(build = levels.raise(a, "dash"))) }
        assertEquals(CoreSkillLevels.MAX_BASE, levels.base(a.journey.build, "dash"))
        assertEquals(19 - 9, levels.available(a))
        assertFailsWith<IllegalArgumentException> { levels.raise(a, "dash") }  // the cap points can reach
        // Only skills of the current class, never retired ones.
        assertFailsWith<IllegalArgumentException> { levels.raise(a, "firebolt") }
        assertFalse("mage_garden" in levels.levelable(CoreClass.MAGE))
    }

    @Test fun `an account cannot hold more levels than its points`() {
        assertFailsWith<IllegalArgumentException> {
            account(atLevel(3).copy(build = CoreClassBuild(skillLevels = mapOf("dash" to 5))))
        }
        assertFailsWith<IllegalArgumentException> { CoreClassBuild(skillLevels = mapOf("dash" to CoreSkillLevels.MAX_BASE + 1)) }
        // Another class's skills on this build are rejected too.
        assertFailsWith<IllegalArgumentException> { account(atLevel(30).copy(build = CoreClassBuild(skillLevels = mapOf("firebolt" to 2)))) }
    }

    @Test fun `a level multiplies the skill's damage and gear goes past the cap`() {
        val journey = atLevel(30).copy(build = CoreClassBuild(skillLevels = mapOf("slam" to 5)))
        val base = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "slam" }
        fun formula(stats: CoreAffixStats) = CoreSkillCatalog.modify(base, journey, stats).formula
        val plain = CoreSkillCatalog.modify(base, atLevel(30), CoreAffixStats()).formula
        assertEquals(plain.ad * 1.20, formula(CoreAffixStats()).ad, 1e-9)
        assertEquals(6, levels.level(journey.build, CoreAffixStats(skillLevels = mapOf("slam" to 1)), "slam"))
        val gear = CoreAffixStats(additional = mapOf(CoreAffixStat.SKILL_LEVEL to 2.0), skillLevels = mapOf("slam" to 2))
        assertEquals(9, levels.level(journey.build, gear, "slam"))
        assertEquals(plain.ad * 1.40, formula(gear).ad, 1e-9)
        // A stone naming the skill only lifts that skill.
        assertEquals(1, levels.level(journey.build, CoreAffixStats(skillLevels = mapOf("slam" to 2)), "dash"))
    }

    @Test fun `skill stones are rare weapon stones that name one skill or all of them`() {
        val all = CoreAffixCatalog.definitions.single { it.id == "projects:skill-all" }
        assertEquals(setOf(CoreGearSlot.WEAPON), all.allowedGear)
        assertEquals(1..2, all.range(4))
        val slam = CoreAffixCatalog.definitions.single { it.id == "projects:skill-slam" }
        assertEquals(1, slam.weight)
        val stone = CoreAffixStone(UUID.randomUUID(), "projects:skill-war-guard", 2, 2.0)
        assertEquals("war_guard", CoreAffixCatalog.skillOf(stone))
        assertTrue(CoreAffixCatalog.describe(stone).endsWith("Lv +2"), CoreAffixCatalog.describe(stone))
        assertEquals("全部のスキル Lv +1", CoreAffixCatalog.describe(CoreAffixStone(UUID.randomUUID(), all.id, 1, 1.0)))
        // Every class's levelable skill has its stone.
        CoreClass.entries.forEach { job -> levels.levelable(job).forEach { icon ->
            assertNotNull(CoreAffixCatalog.definitions.singleOrNull { CoreAffixCatalog.skillOf(CoreAffixStone(UUID.randomUUID(), it.id, 1, 1.0)) == icon }, icon)
        } }
    }

    @Test fun `raising and resetting go through the server and reset costs silver like passives`() {
        val dir = Files.createTempDirectory("skill-levels-"); val id = UUID.randomUUID()
        val start = CoreAccount(id, silver = 1000, journey = atLevel(5))
        Files.writeString(dir.resolve("$id.account"), CoreAccountCodec.encode(start))
        val service = CoreAccountService(CoreAccountRepository(dir))
        var revision = assertIs<CoreAccountLoadResult.Ready>(service.open(id)).account.revision
        fun run(action: CoreAction) = service.transact(id, CoreOperation(UUID.randomUUID(), revision, action)).also { r -> r.account?.let { revision = it.revision } }
        repeat(4) { assertEquals(CoreTransactionStatus.COMMITTED, run(CoreAction.RaiseSkill("whirl")).status) }
        assertEquals(CoreTransactionStatus.REJECTED, run(CoreAction.RaiseSkill("whirl")).status, "out of points")
        val reset = run(CoreAction.ResetSkillLevels)
        assertEquals(CoreTransactionStatus.COMMITTED, reset.status, reset.message)
        assertEquals(1000 - CorePassiveTree.refundPrice(start.journey) * 4, reset.account!!.silver)
        assertEquals(emptyMap(), reset.account!!.journey.build.skillLevels)
    }

    @Test fun `levels survive a save, per class, and saves from before have every skill at Lv 1`() {
        val mage = CoreClassBuild(skillLevels = mapOf("firebolt" to 3))
        val j = CoreJourney.legacy(CoreClass.MAGE, build = mage).changeClass(CoreClass.WARRIOR)
            .let { it.copy(build = it.build.copy(skillLevels = mapOf("slam" to 4, "dash" to 2))) }
        val a = CoreAccount(UUID.randomUUID(), journey = j)
        val text = CoreAccountCodec.encode(a)
        assertTrue("skill-level\tWARRIOR\tdash:2,slam:4\n" in text, text)
        val decoded = CoreAccountCodec.decode(text, a.playerId)
        assertEquals(j, decoded.journey)
        assertEquals(mapOf("firebolt" to 3), decoded.journey.savedBuilds.getValue(CoreClass.MAGE).skillLevels)
        // Nothing spent: no row at all, so the save reads exactly as one written before skill levels.
        val plain = CoreAccount(UUID.randomUUID(), journey = atLevel(10))
        val before = CoreAccountCodec.encode(plain)
        assertFalse("skill-level" in before)
        assertEquals(emptyMap(), CoreAccountCodec.decode(before, plain.playerId).journey.build.skillLevels)
    }
}
