package dev.projects.webui

/** Server-owned state behind the approved forge display. Input contains action IDs only. */
interface ForgeUiFlow {
    val muted: Boolean
    val view: String
    val operationActive: Boolean
    fun scene(light: ForgeLightPhase = ForgeLightPhase.IDLE): UiScene
    fun action(action: String): Boolean
    fun takeStrike(nowMs: Long = System.currentTimeMillis()): Boolean = false
    fun tick(nowMs: Long = System.currentTimeMillis()): ForgeUiReceipt? = null
}

data class ForgeUiReceipt(val success: Boolean, val refined: Boolean = false)
