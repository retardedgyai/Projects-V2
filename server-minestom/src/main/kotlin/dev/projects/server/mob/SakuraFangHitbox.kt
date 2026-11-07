package dev.projects.server.mob

/**
 * 桜牙獣's body for player hits: spheres pinned to its bones (assets/bosses/sakura_fang.bbmodel), so they follow every
 * animation, as ShadeWyrmHitbox does for the wyrm. Until now it borrowed the wyrm's spheres, whose bones it mostly
 * lacks, and a 4×3.6×4 box, while its body is 2.75 wide, 5.9 high and 7 long. Each bone's cubes (the fur, mane and tail
 * shells left out) are split along their long side into spheres; radii are blocks (16 px), the trunk a little fuller.
 */
internal object SakuraFangHitbox {
    val BODY = listOf(
        S("body", 0.0, 33.5, -26.2, 1.21),
        S("body", 0.0, 33.5, -10.8, 1.21),
        S("chest", 0.0, 30.7, 6.0, 1.13),
        S("chest", 0.0, 47.3, 6.0, 1.13),
        S("neck", 0.0, 35.4, 21.0, 0.60),
        S("neck", 0.0, 46.6, 21.0, 0.60),
        S("skull", 0.0, 34.7, 37.9, 0.75),
        S("skull", 0.0, 46.5, 37.9, 0.75),
        S("jaw", 0.0, 24.6, 37.4, 0.60),
        S("jaw", 0.0, 31.8, 37.4, 0.60),
        S("jaw", 0.0, 39.0, 37.4, 0.60),
        S("horn_l", 8.3, 46.8, 28.6, 0.60),
        S("horn_l", 8.3, 58.0, 28.6, 0.60),
        S("horn_l_tip", 12.3, 64.9, 20.3, 0.60),
        S("horn_l_tip", 12.3, 71.7, 20.3, 0.60),
        S("horn_r", -8.3, 46.8, 28.6, 0.60),
        S("horn_r", -8.3, 58.0, 28.6, 0.60),
        S("horn_r_tip", -12.3, 64.9, 20.3, 0.60),
        S("horn_r_tip", -12.3, 71.7, 20.3, 0.60),
        S("arm_l", 14.6, 25.2, 10.8, 0.60),
        S("arm_l", 14.6, 40.4, 10.8, 0.60),
        S("forearm_l", 15.0, 6.2, 10.8, 0.67),
        S("forearm_l", 15.0, 19.8, 10.8, 0.67),
        S("hand_l", 15.0, 2.6, 17.0, 0.60),
        S("hand_l", 15.0, 2.6, 27.1, 0.60),
        S("arm_r", -14.6, 25.2, 10.8, 0.60),
        S("arm_r", -14.6, 40.4, 10.8, 0.60),
        S("forearm_r", -15.0, 6.2, 10.8, 0.67),
        S("forearm_r", -15.0, 19.8, 10.8, 0.67),
        S("hand_r", -15.0, 2.6, 17.0, 0.60),
        S("hand_r", -15.0, 2.6, 27.1, 0.60),
        S("thigh_l", 11.0, 18.4, -20.5, 0.60),
        S("thigh_l", 11.0, 26.5, -20.5, 0.60),
        S("thigh_l", 11.0, 34.6, -20.5, 0.60),
        S("shin_l", 12.0, 6.6, -23.0, 0.60),
        S("shin_l", 12.0, 13.5, -23.0, 0.60),
        S("shin_l", 12.0, 20.4, -23.0, 0.60),
        S("foot_l", 12.0, 5.0, -26.1, 0.60),
        S("foot_l", 12.0, 5.0, -18.6, 0.60),
        S("thigh_r", -11.0, 18.4, -20.5, 0.60),
        S("thigh_r", -11.0, 26.5, -20.5, 0.60),
        S("thigh_r", -11.0, 34.6, -20.5, 0.60),
        S("shin_r", -12.0, 6.6, -23.0, 0.60),
        S("shin_r", -12.0, 13.5, -23.0, 0.60),
        S("shin_r", -12.0, 20.4, -23.0, 0.60),
        S("foot_r", -12.0, 5.0, -26.1, 0.60),
        S("foot_r", -12.0, 5.0, -18.6, 0.60),
        S("tail_1", 0.0, 32.0, -36.0, 0.60),
        S("tail_1", 0.0, 42.0, -36.0, 0.60),
        S("tail_2", 0.0, 40.4, -46.5, 0.60),
        S("tail_2", 0.0, 50.6, -46.5, 0.60),
        S("tail_3", 0.0, 52.8, -52.0, 0.60),
        S("tail_3", 0.0, 62.2, -52.0, 0.60),
        S("tail_4", 0.0, 65.3, -50.5, 0.60),
        S("tail_4", 0.0, 73.7, -50.5, 0.60),
        S("tail_5", 0.0, 75.3, -41.3, 0.60),
        S("tail_5", 0.0, 84.7, -41.3, 0.60),
        S("shell_b_l", 2.6, 51.3, -13.9, 0.60),
        S("shell_b_l", 2.6, 51.3, -2.9, 0.60),
        S("shell_b_r", -2.6, 51.3, -13.9, 0.60),
        S("shell_b_r", -2.6, 51.3, -2.9, 0.60),
    )

    fun parts(view: BossModelView): List<HitSphere> =
        BODY.mapNotNull { s -> view.point(s.bone, s.px)?.let { HitSphere(it, s.radius) } }

    private fun S(bone: String, x: Double, y: Double, z: Double, radius: Double) = ShadeWyrmHitbox.S(bone, x, y, z, radius)
}
