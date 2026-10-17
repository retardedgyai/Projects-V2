package dev.projects.server.coreloop

import kotlin.test.*

class CoreWarriorSkillPresentationTest {
    @Test fun `all ten skills have concrete identities and every selectable candidate is reachable without paging`() {
        assertEquals(CoreSkillCatalog.skills(CoreClass.WARRIOR).map { it.icon }.toSet(),CoreWarriorSkillPresentation.skills.keys)
        assertEquals(CoreSkillCatalog.normalChoices(CoreClass.WARRIOR),CoreWarriorSkillPresentation.candidateOrder.sorted())
        assertTrue(CoreWarriorSkillPresentation.candidateOrder.size<=CoreWarriorSkillPresentation.candidateSlots.size)
        val occupied=CoreWarriorSkillPresentation.candidateSlots.flatMap { it until it+4 }
        assertEquals(32,occupied.distinct().size)
        assertTrue(occupied.none { it in CoreWarriorSkillPresentation.loadoutSlots || it>=45 })
    }
    @Test fun `caption reads actual defensive windows and vanishes when consumed or expired`() {
        fun cue(t: Long,c: Long,g: Long)=CoreWarriorSkillPresentation.combatCue(CoreClass.WARRIOR,t,c,g)
        assertEquals("",cue(0,-1,-1))
        assertEquals("踏ん張り 2秒",cue(10,-1,40))
        // The warrior has no parry counter any more; a counter window never shows.
        assertEquals("踏ん張り 2秒",cue(10,70,40))
        assertEquals("",cue(71,70,40))
        for(job in CoreClass.entries.filter { it!=CoreClass.WARRIOR })
            assertEquals("",CoreWarriorSkillPresentation.combatCue(job,10,70,40))
    }
    @Test fun `tooltip keeps actual coefficients costs status and modifier notes`() {
        val j=CoreJourney.legacy(job=CoreClass.WARRIOR)
        val sheet=CoreCombatSheet.from(CoreAccount(java.util.UUID.randomUUID()))
        for(s in CoreSkillCatalog.skills(CoreClass.WARRIOR)) {
            val modified=CoreSkillCatalog.modify(s,j,sheet.mods)
            val text=CoreWarriorSkillPresentation.tooltip(modified,sheet,j)
            assertTrue(text.containsAll(modified.tooltip(sheet,j).drop(2)))
            assertEquals(CoreWarriorSkillPresentation.skills.getValue(s.icon).role,text.first())
        }
    }
}
