package dev.projects.server.coreloop.adventure

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block

/** Small physical altar plus readable Japanese label; interaction is deliberately larger than the art. */
internal class DungeonMarker(instance: InstanceContainer, val position: Pos, label: String, block: Block = Block.AMETHYST_BLOCK) {
    @Volatile private var disposed = false
    val interaction = Entity(EntityType.INTERACTION)
    private val art = Entity(EntityType.BLOCK_DISPLAY)
    private val text = Entity(EntityType.TEXT_DISPLAY)
    @Volatile var failed = false
        private set
    init {
        interaction.editEntityMeta(InteractionMeta::class.java) { it.width = 2.3f; it.height = 2.5f; it.response = true }
        art.editEntityMeta(BlockDisplayMeta::class.java) { it.setBlockState(block); it.setScale(Vec(.85, .85, .85)); it.setTranslation(Vec(-.425, 0.0, -.425)); it.setBrightness(15, 15) }
        text.editEntityMeta(TextDisplayMeta::class.java) {
            it.setText(Component.text(label, NamedTextColor.GOLD)); it.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
            it.setScale(Vec(.9, .9, .9)); it.setBackgroundColor(0x800B111E.toInt()); it.setShadow(true); it.setLineWidth(240)
        }
        listOf(interaction to position, art to position, text to position.add(0.0, 2.0, 0.0)).forEach { (e, p) ->
            e.setNoGravity(true); e.setHasPhysics(false)
            e.setInstance(instance, p).whenComplete { _, error -> if (error != null) failed = true; if (disposed) e.remove() }
        }
    }
    fun label(value: String, color: NamedTextColor = NamedTextColor.GOLD) { (text.entityMeta as TextDisplayMeta).setText(Component.text(value, color)) }
    fun dispose() { disposed = true; listOf(interaction, art, text).forEach { it.remove() } }
}
