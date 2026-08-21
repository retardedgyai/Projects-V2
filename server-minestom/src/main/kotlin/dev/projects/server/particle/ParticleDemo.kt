package dev.projects.server.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.particle.Particle
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.PI

fun startParticleDemo(
    player: Player,
    type: String,
    scheduler: ParticleAnimationScheduler,
    values: List<Double> = emptyList(),
    manager: ParticleManager? = null,
) {
    val origin = player.position.add(player.position.direction().x() * 3.0, 1.2, player.position.direction().z() * 3.0)
    val direction = player.position.direction()
    val sink = manager?.sink(ParticleViewer(player.position, player), PlayerParticleSink(player)) ?: PlayerParticleSink(player)
    val effect = when (type) {
        "line" -> {
            val length = values.getOrNull(0) ?: 3.0
            val density = values.getOrNull(1) ?: 7.0
            ParticleLine(origin, origin.add(direction.x() * length, direction.y() * length, direction.z() * length), Particle.END_ROD, density, durationTicks = 4)
        }
        "circle" -> ParticleCircle(origin, values.getOrNull(0) ?: 1.3, style = ParticleStyle(Particle.END_ROD), durationTicks = 4)
        "arc" -> {
            val radius = values.getOrNull(0) ?: 1.6
            val start = values.getOrNull(1) ?: -90.0
            val end = values.getOrNull(2) ?: 90.0
            ParticleGeometry.drawCleaveArc(origin, direction, radius, 0.0, start, end, 1, degreesPerTick = kotlin.math.abs(end - start) / 4.0)
        }
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
        "sphere" -> ParticleUtils.sphere(origin, values.getOrNull(0) ?: 1.4, 72, ParticleStyle(Particle.END_ROD))
        "dome" -> ParticleUtils.dome(origin, values.getOrNull(0) ?: 1.4, 48, style = ParticleStyle(Particle.END_ROD))
        "flower" -> ParticleUtils.flowerPattern(origin, values.getOrNull(0)?.toInt()?.coerceAtLeast(1) ?: 5, values.getOrNull(1) ?: 1.6, style = ParticleStyle(dust(0xff55dd, 0.45f)))
        "prism" -> ParticleUtils.rectangleTelegraph(origin, 3.0, 2.0, style = ParticleStyle(dust(0x55aaff, 0.4f)))
        "image" -> ParticleImage(demoImage(), origin, alphaThreshold = 20, dimensions = Vec(2.0, 2.0, 0.0), planeNormal = direction, dustScale = 0.35f)
        "slash" -> ParticleGeometry.drawParticleLineSlash(origin, direction, 35.0, values.getOrNull(0) ?: 3.0, 0.18, values.getOrNull(1)?.toInt()?.coerceAtLeast(1) ?: 4) { _, middle, end, _ ->
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

private fun demoImage(): BufferedImage {
    val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until 8) for (x in 0 until 8) {
        val visible = x in 1..6 && y in 1..6 && (x == 1 || x == 6 || y == 1 || y == 6 || x == y || x + y == 7)
        image.setRGB(x, y, if (visible) Color(0x66ddff).rgb or (0xff shl 24) else 0)
    }
    return image
}
