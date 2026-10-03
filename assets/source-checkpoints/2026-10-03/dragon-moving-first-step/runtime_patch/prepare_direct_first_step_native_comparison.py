"""Prepare a short same-condition native comparison from accepted Actor snapshots.
No editor process, captures or animation rendering launched by this script.
"""
from pathlib import Path
import json,uuid,numpy as np
import audit_native_plain_delivery as c
R=Path(__file__).resolve().parents[1];O=R/'outputs/reentry_direct_first_step'
new=json.loads((O/'ACTUAL_DIRECT_FIRST_STEP_TRACE.json').read_text())['cases']
old=json.loads((R/'outputs/locomotion_reentry_isolation/ACTUAL_LOCOMOTION_REENTRY_TRACE.json').read_text())['cases']
byclip={}
for rel in ['outputs/grounded_stride_rebuild/NEW_GROUNDED_STRIDE_GAME_CLIP.json','outputs/walk26_to_attack_stop/NEW_PHASE_SPECIFIC_STOP_GAME_CLIP.json','outputs/foreclaw_hook_rake/NEW_HOOK_RAKE_FORECLAW_GAME_CLIP.json','outputs/locomotion_reentry_isolation/NEW_READY_TO_WALK26_TRANSITION_CLIP.json','outputs/locomotion_reentry_isolation/NEW_ALTERNATE_CONTACT_STOP_CLIP.json','outputs/reentry_direct_first_step/NEW_MOVING_FIRST_STEP_TRANSITION_CLIP.json']:
    a=json.loads((R/rel).read_text());byclip[a['name']]=c.tables(a)
unchanged=[]
for after in new:
    before=next(x for x in old if x['action']==after['action'] and x['requested_phase']==after['requested_phase'] and x['requested_speed_blocks_sec']==after['requested_speed_blocks_sec'])
    lookup={round(x['elapsed'],8):x for x in before['frames']};pose=[];root=[];time=[]
    for row in after['frames']:
        if row['elapsed']>after['restart_begin_at']+1e-9:break
        a=lookup.get(round(row['elapsed'],8))
        if a is None:continue
        root.append(float(np.max(abs(np.array(row['actual_root'])-a['actual_root']))))
        time.append(abs(row['snapshot']['sourceSeconds']-a['snapshot']['sourceSeconds']))
        if 'all34_local_pose_bb' in row and 'all34_local_pose_bb' in a:
            pose.extend(float(np.max(abs(np.array(v)-a['all34_local_pose_bb'][n]))) for n,v in row['all34_local_pose_bb'].items())
    # The requestWalk instant changes the selected clip, but its ready pose is
    # deliberately identical. Only clocks after that instant may differ.
    result={'phase':after['requested_phase'],'speed':after['requested_speed_blocks_sec'],'action':after['action'],'matched_frames':len(root),'max_pre_restart_all34_pose_error_BB':max(pose),'max_pre_restart_root_error_blocks':max(root),'max_pre_restart_source_clock_error_seconds':max(time)}
    assert max(pose)<1e-8 and max(root)<1e-8 and max(time)<1e-8,result
    unchanged.append(result)
(O/'BEFORE_AFTER_STOP_ATTACK_READY_UNCHANGED.json').write_text(json.dumps(unchanged,indent=2),encoding='utf8')
M=json.loads((R/'outputs/foreclaw_hook_rake/dragon_v8_FORECLAW_HOOK_RAKE_NATIVE_REVIEW.bbmodel').read_text());M['animations']=[];mapping=[]
for tag,cases in [('BEFORE_STATIC_PHASE070',old),('AFTER_MOVING_PHASE000',new)]:
    case=next(x for x in cases if x['action']=='CLAW' and x['requested_phase']==.4 and x['requested_speed_blocks_sec']==1.5)
    begin=next(x['elapsed'] for x in case['transitions'] if x['to']=='RESTART')
    bytime={round(x['elapsed']-begin,8):x for x in case['frames'] if begin-1e-8<=x['elapsed']<=begin+3.6+1e-8}
    times=sorted(bytime);assert abs(times[0])<1e-8 and abs(times[-1]-3.6)<1e-8
    initial=np.array([bytime[times[0]]['snapshot']['actor'][ax] for ax in 'xyz'])
    channels={n:{ch:[] for ch in ['rotation','position']} for n in c.p.b.G};rows=[]
    for t in times:
        row=bytime[t];sn=row['snapshot'];source=c.p.local(c.fk(byclip[sn['clip']],sn['sourceSeconds']))
        actual=np.array([sn['actor'][ax] for ax in 'xyz']);root=(actual-initial)*[-1,1,-1]/(3/16)
        source['root']['position']+=root
        for n in channels:
            for ch in channels[n]:channels[n][ch].append(source[n][ch])
        rows.append({'preview_seconds':t,'state':sn['state'],'source_clip':sn['clip'],'source_seconds':sn['sourceSeconds'],'actor_root_BB':root.tolist()})
    uid=lambda text:str(uuid.uuid5(uuid.NAMESPACE_URL,'projects:moving-first-step-preview/'+tag+'/'+text))
    clip={'uuid':uid('clip'),'name':'PREVIEW_'+tag+'_ACTUAL_ACTOR_ROOT_ONCE','loop':'once','length':3.6,'snapping':20,'override':False,'animators':{}}
    for n,chs in channels.items():
        chs['rotation']=np.degrees(np.unwrap(np.radians(chs['rotation']),axis=0));keys=[]
        for ch,arr in chs.items():
            for i,(t,v) in enumerate(zip(times,arr)):
                keys.append({'uuid':uid(f'{n}/{ch}/{i}'),'channel':ch,'time':t,'interpolation':'linear','color':-1,'data_points':[dict(zip('xyz',[c.p.w.number(x) for x in v]))]})
        clip['animators'][c.p.b.G[n]['uuid']]={'name':n,'type':'bone','keyframes':keys}
    M['animations'].append(clip);mapping.append({'key':tag,'clip':clip['name'],'length_seconds':3.6,'commanded_speed_blocks_sec':1.5,'scale':3,'rows':rows})
M['name']=M['model_identifier']='dragon_v8_REENTRY_BEFORE_AFTER_RUNTIME_PREVIEW'
(O/'dragon_v8_REENTRY_BEFORE_AFTER_RUNTIME_PREVIEW_NOT_GAME_EXPORT.bbmodel').write_text(json.dumps(M,separators=(',',':')),encoding='utf8')
(O/'NATIVE_COMPARISON_MAPPING.json').write_text(json.dumps({'cases':mapping,'world_root_baked_once_preview_only_never_game_export':True,'same34bones132elements_original_texture':True,'same_commanded_speed_camera_size_and_3p6sec_elapsed_window':True,'different_startup_root_profile_is_change_under_test':True,'full_walk_cycle_or_visual_pass_not_claimed':True},indent=2),encoding='utf8')
print('NATIVE_OLD_NEW_3p6SEC_MODEL_PREPARED_RENDER_NOT_LAUNCHED; PRIOR_STOP_ATTACK_READY7CASES_IDENTICAL',flush=True)
