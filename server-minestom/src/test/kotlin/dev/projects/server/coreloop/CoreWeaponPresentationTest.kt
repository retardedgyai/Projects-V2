package dev.projects.server.coreloop

import dev.projects.server.coreloop.ui.CoreUiIcon
import dev.projects.server.coreloop.ui.CoreUiItemSkin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.test.*

class CoreWeaponPresentationTest {
    private fun player(): Player {
        MinecraftServer.init(Auth.Offline())
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        return Player(connection, GameProfile(UUID.randomUUID(), "ForgeSheet")).also { connection.player = it }
    }

    private fun plain(value: Component): String = (value as? TextComponent)?.content().orEmpty() + value.children().joinToString("") { plain(it) }

    @Test fun `every tier and enhancement level displays the authoritative combat values`() {
        val player = player()
        var account = CoreAccount(player.uuid)
        val actor = CorePlayerCombat(player, { account.weaponTier }, { account.armorTier }, { null },
            statSource = { CoreAffixCatalog.stats(account) }, weaponEnhancement = { account.weaponEnhancement.level },
            armorEnhancement = { account.armorEnhancement.level }) {}
        for (tier in 1..4) for (level in 0..30) {
            account = account.copy(weaponTier = tier, armorTier = tier,
                weaponEnhancement = CoreEnhancementState(level), armorEnhancement = CoreEnhancementState(level))
            assertEquals(actor.attackDamage.roundToInt(), CoreWeaponPresentation.damage(account))
            assertEquals((actor.attackSpeed - 1) * 100, CoreWeaponPresentation.attackSpeedPercent(account), 0.000001)
            assertEquals(actor.maxHealth, CoreWeaponPresentation.health(account))
        }
    }

    @Test fun `enhancement applies to base health not flat mods and speed retains fractional percentages`() {
        val player = player()
        val mods = listOf(
            CoreEquippedAffix(CoreGearSlot.WEAPON, 0, CoreAffixStone(UUID.randomUUID(), "projects:force", 4, 22.0)),
            CoreEquippedAffix(CoreGearSlot.WEAPON, 1, CoreAffixStone(UUID.randomUUID(), "projects:haste", 4, 15.0)),
            CoreEquippedAffix(CoreGearSlot.ARMOR, 0, CoreAffixStone(UUID.randomUUID(), "projects:vitality", 4, 39.0)),
        )
        val account = CoreAccount(player.uuid, weaponTier = 4, armorTier = 4, equippedAffixes = mods,
            weaponEnhancement = CoreEnhancementState(1), armorEnhancement = CoreEnhancementState(30))
        assertEquals(343, CoreWeaponPresentation.health(account)) // 190 * 1.6 + 39; not (190 + 39) * 1.6.
        assertEquals("+15.8%", CoreWeaponPresentation.attackSpeedLabel(account))
        assertEquals((12 * CoreLoopCatalog.weaponDamage(4) * 1.04 * 1.22).roundToInt(), CoreWeaponPresentation.damage(account))
    }

    @Test fun `model only applies after pack success and fallback retains canonical equipment tags`() {
        player()
        for (tier in 1..4) {
            val account = CoreAccount(UUID.randomUUID(), weaponTier = tier, weaponEnhancement = CoreEnhancementState(30))
            val packed = CoreLoopItems.gear(account, CoreGearSlot.WEAPON, true)
            val fallback = CoreLoopItems.gear(account, CoreGearSlot.WEAPON, false)
            assertEquals(CoreGearSlot.WEAPON, CoreLoopItems.gearSlot(packed))
            assertEquals("weapon", packed.getTag(CoreLoopItems.actionTag))
            assertNotEquals(fallback.get(DataComponents.ITEM_MODEL), packed.get(DataComponents.ITEM_MODEL))
            assertEquals("projects:weapons/greatsword_t$tier", packed.get(DataComponents.ITEM_MODEL).toString())
            assertEquals(CoreLoopItems.weapon(tier).get(DataComponents.ITEM_MODEL), fallback.get(DataComponents.ITEM_MODEL))
            val text = plain(fallback.get(DataComponents.CUSTOM_NAME)!!) + fallback.get(DataComponents.LORE)!!.joinToString("\n") { plain(it) }
            assertTrue("+30" in text)
            assertTrue("強化 +30/30" in text)
            assertFalse(text.any { it.code in 0xE000..0xF8FF })
            assertFalse(text.contains("Shift"))
        }
        val ordinary = ItemStack.of(Material.STONE_SWORD)
        assertEquals(ordinary, CoreWeaponPresentation.skin(ordinary, 1, false))
        val customSkill = CoreUiItemSkin.apply(ordinary, CoreUiIcon.SLAM, true)
        assertEquals(ordinary, CoreUiItemSkin.apply(customSkill, CoreUiIcon.SLAM, false))
    }

    @Test fun `all four models are included in distributable pack with no global weapon override`() {
        val loader = CoreWeaponPresentation::class.java.classLoader
        val index = assertNotNull(loader.getResourceAsStream("core-ui-pack/index.txt")).bufferedReader().use { it.readLines() }
        for (tier in 1..4) {
            for (path in listOf("items/weapons/greatsword_t$tier.json", "models/item/weapons/greatsword_t$tier.json")) {
                assertTrue("assets/projects/$path" in index)
                assertNotNull(loader.getResourceAsStream("core-ui-pack/assets/projects/$path")).close()
            }
        }
        assertFalse(index.any { it.startsWith("assets/minecraft/items/") || it.startsWith("assets/minecraft/models/") })
    }
}
