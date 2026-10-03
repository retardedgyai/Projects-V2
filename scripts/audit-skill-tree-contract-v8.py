"""Independent graph, input, allocation and targeted regression checks."""
import collections,copy,heapq,json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-contract-v8';OLD=BASE/'proposals/workshop-middle-choice-v7'
def read(p):return json.loads(p.read_text(encoding='utf8'))
s=read(BASE/'graph.json');g=read(OUT/'candidate-graph.json');old=read(OLD/'candidate-graph.json');overlay=read(OUT/'input-overlay.json');examples=read(OUT/'route-examples.json');ledger=read(OUT/'contract-authoring.json')
by={n['id']:n for n in g['nodes']};ob={n['id']:n for n in old['nodes']};ad={i:[] for i in by}
for e in g['edges']:ad[e['a']].append(e['b']);ad[e['b']].append(e['a'])
inputs=copy.deepcopy(s['statInputs']);inputs.update(overlay['statInputs']);profiles={p['id']:p for p in s['profiles'] if p['weaponUsable']};roots={o['id']:o['root'] for o in s['origins']}
def flags(p,siphon=False):return set(p['flags'])|({'AREA_ATTACK'} if overlay['profileAreaSources'][p['id']] else set())|({'LIFESTEAL'} if siphon else set())
def raw(i):return {'hp':2} if by[i]['type']=='road' else by[i]['stats']
def usable(i,f,root,loss=()):
 n=by[i]
 if n['type']=='start':return i==root
 if n['type']=='keystone':return set(n['requirements'])<=f
 return any(k not in loss and set(inputs.get(k,[]))<=f for k in raw(i))
def reachable(root,f,loss=(),seed=None):
 seen=set(seed or [root]);q=list(seen)
 for u in q:
  for v in ad[u]:
   if v not in seen and by[v]['type']!='keystone' and usable(v,f,root,loss):seen.add(v);q.append(v)
 return seen
def values(ids,f,loss=()):
 result=collections.Counter()
 for i in ids:result.update({k:v for k,v in raw(i).items() if k not in loss and set(inputs.get(k,[]))<=f})
 return dict(result)
def difference(a,b):return {k:a.get(k,0)-b.get(k,0) for k in sorted(set(a)|set(b)) if a.get(k,0)!=b.get(k,0)}
changed={r['id'] for r in ledger['nodeChanges']};assert len(changed)==18
assert all(n==by[n['id']] for n in old['nodes'] if n['id'] not in changed)
fixed=set(read(OUT/'central-authoring.json')['centralOpeningIds']);assert all(by[i]==ob[i] for i in fixed)
assert [e for e in old['edges'] if e['a'] in fixed or e['b'] in fixed]==[e for e in g['edges'] if e['a'] in fixed or e['b'] in fixed]
assert all(by[i]['x']==ob[i]['x'] and by[i]['y']==ob[i]['y'] for i in ob if i!='key_02')
for i,n in by.items():
 if n['type']=='keystone':assert len(ad[i])==1
assert {tuple(sorted((e['a'],e['b']))) for e in old['edges'] if e['a'].startswith('g20') and e['b'].startswith('g20')}=={tuple(sorted((e['a'],e['b']))) for e in g['edges'] if e['a'].startswith('g20') and e['b'].startswith('g20')}
assert len(by)==789 and len(g['edges'])==947
cases=[]
for p in profiles.values():
 for siphon in (False,True):
  f=flags(p,siphon);root=roots[p['start']];seen=reachable(root,f);eligible={i for i in by if by[i]['type']!='keystone' and usable(i,f,root)}
  assert eligible<=seen,(p['id'],sorted(eligible-seen))
  for i,n in by.items():
   if n['type']=='keystone' and usable(i,f,root):assert ad[i][0] in seen
  cases.append(dict(profile=p['id'],siphon=siphon,eligible=len(eligible),reachable=len(seen)))
for e in examples:
 p=profiles[e['profileId']];f=flags(p,e['siphon']);root=roots[p['start']];ids=set(e['learned']);loss={k for i in ids for k in s['lostStats'].get(by[i].get('rule'),[])}
 assert sum(by[i]['cost'] for i in ids)==e['spent']<=e['budget']
 assert all(usable(i,f,root) for i in ids)
 seen={root};q=[root]
 for u in q:
  for v in ad[u]:
   if v in ids and v not in seen:seen.add(v);q.append(v)
 assert seen==ids and all(i in ids for i in e['targets'])
 actual=values(ids,f,loss)
 assert all(abs(actual.get(k,0)-e['stats'].get(k,0))<1e-8 for k in set(actual)|set(e['stats']))
 for i in ids:assert not set(by[i].get('conflicts',[]))&ids
fresh=next(e for e in examples if e['id']=='mage-blood');blood_shield=next(e for e in examples if e['id']=='mage-blood-shield')
f=flags(profiles[fresh['profileId']]);blood_loss=set(s['lostStats']['bloodCost'])
for e in (fresh,blood_shield):assert all(by[i]['type'] in ('start','keystone') or values([i],f,blood_loss) for i in e['learned'])
previous=read(OLD/'route-examples.json');old_blood=next(e for e in previous if e['id']=='mage-blood')
old_dead=[i for i in old_blood['learned'] if ob[i]['type'] not in ('start','keystone') and not any(k not in blood_loss for k in ({'hp':2} if ob[i]['type']=='road' else ob[i]['stats']))]
assert len(old_dead)==11
mp=next(e for e in examples if e['id']=='mage-mp');owned=next(e for e in examples if e['id']=='mage-owned-mp-blood');assert set(mp['learned'])<=set(owned['learned']) and owned['spent']==20 and owned['stats'].get('mana',0)==0
shield_cases=[]
entry_ids=['v7g12n0','v7g12n1','v7g12n6','v7g12n7']
for name,ff,loss,synthetic in [('MP+SHIELD',f,set(),False),('SHIELD without MP',f-{'MP_CONSUMER'},set(),True),('bloodCost+SHIELD',f,blood_loss,False)]:
 seen=reachable(roots['mage'],ff,loss)
 assert set(entry_ids)<=seen,(name,set(entry_ids)-seen)
 assert all(usable(i,ff,roots['mage'],loss) for i in entry_ids)
 assert values(entry_ids,ff,loss)=={'hp':2,'armor':2,'barrier':14}
 shield_cases.append(dict(name=name,syntheticSensitivityCase=synthetic,reachable=True,localCost=4,localStats=values(entry_ids,ff,loss),newPaidApproachPoints=2))
# The local noCrit penetration approach remains a paid physical investment.
no_crit=set(s['lostStats']['noCrit']);physical=['v7g04n0','v7g04n2','v7g04n4','v7g04n5'];ff=flags(profiles['warrior:support:plain:standard'])
assert all(usable(i,ff,roots['warrior'],no_crit) for i in physical)
assert all(b in ad[a] for a,b in zip(physical,physical[1:]))
assert not usable('v7g04n3',ff,roots['warrior'],no_crit)
assert sum(by[i]['cost'] for i in physical)==4
# Pure AoE cannot be bought without owned attack geometry; a mixed physical
# node retains only its supported value. Utility AREA tags do not grant input.
ff=flags(profiles['assassin:mark:plain:standard']);without=ff-{'AREA_ATTACK'}
assert usable('v7g10n0',ff,roots['assassin']) and not usable('v7g10n0',without,roots['assassin'])
assert values(['v7g10n4'],without)=={'physical':5}
area_provenance=[]
for p in profiles.values():
 eligible=[x['name'] for x in p['skills'] if 'AREA' in x.get('tags',[]) and x.get('radius',0)>0 and (x.get('ad',0)>0 or x.get('ap',0)>0) and x['motion'] not in ('SHIELD','HEAL','GUARD','EVADE')]
 assert eligible==overlay['profileAreaSources'][p['id']]
 area_provenance.append(dict(profile=p['id'],actualOwnedAreaAttacks=eligible))
for id in ['key_09','key_11','key_13','key_14','key_15']:
 c=by[id]['effectContract'];assert c['approved'] is False and c['runtimeApplied'] is False and c['pending']
 assert all(c[k] for k in ('trigger','effect','cost','stack','cap','sourceRefs'))
assert by['key_11']['classificationCandidate']=='notable'
assert by['key_09']['effectContract']['proposal']['sharedPulseCap']==8
assert by['key_13']['requirements']==['ICE','SIGNATURE']
assert by['key_14']['effectContract']['proposal']['recursive'] is False
assert by['key_15']['effectContract']['proposal']['replacesExistingChain'] is True
comparisons=[]
for budget in (48,64,80):
 a,b=[next(e for e in examples if e['id']==f'assassin-{arm}-{budget}') for arm in ('area','crit')]
 assert a['profileId']==b['profileId'] and a['budget']==b['budget']==budget and a['stats']!=b['stats']
 comparisons.append(dict(budget=budget,budgetAdopted=False,left={k:a[k] for k in ('id','spent','remaining','targets','stats')},right={k:b[k] for k in ('id','spent','remaining','targets','stats')},leftMinusRight=difference(a['stats'],b['stats'])))
life_new=next(e for e in examples if e['id']=='tank-life-reinvest-43');life_old=next(e for e in examples if e['id']=='legacy-tank-life');assert life_new['spent']==life_old['spent']==43
assert life_old['stats']['resource']==22 and life_old['stats']['efficiency']==8 and not life_new['stats'].get('resource') and not life_new['stats'].get('efficiency')
equal_a,equal_b=[next(e for e in examples if e['id']==id) for id in ['assassin-equal-area-43','assassin-equal-crit-43']]
assert equal_a['profileId']==equal_b['profileId'] and equal_a['spent']==equal_b['spent']==43 and equal_a['budget']==equal_b['budget']==48
equal_pair=dict(usedPoints=43,budget=48,sameProfileAndModAndRoadChoice=True,left={k:equal_a[k] for k in ('id','spent','remaining','targets','stats')},right={k:equal_b[k] for k in ('id','spent','remaining','targets','stats')},leftMinusRight=difference(equal_a['stats'],equal_b['stats']))
payload=json.loads(re.search(r'<script id="wholeTreeData" type="application/json">(.*?)</script>',(OUT/'ProjectS_Contract_V8.html').read_text(encoding='utf8'),re.S)[1]);assert payload['source']==s and payload['graph']==g
report=dict(status='PASS',source645EmbeddedVerbatim=True,central60AndIncidentEdgesExact=True,changedNodeIds=sorted(changed),onlyMovedPriorNode='key_02',addedPaidRoads=ledger['addedRoads'],inputCases=cases,examplesChecked=len(examples),bloodFresh=dict(beforeCost=20,beforeDeadNodes=old_dead,afterCost=fresh['spent'],afterDeadNodes=[],effectiveStats=fresh['stats']),voluntaryMpPreserved=dict(beforeCost=mp['spent'],afterCost=owned['spent'],autoRefund=False,retainedIds=mp['learned']),shieldCases=shield_cases,noCritPenetration=dict(localCost=4,stats=values(physical,ff,no_crit),critOnlyArmOptional=True),areaInput=dict(requirements=inputs['aoe'],sourceProfiles=area_provenance,noAreaMixedStats={'physical':5}),sameBudgetComparisons=comparisons,life43=dict(beforeStats=life_old['stats'],afterStats=life_new['stats'],delta=difference(life_new['stats'],life_old['stats']),runtimeApplied=False),effectContractsUnimplemented=True)
report['upperBudgetExamples']=report.pop('sameBudgetComparisons');report['upperBudgetExamplesMeaning']='Unused budget is explicit. The 80-budget examples are also feasible at 64 and do not demonstrate an 80-only unlock.';report['sameConsumptionComparisons']=[equal_pair]
(OUT/'allocation-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'status':'PASS','inputCases':len(cases),'examples':len(examples),'bloodCost':fresh['spent'],'bloodDead':0,'shieldCases':3,'sameBudgetPairs':3,'life43Delta':report['life43']['delta']},ensure_ascii=False))
# A fixture-only follow-up can reuse the unchanged graph's existing exhaustive
# actual-polyline result, instead of rendering or checking geometry again.
if '--allocation-only' not in sys.argv:
 audit=ROOT/'scripts/audit-skill-tree-poe2-central-v6.py';code=audit.read_text(encoding='utf8').replace('workshop-poe2-central-v6','workshop-contract-v8').replace('nodes=645,placedNodes=645-len(unused)',"nodes=len(g['nodes']),placedNodes=len(g['nodes'])-len(unused)")
 exec(compile(code,str(audit),'exec'),{'__file__':str(audit)})
assert read(OUT/'geometry-verification.json')['status']=='PASS'
