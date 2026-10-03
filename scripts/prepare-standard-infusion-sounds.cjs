// Read only installed Minecraft 26.2 sound catalog; decode all variants of three standard events.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),http=require('node:http'),cp=require('node:child_process');
const repo=path.resolve(__dirname,'..'),root=path.dirname(repo),out=path.join(repo,'assets/model-lab/infusion-minecraft-standard-sounds');fs.mkdirSync(out,{recursive:true});
const assets='C:/Users/xgaiz/Documents/Codex/2026-08-30/referenced-chatgpt-conversation-this-is-an/work/gradle-home-quest-client/caches/fabric-loom/assets';
const index=JSON.parse(fs.readFileSync(path.join(assets,'indexes/26.2-32.json'),'utf8')),object=h=>path.join(assets,'objects',h.slice(0,2),h);
const sounds=JSON.parse(fs.readFileSync(object(index.objects['minecraft/sounds.json'].hash),'utf8'));
const names=['block.amethyst_block.hit','block.enchantment_table.use','block.amethyst_block.resonate'];
const entries=[];for(const event of names){
 const definition=sounds[event];if(!definition)throw Error('Event missing from installed catalog: '+event);
 for(const v of definition.sounds){const d=typeof v==='string'?{name:v}:v,asset=index.objects['minecraft/sounds/'+d.name+'.ogg'];if(!asset)throw Error('Sound asset missing');
 const bytes=fs.readFileSync(object(asset.hash));if(crypto.createHash('sha1').update(bytes).digest('hex')!==asset.hash)throw Error('Asset hash mismatch');
 entries.push({event,sound:d.name,assetSha1:asset.hash,bytes:bytes.length,definitionVolume:d.volume??1,definitionPitch:d.pitch??1,weight:d.weight??1,attenuationDistance:d.attenuation_distance??16,source:object(asset.hash),data:bytes});
 }}
const reference=path.join(root,'reference-only/standard-infusion-sounds');fs.mkdirSync(reference,{recursive:true});
const html=`<!doctype html><script>window.decodeAll=async()=>{
const ac=new AudioContext({sampleRate:48000}),results=[];
for(let i=0;i<${entries.length};i++){
const b=await ac.decodeAudioData(await (await fetch('/sound/'+i)).arrayBuffer()),mono=new Float32Array(b.length);
for(let ch=0;ch<b.numberOfChannels;ch++){const d=b.getChannelData(ch);for(let k=0;k<d.length;k++)mono[k]+=d[k]/b.numberOfChannels}
let binary='';const bytes=new Uint8Array(mono.buffer);for(let k=0;k<bytes.length;k+=8192)binary+=String.fromCharCode(...bytes.subarray(k,k+8192));
results.push({base64:btoa(binary),sampleRate:b.sampleRate,channels:b.numberOfChannels,duration:b.duration,length:b.length});
}await ac.close();return results;};</script>`;
const server=http.createServer((req,res)=>{const match=/^\/sound\/(\d+)$/.exec(req.url);if(match&&entries[Number(match[1])]){res.setHeader('Content-Type','audio/ogg');res.end(entries[Number(match[1])].data)}else{res.setHeader('Content-Type','text/html');res.end(html)}});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
await new Promise(r=>server.listen(0,'127.0.0.1',r));const port=server.address().port;
const profile=path.join(repo,'.tools/standard-sound-decode-'+Date.now());fs.mkdirSync(profile,{recursive:true});let browser,ws;
try{
browser=cp.spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--disable-background-networking','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0','--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','http://127.0.0.1:'+port],{windowsHide:true,stdio:'ignore'});
const active=path.join(profile,'DevToolsActivePort');for(let i=0;i<100&&!fs.existsSync(active);i++)await sleep(100);if(!fs.existsSync(active))throw Error('Owned browser unavailable');
const debug=Number(fs.readFileSync(active,'utf8').split('\n')[0]);let pages=[];for(let i=0;i<50;i++){pages=await (await fetch('http://127.0.0.1:'+debug+'/json/list')).json();if(pages.some(p=>p.url.startsWith('http://127.0.0.1:'+port)))break;await sleep(100)}
const page=pages.find(p=>p.type==='page'&&p.url.startsWith('http://127.0.0.1:'+port));if(!page)throw Error('Owned page unavailable');
ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.addEventListener('open',r);ws.addEventListener('error',j)});
let id=0;const pending=new Map();ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(pending.has(m.id)){pending.get(m.id)(m);pending.delete(m.id)}});
const send=(method,params={})=>new Promise(r=>{const key=++id;pending.set(key,r);ws.send(JSON.stringify({id:key,method,params}))});
let ready=false;for(let i=0;i<100;i++){const state=await send('Runtime.evaluate',{expression:'typeof window.decodeAll',returnByValue:true});if(state.result?.result?.value==='function'){ready=true;break}await sleep(100)}if(!ready)throw Error('Decode page failed');
const response=await send('Runtime.evaluate',{expression:'window.decodeAll()',awaitPromise:true,returnByValue:true});if(response.result?.exceptionDetails)throw Error(JSON.stringify(response.result.exceptionDetails));
const results=response.result.result.value;
for(let i=0;i<results.length;i++){
 const r=results[i],pcm=Buffer.from(r.base64,'base64');delete r.base64;
 fs.writeFileSync(path.join(reference,i+'.f32'),pcm);let peak=0,sum=0;
 for(let k=0;k<pcm.length;k+=4){const v=pcm.readFloatLE(k);peak=Math.max(peak,Math.abs(v));sum+=v*v}
 Object.assign(entries[i],r,{index:i,sourcePeak:peak,sourceRms:Math.sqrt(sum/(pcm.length/4))});delete entries[i].data;delete entries[i].source;
 }
const report={catalogVersion:'26.2-32',catalogSha1:index.objects['minecraft/sounds.json'].hash,existingNativeEvent:'block.amethyst_block.chime',
actuallyListened:false,listenLimitation:'No audio-perception/listening tool exposed; event semantics, decoded sources and signal measurements only',
source:'Existing installed client assets only; no external downloads, unknown sources or synthesis',events:names,entries};
fs.writeFileSync(path.join(out,'standard-sound-catalog.json'),JSON.stringify(report,null,2));await send('Browser.close');
console.log('STANDARD_SOUND_CATALOG_PASS '+JSON.stringify(entries.map(e=>({index:e.index,event:e.event,sound:e.sound,duration:e.duration,peak:e.sourcePeak}))));
}finally{if(ws)ws.close();if(browser&&!browser.killed)browser.kill();server.close()}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
