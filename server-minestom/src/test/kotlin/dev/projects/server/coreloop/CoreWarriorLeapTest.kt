package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreWarriorLeapTest {
    @Test fun `the launch flies vanilla physics onto the aimed point with an arc that grows with the reach`() {
        val from = Pos(0.0, 40.0, 0.0)
        for (reach in listOf(2.0, 4.0, 6.0, 8.0)) {
            val target = Vec(reach * .6, 40.0, reach * .8)
            val path = CoreWarriorLeap.path(from, CoreWarriorLeap.launch(from, target), ground = 40.0)
            val land = path.last()
            assertEquals(40.0, land.y(), 1e-9)
            assertTrue(Vec(land.x(), 0.0, land.z()).distance(Vec(target.x(), 0.0, target.z())) < .35, "reach $reach lands at $land")
            val top = path.maxOf { it.y() } - 40.0
            assertEquals(CoreWarriorLeap.apex(reach), top, .25, "reach $reach")
            assertTrue(path.size in 8..CoreWarriorLeap.MAX_AIR_TICKS, "it is a flight, not a jump cut: ${path.size} ticks")
        }
    }

    @Test fun `aim takes the looked-at ground within reach and clamps short and long looks`() {
        val eye = Pos(0.0, 41.62, 0.0)
        val ground: (Vec) -> Boolean = { it.y() < 40.0 }
        val down = CoreWarriorLeap.aim(eye, Vec(0.0, -1.0, 1.0).normalize(), ground)
        assertEquals(CoreWarriorLeap.MIN_REACH, Vec(down.x(), 0.0, down.z()).length(), 1e-6)
        val mid = CoreWarriorLeap.aim(eye, Vec(0.0, -.3, 1.0).normalize(), ground)
        assertTrue(mid.z() in 4.5..6.5 && mid.y() == 40.0, "$mid")
        val sky = CoreWarriorLeap.aim(eye, Vec(0.0, .5, 1.0).normalize(), ground)
        assertEquals(CoreWarriorLeap.MAX_REACH, sky.z(), 1e-6)
    }
}
