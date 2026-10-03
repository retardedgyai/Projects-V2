"""Pose only: reuse the exact approved closed opposite-wing state."""
import json,copy,hashlib,numpy as np
import noctveil_v42_pose_tools as p
src=p.ROOT/'outputs/v42_path_study/iteration_08_timing/projects_noctveil_v42_timed_sweep_review.bbmodel';out=p.ROOT/'outputs/v42_path_study/iteration_09_cloak_counterwing';out.mkdir(exist_ok=True);m=json.loads(src.read_text());clip=m['animations'][0]
def names(g):
 result={g['name']}
 for c in g['children']:
  if isinstance(c,dict):result|=names(c)
 return result
allowed=names(p.rig.groups['left_wing_shoulder']);approved=next(a for a in p.rig.source['animations'] if a['name']=='closed_idle_breath_v32');changed=[]
for u,a in approved['animators'].items():
 if a['name'] not in allowed:continue
 frames=[]
 for channel in {k['channel'] for k in a['keyframes']}:
  value=p.skeleton.channel(approved,u,channel,0)
  for t in [0,clip['length']]:frames.append({'uuid':p.uid('closed-counterwing/'+a['name']+'/'+channel+'/'+str(t)),'channel':channel,'time':t,'interpolation':'linear','data_points':[dict(zip('xyz',map(p.rig.literal,value)))]})
 clip['animators'][u]={'name':a['name'],'type':'bone','rotation_global':False,'keyframes':frames};changed.append(a['name'])
assert all(m[k]==p.rig.source[k] for k in ['elements','outliner','textures']) and m['animations'][1:]==p.rig.source['animations']
path=out/'projects_noctveil_v42_timed_sweep_review.bbmodel';path.write_text(json.dumps(m,separators=(',',':'))+'\n',encoding='utf8');minimum=1e9;lowest='';rows=[]
for t in np.linspace(0,2.05,65):
 mat=p.skeleton.transforms(m['outliner'][0],clip,float(t),np.eye(4),{});w={e['name']:(mat[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mat[e['uuid']][:3,3] for e in m['elements']};n=min(w,key=lambda n:w[n][:,1].min());y=float(w[n][:,1].min());minimum=min(minimum,y);rows.append({'time':float(t),'minimum_all_mesh_y':y,'lowest_mesh':n})
result={'model':str(path.relative_to(p.ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'geometry_hierarchy_uv_textures_original3_clips_exact':True,'pose_only_change':'Opposite left-wing bones sample exact closed_idle_breath_v32 at0 and hold that approved coupled state. Earlier new attack study had left wing default rest spread; this reduces competing wing span and leaves the striking arm as the active silhouette.','closed_left_bones':changed,'minimum_all_mesh_y_65_timed_samples':minimum,'timed_ground_samples':rows,'full_body_self_collision_pass':False,'native_verified':False,'native_pixel_shading_equivalence_claimed':False,'art_pass':False,'game_connected':False}
(out/'v42_counterwing_evidence.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({'model':result['model'],'sha256':result['sha256'],'closed_left_bones':len(changed),'minimum_y':minimum}))
