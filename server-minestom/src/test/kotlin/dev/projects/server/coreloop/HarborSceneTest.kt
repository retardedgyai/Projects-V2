package dev.projects.server.coreloop

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block

class HarborSceneTest {
    @Test
    fun `arrival and every facility connect through level unobstructed walking space`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val scene = HarborScene.build(instance)
        try {
            val spawn = scene.spawn
            assertEquals(41.0, spawn.y())
            assertTrue(instance.getBlock(spawn.blockX(), 40, spawn.blockZ()).isSolid)
            assertEquals(Block.AIR, instance.getBlock(spawn))
            assertEquals(5, scene.facilities.size)
            assertEquals(HarborFacilityKind.entries.toSet(), scene.facilities.map { it.kind }.toSet())
            assertEquals(5, scene.labels.size)

            fun walkable(x: Int, z: Int): Boolean =
                x in -32..32 && z in -28..34 && instance.getBlock(x, 40, z).isSolid &&
                    instance.getBlock(x, 41, z).isAir && instance.getBlock(x, 42, z).isAir

            val reached = mutableSetOf(spawn.blockX() to spawn.blockZ())
            val pending = ArrayDeque(reached)
            val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            while (pending.isNotEmpty()) {
                val (x, z) = pending.removeFirst()
                directions.forEach { (dx, dz) ->
                    val next = x + dx to z + dz
                    if (walkable(next.first, next.second) && reached.add(next)) pending.add(next)
                }
            }
            scene.facilities.forEach { facility ->
                assertTrue(facility.position.distance(spawn) < 25.0, "${facility.kind} is too far from arrival")
                facility.blockPositions.forEach { block ->
                    assertFalse(instance.getBlock(block).isAir, "${facility.kind} exposes an empty interaction block")
                }
                assertTrue(
                    facility.blockPositions.any { block ->
                        directions.any { (dx, dz) -> block.blockX() + dx to block.blockZ() + dz in reached }
                    },
                    "No two-block-high, level path reaches ${facility.kind}",
                )
            }
            assertTrue(0 to 33 in reached, "The pier cannot be reached from arrival")
            assertEquals(Block.WATER, instance.getBlock(50, 38, 50), "Outside the harbor should be sea, not void")
            // Export actual generated blocks, not an aspirational concept illustration.
            val target = java.nio.file.Path.of("build/reports/harbor-blocks.tsv")
            java.nio.file.Files.createDirectories(target.parent)
            java.nio.file.Files.newBufferedWriter(target).use { out ->
                for (x in -48..48) for (z in -56..46) for (y in 36..66) {
                    val b = instance.getBlock(x,y,z)
                    if (!b.isAir && b != Block.WATER && listOf(Triple(1,0,0),Triple(0,1,0),Triple(0,0,1)).any { (dx,dy,dz) -> instance.getBlock(x+dx,y+dy,z+dz).let { it.isAir || it == Block.WATER } })
                        out.appendLine("$x\t$y\t$z\t${b.name()}")
                }
            }
        } finally {
            scene.labels.forEach { it.remove() }
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }
}
