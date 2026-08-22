package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.Entity

enum class ParticleAnchorPoint { FEET, CENTER, EYE }

data class ParticleAnchorSample(val position: Point, val direction: Vec, val valid: Boolean = true)

/** A VFX-only position provider. It never participates in gameplay authority. */
class ParticleAnchor private constructor(
    private val positionProvider: () -> Point?,
    private val directionProvider: () -> Vec,
    private val validityProvider: () -> Boolean,
    val cancelWhenInvalid: Boolean = true,
    val localOffset: Vec = Vec.ZERO,
    val relativeOffset: Vec = Vec.ZERO,
    private val coherentSampleProvider: (() -> ParticleAnchorSample)? = null,
) {
    fun sample(): ParticleAnchorSample {
        coherentSampleProvider?.let { provider ->
            val base = runCatching { provider() }.getOrNull()
                ?: return ParticleAnchorSample(Vec.ZERO, Vec(0.0, 0.0, 1.0), false)
            if (!base.valid || !finite(base.position)) return base.copy(valid = false)
            val transform = ParticleTransform.fromDirection(base.position, base.direction)
            val local = transform.localDirection(localOffset)
            return base.copy(position = base.position.add(
                local.x() + relativeOffset.x(), local.y() + relativeOffset.y(), local.z() + relativeOffset.z(),
            ))
        }
        val valid = runCatching { validityProvider() }.getOrDefault(false)
        val position = runCatching { positionProvider() }.getOrNull()
        if (!valid || position == null || !finite(position)) return ParticleAnchorSample(Vec.ZERO, Vec(0.0, 0.0, 1.0), false)
        val direction = runCatching { directionProvider() }.getOrDefault(Vec(0.0, 0.0, 1.0))
        val transform = ParticleTransform.fromDirection(position, direction)
        val local = transform.localDirection(localOffset)
        val worldOffset = Vec(
            local.x() + relativeOffset.x(),
            local.y() + relativeOffset.y(),
            local.z() + relativeOffset.z(),
        )
        return ParticleAnchorSample(position.add(worldOffset.x(), worldOffset.y(), worldOffset.z()), transform.forward, true)
    }

    fun position(): Point? = sample().takeIf { it.valid }?.position

    fun direction(): Vec = sample().direction

    fun withOffsets(local: Vec = localOffset, relative: Vec = relativeOffset): ParticleAnchor =
        ParticleAnchor(positionProvider, directionProvider, validityProvider, cancelWhenInvalid, local, relative, coherentSampleProvider)

    companion object {
        fun fixed(point: Point, direction: Vec = Vec(0.0, 0.0, 1.0)): ParticleAnchor =
            ParticleAnchor({ point }, { direction }, { true }, cancelWhenInvalid = false)

        fun follow(
            position: () -> Point?,
            direction: () -> Vec = { Vec(0.0, 0.0, 1.0) },
            cancelWhenInvalid: Boolean = true,
            validity: () -> Boolean = { position() != null },
        ): ParticleAnchor = ParticleAnchor(position, direction, validity, cancelWhenInvalid)

        fun player(
            player: Player,
            point: ParticleAnchorPoint = ParticleAnchorPoint.CENTER,
            cancelWhenInvalid: Boolean = true,
        ): ParticleAnchor = follow(
            position = {
                if (!player.isOnline) null else when (point) {
                    ParticleAnchorPoint.FEET -> player.position
                    ParticleAnchorPoint.CENTER -> player.position.add(0.0, 0.9, 0.0)
                    ParticleAnchorPoint.EYE -> player.position.add(0.0, 1.62, 0.0)
                }
            },
            direction = { player.position.direction() },
            cancelWhenInvalid = cancelWhenInvalid,
        )

        fun entity(
            entity: Entity,
            point: ParticleAnchorPoint = ParticleAnchorPoint.CENTER,
            cancelWhenInvalid: Boolean = true,
        ): ParticleAnchor = follow(
            position = {
                if (!entity.isActive || entity.isRemoved) null else when (point) {
                    ParticleAnchorPoint.FEET -> entity.position
                    ParticleAnchorPoint.CENTER -> entity.position.add(0.0, entity.getBoundingBox().height() / 2.0, 0.0)
                    ParticleAnchorPoint.EYE -> entity.position.add(0.0, entity.getBoundingBox().height() * 0.9, 0.0)
                }
            },
            direction = { entity.position.direction() },
            cancelWhenInvalid = cancelWhenInvalid,
        )

        fun sourceToTarget(
            source: ParticleAnchor,
            target: ParticleAnchor,
            cancelWhenInvalid: Boolean = true,
        ): ParticleAnchor = ParticleAnchor(
            positionProvider = { null },
            directionProvider = { Vec(0.0, 0.0, 1.0) },
            validityProvider = { true },
            cancelWhenInvalid = cancelWhenInvalid,
            coherentSampleProvider = {
                val from = source.sample()
                val to = target.sample()
                if (!from.valid || !to.valid) ParticleAnchorSample(Vec.ZERO, Vec(0.0, 0.0, 1.0), false)
                else ParticleAnchorSample(
                    from.position,
                    Vec(to.position.x() - from.position.x(), to.position.y() - from.position.y(), to.position.z() - from.position.z()),
                )
            },
        )
    }
}

private fun finite(point: Point): Boolean = point.x().isFinite() && point.y().isFinite() && point.z().isFinite()
