package dev.projects.server.questmap

import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

/** Smooth value noise in [-1, 1]: coherent clumps instead of per-block speckle. */
internal fun questClump(seed: Long, x: Int, z: Int, scale: Double, salt: Int): Double {
    val fx = x / scale
    val fz = z / scale
    val ix = floor(fx).toInt()
    val iz = floor(fz).toInt()
    fun corner(cx: Int, cz: Int): Double {
        var h = seed xor (salt * 0x9E3779B97F4A7C15uL.toLong()) xor (cx * 0x632BE59BD9B4E019L) xor (cz * 0x85157AF5L)
        h = (h xor (h ushr 31)) * 0x7FB5D329728EA185L
        h = (h xor (h ushr 27)) * -0x7B3E1F7A5E4C1C3BL
        return ((h ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
    }
    fun smooth(t: Double) = t * t * (3.0 - 2.0 * t)
    val tx = smooth(fx - ix)
    val tz = smooth(fz - iz)
    val a = corner(ix, iz) + (corner(ix + 1, iz) - corner(ix, iz)) * tx
    val b = corner(ix, iz + 1) + (corner(ix + 1, iz + 1) - corner(ix, iz + 1)) * tx
    return a + (b - a) * tz
}

/**
 * Verdant flora drifts: plants grow in groups of one species (a poppy field, a fern bank),
 * never as an even sprinkle. Runs after the single-plant pass and only fills empty soil.
 */
internal object QuestMapFlora {
    private val SOIL = setOf(Block.GRASS_BLOCK, Block.MOSS_BLOCK, Block.PODZOL, Block.ROOTED_DIRT)
    private val MEADOW_FLOWERS = listOf(Block.POPPY, Block.DANDELION, Block.AZURE_BLUET, Block.OXEYE_DAISY, Block.CORNFLOWER, Block.ALLIUM)

    fun decorate(instance: Instance, plan: QuestMapPlan, scenes: List<QuestLandscapeScene>) {
        if (plan.style != QuestTerrainStyle.VERDANT) return
        val random = Random(plan.seed xor 0x464C4F5241L)
        repeat(420) {
            val center = QuestMapPoint(14 + random.nextInt(plan.size - 28), 14 + random.nextInt(plan.size - 28))
            if (plan.roadDistanceSquaredAt(center.x, center.z) <= 7 * 7) return@repeat
            if (plan.contents.any { it.position.distanceSquared(center) < 10 * 10 }) return@repeat
            if (scenes.any { it.center.distanceSquared(center) < it.radius * it.radius }) return@repeat
            val cover = plan.groundCoverAt(center)
            val plant: Block = when (cover) {
                QuestGroundCover.MEADOW -> if (random.nextInt(100) < 62) MEADOW_FLOWERS[random.nextInt(MEADOW_FLOWERS.size)] else Block.SHORT_GRASS
                QuestGroundCover.FOREST_FLOOR -> if (random.nextBoolean()) Block.FERN else if (random.nextBoolean()) Block.LILY_OF_THE_VALLEY else Block.BROWN_MUSHROOM
                QuestGroundCover.HEATH -> if (random.nextInt(3) == 0) Block.SWEET_BERRY_BUSH.withProperty("age", "2") else Block.FERN
                else -> return@repeat
            }
            val radius = 2 + random.nextInt(4)
            for (dz in -radius..radius) {
                for (dx in -radius..radius) {
                    val d = sqrt((dx * dx + dz * dz).toDouble())
                    if (d > radius + 0.4) continue
                    val chance = 0.85 * (1.0 - d / (radius + 1.0)) + 0.12
                    if (random.nextDouble() > chance) continue
                    val x = center.x + dx
                    val z = center.z + dz
                    if (x !in 2 until plan.size - 2 || z !in 2 until plan.size - 2) continue
                    if (plan.roadDistanceSquaredAt(x, z) <= 4 * 4) continue
                    if (plan.groundCoverAt(x, z) != cover) continue
                    val ground = plan.heightAt(x, z)
                    if (instance.getBlock(x, ground, z) !in SOIL) continue
                    if (!instance.getBlock(x, ground + 1, z).isAir) continue
                    // A few grass tufts inside every flower patch keep it from looking planted.
                    val block = if (plant in MEADOW_FLOWERS && random.nextInt(5) == 0) Block.SHORT_GRASS else plant
                    instance.setBlock(x, ground + 1, z, block)
                }
            }
        }
    }
}
