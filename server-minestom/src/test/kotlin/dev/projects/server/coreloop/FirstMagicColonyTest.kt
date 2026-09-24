package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstMagicColonyTest {
    @TempDir lateinit var directory: Path

    @Test fun allWorkshopPiecesAreItemsThatPlacePickUpAndReloadWithoutOldPlinths() {
        MinecraftServer.init(Auth.Offline())
        val playerId = UUID.randomUUID()
        val repository = FirstMagicColonyLayoutRepository(directory)
        val state = FirstMagicState(jars = mapOf(FirstAspect.EMBER to 9))
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
            assertTrue(colony.place(ColonyPlaceable.JAR_EMBER, BlockVec(8, 42, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_TIDE, BlockVec(9, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_GALE, BlockVec(10, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.JAR_STONE, BlockVec(11, 41, 12), BlockFace.SOUTH))
            assertTrue(colony.place(ColonyPlaceable.STAR_CHART, BlockVec(11, 42, 2), BlockFace.SOUTH))
            assertTrue(colony.missingItems().isEmpty())
            assertEquals(ColonyFixture.DESK, colony.fixture(BlockVec(8, 41, 4)))
            assertEquals(ColonyFixture.DISTILLER, colony.fixture(BlockVec(14, 41, 4)))
            assertEquals(ColonyFixture.JARS, colony.fixture(BlockVec(8, 42, 12)))
            assertEquals(ColonyFixture.CHART, colony.fixture(BlockVec(11, 42, 2)))
            assertFalse(colony.place(ColonyPlaceable.DESK, BlockVec(12, 41, 8), BlockFace.SOUTH))
            assertFalse(colony.place(ColonyPlaceable.DISTILLER, BlockVec(8, 42, 12), BlockFace.SOUTH))
            assertEquals(null, colony.pickUp(BlockVec(8, 41, 12)), "shelf must support its jar until that jar is picked up")
            assertEquals(ColonyPlaceable.JAR_EMBER, colony.pickUp(BlockVec(8, 42, 12)))
            assertEquals(ColonyPlaceable.SHELF, colony.pickUp(BlockVec(8, 41, 12)))
            assertEquals(Block.AIR, colony.instance.getBlock(8, 41, 12))
            assertEquals(2, colony.missingItems().size)
        } finally { colony.dispose() }

        val saved = repository.load(playerId)
        assertEquals(6, saved.size)
        val restored = FirstMagicColony.create(state, saved)
        try {
            assertEquals(ColonyFixture.DESK, restored.fixture(BlockVec(8, 41, 4)))
            assertEquals(ColonyFixture.JARS, restored.fixture(BlockVec(9, 41, 12)))
            assertEquals(ColonyFixture.CHART, restored.fixture(BlockVec(11, 42, 2)))
            assertEquals(2, restored.missingItems().size)
        } finally { restored.dispose() }
    }
}
