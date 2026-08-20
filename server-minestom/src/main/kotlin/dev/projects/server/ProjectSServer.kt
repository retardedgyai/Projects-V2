package dev.projects.server

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.AttackHitConfirmed
import dev.projects.protocol.AttackInput
import dev.projects.protocol.AttackStarted
import dev.projects.protocol.AirJumpInput
import dev.projects.protocol.AerialHoldInput
import dev.projects.protocol.ClassSkillInput
import dev.projects.protocol.DodgeInput
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import net.kyori.adventure.text.Component
import net.kyori.adventure.bossbar.BossBar
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerPluginMessageEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.common.PluginMessagePacket
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.key.Key
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.floor
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.ArgumentType

private const val SERVER_ADDRESS = "127.0.0.1"
private const val SERVER_PORT = 25565
private const val DEFAULT_ATTACK_SPEED = 1.0
private val SUPPORTED_ATTACK_SPEEDS = setOf(1.0, 1.5, 2.0)
private const val DODGE_PLAYER_WIDTH = 0.6
private const val DODGE_PLAYER_HEIGHT = 1.8

fun main() {
    val server = MinecraftServer.init(Auth.Offline())
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.setTime(6000)
    instance.defaultClock()?.pause()
    instance.setWeather(Weather.CLEAR)
    instance.setChunkSupplier(::LightingChunk)
    instance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK) }

    val events = MinecraftServer.getGlobalEventHandler()
    val combatStates = mutableMapOf<UUID, CombatState>()
    val dodgeStates = mutableMapOf<UUID, DodgeState>()
    val twinRodsAirStates = mutableMapOf<UUID, TwinRodsAirState>()
    val classResources = mutableMapOf<UUID, ClassResourceState>()
    val aerialHovers = mutableMapOf<UUID, AerialHoverState>()
    val aerialGaugeRewards = mutableMapOf<UUID, AerialGaugeRewardState>()
    val resourceSyncTicks = mutableMapOf<UUID, Int>()
    val lastSentResourceGauge = mutableMapOf<UUID, Int>()
    val dodgeVelocityActive = mutableMapOf<UUID, Boolean>()
    val attackSpeeds = mutableMapOf<UUID, Double>()
    val prototypeBoss = PrototypeBossState()
    val bossBar = BossBar.bossBar(
        Component.text("Prototype Hunt Boss ${prototypeBoss.currentHealth} / ${prototypeBoss.maxHealth}"),
        prototypeBoss.healthProgress,
        BossBar.Color.RED,
        BossBar.Overlay.PROGRESS,
    )
    var dummy: Entity? = null
    var testerMarkerTick = 0L
    val fixedTester = FixedAttackTester()

    fun sendResourceSnapshot(player: net.minestom.server.entity.Player) {
        val resources = classResources[player.uuid] ?: return
        player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(resources.snapshot()))
        lastSentResourceGauge[player.uuid] = resources.aerialGaugeDisplay
    }

    fun updateBossBar() {
        val status = when {
            prototypeBoss.isVictory -> "VICTORY"
            prototypeBoss.isDefeat -> "DEFEAT"
            else -> null
        }
        val label = buildString {
            append("Prototype Hunt Boss")
            if (status != null) append(" - $status")
            append(" ${prototypeBoss.currentHealth} / ${prototypeBoss.maxHealth}")
        }
        bossBar.name(Component.text(label))
        bossBar.progress(prototypeBoss.healthProgress)
    }

    fun stopPlayerActions() {
        combatStates.values.forEach { it.reset() }
        dodgeStates.values.forEach { it.reset() }
        twinRodsAirStates.values.forEach { it.tick(true) }
        aerialHovers.values.forEach { it.reset() }
        instance.players.forEach { player ->
            player.setVelocity(Vec.ZERO)
        }
        dodgeVelocityActive.clear()
    }

    fun resetPlayers() {
        stopPlayerActions()
        instance.players.forEach { player ->
            classResources[player.uuid]?.reset()
            aerialGaugeRewards[player.uuid]?.reset()
            resourceSyncTicks[player.uuid] = 0
            player.setHealth(prototypeBoss.playerMaxHealth.toFloat())
            player.teleport(player.respawnPoint)
            player.showBossBar(bossBar)
            sendResourceSnapshot(player)
        }
    }

    fun finishEncounter() {
        fixedTester.reset()
        stopPlayerActions()
        updateBossBar()
        val result = if (prototypeBoss.isVictory) "VICTORY" else "DEFEAT"
        instance.players.forEach { it.sendMessage(Component.text(result)) }
    }

    fun resetEncounter() {
        prototypeBoss.reset()
        fixedTester.reset()
        resetPlayers()
        updateBossBar()
        instance.players.forEach { it.sendMessage(Component.text("Prototype Hunt Boss reset")) }
    }

    val speedArgument = ArgumentType.Double("speed")
    fun handleAttackSpeed(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player
        if (player == null) {
            sender.sendMessage(Component.text("/as can only be used by a player"))
            return
        }
        val speed = context.get<Double>(speedArgument)
        if (speed !in SUPPORTED_ATTACK_SPEEDS) {
            player.sendMessage(Component.text("Use /as 1.0, /as 1.5, or /as 2.0"))
            return
        }
        attackSpeeds[player.uuid] = speed
        player.sendMessage(Component.text("Attack Speed set to $speed"))
    }
    MinecraftServer.getCommandManager().register(
        Command("as").apply { addSyntax(::handleAttackSpeed, speedArgument) },
    )
    MinecraftServer.getCommandManager().register(
        Command("bossreset").apply { setDefaultExecutor { _, _ -> resetEncounter() } },
    )

    events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
        event.player.respawnPoint = Pos(0.0, 41.0, 0.0)
    }
    events.addListener(PlayerSpawnEvent::class.java) { event ->
        prototypeBoss.registerPlayer(event.player.uuid)
        event.player.setHealth(prototypeBoss.playerMaxHealth.toFloat())
        val resources = classResources.getOrPut(event.player.uuid) { ClassResourceState() }
        val hover = aerialHovers.getOrPut(event.player.uuid) { AerialHoverState() }
        val rewards = aerialGaugeRewards.getOrPut(event.player.uuid) { AerialGaugeRewardState() }
        if (!event.isFirstSpawn) {
            resources.reset()
            hover.reset()
            rewards.reset()
        }
        resourceSyncTicks[event.player.uuid] = 0
        sendResourceSnapshot(event.player)
        updateBossBar()
        event.player.showBossBar(bossBar)
        if (event.isFirstSpawn) {
            attackSpeeds[event.player.uuid] = DEFAULT_ATTACK_SPEED
            combatStates[event.player.uuid] = CombatState(
                weaponSource = { weaponFor(event.player) },
                attackSpeedSource = { attackSpeeds[event.player.uuid] ?: DEFAULT_ATTACK_SPEED },
            )
            dodgeStates[event.player.uuid] = DodgeState()
            twinRodsAirStates[event.player.uuid] = TwinRodsAirState()
            event.player.inventory.addItemStack(
                ItemStack.builder(Material.NETHERITE_SWORD).customName(Component.text("Heavy Blade")).build(),
            )
            event.player.inventory.addItemStack(
                ItemStack.builder(Material.BLAZE_ROD).customName(Component.text("Twin Rods")).build(),
            )
            if (dummy == null) {
                dummy = Entity(EntityType.RAVAGER).apply {
                    customName = Component.text("Prototype Hunt Boss")
                    isCustomNameVisible = true
                    setNoGravity(true)
                    setHasPhysics(false)
                    val spawnPos = event.player.position
                        .add(event.player.position.direction().mul(3.0))
                    setInstance(
                        instance,
                        spawnPos.withDirection(directionFrom(spawnPos, event.player.position)),
                    )
                }
            }
            event.player.sendPacket(
                PluginMessagePacket("minecraft:register", PROJECTS_CHANNEL.toByteArray(StandardCharsets.UTF_8)),
            )
            event.player.sendPacket(
                PluginMessagePacket(
                    PROJECTS_CHANNEL,
                    ProtocolCodec.encode(ProtocolHello(ProtocolVersion.CURRENT)),
                ),
            )
        }
    }
    events.addListener(PlayerDisconnectEvent::class.java) { event ->
        val playerId = event.player.uuid
        combatStates.remove(playerId)
        dodgeStates.remove(playerId)
        twinRodsAirStates.remove(playerId)
        classResources.remove(playerId)
        aerialHovers.remove(playerId)
        aerialGaugeRewards.remove(playerId)
        resourceSyncTicks.remove(playerId)
        lastSentResourceGauge.remove(playerId)
        attackSpeeds.remove(playerId)
    }
    events.addListener(PlayerTickEvent::class.java) { event ->
        val state = combatStates[event.player.uuid] ?: return@addListener
        val dodge = dodgeStates[event.player.uuid] ?: return@addListener
        val twinRodsAir = twinRodsAirStates[event.player.uuid] ?: return@addListener
        val resources = classResources[event.player.uuid] ?: return@addListener
        val aerialHover = aerialHovers[event.player.uuid] ?: return@addListener
        val aerialRewards = aerialGaugeRewards[event.player.uuid] ?: return@addListener
        if (event.player == instance.players.firstOrNull()) {
            if (prototypeBoss.isActive) {
                tickFixedTester(instance, dummy, fixedTester, prototypeBoss, testerMarkerTick++)
                if (!prototypeBoss.isActive) {
                    finishEncounter()
                    return@addListener
                }
            }
        }
        if (!prototypeBoss.isActive) return@addListener
        twinRodsAir.tick(event.player.isOnGround)
        if (dodge.hasPending) state.deferAttackRestart()
        val tester = dummy?.takeIf { it.instance == event.player.instance && !it.isRemoved }
        val testerId = tester?.uuid
        val weakpoint = tester?.let {
            val weapon = state.activeProfile?.weapon ?: weaponFor(event.player)
            val range = state.activeProfile?.range
                ?: weapon.profile(attackSpeeds[event.player.uuid] ?: DEFAULT_ATTACK_SPEED).range
            FixedAttackTester.selectWeakpoint(
                playerPosition = event.player.position.add(0.0, event.player.eyeHeight, 0.0),
                playerDirection = event.player.position.direction(),
                testerOrigin = it.position,
                testerFacing = it.position.direction(),
                weaponRange = range,
            )
        }
        val targets = tester?.let { listOf(CombatTarget(it.uuid, weakpoint?.center ?: it.position)) } ?: emptyList()
        val combatEvents = state.tick(event.player.position, event.player.position.direction(), targets)
        combatEvents.filterIsInstance<CombatEvent.HitConfirmed>().forEach { hit ->
            val damage = prototypeBoss.applyPlayerAttack(
                attackExecutionId = hit.attackExecutionId,
                weapon = hit.weapon,
                weakpoint = weakpoint?.weakpoint,
            )
            twinRodsAir.onAttackHit(hit.weapon, event.player.isOnGround, hit.attackExecutionId)
            if (aerialRewards.onNormalHit(resources, hit.attackExecutionId, weakpoint?.weakpoint)) {
                sendResourceSnapshot(event.player)
            }
            if (damage > 0) {
                updateBossBar()
                if (weakpoint != null) showWeakpointHit(event.player, weakpoint)
            }
        }
        publishCombatEvents(event.player, combatEvents)
        if (!prototypeBoss.isActive) {
            finishEncounter()
            return@addListener
        }
        val currentWeapon = weaponFor(event.player)
        if (currentWeapon != WeaponType.TWIN_RODS) twinRodsAir.clearAirJump()
        val velocityWasApplied = dodgeVelocityActive[event.player.uuid] == true
        val movement = dodge.tick(
            canStart = !state.isAttacking,
            facing = event.player.position.direction(),
            startAllowed = {
                event.player.isOnGround ||
                    (weaponFor(event.player) == WeaponType.TWIN_RODS && twinRodsAir.canStartAirDodge())
            },
            onStart = {
                if (!event.player.isOnGround) check(twinRodsAir.consumeAirDodge())
            },
        )
        if (movement != null) {
            moveDodge(event.player, dodge, movement)
            dodgeVelocityActive[event.player.uuid] = true
        } else if (velocityWasApplied) {
            stopDodgeVelocity(event.player)
            dodgeVelocityActive[event.player.uuid] = false
        }
        val hoverTick = aerialHover.tick(
            isGrounded = event.player.isOnGround,
            velocityY = event.player.velocity.y(),
            resources = resources,
        )
        if (hoverTick.drained > 0.0) {
            val velocity = event.player.velocity
            event.player.setVelocity(Vec(velocity.x(), hoverTick.velocityY, velocity.z()))
        }
        val syncTick = (resourceSyncTicks[event.player.uuid] ?: 0) + 1
        if (syncTick >= 2) {
            resourceSyncTicks[event.player.uuid] = 0
            if (resources.aerialGaugeDisplay != lastSentResourceGauge[event.player.uuid]) {
                sendResourceSnapshot(event.player)
            }
        } else {
            resourceSyncTicks[event.player.uuid] = syncTick
        }
    }
    events.addListener(PlayerPluginMessageEvent::class.java) { event ->
        if (event.identifier != PROJECTS_CHANNEL) return@addListener

        try {
            val message = ProtocolCodec.decode(event.message)
            when (message) {
                is ProtocolHelloAck -> {
                    ProtocolVersion.requireCompatible(message.version)
                    event.player.sendMessage(Component.text("ProjectS protocol ${message.version} handshake complete"))
                    println("ProjectS handshake complete for ${event.player.username}")
                }
                is AttackInput -> {
                    if (!prototypeBoss.isActive) return@addListener
                    val state = combatStates[event.player.uuid] ?: return@addListener
                    publishCombatEvents(event.player, state.input(message.state))
                }
                is DodgeInput -> {
                    if (!prototypeBoss.isActive) return@addListener
                    val state = combatStates[event.player.uuid] ?: return@addListener
                    val dodge = dodgeStates[event.player.uuid] ?: return@addListener
                    val twinRodsAir = twinRodsAirStates[event.player.uuid] ?: return@addListener
                    dodge.request(
                        message,
                        canStart = !state.isAttacking,
                        facing = event.player.position.direction(),
                        startAllowed = {
                            event.player.isOnGround ||
                                (weaponFor(event.player) == WeaponType.TWIN_RODS && twinRodsAir.canStartAirDodge())
                        },
                        onStart = {
                            if (!event.player.isOnGround) check(twinRodsAir.consumeAirDodge())
                        },
                    )
                }
                is AirJumpInput -> {
                    if (!prototypeBoss.isActive) return@addListener
                    val twinRodsAir = twinRodsAirStates[event.player.uuid] ?: return@addListener
                    if (!event.player.isOnGround &&
                        weaponFor(event.player) == WeaponType.TWIN_RODS &&
                        twinRodsAir.consumeAirJump()
                    ) {
                        event.player.setVelocity(
                            airJumpVelocity(event.player.velocity, event.player.position.direction(), message),
                        )
                    }
                }
                is ClassSkillInput -> {
                    // Skill effects are intentionally not part of this foundation.
                }
                is AerialHoldInput -> {
                    val hover = aerialHovers[event.player.uuid] ?: return@addListener
                    if (!prototypeBoss.isActive) {
                        hover.reset()
                    } else {
                        hover.request(message.active, event.player.isOnGround)
                    }
                }
                else -> throw IllegalArgumentException("Unexpected ProjectS message")
            }
        } catch (error: IllegalArgumentException) {
            event.player.kick(Component.text(error.message ?: "Invalid ProjectS protocol handshake"))
        }
    }

    server.start(SERVER_ADDRESS, SERVER_PORT)
    println("ProjectS Minestom server listening on $SERVER_ADDRESS:$SERVER_PORT")
}

private fun publishCombatEvents(player: net.minestom.server.entity.Player, events: List<CombatEvent>) {
    for (event in events) {
        val message = when (event) {
            is CombatEvent.Started -> AttackStarted(event.attackExecutionId)
            is CombatEvent.HitConfirmed -> AttackHitConfirmed(event.attackExecutionId, event.targetId)
        }
        player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(message))
    }
}

private fun weaponFor(player: net.minestom.server.entity.Player): WeaponType = when (
    player.getEquipment(EquipmentSlot.MAIN_HAND).material()
) {
    Material.BLAZE_ROD -> WeaponType.TWIN_RODS
    Material.NETHERITE_SWORD -> WeaponType.HEAVY_BLADE
    else -> WeaponType.HEAVY_BLADE
}

private fun tickFixedTester(
    instance: Instance,
    testerEntity: Entity?,
    tester: FixedAttackTester,
    bossState: PrototypeBossState,
    markerTick: Long,
) {
    if (testerEntity == null || testerEntity.isRemoved || testerEntity.instance != instance) return
    val players = instance.players.filter { it.isOnline }
    if (players.isEmpty()) return
    if (markerTick % 2L == 0L) {
        val weakpointFacing = testerEntity.position.direction()
        players.forEach { player -> showWeakpointMarkers(player, testerEntity.position, weakpointFacing) }
    }
    val targets = players.map { FixedAttackTarget(it.uuid, it.position) }
    val facing = players.firstOrNull()?.let { directionFrom(testerEntity.position, it.position) }
        ?: testerEntity.position.direction()
    val events = tester.tick(testerEntity.position, facing, targets)
    for (event in events) {
        when (event) {
            is FixedAttackEvent.Started -> {
                val label = event.attack.displayName()
                players.forEach { player ->
                    player.sendMessage(Component.text("[Tester] $label: telegraph"))
                }
            }
            is FixedAttackEvent.Telegraph -> {
                players.forEach { player ->
                    showTesterTelegraph(player, testerEntity.position, event.attack, event.direction)
                }
            }
            is FixedAttackEvent.Active -> {
                players.forEach { player ->
                    showTesterActive(player, testerEntity.position, event.attack, event.direction)
                    player.sendMessage(Component.text("[Tester] ${event.attack.displayName()}: ACTIVE"))
                }
            }
            is FixedAttackEvent.HitConfirmed -> {
                instance.getPlayerByUuid(event.targetId)?.let { player ->
                    val damage = bossState.applyBossAttack(player.uuid, event.executionId, event.attack)
                    if (damage == 0) return@let
                    player.setHealth(bossState.playerEntityHealth(player.uuid))
                    player.sendMessage(Component.text("[Tester] HIT"))
                    player.sendPacket(
                        ParticlePacket(
                            Particle.DAMAGE_INDICATOR,
                            player.position.x(),
                            player.position.y() + 1.0,
                            player.position.z(),
                            0.35f,
                            0.45f,
                            0.35f,
                            0.1f,
                            8,
                        ),
                    )
                }
            }
        }
    }
}

private fun showWeakpointMarkers(player: net.minestom.server.entity.Player, origin: Pos, facing: Vec) {
    val forward = FixedAttackTester.normalizeHorizontal(facing)
    val right = Vec(-forward.z(), 0.0, forward.x())
    for (weakpoint in FixedWeakpoint.entries) {
        val center = FixedAttackTester.weakpointCenter(origin, forward, weakpoint)
        for (step in 0..5) {
            val angle = step * Math.PI / 3.0
            val radial = Vec(
                right.x() * kotlin.math.cos(angle) + forward.x() * kotlin.math.sin(angle),
                0.0,
                right.z() * kotlin.math.cos(angle) + forward.z() * kotlin.math.sin(angle),
            )
            sendTesterParticle(
                player,
                Particle.GLOW,
                center.add(radial.x() * 0.18, kotlin.math.cos(angle) * 0.08, radial.z() * 0.18),
            )
        }
    }
}

private fun showWeakpointHit(
    player: net.minestom.server.entity.Player,
    selection: FixedWeakpointSelection,
) {
    val center = selection.center
    player.sendMessage(Component.text("[Tester] WEAKPOINT: ${selection.weakpoint}"))
    player.sendPacket(
        ParticlePacket(
            Particle.CRIT,
            center.x(),
            center.y(),
            center.z(),
            0.45f,
            0.45f,
            0.45f,
            0.18f,
            18,
        ),
    )
    player.sendPacket(
        ParticlePacket(
            Particle.DAMAGE_INDICATOR,
            center.x(),
            center.y(),
            center.z(),
            0.2f,
            0.2f,
            0.2f,
            0.1f,
            10,
        ),
    )
    player.playSound(
        Sound.sound(
            requireNotNull(SoundEvent.fromKey(Key.key("minecraft", "entity.player.attack.crit"))),
            Sound.Source.PLAYER,
            1.0f,
            1.2f,
        ),
        center,
    )
}

internal fun directionFrom(origin: Pos, target: Pos): Vec =
    FixedAttackTester.normalizeHorizontal(
        Vec(target.x() - origin.x(), 0.0, target.z() - origin.z()),
    )

private fun showTesterTelegraph(
    player: net.minestom.server.entity.Player,
    origin: Pos,
    attack: FixedAttackType,
    direction: Vec,
) {
    val right = Vec(-direction.z(), 0.0, direction.x())
    when (attack) {
        FixedAttackType.SIDE_SWEEP -> {
            for (step in 0..8) {
                val angle = -1.15 + 2.3 * step / 8.0
                val radial = Vec(
                    direction.x() * kotlin.math.cos(angle) + right.x() * kotlin.math.sin(angle),
                    0.0,
                    direction.z() * kotlin.math.cos(angle) + right.z() * kotlin.math.sin(angle),
                )
                sendTesterParticle(player, Particle.ELECTRIC_SPARK, origin.add(radial.x() * 4.5, 0.08, radial.z() * 4.5))
            }
        }
        FixedAttackType.FORWARD_SLAM -> {
            for (step in 1..5) {
                val distance = step.toDouble()
                for (side in -1..1) {
                    val point = origin.add(
                        direction.x() * distance + right.x() * side * 0.9,
                        0.08,
                        direction.z() * distance + right.z() * side * 0.9,
                    )
                    sendTesterParticle(player, Particle.END_ROD, point)
                }
            }
        }
    }
}

private fun showTesterActive(
    player: net.minestom.server.entity.Player,
    origin: Pos,
    attack: FixedAttackType,
    direction: Vec,
) {
    val right = Vec(-direction.z(), 0.0, direction.x())
    when (attack) {
        FixedAttackType.SIDE_SWEEP -> {
            for (step in 0..10) {
                val angle = -1.15 + 2.3 * step / 10.0
                val radial = Vec(
                    direction.x() * kotlin.math.cos(angle) + right.x() * kotlin.math.sin(angle),
                    0.0,
                    direction.z() * kotlin.math.cos(angle) + right.z() * kotlin.math.sin(angle),
                )
                sendTesterParticle(player, Particle.SWEEP_ATTACK, origin.add(radial.x() * 3.0, 1.0, radial.z() * 3.0))
            }
        }
        FixedAttackType.FORWARD_SLAM -> {
            sendTesterParticle(
                player,
                Particle.EXPLOSION,
                origin.add(direction.x() * 3.0, 0.5, direction.z() * 3.0),
            )
        }
    }
}

private fun sendTesterParticle(
    player: net.minestom.server.entity.Player,
    particle: Particle,
    point: net.minestom.server.coordinate.Point,
) {
    player.sendPacket(ParticlePacket(particle, point.x(), point.y(), point.z(), 0f, 0f, 0f, 0f, 1))
}

private fun FixedAttackType.displayName(): String = when (this) {
    FixedAttackType.SIDE_SWEEP -> "Side Sweep"
    FixedAttackType.FORWARD_SLAM -> "Forward Slam"
}

private fun moveDodge(
    player: net.minestom.server.entity.Player,
    dodge: DodgeState,
    movement: net.minestom.server.coordinate.Vec,
) {
    val current = player.position
    val target = current.add(movement.x(), 0.0, movement.z())
    val instance = player.instance
    if (isDodgePathClear(instance, current, target)) {
        player.setVelocity(dodgeVelocity(movement, player.velocity.y()))
        return
    }

    var safeProgress = 0.0
    var blockedProgress = 1.0
    repeat(8) {
        val progress = (safeProgress + blockedProgress) / 2.0
        val samplePosition = current.add(
            (target.x() - current.x()) * progress,
            0.0,
            (target.z() - current.z()) * progress,
        )
        if (isDodgePathClear(instance, current, samplePosition)) {
            safeProgress = progress
        } else {
            blockedProgress = progress
        }
    }

    dodge.stop()
    player.setVelocity(dodgeVelocity(movement.mul(safeProgress), player.velocity.y()))
}

internal fun dodgeVelocity(movement: Vec, verticalVelocity: Double): Vec = Vec(
    movement.x() * ServerFlag.SERVER_TICKS_PER_SECOND,
    verticalVelocity,
    movement.z() * ServerFlag.SERVER_TICKS_PER_SECOND,
)

internal fun airJumpVelocity(velocity: Vec, facing: Vec, input: AirJumpInput): Vec {
    val verticalVelocity = maxOf(velocity.y(), AIR_JUMP_VERTICAL_SPEED)
    if (input.directionX == 0.0 && input.directionZ == 0.0) {
        return Vec(velocity.x(), verticalVelocity, velocity.z())
    }

    val direction = dodgeDirection(facing, DodgeInput(input.directionX, input.directionZ))
    return Vec(
        direction.x() * AIR_JUMP_HORIZONTAL_SPEED,
        verticalVelocity,
        direction.z() * AIR_JUMP_HORIZONTAL_SPEED,
    )
}

private const val AIR_JUMP_VERTICAL_SPEED = 8.4
private const val AIR_JUMP_HORIZONTAL_SPEED = 5.0

private fun stopDodgeVelocity(player: net.minestom.server.entity.Player) {
    player.setVelocity(stopDodgeVelocity(player.velocity))
}

internal fun stopDodgeVelocity(velocity: Vec): Vec = Vec(0.0, velocity.y(), 0.0)

private fun isDodgePathClear(instance: Instance, start: Pos, end: Pos): Boolean {
    val samples = 4
    for (sample in 1..samples) {
        val progress = sample.toDouble() / samples
        val samplePosition = start.add(
            (end.x() - start.x()) * progress,
            0.0,
            (end.z() - start.z()) * progress,
        )
        if (!isDodgePositionClear(instance, samplePosition)) return false
    }
    return true
}

private fun isDodgePositionClear(instance: Instance, position: Pos): Boolean {
    val minX = floor(position.x() - DODGE_PLAYER_WIDTH / 2.0).toInt()
    val maxX = floor(position.x() + DODGE_PLAYER_WIDTH / 2.0 - 1.0e-6).toInt()
    val minY = floor(position.y()).toInt()
    val maxY = floor(position.y() + DODGE_PLAYER_HEIGHT - 1.0e-6).toInt()
    val minZ = floor(position.z() - DODGE_PLAYER_WIDTH / 2.0).toInt()
    val maxZ = floor(position.z() + DODGE_PLAYER_WIDTH / 2.0 - 1.0e-6).toInt()

    for (x in minX..maxX) {
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                if (instance.getBlock(x, y, z).blocksMotion()) return false
            }
        }
    }
    return true
}
