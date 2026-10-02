"""Author the full goal tree from retained V3 and original 645 records.

This script writes V4 only. Region shapes are explicit goal decisions in the
companion JSON; route planning checks real geometry, not a repeated cell layout.
"""
import collections,copy,json,math,sys
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-whole-goals-v4'
source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));original={n['id']:n for n in source['nodes']};gb={g['id']:g for g in source['groups']}
prior=BASE/'proposals/workshop-goal-study-v3';g=json.loads((prior/'candidate-region.json').read_text(encoding='utf-8'));plan=json.loads((prior/'full-tree-plan.json').read_text(encoding='utf-8'));reserve={r['id']:r for r in plan['regions']}
by={n['id']:n for n in g['nodes']};shapes=json.loads((ROOT/'scripts/skill-tree-whole-regions-v4.json').read_text(encoding='utf-8'));new_regions=[];corridors=[];issues=[]
def put(id,p):
 assert id not in by; n=dict(copy.deepcopy(original[id]),x=round(p[0],5),y=round(p[1],5));g['nodes'].append(n);by[id]=n;return id
def edge(a,b,role='growth'):
 assert a in by and b in by and a!=b
 if any({e['a'],e['b']}=={a,b} for e in g['edges']):return
 g['edges'].append(dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind=role,points=[[by[a]['x'],by[a]['y']],[by[b]['x'],by[b]['y']]],crossingGaps=[]))
for gid,shape in shapes.items():
 src=gb[gid];r=reserve[gid];assert len(shape['points'])==len(src['nodes'])
 for id,p in zip(src['nodes'],shape['points']):put(id,(r['x']+p[0],r['y']+p[1]))
 for a,b in shape['edges']:edge(src['nodes'][a],src['nodes'][b])
 ng=copy.deepcopy(src);ng.update(x=sum(by[i]['x'] for i in src['nodes'])/len(src['nodes']),y=sum(by[i]['y'] for i in src['nodes'])/len(src['nodes']),role=shape['purpose']);g['groups'].append(ng);new_regions.append(gid)
# Five source professions keep their own opening rewards and identity.
opening_positions={
 'tank':[[(-1130,-3070),(-1370,-2960),(-1530,-2770)],[(-1020,-3350),(-1250,-3550),(-1520,-3520)],[(-650,-3290),(-500,-3510),(-330,-3590)]],
 'mage':[[(3620,-3440),(3800,-3650),(3750,-3870)],[(3200,-3400),(2920,-3450),(2720,-3590)],[(3520,-3010),(3610,-2760),(3450,-2550)]],
 'ranger':[[(5760,330),(5720,130),(5840,-50)],[(5580,770),(5340,850),(5220,1100)],[(5930,430),(6120,490),(6300,590)]],
 'assassin':[[(5000,3770),(4800,3950),(4560,4040)],[(5110,3320),(4920,3140),(4970,2910)],[(5430,3680),(5680,3550),(5800,3320)]]}
entries={o['id']:o for o in plan['origins']}
entries['assassin'].update(x=5170,y=3600)
for o in source['origins']:
 if o['id']=='warrior':continue
 put(o['root'],(entries[o['id']]['x'],entries[o['id']]['y']))
 for lane,points in zip(o['lanes'],opening_positions[o['id']]):
  for id,p in zip(lane['nodes'],points):put(id,p)
  ids=[o['root']]+lane['nodes']
  for a,b in zip(ids,ids[1:]):edge(a,b,'opening')
g['origins']=copy.deepcopy(source['origins'])
# Keys are optional leaves; their inputs and tradeoffs are unchanged records.
key_positions={'key_02':(2970,-5240),'key_07':(-2450,-2100),'key_09':(1290,-5780),'key_10':(6900,1140),'key_11':(240,-3960),'key_12':(6050,3530),'key_13':(3180,-2710),'key_14':(5430,-3820),'key_15':(4660,-5040)}
for id,p in key_positions.items():put(id,p);edge(original[id]['anchor'],id,'optional-keystone')
# Previously unused historical hubs become optional growth, never fake roads.
hub_specs={'g3hub':((4490,-1800),['g3n2','g3n3']),'g10hub':((6810,1740),['g10n5','g10n4']),'g17hub':((6850,2950),['g17n6','g17n5']),'g24hub':((5050,-3050),['g24n4','g24n3']),'g31hub':((-320,-4400),['g31n1','g31n3']),'g38hub':((-1740,-930),['g38n1']),'g45hub':((-620,-520),['g45n2','g45n1']),'g52hub':((4230,470),['g52n0','g52n6'])}
for id,(p,neighbors) in hub_specs.items():
 put(id,p)
 for v in neighbors:edge(id,v,'source-hub-growth')
def save(stage):
 g['bounds']={'minX':min(n['x'] for n in g['nodes'])-170,'maxX':max(n['x'] for n in g['nodes'])+170,'minY':min(n['y'] for n in g['nodes'])-170,'maxY':max(n['y'] for n in g['nodes'])+170}
 g.update(source645Retained=True,fullTreeLayoutComplete=len(by)==645,runtimeApplied=False,scope='全47領域と5職業の共有育成。Keyは任意の末端、原効果・入力を保持。',layoutStage=stage)
 OUT.mkdir(parents=True,exist_ok=True);(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf-8');(OUT/'authoring-progress.json').write_text(json.dumps({'stage':stage,'nodes':len(by),'regions':len(g['groups']),'edges':len(g['edges']),'unplaced':[i for i in original if i not in by],'corridors':corridors,'issues':issues},ensure_ascii=False,indent=2),encoding='utf-8')
save('regions-and-origins')
print(json.dumps({'stage':'regions','nodes':len(by),'regions':len(g['groups']),'edges':len(g['edges']),'unplaced':len(original)-len(by)},ensure_ascii=False),flush=True)
if '--regions-only' in sys.argv:sys.exit(0)
roadpool=[n['id'] for n in source['nodes'] if n['type']=='road' and n['id'] not in by]
def road(p):
 if not roadpool:raise ValueError('Source road budget exhausted')
 return put(roadpool.pop(0),p)
gate_specs={'g27':((2160,-1750),'g27n0'),'g3':((3830,-1350),'g3n0'),'g47':((5050,-4310),'g47n0'),'g8':((5450,-2820),'g8n0')};gates={}
for gid,(p,child) in gate_specs.items():id=road(p);edge(id,child,'optional-input-region');gates[gid]=id
def stable(n):
 if n['type']=='road':return True
 return any(set(source['statInputs'].get(k,[]))<={'DAMAGE','MP_CONSUMER','RESOURCE'} for k in n['stats'])
ports={gid:[i for i in gb[gid]['nodes'] if stable(by[i])] for gid in gb}
for gid,id in gates.items():ports[gid]=[id]
for o in source['origins']:
 for li,lane in enumerate(o['lanes']):ports['@'+o['id']+':'+str(li)]=[lane['nodes'][-1]]
def xy(id):return np.array([by[id]['x'],by[id]['y']],dtype=float)
def segments():return [(e['a'],e['b'],np.array(e['points'][0],float),np.array(e['points'][-1],float)) for e in g['edges']]
def point_dist(p,a,b):
 v=b-a;t=max(0,min(1,float(np.dot(p-a,v))/max(float(np.dot(v,v)),1e-12)));return float(np.linalg.norm(p-a-t*v))
def intersects(a,b,c,d,shared=False):
 u=b-a;v=d-c;w=c-a;den=u[0]*v[1]-u[1]*v[0]
 if abs(den)>1e-8:
  t=(w[0]*v[1]-w[1]*v[0])/den;k=(w[0]*u[1]-w[1]*u[0])/den
  if -1e-7<=t<=1+1e-7 and -1e-7<=k<=1+1e-7:
   return not shared or 1e-7<t<1-1e-7 or 1e-7<k<1-1e-7
  return False
 if abs(w[0]*u[1]-w[1]*u[0])>1e-5:return False
 size=float(np.dot(u,u))
 if size<1e-9:return False
 t0=float(np.dot(w,u))/size;t1=float(np.dot(d-a,u))/size
 return min(1,max(t0,t1))-max(0,min(t0,t1))>1e-7
def clear_segment(a,b,end_ids=()):
 v=b-a;size=float(np.dot(v,v))
 if size<100:return False
 ns=[n for n in g['nodes'] if n['id'] not in end_ids];ps=np.array([[n['x'],n['y']] for n in ns]);t=np.clip((ps-a)@v/size,0,1);ds=np.linalg.norm(ps-a-t[:,None]*v,axis=1);limits=np.array([{'start':145,'keystone':110,'notable':90,'small':75,'road':65}[n['type']] for n in ns])
 if np.any(ds<limits):return False
 low=np.minimum(a,b)-60;high=np.maximum(a,b)+60
 for c,d,p,q in segments():
  if np.any(np.maximum(p,q)<low) or np.any(np.minimum(p,q)>high):continue
  shared=bool(set(end_ids)&{c,d})
  if intersects(a,b,p,q,shared):return False
  if not shared and min(point_dist(a,p,q),point_dist(b,p,q),point_dist(p,a,b),point_dist(q,a,b))<55:return False
 return True
def clear_path(path,a,b):
 for i,(p,q) in enumerate(zip(path,path[1:])):
  if not clear_segment(p,q,[a] if i==0 else [b] if i==len(path)-2 else []):return False
 for p in path[1:-1]:
  if min(float(np.linalg.norm(p-xy(id))) for id in by)<125:return False
 return True
def corridor(A,B,why):
 pairs=sorted([(float(np.linalg.norm(xy(a)-xy(b))),a,b) for a in ports[A] for b in ports[B]],key=lambda r:r[0])
 for length,a,b in pairs:
  if length>2100:continue
  if clear_segment(xy(a),xy(b),(a,b)):
   edge(a,b,'goal-corridor');corridors.append({'from':A,'to':B,'a':a,'b':b,'why':why,'path':[a,b]});return True
 for length,a,b in pairs[:15]:
  if length>2100:continue
  p,q=xy(a),xy(b);mid=(p+q)/2;v=q-p;normal=np.array([-v[1],v[0]])/max(length,1)
  bends=[mid+normal*k for k in [200,-200,400,-400,650,-650,950,-950]]+[np.array([p[0],q[1]]),np.array([q[0],p[1]])]
  for r in bends:
   path=[p,r,q]
   if float(np.linalg.norm(p-r)+np.linalg.norm(r-q))>2400:continue
   if clear_path(path,a,b):
    id=road(r);edge(a,id,'goal-corridor');edge(id,b,'goal-corridor');corridors.append({'from':A,'to':B,'a':a,'b':b,'why':why,'path':[a,id,b]});return True
 return False
requests=[(a,b,'異なる恩恵を拾って隣の目標へ') for a,b in plan['plannedNeighborPairs']]
opening_targets={'tank':['g18','g6','g31'],'mage':['g2','g12','g27'],'ranger':['g39','g52','g32'],'assassin':['g42','g41','g17']}
for o,targets in opening_targets.items():
 for li,target in enumerate(targets):requests.append(('@'+o+':'+str(li),target,'職業の開始恩恵から共有領域へ'))
requests.sort(key=lambda r:min(float(np.linalg.norm(xy(a)-xy(b))) for a in ports[r[0]] for b in ports[r[1]]))
for A,B,why in requests:
 if not corridor(A,B,why):issues.append({'from':A,'to':B,'reason':'no clear corridor in first pass'})
ports['g27:shield-entry']=['g27n4']
if corridor('@mage:2','g27:shield-entry','所有する障壁の開始恩恵から氷・障壁の目標へ'):
 issues=[i for i in issues if i['from']!='@mage:2']
save('neighborhood-corridors')
print(json.dumps({'stage':'corridors','nodes':len(by),'corridors':len(corridors),'unrouted':len(issues),'roadsRemaining':len(roadpool)},ensure_ascii=False),flush=True)
if '--corridors-only' in sys.argv:sys.exit(0)
def safe_insert_point(p,e,kind):
 ends={e['a'],e['b']}
 for id,n in by.items():
  limit=135 if kind=='small' else 120
  if n['type']=='start':limit+=35
  if float(np.linalg.norm(p-xy(id)))<limit:return False
 for f in g['edges']:
  if f is e:continue
  if point_dist(p,np.array(f['points'][0]),np.array(f['points'][-1]))<(80 if kind=='small' else 70):return False
 return True
def insert(e,id,p,role):
 a,b=e['a'],e['b'];g['edges'].remove(e);put(id,p);edge(a,id,role);edge(id,b,role)
 for c in corridors:
  for k in range(len(c['path'])-1):
   if {c['path'][k],c['path'][k+1]}=={a,b}:c['path'].insert(k+1,id);break
# Retain the nineteen warrior connection benefits near their original purposes.
preferences={'physical':(-2300,650),'hp':(-600,-50),'attackSpeed':(-1500,1100),'resource':(300,1400),'armor':(-200,520),'aoe':(1740,1500),'move':(-3300,-100)}
links=[n for n in source['nodes'] if n['id'].startswith('link_warrior') and n['type']=='small' and n['id'] not in by]
for n in links:
 k=next(iter(n['stats']));goal=np.array(preferences[k],float);options=[]
 for e in g['edges']:
  if by[e['a']]['type'] in ['start','keystone'] or by[e['b']]['type'] in ['start','keystone'] or e['proposalKind']=='opening':continue
  p,q=xy(e['a']),xy(e['b'])
  if np.linalg.norm(p-q)<300 or (p[0]+q[0])/2>2350:continue
  for f in [.43,.57,.5,.35,.65]:
   r=p+(q-p)*f
   if safe_insert_point(r,e,'small'):options.append((float(np.linalg.norm(r-goal)),e,r))
 if not options:raise ValueError('No useful free placement for '+n['id'])
 _,e,p=min(options,key=lambda r:r[0]);insert(e,n['id'],p,'source-connection-investment')
print(json.dumps({'stage':'source-investments','nodes':len(by),'links':len(links)},ensure_ascii=False),flush=True)
# Additional close goals create loops with useful rewards, not empty long roads.
extras=[('g44','g37'),('g11','g28'),('g18','g16'),('g18','g13'),('g31','g20'),('g46','g20'),('g33','g12'),('g33','g13'),('g27','g4'),('g24','g4'),('g2','g12'),('g43','g20'),('g9','g24'),('g8','g39'),('g32','g52'),('g41','g10'),('g7','g14'),('g42','g34'),('g17','g32')]
connected_pairs={tuple(sorted([c['from'],c['to']])) for c in corridors}
for A,B in extras:
 if tuple(sorted([A,B])) not in connected_pairs and corridor(A,B,'補完する恩恵を拾う別の経路'):connected_pairs.add(tuple(sorted([A,B])))
# Local alternatives share a real effect or a deliberate complementary purpose.
common={gid:{k for id in gb[gid]['nodes'] for k in by[id]['stats']} for gid in gb};candidate_pairs=[]
for ai,A in enumerate(gb):
 for B in list(gb)[ai+1:]:
  if tuple(sorted([A,B])) in connected_pairs or not(common[A]&common[B]):continue
  dist=min(float(np.linalg.norm(xy(a)-xy(b))) for a in ports[A] for b in ports[B])
  if 250<dist<1700:candidate_pairs.append((dist,A,B))
for _,A,B in sorted(candidate_pairs):
 if len(corridors)>=90:break
 if corridor(A,B,'同じ恩恵の別目標へ回る短い接続'):connected_pairs.add(tuple(sorted([A,B])))
print(json.dumps({'stage':'alternative-goals','nodes':len(by),'corridors':len(corridors),'roadsRemaining':len(roadpool)},ensure_ascii=False),flush=True)
def road_chain_ok(e):
 # Adding one road may not extend an existing degree-two road run beyond two.
 adj={i:[] for i in by}
 for f in g['edges']:adj[f['a']].append(f['b']);adj[f['b']].append(f['a'])
 total=1
 for start,other in [(e['a'],e['b']),(e['b'],e['a'])]:
  u,prev=start,other;seen=set()
  while by[u]['type']=='road' and len(adj[u])==2 and u not in seen:
   total+=1;seen.add(u);nxt=next(v for v in adj[u] if v!=prev);prev,u=u,nxt
 return total<=2
# Spend the original road budget only on short runs between real growth goals.
while roadpool:
 options=[]
 for e in g['edges']:
  if e['proposalKind']!='goal-corridor' or not road_chain_ok(e):continue
  a,b=xy(e['a']),xy(e['b']);length=float(np.linalg.norm(a-b))
  if length<270:continue
  for f in [.43,.57,.5]:
   p=a+(b-a)*f
   if safe_insert_point(p,e,'road'):options.append((length,e,p));break
 if not options:break
 _,e,p=max(options,key=lambda r:r[0]);id=roadpool.pop(0);insert(e,id,p,'goal-corridor')
print(json.dumps({'stage':'road-budget','nodes':len(by),'corridors':len(corridors),'roadsRemaining':len(roadpool)},ensure_ascii=False),flush=True)
save('whole-graph-routing')
