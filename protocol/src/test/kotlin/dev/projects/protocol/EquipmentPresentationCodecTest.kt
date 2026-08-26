package dev.projects.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EquipmentPresentationCodecTest {
    private val snapshot = EquipmentPresentationSnapshot(
        itemId = "projects:twin-blades",
        displayName = "Twin Blades",
        category = "weapon",
        slot = "weapon",
        tier = "T1",
        itemLevel = 5,
        rarity = "uncommon",
        baseStats = listOf(EquipmentPresentationStat("projects:physical-attack", 12.5)),
        installedMods = listOf(
            EquipmentPresentationMod(0, "projects:keen-edge", 1, 2.5, "projects:physical-attack", "base_flat"),
        ),
    )

    @Test
    fun `snapshot codec round trips deterministically`() {
        val first = EquipmentPresentationCodec.encode(snapshot)
        assertEquals(first.toList(), EquipmentPresentationCodec.encode(snapshot).toList())
        assertEquals(snapshot, EquipmentPresentationCodec.decodeOrNull(first))
    }

    @Test
    fun `unknown and malformed schema are ignored`() {
        val encoded = EquipmentPresentationCodec.encode(snapshot)
        encoded[0] = 99
        assertNull(EquipmentPresentationCodec.decodeOrNull(encoded))
        assertNull(EquipmentPresentationCodec.decodeOrNull(byteArrayOf(1, 0)))
    }
}
