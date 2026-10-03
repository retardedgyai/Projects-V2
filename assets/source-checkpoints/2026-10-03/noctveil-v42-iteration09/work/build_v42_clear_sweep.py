"""Separate candidate: geometry preserved; crown/neck and low cross-pose repair."""
import sys,json,copy,hashlib,numpy as np
import noctveil_v42_pose_tools as p
sys.argv=['build_v42_arc_candidate.py','refined'];designenv=p.prefix(p.ROOT/'work/build_v42_arc_candidate.py','values');check=p.prefix(p.ROOT/'work/probe_v42_roll_head_clearance.py','rows')
out=p.ROOT/'outputs/v42_path_study/iteration_07';out.mkdir(exist_ok=True)
def S(x):x=float(np.clip(x,0,1));return x*x*(3-2*x)
def design(t):
 q,_=designenv['design'](t);q['wrist'][1]-=1.25*S((t-2.25)/.5)*(1-S((t-3.15)/.85));roll=285+3*S((t-2.25)/.75)-68*S(t-3);return q,roll
values=[];keys={};reports=[]
for t in np.linspace(0,4,129):
 q,roll=design(float(t));v,r=p.closed_pose(q,roll,float(t))
 if v is None:raise RuntimeError('Membrane closure failed at '+str(t))
 v[('neck','rotation')]=[10,q['body'][1]*.6,0];v[('left_crown','rotation')]=[-20,0,0];v[('right_crown','rotation')]=[-20,0,0]
 if values:
  for k,a in v.items():
   if k[1]=='rotation':v[k]=(np.asarray(a)+360*np.round((np.asarray(values[-1][k])-np.asarray(a))/360)).tolist()
 values.append(v);reports.append({'progress':float(t),'design':q,'roll':roll,'forearm_rotation':r['forearm_rotation'],'feet':r['feet'],'support_proxy_margin':r['support_proxy_margin']})
 for (name,channel),a in v.items():keys.setdefault(name,[]).append({'uuid':p.uid('clear-sweep/'+str(t)+'/'+name+'/'+channel),'channel':channel,'time':float(t),'interpolation':'linear','data_points':[dict(zip('xyz',map(p.rig.literal,a)))]})
 if t in [0,1,2,3,4]:print(json.dumps({'progress':t,'roll':roll,'wrist':q['wrist'],'forearm_rotation':r['forearm_rotation']}),flush=True)
clip={'uuid':p.uid('clear-sweep-arc'),'name':'wing_arm_arc_constraint_study_v42','loop':'hold','length':4,'override':False,'snapping':32,'animators':{p.rig.groups[n]['uuid']:{'name':n,'type':'bone','rotation_global':False,'keyframes':k} for n,k in keys.items()}}
model=copy.deepcopy(p.rig.source);model['name']=model['model_identifier']='projects_noctveil_v42_wing_arc_candidate';model['animations']=[clip]+copy.deepcopy(p.rig.source['animations']);path=out/(model['name']+'.bbmodel');path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
assert all(model[k]==p.rig.source[k] for k in ['elements','outliner','textures']) and model['animations'][1:]==p.rig.source['animations']
rows=[]
for t in np.linspace(0,4,65):
 mats=p.skeleton.transforms(model['outliner'][0],clip,float(t),np.eye(4),{});w={e['name']:(mats[e['uuid']][:3,:3]@np.asarray(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3] for e in model['elements']};a=p.audit(w);head=check['head_crossings'](w);r=check['records'](w);internal=[]
 for n,b in [('right_v24_tension_panel_15_0','right_v24_tension_panel_16_2'),('right_v2_folded_knuckle','right_v24_mantle_panel_05'),('right_v2_folded_hook_0','right_v24_mantle_panel_05')]:
  cs=[c for A,B in check['env']['sat_pairs'](r[n],r[b]) if (c:=check['env']['crossing'](A,B))]
  if cs:internal.append({'a':n,'b':b,**max(cs,key=lambda c:c['segment_length'])})
 rows.append({'progress':float(t),'head_and_crown_surface_crossings':head,'selected_internal_crossings':internal,'minimum_all_mesh_y':float(min(v[:,1].min() for v in w.values())),**a})
 if len(rows)%16==1:print(json.dumps({'progress':float(t),'head_and_crown_crossings':len(head),'selected_internal_crossings':len(internal),'wing_y':a['minimum_wing_y']}),flush=True)
 (out/'v42_clear_sweep_partial.json').write_text(json.dumps({'samples':rows})+'\n',encoding='utf8')
summary={'head_and_crown_crossing_count':sum(len(r['head_and_crown_surface_crossings']) for r in rows),'selected_internal_crossing_count':sum(len(r['selected_internal_crossings']) for r in rows),'minimum_wing_y':min(r['minimum_wing_y'] for r in rows),'minimum_all_mesh_y':min(r['minimum_all_mesh_y'] for r in rows),'first_hook_overlap':next((r['progress'] for r in rows if r['hook_overlap']),None),'first_non_hook_overlap':next((r['progress'] for r in rows if r['non_hook_overlap']),None),'palm_or_radius_overlap':any(r['palm_or_radius_overlap'] for r in rows)}
result={'model':str(path.relative_to(p.ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'geometry_hierarchy_uv_textures_original3_clips_exact':True,'fixed_target':p.target,'key_count':129,'sample_count':65,'key_design_rows':reports,'samples':rows,'summary':summary,'changes':'Same broad spherical path, neck nativeX10; existing crown bones nativeX−20. Lower cross wrist smoothly1.25 units to reduce membrane folding; rotate whole wing arm285→288→220 and counterrotate palm. No vertex/UV/material/bone-hierarchy changes.','full_body_self_collision_pass':False,'continuous_time_collision_proven':False,'native_verified':False,'art_pass':False,'game_connected':False}
(out/'v42_clear_sweep_evidence.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({'model':result['model'],'sha256':result['sha256'],'summary':summary}))
