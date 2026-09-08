package dev.projects.server.coreloop

import com.google.gson.Gson
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class CoreSlashChoreographyTest {
    private fun skill(job: CoreClass,id: String)=if(id.startsWith("normal_"))
        CoreSkillCatalog.skills(job).first().copy(pulses=1) else CoreSkillCatalog.skills(job).first { it.icon==id }
    private fun effect(job: CoreClass,id: String,pulse: Int=0)=CoreSkillEffect(job,skill(job,id),
        Vec.ZERO,Vec(0.0,0.0,1.0),pulse=pulse,sceneId=id)

    @Test fun `normal combo and every hit of assassin ultimate use fixed cutting planes`() {
        for(id in listOf("normal_sweep","normal_reverse","normal_finish","ass_ult")) {
            val job=if(id=="ass_ult") CoreClass.ASSASSIN else CoreClass.WARRIOR
            for(pulse in 0 until skill(job,id).pulses) {
                val parts=CoreSkillChoreography.parts(effect(job,id,pulse))
                assertEquals(if(id=="ass_ult") 2 else 1,parts.size)
                for(p in parts) {
                    assertEquals("directional_cut",p.shape)
                    val poses=(0 until p.durationTicks).map { CoreSkillChoreography.pose(p,p.delayTicks+it.toDouble()) }
                    assertTrue(poses.all { it.yaw==p.yaw && it.roll==p.roll && it.pitch==p.pitch && it.scale==p.scale && it.offset==p.offset })
                    assertTrue(poses.map { it.model }.distinct().size>=6)
                    assertFalse(CoreSkillChoreography.pose(p,-1.0).visible)
                    assertFalse(CoreSkillChoreography.pose(p,(p.delayTicks+p.durationTicks).toDouble()).visible)
                }
            }
        }
    }

    @Test fun `export actual multi hit timelines instead of repeating a preview loop as fake extra attacks`() {
        fun xyz(v: Vec)=listOf(v.x(),v.y(),v.z())
        val rows=listOf(CoreClass.WARRIOR to "normal_sweep",CoreClass.WARRIOR to "normal_reverse",
            CoreClass.WARRIOR to "normal_finish",CoreClass.WARRIOR to "dash",CoreClass.WARRIOR to "slam",
            CoreClass.WARRIOR to "whirl",CoreClass.ASSASSIN to "ass_execute",CoreClass.ASSASSIN to "ass_fan",
            CoreClass.ASSASSIN to "ass_ult",CoreClass.ASSASSIN to "ass_stab")
        val scenes=rows.map { (job,id) ->
            val s=skill(job,id)
            val waves=(0 until s.pulses).map { CoreSkillChoreography.parts(effect(job,id,it)) }
            val end=(s.pulses-1)*8+waves.last().maxOf { it.durationTicks+it.delayTicks }
            val frames=(0..end).map { tick -> waves.flatMapIndexed { pulse,parts -> parts.mapNotNull { p ->
                val pose=CoreSkillChoreography.pose(p,tick-pulse*8.0)
                if(!pose.visible) null else mapOf("model" to pose.model,"offset" to xyz(pose.offset),
                    "scale" to xyz(pose.scale),"yaw" to pose.yaw,"pitch" to pose.pitch,"roll" to pose.roll)
            } }.also {
                val limit=if(waves.any { parts -> parts.any { p -> p.shape.startsWith("flow:") } }) 16 else 12
                assertTrue(it.size<=limit,"$id: overlapping display budget at tick $tick")
            } }
            assertTrue(frames.last().isEmpty())
            mapOf("id" to id,"name" to CoreSkillScenes.get(id).name,"frames" to frames)
        }
        val cwd=Path.of(System.getProperty("user.dir"))
        val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/slash-timelines.json"),Gson().toJson(scenes))
    }
}
