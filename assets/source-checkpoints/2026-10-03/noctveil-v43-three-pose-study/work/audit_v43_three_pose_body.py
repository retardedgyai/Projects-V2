"""All128 mesh surfaces at three poses, existing finite audit only."""
import json
import noctveil_v42_pose_tools as p
env=p.prefix(p.ROOT/'work/inspect_v42_self_collision.py','start');out=p.ROOT/'outputs/v43_gesture_study'
m=json.loads((out/'projects_noctveil_v43_three_pose_study.bbmodel').read_text());env['model']=m
base=json.loads((p.ROOT/'outputs/v42_path_study/iteration_05/v42_self_surface_crossing_audit.json').read_text())['baselines'];pairs={tuple(sorted((h['a'],h['b']))) for b in base for h in b['crossings']};rows=[]
for clip in m['animations'][:3]:
 r=env['audit'](clip,0);r['pose']=clip['name']
 for h in r['crossings']:h['present_in_two_original_baselines']=tuple(sorted((h['a'],h['b']))) in pairs
 rows.append(r);print(json.dumps({'pose':r['pose'],'crossings':len(r['crossings'])}),flush=True)
result={'model':'outputs/v43_gesture_study/projects_noctveil_v43_three_pose_study.bbmodel','scope':'All128 meshes at three independent static poses; noncoplanar proper triangle crossings only. Same owner assemblies excluded. Original baseline pair presence is not harmlessness. Coplanar overlap, solid containment, intermediate motion and continuous-time collision unproved.','samples':rows,'new_nonadjacent_wing_crossings':[dict(pose=r['pose'],**h) for r in rows for h in r['crossings'] if h['attacking_wing_involved'] and not h['directly_adjacent_bones'] and not h['present_in_two_original_baselines']],'full_body_pass':False,'art_pass':False,'game_connected':False}
(out/'v43_three_pose_full_body_surface_audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print(json.dumps({'new_wing_pairs':[(h['pose'],h['a'],h['b']) for h in result['new_nonadjacent_wing_crossings']]}))
