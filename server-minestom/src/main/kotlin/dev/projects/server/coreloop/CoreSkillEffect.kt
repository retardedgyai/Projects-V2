package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.*

/** Authored combat art, not hit logic. One PULSE is created by one server damage pulse. */
internal enum class CoreSkillMotif {
    SLASH, THRUST, CLEAVE, WHIRL, VENOM, FROST_FAN, ARROW, NEEDLE, FIRE, LIGHTNING,
    STAR_BEAM, RAIN, METEOR, FROST, STARS, TRAP, PULL, WARD, HEAL, PILLAR, BLINK, GUARD, SHOCK,
}

internal enum class CoreSkillVisualPhase { PREPARE, PULSE, CONTACT }

internal object CoreSkillArt {
    // Explicit assignments: adding a skill requires choosing its visual language, not hashing its name.
    val motifs = buildMap {
        fun put(kind: CoreSkillMotif, vararg ids: String) = ids.forEach { put(it, kind) }
        put(CoreSkillMotif.SLASH, "dash", "war_wound", "war_counter", "ass_execute")
        put(CoreSkillMotif.THRUST, "war_breach", "ass_stab", "ass_chase", "ass_contract", "temp_dash")
        put(CoreSkillMotif.CLEAVE, "slam", "war_ult", "temp_mace", "temp_break")
        put(CoreSkillMotif.WHIRL, "whirl", "ass_fan", "ass_ult")
        put(CoreSkillMotif.VENOM, "ass_poison")
        put(CoreSkillMotif.FROST_FAN, "frost_fan")
        put(CoreSkillMotif.ARROW, "pierce", "hunt_pierce", "hunt_mark", "hunt_volley", "hunt_ult", "hunt_retreat")
        put(CoreSkillMotif.NEEDLE, "ass_needle")
        put(CoreSkillMotif.FIRE, "firebolt")
        put(CoreSkillMotif.LIGHTNING, "mage_mark", "mage_burst")
        put(CoreSkillMotif.STAR_BEAM, "star_thread", "star_needle", "star_break", "heal_light", "heal_mark")
        put(CoreSkillMotif.RAIN, "arrow_rain", "hunt_storm")
        put(CoreSkillMotif.METEOR, "meteor", "mage_ult")
        put(CoreSkillMotif.FROST, "frost_nova", "mage_garden", "mage_zero")
        put(CoreSkillMotif.STARS, "star_ring", "starfall", "star_cloud", "star_ult")
        put(CoreSkillMotif.TRAP, "hunt_trap")
        put(CoreSkillMotif.PULL, "temp_pull", "temp_ult")
        put(CoreSkillMotif.WARD, "war_cry", "war_banner", "mage_ward", "temp_ward", "temp_sanctuary",
            "heal_shield", "star_shield", "star_constellation")
        put(CoreSkillMotif.HEAL, "heal_ring", "heal_wind", "heal_ult")
        put(CoreSkillMotif.PILLAR, "heal_pillar", "heal_lamp", "heal_judgment", "temp_field")
        put(CoreSkillMotif.BLINK, "ass_escape", "mage_blink", "heal_step", "star_step")
        put(CoreSkillMotif.GUARD, "war_guard", "ass_guard", "temp_guard")
        put(CoreSkillMotif.SHOCK, "temp_rebuke")
    }

    fun color(job: CoreClass, skill: CoreSkillDefinition): Int = when {
        skill.status == CoreSkillStatus.POISON -> 0x92db43
        skill.element == 1 -> 0xff9438
        skill.element == 2 -> 0x82daff
        skill.element == 3 -> 0xa693ff
        else -> when (job) {
            CoreClass.WARRIOR -> 0xf5b667
            CoreClass.ASSASSIN -> 0xc378ca
            CoreClass.RANGER -> 0xc8da96
            CoreClass.TEMPLAR -> 0xf1d57f
            CoreClass.HEALER -> 0x85edc5
            CoreClass.STARWEAVER -> 0xca58ff
            CoreClass.MAGE -> 0xabbbff
        }
    }
}

/** Bright hit silhouette, moving edge, then a sparse fading wake. No cosmetic extra impacts. */
internal class CoreSkillEffect(
    internal val job: CoreClass,
    internal val skill: CoreSkillDefinition,
    internal val origin: Point,
    internal val direction: Vec,
    internal val phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE,
    internal val pulse: Int = 0,
    prepareTicks: Int = 4,
    rayLength: Double = 0.0,
    internal val clippedRay: Boolean = false,
    internal val sceneId: String = skill.icon,
    internal val endpoint: CoreSkillEndpoint = CoreSkillEndpoint.NONE,
) : ParticleEffect {
    internal var solidCompanion: Boolean = false
    internal val valid = listOf(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(), skill.radius, rayLength).all(Double::isFinite)
    private val frame = ParticleTransform.fromDirection(if (valid) origin else Vec.ZERO, direction)
    internal val motif = CoreSkillArt.motifs.getValue(skill.icon)
    private val color = CoreSkillArt.color(job, skill)
    internal val radius = if (skill.radius.isFinite()) skill.radius.coerceIn(.3, 10.5) else .3
    internal val length = if (rayLength.isFinite()) rayLength.coerceIn(0.0, 24.0) else 0.0
    internal val prepareDuration = prepareTicks.coerceIn(1,60)
    override val durationTicks get() = CoreSkillChoreography.duration(this)

    override fun emit(tick: Int, sink: ParticleSink) {
        if (!valid || tick !in 0 until durationTicks) return
        if (solidCompanion) { CoreSceneParticles.emit(this,tick,sink); return }
        val t = tick.toDouble() / durationTicks
        val bright = 0xfff4d9
        fun p(x: Double, y: Double, z: Double, ink: Int = color, size: Double = 1.1, key: Boolean = false) {
            sink.spawn(ParticleSpawn(dustTransition(ink, color, (size * (1 - t * .6)).toFloat()),
                frame.localPoint(Vec(x, y, z)), category = ParticleCategory.OWN_ACTIVE,
                importance = if (key) ParticleImportance.COMBAT_FEEDBACK else ParticleImportance.COSMETIC))
        }
        fun line(a: Vec, b: Vec, steps: Int = 12, ink: Int = color, size: Double = 1.1, key: Boolean = false) {
            for (i in 0..steps) {
                val v = a.add(b.sub(a).mul(i.toDouble() / steps))
                p(v.x(), v.y(), v.z(), ink, size, key)
            }
        }
        fun ring(r: Double, y: Double = .13, count: Int = 24, tilt: Double = 0.0, key: Boolean = false) {
            repeat(count) { i -> val a = i * PI * 2 / count
                p(cos(a) * r, y + sin(a) * tilt, sin(a) * r, size = .8, key = key)
            }
        }
        if (phase == CoreSkillVisualPhase.CONTACT) {
            // Target-centred: only spawned after damage was actually accepted.
            repeat(5) { arm ->
                val a = arm * PI * 2 / 5 + pulse * .7
                line(Vec(cos(a) * .1, 1.0 + sin(a) * .1, 0.0),
                    Vec(cos(a) * (.45 + t * .25), 1.0 + sin(a) * (.45 + t * .25), 0.0),
                    3, if (tick == 0) bright else color, .8, tick == 0)
            }
            return
        }
        if (phase == CoreSkillVisualPhase.PREPARE) {
            val progress = (tick + 1.0) / durationTicks
            if (skill.motion == CoreSkillMotion.FIELD) {
                if (tick % 3 == 0) ring(radius, count = 20)
                if (motif in setOf(CoreSkillMotif.METEOR, CoreSkillMotif.RAIN, CoreSkillMotif.STARS, CoreSkillMotif.PILLAR)) {
                    repeat(3) { i ->
                        val a = i * PI * 2 / 3 + pulse * 1.4
                        val r = radius * .45
                        val y = .3 + (1 - progress) * 4.5
                        line(Vec(cos(a) * r, y, sin(a) * r), Vec(cos(a) * r, y + .8, sin(a) * r), 5, size = .8)
                    }
                } else ring(radius * (1 - progress * .5), count = 12)
            } else if (motif in setOf(CoreSkillMotif.SLASH, CoreSkillMotif.CLEAVE, CoreSkillMotif.THRUST, CoreSkillMotif.WHIRL, CoreSkillMotif.VENOM)) {
                line(Vec(.6, 1.0, .1), Vec(.6 + (1 - progress) * .5, 2.6, .3), 10, size = .75)
            } else {
                repeat(12) { i -> val a = i * PI / 6 + progress * 2
                    p(cos(a) * (.55 - progress * .3), 1.0 + sin(a) * (.55 - progress * .3), .7, size = .7)
                }
            }
            return
        }

        val reverse = if ((pulse + if (skill.icon == "war_counter") 1 else 0) % 2 == 0) 1 else -1
        val tail = tick >= 3
        val samples = if (tail) 14 else 28
        // Material accents are brief and secondary: embers, ice glints, electricity, metal sparks.
        // The authored silhouette still carries the hit timing and reach.
        if (tick == 0) {
            val material = when (motif) {
                CoreSkillMotif.FIRE, CoreSkillMotif.METEOR -> Particle.SMALL_FLAME
                CoreSkillMotif.FROST, CoreSkillMotif.FROST_FAN -> Particle.SNOWFLAKE
                CoreSkillMotif.LIGHTNING -> Particle.ELECTRIC_SPARK
                CoreSkillMotif.STARS, CoreSkillMotif.STAR_BEAM, CoreSkillMotif.HEAL -> Particle.END_ROD
                CoreSkillMotif.CLEAVE, CoreSkillMotif.THRUST -> Particle.CRIT
                else -> null
            }
            if (material != null) sink.spawn(ParticleSpawn(material,
                frame.localPoint(if (length > 0) Vec(0.0, 0.0, length) else Vec(0.0, .7, 0.0)),
                5, Vec(.2, .25, .2), .015f, ParticleCategory.OWN_ACTIVE, importance = ParticleImportance.COSMETIC))
        }
        when (motif) {
            CoreSkillMotif.SLASH, CoreSkillMotif.WHIRL, CoreSkillMotif.VENOM -> {
                val full = motif == CoreSkillMotif.WHIRL
                val extent = if (full) PI else acos(.35)
                // Whole reach reads on the hit frame; the luminous cutting edge then crosses it.
                for (i in 0..samples) {
                    val u = i.toDouble() / samples
                    val a = -extent + u * extent * 2 + if (full) pulse * .9 else 0.0
                    val y = .9 + sin(a) * (if (pulse % 3 == 2) -.7 else .55)
                    val edge = tick == 0 || abs(u - tick / 3.0) < .22
                    p(sin(a) * radius * reverse, y, cos(a) * radius,
                        if (edge && !tail) bright else color, if (edge) 1.6 else .7, tick == 0)
                    if (!tail) p(sin(a) * (radius - .18) * reverse, y - .13, cos(a) * (radius - .18), size = 1.3)
                }
                if (motif == CoreSkillMotif.VENOM) repeat(8) { i ->
                    val a = -.9 + i * 1.8 / 7
                    p(sin(a) * radius * .8, .65 + t, cos(a) * radius * .8, 0x80b72f, .9)
                }
            }
            CoreSkillMotif.CLEAVE, CoreSkillMotif.THRUST -> {
                if (motif == CoreSkillMotif.CLEAVE) {
                    line(Vec(.15 * reverse, 3.1, radius * .4), Vec(0.0, .15, radius), 22,
                        if (tick == 0) bright else color, if (tail) .75 else 1.7, tick == 0)
                } else {
                    line(Vec(0.0, 1.0, .2), Vec(0.0, 1.0, radius), 20,
                        if (tick == 0) bright else color, if (tail) .65 else 1.5, tick == 0)
                    if (!tail) for (side in listOf(-1, 1)) line(Vec(side * .5, .8, radius * .7), Vec(0.0, 1.0, radius), 6)
                }
                // This hit is a cone in current combat, including the lateral edges, not only the blade.
                if (tick == 0 || tick == 2) for (i in 0..20) {
                    val a = -acos(.35) + 2 * acos(.35) * i / 20
                    p(sin(a) * radius, .12, cos(a) * radius, size = 1.0, key = true)
                }
                if (motif == CoreSkillMotif.CLEAVE && !tail) for (arm in -1..1) {
                    val a = arm * .85
                    line(Vec.ZERO.add(0.0, .12, 0.0), Vec(sin(a) * radius, .12, cos(a) * radius), 9, size = .9)
                }
            }
            CoreSkillMotif.ARROW, CoreSkillMotif.NEEDLE, CoreSkillMotif.FIRE, CoreSkillMotif.STAR_BEAM -> {
                if (length == 0.0) return
                val count = ceil(length * 2).toInt().coerceIn(2, 48)
                for (i in 0..count) {
                    val z = length * i / count
                    if (tail && i % 2 != 0) continue
                    p(0.0, 0.0, z, if (tick == 0) bright else color, if (skill.ultimate) 1.65 else .9, tick == 0)
                    if (!tail && motif in setOf(CoreSkillMotif.FIRE, CoreSkillMotif.STAR_BEAM)) {
                        val a = i * .7 + t * 4
                        p(cos(a) * .22, sin(a) * .22, z, size = 1.0)
                    }
                }
                if (motif == CoreSkillMotif.ARROW && !tail) for (side in listOf(-1, 1))
                    line(Vec(side * .35, 0.0, (length - .6).coerceAtLeast(0.0)), Vec(0.0, 0.0, length), 4, size = 1.0)
            }
            CoreSkillMotif.LIGHTNING -> {
                if (length > 0) for (arm in 0..1) for (i in 0..24) {
                    val u = i / 24.0
                    p(sin(i * 2.3 + pulse) * .28 * sin(u * PI), cos(i * 1.7) * .16 * arm, length * u,
                        if (arm == 0 && tick == 0) bright else color, if (tail) .55 else 1.1, tick == 0)
                } else repeat(6) { arm ->
                    val a = arm * PI / 3 + pulse * .2
                    for (i in 0..7) { val r = radius * i / 7
                        p(cos(a + sin(i * 2.4) * .08) * r, .2, sin(a + sin(i * 2.4) * .08) * r,
                            if (tick == 0) bright else color, key = tick == 0)
                    }
                }
            }
            CoreSkillMotif.FROST_FAN -> for (arm in -2..2) {
                val a = arm * acos(.35) / 2
                line(Vec(0.0, .8, .1), Vec(sin(a) * radius, .8, cos(a) * radius), 10,
                    if (tick == 0) bright else color, key = tick == 0)
            }
            CoreSkillMotif.RAIN, CoreSkillMotif.METEOR, CoreSkillMotif.PILLAR -> {
                if (tick == 0) ring(radius, key = true)
                val columns = if (motif == CoreSkillMotif.RAIN) 7 else 3
                repeat(columns) { i ->
                    val a = i * PI * 2 / columns + pulse * 1.4
                    val r = radius * if (motif == CoreSkillMotif.RAIN) .7 else .45
                    val x = cos(a) * r; val z = sin(a) * r
                    val height = if (motif == CoreSkillMotif.PILLAR) 4.0 * (1 - t) else 2.2 * (1 - t)
                    line(Vec(x, .15, z), Vec(x, height, z), if (tail) 3 else 9,
                        if (tick == 0) bright else color, if (motif == CoreSkillMotif.METEOR) 1.8 else .85, tick == 0)
                    if (motif == CoreSkillMotif.METEOR && !tail) repeat(6) { arm ->
                        val b = arm * PI / 3
                        p(x + cos(b) * (.4 + t), .15 + t * .4, z + sin(b) * (.4 + t), size = 1.3)
                    }
                }
            }
            CoreSkillMotif.FROST -> {
                if (tick == 0) ring(radius, key = true)
                repeat(6) { arm -> val a = arm * PI / 3 + pulse * PI / 12
                    val r = radius * (.55 + t * .4)
                    val x = cos(a) * r; val z = sin(a) * r
                    line(Vec(x, .1, z), Vec(x * .9, if (skill.ultimate) 2.1 else 1.35, z * .9), if (tail) 4 else 7,
                        if (tick == 0) bright else color, key = tick == 0)
                    if (!tail) line(Vec(x, .8, z), Vec(x + cos(a + 1) * .4, 1.15, z + sin(a + 1) * .4), 3)
                }
            }
            CoreSkillMotif.STARS -> {
                if (tick == 0) ring(radius, key = true)
                repeat(5) { i ->
                    val a = i * PI * 2 / 5 + pulse * .6
                    val b = a + PI * 4 / 5
                    line(Vec(cos(a) * radius * .65, .25, sin(a) * radius * .65),
                        Vec(cos(b) * radius * .65, .25, sin(b) * radius * .65), if (tail) 5 else 9, key = tick == 0)
                    if (!tail) line(Vec(cos(a) * radius * .65, .3, sin(a) * radius * .65),
                        Vec(cos(a) * radius * .65, 1.5 - t, sin(a) * radius * .65), 4, bright, .8)
                }
            }
            CoreSkillMotif.TRAP -> {
                ring(radius, key = tick == 0)
                repeat(8) { i -> val a = i * PI / 4 + pulse * .3
                    val r = radius * (.6 - t * .2)
                    p(cos(a) * r, .25 + t * .8, sin(a) * r, 0x6faa38, 1.5)
                    if (!tail) line(Vec(cos(a) * radius, .15, sin(a) * radius), Vec(cos(a) * r, .65, sin(a) * r), 4)
                }
            }
            CoreSkillMotif.PULL -> {
                if (tick == 0) ring(radius, key = true)
                repeat(4) { arm -> for (i in 0..12) {
                    val u = i / 12.0; val r = radius * (1 - u) * (1 - t * .7)
                    val a = arm * PI / 2 + u * 2.2 + pulse * .5
                    p(cos(a) * r, .2 + u * .7, sin(a) * r, if (i > 9) bright else color, 1.1, tick == 0)
                } }
            }
            CoreSkillMotif.WARD, CoreSkillMotif.GUARD -> {
                val r = if (motif == CoreSkillMotif.GUARD) 1.0 else min(radius, 2.0)
                if (tick == 0 && motif == CoreSkillMotif.WARD) ring(radius, key = true)
                if (skill.icon == "ass_guard") {
                    // A humanoid afterimage, not the tank's shield cage.
                    for (side in listOf(-1, 1)) {
                        line(Vec(side * .3, .1, .5), Vec(side * .22, 1.5, .5), 10, key = tick == 0)
                        line(Vec(side * .22, 1.5, .5), Vec(0.0, 1.9, .5), 5)
                    }
                } else repeat(4) { arm -> val a = arm * PI / 2 + PI / 4
                    val x = cos(a) * r; val z = sin(a) * r
                    line(Vec(x, .1, z), Vec(x, 1.9, z), 8, key = tick == 0)
                    line(Vec(x, 1.9, z), Vec(0.0, 2.5, 0.0), 6)
                }
            }
            CoreSkillMotif.HEAL -> {
                if (tick == 0) ring(radius, key = true)
                repeat(4) { arm -> for (i in 0..10) {
                    val u = i / 10.0; val a = arm * PI / 2 + u * .8 + t
                    val r = radius * (1 - u) * .8
                    p(cos(a) * r, .15 + u * (1 + t), sin(a) * r, if (i > 7) bright else color, key = tick == 0)
                } }
            }
            CoreSkillMotif.BLINK -> repeat(3) { echo ->
                val y = .3 + echo * .55
                line(Vec(-.5 * (1 - t), y, .2), Vec(.5 * (1 - t), y, .2), 9, key = tick == 0)
            }
            CoreSkillMotif.SHOCK -> {
                ring(radius, key = tick == 0)
                if (!tail) ring(radius * (1 - t * .5), .3, tilt = .25)
            }
        }
    }
}

/** A snapshot sampled from the real DOT target, not an independently living status animation. */
internal class CorePoisonEffect(origin: Point, private val age: Long, private val damageTick: Boolean) : ParticleEffect {
    private val frame = ParticleTransform.fromDirection(origin, Vec(0.0, 0.0, 1.0))
    override val durationTicks = 1
    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        repeat(if (damageTick) 14 else 8) { i ->
            val a = i * PI / 4 + age * .16
            val r = if (damageTick) .6 else .4
            sink.spawn(ParticleSpawn(dustTransition(if (damageTick) 0xc5f478 else 0x79b83b, 0x426b25, .9f),
                frame.localPoint(Vec(cos(a) * r, .3 + (i % 4) * .32, sin(a) * r)),
                category = ParticleCategory.OWN_ACTIVE, importance = ParticleImportance.COMBAT_FEEDBACK))
        }
    }
}
