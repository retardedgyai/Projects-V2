import json,numpy as np
import noctveil_v42_pose_tools as p
out=p.ROOT/'outputs/v42_path_study/iteration_05';d=json.loads((out/'v42_arc_candidate_evidence.json').read_text());m=json.loads((out/'projects_noctveil_v42_wing_arc_candidate.bbmodel').read_text());clip=m['animations'][0]
def hit(t,kind):
 mats=p.skeleton.transforms(m['outliner'][0],clip,t,np.eye(4),{})
 for e in m['elements']:
  n=e['name'];is_hook=n.startswith('right_v2_folded_hook_')
  if kind=='hook' and not is_hook:continue
  if kind=='sheet' and (p.review['region'](n)!='membrane_and_ribs'):continue
  if kind=='body' and n in p.wing_names:continue
  w=(mats[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3]
  if p.review['env']['triangles_in_box'](e,w):return n
 return None
rows=[]
for kind,field in [('hook','hook_overlap'),('sheet','non_hook_overlap')]:
 series=d['bbmodel_interpolation_rows'];i=next(i for i,r in enumerate(series) if r[field]);lo,hi=series[i-1]['progress'],series[i]['progress'];original=[lo,hi]
 for _ in range(14):
  mid=(lo+hi)/2
  if hit(mid,kind):hi=mid
  else:lo=mid
 rows.append({'part':kind,'original_sample_bracket':original,'refined_clear_to_hit_bracket':[lo,hi],'first_hit_mesh_at_upper_bound':hit(hi,kind)})
body=[{'progress':r['progress'],'mesh':n} for r in d['bbmodel_interpolation_rows'][::8] if (n:=hit(r['progress'],'body'))]
result={'scope':'14 bisections of each earliest sampled clear-to-hit interval in the actual serialized bbmodel. These brackets refine observed onset, not an interval-global continuous collision proof. Body-to-fixed-opponent checked33 finite poses, excluding the attacking wing assembly.','fixed_target':p.target,'contact_onset_brackets':rows,'body_opponent_overlap_samples':body,'unobserved_earlier_contact_continuously_excluded':False,'game_hitbox_calibrated':False,'game_connected':False}
(out/'v42_contact_bracket_refinement.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
