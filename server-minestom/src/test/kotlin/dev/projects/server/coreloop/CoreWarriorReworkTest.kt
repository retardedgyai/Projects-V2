package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

/** 2026-10 cooldown-first warrior: cooldown tiers, retired catalog entries and save migration. */
class CoreWarriorReworkTest {
    private val warrior get() = CoreSkillCatalog.skills(CoreClass.WARRIOR)

    @Test fun `warrior skills sit in cooldown tiers and the beast gates nothing`() {
        val byIcon = warrior.filterNot { it.retired }.associateBy { it.icon }
        assertEquals(setOf("dash", "slam", "whirl", "war_guard", "war_wound", "war_counter", CoreWarriorRules.PUSH, CoreWarriorRules.TIP,
            "war_banner", "war_awaken"), byIcon.keys)
        assertTrue(listOf(CoreWarriorRules.PUSH, CoreWarriorRules.TIP).all { byIcon.getValue(it).cooldown in 120..180 }, "the new builders: 7-8 s")
        assertEquals(100, byIcon.getValue("dash").cooldown)
        assertTrue(listOf("slam", "whirl", "war_guard", "war_counter").all { byIcon.getValue(it).cooldown in 200..320 }, "spenders")
        assertTrue(byIcon.getValue("war_wound").cooldown in 160..240, "the leap moves")
        assertTrue(listOf("war_banner", "war_awaken").all { byIcon.getValue(it).cooldown >= 1200 }, "ultimates")
        assertTrue(byIcon.values.all { it.spend == 0 }, "no warrior skill needs the beast to be cast")
    }

    @Test fun `retired entries keep catalog order and art but are never selectable`() {
        assertEquals(61, CoreSkillCatalog.artNames.size)
        // 引き摺り (6) and 斬り上げ (7) were retired (2026-10-27); 押し払い and 切っ先薙ぎ took their slots (2026-10-28).
        assertEquals((0..7).toList(), CoreSkillCatalog.normalChoices(CoreClass.WARRIOR))
        assertEquals(listOf(CoreWarriorRules.PUSH, CoreWarriorRules.TIP), warrior.subList(6, 8).map { it.icon })
        // 全力の一振り (index 8, ultimate 0) is retired (2026-10-27): it is 地割り at a full beast now; its slot stays empty.
        assertEquals(listOf(1, 2), CoreSkillCatalog.ultimateChoices(CoreClass.WARRIOR))
        assertEquals(listOf("war_ult"), warrior.filter { it.retired }.map { it.icon })
        assertEquals(8, warrior.indexOfFirst { it.icon == CoreWarriorRules.FULL_SWING })
        for (job in CoreClass.entries) {
            val fresh = CoreSkillCatalog.defaultBuild(job)
            assertEquals(fresh, CoreSkillCatalog.migrate(job, fresh), "$job default build stays valid")
            assertEquals(if (job == CoreClass.WARRIOR) CoreClassBuild(ultimate = 1) else CoreClassBuild(), fresh)
            assertEquals(fresh, CoreJourney(job = job).build)
            assertFalse(CoreSkillCatalog.selectable(job, 0, 8))
            for (i in 0..7) assertEquals(!CoreSkillCatalog.skills(job)[i].retired, CoreSkillCatalog.selectable(job, 0, i))
        }
    }

    @Test fun `migration replaces only retired picks and keeps slot order tree and ultimate when valid`() {
        val old = CoreClassBuild(first = 0, second = 1, third = 2, fourth = 3, ultimate = 1, nodes = 1031)
        val migrated = CoreSkillCatalog.migrate(CoreClass.WARRIOR, old)
        assertEquals(listOf(0, 1, 2, 3), migrated.skills); assertEquals(1, migrated.ultimate); assertEquals(1031, migrated.nodes)
        assertEquals(migrated, CoreSkillCatalog.migrate(CoreClass.WARRIOR, migrated))
        assertEquals(old, CoreSkillCatalog.migrate(CoreClass.MAGE, old), "classes without retired skills are untouched")
    }

    @Test fun `a save with 全力の一振り equipped loads with another ultimate and never equips it`() {
        val old = CoreClassBuild(ultimate = 0, nodes = 1031)
        val migrated = CoreSkillCatalog.migrate(CoreClass.WARRIOR, old)
        assertEquals(1, migrated.ultimate); assertEquals(old.skills, migrated.skills); assertEquals(1031, migrated.nodes)
        val a = CoreAccount(UUID.randomUUID(), journey = CoreJourney.legacy(build = old))
        assertEquals(1, CoreAccountCodec.decode(CoreAccountCodec.encode(a), a.playerId).journey.build.ultimate)
        // Even a build that never went through a save never equips it.
        assertEquals(CoreWarriorRules.ARMOR, CoreSkillCatalog.equipped(CoreJourney.legacy(build = old))[4].icon)
        assertFalse(CoreSkillCatalog.selectable(CoreClass.WARRIOR, 4, 0))
    }

    @Test fun `saved builds with retired skills load migrated through the codec and rewrite stably`() {
        val legacyWarrior = CoreClassBuild(first = 0, second = 1, third = 2, fourth = 3, ultimate = 1)
        val j = CoreJourney.legacy(build = legacyWarrior).changeClass(CoreClass.HEALER)
        val a = CoreAccount(UUID.randomUUID(), journey = j)
        val loaded = CoreAccountCodec.decode(CoreAccountCodec.encode(a), a.playerId)
        val saved = loaded.journey.savedBuilds.getValue(CoreClass.WARRIOR)
        assertEquals(listOf(0, 1, 2, 3), saved.skills); assertEquals(1, saved.ultimate)
        assertEquals(CoreAccountCodec.encode(loaded), CoreAccountCodec.encode(CoreAccountCodec.decode(CoreAccountCodec.encode(loaded), a.playerId)))
        assertTrue(CoreAccountCodec.encode(loaded).startsWith("PROJECTS_CORE_LOOP\t12\t"))
        val current = CoreAccount(UUID.randomUUID(), journey = CoreJourney.legacy(build = legacyWarrior))
        assertEquals(listOf(0, 1, 2, 3), CoreAccountCodec.decode(CoreAccountCodec.encode(current), current.playerId).journey.build.skills)
    }

    @Test fun `a retired skill cannot be equipped through the account service`() {
        val dir = Files.createTempDirectory("warrior-rework-")
        val id = UUID.randomUUID()
        val repo = CoreAccountRepository(dir)
        assertEquals(CoreRepositorySave.Saved, repo.commit(0, CoreAccount(id, revision = 1, journey = CoreJourney.legacy())))
        val service = CoreAccountService(repo); service.open(id)
        assertEquals(CoreTransactionStatus.REJECTED, service.transact(id, CoreOperation(UUID.randomUUID(), 1, CoreAction.SelectSkill(4, 3))).status)
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

    @Test fun `spender tooltips state the beast and finisher rules in whole numbers`() {
        val j = CoreJourney.legacy()
        val sheet = CoreCombatSheet.from(CoreAccount(UUID.randomUUID(), journey = j))
        val slam = warrior.first { it.icon == "slam" }.tooltip(sheet, j).joinToString("\n")
        assertContains(slam, "獣：全部使う（0でも撃てる）")
        assertContains(slam, "獣1つごとに威力+25%")
        assertContains(slam, "振っている間は怯まない")
        assertContains(slam, "通常3段目の命中から0.6秒以内")
        assertContains(slam, "ボスの崩し 12＋獣1つごとに+4")
        assertFalse("30.0" in slam)
        assertContains(warrior.first { it.icon == "dash" }.tooltip(sheet, j).joinToString("\n"), "獣：当たると1つ溜まる")
    }
}
