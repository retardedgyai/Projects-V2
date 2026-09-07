package dev.projects.server.warden

import java.util.UUID
import kotlin.math.*

/** Bounded solo/multiplayer arena encounter. No account data or economy writes. */
internal class WardenFight(val asset:WardenAsset) {
    var health=360.0;private set
    var phase=1;private set
    var position=V3(0.0,0.0,0.0);private set
    var yaw=0.0;private set
    var action="idle";private set
    var frame=0;private set
    var sequence=0;private set
    var running=false;private set
    var ticks=0L;private set
    private var phasePending=false
    private var nextAttack=30L
    private var attackIndex=0
    private var demonstration=false
    private var noTarget=0
    private var facingWait=0
    private val hit=mutableSetOf<Pair<Int,UUID>>()
    private var transitionFrom:List<BonePose>?=null
    private var transitionAt=0L
    private var transitionPosition=V3.ZERO
    var previousWeapon:BonePose=worldPose()[asset.weapon];private set
    var currentWeapon=previousWeapon;private set
    val clip get()=asset.clips.getValue(action)
    val active get()=running && clip.active(frame)
    val dead get()=action=="death"
    val finished get()=dead && frame>=clip.duration
    val recovery get()=clip.activeStart>=0 && frame>clip.activeEnd
    fun reset() {
        health=360.0;phase=1;position=V3.ZERO;yaw=0.0;running=false;phasePending=false;nextAttack=ticks+30;attackIndex=0;noTarget=0;facingWait=0;demonstration=false
        start("idle",false);currentWeapon=worldPose()[asset.weapon];previousWeapon=currentWeapon
    }
    fun begin(){reset();running=true}
    fun demonstrate(name:String){require(asset.clips.getValue(name).activeStart>=0);begin();demonstration=true;start(name)}
    private fun start(name:String,blend:Boolean=true){
        val from=if(blend)worldPose() else null
        action=name;frame=0;sequence++;hit.clear();facingWait=0
        transitionFrom=from;transitionAt=ticks;transitionPosition=position
    }
    fun damage(amount:Double):Boolean {
        require(amount.isFinite() && amount>=0)
        if(!running || dead || action=="phase_transition")return false
        health=max(0.0,health-amount)
        if(health<=0) {start("death");return true}
        if(phase==1 && health<=180)phasePending=true
        // Hit reaction only outside committed attacks; never allow stun-lock cancellation.
        if(action in listOf("idle","walk"))start("hurt")
        return true
    }
    fun worldPose(ahead:Int=0):List<BonePose> {
        val poses=clip.frame((frame+ahead).toDouble())
        val root=clip.frame(frame.toDouble())[0].p.let {V3(it.x,0.0,it.z)}
        val target=poses.map {
            BonePose(position+(it.p-root).rotateYaw(yaw),Q4.yaw(yaw)*it.q)
        }
        val from=transitionFrom?:return target
        val t=((ticks+ahead-transitionAt)/6.0).coerceIn(0.0,1.0)
        if(t>=1)return target
        val moved=from.map {BonePose(it.p+position-transitionPosition,it.q)}
        return blendBoneHierarchy(asset.bones,moved,target,t*t*(3-2*t))
    }
    fun claimHit(id:UUID):Boolean=active && hit.add(clip.hitWindow(frame) to id)
    fun tick(target:V3?) {
        ticks++;previousWeapon=currentWeapon
        if(!running){frame=(frame+1)%clip.duration;currentWeapon=worldPose()[asset.weapon];return}
        if(dead){frame=min(frame+1,clip.duration);currentWeapon=worldPose()[asset.weapon];return}
        if(target==null)noTarget++ else noTarget=0
        if(noTarget>100 || position.length()>15){reset();return}
        if(action !in listOf("idle","walk")) {
            // Reacquire between swings, then visibly commit before contact. Never steer an active blade.
            val upcoming=clip.windows.firstOrNull {frame<it.first}
            val preceding=clip.windows.lastOrNull {it.last<frame}
            if(!demonstration && target!=null && upcoming!=null && frame<upcoming.first-4 && (preceding==null || frame>=preceding.last+3)) {
                val delta=target-position;val desired=atan2(delta.x,delta.z)
                yaw+=atan2(sin(desired-yaw),cos(desired-yaw)).coerceIn(-.18,.18)
            }
            val before=clip.frame(frame.toDouble())[0].p
            val after=clip.frame((frame+1).toDouble())[0].p
            position+=V3(after.x-before.x,0.0,after.z-before.z).rotateYaw(yaw)
            frame++
            if(frame>=clip.duration){if(action=="phase_transition")phase=2;if(demonstration)running=false;start("idle");nextAttack=ticks+if(phase==2)2 else 5}
        } else {
            if(phasePending){phasePending=false;start("phase_transition")}
            else if(target!=null) {
                val delta=target-position;val distance=hypot(delta.x,delta.z)
                val desired=atan2(delta.x,delta.z);val turn=atan2(sin(desired-yaw),cos(desired-yaw));yaw+=turn.coerceIn(-.16,.16)
                val remaining=atan2(sin(desired-yaw),cos(desired-yaw))
                if(ticks>=nextAttack && distance<6.0)facingWait++ else facingWait=0
                if(ticks>=nextAttack && distance<6.0 && (abs(remaining)<.35 || facingWait>=8)) {
                    val choices=if(distance>3.8)listOf("dash","vault_slam","dash","vault_slam")
                        else if(phase==2)listOf("onslaught","rush_combo","spiral_combo","onslaught","spin_slash")
                        else listOf("slash_01","rush_combo","spin_slash","rush_combo","heavy_slash")
                    val selected=if(abs(remaining)>=.35)if(attackIndex%2==0)"spin_slash" else if(phase==2)"onslaught" else "rush_combo"
                        else choices[attackIndex%choices.size]
                    attackIndex++
                    start(selected)
                } else if(distance>2.35) {
                    if(action!="walk")start("walk")
                    position+=V3(0.0,0.0,.04).rotateYaw(yaw);frame=(frame+1)%clip.duration
                } else {if(action!="idle")start("idle");frame=(frame+1)%clip.duration}
            } else {if(action!="idle")start("idle");frame=(frame+1)%clip.duration}
        }
        currentWeapon=worldPose()[asset.weapon]
        // A transition from an unrelated clip is never a damaging sweep.
        if(frame==0)previousWeapon=currentWeapon
    }
}
