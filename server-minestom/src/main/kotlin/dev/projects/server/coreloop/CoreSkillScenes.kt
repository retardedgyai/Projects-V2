package dev.projects.server.coreloop

import net.minestom.server.coordinate.Vec
import kotlin.math.*

internal enum class CoreSceneKind { CUT, CLEAVE, SPIN, GUARD, SHIELD, THRUST, BANNER, RAY, NOVA, RAIN, TELEPORT, FIELD, FAN, AFTERIMAGE, HAMMER, PULL, HEAL, PILLAR }
internal enum class CoreSkillEndpoint { NONE, DEPARTURE, ARRIVAL }

/** Concrete authored contract for every existing skill. Shared with the mesh builder and art sheet. */
internal data class CoreSkillScene(val id: String, val name: String, val kind: CoreSceneKind, val palette: String,
    val body: String, val accent: String, val windup: String, val impact: String,
    val reach: Double, val width: Double, val height: Double, val intent: String)

internal object CoreSkillScenes {
    val all: Map<String, CoreSkillScene> = CoreSkillScenes::class.java.getResourceAsStream("/combat-art/skill-scenes.psv")!!
        .bufferedReader(Charsets.UTF_8).use { reader -> reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val c = line.split('|'); require(c.size == 12) { line }
            CoreSkillScene(c[0], c[1], CoreSceneKind.valueOf(c[2]), c[3], c[4], c[5], c[6], c[7], c[8].toDouble(), c[9].toDouble(), c[10].toDouble(), c[11])
        }.also { rows -> require(rows.map { it.id }.distinct().size == rows.size) }.associateBy { it.id } }
    fun get(id: String) = all.getValue(id)
}

/** XZ is the authored drawing plane, +Z points forward. Standing art rotates -90° about X. */
internal data class CoreCombatMeshPart(
    val shape: String, val palette: String, val offset: Vec = Vec.ZERO,
    val scale: Vec = Vec(1.0, 1.0, 1.0), val yaw: Double = 0.0, val pitch: Double = 0.0, val roll: Double = 0.0,
    val spin: Double = 0.0, val travel: Vec = Vec.ZERO, val secondary: Boolean = false,
    val startSize: Double = 1.0, val endSize: Double = .65, val durationTicks: Int = 6,
    val followOwner: Boolean = false, val ground: Boolean = false,
) {
    fun sizeAt(t: Double) = startSize + (endSize-startSize) * t.coerceIn(0.0,1.0)
}

internal object CoreCombatMeshArt {
    // Verified in Vanilla 26.2 DisplayRenderer.ItemDisplayRenderer.submitInner (Y rotation PI).
    // Right rotation cancels it BEFORE our scale and authored orientation. No client patch.
    val vanillaItemCorrection get() = floatArrayOf(0f, 1f, 0f, 0f)

    fun parts(e: CoreSkillEffect): List<CoreCombatMeshPart> {
        if (!e.valid) return emptyList()
        val s = CoreSkillScenes.get(e.sceneId)
        val r = min(s.reach, e.radius.coerceAtLeast(.5))
        val yaw = atan2(e.direction.x(), e.direction.z())
        val pitch = -atan2(e.direction.y(), hypot(e.direction.x(), e.direction.z()))
        fun local(x: Double, y: Double, z: Double) = Vec(cos(yaw)*x+sin(yaw)*z, y, -sin(yaw)*x+cos(yaw)*z)
        fun part(shape: String = s.body, offset: Vec = Vec.ZERO, scale: Vec = Vec(1.0,1.0,1.0),
                 face: Double = yaw, tilt: Double = 0.0, roll: Double = 0.0, spin: Double = 0.0,
                 travel: Vec = Vec.ZERO, secondary: Boolean = false, start: Double = 1.0, end: Double = .65,
                 ticks: Int = e.durationTicks, follow: Boolean = false, ground: Boolean = false) =
            CoreCombatMeshPart(shape,s.palette,offset,scale,face,tilt,roll,spin,travel,secondary,start,end,ticks,follow,ground)
        fun floor(shape: String, radius: Double, start: Double = .8, end: Double = 1.0, secondary: Boolean = true) =
            part(shape, Vec(0.0,.12,0.0), Vec(radius*2,1.0,radius*2), secondary=secondary,start=start,end=end,ground=true)
        val ray = e.clippedRay || e.skill.motion == CoreSkillMotion.RAY
        if (e.phase == CoreSkillVisualPhase.CONTACT) {
            // Only authoritative accepted hits produce an impact, never an empty ray endpoint.
            return listOf(part(s.impact, Vec(0.0,1.0,0.0), Vec(1.0,1.0,1.0), tilt=-PI/2,start=.6,end=1.0,ticks=4))
        }
        fun rainPoint(i: Int, count: Int): Vec {
            val a = i*PI*2/count + e.pulse*2.39996
            val radial = min(e.radius*.35, 2.4) * if (count==1 && e.pulse==0) 0.0 else 1.0
            return Vec(cos(a)*radial,0.0,sin(a)*radial)
        }
        if (e.phase == CoreSkillVisualPhase.PREPARE) {
            if (s.kind == CoreSceneKind.RAIN) {
                val count = if (s.palette == "hunter") 4 else 1
                val falling = (0 until count).map { i -> part(offset=rainPoint(i,count).add(0.0,4.3,0.0),
                    scale=Vec(s.reach,s.reach,s.reach*1.6),tilt=if(s.palette=="hunter") PI/2 else 0.0,
                    travel=Vec(0.0,s.reach*.6+.15-4.3,0.0),start=.75,end=1.0) }
                return falling + floor(s.windup,min(e.radius,if(s.windup in setOf("fire_seal","constellation","aim_bracket")) 2.5 else .8),.7,1.0)
            }
            val field = s.kind in setOf(CoreSceneKind.FIELD,CoreSceneKind.PILLAR)
            return listOf(part(s.windup, if(field) Vec(0.0,.15,0.0) else local(.38,1.2,.55),
                Vec(.65,.65,.65),tilt=if(field) 0.0 else -PI/2,spin=.3,start=.35,end=1.0,ground=field))
        }
        if (ray) {
            if (e.length <= .05) return emptyList()
            val direction = if(e.direction.lengthSquared()>1e-8) e.direction.normalize() else Vec(0.0,0.0,1.0)
            val bodyLength = min(s.reach, e.length)
            val head = part(offset=direction.mul(e.length-bodyLength*.5), scale=Vec(s.width,s.height,bodyLength),
                tilt=pitch,start=1.0,end=.65)
            val trailLength = if(s.body=="fire_orb") min(e.length,2.0) else e.length
            val trail = part(s.accent,direction.mul(e.length-trailLength*.5),Vec(.42,.42,trailLength),tilt=pitch,
                secondary=true,start=1.0,end=1.0)
            return listOf(head,trail)
        }
        return when(s.kind) {
            CoreSceneKind.CUT -> {
                val reverse = (if (s.body=="slash_reverse") 1 else 0) + e.pulse
                val sign = if(reverse%2==0) 1.0 else -1.0
                val cross=s.body=="cross_cut"
                val forwardPlaced=cross || s.body=="poison_fang"
                listOf(part(offset=if(forwardPlaced) local(0.0,1.05,r*.75) else Vec(0.0,1.05,0.0),
                    scale=if(cross) Vec(r*.95,.9,r*.95) else if(forwardPlaced) Vec(r*1.2,.9,r) else Vec(r*2,.9,r*2),
                    face=yaw-sign*.16,tilt=if(cross) -PI/2 else 0.0,
                    roll=sign*.12,spin=sign*.32,start=1.0,end=.8),
                    part(s.accent,local(sign*.3,1.0,r*.6),Vec(.55,.55,.9),tilt=-.12,secondary=true))
            }
            CoreSceneKind.CLEAVE -> listOf(
                // Roll makes the XZ drawing into a YZ blade while preserving its +Z forward tip.
                part(offset=Vec(0.0,1.25,0.0),scale=Vec(2.25,.65,r*2),roll=PI/2,
                    travel=Vec(0.0,-.15,0.0),start=1.0,end=.9),
                part(s.accent,local(0.0,.12,r*.5),Vec(r,1.0,r),secondary=true,ground=true,start=.7,end=1.0))
            CoreSceneKind.SPIN -> {
                val sign=if(e.pulse%2==0) 1.0 else -1.0
                listOf(part(offset=Vec(0.0,1.0,0.0),scale=Vec(r*2,.7,r*2),roll=sign*.1,spin=sign*PI*2,start=1.0,end=.9),
                    part(s.accent,Vec(0.0,.9,0.0),Vec(r,.5,r),spin=sign*PI*2,secondary=true,start=.8,end=.5))
            }
            CoreSceneKind.THRUST -> listOf(part(offset=local(0.0,1.05,r*.5),scale=Vec(s.width,s.height,r),
                tilt=if(s.body=="shield_bash") -PI/2 else 0.0,start=1.0,end=.8),
                part(s.accent,local(.3,.9,r*.4),Vec(.6,.6,r*.6),secondary=true))
            CoreSceneKind.HAMMER -> listOf(part(offset=local(0.0,1.05,r*.6),scale=Vec(1.5,1.0,1.7),tilt=-PI/2,
                travel=Vec(0.0,-.15,0.0),start=1.0,end=.85),
                part(s.accent,local(0.0,.13,r*.6),Vec(r,1.0,r),secondary=true,ground=true,start=.5,end=1.0))
            CoreSceneKind.TELEPORT -> {
                val depart=e.endpoint==CoreSkillEndpoint.DEPARTURE
                listOf(part(offset=Vec(0.0,1.1,0.0),scale=Vec(1.6,1.0,2.15),tilt=-PI/2,
                    start=if(depart) 1.0 else .15,end=if(depart) .12 else 1.0,ticks=8),
                    part(s.accent,Vec(.45,.8,0.0),Vec(.5,.5,.8),tilt=-PI/2,
                        travel=Vec(0.0,.45,0.0),secondary=true,start=.7,end=.1,ticks=8))
            }
            CoreSceneKind.GUARD -> listOf(part(offset=local(0.0,1.05,.72),scale=Vec(r*1.7,1.0,1.8),tilt=-PI/2,
                start=1.0,end=1.0,ticks=e.skill.duration.coerceIn(1,100),follow=true),
                part(s.accent,local(.2,1.3,.75),Vec(.3,.3,.3),tilt=-PI/2,secondary=true,follow=true))
            CoreSceneKind.AFTERIMAGE -> listOf(part(offset=local(.35,1.0,-.35),scale=Vec(1.0,1.0,2.0),tilt=-PI/2,
                travel=local(.15,0.0,-.3),start=1.0,end=.5,ticks=e.skill.duration.coerceIn(1,60)),
                part(s.accent,local(-.25,.8,-.5),Vec(.6,.6,1.0),tilt=-PI/2,travel=local(0.0,.2,-.25),secondary=true))
            CoreSceneKind.SHIELD -> {
                val star=s.palette=="astral"
                val body=part(offset=local(0.0,if(s.body=="constellation_crown") 1.95 else 1.05,.65),
                    scale=Vec(min(r*1.6,2.4),1.0,if(s.body=="constellation_crown") 1.0 else 2.0),tilt=-PI/2,
                    start=.65,end=1.0,ticks=9,follow=true)
                if(star) listOf(body) + listOf(-1,1).map { side ->
                    part(s.accent,local(side*.65,1.2,.1),Vec(1.0,1.0,1.25),face=yaw+side*.6,tilt=-PI/2,
                        secondary=true,start=.7,end=1.0,ticks=9,follow=true)
                } else listOf(body,floor(s.accent,min(e.radius,3.2),.45,1.0))
            }
            CoreSceneKind.BANNER -> listOf(part(offset=local(-.6,1.1,.45),scale=Vec(1.7,1.0,2.3),tilt=-PI/2,
                start=1.0,end=1.0,ticks=40),floor(s.accent,min(e.radius,3.2),.5,1.0))
            CoreSceneKind.PULL -> (0..3).map { i ->
                val a=yaw+i*PI/2
                val at=Vec(sin(a)*r,.6,cos(a)*r)
                part(offset=at,scale=Vec(.8,.8,1.0),face=a+PI,travel=Vec(-at.x()*.8,0.0,-at.z()*.8),start=1.0,end=.5)
            } + floor(s.accent,r,1.0,.2)
            CoreSceneKind.NOVA -> listOf(floor(s.body,r,.3,1.0,secondary=false)) + (0..3).map { i ->
                val a=i*PI/2+e.pulse*.4
                val ground=s.accent.endsWith("crack")
                part(s.accent,Vec(sin(a)*r*.75,if(ground) .12 else .55,cos(a)*r*.75),Vec(.55,.55,.85),face=a,tilt=if(ground) 0.0 else -PI/2,
                    travel=Vec(0.0,if(ground) 0.0 else .2,0.0),secondary=true,start=.4,end=1.0,ground=ground)
            }
            CoreSceneKind.FIELD -> {
                val body=floor(s.body,r,.85,1.0,secondary=false)
                listOf(body) + (0..3).map { i ->
                    val a=i*PI/2+.5
                    part(s.accent,Vec(sin(a)*r*.65,.45,cos(a)*r*.65),Vec(.65,.65,.85),face=a,
                        tilt=if(s.body=="nebula_wisp") 0.0 else -PI/2,secondary=true,start=.6,end=1.0)
                }
            }
            CoreSceneKind.FAN -> (-2..2).flatMap { i ->
                val a=yaw+i*.4
                // Five arrowheads; only the two outer trails mark the fan boundary (seven displays total).
                listOf(part(offset=Vec(sin(a)*r*.7,1.0,cos(a)*r*.7),scale=Vec(s.width,s.height,1.0),face=a,
                    start=1.0,end=.8,secondary=i%2!=0)) + if(abs(i)==2)
                    listOf(part(s.accent,Vec(sin(a)*r*.4,1.0,cos(a)*r*.4),Vec(.3,.3,r*.6),face=a,
                        secondary=true,start=1.0,end=.6)) else emptyList()
            }
            CoreSceneKind.RAIN -> {
                val count=if(s.palette=="hunter") 4 else 1
                (0 until count).map { i -> part(offset=rainPoint(i,count).add(0.0,s.reach*.6+.15,0.0),
                    scale=Vec(s.reach,s.reach,s.reach*1.2),tilt=if(s.palette=="hunter") PI/2 else 0.0,
                    start=1.0,end=.15) } + floor(s.accent,min(e.radius,3.6),.4,1.0)
            }
            CoreSceneKind.PILLAR -> when(s.body) {
                "lantern" -> listOf(part(offset=Vec(0.0,2.2,0.0),scale=Vec(.9,.9,1.0),tilt=-PI/2,start=1.0,end=1.0),
                    part(s.accent,Vec(0.0,1.0,0.0),Vec(.7,.7,2.0),tilt=-PI/2,secondary=true,start=.8,end=1.0))
                "judgment_sword" -> listOf(part(offset=Vec(0.0,1.8,0.0),scale=Vec(s.reach,1.0,3.0),tilt=PI/2,
                    travel=Vec(0.0,-.3,0.0),start=1.0,end=1.0),floor(s.accent,min(e.radius,2.8),.8,1.0))
                else -> listOf(part(offset=Vec(0.0,1.5,0.0),scale=Vec(s.reach,1.0,3.0),tilt=-PI/2,
                    start=.8,end=1.0),floor(s.accent,min(e.radius,2.8),.8,1.0))
            }
            CoreSceneKind.HEAL -> listOf(floor(s.accent,r,.4,1.0)) + (0..3).map { i ->
                val a=i*PI/2
                part(offset=Vec(sin(a)*r*.6,.45,cos(a)*r*.6),scale=Vec(.8,.8,1.0),face=a,tilt=-PI/2,
                    travel=Vec(0.0,.8,0.0),secondary=i>1,start=.5,end=1.0)
            }
            CoreSceneKind.RAY -> emptyList()
        }
    }

    fun rotation(yaw: Double, pitch: Double, roll: Double): FloatArray {
        fun mul(a: DoubleArray,b: DoubleArray)=doubleArrayOf(a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
            a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
            a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2])
        return mul(mul(doubleArrayOf(0.0,sin(yaw/2),0.0,cos(yaw/2)),doubleArrayOf(0.0,0.0,sin(roll/2),cos(roll/2))),
            doubleArrayOf(sin(pitch/2),0.0,0.0,cos(pitch/2))).map { it.toFloat() }.toFloatArray()
    }
}
