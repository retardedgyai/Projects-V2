// Encode with an installed browser, using an owned headless profile and local HTTP only.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),cp=require('node:child_process');
const root=path.resolve(__dirname,'../assets/model-lab/infusion-minecraft-standard-sounds');
const profile=path.resolve(__dirname,'../.tools/media-recorder-profile-'+Date.now());fs.mkdirSync(profile,{recursive:true});
const trace=JSON.parse(fs.readFileSync(path.join(root,'full-ritual-trace.json'),'utf8'));
const html=`<!doctype html><meta charset="utf-8"><canvas id="c" width="960" height="744"></canvas><script>
window.record=async()=>{
const frames=${trace.frames.length},fps=10,canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d');
const image=async i=>{const im=new Image();im.src='/frames/'+String(i).padStart(4,'0')+'.png';await im.decode();return im};
const ac=new AudioContext({sampleRate:48000});await ac.resume();
const audio=await ac.decodeAudioData(await (await fetch('/synchronized-original-sfx.wav')).arrayBuffer());
const dest=ac.createMediaStreamDestination(),source=ac.createBufferSource();source.buffer=audio;source.connect(dest);
const vs=canvas.captureStream(0),vt=vs.getVideoTracks()[0];
const stream=new MediaStream([...vs.getVideoTracks(),...dest.stream.getAudioTracks()]);
const mime='video/webm;codecs=vp8,opus';if(!MediaRecorder.isTypeSupported(mime))throw Error('VP8/Opus unavailable');
const recorder=new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:1800000,audioBitsPerSecond:128000});
const chunks=[];recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};
const done=new Promise((resolve,reject)=>{recorder.onstop=resolve;recorder.onerror=reject});
const cache=new Map();cache.set(0,await image(0));ctx.drawImage(cache.get(0),0,0);
recorder.start(1000);vt.requestFrame();const start=ac.currentTime+.15;source.start(start);
for(let i=0;i<frames;i++){
 if(!cache.has(i))cache.set(i,await image(i));
 if(i+1<frames&&!cache.has(i+1))cache.set(i+1,await image(i+1));
 const target=start+i/fps;while(ac.currentTime<target)await new Promise(r=>setTimeout(r,5));
 ctx.drawImage(cache.get(i),0,0);vt.requestFrame();cache.delete(i);
}
while(ac.currentTime<start+audio.duration)await new Promise(r=>setTimeout(r,10));
recorder.stop();await done;source.disconnect();await ac.close();
const blob=new Blob(chunks,{type:mime}),bytes=new Uint8Array(await blob.arrayBuffer());
let binary='';for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
const video=document.createElement('video');video.src=URL.createObjectURL(blob);video.muted=true;
await new Promise((r,j)=>{video.onloadedmetadata=r;video.onerror=j});
// Live WebM reports Infinity; seeking to its end establishes the actual duration.
if(!Number.isFinite(video.duration)){video.currentTime=1e8;await new Promise(r=>{video.ontimeupdate=r;setTimeout(r,2500)})}
return {base64:btoa(binary),mime,bytes:bytes.length,videoTracks:stream.getVideoTracks().length,audioTracks:stream.getAudioTracks().length,
durationSeconds:Number.isFinite(video.duration)?video.duration:null,expectedSeconds:audio.duration+.15,width:video.videoWidth,height:video.videoHeight};
};</script>`;
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1');if(url.pathname==='/'){res.setHeader('Content-Type','text/html');res.end(html);return}
 const file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.statusCode=404;res.end();return}
 res.setHeader('Content-Type',file.endsWith('.png')?'image/png':file.endsWith('.wav')?'audio/wav':'application/octet-stream');fs.createReadStream(file).pipe(res);
});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const port=server.address().port;
 const chrome='C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
 let browser,ws;try{
 browser=cp.spawn(chrome,['--headless=new','--disable-background-networking','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',
 '--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','--autoplay-policy=no-user-gesture-required','http://127.0.0.1:'+port+'/'],{windowsHide:true,stdio:'ignore'});
 const active=path.join(profile,'DevToolsActivePort');for(let i=0;i<100&&!fs.existsSync(active);i++)await sleep(100);
 if(!fs.existsSync(active))throw Error('Owned browser DevTools unavailable');
 const debugPort=Number(fs.readFileSync(active,'utf8').split('\n')[0]);
 let pages=[];for(let i=0;i<50;i++){pages=await (await fetch('http://127.0.0.1:'+debugPort+'/json/list')).json();if(pages.some(p=>p.url.startsWith('http://127.0.0.1:'+port)))break;await sleep(100)}
 const page=pages.find(p=>p.type==='page'&&p.url.startsWith('http://127.0.0.1:'+port));if(!page)throw Error('Owned page unavailable');
 ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.addEventListener('open',r);ws.addEventListener('error',j)});
 let id=0;const pending=new Map();ws.addEventListener('message',e=>{const msg=JSON.parse(e.data);if(pending.has(msg.id)){pending.get(msg.id)(msg);pending.delete(msg.id)}});
 const send=(method,params={})=>new Promise(r=>{const key=++id;pending.set(key,r);ws.send(JSON.stringify({id:key,method,params}))});
 let ready=false;for(let i=0;i<100;i++) {
   const state=await send('Runtime.evaluate',{expression:'typeof window.record',returnByValue:true});
   if(state.result?.result?.value==='function'){ready=true;break}await sleep(100);
 }
 if(!ready)throw Error('Owned local recording page did not load');
 const response=await send('Runtime.evaluate',{expression:'window.record()',awaitPromise:true,returnByValue:true});
 if(response.result?.exceptionDetails||response.error)throw Error(JSON.stringify(response.result?.exceptionDetails||response.error));
 const result=response.result.result.value;const bytes=Buffer.from(result.base64,'base64');delete result.base64;
 if(!bytes.includes(Buffer.from('A_OPUS'))||!bytes.includes(Buffer.from('V_VP8')))throw Error('Encoded audio/video codecs missing');
 fs.writeFileSync(path.join(root,'minecraft-standard-sound-comparison.webm'),bytes);
 result.codecsConfirmed=['V_VP8','A_OPUS'];result.ownedBrowserPid=browser.pid;result.localOnly=true;result.nativeNotApplied=true;
 fs.writeFileSync(path.join(root,'video-verification.json'),JSON.stringify(result,null,2));
 await send('Browser.close');console.log('MINECRAFT_STANDARD_SOUND_VIDEO_PASS '+JSON.stringify(result));
 }finally{if(ws)ws.close();if(browser&&!browser.killed)browser.kill();server.close()}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
