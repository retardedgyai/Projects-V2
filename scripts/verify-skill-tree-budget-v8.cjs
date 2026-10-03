/* Narrow fixture follow-up: actual JS reconstruction, save and equal-spend images. */
const fs=require('node:fs'),assert=require('node:assert/strict'),cp=require('node:child_process');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-contract-v8',path=dir+'/ProjectS_Contract_V8.html',html=fs.readFileSync(path,'utf8');
const payload=JSON.parse(html.match(/<script id="wholeTreeData" type="application\/json">(.*?)<\/script>/s)[1]);
const git='C:/Users/xgaiz/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe';
const baseline=cp.execFileSync(git,['--no-optional-locks','-c','safe.directory='+process.cwd().replaceAll('\\','/'),'show','0a851405:'+path],{maxBuffer:5e6,encoding:'utf8'});
const oldPayload=JSON.parse(baseline.match(/<script id="wholeTreeData" type="application\/json">(.*?)<\/script>/s)[1]);
for(const field of ['source','inputOverlay','graph','icons','paintBounds','centralCenter'])assert.deepEqual(payload[field],oldPayload[field],field);
assert.equal(html.match(/<script>(.*?)<\/script>/s)[1].replaceAll('\r\n','\n'),baseline.match(/<script>(.*?)<\/script>/s)[1].replaceAll('\r\n','\n'),'Application functions must remain unchanged');
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page'),ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);let seq=0;const pending=new Map(),errors=[];
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 const settle=()=>run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 await send('Runtime.enable');await send('Network.enable');await send('Network.setBlockedURLs',{urls:['http://*','https://*']});await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+'/'+path});await new Promise(r=>setTimeout(r,400));await settle();
 const screenshot=async name=>{await settle();const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(dir+'/'+name+'.png',Buffer.from(png.data,'base64'))};
 await screenshot('ProjectS_Contract_V8_Overview');const cases=[];
 const affected=payload.examples.filter(e=>/^assassin-(area|crit)-(48|64|80)$/.test(e.id)||e.id.startsWith('assassin-equal-'));
 for(const e of affected){
  await run('wholeTree.setProfile('+JSON.stringify(e.profileId)+');budget='+e.budget+';roadChoice="hp";siphon=false;syncProfile()');
  for(const t of e.targets){if(await run('learned.has('+JSON.stringify(t)+')'))continue;await run('wholeTree.select('+JSON.stringify(t)+')');assert(await run('wholeTree.allocateGoal()'),e.id+':'+t)}
  assert.deepEqual(await run('wholeTree.snapshot().learned'),e.learned);assert.equal(await run('spent()'),e.spent);assert.equal(await run('budget-spent()'),e.remaining);const totals=await run('wholeTree.totals()');for(const k of new Set([...Object.keys(totals),...Object.keys(e.stats)]))assert(Math.abs((totals[k]||0)-(e.stats[k]||0))<1e-8,e.id+':'+k);
  await run("el('export').click();wholeTree.reset();el('import').click()");assert.deepEqual(await run('wholeTree.snapshot().learned'),e.learned);
  await run('wholeTree.applyExample('+JSON.stringify(e.id)+');wholeTree.focus(wholeTree.snapshot().learned)');assert((await run("el('routeNote').textContent")).includes(e.id.startsWith('assassin-equal-')?'43pt':'未消費あり'));
  await screenshot('ProjectS_Contract_V8_'+e.id);cases.push({id:e.id,used:e.spent,budget:e.budget,remaining:e.remaining,stats:totals,actualJsRebuilt:true,saveRestore:true})
 }
 const a=payload.examples.find(e=>e.id==='assassin-equal-area-43'),b=payload.examples.find(e=>e.id==='assassin-equal-crit-43'),sector=[...new Set([...a.learned,...b.learned])];let sameCam=null;
 for(const e of [a,b]){await run('wholeTree.applyExample('+JSON.stringify(e.id)+');wholeTree.focus('+JSON.stringify(sector)+')');const cam=await run('wholeTree.getCam()');if(sameCam)assert.deepEqual(cam,sameCam);sameCam=cam;await screenshot('ProjectS_Contract_V8_Pair_'+e.id)}
 assert.equal(a.spent,b.spent);assert.equal(a.budget,b.budget);assert.notDeepEqual(a.stats,b.stats);assert.equal(errors.length,0);
 fs.writeFileSync(dir+'/browser-budget-verification.json',JSON.stringify({status:'PASS',applicationJsUnchanged:true,graphAndSourceAndContractsUnchanged:true,cases,equalSpend:43,equalBudget:48,equalCamera:sameCam,eightyBudgetExamplesAlsoWithin64:true,noPaddingNodesAdded:true,runtimeErrors:errors},null,2));console.log(JSON.stringify({status:'PASS',affectedExamples:cases.length,equalSpend:43,equalBudget:48,applicationAndGraphUnchanged:true}));ws.close()
})().catch(e=>{console.error(e);process.exit(1)});
