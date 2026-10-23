package dev.projects.server.mob

import dev.projects.server.coreloop.CoreDamageType
import dev.projects.server.coreloop.CoreCombatMath
import dev.projects.server.coreloop.CoreAffixStats
import net.kyori.adventure.bossbar.BossBar
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

/** GROWL, SHAKE and SCRAPE stand in place (桜牙獣's moves between attacks); STALK creeps in on a far target. */
internal enum class ManeuverKind { STRAFE, HOP_BACK, DASH, RELOCATE, ADVANCE, GROWL, SHAKE, SCRAPE, STALK }

/** An attack that carries the body (2026-10-19, A): moved between [from] and [to] ms, [reach] blocks for a target that far. */
private class Travel(val from: Long, val to: Long, val reach: (Double) -> Double)

/** The second-form roar holds this long (the `awaken` clip). */
private const val AWAKEN_HOLD_MILLIS = 3000L

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
    /** Receives an ability's crowd control for each player it hits, with the attack's origin. */
    private val crowdPlayer: ((Player, CrowdControl, Long, Pos) -> Unit)? = null,
    /** True while the player's dodge makes them untouchable: a move that only met dodges whiffs ([openAfter]). */
    private val evading: (Player) -> Boolean = { false },
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
        /** Picks the moves between attacks and the breathers (桜牙獣). */
        val moveRandom = Random(entity.uuid.mostSignificantBits)
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
        /** When crowd control last wore the break gauge: it refills after [BREAK_REFILL_AFTER_MILLIS] without. */
        var lastCcAt = 0L
        /** The target's position a tick ago, to guess where it is going (move choice, see [situationWeight]). */
        var lastTargetPos: Pos? = null
        var prevTargetPos: Pos? = null
        var brokenUntil = 0L
        var guardianIds = emptySet<UUID>()
        @Volatile var spawnFailure: Throwable? = null
        @Volatile var model: WardenModel? = null
        /** Blockbench (WSEE) body, created after the carrier entity is in the instance. */
        @Volatile var view: BossModelView? = null
        var enraged = false
        var enrageHoldUntil = 0L
        /** 第2形態 (glowing) since its roar; see [tickAwaken]. */
        var awakened = false
        var awakenHoldUntil = 0L
        var tailslapAt = 0L
        var nextRelocateAt = 0L
        /** An attack just ended: move (strafe, dart, hop, fly) before the next one. */
        var moveAfterAttack = false
        /**
         * 後隙 (2026-10-19, 藍「読んで避けた人に殴る時間を」): after a move it stands open — no turning, moving or
         * next move — until then. Longer after a heavy move or a whiff, shorter as it grows angry ([openAfter]).
         */
        var openUntil = 0L
        /** Someone not dodging was struck by the move in progress; a move that struck nobody near it whiffs. */
        var struckThisMove = false
        /** The counter glow (光る溜め) of the move in progress has shown: a break skill now knocks it down. */
        var counterShown = false
        /** MMO gimmicks (see [tickGimmickCast]): when the next cast / shell comes, what has been dealt into them. */
        var nextChannelAt = 0L
        var nextShellAt = 0L
        var castDamage = 0.0
        var shellHealth = 0.0
        /** Knocked down (the cast broken, the shell shattered): no attacks, no movement until then. */
        var downUntil = 0L
        /** Model bosses: a move round the target between attacks (see [startManeuver]). */
        var maneuver: Maneuver? = null
        var nextManeuverAt = 0L
        var maneuvers = 0
        var lastManeuver: ManeuverKind? = null
        /** 桜牙獣: after an attack (and its combo) it moves round its prey until then instead of attacking again. */
        var restUntil = 0L
        /** Where the model looks while it moves sideways or backward: at its target. */
        var faceYaw = 0f
        /** Since when the target has stood behind the model (0 = not behind); see [TURN_AFTER_MILLIS]. */
        var behindSince = 0L
        /** An ability to follow the maneuver that just ended (hop back -> breath, dash -> sweep). */
        var combo: String? = null
        /** Where an ability that carries the body (the dive) took off from. */
        var travelFrom: Pos? = null
        /** Where a body-carrying attack ([TRAVEL]) ends, fixed when its move starts (the aim is locked by then). */
        var travelTo: Pos? = null
        var nextAdvanceAt = 0L
        /** A combo waits up to [COMBO_WAIT_MILLIS] for the global cooldown instead of being dropped (review 2026-10-19). */
        var comboSince = 0L
        /** Since when it has not seen its target (a pillar between): it flies round after [LOST_SIGHT_MILLIS]. */
        var lostSightSince = 0L
        /** Players the running charge has already struck (one hit each, along the body's path). */
        val chargeHits = mutableSetOf<UUID>()
        /** Lost its target (far or out of sight): it creeps and searches. */
        var searching = false
        /** Enraged parts (gimmicks): damage dealt near the head and on each side, and what has broken. 0 = its right fan. */
        var hornDamage = 0.0
        var hornsBroken = false
        /** 桜牙獣: the counter window of the strike being wound up (ms, 0 = none), and its torn tail shells. */
        var counterFrom = 0L
        var counterTo = 0L
        var tailDamage = 0.0
        var tailTorn = false
        /** 桜紋の陣: when the next one starts, when the running one resolves (0 = none), its crests, the shown count. */
        var sigilNextAt = 0L
        var sigilEndsAt = 0L
        var sigilCrests: List<Pos> = emptyList()
        var sigilShown = -1L
        /** 千本桜 (the last 25%): started, when it blows (0 = not running), the shown count. */
        var finaleStarted = false
        var finaleEndsAt = 0L
        var finaleShown = -1L
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
    /** 桜牙獣's petal storm and strike effects (only built when the encounter has one). */
    private var sakuraFx: SakuraPetalFx? = null
    private var sakuraStrike: SakuraStrikeFx? = null
    /** The post effects each player has on (26.3 /posteffect): the shade glow (assets/projects/post_effect/shade_glow.json)
     *  and the sakura light (SakuraLight). One packet sets the whole list, so both go through [tickGlow]. */
    private val glowing = mutableMapOf<Player, List<net.kyori.adventure.key.Key>>()
    /** 桜牙獣's own pink light, carried at its chest (SakuraLight); gone with its death. */
    private var sakuraLight: SakuraLight? = null
    private var sakuraLightEnraged = false
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
    /** This player's erosion as a share of the eruption threshold (0..1), for the boss bar. */
    internal fun erosionShare(player: UUID): Double = (gimmicks.erosionOf(player) / ShadeWyrmGimmicks.FULL).coerceIn(0.0, 1.0)
    internal val poolCount: Int get() = gimmicks.poolCount
    /** Fight preview dump: the model's clip and the shader displays on show. */
    internal fun shownClip(targetId: UUID): Pair<String, Long>? = mobs[targetId]?.view?.let { it.shownClip to it.shownSince }
    internal fun modelFacing(targetId: UUID): Float? = mobs[targetId]?.let { if (it.maneuver != null) it.faceYaw else it.entity.position.yaw() }
    internal fun shaderViews() = shaderFx.views()
    internal fun lightsOn() = lights.snapshot()
    internal fun isAwakened(targetId: UUID): Boolean = mobs[targetId]?.awakened ?: false
    internal fun isGlowing(targetId: UUID): Boolean = mobs[targetId]?.view?.glowing ?: false
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
    internal fun healthOf(targetId: UUID): Double = mobs[targetId]?.life?.health ?: 0.0
    /** Tests: start [abilityId] now at the mob's current target (or the nearest player), as a combo would. */
    internal fun startAbilityForTest(targetId: UUID, abilityId: String, now: Long): Boolean {
        val mob = mobs[targetId] ?: return false
        val target = mob.target ?: instance.players.minByOrNull { it.position.distanceSquared(mob.entity.position) } ?: return false
        mob.target = target
        // Whatever the free AI was doing (a flight, the pause between moves, an opening) ends here.
        mob.maneuver = null; mob.combo = null; mob.openUntil = 0L
        mob.abilities.reset(); clearWarning(mob.entity.uuid)
        val start = mob.abilities.tryStart(now, mob.entity.position, target.position, mob.life.health / mob.life.maximumHealth, only = abilityId) ?: return false
        mob.life.phase = QuestMobPhase.ATTACKING
        return handleAbilityEvent(mob, start, instance.players.toList())
    }
    /** Tests: a move is in progress, and how long it still stands open (後隙) after one. */
    internal fun busy(targetId: UUID): Boolean = mobs[targetId]?.abilities?.isActive == true
    internal fun openFor(targetId: UUID): Long = mobs[targetId]?.let { it.openUntil - lastTickAt } ?: 0L
    fun bossName(): String = mobs.values.firstOrNull { it.boss }?.definition?.name ?: ""
    fun bossEnrageRatio(): Double = mobs.values.firstOrNull { it.boss }?.definition?.enrageHealthRatio ?: 0.0
    /** Attackers whose latest hit landed on a weak part (the wyrm's head); read once by the attacker's feedback. */
    private val weakSpotHits = mutableSetOf<UUID>()
    fun takeWeakSpot(attacker: Player): Boolean = weakSpotHits.remove(attacker.uuid)

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
            if (mob.abilities.current?.ability?.id in GIMMICKS) return true      // the cast / shell cannot be pulled away
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
        if (mob.boss) return breakBoss(mob, targetId, kind.breakDamage, now, cc = kind)
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
            // About 3 blocks up for 0.7 s in the air; a shorter launch is lower, a longer one higher (地割り by its beast).
            CrowdControl.AIRBORNE -> { mob.entity.navigator.reset()
                mob.entity.velocity = Vec(0.0, 14.0 * kotlin.math.sqrt(durationMillis.coerceIn(200L, 1500L) / 700.0), 0.0) }
            CrowdControl.KNOCKBACK -> slide(mob, normalizeHorizontal(mob.entity.position.sub(source.position)), distance)
            else -> Unit
        }
        updateName(mob)
        return true
    }
    /**
     * Wears [amount] off a boss's break gauge from a hit that already landed (players' break, CoreBreak).
     * Regular enemies have no gauge. Returns false when nothing could be broken (not a boss, already broken, sealed).
     */
    fun applyBreak(targetId: UUID, amount: Double, source: Player): Boolean {
        if (disposed || !amount.isFinite() || amount <= 0.0) return false
        val mob = mobs[targetId] ?: return false
        if (!mob.boss || !mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING || bossSealed || mob.guardianIds.any(::isAlive) ||
            source.instance !== instance || !canTarget(source)) return false
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        return breakBoss(mob, targetId, amount, now)
    }

    /**
     * The one place a boss's break gauge wears down: crowd control ([cc], applyCrowdControl) and players' per-hit
     * break (applyBreak, CoreBreak). Crowd control landing in a glowing charge's window is a counter and knocks the
     * boss down; during a big cast or a shell crowd control counts toward stopping it instead. Neither path breaks
     * the gauge during those gimmicks: they end by their own rule.
     */
    private fun breakBoss(mob: Mob, targetId: UUID, amount: Double, now: Long, cc: CrowdControl? = null): Boolean {
        if (now < mob.brokenUntil) return false
        if (cc != null && counterOpen(mob, now)) {
            knockDown(mob, now, "カウンター・殴り放題", COUNTER_DOWN_MILLIS)
            return true
        }
        mob.breakGauge = (mob.breakGauge - amount).coerceAtLeast(0.0)
        mob.lastCcAt = now  // any break, CC or hit, holds off the refill (BREAK_REFILL_AFTER_MILLIS)
        // A cast / shell is broken by its own rule (damage), never by the break gauge; a break tool still counts
        // toward that damage, so choosing the right skill matters during the gimmick.
        val gimmick = mob.abilities.current?.ability?.id
        if (gimmick in GIMMICKS) {
            mob.breakGauge = maxOf(mob.breakGauge, 1.0)
            if (cc != null) {
                val share = mob.life.maximumHealth * (if (cc.hard) CC_CAST_HARD else CC_CAST_SOFT)
                if (gimmick == CHANNEL) {
                    mob.castDamage += share
                    if (mob.castDamage >= mob.life.maximumHealth * CAST_SHARE) knockDown(mob, now, "詠唱を止めた・殴り放題")
                } else if (gimmick == SHELL && mob.shellHealth > 0.0) {
                    mob.shellHealth -= share
                    if (mob.shellHealth <= 0.0) knockDown(mob, now, "殻が割れた・殴り放題")
                }
            }
            updateName(mob)
            return true
        }
        if (mob.breakGauge <= 0.0) {
            mob.brokenUntil = now + BREAK_MILLIS
            mob.maneuver = null
            mob.breakGauge = BREAK_MAX
            mob.abilities.cancel(); clearWarning(targetId); castingPose(mob, false); stopNavigation(mob)
            mob.life.phase = QuestMobPhase.CHASING
            sound(mob.entity.position, "minecraft:block.anvil.land", 1.0f, 0.5f)
            particle(mob.entity.position.add(0.0, 1.0, 0.0), Particle.EXPLOSION, 2)
        }
        updateName(mob)
        return true
    }

    /** 引き摺り (warrior): a hooked mob is dragged along toward [point], as far as the ground allows. Bosses stay put. */
    fun dragTo(targetId: UUID, point: Pos, step: Double = .7): Boolean {
        val mob = mobs[targetId] ?: return false
        if (mob.boss || !mob.life.isAlive || mob.life.phase == QuestMobPhase.RETURNING) return false
        val delta = Vec(point.x() - mob.entity.position.x(), 0.0, point.z() - mob.entity.position.z())
        if (delta.length() < .3) return true
        slide(mob, delta.normalize(), minOf(step, delta.length()))
        return true
    }
    /** 叩き落とし (warrior): a mob up in the air is driven straight down. Bosses never leave the ground, so nothing. */
    fun slamDown(targetId: UUID): Boolean {
        val mob = mobs[targetId] ?: return false
        if (mob.boss || !mob.life.isAlive) return false
        mob.entity.velocity = Vec(0.0, -30.0, 0.0)
        return true
    }
    fun crowdControl(targetId: UUID): Set<CrowdControl> {
        val now = if (lastTickAt == Long.MIN_VALUE) System.currentTimeMillis() else lastTickAt
        return mobs[targetId]?.crowd?.filterValues { it > now }?.keys ?: emptySet()
    }
    /** 桜牙獣's open counter window (ms), for tests. */
    internal fun counterWindow(targetId: UUID): LongRange? = mobs[targetId]?.takeIf { it.counterTo != 0L }?.let { it.counterFrom..it.counterTo }
    internal fun partsTorn(targetId: UUID): Pair<Boolean, Boolean>? = mobs[targetId]?.let { it.hornsBroken to it.tailTorn }
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
        val part = if (effect || fieldOrigin != null || !mob.shade) 1.0 else partMultiplier(mob, attacker, projectile)
        if (part > 1.0) weakSpotHits += attacker.uuid else weakSpotHits -= attacker.uuid
        val mitigated = amount * part * (if (guarded) mob.definition.frontalDamageMultiplier else 1.0) *
            (if (now < mob.brokenUntil) BROKEN_DAMAGE else 1.0) *
            (if (MobMod.IRONHIDE in mob.mods) MobMod.IRONHIDE_TAKEN else 1.0)
        val gated = if (mob.boss) minOf(mitigated, (mob.life.health - mob.life.maximumHealth * bossGateFraction).coerceAtLeast(0.0)).let {
            // 桜牙獣 cannot be pushed past 25% before its 千本桜 starts
            if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG && !mob.finaleStarted)
                minOf(it, (mob.life.health - mob.life.maximumHealth * FINALE_RATIO).coerceAtLeast(0.0)) else it
        } else mitigated
        val gimmick = mob.abilities.current?.ability?.id
        if (gimmick == SHELL && mob.shellHealth > 0.0) {
            // 鱗粉の殻: the shell takes the hit, not the wyrm.
            mob.lastAttacker = attacker
            gimmicks.playerStruck(attacker)                    // attacking the shell still clears erosion
            mob.view?.hurt(now)
            mob.shellHealth -= mitigated
            if (mob.shellHealth <= 0.0) knockDown(mob, now, "殻が割れた・殴り放題")
            return mitigated
        }
        if (!mob.life.damage(gated)) return null
        val applied = healthBefore - mob.life.health
        mob.lastAttacker = attacker
        mob.hurtAt = now
        if (gimmick == CHANNEL && mob.life.isAlive) {
            mob.castDamage += applied
            if (mob.castDamage >= mob.life.maximumHealth * CAST_SHARE) knockDown(mob, now, "詠唱を止めた・殴り放題")
        }
        if (mob.shade && applied > 0.0 && mob.life.isAlive) {
            gimmicks.playerStruck(attacker)
            breakParts(mob, attacker, applied, now)
        }
        if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG && applied > 0.0 && mob.life.isAlive) sakuraHit(mob, attacker, applied, now)
        if (!mob.life.isAlive) {
            endGimmick(mob)
            if (mob.shade) { gimmicks.dispose(); lights.hold("horns-${mob.entity.uuid}", null, 0) }
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
            if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) sakuraFx?.death()
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
        if (mobs.values.any { it.shade && it.life.isAlive }) gimmicks.tick(nowMillis, instance.players.filter { canTarget(it) }) { p, amount, type ->
            if (typedDamagePlayer != null) typedDamagePlayer.invoke(p, amount, type) else damagePlayer(p, amount)
        }
        mobs.values.firstOrNull { it.definition.archetype == QuestMobArchetype.SAKURA_FANG }?.let { mob ->
            // Ticks on after the death too, so the falling petals finish.
            val fx = sakuraFx ?: SakuraPetalFx(instance).also { sakuraFx = it }
            val view = mob.view
            // the petals take the model's facing (a strafe or dash faces the target, not the way the carrier walks)
            fx.tick(if (mob.maneuver != null) mob.entity.position.withView(mob.faceYaw, 0f) else mob.entity.position, mob.enraged) { bone, px -> view?.point(bone, px) }
            (sakuraStrike ?: SakuraStrikeFx(instance).also { sakuraStrike = it }).tick()
            tickSakuraLight(mob)
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
            if (mob.shade) tickRageWave(mob, nowMillis)
            if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG && mob.enraged) tickSakuraRoar(mob, nowMillis)
            if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) tickCounterGlow(mob, nowMillis)
            if (mob.shade) lights.hold("horns-${mob.entity.uuid}",
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
        hideGimmickBars()
        glowing.keys.forEach { it.sendPacket(net.minestom.server.network.packet.server.common.PostEffectsPacket(emptyList())) }
        glowing.clear()
        sakuraLight?.remove()
        shaderFx.dispose()
        sakuraFx?.dispose()
        sakuraStrike?.dispose()
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
        // The break gauge comes back (5 a second) once crowd control has left it alone for a while (review 2026-10-19).
        if (mob.boss && mob.breakGauge < BREAK_MAX && now - mob.lastCcAt > BREAK_REFILL_AFTER_MILLIS)
            mob.breakGauge = minOf(BREAK_MAX, mob.breakGauge + BREAK_REFILL_PER_SECOND / 20.0)
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
        if (tickAwaken(mob, now)) return
        if (tickEnrage(mob, now)) return
        tickGimmickBars(mob, now)
        if (now < mob.downUntil) { stopNavigation(mob); return }
        if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG && (tickFinale(mob, now) || tickSigil(mob, now))) return
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
            for (event in mob.abilities.tick(now, aimAt(mob, target, now), if (mob.shade) TURN_PER_TICK else null)) {
                if (!handleAbilityEvent(mob, event, players)) return
                if (disposed || !mob.life.isAlive) return
            }
            val frame = mob.abilities.current?.let { effectiveFrame(mob, it) }
            if (frame != null) {
                // The tail slap strikes behind without turning: the body keeps its back to the player.
                mob.entity.setView(Pos.ZERO.withDirection(frame.facing).yaw() + if (frame.ability.id == TAIL_SLAP) 180f else 0f, 0.0f)
                carryThroughAbility(mob, frame, now)
                behindFor(mob, target, now)                // standing behind it during an attack counts too
                if (mob.shade && !mob.counterShown && frame.phase != MobAbilityPhase.RECOVERY &&
                    counterWindow(frame.ability)?.let { now - frame.startedAt >= it.first } == true) {
                    mob.counterShown = true
                    counterGlow(mob)
                }
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
        if (now < mob.openUntil && !gimmickDueNow(mob, now)) { stopNavigation(mob); return }
        val seen = mob.entity.hasLineOfSight(target)
        val resting = mob.combo == null && now < mob.restUntil
        mob.prevTargetPos = mob.lastTargetPos ?: target.position
        mob.lastTargetPos = target.position
        if (seen) mob.lostSightSince = 0L else if (mob.lostSightSince == 0L) mob.lostSightSince = now
        if (now >= mob.warningRetryAt && seen && telegraphs.canStart(mob.entity.uuid)) {
            val ratio = mob.life.health / mob.life.maximumHealth
            val silenced = crowded(mob, CrowdControl.SILENCE, now)
            val shellDue = mob.enraged && mob.nextShellAt != 0L && now >= mob.nextShellAt && ratio <= mob.definition.enrageHealthRatio
            val channelDue = mob.awakened && mob.nextChannelAt != 0L && now >= mob.nextChannelAt && ratio <= mob.definition.awakenHealthRatio
            val gimmickDue = mob.shade && (shellDue || channelDue)
            if (mob.shade && mob.moveAfterAttack && mob.combo == null && !gimmickDue) {
                mob.moveAfterAttack = false
                if (!silenced && startManeuver(mob, target, now)) return
            }
            val behind = behindFor(mob, target, now)
            // The gimmicks come when due, ahead of a combo; then a combo; then someone at its back.
            val scripted = if (silenced) null else when {
                mob.shade && shellDue -> SHELL
                mob.shade && channelDue -> CHANNEL
                mob.combo != null -> mob.combo
                mob.shade && behind >= TAIL_SLAP_AFTER_MILLIS && now - mob.tailslapAt > TAIL_SLAP_AGAIN_MILLIS -> TAIL_SLAP
                behind >= TURN_AFTER_MILLIS -> if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) SAKURA_TURN else TURN_SWEEP
                else -> null
            }
            val aim = aimAt(mob, target, now)
            val scriptedStart = scripted?.let { mob.abilities.tryStart(now, mob.entity.position, aim, ratio, only = it,
                facing = if (mob.shade && it != TURN_SWEEP) mob.entity.position.direction() else null) }
            // A combo that could not start yet (the 0.35 s between moves) waits for it rather than being lost or
            // replaced by a random move (the combos never fired before the 2026-10-19 review).
            if (scriptedStart == null && scripted != null && scripted == mob.combo) {
                if (mob.comboSince == 0L) mob.comboSince = now
                if (now - mob.comboSince < COMBO_WAIT_MILLIS) return
            }
            mob.combo = null; mob.comboSince = 0L
            val bodyFacing = if (mob.shade) mob.entity.position.direction() else null
            val start = scriptedStart ?: mob.abilities.tryStart(now, mob.entity.position, aim, ratio, plainOnly = silenced,
                facing = bodyFacing, weigh = if (mob.shade) { a -> situationWeight(mob, target, a) } else null,
                allowed = if (resting) SAKURA_LIGHT else null)    // 桜牙獣 resting: only its small blows
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
        endGimmick(mob)
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

    /**
     * 第2形態 (2026-10-19): at the definition's awaken ratio, once, before any enrage: the attack stops, the
     * `awaken` roar is held (no attacks: a chance to hit it), the body starts to glow; then a little faster, with the
     * phase-two abilities (their maximumHealthRatio) unlocked by the health ratio itself.
     */
    private fun tickAwaken(mob: Mob, now: Long): Boolean {
        val threshold = mob.definition.awakenHealthRatio
        if (!mob.awakened) {
            if (threshold <= 0.0 || mob.enraged || mob.life.health / mob.life.maximumHealth > threshold) return false
            if (airborne(mob)) return false                       // lands first, then roars
            mob.awakened = true
            mob.maneuver = null
            mob.awakenHoldUntil = now + AWAKEN_HOLD_MILLIS
            mob.abilities.cancel()
            clearWarning(mob.entity.uuid)
            castingPose(mob, false)
            stopNavigation(mob)
            mob.view?.awaken(now)
            mob.warningRetryAt = mob.awakenHoldUntil
            mob.nextRelocateAt = mob.awakenHoldUntil + 4000              // flies off soon after it wakes
            mob.nextChannelAt = mob.awakenHoldUntil + CHANNEL_FIRST_MILLIS
            updateName(mob, "第2形態・鱗粉が光る")
            sound(mob.entity.position, "minecraft:entity.ravager.roar", 1.1f, 0.8f)
            particle(mob.entity.position.add(0.0, 2.0, 0.0), Particle.DUST.withColor(NamedTextColor.LIGHT_PURPLE).withScale(1.6f), 30)
            updateMovementSpeed(mob, now)
            return true
        }
        if (mob.enraged || now >= mob.awakenHoldUntil) return false
        stopNavigation(mob)
        return true
    }

    /**
     * MMO gimmicks (2026-10-19, 藍が選んだ2つ): 翳の大詠唱 (second form on, every [CHANNEL_AGAIN_MILLIS]) hangs in the air
     * for 6 s; [CAST_SHARE] of its health dealt in that time interrupts it. 鱗粉の殻 (enraged, every [SHELL_AGAIN_MILLIS])
     * holds 12 s with a shell of [SHELL_SHARE] that soaks the damage. Either broken: knocked down for [DOWN_MILLIS]
     * (broken: +30 % damage taken). Not broken: the ability's own hit (the arena blast / the shell burst).
     */
    private fun knockDown(mob: Mob, now: Long, label: String, millis: Long = DOWN_MILLIS) {
        val shell = mob.abilities.current?.ability?.id == SHELL
        mob.abilities.cancel()
        clearWarning(mob.entity.uuid)
        castingPose(mob, false)
        stopNavigation(mob)
        if (shell) shaderFx.shellBreak(mob.entity.position, gimmickTag(mob)) else shaderFx.endTag(gimmickTag(mob))
        mob.shellHealth = 0.0
        mob.downUntil = now + millis
        mob.brokenUntil = maxOf(mob.brokenUntil, now + millis)
        mob.openUntil = 0L
        mob.warningRetryAt = mob.downUntil
        mob.view?.maneuver("down")
        hideGimmickBars()
        updateName(mob, label)
        sound(mob.entity.position, "minecraft:block.anvil.land", 1.0f, 0.5f)
        sound(mob.entity.position, "minecraft:entity.ravager.stunned", 1.2f, 0.7f)
    }

    private val castBar: BossBar = BossBar.bossBar(Component.text("翳の大詠唱", NamedTextColor.LIGHT_PURPLE), 0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS)
    private val gaugeBar: BossBar = BossBar.bossBar(Component.text("止める量", NamedTextColor.GOLD), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10)
    private val barViewers = mutableSetOf<Player>()

    private fun gimmickTag(mob: Mob) = "gimmick-${mob.entity.uuid}"

    /** The cast's / shell's two bars: time left, and how far the interrupt / the shell has got. */
    private fun tickGimmickBars(mob: Mob, now: Long) {
        val frame = mob.abilities.current
        if (frame == null || frame.ability.id !in GIMMICKS) {
            // Ended or cut short some other way (a part broke, a phase change, a break): nothing left on screen.
            if (barViewers.isNotEmpty() || mob.shellHealth > 0.0) endGimmick(mob)
            return
        }
        val id = frame.ability.id
        val k = ((now - frame.startedAt).toFloat() / frame.ability.telegraphMillis).coerceIn(0f, 1f)
        val left = ((frame.ability.telegraphMillis - (now - frame.startedAt)) / 1000.0).coerceAtLeast(0.0)
        castBar.name(Component.text("${if (id == SHELL) "鱗粉の殻" else "翳の大詠唱"}  ${"%.1f".format(left)}秒", NamedTextColor.LIGHT_PURPLE))
        castBar.progress(1f - k)
        gaugeBar.name(Component.text(if (id == SHELL) "殻の耐久" else "止める量", NamedTextColor.GOLD))
        gaugeBar.progress(if (id == SHELL) (mob.shellHealth / (mob.life.maximumHealth * SHELL_SHARE)).toFloat().coerceIn(0f, 1f)
            else (mob.castDamage / (mob.life.maximumHealth * CAST_SHARE)).toFloat().coerceIn(0f, 1f))
        val viewers = instance.players.toSet()
        for (p in viewers - barViewers) { p.showBossBar(castBar); p.showBossBar(gaugeBar) }
        barViewers += viewers
    }

    /** Everything a cast / shell left on screen goes (death, a return, any cancel). */
    private fun endGimmick(mob: Mob) {
        hideGimmickBars()
        shaderFx.endTag(gimmickTag(mob))
        mob.shellHealth = 0.0
    }

    private fun hideGimmickBars() {
        for (p in barViewers) { p.hideBossBar(castBar); p.hideBossBar(gaugeBar) }
        barViewers.clear()
    }

    /** The cast / shell showing right now (preview dump): name, time left 0..1, gauge 0..1; null when none. */
    internal fun gimmickBars(): Triple<String, Float, Float>? = mobs.values.firstNotNullOfOrNull { mob ->
        mob.abilities.current?.takeIf { it.ability.id in GIMMICKS }?.let { Triple(it.ability.id, castBar.progress(), gaugeBar.progress()) }
    }
    internal fun isDown(targetId: UUID): Boolean = mobs[targetId]?.let { lastTickAt < it.downUntil } ?: false

    /**
     * Each move announces itself its own way (review 2026-10-19: the small moves have no floor warning, so the sound
     * and a glow on the part that will strike are how the player reads them).
     */
    /** How long it stands open after [id]: heavier moves longer, a whiff longer still, anger shortens the plain part. */
    private fun openAfter(mob: Mob, id: String, whiffed: Boolean): Long {
        val anger = if (mob.enraged) ENRAGED_OPENING else if (mob.awakened) AWAKENED_OPENING else 1.0
        val whiff = if (!whiffed) 0L else if (id in BIG_WHIFF) BIG_WHIFF_MILLIS else WHIFF_MILLIS
        return ((OPENING[id] ?: 0L) * anger).toLong() + whiff
    }

    /** A whiff: it stumbles, off balance (the smoke and the groan say so). */
    private fun stagger(mob: Mob) {
        updateName(mob, "空振り")
        sound(mob.entity.position, "minecraft:entity.ravager.stunned", 1.0f, 1.1f)
        particle(mob.entity.position.add(0.0, 0.4, 0.0), Particle.CLOUD, 10)
    }

    /** The gimmicks never wait for an opening to end. */
    private fun gimmickDueNow(mob: Mob, now: Long): Boolean {
        if (!mob.shade) return false
        val ratio = mob.life.health / mob.life.maximumHealth
        return (mob.enraged && mob.nextShellAt != 0L && now >= mob.nextShellAt && ratio <= mob.definition.enrageHealthRatio) ||
            (mob.awakened && mob.nextChannelAt != 0L && now >= mob.nextChannelAt && ratio <= mob.definition.awakenHealthRatio)
    }

    /**
     * 光る溜め (Lost Ark's counter): [COUNTERABLE] moves flash white for a moment of their wind-up, ending before the
     * body leaves the ground; a break skill (any skill with crowd control) landing then knocks the wyrm down.
     */
    private fun counterWindow(ability: MobAbility): Pair<Long, Long>? {
        if (ability.id !in COUNTERABLE) return null
        val end = (TRAVEL[ability.id]?.from ?: ability.telegraphMillis) - 50L
        return (end - COUNTER_WINDOW_MILLIS) to end
    }

    private fun counterOpen(mob: Mob, now: Long): Boolean {
        val frame = mob.abilities.current ?: return false
        if (!mob.counterShown || frame.phase == MobAbilityPhase.RECOVERY) return false
        val window = counterWindow(frame.ability) ?: return false
        return now - frame.startedAt in window.first..window.second
    }

    private fun counterGlow(mob: Mob) {
        val head = mob.view?.point("skull", ShadeWyrmVfx.CROWN)?.let(Pos::fromPoint) ?: mob.entity.position.add(0.0, 2.5, 0.0)
        sound(head, "minecraft:block.bell.use", 1.2f, 1.6f)
        sound(head, "minecraft:block.amethyst_block.resonate", 1.0f, 1.8f)
        particle(head, Particle.END_ROD, 24)
        particle(head, Particle.ELECTRIC_SPARK, 16)
    }

    private fun windupCue(mob: Mob, frame: MobAbilityFrame) {
        val id = frame.ability.id
        val (sound, pitch) = WINDUP_SOUNDS[id] ?: ("minecraft:block.note_block.hat" to .8f)
        val view = mob.view
        val head = view?.point("skull", ShadeWyrmVfx.SNOUT)?.let(Pos::fromPoint) ?: frame.casterOrigin.add(0.0, 2.0, 0.0)
        sound(head, sound, 1.0f, pitch)
        if (view == null) return
        val ticks = (frame.ability.telegraphMillis / 50).toInt()
        val arm = if (mob.enraged) "cloak_l" else "wing_l_hand"
        val armPx = if (mob.enraged) Vec(60.0, 60.0, 20.0) else Vec(27.0, 21.0, -24.0)
        val spot = when (id) {
            "shade-sweep", "shade-turnsweep" -> view.point(arm, armPx)
            "shade-bite" -> view.point("skull", ShadeWyrmVfx.SNOUT)
            "shade-horn" -> view.point("skull", ShadeWyrmVfx.CROWN)
            "shade-tailspin", TAIL_SLAP -> view.point("tail_end", Vec(-12.0, 5.0, -117.0))
            "shade-pounce" -> view.point("leg_fl_paw", Vec(27.0, 4.0, 48.0))
            else -> null
        } ?: return
        shaderFx.cue(Pos.fromPoint(spot), ticks)
    }

    /**
     * Move choice reads the player (review 2026-10-19): a move that would reach where they are going (their spot half a
     * second on, from their movement) counts three times; one that cannot, a third. The tail spin counts more against
     * someone on its flank, the pounce and the orb more against someone standing still at range.
     */
    private fun situationWeight(mob: Mob, target: Player, ability: MobAbility): Double {
        val here = mob.entity.position
        val last = mob.prevTargetPos ?: target.position
        val ahead = target.position.add(target.position.sub(last).mul(10.0))       // ~0.5 s on at this pace
        val facing = normalizeHorizontal(ahead.sub(here))
        val reach = ability.anchor == MobAbilityAnchor.TARGET || ability.shape.contains(here, facing, ahead)
        var w = if (reach) 3.0 else .35
        val off = Math.toDegrees(kotlin.math.acos(normalizeHorizontal(target.position.sub(here)).dot(normalizeHorizontal(here.direction())).coerceIn(-1.0, 1.0)))
        if (ability.id == "shade-tailspin" && off in 60.0..130.0) w *= 2.0
        val still = target.position.distanceSquared(last) < .0004
        if (still && ability.id in setOf("shade-pounce", "shade-orb", CHARGE) && horizontalLength(target.position.sub(here)) > 6.0) w *= 2.0
        return w
    }

    /** In the air or mid-leap: a phase roar waits for it to land instead of cutting the clip off in the air. */
    private fun airborne(mob: Mob): Boolean = mob.view != null &&
        (mob.abilities.current?.ability?.id in AIRBORNE || mob.maneuver?.kind in setOf(ManeuverKind.RELOCATE, ManeuverKind.ADVANCE, ManeuverKind.HOP_BACK))

    /** One-way rage at the definition's health ratio: a held roar (no attacks), then faster with new abilities. */
    private fun tickEnrage(mob: Mob, now: Long): Boolean {
        val threshold = mob.definition.enrageHealthRatio
        if (!mob.enraged) {
            if (threshold <= 0.0 || mob.life.health / mob.life.maximumHealth > threshold) return false
            if (airborne(mob)) return false
            mob.enraged = true
            mob.maneuver = null
            mob.enrageHoldUntil = now + ENRAGE_HOLD_MILLIS
            mob.abilities.cancel()
            clearWarning(mob.entity.uuid)
            castingPose(mob, false)
            stopNavigation(mob)
            mob.view?.enrage(now)
            if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) sakuraFx?.enrage()
            mob.warningRetryAt = mob.enrageHoldUntil
            if (mob.shade) mob.nextShellAt = mob.enrageHoldUntil + SHELL_FIRST_MILLIS
            updateName(mob, if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) "怒り・花が開いた" else "怒り・翼を広げた")
            sound(mob.entity.position, "minecraft:entity.ravager.roar", 1.2f, 0.6f)
            if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) mob.waveAt = -1L   // petals burst with the roar, see tickSakuraRoar
            else particle(mob.entity.position.add(0.0, 2.0, 0.0), Particle.DUST.withColor(NamedTextColor.DARK_PURPLE).withScale(2.0f), 40)
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
        sakuraStrike?.cancelWindup()
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
            (if (mob.enraged) mob.definition.enragedSpeedMultiplier else if (mob.awakened) mob.definition.awakenSpeedMultiplier else 1.0) *
            (if (frenzied(mob)) MobMod.FRENZY_SPEED else 1.0) *
            (when (mob.maneuver?.kind) { ManeuverKind.STRAFE -> STRAFE_SPEED; ManeuverKind.STALK -> STALK_SPEED; else -> 1.0 }) *
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
        if (!view.canManeuver || now < mob.nextManeuverAt || mob.enraged && now < mob.enrageHoldUntil ||
            mob.awakened && now < mob.awakenHoldUntil) return false
        if (behindFor(mob, target, now) > 0L) return false          // someone at its back: hold still and turn on them
        val here = mob.entity.position
        val distance = horizontalLength(target.position.sub(here))
        val away = normalizeHorizontal(here.sub(target.position))
        val right = Vec(away.z(), 0.0, -away.x())            // the boss's right while it faces the target
        val sakura = mob.definition.archetype == QuestMobArchetype.SAKURA_FANG
        if (mob.nextRelocateAt == 0L) mob.nextRelocateAt = now + RELOCATE_FIRST_MILLIS
        val kind = when {
            mob.lostSightSince != 0L && now - mob.lostSightSince >= LOST_SIGHT_MILLIS && distance > 4.0 && view.has("relocate") -> ManeuverKind.ADVANCE
            now >= mob.nextRelocateAt && distance < 18.0 && view.has("relocate") -> ManeuverKind.RELOCATE
            distance > 10.0 && distance < 30.0 && now >= mob.nextAdvanceAt && view.has("relocate") -> ManeuverKind.ADVANCE
            distance < 3.5 -> if (sakura && mob.lastManeuver == ManeuverKind.HOP_BACK) ManeuverKind.DASH else ManeuverKind.HOP_BACK
            sakura && distance > 10.0 -> ManeuverKind.STALK
            sakura && distance >= 3.5 -> pickSakuraManeuver(mob)
            distance in 4.0..10.0 -> if (mob.maneuvers % 3 == 2) ManeuverKind.DASH else ManeuverKind.STRAFE
            else -> return false
        }
        val sign = if (mob.maneuvers % 2 == 0) 1 else -1
        val leash = mob.definition.leashRange
        val maneuver = when (kind) {
            ManeuverKind.HOP_BACK -> {
                val to = safeRetreat(here.add(away.mul(clearReach(here, away, 4.0))), mob.home, leash) ?: return false
                Maneuver(kind, here, to, now + 280, now + 680, now + 1100, 0)
            }
            ManeuverKind.DASH -> {
                val side = right.mul(sign.toDouble())
                val to = safeRetreat(here.add(side.mul(clearReach(here, side, 5.0))), mob.home, leash)
                    ?: safeRetreat(here.add(side.mul(-clearReach(here, side.mul(-1.0), 5.0))), mob.home, leash) ?: return false
                val toRight = (to.x() - here.x()) * right.x() + (to.z() - here.z()) * right.z() > 0
                Maneuver(kind, here, to, now + 200, now + 500, now + 900, if (toRight) 1 else -1)
            }
            ManeuverKind.STRAFE, ManeuverKind.STALK -> Maneuver(kind, here, here, now, now, now + 1600, sign)
            ManeuverKind.GROWL, ManeuverKind.SHAKE, ManeuverKind.SCRAPE ->
                Maneuver(kind, here, here, now, now, now + SAKURA_STAND_MILLIS.getValue(kind), 0)
            ManeuverKind.RELOCATE -> {
                val to = relocateSpot(mob, target) ?: run { mob.nextRelocateAt = now + 5000; return false }
                Maneuver(kind, here, to, now + 700, now + 2300, now + 3000, 0)
            }
            ManeuverKind.ADVANCE -> {                     // C: the player got away: it flies straight at them
                val toward = normalizeHorizontal(target.position.sub(here))
                val to = safeRetreat(target.position.sub(toward.mul(4.0)), mob.home, leash) ?: return false
                mob.nextAdvanceAt = now + ADVANCE_AGAIN_MILLIS
                Maneuver(kind, here, to, now + 700, now + 2300, now + 3000, 0)
            }
        }
        if (kind == ManeuverKind.RELOCATE) mob.nextRelocateAt = now + if (mob.awakened || mob.enraged) RELOCATE_AGAIN_MILLIS else RELOCATE_CALM_MILLIS
        mob.maneuvers++
        mob.lastManeuver = kind
        mob.nextManeuverAt = maneuver.endAt + if (sakura) SAKURA_MANEUVER_GAP else if (kind == ManeuverKind.STRAFE) 900 else 1700
        mob.maneuver = maneuver
        mob.faceYaw = yawToward(here, target.position)
        if (kind != ManeuverKind.STRAFE && kind != ManeuverKind.STALK) stopNavigation(mob)
        when (kind) {
            ManeuverKind.HOP_BACK -> view.maneuver("hop_back")
            ManeuverKind.DASH -> view.maneuver(if (maneuver.sign > 0) "dash_l" else "dash_r")
            ManeuverKind.STRAFE -> mob.nextPathAt = now
            ManeuverKind.STALK -> { mob.nextPathAt = now; view.maneuver("stalk") }
            ManeuverKind.GROWL -> view.maneuver("growl")
            ManeuverKind.SHAKE -> view.maneuver("shake")
            ManeuverKind.SCRAPE -> view.maneuver("scrape")
            ManeuverKind.RELOCATE, ManeuverKind.ADVANCE -> view.maneuver("relocate")
        }
        updateMovementSpeed(mob, now)
        return true
    }

    /** 桜牙獣 at mid range: circling most often, then a side leap, now and then a growl, a shake or a paw at the
     *  ground; never the same move twice running. */
    private fun pickSakuraManeuver(mob: Mob): ManeuverKind {
        val choices = SAKURA_MANEUVERS.filter { it.first != mob.lastManeuver }
        var roll = mob.moveRandom.nextInt(choices.sumOf { it.second })
        return choices.first { roll -= it.second; roll < 0 }.first
    }

    /**
     * 飛び移り: a spot across the arena, 10-16 blocks from where it stands and as far from the player as the leash
     * allows (it takes the fight elsewhere); null when no spot is open.
     */
    private fun relocateSpot(mob: Mob, target: Player): Pos? {
        val here = mob.entity.position
        return (0 until 12).mapNotNull { k ->
            val a = k * Math.PI / 6
            val r = if (k % 2 == 0) 12.0 else 16.0
            safeRetreat(mob.home.add(kotlin.math.cos(a) * r * .8, 0.0, kotlin.math.sin(a) * r * .8), mob.home, mob.definition.leashRange)
        }.filter { horizontalLength(it.sub(here)) in 8.0..20.0 }
            .maxByOrNull { horizontalLength(it.sub(target.position)) - .3 * horizontalLength(it.sub(here)) }
    }

    /** True while the maneuver holds the mob (no attacks, no chase). */
    private fun tickManeuver(mob: Mob, maneuver: Maneuver, target: Player, now: Long): Boolean {
        if (now >= maneuver.endAt) {
            mob.maneuver = null
            // 桜牙獣 only flows from a move into a blow while it rests; once rested, the next big attack comes first
            mob.combo = if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) if (now >= mob.restUntil) null else when (maneuver.kind) {
                ManeuverKind.HOP_BACK -> if (mob.enraged) "sakura-scatter" else "sakura-lunge"   // 飛び退き→散華／踏み込み爪
                ManeuverKind.DASH -> if (mob.moveRandom.nextBoolean()) "sakura-horn" else "sakura-barge"   // 回り込み→突き上げ／体当たり
                ManeuverKind.STRAFE -> if (mob.moveRandom.nextInt(3) == 0) null else SAKURA_LIGHT.random(mob.moveRandom)
                else -> null
            } else when (maneuver.kind) {
                ManeuverKind.HOP_BACK -> if (mob.enraged) "shade-breath" else if (Random.nextDouble() < .6) "shade-pounce" else null   // 跳び退き→吐息/飛びかかり
                ManeuverKind.DASH -> "shade-sweep"                                   // 3 回り込み→薙ぎ
                ManeuverKind.STRAFE -> null
                // 飛び移り→鱗粉弾 (第2形態から), 吐息 once enraged, else the dust burst where it landed
                ManeuverKind.RELOCATE -> if (mob.enraged) "shade-breath" else if (mob.awakened) "shade-orb" else "shade-pounce"
                ManeuverKind.ADVANCE -> if (Random.nextBoolean()) "shade-bite" else "shade-pounce"   // lands on them biting
                else -> null
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
        } else if (maneuver.kind == ManeuverKind.STALK) {
            if (now >= mob.nextPathAt) {
                mob.entity.navigator.setPathTo(target.position, 6.0, null)
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

    /** The glow post effect is on for exactly the players in an arena whose model boss is alive; the sakura light while
     *  桜牙獣's light stands. */
    private fun tickGlow() {
        val shade = mobs.values.any { it.view != null && it.life.isAlive && it.definition.model == "shade_wyrm" }
        val effects = listOfNotNull(GLOW.takeIf { shade }, SakuraLight.POST.takeIf { sakuraLight != null })
        val wanted = if (effects.isEmpty()) emptyMap() else instance.players.associateWith { effects }
        for ((p, list) in wanted) if (glowing[p] != list)
            p.sendPacket(net.minestom.server.network.packet.server.common.PostEffectsPacket(list))
        for (p in glowing.keys - wanted.keys) p.sendPacket(net.minestom.server.network.packet.server.common.PostEffectsPacket(emptyList()))
        glowing.clear(); glowing += wanted
    }

    /** 桜牙獣 lights the ground round it pink, brighter and wider once enraged; it goes out with the death. The probe is
     *  only re-sent when that changes: a new item every tick makes the light blink. */
    private fun tickSakuraLight(mob: Mob) {
        val at = if (mob.life.isAlive) mob.view?.point("body", SAKURA_LIGHT_AT) else null
        if (at == null) { sakuraLight?.remove(); sakuraLight = null; return }
        val radius = if (mob.enraged) 14 else 11
        val strength = if (mob.enraged) 2.6 else 2.0
        val light = sakuraLight ?: SakuraLight(instance, Pos.fromPoint(at), slot = 0, radius, strength) {
            dev.projects.server.coreloop.CoreCombatPresentation.packed(it) }.also { sakuraLight = it; sakuraLightEnraged = mob.enraged }
        light.moveTo(Pos.fromPoint(at))
        if (sakuraLightEnraged != mob.enraged) { sakuraLightEnraged = mob.enraged; light.set(0, radius, strength) }
    }

    /** With its horns broken it aims badly for a while: its target point swings up to 3 blocks to either side. */
    private fun aimAt(mob: Mob, target: Player, now: Long): Pos {
        if (now >= mob.sloppyUntil) return target.position
        val yaw = Math.toRadians(mob.entity.position.yaw().toDouble())
        return target.position.add(Vec(-kotlin.math.cos(yaw), 0.0, -kotlin.math.sin(yaw)).mul(3.0 * kotlin.math.sin(now / 600.0)))
    }

    /** A torn right fan (the side its wing-arm sweeps with) shortens the sweeps to [TORN_SWEEP_RADIUS]. */
    private fun effectiveFrame(mob: Mob, raw: MobAbilityFrame): MobAbilityFrame {
        // A carried attack strikes from where the body has got to (the charge keeps its band from the start).
        val frame = if (raw.ability.id in TRAVEL && raw.ability.id != "shade-charge" && mob.view != null)
            raw.copy(origin = mob.entity.position) else raw
        val shape = frame.ability.shape
        if (!mob.fanTorn[0] || shape !is MobAttackShape.Sweep || frame.ability.id !in setOf("shade-sweep", "shade-turnsweep")) return frame
        return frame.copy(ability = frame.ability.copy(shape = MobAttackShape.Sweep(minOf(shape.radius, TORN_SWEEP_RADIUS), shape.halfAngleDegrees)))
    }

    /**
     * Enraged part breaks (gimmicks): damage dealt from in front within [HEAD_REACH] of the head wears the horns,
     * from either side the fan on that side. Thresholds are shares of its maximum health.
     */
    /**
     * 部位の手応え: the head takes more, the tail less. Read from where the attacker stands relative to the wyrm,
     * like [breakParts]: melee must be at the head, a shot must come from in front.
     */
    private fun partMultiplier(mob: Mob, attacker: Player, projectile: Boolean): Double {
        val here = mob.entity.position
        val yaw = Math.toRadians((if (mob.maneuver != null) mob.faceYaw else here.yaw()).toDouble())
        val ahead = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
        val off = Vec(attacker.position.x() - here.x(), 0.0, attacker.position.z() - here.z())
        if (off.length() < 1e-6) return 1.0
        val angle = Math.toDegrees(kotlin.math.acos(off.normalize().dot(ahead).coerceIn(-1.0, 1.0)))
        val head = here.add(ahead.mul(4.0))
        return when {
            angle <= HEAD_ANGLE && (projectile || attacker.position.distance(head.withY(attacker.position.y())) <= HEAD_REACH) -> HEAD_MULTIPLIER
            angle >= TAIL_ANGLE -> TAIL_MULTIPLIER
            else -> 1.0
        }
    }

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
        if (frame.ability.id == "shade-bite") { driveForward(mob, frame, now); return }
        TRAVEL[frame.ability.id]?.let { travel(mob, frame, it, now); return }
        // shade-dive and 桜牙獣's pounce land on the target (the frame's origin); the charge runs down its strip.
        val window = when (frame.ability.id) {
            "shade-dive" -> DIVE_CARRY; "sakura-pounce" -> POUNCE_CARRY; "sakura-charge" -> CHARGE_CARRY
            "sakura-lunge" -> LUNGE_CARRY; "sakura-barge" -> BARGE_CARRY; "sakura-hopswipe" -> HOP_CARRY
            else -> return
        }
        val from = mob.travelFrom ?: return
        val elapsed = now - frame.startedAt
        if (elapsed < window.first) return
        val k = ((elapsed - window.first).toDouble() / (window.last - window.first)).coerceIn(0.0, 1.0)
        val e = if (frame.ability.id == "sakura-charge") k else k * k * (3 - 2 * k)
        val shape = frame.ability.shape
        val f = normalizeHorizontal(frame.facing)
        val leash = mob.definition.leashRange
        val to = when (frame.ability.id) {
            "sakura-charge" -> if (shape is MobAttackShape.Slam) safeRetreat(frame.origin.add(f.mul(shape.length * .8)), mob.home, leash) ?: return else return
            // the small blows travel: a spring forward, a shoulder thrown in, a hop to its right
            "sakura-lunge" -> safeRetreat(from.add(f.mul(3.0)), mob.home, leash) ?: return
            "sakura-barge" -> safeRetreat(from.add(f.mul(3.5)), mob.home, leash) ?: return
            "sakura-hopswipe" -> safeRetreat(from.add(Vec(-f.z(), 0.0, f.x()).mul(2.5)), mob.home, leash) ?: return
            else -> frame.origin
        }
        val ground = telegraphs.groundHeight(to) ?: from.y()
        mob.entity.teleport(Pos(from.x() + (to.x() - from.x()) * e, from.y() + (ground - from.y()) * e, from.z() + (to.z() - from.z()) * e,
            mob.entity.position.yaw(), 0f))
    }

    /**
     * The pounce lands on the player, the bites drive forward, the horn thrusts in, the charge bounds across the arena:
     * the carrier moves with the clip (between the [Travel] window), so the wyrm comes at the player instead of
     * standing still while the floor warns (藍「動き回らない・離れては殴るの繰り返し」).
     */
    private fun travel(mob: Mob, frame: MobAbilityFrame, travel: Travel, now: Long) {
        val from = mob.travelFrom ?: return
        val elapsed = now - frame.startedAt
        if (elapsed < travel.from) return
        val dir = normalizeHorizontal(frame.facing)
        val to = mob.travelTo ?: run {
            val distance = mob.target?.let { horizontalLength(it.position.sub(from)) } ?: 6.0
            var reach = clearReach(from, dir, travel.reach(distance))
            var spot: Pos? = null
            while (spot == null && reach > 0.5) {               // stop short of the arena wall and of a pillar
                spot = safeRetreat(from.add(dir.mul(reach)), mob.home, mob.definition.leashRange)
                reach -= 1.0
            }
            (spot ?: from).also { mob.travelTo = it }
        }
        if (frame.ability.id == CHARGE && elapsed <= travel.to) {
            // The charge strikes whoever the running body reaches, once each.
            val body = mob.entity.position
            for (player in instance.players) {
                if (!canTarget(player) || player.uuid in mob.chargeHits) continue
                if (horizontalLength(player.position.sub(body)) <= CHARGE_REACH && kotlin.math.abs(player.position.y() - body.y()) < 2.5) {
                    mob.chargeHits += player.uuid
                    if (!evading(player)) mob.struckThisMove = true
                    if (typedDamagePlayer != null) typedDamagePlayer.invoke(player, frame.ability.damage, frame.ability.damageType)
                    else damagePlayer(player, frame.ability.damage)
                }
            }
        }
        val k = ((elapsed - travel.from).toDouble() / (travel.to - travel.from)).coerceIn(0.0, 1.0)
        val e = if (frame.ability.id == "shade-charge") k else k * k * (3 - 2 * k)
        mob.entity.teleport(Pos(from.x() + (to.x() - from.x()) * e, from.y() + (to.y() - from.y()) * e, from.z() + (to.z() - from.z()) * e,
            Pos.ZERO.withDirection(dir).yaw(), 0f))
    }

    /** The bites drive forward along the aim as it follows the player (it keeps turning), stopping 2.5 blocks short. */
    private fun driveForward(mob: Mob, frame: MobAbilityFrame, now: Long) {
        val elapsed = now - frame.startedAt
        if (elapsed !in BITE_DRIVE) return
        val here = mob.entity.position
        val dir = normalizeHorizontal(frame.facing)
        val room = (mob.target?.let { horizontalLength(it.position.sub(here)) } ?: 0.0) - 2.5
        val step = minOf(BITE_STEP, room, clearReach(here, dir, BITE_STEP))
        val yaw = Pos.ZERO.withDirection(dir).yaw()
        if (step <= .01) { mob.entity.setView(yaw, 0f); return }
        val to = safeRetreat(here.add(dir.mul(step)), mob.home, mob.definition.leashRange) ?: return
        mob.entity.teleport(to.withView(yaw, 0f))
    }

    /** How far the body can go from [from] along [dir] (up to [max]) before a solid block at body height (a pillar). */
    private fun clearReach(from: Pos, dir: Vec, max: Double): Double {
        var d = 0.0
        while (d + .5 <= max) {
            val p = from.add(dir.mul(d + .5))
            if (instance.getBlock(p.add(0.0, .6, 0.0)).solid() || instance.getBlock(p.add(0.0, 1.6, 0.0)).solid()) break
            d += .5
        }
        return d
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
                mob.travelTo = null
                mob.chargeHits.clear()
                mob.struckThisMove = false
                mob.counterShown = false
                if (event.frame.ability.id == TURN_SWEEP || event.frame.ability.id == SAKURA_TURN) mob.behindSince = 0L
                if (event.frame.ability.id == TAIL_SLAP) mob.tailslapAt = lastTickAt
                when (event.frame.ability.id) {
                    CHANNEL -> {
                        mob.castDamage = 0.0
                        mob.nextChannelAt = lastTickAt + CHANNEL_AGAIN_MILLIS
                        shaderFx.channel(mob.entity.position, gimmickTag(mob), (event.frame.ability.telegraphMillis / 50).toInt())
                    }
                    SHELL -> {
                        mob.shellHealth = mob.life.maximumHealth * SHELL_SHARE
                        mob.nextShellAt = lastTickAt + SHELL_AGAIN_MILLIS
                        shaderFx.shell(mob.entity.position, gimmickTag(mob), (event.frame.ability.telegraphMillis / 50).toInt())
                    }
                }
                updateName(mob, event.frame.ability.displayName)
                mob.view?.ability(event.frame.ability.id)
                if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) {
                    val t = event.frame.ability.telegraphMillis
                    if (event.frame.ability.id in SAKURA_COUNTERABLE && t >= 900) {
                        mob.counterFrom = event.frame.startedAt + t - COUNTER_LEAD_MILLIS
                        mob.counterTo = event.frame.startedAt + t - COUNTER_CLOSE_MILLIS
                    } else { mob.counterFrom = 0L; mob.counterTo = 0L }
                    sakuraFx?.start(event.frame.ability.id, event.frame.ability.telegraphMillis / 1000.0, event.frame.origin)
                    sakuraStrike?.windup(event.frame.ability.id, event.frame) { bone, px -> mob.view?.point(bone, px) }
                }
                if (!drawWarning(mob, event.frame, false)) {
                    cancelWarning(mob)
                    return false
                }
                playVfx(mob, ShadeWyrmVfx.windup(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) })
                if (mob.shade) shaderFx.windup(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) }
                if (event.frame.ability.id == ORB) mob.view?.point("skull", ShadeWyrmVfx.SNOUT)?.let {
                    shaderFx.orb(Pos(it), event.frame.origin, ORB_RELEASE_TICKS, (event.frame.ability.telegraphMillis / 50).toInt() - ORB_RELEASE_TICKS)
                }
                mob.entity.refreshActiveHand(false, true, false)
                castingPose(mob, true)
                if (mob.shade) windupCue(mob, event.frame)       // the wyrm's wind-up sounds and arm light
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
                if (mob.shade) shaderFx.strike(event.frame.ability.id, event.frame) { mob.view?.point("skull", it) }
                sound(event.frame.origin,
                    if (mob.entity.entityMeta is SpellcasterIllagerMeta) "minecraft:entity.evoker.cast_spell"
                    else if (event.frame.ability.shape is MobAttackShape.Sweep) "minecraft:entity.player.attack.sweep"
                    else "minecraft:block.anvil.land", 0.8f, if (mob.boss) 0.7f else 1.0f)
                for (player in players) {
                    if (player.instance !== instance || !canTarget(player)) continue
                    // Rings and the gimmicks reach round a pillar; the charge strikes along its path instead (see [travel]).
                    if (event.frame.ability.id != CHARGE && event.frame.ability.shape.contains(event.frame.origin, event.frame.facing, player.position) &&
                        (event.frame.ability.shape is MobAttackShape.Ring || event.frame.ability.id in GIMMICKS || mob.entity.hasLineOfSight(player))
                    ) {
                        if (!evading(player)) mob.struckThisMove = true
                        val dealt = event.frame.ability.damage * (if (frenzied(mob)) MobMod.FRENZY_DAMAGE else 1.0)
                        if (typedDamagePlayer != null) typedDamagePlayer.invoke(player, dealt, event.frame.ability.damageType)
                        else damagePlayer(player, dealt)
                        event.frame.ability.crowd?.let { crowdPlayer?.invoke(player, it, event.frame.ability.crowdMillis, event.frame.casterOrigin) }
                        if (MobMod.LEECH in mob.mods) mob.life.heal(mob.life.maximumHealth * MobMod.LEECH_RATIO)
                        if (mob.shade) gimmicks.playerHit(player, event.frame.ability.id, lastTickAt)
                    }
                    if (disposed || actionsStoppedForReturn) return false
                }
                if (mob.shade) { gimmicks.abilityHit(event.frame, lastTickAt); lightUp(event.frame) }
                if (event.frame.ability.id in GIMMICKS) { shaderFx.endTag(gimmickTag(mob)); mob.shellHealth = 0.0 }
                if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) {
                    sakuraBurst(event.frame)
                    sakuraStrike?.strike(event.frame.ability.id, event.frame) { bone, px -> mob.view?.point(bone, px) }
                }
            }
            is MobAbilityEvent.Finished -> {
                clearWarning(mob.entity.uuid); updateName(mob)
                if (event.abilityId in GIMMICKS) hideGimmickBars()
                if (mob.shade) {
                    val hugging = mob.target?.let { horizontalLength(it.position.sub(mob.entity.position)) < HUG_DISTANCE } == true
                    val whiffed = event.abilityId !in GIMMICKS && !mob.struckThisMove && players.any { p ->
                        canTarget(p) && p.instance === instance && horizontalLength(p.position.sub(mob.entity.position)) <= ENGAGED_DISTANCE }
                    // 連携技: some attacks lead straight into another (half the time) — never out of a whiff, which staggers it.
                    mob.combo = if (whiffed) null else when (event.abilityId) {
                        "shade-bite" -> if (mob.awakened && Random.nextBoolean()) "shade-tailspin" else null
                        "shade-horn" -> if (Random.nextBoolean()) "shade-pounce" else null
                        CHARGE -> if (Random.nextBoolean()) CHARGE else null   // turns at the wall and comes again
                        else -> null
                    }
                    if (mob.combo != null) mob.warningRetryAt = lastTickAt
                    else {
                        mob.openUntil = lastTickAt + openAfter(mob, event.abilityId, whiffed)
                        if (whiffed) stagger(mob)
                        // Someone glued to it: after the opening it sometimes answers, always through a wind-up.
                        if (hugging && event.abilityId !in setOf("shade-tailspin", "shade-pulse") && Random.nextDouble() < HUG_COUNTER_CHANCE)
                            mob.combo = if (mob.awakened && Random.nextBoolean()) "shade-tailspin" else "shade-pulse"
                        // It does not run from a player who stays on it.
                        else if (Random.nextDouble() < if (hugging) .25 else .5) {
                            mob.moveAfterAttack = true; mob.nextManeuverAt = minOf(mob.nextManeuverAt, lastTickAt)
                        }
                    }
                } else if (mob.definition.archetype == QuestMobArchetype.SAKURA_FANG) {
                    // 三連桜爪: the first slam always takes a second blow, the second a third once enraged.
                    mob.combo = when (event.abilityId) {
                        "sakura-claw" -> "sakura-claw2"
                        "sakura-claw2" -> if (mob.enraged) "sakura-claw3" else null
                        // once enraged it strings its moves: the somersault lands into the spin, the horn into the paw
                        "sakura-tail" -> if (mob.enraged) "sakura-spin" else null
                        "sakura-horn" -> if (mob.enraged) "sakura-claw" else null
                        "sakura-pounce" -> if (mob.enraged) "sakura-horn" else null
                        else -> null
                    }
                    if (mob.combo != null) mob.warningRetryAt = lastTickAt
                    // the chain is over: it moves round its prey for a while before the next big attack
                    else if (event.abilityId !in SAKURA_LIGHT) mob.restUntil = lastTickAt + if (mob.enraged) SAKURA_REST_ENRAGED.random(mob.moveRandom) else SAKURA_REST.random(mob.moveRandom)
                }
            }
        }
        return true
    }

    /** Set by the server once the owner's UI pack is loaded; name tags then use the pixel nameplate. */
    var pixelNameplates = false
        set(value) {
            field = value
            gimmicks.vanillaBar = !value
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
            now < mob.brokenUntil -> "  ［崩れ］"
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
            if (now < mob.brokenUntil) add(MobHudEffect(MobHudEffectKind.CC, "broken"))
            if (mob.enraged || frenzied(mob)) add(MobHudEffect(MobHudEffectKind.BUFF, "rage"))
            if (now < mob.exposedUntil) add(MobHudEffect(MobHudEffectKind.DEBUFF, "exposed"))
            if (now < mob.slowedUntil && mob.slowPercent > 0.0) add(MobHudEffect(MobHudEffectKind.DEBUFF, "slow"))
            if (now < mob.tauntedUntil) add(MobHudEffect(MobHudEffectKind.DEBUFF, "taunt"))
            mob.crowd.filterValues { it > now }.keys.sortedBy { it.ordinal }
                .forEach { add(MobHudEffect(MobHudEffectKind.CC, it.name.lowercase())) }
        }
        val breaks = mob.boss || mob.grade.breaks
        return MobHud(encounterLevel, mob.definition.name, if (mob.boss) BossBars.grade(mob.definition.archetype) else mob.grade,
            mob.mods, mob.life.health, mob.life.maximumHealth,
            breakGauge = if (breaks) mob.breakGauge / BREAK_MAX else null,
            broken = if (now < mob.brokenUntil) ((mob.brokenUntil - now).toDouble() / BREAK_MILLIS).coerceIn(0.0, 1.0) else null,
            effects = effects,
            notice = mob.notice ?: if (mob.life.phase == QuestMobPhase.RETURNING) "帰還中" else null,
            dedicated = if (mob.boss) BossBars.dedicated(mob.definition.archetype) else null)
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
        // B (2026-10-19): the small attacks show no floor; they are read from the wyrm's wind-up and its sound.
        if (mob.view != null && frame.ability.id in NO_FLOOR) return true
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

    /** 桜牙獣's strikes scatter petals over the struck shape (vanilla particles, no shader). */
    private fun sakuraBurst(frame: MobAbilityFrame) {
        val o = frame.origin.add(0.0, .3, 0.0)
        val f = normalizeHorizontal(frame.facing)
        val pink = Particle.DUST.withColor(net.kyori.adventure.text.format.TextColor.color(0xff8fc0)).withScale(1.6f)
        val points = when (val shape = frame.ability.shape) {
            is MobAttackShape.Ring -> (0 until 24).map { k ->
                val a = k * Math.PI / 12; val r = shape.radius * (if (k % 2 == 0) 1.0 else .55)
                o.add(kotlin.math.cos(a) * r, 0.0, kotlin.math.sin(a) * r) }
            is MobAttackShape.Slam -> (0..(shape.length / 1.5).toInt()).map { o.add(f.mul(it * 1.5)) }
            is MobAttackShape.Sweep -> (-4..4).flatMap { k -> listOf(.45, .9).map { d ->
                o.add(f.rotateAroundY(Math.toRadians(shape.halfAngleDegrees * k / 4.0)).mul(shape.radius * d)) } }
            else -> listOf(o)
        }
        for (p in points) { particle(p, Particle.CHERRY_LEAVES, 6); particle(p, pink, 2) }
    }

    /** The enrage roar of 桜牙獣 (2.5 s into the hold, when its petals burst open): a ring of petals across the arena. */
    private fun tickSakuraRoar(mob: Mob, now: Long) {
        if (mob.waveAt != -1L || now < mob.enrageHoldUntil - 2500) return
        mob.waveAt = now
        sakuraStrike?.roar(mob.entity.position, RAGE_WAVE_RADIUS)
        val o = mob.entity.position.add(0.0, 2.5, 0.0)
        particle(o, Particle.CHERRY_LEAVES, 60)
        for (k in 0 until 32) {
            val a = k * Math.PI / 16
            particle(o.add(kotlin.math.cos(a) * 6.0, -2.0, kotlin.math.sin(a) * 6.0), Particle.CHERRY_LEAVES, 4)
        }
    }

    /**
     * 桜牙獣's counter window: for the last moments before a big strike its horns flash blue. A hit from in front in that
     * window throws it out of the strike: stagger, the `countered` clip, +30% damage taken while it is down.
     */
    private fun tickCounterGlow(mob: Mob, now: Long) {
        if (mob.counterTo == 0L || now < mob.counterFrom) return
        if (now > mob.counterTo || !mob.abilities.isActive) {
            mob.counterFrom = 0L; mob.counterTo = 0L; updateName(mob); sakuraFx?.burstWreath(); return
        }
        if (now - mob.counterFrom < 60) {
            // the cue is the concept itself: a wreath of petals whirls round its head and the horn tips burst into bloom
            updateName(mob, "花輪が回った！ 正面から打て")
            sound(mob.entity.position, "minecraft:block.amethyst_block.resonate", 1.4f, 1.6f)
            sound(mob.entity.position, "minecraft:block.cherry_leaves.break", 1.4f, .8f)
            sakuraFx?.wreath((mob.counterTo - mob.counterFrom) / 1000.0)
            sakuraStrike?.counterBloom(((mob.counterTo - mob.counterFrom) / 50).toInt()) { bone, px -> mob.view?.point(bone, px) }
        }
        // without the pack: cherry leaves whirling at the horn tips
        for (tip in listOf(Vec(11.4, 27.0, -18.0), Vec(-11.4, 27.0, -18.0))) {
            val at = mob.view?.point("skull", Vec(tip.x(), 40.5 + tip.y() * .94 - tip.z() * .34, 27.0 + tip.y() * .34 + tip.z() * .94)) ?: continue
            particle(Pos.fromPoint(at), Particle.CHERRY_LEAVES, 3)
        }
    }

    /**
     * 桜紋の陣 (enraged 桜牙獣, the party mechanic): it sits and howls while one sakura crest per player blooms on the
     * ground round the arena. When the howl ends, every crest must have someone on it: then the spell backfires and it
     * collapses. Each empty crest blasts the whole party, and whoever stands on none takes another blast.
     */
    private fun tickSigil(mob: Mob, now: Long): Boolean {
        if (!mob.enraged || now < mob.enrageHoldUntil) return false
        val players = instance.players.filter { canTarget(it) && it.position.distanceSquared(mob.home) <= mob.definition.leashRange * mob.definition.leashRange }
        if (mob.sigilEndsAt == 0L) {
            if (mob.sigilNextAt == 0L) mob.sigilNextAt = mob.enrageHoldUntil + SIGIL_FIRST_MILLIS
            if (now < mob.sigilNextAt || mob.abilities.isActive || mob.maneuver != null || players.isEmpty()) return false
            val count = players.size.coerceIn(1, 5)
            val turn = Random.nextDouble(0.0, 2 * Math.PI)
            mob.sigilCrests = (0 until count).map { k ->
                val a = turn + k * 2 * Math.PI / count
                val r = SIGIL_RING + (k % 2) * 1.5
                val at = mob.home.add(kotlin.math.cos(a) * r, 0.0, kotlin.math.sin(a) * r)
                Pos(at.x(), telegraphs.groundHeight(at) ?: mob.home.y(), at.z())
            }
            mob.sigilEndsAt = now + SIGIL_MILLIS
            mob.sigilShown = -1L
            mob.counterFrom = 0L; mob.counterTo = 0L
            stopNavigation(mob)
            mob.view?.maneuver("sigil")
            sakuraStrike?.sigil(mob.sigilCrests, SIGIL_RADIUS, (SIGIL_MILLIS / 50).toInt())
            sakuraFx?.sigil(mob.sigilCrests, SIGIL_MILLIS / 1000.0)
            sound(mob.entity.position, "minecraft:entity.wolf.howl", 2.0f, .6f)
            return true
        }
        if (now < mob.sigilEndsAt) {
            stopNavigation(mob)
            val left = (mob.sigilEndsAt - now + 999) / 1000
            if (left != mob.sigilShown) { mob.sigilShown = left; updateName(mob, "桜紋の陣・一人ひとつ紋に立て（$left）") }
            if ((now / 50) % 6 == 0L) {
                val pink = Particle.DUST.withColor(net.kyori.adventure.text.format.TextColor.color(0xff8fc0)).withScale(1.3f)
                for (c in mob.sigilCrests) for (k in 0 until 12) {
                    val a = k * Math.PI / 6
                    particle(c.add(kotlin.math.cos(a) * SIGIL_RADIUS, .15, kotlin.math.sin(a) * SIGIL_RADIUS), pink, 1)
                }
            }
            return true
        }
        val filled = mob.sigilCrests.map { c -> players.any { horizontalLength(it.position.sub(c)) <= SIGIL_RADIUS } }
        val scale = 1.65.pow(tier - 1) * damageMultiplier
        sakuraStrike?.sigilResolve(mob.sigilCrests, filled)
        sakuraFx?.sigilResolve(mob.sigilCrests)
        if (filled.all { it }) {
            mob.brokenUntil = now + SIGIL_BREAK_MILLIS
            staggerBoss(maxOf(bossStaggerUntil, now + SIGIL_BREAK_MILLIS))
            mob.view?.maneuver("countered")
            updateName(mob, "陣が成った！ 崩れた")
            sound(mob.entity.position, "minecraft:entity.ravager.stunned", 1.4f, .8f)
        } else {
            val empty = filled.count { !it }
            for (p in players) {
                val onCrest = mob.sigilCrests.any { horizontalLength(p.position.sub(it)) <= SIGIL_RADIUS }
                val amount = SIGIL_DAMAGE * scale * (empty + if (onCrest) 0 else 1)
                if (typedDamagePlayer != null) typedDamagePlayer.invoke(p, amount, dev.projects.server.coreloop.CoreDamageType.MAGICAL)
                else damagePlayer(p, amount)
            }
            updateName(mob, "陣が崩れた（空いた紋 $empty）")
            sound(mob.entity.position, "minecraft:entity.generic.explode", 1.2f, 1.2f)
        }
        mob.sigilEndsAt = 0L
        mob.sigilCrests = emptyList()
        mob.sigilNextAt = now + SIGIL_EVERY_MILLIS
        mob.warningRetryAt = now + 400L
        return false
    }

    /**
     * 千本桜 (桜牙獣 at 25%, once): it bounds to the middle of the arena, sits and howls while a sakura crest spreads over
     * the whole floor and the petals spiral in; when it blows, everyone it can see is struck — the pillars are the only
     * cover. Spent, it collapses.
     */
    private fun tickFinale(mob: Mob, now: Long): Boolean {
        if (mob.finaleStarted && mob.finaleEndsAt == 0L) return false
        val players = instance.players.filter { canTarget(it) && it.position.distanceSquared(mob.home) <= mob.definition.leashRange * mob.definition.leashRange }
        if (!mob.finaleStarted) {
            if (mob.life.health > mob.life.maximumHealth * FINALE_RATIO || mob.sigilEndsAt != 0L || now < mob.enrageHoldUntil) return false
            mob.finaleStarted = true
            mob.finaleEndsAt = now + FINALE_MILLIS
            mob.finaleShown = -1L
            mob.abilities.cancel(); clearWarning(mob.entity.uuid); castingPose(mob, false); mob.maneuver = null; stopNavigation(mob)
            mob.counterFrom = 0L; mob.counterTo = 0L
            val centre = mob.home
            val facing = players.minByOrNull { it.position.distanceSquared(centre) }?.let { yawToward(centre, it.position) } ?: centre.yaw()
            mob.entity.teleport(Pos(centre.x(), centre.y(), centre.z(), facing, 0f))
            mob.view?.maneuver("finale")
            sakuraStrike?.finale(centre, (FINALE_MILLIS / 50).toInt())
            sakuraFx?.finale(FINALE_MILLIS / 1000.0)
            sound(centre, "minecraft:entity.wolf.howl", 2.0f, .5f)
            return true
        }
        if (now < mob.finaleEndsAt) {
            stopNavigation(mob)
            val left = (mob.finaleEndsAt - now + 999) / 1000
            if (left != mob.finaleShown) {
                mob.finaleShown = left
                updateName(mob, "千本桜・柱の陰へ（$left）")
                if (left <= 3) sound(mob.entity.position, "minecraft:block.note_block.bell", 1.6f, .5f + .2f * (4 - left))
            }
            return true
        }
        mob.finaleEndsAt = 0L
        val scale = 1.65.pow(tier - 1) * damageMultiplier
        for (p in players) {
            if (!mob.entity.hasLineOfSight(p)) continue                // behind a pillar: safe
            val amount = FINALE_DAMAGE * scale
            if (typedDamagePlayer != null) typedDamagePlayer.invoke(p, amount, dev.projects.server.coreloop.CoreDamageType.MAGICAL)
            else damagePlayer(p, amount)
        }
        sakuraStrike?.finaleBlow(mob.entity.position, RAGE_WAVE_RADIUS)
        sakuraFx?.finaleBlow()
        sound(mob.entity.position, "minecraft:entity.generic.explode", 2.0f, .6f)
        mob.brokenUntil = now + FINALE_SPENT_MILLIS
        staggerBoss(maxOf(bossStaggerUntil, now + FINALE_SPENT_MILLIS))
        mob.view?.maneuver("countered")
        updateName(mob, "千本桜が散った…（崩れた）")
        mob.warningRetryAt = now + FINALE_SPENT_MILLIS
        mob.sigilNextAt = maxOf(mob.sigilNextAt, now + FINALE_SPENT_MILLIS + 8000L)
        return false
    }

    /** 千本桜's state, for tests: started, running. */
    internal fun finaleState(targetId: UUID): Pair<Boolean, Boolean>? = mobs[targetId]?.let { it.finaleStarted to (it.finaleEndsAt != 0L) }

    /** 桜紋の陣's crests, for tests. */
    internal fun sigilCrests(targetId: UUID): List<Pos> = mobs[targetId]?.sigilCrests ?: emptyList()

    /** Where a hit on 桜牙獣 landed: the counter, and the parts it wears down (horns from in front, tail shells from behind). */
    private fun sakuraHit(mob: Mob, attacker: Player, applied: Double, now: Long) {
        val here = mob.entity.position
        val yaw = Math.toRadians(here.yaw().toDouble())
        val ahead = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
        val off = Vec(attacker.position.x() - here.x(), 0.0, attacker.position.z() - here.z())
        if (off.length() < 1e-6) return
        val angle = Math.toDegrees(kotlin.math.acos(off.normalize().dot(ahead).coerceIn(-1.0, 1.0)))
        if (mob.counterTo != 0L && now in mob.counterFrom..mob.counterTo && angle <= 80.0) {
            mob.counterFrom = 0L; mob.counterTo = 0L
            mob.abilities.cancel(); clearWarning(mob.entity.uuid); castingPose(mob, false); mob.maneuver = null; stopNavigation(mob)
            staggerBoss(maxOf(bossStaggerUntil, now + COUNTER_STAGGER_MILLIS))
            mob.brokenUntil = now + COUNTER_STAGGER_MILLIS
            mob.view?.maneuver("countered")
            sakuraFx?.burstWreath()
            sakuraFx?.countered()
            updateName(mob, "カウンター！ 崩れた")
            sound(here, "minecraft:entity.ravager.stunned", 1.4f, .8f)
            sound(here, "minecraft:block.amethyst_block.break", 1.2f, .7f)
            return
        }
        val max = mob.life.maximumHealth
        val head = here.add(ahead.mul(3.0))
        if (!mob.hornsBroken && angle <= 60.0 && attacker.position.distance(head.withY(attacker.position.y())) <= HEAD_REACH) {
            mob.hornDamage += applied
            if (mob.hornDamage >= max * HORN_BREAK) {
                mob.hornsBroken = true
                mob.view?.hideBones(listOf("horn_l_tip", "horn_r_tip"))
                mob.abilities.disable("sakura-horn")
                sakuraFx?.partBurst(true)
                partBroken(mob, now, 3000L, "枝角が折れた")
            }
        }
        if (!mob.tailTorn && angle >= 110.0) {
            mob.tailDamage += applied
            if (mob.tailDamage >= max * FAN_TEAR) {
                mob.tailTorn = true
                mob.view?.hideBones(listOf("shell_t4_l", "shell_t4_r", "shell_t5_l", "shell_t5_r"))
                mob.abilities.disable("sakura-scatter")
                sakuraFx?.partBurst(false)
                partBroken(mob, now, 2500L, "尾の花びらが散った")
            }
        }
    }

    private val Mob.shade: Boolean get() = definition.archetype == QuestMobArchetype.SHADE_WYRM

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
        /** Where 桜牙獣's light sits: the chest, under the back's petal shells (body bone, Blockbench px). */
        private val SAKURA_LIGHT_AT = net.minestom.server.coordinate.Vec(0.0, 18.0, 10.0)
        /** Gimmicks: horn break (12 % of max health from near the head), fan tear (10 % per side). */
        const val HEAD_REACH = 6.0
        const val HEAD_ANGLE = 50.0
        const val TAIL_ANGLE = 140.0
        const val HEAD_MULTIPLIER = 1.25
        const val TAIL_MULTIPLIER = .8
        /** Share of max HP a crowd-control hit adds to a channel / takes off a shell (hard / soft CC). */
        const val CC_CAST_HARD = .02
        const val CC_CAST_SOFT = .008
        const val HORN_BREAK = .12
        const val FAN_TEAR = .10
        const val SLOPPY_MILLIS = 20_000L
        const val TORN_SWEEP_RADIUS = 4.5
        /** Search: the creeping walk's share of the walking speed (its clip is timed to it), and when it starts. */
        const val SEARCH_SPEED = .3
        const val SEARCH_BEYOND = 14.0
        const val TURN_SWEEP = "shade-turnsweep"
        /** 桜牙獣 answers someone at its back with the 360 tail spin. */
        const val SAKURA_TURN = "sakura-spin"
        /** 桜牙獣's breather between attack chains (ms), and the pause between its moves. */
        val SAKURA_REST = 2200L..3800L
        val SAKURA_REST_ENRAGED = 1400L..2600L
        const val SAKURA_MANEUVER_GAP = 400L        // longer than MobAbilityManager's global cooldown, so a rested attack starts first
        /** Its small blows on the move, the only attacks it throws while it rests. */
        val SAKURA_LIGHT = setOf("sakura-lunge", "sakura-barge", "sakura-hopswipe")
        val LUNGE_CARRY = 200L..500L
        val BARGE_CARRY = 300L..650L
        val HOP_CARRY = 200L..550L
        const val STALK_SPEED = .55
        /** Its standing moves, the lengths of their clips (assets/bosses/sakura_fang.bbmodel). */
        internal val SAKURA_STAND_MILLIS = mapOf(ManeuverKind.GROWL to 1600L, ManeuverKind.SHAKE to 1300L, ManeuverKind.SCRAPE to 1200L)
        internal val SAKURA_MANEUVERS = listOf(ManeuverKind.STRAFE to 4, ManeuverKind.DASH to 2, ManeuverKind.GROWL to 1,
            ManeuverKind.SHAKE to 1, ManeuverKind.SCRAPE to 1)
        /** 桜牙獣's big strikes that open a counter window [COUNTER_LEAD_MILLIS]..[COUNTER_CLOSE_MILLIS] before they land. */
        val SAKURA_COUNTERABLE = setOf("sakura-claw", "sakura-tail", "sakura-bloom", "sakura-charge", "sakura-howl", "sakura-rain", "sakura-pounce", "sakura-claw3")
        const val COUNTER_LEAD_MILLIS = 600L
        const val COUNTER_CLOSE_MILLIS = 150L
        const val COUNTER_STAGGER_MILLIS = 3500L
        /** 桜紋の陣: first one this long after the enrage, then every [SIGIL_EVERY_MILLIS]; crests this far out. */
        const val SIGIL_FIRST_MILLIS = 10_000L
        const val SIGIL_EVERY_MILLIS = 40_000L
        const val SIGIL_MILLIS = 6000L
        const val SIGIL_RING = 7.0
        const val SIGIL_RADIUS = 1.8
        const val SIGIL_DAMAGE = 18.0
        const val SIGIL_BREAK_MILLIS = 4500L
        /** 千本桜: at this share of health, an 8 s howl, then a blow on everyone in its sight; spent for 5 s. */
        const val FINALE_RATIO = .25
        const val FINALE_MILLIS = 8000L
        const val FINALE_DAMAGE = 70.0
        const val FINALE_SPENT_MILLIS = 5000L
        /** Got behind it (usually while an attack holds its facing) for this long when it is free to act. */
        const val TURN_AFTER_MILLIS = 600L
        /** Behind it this long: the quick tail slap (once per [TAIL_SLAP_AGAIN_MILLIS]); still there: the turning sweep. */
        const val TAIL_SLAP = "shade-tailslap"
        /** A (2026-10-19): attacks that carry the body, see [travel]. */
        private val TRAVEL = mapOf(
            "shade-pounce" to Travel(1120, 1400) { d -> (d - 2.0).coerceIn(2.0, 8.0) },
            "shade-bite" to Travel(450, 1700) { d -> (d - 2.5).coerceIn(0.0, 3.6) },
            "shade-horn" to Travel(850, 1050) { d -> (d - 1.5).coerceIn(0.0, 4.0) },
            "shade-charge" to Travel(800, 2000) { _ -> 14.0 },
        )
        /** B (2026-10-19): read from the wind-up, no floor warning. */
        val NO_FLOOR = setOf("shade-sweep", "shade-turnsweep", "shade-tailslap", "shade-bite", "shade-horn", "shade-tailspin",
            "shade-pounce", "shade-pulse")
        /** C (2026-10-19): flies at a player who got away, at most this often. */
        const val ADVANCE_AGAIN_MILLIS = 8000L
        const val CHARGE = "shade-charge"
        /** ③ (2026-10-19): the body turns at most 90°/s while it aims (circle a slow-turning wyrm). */
        val TURN_PER_TICK = Math.toRadians(4.5)
        const val HUG_DISTANCE = 3.5
        /** After the opening, how often it answers someone glued to it (with a wind-up: tail spin or pulse). */
        const val HUG_COUNTER_CHANCE = .3
        /** Extra standing-open time after each move (ms), on top of its recovery; heavy moves the longest. */
        val OPENING = mapOf(
            "shade-sweep" to 0L, "shade-tailslap" to 0L, "shade-pulse" to 0L, "shade-turnsweep" to 200L,
            "shade-bite" to 400L, "shade-horn" to 300L, "shade-tailspin" to 200L,
            "shade-pounce" to 700L, "shade-dust" to 900L, "shade-breath" to 1200L, "shade-storm" to 1000L,
            "shade-orb" to 500L, "shade-charge" to 600L, "shade-dive" to 900L)
        const val AWAKENED_OPENING = .85
        const val ENRAGED_OPENING = .6
        /** A move that struck nobody while someone was this close whiffed. */
        const val ENGAGED_DISTANCE = 10.0
        const val WHIFF_MILLIS = 800L
        const val BIG_WHIFF_MILLIS = 1500L
        val BIG_WHIFF = setOf("shade-pounce", "shade-charge", "shade-dive", "shade-horn")
        val COUNTERABLE = setOf("shade-pounce", "shade-horn", "shade-charge", "shade-breath")
        const val COUNTER_WINDOW_MILLIS = 550L
        const val COUNTER_DOWN_MILLIS = 2500L
        const val BREAK_REFILL_AFTER_MILLIS = 6000L
        const val BREAK_REFILL_PER_SECOND = 5.0
        val BITE_DRIVE = 450L..1700L
        const val BITE_STEP = .15                        // per tick: ~3.7 blocks over the three bites
        val WINDUP_SOUNDS = mapOf(
            "shade-sweep" to ("minecraft:entity.phantom.flap" to .6f), "shade-turnsweep" to ("minecraft:entity.phantom.flap" to .5f),
            "shade-bite" to ("minecraft:entity.ravager.attack" to 1.3f), "shade-horn" to ("minecraft:entity.ravager.step" to .5f),
            "shade-tailspin" to ("minecraft:entity.breeze.whirl" to .7f), TAIL_SLAP to ("minecraft:item.armor.equip_leather" to .6f),
            "shade-pounce" to ("minecraft:entity.ender_dragon.flap" to 1.2f), "shade-pulse" to ("minecraft:entity.warden.sonic_charge" to 1.4f),
            "shade-dust" to ("minecraft:block.sculk_catalyst.bloom" to .6f), "shade-breath" to ("minecraft:entity.ender_dragon.growl" to 1.3f),
            "shade-storm" to ("minecraft:entity.warden.roar" to 1.2f), "shade-orb" to ("minecraft:entity.ravager.roar" to 1.6f),
            CHARGE to ("minecraft:entity.ravager.roar" to .8f), "shade-dive" to ("minecraft:entity.ender_dragon.flap" to .7f),
        )
        val AIRBORNE = setOf("shade-dive", "shade-pounce", CHARGE, "shade-channel")
        const val CHARGE_REACH = 2.2
        const val COMBO_WAIT_MILLIS = 1500L
        const val LOST_SIGHT_MILLIS = 2000L
        /** MMO gimmicks (see [knockDown]). */
        const val CHANNEL = "shade-channel"
        const val SHELL = "shade-shell"
        val GIMMICKS = setOf(CHANNEL, SHELL)
        const val CAST_SHARE = .05
        const val SHELL_SHARE = .07
        const val DOWN_MILLIS = 4000L
        const val CHANNEL_FIRST_MILLIS = 12_000L
        const val CHANNEL_AGAIN_MILLIS = 40_000L
        const val SHELL_FIRST_MILLIS = 15_000L
        const val SHELL_AGAIN_MILLIS = 45_000L
        const val TAIL_SLAP_AFTER_MILLIS = 350L
        const val TAIL_SLAP_AGAIN_MILLIS = 6000L
        /** 飛び移り: first after this long, then this often (more often from the second form). */
        const val RELOCATE_FIRST_MILLIS = 15_000L
        const val RELOCATE_CALM_MILLIS = 26_000L
        const val RELOCATE_AGAIN_MILLIS = 18_000L
        /** 鱗粉弾: the clot leaves the jaws 1.2 s in (the clip) and lands at the telegraph's end. */
        const val ORB = "shade-orb"
        const val ORB_RELEASE_TICKS = 24
        /** ms into shade-dive while the body is carried over the target (the clip hangs in the air then). */
        val DIVE_CARRY = 1000L..2300L
        /** 桜牙獣's leap (sakura-pounce clip: airborne 0.6..1.1 s) and its charge (the gallop 0.55..1.35 s). */
        val POUNCE_CARRY = 600L..1100L
        val CHARGE_CARRY = 550L..1350L
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
