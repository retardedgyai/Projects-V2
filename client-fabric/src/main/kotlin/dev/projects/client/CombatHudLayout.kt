package dev.projects.client

internal data class HudRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int
        get() = x + width
    val bottom: Int
        get() = y + height
    val centerX: Int
        get() = x + width / 2

    fun isWithin(guiWidth: Int, guiHeight: Int): Boolean =
        x >= 0 && y >= 0 && right <= guiWidth && bottom <= guiHeight

    fun intersects(other: HudRect): Boolean =
        x < other.right && right > other.x && y < other.bottom && bottom > other.y
}

internal data class CombatHudLayout(
    val health: HudRect,
    val core: HudRect,
    val resource: HudRect,
    val skills: HudRect,
    val hotbar: HudRect,
    val offhand: HudRect,
    val stored: HudRect,
)

internal fun calculateCombatHudLayout(guiWidth: Int, guiHeight: Int): CombatHudLayout {
    require(guiWidth > 0 && guiHeight > 0)

    val core = HudRect(
        x = guiWidth / 2 - 28,
        y = guiHeight - 120,
        width = 56,
        height = 56,
    )
    val health = HudRect(core.x - 8 - 160, core.y + 16, 160, 24)
    val resource = HudRect(core.right + 8, core.y + 16, 160, 24)
    val skills = HudRect(core.x - 18 - 164, guiHeight - 52, 164, 40)
    val hotbar = HudRect(core.right + 18, guiHeight - 48, 224, 34)
    val offhand = HudRect(hotbar.right + 4, hotbar.y, 34, 34)
    val stored = HudRect(resource.right - 96, core.y - 34, 96, 28)

    return CombatHudLayout(health, core, resource, skills, hotbar, offhand, stored)
}
