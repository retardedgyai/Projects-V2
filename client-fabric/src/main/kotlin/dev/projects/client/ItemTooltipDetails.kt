package dev.projects.client

internal object ItemTooltipDetails {
    const val ADVANCED_ONLY_MARKER: Char = '\uE120'
    const val COMPACT_ONLY_MARKER: Char = '\uE121'

    fun shouldDisplay(lineText: String, shiftDown: Boolean): Boolean = when (lineText.firstOrNull()) {
        ADVANCED_ONLY_MARKER -> shiftDown
        COMPACT_ONLY_MARKER -> !shiftDown
        else -> true
    }
}
