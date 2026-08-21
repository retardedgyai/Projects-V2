package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.particle.Particle
import kotlin.math.PI

fun startParticleDemo(player: Player, type: String, scheduler: ParticleAnimationScheduler) {
    val origin = player.position.add(player.position.direction().x() * 3.0, 1.2, player.position.direction().z() * 3.0)
    val direction = player.position.direction()
    val sink = PlayerParticleSink(player)
    val effect = when (type) {
        "line" -> ParticleLine(origin, origin.add(direction.x() * 3.0, direction.y() * 3.0, direction.z() * 3.0), Particle.END_ROD, 7.0, durationTicks = 4)
        "circle" -> ParticleCircle(origin, 1.3, style = ParticleStyle(Particle.END_ROD), durationTicks = 4)
        "arc" -> ParticleGeometry.drawHalfArc(origin, 1.6, 4)
        "bezier" -> ParticleBezier(
            origin,
            origin.add(direction.x() * 3.0, direction.y() * 3.0 + 1.0, direction.z() * 3.0),
            listOf(origin.add(0.0, 2.0, 0.0)),
            particle = Particle.ENCHANT,
            durationTicks = 4,
        )
        "spiral" -> ParticleSpiral(origin, direction, 1.0, PI * 2.0, 2.0, durationTicks = 6)
        "lightning" -> ParticleLightning(origin, origin.add(direction.x() * 4.0, direction.y() * 4.0, direction.z() * 4.0), hops = 8, hopVariance = 0.45, durationTicks = 4)
        "explosion" -> ParticleGeometry.drawParticleCircleExplosion(origin, 1.8, 28, durationTicks = 4)
        "slash" -> ParticleGeometry.drawParticleLineSlash(origin, direction, 35.0, 3.0, 0.18, 4) { _, middle, end, _ ->
            ParticleStyle(dust(lerpColor(0xff2020, 0xffff55, middle), 0.3f + middle.toFloat() * 0.35f), if (end > 0.5) 2 else 1)
        }
        "cleave" -> ParticleGeometry.drawCleaveArc(origin, 1.2, 20.0, -70.0, 70.0, 3, degreesPerTick = 35.0)
        "all" -> ParticleBatch.of(
            ParticleLine(origin, origin.add(direction.x() * 2.5, direction.y() * 2.5, direction.z() * 2.5), durationTicks = 3),
            ParticleCircle(origin, 1.1, durationTicks = 3),
            ParticleGeometry.drawHalfArc(origin.add(0.0, 0.8, 0.0), 1.4, 3),
            ParticleSpiral(origin, direction, 0.7, PI * 2.0, 1.5, durationTicks = 3),
            ParticleLightning(origin, origin.add(direction.x() * 3.0, direction.y() * 3.0, direction.z() * 3.0), durationTicks = 3),
            ParticleGeometry.drawParticleCircleExplosion(origin, 1.4, 14, durationTicks = 3),
        )
        else -> return
    }
    scheduler.start(effect, sink)
}
