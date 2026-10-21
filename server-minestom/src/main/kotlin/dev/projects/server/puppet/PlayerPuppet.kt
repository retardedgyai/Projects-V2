package dev.projects.server.puppet

import dev.projects.server.coreloop.CoreCombatPresentation
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket
import net.minestom.server.timer.TaskSchedule
import java.awt.image.BufferedImage
import java.util.UUID
import kotlin.math.PI

/**
 * While a motion plays, the player is hidden from everyone else and a puppet of item displays wearing their skin
 * plays the motion in their place (2026-10-19, docs/development/player-motion.md). The owner never sees their
 * own puppet: it would sit around their camera. Body parts are scripts/build_player_puppet.py's models with the
 * skin as custom model data colours; the weapons are the real weapon item models.
 *
 * Server-side only, no client mod. The puppet stands where the player stands, facing the way they faced when the
 * motion started. Driven by [tick] once per server tick; [stop] restores the player.
 */
internal class PlayerPuppet(private val owner: Player, private val motion: PlayerMotion, private val showOwner: Boolean = false) {
    private val displays = LinkedHashMap<String, Entity>()
    private val yaw = -owner.position.yaw() * PI / 180
    private var age = 0
    var finished = false; private set

    fun start() {
        val instance = owner.instance ?: return
        val skin = PuppetSkin.mannequin
        for ((part, layout) in PuppetSkin.parts) displays[part] = spawn(instance, bodyItem(layout.model, PuppetSkin.colors(skin, part)))
        val weaponItem = ItemStack.of(Material.PAPER).withItemModel("projects:${motion.weapon.model}")
        displays["weapon_r"] = spawn(instance, weaponItem)
        if (motion.weapon.dual) displays["weapon_l"] = spawn(instance, weaponItem)
        // The real skin arrives later the first time; repaint the parts then.
        PuppetSkin.skin(owner).thenAccept { real -> if (real != null) owner.scheduler().scheduleNextTick { paint(real) } }
        owner.isInvisible = true
        hideEquipment()
        update()
    }

    private fun paint(skin: BufferedImage) {
        if (finished) return
        for ((part, layout) in PuppetSkin.parts) displays[part]?.editEntityMeta(ItemDisplayMeta::class.java) {
            it.setItemStack(bodyItem(layout.model, PuppetSkin.colors(skin, part)))
        }
    }

    fun tick() {
        if (finished) return
        if (owner.isRemoved || owner.instance == null || displays.values.any { it.instance !== owner.instance }) { stop(); return }
        age++
        if (age / 20.0 > motion.length) { stop(); return }
        update()
        hideEquipment()  // the held weapon's pose clip re-sends equipment; keep it hidden from the others
    }

    fun stop() {
        if (finished) return
        finished = true
        displays.values.forEach { it.remove() }
        displays.clear()
        if (!owner.isRemoved) {
            owner.isInvisible = false
            owner.sendPacketToViewers(owner.equipmentsPacket)
        }
    }

    private fun update() {
        val frames = motion.pose(age / 20.0)
        val turn = M4.yaw(yaw)
        val at = owner.position
        for ((part, entity) in displays) {
            val frame = frames[part] ?: continue
            val (translation, rotation, scale) = if (part.startsWith("weapon")) weapon(frame) else body(part, frame)
            entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.setTransformationInterpolationStartDelta(0)
                meta.setTransformationInterpolationDuration(if (age == 0) 0 else 1)
                meta.setTranslation(Vec.fromPoint(M4.apply3(turn, translation).let { Vec(it[0], it[1], it[2]) }))
                meta.setLeftRotation(M4.quaternion(M4.mul3(turn, rotation)))
                meta.setScale(Vec(scale, scale, scale))
            }
            if (entity.position.distanceSquared(at) > 1e-6) entity.teleport(Pos(at.x(), at.y(), at.z()))
        }
    }

    /** Body part: the model's centre (8, 8, 8) is the part's anchor, offset from the bone pivot in skin pixels. */
    private fun body(part: String, frame: M4): Triple<DoubleArray, DoubleArray, Double> {
        val a = PuppetSkin.parts.getValue(part).anchor
        val k = PlayerMotion.K
        return Triple(frame.apply(doubleArrayOf(a[0] * k, a[1] * k, a[2] * k)), frame.rotation, .9375)
    }

    /** Weapon: grip at the hand, blade along the bone's +z, edge-to-edge along its y (the item model's blade is +y). */
    private fun weapon(frame: M4): Triple<DoubleArray, DoubleArray, Double> {
        val w = motion.weapon
        val r = M4.mul3(M4.mul3(frame.rotation, M4.rotX(PI / 2)), M4.rotY(PI / 2))
        val grip = M4.apply3(r, doubleArrayOf(0.0, (8 - w.grip) / 16 * w.scale, 0.0))
        val o = frame.origin
        return Triple(doubleArrayOf(o[0] + grip[0], o[1] + grip[1], o[2] + grip[2]), r, w.scale)
    }

    private fun spawn(instance: net.minestom.server.instance.Instance, item: ItemStack): Entity = Entity(EntityType.ITEM_DISPLAY).apply {
        setNoGravity(true)
        setHasPhysics(false)
        // showOwner: dev only (/motion <name> self), to check the puppet alone in F5.
        updateViewableRule { (showOwner || it !== owner) && CoreCombatPresentation.packed(it) }
        editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
            meta.setItemStack(item)
            meta.setViewRange(2f)
            meta.setShadowRadius(0f)
            meta.setPosRotInterpolationDuration(1)
        }
        setInstance(instance, Pos(owner.position.x(), owner.position.y(), owner.position.z()))
    }

    private fun bodyItem(model: String, colors: List<Int>): ItemStack = ItemStack.of(Material.PAPER).withItemModel(model)
        .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(), colors.map { TextColor.color(it) }))

    private fun hideEquipment() {
        owner.sendPacketToViewers(EntityEquipmentPacket(owner.entityId, EquipmentSlot.entries.associateWith { ItemStack.AIR }))
    }

    companion object {
        private val active = HashMap<UUID, PlayerPuppet>()

        /** Plays [name] on [player], replacing any motion already playing. False if the motion does not exist. */
        fun play(player: Player, name: String, showOwner: Boolean = false): Boolean {
            val motion = PlayerMotion.load(name) ?: return false
            active.remove(player.uuid)?.stop()
            val puppet = PlayerPuppet(player, motion, showOwner)
            active[player.uuid] = puppet
            puppet.start()
            player.scheduler().submitTask {
                puppet.tick()
                if (puppet.finished) { active.remove(player.uuid, puppet); TaskSchedule.stop() } else TaskSchedule.nextTick()
            }
            return true
        }

        fun stop(player: Player) { active.remove(player.uuid)?.stop() }
    }
}
