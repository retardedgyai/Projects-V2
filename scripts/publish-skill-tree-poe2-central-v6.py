"""Package the complete 645-point proposal; original graph and earlier studies stay intact."""
import json, re, copy, hashlib, heapq, collections
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview'
OUT=BASE/'proposals/workshop-poe2-central-v6'
source=json.loads((BASE/'graph.json').read_text(encoding='utf8'))
graph=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'))
assert len(graph['nodes'])==645 and len(graph['groups'])==47
by={n['id']:n for n in graph['nodes']}; original={n['id']:n for n in source['nodes']}
for n in graph['nodes']:
 assert {k:v for k,v in n.items() if k not in ('x','y')}=={k:v for k,v in original[n['id']].items() if k not in ('x','y')}
adj={i:[] for i in by}
for e in graph['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
assert all(len(adj[n['id']])==1 for n in graph['nodes'] if n['type']=='keystone')
profiles={p['id']:p for p in source['profiles'] if p['weaponUsable']}
def route(owned,target,profile,siphon):
 flags=set(profile['flags'])|({'LIFESTEAL'} if siphon else set())
 root=next(o['root'] for o in source['origins'] if o['id']==profile['start'])
 def eligible(n):
  if n['type']=='start':return n['id']==root
  if n['type']=='keystone':return set(n['requirements'])<=flags and not set(n.get('conflicts',[]))&owned
  stats={'hp':2} if n['type']=='road' else n['stats']
  return any(set(source['statInputs'].get(k,[]))<=flags for k in stats)
 costs={i:0 for i in owned};prev={};q=[(0,i) for i in owned];heapq.heapify(q)
 while q:
  c,u=heapq.heappop(q)
  if costs[u]!=c:continue
  if u==target:
   path=[u]
   while path[-1] in prev:path.append(prev[path[-1]])
   return path[::-1]
  for v in adj[u]:
   n=by[v]
   if not eligible(n) or n['type']=='keystone' and v!=target and v not in owned:continue
   nc=c+(0 if v in owned else n['cost'])
   if nc<costs.get(v,float('inf')):costs[v]=nc;prev[v]=u;heapq.heappush(q,(nc,v))
 raise ValueError((profile['id'],target))
specs=[
 ('critical','会心へ投資','warrior:support:plain:standard',True,['g25n6','g48n4','g48n8'],'会心と倍率を積む。弱点入力は供給されないため、混合点の会心側が有効。'),
 ('quiet','貫通から静かな刃','warrior:support:plain:standard',True,['g37n0','g37n4','key_04'],'会心を失う代わりに直接ヒットを伸ばす原案。武器の一撃を育てる分岐。'),
 ('tank-life','生命と装甲','tank:support:plain:standard',False,['g6n0','g6n4','g18n1'],'装甲を短く拾うか、HPの目標まで回るか。回復入力のない点ではHP側だけを育てる。'),
 ('tank-shield','障壁と仲間','tank:support:plain:standard',False,['g31n4','key_11'],'手持ちの仲間向け障壁を育てる原案。障壁源を新しく与える点ではない。'),
 ('mage-mp','MPへ投資','mage:support:plain:standard',False,['g12n2','g20n6'],'MPの器と効率へ投資。同じ編成の技能に対するHP消費への転換も比較できる。'),
 ('mage-blood','MPから血の代価','mage:support:plain:standard',False,['g12n2','g20n6','key_02'],'MP消費を同量のHPに置換する原案。取得済みのMP・効率・再生は無効化し、点数と接続は保持。'),
 ('mage-ice','氷と障壁','mage:support:plain:standard',False,['g27n4','g3n5','key_13'],'既に使える氷と障壁を育てる。技能名だけで凍結を供給する扱いにはしない。'),
 ('ranger','会心と資源','ranger:mark:plain:standard',False,['g32n2','g39n6','key_10'],'印を利用する機会の返還へ。印を外した編成ではこのKeyを選べない。'),
 ('assassin','手数と範囲','assassin:mark:plain:standard',False,['g41n3','g42n0','g17n0'],'手数から範囲と会心へ。毒のKeyは選択肢として残し、取得を強制しない。'),
 ('lightning','既存の雷を単体へ','mage:support:lightning:standard',False,['g9n7','key_15'],'所有する雷MODの連鎖入力を使う原案。無属性の編成には連鎖を付与しない。'),
]
# Pick the original attack-speed Notable rather than a guessed node index.
speed=next(g for g in source['groups'] if g['id']=='g41')['notables'][0]
examples=[]
for id,label,pid,siphon,targets,note in specs:
 if id=='assassin':targets[0]=speed
 profile=profiles[pid];owned={next(o['root'] for o in source['origins'] if o['id']==profile['start'])};paths=[]
 for target in targets:
  path=route(owned,target,profile,siphon);owned.update(path);paths.append({'target':target,'nodes':path})
 flags=set(profile['flags'])|({'LIFESTEAL'} if siphon else set())
 lost={k for i in owned for k in source['lostStats'].get(by[i].get('rule'),[])}
 sums=collections.Counter()
 for i in owned:
  stats={'hp':2} if by[i]['type']=='road' else by[i]['stats']
  sums.update({k:v for k,v in stats.items() if k not in lost and set(source['statInputs'].get(k,[]))<=flags})
 cost=sum(by[i]['cost'] for i in owned)
 exampleBudget=48 if cost<=48 else 64 if cost<=64 else 80
 examples.append(dict(id=id,label=label,profileId=pid,siphon=siphon,targets=targets,note=note,learned=sorted(owned),paths=paths,spent=cost,stats=dict(sums),keys=sorted(i for i in owned if by[i]['type']=='keystone'),budget=exampleBudget,remaining=exampleBudget-cost))
 assert cost<=80,(id,cost)
prior=(BASE/'proposals/workshop-goal-study-v3/ProjectS_Goal_Routes_Study_V3.html').read_text(encoding='utf8')
priorPayload=json.loads(re.search(r'<script id="goalStudyData" type="application/json">(.*?)</script>',prior,re.S)[1])
icons=copy.deepcopy(priorPayload['icons'])
art=json.loads(re.search(r'const ART=(\{.*?\});',(BASE/'proposals/workshop-clear-wiring-v4/ProjectS_Passive_Clear_Wiring_V4.html').read_text(encoding='utf8'),re.S)[1])
semantic={'sword':'war_breach','speed':'whirl','shield':'war_guard','ward':'heal_shield','heart':'war_guard','leaf':'heal_light','target':'hunt_pierce','spear':'war_breach','boot':'hunt_retreat','crystal':'mage_ult','clock':'mage_mark','nova':'frost_nova','flame':'firebolt','snow':'frost_nova','bolt':'mage_burst','drop':'ass_poison','fang':'ass_ult','star':'mage_ult'}
for n in graph['nodes']:
 if n['type'] in ['start','keystone','notable'] and n['icon'] not in icons:icons[n['icon']]=art[n['icon'] if n['icon'] in art else semantic[n['icon']]]
import numpy as np
segments=[(e['a'],e['b'],a,c) for e in graph['edges'] for a,c in zip(e['points'],e['points'][1:])]
A=np.array([x[2] for x in segments]);D=np.array([np.array(x[3])-x[2] for x in segments]);den=(D*D).sum(axis=1);paintBounds={}
for n in graph['nodes']:
 p=np.array([n['x'],n['y']]);v=p-A;t=np.clip((v*D).sum(axis=1)/den,0,1);dist=np.sqrt(((v-t[:,None]*D)**2).sum(axis=1));dist[np.array([n['id'] in x[:2] for x in segments])]=float('inf')
 pair=min(float(np.linalg.norm(p-np.array([other['x'],other['y']]))) for other in graph['nodes'] if other['id']!=n['id'])
 paintBounds[n['id']]={'wire':float(np.min(dist)),'pair':pair}
# Reuse the inspected approved sprites: the existing skill keys are not semantic names.
icons['start_warrior']=art['war_guard']
icons['start_tank']=art['war_breach']
for oid,key in [('mage','star'),('ranger','target'),('assassin','fang')]:icons['start_'+oid]=icons[key]
central=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'))['center']
payload=dict(source=source,graph=graph,icons=icons,examples=examples,paintBounds=paintBounds,centralCenter=central)

fonts='\n'.join(re.findall(r'@font-face\{[^}]+\}',prior))
template=(ROOT/'scripts/skill-tree-poe2-central-v6-template.html').read_text(encoding='utf8')
html=template.replace('/*FONTS*/',fonts).replace('/*PAYLOAD*/',json.dumps(payload,ensure_ascii=False,separators=(',',':')))
(OUT/'ProjectS_PoE2_Central_V6.html').write_text(html,encoding='utf8')
(OUT/'route-examples.json').write_text(json.dumps(examples,ensure_ascii=False,indent=2),encoding='utf8')
manifest=dict(status='COMPLETE_LAYOUT_PROPOSAL',adopted=False,source645Sha256=hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),source645EmbeddedVerbatim=True,displayedNodes=645,displayedGroups=47,origins=5,keystones=15,connections=len(graph['edges']),full645LayoutCompleted=True,effectsCostsRequirementsUnchanged=True,runtimeApplied=False,earlierFilesChanged=False,sourceWeaponProfiles=60,inputReachabilityCases=120,saveScope='preview-only')
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'examples':[(e['id'],e['spent']) for e in examples],'htmlBytes':len(html.encode())},ensure_ascii=False))
