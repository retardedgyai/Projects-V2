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
    private var noTarget=0
    private val hit=mutableSetOf<UUID>()
    var previousWeapon:BonePose=worldPose()[asset.weapon];private set
    var currentWeapon=previousWeapon;private set
    val clip get()=asset.clips.getValue(action)
    val active get()=running && clip.active(frame)
    val dead get()=action=="death"
    val finished get()=dead && frame>=clip.duration
    val recovery get()=action in listOf("slash_01","heavy_slash","dash") && frame>clip.activeEnd
    fun reset() {
        health=360.0;phase=1;position=V3.ZERO;yaw=0.0;running=false;phasePending=false;nextAttack=ticks+30;attackIndex=0;noTarget=0
        start("idle");currentWeapon=worldPose()[asset.weapon];previousWeapon=currentWeapon
    }
    fun begin(){reset();running=true}
    private fun start(name:String){action=name;frame=0;sequence++;hit.clear()}
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
    fun worldPose(ahead:Int=0):List<BonePose> = clip.frame((frame+ahead).toDouble()).map {
        BonePose(position+it.p.rotateYaw(yaw),Q4.yaw(yaw)*it.q)
    }
    fun claimHit(id:UUID):Boolean=active && hit.add(id)
    fun tick(target:V3?) {
        ticks++;previousWeapon=currentWeapon
        if(!running){frame=(frame+1)%clip.duration;currentWeapon=worldPose()[asset.weapon];return}
        if(dead){frame=min(frame+1,clip.duration);currentWeapon=worldPose()[asset.weapon];return}
        if(target==null)noTarget++ else noTarget=0
        if(noTarget>100 || position.length()>15){reset();return}
        if(action !in listOf("idle","walk")) {
            if(action=="dash" && frame in 13..20)position+=V3(0.0,0.0,.34).rotateYaw(yaw)
            frame++
            if(frame>=clip.duration){if(action=="phase_transition")phase=2;start("idle");nextAttack=ticks+if(phase==2)10 else 18}
        } else {
            if(phasePending){phasePending=false;start("phase_transition")}
            else if(target!=null) {
                val delta=target-position;val distance=hypot(delta.x,delta.z)
                val desired=atan2(delta.x,delta.z);val turn=atan2(sin(desired-yaw),cos(desired-yaw));yaw+=turn.coerceIn(-.06,.06)
                if(ticks>=nextAttack && distance<7 && abs(turn)<.20) {
                    val selected=if(distance>3.9)"dash" else if(attackIndex++%3==2)"heavy_slash" else "slash_01"
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
