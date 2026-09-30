const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
(async()=>{
  const browser=await chromium.launch({channel:'msedge',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1920,height:1200}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    const base=process.env.FORGE_PREVIEW_URL||'http://127.0.0.1:8155/assets/ui/polish05-design-review/shared-preview/';
    await page.goto(base);
    await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('実際のパック画像'));
    const pixels=await page.evaluate(async()=>{
      const screen=document.getElementById('screen');
      const im=await loadImage('reference.png');
      const ref=document.createElement('canvas');ref.width=im.width;ref.height=im.height;
      const ctx=ref.getContext('2d');ctx.drawImage(im,0,0);
      const a=ctx.getImageData(0,0,ref.width,ref.height).data;
      const b=published.source.getContext('2d').getImageData(0,0,published.source.width,published.source.height).data;
      let count=0;for(let i=0;i<a.length;i+=4){if(a[i]!==b[i]||a[i+1]!==b[i+1]||a[i+2]!==b[i+2]||a[i+3]!==b[i+3])count++}
      return{width:published.source.width,height:published.source.height,changedPixels:count};
    });
    assert.equal(pixels.changedPixels,0,'Actual decoded pack source vs dense HTML capture pixels');
    const out=path.resolve(__dirname,'../../../.tools/shared-forge-preview');fs.mkdirSync(out,{recursive:true});
    await page.screenshot({path:path.join(out,'shared-preview.png'),fullPage:true});
    assert.equal(await page.locator('#frame option').count(),24);
    for(const f of ['head','legs-focused','weapon-focused','shortage','risk','maximum','confirm-weapon-focused']){
      await page.locator('#frame').selectOption(f);
      await page.waitForFunction(id=>active===id&&document.getElementById('status').textContent.startsWith('実際のパック画像'),f);
      assert.equal(await page.evaluate(()=>current().id),f);
    }
    await page.locator('#frame').selectOption('risk');
    await page.waitForFunction(()=>active==='risk'&&document.getElementById('status').textContent.startsWith('実際のパック画像'));
    async function clickAction(action){
      await page.waitForFunction(()=>canvas.dataset.frame===active&&canvas.dataset.mode===mode);
      const xy=await page.evaluate(a=>{
        const h=current().hits.find(h=>h.action===a);if(!h)throw Error('Missing hit: '+a);
        const r=canvas.getBoundingClientRect();const b=mode==='native'?nativeBox(current(),h):h;
        return{x:r.x+(b.x+b.w/2)*r.width/Number(canvas.dataset.logicalWidth),y:r.y+(b.y+b.h/2)*r.height/Number(canvas.dataset.logicalHeight)};
      },action);await page.mouse.click(xy.x,xy.y);
    }
    await clickAction('enhance');
    await page.waitForFunction(()=>active==='confirm-risk');
    await clickAction('cancel');
    await page.waitForFunction(()=>active==='risk');
    await page.locator('#frame').selectOption('weapon-focused');
    await page.waitForFunction(()=>active==='weapon-focused'&&document.getElementById('status').textContent.startsWith('実際のパック画像'));
    await clickAction('select:head');
    await page.waitForFunction(()=>active==='head-focused');
    await clickAction('catalyst');
    await page.waitForFunction(()=>active==='head');
    await page.locator('#native').click();
    await page.waitForFunction(()=>canvas.dataset.mode==='native'&&canvas.dataset.frame===active);
    assert.deepEqual(await page.evaluate(()=>[Number(canvas.dataset.logicalWidth),Number(canvas.dataset.logicalHeight)]),[800,480]);
    assert.ok(await page.evaluate(()=>canvas.width>800),'Logical game coordinates must not collapse source resolution');
    await clickAction('select:chest');
    await page.waitForFunction(()=>active==='chest');
    await page.locator('#raster').click();
    await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('実際のパック画像'));

    const resolutionChecks=[];
    for(const dpr of [1,2]){
      const display=await browser.newPage({viewport:{width:1304,height:930},deviceScaleFactor:dpr});
      display.on('pageerror',e=>errors.push(e.message));
      await display.goto(base);
      await display.waitForFunction(()=>document.querySelector('#screen').dataset.frame==='chest');
      for(const width of [1920,1304,900,390]){
        await display.setViewportSize({width,height:1080});
        for(const m of ['raster','native']){
          await display.locator('#'+m).click();
          await display.waitForFunction(m=>{
            const c=document.querySelector('#screen'),r=c.getBoundingClientRect();
            return c.dataset.mode===m && Math.abs(c.width-r.width*devicePixelRatio)<=1;
          },m);
          const check=await display.evaluate(()=>{
            const c=document.querySelector('#screen'),r=c.getBoundingClientRect();
            return{mode:c.dataset.mode,viewport:innerWidth,dpr:devicePixelRatio,width:c.width,height:c.height,cssWidth:r.width,cssHeight:r.height,sourceWidth:Number(c.dataset.sourceWidth),sourceHeight:Number(c.dataset.sourceHeight),rendering:getComputedStyle(c).imageRendering,overflow:document.documentElement.scrollWidth>innerWidth};
          });
          assert.ok(Math.abs(check.width-check.cssWidth*dpr)<=1,'Canvas backing must match display density');
          assert.ok(Math.abs(check.height-check.cssHeight*dpr)<=1);
          assert.equal(check.sourceWidth,3400);assert.equal(check.sourceHeight,1864);
          assert.equal(check.rendering,'auto','Text and glow must not use nearest-neighbour CSS resizing');
          assert.equal(check.overflow,false);resolutionChecks.push(check);
          if(width===1304)await display.screenshot({path:path.join(out,`after-${m}-1304-dpr${dpr}.png`),fullPage:true});
        }
      }
      await display.setViewportSize({width:1304,height:930});
      await display.locator('#raster').click();
      await display.waitForFunction(()=>document.querySelector('#screen').dataset.mode==='raster');
      await display.evaluate(()=>{const c=document.querySelector('#screen');window.comparisonPixels=c.getContext('2d').getImageData(0,0,c.width,c.height).data});
      await display.locator('#reference').click();
      await display.waitForFunction(()=>document.querySelector('#screen').dataset.mode==='reference');
      const changed=await display.evaluate(()=>{const c=document.querySelector('#screen'),a=window.comparisonPixels,b=c.getContext('2d').getImageData(0,0,c.width,c.height).data;let count=0;for(let i=0;i<a.length;i+=4)if(a[i]!==b[i]||a[i+1]!==b[i+1]||a[i+2]!==b[i+2]||a[i+3]!==b[i+3])count++;delete window.comparisonPixels;return count});
      assert.equal(changed,0,'Pack tiles and dense HTML source must remain identical after display resizing');
      await display.locator('#size').click();
      await display.waitForFunction(()=>document.querySelector('#screen').width===1700*devicePixelRatio);
      assert.equal(await display.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Original-size scroll must stay inside stage');
      // High-density buffers must not multiply the hit coordinates.
      await display.locator('#raster').click();
      await display.waitForFunction(()=>document.querySelector('#screen').dataset.mode==='raster');
      const gear=await display.evaluate(()=>{const c=document.querySelector('#screen'),r=c.getBoundingClientRect();const h=current().hits.find(h=>h.action==='select:head');return{x:r.x+h.x+5,y:r.y+h.y+5}});
      await display.mouse.click(gear.x,gear.y);
      await display.waitForFunction(()=>document.querySelector('#screen').dataset.frame==='head');
      await display.close();
    }
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({passed:true,...pixels,frameCount:24,browserPixelComparison:true,resolutionChecks,engineCapture:false},null,2));
    console.log('PASS: dense pack source pixel diff=0; displayed reference diff=0 at DPR 1/2; density/resizing gates in both modes at 4 widths; 24 frames; hit mapping, modal, original-size view, no overflow/errors.');
  }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
