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

    @Test
    fun `shader strikes spawn marked displays and clean them up`() {
        net.minestom.server.MinecraftServer.init(net.minestom.server.Auth.Offline())
        val instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer()
        for (x in -2..1) for (z in -2..1) instance.loadChunk(x, z).join()
        try {
            for (ability in definition.abilities) {
                val fx = ShadeWyrmShaderFx(instance)
                fx.strike(ability.id, MobAbilityFrame(ability, Pos(0.0, 64.0, 0.0), Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L)) { null }
                var peak = 0
                repeat(40) { fx.tick(); peak = maxOf(peak, fx.liveCount) }
                assertTrue(peak > 0, "${ability.id} showed nothing")
                assertTrue(fx.liveCount == 0, "${ability.id} left ${fx.liveCount} displays")
                val marked = instance.entities.none { it.entityType == net.minestom.server.entity.EntityType.ITEM_DISPLAY }
                assertTrue(marked, "${ability.id} displays not removed")
            }
        } finally {
            net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance)
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
        // The shader displays, as a client would show them each tick from the strike on.
        net.minestom.server.MinecraftServer.init(net.minestom.server.Auth.Offline())
        val instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer()
        for (x in -2..1) for (z in -2..1) instance.loadChunk(x, z).join()
        for (ability in definition.abilities) {
            val fx = ShadeWyrmShaderFx(instance)
            // A target-anchored ability lands where the player stands, 7 blocks ahead here.
            val at = when {
                ability.id == "shade-dive" -> Pos(0.0, 0.0, 8.0)
                ability.anchor == MobAbilityAnchor.TARGET -> Pos(0.0, 0.0, 7.0)
                else -> Pos.ZERO
            }
            val frame = MobAbilityFrame(ability, at, Vec(0.0, 0.0, 1.0), MobAbilityPhase.LOCKED, 0L, Pos.ZERO)
            val hitTick = (ability.telegraphMillis / 50).toInt()
            fx.windup(ability.id, frame) { null }
            repeat(hitTick + 50) { tick ->
                if (tick == hitTick) fx.strike(ability.id, frame) { null }
                fx.tick()
                for (v in fx.views()) {
                    fun vec(p: net.minestom.server.coordinate.Point) = "[${p.x()},${p.y()},${p.z()}]"
                    lines += "{\"ability\":\"${ability.id}\",\"phase\":\"shader\",\"tick\":$tick,\"time\":${(tick + 1) * .05},\"sprite\":\"${v.sprite}\",\"kind\":${v.kind}," +
                        "\"progress\":${v.progress},\"at\":${vec(v.at)},\"scale\":${vec(v.scale)},\"translation\":${vec(v.translation)}," +
                        "\"rotation\":[${v.rotation.joinToString(",")}],\"billboard\":${v.billboard}}"
                }
            }
            fx.dispose()
        }
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance)
        java.io.File(path).writeText(lines.joinToString("\n"))
    }
}
