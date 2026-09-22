package dev.projects.server.mob

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import kotlin.math.*

/** Original articulated voxel sculpture. Native block textures also work with the pack declined. */
internal class WardenModel(private val instance: InstanceContainer, private val type: QuestMobArchetype, origin: Pos) {
    data class Part(val at: Vec, val size: Vec, val block: Block, val joint: Int = 0)
    companion object {
        fun supports(type: QuestMobArchetype) = type in setOf(QuestMobArchetype.IRON_WARDEN, QuestMobArchetype.FORGE_SENTINEL, QuestMobArchetype.GLACIAL_COLOSSUS)
        fun parts(type: QuestMobArchetype): List<Part> {
            val shell = when (type) { QuestMobArchetype.GLACIAL_COLOSSUS -> Block.BLUE_ICE; QuestMobArchetype.FORGE_SENTINEL -> Block.WAXED_CUT_COPPER; else -> Block.POLISHED_DEEPSLATE }
            val trim = if (type == QuestMobArchetype.GLACIAL_COLOSSUS) Block.QUARTZ_BLOCK else Block.GOLD_BLOCK
            val heart = if (type == QuestMobArchetype.FORGE_SENTINEL) Block.SHROOMLIGHT else Block.SEA_LANTERN
            fun p(x: Double, y: Double, z: Double, w: Double, h: Double, d: Double, b: Block, joint: Int = 0) = Part(Vec(x,y,z),Vec(w,h,d),b,joint)
            return listOf(
                p(0.0,1.85,0.0,1.1,1.2,.75,shell), p(0.0,2.1,.42,.42,.5,.18,heart),
                p(0.0,1.35,0.0,1.25,.22,.85,trim), p(0.0,2.5,0.0,1.4,.25,.9,trim),
                p(0.0,2.95,0.0,.78,.7,.72,shell), p(0.0,2.95,.38,.6,.12,.08,heart),
                p(-.44,3.35,0.0,.15,.65,.18,trim), p(.44,3.35,0.0,.15,.65,.18,trim),
                p(-.98,2.5,0.0,.68,.58,.9,shell,-1), p(.98,2.5,0.0,.68,.58,.9,shell,1),
                p(-1.03,1.85,0.0,.42,.7,.48,trim,-1), p(1.03,1.85,0.0,.42,.7,.48,trim,1),
                p(-1.06,1.3,.12,.6,.55,.6,shell,-1), p(1.06,1.3,.12,.6,.55,.6,shell,1),
                p(-.4,.78,0.0,.4,.9,.5,shell,2), p(.4,.78,0.0,.4,.9,.5,shell,-2),
                p(-.4,.22,.15,.58,.4,.85,trim,2), p(.4,.22,.15,.58,.4,.85,trim,-2),
                p(1.12,.82,.35,.18,1.6,.18,trim,1), p(1.12,1.55,.35,1.1,.55,.65,shell,1),
                p(-1.06,1.72,.44,.76,1.1,.18,shell,-1), p(-1.06,1.72,.55,.15,.85,.1,heart,-1),
            )
        }
    }
    private val pieces = parts(type).map { part ->
        val entity = Entity(EntityType.BLOCK_DISPLAY)
        entity.setNoGravity(true); entity.setHasPhysics(false)
        entity.editEntityMeta(BlockDisplayMeta::class.java) { m ->
            m.setBlockState(part.block); m.setScale(part.size)
            m.setTranslation(part.at.sub(part.size.mul(.5)))
            m.setTransformationInterpolationDuration(3); m.setPosRotInterpolationDuration(3)
            m.setViewRange(1.2f); m.setShadowRadius(.3f)
        }
        entity.setInstance(instance, origin).join()
        part to entity
    }
    val count get() = pieces.count { !it.second.isRemoved }
    private var removed = false
    private var lastUpdate = Long.MIN_VALUE
    fun tick(at: Pos, now: Long, attacking: Boolean, healthFraction: Double) {
        if (removed || (lastUpdate != Long.MIN_VALUE && now - lastUpdate < 100)) return
        lastUpdate = now
        val phase = now / (if (healthFraction < .4) 180.0 else 300.0)
        pieces.forEach { (part, e) ->
            val lift = if (abs(part.joint) == 1 && attacking) .35 + .25 * sin(phase) else 0.0
            val sway = if (part.joint == 0) 0.0 else sin(phase * part.joint.sign) * if (abs(part.joint) == 2) .12 else .06
            e.editEntityMeta(BlockDisplayMeta::class.java) { m ->
                m.setTransformationInterpolationStartDelta(0)
                m.setTranslation(part.at.add(0.0, lift, sway).sub(part.size.mul(.5)))
                if (part.block == Block.SEA_LANTERN || part.block == Block.SHROOMLIGHT) m.setBrightness(15,15)
            }
            e.teleport(at.withPitch(0f))
        }
    }
    fun dispose() { if (!removed) { removed = true; pieces.forEach { it.second.remove() } } }
}
