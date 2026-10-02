/* Real browser draw/input QA for the separate passive structure proposal. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-clear-wiring-v3';
const source=JSON.parse(fs.readFileSync('assets/core-ui/large-tree-preview/graph.json','utf8'));
const graph=JSON.parse(fs.readFileSync(`${dir}/candidate-graph.json`,'utf8'));
const costs=JSON.parse(fs.readFileSync(`${dir}/cost-comparison.json`,'utf8'));
const mirror=JSON.parse(fs.readFileSync(`${dir}/mirror-map.json`,'utf8'));
const html=fs.readFileSync(`${dir}/ProjectS_Passive_Clear_Wiring_V3.html`,'utf8');
assert.deepEqual(JSON.parse(html.match(/<script id="treeData" type="application\/json">(.*?)<\/script>/s)[1]),source);
const strip=n=>Object.fromEntries(Object.entries(n).filter(([k])=>!['x','y'].includes(k)));
assert.deepEqual(graph.nodes.map(strip),source.nodes.map(strip));
assert.equal(graph.nodes.length,645);assert.equal(graph.groups.length,47);
const by=new Map(graph.nodes.map(n=>[n.id,n]));
for(const [id,m] of Object.entries(mirror)){assert(by.get(id).x===-by.get(m).x);assert.equal(by.get(id).y,by.get(m).y)}
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
 let sequence=0;const pending=new Map(),errors=[],requests=[];
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}if(m.method==='Runtime.executionContextsCleared')errors.length=0;if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails);if(m.method==='Network.requestWillBeSent')requests.push(m.params.request.url)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 const settle=()=>run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 await send('Runtime.enable');await send('Network.enable');await send('Network.setBlockedURLs',{urls:['http://*','https://*']});
 await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+`/${dir}/ProjectS_Passive_Clear_Wiring_V3.html`});await new Promise(r=>setTimeout(r,700));await settle();assert(await run('!!window.structureProposal'));
 await run(`(()=>{const realShape=shape,realEdge=strokeEdge,realDraw=draw;window.structurePaint={nodes:new Set(),edges:new Set()};shape=(x,y,r,t)=>{structurePaint.nodes.add(x.toFixed(4)+','+y.toFixed(4));return realShape(x,y,r,t)};strokeEdge=(e,c,s)=>{if(c===ctx)structurePaint.edges.add(ekey(e.a,e.b));return realEdge(e,c,s)};draw=()=>{structurePaint.nodes.clear();structurePaint.edges.clear();realDraw()}})()`);
 const frames=[];
 async function frame(name){await run("document.getElementById('toast').style.visibility='hidden'");await settle();const audit=await run('largeTreeTest.labelAudit()'),state=await run('workshopFull.snapshot()');
  const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(`${dir}/${name}.png`,Buffer.from(png.data,'base64'));
  const footprint=await run('({nodes:structurePaint.nodes.size,edges:structurePaint.edges.size})');
  const rect=await run("(()=>{const r=canvas.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}})()");
  const item={name,mode:await run('structureProposal.getMode()'),state,footprint,rect,captions:audit.captions,labelPairs:audit.labelPairs,labelNodes:audit.labelNodes,nodePairs:audit.nodePairs,unplaced:audit.unplaced};frames.push(item);console.log(JSON.stringify({frame:name,...footprint,captions:item.captions,labelPairs:item.labelPairs.length,labelNodes:item.labelNodes.length,nodePairs:item.nodePairs.length,unplaced:item.unplaced}));return item;
 }
 await run("structureProposal.setMode('previous',true)");const old=await frame('ProjectS_Clear_Wiring_V3_Previous_Overview');assert.equal(old.footprint.nodes,645);assert.equal(old.footprint.edges,741);
 await run("structureProposal.focusRegion('g35')");await frame('ProjectS_Clear_Wiring_V3_Previous_Region');
 await run("structureProposal.setMode('proposal',true)");const full=await frame('ProjectS_Clear_Wiring_V3_Overview');assert.equal(full.footprint.nodes,645);assert.equal(full.footprint.edges,graph.edges.length);assert(full.captions>=20);
 await run("treeTest.changeOrigin('warrior');largeTreeTest.setFixture('basic','plain','standard');structureV2.focusDistrict('D_L');structureProposal.focusRegion('g35')");await frame('ProjectS_Clear_Wiring_V3_Region');
 const plans=await run('treeTest.getPlans().map(p=>({cost:p.cost,path:p.path,stats:largeTreeTest.usableSums(p.fresh)}))');assert(plans.length>=2);assert.notDeepEqual(plans[0].path,plans[1].path);assert.notDeepEqual(plans[0].stats,plans[1].stats);await run('treeTest.allocate()');assert.equal(await run('largeTreeTest.getUsed()'),plans[0].cost);await run('treeTest.reset()');assert.equal(await run('largeTreeTest.getUsed()'),0);
 await run("structureV2.focusDistrict('D_L')");await frame('ProjectS_Clear_Wiring_V3_District');
 await run('workshopFull.focusStarts()');await frame('ProjectS_Clear_Wiring_V3_Five_Starts');
 // Compare all 225 original/V2/V3 single-Key structural costs.
 let checks=0;for(const mode of ['original','previous','proposal']){await run(`structureProposal.setMode('${mode}',true)`);for(const row of costs.singleKeyCosts){await run(`treeTest.reset();treeTest.changeOrigin('${row.origin}')`);const actual=await run(`largeTreeTest.structuralShortest('${row.key}').reduce((s,id)=>s+treeTest.by.get(id).cost,0)`);assert.equal(actual,row[mode],JSON.stringify({mode,...row,actual}));checks++}}assert.equal(checks,225);
 await run("treeTest.reset();treeTest.changeOrigin('warrior');treeTest.fit()");await settle();const rect=await run("(()=>{const r=canvas.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}})()");const camera=await run('treeTest.getCam()');
 await send('Input.dispatchMouseEvent',{type:'mousePressed',x:rect.x+rect.w*.5,y:rect.y+rect.h*.6,button:'left',clickCount:1});await send('Input.dispatchMouseEvent',{type:'mouseMoved',x:rect.x+rect.w*.5+80,y:rect.y+rect.h*.6+50,button:'left',buttons:1});await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:rect.x+rect.w*.5+80,y:rect.y+rect.h*.6+50,button:'left',clickCount:1});assert.notDeepEqual(await run('treeTest.getCam()'),camera);
 const z=await run('treeTest.getCam().z');await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:rect.x+rect.w*.5,y:rect.y+rect.h*.6,deltaX:0,deltaY:-200});await settle();assert((await run('treeTest.getCam().z'))>z);
 await run(`(()=>{const r=document.querySelector('.map-info').getBoundingClientRect(),tr=canvas.getBoundingClientRect(),n=workshopFull.viewNodes.get('g35n1'),x=r.x+r.width*.5-tr.x,y=r.y+r.height*.5-tr.y;cam={x:n.x-(x-w/2)/.45,y:n.y-(y-h/2)/.45,z:.45};drawSoon()})()`);await settle();
 const covered=await run("(()=>{const r=document.querySelector('.map-info').getBoundingClientRect();return {x:r.x+r.width*.5,y:r.y+r.height*.5,state:treeTest.getState(),selected,cam:treeTest.getCam()}})()");for(const button of ['left','right']){await send('Input.dispatchMouseEvent',{type:'mousePressed',x:covered.x,y:covered.y,button,clickCount:1});await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:covered.x,y:covered.y,button,clickCount:1})}await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:covered.x,y:covered.y,deltaX:0,deltaY:-150});assert.deepEqual(await run('treeTest.getState()'),covered.state);assert.equal(await run('selected'),covered.selected);assert.deepEqual(await run('treeTest.getCam()'),covered.cam);
 await run("document.getElementById('search').value='会心';document.getElementById('search').dispatchEvent(new Event('input'));document.getElementById('nextResult').click()");assert(await run('searchMatches.length>0'));await run("document.getElementById('clearSearch').click()");
 for(const id of ['tank','mage','ranger','assassin']){await run(`treeTest.reset();document.querySelector('.atelier-classes [data-origin="${id}"]').click()`);assert.equal((await run('treeTest.getState()')).origin,id)}
 // Switching the user-facing comparison button must rebuild real path adjacency.
 await run("treeTest.reset();document.getElementById('structureToggle').click()");assert.equal(await run('DATA.edges.length'),741);await run("document.getElementById('structureToggle').click()");assert.equal(await run('DATA.edges.length'),graph.edges.length);
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:2,mobile:true});await settle();await run("treeTest.changeOrigin('warrior');treeTest.home()");await frame('ProjectS_Clear_Wiring_V3_Mobile');
 assert(await run('document.documentElement.scrollWidth<=innerWidth'));
 const toolbar=await run(`(()=>{const h=document.querySelector('.header').getBoundingClientRect();return [...document.querySelectorAll('.header .tools button')].every(b=>{const r=b.getBoundingClientRect();return r.width>0&&r.height>=30&&r.left>=h.left&&r.right<=h.right&&r.top>=h.top&&r.bottom<=h.bottom})})()`);assert(toolbar);
 const pinch=await run('treeTest.getCam().z');await run("(()=>{const v=canvas,r=v.getBoundingClientRect(),fire=(t,id,x)=>v.dispatchEvent(new PointerEvent(t,{pointerId:id,pointerType:'touch',clientX:r.x+x,clientY:r.y+260,bubbles:true}));v.setPointerCapture=()=>{};fire('pointerdown',701,80);fire('pointerdown',702,180);fire('pointermove',702,230);fire('pointerup',701,80);fire('pointerup',702,230)})()");assert((await run('treeTest.getCam().z'))>pinch);
 assert.equal(errors.length,0,JSON.stringify(errors));assert.equal(requests.filter(u=>/^https?:/.test(u)).length,0);
 for(const f of frames.filter(f=>f.mode==='proposal'))assert.equal(f.labelPairs.length+f.labelNodes.length+f.nodePairs.length+f.unplaced.length,0,JSON.stringify({frame:f.name,...f}));

 // Include actual line width, glyph outlines, every possible 2px selection halo,
 // label backplates and complete unsplit wire geometry. Never ignore a masked line.
 await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});
 await run("structureProposal.setMode('proposal',true);treeTest.changeOrigin('warrior');largeTreeTest.setFixture('basic','plain','standard');treeTest.fit()");await settle();
 const strict=[];
 async function strictAudit(name){await settle();const data=await run(`(()=>{
  const labels=largeTreeTest.labelAudit(),ns=DISPLAY.nodes.map(n=>{const p=worldToScreen(n),glyph=displayRadius(n)*(n.type==='notable'?1.25:n.type==='keystone'?1.19:1),stroke=Math.max(1,(n.type==='notable'||n.type==='keystone'?4:2.3)*cam.z/2);return {id:n.id,x:p.x,y:p.y,r:glyph+2*(n.type==='notable'?1.25:n.type==='keystone'?1.19:1)+stroke,hit:Math.max(n.type==='keystone'?13:n.type==='notable'?7:5,BASE_RADIUS[n.type]*cam.z+4)}}),near=[],hits=[];let minimum=Infinity,minHit=Infinity;
  for(const e of DISPLAY.edges){const ps=e.points.map(([x,y])=>worldToScreen({x,y})),half=Math.max(1.65,cam.z*5)/2;for(let i=1;i<ps.length;i++){const a=ps[i-1],b=ps[i],dx=b.x-a.x,dy=b.y-a.y;for(const n of ns){if(n.id===e.a||n.id===e.b)continue;const t=Math.max(0,Math.min(1,((n.x-a.x)*dx+(n.y-a.y)*dy)/(dx*dx+dy*dy))),d=Math.hypot(n.x-a.x-t*dx,n.y-a.y-t*dy),gap=d-n.r-half,hitGap=d-n.hit-half;minimum=Math.min(minimum,gap);minHit=Math.min(minHit,hitGap);if(gap<2.95)near.push({edge:[e.a,e.b],node:n.id,gap:Number(gap.toFixed(3))});if(hitGap<0)hits.push({edge:[e.a,e.b],node:n.id,gap:hitGap})}}}
  return {zoom:cam.z,minimumGlyphGap:minimum,minimumHitGap:minHit,nearNodes:near,hitAreaCrossings:hits,labelLines:labels.labelLines,backplates:labels.captionBackplateIntersections,labelPairs:labels.labelPairs,labelNodes:labels.labelNodes,unplaced:labels.unplaced,occludedRootCaptions:window.clearWiringOccludedCaptions};})()`);
  strict.push({name,...data});if(name==='overview'||data.nearNodes.length||data.hitAreaCrossings.length||data.labelLines.length||data.unplaced.length)console.log(JSON.stringify({strict:name,zoom:data.zoom,minGap:data.minimumGlyphGap,near:data.nearNodes.length,hits:data.hitAreaCrossings.length,labels:data.labelLines.length,backplates:data.backplates.length,unplaced:data.unplaced}));return data;
 }
 await strictAudit('overview');
 for(const group of graph.groups){await run(`structureProposal.focusRegion('${group.id}')`);await strictAudit(group.id)}
 for(const z of [.11,.19,.3,.65,1.2,1.7]){await run(`cam={x:0,y:0,z:${z}};drawSoon()`);await strictAudit('zoom-'+z)}
 // Actual wheel clamping and narrow-screen overview preserve readable clearance.
 await run('treeTest.fit()');await settle();const safeRect=await run("(()=>{const r=canvas.getBoundingClientRect();return {x:r.x+r.width*.5,y:r.y+r.height*.6}})()");
 await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:safeRect.x,y:safeRect.y,deltaX:0,deltaY:10000});await settle();assert.equal(await run('treeTest.getCam().z'),.105);await strictAudit('minimum-wheel-zoom');
 for(const target of ['g7','g20']){await run(`structureProposal.focusRegion('${target}')`);await frame('ProjectS_Clear_Wiring_V3_'+(target==='g7'?'Problem_Region':'Key02_Region'))}
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:2,mobile:true});await settle();await run('treeTest.fit()');await strictAudit('mobile-fit');await frame('ProjectS_Clear_Wiring_V3_Mobile_Overview');
 for(const group of graph.groups){await run(`structureProposal.focusRegion('${group.id}')`);await strictAudit('mobile-'+group.id)}
 await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});await settle();
 const geometry=JSON.parse(fs.readFileSync(`${dir}/layout-verification.json`,'utf8'));assert.equal(geometry.nonVertexCrossingsMarkedWithGaps,0);assert(graph.edges.every(e=>e.crossingGaps.length===0));
 const bad=strict.filter(x=>x.nearNodes.length||x.hitAreaCrossings.length||x.labelLines.length||x.backplates.length||x.labelPairs.length||x.labelNodes.length||x.unplaced.length);
 fs.writeFileSync(`${dir}/strict-visual-verification.json`,JSON.stringify({status:bad.length?'FIX-FIRST':'PASS',checks:strict.length,actualWidthAndSelectionHalo:true,maskedLinesExcluded:false,crossingGapsUsed:false,geometry,results:strict},null,2));
 assert.equal(bad.length,0,JSON.stringify(bad.map(x=>({name:x.name,near:x.nearNodes.slice(0,4),labels:x.labelLines,unplaced:x.unplaced}))));

 const out={status:'PASS',all645EffectsPreserved:true,all645CoordinatesMirrored:true,candidateConnections:graph.edges.length,costChecks:checks,twoKeyCases:costs.twoKeyExactSteinerCosts.length,originalAndProposalToggle:true,usableRouteAllocationReset:true,plans,dragWheelPinch:true,coveredNodeInputBlocked:true,mobileToolbar:true,search:true,origins:5,externalRequests:0,jsExceptions:0,frames,nativeMinecraft:false};fs.writeFileSync(`${dir}/browser-verification.json`,JSON.stringify(out,null,2));console.log(JSON.stringify({status:'PASS',costChecks:checks,frames:frames.length,connections:graph.edges.length}));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
