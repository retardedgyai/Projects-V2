package dev.projects.server.equipment

import dev.projects.server.mod.AttackTag
import dev.projects.server.mod.ModDefinition
import dev.projects.server.mod.ModEntry
import dev.projects.server.mod.ModRank
import dev.projects.server.mod.ModStackingLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.item.Material

class EquipmentPresentationBridgeTest {
    @Test
    fun `equipment presentation survives item stack bridge`() {
        MinecraftServer.init(Auth.Offline())
        val definition = ModDefinition(
            "projects:keen-edge", ModRank.RANK_1, setOf(EquipmentSlot.WEAPON), setOf(AttackTag.MELEE),
            emptySet(), "projects:physical-attack", 1.0, 3.0, ModStackingLayer.BASE_FLAT, 1,
        )
        val item = EquipmentItem(
            "projects:twin-blades", EquipmentCategory.WEAPON, EquipmentSlot.WEAPON, EquipmentTier.T1, 5,
            EquipmentRarity.UNCOMMON, listOf(BaseStatRoll("projects:physical-attack", 12.5)), listOf(
                EquipmentModSlot(0, ModEntry("projects:keen-edge", ModRank.RANK_1, 2.5, 0, 1)),
                EquipmentModSlot.empty(1),
            ),
        )

        val restored = item.toPresentationItemStack(Material.IRON_SWORD, "Twin Blades", mapOf(definition.modId to definition))
            .readEquipmentPresentation()

        assertNotNull(restored)
        assertEquals(item.itemId, restored.itemId)
        assertEquals(1, restored.installedMods.size)
        assertEquals(2.5, restored.installedMods.single().rolledValue)
    }
}
