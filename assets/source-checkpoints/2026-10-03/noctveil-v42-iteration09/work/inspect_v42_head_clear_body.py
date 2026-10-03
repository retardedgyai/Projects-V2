import sys,json,time,numpy as np
import noctveil_v42_pose_tools as p
env=p.prefix(p.ROOT/'work/inspect_v42_self_collision.py','start');iteration=sys.argv[1] if len(sys.argv)>1 else 'iteration_06';stem=sys.argv[2] if len(sys.argv)>2 else 'projects_noctveil_v42_wing_arc_candidate';out=p.ROOT/'outputs/v42_path_study'/iteration;model=json.loads((out/(stem+'.bbmodel')).read_text());env['model']=model;clip=model['animations'][0]
old=json.loads((p.ROOT/'outputs/v42_path_study/iteration_05/v42_self_surface_crossing_audit.json').read_text());baseline=old['baselines'];pairs={tuple(sorted((h['a'],h['b']))) for r in baseline for h in r['crossings']};rows=[];start=time.monotonic()
for t in np.linspace(0,clip['length'],17):
 r=env['audit'](clip,float(t));rows.append(r)
 for h in r['crossings']:h['also_present_in_two_sample_original_baseline']=tuple(sorted((h['a'],h['b']))) in pairs
 (out/'v42_self_surface_crossing_partial.json').write_text(json.dumps({'samples':rows})+'\n',encoding='utf8')
 print(json.dumps({'progress':float(t),'crossings':len(r['crossings']),'new_wing_nonadjacent':sum(h['attacking_wing_involved'] and not h['directly_adjacent_bones'] and not h['also_present_in_two_sample_original_baseline'] for h in r['crossings'])}),flush=True)
 if time.monotonic()-start>65:break
result={'scope':old['scope'],'model':f'outputs/v42_path_study/{iteration}/{stem}.bbmodel','baseline_reused_exact_original_model':True,'baselines':baseline,'samples':rows,'all_requested_samples_complete':len(rows)==17,'elapsed_seconds':time.monotonic()-start,'new_wing_nonadjacent_crossing_pairs':sorted({tuple(sorted((h['a'],h['b']))) for r in rows for h in r['crossings'] if h['attacking_wing_involved'] and not h['directly_adjacent_bones'] and not h['also_present_in_two_sample_original_baseline']}),'full_body_self_collision_pass':False,'art_pass':False,'game_connected':False}
(out/'v42_self_surface_crossing_audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps({'complete':result['all_requested_samples_complete'],'new_wing_nonadjacent_pairs':result['new_wing_nonadjacent_crossing_pairs']}))
