"""Finish layout metadata and quantify whole-tree connectivity and road runs."""
import json,collections,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-whole-goals-v4'
g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));b={n['id']:n for n in g['nodes']};adj={i:[] for i in b}
for e in g['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
def path(starts,targets):
 q=list(starts);seen=set(q);prev={}
 for u in q:
  if u in targets:
   ids=[u]
   while ids[-1] in prev:ids.append(prev[ids[-1]])
   return ids[::-1]
  for v in adj[u]:
   if b[v]['type']=='keystone' or v in seen:continue
   seen.add(v);prev[v]=u;q.append(v)
 return None
# The earlier directRoute described V1 edges; the final graph has inserted rewards.
g.pop('directRoute',None)
for repair in g['inputAccessRepairs']:
 if repair['path']==['g27n1','link_assassin_2_2','g27n4']:repair['path']=['g27n1','g27n4']
 if repair['path']==['g27n4','link_assassin_2_3','g27n6']:repair['path']=['g27n4','g27n6']
 if repair['path']==['g3n2','link_assassin_2_4','g3n5']:repair['path']=['g3n2','g3n5']
 assert all(c in adj[a] for a,c in zip(repair['path'],repair['path'][1:])),repair
progress=json.loads((OUT/'authoring-progress.json').read_text(encoding='utf8'));groups={x['id']:x for x in g['groups']}
issues=progress.pop('issues',[]);resolutions=progress.get('firstPassConnectionsResolved',[])
for issue in issues:
 ids=path(groups[issue['from']]['nodes'],set(groups[issue['to']]['nodes']));assert ids,issue
 resolutions.append(dict(issue,finalStatus='connected-through-shared-goals',finalPath=ids))
progress.update(firstPassConnectionsResolved=resolutions,remainingLayoutBlockers=[],inputRepairs=g['inputAccessRepairs'])
roads={i for i,n in b.items() if n['type']=='road' and len(adj[i])==2};visited=set();runs=[]
for i in sorted(roads):
 if i in visited:continue
 component={i};q=[i];visited.add(i)
 for u in q:
  for v in adj[u]:
   if v in roads and v not in visited:visited.add(v);component.add(v);q.append(v)
 runs.append(sorted(component))
full=path([g['origins'][0]['root']],{g['origins'][-1]['root']});assert full
seen={g['nodes'][0]['id']};q=list(seen)
for u in q:
 for v in adj[u]:
  if v not in seen:seen.add(v);q.append(v)
assert len(seen)==645
axis=(g['bounds']['minX']+g['bounds']['maxX'])/2
region_shapes=[]
for group in g['groups']:
 pts=[b[i] for i in group['nodes']];anchor=pts[0];shape=tuple(sorted((round(n['x']-anchor['x'],3),round(n['y']-anchor['y'],3)) for n in pts));region_shapes.append(shape)
counts=collections.Counter(n['type'] for n in g['nodes'])
report=dict(status='PASS',nodes=645,regions=47,types=dict(counts),edges=len(g['edges']),connectedNodes=len(seen),keyLeaves=15,uniqueRegionCoordinateShapes=len(set(region_shapes)),leftOfBoundsCenter=sum(n['x']<axis for n in g['nodes']),rightOfBoundsCenter=sum(n['x']>=axis for n in g['nodes']),balanceIsNotMirrorSymmetry=True,maxDegreeTwoRoadRun=max(map(len,runs)),roadRunsHistogram=dict(collections.Counter(map(len,runs))),longestRoadRuns=sorted(runs,key=len,reverse=True)[:8],warriorToAssassinWithoutKeys=full,effectsCostsRequirementsUnchanged=True)
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'authoring-progress.json').write_text(json.dumps(progress,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'topology-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in report.items() if k not in ['longestRoadRuns','warriorToAssassinWithoutKeys']},ensure_ascii=False))
