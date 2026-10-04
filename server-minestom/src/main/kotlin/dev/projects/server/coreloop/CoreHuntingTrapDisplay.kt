package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.instance.block.Block
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/** Small vanilla block fixture: pressure plate, four jaws, eight footprint pegs. No new pack. */
internal class CoreHuntingTrapDisplay(private val owner: Player, private val trap: CoreHuntingTrap) {
    private val instance = owner.instance
    private val cancelled = AtomicBoolean()
    private val pieces = mutableListOf<Entity>()
    private val jaws = mutableListOf<Pair<Entity, Vec>>()
    internal val size get() = pieces.size
    init {
        part(Block.SPRUCE_PRESSURE_PLATE, Vec(-.45, .02, -.45), Vec(.9, .16, .9))
        for (n in 0..3) {
            val angle = n * PI / 2
            val offset = Vec(cos(angle) * .62 - .09, .03, sin(angle) * .62 - .45)
            val entity = part(Block.DEEPSLATE_BRICKS, offset, Vec(.18, .18, .9), angle)
            jaws += entity to offset
        }
        for (n in 0..7) {
            val angle = n * PI / 4
            part(Block.MOSS_BLOCK, Vec(cos(angle) * trap.radius - .09, .02, sin(angle) * trap.radius - .09), Vec(.18, .08, .18))
        }
    }
    private fun part(block: Block, offset: Vec, scale: Vec, yaw: Double = 0.0): Entity {
        val entity = Entity(EntityType.BLOCK_DISPLAY)
        entity.setHasPhysics(false); entity.setNoGravity(true)
        entity.editEntityMeta(BlockDisplayMeta::class.java) {
            it.setBlockState(block); it.setScale(scale); it.setTranslation(offset)
            it.setLeftRotation(CoreCombatMeshArt.rotation(yaw, 0.0, 0.0))
            it.setBrightness(12, 12); it.setViewRange(1f); it.setTransformationInterpolationDuration(2)
        }
        pieces += entity
        entity.setInstance(instance, trap.origin).whenComplete { _, failure ->
            if (failure != null || cancelled.get() || owner.instance !== instance || owner.isRemoved) entity.remove()
        }
        return entity
    }
    fun tick(now: Long) {
        if (cancelled.get()) return
        val triggered = trap.triggeredAt ?: return
        val snap = ((now - triggered) / 3.0).coerceIn(0.0, 1.0)
        jaws.forEach { (entity, offset) ->
            if (!entity.isRemoved) entity.editEntityMeta(BlockDisplayMeta::class.java) {
                it.setTransformationInterpolationStartDelta(0)
                it.setTranslation(Vec(offset.x() * (1 - snap * .4), offset.y(), offset.z() * (1 - snap * .4)))
                it.setScale(Vec(.18, .18 + snap * .5, .9))
            }
        }
    }
    fun close() { cancelled.set(true); pieces.forEach(Entity::remove); pieces.clear(); jaws.clear() }
}
