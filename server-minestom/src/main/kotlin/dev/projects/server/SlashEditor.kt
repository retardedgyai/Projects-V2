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

private fun parseSlashParameters(raw: String): SlashEditorParameters? = runCatching {
    fun number(key: String, default: Double? = null): Double =
        Regex("\\\"$key\\\":([^,}]+)").find(raw)?.groupValues?.get(1)?.toDouble() ?: default
        ?: error("Missing numeric slash field: $key")
    fun integer(key: String): Int = Regex("\\\"$key\\\":([^,}]+)").find(raw)!!.groupValues[1].toInt()
    SlashEditorParameters.clamped(
        originY = number("originY"), forwardOffset = number("forwardOffset"), length = number("length"),
        arcSpan = number("arcSpan"), curvature = number("curvature"), tilt = number("tilt"), yaw = number("yaw"),
        width = number("width"), laneCount = Regex("\\\"laneCount\\\":([^,}]+)").find(raw)?.groupValues?.get(1)?.toInt() ?: 1,
        laneSpacing = number("laneSpacing", 0.18),
        particleSize = number("particleSize"), spacing = number("spacing"), durationTicks = integer("durationTicks"),
        color = integer("color"), targetDistance = number("targetDistance"),
    )
}.getOrNull()

private fun appendSlashParameters(json: StringBuilder, parameters: SlashEditorParameters) {
    json.append("\"originY\":").append(parameters.originY)
        .append(",\"forwardOffset\":").append(parameters.forwardOffset)
        .append(",\"length\":").append(parameters.length)
        .append(",\"arcSpan\":").append(parameters.arcSpan)
        .append(",\"curvature\":").append(parameters.curvature)
        .append(",\"tilt\":").append(parameters.tilt)
        .append(",\"yaw\":").append(parameters.yaw)
        .append(",\"width\":").append(parameters.width)
        .append(",\"laneCount\":").append(parameters.laneCount)
        .append(",\"laneSpacing\":").append(parameters.laneSpacing)
        .append(",\"particleSize\":").append(parameters.particleSize)
        .append(",\"spacing\":").append(parameters.spacing)
        .append(",\"durationTicks\":").append(parameters.durationTicks)
        .append(",\"color\":").append(parameters.color)
        .append(",\"targetDistance\":").append(parameters.targetDistance)
}

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

    private fun parseParameters(raw: String): SlashEditorParameters? = parseSlashParameters(raw)

    private fun writeToDisk(): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val json = buildString {
            append("{\"schemaVersion\":").append(DRAFT_SCHEMA_VERSION).append(",\"drafts\":[")
            drafts.entries.sortedBy { it.key }.forEachIndexed { index, (name, parameters) ->
                if (index > 0) append(',')
                append("{\"name\":\"").append(name).append("\",\"parameters\":{")
                appendSlashParameters(this, parameters)
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

class Skill3SlashBindingStore(private val file: Path) {
    private var binding: SlashEditorParameters? = readFromDisk()

    fun load(): SlashEditorParameters? = binding

    fun save(parameters: SlashEditorParameters): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val json = StringBuilder("{\"schemaVersion\":1,\"parameters\":{")
        appendSlashParameters(json, parameters)
        json.append("}}")
        Files.writeString(file, json)
        binding = parameters
        true
    }.getOrDefault(false)

    private fun readFromDisk(): SlashEditorParameters? = runCatching {
        if (!Files.isRegularFile(file)) return null
        parseSlashParameters(Files.readString(file))
    }.getOrNull()
}

object SlashEditorPreview {
    fun create(origin: Point, direction: Vec, parameters: SlashEditorParameters, reverseDraw: Boolean = false): ParticleEffect {
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
                startDegrees = if (reverseDraw) parameters.arcSpan / 2.0 else -parameters.arcSpan / 2.0,
                endDegrees = if (reverseDraw) -parameters.arcSpan / 2.0 else parameters.arcSpan / 2.0,
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

data class Skill3SlashPulseSpec(
    val parameters: SlashEditorParameters,
    val reverseDraw: Boolean,
)

internal fun skill3SlashPulseSpec(authored: SlashEditorParameters, pulseIndex: Int): Skill3SlashPulseSpec {
    val (yawOffset, tiltOffset, originYOffset, reverseDraw) = when (pulseIndex) {
        1 -> PulseChoreography(-28.0, 28.0, -0.10, false)
        2 -> PulseChoreography(30.0, -26.0, 0.15, true)
        3 -> PulseChoreography(-42.0, 5.0, 0.30, false)
        4 -> PulseChoreography(45.0, 32.0, 0.0, true)
        else -> PulseChoreography(0.0, 0.0, 0.0, false)
    }
    return Skill3SlashPulseSpec(
        parameters = authored.copy(
            yaw = authored.yaw + yawOffset,
            tilt = authored.tilt + tiltOffset,
            originY = authored.originY + originYOffset,
            durationTicks = Skill3State.PULSE_INTERVAL_TICKS,
        ),
        reverseDraw = reverseDraw,
    )
}

private data class PulseChoreography(
    val yawOffset: Double,
    val tiltOffset: Double,
    val originYOffset: Double,
    val reverseDraw: Boolean,
)

internal fun slashOrigin(position: Point, direction: Vec, parameters: SlashEditorParameters): Point {
    val forward = FixedAttackTester.normalizeHorizontal(direction)
    return position.add(
        forward.x() * parameters.forwardOffset,
        parameters.originY,
        forward.z() * parameters.forwardOffset,
    )
}

internal fun skill3SlashOrigin(position: Point, direction: Vec, parameters: SlashEditorParameters): Point {
    val length = direction.length()
    val forward = if (length.isFinite() && length > 1.0e-9) {
        direction.mul(1.0 / length)
    } else {
        Vec(0.0, 0.0, 1.0)
    }
    return position.add(
        forward.x() * parameters.forwardOffset,
        parameters.originY + forward.y() * parameters.forwardOffset,
        forward.z() * parameters.forwardOffset,
    )
}
