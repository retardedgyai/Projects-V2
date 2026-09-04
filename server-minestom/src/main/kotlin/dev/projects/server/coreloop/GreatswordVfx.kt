package dev.projects.server.coreloop

import dev.projects.server.particle.*
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import kotlin.math.*
import java.util.UUID
import java.util.WeakHashMap

internal enum class GreatswordVisual { WINDUP, SWEEP, REVERSE, FINISHER, SLAM_BLADE, LUNGE, SLAM, WHIRL, HIT }

/** Authored consumer, not a second particle framework. All geometry is local and bounded. */
internal class GreatswordEffect(
    private val visual: GreatswordVisual,
    origin: Point,
    direction: Vec,
) : ParticleEffect {
    private val valid = listOf(origin.x(), origin.y(), origin.z()).all { it.isFinite() }
    private val transform = ParticleTransform.fromDirection(if (valid) origin else Vec.ZERO, direction)
    override val durationTicks: Int = when (visual) {
        GreatswordVisual.WINDUP -> 4
        GreatswordVisual.HIT -> 4
        GreatswordVisual.SLAM -> 10
        else -> 7
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (!valid || tick !in 0 until durationTicks) return
        fun point(local: Vec, color: Int = 0xffefd0, scale: Float = 1.2f, edge: Boolean = false) {
            sink.spawn(ParticleSpawn(dustTransition(color, if (edge) 0x514538 else 0xbc814a, scale), transform.localPoint(local),
                category = ParticleCategory.OWN_ACTIVE, importance = if (edge) ParticleImportance.COSMETIC else ParticleImportance.COMBAT_FEEDBACK))
        }
        fun burst(local: Vec, particle: Particle, count: Int, spread: Vec, speed: Float) {
            sink.spawn(ParticleSpawn(particle, transform.localPoint(local), count, spread, speed,
                ParticleCategory.OWN_ACTIVE, importance = ParticleImportance.COMBAT_FEEDBACK))
        }
        val decay = 1.0 - tick.toDouble() / durationTicks
        when (visual) {
            GreatswordVisual.WINDUP -> {
                repeat(9) { n -> point(Vec(.6, 1.1 + n * .12, -.1 - n * .08), 0xd8b57a, .55f) }
            }
            GreatswordVisual.SWEEP, GreatswordVisual.REVERSE, GreatswordVisual.FINISHER, GreatswordVisual.SLAM_BLADE -> {
                // A bright, thick blade, copper middle and shrinking outer wake. No sweep icons.
                val movingFrame = min(tick, 2)
                val start = if (tick <= 2) movingFrame * 8 else 0
                val end = if (tick <= 2) start + 8 else 24
                for (n in 0..24) {
                    if (tick > 3 && n % 2 != 0) continue
                    val t = n / 24.0
                    val angle = -1.12 + t * 2.24
                    for (layer in 0..2) {
                        if (layer > 0 && n !in start..end) continue
                        val radius = 3.95 + layer * .20
                        val local = when (visual) {
                            GreatswordVisual.REVERSE -> Vec(-sin(angle) * radius, .15 + t * 2.35, cos(angle) * radius)
                            GreatswordVisual.FINISHER, GreatswordVisual.SLAM_BLADE -> Vec((layer - 1) * .18, 3.7 - t * 3.45, 1.1 + sin(t * PI / 2) * 3.15)
                            else -> Vec(sin(angle) * radius, 1.05 + layer * .06, cos(angle) * radius)
                        }
                        point(local, if (layer == 0 && tick < 4) 0xfff6dd else 0xe5ad66,
                            (doubleArrayOf(2.4, 1.6, 1.0)[layer] * decay.coerceAtLeast(.25)).toFloat(), layer == 2 || tick > 3)
                    }
                }
                if (visual in listOf(GreatswordVisual.FINISHER, GreatswordVisual.SLAM_BLADE) && tick == 2) {
                    burst(Vec(0.0, .2, 3.7), Particle.CRIT, 14, Vec(.7, .15, .7), .12f)
                }
                if (visual == GreatswordVisual.FINISHER && tick != 3) {
                    // The vertical blade is the source, but its ground shock reaches the whole
                    // normal-attack cone. Show the exact 4.5m / dot .4 edges, including lateral hits.
                    // Frame 3 already contains the full bright blade; its ground cue persists
                    // client-side rather than spending another dense frame on the same outline.
                    val halfAngle = acos(.4)
                    val faded = tick > 3
                    for (n in 0..24) {
                        if (faded && n % 2 != 0) continue
                        val angle = -halfAngle + 2 * halfAngle * n / 24.0
                        point(Vec(sin(angle) * 4.5, .12, cos(angle) * 4.5), 0xffcd83,
                            (1.5 * decay.coerceAtLeast(.25)).toFloat(), faded)
                    }
                    for (ray in -1..1) for (n in 1..6) {
                        if (faded && n % 2 != 0) continue
                        val radius = n * .75
                        val angle = ray * halfAngle + if (ray == 0) sin(n * 1.9) * .035 else 0.0
                        point(Vec(sin(angle) * radius, .12, cos(angle) * radius), 0xe9a24e,
                            (1.15 * decay.coerceAtLeast(.25)).toFloat(), faded)
                    }
                }
            }
            GreatswordVisual.LUNGE -> {
                repeat(16) { n ->
                    val z = n / 15.0 * 4.4
                    val twist = n * .45 + tick * .6
                    point(Vec(cos(twist) * .4, 1.0 + sin(twist) * .3, z), 0xfff3cb, (1.2 * decay).toFloat())
                    if (tick < 3) point(Vec(-.45, .16, z), 0xcfa66a, .7f, true)
                }
            }
            GreatswordVisual.SLAM -> {
                // Three branching fissures fan through the same five-metre forward sector.
                repeat(3) { ray -> repeat(14) segment@{ n ->
                    val radius = (n + 1) / 14.0 * 5.0
                    if (radius > (tick + 1) * 1.5) return@segment
                    val angle = (ray - 1) * .64 + sin(n * 1.9) * .045
                    point(Vec(sin(angle) * radius, .12, cos(angle) * radius), if (tick < 3) 0xffdc94 else 0xb77539,
                        (1.5 * decay.coerceAtLeast(.25)).toFloat(), tick > 4)
                } }
                if (tick == 1) burst(Vec(0.0, .18, 2.5), Particle.CAMPFIRE_COSY_SMOKE, 8, Vec(1.0, .1, 1.0), .015f)
            }
            GreatswordVisual.WHIRL -> {
                repeat(36) { n ->
                    val angle = n / 36.0 * PI * 2 + tick * .35
                    val radius = 3.15 + sin(angle * 3 + tick) * .12
                    point(Vec(cos(angle) * radius, .65 + sin(angle * 2 + tick * .5) * .38, sin(angle) * radius),
                        if (n % 2 == 0) 0xffedc6 else 0xe1a45c, (1.4 * decay.coerceAtLeast(.25)).toFloat(), tick > 3)
                }
            }
            GreatswordVisual.HIT -> {
                // Short white-hot radial sparks, not the old single particle at a mob's feet.
                repeat(12) { ray -> repeat(3) { segment ->
                    val angle = ray / 12.0 * PI * 2
                    val r = .12 + (segment + tick * 1.5) * .10
                    point(Vec(cos(angle) * r, 1.1 + sin(angle) * r, -.08 + sin(ray * 3.0) * .16),
                        if (tick == 0) 0xffffff else 0xffc15e, (.95 * decay).toFloat())
                } }
                if (tick == 0) burst(Vec(0.0, 1.1, 0.0), Particle.CRIT, 10, Vec(.2, .3, .2), .15f)
            }
        }
    }
}

/** Player-owned lifetime, no world entities or global tasks; map exit cancels every animation. */
internal class GreatswordVfx(private val player: Player) {
    private val scheduler = ParticleAnimationScheduler()
    private val manager = ParticleManager(
        ParticleQuality(otherActiveMultiplier = .45, distanceFalloffStart = 12.0, distanceFalloffEnd = 32.0, skipBelowMultiplier = .08),
        ParticleBudget(MAX_PARTICLES_PER_VIEWER_TICK))
    private val frame = RecordingParticleSink()
    private val elementalFrame = mutableListOf<ParticleSpawn>()
    private var instance: Instance? = null
    private var contactHold = 0
    private var holdAfterFrame = 0
    internal val activeEffects: Int get() = scheduler.activeAnimationCount
    internal val retainsInstance: Boolean get() = instance != null
    internal fun holdContact(ticks: Int) { holdAfterFrame = maxOf(holdAfterFrame, ticks.coerceIn(0, 3)) }
    fun particles(particle: Particle, position: Point, count: Int, spread: Vec = Vec.ZERO, speed: Float = 0f) {
        if (elementalFrame.size < 48) elementalFrame += ParticleSpawn(particle, position, count.coerceIn(0, 16), spread, speed,
            ParticleCategory.OWN_ACTIVE, importance = ParticleImportance.COMBAT_FEEDBACK)
    }

    fun play(visual: GreatswordVisual, origin: Point, direction: Vec) {
        if (player.instance !== instance) cancel()
        instance = player.instance
        if (scheduler.activeAnimationCount >= MAX_EFFECTS) return
        scheduler.start(GreatswordEffect(visual, origin, direction), frame)
    }

    fun tick() {
        val currentInstance = instance ?: return
        if (player.instance !== currentInstance || player.isRemoved) { cancel(); return }
        val viewers = currentInstance.players.filter { it.position.distanceSquared(player.position) <= 40.0 * 40.0 }
        if (viewers.isEmpty()) { cancel(); return }
        // Draw impact first, then let the hot blade linger without emitting or advancing it.
        // Only this player's VFX clock pauses; the server, movement and other players never do.
        if (contactHold > 0) { contactHold--; return }
        frame.clear()
        scheduler.tick()
        frame.spawns += elementalFrame
        elementalFrame.clear()
        contactHold = holdAfterFrame
        holdAfterFrame = 0
        manager.beginTick()
        for (viewer in viewers) {
            val category = if (viewer === player) ParticleCategory.OWN_ACTIVE else ParticleCategory.OTHER_ACTIVE
            val delegate = PlayerParticleSink(viewer)
            val bounded = ParticleSink { spawn ->
                val accepted = synchronized(sceneBudgets) {
                    sceneBudgets.getOrPut(currentInstance) { GreatswordSceneBudget() }.accept(currentInstance.worldAge, viewer.uuid, spawn.count)
                }
                if (accepted > 0) delegate.spawn(spawn.copy(count = accepted))
            }
            manager.dispatchAll(ParticleViewer(viewer.position, viewer), frame.spawns.map { it.copy(category = category) }, bounded)
        }
    }

    fun startSound(step: Int) {
        sound(SoundEvent.ITEM_ARMOR_EQUIP_IRON, .30f, if (step == 3) .65f else .9f)
    }
    fun swingSound(step: Int) {
        sound(SoundEvent.ITEM_TRIDENT_THROW, .7f, floatArrayOf(.78f, .9f, .55f)[step - 1])
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, .55f, if (step == 3) .65f else .8f)
    }
    fun impactSound(heavy: Boolean) {
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_STRONG, .65f, if (heavy) .55f else .85f)
        sound(SoundEvent.ITEM_TRIDENT_HIT, .45f, if (heavy) .65f else 1.0f)
    }
    fun cancel() {
        scheduler.cancelAll(); frame.clear(); elementalFrame.clear(); manager.resetCounters()
        contactHold = 0; holdAfterFrame = 0; instance = null
    }
    private fun sound(event: SoundEvent, volume: Float, pitch: Float) = player.playSound(Sound.sound(event, Sound.Source.PLAYER, volume, pitch))

    companion object {
        const val MAX_EFFECTS = 12
        const val MAX_PARTICLES_PER_VIEWER_TICK = 180
        private val sceneBudgets = WeakHashMap<Instance, GreatswordSceneBudget>()
    }
}

/** A party's concurrent swords also share a cap; budget ownership cannot retain disposed instances. */
internal class GreatswordSceneBudget {
    private var tick = Long.MIN_VALUE
    private var total = 0
    private val viewers = mutableMapOf<UUID, Int>()
    fun accept(worldTick: Long, viewer: UUID, requested: Int): Int {
        if (tick != worldTick) { tick = worldTick; total = 0; viewers.clear() }
        val accepted = minOf(requested.coerceAtLeast(0), (240 - (viewers[viewer] ?: 0)).coerceAtLeast(0), (2400 - total).coerceAtLeast(0))
        viewers[viewer] = (viewers[viewer] ?: 0) + accepted
        total += accepted
        return accepted
    }
}
