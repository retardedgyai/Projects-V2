package dev.projects.server

import dev.projects.protocol.ProtocolMessage
import dev.projects.protocol.VfxEditor2Open
import dev.projects.protocol.VfxEditor2PreviewStart
import dev.projects.protocol.VfxEditor2PreviewStop
import dev.projects.protocol.VfxEditor2Status
import dev.projects.protocol.VfxEditor2StatusKind
import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleBatch
import dev.projects.server.particle.ParticleEffectHandle
import dev.projects.server.particle.ParticleEffectState
import dev.projects.server.particle.ParticleExplosion
import dev.projects.server.particle.ParticleManager
import dev.projects.server.particle.ParticleRibbon
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.ParticleViewer
import dev.projects.server.particle.PlayerParticleSink
import dev.projects.server.particle.constantCurve
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.particle.Particle
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

private const val VFX_EDITOR_2_PREVIEW_DURATION_TICKS = 10
private const val VFX_EDITOR_2_PREVIEW_DISTANCE = 3.0
private const val VFX_EDITOR_2_PREVIEW_HEIGHT = 1.1

/** Fixed, deliberately small Test Slash used only to validate the Editor 2 pipeline. */
object VfxWorkbenchPreview {
    fun create(origin: Point, direction: Vec) : dev.projects.server.particle.ParticleEffect {
        val forward = normalize(direction)
        val right = horizontalRight(forward)
        val center = origin
        val path: (Double) -> Point = { progress ->
            val side = (progress - 0.5) * 4.6
            val forwardDepth = sin(progress * PI) * 0.85
            center.add(
                right.x() * side + forward.x() * forwardDepth,
                right.y() * side + forward.y() * forwardDepth,
                right.z() * side + forward.z() * forwardDepth,
            )
        }
        val redArc = ParticleRibbon(
            path = path,
            particle = dust(0xc51f3a, 0.62f),
            sampleCount = 36,
            lanes = 3,
            width = constantCurve(0.22),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { _, _ -> ParticleStyle(dust(0xc51f3a, 0.62f)) },
        )
        val whiteCore = ParticleRibbon(
            path = path,
            particle = dust(0xffffff, 0.82f),
            sampleCount = 30,
            lanes = 1,
            width = constantCurve(0.0),
            durationTicks = VFX_EDITOR_2_PREVIEW_DURATION_TICKS,
            styleAt = { _, _ -> ParticleStyle(dust(0xffffff, 0.82f)) },
        )
        val flash = ParticleExplosion(
            center = path(0.5),
            radius = 0.28,
            sphere = true,
            particle = Particle.CRIT,
            count = 8,
            speed = 0.08f,
            seed = 0x5F2L,
        )
        return ParticleBatch.of(redArc, whiteCore, flash)
    }

    private fun horizontalRight(forward: Vec): Vec {
        val flat = Vec(-forward.z(), 0.0, forward.x())
        return if (flat.length() > 1.0e-9) normalize(flat) else normalize(cross(Vec(0.0, 1.0, 0.0), forward))
    }

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())

    private fun cross(a: Vec, b: Vec): Vec = Vec(
        a.y() * b.z() - a.z() * b.y(),
        a.z() * b.x() - a.x() * b.z(),
        a.x() * b.y() - a.y() * b.x(),
    )
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

    fun open(player: Player) {
        send(player, VfxEditor2Open("Ronin Q"))
        sendStatus(player, VfxEditor2StatusKind.READY, "Ready")
    }

    fun preview(player: Player, request: VfxEditor2PreviewStart) {
        previews.cancel(player.uuid)
        sendStatus(player, VfxEditor2StatusKind.PREVIEW_REQUESTED, "Preview requested")
        try {
            // Capture the server-known position/direction at the moment the packet arrives.
            val direction = normalize(player.position.direction())
            val origin = player.position.add(
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
                effect = VfxWorkbenchPreview.create(origin, direction),
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
    }

    internal fun activePreviewCount(): Int = previews.size

    private fun sendStatus(player: Player, kind: VfxEditor2StatusKind, message: String) {
        send(player, VfxEditor2Status(kind, message.take(160)))
    }

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())
}
