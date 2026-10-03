"""Reuse approved V8 canvas/art and add a real-data start/direction comparison."""
import copy, hashlib, json, re
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview'
OLD=BASE/'proposals/workshop-contract-v8'
OUT=BASE/'proposals/workshop-specialization-v9'
def read(p):return json.loads(p.read_text(encoding='utf8'))
html=(OLD/'ProjectS_Contract_V8.html').read_text(encoding='utf8')
match=re.search(r'<script id="wholeTreeData" type="application/json">(.*?)</script>',html,re.S)
payload=json.loads(match[1]);baseline=payload['graph'];g=read(OUT/'candidate-graph.json')
payload.update(graph=g,previousGraph=baseline,examples=read(OUT/'route-examples.json'),directions=read(OUT/'specialization-directions.json'))
segments=[(e['a'],e['b'],a,b) for e in g['edges'] for a,b in zip(e['points'],e['points'][1:])]
A=np.array([r[2] for r in segments]);D=np.array([np.array(r[3])-r[2] for r in segments]);den=np.maximum((D*D).sum(axis=1),1e-12)
points=np.array([[n['x'],n['y']] for n in g['nodes']]);paint={}
for k,n in enumerate(g['nodes']):
 p=points[k];v=p-A;t=np.clip((v*D).sum(axis=1)/den,0,1);dist=np.linalg.norm(v-t[:,None]*D,axis=1);dist[np.array([n['id'] in r[:2] for r in segments])]=float('inf');pair=np.linalg.norm(points-p,axis=1);pair[k]=float('inf');paint[n['id']]=dict(wire=float(np.min(dist)),pair=float(np.min(pair)))
payload['paintBounds']=paint
html=html[:match.start(1)]+'/*SPECIALIZATION_PAYLOAD*/'+html[match.end(1):]
html=html.replace('contract-v8','specialization-v9').replace('789点・62領域','850点・67領域').replace('入口・入力・配分例の比較 / 未採用','5始点 × 2方向 / 同消費12pt・未採用')
html=html.replace('中央から、育てる道を選ぶ','始点から、得意を育てる')
html=html.replace('<option selected>48</option>','<option selected>12</option><option>24</option><option>48</option>')
html=html.replace('budget=48,roadChoice','budget=12,roadChoice').replace('![48,64,80].includes(x.budget)','![12,24,48,64,80].includes(x.budget)')
html=html.replace('<button id="focusBuild">配分を拡大</button>','<button id="focusBuild">配分を拡大</button><button id="compareStarts">始点と2方向</button>')
html=html.replace('<h3>選んだ育成の恩恵</h3>','<div id="directionIntent" class="intent"></div><h3>選んだ育成の恩恵</h3>')
css='''
.intent{border-left:3px solid #c5ae78;padding:8px 12px;margin:12px 0;background:#293326}.intent strong{color:#e1cd9c}.intent p{font-size:12px;line-height:1.7;margin:7px 0}dialog{width:min(1180px,96vw);max-height:92vh;color:var(--ink);background:#202d22;border:3px double #b39963;padding:20px 25px}dialog::backdrop{background:#08110de0}.compare-head{display:flex;justify-content:space-between;gap:14px;align-items:start;border-bottom:1px solid #877950;padding-bottom:12px}.compare-head h2{font:23px ps-serif;margin:2px 0;color:#e1cd9c}.compare-head p{margin:7px 0;font-size:12px;color:#b5bfa8}.compare-controls{display:flex;gap:10px;flex-wrap:wrap;margin:15px 0}.compare-controls label{flex:1;min-width:220px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:16px}.pair article{padding:15px;border:1px solid #727b54;background:#2a3427}.pair h3{margin:0;color:#e5cf9b;font:20px ps-serif}.pair p{line-height:1.7;font-size:12px}.pair .values{margin:12px 0}.order{font-size:11px;line-height:1.8;color:#b6c3a9}.cost-wrap{overflow:auto}.cost-table{width:100%;border-collapse:collapse;font-size:12px}.cost-table th,.cost-table td{padding:8px 10px;text-align:right;border-bottom:1px solid #48563e;white-space:nowrap}.cost-table th:first-child,.cost-table td:first-child{text-align:left}.cost-table .best{color:#f1d38f;background:#41482d;font-weight:bold}.compare-note{font-size:12px;line-height:1.7;color:#b5bfa8}.proof{border:1px solid #6c7851;padding:10px 14px;margin:15px 0;color:#d2ddbd;font-size:12px;line-height:1.7}@media(max-width:800px){dialog{padding:14px}.pair{grid-template-columns:1fr}.compare-head h2{font-size:20px}.toolbar{flex-wrap:wrap}.toolbar select{flex-basis:100%}.map-key{top:100px}.compare-controls label{min-width:170px}.cost-table th,.cost-table td{padding:7px}.pair h3{font-size:18px}}
'''
html=html.replace('</style>',css+'</style>')
html=html.replace('</style>','.compare-head button{flex:none;width:70px;white-space:nowrap;padding:9px 10px}.compare-controls label{display:flex;align-items:center;gap:12px}.compare-controls select{flex:1;min-width:0;width:auto;max-width:none;padding:8px}@media(max-width:800px){.compare-controls label{display:block}.compare-controls select{display:block;width:100%;margin-top:7px}}\n</style>')
dialog='''<dialog id="startComparison" aria-labelledby="compareTitle"><div class="compare-head"><div><h2 id="compareTitle">始点の得意と、同消費の2方向</h2><p>中央の形はそのまま。近くで深掘りする能力と、越境で払う機会費用を比べる。</p></div><button id="closeComparison" aria-label="比較を閉じる">閉じる</button></div><div class="compare-controls"><label>同消費12ptの職業<select id="compareJob"></select></label><label>費用表の入力<select id="compareMode"><option value="fixed">同一Warrior-support入力で5始点を比較</option><option value="owned">各職のsupport入力で到達性を確認</option></select></label></div><div class="pair" id="directionPair"></div><p class="compare-note">追加パッシブ値の比較。技能・専用MODの効果は増やさず、実戦DPSや操作感の証明は含まない。職業内の2例は編成・MOD・通路・使用12ptを共通にする。</p><h3>専門帯の深掘りまで、何ptか</h3><p class="compare-note" id="atlasInput"></p><div class="cost-wrap"><table class="cost-table" id="costAtlas"></table></div><div class="proof" id="outerProof"></div><details><summary>既存の外側目標：V8 → 今回</summary><div class="cost-wrap"><table class="cost-table" id="oldGoalCosts"></table></div></details><p class="compare-note">各目標へ別々に到達する最小費用。表全体を同時取得する費用ではない。比較予算と新しい能力値は未採用。ゲーム本編のポイント・保存・packetは未変更。</p></dialog>'''
html=html.replace('<script id="wholeTreeData"',dialog+'<script id="wholeTreeData"')
js='''
const specializationIds=P.directions.flatMap(d=>d.nodeIds),atlasCache=new Map();
function specializationFocus(job){const d=P.directions.find(d=>d.job===job);focus([S.origins.find(o=>o.id===job).root,...G.origins.find(o=>o.id===job).lanes.flatMap(l=>l.nodes),...d.nodeIds])}
function startAtlas(graph=G,mode='fixed'){
 const nodes=new Map(graph.nodes.map(n=>[n.id,n])),links=new Map(graph.nodes.map(n=>[n.id,[]]));for(const e of graph.edges){links.get(e.a).push(e.b);links.get(e.b).push(e.a)}
 const result={};for(const o of S.origins){const p=profiles.find(p=>p.id===(mode==='fixed'?'warrior':o.id)+':support:plain:standard'),f=new Set(p.flags),root=o.root,prices=new Map([[root,0]]),q=[[0,root]];
  const eligible=n=>n.type==='start'?n.id===root:n.type!=='keystone'&&Object.keys(n.type==='road'?{hp:2}:n.stats).some(k=>(S.statInputs[k]||[]).every(flag=>f.has(flag)));
  while(q.length){q.sort((a,b)=>a[0]-b[0]);const [cost,u]=q.shift();if(prices.get(u)!==cost)continue;for(const v of links.get(u)){const n=nodes.get(v);if(!eligible(n))continue;const nc=cost+n.cost;if(nc<(prices.get(v)??Infinity)){prices.set(v,nc);q.push([nc,v])}}}result[o.id]=Object.fromEntries(prices)
 }return result
}
function cachedAtlas(mode){if(!atlasCache.has(mode))atlasCache.set(mode,{current:startAtlas(G,mode),previous:startAtlas(P.previousGraph,mode)});return atlasCache.get(mode)}
function exampleValues(e){const old={profile,learned,budget,roadChoice,siphon};profile=profiles.find(p=>p.id===e.profileId);learned=new Set(e.learned);budget=e.budget;roadChoice='hp';siphon=false;const values=totals();({profile,learned,budget,roadChoice,siphon}=old);return values}
function renderDirection(){const box=el('directionIntent');box.replaceChildren();const e=P.examples.find(e=>e.id===activeExample);if(!e)return;const h=document.createElement('strong');h.textContent=e.direction+' / 使用12pt';box.append(h);for(const text of [e.play,'装備選好：'+e.gear]){const p=document.createElement('p');p.textContent=text;box.append(p)}}
function renderComparison(){const job=el('compareJob').value,mode=el('compareMode').value,cols=S.origins,pair=el('directionPair');pair.replaceChildren();for(const e of P.examples.filter(e=>e.profileId.startsWith(job+':'))){const card=document.createElement('article'),h=document.createElement('h3');h.textContent=e.direction;card.append(h);const stats=document.createElement('div');stats.className='values';stats.innerHTML=statRows(exampleValues(e));card.append(stats);for(const text of ['使用12pt / 未消費0pt / 追加MODなし',e.play,'装備選好：'+e.gear]){const p=document.createElement('p');p.textContent=text;card.append(p)}const order=document.createElement('details'),summary=document.createElement('summary');summary.textContent='実データの取得順';order.append(summary);const p=document.createElement('p');p.className='order';p.textContent=[...new Set(e.paths.flatMap(path=>path.nodes))].filter(id=>by.get(id).type!=='start').map((id,i)=>(i+1)+'. '+by.get(id).name).join(' → ');order.append(p);card.append(order);const button=document.createElement('button');button.textContent='この12pt配分を開く';button.onclick=()=>{applyExample(e.id);specializationFocus(job);el('startComparison').close()};card.append(button);pair.append(card)}
 const {current,previous}=cachedAtlas(mode);el('atlasInput').textContent=mode==='fixed'?'装備・技能入力を同一にし、始点の位置と接続だけを比較。MP・障壁入力もWarrior-supportの所有条件へ固定。':'各職のsupport編成・追加MODなし。入力の差も含むため、固定入力の表と分けて読む。';
 const head='<thead><tr><th>目標</th>'+cols.map(o=>'<th>'+o.id+'</th>').join('')+'</tr></thead>',body=[];for(const d of P.directions)for(let a=0;a<2;a++){const id=d.goals[a],v=cols.map(o=>current[o.id][id]??Infinity),best=Math.min(...v);body.push('<tr><td>'+d.job+' / '+d.arms[a].label+'</td>'+v.map(n=>'<td class="'+(n===best?'best':'')+'">'+(Number.isFinite(n)?n:'入力不足')+'</td>').join('')+'</tr>')}el('costAtlas').innerHTML=head+'<tbody>'+body.join('')+'</tbody>';
 const ids=G.nodes.filter(n=>n.type==='notable'&&!n.opening&&!specializationIds.includes(n.id)&&Number.isFinite(current.warrior[n.id])&&Number.isFinite(current.tank[n.id])&&Number.isFinite(previous.warrior[n.id])&&Number.isFinite(previous.tank[n.id])).map(n=>n.id),count=atlas=>{const r={warrior:0,tank:0,tie:0};for(const id of ids)r[atlas.warrior[id]<atlas.tank[id]?'warrior':atlas.tank[id]<atlas.warrior[id]?'tank':'tie']++;return r},before=count(previous),after=count(current);el('outerProof').textContent='同じ既存外側目標 '+ids.length+'点でのWarrior / Tank比較：V8は '+before.warrior+' / '+before.tank+'（同費用'+before.tie+'）、今回は '+after.warrior+' / '+after.tank+'（同費用'+after.tie+'）。Tankの独立出口で費用の優位が分かれ、Warriorが安い目標も残る。';
 const oldTargets=['g6n0','g18n1','g31n4','g35n0','g12n2','g41n3','g25n6'];el('oldGoalCosts').innerHTML=head+'<tbody>'+oldTargets.map(id=>'<tr><td>'+by.get(id).name+'</td>'+cols.map(o=>'<td>'+(previous[o.id][id]??'—')+' → '+(current[o.id][id]??'—')+'</td>').join('')+'</tr>').join('')+'</tbody>'
}
for(const o of S.origins)el('compareJob').add(new Option(o.name,o.id));el('compareStarts').onclick=()=>{el('compareJob').value=profile.start;renderComparison();el('startComparison').showModal()};el('closeComparison').onclick=()=>el('startComparison').close();el('compareJob').onchange=renderComparison;el('compareMode').onchange=renderComparison;
'''
html=html.replace('function render(){',js+'function render(){')
html=html.replace("if(!e)return;const h=document.createElement('strong')","if(!e||!e.direction)return;const h=document.createElement('strong')")
html=html.replace(".filter(e=>e.profileId.startsWith(job+':'))", ".filter(e=>e.direction&&e.profileId.startsWith(job+':'))")
html=html.replace('<details><summary>既存の外側目標', '<div class="proof" id="fusionProof"></div><details><summary>既存の外側目標')
html=html.replace("\n}\nfor(const o of S.origins)el('compareJob')", "\n const fusion=el('fusionProof');fusion.replaceChildren();for(const e of P.examples.filter(e=>e.fusion)){const p=document.createElement('p');p.textContent=e.label+'：使用'+e.spent+'pt / 上限24pt / 未消費'+e.remaining+'pt。';const b=document.createElement('button');b.textContent='越境配分を開く';b.onclick=()=>{applyExample(e.id);focus(e.focusIds);el('startComparison').close()};p.append(b);fusion.append(p)}\n}\nfor(const o of S.origins)el('compareJob')")
html=html.replace('renderContract(n);draw()}','renderContract(n);renderDirection();draw()}')
html=html.replace("window.wholeTree={source:S,", "window.wholeTree={source:S,startAtlas,cachedAtlas,exampleValues,specializationFocus,renderComparison,")
html=html.replace("el('center').onclick=()=>{focus(G.origins.flatMap(o=>[o.root,...o.lanes.flatMap(l=>l.nodes)]));render()}","el('center').onclick=()=>{focus([...G.origins.flatMap(o=>[o.root,...o.lanes.flatMap(l=>l.nodes)]),...specializationIds]);render()}")
html=html.replace("el('focusBuild').onclick=()=>focus([...learned]);", "el('focusBuild').onclick=()=>specializationFocus(profile.start);")
html=html.replace("el('applyExample').onclick=()=>applyExample(el('example').value);", "el('applyExample').onclick=()=>{applyExample(el('example').value);specializationFocus(profile.start)};")
html=html.replace("selected=rootId();focus([rootId(),...currentOrigin().lanes.flatMap(l=>l.nodes)]);render()", "selected=rootId();specializationFocus(profile.start);render()")
# Exact source units: increased critical chance differs from added multiplier points.
lines=html.splitlines();idx=next(i for i,l in enumerate(lines) if l.startswith('function statRows('))
lines[idx]="function statRows(values){return Object.entries(values).map(([k,v])=>`<div>${labels[k]||k}</div><div>+${Number(v.toFixed(2))} ${S.stats[k]?.unit||'%'}</div>`).join('')}"
html='\n'.join(lines)+'\n'
html=html.replace('captionCache=null;fit();render()','captionCache=null;specializationFocus(profile.start);render()')
html=html.replace('syncProfile();new ResizeObserver','applyExample(P.examples[0].id);syncProfile();new ResizeObserver')
html=html.replace('中央の5起点を保持した850点・67領域の比較案です。血の代価の独立入口、MP投資なしの障壁入口、会心を捨てた後の貫通、実際に持つ範囲攻撃を確認できます。終端15点のうち仲間の盾はNotable候補です。','承認済み中央の位置と線を保持し、5始点に二方向の専門帯を置いた比較案。初期内容とTankの外側出口を見直し、同消費12ptで育てる能力と装備選好を比較します。')
html=html.replace('/*SPECIALIZATION_PAYLOAD*/',json.dumps(payload,ensure_ascii=False,separators=(',',':')))
(OUT/'ProjectS_Specialization_V9.html').write_text(html,encoding='utf8')
manifest=dict(status='CANDIDATE_PENDING_ACTUAL_JS',adopted=False,runtimeApplied=False,sourceEmbeddedVerbatim=True,sourceSha256=hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),preservedV8Sha256=hashlib.sha256((OLD/'ProjectS_Contract_V8.html').read_bytes()).hexdigest(),centralPositionsAndOldEdgesExact=True,changedInitialContents=True,nodes=len(g['nodes']),groups=len(g['groups']),edges=len(g['edges']),newNodes=61,newGroups=5,examples=12,equalConsumptionExamples=10,equalConsumption=12,fusionExamplesWithUnusedBudget=2,saveStudy='specialization-v9',budgetAdopted=False,nativeFilesChanged=False,gameSaveOrPacketChanged=False,notes=['Numeric passives remain unimplemented proposals; no combat DPS proof.','No new class abilities, MOD duplication, armor restriction, mandatory magic, bleed or own-low-HP build.','Some historic outer regions still favor Warrior; small candidate, not whole-tree rebalance.'])
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(dict(htmlBytes=len(html.encode()),nodes=len(g['nodes']),examples=len(payload['examples']))))
