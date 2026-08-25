package dev.projects.client

import dev.projects.protocol.RoninVfxEvent
import dev.projects.protocol.RoninVfxTexture
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class ClientRoninSlash(
    val event: RoninVfxEvent,
    val ageTicks: Int,
)

/**
 * Ronin-local world-space PNG quad renderer. It deliberately does not expose a
 * general ability VFX API: the only producer is the Ronin protocol event.
 */
object RoninTexturedSlashRenderer {
    private const val FULL_BRIGHT_LIGHT = 0x00F000F0

    private val renderDataKey = RenderStateDataKey.create<List<ClientRoninSlash>> {
        "projects:ronin_textured_slashes"
    }
    private val active = mutableListOf<ClientRoninSlash>()
    private var lastDimension: Identifier? = null
    private var registered = false

    private val arcTexture = Identifier.fromNamespaceAndPath("projects", "textures/ronin/arc_slash.png")
    private val thinTexture = Identifier.fromNamespaceAndPath("projects", "textures/ronin/thin_slash.png")
    private val circularTexture = Identifier.fromNamespaceAndPath("projects", "textures/ronin/circular_slash.png")

    fun register() {
        if (registered) return
        registered = true
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        LevelExtractionEvents.END_EXTRACTION.register { context ->
            (context.levelState() as FabricRenderState).setData(renderDataKey, snapshot())
        }
        LevelRenderEvents.COLLECT_SUBMITS.register(::collectSubmits)
    }

    @Synchronized
    fun enqueue(event: RoninVfxEvent) {
        active += ClientRoninSlash(event, 0)
        // W3 deliberately uses several small quads, so retain a generous but
        // bounded local list if a crowded test scene sends many events.
        if (active.size > 160) active.subList(0, active.size - 160).clear()
    }

    private fun tick(client: Minecraft) {
        val dimension = client.level?.dimension()?.identifier()
        if (client.player == null || dimension != lastDimension) {
            synchronized(this) { active.clear() }
        }
        lastDimension = dimension
        if (client.player == null) return
        synchronized(this) {
            active.removeIf { slash -> slash.ageTicks >= slash.event.delayTicks + slash.event.lifetimeTicks }
            active.replaceAll { slash -> slash.copy(ageTicks = slash.ageTicks + 1) }
        }
    }

    @Synchronized
    private fun snapshot(): List<ClientRoninSlash> = active.toList()

    private fun collectSubmits(context: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext) {
        val renderState = context.levelState() as FabricRenderState
        val slashes = renderState.getData(renderDataKey) ?: return
        if (slashes.isEmpty()) return

        val camera = context.levelState().cameraRenderState.pos
        val poseStack = context.poseStack()
        poseStack.pushPose()
        poseStack.translate(-camera.x, -camera.y, -camera.z)
        val collector = context.submitNodeCollector()
        slashes.forEach { slash ->
            val progress = ((slash.ageTicks - slash.event.delayTicks).toDouble() /
                max(1, slash.event.lifetimeTicks - 1)).coerceIn(0.0, 1.0)
            if (slash.ageTicks < slash.event.delayTicks) return@forEach
            val envelope = sin(Math.PI * progress).toFloat().coerceAtLeast(0.0f)
            val growth = if (progress < 0.35) {
                progress / 0.35
            } else {
                1.0 + (progress - 0.35) / 0.65 * 0.10
            }
            val scale = slash.event.scale * (0.68 + 0.32 * growth)
            val alpha = slash.event.alpha * envelope
            if (alpha <= 0.001) return@forEach
            submitSlash(collector, poseStack, slash.event, scale, alpha)
            if (slash.event.effect.name in setOf("Q_SLASH", "CROSSCUT_FLASH", "TEMPEST_FINAL", "R_SWEET_DRAW")) {
                submitSlash(
                    collector,
                    poseStack,
                    slash.event,
                    scale = scale * 0.72,
                    alpha = alpha * 0.78,
                    tintOverride = 0xffffff,
                )
            }
        }
        poseStack.popPose()
    }

    private fun submitSlash(
        collector: SubmitNodeCollector,
        poseStack: com.mojang.blaze3d.vertex.PoseStack,
        event: RoninVfxEvent,
        scale: Double,
        alpha: Double,
        tintOverride: Int? = null,
    ) {
        val texture = when (event.texture) {
            RoninVfxTexture.ARC -> arcTexture
            RoninVfxTexture.THIN -> thinTexture
            RoninVfxTexture.CIRCULAR -> circularTexture
        }
        val renderType = RenderTypes.entityTranslucent(texture)
        collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
            submitQuad(pose, consumer, event, scale, alpha, tintOverride)
        }
    }

    private fun submitQuad(
        pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        event: RoninVfxEvent,
        scale: Double,
        alpha: Double,
        tintOverride: Int?,
    ) {
        val directionLength = sqrt(
            event.directionX * event.directionX +
                event.directionY * event.directionY +
                event.directionZ * event.directionZ,
        )
        if (directionLength <= 1.0e-9) return
        val forwardX = event.directionX / directionLength
        val forwardY = event.directionY / directionLength
        val forwardZ = event.directionZ / directionLength
        val referenceX: Double
        val referenceY: Double
        val referenceZ: Double
        if (abs(forwardY) < 0.92) {
            referenceX = 0.0
            referenceY = 1.0
            referenceZ = 0.0
        } else {
            referenceX = 1.0
            referenceY = 0.0
            referenceZ = 0.0
        }
        var rightX = referenceY * forwardZ - referenceZ * forwardY
        var rightY = referenceZ * forwardX - referenceX * forwardZ
        var rightZ = referenceX * forwardY - referenceY * forwardX
        val rightLength = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
        rightX /= rightLength
        rightY /= rightLength
        rightZ /= rightLength
        var upX = forwardY * rightZ - forwardZ * rightY
        var upY = forwardZ * rightX - forwardX * rightZ
        var upZ = forwardX * rightY - forwardY * rightX

        val roll = event.roll
        val rollCos = cos(roll)
        val rollSin = sin(roll)
        val rolledRightX = rightX * rollCos + upX * rollSin
        val rolledRightY = rightY * rollCos + upY * rollSin
        val rolledRightZ = rightZ * rollCos + upZ * rollSin
        val rolledUpX = upX * rollCos - rightX * rollSin
        val rolledUpY = upY * rollCos - rightY * rollSin
        val rolledUpZ = upZ * rollCos - rightZ * rollSin
        rightX = rolledRightX
        rightY = rolledRightY
        rightZ = rolledRightZ
        upX = rolledUpX
        upY = rolledUpY
        upZ = rolledUpZ

        val halfWidth = event.width * scale / 2.0
        val halfHeight = event.height * scale / 2.0
        val cx = event.originX
        val cy = event.originY
        val cz = event.originZ
        val corners = arrayOf(
            doubleArrayOf(cx - rightX * halfWidth - upX * halfHeight, cy - rightY * halfWidth - upY * halfHeight, cz - rightZ * halfWidth - upZ * halfHeight),
            doubleArrayOf(cx + rightX * halfWidth - upX * halfHeight, cy + rightY * halfWidth - upY * halfHeight, cz + rightZ * halfWidth - upZ * halfHeight),
            doubleArrayOf(cx + rightX * halfWidth + upX * halfHeight, cy + rightY * halfWidth + upY * halfHeight, cz + rightZ * halfWidth + upZ * halfHeight),
            doubleArrayOf(cx - rightX * halfWidth + upX * halfHeight, cy - rightY * halfWidth + upY * halfHeight, cz - rightZ * halfWidth + upZ * halfHeight),
        )
        val tint = tintOverride ?: event.tintRgb
        val red = (tint shr 16) and 0xff
        val green = (tint shr 8) and 0xff
        val blue = tint and 0xff
        val alphaByte = (alpha.coerceIn(0.0, 1.0) * 255.0).toInt()
        addTexturedQuad(consumer, pose, corners, red, green, blue, alphaByte)
        // Emit the reverse winding as well, keeping the slash readable from
        // both sides without introducing a custom shader or render framework.
        addTexturedQuad(consumer, pose, corners.reversedArray(), red, green, blue, alphaByte)
    }

    private fun addTexturedQuad(
        consumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
        corners: Array<DoubleArray>,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        val uvs = arrayOf(floatArrayOf(0.0f, 1.0f), floatArrayOf(1.0f, 1.0f), floatArrayOf(1.0f, 0.0f), floatArrayOf(0.0f, 0.0f))
        val edgeAX = corners[1][0] - corners[0][0]
        val edgeAY = corners[1][1] - corners[0][1]
        val edgeAZ = corners[1][2] - corners[0][2]
        val edgeBX = corners[2][0] - corners[0][0]
        val edgeBY = corners[2][1] - corners[0][1]
        val edgeBZ = corners[2][2] - corners[0][2]
        val normalX = edgeAY * edgeBZ - edgeAZ * edgeBY
        val normalY = edgeAZ * edgeBX - edgeAX * edgeBZ
        val normalZ = edgeAX * edgeBY - edgeAY * edgeBX
        val normalLength = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
        if (normalLength <= 1.0e-9) return
        val normalizedNormalX = (normalX / normalLength).toFloat()
        val normalizedNormalY = (normalY / normalLength).toFloat()
        val normalizedNormalZ = (normalZ / normalLength).toFloat()
        corners.indices.forEach { index ->
            val corner = corners[index]
            consumer.addVertex(pose, corner[0].toFloat(), corner[1].toFloat(), corner[2].toFloat())
                .setColor(red, green, blue, alpha)
                .setUv(uvs[index][0], uvs[index][1])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, normalizedNormalX, normalizedNormalY, normalizedNormalZ)
        }
    }
}
