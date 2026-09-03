package dev.projects.client

internal object ItemTooltipLayout {
    fun shouldCenterTitle(namespace: String?, path: String?): Boolean =
        namespace == "projects" && path?.startsWith("item_") == true

    fun centeredTitleX(tooltipLeft: Int, tooltipWidth: Int, titleWidth: Int): Int =
        tooltipLeft + ((tooltipWidth - titleWidth).coerceAtLeast(0) / 2)
}
