package dev.projects.server.coreloop

import com.google.gson.Gson
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreFrostChoreographyTest {
    private val skill=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon=="mage_zero" }
    private fun effect(pulse: Int=0,radius: Double=skill.radius)=
        CoreSkillEffect(CoreClass.MAGE,skill.copy(radius=radius),Vec.ZERO,Vec(0.0,0.0,1.0),pulse=pulse)

    @Test fun `each accepted wave owns a short floor four raised bands and four optional flakes`() {
        assertEquals(5,skill.pulses)
        for(i in 0 until skill.pulses) {
            val parts=CoreSkillChoreography.parts(effect(i))
            assertEquals(9,parts.size)
            assertEquals(5,parts.count { !it.secondary })
            assertEquals(1,parts.count { it.ground })
            assertEquals(4,parts.count { it.shape=="frost_domain_band" })
            assertEquals(4,parts.count { it.shape=="frost_domain_flake" && it.secondary })
            assertTrue(parts.none { it.followOwner || it.sprite || it.shape=="frost_crest" })
            assertTrue(parts.all { it.durationTicks==if(i==4) 16 else 8 })
            for(p in parts) {
                assertFalse(CoreSkillChoreography.pose(p,-1.0).visible)
                assertTrue(CoreSkillChoreography.pose(p,0.0).visible)
                assertFalse(CoreSkillChoreography.pose(p,p.durationTicks.toDouble()).visible)
                val full=CoreSkillChoreography.pose(p,2.0)
                assertEquals(p.scale,full.scale)
                assertNotEquals(full.yaw,CoreSkillChoreography.pose(p,5.0).yaw)
            }
        }
    }

    @Test fun `planes stay within scaled reach and the open upper layer stays away from the camera centre`() {
        for(r in listOf(.3,1.0,4.0,8.0,10.5)) {
            val e=effect(radius=r)
            val reach=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
            for(p in CoreSkillChoreography.parts(e)) for(tick in 0 until p.durationTicks) {
                val pose=CoreSkillChoreography.pose(p,tick.toDouble())
                when(p.shape) {
                    "frost_domain_floor" -> {
                        // Exported alpha is inside radius 44 on a 96px grid.
                        assertTrue(pose.scale.x()*.46<=reach)
                        assertEquals(.12,pose.offset.y())
                    }
                    "frost_domain_band" -> {
                        assertEquals(reach*.65,hypot(pose.offset.x(),pose.offset.z()),.00001)
                        assertTrue(pose.offset.y() in .4..1.0)
                        // Tangential card corners remain inside the gameplay circle.
                        assertTrue(hypot(reach*.65,pose.scale.x()*.5)<reach)
                    }
                }
            }
        }
    }

    @Test fun `invalid effects stay rejected and preparation contact and other frost spells are unchanged`() {
        assertTrue(CoreSkillChoreography.parts(effect(radius=Double.NaN)).isEmpty())
        for(phase in listOf(CoreSkillVisualPhase.PREPARE,CoreSkillVisualPhase.CONTACT)) {
            val e=CoreSkillEffect(CoreClass.MAGE,skill,Vec.ZERO,Vec(0.0,0.0,1.0),phase)
            assertTrue(CoreSkillChoreography.parts(e).none { it.shape.startsWith("frost_domain_") })
        }
        for(id in listOf("frost_nova","mage_garden")) {
            val s=CoreSkillCatalog.skills(CoreClass.MAGE).first { it.icon==id }
            assertTrue(CoreSkillChoreography.parts(CoreSkillEffect(CoreClass.MAGE,s,Vec.ZERO,Vec(0.0,0.0,1.0)))
                .none { it.shape.startsWith("frost_domain_") })
        }
    }

    @Test fun `actual five pulse timeline stays under display budget and exports for visual QA`() {
        val waves=(0 until skill.pulses).map { CoreSkillChoreography.parts(effect(it)) }
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val frames=(0..48).map { tick ->
            waves.flatMapIndexed { pulse,parts -> parts.mapNotNull { part ->
                val pose=CoreSkillChoreography.pose(part,(tick-pulse*8).toDouble())
                if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),
                    "scale" to xyz(pose.scale),"yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
            } }.also { assertTrue(it.size<=9,"tick=$tick displays=${it.size}") }
        }
        assertTrue(frames.last().isEmpty())
        val cwd=Path.of(System.getProperty("user.dir"))
        val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/frost-domain-frames.json"),Gson().toJson(listOf(
            mapOf("id" to "mage_zero","name" to "絶対零界・実際の五連波","frames" to frames))))
    }
}
