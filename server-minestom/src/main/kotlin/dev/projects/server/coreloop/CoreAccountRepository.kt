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
import java.nio.file.StandardOpenOption.CREATE_NEW
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
    fun load(playerId: UUID): CoreRepositoryLoad = try {
        exclusive { recoverTrade(); loadInternal(playerId) }
    } catch (failure: Exception) { CoreRepositoryLoad.Invalid("取引の復旧が必要です: ${failure.message?.take(120)}") }

    private fun loadInternal(playerId: UUID): CoreRepositoryLoad {
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
    fun commit(expectedRevision: Long, next: CoreAccount): CoreRepositorySave = try {
        exclusive { recoverTrade(); commitInternal(expectedRevision, next) }
    } catch (failure: Exception) { CoreRepositorySave.Failed(failure.message ?: "保存できません") }

    private fun commitInternal(expectedRevision: Long, next: CoreAccount): CoreRepositorySave {
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
                    val before = loadInternal(next.playerId)
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
                    preserveLegacyBackup(next.playerId)
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

    /** One durable intent commits both parties. Recovery runs before ANY subsequent read/write. */
    @Synchronized
    fun commitTrade(buyer: CoreAccount, seller: CoreAccount): CoreRepositorySave = try {
        exclusive {
            recoverTrade()
            require(buyer.playerId != seller.playerId)
            val pair = listOf(buyer, seller)
            if (pair.any { next -> (loadInternal(next.playerId) as? CoreRepositoryLoad.Loaded)?.account?.revision != next.revision - 1 })
                CoreRepositorySave.Conflict
            else {
                val body = pair.joinToString("\n", postfix = "\n") {
                    val bytes = CoreAccountCodec.encode(it).toByteArray(UTF_8)
                    require(bytes.size <= MAX_FILE_BYTES) { "保存データが上限を超えています" }
                    "${it.playerId}\t" + Base64.getEncoder().encodeToString(bytes)
                }
                durableReplace(directory.resolve("market.pending"), body.toByteArray(UTF_8))
                recoverTrade()
                CoreRepositorySave.Saved
            }
        }
    } catch (failure: Exception) { CoreRepositorySave.Failed("取引結果を復旧中です。再接続してください") }

    @Synchronized
    fun marketAccounts(): List<CoreAccount> = exclusive {
        recoverTrade()
        Files.newDirectoryStream(directory, "*.account").use { files -> files.mapNotNull { file ->
            val id = runCatching { UUID.fromString(file.fileName.toString().removeSuffix(".account")) }.getOrNull()
            id?.let { (loadInternal(it) as? CoreRepositoryLoad.Loaded)?.account }
        } }
    }

    private fun recoverTrade() {
        val pending = directory.resolve("market.pending")
        if (!Files.exists(pending, NOFOLLOW_LINKS)) return
        require(Files.isRegularFile(pending, NOFOLLOW_LINKS) && Files.size(pending) <= MAX_FILE_BYTES * 3L)
        val rows = Files.readString(pending, UTF_8).trimEnd('\n').split('\n')
        require(rows.size == 2)
        val next = rows.map { row ->
            val parts = row.split('\t'); require(parts.size == 2)
            CoreAccountCodec.decode(String(Base64.getDecoder().decode(parts[1]), UTF_8), UUID.fromString(parts[0]))
        }
        require(next[0].playerId != next[1].playerId)
        // Validate BOTH before installing either; never overwrite an unrelated later revision.
        next.forEach { target ->
            val current = (loadInternal(target.playerId) as? CoreRepositoryLoad.Loaded)?.account ?: error("取引元の保存がありません")
            require(current.revision == target.revision - 1 || CoreAccountCodec.encode(current) == CoreAccountCodec.encode(target))
        }
        next.forEach { target ->
            preserveLegacyBackup(target.playerId)
            durableReplace(fileFor(target.playerId), CoreAccountCodec.encode(target).toByteArray(UTF_8))
        }
        Files.delete(pending)
    }

    private fun durableReplace(target: Path, bytes: ByteArray) {
        val temp = Files.createTempFile(directory, ".market-", ".tmp")
        try {
            FileChannel.open(temp, WRITE, TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            atomicReplace(temp, target)
        } finally { Files.deleteIfExists(temp) }
    }

    private fun <T> exclusive(action: () -> T): T {
        requireSafeDirectory(); Files.createDirectories(directory)
        val lock = directory.resolve("market.lock")
        require(!Files.exists(lock, NOFOLLOW_LINKS) || Files.isRegularFile(lock, NOFOLLOW_LINKS))
        return FileChannel.open(lock, CREATE, WRITE).use { channel -> channel.lock().use { action() } }
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

    /** First v7 mutation preserves exact legacy bytes. Loading alone never writes or changes old rolls. */
    private fun preserveLegacyBackup(playerId: UUID) {
        val original = fileFor(playerId)
        if (!Files.exists(original, NOFOLLOW_LINKS)) return
        val bytes = Files.readAllBytes(original)
        val version = when {
            bytes.toString(UTF_8).startsWith("PROJECTS_CORE_LOOP\t1\t") -> 1
            bytes.toString(UTF_8).startsWith("PROJECTS_CORE_LOOP\t2\t") -> 2
            bytes.toString(UTF_8).startsWith("PROJECTS_CORE_LOOP\t3\t") -> 3
            bytes.toString(UTF_8).startsWith("PROJECTS_CORE_LOOP\t4\t") -> 4
            bytes.toString(UTF_8).startsWith("PROJECTS_CORE_LOOP\t5\t") -> 5
            bytes.toString(UTF_8).startsWith("PROJECTS_CORE_LOOP\t6\t") -> 6
            else -> return
        }
        val backup = directory.resolve("$playerId.account.v$version.bak")
        if (Files.exists(backup, NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(backup, NOFOLLOW_LINKS) && Files.size(backup) == bytes.size.toLong() &&
                Files.readAllBytes(backup).contentEquals(bytes)) { "旧形式のバックアップが既存データと一致しません" }
            return
        }
        FileChannel.open(backup, CREATE_NEW, WRITE).use { output ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) output.write(buffer)
            output.force(true)
        }
    }
    companion object { const val MAX_FILE_BYTES = 8 * 1024 * 1024 }
}

/** Strict UTF-8 schema, canonical rows, and whole-record checksum; display text is never authority. */
internal object CoreAccountCodec {
    fun encode(account: CoreAccount): String {
        val body = buildString {
            append("PROJECTS_CORE_LOOP\t7\t${account.playerId}\t${account.revision}\n")
            append("gear\t${account.weaponTier}\t${account.armorTier}\t${account.unlockedMapTier}\n")
            append("crafting\t${account.weaponRarity}\t${account.armorRarity}\t${account.craftingSeed}\n")
            append("enhancement\t${account.weaponEnhancement.level}\t${account.weaponEnhancement.failures}\t${account.armorEnhancement.level}\t${account.armorEnhancement.failures}\t${account.smithingXp}\n")
            append("economy\t${account.silver}\t${account.deliveryDay}\t${account.deliveries}\t${account.weaponBroken}\t${account.armorBroken}\n")
            append("identity\tWEAPON\t${identityFields(account.weaponIdentity)}\n")
            append("identity\tARMOR\t${identityFields(account.armorIdentity)}\n")
            account.storedGear.forEach { item ->
                append("stored-gear\t${identityFields(item.identity)}\t${item.slot}\t${item.tier}\t${item.rarity}\t${item.enhancement.level}\t${item.enhancement.failures}\t${item.legacy}\t${item.broken}\n")
                item.affixes.forEach { append("stored-affix\t${item.identity.id}\t${it.index}\t${affixFields(it.stone)}\n") }
            }
            append("survey\t${account.surveyPoints}\n")
            account.professions.entries.sortedBy { it.key.ordinal }.forEach { (key, p) -> append("profession\t$key\t${p.xp}\t${p.returnCredit}\n") }
            account.buyOrders.forEach { append("buy-order\t${it.id}\t${it.unitPrice}\t${it.remaining}\t${it.tier}\t${it.resource ?: ""}\t${it.slot ?: ""}\n") }
            account.dungeonRecords.toSortedMap().forEach { (tier, rank) -> append("dungeon-record\t$tier\t$rank\n") }
            account.activeRun?.dungeon?.let { append("dungeon-run\t${it.ascension}\t${it.stages}\t${it.roomsPerFloor}\t${it.rewardedStage}\n") }
            (account.storedGear.map { it.identity } + account.weaponIdentity + account.armorIdentity).forEach { append("gear-quality\t${it.id}\t${it.quality}\n") }
            account.offers.forEach { append("offer\t${it.id}\t${it.price}\t${it.material?.resource ?: ""}\t${it.material?.tier ?: 1}\t${it.quantity}\t${it.gearId ?: ""}\n") }
            account.currencies.entries.sortedBy { it.key.ordinal }.forEach { (key, amount) -> append("currency\t$key\t$amount\n") }
            account.fragments.entries.sortedBy { it.key.ordinal }.forEach { (key, amount) -> append("fragment\t$key\t$amount\n") }
            account.legacyLayouts.sortedBy { it.ordinal }.forEach { append("legacy-layout\t$it\n") }
            account.balances.entries.sortedWith(compareBy({ it.key.resource.ordinal }, { it.key.tier })).forEach { (key, amount) ->
                append("balance\t${key.resource.name}\t${key.tier}\t$amount\n")
            }
            account.maps.forEach { append("map\t${mapFields(it)}\n") }
            account.activeRun?.let { append("run\t${it.id}\t${it.bossDefeated}\t${mapFields(it.map)}\t${it.trialId ?: ""}\n") }
            account.receipts.forEach { (id, receipt) -> append("receipt\t$id\t${receipt.fingerprint}\t${receipt.revision}\t${base64(receipt.message)}\n") }
            account.claimedSources.forEach { append("source\t${base64(it)}\n") }
            account.affixStones.forEach { append("affix\t${affixFields(it)}\n") }
            account.equippedAffixes.forEach { append("equipped-affix\t${it.gear}\t${it.index}\t${affixFields(it.stone)}\n") }
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
        require(header.size == 4 && header[0] == "PROJECTS_CORE_LOOP" && header[1] in setOf("1", "2", "3", "4", "5", "6", "7")) { "未対応の保存形式です" }
        val version = header[1].toInt()
        require(UUID.fromString(header[2]) == playerId) { "保存データのプレイヤーが一致しません" }
        val gear = rows.getOrNull(1) ?: error("装備データがありません")
        require(gear.size == 4 && gear[0] == "gear")
        val balances = linkedMapOf<CoreMaterial, Long>()
        val maps = mutableListOf<CoreOwnedMap>()
        var active: CoreActiveRun? = null
        val receipts = linkedMapOf<UUID, CoreReceipt>()
        val sources = linkedSetOf<String>()
        val stones = mutableListOf<CoreAffixStone>()
        val equipped = mutableListOf<CoreEquippedAffix>()
        val currencies = linkedMapOf<CoreCraftingCurrency, Long>()
        val fragments = linkedMapOf<CoreActivityKind, Long>()
        val legacy = linkedSetOf<CoreGearSlot>()
        var crafting: List<String>? = null
        var enhancement: List<String>? = null
        var economy: List<String>? = null
        val identities = mutableMapOf<CoreGearSlot, CoreGearIdentity>()
        val gearRows = linkedMapOf<UUID, List<String>>()
        val storedAffixes = mutableMapOf<UUID, MutableList<Pair<Int, CoreAffixStone>>>()
        val offers = mutableListOf<CoreMarketOffer>()
        var survey: Long? = null
        val professions = linkedMapOf<CoreProfession, CoreProfessionProgress>()
        val buyOrders = mutableListOf<CoreBuyOrder>()
        val records = linkedMapOf<Int, Int>()
        val qualities = linkedMapOf<UUID, Int>()
        var dungeon: CoreDungeonEntry? = null
        rows.drop(2).forEach { row -> when (row[0]) {
            "survey" -> { require(version >= 7 && row.size == 2 && survey == null); survey = row[1].toLong() }
            "profession" -> { require(version >= 7 && row.size == 4); require(professions.put(CoreProfession.valueOf(row[1]), CoreProfessionProgress(row[2].toLong(), row[3].toInt())) == null) }
            "buy-order" -> { require(version >= 7 && row.size == 7 && buyOrders.size < CoreEconomy.MAX_OFFERS)
                buyOrders += CoreBuyOrder(UUID.fromString(row[1]), row[2].toLong(), row[3].toInt(), row[4].toInt(),
                    row[5].takeIf { it.isNotEmpty() }?.let(CoreResource::valueOf), row[6].takeIf { it.isNotEmpty() }?.let(CoreGearSlot::valueOf)) }
            "dungeon-record" -> { require(version >= 7 && row.size == 3); require(records.put(row[1].toInt(), row[2].toInt()) == null) }
            "gear-quality" -> { require(version >= 7 && row.size == 3); require(qualities.put(UUID.fromString(row[1]), row[2].toInt()) == null) }
            "dungeon-run" -> { require(version >= 7 && row.size == 5 && dungeon == null); dungeon = CoreDungeonEntry(row[1].toInt(), row[2].toInt(), row[3].toInt(), row[4].toInt()) }
            "economy" -> { require(version >= 5 && economy == null && row.size == 6); economy = row }
            "identity" -> { require(version >= 5 && row.size == 5); require(identities.put(CoreGearSlot.valueOf(row[1]), readIdentity(row.drop(2))) == null) }
            "stored-gear" -> { require(version >= 5 && row.size == 11 && gearRows.size < CoreEconomy.MAX_GEAR); require(gearRows.put(UUID.fromString(row[1]), row) == null) }
            "stored-affix" -> {
                require(version >= 5 && row.size == 8)
                val list = storedAffixes.getOrPut(UUID.fromString(row[1])) { mutableListOf() }
                require(list.size < 6); list += row[2].toInt() to readAffix(row.drop(3))
            }
            "offer" -> {
                require(version >= 5 && row.size == 7 && offers.size < CoreEconomy.MAX_OFFERS)
                offers += CoreMarketOffer(UUID.fromString(row[1]), row[2].toLong(), row[3].takeIf { it.isNotEmpty() }?.let {
                    CoreMaterial(CoreResource.valueOf(it), row[4].toInt()) }, row[5].toLong(), row[6].takeIf { it.isNotEmpty() }?.let(UUID::fromString))
            }
            "crafting" -> { require(version >= 3 && crafting == null && row.size == 4); crafting = row }
            "enhancement" -> { require(version >= 4 && enhancement == null && row.size == 6); enhancement = row }
            "currency" -> {
                require(version >= 3 && row.size == 3)
                require(currencies.put(CoreCraftingCurrency.valueOf(row[1]), row[2].toLong()) == null)
            }
            "fragment" -> {
                require(version >= 3 && row.size == 3)
                require(fragments.put(CoreActivityKind.valueOf(row[1]), row[2].toLong()) == null)
            }
            "legacy-layout" -> { require(version >= 3 && row.size == 2 && legacy.add(CoreGearSlot.valueOf(row[1]))) }
            "balance" -> {
                require(row.size == 4 && balances.size < CoreLoopCatalog.MAX_BALANCES)
                require(balances.put(CoreMaterial(CoreResource.valueOf(row[1]), row[2].toInt()), row[3].toLong()) == null)
            }
            "map" -> { require(maps.size < CoreLoopCatalog.MAX_MAPS); maps += readMap(row.drop(1)) }
            "run" -> {
                require(active == null && row.size == if (version >= 3) 8 else 7)
                val defeated = when (row[2]) { "true" -> true; "false" -> false; else -> error("Invalid boolean") }
                active = CoreActiveRun(UUID.fromString(row[1]), readMap(row.subList(3, 7)), defeated,
                    if (version >= 3) row[7].takeUnless { it.isEmpty() } else null)
            }
            "receipt" -> {
                require(row.size == 5 && receipts.size < CoreLoopCatalog.MAX_RECEIPTS)
                require(receipts.put(UUID.fromString(row[1]), CoreReceipt(row[2], row[3].toLong(), unbase64(row[4]))) == null)
            }
            "source" -> { require(row.size == 2 && sources.size < CoreLoopCatalog.MAX_SOURCES && sources.add(unbase64(row[1]))) }
            "affix" -> { require(version >= 2 && stones.size < CoreAffixCatalog.MAX_STONES); stones += readAffix(row.drop(1)) }
            "equipped-affix" -> {
                require(version >= 2 && row.size == 8 && equipped.size < if (version >= 3) 12 else 8)
                equipped += CoreEquippedAffix(CoreGearSlot.valueOf(row[1]), row[2].toInt(), readAffix(row.drop(3)))
            }
            else -> error("未知の保存項目: ${row[0].take(32)}")
        } }
        val weaponTier = gear[1].toInt()
        val armorTier = gear[2].toInt()
        if (version < 3) {
            // Preserve the old validity boundary; migration must not legitimize an invalid v2 slot.
            require(equipped.all { it.index < if (it.gear == CoreGearSlot.WEAPON) weaponTier else armorTier })
            return CoreAccount(playerId, header[3].toLong(), balances, weaponTier, armorTier, gear[3].toInt(), maps, active, receipts, sources, stones, equipped,
                craftingSeed = CoreCraftingCatalog.legacySeed(playerId))
        }
        val craft = requireNotNull(crafting) { "装備クラフトの保存項目がありません" }
        val enhanced = if (version >= 4) requireNotNull(enhancement) { "装備強化の保存項目がありません" } else null
        if (version >= 5) require(economy != null && identities.size == 2)
        require(storedAffixes.keys.all { it in gearRows })
        if (version >= 7) {
            require(survey != null && (dungeon == null || active != null))
            require(qualities.keys == (identities.values.map { it.id } + gearRows.keys).toSet())
            identities.replaceAll { _, id -> id.copy(quality = qualities.getValue(id.id)) }
        }
        active = active?.copy(dungeon = dungeon)
        val stored = gearRows.map { (id, r) ->
            val slot = CoreGearSlot.valueOf(r[4])
            CoreStoredGear(readIdentity(r.subList(1, 4)).copy(quality = qualities[id] ?: 0), slot, r[5].toInt(), CoreGearRarity.valueOf(r[6]),
                CoreEnhancementState(r[7].toInt(), r[8].toInt()), storedAffixes[id].orEmpty().map { CoreEquippedAffix(slot, it.first, it.second) }, r[9].toBooleanStrict(), readBroken(r[10], version))
        }
        return CoreAccount(playerId, header[3].toLong(), balances, weaponTier, armorTier, gear[3].toInt(), maps, active, receipts, sources, stones, equipped,
            CoreGearRarity.valueOf(craft[1]), CoreGearRarity.valueOf(craft[2]), currencies, fragments, legacy, craft[3].toLong(),
            enhanced?.let { CoreEnhancementState(it[1].toInt(), it[2].toInt()) } ?: CoreEnhancementState(),
            enhanced?.let { CoreEnhancementState(it[3].toInt(), it[4].toInt()) } ?: CoreEnhancementState(),
            enhanced?.get(5)?.toLong() ?: 0L,
            silver = economy?.get(1)?.toLong() ?: 0,
            weaponIdentity = identities[CoreGearSlot.WEAPON] ?: CoreGearIdentity.legacy(playerId, CoreGearSlot.WEAPON),
            armorIdentity = identities[CoreGearSlot.ARMOR] ?: CoreGearIdentity.legacy(playerId, CoreGearSlot.ARMOR),
            storedGear = stored, offers = offers, deliveryDay = economy?.get(2)?.toLong() ?: 0, deliveries = economy?.get(3)?.toInt() ?: 0,
            weaponBroken = economy?.get(4)?.let { readBroken(it, version) } ?: false,
            armorBroken = economy?.get(5)?.let { readBroken(it, version) } ?: false,
            professions = professions, surveyPoints = survey ?: 0, buyOrders = buyOrders, dungeonRecords = records)
    }

    /** Retired v5 wear is validated but never reinterpreted as enhancement damage. */
    private fun readBroken(value: String, version: Int): Boolean {
        if (version >= 6) return value.toBooleanStrict()
        require(value.toInt() in 0..100) { "旧整備度が不正です" }
        return false
    }

    private fun affixFields(stone: CoreAffixStone): String = "${stone.id}\t${stone.modId}\t${stone.tier}\t${stone.value}\t${stone.definitionRevision}"
    private fun identityFields(id: CoreGearIdentity) = "${id.id}\t${id.crafter}\t${id.bound}"
    private fun readIdentity(parts: List<String>): CoreGearIdentity {
        require(parts.size == 3)
        return CoreGearIdentity(UUID.fromString(parts[0]), UUID.fromString(parts[1]), parts[2].toBooleanStrict())
    }
    private fun readAffix(parts: List<String>): CoreAffixStone {
        require(parts.size == 5)
        return CoreAffixStone(UUID.fromString(parts[0]), parts[1], parts[2].toInt(), parts[3].toDouble(), parts[4].toInt())
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
