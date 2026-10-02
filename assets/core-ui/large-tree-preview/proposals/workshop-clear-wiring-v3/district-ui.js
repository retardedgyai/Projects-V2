/* Hierarchical reading of the same passive nodes. Preview only, no game effects. */
(()=>{
 const api=window.structureProposal;
 const previous=JSON.parse(document.getElementById('previousGraphData').textContent);
 const original=api.original,candidate=api.candidate,comparison=api.comparison;
 const originalDisplay=JSON.parse(document.getElementById('originalDisplayData').textContent);
 const toggle=document.getElementById('structureToggle'),marker=document.querySelector('.sidebar>.block>.status');
 const selector=document.createElement('select');selector.id='structureMode';selector.style.cssText='display:block;width:100%;margin-top:8px';selector.innerHTML='<option value="proposal">配線案3 / 採用前</option><option value="previous">前案 a342d87c</option><option value="original">保持した原版</option>';marker.after(selector);
 let mode='proposal';
 function hull(points){const ps=[...new Map(points.map(p=>[p.x+','+p.y,p])).values()].sort((a,b)=>a.x-b.x||a.y-b.y);if(ps.length<3)return ps;const cross=(a,b,c)=>(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x),lo=[],hi=[];for(const p of ps){while(lo.length>1&&cross(lo.at(-2),lo.at(-1),p)<=0)lo.pop();lo.push(p)}for(const p of [...ps].reverse()){while(hi.length>1&&cross(hi.at(-2),hi.at(-1),p)<=0)hi.pop();hi.push(p)}return lo.slice(0,-1).concat(hi.slice(0,-1))}
 const makeDistricts=(graph)=>graph.structureProposal.districts.map(d=>{
  const nodes=[...new Set(d.nodes)],points=nodes.map(id=>graph.nodes.find(n=>n.id===id));const center={x:points.reduce((s,p)=>s+p.x,0)/points.length,y:points.reduce((s,p)=>s+p.y,0)/points.length};const boundary=hull(points).map(p=>({x:center.x+(p.x-center.x)*1.1,y:center.y+(p.y-center.y)*1.1}));
  return {...d,nodes,x:center.x,y:center.y,boundary,labelX:center.x,labelY:Math.min(...points.map(p=>p.y))-68,outline:[],theme:graph.groups.find(g=>g.id===d.groups[0]).theme};
 });
 const districts=makeDistricts(candidate),previousDistricts=makeDistricts(previous);
 const districtOf=new Map(districts.flatMap(d=>d.nodes.map(id=>[id,d])));
 function setMode(next,force=false){
  if(!force&&used()>0&&!confirm('比較対象を切り替えると、このプレビューの配分をリセットします。切り替えますか？')){selector.value=mode;return false}
  const graph=next==='original'?original:next==='previous'?previous:candidate,display=next==='original'?originalDisplay:graph;
  DATA.edges=graph.edges;for(const [,list] of adj)list.length=0;for(const e of DATA.edges){adj.get(e.a).push(e.b);adj.get(e.b).push(e.a)}
  for(const key of ['nodes','edges','groups','bounds'])DISPLAY[key]=display[key];
  for(const map of [workshopFull.viewNodes,workshopFull.viewGroups,workshopFull.viewEdges])map.clear();for(const n of DISPLAY.nodes)workshopFull.viewNodes.set(n.id,n);for(const g of DISPLAY.groups)workshopFull.viewGroups.set(g.id,g);for(const e of DISPLAY.edges)workshopFull.viewEdges.set(ekey(e.a,e.b),e);
  mode=next;selector.value=next;treeTest.reset();drawOriginMedallion=()=>{};
  document.querySelector('.mapheading h1').textContent=next==='proposal'?'育てる領域を選び、その中で枝分かれする。':next==='previous'?'改善案2：領域をまとめた前案。':'原版：点在する領域と長い通路。';
  marker.textContent=next==='proposal'?'配線案3・領域のまとまりと主要経路を分離':next==='previous'?'保持した改善案2 / 配線の見づらさが残る比較用':'原版の配置と接続を保持';
  document.querySelector('.atelier-scope').textContent='採用前の比較・実戦効果は未反映';document.querySelector('.atelier-context').firstChild.textContent=next==='proposal'?'パッシブツリー / 配線案3':next==='previous'?'パッシブツリー / 前案':'パッシブツリー / 原版';toggle.textContent=next==='proposal'?'前案と比較':'配線案3へ';treeTest.fit();return true;
 }
 selector.onchange=()=>setMode(selector.value);toggle.onclick=()=>setMode(mode==='proposal'?'previous':'proposal');
 const clear=ctx.clearRect.bind(ctx);ctx.clearRect=(...args)=>{
  clear(...args);if(mode!=='proposal')return;ctx.save();
  for(const d of districts){if(d.boundary.length<3)continue;const selectedDistrict=districtOf.get(selected)?.id===d.id;ctx.beginPath();d.boundary.forEach((p,i)=>{const s=worldToScreen(p);if(i)ctx.lineTo(s.x,s.y);else ctx.moveTo(s.x,s.y)});ctx.closePath();ctx.fillStyle=selectedDistrict?'rgba(128,136,78,.07)':'rgba(95,117,76,.035)';ctx.fill();ctx.strokeStyle=selectedDistrict?'rgba(176,157,94,.38)':'rgba(133,153,105,.15)';ctx.lineWidth=.8}
  ctx.restore();
 };
 const stroke=strokeEdge;strokeEdge=(e,context,screen)=>{
  if(mode!=='proposal'||context!==ctx||!['#4d5e4b','#65745d'].includes(ctx.strokeStyle))return stroke(e,context,screen);
  const kind=workshopFull.viewEdges.get(ekey(e.a,e.b))?.proposalKind;ctx.save();ctx.strokeStyle=kind==='district-bridge'?'#879274':kind==='optional-keystone'?'#827451':kind==='regional'?'#42533f':'#5c7151';ctx.lineWidth=kind==='district-bridge'?1.15:kind==='regional'?.65:.85;stroke(e,context,screen);ctx.restore();
 };
 function captionGroups(){return cam.z<=.24?(mode==='proposal'?districts:mode==='previous'?previousDistricts:DISPLAY.groups):DISPLAY.groups}
 const side=renderSide;renderSide=()=>{side();const row=comparison.singleKeyCosts.find(r=>r.origin===origin&&r.key===selected);document.getElementById('structureCost').textContent=row?`構造費用：原版 ${row.original}pt / 前案 ${row.previous}pt / 配線案3 ${row.proposal}pt。入力条件は別に判定。`:'全体では領域のまとまりを表示。拡大すると各地域と道中の恩恵を確認できます。技の習得は別の仕組みです。'};
 api.setMode=setMode;api.getMode=()=>mode;api.previous=previous;
 window.structureV2={setMode,getMode:()=>mode,districts,captionGroups,districtOf,focusDistrict:id=>{const d=districts.find(d=>d.id===id),ps=d.nodes.map(id=>workshopFull.viewNodes.get(id)),xs=ps.map(p=>p.x),ys=ps.map(p=>p.y);cam={x:(Math.min(...xs)+Math.max(...xs))/2,y:(Math.min(...ys)+Math.max(...ys))/2,z:Math.min(.65,(w-180)/(Math.max(...xs)-Math.min(...xs)+500),(h-220)/(Math.max(...ys)-Math.min(...ys)+500))};selectNode(candidate.groups.find(g=>g.id===d.groups[0]).notables[0]);drawSoon()}};
 setMode('proposal',true);renderSide();
})();
