"""Local groups with useful exits. No central relocation, road additions or runtime write."""
import ast, collections, copy, hashlib, heapq, itertools, json, math, re
from pathlib import Path
import numpy as np
import skill_tree_contract_v8 as model

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview/proposals'
V9=BASE/'workshop-specialization-v9'
OUT=BASE/'workshop-growth-v11'
OUT.mkdir(exist_ok=True)
baseline=model.read(V9/'candidate-graph.json');g=copy.deepcopy(baseline);by={n['id']:n for n in g['nodes']}
src=(ROOT/'scripts/build-skill-tree-middle-choice-v7.py').read_text(encoding='utf8')
defs=[n for n in ast.parse(src).body if isinstance(n,(ast.FunctionDef,ast.ClassDef)) and n.name in {'xy','Geometry','inside'}]
exec(compile(ast.Module(body=defs,type_ignores=[]),'inspected-pure-geometry','exec'))
geom=Geometry();geom.refresh()
changes=[]
for id,stats,name,play in [
 ('v7g14n2',{'resource':10,'attackSpeed':4},'命中から資源を回収','通常攻撃を挟む手数と、所有する固有資源の獲得へ投資する。追加攻撃や資源の新規供給は行わない。'),
 ('v7g14n3',{'haste':8,'efficiency':4},'再使用と消費を整える','手持ちの技能の回転とMP負担へ投資する。追加pulseや新技能は供給しない。'),
 ('v7g06n3',{'hp':12,'armor':8},'容量を積んで前に残る','HP容量と装甲へ投資する。防具は職業を問わず部位ごとに自由混合できる。'),
 ('v7g06n5',{'regen':10,'resist':8},'回復を待ち、立て直す','自動再生と魔法防御へ投資する。HEAL技能や味方回復を与えず、再生が無効なKeyでは再生値が働かない。'),
 ('v7g14n0',{'resource':4,'hp':2},'外側で回収を補う','西側の一般経路から短く拾える資源獲得とHPの備え。近傍の専門群と一般幹線を直結する迂回路にはしない。'),
]:
 n=by[id];before=copy.deepcopy(n);n.update(stats=stats,name=name,desc=play,description=play,buildIntent=play,icon={'v7g14n2':'speed','v7g14n3':'sword','v7g06n3':'ward','v7g06n5':'temp_sanctuary','v7g14n0':'speed'}[id],benefitInputs={k:model.EFFECTIVE['statInputs'].get(k,[]) for k in stats},sourceState='growth_v11_proposal',runtimeApplied=False)
 changes.append({'id':id,'before':before,'after':copy.deepcopy(n)})
for gr in g['groups']:
 if gr['id']=='v7g14':gr.update(name='手数か、技の再使用か',role='通常命中の資源回収と、所有技能の再使用・MP負担を分けて育てる。')
 if gr['id']=='v7g06':gr.update(name='容量か、立て直しか',role='HP容量・装甲と、自動再生・魔法防御を選び分ける。')
removed=[e for e in g['edges'] if {e['a'],e['b']}=={'v7g14n0','v7g14n1'}];assert len(removed)==1
g['edges']=[e for e in g['edges'] if e not in removed];geom.refresh();added=[]
for a,b,role in [
 ('link_ranger_2_2','v7g14n1','回収と再使用の二つの育成目標へ進む。'),
 ('link_assassin_2_7','v7g06n0','装甲側から容量の群へ進む。'),
 ('link_assassin_2_7','v7g06n4','同じ装甲側から、容量を経由せず立て直しへ進む。'),
]:
 assert geom.clear(xy(a),xy(b),ignore=(a,b),clearance=70.5),(a,b)
 e=dict(a=a,b=b,road=False,proposalKind='growth-v11',role=role,points=[list(xy(a)),list(xy(b))],crossingGaps=[])
 g['edges'].append(e);added.append(copy.deepcopy(e));geom.refresh()
assert len(g['nodes'])==850 and len(g['edges'])==1011
assert all((n['x'],n['y'])==(by[n['id']]['x'],by[n['id']]['y']) for n in baseline['nodes'])
assert g['edges'][:len(baseline['edges'])-1]==[e for e in baseline['edges'] if e not in removed]

model.GRAPH=g;model.BY=by;model.ADJ={id:[] for id in by}
for e in g['edges']:model.ADJ[e['a']].append(e['b']);model.ADJ[e['b']].append(e['a'])
seen={model.ROOTS['warrior']};queue=list(seen)
for u in queue:
 for v in model.ADJ[u]:
  if v not in seen:seen.add(v);queue.append(v)
assert seen==set(by),'Removing a group entrance disconnected the graph'
examples=model.read(V9/'route-examples.json')
for e in examples:
 trial=model.example({k:e[k] for k in ['id','label','profileId','siphon','targets','budget','note']})
 assert trial['spent']==e['spent'],(e['id'],trial['spent'],e['spent'])
 assert trial['learned']==e['learned'],e['id']
 assert trial['stats']==e['stats'],e['id']

# Explicit staged bundles: preserve every investment of the 12-point build.
# Targets are role choices, including a partial secondary direction and survival.
menus=[
 ('warrior',0,'主力と再使用を伸ばす',['v7g14n3','v9_warrior_1_4','opening_warrior_2_2']),
 ('warrior',1,'通常命中から資源を回収',['v7g14n2','g13n9','v9_warrior_0_3','opening_warrior_2_1']),
 ('tank',0,'容量と装甲で前に残る',['v7g06n3','g46n6','v7g06n4']),
 ('tank',1,'守りを回し、立て直す',['v7g06n5','v7g14n3','v7g06n3']),
]
growth=[]
for job,arm,label,targets in menus:
 seed=next(e for e in examples if e['id']==f'{job}-direction-{arm}-12')
 chosen=model.example(dict(id=f'{job}-growth-{arm}-24',label=job+' / '+label+' / 同消費24pt',profileId=seed['profileId'],siphon=False,targets=seed['targets']+targets,budget=24,note='12ptの全配分を保持し、外側の主要目標・副方向・耐久へ12ptを追加。追加MODなし・通路HP。無効点や無目的な埋め草は購入しない。'))
 assert chosen['spent']==24 and not model.Allocation(chosen['profileId'],owned=chosen['learned']).dead(),(job,arm,chosen['spent'])
 assert set(seed['learned'])<=set(chosen['learned'])
 chosen.update(growth=True,baseExampleId=seed['id'],baseDirection=arm,direction=None,play=label,gear='既存の装備・技能の力を育てる。専用MODの新規付与や防具制限は行わない。',focusIds=chosen['learned'])
 growth.append(chosen)
 print(json.dumps({'id':chosen['id'],'spent':chosen['spent'],'remaining':chosen['remaining'],'targets':chosen['targets'],'stats':chosen['stats']},ensure_ascii=False),flush=True)
examples+=growth

def atlas(graph,mode):
 ns={n['id']:n for n in graph['nodes']};adj={id:[] for id in ns}
 for e in graph['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
 out={}
 for job,root in model.ROOTS.items():
  flags=set(model.PROFILES[('warrior' if mode=='fixed' else job)+':support:plain:standard']['flags']);d={root:0};q=[(0,root)]
  while q:
   c,u=heapq.heappop(q)
   if c!=d[u]:continue
   for v in adj[u]:
    n=ns[v];raw={'hp':2} if n['type']=='road' else n['stats']
    if n['type'] in ('start','keystone') or not any(set(model.EFFECTIVE['statInputs'].get(k,[]))<=flags for k in raw):continue
    nc=c+n['cost']
    if nc<d.get(v,math.inf):d[v]=nc;heapq.heappush(q,(nc,v))
  out[job]=d
 return out
fixtures={mode:dict(current=atlas(g,mode),previous=atlas(baseline,mode)) for mode in ['fixed','owned']}
fixed=fixtures['fixed']['current'];proof={id:{j:fixed[j].get(id) for j in fixed} for id in ['v7g14n2','v7g14n3','v7g06n3','v7g06n5']}
assert all(proof[id]['warrior']<proof[id]['tank'] for id in ['v7g14n2','v7g14n3'])
assert all(proof[id]['tank']<proof[id]['warrior'] for id in ['v7g06n3','v7g06n5'])
unions=[]
for job in ['warrior','tank']:
 es=[e for e in growth if e['profileId'].startswith(job+':')];union=set(es[0]['learned'])|set(es[1]['learned']);spent=sum(by[id]['cost'] for id in union);assert spent>24,(job,spent)
 unions.append({'job':job,'unionSpend':spent,'ids':[e['id'] for e in es]})
g.update(layoutStage='growth-v11',scope='既存の二群へ有用な専門出口。中央・旧座標と一般幹線を保持。群の旧入口1辺だけ付け替え。',runtimeApplied=False)
def write(name,obj):(OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf8')
write('candidate-graph.json',g);write('route-examples.json',examples);write('cost-atlas-fixtures.json',fixtures)
write('growth-authoring.json',dict(source='V9',runtimeApplied=False,nodeChanges=changes,addedEdges=added,removedEdges=removed,addedNodes=0,oldCoordinatesExact=True,otherOldEdgesExact=True,centralEdgesExact=True,sourceFilesChanged=False,groupGoalCosts=proof,two24PointUnionCosts=unions,rejectedBroadShortcut='V9 54/21/tie17 became Warrior65/Tank16/tie11; replaced with a local group entrance change.'))
minimum=math.inf
for e in added:
 a,b=xy(e['a']),xy(e['b']);v=b-a;mask=np.array([i not in (e['a'],e['b']) for i in geom.node_ids]);q=geom.N[mask]-a;t=np.clip((q@v)/(v@v),0,1);minimum=min(minimum,float(np.min(np.linalg.norm(q-t[:,None]*v,axis=1))))
assert minimum>=70.5
write('geometry-verification.json',dict(status='PASS',all850CoordinatesExact=True,centralPositionsAndIncidentEdgesExact=True,other1008EdgesExact=True,removedOuterGroupEntryEdges=1,addedEdges=3,addedNodes=0,addedRoads=0,newSegmentsCheckedForCrossingsAndOverlap=True,minNewWireToNonIncidentNodeDistance=minimum,all850NodesConnected=True,classRestrictedEdges=0))
print(json.dumps({'goals':proof,'unions':unions},ensure_ascii=False))
