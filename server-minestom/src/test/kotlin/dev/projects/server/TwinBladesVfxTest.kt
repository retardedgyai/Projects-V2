package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec

class TwinBladesVfxTest {
    @Test
    fun `started swings alternate plus and minus 35 degrees`() {
        var previous: Double? = null
        val angles = (0..2).map { nextTwinBladesSwingAngle(previous).also { previous = it } }
        assertEquals(listOf(35.0, -35.0, 35.0), angles)
    }

    @Test
    fun `swing origin stays ahead of the camera and mirrors hand side`() {
        val direction = Vec(0.3, 0.4, 0.8)
        val eye = Pos.ZERO.add(0.0, 1.62, 0.0)
        val positive = twinBladesSwingOrigin(Pos.ZERO, 1.62, direction, 35.0)
        val negative = twinBladesSwingOrigin(Pos.ZERO, 1.62, direction, -35.0)
        val positiveDelta = Vec(positive.x() - eye.x(), positive.y() - eye.y(), positive.z() - eye.z())
        val negativeDelta = Vec(negative.x() - eye.x(), negative.y() - eye.y(), negative.z() - eye.z())

        assertTrue(positiveDelta.dot(direction) / direction.length() > 1.0)
        assertTrue(negativeDelta.dot(direction) / direction.length() > 1.0)
        assertTrue(positive.distance(negative) > 0.3)
    }

    @Test
    fun `combo steps cycle from one through three`() {
        val state = TwinBladesComboState()
        assertEquals(listOf(1, 2, 3, 1), (0..3).map { state.start() })
    }

    @Test
    fun `combo resets to step one after twelve idle ticks`() {
        val state = TwinBladesComboState()
        assertEquals(1, state.start())
        assertEquals(2, state.start())
        repeat(TWIN_BLADES_COMBO_RESET_TICKS) { state.tick() }
        assertEquals(1, state.start())
    }

    @Test
    fun `each combo step has a distinct visual plan`() {
        val first = twinBladesComboVisual(1)
        val second = twinBladesComboVisual(2)
        val third = twinBladesComboVisual(3)

        assertTrue(first.swingLength < second.swingLength)
        assertTrue(second.swingLength < third.swingLength)
        assertTrue(first.swingDuration < second.swingDuration)
        assertTrue(second.hitLength < third.hitLength)
        assertTrue(first.swingPrimary != second.swingPrimary)
        assertTrue(second.swingPrimary != third.swingPrimary)
        assertTrue(first.weakpointDuration < third.weakpointDuration)
    }

    @Test
    fun `miss has no contact presets`() {
        assertEquals(emptyList(), twinBladesHitVfxPlan(WeaponType.TWIN_RODS, false, false).presets)
    }

    @Test
    fun `normal and weakpoint confirmed hits route through twin presets`() {
        assertEquals(listOf("projects:class/twin_blades/aa_hit"), twinBladesHitVfxPlan(WeaponType.TWIN_RODS, true, false).presets)
        assertEquals(
            listOf("projects:class/twin_blades/aa_hit", "projects:class/twin_blades/weakpoint_hit"),
            twinBladesHitVfxPlan(WeaponType.TWIN_RODS, true, true).presets,
        )
    }

    @Test
    fun `heavy blade never routes through twin presets`() {
        assertEquals(emptyList(), twinBladesHitVfxPlan(WeaponType.HEAVY_BLADE, true, true).presets)
    }

    @Test
    fun `target scale is bounded and leaves small targets at baseline`() {
        assertEquals(1.0, twinBladesVisualScale(0.6, 1.8))
        assertEquals(1.5, twinBladesVisualScale(3.0, 3.0))
        assertEquals(1.25, twinBladesVisualScale(1.25, 2.0))
    }

    @Test
    fun `weakpoint radius is stronger than normal hit and respects preset bound`() {
        assertEquals(1.35, twinBladesWeakpointRadius(1.0))
        assertEquals(2.0, twinBladesWeakpointRadius(1.5))
    }

    @Test
    fun `hit dimensions rely on preset scale exactly once`() {
        val dimensions = twinBladesHitVisualDimensions(twinBladesComboVisual(3))
        val visualScale = 1.5

        assertEquals(3.2, dimensions.length)
        assertEquals(1.1, dimensions.radius)
        assertEquals(4.8, dimensions.length * visualScale, absoluteTolerance = 0.000001)
        assertEquals(1.65, dimensions.radius * visualScale, absoluteTolerance = 0.000001)
    }
}
