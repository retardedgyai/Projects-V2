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
    fun `swing origin uses a camera-safe forward offset`() {
        assertEquals(1.25, TWIN_BLADES_SWING_FORWARD_OFFSET)
        listOf(
            Vec(0.0, 0.0, 1.0),
            Vec(-0.7, 0.2, 0.68),
            Vec(0.25, -0.8, 0.55),
        ).forEach { direction ->
            val eye = Pos.ZERO.add(0.0, 1.62, 0.0)
            val origin = twinBladesSwingOrigin(Pos.ZERO, 1.62, direction, 35.0)
            val forwardDistance = Vec(origin.x() - eye.x(), origin.y() - eye.y(), origin.z() - eye.z())
                .dot(direction) / direction.length()
            assertTrue(forwardDistance >= 1.2)
        }
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
        assertTrue(first.swingDuration <= second.swingDuration)
        assertTrue(second.hitLength < third.hitLength)
        assertTrue(first.swingPrimary != second.swingPrimary)
        assertTrue(second.swingPrimary != third.swingPrimary)
        assertTrue(first.weakpointDuration < third.weakpointDuration)
        assertEquals(2.9, first.swingLength)
        assertEquals(3.4, second.swingLength)
        assertEquals(4.0, third.swingLength)
        assertTrue(first.hitLength >= 2.8)
        assertTrue(second.hitLength >= 3.2)
        assertTrue(third.hitLength >= 3.8)
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
    fun `twin blades sound plan separates swing contact and weakpoint accent`() {
        val miss = twinBladesSoundPlan(WeaponType.TWIN_RODS, 1, confirmed = false, weakpoint = false)
        val normal = twinBladesSoundPlan(WeaponType.TWIN_RODS, 2, confirmed = true, weakpoint = false)
        val weakpoint = twinBladesSoundPlan(WeaponType.TWIN_RODS, 3, confirmed = true, weakpoint = true)

        assertEquals(3, miss.swing.size)
        assertTrue(miss.contact.isEmpty())
        assertTrue(miss.weakpointAccent.isEmpty())
        assertEquals(listOf("item.trident.hit", "item.axe.scrape"), normal.contact.map { it.key })
        assertTrue(normal.weakpointAccent.isEmpty())
        assertEquals(listOf("block.note_block.chime"), weakpoint.weakpointAccent.map { it.key })
        assertTrue(weakpoint.swing != normal.swing)
    }

    @Test
    fun `heavy blade never gets twin blades sounds`() {
        val plan = twinBladesSoundPlan(WeaponType.HEAVY_BLADE, 3, confirmed = true, weakpoint = true)

        assertTrue(plan.swing.isEmpty())
        assertTrue(plan.contact.isEmpty())
        assertTrue(plan.weakpointAccent.isEmpty())
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

        assertEquals(4.0, dimensions.length)
        assertEquals(1.1, dimensions.radius)
        assertEquals(6.0, dimensions.length * visualScale, absoluteTolerance = 0.000001)
        assertEquals(1.65, dimensions.radius * visualScale, absoluteTolerance = 0.000001)
    }
}
