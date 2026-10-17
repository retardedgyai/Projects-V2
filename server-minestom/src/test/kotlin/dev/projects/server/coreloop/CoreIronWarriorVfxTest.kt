package dev.projects.server.coreloop

import dev.projects.server.particle.ParticleSink
import dev.projects.server.particle.ParticleSpawn
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import kotlin.test.*

/** Iron-mass warrior presentation: every skill and phase draws and sounds; only the six skills are routed. */
class CoreIronWarriorVfxTest {
    private val warrior = CoreSkillCatalog.skills(CoreClass.WARRIOR).filterNot { it.retired }

    private fun spawns(effect: CoreSkillEffect): List<List<ParticleSpawn>> {
        val drawn = CoreIronWarriorVfx.effect(effect, Block.STONE)
        return (0 until drawn.durationTicks).map { tick -> mutableListOf<ParticleSpawn>().also { list -> drawn.emit(tick, ParticleSink { list += it }) } }
    }

    @Test fun `all six warrior skills are routed and other scenes keep their old path`() {
        for (s in warrior) assertTrue(CoreIronWarriorVfx.handles(CoreSkillEffect(CoreClass.WARRIOR, s, Pos.ZERO, Vec(0.0, 0.0, 1.0))), s.icon)
        val slam = warrior.first { it.icon == "slam" }
        assertFalse(CoreIronWarriorVfx.handles(CoreSkillEffect(CoreClass.WARRIOR, slam, Pos.ZERO, Vec(0.0, 0.0, 1.0), sceneId = "normal_finish")))
        val mage = CoreSkillCatalog.skills(CoreClass.MAGE).first()
        assertFalse(CoreIronWarriorVfx.handles(CoreSkillEffect(CoreClass.MAGE, mage, Pos.ZERO, Vec(0.0, 0.0, 1.0))))
    }

    @Test fun `every skill and phase draws within budget and has its own sound`() {
        for (s in warrior) for (phase in CoreSkillVisualPhase.entries) for (pulse in 0..2) {
            val effect = CoreSkillEffect(CoreClass.WARRIOR, s, Pos(0.0, 40.0, 0.0), Vec(0.0, 0.0, 1.0), phase, pulse, prepareTicks = 12)
            val ticks = spawns(effect)
            assertTrue(ticks.flatten().isNotEmpty(), "${s.icon} $phase draws")
            assertTrue(ticks.all { it.size <= 72 }, "${s.icon} $phase stays within the per-tick budget: ${ticks.maxOf { it.size }}")
            assertTrue(ticks.flatten().any { it.importance == dev.projects.server.particle.ParticleImportance.COMBAT_FEEDBACK }, "${s.icon} $phase keeps a key particle")
            assertTrue(CoreIronWarriorVfx.cues(effect).isNotEmpty(), "${s.icon} $phase sounds")
        }
    }

    @Test fun `slam cracks run forward from the impact and the ultimate's last cut is the big one`() {
        val slam = warrior.first { it.icon == "slam" }
        val first = spawns(CoreSkillEffect(CoreClass.WARRIOR, slam, Pos(0.0, 40.0, 0.0), Vec(0.0, 0.0, 1.0)))
        assertTrue(first[0].any { it.particle.key().value() == "explosion" })
        val reach = { tick: Int -> first[tick].filter { it.particle.key().value() == "dust_color_transition" }.maxOf { it.position.z() } }
        assertTrue(reach(4) > reach(0), "the crack runs out over time")
        assertTrue(first.flatten().all { it.position.z() >= -0.01 }, "nothing is drawn behind the warrior")
        val ult = warrior.first { it.icon == "war_ult" }
        val cut = spawns(CoreSkillEffect(CoreClass.WARRIOR, ult, Pos.ZERO, Vec(0.0, 0.0, 1.0), pulse = 0))
        val finale = spawns(CoreSkillEffect(CoreClass.WARRIOR, ult, Pos.ZERO, Vec(0.0, 0.0, 1.0), pulse = 2))
        assertTrue(finale.size > cut.size && finale[0].any { it.particle.key().value() == "explosion_emitter" })
        assertTrue(CoreIronWarriorVfx.cues(CoreSkillEffect(CoreClass.WARRIOR, ult, Pos.ZERO, Vec(0.0, 0.0, 1.0), pulse = 2))
            .any { it.event == net.minestom.server.sound.SoundEvent.ENTITY_WARDEN_SONIC_BOOM })
    }

    @Test fun `rampage enters with a flash and keeps a low steady aura`() {
        val enter = mutableListOf<String>(); CoreIronWarriorVfx.rampage(Pos.ZERO, Vec(0.0, 0.0, 1.0), 0, true) { p, _, _, _, _ -> enter += p.key().value() }
        assertTrue("flash" in enter && "flame" in enter)
        var aura = 0; for (t in 0L until 18L) CoreIronWarriorVfx.rampage(Pos.ZERO, Vec(0.0, 0.0, 1.0), t, false) { _, _, _, _, _ -> aura++ }
        assertTrue(aura in 6..20, "aura per 18 ticks: $aura")
    }
}
