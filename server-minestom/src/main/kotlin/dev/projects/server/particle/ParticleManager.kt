package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.math.floor
import kotlin.math.max

data class ParticleQuality(
    val ownActiveMultiplier: Double = 1.0,
    val otherActiveMultiplier: Double = 1.0,
    val enemyMultiplier: Double = 1.0,
    val bossMultiplier: Double = 1.0,
    val fullMultiplier: Double = 1.0,
    val maximumMultiplier: Double = 1.0,
    val minimumCount: Int = 0,
    val distanceFalloffStart: Double = 0.0,
    val distanceFalloffEnd: Double = 64.0,
    val skipBelowMultiplier: Double = 0.0,
) {
    init {
        require(listOf(ownActiveMultiplier, otherActiveMultiplier, enemyMultiplier, bossMultiplier, fullMultiplier).all { it >= 0.0 })
        require(maximumMultiplier >= 0.0 && minimumCount >= 0 && distanceFalloffStart >= 0.0)
        require(distanceFalloffEnd >= distanceFalloffStart && skipBelowMultiplier >= 0.0)
    }

    fun multiplier(category: ParticleCategory): Double = when (category) {
        ParticleCategory.OWN_ACTIVE -> ownActiveMultiplier
        ParticleCategory.OTHER_ACTIVE -> otherActiveMultiplier
        ParticleCategory.ENEMY -> enemyMultiplier
        ParticleCategory.BOSS -> bossMultiplier
        ParticleCategory.FULL -> fullMultiplier
    }.coerceAtMost(maximumMultiplier)

    fun distanceMultiplier(distance: Double): Double {
        if (distanceFalloffEnd <= distanceFalloffStart || distance <= distanceFalloffStart) return 1.0
        if (distance >= distanceFalloffEnd) return 0.0
        return 1.0 - (distance - distanceFalloffStart) / (distanceFalloffEnd - distanceFalloffStart)
    }
}

data class ParticleBudget(val particlesPerTick: Int = Int.MAX_VALUE) {
    init {
        require(particlesPerTick >= 0)
    }
}

data class ParticleViewer(
    val position: Point,
    val player: Player? = null,
)

data class ParticleCounters(
    val attempted: Int = 0,
    val dispatched: Int = 0,
    val dropped: Int = 0,
    val byCategory: Map<ParticleCategory, Int> = emptyMap(),
)

/** Applies viewer quality and a per-tick density budget before packet dispatch. */
class ParticleManager(
    var quality: ParticleQuality = ParticleQuality(),
    var budget: ParticleBudget = ParticleBudget(),
    var viewerCondition: (ParticleViewer) -> Boolean = { true },
    var viewerFilter: (ParticleViewer, ParticleSpawn) -> Boolean = { _, _ -> true },
    var profiler: ParticleProfiler? = null,
) {
    private var attempted = 0
    private var dispatched = 0
    private var dropped = 0
    private val categoryCounts = mutableMapOf<ParticleCategory, Int>()
    private val tickBudgetUsed = mutableMapOf<Any, Int>()
    private data class Pending(val viewer: ParticleViewer, val spawn: ParticleSpawn, val sink: ParticleSink, val effectId: String)
    private val pending = mutableListOf<Pending>()
    private var nextViewerId = 0
    private val playerViewerIds = mutableMapOf<UUID, String>()
    private val snapshotViewerIds = IdentityHashMap<ParticleViewer, String>()

    val counters: ParticleCounters
        get() = ParticleCounters(attempted, dispatched, dropped, categoryCounts.toMap())

    fun beginTick() {
        tickBudgetUsed.clear()
        pending.clear()
    }

    fun resetCounters() {
        attempted = 0
        dispatched = 0
        dropped = 0
        categoryCounts.clear()
        tickBudgetUsed.clear()
        pending.clear()
    }

    fun qualityMultiplier(category: ParticleCategory): Double = quality.multiplier(category)

    fun dispatch(viewer: ParticleViewer, spawn: ParticleSpawn, sink: ParticleSink): Boolean =
        dispatchNow(viewer, spawn, sink, "particle")

    private fun dispatchNow(viewer: ParticleViewer, spawn: ParticleSpawn, sink: ParticleSink, effectId: String): Boolean {
        attempted++
        if (!viewerCondition(viewer) || !viewerFilter(viewer, spawn)) {
            dropped++
            return false
        }
        // A live Player is the distance origin; position is only the fixed/snapshot fallback.
        val distanceOrigin = viewer.player?.position ?: viewer.position
        val distance = distanceOrigin.distance(spawn.position)
        val multiplier = quality.multiplier(spawn.category) * quality.distanceMultiplier(distance)
        if (multiplier < quality.skipBelowMultiplier || multiplier <= 0.0) {
            dropped++
            return false
        }
        val logicalCount = scaledCountForManager(spawn.count, multiplier, attempted, quality.minimumCount)
        if (logicalCount == 0) {
            dropped++
            return false
        }
        val viewerKey = ViewerBudgetKey(viewer)
        val used = tickBudgetUsed[viewerKey] ?: 0
        val acceptedCount = minOf(logicalCount, (budget.particlesPerTick - used).coerceAtLeast(0))
        if (acceptedCount == 0) {
            dropped++
            return false
        }
        try {
            sink.spawn(spawn.copy(count = acceptedCount))
        } catch (_: RuntimeException) {
            dropped++
            return false
        }
        tickBudgetUsed[viewerKey] = used + acceptedCount
        val acceptedPackets = if (spawn.directional) acceptedCount else 1
        dispatched += acceptedPackets
        categoryCounts[spawn.category] = (categoryCounts[spawn.category] ?: 0) + acceptedPackets
        if (acceptedCount < logicalCount) dropped += logicalCount - acceptedCount
        profiler?.record(
            effectId = effectId,
            viewerKey = profilerViewerKey(viewer),
            category = spawn.category,
            requestedCount = logicalCount,
            sentCount = acceptedCount,
        )
        return true
    }

    fun dispatch(viewerPosition: Point, spawn: ParticleSpawn, sink: ParticleSink): Boolean =
        dispatch(ParticleViewer(viewerPosition), spawn, sink)

    fun dispatch(player: Player, spawn: ParticleSpawn): Boolean =
        dispatch(ParticleViewer(player.position, player), spawn, PlayerParticleSink(player))

    fun sink(viewer: ParticleViewer, delegate: ParticleSink, effectId: String = "particle"): ParticleSink {
        val queued = ParticleSink { spawn -> pending += Pending(viewer, spawn, delegate, effectId) }
        return if (viewer.player != null) ManagedParticleSink(viewer.player, queued) else queued
    }

    fun flush() {
        val queued = pending.toList()
        pending.clear()
        queued.groupBy { it.viewer.player?.uuid ?: it.viewer }.values.forEach { entries ->
            entries.sortedWith(compareByDescending<Pending> { importancePriority(it.spawn.importance) }
                .thenByDescending { priority(it.spawn.category) })
                .forEach { dispatchNow(it.viewer, it.spawn, it.sink, it.effectId) }
        }
    }

    fun dispatchAll(viewer: ParticleViewer, spawns: Iterable<ParticleSpawn>, sink: ParticleSink) {
        // Spend the finite budget on gameplay-critical categories first.
        spawns.sortedWith(compareByDescending<ParticleSpawn> { importancePriority(it.importance) }
            .thenByDescending { priority(it.category) })
            .forEach { dispatchNow(viewer, it, sink, "particle") }
    }

    private fun importancePriority(importance: ParticleImportance): Int = when (importance) {
        ParticleImportance.GAMEPLAY_TELEGRAPH -> 3
        ParticleImportance.COMBAT_FEEDBACK -> 2
        ParticleImportance.COSMETIC -> 1
    }

    private fun priority(category: ParticleCategory): Int = when (category) {
        ParticleCategory.BOSS -> 4
        ParticleCategory.ENEMY -> 3
        ParticleCategory.OWN_ACTIVE -> 2
        ParticleCategory.OTHER_ACTIVE -> 1
        ParticleCategory.FULL -> 0
    }

    private fun profilerViewerKey(viewer: ParticleViewer): String {
        if (viewer.player != null) {
            return playerViewerIds.getOrPut(viewer.player.uuid) { "viewer-${++nextViewerId}" }
        }
        return snapshotViewerIds.getOrPut(viewer) { "viewer-${++nextViewerId}" }
    }
}

private class ViewerBudgetKey(private val viewer: ParticleViewer) {
    override fun equals(other: Any?): Boolean = other is ViewerBudgetKey && when {
        viewer.player != null && other.viewer.player != null -> viewer.player.uuid == other.viewer.player.uuid
        else -> viewer === other.viewer
    }

    override fun hashCode(): Int = viewer.player?.uuid?.hashCode() ?: System.identityHashCode(viewer)
}

private fun scaledCountForManager(count: Int, multiplier: Double, index: Int, minimum: Int): Int {
    if (count <= 0) return 0
    val exact = count * multiplier
    val base = floor(exact).toInt()
    val fractional = exact - base
    val rounded = base + if (fractional > 0.0 && ((index * 1103515245L + 12345L) and 0x7fffffff) / 2147483648.0 < fractional) 1 else 0
    return max(if (multiplier > 0.0) minimum else 0, rounded)
}
