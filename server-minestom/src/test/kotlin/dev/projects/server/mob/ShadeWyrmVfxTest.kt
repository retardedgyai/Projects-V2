package dev.projects.server.mob

import dev.projects.server.particle.ParticleImportance
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShadeWyrmVfxTest {
    private val definition = QuestMobContent.definition(1, QuestMobArchetype.SHADE_WYRM)

    @Test
    fun `every ability strikes with particles where its shape hurts`() {
        val origin = Pos(10.0, 64.0, -4.0)
        for (facing in listOf(Vec(0.0, 0.0, 1.0), Vec(1.0, 0.0, -1.0).normalize())) for (ability in definition.abilities) {
            val frame = MobAbilityFrame(ability, origin, facing, MobAbilityPhase.LOCKED, 0L)
            val effect = assertNotNull(ShadeWyrmVfx.hit(ability.id, frame) { null }, ability.id)
            val sink = RecordingParticleSink()
            repeat(effect.durationTicks) { effect.emit(it, sink) }
            val strike = sink.spawns.filter { it.importance == ParticleImportance.COMBAT_FEEDBACK }
            assertTrue(strike.size >= 20, "${ability.id}: ${strike.size} particles")
            // Measured on the ground plane: the strike is drawn inside the area that deals the damage.
            val inside = strike.count { ability.shape.contains(origin, facing, it.position.withY(origin.y())) }
            assertTrue(inside >= strike.size * .75, "${ability.id}: $inside of ${strike.size} inside the shape")
        }
    }

    @Test
    fun `wind-up cues last exactly the telegraph`() {
        for (ability in definition.abilities) {
            val effect = ShadeWyrmVfx.windup(ability.id, MobAbilityFrame(ability, Pos.ZERO, Vec(0.0, 0.0, 1.0), MobAbilityPhase.TRACKING, 0L)) { null }
                ?: continue
            assertTrue(effect.durationTicks * 50L == ability.telegraphMillis, ability.id)
        }
    }

    /** `SHADE_VFX_DUMP=<file> ./gradlew :server-minestom:test --tests '*ShadeWyrmVfxTest' --rerun` writes every
     * particle (facing +Z from the origin, in blocks) for scripts/boss_preview/vfxclip.py to draw over the clips. */
    @Test
    fun `dump the particles for the preview`() {
        val path = System.getenv("SHADE_VFX_DUMP") ?: return
        val lines = mutableListOf<String>()
        for (ability in definition.abilities) {
            val frame = MobAbilityFrame(ability, Pos.ZERO, Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L)
            for ((phase, effect) in listOf("windup" to ShadeWyrmVfx.windup(ability.id, frame) { null }, "hit" to ShadeWyrmVfx.hit(ability.id, frame) { null })) {
                effect ?: continue
                repeat(effect.durationTicks) { tick ->
                    val sink = RecordingParticleSink()
                    effect.emit(tick, sink)
                    for (s in sink.spawns) {
                        val p = s.particle
                        val (rgb, size) = when (p) {
                            is net.minestom.server.particle.Particle.Dust -> p.color() to p.scale()
                            is net.minestom.server.particle.Particle.DustColorTransition -> p.color() to p.scale()
                            else -> null to 1f
                        }
                        val color = rgb?.let { it.red() shl 16 or (it.green() shl 8) or it.blue() } ?: -1
                        lines += "{\"ability\":\"${ability.id}\",\"phase\":\"$phase\",\"tick\":$tick,\"kind\":\"${p.key().value()}\"," +
                            "\"x\":${s.position.x()},\"y\":${s.position.y()},\"z\":${s.position.z()},\"color\":$color,\"size\":$size," +
                            "\"count\":${s.count},\"spread\":[${s.offset.x()},${s.offset.y()},${s.offset.z()}]}"
                    }
                }
            }
        }
        java.io.File(path).writeText(lines.joinToString("\n"))
    }
}
