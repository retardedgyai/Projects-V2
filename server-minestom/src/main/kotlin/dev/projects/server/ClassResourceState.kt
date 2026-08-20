package dev.projects.server

import dev.projects.protocol.ClassResourceSnapshot
import kotlin.math.min
import kotlin.math.roundToInt

/** Server-owned mana and first-class aerial resource state. */
class ClassResourceState(
    val maxMana: Int = MAX_MANA,
    val maxAerialGauge: Int = MAX_AERIAL_GAUGE,
) {
    init {
        require(maxMana > 0) { "Max Mana must be positive" }
        require(maxAerialGauge > 0) { "Max Aerial Gauge must be positive" }
    }

    private var manaValue = maxMana
    private var aerialGaugeValue = 0.0

    val mana: Int
        get() = manaValue

    val aerialGauge: Double
        get() = aerialGaugeValue

    val aerialGaugeDisplay: Int
        get() = aerialGaugeValue.roundToInt().coerceIn(0, maxAerialGauge)

    fun reset() {
        manaValue = maxMana
        aerialGaugeValue = 0.0
    }

    fun canSpend(amount: Int): Boolean = amount >= 0 && manaValue >= amount

    fun trySpend(amount: Int): Boolean {
        if (!canSpend(amount)) return false
        manaValue -= amount
        return true
    }

    fun addAerialGauge(amount: Int): Boolean {
        require(amount >= 0) { "Aerial Gauge reward must not be negative" }
        val previous = aerialGaugeValue
        aerialGaugeValue = (aerialGaugeValue + amount).coerceAtMost(maxAerialGauge.toDouble())
        return aerialGaugeValue != previous
    }

    fun drainAerialGauge(amount: Double): Double {
        require(amount >= 0.0 && amount.isFinite()) { "Aerial Gauge drain must be finite and non-negative" }
        val drained = min(amount, aerialGaugeValue)
        aerialGaugeValue -= drained
        return drained
    }

    fun snapshot(): ClassResourceSnapshot = ClassResourceSnapshot(
        mana = mana,
        maxMana = maxMana,
        aerialGauge = aerialGaugeDisplay,
        maxAerialGauge = maxAerialGauge,
    )

    companion object {
        const val MAX_MANA = 100
        const val MAX_AERIAL_GAUGE = 100
    }
}

data class AerialHoverTick(
    val velocityY: Double,
    val drained: Double,
    val hovering: Boolean,
)

/** Server-side hold state and per-tick hover rules. */
class AerialHoverState(
    private val drainPerSecond: Double = DRAIN_PER_SECOND,
    private val hoverFallSpeed: Double = HOVER_FALL_SPEED,
) {
    init {
        require(drainPerSecond > 0.0 && drainPerSecond.isFinite())
        require(hoverFallSpeed >= 0.0 && hoverFallSpeed.isFinite())
    }

    var isHolding: Boolean = false
        private set

    fun request(active: Boolean, isGrounded: Boolean) {
        isHolding = active && !isGrounded
    }

    fun reset() {
        isHolding = false
    }

    fun tick(
        isGrounded: Boolean,
        velocityY: Double,
        resources: ClassResourceState,
        ticksPerSecond: Double = 20.0,
    ): AerialHoverTick {
        require(ticksPerSecond > 0.0 && ticksPerSecond.isFinite())
        if (isGrounded) {
            isHolding = false
            return AerialHoverTick(velocityY, 0.0, false)
        }
        if (!isHolding || velocityY > 0.0 || resources.aerialGauge <= 0.0) {
            if (resources.aerialGauge <= 0.0) isHolding = false
            return AerialHoverTick(velocityY, 0.0, isHolding)
        }

        val drained = resources.drainAerialGauge(drainPerSecond / ticksPerSecond)
        if (resources.aerialGauge <= 0.0) isHolding = false
        return AerialHoverTick(
            velocityY = maxOf(velocityY, -hoverFallSpeed),
            drained = drained,
            hovering = isHolding,
        )
    }

    companion object {
        const val DRAIN_PER_SECOND = 25.0
        const val HOVER_FALL_SPEED = 0.4
    }
}

/** One normal attack execution can reward the class resource only once. */
class AerialGaugeRewardState {
    private val rewardedExecutionIds = mutableSetOf<Long>()

    fun reset() {
        rewardedExecutionIds.clear()
    }

    fun onNormalHit(
        resources: ClassResourceState,
        attackExecutionId: Long,
        weakpoint: FixedWeakpoint?,
    ): Boolean {
        if (!rewardedExecutionIds.add(attackExecutionId)) return false
        resources.addAerialGauge(if (weakpoint == null) BODY_REWARD else WEAKPOINT_REWARD)
        return true
    }

    companion object {
        const val BODY_REWARD = 20
        const val WEAKPOINT_REWARD = 30
    }
}
