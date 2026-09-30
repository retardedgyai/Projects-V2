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
    // Crop only the forge, excluding the browser-only comparison controls.
    await page.screenshot({path:path.join(output,d.id+'.png'),clip});
    const ids=d.modal?['#cancel','#confirm']:['[data-id="weapon"]','[data-id="head"]','[data-id="chest"]','[data-id="legs"]','[data-id="feet"]','#catalyst','#enhance'];
    const hits=[];
    for(const selector of ids){
      const locator=page.locator(selector),b=await locator.boundingBox();
      if(!b||await locator.isDisabled())continue;
      const action=selector.startsWith('[data-id')?'select:'+await locator.getAttribute('data-id'):selector.slice(1);
      hits.push({action,x:b.x-clip.x,y:b.y-clip.y,w:b.width,h:b.height});
    }
    frames.push({...d,width:clip.width,height:clip.height,rasterScale,hits});
  }
  await browser.close();
  if(errors.length)throw Error(errors.join('\n'));
  fs.writeFileSync(path.join(output,'captures.json'),JSON.stringify({source:'index.html',referenceViewport:[1920,1080],frames},null,2));
  console.log('Captured '+frames.length+' calibration fixtures:',output);
})().catch(e=>{console.error(e);process.exitCode=1});
