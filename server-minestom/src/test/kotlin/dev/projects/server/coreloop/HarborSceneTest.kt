package dev.projects.server.coreloop

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer

class HarborSceneTest {
    /**
     * Where a player can walk from [start] in the town's own blocks: up one block, down three, two free blocks to
     * stand in. Fences and walls (1.5 high) are not stood on.
     */
    private fun reachable(instance: InstanceContainer, start: Pos): Set<Pair<Int, Int>> {
        fun open(x: Int, y: Int, z: Int) = instance.getBlock(x, y, z).let { !it.solid() && !it.liquid() }
        fun stands(x: Int, y: Int, z: Int) = instance.getBlock(x, y - 1, z).let { it.solid() && it.registry().collisionShape().relativeEnd().y() <= 1.0 } &&
            open(x, y, z) && open(x, y + 1, z)
        val seen = hashSetOf(start.blockX() to start.blockZ())
        val queue = ArrayDeque(listOf(Triple(start.blockX(), start.blockY(), start.blockZ())))
        while (queue.isNotEmpty()) {
            val (x, y, z) = queue.removeFirst()
            for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val nz = z + dz
                if (nx !in -92..104 || nz !in -92..84 || (nx to nz) in seen) continue
                val ny = (1 downTo -3).map { y + it }.firstOrNull { stands(nx, it, nz) && (it <= y || open(x, y + 2, z)) } ?: continue
                seen += nx to nz; queue += Triple(nx, ny, nz)
            }
        }
        return seen
    }

    @Test
    fun `the town is where players arrive and every facility is walked to from there`() {
        MinecraftServer.init(Auth.Offline())
        val hub = MinecraftServer.getInstanceManager().createInstanceContainer()
        val harbor = HarborScene.build(hub)
        try {
            assertTrue(QuestCombatPlacement.clear(hub, harbor.spawn), "Players arrive inside a block")
            // 遠征受付 and 自分のコロニー have no door in the town for 11/20; their code stays.
            assertEquals(HarborFacilityKind.entries.toSet() - setOf(HarborFacilityKind.EXPEDITIONS, HarborFacilityKind.COLONY),
                harbor.facilities.map { it.kind }.toSet())
            assertEquals(harbor.facilities.size, harbor.labels.size)
            val walk = reachable(hub, harbor.spawn)
            for (facility in harbor.facilities) {
                val p = facility.position
                assertTrue((-2..2).any { dx -> (-2..2).any { dz -> (p.blockX() + dx to p.blockZ() + dz) in walk } },
                    "${facility.displayName} cannot be walked to from where players arrive: " +
                        (-3..3).joinToString(" | ") { dz -> (-3..3).joinToString(",") { dx -> (p.blockY() - 1..p.blockY() + 1).joinToString("/") { y -> hub.getBlock(p.blockX() + dx, y, p.blockZ() + dz).name().removePrefix("minecraft:").take(6) } + (if ((p.blockX() + dx to p.blockZ() + dz) in walk) "*" else "") } })
                assertTrue(facility.blockPositions.isNotEmpty() && facility.blockPositions.all { !hub.getBlock(it).air() },
                    "${facility.displayName} has nothing to click")
                assertTrue(facility.blockPositions.all { it.distance(p) <= 8.0 }, "${facility.displayName}: its blocks are far from its label")
            }
            // The smith stands on the smithy's floor, by the anvil.
            val smith = harbor.smith.position
            assertTrue(hub.getBlock(smith.sub(0.0, 0.5, 0.0)).solid() && !hub.getBlock(smith).solid())
            val workshop = harbor.facilities.single { it.kind == HarborFacilityKind.WORKSHOP }
            assertTrue(smith.distance(workshop.position) <= 4.0)
        } finally { MinecraftServer.getInstanceManager().unregisterInstance(hub) }
    }
}
