"""Small, source-owned start specialization study; preserved V8 is read-only."""
import ast, collections, copy, hashlib, heapq, json, math, random, sys
from pathlib import Path
import numpy as np
import skill_tree_contract_v8 as model

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'assets/core-ui/large-tree-preview'
OLD = BASE / 'proposals/workshop-contract-v8'
OUT = BASE / 'proposals/workshop-specialization-v9'
OUT.mkdir(parents=True, exist_ok=True)
baseline = model.read(OLD / 'candidate-graph.json')
g = copy.deepcopy(baseline)
by = {n['id']: n for n in g['nodes']}
central = model.read(OLD / 'central-authoring.json')
fixed = set(central['centralOpeningIds'])
protected_xy = {i: (by[i]['x'], by[i]['y']) for i in fixed}
old_edges = copy.deepcopy(g['edges'])
src = (ROOT / 'scripts/build-skill-tree-middle-choice-v7.py').read_text(encoding='utf8')
names = {'xy', 'rounded', 'face_inventory', 'inside', 'Geometry', 'route_in_face'}
defs = [n for n in ast.parse(src).body if isinstance(n, (ast.FunctionDef, ast.ClassDef)) and n.name in names]
exec(compile(ast.Module(body=defs, type_ignores=[]), 'inspected-v7-geometry', 'exec'))
geom = Geometry(); geom.refresh()
faces = face_inventory(g)
ledger = dict(baseline='V8 / 8d384a4b', runtimeApplied=False, adopted=False, nodeChanges=[], addedNodes=[], addedEdges=[], representativeGate=None)

# These are aggregate passive proposals, not newly granted class mechanics.
# Every mandatory node retains a universally usable or owned DAMAGE/resource benefit.
specs = [
 dict(job='warrior', stem='warrior', face=36, title='打撃を選び、闘気を使う', aim=(-650,-1000), arms=[
  dict(label='一撃と再使用', theme='physical', icon='sword', stats=[{'physical':3,'hp':1},{'physical':3,'resource':2},{'physical':8,'haste':3},{'physical':3,'armor':2},{'haste':3,'resource':2},{'physical':12,'haste':6}], play='主力の一回を重くし、再使用の機会へ投資する。既存の受け流し・反撃は所有技能がある時だけ使う。', gear='攻撃力・スキルダメージ・再使用速度を優先候補にする。余波の範囲と威力の交換はMOD側に残す。'),
  dict(label='通常攻撃と資源獲得', theme='speed', icon='speed', stats=[{'attackSpeed':2,'hp':1},{'resource':3,'physical':2},{'attackSpeed':5,'resource':6},{'attackSpeed':2,'hp':2},{'resource':3,'armor':2},{'attackSpeed':7,'resource':8}], play='通常攻撃を挟んで固有資源を回収し、消費技へつなぐ。新たな連撃や追加発動は与えない。', gear='通常攻撃ダメージ・攻撃速度を優先候補にし、MPと耐久を補う。専用MODの追加pulseは増やさない。')]),
 dict(job='tank', stem='templar', face=13, title='耐える備え、守りを回す', aim=(500,-3300), arms=[
  dict(label='装甲と継戦', theme='armor', icon='shield', stats=[{'armor':3,'hp':2},{'hp':3,'regen':2},{'armor':8,'hp':5},{'armor':3,'resist':2},{'hp':3,'regen':2},{'armor':12,'hp':7}], play='装甲とHPへ投資し、敵の前で通常攻撃と既存の挑発を継続する。装備種類や職業で防具を制限しない。', gear='HP・被ダメージ軽減・耐久を優先候補にする。防具は自由に混合し、既存障壁MODは任意に選ぶ。'),
  dict(label='守りの再使用', theme='ward', icon='ward', stats=[{'haste':3,'hp':2},{'armor':3,'efficiency':2},{'barrier':6,'haste':5,'hp':2},{'haste':3,'resist':2},{'efficiency':3,'hp':2},{'barrier':10,'haste':7,'armor':4}], play='手持ちの守り・吸引・攻撃の再使用とMP負担を育てる。障壁量はSHIELDを所有する時だけ有効で、障壁なしでも必須経路の恩恵は残る。', gear='再使用速度・MP回復を優先候補にし、障壁を使う場合だけ庇護を検討する。吸引の変性は重力MOD側に残す。')]),
 dict(job='mage', stem='mage', face=13, title='術の器か、属性の循環か', aim=(2110,-3400), arms=[
  dict(label='MPを蓄え、術を重く', theme='mana', icon='crystal', stats=[{'mana':3,'hp':1},{'magic':3,'mana':2},{'mana':8,'magic':5},{'mana':3,'resist':2},{'efficiency':3,'hp':1},{'mana':10,'magic':8,'efficiency':3}], play='MPの器と手持ちのAP攻撃へ投資する。消費の重い技能を使い続ける備えで、魔術やAP技能は与えない。', gear='スキルダメージ・最大MP・MP回復を優先候補にする。元素MODの所有は必須にしない。'),
  dict(label='資源獲得と再使用', theme='resource', icon='clock', stats=[{'resource':3,'hp':1},{'haste':3,'mana':2},{'resource':7,'haste':5},{'resource':3,'resist':2},{'efficiency':3,'hp':1},{'resource':10,'haste':7,'efficiency':3}], play='固有資源の獲得と技の再使用へ投資する。現行メイジでは異属性命中の獲得も育つが、属性源そのものや残響は与えない。', gear='再使用速度・キャスト短縮を優先候補にし、実際の所有技能に合わせて属性MODを任意で選ぶ。残響の追加発動はMOD側に残す。')]),
 dict(job='ranger', stem='ranger', face=33, title='狙いを定める、位置を選ぶ', aim=(3090,-1550), arms=[
  dict(label='同じ標的への集中', theme='crit', icon='target', stats=[{'damage':3,'hp':1},{'crit':4,'resource':2},{'crit':8,'critMulti':4},{'damage':3,'resist':2},{'crit':4,'hp':1},{'crit':12,'critMulti':6}], play='会心と直撃へ投資し、同じ標的へ攻撃を続ける。静止・集中の現行効果はゲーム側の所有技能・職業処理のまま。', gear='攻撃力・会心率・会心倍率を優先候補にする。追矢は任意のMODとして残す。'),
  dict(label='移動と設置の回転', theme='move', icon='boot', stats=[{'move':2,'hp':1},{'haste':3,'resource':2},{'move':3,'haste':6},{'haste':3,'resist':2},{'efficiency':3,'hp':1},{'move':4,'haste':8,'resource':4}], play='移動速度と再使用へ投資し、手持ちの回避・設置・射撃を組み合わせる。新しい罠や回避後の追加射撃は与えない。', gear='移動速度・再使用速度・MP回復を優先候補にする。装備・技能で選ぶ設置効果を増やさない。')]),
 dict(job='assassin', stem='assassin', face=31, title='決め手と、離脱後の次手', aim=(1950,830), arms=[
  dict(label='印を消費する決め手', theme='physical', icon='fang', stats=[{'physical':3,'hp':1},{'critMulti':3,'resource':2},{'physical':8,'critMulti':4},{'physical':3,'resist':2},{'crit':4,'hp':1},{'physical':12,'critMulti':6}], play='直撃と会心倍率へ投資し、印を付けて手持ちの消費技を当てる。印や敵低HPの職業効果はゲーム側に残し、自身低HP型は追加しない。', gear='攻撃力・スキルダメージ・会心倍率を優先候補にする。毒印は所有する場合だけ働き、ツリーから毒を与えない。'),
  dict(label='接近離脱と資源回収', theme='speed', icon='speed', stats=[{'attackSpeed':2,'hp':1},{'move':2,'resource':2},{'attackSpeed':5,'resource':6},{'move':2,'resist':2},{'haste':3,'hp':1},{'attackSpeed':7,'resource':8,'move':2}], play='通常攻撃による資源獲得と移動へ投資し、次の接近機会を作る。回避リセット・毒・出血を新しく与えない。', gear='攻撃速度・通常攻撃ダメージ・移動速度を優先候補にする。毒印や吸命を選ぶ場合も独立の所有MODとして扱う。')])
]

opening = {
 'warrior': [[{'physical':4,'resource':2},{'physical':4,'hp':2},{'physical':6,'resource':3}], [{'hp':3,'armor':3},{'hp':3,'armor':3},{'hp':5,'armor':4}], [{'attackSpeed':2,'resource':2},{'attackSpeed':2,'hp':2},{'attackSpeed':3,'resource':3}]],
 'tank': [[{'hp':3,'resist':2},{'hp':3,'armor':2},{'hp':5,'resist':4}], [{'armor':5,'hp':2},{'armor':5,'hp':2},{'armor':7,'hp':3}], [{'hp':2,'barrier':3},{'hp':2,'barrier':3},{'hp':4,'haste':3}]],
 'mage': [[{'magic':3,'mana':2},{'magic':3,'mana':2},{'magic':5,'mana':4}], [{'mana':3,'resource':2},{'mana':3,'resource':2},{'efficiency':5,'mana':4}], [{'hp':2,'barrier':3},{'hp':2,'barrier':3},{'hp':4,'resist':4}]],
 'ranger': [[{'damage':4,'crit':3},{'damage':4,'resource':2},{'damage':6,'crit':5}], [{'move':2,'resource':2},{'haste':3,'hp':2},{'move':3,'haste':4}], [{'crit':4,'hp':1},{'crit':4,'resist':2},{'crit':6,'critMulti':3}]],
 'assassin': [[{'physical':4,'resource':2},{'physical':4,'hp':2},{'physical':6,'critMulti':3}], [{'attackSpeed':2,'resource':2},{'attackSpeed':2,'move':2},{'attackSpeed':3,'resource':4}], [{'move':2,'hp':2},{'move':2,'resist':2},{'move':3,'hp':3}]]
}
plays={
 'warrior':['主力の一回を重くし、再使用の機会へ投資する。受け流し技能を使う編成では、反撃の一回も重くする。','通常攻撃を挟んで固有資源を回収し、消費技へつなぐ。手数と資源獲得に配分して闘気をためる。'],
 'tank':['装甲とHPへ投資し、敵の前で通常攻撃と既存の挑発を継続する。防具は部位ごとに自由に混ぜて補う。','手持ちの守り・吸引・攻撃の再使用とMP負担を育てる。障壁を持つ編成は付与量も育ち、それ以外は装甲とHPの備えが残る。'],
 'mage':['MPの器と手持ちの魔法攻撃へ投資する。消費の重い技能を使い続ける備えを優先する。','固有資源の獲得と技の再使用へ投資する。異なる属性の技能を持つ編成では、命中で回収して消費技へつなぐ。'],
 'ranger':['会心と直撃へ投資し、同じ標的へ攻撃を続ける。移動よりも標的への継続命中を優先する。','移動速度と再使用へ投資し、手持ちの回避・設置・射撃を組み合わせる。位置を変えて、次の射撃・設置の機会を作る。'],
 'assassin':['直撃と会心倍率へ投資し、印を付けて手持ちの消費技を当てる。短い攻撃機会の一回を重くする。','通常攻撃による資源獲得と移動へ投資し、次の接近機会を作る。離脱後の再接近と資源回収へ配分する。']}
lane_names={'warrior':['打撃と闘気','前線の備え','通常攻撃の備え'],'tank':['継戦の備え','装甲の備え','守りの備え'],'mage':['術とMPの器','資源循環の備え','耐久の備え'],'ranger':['集中射撃の備え','位置取りの備え','会心の備え'],'assassin':['決め手の備え','接近回収の備え','離脱の備え']}
for s in specs:
 for a,arm in enumerate(s['arms']):arm['play']=plays[s['job']][a]
next(s for s in specs if s['job']=='tank')['arms'][0]['icon']='ward'
next(s for s in specs if s['job']=='tank')['arms'][1]['icon']='temp_sanctuary'
def edge(a,b,reason):
 assert geom.clear(xy(a),xy(b),ignore=(a,b),clearance=70.5), ('unclear edge',a,b)
 e=dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind='specialization-v9',role=reason,points=[rounded(xy(a)),rounded(xy(b))],crossingGaps=[])
 g['edges'].append(e);ledger['addedEdges'].append(copy.deepcopy(e));geom.refresh()

# Independent Tank outlet: actual useful purchases, local geometry, no global road tax.
anchor='opening_templar_1_3';portal='r6_35_1';pos=(xy(anchor)+xy(portal))/2
n=copy.deepcopy(next(n for n in g['nodes'] if n['type']=='road'))
n.update(id='v9road_tank_outlet',x=float(pos[0]),y=float(pos[1]),name='装甲から外側へ',group=None,stats={},sourceState='specialization_v9_proposal',sourceRef='V9:independent Tank outlet',description='装甲側から一般の外側経路へ進む有料出口。通路の恩恵は現在使える能力から選ぶ。')
g['nodes'].append(n);by[n['id']]=n;geom.refresh();ledger['addedNodes'].append(n['id']);edge(anchor,n['id'],'Independent armor-side outlet');edge(n['id'],portal,'Useful paid exit to existing general network')

def graph_model():
 model.GRAPH=g;model.BY=by;model.ADJ={i:[] for i in by}
 for e in g['edges']:model.ADJ[e['a']].append(e['b']);model.ADJ[e['b']].append(e['a'])
def costs(target):
 graph_model();flags=model.PROFILES['warrior:support:plain:standard']['flags']
 return {job:sum(by[i]['cost'] for i in model.Allocation(job+':support:plain:standard',flags=flags).route(target)) for job in opening}

arm_faces={'warrior':[44,36],'tank':[13,13],'mage':[13,33],'ranger':[33,31],'assassin':[31,44]}
arm_aims={'warrior':[(-340,240),(-640,-640)],'tank':[(650,-3300),(-100,-2800)],'mage':[(2020,-3550),(2800,-2220)],'ranger':[(3100,-1100),(2370,290)],'assassin':[(1550,820),(700,940)]}
def author_job(s):
 for lane,values in enumerate(opening[s['job']]):
  for depth,stats in enumerate(values,1):
   id=f"opening_{s['stem']}_{lane}_{depth}";n=by[id];before=copy.deepcopy(n)
   n.update(stats=stats,primary=next(iter(stats)),benefitInputs={k:model.EFFECTIVE['statInputs'].get(k,[]) for k in stats},name=lane_names[s['job']][lane]+[' I',' II','・入口'][depth-1],description='直外側の専門帯へ進む初期能力の比較案。中央の位置・線・費用は保持。隣職の終端だけで専門帯の主要効果を取り切れない。',sourceState='specialization_v9_proposal')
   if s['job']=='tank':n['icon']='temp_sanctuary' if lane==2 else 'ward'
   ledger['nodeChanges'].append(dict(id=id,before=before,after=copy.deepcopy(n)))
 # Deterministic routing in existing empty faces. No old node or wire moves.
 # Retry only this new twig pair if the first arm consumes the second arm's portal.
 initial_g=copy.deepcopy(g);initial_ledger=copy.deepcopy(ledger)
 for attempt in range(32):
  if attempt:
   g.clear();g.update(copy.deepcopy(initial_g));by.clear();by.update({n['id']:n for n in g['nodes']});ledger.clear();ledger.update(copy.deepcopy(initial_ledger));geom.refresh()
  rng=random.Random(9300+attempt+sum(map(ord,s['job'])))
  ok=True
  arm_order=[0,1] if attempt%2==0 else [1,0]
  for arm_index in arm_order:
   face=faces[arm_faces[s['job']][arm_index]];lo=face['points'].min(axis=0);hi=face['points'].max(axis=0)
   candidates=[]
   for x in np.arange(lo[0]+40,hi[0]-35,50):
    for y in np.arange(lo[1]+40,hi[1]-35,50):
     p=np.array([x,y])
     if inside(p,face) and geom.wire_distance(p)>=74 and np.min(np.linalg.norm(geom.N-p,axis=1))>=115:
      candidates.append(p)
   arm=s['arms'][arm_index];previous=f"opening_{s['stem']}_{arm_index}_3";created=[]
   anchor_xy=xy(previous);visits=[0]
   def planned_chain(points):
    visits[0]+=1
    if len(points)==6:return points
    if visits[0]>3000:return None
    start=points[-1] if points else anchor_xy;history=[anchor_xy]+points;available=[]
    for p in candidates:
     d=float(np.linalg.norm(p-start))
     if not 115<=d<=620 or any(np.linalg.norm(p-q)<115 for q in history):continue
     if not geom.clear(start,p,ignore=(previous,) if not points else (),clearance=70.5,face=face):continue
     if points:
      v=p-start;den=float(v@v)
      if any(np.linalg.norm(q-(start+np.clip(float((q-start)@v)/den,0,1)*v))<70.5 for q in history[:-1]):continue
      if len(history)>1:
       aa=np.array(history[:-1]);dd=np.array(history[1:])-aa;vv=p-aa;ts=np.clip((vv*dd).sum(axis=1)/np.maximum((dd*dd).sum(axis=1),1e-12),0,1)
       if np.min(np.linalg.norm(vv-ts[:,None]*dd,axis=1))<70.5:continue
       cross=v[0]*dd[:,1]-v[1]*dd[:,0];w=aa-start;nz=np.abs(cross)>1e-8;t=np.zeros(len(cross));u=t.copy();t[nz]=(w[nz,0]*dd[nz,1]-w[nz,1]*dd[nz,0])/cross[nz];u[nz]=(w[nz,0]*v[1]-w[nz,1]*v[0])/cross[nz]
       if np.any(nz&(t>1e-7)&(t<1-1e-7)&(u>-1e-7)&(u<1+1e-7)):continue
       col=(~nz)&(np.abs(w[:,0]*v[1]-w[:,1]*v[0])<1e-7)
       if np.any(col):
        low=(w[col]@v)/den;high=((w[col]+dd[col])@v)/den
        if np.any(np.minimum(1,np.maximum(low,high))-np.maximum(0,np.minimum(low,high))>1e-7):continue
     score=d*.45+float(np.linalg.norm(p-np.array(arm_aims[s['job']][arm_index])))*.48+rng.uniform(0,190)
     available.append((score,p))
    for _,p in sorted(available,key=lambda z:z[0])[:14]:
     answer=planned_chain(points+[p])
     if answer is not None:return answer
    return None
   plan=planned_chain([])
   if plan is None:
    if attempt<3:print(s['job'],'retry',attempt,'arm',arm_index,'no six-point route','visits',visits[0],flush=True)
    ok=False;break
   for depth,stats in enumerate(arm['stats']):
    p=plan[depth];id=f"v9_{s['job']}_{arm_index}_{depth}"
    n=dict(id=id,x=round(float(p[0]),5),y=round(float(p[1]),5),type='notable' if depth in (2,5) else 'small',name=arm['label']+([' I',' II','・要点',' III',' IV','・深掘り'][depth]),stats=stats,group='v9_'+s['job'],theme=arm['theme'],icon=arm['icon'],cost=1,runtimeApplied=False,sourceState='specialization_v9_proposal',sourceRef='V9:'+s['job']+':'+str(arm_index),requirements=[],effectState='unimplemented_proposal',inputPolicy='any_usable_benefit',benefitInputs={k:model.EFFECTIVE['statInputs'].get(k,[]) for k in stats},description=arm['play'],buildIntent=arm['play'],equipmentPreference=arm['gear'])
    g['nodes'].append(n);by[id]=n;ledger['addedNodes'].append(id);geom.refresh();edge(previous,id,'Owned-source local specialization');previous=id;created.append(id)
   if not ok:break
  if ok:
   nodes=[n for n in g['nodes'] if n.get('group')=='v9_'+s['job']];center=np.mean([[n['x'],n['y']] for n in nodes],axis=0)
   template=dict(id='v9_'+s['job'],name=s['title'],theme=s['arms'][0]['theme'],x=float(center[0]),y=float(center[1]),labelX=float(center[0]),labelY=float(center[1]),pattern='two_specialization_spurs',nodes=[n['id'] for n in nodes],notables=[n['id'] for n in nodes if n['type']=='notable'],entrances=[f"v9_{s['job']}_{a}_0" for a in (0,1)],outline=[],role='同消費12ptで二方向を比較。両終端を取る配分には追加投資が必要。',runtimeApplied=False)
   g['groups'].append(template);s['goals']=[f"v9_{s['job']}_{a}_5" for a in (0,1)];s['nodeIds']=[n['id'] for n in nodes];s['placementAttempt']=attempt
   print(s['job'],'placement attempt',attempt,flush=True);return
 raise ValueError(('Cannot author clear specialization',s['job']))

# Test the representative behavior before extending the other three starts.
for s in specs[:2]:author_job(s)
w=costs('v9_warrior_0_5');t=costs('v9_tank_0_5')
assert w['warrior']<w['tank'] and t['tank']<t['warrior'],(w,t)
ledger['representativeGate']=dict(status='PASS_PYTHON_PENDING_ACTUAL_JS',warriorStrike=w,tankDefense=t,checkedBeforeOtherThree=True)
for s in specs[2:]:author_job(s)
graph_model()
examples=[]
for s in specs:
 for a,arm in enumerate(s['arms']):
  other=1-a
  targets=[s['goals'][a]]+[f"opening_{s['stem']}_{other}_{depth}" for depth in (1,2,3)]
  e=model.example(dict(id=f"{s['job']}-direction-{a}-12",label=s['job']+' / '+arm['label']+' / 同消費12pt',profileId=s['job']+':support:plain:standard',siphon=False,targets=targets,budget=12,note='同じ職業・編成・追加MODなし・通路HPで12ptを使用。'+arm['play']))
  assert e['spent']==12 and e['remaining']==0 and not model.Allocation(e['profileId'],owned=e['learned']).dead(),e
  e.update(direction=arm['label'],play=arm['play'],gear=arm['gear'],focusIds=[model.ROOTS[s['job']]]+s['nodeIds']+[f"opening_{s['stem']}_{lane}_{depth}" for lane in (0,1,2) for depth in (1,2,3)])
  examples.append(e)
for id,pid,targets in [('warrior-tank-fusion-24','warrior:support:plain:standard',['v9_warrior_0_5','v9_tank_0_5']),('mage-ranger-fusion-24','mage:support:plain:standard',['v9_mage_1_5','v9_ranger_1_5'])]:
 e=model.example(dict(id=id,label='24pt以内の越境 / '+id.split('-fusion')[0]+'（未消費あり）',profileId=pid,siphon=False,targets=targets,budget=24,note='24ptは比較用の上限。使用値と未消費値を明示する越境例で、24ptを使い切る同消費比較ではない。技能やMODの新しい能力源は与えない。'))
 assert e['spent']<=24 and not model.Allocation(pid,owned=e['learned']).dead(),e
 e.update(fusion=True,focusIds=list(e['learned']));examples.append(e)
for e in examples:e['stats']=dict(sorted(e['stats'].items()));e['focusIds']=sorted(e['focusIds'])
g.update(layoutStage='specialization-v9',scope='5始点×2方向の小さな専門帯。承認中央幾何を保持し初期内容とTank独立出口を比較。',runtimeApplied=False)
assert all(protected_xy[i]==(by[i]['x'],by[i]['y']) for i in fixed)
assert g['edges'][:len(old_edges)]==old_edges
assert all(n==by[n['id']] for n in baseline['nodes'] if n['id'] not in fixed)
assert len(g['nodes'])==len(baseline['nodes'])+61
# Incremental geometry evidence: old drawing unchanged; every new node/wire checked.
geom.refresh();new=set(ledger['addedNodes'])
clearances=[]
for id in new:
 p=xy(id);v=p-geom.A;ts=np.clip((v*geom.D).sum(axis=1)/geom.den,0,1);ds=np.linalg.norm(v-ts[:,None]*geom.D,axis=1);ds[np.array([id in ends for ends in geom.ends])]=float('inf');clearances.append(float(np.min(ds)))
assert min(clearances)>=70.5, min(clearances)
def write(name,value): (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf8')
write('candidate-graph.json',g);write('specialization-authoring.json',ledger);write('specialization-directions.json',specs);write('route-examples.json',examples)
write('geometry-verification.json',dict(status='PASS',oldNodeCoordinatesExact=True,oldEdgesExact=True,central60CoordinatesExact=True,newNodes=len(new),newEdges=len(ledger['addedEdges']),minNewNonIncidentWireDistance=min(clearances),hiddenCrossings=0,newEdgesCheckedByGeometryClear=True))
write('input-overlay.json',model.OVERLAY)
def atlas(graph,mode):
 adj={n['id']:[] for n in graph['nodes']};ns={n['id']:n for n in graph['nodes']}
 for e in graph['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
 result={}
 for job,root in model.ROOTS.items():
  flags=set(model.PROFILES[('warrior' if mode=='fixed' else job)+':support:plain:standard']['flags']);d={root:0};q=[(0,root)]
  while q:
   c,u=heapq.heappop(q)
   if d[u]!=c:continue
   for v in adj[u]:
    n=ns[v]
    if n['type'] in ('start','keystone'):continue
    raw={'hp':2} if n['type']=='road' else n['stats']
    if not any(set(model.EFFECTIVE['statInputs'].get(k,[]))<=flags for k in raw):continue
    nc=c+n['cost']
    if nc<d.get(v,math.inf):d[v]=nc;heapq.heappush(q,(nc,v))
  result[job]=d
 return result
write('cost-atlas-fixtures.json',{mode:dict(current=atlas(g,mode),previous=atlas(baseline,mode)) for mode in ('fixed','owned')})
print(json.dumps(dict(nodes=len(g['nodes']),groups=len(g['groups']),edges=len(g['edges']),representative=ledger['representativeGate'],examples=[(e['id'],e['spent'],e['stats']) for e in examples]),ensure_ascii=False))
