"""Native pixel plates and short crystals for one Ice Garden; no other art is rebuilt."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
INK = ['#253657', '#426b95', '#65adb9', '#a5dfe4', '#e7fcf5']

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, separators=(',', ':'))+'\n', encoding='utf-8')

def texture(stage, preparing=False):
    im = Image.new('RGBA', (16,16)); d = ImageDraw.Draw(im)
    if preparing:
        for xy in [(0,0,5,0),(0,0,0,5),(10,15,15,15),(15,10,15,15),(7,7,8,8)]:
            d.rectangle(xy, fill=INK[2])
        return im
    d.polygon([(0,0),(15,0),(15,15),(0,15)], fill=INK[1 if stage<2 else 0])
    d.polygon([(0,0),(15,0),(13,3),(3,3),(3,12),(0,15)], fill=INK[3 if stage<3 else 1])
    d.polygon([(4,4),(14,2),(12,11),(3,14)], fill=INK[2])
    d.line([(0,0),(15,0),(15,15)], fill=INK[4 if stage<3 else 2], width=1)
    d.line([(0,15),(0,0)], fill=INK[3 if stage<3 else 1], width=1)
    # A single branching seam rather than texture noise.
    d.line([(3,14),(7,9),(7,6),(12,2)], fill=INK[1], width=1)
    d.line([(7,9),(12,10),(15,14)], fill=INK[1], width=1)
    if stage>=4:
        # Active cyan boundaries disappear at expiry; neutral chips are only the ending.
        for y in range(16):
            for x in range(16):
                r,g,b,a=im.getpixel((x,y));im.putpixel((x,y),(g//2,g//2+8,g//2+15,a))
        # Deliberately shaped shards separate; the ice is gone when damage ends.
        d.polygon([(6,0),(9,0),(8,16),(5,16)], fill=(0,0,0,0))
        for y in range(16):
            for x in range(16):
                if x in (0,15) or y in (0,15) or ((x//3)*3+(y//3)*5)%5 < min(5,stage-2): im.putpixel((x,y),(0,0,0,0))
    return im

OFFSETS=[(x,z) for x in range(-2,3) for z in range(-2,3) if x*x+z*z<=5]

def garden_art(stage):
    """One connected composition; slicing it does not add a checkerboard of inner borders."""
    im=Image.new('RGBA',(80,80));d=ImageDraw.Draw(im)
    for x,z in OFFSETS:
        xx,yy=(x+2)*16,(z+2)*16
        d.rectangle((xx,yy,xx+15,yy+15),fill=INK[0])
    # Large material facets, deliberately independent of the tile grid.
    for poly,ink in [([(16,16),(41,3),(64,16),(43,34),(20,37)],1),
        ([(4,35),(25,26),(41,40),(26,63),(3,48)],1),
        ([(43,36),(70,20),(78,47),(58,66),(37,57)],1),
        ([(21,61),(41,52),(66,69),(57,77),(22,76)],1)]:
        d.polygon(poly,fill=INK[ink])
    # Broad roots branch from the central seam; pale rims expose their direction.
    paths=[[(39,40),(31,30),(29,22),(20,14),(18,2)],
        [(39,40),(49,32),(53,21),(65,14),(76,23)],
        [(39,40),(50,44),(59,52),(72,55),(77,48)],
        [(39,40),(34,52),(37,61),(29,72),(28,77)],
        [(39,40),(26,43),(20,53),(6,55),(2,49)]]
    for path in paths:
        d.line(path,fill=INK[2 if stage==1 else 3 if stage==2 else 1],width=4)
        d.line([(x-1,y-1) for x,y in path],fill=INK[3 if stage<3 else 2],width=1)
    for path in [[(31,30),(17,29),(11,34)],[(50,44),(57,38),(67,36)],[(34,52),(25,59),(18,59)]]:
        d.line(path,fill=INK[2],width=2)
    for x,z in OFFSETS:
        xx,yy=(x+2)*16,(z+2)*16
        edge=INK[3 if stage<3 else 1]
        if (x-1,z) not in OFFSETS:d.line([(xx,yy),(xx,yy+15)],fill=edge,width=1)
        if (x+1,z) not in OFFSETS:d.line([(xx+15,yy),(xx+15,yy+15)],fill=edge,width=1)
        if (x,z-1) not in OFFSETS:d.line([(xx,yy),(xx+15,yy)],fill=edge,width=1)
        if (x,z+1) not in OFFSETS:d.line([(xx,yy+15),(xx+15,yy+15)],fill=edge,width=1)
    if stage>=4:
        for y in range(80):
            for x in range(80):
                r,g,b,a=im.getpixel((x,y))
                drop=((x//4)*3+(y//4)*2)%5<min(5,stage-1)
                im.putpixel((x,y),(g//2,g//2+8,g//2+15,0 if drop else a))
    # Clip the composition to the same 21 supported floor squares.
    mask=Image.new('L',(80,80));md=ImageDraw.Draw(mask)
    for x,z in OFFSETS:md.rectangle(((x+2)*16,(z+2)*16,(x+2)*16+15,(z+2)*16+15),fill=255)
    for y in range(80):
        for x in range(80):
            if not mask.getpixel((x,y)):im.putpixel((x,y),(0,0,0,0))
    return im

def slab():
    return {'from':[0,8,0], 'to':[16,8.35,16], 'shade':False,
            'faces':{side:{'texture':'#0','uv':[0,0,16,16]} for side in ['up','down','north','south','east','west']}}

def crystal(x,z,h,turn=22.5):
    return {'from':[x,8.4,z], 'to':[x+2,8.4+h,z+2],
            'rotation':{'origin':[x+1,8.4,z+1],'axis':'z','angle':turn,'rescale':False},
            'faces':{side:{'texture':'#1','uv':uv} for side,uv in {
                'up':[0,0,2,2],'down':[0,0,2,2],'north':[0,2,2,12],
                'south':[2,2,4,12],'east':[4,2,6,12],'west':[6,2,8,12]}.items()}}

def build():
    assets=PACK/'assets/projects'
    authored=[]
    for name in ['prepare','contact']:
        authored.append((name,texture(2,name=='prepare'),[slab()] if name=='prepare' else [crystal(3,4,7),crystal(9,6,5,-22.5)]))
    for stage in range(1,9):
        master=garden_art(stage)
        for x,z in OFFSETS:
            image=master.crop(((x+2)*16,(z+2)*16,(x+3)*16,(z+3)*16))
            elements=[slab()]
            if (abs(x)==2 or abs(z)==2) and stage<4:
                h=(9 if x==0 or z==0 else 5) * (0.45 if stage==1 else 1.0 if stage==2 else .3)
                elements += [crystal(3,4,h),crystal(9,8,h*.6,-22.5)]
            authored.append((f'tile_{x+2}_{z+2}_{stage}',image,elements))
    crystal_image=Image.new('RGBA',(16,16));cd=ImageDraw.Draw(crystal_image)
    for i in range(4):cd.rectangle((i*4,0,i*4+3,15),fill=INK[4-i])
    crystal_path=assets/'textures/combat_vfx/garden/crystal.png'
    crystal_path.parent.mkdir(parents=True,exist_ok=True);crystal_image.save(crystal_path)
    for name,image,elements in authored:
        key=f'combat_vfx/garden/{name}'
        path=assets/f'textures/{key}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
        write_json(assets/f'models/{key}.json',{'ambientocclusion':False,'textures':{'0':f'projects:{key}','1':'projects:combat_vfx/garden/crystal'},'elements':elements})
        write_json(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':f'projects:{key}'}})
    obsolete={f'assets/projects/{folder}/combat_vfx/garden/{kind}_{stage}.{ext}' for folder,ext in [('models','json'),('items','json'),('textures','png')] for kind in ['floor','rim'] for stage in range(1,9)}
    old=[line for line in (PACK/'index.txt').read_text(encoding='utf-8').splitlines() if line not in obsolete]
    added=[p.relative_to(PACK).as_posix() for p in assets.rglob('*') if p.is_file() and '/garden/' in p.as_posix()]
    (PACK/'index.txt').write_text('\n'.join(sorted(set(old+added)))+'\n',encoding='utf-8')
    print('170 connected garden models/items + native pixel textures; other art untouched')

if __name__ == '__main__': build()
