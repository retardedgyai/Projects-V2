package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Eight non-pull skills. The two pulls retain their endpoint-bound chains. */
internal object CoreTemplarChoreography {
    fun parts(e: CoreSkillEffect,life: Int): List<CoreCombatMeshPart> {
        val yaw=atan2(e.direction.x(),e.direction.z())
        fun local(x: Double,y: Double,z: Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        val prepare=e.phase==CoreSkillVisualPhase.PREPARE
        if(e.phase==CoreSkillVisualPhase.CONTACT) return chips(Vec(0.0,1.0,0.0),yaw,18,
            armor=e.sceneId=="temp_break",count=6)
        return when(e.sceneId) {
            "temp_mace","temp_break" -> {
                val heavy=e.sceneId=="temp_break"
                val length=if(heavy) 1.9 else 1.5
                val grip=local(.32,if(heavy) 1.3 else 1.1,.45)
                val hammer=CoreCombatMeshPart(if(heavy) "oath_breaker" else "oath_hammer","gold",grip,
                    Vec(if(heavy) 1.1 else .9,1.0,length),yaw=yaw,pitch=if(prepare) -1.3 else .7,
                    pitchTravel=if(prepare) 2.0 else -.45,durationTicks=if(prepare) life else 12,
                    startSize=1.0,endSize=1.0,followOwner=prepare,erode=!prepare)
                if(prepare) listOf(hammer) else {
                    val tip=grip.add(Vec(sin(yaw)*cos(.7),-sin(.7),cos(yaw)*cos(.7)).mul(length))
                    val root=Vec(tip.x(),.12,tip.z())
                    listOf(hammer)+fractures(root,yaw,if(heavy) 2.1 else 1.65,life)+
                        chips(root.add(0.0,.2,0.0),yaw,life,armor=false,count=5)
                }
            }
            "temp_guard" -> listOf(-1,1).map { side ->
                CoreCombatMeshPart(if(side<0) "oath_guard_left" else "oath_guard_right","gold",
                    local(side*.2,1.03,.72),Vec(1.4,1.7,.8),yaw=yaw,
                    durationTicks=if(prepare) life else e.skill.duration,followOwner=true,
                    startSize=1.0,endSize=1.0)
            }
            "temp_dash" -> {
                val shield=CoreCombatMeshPart("oath_shield","gold",local(0.0,1.0,if(prepare) .55 else .95),
                    Vec(1.4,1.7,.8),yaw=yaw,travel=local(0.0,0.0,if(prepare) .4 else .45),
                    durationTicks=if(prepare) life else 10,startSize=1.0,endSize=1.0,
                    motion=CoreMeshMotion.THRUST,erode=!prepare,followOwner=prepare)
                if(prepare) listOf(shield) else listOf(shield)+
                    chips(local(0.0,.8,1.15),yaw,life,armor=false,count=5)
            }
            "temp_rebuke" -> if(prepare) listOf(-1,1).map { side ->
                CoreCombatMeshPart("oath_shield","gold",local(side*.7,1.0,.65),Vec(.75,1.3,.6),
                    yaw=yaw,travel=local(-side*.35,0.0,0.0),durationTicks=life,
                    startSize=.6,endSize=1.0,motion=CoreMeshMotion.GATHER,followOwner=true)
            } else wave(Vec(0.0,.12,0.0),yaw,e.radius,life,4)+
                chips(Vec(0.0,.3,0.0),yaw,life,armor=false,count=6)
            "temp_field" -> {
                // Only the first wave owns the posts. Repeated server pulses brighten
                // the boundary, not four overlapping copies of the entire enclosure.
                if(prepare && e.pulse>0) emptyList() else buildList {
                    if(prepare || e.pulse==0) repeat(4) { i ->
                        val a=yaw+PI/4+i*PI/2
                        add(CoreCombatMeshPart("oath_boundary","gold",Vec(sin(a)*e.radius,.12,cos(a)*e.radius),
                            Vec(.7,.7,1.05),yaw=a,pitch=-PI/2,ground=true,
                            startSize=if(prepare) .05 else 1.0,endSize=1.0,
                            durationTicks=if(prepare) life else (e.skill.pulses-1)*8+8,
                            motion=if(prepare) CoreMeshMotion.GATHER else CoreMeshMotion.LINEAR,erode=false))
                    }
                    if(!prepare) addAll(wave(Vec(0.0,.12,0.0),yaw+PI/4,e.radius,8,4).map {
                        it.copy(startSize=.9,endSize=1.0)
                    })
                }
            }
            "temp_ward","temp_sanctuary" -> {
                val sanctuary=e.sceneId=="temp_sanctuary"
                val count=if(sanctuary) 6 else 3
                val radius=if(sanctuary) 2.3 else 1.05
                (0 until count).map { i ->
                    // No panel is centered directly on the forward view line.
                    val a=yaw+if(sanctuary) PI/6+i*PI/3 else listOf(-PI/3,PI/3,PI)[i]
                    CoreCombatMeshPart(if(sanctuary) "oath_arch" else "oath_shield","gold",
                        Vec(sin(a)*radius,if(sanctuary) .12 else 1.0,cos(a)*radius),
                        if(sanctuary) Vec(1.3,1.0,2.25) else Vec(1.1,1.6,.8),yaw=a,
                        pitch=if(sanctuary) -PI/2 else 0.0,
                        startSize=if(prepare) .05 else 1.0,endSize=1.0,
                        durationTicks=life,motion=if(prepare) CoreMeshMotion.GATHER else CoreMeshMotion.LINEAR,
                        followOwner=!sanctuary,ground=sanctuary,erode=!prepare)
                } + if(prepare) emptyList() else (0 until 4).map { i ->
                    val side=if(i%2==0) -1 else 1
                    CoreCombatMeshPart("oath_rune","gold",local(side*.65,.7+i/2*.35,.8),
                        Vec(.3,.4,.3),yaw=yaw,travel=local(side*.25,1.0,0.0),
                        startSize=.8,endSize=.25,delayTicks=4+i,durationTicks=life-4-i,
                        motion=CoreMeshMotion.FLOAT,erode=true,secondary=true,followOwner=!sanctuary)
                }
            }
            else -> error("Unassigned templar phrase ${e.sceneId}")
        }
    }

    /** The hammer rotates around its grip, not its centre; PULSE begins at contact. */
    fun pose(p: CoreCombatMeshPart,age: Double): CoreMeshPose? {
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        if(p.shape in setOf("oath_hammer","oath_breaker")) {
            val t=(local/(if(p.erode) 7.0 else (p.durationTicks-1).coerceAtLeast(1).toDouble())).coerceIn(0.0,1.0)
            val u=if(p.erode) 1-(1-t).pow(3) else t*t
            val pitch=p.pitch+p.pitchTravel*u
            val axis=Vec(sin(p.yaw)*cos(pitch),-sin(pitch),cos(p.yaw)*cos(pitch))
            val fade=if(p.erode) floor(((local-4)/7).coerceIn(0.0,1.0)*7).toInt() else 0
            return CoreMeshPose(p.offset.add(axis.mul(p.scale.z()*.5)),p.scale,p.yaw,pitch,0.0,
                "combat_vfx/${p.shape}_gold"+if(fade>0) "_fade$fade" else "",
                age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
        }
        if(p.shape in setOf("oath_guard_left","oath_guard_right")) {
            val side=if(p.shape.endsWith("left")) -1 else 1
            val enter=(local/4).coerceIn(0.0,1.0)
            val leave=((local-(p.durationTicks-6))/5).coerceIn(0.0,1.0)
            val shift=side*.3*leave
            val fade=floor(leave*7).toInt()
            return CoreMeshPose(p.offset.add(Vec(cos(p.yaw)*shift,0.0,-sin(p.yaw)*shift)),p.scale,
                p.yaw+side*(.5*(1-enter)+.45*leave),0.0,0.0,
                "combat_vfx/${p.shape}_gold"+if(fade>0) "_fade$fade" else "",
                age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
        }
        return null
    }

    private fun wave(root: Vec,yaw: Double,radius: Double,life: Int,count: Int)=(0 until count).map { i ->
        CoreCombatMeshPart("oath_wave_${ceil(radius).toInt().coerceIn(1,11)}","gold",root,Vec(radius,1.0,radius),yaw=yaw+i*PI/2,
            durationTicks=life,startSize=.15,endSize=1.0,motion=CoreMeshMotion.SWEEP,ground=true,erode=true)
    }
    private fun fractures(root: Vec,yaw: Double,radius: Double,life: Int)=(0 until 5).map { i ->
        CoreCombatMeshPart("oath_fracture","steel",root,Vec(.75,1.0,radius),yaw=yaw+i*PI*2/5,
            durationTicks=life,startSize=.1,endSize=1.0,motion=CoreMeshMotion.SWEEP,ground=true,
            erode=true,secondary=i>=3)
    }
    private fun chips(root: Vec,yaw: Double,life: Int,armor: Boolean,count: Int)=(0 until count).map { i ->
        val a=yaw+i*PI*2/count+.18
        CoreCombatMeshPart(if(armor) "oath_armor_chip" else "oath_stone_chip",if(armor) "gold" else "steel",
            root,Vec(.38+i%2*.13,.45,.55),yaw=a,pitch=i*.4,roll=i*.3,
            travel=Vec(sin(a)*1.25,-.15,cos(a)*1.25),bend=Vec(0.0,.9+i%2*.35,0.0),
            pitchTravel=1.6,rollTravel=.9,startSize=1.0,endSize=.45,durationTicks=life,
            motion=CoreMeshMotion.FLOAT,erode=true,secondary=i>=3)
    }
}
