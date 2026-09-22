package dev.projects.server.coreloop

import java.util.UUID
import kotlin.test.*

class CoreCombatLabTest {
    @Test fun `all classes get compatible temporary equipment with all five slots unlocked`() {
        val loadout = CoreLabLoadout(UUID.randomUUID(), CoreClass.WARRIOR)
        for (job in CoreClass.entries) {
            loadout.changeClass(job)
            val a = loadout.account()
            assertEquals(job, a.journey.job)
            assertEquals(40, a.journey.level)
            assertTrue(a.weaponIdentity.base.usable(job))
            assertTrue((0..4).all { CoreJourneyRules.skillUnlocked(a, it) })
            assertNull(a.activeRun)
            assertTrue(a.balances.isEmpty())
            assertTrue(a.maps.isEmpty())
            assertEquals(0, a.silver)
        }
    }

    @Test fun `every normal skill and ultimate can be equipped while preserving distinct slots`() {
        val loadout = CoreLabLoadout(UUID.randomUUID(), CoreClass.WARRIOR)
        for (job in CoreClass.entries) {
            loadout.changeClass(job)
            for (slot in 0..3) for (choice in 0..7) {
                loadout.selectedSlot = slot; loadout.equip(choice)
                assertEquals(choice, loadout.journey.build.skills[slot])
                assertEquals(4, loadout.journey.build.skills.distinct().size)
            }
            for (choice in 8..9) { loadout.equip(choice); assertEquals(choice - 8, loadout.journey.build.ultimate) }
        }
    }

    @Test fun `class switching remembers temporary build but never mutates the saved account`() {
        val original = CoreAccount(UUID.randomUUID(), weaponTier = 4, silver = 9876)
        val loadout = CoreLabLoadout(original.playerId, original.journey.job)
        loadout.equip(7); loadout.changeClass(CoreClass.MAGE); loadout.equip(6); loadout.changeClass(CoreClass.WARRIOR)
        assertEquals(7, loadout.journey.build.first)
        assertEquals(0, original.journey.build.first)
        assertEquals(4, original.weaponTier)
        assertEquals(9876, original.silver)
        assertEquals(1, loadout.account().weaponTier)
        assertTrue(loadout.free)
        assertFalse(loadout.retaliation)
    }
}
