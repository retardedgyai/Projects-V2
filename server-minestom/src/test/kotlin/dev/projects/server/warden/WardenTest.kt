package dev.projects.server.warden

import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.*
import kotlin.test.*

class WardenTest {
    private val asset=WardenAsset.load()
    @Test fun `asset has hierarchy locators eight full clips and normalized transforms`() {
        assertEquals(31,asset.bones.size);assertEquals(8,asset.clips.size)
        assertTrue(asset.bones.any {it.name=="vfx_ground"});assertTrue(asset.bones[asset.tip].parent==asset.weapon)
        asset.clips.values.forEach {c->
            assertEquals(c.duration+1,c.frames.size)
            c.frames.flatten().forEach {p->assertEquals(1.0,p.q.x*p.q.x+p.q.y*p.q.y+p.q.z*p.q.z+p.q.w*p.q.w,1e-4)}
            if(c.activeStart>=0){assertTrue(c.activeStart>=10);assertTrue(c.duration-c.activeEnd>=12)}
        }
    }
    @Test fun `capsule rejects expanded-box corner false positives`() {
        val lo=V3(0.0,0.0,0.0);val hi=V3(1.0,1.0,1.0)
        assertEquals(.08,segmentBoxDistanceSquared(V3(-.2,-.2,0.0),V3(-.2,-.2,1.0),lo,hi),1e-9)
        assertEquals(0.0,segmentBoxDistanceSquared(V3(-1.0,.5,.5),V3(2.0,.5,.5),lo,hi),1e-9)
        assertEquals(3.0,segmentBoxDistanceSquared(V3(2.0,2.0,2.0),V3(2.0,2.0,2.0),lo,hi),1e-9)
    }
    @Test fun `rotating sword hits between endpoint poses without hitting remote victims`() {
        val a=BonePose(V3(0.0,1.0,0.0),Q4.yaw(-PI/3));val b=BonePose(V3(0.0,1.0,0.0),Q4.yaw(PI/3))
        assertTrue(sweptBladeHit(a,b,V3(-.1,.5,1.8),V3(.1,1.5,2.0)))
        assertFalse(sweptBladeHit(a,b,V3(8.0,0.0,8.0),V3(8.6,1.8,8.6)))
    }
    @Test fun `each authored attack crosses reachable player height`() {
        for(name in listOf("slash_01","heavy_slash","dash")) {
            val c=asset.clips.getValue(name)
            val hits=(c.activeStart..c.activeEnd).any {t->
                sweptBladeHit(c.frames[t-1][asset.weapon],c.frames[t][asset.weapon],V3(-.3,0.0,1.8),V3(.3,1.8,2.4))
            }
            assertTrue(hits,"$name never crosses a player in front")
        }
    }
    @Test fun `weapon locator is rigidly attached to display bone`() {
        asset.clips.values.forEach {c->c.frames.forEach {row->
            val expected=row[asset.weapon].point(V3(0.0,0.0,2.64))
            assertTrue((expected-row[asset.tip].p).length()<1e-4)
        }}
    }
    @Test fun `sword grip cannot rotate or drift independently of the closed hand`() {
        val hand=asset.bones.indexOfFirst {it.name=="hand_r"}
        asset.clips.values.forEach {c->c.frames.forEach {row->
            val h=row[hand];val w=row[asset.weapon]
            assertTrue((h.point(V3(0.0,-.08,-.06))-w.p).length()<1e-4,c.name)
            for(axis in listOf(V3(1.0,0.0,0.0),V3(0.0,1.0,0.0),V3(0.0,0.0,1.0))) {
                assertTrue(((h.point(axis)-h.p)-(w.point(axis)-w.p)).length()<1e-4,c.name)
            }
        }}
    }
    @Test fun `right arm joints remain connected without stretching across all clips`() {
        val upper=asset.bones.indexOfFirst {it.name=="upper_arm_r"}
        val fore=asset.bones.indexOfFirst {it.name=="forearm_r"}
        val hand=asset.bones.indexOfFirst {it.name=="hand_r"}
        asset.clips.values.forEach {c->c.frames.forEach {row->
            assertTrue((row[upper].point(V3(0.0,-.43*1.18,0.0))-row[fore].p).length()<1e-4,c.name)
            assertTrue((row[fore].point(V3(0.0,-.41*1.18,0.0))-row[hand].p).length()<1e-4,c.name)
        }}
    }
    @Test fun `walking stance foot remains planted when root advances at authored speed`() {
        val c=asset.clips.getValue("walk")
        val foot=asset.bones.indexOfFirst {it.name=="foot_l"}
        for(t in 1..19) {
            val before=c.frames[t-1][foot].p+V3(0.0,0.0,(t-1)*.04)
            val after=c.frames[t][foot].p+V3(0.0,0.0,t*.04)
            assertTrue((before-after).length()<1e-4)
        }
    }
    @Test fun `attack locks facing and only claims a victim once`() {
        val f=WardenFight(asset);f.begin();val target=V3(0.0,0.0,2.5)
        repeat(31){f.tick(target)}
        assertEquals("slash_01",f.action);val yaw=f.yaw
        while(!f.active)f.tick(V3(4.0,0.0,0.0))
        assertEquals(yaw,f.yaw);val id=UUID.randomUUID();assertTrue(f.claimHit(id));assertFalse(f.claimHit(id))
        while(f.active)f.tick(target)
        assertFalse(f.claimHit(UUID.randomUUID()))
    }
    @Test fun `phase transition waits for committed attack recovery and happens once`() {
        val f=WardenFight(asset);f.begin();val target=V3(0.0,0.0,2.5)
        repeat(35){f.tick(target)};assertEquals("slash_01",f.action)
        f.damage(185.0);assertEquals("slash_01",f.action)
        repeat(120){f.tick(target)};assertEquals(2,f.phase)
        val hp=f.health;f.damage(1.0);assertEquals(hp-1,f.health)
    }
    @Test fun `death cancels damage windows and reset restores clean state`() {
        val f=WardenFight(asset);f.begin();repeat(46){f.tick(V3(0.0,0.0,2.5))}
        f.damage(400.0);assertTrue(f.dead);assertFalse(f.active);assertFalse(f.damage(1.0))
        repeat(60){f.tick(null)};assertTrue(f.finished)
        f.reset();assertFalse(f.running);assertEquals(360.0,f.health);assertEquals(1,f.phase);assertEquals("idle",f.action)
    }
    @Test fun `abandoned encounter resets and idle never deals damage`() {
        val f=WardenFight(asset);assertFalse(f.active);f.begin();repeat(110){f.tick(null)}
        assertFalse(f.running);assertEquals(V3.ZERO,f.position)
    }
    @Test fun `bundled pack has every visible bone model and only 32px textures`() {
        val files=mutableMapOf<String,ByteArray>()
        ZipInputStream(requireNotNull(javaClass.getResourceAsStream("/ashen-warden/warden-pack.zip"))).use {z->
            while(true){val e=z.nextEntry?:break;assertFalse(e.name.contains(".."));files[e.name]=z.readAllBytes()}
        }
        assertTrue(files.getValue("pack.mcmeta").decodeToString().contains("88"))
        asset.bones.filter {it.visible}.forEach {assertTrue(files.containsKey("assets/projects/items/warden/${it.name}.json"))}
        val png=files.filterKeys {it.endsWith(".png")};assertEquals(7,png.size)
        png.forEach {(name,bytes)->val im=javax.imageio.ImageIO.read(bytes.inputStream());assertEquals(32,im.width,name);assertEquals(32,im.height,name)}
        assertTrue(files.keys.none {it.startsWith("assets/minecraft/")})
    }
}
