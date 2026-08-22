package dev.projects.server.particle

data class ParticleProfileSnapshot(
    val activeEffects: Int,
    val currentAnimations: Int,
    val particlesRequested: Int,
    val particlesSent: Int,
    val particlesDegraded: Int,
    val byCategory: Map<ParticleCategory, Int>,
    val byViewer: Map<String, Int>,
    val topEffectIds: List<Pair<String, Int>>,
)

/** In-memory counters for /vfxstats-style diagnostics. It stores no player identity. */
class ParticleProfiler {
    private var activeEffects = 0
    private var currentAnimations = 0
    private var requested = 0
    private var sent = 0
    private var degraded = 0
    private val categories = mutableMapOf<ParticleCategory, Int>()
    private val viewers = mutableMapOf<String, Int>()
    private val effects = mutableMapOf<String, Int>()

    fun setActiveEffects(active: Int, animations: Int = active) {
        activeEffects = active.coerceAtLeast(0)
        currentAnimations = animations.coerceAtLeast(0)
    }

    fun record(effectId: String, viewerKey: String, category: ParticleCategory, requestedCount: Int, sentCount: Int) {
        val requestedSafe = requestedCount.coerceAtLeast(0)
        val sentSafe = sentCount.coerceIn(0, requestedSafe)
        requested += requestedSafe
        sent += sentSafe
        degraded += requestedSafe - sentSafe
        categories[category] = (categories[category] ?: 0) + sentSafe
        viewers[viewerKey] = (viewers[viewerKey] ?: 0) + sentSafe
        effects[effectId] = (effects[effectId] ?: 0) + sentSafe
    }

    fun snapshot(): ParticleProfileSnapshot = ParticleProfileSnapshot(
        activeEffects,
        currentAnimations,
        requested,
        sent,
        degraded,
        categories.toMap(),
        viewers.toMap(),
        effects.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
    )

    fun reset() {
        requested = 0
        sent = 0
        degraded = 0
        categories.clear()
        viewers.clear()
        effects.clear()
    }
}
