/* Fixed-camera comparisons and actual contract detail, without tree repaint. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-contract-v8',old='assets/core-ui/large-tree-preview/proposals/workshop-middle-choice-v7';
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page'),ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);let seq=0;const pending=new Map(),errors=[];
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 const settle=()=>run('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 await send('Runtime.enable');await send('Network.enable');await send('Network.setBlockedURLs',{urls:['http://*','https://*']});await send('Emulation.setDeviceMetricsOverride',{width:1920,height:1200,deviceScaleFactor:1,mobile:false});
 const prior=fs.readFileSync(__dirname+'/verify-skill-tree-poe2-middle-v6.cjs','utf8');const start=prior.indexOf('const inject=')+'const inject='.length,end=prior.indexOf('\nsuite=suite.replace');const inject=Function('return ('+prior.slice(start,end).trim().replace(/;$/,'')+')')();
 const load=async path=>{await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+'/'+path});await new Promise(r=>setTimeout(r,350));await settle();assert(await run('!!window.wholeTree'));await Function('run','assert','return (async()=>{'+inject+'})()')(run,assert)};
 const frames=[];const capture=async(name,cam,{profile='mage:support:plain:standard',example=null,selected=null,clip=true,scroll=false}={})=>{
  await run(example?'wholeTree.applyExample('+JSON.stringify(example)+')':'wholeTree.setProfile('+JSON.stringify(profile)+')');if(selected)await run('wholeTree.select('+JSON.stringify(selected)+')');await run('wholeTree.setCam('+JSON.stringify(cam)+')');
  await run(clip?"[...stage.children].filter(e=>e!==canvas).forEach(e=>e.style.visibility='hidden')":"[...stage.children].forEach(e=>e.style.visibility='visible');el('toast').style.display='none';tip.style.display='none'");
  if(scroll)await run("document.querySelector('aside').scrollTop=document.querySelector('.detail').offsetTop-document.querySelector('aside').offsetTop-12");await settle();const audit=await run('wholeTree.audit()');assert(audit.minGlyphGap>0&&audit.minClickGap>0&&audit.minNodePairPaintGap>0&&audit.labelLines.length===0&&audit.centralLabelIntrusions.length===0,JSON.stringify(audit));
  const rect=await run('(()=>{const r=canvas.getBoundingClientRect();return{x:r.x,y:r.y,width:r.width,height:r.height,scale:1}})()');const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false,...(clip?{clip:rect}:{})});fs.writeFileSync(dir+'/'+name+'.png',Buffer.from(png.data,'base64'));frames.push({name,cam,profile,example,selected,audit,clip});console.log(name)
 };
 const cameras={Whole:{x:1230,y:-1245,z:.0667397060226203},Blood:{x:3250,y:-5600,z:.22},Shield:{x:4850,y:-4100,z:.50},Pen:{x:-2480,y:1500,z:.35}};
 for(const [version,path] of [['Before',old+'/ProjectS_Middle_Choices_V7.html'],['After',dir+'/ProjectS_Contract_V8.html']]){await load(path);for(const [region,cam] of Object.entries(cameras))await capture('ProjectS_Contract_V8_'+version+'_'+region,cam,region==='Blood'?{example:'mage-blood'}:region==='Shield'?{selected:'v7g12n7'}:region==='Pen'?{example:'quiet',selected:'v7g04n5'}:{})}
 await load(dir+'/ProjectS_Contract_V8.html');
 for(const [id,profile] of [['key_09','warrior:support:plain:standard'],['key_11','tank:support:plain:standard'],['key_13','mage:support:plain:standard'],['key_14','mage:support:fire:standard'],['key_15','mage:support:lightning:standard']]){
  const n=await run('by.get('+JSON.stringify(id)+')');await capture('ProjectS_Contract_V8_Contract_'+id,{x:n.x,y:n.y,z:.35},{profile,selected:id,clip:false,scroll:true})
 }
 await run("wholeTree.setProfile('mage:support:plain:standard');profile={...profile,flags:profile.flags.filter(x=>x!=='MP_CONSUMER')};wholeTree.select('v7g12n7');wholeTree.allocateGoal();document.querySelector('aside').scrollTop=0;[...stage.children].forEach(e=>e.style.visibility='visible');el('routeNote').textContent='入力感度テスト：既存編成からMP入力だけを除いた仮条件。新技能を供給する例ではありません。';wholeTree.setCam({x:4700,y:-4100,z:.35})");await settle();const png=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(dir+'/ProjectS_Contract_V8_Sensitivity_Shield_NoMP.png',Buffer.from(png.data,'base64'));
 assert.equal(errors.length,0);fs.writeFileSync(dir+'/actual-comparison-captures.json',JSON.stringify({status:'PASS',viewport:[1920,1200],sameCameras:true,frames,contractDetailFrames:5,syntheticScreenshotLabelled:true,runtimeErrors:errors},null,2));ws.close()
})().catch(e=>{console.error(e);process.exit(1)});
