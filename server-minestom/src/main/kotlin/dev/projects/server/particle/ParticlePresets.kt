package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.ceil
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

internal fun twinBladesArcPoint(
    origin: Point,
    direction: Vec,
    angleDegrees: Double,
    length: Double,
    progress: Double,
    radiusFactor: Double = 0.46,
): Point {
    val transform = ParticleTransform.fromDirection(origin, direction)
    val side = if (angleDegrees >= 0.0) 1.0 else -1.0
    val theta = Math.toRadians(-side * 48.0 + side * 96.0 * progress.coerceIn(0.0, 1.0))
    val radius = length.coerceAtLeast(0.01) * radiusFactor.coerceAtLeast(0.01)
    val localLateral = side * 0.28 + sin(theta) * radius
    val localVertical = -0.28 + (1.0 - cos(theta)) * radius * 0.55
    val localDepth = (1.0 - cos(theta)) * radius * 0.22
    val orientation = Math.toRadians(angleDegrees)
    val lateral = localLateral * cos(orientation) - localVertical * sin(orientation)
    val vertical = localLateral * sin(orientation) + localVertical * cos(orientation)
    return transform.localPoint(Vec(lateral, vertical, localDepth))
}

private fun twinBladesArcRibbon(
    parameters: ParticlePresetParameters,
    angleDegrees: Double,
    length: Double,
    width: Double,
    start: Double,
    end: Double,
    color: Int,
    dustScale: Float,
    swingGeometry: Boolean,
    durationTicks: Int = 1,
): ParticleEffect = ParticleRibbon(
    path = { progress ->
        twinBladesArcPoint(
            parameters.origin,
            parameters.direction,
            angleDegrees,
            length,
            start + (end - start) * progress,
            radiusFactor = if (swingGeometry) 0.55 else 0.46,
        )
    },
    sampleCount = 12,
    lanes = 3,
    width = KeyframeCurve.double(
        CurveKeyframe(0.0, width.coerceAtLeast(0.0) * 0.45),
        CurveKeyframe(0.5, width.coerceAtLeast(0.0)),
        CurveKeyframe(1.0, width.coerceAtLeast(0.0) * 0.45),
        easing = Easing.SINE,
    ),
    styleAt = { _, laneProgress ->
        ParticleStyle(dust(lerpColor(color, 0xe8fdff, if (laneProgress == 0.5) 0.35 else 0.0), dustScale), count = if (laneProgress == 0.5) 2 else 1)
    },
    durationTicks = durationTicks,
)

private fun twinBladesCrescent(
    parameters: ParticlePresetParameters,
    angleDegrees: Double,
    length: Double,
    durationTicks: Int,
    swingGeometry: Boolean,
): ParticleEffect = object : ParticleEffect {
    override val durationTicks: Int = durationTicks.coerceAtLeast(1)

    override fun emit(tick: Int, sink: ParticleSink) {
        val progress = if (this.durationTicks == 1) 1.0 else tick.toDouble() / (this.durationTicks - 1)
        val leadingStart = (progress - 0.22).coerceAtLeast(0.0)
        val bodyStart = (progress - 0.48).coerceAtLeast(0.0)
        val bodyColor = if (swingGeometry) {
            lerpColor(parameters.color("colorSecondary", 0x071525), 0x1259d8, 0.42)
        } else {
            parameters.color("colorSecondary", 0x126bff)
        }
        val body = ParticleParallel.of(
            twinBladesArcRibbon(parameters, angleDegrees, length, 0.18, bodyStart, progress, bodyColor, 0.38f, swingGeometry),
            twinBladesArcRibbon(parameters, angleDegrees, length, 0.12, leadingStart, progress, parameters.color("colorPrimary", 0x70e9ff), 0.24f, swingGeometry),
            twinBladesArcRibbon(parameters, angleDegrees, length, 0.07, progress, minOf(1.0, progress + 0.08), 0xe8fdff, 0.20f, swingGeometry),
        )
        if (swingGeometry) {
            ParticleParallel.of(
                twinBladesArcRibbon(parameters, angleDegrees, length, 0.14, bodyStart, progress, 0x050a14, 0.20f, true),
                body,
            ).emit(0, sink)
        } else {
            body.emit(0, sink)
        }
    }
}

private fun twinBladesReverseHook(
    parameters: ParticlePresetParameters,
    angleDegrees: Double,
    length: Double,
    durationTicks: Int,
    swingGeometry: Boolean,
): ParticleEffect {
    val transform = ParticleTransform.fromDirection(parameters.origin, parameters.direction)
    val side = if (angleDegrees >= 0.0) 1.0 else -1.0
    val scale = length / 2.1
    fun local(x: Double, y: Double, z: Double): Point = transform.localPoint(Vec(side * x * scale, y * scale, z * scale))

    val main = ParticleBezier(
        start = local(0.18, -0.28, 0.0),
        end = twinBladesReverseHookEndpoint(parameters.origin, parameters.direction, angleDegrees, length),
        controlPoints = if (swingGeometry) {
            listOf(local(0.78, 0.52, 0.18), local(-0.86, 0.82, 0.42))
        } else {
            listOf(local(0.62, 0.42, 0.16), local(-0.72, 0.7, 0.38))
        },
        sampleCount = 24,
        durationTicks = durationTicks,
        styleAt = { progress ->
            ParticleStyle(dust(lerpColor(parameters.color("colorSecondary", 0x126bff), parameters.color("colorPrimary", 0x70e9ff), progress), 0.36f))
        },
    )
    val echo = ParticleBezier(
        start = local(-0.12, -0.04, 0.48),
        end = local(-0.52, 0.28, 0.7),
        controlPoints = listOf(local(-0.42, 0.18, 0.58)),
        sampleCount = 10,
        durationTicks = (durationTicks - 1).coerceAtLeast(1),
        styleAt = { ParticleStyle(dust(parameters.color("colorSecondary", 0x126bff), 0.22f)) },
    )
    return ParticleParallel.of(main, ParticleSequence.of(ParticleDelay(1), echo))
}

internal fun twinBladesReverseHookEndpoint(
    origin: Point,
    direction: Vec,
    angleDegrees: Double,
    length: Double,
): Point {
    val transform = ParticleTransform.fromDirection(origin, direction)
    val side = if (angleDegrees >= 0.0) 1.0 else -1.0
    val scale = length / 2.1
    return transform.localPoint(Vec(side * -0.22 * scale, -0.04 * scale, 0.62 * scale))
}

private fun twinBladesDelayedBurst(
    point: Point,
    delayTicks: Int,
    particle: Particle,
    count: Int,
    speed: Float,
    seed: Long,
): ParticleEffect = ParticleSequence.of(
    ParticleDelay(delayTicks.coerceAtLeast(0)),
    ParticleExplosion(point, count = count, speed = speed, particle = particle, seed = seed),
)

private fun twinBladesFinisherAccent(
    parameters: ParticlePresetParameters,
    angleDegrees: Double,
    length: Double,
    durationTicks: Int,
    swingGeometry: Boolean,
): ParticleEffect {
    val center = twinBladesArcPoint(
        parameters.origin,
        parameters.direction,
        angleDegrees,
        length,
        0.5,
        radiusFactor = if (swingGeometry) 0.55 else 0.46,
    )
    return ParticleSequence.of(
        ParticleDelay((durationTicks - 1).coerceAtLeast(0)),
        if (swingGeometry) {
            ParticleParallel.of(
                ParticleFlower(
                    center = center,
                    petals = 4,
                    radius = 0.24,
                    sharp = true,
                    planeNormal = parameters.direction,
                    count = 16,
                    style = ParticleStyle(dust(if (swingGeometry) 0x46dfff else parameters.color("colorPrimary", 0xe8fdff), 0.2f), importance = ParticleImportance.COMBAT_FEEDBACK),
                ),
                ParticleExplosion(
                    center,
                    radius = 0.14,
                    count = 4,
                    speed = 0.03f,
                    particle = Particle.ELECTRIC_SPARK,
                    seed = parameters.seedValue() + 31,
                ),
            )
        } else {
            ParticleFlower(
                center = center,
                petals = 4,
                radius = 0.24,
                sharp = true,
                planeNormal = parameters.direction,
                count = 20,
                style = ParticleStyle(dust(parameters.color("colorPrimary", 0xe8fdff), 0.2f), importance = ParticleImportance.COMBAT_FEEDBACK),
            )
        },
    )
}

private fun twinBladesScissor(
    parameters: ParticlePresetParameters,
    angleDegrees: Double,
    length: Double,
    durationTicks: Int,
    swingGeometry: Boolean,
): ParticleEffect = ParticleParallel.of(
    twinBladesCrescent(parameters, angleDegrees, length, durationTicks, swingGeometry),
    ParticleSequence.of(
        ParticleDelay(1),
        twinBladesCrescent(parameters, -angleDegrees, length * 0.9, (durationTicks - 1).coerceAtLeast(1), swingGeometry),
    ),
)

private fun twinBladesStepEffect(
    parameters: ParticlePresetParameters,
    angleDegrees: Double,
    length: Double,
    durationTicks: Int,
    step: Int,
    swingGeometry: Boolean = false,
): ParticleEffect = when (step.coerceIn(1, 3)) {
    1 -> ParticleParallel.of(
        twinBladesCrescent(parameters, angleDegrees, length, durationTicks, swingGeometry),
        twinBladesDelayedBurst(
            twinBladesArcPoint(parameters.origin, parameters.direction, angleDegrees, length, 1.0, if (swingGeometry) 0.55 else 0.46),
            durationTicks - 1,
            if (swingGeometry) Particle.ELECTRIC_SPARK else Particle.ENCHANT,
            count = 3,
            speed = 0.03f,
            seed = parameters.seedValue(),
        ),
    )
    2 -> ParticleParallel.of(
        twinBladesReverseHook(parameters, angleDegrees, length, durationTicks, swingGeometry),
        twinBladesDelayedBurst(
            twinBladesReverseHookEndpoint(parameters.origin, parameters.direction, angleDegrees, length),
            durationTicks - 1,
            Particle.END_ROD,
            count = 3,
            speed = 0.03f,
            seed = parameters.seedValue() + 17,
        ),
    )
    else -> ParticleParallel.of(
        twinBladesScissor(parameters, angleDegrees, length, durationTicks, swingGeometry),
        twinBladesFinisherAccent(parameters, angleDegrees, length, durationTicks, swingGeometry),
    )
}

private fun twinBladesContactEmitter(parameters: ParticlePresetParameters, count: Int, radius: Double): ParticleEffect =
    ParticleEmitter(
        anchor = ParticleAnchor.fixed(parameters.origin, parameters.direction),
        particle = Particle.ELECTRIC_SPARK,
        rate = EmitterRate.BURST,
        shape = SpawnShape.DISC,
        durationTicks = 1,
        particlesPerTick = count,
        burstCount = count,
        radius = radius,
        seed = parameters.seedValue(),
        styleCurve = ParticleStyleCurve(ParticleStyle(Particle.ELECTRIC_SPARK, importance = ParticleImportance.COMBAT_FEEDBACK)),
    )

private fun skill1TrailRibbon(
    parameters: ParticlePresetParameters,
    length: Double,
    side: Double,
    color: Int,
    width: Double,
): ParticleEffect {
    val transform = ParticleTransform.fromDirection(parameters.origin, parameters.direction)
    return ParticleRibbon(
        path = { progress ->
            val radius = 0.16 * (1.0 - progress * 0.45)
            val angle = progress * PI * 2.2 + if (side > 0.0) 0.0 else PI
            val lateral = side * 0.18 + cos(angle) * radius
            val vertical = sin(angle) * radius
            transform.localPoint(Vec(lateral, vertical, -length * progress))
        },
        sampleCount = 12,
        lanes = 2,
        width = constantCurve(width),
        styleAt = { progress, laneProgress ->
            ParticleStyle(
                dust(lerpColor(color, 0xe8fdff, (1.0 - progress) * if (laneProgress == 0.5) 0.30 else 0.0), 0.25f),
                count = if (laneProgress == 0.5) 2 else 1,
                importance = ParticleImportance.COMBAT_FEEDBACK,
            )
        },
    )
}

private fun skill1TravelOrbit(parameters: ParticlePresetParameters, length: Double, durationTicks: Int): ParticleEffect =
    ParticleParallel.of(
        ParticleSpiral(
            origin = parameters.origin,
            axis = parameters.direction,
            radius = 0.28,
            curveAngle = PI * 2.0,
            curves = 1.15,
            axialLength = length * 0.8,
            durationTicks = durationTicks,
            style = ParticleStyle(dust(0x126bff, 0.18f), importance = ParticleImportance.COMBAT_FEEDBACK),
        ),
        ParticleExplosion(
            parameters.origin,
            radius = 0.32,
            sphere = true,
            particle = Particle.END_ROD,
            count = 3,
            speed = 0.025f,
            seed = parameters.seedValue() + 7,
        ),
    )

private fun skill1StompShards(parameters: ParticlePresetParameters, durationTicks: Int): ParticleEffect {
    val transform = ParticleTransform.fromDirection(parameters.origin, parameters.direction)
    val shards = (-3..3).map { index ->
        val side = index * 0.25
        val vertical = (index % 2) * 0.1 - 0.05
        ParticleLine(
            start = transform.localPoint(Vec(side, vertical, 0.05)),
            end = transform.localPoint(Vec(side * 1.55, vertical + index * 0.08, -1.08)),
            countPerMeter = 9.0,
            durationTicks = durationTicks,
            style = ParticleStyle(
                dust(lerpColor(0x071525, 0x168cff, (index + 2) / 4.0), 0.28f),
                importance = ParticleImportance.COMBAT_FEEDBACK,
            ),
        )
    }
    return ParticleBatch.of(*shards.toTypedArray())
}

private fun skill1StompEffect(parameters: ParticlePresetParameters): ParticleEffect {
    val radius = parameters.radius(0.82) * 1.28
    val duration = parameters.ticks().coerceIn(4, 6)
    val compressionTicks = 2
    val peakTicks = 1
    val burstTicks = (duration - compressionTicks - peakTicks).coerceAtLeast(1)
    val (_, right, up) = basis(parameters.direction)
    val shadow = ParticleStyle(
        dust(0x020813, 0.46f),
        count = 2,
        importance = ParticleImportance.COMBAT_FEEDBACK,
    )
    val body = ParticleStyle(
        dust(0x168cff, 0.34f),
        count = 2,
        importance = ParticleImportance.COMBAT_FEEDBACK,
    )
    val core = ParticleStyle(
        dust(0xe8fdff, 0.22f),
        count = 2,
        importance = ParticleImportance.COMBAT_FEEDBACK,
    )
    return ParticleSequence.of(
        ParticleParallel.of(
            shrinkingRing(parameters.origin, parameters.direction, radius * 0.82, compressionTicks, shadow),
            pulseRing(parameters.origin, parameters.direction, radius * 0.46, compressionTicks, body),
            ParticleCircle(
                parameters.origin,
                radius * 0.25,
                axis1 = right,
                axis2 = up,
                countPerMeter = 12.0,
                style = core,
                durationTicks = compressionTicks,
            ),
        ),
        ParticleParallel.of(
            ParticleCircle(
                parameters.origin,
                radius * 0.7,
                axis1 = right,
                axis2 = up,
                countPerMeter = 18.0,
                style = shadow,
            ),
            ParticleCircle(
                parameters.origin,
                radius * 0.32,
                axis1 = right,
                axis2 = up,
                countPerMeter = 20.0,
                style = core,
            ),
            ParticleExplosion(
                parameters.origin,
                radius = radius * 0.3,
                sphere = true,
                particle = Particle.ELECTRIC_SPARK,
                count = 6,
                speed = 0.04f,
                seed = parameters.seedValue() + 13,
            ),
        ),
        ParticleParallel.of(
            pulseRing(parameters.origin, parameters.direction, radius, burstTicks, body),
            ParticleCircle(
                parameters.origin,
                radius * 0.38,
                axis1 = right,
                axis2 = up,
                countPerMeter = 14.0,
                style = core,
            ),
            skill1StompShards(parameters, burstTicks),
            ParticleExplosion(
                parameters.origin,
                radius = radius * 0.45,
                sphere = false,
                particle = Particle.ELECTRIC_SPARK,
                count = 12,
                speed = 0.12f,
                seed = parameters.seedValue(),
            ),
        ),
    )
}

private fun skill1EscapeEffect(parameters: ParticlePresetParameters): ParticleEffect {
    val duration = parameters.ticks().coerceIn(3, 6)
    val length = parameters.length(1.7) * 1.3
    val backward = normalize(parameters.direction).mul(-length * 0.78)
    return ParticleParallel.of(
        skill1TrailRibbon(parameters, length, 1.0, 0x071525, 0.17),
        skill1TrailRibbon(parameters, length, -1.0, 0x126bff, 0.13),
        ParticleLine(
            start = parameters.origin,
            end = parameters.origin.add(backward.x(), backward.y(), backward.z()),
            countPerMeter = 13.0,
            durationTicks = duration,
            style = ParticleStyle(dust(0x70e9ff, 0.20f), importance = ParticleImportance.COMBAT_FEEDBACK),
        ),
        ParticleCircle(
            parameters.origin,
            radius = 0.26,
            axis1 = basis(parameters.direction).second,
            axis2 = basis(parameters.direction).third,
            countPerMeter = 14.0,
            style = ParticleStyle(dust(0x126bff, 0.18f), importance = ParticleImportance.COMBAT_FEEDBACK),
        ),
        ParticleExplosion(
            parameters.origin,
            radius = 0.22,
            count = 7,
            speed = 0.04f,
            particle = Particle.ELECTRIC_SPARK,
            seed = parameters.seedValue() + 41,
        ),
    )
}

private fun twinBladesSkill2StormField(
    parameters: ParticlePresetParameters,
    pulse: Int,
    durationTicks: Int,
): ParticleEffect {
    val (_, right, up) = basis(parameters.direction)
    val radius = 0.42 + pulse * 0.035
    return ParticleParallel.of(
        ParticleSpiral(
            origin = parameters.origin,
            axis = parameters.direction,
            radius = radius,
            curveAngle = PI * 2.0,
            curves = 1.25,
            axialLength = 0.7,
            angleOffset = pulse * PI / 2.0,
            durationTicks = durationTicks,
            style = ParticleStyle(dust(0x071525, 0.24f), count = 2),
        ),
        ParticleCircle(
            parameters.origin,
            radius = 0.8 + pulse * 0.08,
            axis1 = right,
            axis2 = up,
            startDegrees = pulse * 37.0,
            endDegrees = pulse * 37.0 + 250.0,
            countPerMeter = 7.0,
            durationTicks = durationTicks,
            style = ParticleStyle(dust(0x126bff, 0.16f), importance = ParticleImportance.COMBAT_FEEDBACK),
        ),
        ParticleExplosion(
            parameters.origin,
            radius = 0.65,
            sphere = true,
            particle = if (pulse >= 3) Particle.ELECTRIC_SPARK else Particle.END_ROD,
            count = 3 + pulse,
            speed = 0.025f,
            seed = parameters.seedValue() + pulse * 29L,
        ),
    )
}

private fun twinBladesConvergingStreaks(parameters: ParticlePresetParameters, durationTicks: Int): ParticleEffect {
    val (forward, right, up) = basis(parameters.direction)
    val streaks = listOf(-1.0, -0.55, 0.55, 1.0).mapIndexed { index, lateral ->
        val vertical = -0.35 - index * 0.12
        val start = parameters.origin.add(
            right.x() * lateral + up.x() * vertical + forward.x() * 0.65,
            right.y() * lateral + up.y() * vertical + forward.y() * 0.65,
            right.z() * lateral + up.z() * vertical + forward.z() * 0.65,
        )
        ParticleLine(
            start = start,
            end = parameters.origin.add(forward.x() * 0.12, forward.y() * 0.12, forward.z() * 0.12),
            countPerMeter = 10.0,
            durationTicks = durationTicks,
            style = ParticleStyle(dust(lerpColor(0x071525, 0x70e9ff, index / 3.0), 0.26f), importance = ParticleImportance.COMBAT_FEEDBACK),
        )
    }
    return ParticleBatch.of(*streaks.toTypedArray())
}

private fun twinBladesSkill2Pulse(parameters: ParticlePresetParameters): ParticleEffect {
    val pulse = parameters.number("pulse", 1.0).roundToInt().coerceIn(1, 4)
    val duration = parameters.ticks().coerceIn(1, 3)
    val length = when (pulse) {
        1 -> 4.0
        2 -> 4.25
        3 -> 4.65
        else -> 4.9
    }
    val primary = parameters.color("colorPrimary", 0x168cff)
    val secondary = parameters.color("colorSecondary", 0x071525)
    val spark = ParticleExplosion(
        parameters.origin,
        radius = 0.55,
        count = if (pulse >= 3) 8 else 4,
        speed = 0.08f,
        particle = Particle.ELECTRIC_SPARK,
        seed = parameters.seedValue() + pulse,
    )
    return when (pulse) {
        1 -> ParticleParallel.of(
            twinBladesSkill2StormField(parameters, pulse, duration),
            twinBladesCrescent(parameters, 42.0, length, duration, swingGeometry = false),
            ParticleGeometry.drawParticleLineSlash(
                parameters.origin,
                parameters.direction,
                42.0,
                length,
                0.14,
                duration,
            ) { _, middle, _, _ -> ParticleStyle(dust(lerpColor(secondary, primary, middle), 0.22f)) },
            spark,
        )
        2 -> ParticleParallel.of(
            twinBladesSkill2StormField(parameters, pulse, duration),
            twinBladesReverseHook(parameters, -48.0, length, duration, swingGeometry = false),
            ParticleSequence.of(ParticleDelay(1), twinBladesCrescent(parameters, -42.0, length * 0.88, 1, swingGeometry = false)),
            ParticleExplosion(parameters.origin, radius = 0.4, count = 5, speed = 0.04f, particle = Particle.ENCHANT, seed = parameters.seedValue() + 19),
            spark,
        )
        3 -> ParticleParallel.of(
            twinBladesSkill2StormField(parameters, pulse, duration),
            ParticleGeometry.drawCleaveArc(
                parameters.origin,
                parameters.direction,
                parameters.radius(2.75),
                0.0,
                -150.0,
                150.0,
                2,
                ringSpacing = 0.18,
                degreesPerTick = 300.0 / duration,
            ) { _, ring, progress ->
                ParticleStyle(dust(if (ring == 0) lerpColor(secondary, primary, progress) else 0x168cff, if (ring == 0) 0.36f else 0.24f))
            },
            ParticleCircle(
                parameters.origin,
                parameters.radius(2.15),
                axis1 = basis(parameters.direction).second,
                axis2 = basis(parameters.direction).third,
                countPerMeter = 9.0,
                style = ParticleStyle(dust(0xe8fdff, 0.16f)),
            ),
            spark,
        )
        else -> ParticleParallel.of(
            twinBladesSkill2StormField(parameters, pulse, duration),
            twinBladesReverseHook(parameters, -56.0, length, duration, swingGeometry = false),
            ParticleGeometry.drawParticleLineSlash(
                parameters.origin,
                parameters.direction,
                -68.0,
                length,
                0.12,
                duration,
            ) { _, middle, _, _ -> ParticleStyle(dust(lerpColor(secondary, 0xe8fdff, middle), 0.3f)) },
            ParticleGeometry.drawCleaveArc(
                parameters.origin,
                parameters.direction,
                parameters.radius(2.55),
                -18.0,
                -86.0,
                28.0,
                1,
                degreesPerTick = 114.0 / duration,
            ) { _, _, progress -> ParticleStyle(dust(lerpColor(primary, 0xe8fdff, progress), 0.24f)) },
            twinBladesConvergingStreaks(parameters, duration),
            spark,
        )
    }
}

private fun twinBladesSkill2Finisher(parameters: ParticlePresetParameters): ParticleEffect {
    val duration = parameters.ticks().coerceIn(4, 8)
    val radius = parameters.radius(4.0) * 1.2
    val primary = parameters.color("colorPrimary", 0xe8fdff)
    val secondary = parameters.color("colorSecondary", 0x071525)
    val arcStyle: (Point, Int, Double) -> ParticleStyle = { _, ring, progress ->
        ParticleStyle(dust(if (ring == 0) lerpColor(secondary, primary, progress) else 0x168cff, if (ring == 0) 0.48f else 0.3f), count = if (ring == 0) 2 else 1)
    }
    return ParticleParallel.of(
        ParticleGeometry.drawCleaveArc(
            parameters.origin,
            parameters.direction,
            radius,
            24.0,
            -118.0,
            38.0,
            2,
            ringSpacing = 0.22,
            degreesPerTick = 156.0 / duration,
            sample = arcStyle,
        ),
        ParticleGeometry.drawCleaveArc(
            parameters.origin,
            parameters.direction,
            radius * 0.94,
            -24.0,
            -38.0,
            118.0,
            2,
            ringSpacing = 0.22,
            degreesPerTick = 156.0 / duration,
            sample = arcStyle,
        ),
        pulseRing(
            parameters.origin,
            parameters.direction,
            radius,
            duration,
            ParticleStyle(dust(0x168cff, 0.34f), count = 2),
        ),
        pulseRing(
            parameters.origin,
            parameters.direction,
            radius * 0.78,
            duration,
            ParticleStyle(dust(0x071525, 0.44f), count = 2),
        ),
        twinBladesLandingShards(parameters, duration),
        ParticleSpiral(
            origin = parameters.origin,
            axis = parameters.direction,
            radius = radius * 0.42,
            curveAngle = PI * 2.0,
            curves = 1.6,
            axialLength = 0.55,
            durationTicks = duration,
            style = ParticleStyle(dust(0x126bff, 0.2f), count = 2),
        ),
        ParticleExplosion(
            parameters.origin,
            radius = radius * 0.55,
            sphere = true,
            particle = Particle.END_ROD,
            count = 42,
            speed = 0.24f,
            seed = parameters.seedValue() + 41,
        ),
        ParticleExplosion(
            parameters.origin.add(0.0, 0.2, 0.0),
            radius = radius * 0.7,
            sphere = true,
            particle = Particle.ENCHANT,
            count = 22,
            speed = 0.18f,
            seed = parameters.seedValue() + 47,
        ),
        ParticleExplosion(
             parameters.origin,
             radius = radius * 0.35,
             sphere = true,
             particle = Particle.ELECTRIC_SPARK,
             count = 26,
             speed = 0.2f,
             seed = parameters.seedValue() + 53,
         ),
      )
  }

private fun twinBladesLandingShards(parameters: ParticlePresetParameters, durationTicks: Int): ParticleEffect {
    val (forward, right, up) = basis(parameters.direction)
    val shards = (0 until 6).map { index ->
        val angle = index * PI / 3.0
        val radial = right.mul(cos(angle) * 0.72).add(up.mul(sin(angle) * 0.72))
        ParticleLine(
            start = parameters.origin.add(radial.x(), radial.y(), radial.z()),
            end = parameters.origin.add(
                radial.x() * 0.28 + forward.x() * 1.15,
                radial.y() * 0.28 + forward.y() * 1.15,
                radial.z() * 0.28 + forward.z() * 1.15,
            ),
            countPerMeter = 11.0,
            durationTicks = durationTicks,
            style = ParticleStyle(dust(if (index % 2 == 0) 0x70e9ff else 0xe8fdff, 0.26f), importance = ParticleImportance.COMBAT_FEEDBACK),
        )
    }
    return ParticleBatch.of(*shards.toTypedArray())
}

private fun twinBladesSkill3Slash(
    parameters: ParticlePresetParameters,
    origin: Point,
    angleDegrees: Double,
    length: Double,
    durationTicks: Int,
    bodyColor: Int,
): ParticleEffect = ParticleBatch.of(
    twinBladesArcRibbon(parameters.copy(origin = origin), angleDegrees, length, 0.42, 0.0, 1.0, 0x030811, 0.28f, false, durationTicks),
    twinBladesArcRibbon(parameters.copy(origin = origin), angleDegrees, length, 0.3, 0.0, 1.0, bodyColor, 0.42f, false, durationTicks),
    twinBladesArcRibbon(parameters.copy(origin = origin), angleDegrees, length, 0.1, 0.0, 1.0, parameters.color("colorPrimary", 0x8fffff), 0.24f, false, durationTicks),
    ParticleGeometry.drawParticleLineSlash(
        origin,
        parameters.direction,
        angleDegrees,
        length * 1.04,
        spacing = 0.15,
        durationTicks = durationTicks,
    ) { _, _, _, _ -> ParticleStyle(dust(0x030811, 0.34f), count = 1) },
    ParticleGeometry.drawParticleLineSlash(
        origin,
        parameters.direction,
        angleDegrees,
        length,
        spacing = 0.09,
        durationTicks = durationTicks,
    ) { _, middle, _, _ -> ParticleStyle(dust(bodyColor, (0.3 + middle * 0.18).toFloat()), count = if (middle > 0.55) 2 else 1) },
    ParticleGeometry.drawParticleLineSlash(
        origin,
        parameters.direction,
        angleDegrees,
        length * 0.92,
        spacing = 0.045,
        durationTicks = durationTicks,
    ) { _, middle, _, _ -> ParticleStyle(dust(parameters.color("colorPrimary", 0x8fffff), (0.16 + middle * 0.12).toFloat())) },
)

private fun twinBladesSkill3RecoilRibbon(parameters: ParticlePresetParameters, durationTicks: Int): ParticleEffect {
    val transform = ParticleTransform.fromDirection(parameters.origin, parameters.direction)
    return ParticleBatch.of(
        ParticleRibbon(
             path = { progress -> transform.localPoint(Vec(0.0, 0.0, progress * 1.35)) },
             sampleCount = 10,
             lanes = 4,
             width = KeyframeCurve.double(
                 CurveKeyframe(0.0, 0.3),
                 CurveKeyframe(0.55, 0.2),
                CurveKeyframe(1.0, 0.0),
                easing = Easing.SINE,
            ),
            durationTicks = durationTicks,
            styleAt = { progress, laneProgress ->
                val color = if (laneProgress == 0.5) parameters.color("colorPrimary", 0x168cff) else 0x050a14
                ParticleStyle(dust(color, (0.34 * (1.0 - progress)).coerceAtLeast(0.08).toFloat()))
            },
        ),
        ParticleSpiral(
            origin = parameters.origin,
            axis = parameters.direction,
            radius = 0.22,
            curveAngle = PI * 1.5,
            curves = 1.0,
            axialLength = 1.1,
            durationTicks = durationTicks,
            style = ParticleStyle(dust(parameters.color("colorPrimary", 0x168cff), 0.22f)),
        ),
        ParticleExplosion(
            parameters.origin,
            radius = 0.18,
            count = 3,
            speed = 0.04f,
            particle = Particle.ELECTRIC_SPARK,
            seed = parameters.seedValue(),
        ),
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
    val skillCommon = listOf(scale(), density(), seed())
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
        preset("projects:geometry/helix", "Helix", geometry, listOf(number("length", 3.0, 0.0, 12.0), number("turns", 2.0, 0.25, 8.0)) + common) { p -> ParticleSpiral(p.origin, p.direction, p.radius(0.7), PI * 2.0, p.number("turns", 2.0), axialLength = p.length(), durationTicks = p.ticks(), style = style(p, particle = Particle.DUST)) },
        preset("projects:geometry/double_helix", "Double Helix", geometry, listOf(number("length", 3.0, 0.0, 12.0), number("turns", 2.0, 0.25, 8.0)) + common) { p -> ParticleBatch.of(ParticleSpiral(p.origin, p.direction, p.radius(0.65), PI * 2.0, p.number("turns", 2.0), axialLength = p.length(), angleOffset = 0.0, durationTicks = p.ticks(), style = style(p, particle = Particle.DUST)), ParticleSpiral(p.origin, p.direction, p.radius(0.65), PI * 2.0, p.number("turns", 2.0), axialLength = p.length(), angleOffset = PI, durationTicks = p.ticks(), style = style(p, primary = false, particle = Particle.DUST))) },

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
          preset(
               "projects:class/twin_blades/aa_swing",
               "Twin Blades AA Swing",
               combat + setOf("class", "twin_blades"),
               listOf(number("length", 2.4, 0.0, 4.0), number("angle", 35.0, -180.0, 180.0), number("step", 1.0, 1.0, 3.0)) + colors + common,
           ) { p ->
                 twinBladesStepEffect(
                     p,
                     p.number("angle", 35.0),
                      p.length(2.4),
                      p.ticks().coerceAtMost(3),
                      p.number("step", 1.0).roundToInt(),
                      swingGeometry = true,
                   )
            },
          preset(
              "projects:class/twin_blades/skill3_dash_trail",
              "Twin Blades Skill3 Dash Trail",
              combat + setOf("class", "twin_blades"),
              listOf(duration(2.0), color("colorPrimary", 0x168cff), color("colorSecondary", 0x050a14)) + common.filter { it.name != "duration" },
           ) { p ->
               val transform = ParticleTransform.fromDirection(p.origin, p.direction)
               ParticleParallel.of(
                   ParticleRibbon(
                       path = { progress -> transform.localPoint(Vec(0.0, 0.0, -(0.12 + progress * 1.25))) },
                       sampleCount = 10,
                       lanes = 3,
                       width = KeyframeCurve.double(
                           CurveKeyframe(0.0, 0.22),
                           CurveKeyframe(0.5, 0.14),
                           CurveKeyframe(1.0, 0.02),
                          easing = Easing.SINE,
                      ),
                      durationTicks = p.ticks(),
                      styleAt = { progress, laneProgress ->
                          val color = if (laneProgress == 0.5) p.color("colorPrimary", 0x168cff) else p.color("colorSecondary", 0x050a14)
                          ParticleStyle(dust(color, (0.26 * (1.0 - progress)).coerceAtLeast(0.08).toFloat()))
                       },
                   ),
                   ParticleSpiral(
                       origin = p.origin,
                       axis = p.direction,
                       radius = 0.18,
                       curveAngle = PI * 1.5,
                       curves = 1.0,
                       axialLength = 0.9,
                       durationTicks = p.ticks(),
                       style = ParticleStyle(dust(p.color("colorPrimary", 0x168cff), 0.18f)),
                   ),
                   ParticleExplosion(
                      transform.localPoint(Vec(0.0, 0.0, -0.28)),
                      count = 2,
                      speed = 0.035f,
                      particle = Particle.ELECTRIC_SPARK,
                      seed = p.seedValue(),
                  ),
              )
          },
          preset(
              "projects:class/twin_blades/skill3_hit",
              "Twin Blades Skill3 Slash Through",
              combat + setOf("class", "twin_blades"),
              listOf(
                   number("length", 5.8, 5.5, 6.0),
                   number("aftercutLength", 3.1, 2.5, 3.4),
                  duration(4.0),
                  color("colorPrimary", 0x8fffff),
                  color("colorSecondary", 0x168cff),
              ) + common.filter { it.name !in setOf("duration", "colorPrimary", "colorSecondary") },
          ) { p ->
              val duration = p.ticks().coerceIn(3, 4)
              val transform = ParticleTransform.fromDirection(p.origin, p.direction)
              val aftercutOrigin = transform.localPoint(Vec(0.42, -0.2, 0.08))
              val primary = twinBladesSkill3Slash(
                  p,
                  p.origin,
                  angleDegrees = 24.0,
                  length = p.number("length", 4.8),
                  durationTicks = duration,
                  bodyColor = p.color("colorSecondary", 0x168cff),
              )
              val aftercut = twinBladesSkill3Slash(
                  p,
                  aftercutOrigin,
                  angleDegrees = -24.0,
                  length = p.number("aftercutLength", 2.8),
                  durationTicks = 2,
                  bodyColor = p.color("colorSecondary", 0x126bff),
              )
              ParticleParallel.of(
                  primary,
                  ParticleSequence.of(ParticleDelay(1), aftercut),
                  ParticleSequence.of(
                      ParticleDelay(1),
                      ParticleExplosion(
                          p.origin,
                          radius = 0.32,
                          count = 6,
                          speed = 0.06f,
                          particle = Particle.ELECTRIC_SPARK,
                          seed = p.seedValue(),
                      ),
                  ),
              )
          },
          preset(
              "projects:class/twin_blades/skill3_recoil",
              "Twin Blades Skill3 Recoil Ribbon",
              combat + setOf("class", "twin_blades"),
              listOf(duration(4.0), color("colorPrimary", 0x168cff), color("colorSecondary", 0x050a14)) + common.filter { it.name != "duration" },
          ) { p -> twinBladesSkill3RecoilRibbon(p, p.ticks().coerceIn(3, 4)) },
           preset(
               "projects:class/twin_blades/aa_hit",
               "Twin Blades AA Hit",
               combat + setOf("class", "twin_blades"),
               listOf(number("length", 3.2, 0.0, 4.0), number("radius", 1.25, 0.0, 2.0), number("angle", 35.0, -180.0, 180.0), number("step", 1.0, 1.0, 3.0)) + colors + common,
           ) { p ->
               val duration = p.ticks()
               val step = p.number("step", 1.0).roundToInt()
               ParticleParallel.of(
                     twinBladesStepEffect(p, p.number("angle", 35.0), p.length(3.0), duration, step),
                   ParticleGeometry.drawCleaveArc(
                      p.origin,
                      p.direction,
                      p.radius(1.25),
                      0.0,
                      -52.0,
                      52.0,
                       2,
                       ringSpacing = 0.18,
                      degreesPerTick = 104.0 / duration,
                  ) { _, ring, progress ->
                        ParticleStyle(
                            dust(
                                when (ring) {
                                    0 -> lerpColor(0x168cff, 0x70e9ff, progress)
                                    1 -> lerpColor(0x126bff, 0x168cff, progress)
                                    else -> 0x071525
                                },
                                when (ring) {
                                    0 -> 0.24f
                                    1 -> 0.42f
                                    else -> 0.3f
                                },
                            ),
                        )
                  },
                    if (step == 3) ParticleSequence.of(ParticleDelay(1), twinBladesContactEmitter(p, count = 8, radius = 0.45)) else ParticleExplosion(p.origin, count = 5, speed = 0.1f, particle = Particle.ELECTRIC_SPARK, seed = p.seedValue()),
                )
            },
           preset(
              "projects:class/twin_blades/weakpoint_hit",
               "Twin Blades Weakpoint Hit",
               combat + setOf("class", "twin_blades"),
               listOf(number("radius", 1.35, 0.0, 2.0), number("step", 1.0, 1.0, 3.0)) + colors + common,
           ) { p ->
               val duration = p.ticks().coerceIn(4, 6)
               val radius = p.radius(1.35)
               val step = p.number("step", 1.0).roundToInt()
               ParticleParallel.of(
                    twinBladesStepEffect(p, 42.0, radius, duration, step),
                    ParticleFlower(
                        p.origin,
                        petals = 4,
                        radius = radius * 0.72,
                        sharp = true,
                        planeNormal = p.direction,
                        count = 24,
                        style = ParticleStyle(dust(p.color("colorPrimary", 0xffffff), 0.28f)),
                     ),
                     ParticleExplosion(p.origin, radius = radius * 0.5, sphere = true, count = 7, speed = 0.08f, particle = Particle.END_ROD, seed = p.seedValue()),
                )
            },
            preset(
              "projects:class/twin_blades/skill1_travel",
              "Twin Blades Skill1 Travel",
              combat + setOf("class", "twin_blades", "skill1"),
              listOf(number("length", 1.35, 0.0, 3.0), number("duration", 2.0, 1.0, 6.0)) + colors + skillCommon,
           ) { p ->
               val length = p.length(1.35) * 1.3
               val backward = normalize(p.direction).mul(-length)
               ParticleParallel.of(
                  skill1TrailRibbon(p, length, 1.0, p.color("colorSecondary", 0x071525), 0.11),
                  skill1TrailRibbon(p, length, -1.0, p.color("colorPrimary", 0x168cff), 0.08),
                   ParticleLine(
                      p.origin,
                      p.origin.add(backward.x(), backward.y(), backward.z()),
                      countPerMeter = 8.0,
                      durationTicks = p.ticks(),
                       style = ParticleStyle(dust(p.color("colorSecondary", 0x071525), 0.18f), importance = ParticleImportance.COMBAT_FEEDBACK),
                   ),
                   skill1TravelOrbit(p, length, p.ticks()),
                   ParticleExplosion(p.origin, radius = 0.12, count = 3, speed = 0.025f, particle = Particle.ELECTRIC_SPARK, seed = p.seedValue()),
               )
           },
           preset(
              "projects:class/twin_blades/skill1_stomp",
              "Twin Blades Skill1 Stomp",
              combat + setOf("class", "twin_blades", "skill1"),
              listOf(number("radius", 0.82, 0.0, 2.0), number("duration", 5.0, 4.0, 6.0)) + colors + skillCommon,
          ) { p -> skill1StompEffect(p) },
          preset(
              "projects:class/twin_blades/skill1_escape",
              "Twin Blades Skill1 Escape",
              combat + setOf("class", "twin_blades", "skill1"),
              listOf(number("length", 1.7, 0.0, 3.5), number("duration", 4.0, 3.0, 6.0)) + colors + skillCommon,
           ) { p -> skill1EscapeEffect(p) },
           preset(
              "projects:class/twin_blades/skill2_pulse",
              "Twin Blades Blade Storm Pulse",
              combat + setOf("class", "twin_blades", "skill2"),
              listOf(number("pulse", 1.0, 1.0, 4.0), number("radius", 2.75, 0.0, 4.0)) + colors + common,
          ) { p -> twinBladesSkill2Pulse(p) },
          preset(
              "projects:class/twin_blades/skill2_finisher",
              "Twin Blades Blade Storm Finisher",
              combat + setOf("class", "twin_blades", "skill2"),
              listOf(number("radius", 4.0, 0.0, 8.0)) + colors + common,
          ) { p -> twinBladesSkill2Finisher(p) },
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
