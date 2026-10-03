import json,numpy as np
import noctveil_v42_pose_tools as p
env=p.prefix(p.ROOT/'work/probe_v42_roll_head_clearance.py','rows');out=p.ROOT/'outputs/v42_path_study/iteration_07';m=json.loads((out/'projects_noctveil_v42_wing_arc_candidate.bbmodel').read_text());clip=m['animations'][0];rows=json.loads((out/'v42_clear_midpoint_verification.json').read_text())['samples'];records=[]
def distance(P,A,B):d=B-A;t=float(np.clip((P-A)@d/(d@d),0,1));return float(np.linalg.norm(P-(A+t*d)))
for row in rows:
 if not row['selected_internal_crossings']:continue
 t=row['progress'];mat=p.skeleton.transforms(m['outliner'][0],clip,t,np.eye(4),{});w={e['name']:(mat[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mat[e['uuid']][:3,3] for e in m['elements']};r=env['records'](w)
 a='right_v24_tension_panel_15_0';b='right_v24_tension_panel_16_2';cs=[c for A,B in env['env']['sat_pairs'](r[a],r[b]) if (c:=env['env']['crossing'](A,B))]
 bones=p.rig.matrices_at(t,p.rig.track_arrays(clip));M=bones['right_mantle_hinge_15'];A=(M@np.r_[p.closure['X'][14],1])[:3];B=(M@np.r_[p.closure['X'][12],1])[:3]
 points=[np.array(P) for c in cs for P in c['points']];records.append({'progress':t,'crossing_pairs':len(cs),'maximum_intersection_distance_from_shared_edge':max([distance(P,A,B) for P in points] or [0]),'maximum_intersection_segment_length':max([c['segment_length'] for c in cs] or [0]),'shared_outer_edge_world':[A.tolist(),B.tolist()]})
result={'scope':'The only selected mid-key internal surface-crossing pair is between tension strips on adjacent faces15 and16, which share outer edge(v14,v12). Segment length is not penetration depth. Measure crossing endpoints relative to that actual shared edge; do not declare all such contacts harmless by topology alone.','adjacent_face_shared_edge':[[14,11,12],[14,12,13]],'rows':records,'maximum_distance_from_shared_edge':max(r['maximum_intersection_distance_from_shared_edge'] for r in records),'full_self_collision_pass':False,'art_pass':False}
(out/'v42_midpoint_contact_classification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({'count':len(records),'maximum_distance_from_shared_edge':result['maximum_distance_from_shared_edge'],'maximum_segment_length':max(r['maximum_intersection_segment_length'] for r in records)}))
