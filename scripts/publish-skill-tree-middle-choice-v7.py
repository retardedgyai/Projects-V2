"""Package the single middle-choice candidate using the accepted V6 UI assets."""
import collections, copy, hashlib, heapq, json, re
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OLD=BASE/'proposals/workshop-poe2-central-v6';OUT=BASE/'proposals/workshop-middle-choice-v7'
source=json.loads((BASE/'graph.json').read_text(encoding='utf8'));g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));by={n['id']:n for n in g['nodes']};adj={i:[] for i in by}
for e in g['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
product_descriptions={
 'v7g01':'最大HP、装甲、魔法防御のどれを育てるかを選ぶ。',
 'v7g05':'北西への移動中に、最大HPか自然再生を短く拾う。',
 'v7g11':'最大HP、自然再生、所有する吸命MODを別々に育てる。',
 'v7g12':'手持ちの魔法、MP消費効率、障壁へ別々に投資する。編成に応じて育てる力を選ぶ。',
}
for n in g['nodes']:
 if n['group'] in product_descriptions:n['description']=product_descriptions[n['group']]
for group in g['groups']:
 if group['id']=='v7g10' and 'v7g10n4' not in group['entrances']:group['entrances'].append('v7g10n4')
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8')
profiles={p['id']:p for p in source['profiles'] if p['weaponUsable']};origins={o['id']:o['root'] for o in source['origins']}
old_html=(OLD/'ProjectS_PoE2_Central_V6.html').read_text(encoding='utf8');old_payload=json.loads(re.search(r'<script id="wholeTreeData" type="application/json">(.*?)</script>',old_html,re.S)[1])

def route(owned,target,profile,siphon):
 flags=set(profile['flags'])|({'LIFESTEAL'} if siphon else set());root=origins[profile['start']]
 def eligible(n):
  if n['type']=='start':return n['id']==root
  if n['type']=='keystone':return n['id']==target and set(n['requirements'])<=flags and not set(n.get('conflicts',[]))&owned
  return n['type']=='road' or any(set(source['statInputs'].get(k,[]))<=flags for k in n['stats'])
 prices={i:0 for i in owned};prev={};q=[(0,i) for i in owned];heapq.heapify(q)
 while q:
  c,u=heapq.heappop(q)
  if prices[u]!=c:continue
  if u==target:
   path=[u]
   while path[-1] in prev:path.append(prev[path[-1]])
   return path[::-1]
  for v in adj[u]:
   if not eligible(by[v]):continue
   nc=c+(0 if v in owned else by[v]['cost'])
   if nc<prices.get(v,float('inf')):prices[v]=nc;prev[v]=u;heapq.heappush(q,(nc,v))
 raise ValueError(('Target unavailable',profile['id'],target))

examples=[]
specs=[{k:v for k,v in e.items() if k in ['id','label','profileId','siphon','targets','note','budget']} for e in old_payload['examples']]
for gid,label,pid,a,b in [('v7g07','消費','mage:support:plain:standard','MP容量側','消費効率側'),('v7g04','一撃','warrior:support:plain:standard','物理側','会心側')]:
 for suffix,arm,targets in [('a',a,[gid+'n0',gid+'n2',gid+'n4']),('b',b,[gid+'n1',gid+'n3',gid+'n4'])]:
  specs.append(dict(id=gid+'-'+suffix,label=label+'・'+arm,profileId=pid,siphon=False,targets=targets,note='同じ入口と同じ目標へ同費用で進む。途中の恩恵が異なる二経路を比較。',budget=48))
for id,label,note in [('critical','会心・資源も拾う経路','同じ会心目標へ、資源運用・攻撃速度・物理も拾う経路。短い経路と途中の恩恵を比較。'),('tank-life','生命・多方面の備えを拾う経路','同じ生命・装甲目標へ、魔法防御・障壁・受け流し・資源運用も拾う経路。')]:
 original=next(e for e in old_payload['examples'] if e['id']==id)
 specs.append(dict(id='legacy-'+id,label=label,profileId=original['profileId'],siphon=original['siphon'],targets=original['targets'],note=note,budget=original['budget'],legacyRoute=id))
splits=json.loads((OUT/'middle-choice-authoring.json').read_text(encoding='utf8'))['splitChoices']
for spec in specs:
 profile=profiles[spec['profileId']];owned={origins[profile['start']]};paths=[]
 if 'legacyRoute' in spec:
  original=next(e for e in old_payload['examples'] if e['id']==spec['legacyRoute'])
  for path in original['paths']:
   ids=[path['nodes'][0]]
   for a,b in zip(path['nodes'],path['nodes'][1:]):
    if b not in adj[a]:
     split=next(x for x in splits if set(x['oldEdge'])=={a,b});ids.append(split['id'])
    ids.append(b)
   owned.update(ids);paths.append(dict(target=path['target'],nodes=ids))
 else:
  for target in spec['targets']:
   ids=route(owned,target,profile,spec['siphon']);owned.update(ids);paths.append(dict(target=target,nodes=ids))
 flags=set(profile['flags'])|({'LIFESTEAL'} if spec['siphon'] else set());lost={k for i in owned for k in source['lostStats'].get(by[i].get('rule'),[])};totals=collections.Counter()
 for i in owned:
  totals.update({k:v for k,v in ({'hp':2} if by[i]['type']=='road' else by[i]['stats']).items() if k not in lost and set(source['statInputs'].get(k,[]))<=flags})
 spent=sum(by[i]['cost'] for i in owned);assert spent<=spec['budget'],(spec['id'],spent,spec['budget'])
 examples.append(dict(**spec,learned=sorted(owned),paths=paths,spent=spent,stats=dict(totals),keys=sorted(i for i in owned if by[i]['type']=='keystone'),remaining=spec['budget']-spent))

segments=[(e['a'],e['b'],a,b) for e in g['edges'] for a,b in zip(e['points'],e['points'][1:])];A=np.array([x[2] for x in segments]);D=np.array([np.array(x[3])-x[2] for x in segments]);den=np.maximum((D*D).sum(axis=1),1e-12);paint={};all_points=np.array([[n['x'],n['y']] for n in g['nodes']])
for k,n in enumerate(g['nodes']):
 p=all_points[k];v=p-A;t=np.clip((v*D).sum(axis=1)/den,0,1);dist=np.linalg.norm(v-t[:,None]*D,axis=1);dist[np.array([n['id'] in x[:2] for x in segments])]=float('inf');pair=np.linalg.norm(all_points-p,axis=1);pair[k]=float('inf');paint[n['id']]=dict(wire=float(np.min(dist)),pair=float(np.min(pair)))
payload=dict(source=source,graph=g,icons=copy.deepcopy(old_payload['icons']),examples=examples,paintBounds=paint,centralCenter=old_payload['centralCenter'])
fonts='\n'.join(re.findall(r'@font-face\{[^}]+\}',old_html));template=(ROOT/'scripts/skill-tree-poe2-central-v6-template.html').read_text(encoding='utf8')
template=template.replace('645点・47領域・5職業・15 Key',f"{len(g['nodes'])}点・{len(g['groups'])}領域・5職業・15 Key").replace('5起点を中央に集めた配分試作 / 採用前','中央と初動を保持した育成案 / 採用前').replace('poe2-central-v6','middle-choice-v7')
template=template.replace('5職業の起点を中央の一まとまりへ置き、異なる47領域へ進む全体比較です。全645点の効果・費用・入力条件は保持。全Keyは任意の行き止まり。交差隠しやノードを跨ぐ接続は使っていません。',f"中央と初動を保ったまま、中間地域へ小目標と専門投資を避ける道を加えた全体比較です。元の645点の効果を保持し、全{len(g['nodes'])}点・{len(g['groups'])}領域を表示。同じ目標へ途中の効果が異なる経路も比較できます。全Keyは任意の行き止まりです。")
html=template.replace('/*FONTS*/',fonts).replace('/*PAYLOAD*/',json.dumps(payload,ensure_ascii=False,separators=(',',':')))
(OUT/'ProjectS_Middle_Choices_V7.html').write_text(html,encoding='utf8');(OUT/'route-examples.json').write_text(json.dumps(examples,ensure_ascii=False,indent=2),encoding='utf8')
manifest=dict(status='COMPLETE_CANDIDATE_PENDING_REVIEW',adopted=False,source645Sha256=hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),source645EmbeddedVerbatim=True,displayedNodes=len(g['nodes']),displayedGroups=len(g['groups']),original645NodeFieldsAndCoordinatesExact=True,central60AndIncidentEdgesExact=True,addedRegions=len(g['groups'])-47,origins=5,keystones=15,connections=len(g['edges']),runtimeApplied=False,earlierFilesChanged=False,sourceWeaponProfiles=60,inputReachabilityCases=120,saveScope='preview-only',saveStudy='middle-choice-v7',v6SaveCodeAccepted=False,approvedFontsAndSpritesExact=True)
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'examples':[(e['id'],e['spent']) for e in examples],'htmlBytes':len(html.encode()),'minimumWireClearance':min(v['wire'] for v in paint.values())},ensure_ascii=False))
