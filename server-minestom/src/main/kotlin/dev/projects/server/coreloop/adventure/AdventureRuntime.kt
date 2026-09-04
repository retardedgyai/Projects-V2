package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.QuestCombatPlacement
import dev.projects.server.mob.QuestCombatEncounter
import dev.projects.server.mob.QuestEncounterCombat
import dev.projects.server.mob.QuestMobArchetype
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

enum class AdventureKind { RIFT, RITUAL }
enum class AdventurePhase { READY, FIGHTING, TRAVEL, DECISION, COMPLETED, FAILED, DISPOSED }

data class AdventureSite(val kind: AdventureKind, val sourceId: String, val centers: List<Pos>) {
    init {
        require(sourceId.isNotBlank())
        require(centers.size == if (kind == AdventureKind.RIFT) 3 else 1)
    }
}
data class AdventureReward(val kind: AdventureKind, val sourceId: String, val rewardCount: Int)
data class AdventureSnapshot(val sourceId: String, val kind: AdventureKind, val phase: AdventurePhase,
    val wave: Int, val aliveEnemies: Int, val position: Pos, val availableReward: Int)

/** The two event rules have explicit terminal states; timer/UI callbacks cannot settle a reward twice. */
internal class AdventureProgress(val kind: AdventureKind) {
    var phase = AdventurePhase.READY
        private set
    var wave = 0
        private set
    val availableReward: Int get() = if (kind == AdventureKind.RIFT) 3 else when (wave) { 1 -> 1; 2 -> 3; 3 -> 6; else -> 0 }
    val terminal: Boolean get() = phase in setOf(AdventurePhase.COMPLETED, AdventurePhase.FAILED, AdventurePhase.DISPOSED)
    fun start(): Boolean {
        if (phase !in setOf(AdventurePhase.READY, AdventurePhase.TRAVEL, AdventurePhase.DECISION)) return false
        wave++
        phase = AdventurePhase.FIGHTING
        return true
    }
    fun clearWave(): Int? {
        if (phase != AdventurePhase.FIGHTING) return null
        if (wave == 3) { phase = AdventurePhase.COMPLETED; return availableReward }
        phase = if (kind == AdventureKind.RIFT) AdventurePhase.TRAVEL else AdventurePhase.DECISION
        return null
    }
    fun claim(): Int? {
        if (phase != AdventurePhase.DECISION || kind != AdventureKind.RITUAL) return null
        phase = AdventurePhase.COMPLETED
        return availableReward
    }
    fun fail() { if (!terminal) phase = AdventurePhase.FAILED }
    fun dispose() { if (!terminal) phase = AdventurePhase.DISPOSED }
}

/** One map owns this runtime and its markers/wave IDs. It never issues currency or alters the terrain. */
class AdventureRuntime(
    private val instance: InstanceContainer,
    private val combat: QuestEncounterCombat,
    sites: List<AdventureSite>,
    private val canParticipate: (Player) -> Boolean,
    private val onReward: (AdventureReward) -> Unit,
) {
    private class Activity(val site: AdventureSite) {
        val progress = AdventureProgress(site.kind)
        var player: Player? = null
        var enemies = emptySet<UUID>()
        val allEnemies = mutableSetOf<UUID>()
        lateinit var interaction: Entity
        lateinit var objectDisplay: Entity
        lateinit var text: Entity
        var center = site.centers.first()
        var previousCenter: Pos? = null
        var deadline = Long.MAX_VALUE
        var outsideSince: Long? = null
    }
    private val activities = sites.map(::Activity)
    private var disposed = false
    private var nextVisualAt = 0L

    init {
        require(sites.map { it.sourceId }.distinct().size == sites.size)
        activities.forEach(::createMarker)
    }

    fun snapshots(): List<AdventureSnapshot> = activities.map {
        AdventureSnapshot(it.site.sourceId, it.site.kind, it.progress.phase, it.progress.wave,
            it.enemies.count(combat::isAlive), it.center, it.progress.availableReward)
    }
    fun markerEntities(): List<Entity> = if (disposed) emptyList() else activities.map { it.interaction }

    /** Root routes an actual PlayerEntityInteract target here before gathering/cache interactions. */
    fun interact(player: Player, target: Entity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (disposed) return false
        val activity = activities.firstOrNull { it.interaction.uuid == target.uuid } ?: return false
        if (player.instance !== instance || !canParticipate(player) || player.isDead ||
            player.position.distanceSquared(activity.center) > 4.5 * 4.5) return true
        if (activity.progress.terminal) return true
        if (activity.player != null && activity.player !== player) return true
        if (activities.any { it !== activity && it.player === player && !it.progress.terminal }) {
            player.sendMessage(Component.text("進行中の寄り道を終えてから起動してください。", NamedTextColor.YELLOW))
            return true
        }
        if (activity.progress.phase == AdventurePhase.DECISION && player.isSneaking) {
            activity.progress.claim()?.let { settle(activity, it) }
            return true
        }
        if (!activity.progress.start()) return true
        activity.player = player
        activity.deadline = nowMillis + 90_000L
        activity.outsideSince = null
        try {
            val types = when (activity.progress.wave) {
                1 -> listOf(QuestMobArchetype.SOLDIER, QuestMobArchetype.SOLDIER, QuestMobArchetype.RIFT_CASTER)
                2 -> listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SOLDIER)
                else -> listOf(QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.SOLDIER)
            }
            val offsets = listOf(-3.0 to -2.0, 3.0 to -2.0, -3.0 to 2.0, 3.0 to 2.0)
            val positions = offsets.take(types.size).map { (x, z) -> QuestCombatPlacement.resolve(instance, activity.center.add(x, 0.0, z)) }
            if (positions.distinct().size != types.size) error("The activity plaza has insufficient safe spawn positions")
            activity.enemies = combat.spawnEncounter(QuestCombatEncounter(positions, types))
            activity.allEnemies += activity.enemies
            player.sendMessage(Component.text("${title(activity)} 第${activity.progress.wave}波・敵を倒せ！", NamedTextColor.GOLD))
            updateText(activity)
        } catch (_: IllegalStateException) {
            fail(activity, "敵を安全に配置できないため、この寄り道を中止しました。")
        }
        return true
    }

    fun tick(nowMillis: Long) {
        if (disposed) return
        for (activity in activities) {
            val progress = activity.progress
            if (progress.terminal || progress.phase == AdventurePhase.READY) continue
            val player = activity.player
            if (player == null || player.instance !== instance || player.isDead || !canParticipate(player)) {
                fail(activity, "寄り道失敗・探索者が離脱しました。")
                continue
            }
            if (nowMillis >= activity.deadline) {
                fail(activity, "寄り道失敗・制限時間を超えました。")
                continue
            }
            if (progress.phase == AdventurePhase.FIGHTING) {
                val radius = if (activity.site.kind == AdventureKind.RITUAL) 10.0 else 24.0
                if (player.position.distanceSquared(activity.center) > radius * radius) {
                    if (activity.outsideSince == null) {
                        activity.outsideSince = nowMillis
                        player.sendMessage(Component.text("5秒以内に${if (activity.site.kind == AdventureKind.RITUAL) "祭域" else "亀裂"}へ戻ってください！", NamedTextColor.RED))
                    } else if (nowMillis - checkNotNull(activity.outsideSince) >= 5000L) {
                        fail(activity, "寄り道失敗・戦闘地域から離れました。")
                        continue
                    }
                } else activity.outsideSince = null
                if (activity.enemies.isNotEmpty() && activity.enemies.none(combat::isAlive)) {
                    combat.removeEncounter(activity.enemies)
                    activity.enemies = emptySet()
                    val reward = progress.clearWave()
                    if (reward != null) { settle(activity, reward); continue }
                    activity.deadline = nowMillis + 120_000L
                    if (progress.phase == AdventurePhase.TRAVEL) {
                        activity.previousCenter = activity.center
                        activity.center = activity.site.centers[progress.wave]
                        moveMarker(activity)
                        player.sendMessage(Component.text("次の亀裂へ：X ${activity.center.blockX()} / Z ${activity.center.blockZ()}。右クリックで追撃。", NamedTextColor.LIGHT_PURPLE))
                    } else player.sendMessage(Component.text("祭儀の報酬 ${progress.availableReward}個：祭壇を右クリックで続行／しゃがみ右クリックで確定。", NamedTextColor.AQUA))
                    updateText(activity)
                }
            }
        }
        if (nowMillis >= nextVisualAt) {
            activities.forEach { activity -> updateText(activity); drawGuide(activity) }
            nextVisualAt = nowMillis + 500L
        }
    }

    fun failAll() { activities.forEach { fail(it, "寄り道は終了しました。") } }

    fun dispose() {
        if (disposed) return
        disposed = true
        activities.forEach {
            it.progress.dispose()
            combat.removeEncounter(it.allEnemies)
            listOf(it.interaction, it.objectDisplay, it.text).forEach(Entity::remove)
            it.player = null
        }
    }

    private fun settle(activity: Activity, count: Int) {
        combat.removeEncounter(activity.allEnemies)
        updateText(activity)
        activity.player?.sendMessage(Component.text("${title(activity)} 達成！ 専用オーブ $count 個の報酬確定。", NamedTextColor.GREEN))
        // State is already COMPLETED before root's durable, source-deduplicated reward queue is invoked.
        onReward(AdventureReward(activity.site.kind, activity.site.sourceId, count))
    }

    private fun fail(activity: Activity, message: String) {
        if (activity.progress.terminal) return
        activity.progress.fail()
        combat.removeEncounter(activity.allEnemies)
        activity.player?.sendMessage(Component.text(message, NamedTextColor.RED))
        updateText(activity)
    }

    private fun title(activity: Activity) = if (activity.site.kind == AdventureKind.RIFT) "裂界の亀裂" else "血誓の祭儀"

    private fun createMarker(activity: Activity) {
        activity.interaction = Entity(EntityType.INTERACTION).apply {
            setNoGravity(true)
            setHasPhysics(false)
            editEntityMeta(InteractionMeta::class.java) { it.width = 2.5f; it.height = 2.8f; it.response = true }
            setInstance(this@AdventureRuntime.instance, activity.center).join()
        }
        activity.objectDisplay = Entity(EntityType.BLOCK_DISPLAY).apply {
            setNoGravity(true)
            setHasPhysics(false)
            editEntityMeta(BlockDisplayMeta::class.java) {
                it.setBlockState(if (activity.site.kind == AdventureKind.RIFT) Block.CRYING_OBSIDIAN else Block.CHISELED_DEEPSLATE)
                it.scale = Vec(1.5, 1.7, 1.5)
                it.translation = Vec(-0.75, 0.0, -0.75)
                it.setBrightness(15, 15)
            }
            setInstance(this@AdventureRuntime.instance, activity.center).join()
        }
        activity.text = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true)
            setHasPhysics(false)
            editEntityMeta(TextDisplayMeta::class.java) {
                it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
                it.scale = Vec(0.8, 0.8, 0.8)
                it.backgroundColor = 0x60000000
                it.setShadow(true)
                it.setBrightness(15, 15)
            }
            setInstance(this@AdventureRuntime.instance, activity.center.add(0.0, 3.0, 0.0)).join()
        }
        updateText(activity)
    }

    private fun moveMarker(activity: Activity) {
        activity.interaction.teleport(activity.center)
        activity.objectDisplay.teleport(activity.center)
        activity.text.teleport(activity.center.add(0.0, 3.0, 0.0))
    }

    private fun updateText(activity: Activity) {
        val progress = activity.progress
        val detail = when (progress.phase) {
            AdventurePhase.READY -> "右クリックで起動・全3波"
            AdventurePhase.FIGHTING -> "第${progress.wave}/3波・残り ${activity.enemies.count(combat::isAlive)}体" + if (activity.outsideSince != null) "\n地域外！ 戻れ" else ""
            AdventurePhase.TRAVEL -> "次の亀裂 ${progress.wave + 1}/3・右クリックで追撃"
            AdventurePhase.DECISION -> "確定 ${progress.availableReward}個\n右クリック：続行／しゃがみ右クリック：確定"
            AdventurePhase.COMPLETED -> "達成・報酬確定済み"
            AdventurePhase.FAILED -> "失敗・再起動できません"
            AdventurePhase.DISPOSED -> "終了"
        }
        (activity.text.entityMeta as TextDisplayMeta).text = Component.text("${title(activity)}\n$detail",
            if (progress.phase == AdventurePhase.FAILED) NamedTextColor.RED else NamedTextColor.LIGHT_PURPLE)
    }

    private fun drawGuide(activity: Activity) {
        if (activity.progress.terminal) return
        if (activity.site.kind == AdventureKind.RITUAL) {
            for (n in 0 until 40) {
                val angle = n * Math.PI * 2.0 / 40.0
                particle(activity.center.add(cos(angle) * 10.0, 0.12, sin(angle) * 10.0), Particle.DUST.withColor(NamedTextColor.LIGHT_PURPLE).withScale(0.75f))
            }
        } else {
            for (y in 2..7) particle(activity.center.add(0.0, y.toDouble(), 0.0), Particle.END_ROD)
            if (activity.progress.phase == AdventurePhase.TRAVEL) activity.previousCenter?.let { start ->
                for (n in 0..40) particle(start.lerp(activity.center, n / 40.0).add(0.0, 0.25, 0.0), Particle.DUST.withColor(NamedTextColor.LIGHT_PURPLE).withScale(0.75f))
            }
        }
    }

    private fun particle(position: Pos, type: Particle) {
        val packet = ParticlePacket(type, position.x(), position.y(), position.z(), 0f, 0f, 0f, 0f, 1)
        instance.players.filter { it.position.distanceSquared(position) < 64.0 * 64.0 }.forEach { it.sendPacket(packet) }
    }
}
