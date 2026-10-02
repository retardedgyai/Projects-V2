"""Continue the retained V2 with distinct critical and no-critical goals.

Only V3 paths are written. Original effects and all earlier milestones stay intact.
"""
import collections,copy,hashlib,heapq,json,math,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-goal-study-v3'
source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));original={n['id']:n for n in source['nodes']};gb={g['id']:g for g in source['groups']}
prior=BASE/'proposals/workshop-goal-study-v2';g=json.loads((prior/'candidate-region.json').read_text(encoding='utf-8'));nodes=g['nodes'];by={n['id']:n for n in nodes};new_roads=[];new_groups=[]
roadpool=[n['id'] for n in source['nodes'] if n['type']=='road' and n['id'] not in by]
def put(id,p):
 assert id not in by; n=dict(copy.deepcopy(original[id]),x=p[0],y=p[1]);nodes.append(n);by[id]=n;return id
def edge(a,b,role='growth'):
 assert a in by and b in by and not any({e['a'],e['b']}=={a,b} for e in g['edges']);g['edges'].append(dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind=role,points=[[by[a]['x'],by[a]['y']],[by[b]['x'],by[b]['y']]],crossingGaps=[]))
def travel(a,b,points,role):
 ids=[a]
 for p in points:id=roadpool.pop(0);put(id,p);new_roads.append(id);ids.append(id)
 ids.append(b)
 for a,b in zip(ids,ids[1:]):edge(a,b,role)
def group(gid,points,notables,links,role):
 src=gb[gid];small=[i for i in src['nodes'] if original[i]['type']!='notable'];ids=[]
 for slot,p in enumerate(points):id=notables.get(slot) or small.pop(0);put(id,p);ids.append(id)
 assert not small and len(ids)==len(src['nodes']);ng=copy.deepcopy(src);ng.update(x=sum(p[0] for p in points)/len(points),y=sum(p[1] for p in points)/len(points),role=role);g['groups'].append(ng);new_groups.append(gid)
 for a,b in links:edge(ids[a],ids[b])
 return ids
# A small corner: the useful speed Notable is not gated by unavailable GUARD.
parry=group('g26',[(-2260,710),(-2050,580),(-2390,830),(-2280,490)],{3:'g26n3'},[(1,3),(3,0),(0,2)],'受け流しの小さな角 / 手数は入口、受け流し入力の枝は任意')
travel('opening_warrior_2_2','g26n1',[(-1650,240),(-1900,270)],'speed-to-parry')
# Penetration has an upper efficient goal and a lower physical investment route.
pen=group('g37',[(-2810,660),(-2560,710),(-2510,970),(-2770,1120),(-3220,940),(-3010,1100),(-3150,580),(-3350,680),(-3300,1170)],{0:'g37n0',4:'g37n4'},[(1,0),(0,6),(6,7),(7,4),(1,2),(2,3),(3,5),(5,4),(4,8)],'貫通と一撃 / 上で貫通を拾うか、下の物理恩恵を回るか')
travel('g26n3','g37n1',[(-2580,420)],'penetration-approach')
put('key_04',(-3680,850));travel('g37n4','key_04',[(-3510,990)],'optional-keystone')
# The shorter multiplier arm and the longer probability arm meet one target.
crit=group('g25',[(-2050,1720),(-2330,1710),(-2440,1900),(-2860,2240),(-2670,1690),(-2900,1980),(-2610,2220),(-2420,2390)],{6:'g25n6'},[(0,1),(1,2),(2,6),(1,4),(4,5),(5,3),(3,6),(6,7)],'会心へ投資 / 短い倍率の腕と、確率を積んで回る長い腕')
travel('g30n2','g25n0',[(-1490,1520),(-1780,1650)],'speed-to-critical')
travel('g37n2','g25n1',[(-2260,1210),(-2380,1460)],'physical-or-critical')
# Two unequal weak-point forks retain useful critical routes without WEAKPOINT.
weak=group('g48',[(-1970,2710),(-2180,2880),(-2100,3090),(-1820,2980),(-1900,3250),(-1530,3370),(-1670,3540),(-2050,3460),(-2150,3620),(-1800,3760)],{4:'g48n4',8:'g48n8'},[(0,3),(3,4),(0,1),(1,2),(2,4),(4,5),(5,6),(4,6),(6,9),(9,8),(4,7),(7,8)],'弱点と会心の二目標 / 弱点入力の枝と一般の会心の道を分ける')
travel('g25n7','g48n0',[(-2110,2500)],'critical-to-weakpoint')
travel('g48n6','g21n4',[(-1260,3500),(-1110,3170),(-1370,2840)],'physical-return')
edge('g48n5',new_roads[-3],'weakpoint-return-choice')
g['bounds']={k:v for k,v in [('minX',min(n['x'] for n in nodes)-170),('maxX',max(n['x'] for n in nodes)+170),('minY',min(n['y'] for n in nodes)-170),('maxY',max(n['y'] for n in nodes)+170)]}
g.update(scope='会心・非会心・弱点・受け流しを既存の守りと武器圏へ接続。全体47領域の予約配置を別図へ保持。',fullTreeLayoutComplete=False)
profile=next(p for p in source['profiles'] if p['id']=='warrior:support:plain:standard');flags=set(profile['flags'])|{'LIFESTEAL'}
def input_stats(n):
 raw={'hp':2} if n['type']=='road' else n['stats'];return {k:v for k,v in raw.items() if all(f in flags for f in source['statInputs'].get(k,[]))}
adj={i:[] for i in by}
for e in g['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
def route(owned,target):
 prices={i:0 for i in owned};prev={};q=[(0,i) for i in owned];heapq.heapify(q)
 while q:
  cost,u=heapq.heappop(q)
  if cost!=prices[u]:continue
  if u==target:
   path=[u]
   while path[-1] in prev:path.append(prev[path[-1]])
   return path[::-1]
  for v in adj[u]:
   n=by[v]
   if n['type']=='keystone' and v!=target or n['type']=='small' and not input_stats(n):continue
   if n['type']=='keystone' and not set(n.get('requirements',[]))<=flags:continue
   nc=cost+(0 if v in owned else n['cost'])
   if nc<prices.get(v,math.inf):prices[v]=nc;prev[v]=u;heapq.heappush(q,(nc,v))
 raise ValueError(target)
examples=json.loads((prior/'route-examples.json').read_text(encoding='utf-8'))
for id,label,targets in [('critical','会心と弱点へ投資',['g25n6','g48n4','g48n8']),('quiet','会心を捨てて一撃へ',['g37n0','g37n4','key_04'])]:
 owned={'origin_warrior'};paths=[]
 for target in targets:p=route(owned,target);owned.update(p);paths.append({'target':target,'nodes':p})
 lost={k for i in owned for k in source['lostStats'].get(by[i].get('rule'),[])};sums=collections.Counter()
 for i in sorted(owned):sums.update({k:v for k,v in input_stats(by[i]).items() if k not in lost})
 spent=sum(by[i]['cost'] for i in owned);assert spent<=48
 examples.append(dict(id=id,label=label,targets=targets,paths=paths,learned=sorted(owned),spent=spent,stats={k:round(v,5) for k,v in sums.items()},keys=[i for i in owned if by[i]['type']=='keystone'],budget=48,remaining=48-spent))
payload=json.loads(re.search(r'<script id="goalStudyData" type="application/json">(.*?)</script>',(prior/'ProjectS_Goal_Routes_Study_V2.html').read_text(encoding='utf-8'),re.S)[1]);payload.update(graph=g,examples=examples)
accepted=(BASE/'proposals/workshop-clear-wiring-v4/ProjectS_Passive_Clear_Wiring_V4.html').read_text(encoding='utf-8');art=json.loads(re.search(r'const ART=(\{.*?\});',accepted,re.S)[1]);semantic={'target':'hunt_pierce','spear':'war_breach','shield':'war_guard'}
for n in nodes:
 if n['type'] in ['notable','keystone','start'] and n['icon'] not in payload['icons']:payload['icons'][n['icon']]=art[n['icon'] if n['icon'] in art else semantic[n['icon']]]
OUT.mkdir(parents=True,exist_ok=True)
for name,data in [('candidate-region.json',g),('route-examples.json',examples)]: (OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
template=(ROOT/'scripts/skill-tree-goal-study-v2-template.html').read_text(encoding='utf-8').replace('goal-study-v2','goal-study-v3')
template=template.replace('<button data-build="single">連撃を一撃へ</button>','<button data-build="single">連撃を一撃へ</button><button data-build="critical">会心へ投資</button><button data-build="quiet">会心を捨てて一撃へ</button>')
template=template.replace("const labels={bleed:","const labels={crit:'会心率',critMulti:'会心倍率',weakpoint:'弱点',penetration:'貫通',parry:'受け流し',bleed:")
old="function benefits(n){const raw=n.type==='road'?S.travelChoices.find(c=>c.id===roadChoice).stats:n.stats;const f=flags();const lost=new Set([...learned].filter(id=>by.get(id).type==='keystone').flatMap(id=>S.lostStats[by.get(id).rule]||[]));return Object.fromEntries(Object.entries(raw).filter(([k])=>!lost.has(k)&&(S.statInputs[k]||[]).every(v=>f.has(v))))}"
new="function inputBenefits(n){const raw=n.type==='road'?S.travelChoices.find(c=>c.id===roadChoice).stats:n.stats;const f=flags();return Object.fromEntries(Object.entries(raw).filter(([k])=>(S.statInputs[k]||[]).every(v=>f.has(v))))}function lostStats(){return new Set([...learned].filter(id=>by.get(id).type==='keystone').flatMap(id=>S.lostStats[by.get(id).rule]||[]))}function benefits(n){const lost=lostStats();return Object.fromEntries(Object.entries(inputBenefits(n)).filter(([k])=>!lost.has(k)))}"
assert old in template;template=template.replace(old,new).replace('Object.keys(benefits(n)).length>0','Object.keys(inputBenefits(n)).length>0')
template=template.replace("activeExample==='area'?","activeExample==='critical'?'会心の倍率へ届き、二つの混合Notableへ。弱点入力がないため、今回は会心側だけ有効です。':activeExample==='quiet'?'貫通・物理を拾って、会心を失う代償のKeyへ。直接ヒット22%乗算増は原案で、実戦未反映です。':activeExample==='area'?")
template=template.replace('守り、一撃、広がる刃','会心か、静かな一撃か').replace('守りへの投資を残すか、一撃・手数・近接の広さへ向かうか。欲しい目標に合わせて道と恩恵を選びます。','会心・倍率を育てる道と、貫通を拾って会心を捨てる道。守り・手数・資源への回り方も残します。')
template=template.replace("const n=by.get(selected),plan=plannedRoute()","const disabled=Object.keys(inputBenefits(inactiveNode)).filter(k=>lostStats().has(k));if(disabled.length)document.getElementById('inactiveStats').textContent='静かな刃により '+disabled.map(k=>labels[k]||k).join('・')+' は無効。取得ポイントと接続は保持されます。';const n=by.get(selected),plan=plannedRoute()")
template=template.replace('新配置はこの育成圏だけです。','詳細配線済みはこの育成圏だけです。全47領域と5職業の入口は別の全体配置計画に保持しています。')
(ROOT/'scripts/skill-tree-goal-study-v3-template.html').write_text(template,encoding='utf-8')
oldhtml=(prior/'ProjectS_Goal_Routes_Study_V2.html').read_text(encoding='utf-8');fonts='\n'.join(re.findall(r'@font-face\{[^}]+\}',oldhtml));html=template.replace('/*FONTS*/',fonts).replace('/*PAYLOAD*/',json.dumps(payload,ensure_ascii=False,separators=(',',':')))
(OUT/'ProjectS_Goal_Routes_Study_V3.html').write_text(html,encoding='utf-8')
manifest=dict(status='STRUCTURE_STUDY',adopted=False,source645Sha256=hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),source645EmbeddedVerbatim=True,displayedNodes=len(nodes),displayedGroups=len(g['groups']),connections=len(g['edges']),newGroups=new_groups,newRoads=new_roads,full645LayoutCompleted=False,effectsCostsRequirementsUnchanged=True,previousFilesChanged=False,runtimeApplied=False,retainedV2Commit='7cee655d',nextStep='全47領域の配置計画に沿って他職業の入口と血の代価・魔力・障壁圏を詳細接続する。')
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'nodes':len(nodes),'groups':len(g['groups']),'edges':len(g['edges']),'newExamples':examples[-2:]},ensure_ascii=False))
