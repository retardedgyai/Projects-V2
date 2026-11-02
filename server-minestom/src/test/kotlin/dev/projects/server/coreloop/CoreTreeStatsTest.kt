package dev.projects.server.coreloop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTreeStatsTest {
    private fun stats(vararg pairs: Pair<CoreAffixStat, Double>) = CoreAffixStats(additional = pairs.toMap())

    @Test fun `everyone starts with healing power and it feeds lifesteal and regeneration`() {
        val sheet = CoreCombatSheet.from(CoreCombatGear(1, 1, CoreWeaponBase.STANDARD), CoreAffixStats())
        assertEquals(CoreCombatMath.BASE_HEALING_POWER, sheet.healingPower, 1e-9)
        val boosted = CoreCombatSheet.from(CoreCombatGear(1, 1, CoreWeaponBase.STANDARD), stats(CoreAffixStat.HEALING_PERCENT to 50.0))
        assertEquals(30.0, boosted.healingPower, 1e-9)
        val steal = stats(CoreAffixStat.LIFESTEAL to 10.0)
        assertEquals(10.0, CoreCombatMath.lifeSteal(100.0, steal, false), 1e-9)
        assertEquals(13.0, CoreCombatMath.lifeSteal(100.0, steal, false, boosted.healingPower), 1e-9)
        assertEquals(1.5, CoreCombatMath.healthRegeneration(30.0, false), 1e-9)
        assertEquals(4.5, CoreCombatMath.healthRegeneration(30.0, true), 1e-9)
    }

    @Test fun `crowd control time, break, damage over time and dodge grow and stop at their caps`() {
        assertEquals(1500L, CoreCombatMath.crowdMillis(1000, stats(CoreAffixStat.CC_DURATION to 50.0)))
        assertEquals(2000L, CoreCombatMath.crowdMillis(1000, stats(CoreAffixStat.CC_DURATION to 500.0)))
        assertEquals(12.0, CoreCombatMath.breakAmount(10.0, stats(CoreAffixStat.BREAK_POWER to 20.0)), 1e-9)
        assertEquals(30.0, CoreCombatMath.dot(10.0, stats(CoreAffixStat.DOT_DAMAGE to 900.0)), 1e-9)
        assertEquals(24L, CoreCombatMath.dodgeTicks(24, CoreAffixStats()))
        assertEquals(16L, CoreCombatMath.dodgeTicks(24, stats(CoreAffixStat.DODGE_RECOVERY to 90.0)))
    }

    @Test fun `seventy levels give sixty nine points`() {
        assertEquals(70, CoreJourneyRules.MAX_LEVEL)
        assertEquals(70, CoreJourneyRules.level(CoreJourneyRules.threshold(70)))
        assertEquals(69, CorePassiveTree.budget(CoreJourney.fresh().copy(xp = CoreJourneyRules.threshold(70))))
    }

    @Test fun `AP nodes sit only by the casters, the only classes whose weapon has AP`() {
        val starts = CorePassiveTree.nodes.filter { it.start != null }
        for (n in CorePassiveTree.nodes.filter { CoreAffixStat.AP_PERCENT in it.stats }) {
            val nearest = starts.minBy { (it.x - n.x).toDouble().let { dx -> dx * dx } + (it.y - n.y).toDouble().let { dy -> dy * dy } }.start
            assertTrue(nearest == CoreClass.MAGE || nearest == CoreClass.HEALER, "${n.name} (${n.id}) は ${nearest} の近く")
        }
    }

    @Test fun `element effect grows what the elements do, and the alliance puts every element into the highest`() {
        assertEquals(1.0, CoreCombatMath.elementScale(CoreAffixStats()), 1e-9)
        assertEquals(1.4, CoreCombatMath.elementScale(stats(CoreAffixStat.ELEMENT_EFFECT to 40.0)), 1e-9)
        assertEquals(3.0, CoreCombatMath.elementScale(stats(CoreAffixStat.ELEMENT_EFFECT to 900.0)), 1e-9)
        val gear = mapOf(CoreAffixStat.FIRE to 6.0, CoreAffixStat.ICE to 9.0, CoreAffixStat.LIGHTNING to 3.0)
        val apart = CoreAffixCatalog.capped(gear, emptyMap(), emptySet())
        assertEquals(listOf(6.0, 9.0, 3.0), listOf(apart.fireFlat, apart.iceFlat, apart.lightningFlat))
        val allied = CoreAffixCatalog.capped(gear, emptyMap(), setOf(CoreKeystone.ELEMENTAL_ALLIANCE))
        assertEquals(listOf(0.0, 18.0, 0.0), listOf(allied.fireFlat, allied.iceFlat, allied.lightningFlat))
        assertTrue(CorePassiveTree.nodes.none { n -> n.stats.keys.any { it in setOf(CoreAffixStat.FIRE, CoreAffixStat.ICE, CoreAffixStat.LIGHTNING) } },
            "the tree gives no element value itself")
        assertTrue(CorePassiveTree.nodes.any { it.keystone == CoreKeystone.ELEMENTAL_ALLIANCE })
    }
}
