package dev.projects.server.mob

import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import java.util.UUID

/**
 * Overhead mob HUD: one text display riding each mob. A passenger follows its mob on the client (no per-tick
 * teleports, so it never trails or flickers) and a change is one metadata packet; lines are separate vanilla
 * text lines, so the name and MODs stay in the game's own font while gauges and icons are pack glyphs.
 */
internal class MobPlates {
    private class Plate(val display: Entity, var text: Component)

    private val plates = mutableMapOf<UUID, Plate>()

    val count: Int get() = plates.size

    fun show(carrier: Entity, text: Component) {
        val instance = carrier.instance ?: return
        plates[carrier.uuid]?.let { plate ->
            if (plate.display.isRemoved) plates.remove(carrier.uuid) else {
                if (plate.text != text) {
                    plate.text = text
                    plate.display.editEntityMeta(TextDisplayMeta::class.java) { it.text = text }
                }
                return
            }
        }
        val display = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true); setHasPhysics(false)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.text = text
                meta.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
                meta.backgroundColor = 0
                meta.isShadow = true
                meta.lineWidth = 400
                meta.viewRange = 0.5f
                meta.translation = Vec(0.0, PLATE_LIFT, 0.0)
            }
        }
        plates[carrier.uuid] = Plate(display, text)
        display.setInstance(instance, carrier.position).thenRun {
            if (carrier.isRemoved || plates[carrier.uuid]?.display !== display) display.remove() else carrier.addPassenger(display)
        }
    }

    fun hide(carrierId: UUID) {
        plates.remove(carrierId)?.display?.remove()
    }

    /** Drop plates whose mob is gone. */
    fun retain(alive: Set<UUID>) {
        plates.keys.filter { it !in alive }.forEach(::hide)
    }

    fun dispose() {
        plates.values.forEach { it.display.remove() }
        plates.clear()
    }

    companion object {
        /** Blocks above the mob's passenger point, so the lowest line clears the head. */
        const val PLATE_LIFT = 0.15
    }
}
