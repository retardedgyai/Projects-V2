package dev.projects.server

import dev.projects.protocol.PROJECTS_CHANNEL
import dev.projects.protocol.AttackDebugShape
import dev.projects.protocol.AttackDebugShapeKind
import dev.projects.protocol.AttackHitConfirmed
import dev.projects.protocol.AttackInput
import dev.projects.protocol.AttackStarted
import dev.projects.protocol.AirJumpInput
import dev.projects.protocol.ClassSkillInput
import dev.projects.protocol.ClassSkillSlot
import dev.projects.protocol.DodgeInput
import dev.projects.protocol.GroundTelegraphRemove
import dev.projects.protocol.GroundTelegraphStart
import dev.projects.protocol.ProtocolCodec
import dev.projects.protocol.ProtocolHello
import dev.projects.protocol.ProtocolHelloAck
import dev.projects.protocol.ProtocolVersion
import dev.projects.protocol.RoninHudSnapshot
import dev.projects.protocol.SlashEditorParameters
import dev.projects.protocol.StarweaverHudCelestial
import dev.projects.protocol.StarweaverHudSnapshot
import dev.projects.protocol.VfxEditorNotice
import dev.projects.protocol.VfxEditorOpen
import dev.projects.protocol.VfxEditor2ApplyRequest
import dev.projects.protocol.VfxEditor2LoadRequest
import dev.projects.protocol.VfxEditor2PreviewCancel
import dev.projects.protocol.VfxEditor2PreviewRequest
import dev.projects.protocol.VfxEditor2SaveRequest
import dev.projects.protocol.VfxSlashDraft
import dev.projects.protocol.VfxSlashDraftList
import dev.projects.protocol.VfxSlashDraftLoadRequest
import dev.projects.protocol.VfxSlashPreviewRequest
import dev.projects.protocol.VfxSlashPreviewCancel
import dev.projects.protocol.VfxSlashSaveRequest
import dev.projects.protocol.VfxSlashApplySkill3
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
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerPluginMessageEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.network.packet.server.common.PluginMessagePacket
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.key.Key
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Point
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.ArgumentType
import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleAnchor
import dev.projects.server.particle.ParticleAnchorPoint
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleManager
import dev.projects.server.particle.ParticleProfiler
import dev.projects.server.particle.ParticlePresetRegistry
import dev.projects.server.particle.parseParticlePresetOverrides
import dev.projects.server.particle.startParticlePreset
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import dev.projects.server.particle.startParticleDemo
import dev.projects.server.particle.startParticleV2Diagnostic
import dev.projects.server.particle.ParticleEffectHandle
import dev.projects.server.particle.ParticleViewer
import dev.projects.server.particle.PlayerParticleSink

private const val SERVER_ADDRESS = "127.0.0.1"
private const val SERVER_PORT = 25565
private const val DEFAULT_ATTACK_SPEED = 1.0
private val SUPPORTED_ATTACK_SPEEDS = setOf(1.0, 1.5, 2.0)
private const val DODGE_PLAYER_WIDTH = 0.6
private const val DODGE_PLAYER_HEIGHT = 1.8
private val TWIN_BLADES_AUTO_OFFHAND = Tag.Boolean("projects_twin_blades_auto_offhand").defaultValue(false)

internal enum class PlayableClass {
    TWIN_BLADES,
    STARWEAVER,
    RONIN,
}

internal data class SkillCooldowns(
    val skill1: Int,
    val skill2: Int,
    val skill3: Int,
)

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
    val skill1States = mutableMapOf<UUID, Skill1State>()
    val skill2States = mutableMapOf<UUID, Skill2State>()
    val skill3States = mutableMapOf<UUID, Skill3State>()
    val selectedClasses = mutableMapOf<UUID, PlayableClass>()
    val starweaverStates = mutableMapOf<UUID, StarweaverRuntimeState>()
    val roninStates = mutableMapOf<UUID, RoninState>()
    val roninLockPositions = mutableMapOf<UUID, Pos>()
    val resourceSyncTicks = mutableMapOf<UUID, Int>()
    val lastSentCooldowns = mutableMapOf<UUID, SkillCooldowns>()
    val dodgeVelocityActive = mutableMapOf<UUID, Boolean>()
    val attackSpeeds = mutableMapOf<UUID, Double>()
    val twinBladesSwingAngles = mutableMapOf<UUID, Double>()
    val twinBladesComboStates = mutableMapOf<UUID, TwinBladesComboState>()
    val twinBladesAttackSteps = mutableMapOf<UUID, Int>()
    val prototypeBoss = PrototypeBossState()
    val bossBar = BossBar.bossBar(
        Component.text("Rift Executioner ${prototypeBoss.currentHealth} / ${prototypeBoss.maxHealth}"),
        prototypeBoss.healthProgress,
        BossBar.Color.RED,
        BossBar.Overlay.PROGRESS,
    )
    var dummy: Entity? = null
    var testerMarkerTick = 0L
    val riftExecutioner = RiftExecutionerController()
    val particleAnimations = ParticleAnimationScheduler()
    val particleProfiler = ParticleProfiler()
    val particleManager = ParticleManager(profiler = particleProfiler)
    var nextGroundTelegraphId = 0L
    val lastGroundTelegraphIds = mutableMapOf<UUID, Long>()
    val bossGroundTelegraphIds = mutableSetOf<Long>()
    val bossRiftTelegraphIds = mutableMapOf<Long, Long>()
    val slashDraftStore = SlashDraftStore(Path.of("config", "projects", "vfx-editor", "slash-drafts.json"))
    val skill3SlashBindingStore = Skill3SlashBindingStore(Path.of("config", "projects", "vfx-editor", "twin-blades-skill3-slash.json"))
    val slashPreviewHandles = mutableMapOf<UUID, ParticleEffectHandle>()
    val vfxEditor2 = VfxEditor2Runtime(
        scheduler = particleAnimations,
        particleManager = particleManager,
        send = { player, message ->
            player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(message))
        },
        viewersFor = { source ->
            val sourcePosition = source.position
            instance.players.filter { viewer ->
                viewer.instance == source.instance &&
                    viewer.position.distanceSquared(sourcePosition) <= 64.0 * 64.0
            }
        },
        draftFile = Path.of("config", "projects", "vfx-editor2", "drafts.json"),
        bindingFile = Path.of("config", "projects", "vfx-editor2", "runtime-binding.json"),
    )

    fun sendSlashDraftList(player: net.minestom.server.entity.Player) {
        player.sendPluginMessage(
            PROJECTS_CHANNEL,
            ProtocolCodec.encode(VfxSlashDraftList(slashDraftStore.list())),
        )
    }

    fun openSlashEditor(player: net.minestom.server.entity.Player) {
        player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(VfxEditorOpen()))
        sendSlashDraftList(player)
    }

    fun previewSlash(player: net.minestom.server.entity.Player, parameters: SlashEditorParameters) {
        particleAnimations.cancel(slashPreviewHandles.remove(player.uuid))
        val direction = player.position.direction()
        val origin = slashOrigin(player.position, direction, parameters)
        val sink = particleManager.sink(
            ParticleViewer(player.position, player),
            PlayerParticleSink(player),
            "editor:slash",
        )
        slashPreviewHandles[player.uuid] = particleAnimations.start(
            SlashEditorPreview.create(origin, direction, parameters),
            sink,
            id = "editor:slash:${player.uuid}",
        )
    }

    fun clearBossTelegraphs() {
        bossGroundTelegraphIds.toList().forEach { telegraphId ->
            instance.players.forEach { player ->
                player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(GroundTelegraphRemove(telegraphId)))
            }
        }
        bossGroundTelegraphIds.clear()
        bossRiftTelegraphIds.clear()
    }

    fun sendResourceSnapshot(player: net.minestom.server.entity.Player) {
        val resources = classResources[player.uuid] ?: return
        val skill1 = skill1States[player.uuid] ?: return
        val skill2 = skill2States[player.uuid] ?: return
        val skill3 = skill3States[player.uuid] ?: return
        val cooldowns = SkillCooldowns(
            skill1 = skill1.cooldownTicksRemaining,
            skill2 = skill2.cooldownTicksRemaining,
            skill3 = skill3.cooldownTicksRemaining,
        )
        player.sendPluginMessage(
            PROJECTS_CHANNEL,
            ProtocolCodec.encode(resources.snapshot(cooldowns.skill1, cooldowns.skill2, cooldowns.skill3)),
        )
        lastSentCooldowns[player.uuid] = cooldowns
    }

    fun sendStarweaverHudSnapshot(player: net.minestom.server.entity.Player) {
        val state = starweaverStates[player.uuid] ?: return
        val snapshot = state.rotation.snapshot()
        val selected = selectedClasses[player.uuid] == PlayableClass.STARWEAVER
        player.sendPluginMessage(
            PROJECTS_CHANNEL,
            ProtocolCodec.encode(
                StarweaverHudSnapshot(
                    selected = selected,
                    queue = if (selected) snapshot.queue.map(StarweaverCelestial::toHudCelestial) else emptyList(),
                    stored = snapshot.stored.toHudCelestial(),
                    conjunctionAvailable = selected && snapshot.conjunctionSlot != null,
                    conjunctionUsed = selected && snapshot.conjunctionUsed,
                    reloadTicksRemaining = if (selected) snapshot.reloadTicksRemaining else 0,
                ),
            ),
        )
    }

    fun resetStarweaverPlayerState(player: net.minestom.server.entity.Player) {
        starweaverStates[player.uuid]?.reset()
        restoreStarweaverMovementSpeed(player)
        player.setAdditionalHearts(0f)
    }

    fun sendRoninHudSnapshot(player: net.minestom.server.entity.Player) {
        val state = roninStates[player.uuid] ?: return
        val selected = selectedClasses[player.uuid] == PlayableClass.RONIN
        player.sendPluginMessage(
            PROJECTS_CHANNEL,
            ProtocolCodec.encode(
                RoninHudSnapshot(
                    selected = selected,
                    iaido = if (selected) state.iaido else 0,
                    qCooldownTicks = if (selected) state.qCooldownTicksRemaining else 0,
                    eCooldownTicks = if (selected) state.eCooldownTicksRemaining else 0,
                    rCooldownTicks = if (selected) state.rCooldownTicksRemaining else 0,
                    movementLockTicksRemaining = if (selected) state.movementLockTicksRemaining else 0,
                    wVariant = if (selected) state.iaido else 0,
                ),
            ),
        )
    }

    fun resetRoninPlayerState(player: net.minestom.server.entity.Player) {
        roninStates[player.uuid]?.reset()
        roninLockPositions.remove(player.uuid)
        player.setVelocity(Vec.ZERO)
    }

    fun updateBossBar() {
        val status = when {
            prototypeBoss.isVictory -> "VICTORY"
            prototypeBoss.isDefeat -> "DEFEAT"
            prototypeBoss.encounterState == PrototypeEncounterState.FINAL_STRUGGLE -> "FINAL STRUGGLE"
            else -> null
        }
        val label = buildString {
            append("Rift Executioner")
            if (status != null) append(" - $status")
            append(" ${prototypeBoss.currentHealth} / ${prototypeBoss.maxHealth}")
        }
        bossBar.name(Component.text(label))
        bossBar.progress(prototypeBoss.healthProgress)
    }

    fun stopPlayerActions() {
        combatStates.values.forEach { it.reset() }
        twinBladesComboStates.values.forEach { it.reset() }
        twinBladesAttackSteps.clear()
        dodgeStates.values.forEach { it.reset() }
        twinRodsAirStates.values.forEach { it.tick(true) }
        skill1States.values.forEach { it.reset() }
        skill2States.values.forEach { it.reset() }
        skill3States.values.forEach { it.reset() }
        starweaverStates.values.forEach { it.reset() }
        roninStates.values.forEach { it.reset() }
        roninLockPositions.clear()
        instance.players.forEach { player ->
            player.setVelocity(Vec.ZERO)
            restoreStarweaverMovementSpeed(player)
            player.setAdditionalHearts(0f)
        }
        dodgeVelocityActive.clear()
    }

    fun resetPlayers() {
        stopPlayerActions()
        instance.players.forEach { player ->
            classResources[player.uuid]?.reset()
            resourceSyncTicks[player.uuid] = 0
            player.setHealth(prototypeBoss.playerMaxHealth.toFloat())
            player.teleport(player.respawnPoint)
            player.showBossBar(bossBar)
            sendResourceSnapshot(player)
        }
    }

    fun finishEncounter() {
        clearBossTelegraphs()
        riftExecutioner.reset()
        prototypeBoss.setBreakActive(false)
        stopPlayerActions()
        updateBossBar()
        val result = if (prototypeBoss.isVictory) "VICTORY" else "DEFEAT"
        instance.players.forEach {
            sendResourceSnapshot(it)
            it.sendMessage(Component.text(result))
        }
    }

    fun resetEncounter() {
        prototypeBoss.reset()
        clearBossTelegraphs()
        riftExecutioner.reset()
        dummy?.let { boss ->
            instance.players.firstOrNull()?.let { player -> boss.teleport(player.respawnPoint) }
        }
        resetPlayers()
        updateBossBar()
        instance.players.forEach { it.sendMessage(Component.text("Rift Executioner reset")) }
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

    fun handleGameMode(sender: CommandSender, mode: GameMode) {
        val player = sender as? net.minestom.server.entity.Player
        if (player == null) {
            sender.sendMessage(Component.text("This command can only be used by a player"))
            return
        }
        player.setGameMode(mode)
        player.sendMessage(Component.text("Game mode set to ${mode.name.lowercase()}"))
    }

    val classNameArgument = ArgumentType.Word("className")
    fun handleClass(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val requested = context.get<String>(classNameArgument).lowercase(Locale.ROOT)
        val selected = when (requested) {
            "starweaver", "star_weaver" -> PlayableClass.STARWEAVER
            "twinblades", "twin_blades", "twin-blades" -> PlayableClass.TWIN_BLADES
            "ronin" -> PlayableClass.RONIN
            else -> {
                player.sendMessage(Component.text("Use /class starweaver, /class twinblades, or /class ronin"))
                return
            }
        }
        resetStarweaverPlayerState(player)
        resetRoninPlayerState(player)
        combatStates[player.uuid]?.reset()
        dodgeStates[player.uuid]?.reset()
        twinRodsAirStates[player.uuid]?.tick(true)
        skill1States[player.uuid]?.reset()
        skill2States[player.uuid]?.reset()
        skill3States[player.uuid]?.reset()
        selectedClasses[player.uuid] = selected
        player.setVelocity(Vec.ZERO)
        when (selected) {
            PlayableClass.STARWEAVER -> player.sendMessage(Component.text("Class selected: Starweaver (Q/W/E/R slots)"))
            PlayableClass.RONIN -> player.sendMessage(Component.text("Class selected: Ronin (Q/W/E/R slots)"))
            PlayableClass.TWIN_BLADES -> {
                player.sendActionBar(Component.empty())
                player.sendMessage(Component.text("Class selected: Twin Blades"))
            }
        }
        if (selected != PlayableClass.STARWEAVER && selected != PlayableClass.RONIN) {
            player.sendActionBar(Component.empty())
        }
        sendStarweaverHudSnapshot(player)
        sendRoninHudSnapshot(player)
    }

    fun handleStarweaverState(sender: CommandSender) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        if (selectedClasses[player.uuid] != PlayableClass.STARWEAVER) {
            player.sendMessage(Component.text("Select Starweaver first with /class starweaver"))
            return
        }
        sendStarweaverHudSnapshot(player)
    }

    val queueArguments = (1..6).map { ArgumentType.Word("mark$it") }
    fun handleStarweaverQueue(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val state = starweaverStates[player.uuid]
        if (state == null || selectedClasses[player.uuid] != PlayableClass.STARWEAVER) {
            player.sendMessage(Component.text("Select Starweaver first with /class starweaver"))
            return
        }
        val marks = queueArguments.map { argument ->
            when (context.get<String>(argument).lowercase(Locale.ROOT)) {
                "sun" -> StarweaverCelestial.SUN
                "moon" -> StarweaverCelestial.MOON
                "star" -> StarweaverCelestial.STAR
                else -> null
            }
        }
        if (marks.any { it == null }) {
            player.sendMessage(Component.text("Use /starweaverqueue sun|moon|star six times"))
            return
        }
        runCatching {
            state.rotation.setRotationForTest(marks.take(5).filterNotNull(), marks[5]!!)
        }.onFailure { error ->
            player.sendMessage(Component.text(error.message ?: "Invalid Starweaver rotation"))
            return
        }
        sendStarweaverHudSnapshot(player)
        player.sendMessage(Component.text("Starweaver rotation fixed for testing"))
    }

    MinecraftServer.getCommandManager().register(
        Command("as").apply { addSyntax(::handleAttackSpeed, speedArgument) },
    )
    MinecraftServer.getCommandManager().register(
        Command("creative").apply {
            setDefaultExecutor { sender, _ -> handleGameMode(sender, GameMode.CREATIVE) }
        },
    )
    MinecraftServer.getCommandManager().register(
        Command("survival").apply {
            setDefaultExecutor { sender, _ -> handleGameMode(sender, GameMode.SURVIVAL) }
        },
    )
    MinecraftServer.getCommandManager().register(
        Command("class").apply { addSyntax(::handleClass, classNameArgument) },
    )
    MinecraftServer.getCommandManager().register(
        Command("starweaverstate").apply { setDefaultExecutor { sender, _ -> handleStarweaverState(sender) } },
    )
    MinecraftServer.getCommandManager().register(
        Command("starweaverqueue").apply {
            addSyntax(::handleStarweaverQueue, *queueArguments.toTypedArray())
        },
    )
    MinecraftServer.getCommandManager().register(
        Command("bossreset").apply { setDefaultExecutor { _, _ -> resetEncounter() } },
    )
    val bossPhaseArgument = ArgumentType.Word("phase")
    fun handleBossPhase(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        when (context.get<String>(bossPhaseArgument).lowercase()) {
            "1" -> prototypeBoss.forcePhase(PrototypeBossPhase.DUEL)
            "2" -> prototypeBoss.forcePhase(PrototypeBossPhase.RIFT_PRESSURE)
            "3" -> prototypeBoss.forcePhase(PrototypeBossPhase.EXECUTION)
            "final" -> prototypeBoss.forceFinalStruggle()
            else -> {
                player.sendMessage(Component.text("Use /bossphase 1|2|3|final"))
                return
            }
        }
        clearBossTelegraphs()
        riftExecutioner.reset()
        updateBossBar()
        player.sendMessage(Component.text("Rift Executioner phase ${context.get<String>(bossPhaseArgument)}"))
    }
    MinecraftServer.getCommandManager().register(
        Command("bossphase").apply { addSyntax(::handleBossPhase, bossPhaseArgument) },
    )
    val aoeSectorLiteral = ArgumentType.Literal("sector")
    val aoeClearLiteral = ArgumentType.Literal("clear")
    val aoeRadiusArgument = ArgumentType.Double("radius")
    val aoeAngleArgument = ArgumentType.Double("angle")
    val aoeDurationArgument = ArgumentType.Integer("durationTicks")

    fun startAoeSector(
        sender: CommandSender,
        radius: Double = 6.0,
        angleDegrees: Double = 100.0,
        durationTicks: Int = 140,
    ) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val horizontalFacing = FixedAttackTester.normalizeHorizontal(player.position.direction())
        lastGroundTelegraphIds.remove(player.uuid)?.let { previousId ->
            player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(GroundTelegraphRemove(previousId)))
        }
        val message = runCatching {
            GroundTelegraphStart.clamped(
                telegraphId = ++nextGroundTelegraphId,
                centerX = player.position.x() + horizontalFacing.x() * 5.0,
                centerY = player.position.y(),
                centerZ = player.position.z() + horizontalFacing.z() * 5.0,
                facingX = horizontalFacing.x(),
                facingZ = horizontalFacing.z(),
                radius = radius,
                angleDegrees = angleDegrees,
                durationTicks = durationTicks,
            )
        }.getOrElse {
            player.sendMessage(Component.text("/aoedemo sector values must be finite"))
            return
        }
        lastGroundTelegraphIds[player.uuid] = message.telegraphId
        player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(message))
        player.sendMessage(Component.text("Ground sector telegraph started"))
    }

    MinecraftServer.getCommandManager().register(
        Command("aoedemo").apply {
            addSyntax({ sender, _ -> startAoeSector(sender) }, aoeSectorLiteral)
            addSyntax(
                { sender, context ->
                    startAoeSector(
                        sender,
                        context.get<Double>(aoeRadiusArgument),
                        context.get<Double>(aoeAngleArgument),
                        context.get<Int>(aoeDurationArgument),
                    )
                },
                aoeSectorLiteral,
                aoeRadiusArgument,
                aoeAngleArgument,
                aoeDurationArgument,
            )
            addSyntax({ sender, _ ->
                val player = sender as? net.minestom.server.entity.Player ?: return@addSyntax
                val id = lastGroundTelegraphIds.remove(player.uuid)
                if (id == null) {
                    player.sendMessage(Component.text("No ground telegraph is active"))
                } else {
                    player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(GroundTelegraphRemove(id)))
                    player.sendMessage(Component.text("Ground telegraph cleared"))
                }
            }, aoeClearLiteral)
        },
    )
    val vfxTypeArgument = ArgumentType.Word("type")
    val vfxImageArgument = ArgumentType.Word("bundledDemoId")
    val vfxLengthArgument = ArgumentType.Double("length")
    val vfxDensityArgument = ArgumentType.Double("density")
    val vfxRadiusArgument = ArgumentType.Double("radius")
    val vfxStartDegreesArgument = ArgumentType.Double("startDeg")
    val vfxEndDegreesArgument = ArgumentType.Double("endDeg")
    val vfxDurationArgument = ArgumentType.Double("duration")
    val vfxPetalsArgument = ArgumentType.Double("petals")
    val vfxLineLiteral = ArgumentType.Literal("line")
    val vfxCircleLiteral = ArgumentType.Literal("circle")
    val vfxArcLiteral = ArgumentType.Literal("arc")
    val vfxSlashLiteral = ArgumentType.Literal("slash")
    val vfxFlowerLiteral = ArgumentType.Literal("flower")
    val vfxV2TypeArgument = ArgumentType.Word("diagnostic")
    val vfxV2Types = setOf("anchor", "easing", "timeline", "trail", "ribbon", "lifecycle")
    val vfxImageLiteral = ArgumentType.Literal("image")
    fun handleVfxDemo(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val type = context.get<String>(vfxTypeArgument).lowercase()
        if (type !in setOf("line", "circle", "arc", "bezier", "spiral", "lightning", "explosion", "slash", "cleave", "sphere", "dome", "flower", "prism", "all", "evolved")) {
            player.sendMessage(Component.text("Use /vfxdemo line|circle|arc|bezier|spiral|lightning|explosion|slash|cleave|sphere|dome|flower|prism|all|evolved"))
            return
        }
        startParticleDemo(player, type, particleAnimations, manager = particleManager)
    }
    fun handleVfxImageDemo(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val bundledDemoId = context.get<String>(vfxImageArgument).lowercase()
        if (bundledDemoId !in setOf("demo", "default")) {
            player.sendMessage(Component.text("Use /vfxdemo image demo"))
            return
        }
        startParticleDemo(player, "image", particleAnimations, manager = particleManager)
    }
    fun handleVfxParameterized(type: String, sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val values = when (type) {
            "line" -> listOf(context.get<Double>(vfxLengthArgument), context.get<Double>(vfxDensityArgument))
            "circle" -> listOf(context.get<Double>(vfxRadiusArgument))
            "arc" -> listOf(context.get<Double>(vfxRadiusArgument), context.get<Double>(vfxStartDegreesArgument), context.get<Double>(vfxEndDegreesArgument))
            "slash" -> listOf(context.get<Double>(vfxLengthArgument), context.get<Double>(vfxDurationArgument))
            "flower" -> listOf(context.get<Double>(vfxPetalsArgument), context.get<Double>(vfxRadiusArgument))
            else -> emptyList()
        }
        startParticleDemo(player, type, particleAnimations, values, particleManager)
    }
    MinecraftServer.getCommandManager().register(
        Command("vfxstats").apply {
            setDefaultExecutor { sender, _ ->
                val counters = particleManager.counters
                val profile = particleProfiler.snapshot()
                sender.sendMessage(Component.text("VFX active=${profile.activeEffects} animations=${profile.currentAnimations} requested=${profile.particlesRequested} sent=${profile.particlesSent} degraded=${profile.particlesDegraded} attempted=${counters.attempted} dropped=${counters.dropped} categories=${profile.byCategory} viewers=${profile.byViewer} top=${profile.topEffectIds}"))
            }
            addSyntax({ sender, _ -> particleProfiler.reset(); particleManager.resetCounters(); sender.sendMessage(Component.text("VFX stats reset")) }, ArgumentType.Literal("reset"))
        },
    )
    MinecraftServer.getCommandManager().register(
        Command("vfxedit").apply {
            addSyntax(
                { sender, _ -> (sender as? net.minestom.server.entity.Player)?.let(::openSlashEditor) },
                ArgumentType.Literal("slash"),
            )
        },
    )
    MinecraftServer.getCommandManager().register(
        Command("vfxeditor2").apply {
            setDefaultExecutor { sender, _ -> (sender as? net.minestom.server.entity.Player)?.let(vfxEditor2::open) }
        },
    )
    MinecraftServer.getCommandManager().register(
        Command("vfxdemo").apply {
            addSyntax(::handleVfxDemo, vfxTypeArgument)
            addSyntax({ sender, _ -> (sender as? net.minestom.server.entity.Player)?.let { particleAnimations.pauseFor(it) } }, ArgumentType.Literal("evolved"), ArgumentType.Literal("pause"))
            addSyntax({ sender, _ -> (sender as? net.minestom.server.entity.Player)?.let { particleAnimations.resumeFor(it) } }, ArgumentType.Literal("evolved"), ArgumentType.Literal("resume"))
            addSyntax({ sender, _ -> (sender as? net.minestom.server.entity.Player)?.let { particleAnimations.cancelFor(it) } }, ArgumentType.Literal("evolved"), ArgumentType.Literal("cancel"))
            addSyntax(::handleVfxImageDemo, vfxImageLiteral, vfxImageArgument)
            addSyntax({ sender, context -> handleVfxParameterized("line", sender, context) }, vfxLineLiteral, vfxLengthArgument, vfxDensityArgument)
            addSyntax({ sender, context -> handleVfxParameterized("circle", sender, context) }, vfxCircleLiteral, vfxRadiusArgument)
            addSyntax({ sender, context -> handleVfxParameterized("arc", sender, context) }, vfxArcLiteral, vfxRadiusArgument, vfxStartDegreesArgument, vfxEndDegreesArgument)
            addSyntax({ sender, context -> handleVfxParameterized("slash", sender, context) }, vfxSlashLiteral, vfxLengthArgument, vfxDurationArgument)
            addSyntax({ sender, context -> handleVfxParameterized("flower", sender, context) }, vfxFlowerLiteral, vfxPetalsArgument, vfxRadiusArgument)
        },
    )
    fun handleVfxV2(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val type = context.get<String>(vfxV2TypeArgument).lowercase()
        if (type !in vfxV2Types) {
            player.sendMessage(Component.text("Use /vfxv2 anchor|easing|timeline|trail|ribbon|lifecycle"))
            return
        }
        startParticleV2Diagnostic(player, type, particleAnimations, particleManager)
    }
    MinecraftServer.getCommandManager().register(
        Command("vfxv2").apply {
            addSyntax(::handleVfxV2, vfxV2TypeArgument)
            addSyntax({ sender, _ -> (sender as? net.minestom.server.entity.Player)?.let { particleAnimations.pauseFor(it) } }, ArgumentType.Literal("pause"))
            addSyntax({ sender, _ -> (sender as? net.minestom.server.entity.Player)?.let { particleAnimations.resumeFor(it) } }, ArgumentType.Literal("resume"))
            addSyntax({ sender, _ -> (sender as? net.minestom.server.entity.Player)?.let { particleAnimations.cancelFor(it) } }, ArgumentType.Literal("cancel"))
        },
    )
    val presetIdArgument = ArgumentType.Word("id").setSuggestionCallback { _, _, suggestion ->
        ParticlePresetRegistry.all.forEach { suggestion.addEntry(SuggestionEntry(it.id)) }
    }
    val presetTagArgument = ArgumentType.Word("tag").setSuggestionCallback { _, _, suggestion ->
        ParticlePresetRegistry.all.flatMap { it.tags }.toSet().sorted().forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }
    val presetValuesArgument = ArgumentType.StringArray("parameters").setSuggestionCallback { _, context, suggestion ->
        val id = runCatching { context.get<String>(presetIdArgument) }.getOrNull()
        id?.let { ParticlePresetRegistry[it] }?.parameters?.forEach { parameter ->
            suggestion.addEntry(SuggestionEntry("${parameter.name}="))
        }
    }
    fun handlePresetList(sender: CommandSender, context: CommandContext) {
        val tag = if (context.has(presetTagArgument)) context.get<String>(presetTagArgument) else null
        val presets = ParticlePresetRegistry.list(tag)
        if (presets.isEmpty()) {
            sender.sendMessage(Component.text(if (tag == null) "No VFX presets registered" else "No VFX presets for tag $tag"))
            return
        }
        sender.sendMessage(Component.text("VFX presets (${presets.size})"))
        presets.forEach { preset ->
            val schema = if (preset.parameters.isEmpty()) "" else " params=${preset.parameters.joinToString(",") { it.name }}"
            sender.sendMessage(Component.text("${preset.id} - ${preset.displayName} [${preset.tags.joinToString(",")}]$schema"))
        }
    }
    fun handlePresetPlay(sender: CommandSender, context: CommandContext) {
        val player = sender as? net.minestom.server.entity.Player ?: return
        val id = context.get<String>(presetIdArgument).lowercase()
        val preset = ParticlePresetRegistry[id]
        if (preset == null) {
            player.sendMessage(Component.text("Unknown VFX preset: $id. Use /vfxpreset list"))
            return
        }
        val rawValues = if (context.has(presetValuesArgument)) context.get<Array<String>>(presetValuesArgument) else emptyArray()
        val parsed = parseParticlePresetOverrides(preset, rawValues)
        if (parsed.error != null) {
            player.sendMessage(Component.text(parsed.error!!))
            return
        }
        val direction = player.position.direction()
        val origin = player.position.add(direction.x() * 3.0, 1.2, direction.z() * 3.0)
        startParticlePreset(player, id, particleAnimations, origin, direction, particleManager, parsed.values)
        player.sendMessage(Component.text("Playing ${preset.displayName} ($id)"))
    }
    MinecraftServer.getCommandManager().register(
        Command("vfxpreset").apply {
            setDefaultExecutor { sender, _ -> handlePresetList(sender, CommandContext("vfxpreset")) }
            addSyntax(::handlePresetList, ArgumentType.Literal("list"))
            addSyntax(::handlePresetList, ArgumentType.Literal("list"), presetTagArgument)
            addSyntax(::handlePresetPlay, ArgumentType.Literal("play"), presetIdArgument)
            addSyntax(::handlePresetPlay, ArgumentType.Literal("play"), presetIdArgument, presetValuesArgument)
        },
    )

    fun starweaverProjectileTargets(caster: net.minestom.server.entity.Player): List<StarweaverProjectileTarget> = buildList {
        dummy?.takeIf { it.instance == caster.instance && !it.isRemoved }?.let {
            add(StarweaverProjectileTarget(combatTarget(it), isAlly = false))
        }
        instance.players.filter { it !== caster && it.isOnline }.forEach { ally ->
            add(StarweaverProjectileTarget(combatTarget(ally), isAlly = true))
        }
    }

    fun starweaverAreaTargets(caster: net.minestom.server.entity.Player): List<StarweaverProjectileTarget> = buildList {
        dummy?.takeIf { it.instance == caster.instance && !it.isRemoved }?.let {
            add(StarweaverProjectileTarget(combatTarget(it), isAlly = false))
        }
        instance.players.filter { it.isOnline }.forEach { ally ->
            add(StarweaverProjectileTarget(combatTarget(ally), isAlly = true))
        }
    }

    fun applyStarweaverDirectDamage(
        caster: net.minestom.server.entity.Player,
        state: StarweaverRuntimeState,
        cast: StarweaverCast,
        targetId: UUID,
        damage: Int,
    ): Int {
        val target = dummy?.takeIf { it.uuid == targetId && !it.isRemoved } ?: return 0
        val applied = prototypeBoss.applyStarweaverDamage(cast.castId, target.uuid, damage)
        if (applied <= 0) return 0
        updateBossBar()
        val enemyIds = listOf(target.uuid)
        state.effects.moonlitPropagation(cast.castId, target.uuid, applied, enemyIds).forEach { (transferId, transferDamage) ->
            val transferred = prototypeBoss.applyStarweaverDamage(cast.castId, transferId, transferDamage)
            if (transferred > 0) updateBossBar()
        }
        return applied
    }

    fun applyStarweaverPeriodicDamage(state: StarweaverRuntimeState, effect: StarweaverPeriodicEffect) {
        val target = dummy?.takeIf { it.uuid == effect.targetId && !it.isRemoved } ?: return
        val applied = prototypeBoss.applyStarweaverDamage(state.nextExecutionId(), target.uuid, effect.damage)
        if (applied > 0) updateBossBar()
    }

    fun healStarweaverPlayer(targetId: UUID, amount: Int) {
        val target = instance.players.firstOrNull { it.uuid == targetId } ?: return
        target.setHealth((target.getHealth() + amount).coerceAtMost(prototypeBoss.playerMaxHealth.toFloat()))
    }

    fun updateStarweaverShields() {
        instance.players.forEach { target ->
            val shield = starweaverStates.values.mapNotNull { it.effects.shield(target.uuid)?.amount }.maxOrNull() ?: 0
            target.setAdditionalHearts(shield.toFloat())
        }
    }

    fun activateStarweaverZone(
        caster: net.minestom.server.entity.Player,
        state: StarweaverRuntimeState,
        zone: StarweaverPendingZone,
    ) {
        val cast = zone.cast
        val radius = when {
            cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.MOON -> StarweaverBalance.LUNAR_W_RADIUS
            cast.slot == StarweaverSlot.W -> StarweaverBalance.W_RADIUS
            else -> StarweaverBalance.E_RADIUS
        }
        val targets = starweaverAreaTargets(caster).filter {
            isWithinStarweaverAabbRadius(zone.center, radius, it.target)
        }
        targets.forEach { target ->
            val targetId = target.target.id
            val isEnemy = !target.isAlly
            when (cast.slot) {
                StarweaverSlot.W -> when {
                    cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.MOON -> {
                        if (isEnemy) {
                            applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.LUNAR_W_DAMAGE)
                            state.effects.applyMoonlit(targetId)
                            if (targetId != dummy?.uuid) {
                                state.effects.applyStun(targetId, StarweaverBalance.LUNAR_W_STUN_TICKS)
                            }
                        }
                    }
                    cast.celestial == StarweaverCelestial.SUN -> if (isEnemy) {
                        applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.SUN_W_DAMAGE)
                        state.effects.applySolarBurn(targetId)
                    }
                    cast.celestial == StarweaverCelestial.MOON -> if (isEnemy) {
                        applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.MOON_W_DAMAGE)
                        if (targetId != dummy?.uuid) state.effects.applyStun(targetId, StarweaverBalance.MOON_W_STUN_TICKS)
                    }
                    target.isAlly -> {
                        state.effects.applyShield(targetId, StarweaverBalance.STAR_W_SHIELD)
                    }
                    else -> {
                        applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.STAR_W_DAMAGE)
                    }
                }
                StarweaverSlot.E -> when {
                    cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.STAR -> {
                        if (isEnemy) {
                            applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.STELLAR_E_DAMAGE)
                        } else {
                            healStarweaverPlayer(targetId, StarweaverBalance.STELLAR_E_HEAL)
                        }
                    }
                    cast.celestial == StarweaverCelestial.SUN -> if (isEnemy) {
                        applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.SUN_E_DAMAGE)
                        state.effects.applySolarBurn(targetId)
                    }
                    cast.celestial == StarweaverCelestial.MOON -> if (isEnemy) {
                        applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.MOON_E_DAMAGE)
                        if (targetId != dummy?.uuid) state.effects.applyStun(targetId, StarweaverBalance.MOON_E_STUN_TICKS)
                    }
                    target.isAlly -> healStarweaverPlayer(targetId, StarweaverBalance.STAR_E_HEAL)
                    else -> applyStarweaverDirectDamage(caster, state, cast, targetId, StarweaverBalance.STAR_E_DAMAGE)
                }
                StarweaverSlot.Q -> Unit
            }
        }

        if (cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.STAR) {
            state.addField(cast.castId, zone.center)
            showStarweaverPreset(
                caster,
                starweaverZonePresetId(cast),
                zone.center,
                Vec(0.0, 1.0, 0.0),
                particleAnimations,
                particleManager,
                durationTicks = 20,
            )
        } else {
            showStarweaverPreset(
                caster,
                starweaverZonePresetId(cast),
                zone.center,
                Vec(0.0, 1.0, 0.0),
                particleAnimations,
                particleManager,
                durationTicks = 5,
            )
        }
    }

    fun tickStarweaverPlayer(
        player: net.minestom.server.entity.Player,
        combatState: CombatState,
        dodge: DodgeState,
        state: StarweaverRuntimeState,
    ) {
        if (!prototypeBoss.isEncounterRunning) return

        val runtimeTick = state.tick()
        runtimeTick.periodicEffects.forEach { applyStarweaverPeriodicDamage(state, it) }
        val targets = starweaverProjectileTargets(player)
        state.projectiles().forEach { projectile ->
            val result = projectile.tick(targets) { start, end ->
                isStarweaverBlockCollision(player.instance, start, end)
            }
            result.hitTargetIds.forEach { targetId ->
                val target = targets.firstOrNull { it.target.id == targetId } ?: return@forEach
                val cast = projectile.cast
                if (target.isAlly) {
                    if (cast.celestial == StarweaverCelestial.STAR) {
                        healStarweaverPlayer(targetId, StarweaverBalance.STAR_Q_HEAL)
                    }
                    return@forEach
                }
                when {
                    cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.SUN -> {
                        applyStarweaverDirectDamage(player, state, cast, targetId, StarweaverBalance.SOLAR_Q_DAMAGE)
                        state.effects.applySolarQDot(targetId, prototypeBoss.maxHealth)
                    }
                    cast.celestial == StarweaverCelestial.SUN -> {
                        applyStarweaverDirectDamage(player, state, cast, targetId, StarweaverBalance.SUN_Q_DAMAGE)
                    }
                    cast.celestial == StarweaverCelestial.MOON -> {
                        applyStarweaverDirectDamage(player, state, cast, targetId, StarweaverBalance.MOON_Q_DAMAGE)
                        val bossTarget = targetId == dummy?.uuid
                        state.effects.applySlow(
                            targetId,
                            if (bossTarget) StarweaverBalance.MOON_Q_BOSS_SLOW_MULTIPLIER else StarweaverBalance.MOON_Q_SLOW_MULTIPLIER,
                            if (bossTarget) StarweaverBalance.MOON_Q_BOSS_SLOW_TICKS else StarweaverBalance.MOON_Q_SLOW_TICKS,
                        )
                    }
                    cast.celestial == StarweaverCelestial.STAR -> {
                        val damage = applyStarweaverDirectDamage(player, state, cast, targetId, StarweaverBalance.STAR_Q_DAMAGE)
                        if (damage > 0 && state.markSelfHealIfFirst(cast.castId)) {
                            healStarweaverPlayer(player.uuid, StarweaverBalance.STAR_Q_SELF_HEAL)
                        }
                    }
                }
                showStarweaverPreset(
                    player,
                    starweaverImpactPresetId(cast),
                    projectile.position,
                    Vec(0.0, 1.0, 0.0),
                    particleAnimations,
                    particleManager,
                    durationTicks = 4,
                )
            }
            if (result.active || result.hitTargetIds.isNotEmpty()) {
                val direction = Vec(
                    result.position.x() - projectile.previousPosition.x(),
                    result.position.y() - projectile.previousPosition.y(),
                    result.position.z() - projectile.previousPosition.z(),
                )
                showStarweaverPreset(
                    player,
                    starweaverProjectilePresetId(projectile.cast),
                    result.position,
                    direction,
                    particleAnimations,
                    particleManager,
                    durationTicks = 2,
                )
            }
        }
        state.removeInactiveProjectiles()

        runtimeTick.activatedZones.forEach { activateStarweaverZone(player, state, it) }
        runtimeTick.fieldPulses.forEach { pulse ->
            starweaverAreaTargets(player)
                .filter { isWithinStarweaverAabbRadius(pulse.center, pulse.radius, it.target) }
                .forEach { target ->
                    if (target.isAlly) {
                        healStarweaverPlayer(target.target.id, StarweaverBalance.STELLAR_E_HEAL)
                    } else {
                        applyStarweaverPeriodicDamage(
                            state,
                            StarweaverPeriodicEffect(
                                targetId = target.target.id,
                                damage = StarweaverBalance.STELLAR_FIELD_DAMAGE,
                                source = StarweaverPeriodicSource.STELLAR_FIELD,
                            ),
                        )
                    }
                }
            showStarweaverPreset(
                player,
                "projects:class/starweaver/e_stellar",
                pulse.center,
                Vec(0.0, 1.0, 0.0),
                particleAnimations,
                particleManager,
                durationTicks = 10,
            )
        }

        applyStarweaverMovementSpeed(player, state.rotation.movementSpeedBonus)
        updateStarweaverShields()
        sendStarweaverHudSnapshot(player)

        val velocityWasApplied = dodgeVelocityActive[player.uuid] == true
        val movement = dodge.tick(
            canStart = !combatState.isAttacking,
            facing = player.position.direction(),
            startAllowed = { canStartDodge(player.isOnGround, weaponFor(player)) },
        )
        if (movement != null) {
            moveDodge(player, dodge, movement)
            dodgeVelocityActive[player.uuid] = true
        } else if (velocityWasApplied) {
            stopDodgeVelocity(player)
            dodgeVelocityActive[player.uuid] = false
        }
        if (!prototypeBoss.isEncounterRunning) finishEncounter()
    }

    fun handleStarweaverSkillInput(player: net.minestom.server.entity.Player, slot: ClassSkillSlot) {
        val state = starweaverStates[player.uuid] ?: return
        when (slot) {
            ClassSkillSlot.ULTIMATE -> {
                if (state.rotation.trySwap()) {
                    showStarweaverPreset(
                        player,
                        "projects:class/starweaver/r_swap",
                        player.position.add(0.0, 1.0, 0.0),
                        Vec(0.0, 1.0, 0.0),
                        particleAnimations,
                        particleManager,
                        durationTicks = 6,
                    )
                }
            }
            ClassSkillSlot.SKILL_1,
            ClassSkillSlot.SKILL_2,
            ClassSkillSlot.SKILL_3 -> {
                val starweaverSlot = when (slot) {
                    ClassSkillSlot.SKILL_1 -> StarweaverSlot.Q
                    ClassSkillSlot.SKILL_2 -> StarweaverSlot.W
                    ClassSkillSlot.SKILL_3 -> StarweaverSlot.E
                    ClassSkillSlot.ULTIMATE -> error("Handled above")
                }
                val groundTarget = if (starweaverSlot == StarweaverSlot.W || starweaverSlot == StarweaverSlot.E) {
                    resolveStarweaverGroundTarget(
                        player.instance,
                        player.position.add(0.0, player.eyeHeight, 0.0),
                        player.position.direction(),
                        if (starweaverSlot == StarweaverSlot.W) StarweaverBalance.W_RANGE else StarweaverBalance.E_RANGE,
                    ) ?: run {
                        player.sendMessage(Component.text("Starweaver target is out of range or has no surface"))
                        return
                    }
                } else {
                    null
                }
                val cast = state.rotation.tryCast(starweaverSlot) ?: return
                if (starweaverSlot == StarweaverSlot.Q) {
                    val origin = player.position.add(0.0, player.eyeHeight, 0.0)
                    state.addProjectile(
                        if (cast.kind == StarweaverCastKind.CONJUNCTION && cast.celestial == StarweaverCelestial.SUN) {
                            StarweaverProjectileState.solar(cast, origin, player.position.direction())
                        } else {
                            StarweaverProjectileState.normal(cast, origin, player.position.direction())
                        },
                    )
                    showStarweaverPreset(
                        player,
                        starweaverProjectilePresetId(cast),
                        origin,
                        player.position.direction(),
                        particleAnimations,
                        particleManager,
                        durationTicks = 5,
                    )
                } else {
                    val center = requireNotNull(groundTarget)
                    val delay = when {
                        starweaverSlot == StarweaverSlot.W && cast.celestial == StarweaverCelestial.STAR -> StarweaverBalance.STAR_W_DELAY_TICKS
                        starweaverSlot == StarweaverSlot.E && cast.celestial == StarweaverCelestial.STAR -> StarweaverBalance.STAR_E_DELAY_TICKS
                        starweaverSlot == StarweaverSlot.W -> StarweaverBalance.W_DELAY_TICKS
                        else -> StarweaverBalance.E_DELAY_TICKS
                    }
                    state.addPendingZone(cast, center, delay)
                    showStarweaverPreset(
                        player,
                        starweaverZonePresetId(cast),
                        center,
                        Vec(0.0, 1.0, 0.0),
                        particleAnimations,
                        particleManager,
                        durationTicks = delay,
                    )
                }
            }
        }
        sendStarweaverHudSnapshot(player)
    }

    fun startRoninParticleEffect(
        source: net.minestom.server.entity.Player,
        id: String,
        anchors: List<ParticleAnchor> = emptyList(),
        effectFactory: () -> ParticleEffect,
    ) {
        val sourcePosition = source.position
        instance.players
            .filter { player ->
                player.instance == source.instance &&
                    (player.position.x() - sourcePosition.x()) * (player.position.x() - sourcePosition.x()) +
                    (player.position.y() - sourcePosition.y()) * (player.position.y() - sourcePosition.y()) +
                    (player.position.z() - sourcePosition.z()) * (player.position.z() - sourcePosition.z()) <= 64.0 * 64.0
            }
            .forEach { viewer ->
                val sink = particleManager.sink(
                    ParticleViewer(viewer.position, viewer),
                    PlayerParticleSink(viewer),
                    "ronin:$id",
                )
                particleAnimations.start(
                    effect = effectFactory(),
                    sink = sink,
                    id = "ronin:$id:${source.uuid}:${viewer.uuid}",
                    anchors = anchors,
                )
            }
    }

    fun emitRoninSlash(
        source: net.minestom.server.entity.Player,
        effect: RoninSlashEffect,
        origin: Point,
        direction: Vec,
        variant: Int = 0,
        seed: Long = 0L,
    ) {
        if (effect == RoninSlashEffect.Q && vfxEditor2.playRoninQ(source, origin, direction, seed)) return
        val visualSeed = seed xor source.uuid.mostSignificantBits xor source.uuid.leastSignificantBits xor
            origin.x().toBits() xor origin.y().toBits() xor origin.z().toBits()
        startRoninParticleEffect(
            source = source,
            id = effect.name.lowercase(Locale.ROOT),
        ) {
            RoninSlashEffects.create(effect, origin, direction, variant = variant, seed = visualSeed)
        }
    }

    fun startRoninBlinkTrail(source: net.minestom.server.entity.Player) {
        val anchor = ParticleAnchor.player(source, ParticleAnchorPoint.CENTER)
        startRoninParticleEffect(
            source = source,
            id = "blink-trail",
            anchors = listOf(anchor),
        ) {
            RoninSlashEffects.blinkTrail(anchor)
        }
    }

    fun roninEnemyTargets(caster: net.minestom.server.entity.Player): List<CombatTarget> = buildList {
        dummy?.takeIf { it.instance == caster.instance && !it.isRemoved }?.let { add(combatTarget(it)) }
    }

    fun healRoninPlayer(player: net.minestom.server.entity.Player, amount: Double) {
        if (amount <= 0.0) return
        player.setHealth((player.getHealth() + amount.toFloat()).coerceAtMost(prototypeBoss.playerMaxHealth.toFloat()))
    }

    fun applyRoninDirectDamage(
        player: net.minestom.server.entity.Player,
        state: RoninState,
        target: CombatTarget,
        baseDamage: Double,
        executionId: Long,
        consumeWound: Boolean = true,
        recordW3Healing: Boolean = false,
    ): Int {
        val damage = baseDamage * state.roninDamageMultiplier(target.id)
        val applied = prototypeBoss.applyRoninDamage(executionId, target.id, damage)
        if (applied <= 0) return 0
        updateBossBar()
        if (consumeWound && state.consumeWound(executionId, target.id)) {
            healRoninPlayer(player, prototypeBoss.playerMaxHealth * RoninBalance.WOUND_HEAL_RATIO)
            emitRoninSlash(
                player,
                RoninSlashEffect.WOUND_CONSUME,
                target.position.add(0.0, 1.0, 0.0),
                player.position.direction(),
                seed = executionId,
            )
        }
        if (recordW3Healing) {
            healRoninPlayer(player, state.recordW3Healing(applied, prototypeBoss.playerMaxHealth))
        }
        return applied
    }

    fun roninHorizontalDirection(direction: Vec): Vec {
        val length = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
        return if (length > 1.0e-9) Vec(direction.x() / length, 0.0, direction.z() / length) else Vec(0.0, 0.0, 1.0)
    }

    fun selectRoninFrontTarget(
        origin: Point,
        direction: Vec,
        targets: List<CombatTarget>,
        range: Double,
    ): CombatTarget? {
        val horizontal = roninHorizontalDirection(direction)
        val right = Vec(-horizontal.z(), 0.0, horizontal.x())
        return targets
            .filter { isRoninFrontVolumeHit(origin, direction, it, range, 2.6, 2.5) }
            .minWithOrNull(
                compareBy<CombatTarget> {
                    val lateral = (it.position.x() - origin.x()) * right.x() +
                        (it.position.z() - origin.z()) * right.z()
                    abs(lateral) * 5.0
                }.thenBy { roninForwardProjection(origin, direction, it) },
            )
    }

    fun showRoninCastStartVfx(player: net.minestom.server.entity.Player, cast: RoninCast) {
        when (cast.skill to cast.variant) {
            RoninSkill.Q to RoninWVariant.NONE -> Unit
            RoninSkill.W to RoninWVariant.WOUND -> Unit
            RoninSkill.W to RoninWVariant.CROSSCUT -> Unit
            RoninSkill.W to RoninWVariant.TEMPEST -> emitRoninSlash(
                player,
                RoninSlashEffect.TEMPEST_SEQUENCE,
                cast.origin.add(0.0, 1.0, 0.0),
                cast.direction,
                seed = cast.castId,
            )
            RoninSkill.E to RoninWVariant.NONE -> startRoninBlinkTrail(player)
            RoninSkill.R to RoninWVariant.NONE -> emitRoninSlash(
                player,
                RoninSlashEffect.R_SHEATH,
                cast.origin.add(0.0, 1.0, 0.0),
                cast.direction,
            )
            else -> Unit
        }
    }

    fun tickRoninPlayer(
        player: net.minestom.server.entity.Player,
        combatState: CombatState,
        dodge: DodgeState,
        state: RoninState,
    ) {
        if (!prototypeBoss.isEncounterRunning) return
        if (player.getHealth() <= 0.0f) {
            resetRoninPlayerState(player)
            return
        }

        val wasLocked = state.isMovementLocked
        val tick = state.tick()
        val cast = state.currentCast ?: tick.completedCast
        tick.events.forEach { event ->
            val activeCast = state.currentCast ?: cast ?: return@forEach
            val targets = roninEnemyTargets(player)
            when (event.kind) {
                RoninCastEventKind.Q_IMPACT -> {
                    var hit = false
                    targets.filter {
                        isRoninFrontVolumeHit(
                            activeCast.origin,
                            activeCast.direction,
                            it,
                            RoninBalance.Q_RANGE,
                            RoninBalance.Q_WIDTH,
                            RoninBalance.Q_VERTICAL_TOLERANCE,
                        )
                    }.forEach { target ->
                        if (applyRoninDirectDamage(player, state, target, RoninBalance.Q_DAMAGE.toDouble(), activeCast.castId) > 0) {
                            hit = true
                        }
                    }
                    if (hit) state.recordEnemyHit()
                    emitRoninSlash(
                        player,
                        RoninSlashEffect.Q,
                        activeCast.origin.add(
                            activeCast.direction.x() * 2.7,
                            1.05,
                            activeCast.direction.z() * 2.7,
                        ),
                        activeCast.direction,
                        seed = activeCast.castId,
                    )
                }
                RoninCastEventKind.W_INITIAL -> when (activeCast.variant) {
                    RoninWVariant.WOUND -> {
                        targets.filter {
                            isRoninFrontVolumeHit(
                                activeCast.origin,
                                activeCast.direction,
                                it,
                                RoninBalance.W1_RANGE,
                                RoninBalance.W1_WIDTH,
                                RoninBalance.W1_VERTICAL_TOLERANCE,
                            )
                        }.forEach { target ->
                            val applied = applyRoninDirectDamage(
                                player,
                                state,
                                target,
                                RoninBalance.W1_DAMAGE.toDouble(),
                                activeCast.castId * 10L + 1L,
                                consumeWound = false,
                            )
                            if (applied > 0) {
                                state.applyWound(target.id)
                                emitRoninSlash(
                                    player,
                                    RoninSlashEffect.WOUND_MARK,
                                    target.position.add(0.0, 1.0, 0.0),
                                    activeCast.direction,
                                    seed = activeCast.castId * 10L + 1L,
                                )
                            }
                        }
                    }
                    RoninWVariant.CROSSCUT -> {
                        emitRoninSlash(
                            player,
                            RoninSlashEffect.CROSSCUT,
                            activeCast.origin.add(activeCast.direction.x() * 3.0, 1.0, activeCast.direction.z() * 3.0),
                            activeCast.direction,
                            variant = 1,
                            seed = activeCast.castId * 10L + 1L,
                        )
                        emitRoninSlash(
                            player,
                            RoninSlashEffect.CROSSCUT,
                            activeCast.origin.add(activeCast.direction.x() * 3.0, 1.0, activeCast.direction.z() * 3.0),
                            activeCast.direction,
                            variant = -1,
                            seed = activeCast.castId * 10L + 2L,
                        )
                        selectRoninFrontTarget(activeCast.origin, activeCast.direction, targets, RoninBalance.W2_RANGE)?.let { target ->
                            if (applyRoninDirectDamage(
                                    player,
                                    state,
                                    target,
                                    RoninBalance.W2_INITIAL_DAMAGE.toDouble(),
                                    activeCast.castId * 10L + 1L,
                                ) > 0
                            ) {
                                state.lockDelayedTarget(target.id)
                            }
                        }
                    }
                    else -> Unit
                }
                RoninCastEventKind.W_DELAYED -> {
                    val target = event.targetId?.let { id -> targets.firstOrNull { it.id == id } }
                    if (target != null) {
                        emitRoninSlash(
                            player,
                            RoninSlashEffect.CROSSCUT_FLASH,
                            target.position.add(0.0, 1.0, 0.0),
                            activeCast.direction,
                            seed = activeCast.castId * 10L + 2L,
                        )
                        if (applyRoninDirectDamage(
                                player,
                                state,
                                target,
                                RoninBalance.W2_DELAYED_DAMAGE.toDouble(),
                                activeCast.castId * 10L + 2L,
                            ) > 0
                        ) {
                            state.applySevered(target.id)
                        }
                    }
                }
                RoninCastEventKind.W3_PULSE -> {
                    targets.filter { isRoninRadialHit(activeCast.origin.add(0.0, 1.0, 0.0), RoninBalance.W3_RADIUS, it) }
                        .forEach { target ->
                            applyRoninDirectDamage(
                                player,
                                state,
                                target,
                                RoninBalance.W3_PULSE_DAMAGE.toDouble(),
                                activeCast.castId * 10L + event.pulseIndex,
                                recordW3Healing = true,
                            )
                        }
                }
                RoninCastEventKind.W3_FINAL -> {
                    targets.filter { isRoninRadialHit(activeCast.origin.add(0.0, 1.0, 0.0), RoninBalance.W3_RADIUS, it) }
                        .forEach { target ->
                            applyRoninDirectDamage(
                                player,
                                state,
                                target,
                                RoninBalance.W3_FINAL_DAMAGE.toDouble(),
                                activeCast.castId * 10L + 4L,
                                recordW3Healing = true,
                            )
                        }
                    emitRoninSlash(
                        player,
                        RoninSlashEffect.TEMPEST_FINAL,
                        activeCast.origin.add(0.0, 1.0, 0.0),
                        activeCast.direction,
                        seed = activeCast.castId,
                    )
                }
                RoninCastEventKind.E_BLINK -> {
                    val start = Pos(activeCast.origin.x(), activeCast.origin.y(), activeCast.origin.z())
                    val direction = roninHorizontalDirection(activeCast.direction)
                    val end = resolveRoninBlinkEnd(player.instance, start, direction, RoninBalance.E_RANGE)
                    player.teleport(end.withDirection(player.position.direction()))
                    roninLockPositions[player.uuid] = end
                    val hitTargets = targets.filter { roninSegmentIntersectsAabb(start, end, it) }
                    var hit = false
                    hitTargets.forEach { target ->
                        if (applyRoninDirectDamage(
                                player,
                                state,
                                target,
                                RoninBalance.E_DAMAGE.toDouble(),
                                activeCast.castId * 10L + 1L,
                            ) > 0
                        ) {
                            hit = true
                            emitRoninSlash(
                                player,
                                RoninSlashEffect.BLINK_HIT,
                                target.position.add(0.0, 1.0, 0.0),
                                activeCast.direction,
                                seed = activeCast.castId * 10L + 1L,
                            )
                        }
                    }
                    if (hit) state.recordEnemyHit()
                    emitRoninSlash(
                        player,
                        RoninSlashEffect.BLINK_TRAIL,
                        end.add(0.0, 1.0, 0.0),
                        activeCast.direction,
                        variant = 1,
                        seed = activeCast.castId,
                    )
                }
                RoninCastEventKind.R_IMPACT -> {
                    var hit = false
                    var sweetHit = false
                    targets.filter {
                        isRoninSectorHit(activeCast.origin, activeCast.direction, it, RoninBalance.R_RANGE, RoninBalance.R_HALF_ANGLE_DEGREES)
                    }.forEach { target ->
                        val sweet = isRoninSectorHit(
                            activeCast.origin,
                            activeCast.direction,
                            target,
                            RoninBalance.R_RANGE,
                            RoninBalance.R_SWEET_HALF_ANGLE_DEGREES,
                        )
                        val missing = if (target.id == dummy?.uuid) {
                            (1.0 - prototypeBoss.currentHealth.toDouble() / prototypeBoss.maxHealth).coerceIn(0.0, 1.0)
                        } else {
                            0.0
                        }
                        if (applyRoninDirectDamage(
                                player,
                                state,
                                target,
                                RoninBalance.R_DAMAGE * if (sweet) 1.0 + missing else 1.0,
                                activeCast.castId,
                            ) > 0
                        ) {
                            hit = true
                            sweetHit = sweetHit || sweet
                        }
                    }
                    if (hit) state.recordEnemyHit()
                    emitRoninSlash(
                        player,
                        if (sweetHit) RoninSlashEffect.R_SWEET_DRAW else RoninSlashEffect.R_DRAW,
                        activeCast.origin.add(activeCast.direction.x() * 3.4, 1.1, activeCast.direction.z() * 3.4),
                        activeCast.direction,
                        seed = activeCast.castId,
                    )
                }
            }
        }

        if (state.isMovementLocked) {
            val lockPosition = roninLockPositions[player.uuid] ?: Pos(
                cast?.origin?.x() ?: player.position.x(),
                cast?.origin?.y() ?: player.position.y(),
                cast?.origin?.z() ?: player.position.z(),
            ).also { roninLockPositions[player.uuid] = it }
            player.teleport(lockPosition.withDirection(player.position.direction()))
            player.setVelocity(Vec.ZERO)
            sendRoninHudSnapshot(player)
            return
        }
        roninLockPositions.remove(player.uuid)
        if (wasLocked) player.setVelocity(Vec.ZERO)

        if (!prototypeBoss.isEncounterRunning) return
        if (dodge.hasPending) combatState.deferAttackRestart()
        val targets = roninEnemyTargets(player)
        val combatEvents = combatState.tick(player.position, player.position.direction(), targets)
        publishCombatEvents(player, combatEvents.filter { it is CombatEvent.Started || it is CombatEvent.Active })
        if (combatEvents.any { it is CombatEvent.Started }) {
            emitRoninSlash(
                player,
                RoninSlashEffect.AA,
                player.position.add(0.0, 1.0, 0.0),
                player.position.direction(),
            )
        }
        combatEvents.filterIsInstance<CombatEvent.HitConfirmed>().forEach { hit ->
            targets.firstOrNull { it.id == hit.targetId }?.let { target ->
                applyRoninDirectDamage(
                    player,
                    state,
                    target,
                    RoninBalance.AA_DAMAGE.toDouble(),
                    hit.attackExecutionId,
                )
            }
        }
        val velocityWasApplied = dodgeVelocityActive[player.uuid] == true
        val movement = dodge.tick(
            canStart = !combatState.isAttacking,
            facing = player.position.direction(),
            startAllowed = { canStartDodge(player.isOnGround, WeaponType.RONIN) },
        )
        if (movement != null) {
            moveDodge(player, dodge, movement)
            dodgeVelocityActive[player.uuid] = true
        } else if (velocityWasApplied) {
            stopDodgeVelocity(player)
            dodgeVelocityActive[player.uuid] = false
        }
        sendRoninHudSnapshot(player)
        if (!prototypeBoss.isEncounterRunning) finishEncounter()
    }

    fun handleRoninSkillInput(
        player: net.minestom.server.entity.Player,
        combatState: CombatState,
        slot: ClassSkillSlot,
    ) {
        val state = roninStates[player.uuid] ?: return
        if (state.isMovementLocked || combatState.isAttacking) return
        val skill = when (slot) {
            ClassSkillSlot.SKILL_1 -> RoninSkill.Q
            ClassSkillSlot.SKILL_2 -> RoninSkill.W
            ClassSkillSlot.SKILL_3 -> RoninSkill.E
            ClassSkillSlot.ULTIMATE -> RoninSkill.R
        }
        val cast = state.tryCast(skill, player.position, player.position.direction()) ?: return
        roninLockPositions[player.uuid] = player.position
        player.setVelocity(Vec.ZERO)
        showRoninCastStartVfx(player, cast)
        sendRoninHudSnapshot(player)
    }

    events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
        event.player.respawnPoint = Pos(0.0, 41.0, 0.0)
    }
    events.addListener(PlayerSpawnEvent::class.java) { event ->
        prototypeBoss.registerPlayer(event.player.uuid)
        event.player.setHealth(prototypeBoss.playerMaxHealth.toFloat())
        val starweaver = starweaverStates.getOrPut(event.player.uuid) { StarweaverRuntimeState() }
        val ronin = roninStates.getOrPut(event.player.uuid) { RoninState() }
        selectedClasses.putIfAbsent(event.player.uuid, PlayableClass.TWIN_BLADES)
        val resources = classResources.getOrPut(event.player.uuid) { ClassResourceState() }
        val skill1 = skill1States.getOrPut(event.player.uuid) { Skill1State() }
        val skill2 = skill2States.getOrPut(event.player.uuid) { Skill2State() }
        val skill3 = skill3States.getOrPut(event.player.uuid) { Skill3State() }
        if (!event.isFirstSpawn) {
            starweaver.reset()
            ronin.reset()
            roninLockPositions.remove(event.player.uuid)
            restoreStarweaverMovementSpeed(event.player)
            event.player.setAdditionalHearts(0f)
            resources.reset()
            skill1.reset()
            skill2.reset()
            skill3.reset()
        }
        resourceSyncTicks[event.player.uuid] = 0
        sendResourceSnapshot(event.player)
        sendStarweaverHudSnapshot(event.player)
        sendRoninHudSnapshot(event.player)
        updateBossBar()
        event.player.showBossBar(bossBar)
        if (event.isFirstSpawn) {
            attackSpeeds[event.player.uuid] = DEFAULT_ATTACK_SPEED
            twinBladesComboStates[event.player.uuid] = TwinBladesComboState()
            combatStates[event.player.uuid] = CombatState(
                weaponSource = {
                    if (selectedClasses[event.player.uuid] == PlayableClass.RONIN) WeaponType.RONIN
                    else weaponFor(event.player)
                },
                attackSpeedSource = { attackSpeeds[event.player.uuid] ?: DEFAULT_ATTACK_SPEED },
            )
            dodgeStates[event.player.uuid] = DodgeState()
            twinRodsAirStates[event.player.uuid] = TwinRodsAirState()
            event.player.inventory.addItemStack(
                ItemStack.builder(Material.NETHERITE_SWORD).customName(Component.text("Heavy Blade")).build(),
            )
            event.player.inventory.addItemStack(
                ItemStack.builder(Material.IRON_SWORD).customName(Component.text("Twin Blades")).build(),
            )
            if (dummy == null) {
                dummy = Entity(EntityType.RAVAGER).apply {
                        customName = Component.text("Rift Executioner")
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
        resetStarweaverPlayerState(event.player)
        resetRoninPlayerState(event.player)
        particleAnimations.cancel(slashPreviewHandles.remove(playerId))
        vfxEditor2.disconnect(event.player)
        particleAnimations.cancelFor(event.player)
        combatStates.remove(playerId)
        dodgeStates.remove(playerId)
        twinRodsAirStates.remove(playerId)
        classResources.remove(playerId)
        skill1States.remove(playerId)
        skill2States.remove(playerId)
        skill3States.remove(playerId)
        starweaverStates.remove(playerId)
        roninStates.remove(playerId)
        roninLockPositions.remove(playerId)
        selectedClasses.remove(playerId)
        resourceSyncTicks.remove(playerId)
        lastSentCooldowns.remove(playerId)
        attackSpeeds.remove(playerId)
        twinBladesSwingAngles.remove(playerId)
        twinBladesComboStates.remove(playerId)
        twinBladesAttackSteps.remove(playerId)
        lastGroundTelegraphIds.remove(playerId)
        if (instance.players.none { it.uuid != playerId }) {
            clearBossTelegraphs()
            riftExecutioner.reset()
            prototypeBoss.reset()
        }
    }
    events.addListener(InstanceTickEvent::class.java) { event ->
        if (event.instance === instance) {
            particleManager.beginTick()
            particleProfiler.setActiveEffects(particleAnimations.activeAnimationCount)
            particleAnimations.tick()
            vfxEditor2.tick()
            particleManager.flush()
        }
    }
    events.addListener(PlayerTickEvent::class.java) { event ->
        if (selectedClasses[event.player.uuid] != PlayableClass.STARWEAVER) {
            synchronizeTwinBladesOffhand(event.player)
        }
        val state = combatStates[event.player.uuid] ?: return@addListener
        val dodge = dodgeStates[event.player.uuid] ?: return@addListener
        val twinRodsAir = twinRodsAirStates[event.player.uuid] ?: return@addListener
        val resources = classResources[event.player.uuid] ?: return@addListener
        val skill1 = skill1States[event.player.uuid] ?: return@addListener
        val skill2 = skill2States[event.player.uuid] ?: return@addListener
        val skill3 = skill3States[event.player.uuid] ?: return@addListener
        if (event.player == instance.players.firstOrNull()) {
            if (prototypeBoss.isEncounterRunning) {
                tickRiftExecutioner(
                    instance,
                    dummy,
                    riftExecutioner,
                    prototypeBoss,
                    testerMarkerTick++,
                    particleAnimations,
                    particleManager,
                    bossGroundTelegraphIds,
                    bossRiftTelegraphIds,
                    nextTelegraphId = { nextGroundTelegraphId++ },
                    damageAllowed = { playerId -> roninStates[playerId]?.isW3Untargetable != true },
                )
                if (!prototypeBoss.isEncounterRunning) {
                    finishEncounter()
                    return@addListener
                }
            }
        }
        if (selectedClasses[event.player.uuid] == PlayableClass.STARWEAVER) {
            val starweaver = starweaverStates[event.player.uuid] ?: return@addListener
            tickStarweaverPlayer(event.player, state, dodge, starweaver)
            return@addListener
        }
        if (selectedClasses[event.player.uuid] == PlayableClass.RONIN) {
            val ronin = roninStates[event.player.uuid] ?: return@addListener
            tickRoninPlayer(event.player, state, dodge, ronin)
            return@addListener
        }
        if (!prototypeBoss.isEncounterRunning) return@addListener
        twinBladesComboStates[event.player.uuid]?.tick()
        twinRodsAir.tick(event.player.isOnGround)
        if (dodge.hasPending) state.deferAttackRestart()
        val tester = dummy?.takeIf { it.instance == event.player.instance && !it.isRemoved }
        val testerId = tester?.uuid
        val weapon = state.activeProfile?.weapon ?: weaponFor(event.player)
        val profileRange = state.activeProfile?.range
            ?: weapon.profile(attackSpeeds[event.player.uuid] ?: DEFAULT_ATTACK_SPEED).range
        val eyePosition = event.player.position.add(0.0, event.player.eyeHeight, 0.0)
        val attackOrigin = if (weapon == WeaponType.TWIN_RODS) eyePosition else event.player.position
        val weakpoint = tester?.let {
            FixedAttackTester.selectWeakpoint(
                playerPosition = eyePosition,
                playerDirection = event.player.position.direction(),
                testerOrigin = it.position,
                testerFacing = it.position.direction(),
                weaponRange = if (weapon == WeaponType.TWIN_RODS) {
                    profileRange + FixedAttackTester.WEAKPOINT_RADIUS
                } else {
                    profileRange
                },
            )
        }
        val targets = tester?.let {
            val target = if (weapon == WeaponType.TWIN_RODS) {
                weakpoint?.let { selection ->
                    CombatTarget(
                        id = it.uuid,
                        position = selection.center,
                        sphereRadius = FixedAttackTester.WEAKPOINT_RADIUS,
                    )
                } ?: combatTarget(it)
            } else {
                CombatTarget(it.uuid, weakpoint?.center ?: it.position)
            }
            listOf(target)
        } ?: emptyList()
        val combatEvents = state.tick(attackOrigin, event.player.position.direction(), targets)
        showTwinBladesSwingVfx(
            event.player,
            state,
            combatEvents,
            twinBladesSwingAngles,
            twinBladesComboStates,
            twinBladesAttackSteps,
            particleAnimations,
            particleManager,
        )
        publishCombatEvents(
            event.player,
            combatEvents.filter { it is CombatEvent.Started || it is CombatEvent.Active },
        )
        combatEvents.filterIsInstance<CombatEvent.HitConfirmed>().forEach { hit ->
            val damage = prototypeBoss.applyPlayerAttack(
                attackExecutionId = hit.attackExecutionId,
                weapon = hit.weapon,
                weakpoint = weakpoint?.weakpoint,
            )
            twinRodsAir.onAttackHit(hit.weapon, event.player.isOnGround, hit.attackExecutionId)
            if (skill3.reduceCooldownForNormalAttack(hit.attackExecutionId)) {
                sendResourceSnapshot(event.player)
            }
            if (damage > 0) {
                updateBossBar()
                if (weakpoint != null) {
                    if (hit.weapon == WeaponType.TWIN_RODS) showWeakpointLabel(event.player, weakpoint)
                    else showWeakpointHit(event.player, weakpoint)
                }
                if (hit.weapon == WeaponType.TWIN_RODS) {
                    val target = tester ?: return@forEach
                    val vfxPlan = twinBladesHitVfxPlan(hit.weapon, confirmed = true, weakpoint = weakpoint != null)
                    val visualScale = twinBladesVisualScale(
                        width = target.boundingBox.maxX() - target.boundingBox.minX(),
                        height = target.boundingBox.maxY() - target.boundingBox.minY(),
                    )
                    if ("projects:class/twin_blades/weakpoint_hit" in vfxPlan.presets && weakpoint != null) {
                        showTwinBladesWeakpointVfx(
                            event.player,
                            weakpoint,
                            visualScale,
                            twinBladesComboVisual(twinBladesAttackSteps[event.player.uuid] ?: 1),
                            particleAnimations,
                            particleManager,
                        )
                    }
                    showTwinRodsHitVfx(
                        event.player,
                        weakpoint?.center ?: target.position.add(0.0, 1.1, 0.0),
                        Vec(
                            event.player.position.x() - target.position.x(),
                            event.player.position.y() + event.player.eyeHeight - target.position.y() - 1.1,
                            event.player.position.z() - target.position.z(),
                        ),
                        particleAnimations,
                        particleManager,
                        visualScale,
                        twinBladesComboVisual(twinBladesAttackSteps[event.player.uuid] ?: 1),
                    )
                }
            }
        }
        publishCombatEvents(event.player, combatEvents.filterIsInstance<CombatEvent.HitConfirmed>())
        if (!prototypeBoss.isEncounterRunning) {
            finishEncounter()
            return@addListener
        }
        val skill1Tick = skill1.tick()
        if (skill1Tick.dashActive) {
            val direction = requireNotNull(skill1Tick.dashDirection)
            val start = event.player.position
            val end = start.add(
                direction.x() * Skill1State.DASH_SPEED / ServerFlag.SERVER_TICKS_PER_SECOND,
                0.0,
                direction.z() * Skill1State.DASH_SPEED / ServerFlag.SERVER_TICKS_PER_SECOND,
            )
            event.player.setVelocity(
                Vec(
                    direction.x() * Skill1State.DASH_SPEED,
                    event.player.velocity.y(),
                    direction.z() * Skill1State.DASH_SPEED,
                ),
            )
            showSkill1Trail(event.player, start, direction, particleAnimations, particleManager)
            val skillTargets = tester?.let { listOf(combatTarget(it)) } ?: emptyList()
            val hitTargets = skill1.hitTargetsOnSegment(start, end, skillTargets)
            if (hitTargets.isNotEmpty()) {
                hitTargets.forEach { targetId ->
                    val damage = prototypeBoss.applySkill1Attack(skill1.castId, targetId)
                    if (damage > 0) updateBossBar()
                }
                showSkill1Impact(event.player, tester?.position ?: end, direction, particleAnimations, particleManager)
                showSkill1Launch(event.player, direction, particleAnimations, particleManager)
                event.player.setVelocity(
                    Vec(
                        direction.x() * Skill1State.LAUNCH_HORIZONTAL_SPEED,
                        Skill1State.LAUNCH_SPEED_Y,
                        direction.z() * Skill1State.LAUNCH_HORIZONTAL_SPEED,
                    ),
                )
            } else if (skill1Tick.stopHorizontalVelocity) {
                event.player.setVelocity(Vec(0.0, event.player.velocity.y(), 0.0))
            }
        }
        if (!prototypeBoss.isEncounterRunning) {
            finishEncounter()
            return@addListener
        }
        val skill2Tick = skill2.tick(event.player.isOnGround)
        if (skill2Tick.diveActive) {
            event.player.setVelocity(Vec(0.0, skill2Tick.velocityY, 0.0))
            showSkill2DiveTrail(event.player)
            skill2Tick.pulseIndex?.let { pulseIndex ->
                val skillTargets = tester?.let { listOf(combatTarget(it)) } ?: emptyList()
                skill2.hitTargetsAtPulse(pulseIndex, event.player.position, skillTargets).forEach { targetId ->
                    val damage = prototypeBoss.applySkill2Pulse(skill2.castId, pulseIndex, targetId)
                    if (damage > 0) updateBossBar()
                }
                showSkill2Pulse(event.player, pulseIndex, particleAnimations, particleManager)
            }
        } else if (skill2Tick.landed) {
            val skillTargets = tester?.let { listOf(combatTarget(it)) } ?: emptyList()
            skill2.hitTargetsAtLanding(event.player.position, skillTargets).forEach { targetId ->
                val damage = prototypeBoss.applySkill2Landing(skill2.castId, targetId)
                if (damage > 0) updateBossBar()
            }
            showSkill2Landing(event.player, particleAnimations, particleManager)
            sendResourceSnapshot(event.player)
            event.player.setVelocity(Vec.ZERO)
        }
        if (!prototypeBoss.isEncounterRunning) {
            finishEncounter()
            return@addListener
        }
        val skill3Target = tester?.let(::combatTarget)
        if (skill3.phase == Skill3Phase.MULTIHIT && skill3.primaryTargetId != skill3Target?.id) {
            // A removed target must not receive delayed pulses or a finisher.
            skill3.cancelActiveMovement()
        } else {
            val previousSkill3Phase = skill3.phase
            val skill3Tick = skill3.tick(event.player.isOnGround, event.player.velocity.y())
            if (skill3Tick.dashActive) {
                val direction = requireNotNull(skill3Tick.dashDirection)
                val start = event.player.position
                val end = start.add(
                    direction.x() * Skill3State.DASH_SPEED / ServerFlag.SERVER_TICKS_PER_SECOND,
                    direction.y() * Skill3State.DASH_SPEED / ServerFlag.SERVER_TICKS_PER_SECOND,
                    direction.z() * Skill3State.DASH_SPEED / ServerFlag.SERVER_TICKS_PER_SECOND,
                )
                val skillTargets = skill3Target?.let(::listOf) ?: emptyList()
                val hitTargets = skill3.hitTargetsOnSegment(start, end, skillTargets)
                if (hitTargets.isNotEmpty()) {
                    val target = skillTargets.first { it.id == hitTargets.first() }
                    if (skill3.finishDashOnHit(target.id)) {
                        event.player.setVelocity(Vec.ZERO)
                        showSkill3CatchVfx(
                            event.player,
                            start,
                            direction,
                            target,
                            particleAnimations,
                            particleManager,
                        )
                    }
                } else {
                    event.player.setVelocity(
                        Vec(
                            direction.x() * Skill3State.DASH_SPEED,
                            direction.y() * Skill3State.DASH_SPEED,
                            direction.z() * Skill3State.DASH_SPEED,
                        ),
                    )
                }
                showSkill3DashTrail(event.player, start, direction, particleAnimations, particleManager)
            } else if (skill3Tick.phase == Skill3Phase.MULTIHIT) {
                val target = skill3Target?.takeIf { it.id == skill3.primaryTargetId }
                if (target == null) {
                    skill3.cancelActiveMovement()
                } else {
                    skill3Tick.pulseIndex?.let { pulseIndex ->
                        val damage = prototypeBoss.applySkill3Pulse(skill3.castId, pulseIndex, target.id)
                        if (damage > 0) updateBossBar()
                        showSkill3PulseVfx(
                            event.player,
                            target,
                            requireNotNull(skill3Tick.dashDirection),
                            pulseIndex,
                            skill3SlashBindingStore,
                            particleAnimations,
                            particleManager,
                        )
                    }
                    if (skill3Tick.finisherActive) {
                        val damage = prototypeBoss.applySkill3Finisher(skill3.castId, target.id)
                        if (damage > 0) updateBossBar()
                        sendResourceSnapshot(event.player)
                        event.player.setVelocity(skill3HitBounceVelocity(requireNotNull(skill3Tick.dashDirection)))
                        showSkill3FinisherVfx(
                            event.player,
                            target,
                            requireNotNull(skill3Tick.dashDirection),
                            particleAnimations,
                            particleManager,
                        )
                    } else {
                        event.player.setVelocity(Vec.ZERO)
                    }
                }
            } else if (skill3Tick.phase == Skill3Phase.HOVER) {
                if (skill3Tick.stopHorizontalVelocity) {
                    event.player.setVelocity(Vec.ZERO)
                } else {
                    event.player.setVelocity(skill3HoverVelocity(event.player.velocity, skill3Tick.velocityY))
                }
            }
            if (previousSkill3Phase == Skill3Phase.DASH && skill3.phase == Skill3Phase.HOVER) {
                showSkill3Hover(event.player)
                sendResourceSnapshot(event.player)
            }
        }
        if (!prototypeBoss.isEncounterRunning) {
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
                canStartDodge(event.player.isOnGround, weaponFor(event.player))
            },
        )
        if (movement != null) {
            moveDodge(event.player, dodge, movement)
            dodgeVelocityActive[event.player.uuid] = true
        } else if (velocityWasApplied) {
            stopDodgeVelocity(event.player)
            dodgeVelocityActive[event.player.uuid] = false
        }
        val syncTick = (resourceSyncTicks[event.player.uuid] ?: 0) + 1
        if (syncTick >= 4) {
            resourceSyncTicks[event.player.uuid] = 0
            if (shouldSyncSkillCooldowns(
                    syncTick,
                    SkillCooldowns(
                        skill1 = skill1.cooldownTicksRemaining,
                        skill2 = skill2.cooldownTicksRemaining,
                        skill3 = skill3.cooldownTicksRemaining,
                    ),
                    lastSentCooldowns[event.player.uuid],
                )
            ) {
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
                    if (selectedClasses[event.player.uuid] == PlayableClass.STARWEAVER) return@addListener
                    if (selectedClasses[event.player.uuid] == PlayableClass.RONIN &&
                        roninStates[event.player.uuid]?.isMovementLocked == true
                    ) return@addListener
                    if (!prototypeBoss.isActive) return@addListener
                    val state = combatStates[event.player.uuid] ?: return@addListener
                    val combatEvents = state.input(message.state)
                    showTwinBladesSwingVfx(
                        event.player,
                        state,
                        combatEvents,
                        twinBladesSwingAngles,
                        twinBladesComboStates,
                        twinBladesAttackSteps,
                        particleAnimations,
                        particleManager,
                    )
                    publishCombatEvents(event.player, combatEvents)
                }
                is DodgeInput -> {
                    if (!prototypeBoss.isActive) return@addListener
                    val state = combatStates[event.player.uuid] ?: return@addListener
                    val dodge = dodgeStates[event.player.uuid] ?: return@addListener
                    val twinRodsAir = twinRodsAirStates[event.player.uuid] ?: return@addListener
                    val isRonin = selectedClasses[event.player.uuid] == PlayableClass.RONIN
                    if (isRonin && roninStates[event.player.uuid]?.isMovementLocked == true) return@addListener
                    dodge.request(
                        message,
                        canStart = !state.isAttacking,
                        facing = event.player.position.direction(),
                        startAllowed = {
                            canStartDodge(
                                event.player.isOnGround,
                                if (isRonin) WeaponType.RONIN else weaponFor(event.player),
                            )
                        },
                    )
                }
                is AirJumpInput -> {
                    if (!prototypeBoss.isActive) return@addListener
                    if (selectedClasses[event.player.uuid] == PlayableClass.RONIN) return@addListener
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
                    if (!prototypeBoss.isActive) return@addListener
                    if (selectedClasses[event.player.uuid] == PlayableClass.STARWEAVER) {
                        handleStarweaverSkillInput(event.player, message.slot)
                        return@addListener
                    }
                    if (selectedClasses[event.player.uuid] == PlayableClass.RONIN) {
                        handleRoninSkillInput(
                            event.player,
                            combatStates[event.player.uuid] ?: return@addListener,
                            message.slot,
                        )
                        return@addListener
                    }
                    val skill1 = skill1States[event.player.uuid] ?: return@addListener
                    val skill2 = skill2States[event.player.uuid] ?: return@addListener
                    val skill3 = skill3States[event.player.uuid] ?: return@addListener
                    when (message.slot) {
                        ClassSkillSlot.SKILL_1 -> {
                            if (skill2.phase == Skill2Phase.DIVE || skill3.phase != Skill3Phase.IDLE) return@addListener
                            if (skill1.tryCast(event.player.position.direction()) != null) {
                                showSkill1Cast(event.player, particleAnimations, particleManager)
                                sendResourceSnapshot(event.player)
                            }
                        }
                        ClassSkillSlot.SKILL_2 -> {
                            val castId = skill2.tryCast(event.player.isOnGround)
                            if (castId != null) {
                                skill1.cancelActiveMovement()
                                skill3.cancelActiveMovement()
                                event.player.setVelocity(Vec(0.0, -Skill2State.DESCENT_SPEED, 0.0))
                                showSkill2Cast(event.player, particleAnimations, particleManager)
                            }
                        }
                        ClassSkillSlot.SKILL_3 -> {
                            if (skill1.phase == Skill1Phase.DASH || skill2.phase == Skill2Phase.DIVE) {
                                return@addListener
                            }
                            val castId = skill3.tryCast(
                                event.player.position.direction(),
                                ClassSkillDirection(message.directionX, message.directionZ),
                            )
                            if (castId != null) {
                                showSkill3Cast(event.player)
                                sendResourceSnapshot(event.player)
                            }
                        }
                        ClassSkillSlot.ULTIMATE -> return@addListener
                    }
                }
                is VfxSlashPreviewRequest -> previewSlash(event.player, message.parameters)
                VfxSlashPreviewCancel -> particleAnimations.cancel(slashPreviewHandles.remove(event.player.uuid))
                is VfxSlashApplySkill3 -> {
                    val saved = skill3SlashBindingStore.save(message.parameters)
                    event.player.sendPluginMessage(
                        PROJECTS_CHANNEL,
                        ProtocolCodec.encode(
                            if (saved) VfxEditorNotice("Skill3へ適用しました")
                            else VfxEditorNotice("Skill3への適用に失敗しました"),
                        ),
                    )
                }
                is VfxSlashSaveRequest -> {
                    val saved = slashDraftStore.save(message.name, message.parameters)
                    event.player.sendPluginMessage(
                        PROJECTS_CHANNEL,
                        ProtocolCodec.encode(
                            if (saved) VfxEditorNotice("Saved draft '${message.name}'")
                            else VfxEditorNotice("Draft name is invalid or storage is full"),
                        ),
                    )
                    if (saved) sendSlashDraftList(event.player)
                }
                is VfxSlashDraftLoadRequest -> {
                    val parameters = slashDraftStore.load(message.name)
                    event.player.sendPluginMessage(
                        PROJECTS_CHANNEL,
                        ProtocolCodec.encode(
                            if (parameters == null) VfxEditorNotice("Draft not found")
                            else VfxSlashDraft(message.name, parameters),
                        ),
                    )
                }
                is VfxEditor2PreviewRequest -> vfxEditor2.preview(event.player, message)
                VfxEditor2PreviewCancel -> vfxEditor2.cancel(event.player)
                is VfxEditor2SaveRequest -> vfxEditor2.save(event.player, message.composition)
                is VfxEditor2LoadRequest -> vfxEditor2.load(event.player, message)
                is VfxEditor2ApplyRequest -> vfxEditor2.apply(event.player, message)
                else -> throw IllegalArgumentException("Unexpected ProjectS message")
            }
        } catch (error: IllegalArgumentException) {
            event.player.kick(Component.text(error.message ?: "Invalid ProjectS protocol handshake"))
        }
    }

    server.start(SERVER_ADDRESS, SERVER_PORT)
    println("ProjectS Minestom server listening on $SERVER_ADDRESS:$SERVER_PORT")
}

private fun StarweaverCelestial.toHudCelestial(): StarweaverHudCelestial = when (this) {
    StarweaverCelestial.SUN -> StarweaverHudCelestial.SUN
    StarweaverCelestial.MOON -> StarweaverHudCelestial.MOON
    StarweaverCelestial.STAR -> StarweaverHudCelestial.STAR
}

private fun publishCombatEvents(player: net.minestom.server.entity.Player, events: List<CombatEvent>) {
    for (event in events) {
        val message = when (event) {
            is CombatEvent.Started -> AttackStarted(event.attackExecutionId)
            is CombatEvent.Active -> AttackDebugShape(
                kind = when (event.profile.weapon) {
                    WeaponType.TWIN_RODS -> AttackDebugShapeKind.TWIN_RODS
                    WeaponType.HEAVY_BLADE -> AttackDebugShapeKind.HEAVY_BLADE
                    WeaponType.RONIN -> AttackDebugShapeKind.HEAVY_BLADE
                },
                originX = event.position.x(),
                originY = event.position.y(),
                originZ = event.position.z(),
                directionX = event.direction.x(),
                directionY = event.direction.y(),
                directionZ = event.direction.z(),
                range = event.profile.range,
                minForwardDot = event.profile.minForwardDot,
                verticalRange = event.profile.verticalRange,
            )
            is CombatEvent.HitConfirmed -> AttackHitConfirmed(event.attackExecutionId, event.targetId)
        }
        player.sendPluginMessage(PROJECTS_CHANNEL, ProtocolCodec.encode(message))
    }
}

private fun weaponFor(player: net.minestom.server.entity.Player): WeaponType = when (
    player.getEquipment(EquipmentSlot.MAIN_HAND).material()
) {
    Material.IRON_SWORD -> WeaponType.TWIN_RODS
    Material.NETHERITE_SWORD -> WeaponType.HEAVY_BLADE
    else -> WeaponType.HEAVY_BLADE
}

private fun synchronizeTwinBladesOffhand(player: net.minestom.server.entity.Player) {
    val offhand = player.getEquipment(EquipmentSlot.OFF_HAND)
    if (weaponFor(player) == WeaponType.TWIN_RODS) {
        if (offhand.isAir) {
            player.setEquipment(
                EquipmentSlot.OFF_HAND,
                ItemStack.builder(Material.IRON_SWORD)
                    .customName(Component.text("Twin Blades"))
                    .set(TWIN_BLADES_AUTO_OFFHAND, true)
                    .build(),
            )
        }
    } else if (offhand.getTag(TWIN_BLADES_AUTO_OFFHAND)) {
        player.setEquipment(EquipmentSlot.OFF_HAND, ItemStack.AIR)
    }
}

internal fun canStartDodge(isGrounded: Boolean, weapon: WeaponType): Boolean =
    isGrounded || weapon == WeaponType.TWIN_RODS

private fun combatTarget(entity: Entity): CombatTarget {
    return combatTargetFromBoundingBox(entity.uuid, entity.position, entity.boundingBox)
}

internal fun combatTargetFromBoundingBox(id: UUID, origin: Point, relativeBox: BoundingBox): CombatTarget {
    val box = relativeBox.withOffset(origin)
    return CombatTarget(
        id = id,
        position = Pos(
            (box.minX() + box.maxX()) / 2.0,
            (box.minY() + box.maxY()) / 2.0,
            (box.minZ() + box.maxZ()) / 2.0,
        ),
        halfExtent = Vec(
            (box.maxX() - box.minX()) / 2.0,
            (box.maxY() - box.minY()) / 2.0,
            (box.maxZ() - box.minZ()) / 2.0,
        ),
    )
}

internal fun skill3HoverVelocity(currentVelocity: Vec, velocityY: Double): Vec = Vec(
    currentVelocity.x(),
    velocityY,
    currentVelocity.z(),
)

internal fun shouldSyncSkill3Cooldown(syncTick: Int, currentCooldown: Int, lastSentCooldown: Int?): Boolean =
    syncTick >= 4 && currentCooldown != lastSentCooldown

internal fun shouldSyncSkillCooldowns(
    syncTick: Int,
    currentCooldowns: SkillCooldowns,
    lastSentCooldowns: SkillCooldowns?,
): Boolean = syncTick >= 4 && currentCooldowns != lastSentCooldowns

private fun tickRiftExecutioner(
    instance: Instance,
    testerEntity: Entity?,
    controller: RiftExecutionerController,
    bossState: PrototypeBossState,
    markerTick: Long,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
    bossGroundTelegraphIds: MutableSet<Long>,
    bossRiftTelegraphIds: MutableMap<Long, Long>,
    nextTelegraphId: () -> Long,
    damageAllowed: (UUID) -> Boolean = { true },
) {
    if (testerEntity == null || testerEntity.isRemoved || testerEntity.instance != instance) return
    val players = instance.players.filter { it.isOnline }
    if (players.isEmpty()) return
    if (markerTick % 2L == 0L) {
        val weakpointFacing = testerEntity.position.direction()
        players.forEach { player -> showWeakpointMarkers(player, testerEntity.position, weakpointFacing) }
    }
    val targets = players.map { RiftExecutionerTarget(it.uuid, it.position) }
    val facing = players.firstOrNull()?.let { directionFrom(testerEntity.position, it.position) }
        ?: testerEntity.position.direction()
    val events = controller.tick(
        origin = testerEntity.position,
        facing = facing,
        targets = targets,
        bossPhase = bossState.phase,
        encounterState = bossState.encounterState,
    )
    for (event in events) {
        when (event) {
            is RiftExecutionerEvent.SectorTelegraph -> {
                val label = event.attack.displayName()
                players.forEach { player -> player.sendMessage(Component.text("[Rift Executioner] $label: telegraph")) }
                if (event.attack == RiftExecutionerAttack.SECTOR_CLEAVE) {
                    val telegraphId = nextTelegraphId()
                    val message = GroundTelegraphStart.clamped(
                        telegraphId = telegraphId,
                        centerX = event.origin.x(),
                        centerY = event.origin.y(),
                        centerZ = event.origin.z(),
                        facingX = event.facing.x(),
                        facingZ = event.facing.z(),
                        radius = RiftExecutionerController.SECTOR_RADIUS,
                        angleDegrees = RiftExecutionerController.SECTOR_ANGLE,
                        durationTicks = event.durationTicks,
                    )
                    bossGroundTelegraphIds += telegraphId
                    val payload = ProtocolCodec.encode(message)
                    players.forEach { player -> player.sendPluginMessage(PROJECTS_CHANNEL, payload) }
                } else {
                    players.forEach { player ->
                        showTesterTelegraph(
                            player,
                            testerEntity.position,
                            FixedAttackType.FORWARD_SLAM,
                            event.facing,
                            scheduler,
                            manager,
                        )
                    }
                }
            }
            is RiftExecutionerEvent.SectorActive -> {
                val attack = if (event.attack == RiftExecutionerAttack.FORWARD_SLAM) {
                    FixedAttackType.FORWARD_SLAM
                } else {
                    FixedAttackType.SIDE_SWEEP
                }
                players.forEach { player ->
                    showTesterActive(player, Pos(event.origin.x(), event.origin.y(), event.origin.z()), attack, event.facing)
                }
            }
            is RiftExecutionerEvent.DashTelegraph -> {
                players.forEach { player ->
                    player.sendMessage(Component.text("[Rift Executioner] Chain Dash: telegraph"))
                    startParticlePreset(
                        player,
                        "projects:combat/projectile_trail",
                        scheduler,
                        event.origin.add(0.0, 0.8, 0.0),
                        FixedAttackTester.normalizeHorizontal(Vec(
                            event.target.x() - event.origin.x(),
                            0.0,
                            event.target.z() - event.origin.z(),
                        )),
                        manager,
                        mapOf("length" to 3.0, "duration" to 18.0),
                    )
                }
            }
            is RiftExecutionerEvent.DashPosition -> {
                testerEntity.teleport(Pos(event.position.x(), event.position.y(), event.position.z()).withDirection(event.facing))
            }
            is RiftExecutionerEvent.AttackHit -> {
                if (damageAllowed(event.targetId)) {
                    instance.getPlayerByUuid(event.targetId)?.let { player ->
                        val damage = bossState.applyBossDamage(player.uuid, event.executionId, event.damage)
                        if (damage == 0) return@let
                        player.setHealth(bossState.playerEntityHealth(player.uuid))
                        player.sendMessage(Component.text("[Rift Executioner] HIT $damage"))
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
            is RiftExecutionerEvent.RiftCreated -> {
                val telegraphId = nextTelegraphId()
                bossRiftTelegraphIds[event.zone.id] = telegraphId
                bossGroundTelegraphIds += telegraphId
                val message = GroundTelegraphStart.clamped(
                    telegraphId = telegraphId,
                    centerX = event.zone.origin.x(),
                    centerY = event.zone.origin.y(),
                    centerZ = event.zone.origin.z(),
                    facingX = event.zone.facing.x(),
                    facingZ = event.zone.facing.z(),
                    radius = RiftExecutionerController.SECTOR_RADIUS,
                    angleDegrees = RiftExecutionerController.SECTOR_ANGLE,
                    durationTicks = event.zone.remainingTicks,
                )
                val payload = ProtocolCodec.encode(message)
                players.forEach { player -> player.sendPluginMessage(PROJECTS_CHANNEL, payload) }
            }
            is RiftExecutionerEvent.RiftRemoved -> {
                bossRiftTelegraphIds.remove(event.zoneId)?.let { telegraphId ->
                    bossGroundTelegraphIds.remove(telegraphId)
                    val payload = ProtocolCodec.encode(GroundTelegraphRemove(telegraphId))
                    players.forEach { player -> player.sendPluginMessage(PROJECTS_CHANNEL, payload) }
                }
            }
            RiftExecutionerEvent.BreakStarted -> {
                bossState.setBreakActive(true)
                players.forEach { player ->
                    player.sendMessage(Component.text("[Rift Executioner] BREAK"))
                    startParticlePreset(
                        player,
                        "projects:combat/projectile_impact",
                        scheduler,
                        testerEntity.position.add(0.0, 0.9, 0.0),
                        Vec(0.0, 1.0, 0.0),
                        manager,
                        mapOf("radius" to 1.4, "duration" to 10.0),
                    )
                }
            }
            RiftExecutionerEvent.BreakEnded -> bossState.setBreakActive(false)
            RiftExecutionerEvent.FinalStruggleComplete -> bossState.completeFinalStruggle()
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
    showWeakpointLabel(player, selection)
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

private fun showWeakpointLabel(player: net.minestom.server.entity.Player, selection: FixedWeakpointSelection) {
    player.sendMessage(Component.text("[Tester] WEAKPOINT: ${selection.weakpoint}"))
}

private fun showTwinBladesWeakpointVfx(
    player: net.minestom.server.entity.Player,
    selection: FixedWeakpointSelection,
    visualScale: Double,
    visual: TwinBladesComboVisual,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val center = selection.center
    playTwinBladesSounds(
        player,
        center,
        twinBladesSoundPlan(WeaponType.TWIN_RODS, visual.step, confirmed = true, weakpoint = true).weakpointAccent,
    )
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/weakpoint_hit",
        scheduler = scheduler,
        origin = center,
        direction = player.position.direction(),
        manager = manager,
        values = mapOf(
            "radius" to twinBladesWeakpointRadius(visualScale),
            "step" to visual.step,
            "duration" to visual.weakpointDuration.toDouble(),
            "colorPrimary" to visual.weakpointPrimary,
            "colorSecondary" to visual.weakpointSecondary,
        ),
    )
}

private fun showTwinRodsHitVfx(
    player: net.minestom.server.entity.Player,
    center: Point,
    facing: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
    scale: Double,
    visual: TwinBladesComboVisual,
) {
    val dimensions = twinBladesHitVisualDimensions(visual)
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/aa_hit",
        scheduler = scheduler,
        origin = center,
        direction = facing,
        manager = manager,
        values = mapOf(
            "length" to dimensions.length,
            "radius" to dimensions.radius,
            "scale" to scale,
            "step" to visual.step,
            "duration" to visual.hitDuration.toDouble(),
            "colorPrimary" to visual.hitPrimary,
            "colorSecondary" to visual.hitSecondary,
        ),
    )
    playTwinBladesSounds(
        player,
        center,
        twinBladesSoundPlan(WeaponType.TWIN_RODS, visual.step, confirmed = true, weakpoint = false).contact,
    )
}

private fun showTwinBladesSwingVfx(
    player: net.minestom.server.entity.Player,
    state: CombatState,
    events: List<CombatEvent>,
    angles: MutableMap<UUID, Double>,
    comboStates: MutableMap<UUID, TwinBladesComboState>,
    attackSteps: MutableMap<UUID, Int>,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    if (state.activeProfile?.weapon != WeaponType.TWIN_RODS || events.none { it is CombatEvent.Started }) return
    val step = comboStates.getOrPut(player.uuid) { TwinBladesComboState() }.start()
    attackSteps[player.uuid] = step
    val visual = twinBladesComboVisual(step)
    val direction = player.position.direction()
    val angle = nextTwinBladesSwingAngle(angles[player.uuid])
    angles[player.uuid] = angle
    val origin = twinBladesSwingOrigin(player.position, player.eyeHeight, direction, angle)
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/aa_swing",
        scheduler = scheduler,
        origin = origin,
        direction = direction,
        manager = manager,
        values = mapOf(
            "length" to visual.swingLength,
            "angle" to angle,
            "step" to visual.step,
            "duration" to visual.swingDuration.toDouble(),
            "colorPrimary" to visual.swingPrimary,
            "colorSecondary" to visual.swingSecondary,
        ),
    )
    playTwinBladesSounds(
        player,
        origin,
        twinBladesSoundPlan(WeaponType.TWIN_RODS, step, confirmed = false, weakpoint = false).swing,
    )
}

private fun showSkill1Cast(
    player: net.minestom.server.entity.Player,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val origin = player.position.add(0.0, 0.45, 0.0)
    val direction = FixedAttackTester.normalizeHorizontal(player.position.direction())
    startParticlePreset(
        player = player,
        id = SKILL1_TRAVEL_VFX,
        scheduler = scheduler,
        origin = origin,
        direction = direction,
        manager = manager,
        values = mapOf("length" to 1.0, "duration" to 2.0, "colorPrimary" to 0x168cff, "colorSecondary" to 0x071525),
    )
    skill1SoundPlan(confirmedHit = false).travel.forEach { cue ->
        playSkillSound(player, cue.key, origin, cue.volume, cue.pitch)
    }
}

private fun showSkill1Trail(
    player: net.minestom.server.entity.Player,
    position: Pos,
    direction: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    startParticlePreset(
        player = player,
        id = SKILL1_TRAVEL_VFX,
        scheduler = scheduler,
        origin = position.add(0.0, 0.45, 0.0),
        direction = direction,
        manager = manager,
        values = mapOf("length" to 1.35, "duration" to 2.0, "colorPrimary" to 0x168cff, "colorSecondary" to 0x071525),
    )
}

private fun showSkill1Impact(
    player: net.minestom.server.entity.Player,
    position: Point,
    direction: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val center = position.add(0.0, 1.05, 0.0)
    startParticlePreset(
        player = player,
        id = SKILL1_STOMP_VFX,
        scheduler = scheduler,
        origin = center,
        direction = direction,
        manager = manager,
        values = mapOf("radius" to 0.82, "duration" to 5.0, "colorPrimary" to 0x168cff, "colorSecondary" to 0x071525),
    )
    skill1SoundPlan(confirmedHit = true).stomp.forEach { cue ->
        playSkillSound(player, cue.key, center, cue.volume, cue.pitch)
    }
}

private fun showSkill1Launch(
    player: net.minestom.server.entity.Player,
    direction: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val origin = player.position.add(0.0, 0.65, 0.0)
    startParticlePreset(
        player = player,
        id = SKILL1_ESCAPE_VFX,
        scheduler = scheduler,
        origin = origin,
        direction = direction,
        manager = manager,
        values = mapOf("length" to 1.7, "duration" to 4.0, "colorPrimary" to 0x168cff, "colorSecondary" to 0x071525),
    )
    skill1SoundPlan(confirmedHit = true).escape.forEach { cue ->
        playSkillSound(player, cue.key, origin, cue.volume, cue.pitch)
    }
}

private fun showSkill2Cast(
    player: net.minestom.server.entity.Player,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val origin = player.position.add(0.0, 0.3, 0.0)
    startParticlePreset(
        player = player,
        id = "projects:combat/charge_inward",
        scheduler = scheduler,
        origin = origin,
        direction = Vec(0.0, 1.0, 0.0),
        manager = manager,
        values = mapOf(
            "radius" to 2.25,
            "duration" to 4.0,
            "colorPrimary" to 0x168cff,
            "colorSecondary" to 0x071525,
        ),
    )
    sendSkillParticle(player, Particle.END_ROD, origin)
    sendSkillParticle(player, Particle.ENCHANT, origin.add(0.0, 0.4, 0.0))
    sendSkillParticle(player, Particle.ELECTRIC_SPARK, origin.add(0.18, 0.2, 0.0))
    sendSkillParticle(player, Particle.ELECTRIC_SPARK, origin.add(-0.18, 0.2, 0.0))
    playSkillSound(player, "item.trident.throw", origin, 0.24f, 0.78f)
}

private fun showSkill2DiveTrail(player: net.minestom.server.entity.Player) {
    val position = player.position
    for (step in 0..4) {
        val progress = step / 4.0
        val y = 0.18 + progress * 1.15
        val radius = 0.2 - progress * 0.12
        sendSkillParticle(player, Particle.END_ROD, position.add(0.0, y, 0.0))
        sendSkillParticle(player, Particle.ELECTRIC_SPARK, position.add(radius, y - 0.08, 0.0))
        sendSkillParticle(player, Particle.ELECTRIC_SPARK, position.add(-radius, y - 0.08, 0.0))
    }
}

private fun showSkill2Pulse(
    player: net.minestom.server.entity.Player,
    pulseIndex: Int,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val center = player.position.add(0.0, 0.9, 0.0)
    val direction = FixedAttackTester.normalizeHorizontal(player.position.direction())
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/skill2_pulse",
        scheduler = scheduler,
        origin = center,
        direction = direction,
        manager = manager,
        values = mapOf(
            "pulse" to pulseIndex,
            "duration" to 2.0,
            "colorPrimary" to when (pulseIndex) {
                1 -> 0x168cff
                2 -> 0x70e9ff
                3 -> 0x8fffff
                else -> 0xe8fdff
            },
            "colorSecondary" to 0x071525,
        ),
    )
    playTwinBladesSounds(player, center, twinBladesSkill2PulseSoundPlan(pulseIndex))
}

private fun showSkill2Landing(
    player: net.minestom.server.entity.Player,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val center = player.position.add(0.0, 0.12, 0.0)
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/skill2_finisher",
        scheduler = scheduler,
        origin = center,
        direction = Vec(0.0, 1.0, 0.0),
        manager = manager,
        values = mapOf(
            "radius" to 4.0,
            "duration" to 6.0,
            "colorPrimary" to 0xe8fdff,
            "colorSecondary" to 0x071525,
        ),
    )
    playTwinBladesSounds(player, center, twinBladesSkill2LandingSoundPlan())
}

private fun showSkill3Cast(player: net.minestom.server.entity.Player) {
    val origin = player.position.add(0.0, 0.8, 0.0)
    val (forward, _, _) = directionBasis(player.position.direction())
    for (step in 0..2) {
        sendSkillParticle(player, Particle.END_ROD, origin.add(forward.x() * step * 0.3, forward.y() * step * 0.3, forward.z() * step * 0.3))
    }
    playTwinBladesSounds(player, origin, twinBladesSkill3SoundPlan().travel)
}

private fun showSkill3DashTrail(
    player: net.minestom.server.entity.Player,
    position: Pos,
    direction: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/skill3_dash_trail",
        scheduler = scheduler,
        origin = position.add(0.0, 0.8, 0.0),
        direction = direction,
        manager = manager,
        values = mapOf("duration" to 2.0),
    )
}

private fun showSkill3CatchVfx(
    player: net.minestom.server.entity.Player,
    dashOrigin: Pos,
    dashDirection: Vec,
    target: CombatTarget,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val contact = twinBladesSkill3ContactPoint(dashOrigin, dashDirection, target.position, target.halfExtent)
    val visual = TwinBladesSkill3Visual()
    val started = startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/skill3_hit",
        scheduler = scheduler,
        origin = contact,
        direction = dashDirection,
        manager = manager,
        values = mapOf(
            "length" to visual.primaryLength,
            "aftercutLength" to visual.aftercutLength,
            "duration" to visual.primaryDuration.toDouble(),
        ),
    )
    if (!started) {
        System.err.println("Skill3 VFX preset failed to start: projects:class/twin_blades/skill3_hit")
    }
    playTwinBladesSounds(player, contact, twinBladesSkill3SoundPlan().confirmedHit)
}

private fun showSkill3PulseVfx(
    player: net.minestom.server.entity.Player,
    target: CombatTarget,
    direction: Vec,
    pulseIndex: Int,
    bindingStore: Skill3SlashBindingStore,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val authored = bindingStore.load()
    if (authored == null) {
        val started = startParticlePreset(
            player = player,
            id = "projects:class/twin_blades/skill3_pulse",
            scheduler = scheduler,
            origin = target.position,
            direction = direction,
            manager = manager,
            values = mapOf("pulse" to pulseIndex.toDouble(), "duration" to 2.0),
        )
        if (!started) System.err.println("Skill3 VFX preset failed to start: projects:class/twin_blades/skill3_pulse")
    } else {
        val choreography = skill3SlashPulseSpec(authored, pulseIndex)
        val origin = slashOrigin(player.position, direction, choreography.parameters)
        val sink = manager.sink(ParticleViewer(player.position, player), PlayerParticleSink(player), "skill3:slash")
        scheduler.start(
            SlashEditorPreview.create(origin, direction, choreography.parameters, choreography.reverseDraw),
            sink,
            id = "skill3:slash:${player.uuid}:$pulseIndex",
        )
    }
    playTwinBladesSounds(player, target.position, twinBladesSkill3PulseSoundPlan(pulseIndex))
}

private fun showSkill3FinisherVfx(
    player: net.minestom.server.entity.Player,
    target: CombatTarget,
    direction: Vec,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val contact = target.position
    val visual = TwinBladesSkill3Visual()
    val started = startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/skill3_finisher",
        scheduler = scheduler,
        origin = contact,
        direction = direction,
        manager = manager,
        values = mapOf("length" to visual.finisherLength, "duration" to visual.finisherDuration.toDouble()),
    )
    if (!started) {
        System.err.println("Skill3 VFX preset failed to start: projects:class/twin_blades/skill3_finisher")
    }
    val recoilOrigin = player.position.add(0.0, 0.8, 0.0)
    startParticlePreset(
        player = player,
        id = "projects:class/twin_blades/skill3_recoil",
        scheduler = scheduler,
        origin = recoilOrigin,
        direction = direction.mul(-1.0),
        manager = manager,
        values = mapOf("duration" to visual.recoilDuration.toDouble()),
    )
    playTwinBladesSounds(player, contact, twinBladesSkill3FinisherSoundPlan())
    playTwinBladesSounds(player, recoilOrigin, twinBladesSkill3SoundPlan().bounce)
}

private fun showSkill3Hover(player: net.minestom.server.entity.Player) {
    val position = player.position.add(0.0, 1.0, 0.0)
    for (step in 0..7) {
        val angle = step * Math.PI / 4.0
        val radial = 0.32
        sendSkillParticle(
            player,
            if (step % 2 == 0) Particle.GLOW else Particle.END_ROD,
            position.add(
                kotlin.math.cos(angle) * radial,
                kotlin.math.sin(angle * 1.5) * 0.18,
                kotlin.math.sin(angle) * radial,
            ),
        )
    }
}

private fun sendSkillArc(
    player: net.minestom.server.entity.Player,
    particle: Particle,
    center: Point,
    direction: Vec,
    radius: Double,
    startAngle: Double,
    endAngle: Double,
    segments: Int,
) {
    val forward = FixedAttackTester.normalizeHorizontal(direction)
    val right = Vec(-forward.z(), 0.0, forward.x())
    for (step in 0..segments) {
        val angle = startAngle + (endAngle - startAngle) * step / segments
        val radial = Vec(
            forward.x() * kotlin.math.cos(angle) + right.x() * kotlin.math.sin(angle),
            0.0,
            forward.z() * kotlin.math.cos(angle) + right.z() * kotlin.math.sin(angle),
        )
        sendSkillParticle(player, particle, center.add(radial.x() * radius, 0.0, radial.z() * radius))
    }
}

private fun sendSkillRing(
    player: net.minestom.server.entity.Player,
    particle: Particle,
    center: Point,
    radius: Double,
    segments: Int,
) {
    for (step in 0 until segments) {
        val angle = 2.0 * Math.PI * step / segments
        sendSkillParticle(
            player,
            particle,
            center.add(kotlin.math.cos(angle) * radius, 0.0, kotlin.math.sin(angle) * radius),
        )
    }
}

private fun directionBasis(direction: Vec): Triple<Vec, Vec, Vec> {
    val length = sqrt(direction.x() * direction.x() + direction.y() * direction.y() + direction.z() * direction.z())
    val forward = Vec(direction.x() / length, direction.y() / length, direction.z() / length)
    val reference = if (abs(forward.y()) < 0.9) Vec(0.0, 1.0, 0.0) else Vec(1.0, 0.0, 0.0)
    val rawRight = Vec(
        forward.y() * reference.z() - forward.z() * reference.y(),
        forward.z() * reference.x() - forward.x() * reference.z(),
        forward.x() * reference.y() - forward.y() * reference.x(),
    )
    val rightLength = sqrt(rawRight.x() * rawRight.x() + rawRight.y() * rawRight.y() + rawRight.z() * rawRight.z())
    val right = Vec(rawRight.x() / rightLength, rawRight.y() / rightLength, rawRight.z() / rightLength)
    val up = Vec(
        forward.y() * right.z() - forward.z() * right.y(),
        forward.z() * right.x() - forward.x() * right.z(),
        forward.x() * right.y() - forward.y() * right.x(),
    )
    return Triple(forward, right, up)
}

private fun playSkillSound(
    player: net.minestom.server.entity.Player,
    key: String,
    point: Point,
    volume: Float,
    pitch: Float,
) {
    val soundEvent = SoundEvent.fromKey(Key.key("minecraft", key)) ?: return
    player.playSound(Sound.sound(soundEvent, Sound.Source.PLAYER, volume, pitch), point)
}

private fun playTwinBladesSounds(
    player: net.minestom.server.entity.Player,
    point: Point,
    cues: List<TwinBladesSoundCue>,
) {
    cues.forEach { cue ->
        playSkillSound(player, cue.key, point, cue.volume, cue.pitch)
    }
}

private fun sendSkillParticle(
    player: net.minestom.server.entity.Player,
    particle: Particle,
    point: Point,
) {
    player.sendPacket(ParticlePacket(particle, point.x(), point.y(), point.z(), 0f, 0f, 0f, 0f, 1))
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
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    when (attack) {
        FixedAttackType.SIDE_SWEEP -> {
            startParticlePreset(player, "projects:combat/shockwave_ring", scheduler, origin.add(0.0, 0.08, 0.0), Vec(0.0, 1.0, 0.0), manager, mapOf("radius" to 4.5, "duration" to 8.0))
        }
        FixedAttackType.FORWARD_SLAM -> {
            val center = origin.add(direction.x() * 3.0, 0.08, direction.z() * 3.0)
            startParticlePreset(player, "projects:combat/charge_inward", scheduler, center, Vec(0.0, 1.0, 0.0), manager, mapOf("radius" to 2.5, "duration" to 8.0))
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

private fun RiftExecutionerAttack.displayName(): String = when (this) {
    RiftExecutionerAttack.SECTOR_CLEAVE -> "Sector Cleave"
    RiftExecutionerAttack.FORWARD_SLAM -> "Forward Slam"
    RiftExecutionerAttack.CHAIN_DASH -> "Chain Dash"
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

/** Resolves Ronin's blink against the same simple player-volume block sweep as dodge. */
private fun resolveRoninBlinkEnd(
    instance: Instance,
    start: Pos,
    direction: Vec,
    distance: Double,
): Pos {
    val target = start.add(direction.x() * distance, 0.0, direction.z() * distance)
    if (isDodgePathClear(instance, start, target)) return target
    var safeProgress = 0.0
    var blockedProgress = 1.0
    repeat(10) {
        val progress = (safeProgress + blockedProgress) / 2.0
        val sample = start.add(
            (target.x() - start.x()) * progress,
            0.0,
            (target.z() - start.z()) * progress,
        )
        if (isDodgePathClear(instance, start, sample)) safeProgress = progress else blockedProgress = progress
    }
    return start.add(
        (target.x() - start.x()) * safeProgress,
        0.0,
        (target.z() - start.z()) * safeProgress,
    )
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
