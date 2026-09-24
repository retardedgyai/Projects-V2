package dev.projects.server.coreloop

import net.minestom.server.instance.block.BlockFace
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

internal enum class ColonyPlaceable(
    val label: String, val model: String, val fixture: ColonyFixture?,
    val width: Int, val depth: Int, val height: Int, val visualHeight: Double, val visualDepth: Double = depth.toDouble(),
) {
    DESK("研究机", "research_desk_dormant", ColonyFixture.DESK, 2, 1, 2, 1.25),
    DISTILLER("粗末な蒸留器", "crude_distiller", ColonyFixture.DISTILLER, 2, 2, 3, 2.35),
    SHELF("Jar棚", "jar_shelf", ColonyFixture.JARS, 3, 1, 2, 2.0),
    JAR_EMBER("Ember Jar", "jar_ember_empty", ColonyFixture.JARS, 1, 1, 1, 1.0),
    JAR_TIDE("Tide Jar", "jar_tide_empty", ColonyFixture.JARS, 1, 1, 1, 1.0),
    JAR_GALE("Gale Jar", "jar_gale_empty", ColonyFixture.JARS, 1, 1, 1, 1.0),
    JAR_STONE("Stone Jar", "jar_stone_empty", ColonyFixture.JARS, 1, 1, 1, 1.0),
    STAR_CHART("古い星図", "star_chart", ColonyFixture.CHART, 2, 1, 2, 2.0, .22);

    val isJar get() = name.startsWith("JAR_")
    val aspect get() = if (isJar) FirstAspect.valueOf(name.removePrefix("JAR_")) else null
}

internal data class ColonyPlacement(
    val kind: ColonyPlaceable, val x: Int, val y: Int, val z: Int, val facing: BlockFace,
    val shelfSlot: Int? = null,
)

/** A separate, bounded layout sidecar; the existing magic progress save stays untouched. */
internal class FirstMagicColonyLayoutRepository(private val directory: Path) {
    fun load(playerId: UUID): List<ColonyPlacement> {
        val path = file(playerId)
        if (!Files.exists(path)) return emptyList()
        require(Files.isRegularFile(path) && Files.size(path) <= 2048) { "Invalid colony layout" }
        val lines = Files.readAllLines(path, UTF_8)
        require(lines.size in 2..(2 + ColonyPlaceable.entries.size) &&
            lines[0] in listOf("FIRST_MAGIC_COLONY_V1", "FIRST_MAGIC_COLONY_V2") && lines[1] == playerId.toString()) {
            "Unknown colony layout"
        }
        val placements = lines.drop(2).map { line ->
            val fields = line.split(',')
            require(fields.size == if (lines[0] == "FIRST_MAGIC_COLONY_V1") 5 else 6) { "Invalid colony placement" }
            ColonyPlacement(ColonyPlaceable.valueOf(fields[0]), fields[1].toInt(), fields[2].toInt(), fields[3].toInt(),
                BlockFace.valueOf(fields[4]), fields.getOrNull(5)?.takeUnless { it == "-" }?.toInt())
                .also { placement ->
                    require(placement.x in -21..21 && placement.z in -20..23 && placement.y in 41..47)
                    require(placement.facing in listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST))
                    require(placement.shelfSlot == null || (placement.kind.isJar && placement.shelfSlot in 0..3))
                }
        }
        require(placements.map { it.kind }.distinct().size == placements.size)
        require(placements.filter { it.shelfSlot == null }.map { Triple(it.x, it.y, it.z) }.distinct().size == placements.count { it.shelfSlot == null })
        val shelf = placements.firstOrNull { it.kind == ColonyPlaceable.SHELF }
        require(placements.filter { it.shelfSlot != null }.all { jar ->
            shelf != null && jar.x == shelf.x && jar.y == shelf.y && jar.z == shelf.z &&
                jar.facing == shelf.facing && jar.shelfSlot == jar.kind.aspect?.ordinal
        }) { "Jar shelf attachment is invalid" }
        return placements
    }

    fun save(playerId: UUID, placements: Collection<ColonyPlacement>) {
        require(placements.size <= ColonyPlaceable.entries.size)
        Files.createDirectories(directory)
        val content = (listOf("FIRST_MAGIC_COLONY_V2", playerId.toString()) + placements.sortedBy { it.kind.ordinal }
            .map { "${it.kind},${it.x},${it.y},${it.z},${it.facing},${it.shelfSlot ?: "-"}" }).joinToString("\n", postfix = "\n")
        val temporary = Files.createTempFile(directory, ".$playerId.", ".tmp")
        try {
            Files.writeString(temporary, content, UTF_8)
            Files.move(temporary, file(playerId), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun file(playerId: UUID) = directory.resolve("$playerId.colony")
}
