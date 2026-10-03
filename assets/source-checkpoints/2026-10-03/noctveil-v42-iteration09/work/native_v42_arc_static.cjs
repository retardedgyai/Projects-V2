const fs=require('fs'),path=require('path'),crypto=require('crypto');
const {chromium}=require('C:/Users/xgaiz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
let browser;
(async()=>{
 const guard=setTimeout(()=>process.exit(2),120000);
 browser=await chromium.connectOverCDP('http://127.0.0.1:19409',{timeout:20000});
 const page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().endsWith('index.html'));
 if(!page)throw Error('Owned Blockbench page not ready');
 page.setDefaultTimeout(20000);await page.setViewportSize({width:1600,height:900});
 const iteration=process.argv[2]||'iteration_03';
 const file=`outputs/v42_path_study/${iteration}/projects_noctveil_v42_wing_arc_candidate.bbmodel`;
 const output=`outputs/v42_path_study/${iteration}/native_static`;fs.mkdirSync(output,{recursive:true});
 const clip='wing_arm_arc_constraint_study_v42';
 const loaded=await page.evaluate(async({file,clip})=>{
  for(const d of Object.values(Dialog.all||{}))d.hide?.();
  settings.fps_limit.value=10;settings.shading.value=true;
  for(const k of ['grids','ground_plane']){settings[k].value=false;settings[k].onChange?.()}
  Timeline.pause();for(const p of ModelProject.all.slice()){p.saved=true;await p.close()}
  const data=await new Promise(r=>Blockbench.read([file],{readtype:'text',errorbox:false},files=>r(JSON.parse(files[0].content))));
  Codecs.project.load(data,{name:data.name+'.bbmodel',path:''});
  await new Promise(r=>setTimeout(r,300));Modes.options.animate.select();
  for(const a of Animation.all)a.playing=false;
  const a=Animation.all.find(a=>a.name===clip);if(!a)throw Error('Missing study clip');a.select();a.playing=true;
  return {version:Blockbench.version,elements:Outliner.elements.length,groups:Group.all.length,clip:a.name,textures:Texture.all.map(t=>({name:t.name,error:t.error}))};
 },{file:path.resolve(file),clip});
 const hero=JSON.parse(fs.readFileSync('work/native_v3_review.json')).captures.find(c=>c.variant==='v3'&&c.view==='hero');
 const cameras={hero:{target:hero.target,camera:hero.camera},front:{target:[0,19,8.689],camera:[0,35,-180]},side:{target:[0,19,8.689],camera:[180,27,8.689]}};
 const samples=[],captures=[];
 const sampleTimes=process.argv[3]?process.argv[3].split(',').map(Number):[0,1,1.75,2,2.1875,3,3.5,4];
 for(const time of sampleTimes){
  const s=await page.evaluate(async t=>{
   Timeline.pause();Timeline.time=t;unselectAll();Canvas.updateAll();Animator.preview();await new Promise(r=>requestAnimationFrame(r));
   Timeline.time=t;Animator.preview();Canvas.scene.updateMatrixWorld(true);Canvas.ground_plane.visible=false;
   const world={},bones={};let minimum_y=Infinity;
   for(const e of Outliner.elements){e.mesh.updateWorldMatrix(true,true);const w={};for(const [k,v] of Object.entries(e.vertices)){const p=new THREE.Vector3(...v).sub(new THREE.Vector3(...e.origin)).applyMatrix4(e.mesh.matrixWorld).toArray();w[k]=p;minimum_y=Math.min(minimum_y,p[1])}world[e.uuid]=w}
   for(const g of Group.all){g.mesh.updateWorldMatrix(true,true);bones[g.name]={pivot:g.mesh.getWorldPosition(new THREE.Vector3()).toArray(),world_rotation:g.mesh.getWorldQuaternion(new THREE.Quaternion()).toArray()}}
   return {time:t,actual_time:Timeline.time,selected_clip:Animation.selected.name,world,bones,minimum_y};
  },time);samples.push(s);
  fs.writeFileSync(`work/native_v42_arc_${iteration}_partial.json`,JSON.stringify({loaded,samples}));
 }
 const allPoints=samples.flatMap(s=>Object.values(s.world).flatMap(v=>Object.values(v)));
 const lo=[0,1,2].map(a=>Math.min(...allPoints.map(p=>p[a]))),hi=[0,1,2].map(a=>Math.max(...allPoints.map(p=>p[a])));
 const bounds=[];for(const x of [lo[0],hi[0]])for(const y of [lo[1],hi[1]])for(const z of [lo[2],hi[2]])bounds.push([x,y,z]);
 for(const time of samples.map(s=>s.time)){
  await page.evaluate(t=>{Timeline.pause();Timeline.time=t;Animator.preview();Canvas.scene.updateMatrixWorld(true)},time);
  for(const view of time===2?['hero','front','side','hand_front']:['hero']){
   const c=await page.evaluate(({view,cameras,bounds})=>{
    const v=Preview.selected;v.setProjectionMode(true);
    if(view==='hand_front'){
     const g=Group.all.find(g=>g.name==='right_wing_hand');g.mesh.updateWorldMatrix(true,true);const p=g.mesh.getWorldPosition(new THREE.Vector3());
     v.controls.target.copy(p);v.camera.position.copy(p).add(new THREE.Vector3(0,2,-130));v.camera.zoom=1.2;
    }else{
     const c=cameras[view];const center=new THREE.Vector3(...[0,1,2].map(a=>(Math.min(...bounds.map(p=>p[a]))+Math.max(...bounds.map(p=>p[a])))/2));
     const direction=new THREE.Vector3(...c.camera).sub(new THREE.Vector3(...c.target)).normalize();
     v.controls.target.copy(center);v.camera.position.copy(center).add(direction.multiplyScalar(250));v.camera.zoom=1;v.camera.updateProjectionMatrix();v.controls.update();v.camera.updateMatrixWorld(true);
     const pts=bounds.map(p=>new THREE.Vector3(...p).project(v.camera));v.camera.zoom=Math.min(1.1/Math.max(...pts.map(p=>Math.abs(p.x))),1.1/Math.max(...pts.map(p=>Math.abs(p.y))));
    }
    v.camera.updateProjectionMatrix();v.controls.update();v.camera.updateMatrixWorld(true);v.render();
    const project=p=>{const n=p.clone().project(v.camera);return {world:p.toArray(),pixel:[(n.x+1)*v.canvas.width/2,(-n.y+1)*v.canvas.height/2]}};
    const box=[];for(const x of [-5,5])for(const y of [0,18])for(const z of [-32,-28])box.push(project(new THREE.Vector3(x,y,z)));
    const geometry_projection={};for(const e of Outliner.elements.filter(e=>e.name.startsWith('right_v2_folded_')))geometry_projection[e.name]=Object.values(e.vertices).map(q=>project(new THREE.Vector3(...q).sub(new THREE.Vector3(...e.origin)).applyMatrix4(e.mesh.matrixWorld)));
    return {camera:v.camera.position.toArray(),target:v.controls.target.toArray(),zoom:v.camera.zoom,width:v.canvas.width,height:v.canvas.height,opponent_projection:box,geometry_projection,image:v.canvas.toDataURL('image/png')};
   },{view,cameras,bounds});
   const imagePath=path.join(output,`pose_${time}_${view}.png`);fs.writeFileSync(imagePath,Buffer.from(c.image.split(',')[1],'base64'));delete c.image;captures.push({time,view,path:imagePath,...c});
  }
 }
 const result={path:file,clip,loaded,model_sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),samples,captures,scope:`${samples.length} paused native static poses/intermediate inspection samples;${captures.length} snapshots. No full animation render, real-time playback, game collision, MatE quality pass or user acceptance. Fixed provisional opponent unchanged.`};
 fs.writeFileSync(`work/native_v42_arc_${iteration}.json`,JSON.stringify(result));
 console.log(JSON.stringify({sha:result.model_sha256,poses:samples.length,captures:captures.length,loaded}));
 await browser.close();browser=null;clearTimeout(guard);
})().catch(async e=>{console.error(e);if(browser)await browser.close().catch(()=>{});process.exit(1)});
