import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir,access} from 'node:fs/promises';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
const root=dirname(fileURLToPath(import.meta.url)),audit=join(root,'reference-private','browser-audit'),profile=join(audit,'edge-profile');
const pause=ms=>new Promise(r=>setTimeout(r,ms));await mkdir(profile,{recursive:true});
const browser=spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',['--headless=new','--disable-gpu','--disable-background-networking','--disable-component-update','--disable-extensions','--disable-crash-reporter','--no-first-run','--no-default-browser-check','--renderer-process-limit=1','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'});
let exited=false;browser.on('exit',()=>{exited=true});let socket,seq=0;const pending=new Map();
async function call(method,params={}){const id=++seq;return await new Promise((ok,fail)=>{const timer=setTimeout(()=>{pending.delete(id);fail(new Error('CDP timeout: '+method))},10000);pending.set(id,{ok,fail,timer});socket.send(JSON.stringify({id,method,params}))})}
async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
async function navigate(path){await call('Page.navigate',{url:pathToFileURL(path).href});for(let i=0;i<40;i++){await pause(100);if(await evaluate('document.readyState==="complete"'))return;}throw new Error('Page did not load')}
const evidence={browser:'installed Edge headless, software rendering',browser_pid:browser.pid,game_capture:false,gpu_fps_measured:false,human_feel_verified:false,ordinary_gif_playback:[],viewer_buttons_verified:[],viewer_keyframes_verified:[],success:false};
try{
 let port;for(let i=0;i<100;i++){try{port=Number((await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0]);break;}catch{await pause(100)}}
 if(!port)throw new Error('Own Edge debugging endpoint did not start');
 const pages=await (await fetch('http://127.0.0.1:'+port+'/json/list')).json();const page=pages.find(p=>p.type==='page');if(!page)throw new Error('No own page');
 socket=new WebSocket(page.webSocketDebuggerUrl);await new Promise((ok,fail)=>{socket.addEventListener('open',ok,{once:true});socket.addEventListener('error',fail,{once:true})});
 socket.addEventListener('message',event=>{const m=JSON.parse(String(event.data)),p=pending.get(m.id);if(!p)return;clearTimeout(p.timer);pending.delete(m.id);if(m.error)p.fail(new Error(JSON.stringify(m.error)));else p.ok(m.result)});
 await call('Page.enable');await call('Runtime.enable');await call('Emulation.setDeviceMetricsOverride',{width:1280,height:900,deviceScaleFactor:1,mobile:false});
 const player=join(audit,'player.html');await writeFile(player,'<!doctype html><meta charset="utf-8"><style>body{margin:0;background:#14202c}img{width:640px;height:388px;image-rendering:pixelated}</style><img id="play">');await navigate(player);
 for(const [name,times] of [['contact-12.gif',[.19,.25,.32,.40,.50,.60,.72]],['11c-to-12-contact.gif',[.19,.32,.60,.99,1.12,1.40,1.52]]]){
  const url=pathToFileURL(join(root,'preview',name)).href+'?ordinary='+Date.now();
  const loaded=await evaluate('(async()=>{const im=document.getElementById("play");im.src='+JSON.stringify(url)+';await im.decode();return {w:im.naturalWidth,h:im.naturalHeight};})()');if(loaded.w!==640||loaded.h!==388)throw new Error('Unexpected GIF dimensions');
  const start=performance.now(),row={file:name,normal_wall_clock:true,shots:[]};
  for(const time of times){await pause(Math.max(0,time*1000-(performance.now()-start)));const result=await call('Page.captureScreenshot',{format:'png'});const bytes=Buffer.from(result.data,'base64'),file=name.replace('.gif','')+'-at-'+time.toFixed(2)+'s.png';await writeFile(join(audit,file),bytes);row.shots.push({requested_seconds:time,wall_seconds:Number(((performance.now()-start)/1000).toFixed(3)),file,sha256:createHash('sha256').update(bytes).digest('hex')})}
  evidence.ordinary_gif_playback.push(row);
 }
 await navigate(join(root,'preview','viewer.html'));const files=await evaluate('Array.from(document.querySelectorAll("button[data-file]"),b=>b.dataset.file)');
 for(const file of files){await access(join(root,'preview',file));const ok=await evaluate('(async()=>{Array.from(document.querySelectorAll("button[data-file]")).find(b=>b.dataset.file==='+JSON.stringify(file)+').click();const im=document.getElementById("image");await im.decode();return im.complete&&im.naturalWidth>0})()');if(!ok)throw new Error('Viewer asset failed: '+file);evidence.viewer_buttons_verified.push(file)}
 for(const index of [0,8,10,12,15,20,26,31]){const ok=await evaluate('(async()=>{const slider=document.getElementById("scrub");slider.value='+index+';slider.dispatchEvent(new Event("input",{bubbles:true}));const im=document.getElementById("image");await im.decode();return im.complete&&im.naturalWidth===640})()');if(!ok)throw new Error('Scrub failed');evidence.viewer_keyframes_verified.push(index)}
 const shot=await call('Page.captureScreenshot',{format:'png'});await writeFile(join(root,'preview','browser-player.png'),Buffer.from(shot.data,'base64'));evidence.success=true;
}catch(error){evidence.error=String(error)}finally{
 if(socket&&socket.readyState===1){try{await call('Browser.close')}catch{}socket.close()}
 for(let i=0;i<30&&!exited;i++)await pause(100);if(!exited)browser.kill();
 evidence.own_browser_root_exited=exited;await writeFile(join(root,'browser-evidence.json'),JSON.stringify(evidence,null,2)+'\n');
 console.log(JSON.stringify({success:evidence.success,ordinary_clips:evidence.ordinary_gif_playback.length,buttons:evidence.viewer_buttons_verified.length,scrub_frames:evidence.viewer_keyframes_verified.length,browser_pid:browser.pid,root_exited:exited,error:evidence.error}));if(!evidence.success||!exited)process.exitCode=1;
}
