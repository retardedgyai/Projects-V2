package dev.projects.client

import java.util.Locale

internal fun cooldownFillRatio(remainingTicks: Int, maxTicks: Int): Float {
    if (maxTicks <= 0) return 0f
    return (remainingTicks.toFloat() / maxTicks.toFloat()).coerceIn(0f, 1f)
}

internal fun cooldownSecondsText(remainingTicks: Int): String =
    String.format(Locale.ROOT, "%.1f", remainingTicks.coerceAtLeast(0) / 20.0)

internal fun skillHudReady(available: Boolean, remainingTicks: Int): Boolean =
    available && remainingTicks == 0

internal fun meterFillWidth(value: Int, maximum: Int, width: Int): Int {
    if (maximum <= 0 || width <= 0) return 0
    return (width.toLong() * value.coerceIn(0, maximum) / maximum).toInt()
}
