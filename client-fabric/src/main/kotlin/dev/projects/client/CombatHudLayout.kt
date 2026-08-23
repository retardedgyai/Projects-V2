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
)

internal fun calculateCombatHudLayout(guiWidth: Int, guiHeight: Int): CombatHudLayout {
    require(guiWidth > 0 && guiHeight > 0)

    val core = HudRect(
        x = guiWidth / 2 - 16,
        y = guiHeight - 92,
        width = 32,
        height = 26,
    )
    val health = HudRect(core.x - 8 - 106, core.y + 4, 106, 18)
    val resource = HudRect(core.right + 8, core.y + 4, 106, 18)
    val skills = HudRect(core.x - 12 - 113, guiHeight - 45, 113, 28)
    val hotbar = HudRect(core.right + 12, guiHeight - 42, 196, 22)
    val offhand = HudRect(hotbar.right + 2, hotbar.y, 20, 22)

    return CombatHudLayout(health, core, resource, skills, hotbar, offhand)
}
