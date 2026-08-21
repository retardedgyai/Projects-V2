package dev.projects.server.particle

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.particle.Particle
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticleParityTest {
    @Test
    fun `multi selects an even subset without endpoint bias`() {
        val locations = (0 until 10).map { Pos(it.toDouble(), 0.0, 0.0) }
        val points = ParticleMulti(locations, count = 4).points()
        assertEquals(listOf(0.0, 3.0, 6.0, 9.0), points.map { it.x() })
    }

    @Test
    fun `pillar stays within arbitrary axis bounds`() {
        val points = ParticlePillar(Pos.ZERO, height = 4.0, radius = 1.0, count = 100, axis = Vec(0.0, 1.0, 0.0), seed = 7).points()
        assertTrue(points.all { it.y() in 0.0..4.0 && it.x() * it.x() + it.z() * it.z() <= 1.0 + 1.0e-9 })
    }

    @Test
    fun `rect prism handles negative and degenerate dimensions`() {
        val edge = ParticleRectPrism(Pos(2.0, 2.0, 2.0), Pos(0.0, 0.0, 0.0), RectPrismMode.EDGE, countPerMeter = 3.0)
        val face = ParticleRectPrism(Pos.ZERO, Vec(0.0, 0.0, 2.0), RectPrismMode.FACE, countPerMeterSquared = 3.0)
        assertTrue(edge.points().isNotEmpty() && face.points().isNotEmpty())
        assertTrue((edge.points() + face.points()).all { it.x().isFinite() && it.y().isFinite() && it.z().isFinite() })
    }

    @Test
    fun `flower follows its plane and image honors alpha lod and resize`() {
        val flower = ParticleFlower(Pos.ZERO, petals = 4, radius = 2.0, planeNormal = Vec(1.0, 0.0, 0.0), count = 32)
        assertTrue(flower.points().all { kotlin.math.abs(it.x()) < 1.0e-9 })

        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, Color(0xff0000).rgb or (0xff shl 24))
        image.setRGB(2, 0, Color(0x00ff00).rgb)
        val effect = ParticleImage(image, Pos.ZERO, alphaThreshold = 128, lod = 2, dimensions = Vec(2.0, 2.0, 0.0))
        assertEquals(1, effect.pixels().size)
        assertEquals(1, effect.points().size)
    }

    @Test
    fun `blend modes are exact and manager degrades low priority first`() {
        assertEquals(0x000808, NamedBlendMode.MULTIPLY.blend(0x804020, 0x002040))
        assertEquals(0x002040, ParticleImage.blend(0x804020, 0x002040, NamedBlendMode.REPLACE))

        val manager = ParticleManager(budget = ParticleBudget(1))
        val sink = RecordingParticleSink()
        manager.dispatchAll(ParticleViewer(Pos.ZERO), listOf(
            ParticleSpawn(Particle.END_ROD, Pos.ZERO, category = ParticleCategory.FULL),
            ParticleSpawn(Particle.END_ROD, Pos.ZERO, category = ParticleCategory.BOSS),
        ), sink)
        assertEquals(1, sink.spawns.size)
        assertEquals(ParticleCategory.BOSS, sink.spawns.single().category)
    }
}
