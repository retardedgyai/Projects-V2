package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.server.mob.QuestMobArchetype
import dev.projects.server.questmap.QuestGatheringDiscipline
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreFrontierZonesTest {
    private class Fixture {
        val repository = CoreAccountRepository(Files.createTempDirectory("projects-frontier-test"))
        val player: UUID = UUID.randomUUID()
        val service = CoreAccountService(repository)
        init { assertIs<CoreAccountLoadResult.Ready>(service.open(player)) }
        val account: CoreAccount get() = assertNotNull(service.snapshot(player))
        fun perform(action: CoreAction) = service.transact(player, CoreOperation(UUID.randomUUID(), account.revision, action))
        fun commit(action: CoreAction): CoreTransactionResult = perform(action).also {
            assertEquals(CoreTransactionStatus.COMMITTED, it.status, it.message)
        }
        /** Claim for [zone], depart, beat the boss, come home. Returns the boss reward message and the map level. */
        fun clear(zone: CoreFrontierZone, seed: Long = 7): Pair<String, Int> {
            val map = commit(CoreAction.ClaimMap(zone.tier, seed, zone = zone.id)).account!!.maps.last()
            assertEquals(zone.id, map.zone)
            val run = UUID.randomUUID()
            commit(CoreAction.StartRun(map.id, run))
            val message = commit(CoreAction.BossReward(run)).message
            commit(CoreAction.FinishRun(run))
            return message to map.level
        }
    }

    private fun atlasJson() = JsonParser.parseString(requireNotNull(javaClass.classLoader.getResourceAsStream("polish05/atlas-map.json"))
        .use { it.readBytes() }.toString(UTF_8)).asJsonObject.getAsJsonArray("zones").map { it.asJsonObject }

    @Test fun `every painted destination has exactly one zone with the painted tier and terrain`() {
        val painted = atlasJson()
        val byId = painted.associateBy { it["id"].asInt }
        for (zone in CoreFrontierZones.all) {
            val marker = assertNotNull(byId[zone.id], "zone ${zone.id} has no marker")
            assertFalse(marker["pvp"].asBoolean)
            assertEquals(marker["tier"].asInt, zone.tier, zone.name)
            assertEquals(marker["biome"].asString, zone.terrain.name, zone.name)
            assertEquals(marker["hub"].asBoolean, zone.hub, zone.name)
        }
        // Wilderness markers stay off the map until PvP exists; every other marker is a real destination.
        assertEquals(painted.filterNot { it["pvp"].asBoolean }.map { it["id"].asInt }.toSet(), CoreFrontierZones.all.map { it.id }.toSet())
        assertEquals(CoreFrontierZones.all.size, CoreFrontierZones.all.map { it.name }.distinct().size)
    }

    @Test fun `every tier offers every boss and from T2 every gathering specialty`() {
        for (tier in 1..4) {
            val zones = CoreFrontierZones.expeditions.filter { it.tier == tier }
            assertEquals(CoreFrontierZones.BOSSES.toSet(), zones.mapNotNull { it.boss }.toSet(), "T$tier bosses")
            if (tier >= 2) assertEquals(QuestGatheringDiscipline.entries.toSet(), zones.mapNotNull { it.specialty }.toSet(), "T$tier specialties")
        }
        assertTrue(CoreFrontierZones.BOSSES.all { it.rarity == dev.projects.server.mob.QuestMobRarity.BOSS })
    }

    @Test fun `the specialty takes half of the base gathering sites and every material stays available`() {
        for (specialty in QuestGatheringDiscipline.entries) {
            val sites = CoreFrontierZones.gatheringSites(specialty)
            assertEquals(specialty, sites.keys.first())
            assertEquals(4, sites.getValue(specialty))
            assertEquals(QuestGatheringDiscipline.entries.toSet(), sites.keys)
            assertTrue(sites.filterKeys { it != specialty }.values.all { it == 1 })
            assertEquals(CoreFrontierZones.GATHERING_SITES, sites.values.sum())
        }
        // Without a zone the old round robin is unchanged.
        assertEquals((0 until 8).map { QuestGatheringDiscipline.forGatheringOrdinal(it) },
            (0 until 8).map { QuestGatheringDiscipline.forGatheringOrdinal(it, null) })
    }

    @Test fun `departure mirrors the claim rules - tier lock, ticket price and level`() {
        val fresh = CoreAccount(UUID.randomUUID())
        val t1 = CoreFrontierZones.expeditions.first { it.tier == 1 }
        val t2 = CoreFrontierZones.expeditions.first { it.tier == 2 }
        CoreFrontierZones.departure(fresh, t1).let { assertNull(it.blocked); assertNull(it.cost); assertEquals(1, it.level) }
        assertEquals("T2 は未解放", CoreFrontierZones.departure(fresh, t2).blocked)
        val unlocked = fresh.copy(unlockedMapTier = 2)
        CoreFrontierZones.departure(unlocked, t2).let {
            assertEquals("戦利品券が足りない", it.blocked)
            assertEquals(CoreMaterial(CoreResource.COMBAT_TOKEN, 1), it.cost)
            assertEquals(CoreJourneyRules.floor(2), it.level)
        }
        val paid = unlocked.copy(balances = unlocked.balances + (CoreMaterial(CoreResource.COMBAT_TOKEN, 1) to 1L))
        assertNull(CoreFrontierZones.departure(paid, t2).blocked)
        assertNotNull(CoreFrontierZones.departure(paid, CoreFrontierZones.all.single { it.hub }).blocked)
    }

    @Test fun `a zone claim keeps its zone and a boss clear is recorded once per run and survives restart`() {
        val f = Fixture()
        val zone = CoreFrontierZones.expeditions.first { it.tier == 1 }
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ClaimMap(2, 1, zone = zone.id)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ClaimMap(1, 1, zone = CoreFrontierZones.all.single { it.hub }.id)).status)
        assertEquals(CoreTransactionStatus.REJECTED, f.perform(CoreAction.ClaimMap(1, 1, zone = 5)).status) // a wilderness marker
        val (first, firstLevel) = f.clear(zone)
        assertTrue(first.startsWith("${zone.name}を初踏破！"), first)
        assertEquals(CoreZoneRecord(1, firstLevel), f.account.zoneRecords[zone.id])
        val (second, secondLevel) = f.clear(zone, seed = 8)
        assertFalse(second.contains("初踏破"))
        // The first boss levels the player up, so the second map is drawn at a higher enemy level.
        assertTrue(secondLevel > firstLevel)
        assertEquals(CoreZoneRecord(2, secondLevel), f.account.zoneRecords[zone.id])
        f.service.forget(f.player)
        val reopened = assertIs<CoreAccountLoadResult.Ready>(f.service.open(f.player)).account
        assertEquals(mapOf(zone.id to CoreZoneRecord(2, secondLevel)), reopened.zoneRecords)
        // A map without a zone (boss reward maps, the old menu) records nothing.
        val reward = reopened.maps.first { it.zone == null }
        val run = UUID.randomUUID()
        f.commit(CoreAction.StartRun(reward.id, run))
        f.commit(CoreAction.BossReward(run))
        assertEquals(1, f.account.zoneRecords.size)
    }

    @Test fun `best level only rises and clears saturate`() {
        assertEquals(CoreZoneRecord(2, 15), CoreZoneRecord(1, 15).cleared(12))
        assertEquals(CoreZoneRecord(2, 18), CoreZoneRecord(1, 15).cleared(18))
        assertEquals(CoreZoneRecord.MAX_CLEARS, CoreZoneRecord(CoreZoneRecord.MAX_CLEARS, 1).cleared(1).clears)
        assertFailsWith<IllegalArgumentException> { CoreZoneRecord(0, 1) }
        assertFailsWith<IllegalArgumentException> { CoreOwnedMap(UUID.randomUUID(), 1, 1, zone = 999) }
    }

    @Test fun `v12 rows round trip and a v11 save opens with an empty record`() {
        val id = UUID.randomUUID()
        val zone = CoreFrontierZones.expeditions.first { it.tier == 2 }
        val map = CoreOwnedMap(UUID.randomUUID(), 42, 2, level = 13, zone = zone.id)
        val active = CoreOwnedMap(UUID.randomUUID(), 43, 1, zone = 26)
        val account = CoreAccount(id, revision = 3, unlockedMapTier = 2, maps = listOf(map),
            activeRun = CoreActiveRun(UUID.randomUUID(), active), zoneRecords = mapOf(26 to CoreZoneRecord(3, 7), zone.id to CoreZoneRecord(1, 13)))
        val encoded = CoreAccountCodec.encode(account)
        assertTrue(encoded.startsWith("PROJECTS_CORE_LOOP\t13\t"))
        val decoded = CoreAccountCodec.decode(encoded, id)
        assertEquals(account.zoneRecords, decoded.zoneRecords)
        assertEquals(zone.id, decoded.maps.single().zone)
        assertEquals(26, decoded.activeRun?.map?.zone)
        assertEquals(map.toString(), decoded.maps.single().toString())
        assertEquals(encoded, CoreAccountCodec.encode(decoded))

        fun sealed(body: String) = body + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) } + "\n"
        val v11 = encoded.substringBefore("checksum\t").lineSequence()
            .filterNot { it.startsWith("map-zone\t") || it.startsWith("zone-record\t") || it.startsWith("passive") }
            .joinToString("\n").replaceFirst("PROJECTS_CORE_LOOP\t13\t", "PROJECTS_CORE_LOOP\t11\t")
        val migrated = CoreAccountCodec.decode(sealed(v11), id)
        assertTrue(migrated.zoneRecords.isEmpty())
        assertNull(migrated.maps.single().zone)
        assertNull(migrated.activeRun?.map?.zone)
        assertTrue(CoreAccountCodec.encode(migrated).startsWith("PROJECTS_CORE_LOOP\t13\t"))
        // v12 rows are not accepted in an older header, and a zone needs its map.
        assertFailsWith<IllegalArgumentException> {
            CoreAccountCodec.decode(sealed(v11 + "zone-record\t26\t1\t1\n"), id)
        }
        assertFailsWith<IllegalArgumentException> {
            CoreAccountCodec.decode(sealed(encoded.substringBefore("checksum\t") + "map-zone\t${UUID.randomUUID()}\t26\n"), id)
        }
    }

    @Test fun `a v11 file is backed up before the first v12 write`() {
        val directory = Files.createTempDirectory("projects-frontier-v11")
        val id = UUID.randomUUID()
        val current = CoreAccountCodec.encode(CoreAccount(id, revision = 1)).substringBefore("checksum\t")
            .lineSequence().filterNot { it.startsWith("passive") }.joinToString("\n")
            .replaceFirst("PROJECTS_CORE_LOOP\t13\t", "PROJECTS_CORE_LOOP\t11\t")
        val bytes = (current + "checksum\t" + MessageDigest.getInstance("SHA-256").digest(current.toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) } + "\n").toByteArray(UTF_8)
        Files.write(directory.resolve("$id.account"), bytes)
        val repository = CoreAccountRepository(directory)
        val loaded = assertIs<CoreRepositoryLoad.Loaded>(repository.load(id)).account
        assertEquals(CoreRepositorySave.Saved, repository.commit(1, loaded.copy(revision = 2, zoneRecords = mapOf(26 to CoreZoneRecord(1, 1)))))
        assertTrue(Files.readAllBytes(directory.resolve("$id.account.v11.bak")).contentEquals(bytes))
        assertEquals(mapOf(26 to CoreZoneRecord(1, 1)), assertIs<CoreRepositoryLoad.Loaded>(repository.load(id)).account.zoneRecords)
    }

    @Test fun `zone bosses are the quest-map road bosses`() {
        assertEquals(listOf(QuestMobArchetype.EXECUTIONER, QuestMobArchetype.IRON_WARDEN, QuestMobArchetype.RIFT_ORACLE), CoreFrontierZones.BOSSES)
    }
}
