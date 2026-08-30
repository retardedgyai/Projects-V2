package dev.projects.server.experiment.swarm

import dev.projects.server.experiment.swarm.combat.BrineclawAttack
import dev.projects.server.experiment.swarm.combat.BrineclawController
import dev.projects.server.experiment.swarm.combat.BrineclawDefeatSink
import dev.projects.server.experiment.swarm.combat.BrineclawEvent
import dev.projects.server.experiment.swarm.combat.BrineclawScheduledAction
import dev.projects.server.experiment.swarm.combat.BrineclawState
import dev.projects.server.experiment.swarm.combat.CairnbackAttack
import dev.projects.server.experiment.swarm.combat.CairnbackConfig
import dev.projects.server.experiment.swarm.combat.CairnbackEncounter
import dev.projects.server.experiment.swarm.combat.CairnbackEvent
import dev.projects.server.experiment.swarm.combat.CairnbackLifecycle
import dev.projects.server.experiment.swarm.combat.CairnbackResetReason
import dev.projects.server.experiment.swarm.combat.CairnbackRewardSink
import dev.projects.server.experiment.swarm.combat.CairnbackScheduledAction
import dev.projects.server.experiment.swarm.combat.CairnbackState
import dev.projects.server.experiment.swarm.combat.CombatBounds
import dev.projects.server.experiment.swarm.combat.CombatGeometry
import dev.projects.server.experiment.swarm.combat.CombatPoint
import dev.projects.server.experiment.swarm.combat.TidehookBraceIntent
import dev.projects.server.experiment.swarm.combat.TidehookCombat
import dev.projects.server.experiment.swarm.combat.TidehookConfig
import dev.projects.server.experiment.swarm.combat.TidehookHand
import dev.projects.server.experiment.swarm.combat.TidehookMod
import dev.projects.server.experiment.swarm.combat.TidehookThrustIntent
import dev.projects.server.experiment.swarm.loop.FileSwarmSnapshotStore
import dev.projects.server.experiment.swarm.loop.FittingState
import dev.projects.server.experiment.swarm.loop.LoopOperationResult
import dev.projects.server.experiment.swarm.loop.LoopStatus
import dev.projects.server.experiment.swarm.loop.PlayerLoadResult
import dev.projects.server.experiment.swarm.loop.ProcurementRoute
import dev.projects.server.experiment.swarm.loop.QuestStage
import dev.projects.server.experiment.swarm.loop.SwarmLoopService
import dev.projects.server.experiment.swarm.loop.SwarmPlayerSnapshot
import dev.projects.server.experiment.swarm.loop.SwarmResource
import dev.projects.server.experiment.swarm.loop.SwarmWorldInteractions
import dev.projects.server.experiment.swarm.loop.TargetInteractionAttempt
import dev.projects.server.experiment.swarm.loop.TidehookModChoice
import dev.projects.server.experiment.swarm.world.BlockBounds
import dev.projects.server.experiment.swarm.world.TidebreakActorId
import dev.projects.server.experiment.swarm.world.TidebreakCombatSpawnSpec
import dev.projects.server.experiment.swarm.world.TidebreakTargetKind
import dev.projects.server.experiment.swarm.world.TidebreakWorldGenerator
import dev.projects.server.experiment.swarm.world.TidebreakWorldSpec
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventNode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.nio.file.Path
import java.util.UUID
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Runtime-only composition for the frozen Tidebreak vertical slice. */
class SwarmSliceWiring(dataRoot: Path) {
    val dataDirectory: Path = dataRoot.resolve(FileSwarmSnapshotStore.EXPERIMENT_RELATIVE_PATH).normalize()

    private val instance: InstanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer()
    private val loop = SwarmLoopService(FileSwarmSnapshotStore(dataDirectory))
    private val interactions = SwarmWorldInteractions(loop)
    private val tidehook = TidehookCombat(TidehookConfig(thrustRecoveryTicks = SwarmSliceBalance.THRUST_RECOVERY_TICKS))
    private val sidebars = mutableMapOf<UUID, Sidebar>()
    private val routeMenus = mutableMapOf<UUID, Inventory>()
    private val modMenus = mutableMapOf<UUID, Inventory>()
    private val npcByEntity = mutableMapOf<UUID, TidebreakActorId>()
    private val brineByEntity = mutableMapOf<UUID, BrineclawRuntime>()
    private val targetByBlock = TidebreakWorldSpec.targets.associateBy { it.position.blockKey() }
    private val interactionDebounce = mutableMapOf<Pair<UUID, UUID>, Long>()
    private val useActions = SwarmUseActionDeduplicator()
    private val inputSequence = mutableMapOf<UUID, Long>()
    private val cairnbackScheduled = mutableListOf<CairnbackScheduledAction>()
    private var cairnbackAttack: RuntimeAttack<CairnbackAttack>? = null
    private var bossEntity: LivingEntity? = null
    private var bossBar: BossBar? = null
    private var serverTick = 0L
    private var nextBossAttackTick = 0L
    private var victoryResetTick: Long? = null
    private var victoryPersistenceFailed = false

    private val cairnback = CairnbackEncounter(
        config = CairnbackConfig(baseMaxHealth = SwarmSliceBalance.CAIRNBACK_BASE_HEALTH),
        rewardSink = CairnbackRewardSink { rewardBossVictory(it) },
    )

    fun install() {
        configureWorld()
        preloadAndVerifyWorld()
        spawnActors()
        TidebreakWorldSpec.brineclawSpawns.forEach(::spawnBrineclaw)
        spawnBoss()
        registerCommands()
        registerEvents(MinecraftServer.getGlobalEventHandler())
        println("[swarm-vslice] Tidebreak world verified; player data: $dataDirectory")
    }

    private fun configureWorld() {
        instance.setChunkSupplier(::LightingChunk)
        instance.setGenerator(TidebreakWorldGenerator())
        instance.setTime(6000)
        instance.defaultClock()?.pause()
        instance.setWeather(Weather.CLEAR)
    }

    private fun preloadAndVerifyWorld() {
        for (chunkX in -3..2) for (chunkZ in -3..2) instance.loadChunk(chunkX, chunkZ).join()
        val spawn = TidebreakWorldSpec.spawn
        check(instance.getBlock(floor(spawn.x()).toInt(), TidebreakWorldSpec.GROUND_Y, floor(spawn.z()).toInt()).isSolid) {
            "Tidebreak spawn floor did not generate"
        }
    }

    private fun spawnActors() {
        TidebreakWorldSpec.actors.forEach { spec ->
            val entity = Entity(EntityType.VILLAGER).apply {
                customName = Component.text(spec.displayName, NamedTextColor.AQUA)
                isCustomNameVisible = true
                setNoGravity(true)
                setHasPhysics(false)
                setInstance(this@SwarmSliceWiring.instance, spec.position).join()
            }
            npcByEntity[entity.uuid] = spec.id
        }
    }

    private fun spawnBrineclaw(spec: TidebreakCombatSpawnSpec) {
        lateinit var runtime: BrineclawRuntime
        val controller = BrineclawController(
            defeatSink = BrineclawDefeatSink { event ->
                event.participants.forEach { playerId ->
                    val beforeTraining = loop.snapshot(playerId)?.brineclawTrainingComplete == true
                    val result = loop.grantBrineclawCord(playerId)
                    player(playerId)?.let { participant ->
                        showResult(participant, result, "Brineclaw Cord secured")
                        if (!beforeTraining && result.snapshot?.brineclawTrainingComplete == true) {
                            participant.sendMessage(Component.text("Brineclaw lesson complete: Sweep and Charge recognized.", NamedTextColor.GREEN))
                            participant.sendMessage(Component.text("Install the Coupler, then report to Lookout Tern.", NamedTextColor.GRAY))
                        }
                    }
                }
            },
        )
        runtime = BrineclawRuntime(spec, controller, createBrineclawEntity(spec))
        brineByEntity[runtime.entity.uuid] = runtime
    }

    private fun createBrineclawEntity(spec: TidebreakCombatSpawnSpec): LivingEntity = LivingEntity(EntityType.DROWNED).apply {
        customName = Component.text("Brineclaw [${spec.spawnId.substringAfterLast('/')}]")
        isCustomNameVisible = true
        isInvulnerable = true
        setNoGravity(true)
        setHasPhysics(false)
        setInstance(this@SwarmSliceWiring.instance, spec.position).join()
    }

    private fun spawnBoss() {
        val entity = LivingEntity(EntityType.RAVAGER).apply {
            customName = Component.text("Cairnback")
            isCustomNameVisible = true
            isInvulnerable = true
            setNoGravity(true)
            setHasPhysics(false)
            setInstance(this@SwarmSliceWiring.instance, TidebreakWorldSpec.bossSpawn).join()
        }
        bossEntity = entity
        bossBar = BossBar.bossBar(
            Component.text("Cairnback — dormant"),
            1f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS,
        )
    }

    private fun registerEvents(events: GlobalEventHandler) {
        events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instance
            event.player.respawnPoint = TidebreakWorldSpec.respawn
        }
        events.addListener(PlayerSpawnEvent::class.java) { event -> onSpawn(event.player, event.isFirstSpawn) }
        events.addListener(PlayerDisconnectEvent::class.java) { event -> onDisconnect(event.player) }
        events.addListener(PlayerMoveEvent::class.java) { event -> enforceBounds(event) }
        events.addListener(PlayerEntityInteractEvent::class.java) { event -> onEntityInteract(event) }
        events.addListener(PlayerBlockInteractEvent::class.java) { event -> onBlockInteract(event) }
        events.addListener(PlayerUseItemEvent::class.java) { event -> onUseItem(event) }
        events.addListener(EntityAttackEvent::class.java) { event -> onAttack(event) }
        events.addListener(EntityDamageEvent::class.java) { event ->
            if (event.entity.uuid in npcByEntity || event.entity.uuid in brineByEntity || event.entity === bossEntity) {
                event.isCancelled = true
            }
        }
        events.addListener(ItemDropEvent::class.java) { event ->
            if (isTidehook(event.itemStack)) event.isCancelled = true
        }
        events.addListener(InventoryPreClickEvent::class.java) { event -> onMenuClick(event) }
        events.addListener(InstanceTickEvent::class.java) { event -> if (event.instance === instance) tick() }
    }

    private fun onSpawn(player: Player, firstSpawn: Boolean) {
        if (!firstSpawn && loop.snapshot(player.uuid) != null) {
            preparePlayer(player)
            return
        }
        when (val loaded = loop.loadPlayer(player.uuid)) {
            is PlayerLoadResult.Ready -> {
                preparePlayer(player)
                player.sendMessage(Component.text("Tidebreak Anchorage", NamedTextColor.GOLD))
                player.sendMessage(Component.text("Speak to Warden Iona, then reopen the breakwater.", NamedTextColor.GRAY))
            }
            is PlayerLoadResult.Invalid -> player.kick(Component.text("Profile rejected: ${loaded.reason}"))
            PlayerLoadResult.PersistenceFailed -> player.kick(Component.text("Profile storage unavailable; no progress was replaced."))
        }
    }

    private fun preparePlayer(player: Player) {
        player.gameMode = GameMode.ADVENTURE
        player.respawnPoint = TidebreakWorldSpec.respawn
        player.setHealth(20f)
        player.inventory.clear()
        player.inventory.setItemStack(TIDEHOOK_SLOT, tidehookItem(loop.snapshot(player.uuid)))
        refreshSidebar(player)
    }

    private fun onDisconnect(player: Player) {
        if (player.uuid in cairnback.roster()) cairnback.markDisconnected(player.uuid)
        tidehook.clearPlayer(player.uuid)
        useActions.clear(player.uuid)
        sidebars.remove(player.uuid)?.removeViewer(player)
        routeMenus.remove(player.uuid)
        modMenus.remove(player.uuid)
        loop.unloadPlayer(player.uuid)
    }

    private fun enforceBounds(event: PlayerMoveEvent) {
        val pos = event.newPosition
        if (pos.y() < 35.0 || pos.x() < -47.5 || pos.x() > 47.5 || pos.z() < -47.5 || pos.z() > 47.5) {
            event.newPosition = if (event.player.uuid in cairnback.roster()) TidebreakWorldSpec.stagingSpawn else TidebreakWorldSpec.respawn
            event.player.sendActionBar(Component.text("The Tidebreak boundary turns you back.", NamedTextColor.RED))
        }
    }

    private fun onEntityInteract(event: PlayerEntityInteractEvent) {
        if (event.hand != PlayerHand.MAIN) return
        val actor = npcByEntity[event.target.uuid] ?: return
        if (distance(event.player.position, event.target.position) > NPC_DISTANCE) return
        val debounceKey = event.player.uuid to event.target.uuid
        if ((interactionDebounce[debounceKey] ?: Long.MIN_VALUE) + 4 > serverTick) return
        interactionDebounce[debounceKey] = serverTick
        when (actor) {
            TidebreakActorId.WARDEN -> interactWarden(event.player)
            TidebreakActorId.BROKER_SMITH -> interactBroker(event.player)
            TidebreakActorId.LOOKOUT -> interactLookout(event.player)
        }
    }

    private fun interactWarden(player: Player) {
        val snapshot = loop.snapshot(player.uuid) ?: return
        when {
            snapshot.selectedRoute == null -> openRouteMenu(player)
            snapshot.questStage == QuestStage.MOD_INSTALLED -> showResult(player, loop.reportCompletion(player.uuid), "Contract complete")
            else -> {
                player.sendMessage(Component.text("Iona: ${objective(snapshot)}", NamedTextColor.YELLOW))
                player.sendMessage(Component.text("Your route is ${snapshot.selectedRoute}; every resource may still be traded.", NamedTextColor.GRAY))
            }
        }
    }

    private fun interactBroker(player: Player) {
        val snapshot = loop.snapshot(player.uuid) ?: return
        if (snapshot.selectedRoute == ProcurementRoute.SUPPLIER && !snapshot.supplierCommissionClaimed) {
            val commission = loop.claimSupplierCommission(player.uuid)
            if (commission.accepted) showResult(player, commission, "Supplier commission paid")
        }
        if (loop.snapshot(player.uuid)?.questStage == QuestStage.BOSS_CLEARED) {
            openModMenu(player)
            return
        }
        player.sendMessage(Component.text("Brann: fixed exchange — buy 6 Scrip, sell 2 Scrip.", NamedTextColor.GOLD))
        player.sendMessage(
            Component.text("[Buy Ore]", NamedTextColor.GREEN).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/swarm-buy-ore"))
                .append(Component.space())
                .append(Component.text("[Buy Cord]", NamedTextColor.GREEN).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/swarm-buy-cord")))
                .append(Component.space())
                .append(Component.text("[Sell Ore]", NamedTextColor.AQUA).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/swarm-sell-ore")))
                .append(Component.space())
                .append(Component.text("[Sell Cord]", NamedTextColor.AQUA).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/swarm-sell-cord"))),
        )
    }

    private fun interactLookout(player: Player) {
        player.sendMessage(Component.text("Tern: Sweep leaves the arc. Charge: sidestep or Brace.", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("Line Charge into the copper post; Cairnback must hit a lit crash pillar.", NamedTextColor.GRAY))
        if (!loop.preparedForEncounter(player.uuid)) {
            val snapshot = loop.snapshot(player.uuid)
            val reason = if (snapshot?.fittingState == FittingState.NONE) {
                "Install the Signal Coupler first."
            } else {
                "Brineclaw training ${snapshot?.brineclawTrainingDefeats ?: 0}/${SwarmPlayerSnapshot.BRINECLAW_TRAINING_OBJECTIVE}."
            }
            player.sendActionBar(Component.text(reason, NamedTextColor.RED))
            return
        }
        if (cairnback.lifecycle == CairnbackLifecycle.ACTIVE) {
            player.sendActionBar(Component.text("Encounter already active; late join is closed.", NamedTextColor.RED))
            return
        }
        if (cairnback.lifecycle == CairnbackLifecycle.VICTORY) resetBoss(CairnbackResetReason.INTEGRATION_REQUEST)
        val roster = instance.players
            .filter { loop.preparedForEncounter(it.uuid) && TidebreakWorldSpec.stagingBounds.contains(it.position) }
            .take(4)
        val start = cairnback.start(roster.map(Player::getUuid), serverTick)
        if (!start.accepted) {
            player.sendActionBar(Component.text(start.reason ?: "Cannot start encounter", NamedTextColor.RED))
            return
        }
        roster.forEach {
            it.teleport(Pos(29.5, 41.0, 0.5, -90f, 0f)).join()
            it.setHealth(20f)
            sidebars[it.uuid]?.removeViewer(it)
            bossBar?.let(it::showBossBar)
        }
        bossEntity?.teleport(TidebreakWorldSpec.bossSpawn)?.join()
        nextBossAttackTick = serverTick + 30
        broadcastRoster(Component.text("Cairnback wakes. Build three confirmed hits to qualify.", NamedTextColor.RED))
        updateBossBar()
    }

    private fun onBlockInteract(event: PlayerBlockInteractEvent) {
        if (event.hand != PlayerHand.MAIN) return
        val target = targetByBlock[event.blockPosition.blockKey()]
        if (target != null) {
            event.isCancelled = true
            val attempt = TargetInteractionAttempt(
                event.player.uuid,
                target.targetId,
                event.player.position.x(),
                event.player.position.y(),
                event.player.position.z(),
                hasLineOfSight(event.player, point(target.position)),
                serverTick,
            )
            val result = when (target.kind) {
                TidebreakTargetKind.ORE_NODE -> interactions.harvestOre(attempt)
                TidebreakTargetKind.SUPPLIER_RECORD -> interactions.inspectSupplierRecord(attempt)
                TidebreakTargetKind.COUPLER_RACK -> interactions.craftAndInstallCoupler(attempt)
                else -> null
            }
            result?.let { showResult(event.player, it, interactionSuccess(target.kind)) }
            return
        }
        if (isTidehook(event.player.itemInMainHand)) {
            event.isCancelled = true
            event.isBlockingItemUse = false
            requestBrace(event.player, PlayerHand.MAIN)
        }
    }

    private fun onUseItem(event: PlayerUseItemEvent) {
        if (!isTidehook(event.itemStack)) return
        event.isCancelled = true
        if (event.hand == PlayerHand.MAIN) requestBrace(event.player, event.hand)
    }

    private fun requestBrace(player: Player, hand: PlayerHand) {
        val actionId = useActions.actionId(player.uuid, serverTick) { nextActionId(player.uuid) }
        val result = tidehook.requestBrace(
            TidehookBraceIntent(
                player.uuid,
                actionId,
                serverTick,
                if (hand == PlayerHand.MAIN) TidehookHand.MAIN else TidehookHand.OFF,
                isTidehook(player.itemInMainHand),
                player.instance === instance,
                player.health > 0f,
                tidehookMod(player.uuid),
            ),
        )
        if (result.accepted) player.sendActionBar(Component.text("Brace: windup → ACTIVE", NamedTextColor.AQUA))
    }

    private fun onAttack(event: EntityAttackEvent) {
        val player = event.entity as? Player ?: return
        val target = event.target
        val brine = brineByEntity[target.uuid]
        val isBoss = target === bossEntity && cairnback.lifecycle == CairnbackLifecycle.ACTIVE
        if (brine == null && !isBoss) return
        val correctZone = if (isBoss) {
            player.uuid in cairnback.roster() && TidebreakWorldSpec.arenaBounds.contains(player.position)
        } else {
            brine != null && distance(player.position, brine.entity.position) <= 6.0
        }
        val eye = CombatPoint(player.position.x(), player.position.y() + player.eyeHeight, player.position.z())
        val targetBounds = bounds(target)
        val result = tidehook.requestThrust(
            TidehookThrustIntent(
                player.uuid,
                target.uuid,
                nextActionId(player.uuid),
                serverTick,
                eye,
                point(player.position.direction()),
                targetBounds,
                blockersBetween(eye, targetBounds.center),
                isTidehook(player.itemInMainHand),
                correctZone,
                registeredTarget = true,
                alive = player.health > 0f,
                targetExposed = if (isBoss) cairnback.exposed else brine?.controller?.state == BrineclawState.EXPOSED,
                mod = tidehookMod(player.uuid),
            ),
        )
        if (!result.accepted) {
            result.reason?.let { player.sendActionBar(Component.text(it.name.lowercase().replace('_', ' '), NamedTextColor.RED)) }
            return
        }
        val exposed = if (isBoss) cairnback.exposed else brine?.controller?.state == BrineclawState.EXPOSED
        val damage = if (isBoss) {
            SwarmSliceBalance.cairnbackThrustDamage(exposed == true, result.damageMultiplier)
        } else {
            SwarmSliceBalance.brineclawThrustDamage(exposed == true, result.damageMultiplier)
        }
        if (isBoss) {
            val hit = cairnback.applyTidehookHit(player.uuid, result.actionId!!, damage)
            if (hit.accepted) {
                val feedback = if (hit.exposed) {
                    "Thrust ${hit.damageApplied} — EXPOSED"
                } else {
                    "Shell deflects (${hit.damageApplied}) — line Charge into a lit pillar"
                }
                player.sendActionBar(Component.text(feedback, if (hit.exposed) NamedTextColor.GREEN else NamedTextColor.YELLOW))
                showImpact(target.position.add(0.0, 1.0, 0.0), if (hit.exposed) Particle.ELECTRIC_SPARK else Particle.CRIT, setOf(player.uuid))
                handleBossEvents(hit.events)
                updateBossBar()
            }
        } else if (brine != null) {
            val hit = brine.controller.applyTidehookHit(player.uuid, result.actionId!!, damage)
            if (hit.accepted) {
                player.sendActionBar(Component.text("Thrust ${hit.damageApplied}", NamedTextColor.GREEN))
                showImpact(target.position.add(0.0, 1.0, 0.0), if (exposed == true) Particle.ELECTRIC_SPARK else Particle.CRIT, setOf(player.uuid))
            }
            if (hit.defeated) defeatBrineclaw(brine)
        }
    }

    private fun tick() {
        serverTick += 1
        tickBrineclaws()
        tickBoss()
        if (serverTick % 10L == 0L) {
            instance.players.forEach { player ->
                if (player.uuid !in cairnback.roster()) refreshSidebar(player)
            }
        }
    }

    private fun tickBrineclaws() {
        brineByEntity.values.toSet().forEach { runtime ->
            if (runtime.respawnTick != null) {
                if (serverTick >= runtime.respawnTick!!) respawnBrineclaw(runtime)
                return@forEach
            }
            val due = runtime.scheduled.filter { it.dueTick <= serverTick }
            runtime.scheduled.removeAll(due.toSet())
            due.forEach { action -> handleBrineTransition(runtime, runtime.controller.executeScheduled(action, serverTick)) }
            resolveBrineActive(runtime)
            if (runtime.controller.state == BrineclawState.READY && serverTick >= runtime.nextAttackTick) {
                val target = nearestPlayer(runtime.entity.position, 10.0) ?: return@forEach
                val attack = if (runtime.nextIsCharge) BrineclawAttack.CHARGE else BrineclawAttack.SWEEP
                runtime.nextIsCharge = !runtime.nextIsCharge
                runtime.entity.lookAt(target)
                val plan = if (attack == BrineclawAttack.CHARGE) runtime.controller.beginCharge(serverTick) else runtime.controller.beginSweep(serverTick)
                if (plan != null) {
                    runtime.attack = RuntimeAttack(plan.telegraph.attackId, attack, point(runtime.entity.position), horizontalDirection(runtime.entity.position, target.position))
                    runtime.scheduled += plan.scheduled
                    tell(runtime.entity.position, plan.telegraph.textCue, attack == BrineclawAttack.CHARGE)
                    renderTelegraph(runtime.attack!!, boss = false)
                }
            }
        }
    }

    private fun handleBrineTransition(runtime: BrineclawRuntime, transition: dev.projects.server.experiment.swarm.combat.BrineclawTransition) {
        runtime.scheduled += transition.scheduled
        transition.events.forEach { event ->
            when (event) {
                is BrineclawEvent.Active -> runtime.attack?.activeFromTick = serverTick
                is BrineclawEvent.ExposureStarted -> {
                    tell(runtime.entity.position, "Brineclaw exposed — thrust now", true)
                    showImpact(runtime.entity.position.add(0.0, 1.0, 0.0), Particle.EXPLOSION)
                }
                is BrineclawEvent.Ready -> {
                    runtime.entity.teleport(runtime.spec.position)
                    runtime.attack = null
                    runtime.nextAttackTick = serverTick + 16
                }
                else -> Unit
            }
        }
    }

    private fun resolveBrineActive(runtime: BrineclawRuntime) {
        val attack = runtime.attack ?: return
        val controller = runtime.controller
        if (
            (controller.state == BrineclawState.SWEEP_TELEGRAPH || controller.state == BrineclawState.CHARGE_TELEGRAPH) &&
            serverTick % TELEGRAPH_REFRESH_TICKS == 0L
        ) {
            renderTelegraph(attack, boss = false)
        }
        if (controller.state != BrineclawState.SWEEP_ACTIVE && controller.state != BrineclawState.CHARGE_ACTIVE) return
        val chargeLength = if (controller.state == BrineclawState.CHARGE_ACTIVE) {
            attack.chargeLength(serverTick, BRINE_CHARGE_ACTIVE_TICKS, BRINE_CHARGE_LENGTH)
        } else {
            0.0
        }
        if (controller.state == BrineclawState.CHARGE_ACTIVE) moveChargeEntity(runtime.entity, attack, chargeLength)
        if (
            controller.state == BrineclawState.CHARGE_ACTIVE &&
            CombatGeometry.intersectsChargeCorridor(
                attack.origin,
                attack.forward,
                chargeLength.coerceAtLeast(0.05),
                BRINE_CHARGE_HALF_WIDTH,
                BRINE_CHARGE_HEIGHT,
                targetBounds(TidebreakWorldSpec.practicePost.position, 1.6, 4.0),
            )
        ) {
            handleBrineTransition(runtime, controller.collideChargeWithPracticePost(serverTick))
            return
        }
        instance.players.forEach { player ->
            if (!attack.hitPlayers.add(player.uuid)) return@forEach
            val hit = if (controller.state == BrineclawState.SWEEP_ACTIVE) {
                controller.intersectsSweep(attack.origin, attack.forward, bounds(player))
            } else {
                CombatGeometry.intersectsChargeCorridor(
                    attack.origin,
                    attack.forward,
                    chargeLength.coerceAtLeast(0.05),
                    BRINE_CHARGE_HALF_WIDTH,
                    BRINE_CHARGE_HEIGHT,
                    bounds(player),
                )
            }
            if (!hit) {
                attack.hitPlayers.remove(player.uuid)
                return@forEach
            }
            damagePlayer(player, if (controller.state == BrineclawState.CHARGE_ACTIVE) 7.0 else 4.0, controller.state == BrineclawState.CHARGE_ACTIVE, attack.forward)
        }
    }

    private fun defeatBrineclaw(runtime: BrineclawRuntime) {
        brineByEntity.remove(runtime.entity.uuid)
        runtime.entity.remove()
        runtime.scheduled.clear()
        runtime.attack = null
        runtime.respawnTick = serverTick + BRINE_RESPAWN_TICKS
    }

    private fun respawnBrineclaw(runtime: BrineclawRuntime) {
        runtime.controller.reset()
        runtime.entity = createBrineclawEntity(runtime.spec)
        runtime.respawnTick = null
        runtime.nextAttackTick = serverTick + 20
        brineByEntity[runtime.entity.uuid] = runtime
    }

    private fun tickBoss() {
        if (cairnback.lifecycle == CairnbackLifecycle.ACTIVE) {
            val due = cairnbackScheduled.filter { it.dueTick <= serverTick }
            cairnbackScheduled.removeAll(due.toSet())
            due.forEach { action -> handleBossTransition(cairnback.executeScheduled(action, serverTick)) }
            resolveBossActive()
            handleBossTransition(cairnback.tick(serverTick))
            cairnback.roster().forEach { id ->
                val p = player(id)
                if (p != null && !TidebreakWorldSpec.arenaBounds.contains(p.position)) cairnback.markExited(id)
            }
            if (cairnback.state == CairnbackState.READY && serverTick >= nextBossAttackTick) {
                val target = cairnback.roster().mapNotNull(::player).firstOrNull { cairnback.member(it.uuid)?.alive == true }
                if (target != null) {
                    val entity = bossEntity ?: return
                    entity.teleport(TidebreakWorldSpec.bossSpawn).join()
                    entity.lookAt(target)
                    cairnback.beginNextAttack(serverTick, target.uuid)?.let { plan ->
                        cairnbackAttack = RuntimeAttack(plan.telegraph.attackId, plan.telegraph.attack, point(entity.position), horizontalDirection(entity.position, target.position))
                        cairnbackScheduled += plan.scheduled
                        tell(entity.position, plan.telegraph.textCue, plan.telegraph.attack == CairnbackAttack.CHARGE, cairnback.roster())
                        renderTelegraph(cairnbackAttack!!, boss = true, recipients = cairnback.roster())
                    }
                }
            }
        }
        victoryResetTick?.let { if (serverTick >= it) resetBoss(CairnbackResetReason.INTEGRATION_REQUEST) }
    }

    private fun handleBossTransition(transition: dev.projects.server.experiment.swarm.combat.CairnbackTransition) {
        cairnbackScheduled += transition.scheduled
        handleBossEvents(transition.events)
    }

    private fun handleBossEvents(events: List<CairnbackEvent>) {
        events.forEach { event ->
            when (event) {
                is CairnbackEvent.Active -> cairnbackAttack?.activeFromTick = serverTick
                is CairnbackEvent.ExposureStarted -> {
                    broadcastRoster(Component.text("PILLAR CRASH — Cairnback exposed!", NamedTextColor.GREEN))
                    bossEntity?.position?.add(0.0, 1.0, 0.0)?.let { showImpact(it, Particle.EXPLOSION, cairnback.roster()) }
                }
                is CairnbackEvent.PhaseChanged -> broadcastRoster(Component.text("Phase 2 — the same tells, faster recombination.", NamedTextColor.GOLD))
                is CairnbackEvent.Victory -> finishBossVictory(event)
                is CairnbackEvent.Reset -> finishBossReset(event.reason)
                else -> Unit
            }
        }
    }

    private fun resolveBossActive() {
        val attack = cairnbackAttack ?: return
        if (
            (cairnback.state == CairnbackState.SWEEP_TELEGRAPH || cairnback.state == CairnbackState.CHARGE_TELEGRAPH) &&
            serverTick % TELEGRAPH_REFRESH_TICKS == 0L
        ) {
            renderTelegraph(attack, boss = true, recipients = cairnback.roster())
        }
        if (cairnback.state != CairnbackState.SWEEP_ACTIVE && cairnback.state != CairnbackState.CHARGE_ACTIVE) return
        val chargeLength = if (cairnback.state == CairnbackState.CHARGE_ACTIVE) {
            attack.chargeLength(serverTick, BOSS_CHARGE_ACTIVE_TICKS, BOSS_CHARGE_LENGTH)
        } else {
            0.0
        }
        if (cairnback.state == CairnbackState.CHARGE_ACTIVE) bossEntity?.let { moveChargeEntity(it, attack, chargeLength) }
        if (cairnback.state == CairnbackState.CHARGE_ACTIVE && TidebreakWorldSpec.crashPillars.any {
                CombatGeometry.intersectsChargeCorridor(
                    attack.origin,
                    attack.forward,
                    chargeLength.coerceAtLeast(0.05),
                    BOSS_CHARGE_HALF_WIDTH,
                    BOSS_CHARGE_HEIGHT,
                    targetBounds(it.position, 1.6, 6.5),
                )
            }
        ) {
            handleBossTransition(cairnback.collideChargeWithPillar(powered = true, tick = serverTick))
            return
        }
        cairnback.roster().mapNotNull(::player).forEach { player ->
            if (!attack.hitPlayers.add(player.uuid)) return@forEach
            val hit = if (cairnback.state == CairnbackState.SWEEP_ACTIVE) {
                cairnback.intersectsSweep(attack.origin, attack.forward, bounds(player))
            } else {
                CombatGeometry.intersectsChargeCorridor(
                    attack.origin,
                    attack.forward,
                    chargeLength.coerceAtLeast(0.05),
                    BOSS_CHARGE_HALF_WIDTH,
                    BOSS_CHARGE_HEIGHT,
                    bounds(player),
                )
            }
            if (!hit) {
                attack.hitPlayers.remove(player.uuid)
                return@forEach
            }
            damagePlayer(player, if (cairnback.state == CairnbackState.CHARGE_ACTIVE) 12.0 else 6.0, cairnback.state == CairnbackState.CHARGE_ACTIVE, attack.forward)
        }
    }

    private fun rewardBossVictory(event: CairnbackEvent.Victory) {
        victoryPersistenceFailed = false
        event.eligiblePlayers.forEach { playerId ->
            val result = loop.claimBossVictory(playerId)
            if (result.status == LoopStatus.PERSISTENCE_FAILED) victoryPersistenceFailed = true
            player(playerId)?.let { showResult(it, result, "Pressure Pearl secured") }
        }
    }

    private fun finishBossVictory(event: CairnbackEvent.Victory) {
        cairnback.roster().mapNotNull(::player).forEach { player ->
            bossBar?.let(player::hideBossBar)
            refreshSidebar(player)
            if (player.uuid !in event.eligiblePlayers) {
                player.sendMessage(Component.text("No Pearl: three confirmed Tidehook hits were required.", NamedTextColor.RED))
            }
        }
        broadcastRoster(Component.text("Cairnback defeated. Return to Broker-Smith Brann.", NamedTextColor.GOLD))
        victoryResetTick = serverTick + if (victoryPersistenceFailed) 1 else 100
    }

    private fun resetBoss(reason: CairnbackResetReason) {
        if (cairnback.lifecycle != CairnbackLifecycle.READY) cairnback.reset(reason)
        finishBossReset(reason)
    }

    private fun finishBossReset(reason: CairnbackResetReason) {
        cairnbackScheduled.clear()
        cairnbackAttack = null
        victoryResetTick = null
        bossEntity?.teleport(TidebreakWorldSpec.bossSpawn)
        instance.players.forEach { player ->
            bossBar?.let(player::hideBossBar)
            if (reason != CairnbackResetReason.INTEGRATION_REQUEST || victoryPersistenceFailed) {
                player.teleport(TidebreakWorldSpec.stagingSpawn)
            }
            player.setHealth(20f)
            refreshSidebar(player)
        }
        if (victoryPersistenceFailed) {
            victoryPersistenceFailed = false
            instance.players.forEach { it.sendMessage(Component.text("Reward save failed; encounter reset for a safe retry.", NamedTextColor.RED)) }
        }
        updateBossBar()
    }

    private fun damagePlayer(player: Player, baseDamage: Double, charge: Boolean, forward: CombatPoint) {
        val mitigation = if (charge) tidehook.resolveIncomingCharge(player.uuid, serverTick) else null
        val damage = baseDamage * (mitigation?.damageMultiplier ?: 1.0)
        val remaining = player.health - damage.toFloat()
        if (remaining <= 0f) {
            player.setHealth(20f)
            if (player.uuid in cairnback.roster()) {
                cairnback.setAlive(player.uuid, false)
                player.teleport(TidebreakWorldSpec.stagingSpawn)
            } else {
                player.teleport(TidebreakWorldSpec.respawn)
            }
            player.sendMessage(Component.text("You were routed; Brace the Charge and try again.", NamedTextColor.RED))
        } else {
            player.setHealth(remaining)
            if (charge) {
                val force = 8.0 * (mitigation?.knockbackMultiplier ?: 1.0)
                player.velocity = Vec(forward.x * force, 2.0, forward.z * force)
            }
            player.sendActionBar(Component.text(if (mitigation?.braced == true) "BRACED — ${damage.roundToInt()}" else "Hit — ${damage.roundToInt()}", NamedTextColor.RED))
        }
    }

    private fun registerCommands() {
        exchangeCommand("swarm-buy-ore") { id -> loop.buy(id, SwarmResource.ORE) }
        exchangeCommand("swarm-buy-cord") { id -> loop.buy(id, SwarmResource.CORD) }
        exchangeCommand("swarm-sell-ore") { id -> loop.sell(id, SwarmResource.ORE) }
        exchangeCommand("swarm-sell-cord") { id -> loop.sell(id, SwarmResource.CORD) }
    }

    private fun exchangeCommand(name: String, operation: (UUID) -> LoopOperationResult) {
        MinecraftServer.getCommandManager().register(Command(name).apply {
            setDefaultExecutor { sender, _ ->
                val player = sender as? Player ?: return@setDefaultExecutor
                val broker = TidebreakWorldSpec.actors.first { it.id == TidebreakActorId.BROKER_SMITH }
                if (distance(player.position, broker.position) > NPC_DISTANCE) {
                    player.sendActionBar(Component.text("Trade only beside Broker-Smith Brann.", NamedTextColor.RED))
                    return@setDefaultExecutor
                }
                showResult(player, operation(player.uuid), "Exchange complete")
            }
        })
    }

    private fun openRouteMenu(player: Player) {
        val menu = Inventory(InventoryType.CHEST_3_ROW, Component.text("Choose one procurement route"))
        menu.setItemStack(11, menuItem(Material.IRON_SWORD, "Hunter — confirm 4 Cord"))
        menu.setItemStack(13, menuItem(Material.IRON_PICKAXE, "Gatherer — visit 4 distinct Ore sites"))
        menu.setItemStack(15, menuItem(Material.WRITABLE_BOOK, "Supplier — inspect 2 records"))
        routeMenus[player.uuid] = menu
        player.openInventory(menu)
    }

    private fun openModMenu(player: Player) {
        val menu = Inventory(InventoryType.CHEST_3_ROW, Component.text("Fit the Pressure Pearl"))
        menu.setItemStack(12, menuItem(Material.FLINT, "Barbed Point — +20% exposed damage"))
        menu.setItemStack(14, menuItem(Material.IRON_NUGGET, "Guard Ring — longer, stronger Brace"))
        modMenus[player.uuid] = menu
        player.openInventory(menu)
    }

    private fun onMenuClick(event: InventoryPreClickEvent) {
        val player = event.player
        if (event.inventory === player.inventory && (event.slot == TIDEHOOK_SLOT || isTidehook(event.clickedItem))) {
            event.isCancelled = true
            preparePlayerInventory(player)
            return
        }
        routeMenus[player.uuid]?.let { menu ->
            if (player.openInventory === menu) {
                event.isCancelled = true
                if (event.inventory === menu) {
                    val route = when (event.slot) {
                        11 -> ProcurementRoute.HUNTER
                        13 -> ProcurementRoute.GATHERER
                        15 -> ProcurementRoute.SUPPLIER
                        else -> null
                    }
                    if (route != null) {
                        showResult(player, loop.selectRoute(player.uuid, route), "$route route selected")
                        routeMenus.remove(player.uuid)
                        player.closeInventory()
                    }
                }
                return
            }
        }
        modMenus[player.uuid]?.let { menu ->
            if (player.openInventory === menu) {
                event.isCancelled = true
                if (event.inventory === menu) {
                    val choice = when (event.slot) {
                        12 -> TidehookModChoice.BARBED_POINT
                        14 -> TidehookModChoice.GUARD_RING
                        else -> null
                    }
                    if (choice != null) {
                        showResult(player, loop.installMod(player.uuid, choice), "$choice installed")
                        modMenus.remove(player.uuid)
                        player.closeInventory()
                        preparePlayerInventory(player)
                    }
                }
            }
        }
    }

    private fun showResult(player: Player, result: LoopOperationResult, success: String) {
        val text = if (result.accepted) success else statusText(result.status)
        player.sendActionBar(Component.text(text, if (result.accepted) NamedTextColor.GREEN else NamedTextColor.RED))
        if (result.accepted) {
            preparePlayerInventory(player)
            refreshSidebar(player)
        }
    }

    private fun preparePlayerInventory(player: Player) {
        player.inventory.setItemStack(TIDEHOOK_SLOT, tidehookItem(loop.snapshot(player.uuid)))
    }

    private fun refreshSidebar(player: Player) {
        val snapshot = loop.snapshot(player.uuid) ?: return
        if (player.uuid in cairnback.roster() && cairnback.lifecycle == CairnbackLifecycle.ACTIVE) return
        val sidebar = sidebars.getOrPut(player.uuid) {
            Sidebar(Component.text("Tidebreak Contract", NamedTextColor.GOLD)).apply {
                createLine(Sidebar.ScoreboardLine("objective", Component.empty(), 5))
                createLine(Sidebar.ScoreboardLine("route", Component.empty(), 4))
                createLine(Sidebar.ScoreboardLine("scrip", Component.empty(), 3))
                createLine(Sidebar.ScoreboardLine("materials", Component.empty(), 2))
                createLine(Sidebar.ScoreboardLine("fitting", Component.empty(), 1))
            }
        }
        sidebar.updateLineContent("objective", Component.text(objective(snapshot), NamedTextColor.YELLOW))
        sidebar.updateLineContent("route", Component.text("Route: ${snapshot.selectedRoute ?: "unselected"}"))
        sidebar.updateLineContent("scrip", Component.text("Scrip: ${snapshot.scrip}"))
        sidebar.updateLineContent("materials", Component.text("Ore ${snapshot.ore} | Cord ${snapshot.cord}"))
        sidebar.updateLineContent("fitting", Component.text("Tidehook: ${snapshot.fittingState}"))
        sidebar.addViewer(player)
    }

    private fun updateBossBar() {
        val bar = bossBar ?: return
        val progress = if (cairnback.maxHealth <= 0) 0f else (cairnback.health.toFloat() / cairnback.maxHealth).coerceIn(0f, 1f)
        bar.progress(progress)
        bar.name(Component.text("Cairnback ${cairnback.phase} — ${cairnback.health}/${cairnback.maxHealth}"))
        bar.color(if (cairnback.phase.name.endsWith("TWO")) BossBar.Color.RED else BossBar.Color.BLUE)
    }

    private fun tell(origin: Pos, text: String, charge: Boolean, recipients: Set<UUID>? = null) {
        val sound = Sound.sound(
            Key.key(if (charge) "minecraft:entity.ravager.roar" else "minecraft:entity.ravager.attack"),
            Sound.Source.HOSTILE,
            1f,
            if (charge) 0.7f else 1.2f,
        )
        instance.players.filter { recipients == null || it.uuid in recipients }.filter { distance(it.position, origin) <= 24.0 }.forEach {
            it.sendActionBar(Component.text(text, NamedTextColor.RED))
            it.playSound(sound, origin.x(), origin.y(), origin.z())
        }
    }

    private fun renderTelegraph(
        attack: RuntimeAttack<*>,
        boss: Boolean,
        recipients: Set<UUID>? = null,
    ) {
        val isCharge = attack.attack == BrineclawAttack.CHARGE || attack.attack == CairnbackAttack.CHARGE
        val viewers = instance.players.filter { recipients == null || it.uuid in recipients }
            .filter { distance(it.position, Pos(attack.origin.x, attack.origin.y, attack.origin.z)) <= 28.0 }
        if (viewers.isEmpty()) return

        val points = if (isCharge) {
            val length = if (boss) BOSS_CHARGE_LENGTH else BRINE_CHARGE_LENGTH
            (0..20).map { step ->
                val travel = length * step / 20.0
                CombatPoint(
                    attack.origin.x + attack.forward.x * travel,
                    attack.origin.y + 0.12,
                    attack.origin.z + attack.forward.z * travel,
                )
            }
        } else {
            val radius = if (boss) 6.0 else 4.0
            val halfAngle = Math.toRadians(if (boss) 70.0 else 65.0)
            val right = CombatPoint(-attack.forward.z, 0.0, attack.forward.x)
            (0..24).map { step ->
                val angle = -halfAngle + halfAngle * 2.0 * step / 24.0
                CombatPoint(
                    attack.origin.x + (attack.forward.x * cos(angle) + right.x * sin(angle)) * radius,
                    attack.origin.y + 0.12,
                    attack.origin.z + (attack.forward.z * cos(angle) + right.z * sin(angle)) * radius,
                )
            }
        }
        val particle = if (isCharge) Particle.ELECTRIC_SPARK else Particle.SWEEP_ATTACK
        viewers.forEach { viewer ->
            points.forEach { point ->
                viewer.sendPacket(ParticlePacket(particle, point.x, point.y, point.z, 0f, 0f, 0f, 0f, 1))
            }
        }
    }

    private fun showImpact(position: Pos, particle: Particle, recipients: Set<UUID>? = null) {
        instance.players.filter { recipients == null || it.uuid in recipients }.forEach { viewer ->
            viewer.sendPacket(ParticlePacket(particle, position.x(), position.y(), position.z(), 0.35f, 0.35f, 0.35f, 0.08f, 12))
        }
    }

    private fun broadcastRoster(component: Component) = cairnback.roster().mapNotNull(::player).forEach { it.sendMessage(component) }

    private fun nearestPlayer(origin: Pos, range: Double): Player? = instance.players
        .filter { loop.snapshot(it.uuid)?.selectedRoute != null && it.health > 0f }
        .minByOrNull { distance(it.position, origin) }
        ?.takeIf { distance(it.position, origin) <= range }

    private fun player(id: UUID): Player? = instance.players.firstOrNull { it.uuid == id && it.isOnline }

    private fun nextActionId(id: UUID): Long = (inputSequence[id] ?: 0L).plus(1).also { inputSequence[id] = it }

    private fun tidehookMod(id: UUID): TidehookMod = when (loop.snapshot(id)?.fittingState) {
        FittingState.MOD_BARBED -> TidehookMod.BARBED_POINT
        FittingState.MOD_GUARD -> TidehookMod.GUARD_RING
        else -> TidehookMod.NONE
    }

    private fun tidehookItem(snapshot: SwarmPlayerSnapshot?): ItemStack {
        val suffix = when (snapshot?.fittingState) {
            FittingState.MOD_BARBED -> " — Barbed Point"
            FittingState.MOD_GUARD -> " — Guard Ring"
            FittingState.PREPARED -> " — Signal Coupler"
            FittingState.CATALYST_READY -> " — Pressure Pearl ready"
            else -> ""
        }
        return ItemStack.builder(Material.TRIDENT)
            .customName(Component.text("Tidehook$suffix", NamedTextColor.AQUA))
            .set(TIDEHOOK_TAG, true)
            .build()
    }

    private fun isTidehook(item: ItemStack): Boolean = item.getTag(TIDEHOOK_TAG)

    private fun objective(snapshot: SwarmPlayerSnapshot): String = when {
        snapshot.selectedRoute == null -> "Speak to Warden: choose a route"
        snapshot.questStage == QuestStage.ROUTE_SELECTED && !snapshot.routeObjectiveComplete -> when (snapshot.selectedRoute) {
            ProcurementRoute.HUNTER -> "Defeat Brineclaw ${snapshot.hunterCordEarned}/4"
            ProcurementRoute.GATHERER -> "Visit distinct Ore sites ${snapshot.gathererOreEarned}/4"
            ProcurementRoute.SUPPLIER -> "Inspect records ${Integer.bitCount(snapshot.supplierRecordMask)}/2"
        }
        snapshot.questStage == QuestStage.ROUTE_SELECTED -> "Get 2 Ore + 2 Cord, use Coupler rack"
        snapshot.questStage == QuestStage.COUPLER_INSTALLED && !snapshot.brineclawTrainingComplete ->
            "Learn Brineclaw ${snapshot.brineclawTrainingDefeats}/${SwarmPlayerSnapshot.BRINECLAW_TRAINING_OBJECTIVE}"
        snapshot.questStage == QuestStage.COUPLER_INSTALLED -> "Speak to Lookout; defeat Cairnback"
        snapshot.questStage == QuestStage.BOSS_CLEARED -> "Broker-Smith: choose a Tidehook MOD"
        snapshot.questStage == QuestStage.MOD_INSTALLED -> "Report completion to Warden"
        snapshot.questStage == QuestStage.COMPLETE -> "COMPLETE — Tidebreak reopened"
        else -> "Continue the Tidebreak contract"
    }

    private fun statusText(status: LoopStatus): String = when (status) {
        LoopStatus.OBJECTIVE_INCOMPLETE -> "Selected route objective is incomplete"
        LoopStatus.INSUFFICIENT_RESOURCES -> "Need more Scrip, Ore, or Cord"
        LoopStatus.WRONG_ROUTE -> "That record belongs to the Supplier route"
        LoopStatus.TOO_FAR -> "Move closer"
        LoopStatus.NO_LINE_OF_SIGHT -> "Target is obstructed"
        LoopStatus.COOLDOWN -> "This node is recovering"
        LoopStatus.PERSISTENCE_FAILED -> "Save failed; progress was rolled back"
        else -> status.name.lowercase().replace('_', ' ')
    }

    private fun interactionSuccess(kind: TidebreakTargetKind): String = when (kind) {
        TidebreakTargetKind.ORE_NODE -> "Ore secured"
        TidebreakTargetKind.SUPPLIER_RECORD -> "Supplier record inspected"
        TidebreakTargetKind.COUPLER_RACK -> "Signal Coupler installed"
        else -> "Interaction complete"
    }

    private fun hasLineOfSight(player: Player, target: CombatPoint): Boolean =
        blockersBetween(CombatPoint(player.position.x(), player.position.y() + player.eyeHeight, player.position.z()), target).isEmpty()

    private fun blockersBetween(start: CombatPoint, end: CombatPoint): List<CombatBounds> {
        val delta = end - start
        val steps = (delta.length() * 4.0).roundToInt().coerceAtLeast(1)
        val seen = mutableSetOf<Triple<Int, Int, Int>>()
        val result = mutableListOf<CombatBounds>()
        for (index in 1 until steps) {
            val t = index.toDouble() / steps
            if (t > 0.86) break
            val point = start + delta * t
            val x = floor(point.x).toInt()
            val y = floor(point.y).toInt()
            val z = floor(point.z).toInt()
            val key = Triple(x, y, z)
            if (seen.add(key) && instance.getBlock(x, y, z).isSolid) {
                result += CombatBounds(CombatPoint(x.toDouble(), y.toDouble(), z.toDouble()), CombatPoint(x + 1.0, y + 1.0, z + 1.0))
            }
        }
        return result
    }

    private fun bounds(entity: Entity): CombatBounds {
        val box = entity.boundingBox
        val pos = entity.position
        return CombatBounds(
            CombatPoint(pos.x() + box.minX(), pos.y() + box.minY(), pos.z() + box.minZ()),
            CombatPoint(pos.x() + box.maxX(), pos.y() + box.maxY(), pos.z() + box.maxZ()),
        )
    }

    private fun targetBounds(pos: Pos, halfWidth: Double, height: Double) = CombatBounds(
        CombatPoint(pos.x() - halfWidth, pos.y(), pos.z() - halfWidth),
        CombatPoint(pos.x() + halfWidth, pos.y() + height, pos.z() + halfWidth),
    )

    private fun horizontalDirection(from: Pos, to: Pos): CombatPoint =
        CombatPoint(to.x() - from.x(), 0.0, to.z() - from.z()).normalizedOrNull() ?: CombatPoint(1.0, 0.0, 0.0)

    private fun moveChargeEntity(entity: Entity, attack: RuntimeAttack<*>, distance: Double) {
        entity.teleport(
            Pos(
                attack.origin.x + attack.forward.x * distance,
                attack.origin.y,
                attack.origin.z + attack.forward.z * distance,
                entity.position.yaw(),
                entity.position.pitch(),
            ),
        )
    }

    private fun menuItem(material: Material, name: String): ItemStack = ItemStack.builder(material)
        .customName(Component.text(name, NamedTextColor.GOLD))
        .build()

    private fun Pos.blockKey() = Triple(floor(x()).toInt(), floor(y()).toInt(), floor(z()).toInt())
    private fun net.minestom.server.coordinate.Point.blockKey() = Triple(blockX(), blockY(), blockZ())
    private fun point(pos: net.minestom.server.coordinate.Point) = CombatPoint(pos.x(), pos.y(), pos.z())
    private fun distance(a: Pos, b: Pos): Double {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        val dz = a.z() - b.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private data class RuntimeAttack<T>(
        val id: Long,
        val attack: T,
        val origin: CombatPoint,
        val forward: CombatPoint,
        val hitPlayers: MutableSet<UUID> = mutableSetOf(),
        var activeFromTick: Long? = null,
    ) {
        fun chargeLength(tick: Long, activeTicks: Long, maximumLength: Double): Double {
            val activeFrom = activeFromTick ?: return 0.0
            val elapsed = (tick - activeFrom + 1).coerceIn(0, activeTicks)
            return maximumLength * elapsed.toDouble() / activeTicks
        }
    }

    private data class BrineclawRuntime(
        val spec: TidebreakCombatSpawnSpec,
        val controller: BrineclawController,
        var entity: LivingEntity,
        val scheduled: MutableList<BrineclawScheduledAction> = mutableListOf(),
        var attack: RuntimeAttack<BrineclawAttack>? = null,
        var nextAttackTick: Long = 20,
        var nextIsCharge: Boolean = false,
        var respawnTick: Long? = null,
    )

    private companion object {
        const val TIDEHOOK_SLOT = 0
        const val NPC_DISTANCE = 6.0
        const val BRINE_RESPAWN_TICKS = 120L
        const val TELEGRAPH_REFRESH_TICKS = 4L
        const val BRINE_CHARGE_ACTIVE_TICKS = 10L
        const val BRINE_CHARGE_LENGTH = 9.0
        const val BRINE_CHARGE_HALF_WIDTH = 1.0
        const val BRINE_CHARGE_HEIGHT = 2.5
        const val BOSS_CHARGE_ACTIVE_TICKS = 12L
        const val BOSS_CHARGE_LENGTH = 16.0
        const val BOSS_CHARGE_HALF_WIDTH = 1.6
        const val BOSS_CHARGE_HEIGHT = 4.0
        val TIDEHOOK_TAG = Tag.Boolean("swarm_tidehook").defaultValue(false)
    }
}

internal object SwarmSliceBalance {
    const val THRUST_RECOVERY_TICKS = 12L
    const val CAIRNBACK_BASE_HEALTH = 4_800
    private const val BRINECLAW_CLOSED_DAMAGE = 42.0
    private const val BRINECLAW_EXPOSED_DAMAGE = 68.0
    private const val CAIRNBACK_CLOSED_DAMAGE = 5.0
    private const val CAIRNBACK_EXPOSED_DAMAGE = 75.0

    fun brineclawThrustDamage(exposed: Boolean, modMultiplier: Double): Int =
        ((if (exposed) BRINECLAW_EXPOSED_DAMAGE else BRINECLAW_CLOSED_DAMAGE) * modMultiplier).roundToInt()

    fun cairnbackThrustDamage(exposed: Boolean, modMultiplier: Double): Int =
        ((if (exposed) CAIRNBACK_EXPOSED_DAMAGE else CAIRNBACK_CLOSED_DAMAGE) * modMultiplier).roundToInt()
}

internal class SwarmUseActionDeduplicator {
    private val actionByPlayer = mutableMapOf<UUID, Pair<Long, Long>>()

    fun actionId(playerId: UUID, serverTick: Long, nextActionId: () -> Long): Long {
        val current = actionByPlayer[playerId]
        if (current?.first == serverTick) return current.second
        return nextActionId().also { actionByPlayer[playerId] = serverTick to it }
    }

    fun clear(playerId: UUID) {
        actionByPlayer.remove(playerId)
    }
}
