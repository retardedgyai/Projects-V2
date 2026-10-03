package dev.projects.modellab

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.worldseed.multipart.GenericModelImpl
import net.worldseed.multipart.ModelLoader
import net.worldseed.multipart.animations.AnimationHandlerImpl
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.*

/** Isolated straight/flat locomotion owner. No target AI, damage, FX, turning or production wiring. */
class DragonActionController(private val model: GenericModelImpl, private val handler: AnimationHandlerImpl,
                            modelId: String, val scale: Float, private val mover: Consumer<Pos>,
                            private val reader: Supplier<Pos>) {
    enum class State { READY, RESTART, WALK, STOP, CLAW, BREATH }
    enum class Action { CLAW, BREATH }
    companion object {
        const val WALK = "walk_grounded_stride_receive_hind_push_native_rigid"
        const val STOP = "transition_walk26_phase070_stop_to_foreclaw_ready_native_rigid"
        const val STOP_ALTERNATE = "transition_walk26_phase020_stop_to_ready_native_rigid"
        const val CLAW = "attack_foreclaw_hook_transverse_rake_native_rigid"
        const val BREATH = "attack_ground_breath_aimed_native_rigid"
        const val RESTART = "transition_ready_moving_first_step_to_approved_walk26_phase000_native_rigid"
        const val RESTART_SECONDS = 2.10
        const val RESTART_ACCEL_SECONDS = 1.35
        const val RESTART_TRAVEL_BB = 11.40
        const val RESTART_ENTRY = .0
        const val ACCEL_SECONDS = .60
        const val CYCLE = 3.2
        const val STRIDE_BB = 25.6
        const val ENTRY = .70
        const val STOP_SECONDS = 2.30
        const val DECEL_SECONDS = .5345875000000002
        const val STOP_TRAVEL_BB = 2.138350000000001
    }
    data class Transition(val elapsed: Double, val from: State, val to: State,
                          val fromClip: String, val toClip: String, val fromSeconds: Double,
                          val toSeconds: Double, val all34LocalPoseJumpBB: Double)
    data class Snapshot(val state: State, val clip: String, val sourceSeconds: Double,
                        val walkCycles: Double, val acceptedWalkingBlocks: Double, val actor: Pos,
                        val nominalSpeed: Double, val pending: Action?, val decision: String,
                        val stopRequested: Boolean, val stopRate: Double, val accelerationTime: Double)
    val strideBlocks = STRIDE_BB*scale/16.0
    val nominalSpeed = strideBlocks/CYCLE
    private val players = linkedMapOf<String,DragonNumericClip>()
    private var selected: DragonNumericClip
    var state = State.READY
        private set
    private var cycles=0.0
    private var acceptedWalking=0.0
    private var pending: Action?=null
    private var elapsed=0.0
    private var stopRequested=false
    private var stopRate=1.0
    private var accelerationTime=ACCEL_SECONDS
    private var stageTime=0.0
    private var stopStart=reader.get()
    private var restartStart=reader.get()
    private var decision="READY_HOLD"
    val transitions = mutableListOf<Transition>()
    init {
        require(scale.isFinite() && scale>0)
        val clips=requireNotNull(ModelLoader.loadAnimations(modelId)).getAsJsonObject("animations")
        for (name in listOf(WALK,STOP,STOP_ALTERNATE,CLAW,BREATH,RESTART)) {
            val p=DragonNumericClip(model,name,requireNotNull(clips.getAsJsonObject(name)),requireNotNull(handler.getAnimation(name)).priority())
            players[name]=p;handler.registerAnimation(p)
        }
        require(abs(players.getValue(WALK).lengthSeconds-CYCLE)<1e-9)
        require(abs(players.getValue(STOP).lengthSeconds-STOP_SECONDS)<1e-9)
        require(abs(players.getValue(STOP_ALTERNATE).lengthSeconds-STOP_SECONDS)<1e-9)
        require(abs(players.getValue(RESTART).lengthSeconds-RESTART_SECONDS)<1e-9)
        handler.repeating?.let(handler::stopRepeat)
        selected=players.getValue(CLAW);selected.seek(0.0);handler.playRepeat(selected.name());model.draw()
    }
    fun snapshot()=Snapshot(state,selected.sourceName,selected.sourceSeconds,cycles,acceptedWalking,reader.get(),nominalSpeed,pending,decision,stopRequested,stopRate,accelerationTime)
    // Test helper: seed existing walk; normal READY startup uses moving first-step clip.
    fun seedAlreadyWalkingPhase(phase: Double) {
        require(state==State.READY && phase.isFinite() && phase in 0.0..1.0)
        cycles=phase%1.0;acceptedWalking=0.0;selected=players.getValue(WALK);selected.seek((cycles%1.0)*CYCLE)
        handler.playRepeat(selected.name());state=State.WALK;model.draw();decision="SEEDED_ALREADY_WALKING"
    }
    fun requestAction(action: Action) {
        check(!stopRequested && pending==null && (state==State.WALK || state==State.READY)) { "Action already committed; no implicit interrupt" }
        pending=action
        if (state==State.READY) beginAction() else { stopRequested=true;decision="WAIT_PHASE070_AT_ACCEPTED_SPEED" }
    }
    fun requestStop() {
        check(state==State.WALK && !stopRequested && pending==null) { "Stop already committed or not walking" }
        stopRequested=true;decision="WAIT_PHASE070_AT_ACCEPTED_SPEED"
    }
    fun requestWalk() {
        check(state==State.READY && !stopRequested && pending==null) { "Only settled READY can restart; no implicit action interrupt" }
        stageTime=0.0;restartStart=reader.get();select(State.RESTART,RESTART,0.0);decision="READY_MOVING_FIRST_STEP_TO_APPROVED_WALK26"
    }
    private fun localPose(): Map<String,List<Double>> = model.parts.associate { part ->
        part.name to listOf(Vec.ZERO,Vec(.25,0.0,0.0),Vec(0.0,.25,0.0),Vec(0.0,0.0,.25)).flatMap { v ->
            val p=part.applyTransform(v);listOf(p.x()*4,p.y()*4,p.z()*4)
        }
    }
    private fun select(next: State, clip: String, time: Double) {
        val before=localPose();val oldState=state;val oldClip=selected.sourceName;val oldTime=selected.sourceSeconds
        selected=players.getValue(clip);selected.seek(time);handler.playRepeat(selected.name());state=next;model.draw()
        val after=localPose();val jump=before.keys.maxOf { name -> before.getValue(name).zip(after.getValue(name)).maxOf { (a,b)->abs(a-b) } }
        transitions.add(Transition(elapsed,oldState,next,oldClip,clip,oldTime,time,jump))
    }
    private fun beginAction() {
        val a=requireNotNull(pending);pending=null;stopRequested=false;stageTime=0.0
        select(if(a==Action.CLAW)State.CLAW else State.BREATH,if(a==Action.CLAW)CLAW else BREATH,0.0);decision="COMMITTED_ACTION"
    }
    private fun advanceWalking(blocks: Double): Double {
        val start=reader.get();val yaw=Math.toRadians(start.yaw().toDouble())
        val to=Pos(start.x()-sin(yaw)*blocks,start.y(),start.z()+cos(yaw)*blocks,start.yaw(),start.pitch())
        mover.accept(to);val actual=reader.get();val dx=actual.x()-start.x();val dz=actual.z()-start.z()
        val accepted=-sin(yaw)*dx+cos(yaw)*dz;val lateral=cos(yaw)*dx+sin(yaw)*dz
        check(abs(lateral)<1e-8 && abs(actual.y()-start.y())<1e-8 && abs(actual.yaw()-start.yaw())<1e-8 && accepted>=-1e-8 && accepted<=blocks+1e-8) { "Unsupported non-straight accepted locomotion" }
        acceptedWalking+=accepted;cycles+=accepted/strideBlocks
        val q=cycles%1.0;selected.seek((if(q<1e-12 || q>1-1e-12)0.0 else q)*CYCLE);model.draw()
        return accepted
    }
    private fun stopTravel(seconds: Double)=if(seconds<DECEL_SECONDS)8*seconds-4*seconds*seconds/DECEL_SECONDS else STOP_TRAVEL_BB
    private fun accelerationIntegral(t: Double)=if(t<ACCEL_SECONDS)t*t/(2*ACCEL_SECONDS) else t-ACCEL_SECONDS/2
    private fun restartTravel(t: Double)=8*(if(t<RESTART_ACCEL_SECONDS)t*t/(2*RESTART_ACCEL_SECONDS) else t-RESTART_ACCEL_SECONDS/2)
    private fun restartInverse(travelBB: Double): Double {
        val d=travelBB.coerceIn(0.0,RESTART_TRAVEL_BB)
        return if(d<4*RESTART_ACCEL_SECONDS)sqrt(d*RESTART_ACCEL_SECONDS/4) else d/8+RESTART_ACCEL_SECONDS/2
    }
    private fun stopInverse(travelBB: Double)=DECEL_SECONDS*(1-sqrt((1-travelBB/STOP_TRAVEL_BB).coerceIn(0.0,1.0)))
    private fun finishStop() {
        stopRequested=false
        if(pending!=null)beginAction()
        else {select(State.READY,CLAW,0.0);stageTime=0.0;decision="READY_HOLD"}
    }
    fun step(dt: Double, commandedForwardSpeed: Double) {
        require(dt.isFinite() && dt>0 && dt<=.25 && commandedForwardSpeed.isFinite() && commandedForwardSpeed>=0 && commandedForwardSpeed<=nominalSpeed*4)
        var remaining=dt;var turns=0
        while(remaining>1e-10) {
            check(++turns<=8)
            when(state) {
                State.RESTART -> {
                    if(commandedForwardSpeed<=1e-10) {
                        elapsed+=remaining;remaining=0.0;decision="ZERO_SPEED_RESTART_SOURCE_AND_CONTACTS_HELD"
                        continue
                    }
                    val rate=commandedForwardSpeed/nominalSpeed
                    val n=min(remaining,(RESTART_SECONDS-stageTime)/rate)
                    val wanted=(stageTime+n*rate).coerceAtMost(RESTART_SECONDS)
                    val yaw=Math.toRadians(restartStart.yaw().toDouble())
                    val travel=restartTravel(wanted)*scale/16.0
                    val to=Pos(restartStart.x()-sin(yaw)*travel,restartStart.y(),restartStart.z()+cos(yaw)*travel,restartStart.yaw(),restartStart.pitch())
                    val before=reader.get();mover.accept(to);val actual=reader.get()
                    val dx=actual.x()-restartStart.x();val dz=actual.z()-restartStart.z()
                    val along=-sin(yaw)*dx+cos(yaw)*dz;val lateral=cos(yaw)*dx+sin(yaw)*dz
                    val oldTravel=restartTravel(stageTime)*scale/16.0
                    check(abs(lateral)<1e-8 && abs(actual.y()-restartStart.y())<1e-8 && abs(actual.yaw()-restartStart.yaw())<1e-8 && along>=oldTravel-1e-8 && along<=travel+1e-8) {"Unsupported sideways/backwards/vertical accepted restart movement"}
                    if(actual.distance(to)<1e-8) {stageTime=wanted;decision="MOVING_FIRST_STEP_PROFILE"}
                    else {
                        stageTime=restartInverse(along*16.0/scale).coerceAtLeast(stageTime).coerceAtMost(wanted)
                        decision=if(actual.distance(before)<1e-8)"BLOCKED_RESTART_SOURCE_AND_FOOT_CONTACTS_HELD" else "PARTIAL_RESTART_FROM_ACCEPTED_DISPLACEMENT"
                    }
                    elapsed+=n;remaining-=n;selected.seek(stageTime);model.draw()
                    if(stageTime>=RESTART_SECONDS-1e-10) {
                        // A deliberate startup creates the first stance; rebase only its
                        // cycle origin. Thereafter unchanged walk phase follows distance.
                        cycles=ceil(cycles-1e-10)+RESTART_ENTRY
                        select(State.WALK,WALK,RESTART_ENTRY*CYCLE)
                        accelerationTime=ACCEL_SECONDS;stageTime=0.0;decision="APPROVED_WALK26_DISTANCE_PHASE_AFTER_MOVING_START"
                    }
                }
                State.WALK -> {
                    if(commandedForwardSpeed>1e-10 && accelerationTime<ACCEL_SECONDS-1e-10) {
                        val n=min(remaining,ACCEL_SECONDS-accelerationTime)
                        val requested=commandedForwardSpeed*(accelerationIntegral(accelerationTime+n)-accelerationIntegral(accelerationTime))
                        val accepted=advanceWalking(requested)
                        if(abs(accepted-requested)<1e-8)accelerationTime+=n
                        else if(accepted>1e-10) {
                            accelerationTime=sqrt(accelerationTime*accelerationTime+2*ACCEL_SECONDS*accepted/commandedForwardSpeed).coerceAtMost(ACCEL_SECONDS)
                        }
                        elapsed+=n;remaining-=n;decision=if(accepted<requested-1e-8)"BLOCKED_RESTART_DISTANCE_PHASE_HELD" else "APPROVED_WALK26_DISTANCE_RAMP"
                        continue
                    }
                    val phase=cycles%1.0
                    val gate=listOf(.20,ENTRY).map { candidate ->
                        var distance=(candidate-phase+1.0)%1.0
                        if(distance<1e-10 || distance>1-1e-10)distance=0.0
                        candidate to distance
                    }.minBy { it.second }
                    val entry=gate.first;val toEntry=gate.second
                    val moving=commandedForwardSpeed>1e-10
                    val wait=if(moving)toEntry*strideBlocks/commandedForwardSpeed else Double.POSITIVE_INFINITY
                    if(stopRequested && moving && wait<=remaining+1e-10) {
                        val requested=toEntry*strideBlocks;val accepted=advanceWalking(requested);elapsed+=wait;remaining=(remaining-wait).coerceAtLeast(0.0)
                        if(abs(accepted-requested)>1e-8) {decision="BLOCKED_WALK_PHASE_HELD";elapsed+=remaining;remaining=0.0}
                        else {selected.seek(entry*CYCLE);stageTime=0.0;stopStart=reader.get();stopRate=commandedForwardSpeed/nominalSpeed;select(State.STOP,if(entry==ENTRY)STOP else STOP_ALTERNATE,0.0);decision="CONTACT_GATE_STOP_DISTANCE_PRESERVED_SPEED_WARP"}
                    } else {
                        advanceWalking(commandedForwardSpeed*remaining);elapsed+=remaining;remaining=0.0
                        decision=if(stopRequested && !moving)"WAIT_ACCEPTED_MOTION_TO_CONTACT_GATE" else "DISTANCE_PHASE"
                    }
                }
                State.STOP -> {
                    val n=min(remaining,(STOP_SECONDS-stageTime)/stopRate)
                    val wanted=(stageTime+n*stopRate).coerceAtMost(STOP_SECONDS)
                    val yaw=Math.toRadians(stopStart.yaw().toDouble());val travel=stopTravel(wanted)*scale/16.0
                    val to=Pos(stopStart.x()-sin(yaw)*travel,stopStart.y(),stopStart.z()+cos(yaw)*travel,stopStart.yaw(),stopStart.pitch())
                    val before=reader.get();mover.accept(to);val actual=reader.get()
                    val dx=actual.x()-stopStart.x();val dz=actual.z()-stopStart.z()
                    val along=-sin(yaw)*dx+cos(yaw)*dz;val lateral=cos(yaw)*dx+sin(yaw)*dz
                    val oldTravel=stopTravel(stageTime)*scale/16.0
                    check(abs(lateral)<1e-8 && abs(actual.y()-stopStart.y())<1e-8 && abs(actual.yaw()-stopStart.yaw())<1e-8 && along>=oldTravel-1e-8 && along<=travel+1e-8) {"Unsupported sideways/backwards/vertical accepted stop movement"}
                    if(actual.distance(to)<1e-8) {stageTime=wanted;decision="STOP_PROFILE"}
                    else {
                        stageTime=stopInverse(along*16.0/scale).coerceAtLeast(stageTime).coerceAtMost(wanted)
                        decision=if(actual.distance(before)<1e-8)"BLOCKED_STOP_SOURCE_AND_FOOT_CONTACT_HELD" else "PARTIAL_STOP_FROM_ACCEPTED_DISPLACEMENT"
                    }
                    elapsed+=n;remaining-=n;selected.seek(stageTime);model.draw()
                    if(stageTime>=STOP_SECONDS-1e-10)finishStop()
                }
                State.CLAW,State.BREATH -> {
                    val n=min(remaining,selected.lengthSeconds-stageTime);stageTime+=n;elapsed+=n;remaining-=n
                    selected.seek(stageTime);model.draw()
                    if(stageTime>=selected.lengthSeconds-1e-10) {select(State.READY,CLAW,0.0);stageTime=0.0;decision="READY_HOLD"}
                }
                State.READY -> {elapsed+=remaining;remaining=0.0}
            }
        }
    }
}
