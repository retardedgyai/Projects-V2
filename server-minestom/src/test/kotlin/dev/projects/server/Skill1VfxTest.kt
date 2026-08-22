package dev.projects.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Skill1VfxTest {
    @Test
    fun `travel always starts while stomp and escape require a confirmed hit`() {
        val miss = skill1VfxPlan(confirmedHit = false)
        val hit = skill1VfxPlan(confirmedHit = true)

        assertEquals(SKILL1_TRAVEL_VFX, miss.travel)
        assertEquals(null, miss.stomp)
        assertEquals(null, miss.escape)
        assertEquals(SKILL1_STOMP_VFX, hit.stomp)
        assertEquals(SKILL1_ESCAPE_VFX, hit.escape)
    }

    @Test
    fun `miss has no stomp or escape sound cues`() {
        val miss = skill1SoundPlan(confirmedHit = false)

        assertTrue(miss.travel.isNotEmpty())
        assertTrue(miss.stomp.isEmpty())
        assertTrue(miss.escape.isEmpty())
    }

    @Test
    fun `confirmed hit uses wind impact and escape sound hierarchy`() {
        val hit = skill1SoundPlan(confirmedHit = true)

        assertEquals(listOf("item.trident.hit", "entity.player.attack.strong", "item.axe.scrape"), hit.stomp.map { it.key })
        assertEquals(listOf("item.trident.throw", "item.trident.riptide_1"), hit.escape.map { it.key })
        assertTrue(hit.stomp.none { it.key == "block.note_block.chime" })
        assertTrue(hit.escape.none { it.key == "entity.player.levelup" })
    }
}
