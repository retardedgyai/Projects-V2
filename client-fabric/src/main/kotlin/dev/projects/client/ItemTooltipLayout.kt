package dev.projects.client

internal data class ItemTooltipHeaderPalette(
    val plateColor: Int,
    val dividerColor: Int,
)

internal object ItemTooltipLayout {
    private val headerPalettes = mapOf(
        "item_common" to ItemTooltipHeaderPalette(0xB024282A.toInt(), 0xFF9CA4A8.toInt()),
        "item_uncommon" to ItemTooltipHeaderPalette(0xB0192A23.toInt(), 0xFF66B28B.toInt()),
        "item_rare" to ItemTooltipHeaderPalette(0xB0162530.toInt(), 0xFF5BA9D3.toInt()),
        "item_epic" to ItemTooltipHeaderPalette(0xB0251730.toInt(), 0xFFB884E0.toInt()),
    )

    fun headerPalette(namespace: String?, path: String?): ItemTooltipHeaderPalette? =
        if (namespace == "projects") headerPalettes[path] else null

    fun shouldCenterTitle(namespace: String?, path: String?): Boolean =
        headerPalette(namespace, path) != null

    fun centeredTitleX(tooltipLeft: Int, tooltipWidth: Int, titleWidth: Int): Int =
        tooltipLeft + ((tooltipWidth - titleWidth).coerceAtLeast(0) / 2)
}
