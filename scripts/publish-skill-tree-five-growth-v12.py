"""Local V12 preview preserving the V11 renderer, source artwork and geometry."""
import hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview/proposals';V11=BASE/'workshop-growth-v11';OUT=BASE/'workshop-five-growth-v12'
def read(p):return json.loads(p.read_text(encoding='utf8'))
source=V11/'ProjectS_Growth_V11.html';html=source.read_text(encoding='utf8');pattern=r'<script id="wholeTreeData" type="application/json">(.*?)</script>';m=re.search(pattern,html,re.S);P=json.loads(m[1]);previous=P['graph'];P.update(previousGraph=previous,graph=read(OUT/'candidate-graph.json'),examples=read(OUT/'route-examples.json'),growth=read(OUT/'five-growth-authoring.json'))
assert all(c['after']['icon'] in P['icons'] for c in P['growth']['nodeChanges'])
html=html[:m.start(1)]+'/*PAYLOAD*/'+html[m.end(1):]
html=html.replace('V11','V12').replace('V9・V12','V11・V12').replace('V9 → V12','V11 → V12').replace('：V9は ', '：V11は ')
html=html.replace("study:'growth-v11'","study:'five-growth-v12'").replace("x.study!=='growth-v11'","x.study!=='five-growth-v12'")
html=html.replace('V9/V10のコードとは版が異なります。','V11以前のコードとは版が異なります。')
html=html.replace('V9の850点・全座標・中央の全接続を保持。運用群の旧入口1辺を付け替え、運用・容量・再生の群へ3辺を追加。4目標と旧入口点の報酬を見直し、12ptから24ptへの選択を比べます。点・通路は増やしていません。','V11の850点・全座標・1011辺・中央の内容を保持。既存8点の報酬と4点の表示種別を見直し、障壁未所有時に迂回していた二つの入口へ使える防御を加えました。五始点で12ptから24ptの育成を選べます。点・通路・線は増やしていません。')
html=html.replace('主力・手数、または容量・回転の配分を選ぶ。','育てる能力と配分を選ぶ。')
html=html.replace('職業内の2例は編成・MOD・通路・使用12ptを共通にする。','12pt同士・24pt同士で消費を揃え、職業内では編成・MOD・通路を共通にする。')
start=html.index('const ids=G.nodes.filter(n=>n.type===');end=html.index('.map(n=>n.id),count=',start)+len('.map(n=>n.id)')
html=html[:start]+'const ids=P.growth.common92Ids'+html[end:]
html=html.replace("['v7g14n2','v7g14n3','v7g06n3','v7g06n5']", "P.growth.goalIdsByJob[job]")
html=html.replace("el('growthProof').append(wrap)","el('growthProof').append(wrap);renderGlobalBias()")
js=r'''
function renderGlobalBias(){
 const div=document.createElement('div');div.className='cost-wrap';const title=document.createElement('p');title.textContent='同じ既存外側92目標：五始点のうち単独で最安になる目標数。各職の所有入力では入口の障壁条件を外し、固定入力では全接続が同じなので費用差を維持する。';div.append(title);const table=document.createElement('table');table.className='cost-table';const head=document.createElement('tr');for(const s of ['費用表の条件',...S.origins.map(o=>o.name),'同率最安']){const th=document.createElement('th');th.textContent=s;head.append(th)}table.append(head);for(const [mode,version,label] of [['fixed','previous','固定入力 / V11'],['fixed','current','固定入力 / V12'],['owned','previous','各職の所有入力 / V11'],['owned','current','各職の所有入力 / V12']]){const row=document.createElement('tr'),th=document.createElement('th');th.textContent=label;row.append(th);const data=P.growth.common92Dominance[mode][version];for(const o of S.origins){const td=document.createElement('td');td.textContent=data.strictMinimumByStart[o.id];if(mode==='owned'&&version==='current'&&data.strictMinimumByStart[o.id]>P.growth.common92Dominance.owned.previous.strictMinimumByStart[o.id])td.className='best';row.append(td)}const td=document.createElement('td');td.textContent=data.sharedMinimumTargets;row.append(td);table.append(row)}div.append(table);el('growthProof').append(div)
}
'''
html=html.replace('function renderGrowth(){',js+'\nfunction renderGrowth(){')
html=html.replace('/*PAYLOAD*/',json.dumps(P,ensure_ascii=False,separators=(',',':')))
dest=OUT/'ProjectS_Five_Growth_V12.html';dest.write_text(html,encoding='utf8')
manifest=dict(status='PENDING_ACTUAL_BROWSER',runtimeApplied=False,nativeFilesChanged=False,nodes=850,groups=67,edges=1011,addedNodes=0,addedRoads=0,addedEdges=0,removedEdges=0,changedRewards=8,changedTypes=4,allCoordinatesExact=True,allEdgesExact=True,centralContentsExact=True,saveStudy='five-growth-v12',sourceHtml=str(source.relative_to(ROOT)),sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),candidateSha256=hashlib.sha256(dest.read_bytes()).hexdigest(),canonicalSourceExact=True,approvedSpritesAndFontsExact=True,gameSaveAndPacketChanged=False)
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(dict(htmlBytes=dest.stat().st_size,examples=len(P['examples']),growthExamples=sum(bool(e.get('growth')) for e in P['examples']))))
