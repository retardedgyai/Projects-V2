package dev.projects.server.coreloop

import kotlin.test.*

class CoreCombatMathTest {
    private val base = CoreCombatSheet(100.0, 200.0, 300.0, 600.0, 100.0, 100.0, 1.0, 20.0)
    @Test fun `coefficient and damage type are independent`() {
        val f = CoreDamageFormula(10.0, ad = .5, ap = 1.2)
        assertEquals(300.0, f.evaluate(base))
        assertEquals("10 + AD 50% + AP 120%", f.label())
        assertEquals(150.0, CoreCombatMath.mitigate(f.evaluate(base), CoreDamageType.PHYSICAL, 300.0, 600.0))
        assertEquals(100.0, CoreCombatMath.mitigate(f.evaluate(base), CoreDamageType.MAGICAL, 300.0, 600.0))
    }
    @Test fun `old reduction then percent penetration then flat penetration order is preserved`() {
        assertEquals(70.0, CoreCombatMath.effectiveDefense(300.0, 20.0, 50.0, 50.0))
        assertEquals(0.0, CoreCombatMath.effectiveDefense(20.0, 0.0, 0.0, 30.0))
        assertEquals(300.0 / 370, CoreCombatMath.defenseMultiplier(70.0))
    }
    @Test fun `physical penetration cannot increase magic damage or vice versa`() {
        val s = CoreAffixStats(additional = mapOf(CoreAffixStat.PHYSICAL_PEN_PERCENT to 100.0))
        assertEquals(100.0, CoreCombatMath.mitigate(100.0, CoreDamageType.PHYSICAL, 300.0, 300.0, s))
        assertEquals(50.0, CoreCombatMath.mitigate(100.0, CoreDamageType.MAGICAL, 300.0, 300.0, s))
        assertEquals(100.0, CoreCombatMath.mitigate(100.0, CoreDamageType.TRUE, 999.0, 999.0, s))
    }
    @Test fun `mage autos use AD and spells use AP without hidden conversion`() {
        val staff = CoreCombatGear(base = CoreWeaponBase.STAFF)
        val plain = CoreCombatSheet.from(staff, CoreAffixStats())
        val ad = CoreCombatSheet.from(staff, CoreAffixStats(additional = mapOf(CoreAffixStat.AD_FLAT to 100.0)))
        val ap = CoreCombatSheet.from(staff, CoreAffixStats(additional = mapOf(CoreAffixStat.AP_FLAT to 100.0)))
        assertEquals(plain.ad, ap.ad)
        assertEquals(plain.ap, ad.ap)
        assertEquals(CoreSkillCatalog.basicFormula.evaluate(plain), CoreSkillCatalog.basicFormula.evaluate(ap))
        for (job in listOf(CoreClass.MAGE, CoreClass.STARWEAVER)) for (skill in CoreSkillCatalog.skills(job)) {
            assertEquals(skill.preview(plain), skill.preview(ad))
            if (skill.formula.ap > 0) assertTrue(skill.preview(ap) > skill.preview(plain))
            else assertEquals(skill.preview(plain), skill.preview(ap))
            assertEquals(0.0, skill.formula.ad)
        }
    }
    @Test fun `physical skills gain nothing from AP`() {
        for (job in listOf(CoreClass.WARRIOR, CoreClass.RANGER)) for (s in CoreSkillCatalog.skills(job)) {
            assertEquals(s.preview(base), s.preview(base.copy(ap = 9999.0)))
            if (s.formula.ad > 0) assertTrue(s.preview(base.copy(ad = 200.0)) > s.preview(base))
            else assertEquals(s.preview(base), s.preview(base.copy(ad = 200.0)))
        }
    }
    @Test fun `damage increases add before critical and defence`() {
        val s = CoreAffixStats(damagePercent = 20.0, skillDamagePercent = 30.0,
            additional = mapOf(CoreAffixStat.MAGICAL_DAMAGE to 40.0, CoreAffixStat.PROJECTILE_DAMAGE to 10.0))
        assertEquals(300.0, CoreCombatMath.outgoing(100.0, CoreDamageType.MAGICAL,
            setOf(CoreAttackTag.SKILL, CoreAttackTag.PROJECTILE), s, critical = true))
    }
    @Test fun `speed denominators and legacy timing floors are used`() {
        assertEquals(112, CoreCombatMath.cooldownTicks(140, 25.0))
        assertEquals(35, CoreCombatMath.cooldownTicks(140, 9999.0))
        assertEquals(10, CoreCombatMath.cooldownTicks(20, 9999.0))
        assertEquals(10, CoreCombatMath.castTicks(20, 100.0))
        assertEquals(7, CoreCombatMath.castTicks(20, 9999.0))
        val slam = CoreSkillCatalog.skills(CoreClass.WARRIOR)[1]
        val light = CoreCombatSheet.from(CoreCombatGear(base = CoreWeaponBase.FLOW, weaponEnhancement = 30), CoreAffixStats())
        val heavy = CoreCombatSheet.from(CoreCombatGear(base = CoreWeaponBase.CLEAVER), CoreAffixStats())
        assertTrue(slam.startupTicks(light) < slam.startupTicks(base))
        assertTrue(slam.startupTicks(heavy) > slam.startupTicks(base))
    }
    @Test fun `health mana recovery healing and lifesteal are distinct consumers`() {
        val s = CoreAffixStats(healthFlat = 20.0, maxManaFlat = 30.0, manaRegenPercent = 50.0,
            additional = mapOf(CoreAffixStat.HEALTH_PERCENT to 10.0, CoreAffixStat.MANA_PERCENT to 20.0,
                CoreAffixStat.MANA_REGEN_FLAT to 1.0, CoreAffixStat.HEALING_FLAT to 10.0,
                CoreAffixStat.HEALING_PERCENT to 100.0, CoreAffixStat.OUTGOING_HEALING to 10.0,
                CoreAffixStat.INCOMING_HEALING to 20.0, CoreAffixStat.LIFESTEAL to 10.0))
        val sheet = CoreCombatSheet.from(CoreCombatGear(), s)
        assertEquals(132.0, sheet.health)
        assertEquals(156.0, sheet.mana)
        assertEquals(9.0, CoreCombatMath.manaRegeneration(s, false))
        assertEquals(27.0, CoreCombatMath.manaRegeneration(s, true))
        assertEquals(52.8, CoreCombatMath.healing(20.0, 1.0, sheet), .000001)
        assertEquals(10.0, CoreCombatMath.lifeSteal(100.0, s, false))
        assertEquals(3.3, CoreCombatMath.lifeSteal(100.0, s, true), .000001)
    }
    @Test fun `catalog previews show each actual per-hit formula timing and hit count`() {
        for (job in CoreClass.entries) for (s in CoreSkillCatalog.skills(job)) {
            val text = s.tooltip(base).joinToString("\n")
            assertContains(text, s.formula.label())
            if (s.motion !in setOf(CoreSkillMotion.HEAL, CoreSkillMotion.SHIELD)) assertContains(text, s.type.label)
            assertContains(text, CoreCombatMath.number(s.preview(base)))
            if (s.pulses > 1) assertContains(text, "× ${s.pulses} 回")
        }
    }
    @Test fun `invalid numeric inputs cannot poison the damage or resource pipeline`() {
        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            assertTrue(CoreCombatMath.power(v, v, v).isFinite())
            assertTrue(CoreCombatMath.mitigate(v, CoreDamageType.PHYSICAL, v, v).isFinite())
            assertTrue(CoreCombatMath.effectiveDefense(v, v, v, v).isFinite())
        }
        assertFailsWith<IllegalArgumentException> { CoreDamageFormula(ad = Double.NaN) }
    }
}
