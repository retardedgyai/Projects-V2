"""Compact shipped native models for the isolated Ice Garden bloom prototype.

The original garden assets stay intact. No full pack or other skill is rebuilt.
Only five crowns animate; ordinary cells reuse one static model, while expiry
and contact share a small set of native models.
"""
from pathlib import Path
import io,json,zipfile
from PIL import Image,ImageDraw
from build_ice_garden import OFFSETS,garden_art
from ice_garden_visual_study import box,prism,PALETTE,rotate

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack'
ASSETS=PACK/'assets/projects'
PREFIX='combat_vfx/garden_bloom'
CROWNS={(0,2),(-2,0),(1,-2),(2,1),(-1,1)}

def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')

def floor():
    e=box(0,8,0,16,.5,16)
    e['faces']['up']={'texture':'#0','uv':[0,0,16,16]}
    return e

def elements(x,z,stage=3):
    result=[floor()]
    if (x,z)==(0,0):return result
    growth={1:.24,2:.65,3:1,4:.75,5:.48}[stage]
    if (x,z) not in CROWNS:
        return result+[box(8,8.55,7,2.6,2.5,2.6,0,22.5 if (x+z)%2 else -22.5)]
    core=prism(5.8,5.8,4.4,9.4*growth)
    if stage>=4:core=core[:1] # The tip breaks off at the warning instead of the whole field pulsing.
    result+=core
    result+=[box(2.6,8.55,6.2,3.9,8.3*growth+.08,3.7,0,22.5),
             box(9.6,8.55,4.8,3.8,6.8*growth+.08,3.7,0,-22.5),
             box(6.6,8.55,2.2,3.7,7.4*growth+.08,3.7,0,22.5,'x')]
    if stage>=4:result.append(box(5,8.65,10.5,2.2,.8,2.4,0,-22.5))
    return result

def collapse(stage):
    if stage==2:return []
    result=[]
    for i in range(5):
        import math
        a=i*2*math.pi/5+.3;r=2.5 if stage==0 else 4.3
        result.append(box(7+math.cos(a)*r,9.8 if stage==0 else 8.25,7+math.sin(a)*r,
                          1.7,.5 if stage==0 else .15,2.2,5,22.5 if i%2 else -22.5))
    return result

def contact(stage):
    if stage==0:
        return prism(6,6,2.5,7,-22.5)+prism(9,8,2,5,22.5)+[box(3.5,8.5,5,4,.7,6,0,22.5,'x')]
    import math
    result=[]
    for i in range(5):
        a=i*2*math.pi/5+.3;r=3.7 if stage==1 else 5.1
        result.append(box(7.2+math.cos(a)*r,12.5+(i%2) if stage==1 else 8.5,7.2+math.sin(a)*r,
                          1.5,.8 if stage==1 else .15,1.8,0 if stage==1 else 5,22.5 if i%2 else -22.5))
    return result

def floor_art():
    image=garden_art(2)
    inks={'#253657':'#203641','#426b95':'#2b4651','#65adb9':'#4a7180','#a5dfe4':'#608d99'}
    table={tuple(bytes.fromhex(a[1:])):tuple(bytes.fromhex(b[1:])) for a,b in inks.items()}
    for y in range(80):
        for x in range(80):
            r,g,b,a=image.getpixel((x,y));image.putpixel((x,y),(*table.get((r,g,b),(r,g,b)),a))
    d=ImageDraw.Draw(image)
    for x,z in OFFSETS:
        xx,yy=(x+2)*16,(z+2)*16
        for neighbour,line in [((x-1,z),[(xx,yy),(xx,yy+15)]),((x+1,z),[(xx+15,yy),(xx+15,yy+15)]),
                               ((x,z-1),[(xx,yy),(xx+15,yy)]),((x,z+1),[(xx,yy+15),(xx+15,yy+15)])]:
            if neighbour not in OFFSETS:d.line(line,fill='#b5d7d5')
    return image

def build():
    palette=Image.new('RGBA',(16,16));d=ImageDraw.Draw(palette)
    for i,ink in enumerate(PALETTE):d.rectangle((i,0,i,15),fill=ink)
    texture_dir=ASSETS/f'textures/{PREFIX}';texture_dir.mkdir(parents=True,exist_ok=True)
    palette.save(texture_dir/'facets.png')
    art=floor_art();authored=[]
    for x,z in OFFSETS:
        tile=f'tile_{x+2}_{z+2}'
        art.crop(((x+2)*16,(z+2)*16,(x+3)*16,(z+3)*16)).save(texture_dir/f'{tile}.png')
        for stage in ([1,2,3,4,5] if (x,z) in CROWNS else [3]):
            name=f'{tile}_{stage}' if (x,z) in CROWNS else tile
            authored.append((name,elements(x,z,stage),tile))
    authored += [(f'collapse_{stage}',collapse(stage),None) for stage in range(3)]
    authored += [(f'contact_{stage}',contact(stage),None) for stage in range(3)]
    for name,geometry,tile in authored:
        textures={'1':f'projects:{PREFIX}/facets'}
        if tile:textures['0']=f'projects:{PREFIX}/{tile}'
        model={'ambientocclusion':False,'textures':textures,'elements':geometry}
        write_json(ASSETS/f'models/{PREFIX}/{name}.json',model)
        write_json(ASSETS/f'items/{PREFIX}/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:{PREFIX}/{name}'}})
    # Index only this effect, retaining every existing asset and every other skill.
    index=set((PACK/'index.txt').read_text(encoding='utf-8').splitlines())
    for directory in ['models','items','textures']:
        index.update(p.relative_to(PACK).as_posix() for p in (ASSETS/f'{directory}/{PREFIX}').rglob('*') if p.is_file())
    (PACK/'index.txt').write_text('\n'.join(sorted(index))+'\n',encoding='utf-8')
    files=[p for directory in ['models','items','textures'] for p in (ASSETS/f'{directory}/{PREFIX}').rglob('*') if p.is_file()]
    compressed=io.BytesIO()
    with zipfile.ZipFile(compressed,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p in files:z.write(p,p.relative_to(PACK).as_posix())
    metrics={'runtime_new_models':len(authored),'new_textures':22,'new_assets':len(files),
             'additional_uncompressed_asset_bytes':sum(p.stat().st_size for p in files),
             'additional_zipped_asset_bytes':len(compressed.getvalue()),
             'original_170_models_retained':True,'mature_cuboids':sum(len(elements(x,z)) for x,z in OFFSETS),
             'field_displays':21,'contact_displays_per_event':1,
             'study_models_before':473,'study_mature_cuboids_before':101,
             'original_mature_cuboids':45,'actual_fps':'NOT MEASURED'}
    (ROOT/'.tools').mkdir(exist_ok=True)
    write_json(ROOT/'.tools/ice-garden-bloom-assets.json',metrics)
    print(json.dumps(metrics,indent=2))

if __name__=='__main__':build()
