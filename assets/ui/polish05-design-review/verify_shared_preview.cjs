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
      const b=screen.getContext('2d').getImageData(0,0,screen.width,screen.height).data;
      let count=0;for(let i=0;i<a.length;i+=4){if(a[i]!==b[i]||a[i+1]!==b[i+1]||a[i+2]!==b[i+2]||a[i+3]!==b[i+3])count++}
      return{width:screen.width,height:screen.height,changedPixels:count};
    });
    assert.equal(pixels.changedPixels,0,'Actual browser canvas vs original captured HTML pixels');
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
        return{x:r.x+(b.x+b.w/2)*r.width/canvas.width,y:r.y+(b.y+b.h/2)*r.height/canvas.height};
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
    assert.deepEqual(await page.evaluate(()=>[canvas.width,canvas.height]),[800,480]);
    await clickAction('select:chest');
    await page.waitForFunction(()=>active==='chest');
    await page.locator('#raster').click();
    await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('実際のパック画像'));
    for(const width of [1920,900,390]){await page.setViewportSize({width,height:1080});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)}
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({passed:true,...pixels,frameCount:24,browserPixelComparison:true,engineCapture:false},null,2));
    console.log('PASS: actual canvas pixel diff=0; 24 frames; hit mapping, conditional states, modal, native coordinate mode, no overflow/errors.');
  }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
