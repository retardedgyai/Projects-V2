"""Check usable growth without crossing Keys or another profession's root."""
import collections,json,heapq
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-poe2-central-v6';s=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));b={n['id']:n for n in g['nodes']};origins={o['id']:o['root'] for o in s['origins']};adj={i:[] for i in b}
for e in g['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
cases=[];missing=collections.Counter();key_missing=collections.Counter()
for profile in s['profiles']:
 if not profile['weaponUsable']:continue
 for siphon in [False,True]:
  flags=set(profile['flags'])|({'LIFESTEAL'} if siphon else set());root=origins[profile['start']]
  eligible={i for i,n in b.items() if n['type']=='road' or n['type']=='start' and i==root or n['type'] not in ['start','keystone'] and any(set(s['statInputs'].get(k,[]))<=flags for k in n['stats'])}
  seen={root};queue=[root]
  for u in queue:
   for v in adj[u]:
    if v in eligible and v not in seen:seen.add(v);queue.append(v)
  prices={root:0};pending=[(0,root)]
  while pending:
   cost,u=heapq.heappop(pending)
   if prices[u]!=cost:continue
   for v in adj[u]:
    if v not in eligible:continue
    nc=cost+b[v]['cost']
    if nc<prices.get(v,float('inf')):prices[v]=nc;heapq.heappush(pending,(nc,v))
  keys=[]
  for i,n in b.items():
   if n['type']!='keystone':continue
   fed=set(n['requirements'])<=flags
   approaches=[prices[v]+n['cost'] for v in adj[i] if v in prices]
   cost=min(approaches) if fed and approaches else None
   if fed and cost is None:key_missing.update([i])
   keys.append({'id':i,'inputAvailable':fed,'newPointCost':cost})
  absent=sorted(eligible-seen);missing.update(absent);cases.append({'profile':profile['id'],'siphon':siphon,'reachable':len(seen),'eligible':len(eligible),'missing':absent,'keyGoals':keys})
report={'status':'PASS' if not missing and not key_missing else 'FIX-FIRST','cases':len(cases),'missingCounts':dict(missing),'missingAvailableKeyGoals':dict(key_missing),'fixtures':cases,'foreignWeaponInputsNotGranted':True,'keysNeverRequiredForTransit':True,'otherProfessionRootsNeverRequiredForTransit':True}
(OUT/'input-reachability-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':report['status'],'cases':len(cases),'missingCounts':dict(missing),'missingKeyGoals':dict(key_missing)},ensure_ascii=False))
