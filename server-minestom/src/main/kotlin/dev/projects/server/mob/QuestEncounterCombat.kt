package dev.projects.server.mob

import dev.projects.server.CombatTarget
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.MobMeta
import net.minestom.server.entity.metadata.monster.raider.SpellcasterIllagerMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

data class QuestCombatEncounter(
    val spawnPositions: List<Pos>,
    val archetypes: List<QuestMobArchetype> = emptyList(),
) {
    init {
        require(spawnPositions.size in 1..4)
        require(archetypes.isEmpty() || archetypes.size == spawnPositions.size)
        require(archetypes.none { it.rarity == QuestMobRarity.BOSS })
    }
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
    contentSeed: Long = 0L,
    explicitBossArchetype: QuestMobArchetype? = null,
    spawnBoss: Boolean = true,
    private val healthMultiplier: Double = 1.0,
    private val damageMultiplier: Double = 1.0,
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
        var warningRetryAt = 0L
        var returningSince = 0L
        var slowPercent = 0.0
        var slowedUntil = 0L
        var challengeStage = 0
        var guardianIds = emptySet<UUID>()
        @Volatile var spawnFailure: Throwable? = null
    }

    private val mobs = linkedMapOf<UUID, Mob>()
    private val telegraphs = MobGroundTelegraph(instance)
    @Volatile private var disposed = false
    private var bossSealed = false
    private var bossGateFraction = 0.0
    private var bossStaggerUntil = 0L
    private var actionsStoppedForReturn = false
    private var lastTickAt = Long.MIN_VALUE
    val totalEncounterCount: Int = encounters.size
    val defeatedMobCount: Int get() = mobs.values.count { !it.boss && it.life.phase == QuestMobPhase.DEAD }
    val clearedEncounterCount: Int
        get() = (0 until totalEncounterCount).count { index ->
            mobs.values.filter { it.encounterIndex == index }.all { it.life.phase == QuestMobPhase.DEAD }
        }
    val bossDefeated: Boolean get() = mobs.values.any { it.boss && it.life.phase == QuestMobPhase.DEAD }
    var latestDefeat: QuestMobDefeat? = null
        private set
    internal val groundDisplayCount: Int get() = telegraphs.displayCount

    init {
        require(tier in 1..4)
        require(healthMultiplier.isFinite() && healthMultiplier in .1..20.0 && damageMultiplier.isFinite() && damageMultiplier in .1..10.0)
        require(explicitBossArchetype == null || explicitBossArchetype.rarity == QuestMobRarity.BOSS)
        encounters.forEachIndexed { index, encounter ->
            val archetypes = encounter.archetypes.ifEmpty { QuestMobContent.composition(contentSeed, index, encounter.spawnPositions.size) }
            encounter.spawnPositions.zip(archetypes).forEach { (position, archetype) -> spawn(position, index, archetype) }
        }
        if (spawnBoss) spawn(bossPosition, -1, explicitBossArchetype ?: QuestMobContent.boss(contentSeed))
    }

    fun sealBoss(sealed: Boolean) {
        bossSealed = sealed
        if (sealed) mobs.values.filter { it.boss }.forEach { it.abilities.cancel(); telegraphs.clear(it.entity.uuid); stopNavigation(it) }
    }
    fun gateBossHealth(fraction: Double) { require(fraction in 0.0..1.0); bossGateFraction = fraction }
    fun staggerBoss(untilMillis: Long) { bossStaggerUntil = untilMillis }
    fun entities(): List<EntityCreature> = mobs.values.filter { it.life.isAlive }.map { it.entity }
    fun isAlive(targetId: UUID): Boolean = mobs[targetId]?.life?.isAlive == true
    fun spawnEncounter(encounter: QuestCombatEncounter): Set<UUID> {
        check(!disposed)
        val types = encounter.archetypes.ifEmpty { QuestMobContent.composition(0, mobs.size, encounter.spawnPositions.size) }
        return encounter.spawnPositions.zip(types).map { (position, type) -> spawn(position, -2, type) }.toSet()
    }
    /** Event cancellation never calls death/reward callbacks, including partially cleared waves. */
    fun removeEncounter(ids: Set<UUID>) {
        ids.mapNotNull(mobs::get).filter { !it.boss }.forEach { mob ->
            mob.abilities.cancel()
            telegraphs.clear(mob.entity.uuid)
            stopNavigation(mob)
            if (mob.life.isAlive) mob.life.phase = QuestMobPhase.DISPOSED
            mob.entity.remove()
        }
    }
    fun positionOf(targetId: UUID): Pos? = mobs[targetId]?.entity?.position
    fun isBoss(targetId: UUID): Boolean = mobs[targetId]?.boss == true
    fun bossHealth(): Double = mobs.values.firstOrNull { it.boss }?.life?.health ?: 0.0
    fun bossMaxHealth(): Double = mobs.values.firstOrNull { it.boss }?.life?.maximumHealth ?: 1.0
    fun bossName(): String = mobs.values.firstOrNull { it.boss }?.definition?.name ?: ""
    fun weaknessOf(targetId: UUID): String? = mobs[targetId]?.definition?.archetype?.weakness
    fun mobInfo(targetId: UUID): QuestMobInfo? = mobs[targetId]?.let {
        QuestMobInfo(targetId, it.definition.archetype, it.definition.archetype.rarity, tier,
            it.entity.position, it.life.health, it.life.maximumHealth)
    }

    /** Percent is a fraction: .25 means 25%. Bosses resist half the reduction; strongest duration wins. */
    fun applySlow(targetId: UUID, percent: Double, durationMillis: Long): Boolean {
        if (disposed || !percent.isFinite() || percent <= 0.0 || durationMillis <= 0L) return false
        val mob = mobs[targetId] ?: return false
        if (mob.guardianIds.any(::isAlive)) return false
        if (!mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING) return false
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        val effective = percent.coerceAtMost(0.65) * if (mob.boss) 0.5 else 1.0
        mob.slowPercent = maxOf(if (now < mob.slowedUntil) mob.slowPercent else 0.0, effective)
        mob.slowedUntil = maxOf(mob.slowedUntil, now + durationMillis.coerceAtMost(30_000L))
        updateMovementSpeed(mob, now)
        return true
    }

    fun combatTargets(): List<CombatTarget> = if (disposed) emptyList() else mobs.values
        .filter { it.life.isAlive && it.life.phase != QuestMobPhase.RETURNING && isSpawned(it) }
        .map { mob ->
            val scale = mob.definition.scale
            CombatTarget(mob.entity.uuid, mob.entity.position.add(0.0, 0.9 * scale, 0.0), Vec(0.35 * scale, 0.9 * scale, 0.35 * scale))
        }

    fun applyDamage(targetId: UUID, attacker: Player, amount: Double): Boolean {
        return applyDamageAmount(targetId, attacker, amount) != null
    }

    /** Damage feedback uses actual HP removed after guard and overkill, never a pre-mitigation guess. */
    fun applyDamageAmount(targetId: UUID, attacker: Player, amount: Double): Double? =
        damage(targetId, attacker, amount, effect = false)

    /** Only for an effect whose initial server hit was already validated (burn/chain), never packet input. */
    fun applyEffectDamage(targetId: UUID, attacker: Player, amount: Double): Boolean {
        return damage(targetId, attacker, amount, effect = true) != null
    }

    private fun damage(targetId: UUID, attacker: Player, amount: Double, effect: Boolean): Double? {
        if (disposed || attacker.instance !== instance || !canTarget(attacker)) return null
        val mob = mobs[targetId] ?: return null
        if ((mob.boss && bossSealed) || mob.guardianIds.any(::isAlive)) return null
        val range = if (effect) 24.0 else 8.0
        if (!isSpawned(mob) || attacker.position.distanceSquared(mob.entity.position) > range * range) return null
        if (!effect && !mob.entity.hasLineOfSight(attacker)) return null
        val guarded = !effect && guarding(mob) &&
            normalizeHorizontal(attacker.position.sub(mob.entity.position)).dot(normalizeHorizontal(mob.entity.position.direction())) >= 0.5
        val healthBefore = mob.life.health
        val mitigated = amount * if (guarded) mob.definition.frontalDamageMultiplier else 1.0
        val gated = if (mob.boss) minOf(mitigated, (mob.life.health - mob.life.maximumHealth * bossGateFraction).coerceAtLeast(0.0)) else mitigated
        if (!mob.life.damage(gated)) return null
        val applied = healthBefore - mob.life.health
        mob.lastAttacker = attacker
        if (!mob.life.isAlive) {
            mob.abilities.cancel()
            telegraphs.clear(mob.entity.uuid)
            castingPose(mob, false)
            stopNavigation(mob)
            sound(mob.entity.position, "minecraft:entity.${mob.definition.soundFamily}.death", 1.0f, if (mob.boss) 0.65f else 1.0f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.POOF, 8)
            latestDefeat = QuestMobDefeat("mob:${mob.entity.uuid}", mob.entity.uuid, mob.definition.archetype,
                mob.definition.archetype.rarity, mob.definition.dropKind, tier, mob.entity.position, attacker.uuid)
            mob.entity.kill()
            // Health is already terminal before this callback can re-enter the runtime.
            onMobDefeated(attacker, mob.boss)
        } else {
            mob.target = attacker
            if (mob.life.phase == QuestMobPhase.IDLE) mob.life.phase = QuestMobPhase.CHASING
            updateName(mob)
            sound(mob.entity.position, if (guarded) "minecraft:item.shield.block" else "minecraft:entity.${mob.definition.soundFamily}.hurt", 0.6f, 1.0f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.DAMAGE_INDICATOR, 4)
        }
        return applied
    }

    fun tick(nowMillis: Long) {
        if (disposed || nowMillis <= lastTickAt) return
        lastTickAt = nowMillis
        telegraphs.tick(nowMillis)
        val players = instance.players.filter { canTarget(it) }
        if (actionsStoppedForReturn) {
            if (players.isEmpty()) return
            actionsStoppedForReturn = false
        }
        for (mob in mobs.values.toList()) {
            mob.spawnFailure?.let { throw IllegalStateException("Quest mob failed to spawn at ${mob.home}", it) }
            if (!mob.life.isAlive || !isSpawned(mob)) continue
            tickMob(mob, players, nowMillis)
            if (actionsStoppedForReturn || disposed) return
        }
    }

    /** Root sets returning=true first. A failed save may resume later, but cannot resume an old hit frame. */
    fun stopActionsForReturn(nowMillis: Long = System.currentTimeMillis()) {
        if (disposed) return
        actionsStoppedForReturn = true
        mobs.values.filter { it.life.isAlive }.forEach { mob ->
            mob.abilities.cancel()
            telegraphs.clear(mob.entity.uuid)
            stopNavigation(mob)
            castingPose(mob, false)
            mob.entity.refreshActiveHand(false, true, false)
            mob.target = null
            if (mob.life.phase != QuestMobPhase.RETURNING) mob.life.phase = QuestMobPhase.IDLE
            mob.nextPathAt = nowMillis
            mob.nextWarningAt = nowMillis
            mob.warningRetryAt = maxOf(mob.warningRetryAt, nowMillis + 350L)
            updateName(mob)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        telegraphs.dispose()
        mobs.values.forEach { mob ->
            mob.abilities.cancel()
            stopNavigation(mob)
            mob.life.phase = QuestMobPhase.DISPOSED
            mob.target = null
            mob.lastAttacker = null
            mob.entity.remove()
        }
    }

    private fun spawn(position: Pos, encounterIndex: Int, archetype: QuestMobArchetype): UUID {
        val base = QuestMobContent.definition(tier, archetype)
        val definition = base.copy(maximumHealth = base.maximumHealth * healthMultiplier, abilities = base.abilities.map { it.copy(damage = it.damage * damageMultiplier) })
        val entity = EntityCreature(definition.entityType)
        entity.isInvulnerable = true
        entity.isCustomNameVisible = true
        entity.setCanPickupItem(false)
        (entity.entityMeta as MobMeta).isAggressive = false
        entity.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = definition.movementSpeed
        entity.getAttribute(Attribute.SCALE).baseValue = definition.scale
        entity.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(definition.weapon))
        entity.setEquipment(EquipmentSlot.OFF_HAND, ItemStack.of(definition.offhand))
        entity.setEquipment(EquipmentSlot.HELMET, ItemStack.of(definition.helmet))
        entity.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(definition.chestplate))
        val mob = Mob(entity, position, encounterIndex, definition.boss, definition)
        mobs[entity.uuid] = mob
        updateName(mob)
        entity.setInstance(instance, position).whenComplete { _, error ->
            if (error != null) mob.spawnFailure = error
            if (disposed) entity.remove()
        }
        return entity.uuid
    }

    private fun isSpawned(mob: Mob): Boolean = !mob.entity.isRemoved && mob.entity.instance === instance

    private fun tickMob(mob: Mob, players: List<Player>, now: Long) {
        if (mob.boss && (bossSealed || now < bossStaggerUntil)) { stopNavigation(mob); return }
        updateMovementSpeed(mob, now)
        mob.entity.refreshActiveHand(guarding(mob), true, false)
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
        if (tickChallengeBarrier(mob, now)) return
        if (mob.abilities.isActive) {
            for (event in mob.abilities.tick(now, target.position)) {
                if (!handleAbilityEvent(mob, event, players)) return
                if (disposed || !mob.life.isAlive) return
            }
            val frame = mob.abilities.current
            if (frame != null) {
                mob.entity.setView(Pos.ZERO.withDirection(frame.facing).yaw(), 0.0f)
                if (frame.phase != MobAbilityPhase.RECOVERY && now >= mob.nextWarningAt) {
                    if (!drawWarning(mob, frame, false)) {
                        cancelWarning(mob)
                    }
                    mob.nextWarningAt = now + 180L
                }
                return
            }
            mob.life.phase = QuestMobPhase.CHASING
        }
        if (now >= mob.warningRetryAt && mob.entity.hasLineOfSight(target) && telegraphs.canStart(mob.entity.uuid)) {
            val start = mob.abilities.tryStart(now, mob.entity.position, target.position, mob.life.health / mob.life.maximumHealth)
            if (start != null) {
                mob.life.phase = QuestMobPhase.ATTACKING
                stopNavigation(mob)
                handleAbilityEvent(mob, start, players)
                return
            }
        }
        mob.life.phase = QuestMobPhase.CHASING
        if (now >= mob.nextPathAt) {
            val distance = mob.definition.preferredDistance
            val retreat = if (distance >= 6.0 && mob.entity.position.distanceSquared(target.position) < 4.5 * 4.5) {
                val away = normalizeHorizontal(mob.entity.position.sub(target.position))
                safeRetreat(mob.entity.position.add(away.mul(3.0)), mob.home, mob.definition.leashRange)
            } else null
            mob.entity.navigator.setPathTo(retreat ?: target.position, if (retreat == null) distance else 0.6, null)
            mob.nextPathAt = now + 550L
        }
    }

    private fun startReturn(mob: Mob, now: Long) {
        removeEncounter(mob.guardianIds)
        mob.guardianIds = emptySet()
        mob.challengeStage = 0
        mob.abilities.cancel()
        telegraphs.clear(mob.entity.uuid)
        castingPose(mob, false)
        mob.slowedUntil = 0L
        mob.slowPercent = 0.0
        updateMovementSpeed(mob, now)
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

    private fun tickChallengeBarrier(mob: Mob, now: Long): Boolean {
        if (mob.definition.archetype != QuestMobArchetype.TEMPEST_HIEROPHANT) return false
        if (mob.guardianIds.any(::isAlive)) {
            stopNavigation(mob)
            updateName(mob, "障壁・護衛を倒せ")
            return true
        }
        val threshold = when (mob.challengeStage) { 0 -> 0.66; 1 -> 0.33; else -> return false }
        if (mob.life.health / mob.life.maximumHealth > threshold) return false
        mob.challengeStage++
        mob.abilities.cancel()
        telegraphs.clear(mob.entity.uuid)
        castingPose(mob, false)
        stopNavigation(mob)
        mob.guardianIds = spawnEncounter(QuestCombatEncounter(
            listOf(mob.home.add(-5.0, 0.0, 0.0), mob.home.add(5.0, 0.0, 0.0)),
            listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER)))
        mob.warningRetryAt = now + 800L
        updateName(mob, "障壁・護衛を倒せ")
        sound(mob.home, "minecraft:block.beacon.activate", 1f, 0.8f)
        return true
    }

    private fun guarding(mob: Mob): Boolean = mob.definition.frontalDamageMultiplier < 1.0 &&
        mob.life.phase in listOf(QuestMobPhase.IDLE, QuestMobPhase.CHASING)

    private fun castingPose(mob: Mob, active: Boolean) {
        (mob.entity.entityMeta as? SpellcasterIllagerMeta)?.spell =
            if (active) SpellcasterIllagerMeta.Spell.ATTACK else SpellcasterIllagerMeta.Spell.NONE
    }

    private fun cancelWarning(mob: Mob) {
        mob.abilities.cancel()
        telegraphs.clear(mob.entity.uuid)
        mob.life.phase = QuestMobPhase.CHASING
        mob.warningRetryAt = lastTickAt + 600L
        castingPose(mob, false)
        updateName(mob)
    }

    private fun updateMovementSpeed(mob: Mob, now: Long) {
        if (now >= mob.slowedUntil) mob.slowPercent = 0.0
        val speed = mob.definition.movementSpeed * (1.0 - mob.slowPercent)
        if (mob.entity.getAttribute(Attribute.MOVEMENT_SPEED).baseValue != speed) {
            mob.entity.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = speed
        }
    }

    private fun safeRetreat(position: Pos, home: Pos, leash: Double): Pos? {
        val ground = telegraphs.groundHeight(position) ?: return null
        val candidate = position.withY(ground)
        if (candidate.distanceSquared(home) > leash * leash) return null
        val x = floor(candidate.x()).toInt()
        val z = floor(candidate.z()).toInt()
        val y = floor(candidate.y()).toInt()
        return candidate.takeUnless { (y..y + 2).any { instance.getBlock(x, it, z).isSolid } }
    }

    private fun handleAbilityEvent(mob: Mob, event: MobAbilityEvent, players: List<Player>): Boolean {
        when (event) {
            is MobAbilityEvent.Started -> {
                updateName(mob, event.frame.ability.displayName)
                if (!drawWarning(mob, event.frame, false)) {
                    cancelWarning(mob)
                    return false
                }
                mob.entity.refreshActiveHand(false, true, false)
                castingPose(mob, true)
                sound(event.frame.origin, "minecraft:block.note_block.hat", 0.7f, 0.8f)
            }
            is MobAbilityEvent.Locked -> {
                if (!drawWarning(mob, event.frame, false)) { cancelWarning(mob); return false }
                sound(event.frame.origin, "minecraft:block.note_block.pling", 0.8f, 0.7f)
            }
            is MobAbilityEvent.Hit -> {
                mob.entity.swingMainHand()
                castingPose(mob, false)
                if (!drawWarning(mob, event.frame, true)) { cancelWarning(mob); return false }
                sound(event.frame.origin,
                    if (mob.entity.entityMeta is SpellcasterIllagerMeta) "minecraft:entity.evoker.cast_spell"
                    else if (event.frame.ability.shape is MobAttackShape.Sweep) "minecraft:entity.player.attack.sweep"
                    else "minecraft:block.anvil.land", 0.8f, if (mob.boss) 0.7f else 1.0f)
                for (player in players) {
                    if (player.instance !== instance || !canTarget(player)) continue
                    if (event.frame.ability.shape.contains(event.frame.origin, event.frame.facing, player.position) &&
                        mob.entity.hasLineOfSight(player)
                    ) damagePlayer(player, event.frame.ability.damage)
                    if (disposed || actionsStoppedForReturn) return false
                }
            }
            is MobAbilityEvent.Finished -> { telegraphs.clear(mob.entity.uuid); updateName(mob) }
        }
        return true
    }

    private fun updateName(mob: Mob, attackName: String? = null) {
        val weakness = when (mob.definition.archetype.weakness) { "fire" -> "炎"; "ice" -> "氷"; else -> "雷" }
        val base = "T$tier ${mob.definition.name}  ${ceil(mob.life.health).toInt()}/${ceil(mob.life.maximumHealth).toInt()}  弱点:$weakness"
        val suffix = when {
            mob.life.phase == QuestMobPhase.RETURNING -> "  帰還中"
            attackName != null -> "  $attackName"
            else -> ""
        }
        val color = when (mob.definition.archetype.rarity) {
            QuestMobRarity.BOSS -> NamedTextColor.RED
            QuestMobRarity.ELITE -> NamedTextColor.LIGHT_PURPLE
            QuestMobRarity.NORMAL -> NamedTextColor.GOLD
        }
        mob.entity.customName = Component.text(base + suffix, color)
    }

    private fun drawWarning(mob: Mob, frame: MobAbilityFrame, impact: Boolean): Boolean {
        if (!telegraphs.show(mob.entity.uuid, frame, impact, lastTickAt)) return false
        val color = when {
            impact -> NamedTextColor.RED
            frame.phase == MobAbilityPhase.LOCKED -> NamedTextColor.DARK_RED
            else -> NamedTextColor.RED
        }
        val dust = Particle.DUST.withColor(color).withScale(if (impact) 1.5f else 1.0f)
        for (point in frame.ability.shape.outline(frame.origin, frame.facing)) {
            telegraphs.groundHeight(point)?.let { particle(point.withY(it + 0.075), dust) }
        }
        return true
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
