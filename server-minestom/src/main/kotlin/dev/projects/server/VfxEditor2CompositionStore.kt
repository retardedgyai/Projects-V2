package dev.projects.server

import dev.projects.protocol.VFX_EDITOR_2_MAX_SAVED_COMPOSITIONS
import dev.projects.protocol.VfxEditor2Appearance
import dev.projects.protocol.VfxEditor2BoxMode
import dev.projects.protocol.VfxEditor2Composition
import dev.projects.protocol.VfxEditor2Direction
import dev.projects.protocol.VfxEditor2Effect
import dev.projects.protocol.VfxEditor2EffectType
import dev.projects.protocol.VfxEditor2ParticleType
import dev.projects.protocol.VfxEditor2Shape
import dev.projects.protocol.VfxEditor2Transform
import dev.projects.protocol.isSafeVfxEditor2CompositionName
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val VFX_EDITOR_2_STORE_SCHEMA_VERSION = 1

data class VfxEditor2StoreSaveResult(
    val success: Boolean,
    val overwritten: Boolean,
    val message: String,
)

/** Editor2-only authoring storage. Runtime class bindings never read this file. */
class VfxEditor2CompositionStore(private val file: Path) {
    private val compositions = linkedMapOf<String, VfxEditor2Composition>()

    init {
        readFromDisk()
    }

    @Synchronized
    fun list(): List<String> = compositions.keys.sorted()

    @Synchronized
    fun load(name: String): VfxEditor2Composition? = compositions[name]?.withoutSolo()

    @Synchronized
    fun save(composition: VfxEditor2Composition): VfxEditor2StoreSaveResult {
        if (!isSafeVfxEditor2CompositionName(composition.name)) {
            return VfxEditor2StoreSaveResult(false, false, "Composition name is invalid")
        }
        val normalized = runCatching { composition.normalizedForStorage() }.getOrElse {
            return VfxEditor2StoreSaveResult(false, false, "Composition data is invalid")
        }
        val overwritten = compositions.containsKey(normalized.name)
        if (!overwritten && compositions.size >= VFX_EDITOR_2_MAX_SAVED_COMPOSITIONS) {
            return VfxEditor2StoreSaveResult(false, false, "Composition storage is full")
        }
        val previous = compositions[normalized.name]
        compositions[normalized.name] = normalized
        if (!writeToDisk()) {
            if (previous == null) compositions.remove(normalized.name) else compositions[normalized.name] = previous
            return VfxEditor2StoreSaveResult(false, overwritten, "Could not write composition storage")
        }
        return VfxEditor2StoreSaveResult(
            success = true,
            overwritten = overwritten,
            message = if (overwritten) "Saved '${normalized.name}' (overwritten)" else "Saved '${normalized.name}'",
        )
    }

    private fun readFromDisk() {
        if (!Files.isRegularFile(file)) return
        val loaded = runCatching {
            val root = JsonParser(Files.readString(file, StandardCharsets.UTF_8)).parse().asObject()
            require(root.int("schemaVersion") == VFX_EDITOR_2_STORE_SCHEMA_VERSION) {
                "Unknown VFX Editor 2 composition schema"
            }
            val values = root.array("compositions")
            require(values.size <= VFX_EDITOR_2_MAX_SAVED_COMPOSITIONS) {
                "Too many saved VFX Editor 2 compositions"
            }
            values.mapNotNull { value -> runCatching { parseComposition(value) }.getOrNull() }
        }.getOrElse { emptyList() }
        compositions.clear()
        loaded.forEach { compositions[it.name] = it.normalizedForStorage() }
    }

    private fun writeToDisk(): Boolean = runCatching {
        val parent = file.parent ?: Path.of(".")
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, "${file.fileName}.", ".tmp")
        try {
            Files.writeString(temporary, serialize(), StandardCharsets.UTF_8)
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        true
    }.getOrDefault(false)

    private fun serialize(): String = buildString {
        append("{\"schemaVersion\":").append(VFX_EDITOR_2_STORE_SCHEMA_VERSION)
        append(",\"compositions\":[")
        compositions.values.sortedBy { it.name }.forEachIndexed { index, composition ->
            if (index > 0) append(',')
            appendComposition(this, composition)
        }
        append("]}")
    }

    companion object {
        private fun appendComposition(json: StringBuilder, composition: VfxEditor2Composition) {
            json.append("{\"name\":")
            appendString(json, composition.name)
            json.append(",\"timelineLengthTicks\":").append(composition.timelineLengthTicks)
            json.append(",\"effects\":[")
            composition.effects.forEachIndexed { index, effect ->
                if (index > 0) json.append(',')
                appendEffect(json, effect)
            }
            json.append("]}")
        }

        private fun appendEffect(json: StringBuilder, effect: VfxEditor2Effect) {
            json.append("{\"id\":").append(effect.id)
            json.append(",\"name\":")
            appendString(json, effect.name)
            json.append(",\"type\":")
            appendString(json, effect.type.name)
            json.append(",\"enabled\":").append(effect.enabled)
            json.append(",\"startTick\":").append(effect.startTick)
            json.append(",\"durationTicks\":").append(effect.durationTicks)
            json.append(",\"transform\":")
            appendTransform(json, effect.transform)
            json.append(",\"appearance\":")
            appendAppearance(json, effect.appearance)
            json.append(",\"shape\":")
            appendShape(json, effect.shape)
            json.append('}')
        }

        private fun appendTransform(json: StringBuilder, transform: VfxEditor2Transform) {
            json.append("{\"forward\":").append(transform.forward)
                .append(",\"side\":").append(transform.side)
                .append(",\"height\":").append(transform.height)
                .append(",\"yaw\":").append(transform.yaw)
                .append(",\"pitch\":").append(transform.pitch)
                .append(",\"roll\":").append(transform.roll)
                .append('}')
        }

        private fun appendAppearance(json: StringBuilder, appearance: VfxEditor2Appearance) {
            json.append("{\"color\":").append(appearance.color)
                .append(",\"particleSize\":").append(appearance.particleSize)
                .append(",\"density\":").append(appearance.density)
                .append(",\"particleType\":")
            appendString(json, appearance.particleType.name)
            json.append('}')
        }

        private fun appendShape(json: StringBuilder, shape: VfxEditor2Shape) {
            json.append("{\"type\":")
            appendString(json, shape.type.name)
            when (shape) {
                is VfxEditor2Shape.ArcSlash -> json.append(",\"length\":").append(shape.length)
                    .append(",\"arcDegrees\":").append(shape.arcDegrees)
                    .append(",\"curvature\":").append(shape.curvature)
                    .append(",\"thickness\":").append(shape.thickness)
                is VfxEditor2Shape.StraightSlash -> json.append(",\"length\":").append(shape.length)
                    .append(",\"thickness\":").append(shape.thickness)
                is VfxEditor2Shape.Ring -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"arcDegrees\":").append(shape.arcDegrees)
                    .append(",\"thickness\":").append(shape.thickness)
                is VfxEditor2Shape.Burst -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"count\":").append(shape.count)
                    .append(",\"spread\":").append(shape.spread)
                    .append(",\"speed\":").append(shape.speed)
                    .append(",\"seed\":").append(shape.seed)
                is VfxEditor2Shape.Bezier -> json.append(",\"length\":").append(shape.length)
                    .append(",\"controlForward\":").append(shape.controlForward)
                    .append(",\"controlSide\":").append(shape.controlSide)
                    .append(",\"controlHeight\":").append(shape.controlHeight)
                    .append(",\"endSide\":").append(shape.endSide)
                    .append(",\"endHeight\":").append(shape.endHeight)
                    .append(",\"thickness\":").append(shape.thickness)
                is VfxEditor2Shape.Wave -> json.append(",\"length\":").append(shape.length)
                    .append(",\"amplitude\":").append(shape.amplitude)
                    .append(",\"waves\":").append(shape.waves)
                    .append(",\"phaseDegrees\":").append(shape.phaseDegrees)
                    .append(",\"thickness\":").append(shape.thickness)
                is VfxEditor2Shape.Lightning -> json.append(",\"length\":").append(shape.length)
                    .append(",\"jitter\":").append(shape.jitter)
                    .append(",\"hops\":").append(shape.hops)
                    .append(",\"seed\":").append(shape.seed)
                is VfxEditor2Shape.Spiral -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"length\":").append(shape.length)
                    .append(",\"turns\":").append(shape.turns)
                    .append(",\"angleOffsetDegrees\":").append(shape.angleOffsetDegrees)
                    .append(",\"reverse\":").append(shape.reverse)
                is VfxEditor2Shape.Helix -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"length\":").append(shape.length)
                    .append(",\"turns\":").append(shape.turns)
                    .append(",\"phaseDegrees\":").append(shape.phaseDegrees)
                is VfxEditor2Shape.Disk -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"innerRadius\":").append(shape.innerRadius)
                is VfxEditor2Shape.Sector -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"angleDegrees\":").append(shape.angleDegrees)
                    .append(",\"innerRadius\":").append(shape.innerRadius)
                is VfxEditor2Shape.Grid -> json.append(",\"width\":").append(shape.width)
                    .append(",\"height\":").append(shape.height)
                    .append(",\"rows\":").append(shape.rows)
                is VfxEditor2Shape.Sphere -> json.append(",\"radius\":").append(shape.radius)
                is VfxEditor2Shape.Orb -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"count\":").append(shape.count)
                    .append(",\"seed\":").append(shape.seed)
                is VfxEditor2Shape.Dome -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"direction\":")
                    .also { appendString(json, shape.direction.name) }
                is VfxEditor2Shape.Cylinder -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"height\":").append(shape.height)
                    .append(",\"count\":").append(shape.count)
                    .append(",\"shell\":").append(shape.shell)
                is VfxEditor2Shape.Cone -> json.append(",\"length\":").append(shape.length)
                    .append(",\"radius\":").append(shape.radius)
                    .append(",\"angleDegrees\":").append(shape.angleDegrees)
                is VfxEditor2Shape.Box -> json.append(",\"width\":").append(shape.width)
                    .append(",\"height\":").append(shape.height)
                    .append(",\"depth\":").append(shape.depth)
                    .append(",\"mode\":")
                    .also { appendString(json, shape.mode.name) }
                is VfxEditor2Shape.Torus -> json.append(",\"majorRadius\":").append(shape.majorRadius)
                    .append(",\"tubeRadius\":").append(shape.tubeRadius)
                is VfxEditor2Shape.Star -> json.append(",\"points\":").append(shape.points)
                    .append(",\"radius\":").append(shape.radius)
                    .append(",\"innerRadius\":").append(shape.innerRadius)
                    .append(",\"sharpness\":").append(shape.sharpness)
                is VfxEditor2Shape.Cross -> json.append(",\"size\":").append(shape.size)
                    .append(",\"angleDegrees\":").append(shape.angleDegrees)
                    .append(",\"thickness\":").append(shape.thickness)
                is VfxEditor2Shape.Shockwave -> json.append(",\"startRadius\":").append(shape.startRadius)
                    .append(",\"endRadius\":").append(shape.endRadius)
                is VfxEditor2Shape.Vortex -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"height\":").append(shape.height)
                    .append(",\"turns\":").append(shape.turns)
                    .append(",\"direction\":")
                    .also { appendString(json, shape.direction.name) }
                is VfxEditor2Shape.Tornado -> json.append(",\"bottomRadius\":").append(shape.bottomRadius)
                    .append(",\"topRadius\":").append(shape.topRadius)
                    .append(",\"height\":").append(shape.height)
                    .append(",\"turns\":").append(shape.turns)
                is VfxEditor2Shape.Fountain -> json.append(",\"radius\":").append(shape.radius)
                    .append(",\"height\":").append(shape.height)
                    .append(",\"spreadDegrees\":").append(shape.spreadDegrees)
                    .append(",\"count\":").append(shape.count)
                is VfxEditor2Shape.SphereBurst -> json.append(",\"spawnRadius\":").append(shape.spawnRadius)
                    .append(",\"count\":").append(shape.count)
                    .append(",\"speed\":").append(shape.speed)
                    .append(",\"variance\":").append(shape.variance)
                    .append(",\"seed\":").append(shape.seed)
                is VfxEditor2Shape.ConeBurst -> json.append(",\"length\":").append(shape.length)
                    .append(",\"radius\":").append(shape.radius)
                    .append(",\"angleDegrees\":").append(shape.angleDegrees)
                    .append(",\"count\":").append(shape.count)
                    .append(",\"speed\":").append(shape.speed)
                    .append(",\"seed\":").append(shape.seed)
            }
            json.append('}')
        }

        private fun appendString(json: StringBuilder, value: String) {
            json.append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> json.append("\\\\")
                    '"' -> json.append("\\\"")
                    '\b' -> json.append("\\b")
                    '\u000C' -> json.append("\\f")
                    '\n' -> json.append("\\n")
                    '\r' -> json.append("\\r")
                    '\t' -> json.append("\\t")
                    else -> if (character.code < 0x20) {
                        json.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        json.append(character)
                    }
                }
            }
            json.append('"')
        }

        private fun parseComposition(value: JsonValue): VfxEditor2Composition {
            val json = value.asObject()
            val name = json.string("name")
            val effects = json.array("effects").map(::parseEffect)
            return VfxEditor2Composition.clamped(name, json.int("timelineLengthTicks"), effects)
        }

        private fun parseEffect(value: JsonValue): VfxEditor2Effect {
            val json = value.asObject()
            val type = enumValue<VfxEditor2EffectType>(json.string("type"))
            val transformJson = json.objectValue("transform")
            val appearanceJson = json.objectValue("appearance")
            val particleType = enumValue<VfxEditor2ParticleType>(appearanceJson.string("particleType"))
            return VfxEditor2Effect(
                id = json.long("id"),
                name = json.string("name"),
                type = type,
                shape = parseShape(json.objectValue("shape"), type),
                transform = VfxEditor2Transform.clamped(
                    transformJson.double("forward"), transformJson.double("side"), transformJson.double("height"),
                    transformJson.double("yaw"), transformJson.double("pitch"), transformJson.double("roll"),
                ),
                appearance = VfxEditor2Appearance.clamped(
                    appearanceJson.int("color"), appearanceJson.double("particleSize"),
                    appearanceJson.double("density"), particleType,
                ),
                enabled = json.boolean("enabled"),
                solo = false,
                startTick = json.int("startTick"),
                durationTicks = json.int("durationTicks"),
            )
        }

        private fun parseShape(json: JsonObject, type: VfxEditor2EffectType): VfxEditor2Shape {
            val storedType = enumValue<VfxEditor2EffectType>(json.string("type"))
            require(storedType == type) { "Stored VFX Editor 2 shape type does not match effect type" }
            return when (type) {
                VfxEditor2EffectType.ARC_SLASH -> VfxEditor2Shape.ArcSlash.clamped(json.double("length"), json.double("arcDegrees"), json.double("curvature"), json.double("thickness"))
                VfxEditor2EffectType.STRAIGHT_SLASH -> VfxEditor2Shape.StraightSlash.clamped(json.double("length"), json.double("thickness"))
                VfxEditor2EffectType.RING -> VfxEditor2Shape.Ring.clamped(json.double("radius"), json.double("arcDegrees"), json.double("thickness"))
                VfxEditor2EffectType.BURST -> VfxEditor2Shape.Burst.clamped(json.double("radius"), json.int("count"), json.double("spread"), json.double("speed"), json.long("seed"))
                VfxEditor2EffectType.BEZIER -> VfxEditor2Shape.Bezier.clamped(json.double("length"), json.double("controlForward"), json.double("controlSide"), json.double("controlHeight"), json.double("endSide"), json.double("endHeight"), json.double("thickness"))
                VfxEditor2EffectType.WAVE -> VfxEditor2Shape.Wave.clamped(json.double("length"), json.double("amplitude"), json.int("waves"), json.double("phaseDegrees"), json.double("thickness"))
                VfxEditor2EffectType.LIGHTNING -> VfxEditor2Shape.Lightning.clamped(json.double("length"), json.double("jitter"), json.int("hops"), json.long("seed"))
                VfxEditor2EffectType.SPIRAL -> VfxEditor2Shape.Spiral.clamped(json.double("radius"), json.double("length"), json.double("turns"), json.double("angleOffsetDegrees"), json.boolean("reverse"))
                VfxEditor2EffectType.HELIX -> VfxEditor2Shape.Helix.clamped(json.double("radius"), json.double("length"), json.double("turns"), json.double("phaseDegrees"))
                VfxEditor2EffectType.DISK -> VfxEditor2Shape.Disk.clamped(json.double("radius"), json.double("innerRadius"))
                VfxEditor2EffectType.SECTOR -> VfxEditor2Shape.Sector.clamped(json.double("radius"), json.double("angleDegrees"), json.double("innerRadius"))
                VfxEditor2EffectType.GRID -> VfxEditor2Shape.Grid.clamped(json.double("width"), json.double("height"), json.int("rows"))
                VfxEditor2EffectType.SPHERE -> VfxEditor2Shape.Sphere.clamped(json.double("radius"))
                VfxEditor2EffectType.ORB -> VfxEditor2Shape.Orb.clamped(json.double("radius"), json.int("count"), json.long("seed"))
                VfxEditor2EffectType.DOME -> VfxEditor2Shape.Dome.clamped(json.double("radius"), enumValue(json.string("direction")))
                VfxEditor2EffectType.CYLINDER -> VfxEditor2Shape.Cylinder.clamped(json.double("radius"), json.double("height"), json.int("count"), json.boolean("shell"))
                VfxEditor2EffectType.CONE -> VfxEditor2Shape.Cone.clamped(json.double("length"), json.double("radius"), json.double("angleDegrees"))
                VfxEditor2EffectType.BOX -> VfxEditor2Shape.Box.clamped(json.double("width"), json.double("height"), json.double("depth"), enumValue(json.string("mode")))
                VfxEditor2EffectType.TORUS -> VfxEditor2Shape.Torus.clamped(json.double("majorRadius"), json.double("tubeRadius"))
                VfxEditor2EffectType.STAR_FLOWER -> VfxEditor2Shape.Star.clamped(json.int("points"), json.double("radius"), json.double("innerRadius"), json.double("sharpness"))
                VfxEditor2EffectType.CROSS -> VfxEditor2Shape.Cross.clamped(json.double("size"), json.double("angleDegrees"), json.double("thickness"))
                VfxEditor2EffectType.SHOCKWAVE -> VfxEditor2Shape.Shockwave.clamped(json.double("startRadius"), json.double("endRadius"))
                VfxEditor2EffectType.VORTEX -> VfxEditor2Shape.Vortex.clamped(json.double("radius"), json.double("height"), json.double("turns"), enumValue(json.string("direction")))
                VfxEditor2EffectType.TORNADO -> VfxEditor2Shape.Tornado.clamped(json.double("bottomRadius"), json.double("topRadius"), json.double("height"), json.double("turns"))
                VfxEditor2EffectType.FOUNTAIN -> VfxEditor2Shape.Fountain.clamped(json.double("radius"), json.double("height"), json.double("spreadDegrees"), json.int("count"))
                VfxEditor2EffectType.SPHERE_BURST -> VfxEditor2Shape.SphereBurst.clamped(json.double("spawnRadius"), json.int("count"), json.double("speed"), json.double("variance"), json.long("seed"))
                VfxEditor2EffectType.CONE_BURST -> VfxEditor2Shape.ConeBurst.clamped(json.double("length"), json.double("radius"), json.double("angleDegrees"), json.int("count"), json.double("speed"), json.long("seed"))
            }
        }

        private inline fun <reified T : Enum<T>> enumValue(value: String): T = enumValues<T>().first { it.name == value }
    }
}

private sealed interface JsonValue
private data class JsonObject(val values: Map<String, JsonValue>) : JsonValue
private data class JsonArray(val values: List<JsonValue>) : JsonValue
private data class JsonString(val value: String) : JsonValue
private data class JsonNumber(val raw: String) : JsonValue
private data class JsonBoolean(val value: Boolean) : JsonValue
private data object JsonNull : JsonValue

private fun JsonValue.asObject(): JsonObject = this as? JsonObject ?: error("Expected JSON object")
private fun JsonObject.value(key: String): JsonValue = values[key] ?: error("Missing JSON field: $key")
private fun JsonObject.string(key: String): String = (value(key) as? JsonString)?.value ?: error("Expected JSON string: $key")
private fun JsonObject.int(key: String): Int = (value(key) as? JsonNumber)?.raw?.toInt() ?: error("Expected JSON integer: $key")
private fun JsonObject.long(key: String): Long = (value(key) as? JsonNumber)?.raw?.toLong() ?: error("Expected JSON long: $key")
private fun JsonObject.double(key: String): Double = (value(key) as? JsonNumber)?.raw?.toDouble()?.let {
    require(it.isFinite())
    it
} ?: error("Expected JSON number: $key")
private fun JsonObject.boolean(key: String): Boolean = (value(key) as? JsonBoolean)?.value ?: error("Expected JSON boolean: $key")
private fun JsonObject.objectValue(key: String): JsonObject = value(key).asObject()
private fun JsonObject.array(key: String): List<JsonValue> = (value(key) as? JsonArray)?.values ?: error("Expected JSON array: $key")

private class JsonParser(private val source: String) {
    private var index = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == source.length) { "Trailing JSON data" }
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON" }
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't' -> { expectLiteral("true"); JsonBoolean(true) }
            'f' -> { expectLiteral("false"); JsonBoolean(false) }
            'n' -> { expectLiteral("null"); JsonNull }
            '-', in '0'..'9' -> JsonNumber(parseNumber())
            else -> error("Unexpected JSON character at $index")
        }
    }

    private fun parseObject(): JsonObject {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (takeIf('}')) return JsonObject(values)
        while (true) {
            skipWhitespace()
            require(index < source.length && source[index] == '"') { "Expected JSON object key" }
            val key = parseString()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            if (takeIf('}')) return JsonObject(values)
            expect(',')
        }
    }

    private fun parseArray(): JsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (takeIf(']')) return JsonArray(values)
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (takeIf(']')) return JsonArray(values)
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    require(index < source.length) { "Incomplete JSON escape" }
                    when (val escaped = source[index++]) {
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        '/' -> result.append('/')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index + 4 <= source.length) { "Incomplete JSON unicode escape" }
                            val code = source.substring(index, index + 4).toInt(16)
                            result.append(code.toChar())
                            index += 4
                        }
                        else -> error("Invalid JSON escape: $escaped")
                    }
                }
                else -> {
                    require(character.code >= 0x20) { "Control character in JSON string" }
                    result.append(character)
                }
            }
        }
        error("Unterminated JSON string")
    }

    private fun parseNumber(): String {
        val start = index
        if (takeIf('-')) Unit
        require(index < source.length) { "Incomplete JSON number" }
        if (takeIf('0')) {
            require(index >= source.length || source[index] !in '0'..'9') { "Invalid JSON number" }
        } else {
            require(index < source.length && source[index] in '1'..'9') { "Invalid JSON number" }
            while (index < source.length && source[index].isDigit()) index++
        }
        if (takeIf('.')) {
            val fractionStart = index
            while (index < source.length && source[index].isDigit()) index++
            require(index > fractionStart) { "Invalid JSON fraction" }
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            val exponentStart = index
            while (index < source.length && source[index].isDigit()) index++
            require(index > exponentStart) { "Invalid JSON exponent" }
        }
        return source.substring(start, index)
    }

    private fun expect(character: Char) {
        require(index < source.length && source[index] == character) { "Expected '$character' in JSON" }
        index++
    }

    private fun expectLiteral(literal: String) {
        require(source.regionMatches(index, literal, 0, literal.length)) { "Expected JSON literal" }
        index += literal.length
    }

    private fun takeIf(character: Char): Boolean {
        if (index < source.length && source[index] == character) {
            index++
            return true
        }
        return false
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }
}
