"""Original armor paintings, equipment icons and modelled helmets.

Humanoid armor uses the native 64x32 UV layout and follows vanilla limb poses.
Each pixel is painted directly without interpolation.
Head equipment has no equipment asset_id: CustomHeadLayer renders its item model.
No skin replacement, global armor override, following display entity or client mod.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from class_armament_geometry import ASSETS, box
from build_bold_class_armor import ART as CLASS_ART, armor_texture as class_armor_texture
from build_warrior_body import armor_texture as warrior_armor_texture, item_texture as warrior_item_texture
from build_project_helmets import helmet_texture as project_helmet_texture, helmet_elements as project_helmet_elements

JOBS=('warrior','mage','ranger','assassin','templar','healer','starweaver')
SLOTS=('helmet','chestplate','leggings','boots')
# Base / secondary surface / trim / light. Four levels per shared material.
KITS={
    'warrior':('iron','steel','bronze','ember'),
    'mage':('navy','violet','bronze','venom'),
    'ranger':('green','moss','bronze','ice'),
    'assassin':('violet','cloth','steel','venom'),
    'templar':('steel','ivory','bronze','ice'),
    'healer':('ivory','cloth','bronze','light'),
    'starweaver':('navy','steel','bronze','ice'),
}
def palette(job):
    shades=CLASS_ART[job]['colors']
    return {key:tuple(bytes.fromhex(shades[index]))+(255,) for key,index in {
        'B':2,'S':3,'D':1,'L':5,'T':7,'G':8,'H':4,
        'd':1,'s':2,'p':3,'P':4,'t':6,'u':7,'U':7,
        'F':6,'g':8,'E':8}.items()}


def armor_texture(job,tier,inner=False):
    if job=='warrior': return warrior_armor_texture(tier,inner)
    return class_armor_texture(job,tier,inner)


def armor_model(job,tier,slot):
    base,secondary,trim,glow=KITS[job]
    gui_scale=({'starweaver':.78,'mage':.82,'healer':.9}.get(job,.95) if slot=='helmet' else 1)
    if slot=='helmet': elements=project_helmet_elements(job,tier)
    elif slot=='chestplate' and job=='warrior':
        # Long articulated sleeves hang away from the breastplate. The item
        # silhouette is led by the entire arm, not square shoulder caps.
        elements=[box('cuirass',[3.25,1.6,5.25],[12.75,14.2,10.75],base),
                  box('left sleeve',[-1.0,2.0,5.1],[4.5,14.5,10.9],secondary,-22.5,'z'),
                  box('right sleeve',[11.5,2.0,5.1],[17.0,14.5,10.9],secondary,22.5,'z')]
    elif slot=='chestplate':
        elements=[box('cuirass',[3.8,1.6,5.4],[12.2,15,10.6],base),
                  box('left sleeve',[.2,1.8,5.3],[4,15.4,10.7],secondary),
                  box('right sleeve',[12,1.8,5.3],[15.8,15.4,10.7],secondary)]
    elif slot=='leggings' and job=='warrior':
        elements=[box('left thigh guard',[3.4,7.3,5],[7,14.2,10],base),
                  box('right thigh guard',[9,7.3,5],[12.6,14.2,10],base),
                  box('joined leather waist',[3.5,12.3,5.2],[12.5,14.8,10.1],'leather'),
                  box('small forge buckle',[7.2,12.7,4.65],[8.8,14.1,5.25],'bronze')]
    elif slot=='boots' and job=='warrior':
        elements=[box('left high boot',[2.7,1.8,5.1],[7.5,7.6,10.5],base),
                  box('right high boot',[8.5,1.8,4.8],[13.3,7.6,10.2],base),
                  box('left projecting toe',[2.3,0.5,2.7],[7.8,2.9,7.5],base),
                  box('right projecting toe',[8.1,0.5,2.4],[13.6,2.9,7.2],base)]
    else:
        elements=[]
        for x in (3.5,9):
            elements.append(box(slot+' left/right',[x,3,5],[x+3.5,14 if slot=='leggings' else 9,10],base))
    # Native armor UVs cover every visible face. A single small front crop
    # pasted on otherwise generic material cubes made the item look flat.
    worn='inner' if slot=='leggings' else 'outer'
    for element in (() if slot=='helmet' else elements):
        if element['name'] in ('joined leather waist','small forge buckle'): continue
        if element['name'].endswith('toe'):
            element['faces']={
                'north':{'uv':[1,13,2,16],'texture':'#outer'},
                'south':{'uv':[3,13,4,16],'texture':'#outer'},
                'east':{'uv':[2,13,3,16],'texture':'#outer'},
                'west':{'uv':[0,13,1,16],'texture':'#outer'},
                'up':{'uv':[1,8,2,10],'texture':'#outer'},
                'down':{'uv':[2,8,3,10],'texture':'#outer'},
            }
            continue
        origin,width,depth=((40,16),4,4) if element['name'].endswith('sleeve') else (
            ((16,16),8,4) if slot=='chestplate' else ((0,16),4,4))
        u,v=origin
        coords={
            'west':(u,v+depth,depth,12),
            'north':(u+depth,v+depth,width,12),
            'east':(u+depth+width,v+depth,depth,12),
            'south':(u+2*depth+width,v+depth,width,12),
            'up':(u+depth,v,width,depth),
            'down':(u+depth+width,v,width,depth),
        }
        element['faces']={face:{'uv':[x/4,y/2,(x+w)/4,(y+h)/2],
                                'texture':'#'+worn}
                          for face,(x,y,w,h) in coords.items()}
    return {'credit':'ProjectS original class armor / native pixel source','gui_light':'front','ambientocclusion':False,
        'textures':{'atlas':'projects:item/weapons/materials','particle':'projects:item/weapons/materials',
            # Item models use the block/item atlas. Equipment-layer PNGs under
            # entity/equipment do not stitch there and render as missing art.
            'outer':f'projects:item/armor/{job}_t{tier}_outer',
            'inner':f'projects:item/armor/{job}_t{tier}_inner',
            'helm':f'projects:item/armor/helmet_faces/{job}_t{tier}'},'elements':elements,
        'display':{'head':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1.6,1.6,1.6]},
            'gui':{'rotation':[15,-25,0],'translation':[0,-1,0],'scale':[gui_scale]*3},
            'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.5,.5,.5]},
            'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.65,.65,.65]}}}


def icon_texture(job,tier,slot):
    """Small original icon silhouettes; the wearable model remains three dimensional."""
    if job=='warrior':
        path=Path(__file__).resolve().parent/'art/forge_warrior'/f'{slot}.png'
        image=Image.open(path).convert('RGBA')
        if tier>1:
            draw=ImageDraw.Draw(image)
            for i in range(tier-1):
                x=29-i*3
                draw.rectangle((x,2,x+1,3),fill='#edc97f')
        return image
    if slot=='helmet':
        return project_helmet_texture(job,tier).crop((0,0,32,32)).resize(
            (16,16),Image.Resampling.NEAREST)
    colors=palette(job)
    image=Image.new('RGBA',(16,16),(0,0,0,0))
    draw=ImageDraw.Draw(image)
    def polygon(points,shade): draw.polygon(points,fill=colors[shade])
    def rectangle(box,shade): draw.rectangle(box,fill=colors[shade])
    def line(points,shade): draw.line(points,fill=colors[shade],width=1)
    robe=job in ('mage','healer','starweaver')
    leather=job in ('ranger','assassin')
    if job=='warrior':
        if slot=='helmet':
            polygon([(4,2),(10,1),(13,3),(14,6),(13,10),(11,12),(10,10),
                     (10,8),(6,8),(6,10),(4,12),(2,10),(2,6)],'D')
            polygon([(4,3),(10,2),(12,4),(12,6),(5,6),(3,7)],'B')
            polygon([(4,3),(7,2),(9,2),(9,4),(5,5),(3,6)],'S')
            line([(3,7),(6,6),(11,6),(13,7)],'P')
            line([(7,2),(7,6)],'H')
            rectangle((6,8,10,10),'d')
            line([(3,8),(4,11),(5,9)],'S')
            line([(12,8),(11,11),(10,9)],'B')
            rectangle((7,6,8,6),'U')
        elif slot=='chestplate':
            polygon([(2,2),(5,2),(6,4),(10,4),(11,2),(14,3),(15,7),
                     (13,9),(12,8),(12,13),(10,15),(5,15),(3,13),
                     (3,8),(1,9),(0,7)],'D')
            polygon([(1,4),(4,3),(6,5),(5,8),(2,8),(1,7)],'S')
            polygon([(11,4),(13,3),(14,5),(14,7),(12,8),(10,7)],'B')
            line([(1,4),(4,3),(5,4)],'P')
            line([(11,4),(13,4),(14,5)],'S')
            polygon([(5,5),(10,5),(11,7),(11,11),(10,12),(5,12),(4,10),(4,7)],'B')
            polygon([(5,5),(9,5),(10,7),(9,10),(6,10),(5,9)],'S')
            line([(5,6),(7,5),(9,6)],'P')
            line([(4,4),(6,6)],'L')
            rectangle((5,5,5,5),'U')
            line([(4,4),(11,4)],'T')
            rectangle((7,8,8,9),'D')
            rectangle((8,8,8,9),'E')
            line([(4,12),(11,12)],'T')
            rectangle((7,12,8,12),'U')
            line([(5,13),(10,13)],'d')
        elif slot=='leggings':
            polygon([(3,2),(12,2),(13,5),(12,14),(10,15),(8,15),(8,9),
                     (7,9),(7,15),(5,15),(3,14),(2,5)],'D')
            rectangle((3,3,12,5),'L')
            line([(3,3),(12,3)],'t')
            rectangle((7,4,8,5),'U')
            polygon([(3,6),(7,6),(6,13),(5,14),(3,13)],'B')
            polygon([(9,6),(12,6),(12,13),(10,14),(9,13)],'d')
            line([(4,6),(4,9)],'S')
            line([(10,6),(11,9)],'B')
            polygon([(3,10),(6,10),(7,12),(5,13),(3,12)],'S')
            polygon([(9,10),(12,10),(12,12),(10,13),(9,12)],'S')
            rectangle((4,10,5,10),'P')
            rectangle((10,10,11,10),'P')
            line([(4,13),(6,14)],'H')
        else:
            # Back boot first; projecting toes and staggered shafts make the
            # pair read as footwear at inventory scale rather than two bars.
            polygon([(9,3),(12,3),(13,5),(13,10),(15,12),(15,14),
                     (8,14),(7,12),(9,10)],'D')
            polygon([(9,4),(12,4),(12,10),(14,12),(14,13),(9,13),(8,12)],'B')
            line([(9,4),(12,4)],'P')
            line([(9,8),(12,8)],'t')
            line([(10,12),(14,12)],'S')
            polygon([(3,2),(6,2),(7,4),(7,10),(9,12),(9,14),
                     (1,14),(0,12),(2,10),(2,4)],'D')
            polygon([(3,3),(6,3),(6,10),(8,12),(8,13),(1,13),(2,11)],'B')
            line([(3,3),(6,3)],'P')
            line([(2,7),(6,7)],'t')
            polygon([(2,10),(6,10),(8,12),(7,13),(1,13)],'S')
            line([(2,11),(6,11),(8,12)],'P')
        if tier>1: rectangle((11,3,11,3),'U')
        return image
    if slot=='helmet':
        polygon([(3,3),(5,1),(11,1),(13,3),(14,8),(12,11),(10,11),(10,9),
                 (6,9),(6,11),(3,11),(2,8)],'D')
        polygon([(4,3),(6,2),(10,2),(12,4),(12,6),(10,6),(10,5),(6,5),
                 (6,7),(4,7)],'B')
        line([(4,3),(6,2),(10,2)],'H')
        line([(2,7),(5,7),(6,6)],'S')
        line([(10,6),(11,7),(14,7)],'s')
        if robe:
            polygon([(5,2),(7,0),(9,0),(12,4),(10,5),(6,5)],'S')
            line([(6,2),(8,1),(10,3)],'P')
        elif leather:
            line([(4,8),(5,10),(7,11)],'L')
            line([(11,8),(10,10),(9,11)],'t')
        else:
            rectangle((6,3,9,4),'S')
            rectangle((7,3,8,3),'U')
            line([(4,8),(4,10),(6,11)],'S')
            line([(12,8),(12,10),(10,11)],'H')
        if tier>1: rectangle((8,2,8,2),'E')
    elif slot=='chestplate':
        polygon([(3,2),(5,2),(6,4),(9,4),(10,2),(12,2),(15,5),(14,9),
                 (12,9),(11,8),(11,13),(9,15),(6,15),(4,13),(4,8),(2,9),(0,8),(0,5)],'D')
        polygon([(2,4),(4,3),(6,5),(5,8),(3,8),(1,7)],'S')
        polygon([(11,4),(12,3),(14,5),(14,7),(12,8),(10,7)],'B')
        line([(1,5),(3,3),(5,3)],'H')
        line([(11,3),(13,3),(14,5)],'P')
        polygon([(5,5),(10,5),(11,9),(10,13),(5,13),(4,9)],'B')
        polygon([(5,5),(7,6),(7,10),(5,10),(4,8)],'S')
        line([(10,6),(10,11),(9,13)],'s')
        line([(4,12),(6,13),(10,13)],'t')
        if robe:
            line([(7,5),(7,12)],'T'); line([(9,5),(9,12)],'u')
            rectangle((7,8,8,9),'G')
        elif leather:
            line([(5,7),(10,11)],'L')
            rectangle((8,8,9,9),'T')
        else:
            polygon([(6,7),(7,6),(9,6),(10,7),(9,11),(7,11),(6,10)],'d')
            line([(6,7),(7,6),(9,6)],'H')
            rectangle((7,8,8,9),'G')
        if tier>1: rectangle((7,14,8,14),'U')
    elif slot=='leggings':
        polygon([(3,2),(12,2),(13,5),(12,13),(10,15),(8,15),(8,10),
                 (7,10),(7,15),(5,15),(3,13),(2,5)],'D')
        rectangle((3,3,12,5),'B')
        line([(3,3),(11,3)],'T')
        rectangle((7,4,8,5),'L')
        polygon([(3,6),(7,6),(6,13),(5,14),(3,13)],'B')
        polygon([(9,6),(12,6),(12,13),(10,14),(9,13)],'B')
        line([(4,6),(4,11),(5,13)],'S')
        line([(10,6),(11,7),(11,12)],'s')
        if robe:
            line([(3,7),(5,10),(5,13)],'t')
            line([(12,7),(10,10),(10,13)],'u')
        else:
            rectangle((3,10,6,11),'S')
            rectangle((9,10,12,11),'P')
            rectangle((4,10,4,10),'H')
            rectangle((10,10,10,10),'H')
        if tier>1: rectangle((7,3,8,3),'U')
    else:
        for offset in (0,8):
            polygon([(offset+2,3),(offset+5,3),(offset+6,5),(offset+6,10),
                     (offset+7,12),(offset+6,14),(offset+1,14),(offset,12),
                     (offset+1,9),(offset+1,5)],'D')
            polygon([(offset+2,4),(offset+5,4),(offset+5,9),(offset+6,12),
                     (offset+5,13),(offset+1,13),(offset+2,10)],'B')
            line([(offset+2,4),(offset+5,4)],'H')
            line([(offset+2,8),(offset+5,8)],'T')
            line([(offset+1,12),(offset+4,12)],'S')
            if not robe: rectangle((offset+2,10,offset+4,10),'P')
        if tier>1: rectangle((2,5,2,5),'U')
    return image


def build():
    for job in JOBS:
        for tier in range(1,5):
            key=f'armor/{job}_t{tier}'
            files={f'equipment/{key}.json':{'layers':{layer:[{'texture':f'projects:{key}'}] for layer in ('humanoid','humanoid_leggings')}}}
            face_path=ASSETS/f'textures/item/armor/helmet_faces/{job}_t{tier}.png'
            face_path.parent.mkdir(parents=True,exist_ok=True)
            project_helmet_texture(job,tier).save(face_path)
            for layer,inner in (('humanoid',False),('humanoid_leggings',True)):
                surface=armor_texture(job,tier,inner)
                path=ASSETS/f'textures/entity/equipment/{layer}/{key}.png'
                path.parent.mkdir(parents=True,exist_ok=True); surface.save(path)
                item_path=ASSETS/f'textures/item/{key}_{"inner" if inner else "outer"}.png'
                item_path.parent.mkdir(parents=True,exist_ok=True)
                (warrior_item_texture(tier,inner) if job=='warrior' else surface).save(item_path)
            for slot in SLOTS:
                name=f'{key}_{slot}'
                icon_path=ASSETS/f'textures/item/armor/icons/{job}_t{tier}_{slot}.png'
                icon_path.parent.mkdir(parents=True,exist_ok=True)
                icon_texture(job,tier,slot).save(icon_path)
                files[f'models/item/armor/icons/{job}_t{tier}_{slot}.json']={
                    'parent':'minecraft:item/generated','textures':{
                        'layer0':f'projects:item/armor/icons/{job}_t{tier}_{slot}'}}
                files[f'items/{name}.json']={'model':{
                    'type':'minecraft:select','property':'minecraft:display_context',
                    'cases':[{'when':['gui'],'model':{'type':'minecraft:model',
                        'model':f'projects:item/armor/icons/{job}_t{tier}_{slot}'}}],
                    'fallback':{'type':'minecraft:model','model':f'projects:item/{name}'}}}
                files[f'models/item/{name}.json']=armor_model(job,tier,slot)
            for relative,data in files.items():
                path=ASSETS/relative; path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')
    pack=ASSETS.parents[1]
    paths=sorted(str(p.relative_to(pack)).replace('\\','/') for p in pack.rglob('*') if p.is_file() and p.name!='index.txt')
    (pack/'index.txt').write_text('\n'.join(paths)+'\n',encoding='utf-8')
    print('Built 28 class armor sets: 56 equipment textures, 56 item textures, 112 UI icons, 28 equipment definitions, 112 wearable and UI models/items.')


if __name__=='__main__': build()
