"""Review corrections using existing nodes: routes, per-stat inputs, optional specialist branches."""
import math,collections
from large_tree_rules import STAT_INPUTS,FLAG_KINDS,LOST_STATS,node_input
from large_tree_topology import chain_report,limit_chains

def correct(d):
 before=chain_report(d)
 by={n['id']:n for n in d['nodes']};existing={tuple(sorted((e['a'],e['b']))) for e in d['edges']}
 def add(a,b,kind,**details):
  pair=tuple(sorted((a,b)))
  if a==b or pair in existing:return False
  existing.add(pair);d['edges'].append({'a':a,'b':b,'diff':True,'reviewCorrection':kind,**details});return True
 # Reuse all 31 Warrior approach nodes. The short entrance and longer side
 # route merge; optional loops and lateral links retain density and investments.
 ids={n['id'] for n in d['nodes'] if n['id'].startswith('link_warrior_')}
 d['edges']=[e for e in d['edges'] if e['a'] not in ids and e['b'] not in ids]
 existing={tuple(sorted((e['a'],e['b']))) for e in d['edges']}
 proposals={2:('踏み込む威力',{'physical':2}),3:('踏み止まる備え',{'hp':2}),
  4:('打ち込む速度',{'attackSpeed':2}),5:('闘気の運用',{'resource':3}),
  8:('鎧を重ねる',{'armor':3}),9:('間合いを広げる',{'aoe':3}),11:('踏み替える足',{'move':2})}
 positions={1:(.10,0),2:(.30,-110),3:(.30,110),4:(.70,-120),5:(.58,120),6:(.83,80),
  7:(.43,-240),8:(.61,-250),9:(.40,270),10:(.48,260),11:(.72,10)}
 placed=[n for n in d['nodes'] if n['id'] not in ids]
 approaches=[]
 for lane in range(3):
  start=by[f'opening_warrior_{lane}_3'];end=by[f'join_warrior_{lane}'];dx=end['x']-start['x'];dy=end['y']-start['y'];length=math.hypot(dx,dy)
  nodes=sorted((by[i] for i in ids if i.startswith(f'link_warrior_{lane}_')),key=lambda n:int(n['id'].rsplit('_',1)[1]))
  def nid(i):return f'link_warrior_{lane}_{i}'
  for n in nodes:
   number=int(n['id'].rsplit('_',1)[1]);t,offset=positions[number]
   choices=[]
   for adjust in (0,75,-75,150,-150,225,-225):
    x=start['x']+t*dx-dy/length*(offset+adjust);y=start['y']+t*dy+dx/length*(offset+adjust)
    clearance=min(math.hypot(x-o['x'],y-o['y']) for o in placed)
    if clearance>=85:choices.append((abs(adjust),x,y))
   if not choices:raise ValueError('Approach collision '+n['id'])
   _,n['x'],n['y']=min(choices);n['x']=round(n['x'],3);n['y']=round(n['y'],3);placed.append(n)
   n.update(sourceState='review_diff_proposal',sourceRef='v6:'+n['id'],desc='長い一本道から、途中の分岐・合流と地域入口へ再配分した比較案。')
   if number in proposals:n['type']='small';n['name'],n['stats']=proposals[number];n['requirements']=[]
  pairs=[('s',1),(1,2),(1,3),(2,4),(3,5),(4,'e'),(5,6),(6,'e'),(2,7),(7,8),(8,4),(3,9),(9,10),(10,5)]
  if len(nodes)==11:pairs += [(4,11),(11,6)]
  for a,b in pairs:add(start['id'] if a=='s' else nid(a),end['id'] if b=='e' else nid(b),'warrior-approach')
  approaches.append({'lane':lane,'nodes':[n['id'] for n in nodes],'entrance':end['id'],
    'shortRoute':[start['id'],nid(1),nid(2),nid(4),end['id']],
    'sideRoute':[start['id'],nid(1),nid(3),nid(5),nid(6),end['id']]})
 for lane in range(2):add(f'link_warrior_{lane}_8',f'link_warrior_{lane+1}_7','warrior-lateral')
 add('link_warrior_2_2','g7n2','warrior-region-entrance')
 d['warriorApproaches']=approaches
 # These Mage investments now retain a mana benefit before a shield source is
 # equipped. The conditional barrier contribution remains visibly unsupported.
 for suffix in (1,2):
  n=by[f'opening_mage_2_{suffix}'];n['stats']['mana']=3;n['name']=f'術の備え {"I" if suffix==1 else "II"}'
  n.update(sourceState='review_diff_proposal',desc='MPを使う基本編成の備え。障壁の増加は、障壁を発生させる入力を持つ場合のみ。')
 d['statInputs']=STAT_INPUTS;d['flagKinds']=FLAG_KINDS;d['lostStats']={k:sorted(v) for k,v in LOST_STATS.items()}
 for n in d['nodes']:
  n['effectState']='unimplemented_proposal'
  if n['type']!='keystone':
   n['requirements']=[];n['inputPolicy']='any_usable_benefit'
   n['benefitInputs']={k:STAT_INPUTS[k] for k in n.get('stats',{})}
 d['assumptions']=[
  {'id':'weakpoint','label':'仮の弱点命中条件','flags':['WEAKPOINT'],'kind':'unwired_context','note':'戦闘条件を模した入力テスト。現行装備の確定能力としては扱わない。'},
  {'id':'bleed','label':'仮の出血供給','flags':['BLEED_SOURCE'],'kind':'unsupported_source','note':'合法な出血源が接続された場合の入力テスト。現行catalogには供給なし。'},
  {'id':'heal','label':'仮の治癒供給','flags':['HEAL'],'kind':'unsupported_source','note':'合法な治癒源が接続された場合の入力テスト。現行5職への実装ではない。'},
  {'id':'cross','label':'仮の越境接続','flags':['CROSS_WEAPON_SKILL'],'kind':'unimplemented_connection','note':'対応武器・技能・資源の接続が合法に成立した仮条件。本体の越境処理は未実装。'},
 ]
 # Optional specialists must not be mandatory bridges. For each actual legal
 # input profile, join the usable boundary of an inactive component with a
 # minimum-distance tree. Existing specialist investments remain optional.
 bypasses=[]
 legal=[p for p in d['profiles'] if p['weaponUsable']]
 masks={}
 for p in legal:
  for loss in ((),('key_04',),('key_02',),('key_04','key_02')):
   active={n['id'] for n in d['nodes'] if n['type'] not in ('start','keystone') and node_input(d,n,p,loss,by=by)['present']}
   masks.setdefault((p['start'],tuple(sorted(active))),(p,loss,active))
 for p,loss,active in masks.values():
  adj=collections.defaultdict(list)
  for e in d['edges']:
   if by[e['a']]['type']!='keystone' and by[e['b']]['type']!='keystone':adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
  inactive={i for i in by if by[i]['type'] not in ('start','keystone') and i not in active};seen=set()
  for begin in sorted(inactive):
   if begin in seen:continue
   comp={begin};stack=[begin];seen.add(begin)
   while stack:
    for nxt in adj[stack.pop()]:
     if nxt in inactive and nxt not in seen:seen.add(nxt);comp.add(nxt);stack.append(nxt)
   boundary=sorted({j for i in comp for j in adj[i] if j in active})
   if len(boundary)<2:continue
   joined={boundary[0]}
   while len(joined)<len(boundary):
    options=[(math.hypot(by[a]['x']-by[b]['x'],by[a]['y']-by[b]['y']),a,b) for a in joined for b in boundary if b not in joined]
    distance,a,b=min(options);joined.add(b)
    if add(a,b,'optional-specialist-bypass',avoids=sorted(comp)):
     bypasses.append({'a':a,'b':b,'avoids':sorted(comp),'distance':round(distance,1)})
 d['reviewCorrections']={'warriorNodesReused':31,'newNodes':0,'optionalBypasses':bypasses,
  'inactiveNotRequired':True,'balanceVerified':False}
 interval=limit_chains(d,add)
 source_intervals=[]
 for p,loss,active in masks.values():
  active=active|{next(o['root'] for o in d['origins'] if o['id']==p['start'])}
  source_intervals.extend(limit_chains(d,add,active,p['id']+':'+','.join(loss)))
 # Source-specific corrections only add genuine reconnections, so they cannot
 # lengthen the all-graph chain intervals.
 d['chainReview']={'before':before,'after':chain_report(d),'crossings':interval+source_intervals,
  'proposedMaxDecisionSteps':3,'productionAdopted':False,'scope':'全体および実際に使える標準武器の入力条件。Keyなし、会心喪失、自然回復喪失も検証。'}
 d['version']='projects-large-tree-diff-v2'
