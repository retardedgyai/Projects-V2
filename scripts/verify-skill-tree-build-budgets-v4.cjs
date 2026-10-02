/* Import every usable example in the real HTML and compare effective sums. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-clear-wiring-v4';
const report=JSON.parse(fs.readFileSync(`${dir}/build-budget-comparison.json`,'utf8'));
(async()=>{
const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page');
const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);let seq=0;const pending=new Map();
ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}};
const run=expression=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve:r=>{assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));resolve(r.result.value)},reject});ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,returnByValue:true}}))});
const checked=[];
for(const b of report.builds.filter(b=>b.buildCode)){
 await run(`structureProposal.setMode(${JSON.stringify(b.version)},true);treeTest.changeOrigin('warrior');largeTreeTest.setFixture('support','plain','standard',true);largeTreeTest.setBudget(${b.budget});treeTest.importData(${JSON.stringify(b.buildCode)})`);
 const actual=await run('({used:largeTreeTest.getUsed(),stats:largeTreeTest.usableSums([...treeTest.getState().learned])})');assert.equal(actual.used,b.spent);
 for(const k of new Set([...Object.keys(actual.stats),...Object.keys(b.stats)]))assert(Math.abs((actual.stats[k]||0)-(b.stats[k]||0))<.00001,`${b.version}/${b.budget}/${b.strategy}/${k}: ${actual.stats[k]} != ${b.stats[k]}`);
 const inactive=await run("[...treeTest.getState().learned].filter(id=>!['start','keystone'].includes(treeTest.by.get(id).type)&&Object.keys(largeTreeTest.inputFor(treeTest.by.get(id)).usable).length===0)");assert.equal(inactive.length,0,JSON.stringify(inactive));
 checked.push({version:b.version,budget:b.budget,strategy:b.strategy,spent:b.spent,inactiveNodes:inactive});
}
const examples=[];
for(const budget of [48,64,80])for(const strategy of ['weapon','survival','two_key']){
 await run(`treeTest.reset();largeTreeTest.setBudget(${budget});document.querySelector('[data-build-example="${strategy}"]').click()`);
 const sample=report.builds.find(b=>b.version==='proposal'&&b.budget===budget&&b.strategy===strategy);
 const actual=await run('({state:treeTest.getState(),used:largeTreeTest.getUsed(),profile:largeTreeTest.getProfile().id,summary:document.getElementById("buildExampleSummary").textContent})');assert.equal(actual.used,budget);assert.equal(actual.profile,'warrior:support:plain:standard');assert.deepEqual([...actual.state.learned].sort(),sample.buildCode.learned);assert(actual.summary.includes(budget+'pt'));
 await run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 const audit=await run(`(()=>{const labels=largeTreeTest.labelAudit(),nodes=DISPLAY.nodes.map(n=>{const p=worldToScreen(n),factor=n.type==='notable'?1.25:n.type==='keystone'?1.19:1;return {id:n.id,x:p.x,y:p.y,r:(displayRadius(n)+2)*factor+Math.max(1,(n.type==='notable'||n.type==='keystone'?4:2.3)*cam.z/2),hit:Math.max(n.type==='keystone'?13:n.type==='notable'?7:5,BASE_RADIUS[n.type]*cam.z+4)}});let min=Infinity,minHit=Infinity;for(const e of DISPLAY.edges){const ps=e.points.map(([x,y])=>worldToScreen({x,y})),half=Math.max(1.65,cam.z*5)/2;for(let i=1;i<ps.length;i++){const a=ps[i-1],b=ps[i],dx=b.x-a.x,dy=b.y-a.y;for(const n of nodes){if(n.id===e.a||n.id===e.b)continue;const t=Math.max(0,Math.min(1,((n.x-a.x)*dx+(n.y-a.y)*dy)/(dx*dx+dy*dy))),d=Math.hypot(n.x-a.x-t*dx,n.y-a.y-t*dy);min=Math.min(min,d-n.r-half);minHit=Math.min(minHit,d-n.hit-half)}}}return {minimumGlyphGap:min,minimumHitGap:minHit,labelFaults:labels.labelPairs.length+labels.labelNodes.length+labels.labelLines.length+labels.paintedLabelLines.length+labels.unplaced.length,inactive:[...treeTest.getState().learned].filter(id=>!['start','keystone'].includes(treeTest.by.get(id).type)&&Object.keys(largeTreeTest.inputFor(treeTest.by.get(id)).usable).length===0),keyInputPresent:[...treeTest.getState().learned].filter(id=>treeTest.by.get(id).type==='keystone').every(id=>largeTreeTest.inputFor(treeTest.by.get(id)).present),magicFlagPresent:largeTreeTest.getProfile().flags.includes('AP')};})()`);
 assert(audit.minimumGlyphGap>=2.95);assert(audit.minimumHitGap>=0);assert.equal(audit.labelFaults,0);assert.equal(audit.inactive.length,0);assert(audit.keyInputPresent);assert.equal(audit.magicFlagPresent,false);
 examples.push({budget,strategy,spent:actual.used,...audit});
 if(budget===64){await run("document.getElementById('toast').style.visibility='hidden'");await run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');const id=++seq,png=await new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method:'Page.captureScreenshot',params:{format:'png',captureBeyondViewport:false}}))});fs.writeFileSync(`${dir}/ProjectS_Clear_Wiring_V4_Build_${strategy}.png`,Buffer.from(png.data,'base64'))}
}
await run('treeTest.reset()');assert((await run('document.getElementById("buildExampleSummary").textContent')).includes('変更中'));
await run("structureProposal.setMode('proposal',true);treeTest.changeOrigin('warrior');largeTreeTest.setFixture('support','plain','standard',true);largeTreeTest.setBudget(64);treeTest.reset()");
const g24=await run("largeTreeTest.inputFor(treeTest.by.get('g24hub'))");assert.equal(Object.keys(g24.usable).length,0); // It remains optional, preserving the specialist condition.
const result={status:'PASS',validImports:checked.length,unavailableExamples:report.builds.filter(b=>!b.buildCode).map(b=>({version:b.version,budget:b.budget,strategy:b.strategy})),checked,actualExampleButtonCases:examples,staleSummaryClearedAfterReset:true,g24hubEffective:g24};fs.writeFileSync(`${dir}/build-budget-browser-verification.json`,JSON.stringify(result,null,2));console.log(JSON.stringify({status:result.status,validImports:result.validImports,actualExampleButtonCases:examples.length,unavailablePreviousExamples:result.unavailableExamples.length,g24hubEffective:g24}));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
