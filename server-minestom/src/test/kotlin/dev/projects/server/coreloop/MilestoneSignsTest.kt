package dev.projects.server.coreloop

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MilestoneSignsTest {
    private val base = CoreAccount(UUID.randomUUID()).let { it.copy(journey = it.journey.changeClass(CoreClass.WARRIOR)) }

    @Test fun `each milestone gets its sign and a plain change gets none`() {
        val levelled = base.copy(journey = base.journey.gain(CoreJourneyRules.threshold(3)))
        assertEquals(MilestoneSigns.Milestone(MilestoneSigns.Kind.LEVEL, "冒険 Lv 3"), MilestoneSigns.of(base, levelled))

        // Skill points come with 冒険 Lv, so the raise is from an account that already has them.
        val skilled = levelled.copy(journey = levelled.journey.copy(build = levelled.journey.build.copy(skillLevels = mapOf("dash" to 2))))
        assertEquals(MilestoneSigns.Milestone(MilestoneSigns.Kind.SKILL, "轟進 Lv 2"), MilestoneSigns.of(levelled, skilled))

        val cleared = base.copy(dungeonClears = mapOf(CoreDungeons.SAKURA to 1))
        assertEquals(MilestoneSigns.Milestone(MilestoneSigns.Kind.CLEAR, "散り桜の社"), MilestoneSigns.of(base, cleared))
        // The second clear is not a first one.
        assertNull(MilestoneSigns.of(cleared, cleared.copy(dungeonClears = mapOf(CoreDungeons.SAKURA to 2))))
        assertNull(MilestoneSigns.of(base, base.copy(silver = 10)))
    }
}
