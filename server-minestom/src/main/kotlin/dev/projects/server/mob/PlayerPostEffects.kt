package dev.projects.server.mob

import net.kyori.adventure.key.Key
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.common.PostEffectsPacket
import java.util.UUID

/**
 * The one owner of each player's post-effect list. A PostEffectsPacket replaces the whole list, so the boss arena's glow
 * and light (QuestEncounterCombat), a mage's glow and coloured lights (coreloop/CoreMageLight) and /lighttest each name
 * what they want under their own [source], and the player gets the union, sent only when it changes.
 * The union is in [ORDER]: the coloured light first (it lights the scene), the glows after it.
 */
internal object PlayerPostEffects {
    private val wants = HashMap<UUID, MutableMap<String, List<Key>>>()
    private val sent = HashMap<UUID, List<Key>>()

    fun set(player: Player, source: String, effects: List<Key>) {
        val mine = wants.getOrPut(player.uuid) { HashMap() }
        if (effects.isEmpty()) mine.remove(source) else mine[source] = effects
        val union = mine.values.flatten().distinct().sortedBy { ORDER.indexOf(it).let { i -> if (i < 0) ORDER.size else i } }
        if (mine.isEmpty()) wants.remove(player.uuid)
        if ((sent[player.uuid] ?: emptyList()) == union) return
        if (union.isEmpty()) sent.remove(player.uuid) else sent[player.uuid] = union
        player.sendPacket(PostEffectsPacket(union))
    }

    fun clear(player: Player, source: String) = set(player, source, emptyList())

    /** The player left: a new connection starts with no post effects on. */
    fun forget(player: Player) { wants.remove(player.uuid); sent.remove(player.uuid) }

    /** What [player] has on now (for tests). */
    fun current(player: Player): List<Key> = sent[player.uuid] ?: emptyList()

    private val ORDER = listOf(SakuraLight.POST, Key.key("projects", "shade_glow"), Key.key("projects", "mage_glow"))
}
