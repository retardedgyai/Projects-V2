package dev.projects.server

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.AttackHitConfirmed
import dev.projects.protocol.AttackInput
import dev.projects.protocol.AttackStarted
import dev.projects.protocol.DodgeInput
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import net.kyori.adventure.text.Component
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerPluginMessageEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.common.PluginMessagePacket
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.floor
import kotlin.math.sqrt
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
    val attackSpeeds = mutableMapOf<UUID, Double>()
    var dummy: Entity? = null

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

    events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
        event.player.respawnPoint = Pos(0.0, 41.0, 0.0)
    }
    events.addListener(PlayerSpawnEvent::class.java) { event ->
        if (event.isFirstSpawn) {
            attackSpeeds[event.player.uuid] = DEFAULT_ATTACK_SPEED
            combatStates[event.player.uuid] = CombatState(
                weaponSource = { weaponFor(event.player) },
                attackSpeedSource = { attackSpeeds[event.player.uuid] ?: DEFAULT_ATTACK_SPEED },
            )
            dodgeStates[event.player.uuid] = DodgeState()
            event.player.inventory.addItemStack(
                ItemStack.builder(Material.NETHERITE_SWORD).customName(Component.text("Heavy Blade")).build(),
            )
            event.player.inventory.addItemStack(
                ItemStack.builder(Material.BLAZE_ROD).customName(Component.text("Twin Rods")).build(),
            )
            if (dummy == null) {
                dummy = Entity(EntityType.PIG).apply {
                    customName = Component.text("Dummy")
                    isCustomNameVisible = true
                    setNoGravity(true)
                    setInstance(instance, event.player.position.add(event.player.position.direction().mul(3.0)))
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
    events.addListener(PlayerTickEvent::class.java) { event ->
        val state = combatStates[event.player.uuid] ?: return@addListener
        val dodge = dodgeStates[event.player.uuid] ?: return@addListener
        if (dodge.hasPending) state.deferAttackRestart()
        val targets = dummy?.takeIf { it.instance == event.player.instance && !it.isRemoved }
            ?.let { listOf(CombatTarget(it.uuid, it.position)) }
            ?: emptyList()
        publishCombatEvents(event.player, state.tick(event.player.position, event.player.position.direction(), targets))
        val movement = dodge.tick(canStart = event.player.isOnGround && !state.isAttacking)
        if (movement != null) moveDodge(event.player, dodge, movement)
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
                    val state = combatStates[event.player.uuid] ?: return@addListener
                    publishCombatEvents(event.player, state.input(message.state))
                }
                is DodgeInput -> {
                    val state = combatStates[event.player.uuid] ?: return@addListener
                    val dodge = dodgeStates[event.player.uuid] ?: return@addListener
                    if (!event.player.isOnGround) return@addListener
                    dodge.request(
                        dodgeDirection(event.player.position, message),
                        canStart = !state.isAttacking,
                    )
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

private fun dodgeDirection(position: Pos, input: DodgeInput): net.minestom.server.coordinate.Vec {
    return dodgeDirection(position.direction(), input)
}

internal fun dodgeDirection(facing: net.minestom.server.coordinate.Vec, input: DodgeInput): net.minestom.server.coordinate.Vec {
    val horizontalLength = sqrt(facing.x() * facing.x() + facing.z() * facing.z())
    val forward = if (horizontalLength > 1.0e-9) {
        net.minestom.server.coordinate.Vec(facing.x() / horizontalLength, 0.0, facing.z() / horizontalLength)
    } else {
        net.minestom.server.coordinate.Vec(0.0, 0.0, 1.0)
    }
    val right = net.minestom.server.coordinate.Vec(-forward.z(), 0.0, forward.x())
    val worldDirection = net.minestom.server.coordinate.Vec(
        right.x() * input.directionX + forward.x() * input.directionZ,
        0.0,
        right.z() * input.directionX + forward.z() * input.directionZ,
    )
    return if (input.directionX == 0.0 && input.directionZ == 0.0) {
        forward
    } else {
        DodgeState.normalizeDirection(worldDirection)
    }
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
        player.refreshPosition(target, true)
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
    if (safeProgress > 0.0) {
        player.refreshPosition(
            current.add(
                (target.x() - current.x()) * safeProgress,
                0.0,
                (target.z() - current.z()) * safeProgress,
            ),
            true,
        )
    }
}

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
