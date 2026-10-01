package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import java.util.UUID
import kotlin.math.abs

/** One garden, not a pending attack. Its visible tiles are also its server hit footprint. */
internal class CoreIceGarden(val cells: List<Pos>, val openedAt: Long, val duration: Int, val cellSize: Double = CELL) {
    private data class Visit(var hits: Int = 0, var nextHit: Long = 0)
    private val visits = mutableMapOf<UUID, Visit>()
    val slowSource: UUID = UUID.randomUUID()
    val endsAt get() = openedAt + duration
    fun cellAt(feet: Pos, supported: Collection<Pos> = cells) = supported.firstOrNull {
        abs(feet.x() - it.x()) <= cellSize / 2 && abs(feet.z() - it.z()) <= cellSize / 2 && abs(feet.y() - it.y()) <= .55
    }
    fun contains(feet: Pos) = cellAt(feet) != null
    fun active(now: Long) = now >= openedAt && now < endsAt
    fun firstHit(id: UUID) = visits[id]?.hits == 1
    fun hitCount(id: UUID) = visits[id]?.hits ?: 0
    fun canHit(id: UUID, now: Long): Boolean {
        if (!active(now)) return false
        val visit = visits[id] ?: return visits.size < TARGET_LIMIT
        return visit.hits < HITS && now >= visit.nextHit
    }
    fun claimHit(id: UUID, now: Long): Boolean {
        if (!canHit(id, now)) return false
        val visit = visits[id] ?: run {
            if (visits.size >= TARGET_LIMIT) return false
            Visit().also { visits[id] = it }
        }
        visit.hits++; visit.nextHit = now + HIT_INTERVAL
        return true
    }
    companion object {
        const val CELL = 1.25
        const val HITS = 4
        const val HIT_INTERVAL = 20
        const val TARGET_LIMIT = 128
        const val CHECK_INTERVAL = 4
        const val SLOW_MILLIS = 350L
        fun cellSize(radius: Double) = CELL * radius / 3.65
        // A stepped octagon with an open centre: 21 supported tiles, no physics wall.
        val offsets = (-2..2).flatMap { x -> (-2..2).filter { z -> x*x+z*z <= 5 }.map { z -> x to z } }
    }
}
