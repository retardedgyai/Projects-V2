/* Import every usable example in the real HTML and compare effective sums. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-clear-wiring-v3';
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
 checked.push({version:b.version,budget:b.budget,strategy:b.strategy,spent:b.spent});
}
await run("structureProposal.setMode('proposal',true);treeTest.changeOrigin('warrior');largeTreeTest.setFixture('support','plain','standard',true);largeTreeTest.setBudget(64);treeTest.reset()");
const g24=await run("largeTreeTest.inputFor(treeTest.by.get('g24hub'))");assert.equal(Object.keys(g24.usable).length,0);
const result={status:'PASS',validImports:checked.length,unavailableExamples:report.builds.filter(b=>!b.buildCode).length,checked,g24hubEffective:g24};fs.writeFileSync(`${dir}/build-budget-browser-verification.json`,JSON.stringify(result,null,2));console.log(JSON.stringify({status:result.status,validImports:result.validImports,unavailableExamples:result.unavailableExamples,g24hubEffective:g24}));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
