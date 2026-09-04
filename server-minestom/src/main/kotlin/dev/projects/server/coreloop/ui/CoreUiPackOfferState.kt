package dev.projects.server.coreloop.ui

import net.kyori.adventure.resource.ResourcePackStatus
import java.util.UUID

/** One connection's one pack offer; deliberately remains receptive after a successful reload. */
internal class CoreUiPackOfferState(val packId: UUID = UUID.randomUUID()) {
    data class Change(val packId: UUID, val revision: Long, val loaded: Boolean)

    @Volatile var loaded: Boolean = false
        private set
    private var active = true
    private var revision = 0L
    private var lastStatus: ResourcePackStatus? = null

    @Synchronized
    fun accept(id: UUID, status: ResourcePackStatus): Change? {
        if (!active || id != packId || status.intermediate() || status == lastStatus) return null
        lastStatus = status
        // ACCEPTED/DOWNLOADED do not prove the client can render our private glyphs.
        loaded = status == ResourcePackStatus.SUCCESSFULLY_LOADED
        revision++
        return current()
    }

    @Synchronized
    fun current(): Change = Change(packId, revision, loaded)

    @Synchronized
    fun isCurrent(change: Change): Boolean = active && change == current()

    @Synchronized
    fun invalidate() {
        active = false
        loaded = false
        revision++
    }
}
