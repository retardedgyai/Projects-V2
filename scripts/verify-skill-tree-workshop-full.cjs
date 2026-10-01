/* Full-map UI QA. Uses the dedicated local Chrome; no game or real save writes. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-full-v1';
const source=JSON.parse(fs.readFileSync('assets/core-ui/large-tree-preview/graph.json','utf8'));
const html=fs.readFileSync(`${dir}/ProjectS_Passive_Workshop_Full.html`,'utf8');
assert.deepEqual(JSON.parse(html.match(/<script id="treeData" type="application\/json">(.*?)<\/script>/s)[1]),source);
const view=JSON.parse(fs.readFileSync(`${dir}/display-layout.json`,'utf8'));
const without=(o,keys)=>Object.fromEntries(Object.entries(o).filter(([k])=>!keys.includes(k)));
assert.deepEqual(view.nodes.map(n=>without(n,['x','y'])),source.nodes.map(n=>without(n,['x','y'])));
assert.deepEqual(view.edges.map(n=>without(n,['points','crossingGaps'])),source.edges.map(n=>without(n,['points','crossingGaps'])));
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page');const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
 let sequence=0;const pending=new Map(),errors=[],requests=[];
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}if(m.method==='Runtime.executionContextsCleared')errors.length=0;if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails);if(m.method==='Network.requestWillBeSent')requests.push(m.params.request.url)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 const settle=()=>run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 await send('Runtime.enable');await send('Network.enable');await send('Network.setBlockedURLs',{urls:['http://*','https://*']});
 await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+`/${dir}/ProjectS_Passive_Workshop_Full.html`});
 await new Promise(r=>setTimeout(r,900));await settle();assert(await run('!!window.workshopFull'));
 await run(`(()=>{const realShape=shape,realEdge=strokeEdge,realDraw=draw;window.fullPaint={nodes:new Set(),edges:new Set()};shape=(x,y,r,t)=>{fullPaint.nodes.add(x.toFixed(4)+','+y.toFixed(4));return realShape(x,y,r,t)};strokeEdge=(e,c,s)=>{if(c===ctx)fullPaint.edges.add(ekey(e.a,e.b));return realEdge(e,c,s)};draw=()=>{fullPaint.nodes.clear();fullPaint.edges.clear();realDraw()}})()`);
 const frames=[];
 async function frame(name){await run("document.getElementById('toast').style.visibility='hidden'");await settle();const state=await run('workshopFull.snapshot()'),audit=await run('largeTreeTest.labelAudit()');
  const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(`${dir}/${name}.png`,Buffer.from(png.data,'base64'));
  const footprint=await run('({nodes:fullPaint.nodes.size,edges:fullPaint.edges.size})');frames.push({name,state,footprint,captions:audit.captions,labelPairs:audit.labelPairs,labelNodes:audit.labelNodes,nodePairs:audit.nodePairs,unplaced:audit.unplaced});
  console.log(JSON.stringify({frame:name,...footprint,labelPairs:audit.labelPairs.length,labelNodes:audit.labelNodes.length,nodePairs:audit.nodePairs.length,unplaced:audit.unplaced}));return frames.at(-1)}
 await run('treeTest.reset();workshopFull.focusStarts()');await frame('ProjectS_Workshop_Full_Five_Starts');
 await run('treeTest.fit()');const overview=await frame('ProjectS_Workshop_Full_Overview');assert.equal(overview.state.visibleNodes,645);assert.equal(overview.footprint.nodes,645);assert.equal(overview.footprint.edges,952);assert(overview.captions>=52);
 const roots=Object.fromEntries(overview.state.roots.map(n=>[n.origin,n]));assert.equal(roots.mage.x,0);for(const [a,b] of [['tank','ranger'],['warrior','assassin']]){assert.equal(roots[a].x,-roots[b].x);assert.equal(roots[a].y,roots[b].y)}
 for(const [a,b] of [['tank','ranger'],['warrior','assassin']]){const left=view.nodes.filter(n=>n.origin===a&&(n.type==='start'||n.opening)),right=view.nodes.filter(n=>n.origin===b&&(n.type==='start'||n.opening));assert.equal(left.length,10);assert.equal(right.length,10);for(const n of left)assert(right.some(m=>Math.hypot(m.x+n.x,m.y-n.y)<.003))}
 let costChecks=0;for(const row of source.budgetAudit.matrix){await run(`treeTest.reset();treeTest.changeOrigin('${row.origin}')`);for(const [id,expected] of Object.entries(row.costs)){const actual=await run(`largeTreeTest.structuralShortest('${id}').reduce((s,id)=>s+treeTest.by.get(id).cost,0)`);assert.equal(actual,expected);costChecks++}}assert.equal(costChecks,75);
 await run("treeTest.reset();treeTest.changeOrigin('warrior');largeTreeTest.setFixture('basic','plain','standard');treeTest.focusNode('opening_warrior_2_3');cam.z=.48;drawSoon()");
 const plans=await run('treeTest.getPlans().map(p=>({cost:p.cost,path:p.path,stats:largeTreeTest.usableSums(p.fresh)}))');assert.deepEqual(plans.map(p=>p.cost),[3,3]);assert.notDeepEqual(plans[0].stats,plans[1].stats);
 await frame('ProjectS_Workshop_Full_Route_A');await run('treeTest.setPlan(1)');await frame('ProjectS_Workshop_Full_Route_B');await run('treeTest.allocate()');assert.equal(await run('largeTreeTest.getUsed()'),3);await run('treeTest.reset()');assert.equal(await run('largeTreeTest.getUsed()'),0);
 await run("treeTest.focusNode('g7n4');cam.z=.47;drawSoon()");await frame('ProjectS_Workshop_Full_Far_Region');
 await run("treeTest.focusNode('g27n4');cam.z=.47;drawSoon();document.getElementById('toast').classList.remove('show')");await frame('ProjectS_Workshop_Full_Dense_Region');
 await run('treeTest.fit()');await settle();const rectangle=await run("(()=>{const r=canvas.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}})()");
 const before=await run('treeTest.getCam()');await send('Input.dispatchMouseEvent',{type:'mousePressed',x:rectangle.x+rectangle.w*.5,y:rectangle.y+rectangle.h*.6,button:'left',clickCount:1});await send('Input.dispatchMouseEvent',{type:'mouseMoved',x:rectangle.x+rectangle.w*.5+85,y:rectangle.y+rectangle.h*.6+55,button:'left',buttons:1});await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:rectangle.x+rectangle.w*.5+85,y:rectangle.y+rectangle.h*.6+55,button:'left',clickCount:1});assert.notDeepEqual(await run('treeTest.getCam()'),before);
 const z=await run('treeTest.getCam().z');await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:rectangle.x+rectangle.w*.5,y:rectangle.y+rectangle.h*.6,deltaX:0,deltaY:-220});await settle();assert((await run('treeTest.getCam().z'))>z);
 await run(`(()=>{const r=document.querySelector('.map-info').getBoundingClientRect(),tr=canvas.getBoundingClientRect(),n=workshopFull.viewNodes.get('g7n4'),x=r.x+r.width*.5-tr.x,y=r.y+r.height*.5-tr.y;cam={x:n.x-(x-w/2)/.45,y:n.y-(y-h/2)/.45,z:.45};drawSoon()})()`);await settle();
 const covered=await run("(()=>{const r=document.querySelector('.map-info').getBoundingClientRect();return {x:r.x+r.width*.5,y:r.y+r.height*.5,state:treeTest.getState(),selected,cam:treeTest.getCam()}})()");
 for(const button of ['left','right']){await send('Input.dispatchMouseEvent',{type:'mousePressed',x:covered.x,y:covered.y,button,clickCount:1});await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:covered.x,y:covered.y,button,clickCount:1})}
 await send('Input.dispatchMouseEvent',{type:'mouseWheel',x:covered.x,y:covered.y,deltaX:0,deltaY:-180});await settle();assert.deepEqual(await run('treeTest.getState()'),covered.state);assert.equal(await run('selected'),covered.selected);assert.deepEqual(await run('treeTest.getCam()'),covered.cam);
 await run("document.getElementById('search').value='会心';document.getElementById('search').dispatchEvent(new Event('input'));document.getElementById('nextResult').click()");assert(await run('searchMatches.length>0'));assert.equal(await run('largeTreeTest.getUsed()'),0);await run("document.getElementById('clearSearch').click()");
 for(const id of ['tank','mage','ranger','assassin']){await run(`treeTest.reset();document.querySelector('.atelier-classes [data-origin="${id}"]').click()`);assert.equal((await run('treeTest.getState()')).origin,id)}
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:2,mobile:true});await settle();await run("treeTest.reset();treeTest.changeOrigin('warrior');treeTest.home();document.getElementById('toast').classList.remove('show')");await frame('ProjectS_Workshop_Full_Mobile');
  assert(await run('document.querySelector("#zoomIn").getBoundingClientRect().height>=42'));assert(await run('document.documentElement.scrollWidth<=innerWidth'));
 const mobileToolbar=await run(`(()=>{const h=document.querySelector('.header').getBoundingClientRect(),buttons=[...document.querySelectorAll('.header .tools button')].map(b=>{const r=b.getBoundingClientRect();return {id:b.id,x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom}});return {header:{x:h.x,y:h.y,right:h.right,bottom:h.bottom},buttons}})()`);
 assert(mobileToolbar.buttons.length>=4);for(const b of mobileToolbar.buttons){assert(b.w>0&&b.h>=30);assert(b.x>=mobileToolbar.header.x&&b.right<=mobileToolbar.header.right&&b.y>=mobileToolbar.header.y&&b.bottom<=mobileToolbar.header.bottom,JSON.stringify(b))}for(let i=1;i<mobileToolbar.buttons.length;i++)assert(mobileToolbar.buttons[i-1].right<=mobileToolbar.buttons[i].x);
 const pinch=await run('treeTest.getCam().z');await run("(()=>{const v=canvas,r=v.getBoundingClientRect(),fire=(t,id,x)=>v.dispatchEvent(new PointerEvent(t,{pointerId:id,pointerType:'touch',clientX:r.x+x,clientY:r.y+260,bubbles:true}));v.setPointerCapture=()=>{};fire('pointerdown',701,80);fire('pointerdown',702,180);fire('pointermove',702,230);fire('pointerup',701,80);fire('pointerup',702,230)})()");assert((await run('treeTest.getCam().z'))>pinch);
 assert.equal(errors.length,0,JSON.stringify(errors));assert.equal(requests.filter(u=>/^https?:/.test(u)).length,0);
 for(const f of frames)assert.equal(f.labelPairs.length+f.labelNodes.length+f.nodePairs.length+f.unplaced.length,0,JSON.stringify({frame:f.name,...f}));
 const out={status:'PASS',fullGraphDrawn:{nodes:645,edges:952,groups:47},sameProgressionData:true,symmetricRoots:true,symmetricOpeningNodes:50,costComparisons:75,plans,allocationReset:true,dragWheelPinch:true,hiddenPanelNodeInputBlocked:true,mobileToolbarWithinHeader:true,search:true,classButtons:5,externalRequests:0,jsExceptions:0,frames,nativeMinecraft:false};
 fs.writeFileSync(`${dir}/browser-verification.json`,JSON.stringify(out,null,2));console.log(JSON.stringify({status:out.status,fullGraph:out.fullGraphDrawn,costComparisons:75,frames:frames.length}));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
