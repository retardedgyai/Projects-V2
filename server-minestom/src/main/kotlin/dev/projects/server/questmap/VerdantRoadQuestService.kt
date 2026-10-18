package dev.projects.server.questmap

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Concrete service for the generated ProjectS quest map, not a generic quest framework. */
internal class VerdantRoadQuestService(
    private val hubInstance: Instance,
    private val hubSpawn: Pos,
    private val masteryRepository: QuestGatheringMasteryRepository = QuestGatheringMasteryRepository(
        Path.of("config", "projects", "gathering-mastery"),
    ),
    private val durableGatheringReward: ((Player, QuestGatheringNode, Int) -> CompletableFuture<Boolean>)? = null,
    private val respawnResources: Boolean = true,
    private val technicalMessages: Boolean = true,
) {
    private val activeByPlayer = ConcurrentHashMap<UUID, VerdantRoadQuestRuntime>()
    private val gatheringMasteries = ConcurrentHashMap<UUID, QuestGatheringMastery>()
    private val activeGathering = ConcurrentHashMap<UUID, ActiveGathering>()
    /** Runtimes whose incoming transfer is in flight; their transfer callback, not disconnect, closes them. */
    private val transferring = ConcurrentHashMap.newKeySet<VerdantRoadQuestRuntime>()

    private data class ActiveGathering(
        val runtime: VerdantRoadQuestRuntime,
        val node: QuestGatheringNode,
        val targetPosition: BlockVec,
        val targetEntity: Entity?,
        val requiredTicks: Int,
        val progressDisplay: Entity,
        var elapsedTicks: Int = 0,
    )

    fun currentRuntime(playerId: UUID): VerdantRoadQuestRuntime? = activeByPlayer[playerId]

    /** The caller prepares the map off-thread and owns the durable expedition reservation. */
    fun enterPrepared(player: Player, runtime: VerdantRoadQuestRuntime): CompletableFuture<Boolean> {
        if (!player.isOnline || activeByPlayer.containsKey(player.uuid)) {
            if (runtime.instance.players.isEmpty()) runtime.close()
            return CompletableFuture.completedFuture(false)
        }
        return enterRuntime(player, runtime)
    }

    fun masterySnapshot(playerId: UUID): QuestGatheringMastery = gatheringMastery(playerId)

    fun startGathering(player: Player, blockPosition: BlockVec): Boolean {
        val runtime = activeByPlayer[player.uuid] ?: return false
        if (player.instance !== runtime.instance) return false
        val node = runtime.gatheringNodeAt(blockPosition) ?: return false
        return startGathering(player, runtime, node, blockPosition, null)
    }

    fun startGathering(player: Player, target: Entity): Boolean {
        val runtime = activeByPlayer[player.uuid] ?: return false
        if (player.instance !== runtime.instance || target.instance !== runtime.instance) return false
        val node = runtime.gatheringNodeForEntity(target) ?: return false
        val targetPosition = runtime.gatheringObjects.getValue(node.id).interactionPosition.asBlockVec()
        return startGathering(player, runtime, node, targetPosition, target)
    }

    private fun startGathering(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
        node: QuestGatheringNode,
        targetPosition: BlockVec,
        targetEntity: Entity?,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!runtime.isGatheringNodeAvailable(node, now)) {
            player.sendActionBar(Component.text("この採取物は再生中です。", NamedTextColor.DARK_GRAY))
            return true
        }
        if (!node.discipline.accepts(player.itemInMainHand)) {
            cancelGathering(player)
            player.sendActionBar(
                Component.text("${node.discipline.toolName}が必要です。", NamedTextColor.RED),
            )
            return true
        }
        val current = activeGathering[player.uuid]
        if (current != null && current.runtime === runtime && current.node.id == node.id) {
            beginGatheringInput(player)
            return true
        }
        cancelGathering(player)
        val mastery = gatheringMastery(player.uuid)
        val active = ActiveGathering(
            runtime = runtime,
            node = node,
            targetPosition = targetPosition,
            targetEntity = targetEntity,
            requiredTicks = mastery.harvestTicks(node.discipline),
            progressDisplay = createGatheringProgressDisplay(),
        )
        activeGathering[player.uuid] = active
        beginGatheringInput(player)
        updateGatheringProgressDisplay(active)
        spawnGatheringProgressDisplay(player, active)
        return true
    }

    fun cancelGathering(player: Player) {
        if (removeActiveGathering(player.uuid) != null) {
            player.refreshActiveHand(false, false, false)
            player.clearItemUse()
        }
    }

    private fun beginGatheringInput(player: Player) {
        player.refreshItemUse(PlayerHand.MAIN, GATHERING_HOLD_DURATION_TICKS)
        player.refreshActiveHand(true, false, false)
    }

    fun tick(player: Player) {
        val runtime = activeByPlayer[player.uuid] ?: return
        val now = System.currentTimeMillis()
        if (respawnResources) runtime.respawnGatheringNodes(now)
        if (player.aliveTicks % RARE_PARTICLE_INTERVAL_TICKS == 0L) {
            emitRareGatheringParticles(player, runtime, now)
        }
        val active = activeGathering[player.uuid] ?: return
        val interaction = active.runtime.gatheringObjects.getValue(active.node.id)
        val allowedDistance = maxOf(
            MIN_GATHERING_DISTANCE,
            interaction.interactionWidth / 2.0 + GATHERING_INTERACTION_REACH,
        )
        if (active.runtime !== runtime || player.instance !== runtime.instance ||
            !active.node.discipline.accepts(player.itemInMainHand) ||
            player.position.distanceSquared(active.targetPosition) > allowedDistance * allowedDistance ||
            (active.targetEntity != null && active.targetEntity.instance !== runtime.instance)
        ) {
            cancelGathering(player)
            return
        }
        if (!runtime.isGatheringNodeAvailable(active.node, now)) {
            cancelGathering(player)
            return
        }
        active.elapsedTicks++
        if (active.elapsedTicks == 1 || active.elapsedTicks % GATHERING_SWING_INTERVAL_TICKS == 0) {
            player.swingMainHand()
            playGatheringSound(player, active.node.discipline, active.targetPosition, completion = false)
        }
        updateGatheringProgressDisplay(active)
        if (active.elapsedTicks < active.requiredTicks) return
        cancelGathering(player)
        if (!runtime.tryDepleteGatheringNode(active.node, now, permanent = !respawnResources)) return
        playGatheringSound(player, active.node.discipline, active.targetPosition, completion = true)
        completeGathering(player, active.node)
    }

    fun unlockGatheringMasteryNode(player: Player, disciplineId: String, nodeId: String) {
        val discipline = QuestGatheringDiscipline.entries.firstOrNull { it.id == disciplineId.lowercase() }
        val node = QuestGatheringMasteryNode.byId(nodeId.lowercase())
        if (discipline == null || node == null) {
            player.sendMessage(Component.text("採取系統またはマスタリーノードが見つかりません。", NamedTextColor.RED))
            return
        }
        val current = gatheringMastery(player.uuid)
        when (val result = current.unlock(discipline, node)) {
            is QuestGatheringMasteryUnlockResult.Unlocked -> {
                gatheringMasteries[player.uuid] = result.mastery
                if (!masteryRepository.save(player.uuid, result.mastery)) {
                    System.err.println("GATHERING_MASTERY_SAVE_FAILURE player=${player.uuid}")
                }
                player.sendMessage(Component.text("${discipline.displayName}: ${node.displayName}を取得しました。", NamedTextColor.GREEN))
            }
            QuestGatheringMasteryUnlockResult.AlreadyUnlocked -> player.sendMessage(Component.text("そのノードは取得済みです。", NamedTextColor.YELLOW))
            QuestGatheringMasteryUnlockResult.MissingPrerequisite -> player.sendMessage(Component.text("先に${node.prerequisite?.id}を取得してください。", NamedTextColor.RED))
            QuestGatheringMasteryUnlockResult.KeystoneConflict -> player.sendMessage(Component.text("各採取系統で選べるキーストーンは1つだけです。", NamedTextColor.RED))
            QuestGatheringMasteryUnlockResult.NotEnoughPoints -> player.sendMessage(Component.text("マスタリーツリーポイントが足りません。", NamedTextColor.RED))
        }
    }

    private fun completeGathering(player: Player, node: QuestGatheringNode) {
        val previous = gatheringMastery(player.uuid)
        val previousLevel = previous.level(node.discipline)
        val amount = previous.yieldAmount(node.discipline, node.quality)
        val rewardSink = durableGatheringReward
        if (rewardSink != null) {
            val runtime = activeByPlayer[player.uuid] ?: return
            rewardSink(player, node, amount).whenComplete { saved, failure ->
                // This callback must run even if the player disconnected immediately after harvesting.
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    if (failure != null || saved != true) {
                        if (activeByPlayer[player.uuid] === runtime) runtime.restoreGatheringNode(node)
                        if (player.isOnline) player.sendMessage(Component.text("素材を受け取れませんでした。インベントリに空きを作って再度採取してください。", NamedTextColor.RED))
                    } else {
                        val current = gatheringMastery(player.uuid)
                        val updated = current.addExperience(node.discipline, node.quality.masteryExperience)
                        gatheringMasteries[player.uuid] = updated
                        if (!masteryRepository.save(player.uuid, updated)) {
                            System.err.println("GATHERING_MASTERY_SAVE_FAILURE player=${player.uuid}")
                        }
                        if (!player.isOnline) {
                            gatheringMasteries.remove(player.uuid, updated)
                            return@scheduleNextTick
                        }
                        player.sendMessage(Component.text("T${node.tier} ${node.discipline.commonResourceName} +$amount → インベントリ", NamedTextColor.GREEN))
                        if (updated.level(node.discipline) > current.level(node.discipline)) {
                            player.sendMessage(Component.text("${node.discipline.displayName}マスタリー Lv ${updated.level(node.discipline)}", NamedTextColor.GOLD))
                        }
                    }
                }
            }
            return
        }
        val nodeCenter = Vec(
            node.blockPosition.blockX() + 0.5,
            node.blockPosition.blockY() + 0.5,
            node.blockPosition.blockZ() + 0.5,
        )
        player.sendPacket(
            ParticlePacket(
                Particle.BLOCK.withBlock(node.discipline.nodeBlock),
                nodeCenter,
                Vec(0.42, 0.42, 0.42),
                0.12f,
                34,
            ),
        )
        giveGatheringReward(
            player,
            ItemStack.of(node.discipline.commonMaterial, amount)
                .withCustomName(Component.text(node.discipline.commonResourceName, NamedTextColor.WHITE)),
        )
        val rareDiscovered = node.quality == QuestGatheringQuality.RARE ||
            rareDiscoveryRoll(player.uuid, node, previous) < previous.rareDiscoveryChancePercent(node.discipline)
        if (rareDiscovered) {
            giveGatheringReward(
                player,
                ItemStack.of(node.discipline.rareMaterial)
                    .withCustomName(Component.text(node.discipline.rareResourceName, NamedTextColor.LIGHT_PURPLE))
                    .withGlowing(true),
            )
        }
        val updated = previous.addExperience(node.discipline, node.quality.masteryExperience)
        gatheringMasteries[player.uuid] = updated
        masteryRepository.save(player.uuid, updated)
        player.sendMessage(
            Component.text(
                "+$amount ${node.discipline.commonResourceName}  •  +${node.quality.masteryExperience} ${node.discipline.displayName}マスタリー経験値",
                if (rareDiscovered) NamedTextColor.LIGHT_PURPLE else NamedTextColor.GREEN,
            ),
        )
        val newLevel = updated.level(node.discipline)
        if (newLevel > previousLevel) {
            player.sendMessage(
                Component.text("${node.discipline.displayName}マスタリーがLv ${newLevel}になりました。", NamedTextColor.GOLD),
            )
        }
    }

    private fun giveGatheringReward(player: Player, item: ItemStack) {
        if (!player.inventory.addItemStack(item)) player.dropItem(item)
    }

    private fun playGatheringSound(
        player: Player,
        discipline: QuestGatheringDiscipline,
        position: BlockVec,
        completion: Boolean,
    ) {
        val key = when (discipline) {
            QuestGatheringDiscipline.SKINNING -> if (completion) "minecraft:entity.sheep.shear" else "minecraft:item.axe.scrape"
            QuestGatheringDiscipline.WOODCUTTING -> if (completion) "minecraft:block.wood.break" else "minecraft:block.wood.hit"
            QuestGatheringDiscipline.QUARRYING -> if (completion) "minecraft:block.stone.break" else "minecraft:block.stone.hit"
            QuestGatheringDiscipline.MINING -> if (completion) "minecraft:block.deepslate.break" else "minecraft:block.deepslate.hit"
            QuestGatheringDiscipline.HERBALISM -> if (completion) "minecraft:block.grass.break" else "minecraft:block.grass.hit"
        }
        val event = SoundEvent.fromKey(key) ?: return
        val pitch = if (completion) 0.9f else 0.82f + (player.aliveTicks % 4L) * 0.04f
        player.playSound(
            Sound.sound(event, Sound.Source.BLOCK, if (completion) 1.15f else 0.72f, pitch),
            position,
        )
    }

    private fun createGatheringProgressDisplay(): Entity = Entity(EntityType.TEXT_DISPLAY).apply {
        setAutoViewable(false)
        setNoGravity(true)
        editEntityMeta(TextDisplayMeta::class.java) { meta ->
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
            meta.setScale(Vec(0.72, 0.72, 0.72))
            meta.setViewRange(16f)
            meta.setShadow(true)
            meta.setSeeThrough(true)
            meta.setBackgroundColor(0xB0000000.toInt())
            meta.setLineWidth(180)
            meta.setBrightness(15, 15)
        }
    }

    private fun spawnGatheringProgressDisplay(player: Player, active: ActiveGathering) {
        val position = gatheringProgressDisplayPosition(player.position, player.eyeHeight, active.targetPosition)
        active.progressDisplay.setInstance(active.runtime.instance, position).thenRun {
            if (activeGathering[player.uuid] === active && player.instance === active.runtime.instance) {
                active.progressDisplay.addViewer(player)
            } else {
                active.progressDisplay.remove()
            }
        }
    }

    private fun updateGatheringProgressDisplay(active: ActiveGathering) {
        val filled = (active.elapsedTicks * GATHERING_BAR_SEGMENTS / active.requiredTicks)
            .coerceIn(0, GATHERING_BAR_SEGMENTS)
        val titleColor = if (active.node.quality == QuestGatheringQuality.RARE) {
            NamedTextColor.LIGHT_PURPLE
        } else {
            NamedTextColor.GOLD
        }
        val text = Component.text(
            "${active.node.quality.displayName} ${active.node.discipline.displayName}",
            titleColor,
        ).append(Component.newline())
            .append(Component.text("█".repeat(filled), NamedTextColor.GREEN))
            .append(Component.text("░".repeat(GATHERING_BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
        active.progressDisplay.editEntityMeta(TextDisplayMeta::class.java) { it.setText(text) }
    }

    private fun removeActiveGathering(playerId: UUID): ActiveGathering? =
        activeGathering.remove(playerId)?.also { it.progressDisplay.remove() }

    private fun rareDiscoveryRoll(
        playerId: UUID,
        node: QuestGatheringNode,
        mastery: QuestGatheringMastery,
    ): Int = Math.floorMod(
        playerId.mostSignificantBits xor playerId.leastSignificantBits xor
            (node.id * 2_654_435_761L) xor mastery.experience(node.discipline).toLong(),
        100L,
    ).toInt()

    private fun emitRareGatheringParticles(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
        nowMillis: Long,
    ) {
        runtime.gatheringNodes.asSequence()
            .filter { it.quality == QuestGatheringQuality.RARE && runtime.isGatheringNodeAvailable(it, nowMillis) }
            .filter { player.position.distanceSquared(it.blockPosition) <= RARE_PARTICLE_DISTANCE_SQUARED }
            .forEach { node ->
                player.sendPacket(
                    ParticlePacket(
                        Particle.END_ROD,
                        Vec(
                            node.blockPosition.blockX() + 0.5,
                            node.blockPosition.blockY() + 1.0,
                            node.blockPosition.blockZ() + 0.5,
                        ),
                        Vec(0.35, 0.55, 0.35),
                        0.01f,
                        3,
                    ),
                )
            }
    }

    private fun gatheringMastery(playerId: UUID): QuestGatheringMastery = gatheringMasteries.computeIfAbsent(playerId) {
        when (val loaded = masteryRepository.load(playerId)) {
            QuestGatheringMasteryLoadResult.Missing -> QuestGatheringMastery()
            is QuestGatheringMasteryLoadResult.Loaded -> loaded.mastery
            is QuestGatheringMasteryLoadResult.Invalid -> {
                System.err.println("Gathering mastery load blocked for $playerId: ${loaded.reason}")
                QuestGatheringMastery()
            }
        }
    }

    private fun ensureGatheringTools(player: Player) {
        val ownedDisciplines = player.inventory.itemStacks.mapNotNull { it.getTag(QUEST_GATHERING_TOOL_TAG) }.toSet()
        QuestGatheringDiscipline.entries.filterNot { it.id in ownedDisciplines }.forEach { discipline ->
            giveGatheringReward(player, discipline.toolItem())
        }
    }

    private fun enterRuntime(
        player: Player,
        runtime: VerdantRoadQuestRuntime,
    ): CompletableFuture<Boolean> {
        activeByPlayer[player.uuid] = runtime
        transferring += runtime
        player.setVelocity(Vec.ZERO)
        val transferStartedAt = System.nanoTime()
        val transfer = try {
            player.setInstance(runtime.instance, runtime.spawn)
        } catch (failure: Exception) {
            CompletableFuture.failedFuture(failure)
        }
        return transfer.handle { _, failure ->
            // Order matters: leave the transferring set before reading activeByPlayer so a concurrent
            // disconnect() either sees the transfer and defers, or this callback sees the disconnect.
            transferring -= runtime
            if (failure != null) {
                activeByPlayer.remove(player.uuid, runtime)
                runtime.close()
                if (player.isOnline) {
                    player.sendMessage(Component.text("クエストマップへの転送に失敗しました: ${failure.message}", NamedTextColor.RED))
                }
                println("Quest map transfer failed for ${player.username}: ${failure.message}")
                false
            } else if (!player.isOnline || activeByPlayer[player.uuid] !== runtime) {
                // Disconnected (or was cleaned up) while the transfer was in flight.
                activeByPlayer.remove(player.uuid, runtime)
                runtime.close()
                false
            } else {
                val transferMillis = (System.nanoTime() - transferStartedAt) / 1_000_000
                gatheringMastery(player.uuid)
                ensureGatheringTools(player)
                println(
                    "Quest map transfer complete: player=${player.username} seed=${runtime.plan.seed} " +
                        "style=${runtime.plan.style} layout=${runtime.plan.routeLayout} " +
                        "terrain=${runtime.plan.terrainProfile} chunks=${runtime.loadedChunkCount} " +
                        "ready=${runtime.preparationMillis}ms transfer=${transferMillis}ms",
                )
                if (technicalMessages) player.sendMessage(
                    Component.text(
                        "クエストマップ seed=${runtime.plan.seed} 景観=${runtime.plan.style} 経路=${runtime.plan.routeLayout} " +
                            "地形=${runtime.plan.terrainProfile} チャンク=${runtime.loadedChunkCount} " +
                            "候補数=${runtime.candidateCount} 評価=${"%.2f".format(runtime.candidateScore.total)} " +
                            "生成=${runtime.preparationMillis}ms 転送=${transferMillis}ms",
                        NamedTextColor.GREEN,
                    ),
                )
                player.sendMessage(Component.text("道を進むとボス地点へ到達できます。脇道には採取物や発見があります。", NamedTextColor.GRAY))
                player.sendMessage(Component.text("採取: 対応する道具を持ち、対象を右クリック長押ししてください。", NamedTextColor.GOLD))
                if (runtime.customization.modifiers.isNotEmpty()) {
                    player.sendMessage(Component.text("マップMOD", NamedTextColor.LIGHT_PURPLE))
                    runtime.customization.modifiers.forEach { modifier ->
                        player.sendMessage(Component.text("・${modifier.displayName()}", NamedTextColor.AQUA))
                    }
                }
                val denseRegionCount = runtime.gatheringNodes.mapNotNull { it.denseRegionId }.distinct().size
                if (denseRegionCount > 0) {
                    player.sendMessage(
                        Component.text(
                            "このマップには資源密集地域が${denseRegionCount}か所あります。",
                            NamedTextColor.LIGHT_PURPLE,
                        ),
                    )
                }
                true
            }
        }
    }

    fun returnToHub(player: Player): CompletableFuture<Boolean> {
        val runtime = activeByPlayer.remove(player.uuid) ?: return CompletableFuture.completedFuture(false)
        cancelGathering(player)
        player.setVelocity(Vec.ZERO)
        return player.setInstance(hubInstance, hubSpawn).handle { _, failure ->
            if (failure == null || !player.isOnline) {
                // A disconnect during the return already ran disconnect(); never re-register the map then.
                runtime.close()
                if (failure == null) player.sendMessage(Component.text("ProjectSの拠点へ戻りました。", NamedTextColor.GREEN))
                failure == null
            } else {
                activeByPlayer[player.uuid] = runtime
                player.sendMessage(Component.text("拠点への転送に失敗しました: ${failure.message}", NamedTextColor.RED))
                false
            }
        }
    }

    fun disconnect(playerId: UUID) {
        removeActiveGathering(playerId)
        gatheringMasteries.remove(playerId)?.let { masteryRepository.save(playerId, it) }
        val runtime = activeByPlayer.remove(playerId) ?: return
        // An in-flight incoming transfer closes the map from its own callback once it settles.
        if (runtime !in transferring) runtime.close()
    }

    private companion object {
        const val GATHERING_BAR_SEGMENTS = 12
        const val GATHERING_SWING_INTERVAL_TICKS = 6
        const val GATHERING_HOLD_DURATION_TICKS = 72_000L
        const val MIN_GATHERING_DISTANCE = 7.0
        const val GATHERING_INTERACTION_REACH = 4.0
        const val RARE_PARTICLE_INTERVAL_TICKS = 18L
        const val RARE_PARTICLE_DISTANCE_SQUARED = 28.0 * 28.0
    }
}
