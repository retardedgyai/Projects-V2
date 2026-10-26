package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * The greatsword's normal-attack slashes (2026-10-26, Creator: 「斬撃エフェクトをスキルと同じにしてほしい作画を」, then
 * 「アニメーションはいいの、エフェクトがだめ」 and 「てきとうにやっといて」 to a slash across the middle of the view). Painted
 * as the warrior's skills paint their swings: the same dark band of wind (CoreWarriorRefChoreography "wind",
 * build_warrior_ref_vfx.wind_band), its head running round with the blade and burning away from its tail. It stands
 * about two blocks ahead, facing the eye along the look (pitch included), so it always reads as a whole crescent across
 * the view: the first cut from the right over to the left, the second back, the slam from overhead down. Dropped on the
 * way: the blade tip's own path (from the eye a level cut is a straight thin line), bands laid flat round the player
 * as Better Combat lays its trails (a dark smear near the feet), bands at fixed angles in front (off to the side).
 */
internal object CoreApprovedNormalV3 {
    val sceneIds = setOf("normal_sweep", "normal_reverse", "normal_finish")
    const val LIFE = 8
    const val SHOW_SLASH = false
    private const val EYE = 1.62
    private const val AHEAD = 2.2

    /** (board x and y as angles on the view, degrees from the right toward up; the pivot right and up of the view's
     *  middle; the board's size): found against first-person previews. The crescent runs round the pivot. */
    private data class Arc(val x: Double, val y: Double, val right: Double, val up: Double, val size: Double)
    private val arcs = mapOf(
        "normal_sweep" to Arc(23.0, 113.0, 0.0, -1.5, 4.4),     // from the right over to the left
        "normal_reverse" to Arc(157.0, 67.0, 0.0, -1.5, 4.4),   // from the left over to the right
        "normal_finish" to Arc(-17.0, 73.0, -1.4, 0.0, 3.1))     // from overhead down

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (e.job != CoreClass.WARRIOR || e.sceneId !in sceneIds || !e.valid) return null
        if (e.phase == CoreSkillVisualPhase.CONTACT) return CoreApprovedDashV3.parts(
            CoreSkillEffect(e.job, e.skill, e.origin, e.direction, e.phase, sceneId = "dash"))
        // Off for now (Creator 2026-10-26: 「エフェクト一旦ださなくていいよ」): no slash yet sat on the first-person blade.
        if (!SHOW_SLASH) return emptyList()
        val arc = arcs.getValue(e.sceneId)
        val a = atan2(e.direction.x(), e.direction.z())
        val p = e.lookPitch.coerceIn(-PI / 2.2, PI / 2.2)
        val right = doubleArrayOf(-cos(a), 0.0, sin(a))
        val look = doubleArrayOf(sin(a) * cos(p), -sin(p), cos(a) * cos(p))
        val up = doubleArrayOf(sin(a) * sin(p), cos(p), cos(a) * sin(p))
        fun onView(deg: Double) = Math.toRadians(deg).let { r -> DoubleArray(3) { right[it] * cos(r) + up[it] * sin(r) } }
        val x = onView(arc.x)
        val y = onView(arc.y)
        val z = doubleArrayOf(x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0])
        // m = [x y z] as columns, read as CoreCombatMeshArt.rotation's Ry(yaw) Rz(roll) Rx(pitch)
        val yaw = atan2(-x[2], x[0])
        val roll = asin(x[1].coerceIn(-1.0, 1.0))
        val pitch = atan2(-z[1], y[1])
        // the crescent's pivot (model (4, 7.5) of 16) stands AHEAD along the look, beside the view's middle
        val pivot = DoubleArray(3) { look[it] * AHEAD + right[it] * arc.right + up[it] * arc.up + (if (it == 1) EYE else 0.0) }
        val local = doubleArrayOf(-.25 * arc.size, -.03125 * arc.size)
        val at = Vec(pivot[0] - x[0] * local[0] - y[0] * local[1], pivot[1] - x[1] * local[0] - y[1] * local[1],
            pivot[2] - x[2] * local[0] - y[2] * local[1])
        return listOf(CoreCombatMeshPart("war_ref:wind", "steel", at, Vec(arc.size, arc.size, arc.size), yaw = yaw,
            pitch = pitch, roll = roll, durationTicks = LIFE, startSize = 1.0, endSize = 1.0))
    }
}
