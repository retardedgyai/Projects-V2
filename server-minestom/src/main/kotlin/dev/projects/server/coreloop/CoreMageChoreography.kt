package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

/** Mage-only material direction. Never decides a hit, cast origin, pulse, cost or movement. */
internal object CoreMageChoreography {
    val sceneIds=setOf("firebolt","frost_nova","meteor","mage_blink","mage_mark",
        "mage_garden","mage_burst","mage_ward","mage_ult","mage_zero")
    fun owns(p:CoreCombatMeshPart)=p.shape.startsWith("mage_material:")
    private val movingContours=setOf("mage_material:meteor_front","mage_material:meteor_break_1","mage_material:meteor_break_2")+(0..3).map { "mage_material:meteor_flow_$it" }
    // Native model-space centres from build_mage_meteor.lobe_centers().
    internal val meteorFragmentCenters=listOf(Vec(-6.75,4.0,-.75),
        Vec(1.875,10.0,.75),Vec(7.5,4.0,-2.25))
    fun interpolated(p:CoreCombatMeshPart)=p.shape in movingContours
    private val longClips=setOf("pyre","corona","crystal","ice_root","ice_shelf","zero_crown","zero_floor","zero_shelf","zero_wing",
        "garden_spires","garden_fan","garden_bed","garden_spray")
    private fun frames(clip:String)=when(clip) {
        in longClips -> 48
        "flame_hit","ice_hit","thunder_hit" -> 18
        "frost_wave","frost_trace","ward","ward_mote" -> 30
        "discharge","rupture" -> 28
        else -> 24
    }
    fun parts(e:CoreSkillEffect):List<CoreCombatMeshPart>? {
        if(e.job!=CoreClass.MAGE || e.sceneId !in sceneIds) return null
        if(!e.valid) return emptyList()
        val yaw=atan2(e.direction.x(),e.direction.z())
        val pitch=-atan2(e.direction.y(),hypot(e.direction.x(),e.direction.z()))
        val r=min(e.radius,CoreSkillScenes.get(e.sceneId).reach)
        fun local(x:Double,y:Double,z:Double)=Vec(cos(yaw)*x+sin(yaw)*z,y,-sin(yaw)*x+cos(yaw)*z)
        fun piece(clip:String,at:Vec,scale:Vec,tilt:Double=0.0,life:Int=frames(clip),
                  facing:Double=yaw,secondary:Boolean=false,ground:Boolean=false,follow:Boolean=false,
                  travel:Vec=Vec.ZERO,delay:Int=0)=CoreCombatMeshPart("mage_material:$clip",
            CoreSkillScenes.get(e.sceneId).palette,at,scale,yaw=facing,pitch=tilt,
            durationTicks=life,startSize=1.0,endSize=1.0,secondary=secondary,ground=ground,
            followOwner=follow,travel=travel,delayTicks=delay)
        fun floor(clip:String,radius:Double,life:Int=frames(clip),follow:Boolean=false)=
            piece(clip,Vec(0.0,.12,0.0),Vec(radius*2,1.0,radius*2),life=life,ground=true,follow=follow)
        if(e.phase==CoreSkillVisualPhase.CONTACT) {
            val clip=when(e.skill.element) { 1 -> "flame_hit"; 2 -> "ice_hit"; else -> "thunder_hit" }
            // One small hit confirmation, never a second field explosion at each victim.
            return listOf(piece(clip,Vec(0.0,1.0,0.0),
                Vec(1.25,1.25,1.25),if(clip=="flame_hit")pitch else 0.0))
        }
        if(e.phase==CoreSkillVisualPhase.PREPARE) {
            // FIELD runtime requests a new preparation before every damage beat.
            // Persistent garden/pyre already own the whole phrase after the first beat.
            if(e.pulse>0 && e.sceneId in setOf("mage_garden","mage_ult")) return emptyList()
            if(e.sceneId=="mage_garden") return listOf(floor("garden_charge",min(r*.55,2.5),e.prepareDuration))
            val charge=when(e.skill.element) { 1 -> "fire_charge"; 2 -> "ice_charge"; else -> "arcane_charge" }
            if(e.sceneId in setOf("meteor","mage_ult")) {
                val a=e.pulse*2.39996
                val at=if(e.pulse==0) Vec.ZERO else Vec(cos(a)*min(e.radius*.35,2.4),0.0,sin(a)*min(e.radius*.35,2.4))
                if(e.sceneId=="meteor") return listOf(piece("meteor",at.add(0.0,4.6,0.0),Vec(2.4,2.4,2.4),
                    life=e.prepareDuration+if(e.pulse==0)1 else 0,travel=Vec(0.0,-3.7,0.0)))
                return listOf(piece("meteor",at.add(0.0,4.6,0.0),Vec(1.8,2.0,1.8),
                    life=e.prepareDuration,travel=Vec(0.0,-3.1,0.0)),floor(charge,min(e.radius,2.5),e.prepareDuration))
            }
            if(e.skill.motion==CoreSkillMotion.FIELD && charge=="ice_charge")
                return listOf(piece(charge,Vec(0.0,.12,0.0),Vec(1.0,.45,1.0),
                    life=e.prepareDuration,ground=true))
            if(e.skill.motion==CoreSkillMotion.FIELD)
                return listOf(floor(charge,r,e.prepareDuration))
            return listOf(piece(charge,local(.38,1.2,.55),
                if(charge=="fire_charge")Vec(1.2,.85,.9) else if(charge=="ice_charge")Vec(.6,.45,.6) else Vec(.9,.7,.7),
                if(charge=="fire_charge")pitch else 0.0,
                life=e.prepareDuration,follow=true))
        }
        if(e.sceneId in setOf("firebolt","mage_mark")) {
            if(e.length<=.05) return emptyList()
            val d=if(e.direction.lengthSquared()>1e-8)e.direction.normalize() else Vec(0.0,0.0,1.0)
            val flame=e.sceneId=="firebolt"
            val length=if(flame) min(e.length,3.4) else e.length
            // Drawing plane is XZ, so its Z extent remains strictly on the accepted segment at every pitch.
            val main=piece(if(flame)"cinder" else "conductor",d.mul(e.length-length*.5),
                Vec(if(flame)4.0 else 1.4,if(flame)1.8 else .65,length),pitch)
            return if(!flame)listOf(main,piece("arcane_forks",d.mul(e.length*.5),Vec(4.0,3.4,e.length),pitch)) else listOf(main,
                piece("fire_stream",d.mul(e.length*.5),Vec(2.2,2.2,e.length),pitch))
        }
        return when(e.sceneId) {
            "meteor" -> {
                val a=e.pulse*2.39996
                val at=if(e.pulse==0)Vec.ZERO else Vec(cos(a)*min(e.radius*.35,2.4),0.0,sin(a)*min(e.radius*.35,2.4))
                // R12: one ground-rooted pressure front opens around an empty
                // centre. Its three regions only separate AFTER expansion.
                listOf(piece("eruption",at.add(0.0,.12,0.0),Vec(4.2,2.0,4.2),ground=true),
                    piece("meteor_ring",at.add(0.0,.13,0.0),Vec(6.0,1.0,6.0),ground=true,secondary=true),
                    piece("meteor_front",at.add(0.0,.12,0.0),Vec(6.2,3.0,4.2),life=5,ground=true))+
                    listOf(Vec(.5,.4,.0),Vec.ZERO,Vec(-.4,.35,-.1),Vec(-1.3,1.45,-.2)).mapIndexed { i,start ->
                        val drift=listOf(Vec(1.1,.25,.35),Vec(0.0,.05,.15),Vec(-1.2,.4,-.15),Vec(-.6,.55,-.35))[i]
                        val end=start.add(drift)
                        val scale=when(i) { 1 -> Vec(4.4,4.8,4.4); 3 -> Vec(4.2,2.0,4.2); else -> Vec(5.6,3.0,4.8) }
                        piece("meteor_flow_$i",at.add(0.0,.12,0.0),scale,life=if(i%2==0)6 else 12,ground=true,
                            travel=local(end.x(),end.y(),end.z())).copy(
                            bend=local(start.x(),start.y(),start.z()))
                    }.flatMap { p -> if(p.shape!="mage_material:meteor_flow_1")listOf(p)
                        else listOf(p,p.copy(shape="mage_material:meteor_break_1"),p.copy(shape="mage_material:meteor_break_2")) }
            }
            "mage_ult" -> if(e.pulse>0) listOf(piece("solar_flare",Vec(0.0,.12,0.0),Vec(2.6,2.3,2.3),
                life=12,ground=true,secondary=true,facing=yaw+e.pulse*.9)) else {
                val life=(e.skill.pulses-1)*8+24
                listOf(floor("corona",r,life),
                    piece("pyre",Vec(0.0,3.4,0.0),Vec(2.6,2.6,2.6),life=life),
                    piece("solar_flare",Vec(0.0,.12,0.0),Vec(2.6,2.3,2.3),life=12,ground=true,secondary=true))
            }
            "frost_nova" -> listOf(floor("frost_trace",r*.68,18))+
                listOf(.35 to 1.0,2.45 to .77,4.5 to .58).map { (a,size) ->
                piece("frost_wave",Vec(sin(a)*r*.24,.12,cos(a)*r*.24),Vec(min(3.0,r*.7)*size,.65*size,1.8),
                    life=18,facing=a,ground=true,travel=Vec(sin(a)*r*.44,0.0,cos(a)*r*.44))
            }
            "mage_garden" -> if(e.pulse>0) listOf(piece("garden_beat",Vec(0.0,.12,0.0),
                Vec(r*1.1,1.8,r*1.1),life=8,ground=true,secondary=true)) else {
                val life=(e.skill.pulses-1)*8+24
                listOf(floor("garden_bed",r*.55,life),
                    piece("garden_spires",local(-r*.18,.12,r*.10),Vec(1.8,1.8,1.8),life=life,ground=true,facing=yaw+.25),
                    piece("garden_fan",local(r*.20,.12,r*.22),Vec(1.8,1.8,1.8),life=life-2,ground=true,delay=2,facing=yaw-.65),
                    piece("garden_fan",local(r*.08,.12,-r*.23),Vec(1.2,1.15,1.2),life=life-4,ground=true,delay=4,facing=yaw+2.1),
                    piece("garden_spray",Vec(0.0,.12,0.0),Vec(r*.9,2.3,r*.9),life=life,ground=true,secondary=true))
            }
            "mage_zero" -> if(e.pulse>0) listOf(floor("ice_pulse",r*.68,8,follow=true).copy(secondary=true)) else {
                val life=(e.skill.pulses-1)*8+16
                // Three unequal broken faces, not six identical towers in a ring.
                // Side and rear placement leaves the owner's forward aim open.
                listOf(floor("zero_floor",r*.68,life,follow=true),
                    piece("zero_crown",local(-r*.52,.12,r*.15),Vec(r*.20,4.2,min(3.5,r*.6)),
                        life=life,facing=yaw+atan2(-.52,.15),ground=true,follow=true),
                    piece("zero_wing",local(r*.45,.12,r*.36),Vec(r*.24,3.2,min(3.2,r*.6)),
                        life=life,facing=yaw+atan2(.45,.36),ground=true,follow=true),
                    piece("zero_shelf",local(-r*.10,.12,-r*.49),Vec(r*.64,.8,min(3.6,r*.6)),
                        life=life,facing=yaw+atan2(-.10,-.49),ground=true,follow=true))
            }
            "mage_burst" -> listOf(floor("rupture",r))+(0 until 4).map { i ->
                val a=i*PI/2+.2
                piece("discharge",Vec(sin(a)*r*.48,.65,cos(a)*r*.48),Vec(1.6,.55,r*.92),facing=a)
            }
            "mage_blink" -> {
                val depart=e.endpoint==CoreSkillEndpoint.DEPARTURE
                val clip=if(depart)"fold_in" else "fold_out"
                listOf(piece(clip,Vec(0.0,1.15,0.0),Vec(1.8,2.0,1.0)),
                    piece("thunder_hit",Vec(0.0,.2,0.0),Vec(2.4,.5,2.4),-PI/2,ground=true,secondary=true))
            }
            "mage_ward" -> listOf(-1,1).flatMap { side ->
                listOf(piece("ward",local(side*.95,1.05,.15),Vec(.9,1.65,1.0),
                    facing=yaw+side*.6,follow=true),
                    piece("ward_mote",local(side*1.1,1.15,.1),Vec(1.2,1.9,1.0),
                        facing=yaw+side*.6,follow=true,secondary=true))
            }
            else -> emptyList()
        }
    }
    fun pose(p:CoreCombatMeshPart,age:Double):CoreMeshPose? {
        if(!owns(p)) return null
        val clip=p.shape.substringAfter(':')
        val local=(age-p.delayTicks).coerceAtLeast(0.0)
        val t=(local/(p.durationTicks-1).coerceAtLeast(1)).coerceIn(0.0,1.0)
        if(interpolated(p)) {
            val front=clip=="meteor_front"
            val fragment=when { clip=="meteor_flow_1" -> 0; clip.startsWith("meteor_break_") -> clip.substringAfterLast('_').toInt(); else -> -1 }
            val role=if(front)-1 else if(fragment>=0)1 else clip.substringAfterLast('_').toInt()
            val openingDelay=when(role) { 0 -> .15; 1 -> .25; 2 -> .5; 3 -> 1.1; else -> .5 }
            val rise=((local-openingDelay)/2.8).coerceIn(0.0,1.0)
            val open=rise*rise*(3-2*rise)
            // Short pressure tongues begin losing energy while still opening;
            // compressing the old long-lobe fade into two ticks made a snap.
            val collapseStart=if(role==0 || role==2).5 else 3.0
            val tail=if(front)(local/(p.durationTicks-1)).coerceIn(0.0,1.0)
                else ((local-collapseStart)/(p.durationTicks-1-collapseStart)).coerceIn(0.0,1.0)
            val fade=1-tail*tail*(3-2*tail)
            // Solid ignition first, fracture second. Never grow a late-dissolve
            // silhouette from tiny seeds after the contact has already passed.
            val scale=if(front)Vec(1+tail*.8,1-tail*.7,1+tail*.8).mul(fade)
                else when(role) {
                    0,2 -> Vec(.7+open*.65,.62+open*.35,.85+open*.2)
                    1 -> Vec(.58+open*.48,.5+open*.5,.65+open*.4)
                    else -> Vec(.6+open*.24,.55+open*.38,.7+open*.1)
                }.mul(fade)
            val offset=if(front)p.offset else p.offset.add(p.bend.mul(1-open)).add(p.travel.mul(open))
                .add(p.travel.x()*tail*.22,tail*(if(fragment>=0).15 else .8),p.travel.z()*tail*.22)
            val state=if(local<4)0 else if(local<6)1 else 2
            // Restrained roll of real volumes, not a spinning image. The low
            // tongues unfold outwards while the lifted pressure rolls inwards.
            val roll=p.roll+when(role) { 0 -> -.14+open*.25; 1 -> 0.0; 2 -> .1-open*.3; 3 -> .18-open*.45; else -> 0.0 }
            var position=offset
            var size=p.scale.mul(scale)
            var tilt=roll
            if(fragment>=0) {
                // All three regions reconstruct the intact body through tick 3.
                // Then their own centres separate: no replacement explosion,
                // no global shrinking crescent pretending to disintegrate.
                fun orient(v:Vec,angle:Double):Vec {
                    val x=v.x()*cos(angle)-v.y()*sin(angle)
                    val y=v.x()*sin(angle)+v.y()*cos(angle)
                    return Vec(x*cos(p.yaw)+v.z()*sin(p.yaw),y,-x*sin(p.yaw)+v.z()*cos(p.yaw))
                }
                val split=((local-3.0)/4.0).coerceIn(0.0,1.0)
                val peel=split*split*(3-2*split)
                val centre=meteorFragmentCenters[fragment].div(16.0).mul(size)
                val drift=listOf(Vec(-.6,.2,-.3),Vec(.2,.8,.25),Vec(.7,.35,-.2))[fragment]
                position=position.add(orient(centre,roll)).add(orient(drift.mul(peel),0.0))
                size=size.mul(1-peel*.6)
                tilt+=listOf(-.2,.48,-.5)[fragment]*peel
            }
            return CoreMeshPose(position,size,p.yaw,p.pitch,tilt,
                "combat_vfx/mage_material/${clip}_$state",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
        }
        // The final rock pose is still visible immediately before the impact
        // event. Its empty terminal asset must not erase the last falling tick.
        val frame=floor(t*(frames(clip)-1)+1e-8).toInt().coerceAtMost(frames(clip)-if(clip=="meteor")2 else 1)
        // Falling anticipation reaches its landing point; everything else moves inside its own contours.
        val u=when(clip) { "meteor" -> t*t; "frost_wave" -> t; else -> 1-(1-t).pow(3) }
        return CoreMeshPose(p.offset.add(p.travel.mul(u)),p.scale,p.yaw,p.pitch,p.roll,
            "combat_vfx/mage_material/${clip}_$frame",age>=p.delayTicks && age<p.delayTicks+p.durationTicks)
    }
}
