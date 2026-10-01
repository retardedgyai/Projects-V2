"""Adapt the recovered v6 geometry, without adopting its numbers or touching saves."""
from pathlib import Path
import json,copy,math,collections,heapq
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets/core-ui/large-tree-preview';OUT.mkdir(parents=True,exist_ok=True)
original=json.loads((ROOT/'docs/references/skill-tree-2026-09-16/v6-graph-original.json').read_text(encoding='utf-8'))
prior=json.loads((OUT/'reference-capabilities.json').read_text(encoding='utf-8'))
catalog=json.loads((OUT/'current-catalog.json').read_text(encoding='utf-8'))
d=copy.deepcopy(original);d['version']='projects-large-tree-diff-v1';d['budget']=64
removed=[n for n in d['nodes'] if 'healer' in n['id'] and not n['id'].startswith('join_') or n['type']=='mastery']
removed_ids={n['id'] for n in removed}
d['nodes']=[n for n in d['nodes'] if n['id'] not in removed_ids]
d['edges']=[e for e in d['edges'] if e['a'] not in removed_ids and e['b'] not in removed_ids]
d['origins']=[o for o in d['origins'] if o['id']!='healer']
for o in d['origins']:
 if o['id']=='templar':o['id']='tank';o['name']='タンク〈仮称〉'
for g in d['groups']:
 g.pop('mastery',None);g['nodes']=[i for i in g['nodes'] if i not in removed_ids]
for n in d['nodes']:
 n['cost']=0 if n['type']=='start' else 1;n['runtimeApplied']=False;n['sourceState']='historical_proposal'
 n['sourceRef']='v6:'+n['id'];n['requirements']=[]
 if n.get('origin')=='templar':n['origin']='tank'
 if n['id']=='origin_templar':n['name']='タンク〈仮称〉の起点'
 n['requirements']=list(set({'magic':'AP','barrier':'SHIELD','healing':'HEAL','parry':'GUARD','bleed':'BLEED_SOURCE','leech':'LIFESTEAL','fire':'FIRE_SOURCE','ice':'ICE','lightning':'LIGHTNING_SOURCE','weakpoint':'WEAKPOINT'}.get(k) for k in n.get('stats',{}) if k in {'magic','barrier','healing','parry','bleed','leech','fire','ice','lightning','weakpoint'}))
by={n['id']:n for n in d['nodes']}
# Replace the inherited three spokes with a branching and merging start area.
# Each existing small has a real proposed stat. No empty routing nodes are added.
braids=[]
for o in d['origins']:
 oldjob='templar' if o['id']=='tank' else o['id'];prefix='opening_'+oldjob+'_'
 local={key:prefix+key for key in ('0_1','0_2','0_3','1_1','1_2','1_3','2_1','2_2','2_3')}
 internal=set(local.values())|{o['root']}
 d['edges']=[e for e in d['edges'] if not(e['a'] in internal and e['b'] in internal)]
 positions={'0_1':(180,-140),'1_1':(180,140),'0_2':(400,-250),'1_2':(400,250),'2_1':(330,0),'2_2':(550,0),'0_3':(760,-300),'1_3':(760,300),'2_3':(810,0)}
 a=o['angle']*math.pi/180;origin_node=by[o['root']]
 for key,(forward,side) in positions.items():
  n=by[local[key]];n['x']=round(origin_node['x']+math.cos(a)*forward-math.sin(a)*side,3);n['y']=round(origin_node['y']+math.sin(a)*forward+math.cos(a)*side,3);n['sourceState']='diff_proposal';n['opening']=True;n['desc']='起点近くで分岐・合流・横断する比較案。同じ目標へ進んでも途中の能力を選べる。'
 links=[('root','0_1'),('root','1_1'),('0_1','0_2'),('0_1','2_1'),('1_1','1_2'),('1_1','2_1'),('2_1','2_2'),('0_2','0_3'),('0_2','2_3'),('1_2','1_3'),('1_2','2_3'),('2_2','0_3'),('2_2','1_3')]
 for left,right in links:
  left=o['root'] if left=='root' else local[left];right=local[right];d['edges'].append({'a':left,'b':right,'diff':True,'startBraid':o['id']})
 braids.append({'origin':o['id'],'root':o['root'],'sharedGoal':local['2_3'],'equalCostRoutes':[[o['root'],local['0_1'],local['0_2'],local['2_3']],[o['root'],local['1_1'],local['1_2'],local['2_3']]],'sideBranch':[o['root'],local['0_1'],local['2_1'],local['2_2'],local['1_3']]})
for e in d['edges']:
 if e.get('points'):
  e['points'][0]=[by[e['a']]['x'],by[e['a']]['y']];e['points'][-1]=[by[e['b']]['x'],by[e['b']]['y']]
oldkeys=[copy.deepcopy(n) for n in d['nodes'] if n['type']=='keystone']
for n in d['nodes']:
 if n['type']=='keystone':
  g=next(g for g in d['groups'] if g['id']==n['group'])
  peer=next(p for p in d['nodes'] if p['type']=='notable' and p.get('theme')==n['theme'])
  n['type']='notable';n['stats']=copy.deepcopy(peer['stats']);n['name']=peer['name']+'〈比較〉';n['desc']='旧Keystone位置を、同分野のまとまった能力へ差し替える案。係数は旧v6の比較値。';n['sourceState']='diff_proposal';n['sourceRef']='v6:'+peer['id']+' / old-slot:'+n['id'];n['requirements']=copy.deepcopy(peer['requirements']);n.pop('rule',None);n.pop('caveat',None)
  if n['id'] not in g['notables']:g['notables'].append(n['id'])
requirements={**prior['requirements'],'AP':'対応装備のAP（ADから自動生成しない）','LIFESTEAL':'吸命MOD等のHP吸収源','MP_CONSUMER':'実際にMPを消費する編成技能','BLEED_SOURCE':'出血を付与する入力源（現行catalogに出血statusなし）','DODGE':'使用可能な回避','MELEE':'近接タグを持つ対応技能','GUARD':'防御姿勢または受け流し技能','FIRE_SOURCE':'炎付与MODまたは炎技能（燃焼の供給とは別）','LIGHTNING_SOURCE':'雷付与MODまたは雷技能（連鎖の供給とは別）','WEAKPOINT':'位置・弱点判定を利用できる攻撃'}
defs=[]
anchors=['g51','g20','g50','g37','g49','g34']
icons=['heal_shield','war_ult','ass_poison','war_breach','war_guard','whirl']
reqs=[['LIFESTEAL'],['MP_CONSUMER'],['BLEED_SOURCE'],['DAMAGE'],['DODGE'],['MELEE']]
for i,n in enumerate(oldkeys):
 defs.append({'name':n['name'],'description':n['desc'],'tradeoff':n['caveat'],'rule':n['rule'],'icon':icons[i],'requirements':reqs[i],'groupAnchor':anchors[i],'sourceState':'historical_keystone_proposal','sourceRef':'v6:'+n['id'],'risks':['旧係数・制約とも未採用。共通の実戦効果は未実装'],'conflicts':[],'referenceJobs':[]})
new_numbers=[1,2,3,10,8,11,13,14,15]
new_anchors=['g16','g35','g19','g10','g31','g17','g27','g47','g9']
for number,anchor in zip(new_numbers,new_anchors):
 n=next(n for n in prior['nodes'] if n['id']==f'k:{number:02}')
 defs.append({k:copy.deepcopy(n.get(k,[] if k in ('requirements','risks','referenceJobs','conflicts') else '')) for k in ('name','description','tradeoff','icon','requirements','risks','referenceJobs','conflicts')})
 defs[-1].update(groupAnchor=anchor,sourceState='new_keystone_proposal',sourceRef=f'prior-draft:k:{number:02}',priorInputId=f'k:{number:02}')
def segdist(p,a,b):
 dx,dy=b[0]-a[0],b[1]-a[1];den=dx*dx+dy*dy
 t=max(0,min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/den)) if den else 0
 return math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy)
def segments(e):
 return list(zip(e.get('points') or [[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]],(e.get('points') or [[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]])[1:]))
for i,key in enumerate(defs,1):
 g=next(g for g in d['groups'] if g['id']==key['groupAnchor']);anchor=by[g['notables'][0]]
 outward=math.atan2(anchor['y'],anchor['x']);candidates=[]
 for distance in (210,260,310,370):
  for offset in (0,.5,-.5,1,-1,1.6,-1.6,2.2,-2.2,math.pi):
   p=(anchor['x']+math.cos(outward+offset)*distance,anchor['y']+math.sin(outward+offset)*distance)
   clearance=min(math.hypot(p[0]-n['x'],p[1]-n['y']) for n in d['nodes'])
   edge_clearance=min(segdist(p,a,b) for e in d['edges'] for a,b in segments(e))
   crossed=[n['id'] for n in d['nodes'] if n['id']!=anchor['id'] and segdist((n['x'],n['y']),(anchor['x'],anchor['y']),p)<55]
   if clearance>=155 and edge_clearance>=75 and not crossed:candidates.append((distance+abs(offset)*15,p))
 if not candidates:raise ValueError('No clear placement '+key['name'])
 _,p=min(candidates)
 key.update(id=f'key_{i:02}',x=round(p[0],3),y=round(p[1],3),type='keystone',stats={},cost=3,runtimeApplied=False,anchor=anchor['id'],group=g['id'],theme=g['theme'])
 key['conflicts']=['key_09'] if i==8 else ['key_08'] if i==9 else []
 d['nodes'].append(key);by[key['id']]=key;d['edges'].append({'a':anchor['id'],'b':key['id'],'points':[[anchor['x'],anchor['y']],[key['x'],key['y']]],'diff':True})
profiles=[]
for p in prior['profiles']:
 p=copy.deepcopy(p);c=next(c for c in catalog['profiles'] if c['start']==p['start'] and c['kit']==p['kit'])
 flags=set(p['flags']);skills=c['skills']
 if any(s['mana']>0 for s in skills):flags.add('MP_CONSUMER')
 if any('MELEE' in s['tags'] for s in skills):flags.add('MELEE')
 flags.add('DODGE')
 if any(s['motion']=='GUARD' for s in skills):flags.add('GUARD')
 if p['weaponUsable'] and any(s['ap']>0 for s in skills) and p['start']=='mage':flags.add('AP')
 if p['element']=='fire' or any(s['element']==1 for s in skills):flags.add('FIRE_SOURCE')
 if p['element']=='lightning' or any(s['element']==3 for s in skills):flags.add('LIGHTNING_SOURCE')
 # Hitting a weak point is conditional; loadout presence does not guarantee it.
 p['flags']=sorted(flags);p['skills']=skills;p.pop('inputs',None);profiles.append(p)
d['profiles']=profiles;d['requirements']=requirements;d['currentCatalog']=catalog
d['startBraids']=braids
d['choices']={}
d['sources']=[{'title':'PoE1 公式パッシブツリーの構造参照','url':'https://www.pathofexile.com/passive-skill-tree'},{'title':'GGG 公式グラフデータ（親側で構造を検証したrevision）','url':'https://github.com/grindinggear/skilltree-export/blob/8bd138b32ea2631455cac5935bfab089f826094f/data.json'}]
d['originalRemoved']=removed;d['originalKeystones']=oldkeys
d['status']='5起点・15候補の比較用差分。全ノードの新共通効果は未適用。係数・費用・総ptは未採用。'
d['pointPolicy']={'candidates':[48,64,80],'default':64,'small':1,'notable':1,'road':1,'keystone':3,'productionAdopted':False,'reason':'まず64ptを比較基準。旧44ptの遠方不足を調べ、1個3ptのKeystoneとNotableへの投資を比較する。強さの最適性は実戦未検証。'}
d['bounds']={'minX':min(n['x'] for n in d['nodes'])-130,'maxX':max(n['x'] for n in d['nodes'])+130,'minY':min(n['y'] for n in d['nodes'])-130,'maxY':max(n['y'] for n in d['nodes'])+130}
d['qa']={'nodes':len(d['nodes']),'edges':len(d['edges']),'groups':len(d['groups']),'nodeTypes':dict(collections.Counter(n['type'] for n in d['nodes']))}
d['schema']='projects.large-tree-diff.v1';d['sourceBase']='7341fe25';d['runtimeApplied']=False
d['sourcePolicy']='v6原本を保持。6起点・44pt・664点・専攻は本番へ未採用。15候補は旧6+比較用9で、名称・効果は未採用。'
(OUT/'graph.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(d['qa'],ensure_ascii=False))
