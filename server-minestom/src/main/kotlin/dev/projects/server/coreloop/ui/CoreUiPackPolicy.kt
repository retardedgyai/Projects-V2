package dev.projects.server.coreloop.ui

/**
 * The only global overrides: blank the vanilla HUD parts the pixel HUD replaces (hearts, food,
 * armour row, XP bar) and the pink boss bar, whose name component draws the pixel boss bar.
 * Mirrors build_combat_hud.vanilla_overrides().
 */
internal object CoreUiPackPolicy {
    // Appends the custom VFX directory to the native atlas, without replacing sprites.
    val vanillaAdditions: Set<String> = setOf("assets/minecraft/atlases/items.json")
    private const val HUD = "assets/minecraft/textures/gui/sprites/hud/"
    private const val BOSS = "assets/minecraft/textures/gui/sprites/boss_bar/"
    val vanillaOverrides: Set<String> = buildSet {
        for (type in listOf("", "absorbing_", "frozen_", "poisoned_", "withered_")) {
            for (hardcore in listOf("", "hardcore_")) {
                for (fill in listOf("full", "half")) {
                    for (blink in listOf("", "_blinking")) add("${HUD}heart/$type$hardcore$fill$blink.png")
                }
            }
        }
        for (variant in listOf("container", "container_blinking", "container_hardcore", "container_hardcore_blinking")) {
            add("${HUD}heart/$variant.png")
        }
        for (fill in listOf("empty", "half", "full")) {
            for (hunger in listOf("", "_hunger")) add("${HUD}food_$fill$hunger.png")
            add("${HUD}armor_$fill.png")
        }
        add("${HUD}experience_bar_background.png"); add("${HUD}experience_bar_progress.png")
        add("${BOSS}pink_background.png"); add("${BOSS}pink_progress.png")
    }

    /** Vanilla button and text-field frames in the ProjectS style, so server dialogs match the game (asked 2026-10-07). */
    val dialogWidgets: Set<String> = buildSet {
        for (name in listOf("button", "button_highlighted", "button_disabled", "text_field", "text_field_highlighted")) {
            add("assets/minecraft/textures/gui/sprites/widget/$name.png"); add("assets/minecraft/textures/gui/sprites/widget/$name.png.mcmeta")
        }
    }

    fun allowedPath(path: String): Boolean = !path.startsWith('/') && ".." !in path && '\\' !in path &&
        (!path.startsWith("assets/minecraft/") || path in vanillaOverrides || path in vanillaAdditions || path in dialogWidgets)
}
