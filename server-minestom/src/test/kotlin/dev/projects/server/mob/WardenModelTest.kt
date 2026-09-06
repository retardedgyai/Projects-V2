package dev.projects.server.mob

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class WardenModelTest {
    @Test fun `original articulated bosses own bounded native displays and dispose all parts`() {
        MinecraftServer.init(Auth.Offline())
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0,40,Block.STONE) }; instance.loadChunk(0,0).join()
        for (type in QuestMobArchetype.entries.filter(WardenModel::supports)) {
            val runtime = QuestEncounterCombat(instance,1,emptyList(),Pos(8.0,40.0,8.0),{ _,_-> },{ _,_-> },explicitBossArchetype = type)
            try {
                assertEquals(22,runtime.modelDisplayCount)
                assertTrue(runtime.entities().single().isInvisible)
                assertEquals(1.2,runtime.combatTargets().single().halfExtent.x())
                runtime.tick(1000); runtime.tick(1200)
            } finally { runtime.dispose() }
            assertEquals(0,runtime.modelDisplayCount)
        }
        assertTrue(instance.entities.isEmpty())
        val out = Path.of("build/reports/warden-parts.tsv"); Files.createDirectories(out.parent)
        Files.newBufferedWriter(out).use { w ->
            WardenModel.parts(QuestMobArchetype.FORGE_SENTINEL).forEach { p ->
                w.appendLine("${p.at.x()}\t${p.at.y()}\t${p.at.z()}\t${p.size.x()}\t${p.size.y()}\t${p.size.z()}\t${p.block.name()}")
            }
        }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }
}
