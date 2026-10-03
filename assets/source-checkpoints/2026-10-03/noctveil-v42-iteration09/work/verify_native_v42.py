from pathlib import Path
import json,sys,numpy as np
from PIL import Image,ImageDraw,ImageFont
import noctveil_v42_pose_tools as p
iteration=sys.argv[1] if len(sys.argv)>1 else 'iteration_03';report_path=p.ROOT/f'work/native_v42_arc_{iteration}.json';d=json.loads(report_path.read_text());model=json.loads((p.ROOT/d['path']).read_text());clip=model['animations'][0]
rows=[]
for s in d['samples']:
 mats=p.skeleton.transforms(model['outliner'][0],clip,s['time'],np.eye(4),{});world={};max_error=0
 for e in model['elements']:
  actual=np.array([s['world'][e['uuid']][k] for k in e['vertices']]);expected=(mats[e['uuid']][:3,:3]@np.asarray(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3]
  max_error=max(max_error,float(np.linalg.norm(actual-expected,axis=1).max()));world[e['name']]=actual
 rows.append({'time':s['time'],'maximum_native_software_vertex_error':max_error,'audit':p.audit(world)})
out=p.ROOT/'outputs/v42_path_study'/iteration
(out/'v42_native_sparse_verification.json').write_text(json.dumps({'native_version':d['loaded']['version'],'model_sha256':d['model_sha256'],'sparse_samples':rows,'geometry_uv_original3_clips_unchanged':all(model[k]==p.rig.source[k] for k in ['elements','outliner','textures']) and model['animations'][1:]==p.rig.source['animations'],'art_pass':False,'scope':'Native matrix/8 static sample checks only. No continuous proof or full-body collision pass.'},indent=2)+'\n',encoding='utf8')
print(json.dumps({'maximum_native_software_vertex_error':max(r['maximum_native_software_vertex_error'] for r in rows),'poses':len(rows),'textures':d['loaded']['textures']}))
