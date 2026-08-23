package dev.projects.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class StarweaverCelestial(val symbol: String) {
    SUN("☀"),
    MOON("☾"),
    STAR("★"),
}

enum class StarweaverSlot {
    Q,
    W,
    E,
}

enum class StarweaverCastKind {
    BASE,
    CONJUNCTION,
}

data class StarweaverCast(
    val castId: Long,
    val slot: StarweaverSlot,
    val celestial: StarweaverCelestial,
    val kind: StarweaverCastKind,
)

data class StarweaverRotationSnapshot(
    val queue: List<StarweaverCelestial>,
    val stored: StarweaverCelestial,
    val cooldowns: Map<StarweaverSlot, Int>,
    val reloadTicksRemaining: Int,
    val conjunctionUsed: Boolean,
) {
    val current: StarweaverCelestial?
        get() = queue.firstOrNull()

    val conjunctionSlot: StarweaverSlot?
        get() {
            if (conjunctionUsed || queue.size < 2 || queue[0] != queue[1]) return null
            return when (queue[0]) {
                StarweaverCelestial.SUN -> StarweaverSlot.Q
                StarweaverCelestial.MOON -> StarweaverSlot.W
                StarweaverCelestial.STAR -> StarweaverSlot.E
            }
        }
}

/**
 * The complete six-mark rotation for the Starweaver prototype.
 *
 * This intentionally owns only Starweaver's small state machine. It is not a
 * class framework or a reusable resource abstraction.
 */
class StarweaverRotationState(
    private val random: Random = Random.Default,
    private val castIdSource: () -> Long = StarweaverExecutionIds::next,
) {
    private val queue = ArrayDeque<StarweaverCelestial>()
    private var stored = StarweaverCelestial.SUN
    private var reloadTicksRemainingValue = 0
    private var conjunctionUsedValue = false
    private val cooldowns = StarweaverSlot.entries.associateWithTo(mutableMapOf()) { 0 }

    init {
        reset()
    }

    val current: StarweaverCelestial?
        get() = queue.firstOrNull()

    val isReloading: Boolean
        get() = reloadTicksRemainingValue > 0

    val reloadTicksRemaining: Int
        get() = reloadTicksRemainingValue

    val conjunctionUsed: Boolean
        get() = conjunctionUsedValue

    val movementSpeedBonus: Double
        get() = if (reloadTicksRemainingValue == 0) 0.0 else {
            StarweaverBalance.RELOAD_MOVEMENT_SPEED_BONUS *
                reloadTicksRemainingValue.toDouble() / StarweaverBalance.RELOAD_TICKS
        }

    fun cooldownRemaining(slot: StarweaverSlot): Int = cooldowns.getValue(slot)

    internal fun setCooldownForTest(slot: StarweaverSlot, ticks: Int) {
        cooldowns[slot] = ticks.coerceAtLeast(0)
    }

    fun snapshot(): StarweaverRotationSnapshot = StarweaverRotationSnapshot(
        queue = queue.toList(),
        stored = stored,
        cooldowns = cooldowns.toMap(),
        reloadTicksRemaining = reloadTicksRemainingValue,
        conjunctionUsed = conjunctionUsedValue,
    )

    fun reset() {
        queue.clear()
        val marks = StarweaverCelestial.entries.flatMap { celestial ->
            List(StarweaverBalance.MARKS_PER_CELESTIAL) { celestial }
        }.shuffled(random).toMutableList()
        stored = requireNotNull(marks.removeAt(marks.lastIndex))
        queue.addAll(queueForCycle(marks, stored))
        reloadTicksRemainingValue = 0
        conjunctionUsedValue = false
        cooldowns.keys.forEach { cooldowns[it] = 0 }
    }

    /**
     * Development/test hook for deterministic rotation checks. It is also used
     * by the optional `/starweaverqueue` playground command.
     */
    fun setRotationForTest(queueMarks: List<StarweaverCelestial>, storedMark: StarweaverCelestial) {
        require(queueMarks.size == StarweaverBalance.QUEUE_SIZE) { "Starweaver queue must contain five marks" }
        val counts = (queueMarks + storedMark).groupingBy { it }.eachCount()
        require(StarweaverCelestial.entries.all { counts[it] == StarweaverBalance.MARKS_PER_CELESTIAL }) {
            "Starweaver rotation must contain exactly two of each celestial"
        }
        queue.clear()
        queue.addAll(queueMarks)
        stored = storedMark
        reloadTicksRemainingValue = 0
        conjunctionUsedValue = false
        cooldowns.keys.forEach { cooldowns[it] = 0 }
    }

    fun tryCast(slot: StarweaverSlot): StarweaverCast? {
        if (isReloading) return null
        val celestial = current ?: return null
        val conjunctionSlot = snapshot().conjunctionSlot
        if (conjunctionSlot == slot) {
            queue.removeFirst()
            queue.removeFirst()
            conjunctionUsedValue = true
            beginReloadIfEmpty()
            return StarweaverCast(
                castId = castIdSource(),
                slot = slot,
                celestial = celestial,
                kind = StarweaverCastKind.CONJUNCTION,
            )
        }
        if (cooldownRemaining(slot) > 0) return null

        queue.removeFirst()
        cooldowns[slot] = StarweaverBalance.BASE_COOLDOWN_TICKS
        beginReloadIfEmpty()
        return StarweaverCast(
            castId = castIdSource(),
            slot = slot,
            celestial = celestial,
            kind = StarweaverCastKind.BASE,
        )
    }

    fun trySwap(): Boolean {
        if (isReloading || current == null) return false
        val current = queue.removeFirst()
        queue.addFirst(stored)
        stored = current
        return true
    }

    fun tick() {
        cooldowns.keys.forEach { slot ->
            cooldowns[slot] = max(0, cooldowns.getValue(slot) - 1)
        }
        if (reloadTicksRemainingValue > 0) {
            reloadTicksRemainingValue--
            if (reloadTicksRemainingValue == 0) refillQueuePreservingStored()
        }
    }

    private fun beginReloadIfEmpty() {
        if (queue.isEmpty()) reloadTicksRemainingValue = StarweaverBalance.RELOAD_TICKS
    }

    private fun refillQueuePreservingStored() {
        val marks = StarweaverCelestial.entries.flatMap { celestial ->
            List(StarweaverBalance.MARKS_PER_CELESTIAL) { celestial }
        }.toMutableList()
        check(marks.remove(stored)) { "Stored Starweaver mark was not present in refill pool" }
        queue.clear()
        queue.addAll(queueForCycle(marks.shuffled(random), stored))
        check(queue.size == StarweaverBalance.QUEUE_SIZE)
        conjunctionUsedValue = false
    }

    /**
     * Applies the cycle-start invariant with one finite swap after shuffling.
     * The mark multiset is unchanged, so the two-of-each composition remains
     * intact while a refill can never begin with the stored mark.
     */
    private fun queueForCycle(
        marks: List<StarweaverCelestial>,
        storedMark: StarweaverCelestial,
    ): List<StarweaverCelestial> {
        check(marks.size == StarweaverBalance.QUEUE_SIZE)
        val queueMarks = marks.toMutableList()
        if (queueMarks.firstOrNull() == storedMark) {
            val swapIndex = queueMarks.indexOfFirst { it != storedMark }
            check(swapIndex > 0) { "A Starweaver cycle needs a mark different from stored" }
            val replacement = queueMarks[swapIndex]
            queueMarks[swapIndex] = queueMarks[0]
            queueMarks[0] = replacement
        }
        check(queueMarks.firstOrNull() != storedMark)
        return queueMarks
    }
}

data class StarweaverProjectileTarget(
    val target: CombatTarget,
    val isAlly: Boolean,
)

data class StarweaverProjectileTick(
    val hitTargetIds: List<UUID>,
    val position: Point,
    val active: Boolean,
)

/** A server-only projectile; collision is checked by segment sweep each tick. */
class StarweaverProjectileState(
    val cast: StarweaverCast,
    origin: Point,
    direction: Vec,
    private val speedBlocksPerTick: Double,
    private val range: Double,
    private val hitRadius: Double,
) {
    private val direction = normalizeDirection(direction)
    private val hitTargets = mutableSetOf<UUID>()
    private var distanceTravelled = 0.0

    var previousPosition: Point = origin
        private set
    var position: Point = origin
        private set
    var active: Boolean = true
        private set

    val hitTargetIds: Set<UUID>
        get() = hitTargets

    fun tick(
        targets: Collection<StarweaverProjectileTarget>,
        blocksAlongSegment: (Point, Point) -> Boolean = { _, _ -> false },
    ): StarweaverProjectileTick {
        if (!active) return StarweaverProjectileTick(emptyList(), position, false)

        previousPosition = position
        val distance = min(speedBlocksPerTick, range - distanceTravelled)
        position = position.add(
            direction.x() * distance,
            direction.y() * distance,
            direction.z() * distance,
        )
        distanceTravelled += distance

        val hits = targets.asSequence()
            .filter { it.target.id !in hitTargets }
            .filter { segmentIntersectsExpandedAabb(previousPosition, position, it.target, hitRadius) }
            .map { it.target.id }
            .toList()
        hitTargets += hits

        if (blocksAlongSegment(previousPosition, position) || distanceTravelled >= range - 1.0e-9) {
            active = false
        }
        return StarweaverProjectileTick(hits, position, active)
    }

    companion object {
        fun normal(cast: StarweaverCast, origin: Point, direction: Vec): StarweaverProjectileState =
            StarweaverProjectileState(
                cast = cast,
                origin = origin,
                direction = direction,
                speedBlocksPerTick = StarweaverBalance.BASE_PROJECTILE_SPEED_BLOCKS_PER_TICK *
                    if (cast.celestial == StarweaverCelestial.STAR) StarweaverBalance.STAR_Q_SPEED_MULTIPLIER else 1.0,
                range = StarweaverBalance.BASE_PROJECTILE_RANGE,
                hitRadius = StarweaverBalance.BASE_PROJECTILE_RADIUS,
            )

        fun solar(cast: StarweaverCast, origin: Point, direction: Vec): StarweaverProjectileState =
            StarweaverProjectileState(
                cast = cast,
                origin = origin,
                direction = direction,
                speedBlocksPerTick = StarweaverBalance.BASE_PROJECTILE_SPEED_BLOCKS_PER_TICK,
                range = StarweaverBalance.SOLAR_Q_RANGE,
                hitRadius = StarweaverBalance.SOLAR_Q_RADIUS,
            )
    }
}

data class StarweaverPendingZone(
    val cast: StarweaverCast,
    val center: Point,
    var ticksRemaining: Int,
)

data class StarweaverFieldPulse(
    val castId: Long,
    val center: Point,
    val radius: Double,
)

data class StarweaverPeriodicEffect(
    val targetId: UUID,
    val damage: Int,
    val source: StarweaverPeriodicSource,
)

enum class StarweaverPeriodicSource {
    SOLAR_BURN,
    SOLAR_Q_DOT,
    STELLAR_FIELD,
}

private data class ActiveStarweaverField(
    val castId: Long,
    val center: Point,
    var ticksRemaining: Int,
    var ticksUntilPulse: Int,
)

private data class TimedDot(
    val damage: Int,
    var ticksRemaining: Int,
    var ticksUntilPulse: Int,
    val source: StarweaverPeriodicSource,
)

data class StarweaverShield(
    val amount: Int,
    val ticksRemaining: Int,
)

/**
 * Starweaver-owned timed effects. These are deliberately local to the
 * prototype; they do not introduce a shared status-effect framework.
 */
class StarweaverEffectState {
    private val slows = mutableMapOf<UUID, TimedValue>()
    private val stuns = mutableMapOf<UUID, TimedValue>()
    private val moonlit = mutableMapOf<UUID, Int>()
    private val shields = mutableMapOf<UUID, TimedValue>()
    private val burns = mutableMapOf<UUID, TimedDot>()
    private val solarDots = mutableMapOf<UUID, TimedDot>()
    private val propagationGuards = mutableSetOf<Long>()

    fun clear() {
        slows.clear()
        stuns.clear()
        moonlit.clear()
        shields.clear()
        burns.clear()
        solarDots.clear()
        propagationGuards.clear()
    }

    fun tick(): List<StarweaverPeriodicEffect> {
        decrementValues(slows)
        decrementValues(stuns)
        decrementTicks(moonlit)
        decrementValues(shields)
        return tickDotMap(burns) + tickDotMap(solarDots)
    }

    fun applySlow(targetId: UUID, multiplier: Double, durationTicks: Int) {
        val clamped = multiplier.coerceIn(0.0, 1.0)
        slows[targetId] = TimedValue(clamped, max(durationTicks, 1))
    }

    fun slowMultiplier(targetId: UUID): Double? = slows[targetId]?.value

    fun applyStun(targetId: UUID, durationTicks: Int) {
        stuns[targetId] = TimedValue(1.0, max(durationTicks, 1))
    }

    fun isStunned(targetId: UUID): Boolean = stuns.containsKey(targetId)

    fun applyMoonlit(targetId: UUID, durationTicks: Int = StarweaverBalance.MOONLIT_DURATION_TICKS) {
        moonlit[targetId] = max(moonlit[targetId] ?: 0, durationTicks)
    }

    fun isMoonlit(targetId: UUID): Boolean = (moonlit[targetId] ?: 0) > 0

    fun applyShield(targetId: UUID, amount: Int, durationTicks: Int = StarweaverBalance.SHIELD_DURATION_TICKS) {
        shields[targetId] = TimedValue(amount.coerceAtLeast(0).toDouble(), max(durationTicks, 1))
    }

    fun shield(targetId: UUID): StarweaverShield? = shields[targetId]?.let {
        StarweaverShield(it.value.toInt(), it.ticksRemaining)
    }

    fun applySolarBurn(targetId: UUID) {
        burns[targetId] = refreshedDot(
            previous = burns[targetId],
            damage = StarweaverBalance.SOLAR_BURN_DAMAGE_PER_TICK,
            durationTicks = StarweaverBalance.SOLAR_BURN_DURATION_TICKS,
            source = StarweaverPeriodicSource.SOLAR_BURN,
        )
    }

    fun applySolarQDot(targetId: UUID, targetMaxHealth: Int) {
        val damage = min(
            (targetMaxHealth * StarweaverBalance.SOLAR_Q_DOT_MAX_HEALTH_PERCENT).toInt(),
            StarweaverBalance.SOLAR_Q_DOT_DAMAGE_CAP,
        ).coerceAtLeast(1)
        solarDots[targetId] = refreshedDot(
            previous = solarDots[targetId],
            damage = damage,
            durationTicks = StarweaverBalance.SOLAR_Q_DOT_DURATION_TICKS,
            source = StarweaverPeriodicSource.SOLAR_Q_DOT,
        )
    }

    /**
     * Returns one non-recursive transfer set for a direct skill execution.
     * Periodic effects never call this method.
     */
    fun moonlitPropagation(
        castId: Long,
        primaryTargetId: UUID,
        directDamage: Int,
        moonlitEnemyIds: Collection<UUID>,
    ): List<Pair<UUID, Int>> {
        if (directDamage <= 0 || !isMoonlit(primaryTargetId) || !propagationGuards.add(castId)) return emptyList()
        return moonlitEnemyIds.asSequence()
            .filter { it != primaryTargetId && isMoonlit(it) }
            .distinct()
            .take(StarweaverBalance.MOONLIT_MAX_TRANSFER_TARGETS)
            .map { it to max(1, (directDamage * StarweaverBalance.MOONLIT_TRANSFER_RATIO).toInt()) }
            .toList()
    }

    private fun refreshedDot(
        previous: TimedDot?,
        damage: Int,
        durationTicks: Int,
        source: StarweaverPeriodicSource,
    ): TimedDot = TimedDot(
        damage = damage,
        ticksRemaining = max(previous?.ticksRemaining ?: 0, durationTicks),
        ticksUntilPulse = previous?.ticksUntilPulse?.coerceAtMost(StarweaverBalance.PERIODIC_TICK_INTERVAL) ?: StarweaverBalance.PERIODIC_TICK_INTERVAL,
        source = source,
    )

    private fun decrementValues(values: MutableMap<UUID, TimedValue>) {
        values.entries.removeIf { (_, value) ->
            value.ticksRemaining--
            value.ticksRemaining <= 0
        }
    }

    private fun decrementTicks(values: MutableMap<UUID, Int>) {
        values.entries.removeIf { (_, ticks) -> ticks - 1 <= 0 }
        values.keys.toList().forEach { values[it] = values.getValue(it) - 1 }
    }

    private fun tickDotMap(values: MutableMap<UUID, TimedDot>): List<StarweaverPeriodicEffect> {
        val events = mutableListOf<StarweaverPeriodicEffect>()
        values.entries.removeIf { (targetId, dot) ->
            dot.ticksRemaining--
            dot.ticksUntilPulse--
            if (dot.ticksUntilPulse <= 0) {
                events += StarweaverPeriodicEffect(
                    targetId = targetId,
                    damage = dot.damage,
                    source = dot.source,
                )
                dot.ticksUntilPulse = StarweaverBalance.PERIODIC_TICK_INTERVAL
            }
            dot.ticksRemaining <= 0
        }
        return events
    }

    private data class TimedValue(val value: Double, var ticksRemaining: Int)
}

/** Runtime containers for projectiles, delayed zones, fields, and effects. */
class StarweaverRuntimeState(
    random: Random = Random.Default,
    private val castIdSource: () -> Long = StarweaverExecutionIds::next,
) {
    val rotation = StarweaverRotationState(random, castIdSource)
    val effects = StarweaverEffectState()

    private val projectiles = mutableListOf<StarweaverProjectileState>()
    private val pendingZones = mutableListOf<StarweaverPendingZone>()
    private val fields = mutableListOf<ActiveStarweaverField>()
    private val selfHealCasts = mutableSetOf<Long>()

    fun reset() {
        projectiles.clear()
        pendingZones.clear()
        fields.clear()
        selfHealCasts.clear()
        effects.clear()
        rotation.reset()
    }

    fun nextExecutionId(): Long = castIdSource()

    fun markSelfHealIfFirst(castId: Long): Boolean = selfHealCasts.add(castId)

    fun addProjectile(projectile: StarweaverProjectileState) {
        projectiles += projectile
    }

    fun projectiles(): List<StarweaverProjectileState> = projectiles.toList()

    fun removeInactiveProjectiles() {
        projectiles.removeIf { !it.active }
    }

    fun addPendingZone(cast: StarweaverCast, center: Point, ticks: Int) {
        pendingZones += StarweaverPendingZone(cast, center, ticks.coerceAtLeast(1))
    }

    fun addField(castId: Long, center: Point) {
        fields += ActiveStarweaverField(
            castId = castId,
            center = center,
            ticksRemaining = StarweaverBalance.STELLAR_FIELD_DURATION_TICKS,
            ticksUntilPulse = StarweaverBalance.STELLAR_FIELD_TICK_INTERVAL,
        )
    }

    fun tick(): StarweaverRuntimeTick {
        rotation.tick()
        val periodicEffects = effects.tick()
        val activatedZones = mutableListOf<StarweaverPendingZone>()
        pendingZones.removeIf { zone ->
            zone.ticksRemaining--
            if (zone.ticksRemaining <= 0) {
                activatedZones += zone
                true
            } else {
                false
            }
        }

        val fieldPulses = mutableListOf<StarweaverFieldPulse>()
        fields.removeIf { field ->
            field.ticksRemaining--
            field.ticksUntilPulse--
            if (field.ticksUntilPulse <= 0) {
                fieldPulses += StarweaverFieldPulse(field.castId, field.center, StarweaverBalance.STELLAR_FIELD_RADIUS)
                field.ticksUntilPulse = StarweaverBalance.STELLAR_FIELD_TICK_INTERVAL
            }
            field.ticksRemaining <= 0
        }

        return StarweaverRuntimeTick(
            activatedZones = activatedZones,
            fieldPulses = fieldPulses,
            periodicEffects = periodicEffects,
        )
    }
}

data class StarweaverRuntimeTick(
    val activatedZones: List<StarweaverPendingZone>,
    val fieldPulses: List<StarweaverFieldPulse>,
    val periodicEffects: List<StarweaverPeriodicEffect>,
)

object StarweaverBalance {
    const val MARKS_PER_CELESTIAL = 2
    const val QUEUE_SIZE = 5
    const val BASE_COOLDOWN_TICKS = 90
    const val RELOAD_TICKS = 60
    const val RELOAD_MOVEMENT_SPEED_BONUS = 0.30

    const val BASE_PROJECTILE_RANGE = 18.0
    const val BASE_PROJECTILE_SPEED_BLOCKS_PER_TICK = 0.60
    const val BASE_PROJECTILE_RADIUS = 1.25
    const val STAR_Q_SPEED_MULTIPLIER = 1.75
    const val SOLAR_Q_RANGE = 22.0
    const val SOLAR_Q_RADIUS = 2.25

    const val W_RANGE = 14.0
    const val W_RADIUS = 3.5
    const val W_DELAY_TICKS = 15
    const val STAR_W_DELAY_TICKS = 8
    const val LUNAR_W_RADIUS = 5.0
    const val LUNAR_W_STUN_TICKS = 12

    const val E_RANGE = 14.0
    const val E_RADIUS = 3.0
    const val E_DELAY_TICKS = 22
    const val STAR_E_DELAY_TICKS = 9
    const val STELLAR_FIELD_RADIUS = 4.5
    const val STELLAR_FIELD_DURATION_TICKS = 120
    const val STELLAR_FIELD_TICK_INTERVAL = 20

    const val MOON_Q_SLOW_MULTIPLIER = 0.65
    const val MOON_Q_SLOW_TICKS = 50
    const val MOON_Q_BOSS_SLOW_MULTIPLIER = 0.85
    const val MOON_Q_BOSS_SLOW_TICKS = 25
    const val MOON_W_STUN_TICKS = 18
    const val MOON_E_STUN_TICKS = 25
    const val MOONLIT_DURATION_TICKS = 100
    const val MOONLIT_TRANSFER_RATIO = 0.25
    const val MOONLIT_MAX_TRANSFER_TARGETS = 4
    const val SHIELD_DURATION_TICKS = 80
    const val PERIODIC_TICK_INTERVAL = 20

    const val SUN_Q_DAMAGE = 42
    const val MOON_Q_DAMAGE = 26
    const val STAR_Q_DAMAGE = 24
    const val STAR_Q_HEAL = 4
    const val STAR_Q_SELF_HEAL = 2
    const val SOLAR_Q_DAMAGE = 96
    const val SOLAR_Q_DOT_MAX_HEALTH_PERCENT = 0.04
    const val SOLAR_Q_DOT_DAMAGE_CAP = 30
    const val SOLAR_Q_DOT_DURATION_TICKS = 100
    const val SUN_W_DAMAGE = 24
    const val MOON_W_DAMAGE = 20
    const val STAR_W_DAMAGE = 20
    const val STAR_W_SHIELD = 5
    const val LUNAR_W_DAMAGE = 28
    const val SUN_E_DAMAGE = 50
    const val MOON_E_DAMAGE = 30
    const val STAR_E_DAMAGE = 28
    const val STAR_E_HEAL = 7
    const val STELLAR_E_DAMAGE = 38
    const val STELLAR_E_HEAL = 6
    const val SOLAR_BURN_DAMAGE_PER_TICK = 3
    const val SOLAR_BURN_DURATION_TICKS = 80
    const val STELLAR_FIELD_DAMAGE = 10
}

private object StarweaverExecutionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}

private fun normalizeDirection(direction: Vec): Vec {
    val length = direction.length()
    return if (length > 1.0e-9) direction.mul(1.0 / length) else Vec(0.0, 0.0, 1.0)
}

internal fun segmentIntersectsExpandedAabb(
    start: Point,
    end: Point,
    target: CombatTarget,
    radius: Double,
): Boolean {
    val minX = target.position.x() - target.halfExtent.x() - radius
    val maxX = target.position.x() + target.halfExtent.x() + radius
    val minY = target.position.y() - target.halfExtent.y() - radius
    val maxY = target.position.y() + target.halfExtent.y() + radius
    val minZ = target.position.z() - target.halfExtent.z() - radius
    val maxZ = target.position.z() + target.halfExtent.z() + radius
    var near = 0.0
    var far = 1.0

    fun update(startValue: Double, delta: Double, minValue: Double, maxValue: Double): Boolean {
        if (kotlin.math.abs(delta) < 1.0e-9) return startValue in minValue..maxValue
        var axisNear = (minValue - startValue) / delta
        var axisFar = (maxValue - startValue) / delta
        if (axisNear > axisFar) {
            val swap = axisNear
            axisNear = axisFar
            axisFar = swap
        }
        near = max(near, axisNear)
        far = min(far, axisFar)
        return near <= far
    }

    return update(start.x(), end.x() - start.x(), minX, maxX) &&
        update(start.y(), end.y() - start.y(), minY, maxY) &&
        update(start.z(), end.z() - start.z(), minZ, maxZ) &&
        far >= 0.0 && near <= 1.0
}

internal fun isWithinStarweaverAabbRadius(center: Point, radius: Double, target: CombatTarget): Boolean {
    val closestX = center.x().coerceIn(
        target.position.x() - target.halfExtent.x(),
        target.position.x() + target.halfExtent.x(),
    )
    val closestY = center.y().coerceIn(
        target.position.y() - target.halfExtent.y(),
        target.position.y() + target.halfExtent.y(),
    )
    val closestZ = center.z().coerceIn(
        target.position.z() - target.halfExtent.z(),
        target.position.z() + target.halfExtent.z(),
    )
    val dx = closestX - center.x()
    val dy = closestY - center.y()
    val dz = closestZ - center.z()
    return dx * dx + dy * dy + dz * dz <= radius * radius + 1.0e-9
}
