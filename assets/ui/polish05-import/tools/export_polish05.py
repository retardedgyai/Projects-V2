#!/usr/bin/env python3
"""Export the approved, self-contained Polish05 HTML without redesigning it.

No network requests and no write to a Minecraft installation or repository.
Requires Python 3.10+, Pillow and Playwright + a locally installed Chromium.
Run: python tools/export_polish05.py --html reference/ProjectS_UI_Polish05_Workbench.html --output .
"""
from __future__ import annotations
import argparse, asyncio, base64, hashlib, io, json, os, re, shutil
from pathlib import Path
from PIL import Image
from playwright.async_api import async_playwright

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def save_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def embedded(source: str, name: str) -> dict:
    key = 'const ' + name + '='
    if key not in source:
        raise ValueError('Missing embedded asset map: ' + name)
    result, _ = json.JSONDecoder().raw_decode(source.split(key, 1)[1].lstrip())
    if not isinstance(result, dict):
        raise ValueError('Asset map must be an object')
    return result

RECTS = r'''() => {
 const st=document.querySelector('#stage').getBoundingClientRect();
 const scale=st.width/1440;
 const rect=e=>{const r=e.getBoundingClientRect();return {x:(r.x-st.x)/scale,y:(r.y-st.y)/scale,w:r.width/scale,h:r.height/scale};};
 const visible=e=>{const r=e.getBoundingClientRect();const c=getComputedStyle(e);return r.width>0&&r.height>0&&c.visibility!=='hidden'&&c.display!=='none';};
 return {view:S.view, state:JSON.parse(JSON.stringify(S)), selected:JSON.parse(JSON.stringify(selected())),
  costs:costs(), chance:chance(), power:power(selected()), prep:[prepInfo('ore'),prepInfo('crystal')],
  modalOpen:!document.querySelector('#modal').classList.contains('hidden'),
  controls:[...document.querySelectorAll('button,input,select')].filter(visible).map(e=>({
    selector:e.id?'#'+e.id:null,tag:e.tagName.toLowerCase(),text:e.textContent.trim(),
    aria:e.getAttribute('aria-label'),disabled:!!e.disabled, pressed:e.getAttribute('aria-pressed'),
    receivesPointer:(()=>{const r=e.getBoundingClientRect();const at=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return !!at&&(at===e||e.contains(at));})(),
    dataset:{...e.dataset},rect:rect(e)})),
  nodes:[...document.querySelectorAll('#window [id]')].filter(visible).map(e=>({id:e.id,tag:e.tagName.toLowerCase(),rect:rect(e),
    text:['IMG','CANVAS'].includes(e.tagName)?null:e.innerText?.slice(0,200),
    image:e.dataset.img||null,style:{font:getComputedStyle(e).font,color:getComputedStyle(e).color,
    filter:getComputedStyle(e).filter,background:getComputedStyle(e).background}})),
  frames:Object.fromEntries(['#window','.content','.inventory-side','.hero-column','.result-column','#weapon-stage','#hero-weapon','#weapon-glow','.backlight','.weapon-stage .forge-art'].map(sel=>{
    const e=document.querySelector(sel);return [sel,e&&visible(e)?rect(e):null];})),
  cssVariables:Object.fromEntries([...getComputedStyle(document.documentElement)].filter(x=>x.startsWith('--')).map(x=>[x,getComputedStyle(document.documentElement).getPropertyValue(x).trim()]))};
}'''

async def render_exports(html: Path, root: Path, browser_path: str | None) -> None:
    source=html.read_text(encoding='utf-8')
    asset_manifest={'source':html.name,'source_sha256':digest(html.read_bytes()),'images':{},'audio':{}}
    for map_name, folder in [('ASSETS','images'),('MC_AUDIO','audio')]:
        for key,value in embedded(source,map_name).items():
            if not re.fullmatch(r'[a-z0-9_]+',key):
                raise ValueError('Unexpected asset ID')
            data=base64.b64decode(value.split(',',1)[1] if value.startswith('data:') else value,validate=True)
            ext='.png' if folder=='images' else '.ogg'
            path=root/'assets'/folder/(key+ext);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            entry={'path':str(path.relative_to(root)),'sha256':digest(data),'bytes':len(data)}
            if folder=='images':
                with Image.open(io.BytesIO(data)) as im:entry.update(width=im.width,height=im.height,mode=im.mode)
            asset_manifest['images' if folder=='images' else 'audio'][key]=entry
    save_json(root/'assets'/'manifest.json',asset_manifest)
    out=root/'reference'/'screens';out.mkdir(parents=True,exist_ok=True)
    scene_dir=root/'layout';scene_dir.mkdir(parents=True,exist_ok=True)
    async with async_playwright() as pw:
        launch={'headless':True}
        if browser_path:launch['executable_path']=browser_path
        browser=await pw.chromium.launch(**launch)
        page=await browser.new_page(viewport={'width':1440,'height':920},device_scale_factor=1)
        errors=[]; network=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        async def deny(route):
            network.append(route.request.url)
            await route.abort()
        await page.route('**/*',deny)
        await page.emulate_media(reduced_motion='reduce')
        # Render authorized file bytes directly, rather than navigate file:// or a local server.
        await page.set_content(source,wait_until='load')
        await page.evaluate('document.fonts.ready')
        await page.wait_for_function("document.querySelector('#gear-list').children.length===2")
        await page.evaluate("window.ProjectSAudio.setEnabled(false); S.motion=false; render();")
        await page.add_style_tag(content='*{transition:none!important;scroll-behavior:auto!important} #embers{visibility:hidden!important}')
        await page.mouse.move(1435,915)
        async def capture(name, js=None):
            if js:await page.evaluate(js)
            await page.wait_for_timeout(65)
            await page.mouse.move(1435,915)
            await page.locator('#stage').screenshot(path=str(out/(name+'.png')),animations='disabled')
            snapshot=await page.evaluate(RECTS)
            save_json(scene_dir/(name+'.json'),snapshot)
        async def reset(js=''):
            await page.evaluate("dismiss(); busy=false; S=initial(); S.motion=false; open=true; preparationOrigin=null; $('#window').classList.remove('hidden'); $('.top-title').classList.remove('hidden'); $('#reopen').classList.add('hidden'); $('#weapon-stage').classList.remove('striking','success','result-warm'); $('#toast').classList.remove('show'); "+js+"; render();")
        await capture('forge_initial')
        await capture('forge_ash',"selectGear('ash')")
        await reset('S.catalyst=true')
        await capture('forge_catalyst')
        await reset()
        await capture('forge_confirm','confirmEnhance()')
        await page.evaluate('dismiss()')
        await page.evaluate('enhance()')
        await page.wait_for_timeout(120)
        await capture('forge_success',"$('#toast').classList.remove('show')")
        await reset("S.outcome='fail'")
        await page.evaluate('enhance()');await page.wait_for_timeout(120)
        await capture('forge_fail',"$('#toast').classList.remove('show')")
        await reset('S.materials.ore=2; S.materials.crystal=0')
        await capture('forge_shortage')
        await page.locator('[data-prepare-material="ore"]').click()
        await capture('refine_from_shortage')
        await capture('refine_confirm','confirmRefine()')
        await reset('S.gears[0].level=30')
        await capture('forge_max')
        await reset("S.view='bag'")
        await capture('inventory')
        await capture('inventory_compare','compare()')
        await reset("S.view='refine'")
        await capture('refine_ore')
        await capture('refine_crystal',"S.recipe='crystal';render()")
        await reset()
        # Exact CSS-derived environment/Glow plates. The weapon stays a separate sprite.
        layers=root/'assets'/'plates';layers.mkdir(parents=True,exist_ok=True)
        composite=await page.add_style_tag(content='#hero-weapon,#embers{visibility:hidden!important}')
        await page.locator('#weapon-stage').screenshot(path=str(layers/'forge_environment_with_glow.png'),animations='disabled')
        await composite.evaluate('(e)=>e.remove()')
        hidden=await page.add_style_tag(content='#hero-weapon,#weapon-glow,#embers{visibility:hidden!important}')
        await page.locator('#weapon-stage').screenshot(path=str(layers/'forge_environment_plate.png'),animations='disabled')
        await hidden.evaluate('(e)=>e.remove()')
        await page.locator('#window').screenshot(path=str(layers/'forge_initial_reference_plate.png'),animations='disabled')
        # Sprite-ready isolated layers at original CSS sizes; not a redraw.
        sprites=root/'assets'/'layers';sprites.mkdir(parents=True,exist_ok=True)
        layer_records={}
        for selector,name in [('.window-head','window_header'),('.window-foot','window_footer'),('.prep-recipe','replenish_row'),('#enhance-btn','enhance_button')]:
            el=page.locator(selector).first
            box=await el.bounding_box()
            if not box:continue
            # Component screenshots contain their original backing surface; hover variants can be regenerated.
            await el.screenshot(path=str(sprites/(name+'.png')),animations='disabled')
            layer_records[name]={'selector':selector,'bounds':box,'alpha_note':'Rendered component crop; do not overlay opaque backing on unrelated layers.'}
        for selector,name in [('#weapon-glow','weapon_glow_isolated'),('.backlight','backlight_isolated')]:
            el=page.locator(selector).first
            box=await el.bounding_box()
            if not box:continue
            pad=32
            bounds={'x':max(0,box['x']-pad),'y':max(0,box['y']-pad),'width':box['width']+pad*2,'height':box['height']+pad*2}
            hide=await page.add_style_tag(content='body *{visibility:hidden!important} '+selector+'{visibility:visible!important} html,body,#stage{background:transparent!important}')
            await page.screenshot(path=str(sprites/(name+'.png')),clip=bounds,omit_background=True,animations='disabled')
            await hide.evaluate('(e)=>e.remove()')
            layer_records[name]={'selector':selector,'bounds':bounds,'padding':pad,'alpha_note':'isolated transparent RGBA; raw sharp weapon is a separate layer'}
        hide=await page.add_style_tag(content='#forge-view,#bag-view,#refine-view,.wallet,.tab,.workshop-stamp,.close-window,.window-foot>*{visibility:hidden!important}')
        await page.locator('#window').screenshot(path=str(layers/'window_chrome.png'),animations='disabled')
        await hide.evaluate('(e)=>e.remove()')
        save_json(root/'assets'/'layers'/'layout.json',layer_records)
        font_info=await page.evaluate("({body:getComputedStyle(document.body).fontFamily,serif:getComputedStyle(document.querySelector('.serif')).fontFamily,devicePixelRatio:devicePixelRatio})")
        save_json(root/'verification'/'browser_export.json',{'pageErrors':errors,'attemptedExternalRequests':network,'renderedIn':'Chromium, set_content of original HTML','notMinecraftCapture':True,'fontEnvironment':font_info,'motion':'disabled for stable golden images','count':len(list(out.glob('*.png')))})
        await browser.close()
        if errors:raise RuntimeError('Browser errors: '+str(errors))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--html',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--chromium',default=shutil.which('chromium') or shutil.which('google-chrome'))
    args=p.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    asyncio.run(render_exports(args.html,args.output,args.chromium))
    print('Export complete. No Minecraft runtime, server or repository was modified.')
if __name__=='__main__':main()
