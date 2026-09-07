package dev.projects.server.warden

import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.*
import kotlin.test.*

class WardenTest {
    private val asset=WardenAsset.load()
    @Test fun `asset has hierarchy locators thirteen full clips and normalized transforms`() {
        assertEquals(31,asset.bones.size);assertEquals(13,asset.clips.size)
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
        for(name in asset.clips.values.filter {it.activeStart>=0}.map {it.name}) {
            val c=asset.clips.getValue(name)
            val hits=(c.activeStart..c.activeEnd).filter(c::active).any {t->
                val distance=when(name){"dash"->3.6;"vault_slam"->4.3;else->2.1}
                sweptBladeHit(c.frames[t-1][asset.weapon],c.frames[t][asset.weapon],V3(-.3,0.0,distance-.3),V3(.3,1.8,distance+.3))
            }
            assertTrue(hits,"$name never crosses a player in front")
        }
    }
    @Test fun `weapon locator is rigidly attached to display bone`() {
        asset.clips.values.forEach {c->c.frames.forEach {row->
            val expected=row[asset.weapon].point(V3(0.0,0.0,WardenBlade.TIP))
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
        assertEquals("slash_01",f.action)
        while(f.frame<f.clip.activeStart-4)f.tick(target)
        val yaw=f.yaw
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
    @Test fun `authored lunge remains in server position after attack recovery`() {
        val f=WardenFight(asset);f.begin();val target=V3(0.0,0.0,2.5)
        repeat(31){f.tick(target)};assertEquals("slash_01",f.action)
        val start=f.position
        while(f.action=="slash_01")f.tick(target)
        assertEquals(.55,(f.position-start).z,1e-4)
        val pelvis=asset.bones.indexOfFirst {it.name=="pelvis"}
        val expected=f.position+asset.clips.getValue("idle").frames[0][pelvis].p.rotateYaw(f.yaw)
        assertTrue((expected-f.worldPose()[pelvis].p).length()<1e-4)
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
    @Test fun `interrupted pose blends preserve rigid grip and arm joints`() {
        val from=asset.clips.getValue("slash_01").frames[21]
        val to=asset.clips.getValue("death").frames[3]
        val hand=asset.bones.indexOfFirst {it.name=="hand_r"}
        val fore=asset.bones.indexOfFirst {it.name=="forearm_r"}
        for(step in 0..6) {
            val poses=blendBoneHierarchy(asset.bones,from,to,step/6.0)
            assertTrue((poses[hand].point(V3(0.0,-.08,-.06))-poses[asset.weapon].p).length()<1e-4)
            assertTrue((poses[fore].point(V3(0.0,-.41*1.18,0.0))-poses[hand].p).length()<1e-4)
        }
    }
    @Test fun `vanilla item renderer half-turn is cancelled for all animated bone bases`() {
        // Actual 26.2 client pipeline: bone T R S, right rotation, renderer Y(pi),
        // then the centered model whose export uses 6 model pixels per world block.
        val clientRotation=Q4(0.0,1.0,0.0,0.0)
        val probes=listOf(V3(.31,.12,.27),V3(-.23,-.41,.16),V3(0.0,0.0,WardenBlade.TIP))
        asset.clips.values.forEach {c->c.frames.forEach {row->row.forEach {bone->
            probes.forEach {local->
                val model=local*(1.0/WardenItemDisplay.SCALE)
                val displayed=bone.point(WardenItemDisplay.rightRotation.rotate(clientRotation.rotate(model))*WardenItemDisplay.SCALE)
                assertTrue((displayed-bone.point(local)).length()<1e-6,"${c.name}: display differs from authoritative bone")
            }
        }}}
    }
    @Test fun `double spin has two separate damage windows and one hit per victim per pass`() {
        val f=WardenFight(asset);f.demonstrate("spiral_combo");val id=UUID.randomUUID()
        while(f.frame<18)f.tick(null)
        assertTrue(f.claimHit(id));assertFalse(f.claimHit(id))
        while(f.frame<35)f.tick(null)
        assertFalse(f.active);assertFalse(f.claimHit(UUID.randomUUID()))
        while(f.frame<41)f.tick(null)
        assertTrue(f.claimHit(id));assertFalse(f.claimHit(id))
    }
    @Test fun `rebuilt dash stays grounded and returns without reversing its travel`() {
        val c=asset.clips.getValue("dash")
        assertTrue(c.frames.all {abs(it[0].p.y)<1e-5})
        assertEquals(1.70,c.frames.last()[0].p.z,1e-4)
        assertTrue(c.frames.zipWithNext().all {(a,b)->b[0].p.z>=a[0].p.z-1e-5})
    }
    @Test fun `somersault has genuine inverted torso at jump apex and a finite landing window`() {
        val c=asset.clips.getValue("vault_slam");val pelvis=asset.bones.indexOfFirst {it.name=="pelvis"}
        assertTrue(c.frames.maxOf {it[0].p.y}>2.3)
        assertTrue(c.frames.any {it[0].p.y>1.8 && it[pelvis].q.rotate(V3(0.0,1.0,0.0)).y<-.8})
        assertEquals(listOf(34..37),c.windows)
        assertEquals(0.0,c.frames.last()[0].p.y,1e-5)
    }
    @Test fun `move demonstration ends after one action without starting another attack`() {
        val f=WardenFight(asset);f.demonstrate("spin_slash")
        repeat(100){f.tick(V3(0.0,0.0,2.0))}
        assertFalse(f.running);assertEquals("idle",f.action)
    }
    @Test fun `cutting edge leads the transverse blade travel instead of striking with the flat`() {
        // The actual exported blade is wide along local Y, thin along X, long along Z.
        asset.clips.values.filter {it.activeStart>=0}.forEach {c->
            for(t in 1..c.duration)if(c.active(t)) {
                val before=c.frames[t-1][asset.weapon];val blade=c.frames[t][asset.weapon]
                val velocity=blade.point(V3(0.0,0.0,1.6))-before.point(V3(0.0,0.0,1.6))
                val axis=blade.q.rotate(V3(0.0,0.0,1.0));val transverse=velocity-axis*velocity.dot(axis)
                if(transverse.length()>.005)assertTrue(abs(transverse.unit().dot(blade.q.rotate(V3(0.0,1.0,0.0))))>.95,"${c.name} tick $t hits with the blade flat")
            }
        }
    }
    @Test fun `blade and guard stay outside the head and torso cores in every attack pose`() {
        val cores=listOf(Triple("chest",V3(-.34,-.22,-.23),V3(.34,.29,.30)),Triple("head",V3(-.24,-.23,-.18),V3(.24,.57,.24)),Triple("spine",V3(-.26,-.24,-.18),V3(.26,.25,.25)))
        val probes=(0..31).flatMap {n->listOf(-.30,0.0,.30).map {y->V3(0.0,y,.43+n*(WardenBlade.TIP-.43)/31)}}+
            listOf(.30,.44,.60).flatMap {z->listOf(-.45,-.30,0.0,.30,.45).map {y->V3(0.0,y,z)}}
        asset.clips.values.filter {it.activeStart>=0}.forEach {c->c.frames.forEachIndexed {t,row->
            cores.forEach {(name,lo,hi)->
                val body=row[asset.bones.indexOfFirst {it.name==name}];val inverse=Q4(-body.q.x,-body.q.y,-body.q.z,body.q.w)
                probes.forEach {probe->val p=inverse.rotate(row[asset.weapon].point(probe)-body.p)
                    assertFalse(p.x>lo.x && p.x<hi.x && p.y>lo.y && p.y<hi.y && p.z>lo.z && p.z<hi.z,"${c.name} tick $t sword enters $name")}
            }
        }}
    }
    @Test fun `every attack moves upper arm relative to chest and folds then extends the elbow`() {
        val chest=asset.bones.indexOfFirst {it.name=="chest"};val upper=asset.bones.indexOfFirst {it.name=="upper_arm_r"}
        val fore=asset.bones.indexOfFirst {it.name=="forearm_r"};val hand=asset.bones.indexOfFirst {it.name=="hand_r"}
        asset.clips.values.filter {it.activeStart>=0}.forEach {c->
            val bends=c.frames.map {row->acos(((row[fore].p-row[upper].p).unit().dot((row[hand].p-row[fore].p).unit())).coerceIn(-1.0,1.0))}
            val arms=c.frames.map {row->val q=row[chest].q;Q4(-q.x,-q.y,-q.z,q.w).rotate((row[fore].p-row[upper].p).unit())}
            val swing=arms.maxOf {a->arms.maxOf {b->acos(a.dot(b).coerceIn(-1.0,1.0))}}
            assertTrue(swing>PI/4,"${c.name} upper arm only rides the chest")
            assertTrue(bends.max()-bends.min()>PI/4,"${c.name} elbow stays fixed")
        }
    }
    @Test fun `circling either side cannot starve attacks in either phase`() {
        for(direction in listOf(-1,1))for(secondPhase in listOf(false,true)) {
            val f=WardenFight(asset);f.begin();if(secondPhase)f.damage(190.0)
            val attacks=mutableSetOf<Int>();val names=mutableSetOf<String>()
            repeat(700){t->
                val angle=direction*t*.22
                f.tick(f.position+V3(sin(angle)*2.6,0.0,cos(angle)*2.6))
                if(f.active){attacks+=f.sequence;names+=f.action}
            }
            assertTrue(attacks.size>=5,"orbit $direction phase2=$secondPhase starved attacks: $attacks")
            assertTrue(names.any {it in listOf("rush_combo","onslaught","spin_slash")})
        }
    }
    @Test fun `rush and onslaught deliver three and four independently claimable fast hits`() {
        for((name,count)in listOf("rush_combo" to 3,"onslaught" to 4)) {
            val f=WardenFight(asset);f.demonstrate(name);val id=UUID.randomUUID();var hits=0
            repeat(f.clip.duration){if(f.claimHit(id)){hits++;assertFalse(f.claimHit(id))};f.tick(null)}
            assertEquals(count,hits,name)
            assertTrue(asset.clips.getValue(name).windows.zipWithNext().all {(a,b)->b.first-a.last<=14})
        }
    }
    @Test fun `leap rises and falls continuously instead of hovering during the rotation`() {
        val c=asset.clips.getValue("vault_slam")
        for(t in 20..35)assertTrue(c.frames[t+1][0].p.y-2*c.frames[t][0].p.y+c.frames[t-1][0].p.y<-.02)
        assertEquals(3.2,c.frames.last()[0].p.z,1e-4)
        assertTrue(WardenBlade.TIP>3.4)
    }
    @Test fun `pixel surfaces have small discrete palettes instead of continuous material noise`() {
        ZipInputStream(requireNotNull(javaClass.getResourceAsStream("/ashen-warden/warden-pack.zip"))).use {z->
            while(true){val e=z.nextEntry?:break;if(!e.name.endsWith(".png"))continue
                val im=javax.imageio.ImageIO.read(z.readAllBytes().inputStream());val colors=mutableSetOf<Int>()
                for(y in 0 until im.height)for(x in 0 until im.width)colors+=im.getRGB(x,y)
                assertTrue(colors.size in 3..5,"${e.name} has ${colors.size} colors")
                if(e.name.endsWith("/iron.png"))assertTrue(colors.contains(0xFF7896BE.toInt()),"sRGB palette was darkened during export")
            }
        }
    }
    @Test fun `combo wrist does not snap ninety degrees between swings or during recovery`() {
        for(name in listOf("rush_combo","onslaught")) {
            val c=asset.clips.getValue(name)
            for(t in 1..c.duration)if(!c.active(t)) {
                val a=c.frames[t-1][asset.weapon].q;val b=c.frames[t][asset.weapon].q
                val dot=abs(a.x*b.x+a.y*b.y+a.z*b.z+a.w*b.w).coerceIn(0.0,1.0)
                assertTrue(2*acos(dot)<Math.toRadians(65.0),"$name wrist snaps at tick $t")
            }
        }
    }
}
