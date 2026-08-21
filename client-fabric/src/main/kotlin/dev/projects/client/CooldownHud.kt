package dev.projects.client

import java.util.Locale

internal fun cooldownFillRatio(remainingTicks: Int, maxTicks: Int): Float {
    if (maxTicks <= 0) return 0f
    return (remainingTicks.toFloat() / maxTicks.toFloat()).coerceIn(0f, 1f)
}

internal fun cooldownSecondsText(remainingTicks: Int): String =
    String.format(Locale.ROOT, "%.1f", remainingTicks.coerceAtLeast(0) / 20.0)
