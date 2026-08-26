package dev.projects.server

import dev.projects.protocol.VFX_EDITOR_2_MAX_BINDINGS
import dev.projects.protocol.isSafeVfxEditor2CompositionName
import dev.projects.protocol.isSafeVfxEditor2TargetId
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val VFX_EDITOR_2_BINDING_SCHEMA_VERSION = 1

data class VfxEditor2BindingOperationResult(
    val success: Boolean,
    val message: String,
)

/** Persistent target-to-name references for Editor 2. Composition data is never copied here. */
class VfxEditor2BindingStore(
    private val file: Path,
    private val catalog: VfxEditor2TargetCatalog = VfxEditor2TargetCatalog,
    private val warn: (String) -> Unit = { System.err.println(it) },
) {
    private val bindings = linkedMapOf<String, String>()

    init {
        readFromDisk()
    }

    @Synchronized
    fun snapshot(): Map<String, String> = bindings.toMap()

    @Synchronized
    fun bindingFor(targetId: String): String? = bindings[targetId]

    @Synchronized
    fun apply(
        targetId: String,
        compositionName: String,
        compositionExists: (String) -> Boolean,
    ): VfxEditor2BindingOperationResult {
        if (!catalog.contains(targetId)) {
            return VfxEditor2BindingOperationResult(false, "Unknown VFX Editor 2 target '$targetId'")
        }
        if (!isSafeVfxEditor2CompositionName(compositionName)) {
            return VfxEditor2BindingOperationResult(false, "Composition name is invalid")
        }
        val exists = runCatching { compositionExists(compositionName) }.getOrDefault(false)
        if (!exists) {
            return VfxEditor2BindingOperationResult(false, "Composition '$compositionName' was not found")
        }
        if (bindings[targetId] == compositionName) {
            return VfxEditor2BindingOperationResult(true, "Binding already uses '$compositionName'")
        }

        val previous = bindings[targetId]
        bindings[targetId] = compositionName
        if (!writeToDisk()) {
            if (previous == null) bindings.remove(targetId) else bindings[targetId] = previous
            return VfxEditor2BindingOperationResult(false, "Could not write VFX Editor 2 binding storage")
        }
        return VfxEditor2BindingOperationResult(true, "Binding updated to '$compositionName'")
    }

    @Synchronized
    fun clear(targetId: String): VfxEditor2BindingOperationResult {
        if (!catalog.contains(targetId)) {
            return VfxEditor2BindingOperationResult(false, "Unknown VFX Editor 2 target '$targetId'")
        }
        val previous = bindings.remove(targetId)
            ?: return VfxEditor2BindingOperationResult(true, "Binding already uses the default VFX")
        if (!writeToDisk()) {
            bindings[targetId] = previous
            return VfxEditor2BindingOperationResult(false, "Could not write VFX Editor 2 binding storage")
        }
        return VfxEditor2BindingOperationResult(true, "Restored default VFX")
    }

    private fun readFromDisk() {
        if (!Files.isRegularFile(file)) return
        val loaded = runCatching {
            val root = BindingJsonParser(Files.readString(file, StandardCharsets.UTF_8)).parse().asObject()
            require(root.int("schemaVersion") == VFX_EDITOR_2_BINDING_SCHEMA_VERSION) {
                "Unknown VFX Editor 2 binding schema"
            }
            val values = root.objectValue("bindings").values
            require(values.size <= VFX_EDITOR_2_MAX_BINDINGS) {
                "Too many VFX Editor 2 bindings"
            }
            values.mapNotNull { (targetId, value) ->
                val compositionName = (value as? BindingJsonString)?.value
                    ?: error("Binding composition must be a string")
                if (!catalog.contains(targetId) || !isSafeVfxEditor2TargetId(targetId)) {
                    warn("Ignoring unknown VFX Editor 2 binding target '$targetId'")
                    null
                } else if (!isSafeVfxEditor2CompositionName(compositionName)) {
                    warn("Ignoring invalid VFX Editor 2 binding name for '$targetId'")
                    null
                } else {
                    targetId to compositionName
                }
            }
        }.getOrElse { error ->
            warn(
                "Ignoring malformed VFX Editor 2 binding storage at $file: " +
                    (error.message ?: error.javaClass.simpleName),
            )
            emptyList()
        }
        bindings.clear()
        loaded.forEach { (targetId, compositionName) -> bindings[targetId] = compositionName }
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
        append("{\"schemaVersion\":").append(VFX_EDITOR_2_BINDING_SCHEMA_VERSION)
        append(",\"bindings\":{")
        bindings.toSortedMap().entries.forEachIndexed { index, (targetId, compositionName) ->
            if (index > 0) append(',')
            appendString(this, targetId)
            append(':')
            appendString(this, compositionName)
        }
        append("}}")
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
}

private sealed interface BindingJsonValue
private data class BindingJsonObject(val values: Map<String, BindingJsonValue>) : BindingJsonValue
private data class BindingJsonArray(val values: List<BindingJsonValue>) : BindingJsonValue
private data class BindingJsonString(val value: String) : BindingJsonValue
private data class BindingJsonNumber(val raw: String) : BindingJsonValue
private data class BindingJsonBoolean(val value: Boolean) : BindingJsonValue
private data object BindingJsonNull : BindingJsonValue

private fun BindingJsonValue.asObject(): BindingJsonObject = this as? BindingJsonObject ?: error("Expected JSON object")
private fun BindingJsonObject.value(key: String): BindingJsonValue = values[key] ?: error("Missing JSON field: $key")
private fun BindingJsonObject.int(key: String): Int =
    (value(key) as? BindingJsonNumber)?.raw?.toInt() ?: error("Expected JSON integer: $key")
private fun BindingJsonObject.objectValue(key: String): BindingJsonObject = value(key).asObject()

private class BindingJsonParser(private val source: String) {
    private var index = 0

    fun parse(): BindingJsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == source.length) { "Trailing JSON data" }
        return value
    }

    private fun parseValue(): BindingJsonValue {
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON" }
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> BindingJsonString(parseString())
            't' -> {
                expectLiteral("true")
                BindingJsonBoolean(true)
            }
            'f' -> {
                expectLiteral("false")
                BindingJsonBoolean(false)
            }
            'n' -> {
                expectLiteral("null")
                BindingJsonNull
            }
            '-', in '0'..'9' -> BindingJsonNumber(parseNumber())
            else -> error("Unexpected JSON character at $index")
        }
    }

    private fun parseObject(): BindingJsonObject {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, BindingJsonValue>()
        if (takeIf('}')) return BindingJsonObject(values)
        while (true) {
            skipWhitespace()
            require(index < source.length && source[index] == '"') { "Expected JSON object key" }
            val key = parseString()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            if (takeIf('}')) return BindingJsonObject(values)
            expect(',')
        }
    }

    private fun parseArray(): BindingJsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<BindingJsonValue>()
        if (takeIf(']')) return BindingJsonArray(values)
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (takeIf(']')) return BindingJsonArray(values)
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
