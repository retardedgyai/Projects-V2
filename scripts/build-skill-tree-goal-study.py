"""A bounded goal-first route study using unchanged source effects and input rules.

This is a local design study, not a replacement layout for the full 645-node tree.
All 645 source records are embedded verbatim. Earlier proposals are immutable.
"""
import base64, collections, copy, hashlib, heapq, json, math, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-goal-study-v1'
source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));original={n['id']:n for n in source['nodes']};gb={g['id']:g for g in source['groups']};pos={};edges={};roles={};selected=[]
def put(id,p):assert id not in pos;pos[id]=tuple(p);selected.append(id)
def edge(a,b,role='growth'):assert a in pos and b in pos;edges[tuple(sorted((a,b)))]=dict(a=a,b=b,road=original[a]['type']=='road' or original[b]['type']=='road',proposalKind=role,points=[list(pos[a]),list(pos[b])],crossingGaps=[])
def chain(ids,role='growth'):
    for a,b in zip(ids,ids[1:]):edge(a,b,role)
def group(gid,points,notable_slots,links,role):
    g=gb[gid];small=[i for i in g['nodes'] if original[i]['type']!='notable'];notable=list(notable_slots);ids=[]
    for i,p in enumerate(points):
        id=notable_slots.get(i) or small.pop(0);put(id,p);ids.append(id)
    assert not small and len(ids)==len(g['nodes']);roles[gid]=role
    for a,b in links:edge(ids[a],ids[b])
    return ids
roadpool=[n['id'] for n in source['nodes'] if n['type']=='road'];roads=[]
def road(p):
    id=roadpool.pop(0);put(id,p);roads.append(id);return id
def travel(a,b,points,role='travel'):
    ids=[a]+[road(p) for p in points]+[b];chain(ids,role);return ids
o=next(o for o in source['origins'] if o['id']=='warrior');put(o['root'],(-1300,700))
opening=[ [(-1120,500),(-940,470),(-750,520)], [(-1130,875),(-980,830),(-780,810)], [(-1330,430),(-1360,220),(-1240,80)] ]
for lane,points in zip(o['lanes'],opening):
    for id,p in zip(lane['nodes'],points):put(id,p)
    chain([o['root']]+lane['nodes'],'opening')
# A broad armour hook has two entrances. Its notable opens a short optional Key.
armor=group('g49',[(-420,680),(-400,480),(100,400),(-230,360),(-50,330),(180,570),(100,740),(-150,800)],{2:'g49n2'},[(0,1),(1,3),(3,4),(4,2),(2,5),(5,6),(6,7),(7,0)],'装甲を拾って不動へ / 生命側と武器側の二入口')
travel(o['lanes'][1]['nodes'][-1],armor[0],[(-620,830)])
put('key_05',(350,-40));travel('g49n2','key_05',[(270,220),(390,100)],'optional-keystone')
# Life is a fan: a quick lower arm or a longer upper arm to the same notable.
life=group('g45',[(-670,20),(-660,-210),(-260,-330),(-440,-350),(-100,-160),(-260,20),(-510,-30)],{2:'g45n2'},[(0,1),(1,3),(3,2),(0,6),(6,5),(5,4),(4,2)],'生命の二経路 / 上でHPを積むか下から目標へ')
travel(armor[1],life[0],[(-550,260)])
travel(o['lanes'][2]['nodes'][-1],life[0],[(-1040,-30),(-860,-40)])
# Healing is a compact side branch, never a compulsory transit or mandatory magic.
healing=group('g38',[(-1180,-420),(-1530,-850),(-1320,-480),(-1460,-570),(-1350,-750),(-1210,-750),(-1120,-590)],{1:'g38n1'},[(0,2),(2,3),(3,1),(1,4),(4,5),(5,6),(6,0)],'回復は寄り道 / 生命経路に戻る小さな輪')
travel(life[1],healing[0],[(-850,-350),(-1000,-350)])
# Resistance has two separated notable goals, with a small useful cross-connection.
resist=group('g23',[(-330,-620),(-440,-850),(-180,-740),(20,-760),(210,-890),(390,-920),(360,-720),(190,-610),(30,-600)],{1:'g23n1',5:'g23n5'},[(0,1),(0,2),(2,3),(3,4),(4,5),(5,6),(6,7),(7,8),(8,0),(3,8)],'生命から耐性へ / 二つのNotableを独立して拾う')
travel('g45n2',resist[0],[(-290,-480)])
# A large absorption crescent connects to the ward goal, distinct from a direct road.
leech=group('g13',[(590,-510),(600,-300),(780,-220),(780,-550),(960,-270),(1060,-420),(1080,-600),(1030,-790),(850,-890),(740,-790)],{3:'g13n3',9:'g13n9'},[(0,1),(1,2),(2,4),(4,5),(5,6),(6,7),(7,8),(8,9),(9,3),(3,0)],'吸収を積む長い回り道 / 障壁Keyとの組み合わせ')
travel(resist[6],leech[0],[(480,-650)])
# Ward is an open branching canopy: one notable near the entrance, one by the Key.
ward=group('g51',[(1370,-420),(1520,-770),(1500,-570),(1630,-590),(1720,-920),(1630,-1080),(1420,-1100),(1320,-920),(1350,-700)],{1:'g51n1',4:'g51n4'},[(0,2),(2,3),(3,1),(1,4),(4,5),(5,6),(6,7),(7,8),(8,0)],'守りの循環へ / 吸収側と短い通路側から入る')
travel(leech[7],ward[8],[(1190,-730)])
put('key_01',(1720,-1370));travel('g51n4','key_01',[(1870,-1050),(1890,-1240)],'optional-keystone')
# Direct transfer is shorter but buys fewer thematic rewards than life/resist/leech.
direct=travel(armor[5],ward[0],[(500,530),(770,450),(1060,230),(1340,-70)],'direct-route')
# Weapon investments split locally into damage, hand speed, and resource economy.
force=group('g35',[(580,920),(910,840),(720,830),(820,1060),(1020,1040),(1100,910),(1040,720),(770,710)],{1:'g35n1'},[(0,2),(2,1),(1,6),(6,7),(0,3),(3,4),(4,5),(5,1)],'一撃を伸ばす短い目標 / 防御の通路から離れる選択')
travel(armor[6],force[0],[(330,890)])
travel(o['lanes'][0]['nodes'][-1],armor[0],[(-650,640)])
speed=group('g30',[(-1140,1130),(-1060,1410),(-1260,1280),(-1230,1460),(-830,1500),(-660,1450),(-590,1260),(-820,1200)],{1:'g30n1',4:'g30n4'},[(0,2),(2,3),(3,1),(1,4),(4,5),(5,6),(6,7),(7,0)],'手数を先に育てる / 二段のNotableと資源への横道')
travel(o['lanes'][1]['nodes'][-1],speed[0],[(-970,1040)])
resource=group('g14',[(20,1190),(170,1320),(330,1450),(520,1500),(680,1340),(860,1410),(1020,1300),(1120,1140)],{4:'g14n4'},[(0,1),(1,2),(2,3),(3,4),(4,5),(5,6),(6,7)],'資源を拾う長い横断 / 手数と一撃をつなぐ道')
travel(speed[6],resource[0],[(-360,1190),(-170,1170)])
travel(resource[-1],force[4],[(1130,1030)])
nodes=[dict(copy.deepcopy(original[i]),x=pos[i][0],y=pos[i][1]) for i in selected]
groups=[]
for gid,role in roles.items():
    g=copy.deepcopy(gb[gid]);ps=[pos[i] for i in g['nodes']];g.update(x=sum(p[0] for p in ps)/len(ps),y=sum(p[1] for p in ps)/len(ps),role=role);groups.append(g)
graph={'nodes':nodes,'edges':list(edges.values()),'groups':groups,'origins':[o],'bounds':{'minX':min(p[0] for p in pos.values())-170,'maxX':max(p[0] for p in pos.values())+170,'minY':min(p[1] for p in pos.values())-170,'maxY':max(p[1] for p in pos.values())+170},'source645Retained':True,'fullTreeLayoutComplete':False,'runtimeApplied':False,'directRoute':direct,'scope':'戦士の不動・吸収障壁・武器育成を比べる局所構造試作。全47領域の新配置は未完成。'}
profile=next(p for p in source['profiles'] if p['id']=='warrior:support:plain:standard');flags=set(profile['flags'])|{'LIFESTEAL'}
def stats(id):
    n=original[id];raw={'hp':2} if n['type']=='road' else n['stats'];return {k:v for k,v in raw.items() if all(f in flags for f in source['statInputs'].get(k,[]))}
adj={i:[] for i in selected}
for e in edges.values():adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
def route(start,target,blocked=(),owned=()):
    own=set(owned);pr={start:0};prev={};q=[(0,start)]
    while q:
        cost,u=heapq.heappop(q)
        if cost!=pr[u]:continue
        if u==target:
            ids=[u]
            while ids[-1] in prev:ids.append(prev[ids[-1]])
            return ids[::-1]
        for v in adj[u]:
            n=original[v]
            if v in blocked or n['type']=='keystone' and v!=target or n['type']=='small' and not stats(v):continue
            nc=cost+(0 if v in own else n['cost'])
            if nc<pr.get(v,math.inf):pr[v]=nc;prev[v]=u;heapq.heappush(q,(nc,v))
    raise ValueError(target)
examples=[]
for id,label,targets,blocked in [('direct','Keyに先に届く',['key_05','key_01'],[i for g in [life,leech,resist] for i in g]),('growth','生命・吸収を拾って届く',['g45n2','g23n1','g13n3','g13n9','key_01','key_05'],direct[1:-1]),('weapon','一撃と手数を育てる',['g35n1','g30n1','g30n4','g14n4'],[])]:
    owned={o['root']};paths=[]
    for target in targets:
        candidates=[route(start,target,blocked,owned) for start in sorted(owned)];p=min(candidates,key=lambda p:(sum(original[i]['cost'] for i in p if i not in owned),len(p),p));owned.update(p);paths.append({'target':target,'nodes':p})
    sums=collections.Counter()
    for i in sorted(owned):sums.update(stats(i))
    spent=sum(original[i]['cost'] for i in owned);examples.append({'id':id,'label':label,'targets':targets,'paths':paths,'learned':sorted(owned),'spent':spent,'stats':{k:round(v,5) for k,v in sums.items()},'keys':[i for i in owned if original[i]['type']=='keystone'],'budget':48,'remaining':48-spent})
    assert spent<=48
OUT.mkdir(parents=True,exist_ok=True)
for name,obj in [('candidate-region.json',graph),('route-examples.json',examples)]: (OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf-8')
old=(BASE/'proposals/workshop-clear-wiring-v4/ProjectS_Passive_Clear_Wiring_V4.html').read_text(encoding='utf-8');fonts='\n'.join(re.findall(r'@font-face\{[^}]+\}',old))
art=json.loads(re.search(r'const ART=(\{.*?\});',old,re.S)[1])
icon_map={'sword':'war_breach','speed':'whirl','shield':'war_guard','ward':'heal_shield','heart':'war_guard','leaf':'heal_light','target':'hunt_pierce','spear':'war_breach','boot':'hunt_retreat','crystal':'mage_ult','clock':'mage_mark','nova':'frost_nova','flame':'firebolt','snow':'frost_nova','bolt':'mage_burst','drop':'ass_poison','fang':'ass_ult','star':'mage_ult'}
# Exact accepted sprite URLs and semantic mapping from the retained workshop UI.
icons={n['icon']:art[n['icon'] if n['icon'] in art else icon_map[n['icon']]] for n in nodes if n['type'] in ['notable','keystone','start']}
icons['sword']=art['war_breach']
template=(ROOT/'scripts/skill-tree-goal-study-template.html').read_text(encoding='utf-8');payload=json.dumps({'source':source,'graph':graph,'examples':examples,'icons':icons,'profile':profile},ensure_ascii=False,separators=(',',':'))
html=template.replace('/*FONTS*/',fonts).replace('/*PAYLOAD*/',payload)
(OUT/'ProjectS_Goal_Routes_Study.html').write_text(html,encoding='utf-8')
manifest={'status':'STRUCTURE_STUDY','adopted':False,'source645Sha256':hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),'source645EmbeddedVerbatim':True,'displayedNodes':len(nodes),'displayedGroups':len(groups),'connections':len(edges),'full645LayoutCompleted':False,'effectsCostsRequirementsUnchanged':True,'v4CompressionCancelled':True,'previousFilesChanged':False,'runtimeApplied':False,'sourceReference':'https://github.com/grindinggear/skilltree-export','nextStep':'この目標圏の接続を基準に、残る領域の異なる目標・接続を設計する。全体へ機械的複製しない。'}
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'nodes':len(nodes),'edges':len(edges),'examples':[{k:e[k] for k in ['id','spent','remaining','stats']} for e in examples]},ensure_ascii=False))
