package dev.projects.server

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class RiftExecutionerTarget(val id: UUID, val position: Point)

data class RiftArena(
    val minX: Double = -16.0,
    val maxX: Double = 16.0,
    val minZ: Double = -16.0,
    val maxZ: Double = 16.0,
) {
    init {
        require(minX <= maxX && minZ <= maxZ) { "Arena bounds must be ordered" }
    }

    fun clamp(point: Point): Point = Vec(
        point.x().coerceIn(minX, maxX),
        point.y(),
        point.z().coerceIn(minZ, maxZ),
    )
}

data class RiftZone(
    val id: Long,
    val origin: Point,
    val facing: Vec,
    val remainingTicks: Int,
    internal val damageTicks: Int,
    internal val damagePulse: Long = 0,
)

enum class RiftExecutionerAttack {
    SECTOR_CLEAVE,
    FORWARD_SLAM,
    CHAIN_DASH,
}

sealed interface RiftExecutionerEvent {
    data class SectorTelegraph(
        val executionId: Long,
        val attack: RiftExecutionerAttack,
        val origin: Point,
        val facing: Vec,
        val durationTicks: Int,
    ) : RiftExecutionerEvent

    data class SectorActive(
        val executionId: Long,
        val attack: RiftExecutionerAttack,
        val origin: Point,
        val facing: Vec,
    ) : RiftExecutionerEvent

    data class AttackHit(
        val executionId: Long,
        val targetId: UUID,
        val damage: Int,
    ) : RiftExecutionerEvent

    data class DashTelegraph(
        val executionId: Long,
        val origin: Point,
        val target: Point,
        val destination: Point,
    ) : RiftExecutionerEvent

    data class DashPosition(val position: Point, val facing: Vec) : RiftExecutionerEvent

    data class RiftCreated(val zone: RiftZone) : RiftExecutionerEvent
    data class RiftRemoved(val zoneId: Long) : RiftExecutionerEvent
    data object BreakStarted : RiftExecutionerEvent
    data object BreakEnded : RiftExecutionerEvent
    data object FinalStruggleComplete : RiftExecutionerEvent
}

private enum class ControllerPhase {
    PAUSE,
    SECTOR_TELEGRAPH,
    SECTOR_ACTIVE,
    SECTOR_RECOVERY,
    DASH_TELEGRAPH,
    DASHING,
    DASH_RECOVERY,
    BREAK,
    FINAL_STRUGGLE,
    COMPLETE,
    DEFEAT,
}

/** The one-off state machine for Rift Executioner. Geometry and damage timing stay server-owned. */
class RiftExecutionerController(
    private val arena: RiftArena = RiftArena(),
    private val executionIdSource: () -> Long = RiftExecutionerIds::next,
    private val initialPauseTicks: Int = 20,
) {
    var position: Point? = null
        private set

    var controllerPhase: String = "PAUSE"
        private set

    private var phase = ControllerPhase.PAUSE
    private var phaseTicks = initialPauseTicks
    private var nextAttack = 0
    private var executionId = 0L
    private var attackOrigin: Point = Vec(0.0, 0.0, 0.0)
    private var attackFacing = Vec(0.0, 0.0, 1.0)
    private var dashStart: Point = Vec(0.0, 0.0, 0.0)
    private var dashDestination: Point = Vec(0.0, 0.0, 0.0)
    private var dashTargetId: UUID? = null
    private var dashChain = 0
    private var dashChainActive = false
    private var dashHitTargets = mutableSetOf<UUID>()
    private var riftId = 0L
    private var finalTicks = 0
    private var finalDashStartTick = -1
    private var finalDashExecutionId = 0L
    private var finalDashStart: Point? = null
    private var finalDashDestination: Point? = null
    private var finalDashFacing = Vec(0.0, 0.0, 1.0)
    private val rifts = linkedMapOf<Long, RiftZone>()

    val activeRifts: List<RiftZone>
        get() = rifts.values.toList()

    val isBreak: Boolean
        get() = phase == ControllerPhase.BREAK

    fun tick(
        origin: Point,
        facing: Vec,
        targets: Collection<RiftExecutionerTarget>,
        bossPhase: PrototypeBossPhase,
        encounterState: PrototypeEncounterState = PrototypeEncounterState.ACTIVE,
    ): List<RiftExecutionerEvent> {
        if (position == null) position = arena.clamp(origin)
        val events = mutableListOf<RiftExecutionerEvent>()

        if (encounterState == PrototypeEncounterState.DEFEAT) {
            phase = ControllerPhase.DEFEAT
            controllerPhase = phase.name
            return events
        }
        if (encounterState == PrototypeEncounterState.VICTORY) {
            phase = ControllerPhase.COMPLETE
            controllerPhase = phase.name
            return events
        }
        tickRifts(targets, events)
        if (encounterState == PrototypeEncounterState.FINAL_STRUGGLE && phase != ControllerPhase.FINAL_STRUGGLE) {
            if (phase == ControllerPhase.COMPLETE) return events
            startFinalStruggle()
        }
        if (phase == ControllerPhase.FINAL_STRUGGLE) {
            tickFinalStruggle(targets, bossPhase, events)
            controllerPhase = phase.name
            return events
        }
        if (phase == ControllerPhase.COMPLETE || phase == ControllerPhase.DEFEAT) return events
        if (phase == ControllerPhase.BREAK) {
            phaseTicks--
            if (phaseTicks <= 0) {
                phase = ControllerPhase.PAUSE
                phaseTicks = 12
                dashChain = 0
                dashChainActive = false
                nextAttack = 0
                events += RiftExecutionerEvent.BreakEnded
            }
            controllerPhase = phase.name
            return events
        }

        when (phase) {
            ControllerPhase.PAUSE -> {
                phaseTicks--
                if (phaseTicks <= 0) startNextAttack(origin, facing, bossPhase, targets, events)
            }
            ControllerPhase.SECTOR_TELEGRAPH -> {
                phaseTicks--
                if (phaseTicks <= 0) {
                    phase = ControllerPhase.SECTOR_ACTIVE
                    phaseTicks = 2
                    events += RiftExecutionerEvent.SectorActive(
                        executionId,
                        if (nextAttack == 1) RiftExecutionerAttack.FORWARD_SLAM else RiftExecutionerAttack.SECTOR_CLEAVE,
                        attackOrigin,
                        attackFacing,
                    )
                }
            }
            ControllerPhase.SECTOR_ACTIVE -> {
                hitSectorTargets(targets, events)
                phaseTicks--
                if (phaseTicks <= 0) {
                    if (bossPhase != PrototypeBossPhase.DUEL && nextAttack == 0) createRift(bossPhase, events)
                    phase = ControllerPhase.SECTOR_RECOVERY
                    phaseTicks = if (nextAttack == 1) 22 else 18
                }
            }
            ControllerPhase.SECTOR_RECOVERY -> finishRecovery()
            ControllerPhase.DASH_TELEGRAPH -> {
                phaseTicks--
                if (phaseTicks <= 0) {
                    phase = ControllerPhase.DASHING
                    phaseTicks = DASH_TICKS
                }
            }
            ControllerPhase.DASHING -> tickDash(targets, events, bossPhase)
            ControllerPhase.DASH_RECOVERY -> finishRecovery()
            else -> Unit
        }
        controllerPhase = phase.name
        return events
    }

    fun startFinalStruggle() {
        phase = ControllerPhase.FINAL_STRUGGLE
        finalTicks = 0
        phaseTicks = 0
        finalDashStartTick = -1
        finalDashStart = null
        finalDashDestination = null
        dashHitTargets.clear()
        controllerPhase = phase.name
    }

    fun reset() {
        phase = ControllerPhase.PAUSE
        phaseTicks = initialPauseTicks
        nextAttack = 0
        executionId = 0L
        position = null
        dashTargetId = null
        dashChain = 0
        dashChainActive = false
        dashHitTargets.clear()
        finalTicks = 0
        finalDashStartTick = -1
        finalDashStart = null
        finalDashDestination = null
        rifts.clear()
        controllerPhase = phase.name
    }

    fun removeAllRifts(): List<Long> {
        val removed = rifts.keys.toList()
        rifts.clear()
        return removed
    }

    private fun startNextAttack(
        origin: Point,
        facing: Vec,
        bossPhase: PrototypeBossPhase,
        targets: Collection<RiftExecutionerTarget>,
        events: MutableList<RiftExecutionerEvent>,
    ) {
        attackOrigin = position ?: arena.clamp(origin)
        attackFacing = normalizeHorizontal(facing)
        executionId = executionIdSource()
        dashHitTargets.clear()
        if (nextAttack == 2) {
            if (!dashChainActive) dashChain = 0
            val target = targets.minByOrNull { distanceSquared(attackOrigin, it.position) }
            if (target == null) {
                nextAttack = 0
                phaseTicks = 8
                return
            }
            val direction = normalizeHorizontal(Vec(
                target.position.x() - attackOrigin.x(),
                0.0,
                target.position.z() - attackOrigin.z(),
            ))
            val destination = arena.clamp(target.position.add(direction.x() * DASH_OVERSHOOT, 0.0, direction.z() * DASH_OVERSHOOT))
            dashStart = attackOrigin
            dashDestination = destination
            dashTargetId = target.id
            dashHitTargets.clear()
            phase = ControllerPhase.DASH_TELEGRAPH
            phaseTicks = DASH_TELEGRAPH_TICKS
            events += RiftExecutionerEvent.DashTelegraph(executionId, attackOrigin, target.position, destination)
        } else {
            phase = ControllerPhase.SECTOR_TELEGRAPH
            phaseTicks = if (nextAttack == 1) SLAM_TELEGRAPH_TICKS else SECTOR_TELEGRAPH_TICKS
            events += RiftExecutionerEvent.SectorTelegraph(
                executionId,
                if (nextAttack == 1) RiftExecutionerAttack.FORWARD_SLAM else RiftExecutionerAttack.SECTOR_CLEAVE,
                attackOrigin,
                attackFacing,
                phaseTicks,
            )
        }
    }

    private fun finishRecovery() {
        phaseTicks--
        if (phaseTicks <= 0) {
            phase = ControllerPhase.PAUSE
            phaseTicks = 12
            nextAttack = (nextAttack + 1) % 3
        }
    }

    private fun hitSectorTargets(targets: Collection<RiftExecutionerTarget>, events: MutableList<RiftExecutionerEvent>) {
        targets.filter {
            if (nextAttack == 1) {
                FixedAttackTester.isInAttackRegion(
                    FixedAttackType.FORWARD_SLAM,
                    attackOrigin,
                    attackFacing,
                    it.position,
                )
            } else {
                isInsideSector(attackOrigin, attackFacing, SECTOR_RADIUS, SECTOR_ANGLE, it.position)
            }
        }
            .filter { dashHitTargets.add(it.id) }
            .forEach { events += RiftExecutionerEvent.AttackHit(executionId, it.id, if (nextAttack == 1) SLAM_DAMAGE else SECTOR_DAMAGE) }
    }

    private fun tickDash(
        targets: Collection<RiftExecutionerTarget>,
        events: MutableList<RiftExecutionerEvent>,
        bossPhase: PrototypeBossPhase,
    ) {
        val progress = (DASH_TICKS - phaseTicks + 1).toDouble() / DASH_TICKS
        val previous = position ?: dashStart
        val current = interpolate(dashStart, dashDestination, progress.coerceIn(0.0, 1.0))
        position = current
        val direction = normalizeHorizontal(Vec(
            dashDestination.x() - previous.x(),
            0.0,
            dashDestination.z() - previous.z(),
        ))
        events += RiftExecutionerEvent.DashPosition(current, direction)
        targets.forEach { target ->
            if (target.id !in dashHitTargets && distanceToSegment(target.position, previous, current) <= DASH_HIT_RADIUS &&
                abs(target.position.y() - current.y()) <= DASH_VERTICAL_TOLERANCE
            ) {
                dashHitTargets += target.id
                events += RiftExecutionerEvent.AttackHit(executionId, target.id, DASH_DAMAGE)
            }
        }
        phaseTicks--
        if (phaseTicks > 0) return
        if (dashHitTargets.isEmpty()) {
            phase = ControllerPhase.BREAK
            phaseTicks = BREAK_TICKS
            dashChain = 0
            dashChainActive = false
            events += RiftExecutionerEvent.BreakStarted
            return
        }
        dashChain++
        val maxChain = if (bossPhase == PrototypeBossPhase.EXECUTION) 4 else 3
        if (dashChain >= maxChain) {
            phase = ControllerPhase.DASH_RECOVERY
            phaseTicks = 14
            dashChainActive = false
        } else {
            phase = ControllerPhase.PAUSE
            phaseTicks = if (bossPhase == PrototypeBossPhase.EXECUTION) 10 else 12
            nextAttack = 2
            dashChainActive = true
        }
    }

    private fun tickFinalStruggle(
        targets: Collection<RiftExecutionerTarget>,
        bossPhase: PrototypeBossPhase,
        events: MutableList<RiftExecutionerEvent>,
    ) {
        finalTicks++
        // The final sequence reuses the encounter's readable attacks instead of adding a timeline framework.
        if (finalTicks == 1 || finalTicks == 95) {
            attackOrigin = position ?: Vec(0.0, 0.0, 0.0)
            attackFacing = normalizeHorizontal(targets.firstOrNull()?.let {
                Vec(it.position.x() - attackOrigin.x(), 0.0, it.position.z() - attackOrigin.z())
            } ?: Vec(0.0, 0.0, 1.0))
            executionId = executionIdSource()
            nextAttack = 0
            dashHitTargets.clear()
            events += RiftExecutionerEvent.SectorTelegraph(
                executionId,
                RiftExecutionerAttack.SECTOR_CLEAVE,
                attackOrigin,
                attackFacing,
                SECTOR_TELEGRAPH_TICKS,
            )
        }
        if (finalTicks in 15..16 || finalTicks in 109..110) hitSectorTargets(targets, events)
        if (finalTicks == 45 || finalTicks == 145) {
            val target = targets.minByOrNull { distanceSquared(position ?: it.position, it.position) }
            if (target != null) {
                val origin = position ?: target.position
                val direction = normalizeHorizontal(Vec(target.position.x() - origin.x(), 0.0, target.position.z() - origin.z()))
                finalDashStartTick = finalTicks
                finalDashExecutionId = executionIdSource()
                finalDashStart = origin
                finalDashDestination = arena.clamp(target.position.add(direction.x() * DASH_OVERSHOOT, 0.0, direction.z() * DASH_OVERSHOOT))
                finalDashFacing = direction
                dashHitTargets.clear()
                events += RiftExecutionerEvent.DashTelegraph(
                    finalDashExecutionId,
                    origin,
                    target.position,
                    requireNotNull(finalDashDestination),
                )
            }
        }
        if (finalDashStartTick >= 0) {
            val elapsed = finalTicks - finalDashStartTick
            if (elapsed in DASH_TELEGRAPH_TICKS until DASH_TELEGRAPH_TICKS + DASH_TICKS) {
                val start = requireNotNull(finalDashStart)
                val destination = requireNotNull(finalDashDestination)
                val previous = position ?: start
                val progress = (elapsed - DASH_TELEGRAPH_TICKS + 1).toDouble() / DASH_TICKS
                val current = interpolate(start, destination, progress.coerceIn(0.0, 1.0))
                position = current
                events += RiftExecutionerEvent.DashPosition(current, finalDashFacing)
                targets.forEach { target ->
                    if (target.id !in dashHitTargets &&
                        distanceToSegment(target.position, previous, current) <= DASH_HIT_RADIUS &&
                        abs(target.position.y() - current.y()) <= DASH_VERTICAL_TOLERANCE
                    ) {
                        dashHitTargets += target.id
                        events += RiftExecutionerEvent.AttackHit(finalDashExecutionId, target.id, DASH_DAMAGE)
                    }
                }
            }
            if (elapsed >= DASH_TELEGRAPH_TICKS + DASH_TICKS - 1) {
                finalDashStartTick = -1
                finalDashStart = null
                finalDashDestination = null
            }
        }
        if (finalTicks == 140) createRift(PrototypeBossPhase.EXECUTION, events)
        if (finalTicks >= FINAL_STRUGGLE_TICKS) {
            phase = ControllerPhase.COMPLETE
            events += RiftExecutionerEvent.FinalStruggleComplete
        }
    }

    private fun createRift(bossPhase: PrototypeBossPhase, events: MutableList<RiftExecutionerEvent>) {
        val duration = if (bossPhase == PrototypeBossPhase.EXECUTION) RIFT_PHASE_3_TICKS else RIFT_PHASE_2_TICKS
        val maxRifts = if (bossPhase == PrototypeBossPhase.EXECUTION) MAX_RIFTS_PHASE_3 else MAX_RIFTS_PHASE_2
        while (rifts.size >= maxRifts) {
            val oldest = rifts.entries.first()
            rifts.remove(oldest.key)
            events += RiftExecutionerEvent.RiftRemoved(oldest.key)
        }
        val zone = RiftZone(++riftId, attackOrigin, attackFacing, duration, 0)
        rifts[zone.id] = zone
        events += RiftExecutionerEvent.RiftCreated(zone)
    }

    private fun tickRifts(targets: Collection<RiftExecutionerTarget>, events: MutableList<RiftExecutionerEvent>) {
        val updated = mutableListOf<RiftZone>()
        for (zone in rifts.values) {
            val remaining = zone.remainingTicks - 1
            if (remaining <= 0) {
                events += RiftExecutionerEvent.RiftRemoved(zone.id)
                continue
            }
            val damageTicks = zone.damageTicks + 1
            if (damageTicks >= RIFT_DAMAGE_PERIOD) {
                targets.filter { isInsideSector(zone.origin, zone.facing, SECTOR_RADIUS, SECTOR_ANGLE, it.position) }
                    .forEach {
                        events += RiftExecutionerEvent.AttackHit(
                            zone.id * 1_000_000L + zone.damagePulse + 1,
                            it.id,
                            RIFT_DAMAGE,
                        )
                    }
                updated += zone.copy(remainingTicks = remaining, damageTicks = 0, damagePulse = zone.damagePulse + 1)
            } else {
                updated += zone.copy(remainingTicks = remaining, damageTicks = damageTicks)
            }
        }
        rifts.clear()
        updated.forEach { rifts[it.id] = it }
    }

    companion object {
        const val SECTOR_RADIUS = 4.5
        const val SECTOR_ANGLE = 100.0
        const val SECTOR_TELEGRAPH_TICKS = 14
        const val SECTOR_ACTIVE_TICKS = 2
        const val SECTOR_RECOVERY_TICKS = 18
        const val SLAM_TELEGRAPH_TICKS = 18
        const val SLAM_DAMAGE = 8
        const val SECTOR_DAMAGE = 6
        const val DASH_TELEGRAPH_TICKS = 18
        const val DASH_TICKS = 5
        const val DASH_OVERSHOOT = 2.5
        const val DASH_HIT_RADIUS = 1.1
        const val DASH_VERTICAL_TOLERANCE = 2.0
        const val DASH_DAMAGE = 5
        const val BREAK_TICKS = 40
        const val RIFT_PHASE_2_TICKS = 120
        const val RIFT_PHASE_3_TICKS = 200
        const val RIFT_DAMAGE = 2
        const val RIFT_DAMAGE_PERIOD = 20
        const val MAX_RIFTS_PHASE_2 = 3
        const val MAX_RIFTS_PHASE_3 = 4
        const val FINAL_STRUGGLE_TICKS = 200

        fun isInsideSector(origin: Point, facing: Vec, radius: Double, angleDegrees: Double, target: Point, verticalTolerance: Double = 2.0): Boolean {
            if (radius < 0.0 || angleDegrees !in 0.0..360.0 || !radius.isFinite() || !angleDegrees.isFinite()) return false
            if (abs(target.y() - origin.y()) > verticalTolerance) return false
            val offsetX = target.x() - origin.x()
            val offsetZ = target.z() - origin.z()
            val distance = sqrt(offsetX * offsetX + offsetZ * offsetZ)
            if (distance > radius) return false
            if (distance <= 1.0e-9) return true
            val forward = normalizeHorizontal(facing)
            val dot = (offsetX * forward.x() + offsetZ * forward.z()) / distance
            return dot >= cos(Math.toRadians(angleDegrees / 2.0))
        }

        fun overshootDestination(target: Point, origin: Point, overshoot: Double = DASH_OVERSHOOT): Point {
            val direction = normalizeHorizontal(Vec(target.x() - origin.x(), 0.0, target.z() - origin.z()))
            return target.add(direction.x() * overshoot, 0.0, direction.z() * overshoot)
        }

        fun distanceToSegment(point: Point, start: Point, end: Point): Double {
            val dx = end.x() - start.x()
            val dy = end.y() - start.y()
            val dz = end.z() - start.z()
            val lengthSquared = dx * dx + dy * dy + dz * dz
            if (lengthSquared <= 1.0e-12) return distance(point, start)
            val projection = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy + (point.z() - start.z()) * dz) / lengthSquared
            val t = projection.coerceIn(0.0, 1.0)
            return distance(point, Vec(start.x() + dx * t, start.y() + dy * t, start.z() + dz * t))
        }

        private fun distanceSquared(first: Point, second: Point): Double {
            val x = first.x() - second.x()
            val y = first.y() - second.y()
            val z = first.z() - second.z()
            return x * x + y * y + z * z
        }

        private fun distance(first: Point, second: Point): Double = sqrt(distanceSquared(first, second))

        private fun interpolate(start: Point, end: Point, progress: Double): Point = Vec(
            start.x() + (end.x() - start.x()) * progress,
            start.y() + (end.y() - start.y()) * progress,
            start.z() + (end.z() - start.z()) * progress,
        )

        private fun normalizeHorizontal(direction: Vec): Vec {
            val length = sqrt(direction.x() * direction.x() + direction.z() * direction.z())
            return if (length > 1.0e-9) Vec(direction.x() / length, 0.0, direction.z() / length) else Vec(0.0, 0.0, 1.0)
        }
    }
}

private object RiftExecutionerIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long = ++nextId
}
