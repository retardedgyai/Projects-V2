"""Scoped copy of tested controller/bundle; change only READY moving reentry."""
from pathlib import Path
import shutil,json,hashlib
R=Path(__file__).resolve().parents[1];O=R/'outputs/reentry_direct_first_step';BASE=R/'outputs/locomotion_reentry_isolation'
PATCH=O/'runtime_patch';PATCH.mkdir(exist_ok=True)
s=(BASE/'runtime_patch/DragonActionController.kt').read_text(encoding='utf8')
s=s.replace('const val RESTART = "transition_ready_to_approved_walk26_phase070_native_rigid"','const val RESTART = "transition_ready_moving_first_step_to_approved_walk26_phase000_native_rigid"')
s=s.replace('const val RESTART_SECONDS = 2.60','const val RESTART_SECONDS = 2.10\n        const val RESTART_ACCEL_SECONDS = 1.35\n        const val RESTART_TRAVEL_BB = 11.40\n        const val RESTART_ENTRY = .0')
s=s.replace('private var stopStart=reader.get()','private var stopStart=reader.get()\n    private var restartStart=reader.get()')
s=s.replace('// Test starts from an already-walking pose. READY->WALK blending is deliberately not claimed.','// Test helper: seed existing walk; normal READY startup uses moving first-step clip.')
s=s.replace('stageTime=0.0;select(State.RESTART,RESTART,0.0);decision="READY_REPOSITION_TO_APPROVED_WALK26"','stageTime=0.0;restartStart=reader.get();select(State.RESTART,RESTART,0.0);decision="READY_MOVING_FIRST_STEP_TO_APPROVED_WALK26"')
s=s.replace('private fun stopInverse(travelBB: Double)', '''private fun restartTravel(t: Double)=8*(if(t<RESTART_ACCEL_SECONDS)t*t/(2*RESTART_ACCEL_SECONDS) else t-RESTART_ACCEL_SECONDS/2)
    private fun restartInverse(travelBB: Double): Double {
        val d=travelBB.coerceIn(0.0,RESTART_TRAVEL_BB)
        return if(d<4*RESTART_ACCEL_SECONDS)sqrt(d*RESTART_ACCEL_SECONDS/4) else d/8+RESTART_ACCEL_SECONDS/2
    }
    private fun stopInverse(travelBB: Double)''')
start=s.index('                State.RESTART -> {');end=s.index('                State.WALK -> {',start)
s=s[:start]+'''                State.RESTART -> {
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
'''+s[end:]
assert 'READY_REPOSITION_TO_APPROVED_WALK26' not in s
(PATCH/'DragonActionController.kt').write_text(s,encoding='utf8')
shutil.copy2(BASE/'runtime_patch/DragonNumericClip.kt',PATCH/'DragonNumericClip.kt')
B=O/'projects_bundle';shutil.copytree(BASE/'projects_bundle',B,dirs_exist_ok=True)
compiler=(R/'work/compile_locomotion_reentry_runtime.ps1').read_text(encoding='utf8')
compiler=compiler.replace('.\\work\\compiled_locomotion_reentry_runtime','.\\work\\compiled_direct_first_step_runtime').replace('.\\work\\locomotion_reentry_runtime_patch','.\\outputs\\reentry_direct_first_step\\runtime_patch').replace('.\\work\\locomotion_reentry_runtime_classpath.txt','.\\work\\direct_first_step_runtime_classpath.txt').replace('dragon_locomotion_reentry_isolated','dragon_direct_first_step_isolated')
(R/'work/compile_direct_first_step_runtime.ps1').write_text(compiler,encoding='utf8')
print('SCOPED_CONTROLLER_AND_EXISTING13_CLIP_BUNDLE_COPIED_NEW_TRANSITION_EXPORT_PENDING',flush=True)
