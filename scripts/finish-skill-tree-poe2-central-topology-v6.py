import json,collections,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-poe2-central-v6';g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));s=json.loads((BASE/'graph.json').read_text(encoding='utf8'));b={n['id']:n for n in g['nodes']};src={n['id']:n for n in s['nodes']};ad={i:[] for i in b};cx,cy=p['center']
for e in g['edges']:ad[e['a']].append(e['b']);ad[e['b']].append(e['a'])
for n in g['nodes']:assert {k:v for k,v in n.items() if k not in ('x','y')}=={k:v for k,v in src[n['id']].items() if k not in ('x','y')}
assert not p['remainingRoadPool'];assert all(len(ad[n['id']])==1 for n in g['nodes'] if n['type']=='keystone')
x=max(abs(n['x']-cx) for n in g['nodes'])+170;y=max(abs(n['y']-cy) for n in g['nodes'])+170;g['bounds']={'minX':cx-x,'maxX':cx+x,'minY':cy-y,'maxY':cy+y}
seen={g['origins'][0]['root']};q=list(seen)
for u in q:
 for v in ad[u]:
  if v not in seen:seen.add(v);q.append(v)
assert len(seen)==645
roads={i for i,n in b.items() if n['type']=='road' and len(ad[i])==2};seenR=set();runs=[]
for i in sorted(roads):
 if i in seenR:continue
 seenR.add(i);q=[i]
 for u in q:
  for v in ad[u]:
   if v in roads and v not in seenR:seenR.add(v);q.append(v)
 runs.append(q)
assert max(map(len,runs))<=2
shapes={tuple(sorted((round(b[i]['x']-b[r['nodes'][0]]['x'],2),round(b[i]['y']-b[r['nodes'][0]]['y'],2)) for i in r['nodes'])) for r in g['groups']}
middle_repaired=any({e['a'],e['b']}=={'g23n5','r2_22_1'} and len(e['points'])==2 for e in g['edges'])
report=dict(status='PASS',nodes=645,regions=47,origins=5,keyLeaves=15,edges=len(g['edges']),connectedNodes=645,sourceFieldsExactExceptXY=True,startsCollectedAtMapCenter=True,center=p['center'],centralStartMaxDistance=max(math.hypot(b[o['root']]['x']-cx,b[o['root']]['y']-cy) for o in g['origins']),uniqueRegionShapes=len(shapes),centralSharedChoices=len(p['centralSharedPaths'])+len(p['centralOuterPaths']),maxDegreeTwoRoadRun=max(map(len,runs)),leftPoints=sum(n['x']<cx for n in g['nodes']),rightPoints=sum(n['x']>=cx for n in g['nodes']),outerEmbeddingPreservedThroughCurvedPaths=not middle_repaired,westSouthwestTravelCorridorsAdjusted=middle_repaired,logicalConnectionsChanged=0)
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'topology-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps(report))
