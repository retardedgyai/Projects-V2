package dev.projects.server.coreloop

import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

/**
 * Every save format the core loop ever wrote (v1..v14) loads with today's code and keeps what it held.
 * The files in `src/test/resources/save-compat/` are byte-exact saves in each version's historical layout
 * (row order, columns, checksum), written by `make_fixtures.py` from the encoder at the commit that introduced
 * the version. They are never regenerated from the current encoder, so a decoder change that breaks an old
 * save fails here.
 */
class CoreSaveCompatibilityTest {
    private val player = UUID.fromString("5a1e0000-0000-4000-8000-000000000001")
    private val m1 = UUID.fromString("3a000000-0000-4000-8000-000000000001")
    private val m2 = UUID.fromString("3a000000-0000-4000-8000-000000000002")
    private val run = UUID.fromString("4a000000-0000-4000-8000-000000000001")
    private val receipt = UUID.fromString("6a000000-0000-4000-8000-000000000001")
    private val weapon = UUID.fromString("7a000000-0000-4000-8000-000000000001")
    private val armor = UUID.fromString("7a000000-0000-4000-8000-000000000002")
    private val head = UUID.fromString("7a000000-0000-4000-8000-000000000011")
    private val s1 = UUID.fromString("8a000000-0000-4000-8000-000000000001")
    private val s2 = UUID.fromString("8a000000-0000-4000-8000-000000000002")
    private val offers = (1..3).map { UUID.fromString("aa000000-0000-4000-8000-00000000000$it") }
    private val orders = (1..3).map { UUID.fromString("ba000000-0000-4000-8000-00000000000$it") }

    private val fixtures = (1..14).map { "v%02d".format(it) to it } +
        listOf("v13-skill-levels" to 13, "v12-retired-orbit" to 12)

    private fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream("save-compat/$name.account")) { "fixture $name がありません" }.use { it.readAllBytes() }

    private fun load(name: String): CoreAccount {
        val dir = Files.createTempDirectory("save-compat-$name-")
        Files.write(dir.resolve("$player.account"), bytes(name))
        return when (val result = CoreAccountRepository(dir).load(player)) {
            is CoreRepositoryLoad.Loaded -> result.account
            else -> fail("$name を読めません: $result")
        }
    }

    @Test fun `every fixture is in the version it claims`() {
        fixtures.forEach { (name, version) ->
            assertTrue(String(bytes(name), Charsets.UTF_8).startsWith("PROJECTS_CORE_LOOP\t$version\t$player\t7\n"), name)
        }
    }

    @Test fun `every version loads and keeps the fields it saved`() {
        val failures = fixtures.mapNotNull { (name, version) ->
            runCatching { check(name, version, load(name)) }.exceptionOrNull()?.let { "$name: ${it.message}" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun `every version saves again in the current format and reloads unchanged`() {
        fixtures.forEach { (name, version) ->
            val dir = Files.createTempDirectory("save-compat-resave-$name-")
            val file = dir.resolve("$player.account"); Files.write(file, bytes(name))
            val repository = CoreAccountRepository(dir)
            val loaded = assertIs<CoreRepositoryLoad.Loaded>(repository.load(player), name).account
            assertEquals(CoreRepositorySave.Saved, repository.commit(loaded.revision, loaded.copy(revision = loaded.revision + 1)), name)
            assertTrue(Files.readString(file).startsWith("PROJECTS_CORE_LOOP\t14\t"), name)
            val again = assertIs<CoreRepositoryLoad.Loaded>(CoreAccountRepository(dir).load(player), name).account
            assertEquals(CoreAccountCodec.encode(loaded.copy(revision = 8)), CoreAccountCodec.encode(again), name)
            check(name, version, again, resaved = true)
            // The first write keeps the exact old bytes beside the new save.
            if (version < 14) assertContentEquals(bytes(name), Files.readAllBytes(dir.resolve("$player.account.v$version.bak")), name)
        }
    }

    private fun check(name: String, v: Int, a: CoreAccount, resaved: Boolean = false) {
        assertEquals(player, a.playerId)
        assertEquals(if (resaved) 8 else 7, a.revision)
        assertEquals(listOf(2, 2, 2), listOf(a.weaponTier, a.armorTier, a.unlockedMapTier))
        assertEquals(40, a.amount(CoreResource.ORE)); assertEquals(5, a.amount(CoreResource.INGOT, 2)); assertEquals(3, a.amount(CoreResource.POTION))
        val map = a.maps.single()
        assertEquals(listOf<Any?>(m1, 12345L, 2, if (v >= 8) 15 else 11, if (v >= 12) 3 else null), listOf(map.id, map.seed, map.tier, map.level, map.zone))
        assertEquals(listOf(CoreMapModifier("mining", "amount", 20), CoreMapModifier(null, "quality", 10)), map.modifiers)
        val active = assertNotNull(a.activeRun)
        assertEquals(listOf<Any?>(run, m2, false), listOf(active.id, active.map.id, active.bossDefeated))
        assertEquals(when (v) { in 3..4 -> "rift"; 14 -> CoreDungeons.SAKURA; else -> null }, active.trialId)
        assertEquals("鍛冶を終えました", a.receipts.getValue(receipt).message)
        assertEquals(setOf("boss:t1:harbor-warden"), a.claimedSources)
        if (v == 1) { assertTrue(a.affixStones.isEmpty() && a.equippedAffixes.isEmpty()); return }

        // v2: loose and equipped MOD stones. The single v2 armor became the chest (v11).
        assertEquals(listOf("projects:precision" to 15.0), a.affixStones.map { it.modId to it.value })
        val equipped = a.equippedAffixes.map { Triple(it.gear, it.stone.modId, it.stone.value) }
        assertTrue(Triple(CoreGearSlot.WEAPON, "projects:force", 11.0) in equipped, "$equipped")
        assertTrue(Triple(CoreGearSlot.CHEST, "projects:guard", 2.0) in equipped, "$equipped")
        if (name == "v12-retired-orbit") {
            assertTrue(Triple(CoreGearSlot.WEAPON, "projects:orbit", 2.0) in equipped, "the retired stone stays on the weapon")
            assertTrue(CoreGearSlot.WEAPON in a.legacyLayouts, "the weapon must be rerolled to the new layout")
        }
        if (v < 3) {
            assertEquals(CoreCraftingCatalog.legacySeed(player), a.craftingSeed)
            assertEquals(CoreGearRarity.MAGIC, a.armorRarity)
            return
        }

        // v3: crafting state and currencies.
        assertEquals(listOf(CoreGearRarity.MAGIC, CoreGearRarity.MAGIC), listOf(a.weaponRarity, a.armorRarity))
        assertEquals(424242L, a.craftingSeed)
        assertEquals(listOf(3L, 1L, 2L), listOf(a.amount(CoreCraftingCurrency.TRANSMUTATION), a.amount(CoreCraftingCurrency.ALCHEMY), a.amount(CoreActivityKind.RIFT)))
        if (v < 4) return

        // v4: enhancement.
        assertEquals(CoreEnhancementState(7, 2), a.weaponEnhancement); assertEquals(CoreEnhancementState(3, 0), a.armorEnhancement)
        assertEquals(40L, a.smithingXp)
        if (v < 5) { assertEquals(legacyJourney(), a.journey); return }

        // v5: economy, identities, stored gear and market offers.
        assertEquals(20400L, a.deliveryDay); assertEquals(1, a.deliveries)
        assertEquals(listOf(weapon, armor), listOf(a.weaponIdentity.id, a.armorIdentity.id))
        // v5 kept 0..100 wear, retired without reinterpreting it as breakage.
        assertEquals(listOf(false, v >= 6), listOf(a.weaponBroken, a.armorBroken))
        val stored = a.storedGear.associateBy { it.identity.id }
        assertEquals(listOf<Any?>(CoreGearSlot.WEAPON, 1, CoreGearRarity.MAGIC, listOf(7.0)),
            stored.getValue(s1).let { listOf(it.slot, it.tier, it.rarity, it.affixes.map { x -> x.stone.value }) })
        assertEquals(CoreGearSlot.CHEST, stored.getValue(s2).slot)
        // A v5..v10 stored armor was a whole set: it splits into four pieces; the offer on it is withdrawn.
        assertEquals(if (v < 11) 5 else 2, a.storedGear.size)
        assertEquals(if (v < 11) offers.take(2) else offers, a.offers.map { it.id })
        if (v < 11) assertTrue(a.armorParts.values.all { it.tier == 2 && it.rarity == CoreGearRarity.MAGIC && it.enhancement == CoreEnhancementState(3, 0) && it.broken == (v >= 6) })
        // v7..v10 whole-set buy orders are refunded (250 x 2) rather than reread as a chest order.
        assertEquals(if (v in 7..10) 1700L else 1200L, a.silver)
        if (v < 7) { assertEquals(legacyJourney(), a.journey); return }

        // v7: survey, professions, buy orders, quality. Retired dungeon rows are dropped.
        assertEquals(12L, a.surveyPoints)
        assertEquals(CoreProfessionProgress(400, 3), a.professions[CoreProfession.SMELTING])
        assertEquals(CoreProfessionProgress(90, 0), a.professions[CoreProfession.WEAPONSMITH])
        assertEquals(if (v < 11) orders.take(2) else orders, a.buyOrders.map { it.id })
        assertEquals("greatsword", a.buyOrders.single { it.id == orders[1] }.family)
        if (v >= 11) assertEquals(CoreGearSlot.CHEST, a.buyOrders.single { it.id == orders[2] }.slot)
        assertEquals(listOf(12, 6, 4), listOf(a.weaponIdentity.quality, a.armorIdentity.quality, stored.getValue(s1).identity.quality))
        if (v < 8) { assertEquals(legacyJourney(), a.journey); return }

        // v8: journey, weapon bases, item and map levels.
        assertEquals(listOf<Any>(CoreClass.WARRIOR, true, 9000L, 5, false), a.journey.let { listOf(it.job, it.chosen, it.xp, it.lessons, it.legacy) })
        assertEquals(17, a.journey.level)
        assertEquals(CoreWeaponBase.CLEAVER to 14, a.weaponIdentity.let { it.base to it.itemLevel })
        assertEquals(CoreWeaponBase.FLOW to 5, stored.getValue(s1).identity.let { it.base to it.itemLevel })
        if (v < 9) return

        // v9: class builds. Old tree points are refunded; the retired 大地崩し (index 5) is swapped for a live skill.
        val build = a.journey.build
        assertEquals(listOf(0, 1, 2), build.skills.take(3))
        if (v < 14) assertEquals(3, build.fourth, "retired index 5 becomes the first free selectable skill")
        assertTrue(build.skills.all { it in CoreSkillCatalog.normalChoices(CoreClass.WARRIOR) })
        assertEquals(1, build.ultimate)
        assertEquals(1, assertNotNull(a.journey.savedBuilds[CoreClass.MAGE]).ultimate)
        if (v < 11) return

        // v11: separate armor pieces.
        assertEquals(head, a.armorParts.getValue(CoreGearSlot.HEAD).identity.id)
        assertEquals(CoreEnhancementState(2, 0), a.armorParts.getValue(CoreGearSlot.HEAD).enhancement)
        assertEquals(1, a.armorParts.getValue(CoreGearSlot.HEAD).identity.quality)
        if (v < 12) return

        // v12: zone clears.
        assertEquals(CoreZoneRecord(2, 15), a.zoneRecords[3])
        if (v < 13) return

        // v13: passives from another tree are refunded; skill levels of retired skills are refunded too.
        assertTrue(build.passives.isEmpty() && a.journey.savedBuilds.values.all { it.passives.isEmpty() })
        when (name) {
            "v13-skill-levels" -> assertEquals(mapOf("dash" to 2), build.skillLevels)
            "v14" -> { assertEquals(mapOf("dash" to 3), build.skillLevels); assertEquals(mapOf(CoreDungeons.SAKURA to 2), a.dungeonClears) }
            else -> assertTrue(build.skillLevels.isEmpty())
        }
    }

    private fun legacyJourney() = CoreJourney.legacy(xp = CoreJourneyRules.threshold(CoreJourneyRules.floor(2)))
}
