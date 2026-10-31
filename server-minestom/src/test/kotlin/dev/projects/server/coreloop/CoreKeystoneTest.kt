package dev.projects.server.coreloop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreKeystoneTest {
    private fun keys(vararg k: CoreKeystone) = k.toSet()
    private fun stats(vararg pairs: Pair<CoreAffixStat, Double>) = CoreAffixStats(additional = pairs.toMap())

    @Test fun `damage keystones multiply outgoing damage`() {
        assertEquals(1.0, CoreKeystone.damage(emptySet()), 1e-9)
        assertEquals(1.0, CoreKeystone.damage(keys(CoreKeystone.BERSERKER_BLOOD), missingHealth = 0.0), 1e-9)
        assertEquals(1.2, CoreKeystone.damage(keys(CoreKeystone.BERSERKER_BLOOD), missingHealth = .5), 1e-9)
        assertEquals(1.4, CoreKeystone.damage(keys(CoreKeystone.BERSERKER_BLOOD), missingHealth = 1.0), 1e-9)
        assertEquals(.85, CoreKeystone.damage(keys(CoreKeystone.SANCTUARY)), 1e-9)
        assertEquals(.8, CoreKeystone.damage(keys(CoreKeystone.CREEPING_ROT)), 1e-9)
        assertEquals(1.0, CoreKeystone.damage(keys(CoreKeystone.CREEPING_ROT), direct = false), 1e-9)
        assertEquals(1.35, CoreKeystone.damage(keys(CoreKeystone.GLASS_CANNON)), 1e-9)
        assertEquals(.9, CoreKeystone.damage(keys(CoreKeystone.LIFE_CYCLE)), 1e-9)
        assertEquals(1.0, CoreKeystone.damage(keys(CoreKeystone.SHADOW_STEP)), 1e-9)
        assertEquals(1.3, CoreKeystone.damage(keys(CoreKeystone.SHADOW_STEP), afterDodge = true), 1e-9)
        assertEquals(1.3, CoreKeystone.damage(keys(CoreKeystone.ONE_STRIKE)), 1e-9)
        assertEquals(.85, CoreKeystone.damage(keys(CoreKeystone.BREAKER)), 1e-9)
        assertEquals(1.0, CoreKeystone.damage(keys(CoreKeystone.BREAKER), boss = true), 1e-9)
        assertEquals(1.2, CoreKeystone.damage(keys(CoreKeystone.LONE_WOLF), alone = true), 1e-9)
        assertEquals(1.0, CoreKeystone.damage(keys(CoreKeystone.LONE_WOLF), alone = false), 1e-9)
        assertEquals(1.5, CoreKeystone.breakAmount(keys(CoreKeystone.BREAKER)), 1e-9)
    }

    @Test fun `defensive keystones and their prices`() {
        assertEquals(.8, CoreKeystone.taken(keys(CoreKeystone.IRON_WILL)), 1e-9)
        assertEquals(1.1, CoreKeystone.taken(keys(CoreKeystone.SHADOW_STEP)), 1e-9)
        assertEquals(.9, CoreKeystone.taken(keys(CoreKeystone.LONE_WOLF), alone = true), 1e-9)
        assertEquals(1.0, CoreKeystone.taken(keys(CoreKeystone.LONE_WOLF), alone = false), 1e-9)
        assertEquals(.7, CoreKeystone.incomingHealing(keys(CoreKeystone.BERSERKER_BLOOD)), 1e-9)
        val gear = CoreCombatGear()
        val plain = CoreCombatSheet.from(gear, CoreAffixStats())
        assertEquals(plain.health * .7, CoreCombatSheet.from(gear, CoreAffixStats(keystones = keys(CoreKeystone.GLASS_CANNON))).health, 1e-9)
        assertEquals(plain.health * .85, CoreCombatSheet.from(gear, CoreAffixStats(keystones = keys(CoreKeystone.ENDLESS_HUNT))).health, 1e-9)
        assertEquals(plain.mana * .6, CoreCombatSheet.from(gear, CoreAffixStats(keystones = keys(CoreKeystone.MANA_CYCLE))).mana, 1e-9)
        assertEquals(30.0, CoreKeystone.manaShield(100.0, 500.0), 1e-9)
        assertEquals(12.0, CoreKeystone.manaShield(100.0, 12.0), 1e-9)
        assertEquals(1.0, CoreKeystone.bloodPrice(10.0, 40), 1e-9)
        assertEquals(60.0, CoreKeystone.bloodPrice(100.0, 40), 1e-9)
    }

    @Test fun `critical keystones`() {
        val crit = CoreAffixStats(critChanceIncreasedPercent = 100.0)
        assertEquals(.1, CoreKeystone.critChance(crit, still = false), 1e-9)
        assertEquals(0.0, CoreKeystone.critChance(crit.copy(keystones = keys(CoreKeystone.ONE_STRIKE)), still = true), 1e-9)
        val patient = crit.copy(keystones = keys(CoreKeystone.HUNTER_PATIENCE))
        assertEquals(.1, CoreKeystone.critChance(patient, still = false), 1e-9)
        assertEquals(.2, CoreKeystone.critChance(patient, still = true), 1e-9)
        assertEquals(1.0, CoreKeystone.dotCritical(crit, 0.0), 1e-9)
        val rot = crit.copy(keystones = keys(CoreKeystone.CREEPING_ROT))
        assertEquals(rot.criticalMultiplier, CoreKeystone.dotCritical(rot, .05), 1e-9)
        assertEquals(1.0, CoreKeystone.dotCritical(rot, .5), 1e-9)
    }

    @Test fun `self conditions turn on from what the actor tracks`() {
        fun mask(tick: Long = 1000, kill: Long = -1, dodge: Long = -1, stillSince: Long = 1000, moved: Long = -1, hp: Double = .8) =
            CorePassiveCondition.selfMask(tick, kill, dodge, stillSince, moved, hp)
        fun has(m: Int, c: CorePassiveCondition) = m and c.bit != 0
        assertEquals(0, mask())
        assertTrue(has(mask(kill = 930), CorePassiveCondition.RECENT_KILL))
        assertTrue(!has(mask(kill = 900), CorePassiveCondition.RECENT_KILL))
        assertTrue(has(mask(dodge = 970), CorePassiveCondition.AFTER_DODGE))
        assertTrue(!has(mask(dodge = 950), CorePassiveCondition.AFTER_DODGE))
        assertTrue(has(mask(stillSince = 950), CorePassiveCondition.STILL))
        assertTrue(!has(mask(stillSince = 980), CorePassiveCondition.STILL))
        assertTrue(has(mask(moved = 998), CorePassiveCondition.MOVING))
        assertTrue(has(mask(hp = .5), CorePassiveCondition.LOW_LIFE))
        assertTrue(has(mask(hp = 1.0), CorePassiveCondition.FULL_LIFE))
        assertEquals(CorePassiveCondition.VS_BOSS.bit or CorePassiveCondition.VS_CONTROLLED.bit, CorePassiveCondition.targetMask(true, true))
    }

    @Test fun `conditional values count only while the condition holds and stay under the caps`() {
        val base = CoreAffixCatalog.capped(mapOf(CoreAffixStat.DAMAGE to 90.0),
            mapOf(CorePassiveCondition.VS_BOSS to mapOf(CoreAffixStat.DAMAGE to 30.0),
                CorePassiveCondition.STILL to mapOf(CoreAffixStat.AR_PERCENT to 25.0)), emptySet())
        assertEquals(90.0, base.damagePercent, 1e-9)
        assertSame(base, base.under(CorePassiveCondition.MOVING.bit))
        assertEquals(100.0, base.under(CorePassiveCondition.VS_BOSS.bit).damagePercent, 1e-9)  // capped at 100
        assertEquals(25.0, base.under(CorePassiveCondition.STILL.bit).bonus(CoreAffixStat.AR_PERCENT), 1e-9)
        assertEquals(0.0, base.under(CorePassiveCondition.VS_BOSS.bit).bonus(CoreAffixStat.AR_PERCENT), 1e-9)
        assertSame(base.under(CorePassiveCondition.STILL.bit), base.under(CorePassiveCondition.STILL.bit or CorePassiveCondition.MOVING.bit))
    }

    @Test fun `armour and resist percent multiply the class base too`() {
        val journey = CoreJourney.legacy(CoreClass.WARRIOR)
        val gear = CoreCombatGear(1, 1)
        val none = CoreCombatSheet.from(gear, CoreAffixStats()).specialize(journey)
        val boosted = CoreCombatSheet.from(gear, stats(CoreAffixStat.AR_PERCENT to 50.0, CoreAffixStat.MR_PERCENT to 20.0)).specialize(journey)
        assertEquals(CoreClass.WARRIOR.baseArmor, none.ar, 1e-9)
        assertEquals(CoreClass.WARRIOR.baseArmor * 1.5, boosted.ar, 1e-9)
        assertEquals(CoreClass.WARRIOR.baseResist * 1.2, boosted.mr, 1e-9)
        val flat = CoreCombatSheet.from(gear, stats(CoreAffixStat.AR_FLAT to 20.0, CoreAffixStat.AR_PERCENT to 50.0)).specialize(journey)
        assertEquals((CoreClass.WARRIOR.baseArmor + 20) * 1.5, flat.ar, 1e-9)
    }

    @Test fun `incoming healing applies to any heal received, heal on kill and overheal shield`() {
        assertEquals(100.0, CoreCombatMath.incomingHealing(100.0, CoreAffixStats()), 1e-9)
        assertEquals(150.0, CoreCombatMath.incomingHealing(100.0, stats(CoreAffixStat.INCOMING_HEALING to 50.0)), 1e-9)
        assertEquals(30.0, CoreCombatMath.healOnKill(1000.0, stats(CoreAffixStat.HEAL_ON_KILL to 3.0)), 1e-9)
        assertEquals(200.0, CoreCombatMath.healOnKill(1000.0, stats(CoreAffixStat.HEAL_ON_KILL to 90.0)), 1e-9)
        assertEquals(0.0, CoreCombatMath.overhealShield(50.0, CoreAffixStats()), 1e-9)
        assertEquals(10.0, CoreCombatMath.overhealShield(50.0, stats(CoreAffixStat.OVERHEAL_SHIELD to 20.0)), 1e-9)
    }
}
