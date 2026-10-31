package dev.projects.server.coreloop

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.timer.TaskSchedule

/**
 * The display entities the mage's spells draw with (CoreMageBolt, CoreMageFire, CoreMageFrost): one bright item
 * display, shown only to the packed players near it; players without the pack get vanilla dust ([fallback]).
 *
 * Every display also goes by itself, so nothing is left standing if its spell stops ticking (the actor replaced):
 * one with a [life] a few ticks after it, one without (a burning or frozen enemy's flames and ice, kept as long as the
 * state lasts) once [alive] has said no for a second.
 */
internal object CoreMageDisplay {
    const val VIEW = 48.0

    fun viewers(instance: Instance, at: Point) =
        instance.players.filter { CoreCombatPresentation.packed(it) && it.position.distanceSquared(at) < VIEW * VIEW }

    /** A new display at [at], or null when nobody near has the pack. [wanted] is checked once it is in the world. */
    fun spawn(owner: Player, at: Pos, item: ItemStack, scale: Double, billboard: Boolean, life: Int?,
              alive: () -> Boolean, wanted: () -> Boolean): Entity? {
        val instance = owner.instance ?: return null
        if (viewers(instance, at).isEmpty()) return null
        val entity = Entity(EntityType.ITEM_DISPLAY)
        entity.setHasPhysics(false); entity.setNoGravity(true); entity.setAutoViewable(false)
        entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(item)
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            if (billboard) {
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                meta.setRightRotation(CoreCombatMeshArt.vanillaItemCorrection)
            }
            meta.setPosRotInterpolationDuration(1)
            meta.setBrightness(15, 15); meta.setViewRange(1.0f)
            meta.setScale(Vec(scale, scale, scale))
        }
        entity.setInstance(instance, at).whenComplete { _, failure ->
            if (failure != null || owner.instance !== instance || !wanted()) entity.remove()
            else viewers(instance, at).forEach(entity::addViewer)
        }
        if (life != null) entity.scheduleRemove(java.time.Duration.ofMillis(50L * (life + 4)))
        else entity.scheduler().buildTask { if (!alive()) entity.remove() }.delay(TaskSchedule.tick(20)).repeat(TaskSchedule.tick(20)).schedule()
        return entity
    }

    fun fallback(owner: Player, at: Point, packet: ParticlePacket) {
        val instance = owner.instance ?: return
        instance.players.filter { !CoreCombatPresentation.packed(it) && it.position.distanceSquared(at) < VIEW * VIEW }.forEach { it.sendPacket(packet) }
    }

    /** The clock a spell's [alive] reads: [touch] it every tick, and [fresh] stays true until a second after the last. */
    class Heartbeat {
        private var at = System.currentTimeMillis()
        fun touch() { at = System.currentTimeMillis() }
        fun fresh() = System.currentTimeMillis() - at < 1000
    }
}
