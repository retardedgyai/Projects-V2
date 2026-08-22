package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals

class TwinBladesVfxTest {
    @Test
    fun `started swings alternate plus and minus 35 degrees`() {
        var previous: Double? = null
        val angles = (0..2).map { nextTwinBladesSwingAngle(previous).also { previous = it } }
        assertEquals(listOf(35.0, -35.0, 35.0), angles)
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
}
