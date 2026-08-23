package dev.projects.server

import dev.projects.protocol.SlashEditorParameters
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleBatch
import dev.projects.server.particle.ParticleGeometry
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path

private const val DRAFT_SCHEMA_VERSION = 2
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
        fun number(key: String, default: Double? = null): Double =
            Regex("\\\"$key\\\":([^,}]+)").find(raw)?.groupValues?.get(1)?.toDouble() ?: default
            ?: error("Missing numeric draft field: $key")
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
            laneCount = Regex("\\\"laneCount\\\":([^,}]+)").find(raw)?.groupValues?.get(1)?.toInt() ?: 1,
            laneSpacing = number("laneSpacing", 0.18),
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
                append(",\"laneCount\":").append(parameters.laneCount)
                append(",\"laneSpacing\":").append(parameters.laneSpacing)
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
        val radius = (parameters.length * (0.72 + parameters.curvature * 0.07)).coerceIn(0.5, 12.0)
        val degreeStep = (parameters.spacing * 42.0).coerceIn(2.0, 32.0)
        val laneOffsets = when (parameters.laneCount) {
            1 -> listOf(0.0)
            2 -> listOf(-0.5, 0.5)
            else -> listOf(-1.0, 0.0, 1.0)
        }
        return ParticleBatch.of(*laneOffsets.map { laneOffset ->
            ParticleGeometry.drawCleaveArc(
                origin = origin,
                facing = direction,
                radius = radius,
                tiltAngle = parameters.tilt,
                startDegrees = -parameters.arcSpan / 2.0,
                endDegrees = parameters.arcSpan / 2.0,
                rings = 1,
                extraYaw = parameters.yaw,
                degreesPerTick = parameters.arcSpan / parameters.durationTicks,
                degreeStep = degreeStep,
                lateralOffset = laneOffset * parameters.laneSpacing,
            ) { _, _, _ ->
                ParticleStyle(
                    dust(parameters.color, parameters.particleSize.toFloat() * (0.75f + parameters.width.toFloat() / 0.28f * 0.25f)),
                    count = 2,
                )
            }
        }.toTypedArray())
    }
}
