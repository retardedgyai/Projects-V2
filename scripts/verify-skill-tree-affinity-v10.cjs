/* Actual local browser comparison; no fixture commit or game integration. */
const fs=require('node:fs'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-affinity-v10';
const base='assets/core-ui/large-tree-preview/proposals/workshop-specialization-v9';
const candidate=dir+'/ProjectS_Affinity_V10.html',baseline=base+'/ProjectS_Specialization_V9.html';
const extract=s=>JSON.parse(s.match(/<script id="wholeTreeData" type="application\/json">(.*?)<\/script>/s)[1]);
const html=fs.readFileSync(candidate,'utf8'),P=extract(html),old=extract(fs.readFileSync(baseline,'utf8'));
assert.deepEqual(P,old,'V9 payload, geometry, rules, font references and sprites exact');
const fonts=s=>s.match(/@font-face\{[^}]+\}/g);
assert.deepEqual(fonts(html),fonts(fs.readFileSync(baseline,'utf8')),'approved font bytes exact');
const fixtures=JSON.parse(fs.readFileSync(base+'/cost-atlas-fixtures.json','utf8'));
const opus='C:/Users/xgaiz/Documents/Codex/2026-10-04/task-4/candidates/ui-opus55-2bb983e8-c45d-4250-b962-bc2b41d35642/output/tree/index.html';
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18130/json/list')).json()).find(t=>t.type==='page');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
 let seq=0;const pending=new Map(),errors=[];
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 const settle=()=>run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 const captures=[];
 const shot=async name=>{await run('document.querySelector(".toast")?.setAttribute("style","display:none")');await settle();const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(dir+'/'+name+'.png',Buffer.from(png.data,'base64'));captures.push(name+'.png')};
 const open=async path=>{await send('Page.navigate',{url:'file:///'+path.replaceAll('\\','/')});await new Promise(r=>setTimeout(r,350));await settle()};
 try{
  await send('Runtime.enable');await send('Network.enable');await send('Network.setBlockedURLs',{urls:['http://*','https://*']});
  await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});
  await open(opus);assert(await run('!!window.PSTree?.ok'));
  const opusData=await run('({nodes:PSTree.graph().nodes.length,edges:PSTree.graph().edges.length,groups:PSTree.graph().groups.length,costs:{warrior:PSTree.costFrom("warrior","v9_tank_0_5"),tank:PSTree.costFrom("tank","v9_tank_0_5")},sprites:PSTree.sprites(),affinity:[...PSTree.affinity()].filter(([id,a])=>a.best).length})');
  const opusExample=await run('(()=>{const i=PSTree.examples.findIndex(e=>e.id==="tank-direction-0-12");const result=PSTree.applyExample(i);PSTree.select("v9_tank_0_5");PSTree.focusBuild();return {index:i,result,state:PSTree.state()}})()');assert(opusExample.result.ok);assert.equal(opusExample.state.job,'tank');assert.equal(opusExample.state.spent,12);await shot('Inspected_Opus_Tank_12pt');
  await open(process.cwd()+'/'+baseline);await run('wholeTree.applyExample("tank-direction-0-12");wholeTree.specializationFocus("tank")');await shot('Inspected_V9_Tank_12pt');
  await open(process.cwd()+'/'+candidate);
  const atlases={};for(const mode of ['fixed','owned']){atlases[mode]=await run('wholeTree.cachedAtlas('+JSON.stringify(mode)+')');assert.deepEqual(atlases[mode],fixtures[mode],mode+' cost atlas preserved')}
  const affinities=await run('G.nodes.filter(n=>n.type!="start"&&n.type!="keystone").map(n=>wholeTree.targetAffinity(n.id))');
  for(const a of affinities){const expected=Object.fromEntries(Object.keys(fixtures.fixed.current).map(j=>[j,fixtures.fixed.current[j][a.id]??null]));assert.deepEqual(a.costs,expected,a.id);const finite=Object.values(expected).filter(Number.isFinite),min=finite.length?Math.min(...finite):null;assert.equal(a.min,min);assert.deepEqual(a.winners,Object.keys(expected).filter(j=>min!==null&&expected[j]===min))}
  const audit=await run('wholeTree.sameTargetAudit()');const ids=P.graph.nodes.filter(n=>n.type==='notable'&&!n.opening&&!P.directions.some(d=>d.nodeIds.includes(n.id))&&Object.keys(fixtures.fixed.current).every(j=>Number.isFinite(fixtures.fixed.current[j][n.id])&&Number.isFinite(fixtures.fixed.previous[j][n.id]))).map(n=>n.id).sort();assert.deepEqual(audit.ids,ids);
  for(const a of Object.keys(audit.pairs))for(const b of Object.keys(audit.pairs)){const counts={a:0,b:0,tie:0};for(const id of ids){const ca=fixtures.fixed.current[a][id],cb=fixtures.fixed.current[b][id];counts[ca<cb?'a':cb<ca?'b':'tie']++}assert.deepEqual(audit.pairs[a][b],counts)}
  const cases=[];for(const id of ['warrior-direction-0-12','tank-direction-0-12']){
   const e=P.examples.find(e=>e.id===id);await run('wholeTree.setProfile('+JSON.stringify(e.profileId)+')');
   for(const goal of e.targets){assert(await run('wholeTree.select('+JSON.stringify(goal)+');wholeTree.allocateGoal()'),goal)}
   assert.deepEqual(await run('wholeTree.snapshot().learned'),e.learned);assert.deepEqual(await run('wholeTree.totals()'),e.stats);
   const state=await run('wholeTree.snapshot()');await run('el("affinityToggle").click()');assert.deepEqual(await run('wholeTree.snapshot()'),state,'overlay cannot spend');
   assert(await run('el("affinityToggle").getAttribute("aria-pressed")==="true"&&!el("affinityLegend").hidden'));
   await run('el("affinityToggle").click()');await run('wholeTree.reset();wholeTree.restore('+JSON.stringify(state)+')');assert.deepEqual(await run('wholeTree.snapshot()'),state,'V9 code compatible');
   await run('wholeTree.applyExample('+JSON.stringify(id)+');wholeTree.specializationFocus('+JSON.stringify(e.profileId.split(':')[0])+')');
   const paint=await run('wholeTree.audit()');assert(paint.connected&&paint.usable);assert(paint.minGlyphGap>=0&&paint.minClickGap>=0);assert.equal(paint.labelLines.length,0);assert.equal(paint.centralLabelIntrusions.length,0);
   cases.push({id,spent:e.spent,stats:e.stats,paint});await shot('V10_'+id);
  }
  await run('wholeTree.select("g6n0")');assert.deepEqual(await run('wholeTree.targetAffinity("g6n0").costs'),Object.fromEntries(Object.keys(fixtures.fixed.current).map(j=>[j,fixtures.fixed.current[j].g6n0])));
  assert.equal(await run('wholeTree.targetAffinity("g6n0").costs.warrior'),30);assert.equal(await run('wholeTree.targetAffinity("g6n0").costs.tank'),31);
  await run('wholeTree.select("key_02")');assert.equal(await run('wholeTree.targetAffinity("key_02")'),null);assert.equal(await run('el("targetCosts").querySelectorAll(".start-price").length'),0);
  await run('wholeTree.select("origin_templar")');assert.equal(await run('el("targetCosts").querySelectorAll(".start-price").length'),0);
  await run('wholeTree.select("v9_tank_0_5");el("affinityToggle").click();el("center").click()');await shot('V10_Affinity_Overview');
  await run('el("compareStarts").click();el("sameTargetAudit").open=true;el("sameTargetAudit").scrollIntoView({block:"start"})');assert.equal(await run('el("sameTargetMatrix").querySelectorAll("tbody tr").length'),5);await shot('V10_Same_Target_Audit');
  const fixedBefore=await run('wholeTree.targetAffinity("v9_tank_0_5")');await run('el("compareMode").value="owned";wholeTree.renderComparison()');assert.deepEqual(await run('wholeTree.targetAffinity("v9_tank_0_5")'),fixedBefore,'fixed audit stays fixed');await run('el("closeComparison").click()');
  await send('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});await run('wholeTree.applyExample("tank-direction-0-12");wholeTree.specializationFocus("tank");wholeTree.select("v9_tank_0_5")');await settle();assert(await run('document.documentElement.scrollWidth<=innerWidth'));await shot('V10_Mobile_Map');
  await run('el("targetCosts").scrollIntoView({block:"center"})');assert(await run('el("targetCosts").scrollWidth<=el("targetCosts").clientWidth'));await shot('V10_Mobile_Target');
  await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await run('document.querySelector("aside").scrollTop=0;wholeTree.specializationFocus("tank")');await settle();assert(await run('document.documentElement.scrollWidth<=innerWidth'));await shot('V10_Tank_1440');
  const sourceReviewedAt=new Date().toISOString();assert.equal(errors.length,0);
  const report={status:'PASS',reviewedAt:sourceReviewedAt,candidateSha256:crypto.createHash('sha256').update(html).digest('hex'),v9PayloadExact:true,approvedFontsExact:true,graphUnchanged:true,fixturesMatchBothModes:true,ordinaryTargetCostsVerified:affinities.length,sameTargetAudit:audit,two12PointCases:cases,overlayDoesNotAllocate:true,v9CodesCompatible:true,keyAndStartExcluded:true,legacyArmorCosts:{warrior:30,tank:31},mobileNoHorizontalOverflow:true,desktop1440NoHorizontalOverflow:true,opusReadOnlyInspection:opusData,opusSameConditionExample:opusExample,captures,runtimeErrors:errors};
  fs.writeFileSync(dir+'/browser-verification.json',JSON.stringify(report,null,2));const manifest=JSON.parse(fs.readFileSync(dir+'/proposal-manifest.json','utf8'));manifest.status='TECHNICAL_PASS_UNADOPTED_HTML';manifest.verification='browser-verification.json';fs.writeFileSync(dir+'/proposal-manifest.json',JSON.stringify(manifest,null,2));
  console.log(JSON.stringify({status:report.status,ordinaryTargets:affinities.length,outerTargets:ids.length,idHash:audit.idHash,pairWarriorTank:audit.pairs.warrior.tank,cases:cases.length,captures:captures.length,runtimeErrors:errors.length,opusData}));
 }finally{await send('Browser.close').catch(()=>{});ws.close()}
})().catch(e=>{console.error(e);process.exit(1)});
