// Resolve and decode the installed client's original amethyst sound. No synth or native mutation.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),http=require('node:http'),cp=require('node:child_process');
const repo=path.resolve(__dirname,'..'),root=path.dirname(repo),out=path.join(repo,'assets/model-lab/infusion-original-sound-timing');fs.mkdirSync(out,{recursive:true});
const assets='C:/Users/xgaiz/Documents/Codex/2026-08-30/referenced-chatgpt-conversation-this-is-an/work/gradle-home-quest-client/caches/fabric-loom/assets';
const index=JSON.parse(fs.readFileSync(path.join(assets,'indexes/26.2-32.json'),'utf8'));
const object=h=>path.join(assets,'objects',h.slice(0,2),h);
const sounds=JSON.parse(fs.readFileSync(object(index.objects['minecraft/sounds.json'].hash),'utf8'));
const definition=sounds['block.amethyst_block.chime'];
if(definition.sounds.length!==1||definition.sounds[0].name!=='block/amethyst/shimmer'||definition.sounds[0].volume!==.2)throw Error('Original sound definition changed');
const hash=index.objects['minecraft/sounds/block/amethyst/shimmer.ogg'].hash,original=fs.readFileSync(object(hash));
if(crypto.createHash('sha1').update(original).digest('hex')!==hash)throw Error('Original sound hash mismatch');
const reference=path.join(root,'reference-only/original-amethyst-shimmer.ogg');fs.writeFileSync(reference,original);
const html=`<!doctype html><script>window.decode=async()=>{
const ac=new AudioContext({sampleRate:48000});const b=await ac.decodeAudioData(await (await fetch('/original.ogg')).arrayBuffer());
const mono=new Float32Array(b.length);for(let ch=0;ch<b.numberOfChannels;ch++){const d=b.getChannelData(ch);for(let i=0;i<d.length;i++)mono[i]+=d[i]/b.numberOfChannels}
let binary='';const bytes=new Uint8Array(mono.buffer);for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));await ac.close();
return {base64:btoa(binary),sampleRate:b.sampleRate,channels:b.numberOfChannels,duration:b.duration,length:b.length};};</script>`;
const server=http.createServer((req,res)=>{if(req.url==='/original.ogg'){res.setHeader('Content-Type','audio/ogg');res.end(original)}else{res.setHeader('Content-Type','text/html');res.end(html)}});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
await new Promise(r=>server.listen(0,'127.0.0.1',r));const port=server.address().port;
const profile=path.join(repo,'.tools/original-sound-decode-'+Date.now());fs.mkdirSync(profile,{recursive:true});let browser,ws;
try{
browser=cp.spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--disable-background-networking','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0','--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','http://127.0.0.1:'+port],{windowsHide:true,stdio:'ignore'});
const active=path.join(profile,'DevToolsActivePort');for(let i=0;i<100&&!fs.existsSync(active);i++)await sleep(100);if(!fs.existsSync(active))throw Error('Owned browser unavailable');
const debug=Number(fs.readFileSync(active,'utf8').split('\n')[0]);let pages=[];for(let i=0;i<50;i++){pages=await (await fetch('http://127.0.0.1:'+debug+'/json/list')).json();if(pages.some(p=>p.url.startsWith('http://127.0.0.1:'+port)))break;await sleep(100)}
const page=pages.find(p=>p.type==='page'&&p.url.startsWith('http://127.0.0.1:'+port));if(!page)throw Error('Owned page unavailable');
ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.addEventListener('open',r);ws.addEventListener('error',j)});
let id=0;const pending=new Map();ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(pending.has(m.id)){pending.get(m.id)(m);pending.delete(m.id)}});
const send=(method,params={})=>new Promise(r=>{const key=++id;pending.set(key,r);ws.send(JSON.stringify({id:key,method,params}))});
let ready=false;for(let i=0;i<100;i++){const state=await send('Runtime.evaluate',{expression:'typeof window.decode',returnByValue:true});if(state.result?.result?.value==='function'){ready=true;break}await sleep(100)}if(!ready)throw Error('Decode page failed');
const response=await send('Runtime.evaluate',{expression:'window.decode()',awaitPromise:true,returnByValue:true});if(response.result?.exceptionDetails)throw Error(JSON.stringify(response.result.exceptionDetails));
const result=response.result.result.value,pcm=Buffer.from(result.base64,'base64');delete result.base64;
fs.writeFileSync(path.join(root,'reference-only/original-amethyst-shimmer.f32'),pcm);
Object.assign(result,{event:'block.amethyst_block.chime',sound:'block/amethyst/shimmer',assetSha1:hash,assetBytes:original.length,soundDefinitionVolume:.2,
originalStart:{serverVolume:.6,effectiveAssetGain:.12,pitch:.8},originalCompletion:{serverVolume:.7,effectiveAssetGain:.14,pitch:1.3},
nativeSpeakerRecording:false,actuallyListened:false,listenLimitation:'This execution environment exposes no listening or audio-perception tool. Decoded file and signal/source comparisons only.'});
fs.writeFileSync(path.join(out,'original-sound-provenance.json'),JSON.stringify(result,null,2));
await send('Browser.close');console.log('ORIGINAL_INFUSION_SOUND_DECODED '+JSON.stringify(result));
}finally{if(ws)ws.close();if(browser&&!browser.killed)browser.kill();server.close()}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
