package dev.projects.server.coreloop

import dev.projects.server.coreloop.adventure.*
import dev.projects.server.questmap.VerdantRoadQuestRuntime
import net.minestom.server.coordinate.Pos
import kotlin.math.abs

/** Reuse the guaranteed walkable route. Displays have no collision and never change road blocks. */
internal object CoreAdventurePlacement {
    fun sites(runtime: VerdantRoadQuestRuntime): List<AdventureSite> {
        val plan = runtime.plan
        val route = plan.mainRoute
        fun plaza(index: Int): Pos? {
            val point = route.getOrNull(index) ?: return null
            if (point.distanceSquared(plan.start) < 40 * 40 || point.distanceSquared(plan.boss) < 55 * 55) return null
            val desired = Pos(point.x + 0.5, plan.heightAt(point) + 1.0, point.z + 0.5)
            return runCatching {
                val center = QuestCombatPlacement.resolve(runtime.instance, desired)
                val spawns = listOf(-3.0 to -2.0, 3.0 to -2.0, -3.0 to 2.0, 3.0 to 2.0).map { (x, z) ->
                    QuestCombatPlacement.resolve(runtime.instance, center.add(x, 0.0, z))
                }
                check(spawns.distinct().size == 4 && spawns.all { it.distanceSquared(center) <= 8.0 * 8.0 })
                center
            }.getOrNull()
        }
        val candidates = (0 until route.size step 4).toList()
        val rift = candidates.sortedBy { abs(it - route.size / 3) }.firstNotNullOfOrNull { index ->
            val centers = listOf(index, index + 18, index + 36).map { plaza(it) }
            if (centers.any { it == null }) null else centers.filterNotNull().takeIf { places ->
                places.zipWithNext().all { (a, b) -> a.distance(b) in 10.0..26.0 }
            }
        }
        val ritual = candidates.sortedBy { abs(it - route.size * 2 / 3) }.firstNotNullOfOrNull { index ->
            plaza(index)?.takeIf { center -> rift.orEmpty().all { it.distanceSquared(center) >= 50 * 50 } }
        }
        val result = buildList {
            if (rift != null) add(AdventureSite(AdventureKind.RIFT, "rift:0", rift))
            if (ritual != null) add(AdventureSite(AdventureKind.RITUAL, "ritual:0", listOf(ritual)))
        }
        println("CORE_ADVENTURE_PLACEMENT seed=${plan.seed} sites=${result.joinToString { "${it.kind}@${it.centers.first().blockX()},${it.centers.first().blockZ()}" }}")
        return result
    }
}
