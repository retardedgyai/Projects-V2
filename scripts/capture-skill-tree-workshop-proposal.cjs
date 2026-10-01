/* Capture the standalone study using an already-running, dedicated Chrome CDP. */
const fs=require('node:fs'),assert=require('node:assert/strict');
const dir='assets/core-ui/large-tree-preview/proposals/workshop-v1';
(async()=>{
 const tab=(await(await fetch('http://127.0.0.1:18126/json/list')).json()).find(t=>t.type==='page');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
 let id=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const i=++id;pending.set(i,{resolve,reject});ws.send(JSON.stringify({id:i,method,params}))});
 const run=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
 await send('Emulation.setDeviceMetricsOverride',{width:1600,height:1060,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:'file:///'+process.cwd().replaceAll('\\','/')+`/${dir}/ProjectS_Passive_Workshop_Proposal.html`});
 await new Promise(r=>setTimeout(r,500));await run('document.fonts.ready.then(()=>true)');
 const frames=[];
 async function capture(name){await run('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');const p=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(`${dir}/${name}.png`,Buffer.from(p.data,'base64'));const s=await run('workshopProposal.snapshot()');frames.push({name,origin:s.origin,mode:s.mode,visibleNodes:s.positions.length,visibleEdges:s.edges.length})}
 await capture('ProjectS_Workshop_Route_A');await run('workshopProposal.selectRoute(1)');await capture('ProjectS_Workshop_Route_B');
 await run("workshopProposal.selectOrigin('warrior');workshopProposal.setMode('origins')");await capture('ProjectS_Workshop_Five_Starts');
 for(const [origin,name] of [['tank','Tank'],['mage','Mage'],['assassin','Assassin'],['ranger','Ranger']]){await run(`workshopProposal.selectOrigin('${origin}');workshopProposal.setMode('route')`);await capture('ProjectS_Workshop_'+name)}
 console.log(JSON.stringify({status:'captured',frames}));ws.close();
})().catch(e=>{console.error(e);process.exit(1)});
