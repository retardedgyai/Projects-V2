"""Retain useful mixed opening rewards and neutral outer entrances without new inputs."""
import ast,json,math
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-poe2-central-v6';s=json.loads((BASE/'graph.json').read_text(encoding='utf8'));g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));by={n['id']:n for n in g['nodes']};pool=[];C=np.array(p['center']);adj={i:set() for i in by};repairs=[]
for e in g['edges']:adj[e['a']].add(e['b']);adj[e['b']].add(e['a'])
path=ROOT/'scripts/build-skill-tree-poe2-central-v6.py';tree=ast.parse(path.read_text(encoding='utf8'));names={'xy','coord','edge','segments','distance','clear','connect','road'};exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),str(path),'exec'))
# Mixed opening rewards already connect via the short outer triangle.
common=set.intersection(*(set(x['flags']) for x in s['profiles'] if x['weaponUsable']))
def generic(n):return n['type']=='road' or n['type'] not in ('start','keystone') and any(set(s['statInputs'].get(k,[]))<=common for k in n['stats'])
a='link_assassin_2_3';missing={'link_assassin_2_3','link_assassin_0_3'}
choices=sorted((float(np.linalg.norm(xy(i)-xy(a))),i) for i,n in by.items() if i not in missing and generic(n) and i not in adj[a])
for d,id in choices:
 if d>2500:raise ValueError('No nearby generic entrance')
 if clear(xy(a),xy(id),(a,id)):
  connect(a,id,role='neutral-entrance-without-ice-or-shield');break
else:raise ValueError('No free neutral entrance')
p['inputGateRepairs']=repairs
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'central-authoring.json').write_text(json.dumps(p,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({'repairs':repairs,'edges':len(g['edges'])}))
