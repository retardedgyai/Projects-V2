package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoreDungeonStageTest {
    private fun sealed(body: String) = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray(UTF_8))
        .joinToString("") { "%02x".format(it) } + "\n"

    @Test fun `dungeon clears are saved from v14, and a v13 file reads as none`() {
        val id = UUID.randomUUID()
        val account = CoreAccount(id, revision = 3, dungeonClears = mapOf(CoreDungeons.SAKURA to 2))
        val encoded = CoreAccountCodec.encode(account)
        assertTrue(encoded.startsWith("PROJECTS_CORE_LOOP\t14\t"))
        assertTrue("dungeon-clear\t${CoreDungeons.SAKURA}\t2\n" in encoded)
        assertEquals(mapOf(CoreDungeons.SAKURA to 2), CoreAccountCodec.decode(encoded, id).dungeonClears)
        // A v13 file has no such rows: it reads as no clears and is written back as v14.
        val v13 = encoded.substringBefore("checksum\t").lineSequence().filterNot { it.startsWith("dungeon-clear\t") }
            .joinToString("\n").replaceFirst("PROJECTS_CORE_LOOP\t14\t", "PROJECTS_CORE_LOOP\t13\t")
        val migrated = CoreAccountCodec.decode(sealed(v13), id)
        assertTrue(migrated.dungeonClears.isEmpty())
        assertTrue(CoreAccountCodec.encode(migrated).startsWith("PROJECTS_CORE_LOOP\t14\t"))
        // The row is not accepted under an older header, nor for an unknown dungeon.
        assertFailsWith<IllegalArgumentException> { CoreAccountCodec.decode(sealed(v13 + "dungeon-clear\t${CoreDungeons.SAKURA}\t1\n"), id) }
        assertFailsWith<IllegalArgumentException> {
            CoreAccountCodec.decode(sealed(encoded.substringBefore("checksum\t") + "dungeon-clear\tnowhere\t1\n"), id)
        }
    }

    @Test fun `each clear cuts the next run one 段階 harder, up to five`() {
        assertEquals(listOf(1, 2, 3, 5, 5), listOf(0, 1, 2, 4, 9).map(CoreDungeons::stage))
        assertEquals(1.0, CoreDungeons.health(1)); assertEquals(1.3, CoreDungeons.health(2), 1e-9)
        assertEquals(1.0, CoreDungeons.damage(1)); assertEquals(1.2, CoreDungeons.damage(2), 1e-9)
    }
}
