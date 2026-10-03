// Prepared for the parent's rendering queue. This script has NOT been executed.
// One owned official local Blockbench; two 3.6sec clips, side +3/4, total144 captures.
const fs=require('fs'),{chromium}=require('C:/Users/xgaiz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const out='outputs/reentry_direct_first_step',project='dragon_v8_REENTRY_BEFORE_AFTER_RUNTIME_PREVIEW';
(async()=>{const browser=await chromium.connectOverCDP('http://127.0.0.1:19493');try{
 const page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().endsWith('index.html'));if(!page)throw Error('Owned local editor missing');
 const M=JSON.parse(fs.readFileSync(out+'/dragon_v8_REENTRY_BEFORE_AFTER_RUNTIME_PREVIEW_NOT_GAME_EXPORT.bbmodel','utf8'));
 const map=JSON.parse(fs.readFileSync(out+'/NATIVE_COMPARISON_MAPPING.json','utf8'));
 await page.evaluate(async({M,project})=>{if(Project){if(!['',project,'dragon_v8_STOP_REENTRY_ACTUAL_RUNTIME_NATIVE_REVIEW'].includes(Project.name))throw Error('Another project present; untouched');Timeline.pause();Project.saved=true;await Project.close(true);}
  Codecs.project.load(M,{name:M.name+'.bbmodel',path:''});await new Promise(r=>setTimeout(r,600));Modes.options.animate.select();Timeline.pause();Project.view_mode='textured';Canvas.updateViewMode();if(typeof unselectAll==='function')unselectAll();Project.model_3d.position.set(0,0,0);
  const helper=new THREE.Group();helper.name='owned_direct_first_step_floor_not_exported';helper.add(new THREE.GridHelper(240,40,0x4c5665,0x303640));const ground=new THREE.Mesh(new THREE.PlaneGeometry(240,240),new THREE.MeshBasicMaterial({color:0x1f252c,side:THREE.DoubleSide}));ground.rotation.x=-Math.PI/2;ground.position.y=-.05;helper.add(ground);scene.add(helper);window.ownedDirectFirstStepFloor=helper;
 },{M,project});
 const rows=[];
 for(const c of map.cases)for(const view of ['side','threeQuarter']){
  const dir=`work/direct_first_step_${c.key}_${view}_frames`;fs.mkdirSync(dir,{recursive:true});
  for(let frame=0;frame<=36;frame++){
   const r=await page.evaluate(({c,view,frame,project})=>{if(Project.name!==project||Group.all.length!==34||Outliner.elements.length!==132)throw Error('Owned project guard');
    Animation.all.forEach(a=>a.playing=false);const a=Animation.all.find(a=>a.name===c.clip);a.select();a.playing=true;Timeline.setTime(frame*.1);Animator.preview();for(const g of Group.all)g.mesh.updateWorldMatrix(true,false);
    const p=Preview.selected||Preview.all.find(p=>p.id==='main');p.setProjectionMode(true);p.controls.target.set(0,35,10);p.camera.position.set(...(view==='side'?[-140,35,10]:[-88,57,-105]));const half=view==='side'?83:86;p.camera.left=-half;p.camera.right=half;p.camera.top=half*p.height/p.width;p.camera.bottom=-half*p.height/p.width;p.camera.zoom=1;p.camera.updateProjectionMatrix();p.controls.update();p.render();const v=p.canvas.getBoundingClientRect();return{key:c.key,clip:c.clip,view,frame,time:frame*.1,viewport:{x:v.x,y:v.y,width:v.width,height:v.height},bones:Group.all.map(g=>({name:g.name,origin:g.origin,matrix:g.mesh.matrixWorld.toArray()}))};
   },{c,view,frame,project});
   if(frame<36)await page.screenshot({path:`${dir}/${String(frame).padStart(3,'0')}.png`,clip:r.viewport});rows.push(r);
  }
  console.log('ONE_SHORT_NATIVE_COMPARISON_DONE '+c.key+' '+view);
 }
 const playback=[];
 for(const c of map.cases){const result=await page.evaluate(async({name,L,project})=>{if(Project.name!==project)throw Error('Project changed');Animation.all.forEach(a=>a.playing=false);const a=Animation.all.find(a=>a.name===name);a.select();a.playing=true;Timeline.setTime(0);Timeline.start();const start=performance.now();await new Promise(r=>setTimeout(r,Math.ceil((L+.3)*1000)));const result={clip:name,elapsed:(performance.now()-start)/1000,time:Timeline.time,playing:Timeline.playing};Timeline.pause();return result;},{name:c.clip,L:c.length_seconds,project});playback.push(result);}
 const state=await page.evaluate(()=>({version:Blockbench.version,bones:Group.all.length,elements:Outliner.elements.length,textures:Texture.all.map(t=>({width:t.width,height:t.height,error:t.error}))}));
 fs.writeFileSync(out+'/NATIVE_DIRECT_FIRST_STEP_CAPTURE_TRACE.json',JSON.stringify({rows,playback,state,fps:10,motion_seconds:3.6,fixed_camera_same_size:true,game_root_baked_once_preview_only:true},null,2));console.log('NATIVE_SHORT_COMPARISON144FRAMES_AND_BOUNDARIES_DONE');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exit(1)});
