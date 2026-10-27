package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/**
 * Warrior strikes after the Outer Gods reference rules (2026-10-20, Creator: 「このリファレンスで見ながらウォーリアーの
 * スキル作ってみて」, filmed in game). Every piece is one textured model (scripts/build_warrior_ref_vfx.py) that the
 * server poses each tick: iron fangs burst out of the ground in a crown and overshoot, hold, then sink while losing
 * pixels; flame boards stand round the blow for eight ticks and cool in steps; dark plates are thrown and tumble;
 * the crack star is burnt into the ground and breaks up. 地割り first; the other skills follow once it is approved.
 */
internal object CoreWarriorRefChoreography {
    private const val PREFIX = "war_ref:"
    private const val DIR = "combat_vfx/warrior_ref"
    @Volatile var enabled = true
    private val sceneIds = setOf("war_wound")
    /** 引き摺り: how the dragged greatsword is held (found against the preview: blade down and back, tip on the ground). */
    private const val DRAG_SWORD_YAW = PI / 2
    private const val DRAG_SWORD_PITCH = 0.0
    private const val DRAG_SWORD_ROLL = -2.3
    /** 狂乱 / 天落とし / 大地崩し: every phase is drawn here (their ids still carry older choreographies elsewhere). */
    private val ownedIds = setOf(CoreWarriorRules.FRENZY, CoreWarriorRules.SKYFALL, CoreWarriorRules.SHATTER, CoreWarriorRules.LAUNCHER,
        CoreWarriorRules.DRAG, CoreWarriorRules.FULL_SWING, CoreWarriorRules.ARMOR, CoreWarriorRules.AWAKEN)

    fun owns(p: CoreCombatMeshPart) = p.shape.startsWith(PREFIX)
    /** Camera-facing pieces whose pose moves them (the meshes carry these by moving the entity). */
    fun drifts(p: CoreCombatMeshPart) = owns(p) && p.shape.removePrefix(PREFIX).let {
        it == "bdrop" || it == "bfling" || it == "bsmoke" || it == "bmistup" || it == "bgush" || it.startsWith("scale") ||
            it.startsWith("sgather") || it == "kick" || it == "fspark" }
    fun handles(e: CoreSkillEffect) = enabled && e.job == CoreClass.WARRIOR && e.valid &&
        (e.phase == CoreSkillVisualPhase.PULSE && e.sceneId in sceneIds || e.sceneId in ownedIds || e.sceneId in lookIds)
    private const val TACKLE = "dash"
    /** 体当たり, 跳び叩き and 地割り: every phase is drawn here, and only what the body and blade leave in the world. */
    val lookIds = setOf(TACKLE, CoreWarriorLeap.ICON, SLAM)
    private const val SLAM = "slam"

    // The world look of 体当たり / 跳び叩き is played by the server at the moment it happens (CorePlayerCombat, look
    // only: no sound, no hand pose). These pulse numbers say which moment; the skill's own pulses draw nothing.
    const val TACKLE_STEP_PULSE = 100       // + the tick of the charge: one step
    const val TACKLE_STOP_PULSE = 120       // the charge ran out (nothing met)
    const val TACKLE_HIT_PULSE = 121        // the shoulder met something
    const val TACKLE_CAUGHT_PULSE = 122     // ... after catching a blow: harder
    const val TACKLE_CATCH_PULSE = 130      // the moment a blow is caught
    const val LEAP_TAKEOFF_PULSE = 140
    const val LEAP_LAND_PULSE = 141
    const val LEAP_FAR_LAND_PULSE = 142     // after a leap of CoreWarriorRules.LEAP_FAR or more
    const val SLAM_PULSE = 150              // 地割り's blade meets the ground
    const val SLAM_SPLIT_PULSE = 151        // ... and splits again at the head (a second pulse)
    const val FULL_SLAM_PULSE = 152         // 全力の一振り's blow (its blood: CoreWarriorRules.FULL_SWING_GEYSER_PULSE + step)
    const val FULL_WINDUP_PULSE = 153       // 全力の一振り's wind-up: the ground under the feet gives

    private fun hash(vararg v: Int): Double {
        val x = sin(v.foldIndexed(0.0) { i, a, n -> a + n * doubleArrayOf(127.1, 311.7, 74.7, 19.3)[i % 4] }) * 43758.5453
        return x - floor(x)
    }

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart>? {
        if (!handles(e)) return null
        val yaw = atan2(e.direction.x(), e.direction.z())
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        fun part(name: String, at: Vec, scale: Double, partYaw: Double, life: Int, pitch: Double = 0.0, delay: Int = 0,
                 ground: Boolean = true, roll: Double = 0.0, billboard: Boolean = false) =
            CoreCombatMeshPart("$PREFIX$name", "steel", at, Vec(scale, scale, scale), yaw = partYaw, pitch = pitch, roll = roll,
                durationTicks = life, startSize = 1.0, endSize = 1.0, delayTicks = delay, ground = ground, billboard = billboard)
        val out = mutableListOf<CoreCombatMeshPart>()
        /**
         * A vertical cut's dark trail: in the upright plane along the facing, the way the blade travels, but drawn ahead
         * of the body so it does not run through or beside it (Creator 2026-10-23: "前に切り上げてほしい、横じゃなくて"),
         * turned [SKEW] off the facing so it still reads as an arc from behind. The drawing (wind_band) is an arc round
         * a pivot a quarter of its width from one edge, the blade going from up-behind down to the ground ahead; the
         * pivot sits [ahead] in front at [shoulder] height. [rising] turns it upside down: from low, forward and up.
         */
        fun trail(size: Double, life: Int, delay: Int = 0, rising: Boolean = false, shoulder: Double = 1.5, ahead: Double = 1.2): CoreCombatMeshPart {
            val skew = .26
            return if (!rising) part("wind", local(0.0, shoulder + .031 * size, ahead + .25 * size), size, yaw + PI / 2 - skew, life, delay = delay, ground = false)
            else part("wind", local(0.0, shoulder - .031 * size, ahead + .25 * size), size, yaw - PI / 2 + skew, life, delay = delay, ground = false, roll = PI)
        }
        if (e.sceneId == TACKLE) return tackle(e, yaw)
        if (e.sceneId == CoreWarriorLeap.ICON) return leap(e, yaw)
        // 全力の一振り is retired as an ultimate; anything still naming it is drawn as 地割り's full blow.
        if (e.sceneId == SLAM || e.sceneId == CoreWarriorRules.FULL_SWING) return slam(e, yaw)
        if (e.sceneId == CoreWarriorRules.ARMOR && e.phase == CoreSkillVisualPhase.PULSE && e.pulse >= CoreWarriorRules.ARMOR_BEAT_PULSE) {
            // The heartbeat: blood sprays out of the body all round, drops flung out land in a ring of splats.
            val n = e.pulse
            for (k in 0 until 6) {
                val a = 2 * PI * k / 6 + hash(n, k, 301) * .6
                out += part("bmist", Vec(sin(a) * .4, .9 + .5 * hash(n, k, 302), cos(a) * .3), .5 + .2 * hash(n, k, 303), 0.0, 8,
                    ground = false, billboard = true, roll = hash(n, k, 304) * 6.0)
            }
            for (k in 0 until 8) {
                val a = 2 * PI * k / 8 + hash(n, k, 305) * .5
                val y0 = .9 + .6 * hash(n, k, 306); val speed = 2.2 + hash(n, k, 307)
                val flight = ((1.2 + sqrt(1.44 + 2 * 9.8 * y0)) / 9.8 * 20).toInt()     // ticks until it lands
                out += part("bfling", Vec(sin(a) * .25, y0, cos(a) * .25), .16, a, flight, ground = false, billboard = true)
                    .copy(travel = Vec(sin(a) * speed, 0.0, cos(a) * speed))
                val reach = .25 + speed * flight / 20.0
                out += part("bsplat", Vec(sin(a) * reach, .03, cos(a) * reach), .4 + .2 * hash(n, k, 308), a, 60, delay = flight)
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.ARMOR && e.phase == CoreSkillVisualPhase.PULSE && e.pulse >= CoreWarriorRules.ARMOR_BLEED_PULSE) {
            // While it lasts: a drop of blood lets go of the body and falls to a splat that spreads, darkens and soaks
            // away; red scale-dust rises off the body, turning as it goes so it glints.
            val n = e.pulse
            val a = hash(n, 311) * 2 * PI; val y0 = .5 + hash(n, 312)
            val r = if (y0 > .75) .32 else .18
            val at = Vec(cos(a) * r, y0, sin(a) * r * .6)
            val fall = (sqrt(2 * y0 / 9.8) * 20).toInt().coerceAtLeast(2)
            out += part("bdrop", at, .24, 0.0, fall, ground = false, billboard = true)
            out += part("bsplat", Vec(at.x() * 1.3, .03, at.z() * 1.3), .45 + .25 * hash(n, 313), a, 40, delay = fall)
            for (k in 0 until 2) {
                val b = hash(n, k, 314) * 2 * PI
                out += part("scale${(hash(n, k, 315) * 100).toInt()}", Vec(cos(b) * (.15 + .3 * hash(n, k, 316)), .5 + .3 * hash(n, k, 317),
                    sin(b) * (.1 + .2 * hash(n, k, 318))), .1, 0.0, 34 + (hash(n, k, 319) * 16).toInt(), ground = false, billboard = true)
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.ARMOR && e.phase == CoreSkillVisualPhase.PULSE) {
            // 狂戦士の甲冑: black smoke climbing the body from the feet to the head (no cracks in the ground: Creator
            // 2026-10-24); a faint red light round the body stays with it for the whole ten seconds.
            for (k in 0 until 8) {
                val a = yaw + k * 2.4; val r = .6 + .15 * hash(k, 226)
                out += part("smoke", Vec(sin(a) * r, .1 + .26 * k, cos(a) * r), 1.5 - .06 * k, 0.0, 16, ground = false,
                    delay = k, billboard = true, roll = hash(k, 225) * 6.0)
            }
            for ((y, size) in listOf(1.0 to 2.1, 1.25 to 1.35))
                out += part("rhalo", Vec(0.0, y, 0.0), size, 0.0, e.skill.duration, ground = false, billboard = true).copy(followOwner = true)
            return out
        }
        if (e.sceneId == CoreWarriorRules.AWAKEN && e.phase == CoreSkillVisualPhase.PULSE && e.pulse >= CoreWarriorRules.AWAKEN_CLING_PULSE) {
            // While it lasts the beast is worn (Creator 2026-10-24: 「自分に纏う感じ」): smoke wells up from the surface of
            // the body (shoulders, back and arms most), streams up and a little back off it, and is left behind as the warrior
            // moves; red scale-dust rises off the body now and then.
            val n = e.pulse
            for (k in 0 until 5) {
                val h = { m: Int -> hash(n, k, m) }
                val pick = h(401)
                // the body's boxes in blocks: (centre x, y, z), (half x, y, z), front is +z
                val (c, half) = when {
                    pick < .16 -> Vec(if (h(402) < .5) -.12 else .12, .37, 0.0) to Vec(.12, .37, .12)
                    pick < .5 -> Vec(0.0, 1.1, 0.0) to Vec(.24, .36, .13)
                    pick < .66 -> Vec(-.36, 1.1, .05) to Vec(.11, .36, .11)
                    pick < .82 -> Vec(.36, 1.1, .05) to Vec(.11, .36, .11)
                    else -> Vec(0.0, 1.62, 0.0) to Vec(.24, .24, .24)
                }
                val side = (h(403) * 4).toInt()
                val x = c.x() + when (side) { 0 -> half.x(); 1 -> -half.x(); else -> (h(404) * 2 - 1) * half.x() }
                val z = c.z() + when (side) { 2 -> half.z(); 3 -> -half.z(); else -> (h(405) * 2 - 1) * half.z() }
                val y = c.y() + (h(406) * 2 - 1) * half.y()
                out += part("bsmoke", local(x, y, z), .4, 0.0, 10 + (h(407) * 8).toInt(), ground = false, billboard = true,
                    roll = h(408) * 6.0).copy(travel = local(x * .5, .8, -.35))
            }
            if (n % 2 == 0) out += part("scale${(hash(n, 409) * 100).toInt()}", local((hash(n, 410) - .5) * .6, .9 + .9 * hash(n, 411),
                (hash(n, 412) - .5) * .3), .09, 0.0, 26, ground = false, billboard = true)
            return out
        }
        if (e.sceneId == CoreWarriorRules.AWAKEN && e.phase == CoreSkillVisualPhase.PULSE) {
            // 獣の目覚め: the beast roars. No stomp: the breath throws dust out flat along the ground all round and
            // shakes loose earth up off it; nothing climbs the body (that is the armor's).
            for (k in 0 until 10) {
                val a = yaw + k * 2 * PI / 10 + (hash(k, 231) - .5) * .4
                for (ring in 0 until 2) out += part("dust${84 + (k + ring) % 3}", Vec(sin(a) * (.9 + 1.3 * ring), .25, cos(a) * (.9 + 1.3 * ring)),
                    1.0 + .4 * ring, 0.0, 14, ground = false, delay = ring * 3, billboard = true, roll = hash(k, ring, 232) * 6.0)
            }
            for (k in 0 until 5) {
                val a = yaw + k * 2 * PI / 5 + hash(k, 233)
                out += part("clod${120 + k}", Vec(sin(a) * 1.4, .2, cos(a) * 1.4), .25 + .15 * hash(k, 234), a, 18, ground = false,
                    delay = 2 + k % 2).copy(startSize = .9)
            }
            // The warrior's eyes burn red for the whole of it, on the face whichever way it turns.
            for (s in listOf(-1, 1))
                out += part("beye", local(s * .09, 1.62, .27), .12, 0.0, e.skill.duration, ground = false, billboard = true).copy(followOwner = true)
            return out
        }
        if (e.sceneId == CoreWarriorRules.SHATTER && e.phase == CoreSkillVisualPhase.PREPARE) {
            // The warning (Earth Eater): before the blow, thin cracks creep out under the feet and stones lift.
            val life = e.prepareDuration.coerceIn(2, 20)
            for (k in 0 until 3) {
                val a = yaw + .7 + k * 2.2 + (hash(k, 191) - .5) * .8
                val len = 1.4 + .9 * hash(k, 195)
                out += part("furrow", Vec(sin(a) * len / 2, .12, cos(a) * len / 2), 1.0, a, life).copy(scale = Vec(len, 1.0, len))
            }
            for (k in 0 until 6) {
                val a = hash(k, 192) * 2 * PI; val d = .9 + 1.6 * hash(k, 193)
                out += part("pebble$k", Vec(sin(a) * d, .1, cos(a) * d), .65 + .3 * hash(k, 194), a, life, ground = false, delay = k % 3)
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.LAUNCHER && e.phase == CoreSkillVisualPhase.PREPARE) {
            // The blade is dragged along the ground behind the warrior before it comes up: a scrape opening forward.
            out += part("furrow", local(0.0, .12, .2), 1.0, yaw, e.prepareDuration.coerceIn(2, 20) + 20).copy(scale = Vec(2.6, 1.0, 2.6))
            for (k in 0 until 3) out += part("clod${95 + k}", local((k - 1) * .3, .2, .9), .25, yaw + k * 2.0, 12, ground = false,
                delay = e.prepareDuration.coerceIn(2, 20) - 2).copy(startSize = .5)
            return out
        }
        if (e.sceneId in ownedIds && e.phase == CoreSkillVisualPhase.PREPARE) {
            // The wind-up: the weight settles, a little dust off the feet.
            out += part("dust${20}", Vec(0.0, .3, 0.0), .8, 0.0, e.prepareDuration.coerceIn(1, 12), ground = false, billboard = true)
            return out
        }
        if (e.sceneId in ownedIds && e.phase != CoreSkillVisualPhase.PULSE) {
            // A hit landing on an enemy: a clod or two knocked off at its feet.
            for (k in 0 until 2) out += part("clod${90 + k}", Vec((k - .5) * .5, .2, 0.0), .3, yaw + k * 2.0, 14, ground = false).copy(startSize = .6)
            return out
        }
        if (e.sceneId == CoreWarriorRules.DRAG) {
            // 引き摺り (Creator 2026-10-23): the cause is the greatsword itself, held low at the right side with its tip
            // on the ground behind (the held one is hidden meanwhile); behind the tip the ground is scored in a line
            // that follows the walk; the drag ends with a cut coming down in front.
            when {
                e.pulse == 0 -> (e.weaponModel ?: "weapons/greatsword_t1").let { model ->
                    out += part("dragsword:$model", local(-.6, .45, -.85), 1.7, yaw + DRAG_SWORD_YAW, CoreWarriorRules.DRAG_TICKS + 2, ground = false,
                        pitch = DRAG_SWORD_PITCH, roll = DRAG_SWORD_ROLL).copy(followOwner = true)
                }
                e.pulse < CoreWarriorRules.DRAG_FINISH_PULSE -> {
                    out += part("furrow", Vec(0.0, .12, 0.0), 1.0, yaw, 40).copy(scale = Vec(1.5, 1.0, 1.5))
                    if (e.pulse % 2 == 0) out += part("clod${100 + e.pulse % 6}", Vec(0.0, .2, 0.0), .22 + .1 * hash(e.pulse, 201), yaw + hash(e.pulse, 202) * 6.0,
                        12, ground = false).copy(startSize = .45)
                }
                else -> {
                    out += trail(5.2, 8)
                    out += trail(5.8, 7, delay = 1, shoulder = 1.55)
                    val at = local(0.0, .25, 2.4)
                    for (k in 0 until 6) out += part("clod${110 + k}", at.add((hash(k, 203) - .5) * .8, 0.0, (hash(k, 204) - .5) * .8), .3 + .2 * hash(k, 205),
                        yaw + hash(k, 206) * 6.0, 20, ground = false, delay = 1 + k / 3).copy(startSize = 1.0)
                    out += part("furrow", local(0.0, .12, 2.6), 1.0, yaw, 34).copy(scale = Vec(3.4, 1.0, 3.4))
                }
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.LAUNCHER) {
            // 斬り上げ: the cause is the blade's trail going UP (the overhead trail turned round in its plane), then earth
            // erupts under the one it throws, thrown high, with a slab torn up.
            out += trail(4.4, 8, rising = true, shoulder = 1.3, ahead = .6)
            out += trail(5.0, 7, delay = 1, rising = true, shoulder = 1.25, ahead = .6)
            val at = local(0.0, .25, 2.3)
            for (k in 0 until 7) out += part("clod${24 + k}", at.add((hash(k, 196) - .5) * .8, 0.0, (hash(k, 197) - .5) * .8),
                .3 + .25 * hash(k, 198), yaw + hash(k, 199) * 6.0, 24, ground = false, delay = 1 + k / 3).copy(startSize = 1.7)
            for (k in 0 until 2) out += part("turf${120 + k}", at.add((k - .5) * .9, 0.0, 0.0), .8, yaw + (k - .5), 22, delay = 1,
                roll = (k - .5) * 2.0 * .9)
            out += part("dust${80}", at.add(0.0, .4, 0.0), 1.5, 0.0, 10, ground = false, delay = 1, billboard = true)
            return out
        }
        if (e.sceneId == CoreWarriorRules.FRENZY) {
            // 狂乱: each swing leaves a dark band of wind, left and right in turn, and kicks earth off the tip; the
            // last swing is the overhead blow and tears a short furrow forward like a small 地割り.
            val last = e.pulse >= e.skill.pulses - 1
            if (!last) {
                // The cause first: the blade's dark trail, each swing in its own plane (Creator 2026-10-23: the angles
                // must read, and it should look like thrashing about). The trail's plane is tilted with pitch: 0 is the
                // upright overhead plane, ±0.8 the diagonals, 1.45 nearly flat; roll only turns it within its plane.
                // Swings come fast, each trail outlives the gap so two overlap, and each is bigger than the one before.
                val tilts = doubleArrayOf(.8, -.8, 1.45, -.45, .45)
                val tilt = tilts[e.pulse % tilts.size]
                val grow = 1.0 + .14 * e.pulse
                out += part("wind", local(0.0, 1.15, .9), 5.4 * grow, yaw + PI / 2 - 1.15, 9, ground = false, pitch = -tilt,
                    roll = if (e.pulse % 2 == 0) 0.0 else .35)
                // A second trail a tick behind and a little wider: the weight of the blade, not a thin line.
                out += part("wind", local(0.0, 1.1, .85), 6.1 * grow, yaw + PI / 2 - 1.15, 8, ground = false, pitch = -tilt * .92,
                    roll = (if (e.pulse % 2 == 0) 0.0 else .35) + .12, delay = 1)
                // Where the edge comes down: earth bursts out, more each swing, a slab torn up with it.
                val side = when { tilt > 1.2 -> 0.0; tilt > 0 -> -1.0; else -> 1.0 }   // the side the trail comes down on
                val at = local(side * 1.0, .25, 2.2 + .15 * e.pulse)
                for (k in 0 until 5 + e.pulse) out += part("clod${40 + k}", at.add((hash(e.pulse, k, 150) - .5) * .7, 0.0, (hash(e.pulse, k, 149) - .5) * .7),
                    .3 + .2 * hash(e.pulse, k, 151), yaw + hash(e.pulse, k, 152) * 6.0, 18, ground = false, delay = 1 + k / 3).copy(startSize = .8 + .1 * e.pulse)
                out += part("turf${110 + e.pulse}", at, (.6 + .3 * hash(e.pulse, 153)) * grow, yaw + side * .8, 20, delay = 1,
                    roll = -(if (side == 0.0) .8 else side) * (.7 + .3 * hash(e.pulse, 154)))
                for (k in 0 until 2) out += part("dust${70 + k}", at.add(0.0, .2 + .4 * k, 0.0), (1.3 - .25 * k) * grow, 0.0, 10, ground = false,
                    delay = 1, billboard = true, roll = hash(e.pulse, k, 148) * 6.0)
                return out
            }
            // The last swing is the biggest: straight down from overhead, and the ground splits under it.
            out += trail(5.6, 9)
            out += trail(6.2, 8, delay = 1, shoulder = 1.55)
            out += part("furrow", local(0.0, .12, 2.8), 1.0, yaw, 34).copy(scale = Vec(4.8, 1.0, 4.8))
            for (k in 0 until 8) out += part("clod${20 + k}", local((hash(k, 159) - .5) * 1.2, .25, 2.2 + .5 * k), .35 + .25 * hash(k, 160),
                yaw + hash(k, 161) * 6.0, 20, ground = false, delay = k / 2).copy(startSize = 1.2)
            for (k in 0 until 6) {
                val side = if (k % 2 == 0) -1.0 else 1.0
                val tt = 1.0 - (k / 2) / 3.0
                out += part("turf${80 + k}", local(side * (.45 + .2 * hash(k, 153)), .12, 1.0 + (1 - tt) * 2.6), (.6 + .5 * tt) * (.8 + .4 * hash(k, 154)),
                    yaw + (hash(k, 155) - .5) * 1.0, 28, delay = ((1 - tt) * 3).toInt(), roll = -side * (.4 + .5 * hash(k, 156)))
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.SKYFALL) {
            // 天落とし: the landing caves the ground in and each ring heaves a circle of turf up, tipped outward, a
            // moment after the one inside it. Ring 0 also throws the biggest slabs right round the blow.
            val r = e.skill.radius
            // Each ring heaves up and drops back quickly, so the eye follows a wave going out, not a bullseye.
            if (e.pulse == 0) {
                // The cause: the blade brought straight down with the fall, then a column of dust off the impact point.
                out += trail(5.2, 6)
                for (k in 0 until 3) out += part("dust${74 + k}", Vec(0.0, .4 + .8 * k, 0.0), 2.4 - .4 * k, 0.0, 12 + 2 * k, ground = false,
                    billboard = true, roll = hash(k, 159) * 6.0)
            }
            val count = (5 + 1.5 * r).roundToInt().coerceAtMost(14)
            for (k in 0 until count) {
                if (hash(e.pulse, k, 160) < .18) continue
                val a = yaw + k * 2 * PI / count + hash(e.pulse, k, 161) * .3
                val d = r - .5 - .4 * hash(e.pulse, k, 162)
                out += part("turf${90 + k}", Vec(sin(a) * d, .12, cos(a) * d), (.6 + .35 * hash(e.pulse, k, 163)) * (1.15 - .1 * e.pulse),
                    a + PI / 2, 14, delay = k % 2, roll = -(.9 + .4 * hash(e.pulse, k, 164)), pitch = (hash(e.pulse, k, 165) - .5) * .4)
            }
            for (k in 0 until 4) {
                val a = yaw + k * PI / 2 + hash(e.pulse, k, 169) * 1.2
                out += part("clod${70 + k}", Vec(sin(a) * (r - .4), .25, cos(a) * (r - .4)), .35 + .2 * hash(e.pulse, k, 170), a, 16,
                    ground = false, delay = k % 2).copy(startSize = .8)
            }
            if (e.pulse == 0) {
                // The ground caves in under the blow (the round crater fits a landing; 地割り runs forward instead).
                out += part("crater", Vec(0.0, .11, 0.0), 4.2, yaw + hash(e.pulse, 175) * 6.0, 44)
                // The blow itself: five uneven slabs thrown up round the blade (not a flower: angles and sizes vary).
                for (k in 0 until 5) {
                    val a = yaw + k * 2 * PI / 5 + (hash(k, 166) - .5) * .9
                    val d = .9 + .5 * hash(k, 171)
                    out += part("turf${130 + k}", Vec(sin(a) * d, .12, cos(a) * d), .9 + .6 * hash(k, 172), a + PI / 2 + (hash(k, 173) - .5) * .6, 32,
                        roll = -(.8 + .5 * hash(k, 167)), pitch = (hash(k, 174) - .5) * .5)
                }
                for (k in 0 until 6) {
                    val a = yaw + k * PI / 3
                    out += part("clod${50 + k}", Vec(sin(a) * .6, .3, cos(a) * .6), .4 + .3 * hash(k, 168), a, 22, ground = false,
                        delay = k / 3).copy(startSize = 1.2)
                }
            }
            return out
        }
        if (e.sceneId == CoreWarriorRules.SHATTER) {
            // 大地崩し: the blade is driven in and the ground round it breaks up everywhere at once: cracks run out from
            // the blade, the turf between them is flung up in slabs, the wave reaching the edge last.
            val r = e.skill.radius
            // The cause: the blade driven straight down, then a burst at the point it went in.
            out += trail(3.6, 6, shoulder = 1.6)
            for (k in 0 until 2) out += part("dust${78 + k}", Vec(0.0, .4 + .6 * k, 0.0), 1.6 - .3 * k, 0.0, 12, ground = false,
                delay = 1, billboard = true, roll = hash(k, 189) * 6.0)
            // Five cracks, uneven in angle and length (not a star), each widest at the blade.
            val angles = (0 until 5).map { k -> yaw + k * 2 * PI / 5 + (hash(k, 171) - .5) * .9 }
            for ((k, a) in angles.withIndex()) {
                val len = r * (.6 + .4 * hash(k, 170))
                out += part("furrow", Vec(sin(a) * len / 2, .12, cos(a) * len / 2), 1.0, a, 40).copy(scale = Vec(len, 1.0, len))
            }
            // The ground between the cracks comes up in big slabs, tipped away from the blade, inner ones first.
            for (k in 0 until 5) for (j in 0 until 3) {
                val n = k * 3 + j
                if (hash(n, 181) < .25) continue
                val a = angles[k] + PI / 5 + (hash(n, 172) - .5) * .5
                val d = 1.2 + j * (r - 1.8) / 2.6 + .4 * hash(n, 173)
                out += part("turf${140 + n}", Vec(sin(a) * d, .12, cos(a) * d), (.95 + .45 * hash(n, 174)) * (1.0 - .12 * j), a + PI / 2, 30,
                    delay = j * 2, roll = -(.6 + .5 * hash(n, 176)), pitch = (hash(n, 177) - .5) * .4)
            }
            for (k in 0 until 10) {
                val a = hash(k, 178) * 2 * PI
                val d = 1.0 + (r - 1.5) * hash(k, 179)
                out += part("clod${60 + k}", Vec(sin(a) * d, .25, cos(a) * d), .35 + .3 * hash(k, 180), a, 22, ground = false,
                    delay = (d / r * 5).toInt()).copy(startSize = .8 + .1 * r / 2)
            }
            return out
        }
        return out
    }

    private fun piece(name: String, at: Vec, scale: Double, partYaw: Double, life: Int, pitch: Double = 0.0, delay: Int = 0,
                      ground: Boolean = true, roll: Double = 0.0, billboard: Boolean = false, travel: Vec = Vec.ZERO,
                      follow: Boolean = false) =
        CoreCombatMeshPart("$PREFIX$name", "steel", at, Vec(scale, scale, scale), yaw = partYaw, pitch = pitch, roll = roll,
            durationTicks = life, startSize = 1.0, endSize = 1.0, delayTicks = delay, ground = ground, billboard = billboard,
            travel = travel, followOwner = follow)

    /** A slab of broken ground thrown from [at] along [travel] (blocks per second; y is the upward throw). */
    private fun thrown(k: Int, at: Vec, travel: Vec, size: Double, delay: Int = 0, life: Int = 24) =
        piece("rslab$k", at, size, hash(k, 601) * 6.0, life, delay = delay, ground = false, travel = travel)

    /** A plate of ground heaved up on edge at [at], its broad face across [outward] (radians, as yaw), leaning out. */
    private fun heaved(at: Vec, outward: Double, size: Double, lean: Double, delay: Int, life: Int = 34) =
        piece("rplate", at, size, outward, life, pitch = lean, delay = delay)

    /**
     * 体当たり (2026-10-27). The own charge is in the hand; the world gets what the body leaves: each step scrapes a
     * streak in the ground and kicks grit back off the heel (behind the eye, for the others); where the shoulder lands,
     * low and over two blocks ahead, an iron-and-dust shock bursts out along the ground with slabs of it thrown on, and
     * the met one's feet are driven back. A blow caught whole rings off iron in front of the body (carried with it).
     */
    private fun tackle(e: CoreSkillEffect, yaw: Double): List<CoreCombatMeshPart> {
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        val out = mutableListOf<CoreCombatMeshPart>()
        val n = e.pulse
        when {
            e.phase == CoreSkillVisualPhase.PREPARE -> {
                // The weight goes onto the back foot: it digs in and kicks a little grit back just before the charge.
                val at = (e.prepareDuration - 2).coerceAtLeast(0)
                out += piece("skid", local(.2, .12, -.2), 1.0, yaw, 26, delay = at).copy(scale = Vec(.6, 1.0, .55))
                out += piece("kick", local(.25, .3, -.5), .8, 0.0, 8, delay = at, ground = false, billboard = true, travel = local(.1, .15, -.4))
            }
            e.phase == CoreSkillVisualPhase.CONTACT -> {
                // At the feet of the one met (e.origin, the accepted hit): two slabs of ground knocked out from under it,
                // away from the warrior. Nothing up at the height of the eye.
                for (k in 0 until 2) out += thrown(k + 10, local((k - .5) * .5, .2, .45), local((k - .5) * 2.2, 3.4, 2.4), .4 + .1 * k, delay = 1)
            }
            n in TACKLE_STEP_PULSE until TACKLE_STOP_PULSE -> {
                val k = n - TACKLE_STEP_PULSE
                val side = if (k % 2 == 0) 1.0 else -1.0
                // The streak: one step's length, laid end to end behind the body as it goes.
                out += piece("skid", local(0.0, .12, -.15), 1.0, yaw, 28).copy(scale = Vec(1.05, 1.0, .78))
                out += piece("kick", local(side * .24, .45, -.6), 1.25, 0.0, 9, ground = false, billboard = true,
                    travel = local(side * .4, .55, -1.1))
            }
            n == TACKLE_STOP_PULSE -> {
                // Ran out: the feet dig in and skid, grit thrown on ahead.
                out += piece("skid", local(0.0, .12, .15), 1.0, yaw, 30).copy(scale = Vec(1.15, 1.0, 1.05))
                for (s in listOf(-1.0, 1.0)) out += piece("kick", local(s * .45, .25, 1.75), .9, 0.0, 9, ground = false,
                    billboard = true, travel = local(s * .3, .2, .7))
            }
            n == TACKLE_HIT_PULSE || n == TACKLE_CAUGHT_PULSE -> {
                val big = n == TACKLE_CAUGHT_PULSE
                val size = if (big) 3.0 else 2.3
                // The feet planted at the blow, and the met one's feet driven back along the ground ahead.
                out += piece("skid", local(0.0, .12, .15), 1.0, yaw, 30).copy(scale = Vec(1.15, 1.0, .9))
                out += piece("skid", local(0.0, .121, 2.75), 1.0, yaw, 30, delay = 1).copy(scale = Vec(1.25, 1.0, if (big) 1.8 else 1.3))
                // The shock bursts out on both sides of the one met (it stands about a block ahead, so the burst is put
                // beside it, low, its foot on the ground: the drawing's ground row is .354 of it under its middle), and
                // a lower one at its feet past it.
                for (s in listOf(-1.0, 1.0)) out += piece("shock", local(s * 1.4, .354 * size * .85, 1.8), size * .85, 0.0, 9,
                    ground = false, billboard = true, roll = -s * .35)
                out += piece("shock", local(0.0, .354 * size * .7, 2.3), size * .7, 0.0, 8, delay = 1, ground = false, billboard = true)
                // Broad slabs of the ground thrown out to both sides of it, tumbling.
                val slabs = if (big) 8 else 4
                for (k in 0 until slabs) {
                    val s = if (k % 2 == 0) 1.0 else -1.0
                    val a = s * (.75 + .8 * hash(n, k, 611))
                    val speed = 1.8 + 1.4 * hash(n, k, 612)
                    out += thrown(k, local(s * .7, .25, 1.5), local(sin(a) * speed * 1.2, 3.0 + 1.6 * hash(n, k, 613), cos(a) * speed + .6),
                        (.45 + .25 * hash(n, k, 614)) * (if (big) 1.2 else 1.0), delay = k / 2 % 2)
                }
                if (big) {
                    // Caught whole and given back: a second shock thrown on past the first, iron sparks sprayed ahead.
                    for (s in listOf(-1.0, 1.0)) out += piece("shock", local(s * 1.9, .354 * 2.0, 2.3), 2.0, 0.0, 8, delay = 2, ground = false,
                        billboard = true, roll = -s * .5)
                    for (k in 0 until 6) {
                        val a = (k - 2.5) * .45
                        out += piece("fspark", local(sin(a) * .3 + (if (a < 0) -.9 else .9), .7, 1.6), .6, 0.0, 7, ground = false, billboard = true,
                            travel = local(sin(a) * 2.6, 1.2 + .8 * hash(k, 615), 1.4 + cos(a)), roll = -a * 1.6)
                    }
                }
            }
            n == TACKLE_CATCH_PULSE -> {
                // Caught: iron rings in front of the body (carried along with the charge), well clear of the eye and under
                // its line, and sparks go off round it, none straight up across the middle of the view.
                val at = local(0.0, .9, 2.4)
                out += piece("clang", at, 1.8, 0.0, 6, ground = false, billboard = true, follow = true)
                for ((k, deg) in listOf(-160.0, -125.0, -90.0, -55.0, -20.0, 15.0, 165.0, 200.0).withIndex()) {
                    val a = Math.toRadians(deg)
                    out += piece("fspark", at, .75, 0.0, 7, ground = false, billboard = true, follow = true,
                        travel = local(cos(a) * 1.7, sin(a) * 1.3, .2), roll = PI / 2 - a + (hash(k, 616) - .5) * .2)
                }
            }
        }
        return out
    }

    /**
     * 跳び叩き (2026-10-27). The push-off leaves a dent trodden into the ground with cracks pushed out behind and grit
     * thrown back; the landing's blade comes down in front: a cleft cut along the facing, a dish caved in round it with
     * cracks running out, its two lips heaved up, slabs thrown on ahead. After a far leap all of it is bigger and a
     * horseshoe of rock plates heaves up round the blow a moment later (open toward the warrior, out of the eye's way).
     */
    private fun leap(e: CoreSkillEffect, yaw: Double): List<CoreCombatMeshPart> {
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        val out = mutableListOf<CoreCombatMeshPart>()
        val n = e.pulse
        when {
            e.phase == CoreSkillVisualPhase.CONTACT ->
                for (k in 0 until 2) out += thrown(k + 10, local((k - .5) * .5, .2, .45), local((k - .5) * 2.0, 3.8, 1.8), .42, delay = 2)
            n == LEAP_TAKEOFF_PULSE -> {
                out += piece("dent", local(0.0, .12, -.05), 1.7, yaw, 40)
                for (k in 0 until 4) {
                    val a = yaw + PI + (k - 1.5) * .75
                    out += piece("kick", Vec(sin(a) * .55, .3, cos(a) * .55), 1.0, 0.0, 9, delay = k % 2, ground = false, billboard = true,
                        travel = Vec(sin(a) * .9, .35, cos(a) * .9))
                }
                for (s in listOf(-1.0, 1.0)) out += thrown(if (s < 0) 20 else 21, local(s * .35, .2, -.3), local(s * .6, 3.0, -1.4), .32)
            }
            n == LEAP_LAND_PULSE || n == LEAP_FAR_LAND_PULSE -> {
                // The landing breaks the ground the way 天落とし and 大地崩し do (Creator 2026-10-27: 「ほかの割れる見た目と
                // 同じにしてほしい、ブロックが浮くやつ」): a crater where the blade comes down in front, turf heaved up in
                // blocks round it, tipped outward, and clods thrown. A far leap breaks a wider ring a moment later.
                val far = n == LEAP_FAR_LAND_PULSE
                val k0 = if (far) 1.25 else 1.0
                // Centred on the landing itself (Creator 2026-10-27: 「なんで足元じゃなくて前にでるんだ」), only the
                // blade's half block ahead of the feet; the heaved ring goes round the body, clear of the eye.
                val ahead = .5
                val at = local(0.0, .12, ahead)
                // The blade meets the ground a tick and a half into the slam (the hand's SLAM_LOW): the ground answers at 2.
                val hit = 2
                out += piece("crater", at, 3.4 * k0, yaw + hash(n, 640) * 6.0, 44, delay = hit)
                fun ring(count: Int, radius: Double, size: Double, delay: Int, seed: Int, base: Int) {
                    for (k in 0 until count) {
                        if (hash(seed, k, 641) < .15) continue
                        val a = yaw + k * 2 * PI / count + (hash(seed, k, 642) - .5) * .5
                        val d = radius * (.85 + .3 * hash(seed, k, 643))
                        val p = at.add(sin(a) * d, 0.0, cos(a) * d)
                        if (hypot(p.x(), p.z()) < 1.6) continue          // nothing heaves up in the warrior's own face
                        out += piece("turf${base + k}", p, size * (.8 + .45 * hash(seed, k, 644)), a + PI / 2, 30,
                            delay = delay + k % 2, roll = -(.8 + .5 * hash(seed, k, 645)), pitch = (hash(seed, k, 646) - .5) * .5)
                    }
                }
                ring(9, 2.0 * k0, .95 * k0, hit, 1, 150)
                // Clods thrown up off the far side of the crater (none toward the warrior's eye).
                for (k in 0 until 6) {
                    val a = yaw + (k - 2.5) * .45 + (hash(k, 647) - .5) * .3
                    out += piece("clod${150 + k}", at.add(sin(a) * 1.9, .2, cos(a) * 1.9), (.38 + .28 * hash(k, 648)) * k0, a, 22,
                        delay = hit + k / 3, ground = false).copy(startSize = 1.2)
                }
                if (far) ring(13, 3.4, .85, hit + 3, 2, 160)
            }
        }
        return out
    }

    /** 地割り: how far ahead the blade's point meets the ground (the hand's slam lays it along the middle of the view). */
    private const val SLAM_IMPACT = 2.4
    /** Nothing stands up nearer than this in front of the eye; only flat marks lie there. */
    private const val CLEAR = 2.2
    /** 全力の一振り's moments (its wind-up, its blow, its blood): worth the ultimate's piece budget (CoreCombatMeshes). */
    fun fullBlow(e: CoreSkillEffect) = e.job == CoreClass.WARRIOR && e.sceneId == SLAM && (e.pulse == FULL_SLAM_PULSE ||
        e.pulse == FULL_WINDUP_PULSE || e.pulse in CoreWarriorRules.FULL_SWING_GEYSER_PULSE until CoreWarriorRules.FULL_SWING_GEYSER_PULSE + CoreWarriorRules.FULL_SWING_GEYSER_STEPS)

    /**
     * 地割り (2026-10-27). The own swing is the hand's (the rise and the slam beat); the world gets only what the blade
     * leaves. Where its point meets the ground ahead the ground caves in, and a crack runs forward from there: its lips
     * heave up as blocks of ground tipped outward as it runs, clods are thrown off it, and its head breaks open. The
     * beast sets the reach. 全力の一振り (a full beast) is the same family, bigger and longer: while it is charged the
     * ground under the feet gives (cracks, stones lifting), the crack forks, and after the blow blood bursts up out of
     * the rift step by step away from the warrior. Nothing stands up within [CLEAR] blocks in front of the eye.
     */
    private fun slam(e: CoreSkillEffect, yaw: Double): List<CoreCombatMeshPart> {
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z)
        val out = mutableListOf<CoreCombatMeshPart>()
        val n = e.pulse
        val geyser = n >= CoreWarriorRules.FULL_SWING_GEYSER_PULSE && n < CoreWarriorRules.FULL_SWING_GEYSER_PULSE + CoreWarriorRules.FULL_SWING_GEYSER_STEPS
        val full = n == FULL_SLAM_PULSE || e.sceneId == CoreWarriorRules.FULL_SWING
        val reach = e.skill.radius.coerceIn(3.0, 14.0)
        val furrow = reach - .8
        val head = SLAM_IMPACT - .6 + furrow
        val weight = ((reach - 4.0) / 4.0).coerceIn(0.0, 1.0)   // 0 at ≈4 m (no beast) .. 1 at ≈8 m and over
        // The crack runs its whole length in this many ticks; each piece along it answers when the crack gets there.
        val run = if (full) 6.0 else 4.0
        fun at(along: Double) = ((along - SLAM_IMPACT) / (head - SLAM_IMPACT) * run).toInt().coerceAtLeast(0)
        when {
            e.phase == CoreSkillVisualPhase.PREPARE -> Unit   // the wind-up is the hand's (and FULL_WINDUP_PULSE)
            e.phase == CoreSkillVisualPhase.CONTACT ->
                // At the feet of the one struck: two slabs knocked out from under it, away from the warrior.
                for (k in 0 until 2) out += thrown(k + 30, local((k - .5) * .5, .2, .45), local((k - .5) * 2.0, 4.2, 1.6), .42, delay = 1)
            n == FULL_WINDUP_PULSE -> {
                // Charged on the shoulder: short cracks open out from the feet one after another and stones lift off
                // the ground round the body and tremble (none in front of the eye), until the blow.
                val life = CoreWarriorRules.FULL_SWING_WINDUP + 2
                for ((k, turn) in listOf(PI + .5, -1.8, 1.9, PI - .6, .35).withIndex()) {
                    val a = yaw + turn + (hash(k, 701) - .5) * .3
                    val len = 1.3 + .9 * hash(k, 702)
                    val delay = k * 3
                    out += piece("furrow", Vec(sin(a) * len / 2, .12, cos(a) * len / 2), 1.0, a, life - delay + 2, delay = delay)
                        .copy(scale = Vec(len, 1.0, len))
                }
                for (k in 0 until 7) {
                    val a = yaw + PI / 2 + PI * k / 6 + (hash(k, 703) - .5) * .3     // the sides and the back
                    val d = 1.1 + 1.0 * hash(k, 704)
                    val delay = 2 + k * 2
                    out += piece("pebble${20 + k}", Vec(sin(a) * d, .1, cos(a) * d), .3 + .2 * hash(k, 705), a, life - delay,
                        ground = false, delay = delay)
                }
            }
            n == SLAM_PULSE || n == FULL_SLAM_PULSE -> {
                val bulk = if (full) 1.25 else .8 + .2 * weight
                // The blade's point dents the ground (flat, unlit; no round crater, Creator: that is a landing), and the
                // crack runs on from it, widest at the blow. A long crack keeps its width (it would fill the view).
                out += piece("dent", local(0.0, .123, SLAM_IMPACT + .2), if (full) 1.5 else 1.1, yaw, 44)
                out += piece("furrow", local(0.0, .121, SLAM_IMPACT - .6 + furrow / 2), 1.0, yaw, 44).copy(scale = Vec(min(furrow, 5.5), 1.0, furrow))
                if (full) for ((k, turn) in listOf(-.32, .3).withIndex()) {
                    // 全力の一振り forks part way out: two branches split off the main crack.
                    val from = SLAM_IMPACT + furrow * (.45 + .15 * k)
                    val len = furrow * .4
                    out += piece("furrow", local(0.0, .122, from).add(sin(yaw + turn) * len / 2, 0.0, cos(yaw + turn) * len / 2), 1.0, yaw + turn, 40,
                        delay = at(from)).copy(scale = Vec(len, 1.0, len))
                }
                // The lips: blocks of ground heaved up along both edges, tipped outward, biggest near the blow, coming up
                // as the crack runs. Irregular on purpose (Creator: not tiles); a gap here and there.
                val rows = (furrow * (if (full) 1.1 else 1.25)).roundToInt().coerceIn(4, 12)
                for (jj in 0 until rows) for ((m, side) in listOf(-1.0, 1.0).withIndex()) {
                    val k = jj * 2 + m
                    if (hash(k, 711) < .18) continue
                    val tt = (1.0 - (jj + hash(k, 712) * .8) / rows).coerceIn(0.0, 1.0)   // 1 at the blow .. 0 at the head
                    val along = SLAM_IMPACT + .3 + (1 - tt) * (furrow - .8)
                    val hw = (.35 + .55 * tt) * (furrow / 8.0).coerceIn(.6, 1.4)
                    val size = (.45 + .65 * tt) * (.65 + .7 * hash(k, 713)) * bulk
                    out += piece("turf${k % 40}", local(side * (hw + .2 * hash(k, 714)), .12, along), size, yaw + (hash(k, 715) - .5) * 1.2,
                        34 - jj / 2, delay = at(along), roll = -side * (.45 + .65 * hash(k, 716)) * (if (full) 1.2 else 1.0),
                        pitch = (hash(k, 717) - .5) * .5)
                }
                // The head breaks open: blocks heaved up in a fan, tipped forward over it, the middle ones biggest.
                val fan = if (full) 5 else 3
                for (k in 0 until fan) {
                    val a = yaw + (k - (fan - 1) / 2.0) * .45 + (hash(k, 718) - .5) * .15
                    val d = .5 + .4 * hash(k, 719)
                    out += piece("turf${60 + k}", local(0.0, .12, head).add(sin(a) * d, 0.0, cos(a) * d), (.8 + .4 * hash(k, 720)) * bulk, a, 32,
                        delay = at(head) + abs(k - fan / 2) / 2, roll = (hash(k, 721) - .5) * .3, pitch = -(.8 + .4 * hash(k, 722)))
                }
                // Clods thrown up off the crack as it runs (the knock-up), higher with more beast; all beyond the eye.
                val clods = if (full) 8 else 5 + (3 * weight).roundToInt()
                for (k in 0 until clods) {
                    val along = SLAM_IMPACT + .6 + (k + .5) / clods * (furrow - 1.0)
                    val side = if (k % 2 == 0) -1.0 else 1.0
                    out += piece("clod${80 + k}", local(side * (.25 + .35 * hash(k, 723)), .2, along), (.3 + .3 * hash(k, 724)) * bulk,
                        yaw + side * (.6 + .6 * hash(k, 725)), 22, ground = false, delay = at(along)).copy(startSize = if (full) 1.5 else .75 + .55 * weight)
                }
                if (full) for (k in 0 until 4) {
                    // ... and slabs thrown on ahead off the head.
                    val s = if (k % 2 == 0) 1.0 else -1.0
                    out += thrown(40 + k, local(s * .5, .25, head - .4), local(s * (1.0 + 1.2 * hash(k, 726)), 4.0 + 2.0 * hash(k, 727), 2.5 + 1.5 * hash(k, 728)),
                        .55 + .2 * hash(k, 729), delay = at(head))
                }
                // A little dust where the head breaks open, low and short: it must not hide the crack.
                if (full) for (k in 0 until 2) out += piece("dust${90 + k}", local((k - .5) * 1.4, .3, head + .4), .9 + .3 * hash(k, 730), 0.0, 10,
                    ground = false, delay = at(head) + 1, billboard = true, roll = hash(k, 731) * 6.0)
            }
            n == SLAM_SPLIT_PULSE -> {
                // A second pulse (重ね撃ち): the ground bursts open again where the crack ended and forks.
                for ((m, turn) in listOf(-.55, .55).withIndex())
                    out += piece("furrow", local(0.0, .12, head - .3).add(sin(yaw + turn) * 1.6, 0.0, cos(yaw + turn) * 1.6), 1.0, yaw + turn, 36,
                        delay = m).copy(scale = Vec(3.2, 1.0, 3.2))
                for (k in 0 until 5) {
                    val a = yaw + (k - 2) * .42 + (hash(k, 131) - .5) * .12
                    val d = .9 + .5 * hash(k, 132) + .3 * (2 - abs(k - 2))
                    out += piece("turf${60 + k}", local(0.0, .12, head).add(sin(a) * d, 0.0, cos(a) * d),
                        1.1 + .4 * hash(k, 133), a, 34, delay = 1 + abs(k - 2) / 2, roll = (hash(k, 134) - .5) * .3, pitch = -(.9 + .3 * hash(k, 135)))
                }
                for (k in 0 until 6) {
                    val a = yaw + (k - 2.5) * .3
                    out += piece("clod${20 + k}", local(0.0, .3, head).add(sin(a) * .8, 0.0, cos(a) * .8), .45 + .3 * hash(k, 136), a, 22,
                        ground = false, delay = k / 4).copy(startSize = 1.2)
                }
            }
            geyser -> {
                // 全力の一振り (Creator 2026-10-24, 案B 血の噴き上げ): blood bursts up out of the rift, from the near end to
                // the far, in a wall; it falls back as drops and leaves the line soaked. Played a step a tick (two places
                // each) after the blow. It starts clear of the eye.
                val count = 2 * CoreWarriorRules.FULL_SWING_GEYSER_STEPS
                val step = n - CoreWarriorRules.FULL_SWING_GEYSER_PULSE
                val near = SLAM_IMPACT + 2.6        // past the one struck in front, out of the middle of the view
                for (i in 2 * step until 2 * step + 2) {
                    val z = near + (head - near) * i / (count - 1)
                    val delay = i % 2
                    out += piece("bmistup", local((hash(i, 501) - .5) * .5, .25, z), .8 + .05 * i, 0.0, 10, ground = false,
                        billboard = true, delay = delay, roll = hash(i, 502) * 6.0)
                    for (j in 0 until 2) {
                        val vy = 5.5 + 2.5 * hash(i, j, 503); val vx = (hash(i, j, 504) - .5) * 2.4
                        val flight = (2 * vy / 9.8 * 20).toInt()
                        out += piece("bgush", local((hash(i, j, 505) - .5) * .4, .2, z + (hash(i, j, 506) - .5) * .6), .32, 0.0, flight,
                            ground = false, billboard = true, delay = delay + 2, travel = local(vx, vy, 1.2))
                    }
                    out += piece("bsplat", local((hash(i, 507) - .5) * .8, .03, z), .8, yaw + hash(i, 508) * 6.0, 54, delay = delay + 6)
                }
            }
        }
        return out
    }

    private val rise = doubleArrayOf(0.0, .72, 1.12, 1.0)
    private val flameGrow = doubleArrayOf(.6, 1.0, 1.12, 1.1, 1.0, .9, .78, .6)

    fun pose(p: CoreCombatMeshPart, age: Double): CoreMeshPose? {
        if (!owns(p)) return null
        val t = age - p.delayTicks
        val live = t >= 0 && t < p.durationTicks
        val tick = t.toInt().coerceIn(0, p.durationTicks - 1)
        val name = p.shape.removePrefix(PREFIX)
        return when {
            name == "fissure" -> {
                val stage = when { tick < 3 -> 0; tick < 6 -> 1; tick < 9 -> 2; tick < 18 -> 3; tick < 30 -> 4; tick < 35 -> 5; else -> 6 }
                CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/fissure_$stage", live)
            }
            name == "field" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/field", live && tick < p.durationTicks - 2)
            name == "wind" -> CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll, "$DIR/wind_${tick.coerceAtMost(7)}", live)
            name.startsWith("dragsword:") -> CoreMeshPose(p.offset, p.scale, p.yaw, p.pitch, p.roll, name.substringAfter("dragsword:"), live)
            name.startsWith("pebble") -> {
                // 大地崩し's warning: a stone lifts off the ground and trembles there until the blow.
                val lift = (t.coerceAtLeast(0.0) / 10.0).coerceAtMost(1.0) * .45
                CoreMeshPose(p.offset.add(0.0, lift + sin(t * 1.9) * .04, 0.0), p.scale, p.yaw + t * .05, .3, .2, CoreCombatMeshes.GROUND, live)
            }
            name == "sweep" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, p.roll, "$DIR/sweep_${tick.coerceAtMost(7)}", live)
            name == "scrape" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/scrape_${when { tick < 4 -> tick; tick < 32 -> 4; tick < 36 -> 5; else -> 6 }}", live)
            name == "furrow" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/furrow_${when { tick < 4 -> tick; tick < 36 -> 4; tick < 40 -> 5; else -> 6 }}", live)
            name == "crater" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/crater_${when { tick < 34 -> 0; tick < 39 -> 1; else -> 2 }}", live)
            name == "dcrack" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/dcrack_${when { tick < 32 -> 0; tick < 37 -> 1; else -> 2 }}", live)
            name.startsWith("turf") -> {
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .7, 1.15, 1.05)[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 6.0
                }.coerceIn(0.0, 1.2)
                val k = name.removePrefix("turf").toInt()
                // The ground block itself (a vanilla block item renders at half size in FIXED): a flat slab of it,
                // each one its own proportions, tipped about its centre.
                val sx = p.scale.x() * (1.4 + .5 * hash(k, 99)); val sy = p.scale.x() * (.45 + .3 * hash(k, 100))
                val sz = p.scale.x() * (1.1 + .5 * hash(k, 101))
                CoreMeshPose(p.offset.add(0.0, sy * .25 - p.scale.y() * .3 * (1 - up.coerceAtMost(1.0)), 0.0), Vec(sx, sy, sz),
                    p.yaw, p.pitch * up, p.roll * up, CoreCombatMeshes.GROUND, live)
            }
            name.startsWith("clod") -> {
                val k = name.removePrefix("clod").toInt()
                val s = t.coerceAtLeast(0.0) / 20.0
                val y = (6.5 + 3.0 * hash(k, 71)) * p.startSize * s - 18.0 * s * s / 2   // startSize carries the beast's lift
                val a = p.yaw
                CoreMeshPose(p.offset.add(sin(a) * .8 * s, max(y, -.1), cos(a) * .8 * s), p.scale.mul(1.6), p.yaw + t * .3, t * .45, t * .25,
                    CoreCombatMeshes.GROUND, live)
            }
            name == "smoke" -> CoreMeshPose(p.offset.add(0.0, t.coerceAtLeast(0.0) * .05, 0.0), p.scale.mul(1.0 + tick * .04),
                0.0, 0.0, p.roll, "$DIR/smoke_${(tick * 5 / p.durationTicks).coerceAtMost(4)}", live)
            // 狂戦士の甲冑's blood. The drop falls under gravity; the flung drop flies out along its travel and falls;
            // the splat spreads, darkens, soaks away; the mist puffs; the light round the body beats once a second.
            name == "bdrop" -> CoreMeshPose(p.offset.add(0.0, -.5 * 9.8 * (t.coerceAtLeast(0.0) / 20).pow(2), 0.0), p.scale, 0.0, 0.0, 0.0,
                "$DIR/bdrop_${if (tick < p.durationTicks * .6) 0 else 1}", live)
            name == "bfling" -> {
                val s = t.coerceAtLeast(0.0) / 20
                CoreMeshPose(p.offset.add(p.travel.mul(s)).add(0.0, 1.2 * s - .5 * 9.8 * s * s, 0.0), p.scale, 0.0, 0.0, p.yaw, "$DIR/bdrop_0", live)
            }
            name == "bsplat" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/bsplat_${(tick * 5 / p.durationTicks).coerceAtMost(4)}", live)
            name == "bmist" -> CoreMeshPose(p.offset, p.scale.mul(1.0 + tick * .08), 0.0, 0.0, p.roll,
                "$DIR/bmist_${(tick * 5 / p.durationTicks).coerceAtMost(4)}", live)
            name == "bsmoke" -> {
                val u = t.coerceAtLeast(0.0) / p.durationTicks
                CoreMeshPose(p.offset.add(p.travel.mul(u)), p.scale.mul(1.0 + 1.4 * u), 0.0, 0.0, p.roll,
                    "$DIR/bsmoke_${(u * 5).toInt().coerceAtMost(4)}", live)
            }
            name.startsWith("sgather") -> {
                val u = (t.coerceAtLeast(0.0) / p.durationTicks).coerceAtMost(1.0)
                val k = name.removePrefix("sgather").toInt()
                CoreMeshPose(p.offset.add(p.travel.mul(u * u)), p.scale.mul(1 - .5 * u), 0.0, 0.0, 0.0, "$DIR/scale_${(t.toInt() + k) % 4}", live)
            }
            name == "bmistup" -> CoreMeshPose(p.offset.add(0.0, t.coerceAtLeast(0.0) * .16, 0.0), p.scale.mul(1.0 + t.coerceAtLeast(0.0) * .09),
                0.0, 0.0, p.roll, "$DIR/bmist_${(tick / 2).coerceAtMost(4)}", live)
            name == "bgush" -> {
                val s = t.coerceAtLeast(0.0) / 20
                CoreMeshPose(p.offset.add(p.travel.x() * s, p.travel.y() * s - 4.9 * s * s, p.travel.z() * s), p.scale, 0.0, 0.0, 0.0, "$DIR/bdrop_0", live)
            }
            name == "beye" -> CoreMeshPose(p.offset, p.scale.mul(if ((t.toInt() / 3) % 7 == 0) .7 else 1.0), 0.0, 0.0, 0.0, "$DIR/ember_0", live)
            name == "rhalo" -> {
                val ph = (t.coerceAtLeast(0.0) % 20) / 20
                val beat = max(exp(-ph * 12), if (ph > .17) .55 * exp(-(ph - .17) * 12) else 0.0)
                CoreMeshPose(p.offset, p.scale.mul(.9 + .3 * beat), 0.0, 0.0, 0.0, "$DIR/rhalo", live)
            }
            name.startsWith("scale") -> {
                // Red scale-dust: rises and sways, turning (four stages) so it glints and goes dark in turn.
                val k = name.removePrefix("scale").toInt()
                val u = t.coerceAtLeast(0.0) / p.durationTicks
                val sway = sin(t * .18 + k) * .16 * (.4 + u)
                CoreMeshPose(p.offset.add(sway, u * 2.1, cos(t * .15 + k * .5) * .1), p.scale.mul(1 - u * .5), 0.0, 0.0, 0.0,
                    "$DIR/scale_${((t * .5).toInt() + k) % 4}", live)
            }
            name.startsWith("dust") -> CoreMeshPose(p.offset.add(0.0, t.coerceAtLeast(0.0) * .02, 0.0), p.scale.mul(1.0 + tick * .05),
                0.0, 0.0, p.roll, "$DIR/dust_${(tick * 5 / p.durationTicks).coerceAtMost(4)}", live)
            name.startsWith("fing") -> {
                val idx = name.removePrefix("fing").toInt()
                val seg = idx % 3
                val lengths = doubleArrayOf(22.0, 18.0, 14.0).map { it / 16 * p.scale.y() }
                // Joint bends: spread a little backward while rising, then close (grip), then drag down.
                val grip = when {
                    tick < 12 -> 0.0
                    tick < 18 -> (tick - 12) / 6.0
                    else -> 1.0
                }
                val bends = doubleArrayOf(-.18 + .75 * grip, -.05 + .95 * grip, .85 * grip)
                val total = lengths.sum()
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .55, 1.08, 1.02)[tick]
                    tick < p.durationTicks - 9 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 9)) / 8.0
                }.coerceIn(0.0, 1.1)
                // Forward kinematics from the knuckle on the ground.
                var at = p.offset.add(0.0, -total * (1 - up.coerceAtMost(1.0)), 0.0)
                var pitch = 0.0
                for (s2 in 0 until seg) {
                    pitch += bends[s2]
                    at = at.add(sin(pitch) * sin(p.yaw) * lengths[s2], cos(pitch) * lengths[s2], sin(pitch) * cos(p.yaw) * lengths[s2])
                }
                pitch += bends[seg]
                CoreMeshPose(at, p.scale, p.yaw, pitch, 0.0, "$DIR/finger_$seg", live)
            }
            name == "gcrack" -> {
                val stage = when { tick < 6 -> 0; tick < 16 -> 1; tick < 28 -> 2; else -> 3 }
                CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/gcrack_$stage", live)
            }
            name.startsWith("lump") -> {
                // Heaved up from below with an overshoot, settles, then sinks back at the end.
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .7, 1.15, 1.05)[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 6.0
                }.coerceIn(0.0, 1.2)
                val k = name.removePrefix("lump").toInt()
                CoreMeshPose(p.offset.add(0.0, -p.scale.y() * .6 * (1 - up.coerceAtMost(1.0)), 0.0), p.scale, p.yaw,
                    p.pitch * up, p.roll * up, "$DIR/lump_${k % 3}", live)
            }
            name.startsWith("petal") -> {
                // Bursts up leaning inward (closed bud), opens outward past its rest with overshoot, holds, closes and sinks.
                val open = when {
                    tick < 2 -> -.5
                    tick < 5 -> doubleArrayOf(-.1, .75, .62)[tick - 2]
                    tick < p.durationTicks - 8 -> .62 + .04 * sin(tick * .7)
                    else -> .62 - (tick - (p.durationTicks - 8)) / 7.0 * 1.0
                }
                val rise = when { tick < 2 -> doubleArrayOf(.3, .8)[tick]; tick < p.durationTicks - 5 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 5)) / 4.0 }.coerceIn(0.0, 1.0)
                val k = name.removePrefix("petal").toInt()
                CoreMeshPose(p.offset.add(0.0, -p.scale.y() * 2.0 * (1 - rise), 0.0), p.scale, p.yaw, -open, 0.0, "$DIR/petal_${k % 3}", live)
            }
            name.startsWith("gslab") -> {
                val height = p.scale.y() * 12.0 / 16.0
                val up = when {
                    tick < 3 -> doubleArrayOf(.0, .8, 1.12)[tick]
                    tick < p.durationTicks - 8 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 8)) / 7.0
                }.coerceIn(0.0, 1.2)
                val k = name.removePrefix("gslab").toInt()
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch * up.coerceAtMost(1.0), 0.0, "$DIR/gslab_${k % 3}", live)
            }
            name.startsWith("chunk") -> {
                val k = name.removePrefix("chunk").toInt()
                val s = t.coerceAtLeast(0.0) / 20.0
                val a = p.yaw + (hash(k, 33) - .5) * 2.4
                val y = (5.0 + 3.0 * hash(k, 34)) * s - 9.8 * s * s / 2
                CoreMeshPose(p.offset.add(sin(a) * 2.0 * s, max(y, -.3), cos(a) * 2.0 * s), p.scale, p.yaw + t * .4, t * .5, t * .3, "$DIR/chunk", live)
            }
            name.startsWith("shard") -> {
                val height = p.scale.y() * 18.0 / 16.0
                val up = when {
                    tick < 3 -> doubleArrayOf(.0, .8, 1.15)[tick]
                    tick < p.durationTicks - 8 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 8)) / 7.0
                }.coerceIn(0.0, 1.2)
                val model = when { tick < p.durationTicks - 9 -> 0; tick < p.durationTicks - 5 -> 1; else -> 2 }
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch, 0.0, "$DIR/shard_$model", live)
            }
            name.startsWith("streak") -> CoreMeshPose(p.offset, p.scale.mul(1.0 + tick * .15), 0.0, 0.0, p.roll, "$DIR/streak", live)
            name.startsWith("slab") -> {
                // Thrown up out of the split (overshoot), held, then settling back down.
                val lift = when {
                    tick < 3 -> doubleArrayOf(.0, .75, 1.1)[tick]
                    tick < p.durationTicks - 6 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 6)) / 5.0
                }.coerceIn(0.0, 1.2)
                val heat = when { tick < 8 -> 0; tick < 16 -> 1; else -> 2 }
                CoreMeshPose(p.offset.add(0.0, -.35 + .35 * lift, 0.0), p.scale, p.yaw, 0.0, p.roll * lift, "$DIR/slab_$heat", live)
            }
            name == "crack" -> {
                val stage = when { tick < 3 -> 0; tick < 9 -> 1; tick < 22 -> 2; tick < 28 -> 3; else -> 4 }
                CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0, "$DIR/crack_$stage", live)
            }
            name.startsWith("fang") -> {
                val height = p.scale.y() * 17.0 / 16.0
                val up = when {
                    tick < rise.size -> rise[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 6.0
                }.coerceIn(0.0, 1.2)
                val model = when { tick < p.durationTicks - 9 -> 0; tick < p.durationTicks - 5 -> 1; else -> 2 }
                // The fang stays the same size; it slides out of the ground and back.
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch, 0.0, "$DIR/fang_$model", live)
            }
            name.startsWith("ray") -> {
                val stage = intArrayOf(0, 0, 1, 1, 2, 3)[tick.coerceAtMost(5)]
                val grow = doubleArrayOf(.55, 1.0, 1.08, 1.05, .95, .8)[tick.coerceAtMost(5)]
                CoreMeshPose(p.offset, p.scale.mul(grow), 0.0, 0.0, p.roll, "$DIR/ray_$stage", live)
            }
            // 体当たり / 跳び叩き (2026-10-27).
            name == "skid" || name == "dent" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/${name}_${when { tick < p.durationTicks - 8 -> 0; tick < p.durationTicks - 4 -> 1; else -> 2 }}", live)
            name == "cleft" -> CoreMeshPose(p.offset, p.scale, p.yaw, 0.0, 0.0,
                "$DIR/cleft_${when { tick < 2 -> 0; tick < 8 -> 1; tick < p.durationTicks - 10 -> 2; tick < p.durationTicks - 5 -> 3; else -> 4 }}", live)
            name == "kick" -> {
                val u = (t.coerceAtLeast(0.0) / p.durationTicks).coerceAtMost(1.0)
                CoreMeshPose(p.offset.add(p.travel.mul(u)), p.scale.mul(1.0 + .3 * u), 0.0, 0.0, p.roll,
                    "$DIR/kick_${(tick * 5 / p.durationTicks).coerceAtMost(4)}", live)
            }
            name == "shock" -> CoreMeshPose(p.offset, p.scale.mul(doubleArrayOf(1.0, 1.06, 1.1, 1.13, 1.15)[min(tick / 2, 4)]), 0.0, 0.0, p.roll,
                "$DIR/shock_${intArrayOf(0, 0, 1, 2, 2, 3, 3, 4, 4)[tick.coerceAtMost(8)]}", live)
            name == "clang" -> CoreMeshPose(p.offset, p.scale.mul(doubleArrayOf(1.0, 1.1, 1.05, 1.0, .95, .9)[tick.coerceAtMost(5)]), 0.0, 0.0, 0.0,
                "$DIR/clang_${intArrayOf(0, 0, 1, 1, 2, 2)[tick.coerceAtMost(5)]}", live)
            name == "fspark" -> {
                val u = (t.coerceAtLeast(0.0) / p.durationTicks).coerceAtMost(1.0)
                CoreMeshPose(p.offset.add(p.travel.mul(u)).add(0.0, -.5 * u * u, 0.0), p.scale.mul(1.0 - .3 * u), 0.0, 0.0, p.roll,
                    "$DIR/fspark_${if (u < .5) 0 else 1}", live)
            }
            name.startsWith("rslab") -> {
                // Thrown under gravity; it comes down, lies where it fell, and breaks up.
                val k = name.removePrefix("rslab").toInt()
                val g = 16.0
                val vy = p.travel.y()
                val floor = -p.offset.y() + .06
                val land = (vy + sqrt(vy * vy - 2 * g * floor)) / g        // seconds until it is back on the ground
                val s = min(t.coerceAtLeast(0.0) / 20.0, land)
                val y = vy * s - g * s * s / 2
                val spin = s * 20
                CoreMeshPose(p.offset.add(p.travel.x() * s, y, p.travel.z() * s), p.scale, p.yaw + spin * .25 * (hash(k, 631) - .5),
                    spin * (.35 + .25 * hash(k, 632)), spin * (.2 * (hash(k, 633) - .5)),
                    "$DIR/rslab_${when { tick < p.durationTicks - 7 -> 0; tick < p.durationTicks - 3 -> 1; else -> 2 }}", live)
            }
            name == "rplate" -> {
                val up = when {
                    tick < 4 -> doubleArrayOf(.0, .75, 1.12, 1.0)[tick]
                    tick < p.durationTicks - 7 -> 1.0
                    else -> 1.0 - (tick - (p.durationTicks - 7)) / 7.0 * .6
                }.coerceIn(0.0, 1.2)
                val height = p.scale.y() * 18.0 / 16.0
                CoreMeshPose(p.offset.add(0.0, -height * (1 - up), 0.0), p.scale, p.yaw, p.pitch * up.coerceAtMost(1.0), 0.0,
                    "$DIR/rplate_${when { tick < p.durationTicks - 7 -> 0; tick < p.durationTicks - 3 -> 1; else -> 2 }}", live)
            }
            name == "frame" -> CoreMeshPose(p.offset, p.scale, 0.0, 0.0, 0.0, "$DIR/frame", live)
            name == "ring" -> CoreMeshPose(p.offset, p.scale.mul(1.0 + tick * .06), 0.0, 0.0, 0.0, "$DIR/ring_${(tick / 2).coerceAtMost(3)}", live)
            name.startsWith("spark") -> CoreMeshPose(p.offset, p.scale.mul(if (tick % 2 == 0) 1.0 else .8), 0.0, 0.0, 0.0,
                "$DIR/spark_${(tick / 2).coerceAtMost(2)}", live)
            name.startsWith("twinkle") -> CoreMeshPose(p.offset, p.scale.mul(if (tick % 2 == 0) 1.0 else .8), 0.0, 0.0, 0.0,
                "$DIR/twinkle_${(tick / 2).coerceAtMost(2)}", live)
            name.startsWith("bar") -> {
                val k = name.removePrefix("bar").toInt()
                val a = p.yaw - PI / 2
                val s = t.coerceAtLeast(0.0) / 20.0
                val speed = 9.0 + 4.0 * hash(k, 15)
                val y = (3.0 + 3.0 * hash(k, 16)) * s - 9.8 * s * s / 2
                val at = p.offset.add(sin(a) * speed * s, y, cos(a) * speed * s)
                CoreMeshPose(at, p.scale, p.yaw, t * .12 * (hash(k, 17) - .5), t * .3 * (hash(k, 18) - .5), "$DIR/bar", live)
            }
            else -> null
        }
    }
}
