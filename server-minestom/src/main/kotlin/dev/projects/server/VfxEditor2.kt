package dev.projects.server

import dev.projects.protocol.VfxEditor2ApplyRequest
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Draft
import dev.projects.protocol.VfxEditor2DraftList
import dev.projects.protocol.VfxEditor2LoadRequest
import dev.projects.protocol.VfxEditor2Notice
import dev.projects.protocol.VfxEditor2Open
import dev.projects.protocol.VfxEditor2Particle
import dev.projects.protocol.VfxEditor2PreviewRequest
import dev.projects.protocol.VfxEditor2Rotation
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2ShapeParameters
import dev.projects.protocol.VfxEditor2WidthCurve
import dev.projects.protocol.VfxEditor2Layer
import dev.projects.protocol.VfxEditor2Offset
import dev.projects.protocol.ProtocolMessage
import dev.projects.server.particle.ParticleAnimationScheduler
import dev.projects.server.particle.ParticleBatch
import dev.projects.server.particle.ParticleCircle
import dev.projects.server.particle.ParticleDelay
import dev.projects.server.particle.ParticleEffect
import dev.projects.server.particle.ParticleExplosion
import dev.projects.server.particle.ParticleLine
import dev.projects.server.particle.ParticleManager
import dev.projects.server.particle.ParticleParallel
import dev.projects.server.particle.ParticleRibbon
import dev.projects.server.particle.ParticleSink
import dev.projects.server.particle.ParticleStyle
import dev.projects.server.particle.PlayerParticleSink
import dev.projects.server.particle.ParticleViewer
import dev.projects.server.particle.KeyframeCurve
import dev.projects.server.particle.Curve
import dev.projects.server.particle.CurveKeyframe
import dev.projects.server.particle.constantCurve
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.particle.Particle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val VFX_EDITOR_2_SCHEMA_VERSION = 1
private const val VFX_EDITOR_2_MAX_DRAFTS = 32
private const val VFX_EDITOR_2_LOOP_GAP_TICKS = 10

/** Turns an Editor 2 composition into the existing ProjectS particle primitives. */
object VfxEditor2Compiler {
    fun compile(composition: VfxEditor2Composition, origin: Point, direction: Vec): ParticleEffect {
        val activeLayers = composition.layers.filter { it.enabled }
        val soloLayers = activeLayers.filter { it.solo }
        val selectedLayers = if (soloLayers.isEmpty()) activeLayers else soloLayers
        val effects = selectedLayers.map { layer ->
            val delayed = ParticleSequenceWithDuration(
                startTick = layer.startTick,
                layerDurationTicks = layer.durationTicks,
                effect = compileLayer(layer, origin, direction),
            )
            delayed
        }
        val parallel = ParticleParallel.of(*effects.toTypedArray())
        return FixedDurationParticleEffect(parallel, composition.durationTicks)
    }

    fun estimateParticles(composition: VfxEditor2Composition): Int {
        val active = composition.layers.filter { it.enabled }
        val solo = active.filter { it.solo }
        val selected = if (solo.isEmpty()) active else solo
        return selected.sumOf { layer ->
            val shape = layer.shapeParameters
            when (layer.shapeType) {
                VfxEditor2Shape.RIBBON -> (shape.sampleDensity * shape.laneCount * layer.density).toInt()
                VfxEditor2Shape.LINE -> (shape.lineLength / shape.lineSpacing * layer.density).toInt()
                VfxEditor2Shape.CIRCLE -> (shape.circleRadius * Math.toRadians(shape.circleArcDegrees) / shape.circleSpacing * layer.density).toInt()
                VfxEditor2Shape.BURST -> (shape.burstCount * layer.density).toInt()
            }.coerceAtMost(1024)
        }.coerceAtMost(4096)
    }

    private fun compileLayer(layer: VfxEditor2Layer, origin: Point, direction: Vec): ParticleEffect {
        val frame = frame(origin, direction, layer.offset, layer.rotation)
        val shape = layer.shapeParameters
        val style = style(layer)
        return when (layer.shapeType) {
            VfxEditor2Shape.RIBBON -> {
                val span = Math.toRadians(shape.arcSpan.coerceIn(1.0, 360.0))
                val radius = shape.length / span.coerceAtLeast(0.05)
                val start = if (shape.reverse) span / 2.0 else -span / 2.0
                val signedSpan = if (shape.reverse) -span else span
                val curve: (Double) -> Point = { progress ->
                    val angle = start + signedSpan * progress
                    val forwardDistance = (sin(angle) - sin(start)) * radius
                    val rightDistance = (cos(start) - cos(angle)) * radius
                    val lift = sin(progress * PI) * shape.curvature * 0.45
                    frame.origin.add(
                        frame.forward.x() * forwardDistance + frame.right.x() * rightDistance + frame.up.x() * lift,
                        frame.forward.y() * forwardDistance + frame.right.y() * rightDistance + frame.up.y() * lift,
                        frame.forward.z() * forwardDistance + frame.right.z() * rightDistance + frame.up.z() * lift,
                    )
                }
                ParticleRibbon(
                    path = curve,
                    particle = style.particle,
                    sampleCount = (shape.sampleDensity * layer.density).toInt().coerceIn(2, 64),
                    lanes = shape.laneCount,
                    width = widthCurve(shape.width, shape.widthCurve),
                    durationTicks = layer.durationTicks,
                    styleAt = { _, _ -> style },
                )
            }
            VfxEditor2Shape.LINE -> {
                val end = frame.origin.add(
                    frame.forward.x() * shape.lineLength,
                    frame.forward.y() * shape.lineLength,
                    frame.forward.z() * shape.lineLength,
                )
                ParticleLine(
                    start = frame.origin,
                    end = end,
                    particle = style.particle,
                    countPerMeter = (layer.density / shape.lineSpacing).coerceIn(0.5, 32.0),
                    durationTicks = layer.durationTicks,
                    style = style,
                )
            }
            VfxEditor2Shape.CIRCLE -> ParticleCircle(
                center = frame.origin,
                radius = shape.circleRadius,
                axis1 = frame.right,
                axis2 = frame.up,
                startDegrees = -shape.circleArcDegrees / 2.0,
                endDegrees = shape.circleArcDegrees / 2.0,
                countPerMeter = (layer.density / shape.circleSpacing).coerceIn(0.5, 32.0),
                includeEnd = true,
                durationTicks = layer.durationTicks,
                style = style,
            )
            VfxEditor2Shape.BURST -> {
                val burst = ParticleExplosion(
                    center = frame.origin,
                    radius = shape.burstRadius,
                    sphere = shape.burstSpread >= 1.0,
                    particle = style.particle,
                    count = (shape.burstCount * layer.density).toInt().coerceIn(0, 64),
                    speed = shape.burstSpeed.toFloat(),
                    speedVariance = (shape.burstSpread * 0.04).toFloat(),
                    seed = layer.id.toLong() * 31L + layer.startTick,
                )
                if (layer.durationTicks <= 1) burst else ParticleBatch.of(burst, ParticleDelay(layer.durationTicks - 1))
            }
        }
    }

    private data class Frame(val origin: Point, val forward: Vec, val right: Vec, val up: Vec)

    private fun frame(origin: Point, direction: Vec, offset: VfxEditor2Offset, rotation: VfxEditor2Rotation): Frame {
        val baseForward = normalize(direction)
        val baseRight = perpendicular(baseForward)
        val baseUp = normalize(cross(baseForward, baseRight))
        val yaw = Math.toRadians(rotation.yaw)
        val pitch = Math.toRadians(rotation.pitch)
        val roll = Math.toRadians(rotation.roll)
        val yawForward = baseForward.rotateAroundAxis(baseUp, yaw)
        val yawRight = baseRight.rotateAroundAxis(baseUp, yaw)
        val pitchedForward = yawForward.rotateAroundAxis(yawRight, pitch)
        val pitchedUp = baseUp.rotateAroundAxis(yawRight, pitch)
        val right = normalize(yawRight.rotateAroundAxis(pitchedForward, roll))
        val up = normalize(cross(pitchedForward, right))
        val shifted = origin.add(
            pitchedForward.x() * offset.forward + right.x() * offset.right + up.x() * offset.up,
            pitchedForward.y() * offset.forward + right.y() * offset.right + up.y() * offset.up,
            pitchedForward.z() * offset.forward + right.z() * offset.right + up.z() * offset.up,
        )
        return Frame(shifted, normalize(pitchedForward), right, up)
    }

    private fun widthCurve(width: Double, preset: VfxEditor2WidthCurve): Curve<Double> = when (preset) {
        VfxEditor2WidthCurve.CONSTANT -> constantCurve(width)
        VfxEditor2WidthCurve.THIN_THICK_THIN -> KeyframeCurve.double(
            CurveKeyframe(0.0, width * 0.18),
            CurveKeyframe(0.5, width),
            CurveKeyframe(1.0, width * 0.18),
        )
    }

    private fun style(layer: VfxEditor2Layer): ParticleStyle {
        val particle = when (layer.particleType) {
            VfxEditor2Particle.DUST -> dust(layer.color, layer.size.toFloat())
            VfxEditor2Particle.END_ROD -> Particle.END_ROD
            VfxEditor2Particle.ELECTRIC_SPARK -> Particle.ELECTRIC_SPARK
            VfxEditor2Particle.CRIT -> Particle.CRIT
            VfxEditor2Particle.ENCHANT -> Particle.ENCHANT
            VfxEditor2Particle.SOUL_FIRE_FLAME -> Particle.SOUL_FIRE_FLAME
            VfxEditor2Particle.FLAME -> Particle.FLAME
        }
        return ParticleStyle(
            particle = particle,
            count = if (layer.particleType == VfxEditor2Particle.DUST) 1 else 1,
        )
    }

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())

    private fun perpendicular(value: Vec): Vec {
        val reference = if (kotlin.math.abs(value.y()) < 0.9) Vec(0.0, 1.0, 0.0) else Vec(1.0, 0.0, 0.0)
        return normalize(cross(reference, value))
    }

    private fun cross(a: Vec, b: Vec): Vec = Vec(
        a.y() * b.z() - a.z() * b.y(),
        a.z() * b.x() - a.x() * b.z(),
        a.x() * b.y() - a.y() * b.x(),
    )
}

private class ParticleSequenceWithDuration(
    private val startTick: Int,
    private val layerDurationTicks: Int,
    private val effect: ParticleEffect,
) : ParticleEffect {
    override val durationTicks: Int = startTick + layerDurationTicks
    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick < startTick || tick >= durationTicks) return
        effect.emit(tick - startTick, sink)
    }
}

private class FixedDurationParticleEffect(
    private val effect: ParticleEffect,
    override val durationTicks: Int,
) : ParticleEffect {
    init { require(durationTicks >= 1) }

    override fun emit(tick: Int, sink: ParticleSink) {
        if (tick in 0 until effect.durationTicks) effect.emit(tick, sink)
    }
}

class VfxEditor2DraftStore(private val file: Path) {
    private val drafts = linkedMapOf<String, VfxEditor2Composition>()

    init { readFromDisk() }

    fun list(): List<String> = drafts.keys.sorted()

    fun load(name: String): VfxEditor2Composition? = drafts[name]

    fun save(composition: VfxEditor2Composition): Boolean {
        if (composition.name !in drafts && drafts.size >= VFX_EDITOR_2_MAX_DRAFTS) return false
        drafts[composition.name] = composition
        return writeToDisk()
    }

    private fun readFromDisk() {
        if (!Files.isRegularFile(file)) return
        runCatching {
            val reader = VfxEditor2Json.Reader(Files.readString(file))
            reader.objectFields { key ->
                when (key) {
                    "drafts" -> reader.array {
                        var name: String? = null
                        var composition: VfxEditor2Composition? = null
                        reader.objectFields { draftKey ->
                            when (draftKey) {
                                "name" -> name = reader.string()
                                "composition" -> composition = VfxEditor2Json.readComposition(reader)
                                else -> reader.skipValue()
                            }
                        }
                        val value = composition
                        if (name != null && value != null && name == value.name && drafts.size < VFX_EDITOR_2_MAX_DRAFTS) drafts[name!!] = value
                    }
                    else -> reader.skipValue()
                }
            }
            reader.ensureEnd()
        }
    }

    private fun writeToDisk(): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val json = buildString {
            append("{\"schemaVersion\":").append(VFX_EDITOR_2_SCHEMA_VERSION).append(",\"drafts\":[")
            drafts.entries.sortedBy { it.key }.forEachIndexed { index, (name, composition) ->
                if (index > 0) append(',')
                append("{\"name\":").append(VfxEditor2Json.string(name)).append(",\"composition\":")
                VfxEditor2Json.appendComposition(this, composition)
                append('}')
            }
            append("]}")
        }
        Files.writeString(file, json)
        true
    }.getOrDefault(false)
}

private class VfxEditor2RuntimeBindingStore(private val file: Path) {
    fun load(): VfxEditor2Composition? = runCatching {
        if (!Files.isRegularFile(file)) return null
        val reader = VfxEditor2Json.Reader(Files.readString(file))
        var result: VfxEditor2Composition? = null
        reader.objectFields { key ->
            if (key == "composition") result = VfxEditor2Json.readComposition(reader) else reader.skipValue()
        }
        reader.ensureEnd()
        result
    }.getOrNull()

    fun save(composition: VfxEditor2Composition): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val json = buildString {
            append("{\"schemaVersion\":").append(VFX_EDITOR_2_SCHEMA_VERSION).append(",\"composition\":")
            VfxEditor2Json.appendComposition(this, composition)
            append('}')
        }
        Files.writeString(file, json)
        true
    }.getOrDefault(false)
}

class VfxEditor2Runtime(
    private val scheduler: ParticleAnimationScheduler,
    private val particleManager: ParticleManager,
    private val send: (Player, ProtocolMessage) -> Unit,
    private val viewersFor: (Player) -> Iterable<Player>,
    draftFile: Path,
    bindingFile: Path,
) {
    private data class Preview(
        val player: Player,
        val composition: VfxEditor2Composition,
        val origin: Point,
        val direction: Vec,
        val loop: Boolean,
        var handle: dev.projects.server.particle.ParticleEffectHandle,
        var gapTicks: Int = 0,
    )

    private val drafts = VfxEditor2DraftStore(draftFile)
    private val bindingStore = VfxEditor2RuntimeBindingStore(bindingFile)
    private val previews = mutableMapOf<java.util.UUID, Preview>()
    private var runtimeRoninQ: VfxEditor2Composition? = bindingStore.load()

    fun open(player: Player) {
        send(player, VfxEditor2Open(runtimeRoninQ ?: VfxEditor2Composition()))
        send(player, VfxEditor2DraftList(drafts.list()))
    }

    fun preview(player: Player, request: VfxEditor2PreviewRequest) {
        cancel(player)
        val direction = normalize(requestDirection(player))
        val origin = player.position.add(direction.x() * 5.0, direction.y() * 5.0, direction.z() * 5.0)
        val preview = Preview(
            player = player,
            composition = request.composition,
            origin = origin,
            direction = direction,
            loop = request.loop,
            handle = startForViewer(player, request.composition, origin, direction, "editor2:${player.uuid}"),
        )
        previews[player.uuid] = preview
    }

    fun cancel(player: Player) {
        previews.remove(player.uuid)?.let { scheduler.cancel(it.handle) }
    }

    fun tick() {
        previews.values.toList().forEach { preview ->
            if (preview.handle.state == dev.projects.server.particle.ParticleEffectState.CANCELLED) {
                previews.remove(preview.player.uuid)
                return@forEach
            }
            if (preview.handle.state != dev.projects.server.particle.ParticleEffectState.COMPLETED) return@forEach
            if (!preview.loop) {
                previews.remove(preview.player.uuid)
                return@forEach
            }
            if (preview.gapTicks > 0) {
                preview.gapTicks--
            } else {
                preview.handle = startForViewer(
                    preview.player,
                    preview.composition,
                    preview.origin,
                    preview.direction,
                    "editor2:${preview.player.uuid}",
                )
                preview.gapTicks = VFX_EDITOR_2_LOOP_GAP_TICKS
            }
        }
    }

    fun save(player: Player, composition: VfxEditor2Composition) {
        val saved = drafts.save(composition)
        send(player, VfxEditor2Notice(if (saved) "Editor 2 draft saved: ${composition.name}" else "Draft name is invalid or storage is full"))
        if (saved) send(player, VfxEditor2DraftList(drafts.list()))
    }

    fun load(player: Player, request: VfxEditor2LoadRequest) {
        val composition = drafts.load(request.name)
        if (composition == null) {
            send(player, VfxEditor2Notice("Editor 2 draft not found"))
        } else {
            send(player, VfxEditor2Draft(composition))
        }
    }

    fun apply(player: Player, request: VfxEditor2ApplyRequest) {
        if (request.runtimeVfxId != "ronin.q") {
            send(player, VfxEditor2Notice("Only runtime binding ronin.q is available in V1"))
            return
        }
        if (!bindingStore.save(request.composition)) {
            send(player, VfxEditor2Notice("Could not save runtime VFX binding"))
            return
        }
        runtimeRoninQ = request.composition
        send(player, VfxEditor2Notice("Applied ${request.runtimeVfxId}; Ronin Q uses it immediately"))
    }

    fun playRoninQ(source: Player, origin: Point, direction: Vec, seed: Long): Boolean {
        val composition = runtimeRoninQ ?: return false
        viewersFor(source).forEach { viewer ->
            startForViewer(
                viewer,
                composition,
                origin,
                direction,
                "runtime:ronin.q:${source.uuid}:$seed:${viewer.uuid}",
            )
        }
        return true
    }

    fun disconnect(player: Player) = cancel(player)

    private fun startForViewer(
        viewer: Player,
        composition: VfxEditor2Composition,
        origin: Point,
        direction: Vec,
        id: String,
    ): dev.projects.server.particle.ParticleEffectHandle {
        val sink = particleManager.sink(
            ParticleViewer(viewer.position, viewer),
            PlayerParticleSink(viewer),
            id,
        )
        return scheduler.start(VfxEditor2Compiler.compile(composition, origin, direction), sink, id = id)
    }

    private fun requestDirection(player: Player): Vec = player.position.direction()

    private fun normalize(value: Vec): Vec = if (value.length() <= 1.0e-9) Vec(0.0, 0.0, 1.0) else value.mul(1.0 / value.length())
}

private object VfxEditor2Json {
    fun string(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(character)
            }
        }
        append('"')
    }

    fun appendComposition(out: StringBuilder, composition: VfxEditor2Composition) {
        out.append("{\"name\":").append(string(composition.name))
            .append(",\"durationTicks\":").append(composition.durationTicks)
            .append(",\"layers\":[")
        composition.layers.forEachIndexed { index, layer ->
            if (index > 0) out.append(',')
            appendLayer(out, layer)
        }
        out.append("]}")
    }

    private fun appendLayer(out: StringBuilder, layer: VfxEditor2Layer) {
        val shape = layer.shapeParameters
        out.append("{\"id\":").append(layer.id)
            .append(",\"name\":").append(string(layer.name))
            .append(",\"enabled\":").append(layer.enabled)
            .append(",\"solo\":").append(layer.solo)
            .append(",\"shapeType\":").append(string(layer.shapeType.name))
            .append(",\"particleType\":").append(string(layer.particleType.name))
            .append(",\"color\":").append(layer.color)
            .append(",\"size\":").append(layer.size)
            .append(",\"density\":").append(layer.density)
            .append(",\"offset\":{\"forward\":").append(layer.offset.forward)
            .append(",\"right\":").append(layer.offset.right).append(",\"up\":").append(layer.offset.up).append('}')
            .append(",\"rotation\":{\"yaw\":").append(layer.rotation.yaw)
            .append(",\"pitch\":").append(layer.rotation.pitch).append(",\"roll\":").append(layer.rotation.roll).append('}')
            .append(",\"startTick\":").append(layer.startTick)
            .append(",\"durationTicks\":").append(layer.durationTicks)
            .append(",\"shapeParameters\":{")
            .append("\"length\":").append(shape.length)
            .append(",\"arcSpan\":").append(shape.arcSpan)
            .append(",\"curvature\":").append(shape.curvature)
            .append(",\"width\":").append(shape.width)
            .append(",\"sampleDensity\":").append(shape.sampleDensity)
            .append(",\"laneCount\":").append(shape.laneCount)
            .append(",\"laneSpacing\":").append(shape.laneSpacing)
            .append(",\"reverse\":").append(shape.reverse)
            .append(",\"widthCurve\":").append(string(shape.widthCurve.name))
            .append(",\"lineLength\":").append(shape.lineLength)
            .append(",\"lineSpacing\":").append(shape.lineSpacing)
            .append(",\"circleRadius\":").append(shape.circleRadius)
            .append(",\"circleArcDegrees\":").append(shape.circleArcDegrees)
            .append(",\"circleSpacing\":").append(shape.circleSpacing)
            .append(",\"burstRadius\":").append(shape.burstRadius)
            .append(",\"burstCount\":").append(shape.burstCount)
            .append(",\"burstSpread\":").append(shape.burstSpread)
            .append(",\"burstSpeed\":").append(shape.burstSpeed)
            .append("}}")
    }

    fun readComposition(reader: Reader): VfxEditor2Composition {
        var name: String? = null
        var duration = 16
        var layers: List<VfxEditor2Layer> = emptyList()
        reader.objectFields { key ->
            when (key) {
                "name" -> name = reader.string()
                "durationTicks" -> duration = reader.int()
                "layers" -> layers = reader.array { readLayer(reader) }
                else -> reader.skipValue()
            }
        }
        return VfxEditor2Composition.clamped(name ?: error("Missing VFX Editor 2 composition name"), duration, layers)
    }

    private fun readLayer(reader: Reader): VfxEditor2Layer {
        var id = 0
        var name = "Layer"
        var enabled = true
        var solo = false
        var shapeType = VfxEditor2Shape.RIBBON
        var particleType = VfxEditor2Particle.DUST
        var color = 0xffffff
        var size = 0.28
        var density = 1.0
        var offset = VfxEditor2Offset()
        var rotation = VfxEditor2Rotation()
        var startTick = 0
        var duration = 8
        var shape = VfxEditor2ShapeParameters()
        reader.objectFields { key ->
            when (key) {
                "id" -> id = reader.int()
                "name" -> name = reader.string()
                "enabled" -> enabled = reader.boolean()
                "solo" -> solo = reader.boolean()
                "shapeType" -> shapeType = enumValue(reader.string(), VfxEditor2Shape.entries, shapeType)
                "particleType" -> particleType = enumValue(reader.string(), VfxEditor2Particle.entries, particleType)
                "color" -> color = reader.int()
                "size" -> size = reader.double()
                "density" -> density = reader.double()
                "offset" -> offset = readOffset(reader)
                "rotation" -> rotation = readRotation(reader)
                "startTick" -> startTick = reader.int()
                "durationTicks" -> duration = reader.int()
                "shapeParameters" -> shape = readShapeParameters(reader)
                else -> reader.skipValue()
            }
        }
        return VfxEditor2Layer.clamped(
            id, name, enabled, solo, shapeType, particleType, color, size, density,
            offset, rotation, startTick, duration, shape,
        )
    }

    private fun readOffset(reader: Reader): VfxEditor2Offset {
        var forward = 0.0
        var right = 0.0
        var up = 0.0
        reader.objectFields { key ->
            when (key) {
                "forward" -> forward = reader.double()
                "right" -> right = reader.double()
                "up" -> up = reader.double()
                else -> reader.skipValue()
            }
        }
        return VfxEditor2Offset.clamped(forward, right, up)
    }

    private fun readRotation(reader: Reader): VfxEditor2Rotation {
        var yaw = 0.0
        var pitch = 0.0
        var roll = 0.0
        reader.objectFields { key ->
            when (key) {
                "yaw" -> yaw = reader.double()
                "pitch" -> pitch = reader.double()
                "roll" -> roll = reader.double()
                else -> reader.skipValue()
            }
        }
        return VfxEditor2Rotation.clamped(yaw, pitch, roll)
    }

    private fun readShapeParameters(reader: Reader): VfxEditor2ShapeParameters {
        var value = VfxEditor2ShapeParameters()
        reader.objectFields { key ->
            value = when (key) {
                "length" -> value.copy(length = reader.double())
                "arcSpan" -> value.copy(arcSpan = reader.double())
                "curvature" -> value.copy(curvature = reader.double())
                "width" -> value.copy(width = reader.double())
                "sampleDensity" -> value.copy(sampleDensity = reader.double())
                "laneCount" -> value.copy(laneCount = reader.int())
                "laneSpacing" -> value.copy(laneSpacing = reader.double())
                "reverse" -> value.copy(reverse = reader.boolean())
                "widthCurve" -> value.copy(widthCurve = enumValue(reader.string(), VfxEditor2WidthCurve.entries, value.widthCurve))
                "lineLength" -> value.copy(lineLength = reader.double())
                "lineSpacing" -> value.copy(lineSpacing = reader.double())
                "circleRadius" -> value.copy(circleRadius = reader.double())
                "circleArcDegrees" -> value.copy(circleArcDegrees = reader.double())
                "circleSpacing" -> value.copy(circleSpacing = reader.double())
                "burstRadius" -> value.copy(burstRadius = reader.double())
                "burstCount" -> value.copy(burstCount = reader.int())
                "burstSpread" -> value.copy(burstSpread = reader.double())
                "burstSpeed" -> value.copy(burstSpeed = reader.double())
                else -> { reader.skipValue(); value }
            }
        }
        return VfxEditor2ShapeParameters.clamped(
            value.length, value.arcSpan, value.curvature, value.width, value.sampleDensity,
            value.laneCount, value.laneSpacing, value.reverse, value.widthCurve,
            value.lineLength, value.lineSpacing, value.circleRadius, value.circleArcDegrees,
            value.circleSpacing, value.burstRadius, value.burstCount, value.burstSpread, value.burstSpeed,
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, values: List<T>, fallback: T): T = values.firstOrNull { it.name == value } ?: fallback

    class Reader(private val text: String) {
        private var index = 0

        fun objectFields(block: (String) -> Unit) {
            expect('{')
            skipWhitespace()
            if (consume('}')) return
            while (true) {
                val key = string()
                expect(':')
                block(key)
                skipWhitespace()
                if (consume('}')) return
                expect(',')
            }
        }

        fun <T> array(block: () -> T): List<T> {
            expect('[')
            skipWhitespace()
            if (consume(']')) return emptyList()
            val result = mutableListOf<T>()
            while (true) {
                result += block()
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        fun string(): String {
            skipWhitespace()
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                val character = text[index++]
                when (character) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(index < text.length) { "Invalid JSON escape" }
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            else -> throw IllegalArgumentException("Unsupported JSON escape")
                        }
                    }
                    else -> result.append(character)
                }
            }
            error("Unterminated JSON string")
        }

        fun double(): Double = token().toDouble().also { require(it.isFinite()) { "JSON number must be finite" } }

        fun int(): Int = double().toInt()

        fun boolean(): Boolean = when (token()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid JSON boolean")
        }

        fun skipValue() {
            skipWhitespace()
            when (peek()) {
                '{' -> objectFields { skipValue() }
                '[' -> array { skipValue() }
                '"' -> string()
                't', 'f' -> boolean()
                else -> token()
            }
        }

        fun ensureEnd() {
            skipWhitespace()
            require(index == text.length) { "Unexpected trailing JSON data" }
        }

        private fun token(): String {
            skipWhitespace()
            val start = index
            while (index < text.length && text[index] !in "{}[],: \t\r\n") index++
            require(start != index) { "Missing JSON value" }
            return text.substring(start, index)
        }

        private fun peek(): Char {
            skipWhitespace()
            require(index < text.length) { "Missing JSON value" }
            return text[index]
        }

        private fun expect(expected: Char) {
            skipWhitespace()
            require(index < text.length && text[index] == expected) { "Expected JSON '$expected'" }
            index++
        }

        private fun consume(value: Char): Boolean {
            skipWhitespace()
            if (index < text.length && text[index] == value) {
                index++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }
}
