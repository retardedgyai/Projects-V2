package dev.projects.server

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Open
import dev.projects.protocol.VfxEditor2PreviewStart
import dev.projects.protocol.VfxEditor2PreviewStop
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2Status
import dev.projects.protocol.VfxEditor2StatusKind
import dev.projects.protocol.defaultVfxEditor2Composition
import dev.projects.server.particle.EmitterRate
import dev.projects.server.particle.ParticleAnchor
import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleBatch
import dev.projects.server.particle.ParticleCircle
import dev.projects.server.particle.ParticleDelay
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleEffectHandle
import dev.projects.server.particle.ParticleEffectState
import dev.projects.server.particle.ParticleEmitter
import dev.projects.server.particle.ParticleLine
import dev.projects.server.particle.ParticleManager
import dev.projects.server.particle.ParticleRibbon
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.ParticleStyleCurve
import dev.projects.server.particle.ParticleTransform
import dev.projects.server.particle.ParticleViewer
import dev.projects.server.particle.PlayerParticleSink
import dev.projects.server.particle.SpawnShape
import dev.projects.server.particle.constantCurve
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val VFX_EDITOR_2_PREVIEW_DURATION_TICKS = 12
private const val VFX_EDITOR_2_PREVIEW_DISTANCE = 3.0
private const val VFX_EDITOR_2_PREVIEW_HEIGHT = 1.1

/** Compiles the small, typed workbench model into the existing particle primitives. */
object VfxWorkbenchCompiler {
    fun compile(composition: VfxEditor2Composition, origin: Point, direction: Vec): ParticleEffect {
        val visibleEffects = composition.visibleEffects()
        if (visibleEffects.isEmpty()) return ParticleDelay(VFX_EDITOR_2_PREVIEW_DURATION_TICKS)
        return ParticleBatch.of(*visibleEffects.map { effect -> compileEffect(effect, origin, direction) }.toTypedArray())
    }

    private fun compileEffect(
        effect: dev.projects.protocol.VfxEditor2Effect,
        origin: Point,
        direction: Vec,
    ): ParticleEffect {
        val transform = effectFrame(origin, direction, effect.transform)
        val appearance = effect.appearance
        val particle = dust(appearance.color, appearance.particleSize.toFloat())
        return when (val shape = effect.shape) {
            is VfxEditor2Shape.ArcSlash -> compileArcSlash(shape, transform, appearance, particle)
            is VfxEditor2Shape.StraightSlash -> compileStraightSlash(shape, transform, appearance, particle)
            is VfxEditor2Shape.Ring -> compileRing(shape, transform, appearance, particle)
            is VfxEditor2Shape.Burst -> compileBurst(shape, transform, particle)
        }
    }

    private fun compileArcSlash(
        shape: VfxEditor2Shape.ArcSlash,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val arcBulge = shape.length * sin(Math.toRadians(shape.arcDegrees / 2.0)).coerceAtLeast(0.05) * 0.22
        val curvatureBulge = shape.curvature * 0.42
        val path: (Double) -> Point = { progress ->
            val lateral = (progress - 0.5) * shape.length
            val depth = sin(progress * PI) * (arcBulge + curvatureBulge)
            localPoint(transform, lateral, 0.0, depth)
        }
        val sampleCount = (shape.length * appearance.density * 2.4).roundToInt().coerceIn(8, 96)
        val lanes = if (shape.thickness <= 0.04) 1 else 3
        return ParticleRibbon(
            path = path,
            particle = particle,
            sampleCount = sampleCount,
            lanes = lanes,
            width = constantCurve(shape.thickness),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { _, _ -> ParticleStyle(particle) },
        )
    }

    private fun compileStraightSlash(
        shape: VfxEditor2Shape.StraightSlash,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val offsets = if (shape.thickness <= 0.04) listOf(0.0) else listOf(-0.5, 0.0, 0.5)
        return ParticleBatch.of(*offsets.map { offset ->
            val verticalOffset = offset * shape.thickness
            ParticleLine(
                start = localPoint(transform, -shape.length / 2.0, verticalOffset, 0.0),
                end = localPoint(transform, shape.length / 2.0, verticalOffset, 0.0),
                countPerMeter = (appearance.density * 7.0).coerceAtLeast(1.0),
                durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
                style = ParticleStyle(particle),
            )
        }.toTypedArray())
    }

    private fun compileRing(
        shape: VfxEditor2Shape.Ring,
        transform: ParticleTransform,
        appearance: dev.projects.protocol.VfxEditor2Appearance,
        particle: Particle,
    ): ParticleEffect {
        val innerRadiusFactor = if (shape.radius <= 1.0e-6) 0.0 else {
            ((shape.radius - shape.thickness) / shape.radius).coerceIn(0.0, 1.0)
        }
        return ParticleCircle(
            center = transform.origin,
            radius = shape.radius,
            axis1 = transform.right,
            axis2 = transform.up,
            filled = shape.thickness > 0.04,
            innerRadiusFactor = innerRadiusFactor,
            startDegrees = -shape.arcDegrees / 2.0,
            endDegrees = shape.arcDegrees / 2.0,
            countPerMeter = (appearance.density * 8.0).coerceAtLeast(1.0),
            includeEnd = true,
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            style = ParticleStyle(particle),
        )
    }

    private fun compileBurst(
        shape: VfxEditor2Shape.Burst,
        transform: ParticleTransform,
        particle: Particle,
    ): ParticleEffect {
        val anchor = ParticleAnchor.fixed(transform.origin, transform.forward)
        return ParticleEmitter(
            anchor = anchor,
            particle = Particle.END_ROD,
            rate = EmitterRate.BURST,
            shape = SpawnShape.CONE,
            durationTicks = 1,
            particlesPerTick = shape.count,
            burstCount = shape.count,
            radius = shape.radius,
            coneAngleDegrees = shape.spread,
            initialDirection = transform.forward,
            speedRange = shape.speed.toFloat()..shape.speed.toFloat(),
            styleCurve = ParticleStyleCurve(base = ParticleStyle(particle)),
        )
    }

    private fun effectFrame(
        origin: Point,
        direction: Vec,
        transform: dev.projects.protocol.VfxEditor2Transform,
    ): ParticleTransform {
        val base = ParticleTransform.fromDirection(origin, normalize(direction))
        val translatedOrigin = localPoint(
            base,
            transform.side,
            transform.height,
            transform.forward,
        )
        return base.copy(origin = translatedOrigin).rotate(
            yaw = transform.yaw,
            pitch = transform.pitch,
            roll = transform.roll,
        )
    }

    private fun localPoint(transform: ParticleTransform, right: Double, up: Double, forward: Double): Point =
        transform.origin.add(
            transform.right.x() * right + transform.up.x() * up + transform.forward.x() * forward,
            transform.right.y() * right + transform.up.y() * up + transform.forward.y() * forward,
            transform.right.z() * right + transform.up.z() * up + transform.forward.z() * forward,
        )

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())
}

/** Checkpoint A's public entry point, now backed by the editable default composition. */
object VfxWorkbenchPreview {
    fun create(origin: Point, direction: Vec): ParticleEffect =
        create(origin, direction, defaultVfxEditor2Composition())

    fun create(origin: Point, direction: Vec, composition: VfxEditor2Composition): ParticleEffect =
        VfxWorkbenchCompiler.compile(composition, origin, direction)
}

/** Per-player handle bookkeeping for replacement and lifecycle cleanup. */
internal class VfxEditor2PreviewHandles(
    private val cancelHandle: (ParticleEffectHandle) -> Unit,
) {
    private val handles = mutableMapOf<UUID, ParticleEffectHandle>()

    val size: Int get() = handles.size

    fun replace(owner: UUID, handle: ParticleEffectHandle) {
        handles.remove(owner)?.let(cancelHandle)
        handles[owner] = handle
    }

    fun cancel(owner: UUID) {
        handles.remove(owner)?.let(cancelHandle)
    }

    fun remove(owner: UUID): ParticleEffectHandle? = handles.remove(owner)

    fun entries(): List<Pair<UUID, ParticleEffectHandle>> = handles.toList()
}

class VfxEditor2Runtime(
    private val scheduler: ParticleAnimationScheduler,
    private val particleManager: ParticleManager,
    private val send: (Player, ProtocolMessage) -> Unit,
) {
    private val previews = VfxEditor2PreviewHandles { handle -> scheduler.cancel(handle) }
    private val lastRequestIds = mutableMapOf<UUID, Long>()

    fun open(player: Player) {
        lastRequestIds[player.uuid] = -1L
        send(player, VfxEditor2Open("Ronin Q", defaultVfxEditor2Composition()))
        sendStatus(player, VfxEditor2StatusKind.READY, "Ready")
    }

    fun preview(player: Player, request: VfxEditor2PreviewStart) {
        val lastRequestId = lastRequestIds[player.uuid]
        if (lastRequestId != null && request.requestId <= lastRequestId) return
        lastRequestIds[player.uuid] = request.requestId
        previews.cancel(player.uuid)
        sendStatus(player, VfxEditor2StatusKind.PREVIEW_REQUESTED, "Preview requested")
        try {
            // Capture the server-known position/direction at the moment the packet arrives.
            val direction = normalize(player.position.direction())
            val previewOrigin = player.position.add(
                direction.x() * VFX_EDITOR_2_PREVIEW_DISTANCE,
                direction.y() * VFX_EDITOR_2_PREVIEW_DISTANCE + VFX_EDITOR_2_PREVIEW_HEIGHT,
                direction.z() * VFX_EDITOR_2_PREVIEW_DISTANCE,
            )
            val effectId = "editor2:preview:${player.uuid}"
            val sink = particleManager.sink(
                ParticleViewer(player.position, player),
                PlayerParticleSink(player),
                effectId,
            )
            val handle = scheduler.start(
                effect = VfxWorkbenchCompiler.compile(request.composition, previewOrigin, direction),
                sink = sink,
                id = effectId,
                onComplete = { sendStatus(player, VfxEditor2StatusKind.STOPPED, "Stopped") },
            )
            previews.replace(player.uuid, handle)
            sendStatus(player, VfxEditor2StatusKind.PLAYING, "Playing")
        } catch (error: RuntimeException) {
            previews.cancel(player.uuid)
            sendStatus(player, VfxEditor2StatusKind.ERROR, error.message ?: "Preview start failed")
        }
    }

    fun stop(player: Player) {
        previews.cancel(player.uuid)
        sendStatus(player, VfxEditor2StatusKind.STOPPED, "Stopped")
    }

    fun tick() {
        previews.entries().forEach { (playerId, handle) ->
            if (handle.state == ParticleEffectState.COMPLETED || handle.state == ParticleEffectState.CANCELLED) {
                previews.remove(playerId)
            }
        }
    }

    fun disconnect(player: Player) {
        previews.cancel(player.uuid)
        lastRequestIds.remove(player.uuid)
    }

    internal fun activePreviewCount(): Int = previews.size

    private fun sendStatus(player: Player, kind: VfxEditor2StatusKind, message: String) {
        send(player, VfxEditor2Status(kind, message.take(160)))
    }

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())
}
