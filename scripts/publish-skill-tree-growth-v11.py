"""Publish the V11 actual graph and staged builds using the preserved V10 UI."""
import hashlib,json,re
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview/proposals';OUT=BASE/'workshop-growth-v11';V9=BASE/'workshop-specialization-v9';V10=BASE/'workshop-affinity-v10'
def read(p):return json.loads(p.read_text(encoding='utf8'))
source=V10/'ProjectS_Affinity_V10.html';html=source.read_text(encoding='utf8');pattern=r'<script id="wholeTreeData" type="application/json">(.*?)</script>';m=re.search(pattern,html,re.S);P=json.loads(m[1]);g=read(OUT/'candidate-graph.json');P.update(previousGraph=P['graph'],graph=g,examples=read(OUT/'route-examples.json'),growth=read(OUT/'growth-authoring.json'))
segments=[(e['a'],e['b'],p,q) for e in g['edges'] for p,q in zip(e['points'],e['points'][1:])];A=np.array([r[2] for r in segments]);D=np.array([np.array(r[3])-r[2] for r in segments]);den=np.maximum((D*D).sum(axis=1),1e-12);points=np.array([[n['x'],n['y']] for n in g['nodes']]);paint={}
for k,n in enumerate(g['nodes']):
 p=points[k];v=p-A;t=np.clip((v*D).sum(axis=1)/den,0,1);dist=np.linalg.norm(v-t[:,None]*D,axis=1);dist[np.array([n['id'] in r[:2] for r in segments])]=float('inf');pair=np.linalg.norm(points-p,axis=1);pair[k]=float('inf');paint[n['id']]=dict(wire=float(np.min(dist)),pair=float(np.min(pair)))
P['paintBounds']=paint;html=html[:m.start(1)]+'/*PAYLOAD*/'+html[m.end(1):]
html=html.replace('V10 · 始点の費用比較','V11 · 12/24ptの育成').replace('5始点 × 2方向 / 同消費12pt・未採用','同消費12pt → 24ptの育成 / 未採用')
html=html.replace("study:'specialization-v9'","study:'growth-v11'").replace("x.study!=='specialization-v9'","x.study!=='growth-v11'")
html=html.replace('V8・V9','V9・V11').replace('V8 → 今回','V9 → V11').replace("'点でのWarrior / Tank比較：V8は '","'点でのWarrior / Tank比較：V9は '")
html=html.replace('<p class="compare-note">追加パッシブ値の比較。','<section id="growthSection"><h3>12ptの全配分を残して、24ptへ育てる</h3><div id="growthPair" class="pair"></div><div id="growthProof" class="proof"></div></section><p class="compare-note">追加パッシブ値の比較。')
html=html.replace('この画面の配分を保存。ゲームのセーブには反映しません。','V11の配分を保存。V9/V10のコードとは版が異なります。ゲームのセーブには反映しません。')
html=html.replace('承認済み中央の位置と線を保持し、5始点に二方向の専門帯を置いた比較案。初期内容とTankの外側出口を見直し、同消費12ptで育てる能力と装備選好を比較します。','V9の850点・全座標・中央の全接続を保持。運用群の旧入口1辺を付け替え、運用・容量・再生の群へ3辺を追加。4目標と旧入口点の報酬を見直し、12ptから24ptへの選択を比べます。点・通路は増やしていません。')
html=html.replace("if(!e||!e.direction)return;", "if(!e||!e.direction&&!e.growth)return;")
html=html.replace("h.textContent=e.direction+' / 使用12pt'", "h.textContent=(e.direction||e.play)+' / 使用'+e.spent+'pt'")
js=r'''
function renderGrowth(){
 const job=el('compareJob').value,examples=P.examples.filter(e=>e.growth&&e.profileId.startsWith(job+':')),pair=el('growthPair');pair.replaceChildren();el('growthSection').hidden=!examples.length;el('growthSection').querySelector('h3').textContent=S.origins.find(o=>o.id===job).name+'：12ptの全配分を残して、24ptへ育てる';
 for(const e of examples){const seed=P.examples.find(p=>p.id===e.baseExampleId),card=document.createElement('article'),title=document.createElement('h3');title.textContent=e.play;card.append(title);const p=document.createElement('p');p.textContent='使用12pt → 使用24pt / 未消費0pt。12ptの取得済み点は全て残す。';card.append(p);const values=document.createElement('div');values.className='values';values.innerHTML=Object.keys(e.stats).map(k=>'<div>'+labels[k]+'</div><div>'+Number((seed.stats[k]||0).toFixed(2))+' → '+Number(e.stats[k].toFixed(2))+' '+(S.stats[k]?.unit||'%')+'</div>').join('');card.append(values);const targets=document.createElement('p');targets.textContent='追加目標：'+e.targets.filter(id=>!seed.targets.includes(id)).map(id=>by.get(id).name).join(' / ');card.append(targets);for(const [id,text] of [[seed.id,'12pt配分を開く'],[e.id,'24pt配分を開く']]){const b=document.createElement('button');b.textContent=text;b.onclick=()=>{applyExample(id);focus(P.examples.find(x=>x.id===id).focusIds);el('startComparison').close()};card.append(b)}pair.append(card)}
 const union=P.growth.two24PointUnionCosts.find(r=>r.job===job);el('growthProof').textContent=union?'この職業の24pt二例を両方取得すると '+union.unionSpend+'pt。24ptでは全取りできず、主力・手数、または容量・回転の配分を選ぶ。数値は未採用の追加パッシブで、実戦DPSの測定ではない。':'';
 if(examples.length){const table=document.createElement('table');table.className='cost-table';const head=document.createElement('tr');for(const text of ['同一目標',...S.origins.map(o=>o.name)]){const th=document.createElement('th');th.textContent=text;head.append(th)}table.append(head);for(const id of ['v7g14n2','v7g14n3','v7g06n3','v7g06n5']){const row=document.createElement('tr'),a=targetAffinity(id),name=document.createElement('th');name.textContent=by.get(id).name;row.append(name);for(const o of S.origins){const td=document.createElement('td');td.textContent=a.costs[o.id]===null?'—':a.costs[o.id]+'pt';if(a.winners.includes(o.id))td.className='best';row.append(td)}table.append(row)}const wrap=document.createElement('div');wrap.className='cost-wrap';wrap.append(table);el('growthProof').append(wrap)}
}
'''
html=html.replace('function renderComparison(){','function renderComparison(){renderGrowth();')
html=html.replace('function render(){',js+'\nfunction render(){')
html=html.replace('window.wholeTree={source:S,','window.wholeTree={source:S,renderGrowth,')
html=html.replace('</style>','#growthPair button{margin-right:6px;margin-top:8px}#growthPair .values{font-size:11px}#growthProof table{margin-top:12px}.compare-head{position:sticky;top:-20px;background:#202d22;z-index:3;padding-top:12px}@media(max-width:800px){.compare-head{top:-14px}}\n</style>')
html=html.replace('/*PAYLOAD*/',json.dumps(P,ensure_ascii=False,separators=(',',':')))
dest=OUT/'ProjectS_Growth_V11.html';dest.write_text(html,encoding='utf8')
manifest=dict(status='PENDING_ACTUAL_BROWSER',runtimeApplied=False,nativeFilesChanged=False,nodes=850,groups=67,edges=1011,addedNodes=0,addedRoads=0,addedEdges=3,removedOuterGroupEntryEdges=1,changedRewards=5,oldCoordinatesExact=True,otherOldEdgesExact=True,centralEdgesExact=True,saveStudy='growth-v11',sourceHtml=str(source.relative_to(ROOT)),sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),candidateSha256=hashlib.sha256(dest.read_bytes()).hexdigest(),canonicalSourceExact=True,approvedSpritesAndFontsExact=True,gameSaveAndPacketChanged=False)
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'htmlBytes':dest.stat().st_size,'examples':len(P['examples'])}))
