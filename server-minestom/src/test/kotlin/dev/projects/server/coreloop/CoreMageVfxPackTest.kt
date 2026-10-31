package dev.projects.server.coreloop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every item CoreMageFire and CoreMageFrost show is in the pack (scripts/build_mage_fire.py, build_mage_frost.py). */
class CoreMageVfxPackTest {
    private val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }

    private fun assertItem(dir: String, name: String) {
        for (kind in listOf("items", "models")) {
            val path = "assets/projects/$kind/combat_vfx/$dir/$name.json"
            assertTrue(path in index, path)
        }
    }

    @Test fun `the fire bolt's sprites are in the pack`() {
        (0..2).forEach { assertItem("mage_fire", "ball_$it"); assertItem("mage_fire", "lick_$it") }
        (0..1).forEach { assertItem("mage_fire", "burst_$it") }
        (0..3).forEach { assertItem("mage_fire", "tall_$it") }
        (0..4).forEach { assertItem("mage_fire", "ember_$it") }
    }

    @Test fun `the frost nova's pieces are in the pack, every growth step of every column`() {
        for ((shape, pieces) in CoreMageFrost.COLUMN_PIECES) for (s in 1..pieces) assertItem("mage_frost", "col_${shape}_$s")
        for ((name, pieces) in CoreMageFrost.SPROUT_PIECES) for (s in 1..pieces) assertItem("mage_frost", "sprout_${name}_$s")
        assertTrue("assets/projects/items/combat_vfx/mage_frost/col_00_${CoreMageFrost.COLUMN_PIECES.getValue("00") + 1}.json" !in index, "no step past the point")
        (0..3).forEach { assertItem("mage_frost", "patch_$it") }
        (0..5).forEach { assertItem("mage_frost", "dash_$it") }
        for (k in 0..2) for (l in 0..8) assertItem("mage_frost", "halo_${k}_$l")
        listOf("pillar_core", "pillar_rim", "flake", "bit").forEach { assertItem("mage_frost", it) }
    }

    @Test fun `the fire bolt's ball takes longer the farther it flies`() {
        assertEquals(2, CoreMageFire.flightTicks(1.0))
        assertTrue(CoreMageFire.flightTicks(9.0) > CoreMageFire.flightTicks(4.0))
        assertEquals(8, CoreMageFire.flightTicks(40.0))
    }
}
