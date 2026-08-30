package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import java.util.Random
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

internal class VerdantRoadQuestGenerator(
    private val plan: QuestMapPlan,
) : Generator {
    override fun generate(unit: GenerationUnit) {
        val start = unit.absoluteStart()
        unit.modifier().setAllRelative { relativeX, relativeY, relativeZ ->
            blockAt(
                start.blockX() + relativeX,
                start.blockY() + relativeY,
                start.blockZ() + relativeZ,
            )
        }
    }

    private fun blockAt(x: Int, y: Int, z: Int): Block {
        if (x !in 0 until plan.size || z !in 0 until plan.size) return outerSeaBlock(x, y, z)
        val ground = plan.heightAt(x, z)
        val mainRoad = plan.mainRoadDistanceSquaredAt(x, z) <= 3 * 3
        val sideTrail = !mainRoad && plan.roadDistanceSquaredAt(x, z) <= 1 * 1
        val road = mainRoad || sideTrail
        val boundary = x == 0 || z == 0 || x == plan.size - 1 || z == plan.size - 1
        if (boundary && y in ground + 1..ground + 4) return Block.BARRIER
        if (y > ground) {
            return if (plan.style == QuestTerrainStyle.SALTMARSH && !road && y <= QUEST_WATER_LEVEL) Block.WATER else Block.AIR
        }
        if (y < ground - 4) return if (plan.style == QuestTerrainStyle.HIGHLANDS) Block.TUFF else Block.STONE
        if (y < ground) {
            return when (plan.style) {
                QuestTerrainStyle.VERDANT -> Block.DIRT
                QuestTerrainStyle.HIGHLANDS -> if (ground >= 57) Block.STONE else Block.DIRT
                QuestTerrainStyle.SALTMARSH -> Block.MUD
            }
        }
        if (mainRoad) {
            val variation = Math.floorMod(plan.seed xor (x * 734_287L) xor (z * 912_271L), 5L).toInt()
            return when (plan.style) {
                QuestTerrainStyle.SALTMARSH -> if (variation == 0) Block.MUD_BRICKS else Block.PACKED_MUD
                QuestTerrainStyle.HIGHLANDS -> if (variation == 0) Block.COBBLESTONE else Block.GRAVEL
                QuestTerrainStyle.VERDANT -> if (variation == 0) Block.GRAVEL else Block.COARSE_DIRT
            }
        }
        if (sideTrail) {
            return when (plan.style) {
                QuestTerrainStyle.SALTMARSH -> Block.MUD
                QuestTerrainStyle.HIGHLANDS -> Block.PODZOL
                QuestTerrainStyle.VERDANT -> Block.ROOTED_DIRT
            }
        }
        return when (plan.style) {
            QuestTerrainStyle.VERDANT -> Block.GRASS_BLOCK
            QuestTerrainStyle.HIGHLANDS -> if (ground >= 57) Block.STONE else Block.PODZOL
            QuestTerrainStyle.SALTMARSH -> if (ground <= QUEST_WATER_LEVEL) Block.MUD else Block.MOSS_BLOCK
        }
    }

    private fun outerSeaBlock(x: Int, y: Int, z: Int): Block {
        val floorVariation = Math.floorMod(plan.seed xor (x * 341_873L) xor (z * 712_619L), 3L).toInt() - 1
        val floor = QUEST_WATER_LEVEL - 3 + floorVariation
        return when {
            y < floor - 3 -> Block.STONE
            y < floor -> Block.DIRT
            y == floor -> if ((x + z) and 1 == 0) Block.SAND else Block.GRAVEL
            y <= QUEST_WATER_LEVEL -> Block.WATER
            else -> Block.AIR
        }
    }
}

internal object VerdantRoadQuestDecorator {
    fun decorate(instance: InstanceContainer, plan: QuestMapPlan) {
        decorateTrees(instance, plan)
        decorateTerrainDetail(instance, plan)
        decorateRoadGuidance(instance, plan)
        plan.contents.forEachIndexed { ordinal, content ->
            when (content.kind) {
                QuestMapContentKind.START -> decorateStart(instance, plan, content.position)
                QuestMapContentKind.COMBAT -> decorateCombat(instance, plan, content.position)
                QuestMapContentKind.GATHERING -> decorateGathering(instance, plan, content.position, ordinal)
                QuestMapContentKind.DISCOVERY -> decorateDiscovery(instance, plan, content.position)
                QuestMapContentKind.BOSS -> decorateBossArena(instance, plan, content.position)
            }
        }
    }

    private fun decorateTrees(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x47524F5645L)
        val occupied = mutableSetOf<QuestMapPoint>()
        val attempts = when (plan.style) {
            QuestTerrainStyle.VERDANT -> 280
            QuestTerrainStyle.HIGHLANDS -> 210
            QuestTerrainStyle.SALTMARSH -> 190
        }
        repeat(attempts) {
            val point = QuestMapPoint(
                10 + random.nextInt(plan.size - 20),
                10 + random.nextInt(plan.size - 20),
            )
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 6 * 6) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 9 * 9 }) return@repeat
            if (occupied.any { it.distanceSquared(point) < 5 * 5 }) return@repeat
            if (plan.style == QuestTerrainStyle.SALTMARSH && plan.heightAt(point) <= QUEST_WATER_LEVEL) return@repeat
            occupied += point
            placeTree(instance, plan, point, random.nextInt(4))
        }
    }

    private fun placeTree(instance: Instance, plan: QuestMapPlan, point: QuestMapPoint, variation: Int) {
        val ground = plan.heightAt(point)
        val log = when (plan.style) {
            QuestTerrainStyle.VERDANT -> Block.OAK_LOG
            QuestTerrainStyle.HIGHLANDS -> Block.SPRUCE_LOG
            QuestTerrainStyle.SALTMARSH -> Block.MANGROVE_LOG
        }
        val leaves = when (plan.style) {
            QuestTerrainStyle.VERDANT -> Block.OAK_LEAVES
            QuestTerrainStyle.HIGHLANDS -> Block.SPRUCE_LEAVES
            QuestTerrainStyle.SALTMARSH -> Block.MANGROVE_LEAVES
        }
        val trunkHeight = 4 + variation
        repeat(trunkHeight) { offset -> instance.setBlock(point.x, ground + 1 + offset, point.z, log) }
        val crownY = ground + trunkHeight
        for (dy in -1..2) {
            val radius = if (dy == 2) 1 else 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dz) <= radius + 1) {
                        instance.setBlock(point.x + dx, crownY + dy, point.z + dz, leaves)
                    }
                }
            }
        }
    }

    private fun decorateTerrainDetail(instance: Instance, plan: QuestMapPlan) {
        val random = Random(plan.seed xor 0x5445525241494EL)
        repeat(850) {
            val point = QuestMapPoint(8 + random.nextInt(plan.size - 16), 8 + random.nextInt(plan.size - 16))
            if (plan.roadDistanceSquaredAt(point.x, point.z) <= 4 * 4) return@repeat
            if (plan.contents.any { it.position.distanceSquared(point) < 6 * 6 }) return@repeat
            val ground = plan.heightAt(point)
            if (plan.style == QuestTerrainStyle.SALTMARSH && ground <= QUEST_WATER_LEVEL) {
                if (random.nextInt(8) == 0) {
                    instance.setBlock(point.x, QUEST_WATER_LEVEL + 1, point.z, Block.LILY_PAD)
                }
                return@repeat
            }
            when (random.nextInt(12)) {
                0 -> placeRock(instance, point, ground + 1, random.nextBoolean())
                1, 2 -> instance.setBlock(point.x, ground + 1, point.z, Block.FERN)
                3 -> instance.setBlock(point.x, ground + 1, point.z, Block.BROWN_MUSHROOM)
                else -> instance.setBlock(point.x, ground + 1, point.z, Block.SHORT_GRASS)
            }
        }
    }

    private fun placeRock(instance: Instance, point: QuestMapPoint, y: Int, mossy: Boolean) {
        val block = if (mossy) Block.MOSSY_COBBLESTONE else Block.COBBLESTONE
        instance.setBlock(point.x, y, point.z, block)
        instance.setBlock(point.x + 1, y, point.z, block)
        instance.setBlock(point.x, y, point.z + 1, block)
        if (mossy) instance.setBlock(point.x, y + 1, point.z, Block.MOSS_CARPET)
    }

    private fun decorateRoadGuidance(instance: Instance, plan: QuestMapPlan) {
        val interval = maxOf(35, plan.mainRoute.size / 6)
        for (routeIndex in interval until plan.mainRoute.lastIndex - interval step interval) {
            val point = plan.mainRoute[routeIndex]
            val before = plan.mainRoute[(routeIndex - 4).coerceAtLeast(0)]
            val after = plan.mainRoute[(routeIndex + 4).coerceAtMost(plan.mainRoute.lastIndex)]
            val sideX = (after.z - before.z).coerceIn(-1, 1) * 4
            val sideZ = (before.x - after.x).coerceIn(-1, 1) * 4
            val marker = QuestMapPoint(
                (point.x + sideX).coerceIn(3, plan.size - 4),
                (point.z + sideZ).coerceIn(3, plan.size - 4),
            )
            placeLanternPost(instance, marker.x, plan.heightAt(marker) + 1, marker.z)
        }
    }

    private fun decorateStart(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val ground = plan.heightAt(center)
        for (z in center.z - 4..center.z + 4) {
            for (x in center.x - 4..center.x + 4) {
                if ((x + z) % 3 == 0) instance.setBlock(x, ground, z, Block.COARSE_DIRT)
            }
        }
        instance.setBlock(center.x, ground + 1, center.z, Block.CAMPFIRE)
        instance.setBlock(center.x + 2, ground + 1, center.z, Block.BARREL)
        instance.setBlock(center.x - 2, ground + 1, center.z, Block.CRAFTING_TABLE)
        for (x in center.x - 3..center.x + 3) instance.setBlock(x, ground + 4, center.z - 4, Block.GREEN_WOOL)
        listOf(center.x - 3, center.x + 3).forEach { x ->
            repeat(3) { height -> instance.setBlock(x, ground + 1 + height, center.z - 4, Block.OAK_FENCE) }
        }
        placeLanternPost(instance, center.x, ground + 1, center.z + 4)
    }

    private fun decorateCombat(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val y = plan.heightAt(center)
        for (step in 0 until 28) {
            val angle = Math.PI * 2.0 * step / 28.0
            val x = center.x + (kotlin.math.cos(angle) * 6.0).roundToInt()
            val z = center.z + (kotlin.math.sin(angle) * 6.0).roundToInt()
            instance.setBlock(x, plan.heightAt(x, z), z, Block.MOSSY_COBBLESTONE)
        }
        listOf(-4 to -4, -4 to 4, 4 to -4, 4 to 4).forEach { (dx, dz) ->
            repeat(2) { height -> instance.setBlock(center.x + dx, y + 1 + height, center.z + dz, Block.MOSSY_STONE_BRICKS) }
            instance.setBlock(center.x + dx, y + 3, center.z + dz, Block.LANTERN)
        }
    }

    private fun decorateGathering(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint, ordinal: Int) {
        val y = plan.heightAt(center) + 1
        val ore = when (ordinal % 4) {
            0 -> Block.RAW_COPPER_BLOCK
            1 -> Block.IRON_ORE
            2 -> Block.COAL_ORE
            else -> Block.AMETHYST_BLOCK
        }
        listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).forEachIndexed { index, (dx, dz) ->
            instance.setBlock(center.x + dx, y + if (index == 0) 1 else 0, center.z + dz, ore)
        }
        listOf(-3 to -3, 3 to -3).forEach { (dx, dz) ->
            repeat(3) { height -> instance.setBlock(center.x + dx, y + height, center.z + dz, Block.STRIPPED_OAK_LOG) }
        }
        for (x in center.x - 3..center.x + 3) instance.setBlock(x, y + 3, center.z - 3, Block.BROWN_WOOL)
    }

    private fun decorateDiscovery(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val y = plan.heightAt(center) + 1
        for (height in 0..4) {
            instance.setBlock(center.x - 2, y + height, center.z, Block.STONE_BRICKS)
            instance.setBlock(center.x + 2, y + height, center.z, Block.STONE_BRICKS)
        }
        for (x in center.x - 2..center.x + 2) instance.setBlock(x, y + 5, center.z, Block.CHISELED_STONE_BRICKS)
        instance.setBlock(center.x, y, center.z, Block.AMETHYST_BLOCK)
        instance.setBlock(center.x, y + 1, center.z, Block.CANDLE)
    }

    private fun decorateBossArena(instance: Instance, plan: QuestMapPlan, center: QuestMapPoint) {
        val ground = plan.heightAt(center)
        for (z in center.z - 14..center.z + 14) {
            for (x in center.x - 14..center.x + 14) {
                val distance = QuestMapPoint(x, z).distanceSquared(center)
                if (distance <= 13 * 13) {
                    val block = when {
                        distance >= 11 * 11 -> Block.POLISHED_DEEPSLATE
                        distance % 11 == 0 -> Block.CRACKED_DEEPSLATE_BRICKS
                        else -> Block.POLISHED_ANDESITE
                    }
                    instance.setBlock(x, ground, z, block)
                }
            }
        }
        instance.setBlock(center.x, ground + 1, center.z, Block.LODESTONE)
        listOf(-10 to 0, 10 to 0, 0 to -10, 0 to 10).forEach { (dx, dz) ->
            repeat(5) { height -> instance.setBlock(center.x + dx, ground + 1 + height, center.z + dz, Block.DEEPSLATE_BRICKS) }
            instance.setBlock(center.x + dx, ground + 6, center.z + dz, Block.SOUL_LANTERN)
        }
    }

    private fun placeLanternPost(instance: Instance, x: Int, y: Int, z: Int) {
        repeat(3) { offset -> instance.setBlock(x, y + offset, z, Block.OAK_FENCE) }
        instance.setBlock(x, y + 3, z, Block.LANTERN)
    }
}

internal class VerdantRoadQuestRuntime private constructor(
    val plan: QuestMapPlan,
    val instance: InstanceContainer,
    val spawn: Pos,
    val preparationMillis: Long,
) {
    fun close() {
        check(instance.players.isEmpty()) { "Cannot close a quest map while players are inside" }
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    companion object {
        fun prepare(seed: Long): CompletableFuture<VerdantRoadQuestRuntime> {
            val startedAt = System.nanoTime()
            val plan = VerdantRoadQuestPlanner.generate(seed)
            val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
            instance.setTime(6000)
            instance.defaultClock()?.pause()
            instance.setWeather(Weather.CLEAR)
            instance.setChunkSupplier(::LightingChunk)
            instance.setGenerator(VerdantRoadQuestGenerator(plan))
            val chunkCount = plan.size / 16
            val chunks = buildList {
                for (chunkX in -1..chunkCount) {
                    for (chunkZ in -1..chunkCount) add(instance.loadChunk(chunkX, chunkZ))
                }
            }
            return CompletableFuture.allOf(*chunks.toTypedArray()).thenApply {
                VerdantRoadQuestDecorator.decorate(instance, plan)
                val spawn = Pos(plan.start.x + 0.5, plan.heightAt(plan.start) + 1.0, plan.start.z + 0.5)
                VerdantRoadQuestRuntime(
                    plan,
                    instance,
                    spawn,
                    (System.nanoTime() - startedAt) / 1_000_000,
                )
            }.whenComplete { _, failure ->
                if (failure != null) MinecraftServer.getInstanceManager().unregisterInstance(instance)
            }
        }
    }
}

internal data class VerdantRoadQuestServiceStatus(
    val readyMaps: Int,
    val preparingMaps: Int,
    val activeMaps: Int,
)

/** Concrete prewarmed service for the first generated ProjectS quest, not a generic quest framework. */
internal class VerdantRoadQuestService(
    private val hubInstance: Instance,
    private val hubSpawn: Pos,
    seedBase: Long = System.currentTimeMillis(),
) {
    private val ready = ConcurrentLinkedQueue<VerdantRoadQuestRuntime>()
    private val activeByPlayer = ConcurrentHashMap<UUID, VerdantRoadQuestRuntime>()
    private val preparing = AtomicInteger()
    private val nextSeed = AtomicLong(seedBase)

    fun prewarmInitial(): CompletableFuture<Void> = CompletableFuture.allOf(
        *Array(PREWARM_TARGET) { prepareOne() },
    )

    fun enter(player: Player): CompletableFuture<Boolean> {
        if (activeByPlayer.containsKey(player.uuid)) {
            player.sendMessage(Component.text("You are already inside a generated quest map.", NamedTextColor.YELLOW))
            return CompletableFuture.completedFuture(false)
        }
        val runtime = ready.poll()
        if (runtime == null) {
            replenish()
            player.sendMessage(Component.text("Quest map pool is warming; try again shortly.", NamedTextColor.RED))
            return CompletableFuture.completedFuture(false)
        }
        return enterRuntime(player, runtime, returnToReadyOnFailure = true).whenComplete { _, _ -> replenish() }
    }

    fun enterSeed(player: Player, seed: Long): CompletableFuture<Boolean> {
        if (activeByPlayer.containsKey(player.uuid)) {
            player.sendMessage(Component.text("You are already inside a generated quest map.", NamedTextColor.YELLOW))
            return CompletableFuture.completedFuture(false)
        }
        player.sendMessage(Component.text("Preparing manual-smoke seed $seed...", NamedTextColor.GRAY))
        return VerdantRoadQuestRuntime.prepare(seed).thenCompose { runtime ->
            enterRuntime(player, runtime, returnToReadyOnFailure = false)
        }
    }

    private fun enterRuntime(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
        returnToReadyOnFailure: Boolean,
    ): CompletableFuture<Boolean> {
        activeByPlayer[player.uuid] = runtime
        player.setVelocity(Vec.ZERO)
        val transferStartedAt = System.nanoTime()
        return player.setInstance(runtime.instance, runtime.spawn).handle { _, failure ->
            if (failure != null) {
                activeByPlayer.remove(player.uuid, runtime)
                if (returnToReadyOnFailure) {
                    ready += runtime
                } else if (runtime.instance.players.isEmpty()) {
                    runtime.close()
                }
                player.sendMessage(Component.text("Quest transfer failed: ${failure.message}", NamedTextColor.RED))
                false
            } else {
                val transferMillis = (System.nanoTime() - transferStartedAt) / 1_000_000
                player.sendMessage(
                    Component.text(
                        "Verdant Road seed=${runtime.plan.seed} style=${runtime.plan.style} ready=${runtime.preparationMillis}ms transfer=${transferMillis}ms",
                        NamedTextColor.GREEN,
                    ),
                )
                player.sendMessage(Component.text("Follow the road to the Lodestone boss arena. Side trails contain gathering and discoveries.", NamedTextColor.GRAY))
                true
            }
        }
    }

    fun returnToHub(player: Player): CompletableFuture<Boolean> {
        val runtime = activeByPlayer.remove(player.uuid) ?: return CompletableFuture.completedFuture(false)
        player.setVelocity(Vec.ZERO)
        return player.setInstance(hubInstance, hubSpawn).handle { _, failure ->
            if (failure == null) {
                runtime.close()
                player.sendMessage(Component.text("Returned to the ProjectS hub.", NamedTextColor.GREEN))
                true
            } else {
                activeByPlayer[player.uuid] = runtime
                player.sendMessage(Component.text("Hub transfer failed: ${failure.message}", NamedTextColor.RED))
                false
            }
        }
    }

    fun disconnect(playerId: UUID) {
        activeByPlayer.remove(playerId)?.let { closeAfterDisconnect(it, attemptsRemaining = 20) }
    }

    fun status(): VerdantRoadQuestServiceStatus = VerdantRoadQuestServiceStatus(
        readyMaps = ready.size,
        preparingMaps = preparing.get(),
        activeMaps = activeByPlayer.size,
    )

    private fun replenish() {
        while (ready.size + preparing.get() < PREWARM_TARGET) {
            prepareOne().exceptionally { failure ->
                System.err.println("Quest map background prewarm failed: ${failure.message}")
                null
            }
        }
    }

    private fun prepareOne(): CompletableFuture<Void> {
        preparing.incrementAndGet()
        return VerdantRoadQuestRuntime.prepare(nextSeed.getAndIncrement()).whenComplete { _, _ ->
            preparing.decrementAndGet()
        }.thenAccept { runtime -> ready += runtime }
    }

    private fun closeAfterDisconnect(runtime: VerdantRoadQuestRuntime, attemptsRemaining: Int) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            if (runtime.instance.players.isEmpty()) {
                runtime.close()
            } else if (attemptsRemaining > 1) {
                closeAfterDisconnect(runtime, attemptsRemaining - 1)
            } else {
                System.err.println("Quest map ${runtime.plan.seed} still owns players after disconnect cleanup window")
            }
        }
    }

    private companion object {
        const val PREWARM_TARGET = 2
    }
}
