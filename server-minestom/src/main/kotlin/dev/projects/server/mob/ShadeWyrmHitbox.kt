package dev.projects.server.mob

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec

/**
 * The 黒翳竜's body for player hits (藍「頭や羽に当たらない」): spheres pinned to its bones, so they follow every
 * animation. Centres are Blockbench px in model space and radii blocks, fitted to the model's cubes
 * (k-means over each bone's cubes, the radius covering ~70 % of them, then 0.2 more and at least 0.8; the head made larger by hand).
 * Wings count until the enrage folds them, the fans once they are out (BossModelView.revealed / folded).
 */
internal object ShadeWyrmHitbox {
    class S(val bone: String, x: Double, y: Double, z: Double, val radius: Double) { val px: Point = Vec(x, y, z) }

    val BODY = listOf(
        S("body", 0.0, 61.0, 26.0, 1.13),
        S("body", 0.0, 31.0, -41.0, 1.13),
        S("body", 0.0, 32.0, 35.0, 0.99),
        S("body", 0.0, 61.0, -1.0, 1.20),
        S("neck", 0.0, 46.0, 56.0, 1.27),
        S("neck", 0.0, 54.0, 24.0, 0.89),
        S("skull", 0.0, 42.0, 69.0, 1.10),
        S("skull", 0.0, 54.0, 62.0, 0.90),               // the crown and horn roots (added by hand)
        S("tail", 0.0, 21.0, -59.0, 0.89),
        S("tail_2", -2.0, 14.0, -78.0, 0.91),
        S("tail_mid", -7.0, 8.0, -97.0, 0.90),
        S("tail_end", -12.0, 5.0, -117.0, 0.80),
        // legs: upper, lower and paw on each (藍「足も当たり判定ほしい」)
        S("leg_fl", 16.0, 35.0, 23.0, 0.80),
        S("leg_fl_fore", 24.0, 18.0, 33.0, 0.80),
        S("leg_fl_paw", 27.0, 4.0, 48.0, 0.80),
        S("leg_fr", -16.0, 35.0, 23.0, 0.80),
        S("leg_fr_fore", -24.0, 18.0, 33.0, 0.80),
        S("leg_fr_paw", -27.0, 4.0, 48.0, 0.80),
        S("leg_hl", 16.0, 16.0, -37.0, 0.86),
        S("leg_hl_shin", 23.0, 11.0, -48.0, 0.80),
        S("leg_hl_foot", 29.0, 4.0, -47.0, 0.80),
        S("leg_hr", -16.0, 16.0, -37.0, 0.86),
        S("leg_hr_shin", -23.0, 11.0, -48.0, 0.80),
        S("leg_hr_foot", -29.0, 4.0, -47.0, 0.80),
    )
    val WINGS = listOf(
        S("wing_l", 19.0, 67.0, 10.0, 1.09),
        S("wing_l_fore", 27.0, 42.0, -9.0, 1.08),
        S("wing_l_hand", 27.0, 21.0, -24.0, 0.80),
        S("wing_l_membrane", 30.0, 36.0, -22.0, 1.34),
        S("wing_l_hinge02", 26.0, 59.0, -5.0, 1.93),
        S("wing_l_hinge03", 23.0, 62.0, -13.0, 1.96),
        S("wing_l_hinge00", 16.0, 69.0, 3.0, 1.69),
        S("wing_l_hinge01", 8.0, 63.0, 0.0, 1.70),
        S("wing_l_hinge09", 29.0, 30.0, -39.0, 0.90),
        S("wing_l_hinge07", 26.0, 41.0, -38.0, 1.44),
        S("wing_l_hinge08", 19.0, 50.0, -38.0, 1.45),
        S("wing_l_hinge12", 12.0, 59.0, -28.0, 1.24),
        S("wing_l_hinge13", 7.0, 50.0, -36.0, 1.47),
        S("wing_l_hinge14", 12.0, 43.0, -59.0, 1.14),
        S("wing_l_hinge16", 17.0, 27.0, -84.0, 1.55),
        S("wing_l_hinge10", 21.0, 29.0, -59.0, 1.55),
        S("wing_l_hinge11", 18.0, 37.0, -65.0, 1.56),
        S("wing_l_hinge15", 21.0, 18.0, -93.0, 1.74),
        S("wing_r", -19.0, 67.0, 10.0, 1.09),
        S("wing_r_fore", -27.0, 42.0, -9.0, 1.08),
        S("wing_r_hand", -27.0, 21.0, -24.0, 0.80),
        S("wing_r_membrane", -30.0, 36.0, -22.0, 1.34),
        S("wing_r_hinge02", -26.0, 59.0, -5.0, 1.93),
        S("wing_r_hinge03", -23.0, 62.0, -13.0, 1.96),
        S("wing_r_hinge00", -16.0, 69.0, 3.0, 1.69),
        S("wing_r_hinge01", -8.0, 63.0, 0.0, 1.70),
        S("wing_r_hinge09", -29.0, 30.0, -39.0, 0.90),
        S("wing_r_hinge07", -26.0, 41.0, -38.0, 1.44),
        S("wing_r_hinge08", -19.0, 50.0, -38.0, 1.45),
        S("wing_r_hinge12", -12.0, 59.0, -28.0, 1.24),
        S("wing_r_hinge13", -7.0, 50.0, -36.0, 1.47),
        S("wing_r_hinge14", -12.0, 43.0, -59.0, 1.14),
        S("wing_r_hinge16", -17.0, 27.0, -84.0, 1.55),
        S("wing_r_hinge10", -21.0, 29.0, -59.0, 1.55),
        S("wing_r_hinge11", -18.0, 37.0, -65.0, 1.56),
        S("wing_r_hinge15", -21.0, 18.0, -93.0, 1.74),
    )
    val FANS = listOf(
        S("cloak_l", 52.0, 74.0, 43.0, 1.46),
        S("cloak_l", 65.0, 108.0, 48.0, 2.00),
        S("cloak_l", 77.0, 101.0, -2.0, 2.18),
        S("cloak_l", 26.0, 66.0, 16.0, 1.19),
        S("cloak_l", 98.0, 61.0, 33.0, 1.81),
        S("cloak_l", 55.0, 7.0, 36.0, 1.16),
        S("cloak_l", 37.0, 44.0, 37.0, 1.06),
        S("cloak_l", 59.0, 41.0, 33.0, 1.43),
        S("cloak_l", 103.0, 56.0, -17.0, 1.89),
        S("cloak_l", 36.0, 22.0, 53.0, 0.96),
        S("cloak_r", -52.0, 73.0, 43.0, 1.47),
        S("cloak_r", -65.0, 108.0, 48.0, 2.04),
        S("cloak_r", -77.0, 101.0, -2.0, 2.17),
        S("cloak_r", -26.0, 67.0, 14.0, 1.25),
        S("cloak_r", -100.0, 60.0, 29.0, 1.91),
        S("cloak_r", -57.0, 6.0, 37.0, 1.04),
        S("cloak_r", -37.0, 39.0, 31.0, 1.12),
        S("cloak_r", -60.0, 41.0, 33.0, 1.40),
        S("cloak_r", -103.0, 56.0, -22.0, 1.85),
        S("cloak_r", -36.0, 24.0, 54.0, 1.00),
    )

    fun parts(view: BossModelView): List<HitSphere> =
        (BODY + (if (view.fansOut) FANS else emptyList()) + (if (view.folded) emptyList() else WINGS)).mapNotNull { s -> view.point(s.bone, s.px)?.let { HitSphere(it, s.radius) } }
}
