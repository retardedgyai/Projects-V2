/* Unadopted connection/layout comparison. Core rules and node effects unchanged. */
(()=>{
 const original=JSON.parse(document.getElementById('treeData').textContent);
 const oldDisplay=JSON.parse(document.getElementById('originalDisplayData').textContent);
 const candidate=JSON.parse(document.getElementById('structureGraphData').textContent);
 const comparison=JSON.parse(document.getElementById('structureComparisonData').textContent);
 const originalMedallion=drawOriginMedallion;
 let mode='proposal';
 const toggle=document.createElement('button');toggle.id='structureToggle';toggle.textContent='原版と比較';document.querySelector('.header .tools').append(toggle);
 const note=document.querySelector('.atelier-scope');
 const title=document.querySelector('.mapheading h1');
 const marker=document.createElement('div');marker.className='status warn';marker.style.marginBottom='12px';document.querySelector('.sidebar>.block').prepend(marker);
 function setMode(next,force=false){
  if(!force&&used()>0&&!confirm('比較する構造を切り替えると、このプレビューの配分をリセットします。切り替えますか？'))return false;
  const d=next==='original'?oldDisplay:candidate,graph=next==='original'?original:candidate;
  DATA.edges=graph.edges;
  for(const [id,list] of adj)list.length=0;
  for(const e of graph.edges){adj.get(e.a).push(e.b);adj.get(e.b).push(e.a)}
  for(const key of ['nodes','edges','groups','bounds'])DISPLAY[key]=d[key];
  for(const map of [workshopFull.viewNodes,workshopFull.viewGroups,workshopFull.viewEdges])map.clear();
  for(const n of DISPLAY.nodes)workshopFull.viewNodes.set(n.id,n);
  for(const g of DISPLAY.groups)workshopFull.viewGroups.set(g.id,g);
  for(const e of DISPLAY.edges)workshopFull.viewEdges.set(ekey(e.a,e.b),e);
  mode=next;treeTest.reset();drawOriginMedallion=mode==='original'?originalMedallion:()=>{};
  title.textContent=mode==='original'?'原版：点在する領域と長い通路。':'構造案：領域を近づけ、育て方を分岐させる。';
  marker.textContent=mode==='original'?'原版の配置・952接続を表示':'採用前の別案・配置と接続を変更';
  note.textContent=mode==='original'?'原版を保持・実戦未反映':'取得費用も変わる比較案・本番未採用';
  toggle.textContent=mode==='original'?'構造案を見る':'原版と比較';
  document.querySelector('.atelier-context').firstChild.textContent=mode==='original'?'パッシブツリー / 原版':'パッシブツリー / 構造案';
  treeTest.fit();return true;
 }
 toggle.onclick=()=>setMode(mode==='proposal'?'original':'proposal');
 const cost=document.createElement('details');cost.className='atelier-provenance';cost.innerHTML='<summary>原版との取得費用の比較</summary><p>配置だけでなく接続も変えた案です。Keyまでの費用や複数Keyの同時取得は原版より軽くなる場合があります。効果・係数・本番の獲得ポイントは未採用です。</p><div id="structureCost"></div>';document.getElementById('nodeDescription').after(cost);
 const side=renderSide;renderSide=()=>{side();const row=comparison.singleKeyCosts.find(r=>r.origin===origin&&r.key===selected);document.getElementById('structureCost').textContent=row?`構造だけの最短費用：原版 ${row.original}pt → 案 ${row.proposal}pt。装備・技能による入力条件は別に判定します。`:'技そのものの習得は別。Small / Notable / 任意のKeyで持つ力を育てます。'};
 window.structureProposal={setMode,getMode:()=>mode,original,candidate,comparison,focusRegion:id=>{const g=workshopFull.viewGroups.get(id);cam={x:g.x,y:g.y,z:Math.max(.115,Math.min(.65,(w-160)/1800,(h-220)/1500))};selectNode(g.notables[0]);drawSoon()}};
 setMode('proposal',true);renderSide();
})();
