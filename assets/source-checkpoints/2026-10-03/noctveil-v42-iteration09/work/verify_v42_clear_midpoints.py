import json,hashlib,numpy as np
import noctveil_v42_pose_tools as p
out=p.ROOT/'outputs/v42_path_study/iteration_07';m=json.loads((out/'projects_noctveil_v42_wing_arc_candidate.bbmodel').read_text());clip=m['animations'][0];env=p.prefix(p.ROOT/'work/probe_v42_roll_head_clearance.py','rows');rows=[]
for t in np.linspace(0,4,257):
 mats=p.skeleton.transforms(m['outliner'][0],clip,float(t),np.eye(4),{});w={e['name']:(mats[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3] for e in m['elements']};a=p.audit(w);h=env['head_crossings'](w);r=env['records'](w);cs=[]
 for n,b in [('right_v24_tension_panel_15_0','right_v24_tension_panel_16_2'),('right_v2_folded_knuckle','right_v24_mantle_panel_05'),('right_v2_folded_hook_0','right_v24_mantle_panel_05')]:
  pair=[c for A,B in env['env']['sat_pairs'](r[n],r[b]) if (c:=env['env']['crossing'](A,B))]
  if pair:cs.append({'a':n,'b':b,'maximum_segment':max(c['segment_length'] for c in pair)})
 shared={}
 for e in m['elements']:
  if not(e['name'].startswith('right_v24_') and 'panel' in e['name']):continue
  outer={k for n,f in e['faces'].items() if n.startswith('outer') for k in f['vertices'] if not k.startswith('bow_')}
  for k in outer:shared.setdefault(k,[]).append(w[e['name']][list(e['vertices']).index(k)])
 gap=max([np.linalg.norm(q-vs[0]) for vs in shared.values() for q in vs] or [0.])
 rows.append({'progress':float(t),'is_key_midpoint':round(t*64)%2==1,'head_and_crown_crossings':h,'selected_internal_crossings':cs,'outer_shared_vertex_gap':float(gap),'minimum_all_mesh_y':float(min(v[:,1].min() for v in w.values())),**a})
 if len(rows)%64==1:print(json.dumps({'progress':float(t),'head_and_crown_crossings':len(h),'internal_crossings':len(cs),'minimum_y':rows[-1]['minimum_all_mesh_y']}),flush=True)
 if len(rows)%32==1:(out/'v42_clear_midpoints_partial.json').write_text(json.dumps({'samples':rows})+'\n',encoding='utf8')
summary={'samples':len(rows),'actual_key_midpoints':sum(r['is_key_midpoint'] for r in rows),'head_and_crown_crossing_count':sum(len(r['head_and_crown_crossings']) for r in rows),'selected_internal_crossing_count':sum(len(r['selected_internal_crossings']) for r in rows),'minimum_wing_y':min(r['minimum_wing_y'] for r in rows),'minimum_all_mesh_y':min(r['minimum_all_mesh_y'] for r in rows),'maximum_outer_shared_vertex_gap':max(r['outer_shared_vertex_gap'] for r in rows),'first_hook_overlap':next((r['progress'] for r in rows if r['hook_overlap']),None),'first_non_hook_overlap':next((r['progress'] for r in rows if r['non_hook_overlap']),None),'palm_or_radius_overlap':any(r['palm_or_radius_overlap'] for r in rows)}
(out/'v42_clear_midpoint_verification.json').write_text(json.dumps({'scope':'257 actual bbmodel poses, including128 actual midpoints of129 linear-Euler coupled keyframes. Head includes every descendant of neck, including crown bones. Finite surface intersection checks, not continuous-time/solid-containment proof.','summary':summary,'samples':rows,'full_body_self_collision_pass':False,'art_pass':False,'game_connected':False},indent=2)+'\n',encoding='utf8');print(json.dumps(summary))
