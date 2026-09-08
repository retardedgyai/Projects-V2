"""Original native armor pixel layouts + independently modelled head equipment.

Humanoid armor uses the real 64x32 UV layout and follows vanilla limb poses.
Head equipment has no equipment asset_id: CustomHeadLayer renders its item model.
No skin replacement, global armor override, following display entity or client mod.
"""
import json
from pathlib import Path
from PIL import Image
from class_armament_geometry import ASSETS, MATERIALS, box, beam, gem

JOBS=('warrior','mage','ranger','assassin','templar','healer','starweaver')
SLOTS=('helmet','chestplate','leggings','boots')
# Base / secondary surface / trim / light. Four levels per shared material.
KITS={
    'warrior':('iron','steel','bronze','ember'),
    'mage':('navy','cloth','bronze','ice'),
    'ranger':('green','leather','bronze','moss'),
    'assassin':('violet','cloth','steel','venom'),
    'templar':('steel','ivory','bronze','ice'),
    'healer':('ivory','cloth','bronze','light'),
    'starweaver':('navy','cloth','bronze','ice'),
}
FRONTS={
    'warrior':('TTBBBBTT','TBBSSBBT','BBSSSSBB','BSSDDSSB','SSDDDDSS','SSDDGDSS','BSSDDSSB','BBSSSSBB','LLLLLLLL','LTTLLTTL','DDLLLLDD','DDLLLLDD'),
    'mage':('TTDDDDTT','BTSDDSTB','BBTDDTBB','BBTGGTBB','BBTDDTBB','BBTDDTBB','BBTDDTBB','BBTDDTBB','TTTTTTTT','BBTLLTBB','BBTLLTBB','BBTLLTBB'),
    'ranger':('SSTBBBTT','SSTBBBBB','SBBTBBSS','BBBBTBSS','BBBBBTBB','SBBBBBTB','SSBBBBBT','SSBBBBBB','LLTTLLLL','LLTTLLLL','SBBBBBBB','SSBBBBSS'),
    'assassin':('BBSDDSBB','BSLDDLSB','BSLDDLSB','BBSLLSBB','BBSSSSBB','BBGGSSBB','BBGGSSBB','SSSSSSSS','TTLLTTLL','LLLLLLLL','DBSSSSBD','DBSSSSBD'),
    'templar':('TTSSSSTT','TSSBB SST'.replace(' ',''),'SSBBBBSS','SBBT TBBS'.replace(' ',''),'BBTTTTBB','BBTGG TBB'.replace(' ',''),'BBTGG TBB'.replace(' ',''),'BBTTTTBB','TTTTTTTT','BBTTTTBB','BBTBBTBB','BBTBBTBB'),
    'healer':('TTSSSSTT','TBBSSBBT','BTBSSBTB','BTBSSBTB','BTBGGBT B'.replace(' ',''),'BTBGGBT B'.replace(' ',''),'BTBSSBTB','BTBSSBTB','TTTTTTTT','BTSSSSTB','BTSSSSTB','BTSSSSTB'),
    'starweaver':('TTBBB BTT'.replace(' ',''),'BTBBBBTB','BBTBBTBB','BBBGGBBB','BBTBBTBB','BTBBBBTB','BBBBGGBB','BBGTBBBB','TTTTTTTT','BTBBBBTB','BBTBBTBB','BBBT TBBB'.replace(' ','')),
}


def palette(job):
    base,secondary,trim,glow=KITS[job]
    return {c:tuple(bytes.fromhex(MATERIALS[m][shade]))+(255,) for c,m,shade in (
        ('B',base,2),('S',secondary,2),('D',base,0),('L','leather',1),('T',trim,2),('G',glow,2),('H',base,3))}


def put_face(image,uv,size,pattern,colors):
    u,v=uv; w,h=size
    assert len(pattern)==h and all(len(row)==w for row in pattern),(uv,size,pattern)
    for y,row in enumerate(pattern):
        for x,c in enumerate(row): image.putpixel((u+x,v+y),colors.get(c,(0,0,0,0)))


def unwrap(image, origin, width,height,depth, front, colors, side_char='B',back=None):
    u,v=origin
    # Vanilla ModelPart.Cube: +X, -X, -Z/front, +Z/back, top, bottom.
    side=tuple(side_char*depth for _ in range(height))
    put_face(image,(u,v+depth),(depth,height),side,colors)
    put_face(image,(u+depth+width,v+depth),(depth,height),side,colors)
    put_face(image,(u+depth,v+depth),(width,height),front,colors)
    put_face(image,(u+2*depth+width,v+depth),(width,height),back or tuple(side_char*width for _ in range(height)),colors)
    put_face(image,(u+depth,v),(width,depth),tuple('H'*width for _ in range(depth)),colors)
    put_face(image,(u+depth+width,v),(width,depth),tuple('D'*width for _ in range(depth)),colors)


def armor_texture(job,tier,inner=False):
    image=Image.new('RGBA',(64,32),(0,0,0,0)); colors=palette(job)
    front=list(FRONTS[job])
    # Rank detailing is restrained: different piping/hem, not a new random color.
    front[-1]='T'*(tier*2)+'B'*(8-tier*2)
    if inner:
        body=tuple('L'*8 if y==8 else 'B'*8 for y in range(12))
        unwrap(image,(16,16),8,12,4,body,colors,side_char='D')
        leg=('BBBB','BBBB','BSSB','BSSB','BSSB','BBBB','BT TB'.replace(' ',''),'BT TB'.replace(' ',''),'BBBB','BBBB','BBBB','BBBB')
        # Distinct knee plates for armored classes, soft panels for cloth users.
        if job in ('warrior','templar'): leg=('BBBB','BSSB','BSSB','BBBB','TTTT','SGGS','SSSS','BBBB','BBBB','BBBB','BBBB','BBBB')
        unwrap(image,(0,16),4,12,4,leg,colors,side_char='B')
        return image
    back=tuple('TBBBBBBT' if y<8 else 'LLLLLLLL' if y==8 else 'BSSSSSSB' for y in range(12))
    if job in ('mage','healer','starweaver'): back=('TTTTTTTT',)+tuple('TBBBBBBT' for _ in range(10))+('TTTTTTTT',)
    unwrap(image,(16,16),8,12,4,tuple(front),colors,side_char='B',back=back)
    arm=('TTTT','HSSH','BSSB','BSSB','BBBB','BBBB','BBBB','BBBB','LLLL','TLLT','SSSS','DDDD')
    if job in ('ranger','assassin'): arm=('TTTT','BSSB','BSSB','BBBB','....','....','....','....','LLLL','TLLT','BSSB','DDDD')
    if job in ('mage','healer','starweaver'): arm=('TTTT','BBBB','BSSB','BSSB','BSSB','BSSB','BSSB','BSSB','TTTT','BT TB'.replace(' ',''),'BBBB','TTTT')
    unwrap(image,(40,16),4,12,4,arm,colors,side_char='B')
    # Feet render on the outer layer; the upper leg stays transparent so the
    # independently animated leggings are visible, rather than painted over.
    boot=('....',)*6+('TTTT','BSSB','BSSB','BSSB','HHHH','DDDD')
    unwrap(image,(0,16),4,12,4,boot,colors,side_char='B',back=('....',)*6+('LLLL',)*5+('DDDD',))
    for x in range(16):
        for y in range(16,26): image.putpixel((x,y),(0,0,0,0))
    return image


def helmet(job,tier):
    base,secondary,trim,glow=KITS[job]; e=[]
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
        for s in (-1,1):
            x=8+s*5
            e.append(box('swept cheek plate',[x-.8,4,3.5],[x+.8,8,7],secondary,-s*22.5))
            for i in range(tier):
                e.append(box('crested temple wing',[x-.5,10+i*.8,6+i],[x+.5,12+i*.8,9+i],base,-s*22.5))
        e.append(box('central nasal',[7.5,7,2.8],[8.5,11,3.5],secondary))
        gem(e,'forehead ember',8,11,3,1.15,glow)
    elif job=='mage':
        e.append(box('wide angular brim',[2.7,11.7,2.7],[13.3,12.4,13.3],trim))
        e.append(box('high cloth crown',[4.5,12.2,4.5],[11.5,15.2,11.5],base))
        e.append(box('offset folded crown',[5.3,14.7,5.8],[10.5,17.5,10.5],base,22.5))
        for i in range(tier): e.append(box('crown frost pin',[6+i,12.8,4.1],[6.5+i,14,4.6],glow))
        gem(e,'mage focus',8,11,3,1.25,glow)
    elif job=='ranger':
        e.append(box('hood peaked lip',[4.3,10.8,1.7],[11.7,11.8,4.1],secondary))
        e.append(box('hood brow shade',[5.2,10.1,2.6],[10.8,10.8,3.5],base))
        for i in range(1+tier):
            e.append(box('swept leaf plume',[12,10+i*.6,7+i*.6],[12.8,13.3+i*.6,8+i*.6],'moss',-22.5,'x'))
        gem(e,'side clasp',12.7,8.8,5.2,.8,trim)
    elif job=='assassin':
        e.append(box('lower face veil',[4.2,4.2,2.7],[11.8,7.2,3.7],secondary))
        e.append(box('hood overhang',[3.6,10.7,2.3],[12.4,12,4],base))
        for s in (-1,1):
            x=8+s*4.8
            e.append(box('cowl blade rim',[x-.4,6,2.5],[x+.4,11,3.5],trim,-s*22.5))
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
        gem(e,'oath brow',8,11,3,1,glow)
    elif job=='healer':
        # Open face and a split mitre rather than another fighter helmet.
        e.append(box('ivory mitre left',[4.5,12,5],[7.8,16.5,10.5],base,-22.5))
        e.append(box('ivory mitre right',[8.2,12,5],[11.5,16.5,10.5],base,22.5))
        e.append(box('plum central mitre',[7.5,11.6,4.8],[8.5,16,5.3],secondary))
        for i in range(tier):
            e.append(box('mitre golden stitch',[6.3,12.2+i*.8,4.5],[9.7,12.5+i*.8,5],trim))
        gem(e,'prayer focus',8,11,3,1,glow)
    else:
        # A low open coronet and a broken halo leave the skin's face visible.
        for s in (-1,1):
            e.append(box('astral temple point',[8+s*4.3-.4,10,4],[8+s*4.3+.4,14,5.2],trim,-s*22.5))
        ring=[(8+x,16+y,9) for x,y in ((-4,-2),(-2,-4),(2,-4),(4,-2),(4,2),(2,4),(-2,4),(-4,2))]
        for i,p in enumerate(ring):
            if i==4: continue
            beam(e,'broken coronet halo',p,ring[(i+1)%8],.55,.55,trim)
        for i in range(tier):
            x=5+i*2; e.append(box('fixed coronet star',[x-.25,14.5,3],[x+.25,15.5,3.5],glow))
        gem(e,'star forehead',8,11,3,1.1,glow)
    return e


def armor_model(job,tier,slot):
    base,secondary,trim,glow=KITS[job]
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
    return {'credit':'ProjectS original class armor / native pixel source','gui_light':'front','ambientocclusion':False,
        'textures':{'atlas':'projects:item/weapons/materials','particle':'projects:item/weapons/materials'},'elements':elements,
        'display':{'head':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1.6,1.6,1.6]},
            'gui':{'rotation':[15,-25,0],'translation':[0,-1,0],'scale':[.65,.65,.65]},
            'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.5,.5,.5]},
            'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.65,.65,.65]}}}


def build():
    for job in JOBS:
        for tier in range(1,5):
            key=f'armor/{job}_t{tier}'
            files={f'equipment/{key}.json':{'layers':{layer:[{'texture':f'projects:{key}'}] for layer in ('humanoid','humanoid_leggings')}}}
            for layer,inner in (('humanoid',False),('humanoid_leggings',True)):
                path=ASSETS/f'textures/entity/equipment/{layer}/{key}.png'
                path.parent.mkdir(parents=True,exist_ok=True); armor_texture(job,tier,inner).save(path)
            for slot in SLOTS:
                name=f'{key}_{slot}'
                files[f'items/{name}.json']={'model':{'type':'minecraft:model','model':f'projects:item/{name}'}}
                files[f'models/item/{name}.json']=armor_model(job,tier,slot)
            for relative,data in files.items():
                path=ASSETS/relative; path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')
    pack=ASSETS.parents[1]
    paths=sorted(str(p.relative_to(pack)).replace('\\','/') for p in pack.rglob('*') if p.is_file() and p.name!='index.txt')
    (pack/'index.txt').write_text('\n'.join(paths)+'\n',encoding='utf-8')
    print('Built 28 class armor sets: 56 native equipment textures, 28 equipment definitions, 112 item models/items.')


if __name__=='__main__': build()
