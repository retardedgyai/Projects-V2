"""Move three neutral points onto true region entrances, preserving all effects."""
import ast,copy,json,math
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-whole-goals-v4';source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));original={n['id']:n for n in source['nodes']};g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in g['nodes']};progress=json.loads((OUT/'authoring-progress.json').read_text(encoding='utf-8'));corridors=progress['corridors'];roadpool=[];ports={}
path=ROOT/'scripts/build-skill-tree-whole-goals-v4.py';tree=ast.parse(path.read_text(encoding='utf-8'));names={'put','edge','road','xy','segments','point_dist','intersects','clear_segment','clear_path','corridor','safe_insert_point','insert'};exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),str(path),'exec'))
repairs=[];target_ids={'link_assassin_2_2','link_assassin_2_3','link_assassin_2_4'}
shared=[i for i,n in by.items() if i not in target_ids and n['type'] not in ['start','keystone'] and (n['type']=='road' or any(set(source['statInputs'].get(k,[]))<={'DAMAGE','MP_CONSUMER','RESOURCE'} for k in n['stats']))]
specs=[('link_assassin_2_2',['g27n1','g27n4'],['g27n0','g27n1']),('link_assassin_2_3',['g27n4','g27n6'],['g27n4','g27n5']),('link_assassin_2_4',['g3n2','g3n5'],['g3n1','g3n2'])]
for id,old,new in specs:
 incident=[e for e in g['edges'] if id in [e['a'],e['b']]]
 if any(e.get('proposalKind')=='shared-region-entrance' for e in incident):continue
 assert len(incident)==2
 for e in incident:g['edges'].remove(e)
 parent=next(e for e in g['edges'] if {e['a'],e['b']}==set(new));g['edges'].remove(parent)
 p=(xy(new[0])+xy(new[1]))/2;by[id].update(x=float(p[0]),y=float(p[1]))
 assert clear_segment(xy(old[0]),xy(old[1]),old),(id,'original shortcut')
 edge(*old,'input-access');edge(new[0],id,'input-access');edge(id,new[1],'input-access')
 candidates=sorted(shared,key=lambda i:float(np.linalg.norm(xy(i)-p)));chosen=next((v for v in candidates if clear_segment(p,xy(v),(id,v))),None)
 if chosen is None:raise ValueError('No true neutral entrance for '+id)
 edge(id,chosen,'shared-region-entrance');repairs.append({'road':id,'boundary':new,'sharedEntry':chosen,'why':'一般の通路は氷・障壁の入力がなくても入口から取得できる'})
for n in g['nodes']:assert {k:v for k,v in n.items() if k not in ['x','y']}=={k:v for k,v in original[n['id']].items() if k not in ['x','y']}
g['neutralGatewayRepairs']=repairs;g['layoutStage']='whole-645-shared-entrances';(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf-8');progress.update(stage=g['layoutStage'],edges=len(g['edges']),neutralGatewayRepairs=repairs);(OUT/'authoring-progress.json').write_text(json.dumps(progress,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'nodes':len(by),'edges':len(g['edges']),'repairs':repairs},ensure_ascii=False))
