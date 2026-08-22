package dev.projects.server

import dev.projects.protocol.SlashEditorParameters
import dev.projects.server.particle.ParticleCircle
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleGeometry
import dev.projects.server.particle.ParticleParallel
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.basis
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path

private const val DRAFT_SCHEMA_VERSION = 1
private const val MAX_DRAFTS = 16

/** Fixed-path, deliberately small persistence for the in-game slash authoring tool. */
class SlashDraftStore(private val file: Path) {
    private val drafts = linkedMapOf<String, SlashEditorParameters>()

    init {
        readFromDisk()
    }

    fun list(): List<String> = drafts.keys.sorted()

    fun load(name: String): SlashEditorParameters? = drafts[name]

    fun save(name: String, parameters: SlashEditorParameters): Boolean {
        if (!isSafeName(name)) return false
        if (name !in drafts && drafts.size >= MAX_DRAFTS) return false
        drafts[name] = parameters
        return writeToDisk()
    }

    private fun readFromDisk() {
        if (!Files.isRegularFile(file)) return
        runCatching {
            val content = Files.readString(file)
            Regex("""\{"name":"([^"\\]*)","parameters":\{([^}]*)\}\}""")
                .findAll(content)
                .take(MAX_DRAFTS)
                .forEach { match ->
                    val name = match.groupValues[1]
                    if (!isSafeName(name)) return@forEach
                    parseParameters(match.groupValues[2])?.let { drafts[name] = it }
                }
        }
    }

    private fun parseParameters(raw: String): SlashEditorParameters? = runCatching {
        fun number(key: String): Double = Regex("\\\"$key\\\":([^,}]+)").find(raw)!!.groupValues[1].toDouble()
        fun integer(key: String): Int = Regex("\\\"$key\\\":([^,}]+)").find(raw)!!.groupValues[1].toInt()
        SlashEditorParameters.clamped(
            originY = number("originY"),
            forwardOffset = number("forwardOffset"),
            length = number("length"),
            arcSpan = number("arcSpan"),
            curvature = number("curvature"),
            tilt = number("tilt"),
            yaw = number("yaw"),
            width = number("width"),
            particleSize = number("particleSize"),
            spacing = number("spacing"),
            durationTicks = integer("durationTicks"),
            color = integer("color"),
            targetDistance = number("targetDistance"),
        )
    }.getOrNull()

    private fun writeToDisk(): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val json = buildString {
            append("{\"schemaVersion\":").append(DRAFT_SCHEMA_VERSION).append(",\"drafts\":[")
            drafts.entries.sortedBy { it.key }.forEachIndexed { index, (name, parameters) ->
                if (index > 0) append(',')
                append("{\"name\":\"").append(name).append("\",\"parameters\":{")
                append("\"originY\":").append(parameters.originY)
                append(",\"forwardOffset\":").append(parameters.forwardOffset)
                append(",\"length\":").append(parameters.length)
                append(",\"arcSpan\":").append(parameters.arcSpan)
                append(",\"curvature\":").append(parameters.curvature)
                append(",\"tilt\":").append(parameters.tilt)
                append(",\"yaw\":").append(parameters.yaw)
                append(",\"width\":").append(parameters.width)
                append(",\"particleSize\":").append(parameters.particleSize)
                append(",\"spacing\":").append(parameters.spacing)
                append(",\"durationTicks\":").append(parameters.durationTicks)
                append(",\"color\":").append(parameters.color)
                append(",\"targetDistance\":").append(parameters.targetDistance)
                append("}}")
            }
            append("]}")
        }
        Files.writeString(file, json)
        true
    }.getOrDefault(false)

    companion object {
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9 _-]{0,31}")

        fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)
    }
}

object SlashEditorPreview {
    fun create(origin: Point, direction: Vec, parameters: SlashEditorParameters): ParticleEffect {
        val (forward, right, up) = basis(direction)
        val radius = (parameters.length * (0.72 + parameters.curvature * 0.07)).coerceIn(0.5, 12.0)
        val lanes = when {
            parameters.width < 0.16 -> 1
            parameters.width < 0.7 -> 2
            else -> 3
        }
        val degreeStep = (parameters.spacing * 42.0).coerceIn(2.0, 32.0)
        val arc = ParticleGeometry.drawCleaveArc(
            origin = origin,
            facing = direction,
            radius = radius,
            tiltAngle = parameters.tilt,
            startDegrees = -parameters.arcSpan / 2.0,
            endDegrees = parameters.arcSpan / 2.0,
            rings = lanes,
            extraYaw = parameters.yaw,
            ringSpacing = parameters.width.coerceAtLeast(0.04),
            degreesPerTick = parameters.arcSpan / parameters.durationTicks,
            degreeStep = degreeStep,
        ) { _, ring, _ ->
            ParticleStyle(
                dust(parameters.color, parameters.particleSize.toFloat() * if (ring == 0) 1.0f else 0.72f),
                count = if (ring == 0) 2 else 1,
            )
        }
        val target = origin.add(
            forward.x() * parameters.targetDistance,
            forward.y() * parameters.targetDistance,
            forward.z() * parameters.targetDistance,
        )
        val targetMarker = ParticleCircle(
            center = target,
            radius = 0.16,
            axis1 = right,
            axis2 = up,
            countPerMeter = 18.0,
            style = ParticleStyle(dust(parameters.color, (parameters.particleSize * 0.7).toFloat())),
        )
        return ParticleParallel.of(arc, targetMarker)
    }
}
