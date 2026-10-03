"""Three independent poses, comparison and visibility evidence. No motion pass."""
import json,copy,base64,io,hashlib,numpy as np
from PIL import Image,ImageDraw,ImageFont
import noctveil_v42_pose_tools as p
import mesh_preview
probe=p.prefix(p.ROOT/'work/probe_v43_three_gestures.py','rows')
out=p.ROOT/'outputs/v43_gesture_study';data=json.loads((out/'v43_three_gesture_probe.json').read_text())
old=json.loads((p.ROOT/'outputs/v42_path_study/iteration_09_cloak_counterwing/projects_noctveil_v42_timed_sweep_review.bbmodel').read_text());oldclip=old['animations'][0]
scan=json.loads((out/'v43_hand_lead_scan.json').read_text())
selected=[];poses=[];rows=[]
for i,roll in enumerate([285,300,240]):
 row=copy.deepcopy(next(r for r in data['rows'] if r['pose']==i and r['roll']==roll))
 if i==1:
  pick=next(r for r in scan['rows'] if r['direction']==2 and r['roll']==270)
  row.update(pick);row['pose']=1;roll=270
 if i==2:row['q']['claw_direction']=[-1,.3,-.5]
 v,report=p.closed_pose(row['q'],roll,0);v[('neck','rotation')]=[10,row['q']['body'][1]*.6,0]
 for n in ['left_crown','right_crown']:v[(n,'rotation')]=[-20,0,0]
 v=probe['opposite'](v)
 if i==0:
  # Reuse the approved coupled closed-wing state for a compact load, under this body pose.
  wingbones={a['name'] for a in probe['approved']['animators'].values() if a['name'].startswith(('right_wing_','right_mantle_'))}
  for u,a in probe['approved']['animators'].items():
   if a['name'] in wingbones:
    for ch in {k['channel'] for k in a['keyframes']}:v[(a['name'],ch)]=p.skeleton.channel(probe['approved'],u,ch,0)
  row['load_wing_state']='Existing approved closed_idle_breath_v32 at0; no straight outstretched load'
 w=p.world(v);row['head_and_crown_crossings']=probe['env']['check']['head_crossings'](w);row['minimum_all_y']=float(min(a[:,1].min() for a in w.values()));row['target_audit_actual']=p.audit(w)
 clip=p.frame_clip(v);clip['name']=['load_pose_v43','contact_pose_v43','exit_pose_v43'][i];clip['uuid']=p.uid(clip['name']);poses.append(clip);selected.append(row)
model=copy.deepcopy(p.rig.source);model['name']=model['model_identifier']='projects_noctveil_v43_three_pose_study';model['animations']=poses+copy.deepcopy(p.rig.source['animations'])
assert all(model[k]==p.rig.source[k] for k in ['elements','outliner','textures']) and model['animations'][3:]==p.rig.source['animations']
path=out/(model['name']+'.bbmodel');path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
def world(m,c,t):
 mats=p.skeleton.transforms(m['outliner'][0],c,t,np.eye(4),{})
 return {e['name']:(mats[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3] for e in m['elements']}
atlas=probe['atlas'];rd=probe['rd'];font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',12)
cards=[('v42_09',oldclip,t,i) for i,t in enumerate([.25,.93,1.55])]+[('v43',clip,0,i) for i,clip in enumerate(poses)]
board=Image.new('RGB',(960,1120),(31,28,35));ink=ImageDraw.Draw(board)
ink.text((8,7),'POSE COMPARISON / each card 320x240 / same source pixels / no native shading claim',font=font,fill='white')
for j,view in enumerate(['front','hero']):
 pts=np.concatenate([mesh_preview.project(np.concatenate(list(world(model,c,t).values())),view) for _,c,t,_ in cards]);lo,hi=pts.min(0),pts.max(0);lo[:2]-=3;hi[:2]+=3
 for version,clip,t,i in cards:
  m=model if version=='v43' else old;w=world(m,clip,t);im=mesh_preview.render(rd,atlas,clip,t,view,(320,240),[lo,hi]);y=30+j*540+(270 if version=='v43' else 0);x=i*320
  board.paste(im,(x,y+25));ink.text((x+5,y+4),f'{version} / {view} / '+['LOAD','CONTACT','EXIT'][i],font=font,fill='white')
  # Flat IDs are a diagnostic copy only; delivered model and comparison use original texture pixels.
  diag=copy.deepcopy(p.rig.source);colors=np.array([[k+1,0,0,255] for k in range(len(diag['elements']))],dtype=np.uint8)[None,:,:]
  for k,e in enumerate(diag['elements']):
   for f in e['faces'].values():
    for uv in f['uv'].values():uv[:]=[k,0]
  ids=np.array(mesh_preview.render(diag,colors,clip,t,view,(320,240),[lo,hi],fullbright=True))[:,:,0]
  hand_indices=[k+1 for k,e in enumerate(diag['elements']) if e['name'] in p.hand_names]
  diag['elements']=[e for e in diag['elements'] if e['name'] in p.hand_names]
  isolated=np.array(mesh_preview.render(diag,colors,clip,t,view,(320,240),[lo,hi],fullbright=True))[:,:,0]
  visible=int(np.isin(ids,hand_indices).sum());projected=int(np.isin(isolated,hand_indices).sum())
  rows.append({'version':version,'view':view,'pose':i,'hand_visible_pixels':visible,'hand_isolated_projected_pixels':projected,'visibility_fraction':visible/max(1,projected)})
board.save(out/'noctveil_v43_three_pose_comparison.png')
evidence={'model':str(path.relative_to(p.ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'previous_iteration09_whole_gesture_pass':False,'previous_iteration09_review':'Across all25 frames versus Gore64 frames: membrane dominates0.92-1.25s and hides palm/wrist/claw route; outside spread lacks readable chest load; exit1.5-2.0s held; idle recovery absent. Independent parent visual verdict FIX-FIRST.','source_geometry_hierarchy_uv_textures_original3_clips_exact':True,'selected_poses':selected,'normal_size_visibility_diagnostics':rows,'comparison':'3 independent static poses each, front+oblique320x240, same bound per view across both candidates. Original approved textures used. Flat-ID renders only diagnose hand occlusion; counts do not prove aesthetic acceptance.','scope':'Static gesture research only. Source idle clips unchanged. No connected interpolation, idle return, native/current fullbody collision pass, artistic pass or game integration.','art_pass':False,'whole_body_gesture_pass':False,'game_connected':False}
(out/'v43_three_pose_evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf8')
print(json.dumps({'model_sha256':evidence['sha256'],'visibility':rows}),flush=True)
