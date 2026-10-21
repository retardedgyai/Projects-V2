package dev.projects.server.mob

import dev.projects.server.coreloop.CoreDamageType
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 黒翳竜のギミック（docs/development/shade-wyrm-boss.md）: 鱗粉の侵食 and 鱗粉だまり. Encounter-local, nothing saved.
 * - Erosion: dust hits add [HIT_GAIN], standing in a pool adds [POOL_GAIN_PER_SECOND]; at [FULL] it erupts (damage and a
 *   slow) and resets. Hitting the wyrm takes [HIT_RELIEF] off; after [DECAY_AFTER_MILLIS] without gain it decays.
 *   Shown as a second boss bar while above zero.
 * - Pools: the dust burst leaves 4 inside its circle, a thrown dust clot 1 where it lands, the breath 3 along its band (radius [POOL_RADIUS], [POOL_MILLIS],
 *   at most [MAX_POOLS]). The antennae pulse makes them all burst [BURST_DELAY_MILLIS] after its hit.
 */
internal class ShadeWyrmGimmicks(private val tier: Int, private val fx: ShadeWyrmShaderFx, private val random: Random = Random.Default,
                                  private val lights: ShadeWyrmLights? = null) {
    private class Erosion(var value: Double = 0.0, var lastGainAt: Long = 0L) {
        var reliefSince = 0L
        var relieved = 0.0
        val bar: BossBar = BossBar.bossBar(Component.text("侵食", NamedTextColor.LIGHT_PURPLE), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10)
        var shown = false
    }
    internal class Pool(val at: Pos, val until: Long, val tag: String)

    private val erosion = mutableMapOf<UUID, Erosion>()
    private val pools = mutableListOf<Pool>()
    private val viewers = mutableMapOf<UUID, Player>()
    private var burstAt = 0L
    private var serial = 0
    private val factor = Math.pow(1.65, (tier - 1).toDouble())

    val poolCount: Int get() = pools.size
    fun erosionOf(player: UUID): Double = erosion[player]?.value ?: 0.0

    /**
     * Off while the viewer's pixel HUD is on: erosion is then drawn inside the wyrm's own boss bar
     * (CoreBossBarHud), and a second vanilla bar would overlap that frame.
     */
    var vanillaBar = true
        set(value) {
            field = value
            if (!value) for ((id, e) in erosion) if (e.shown) { viewers[id]?.hideBossBar(e.bar); e.shown = false }
        }

    /** A wyrm ability hit [player]. */
    fun playerHit(player: Player, abilityId: String, now: Long) {
        if (abilityId in DUST) gain(player, if (abilityId in setOf("shade-channel", "shade-shell")) CAST_GAIN else HIT_GAIN, now)
    }

    /** A wyrm ability struck (whether or not anyone was in it). */
    fun abilityHit(frame: MobAbilityFrame, now: Long) {
        val shape = frame.ability.shape
        when (frame.ability.id) {
            "shade-dust" -> if (shape is MobAttackShape.Ring) repeat(4) {
                val a = random.nextDouble(0.0, 2 * Math.PI); val r = shape.radius * sqrt(random.nextDouble(.15, .8))
                addPool(frame.origin.add(cos(a) * r, 0.0, sin(a) * r), now)
            }
            "shade-breath" -> if (shape is MobAttackShape.Slam) {
                val f = frame.facing.withY(0.0).normalize()
                for (k in 0 until 3) addPool(frame.origin.add(f.mul(shape.length * (k + 1) / 4.0)), now)
            }
            "shade-orb" -> addPool(frame.origin, now)                      // the thrown clot lands as a pool
            "shade-shell" -> if (shape is MobAttackShape.Ring) repeat(6) {        // the burst shell scatters its dust
                val a = it * Math.PI / 3 + random.nextDouble(-.3, .3); val r = shape.radius * random.nextDouble(.35, .8)
                addPool(frame.origin.add(cos(a) * r, 0.0, sin(a) * r), now)
            }
            "shade-pulse" -> if (pools.isNotEmpty()) {
                burstAt = now + BURST_DELAY_MILLIS
                pools.forEach { fx.poolWarning(it.at, it.tag) }
            }
        }
    }

    /** The player hit the wyrm: attacking clears erosion. */
    fun playerStruck(player: Player) {
        val e = erosion[player.uuid] ?: return
        // At most [RELIEF_PER_SECOND] a second: fast multi-hit attacks no longer wipe it at once (review 2026-10-19).
        val now = System.currentTimeMillis()
        if (now - e.reliefSince >= 1000) { e.reliefSince = now; e.relieved = 0.0 }
        val take = minOf(HIT_RELIEF, RELIEF_PER_SECOND - e.relieved).coerceAtLeast(0.0)
        e.relieved += take
        e.value = (e.value - take).coerceAtLeast(0.0)
    }

    fun tick(now: Long, players: List<Player>, damage: (Player, Double, CoreDamageType) -> Unit) {
        pools.removeAll { pool -> (now >= pool.until).also { if (it) { fx.endTag(pool.tag); lights?.hold(pool.tag, null, 0) } } }
        if (burstAt != 0L && now >= burstAt) {
            burstAt = 0L
            for (pool in pools) {
                fx.poolBurst(pool.at, pool.tag)
                lights?.hold(pool.tag, null, 0); lights?.flash(pool.at, 14, 600)
                for (p in players) if (flat(p.position, pool.at) <= BURST_RADIUS) damage(p, BURST_DAMAGE * factor, CoreDamageType.MAGICAL)
            }
            pools.clear()
        }
        for (p in players) {
            viewers[p.uuid] = p
            if (pools.any { flat(p.position, it.at) <= POOL_RADIUS }) gain(p, POOL_GAIN_PER_SECOND / 20.0, now)
        }
        for ((id, e) in erosion) {
            val p = viewers[id] ?: continue
            if (e.value >= FULL) {
                e.value = 0.0
                damage(p, ERUPT_DAMAGE * factor, CoreDamageType.MAGICAL)
                p.addEffect(Potion(PotionEffect.SLOWNESS, ERUPT_SLOW_LEVEL, (ERUPT_SLOW_MILLIS / 50).toInt()))
                p.addEffect(Potion(PotionEffect.SLOWNESS, 6, (ERUPT_STUN_MILLIS / 50).toInt()))   // rooted for a moment first
                fx.erupt(p.position)
                lights?.flash(p.position, 12, 600)
            } else if (now - e.lastGainAt > DECAY_AFTER_MILLIS) {
                e.value = (e.value - DECAY_PER_SECOND / 20.0).coerceAtLeast(0.0)
            }
            e.bar.progress((e.value / FULL).toFloat().coerceIn(0f, 1f))
            val show = vanillaBar && e.value > 0.0
            if (show != e.shown) { if (show) p.showBossBar(e.bar) else p.hideBossBar(e.bar); e.shown = show }
        }
    }

    fun dispose() {
        for ((id, e) in erosion) if (e.shown) viewers[id]?.hideBossBar(e.bar)
        erosion.clear(); pools.forEach { fx.endTag(it.tag); lights?.hold(it.tag, null, 0) }; pools.clear()
    }

    private fun gain(player: Player, amount: Double, now: Long) {
        viewers[player.uuid] = player
        val e = erosion.getOrPut(player.uuid) { Erosion() }
        e.value = (e.value + amount).coerceAtMost(FULL)
        e.lastGainAt = now
    }

    private fun addPool(at: Pos, now: Long) {
        if (pools.size >= MAX_POOLS) pools.removeAt(0).let { fx.endTag(it.tag); lights?.hold(it.tag, null, 0) }
        val tag = "pool-${serial++}"
        pools += Pool(at, now + POOL_MILLIS, tag)
        fx.pool(at, tag, (POOL_MILLIS / 50).toInt())
        lights?.hold(tag, at, 10)
    }

    private fun flat(a: Pos, b: Pos): Double = sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.z() - b.z()) * (a.z() - b.z()))

    companion object {
        val DUST = setOf("shade-dust", "shade-pulse", "shade-breath", "shade-storm", "shade-orb", "shade-channel", "shade-shell")
        const val FULL = 100.0
        const val HIT_GAIN = 20.0
        /** The cast that was not interrupted soaks everyone in dust. */
        const val CAST_GAIN = 60.0
        const val POOL_GAIN_PER_SECOND = 8.0
        const val HIT_RELIEF = 2.0
        const val RELIEF_PER_SECOND = 10.0
        const val DECAY_AFTER_MILLIS = 5000L
        const val DECAY_PER_SECOND = 2.0
        const val ERUPT_DAMAGE = 40.0
        const val ERUPT_STUN_MILLIS = 1500L
        const val ERUPT_SLOW_LEVEL = 2           // Slowness III: about 45 % slower (the plan said 40 %)
        const val ERUPT_SLOW_MILLIS = 3000L
        const val POOL_RADIUS = 1.8
        const val POOL_MILLIS = 25_000L
        const val MAX_POOLS = 8
        const val BURST_DELAY_MILLIS = 800L
        const val BURST_RADIUS = 2.5
        const val BURST_DAMAGE = 15.0
    }
}
