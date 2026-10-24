package dev.projects.server.coreloop

import com.google.gson.JsonParser
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityPose
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * The greatsword's first-person swing drawn by an item display (2026-10-25, Creator: the held-item frames snap once a
 * tick and the puppet's displays were smooth). The display rides the player and is billboarded to the camera, so its
 * local space is the view space. Only the owner sees it; while it swings the held sword is hidden in first person only
 * (others and F5 still see that one).
 *
 * The swing is a few key poses (scripts/class_armament_swing.py, player-motions/greatsword_viewmodel.json): each is
 * sent once, with how many ticks the client is to take reaching it, so the client draws the move smoothly whatever the
 * tick timing (a pose a tick stalled and jumped). The item is the weapon's _vm model, which turns about the hand on
 * the handle, so a key is where the hand is and which way the blade points. Cosmetic only: never touches combat.
 */
internal object GreatswordViewmodel {
    /** The held weapon's custom_model_data[0] that hides it in first person (class_armament_swing.HIDDEN). */
    const val HIDDEN_POSE = 112
    // Test-only: with dev commands on, the frames are read from the source tree whenever that file changes, so a new
    // motion from scripts/class_armament_swing.py shows on the next swing without a restart.
    private val devFile = java.io.File("../src/main/resources/player-motions/greatsword_viewmodel.json")
        .takeIf { java.lang.Boolean.getBoolean("projects.devCommands") && it.exists() }
    private var loadedAt = -1L
    private var cached: Map<String, Map<String, List<FloatArray>>> = emptyMap()
    private val frames: Map<String, Map<String, List<FloatArray>>> get() {
        val stamp = devFile?.lastModified() ?: 0L
        if (stamp != loadedAt) {
            loadedAt = stamp
            val text = devFile?.readText() ?: GreatswordViewmodel::class.java.classLoader
                .getResourceAsStream("player-motions/greatsword_viewmodel.json")?.use { String(it.readAllBytes(), Charsets.UTF_8) }
            cached = if (text == null) emptyMap() else parse(text)
        }
        return cached
    }

    private fun parse(text: String) = JsonParser.parseString(text).asJsonObject.entrySet().associate { (model, clips) ->
        model to clips.asJsonObject.entrySet().associate { (clip, list) ->
            clip to list.asJsonArray.map { f -> f.asJsonArray.map { it.asFloat }.toFloatArray() }
        }
    }

    private class Active(val display: Entity, val model: String, val weapon: ItemStack, var keys: List<FloatArray>,
        var started: Long, var duration: Int, var next: Int = 1)
    private val active = HashMap<UUID, Active>()

    fun playing(player: Player): Boolean = active.containsKey(player.uuid)

    /** Plays [beat] over [ticks] on the viewmodel; false (nothing shown) for anything but a held greatsword. */
    fun swing(player: Player, beat: Int, ticks: Int): Boolean {
        val instance = player.instance ?: return false
        val item = player.itemInMainHand
        val model = item.get(DataComponents.ITEM_MODEL) ?: return false
        val clips = frames[model] ?: return false
        val keys = clips["swing$beat"] ?: return false
        if (CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return false
        val current = active[player.uuid]?.takeIf { it.model == model && !it.display.isRemoved }
        val state = current ?: run {
            stop(player)
            val display = Entity(EntityType.ITEM_DISPLAY).apply {
                setNoGravity(true)
                setHasPhysics(false)
                updateViewableRule { it === player }
                editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel("${model}_vm"))
                    meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setBrightnessOverride(15 shl 4 or (15 shl 20))
                    meta.setShadowRadius(0f)
                    meta.setViewRange(4f)
                }
            }
            Active(display, model, CoreArmamentPresentation.frame(item, 0), keys, player.aliveTicks, ticks)
        }
        state.keys = keys; state.started = player.aliveTicks; state.duration = ticks.coerceIn(2, 400); state.next = 1
        if (current == null) {
            // Spawned already in the held sword's resting pose, then moved on from there.
            send(player, state, keys[0], 0)
            state.display.setInstance(instance, player.position)
            player.addPassenger(state.display)
            active[player.uuid] = state
        }
        advance(player, state)
        return true
    }

    /** Once a tick: sends the keys that are due; at the end the held sword shows again (the last key is its pose). */
    fun tick(player: Player) {
        val state = active[player.uuid] ?: return
        if (state.display.isRemoved || player.instance == null || state.display.instance !== player.instance ||
            CoreArmamentPresentation.frame(player.itemInMainHand, 0) != state.weapon) { stop(player); return }
        if (player.aliveTicks - state.started >= state.duration) { stop(player); return }
        advance(player, state)
    }

    fun stop(player: Player) {
        val state = active.remove(player.uuid) ?: return
        state.display.remove()
        val item = player.itemInMainHand
        if (CoreArmamentPresentation.frame(item, 0) == state.weapon) player.setItemInMainHand(CoreArmamentPresentation.frame(item, 0))
    }

    /** Key k is sent when key k-1's time comes, to be reached by key k's time. */
    private fun advance(player: Player, state: Active) {
        val elapsed = player.aliveTicks - state.started
        val at = { k: Int -> Math.round(state.keys[k][0] * state.duration).toLong() }
        while (state.next < state.keys.size && at(state.next - 1) <= elapsed) {
            send(player, state, state.keys[state.next], (at(state.next) - at(state.next - 1)).toInt().coerceAtLeast(1))
            state.next++
        }
        val hidden = CoreArmamentPresentation.pose(player.itemInMainHand, HIDDEN_POSE)
        if (hidden != player.itemInMainHand) player.setItemInMainHand(hidden)
    }

    private fun send(player: Player, state: Active, k: FloatArray, ticks: Int) {
        // The display sits on the passenger point above the eye; in view space that offset tilts with the pitch.
        val sneaking = player.pose == EntityPose.SNEAKING
        val above = (if (sneaking) 1.5 else 1.8) - player.eyeHeight
        val pitch = Math.toRadians(player.position.pitch().toDouble())
        state.display.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setTransformationInterpolationStartDelta(0)
            meta.setTransformationInterpolationDuration(ticks)
            meta.setTranslation(Vec(k[1].toDouble(), k[2] - above * cos(pitch), k[3] - above * sin(pitch)))
            meta.setLeftRotation(floatArrayOf(k[4], k[5], k[6], k[7]))
            meta.setScale(Vec(k[8].toDouble(), k[8].toDouble(), k[8].toDouble()))
        }
    }
}
