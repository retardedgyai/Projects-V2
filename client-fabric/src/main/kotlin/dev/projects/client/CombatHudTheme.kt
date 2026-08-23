package dev.projects.client

import net.minecraft.resources.Identifier

internal data class HudTexture(
    val id: Identifier,
    val width: Int,
    val height: Int,
)

internal enum class CombatHudTheme(
    val resourceLabel: String,
    val labelColor: Int,
    val cooldownColor: Int,
    val hpBackground: HudTexture,
    val hpFrame: HudTexture,
    val hpFill: HudTexture,
    val resourceBackground: HudTexture,
    val resourceFrame: HudTexture,
    val resourceFill: HudTexture,
    val core: HudTexture,
    val skillSlotFrame: HudTexture,
    val skillIcons: List<HudTexture>,
    val hotbarFrame: HudTexture,
    val selectedSlot: HudTexture,
    val offhandFrame: HudTexture,
    val storedPanelFrame: HudTexture?,
) {
    TWIN_BLADES(
        resourceLabel = "MANA",
        labelColor = 0xFFFFE8D0.toInt(),
        cooldownColor = 0xFFFFFFFF.toInt(),
        hpBackground = hudTexture("twin_blades/hp_background", 160, 24),
        hpFrame = hudTexture("twin_blades/hp_frame", 160, 24),
        hpFill = hudTexture("twin_blades/hp_fill", 160, 24),
        resourceBackground = hudTexture("twin_blades/resource_background", 160, 24),
        resourceFrame = hudTexture("twin_blades/resource_frame", 160, 24),
        resourceFill = hudTexture("twin_blades/resource_fill", 160, 24),
        core = hudTexture("twin_blades/core", 56, 56),
        skillSlotFrame = hudTexture("twin_blades/skill_slot", 38, 38),
        skillIcons = (1..4).map { hudTexture("twin_blades/skill_$it", 26, 26) },
        hotbarFrame = hudTexture("twin_blades/hotbar_frame", 224, 34),
        selectedSlot = hudTexture("twin_blades/selected_slot", 22, 22),
        offhandFrame = hudTexture("twin_blades/offhand_frame", 34, 34),
        storedPanelFrame = null,
    ),
    STARWEAVER(
        resourceLabel = "AETHER",
        labelColor = 0xFFE8F5FF.toInt(),
        cooldownColor = 0xFFFFFFFF.toInt(),
        hpBackground = hudTexture("starweaver/hp_background", 160, 24),
        hpFrame = hudTexture("starweaver/hp_frame", 160, 24),
        hpFill = hudTexture("starweaver/hp_fill", 160, 24),
        resourceBackground = hudTexture("starweaver/resource_background", 160, 24),
        resourceFrame = hudTexture("starweaver/resource_frame", 160, 24),
        resourceFill = hudTexture("starweaver/resource_fill", 160, 24),
        core = hudTexture("starweaver/core", 56, 56),
        skillSlotFrame = hudTexture("starweaver/skill_slot", 38, 38),
        skillIcons = (1..4).map { hudTexture("starweaver/skill_$it", 26, 26) },
        hotbarFrame = hudTexture("starweaver/hotbar_frame", 224, 34),
        selectedSlot = hudTexture("starweaver/selected_slot", 22, 22),
        offhandFrame = hudTexture("starweaver/offhand_frame", 34, 34),
        storedPanelFrame = hudTexture("starweaver/stored_panel", 96, 28),
    ),
}

private fun hudTexture(path: String, width: Int, height: Int): HudTexture = HudTexture(
    id = Identifier.fromNamespaceAndPath("projects", "textures/gui/combat_hud/$path.png"),
    width = width,
    height = height,
)
