// Raster calibration for this forge proposal, not a general HTML renderer.
const {chromium}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');

(async()=>{
  const output=path.resolve(__dirname,'../../../.tools/forge-shared-capture');
  fs.mkdirSync(output,{recursive:true});
  const browser=await chromium.launch({headless:true,channel:'msedge'});
  // Keep CSS layout/hits logical. Text and composited light need dense source pixels,
  // while the original HTML still uses nearest-neighbour sampling on pixel-art images.
  const rasterScale=2;
  const page=await browser.newPage({viewport:{width:1920,height:1080},reducedMotion:'reduce',deviceScaleFactor:rasterScale});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
  // Fixed calibration bounds. Live HTML is responsive; the game proof uses this one layout.
  await page.addStyleTag({content:'.columns{height:812px;min-height:812px}.quote{overflow:hidden}.item-stage{min-height:0}.motes{display:none}.receipt{backdrop-filter:none}'});
  const bases=[
    {id:'chest',gear:'chest',label:'胴・通常'},
    {id:'head',gear:'head',label:'頭・通常'},
    {id:'legs',gear:'legs',label:'脚・通常'},
    {id:'feet',gear:'feet',label:'足・通常'},
    {id:'weapon',gear:'weapon',label:'武器・通常'},
    {id:'chest-focused',gear:'chest',focused:true,label:'胴・触媒あり'},
    {id:'head-focused',gear:'head',focused:true,label:'頭・触媒あり'},
    {id:'legs-focused',gear:'legs',focused:true,label:'脚・触媒あり'},
    {id:'feet-focused',gear:'feet',focused:true,label:'足・触媒あり'},
    {id:'weapon-focused',gear:'weapon',focused:true,label:'武器・全5素材'},
    {id:'shortage',scenario:'short',label:'素材不足'},
    {id:'risk',scenario:'high',label:'高段階・破損リスク'},
    {id:'maximum',scenario:'max',label:'最大強化'},
  ];
  const definitions=[...bases,...bases.filter(d=>!d.scenario||d.scenario==='high').map(d=>({...d,id:'confirm-'+d.id,modal:true,label:d.label+'・確認'}))];
  const frames=[];
  const layerFiles=new Set();
  async function exportLayer(file,box,html){
    if(layerFiles.has(file))return;
    const style=await page.addStyleTag({content:'html,body{background:transparent!important}body:before,body:after,html:before,html:after{display:none!important}body>*{visibility:hidden!important}#export-layer{visibility:visible!important}'});
    await page.evaluate(({box,html})=>{const layer=document.createElement('div');layer.id='export-layer';layer.style.cssText=`position:absolute;left:${box.x+scrollX}px;top:${box.y+scrollY}px;width:${box.width}px;height:${box.height}px`;layer.innerHTML=html;document.body.append(layer)}, {box,html});
    await page.screenshot({path:path.join(output,file),clip:box,omitBackground:true});
    await page.locator('#export-layer').evaluate(e=>e.remove());await style.evaluate(e=>e.remove());layerFiles.add(file);
  }
  for(const d of definitions){
    await page.evaluate(()=>document.getElementById('confirmation').close());
    await page.locator('#reset').click();
    if(d.scenario)await page.locator('#scenario').selectOption(d.scenario);
    if(d.gear)await page.locator(`[data-id="${d.gear}"]`).click();
    if(d.focused)await page.locator('#catalyst').check();
    // Never export browser focus rings as part of the game's permanent backing.
    await page.evaluate(()=>document.activeElement?.blur());
    await page.locator('#hero').evaluate(i=>i.decode());
    if(d.modal){await page.locator('#enhance').click();await page.evaluate(()=>document.activeElement?.blur())}
    const outer=await page.locator('#forge').boundingBox();
    const clip={x:Math.round(outer.x),y:Math.round(outer.y),width:Math.round(outer.width),height:Math.round(outer.height)};
    let dynamic;
    if(!d.modal){
      const hero=await page.locator('#hero').boundingBox(),stage=await page.locator('.item-stage').boundingBox();
      const part=await page.evaluate(()=>gear().id),file=part+'-hero.png';
      const pad=48,box={x:Math.floor(hero.x-pad),y:Math.floor(hero.y-pad),width:Math.ceil(hero.width+pad*2),height:Math.ceil(hero.height+pad*2)};
      const image=await page.locator('#hero').evaluate(e=>({src:e.src,filter:getComputedStyle(e).filter}));
      await exportLayer(file,box,`<img src="${image.src}" style="position:absolute;left:${hero.x-box.x}px;top:${hero.y-box.y}px;width:${hero.width}px;height:${hero.height}px;image-rendering:pixelated;filter:${image.filter}">`);
      dynamic={hero:{file,x:box.x-clip.x,y:box.y-clip.y,w:box.width,h:box.height},stage:{x:stage.x-clip.x,y:stage.y-clip.y,w:stage.width,h:stage.height},glow:{x:stage.x-clip.x+stage.width*.04,y:stage.y-clip.y+stage.height*.07,w:stage.width*.92,h:stage.height*.81}};
      const glow=dynamic.glow,gradient=await page.locator('.item-stage').evaluate(e=>getComputedStyle(e,':after').backgroundImage);
      await exportLayer('glow.png',{x:clip.x+glow.x,y:clip.y+glow.y,width:glow.w,height:glow.h},`<div style="width:100%;height:100%;background-image:${gradient}"></div>`);
    }
    const still=await page.addStyleTag({content: d.modal?'/* confirmation stays still */': '#hero{visibility:hidden!important}.item-stage:after{display:none!important}'});
    // Crop only the forge, excluding the browser-only comparison controls.
    await page.screenshot({path:path.join(output,d.id+'.png'),clip});
    await still.evaluate(e=>e.remove());
    const ids=d.modal?['#cancel','#confirm']:['[data-id="weapon"]','[data-id="head"]','[data-id="chest"]','[data-id="legs"]','[data-id="feet"]','#catalyst','#enhance'];
    const hits=[];
    for(const selector of ids){
      const locator=page.locator(selector),b=await locator.boundingBox();
      if(!b||await locator.isDisabled())continue;
      const action=selector.startsWith('[data-id')?'select:'+await locator.getAttribute('data-id'):selector.slice(1);
      hits.push({action,x:b.x-clip.x,y:b.y-clip.y,w:b.width,h:b.height});
    }
    frames.push({...d,width:clip.width,height:clip.height,rasterScale,hits,dynamic});
  }
  await browser.close();
  if(errors.length)throw Error(errors.join('\n'));
  const motion={floatPeriodMs:5600,floatAmplitude:3,glowPeriodMs:6400,motePeriodMs:6000,moteSize:2,moteDriftX:9,moteDriftY:-75,motes:[{x:.35,y:.58,delay:1000},{x:.64,y:.78,delay:4000},{x:.70,y:.50,delay:2000},{x:.27,y:.70,delay:5000},{x:.53,y:.82,delay:3000}]};
  fs.writeFileSync(path.join(output,'captures.json'),JSON.stringify({source:'index.html',referenceViewport:[1920,1080],motion,frames},null,2));
  console.log('Captured '+frames.length+' calibration fixtures:',output);
})().catch(e=>{console.error(e);process.exitCode=1});
