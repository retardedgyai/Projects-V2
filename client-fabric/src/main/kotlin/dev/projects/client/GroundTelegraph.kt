package dev.projects.client

import dev.projects.protocol.GroundTelegraphStart
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ClientGroundTelegraph(
    val start: GroundTelegraphStart,
    val ageTicks: Int,
)

class GroundTelegraphClientState {
    private val active = linkedMapOf<Long, ClientGroundTelegraph>()

    @Synchronized
    fun start(message: GroundTelegraphStart) {
        active[message.telegraphId] = ClientGroundTelegraph(message, 0)
    }

    @Synchronized
    fun remove(telegraphId: Long) {
        active.remove(telegraphId)
    }

    @Synchronized
    fun tick() {
        val expired = active.values
            .filter { it.ageTicks + 1 >= it.start.durationTicks }
            .map { it.start.telegraphId }
        expired.forEach(active::remove)
        active.replaceAll { _, telegraph -> telegraph.copy(ageTicks = telegraph.ageTicks + 1) }
    }

    @Synchronized
    fun clear() {
        active.clear()
    }

    @Synchronized
    fun snapshot(): List<ClientGroundTelegraph> = active.values.toList()

    @Synchronized
    fun size(): Int = active.size
}

data class SectorPoint(val x: Double, val y: Double, val z: Double)

data class SectorMesh(
    val fillFan: List<SectorPoint>,
    val borderQuads: List<List<SectorPoint>>,
)

object SectorTelegraphGeometry {
    private const val MAX_ARC_SEGMENTS = 36
    private const val DEGREES_PER_SEGMENT = 10.0
    private const val HEIGHT_OFFSET = 0.035
    private const val BORDER_WIDTH = 0.12

    fun build(telegraph: ClientGroundTelegraph): SectorMesh {
        val message = telegraph.start
        val forwardLength = sqrt(message.facingX * message.facingX + message.facingZ * message.facingZ)
        val forwardX = if (forwardLength > 1.0e-9) message.facingX / forwardLength else 0.0
        val forwardZ = if (forwardLength > 1.0e-9) message.facingZ / forwardLength else 1.0
        val rightX = -forwardZ
        val rightZ = forwardX
        val segments = ceil(message.angleDegrees / DEGREES_PER_SEGMENT)
            .toInt()
            .coerceIn(1, MAX_ARC_SEGMENTS)
        val halfAngle = Math.toRadians(message.angleDegrees / 2.0)
        val center = SectorPoint(message.centerX, message.centerY + HEIGHT_OFFSET, message.centerZ)
        val arc = (0..segments).map { index ->
            val angle = -halfAngle + 2.0 * halfAngle * index / segments
            SectorPoint(
                x = message.centerX + message.radius * (forwardX * cos(angle) + rightX * sin(angle)),
                y = center.y,
                z = message.centerZ + message.radius * (forwardZ * cos(angle) + rightZ * sin(angle)),
            )
        }
        val innerRadius = (message.radius - BORDER_WIDTH).coerceAtLeast(0.0)
        val innerArc = (0..segments).map { index ->
            val angle = -halfAngle + 2.0 * halfAngle * index / segments
            SectorPoint(
                x = message.centerX + innerRadius * (forwardX * cos(angle) + rightX * sin(angle)),
                y = center.y,
                z = message.centerZ + innerRadius * (forwardZ * cos(angle) + rightZ * sin(angle)),
            )
        }
        val border = buildList {
            for (index in 0 until segments) {
                add(listOf(arc[index], arc[index + 1], innerArc[index + 1], innerArc[index]))
            }
            add(radialQuad(center, arc.first(), rightX, rightZ))
            add(radialQuad(center, arc.last(), rightX, rightZ))
        }
        return SectorMesh(listOf(center) + arc, border)
    }

    private fun radialQuad(
        start: SectorPoint,
        end: SectorPoint,
        perpendicularX: Double,
        perpendicularZ: Double,
    ): List<SectorPoint> {
        val halfWidth = BORDER_WIDTH / 2.0
        return listOf(
            SectorPoint(start.x + perpendicularX * halfWidth, start.y, start.z + perpendicularZ * halfWidth),
            SectorPoint(end.x + perpendicularX * halfWidth, end.y, end.z + perpendicularZ * halfWidth),
            SectorPoint(end.x - perpendicularX * halfWidth, end.y, end.z - perpendicularZ * halfWidth),
            SectorPoint(start.x - perpendicularX * halfWidth, start.y, start.z - perpendicularZ * halfWidth),
        )
    }
}

object GroundTelegraphRenderer {
    private val renderDataKey = RenderStateDataKey.create<List<ClientGroundTelegraph>> {
        "projects:ground_telegraphs"
    }
    private val state = GroundTelegraphClientState()
    private var lastDimension: Identifier? = null
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        LevelExtractionEvents.END_EXTRACTION.register { context ->
            (context.levelState() as FabricRenderState).setData(renderDataKey, state.snapshot())
        }
        LevelRenderEvents.COLLECT_SUBMITS.register(::collectSubmits)
    }

    fun start(message: GroundTelegraphStart) {
        state.start(message)
    }

    fun remove(telegraphId: Long) {
        state.remove(telegraphId)
    }

    private fun tick(client: Minecraft) {
        val dimension = client.level?.dimension()?.identifier()
        if (client.player == null || dimension != lastDimension) {
            state.clear()
        }
        lastDimension = dimension
        if (client.player != null) state.tick()
    }

    private fun collectSubmits(context: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext) {
        val renderState = context.levelState() as FabricRenderState
        val telegraphs = renderState.getData(renderDataKey) ?: return
        if (telegraphs.isEmpty()) return

        val camera = context.levelState().cameraRenderState.pos
        val poseStack = context.poseStack()
        poseStack.pushPose()
        poseStack.translate(-camera.x, -camera.y, -camera.z)
        telegraphs.forEach { telegraph ->
            val mesh = SectorTelegraphGeometry.build(telegraph)
            val warning = warningStrength(telegraph)
            submitMesh(context.submitNodeCollector(), poseStack, mesh, warning)
        }
        poseStack.popPose()
    }

    private fun submitMesh(
        collector: SubmitNodeCollector,
        poseStack: com.mojang.blaze3d.vertex.PoseStack,
        mesh: SectorMesh,
        warning: Float,
    ) {
        val fillAlpha = (46.0f + 34.0f * warning).toInt().coerceIn(0, 255)
        val borderAlpha = (122.0f + 52.0f * warning).toInt().coerceIn(0, 255)
        submitGeometry(collector, poseStack, RenderTypes.debugTriangleFan()) { pose, consumer ->
            mesh.fillFan.forEach { vertex ->
                consumer.addVertex(pose, vertex.x.toFloat(), vertex.y.toFloat(), vertex.z.toFloat())
                    .setColor(185, 12, 28, fillAlpha)
            }
        }
        submitGeometry(collector, poseStack, RenderTypes.debugQuads()) { pose, consumer ->
            mesh.borderQuads.flatten().forEach { vertex ->
                consumer.addVertex(pose, vertex.x.toFloat(), vertex.y.toFloat(), vertex.z.toFloat())
                    .setColor(235, 28, 42, borderAlpha)
            }
        }
    }

    private fun submitGeometry(
        collector: SubmitNodeCollector,
        poseStack: com.mojang.blaze3d.vertex.PoseStack,
        renderType: RenderType,
        renderer: net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer,
    ) {
        collector.submitCustomGeometry(poseStack, renderType, renderer)
    }

    private fun warningStrength(telegraph: ClientGroundTelegraph): Float {
        val progress = telegraph.ageTicks.toFloat() / telegraph.start.durationTicks
        if (progress < 0.7f) return 0.0f
        val pulse = (kotlin.math.sin(telegraph.ageTicks * 0.55) * 0.5 + 0.5).toFloat()
        return ((progress - 0.7f) / 0.3f).coerceIn(0.0f, 1.0f) * (0.55f + 0.45f * pulse)
    }
}
