package dev.projects.server.coreloop.ui

/**
 * The only global overrides: blank the vanilla HUD parts the combat HUD replaces (hearts, food,
 * armour row, XP bar) and the pink boss bar, whose name component draws the boss bar; and reskin
 * the hotbar. Mirrors build_combat_hud.vanilla_overrides() + vanilla_reskins().
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
        // Reskinned, not blanked: the hotbar and its selection frame in the HUD's charcoal and iron (2026-10-16).
        add("${HUD}hotbar.png"); add("${HUD}hotbar_selection.png")
    }

    /** Vanilla button and text-field frames in the ProjectS style, so server dialogs match the game (asked 2026-10-07). */
    val dialogWidgets: Set<String> = buildSet {
        for (name in listOf("button", "button_highlighted", "button_disabled", "text_field", "text_field_highlighted")) {
            add("assets/minecraft/textures/gui/sprites/widget/$name.png"); add("assets/minecraft/textures/gui/sprites/widget/$name.png.mcmeta")
        }
    }

    /**
     * Combat VFX shader, one core/item for both experiments: texels carrying the warrior heat/glow alpha markers
     * (scripts/build_warrior_iron_vfx.py) and items tinted with the 黒翳竜 VFX marker (scripts/build_boss_vfx.py).
     * Every other item renders as vanilla. The include holds the greatsword swing's keys (scripts/class_armament_swing.py),
     * which item.vsh plays on the first-person held sword.
     */
    val shaderOverrides: Set<String> = setOf("assets/minecraft/shaders/core/item.vsh", "assets/minecraft/shaders/core/item.fsh",
        "assets/minecraft/shaders/include/greatsword_swing.glsl")

    fun allowedPath(path: String): Boolean = !path.startsWith('/') && ".." !in path && '\\' !in path &&
        (!path.startsWith("assets/minecraft/") || path in vanillaOverrides || path in vanillaAdditions || path in dialogWidgets ||
            path in shaderOverrides)
}
