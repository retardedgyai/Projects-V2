"""One authored middle-region revision of the approved central V6 preview.

The central opening and all existing node effects stay intact. The additions
have explicit purposes; routes are fitted into existing planar faces instead
of making a common circular travel rail. No game data or runtime is rewritten.
"""
import collections
import copy
import heapq
import json
import math
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'assets/core-ui/large-tree-preview'
OLD = BASE / 'proposals/workshop-poe2-central-v6'
OUT = BASE / 'proposals/workshop-middle-choice-v7'
OUT.mkdir(parents=True, exist_ok=True)
source = json.loads((BASE / 'graph.json').read_text(encoding='utf8'))
baseline = json.loads((OLD / 'candidate-graph.json').read_text(encoding='utf8'))
central = json.loads((OLD / 'central-authoring.json').read_text(encoding='utf8'))
g = copy.deepcopy(baseline)
by = {n['id']: n for n in g['nodes']}
fixed = set(central['centralOpeningIds'])
C = np.array(central['center'])
ledger = {'status': 'AUTHORED_PENDING_VERIFICATION', 'baseline': 'f7cf411c275c0cfb3f4d19af0db19b7df9b3df61', 'regions': [], 'routes': [], 'splitChoices': []}

def xy(i):
    return np.array([by[i]['x'], by[i]['y']], dtype=float)

def rounded(p):
    return [round(float(p[0]), 5), round(float(p[1]), 5)]

def face_inventory(graph):
    ad = collections.defaultdict(list)
    paths = {}
    for e in graph['edges']:
        a, b = e['a'], e['b']
        ad[a].append(b); ad[b].append(a)
        paths[a, b] = e['points']; paths[b, a] = e['points'][::-1]
    for a, vs in ad.items():
        vs.sort(key=lambda b: math.atan2(paths[a,b][1][1]-paths[a,b][0][1], paths[a,b][1][0]-paths[a,b][0][0]))
    seen, result = set(), []
    for start in paths:
        if start in seen: continue
        ids, poly, (a,b) = [], [], start
        while (a,b) not in seen:
            seen.add((a,b)); ids.append(a); poly.extend(paths[a,b][:-1])
            a,b = b,ad[b][(ad[b].index(a)-1)%len(ad[b])]
        assert (a,b) == start
        area = sum(p[0]*q[1]-q[0]*p[1] for p,q in zip(poly,poly[1:]+poly[:1]))/2
        if area > 100:
            result.append({'ids': ids, 'points': np.array(poly), 'area': area})
    return sorted(result, key=lambda f: f['area'], reverse=True)

faces = face_inventory(baseline)

def inside(p, face):
    a = face['points']; b = np.roll(a,-1,axis=0)
    mask = (a[:,1] > p[1]) != (b[:,1] > p[1])
    cuts = a[mask,0]+(p[1]-a[mask,1])*(b[mask,0]-a[mask,0])/(b[mask,1]-a[mask,1])
    return bool(np.count_nonzero(cuts > p[0]) % 2)

class Geometry:
    def refresh(self):
        segments = [(e['a'],e['b'],p,q) for e in g['edges'] for p,q in zip(e['points'],e['points'][1:])]
        self.A = np.array([s[2] for s in segments]); self.D = np.array([np.array(s[3])-s[2] for s in segments])
        self.ends = [frozenset(s[:2]) for s in segments]
        self.den = np.maximum((self.D*self.D).sum(axis=1),1e-12)
        self.node_ids = list(by); self.N = np.array([xy(i) for i in self.node_ids])
    def wire_distance(self,p,ignore_edge=None):
        v = p-self.A; t = np.clip((v*self.D).sum(axis=1)/self.den,0,1)
        distances=np.linalg.norm(v-t[:,None]*self.D,axis=1)
        if ignore_edge:distances[np.array([x==frozenset(ignore_edge) for x in self.ends])]=float('inf')
        return float(np.min(distances))
    def clear(self,a,b,ignore=(),clearance=92,face=None):
        v=b-a; length=float(np.linalg.norm(v))
        if length<1e-6: return False
        if face:
            for t in np.linspace(.025,.975,max(3,math.ceil(length/140))):
                if not inside(a+t*v,face): return False
        w=self.A-a; cross=v[0]*self.D[:,1]-v[1]*self.D[:,0]; nz=np.abs(cross)>1e-8
        t=np.zeros(len(cross));u=t.copy()
        t[nz]=(w[nz,0]*self.D[nz,1]-w[nz,1]*self.D[nz,0])/cross[nz]
        u[nz]=(w[nz,0]*v[1]-w[nz,1]*v[0])/cross[nz]
        if np.any(nz&(t>1e-7)&(t<1-1e-7)&(u>-1e-7)&(u<1+1e-7)): return False
        # Collinear overlap is never hidden under a new wire.
        col=(~nz)&(np.abs(w[:,0]*v[1]-w[:,1]*v[0])<1e-7)
        if np.any(col):
            lo=(w[col]@v)/(v@v);hi=((w[col]+self.D[col])@v)/(v@v)
            if np.any(np.minimum(1,np.maximum(lo,hi))-np.maximum(0,np.minimum(lo,hi))>1e-7):return False
        mask=np.array([i not in ignore for i in self.node_ids])
        if np.any(mask):
            q=self.N[mask]-a;t=np.clip((q@v)/(v@v),0,1)
            if np.min(np.linalg.norm(q-t[:,None]*v,axis=1))<clearance:return False
        return True

geom=Geometry(); geom.refresh()

def add_node(id,p,type,name,stats,theme='travel-choice',icon='crystal',group=None,desc=''):
    assert id not in by
    n=dict(id=id,x=rounded(p)[0],y=rounded(p)[1],type=type,name=name,stats=stats,theme=theme,icon=icon,group=group,cost=1,runtimeApplied=False,sourceState='middle_choice_v7_proposal',sourceRef='middle-choice-v7:'+id,requirements=[],effectState='unimplemented_proposal',inputPolicy='any_usable_benefit',benefitInputs={k:source['statInputs'].get(k,[]) for k in stats},desc=desc)
    g['nodes'].append(n);by[id]=n
    return id

def edge(a,b,points=None,role='optional-middle-investment'):
    assert a!=b and not any({a,b}=={e['a'],e['b']} for e in g['edges'])
    g['edges'].append(dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind='middle-choice-v7',role=role,points=[rounded(p) for p in (points if points is not None else [xy(a),xy(b)])],crossingGaps=[]))

def split_at(e, fraction):
    lengths=[math.dist(a,b) for a,b in zip(e['points'],e['points'][1:])];want=sum(lengths)*fraction;travel=0
    for k,le in enumerate(lengths):
        if travel+le>=want:
            q=np.array(e['points'][k])+(np.array(e['points'][k+1])-e['points'][k])*(want-travel)/le
            return q,e['points'][:k+1]+[rounded(q)],[rounded(q)]+e['points'][k+1:]
        travel+=le
    raise ValueError('fraction')

def split_choice(a,b,id,purpose,fraction=.5):
    assert a not in fixed and b not in fixed
    e=next(e for e in g['edges'] if {a,b}=={e['a'],e['b']})
    p,left,right=split_at(e,fraction)
    add_node(id,p,'road','選べる基礎',{},desc='長い移動区間で、次の地域と任意投資を選ぶ分岐。通路の恩恵は画面で選んだ値。')
    g['edges'].remove(e)
    for u,v,pts in [(e['a'],id,left),(id,e['b'],right)]:
        f=copy.deepcopy(e);f.update(a=u,b=v,points=pts);g['edges'].append(f)
    for face in faces:
        if any({face['ids'][k],face['ids'][(k+1)%len(face['ids'])]}=={a,b} for k in range(len(face['ids']))):face['ids'].append(id)
    ledger['splitChoices'].append(dict(id=id,oldEdge=[a,b],addedCost=1,purpose=purpose,existingWireShapeRetained=True))
    geom.refresh()

split_choice('r2_22_1','r11_40_1','v7road_nw','北西の長い区間で耐久の種類と地域間経路を選ぶ')
split_choice('r9_20_2','r12_19_2','v7road_east','東の長い区間で機動・技能運用と南側への経路を選ぶ')
# The final local approaches use existing paid junctions for these two areas.
# Do not leave a new degree-two toll on the unchanged northwest / inner wires.
ledger['discardedSplits'] = [
    {'oldEdge': ['link_warrior_2_7', 'r6_35_1'], 'reason': 'HP / regen choices attach to an existing junction; preserve the original travel cost.'},
    {'oldEdge': ['r18_26_1', 'link_assassin_1_4'], 'reason': 'MP choices attach to an existing junction; preserve the original travel cost.'},
]

# Each configuration owns a different local decision, not a repeated theme ring.
configs = [
    dict(id='v7g01',face=1,seed=(-2800,-2600),pattern='fork3',name='備えを選ぶ',theme='armor',purpose='被弾への備えをHP・装甲・魔法防御から選ぶ。防具seriesや職業制限を付けない。',prefix=[('備えの基礎',{'hp':2},'heart'),('守りの余地',{'hp':2},'heart')],arms=[('生命を厚く',{'hp':3},'生命の備え',{'hp':7},'heart'),('装甲を厚く',{'armor':4},'装甲の備え',{'armor':12},'shield'),('魔法防御を厚く',{'resist':4},'魔法への備え',{'resist':12},'ward')]),
    dict(id='v7g02',face=1,seed=(-4000,-3500),pattern='loop',name='技を回す',theme='haste',purpose='同じ目標へ資源運用側と技の回転側の同費用経路。途中の恩恵が異なる。',armA=('資源を整える',{'resource':3},'clock'),armB=('回転を整える',{'haste':2},'clock'),goal=('続く攻め',{'resource':6,'haste':6},'clock'),side=('歩調を保つ',{'move':4},'boot')),
    dict(id='v7g03',face=2,seed=(-5350,1250),pattern='fork2',name='間合いの選択',theme='mobility',purpose='移動で間合いを変えるか、攻撃速度へ投資するか。単入口の任意投資。',prefix=[('動くための余地',{'move':1},'boot'),('間合いを測る',{'move':1},'boot')],arms=[('足を運ぶ',{'move':2},'間合いを変える',{'move':4},'boot'),('手数を整える',{'attackSpeed':2},'手数を増やす',{'attackSpeed':5},'speed')]),
    dict(id='v7g04',face=2,seed=(-3550,2850),pattern='loop',name='一撃の組み方',theme='crit',purpose='同じ物理・会心の目標へ、途中で物理を積む経路と会心を積む経路。遠方の会心倍率目標とは役割が異なる。',armA=('物理の研鑽',{'physical':3},'sword'),armB=('会心の研鑽',{'crit':6},'target'),goal=('狙いを込める',{'physical':7,'crit':10},'target'),side=('傷を深くする',{'penetration':3},'spear')),
    dict(id='v7g05',face=7,seed=(-2350,-1600),pattern='short',name='息継ぎ',theme='life',purpose='北西への移動中にHP容量か自動再生を短く拾う。HEAL技能を与えない。',prefix=('身体の余地',{'hp':2},'heart'),goals=[('体力を残す',{'hp':6},'heart'),('息を戻す',{'regen':8},'leaf')]),
    dict(id='v7g06',face=12,seed=(-300,-2350),pattern='fork2',name='継続の備え',theme='life',purpose='中央外側で容量と再生を分けて育てる。回復地域の購入を前提にしない。',prefix=[('継続の基礎',{'hp':2},'heart'),('身体を整える',{'hp':2},'heart')],arms=[('体力を蓄える',{'hp':3},'蓄える体力',{'hp':7},'heart'),('回復を待つ',{'regen':4},'戻る体力',{'regen':10},'leaf')]),
    dict(id='v7g07',face=10,seed=(2150,-1700),pattern='loop',name='消費の選択',theme='mana',purpose='MP容量側と消費効率側から同費用で目標へ。MPを使う現在の技能を育て、魔術を供給しない。',armA=('MPを蓄える',{'mana':4},'crystal'),armB=('消費を整える',{'efficiency':2},'clock'),goal=('消費の余裕',{'mana':8,'efficiency':3},'crystal'),side=('足を止めない',{'move':3},'boot')),
    dict(id='v7g08',face=17,seed=(3550,0),pattern='fork3',name='攻める位置',theme='mobility',purpose='東の中間部で機動・攻撃速度・資源運用を分ける。氷や魔術を通過購入しない。',prefix=[('攻める余地',{'damage':2},'sword'),('機会を測る',{'damage':2},'target')],arms=[('位置を変える',{'move':2},'踏み込む位置',{'move':4},'boot'),('手数を選ぶ',{'attackSpeed':2},'攻めの歩調',{'attackSpeed':5},'speed'),('資源を保つ',{'resource':3},'攻めの準備',{'resource':8},'clock')]),
    dict(id='v7g09',face=0,seed=(2150,2050),pattern='fork3',name='攻め時の選択',theme='resource',purpose='南東の中間部で手数・会心・技の回転の小目標を選び、その先の専門地域へ進む。',prefix=[('機会を残す',{'resource':2},'clock'),('機会を整える',{'resource':2},'clock')],arms=[('連撃へ備える',{'attackSpeed':2},'続ける手数',{'attackSpeed':5},'speed'),('一撃へ備える',{'crit':5},'一撃の準備',{'crit':16},'target'),('回転へ備える',{'haste':2},'次の技へ',{'haste':6},'clock')]),
    dict(id='v7g10',face=0,seed=(3550,3000),pattern='loop',name='届き方の選択',theme='area',purpose='範囲を広げる側と物理を積む側から同じ目標へ。範囲の入力がある手持ちの技能にだけ適用。',armA=('範囲を育てる',{'aoe':2},'nova'),armB=('物理を育てる',{'physical':3},'sword'),goal=('届く攻撃',{'aoe':5,'physical':5},'nova'),side=('歩調をつなぐ',{'attackSpeed':4},'speed')),
    dict(id='v7g11',face=0,seed=(800,3300),pattern='fork3',name='持ち直す方法',theme='life',purpose='HP容量・自動再生・所有する吸命MODの強化を分ける。吸命は任意の終端で、移動に使わない。',prefix=[('身体を保つ',{'hp':2},'heart'),('余力を残す',{'hp':2},'heart')],arms=[('体力の余地',{'hp':3},'残る体力',{'hp':6},'heart'),('再生の余地',{'regen':3},'戻る余力',{'regen':8},'leaf'),('吸命の研鑽',{'leech':.15},'吸命を育てる',{'leech':.35},'fang')]),
    dict(id='v7g12',face=5,seed=(3400,-3300),pattern='fork3',name='術と守りの投資',theme='magic',purpose='既に使える魔法・MP効率・障壁を別々の終端で育てる。APやSHIELD入力のない編成には該当枝を供給しない。',prefix=[('消費の備え',{'mana':3},'crystal'),('MPの余地',{'mana':3},'crystal')],arms=[('魔法の研鑽',{'magic':4},'魔法を育てる',{'magic':9},'star'),('効率の研鑽',{'efficiency':2},'消費を抑える',{'efficiency':5},'clock'),('障壁の研鑽',{'barrier':4},'障壁を育てる',{'barrier':10},'ward')]),
    dict(id='v7g13',face=4,seed=(5700,-1200),pattern='loop',name='手数の運用',theme='speed',purpose='東の遠方へ向かう途中で資源運用か攻撃速度を積む。既存の大きな資源目標への投資を残す。',armA=('資源を残す',{'resource':3},'clock'),armB=('手数を残す',{'attackSpeed':2},'speed'),goal=('攻めを保つ',{'resource':6,'attackSpeed':4},'speed'),side=('狙い直す',{'crit':12},'target')),
]
configs.extend([
    dict(id='v7g14',face=7,seed=(-2450,-500),pattern='short',name='技能の余地',theme='resource',purpose='西の中間部で、固有資源を多く得るかMP消費効率を上げるかを選ぶ。今使う技能の運用へ投資する。',prefix=('技能運用の基礎',{'resource':1},'clock'),goals=[('資源を保つ',{'resource':6},'clock'),('消費を抑える',{'efficiency':4},'crystal')]),
    dict(id='v7g15',face=1,seed=(-3100,-1100),pattern='fork2',name='受ける構え',theme='parry',purpose='被弾への備えを装甲か手持ちの受け流しで育てる。防御技能のある編成で受け流し側を選ぶ任意投資。',prefix=[('構えの余地',{'hp':2},'heart'),('備える体力',{'hp':2},'heart')],arms=[('装甲の研鑽',{'armor':3},'受ける装甲',{'armor':10},'shield'),('受け流しの研鑽',{'parry':3},'受け流す構え',{'parry':10},'shield')]),
])

def template(config):
    pattern=config['pattern']
    if pattern in ('fork3','fork2'):
        coords=[(-340,0),(-100,0)]
        mids=[(100,-250),(210,10),(90,260)] if pattern=='fork3' else [(120,-190),(120,200)]
        ends=[(355,-340),(440,45),(340,350)] if pattern=='fork3' else [(365,-270),(345,285)]
        specs=[(*v,'small') for v in config['prefix']]; links=[(0,1)]
        for arm,p,q in zip(config['arms'],mids,ends):
            si=len(coords);coords.extend([p,q]);specs.extend([(arm[0],arm[1],arm[4],'small'),(arm[2],arm[3],arm[4],'notable')]);links.extend([(1,si),(si,si+1)])
        return np.array(coords,dtype=float),specs,links,[0]
    if pattern=='loop':
        coords=np.array([(-250,-170),(-250,170),(-20,-290),(-20,290),(260,0),(330,390)],dtype=float)
        specs=[(*config['armA'],'small'),(*config['armB'],'small'),(*config['armA'],'small'),(*config['armB'],'small'),(*config['goal'],'notable'),(*config['side'],'notable')]
        return coords,specs,[(0,2),(2,4),(1,3),(3,4),(3,5)],[0,1]
    if pattern=='short':
        coords=np.array([(-170,0),(30,0),(245,-170),(240,185)],dtype=float)
        return coords,[(*config['prefix'],'small'),(*config['prefix'],'small'),(*config['goals'][0],'notable'),(*config['goals'][1],'notable')],[(0,1),(1,2),(1,3)],[0]
    raise ValueError(pattern)

region_ports={}
def place_region(config):
    face=faces[config['face']];seed=C+np.array(config['seed']);raw,specs,links,ports=template(config)
    lo=face['points'].min(axis=0);hi=face['points'].max(axis=0)
    grid=[np.array([x,y]) for x in np.arange(lo[0]+120,hi[0]-120,145) for y in np.arange(lo[1]+120,hi[1]-120,145)]
    grid.sort(key=lambda p:float(np.linalg.norm(p-seed)))
    chosen=None
    for center in grid:
        if not inside(center,face) or geom.wire_distance(center)<220:continue
        if any(np.linalg.norm(center-np.array(r['center']))<950 for r in ledger['regions']):continue
        approaches=[i for i,n in by.items() if n['type']=='road' and i not in fixed and (i in face['ids'] or inside(xy(i),face))]
        nearest=min(approaches,key=lambda i:float(np.linalg.norm(xy(i)-center)))
        desired=math.degrees(math.atan2(*(xy(nearest)-center)[::-1]))-180
        rotations=sorted((0,45,90,135,180,225,270,315),key=lambda d:abs((d-desired+180)%360-180))
        for scale in (1,.86,.74):
            for deg in rotations:
                th=math.radians(deg);matrix=np.array([[math.cos(th),-math.sin(th)],[math.sin(th),math.cos(th)]])
                pts=raw@matrix.T*scale+center
                check_pts=pts;check_links=links
                if config['pattern']=='loop':
                    middle=(pts[0]+pts[1])/2;hub=middle-(pts[4]-middle)*.55
                    check_pts=np.vstack([pts,hub]);check_links=links+[(6,0),(6,1)]
                if not all(inside(p,face) and geom.wire_distance(p)>=104 and np.min(np.linalg.norm(geom.N-p,axis=1))>=132 for p in check_pts):continue
                if not all(geom.clear(check_pts[a],check_pts[b],face=face) for a,b in check_links):continue
                # Local glyphs must not meet other local wires.
                valid=True
                for a,b in check_links:
                    v=check_pts[b]-check_pts[a]
                    for k,p in enumerate(check_pts):
                        if k in (a,b):continue
                        t=np.clip(np.dot(p-check_pts[a],v)/np.dot(v,v),0,1)
                        if np.linalg.norm(p-check_pts[a]-t*v)<105*scale:valid=False
                if valid:chosen=(pts,center,deg,scale);break
            if chosen:break
        if chosen:break
    if not chosen:raise ValueError(('Cannot place purposeful region',config['id'],config['face']))
    pts,center,deg,scale=chosen;gid=config['id'];ids=[]
    for k,(p,spec) in enumerate(zip(pts,specs)):
        name,stats,icon,type=spec
        ids.append(add_node(f'{gid}n{k}',p,type,name,stats,config['theme'],icon,gid,config['purpose']))
    for a,b in links:edge(ids[a],ids[b])
    grp=dict(id=gid,x=float(center[0]),y=float(center[1]),rx=450,ry=400,rot=math.radians(deg),name=config['name'],theme=config['theme'],pattern=config['pattern'],nodes=ids,notables=[ids[k] for k,s in enumerate(specs) if s[-1]=='notable'],entrances=[ids[k] for k in ports],outline=[],role=config['purpose'])
    g['groups'].append(grp);region_ports[gid]=grp['entrances']
    ledger['regions'].append(dict(id=gid,name=config['name'],face=config['face'],center=rounded(center),purpose=config['purpose'],pattern=config['pattern'],nodes=ids,notables=grp['notables'],scale=scale,rotation=deg,inputSources={k:source['statInputs'].get(k,[]) for s in specs for k in s[1]},costPerNode=1))
    geom.refresh();print(json.dumps({'placed':gid,'name':config['name'],'center':rounded(center),'scale':scale,'rotation':deg},ensure_ascii=False),flush=True)

def route_in_face(a,b,face_index,fine=False):
    face=faces[face_index];start,end=xy(a),xy(b)
    clearance=70 if fine else 92
    if geom.clear(start,end,ignore=(a,b),face=face,clearance=clearance):return [start,end]
    lo=face['points'].min(axis=0);hi=face['points'].max(axis=0);step=50 if fine else 125
    positions={}
    for ix,x in enumerate(np.arange(lo[0]+45,hi[0],step)):
        for iy,y in enumerate(np.arange(lo[1]+45,hi[1],step)):
            p=np.array([x,y])
            if inside(p,face) and geom.wire_distance(p)>=(55 if fine else 86) and np.min(np.linalg.norm(geom.N-p,axis=1))>=(85 if fine else 115):positions[ix,iy]=p
    starts=[];ends={}
    for key,p in positions.items():
        ds,de=np.linalg.norm(p-start),np.linalg.norm(p-end)
        if ds<620 and geom.clear(start,p,ignore=(a,),face=face,clearance=clearance):starts.append((float(ds),key))
        if de<620 and geom.clear(p,end,ignore=(b,),face=face,clearance=clearance):ends[key]=float(de)
    if not starts or not ends:
        if not fine:return route_in_face(a,b,face_index,True)
        raise ValueError(('No face route portal',a,b,face_index,len(starts),len(ends)))
    best={k:c for c,k in starts};prev={};q=[(c+float(np.linalg.norm(positions[k]-end)),c,k) for c,k in starts];heapq.heapify(q);hit=None
    while q:
        _,cost,u=heapq.heappop(q)
        if cost!=best[u]:continue
        if u in ends:hit=u;break
        for dx,dy in [(0,1),(1,0),(0,-1),(-1,0),(1,1),(-1,1),(1,-1),(-1,-1)]:
            v=u[0]+dx,u[1]+dy
            if v not in positions:continue
            nc=cost+step*math.hypot(dx,dy)
            if nc>=best.get(v,float('inf')):continue
            if not geom.clear(positions[u],positions[v],face=face,clearance=clearance):continue
            best[v]=nc;prev[v]=u;heapq.heappush(q,(nc+float(np.linalg.norm(positions[v]-end)),nc,v))
    if hit is None:
        if not fine:return route_in_face(a,b,face_index,True)
        raise ValueError(('No safe face route',a,b,face_index))
    keys=[hit]
    while keys[-1] in prev:keys.append(prev[keys[-1]])
    points=[start]+[positions[k] for k in keys[::-1]]+[end]
    simple=[points[0]];k=0
    while k<len(points)-1:
        j=len(points)-1
        while j>k+1 and not geom.clear(points[k],points[j],ignore=(a,b),face=face,clearance=clearance):j-=1
        simple.append(points[j]);k=j
    return simple

route_counter=0
def connect(a,b,face_index,count,purpose):
    global route_counter
    route_counter+=1;points=route_in_face(a,b,face_index)
    lengths=[float(np.linalg.norm(y-x)) for x,y in zip(points,points[1:])];total=sum(lengths)
    # Road points are paid junctions; bends alone do not add purchases.
    ids=[a];cuts=[]
    for k in range(count):
        want=total*(k+1)/(count+1);traveled=0
        for si,le in enumerate(lengths):
            if traveled+le>=want:
                p=points[si]+(points[si+1]-points[si])*(want-traveled)/le
                id=f'v7road_r{route_counter}_{k+1}'
                add_node(id,p,'road','選べる基礎',{},desc=purpose+'。通路の恩恵は画面で選んだ値。');ids.append(id);cuts.append((si,p));break
            traveled+=le
    ids.append(b);cur=[points[0]];current_segment=0
    for k,(si,p) in enumerate(cuts):
        cur+=points[current_segment+1:si+1]+[p];edge(ids[k],ids[k+1],cur,role='optional-specialization-bypass');cur=[p];current_segment=si
    cur+=points[current_segment+1:];edge(ids[-2],ids[-1],cur,role='optional-specialization-bypass')
    ledger['routes'].append(dict(id=f'route{route_counter}',fromNode=a,toNode=b,face=face_index,newRoads=ids[1:-1],addedRoadCost=count,length=round(total,2),purpose=purpose))
    geom.refresh();print(json.dumps({'route':[a,b],'face':face_index,'roads':count,'length':round(total)},ensure_ascii=False),flush=True)
    return ids

# Local opt-outs cost points and forego the specialist effects; no free hub.
connect('r14_27_1','r19_24_1',10,2,'回復地域を購入せず北と東を結ぶ。元の再生側は短く、再生効果を持つ')
connect('link_warrior_2_1','r15_43_2',10,1,'MP効率点を購入しない地域間移動。MP側は効率効果を持つ')
connect('link_ranger_1_1','r19_24_1',16,3,'回復地域を購入しない北側の経路。再生とHEAL投資は任意に残す')
connect('link_ranger_2_2','r1_29_1',7,2,'吸収地域を避ける西側の経路。吸命を使わない育成にも移動を残す')
connect('v7road_nw','r21_26_1',1,4,'北西の中間地域から障壁側へ進む。装甲と資源地域を通る経路も残す')
connect('r28_50_1','r2_22_2',2,7,'西の間合いと一撃の小目標を経由する地域間移動。専門投資を直列に買わせない')
connect('r12_19_2','r24_42_2',17,2,'氷や魔術の投資を購入せず東の中間地域を進む')
connect('r1_47_2','v7road_east',0,7,'南の中央外側から東の中間地域へ。複数の小目標へ寄り道できる有料経路')
connect('v7road_r8_4','r39_40_2',0,5,'東南の小目標から外側の技の回転・手数地域へ。単なる全職共通ハブにはしない')
connect('r1_29_1','r24_42_1',26,0,'吸収点を購入せず、魔法防御・生命・装甲への入口を選べる西側の接続')
connect('r24_42_1','r19_24_2',28,0,'吸収地域を避けて装甲側へ戻る。既存の地域入口自体が有料の選択点')

# Reserve real regional movement before placing optional investments.
# This prevents a decorative cluster from blocking the only neutral corridor.
for config in configs:place_region(config)

def attach_region(config):
    gid=config['id'];ports=region_ports[gid];face=faces[config['face']]
    # For loop arms one shared neutral approach preserves equal local costs.
    if len(ports)==2:
        middle=(xy(ports[0])+xy(ports[1]))/2;out=middle-(xy(next(i for i in by if i==gid+'n4'))-middle)*.55
        hub=add_node(gid+'_entry',out,'road','選べる基礎',{},desc='同じ目標へ途中の効果が異なる二経路を選ぶ入口。')
        assert inside(xy(hub),face)
        geom.refresh()
        for port in ports:
            assert geom.clear(xy(hub),xy(port),ignore=(hub,port),face=face),(gid,'entry arm')
            edge(hub,port,role='equal-cost-different-benefit');geom.refresh()
        entry=hub
    else:entry=ports[0]
    candidates=[i for i,n in by.items() if n['type']=='road' and i not in fixed and i!=entry and (i in face['ids'] or i.startswith('v7road_'))]
    candidates.sort(key=lambda i:float(np.linalg.norm(xy(i)-xy(entry))))
    errors=[];long_approaches=[]
    for road in candidates[:18]:
        try:
            points=route_in_face(road,entry,config['face'])
            length=sum(float(np.linalg.norm(y-x)) for x,y in zip(points,points[1:]))
            if length>720:
                long_approaches.append((length,road,points));continue
            edge(road,entry,points,role='short-optional-region-entry');geom.refresh()
            row=next(r for r in ledger['regions'] if r['id']==gid);row.update(approach=road,entry=entry,entryLength=round(length,2))
            print(json.dumps({'attached':gid,'road':road,'length':round(length)},ensure_ascii=False),flush=True)
            return
        except ValueError as e:errors.append(str(e))
    # A long region approach becomes a paid decision on an existing travel wire,
    # rather than an extra unbranched corridor. The old wire's shape is retained.
    junctions=[]
    for e in g['edges']:
        if e['a'] in fixed or e['b'] in fixed or by[e['a']]['type']!='road' or by[e['b']]['type']!='road':continue
        for fraction in (.2,.35,.5,.65,.8):
            p,_,_=split_at(e,fraction);distance=float(np.linalg.norm(p-xy(entry)))
            if distance>700 or np.min(np.linalg.norm(geom.N-p,axis=1))<155 or geom.wire_distance(p,(e['a'],e['b']))<100:continue
            if geom.clear(p,xy(entry),ignore=(entry,),face=face):junctions.append((distance,e,fraction))
    if junctions:
        length,e,fraction=min(junctions,key=lambda r:r[0]);road='v7road_'+gid
        split_choice(e['a'],e['b'],road,config['name']+'への短い任意投資を選ぶ',fraction)
        edge(road,entry,role='short-optional-region-entry');geom.refresh()
        row=next(r for r in ledger['regions'] if r['id']==gid);row.update(approach=road,entry=entry,entryLength=round(length,2),newPaidJunction=True)
        print(json.dumps({'attached':gid,'newDecision':road,'length':round(length)},ensure_ascii=False),flush=True);return
    if long_approaches:
        length,road,points=min(long_approaches,key=lambda x:x[0]);edge(road,entry,points,role='short-optional-region-entry');geom.refresh()
        row=next(r for r in ledger['regions'] if r['id']==gid);row.update(approach=road,entry=entry,entryLength=round(length,2),longApproachRetainedReason='Existing wires leave no clear extra junction; adding a purposeless travel node would not create a choice.')
        print(json.dumps({'attached':gid,'road':road,'length':round(length),'longApproach':True},ensure_ascii=False),flush=True);return
    raise ValueError(('No purposeful region entry',gid,errors))

for config in configs:attach_region(config)

# Reconnect a long interior route to an existing optional investment entrance.
connect('v7road_r6_5','v7g04_entry',2,0,'西の中間経路から物理・会心の二経路を選び、従来の西側経路へ戻れる局所接続')
# One through-region has a separate neutral bypass, as in the researched north
# defense example. Buying its specialization is optional and costs more points.
connect('v7road_r9_4','r33_35_1',0,4,'範囲への投資をせず南東の隣接地域へ進む経路')
connect('v7g10n4','r33_35_1',0,2,'範囲と物理を育てた地域から南東へ抜ける。中立経路は別に残す')
next(r for r in ledger['regions'] if r['id']=='v7g10').update(secondaryEntrance='r33_35_1',role='two-entrance-optional-through-region-with-neutral-bypass')

for n in baseline['nodes']:assert n==by[n['id']],('Original node changed',n['id'])
old_incident=[e for e in baseline['edges'] if e['a'] in fixed or e['b'] in fixed]
new_incident=[e for e in g['edges'] if e['a'] in fixed or e['b'] in fixed]
assert old_incident==new_incident,'Approved central incident edges changed'
for group in g['groups']:
    group['x']=sum(by[i]['x'] for i in group['nodes'])/len(group['nodes']);group['y']=sum(by[i]['y'] for i in group['nodes'])/len(group['nodes'])
g.update(scope='承認済み中央と初動を保持し、専門投資を避ける有料経路と目的の異なる中間小地域を追加する一つの配置案。',layoutStage='middle-choice-v7',source645Retained=True,fullTreeLayoutComplete=True,runtimeApplied=False)
g['bounds']={k:v for k,v in zip(['minX','minY','maxX','maxY'],[min(n['x'] for n in g['nodes'])-180,min(n['y'] for n in g['nodes'])-180,max(n['x'] for n in g['nodes'])+180,max(n['y'] for n in g['nodes'])+180])}
ledger.update(displayedNodes=len(g['nodes']),displayedGroups=len(g['groups']),connections=len(g['edges']),original645NodesExact=True,central60AndIncidentEdgesExact=True,all15KeysStillOriginalOptionalLeaves=True,sourceGraphUntouched=True,saveScope='preview-only-v7-separate-schema',effectsOfExistingNodesChanged=False)
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'middle-choice-authoring.json').write_text(json.dumps(ledger,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'central-authoring.json').write_text(json.dumps(central,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'status':'AUTHORED','nodes':len(g['nodes']),'groups':len(g['groups']),'edges':len(g['edges'])},ensure_ascii=False))
