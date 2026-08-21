package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ParticlePresetParameterType { NUMBER, COLOR, BOOLEAN, ENUM }

data class ParticlePresetParameter(
    val name: String,
    val type: ParticlePresetParameterType,
    val defaultValue: Any,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val values: List<String> = emptyList(),
)

/** Values supplied by an author or command. Origin and direction are runtime context, not schema parameters. */
data class ParticlePresetParameters(
    val origin: Point = Pos.ZERO,
    val direction: Vec = Vec(0.0, 0.0, 1.0),
    val values: Map<String, Any> = emptyMap(),
) {
    fun with(name: String, value: Any): ParticlePresetParameters = copy(values = values + (name to value))

    fun number(name: String, fallback: Double = 0.0): Double = (values[name] as? Number)?.toDouble() ?: fallback
    fun color(name: String, fallback: Int = 0xffffff): Int = (values[name] as? Number)?.toInt() ?: fallback
    fun boolean(name: String, fallback: Boolean = false): Boolean = values[name] as? Boolean ?: fallback
    fun enum(name: String, fallback: String = ""): String = values[name] as? String ?: fallback

    companion object {
        fun at(origin: Point, direction: Vec = Vec(0.0, 0.0, 1.0)): ParticlePresetParameters =
            ParticlePresetParameters(origin, direction)
    }
}

data class ParticlePreset(
    val id: String,
    val displayName: String,
    val tags: Set<String>,
    val parameters: List<ParticlePresetParameter> = emptyList(),
    val factory: (ParticlePresetParameters) -> ParticleEffect,
) {
    val parameterSchema: List<ParticlePresetParameter> get() = parameters

    fun create(input: ParticlePresetParameters = ParticlePresetParameters()): ParticleEffect = factory(
        input.copy(values = parameters.associate { parameter ->
            parameter.name to normalize(parameter, input.values[parameter.name])
        }),
    )

    private fun normalize(parameter: ParticlePresetParameter, raw: Any?): Any = when (parameter.type) {
        ParticlePresetParameterType.NUMBER -> {
            val value = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }?.takeIf { it.isFinite() } ?: (parameter.defaultValue as Number).toDouble()
            value.coerceIn(parameter.minimum ?: -Double.MAX_VALUE, parameter.maximum ?: Double.MAX_VALUE)
        }
        ParticlePresetParameterType.COLOR -> when (raw) {
            is Number -> raw.toInt()
            is String -> raw.removePrefix("#").removePrefix("0x").toIntOrNull(16)
            else -> null
        }?.coerceIn(0, 0xffffff) ?: (parameter.defaultValue as Number).toInt().coerceIn(0, 0xffffff)
        ParticlePresetParameterType.BOOLEAN -> raw as? Boolean ?: parameter.defaultValue as Boolean
        ParticlePresetParameterType.ENUM -> (raw as? String)?.takeIf { it in parameter.values }
            ?: parameter.defaultValue as String
    }
}

data class ParticlePresetOverrideResult(val values: Map<String, Any> = emptyMap(), val error: String? = null)

fun parseParticlePresetOverrides(preset: ParticlePreset, arguments: Array<String>): ParticlePresetOverrideResult {
    val schema = preset.parameters.associateBy { it.name }
    val values = mutableMapOf<String, Any>()
    for (argument in arguments) {
        val separator = argument.indexOf('=')
        if (separator <= 0 || separator == argument.lastIndex) {
            return ParticlePresetOverrideResult(error = "Use parameter=value")
        }
        val name = argument.substring(0, separator)
        val raw = argument.substring(separator + 1)
        val parameter = schema[name] ?: return ParticlePresetOverrideResult(error = "Unknown parameter: $name")
        val value: Any = when (parameter.type) {
            ParticlePresetParameterType.NUMBER -> raw.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return ParticlePresetOverrideResult(error = "$name must be a finite number")
            ParticlePresetParameterType.COLOR -> raw.removePrefix("#").removePrefix("0x").toIntOrNull(16)
                ?: return ParticlePresetOverrideResult(error = "$name must be a hex color")
            ParticlePresetParameterType.BOOLEAN -> raw.toBooleanStrictOrNull()
                ?: return ParticlePresetOverrideResult(error = "$name must be true or false")
            ParticlePresetParameterType.ENUM -> raw.takeIf { it in parameter.values }
                ?: return ParticlePresetOverrideResult(error = "$name must be one of ${parameter.values.joinToString()}")
        }
        values[name] = value
    }
    return ParticlePresetOverrideResult(values)
}

object ParticlePresetRegistry {
    val all: List<ParticlePreset> = buildCatalogue().sortedBy { it.id }
    val size: Int get() = all.size
    private val byId = all.associateBy { it.id }

    init {
        require(byId.size == all.size) { "Particle preset IDs must be unique" }
    }

    operator fun get(id: String): ParticlePreset? = byId[id]
    fun find(id: String): ParticlePreset? = byId[id]
    fun list(tag: String? = null): List<ParticlePreset> = all.filter { tag == null || tag in it.tags }
    fun search(tag: String): List<ParticlePreset> = list(tag)

    fun instantiate(id: String, parameters: ParticlePresetParameters = ParticlePresetParameters()): ParticleEffect =
        requireNotNull(byId[id]) { "Unknown particle preset: $id" }.create(parameters)
}

fun startParticlePreset(
    player: net.minestom.server.entity.Player,
    id: String,
    scheduler: ParticleAnimationScheduler,
    origin: Point = player.position,
    direction: Vec = player.position.direction(),
    manager: ParticleManager? = null,
    values: Map<String, Any> = emptyMap(),
): Boolean {
    val preset = ParticlePresetRegistry[id] ?: return false
    val parameters = ParticlePresetParameters.at(origin, direction).copy(values = values)
    val sink = manager?.sink(ParticleViewer(player.position, player), PlayerParticleSink(player), "preset:$id")
        ?: PlayerParticleSink(player)
    scheduler.start(preset.create(parameters), sink, id = id)
    return true
}

private fun number(name: String, default: Double, minimum: Double, maximum: Double) =
    ParticlePresetParameter(name, ParticlePresetParameterType.NUMBER, default, minimum, maximum)

private fun color(name: String, default: Int) = ParticlePresetParameter(name, ParticlePresetParameterType.COLOR, default)
private fun bool(name: String, default: Boolean) = ParticlePresetParameter(name, ParticlePresetParameterType.BOOLEAN, default)
private fun choice(name: String, default: String, values: List<String>) =
    ParticlePresetParameter(name, ParticlePresetParameterType.ENUM, default, values = values)

private fun duration(default: Double = 6.0) = number("duration", default, 1.0, 40.0)
private fun scale() = number("scale", 1.0, 0.1, 4.0)
private fun density(default: Double = 1.0) = number("density", default, 0.0, 4.0)
private fun seed() = number("seed", 0.0, -9.0e15, 9.0e15)

private fun preset(
    id: String,
    displayName: String,
    tags: Set<String>,
    parameters: List<ParticlePresetParameter> = emptyList(),
    factory: (ParticlePresetParameters) -> ParticleEffect,
) = ParticlePreset(id, displayName, tags, parameters, factory)

private fun style(parameters: ParticlePresetParameters, primary: Boolean = true, particle: Particle = Particle.END_ROD, count: Int = 1): ParticleStyle {
    val colorValue = parameters.color(if (primary) "colorPrimary" else "colorSecondary", if (primary) 0xffee55 else 0xff5522)
    return ParticleStyle(
        particle = if (particle == Particle.DUST) dust(colorValue, parameters.scale("scale")) else particle,
        count = count,
        densityMultiplier = parameters.number("density", 1.0),
    )
}

private fun ParticlePresetParameters.scale(name: String): Float = number(name, 1.0).toFloat().coerceIn(0.05f, 4.0f)
private fun ParticlePresetParameters.ticks(): Int = number("duration", 6.0).roundToInt().coerceIn(1, 40)
private fun ParticlePresetParameters.radius(default: Double = 1.0): Double = number("radius", default).coerceIn(0.0, 8.0) * number("scale", 1.0).coerceIn(0.1, 4.0)
private fun ParticlePresetParameters.length(default: Double = 3.0): Double = number("length", default).coerceIn(0.0, 12.0) * number("scale", 1.0).coerceIn(0.1, 4.0)
private fun ParticlePresetParameters.seedValue(): Long = number("seed", 0.0).toLong()

private fun torus(origin: Point, normal: Vec, majorRadius: Double, minorRadius: Double, style: ParticleStyle, samples: Int = 64): ParticleEffect {
    val (_, right, up) = basis(normal)
    return ParticleParametric(
        positionAt = { t ->
            val outer = t * PI * 2.0
            val inner = t * PI * 2.0 * 6.0
            val radial = majorRadius + minorRadius * cos(inner)
            val point = right.mul(cos(outer) * radial).add(up.mul(sin(outer) * radial)).add(normalize(normal).mul(minorRadius * sin(inner)))
            origin.add(point.x(), point.y(), point.z())
        },
        sampleCount = samples,
        styleAt = { style },
    )
}

private fun wave(origin: Point, direction: Vec, length: Double, amplitude: Double, style: ParticleStyle, samples: Int = 32): ParticleEffect {
    val (forward, right, _) = basis(direction)
    return ParticleParametric(
        positionAt = { t ->
            val point = forward.mul((t - 0.5) * length).add(right.mul(sin(t * PI * 3.0) * amplitude))
            origin.add(point.x(), point.y(), point.z())
        },
        sampleCount = samples,
        styleAt = { style },
    )
}

private fun pulseRing(origin: Point, direction: Vec, radius: Double, durationTicks: Int, style: ParticleStyle): ParticleEffect = object : ParticleEffect {
    override val durationTicks: Int = durationTicks.coerceAtLeast(1)
    override fun emit(tick: Int, sink: ParticleSink) {
        val progress = (tick + 1).toDouble() / this.durationTicks
        ParticleCircle(origin, radius * progress, axis1 = basis(direction).second, axis2 = basis(direction).third, countPerMeter = 8.0, includeEnd = false, style = style).emit(0, sink)
    }
}

private fun shrinkingRing(origin: Point, direction: Vec, radius: Double, durationTicks: Int, style: ParticleStyle): ParticleEffect = object : ParticleEffect {
    override val durationTicks: Int = durationTicks.coerceAtLeast(1)
    override fun emit(tick: Int, sink: ParticleSink) {
        val progress = 1.0 - tick.toDouble() / this.durationTicks
        ParticleCircle(origin, radius * progress.coerceAtLeast(0.0), axis1 = basis(direction).second, axis2 = basis(direction).third, countPerMeter = 8.0, includeEnd = false, style = style).emit(0, sink)
    }
}

private fun buildCatalogue(): List<ParticlePreset> {
    val geometry = setOf("geometry", "basic")
    val motion = setOf("motion", "ambient")
    val combat = setOf("combat")
    val common = listOf(scale(), density(), duration(), seed())
    val colors = listOf(color("colorPrimary", 0xffee55), color("colorSecondary", 0xff5522))
    return listOf(
        preset("projects:geometry/circle", "Circle", geometry, listOf(number("radius", 1.5, 0.0, 8.0)) + common) { p -> ParticleCircle(p.origin, p.radius(1.5), axis1 = basis(p.direction).second, axis2 = basis(p.direction).third, countPerMeter = 10.0, style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/arc", "Arc", geometry, listOf(number("radius", 1.6, 0.0, 8.0), number("angle", 140.0, -360.0, 360.0)) + common) { p -> ParticleCircle(p.origin, p.radius(1.6), axis1 = basis(p.direction).second, axis2 = basis(p.direction).third, startDegrees = -p.number("angle", 140.0) / 2.0, endDegrees = p.number("angle", 140.0) / 2.0, countPerMeter = 10.0, style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/sphere", "Sphere", geometry, listOf(number("radius", 1.4, 0.0, 8.0), number("count", 72.0, 1.0, 512.0)) + common) { p -> ParticleUtils.sphere(p.origin, p.radius(1.4), p.number("count", 72.0).roundToInt(), style(p, particle = Particle.DUST), p.seedValue()) },
        preset("projects:geometry/dome", "Dome", geometry, listOf(number("radius", 1.4, 0.0, 8.0), number("count", 48.0, 1.0, 512.0)) + common) { p -> ParticleUtils.dome(p.origin, p.radius(1.4), p.number("count", 48.0).roundToInt(), p.direction, style(p, particle = Particle.DUST), p.seedValue()) },
        preset("projects:geometry/cube", "Cube", geometry, listOf(number("width", 2.0, 0.0, 8.0)) + common) { p -> ParticleRectPrism(p.origin, Vec(p.number("width", 2.0), p.number("width", 2.0), p.number("width", 2.0)), RectPrismMode.EDGE, countPerMeter = 6.0, style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/cuboid", "Cuboid", geometry, listOf(number("width", 2.5, 0.0, 8.0), number("height", 1.8, 0.0, 8.0), number("depth", 1.2, 0.0, 8.0)) + common) { p -> ParticleRectPrism(p.origin, Vec(p.number("width", 2.5), p.number("height", 1.8), p.number("depth", 1.2)), RectPrismMode.EDGES, countPerMeter = 6.0, style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/cone", "Cone", geometry, listOf(number("length", 3.0, 0.0, 12.0), number("angle", 20.0, 0.0, 80.0)) + common) { p -> ParticleUtils.explodingCone(p.origin, p.direction, p.number("angle", 20.0), p.length(), count = 72, durationTicks = p.ticks(), style = style(p, particle = Particle.DUST), seed = p.seedValue()) },
        preset("projects:geometry/cylinder", "Cylinder Pillar", geometry, listOf(number("radius", 0.8, 0.0, 8.0), number("height", 2.5, 0.0, 12.0)) + common) { p -> ParticlePillar(p.origin, p.number("height", 2.5), p.radius(0.8), count = 96, axis = p.direction, seed = p.seedValue(), style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/torus", "Torus", geometry, listOf(number("radius", 1.2, 0.0, 8.0), number("width", 0.25, 0.0, 2.0)) + common) { p -> torus(p.origin, p.direction, p.radius(1.2), p.number("width", 0.25), style(p, particle = Particle.DUST)) },
        preset("projects:geometry/pyramid", "Pyramid", geometry, listOf(number("width", 2.0, 0.0, 8.0), number("height", 2.5, 0.0, 12.0)) + common) { p ->
            val half = p.number("width", 2.0) / 2.0
            val top = p.origin.add(0.0, p.number("height", 2.5), 0.0)
            val corners = listOf(p.origin.add(-half, 0.0, -half), p.origin.add(half, 0.0, -half), p.origin.add(half, 0.0, half), p.origin.add(-half, 0.0, half))
            ParticleBatch.of(*corners.map { ParticleLine(it, top, countPerMeter = 7.0, style = style(p, particle = Particle.DUST)) }.toTypedArray(), ParticleRectPrism(p.origin, Vec(p.number("width", 2.0), 0.0, p.number("width", 2.0)), RectPrismMode.EDGE, countPerMeter = 7.0, style = style(p, particle = Particle.DUST)))
        },
        preset("projects:geometry/star", "Star", geometry, listOf(number("radius", 1.5, 0.0, 8.0), number("points", 5.0, 3.0, 12.0)) + common) { p -> ParticleFlower(p.origin, p.number("points", 5.0).roundToInt(), p.radius(1.5), sharp = true, planeNormal = p.direction, count = 96, style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/helix", "Helix", geometry, listOf(number("length", 3.0, 0.0, 12.0), number("turns", 2.0, 0.25, 8.0)) + common) { p -> ParticleSpiral(p.origin, p.direction, p.radius(0.7), PI * 2.0, p.number("turns", 2.0), durationTicks = p.ticks(), style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/double_helix", "Double Helix", geometry, listOf(number("length", 3.0, 0.0, 12.0), number("turns", 2.0, 0.25, 8.0)) + common) { p -> ParticleBatch.of(ParticleSpiral(p.origin, p.direction, p.radius(0.65), PI * 2.0, p.number("turns", 2.0), angleOffset = 0.0, durationTicks = p.ticks(), style = style(p, particle = Particle.DUST)), ParticleSpiral(p.origin, p.direction, p.radius(0.65), PI * 2.0, p.number("turns", 2.0), angleOffset = PI, durationTicks = p.ticks(), style = style(p, primary = false, particle = Particle.DUST))) },

        preset("projects:motion/spiral_out", "Spiral Out", motion, listOf(number("radius", 2.0, 0.0, 8.0)) + common) { p -> ParticleSpiral(p.origin, p.direction, p.radius(2.0), PI * 2.0, 2.0, durationTicks = p.ticks(), style = style(p, particle = Particle.END_ROD)) },
        preset("projects:motion/spiral_in", "Spiral In", motion, listOf(number("radius", 2.0, 0.0, 8.0)) + common) { p -> ParticleSpiral(p.origin, p.direction, p.radius(2.0), PI * 2.0, 2.0, reversed = true, durationTicks = p.ticks(), style = style(p, particle = Particle.END_ROD)) },
        preset("projects:motion/vortex", "Vortex", motion, listOf(number("radius", 2.0, 0.0, 8.0)) + common) { p -> ParticleBatch.of(ParticleSpiral(p.origin, p.direction, p.radius(2.0), PI * 3.0, 3.0, durationTicks = p.ticks(), style = style(p, particle = Particle.PORTAL)), ParticleSpiral(p.origin, p.direction, p.radius(1.0), PI * 3.0, 3.0, angleOffset = PI, durationTicks = p.ticks(), style = style(p, primary = false, particle = Particle.END_ROD))) },
        preset("projects:motion/tornado", "Tornado", motion, listOf(number("height", 3.5, 0.0, 12.0), number("radius", 1.8, 0.0, 8.0)) + common) { p -> ParticleParametric({ t -> val r = p.radius(1.8) * (1.0 - t * 0.75); p.origin.add(r * cos(t * PI * 6.0), p.number("height", 3.5) * t, r * sin(t * PI * 6.0)) }, particle = Particle.END_ROD, sampleCount = 72, durationTicks = p.ticks(), styleAt = { style(p, particle = Particle.DUST) }) },
        preset("projects:motion/fountain", "Fountain", motion, listOf(number("height", 3.0, 0.0, 12.0), number("radius", 1.5, 0.0, 8.0)) + common) { p -> ParticleUtils.explodingCone(p.origin, p.direction.add(0.0, 0.8, 0.0), 28.0, p.number("height", 3.0), count = 80, durationTicks = p.ticks(), style = style(p, particle = Particle.FIREWORK), seed = p.seedValue()) },
        preset("projects:motion/wave", "Wave", motion, listOf(number("length", 4.0, 0.0, 12.0), number("width", 0.8, 0.0, 4.0)) + common) { p -> wave(p.origin, p.direction, p.length(4.0), p.number("width", 0.8), style(p, particle = Particle.DUST)) },
        preset("projects:motion/orbit", "Orbit", motion, listOf(number("radius", 1.4, 0.0, 8.0)) + common) { p -> ParticleBatch.of(ParticleCircle(p.origin, p.radius(1.4), axis1 = basis(p.direction).second, axis2 = basis(p.direction).third, countPerMeter = 10.0, style = style(p, particle = Particle.END_ROD)), ParticleCircle(p.origin.add(0.0, 0.5, 0.0), p.radius(1.0), axis1 = basis(p.direction).second, axis2 = basis(p.direction).third, countPerMeter = 10.0, style = style(p, primary = false, particle = Particle.ENCHANT))) },
        preset("projects:motion/aura_pulse", "Aura Pulse", motion, listOf(number("radius", 1.5, 0.0, 8.0)) + common) { p -> ParticleBatch.of(pulseRing(p.origin, p.direction, p.radius(1.5), p.ticks(), style(p, particle = Particle.DUST)), ParticleExplosion(p.origin, p.radius(1.5) * 0.3, sphere = true, particle = Particle.GLOW, count = 12, speed = 0.05f, seed = p.seedValue())) },

        preset("projects:combat/slash_light", "Slash Light", combat, listOf(number("length", 2.2, 0.0, 8.0), number("angle", 35.0, -180.0, 180.0)) + colors + common) { p ->
            val slash = ParticleGeometry.drawParticleLineSlash(p.origin, p.direction, p.number("angle", 35.0), p.length(2.2), 0.12, p.ticks()) { _, middle, _, middleSample -> style(p, primary = middleSample, particle = Particle.DUST, count = if (middleSample) 2 else 1).copy(particle = dust(if (middleSample) p.color("colorPrimary", 0xffff66) else p.color("colorSecondary", 0xff5522), (middle * 0.35 + 0.25).toFloat())) }
            ParticleBatch.of(slash, ParticleGeometry.drawCleaveArc(p.origin, p.direction, p.radius(0.8), 0.0, -55.0, 55.0, 1, degreesPerTick = 110.0 / p.ticks()) { _, _, progress -> ParticleStyle(dust(lerpColor(p.color("colorSecondary", 0xff5522), p.color("colorPrimary", 0xffff66), progress), 0.22f)) }, ParticleExplosion(p.origin, count = 4, speed = 0.1f, particle = Particle.ELECTRIC_SPARK, seed = p.seedValue()))
        },
        preset("projects:combat/slash_heavy", "Slash Heavy", combat, listOf(number("length", 3.0, 0.0, 10.0)) + colors + common) { p -> ParticleBatch.of(ParticleGeometry.drawParticleLineSlash(p.origin, p.direction, -28.0, p.length(3.0), 0.16, p.ticks()) { _, middle, _, _ -> ParticleStyle(dust(lerpColor(p.color("colorSecondary", 0xff5522), p.color("colorPrimary", 0xffffff), middle), 0.38f), if (middle > 0.6) 3 else 1) }, ParticleGeometry.drawCleaveArc(p.origin, p.direction, p.radius(1.0), 0.0, -75.0, 75.0, 2, degreesPerTick = 150.0 / p.ticks()), ParticleExplosion(p.origin, radius = 0.4, sphere = true, count = 8, particle = Particle.CRIT, speed = 0.12f, seed = p.seedValue())) },
        preset("projects:combat/slash_x", "Slash X", combat, listOf(number("length", 2.2, 0.0, 8.0)) + colors + common) { p -> ParticleBatch.of(ParticleGeometry.drawParticleLineSlash(p.origin, p.direction, 35.0, p.length(2.2), 0.12, p.ticks()) { _, middle, _, _ -> ParticleStyle(dust(lerpColor(p.color("colorSecondary", 0xff5522), p.color("colorPrimary", 0xffff66), middle), 0.3f)) }, ParticleGeometry.drawParticleLineSlash(p.origin, p.direction, -35.0, p.length(2.2), 0.12, p.ticks()) { _, middle, _, _ -> ParticleStyle(dust(lerpColor(p.color("colorSecondary", 0xff5522), p.color("colorPrimary", 0xffffff), middle), 0.3f)) }, ParticleExplosion(p.origin, count = 6, particle = Particle.ELECTRIC_SPARK, speed = 0.1f, seed = p.seedValue())) },
        preset("projects:combat/cleave_arc", "Cleave Arc", combat, listOf(number("radius", 1.6, 0.0, 8.0), number("angle", 150.0, 1.0, 360.0)) + colors + common) { p -> ParticleBatch.of(ParticleGeometry.drawCleaveArc(p.origin, p.direction, p.radius(1.6), 12.0, -p.number("angle", 150.0) / 2.0, p.number("angle", 150.0) / 2.0, 2, degreesPerTick = p.number("angle", 150.0) / p.ticks()), ParticleGeometry.drawCleaveArc(p.origin, p.direction, p.radius(1.6) * 0.86, 12.0, -p.number("angle", 150.0) / 2.0, p.number("angle", 150.0) / 2.0, 1, degreesPerTick = p.number("angle", 150.0) / p.ticks()) { _, _, progress -> ParticleStyle(dust(lerpColor(p.color("colorSecondary", 0xff5522), p.color("colorPrimary", 0xffff66), progress), 0.3f)) }) },
        preset("projects:combat/thrust_line", "Thrust Line", combat, listOf(number("length", 4.0, 0.0, 12.0)) + colors + common) { p ->
            val end = p.origin.add(normalize(p.direction).mul(p.length(4.0)))
            ParticleBatch.of(ParticleLine(p.origin, end, countPerMeter = 10.0, durationTicks = p.ticks(), style = style(p, primary = true, particle = Particle.DUST, count = 2)), ParticleExplosion(end, count = 4, particle = Particle.ELECTRIC_SPARK, speed = 0.1f, seed = p.seedValue()))
        },
        preset("projects:combat/hit_impact", "Hit Impact", combat, listOf(number("radius", 0.9, 0.0, 5.0)) + colors + common) { p -> ParticleBatch.of(ParticleExplosion(p.origin, p.radius(0.9), sphere = true, particle = Particle.CRIT, count = 18, speed = 0.25f, seed = p.seedValue()), ParticleCircle(p.origin, p.radius(0.9), countPerMeter = 12.0, style = style(p, particle = Particle.DUST)), ParticleExplosion(p.origin, count = 4, particle = Particle.ELECTRIC_SPARK, speed = 0.12f, seed = p.seedValue() + 101)) },
        preset("projects:combat/critical_burst", "Critical Burst", combat, listOf(number("radius", 1.3, 0.0, 6.0)) + colors + common) { p -> ParticleBatch.of(ParticleExplosion(p.origin, p.radius(1.3), sphere = true, particle = Particle.END_ROD, count = 26, speed = 0.3f, seed = p.seedValue()), ParticleFlower(p.origin, 8, p.radius(1.3), sharp = true, planeNormal = p.direction, count = 64, style = style(p, particle = Particle.DUST)), pulseRing(p.origin, p.direction, p.radius(1.3), p.ticks(), style(p, primary = false, particle = Particle.CRIT))) },
        preset("projects:combat/shockwave_ring", "Shockwave Ring", combat, listOf(number("radius", 4.0, 0.0, 12.0)) + colors + common) { p -> ParticleBatch.of(pulseRing(p.origin, p.direction, p.radius(4.0), p.ticks(), style(p, particle = Particle.DUST, count = 2)), ParticleCircle(p.origin, p.radius(4.0) * 0.75, countPerMeter = 8.0, style = style(p, primary = false, particle = Particle.ELECTRIC_SPARK))) },
        preset("projects:combat/charge_inward", "Charge Inward", combat, listOf(number("radius", 3.5, 0.0, 12.0)) + colors + common) { p -> ParticleBatch.of(shrinkingRing(p.origin, p.direction, p.radius(3.5), p.ticks(), style(p, particle = Particle.DUST)), ParticleSpiral(p.origin, p.direction, p.radius(2.5), PI * 2.0, 1.5, reversed = true, durationTicks = p.ticks(), style = style(p, primary = false, particle = Particle.END_ROD))) },
        preset("projects:combat/projectile_trail", "Projectile Trail", combat, listOf(number("length", 2.5, 0.0, 10.0)) + colors + common) { p -> ParticleBatch.of(ParticleLine(p.origin, p.origin.add(normalize(p.direction).mul(p.length(2.5))), countPerMeter = 8.0, durationTicks = p.ticks(), style = style(p, particle = Particle.END_ROD)), ParticleSpiral(p.origin, p.direction, 0.28, PI * 2.0, 1.0, durationTicks = p.ticks(), style = style(p, primary = false, particle = Particle.ELECTRIC_SPARK))) },
        preset("projects:combat/projectile_impact", "Projectile Impact", combat, listOf(number("radius", 0.9, 0.0, 5.0)) + colors + common) { p -> ParticleBatch.of(ParticleExplosion(p.origin, p.radius(0.9), sphere = true, particle = Particle.EXPLOSION, count = 8, speed = 0.1f, seed = p.seedValue()), pulseRing(p.origin, p.direction, p.radius(1.5), p.ticks(), style(p, particle = Particle.END_ROD)), ParticleExplosion(p.origin, count = 12, particle = Particle.ELECTRIC_SPARK, speed = 0.2f, seed = p.seedValue() + 3)) },
        preset("projects:combat/lightning_strike", "Lightning Strike", combat, listOf(number("length", 4.0, 0.0, 12.0)) + colors + common) { p -> ParticleBatch.of(ParticleLightning.fromDirection(p.origin, p.direction, p.length(4.0), hops = 8, hopVariance = 0.35, density = 2, seed = p.seedValue(), durationTicks = p.ticks(), style = style(p, particle = Particle.ELECTRIC_SPARK)), ParticleExplosion(p.origin, radius = 0.5, sphere = true, particle = Particle.CLOUD, count = 12, speed = 0.08f, seed = p.seedValue() + 8)) },
        preset("projects:combat/magic_tendril", "Magic Tendril", combat, listOf(number("length", 3.0, 0.0, 10.0), number("width", 0.8, 0.0, 4.0)) + colors + common) { p -> ParticleBatch.of(ParticleUtils.tendril(p.origin, p.direction, p.length(3.0), p.number("width", 0.8), 2.0, style = style(p, particle = Particle.ENCHANT)), ParticleUtils.tendril(p.origin, p.direction, p.length(3.0), p.number("width", 0.8) * 0.7, 2.0, style = style(p, primary = false, particle = Particle.END_ROD))) },
        preset("projects:combat/homing_orb_trail", "Homing Orb Trail", combat, listOf(number("length", 3.0, 0.0, 10.0)) + colors + common) { p -> ParticleBatch.of(ParticleUtils.homingOrbTrajectory(p.origin, p.origin.add(normalize(p.direction).mul(p.length(3.0))), durationTicks = p.ticks(), style = style(p, particle = Particle.END_ROD)), ParticleCircle(p.origin, 0.35, axis1 = basis(p.direction).second, axis2 = basis(p.direction).third, countPerMeter = 12.0, style = style(p, primary = false, particle = Particle.GLOW))) },
        preset("projects:combat/landing_burst", "Landing Burst", combat, listOf(number("radius", 3.5, 0.0, 12.0)) + colors + common) { p -> ParticleBatch.of(ParticleExplosion(p.origin, p.radius(1.0), sphere = true, particle = Particle.EXPLOSION, count = 12, speed = 0.1f, seed = p.seedValue()), pulseRing(p.origin, p.direction, p.radius(3.5), p.ticks(), style(p, particle = Particle.DUST, count = 2)), ParticlePillar(p.origin, 1.2, p.radius(3.5), count = 32, axis = p.direction, seed = p.seedValue() + 1, style = style(p, primary = false, particle = Particle.ELECTRIC_SPARK))) },
    )
}
