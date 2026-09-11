package dev.projects.server.coreloop

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.projects.server.particle.RecordingParticleSink
import net.minestom.server.coordinate.Vec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*
import kotlin.test.*

class CoreApprovedNormalV3Test {
    private val skill = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "dash" }
    private fun effect(id: String, phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE,
        heading: Double = 0.0, prepare: Int = 7) = CoreSkillEffect(CoreClass.WARRIOR,
        skill.copy(radius = 3.9), Vec.ZERO, Vec(sin(heading), 0.0, cos(heading)), phase,
        sceneId = id, prepareTicks = prepare)

    @Test fun `all three steps inherit exact approved blade wake lifetimes and frame order`() {
        for (id in CoreApprovedNormalV3.sceneIds) {
            val parts = CoreSkillChoreography.parts(effect(id)).filterNot(CoreWarriorCompanions::owns)
            assertEquals(listOf("approved_dash_blade", "approved_dash_wake"), parts.map { it.shape })
            assertEquals(listOf(7, 13), parts.map { it.durationTicks })
            assertEquals(listOf(false, true), parts.map { it.secondary })
            for (p in parts) {
                assertEquals(0, CoreCombatMeshes.interpolationTicks(p))
                for (age in 0 until p.durationTicks) {
                    val pose = CoreSkillChoreography.pose(p, age.toDouble())
                    assertTrue(pose.model.endsWith("_${age + 3}"))
                    assertEquals(p.offset, pose.offset)
                    assertEquals(p.scale, pose.scale)
                    assertEquals(p.roll, pose.roll)
                    assertTrue(pose.visible)
                    assertNotNull(javaClass.getResource("/core-ui-pack/assets/projects/items/${pose.model}.json"))
                }
                assertFalse(CoreSkillChoreography.pose(p, -1.0).visible)
                assertFalse(CoreSkillChoreography.pose(p, p.durationTicks.toDouble()).visible)
            }
            val contact = CoreSkillChoreography.parts(effect(id, CoreSkillVisualPhase.CONTACT)).filterNot(CoreWarriorCompanions::owns).single()
            assertEquals("approved_dash_impact", contact.shape)
            assertEquals(Vec(0.0, 1.0, 0.0), contact.offset)
            assertEquals(9, contact.durationTicks)
            val e = effect(id).also { it.solidCompanion = true }
            val sink = RecordingParticleSink()
            repeat(e.durationTicks) {
                sink.clear();e.emit(it, sink)
                assertTrue(sink.spawns.isEmpty(),"No vanilla particles around the approved blade")
            }
        }
    }

    @Test fun `immediate AA has no preparation at every speed and ticks never repeat its input hit`() {
        for (speed in listOf(.75, 1.0, 2.1)) {
            val combo = GreatswordCombo()
            for (id in CoreApprovedNormalV3.sceneIds) {
                val swing = combo.press(speed, immediate=true)!!
                assertEquals(0, swing.impactTick)
                assertTrue(CoreSkillChoreography.pose(CoreSkillChoreography.parts(effect(id)).first(), 0.0).model.endsWith("blade_3"))
                var hits = 0
                repeat(swing.totalTicks) { if (combo.tick() != null) hits++ }
                assertEquals(0, hits)
            }
        }
    }

    @Test fun `combo has mirrored return and stronger descending final without spinning the display`() {
        val a = CoreSkillChoreography.parts(effect("normal_sweep")).first()
        val b = CoreSkillChoreography.parts(effect("normal_reverse")).first()
        val c = CoreSkillChoreography.parts(effect("normal_finish")).first()
        assertFalse(a.spriteMirror)
        assertTrue(b.spriteMirror)
        assertTrue(CoreSkillChoreography.pose(b, 0.0).model.contains("approved_aa_reverse_v3/"))
        assertTrue(c.roll < a.roll)
        assertTrue(c.scale.x() > a.scale.x())
        assertTrue(c.offset.y() > a.offset.y())
    }

    @Test fun `native curved model corners are forward above ground and within visual reach in eight headings`() {
        for (id in CoreApprovedNormalV3.sceneIds) repeat(8) { heading ->
            val yaw = heading * PI / 4
            for (phase in listOf(CoreSkillVisualPhase.PREPARE, CoreSkillVisualPhase.PULSE)) {
                for (p in CoreSkillChoreography.parts(effect(id, phase, yaw))) repeat(p.durationTicks) { age ->
                    val pose = CoreSkillChoreography.pose(p, age.toDouble())
                    val model = javaClass.getResourceAsStream("/core-ui-pack/assets/projects/models/${pose.model}.json")!!
                        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                    for (element in model.getAsJsonArray("elements")) {
                        val e = element.asJsonObject
                        val lo = e.getAsJsonArray("from").map { it.asDouble }
                        val hi = e.getAsJsonArray("to").map { it.asDouble }
                        repeat(8) { corner ->
                            val v = DoubleArray(3) { axis -> if (corner and (1 shl axis) == 0) lo[axis] else hi[axis] }
                            e.getAsJsonObject("rotation")?.let { r ->
                                val o = r.getAsJsonArray("origin").map { it.asDouble }
                                val a = Math.toRadians(r.get("angle").asDouble)
                                val y = v[1]-o[1]; val z = v[2]-o[2]
                                v[1]=o[1]+y*cos(a)-z*sin(a); v[2]=o[2]+y*sin(a)+z*cos(a)
                            }
                            val x0=(v[0]-8)/16*pose.scale.x(); val y0=(v[1]-8)/16*pose.scale.y(); val z0=(v[2]-8)/16*pose.scale.z()
                            val y1=y0*cos(pose.pitch)-z0*sin(pose.pitch); val z1=y0*sin(pose.pitch)+z0*cos(pose.pitch)
                            val x1=x0*cos(pose.roll)-y1*sin(pose.roll)
                            val x=x1*cos(yaw)+z1*sin(yaw)+pose.offset.x()
                            val y=x0*sin(pose.roll)+y1*cos(pose.roll)+pose.offset.y()
                            val z=-x1*sin(yaw)+z1*cos(yaw)+pose.offset.z()
                            assertTrue(y>=.02, "$id buried at age=$age y=$y")
                            assertTrue(x*sin(yaw)+z*cos(yaw)>=-.05, "$id behind")
                            assertTrue(hypot(x,z)<=CoreSkillScenes.get(id).reach+.25, "$id beyond visual reach")
                        }
                    }
                }
            }
        }
    }

    @Test fun `export immediate AA cut and accepted hit for review`() {
        fun xyz(v: Vec) = listOf(v.x(), v.y(), v.z())
        val combo = GreatswordCombo()
        val scenes = CoreApprovedNormalV3.sceneIds.mapIndexed { index, id ->
            val swing = combo.press(1.0, immediate=true)!!
            val startup = swing.impactTick
            val beats = listOf(Triple(startup, CoreSkillVisualPhase.PULSE, Vec.ZERO),
                Triple(startup, CoreSkillVisualPhase.CONTACT, Vec(0.0, 0.0, 1.5)))
            val frames = (0..(startup+17)).map { tick -> beats.flatMap { (start, phase, at) ->
                CoreSkillChoreography.parts(effect(id, phase, prepare=startup)).mapNotNull { p ->
                    val pose = CoreSkillChoreography.pose(p, (tick-start).toDouble())
                    if (!pose.visible) null else mapOf("model" to pose.model, "offset" to xyz(pose.offset.add(at)),
                        "scale" to xyz(pose.scale), "yaw" to pose.yaw, "pitch" to pose.pitch, "roll" to pose.roll)
                }
            } }
            repeat(swing.totalTicks) { combo.tick() }
            mapOf("id" to id, "name" to "通常攻撃 ${index+1}段目", "frames" to frames)
        }
        val cwd=Path.of(System.getProperty("user.dir"));val root=if(cwd.fileName.toString()=="server-minestom") cwd.parent else cwd
        Files.createDirectories(root.resolve(".tools"))
        Files.writeString(root.resolve(".tools/approved-aa-v3-timeline.json"), Gson().toJson(scenes))
    }
}
