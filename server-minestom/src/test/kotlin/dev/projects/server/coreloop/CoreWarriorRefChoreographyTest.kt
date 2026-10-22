package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreWarriorRefChoreographyTest {
    private fun cast(icon: String, yaw: Vec = Vec(0.0, 0.0, 1.0)) = CoreSkillEffect(CoreClass.WARRIOR,
        CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == icon }, Vec.ZERO, yaw, CoreSkillVisualPhase.PULSE)
    private fun slam(yaw: Vec = Vec(0.0, 0.0, 1.0)) = cast("slam", yaw)

    @Test fun `地割り is drawn by its own pieces, every frame resolves and stays finite`() {
        val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        val parts = CoreWarriorRefChoreography.parts(slam())!!
        assertTrue(parts.size in 20..32, "${parts.size}")
        assertTrue(parts.any { it.shape.endsWith("furrow") } && parts.count { it.shape.contains("turf") } >= 8)
        for (p in parts) for (tick in 0..p.delayTicks + p.durationTicks) {
            val pose = CoreWarriorRefChoreography.pose(p, tick.toDouble())!!
            assertTrue(listOf(pose.offset.x(), pose.offset.y(), pose.offset.z(), pose.yaw, pose.pitch, pose.roll).all(Double::isFinite))
            assertTrue(pose.model == CoreCombatMeshes.GROUND || "assets/projects/items/${pose.model}.json" in index, pose.model)
        }
    }

    @Test fun `the furrow runs forward of the wielder whichever way they face`() {
        for (dir in listOf(Vec(0.0, 0.0, 1.0), Vec(1.0, 0.0, 0.0), Vec(-.6, 0.0, -.8))) {
            val furrow = CoreWarriorRefChoreography.parts(slam(dir))!!.first { it.shape.endsWith("furrow") }
            assertTrue(furrow.offset.dot(dir) > 3.0, "$dir")
            assertEquals(0.0, furrow.offset.dot(Vec(-dir.z(), 0.0, dir.x())), 1e-6)
        }
    }

    @Test fun `地走り runs one thin crack ahead and its jaws hold for the root`() {
        val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        for (dir in listOf(Vec(0.0, 0.0, 1.0), Vec(-.6, 0.0, -.8))) {
            val parts = CoreWarriorRefChoreography.parts(cast("war_wound", dir))!!
            val rift = parts.first { it.shape.endsWith("rift") }
            assertTrue(rift.offset.dot(dir) > 3.0, "$dir")
            val jaws = parts.filter { it.shape.contains("turf") }
            assertTrue(jaws.size == 8 && jaws.all { it.delayTicks + it.durationTicks >= 30 }, "the jaws outlast the 1.5 s root")
            for (p in parts) for (tick in 0..p.delayTicks + p.durationTicks) {
                val pose = CoreWarriorRefChoreography.pose(p, tick.toDouble())!!
                assertTrue(listOf(pose.offset.x(), pose.offset.y(), pose.offset.z(), pose.yaw, pose.pitch, pose.roll).all(Double::isFinite))
                assertTrue(pose.model == CoreCombatMeshes.GROUND || "assets/projects/items/${pose.model}.json" in index, pose.model)
            }
        }
    }

    @Test fun `only 地割り and 地走り are taken over`() {
        val other = CoreSkillEffect(CoreClass.WARRIOR, CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "whirl" },
            Vec.ZERO, Vec(0.0, 0.0, 1.0), CoreSkillVisualPhase.PULSE)
        assertEquals(null, CoreWarriorRefChoreography.parts(other))
    }
}
