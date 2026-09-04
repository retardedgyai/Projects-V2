package dev.projects.server.coreloop

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

sealed interface CoreRepositoryLoad {
    data object Missing : CoreRepositoryLoad
    data class Loaded(val account: CoreAccount) : CoreRepositoryLoad
    data class Invalid(val reason: String) : CoreRepositoryLoad
}
sealed interface CoreRepositorySave {
    data object Saved : CoreRepositorySave
    data object Conflict : CoreRepositorySave
    data class Failed(val reason: String) : CoreRepositorySave
}

/** Dedicated core-loop directory. Existing progression files are never opened or migrated. */
class CoreAccountRepository(
    directory: Path,
    private val atomicReplace: (Path, Path) -> Unit = { source, target -> Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING); Unit },
) {
    private val directory = directory.toAbsolutePath().normalize()
    private val blocked = mutableSetOf<UUID>()

    @Synchronized
    fun load(playerId: UUID): CoreRepositoryLoad {
        if (playerId in blocked) return CoreRepositoryLoad.Invalid("このデータは破損または未対応の形式です")
        return try {
            requireSafeDirectory()
            val file = fileFor(playerId)
            if (!Files.exists(file, NOFOLLOW_LINKS)) CoreRepositoryLoad.Missing else {
                require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "保存先が通常ファイルではありません" }
                require(Files.size(file) <= MAX_FILE_BYTES) { "保存データが上限を超えています" }
                val bytes = Files.readAllBytes(file)
                require(bytes.size <= MAX_FILE_BYTES)
                val text = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                CoreRepositoryLoad.Loaded(CoreAccountCodec.decode(text, playerId))
            }
        } catch (failure: Exception) {
            blocked += playerId
            CoreRepositoryLoad.Invalid(failure.message?.take(256) ?: "保存データを読み込めません")
        }
    }

    @Synchronized
    fun commit(expectedRevision: Long, next: CoreAccount): CoreRepositorySave {
        if (next.playerId in blocked) return CoreRepositorySave.Failed("破損データを上書きできません")
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE || next.revision != expectedRevision + 1) return CoreRepositorySave.Conflict
        var temporary: Path? = null
        var encoded: ByteArray? = null
        return try {
            requireSafeDirectory()
            Files.createDirectories(directory)
            val lockPath = directory.resolve("${next.playerId}.lock")
            require(!Files.exists(lockPath, NOFOLLOW_LINKS) || Files.isRegularFile(lockPath, NOFOLLOW_LINKS))
            FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    val before = load(next.playerId)
                    val actual = when (before) {
                        CoreRepositoryLoad.Missing -> 0L
                        is CoreRepositoryLoad.Loaded -> before.account.revision
                        is CoreRepositoryLoad.Invalid -> return CoreRepositorySave.Failed(before.reason)
                    }
                    if (actual != expectedRevision) return CoreRepositorySave.Conflict
                    val bytes = CoreAccountCodec.encode(next).toByteArray(UTF_8)
                    require(bytes.size <= MAX_FILE_BYTES) { "保存データが上限を超えています" }
                    encoded = bytes
                    val temp = Files.createTempFile(directory, ".${next.playerId}.", ".tmp")
                    temporary = temp
                    FileChannel.open(temp, WRITE, TRUNCATE_EXISTING).use { output ->
                        val buffer = ByteBuffer.wrap(bytes)
                        while (buffer.hasRemaining()) output.write(buffer)
                        output.force(true)
                    }
                    atomicReplace(temp, fileFor(next.playerId))
                    CoreRepositorySave.Saved
                }
            }
        } catch (failure: Exception) {
            // If an implementation reported failure after the atomic rename, use the exact durable receipt.
            val expected = encoded
            val committed = expected != null && runCatching {
                val target = fileFor(next.playerId)
                Files.isRegularFile(target, NOFOLLOW_LINKS) && Files.size(target) == expected.size.toLong() &&
                    Files.readAllBytes(target).contentEquals(expected)
            }.getOrDefault(false)
            if (committed) CoreRepositorySave.Saved else CoreRepositorySave.Failed(failure.message?.take(256) ?: "保存に失敗しました")
        } finally {
            temporary?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun requireSafeDirectory() {
        var current: Path? = directory
        while (current != null) {
            require(!Files.isSymbolicLink(current)) { "シンボリックリンクの保存先は使用できません" }
            current = current.parent
        }
        require(!Files.exists(directory, NOFOLLOW_LINKS) || Files.isDirectory(directory, NOFOLLOW_LINKS))
    }
    private fun fileFor(playerId: UUID): Path = directory.resolve("$playerId.account")
    companion object { const val MAX_FILE_BYTES = 8 * 1024 * 1024 }
}

/** Strict UTF-8 schema, canonical rows, and whole-record checksum; display text is never authority. */
internal object CoreAccountCodec {
    fun encode(account: CoreAccount): String {
        val body = buildString {
            append("PROJECTS_CORE_LOOP\t1\t${account.playerId}\t${account.revision}\n")
            append("gear\t${account.weaponTier}\t${account.armorTier}\t${account.unlockedMapTier}\n")
            account.balances.entries.sortedWith(compareBy({ it.key.resource.ordinal }, { it.key.tier })).forEach { (key, amount) ->
                append("balance\t${key.resource.name}\t${key.tier}\t$amount\n")
            }
            account.maps.forEach { append("map\t${mapFields(it)}\n") }
            account.activeRun?.let { append("run\t${it.id}\t${it.bossDefeated}\t${mapFields(it.map)}\n") }
            account.receipts.forEach { (id, receipt) -> append("receipt\t$id\t${receipt.fingerprint}\t${receipt.revision}\t${base64(receipt.message)}\n") }
            account.claimedSources.forEach { append("source\t${base64(it)}\n") }
        }
        return body + "checksum\t${digest(body)}\n"
    }

    fun decode(text: String, playerId: UUID): CoreAccount {
        require(text.endsWith('\n') && '\r' !in text) { "保存データの行形式が不正です" }
        val checksumAt = text.lastIndexOf("checksum\t")
        require(checksumAt >= 0)
        val body = text.substring(0, checksumAt)
        require(text.substring(checksumAt) == "checksum\t${digest(body)}\n") { "保存データの検証に失敗しました" }
        val rows = body.trimEnd('\n').split('\n').map { it.split('\t') }
        val header = rows.first()
        require(header.size == 4 && header[0] == "PROJECTS_CORE_LOOP" && header[1] == "1") { "未対応の保存形式です" }
        require(UUID.fromString(header[2]) == playerId) { "保存データのプレイヤーが一致しません" }
        val gear = rows.getOrNull(1) ?: error("装備データがありません")
        require(gear.size == 4 && gear[0] == "gear")
        val balances = linkedMapOf<CoreMaterial, Long>()
        val maps = mutableListOf<CoreOwnedMap>()
        var active: CoreActiveRun? = null
        val receipts = linkedMapOf<UUID, CoreReceipt>()
        val sources = linkedSetOf<String>()
        rows.drop(2).forEach { row -> when (row[0]) {
            "balance" -> {
                require(row.size == 4 && balances.size < CoreLoopCatalog.MAX_BALANCES)
                require(balances.put(CoreMaterial(CoreResource.valueOf(row[1]), row[2].toInt()), row[3].toLong()) == null)
            }
            "map" -> { require(maps.size < CoreLoopCatalog.MAX_MAPS); maps += readMap(row.drop(1)) }
            "run" -> {
                require(active == null && row.size == 7)
                val defeated = when (row[2]) { "true" -> true; "false" -> false; else -> error("Invalid boolean") }
                active = CoreActiveRun(UUID.fromString(row[1]), readMap(row.drop(3)), defeated)
            }
            "receipt" -> {
                require(row.size == 5 && receipts.size < CoreLoopCatalog.MAX_RECEIPTS)
                require(receipts.put(UUID.fromString(row[1]), CoreReceipt(row[2], row[3].toLong(), unbase64(row[4]))) == null)
            }
            "source" -> { require(row.size == 2 && sources.size < CoreLoopCatalog.MAX_SOURCES && sources.add(unbase64(row[1]))) }
            else -> error("未知の保存項目: ${row[0].take(32)}")
        } }
        return CoreAccount(playerId, header[3].toLong(), balances, gear[1].toInt(), gear[2].toInt(), gear[3].toInt(), maps, active, receipts, sources)
    }

    private fun mapFields(map: CoreOwnedMap): String = "${map.id}\t${map.seed}\t${map.tier}\t" +
        map.modifiers.joinToString(";") { "${it.discipline ?: "all"},${it.stat},${it.percent}" }
    private fun readMap(parts: List<String>): CoreOwnedMap {
        require(parts.size == 4)
        val modifiers = if (parts[3].isEmpty()) emptyList() else parts[3].split(';').map {
            val fields = it.split(','); require(fields.size == 3)
            CoreMapModifier(fields[0].takeUnless { name -> name == "all" }, fields[1], fields[2].toInt())
        }
        return CoreOwnedMap(UUID.fromString(parts[0]), parts[1].toLong(), parts[2].toInt(), modifiers)
    }
    private fun base64(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(UTF_8))
    private fun unbase64(text: String): String = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(text))).toString()
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
}
