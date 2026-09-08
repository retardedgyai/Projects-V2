package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.network.NetworkBuffer
import java.util.UUID
import kotlin.test.*

class CoreArmamentPresentationTest {
    @Test fun `every class and tier selects an exported dedicated model only with pack`() {
        MinecraftServer.init(Auth.Offline())
        val seen = mutableSetOf<String>()
        for (job in CoreClass.entries) for (tier in 1..4) {
            val id=UUID.randomUUID()
            val base=CoreWeaponBase.entries.first { it.usable(job) }
            val a=CoreAccount(id,weaponTier=tier,journey=CoreJourney(job=job),
                weaponIdentity=CoreGearIdentity(UUID.randomUUID(),id,base=base))
            val item=CoreLoopItems.gear(a,CoreGearSlot.WEAPON,true)
            val key=CoreArmamentPresentation.model(base,job,tier)
            assertEquals(key,item.get(DataComponents.ITEM_MODEL).toString())
            assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/items/${key.substringAfter(':')}.json"))
            assertFalse(CoreLoopItems.gear(a,CoreGearSlot.WEAPON,false).get(DataComponents.ITEM_MODEL).toString().startsWith("projects:"))
            seen+=key
        }
        assertEquals(28,seen.size)
    }

    @Test fun `animation changes only reserved model float and preserves identity tooltip and other channels`() {
        MinecraftServer.init(Auth.Offline())
        val item=CoreLoopItems.gear(CoreAccount(UUID.randomUUID()),CoreGearSlot.WEAPON,true)
            .with(DataComponents.CUSTOM_MODEL_DATA,CustomModelData(listOf(0f,42f),listOf(true),listOf("keep"),emptyList()))
        for (i in -1..24) {
            val changed=CoreArmamentPresentation.frame(item,i)
            val data=changed.get(DataComponents.CUSTOM_MODEL_DATA)!!
            assertEquals(Math.floorMod(i,12).toFloat(),data.floats()[0])
            assertEquals(listOf(42f),data.floats().drop(1))
            assertEquals(listOf(true),data.flags()); assertEquals(listOf("keep"),data.strings())
            assertEquals(item.without(DataComponents.CUSTOM_MODEL_DATA),changed.without(DataComponents.CUSTOM_MODEL_DATA))
            assertSame(changed,CoreArmamentPresentation.frame(changed,i))
        }
    }

    @Test fun `ordinary items and gear without pack are never animated`() {
        MinecraftServer.init(Auth.Offline())
        for(item in listOf(ItemStack.AIR,ItemStack.of(Material.DIAMOND_SWORD),
            ItemStack.of(Material.PAPER).withItemModel("projects:weapons/greatsword_t1"),
            CoreLoopItems.gear(CoreAccount(UUID.randomUUID()),CoreGearSlot.WEAPON,false))) {
            assertSame(item,CoreArmamentPresentation.frame(item,5))
        }
    }

    @Test fun `measure complete animated stack payload rather than treating a float update as four bytes`() {
        MinecraftServer.init(Auth.Offline())
        val item=CoreLoopItems.gear(CoreAccount(UUID.randomUUID(),weaponTier=4),CoreGearSlot.WEAPON,true)
        val animated=CoreArmamentPresentation.frame(item,5)
        val bytes=NetworkBuffer.makeArray(ItemStack.NETWORK_TYPE,animated).size
        println("ARMAMENT_FRAME_PAYLOAD itemBytes=$bytes rawBytesPerSecondAt10fps=${bytes*10}; excludes packet headers, compression, observers and extra affixes")
        assertTrue(bytes>4) // Inventory synchronization transmits the item, not a float-only delta.
    }
}
