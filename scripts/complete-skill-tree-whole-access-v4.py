"""Complete original road budget with necessary input-specific access paths."""
import ast,copy,json,math
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-whole-goals-v4'
source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));original={n['id']:n for n in source['nodes']};g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in g['nodes']};progress=json.loads((OUT/'authoring-progress.json').read_text(encoding='utf-8'));corridors=progress['corridors'];roadpool=[n['id'] for n in source['nodes'] if n['type']=='road' and n['id'] not in by];ports={};repairs=[]
# Reuse only the scoped geometry functions, without executing its generator.
path=ROOT/'scripts/build-skill-tree-whole-goals-v4.py';tree=ast.parse(path.read_text(encoding='utf-8'));names={'put','edge','road','xy','segments','point_dist','intersects','clear_segment','clear_path','corridor','safe_insert_point','insert'}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),str(path),'exec'))
def direct(a,b,why,road_count=0):
 if any({e['a'],e['b']}=={a,b} for e in g['edges']):return
 if not clear_segment(xy(a),xy(b),(a,b)):raise ValueError('Shortcut crosses geometry: '+a+' '+b)
 if road_count:
  fake={'a':a,'b':b};positions=[xy(a)+(xy(b)-xy(a))*f for f in [.5,.43,.57]];p=next((p for p in positions if safe_insert_point(p,fake,'road')),None)
  if p is None:raise ValueError('No clear road in shortcut: '+a+' '+b)
  id=road(p);edge(a,id,'input-access');edge(id,b,'input-access');ids=[a,id,b]
 else:edge(a,b,'input-access');ids=[a,b]
 repairs.append({'why':why,'path':ids})
direct('g13n3','g13n5','HPを使うために吸収入力を強制しない',1)
direct('g17n4','g17n0','会心の混合Notableへ、弱点入力を通過させない',1)
direct('g27n1','g27n4','二つの混合目標へ、氷だけでも回れる道',1)
direct('g27n4','g27n6','障壁側の小さな目標へ、氷専用点を避ける',1)
direct('g3n2','g3n5','氷だけの入力から混合Notableへ届く',1)
direct('g27n1','g27n8','氷の下側の腕へ、障壁入力を避ける')
direct('g3n3','g3n5','障壁だけの入力から混合Notableへ届く')
direct('g47n0','g47n2','炎だけの入力から目標へ、APを自動供給しない')
gates={by[e['b']]['group']:e['a'] for e in g['edges'] if e['proposalKind']=='optional-input-region'}
direct(gates['g27'],'g27n2','氷の入口を一般の分岐から別に開く',1)
direct(gates['g3'],'g3n1','氷の小さな目標に障壁を通過させない',1)
def bent(a,b,p,why):
 if not clear_path([xy(a),np.array(p,float),xy(b)],a,b):
  ports['repair-a']=[a];ports['repair-b']=[b];before=set(by)
  if not corridor('repair-a','repair-b',why):raise ValueError('No clear access bend: '+a+' '+b)
  repairs.append({'why':why,'newNodes':sorted(set(by)-before)});return
 id=road(p);edge(a,id,'input-access');edge(id,b,'input-access');repairs.append({'why':why,'path':[a,id,b]})
bent('opening_mage_2_3','g12n3',(3300,-3020),'開始の混合耐性を使うために障壁や氷を必須にしない')
bent('r1_29_2','g13n8',(1200,-1000),'一般の通路を吸収・障壁の入力だけで閉じない')
assert len(by)==645 and set(by)==set(original),(len(by),roadpool)
for n in g['nodes']:assert {k:v for k,v in n.items() if k not in ['x','y']}=={k:v for k,v in original[n['id']].items() if k not in ['x','y']}
g['bounds']={'minX':min(n['x'] for n in g['nodes'])-170,'maxX':max(n['x'] for n in g['nodes'])+170,'minY':min(n['y'] for n in g['nodes'])-170,'maxY':max(n['y'] for n in g['nodes'])+170};g.update(fullTreeLayoutComplete=True,layoutStage='whole-645-access-complete',inputAccessRepairs=repairs,runtimeApplied=False)
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf-8');progress.update(stage=g['layoutStage'],nodes=len(by),regions=len(g['groups']),edges=len(g['edges']),unplaced=[],inputRepairs=repairs);(OUT/'authoring-progress.json').write_text(json.dumps(progress,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'nodes':len(by),'edges':len(g['edges']),'repairs':len(repairs),'roadsRemaining':len(roadpool)},ensure_ascii=False))
