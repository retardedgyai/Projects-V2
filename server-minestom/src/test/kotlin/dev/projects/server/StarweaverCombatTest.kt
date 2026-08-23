package dev.projects.server

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Test

class StarweaverCombatTest {
    private val cast = StarweaverCast(1L, StarweaverSlot.Q, StarweaverCelestial.SUN, StarweaverCastKind.BASE)

    @Test
    fun `projectile sweep detects a target crossed between ticks`() {
        val targetId = UUID.randomUUID()
        val projectile = StarweaverProjectileState(
            cast = cast,
            origin = Pos.ZERO,
            direction = Vec(0.0, 0.0, 1.0),
            speedBlocksPerTick = 2.0,
            range = 10.0,
            hitRadius = 0.1,
        )
        val target = StarweaverProjectileTarget(
            CombatTarget(targetId, Pos(0.0, 0.0, 1.0), Vec(0.1, 0.1, 0.1)),
            isAlly = false,
        )

        val first = projectile.tick(listOf(target))
        assertEquals(listOf(targetId), first.hitTargetIds)
        assertFalse(projectile.tick(listOf(target)).hitTargetIds.contains(targetId))
    }

    @Test
    fun `projectile can hit multiple distinct targets but each only once`() {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val projectile = StarweaverProjectileState(
            cast = cast,
            origin = Pos.ZERO,
            direction = Vec(0.0, 0.0, 1.0),
            speedBlocksPerTick = 3.0,
            range = 10.0,
            hitRadius = 0.15,
        )
        val targets = listOf(
            StarweaverProjectileTarget(CombatTarget(firstId, Pos(0.0, 0.0, 1.0), Vec(0.1, 0.1, 0.1)), false),
            StarweaverProjectileTarget(CombatTarget(secondId, Pos(0.0, 0.0, 2.0), Vec(0.1, 0.1, 0.1)), false),
        )

        assertEquals(setOf(firstId, secondId), projectile.tick(targets).hitTargetIds.toSet())
        assertTrue(projectile.tick(targets).hitTargetIds.isEmpty())
    }

    @Test
    fun `AoE uses target AABB and includes an edge intersection`() {
        val edgeTarget = CombatTarget(
            id = UUID.randomUUID(),
            position = Pos(3.4, 0.0, 0.0),
            halfExtent = Vec(0.6, 0.5, 0.5),
        )
        val outsideTarget = CombatTarget(
            id = UUID.randomUUID(),
            position = Pos(3.7, 0.0, 0.0),
            halfExtent = Vec(0.1, 0.1, 0.1),
        )

        assertTrue(isWithinStarweaverAabbRadius(Pos.ZERO, 3.0, edgeTarget))
        assertFalse(isWithinStarweaverAabbRadius(Pos.ZERO, 3.0, outsideTarget))
    }

    @Test
    fun `solar projectile hit selection has no target-count falloff`() {
        val solarCast = cast.copy(castId = 2L, celestial = StarweaverCelestial.SUN, kind = StarweaverCastKind.CONJUNCTION)
        val projectile = StarweaverProjectileState.solar(solarCast, Pos.ZERO, Vec(0.0, 0.0, 1.0))
        val targets = (1..2).map { index ->
            StarweaverProjectileTarget(
                CombatTarget(UUID.randomUUID(), Pos(0.0, 0.0, index.toDouble()), Vec(0.5, 0.5, 0.5)),
                isAlly = false,
            )
        }

        assertEquals(2, projectile.tick(targets).hitTargetIds.size)
    }

    @Test
    fun `Moonlit propagation is direct only and capped at four targets`() {
        val effects = StarweaverEffectState()
        val primary = UUID.randomUUID()
        val otherMoonlit = List(5) { UUID.randomUUID() }
        effects.applyMoonlit(primary)
        otherMoonlit.forEach(effects::applyMoonlit)

        val transfers = effects.moonlitPropagation(42L, primary, 100, otherMoonlit)
        assertEquals(4, transfers.size)
        assertTrue(transfers.all { it.second == 25 })
        assertTrue(effects.moonlitPropagation(42L, primary, 100, otherMoonlit).isEmpty())
    }

    @Test
    fun `periodic damage is emitted as an effect, not Moonlit propagation`() {
        val effects = StarweaverEffectState()
        val target = UUID.randomUUID()
        effects.applyMoonlit(target)
        effects.applySolarBurn(target)

        repeat(StarweaverBalance.PERIODIC_TICK_INTERVAL - 1) {
            assertTrue(effects.tick().isEmpty())
        }
        val periodic = effects.tick()
        assertEquals(1, periodic.size)
        assertEquals(target, periodic.single().targetId)
        assertTrue(effects.moonlitPropagation(99L, target, periodic.single().damage, listOf(target)).isEmpty())
    }

    @Test
    fun `runtime reset clears projectile pending zone field and effects`() {
        val runtime = StarweaverRuntimeState()
        runtime.addProjectile(StarweaverProjectileState.normal(cast, Pos.ZERO, Vec(0.0, 0.0, 1.0)))
        runtime.addPendingZone(cast, Pos.ZERO, 2)
        runtime.addField(7L, Pos.ZERO)
        runtime.effects.applyMoonlit(UUID.randomUUID())
        runtime.reset()

        assertTrue(runtime.projectiles().isEmpty())
        val tick = runtime.tick()
        assertTrue(tick.activatedZones.isEmpty())
        assertTrue(tick.fieldPulses.isEmpty())
        assertTrue(tick.periodicEffects.isEmpty())
        assertFalse(runtime.rotation.isReloading)
    }
}
