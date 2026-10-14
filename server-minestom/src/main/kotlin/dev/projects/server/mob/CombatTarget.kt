package dev.projects.server.mob

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.util.UUID

data class CombatTarget(
    val id: UUID,
    val position: Point,
    val halfExtent: Vec = Vec.ZERO,
    val sphereRadius: Double = 0.0,
)
