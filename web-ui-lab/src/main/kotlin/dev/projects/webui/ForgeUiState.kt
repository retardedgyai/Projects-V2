package dev.projects.webui

/** Live server values to draw on the approved Polish05 forge art. No fixture balance crosses this boundary. */
data class ForgeUiGear(val id: String, val name: String, val tier: Int, val level: Int,
                       val power: Int, val nextPower: Int, val broken: Boolean = false,
                       val iconItem: String? = null)
data class ForgeUiMaterial(val name: String, val owned: Long, val required: Long, val icon: String)
data class ForgeUiState(
    val gears: List<ForgeUiGear>, val selected: String, val silver: Long,
    val chance: Double, val breakOnFailure: Double, val focused: Boolean,
    val materials: List<ForgeUiMaterial>, val catalystOwned: Long,
    val blockedReason: String?, val history: String?, val note: String,
    val modal: Boolean, val muted: Boolean, val busy: Boolean,
)
