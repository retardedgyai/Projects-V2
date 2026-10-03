"""Targeted review fixes from preserved V7; no native gameplay write."""
import ast,collections,copy,heapq,json,math
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview'
OLD=BASE/'proposals/workshop-middle-choice-v7'
OUT=BASE/'proposals/workshop-contract-v8';OUT.mkdir(parents=True,exist_ok=True)
baseline=json.loads((OLD/'candidate-graph.json').read_text(encoding='utf8'))
source=json.loads((BASE/'graph.json').read_text(encoding='utf8'))
central=json.loads((OLD/'central-authoring.json').read_text(encoding='utf8'))
g=copy.deepcopy(baseline);by={n['id']:n for n in g['nodes']};fixed=set(central['centralOpeningIds'])
# Reuse only inspected pure geometry definitions; never execute the V7 writer.
geometry_source=ROOT/'scripts/build-skill-tree-middle-choice-v7.py'
names={'xy','rounded','face_inventory','inside','Geometry','route_in_face'}
definitions=[n for n in ast.parse(geometry_source.read_text(encoding='utf8')).body if isinstance(n,(ast.FunctionDef,ast.ClassDef)) and n.name in names]
assert len(definitions)==len(names)
exec(compile(ast.Module(body=definitions,type_ignores=[]),str(geometry_source),'exec'))
geom=Geometry();geom.refresh()
ledger=dict(status='AUTHORED_PENDING_VERIFICATION',baselineCommit='5cc611a06c8f06e3146942b104817992d6f6d063',nodeChanges=[],edgeChanges=[],contracts=[],sourceGraphUntouched=True,runtimeApplied=False)
def change(id,reason,**values):
 n=by[id];before={k:copy.deepcopy(n.get(k)) for k in values};n.update(values)
 ledger['nodeChanges'].append(dict(id=id,reason=reason,before=before,after=copy.deepcopy(values)))
def remove(a,b,reason):
 e=next(e for e in g['edges'] if {a,b}=={e['a'],e['b']});g['edges'].remove(e)
 ledger['edgeChanges'].append(dict(action='remove',a=a,b=b,reason=reason));geom.refresh()
def connect(a,b,reason):
 assert not any({a,b}=={e['a'],e['b']} for e in g['edges'])
 points=None
 if geom.clear(xy(a),xy(b),ignore=(a,b),clearance=92):points=[xy(a),xy(b)]
 else:
  global faces
  faces=face_inventory(g)
  for i,f in enumerate(faces):
   if a in f['ids'] and (b in f['ids'] or inside(xy(b),f)):
    try:points=route_in_face(a,b,i);break
    except ValueError:pass
 if points is None:raise ValueError(('No clear reviewed connection',a,b))
 g['edges'].append(dict(a=a,b=b,road=by[a]['type']=='road' or by[b]['type']=='road',proposalKind='contract-v8',role=reason,points=[rounded(p) for p in points],crossingGaps=[]))
 ledger['edgeChanges'].append(dict(action='add',a=a,b=b,reason=reason));geom.refresh()

# The mana ring stays intact. Blood gets an independent general Road entrance.
remove('g20n6','key_02','Blood must not require a pure MP investment that its own rule disables.')
change('key_02','Separate the blood choice from the mana specialization.',anchor='r36_48_1',group=None,groupAnchor=None,theme='life',description='MPを消費する手持ちの技能を、同量のHP消費へ置き換える候補。一般経路から選び、MP輪への投資は必須にしない。',tradeoff='MP容量・MP効率・HP自然再生の育成値を無効にする。以前に買った点の費用と接続は保持する。実戦の支払い・生存条件は未統合。')

change('key_02','The former mana pocket has no suppression-safe entrance; move only this optional terminal beside a reachable general Road.',x=2989.72925,y=-4643.91041,anchor='r19_24_1')
geom.refresh()
connect('r19_24_1','key_02','Independent optional blood entry from a suppression-safe general Road.')

# Keep eight points and their layout. MP is an optional arm, not a shield gate.
change('v7g12n0','Neutral paid entrance, usable without MP or AP.',name='備える体',stats={'hp':2},benefitInputs={'hp':[]},description='最大HPと装甲の共通の備えから、手持ちの魔法・MP運用・障壁を別々に育てる。')
change('v7g12n1','Neutral paid entrance, usable without MP or AP.',name='備える装甲',stats={'armor':2},benefitInputs={'armor':[]},description='MP容量への投資を前提にせず、使える力の枝を選ぶ。')
for id,name,eff in [('v7g12n4','MP運用の研鑽',2),('v7g12n5','MP運用を育てる',5)]:
 change(id,'Keep the prior MP capacity on the optional MP-efficiency arm.',name=name,stats={'mana':3,'efficiency':eff},benefitInputs={'mana':['MP_CONSUMER'],'efficiency':['MP_CONSUMER']},description='MPを使う編成だけが選ぶ、容量と消費効率への任意投資。')
for id in ('v7g12n2','v7g12n3','v7g12n6','v7g12n7'):
 change(id,'Explain independent source-owned specialization after the neutral entrance.',description='共通の備えの先で、既に持つ魔法または障壁を育てる。MPへの投資は前提にしない。')
next(q for q in g['groups'] if q['id']=='v7g12')['role']='中立の有料入口から、所有する魔法・MP運用・所有する障壁を別々に選ぶ。能力源は与えない。'

# The old external Road is also inside an MP-only outer pocket. Replace that
# entrance with two paid Road points on a clear 1413-world general approach.
remove('r10_36_1','v7g12n0','Neutral local stats alone do not remove the outer MP gate.')
anchor='link_assassin_0_3';target='v7g12n0';start=xy(anchor);end=xy(target);previous=anchor
road_template=copy.deepcopy(next(n for n in g['nodes'] if n['id']=='v7road_r3_3'))
for k in (1,2):
 id=f'v8road_shield_{k}';point=start+(end-start)*k/3
 n=copy.deepcopy(road_template);n.update(id=id,x=round(float(point[0]),5),y=round(float(point[1]),5),name='障壁への共通路',group=None,stats={},description='HP・資源の一般経路から所有する障壁へ進む。有効な通路の恩恵を選ぶ。',sourceState='contract_v8_proposal',sourceRef=f'contract-v8:{id}')
 g['nodes'].append(n);by[id]=n;geom.refresh();connect(previous,id,'Paid general shield approach without an MP-only gate.');previous=id
connect(previous,target,'Paid general shield approach without an MP-only gate.')
ledger['addedRoads']=['v8road_shield_1','v8road_shield_2']

# The mixed goal retains physical value under noCrit; the crit-only arm is optional.
remove('v7g04n3','v7g04n5','Penetration must not require a crit-only point under noCrit.')
connect('v7g04n4','v7g04n5','Optional penetration from the common physical/crit goal.')
change('v7g04n5','Physical/common approach remains useful under noCrit.',description='物理と会心の共通目標から貫通へ進む。無会心でも物理側から育てられる。')

# Existing fixture geometry is the source. Do not treat DAMAGE alone as area.
overlay={'statInputs':{'aoe':['DAMAGE','AREA_ATTACK']},'requirements':{'AREA_ATTACK':'手持ちの範囲攻撃技能（AREAタグ・正の半径・攻撃係数）'},'profileAreaSources':{}}
for p in source['profiles']:
 if not p['weaponUsable']:continue
 eligible=[s for s in p['skills'] if 'AREA' in s.get('tags',[]) and s.get('radius',0)>0 and (s.get('ad',0)>0 or s.get('ap',0)>0) and s['motion'] not in ('SHIELD','HEAL','GUARD','EVADE')]
 overlay['profileAreaSources'][p['id']]=[s['name'] for s in eligible]
for n in g['nodes']:
 if n['id'].startswith('v7g10') and 'aoe' in n['stats']:
  change(n['id'],'Area applies only to owned attacking AREA geometry.',benefitInputs={k:overlay['statInputs'].get(k,source['statInputs'].get(k,[])) for k in n['stats']},description='範囲攻撃の半径を育てる候補。手持ちのAREA攻撃技能にだけ範囲値を適用し、単体射線・障壁・回復に新しい範囲を与えない。混合した物理値は別に有効。')
g['statInputs']=copy.deepcopy(source['statInputs']);g['statInputs'].update(overlay['statInputs'])
contracts={
 'key_09':dict(classification='keystone_candidate',trigger='手持ちの主力技能を本人が発動する。',effect='同じ発動に追加pulseを一つ予定する候補。新技能や別の再帰発動を作らない。',cost='1pulseの威力を下げる候補。共通版の倍率は未採用。',stack='残響・追矢・既存追加発動と同じpulse予算へ一度だけ合算する案。上限で追加分が消えた場合の代償適用は要採用判断。',cap='既存の最終pulse上限8を超えない。追加pulseからこのKeyを再発動しない。',sourceRefs=['CoreSkillCatalog.kt:85','CoreSkillCatalog.kt:94','CoreSkillCatalog.kt:110','CoreSkillCatalog.kt:127'],pending=['共通威力倍率','上限飽和時の代償とMOD合算の採用'],proposal=dict(extraPulse=1,sharedPulseCap=8,recursive=False,powerMultiplier=None)),
 'key_11':dict(classification='notable_candidate',trigger='既に持つ仲間向け障壁が、本人以外へ届く。自己障壁だけでは起動しない。',effect='その既存障壁の付与量を強める数値交換案。障壁技能そのものは与えない。',cost='本人の直接攻撃を弱める数値交換案。量と倍率は未採用。3ptも比較用の仮費用。',stack='既存の発生量・術者補正・受け手処理に一回だけ適用する案。受け手で強い障壁だけを保持し加算しない。',cap='既存の最大HP75%と期間1〜200tickの受け取り上限を維持する。',sourceRefs=['CoreSkillCatalog.kt:28','CorePlayerCombat.kt:413','CorePlayerCombat.kt:418'],pending=['共通倍率・適用順序','Notable分類と仮費用の採用'],proposal=dict(grantsAbility=False,allyOnly=True,shieldMultiplier=None,ownDamageMultiplier=None,classificationCandidate='notable')),
 'key_13':dict(classification='keystone_candidate',trigger='本人の既存の氷源が、直撃により対象へ減速を適用する。',effect='減速が有効な間、その対象への次の手持ち主力の命中を一度の攻撃機会として記録・消費する候補。追加発動や凍結は与えない。',cost='機会を作った直撃の即時威力を下げる候補。機会の報酬倍率と代償倍率は未採用。',stack='一つの対象に一つの機会。期間は実際の減速終了へ結び付け、足し算で延長しない。同じ主力発動の複数pulseで機会を重ねない。',cap='本人・対象・主力発動ごとに報酬は一度。既存の減速強度・ボス補正を維持する。凍結・ボス停止の契約はない。',sourceRefs=['CorePlayerCombat.kt:493','CorePlayerCombat.kt:533','QuestEncounterCombat.kt:230'],pending=['主力命中の報酬倍率','直撃の代償倍率','機会の記録・消費処理の採用'],proposal=dict(window='actual applied slow expiry',oneChargePerTarget=True,onePayoffPerSignatureActivation=True,grantsFreeze=False,payoffMultiplier=None,initialHitMultiplier=None)),
 'key_14':dict(classification='keystone_candidate',trigger='所有する燃焼源MODを伴う本人の直撃が、生きている敵へ既存燃焼を作る。炎技能名・elementだけでは起動しない。',effect='既存の炎の直撃加算分を、その既存燃焼の3tickへ配分する変換候補。燃焼源と新しい期間を与えない。',cost='配分した炎の直撃加算は直撃から失う。元の技能のAD/AP部分と他元素は変換しない。',stack='術者・対象ごとに一つの燃焼を保持。強いtick量を採り、弱い再命中で加算しない。再適用は3tickへ更新し、残期間を足さない。',cap='既存の20tick間隔・3tickを維持。燃焼tickから再燃焼・連鎖・会心・吸収・命中資源を起動しない。',sourceRefs=['CorePlayerCombat.kt:237','CorePlayerCombat.kt:529','class-combat-formula-recovery.md'],pending=['直撃の炎加算分を取り出す計算層の照合','変換の採用と実戦計算'],proposal=dict(reuseExistingDirectFireBudget=True,newDamageMultiplier=None,tickCount=3,tickInterval=20,additiveDuration=False,recursive=False,grantsBurn=False)),
 'key_15':dict(classification='keystone_candidate',trigger='所有する連鎖源MODを伴う本人の直撃。雷技能名・elementだけでは起動しない。',effect='既存の一回の連鎖ダメージ予算を、隣の敵ではなく元の対象へ二次効果として向ける候補。対象が一体でも同じ一回分だけ使う。',cost='その直撃から別対象へ飛ぶ既存連鎖を失う。通常の直接雷加算をもう一度作らない。',stack='既存連鎖を置き換え、既存MODの連鎖と両方を発生させない。変換hitはeffect扱いで根の直撃へ戻さない。',cap='元の直撃一回につき既存の二次hit一回まで。新しい対象数・連鎖段数を与えない。二次hitから会心・吸収・命中資源・再連鎖を起動しない。',sourceRefs=['CorePlayerCombat.kt:537','QuestEncounterCombat.kt:257'],pending=['単体への変換の採用','二次効果の計算層・同予算評価'],proposal=dict(reuseExistingChainFormula=True,existingChainFlatFactor=.8,secondaryHitsPerRoot=1,replacesExistingChain=True,effectDamage=True,recursive=False,grantsChain=False)),
}
for id,c in contracts.items():
 c.update(status='UNIMPLEMENTED_PROPOSAL_PENDING_ADOPTION',approved=False,runtimeApplied=False)
 ledger['contracts'].append(dict(id=id,**c))
 values=dict(effectContract=copy.deepcopy(c),description=c['effect'],tradeoff=c['cost'],risks=['未実装・未採用の候補。'+v for v in c['pending']])
 if id=='key_11':values.update(classificationCandidate='notable',classificationReason='現在の案は既存の障壁量と本人火力の数値交換で、組み方・運用の転換をまだ定義していない。')
 if id=='key_13':values.update(requirements=['ICE','SIGNATURE'])
 change(id,'Specify source-owned effect contract and unresolved adoption parameters.',**values)
g.update(layoutStage='contract-v8',scope='承認済み中央を保持し、自己無効化する必須経路・障壁のMP関所・予算比較・効果契約を修正する候補。',runtimeApplied=False)
for n in baseline['nodes']:
 if n['id'] in fixed:assert n==by[n['id']]
assert [e for e in baseline['edges'] if e['a'] in fixed or e['b'] in fixed]==[e for e in g['edges'] if e['a'] in fixed or e['b'] in fixed]
assert len(g['nodes'])==len(baseline['nodes'])+2
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'input-overlay.json').write_text(json.dumps(overlay,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'contract-authoring.json').write_text(json.dumps(ledger,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'central-authoring.json').write_text(json.dumps(central,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'nodes':len(g['nodes']),'edges':len(g['edges']),'changedNodes':len({r['id'] for r in ledger['nodeChanges']}),'areaProfiles':sum(bool(v) for v in overlay['profileAreaSources'].values())}))
