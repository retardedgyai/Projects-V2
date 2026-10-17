package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

/** 2026-10 cooldown-first warrior: cooldown tiers, retired catalog entries and save migration. */
class CoreWarriorReworkTest {
    private val warrior get() = CoreSkillCatalog.skills(CoreClass.WARRIOR)

    @Test fun `warrior skills sit in cooldown tiers and rage gates only the ultimate`() {
        val byIcon = warrior.filterNot { it.retired }.associateBy { it.icon }
        assertEquals(setOf("dash", "slam", "whirl", "war_guard", "war_wound", "war_ult"), byIcon.keys)
        assertEquals(100, byIcon.getValue("dash").cooldown)
        assertTrue(byIcon.getValue("slam").cooldown in 200..320 && byIcon.getValue("whirl").cooldown in 200..320)
        assertTrue(byIcon.getValue("war_guard").cooldown in 320..600 && byIcon.getValue("war_wound").cooldown in 320..600)
        assertTrue(byIcon.getValue("war_ult").cooldown >= 1200)
        assertTrue(byIcon.values.filterNot { it.ultimate }.all { it.spend == 0 }, "no normal skill needs rage")
        assertEquals(80, byIcon.getValue("war_ult").spend)
    }

    @Test fun `retired entries keep catalog order and art but are never selectable`() {
        assertEquals(70, CoreSkillCatalog.artNames.size)
        assertEquals(listOf(0, 1, 2, 3, 4), CoreSkillCatalog.normalChoices(CoreClass.WARRIOR))
        assertEquals(listOf(0), CoreSkillCatalog.ultimateChoices(CoreClass.WARRIOR))
        assertEquals(listOf("war_counter", "war_cry", "war_breach", "war_banner"), warrior.filter { it.retired }.map { it.icon })
        for (job in CoreClass.entries) {
            assertEquals(CoreClassBuild(), CoreSkillCatalog.migrate(job, CoreClassBuild()), "$job default build stays valid")
            assertFalse(CoreSkillCatalog.selectable(job, 0, 8))
            for (i in 0..7) assertEquals(!CoreSkillCatalog.skills(job)[i].retired, CoreSkillCatalog.selectable(job, 0, i))
        }
    }

    @Test fun `migration replaces only retired picks and keeps slot order tree and ultimate when valid`() {
        val old = CoreClassBuild(first = 5, second = 1, third = 7, fourth = 3, ultimate = 1, nodes = 1031)
        val migrated = CoreSkillCatalog.migrate(CoreClass.WARRIOR, old)
        assertEquals(listOf(0, 1, 2, 3), migrated.skills); assertEquals(0, migrated.ultimate); assertEquals(1031, migrated.nodes)
        assertEquals(migrated, CoreSkillCatalog.migrate(CoreClass.WARRIOR, migrated))
        assertEquals(old, CoreSkillCatalog.migrate(CoreClass.MAGE, old), "classes without retired skills are untouched")
    }

    @Test fun `saved builds with retired skills load migrated through the codec and rewrite stably`() {
        val legacyWarrior = CoreClassBuild(first = 6, second = 1, third = 2, fourth = 7, ultimate = 1)
        val j = CoreJourney.legacy(build = legacyWarrior).changeClass(CoreClass.HEALER)
        val a = CoreAccount(UUID.randomUUID(), journey = j)
        val loaded = CoreAccountCodec.decode(CoreAccountCodec.encode(a), a.playerId)
        val saved = loaded.journey.savedBuilds.getValue(CoreClass.WARRIOR)
        assertEquals(listOf(0, 1, 2, 3), saved.skills); assertEquals(0, saved.ultimate)
        assertEquals(CoreAccountCodec.encode(loaded), CoreAccountCodec.encode(CoreAccountCodec.decode(CoreAccountCodec.encode(loaded), a.playerId)))
        assertTrue(CoreAccountCodec.encode(loaded).startsWith("PROJECTS_CORE_LOOP\t11\t"))
        val current = CoreAccount(UUID.randomUUID(), journey = CoreJourney.legacy(build = legacyWarrior))
        assertEquals(listOf(0, 1, 2, 3), CoreAccountCodec.decode(CoreAccountCodec.encode(current), current.playerId).journey.build.skills)
    }

    @Test fun `a retired skill cannot be equipped through the account service`() {
        val dir = Files.createTempDirectory("warrior-rework-")
        val id = UUID.randomUUID()
        val repo = CoreAccountRepository(dir)
        assertEquals(CoreRepositorySave.Saved, repo.commit(0, CoreAccount(id, revision = 1, journey = CoreJourney.legacy())))
        val service = CoreAccountService(repo); service.open(id)
        assertEquals(CoreTransactionStatus.REJECTED, service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(0, 5))).status)
        assertEquals(CoreTransactionStatus.REJECTED, service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(4, 1))).status)
        val accepted = service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(0, 4)))
        assertEquals(CoreTransactionStatus.COMMITTED, accepted.status, accepted.message)
        assertEquals(listOf(4, 1, 2, 3), accepted.account!!.journey.build.skills)
    }

    @Test fun `only templar guard reads open a counter`() {
        for (job in CoreClass.entries) {
            val s = CoreClassState(); s.perfectUntil = 12
            s.guarded(job, 1, CoreClassBuild())
            assertEquals(if (job == CoreClass.TEMPLAR) 61L else -1L, s.counterUntil, "$job")
        }
    }

    @Test fun `support tree node targets a selectable skill for every class`() {
        for (job in CoreClass.entries) {
            assertTrue(CoreSkillCatalog.selectable(job, 0, CoreClassTrees.support(job)))
            val j = CoreJourney.legacy(job = job, build = CoreClassBuild(nodes = (1 shl 6) or (1 shl 7) or (1 shl 15) or (1 shl 16)))
            val raw = CoreSkillCatalog.skills(job)[CoreClassTrees.support(job)]
            assertContains(CoreSkillCatalog.modify(raw, j, CoreAffixStats()).description, "広い備え")
        }
    }

    @Test fun `heavy skill tooltips state the rage and finisher rules in whole numbers`() {
        val j = CoreJourney.legacy()
        val sheet = CoreCombatSheet.from(CoreAccount(UUID.randomUUID(), journey = j))
        val slam = warrior.first { it.icon == "slam" }.tooltip(sheet, j).joinToString("\n")
        assertContains(slam, "闘気30以上なら自動で使い、威力+30%")
        assertContains(slam, "通常3段目の命中から0.6秒以内")
        assertFalse("30.0" in slam)
        assertContains(warrior.first { it.icon == "whirl" }.tooltip(sheet, j).joinToString("\n"), "闘気30以上")
        assertFalse("闘気30" in warrior.first { it.icon == "dash" }.tooltip(sheet, j).joinToString("\n"))
    }
}
