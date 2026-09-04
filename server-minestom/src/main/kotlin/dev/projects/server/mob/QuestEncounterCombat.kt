package dev.projects.server.mob

import dev.projects.server.CombatTarget
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.MobMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

data class QuestCombatEncounter(val spawnPositions: List<Pos>) {
    init { require(spawnPositions.size in 1..4) }
}

internal enum class QuestMobPhase { IDLE, CHASING, ATTACKING, RETURNING, DEAD, DISPOSED }

/** The logical health gate remains independent of entity hurt animations and callback re-entry. */
internal class QuestMobLife(val maximumHealth: Double) {
    init { require(maximumHealth > 0.0 && maximumHealth.isFinite()) }
    var health: Double = maximumHealth
        private set
    var phase: QuestMobPhase = QuestMobPhase.IDLE
    val isAlive: Boolean get() = phase != QuestMobPhase.DEAD && phase != QuestMobPhase.DISPOSED

    fun damage(amount: Double): Boolean {
        if (!isAlive || phase == QuestMobPhase.RETURNING || !amount.isFinite() || amount <= 0.0) return false
        health = (health - amount).coerceAtLeast(0.0)
        if (health == 0.0) phase = QuestMobPhase.DEAD
        return true
    }

    fun finishReturn() {
        if (!isAlive) return
        health = maximumHealth
        phase = QuestMobPhase.IDLE
    }
}

private data class QuestMobDefinition(
    val name: String,
    val maximumHealth: Double,
    val movementSpeed: Double,
    val activationRange: Double,
    val leashRange: Double,
    val abilities: List<MobAbility>,
) {
    companion object {
        fun forTier(tier: Int, boss: Boolean): QuestMobDefinition {
            val multiplier = 1.65.pow(tier - 1)
            return QuestMobDefinition(
                name = if (boss) "裂け目の執行官" else "追放兵",
                maximumHealth = (if (boss) 300.0 else 44.0) * multiplier,
                movementSpeed = if (boss) 0.12 else 0.14,
                activationRange = if (boss) 28.0 else 20.0,
                leashRange = if (boss) 38.0 else 28.0,
                abilities = listOf(
                    MobAbility(
                        "sweep", "横薙ぎ", MobAttackShape.Sweep(if (boss) 4.2 else 3.4),
                        maximumStartRange = if (boss) 3.8 else 3.0,
                        damage = (if (boss) 16.0 else 10.0) * multiplier,
                        telegraphMillis = 1000L, trackingMillis = 400L,
                        recoveryMillis = 850L, cooldownMillis = 1900L, weight = 3,
                    ),
                    MobAbility(
                        "slam", "前方叩きつけ", MobAttackShape.Slam(if (boss) 7.0 else 5.8, if (boss) 1.6 else 1.25),
                        maximumStartRange = if (boss) 6.6 else 5.4,
                        damage = (if (boss) 24.0 else 14.0) * multiplier,
                        telegraphMillis = 1450L, trackingMillis = 550L,
                        recoveryMillis = 1400L, cooldownMillis = 3000L, weight = 2,
                    ),
                ),
            )
        }
    }
}

/**
 * One map owns one instance of this runtime. Invoke tick on the map's server tick, and dispose before unloading it.
 * Player attack packets never enter this class: applyDamage accepts targets already resolved by server hit shapes.
 */
class QuestEncounterCombat(
    private val instance: InstanceContainer,
    private val tier: Int,
    encounters: List<QuestCombatEncounter>,
    bossPosition: Pos,
    private val onMobDefeated: (killer: Player?, boss: Boolean) -> Unit,
    private val damagePlayer: (Player, Double) -> Unit,
    private val canTarget: (Player) -> Boolean = {
        !it.isDead && it.gameMode != GameMode.SPECTATOR && it.gameMode != GameMode.CREATIVE
    },
) {
    private class Mob(
        val entity: EntityCreature,
        val home: Pos,
        val encounterIndex: Int,
        val boss: Boolean,
        val definition: QuestMobDefinition,
    ) {
        val life = QuestMobLife(definition.maximumHealth)
        val abilities = MobAbilityManager(definition.abilities, Random(entity.uuid.leastSignificantBits))
        var target: Player? = null
        var lastAttacker: Player? = null
        var nextPathAt = 0L
        var nextWarningAt = 0L
        var returningSince = 0L
        @Volatile var spawnFailure: Throwable? = null
    }

    private val mobs = linkedMapOf<UUID, Mob>()
    @Volatile private var disposed = false
    private var lastTickAt = Long.MIN_VALUE
    val totalEncounterCount: Int = encounters.size
    val defeatedMobCount: Int get() = mobs.values.count { !it.boss && it.life.phase == QuestMobPhase.DEAD }
    val clearedEncounterCount: Int
        get() = (0 until totalEncounterCount).count { index ->
            mobs.values.filter { it.encounterIndex == index }.all { it.life.phase == QuestMobPhase.DEAD }
        }
    val bossDefeated: Boolean get() = mobs.values.any { it.boss && it.life.phase == QuestMobPhase.DEAD }

    init {
        require(tier in 1..4)
        encounters.forEachIndexed { index, encounter ->
            encounter.spawnPositions.forEach { spawn(it, index, false) }
        }
        spawn(bossPosition, -1, true)
    }

    fun entities(): List<EntityCreature> = mobs.values.filter { it.life.isAlive }.map { it.entity }
    fun positionOf(targetId: UUID): Pos? = mobs[targetId]?.entity?.position
    fun isBoss(targetId: UUID): Boolean = mobs[targetId]?.boss == true
    fun bossHealth(): Double = mobs.values.first { it.boss }.life.health
    fun bossMaxHealth(): Double = mobs.values.first { it.boss }.life.maximumHealth

    fun combatTargets(): List<CombatTarget> = if (disposed) emptyList() else mobs.values
        .filter { it.life.isAlive && it.life.phase != QuestMobPhase.RETURNING && isSpawned(it) }
        .map { mob ->
            CombatTarget(mob.entity.uuid, mob.entity.position.add(0.0, 0.9, 0.0), Vec(0.35, 0.9, 0.35))
        }

    fun applyDamage(targetId: UUID, attacker: Player, amount: Double): Boolean {
        if (disposed || attacker.instance !== instance || !canTarget(attacker)) return false
        val mob = mobs[targetId] ?: return false
        if (!isSpawned(mob) || attacker.position.distanceSquared(mob.entity.position) > 8.0 * 8.0) return false
        if (!mob.entity.hasLineOfSight(attacker)) return false
        if (!mob.life.damage(amount)) return false
        mob.lastAttacker = attacker
        if (!mob.life.isAlive) {
            mob.abilities.cancel()
            stopNavigation(mob)
            sound(mob.entity.position, "minecraft:entity.vindicator.death", 1.0f, if (mob.boss) 0.65f else 1.0f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.POOF, 8)
            mob.entity.kill()
            // Health is already terminal before this callback can re-enter the runtime.
            onMobDefeated(attacker, mob.boss)
        } else {
            mob.target = attacker
            if (mob.life.phase == QuestMobPhase.IDLE) mob.life.phase = QuestMobPhase.CHASING
            updateName(mob)
            sound(mob.entity.position, "minecraft:entity.vindicator.hurt", 0.6f, 1.0f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.DAMAGE_INDICATOR, 4)
        }
        return true
    }

    fun tick(nowMillis: Long) {
        if (disposed || nowMillis <= lastTickAt) return
        lastTickAt = nowMillis
        val players = instance.players.filter { canTarget(it) }
        for (mob in mobs.values) {
            mob.spawnFailure?.let { throw IllegalStateException("Quest mob failed to spawn at ${mob.home}", it) }
            if (!mob.life.isAlive || !isSpawned(mob)) continue
            tickMob(mob, players, nowMillis)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        mobs.values.forEach { mob ->
            mob.abilities.cancel()
            stopNavigation(mob)
            mob.life.phase = QuestMobPhase.DISPOSED
            mob.target = null
            mob.lastAttacker = null
            mob.entity.remove()
        }
    }

    private fun spawn(position: Pos, encounterIndex: Int, boss: Boolean) {
        val definition = QuestMobDefinition.forTier(tier, boss)
        val entity = EntityCreature(EntityType.VINDICATOR)
        entity.isInvulnerable = true
        entity.isCustomNameVisible = true
        entity.setCanPickupItem(false)
        (entity.entityMeta as MobMeta).isAggressive = false
        entity.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = definition.movementSpeed
        if (boss) entity.getAttribute(Attribute.SCALE).baseValue = 1.35
        entity.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(if (boss) Material.NETHERITE_AXE else Material.IRON_AXE))
        val mob = Mob(entity, position, encounterIndex, boss, definition)
        mobs[entity.uuid] = mob
        updateName(mob)
        entity.setInstance(instance, position).whenComplete { _, error ->
            if (error != null) mob.spawnFailure = error
            if (disposed) entity.remove()
        }
    }

    private fun isSpawned(mob: Mob): Boolean = !mob.entity.isRemoved && mob.entity.instance === instance

    private fun tickMob(mob: Mob, players: List<Player>, now: Long) {
        if (mob.life.phase == QuestMobPhase.RETURNING) {
            if (mob.entity.position.distanceSquared(mob.home) < 1.5 * 1.5 || now - mob.returningSince > 6000L) {
                // Failed paths cannot leave a damaged, invulnerable encounter stranded forever.
                mob.entity.teleport(mob.home)
                stopNavigation(mob)
                mob.abilities.reset()
                mob.life.finishReturn()
                (mob.entity.entityMeta as MobMeta).isAggressive = false
                updateName(mob)
            } else if (now >= mob.nextPathAt) {
                mob.entity.navigator.setPathTo(mob.home)
                mob.nextPathAt = now + 800L
            }
            return
        }
        val leashSquared = mob.definition.leashRange * mob.definition.leashRange
        if (mob.entity.position.distanceSquared(mob.home) > leashSquared) {
            startReturn(mob, now)
            return
        }
        var target = mob.target?.takeIf { player ->
            player in players && player.instance === instance && player.position.distanceSquared(mob.home) <= leashSquared
        }
        if (target == null) {
            target = players.asSequence()
                .filter { it.position.distanceSquared(mob.home) <= leashSquared }
                .filter { it.position.distanceSquared(mob.entity.position) <= mob.definition.activationRange.pow(2) }
                .filter { mob.entity.hasLineOfSight(it) }
                .minByOrNull { it.position.distanceSquared(mob.entity.position) }
            mob.target = target
        }
        if (target == null) {
            if (mob.life.phase != QuestMobPhase.IDLE) startReturn(mob, now)
            return
        }
        (mob.entity.entityMeta as MobMeta).isAggressive = true
        if (mob.abilities.isActive) {
            for (event in mob.abilities.tick(now, target.position)) {
                handleAbilityEvent(mob, event, players)
                if (disposed || !mob.life.isAlive) return
            }
            val frame = mob.abilities.current
            if (frame != null) {
                mob.entity.setView(Pos.ZERO.withDirection(frame.facing).yaw(), 0.0f)
                if (frame.phase != MobAbilityPhase.RECOVERY && now >= mob.nextWarningAt) {
                    drawWarning(frame, false)
                    mob.nextWarningAt = now + 180L
                }
                return
            }
            mob.life.phase = QuestMobPhase.CHASING
        }
        if (mob.entity.hasLineOfSight(target)) {
            val start = mob.abilities.tryStart(now, mob.entity.position, target.position)
            if (start != null) {
                mob.life.phase = QuestMobPhase.ATTACKING
                stopNavigation(mob)
                handleAbilityEvent(mob, start, players)
                return
            }
        }
        mob.life.phase = QuestMobPhase.CHASING
        if (now >= mob.nextPathAt) {
            mob.entity.navigator.setPathTo(target.position, 2.0, null)
            mob.nextPathAt = now + 550L
        }
    }

    private fun startReturn(mob: Mob, now: Long) {
        mob.abilities.cancel()
        stopNavigation(mob)
        mob.target = null
        (mob.entity.entityMeta as MobMeta).isAggressive = false
        mob.life.phase = QuestMobPhase.RETURNING
        mob.returningSince = now
        mob.nextPathAt = now
        updateName(mob)
    }

    private fun stopNavigation(mob: Mob) {
        mob.entity.navigator.reset()
        mob.entity.velocity = Vec(0.0, mob.entity.velocity.y(), 0.0)
    }

    private fun handleAbilityEvent(mob: Mob, event: MobAbilityEvent, players: List<Player>) {
        when (event) {
            is MobAbilityEvent.Started -> {
                updateName(mob, event.frame.ability.displayName)
                drawWarning(event.frame, false)
                sound(event.frame.origin, "minecraft:block.note_block.hat", 0.7f, 0.8f)
            }
            is MobAbilityEvent.Locked -> {
                drawWarning(event.frame, false)
                sound(event.frame.origin, "minecraft:block.note_block.pling", 0.8f, 0.7f)
            }
            is MobAbilityEvent.Hit -> {
                mob.entity.swingMainHand()
                drawWarning(event.frame, true)
                sound(event.frame.origin,
                    if (event.frame.ability.shape is MobAttackShape.Sweep) "minecraft:entity.player.attack.sweep"
                    else "minecraft:block.anvil.land", 0.8f, if (mob.boss) 0.7f else 1.0f)
                for (player in players) {
                    if (player.instance !== instance || !canTarget(player)) continue
                    if (event.frame.ability.shape.contains(event.frame.origin, event.frame.facing, player.position) &&
                        mob.entity.hasLineOfSight(player)
                    ) damagePlayer(player, event.frame.ability.damage)
                    if (disposed) return
                }
            }
            is MobAbilityEvent.Finished -> updateName(mob)
        }
    }

    private fun updateName(mob: Mob, attackName: String? = null) {
        val base = "T$tier ${mob.definition.name}  ${ceil(mob.life.health).toInt()}/${ceil(mob.life.maximumHealth).toInt()}"
        val suffix = when {
            mob.life.phase == QuestMobPhase.RETURNING -> "  帰還中"
            attackName != null -> "  $attackName"
            else -> ""
        }
        mob.entity.customName = Component.text(base + suffix, if (mob.boss) NamedTextColor.RED else NamedTextColor.GOLD)
    }

    private fun drawWarning(frame: MobAbilityFrame, impact: Boolean) {
        val color = when {
            impact -> NamedTextColor.WHITE
            frame.phase == MobAbilityPhase.LOCKED -> NamedTextColor.RED
            else -> NamedTextColor.GOLD
        }
        val dust = Particle.DUST.withColor(color).withScale(if (impact) 1.5f else 1.0f)
        for (point in frame.ability.shape.outline(frame.origin, frame.facing)) {
            particle(projectGround(point), dust)
        }
    }

    private fun projectGround(point: Pos): Pos {
        val x = floor(point.x()).toInt()
        val z = floor(point.z()).toInt()
        val baseY = floor(point.y()).toInt()
        for (y in baseY + 1 downTo baseY - 3) {
            if (instance.getBlock(x, y, z).isSolid && !instance.getBlock(x, y + 1, z).isSolid) {
                return point.withY(y + 1.1)
            }
        }
        return point
    }

    private fun particle(position: Pos, type: Particle, count: Int = 1) {
        val packet = ParticlePacket(type, position.x(), position.y(), position.z(), 0f, 0f, 0f, 0f, count)
        instance.players.filter { it.position.distanceSquared(position) < 56.0 * 56.0 }.forEach { it.sendPacket(packet) }
    }

    private fun sound(position: Pos, key: String, volume: Float, pitch: Float) {
        val sound = Sound.sound(net.kyori.adventure.key.Key.key(key), Sound.Source.HOSTILE, volume, pitch)
        instance.players.filter { it.position.distanceSquared(position) < 40.0 * 40.0 }
            .forEach { it.playSound(sound, position) }
    }
}
