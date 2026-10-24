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
 * local space is the view space and the client interpolates its transformation between ticks. Only the owner sees it;
 * while it swings the held sword is hidden in first person only (others and F5 still see that one).
 *
 * Frames come from scripts/class_armament_swing.py (player-motions/greatsword_viewmodel.json): per tick a translation
 * from the eye, a rotation and a scale. Cosmetic only: never touches combat.
 */
internal object GreatswordViewmodel {
    /** The held weapon's custom_model_data[0] that hides it in first person (class_armament_swing.HIDDEN). */
    const val HIDDEN_POSE = 112
    private val frames: Map<String, Map<String, List<FloatArray>>> by lazy {
        val text = GreatswordViewmodel::class.java.classLoader.getResourceAsStream("player-motions/greatsword_viewmodel.json")
            ?.use { String(it.readAllBytes(), Charsets.UTF_8) } ?: return@lazy emptyMap()
        JsonParser.parseString(text).asJsonObject.entrySet().associate { (model, clips) ->
            model to clips.asJsonObject.entrySet().associate { (clip, list) ->
                clip to list.asJsonArray.map { f -> f.asJsonArray.map { it.asFloat }.toFloatArray() }
            }
        }
    }

    private class Active(val display: Entity, val model: String, val weapon: ItemStack, var clip: String,
        var started: Long, var duration: Int)
    private val active = HashMap<UUID, Active>()

    fun playing(player: Player): Boolean = active.containsKey(player.uuid)

    /** Plays [beat] over [ticks] on the viewmodel; false (nothing shown) for anything but a held greatsword. */
    fun swing(player: Player, beat: Int, ticks: Int): Boolean {
        val instance = player.instance ?: return false
        val item = player.itemInMainHand
        val model = item.get(DataComponents.ITEM_MODEL) ?: return false
        val clips = frames[model] ?: return false
        if (CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON || "swing$beat" !in clips) return false
        val current = active[player.uuid]
        val state = if (current != null && current.model == model && !current.display.isRemoved) current else {
            stop(player)
            val display = Entity(EntityType.ITEM_DISPLAY).apply {
                setNoGravity(true)
                setHasPhysics(false)
                updateViewableRule { it === player }
                editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel(model))
                    meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setBrightnessOverride(15 shl 4 or (15 shl 20))
                    meta.setShadowRadius(0f)
                    meta.setViewRange(4f)
                }
            }
            Active(display, model, CoreArmamentPresentation.frame(item, 0), "swing$beat", player.aliveTicks, ticks.coerceIn(2, 400))
        }
        state.clip = "swing$beat"; state.started = player.aliveTicks; state.duration = ticks.coerceIn(2, 400)
        update(player, state, first = state !== current)
        if (state !== current) {
            // Spawned with its first pose already set: a display that arrives untransformed fills the screen for a frame.
            state.display.setInstance(instance, player.position)
            player.addPassenger(state.display)
            active[player.uuid] = state
        }
        return true
    }

    /** Once a tick: the next frame; an unfollowed beat settles back to rest, then the held sword shows again. */
    fun tick(player: Player) {
        val state = active[player.uuid] ?: return
        if (state.display.isRemoved || player.instance == null || state.display.instance !== player.instance ||
            CoreArmamentPresentation.frame(player.itemInMainHand, 0) != state.weapon) { stop(player); return }
        // Each clip ends on the held pose, so the held sword takes over without a jump.
        if (player.aliveTicks - state.started >= state.duration) { stop(player); return }
        update(player, state, first = false)
    }

    fun stop(player: Player) {
        val state = active.remove(player.uuid) ?: return
        state.display.remove()
        val item = player.itemInMainHand
        if (CoreArmamentPresentation.frame(item, 0) == state.weapon) player.setItemInMainHand(CoreArmamentPresentation.frame(item, 0))
    }

    private fun update(player: Player, state: Active, first: Boolean) {
        val clip = frames.getValue(state.model).getValue(state.clip)
        val elapsed = (player.aliveTicks - state.started).coerceAtLeast(0)
        val index = (elapsed * (clip.size - 1) / (state.duration - 1).coerceAtLeast(1)).toInt().coerceIn(0, clip.size - 1)
        val f = clip[index]
        // The display sits on the passenger point above the eye; in view space that offset tilts with the pitch.
        val sneaking = player.pose == EntityPose.SNEAKING
        val above = (if (sneaking) 1.5 else 1.8) - player.eyeHeight
        val pitch = Math.toRadians(player.position.pitch().toDouble())
        state.display.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setTransformationInterpolationStartDelta(0)
            meta.setTransformationInterpolationDuration(if (first) 0 else 1)
            meta.setTranslation(Vec(f[0].toDouble(), f[1] - above * cos(pitch), f[2] - above * sin(pitch)))
            meta.setLeftRotation(floatArrayOf(f[3], f[4], f[5], f[6]))
            meta.setScale(Vec(f[7].toDouble(), f[7].toDouble(), f[7].toDouble()))
        }
        val hidden = CoreArmamentPresentation.pose(player.itemInMainHand, HIDDEN_POSE)
        if (hidden != player.itemInMainHand) player.setItemInMainHand(hidden)
    }
}
