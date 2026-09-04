package dev.projects.server.coreloop

import dev.projects.server.coreloop.adventure.AdventureKind
import dev.projects.server.questmap.VerdantRoadQuestRuntime
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import java.util.concurrent.TimeUnit
import kotlin.test.*

class CoreAdventurePlacementTest {
    @Test fun `decorated maps contain accessible separated events without changing any route blocks`() {
        MinecraftServer.init(Auth.Offline())
        for (seed in listOf(1_788_168_623_401L, 386_300_878_984_060L)) {
            val runtime = VerdantRoadQuestRuntime.prepare(seed, candidateCount = 1).get(45, TimeUnit.SECONDS)
            try {
                val plan = runtime.plan
                val route = plan.mainRoute.map { net.minestom.server.coordinate.Pos(it.x + 0.5, plan.heightAt(it) + 1.0, it.z + 0.5) }
                val before = route.map { runtime.instance.getBlock(it.sub(0.0, 1.0, 0.0)) }
                val sites = CoreAdventurePlacement.sites(runtime)
                assertEquals(setOf(AdventureKind.RIFT, AdventureKind.RITUAL), sites.map { it.kind }.toSet(), "seed $seed")
                assertEquals(sites, CoreAdventurePlacement.sites(runtime), "placement must be deterministic")
                assertEquals(before, route.map { runtime.instance.getBlock(it.sub(0.0, 1.0, 0.0)) })
                sites.flatMap { it.centers }.forEach { assertTrue(QuestCombatPlacement.clear(runtime.instance, it)) }
                val rift = sites.single { it.kind == AdventureKind.RIFT }.centers
                assertTrue(rift.zipWithNext().all { (a, b) -> a.distance(b) in 10.0..26.0 })
                val ritual = sites.single { it.kind == AdventureKind.RITUAL }.centers.single()
                assertTrue(rift.all { it.distance(ritual) >= 50 })
            } finally { runtime.close() }
        }
    }
}
