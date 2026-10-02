/* Real browser draw/input QA for the separate passive structure proposal. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-structure-v1';
const source=JSON.parse(fs.readFileSync('assets/core-ui/large-tree-preview/graph.json','utf8'));
const graph=JSON.parse(fs.readFileSync(`${dir}/candidate-graph.json`,'utf8'));
const costs=JSON.parse(fs.readFileSync(`${dir}/cost-comparison.json`,'utf8'));
const mirror=JSON.parse(fs.readFileSync(`${dir}/mirror-map.json`,'utf8'));
const html=fs.readFileSync(`${dir}/ProjectS_Passive_Structure_Comparison.html`,'utf8');
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
 await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+`/${dir}/ProjectS_Passive_Structure_Comparison.html`});await new Promise(r=>setTimeout(r,700));await settle();assert(await run('!!window.structureProposal'));
 await run(`(()=>{const realShape=shape,realEdge=strokeEdge,realDraw=draw;window.structurePaint={nodes:new Set(),edges:new Set()};shape=(x,y,r,t)=>{structurePaint.nodes.add(x.toFixed(4)+','+y.toFixed(4));return realShape(x,y,r,t)};strokeEdge=(e,c,s)=>{if(c===ctx)structurePaint.edges.add(ekey(e.a,e.b));return realEdge(e,c,s)};draw=()=>{structurePaint.nodes.clear();structurePaint.edges.clear();realDraw()}})()`);
 const frames=[];
 async function frame(name){await run("document.getElementById('toast').style.visibility='hidden'");await settle();const audit=await run('largeTreeTest.labelAudit()'),state=await run('workshopFull.snapshot()');
  const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(`${dir}/${name}.png`,Buffer.from(png.data,'base64'));
  const footprint=await run('({nodes:structurePaint.nodes.size,edges:structurePaint.edges.size})');
  const rect=await run("(()=>{const r=canvas.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}})()");
  const item={name,mode:await run('structureProposal.getMode()'),state,footprint,rect,captions:audit.captions,labelPairs:audit.labelPairs,labelNodes:audit.labelNodes,nodePairs:audit.nodePairs,unplaced:audit.unplaced};frames.push(item);console.log(JSON.stringify({frame:name,...footprint,captions:item.captions,labelPairs:item.labelPairs.length,labelNodes:item.labelNodes.length,nodePairs:item.nodePairs.length,unplaced:item.unplaced}));return item;
 }
 await run("structureProposal.setMode('original',true)");const old=await frame('ProjectS_Structure_Original_Overview');assert.equal(old.footprint.nodes,645);assert.equal(old.footprint.edges,952);
 await run("structureProposal.focusRegion('g35')");await frame('ProjectS_Structure_Original_Region');
 await run("structureProposal.setMode('proposal',true)");const full=await frame('ProjectS_Structure_Proposal_Overview');assert.equal(full.footprint.nodes,645);assert.equal(full.footprint.edges,graph.edges.length);assert(full.captions>=52);
 await run("treeTest.changeOrigin('warrior');largeTreeTest.setFixture('basic','plain','standard');structureProposal.focusRegion('g35')");await frame('ProjectS_Structure_Proposal_Region');
 const plans=await run('treeTest.getPlans().map(p=>({cost:p.cost,path:p.path,stats:largeTreeTest.usableSums(p.fresh)}))');assert(plans.length>=2);assert.notDeepEqual(plans[0].path,plans[1].path);assert.notDeepEqual(plans[0].stats,plans[1].stats);await run('treeTest.allocate()');assert.equal(await run('largeTreeTest.getUsed()'),plans[0].cost);await run('treeTest.reset()');assert.equal(await run('largeTreeTest.getUsed()'),0);
 await run('workshopFull.focusStarts()');await frame('ProjectS_Structure_Five_Starts');
 // Compare 75 original and 75 candidate costs against independent Python search.
 let checks=0;for(const mode of ['original','proposal']){await run(`structureProposal.setMode('${mode}',true)`);for(const row of costs.singleKeyCosts){await run(`treeTest.reset();treeTest.changeOrigin('${row.origin}')`);const actual=await run(`largeTreeTest.structuralShortest('${row.key}').reduce((s,id)=>s+treeTest.by.get(id).cost,0)`);assert.equal(actual,row[mode],JSON.stringify({mode,...row,actual}));checks++}}assert.equal(checks,150);
 await run("treeTest.reset();treeTest.changeOrigin('warrior');treeTest.fit()");await settle();const rect=await run("(()=>{const r=canvas.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}})()");const camera=await run('treeTest.getCam()');
 await send('Input.dispatchMouseEvent',{type:'mousePressed',x:rect.x+rect.w*.5,y:rect.y+rect.h*.6,button:'left',clickCount:1});await send('Input.dispatchMouseEvent',{type:'mouseMoved',x:rect.x+rect.w*.5+80,y:rect.y+rect.h*.6+50,button:'left',buttons:1});await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:rect.x+rect.w*.5+80,y:rect.y+rect.h*.6+50,button:'left',clickCount:1});assert.notDeepEqual(await run('treeTest.getCam()'),camera);
 const z=await run('treeTest.getCam().z');await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:rect.x+rect.w*.5,y:rect.y+rect.h*.6,deltaX:0,deltaY:-200});await settle();assert((await run('treeTest.getCam().z'))>z);
 await run(`(()=>{const r=document.querySelector('.map-info').getBoundingClientRect(),tr=canvas.getBoundingClientRect(),n=workshopFull.viewNodes.get('g35n1'),x=r.x+r.width*.5-tr.x,y=r.y+r.height*.5-tr.y;cam={x:n.x-(x-w/2)/.45,y:n.y-(y-h/2)/.45,z:.45};drawSoon()})()`);await settle();
 const covered=await run("(()=>{const r=document.querySelector('.map-info').getBoundingClientRect();return {x:r.x+r.width*.5,y:r.y+r.height*.5,state:treeTest.getState(),selected,cam:treeTest.getCam()}})()");for(const button of ['left','right']){await send('Input.dispatchMouseEvent',{type:'mousePressed',x:covered.x,y:covered.y,button,clickCount:1});await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:covered.x,y:covered.y,button,clickCount:1})}await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:covered.x,y:covered.y,deltaX:0,deltaY:-150});assert.deepEqual(await run('treeTest.getState()'),covered.state);assert.equal(await run('selected'),covered.selected);assert.deepEqual(await run('treeTest.getCam()'),covered.cam);
 await run("document.getElementById('search').value='会心';document.getElementById('search').dispatchEvent(new Event('input'));document.getElementById('nextResult').click()");assert(await run('searchMatches.length>0'));await run("document.getElementById('clearSearch').click()");
 for(const id of ['tank','mage','ranger','assassin']){await run(`treeTest.reset();document.querySelector('.atelier-classes [data-origin="${id}"]').click()`);assert.equal((await run('treeTest.getState()')).origin,id)}
 // Switching the user-facing comparison button must rebuild real path adjacency.
 await run("treeTest.reset();document.getElementById('structureToggle').click()");assert.equal(await run('DATA.edges.length'),952);await run("document.getElementById('structureToggle').click()");assert.equal(await run('DATA.edges.length'),graph.edges.length);
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:2,mobile:true});await settle();await run("treeTest.changeOrigin('warrior');treeTest.home()");await frame('ProjectS_Structure_Mobile');
 assert(await run('document.documentElement.scrollWidth<=innerWidth'));
 const toolbar=await run(`(()=>{const h=document.querySelector('.header').getBoundingClientRect();return [...document.querySelectorAll('.header .tools button')].every(b=>{const r=b.getBoundingClientRect();return r.width>0&&r.height>=30&&r.left>=h.left&&r.right<=h.right&&r.top>=h.top&&r.bottom<=h.bottom})})()`);assert(toolbar);
 const pinch=await run('treeTest.getCam().z');await run("(()=>{const v=canvas,r=v.getBoundingClientRect(),fire=(t,id,x)=>v.dispatchEvent(new PointerEvent(t,{pointerId:id,pointerType:'touch',clientX:r.x+x,clientY:r.y+260,bubbles:true}));v.setPointerCapture=()=>{};fire('pointerdown',701,80);fire('pointerdown',702,180);fire('pointermove',702,230);fire('pointerup',701,80);fire('pointerup',702,230)})()");assert((await run('treeTest.getCam().z'))>pinch);
 assert.equal(errors.length,0,JSON.stringify(errors));assert.equal(requests.filter(u=>/^https?:/.test(u)).length,0);
 for(const f of frames)assert.equal(f.labelPairs.length+f.labelNodes.length+f.nodePairs.length+f.unplaced.length,0,JSON.stringify({frame:f.name,...f}));
 const out={status:'PASS',all645EffectsPreserved:true,all645CoordinatesMirrored:true,candidateConnections:graph.edges.length,costChecks:checks,twoKeyCases:costs.twoKeyExactSteinerCosts.length,originalAndProposalToggle:true,usableRouteAllocationReset:true,plans,dragWheelPinch:true,coveredNodeInputBlocked:true,mobileToolbar:true,search:true,origins:5,externalRequests:0,jsExceptions:0,frames,nativeMinecraft:false};fs.writeFileSync(`${dir}/browser-verification.json`,JSON.stringify(out,null,2));console.log(JSON.stringify({status:'PASS',costChecks:checks,frames:frames.length,connections:graph.edges.length}));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
