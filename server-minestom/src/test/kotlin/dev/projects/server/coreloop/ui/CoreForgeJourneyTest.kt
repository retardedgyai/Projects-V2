package dev.projects.server.coreloop.ui

import dev.projects.server.coreloop.*
import kotlin.test.*

class CoreForgeJourneyTest {
    @Test fun `ingredient return restores exact original goal without an action`() {
        val goal = CoreForgeLayout.Selection(tab = CoreForgeLayout.Tab.CRAFT, gear = CoreGearSlot.ARMOR,
            tier = 4, recipe = 3, quantity = CoreForgeLayout.Quantity.MAX,
            purpose = CoreForgeLayout.Purpose.TUNE, currency = CoreCraftingCurrency.DIVINE, focused = true)
        val journey = CoreForgeJourney()
        journey.push(goal)
        val refine = assertNotNull(CoreForgeLayout.refineSelection(CoreMaterial(CoreResource.CLOTH, 2), goal))
        journey.push(refine)
        assertEquals(2, journey.depth)
        assertEquals(refine, journey.peek())
        assertEquals(refine, journey.pop()) // Supplies -> refine.
        assertEquals(goal, journey.pop()) // Refine -> original craft target and count.
        assertTrue(journey.isEmpty)
    }

    @Test fun `duplicate source clicks do not build an endless back chain`() {
        val journey = CoreForgeJourney()
        val goal = CoreForgeLayout.Selection()
        repeat(100) { journey.push(goal) }
        assertEquals(1, journey.depth)
        assertEquals(goal, journey.pop())
        assertNull(journey.pop())
        assertNull(journey.peek())
    }

    @Test fun `overflow retains the eight most recent goals in order`() {
        val journey = CoreForgeJourney()
        val goals = (0 until 20).map { CoreForgeLayout.Selection(recipe = it) }
        goals.forEach(journey::push)
        assertEquals(CoreForgeJourney.MAX_DEPTH, journey.depth)
        repeat(CoreForgeJourney.MAX_DEPTH) { offset -> assertEquals(goals[19 - offset], journey.pop()) }
        assertNull(journey.pop())
    }

    @Test fun `opening an unrelated workflow can discard the old return path`() {
        val journey = CoreForgeJourney()
        journey.push(CoreForgeLayout.Selection())
        journey.clear()
        assertEquals(0, journey.depth)
        assertTrue(journey.isEmpty)
        assertNull(journey.peek())
    }

    @Test fun `players cannot see or pop another player's craft goal`() {
        val first = CoreForgeJourney()
        val second = CoreForgeJourney()
        val goal = CoreForgeLayout.Selection(gear = CoreGearSlot.ARMOR)
        first.push(goal)
        assertTrue(second.isEmpty)
        assertNull(second.pop())
        assertEquals(goal, first.pop())
    }
}
