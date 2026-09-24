package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstMagicColonyTest {
    @Test fun fourSpacesAndModelInteractionTargetsBuildInPrivateInstance() {
        MinecraftServer.init(Auth.Offline())
        val colony = FirstMagicColony.create(FirstMagicState())
        try {
            assertEquals(ColonyFixture.EXIT, colony.fixture(Vec(-3.0, 41.0, -4.0)))
            assertEquals(ColonyFixture.DESK, colony.fixture(Vec(8.0, 42.0, 4.0)))
            assertEquals(ColonyFixture.DISTILLER, colony.fixture(Vec(14.0, 42.0, 4.0)))
            assertEquals(ColonyFixture.JARS, colony.fixture(Vec(8.0, 42.0, 12.0)))
            assertEquals(ColonyFixture.SEALED_DOOR, colony.fixture(Vec(10.0, 42.0, 22.0)))
            assertEquals(Block.AIR, colony.instance.getBlock(colony.spawn))
            assertTrue(colony.instance.getBlock(-1, 40, -3).isSolid)
            assertEquals(Block.BARRIER, colony.instance.getBlock(8, 42, 12))
            colony.update(FirstMagicState(jars = mapOf(FirstAspect.EMBER to 9)))
            assertEquals(Block.BARRIER, colony.instance.getBlock(8, 43, 12))
            assertTrue(colony.instance.entities.count() >= 7, "desk, distiller, shelf and four jars must exist")
        } finally { colony.dispose() }
    }
}
