package dev.projects.client

import dev.projects.protocol.GroundTelegraphStart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroundTelegraphTest {
    @Test
    fun `sector geometry has finite fan and border vertices`() {
        val mesh = SectorTelegraphGeometry.build(ClientGroundTelegraph(telegraph(), 0))
        assertEquals(11, mesh.fillFan.size)
        assertEquals(11, mesh.borderQuads.size)
        (mesh.fillFan + mesh.borderQuads.flatten()).forEach { point ->
            assertTrue(point.x.isFinite() && point.y.isFinite() && point.z.isFinite())
        }
    }

    @Test
    fun `ninety degree sector endpoints follow horizontal facing`() {
        val mesh = SectorTelegraphGeometry.build(ClientGroundTelegraph(telegraph(), 0))
        val first = mesh.fillFan[1]
        val last = mesh.fillFan.last()
        assertEquals(kotlin.math.sqrt(2.0), first.x, 1.0e-9)
        assertEquals(2.0 + kotlin.math.sqrt(2.0), first.z, 1.0e-9)
        assertEquals(-kotlin.math.sqrt(2.0), last.x, 1.0e-9)
        assertEquals(2.0 + kotlin.math.sqrt(2.0), last.z, 1.0e-9)
    }

    @Test
    fun `full circle remains finite with a degenerate facing`() {
        val message = telegraph().copy(angleDegrees = 360.0, facingX = 0.0, facingZ = 0.0)
        val mesh = SectorTelegraphGeometry.build(ClientGroundTelegraph(message, 0))
        assertEquals(38, mesh.fillFan.size)
        assertTrue((mesh.fillFan + mesh.borderQuads.flatten()).all { it.x.isFinite() && it.z.isFinite() })
    }

    @Test
    fun `client state upserts expires removes and clears`() {
        val state = GroundTelegraphClientState()
        state.start(telegraph().copy(durationTicks = 2))
        state.start(telegraph().copy(centerX = 9.0))
        assertEquals(1, state.size())
        state.tick()
        assertEquals(1, state.size())
        state.remove(7)
        assertEquals(0, state.size())
        state.start(telegraph().copy(durationTicks = 1))
        state.tick()
        assertEquals(0, state.size())
        state.start(telegraph())
        state.clear()
        assertEquals(0, state.size())
    }

    private fun telegraph() = GroundTelegraphStart(
        telegraphId = 7,
        centerX = 0.0,
        centerY = 0.0,
        centerZ = 2.0,
        facingX = 0.0,
        facingZ = 1.0,
        radius = 2.0,
        angleDegrees = 90.0,
        durationTicks = 20,
    )
}
