package dev.projects.server.warden

import java.io.DataInputStream
import kotlin.math.*

internal data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = V3(x * s, y * s, z * s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(dot(this))
    fun unit() = if (length() > 1e-8) this * (1 / length()) else ZERO
    fun lerp(o: V3, t: Double) = this * (1 - t) + o * t
    fun rotateYaw(a: Double) = V3(x * cos(a) + z * sin(a), y, -x * sin(a) + z * cos(a))
    companion object { val ZERO = V3(0.0, 0.0, 0.0) }
}

internal data class Q4(val x: Double, val y: Double, val z: Double, val w: Double) {
    operator fun times(o: Q4) = Q4(w*o.x+x*o.w+y*o.z-z*o.y, w*o.y-x*o.z+y*o.w+z*o.x,
        w*o.z+x*o.y-y*o.x+z*o.w, w*o.w-x*o.x-y*o.y-z*o.z)
    fun rotate(v: V3): V3 {
        val q = this * Q4(v.x,v.y,v.z,0.0) * Q4(-x,-y,-z,w)
        return V3(q.x,q.y,q.z)
    }
    fun array() = floatArrayOf(x.toFloat(),y.toFloat(),z.toFloat(),w.toFloat())
    fun slerp(other: Q4, t: Double): Q4 {
        var o=other;var d=x*o.x+y*o.y+z*o.z+w*o.w
        if(d<0) {o=Q4(-o.x,-o.y,-o.z,-o.w);d = -d}
        val a:Double;val b:Double
        if(d>.9995) {a=1-t;b=t} else {val angle=acos(d.coerceIn(-1.0,1.0));a=sin((1-t)*angle)/sin(angle);b=sin(t*angle)/sin(angle)}
        val q=Q4(x*a+o.x*b,y*a+o.y*b,z*a+o.z*b,w*a+o.w*b)
        val n=sqrt(q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w)
        return Q4(q.x/n,q.y/n,q.z/n,q.w/n)
    }
    companion object {fun yaw(a:Double)=Q4(0.0,sin(a/2),0.0,cos(a/2))}
}
internal data class BonePose(val p:V3,val q:Q4) {
    fun lerp(o:BonePose,t:Double)=BonePose(p.lerp(o.p,t),q.slerp(o.q,t))
    fun point(local:V3)=p+q.rotate(local)
}
internal data class WardenBone(val name:String,val parent:Int,val visible:Boolean)
/** 26.2 ItemDisplayRenderer appends a Y half-turn after the entity transformation.
 * Cancel it in local model space, preserving the animated bone rotation and locators. */
internal object WardenItemDisplay {
    const val SCALE=2.0
    val rightRotation=Q4.yaw(PI)
}
/** Blend joint-local transforms before FK so changing actions cannot detach the grip. */
internal fun blendBoneHierarchy(bones:List<WardenBone>,from:List<BonePose>,to:List<BonePose>,t:Double):List<BonePose> {
    val result=ArrayList<BonePose>(bones.size)
    fun local(p:BonePose,parent:BonePose):BonePose {
        val inverse=Q4(-parent.q.x,-parent.q.y,-parent.q.z,parent.q.w)
        return BonePose(inverse.rotate(p.p-parent.p),inverse*p.q)
    }
    bones.forEachIndexed {i,b->
        if(b.parent<0)result+=from[i].lerp(to[i],t)
        else {
            val pose=local(from[i],from[b.parent]).lerp(local(to[i],to[b.parent]),t)
            val parent=result[b.parent]
            result+=BonePose(parent.point(pose.p),parent.q*pose.q)
        }
    }
    return result
}
internal data class WardenClip(val name:String,val duration:Int,val loop:Boolean,val activeStart:Int,val activeEnd:Int,val frames:List<List<BonePose>>) {
    fun frame(t:Double):List<BonePose> {
        val f=if(loop) ((t%duration)+duration)%duration else t.coerceIn(0.0,duration.toDouble())
        val i=f.toInt();if(i==duration)return frames[i]
        return frames[i].zip(frames[i+1]) {a,b->a.lerp(b,f-i)}
    }
    fun active(t:Int)=activeStart>=0 && t in activeStart..activeEnd
}
internal class WardenAsset(val bones:List<WardenBone>,val clips:Map<String,WardenClip>) {
    val weapon=bones.indexOfFirst {it.name=="weapon_root"}
    val tip=bones.indexOfFirst {it.name=="weapon_tip"}
    val chest=bones.indexOfFirst {it.name=="vfx_chest"}
    companion object {
        fun load():WardenAsset = DataInputStream(requireNotNull(WardenAsset::class.java.getResourceAsStream("/ashen-warden/warden.bin"))).use {s->
            require(s.readInt()==0x41575231)
            val count=s.readInt();require(count in 20..35)
            val bones=List(count){i->WardenBone(s.readUTF(),s.readInt().also {require(it in -1 until i)},s.readInt()==1)}
            val clips=List(s.readInt().also {require(it in 8..32)}) {
                val name=s.readUTF();val n=s.readInt().also {require(it in 1..1200)};val loop=s.readInt()==1;val start=s.readInt();val end=s.readInt()
                val frames=List(n+1){List(count){
                    val v=DoubleArray(7){s.readFloat().toDouble().also {require(it.isFinite())}}
                    BonePose(V3(v[0],v[1],v[2]),Q4(v[3],v[4],v[5],v[6]))
                }}
                WardenClip(name,n,loop,start,end,frames)
            }.associateBy {it.name}
            require(s.read()==-1);require(bones.map {it.name}.distinct().size==count)
            require(clips.keys.containsAll(listOf("idle","walk","slash_01","heavy_slash","dash","hurt","phase_transition","death")))
            WardenAsset(bones,clips).also {require(it.weapon>=0 && it.tip>=0 && it.chest>=0)}
        }
    }
}

/** Exact squared distance from a segment to an axis-aligned box, including corners.
 * Split at box planes; the squared distance on each interval is quadratic. */
internal fun segmentBoxDistanceSquared(a:V3,b:V3,lo:V3,hi:V3):Double {
    val av=doubleArrayOf(a.x,a.y,a.z);val bv=doubleArrayOf(b.x,b.y,b.z)
    val mn=doubleArrayOf(lo.x,lo.y,lo.z);val mx=doubleArrayOf(hi.x,hi.y,hi.z)
    val d=DoubleArray(3){bv[it]-av[it]};val breaks=mutableListOf(0.0,1.0)
    for(i in 0..2)if(abs(d[i])>1e-12)for(v in listOf(mn[i],mx[i])) {val t=(v-av[i])/d[i];if(t>0 && t<1)breaks+=t}
    breaks.sort()
    fun distance(t:Double)=(0..2).sumOf {i->val p=av[i]+d[i]*t;val gap=p-p.coerceIn(mn[i],mx[i]);gap*gap}
    var best=Double.POSITIVE_INFINITY
    for((l,r)in breaks.zipWithNext()) {
        best=min(best,min(distance(l),distance(r)));val mid=(l+r)/2;var aa=0.0;var bb=0.0
        for(i in 0..2) {val p=av[i]+d[i]*mid;val edge=if(p<mn[i])mn[i] else if(p>mx[i])mx[i] else continue
            aa+=d[i]*d[i];bb+=d[i]*(av[i]-edge)}
        if(aa>1e-16)best=min(best,distance((-bb/aa).coerceIn(l,r)))
    }
    return best
}

/** Same translation + quaternion interpolation as ItemDisplay. Sample spacing <= 0.06m;
 * half a spacing is added to the blade radius to cover between samples. */
internal fun sweptBladeHit(previous:BonePose,current:BonePose,lo:V3,hi:V3):Boolean {
    val dot=abs(previous.q.x*current.q.x+previous.q.y*current.q.y+previous.q.z*current.q.z+previous.q.w*current.q.w).coerceIn(0.0,1.0)
    val travel=(current.p-previous.p).length()+2.65*2*acos(dot)
    val steps=ceil(travel/.06).toInt().coerceIn(1,512)
    val radius=.25+travel/steps/2
    return (0..steps).any {i->val p=previous.lerp(current,i.toDouble()/steps)
        segmentBoxDistanceSquared(p.point(V3(0.0,0.0,.44)),p.point(V3(0.0,0.0,2.64)),lo,hi)<=radius*radius}
}
