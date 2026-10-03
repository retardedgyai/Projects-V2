"""Independent allocation, opt-out, local-choice and preservation checks."""
import collections,heapq,json,math,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OLD=BASE/'proposals/workshop-poe2-central-v6';OUT=BASE/'proposals/workshop-middle-choice-v7'
s=json.loads((BASE/'graph.json').read_text(encoding='utf8'));old=json.loads((OLD/'candidate-graph.json').read_text(encoding='utf8'));new=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));examples=json.loads((OUT/'route-examples.json').read_text(encoding='utf8'));prior=json.loads((OLD/'route-examples.json').read_text(encoding='utf8'));p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));fixed=set(p['centralOpeningIds']);profiles={q['id']:q for q in s['profiles'] if q['weaponUsable']};roots={o['id']:o['root'] for o in s['origins']}

def graph_context(g,example,ban=()):
 by={n['id']:n for n in g['nodes']};ad={i:[] for i in by}
 for e in g['edges']:ad[e['a']].append(e['b']);ad[e['b']].append(e['a'])
 prof=profiles[example['profileId']];flags=set(prof['flags'])|({'LIFESTEAL'} if example['siphon'] else set());root=roots[prof['start']];targets=set(example['targets'])
 allowed={i for i,n in by.items() if n['group'] not in ban and (n['type']=='road' or n['type']=='start' and i==root or n['type']=='keystone' and i in targets and set(n['requirements'])<=flags or n['type'] not in ('start','keystone') and any(set(s['statInputs'].get(k,[]))<=flags for k in n['stats']))}
 return by,ad,flags,root,allowed

def shortest(owned,target,by,ad,allowed):
 dist={i:0 for i in owned};prev={};q=[(0,i) for i in owned];heapq.heapify(q)
 while q:
  c,u=heapq.heappop(q)
  if c!=dist[u]:continue
  if u==target:
   ids=[u]
   while ids[-1] in prev:ids.append(prev[ids[-1]])
   return ids[::-1]
  for v in ad[u]:
   if v not in allowed or by[v]['type']=='keystone' and v!=target:continue
   nc=c+(0 if v in owned else by[v]['cost'])
   if nc<dist.get(v,10**9):dist[v]=nc;prev[v]=u;heapq.heappush(q,(nc,v))
 return None

def allocation(g,e,ban=()):
 by,ad,flags,root,allowed=graph_context(g,e,ban);owned={root};paths=[]
 for target in e['targets']:
  ids=shortest(owned,target,by,ad,allowed)
  if ids is None:return None
  owned.update(ids);paths.append(ids)
 lost={k for i in owned for k in s['lostStats'].get(by[i].get('rule'),[])};stats=collections.Counter()
 for i in owned:
  stats.update({k:v for k,v in ({'hp':2} if by[i]['type']=='road' else by[i]['stats']).items() if k not in lost and set(s['statInputs'].get(k,[]))<=flags})
 return dict(spent=sum(by[i]['cost'] for i in owned),learned=sorted(owned),stats=dict(stats),paths=paths)

def exact(g,e,ban=()):
 by,ad,flags,root,allowed=graph_context(g,e,ban);seen={root};q=[root]
 for u in q:
  for v in ad[u]:
   if v in allowed and v not in seen:seen.add(v);q.append(v)
 terminals=list(dict.fromkeys([root,*e['targets']]))
 if not all(i in seen for i in terminals):return None
 ids=sorted(seen);idx={i:k for k,i in enumerate(ids)};cost=[by[i]['cost'] for i in ids];neighbors=[[idx[v] for v in ad[i] if v in idx] for i in ids];full=(1<<len(terminals))-1;dp=[[10**9]*len(ids) for _ in range(full+1)]
 for k,t in enumerate(terminals):dp[1<<k][idx[t]]=cost[idx[t]]
 for mask in range(1,full+1):
  part=(mask-1)&mask
  while part:
   other=mask^part
   if part<other:
    for v in range(len(ids)):dp[mask][v]=min(dp[mask][v],dp[part][v]+dp[other][v]-cost[v])
   part=(part-1)&mask
  q=[(c,v) for v,c in enumerate(dp[mask]) if c<10**9];heapq.heapify(q)
  while q:
   c,u=heapq.heappop(q)
   if c!=dp[mask][u]:continue
   for v in neighbors[u]:
    nc=c+cost[v]
    if nc<dp[mask][v]:dp[mask][v]=nc;heapq.heappush(q,(nc,v))
 return min(dp[full])

nb={n['id']:n for n in new['nodes']};ob={n['id']:n for n in old['nodes']}
assert all(n==nb[n['id']] for n in old['nodes'])
assert new['origins']==old['origins']
assert [e for e in old['edges'] if e['a'] in fixed or e['b'] in fixed]==[e for e in new['edges'] if e['a'] in fixed or e['b'] in fixed]
assert len(nb)==len(new['nodes'])
for n in new['nodes']:
 if n['id'] not in ob:
  assert n['cost']==1 and n['runtimeApplied'] is False and all(k in s['statInputs'] for k in n['stats'])
for n in new['nodes']:
 if n['type']=='keystone':assert sum(n['id'] in (e['a'],e['b']) for e in new['edges'])==1
for e in examples:
 if 'legacyRoute' not in e:
  a=allocation(new,e);assert a['spent']==e['spent'] and a['learned']==e['learned']
  for k,v in a['stats'].items():assert abs(v-e['stats'][k])<1e-8
 else:
  by,ad,flags,root,allowed=graph_context(new,e);owned=set(e['learned']);assert owned<=allowed and sum(by[i]['cost'] for i in owned)==e['spent'];seen={root};q=[root]
  for u in q:
   for v in ad[u]:
    if v in owned and v not in seen:seen.add(v);q.append(v)
  assert seen==owned and all(t in owned for t in e['targets'])

cases=[]
for prof in profiles.values():
 for siphon in (False,True):
  stub=dict(profileId=prof['id'],siphon=siphon,targets=[]);by,ad,flags,root,allowed=graph_context(new,stub);seen={root};q=[root]
  for u in q:
   for v in ad[u]:
    if v in allowed and v not in seen:seen.add(v);q.append(v)
  missing=sorted(allowed-seen);assert not missing,(prof['id'],siphon,missing)
  for n in new['nodes']:
   if n['type']=='keystone' and set(n['requirements'])<=flags:assert any(i in seen for i in ad[n['id']])
  cases.append(dict(profile=prof['id'],siphon=siphon,eligible=len(allowed),reachable=len(seen),missing=missing))

builds=[]
for before in prior:
 after=next(e for e in examples if e['id']==before['id']);row=dict(id=before['id'],budget=before['budget'],beforeSpent=before['spent'],afterSpent=after['spent'],delta=after['spent']-before['spent'],beforeMinimum=exact(old,before),afterMinimum=exact(new,after),beforeStats=before['stats'],afterStats=after['stats'],newJunctionsPurchased=[i for i in after['learned'] if i not in ob],newRegionNodesPurchased=[i for i in after['learned'] if i not in ob and nb[i]['group']],removedOldPurchases=[i for i in before['learned'] if i not in after['learned']],optOut=[])
 target_groups={nb[i]['group'] for i in after['targets']}
 for group in ('g13','g33','g12','g51'):
  if group in target_groups:continue
  was=exact(old,before,[group]);now=exact(new,after,[group]);row['optOut'].append(dict(region=group,beforeMinimum=was,afterMinimum=now,afterWithinBudget=now is not None and now<=after['budget']))
 builds.append(row)
assert all(next(x for x in b['optOut'] if x['region']=='g33')['afterWithinBudget'] for b in builds if b['id'] in ('ranger','assassin'))
assert all(next(x for x in b['optOut'] if x['region']=='g13')['afterWithinBudget'] for b in builds if b['id'] in ('critical','quiet'))
combined=[]
for e in examples:
 if e['id'] in ('ranger','assassin'):
  cost=exact(new,e,['g33','g12']);assert cost is not None and cost<=e['budget'];combined.append(dict(id=e['id'],avoidedRegions=['g33','g12'],minimumCost=cost,budget=e['budget']))

no_siphon=[]
for e in prior[:2]:
 alt=dict(e,siphon=False);was=exact(old,alt);now=exact(new,alt);no_siphon.append(dict(id=e['id'],beforeMinimum=was,afterMinimum=now,within48=now is not None and now<=48));assert now<=48

local=[]
for gid,pid in [('v7g02','warrior:support:plain:standard'),('v7g04','warrior:support:plain:standard'),('v7g07','mage:support:plain:standard'),('v7g10','assassin:mark:plain:standard'),('v7g13','ranger:mark:plain:standard')]:
 flags=set(profiles[pid]['flags']);arms=[]
 for indices in [(0,2,4),(1,3,4)]:
  stats=collections.Counter()
  for k in indices:stats.update({key:v for key,v in nb[gid+'n'+str(k)]['stats'].items() if set(s['statInputs'].get(key,[]))<=flags})
  arms.append(dict(nodes=[gid+'n'+str(k) for k in indices],cost=sum(nb[gid+'n'+str(k)]['cost'] for k in indices),effectiveStats=dict(stats)))
 assert arms[0]['cost']==arms[1]['cost']==3 and arms[0]['effectiveStats']!=arms[1]['effectiveStats'];local.append(dict(group=gid,profile=pid,arms=arms))

def neighborhood(g,e,owned,limit=4):
 by,ad,flags,root,allowed=graph_context(g,e);dist={i:0 for i in owned};q=[(0,i) for i in owned];heapq.heapify(q);goals=[]
 while q:
  c,u=heapq.heappop(q)
  if c!=dist[u] or c>limit:continue
  if u not in owned and by[u]['type']=='notable':goals.append(dict(id=u,extraPoints=c,stats={k:v for k,v in by[u]['stats'].items() if set(s['statInputs'].get(k,[]))<=flags}))
  for v in ad[u]:
   if v not in allowed or by[v]['type']=='keystone':continue
   nc=c+(0 if v in owned else by[v]['cost'])
   if nc<=limit and nc<dist.get(v,10**9):dist[v]=nc;heapq.heappush(q,(nc,v))
 return goals

progress=[]
for before in prior:
 after=next(e for e in examples if e['id']==before['id']);rows=[]
 for label,graph,e in [('before',old,before),('after',new,after)]:
  by={n['id']:n for n in graph['nodes']};sequence=list(dict.fromkeys(i for path in e['paths'] for i in path['nodes']))
  for checkpoint in (8,16,24,32):
   owned=set();spent=0
   for i in sequence:
    if spent+by[i]['cost']>checkpoint:break
    owned.add(i);spent+=by[i]['cost']
   goals=neighborhood(graph,e,owned)
   rows.append(dict(version=label,allocationProgress=checkpoint,spent=spent,withinNextFourPoints=goals))
 progress.append(dict(id=before['id'],checkpoints=rows))

concentration=[]
for label,graph,ex in [('before',old,prior),('after',new,examples[:10])]:
 by={n['id']:n for n in graph['nodes']};counter=collections.Counter(i for e in ex for i in e['learned'] if i not in fixed and by[i]['type']!='start');concentration.append(dict(version=label,top=[dict(id=i,uses=n,group=by[i]['group'],type=by[i]['type']) for i,n in counter.most_common(12)]))
ad={i:[] for i in nb}
for e in new['edges']:ad[e['a']].append(e['b']);ad[e['b']].append(e['a'])
# Compare the deliberately authored through-region alternatives between the
# same two purchased junctions. Unrelated global shortcuts are excluded here.
through=[]
for label,path in [
 ('neutral',['v7road_r9_4',*[f'v7road_r13_{k}' for k in range(1,5)],'r33_35_1']),
 ('area',['v7road_r9_4','v7g10_entry','v7g10n0','v7g10n2','v7g10n4','v7road_r14_1','v7road_r14_2','r33_35_1']),
 ('physical',['v7road_r9_4','v7g10_entry','v7g10n1','v7g10n3','v7g10n4','v7road_r14_1','v7road_r14_2','r33_35_1']),
]:
 assert all(b in ad[a] for a,b in zip(path,path[1:]))
 values=collections.Counter()
 for i in path[1:]:values.update({'hp':2} if nb[i]['type']=='road' else nb[i]['stats'])
 through.append(dict(route=label,nodes=path,additionalCost=sum(nb[i]['cost'] for i in path[1:]),effectiveStats=dict(values)))
assert [v['additionalCost'] for v in through]==[5,7,7]
report=dict(status='PASS',source645AndCoordinatesExact=True,central60AndIncidentEdgesExact=True,inputCases=cases,keyLeaves=15,exampleBuilds=builds,combinedOptOut=combined,noSiphonComparisons=no_siphon,equalCostLocalChoices=local,throughRegionAlternative=through,intermediateChoices=progress,sampleConcentration=concentration,roadDegrees=dict(collections.Counter(len(ad[i]) for i,n in nb.items() if n['type']=='road')),cautions=['Costs measure connectivity and supported passive values, not live combat DPS.','Guide examples are curated, not player telemetry.','Intermediate choices are reachable within the next four points, not already paid benefits.','V7 has its own preview save study ID; V6 codes and files are retained and are not silently migrated.'])
(OUT/'allocation-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'status':'PASS','cases':len(cases),'builds':[{k:b[k] for k in ['id','beforeSpent','afterSpent','delta','beforeMinimum','afterMinimum','optOut']} for b in builds],'noSiphon':no_siphon,'roadDegrees':report['roadDegrees'],'sameCostArms':len(local)},ensure_ascii=False))

# Use the independently implemented actual-polyline audit on this candidate.
audit=ROOT/'scripts/audit-skill-tree-poe2-central-v6.py';code=audit.read_text(encoding='utf8').replace('workshop-poe2-central-v6','workshop-middle-choice-v7').replace('nodes=645,placedNodes=645-len(unused)',"nodes=len(g['nodes']),placedNodes=len(g['nodes'])-len(unused)")
exec(compile(code,str(audit),'exec'),{'__file__':str(audit)})
assert json.loads((OUT/'geometry-verification.json').read_text(encoding='utf8'))['status']=='PASS'
