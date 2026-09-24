package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FirstMagicColonyTest {
    @TempDir lateinit var directory: Path

    @Test fun allWorkshopPiecesAreItemsThatPlacePickUpAndReloadWithoutOldPlinths() {
        MinecraftServer.init(Auth.Offline())
        val playerId = UUID.randomUUID()
        val repository = FirstMagicColonyLayoutRepository(directory)
        val state = FirstMagicState(jars = mapOf(FirstAspect.EMBER to 9))
        for (kind in ColonyPlaceable.entries) {
            val item = FirstMagicColonyItems.item(kind, packed = true)
            assertEquals(kind, FirstMagicColonyItems.kind(item))
            assertTrue(assertNotNull(item.get(DataComponents.CAN_PLACE_ON)).test(Block.STONE_BRICKS),
                "${kind.name} must send a placement click in adventure mode")
        }
        val colony = FirstMagicColony.create(state, persist = { repository.save(playerId, it) })
        try {
            assertEquals(Block.AIR, colony.instance.getBlock(8, 41, 4), "old oversized desk plinth must be gone")
            assertEquals(Block.AIR, colony.instance.getBlock(14, 41, 4), "old copper distiller plinth must be gone")
            assertEquals(Block.AIR, colony.instance.getBlock(8, 41, 12), "old shelf plinth must be gone")
            assertEquals(ColonyFixture.EXIT, colony.fixture(BlockVec(-3, 41, -4)))
            assertEquals(ColonyFixture.SEALED_DOOR, colony.fixture(BlockVec(10, 42, 22)))

            assertTrue(colony.place(ColonyPlaceable.DESK, BlockVec(8, 41, 4), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.DISTILLER, BlockVec(14, 41, 4), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.SHELF, BlockVec(8, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_EMBER, BlockVec(8, 43, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_TIDE, BlockVec(11, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_GALE, BlockVec(12, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_STONE, BlockVec(13, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.STAR_CHART, BlockVec(11, 42, 2), BlockFace.SOUTH))
            assertTrue(colony.missingItems().isEmpty())
            assertEquals(ColonyFixture.DESK, colony.fixture(BlockVec(9, 42, 5)))
            assertEquals(ColonyFixture.DISTILLER, colony.fixture(BlockVec(15, 43, 5)))
            assertEquals(ColonyFixture.JARS, colony.fixture(BlockVec(10, 42, 12)))
            assertEquals(ColonyFixture.CHART, colony.fixture(BlockVec(12, 43, 2)))
            assertFalse(colony.place(ColonyPlaceable.DESK, BlockVec(12, 41, 8), BlockFace.SOUTH))
            assertFalse(colony.place(ColonyPlaceable.DISTILLER, BlockVec(8, 43, 12), BlockFace.SOUTH))
            assertEquals(null, colony.pickUp(BlockVec(8, 41, 12)), "shelf must support its jar until that jar is picked up")
            assertEquals(ColonyPlaceable.JAR_EMBER, colony.pickUp(BlockVec(8, 43, 12)))
            assertEquals(ColonyPlaceable.SHELF, colony.pickUp(BlockVec(8, 41, 12)))
            assertEquals(Block.AIR, colony.instance.getBlock(8, 41, 12))
            assertEquals(2, colony.missingItems().size)
        } finally { colony.dispose() }

        val saved = repository.load(playerId)
        assertEquals(6, saved.size)
        val restored = FirstMagicColony.create(state, saved)
        try {
            assertEquals(ColonyFixture.DESK, restored.fixture(BlockVec(8, 41, 4)))
            assertEquals(ColonyFixture.JARS, restored.fixture(BlockVec(11, 41, 12)))
            assertEquals(ColonyFixture.CHART, restored.fixture(BlockVec(11, 42, 2)))
            assertEquals(2, restored.missingItems().size)
        } finally { restored.dispose() }

        val crowdedLegacy = listOf(
            ColonyPlacement(ColonyPlaceable.DESK, 8, 41, 2, BlockFace.NORTH),
            ColonyPlacement(ColonyPlaceable.DISTILLER, 9, 41, 3, BlockFace.NORTH),
            ColonyPlacement(ColonyPlaceable.SHELF, 11, 41, 3, BlockFace.NORTH),
            ColonyPlacement(ColonyPlaceable.JAR_EMBER, 10, 41, 3, BlockFace.EAST),
            ColonyPlacement(ColonyPlaceable.JAR_TIDE, 11, 42, 3, BlockFace.SOUTH),
            ColonyPlacement(ColonyPlaceable.JAR_GALE, 12, 41, 3, BlockFace.WEST),
            ColonyPlacement(ColonyPlaceable.JAR_STONE, 7, 41, 16, BlockFace.SOUTH),
            ColonyPlacement(ColonyPlaceable.STAR_CHART, 13, 41, 3, BlockFace.EAST),
        )
        val migrated = FirstMagicColony.create(state, crowdedLegacy) { repository.save(playerId, it) }
        try {
            assertTrue(migrated.missingItems().isEmpty(), "older close-packed furniture must all remain available")
            assertEquals(ColonyFixture.DESK, migrated.fixture(BlockVec(8, 41, 2)))
            assertEquals(8, repository.load(playerId).size)
            assertTrue(repository.load(playerId).any { savedPlace -> savedPlace !in crowdedLegacy },
                "overlapping legacy layout must be moved into free cells")
        } finally { migrated.dispose() }
    }
}
