"""Five starts grow from their preserved twelve-point investments; no geometry edits."""
import ast,copy,hashlib,json,math
from pathlib import Path
import skill_tree_contract_v8 as model

ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview/proposals'
V11=BASE/'workshop-growth-v11';OUT=BASE/'workshop-five-growth-v12';OUT.mkdir(exist_ok=True)
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(n,v):(OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf8')
baseline=read(V11/'candidate-graph.json');g=copy.deepcopy(baseline);by={n['id']:n for n in g['nodes']}
changes=[]
rewards=[
 ('v7g07n4','MPの器と消費を支える',{'mana':12,'efficiency':5},'crystal','v7g07','所有技能のMP容量と消費へ投資する。魔法攻撃や元素MODを新規に供給しない。'),
 ('v7g07n5','資源から次の技へ',{'resource':8,'haste':8},'sword','v7g07','所有する固有資源の獲得と技の再使用へ投資する。元素や残響の追加発動は与えない。'),
 ('g51n6','射撃の一回を重く',{'crit':12,'critMulti':5},'target','v9_ranger','手持ちの攻撃の会心率と会心倍率を育てる。追矢やFocusの追加効果は供給しない。'),
 ('r39_40_3','位置を変え、技を回す',{'move':3,'haste':8},'speed','v9_ranger','移動速度と所有技能の再使用へ投資する。回避や設置技能は新規に与えない。'),
 ('r1_51_2','決め手の直撃を育てる',{'physical':10,'critMulti':6},'sword','v9_assassin','手持ちの物理攻撃と会心倍率へ投資する。印の新しい挙動や毒印は与えない。'),
 ('r1_29_2','接近後に資源を回収',{'attackSpeed':6,'resource':8,'move':2},'speed','v9_assassin','通常命中の手数と所有資源の回収、移動へ投資する。吸命や強制魔術を要求しない。'),
 ('g51n5','射撃側の防御の備え',{'barrier':6,'resist':2},'ward','g51','魔法防御を育て、障壁を所有する編成ではその容量も育てる。障壁を所有しなくても魔法防御の投資として通れる。'),
 ('g51n8','接近側の生命の備え',{'barrier':6,'hp':2},'heart','g51','HP容量を育て、障壁を所有する編成ではその容量も育てる。障壁を所有しなくてもHPの投資として通れる。'),
]
for id,name,stats,icon,group,desc in rewards:
 n=by[id];before=copy.deepcopy(n)
 n.update(type=before['type'] if id in ['g51n5','g51n8'] else 'notable',name=name,stats=stats,icon=icon,group=group,description=desc,desc=desc,buildIntent=desc,sourceState='five_growth_v12_proposal',runtimeApplied=False,inputPolicy='any_usable_benefit',benefitInputs={k:model.EFFECTIVE['statInputs'].get(k,[]) for k in stats})
 changes.append(dict(id=id,before=before,after=copy.deepcopy(n)))
 for gr in g['groups']:
  for field in ['nodes','notables','entrances']:
   if id in gr.get(field,[]) and gr['id']!=group:gr[field].remove(id)
  if gr['id']==group:
   for field in ['nodes']+(['notables'] if n['type']=='notable' else []):
    if id not in gr[field]:gr[field].append(id)
for gr in g['groups']:
 if gr['id']=='v7g07':gr.update(name='器か、次の技か',role='MP容量・消費と、所有資源・再使用を選び分ける。')
assert len(g['nodes'])==850 and g['edges']==baseline['edges']
assert all((n['x'],n['y'])==(by[n['id']]['x'],by[n['id']]['y']) for n in baseline['nodes'])
assert all(by[n['id']]==n for n in baseline['nodes'] if n['id'].startswith(('origin_','opening_')))
g.update(scope='全850座標・全1011辺・中央内容を保持。既存8点の報酬と4点の表示種別を更新し、障壁未所有時の二つの入口迂回を解消。五始点で12ptから24ptへ育てる。',layoutStage='five-growth-v12',runtimeApplied=False)
model.GRAPH=g;model.BY=by;model.ADJ={id:[] for id in by}
for e in g['edges']:model.ADJ[e['a']].append(e['b']);model.ADJ[e['b']].append(e['a'])
old_examples=read(V11/'route-examples.json');examples=[]
for e in old_examples:
 spec={k:e[k] for k in ['id','label','profileId','siphon','targets','budget','note']};current=model.example(spec)
 current.update({k:v for k,v in e.items() if k not in current})
 if e['id'].endswith('-12'):
  assert current['learned']==e['learned'] and current['stats']==e['stats']
 examples.append(current)
menus=[
 ('mage',0,'MPの器と消費を積む',['v7g07n4','g12n2','opening_mage_2_1']),
 ('mage',1,'資源から技へ、守りも戻す',['v7g07n5','v7g06n5','v9_mage_0_1']),
 ('ranger',0,'射撃の会心と直撃を育てる',['g51n6','v9_assassin_0_5','opening_ranger_2_2']),
 ('ranger',1,'位置取りと所有技能を回す',['r39_40_3','v9_mage_1_5','opening_ranger_2_1']),
 ('assassin',0,'決め手に会心を重ねる',['r1_51_2','v9_ranger_0_5']),
 ('assassin',1,'手数と資源を越境して積む',['r1_29_2','v9_warrior_1_4']),
]
for job,arm,label,targets in menus:
 seed=next(e for e in examples if e['id']==f'{job}-direction-{arm}-12')
 chosen=model.example(dict(id=f'{job}-growth-{arm}-24',label=job+' / '+label+' / 同消費24pt',profileId=seed['profileId'],siphon=False,targets=seed['targets']+targets,budget=24,note='元の12pt全取得を保持し、役割のある目標へ追加12pt。追加MODなし・通路HP。職業技能や専用MODは供給しない。'))
 assert chosen['spent']==24 and chosen['remaining']==0,(job,arm,chosen['spent'])
 assert set(seed['learned'])<=set(chosen['learned']) and not model.Allocation(chosen['profileId'],owned=chosen['learned']).dead()
 chosen.update(growth=True,baseExampleId=seed['id'],baseDirection=arm,direction=None,play=label,gear='既存技能と自由混合の装備を育てる。専用MOD・強制魔術・防具職業制限は追加しない。',focusIds=chosen['learned'])
 examples.append(chosen)
goal_ids={'warrior':['v7g14n2','v7g14n3'],'tank':['v7g06n3','v7g06n5'],'mage':['v7g07n4','v7g07n5'],'ranger':['g51n6','r39_40_3'],'assassin':['r1_51_2','r1_29_2']}
# Extract the already inspected pure atlas function; do not execute the V11 writer.
src=(ROOT/'scripts/build-skill-tree-growth-v11.py').read_text(encoding='utf8');tree=ast.parse(src)
atlas_node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='atlas')
import heapq
exec(compile(ast.Module(body=[atlas_node],type_ignores=[]),'inspected-atlas','exec'))
fixtures={mode:dict(current=atlas(g,mode),previous=atlas(baseline,mode)) for mode in ['fixed','owned']}
costs={id:{job:table.get(id) for job,table in fixtures['fixed']['current'].items()} for ids in goal_ids.values() for id in ids}
for job in ['mage','ranger','assassin']:
 for id in goal_ids[job]:
  assert all(costs[id][job]<costs[id][other] for other in costs[id] if other!=job),(job,id,costs[id])
unions=[]
for job in goal_ids:
 es=[e for e in examples if e.get('growth') and e['profileId'].startswith(job+':')];assert len(es)==2
 ids=set(es[0]['learned'])|set(es[1]['learned']);spent=sum(by[i]['cost'] for i in ids);assert spent>24
 unions.append(dict(job=job,unionSpend=spent,ids=[e['id'] for e in es]))
common=sorted(n['id'] for n in baseline['nodes'] if n['type']=='notable' and not n['id'].startswith(('v9_','opening_')) and all(fixtures['fixed']['previous'][j].get(n['id']) is not None for j in ['warrior','tank']))
assert len(common)==92
def dominance(a):
 strict={j:0 for j in a};ties=0
 for id in common:
  finite={j:v[id] for j,v in a.items() if v.get(id) is not None};best=min(finite.values());w=[j for j,c in finite.items() if c==best]
  if len(w)==1:strict[w[0]]+=1
  else:ties+=1
 return dict(strictMinimumByStart=strict,sharedMinimumTargets=ties)
authoring=dict(source='V11',runtimeApplied=False,nodeChanges=changes,addedNodes=0,addedRoads=0,addedEdges=0,removedEdges=0,upgradedSmallPoints=1,convertedRoadsToNotables=3,allCoordinatesExact=True,allEdgesExact=True,centralContentsExact=True,groupGoalCosts=costs,goalIdsByJob=goal_ids,two24PointUnionCosts=unions,common92Ids=common,common92Sha256=hashlib.sha256('\n'.join(common).encode()).hexdigest(),common92Dominance={mode:{version:dominance(fixtures[mode][version]) for version in ['previous','current']} for mode in fixtures})
write('candidate-graph.json',g);write('route-examples.json',examples);write('cost-atlas-fixtures.json',fixtures);write('five-growth-authoring.json',authoring)
print(json.dumps(dict(examples=len(examples),costs=costs,unions=unions,dominance=authoring['common92Dominance']),ensure_ascii=False))
