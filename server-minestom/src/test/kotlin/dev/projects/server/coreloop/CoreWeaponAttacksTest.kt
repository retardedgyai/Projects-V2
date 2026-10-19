package dev.projects.server.coreloop

import dev.projects.server.mob.CombatTarget
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

/** Better Combat manner melee: per-weapon combos, hitbox shapes, hands, finishers. */
class CoreWeaponAttacksTest {
    private val forward = Vec(0.0, 0.0, 1.0)
    private fun at(degrees: Double, distance: Double) = CombatTarget(UUID.randomUUID(),
        Vec(sin(Math.toRadians(degrees)) * distance, 0.0, cos(Math.toRadians(degrees)) * distance), Vec(.3, .9, .3))

    @Test fun `greatsword sweeps wide twice then closes with a narrow overhead slice`() {
        val gs = CoreWeaponCombat.of(CoreWeaponBase.STANDARD)!!
        assertEquals(listOf(CoreHitbox.HORIZONTAL, CoreHitbox.HORIZONTAL, CoreHitbox.VERTICAL), gs.attacks.map { it.hitbox })
        val flank = at(60.0, 3.0)
        assertTrue(gs.attacks[0].reaches(Vec.ZERO, forward, flank, gs.range), "a 150 degree sweep reaches 60 degrees out")
        assertFalse(gs.attacks[2].reaches(Vec.ZERO, forward, flank, gs.range), "the overhead slice does not")
        assertTrue(gs.attacks[2].reaches(Vec.ZERO, forward, at(5.0, 4.0), gs.range))
        assertFalse(gs.attacks[0].reaches(Vec.ZERO, forward, at(0.0, 4.8), gs.range), "beyond the reach")
        // 重断大剣 keeps the swings and narrows the slice, much harder.
        val cleaver = CoreWeaponCombat.of(CoreWeaponBase.CLEAVER)!!
        assertTrue(cleaver.attacks[2].multiplier > gs.attacks[2].multiplier * 1.6)
        assertTrue(cleaver.attacks[2].width < gs.attacks[2].width)
    }

    @Test fun `dual daggers alternate hands through four attacks and a crossed finisher`() {
        val daggers = CoreWeaponCombat.of(CoreWeaponBase.DAGGERS)!!
        assertTrue(daggers.dual)
        assertEquals(listOf(CoreAttackHand.MAIN, CoreAttackHand.OFF, CoreAttackHand.MAIN, CoreAttackHand.BOTH), daggers.attacks.map { it.hand })
        val combo = GreatswordCombo()
        val swings = (1..5).map {
            val swing = assertNotNull(combo.press(1.0, quick = true, attacks = daggers.attacks))
            repeat(swing.totalTicks) { combo.tick() }
            swing
        }
        assertEquals(listOf(1, 2, 3, 4, 1), swings.map { it.step })
        assertEquals(listOf(false, false, false, true, false), swings.map { it.finisher })
        assertEquals(listOf(1, 2, 1, 3, 1), swings.map { it.beat }, "sounds and art never index past their three beats")
    }

    @Test fun `the lance thrusts far and narrow`() {
        val lance = CoreWeaponCombat.of(CoreWeaponBase.MACE)!!
        val thrust = lance.attacks[0]
        assertEquals(CoreHitbox.FORWARD, thrust.hitbox)
        assertTrue(thrust.reaches(Vec.ZERO, forward, at(0.0, 4.8), lance.range))
        assertFalse(thrust.reaches(Vec.ZERO, forward, at(35.0, 3.0), lance.range), "1.7 m to the side is out of a thrust")
        assertFalse(thrust.reaches(Vec.ZERO, forward, at(180.0, 1.0), lance.range), "never behind")
        assertTrue(lance.attacks[2].reaches(Vec.ZERO, forward, at(50.0, 3.0), lance.range), "the closing sweep is wide")
    }

    @Test fun `ranged and casting weapons keep their own normal attacks`() {
        for (base in listOf(CoreWeaponBase.LONGBOW, CoreWeaponBase.STAFF, CoreWeaponBase.TOME)) assertNull(CoreWeaponCombat.of(base))
    }
}
