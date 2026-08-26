package dev.projects.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

const val EQUIPMENT_PRESENTATION_SCHEMA = 1

data class EquipmentPresentationStat(val statId: String, val value: Double)

data class EquipmentPresentationMod(
    val slotIndex: Int,
    val modId: String,
    val rank: Int,
    val rolledValue: Double,
    val statId: String,
    val stackingLayer: String,
)

data class EquipmentPresentationSnapshot(
    val itemId: String,
    val displayName: String,
    val category: String,
    val slot: String,
    val tier: String,
    val itemLevel: Int,
    val rarity: String,
    val baseStats: List<EquipmentPresentationStat>,
    val installedMods: List<EquipmentPresentationMod>,
    val schema: Int = EQUIPMENT_PRESENTATION_SCHEMA,
) {
    init {
        require(schema == EQUIPMENT_PRESENTATION_SCHEMA) { "Unsupported equipment presentation schema" }
        require(itemId.isNotBlank() && displayName.isNotBlank()) { "Equipment presentation identity is required" }
        require(itemLevel > 0) { "Equipment presentation item level must be positive" }
        require(baseStats.size <= 16 && installedMods.size <= 4) { "Equipment presentation has too many rows" }
        require(baseStats.zipWithNext().all { it.first.statId < it.second.statId }) { "Base stats must be sorted" }
        require(installedMods.zipWithNext().all { it.first.slotIndex < it.second.slotIndex }) { "MOD rows must be sorted" }
        require(baseStats.all { it.statId.isNotBlank() && it.value.isFinite() }) { "Invalid base stat" }
        require(installedMods.all {
            it.slotIndex in 0..3 && it.modId.isNotBlank() && it.rank > 0 && it.rolledValue.isFinite() &&
                it.statId.isNotBlank() && it.stackingLayer.isNotBlank()
        }) { "Invalid MOD row" }
    }
}

object EquipmentPresentationCodec {
    private const val MAX_BYTES = 4096

    fun encode(snapshot: EquipmentPresentationSnapshot): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeByte(snapshot.schema)
            writeString(data, snapshot.itemId)
            writeString(data, snapshot.displayName)
            writeString(data, snapshot.category)
            writeString(data, snapshot.slot)
            writeString(data, snapshot.tier)
            data.writeInt(snapshot.itemLevel)
            writeString(data, snapshot.rarity)
            data.writeByte(snapshot.baseStats.size)
            snapshot.baseStats.forEach {
                writeString(data, it.statId)
                data.writeDouble(it.value)
            }
            data.writeByte(snapshot.installedMods.size)
            snapshot.installedMods.forEach {
                data.writeByte(it.slotIndex)
                writeString(data, it.modId)
                data.writeByte(it.rank)
                data.writeDouble(it.rolledValue)
                writeString(data, it.statId)
                writeString(data, it.stackingLayer)
            }
        }
    }.toByteArray().also { require(it.size <= MAX_BYTES) { "Equipment presentation is too large" } }

    fun decodeOrNull(bytes: ByteArray): EquipmentPresentationSnapshot? = try {
        require(bytes.size <= MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val schema = input.readUnsignedByte()
            val snapshot = EquipmentPresentationSnapshot(
                itemId = readString(input),
                displayName = readString(input),
                category = readString(input),
                slot = readString(input),
                tier = readString(input),
                itemLevel = input.readInt(),
                rarity = readString(input),
                baseStats = List(input.readUnsignedByte()) {
                    EquipmentPresentationStat(readString(input), input.readDouble())
                }.sortedBy { it.statId },
                installedMods = List(input.readUnsignedByte()) {
                    EquipmentPresentationMod(
                        input.readUnsignedByte(), readString(input), input.readUnsignedByte(), input.readDouble(),
                        readString(input), readString(input),
                    )
                }.sortedBy { it.slotIndex },
                schema = schema,
            )
            require(input.available() == 0) { "Unexpected equipment presentation data" }
            snapshot
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun writeString(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..255) { "Equipment presentation string is invalid" }
        data.writeByte(bytes.size)
        data.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readUnsignedByte()
        require(size > 0)
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
    }
}
