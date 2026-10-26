package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.network.NetworkBuffer
import java.util.UUID
import kotlin.test.*

class CoreArmamentPresentationTest {
    @Test fun `bow firing poses are assigned to arrows and rain never traps guards or blink`() {
        MinecraftServer.init(Auth.Offline())
        val id=UUID.randomUUID()
        val a=CoreAccount(id,journey=CoreJourney.legacy(job=CoreClass.RANGER),
            weaponIdentity=CoreGearIdentity(UUID.randomUUID(),id,base=CoreWeaponBase.LONGBOW))
        val item=CoreLoopItems.gear(a,CoreGearSlot.WEAPON,true)
        for(motif in CoreSkillMotif.entries) assertEquals(motif in setOf(CoreSkillMotif.ARROW,CoreSkillMotif.RAIN),CoreArmamentPresentation.usesWeapon(item,motif))
        assertFalse(CoreArmamentPresentation.usesWeapon(ItemStack.of(Material.BOW),CoreSkillMotif.ARROW))
    }

    @Test fun `action clock spans anticipation and settles with no extrapolated or negative frames`() {
        MinecraftServer.init(Auth.Offline())
        for (stage in CoreArmamentPresentation.Stage.entries) for (duration in 2..61) {
            val clip=CoreArmamentPresentation.Clip(stage,100,duration,ItemStack.AIR,UUID.randomUUID())
            assertNull(clip.poseAt(99)); assertNull(clip.poseAt(100L+duration))
            assertEquals(stage.offset,clip.poseAt(100))
            assertEquals(stage.offset+5,clip.poseAt(99L+duration))
            val poses=(0 until duration).map { clip.poseAt(100L+it)!! }
            assertEquals(poses.sorted(),poses)
            assertTrue(poses.all { it in stage.offset..stage.offset+5 })
        }
    }

    @Test fun `action poses preserve all gear components and export every requested frame`() {
        MinecraftServer.init(Auth.Offline())
        for(job in CoreClass.entries) for(tier in 1..4) {
            val id=UUID.randomUUID(); val base=CoreWeaponBase.entries.first { it.usable(job) }
            val a=CoreAccount(id,weaponTier=tier,journey=CoreJourney.legacy(job=job),
                weaponIdentity=CoreGearIdentity(UUID.randomUUID(),id,base=base))
            val item=CoreLoopItems.gear(a,CoreGearSlot.WEAPON,true)
                .with(DataComponents.CUSTOM_MODEL_DATA,CustomModelData(listOf(0f,42f),listOf(true),listOf("keep"),emptyList()))
            for(stage in CoreArmamentPresentation.Stage.entries) for(frame in 0..5) {
                val pose=CoreArmamentPresentation.pose(item,stage.offset+frame)
                val data=pose.get(DataComponents.CUSTOM_MODEL_DATA)!!
                assertEquals((stage.offset+frame).toFloat(),data.floats().first())
                assertEquals(listOf(42f),data.floats().drop(1)); assertEquals(listOf(true),data.flags()); assertEquals(listOf("keep"),data.strings())
                assertEquals(item.without(DataComponents.CUSTOM_MODEL_DATA),pose.without(DataComponents.CUSTOM_MODEL_DATA))
                val key=CoreArmamentPresentation.model(base,job,tier).substringAfter(':')
                val suffix=stage.name.lowercase()+"%02d".format(frame)
                assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/models/item/${key}_$suffix.json"))
            }
        }
    }

    @Test fun `greatsword swing frames exist for every tier and the pack dispatches each to its own frame`() {
        MinecraftServer.init(Auth.Offline())
        for (tier in 1..4) {
            val json = javaClass.classLoader.getResource("core-ui-pack/assets/projects/items/weapons/greatsword_t$tier.json")!!.readText()
            for (clip in CoreArmamentPresentation.SwingClip.entries) for (frame in 0 until clip.frames) {
                val model = "greatsword_t${tier}_${clip.name.lowercase()}_%02d".format(frame)
                assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/models/item/weapons/$model.json"), model)
                assertTrue("{\"threshold\":${clip.offset + frame},\"model\":{\"type\":\"minecraft:model\",\"model\":\"projects:item/weapons/$model\"}}" in json.replace(" ", ""), model)
            }
        }
    }

    @Test fun `a greatsword swing spans its frames over the swing and never runs past them`() {
        MinecraftServer.init(Auth.Offline())
        for (clip in CoreArmamentPresentation.SwingClip.entries) for (duration in 2..80) {
            val swing = CoreArmamentPresentation.Swing(clip, 100, duration, ItemStack.AIR, UUID.randomUUID())
            assertNull(swing.poseAt(99)); assertNull(swing.poseAt(100L + duration))
            assertEquals(clip.offset, swing.poseAt(100)); assertEquals(clip.offset + clip.frames - 1, swing.poseAt(99L + duration))
            val poses = (0 until duration).map { swing.poseAt(100L + it)!! }
            assertEquals(poses.sorted(), poses)
        }
    }

    @Test fun `the greatsword has no vanilla attack swing and other weapons keep theirs`() {
        MinecraftServer.init(Auth.Offline())
        for (base in CoreWeaponBase.entries) {
            val id = UUID.randomUUID(); val job = CoreClass.entries.first { base.usable(it) }
            val item = CoreLoopItems.gear(CoreAccount(id, journey = CoreJourney.legacy(job = job),
                weaponIdentity = CoreGearIdentity(UUID.randomUUID(), id, base = base)), CoreGearSlot.WEAPON, true)
            assertEquals(base.family == "greatsword", item.get(DataComponents.ATTACK_ANIMATION)?.type() ==
                net.minestom.server.item.component.SwingAnimation.Type.NONE, base.name)
        }
    }

    @Test fun `every class and tier selects an exported dedicated model only with pack`() {
        MinecraftServer.init(Auth.Offline())
        val seen = mutableSetOf<String>()
        for (job in CoreClass.entries) for (tier in 1..4) {
            val id=UUID.randomUUID()
            val base=CoreWeaponBase.entries.first { it.usable(job) }
            val a=CoreAccount(id,weaponTier=tier,journey=CoreJourney.legacy(job=job),
                weaponIdentity=CoreGearIdentity(UUID.randomUUID(),id,base=base))
            val item=CoreLoopItems.gear(a,CoreGearSlot.WEAPON,true)
            val key=CoreArmamentPresentation.model(base,job,tier)
            assertEquals(key,item.get(DataComponents.ITEM_MODEL).toString())
            assertNotNull(javaClass.classLoader.getResource("core-ui-pack/assets/projects/items/${key.substringAfter(':')}.json"))
            assertFalse(CoreLoopItems.gear(a,CoreGearSlot.WEAPON,false).get(DataComponents.ITEM_MODEL).toString().startsWith("projects:"))
            seen+=key
        }
        assertEquals(24,seen.size)
    }

    @Test fun `animation changes only reserved model float and preserves identity tooltip and other channels`() {
        MinecraftServer.init(Auth.Offline())
        val item=CoreLoopItems.gear(CoreAccount(UUID.randomUUID()),CoreGearSlot.WEAPON,true)
            .with(DataComponents.CUSTOM_MODEL_DATA,CustomModelData(listOf(0f,42f),listOf(true),listOf("keep"),emptyList()))
        for (i in -1..24) {
            val changed=CoreArmamentPresentation.frame(item,i)
            val data=changed.get(DataComponents.CUSTOM_MODEL_DATA)!!
            assertEquals(Math.floorMod(i,12).toFloat(),data.floats()[0])
            assertEquals(listOf(42f),data.floats().drop(1))
            assertEquals(listOf(true),data.flags()); assertEquals(listOf("keep"),data.strings())
            assertEquals(item.without(DataComponents.CUSTOM_MODEL_DATA),changed.without(DataComponents.CUSTOM_MODEL_DATA))
            assertSame(changed,CoreArmamentPresentation.frame(changed,i))
        }
    }

    @Test fun `ordinary items and gear without pack are never animated`() {
        MinecraftServer.init(Auth.Offline())
        for(item in listOf(ItemStack.AIR,ItemStack.of(Material.DIAMOND_SWORD),
            ItemStack.of(Material.PAPER).withItemModel("projects:weapons/greatsword_t1"),
            CoreLoopItems.gear(CoreAccount(UUID.randomUUID()),CoreGearSlot.WEAPON,false))) {
            assertSame(item,CoreArmamentPresentation.frame(item,5))
        }
    }

    @Test fun `measure complete animated stack payload rather than treating a float update as four bytes`() {
        MinecraftServer.init(Auth.Offline())
        val item=CoreLoopItems.gear(CoreAccount(UUID.randomUUID(),weaponTier=4),CoreGearSlot.WEAPON,true)
        val animated=CoreArmamentPresentation.frame(item,5)
        val bytes=NetworkBuffer.makeArray(ItemStack.NETWORK_TYPE,animated).size
        println("ARMAMENT_FRAME_PAYLOAD itemBytes=$bytes rawBytesPerSecondAt10fps=${bytes*10}; excludes packet headers, compression, observers and extra affixes")
        assertTrue(bytes>4) // Inventory synchronization transmits the item, not a float-only delta.
    }

    @Test fun `the shader swing has a track for every beat and its markers stay clear of the light probes`() {
        MinecraftServer.init(Auth.Offline())
        val glsl = javaClass.classLoader.getResource("core-ui-pack/assets/minecraft/shaders/include/greatsword_swing.glsl")!!.readText()
        val vsh = javaClass.classLoader.getResource("core-ui-pack/assets/minecraft/shaders/core/item.vsh")!!.readText()
        fun int(name: String) = Regex("const int $name = (\\d+);").find(glsl)!!.groupValues[1].toInt()
        assertEquals(CoreArmamentPresentation.SHADER_BEATS, int("SWING_BEATS"))
        assertEquals(CoreArmamentPresentation.TACKLE_CHARGE_TICKS, int("SWING_CHARGE_TICKS"))
        assertEquals(CoreArmamentPresentation.TACKLE_IMPACT_TICKS, int("SWING_IMPACT_TICKS"))
        for (beat in 1..CoreArmamentPresentation.SHADER_BEATS) for (track in listOf("BIG", "PLAIN", "RARM", "LARM")) {
            assertTrue("void swingTrack_${track}_$beat(" in glsl, "$track $beat")
            assertTrue("swingTrack_${track}_$beat(u, g)" in vsh, "dispatched $track $beat")
        }
        val markers = (1..CoreArmamentPresentation.SHADER_BEATS).flatMap { b -> listOf(false, true).map { CoreArmamentPresentation.shaderMarker(b, it) } }
        assertEquals((84..93).toList(), markers.sorted())
        assertTrue("swingMarker >= 84 && swingMarker <= 93" in vsh)
        assertTrue("swingMarker >= 116 && swingMarker <= 125" in vsh && "swingMarker >= 100 && swingMarker <= 109" in vsh)
        assertTrue("bool plain = swingMarker >= 89;" in vsh)
        assertTrue(markers.all { it !in 80..83 } && markers.all { it + 16 !in 80..83 && it + 32 <= 255 })
        // Beats past the last fall back to it; the held sword's model takes every value from ANIM_POSE.
        assertEquals(CoreArmamentPresentation.shaderMarker(CoreArmamentPresentation.SHADER_BEATS, false), CoreArmamentPresentation.shaderMarker(9, false))
        val item = CoreLoopItems.gear(CoreAccount(UUID.randomUUID()), CoreGearSlot.WEAPON, true)
        for (beat in 1..CoreArmamentPresentation.SHADER_BEATS)
            assertEquals((CoreArmamentPresentation.ANIM_POSE + beat).toFloat(),
                CoreArmamentPresentation.pose(item, CoreArmamentPresentation.ANIM_POSE + beat).get(DataComponents.CUSTOM_MODEL_DATA)?.floats()?.first())
    }
}
