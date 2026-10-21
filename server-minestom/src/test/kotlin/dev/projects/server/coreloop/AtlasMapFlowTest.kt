package dev.projects.server.coreloop

import com.google.gson.JsonParser
import dev.projects.webui.UiScene
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtlasMapFlowTest {
    private fun resource(name: String) = requireNotNull(javaClass.classLoader.getResourceAsStream(name)).use { it.readBytes() }
    private val atlas = AtlasMapFlow.Atlas(resource("polish05/atlas-map.json"), resource("polish05/font-map.json"))

    private class Probe(var account: CoreAccount?) {
        val departed = mutableListOf<Int>()
        var left = 0
    }
    private fun flow(probe: Probe) = AtlasMapFlow(atlas, { probe.account }, { probe.departed += it }, { probe.left++ })
    private fun UiScene.text(id: String) = nodes.singleOrNull { it.id == id }?.text
    private fun UiScene.texts() = nodes.filter { it.text.isNotEmpty() }.map { it.text }

    @Test fun `only real destinations are drawn and the retired placeholders are gone`() {
        val scene = flow(Probe(CoreAccount(UUID.randomUUID()))).scene()
        val drawn = scene.nodes.filter { it.action?.startsWith("zone:") == true }.map { it.action!!.removePrefix("zone:").toInt() }.toSet()
        assertTrue(drawn.isNotEmpty())
        assertTrue(drawn.all { CoreFrontierZones.zone(it) != null }, "wilderness markers must stay hidden: $drawn")
        val all = scene.texts().joinToString("|")
        for (gone in listOf("出現中", "12分", "霧氷の騎士", "領地", "ギルド", "土 21:00", "〜40人")) assertFalse(gone in all, gone)
    }

    @Test fun `panel reads the zone and the account and departs to that zone`() {
        val probe = Probe(CoreAccount(UUID.randomUUID()))
        val flow = flow(probe)
        val zone = CoreFrontierZones.expeditions.first { it.tier == 1 }
        assertFalse(flow.action("zone:5"), "a wilderness marker is not selectable")
        assertTrue(flow.action("zone:${zone.id}"))
        val scene = flow.scene()
        assertEquals(zone.name, scene.text("p-name"))
        assertEquals(zone.boss!!.displayName, scene.text("p-card-n"))
        assertTrue(scene.text("p-sub")!!.contains(zone.specialty!!.displayName))
        assertEquals("無料", scene.text("p-i0-v"))
        assertEquals("まだ踏破していない", scene.text("p-i2-v"))
        assertEquals("×4", scene.text("p-m0-n"))
        assertEquals("遠征に出る", scene.text("p-go-t"))
        assertTrue(flow.action("go"))
        assertEquals(listOf(zone.id), probe.departed)

        probe.account = probe.account!!.copy(zoneRecords = mapOf(zone.id to CoreZoneRecord(3, 8)))
        flow.scene().let {
            assertEquals("踏破済み", it.text("p-type-t"))
            assertEquals("踏破 3 回 · 最高 敵Lv 8", it.text("p-i2-v"))
            assertNotNull(it.nodes.singleOrNull { n -> n.id == "z${zone.id}-ok" })
        }
    }

    @Test fun `a locked tier cannot depart and says how it opens`() {
        val probe = Probe(CoreAccount(UUID.randomUUID()))
        val flow = flow(probe)
        val zone = CoreFrontierZones.expeditions.first { it.tier == 3 }
        assertTrue(flow.action("zone:${zone.id}"))
        val scene = flow.scene()
        assertEquals("未解放", scene.text("p-type-t"))
        assertEquals(CoreFrontierZones.unlockHint(3), scene.text("p-i0-v"))
        assertEquals("T3 は未解放", scene.text("p-go-t"))
        assertNull(scene.nodes.singleOrNull { it.id == "p-go" })
        assertFalse(flow.action("go"))
        assertTrue(probe.departed.isEmpty())
    }

    @Test fun `the harbor panel summarises progress and only returns`() {
        val probe = Probe(CoreAccount(UUID.randomUUID(), unlockedMapTier = 2, zoneRecords = mapOf(26 to CoreZoneRecord(2, 5), 27 to CoreZoneRecord(1, 4))))
        val flow = flow(probe)
        val hub = CoreFrontierZones.all.single { it.hub }
        assertTrue(flow.action("zone:${hub.id}"))
        val scene = flow.scene()
        assertEquals("T2 まで", scene.text("p-i0-v"))
        assertEquals("2 / ${CoreFrontierZones.expeditions.size} 地域", scene.text("p-i1-v"))
        assertEquals("ボス討伐 合計 3 回", scene.text("p-i2-v"))
        assertTrue(flow.action("go"))
        assertEquals(1, probe.left)
        assertTrue(probe.departed.isEmpty())
    }

    /** The v3 fonts are subsets; a missing glyph falls back to the vanilla font and breaks the look. */
    @Test fun `every string the atlas can show exists in its v3 font`() {
        val metrics = JsonParser.parseString(resource("polish05-font-metrics.json").toString(Charsets.UTF_8)).asJsonObject
        val accounts = listOf(CoreAccount(UUID.randomUUID()),
            CoreAccount(UUID.randomUUID(), unlockedMapTier = 2, zoneRecords = CoreFrontierZones.expeditions.associate { it.id to CoreZoneRecord(1, 1) }))
        val missing = sortedSetOf<String>()
        for (account in accounts) for (zone in CoreFrontierZones.all) {
            val flow = flow(Probe(account))
            flow.action("zone:${zone.id}")
            for (node in flow.scene().nodes.filter { it.text.isNotEmpty() }) {
                val family = node.style.getValue("font-family").removePrefix("projects_ui_polish05:")
                val glyphs = metrics.getAsJsonObject(family)
                node.text.codePoints().forEach { cp -> if (cp > 32 && !glyphs.has(cp.toString())) missing += "${String(Character.toChars(cp))} ($family)" }
            }
        }
        assertTrue(missing.isEmpty(), "missing glyphs: $missing")
    }
}
