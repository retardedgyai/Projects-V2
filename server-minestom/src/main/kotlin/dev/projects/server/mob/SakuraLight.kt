package dev.projects.server.mob

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData

/**
 * A coloured point light (prototype, 2026-10-20): the ground, blocks and mobs within [radius] blocks take its colour,
 * fading with distance. The light is an invisible probe item (scripts/build_boss_vfx.py `light_probe`): the pack's
 * core/item shader writes its position into the screen's bottom pixel row and the `projects:sakura_light` post effect
 * (scripts/shaders/post/sakura_light.fsh) lights the scene from it. No shadows. Up to 8 lights on screen, one per
 * [slot]; two lights in one slot overwrite each other. Teleport it, or ride it on a mob, to make it move.
 *
 * Only players with the pack and the post effect on ([enable]) should see the probe: without the post effect it shows
 * as a thin line of coloured pixels at the screen's bottom-left.
 */
internal class SakuraLight(instance: Instance, at: Pos, slot: Int, radius: Int = 8, strength: Double = 1.0,
                           palette: Int = PINK, viewable: (Player) -> Boolean = { true }) {
    /** The entity stands [lift] blocks under the light and its model is raised back up by the same: the client culls a
     *  display by its box (width x height from the entity's feet up), and a box the size of the light's reach keeps the
     *  probe drawn while any lit ground is on screen. A zero box drops it whenever the light's own point leaves the
     *  screen (the camera close to the boss), and the light blinks off for those frames. */
    private var lift = radius.toDouble()

    val entity = Entity(EntityType.ITEM_DISPLAY).apply {
        setNoGravity(true)
        setHasPhysics(false)
        updateViewableRule { viewable(it) }
        editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE)
            meta.setItemStack(stack(slot, radius, strength, palette))
            meta.setViewRange(4f)
            meta.setShadowRadius(0f)
            meta.setPosRotInterpolationDuration(2)
            reach(meta, radius)
        }
        setInstance(instance, at.sub(0.0, lift, 0.0))
    }

    fun moveTo(at: Pos) = entity.teleport(at.sub(0.0, lift, 0.0))

    fun set(slot: Int, radius: Int, strength: Double, palette: Int = PINK) {
        val at = entity.position.add(0.0, lift, 0.0)
        entity.editEntityMeta(ItemDisplayMeta::class.java) {
            it.setItemStack(stack(slot, radius, strength, palette))
            reach(it, radius)
        }
        lift = radius.toDouble()
        moveTo(at)
    }

    private fun reach(meta: ItemDisplayMeta, radius: Int) {
        meta.setTranslation(Vec(0.0, radius.toDouble(), 0.0))
        meta.setWidth(radius * 2f)
        meta.setHeight(radius * 2f)
    }

    fun remove() = entity.remove()

    companion object {
        val POST: Key = Key.key("projects", "sakura_light")
        const val PINK = 0
        const val VIOLET = 1
        const val EMBER = 2
        const val FROST = 3

        /** Tint: blue 80 + palette is the probe marker, green = slot * 16 + (radius - 2), red = strength / 3. */
        private fun stack(slot: Int, radius: Int, strength: Double, palette: Int): ItemStack {
            require(slot in 0..7 && radius in 2..17 && palette in 0..3)
            val tint = TextColor.color((strength / 3.0 * 255).toInt().coerceIn(0, 255), slot * 16 + radius - 2, 80 + palette)
            return ItemStack.of(Material.PAPER).withItemModel("projects:boss_vfx/light_probe")
                .with(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(emptyList(), emptyList(), emptyList(), listOf(tint)))
        }

        /** /lighttest's own switch; it shares the player's list with the arena and the mage (PlayerPostEffects). */
        fun enable(player: Player) = PlayerPostEffects.set(player, "lighttest", listOf(POST))

        fun disable(player: Player) = PlayerPostEffects.clear(player, "lighttest")
    }
}
