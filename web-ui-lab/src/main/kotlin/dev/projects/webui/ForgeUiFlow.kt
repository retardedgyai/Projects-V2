package dev.projects.webui

/** Server-owned state behind the approved forge display. Input contains action IDs only. */
interface ForgeUiFlow {
    val muted: Boolean
    val view: String
    val operationActive: Boolean
    /** True when [scene] already animates the forge, so the Polish05 ember overlay must stay off. */
    val ownsEffects: Boolean get() = false
    fun scene(light: ForgeLightPhase = ForgeLightPhase.IDLE): UiScene
    fun action(action: String): Boolean
    fun takeStrike(nowMs: Long = System.currentTimeMillis()): Boolean = false
    fun tick(nowMs: Long = System.currentTimeMillis()): ForgeUiReceipt? = null
    /** The node under the pointer changed; flows with world-space markers can react. */
    fun hover(id: String?) {}
    /** True when the scene must be rebuilt every tick (world state changes outside clicks). */
    val live: Boolean get() = false
    /** The session closed; remove anything the flow spawned itself. */
    fun dispose() {}
}

data class ForgeUiReceipt(val success: Boolean, val refined: Boolean = false)
