package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

/** 2026-10 class rework: role split, retired catalog entries, save migration and the shared feel table. */
class CoreClassReworkTest {
    @Test fun `every class offers resource paced main skills and long cooldown situational skills`() {
        for (job in CoreClass.entries) {
            val normals = CoreSkillCatalog.normalChoices(job).map { CoreSkillCatalog.skills(job)[it] }
            assertTrue(normals.size in 5..7, "$job offers ${normals.size}")
            assertTrue(CoreSkillCatalog.ultimateChoices(job).isNotEmpty())
            val main = normals.filter { it.role == "主力技" }
            val situational = normals.filter { it.role == "状況技" }
            assertTrue(main.size >= 2, "$job needs two main skills to choose between")
            assertTrue(situational.size >= 2, "$job needs situational answers")
            assertTrue(situational.all { it.cooldown >= 80 && it.spend == 0 || it.motion == CoreSkillMotion.SHIELD })
            // Physical main skills are paced by the class resource only.
            if (!job.magic) assertTrue(main.all { it.mana == 0 && it.spend > 0 }, "$job main skills")
            // Starweaver releases every star instead of a fixed spend.
            if (job != CoreClass.STARWEAVER) assertTrue(main.any { it.spend > 0 }, "$job has a spender")
        }
    }

    @Test fun `retired entries keep catalog order and art but are never selectable`() {
        assertEquals(70, CoreSkillCatalog.artNames.size)
        assertEquals(listOf(0, 1, 2, 3, 4), CoreSkillCatalog.normalChoices(CoreClass.WARRIOR))
        assertEquals(listOf(0), CoreSkillCatalog.ultimateChoices(CoreClass.WARRIOR))
        assertTrue(CoreSkillCatalog.skills(CoreClass.WARRIOR).filter { it.retired }.map { it.icon }
            .containsAll(listOf("war_counter", "war_cry", "war_breach", "war_banner")))
        for (job in CoreClass.entries) {
            // The default fresh build stays valid for every class.
            assertEquals(CoreClassBuild(), CoreSkillCatalog.migrate(job, CoreClassBuild()))
            assertFalse(CoreSkillCatalog.selectable(job, 0, 8))
            for (i in 0..7) assertEquals(!CoreSkillCatalog.skills(job)[i].retired, CoreSkillCatalog.selectable(job, 0, i))
        }
    }

    @Test fun `migration replaces only retired picks and keeps slot order tree and ultimate when valid`() {
        val old = CoreClassBuild(first = 5, second = 1, third = 7, fourth = 3, ultimate = 1, nodes = 1031)
        val warrior = CoreSkillCatalog.migrate(CoreClass.WARRIOR, old)
        assertEquals(listOf(0, 1, 2, 3), warrior.skills); assertEquals(0, warrior.ultimate); assertEquals(1031, warrior.nodes)
        val mage = CoreSkillCatalog.migrate(CoreClass.MAGE, old)
        assertEquals(listOf(5, 1, 0, 3), mage.skills); assertEquals(1, mage.ultimate)
        assertEquals(mage, CoreSkillCatalog.migrate(CoreClass.MAGE, mage))
    }

    @Test fun `saved builds with retired skills load migrated through the codec and rewrite stably`() {
        val legacyWarrior = CoreClassBuild(first = 6, second = 1, third = 2, fourth = 7, ultimate = 1)
        val legacyHealer = CoreClassBuild(first = 6, second = 7, third = 0, fourth = 1)
        val j = CoreJourney.legacy(build = legacyWarrior).changeClass(CoreClass.HEALER).copy(build = legacyHealer)
        val a = CoreAccount(UUID.randomUUID(), journey = j)
        val loaded = CoreAccountCodec.decode(CoreAccountCodec.encode(a), a.playerId)
        assertEquals(listOf(2, 3, 0, 1), loaded.journey.build.skills)
        val warrior = loaded.journey.savedBuilds.getValue(CoreClass.WARRIOR)
        assertEquals(listOf(0, 1, 2, 3), warrior.skills); assertEquals(0, warrior.ultimate)
        assertEquals(CoreAccountCodec.encode(loaded), CoreAccountCodec.encode(CoreAccountCodec.decode(CoreAccountCodec.encode(loaded), a.playerId)))
        assertTrue(CoreAccountCodec.encode(loaded).startsWith("PROJECTS_CORE_LOOP\t11\t"))
    }

    @Test fun `a retired skill cannot be equipped through the account service`() {
        val dir = Files.createTempDirectory("class-rework-")
        val id = UUID.randomUUID()
        val repo = CoreAccountRepository(dir)
        assertEquals(CoreRepositorySave.Saved, repo.commit(0, CoreAccount(id, revision = 1, journey = CoreJourney.legacy())))
        val service = CoreAccountService(repo); service.open(id)
        val rejected = service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(0, 5)))
        assertEquals(CoreTransactionStatus.REJECTED, rejected.status)
        val ultimate = service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(4, 1)))
        assertEquals(CoreTransactionStatus.REJECTED, ultimate.status)
        val accepted = service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(0, 4)))
        assertEquals(CoreTransactionStatus.COMMITTED, accepted.status, accepted.message)
        assertEquals(listOf(4, 1, 2, 3), accepted.account!!.journey.build.skills)
    }

    @Test fun `feel table covers all classes and recovery follows motion and class weight`() {
        for (job in CoreClass.entries) {
            val p = CoreCombatFeel.profile(job)
            assertEquals(6L, p.bufferTicks)
            assertEquals(job == CoreClass.WARRIOR, p.immediateNormal)
            assertEquals(job != CoreClass.TEMPLAR, p.attackBreaksGuard)
            for (s in CoreSkillCatalog.skills(job)) {
                val r = CoreCombatFeel.recovery(job, s)
                assertTrue(r in 0..7)
                if (s.motion == CoreSkillMotion.GUARD) assertEquals(0, r)
                assertEquals(CoreCombatFeel.lastImpact(s, 5) + r, CoreCombatFeel.finish(job, s, 5))
            }
        }
        assertTrue(CoreCombatFeel.profile(CoreClass.ASSASSIN).recovery < CoreCombatFeel.profile(CoreClass.TEMPLAR).recovery)
        assertEquals(.9, CoreCombatFeel.minDot("war_wound")); assertEquals(0.0, CoreCombatFeel.minDot("whirl"))
    }

    @Test fun `tooltips name the role and the shared buffer for every class`() {
        for (job in CoreClass.entries) {
            val j = CoreJourney.legacy(job = job)
            val sheet = CoreCombatSheet.from(CoreAccount(UUID.randomUUID(), journey = j))
            for (s in CoreSkillCatalog.equipped(j)) {
                val text = s.tooltip(sheet, j).joinToString("\n")
                assertContains(text, s.role); assertContains(text, "0.3秒先行受付")
            }
        }
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
            assertTrue(CoreSkillCatalog.selectable(job, 0, CoreClassTrees.signature(job)))
            val j = CoreJourney.legacy(job = job, build = CoreClassBuild(nodes = (1 shl 6) or (1 shl 7) or (1 shl 15) or (1 shl 16)))
            val raw = CoreSkillCatalog.skills(job)[CoreClassTrees.support(job)]
            assertContains(CoreSkillCatalog.modify(raw, j, CoreAffixStats()).description, "広い備え")
        }
    }
}
