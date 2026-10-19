package dev.projects.server.mob

import dev.projects.server.coreloop.CoreDamageType
import dev.projects.server.coreloop.CoreCombatMath
import dev.projects.server.coreloop.CoreAffixStats
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

    fun heal(amount: Double) {
        if (!isAlive || phase == QuestMobPhase.RETURNING || !amount.isFinite() || amount <= 0.0) return
        health = (health + amount).coerceAtMost(maximumHealth)
    }

    fun finishReturn() {
        if (!isAlive) return
        health = maximumHealth
        phase = QuestMobPhase.IDLE
    }
}

/** How long an enraging boss holds its roar before attacking again (matches the `enrage` model clip). */
private const val ENRAGE_HOLD_MILLIS = 5000L

internal enum class ManeuverKind { STRAFE, HOP_BACK, DASH }

/** The carrier is moved from [from] to [to] between [moveFrom] and [moveTo] (hop/dash); a strafe paths instead. */
internal class Maneuver(val kind: ManeuverKind, val from: Pos, val to: Pos, val moveFrom: Long, val moveTo: Long, val endAt: Long, val sign: Int)

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
    private val encounterLevel: Int = (tier - 1) * 10 + 1,
    private val typedDamagePlayer: ((Player, Double, CoreDamageType) -> Unit)? = null,
    /** Roll a grade (等級) and MODs for every non-boss mob; off for labs and fixed-balance tests. */
    private val rollGrades: Boolean = false,
) {
    private data class SlowKey(val source: UUID?, val strength: Double)
    private data class Slow(val strength: Double, val until: Long)
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
        val slows = mutableMapOf<SlowKey, Slow>()
        var challengeStage = 0
        var exposedUntil = 0L
        var tauntedUntil = 0L
        var pullImmuneUntil = 0L
        /** Expiry per active [CrowdControl]; never set on bosses. */
        val crowd = mutableMapOf<CrowdControl, Long>()
        /** Where charm pulls toward and fear runs from. */
        var crowdSource: Pos? = null
        var grade = MobGrade.COMMON
        var mods: List<MobMod> = emptyList()
        /** Last time a player's hit landed (再生 waits for a quiet spell). */
        var hurtAt = 0L
        var lastRegenAt = 0L
        /** The attack being prepared (or a boss's gimmick notice), kept between plate refreshes. */
        var notice: String? = null
        /** Bosses and graded mobs from 精鋭 up: crowd control wears this down; at 0 the mob is broken until [brokenUntil]. */
        var breakGauge = BREAK_MAX
        var brokenUntil = 0L
        var guardianIds = emptySet<UUID>()
        @Volatile var spawnFailure: Throwable? = null
        @Volatile var model: WardenModel? = null
        /** Blockbench (WSEE) body, created after the carrier entity is in the instance. */
        @Volatile var view: BossModelView? = null
        var enraged = false
        var enrageHoldUntil = 0L
        /** Model bosses: a move round the target between attacks (see [startManeuver]). */
        var maneuver: Maneuver? = null
        var nextManeuverAt = 0L
        var maneuvers = 0
        /** Where the model looks while it moves sideways or backward: at its target. */
        var faceYaw = 0f
        /** Since when the target has stood behind the model (0 = not behind); see [TURN_AFTER_MILLIS]. */
        var behindSince = 0L
        /** An ability to follow the maneuver that just ended (hop back -> breath, dash -> sweep). */
        var combo: String? = null
        /** Where an ability that carries the body (the dive) took off from. */
        var travelFrom: Pos? = null
        /** Lost its target (far or out of sight): it creeps and searches. */
        var searching = false
        /** Enraged parts (gimmicks): damage dealt near the head and on each side, and what has broken. 0 = its right fan. */
        var hornDamage = 0.0
        var hornsBroken = false
        /** The enrage roar's wave (see [tickRageWave]): when it left the wyrm and from where; 0 = not yet. */
        var waveAt = 0L
        var waveFrom: Pos? = null
        var sloppyUntil = 0L
        val fanDamage = DoubleArray(2)
        val fanTorn = BooleanArray(2)
    }

    private val mobs = linkedMapOf<UUID, Mob>()
    private val gradeSeed = contentSeed
    private val plates = MobPlates()
    private var plateRefreshAt = 0L
    private val telegraphs = MobGroundTelegraph(instance)
    private val vfx = dev.projects.server.particle.ParticleAnimationScheduler()
    private val vfxHandles = mutableMapOf<UUID, dev.projects.server.particle.ParticleEffectHandle>()
    private val vfxSink = dev.projects.server.particle.ParticleSink { spawn ->
        val packet = ParticlePacket(spawn.particle, spawn.position.x(), spawn.position.y(), spawn.position.z(),
            spawn.offset.x().toFloat(), spawn.offset.y().toFloat(), spawn.offset.z().toFloat(), spawn.speed, spawn.count)
        // With the pack, the shader displays draw the strike; its dust particles would only muddy them.
        val shaded = spawn.importance == dev.projects.server.particle.ParticleImportance.COMBAT_FEEDBACK &&
            (spawn.particle is Particle.Dust || spawn.particle is Particle.DustColorTransition)
        instance.players.filter { it.position.distanceSquared(spawn.position) < 56.0 * 56.0 &&
            !(shaded && dev.projects.server.coreloop.CoreCombatPresentation.packed(it)) }.forEach { it.sendPacket(packet) }
    }
    private val shaderFx = ShadeWyrmShaderFx(instance)
    private val lights = ShadeWyrmLights(instance)
    private val gimmicks = ShadeWyrmGimmicks(tier, shaderFx, lights = lights)
    /** Players who have the boss glow post effect on (26.3 /posteffect, assets/projects/post_effect/shade_glow.json). */
    private val glowing = mutableSetOf<Player>()
    /** 翳の巣's rage clock: started when the wyrm enrages, it turns the arena's light violet (BossArenaFactory.SHADE_RAGE). */
    private val rageClock = if (instance.dimensionType.key() == dev.projects.server.coreloop.adventure.BossArenaFactory.SHADE_NEST)
        instance.clock(dev.projects.server.coreloop.adventure.BossArenaFactory.rageClock()) else null
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
    internal val modelDisplayCount: Int get() = mobs.values.sumOf { (it.model?.count ?: 0) + (it.view?.partCount ?: 0) }
    internal fun isEnraged(targetId: UUID): Boolean = mobs[targetId]?.enraged == true
    internal fun maneuverCount(targetId: UUID): Int = mobs[targetId]?.maneuvers ?: 0
    internal fun partsBroken(targetId: UUID): List<Boolean> = mobs[targetId]?.let { listOf(it.hornsBroken, it.fanTorn[0], it.fanTorn[1]) } ?: emptyList()
    internal fun erosionOf(player: UUID): Double = gimmicks.erosionOf(player)
    internal val poolCount: Int get() = gimmicks.poolCount
    /** Fight preview dump: the model's clip and the shader displays on show. */
    internal fun shownClip(targetId: UUID): Pair<String, Long>? = mobs[targetId]?.view?.let { it.shownClip to it.shownSince }
    internal fun modelFacing(targetId: UUID): Float? = mobs[targetId]?.let { if (it.maneuver != null) it.faceYaw else it.entity.position.yaw() }
    internal fun shaderViews() = shaderFx.views()
    internal fun lightsOn() = lights.snapshot()
    /** How far the arena's light has turned violet (0..1), for the preview dump. */
    internal fun rageShown(): Double = rageClock?.let { (it.time().toDouble() / dev.projects.server.coreloop.adventure.BossArenaFactory.RAGE_FADE_TICKS).coerceIn(0.0, 1.0) } ?: 0.0

    init {
        require(tier in 1..4)
        require(encounterLevel in ((tier - 1) * 10 + 1)..(tier * 10))
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
        if (sealed) mobs.values.filter { it.boss }.forEach { it.abilities.cancel(); clearWarning(it.entity.uuid); stopNavigation(it) }
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
            clearWarning(mob.entity.uuid)
            stopNavigation(mob)
            if (mob.life.isAlive) mob.life.phase = QuestMobPhase.DISPOSED
            mob.entity.remove()
        }
    }
    fun positionOf(targetId: UUID): Pos? = mobs[targetId]?.entity?.position
    internal fun currentTargetId(targetId: UUID): UUID? = mobs[targetId]?.target?.uuid
    fun isBoss(targetId: UUID): Boolean = mobs[targetId]?.boss == true
    fun bossHealth(): Double = mobs.values.firstOrNull { it.boss }?.life?.health ?: 0.0
    fun bossMaxHealth(): Double = mobs.values.firstOrNull { it.boss }?.life?.maximumHealth ?: 1.0
    fun bossName(): String = mobs.values.firstOrNull { it.boss }?.definition?.name ?: ""
    fun bossEnrageRatio(): Double = mobs.values.firstOrNull { it.boss }?.definition?.enrageHealthRatio ?: 0.0
    fun weaknessOf(targetId: UUID): String? = mobs[targetId]?.definition?.archetype?.weakness
    fun expose(targetId: UUID, durationMillis: Long = 4000): Boolean {
        val mob = mobs[targetId] ?: return false
        if (!mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING || durationMillis <= 0) return false
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        mob.exposedUntil = maxOf(mob.exposedUntil, now + durationMillis.coerceAtMost(8000))
        return true
    }
    fun taunt(targetId: UUID, player: Player): Boolean {
        val mob = mobs[targetId] ?: return false
        if (!mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING || (mob.boss && bossSealed) ||
            !canTarget(player) || player.instance !== instance || player.position.distance(mob.entity.position) > 12 || !mob.entity.hasLineOfSight(player)) return false
        val now = if(lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        mob.target = player
        mob.tauntedUntil = now + 2000
        return true
    }
    /** Pulls only along a clear grounded path. Bosses resist displacement and instead take a short interrupt. */
    fun pull(targetId: UUID, attacker: Player, maximumDistance: Double = 3.0): Boolean {
        val mob = mobs[targetId] ?: return false
        if (!mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING || (mob.boss && bossSealed) || mob.guardianIds.any(::isAlive) ||
            attacker.instance !== instance || !canTarget(attacker) || attacker.position.distance(mob.entity.position) > 12 || !mob.entity.hasLineOfSight(attacker)) return false
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        if (mob.boss) {
            if(now < mob.pullImmuneUntil) return false
            mob.pullImmuneUntil = now + 6000
            mob.abilities.cancel(); clearWarning(targetId); staggerBoss(now + 400); return true
        }
        val delta = attacker.position.sub(mob.entity.position)
        val horizontal = Vec(delta.x(), 0.0, delta.z())
        if (horizontal.length() < 1.6) return true
        val direction = horizontal.normalize()
        val distance = minOf(maximumDistance.coerceIn(0.0, 5.0), horizontal.length() - 1.4)
        mob.abilities.cancel(); clearWarning(targetId)
        slide(mob, direction, distance); mob.target = attacker
        return true
    }
    /**
     * Server-validated crowd control from a hit that already landed. Bosses take none of it: it wears down their
     * break gauge, and an empty gauge breaks them for [BREAK_MILLIS] (no attacks, no movement, +30% damage taken).
     */
    fun applyCrowdControl(targetId: UUID, kind: CrowdControl, durationMillis: Long, source: Player, distance: Double = 2.5): Boolean {
        if (disposed || durationMillis <= 0L) return false
        val mob = mobs[targetId] ?: return false
        if (!mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING || (mob.boss && bossSealed) || mob.guardianIds.any(::isAlive) ||
            source.instance !== instance || !canTarget(source)) return false
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        if (mob.boss) {
            if (now < mob.brokenUntil) return false
            mob.breakGauge = (mob.breakGauge - kind.breakDamage).coerceAtLeast(0.0)
            if (mob.breakGauge <= 0.0) {
                mob.brokenUntil = now + BREAK_MILLIS
                mob.breakGauge = BREAK_MAX
                mob.abilities.cancel(); clearWarning(targetId); castingPose(mob, false); stopNavigation(mob)
                mob.life.phase = QuestMobPhase.CHASING
                sound(mob.entity.position, "minecraft:block.anvil.land", 1.0f, 0.5f)
                particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.EXPLOSION, 2)
            }
            updateName(mob)
            return true
        }
        mob.crowd[kind] = maxOf(mob.crowd[kind] ?: 0L, now + durationMillis.coerceAtMost(5000L))
        mob.crowdSource = source.position
        if (mob.grade.breaks && now >= mob.brokenUntil) {
            mob.breakGauge = (mob.breakGauge - kind.breakDamage).coerceAtLeast(0.0)
            if (mob.breakGauge <= 0.0) {
                // A broken graded mob is stunned for the whole down time and takes +30%, like a broken boss.
                mob.brokenUntil = now + BREAK_MILLIS
                mob.breakGauge = BREAK_MAX
                mob.crowd[CrowdControl.STUN] = maxOf(mob.crowd[CrowdControl.STUN] ?: 0L, mob.brokenUntil)
                stopNavigation(mob)
                sound(mob.entity.position, "minecraft:block.anvil.land", 0.8f, 0.7f)
                particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.EXPLOSION, 1)
            }
        }
        if (kind.hard || kind == CrowdControl.FLINCH) {
            mob.abilities.cancel(); clearWarning(targetId); castingPose(mob, false)
            mob.life.phase = QuestMobPhase.CHASING
            mob.warningRetryAt = maxOf(mob.warningRetryAt, now + durationMillis.coerceAtMost(5000L))
        }
        when (kind) {
            CrowdControl.STUN, CrowdControl.ROOT -> stopNavigation(mob)
            CrowdControl.AIRBORNE -> { mob.entity.navigator.reset(); mob.entity.velocity = Vec(0.0, 9.0, 0.0) }
            CrowdControl.KNOCKBACK -> slide(mob, normalizeHorizontal(mob.entity.position.sub(source.position)), distance)
            else -> Unit
        }
        updateName(mob)
        return true
    }
    fun crowdControl(targetId: UUID): Set<CrowdControl> {
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        return mobs[targetId]?.crowd?.filterValues { it > now }?.keys ?: emptySet()
    }
    fun breakGauge(targetId: UUID): Double? = mobs[targetId]?.takeIf { it.boss || it.grade.breaks }?.breakGauge
    fun isBroken(targetId: UUID): Boolean {
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        return mobs[targetId]?.let { now < it.brokenUntil } == true
    }
    /** Moves a mob along the ground only as far as the path is clear (shared by pull and knockback). */
    private fun slide(mob: Mob, direction: Vec, distance: Double) {
        val origin = mob.entity.position
        var destination = origin
        for (step in 1..ceil(distance.coerceIn(0.0, 5.0) / .2).toInt()) {
            val point = origin.add(direction.mul(minOf(distance, step * .2)))
            if (!instance.getBlock(point.sub(0.0, .15, 0.0)).solid() ||
                listOf(-.4, .4).any { x -> listOf(-.4, .4).any { z -> listOf(.1, 1.0, 1.8).any { y -> instance.getBlock(point.add(x,y,z)).solid() } } }) break
            destination = point
        }
        stopNavigation(mob)
        mob.entity.teleport(destination)
    }
    private fun crowded(mob: Mob, kind: CrowdControl, now: Long) = (mob.crowd[kind] ?: 0L) > now
    fun mobInfo(targetId: UUID): QuestMobInfo? = mobs[targetId]?.let {
        QuestMobInfo(targetId, it.definition.archetype, it.definition.archetype.rarity, tier,
            it.entity.position, it.life.health, it.life.maximumHealth)
    }

    /** Each contribution keeps its own expiry. Bosses resist half; active strengths never add. */
    fun applySlow(targetId: UUID, percent: Double, durationMillis: Long, source: UUID? = null): Boolean {
        if (disposed || !percent.isFinite() || percent <= 0.0 || durationMillis <= 0L) return false
        val mob = mobs[targetId] ?: return false
        if (mob.guardianIds.any(::isAlive)) return false
        if (!mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING) return false
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        val effective = percent.coerceAtMost(0.65) * if (mob.boss) 0.5 else 1.0
        // Legacy calls of equal strength refresh one contribution; different strengths cannot borrow its expiry.
        val key = SlowKey(source, if (source == null) effective else 0.0)
        val until = now + durationMillis.coerceAtMost(30_000L)
        mob.slows[key] = Slow(effective, if (source == null) maxOf(mob.slows[key]?.until ?: 0L, until) else until)
        updateMovementSpeed(mob, now)
        return true
    }

    fun clearSlowSource(source: UUID) {
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        for (mob in mobs.values) if (mob.slows.keys.removeAll { it.source == source }) updateMovementSpeed(mob, now)
    }

    fun combatTargets(): List<CombatTarget> = if (disposed) emptyList() else mobs.values
        .filter { it.life.isAlive && it.life.phase != QuestMobPhase.RETURNING && isSpawned(it) }
        .map { mob ->
            val scale = mob.definition.scale
            // The wyrm's body reaches ~1 block ahead of its carrier; hits test the spheres on its bones (ShadeWyrmHitbox).
            if (mob.view != null) CombatTarget(mob.entity.uuid, mob.entity.position.add(normalizeHorizontal(mob.entity.position.direction()))
                .add(0.0, 1.8, 0.0), Vec(2.0, 1.8, 2.0), parts = ShadeWyrmHitbox.parts(mob.view!!))
            else if (mob.model != null) CombatTarget(mob.entity.uuid, mob.entity.position.add(0.0, 1.8, 0.0), Vec(1.2, 1.8, .6))
            else CombatTarget(mob.entity.uuid, mob.entity.position.add(0.0, 0.9 * scale, 0.0), Vec(0.35 * scale, 0.9 * scale, 0.35 * scale))
        }

    fun applyDamage(targetId: UUID, attacker: Player, amount: Double): Boolean {
        return applyDamageAmount(targetId, attacker, amount) != null
    }

    /** Damage feedback uses actual HP removed after guard and overkill, never a pre-mitigation guess. */
    fun applyDamageAmount(targetId: UUID, attacker: Player, amount: Double): Double? =
        damage(targetId, attacker, amount, effect = false)

    /** Only called after the server has resolved an aimed projectile ray, never from packet target IDs. */
    fun applyProjectileDamage(targetId: UUID, attacker: Player, amount: Double): Double? =
        damage(targetId, attacker, amount, effect = false, projectile = true)

    /** Only for an effect whose initial server hit was already validated (burn/chain), never packet input. */
    fun applyEffectDamage(targetId: UUID, attacker: Player, amount: Double): Boolean {
        return damage(targetId, attacker, amount, effect = true) != null
    }

    /** AR/MR is applied exactly once, before guard, boss phase gates and overkill checks. */
    fun applyCalculatedDamage(targetId: UUID, attacker: Player, amount: Double, type: CoreDamageType,
        stats: CoreAffixStats, projectile: Boolean = false, effect: Boolean = false): Double? =
        calculatedDamage(targetId, attacker, amount, type, stats, projectile, effect, null)

    /** A server-sampled field validates source-to-target LOS, independently of the caster's current cover. */
    fun applyCalculatedFieldDamage(targetId: UUID, attacker: Player, origin: Pos, amount: Double,
        type: CoreDamageType, stats: CoreAffixStats): Double? =
        calculatedDamage(targetId, attacker, amount, type, stats, true, false, origin)

    private fun calculatedDamage(targetId: UUID, attacker: Player, amount: Double, type: CoreDamageType,
        stats: CoreAffixStats, projectile: Boolean, effect: Boolean, fieldOrigin: Pos?): Double? {
        if (!amount.isFinite() || amount <= 0) return null
        val mob = mobs[targetId] ?: return null
        val defense = (tier - 1) * 30.0 + (encounterLevel - ((tier - 1) * 10 + 1)) * 2.0
        val armored = mob.definition.archetype in setOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.IRON_WARDEN)
        val caster = mob.definition.archetype in setOf(QuestMobArchetype.RIFT_CASTER, QuestMobArchetype.RIFT_ORACLE)
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        return damage(targetId, attacker, CoreCombatMath.mitigate(amount, type,
            defense * (if (armored) 1.5 else 1.0), defense * (if (caster) 1.5 else 1.0), stats,
            defenseReduction = if (now < mob.exposedUntil) 20.0 else 0.0), effect, projectile, fieldOrigin)
    }

    private fun damage(targetId: UUID, attacker: Player, amount: Double, effect: Boolean, projectile: Boolean = false,
        fieldOrigin: Pos? = null): Double? {
        val now = if(lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        if (disposed || attacker.instance !== instance || !canTarget(attacker)) return null
        val mob = mobs[targetId] ?: return null
        if ((mob.boss && bossSealed) || mob.guardianIds.any(::isAlive)) return null
        val source = fieldOrigin ?: attacker.position
        if (fieldOrigin != null) {
            if (!listOf(source.x(),source.y(),source.z()).all(Double::isFinite) || attacker.position.distanceSquared(source) > 24.0*24.0) return null
            val start = source.add(0.0,1.0,0.0); val end = mob.entity.position.add(0.0,1.0,0.0)
            val steps = ceil(start.distance(end)*4).toInt().coerceAtLeast(1)
            if ((1 until steps).any { instance.getBlock(start.add(end.sub(start).mul(it.toDouble()/steps))).solid() }) return null
        }
        // A model boss is long (tail ~7 blocks back, fans ~7 out): melee may land on a part far from the carrier.
        val range = if (effect || projectile) 24.0 else if (mob.view != null) 13.0 else 8.0
        if (!isSpawned(mob) || source.distanceSquared(mob.entity.position) > range * range) return null
        if (fieldOrigin == null && !effect && !mob.entity.hasLineOfSight(attacker)) return null
        val guarded = !effect && guarding(mob) &&
            normalizeHorizontal(source.sub(mob.entity.position)).dot(normalizeHorizontal(mob.entity.position.direction())) >= 0.5
        val healthBefore = mob.life.health
        val mitigated = amount * (if (guarded) mob.definition.frontalDamageMultiplier else 1.0) *
            (if (now < mob.brokenUntil) BROKEN_DAMAGE else 1.0) *
            (if (MobMod.IRONHIDE in mob.mods) MobMod.IRONHIDE_TAKEN else 1.0)
        val gated = if (mob.boss) minOf(mitigated, (mob.life.health - mob.life.maximumHealth * bossGateFraction).coerceAtLeast(0.0)) else mitigated
        if (!mob.life.damage(gated)) return null
        val applied = healthBefore - mob.life.health
        mob.lastAttacker = attacker
        mob.hurtAt = now
        if (mob.view != null && applied > 0.0 && mob.life.isAlive) {
            gimmicks.playerStruck(attacker)
            breakParts(mob, attacker, applied, now)
        }
        if (!mob.life.isAlive) {
            mob.abilities.cancel()
            clearWarning(mob.entity.uuid)
            castingPose(mob, false)
            stopNavigation(mob)
            sound(mob.entity.position, "minecraft:entity.${mob.definition.soundFamily}.death", 1.0f, if (mob.boss) 0.65f else 1.0f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.POOF, 8)
            latestDefeat = QuestMobDefeat("mob:${mob.entity.uuid}", mob.entity.uuid, mob.definition.archetype,
                mob.definition.archetype.rarity, mob.definition.dropKind, tier, mob.entity.position, attacker.uuid)
            plates.hide(mob.entity.uuid)
            mob.entity.kill()
            mob.model?.dispose()
            mob.view?.die()
            // Health is already terminal before this callback can re-enter the runtime.
            onMobDefeated(attacker, mob.boss)
        } else {
            if(now >= mob.tauntedUntil || mob.target?.let { !canTarget(it) || it.instance !== instance } != false) mob.target = attacker
            if (mob.life.phase == QuestMobPhase.IDLE) mob.life.phase = QuestMobPhase.CHASING
            updateName(mob)
            mob.view?.hurt(now)
            sound(mob.entity.position, if (guarded) "minecraft:item.shield.block" else "minecraft:entity.${mob.definition.soundFamily}.hurt", 0.6f, 1.0f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.DAMAGE_INDICATOR, 4)
        }
        return applied
    }

    fun tick(nowMillis: Long) {
        if (disposed || nowMillis <= lastTickAt) return
        lastTickAt = nowMillis
        telegraphs.tick(nowMillis)
        vfx.tick()
        shaderFx.tick()
        lights.tick(nowMillis)
        tickGlow()
        rageClock?.let { if (!it.paused() && it.time() >= dev.projects.server.coreloop.adventure.BossArenaFactory.RAGE_FADE_TICKS) it.pause() }
        if (mobs.values.any { it.view != null }) gimmicks.tick(nowMillis, instance.players.filter { canTarget(it) }) { p, amount, type ->
            if (typedDamagePlayer != null) typedDamagePlayer.invoke(p, amount, type) else damagePlayer(p, amount)
        }
        val players = instance.players.filter { canTarget(it) }
        if (actionsStoppedForReturn) {
            if (players.isEmpty()) return
            actionsStoppedForReturn = false
        }
        if (pixelNameplates && nowMillis >= plateRefreshAt) {
            // Icons and break timers change without an event (a slow wears off): refresh 4x a second;
            // MobPlates only sends a packet when the text actually changed.
            plateRefreshAt = nowMillis + 250L
            mobs.values.filter { it.life.isAlive && isSpawned(it) }.forEach(::showHud)
            plates.retain(mobs.values.filter { it.life.isAlive && isSpawned(it) && !it.boss }.mapTo(HashSet()) { it.entity.uuid })
        }
        for (mob in mobs.values.toList()) {
            mob.spawnFailure?.let { throw IllegalStateException("Quest mob failed to spawn at ${mob.home}", it) }
            if (!mob.life.isAlive || !isSpawned(mob)) continue
            tickMob(mob, players, nowMillis)
            mob.model?.tick(mob.entity.position, nowMillis, mob.life.phase == QuestMobPhase.ATTACKING, mob.life.health / mob.life.maximumHealth)
            if (mob.view != null) tickRageWave(mob, nowMillis)
            if (mob.view != null) lights.hold("horns-${mob.entity.uuid}",
                if (mob.enraged && !mob.hornsBroken && nowMillis >= mob.enrageHoldUntil - 2500) mob.view?.point("skull", ShadeWyrmVfx.CROWN) else null, 12)
            mob.view?.tick(if (mob.maneuver != null) mob.entity.position.withView(mob.faceYaw, 0f) else mob.entity.position, nowMillis,
                searching = mob.searching && mob.maneuver == null && !mob.abilities.isActive)
            if (actionsStoppedForReturn || disposed) return
        }
    }

    /** Root sets returning=true first. A failed save may resume later, but cannot resume an old hit frame. */
    fun stopActionsForReturn(nowMillis: Long = System.currentTimeMillis()) {
        if (disposed) return
        actionsStoppedForReturn = true
        mobs.values.filter { it.life.isAlive }.forEach { mob ->
            mob.abilities.cancel()
            clearWarning(mob.entity.uuid)
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
        vfx.cancelAll()
        gimmicks.dispose()
        lights.dispose()
        glowing.forEach { it.sendPacket(net.minestom.server.network.packet.server.common.PostEffectsPacket(emptyList())) }
        glowing.clear()
        shaderFx.dispose()
        plates.dispose()
        mobs.values.forEach { mob ->
            mob.abilities.cancel()
            stopNavigation(mob)
            mob.life.phase = QuestMobPhase.DISPOSED
            mob.target = null
            mob.lastAttacker = null
            mob.entity.remove()
            mob.model?.dispose()
            mob.view?.dispose()
        }
    }

    private fun spawn(position: Pos, encounterIndex: Int, archetype: QuestMobArchetype): UUID {
        val base = QuestMobContent.definition(tier, archetype)
        val rank = encounterLevel - ((tier - 1) * 10 + 1)
        val balance = dev.projects.server.coreloop.CoreMmoTuning.balance
        val (grade, mods) = if (rollGrades) MobGrades.roll(gradeSeed * 31 + encounterIndex * 1_000_003L + position.hashCode(), archetype)
            else MobGrade.COMMON to emptyList()
        val might = if (MobMod.MIGHT in mods) MobMod.MIGHT_DAMAGE else 1.0
        val definition = base.copy(
            maximumHealth = base.maximumHealth * healthMultiplier * grade.health * (1 + rank * balance.mobRankHealthPermille / 1000.0),
            movementSpeed = base.movementSpeed * (if (MobMod.SWIFT in mods) MobMod.SWIFT_SPEED else 1.0),
            abilities = base.abilities.map { it.copy(damage = it.damage * damageMultiplier * grade.damage * might *
                (1 + rank * balance.mobRankDamagePermille / 1000.0)) })
        val modelled = definition.model?.takeIf { BossModelView.available(it) }
        val entity = if (modelled != null) ModelCarrier(definition.entityType) else EntityCreature(definition.entityType)
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
        mob.grade = grade; mob.mods = mods
        mobs[entity.uuid] = mob
        updateName(mob)
        entity.setInstance(instance, position).whenComplete { _, error ->
            if (error != null) mob.spawnFailure = error
            if (disposed) entity.remove()
            else if (error == null && modelled != null && entity is ModelCarrier) {
                runCatching {
                    val view = BossModelView(modelled, instance, position, definition.enragedBones, definition.foldedBones)
                    entity.view = view
                    entity.isInvisible = true
                    // Viewers that arrived before the model existed.
                    entity.viewers.forEach(view::show)
                    mob.view = view
                    if (disposed || !mob.life.isAlive) view.dispose()
                }.onFailure { mob.spawnFailure = it }
            }
            else if (error == null && WardenModel.supports(archetype)) {
                runCatching {
                    val model = WardenModel(instance, archetype, position)
                    model.ready.whenComplete { _, modelError -> if (modelError != null) mob.spawnFailure = modelError }
                    mob.model = model
                    entity.isInvisible = true
                    EquipmentSlot.entries.forEach { entity.setEquipment(it, ItemStack.AIR) }
                    if (disposed || !mob.life.isAlive) model.dispose()
                }.onFailure { mob.spawnFailure = it }
            }
        }
        return entity.uuid
    }

    private fun isSpawned(mob: Mob): Boolean = !mob.entity.isRemoved && mob.entity.instance === instance

    private fun tickMob(mob: Mob, players: List<Player>, now: Long) {
        if (mob.boss && (bossSealed || now < bossStaggerUntil)) { stopNavigation(mob); return }
        if (mob.brokenUntil in 1..now) { mob.brokenUntil = 0L; updateName(mob) }
        if (mob.boss && now < mob.brokenUntil) { stopNavigation(mob); return }
        if (mob.crowd.values.removeAll { it <= now }) updateName(mob)
        if (MobMod.REGEN in mob.mods && now - mob.hurtAt >= MobMod.REGEN_DELAY && mob.life.health < mob.life.maximumHealth &&
            mob.life.phase != QuestMobPhase.RETURNING) {
            mob.life.heal(mob.life.maximumHealth * MobMod.REGEN_PER_SECOND * (now - mob.lastRegenAt).coerceIn(0L, 1000L) / 1000.0)
        }
        mob.lastRegenAt = now
        updateMovementSpeed(mob, now)
        mob.entity.refreshActiveHand(guarding(mob), true, false)
        if (mob.life.phase == QuestMobPhase.RETURNING) {
            if (mob.entity.position.distanceSquared(mob.home) < 1.5 * 1.5 || now - mob.returningSince > 6000L) {
                // Failed paths cannot leave a damaged, invulnerable encounter stranded forever.
                mob.entity.teleport(mob.home)
                stopNavigation(mob)
                mob.abilities.reset()
                mob.life.finishReturn()
                mob.exposedUntil = 0L
                mob.tauntedUntil = 0L
                mob.pullImmuneUntil = 0L
                mob.crowd.clear(); mob.breakGauge = BREAK_MAX; mob.brokenUntil = 0L
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
        if (tickEnrage(mob, now)) return
        if (crowded(mob, CrowdControl.STUN, now) || crowded(mob, CrowdControl.AIRBORNE, now) || crowded(mob, CrowdControl.KNOCKBACK, now)) {
            if (!crowded(mob, CrowdControl.AIRBORNE, now)) stopNavigation(mob)
            return
        }
        val charmed = crowded(mob, CrowdControl.CHARM, now)
        if (charmed || crowded(mob, CrowdControl.FEAR, now)) {
            val from = mob.crowdSource ?: target.position
            if (now >= mob.nextPathAt) {
                val goal = if (charmed) from else
                    safeRetreat(mob.entity.position.add(normalizeHorizontal(mob.entity.position.sub(from)).mul(4.0)), mob.home, mob.definition.leashRange)
                if (goal != null) mob.entity.navigator.setPathTo(goal, if (charmed) 1.2 else 0.6, null) else stopNavigation(mob)
                mob.nextPathAt = now + 300L
            }
            return
        }
        mob.maneuver?.let { if (tickManeuver(mob, it, target, now)) return }
        if (mob.abilities.isActive) {
            for (event in mob.abilities.tick(now, aimAt(mob, target, now))) {
                if (!handleAbilityEvent(mob, event, players)) return
                if (disposed || !mob.life.isAlive) return
            }
            val frame = mob.abilities.current?.let { effectiveFrame(mob, it) }
            if (frame != null) {
                mob.entity.setView(Pos.ZERO.withDirection(frame.facing).yaw(), 0.0f)
                carryThroughAbility(mob, frame, now)
                behindFor(mob, target, now)                // standing behind it during an attack counts too
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
            val ratio = mob.life.health / mob.life.maximumHealth
            val silenced = crowded(mob, CrowdControl.SILENCE, now)
            val scripted = if (silenced) null else mob.combo ?: if (behindFor(mob, target, now) >= TURN_AFTER_MILLIS) TURN_SWEEP else null
            mob.combo = null
            val aim = aimAt(mob, target, now)
            val start = scripted?.let { mob.abilities.tryStart(now, mob.entity.position, aim, ratio, only = it) }
                ?: mob.abilities.tryStart(now, mob.entity.position, aim, ratio, plainOnly = silenced)
            if (start != null) {
                mob.life.phase = QuestMobPhase.ATTACKING
                stopNavigation(mob)
                handleAbilityEvent(mob, start, players)
                return
            }
        }
        mob.life.phase = QuestMobPhase.CHASING
        if (crowded(mob, CrowdControl.ROOT, now)) { stopNavigation(mob); return }
        if (startManeuver(mob, target, now)) return
        val wasSearching = mob.searching
        mob.searching = mob.view != null &&
            (mob.entity.position.distanceSquared(target.position) > SEARCH_BEYOND * SEARCH_BEYOND || !mob.entity.hasLineOfSight(target))
        if (mob.searching != wasSearching) updateMovementSpeed(mob, now)
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
        mob.maneuver = null
        removeEncounter(mob.guardianIds)
        mob.guardianIds = emptySet()
        mob.challengeStage = 0
        mob.abilities.cancel()
        clearWarning(mob.entity.uuid)
        castingPose(mob, false)
        mob.slows.clear()
        mob.crowd.clear()
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
        clearWarning(mob.entity.uuid)
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

    /**
     * The enrage roar (2.5 s into the hold, when the model rears up) sends a violet wave from the wyrm to the arena
     * wall: a ring display grows to [RAGE_WAVE_RADIUS] over [RAGE_WAVE_MILLIS], light blocks flash along its front,
     * and the arena's rage clock starts so the light turns violet as the wave crosses it (BossArenaFactory.SHADE_RAGE).
     */
    private fun tickRageWave(mob: Mob, now: Long) {
        if (!mob.enraged) return
        if (mob.waveAt == 0L) {
            if (now < mob.enrageHoldUntil - 2500) return
            mob.waveAt = now
            mob.waveFrom = mob.entity.position
            shaderFx.rageWave(mob.entity.position, RAGE_WAVE_RADIUS, (RAGE_WAVE_MILLIS / 50).toInt())
            rageClock?.resume()
        }
        val from = mob.waveFrom ?: return
        val age = now - mob.waveAt
        if (age > RAGE_WAVE_MILLIS || (age / 50) % 2 != 0L) return
        val r = RAGE_WAVE_RADIUS * age / RAGE_WAVE_MILLIS
        val n = maxOf(4, (2 * Math.PI * r / 4.0).toInt())
        for (k in 0 until n) {
            val a = k * 2 * Math.PI / n
            lights.flash(from.add(kotlin.math.cos(a) * r, 0.0, kotlin.math.sin(a) * r), 13, 150)
        }
    }

    /** One-way rage at the definition's health ratio: a held roar (no attacks), then faster with new abilities. */
    private fun tickEnrage(mob: Mob, now: Long): Boolean {
        val threshold = mob.definition.enrageHealthRatio
        if (!mob.enraged) {
            if (threshold <= 0.0 || mob.life.health / mob.life.maximumHealth > threshold) return false
            mob.enraged = true
            mob.maneuver = null
            mob.enrageHoldUntil = now + ENRAGE_HOLD_MILLIS
            mob.abilities.cancel()
            clearWarning(mob.entity.uuid)
            castingPose(mob, false)
            stopNavigation(mob)
            mob.view?.enrage(now)
            mob.warningRetryAt = mob.enrageHoldUntil
            updateName(mob, "怒り・翼を広げた")
            sound(mob.entity.position, "minecraft:entity.ravager.roar", 1.2f, 0.6f)
            particle(mob.entity.position.add(0.0, 2.0, 0.0), Particle.DUST.withColor(NamedTextColor.DARK_PURPLE).withScale(2.0f), 40)
            updateMovementSpeed(mob, now)
            return true
        }
        if (now >= mob.enrageHoldUntil) return false
        stopNavigation(mob)
        return true
    }

    private fun guarding(mob: Mob): Boolean = mob.definition.frontalDamageMultiplier < 1.0 &&
        mob.life.phase in listOf(QuestMobPhase.IDLE, QuestMobPhase.CHASING)

    private fun castingPose(mob: Mob, active: Boolean) {
        (mob.entity.entityMeta as? SpellcasterIllagerMeta)?.spell =
            if (active) SpellcasterIllagerMeta.Spell.ATTACK else SpellcasterIllagerMeta.Spell.NONE
    }

    private fun clearWarning(id: UUID) {
        telegraphs.clear(id)
        vfx.cancel(vfxHandles.remove(id))
        shaderFx.cancelWindup()
    }

    /** Model bosses only: the wind-up cue is cut when the ability is, the strike always plays out. */
    private fun playVfx(mob: Mob, effect: dev.projects.server.particle.ParticleEffect?) {
        if (mob.view == null || effect == null) return
        vfx.cancel(vfxHandles.remove(mob.entity.uuid))
        vfxHandles[mob.entity.uuid] = vfx.start(effect, vfxSink)
    }

    private fun cancelWarning(mob: Mob) {
        mob.abilities.cancel()
        clearWarning(mob.entity.uuid)
        mob.life.phase = QuestMobPhase.CHASING
        mob.warningRetryAt = lastTickAt + 600L
        castingPose(mob, false)
        updateName(mob)
    }

    private fun updateMovementSpeed(mob: Mob, now: Long) {
        mob.slows.values.removeAll { now >= it.until }
        mob.slowPercent = mob.slows.values.maxOfOrNull { it.strength } ?: 0.0
        mob.slowedUntil = mob.slows.values.maxOfOrNull { it.until } ?: 0L
        val speed = mob.definition.movementSpeed * (1.0 - mob.slowPercent) *
            (if (mob.enraged) mob.definition.enragedSpeedMultiplier else 1.0) *
            (if (frenzied(mob)) MobMod.FRENZY_SPEED else 1.0) *
            (if (mob.maneuver?.kind == ManeuverKind.STRAFE) STRAFE_SPEED else 1.0) *
            (if (mob.searching && mob.maneuver == null) SEARCH_SPEED else 1.0)
        if (mob.entity.getAttribute(Attribute.MOVEMENT_SPEED).baseValue != speed) {
            mob.entity.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = speed
        }
    }

    /**
     * Between attacks a model boss with movement clips moves round its target instead of only walking at it
     * (shade wyrm, after the Wilds Gore Magala): too close -> one wingbeat back (hop_back), mid range -> a low
     * sidestep round it (strafe), every third time a ground-skimming dart to its side (dash). Attacks come first;
     * this runs only when no ability could start.
     */
    private fun startManeuver(mob: Mob, target: Player, now: Long): Boolean {
        val view = mob.view ?: return false
        if (!view.canManeuver || now < mob.nextManeuverAt || mob.enraged && now < mob.enrageHoldUntil) return false
        if (behindFor(mob, target, now) > 0L) return false          // someone at its back: hold still and turn on them
        val here = mob.entity.position
        val distance = horizontalLength(target.position.sub(here))
        val away = normalizeHorizontal(here.sub(target.position))
        val right = Vec(away.z(), 0.0, -away.x())            // the boss's right while it faces the target
        val kind = when {
            distance < 3.5 -> ManeuverKind.HOP_BACK
            distance in 4.0..10.0 -> if (mob.maneuvers % 3 == 2) ManeuverKind.DASH else ManeuverKind.STRAFE
            else -> return false
        }
        val sign = if (mob.maneuvers % 2 == 0) 1 else -1
        val leash = mob.definition.leashRange
        val maneuver = when (kind) {
            ManeuverKind.HOP_BACK -> {
                val to = safeRetreat(here.add(away.mul(4.0)), mob.home, leash) ?: return false
                Maneuver(kind, here, to, now + 280, now + 680, now + 1100, 0)
            }
            ManeuverKind.DASH -> {
                val to = safeRetreat(here.add(right.mul(5.0 * sign)), mob.home, leash)
                    ?: safeRetreat(here.add(right.mul(-5.0 * sign)), mob.home, leash) ?: return false
                val toRight = (to.x() - here.x()) * right.x() + (to.z() - here.z()) * right.z() > 0
                Maneuver(kind, here, to, now + 200, now + 500, now + 900, if (toRight) 1 else -1)
            }
            ManeuverKind.STRAFE -> Maneuver(kind, here, here, now, now, now + 1600, sign)
        }
        mob.maneuvers++
        mob.nextManeuverAt = maneuver.endAt + if (kind == ManeuverKind.STRAFE) 900 else 1700
        mob.maneuver = maneuver
        mob.faceYaw = yawToward(here, target.position)
        if (kind != ManeuverKind.STRAFE) stopNavigation(mob)
        when (kind) {
            ManeuverKind.HOP_BACK -> view.maneuver("hop_back")
            ManeuverKind.DASH -> view.maneuver(if (maneuver.sign > 0) "dash_l" else "dash_r")
            ManeuverKind.STRAFE -> mob.nextPathAt = now
        }
        updateMovementSpeed(mob, now)
        return true
    }

    /** True while the maneuver holds the mob (no attacks, no chase). */
    private fun tickManeuver(mob: Mob, maneuver: Maneuver, target: Player, now: Long): Boolean {
        if (now >= maneuver.endAt) {
            mob.maneuver = null
            mob.combo = when (maneuver.kind) {
                ManeuverKind.HOP_BACK -> if (mob.enraged) "shade-breath" else null   // 2 跳び退き→吐息
                ManeuverKind.DASH -> "shade-sweep"                                   // 3 回り込み→薙ぎ
                ManeuverKind.STRAFE -> null
            }
            if (mob.combo != null) mob.warningRetryAt = now
            updateMovementSpeed(mob, now)
            return false
        }
        val here = mob.entity.position
        mob.faceYaw = yawToward(here, target.position)
        if (maneuver.kind == ManeuverKind.STRAFE) {
            if (now >= mob.nextPathAt) {
                val angle = kotlin.math.atan2(here.z() - target.position.z(), here.x() - target.position.x()) + maneuver.sign * .6
                val radius = horizontalLength(here.sub(target.position)).coerceIn(5.0, 8.0)
                val goal = safeRetreat(target.position.add(kotlin.math.cos(angle) * radius, 0.0, kotlin.math.sin(angle) * radius), mob.home, mob.definition.leashRange)
                if (goal != null) mob.entity.navigator.setPathTo(goal, 0.5, null) else stopNavigation(mob)
                mob.nextPathAt = now + 300L
            }
        } else if (now >= maneuver.moveFrom) {
            val k = ((now - maneuver.moveFrom).toDouble() / (maneuver.moveTo - maneuver.moveFrom)).coerceIn(0.0, 1.0)
            val e = k * k * (3 - 2 * k)
            val f = maneuver.from; val t = maneuver.to
            mob.entity.teleport(Pos(f.x() + (t.x() - f.x()) * e, f.y() + (t.y() - f.y()) * e, f.z() + (t.z() - f.z()) * e, mob.faceYaw, 0f))
        }
        return true
    }

    /** The strike's own violet light on the floor and the wyrm (see [ShadeWyrmLights]). */
    private fun lightUp(frame: MobAbilityFrame) {
        val o = frame.origin
        val f = normalizeHorizontal(frame.facing)
        when (val shape = frame.ability.shape) {
            is MobAttackShape.Ring -> {
                lights.flash(o, 13, 700)
                val r = if (shape.innerRadius > 0) (shape.innerRadius + shape.radius) / 2 else shape.radius * .6
                for (k in 0 until 4) { val a = k * Math.PI / 2 + .4; lights.flash(o.add(kotlin.math.cos(a) * r, 0.0, kotlin.math.sin(a) * r), 11, 700) }
            }
            is MobAttackShape.Slam -> { var d = 2.0; while (d <= shape.length) { lights.flash(o.add(f.mul(d)), 12, 900); d += 4.0 } }
            is MobAttackShape.Sweep -> for (k in -1..1) {
                val a = Math.toRadians(shape.halfAngleDegrees * .6 * k)
                lights.flash(o.add(f.rotateAroundY(a).mul(shape.radius * .7)), 12, 500)
            }
            else -> Unit
        }
    }

    /** The glow post effect is on for exactly the players in an arena whose model boss is alive. */
    private fun tickGlow() {
        val boss = mobs.values.any { it.view != null && it.life.isAlive && it.definition.model == "shade_wyrm" }
        val wanted = if (boss) instance.players.toSet() else emptySet()
        for (p in wanted - glowing) p.sendPacket(net.minestom.server.network.packet.server.common.PostEffectsPacket(listOf(GLOW)))
        for (p in glowing - wanted) p.sendPacket(net.minestom.server.network.packet.server.common.PostEffectsPacket(emptyList()))
        glowing.clear(); glowing += wanted
    }

    /** With its horns broken it aims badly for a while: its target point swings up to 3 blocks to either side. */
    private fun aimAt(mob: Mob, target: Player, now: Long): Pos {
        if (now >= mob.sloppyUntil) return target.position
        val yaw = Math.toRadians(mob.entity.position.yaw().toDouble())
        return target.position.add(Vec(-kotlin.math.cos(yaw), 0.0, -kotlin.math.sin(yaw)).mul(3.0 * kotlin.math.sin(now / 600.0)))
    }

    /** A torn right fan (the side its wing-arm sweeps with) shortens the sweeps to [TORN_SWEEP_RADIUS]. */
    private fun effectiveFrame(mob: Mob, frame: MobAbilityFrame): MobAbilityFrame {
        val shape = frame.ability.shape
        if (!mob.fanTorn[0] || shape !is MobAttackShape.Sweep || frame.ability.id !in setOf("shade-sweep", "shade-turnsweep")) return frame
        return frame.copy(ability = frame.ability.copy(shape = MobAttackShape.Sweep(minOf(shape.radius, TORN_SWEEP_RADIUS), shape.halfAngleDegrees)))
    }

    /**
     * Enraged part breaks (gimmicks): damage dealt from in front within [HEAD_REACH] of the head wears the horns,
     * from either side the fan on that side. Thresholds are shares of its maximum health.
     */
    private fun breakParts(mob: Mob, attacker: Player, applied: Double, now: Long) {
        if (!mob.enraged || now < mob.enrageHoldUntil) return
        val here = mob.entity.position
        val yaw = Math.toRadians((if (mob.maneuver != null) mob.faceYaw else here.yaw()).toDouble())
        val ahead = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
        val right = Vec(-kotlin.math.cos(yaw), 0.0, -kotlin.math.sin(yaw))
        val off = Vec(attacker.position.x() - here.x(), 0.0, attacker.position.z() - here.z())
        if (off.length() < 1e-6) return
        val dir = off.normalize()
        val angle = Math.toDegrees(kotlin.math.acos(dir.dot(ahead).coerceIn(-1.0, 1.0)))
        val max = mob.life.maximumHealth
        val head = here.add(ahead.mul(4.0))
        if (!mob.hornsBroken && angle <= 60.0 && attacker.position.distance(head.withY(attacker.position.y())) <= HEAD_REACH) {
            mob.hornDamage += applied
            if (mob.hornDamage >= max * HORN_BREAK) {
                mob.hornsBroken = true
                mob.sloppyUntil = now + SLOPPY_MILLIS
                mob.view?.hideBones(listOf("horn_l_tip", "horn_r_tip"))
                partBroken(mob, now, 3000L, "角が折れた")
            }
        }
        if (angle in 45.0..135.0) {
            val side = if (dir.dot(right) > 0) 0 else 1
            if (!mob.fanTorn[side]) {
                mob.fanDamage[side] += applied
                if (mob.fanDamage[side] >= max * FAN_TEAR) {
                    mob.fanTorn[side] = true
                    mob.view?.hideBones(listOf(if (side == 0) "cloak_l_tatters" else "cloak_r_tatters"))   // _l = its right
                    if (mob.fanTorn.all { it }) mob.abilities.disable("shade-storm")
                    partBroken(mob, now, 2000L, "扇が裂けた")
                }
            }
        }
    }

    private fun partBroken(mob: Mob, now: Long, staggerMillis: Long, label: String) {
        mob.abilities.cancel()
        clearWarning(mob.entity.uuid)
        castingPose(mob, false)
        mob.maneuver = null
        stopNavigation(mob)
        staggerBoss(maxOf(bossStaggerUntil, now + staggerMillis))
        mob.view?.maneuver("flinch")                       // cuts the attack clip that was playing
        updateName(mob, label)
        sound(mob.entity.position, "minecraft:entity.ravager.stunned", 1.2f, 0.7f)
        particle(mob.entity.position.add(0.0, 2.5, 0.0), Particle.DUST.withColor(NamedTextColor.LIGHT_PURPLE).withScale(2.0f), 30)
    }

    /** How long the target has stood behind the model (more than 120 degrees off its facing). */
    private fun behindFor(mob: Mob, target: Player, now: Long): Long {
        if (mob.view == null) return 0L
        val here = mob.entity.position
        val facing = if (mob.maneuver != null) mob.faceYaw else here.yaw()
        var off = (yawToward(here, target.position) - facing) % 360f
        if (off > 180f) off -= 360f
        if (off < -180f) off += 360f
        if (kotlin.math.abs(off) < 120f) { mob.behindSince = 0L; return 0L }
        if (mob.behindSince == 0L) mob.behindSince = now
        return now - mob.behindSince
    }

    /** The dive carries the body over its target while it hangs in the air (between [DIVE_CARRY] ms of the clip). */
    private fun carryThroughAbility(mob: Mob, frame: MobAbilityFrame, now: Long) {
        if (frame.ability.id != "shade-dive") return
        val from = mob.travelFrom ?: return
        val elapsed = now - frame.startedAt
        if (elapsed < DIVE_CARRY.first) return
        val k = ((elapsed - DIVE_CARRY.first).toDouble() / (DIVE_CARRY.last - DIVE_CARRY.first)).coerceIn(0.0, 1.0)
        val e = k * k * (3 - 2 * k)
        val to = frame.origin
        val ground = telegraphs.groundHeight(to) ?: from.y()
        mob.entity.teleport(Pos(from.x() + (to.x() - from.x()) * e, from.y() + (ground - from.y()) * e, from.z() + (to.z() - from.z()) * e,
            mob.entity.position.yaw(), 0f))
    }

    private fun yawToward(from: Pos, to: net.minestom.server.coordinate.Point): Float =
        Math.toDegrees(kotlin.math.atan2(-(to.x() - from.x()), to.z() - from.z())).toFloat()

    private fun safeRetreat(position: Pos, home: Pos, leash: Double): Pos? {
        val ground = telegraphs.groundHeight(position) ?: return null
        val candidate = position.withY(ground)
        if (candidate.distanceSquared(home) > leash * leash) return null
        val x = floor(candidate.x()).toInt()
        val z = floor(candidate.z()).toInt()
        val y = floor(candidate.y()).toInt()
        return candidate.takeUnless { (y..y + 2).any { instance.getBlock(x, it, z).solid() } }
    }

    private fun handleAbilityEvent(mob: Mob, raw: MobAbilityEvent, players: List<Player>): Boolean {
        val event = when (raw) {
            is MobAbilityEvent.Started -> raw.copy(frame = effectiveFrame(mob, raw.frame))
            is MobAbilityEvent.Locked -> raw.copy(frame = effectiveFrame(mob, raw.frame))
            is MobAbilityEvent.Hit -> raw.copy(frame = effectiveFrame(mob, raw.frame))
            else -> raw
        }
        when (event) {
            is MobAbilityEvent.Started -> {
                mob.travelFrom = mob.entity.position
                if (event.frame.ability.id == TURN_SWEEP) mob.behindSince = 0L
                updateName(mob, event.frame.ability.displayName)
                mob.view?.ability(event.frame.ability.id)
                if (!drawWarning(mob, event.frame, false)) {
                    cancelWarning(mob)
                    return false
                }
                playVfx(mob, ShadeWyrmVfx.windup(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) })
                if (mob.view != null) shaderFx.windup(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) }
                mob.entity.refreshActiveHand(false, true, false)
                castingPose(mob, true)
                sound(event.frame.origin, "minecraft:block.note_block.hat", 0.7f, 0.8f)
            }
            is MobAbilityEvent.Locked -> {
                if (!drawWarning(mob, event.frame, false)) { cancelWarning(mob); return false }
                sound(event.frame.origin, "minecraft:block.note_block.pling", 0.8f, 0.7f)
            }
            is MobAbilityEvent.Hit -> {
                val blind = crowded(mob, CrowdControl.BLIND, lastTickAt)
                val event = if (!blind) event else MobAbilityEvent.Hit(event.frame.copy(
                    // A blinded swing lands well off its mark: turned aside, and a targeted blast lands 4 blocks away.
                    facing = event.frame.facing.rotateAroundY(Math.toRadians(if (Random.nextBoolean()) 70.0 else -70.0)),
                    origin = if (event.frame.ability.anchor == MobAbilityAnchor.TARGET)
                        event.frame.origin.add(event.frame.facing.rotateAroundY(Math.PI / 2).mul(4.0)) else event.frame.origin))
                mob.entity.swingMainHand()
                castingPose(mob, false)
                if (!drawWarning(mob, event.frame, true)) { cancelWarning(mob); return false }
                playVfx(mob, ShadeWyrmVfx.hit(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) })
                if (mob.view != null) shaderFx.strike(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) }
                sound(event.frame.origin,
                    if (mob.entity.entityMeta is SpellcasterIllagerMeta) "minecraft:entity.evoker.cast_spell"
                    else if (event.frame.ability.shape is MobAttackShape.Sweep) "minecraft:entity.player.attack.sweep"
                    else "minecraft:block.anvil.land", 0.8f, if (mob.boss) 0.7f else 1.0f)
                for (player in players) {
                    if (player.instance !== instance || !canTarget(player)) continue
                    if (event.frame.ability.shape.contains(event.frame.origin, event.frame.facing, player.position) &&
                        mob.entity.hasLineOfSight(player)
                    ) {
                        val dealt = event.frame.ability.damage * (if (frenzied(mob)) MobMod.FRENZY_DAMAGE else 1.0)
                        if (typedDamagePlayer != null) typedDamagePlayer.invoke(player, dealt, event.frame.ability.damageType)
                        else damagePlayer(player, dealt)
                        if (MobMod.LEECH in mob.mods) mob.life.heal(mob.life.maximumHealth * MobMod.LEECH_RATIO)
                        if (mob.view != null) gimmicks.playerHit(player, event.frame.ability.id, lastTickAt)
                    }
                    if (disposed || actionsStoppedForReturn) return false
                }
                if (mob.view != null) { gimmicks.abilityHit(event.frame, lastTickAt); lightUp(event.frame) }
            }
            is MobAbilityEvent.Finished -> { clearWarning(mob.entity.uuid); updateName(mob) }
        }
        return true
    }

    /** Set by the server once the owner's UI pack is loaded; name tags then use the pixel nameplate. */
    var pixelNameplates = false
        set(value) {
            field = value
            if (!value) plates.dispose()
            mobs.values.filter { it.life.isAlive }.forEach { updateName(it, it.notice) }
        }

    private fun updateName(mob: Mob, attackName: String? = null) {
        mob.notice = attackName
        if (pixelNameplates) { showHud(mob); return }
        mob.entity.isCustomNameVisible = true
        val weakness = when (mob.definition.archetype.weakness) { "fire" -> "炎"; "ice" -> "氷"; else -> "雷" }
        val base = "T$tier ${mob.definition.name}  ${ceil(mob.life.health).toInt()}/${ceil(mob.life.maximumHealth).toInt()}  弱点:$weakness"
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        val crowd = when {
            now < mob.brokenUntil -> "  ［崩れた！］"
            mob.boss -> "  崩し ${ceil(mob.breakGauge).toInt()}"
            else -> mob.crowd.filterValues { it > now }.keys.joinToString("") { "  ［${it.label}］" }
        }
        val suffix = when {
            mob.life.phase == QuestMobPhase.RETURNING -> "  帰還中"
            attackName != null -> "  $attackName"
            else -> ""
        } + crowd + mob.mods.joinToString("") { "  ${it.label}" }
        val color = when {
            mob.definition.archetype.rarity == QuestMobRarity.BOSS -> NamedTextColor.RED
            mob.grade == MobGrade.COMMON && mob.definition.archetype.rarity == QuestMobRarity.NORMAL -> NamedTextColor.GOLD
            else -> NamedTextColor.LIGHT_PURPLE
        }
        val grade = if (mob.grade == MobGrade.COMMON || mob.boss) "" else "［${mob.grade.label}］"
        mob.entity.customName = Component.text("Lv$encounterLevel $grade" + base + suffix, color)
    }

    /** Pixel HUD: bosses draw on the boss bar (see [bossHud]), every other mob on its overhead plate. */
    private fun showHud(mob: Mob) {
        mob.entity.isCustomNameVisible = false
        if (mob.boss || !mob.life.isAlive || !isSpawned(mob)) return
        plates.show(mob.entity, dev.projects.server.coreloop.ui.CoreMobNameplate.render(hud(mob)))
    }

    private fun hud(mob: Mob): MobHud {
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        val effects = buildList {
            if (mob.enraged || frenzied(mob)) add(MobHudEffect(MobHudEffectKind.BUFF, "rage"))
            if (now < mob.exposedUntil) add(MobHudEffect(MobHudEffectKind.DEBUFF, "exposed"))
            if (now < mob.slowedUntil && mob.slowPercent > 0.0) add(MobHudEffect(MobHudEffectKind.DEBUFF, "slow"))
            if (now < mob.tauntedUntil) add(MobHudEffect(MobHudEffectKind.DEBUFF, "taunt"))
            mob.crowd.filterValues { it > now }.keys.sortedBy { it.ordinal }
                .forEach { add(MobHudEffect(MobHudEffectKind.CC, it.name.lowercase())) }
        }
        val breaks = mob.boss || mob.grade.breaks
        return MobHud(encounterLevel, mob.definition.name, mob.grade, mob.mods, mob.life.health, mob.life.maximumHealth,
            breakGauge = if (breaks) mob.breakGauge / BREAK_MAX else null,
            broken = if (now < mob.brokenUntil) (mob.brokenUntil - now).toDouble() / BREAK_MILLIS else null,
            effects = effects,
            notice = mob.notice ?: if (mob.life.phase == QuestMobPhase.RETURNING) "帰還中" else null)
    }

    /** What the boss bar shows for the boss (null once there is none). */
    internal fun bossHud(): MobHud? = mobs.values.firstOrNull { it.boss }?.let(::hud)

    internal fun hudOf(targetId: UUID): MobHud? = mobs[targetId]?.let(::hud)
    /** Tests: give a spawned mob a grade and MODs without its rolled health (stats stay as spawned). */
    internal fun forceGrade(targetId: UUID, grade: MobGrade, mods: List<MobMod>) {
        mobs[targetId]?.let { it.grade = grade; it.mods = mods; updateName(it) }
    }
    internal val plateCount: Int get() = plates.count

    private fun frenzied(mob: Mob) = MobMod.FRENZY in mob.mods && mob.life.health <= mob.life.maximumHealth * MobMod.FRENZY_HEALTH

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
        val spread = if (count > 8) 1.5f else 0f
        val packet = ParticlePacket(type, position.x(), position.y(), position.z(), spread, spread, spread, 0f, count)
        instance.players.filter { it.position.distanceSquared(position) < 56.0 * 56.0 }.forEach { it.sendPacket(packet) }
    }

    companion object {
        /** A strafe walks at this share of the walking speed, the pace its clip is timed to (1.6 of 2.6 blocks/s). */
        const val STRAFE_SPEED = .62
        /** The enrage wave reaches the arena wall (radius 22) as the light finishes turning violet. */
        const val RAGE_WAVE_RADIUS = 22.0
        val RAGE_WAVE_MILLIS = dev.projects.server.coreloop.adventure.BossArenaFactory.RAGE_FADE_TICKS * 50L
        val GLOW: net.kyori.adventure.key.Key = net.kyori.adventure.key.Key.key("projects", "shade_glow")
        /** Gimmicks: horn break (12 % of max health from near the head), fan tear (10 % per side). */
        const val HEAD_REACH = 6.0
        const val HORN_BREAK = .12
        const val FAN_TEAR = .10
        const val SLOPPY_MILLIS = 20_000L
        const val TORN_SWEEP_RADIUS = 4.5
        /** Search: the creeping walk's share of the walking speed (its clip is timed to it), and when it starts. */
        const val SEARCH_SPEED = .3
        const val SEARCH_BEYOND = 14.0
        const val TURN_SWEEP = "shade-turnsweep"
        /** Got behind it (usually while an attack holds its facing) for this long when it is free to act. */
        const val TURN_AFTER_MILLIS = 600L
        /** ms into shade-dive while the body is carried over the target (the clip hangs in the air then). */
        val DIVE_CARRY = 1000L..2300L
        const val BREAK_MAX = 100.0
        const val BREAK_MILLIS = 4000L
        const val BROKEN_DAMAGE = 1.3
    }

    private fun sound(position: Pos, key: String, volume: Float, pitch: Float) {
        val sound = Sound.sound(net.kyori.adventure.key.Key.key(key), Sound.Source.HOSTILE, volume, pitch)
        instance.players.filter { it.position.distanceSquared(position) < 40.0 * 40.0 }
            .forEach { it.playSound(sound, position) }
    }
}
