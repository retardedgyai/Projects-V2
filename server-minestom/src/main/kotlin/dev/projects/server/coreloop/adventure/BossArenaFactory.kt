package dev.projects.server.coreloop.adventure

import dev.projects.server.mob.QuestMobArchetype
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.sqrt

data class BossArena(
    val instance: InstanceContainer,
    val playerSpawn: Pos,
    val bossSpawn: Pos,
    val archetype: QuestMobArchetype,
    val bossId: String,
    val displayName: String,
) {
    /** Root moves players out and disposes QuestEncounterCombat before calling this. */
    fun dispose() {
        check(instance.players.isEmpty()) { "Move trial players out before unloading their arena" }
        instance.entities.toList().forEach { it.remove() }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }
}

/** Three concrete server-built arenas; no imported world or landscape mutation. */
object BossArenaFactory {
    fun create(bossId: String, tier: Int): BossArena {
        require(tier in 1..4)
        val archetype = when (bossId) {
            "rift" -> QuestMobArchetype.CINDER_REGENT
            "ritual" -> QuestMobArchetype.GLACIAL_COLOSSUS
            "trial" -> QuestMobArchetype.TEMPEST_HIEROPHANT
            else -> error("Unknown trial boss: $bossId")
        }
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.time = if (bossId == "trial") 18_000L else 6_000L
        instance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 39, Block.DEEPSLATE)
            val start = unit.absoluteStart()
            val end = unit.absoluteEnd()
            for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ()) {
                val dx = x - 32.0
                val dz = z - 32.0
                val distance = sqrt(dx * dx + dz * dz)
                val floor = when (bossId) {
                    "rift" -> if (abs(dx) < 2 || abs(dz) < 2) Block.POLISHED_BLACKSTONE else Block.RED_NETHER_BRICKS
                    "ritual" -> if (distance in 4.5..5.5 || distance in 15.5..16.5) Block.BLUE_ICE else Block.SMOOTH_QUARTZ
                    else -> if ((x + z) % 7 == 0) Block.SEA_LANTERN else Block.PRISMARINE_BRICKS
                }
                unit.modifier().setBlock(x, 39, z, floor)
                if (distance in 22.0..23.5) for (y in 40..43) unit.modifier().setBlock(x, y, z,
                    if (bossId == "ritual") Block.BLUE_ICE else Block.POLISHED_DEEPSLATE)
                if (distance in 19.0..20.5 && (abs(dx) < 1 || abs(dz) < 1)) {
                    for (y in 40..44) unit.modifier().setBlock(x, y, z, Block.POLISHED_DEEPSLATE)
                    unit.modifier().setBlock(x, 45, z, if (bossId == "rift") Block.SHROOMLIGHT else Block.SEA_LANTERN)
                }
            }
        }
        try {
            val chunks = (-1..5).flatMap { x -> (-1..5).map { z -> instance.loadChunk(x, z) } }
            CompletableFuture.allOf(*chunks.toTypedArray()).join()
            return BossArena(instance, Pos(32.5, 40.0, 16.5), Pos(32.5, 40.0, 32.5), archetype, bossId,
                when (bossId) { "rift" -> "灰燼の溶炉"; "ritual" -> "氷獄の円庭"; else -> "天雷の祭壇" })
        } catch (failure: Throwable) {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            throw failure
        }
    }
}
