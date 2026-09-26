"""Original armor paintings, equipment icons and modelled helmets.

Humanoid armor uses the native 64x32 UV layout and follows vanilla limb poses.
Each pixel is painted directly without interpolation.
Head equipment has no equipment asset_id: CustomHeadLayer renders its item model.
No skin replacement, global armor override, following display entity or client mod.
"""
import json
from PIL import Image, ImageDraw
from class_armament_geometry import ASSETS, box, beam, gem
from build_bold_class_armor import ART as CLASS_ART, armor_texture as class_armor_texture

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
    return class_armor_texture(job,tier,inner)


def helmet_face_texture(job,tier):
    """Painted front face for the worn head model, with real alpha cutouts."""
    colors=tuple('#'+c for c in CLASS_ART[job]['colors'])
    image=Image.new('RGBA',(32,32),(0,0,0,0))
    draw=ImageDraw.Draw(image)
    def polygon(points,c): draw.polygon(points,fill=colors[c])
    def rectangle(box,c): draw.rectangle(box,fill=colors[c])
    def line(points,c,width=1): draw.line(points,fill=colors[c],width=width)
    if job=='warrior':
        polygon([(2,1),(13,1),(15,4),(14,14),(11,15),(10,12),
                 (5,12),(4,15),(1,14),(0,4)],1)
        polygon([(3,2),(12,2),(14,4),(13,7),(2,7),(1,4)],2)
        polygon([(3,2),(8,2),(10,4),(8,5),(2,5)],3)
        line([(2,6),(13,6)],7,2)
        rectangle((4,8,11,9),0)
        rectangle((5,8,6,8),4); rectangle((9,8,10,8),4)
        polygon([(2,10),(5,10),(6,13),(3,14),(1,13)],2)
        polygon([(10,10),(13,10),(14,13),(12,14),(9,13)],2)
        rectangle((7,7,8,14),7)
        line([(7,8),(7,13)],6)
    elif job=='mage':
        polygon([(3,1),(11,1),(14,4),(14,13),(12,15),(3,15),(1,12),(1,5)],1)
        polygon([(4,2),(10,2),(13,5),(11,9),(3,9),(2,5)],2)
        line([(2,5),(13,5)],7,2)
        polygon([(7,3),(10,6),(7,9),(4,6)],8)
        rectangle((7,4,7,6),4)
        polygon([(4,10),(11,10),(12,14),(3,14)],5)
        line([(4,13),(11,13)],6)
    elif job=='ranger':
        polygon([(3,1),(12,1),(15,5),(13,14),(10,15),(6,13),(3,15),(1,12),(0,5)],1)
        polygon([(3,2),(11,2),(13,5),(12,8),(2,8),(1,5)],2)
        line([(2,5),(7,3),(12,5)],3,2)
        polygon([(2,9),(5,9),(6,14),(3,14),(1,12)],2)
        polygon([(10,9),(13,9),(14,12),(12,14),(9,14)],2)
        line([(11,1),(13,4),(13,10)],7,2)
        rectangle((12,6,13,7),8)
    elif job=='assassin':
        polygon([(2,1),(13,1),(15,5),(14,14),(11,15),(9,13),
                 (6,13),(4,15),(1,14),(0,5)],1)
        polygon([(2,3),(7,2),(11,3),(14,6),(12,8),(3,8),(1,6)],2)
        line([(1,7),(5,8),(10,8),(14,7)],3)
        rectangle((4,9,11,10),0)
        rectangle((5,9,6,9),8); rectangle((9,9,10,9),8)
        polygon([(3,11),(12,11),(10,14),(5,14)],5)
        line([(5,12),(10,12)],6)
    elif job=='templar':
        polygon([(2,1),(13,1),(15,4),(14,14),(11,15),(9,13),
                 (6,13),(4,15),(1,14),(0,4)],1)
        polygon([(3,2),(12,2),(14,5),(12,10),(3,10),(1,5)],2)
        polygon([(4,3),(11,3),(12,5),(11,7),(4,7),(3,5)],4)
        line([(2,7),(13,7)],7,2)
        rectangle((4,9,11,9),0)
        rectangle((7,4,8,14),7)
        rectangle((7,10,8,11),4)
        line([(3,12),(6,14)],3); line([(12,12),(9,14)],3)
    elif job=='healer':
        polygon([(2,1),(13,1),(15,5),(14,13),(11,15),(9,12),
                 (6,12),(4,15),(1,13),(0,5)],1)
        polygon([(3,2),(12,2),(14,5),(12,8),(3,8),(1,5)],3)
        polygon([(3,2),(8,2),(9,5),(6,7),(2,6)],4)
        line([(2,7),(13,7)],7)
        rectangle((7,3,8,9),6)
        rectangle((5,5,10,6),6)
        polygon([(2,10),(5,10),(6,14),(3,14)],2)
        polygon([(10,10),(13,10),(12,14),(9,14)],2)
    else:
        polygon([(2,2),(13,2),(15,5),(14,14),(11,15),(9,12),
                 (6,12),(4,15),(1,14),(0,5)],1)
        polygon([(3,3),(12,3),(14,6),(12,9),(3,9),(1,6)],2)
        line([(2,6),(13,6)],7,2)
        polygon([(7,2),(10,6),(7,10),(4,6)],8)
        rectangle((7,4,7,7),4)
        rectangle((3,11,4,11),8); rectangle((11,11,12,11),8)
        polygon([(3,12),(6,13),(9,13),(12,12),(11,15),(4,15)],5)
    if tier>1: rectangle((12,2,12,2),8)
    # The other quadrants paint actual side and crown surfaces of the model.
    # A 2D front badge alone leaves the head looking like an untextured box.
    rectangle((16,0,31,15),1)
    polygon([(17,1),(29,1),(31,4),(30,12),(27,14),(19,14),(16,11)],2)
    polygon([(18,2),(27,2),(29,4),(25,6),(18,5)],3)
    line([(17,7),(29,7)],7,2)
    line([(18,12),(28,12)],0)
    rectangle((0,16,15,31),1)
    polygon([(2,18),(12,18),(14,21),(13,27),(10,30),(3,29),(1,25)],2)
    polygon([(3,19),(11,19),(12,22),(8,24),(2,22)],3)
    line([(3,27),(12,27)],7)
    rectangle((16,16,31,31),1)
    polygon([(18,18),(29,18),(30,22),(28,29),(18,29),(17,22)],2)
    line([(18,24),(29,24)],7,2)
    return image


def helmet(job,tier):
    base,secondary,trim,glow=KITS[job]; e=[]
    if job=='warrior': base,secondary=secondary,base
    # At head transform scale=1.6, the vanilla 0.625 layer scale cancels.
    # y=3.5..12.5 maps to head-local y=+0.5..-8.5: a hollow shell around skin.
    for name,lo,hi in (
        ('crown cap',[3.5,11.7,3.5],[12.5,12.5,12.5]),
        ('left temple',[3.5,4.5,3.5],[4.3,11.7,12.5]),
        ('right temple',[11.7,4.5,3.5],[12.5,11.7,12.5]),
        ('rear neck plate',[4.3,3.5,11.7],[11.7,11.7,12.5])):
        e.append(box(name,lo,hi,base))
    e.append(box('brow band',[3.45,10.3,3.1],[12.55,11.5,3.7],trim))
    if job=='warrior':
        e.append(box('forged brow',[4.1,10.2,2.75],[11.9,11.1,3.7],base))
        e.append(box('forehead bevel',[5,11.1,2.6],[11,11.5,3.1],secondary))
        e.append(box('raised brow ridge',[5,11.35,2.35],[11,12.1,3.2],trim))
        for s in (-1,1):
            x=8+s*5
            e.append(box('swept cheek plate',[x-.8,4.5,3.5],[x+.8,9,7],base,-s*22.5))
            e.append(box('cheek bevel',[x-.7,5,3.2],[x+.7,7.2,3.8],secondary,-s*22.5))
            for i in range(tier):
                e.append(box('crested temple wing',[x-.5,10+i*.8,6+i],[x+.5,12+i*.8,9+i],base,-s*22.5))
        e.append(box('central nasal',[7.6,7.6,2.7],[8.4,11,3.5],base))
        e.append(box('nasal gold face',[7.75,6.7,2.15],[8.25,11.5,2.7],trim))
    elif job=='mage':
        e.append(box('wide angular brim',[2.7,11.7,2.7],[13.3,12.4,13.3],trim))
        e.append(box('high cloth crown',[4.5,12.2,4.5],[11.5,15.2,11.5],base))
        e.append(box('offset folded crown',[5.3,14.7,5.8],[10.5,17.5,10.5],base,22.5))
        for i in range(tier): e.append(box('crown frost pin',[6+i,12.8,4.1],[6.5+i,14,4.6],glow))
        for side in (-1,1):
            x=8+side*5.3
            e.append(box('folded hood cheek',[x-.8,4.1,2.9],[x+.8,9.1,7],base,-side*22.5))
            e.append(box('hood cheek embroidery',[x-.65,7.1,2.5],[x+.65,8,3.1],trim,-side*22.5))
        gem(e,'mage focus',8,10.6,2.35,2.3,glow)
        e.append(box('amulet below face',[7.3,3.3,2.45],[8.7,4.8,3.2],trim,45))
    elif job=='ranger':
        e.append(box('hood peaked lip',[4.3,10.8,1.7],[11.7,11.8,4.1],secondary))
        e.append(box('hood brow shade',[5.2,10.1,2.6],[10.8,10.8,3.5],base))
        for side in (-1,1):
            x=8+side*5.1
            e.append(box('swept leaf wing',[x-.9,9.5,3.4],
                         [x+.9,16,6.3],trim,-side*22.5))
            e.append(box('wing green enamel',[x-.6,10.3,3.05],
                         [x+.6,14.8,3.55],secondary,-side*22.5))
        gem(e,'forehead leafstone',8,10.2,2.2,2.2,glow)
    elif job=='assassin':
        e.append(box('lower face veil',[4.2,4.2,2.7],[11.8,7.2,3.7],secondary))
        e.append(box('hood overhang',[3.6,10.7,2.3],[12.4,12,4],base))
        for s in (-1,1):
            x=8+s*4.8
            e.append(box('cowl blade rim',[x-.4,6,2.5],[x+.4,11,3.5],trim,-s*22.5))
            e.append(box('raised cowl horn',[x-.7,11.5,5.8],[x+.7,16,8.8],base,-s*22.5))
            e.append(box('horn cutting edge',[x-.5,12.2,5.3],[x+.5,15.3,5.9],secondary,-s*22.5))
        for i in range(tier): e.append(box('veil stitched rune',[5.3+i*1.5,5.2,2.4],[5.8+i*1.5,6,2.8],glow))
    elif job=='templar':
        e.append(box('armored visor',[4.2,5.3,2.7],[11.8,8.6,3.7],secondary))
        e.append(box('visor nasal',[7.5,8.5,2.5],[8.5,11,3.5],trim))
        for s in (-1,1):
            x=8+s*5.2
            e.append(box('bastion temple tower',[x-.65,8.2,6],[x+.65,13.7,9.7],base))
            e.append(box('temple bright bevel',[x-.45,9,5.65],[x+.45,13.3,6.1],secondary))
        for i in range(tier):
            e.append(box('crown crenellation',[4.7+i*1.7,12.5,6],[5.7+i*1.7,14.1,9],trim))
        e.append(box('projecting oath brow',[4.8,11.1,2.1],[11.2,12,4.5],trim))
        e.append(box('temple shield nose',[7.5,6.7,1.9],[8.5,11,3.2],secondary))
        gem(e,'oath brow',8,11,3,1,glow)
    elif job=='healer':
        # Open face and a split mitre rather than another fighter helmet.
        e.append(box('ivory mitre left',[4.5,12,5],[7.8,16.5,10.5],base,-22.5))
        e.append(box('ivory mitre right',[8.2,12,5],[11.5,16.5,10.5],base,22.5))
        e.append(box('plum central mitre',[7.5,11.6,4.8],[8.5,16,5.3],secondary))
        for side in (-1,1):
            x=8+side*5.6
            e.append(box('healing sun wing',[x-.75,10.7,5.6],
                         [x+.75,14.7,8.5],trim,-side*22.5))
        for i in range(tier):
            e.append(box('mitre golden stitch',[6.3,12.2+i*.8,4.5],[9.7,12.5+i*.8,5],trim))
        gem(e,'prayer focus',8,11,3,1,glow)
    else:
        # A low open coronet and a broken halo leave the skin's face visible.
        for s in (-1,1):
            e.append(box('astral temple point',[8+s*4.3-.4,10,4],[8+s*4.3+.4,14,5.2],trim,-s*22.5))
        ring=[(8+x,15+y,9) for x,y in ((-4,-2),(-2,-3),(2,-3),(4,-2),(4,2),(2,3),(-2,3),(-4,2))]
        for i,p in enumerate(ring):
            if i==4: continue
            beam(e,'broken coronet halo',p,ring[(i+1)%8],.55,.55,trim)
        for side in (-1,1):
            x=8+side*5.4
            e.append(box('star arc shoulder',[x-.55,11.6,6.5],
                         [x+.55,16,9.5],secondary,-side*22.5))
        for i in range(tier):
            x=5+i*2; e.append(box('fixed coronet star',[x-.25,14.5,3],[x+.25,15.5,3.5],glow))
        gem(e,'star forehead',8,11,3,1.1,glow)
    for element in e:
        if element['name'] in ('crown cap','left temple','right temple','rear neck plate'):
            element['faces']={name:{'uv':([0,8,8,16] if name=='up' else [8,0,16,8]),
                                    'texture':'#helm'} for name in element['faces']}
    faceplate=box('painted face plate',[4,4,2.9],[12,12,3],base)
    faceplate['faces']={'north':{'uv':[0,0,8,8],'texture':'#helm'}}
    e.append(faceplate)
    return e


def armor_model(job,tier,slot):
    base,secondary,trim,glow=KITS[job]
    gui_scale=({'starweaver':.78,'mage':.82,'healer':.9}.get(job,.95) if slot=='helmet' else 1)
    if slot=='helmet': elements=helmet(job,tier)
    elif slot=='chestplate':
        elements=[box('cuirass',[4,3,5.5],[12,15,10.5],base),box('left shoulder',[.5,11.5,5],[4,16,11],secondary),box('right shoulder',[12,11.5,5],[15.5,16,11],secondary),box('belt',[3.8,4,5.2],[12.2,5.2,10.8],trim)]
        gem(elements,'chest badge',8,11.4,5.3,1.2,glow)
    else:
        elements=[]
        for x in (3.5,9):
            elements.append(box(slot+' left/right',[x,3,5],[x+3.5,14 if slot=='leggings' else 9,10],base))
            elements.append(box('trim band',[x-.15,10.5 if slot=='leggings' else 7.5,4.8],[x+3.65,11.5 if slot=='leggings' else 8.5,10.2],trim))
            if slot=='boots': elements.append(box('projecting toe',[x,3,3.7],[x+3.5,5,10],secondary))
    # Paint the item model with the same motif as the equipped armor. The old
    # model sampled only flat material tiles, so its small GUI icon lost the
    # class motif and appeared like an unrelated dark cube.
    worn='inner' if slot=='leggings' else 'outer'
    front_uv=[5,10,7,16] if slot=='chestplate' else [1,10,2,16]
    for element in elements:
        if element['name'] in ('cuirass',slot+' left/right'):
            element['faces']['north']={'uv':front_uv,'texture':'#'+worn}
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
            line([(4,12),(11,12)],'L')
            rectangle((7,12,8,12),'T')
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
            helmet_face_texture(job,tier).save(face_path)
            for layer,inner in (('humanoid',False),('humanoid_leggings',True)):
                surface=armor_texture(job,tier,inner)
                path=ASSETS/f'textures/entity/equipment/{layer}/{key}.png'
                path.parent.mkdir(parents=True,exist_ok=True); surface.save(path)
                item_path=ASSETS/f'textures/item/{key}_{"inner" if inner else "outer"}.png'
                item_path.parent.mkdir(parents=True,exist_ok=True); surface.save(item_path)
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
    print('Built 28 class armor sets: 56 equipment textures, 56 item-atlas copies, 112 UI icons, 28 equipment definitions, 112 wearable and UI models/items.')


if __name__=='__main__': build()
