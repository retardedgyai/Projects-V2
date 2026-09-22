package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.Locale
import java.util.UUID
import kotlin.test.*

class CoreArmorPresentationTest {
    @Test fun `real refresh dresses all slots and class changes or pack removal leave no stale model`() {
        MinecraftServer.init(Auth.Offline())
        val connection=object:PlayerConnection() {
            override fun sendPacket(packet:SendablePacket)=Unit
            override fun getRemoteAddress():SocketAddress=InetSocketAddress("127.0.0.1",0)
        }
        val player=Player(connection,GameProfile(UUID.randomUUID(),"ArmorReview")).also { connection.player=it }
        val slots=listOf(EquipmentSlot.HELMET,EquipmentSlot.CHESTPLATE,EquipmentSlot.LEGGINGS,EquipmentSlot.BOOTS)
        for(job in CoreClass.entries) for(tier in 1..4) {
            val a=CoreAccount(player.uuid,armorTier=tier,journey=CoreJourney(job=job))
            CoreLoopItems.refresh(player,a,packed=true)
            for(slot in slots) assertTrue(player.getEquipment(slot).get(DataComponents.ITEM_MODEL)!!.startsWith("projects:armor/${job.name.lowercase(Locale.ROOT)}_t$tier"))
            CoreLoopItems.refresh(player,a,packed=false)
            for(slot in slots) {
                val item=player.getEquipment(slot)
                assertFalse(item.get(DataComponents.ITEM_MODEL)!!.startsWith("projects:"))
                assertNotNull(item.get(DataComponents.EQUIPPABLE)!!.assetId())
                assertEquals(slot,item.get(DataComponents.EQUIPPABLE)!!.slot())
            }
        }
    }

    @Test fun `all 112 equipment projections retain tier stats tags and slot with class models`() {
        MinecraftServer.init(Auth.Offline())
        val pieces = listOf(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS)
        val seen = mutableSetOf<String>()
        for (job in CoreClass.entries) for (tier in 1..4) for (material in pieces) {
            val account = CoreAccount(UUID.randomUUID(), armorTier=tier, journey=CoreJourney(job=job))
            val ordinary = CoreLoopItems.gear(account, CoreGearSlot.ARMOR, false, material)
            val skin = CoreArmorPresentation.skin(ordinary, job, tier, true)
            val equipped = skin.get(DataComponents.EQUIPPABLE)!!
            val original = ordinary.get(DataComponents.EQUIPPABLE)!!
            val model = skin.get(DataComponents.ITEM_MODEL)!!
            assertEquals(original.slot(), equipped.slot())
            assertEquals(original, equipped.withAssetId(original.assetId()))
            assertEquals(ordinary, skin.with(DataComponents.EQUIPPABLE,original).withItemModel(ordinary.get(DataComponents.ITEM_MODEL)!!))
            assertEquals(CoreGearSlot.ARMOR, CoreLoopItems.gearSlot(skin))
            assertTrue(model.startsWith("projects:armor/${job.name.lowercase(Locale.ROOT)}_t$tier"))
            assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/items/${model.substringAfter(':')}.json"))
            if(equipped.slot()==EquipmentSlot.HELMET) assertNull(equipped.assetId())
            else assertEquals("projects:armor/${job.name.lowercase(Locale.ROOT)}_t$tier",equipped.assetId())
            assertEquals(model,CoreLoopItems.gear(account,CoreGearSlot.ARMOR,true,material).get(DataComponents.ITEM_MODEL))
            seen+=model
        }
        assertEquals(112,seen.size)
    }

    @Test fun `no pack or non-armor item is not given a custom equipment layer`() {
        MinecraftServer.init(Auth.Offline())
        for(item in listOf(ItemStack.of(Material.DIAMOND_HELMET),ItemStack.of(Material.LEATHER_BOOTS)))
            assertSame(item,CoreArmorPresentation.skin(item,CoreClass.MAGE,4,false))
        for(item in listOf(ItemStack.AIR,ItemStack.of(Material.PAPER),ItemStack.of(Material.DIAMOND_SWORD)))
            assertSame(item,CoreArmorPresentation.skin(item,CoreClass.MAGE,4,true))
    }

    @Test fun `class equipment layers and both native textures are shipped for every tier`() {
        val loader=javaClass.classLoader
        val index=loader.getResourceAsStream("core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        for(job in CoreClass.entries) for(tier in 1..4) {
            val stem="armor/${job.name.lowercase(Locale.ROOT)}_t$tier"
            for(path in listOf("equipment/$stem.json","textures/entity/equipment/humanoid/$stem.png","textures/entity/equipment/humanoid_leggings/$stem.png")) {
                assertTrue("assets/projects/$path" in index)
                assertNotNull(loader.getResource("core-ui-pack/assets/projects/$path"))
            }
        }
    }
}
