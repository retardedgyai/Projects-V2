/* Read-only browser QA for the separate workshop proposal. No native/save writes. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-v1';
const graph=JSON.parse(fs.readFileSync('assets/core-ui/large-tree-preview/graph.json','utf8'));
const nodes=new Map(graph.nodes.map(n=>[n.id,n]));
function edgeNodeHits(s){const hits=[];for(const e of s.edges)for(const n of s.positions){if(n.id===e.a||n.id===e.b)continue;const type=nodes.get(n.id).type,r=type==='start'?26:type==='notable'?23*Math.SQRT2:17;for(let i=1;i<e.points.length;i++){const a=e.points[i-1],b=e.points[i],dx=b.x-a.x,dy=b.y-a.y,t=Math.max(0,Math.min(1,((n.x-a.x)*dx+(n.y-a.y)*dy)/(dx*dx+dy*dy)));if(Math.hypot(n.x-a.x-t*dx,n.y-a.y-t*dy)<r+2){hits.push({edge:[e.a,e.b],node:n.id});break}}}return hits}
const html=fs.readFileSync(`${dir}/ProjectS_Passive_Workshop_Proposal.html`,'utf8');
assert.deepEqual(JSON.parse(html.match(/<script id="treeData" type="application\/json">(.*?)<\/script>/s)[1]),graph);
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
 let id=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const i=++id;pending.set(i,{resolve,reject});ws.send(JSON.stringify({id:i,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 await send('Emulation.setDeviceMetricsOverride',{width:1600,height:1060,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+`/${dir}/ProjectS_Passive_Workshop_Proposal.html`});
 await new Promise(r=>setTimeout(r,500));await run('document.fonts.ready.then(()=>true)');
 await run(`(()=>{window.proposalLabels=[];const p=CanvasRenderingContext2D.prototype,f=p.fillText,c=p.clearRect;p.clearRect=function(...args){if(this.canvas.id==='p-canvas')proposalLabels=[];return c.apply(this,args)};p.fillText=function(text,x,y,...args){if(this.canvas.id==='p-canvas'){const m=this.measureText(text),left=this.textAlign==='center'?x-m.width/2:this.textAlign==='right'?x-m.width:x;proposalLabels.push({text,left,top:y-m.actualBoundingBoxAscent,right:left+m.width,bottom:y+m.actualBoundingBoxDescent})}return f.call(this,text,x,y,...args)}})()`);
 const rows=[];
 for(const origin of ['warrior','tank','mage','assassin','ranger']){
  await run(`workshopProposal.selectOrigin('${origin}');workshopProposal.setMode('route')`);
  const s=await run('workshopProposal.snapshot()');assert.equal(s.positions.length,10);assert.equal(s.used,0);
  const ids=new Set(s.positions.map(p=>p.id));const expected=graph.edges.filter(e=>ids.has(e.a)&&ids.has(e.b));
  assert.deepEqual(s.edges.map(e=>[e.a,e.b]),expected.map(e=>[e.a,e.b]));
  assert.deepEqual(s.plans.slice(0,2).map(p=>p.cost),[3,3]);assert.notDeepEqual(s.plans[0].stats,s.plans[1].stats);
  for(const p of s.plans.slice(0,2))for(let i=1;i<p.path.length;i++)assert(graph.edges.some(e=>e.a===p.path[i-1]&&e.b===p.path[i]||e.b===p.path[i-1]&&e.a===p.path[i]));
  await run("document.getElementById('p-allocate').click()");assert.equal(await run('workshopProposal.snapshot().used'),3);
  await run("document.getElementById('p-reset').click()");assert.equal(await run('workshopProposal.snapshot().used'),0);
  const bounds=await run(`(()=>{const a=document.querySelector('.p-side').getBoundingClientRect(),b=document.querySelector('.p-key-note').getBoundingClientRect(),c=document.querySelector('.p-primary').getBoundingClientRect();return {noteBottom:b.bottom,sideBottom:a.bottom,allocateBottom:c.bottom,canvasTop:document.querySelector('#p-canvas').getBoundingClientRect().top}})()`);
  assert(bounds.noteBottom<=bounds.sideBottom,`${origin}: key explanation clipped`);
  assert(bounds.allocateBottom<=bounds.sideBottom,`${origin}: allocate clipped`);
  const edgeNodeCollisions=edgeNodeHits(s);assert.equal(edgeNodeCollisions.length,0,JSON.stringify(edgeNodeCollisions));
  const labels=await run('proposalLabels');const labelNodeCollisions=[];for(const b of labels)for(const p of s.positions){const type=nodes.get(p.id).type,r=type==='start'?26:type==='notable'?23:17,x=Math.max(b.left,Math.min(p.x,b.right)),y=Math.max(b.top,Math.min(p.y,b.bottom));const hit=type==='notable'?b.right>p.x-r-2&&b.left<p.x+r+2&&b.bottom>p.y-r-2&&b.top<p.y+r+2:Math.hypot(p.x-x,p.y-y)<r+2;if(hit)labelNodeCollisions.push({text:b.text,node:p.id})}
  assert.equal(labelNodeCollisions.length,0,JSON.stringify(labelNodeCollisions));
  rows.push({origin,nodes:ids.size,actualEdges:expected.length,plans:s.plans.slice(0,2),allocationReset:true,edgeNodeCollisions,labelNodeCollisions,bounds});
 }
 await run("workshopProposal.selectOrigin('warrior');workshopProposal.setMode('origins')");
 const starts=await run('workshopProposal.snapshot()');assert.equal(starts.positions.length,5);assert.equal(starts.edges.length,0);
 const r=starts.rootPositions;assert.equal(r.mage[0],460);for(const [a,b] of [['tank','assassin'],['warrior','ranger']]){assert.equal(r[a][0]+r[b][0],920);assert.equal(r[a][1],r[b][1])}
 const out={status:'PASS',scope:'Five symmetric medallions and selected 10-node opening; not the full 645-node layout.',sameEmbeddedGraph:true,sourceNodes:graph.nodes.length,sourceEdges:graph.edges.length,starts:{count:5,connectionsShown:0,mirrorAxis:460,central:'mage',pairs:[['tank','assassin'],['warrior','ranger']]},origins:rows,nativeIntegration:false};
 fs.writeFileSync(`${dir}/verification.json`,JSON.stringify(out,null,2));console.log(JSON.stringify(out));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
