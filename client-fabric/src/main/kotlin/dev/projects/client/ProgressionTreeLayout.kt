package dev.projects.client

import kotlin.math.min

data class ProgressionTreeLayout(
    val panel: HudRect,
    val detail: HudRect,
    val purchase: HudRect,
    val notice: HudRect,
    val footer: HudRect,
    val compact: Boolean,
)

data class ProgressionTreeNodePosition(val x: Int, val y: Int)

fun progressionTreeLayout(screenWidth: Int, screenHeight: Int): ProgressionTreeLayout {
    require(screenWidth > 0 && screenHeight > 0) { "Progression tree viewport must be positive" }
    val panelWidth = min(560, (screenWidth - 16).coerceAtLeast(120))
    val compact = panelWidth < 500
    val panelHeight = min(if (compact) 430 else 320, (screenHeight - 16).coerceAtLeast(120))
    val panel = HudRect(
        x = (screenWidth - panelWidth) / 2,
        y = (screenHeight - panelHeight) / 2,
        width = panelWidth,
        height = panelHeight,
    )
    val footer = HudRect(panel.x + 16, panel.bottom - 18, panel.width - 32, 10)
    val notice = HudRect(panel.x + 16, footer.y - 18, panel.width - 32, 10)

    if (!compact) {
        return ProgressionTreeLayout(
            panel = panel,
            detail = HudRect(panel.x + 330, panel.y + 48, panel.width - 344, 172),
            purchase = HudRect(panel.x + 350, panel.y + 236, 110, 20),
            notice = notice,
            footer = footer,
            compact = false,
        )
    }

    // Keep the detail column beside the tree so short GUI viewports do not overflow vertically.
    val detailWidth = (panel.width - 314).coerceAtLeast(120)
    val purchaseY = notice.y - 28
    val detailTop = panel.y + 48
    val detail = HudRect(panel.x + panel.width - detailWidth - 14, detailTop, detailWidth, (purchaseY - detailTop - 8).coerceAtLeast(80))
    return ProgressionTreeLayout(
        panel = panel,
        detail = detail,
        purchase = HudRect(detail.x, purchaseY, min(110, detail.width), 20),
        notice = notice,
        footer = footer,
        compact = true,
    )
}

fun progressionTreeNodePositions(panel: HudRect): Map<String, ProgressionTreeNodePosition> = mapOf(
    "projects:passive/force" to ProgressionTreeNodePosition(panel.x + 112, panel.y + 174),
    "projects:passive/overpower" to ProgressionTreeNodePosition(panel.x + 62, panel.y + 124),
    "projects:passive/tempo" to ProgressionTreeNodePosition(panel.x + 232, panel.y + 174),
    "projects:passive/flow" to ProgressionTreeNodePosition(panel.x + 282, panel.y + 124),
    "projects:passive/vitality" to ProgressionTreeNodePosition(panel.x + 172, panel.y + 124),
    "projects:passive/guard" to ProgressionTreeNodePosition(panel.x + 172, panel.y + 74),
)

private val HudRect.bottom: Int
    get() = y + height
