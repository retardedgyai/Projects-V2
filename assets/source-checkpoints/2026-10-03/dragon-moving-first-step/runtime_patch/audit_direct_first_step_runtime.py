from pathlib import Path
import json,hashlib,numpy as np
import author_reentry_direct_first_step as s
R=s.R;O=s.O;B=O/'projects_bundle';P=s.p;SCALE=3/16
trace=json.loads((O/'ACTUAL_DIRECT_FIRST_STEP_TRACE.json').read_text());clips={}
for rel in ['outputs/grounded_stride_rebuild/NEW_GROUNDED_STRIDE_GAME_CLIP.json','outputs/walk26_to_attack_stop/NEW_PHASE_SPECIFIC_STOP_GAME_CLIP.json','outputs/foreclaw_hook_rake/NEW_HOOK_RAKE_FORECLAW_GAME_CLIP.json','outputs/aimed_ground_breath/NEW_AIMED_GROUND_BREATH_GAME_CLIP.json','outputs/reentry_direct_first_step/NEW_MOVING_FIRST_STEP_TRANSITION_CLIP.json','outputs/locomotion_reentry_isolation/NEW_ALTERNATE_CONTACT_STOP_CLIP.json']:
    c=json.loads((R/rel).read_text());clips[c['name']]=s.common.tables(c)
OFF={'front_l':0.,'hind_r':.8,'front_r':.5,'hind_l':.3}
AIR_STOP={'front_l':(0.,.94),'hind_r':(1.,1.64),'front_r':(1.72,2.16)}
def stance(state,n,t,clip):
    if state=='WALK':return ((t/3.2+OFF[n])%1)<.7-1e-8
    if state=='READY':return True
    if state=='RESTART':return s.foot(n,t)[2]
    if state!='STOP':return False
    air=AIR_STOP
    if 'phase020' in clip:air={name.replace('_l','_r') if '_l' in name else name.replace('_r','_l'):ab for name,ab in air.items()}
    return n not in air or not (air[n][0]+1e-9<t<air[n][1]-1e-9)
checks=[];failures=[]
for case in trace['cases']:
    errors=[];exact=[];roots=[];anchors=[];floors=[];phase_err=[];profile_err=[];restart_toe=[];restart_anchors={};prior={};previous=None;restart_origin=None;by_state={}
    for row in case['frames']:
        snap=row['snapshot'];state=snap['state'];t=snap['sourceSeconds'];pos=np.array([snap['actor'][ax] for ax in 'xyz'])
        roots.append(float(np.linalg.norm(np.array(row['actual_root'])-pos)))
        if state=='RESTART':
            if restart_origin is None:restart_origin=pos.copy()-[0,0,s.travel(t)*SCALE]
            profile_err.append(float(np.linalg.norm(pos-restart_origin-[0,0,s.travel(t)*SCALE])))
        else:restart_origin=None;restart_anchors={}
        if 'all34_local_pose_bb' in row:
            want=s.common.fk(clips[snap['clip']],t);same=s.common.fk(clips[snap['clip']],t,P.b.a.WSEE_DEGREE)
            for n,v in row['all34_local_pose_bb'].items():
                actual=np.array(v).reshape(4,4);errors.append(float(np.max(abs(actual-want[n]))));exact.append(float(np.max(abs(actual-same[n]))))
            if state in ['READY','RESTART','WALK','STOP']:
                for n in P.OFF:
                    m=np.array(row['all34_local_pose_bb'][n+'_paw']).reshape(4,4)
                    pts=P.b.a.PAWS[n]@m[:3,:3].T+m[:3,3];floors.append(float(pts[:,1].min()*SCALE))
        for n,w in row['toe_world_blocks'].items():
            world=np.array(w);planted=stance(state,n,t,snap['clip']);old=prior.get(n)
            contact=bool(old and old['planted'] and planted)
            if contact and state==old['state']=='WALK':contact=((t/3.2+OFF[n])%1)>=old['phase']-1e-8
            if contact:
                res=float(np.linalg.norm(world-old['anchor']));anchors.append(res);by_state.setdefault(state,[]).append(res);anchor=old['anchor']
            else:anchor=world.copy()
            if state=='RESTART':
                if planted:
                    own_anchor=restart_anchors.setdefault(n,world.copy())
                    restart_toe.append(float(np.linalg.norm(world-own_anchor)))
                else:restart_anchors.pop(n,None)
            prior[n]={'world':world,'anchor':anchor,'planted':planted,'state':state,'phase':(t/3.2+OFF[n])%1}
        if previous and previous['snapshot']['state']==state=='WALK':
            old=previous['snapshot'];phase_err.append(abs((snap['walkCycles']-old['walkCycles'])*4.8-(snap['actor']['z']-old['actor']['z'])))
        previous=row
    trans=case['transitions'];v=case['requested_speed_blocks_sec'];phase=case['requested_phase']
    gate,distance=min(((g,(g-phase+1)%1) for g in [.2,.7]),key=lambda x:x[1]);wait=distance*4.8/v
    first_stop,first_action,first_ready=trans[:3];first_rwalk=next(x for x in trans if x['from']=='RESTART' and x['to']=='WALK')
    firstroot=next(r['snapshot']['actor']['z'] for r in case['frames'] if r['snapshot']['state']=='READY')
    secondroot=next(r['snapshot']['actor']['z'] for r in case['frames'] if r['elapsed']>case['first_ready_at']+.2 and r['snapshot']['state']=='READY')
    final=case['frames'][-1]['snapshot']['actor']['z']
    c={'phase':phase,'speed':v,'action':case['action'],'frames':len(case['frames']),
       'native_matrix_error_BB':max(errors),'WSEE_precision_matrix_error_BB':max(exact),'root_error_blocks':max(roots),
       'walk_distance_phase_error_blocks':max(phase_err),'restart_profile_error_blocks':max(profile_err),
       'stance_anchor_max_drift_blocks':max(anchors),'restart_contact_max_drift_blocks':max(restart_toe),
       'anchor_drift_by_state_blocks':{k:max(vv) for k,vv in by_state.items()},'lowest_paw_vertex_Y_blocks':min(floors),
       'max_transition_all34_pose_jump_BB':max(x['all34LocalPoseJumpBB'] for x in trans),
       'stop_gate_wait_error_seconds':abs(first_stop['elapsed']-wait),
       'stop_speed_clock_error_seconds':abs(first_action['elapsed']-first_stop['elapsed']-2.3*1.5/v),
       'action_clock_error_seconds':abs(first_ready['elapsed']-first_action['elapsed']-(4.8 if case['action']=='CLAW' else 5.3)),
       'moving_restart_clock_error_seconds':abs(first_rwalk['elapsed']-case['restart_begin_at']-s.L*1.5/v),
       'moving_restart_duration_seconds':s.L*1.5/v,'joined_original_walk_phase':first_rwalk['toSeconds']/3.2,
       'first_ready_root_error_blocks':abs(firstroot-(wait*v+2.13835*SCALE)),
       'second_ready_root_error_blocks':abs(secondroot-(firstroot+s.D*SCALE+4.8+.2*4.8+2.13835*SCALE)),
       'final_root_error_blocks':abs(final-(secondroot+s.D*SCALE+1.2*v)),
       'bones':case['bones'],'entities':case['total_entities'],'closed_entities_zero':case['closed_entities_zero']}
    limits={k:1e-8 for k in ['WSEE_precision_matrix_error_BB','root_error_blocks','walk_distance_phase_error_blocks','restart_profile_error_blocks','stop_gate_wait_error_seconds','stop_speed_clock_error_seconds','action_clock_error_seconds','moving_restart_clock_error_seconds','first_ready_root_error_blocks','second_ready_root_error_blocks','final_root_error_blocks']}
    limits.update(native_matrix_error_BB=1e-5,max_transition_all34_pose_jump_BB=1e-5,stance_anchor_max_drift_blocks=.00375,restart_contact_max_drift_blocks=.00020)
    failures.extend({'phase':phase,'speed':v,'key':k,'value':c[k],'limit':limit} for k,limit in limits.items() if c[k]>limit)
    if c['lowest_paw_vertex_Y_blocks']<-.00375 or c['bones']!=34 or c['entities']!=30 or not c['closed_entities_zero']:failures.append({'geometry_entity_cleanup':c})
    checks.append(c)
protected={path:s.sha(R/path)==v for path,v in s.PROTECTED.items()};assert all(protected.values())
mid='dragon_v8_polish_shared_atlas_study.bbmodel'
old=json.loads((R/f'outputs/locomotion_reentry_isolation/projects_bundle/models/{mid}/model.animation.json').read_text())['animations']
new=json.loads((B/f'models/{mid}/model.animation.json').read_text())['animations'];assert len(new)==14 and all(new[n]==v for n,v in old.items())
assert all(v for v in trace['guards'].values() if isinstance(v,bool))
report={'technical_validation':'PASS' if not failures else 'FIX_FIRST','visual_natural_reentry_pass':False,
        'actual_existing_ProjectS_Actor_loader_WSEE_offline':True,'case_count':len(checks),'cases':checks,'guards':trace['guards'],
        'all_prior13_runtime_clips_and9_source_hashes_preserved':True,'protected_sources':protected,'threshold_failures':failures,
        'renderer_new_rig_texture_atlas_changes':False,'no_listening_socket':trace['no_listening_socket'],'online_players':trace['online_players'],
        'server_start_called':trace['server_start_called'],'production_repo_written':False,'native_GUI_full_animation_real_client_AI_damage_FX_perf_tested':False}
(O/'DIRECT_FIRST_STEP_VALIDATION.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(json.dumps({'technical_validation':report['technical_validation'],'case_count':len(checks),'maxima':{k:max(c[k] for c in checks) for k in ['native_matrix_error_BB','WSEE_precision_matrix_error_BB','restart_profile_error_blocks','restart_contact_max_drift_blocks','stance_anchor_max_drift_blocks','max_transition_all34_pose_jump_BB']},'threshold_failures':failures},indent=2),flush=True)
assert not failures
