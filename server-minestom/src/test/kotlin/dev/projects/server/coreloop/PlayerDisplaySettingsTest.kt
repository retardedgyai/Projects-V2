package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlayerDisplaySettingsTest {
    private val dir = Files.createTempDirectory("display-settings")

    @Test
    fun aSavedChoiceSurvivesARestart() {
        val player = UUID.randomUUID()
        assertNull(PlayerDisplaySettings(dir).get(player))
        PlayerDisplaySettings(dir).put(player, PlayerDisplaySettings.Values(2, 90))
        assertEquals(PlayerDisplaySettings.Values(2, 90), PlayerDisplaySettings(dir).get(player))
    }

    @Test
    fun aBrokenFileIsAskedAgainInsteadOfGuessed() {
        val player = UUID.randomUUID()
        Files.writeString(dir.resolve("$player.properties"), "gui=9\nfov=70\n")
        assertNull(PlayerDisplaySettings(dir).get(player))
    }

    @Test
    fun valuesOutsideWhatTheClientOffersAreRejected() {
        assertFailsWith<IllegalArgumentException> { PlayerDisplaySettings.Values(0, 70) }
        assertFailsWith<IllegalArgumentException> { PlayerDisplaySettings.Values(4, 150) }
    }
}
