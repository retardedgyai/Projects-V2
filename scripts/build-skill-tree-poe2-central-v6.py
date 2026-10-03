"""Apply the inspected PoE2 central principle to five unchanged ProjectS starts.

Only the common central opening is regular. Forty-seven unequal outer regions
retain the authored V4 planar embedding through an injective radial expansion.
"""
import ast,copy,json,math,collections
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-poe2-central-v6';OUT.mkdir(parents=True,exist_ok=True)
prior=json.loads((BASE/'proposals/workshop-whole-goals-v4/candidate-graph.json').read_text(encoding='utf8'));source=json.loads((BASE/'graph.json').read_text(encoding='utf8'));original={n['id']:n for n in source['nodes']};g=copy.deepcopy(prior);by={n['id']:n for n in g['nodes']};opening={o['root'] for o in source['origins']}|{i for o in source['origins'] for l in o['lanes'] for i in l['nodes']}
C=np.array([1230.,-1245.]);EXPANSION=1800.;CORE=200.
def warp(p):
 p=np.asarray(p,dtype=float);delta=p-C;r=float(np.linalg.norm(delta))
 if r==0:return C.copy()
 # The source face has >223 world clearance, so all retained wires lie outside CORE.
 scale=(1+EXPANSION/CORE) if r<=CORE else 1+EXPANSION/r
 return C+delta*scale
def xy(id):return np.array([by[id]['x'],by[id]['y']],dtype=float)
def coord(p):return [round(float(p[0]),5),round(float(p[1]),5)]
retained=[]
for e in g['edges']:
 if e['a'] in opening or e['b'] in opening:continue
 points=[]
 for a,b in zip(e['points'],e['points'][1:]):
  a=np.array(a);b=np.array(b);count=max(1,math.ceil(float(np.linalg.norm(b-a))/25))
  points.extend(coord(warp(a+(b-a)*j/count)) for j in range(count))
 points.append(coord(warp(e['points'][-1])));e['points']=points;e['crossingGaps']=[];retained.append(e)
g['edges']=retained
for n in g['nodes']:
 if n['id'] not in opening:n['x'],n['y']=coord(warp((n['x'],n['y'])))
adj={i:set() for i in by}
for e in g['edges']:adj[e['a']].add(e['b']);adj[e['b']].add(e['a'])
# Reuse the road ends left by the former distributed starts. Their effects and IDs stay intact.
pool=[]
while True:
 leaves=sorted(i for i in by if i not in opening and i not in pool and by[i]['type']=='road' and len(adj[i])==1)
 if not leaves:break
 for id in leaves:
  parent=next(iter(adj[id]));adj[parent].remove(id);adj[id].clear();g['edges']=[e for e in g['edges'] if id not in (e['a'],e['b'])];pool.append(id)
# A few original degree-two roads are moved from outer travel into central junctions.
while len(pool)<28:
 choices=sorted((float(np.linalg.norm(xy(i)-C)),i) for i in by if i not in opening and i not in pool and by[i]['type']=='road' and len(adj[i])==2)
 _,id=choices[0];a,b=sorted(adj[id]);ea=next(e for e in g['edges'] if {e['a'],e['b']}=={a,id});eb=next(e for e in g['edges'] if {e['a'],e['b']}=={id,b});pa=ea['points'] if ea['a']==a else ea['points'][::-1];pb=eb['points'] if eb['a']==id else eb['points'][::-1]
 g['edges']=[e for e in g['edges'] if id not in (e['a'],e['b'])];g['edges'].append(dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind='retained-outer-route',points=pa+pb[1:],crossingGaps=[]));adj[a].remove(id);adj[b].remove(id);adj[a].add(b);adj[b].add(a);adj[id].clear();pool.append(id)
initial_pool=pool.copy()

def xy(id):return np.array([by[id]['x'],by[id]['y']],dtype=float)

def coord(p):return [round(float(p[0]),5),round(float(p[1]),5)]

def edge(a,b,role='central-opening',points=None):
 assert a!=b and not any({e['a'],e['b']}=={a,b} for e in g['edges']),(a,b)
 g['edges'].append(dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind=role,points=points or [coord(xy(a)),coord(xy(b))],crossingGaps=[]));adj[a].add(b);adj[b].add(a)

def road(p):
 assert pool,'Original road budget exhausted';id=pool.pop(0);by[id]['x'],by[id]['y']=coord(p);return id

def segments():
 rows=[]
 for ei,e in enumerate(g['edges']):
  for a,b in zip(e['points'],e['points'][1:]):rows.append((ei,e['a'],e['b'],a,b))
 return rows

def distance(p,a,b):
 d=b-a;den=float(d@d);t=np.clip(float((p-a)@d)/max(1e-9,den),0,1);return float(np.linalg.norm(p-a-t*d))

def clear(a,b,ignore=()):
 # Actual polylines, not endpoint chords, are checked.
 for id,n in by.items():
  if id in ignore or id in pool:continue
  if distance(np.array([n['x'],n['y']]),a,b)<({'start':135,'keystone':100,'notable':85,'small':72,'road':65}[n['type']]):return False
 rows=segments();A=np.array([x[3] for x in rows]);B=np.array([x[4] for x in rows]);d=b-a;v=B-A;w=A-a;den=d[0]*v[:,1]-d[1]*v[:,0];nz=np.abs(den)>1e-7
 t=np.zeros(len(rows));u=np.zeros(len(rows));t[nz]=(w[nz,0]*v[nz,1]-w[nz,1]*v[nz,0])/den[nz];u[nz]=(w[nz,0]*d[1]-w[nz,1]*d[0])/den[nz]
 if np.any(nz&(t>1e-6)&(t<1-1e-6)&(u>-1e-6)&(u<1+1e-6)):return False
 return True

def connect(a,b,bends=(),role='central-shared-choice'):
 points=[xy(a)]+[C+np.array(p) for p in bends]+[xy(b)]
 for j,(p,q) in enumerate(zip(points,points[1:])):
  ignore=[a,b]
  if not clear(p,q,ignore):raise ValueError(('Blocked new path',a,b,j,coord(p),coord(q)))
 ids=[a]+[road(p) for p in points[1:-1]]+[b]
 for a,b in zip(ids,ids[1:]):edge(a,b,role)
 repairs.append(dict(path=ids,role=role));return ids
repairs=[];angles={'warrior':162,'tank':234,'mage':306,'ranger':18,'assassin':90};RADIUS=900.;VOID=858.
centralIds=set(opening)
def place(id,p):by[id]['x'],by[id]['y']=coord(p)
def basis(o):
 a=math.radians(angles[o['id']]);return np.array([math.cos(a),math.sin(a)]),np.array([-math.sin(a),math.cos(a)])
for o in source['origins']:
 u,v=basis(o);place(o['root'],C+u*RADIUS)
 for li,(lane,pts) in enumerate(zip(o['lanes'],[[(1080,-130),(1350,-255),(1650,-360)],[(1080,130),(1350,255),(1650,360)],[(1330,0),(1585,0),(1840,0)]])):
  for id,(r,t) in zip(lane['nodes'],pts):place(id,C+u*r+v*t)
  for a,b in zip(lane['nodes'],lane['nodes'][1:]):edge(a,b,'regular-central-opening')
  if li<2:edge(o['root'],lane['nodes'][0],'two-first-exits')
 # The third choice branches after the two initial choices, rather than adding a long spoke.
 for li in [0,1]:edge(o['lanes'][li]['nodes'][1],o['lanes'][2]['nodes'][0],'short-central-branch')
 # Mixed final rewards can be reached without traversing missing AP/SHIELD inputs.
 for li in [0,1]:edge(o['lanes'][li]['nodes'][2],o['lanes'][2]['nodes'][2],'short-central-choice')

# Adjacent short openings join around the void. No learned path enters it.
ordered=sorted(source['origins'],key=lambda o:angles[o['id']])
belt=[]
for k,o in enumerate(ordered):
 nxt=ordered[(k+1)%5];a=angles[o['id']];end=angles[nxt['id']]
 if end<a:end+=360
 bends=[]
 for f in [1/3,2/3]:
  th=math.radians(a+12.3+(end-a-24.6)*f);bends.append(coord(np.array([math.cos(th),math.sin(th)])*1810))
 path=connect(o['lanes'][1]['nodes'][2],nxt['lanes'][0]['nodes'][2],bends,'shared-central-neighbor-route');centralIds.update(path[1:-1]);belt.append(dict(fromClass=o['id'],toClass=nxt['id'],path=path))

# Local outer entrances are chosen from the irregular source face, not five radial rails.
def split_outer(a,b,target):
 e=next(e for e in g['edges'] if {e['a'],e['b']}=={a,b});pts=e['points'] if e['a']==a else e['points'][::-1];target=C+np.array(target);choices=sorted((np.linalg.norm(np.array(p)-target),j) for j,p in enumerate(pts[2:-2],2))
 for _,j in choices:
  p=np.array(pts[j])
  if any(i not in pool and distance(p,xy(i),xy(i))<120 for i in by):continue
  id=road(p);g['edges'].remove(e);adj[a].remove(b);adj[b].remove(a);edge(a,id,'shared-central-boundary',pts[:j+1]);edge(id,b,'shared-central-boundary',pts[j:]);return id
 raise ValueError(('No boundary insertion',a,b))

def attach(a,b):
 if clear(xy(a),xy(b),(a,b)):return connect(a,b,role='central-to-outer-goal')
 # One-bend paths remain short and are checked against every real segment.
 pa,pb=xy(a),xy(b);d=pb-pa;normal=np.array([-d[1],d[0]])/np.linalg.norm(d)
 for f in [.5,.35,.65]:
  for off in [180,-180,350,-350,550,-550,800,-800]:
   p=pa+d*f+normal*off
   if any(i not in pool and np.linalg.norm(p-xy(i))<120 for i in by):continue
   if any(distance(p,np.array(r[3]),np.array(r[4]))<95 for r in segments()):continue
   if clear(pa,p,(a,b)) and clear(p,pb,(a,b)):return connect(a,b,[coord(p-C)],role='central-to-outer-goal')
 raise ValueError(('No clear central exit',a,b))
left=split_outer('g13n8','g33n0',[-2050,-680]);upper=split_outer('g33n3','g51n6',[1670,-1450])
attach('opening_warrior_0_3','g13n8')
wb=next(x for x in belt if x['fromClass']=='warrior')['path'][1];attach(wb,left)
attach('opening_mage_1_3',upper)
attach('opening_ranger_1_3','g51n6')
attach('opening_assassin_1_3','r1_29_2')

g.update(fullTreeLayoutComplete=False,scope='PoE2実参照に沿い、中央余白を囲む等半径72度の5起点と短い2出口を再構成。外側47領域は異なる形を保持。',layoutStage='poe2-central-v6-authored',runtimeApplied=False)
g.pop('inputAccessRepairs',None);g.pop('neutralGatewayRepairs',None)
progress=dict(center=coord(C),expansion=EXPANSION,originalOpeningIds=sorted(opening),reusedRoadIds=initial_pool,remainingRoadPool=pool,centralSharedPaths=repairs,centralOuterPaths=[],regularStartRadius=RADIUS,centralVoidRadius=VOID,regularAngles=angles,centralOpeningIds=sorted(centralIds),neighborBelt=belt,outerTransform='injective radial expansion of unequal V4 embedding with actual sampled polylines',oldV4Retained=True,reference=dict(data='https://github.com/grindinggear/poe2-skilltree-export',viewer='https://cvenzin.github.io/poe2-skilltree/',viewerVersion='0.5.1',officialDataSha256='b52be9c4f17e4114064255ef1b8c58292e9db0e395d95af235a8d3fef0d44642',adaptation='PoE2 six shared positions become five ProjectS positions at 72 degrees; effects are not copied'))
for group in g['groups']:group['x']=sum(by[i]['x'] for i in group['nodes'])/len(group['nodes']);group['y']=sum(by[i]['y'] for i in group['nodes'])/len(group['nodes'])
for n in g['nodes']:assert {k:v for k,v in n.items() if k not in ('x','y')}=={k:v for k,v in original[n['id']].items() if k not in ('x','y')}
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'central-authoring.json').write_text(json.dumps(progress,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'nodes':len(g['nodes']),'edges':len(g['edges']),'remainingRoads':len(pool),'startRadius':RADIUS,'voidRadius':VOID}))
