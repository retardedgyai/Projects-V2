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
import dev.projects.protocol.SlashEditorParameters
import dev.projects.protocol.Skill3VfxTarget
import dev.projects.protocol.VfxEditorNotice
import dev.projects.protocol.VfxEditorOpen
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
import dev.projects.server.equipment.BaseStatRoll
import dev.projects.server.equipment.EquipmentCategory
import dev.projects.server.equipment.EquipmentItem
import dev.projects.server.equipment.EquipmentModSlot
import dev.projects.server.equipment.EquipmentRarity
import dev.projects.server.equipment.EquipmentSlot as ProjectSEquipmentSlot
import dev.projects.server.equipment.EquipmentTier
import dev.projects.server.equipment.toPresentationItemStack
import dev.projects.server.equipment.LootGenerator
import dev.projects.server.equipment.LootSource
import dev.projects.server.equipment.V0_LOOT_MOD_POOL
import dev.projects.server.equipment.V0_LOOT_PROFILES
import dev.projects.server.equipment.lootRewardSeed
import dev.projects.server.mod.AttackTag
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModRank
import dev.projects.server.mod.ModStackingLayer

private const val SERVER_ADDRESS = "127.0.0.1"
private const val SERVER_PORT = 25565
private const val DEFAULT_ATTACK_SPEED = 1.0
private val SUPPORTED_ATTACK_SPEEDS = setOf(1.0, 1.5, 2.0)
private const val DODGE_PLAYER_WIDTH = 0.6
private const val DODGE_PLAYER_HEIGHT = 1.8
private val TWIN_BLADES_AUTO_OFFHAND = Tag.Boolean("projects_twin_blades_auto_offhand").defaultValue(false)

private val TWIN_BLADES_DEFINITIONS = mapOf(
    "projects:keen-edge" to ModDefinition(
        modId = "projects:keen-edge",
        rank = ModRank.RANK_1,
        allowedSlots = setOf(ProjectSEquipmentSlot.WEAPON),
        requiredTags = setOf(AttackTag.MELEE),
        excludedTags = emptySet(),
        statId = "projects:physical-attack",
        minimumValue = 1.0,
        maximumValue = 3.0,
        stackingLayer = ModStackingLayer.BASE_FLAT,
        definitionRevision = 1,
    ),
)

private fun twinBladesItem(): EquipmentItem = EquipmentItem(
    itemId = "projects:twin-blades",
    category = EquipmentCategory.WEAPON,
    slot = ProjectSEquipmentSlot.WEAPON,
    tier = EquipmentTier.T1,
    itemLevel = 5,
    rarity = EquipmentRarity.UNCOMMON,
    baseStatRolls = listOf(BaseStatRoll("projects:physical-attack", 12.5)),
    modSlots = listOf(
        EquipmentModSlot(0, ModEntry("projects:keen-edge", ModRank.RANK_1, 2.5, 0, 1)),
        EquipmentModSlot.empty(1),
    ),
)

private fun twinBladesItemStack() = twinBladesItem().toPresentationItemStack(
    material = Material.IRON_SWORD,
    displayName = "Twin Blades",
    definitions = TWIN_BLADES_DEFINITIONS,
)

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
    val resourceSyncTicks = mutableMapOf<UUID, Int>()
    val lastSentCooldowns = mutableMapOf<UUID, SkillCooldowns>()
    val dodgeVelocityActive = mutableMapOf<UUID, Boolean>()
    val attackSpeeds = mutableMapOf<UUID, Double>()
    val twinBladesSwingAngles = mutableMapOf<UUID, Double>()
    val twinBladesComboStates = mutableMapOf<UUID, TwinBladesComboState>()
    val twinBladesAttackSteps = mutableMapOf<UUID, Int>()
    val prototypeBoss = PrototypeBossState()
    val lootGenerator = LootGenerator(V0_LOOT_MOD_POOL)
    val bossRewardedPlayers = mutableSetOf<UUID>()
    var bossRewardSequence = 0L
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
    val skill3FinisherBindingStore = Skill3SlashBindingStore(Path.of("config", "projects", "vfx-editor", "twin-blades-skill3-finisher-slash.json"))
    val slashPreviewHandles = mutableMapOf<UUID, ParticleEffectHandle>()

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
        instance.players.forEach { player ->
            player.setVelocity(Vec.ZERO)
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

    fun grantLoot(player: net.minestom.server.entity.Player, source: LootSource, seed: Long) {
        val drop = lootGenerator.generate(seed, V0_LOOT_PROFILES.getValue(source))
        val material = if (source == LootSource.RIFT_EXECUTIONER) Material.DIAMOND_SWORD else Material.IRON_SWORD
        player.inventory.addItemStack(drop.item.toPresentationItemStack(material, drop.displayName, TWIN_BLADES_DEFINITIONS + V0_LOOT_MOD_POOL.associateBy { it.modId }))
        player.sendMessage(Component.text("Loot: ${drop.displayName} [${drop.item.rarity.name.lowercase()}] (${drop.item.modSlots.size} MOD)"))
    }

    fun finishEncounter() {
        clearBossTelegraphs()
        riftExecutioner.reset()
        prototypeBoss.setBreakActive(false)
        stopPlayerActions()
        updateBossBar()
        val result = if (prototypeBoss.isVictory) "VICTORY" else "DEFEAT"
        val rewardSequence = if (prototypeBoss.isVictory) ++bossRewardSequence else bossRewardSequence
        instance.players.forEach {
            sendResourceSnapshot(it)
            it.sendMessage(Component.text(result))
            if (prototypeBoss.isVictory && bossRewardedPlayers.add(it.uuid)) {
                grantLoot(it, LootSource.RIFT_EXECUTIONER, lootRewardSeed(it.uuid, rewardSequence))
            }
        }
    }

    fun resetEncounter() {
        prototypeBoss.reset()
        bossRewardedPlayers.clear()
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
    MinecraftServer.getCommandManager().register(
        Command("lootdebug").apply {
            setDefaultExecutor { sender, _ ->
                val player = sender as? net.minestom.server.entity.Player ?: return@setDefaultExecutor
                grantLoot(player, LootSource.NORMAL_ENEMY, System.nanoTime())
            }
        },
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

    events.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
        event.player.respawnPoint = Pos(0.0, 41.0, 0.0)
    }
    events.addListener(PlayerSpawnEvent::class.java) { event ->
        prototypeBoss.registerPlayer(event.player.uuid)
        event.player.setHealth(prototypeBoss.playerMaxHealth.toFloat())
        val resources = classResources.getOrPut(event.player.uuid) { ClassResourceState() }
        val skill1 = skill1States.getOrPut(event.player.uuid) { Skill1State() }
        val skill2 = skill2States.getOrPut(event.player.uuid) { Skill2State() }
        val skill3 = skill3States.getOrPut(event.player.uuid) { Skill3State() }
        if (!event.isFirstSpawn) {
            resources.reset()
            skill1.reset()
            skill2.reset()
            skill3.reset()
        }
        resourceSyncTicks[event.player.uuid] = 0
        sendResourceSnapshot(event.player)
        updateBossBar()
        event.player.showBossBar(bossBar)
        if (event.isFirstSpawn) {
            attackSpeeds[event.player.uuid] = DEFAULT_ATTACK_SPEED
            twinBladesComboStates[event.player.uuid] = TwinBladesComboState()
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
                twinBladesItemStack(),
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
        particleAnimations.cancel(slashPreviewHandles.remove(playerId))
        particleAnimations.cancelFor(event.player)
        combatStates.remove(playerId)
        dodgeStates.remove(playerId)
        twinRodsAirStates.remove(playerId)
        classResources.remove(playerId)
        skill1States.remove(playerId)
        skill2States.remove(playerId)
        skill3States.remove(playerId)
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
            particleManager.flush()
        }
    }
    events.addListener(PlayerTickEvent::class.java) { event ->
        synchronizeTwinBladesOffhand(event.player)
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
                ) {
                    nextGroundTelegraphId++
                }
                if (!prototypeBoss.isEncounterRunning) {
                    finishEncounter()
                    return@addListener
                }
            }
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
                            skill3FinisherBindingStore,
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
                    dodge.request(
                        message,
                        canStart = !state.isAttacking,
                        facing = event.player.position.direction(),
                        startAllowed = {
                            canStartDodge(event.player.isOnGround, weaponFor(event.player))
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
                    if (!prototypeBoss.isActive) return@addListener
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
                    val store = when (message.target) {
                        Skill3VfxTarget.PULSE -> skill3SlashBindingStore
                        Skill3VfxTarget.FINISHER -> skill3FinisherBindingStore
                    }
                    event.player.sendPluginMessage(
                        PROJECTS_CHANNEL,
                        ProtocolCodec.encode(
                            if (store.save(message.parameters)) VfxEditorNotice("${if (message.target == Skill3VfxTarget.PULSE) "Skill3連撃" else "Skill3 Finisher"}へ適用しました")
                            else VfxEditorNotice("適用に失敗しました"),
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
            is CombatEvent.Active -> AttackDebugShape(
                kind = when (event.profile.weapon) {
                    WeaponType.TWIN_RODS -> AttackDebugShapeKind.TWIN_RODS
                    WeaponType.HEAVY_BLADE -> AttackDebugShapeKind.HEAVY_BLADE
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
                twinBladesItemStack().withTag(TWIN_BLADES_AUTO_OFFHAND, true),
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
) {
    val contact = twinBladesSkill3ContactPoint(dashOrigin, dashDirection, target.position, target.halfExtent)
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
        val sink = manager.sink(ParticleViewer(player.position, player), PlayerParticleSink(player), "skill3:slash")
        scheduler.start(
            SlashEditorPreview.createSkill3(player.position, direction, choreography.parameters, choreography.reverseDraw),
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
    bindingStore: Skill3SlashBindingStore,
    scheduler: ParticleAnimationScheduler,
    manager: ParticleManager,
) {
    val contact = target.position
    val visual = TwinBladesSkill3Visual()
    val authored = bindingStore.load()
    val started = if (authored == null) {
        startParticlePreset(
            player = player,
            id = "projects:class/twin_blades/skill3_finisher",
            scheduler = scheduler,
            origin = contact,
            direction = direction,
            manager = manager,
            values = mapOf("length" to visual.finisherLength, "duration" to visual.finisherDuration.toDouble()),
        )
    } else {
        val sink = manager.sink(ParticleViewer(player.position, player), PlayerParticleSink(player), "skill3:finisher-slash")
        scheduler.start(
            SlashEditorPreview.createSkill3(player.position, direction, authored),
            sink,
            id = "skill3:finisher-slash:${player.uuid}",
        )
        true
    }
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
