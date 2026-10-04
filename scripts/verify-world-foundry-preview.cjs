// Opens only an owned, headless Edge instance. It never switches or stops the user's browser/game.
const {chromium}=require('C:/Users/xgaiz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const path=require('path'),fs=require('fs'),assert=require('assert');
const out=path.resolve(__dirname,'../.tools/world-foundry-preview');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true,args:['--disable-extensions']});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:980}}),errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  await page.goto('file:///'+path.join(out,'ProjectS-World-Foundry-Preview.html').replaceAll('\\','/'));
  await page.waitForFunction(()=>document.getElementById('scene').complete&&document.getElementById('scene').naturalWidth>0);
  assert((await page.locator('#label').textContent()).includes('設備'));
  await page.selectOption('#shape','plate');
  await page.locator('#progress').fill('6');
  await page.locator('#progress').dispatchEvent('input');
  assert((await page.locator('#label').textContent()).includes('注ぐ'));
  await page.screenshot({path:path.join(out,'ProjectS-Foundry-HTML-Desktop.png'),fullPage:true});
  await page.click('#next');assert((await page.locator('#label').textContent()).includes('冷却'));
  await page.click('#previous');assert((await page.locator('#label').textContent()).includes('注ぐ'));
  await page.click('#reset');assert((await page.locator('#progress').inputValue())==='0');
  await page.click('#play');await page.waitForFunction(()=>Number(document.getElementById('progress').value)>0);
  await page.click('#play');assert((await page.locator('#play').textContent())==='自動再生');
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:path.join(out,'ProjectS-Foundry-HTML-Mobile.png'),fullPage:true});
  const dimensions=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,viewport:window.innerWidth,images:frames.rod.length+frames.plate.length}));
  assert(dimensions.scroll<=dimensions.viewport,'Mobile must not scroll horizontally');assert.equal(dimensions.images,28);
  assert.deepStrictEqual(errors,[]);
  fs.writeFileSync(path.join(out,'html-qa.json'),JSON.stringify({headlessBrowser:'owned Edge',interactionChecks:8,mobileWidth:390,desktopWidth:1440,embeddedFrames:28,pageErrors:errors},null,2));
  console.log('HTML desktop/mobile and 8 interaction checks passed; owned browser closed');
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
