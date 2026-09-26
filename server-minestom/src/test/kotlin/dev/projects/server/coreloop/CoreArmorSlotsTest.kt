package dev.projects.server.coreloop

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.item.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoreArmorSlotsTest {
    @Test fun defaultMenuIconMaterialMatchesEachArmorPart() {
        MinecraftServer.init(Auth.Offline())
        val account = CoreAccount(UUID.randomUUID())
        val expected = listOf(Material.IRON_HELMET, Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS, Material.IRON_BOOTS)
        assertEquals(expected, CoreGearSlot.armorSlots.map { slot ->
            CoreLoopItems.gear(account, slot, packed = true).material()
        })
    }
    @Test fun eachPartKeepsItsOwnTierQualityEnhancementAndCombatContribution() {
        val id = UUID.randomUUID()
        val original = CoreAccount(id, armorTier = 2, armorEnhancement = CoreEnhancementState(6),
            armorIdentity = CoreGearIdentity.legacy(id, CoreGearSlot.ARMOR).copy(quality = 9))
        val baseline = CoreCombatSheet.from(original)
        val head = original.armor(CoreGearSlot.HEAD)
        val changed = original.withArmor(CoreGearSlot.HEAD, head.copy(tier = 3,
            enhancement = CoreEnhancementState(7), identity = head.identity.copy(quality = 12)))
        assertEquals(2, changed.armor(CoreGearSlot.CHEST).tier)
        assertEquals(6, changed.armor(CoreGearSlot.LEGS).enhancement.level)
        assertEquals(9, changed.armor(CoreGearSlot.FEET).identity.quality)
        assertTrue(CoreCombatSheet.from(changed).health > baseline.health)
        assertTrue(CoreCombatSheet.from(changed).ar > baseline.ar)
        val encoded = CoreAccountCodec.encode(changed)
        assertEquals(encoded, CoreAccountCodec.encode(CoreAccountCodec.decode(encoded, id)))
    }

    @Test fun enhancementConsumesMaterialsAndChangesOnlyTheSelectedPart() {
        val id = UUID.randomUUID()
        val dir = Files.createTempDirectory("armor-four-slots-")
        val materials = CoreResource.entries.flatMap { resource -> (1..4).map { CoreMaterial(resource, it) to 100L } }.toMap()
        val repository = CoreAccountRepository(dir)
        assertEquals(CoreRepositorySave.Saved, repository.commit(0, CoreAccount(id, revision = 1, balances = materials)))
        val service = CoreAccountService(repository)
        assertTrue(service.open(id) is CoreAccountLoadResult.Ready)
        val before = requireNotNull(service.snapshot(id))
        val quote = CoreEnhancementCatalog.quote(before, CoreGearSlot.HEAD)
        val result = service.transact(id, CoreOperation(UUID.randomUUID(), before.revision,
            CoreAction.EnhanceEquipment(CoreGearSlot.HEAD)))
        assertEquals(CoreTransactionStatus.COMMITTED, result.status, result.message)
        val after = requireNotNull(result.account)
        assertEquals(1, after.armor(CoreGearSlot.HEAD).enhancement.level)
        CoreGearSlot.armorSlots.filterNot { it == CoreGearSlot.HEAD }.forEach {
            assertEquals(0, after.armor(it).enhancement.level)
        }
        quote.recipe.costs.forEach { (material, amount) ->
            assertEquals(before.amount(material) - amount, after.amount(material))
        }
        assertEquals(CoreAccountCodec.encode(after), CoreAccountCodec.encode(CoreAccountCodec.decode(CoreAccountCodec.encode(after), id)))
    }

    @Test fun manufactureEquipAndModCraftStayWithinOnePart() {
        val id = UUID.randomUUID()
        val dir = Files.createTempDirectory("armor-equip-")
        val materials = CoreResource.entries.flatMap { resource -> (1..4).map { CoreMaterial(resource, it) to 100L } }.toMap()
        val repository = CoreAccountRepository(dir)
        assertEquals(CoreRepositorySave.Saved, repository.commit(0, CoreAccount(id, revision = 1,
            balances = materials, currencies = mapOf(CoreCraftingCurrency.ALCHEMY to 1))))
        val service = CoreAccountService(repository)
        assertTrue(service.open(id) is CoreAccountLoadResult.Ready)
        fun commit(action: CoreAction): CoreAccount {
            val before = requireNotNull(service.snapshot(id))
            val result = service.transact(id, CoreOperation(UUID.randomUUID(), before.revision, action))
            assertEquals(CoreTransactionStatus.COMMITTED, result.status, result.message)
            return requireNotNull(result.account)
        }
        val oldHead = requireNotNull(service.snapshot(id)).armor(CoreGearSlot.HEAD)
        val oldChest = requireNotNull(service.snapshot(id)).armor(CoreGearSlot.CHEST)
        val made = commit(CoreAction.Manufacture(CoreGearSlot.HEAD, 1)).storedGear.single()
        assertEquals(CoreGearSlot.HEAD, made.slot)
        val equipped = commit(CoreAction.Equip(made.identity.id))
        assertEquals(made.identity.id, equipped.armor(CoreGearSlot.HEAD).identity.id)
        assertEquals(oldChest, equipped.armor(CoreGearSlot.CHEST))
        assertTrue(equipped.storedGear.any { it.identity.id == oldHead.identity.id })
        val crafted = commit(CoreAction.CraftEquipment(CoreGearSlot.HEAD, CoreCraftingCurrency.ALCHEMY))
        assertEquals(CoreGearRarity.RARE, crafted.armor(CoreGearSlot.HEAD).rarity)
        assertTrue(crafted.equippedAffixes.any { it.gear == CoreGearSlot.HEAD })
        assertEquals(oldChest, crafted.armor(CoreGearSlot.CHEST))
        assertFalse(crafted.equippedAffixes.any { it.gear == CoreGearSlot.CHEST })
    }

    @Test fun v10SetExpandsDeterministicallyAndCancelsOnlyItsListing() {
        val id = UUID.randomUUID()
        val oldIdentity = CoreGearIdentity.legacy(id, CoreGearSlot.ARMOR).copy(quality = 8)
        val storedIdentity = CoreGearIdentity(UUID.randomUUID(), id, quality = 11)
        val oldMod = CoreAffixStone(UUID.randomUUID(), "projects:vitality", 2, 22.0)
        val stored = CoreStoredGear(storedIdentity, CoreGearSlot.ARMOR, 2,
            CoreGearRarity.MAGIC, CoreEnhancementState(3), listOf(CoreEquippedAffix(CoreGearSlot.ARMOR, 0, oldMod)))
        val listing = CoreMarketOffer(UUID.randomUUID(), 250, gearId = storedIdentity.id)
        val oldOrder = CoreBuyOrder(UUID.randomUUID(), 10, 2, 2, slot = CoreGearSlot.ARMOR)
        val equippedMod = CoreAffixStone(UUID.randomUUID(), "projects:guard", 2, 3.0)
        val account = CoreAccount(id, armorTier = 2, armorEnhancement = CoreEnhancementState(6),
            armorRarity = CoreGearRarity.MAGIC,
            equippedAffixes = listOf(CoreEquippedAffix(CoreGearSlot.ARMOR, 0, equippedMod)),
            armorIdentity = oldIdentity, storedGear = listOf(stored), offers = listOf(listing),
            buyOrders = listOf(oldOrder), silver = 500)
        val current = CoreAccountCodec.encode(account).substringBefore("checksum\t")
        val newPartIds = account.armorParts.values.map { it.identity.id.toString() }.toSet()
        val oldBody = current.lineSequence().filterNot { row ->
            row.startsWith("armor-part\t") ||
                (row.startsWith("gear-quality\t") || row.startsWith("gear-base\t")) &&
                    row.split('\t').getOrNull(1) in newPartIds
        }.joinToString("\n").replaceFirst("PROJECTS_CORE_LOOP\t11\t", "PROJECTS_CORE_LOOP\t10\t")
        val checksum = MessageDigest.getInstance("SHA-256").digest(oldBody.toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) }
        val migrated = CoreAccountCodec.decode(oldBody + "checksum\t$checksum\n", id)
        assertEquals(4, CoreGearSlot.armorSlots.map { migrated.armor(it).identity.id }.distinct().size)
        assertEquals(oldIdentity.id, migrated.armor(CoreGearSlot.CHEST).identity.id)
        assertEquals(6, migrated.armor(CoreGearSlot.HEAD).enhancement.level)
        assertEquals(4, migrated.storedGear.size)
        assertTrue(migrated.storedGear.all { it.slot in CoreGearSlot.armorSlots })
        assertEquals(listOf(CoreGearSlot.CHEST), migrated.equippedAffixes.map { it.gear })
        assertEquals(listOf(CoreGearSlot.CHEST), migrated.storedGear.flatMap { it.affixes }.map { it.gear })
        assertFalse(migrated.offers.any { it.id == listing.id })
        assertTrue(migrated.buyOrders.isEmpty())
        assertEquals(520, migrated.silver)
        assertNotEquals(storedIdentity.id, migrated.storedGear.single { it.slot == CoreGearSlot.HEAD }.identity.id)
        val encoded = CoreAccountCodec.encode(migrated)
        assertEquals(encoded, CoreAccountCodec.encode(CoreAccountCodec.decode(encoded, id)))
        assertEquals(encoded, CoreAccountCodec.encode(CoreAccountCodec.decode(oldBody + "checksum\t$checksum\n", id)))

        val directory = Files.createTempDirectory("armor-v10-backup-")
        val source = directory.resolve("$id.account")
        val oldBytes = (oldBody + "checksum\t$checksum\n").toByteArray(UTF_8)
        Files.write(source, oldBytes)
        val repository = CoreAccountRepository(directory)
        val loaded = repository.load(id) as CoreRepositoryLoad.Loaded
        assertFalse(Files.exists(directory.resolve("$id.account.v10.bak")))
        assertEquals(CoreRepositorySave.Saved,
            repository.commit(loaded.account.revision, loaded.account.copy(revision = loaded.account.revision + 1)))
        assertTrue(Files.readAllBytes(directory.resolve("$id.account.v10.bak")).contentEquals(oldBytes))
        assertTrue(Files.readString(source).startsWith("PROJECTS_CORE_LOOP\t11\t"))
    }

    @Test fun cappedV10SilverKeepsOldSetOrderCancellableWithoutBlockingLoad() {
        val player = UUID.randomUUID()
        val order = CoreBuyOrder(UUID.randomUUID(), 10, 2, 2, slot = CoreGearSlot.ARMOR)
        val original = CoreAccount(player, silver = CoreEconomy.MAX_SILVER, buyOrders = listOf(order))
        val body = armorV10Body(original)
        val checksum = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) }
        val migrated = CoreAccountCodec.decode(body + "checksum\t$checksum\n", player)
        assertEquals(CoreEconomy.MAX_SILVER, migrated.silver)
        assertEquals(listOf(order), migrated.buyOrders)
        val reopened = CoreAccountCodec.decode(CoreAccountCodec.encode(migrated), player)
        assertEquals(listOf(order), reopened.buyOrders)

        val directory = Files.createTempDirectory("armor-capped-refund-")
        val repository = CoreAccountRepository(directory)
        assertEquals(CoreRepositorySave.Saved, repository.commit(0,
            reopened.copy(revision = 1, silver = reopened.silver - order.escrow)))
        val service = CoreAccountService(repository)
        assertTrue(service.open(player) is CoreAccountLoadResult.Ready)
        val result = service.transact(player, CoreOperation(UUID.randomUUID(), 1, CoreAction.CancelBuyOrder(order.id)))
        assertEquals(CoreTransactionStatus.COMMITTED, result.status, result.message)
        assertEquals(CoreEconomy.MAX_SILVER, result.account!!.silver)
        assertTrue(result.account!!.buyOrders.isEmpty())
    }
}
