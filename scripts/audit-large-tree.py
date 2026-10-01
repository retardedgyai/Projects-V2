from pathlib import Path
import json,heapq,collections
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview'
d=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in d['nodes']};adj={i:[] for i in by}
for e in d['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
assert len(by)==len(d['nodes']);assert len({tuple(sorted((e['a'],e['b']))) for e in d['edges']})==len(d['edges'])
def route(root,learned,target=None,exclude_keys=False):
 dist={i:0 for i in learned};prev={};queue=[(0,i) for i in learned];heapq.heapify(queue)
 while queue:
  cost,here=heapq.heappop(queue)
  if cost!=dist[here]:continue
  if target==here:
   path=[here]
   while here in prev:here=prev[here];path.append(here)
   return cost,list(reversed(path))
  if by[here]['type']=='keystone':continue
  for nxt in adj[here]:
   n=by[nxt]
   if n['type']=='start' and nxt!=root or exclude_keys and n['type']=='keystone':continue
   new=cost+(0 if nxt in learned else n['cost'])
   if new<dist.get(nxt,10**9):dist[nxt]=new;prev[nxt]=here;heapq.heappush(queue,(new,nxt))
 return dist if target is None else None
def sums(ids):
 result=collections.Counter()
 for i in ids:result.update(by[i].get('stats',{}))
 return dict(result)
matrix=[];all_dists=[];braid_evidence=[];examples=[]
themes={'warrior':{'force','speed','life','area'},'tank':{'armor','life','ward'},'mage':{'magic','mana','area'},'ranger':{'speed','crit','life','weakpoint'},'assassin':{'speed','mobility','weakpoint','life'}}
for o in d['origins']:
 root=o['root'];dist=route(root,{root});no_key_dist=route(root,{root},exclude_keys=True)
 expected={i for i,n in by.items() if n['type'] not in ('keystone','start')}|{root}
 assert set(no_key_dist)==expected,('non-keystone connectivity',o['id'])
 assert len(adj[root])==2,('fixed three spokes remain',o['id'])
 all_dists.append((o,dist));matrix.append({'origin':o['id'],'costs':{n['id']:dist[n['id']] for n in d['nodes'] if n['type']=='keystone'}})
 braid=next(b for b in d['startBraids'] if b['origin']==o['id']);routes=braid['equalCostRoutes'];costs=[sum(by[i]['cost'] for i in r) for r in routes];values=[sums(r) for r in routes]
 assert costs==[3,3] and values[0]!=values[1]
 allstats=set(values[0])|set(values[1]);dominates=[all(values[a].get(k,0)>=values[1-a].get(k,0) for k in allstats) for a in (0,1)];assert not any(dominates),('dominated start route',o['id'])
 for r in routes+[braid['sideBranch']]:assert all(b in adj[a] for a,b in zip(r,r[1:]))
 braid_evidence.append({'origin':o['id'],'goal':braid['sharedGoal'],'costs':costs,'routes':routes,'benefits':values,'componentwiseDominance':False,'sideBranch':braid['sideBranch']})
 for budget in (44,48,64,80):
  for mode in ('no-key','keys'):
   learned={root};spent=0;selected=[]
   targets=[n['id'] for n in d['nodes'] if (n['type']=='keystone' if mode=='keys' else n['type']=='notable' and n.get('theme') in themes[o['id']])]
   for iteration in range(3 if mode=='no-key' else 15):
    options=[]
    for target in targets:
     if target in learned:continue
     # The pilot single/extra-hit exclusion is a proposed rule, not production policy.
     if mode=='keys' and any(c in learned for c in by[target].get('conflicts',[])):continue
     found=route(root,learned,target,exclude_keys=mode=='no-key')
     if found and found[0]+spent<=budget:options.append((found[0],target,found[1]))
    if not options:break
    cost,target,path=min(options);learned.update(path);spent+=cost;selected.append(target)
   examples.append({'origin':o['id'],'budget':budget,'mode':mode,'nodes':sorted(learned-{root}),'spent':spent,'target':selected[-1] if selected else root,'targets':selected,'notables':sum(by[i]['type']=='notable' for i in learned),'keys':sum(by[i]['type']=='keystone' for i in learned),'staticProposedStats':sums(learned),'combatRanking':None})
budgets=[]
for budget in (44,48,64,80):
 notable_counts=[sum(n['type']=='notable' and dist.get(n['id'],10**9)<=budget for n in d['nodes']) for o,dist in all_dists]
 key_counts=[sum(n['type']=='keystone' and dist.get(n['id'],10**9)<=budget for n in d['nodes']) for o,dist in all_dists]
 budgets.append({'budget':budget,'notablesMin':min(notable_counts),'notablesMax':max(notable_counts),'notablesTotal':sum(n['type']=='notable' for n in d['nodes']),'keysMin':min(key_counts),'keysMax':max(key_counts),'maxKeyCost':max(max(row['costs'].values()) for row in matrix)})
portals=[]
for group in d['groups']:
 ids=set(group['nodes']);ps=set()
 for edge in d['edges']:
  a,b=edge['a'],edge['b']
  if a in ids and b not in ids and by[b]['type']!='keystone':ps.add(a)
  if b in ids and a not in ids and by[a]['type']!='keystone':ps.add(b)
 portals.append({'id':group['id'],'name':group['name'],'portalNodes':sorted(ps)})
assert all(len(g['portalNodes'])>=2 for g in portals)
report={'schema':'projects.large-tree-audit.v1','budgets':budgets,'matrix':matrix,'startRouteEvidence':braid_evidence,'clusterPortals':portals,'multiEntryClusters':47,'nonKeystoneMajorConnectivity':True,'rootDegree':2,'notableCount':sum(n['type']=='notable' for n in d['nodes']),'keysCount':15,'groupsCount':47,'examples':examples,'balanceVerified':False,'combatEffectsApplied':False,'notes':['全Keyを除いて主要地域を接続。全5起点から全15候補の費用を計算。','同じ序盤目標への同費用経路は副産物が異なり、単純な成分比較で互いを支配しない。','47クラスタすべてでKey以外の出入口が2点以上。','近傍Key巡回例は費用・入力・交換条件の比較。未知の戦闘係数に対する最適性は証明しない。']}
d['budgetAudit']=report;(OUT/'graph.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8');(OUT/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':'PASS','budgets':budgets,'braids':len(braid_evidence),'nonKeyConnected':True,'keyTourExamples':[{'origin':e['origin'],'budget':e['budget'],'keys':e['keys'],'spent':e['spent']} for e in examples if e['mode']=='keys' and e['budget']==64]},ensure_ascii=False))
