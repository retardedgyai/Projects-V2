"""Share opaque forge pixels between a browser canvas and UiRenderer bitmap glyphs.

These finite fixtures prove the raster presentation, not a live account renderer.
"""
from pathlib import Path
from PIL import Image
import hashlib, json, zipfile

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
    for frame in captures['frames']:
        image=Image.open(CAP/(frame['id']+'.png')).convert('RGBA')
        assert image.size==(frame['width']*frame['rasterScale'],frame['height']*frame['rasterScale'])
        assert image.getchannel('A').getextrema()==(255,255), 'Flatten glow/text against backing before glyph transport'
        tiles=[];rebuilt=Image.new('RGBA',image.size)
        for y in range(0,image.height,256):
            for x in range(0,image.width,256):
                tile=image.crop((x,y,min(x+256,image.width),min(y+256,image.height)))
                key=digest(bytes(str(tile.size),'ascii')+tile.tobytes())[:24]
                if key not in sprites:
                    ch=chr(0xF0000+len(sprites))
                    name=key+'.png';tile.save(tex/name)
                    src=f'{NS}:tiles/{name}'
                    sprites[key]={'char':ch,'font':f'{NS}:plates','width':tile.width,'height':tile.height,'source':src,'path':f'pack/assets/{NS}/textures/tiles/{name}'}
                    providers.append({'type':'bitmap','file':src,'height':tile.height,'ascent':tile.height,'chars':[ch]})
                tiles.append({'sprite':key,'x':x,'y':y,'w':tile.width,'h':tile.height})
                # Re-read the EXACT pack file; a source image alone is insufficient evidence.
                decoded=Image.open(OUT/sprites[key]['path']).convert('RGBA')
                rebuilt.paste(decoded,(x,y))
        assert rebuilt.tobytes()==image.tobytes(), frame['id']+' pack pixels changed'
        frames.append({**frame,'tiles':tiles,'rgbaSha256':digest(image.tobytes())})
        checks.append({'frame':frame['id'],'changedPixels':0,'rgbaSha256':digest(rebuilt.tobytes())})
        if frame['id']=='chest':
            image.save(OUT/'reference.png')
    (font/'plates.json').write_text(json.dumps({'providers':providers},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (OUT/'pack/pack.mcmeta').write_text(json.dumps({'pack':{'description':'ProjectS forge / raster calibration fixtures (not live account UI)','min_format':[88,0],'max_format':[88,0]}},indent=2)+'\n',encoding='utf8')
    manifest={'schema':2,'kind':'forge-raster-calibration','nativeEngineCapture':False,'referenceViewport':captures['referenceViewport'],'sceneWidth':800,'sceneHeight':480,'opacity':'fully opaque backing; CSS glow and fixed text are already composited','tileCoordinates':'physical raster pixels; hits and frame bounds are logical CSS pixels','sprites':sprites,'frames':frames}
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
