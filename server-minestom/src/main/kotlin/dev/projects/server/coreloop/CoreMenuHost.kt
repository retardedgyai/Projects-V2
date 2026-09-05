package dev.projects.server.coreloop

import dev.projects.server.coreloop.adventure.*
import dev.projects.server.questmap.QuestGatheringDiscipline
import dev.projects.server.questmap.QuestGatheringMastery
import dev.projects.server.questmap.QuestGatheringMasteryNode
import net.minestom.server.entity.Player
import java.util.UUID

/** Existing core-loop operations consumed by the real menu; the host retains all mutation authority. */
internal interface CoreMenuHost {
    fun account(player: Player): CoreAccount?
    fun market(): List<CoreMarketEntry> = emptyList()
    fun buyOrders(): List<CoreBuyOrderEntry> = emptyList()
    fun dungeonParties(): List<DungeonParty> = emptyList()
    fun dungeonView(player: Player): DungeonRunView? = null
    fun playerName(id: UUID): String = id.toString().take(8)
    fun dungeonLobby(player: Player, action: DungeonLobbyAction) {}
    fun dungeonBoon(player: Player, boon: DungeonBoon) {}
    fun dungeonRoute(player: Player, roomId: Int) {}
    fun packed(player: Player): Boolean
    fun isDeparting(player: Player): Boolean
    fun requireHub(player: Player): Boolean
    fun sessionSummary(player: Player): String
    fun warmMap(player: Player, map: CoreOwnedMap): Boolean
    fun mutate(player: Player, action: CoreAction, revision: Long, onRejected: (() -> Unit)? = null, after: () -> Unit = {})
    fun applyTablet(player: Player, mapId: UUID, revision: Long, onRejected: (() -> Unit)? = null, after: () -> Unit)
    fun depart(player: Player, mapId: UUID, revision: Long)
    fun returnToHarbor(player: Player)
    fun gatheringMastery(player: Player): QuestGatheringMastery
    fun unlockMastery(player: Player, discipline: QuestGatheringDiscipline, node: QuestGatheringMasteryNode)
    fun departTrial(player: Player, kind: CoreActivityKind, tier: Int, revision: Long)
}
