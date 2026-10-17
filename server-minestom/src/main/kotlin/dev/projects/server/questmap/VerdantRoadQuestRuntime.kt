package dev.projects.server.questmap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityPose
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class VerdantRoadQuestRuntime private constructor(
    val requestedSeed: Long,
    val plan: QuestMapPlan,
    val customization: QuestMapCustomization,
    val gatheringNodes: List<QuestGatheringNode>,
    val candidateScore: QuestMapCandidateScore,
    val candidateCount: Int,
    val instance: InstanceContainer,
    val spawn: Pos,
    val preparationMillis: Long,
    val loadedChunkCount: Int,
) {
    val gatheringObjects: Map<Int, QuestMapStructureAssets.GatheringObject> = gatheringNodes.associate { node ->
        val resolved = QuestMapStructureAssets.resolveGatheringObject(plan, node)
        val survivingBlocks = resolved.blocks.filter { (position, block) -> instance.getBlock(position) == block }
        node.id to resolved.copy(blocks = survivingBlocks)
    }
    private val gatheringNodesByPosition = buildMap {
        gatheringNodes.forEach { node ->
            val gathering = gatheringObjects.getValue(node.id)
            gathering.interactionBlocks.forEach { position ->
                if (instance.getBlock(position) != Block.AIR && position !in this) put(position, node)
            }
            if (node.discipline == QuestGatheringDiscipline.WOODCUTTING) {
                gathering.interactionBlocks.forEach { position ->
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            for (dx in -1..1) {
                                val nearby = BlockVec(
                                    position.blockX() + dx,
                                    position.blockY() + dy,
                                    position.blockZ() + dz,
                                )
                                if (nearby !in this && isWoodGatheringBlock(instance.getBlock(nearby))) put(nearby, node)
                            }
                        }
                    }
                }
            }
        }
    }
    private val gatheringNodesById = gatheringNodes.associateBy { it.id }
    // Read lock-free from event threads; every mutation happens under this runtime's monitor.
    private val gatheringInteractionByNode = ConcurrentHashMap<Int, Entity>()
    private val gatheringNodeByEntityId = ConcurrentHashMap<Int, QuestGatheringNode>()
    private val gatheringLabelByNode = ConcurrentHashMap<Int, Entity>()
    private val gatheringRespawnAtMillis = mutableMapOf<Int, Long>()
    @Volatile private var nextRespawnCheckAtMillis = Long.MIN_VALUE
    private val closeRequested = AtomicBoolean()
    private val closed = AtomicBoolean()

    init {
        gatheringNodes.forEach(::spawnGatheringLabel)
        gatheringNodes.filter {
            gatheringObjects.getValue(it.id).visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE
        }.forEach(::spawnGatheringInteraction)
    }

    fun gatheringNodeAt(position: BlockVec): QuestGatheringNode? = gatheringNodesByPosition[position]

    fun gatheringNodeForEntity(entity: Entity): QuestGatheringNode? = gatheringNodeByEntityId[entity.entityId]

    fun gatheringInteractionFor(node: QuestGatheringNode): Entity? = gatheringInteractionByNode[node.id]

    fun gatheringLabelFor(node: QuestGatheringNode): Entity? = gatheringLabelByNode[node.id]

    @Synchronized
    fun isGatheringNodeAvailable(node: QuestGatheringNode, nowMillis: Long): Boolean =
        (gatheringRespawnAtMillis[node.id] ?: Long.MIN_VALUE) <= nowMillis

    @Synchronized
    fun tryDepleteGatheringNode(node: QuestGatheringNode, nowMillis: Long, permanent: Boolean = false): Boolean {
        if (!isGatheringNodeAvailable(node, nowMillis)) return false
        gatheringRespawnAtMillis[node.id] = if (permanent) Long.MAX_VALUE else nowMillis + GATHERING_RESPAWN_MILLIS
        gatheringObjects.getValue(node.id).blocks.keys.forEach { position -> instance.setBlock(position, Block.AIR) }
        removeGatheringInteraction(node.id)
        removeGatheringLabel(node.id)
        return true
    }

    @Synchronized
    fun restoreGatheringNode(node: QuestGatheringNode) {
        if (node.id !in gatheringRespawnAtMillis) return
        gatheringRespawnAtMillis[node.id] = 0L
        respawnReadyGatheringNodes(System.currentTimeMillis())
    }

    /** Called from every player tick, so the actual scan runs at most once per [RESPAWN_CHECK_INTERVAL_MILLIS]. */
    fun respawnGatheringNodes(nowMillis: Long) {
        if (nowMillis < nextRespawnCheckAtMillis) return
        synchronized(this) {
            if (nowMillis < nextRespawnCheckAtMillis) return
            nextRespawnCheckAtMillis = nowMillis + RESPAWN_CHECK_INTERVAL_MILLIS
            respawnReadyGatheringNodes(nowMillis)
        }
    }

    @Synchronized
    private fun respawnReadyGatheringNodes(nowMillis: Long) {
        if (gatheringRespawnAtMillis.isEmpty()) return
        val pending = gatheringRespawnAtMillis.entries.iterator()
        while (pending.hasNext()) {
            val (nodeId, respawnAt) = pending.next()
            if (respawnAt > nowMillis) continue
            val node = gatheringNodesById.getValue(nodeId)
            val gathering = gatheringObjects.getValue(node.id)
            gathering.blocks.forEach { (position, block) -> instance.setBlock(position, block) }
            // Remove before spawning: a synchronous spawn callback treats a present entry as "still depleted".
            pending.remove()
            if (gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE) {
                spawnGatheringInteraction(node)
            }
            spawnGatheringLabel(node)
        }
    }

    private fun spawnGatheringLabel(node: QuestGatheringNode) {
        removeGatheringLabel(node.id)
        val gathering = gatheringObjects.getValue(node.id)
        val qualityColor = when (node.quality) {
            QuestGatheringQuality.COMMON -> NamedTextColor.WHITE
            QuestGatheringQuality.BOUNTIFUL -> NamedTextColor.GREEN
            QuestGatheringQuality.RARE -> NamedTextColor.LIGHT_PURPLE
        }
        val label = Entity(EntityType.TEXT_DISPLAY).apply {
            setHasPhysics(false)
            setNoGravity(true)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                val resourceLabel = Component.text("T${node.tier} ", NamedTextColor.GRAY)
                        .append(Component.text(node.discipline.commonResourceName, NamedTextColor.GOLD))
                        .append(Component.text("  ${node.quality.displayName}", qualityColor))
                meta.setText(
                    if (node.denseRegionId == node.id) {
                        Component.text("◆ ${node.discipline.denseRegionName} ◆\n", NamedTextColor.LIGHT_PURPLE)
                            .append(resourceLabel)
                    } else {
                        resourceLabel
                    },
                )
                meta.setScale(Vec(0.82, 0.82, 0.82))
                meta.setViewRange(20f)
                meta.setShadow(true)
                meta.setSeeThrough(true)
                meta.setBackgroundColor(0xA0000000.toInt())
                meta.setLineWidth(220)
                meta.setBrightness(15, 15)
            }
        }
        gatheringLabelByNode[node.id] = label
        val lift = when (node.discipline) {
            QuestGatheringDiscipline.SKINNING -> 1.75
            QuestGatheringDiscipline.WOODCUTTING -> 3.1
            QuestGatheringDiscipline.QUARRYING, QuestGatheringDiscipline.MINING ->
                (gathering.interactionHeight + 0.4f).coerceIn(2.8f, 4.8f).toDouble()
            QuestGatheringDiscipline.HERBALISM ->
                (gathering.interactionHeight + 0.35f).coerceIn(3.0f, 5.2f).toDouble()
        }
        val labelPosition = Pos(
            gathering.interactionPosition.x(),
            gathering.interactionPosition.y() + lift,
            gathering.interactionPosition.z(),
        )
        label.setInstance(instance, labelPosition).whenComplete { _, failure ->
            synchronized(this) {
                if (failure != null || closed.get() || gatheringRespawnAtMillis.containsKey(node.id)) {
                    gatheringLabelByNode.remove(node.id, label)
                    label.remove()
                }
            }
        }
    }

    private fun spawnGatheringInteraction(node: QuestGatheringNode) {
        removeGatheringInteraction(node.id)
        val gathering = gatheringObjects.getValue(node.id)
        check(gathering.visualKind == QuestMapStructureAssets.GatheringVisualKind.ANIMAL_CORPSE)
        val interaction = Entity(EntityType.COW).apply {
            setHasPhysics(false)
            setNoGravity(true)
            setPose(EntityPose.DYING)
        }
        gatheringInteractionByNode[node.id] = interaction
        gatheringNodeByEntityId[interaction.entityId] = node
        interaction.setInstance(instance, gathering.interactionPosition).whenComplete { _, failure ->
            synchronized(this) {
                if (failure != null || closed.get() || gatheringRespawnAtMillis.containsKey(node.id)) {
                    gatheringNodeByEntityId.remove(interaction.entityId)
                    gatheringInteractionByNode.remove(node.id, interaction)
                    interaction.remove()
                }
            }
        }
    }

    private fun removeGatheringInteraction(nodeId: Int) {
        gatheringInteractionByNode.remove(nodeId)?.let { interaction ->
            gatheringNodeByEntityId.remove(interaction.entityId)
            interaction.remove()
        }
    }

    private fun removeGatheringLabel(nodeId: Int) {
        gatheringLabelByNode.remove(nodeId)?.remove()
    }

    val isClosed: Boolean get() = closed.get()

    /**
     * Idempotent and never leaks the instance. An empty map unregisters immediately; otherwise the map
     * waits for in-flight outgoing transfers, drops disconnected players a late transfer stranded inside,
     * and after [CLOSE_EVICT_AFTER_TICKS] removes (kicks) whoever is still there before unregistering.
     */
    fun close() {
        if (!closeRequested.compareAndSet(false, true)) return
        if (!tryFinishClose()) closeWhenVacated(waitedTicks = 0)
    }

    private fun closeWhenVacated(waitedTicks: Int) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            instance.players.filterNot { it.isOnline }.forEach { it.remove() }
            if (waitedTicks >= CLOSE_EVICT_AFTER_TICKS && instance.players.isNotEmpty()) {
                System.err.println(
                    "QUEST_MAP_CLOSE_EVICT seed=${plan.seed} players=${instance.players.map { it.username }}",
                )
                instance.players.toList().forEach { it.remove() }
            }
            if (!tryFinishClose()) closeWhenVacated(waitedTicks + 1)
        }
    }

    private fun tryFinishClose(): Boolean {
        if (closed.get()) return true
        if (instance.players.isNotEmpty()) return false
        synchronized(this) {
            if (!closed.compareAndSet(false, true)) return true
            gatheringInteractionByNode.keys.toList().forEach(::removeGatheringInteraction)
            gatheringLabelByNode.keys.toList().forEach(::removeGatheringLabel)
        }
        if (instance.isRegistered) MinecraftServer.getInstanceManager().unregisterInstance(instance)
        return true
    }

    companion object {
        private const val GATHERING_RESPAWN_MILLIS = 90_000L
        private const val RESPAWN_CHECK_INTERVAL_MILLIS = 1_000L
        internal const val CLOSE_EVICT_AFTER_TICKS = 100

        private fun isWoodGatheringBlock(block: Block): Boolean {
            val name = block.name().toString()
            return name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem") ||
                name.endsWith("_hyphae") || name.endsWith("_leaves") || name.endsWith("_wart_block") ||
                name.endsWith("mangrove_roots")
        }

        fun prepare(
            seed: Long,
            candidateCount: Int = QuestMapCandidateSelector.DEFAULT_CANDIDATE_COUNT,
            customization: QuestMapCustomization = QuestMapCustomization.NONE,
            resourceTier: Int = 1,
            specialty: QuestGatheringDiscipline? = null,
        ): CompletableFuture<VerdantRoadQuestRuntime> {
            require(resourceTier in 1..4)
            val startedAt = System.nanoTime()
            val selection = QuestMapCandidateSelector.select(seed, candidateCount)
            val plan = selection.plan
            val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
            instance.setTime(6000)
            instance.defaultClock()?.pause()
            instance.setWeather(Weather.CLEAR)
            instance.setChunkSupplier(::LightingChunk)
            instance.setGenerator(VerdantRoadQuestGenerator(plan))
            val chunkRange = questMapRenderChunkRange(plan.size, ServerFlag.CHUNK_VIEW_DISTANCE)
            val chunkCoordinates = buildList {
                for (chunkX in chunkRange) {
                    for (chunkZ in chunkRange) add(chunkX to chunkZ)
                }
            }
            val chunks = chunkCoordinates.map { (chunkX, chunkZ) -> instance.loadChunk(chunkX, chunkZ) }
            return CompletableFuture.allOf(*chunks.toTypedArray()).thenApply {
                val missing = chunkCoordinates.filter { (chunkX, chunkZ) -> instance.getChunk(chunkX, chunkZ) == null }
                check(missing.isEmpty()) { "Quest map render coverage incomplete: ${missing.take(8)} (${missing.size} missing)" }
                val gatheringNodes = questGatheringNodes(plan, customization, specialty).map { it.copy(tier = resourceTier) }
                VerdantRoadQuestDecorator.decorate(instance, plan, gatheringNodes)
                val spawn = Pos(plan.start.x + 0.5, plan.heightAt(plan.start) + 1.0, plan.start.z + 0.5)
                VerdantRoadQuestRuntime(
                    requestedSeed = selection.requestedSeed,
                    plan = plan,
                    customization = customization,
                    gatheringNodes = gatheringNodes,
                    candidateScore = selection.score,
                    candidateCount = selection.attemptedCandidates,
                    instance = instance,
                    spawn = spawn,
                    preparationMillis = (System.nanoTime() - startedAt) / 1_000_000,
                    loadedChunkCount = chunkCoordinates.size,
                )
            }.whenComplete { _, failure ->
                if (failure != null) MinecraftServer.getInstanceManager().unregisterInstance(instance)
            }
        }
    }
}

internal fun questMapRenderChunkRange(mapSize: Int, viewDistance: Int): IntRange {
    require(mapSize > 0)
    val lastMapChunk = Math.floorDiv(mapSize - 1, 16)
    val renderBuffer = viewDistance.coerceAtLeast(2)
    return -renderBuffer..lastMapChunk + renderBuffer
}
