from pathlib import Path
import json,heapq,collections
from large_tree_rules import node_input,profile_key,FLAG_KINDS
from large_tree_topology import chain_report,continuation_report,adjacency,old_route
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview'
d=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in d['nodes']};adj={i:[] for i in by}
for e in d['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
assert len(by)==len(d['nodes']);assert len({tuple(sorted((e['a'],e['b']))) for e in d['edges']})==len(d['edges'])
def route(root,learned,target=None,exclude_keys=False,allowed=None):
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
   if allowed is not None and nxt not in allowed:continue
   new=cost+(0 if nxt in learned else n['cost'])
   if new<dist.get(nxt,10**9):dist[nxt]=new;prev[nxt]=here;heapq.heappush(queue,(new,nxt))
 return dist if target is None else None
def sums(ids,p=None):
 result=collections.Counter()
 for i in ids:result.update(node_input(d,by[i],p,ids,by=by)['usable'] if p else by[i].get('stats',{}))
 return dict(result)
matrix=[];all_dists=[];braid_evidence=[];examples=[];source_checks=[];entrances=[]
allowed_cache={}
def allowed_for(p,state):
 keys=tuple(sorted(i for i in state if by[i]['type']=='keystone'))
 cache_key=(p['id'],keys)
 if cache_key not in allowed_cache:allowed_cache[cache_key]={i for i,n in by.items() if node_input(d,n,p,keys,by=by)['present']}
 return allowed_cache[cache_key]
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
 first=sorted((dist[i],i) for i,n in by.items() if n.get('group') and n['type'] not in ('keystone','start'))[0]
 entrances.append({'origin':o['id'],'firstClusterPoint':first[1],'cost':first[0]})
 p=next(p for p in d['profiles'] if p['start']==o['id'] and p['kit']=='basic' and p['element']=='plain' and p['weaponMode']=='standard')
 for budget in (44,48,64,80):
  for mode in ('no-key','keys'):
   learned={root};spent=0;selected=[]
   stages=['keystone','notable','frontier'] if mode=='keys' else ['notable','frontier']
   for stage in stages:
    for iteration in range(len(by)):
     options=[]
     for target,n in by.items():
      if target in learned or n['type']=='start':continue
      if stage=='keystone' and n['type']!='keystone':continue
      if stage=='notable' and (n['type']!='notable' or n.get('theme') not in themes[o['id']]):continue
      if stage=='frontier' and (n['type']=='keystone' or not any(j in learned for j in adj[target])):continue
      if any(c in learned for c in n.get('conflicts',[])):continue
      prospective=learned|({target} if n['type']=='keystone' else set())
      allowed=allowed_for(p,prospective)|{root}
      if not prospective<=allowed:continue
      if target not in allowed:continue
      found=route(root,learned,target,exclude_keys=mode=='no-key',allowed=allowed)
      if found and found[0]+spent<=budget:
       preference=0 if n.get('theme') in themes[o['id']] else 1
       options.append((found[0],preference,target,found[1]))
     if not options:break
     cost,_,target,path=min(options);learned.update(path);spent+=cost;selected.append(target)
     if spent==budget:break
    if spent==budget:break
   assert spent==budget,('unequal comparison spend',o['id'],mode,budget,spent)
   assert all(node_input(d,by[i],p,learned,by=by)['present'] for i in learned if i!=root)
   focus=next((i for i in reversed(selected) if by[i]['type'] in ('keystone','notable')),root)
   examples.append({'origin':o['id'],'budget':budget,'mode':mode,'profile':profile_key(p),'nodes':sorted(learned-{root}),'spent':spent,'target':focus,'targets':selected,'notables':sum(by[i]['type']=='notable' for i in learned),'keys':sum(by[i]['type']=='keystone' for i in learned),'usableProposedStats':sums(learned,p),'fullyInactivePoints':0,'combatRanking':None,'generationPolicy':'費用と地域テーマによる例。Key候補→共通Notable→使える隣接投資。強さ/Paretoの最適化ではない。'})
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
for p in d['profiles']:
 if not p['weaponUsable']:continue
 root=next(o['root'] for o in d['origins'] if o['id']==p['start'])
 for loss in ((),('key_04',),('key_02',),('key_04','key_02')):
  usable={i for i,n in by.items() if n['type'] not in ('keystone','start') and node_input(d,n,p,loss,by=by)['present']}|{root}
  reached=route(root,{root},exclude_keys=True,allowed=usable)
  assert set(reached)==usable,('dead mandatory toll',p['id'],loss,sorted(usable-set(reached)))
  cr=chain_report(d,usable);assert cr['maxDegree2Interiors']<=2,('source valid long chain',p['id'],loss,cr)
  continuing=continuation_report(d,usable)
  source_checks.append({'profile':p['id'],'loss':list(loss),'usableNodes':len(usable),'unreachableUsableNodes':0,'maxDegree2Interiors':cr['maxDegree2Interiors'],
   'continuingMaxDegree2Interiors':continuing['maxDegree2Interiors'],'terminalRewardBranches':continuing['terminalRewardBranches']})
assert chain_report(d)['maxDegree2Interiors']<=2
input_tests=[]
war=next(p for p in d['profiles'] if p['id']=='warrior:basic:plain:standard')
bleed_node=next(n['id'] for n in d['nodes'] if n.get('group')=='g50' and 'bleed' in n.get('stats',{}))
for flag,ids in [('WEAKPOINT',['opening_ranger_2_3']),('BLEED_SOURCE',[bleed_node,'key_03']),('HEAL',['g33n2','g33n1']),('CROSS_WEAPON_SKILL',['key_07'])]:
 for i in ids:
  n=by[i];before=node_input(d,n,war,by=by);after=node_input(d,n,war,extra=[flag],by=by)
  assert before!=after,(flag,i)
  input_tests.append({'flag':flag,'node':i,'baseline':before,'hypothetical':after,'kind':FLAG_KINDS[flag],'implemented':False})
for root,target,forbidden in [('origin_warrior','key_04',{'g33n2','g33n4'}),('origin_warrior','g37n4',{'g33n2','g33n4'}),('origin_mage','key_13',{'g27n0','g27n3','g27n6','g27n9'})]:
 p=next(p for p in d['profiles'] if p['start']==('mage' if root=='origin_mage' else 'warrior') and p['kit']=='basic' and p['element']=='plain' and p['weaponMode']=='standard')
 allowed={i for i,n in by.items() if node_input(d,n,p,by=by)['present']}|{root}
 found=route(root,{root},target,allowed=allowed);assert found and not(set(found[1])&forbidden),(target,found)
 input_tests.append({'regressionTarget':target,'profile':p['id'],'cost':found[0],'path':found[1],'forbiddenAvoided':sorted(forbidden)})
d['chainReview'].pop('proposedMaxDecisionSteps',None)
d['chainReview']['continuing']=continuation_report(d)
d['chainReview']['sourceContinuingMaxDegree2Interiors']=max(c['continuingMaxDegree2Interiors'] for c in source_checks)
d['chainReview']['after']['definition']=chain_report(d)['definition']
d['chainReview']['before']['definition']=chain_report(d)['definition']
report={'schema':'projects.large-tree-audit.v2','budgets':budgets,'matrix':matrix,'startRouteEvidence':braid_evidence,'clusterPortals':portals,'multiEntryClusters':47,'nonKeystoneMajorConnectivity':True,'rootDegree':2,'notableCount':sum(n['type']=='notable' for n in d['nodes']),'keysCount':15,'groupsCount':47,'examples':examples,'sourceChecks':source_checks,'inputTests':input_tests,'chainReview':d['chainReview'],'firstClusterEntrances':entrances,'baselineFlagCounts':{f:sum(f in p['flags'] for p in d['profiles']) for f in FLAG_KINDS},'balanceVerified':False,'combatEffectsApplied':False,'notes':['全Keyを除いて主要地域を接続。費用は構造上の最短値で、戦闘バランスの証明ではない。','終端報酬を含む枝の次数と、別経路へ続く道の次数を区別する。次の継続先まで3pt以内とは断定しない。','使える恩恵を一つも持たない点を除いても、使える目標へ到達可能。','全48/64/80比較例は同じ支出かつ入力を持つ仮投資。64は画面初期値。係数・実戦強さ・Pareto最適性は未検証。']}
d['budgetAudit']=report;(OUT/'graph.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8');(OUT/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':'PASS','budgets':budgets,'braids':len(braid_evidence),'nonKeyConnected':True,'keyTourExamples':[{'origin':e['origin'],'budget':e['budget'],'keys':e['keys'],'spent':e['spent']} for e in examples if e['mode']=='keys' and e['budget']==64]},ensure_ascii=False))
