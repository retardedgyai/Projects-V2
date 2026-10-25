package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreWarriorRefChoreographyTest {
    private fun slam(yaw: Vec = Vec(0.0, 0.0, 1.0)) = CoreSkillEffect(CoreClass.WARRIOR,
        CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "slam" }, Vec.ZERO, yaw, CoreSkillVisualPhase.PULSE)

    @Test fun `地割り is drawn by its own pieces, every frame resolves and stays finite`() {
        val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        val parts = CoreWarriorRefChoreography.parts(slam())!!
        assertTrue(parts.size in 12..32, "${parts.size}")
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

    @Test fun `the beast it took sets the crack's reach and three stacks split it again ahead`() {
        val base = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "slam" }
        fun furrowEnd(stacks: Int): Double {
            val e = CoreSkillEffect(CoreClass.WARRIOR, CoreWarriorRules.slam(base, stacks), Vec.ZERO, Vec(0.0, 0.0, 1.0), CoreSkillVisualPhase.PULSE)
            val f = CoreWarriorRefChoreography.parts(e)!!.first { it.shape.endsWith("furrow") }
            return f.offset.z() + f.scale.z() / 2
        }
        val ends = (0..3).map(::furrowEnd)
        assertTrue(ends[0] < ends[1] && ends[1] < ends[2] && ends[2] < ends[3], "$ends")
        assertTrue(ends[0] in 3.5..5.0 && ends[2] in 7.5..9.0 && ends[3] in 9.5..11.0, "$ends")
        val split = CoreWarriorRefChoreography.parts(CoreSkillEffect(CoreClass.WARRIOR, CoreWarriorRules.slam(base, 3), Vec.ZERO,
            Vec(0.0, 0.0, 1.0), CoreSkillVisualPhase.PULSE, pulse = 1))!!
        assertTrue(split.isNotEmpty() && split.none { it.shape.endsWith("wind") }, "the second split is only the head")
        assertTrue(split.filter { it.shape.contains("turf") }.all { it.offset.z() > ends[3] - 1.0 }, "it bursts forward at the head")
    }

    @Test fun `only 地割り is taken over`() {
        val other = CoreSkillEffect(CoreClass.WARRIOR, CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "dash" },
            Vec.ZERO, Vec(0.0, 0.0, 1.0), CoreSkillVisualPhase.PULSE)
        assertEquals(null, CoreWarriorRefChoreography.parts(other))
    }
}
