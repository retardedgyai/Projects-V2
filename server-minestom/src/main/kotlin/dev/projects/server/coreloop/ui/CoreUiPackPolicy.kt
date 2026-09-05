package dev.projects.server.coreloop.ui

/** The only global overrides explicitly requested: hide player heart/food sprites for the new HUD. */
internal object CoreUiPackPolicy {
    private const val HUD = "assets/minecraft/textures/gui/sprites/hud/"
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
        }
    }

    fun allowedPath(path: String): Boolean = !path.startsWith('/') && ".." !in path && '\\' !in path &&
        (!path.startsWith("assets/minecraft/") || path in vanillaOverrides)
}
