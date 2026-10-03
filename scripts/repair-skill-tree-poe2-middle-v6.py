"""Straighten only the authored west/southwest middle corridors.

All central nodes and regional node coordinates remain exact. Existing road
junctions move to explicit offset targets when the full embedding stays clear.
Every original logical connection is retained. No effects, route costs, input
conditions, icons, or source IDs change.
Run after finish-skill-tree-poe2-central-topology-v6.py, before publication.
"""
import json,copy,math
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6';g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));baseline=copy.deepcopy(g);p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));fixed=set(p['centralOpeningIds']);by={n['id']:n for n in g['nodes']};C=np.array(p['center']);regional={i for r in g['groups'] for i in r['nodes']};region_of={i:r['id'] for r in g['groups'] for i in r['nodes']}
def segments():
 rows=[(ei,a,b) for ei,e in enumerate(g['edges']) for a,b in zip(e['points'],e['points'][1:])];return rows,np.array([r[1] for r in rows]),np.array([r[2] for r in rows])
def corridor_clear(a,b,ei,clearance=72):
 rows,A,B=segments();D=B-A;v=b-a;w=A-a;den=v[0]*D[:,1]-v[1]*D[:,0];nz=np.abs(den)>1e-8;t=np.zeros(len(rows));u=t.copy();t[nz]=(w[nz,0]*D[nz,1]-w[nz,1]*D[nz,0])/den[nz];u[nz]=(w[nz,0]*v[1]-w[nz,1]*v[0])/den[nz];mask=np.array([r[0]!=ei for r in rows]);hits=mask&nz&(t>1e-7)&(t<1-1e-7)&(u>-1e-7)&(u<1+1e-7)
 if np.any(hits):return False
 e=g['edges'][ei];nodes=np.array([[n['x'],n['y']] for n in g['nodes'] if n['id'] not in (e['a'],e['b'])]);q=nodes-a;t=np.clip((q@v)/max(float(v@v),1e-9),0,1)
 return np.min(np.linalg.norm(q-t[:,None]*v,axis=1))>=clearance
def in_band(e):
 if e['a'] in fixed or e['b'] in fixed:return False
 if e['a'] in region_of and region_of.get(e['a'])==region_of.get(e['b']):return False
 q=np.array(e['points'])-C;x=q[:,0];y=q[:,1];return np.all((x>-4300)&(x<700)&(y>-1800)&(y<3900)) and np.any(np.linalg.norm(q,axis=1)<4100)
changes=[]
# Replace the common circular deformation with individually checked chords.
for ei,e in enumerate(g['edges']):
 if not in_band(e) or len(e['points'])<3:continue
 a,b=np.array(e['points'][0]),np.array(e['points'][-1])
 if corridor_clear(a,b,ei):
  changes.append({'edge':[e['a'],e['b']],'action':'clear-direct-corridor','beforeSegments':len(e['points'])-1});e['points']=[a.tolist(),b.tolist()]
# Different facing corners for the western, southwest, and southern travel.
targets={'r2_22_1':(-2820,-350),'r1_29_1':(-2400,1970),'r6_35_2':(-650,1650),'r1_29_2':(120,2590),'r1_29_5':(-1370,3210),'r1_29_6':(-830,3500),'r1_47_1':(-100,3100),'r1_47_2':(420,3140),'r16_37_2':(-2130,1130),'r19_24_2':(-1700,2800),'r24_42_1':(-2220,2610)}
accepted=[];blocked=[]
for id,target in targets.items():
 assert id not in fixed and id not in regional and by[id]['type']=='road';n=by[id];old=np.array([n['x'],n['y']]);want=C+np.array(target);incident=[(ei,e,copy.deepcopy(e['points'])) for ei,e in enumerate(g['edges']) if id in (e['a'],e['b'])];ok=False
 for scale in (1,.75,.5,.25):
  q=old+(want-old)*scale
  for ei,e,pts in incident:e['points']=[q.tolist(),[by[e['b']]['x'],by[e['b']]['y']]] if e['a']==id else [[by[e['a']]['x'],by[e['a']]['y']],q.tolist()]
  n['x'],n['y']=q.tolist()
  if all(corridor_clear(np.array(e['points'][0]),np.array(e['points'][-1]),ei,80) for ei,e,_ in incident):ok=True;accepted.append({'node':id,'from':old.tolist(),'to':q.tolist(),'targetScale':scale});break
 if not ok:
  n['x'],n['y']=old.tolist()
  for ei,e,pts in incident:e['points']=pts
  blocked.append(id)
assert all(n==by[n['id']] for n in baseline['nodes'] if n['id'] in fixed|regional)
assert [(e['a'],e['b']) for e in g['edges']]==[(e['a'],e['b']) for e in baseline['edges']]
report={'status':'AUTHORED_PENDING_GEOMETRY_AND_BROWSER_CHECK','scope':'west / southwest middle coordinates and rendered travel paths only','central60Exact':True,'all47RegionalNodeCoordinatesExact':True,'all47InternalRegionEdgesExact':True,'sourceFieldsExact':True,'all784LogicalEdgesExact':True,'logicalConnectionsChanged':0,'directCorridors':changes,'offsetRoadJunctions':accepted,'blockedTargetsRetained':blocked}
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'middle-band-authoring.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({'directCorridors':len(changes),'offsetJunctions':accepted,'blocked':blocked}))
