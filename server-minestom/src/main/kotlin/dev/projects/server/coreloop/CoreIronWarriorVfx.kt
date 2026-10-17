package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.util.RGBLike
import net.minestom.server.color.AlphaColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import kotlin.math.*

/**
 * Iron-mass warrior (2026-10-17, Creator's concept A "鉄塊"): every warrior skill is drawn with vanilla particles
 * (rock shards, ground dust, embers, steel dust) and heavy vanilla sounds that everyone nearby hears.
 * Server timing is unchanged: PREPARE follows the windup, PULSE the damage pulse, CONTACT an accepted hit.
 */
internal object CoreIronWarriorVfx {
    val icons = setOf("dash", "slam", "whirl", "war_wound", "war_guard", "war_ult")
    fun handles(effect: CoreSkillEffect) = effect.job == CoreClass.WARRIOR && effect.skill.icon in icons &&
        effect.sceneId == effect.skill.icon

    const val STEEL_LIGHT = 0xdfe6ec
    const val STEEL_DARK = 0x4a4f57
    const val HEAT_CORE = 0xffd27a
    const val HEAT_EMBER = 0x7a1c08
    const val AMBER = 0xffc84a
    const val GUARD_LIGHT = 0xbfe8ff
    const val GUARD_DARK = 0x2f5f96
    val ROCK: Block = Block.COBBLED_DEEPSLATE

    data class Cue(val event: SoundEvent, val volume: Float, val pitch: Float)

    fun cues(effect: CoreSkillEffect): List<Cue> = when (effect.skill.icon) {
        "dash" -> when (effect.phase) {
            CoreSkillVisualPhase.PREPARE -> listOf(Cue(SoundEvent.ENTITY_IRON_GOLEM_STEP, 1f, .6f), Cue(SoundEvent.BLOCK_HEAVY_CORE_STEP, .9f, .7f))
            CoreSkillVisualPhase.PULSE -> listOf(Cue(SoundEvent.ITEM_MACE_SMASH_AIR, .9f, .7f), Cue(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, .9f, .6f))
            CoreSkillVisualPhase.CONTACT -> listOf(Cue(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, .8f, .8f))
        }
        "slam" -> when (effect.phase) {
            CoreSkillVisualPhase.PREPARE -> listOf(Cue(SoundEvent.ITEM_ARMOR_EQUIP_NETHERITE, 1f, .6f), Cue(SoundEvent.BLOCK_FURNACE_FIRE_CRACKLE, 1f, .7f))
            CoreSkillVisualPhase.PULSE -> listOf(Cue(SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, .75f), Cue(SoundEvent.BLOCK_DEEPSLATE_BREAK, 1f, .55f),
                Cue(SoundEvent.ENTITY_GENERIC_EXPLODE, .45f, 1.3f))
            CoreSkillVisualPhase.CONTACT -> listOf(Cue(SoundEvent.ENTITY_IRON_GOLEM_DAMAGE, .7f, .7f))
        }
        "whirl" -> when (effect.phase) {
            CoreSkillVisualPhase.PREPARE -> listOf(Cue(SoundEvent.ITEM_ARMOR_EQUIP_NETHERITE, .8f, .8f))
            CoreSkillVisualPhase.PULSE -> listOf(Cue(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 1f, .5f), Cue(SoundEvent.ENTITY_BREEZE_WIND_BURST, .8f, .6f),
                Cue(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, .7f, .6f))
            CoreSkillVisualPhase.CONTACT -> listOf(Cue(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, .9f, .7f))
        }
        "war_wound" -> when (effect.phase) {
            CoreSkillVisualPhase.PREPARE -> listOf(Cue(SoundEvent.BLOCK_GRINDSTONE_USE, .7f, .6f))
            CoreSkillVisualPhase.PULSE -> listOf(Cue(SoundEvent.BLOCK_POINTED_DRIPSTONE_LAND, 1f, .5f), Cue(SoundEvent.BLOCK_GRAVEL_BREAK, 1f, .6f),
                Cue(SoundEvent.ENTITY_WARDEN_STEP, .8f, .7f))
            CoreSkillVisualPhase.CONTACT -> listOf(Cue(SoundEvent.BLOCK_BASALT_BREAK, .9f, .8f))
        }
        "war_guard" -> when (effect.phase) {
            CoreSkillVisualPhase.PREPARE, CoreSkillVisualPhase.PULSE -> listOf(Cue(SoundEvent.ITEM_ARMOR_EQUIP_NETHERITE, 1f, .5f),
                Cue(SoundEvent.BLOCK_HEAVY_CORE_PLACE, 1f, .7f), Cue(SoundEvent.BLOCK_IRON_DOOR_CLOSE, .6f, .6f))
            CoreSkillVisualPhase.CONTACT -> listOf(Cue(SoundEvent.ITEM_SHIELD_BLOCK, 1f, .7f), Cue(SoundEvent.BLOCK_ANVIL_LAND, .45f, 1.4f))
        }
        "war_ult" -> when (effect.phase) {
            CoreSkillVisualPhase.PREPARE -> listOf(Cue(SoundEvent.BLOCK_BEACON_POWER_SELECT, .7f, .5f), Cue(SoundEvent.BLOCK_FIRE_AMBIENT, 1f, .6f))
            CoreSkillVisualPhase.PULSE -> if (effect.pulse % 3 == 2)
                listOf(Cue(SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, .5f), Cue(SoundEvent.ENTITY_GENERIC_EXPLODE, 1f, .6f),
                    Cue(SoundEvent.ENTITY_WARDEN_SONIC_BOOM, .45f, .6f), Cue(SoundEvent.BLOCK_DEEPSLATE_BREAK, 1f, .5f))
                else listOf(Cue(SoundEvent.ITEM_MACE_SMASH_GROUND, 1f, .7f), Cue(SoundEvent.ENTITY_BLAZE_SHOOT, .6f, .6f))
            CoreSkillVisualPhase.CONTACT -> listOf(Cue(SoundEvent.ENTITY_IRON_GOLEM_DAMAGE, .8f, .6f))
        }
        else -> emptyList()
    }

    /** Positional, so allies and the target's side hear the weight too. */
    fun play(player: Player, effect: CoreSkillEffect) {
        val detail = when (CoreCombatPresentation.detail(player)) {
            CoreCombatPresentation.Detail.FULL -> 1f
            CoreCombatPresentation.Detail.SUBDUED -> .65f
            CoreCombatPresentation.Detail.MINIMAL -> .4f
        }
        val at = effect.origin
        val instance = player.instance ?: return
        val hearers = instance.players.filter { it.position.distanceSquared(at) <= 32.0 * 32.0 }
        for (cue in cues(effect)) {
            val sound = Sound.sound(cue.event, Sound.Source.PLAYER, cue.volume * detail, cue.pitch)
            hearers.forEach { it.playSound(sound, at.x(), at.y(), at.z()) }
        }
    }

    fun effect(source: CoreSkillEffect, ground: Block): ParticleEffect = IronEffect(source, if (ground.isSolid) ground else ROCK)

    /** Rampage: the blade glows and steams. [tick] is the combat tick; called every tick while raging. */
    fun rampage(origin: Point, facing: Vec, tick: Long, entering: Boolean, sink: (Particle, Point, Int, Vec, Float) -> Unit) {
        val frame = ParticleTransform.fromDirection(origin, facing)
        if (entering) {
            sink(Particle.FLASH.withColor(AlphaColor(255, 0xff, 0x8a, 0x2a)), origin.add(0.0, 1.0, 0.0), 1, Vec.ZERO, 0f)
            sink(Particle.LAVA, origin.add(0.0, .3, 0.0), 10, Vec(.6, .1, .6), 0f)
            for (i in 0 until 16) {
                val a = i * PI * 2 / 16
                sink(Particle.FLAME, origin.add(cos(a) * 1.2, .1, sin(a) * 1.2), 1, Vec.ZERO, 0f)
            }
            return
        }
        if (tick % 3 == 0L) sink(Particle.SMALL_FLAME, frame.localPoint(Vec(.45, 1.0, .35)), 2, Vec(.08, .35, .08), .01f)
        if (tick % 6 == 0L) sink(Particle.WHITE_SMOKE, frame.localPoint(Vec(.4, 1.6, .3)), 1, Vec(.05, .05, .05), .02f)
        if (tick % 9 == 0L) sink(Particle.LAVA, origin.add(0.0, .4, 0.0), 1, Vec(.25, .1, .25), 0f)
    }

    private class IronEffect(val e: CoreSkillEffect, val ground: Block) : ParticleEffect {
        private val frame = ParticleTransform.fromDirection(e.origin, e.direction)
        private val r = e.radius
        override val durationTicks: Int = when (e.phase) {
            CoreSkillVisualPhase.PREPARE -> e.prepareDuration
            CoreSkillVisualPhase.CONTACT -> 4
            CoreSkillVisualPhase.PULSE -> when (e.skill.icon) {
                "slam" -> 14; "war_wound" -> 14; "war_guard" -> 40; "war_ult" -> if (e.pulse % 3 == 2) 16 else 8; else -> 9
            }
        }

        private fun put(sink: ParticleSink, particle: Particle, local: Vec, count: Int = 1, spread: Vec = Vec.ZERO, speed: Float = 0f, key: Boolean = false) =
            sink.spawn(ParticleSpawn(particle, frame.localPoint(local), count, spread, speed, ParticleCategory.OWN_ACTIVE,
                importance = if (key) ParticleImportance.COMBAT_FEEDBACK else ParticleImportance.COSMETIC))
        private fun world(sink: ParticleSink, particle: Particle, at: Point, count: Int = 1, spread: Vec = Vec.ZERO, speed: Float = 0f, key: Boolean = false) =
            sink.spawn(ParticleSpawn(particle, at, count, spread, speed, ParticleCategory.OWN_ACTIVE,
                importance = if (key) ParticleImportance.COMBAT_FEEDBACK else ParticleImportance.COSMETIC))
        private fun steel(size: Float) = dustTransition(STEEL_LIGHT, STEEL_DARK, size)
        private fun heat(size: Float) = dustTransition(HEAT_CORE, HEAT_EMBER, size)
        private val rock get() = Particle.BLOCK.withBlock(ROCK)
        private val groundDust get() = Particle.DUST_PILLAR.withBlock(ground)
        private val crumble get() = Particle.BLOCK_CRUMBLE.withBlock(ground)
        /** A jagged ground line: deterministic per pulse so every viewer sees the same crack. */
        private fun jag(i: Int, seed: Int) = ((sin(i * 2.7 + seed * 1.3) + sin(i * 5.1 + seed)) * .18)

        override fun emit(tick: Int, sink: ParticleSink) {
            if (!e.valid || tick !in 0 until durationTicks) return
            when (e.phase) {
                CoreSkillVisualPhase.CONTACT -> contact(tick, sink)
                CoreSkillVisualPhase.PREPARE -> prepare(tick, sink)
                CoreSkillVisualPhase.PULSE -> when (e.skill.icon) {
                    "dash" -> dash(tick, sink); "slam" -> slam(tick, sink); "whirl" -> sweep(tick, sink)
                    "war_wound" -> crackRun(tick, sink); "war_guard" -> guard(tick, sink); "war_ult" -> hammer(tick, sink)
                }
            }
        }

        private fun contact(tick: Int, sink: ParticleSink) {
            val at = e.origin.add(0.0, 1.0, 0.0)
            when (e.skill.icon) {
                "war_guard" -> if (tick == 0) {
                    world(sink, Particle.ELECTRIC_SPARK, at, 12, Vec(.3, .3, .3), .3f, key = true)
                    world(sink, Particle.CRIT, at, 8, Vec(.2, .2, .2), .4f)
                }
                else -> {
                    if (tick == 0) {
                        world(sink, Particle.CRIT, at, 8, Vec(.25, .3, .25), .35f, key = true)
                        world(sink, rock, at, 6, Vec(.2, .2, .2), .15f)
                        if (e.skill.icon == "slam" || e.skill.icon == "war_ult") world(sink, Particle.LAVA, at, 2, Vec(.2, .1, .2))
                    }
                    if (tick == 1) world(sink, Particle.SMOKE, at, 3, Vec(.2, .2, .2), .02f)
                }
            }
        }

        private fun prepare(tick: Int, sink: ParticleSink) {
            val p = (tick + 1.0) / durationTicks
            when (e.skill.icon) {
                "dash" -> if (tick % 2 == 0) put(sink, groundDust, Vec(0.0, .05, -.4), 3, Vec(.3, 0.0, .3), key = tick == 0)
                "slam" -> {
                    // Heat gathers in the raised blade; the ground ahead starts to show the line it will split.
                    put(sink, Particle.SMALL_FLAME, Vec(.4, 1.6 + p, .2), 2, Vec(.1, .2, .1), .01f)
                    val reach = 1.0 + (r - 1.0) * p
                    put(sink, dust(HEAT_EMBER, .7f), Vec(jag(tick, e.pulse), .08, reach), key = true)
                    if (tick % 2 == 0) put(sink, crumble, Vec(0.0, .05, .6), 2, Vec(.4, 0.0, .4))
                }
                "whirl" -> put(sink, steel(.9f), Vec(-1.2 * p, 1.0, .4), key = tick == 0)
                "war_wound" -> put(sink, Particle.SMALL_FLAME, Vec(.3, .2, .6), 1, Vec(.1, .05, .1), key = tick == 0)
                "war_guard" -> put(sink, groundDust, Vec(0.0, .05, 0.0), 4, Vec(.5, 0.0, .5), key = tick == 0)
                "war_ult" -> {
                    // A long windup: embers spiral up the body, the air above glows, then one white flash.
                    val a = tick * .9
                    put(sink, Particle.FLAME, Vec(cos(a) * .9, .2 + p * 2.2, sin(a) * .9))
                    put(sink, Particle.SMALL_FLAME, Vec(-cos(a) * .9, .2 + p * 2.2, -sin(a) * .9))
                    if (tick % 3 == 0) put(sink, Particle.LAVA, Vec(0.0, 1.0, 0.0), 1, Vec(.5, .3, .5))
                    if (tick == durationTicks - 1) put(sink, Particle.FLASH.withColor(AlphaColor(255, 0xff, 0xb0, 0x50)), Vec(0.0, 2.6, .5), key = true)
                }
            }
        }

        private fun dash(tick: Int, sink: ParticleSink) {
            if (tick == 0) {
                // The thrust: a thick steel wedge through the swing, sparks at the point, ground dust behind.
                for (i in 0..14) {
                    val z = i * r / 14
                    put(sink, steel(1.7f - i * .05f), Vec(0.0, 1.05, z), key = i % 4 == 0)
                    if (i % 3 == 0) put(sink, steel(1.0f), Vec(0.0, .9, z))
                }
                put(sink, Particle.CRIT, Vec(0.0, 1.0, r), 10, Vec(.2, .2, .2), .4f, key = true)
                put(sink, Particle.WAX_OFF, Vec(0.0, 1.0, r), 6, Vec(.3, .3, .3), .5f)
            }
            if (tick < 6) {
                put(sink, groundDust, Vec(0.0, .05, -.5 - tick * .45), 4, Vec(.25, 0.0, .25))
                put(sink, Particle.POOF, Vec(0.0, .25, -.4 - tick * .4), 1, Vec(.1, .05, .1), .02f)
            }
        }

        private fun slam(tick: Int, sink: ParticleSink) {
            val impact = Vec(0.0, .05, 1.6)
            if (tick == 0) {
                put(sink, Particle.EXPLOSION, impact.add(0.0, .4, 0.0), key = true)
                put(sink, groundDust, impact, 40, Vec(.8, 0.0, .8), key = true)
                put(sink, rock, impact.add(0.0, .3, 0.0), 26, Vec(.5, .3, .5), .25f)
                put(sink, Particle.LAVA, impact.add(0.0, .2, 0.0), 5, Vec(.4, .1, .4))
                put(sink, Particle.FLASH.withColor(AlphaColor(220, 0xff, 0xa0, 0x40)), impact.add(0.0, .6, 0.0))
            }
            // Three cracks run out from the impact over five ticks, glowing hot and cooling to embers.
            val run = min(1.0, (tick + 1) / 5.0)
            for (branch in -1..1) {
                val steps = (10 * run).toInt()
                for (i in 0..steps) {
                    val d = i / 10.0
                    val z = 1.6 + d * (r - 1.6)
                    val x = branch * d * 1.4 + jag(i, branch + 2)
                    val hot = tick < 6
                    if (i >= steps - 2 || tick % 3 == 0)
                        put(sink, if (hot) heat(1.3f - d.toFloat() * .5f) else dust(HEAT_EMBER, .8f), Vec(x, .06, z), key = i == steps)
                    if (i == steps && tick < 5) put(sink, crumble, Vec(x, .05, z), 3, Vec(.2, 0.0, .2))
                }
            }
            if (tick in 4..13 && tick % 2 == 0) put(sink, Particle.LARGE_SMOKE, impact.add(0.0, .3, 0.0), 1, Vec(.4, .1, .4), .01f)
            if (tick in 6..13) put(sink, Particle.SMALL_FLAME, Vec(jag(tick, 7) * 2, .1, 1.6 + (tick % 5) * (r - 1.6) / 5), 1)
        }

        private fun sweep(tick: Int, sink: ParticleSink) {
            // A heavy 180 degree arc sweeps left to right in four ticks, then a wall of dust is shoved outward.
            if (tick < 4) {
                val from = 180.0 - tick * 45.0
                for (k in 0..6) {
                    val a = Math.toRadians(from - k * 45.0 / 6)
                    val x = cos(a) * r * .9; val z = sin(a) * r * .9
                    put(sink, steel(1.6f), Vec(x, 1.0, z), key = k == 6)
                    put(sink, steel(1.0f), Vec(x * .8, 1.0, z * .8))
                }
                val lead = Math.toRadians(from - 45.0)
                put(sink, Particle.SWEEP_ATTACK, Vec(cos(lead) * r * .7, 1.0, sin(lead) * r * .7), key = true)
            }
            if (tick in 2..8) {
                val out = r * (.85 + (tick - 2) * .06)
                for (k in 0..8) {
                    val a = Math.toRadians(k * 180.0 / 8)
                    put(sink, groundDust, Vec(cos(a) * out, .05, sin(a) * out), 2, Vec(.15, 0.0, .15))
                    if (tick % 2 == 0) put(sink, Particle.SMALL_GUST, Vec(cos(a) * out, .6, sin(a) * out))
                }
            }
        }

        private fun crackRun(tick: Int, sink: ParticleSink) {
            // An amber crack races 7 m along the ground; its head throws up rock, its trail keeps a few embers.
            val head = min(r, (tick + 1) * r / 6)
            if (tick < 6) {
                val from = max(0.5, head - r / 6)
                var z = from
                while (z <= head) {
                    val x = jag((z * 4).toInt(), 3)
                    put(sink, dustTransition(AMBER, HEAT_EMBER, 1.4f), Vec(x, .06, z), key = z + .25 > head)
                    z += .25
                }
                put(sink, groundDust, Vec(jag((head * 4).toInt(), 3), .05, head), 14, Vec(.3, 0.0, .3), key = true)
                put(sink, rock, Vec(0.0, .3, head), 6, Vec(.2, .2, .2), .2f)
                if (tick % 2 == 0) put(sink, Particle.LAVA, Vec(0.0, .1, head))
            } else if (tick % 2 == 0) {
                val z = .5 + ((tick * 37) % 13) / 13.0 * (r - .5)
                put(sink, Particle.SMALL_FLAME, Vec(jag((z * 4).toInt(), 3), .08, z))
                put(sink, dust(HEAT_EMBER, .9f), Vec(jag((z * 4).toInt(), 3), .06, z))
            }
        }

        private fun guard(tick: Int, sink: ParticleSink) {
            if (tick == 0) {
                put(sink, groundDust, Vec(0.0, .05, 0.0), 30, Vec(.7, 0.0, .7), key = true)
                put(sink, rock, Vec(0.0, .2, .3), 10, Vec(.4, .1, .4), .1f)
            }
            // The planted blade raises a curved steel-blue wall a step ahead, re-drawn every four ticks.
            if (tick % 4 == 0) for (k in -4..4) for (h in 0..3) {
                val a = Math.toRadians(k * 14.0)
                val edge = h == 3 || abs(k) == 4
                put(sink, dustTransition(if (edge) 0xffffff else GUARD_LIGHT, GUARD_DARK, if (edge) 1.0f else .7f),
                    Vec(sin(a) * 1.3, .2 + h * .55, cos(a) * 1.3), key = k == 0 && h == 3)
            }
            if (tick % 8 == 0) put(sink, crumble, Vec(0.0, .05, 0.0), 4, Vec(.5, 0.0, .5))
        }

        private fun hammer(tick: Int, sink: ParticleSink) {
            val last = e.pulse % 3 == 2
            if (!last) {
                // Two red-hot overhead cuts: a vertical arc in front, sparks where it meets the ground.
                if (tick < 3) for (k in 0..10) {
                    val a = Math.toRadians(100.0 - (tick * 3 + k * 3) * 6.0)
                    put(sink, heat(1.6f), Vec((if (e.pulse % 3 == 0) -.3 else .3), .2 + sin(a) * 2.4, .6 + cos(a) * 2.4 + 1.2), key = k == 10)
                }
                if (tick == 2) {
                    put(sink, Particle.LAVA, Vec(0.0, .1, 2.6), 4, Vec(.4, .1, .4))
                    put(sink, groundDust, Vec(0.0, .05, 2.6), 20, Vec(.5, 0.0, .5))
                    put(sink, Particle.WAX_OFF, Vec(0.0, .4, 2.6), 10, Vec(.4, .3, .4), .4f)
                }
                return
            }
            val impact = Vec(0.0, .05, 2.2)
            if (tick == 0) {
                put(sink, Particle.EXPLOSION_EMITTER, impact.add(0.0, .5, 0.0), key = true)
                put(sink, Particle.FLASH.withColor(AlphaColor(255, 0xff, 0x60, 0x20)), impact.add(0.0, 1.0, 0.0))
                put(sink, rock, impact.add(0.0, .4, 0.0), 40, Vec(.8, .4, .8), .35f)
                put(sink, Particle.LAVA, impact, 12, Vec(1.0, .2, 1.0))
            }
            // The shockwave rolls out across the whole reach as a ring of hot dust and thrown-up ground.
            if (tick < 8) {
                val ring = .6 + tick * (r - .6) / 7
                val count = (10 + ring * 5).toInt()
                for (k in 0 until count) {
                    val a = k * PI * 2 / count
                    val local = impact.add(cos(a) * ring, 0.0, sin(a) * ring)
                    put(sink, heat(1.5f - tick * .1f), local.add(0.0, .1, 0.0), key = k == 0)
                    if (k % 3 == tick % 3) put(sink, groundDust, local, 3, Vec(.2, 0.0, .2))
                }
            }
            if (tick in 3..15 && tick % 2 == 1) put(sink, Particle.LARGE_SMOKE, impact.add(0.0, .5, 0.0), 2, Vec(.8, .3, .8), .02f)
        }
    }
}
