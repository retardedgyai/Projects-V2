package dev.projects.modellab

/** Bounded immutable snapshots, following Scorpius Metrics; no public admin HTTP endpoint. */
class TickMetrics(private val capacity: Int = 1200) {
    init { require(capacity > 0) }
    private val samples = ArrayDeque<Double>()
    @Synchronized fun record(milliseconds: Double) {
        require(milliseconds.isFinite() && milliseconds >= 0)
        samples.addLast(milliseconds)
        while (samples.size > capacity) samples.removeFirst()
    }
    @Synchronized fun snapshot(): List<Double> = samples.toList()
    fun summary(entities: Int): String {
        val values = snapshot().sorted()
        if (values.isEmpty()) return "計測待ち"
        val p95 = values[((values.size - 1) * .95).toInt()]
        val used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576
        return "tick 平均 %.2fms / p95 %.2fms / 最大 %.2fms / Entity %d / heap %dMB"
            .format(values.average(), p95, values.last(), entities, used)
    }
}
