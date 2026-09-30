"""Share opaque forge pixels between a browser canvas and UiRenderer bitmap glyphs.

These finite fixtures prove the raster presentation, not a live account renderer.
"""
from pathlib import Path
from PIL import Image
import hashlib, json, zipfile, math

BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
CAP=ROOT/'.tools/forge-shared-capture'
OUT=BASE/'shared-preview'
NS='projects_forge_calibration'

def digest(data): return hashlib.sha256(data).hexdigest()

def main():
    OUT.mkdir(exist_ok=True)
    tex=OUT/f'pack/assets/{NS}/textures/tiles'
    font=OUT/f'pack/assets/{NS}/font'
    tex.mkdir(parents=True,exist_ok=True);font.mkdir(parents=True,exist_ok=True)
    captures=json.loads((CAP/'captures.json').read_text(encoding='utf8'))
    sprites={};providers=[];frames=[];checks=[]
    def register(tile, transparent=False):
        tile=tile.copy()
        # Bitmap fonts derive advance from nonzero alpha. Retain the entire box,
        # including transparent margins, so UiRenderer centres the glyph correctly.
        if transparent and tile.getpixel((tile.width-1,tile.height-1))[3]==0:
            tile.putpixel((tile.width-1,tile.height-1),(0,0,0,1))
        key=digest(bytes(str(tile.size),'ascii')+tile.tobytes())[:24]
        if key not in sprites:
            ch=chr(0xF0000+len(sprites));name=key+'.png';tile.save(tex/name)
            src=f'{NS}:tiles/{name}'
            sprites[key]={'char':ch,'font':f'{NS}:plates','width':tile.width,'height':tile.height,'source':src,'path':f'pack/assets/{NS}/textures/tiles/{name}'}
            providers.append({'type':'bitmap','file':src,'height':tile.height,'ascent':tile.height,'chars':[ch]})
        return key
    def split(image, transparent=False):
        tiles=[]
        for y in range(0,image.height,256):
            for x in range(0,image.width,256):
                tile=image.crop((x,y,min(x+256,image.width),min(y+256,image.height)))
                if transparent and not tile.getchannel('A').getbbox():continue
                tiles.append({'sprite':register(tile,transparent),'x':x,'y':y,'w':tile.width,'h':tile.height})
        return tiles
    # Minecraft text shaders discard faint alpha. Bake the breathing light against
    # its fixed stage instead of submitting a transparent gradient to that shader.
    # All twelve frames retain 2x density, constant bounds, and the original CSS glow.
    glow=Image.open(CAP/'glow.png').convert('RGBA')
    light_cache={}
    for frame in captures['frames']:
        image=Image.open(CAP/(frame['id']+'.png')).convert('RGBA')
        assert image.size==(frame['width']*frame['rasterScale'],frame['height']*frame['rasterScale'])
        assert image.getchannel('A').getextrema()==(255,255), 'Flatten glow/text against backing before glyph transport'
        tiles=split(image);rebuilt=Image.new('RGBA',image.size)
        for tile in tiles:
            # Re-read the EXACT pack file; a source image alone is insufficient evidence.
            decoded=Image.open(OUT/sprites[tile['sprite']]['path']).convert('RGBA')
            rebuilt.paste(decoded,(tile['x'],tile['y']))
        assert rebuilt.tobytes()==image.tobytes(), frame['id']+' pack pixels changed'
        dynamic=frame.get('dynamic')
        if dynamic:
            stage=dynamic['stage'];light=dynamic['glow'];density=frame['rasterScale']
            x=math.floor(stage['x']*density);y=math.floor(stage['y']*density)
            right=math.ceil((stage['x']+stage['w'])*density);bottom=math.ceil((stage['y']+stage['h'])*density)
            backing=image.crop((x,y,right,bottom))
            light_key=digest(backing.tobytes())
            if light_key not in light_cache:
                glow_frames=[]
                glow_box={'x':x/density,'y':y/density,'w':backing.width/density,'h':backing.height/density}
                for step in range(12):
                    variant=glow.copy();variant.putalpha(glow.getchannel('A').point(lambda a:round(a*(.86+.14*step/11))))
                    composite=backing.copy()
                    composite.alpha_composite(variant,(round(light['x']*density-x),round(light['y']*density-y)))
                    assert composite.getchannel('A').getextrema()==(255,255), 'Breathing light must bypass Minecraft faint-alpha discard'
                    glow_frames.append({'tiles':split(composite)})
                light_cache[light_key]={**glow_box,'frames':glow_frames}
            dynamic['glow']=light_cache[light_key]
            hero=dynamic['hero'];layer=Image.open(CAP/hero['file']).convert('RGBA')
            assert layer.getchannel('A').getextrema()==(0,255), 'Hero must be a transparent cutout'
            assert layer.size==(hero['w']*frame['rasterScale'],hero['h']*frame['rasterScale'])
            hero['tiles']=split(layer,True)
        frames.append({**frame,'tiles':tiles,'rgbaSha256':digest(image.tobytes())})
        checks.append({'frame':frame['id'],'changedPixels':0,'rgbaSha256':digest(rebuilt.tobytes())})
        if frame['id']=='chest':
            image.save(OUT/'reference.png')
    (font/'plates.json').write_text(json.dumps({'providers':providers},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (OUT/'pack/pack.mcmeta').write_text(json.dumps({'pack':{'description':'ProjectS forge / raster calibration fixtures (not live account UI)','min_format':[88,0],'max_format':[88,0]}},indent=2)+'\n',encoding='utf8')
    manifest={'schema':3,'kind':'forge-raster-calibration','nativeEngineCapture':False,'referenceViewport':captures['referenceViewport'],'sceneWidth':800,'sceneHeight':480,'opacity':'opaque fixed backing and twelve full-density breathing-light stages; transparent hero has alpha-1 advance sentinel at bottom-right','tileCoordinates':'physical raster pixels; hits and frame bounds are logical CSS pixels','motion':captures['motion'],'sprites':sprites,'frames':frames}
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (OUT/'verification.json').write_text(json.dumps({'passed':True,'checks':checks,'uniqueGlyphs':len(sprites),'checksActualPackFiles':True,'nativeVisualTested':False},indent=2)+'\n',encoding='utf8')
    # Only obsolete generated PNGs inside this exact output directory are removed.
    wanted={s['path'].rsplit('/',1)[1] for s in sprites.values()}
    for p in tex.glob('*.png'):
        assert p.resolve().parent==tex.resolve()
        if p.name not in wanted:p.unlink()
    with zipfile.ZipFile(OUT/'Forge_Calibration_26_2.zip','w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted((OUT/'pack').rglob('*')):
            if p.is_file():z.write(p,p.relative_to(OUT/'pack'))
    print(f'PASS: {len(frames)} fixtures, {len(sprites)} shared glyphs; 0 changed RGBA pixels after reading pack textures')

if __name__=='__main__':main()
