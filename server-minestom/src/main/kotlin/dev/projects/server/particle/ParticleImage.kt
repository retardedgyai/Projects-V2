package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.max

interface BlendMode {
    fun blend(base: Int, overlay: Int): Int

    companion object {
        val REPLACE: BlendMode = NamedBlendMode.REPLACE
        val MULTIPLY: BlendMode = NamedBlendMode.MULTIPLY
        val OVERLAY: BlendMode = NamedBlendMode.OVERLAY
        val XOR: BlendMode = NamedBlendMode.XOR
    }
}

enum class NamedBlendMode : BlendMode {
    REPLACE,
    MULTIPLY,
    OVERLAY,
    XOR;

    override fun blend(base: Int, overlay: Int): Int {
        fun channel(shift: Int): Int {
            val a = base shr shift and 0xff
            val b = overlay shr shift and 0xff
            return when (this) {
                REPLACE -> b
                MULTIPLY -> a * b / 255
                OVERLAY -> if (a < 128) 2 * a * b / 255 else 255 - 2 * (255 - a) * (255 - b) / 255
                XOR -> a xor b
            }
        }
        return channel(16) shl 16 or (channel(8) shl 8) or channel(0)
    }
}

data class ImageParticlePixel(val x: Int, val y: Int, val color: Int, val alpha: Int)

/** Converts a PNG into RGB dust points on an arbitrary plane. */
class ParticleImage(
    val image: BufferedImage,
    val origin: Point,
    val alphaThreshold: Int = 1,
    val lod: Int = 1,
    val resolution: Int = 1,
    val centered: Boolean = true,
    val dimensions: Vec? = null,
    val planeNormal: Vec = Vec(0.0, 1.0, 0.0),
    val planeRight: Vec? = null,
    val blendMode: BlendMode = NamedBlendMode.REPLACE,
    val overlayColor: Int? = null,
    val dustScale: Float = 1f,
    override val durationTicks: Int = 1,
    private val precomputedSampledImage: BufferedImage? = null,
) : ParticleEffect {
    private val step = max(1, max(lod, resolution))
    private val sampledImage: BufferedImage by lazy { precomputedSampledImage ?: resize(image, dimensions) }
    private val pixels: List<ImageParticlePixel> by lazy { samplePixels(sampledImage, step, alphaThreshold) }

    init {
        require(alphaThreshold in 0..255) { "alphaThreshold must be between 0 and 255" }
        require(lod >= 1 && resolution >= 1 && durationTicks >= 1) { "lod, resolution and duration must be positive" }
        require(dustScale >= 0f)
    }

    fun pixels(): List<ImageParticlePixel> = pixels

    fun points(): List<Point> {
        val (right, up) = if (planeRight != null) {
            normalize(planeRight) to normalize(planeNormal.cross(planeRight))
        } else {
            val (_, basisRight, basisUp) = basis(planeNormal)
            basisRight to basisUp
        }
        val width = dimensions?.x()?.takeIf { it > 0.0 } ?: sampledImage.width.toDouble()
        val height = dimensions?.y()?.takeIf { it > 0.0 } ?: sampledImage.height.toDouble()
        return pixels.map { pixel ->
            val x = (pixel.x + 0.5) / sampledImage.width * width
            val y = (pixel.y + 0.5) / sampledImage.height * height
            val horizontal = if (centered) x - width / 2.0 else x
            val vertical = if (centered) y - height / 2.0 else y
            origin.add(
                right.x() * horizontal + up.x() * vertical,
                right.y() * horizontal + up.y() * vertical,
                right.z() * horizontal + up.z() * vertical,
            )
        }
    }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick != 0) return
        val (right, up) = if (planeRight != null) {
            normalize(planeRight) to normalize(planeNormal.cross(planeRight))
        } else {
            val (_, basisRight, basisUp) = basis(planeNormal)
            basisRight to basisUp
        }
        val width = dimensions?.x()?.takeIf { it > 0.0 } ?: sampledImage.width.toDouble()
        val height = dimensions?.y()?.takeIf { it > 0.0 } ?: sampledImage.height.toDouble()
        pixels.forEachIndexed { index, pixel ->
            val x = (pixel.x + 0.5) / sampledImage.width * width
            val y = (pixel.y + 0.5) / sampledImage.height * height
            val horizontal = if (centered) x - width / 2.0 else x
            val vertical = if (centered) y - height / 2.0 else y
            val point = origin.add(
                right.x() * horizontal + up.x() * vertical,
                right.y() * horizontal + up.y() * vertical,
                right.z() * horizontal + up.z() * vertical,
            )
            // The configured overlay is the blend base; the image pixel is the overlay operand.
            val color = overlayColor?.let { blendMode.blend(it, pixel.color) } ?: pixel.color
            emitStyle(point, style = ParticleStyle(dust(color, dustScale)), index, sink)
        }
    }

    companion object {
        private val cache = mutableMapOf<String, BufferedImage>()
        private val resizedCache = mutableMapOf<String, BufferedImage>()

        fun fromPng(
            bytes: ByteArray,
            origin: Point,
            alphaThreshold: Int = 1,
            lod: Int = 1,
            resolution: Int = 1,
            centered: Boolean = true,
            dimensions: Vec? = null,
            planeNormal: Vec = Vec(0.0, 1.0, 0.0),
            planeRight: Vec? = null,
            blendMode: BlendMode = NamedBlendMode.REPLACE,
            overlayColor: Int? = null,
            dustScale: Float = 1f,
            durationTicks: Int = 1,
        ): ParticleImage {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val image = synchronized(cache) { cache.getOrPut(digest) {
                ImageIO.read(ByteArrayInputStream(bytes)) ?: error("PNG data could not be decoded")
            } }
            val resized = synchronized(resizedCache) {
                val width = dimensions?.x()?.toInt()?.takeIf { it > 0 } ?: image.width
                val height = dimensions?.y()?.toInt()?.takeIf { it > 0 } ?: image.height
                resizedCache.getOrPut("$digest:$width:$height") { resize(image, dimensions) }
            }
            return ParticleImage(image, origin, alphaThreshold, lod, resolution, centered, dimensions, planeNormal, planeRight, blendMode, overlayColor, dustScale, durationTicks, resized)
        }

        fun clearCache() {
            synchronized(cache) { cache.clear() }
            synchronized(resizedCache) { resizedCache.clear() }
        }

        fun blend(base: Int, overlay: Int, mode: BlendMode = NamedBlendMode.REPLACE): Int = mode.blend(base, overlay)

        private fun resize(source: BufferedImage, dimensions: Vec?): BufferedImage {
            val width = dimensions?.x()?.toInt()?.takeIf { it > 0 } ?: source.width
            val height = dimensions?.y()?.toInt()?.takeIf { it > 0 } ?: source.height
            if (width == source.width && height == source.height) return source
            val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until height) for (x in 0 until width) {
                val sourceX = x * source.width / width
                val sourceY = y * source.height / height
                result.setRGB(x, y, source.getRGB(sourceX, sourceY))
            }
            return result
        }

        private fun samplePixels(image: BufferedImage, step: Int, threshold: Int): List<ImageParticlePixel> {
            val result = mutableListOf<ImageParticlePixel>()
            for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xff
                if (alpha >= threshold) result += ImageParticlePixel(x, y, argb and 0xffffff, alpha)
            }
            return result
        }
    }
}

private fun Vec.cross(other: Vec): Vec = Vec(
    y() * other.z() - z() * other.y(),
    z() * other.x() - x() * other.z(),
    x() * other.y() - y() * other.x(),
)
