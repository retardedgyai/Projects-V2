package dev.projects.server.questmap

import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.IntBinaryTag
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.nbt.BinaryTagReader
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

internal enum class SchematicAnchorMode {
    TREE_TRUNK,
    BURIED_MASS,
    SURFACE_MASS,
}

internal data class SchematicVoxel(
    val x: Int,
    val y: Int,
    val z: Int,
    val state: String,
)

/**
 * Minimal Sponge schematic v2 reader used for reviewed, repository-owned third-party assets.
 * Air and block entities are deliberately ignored; quest-map assets are inert block sculptures.
 */
internal class SpongeSchematicAsset private constructor(
    val id: String,
    val width: Int,
    val height: Int,
    val length: Int,
    val anchorX: Int,
    val anchorY: Int,
    val anchorZ: Int,
    val voxels: List<SchematicVoxel>,
    val anchorMode: SchematicAnchorMode,
) {
    val footprintRadius: Int = voxels.maxOf { voxel ->
        maxOf(kotlin.math.abs(voxel.x - anchorX), kotlin.math.abs(voxel.z - anchorZ))
    }

    internal fun resolvedStates(
        rotation: Int,
        palette: (String, SchematicVoxel) -> String = { state, _ -> state },
    ): List<String> = voxels.map { voxel -> palette(rotateState(voxel.state, rotation), voxel) }

    fun place(
        instance: Instance,
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        rotation: Int,
        palette: (String, SchematicVoxel) -> String = { state, _ -> state },
    ) {
        val originY = plan.heightAt(origin) + 1
        voxels.forEach { voxel ->
            val localX = voxel.x - anchorX
            val localZ = voxel.z - anchorZ
            val (rotatedX, rotatedZ) = rotate(localX, localZ, rotation)
            val x = origin.x + rotatedX
            val z = origin.z + rotatedZ
            if (x !in 1 until plan.size - 1 || z !in 1 until plan.size - 1) return@forEach
            val y = originY + voxel.y - anchorY
            if (y < plan.heightAt(x, z) - 3) return@forEach
            val state = palette(rotateState(voxel.state, rotation), voxel)
            val block = Block.fromState(state) ?: error("Unknown block state in $id: $state")
            val surface = plan.heightAt(x, z)
            val shouldGround = when (anchorMode) {
                SchematicAnchorMode.TREE_TRUNK -> voxel.y <= anchorY + 2 && isTrunkState(state)
                SchematicAnchorMode.BURIED_MASS -> y >= surface + 1
                SchematicAnchorMode.SURFACE_MASS -> voxel.y <= anchorY + 1
            }
            if (shouldGround && y > surface + 1) {
                for (fillY in surface + 1 until y) instance.setBlock(x, fillY, z, block)
            }
            instance.setBlock(x, y, z, block)
        }
    }

    companion object {
        fun read(id: String, input: InputStream, anchorMode: SchematicAnchorMode): SpongeSchematicAsset {
            val root = DataInputStream(GZIPInputStream(input)).use { data ->
                BinaryTagReader(data).readNamed().value as? CompoundBinaryTag
                    ?: error("Schematic $id has no compound root")
            }
            require(root.getInt("Version") == 2) { "Schematic $id is not Sponge v2" }
            val width = root.getShort("Width").toInt()
            val height = root.getShort("Height").toInt()
            val length = root.getShort("Length").toInt()
            require(width > 0 && height > 0 && length > 0) { "Schematic $id has invalid bounds" }

            val stateByPaletteId = mutableMapOf<Int, String>()
            root.getCompound("Palette").forEach { (state, tag) ->
                val paletteId = (tag as? IntBinaryTag)?.value()
                    ?: error("Schematic $id palette entry $state is not an int")
                stateByPaletteId[paletteId] = modernState(state)
            }
            val paletteIds = decodeVarInts(root.getByteArray("BlockData"), width * height * length, id)
            val voxels = buildList {
                paletteIds.forEachIndexed { index, paletteId ->
                    val state = stateByPaletteId[paletteId]
                        ?: error("Schematic $id references missing palette id $paletteId")
                    if (state == "minecraft:air" || state == "minecraft:structure_void") return@forEachIndexed
                    val y = index / (width * length)
                    val remainder = index % (width * length)
                    val z = remainder / width
                    val x = remainder % width
                    add(SchematicVoxel(x, y, z, state))
                }
            }
            require(voxels.isNotEmpty()) { "Schematic $id is empty" }
            val anchorCandidates = when (anchorMode) {
                SchematicAnchorMode.TREE_TRUNK -> {
                    val trunks = voxels.filter { voxel ->
                        val name = voxel.state.substringBefore('[')
                        name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")
                    }
                    require(trunks.isNotEmpty()) { "Tree schematic $id contains no trunk blocks" }
                    val lowestTrunk = trunks.minOf(SchematicVoxel::y)
                    trunks.filter { it.y == lowestTrunk }
                }
                SchematicAnchorMode.BURIED_MASS -> {
                    val lowest = voxels.minOf(SchematicVoxel::y)
                    voxels.filter { it.y == lowest }
                }
                SchematicAnchorMode.SURFACE_MASS -> {
                    val lowest = voxels.minOf(SchematicVoxel::y)
                    voxels.filter { it.y == lowest }
                }
            }
            val anchorX = anchorCandidates.map(SchematicVoxel::x).average().roundToInt()
            val anchorZ = anchorCandidates.map(SchematicVoxel::z).average().roundToInt()
            val anchorY = when (anchorMode) {
                SchematicAnchorMode.TREE_TRUNK -> anchorCandidates.minOf(SchematicVoxel::y)
                SchematicAnchorMode.BURIED_MASS -> anchorCandidates.minOf(SchematicVoxel::y) + 1
                SchematicAnchorMode.SURFACE_MASS -> anchorCandidates.minOf(SchematicVoxel::y)
            }
            return SpongeSchematicAsset(id, width, height, length, anchorX, anchorY, anchorZ, voxels, anchorMode)
        }

        private fun decodeVarInts(data: ByteArray, expected: Int, id: String): IntArray {
            val values = IntArray(expected)
            var dataIndex = 0
            repeat(expected) { valueIndex ->
                var value = 0
                var shift = 0
                while (true) {
                    require(dataIndex < data.size) { "Schematic $id block data ended early" }
                    val byte = data[dataIndex++].toInt() and 0xff
                    value = value or ((byte and 0x7f) shl shift)
                    if (byte and 0x80 == 0) break
                    shift += 7
                    require(shift <= 28) { "Schematic $id contains an invalid varint" }
                }
                values[valueIndex] = value
            }
            require(dataIndex == data.size) { "Schematic $id contains trailing block data" }
            return values
        }

        private fun modernState(state: String): String {
            val renamed = if (state == "minecraft:grass" || state.startsWith("minecraft:grass[")) {
                state.replaceFirst("minecraft:grass", "minecraft:short_grass")
            } else {
                state
            }
            return renamed.replace("persistent=false", "persistent=true")
        }

        private fun isTrunkState(state: String): Boolean {
            val name = state.substringBefore('[')
            return name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem") || name.endsWith("_roots")
        }

        private fun rotate(dx: Int, dz: Int, rotation: Int): Pair<Int, Int> = when (Math.floorMod(rotation, 4)) {
            0 -> dx to dz
            1 -> -dz to dx
            2 -> -dx to -dz
            else -> dz to -dx
        }

        private fun rotateState(state: String, rotation: Int): String {
            val turns = Math.floorMod(rotation, 4)
            if (turns == 0 || '[' !in state) return state
            var result = state
            if (turns and 1 == 1) {
                result = when {
                    "axis=x" in result -> result.replace("axis=x", "axis=z")
                    "axis=z" in result -> result.replace("axis=z", "axis=x")
                    else -> result
                }
            }
            val facing = Regex("facing=(north|east|south|west)").find(result)?.groupValues?.get(1)
            if (facing != null) {
                val directions = listOf("north", "east", "south", "west")
                val rotated = directions[(directions.indexOf(facing) + turns) % directions.size]
                result = result.replace("facing=$facing", "facing=$rotated")
            }
            val connections = Regex("(north|east|south|west)=(true|false)")
                .findAll(result)
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
            if (connections.isNotEmpty()) {
                val directions = listOf("north", "east", "south", "west")
                connections.forEach { (direction, value) ->
                    result = result.replace("$direction=$value", "$direction=__rotating__")
                }
                connections.forEach { (direction, value) ->
                    val rotated = directions[(directions.indexOf(direction) + turns) % directions.size]
                    result = result.replace("$direction=__rotating__", "$rotated=$value")
                }
            }
            return result
        }
    }
}
