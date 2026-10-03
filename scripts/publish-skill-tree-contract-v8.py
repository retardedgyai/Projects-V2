"""Publish V8 using the accepted fonts, sprites and central canvas UI."""
import copy,hashlib,json,re
import numpy as np
from skill_tree_contract_v8 import *

prior=read(OLD/'route-examples.json')
old_html=(OLD/'ProjectS_Middle_Choices_V7.html').read_text(encoding='utf8')
old_payload=json.loads(re.search(r'<script id="wholeTreeData" type="application/json">(.*?)</script>',old_html,re.S)[1])
specs=[]
for e in prior:
    spec={k:e[k] for k in ('id','label','profileId','siphon','targets','note','budget')}
    if e['id'].startswith('legacy-'):
        spec.update(preservedLearned=e['learned'],preservedPaths=e['paths'])
    if e['id']=='mage-blood':
        spec.update(label='血の代価・一般経路から',targets=['key_02'],note='MP専用点を新たに買わず選ぶ経路。MP・効率・自然再生を無効にする原案。実戦のHP支払いは未実装。')
    if e['id']=='assassin':
        spec.update(label='暗殺者・64ptの成長例',targets=['g17n0','g41n3','g42n0'],note='会心・手数・範囲の3目標を52ptで取得。64ptは比較予算で、ゲームの採用済み上限ではない。弱点値は入力が未実装のため加算しない。')
    specs.append(spec)
def spec(id,label,pid,targets,budget=48,note=''):
    return dict(id=id,label=label,profileId=pid,siphon=False,targets=targets,budget=budget,note=note)
specs.append(spec('mage-owned-mp-blood','MP既得から血の代価','mage:support:plain:standard',['g12n2','g20n6','key_02'],note='任意に先に買ったMP点は消えない。費用と接続を保持し、MP・効率・再生の値を無効にする。Key払い戻しで復帰する。'))
specs.append(spec('mage-blood-shield','血の代価と障壁','mage:support:plain:standard',['key_02','v7g12n7'],note='一般経路から血の代価を選び、HP・装甲の共通入口から所有する障壁へ。新たなMP専用点は不要。'))
specs.append(spec('mage-shield-entry','障壁・MP投資を省く','mage:support:plain:standard',['v7g12n7'],note='共通入口はHPと装甲。障壁の枝はSHIELD入力で選び、MP容量・効率の枝は任意。世界全体の最短路でMPを一切買わないことを保証する例ではない。'))
for budget in (48,64,80):
    arms=[('area','手数と範囲',['g41n3','g42n0']),('crit','会心と手数',['g17n0','g41n3'])]
    for arm,label,targets in arms:
        if budget>=64:
            targets=targets+(['g41n6','g41n7','g42n1','g42n2','g42n6','g42n7'] if arm=='area' else ['g17n1','g41n0','g41n6','g41n7'])
        if budget>=80:
            targets=targets+(['v7g10n0','v7g10n2','v7g10n4'] if arm=='area' else ['v7g13n0','v7g13n2','v7g13n4'])
        specs.append(spec(f'assassin-{arm}-{budget}',f'暗殺者・{budget}pt / {label}','assassin:mark:plain:standard',targets,budget,note=f'同じ技能編成・MOD・通路HPで{budget}pt予算を比較。残りも表示する育成例で、最適解や採用済み上限ではない。弱点・出血の未実装入力は加算しない。'))
life=next(e for e in prior if e['id']=='tank-life')
life_targets=life['targets']+['opening_templar_2_3','g28n2','g23n1','opening_templar_1_3','opening_warrior_1_3','g6n5','g23n2']
specs.append(spec('tank-life-reinvest-43','生命・43ptへ防御再投資',life['profileId'],life_targets,note='短縮した31pt経路から12ptを防御へ再投資。旧43pt経路と同じ費用で比較。防御値を回収できる一方、旧経路の資源22・効率8は持たない。'))
examples=[example(s) for s in specs]
for e in examples:assert e['spent']<=e['budget'],(e['id'],e['spent'],e['budget'])
life_new=next(e for e in examples if e['id']=='tank-life-reinvest-43')
assert life_new['spent']==43,life_new

segments=[(e['a'],e['b'],a,b) for e in GRAPH['edges'] for a,b in zip(e['points'],e['points'][1:])]
A=np.array([r[2] for r in segments]);D=np.array([np.array(r[3])-r[2] for r in segments]);den=np.maximum((D*D).sum(axis=1),1e-12)
all_points=np.array([[n['x'],n['y']] for n in GRAPH['nodes']]);paint={}
for k,n in enumerate(GRAPH['nodes']):
    p=all_points[k];v=p-A;t=np.clip((v*D).sum(axis=1)/den,0,1);dist=np.linalg.norm(v-t[:,None]*D,axis=1)
    dist[np.array([n['id'] in r[:2] for r in segments])]=float('inf');pair=np.linalg.norm(all_points-p,axis=1);pair[k]=float('inf')
    paint[n['id']]=dict(wire=float(np.min(dist)),pair=float(np.min(pair)))
payload=dict(source=SOURCE,inputOverlay=OVERLAY,graph=GRAPH,icons=copy.deepcopy(old_payload['icons']),examples=examples,paintBounds=paint,centralCenter=old_payload['centralCenter'])
template=(ROOT/'scripts/skill-tree-poe2-central-v6-template.html').read_text(encoding='utf8')
template=template.replace('645点・47領域・5職業・15 Key','789点・62領域・5職業・14 Key候補＋1 Notable候補').replace('5起点を中央に集めた配分試作 / 採用前','入口・入力・同予算の比較 / 未採用').replace('poe2-central-v6','contract-v8')
template=template.replace('5職業の起点を中央の一まとまりへ置き、異なる47領域へ進む全体比較です。全645点の効果・費用・入力条件は保持。全Keyは任意の行き止まり。交差隠しやノードを跨ぐ接続は使っていません。','中央の5起点を保持した789点・62領域の比較案です。血の代価の独立入口、MP投資なしの障壁入口、会心を捨てた後の貫通、実際に持つ範囲攻撃を確認できます。終端15点のうち仲間の盾はNotable候補です。')
template=template.replace('15 Keyの係数や代償は原案です。','Key・Notableの効果や係数は未採用の候補です。').replace('<label>予算<select','<label>比較予算（未採用）<select')
template=template.replace('<div class="tiny" id="inactiveStats"></div>','<div class="tiny" id="inactiveStats"></div><div id="effectContract"></div>')
template=template.replace('S=P.source,G=P.graph',"S=JSON.parse(JSON.stringify(P.source)),G=P.graph")
needle="for(const e of G.edges){adj.get(e.a).push(e.b);adj.get(e.b).push(e.a)}"
effective="""Object.assign(S.statInputs,P.inputOverlay.statInputs);Object.assign(S.requirements,P.inputOverlay.requirements);for(const p of S.profiles){p.areaSources=P.inputOverlay.profileAreaSources[p.id]||[];if(p.areaSources.length)p.flags.push('AREA_ATTACK')}
"""
template=template.replace(needle,effective+needle)
def replace_function(name,new):
    global template
    lines=template.splitlines();matches=[i for i,line in enumerate(lines) if line.startswith('function '+name+'(')]
    assert len(matches)==1,(name,matches)
    lines[matches[0]]=new;template='\n'.join(lines)+'\n'
original_usable=next(line for line in template.splitlines() if line.startswith('function usable('))
replace_function('usable',original_usable.replace('function usable(','function inputUsable(')+"\nfunction acquirable(n,loss=lostStats()){return inputUsable(n)&&(n.type==='start'||n.type==='keystone'||Object.keys(inputBenefits(n)).some(k=>!loss.has(k)))}\nfunction usable(n){return learned.has(n.id)?inputUsable(n):acquirable(n)}")
replace_function('plannedRoute',"""function plannedRoute(target=selected){const targetNode=by.get(target),loss=new Set([...lostStats(),...(S.lostStats[targetNode?.rule]||[])]);if(!targetNode||!acquirable(targetNode,loss)||conflict(targetNode))return null;if(learned.has(target))return {ids:[],cost:0};const prices=new Map([...learned].map(id=>[id,0])),prev=new Map(),q=[...learned].map(id=>[0,id]);while(q.length){q.sort((a,b)=>a[0]-b[0]||(a[1]<b[1]?-1:a[1]>b[1]?1:0));const [cost,u]=q.shift();if(prices.get(u)!==cost)continue;if(u===target){const ids=[u];while(prev.has(ids[0]))ids.unshift(prev.get(ids[0]));return {ids,cost}}for(const v of adj.get(u)){const n=by.get(v);if(!learned.has(v)&&(!acquirable(n,loss)||conflict(n)||n.type==='keystone'&&v!==target))continue;const nc=cost+(learned.has(v)?0:n.cost);if(nc<(prices.get(v)??Infinity)){prices.set(v,nc);prev.set(v,u);q.push([nc,v])}}}return null}""")
template=template.replace('some(id=>!usable(by.get(id))||conflict(by.get(id),ids))','some(id=>!inputUsable(by.get(id))||conflict(by.get(id),ids))')
template=template.replace("el('nodeKind').textContent=`${({", "el('nodeKind').textContent=`${n.classificationCandidate==='notable'?'Notable候補（分類・費用未採用）':({")
template=template.replace("})[n.type]} / ${n.cost}pt", "})[n.type]} / ${n.cost}pt")
# Rendering contracts uses textContent exclusively; fields are proposal data.
template=template.replace(";draw()}\nfunction hit", ";renderContract(n);draw()}\nfunction hit") if ';draw()}\nfunction hit' in template else template
render_line=next(line for line in template.splitlines() if line.startswith('function render('))
if 'renderContract(n)' not in render_line:
    replace_function('render',render_line[:-len('draw()}')]+'renderContract(n);draw()}')
contract_js="""function renderContract(n){const box=el('effectContract');box.replaceChildren();const c=n.effectContract;if(!c)return;const title=document.createElement('h3');title.textContent='効果の候補・実戦未実装';box.append(title);for(const [k,label] of [['trigger','発生条件'],['effect','変わる動き'],['cost','代償'],['stack','重複'],['cap','上限']]){const p=document.createElement('p');p.textContent=label+'：'+c[k];box.append(p)}const p=document.createElement('p');p.className='warn';p.textContent='未決：'+c.pending.join('・');box.append(p)}
"""
template=template.replace('function render(){',contract_js+'function render(){')
template=template.replace("window.wholeTree={source:S,", "window.wholeTree={source:S,canonicalSource:P.source,")
fonts='\n'.join(re.findall(r'@font-face\{[^}]+\}',old_html))
html=template.replace('/*FONTS*/',fonts).replace('/*PAYLOAD*/',json.dumps(payload,ensure_ascii=False,separators=(',',':')))
(OUT/'ProjectS_Contract_V8.html').write_text(html,encoding='utf8')
(OUT/'route-examples.json').write_text(json.dumps(examples,ensure_ascii=False,indent=2),encoding='utf8')
manifest=dict(status='CANDIDATE_PENDING_VERIFICATION',adopted=False,runtimeApplied=False,source645Sha256=hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),source645EmbeddedVerbatim=True,originalNodeExceptions=['key_02','key_09','key_11','key_13','key_14','key_15'],central60AndIncidentEdgesExact=True,displayedNodes=len(GRAPH['nodes']),displayedGroups=len(GRAPH['groups']),connections=len(GRAPH['edges']),terminalEffectNodes=15,keyCandidates=14,notableCandidates=1,notableClassificationAndCostAdopted=False,sourceWeaponProfiles=60,saveScope='preview-only',saveStudy='contract-v8',previousSaveCodesAccepted=False,approvedFontsAndSpritesExact=True,comparisonBudgets=[48,64,80],budgetAdopted=False,sourceOverlay=OVERLAY,cautions=['No game combat, native save or packet changed.','Existing MP allocations remain paid, connected and suppressed until manual refund.','Effect contract coefficients marked null are unresolved; no combat balance proof.'])
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'examples':[(e['id'],e['spent'],e['budget']) for e in examples],'htmlBytes':len(html.encode()),'life43':life_new['stats']},ensure_ascii=False))
