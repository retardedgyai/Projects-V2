package dev.projects.server.coreloop

import dev.projects.server.mob.QuestCombatEncounter
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobRarity
import dev.projects.server.coreloop.ui.*
import dev.projects.server.coreloop.adventure.*
import dev.projects.server.questmap.*
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.event.player.*
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent
import net.minestom.server.command.builder.Command
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import kotlin.math.roundToInt

/** Entry point for the playable solo loop. The original combat laboratory remains opt-in. */
object CoreLoopServer {
    fun start() {
        CoreMmoTuning.balance = CoreMmoBalance.load(Path.of("config", "projects", "mmo-balance.properties"))
        val server = MinecraftServer.init(Auth.Offline())
        val hub = MinecraftServer.getInstanceManager().createInstanceContainer()
        val harbor = HarborScene.build(hub)
        val game = CoreLoopGame(hub, harbor)
        game.register()
        Runtime.getRuntime().addShutdownHook(Thread({ game.close() }, "projects-core-save-drain"))
        val port = System.getProperty("projects.port", "25565").toInt()
        server.start("127.0.0.1", port)
        println("PROJECTS_CORE_READY address=127.0.0.1:$port branch=astra-core-loop vanilla=26.2 save=config/projects/core-loop")
    }
}

internal class CoreLoopGame(private val hub: InstanceContainer, private val harbor: HarborScene.Result) : CoreMenuHost {
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "projects-core-ledger").apply { isDaemon = true } }
    private val mapBuilder = Executors.newSingleThreadExecutor { r -> Thread(r, "projects-core-map-builder").apply { isDaemon = true } }
    private val preparedMaps = CoreMapPreparation(mapBuilder)
    private val ledger = CoreAccountService(CoreAccountRepository(Path.of("config", "projects", "core-loop")))
    private val accounts = ConcurrentHashMap<UUID, CoreAccount>()
    private val connections = ConcurrentHashMap<UUID, Player>()
    private val rewards = CoreRewardQueue(ledger, io,
        onSnapshot = { id, a -> accounts[id] = a },
        onRetry = { id, message -> System.err.println("CORE_REWARD_RETRY player=$id: $message") })
    private val actors = ConcurrentHashMap<UUID, CorePlayerCombat>()
    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val busy = ConcurrentHashMap.newKeySet<UUID>()
    private val departing = ConcurrentHashMap<UUID, UUID>()
    // Own arenas during asynchronous transfer too; a disconnect can precede a late spawn callback.
    private val pendingTrialArenas = ConcurrentHashMap<UUID, BossArena>()
    private val potionReady = ConcurrentHashMap<UUID, Long>()
    private val lastUseAt = ConcurrentHashMap<UUID, Long>()
    private val menus = CoreLoopMenus(this)
    private val uiPack = CoreUiPackServer.start()
    private val dungeons = CoreDungeonExpeditions(object : CoreDungeonHost {
        override fun account(player: Player) = this@CoreLoopGame.account(player)
        override fun player(id: UUID) = connections[id]
        override fun connected(player: Player) = connections[player.uuid] === player && player.isOnline
        override fun eligible(player: Player) = account(player)?.journey?.chosen == true && player.instance === hub && account(player)?.activeRun == null &&
            !busy.contains(player.uuid) && !this@CoreLoopGame.isDeparting(player)
        override fun transact(player: Player, action: CoreAction, revision: Long?) = this@CoreLoopGame.transact(player.uuid, action, revision)
        override fun drain(player: Player) = rewards.drain(player.uuid)
        override fun harbor(player: Player) = moveToHub(player)
        override fun refreshed(player: Player) = refresh(player)
        override fun hurt(player: Player, damage: Double) { actors[player.uuid]?.hurt(damage) }
        override fun resetActions(player: Player) { actors[player.uuid]?.resetActions() }
        override fun revive(player: Player, fraction: Double) { actors[player.uuid]?.revive(fraction) }
        override fun reward(player: Player, action: CoreAction.DungeonReward) = rewards.submit(player.uuid, action)
        override fun showRunMenu(player: Player) = menus.dungeonRun(player)
    }, mapBuilder)
    private val questMaps = VerdantRoadQuestService(hub, harbor.spawn,
        durableGatheringReward = { player, node, count ->
            val run = accounts[player.uuid]?.activeRun
            val session = sessions[player.uuid]
            if (run == null || session == null || session.returning || session.runId != run.id) CompletableFuture.completedFuture(false)
            else rewards.submit(player.uuid, CoreAction.Gather(run.id, node.id.toString(), disciplineResource(node.discipline), count))
                .thenApply { it.successful }
        }, respawnResources = false, technicalMessages = false)

    private class Session(val owner: Player, val runId: UUID, val runtime: VerdantRoadQuestRuntime?,
        val combat: QuestEncounterCombat, val bossBar: BossBar, val loot: CoreWorldLoot, val arena: BossArena? = null) {
        val instance: InstanceContainer get() = arena?.instance ?: requireNotNull(runtime).instance
        var returning = false
        var rewardPending = false
        var killOrdinal = 0
        var ticks = 0L
        val discoveries = hashSetOf<Int>()
        var caches: CoreMapCaches? = null
        var adventures: AdventureRuntime? = null
    }

    override fun account(player: Player): CoreAccount? = accounts[player.uuid]
    override fun market(): List<CoreMarketEntry> = ledger.marketSnapshot()
    override fun buyOrders(): List<CoreBuyOrderEntry> = ledger.buyOrderSnapshot()
    override fun dungeonParties() = dungeons.parties.list()
    override fun dungeonView(player: Player) = dungeons.view(player)
    override fun playerName(id: UUID) = connections[id]?.username ?: id.toString().take(8)
    override fun dungeonLobby(player: Player, action: DungeonLobbyAction) = dungeons.lobby(player, action)
    override fun dungeonBoon(player: Player, boon: DungeonBoon) = dungeons.boon(player, boon)
    override fun dungeonRoute(player: Player, roomId: Int) = dungeons.route(player, roomId)
    override fun packed(player: Player): Boolean = uiPack?.enabled(player) == true
    override fun isDeparting(player: Player): Boolean = departing.containsKey(player.uuid) || dungeons.isDeparting(player)

    fun register() {
        val events = MinecraftServer.getGlobalEventHandler()
        events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            connections[event.player.uuid]?.takeIf { it !== event.player }?.let { previous ->
                disconnect(previous)
                previous.kick(CoreLoopItems.text("別の接続からログインしました。", NamedTextColor.YELLOW))
            }
            connections[event.player.uuid] = event.player
            rewards.retryPending(event.player.uuid)
            try {
                rewards.drain(event.player.uuid).get(15, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                event.player.kick(CoreLoopItems.text("前回の報酬を保存中です。少し待ってから再接続してください。", NamedTextColor.YELLOW))
                return@addListener
            }
            val loaded = CompletableFuture.supplyAsync({
                when (val opened = ledger.open(event.player.uuid)) {
                    is CoreAccountLoadResult.Invalid -> opened
                    is CoreAccountLoadResult.Ready -> {
                        var a = opened.account
                        a.activeRun?.let { run ->
                            val recovered = ledger.transact(a.playerId, CoreOperation(UUID.randomUUID(), a.revision, CoreAction.FinishRun(run.id)))
                            if (!recovered.successful) return@supplyAsync CoreAccountLoadResult.Invalid(recovered.message)
                            a = requireNotNull(recovered.account)
                        }
                        if (opened.newlyCreated) {
                            val starter = ledger.transact(a.playerId, CoreOperation(UUID.randomUUID(), a.revision, CoreAction.ClaimMap(1, System.nanoTime())))
                            if (!starter.successful) return@supplyAsync CoreAccountLoadResult.Invalid(starter.message)
                            a = requireNotNull(starter.account)
                        }
                        accounts[a.playerId] = a
                        CoreAccountLoadResult.Ready(a, opened.newlyCreated)
                    }
                }
            }, io).join()
            if (loaded is CoreAccountLoadResult.Invalid) {
                event.player.kick(CoreLoopItems.text("保存データを読み込めません。既存データは保持されています。${loaded.reason}", NamedTextColor.RED))
                return@addListener
            }
            event.spawningInstance = hub
            event.player.respawnPoint = harbor.spawn
        }
        events.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            val a = account(player) ?: return@addListener
            player.gameMode = GameMode.ADVENTURE
            player.food = 20
            player.foodSaturation = 20f
            player.getAttribute(Attribute.MAX_HEALTH).baseValue = 20.0
            if (event.isFirstSpawn) {
                actors[player.uuid] = CorePlayerCombat(player, { accounts[player.uuid]?.weaponTier ?: 1 },
                    { accounts[player.uuid]?.armorTier ?: 1 }, { dungeons.combat(player) ?: sessions[player.uuid]?.takeUnless { it.returning }?.combat },
                    statSource = { dungeons.stats(player, accounts[player.uuid]?.let(CoreAffixCatalog::stats) ?: CoreAffixStats()) },
                    weaponEnhancement = { accounts[player.uuid]?.weaponEnhancement?.level ?: 0 },
                    armorEnhancement = { accounts[player.uuid]?.armorEnhancement?.level ?: 0 },
                    weaponBroken = { accounts[player.uuid]?.weaponBroken ?: false },
                    armorBroken = { accounts[player.uuid]?.armorBroken ?: false },
                    weaponQuality = { accounts[player.uuid]?.weaponIdentity?.quality ?: 0 },
                    armorQuality = { accounts[player.uuid]?.armorIdentity?.quality ?: 0 },
                    journey = { accounts[player.uuid]?.journey ?: CoreJourney() },
                    weaponBase = { accounts[player.uuid]?.weaponIdentity?.base ?: CoreWeaponBase.STANDARD },
                    weaponLevelPower = { accounts[player.uuid]?.let { CoreJourneyRules.power(it.weaponIdentity, it.weaponTier) } ?: 1.0 },
                    armorLevelPower = { accounts[player.uuid]?.let { CoreJourneyRules.power(it.armorIdentity, it.armorTier) } ?: 1.0 },
                    onLesson = { bit -> if (accounts[player.uuid]?.journey?.knows(bit) == false) transact(player.uuid, CoreAction.LearnCombat(bit)) }) {
                    if (!dungeons.defeated(player)) {
                        player.showTitle(Title.title(CoreLoopItems.text("力尽きた…", NamedTextColor.RED), CoreLoopItems.text("獲得素材を持って港へ戻ります")))
                        returnToHarbor(player)
                    }
                }
                CoreLoopItems.refresh(player, a, initial = true, packed = packed(player))
                actors[player.uuid]?.reset()
                player.setHeldItemSlot(0)
                player.sendMessage(CoreLoopItems.text("開拓港へようこそ。正面の地図台から遠征へ出発できます。", NamedTextColor.GOLD))
                player.sendMessage(CoreLoopItems.text("ホットバー9番の「冒険の手帳」を右クリックすると、一周の流れを確認できます。"))
                a.maps.firstOrNull()?.let { preparedMaps.warm(player.uuid, it) }
                uiPack?.offer(player) { loadedPlayer, _ ->
                    refresh(loadedPlayer)
                    menus.refreshTheme(loadedPlayer)
                }
                if (!a.journey.chosen) player.scheduler().scheduleNextTick { if (connections[player.uuid] === player) menus.career(player) }
            }
            println("Player connected: ${player.username} uuid=${player.uuid} firstSpawn=${event.isFirstSpawn} coreLoop=true")
        }
        events.addListener(PlayerDisconnectEvent::class.java) { event -> disconnect(event.player) }
        events.addListener(InventoryPreClickEvent::class.java) { event ->
            if (menus.click(event)) return@addListener
            val stoneId = CoreLoopItems.stoneId(event.player.inventory.cursorItem)
            val currency = CoreLoopItems.currencyId(event.player.inventory.cursorItem)
            val gear = CoreLoopItems.gearSlot(event.clickedItem)
            if (currency != null && gear != null && (event.click is Click.Left || event.click is Click.Right)) {
                event.isCancelled = true
                if (requireHub(event.player)) {
                    event.player.inventory.cursorItem = ItemStack.AIR
                    refresh(event.player)
                    menus.confirmCraft(event.player, gear, currency)
                }
                return@addListener
            }
            if (stoneId != null && gear != null && (event.click is Click.Left || event.click is Click.Right)) {
                event.isCancelled = true
                if (requireHub(event.player)) {
                    // The icon is only a projection; the ledger owns the stone, even on cancel.
                    event.player.inventory.cursorItem = ItemStack.AIR
                    refresh(event.player)
                    menus.stoneDetail(event.player, stoneId, gear)
                }
                return@addListener
            }
            val mapId = CoreLoopItems.mapId(event.clickedItem)
            if (mapId != null && event.player.inventory.cursorItem.getTag(CoreLoopItems.actionTag) == "tablet" &&
                (event.click is Click.Left || event.click is Click.Right)) {
                event.isCancelled = true
                val a = account(event.player) ?: return@addListener
                applyTablet(event.player, mapId, a.revision) {
                    event.player.inventory.cursorItem = ItemStack.AIR
                    event.inventory.setItemStack(event.slot, account(event.player)?.maps?.firstOrNull { it.id == mapId }?.let(CoreLoopItems::map) ?: ItemStack.AIR)
                    refresh(event.player)
                }
            }
        }
        events.addListener(ItemDropEvent::class.java) { event ->
            if (event.itemStack.getTag(CoreLoopItems.actionTag) != null || event.itemStack.getTag(QUEST_GATHERING_TOOL_TAG) != null || CoreLoopItems.mapId(event.itemStack) != null) event.isCancelled = true
        }
        events.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.hand != PlayerHand.MAIN) return@addListener
            if (sessions[event.player.uuid]?.returning == true) { event.isCancelled = true; return@addListener }
            if (event.player.instance === hub) {
                val point = event.blockPosition
                harbor.facilities.firstOrNull { facility ->
                    facility.blockPositions.any { it.sameBlock(point) } && event.player.position.distance(facility.position) <= 6.0
                }?.let { facility ->
                    event.isCancelled = true
                    openFacility(event.player, facility.kind)
                }
            } else if (questMaps.startGathering(event.player, event.blockPosition)) event.isCancelled = true
        }
        events.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (sessions[event.player.uuid]?.returning == true) return@addListener
            if (event.hand == PlayerHand.MAIN && dungeons.interact(event.player, event.target, System.currentTimeMillis())) return@addListener
            if (event.hand == PlayerHand.MAIN && sessions[event.player.uuid]?.adventures?.interact(event.player, event.target) == true) return@addListener
            if (event.hand == PlayerHand.MAIN && sessions[event.player.uuid]?.caches?.interact(event.player, event.target) == true) return@addListener
            if (event.hand == PlayerHand.MAIN && !questMaps.startGathering(event.player, event.target)) {
                useAction(event.player, event.player.itemInMainHand)
            }
        }
        events.addListener(PlayerUseItemOnBlockEvent::class.java) { event ->
            if (event.hand == PlayerHand.MAIN) useAction(event.player, event.itemStack)
        }
        events.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.hand == PlayerHand.MAIN && useAction(event.player, event.itemStack)) event.isCancelled = true
        }
        events.addListener(PlayerCancelItemUseEvent::class.java) { event -> if (event.hand == PlayerHand.MAIN) questMaps.cancelGathering(event.player) }
        events.addListener(PlayerHandAnimationEvent::class.java) { event ->
            if (event.hand == PlayerHand.MAIN && event.player.itemInMainHand.getTag(CoreLoopItems.actionTag) == "weapon") actors[event.player.uuid]?.attack()
        }
        events.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (player.itemInMainHand.getTag(CoreLoopItems.actionTag) == "weapon") actors[player.uuid]?.attack()
        }
        events.addListener(PlayerSwapItemEvent::class.java) { event ->
            event.isCancelled = true
            actors[event.player.uuid]?.dodge()
        }
        events.addListener(PlayerBlockBreakEvent::class.java) { it.isCancelled = true }
        events.addListener(PlayerBlockPlaceEvent::class.java) { it.isCancelled = true }
        events.addListener(EntityDamageEvent::class.java) { event ->
            if (event.entity is Player) event.isCancelled = true // Explicit quest attacks own damage; no vanilla contact/fire ticks.
        }
        events.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            questMaps.tick(player)
            actors[player.uuid]?.tick()
            if (player.aliveTicks % 5 == 0L) updateHud(player)
            if (player.instance === hub && player.position.y() < 38) { player.teleport(harbor.spawn); actors[player.uuid]?.reset() }
            if (player.instance !== hub && player.position.y() < 15 && !isDeparting(player)) returnToHarbor(player)
        }
        events.addListener(InstanceTickEvent::class.java) { event ->
            sessions.values.filter { it.instance === event.instance }.forEach { session -> tickSession(session) }
            dungeons.tick(event.instance, System.currentTimeMillis())
        }
        MinecraftServer.getCommandManager().register(Command("projects").apply {
            setDefaultExecutor { sender, _ -> (sender as? Player)?.let { menus.journal(it) } }
        })
        MinecraftServer.getCommandManager().register(Command("hub").apply {
            setDefaultExecutor { sender, _ -> (sender as? Player)?.let { returnToHarbor(it) } }
        })
    }

    private fun useAction(player: Player, item: ItemStack): Boolean {
        val mapId = CoreLoopItems.mapId(item)
        val action = item.getTag(CoreLoopItems.actionTag)
        if (mapId == null && action == null) return false
        val previous = lastUseAt.put(player.uuid, player.aliveTicks)
        if (previous != null && player.aliveTicks - previous < 2) return true
        when {
            mapId != null -> account(player)?.let { depart(player, mapId, it.revision) }
            action == "journal" -> menus.journal(player)
            action == "affix" -> CoreLoopItems.stoneId(item)?.let { menus.stoneDetail(player, it) }
            action == "currency" -> menus.affixes(player)
            action == "armor" -> menus.gearMods(player, CoreGearSlot.ARMOR)
            action == "weapon" -> actors[player.uuid]?.skill(0)
            action?.startsWith("skill:") == true -> action.substringAfter(':').toIntOrNull()?.let { actors[player.uuid]?.skill(it) }
            action == "potion" -> consume(player, CoreResource.POTION)
            action == "whetstone" -> consume(player, CoreResource.WHETSTONE)
        }
        return true
    }

    private fun openFacility(player: Player, kind: HarborFacilityKind) = when (kind) {
        HarborFacilityKind.EXPEDITIONS -> menus.expeditions(player)
        HarborFacilityKind.WORKSHOP -> menus.workshop(player)
        HarborFacilityKind.STORAGE -> menus.storage(player)
        HarborFacilityKind.SUPPLIES -> menus.supplies(player)
        HarborFacilityKind.MASTERY -> menus.mastery(player)
    }

    override fun requireHub(player: Player): Boolean {
        val valid = player.instance === hub && account(player)?.activeRun == null && !isDeparting(player)
        if (!valid) player.sendMessage(CoreLoopItems.text("港へ帰還してから操作してください。", NamedTextColor.YELLOW))
        return valid
    }

    private fun transact(playerId: UUID, action: CoreAction, revision: Long? = null): CompletableFuture<CoreTransactionResult> =
        CompletableFuture.supplyAsync({
            val current = ledger.snapshot(playerId)
                ?: return@supplyAsync CoreTransactionResult(CoreTransactionStatus.UNAVAILABLE, null, "データを読み込めません")
            ledger.transact(playerId, CoreOperation(UUID.randomUUID(), revision ?: current.revision, action)).also { result ->
                result.account?.let { next ->
                    accounts[playerId] = next
                    if (result.successful && (next.journey.level != current.journey.level || CoreJourneyRules.next(next) != CoreJourneyRules.next(current))) {
                        connections[playerId]?.let { player -> player.scheduler().scheduleNextTick {
                            if (connections[playerId] === player && player.isOnline) {
                                if (next.journey.level > current.journey.level) player.sendMessage(CoreLoopItems.text("冒険Lv${next.journey.level}！ 成長と職業の手帳で解放内容を確認できます", NamedTextColor.GOLD))
                                player.sendMessage(CoreLoopItems.text("次の目標：${CoreJourneyRules.next(next)}", NamedTextColor.AQUA))
                                refresh(player)
                            }
                        } }
                    }
                }
                val other = when (action) { is CoreAction.BuyOffer -> action.seller; is CoreAction.FillBuyOrder -> action.buyer; else -> null }
                if (other != null && result.successful) ledger.snapshot(other)?.let { accounts[other] = it }
            }
        }, io)

    override fun mutate(player: Player, action: CoreAction, revision: Long, onRejected: (() -> Unit)?, after: () -> Unit) {
        if (!busy.add(player.uuid)) return
        val beforeEnhancement = (action as? CoreAction.EnhanceEquipment)?.let { operation ->
            account(player)?.let { CoreEnhancementCatalog.state(it, operation.gear).level }
        }
        transact(player.uuid, action, revision).whenComplete { result, error ->
            player.scheduler().scheduleNextTick {
                if (connections[player.uuid] !== player) return@scheduleNextTick
                busy.remove(player.uuid)
                if (!player.isOnline) return@scheduleNextTick
                if (error != null || result == null) {
                    player.sendMessage(CoreLoopItems.text("保存できませんでした。操作をやり直してください。", NamedTextColor.RED))
                    System.err.println("CORE_TRANSACTION_FAILURE player=${player.uuid}: $error")
                    onRejected?.invoke()
                } else {
                    val broke = (action as? CoreAction.EnhanceEquipment)?.let { op -> result.account?.let { CoreEconomy.broken(it, op.gear) } } == true
                    player.sendMessage(CoreLoopItems.text(result.message, if (result.successful && !broke) NamedTextColor.GREEN else NamedTextColor.RED))
                    if (result.successful) {
                        val enhanced = (action as? CoreAction.EnhanceEquipment)?.let { operation ->
                            result.account?.let { CoreEnhancementCatalog.state(it, operation.gear).level > (beforeEnhancement ?: 0) }
                        }
                        val sound = if (broke) SoundEvent.ENTITY_ITEM_BREAK else when (enhanced) {
                            true -> SoundEvent.BLOCK_SMITHING_TABLE_USE
                            false -> SoundEvent.BLOCK_ANVIL_LAND
                            null -> SoundEvent.BLOCK_NOTE_BLOCK_CHIME
                        }
                        player.playSound(Sound.sound(sound, Sound.Source.MASTER, 0.45f, if (enhanced == false) 0.75f else 1.2f))
                    }
                    refresh(player)
                    if (result.successful) after() else (onRejected ?: { menus.journal(player) })()
                }
            }
        }
    }

    override fun applyTablet(player: Player, mapId: UUID, revision: Long, onRejected: (() -> Unit)?, after: () -> Unit) {
        if (!requireHub(player)) return
        val map = account(player)?.maps?.firstOrNull { it.id == mapId } ?: return
        val modifier = CoreLoopItems.nextModifier(map, System.nanoTime())
        if (modifier == null) { player.sendMessage(CoreLoopItems.text("MODは最大3個です。", NamedTextColor.YELLOW)); return }
        mutate(player, CoreAction.ApplyTablet(mapId, modifier), revision, onRejected) {
            account(player)?.maps?.firstOrNull { it.id == mapId }?.let { preparedMaps.warm(player.uuid, it) }
            after()
        }
    }

    override fun warmMap(player: Player, map: CoreOwnedMap): Boolean = preparedMaps.warm(player.uuid, map)

    private fun refresh(player: Player) {
        val a = account(player) ?: return
        player.getAttribute(Attribute.MAX_HEALTH).baseValue = 20.0
        if (player.instance === hub) actors[player.uuid]?.reset()
        CoreLoopItems.refresh(player, a, packed = packed(player))
    }

    override fun depart(player: Player, mapId: UUID, revision: Long) {
        if (account(player)?.journey?.chosen != true) { menus.career(player); return }
        if (!requireHub(player) || !busy.add(player.uuid)) return
        val runId = UUID.randomUUID()
        departing[player.uuid] = runId
        player.closeInventory()
        actors[player.uuid]?.resetActions()
        player.sendMessage(CoreLoopItems.text("地図を広げています… 地形と遠征先を準備中。", NamedTextColor.GOLD))
        transact(player.uuid, CoreAction.StartRun(mapId, runId), revision).whenComplete { result, failure ->
            if (failure != null || result?.successful != true) {
                player.scheduler().scheduleNextTick {
                    if (connections[player.uuid] !== player || !departing.remove(player.uuid, runId)) return@scheduleNextTick
                    busy.remove(player.uuid)
                    player.sendMessage(CoreLoopItems.text(result?.message ?: "遠征を開始できませんでした。", NamedTextColor.RED))
                }
                return@whenComplete
            }
            val map = requireNotNull(result.account?.activeRun).map
            if (!player.isOnline || connections[player.uuid] !== player || departing[player.uuid] != runId) return@whenComplete
            preparedMaps.take(player.uuid, map)
                .whenComplete { runtime, generationError ->
                    if (!player.isOnline || connections[player.uuid] !== player || departing[player.uuid] != runId) {
                        runtime?.close()
                        return@whenComplete
                    }
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        if (generationError != null || runtime == null) {
                            System.err.println("CORE_MAP_GENERATION_FAILURE run=$runId: $generationError")
                            abortDeparture(player, runId)
                        } else if (!player.isOnline || connections[player.uuid] !== player || departing[player.uuid] != runId || accounts[player.uuid]?.activeRun?.id != runId) {
                            runtime.close()
                        } else {
                            questMaps.enterPrepared(player, runtime).whenComplete { entered, transferError ->
                                MinecraftServer.getSchedulerManager().scheduleNextTick {
                                    if (entered != true || transferError != null) abortDeparture(player, runId)
                                    else if (player.isOnline && connections[player.uuid] === player && departing[player.uuid] == runId) {
                                        departing.remove(player.uuid); busy.remove(player.uuid)
                                        try {
                                            attachSession(player, runtime, runId, map.tier)
                                            refresh(player); actors[player.uuid]?.reset()
                                            player.sendMessage(CoreLoopItems.text("T${map.tier} 遠征開始。道の先にボス。裂け目・儀式の寄り道には専用オーブと欠片があります。", NamedTextColor.GOLD))
                                        } catch (failure: Exception) {
                                            System.err.println("CORE_ENCOUNTER_SETUP_FAILURE run=$runId: $failure")
                                            questMaps.returnToHub(player).whenComplete { _, _ -> abortDeparture(player, runId) }
                                        }
                                    } else if (questMaps.currentRuntime(player.uuid) === runtime) {
                                        questMaps.disconnect(player.uuid)
                                    } else if (runtime.instance.players.isEmpty()) {
                                        runtime.close()
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    override fun departTrial(player: Player, kind: CoreActivityKind, tier: Int, revision: Long) {
        if (account(player)?.journey?.chosen != true) { menus.career(player); return }
        if (!requireHub(player) || !busy.add(player.uuid)) return
        val runId = UUID.randomUUID()
        departing[player.uuid] = runId
        player.closeInventory()
        actors[player.uuid]?.resetActions()
        player.sendMessage(CoreLoopItems.text("${kind.displayName}の境界を開いています…", NamedTextColor.LIGHT_PURPLE))
        transact(player.uuid, CoreAction.StartTrial(kind.bossId, tier, runId), revision).whenComplete { result, failure ->
            if (failure != null || result?.successful != true) {
                player.scheduler().scheduleNextTick {
                    if (connections[player.uuid] !== player || !departing.remove(player.uuid, runId)) return@scheduleNextTick
                    busy.remove(player.uuid)
                    player.sendMessage(CoreLoopItems.text(result?.message ?: "試練を開始できませんでした。", NamedTextColor.RED))
                }
                return@whenComplete
            }
            CompletableFuture.supplyAsync({ BossArenaFactory.create(kind.bossId, tier) }, mapBuilder).whenComplete { arena, buildFailure ->
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    if (connections[player.uuid] !== player || !player.isOnline || departing[player.uuid] != runId) {
                        arena?.dispose()
                        return@scheduleNextTick
                    }
                    if (buildFailure != null || arena == null) {
                        System.err.println("CORE_TRIAL_BUILD_FAILURE run=$runId: $buildFailure")
                        abortDeparture(player, runId)
                        return@scheduleNextTick
                    }
                    pendingTrialArenas[player.uuid] = arena
                    val transfer = try { player.setInstance(arena.instance, arena.playerSpawn) }
                        catch (error: Exception) { CompletableFuture.failedFuture<Void>(error) }
                    transfer.whenComplete { _, transferFailure ->
                        MinecraftServer.getSchedulerManager().scheduleNextTick {
                            if (connections[player.uuid] !== player || !player.isOnline || departing[player.uuid] != runId) {
                                pendingTrialArenas.remove(player.uuid, arena)
                                // Minestom may finish spawning this disconnected old Player after
                                // PlayerDisconnectEvent. Remove that exact instance, never a new login.
                                if (player.instance === arena.instance) player.remove()
                                arena.dispose()
                                return@scheduleNextTick
                            }
                            try {
                                if (transferFailure != null) throw IllegalStateException("Trial transfer failed", transferFailure)
                                attachTrial(player, arena, runId, tier)
                                pendingTrialArenas.remove(player.uuid, arena)
                                departing.remove(player.uuid, runId); busy.remove(player.uuid)
                                actors[player.uuid]?.reset(); refresh(player)
                                player.sendMessage(CoreLoopItems.text("${arena.displayName}へ到着。${sessions[player.uuid]?.combat?.bossName()}を討伐せよ！", NamedTextColor.GOLD))
                                println("CORE_TRIAL_STARTED player=${player.username} run=$runId kind=${kind.name} tier=$tier transfer=complete")
                            } catch (error: Exception) {
                                System.err.println("CORE_TRIAL_SETUP_FAILURE run=$runId: $error")
                                sessions[player.uuid]?.takeIf { it.runId == runId }?.let { failed ->
                                    sessions.remove(player.uuid, failed)
                                    failed.returning = true
                                    failed.combat.dispose(); failed.loot.dispose()
                                    player.hideBossBar(failed.bossBar)
                                }
                                moveToHub(player).whenComplete { _, returnFailure ->
                                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                                        pendingTrialArenas.remove(player.uuid, arena)
                                        if (returnFailure == null) { arena.dispose(); abortDeparture(player, runId) }
                                        else {
                                            player.kick(CoreLoopItems.text("試練の準備に失敗しました。再接続してください。", NamedTextColor.RED))
                                            if (player.instance === arena.instance) player.remove()
                                            arena.dispose()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun attachTrial(player: Player, arena: BossArena, runId: UUID, tier: Int) {
        val run = requireNotNull(account(player)?.activeRun).also { check(it.id == runId && it.trialId == arena.bossId) }
        val bar = BossBar.bossBar(CoreLoopItems.text(arena.displayName, NamedTextColor.RED), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)
        val combat = QuestEncounterCombat(arena.instance, tier, emptyList(), arena.bossSpawn,
            onMobDefeated = { _, boss -> onMobDefeated(player, boss) },
            damagePlayer = { target, damage -> actors[target.uuid]?.hurt(damage) },
            canTarget = { target -> target === player && actors[target.uuid]?.defeated == false && sessions[target.uuid]?.returning == false },
            contentSeed = run.map.seed, explicitBossArchetype = arena.archetype)
        try {
            val loot = CoreWorldLoot(player, arena.instance, run, { rewards.submit(player.uuid, it) }, { refresh(player) })
            sessions[player.uuid] = Session(player, runId, null, combat, bar, loot, arena)
        } catch (failure: Exception) { combat.dispose(); throw failure }
    }

    private fun moveToHub(player: Player): CompletableFuture<Boolean> = try {
        if (player.instance === hub) CompletableFuture.completedFuture(true)
        else player.setInstance(hub, harbor.spawn).thenApply { true }
    } catch (failure: Exception) { CompletableFuture.failedFuture(failure) }

    private fun abortDeparture(player: Player, runId: UUID) {
        if (connections[player.uuid] !== player || accounts[player.uuid]?.activeRun?.id != runId) return
        departing.remove(player.uuid, runId); busy.remove(player.uuid)
        transact(player.uuid, CoreAction.AbortRun(runId)).whenComplete { result, _ ->
            player.scheduler().scheduleNextTick {
                if (connections[player.uuid] !== player) return@scheduleNextTick
                player.sendMessage(CoreLoopItems.text(result?.message ?: "遠征の準備に失敗しました。再接続時に状態を確認します。", NamedTextColor.YELLOW))
                refresh(player)
            }
        }
    }

    private fun attachSession(player: Player, runtime: VerdantRoadQuestRuntime, runId: UUID, tier: Int) {
        val plan = runtime.plan
        fun pos(point: QuestMapPoint): Pos = Pos(point.x + 0.5, plan.heightAt(point) + 1.0, point.z + 0.5)
        val groups = plan.contents.filter { it.kind == QuestMapContentKind.COMBAT }.map { content ->
            val center = pos(content.position)
            val positions = listOf(center, center.add(2.0, 0.0, 1.0), center.add(-2.0, 0.0, 1.0)).map { p ->
                QuestCombatPlacement.resolve(runtime.instance, p)
            }
            QuestCombatEncounter(positions)
        }
        val bar = BossBar.bossBar(CoreLoopItems.text("裂け目の執行官", NamedTextColor.RED), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)
        val combat = QuestEncounterCombat(runtime.instance, tier, groups, QuestCombatPlacement.resolve(runtime.instance, pos(plan.boss)),
            onMobDefeated = { _, boss -> onMobDefeated(player, boss) }, damagePlayer = { target, damage -> actors[target.uuid]?.hurt(damage) },
            canTarget = { target -> target.uuid == player.uuid && actors[target.uuid]?.defeated == false && sessions[target.uuid]?.returning == false },
            contentSeed = plan.seed, encounterLevel = account(player)?.activeRun?.map?.level ?: CoreJourneyRules.floor(tier))
        val run = requireNotNull(account(player)?.activeRun)
        val loot = CoreWorldLoot(player, runtime.instance, run,
            reward = { rewards.submit(player.uuid, it) },
            inventoryChanged = { account(player)?.let { CoreLoopItems.refresh(player, it, packed = packed(player)) } })
        val session = Session(player, runId, runtime, combat, bar, loot)
        try {
            session.caches = CoreMapCaches(player, runtime.instance,
            plan.contents.filter { it.kind == QuestMapContentKind.DISCOVERY }.map { pos(it.position) },
            guarded = { at -> combat.combatTargets().any { it.position.distance(at) < 12.0 } },
            opened = { index, at ->
                session.discoveries.add(index)
                loot.spawn("cache:$index", CoreLootKind.ELITE, at)
            })
            val sites = CoreAdventurePlacement.sites(runtime)
            session.adventures = AdventureRuntime(runtime.instance, combat, sites,
                canParticipate = { it === player && !session.returning && actors[it.uuid]?.defeated == false },
                onReward = { reward -> awardActivity(session, reward) })
        } catch (failure: Exception) {
            session.adventures?.dispose(); session.caches?.dispose(); combat.dispose(); loot.dispose()
            throw failure
        }
        sessions[player.uuid] = session
        println("CORE_RUN_STARTED player=${player.username} run=$runId tier=$tier mobs=${groups.size * 3} seed=${plan.seed}")
    }

    private fun onMobDefeated(player: Player, boss: Boolean) {
        val session = sessions[player.uuid]?.takeUnless { it.returning } ?: return
        if (boss) awardBoss(session)
        session.combat.latestDefeat?.let { defeated ->
            val kind = when (defeated.rarity) {
                QuestMobRarity.BOSS -> CoreLootKind.BOSS
                QuestMobRarity.ELITE -> CoreLootKind.ELITE
                else -> CoreLootKind.NORMAL
            }
            session.loot.spawn(defeated.sourceId, kind, defeated.position)
        }
    }

    private fun awardActivity(session: Session, reward: AdventureReward) {
        if (session.returning || sessions[session.owner.uuid] !== session) return
        val kind = CoreActivityKind.valueOf(reward.kind.name)
        // A distinct deterministic source per reward unit makes retries/reconnect drains idempotent.
        val saved = (0 until reward.rewardCount).map { index ->
            rewards.submit(session.owner.uuid, CoreAction.ActivityReward(session.runId, "${reward.sourceId}:reward:$index", kind))
        }
        CompletableFuture.allOf(*saved.toTypedArray()).whenComplete { _, failure ->
            session.owner.scheduler().scheduleNextTick {
                if (connections[session.owner.uuid] !== session.owner) return@scheduleNextTick
                if (failure == null && saved.all { it.getNow(null)?.successful == true }) {
                    refresh(session.owner)
                    session.owner.sendMessage(CoreLoopItems.text("${kind.displayName}報酬を保存：${kind.currency.displayName} ×${reward.rewardCount} / 欠片 ×${reward.rewardCount}。欠片3個で専用ボスへ！", NamedTextColor.LIGHT_PURPLE))
                }
            }
        }
    }

    private fun awardBoss(session: Session) {
        if (session.rewardPending || accounts[session.owner.uuid]?.activeRun?.bossDefeated == true) return
        session.rewardPending = true
        rewards.submit(session.owner.uuid, CoreAction.BossReward(session.runId)).whenComplete { result, failure ->
            session.owner.scheduler().scheduleNextTick {
                session.rewardPending = false
                if (failure != null || result?.successful != true) {
                    session.owner.sendMessage(CoreLoopItems.text("討伐報酬の保存を再試行しています。", NamedTextColor.YELLOW))
                    System.err.println("CORE_BOSS_REWARD_RETRY run=${session.runId}: ${result?.message ?: failure}")
                } else {
                    session.owner.hideBossBar(session.bossBar)
                    refresh(session.owner)
                    session.owner.showTitle(Title.title(CoreLoopItems.text("討伐達成", NamedTextColor.GOLD), CoreLoopItems.text(
                        if (session.arena == null) "討伐証・次の地図・試練の欠片を保存" else "専用オーブ2個・高揚1個・討伐証を保存")))
                    session.owner.playSound(Sound.sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE, Sound.Source.MASTER, 0.6f, 1f))
                    session.owner.sendMessage(CoreLoopItems.text("羅針盤から港へ帰還し、工房で装備を更新しよう。探索は続けても構いません。", NamedTextColor.GREEN))
                    println("CORE_BOSS_REWARD_COMMITTED player=${session.owner.username} run=${session.runId} revision=${result.account?.revision}")
                }
            }
        }
    }

    private fun tickSession(session: Session) {
        if (session.returning) return
        session.combat.tick(System.currentTimeMillis())
        if (session.returning) return
        session.adventures?.tick(System.currentTimeMillis())
        session.loot.tick()
        session.ticks++
        if (session.combat.bossDefeated && session.ticks % 100 == 0L) awardBoss(session)
        if (session.ticks % 20 != 0L) return
        if (actors[session.owner.uuid]?.defeated == true && !session.rewardPending) {
            returnToHarbor(session.owner)
            return
        }
        val p = session.owner
        if (session.combat.bossDefeated) p.hideBossBar(session.bossBar)
        else if (session.arena != null || p.position.distanceSquared(requireNotNull(session.runtime).plan.boss.let { Pos(it.x.toDouble(), p.position.y(), it.z.toDouble()) }) < 45 * 45) {
            session.bossBar.name(CoreLoopItems.text("${session.combat.bossName()}  ${session.combat.bossHealth().roundToInt()} / ${session.combat.bossMaxHealth().roundToInt()}", NamedTextColor.RED))
            session.bossBar.progress((session.combat.bossHealth() / session.combat.bossMaxHealth()).toFloat().coerceIn(0f, 1f))
            p.showBossBar(session.bossBar)
        } else p.hideBossBar(session.bossBar)
    }

    override fun returnToHarbor(player: Player) {
        if (dungeons.returnToHarbor(player)) return
        val session = sessions[player.uuid] ?: return
        if (session.returning || session.rewardPending) return
        if (session.combat.bossDefeated && account(player)?.activeRun?.bossDefeated != true) { awardBoss(session); return }
        session.returning = true
        session.combat.stopActionsForReturn()
        session.adventures?.failAll(); session.adventures?.dispose()
        questMaps.cancelGathering(player)
        actors[player.uuid]?.resetActions()
        player.closeInventory()
        session.loot.collectAll().thenCompose { rewards.drain(player.uuid) }
            .thenCompose { transact(player.uuid, CoreAction.FinishRun(session.runId)) }.whenComplete { result, error ->
            player.scheduler().scheduleNextTick {
                if (connections[player.uuid] !== player || sessions[player.uuid] !== session) return@scheduleNextTick
                if (error != null || result?.successful != true) {
                    session.returning = false
                    player.sendMessage(CoreLoopItems.text("帰還を保存できません。もう一度操作してください。", NamedTextColor.RED))
                    return@scheduleNextTick
                }
                player.hideBossBar(session.bossBar)
                session.combat.dispose()
                session.loot.dispose(); session.caches?.dispose()
                val transfer = if (session.arena != null) moveToHub(player) else questMaps.returnToHub(player)
                transfer.whenComplete { returned, failure ->
                    player.scheduler().scheduleNextTick {
                        if (connections[player.uuid] !== player || sessions[player.uuid] !== session) {
                            if (!player.isOnline && session.arena != null) player.remove()
                            session.arena?.takeIf { it.instance.players.isEmpty() }?.dispose()
                            return@scheduleNextTick
                        }
                        if (returned == true) {
                            sessions.remove(player.uuid, session)
                            session.arena?.dispose()
                            actors[player.uuid]?.reset(); refresh(player)
                            player.sendMessage(CoreLoopItems.text("採取素材を精製 → 工房で装備を制作 → 装備庫から使う・売る。市場で素材の売買もできます。", NamedTextColor.GOLD))
                            menus.journal(player)
                            println("CORE_RUN_RETURNED player=${player.username} run=${session.runId}")
                        } else {
                            System.err.println("CORE_RETURN_TRANSFER_FAILURE run=${session.runId}: $failure")
                            player.kick(CoreLoopItems.text("帰還先を読み直すため再接続してください。獲得素材は保存済みです。", NamedTextColor.YELLOW))
                        }
                    }
                }
            }
        }
    }

    private fun consume(player: Player, resource: CoreResource) {
        val actor = actors[player.uuid] ?: return
        if (actor.defeated || (sessions[player.uuid]?.returning != false && dungeons.combat(player) == null) || !busy.add(player.uuid)) return
        if (resource == CoreResource.POTION && (actor.health >= actor.maxHealth || System.currentTimeMillis() < (potionReady[player.uuid] ?: 0L))) {
            busy.remove(player.uuid)
            player.sendActionBar(CoreLoopItems.text("HPが満タン、または回復薬の再使用待ちです。", NamedTextColor.YELLOW))
            return
        }
        transact(player.uuid, CoreAction.Consume(resource)).whenComplete { result, _ ->
            player.scheduler().scheduleNextTick {
                if (connections[player.uuid] !== player) return@scheduleNextTick
                busy.remove(player.uuid)
                if (result?.status == CoreTransactionStatus.COMMITTED) {
                    if (resource == CoreResource.POTION) { actor.healPotion(); potionReady[player.uuid] = System.currentTimeMillis() + 10_000 }
                    else actor.sharpen()
                    CoreLoopItems.refresh(player, requireNotNull(account(player)), packed = packed(player))
                } else player.sendMessage(CoreLoopItems.text(result?.message ?: "使用できませんでした。", NamedTextColor.RED))
            }
        }
    }

    private fun updateHud(player: Player) {
        val a = account(player) ?: return
        val actor = actors[player.uuid] ?: return
        player.food = 20
        player.foodSaturation = 20f
        player.level = a.journey.level
        val currentXp = CoreJourneyRules.threshold(a.journey.level)
        val nextXp = CoreJourneyRules.threshold((a.journey.level + 1).coerceAtMost(40))
        player.exp = if (nextXp == currentXp) 1f else ((a.journey.xp - currentXp).toFloat() / (nextXp - currentXp)).coerceIn(0f, 1f)
        val session = sessions[player.uuid]
        val message = if (isDeparting(player)) "遠征先を準備中…"
        else if (dungeons.run(player) != null) dungeons.run(player)!!.objective()
        else if (session == null) "開拓港  T${a.weaponTier} / T${a.armorTier}  手帳 [9]"
        else if (a.activeRun?.bossDefeated == true) "討伐達成・手帳 [9] で帰還"
        else if (session.arena != null) "${session.arena.displayName} — ${session.combat.bossName()}"
        else "道の先のボスへ  戦利品 ${session.loot.remainingCount()}"
        val icons = listOf(CoreUiIcon.DASH, CoreUiIcon.SLAM, CoreUiIcon.WHIRL)
        player.sendActionBar(CoreUiComponents.hud(CoreHudState(actor.health, actor.maxHealth.toDouble(), actor.mana.toDouble(), actor.maxMana.toDouble(),
            icons.mapIndexed { i, icon -> CoreHudSkill(icon, (i + 2).toString(), actor.cooldownRemaining(i) / 20.0, actor.cooldownTicks(i) / 20.0, listOf(15, 25, 35)[i], actor.classId.ordinal * 3 + i, actor.skillAvailable(i)) }, message), packed(player)))
    }

    override fun sessionSummary(player: Player): String = dungeons.run(player)?.objective() ?: sessions[player.uuid]?.let {
        val activities = it.adventures?.snapshots().orEmpty()
        "倒した敵 ${it.combat.defeatedMobCount}体 / " + (it.arena?.displayName ?: "発見 ${it.discoveries.size}か所・寄り道 ${activities.count { s -> s.phase == AdventurePhase.COMPLETED }}/${activities.size}")
    } ?: ""
    override fun gatheringMastery(player: Player): QuestGatheringMastery = questMaps.masterySnapshot(player.uuid)
    override fun unlockMastery(player: Player, discipline: QuestGatheringDiscipline, node: QuestGatheringMasteryNode) = questMaps.unlockGatheringMasteryNode(player, discipline.id, node.id)
    fun nextSteps(a: CoreAccount): List<String> = when {
        a.currencies.values.any { it > 0 } && a.equippedAffixes.isEmpty() -> listOf("刻印工房でオーブを使い、MODを抽選", "変成でマジック / 錬金でレア装備へ")
        a.fragments.values.any { it >= 3 } -> listOf("欠片が集まった！境界の試練に挑戦", "専用ボスから特別な加工オーブを狙おう")
        a.weaponTier < a.unlockedMapTier -> listOf("工房で装備を作り、装備庫から装備しよう", "足りない素材は採取、または市場で購入")
        a.weaponTier == 4 && a.armorTier == 4 -> listOf("T4装備完成！石板で地図を調整", "密集地域や高Tierの資源を狙って周回しよう")
        else -> listOf("地図台で地図を選び、遠征へ", "ボス討伐で次Tierの地図を解放")
    }

    private fun disconnect(player: Player) {
        if (!connections.remove(player.uuid, player)) return
        dungeons.disconnect(player)
        preparedMaps.forget(player.uuid)
        uiPack?.forget(player)
        departing.remove(player.uuid)
        sessions.remove(player.uuid)?.let { session ->
            if (session.combat.bossDefeated && accounts[player.uuid]?.activeRun?.bossDefeated != true) {
                rewards.submit(player.uuid, CoreAction.BossReward(session.runId))
            }
            session.returning = true
            session.adventures?.failAll(); session.adventures?.dispose()
            session.combat.dispose()
            session.loot.collectAll()
            session.loot.dispose(); session.caches?.dispose()
            session.arena?.let { arena -> MinecraftServer.getSchedulerManager().scheduleNextTick {
                if (player.instance === arena.instance) player.remove()
                if (arena.instance.players.isEmpty()) arena.dispose()
            } }
        }
        questMaps.disconnect(player.uuid)
        actors.remove(player.uuid)?.resetActions()
        busy.remove(player.uuid); potionReady.remove(player.uuid); lastUseAt.remove(player.uuid); menus.forget(player.uuid)
        rewards.drain(player.uuid).thenRunAsync({
            if (connections.containsKey(player.uuid)) return@thenRunAsync
            ledger.snapshot(player.uuid)?.activeRun?.let { run ->
                ledger.snapshot(player.uuid)?.let { a -> ledger.transact(player.uuid, CoreOperation(UUID.randomUUID(), a.revision, CoreAction.FinishRun(run.id))) }
            }
            ledger.forget(player.uuid); accounts.remove(player.uuid)
        }, io)
    }

    fun close() {
        dungeons.close()
        preparedMaps.close()
        mapBuilder.shutdown()
        sessions.values.forEach { session ->
            session.returning = true
            session.adventures?.failAll(); session.adventures?.dispose()
            if (session.combat.bossDefeated) rewards.submit(session.owner.uuid, CoreAction.BossReward(session.runId))
            session.combat.dispose()
            session.loot.collectAll()
            session.loot.dispose(); session.caches?.dispose()
        }
        try {
            CompletableFuture.allOf(*accounts.keys.map { rewards.drain(it) }.toTypedArray()).get(15, TimeUnit.SECONDS)
        } catch (failure: Exception) {
            System.err.println("CORE_SHUTDOWN_PENDING_REWARDS: $failure")
        } finally {
            uiPack?.close()
            io.shutdown()
            io.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun disciplineResource(discipline: QuestGatheringDiscipline): CoreResource = when (discipline) {
        QuestGatheringDiscipline.WOODCUTTING -> CoreResource.WOOD
        QuestGatheringDiscipline.MINING -> CoreResource.ORE
        QuestGatheringDiscipline.QUARRYING -> CoreResource.STONE
        QuestGatheringDiscipline.SKINNING -> CoreResource.HIDE
        QuestGatheringDiscipline.HERBALISM -> CoreResource.FIBER
    }
}
