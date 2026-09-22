package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import net.minestom.server.sound.SoundEvent
import kotlin.test.*

class CoreSkillAudioTest {
    private fun effect(id: String,pulse: Int=0,phase: CoreSkillVisualPhase=CoreSkillVisualPhase.PULSE): CoreSkillEffect {
        val skill=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon==if(id.startsWith("normal_")) "dash" else id }
        return CoreSkillEffect(CoreClass.WARRIOR,skill,Vec.ZERO,Vec(0.0,0.0,1.0),phase,pulse,sceneId=id)
    }
    @Test fun `normal three hit sequence has audible layers and a distinct heavy body`() {
        val a=CoreSkillAudio.warriorCues(effect("normal_sweep"))
        val b=CoreSkillAudio.warriorCues(effect("normal_reverse"))
        val c=CoreSkillAudio.warriorCues(effect("normal_finish"))
        assertEquals(2,a.size);assertEquals(2,b.size);assertEquals(3,c.size)
        assertNotEquals(a,b)
        assertTrue(c.any { it.event==SoundEvent.ENTITY_IRON_GOLEM_ATTACK })
        assertTrue((a+b+c).all { it.volume in .7f..1f })
    }
    @Test fun `air cuts do not play the hit impact and multi hits follow their pulse`() {
        for(id in CoreWarriorBladeChoreography.sceneIds) {
            val air=CoreSkillAudio.warriorCues(effect(id))
            val hit=CoreSkillAudio.warriorCues(effect(id,phase=CoreSkillVisualPhase.CONTACT))
            assertTrue(air.none { it.event==SoundEvent.ITEM_TRIDENT_HIT })
            assertTrue(hit.any { it.event==SoundEvent.ITEM_TRIDENT_HIT })
        }
        assertEquals(3,(0..2).map { CoreSkillAudio.warriorCues(effect("whirl",it)) }.distinct().size)
    }
    @Test fun `support cues describe guard voice and planted cloth without fake attack hits`() {
        val signatures=CoreWarriorSupportChoreography.sceneIds.map { id ->
            val prepare=CoreSkillAudio.warriorCues(effect(id,phase=CoreSkillVisualPhase.PREPARE))
            val release=CoreSkillAudio.warriorCues(effect(id))
            assertEquals(1,prepare.size)
            assertTrue(release.size in 2..3)
            assertTrue(release.none { it.event in setOf(SoundEvent.ITEM_TRIDENT_HIT,SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP) })
            assertTrue((prepare+release).all { it.volume in .25f..1f && it.pitch in .6f..1.5f })
            if(id!="war_guard") assertTrue(CoreSkillAudio.warriorCues(effect(id,phase=CoreSkillVisualPhase.CONTACT)).isEmpty())
            release
        }
        assertEquals(3,signatures.distinct().size)
    }
    @Test fun `thrust has no sweep sound and only ground finishers add fracture audio`() {
        assertTrue(CoreSkillAudio.warriorCues(effect("war_breach")).none { it.event==SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP })
        assertEquals(3,(0..2).map { CoreSkillAudio.warriorCues(effect("war_ult",it)) }.distinct().size)
        for(pulse in 0..2) assertEquals(pulse==2,CoreSkillAudio.warriorCues(effect("war_ult",pulse)).any { it.event==SoundEvent.BLOCK_STONE_BREAK })
        assertTrue(CoreSkillAudio.warriorCues(effect("slam")).any { it.event==SoundEvent.BLOCK_STONE_BREAK })
        assertTrue(CoreSkillAudio.warriorCues(effect("normal_finish")).none { it.event==SoundEvent.BLOCK_STONE_BREAK })
    }
}
