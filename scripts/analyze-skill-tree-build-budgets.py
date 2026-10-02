"""Concrete same-loadout examples, not a combat optimizer or balance verdict."""
import collections,heapq,json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-clear-wiring-v3'
source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'))
graphs={'original':source,'previous':json.loads((BASE/'proposals/workshop-structure-v2/candidate-graph.json').read_text(encoding='utf-8')),'proposal':json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'))}
profile=next(p for p in source['profiles'] if p['id']=='warrior:support:plain:standard')
job=next(p['job'] for p in source['currentCatalog']['profiles'] if p['start']=='warrior' and p['kit']=='support')
by={n['id']:n for n in source['nodes']}
def benefits(id,flags,choice='hp',keys=()):
    n=by[id];lost=set(k for key in keys for k in source['lostStats'].get(by[key].get('rule'),[]));stats=next(c['stats'] for c in source['travelChoices'] if c['id']==choice) if n['type']=='road' else n['stats']
    return {k:v for k,v in stats.items() if not lost.intersection([k]) and all(f in flags for f in source['statInputs'].get(k,[]))}
def enabled(id,flags,cls,choice='hp',keys=()):
    n=by[id]
    if n['type']=='start':return True
    if n['type']=='keystone':return all(f in flags for f in n.get('requirements',[])) and (not n.get('referenceJobs') or cls in n['referenceJobs'])
    return bool(benefits(id,flags,choice,keys))
def model(g):
    adj={id:[] for id in by}
    for e in g['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
    return adj
def search(adj,sources,root,keys=(),usable=None,inclusive=False,owned=()):
    prices={id:math.inf for id in by};previous={};queue=[];owned=set(owned)
    for id in sources:prices[id]=by[id]['cost'] if inclusive else 0;heapq.heappush(queue,(prices[id],id))
    while queue:
        cost,u=heapq.heappop(queue)
        if cost!=prices[u]:continue
        for v in adj[u]:
            n=by[v]
            if n['type']=='start' and v!=root or n['type']=='keystone' and v not in keys and v not in owned or usable is not None and v not in usable and v not in owned:continue
            new=cost+(0 if v in owned else n['cost'])
            if new<prices[v]:prices[v]=new;previous[v]=u;heapq.heappush(queue,(new,v))
    return prices,previous
def path(previous,target):
    result=[target]
    while result[-1] in previous:result.append(previous[result[-1]])
    return list(reversed(result))
def pair_tree(adj,root,keys,usable=None):
    searches=[search(adj,[id],root,keys,usable,True) for id in [root]+list(keys)]
    meet=min(by,key=lambda id:sum(d[id] for d,p in searches)-2*by[id]['cost']);cost=sum(d[meet] for d,p in searches)-2*by[meet]['cost']
    if not math.isfinite(cost):return None,None
    tree=set(i for d,p in searches for i in path(p,meet));assert sum(by[i]['cost'] for i in tree)==cost
    return cost,tree
def sums(ids,flags,choice,keys=()):
    result=collections.Counter()
    for id in ids:result.update(benefits(id,flags,choice,keys))
    return {k:round(v,5) for k,v in result.items()}
policies={
 'weapon':{'label':'武器特化','road':'damage','themes':[['force'],['penetration'],['speed'],['crit'],['area'],['haste','resource']], 'stats':['physical','damage','penetration','attackSpeed','crit','critMulti']},
 'survival':{'label':'生存特化','road':'hp','themes':[['life','armor'],['resist','ward'],['leech'],['healing'],['mobility']], 'stats':['hp','armor','resist','regen','barrier','leech']},
 'two_key':{'label':'2Key混合','road':'hp','themes':[['leech'],['life','armor'],['force'],['haste','resource']], 'stats':['hp','armor','resist','barrier','leech','physical','resource']}}
builds=[]
for version,g in graphs.items():
    adj=model(g);root=next(o['root'] for o in source['origins'] if o['id']=='warrior')
    for budget in [48,64,80]:
        for strategy,policy in policies.items():
            flags=set(profile['flags'])|{'LIFESTEAL'};keys=['key_01','key_05'] if strategy=='two_key' else [];usable={id for id in by if enabled(id,flags,job,policy['road'],keys)};owned={root};checkpoints=[]
            if keys:
                initial,tree=pair_tree(adj,root,keys,usable)
                if tree is None or initial>budget:
                    structural_cost,structural_tree=pair_tree(adj,root,keys)
                    builds.append({'version':version,'budget':budget,'strategy':strategy,'label':policy['label'],'status':'unavailable','reason':'Required input blocks the connection path' if tree is None else 'Valid route exceeds budget','usablePairCost':initial,'structuralPairCost':structural_cost,'structuralInactiveNodes':[{'id':i,'name':by[i]['name'],'stats':by[i]['stats']} for i in sorted(structural_tree) if i not in usable]})
                    continue
                owned=tree;checkpoints.append({'target':keys,'cost':initial,'added':sorted(tree-{root})})
            for themes in policy['themes']:
                while True:
                    prices,prev=search(adj,owned,root,keys,usable,owned=owned);candidates=[n['id'] for n in g['nodes'] if n['type']=='notable' and n.get('theme') in themes and n['id'] not in owned and n['id'] in usable and prices[n['id']]+sum(by[i]['cost'] for i in owned)<=budget]
                    if not candidates:break
                    target=min(candidates,key=lambda id:(prices[id],id));fresh=set(path(prev,target))-owned;owned|=fresh;checkpoints.append({'target':target,'cost':sum(by[i]['cost'] for i in fresh),'added':sorted(fresh)})
            while sum(by[i]['cost'] for i in owned)<budget:
                frontier={v for u in owned for v in adj[u] if v not in owned and by[v]['type'] not in ['start','keystone'] and v in usable and benefits(v,flags,policy['road'],keys).keys()&set(policy['stats']) and sum(by[i]['cost'] for i in owned)+by[v]['cost']<=budget}
                if not frontier:break
                target=min(frontier,key=lambda id:(by[id]['type']=='road',by[id]['type']!='notable',next((j for j,k in enumerate(policy['stats']) if k in benefits(id,flags,policy['road'],keys)),999),id));owned.add(target);checkpoints.append({'target':target,'cost':by[target]['cost'],'added':[target]})
            price=sum(by[i]['cost'] for i in owned);roads={id:policy['road'] for id in owned if by[id]['type']=='road'}
            assert price<=budget and not any(by[i]['type']=='start' and i!=root for i in owned)
            seen={root};q=[root]
            for u in q:
                for v in adj[u]:
                    if v in owned and v not in seen:seen.add(v);q.append(v)
            assert seen==owned
            builds.append({'version':version,'budget':budget,'strategy':strategy,'label':policy['label'],'spent':price,'remaining':budget-price,'stats':sums(owned,flags,policy['road'],keys),'notables':[{'id':i,'name':by[i]['name']} for i in sorted(owned) if by[i]['type']=='notable'],'counts':dict(collections.Counter(by[i]['type'] for i in owned)), 'keys':keys,'tradeoffs':[by[i]['tradeoff'] for i in keys],'keyDescriptions':[by[i]['description'] for i in keys], 'checkpoints':checkpoints,'buildCode':{'version':source['version'],'budget':budget,'origin':'warrior','learned':sorted(owned),'mastery':{},'travel':roads,'defaultTravel':policy['road']}})
outliers=[]
for origin,keys in [('ranger',['key_01','key_05']),('mage',['key_02','key_09'])]:
    p=next(p for p in source['profiles'] if p['id']==origin+':basic:plain:standard');flags=set(p['flags'])|{'LIFESTEAL'};root=next(o['root'] for o in source['origins'] if o['id']==origin);row={'origin':origin,'keys':keys,'versions':[]}
    for version,g in graphs.items():
        cost,tree=pair_tree(model(g),root,keys);before=sums(tree,flags,'hp');after=sums(tree,flags,'hp',keys);inactive=[i for i in tree if by[i]['type'] not in ['start','keystone'] and not benefits(i,flags,'hp',keys)]
        row['versions'].append({'version':version,'cost':cost,'counts':dict(collections.Counter(by[i]['type'] for i in tree)),'beforeRuleLossStats':before,'afterRuleLossStats':after,'completelyInactiveAfterKeys':inactive,'notables':[{'id':i,'name':by[i]['name']} for i in sorted(tree) if by[i]['type']=='notable'],'tree':sorted(tree)})
    outliers.append(row)
report={'runtimeApplied':False,'combatBalanceVerdict':'not_assessed','sameLoadout':{k:profile[k] for k in ['id','weapon','resource','equippedSkills','flags']},'siphonAssumedOwned':True,'siphonSource':source['currentCatalog']['siphonSource'],'selectionMethod':'目的分野のNotableを増分費用順で選び、残りを使える隣接恩恵へ割り当てる具体例。最適DPS・実戦生存の探索ではない。','hp15PercentKeyPenaltyIncludedInStats':False,'dodgeLossNotConvertedIntoNumericPower':True,'builds':builds,'outliers':outliers}
(OUT/'build-budget-comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'builds':len(builds),'v3_64':[{k:b[k] for k in ['strategy','status','reason','structuralPairCost','structuralInactiveNodes','spent','remaining','stats','counts'] if k in b} for b in builds if b['version']=='proposal' and b['budget']==64],'outliers':[{k:r[k] for k in ['origin','keys']}|{'versions':[{k:v[k] for k in ['version','cost','counts','completelyInactiveAfterKeys']} for v in r['versions']]} for r in outliers]},ensure_ascii=False))
