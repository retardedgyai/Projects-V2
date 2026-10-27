package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreWarriorRefChoreographyTest {
    private val base get() = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "slam" }
    private fun slam(yaw: Vec = Vec(0.0, 0.0, 1.0), stacks: Int = 0, pulse: Int = CoreWarriorRefChoreography.SLAM_PULSE,
                     phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE) =
        CoreSkillEffect(CoreClass.WARRIOR, CoreWarriorRules.slam(base, stacks), Vec.ZERO, yaw, phase, pulse = pulse)
    private val full get() = slam(stacks = 3, pulse = CoreWarriorRefChoreography.FULL_SLAM_PULSE)
    private fun eye(p: CoreCombatMeshPart) = kotlin.math.hypot(p.offset.x(), p.offset.z())

    @Test fun `地割り is drawn by its own pieces, every frame resolves and stays finite`() {
        val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        val effects = listOf(slam(), slam(stacks = 2), full, slam(stacks = 3, pulse = CoreWarriorRefChoreography.FULL_WINDUP_PULSE),
            slam(stacks = 3, pulse = CoreWarriorRefChoreography.SLAM_SPLIT_PULSE), slam(phase = CoreSkillVisualPhase.CONTACT)) +
            (0 until CoreWarriorRules.FULL_SWING_GEYSER_STEPS).map { slam(stacks = 3, pulse = CoreWarriorRules.FULL_SWING_GEYSER_PULSE + it) }
        for (e in effects) {
            val parts = CoreWarriorRefChoreography.parts(e)!!
            assertTrue(parts.size in 1..CoreCombatMeshes.OWNER_LIMIT, "${e.pulse}: ${parts.size}")
            for (p in parts) for (tick in 0..p.delayTicks + p.durationTicks) {
                val pose = CoreWarriorRefChoreography.pose(p, tick.toDouble())!!
                assertTrue(listOf(pose.offset.x(), pose.offset.y(), pose.offset.z(), pose.yaw, pose.pitch, pose.roll).all(Double::isFinite))
                assertTrue(pose.model == CoreCombatMeshes.GROUND || "assets/projects/items/${pose.model}.json" in index, pose.model)
            }
        }
        val parts = CoreWarriorRefChoreography.parts(slam())!!
        assertTrue(parts.any { it.shape.endsWith("furrow") } && parts.count { it.shape.contains("turf") } >= 8, "${parts.map { it.shape }}")
        // The cast's own pulse draws nothing: the world pieces come at the moment (look pulses), the swing is the hand's.
        assertTrue(CoreWarriorRefChoreography.parts(slam(pulse = 0))!!.isEmpty())
    }

    @Test fun `the caster's swing is never drawn in the world and nothing stands up in front of the eye`() {
        val flat = setOf("furrow", "dent")
        for (e in listOf(slam(), slam(stacks = 2), full, slam(stacks = 3, pulse = CoreWarriorRefChoreography.FULL_WINDUP_PULSE)) +
            (0 until CoreWarriorRules.FULL_SWING_GEYSER_STEPS).map { slam(stacks = 3, pulse = CoreWarriorRules.FULL_SWING_GEYSER_PULSE + it) }) {
            val parts = CoreWarriorRefChoreography.parts(e)!!
            assertTrue(parts.none { it.shape.endsWith("wind") }, "no dark band of the swing")
            for (p in parts) {
                val name = p.shape.substringAfter(":")
                if (name in flat) continue
                val ahead = p.offset.z()
                assertTrue(ahead < 0.3 * eye(p) || eye(p) >= 2.2, "${e.pulse} $name stands ${eye(p)} in front")
            }
        }
    }

    @Test fun `the crack runs forward of the wielder whichever way they face`() {
        for (dir in listOf(Vec(0.0, 0.0, 1.0), Vec(1.0, 0.0, 0.0), Vec(-.6, 0.0, -.8))) {
            val furrow = CoreWarriorRefChoreography.parts(slam(dir))!!.first { it.shape.endsWith("furrow") }
            assertTrue(furrow.offset.dot(dir) > 3.0, "$dir")
            assertEquals(0.0, furrow.offset.dot(Vec(-dir.z(), 0.0, dir.x())), 1e-6)
        }
    }

    @Test fun `the beast it took sets the crack's reach and a full beast is bigger with blood running out along it`() {
        fun crack(e: CoreSkillEffect) = CoreWarriorRefChoreography.parts(e)!!.first { it.shape.endsWith("furrow") }
        fun end(e: CoreSkillEffect) = crack(e).let { it.offset.z() + it.scale.z() / 2 }
        val ends = (0..2).map { end(slam(stacks = it)) } + end(full)
        assertTrue(ends.zipWithNext().all { (a, b) -> a < b }, "$ends")
        assertTrue(ends[0] in 4.5..6.0 && ends[2] in 8.5..10.0 && ends[3] in 12.5..14.5, "$ends")
        val small = CoreWarriorRefChoreography.parts(slam(stacks = 2))!!
        val big = CoreWarriorRefChoreography.parts(full)!!
        assertTrue(big.count { it.shape.contains("turf") } > small.count { it.shape.contains("turf") })
        assertTrue(big.filter { it.shape.contains("turf") }.maxOf { it.scale.x() } > small.filter { it.shape.contains("turf") }.maxOf { it.scale.x() })
        // The blood bursts up step by step away from the warrior, out to the head of the crack.
        val steps = (0 until CoreWarriorRules.FULL_SWING_GEYSER_STEPS).map { k ->
            CoreWarriorRefChoreography.parts(slam(stacks = 3, pulse = CoreWarriorRules.FULL_SWING_GEYSER_PULSE + k))!!
                .filter { it.shape.endsWith("bmistup") }.minOf { it.offset.z() } }
        assertTrue(steps.zipWithNext().all { (a, b) -> a < b } && steps.last() > ends[3] - 2.0, "$steps")
        assertTrue(CoreWarriorRefChoreography.fullBlow(full) && !CoreWarriorRefChoreography.fullBlow(slam(stacks = 2)))
    }

    @Test fun `a second pulse splits the crack again at its head`() {
        val head = CoreWarriorRefChoreography.parts(slam(stacks = 2))!!.first { it.shape.endsWith("furrow") }.let { it.offset.z() + it.scale.z() / 2 }
        val split = CoreWarriorRefChoreography.parts(slam(stacks = 2, pulse = CoreWarriorRefChoreography.SLAM_SPLIT_PULSE))!!
        assertTrue(split.isNotEmpty() && split.filter { it.shape.contains("turf") }.all { it.offset.z() > head - 1.5 }, "it bursts forward at the head")
    }

    @Test fun `only the warrior is taken over`() {
        val other = CoreSkillEffect(CoreClass.MAGE, CoreSkillCatalog.skills(CoreClass.MAGE).first(),
            Vec.ZERO, Vec(0.0, 0.0, 1.0), CoreSkillVisualPhase.PULSE)
        assertEquals(null, CoreWarriorRefChoreography.parts(other))
    }
}
