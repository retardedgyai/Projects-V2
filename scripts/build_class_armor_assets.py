"""Original native armor pixel layouts + independently modelled head equipment.

Humanoid armor uses the real 64x32 UV layout and follows vanilla limb poses.
Head equipment has no equipment asset_id: CustomHeadLayer renders its item model.
No skin replacement, global armor override, following display entity or client mod.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from class_armament_geometry import ASSETS, MATERIALS, box, beam, gem

JOBS=('warrior','mage','ranger','assassin','templar','healer','starweaver')
SLOTS=('helmet','chestplate','leggings','boots')
# Base / secondary surface / trim / light. Four levels per shared material.
KITS={
    'warrior':('iron','steel','bronze','ember'),
    'mage':('navy','violet','bronze','ice'),
    'ranger':('green','moss','bronze','light'),
    'assassin':('violet','cloth','steel','venom'),
    'templar':('steel','ivory','bronze','ice'),
    'healer':('ivory','cloth','bronze','light'),
    'starweaver':('navy','steel','bronze','ice'),
}
FRONTS={
    # Large stepped highlights and dark inset panels stay readable at worn
    # scale. Each job keeps its own chest motif instead of a shared flat tile.
    'warrior':('.dHHHHd.','dHPPPPHd','HPSBBSPH','PSBddBSP','PSdBBdSP','SBdFGdBS','SBdGEdBS','HSBddBSH','dHUttUHd','tULddLUt','dBddddBd','.dBHHBd.'),
    'mage':('.dTTTTd.','dTSSSSTd','TSsBBsST','SsBGGBsS','SBGggBSS','SBdBBdBS','SBdBBdBS','SsBBBBsS','dTtLLtTd','tLBBBBLt','dSBBBBsd','dHHHHHHd'),
    'ranger':('.dHHHHd.','dHSBBSHd','HSsLLsSH','SsLBBLsS','SLLBBLLS','SLdBBdLS','SsLBBLsS','dSLLLBSd','tTLttLTt','dLBBBBLd','ddBssBdd','.dHHHHd.'),
    'assassin':('.dHHHHd.','dHSSSSHd','HSsBBsSH','SsBLLBsS','SBdggdBS','SBdggdBS','SsBLLBsS','dSBBBBsd','dtLttLtd','tLBBBBLt','dBssssBd','.dHHHHd.'),
    'templar':('.dHHHHd.','dHPPPPHd','HPsBBsPH','PsBTTBsP','SBtUUtBS','SBtggtBS','SBtUUtBS','PsBBBBsP','dHULLUHd','tLBBBBLt','dBssssBd','.dHUUHd.'),
    'healer':('.dTTTTd.','dTSSSSTd','TSsBBsST','SsBPPBsS','SBgGGgBS','SBgGGgBS','SBdBBdBS','dSBBBBsd','dTLttLTd','tLBBBBLt','dSBBBBsd','.dTTTTd.'),
    'starweaver':('.dTTTTd.','dTSSSSTd','TSsBBsST','SsBggBsS','SBgEEgBS','SBgEEgBS','SsBBBBsS','dSBBsBSd','dTUttUTd','tLBBBBLt','dSBBBBsd','.dTTTTd.'),
}

# Four-pixel limb panels must make the knee and shoe readable independently
# from the torso. Keep one motif per job rather than recoloring one template.
LEGS={
    'warrior':('tLLt','LdBL','dPPd','dPSd','dBsd','dBsd','dHsd','HPdH','PSSP','dBBd','HSSH','dssd'),
    'mage':('tLLt','LBBL','sBBs','sBBs','sBBs','sBBs','sBBs','sBBs','pBBp','dPPd','dssd','dddd'),
    'ranger':('tLLt','LBBL','sBBs','sBsB','sBBs','sBBs','dHHd','HPPH','PssP','dBBd','dPPd','dddd'),
    'assassin':('tLLt','LBBL','sBBs','sBsB','sBBs','sBBs','dHHd','HPPH','PssP','dBBd','dPPd','dddd'),
    'templar':('tLLt','LBBL','dPPd','dPSd','dBsd','dBsd','tUUt','HPPH','PSSP','dBBd','HSSH','dddd'),
    'healer':('tLLt','LBBL','sBBs','sBBs','sBBs','sBBs','sBBs','sBBs','pBBp','dPPd','dssd','dddd'),
    'starweaver':('tLLt','LBBL','sBBs','sBBs','sBBs','sBBs','sBBs','sBBs','pBBp','dPPd','dssd','dddd'),
}
BOOTS={
    'warrior':('....',)*6+('..t.','dPPd','PSSP','dBBd','HSSH','dssd'),
    'mage':('....',)*6+('..t.','sBBs','sBBs','pBBp','dPPd','dddd'),
    'ranger':('....',)*6+('..t.','dHHd','HPPH','dBBd','dPPd','dddd'),
    'assassin':('....',)*6+('..t.','dHHd','HPPH','dBBd','dPPd','dddd'),
    'templar':('....',)*6+('..t.','HPPH','PSSP','dBBd','HSSH','dddd'),
    'healer':('....',)*6+('..t.','sBBs','sBBs','pBBp','dPPd','dddd'),
    'starweaver':('....',)*6+('..t.','sBBs','sBBs','pBBp','dPPd','dddd'),
}
ARMS={
    'warrior':('.HH.','HPPH','PSSP','dBBd','....','....','dBBd','HSSH','tLLt','....','....','....'),
    'mage':('dTTd','TssT','sBBs','sBBs','sBBs','sBBs','sBBs','sBBs','tUUt','dPPd','dssd','....'),
    'ranger':('..HH','dHPP','PSsB','dBs.','....','....','....','tLLt','LBBL','dSsd','dssd','....'),
    'assassin':('dHHd','HPPH','PSsP','dBBd','....','....','....','tLLt','dPPd','dssd','....','....'),
    'templar':('dHHd','HPPH','PSSP','dBBd','dBBd','....','dBBd','HSSH','tLLt','dPPd','dssd','....'),
    'healer':('dTTd','TssT','sBBs','sBBs','sBBs','sBBs','sBBs','sBBs','tUUt','dPPd','dssd','....'),
    'starweaver':('dTTd','TssT','sBBs','sBBs','sBBs','sBBs','sBBs','sBBs','tUUt','dPPd','dssd','....'),
}


def palette(job):
    base,secondary,trim,glow=KITS[job]
    colors={c:tuple(bytes.fromhex(MATERIALS[m][shade]))+(255,) for c,m,shade in (
        ('B',base,2),('S',secondary,3),('D',base,0),('L','leather',1),('T',trim,3),('G',glow,3),('H',base,3))}
    colors['L']=(86,64,41,255)  # Warm hide belt, not the violet weapon-handle tile.
    for character,material,shade in (
        ('d',base,1),('s',secondary,1),('p',secondary,2),('P',secondary,3),
        ('t',trim,1),('u',trim,2),('U',trim,3),
        ('F',glow,1),('g',glow,2),('E',glow,3)):
        colors[character]=tuple(bytes.fromhex(MATERIALS[material][shade]))+(255,)
    if job=='warrior':
        colors['S']=colors['p']; colors['T']=colors['u']; colors['G']=colors['g']
    return colors


def put_face(image,uv,size,pattern,colors):
    u,v=uv; w,h=size
    assert len(pattern)==h and all(len(row)==w for row in pattern),(uv,size,pattern)
    assert all(c=='.' or c in colors for row in pattern for c in row),(uv,pattern)
    for y,row in enumerate(pattern):
        for x,c in enumerate(row): image.putpixel((u+x,v+y),colors.get(c,(0,0,0,0)))


def unwrap(image, origin, width,height,depth, front, colors, side_char='B',back=None):
    u,v=origin
    # Vanilla ModelPart.Cube: +X, -X, -Z/front, +Z/back, top, bottom.
    side=tuple(char*depth for char in side_char) if isinstance(side_char,tuple) else tuple(side_char*depth for _ in range(height))
    put_face(image,(u,v+depth),(depth,height),side,colors)
    put_face(image,(u+depth+width,v+depth),(depth,height),side,colors)
    put_face(image,(u+depth,v+depth),(width,height),front,colors)
    put_face(image,(u+2*depth+width,v+depth),(width,height),back or tuple(row[0]*width for row in side),colors)
    put_face(image,(u+depth,v),(width,depth),tuple('H'*width for _ in range(depth)),colors)
    bottom='D' if any(c!='.' for c in front[-1]) else '.'
    put_face(image,(u+depth+width,v),(width,depth),tuple(bottom*width for _ in range(depth)),colors)


def armor_texture(job,tier,inner=False):
    image=Image.new('RGBA',(64,32),(0,0,0,0)); colors=palette(job)
    front=list(FRONTS[job])
    # Rank detailing is restrained: different piping/hem, not a new random color.
    front[-1]='T'*(tier*2)+'B'*(8-tier*2)
    if job=='warrior':
        front[-1]=('.dBHHBd.','.dBHUBd.','.dUHUBd.','.dUUUUd.')[tier-1]
    if inner:
        body=tuple('L'*8 if y==8 else 'B'*8 for y in range(12))
        unwrap(image,(16,16),8,12,4,body,colors,side_char='D')
        unwrap(image,(0,16),4,12,4,LEGS[job],colors,side_char='d' if job=='warrior' else 'D')
        return image
    back=tuple('TBBBBBBT' if y<8 else 'LLLLLLLL' if y==8 else 'BSSSSSSB' for y in range(12))
    if job in ('mage','healer','starweaver'): back=('TTTTTTTT',)+tuple('TBBBBBBT' for _ in range(10))+('TTTTTTTT',)
    unwrap(image,(16,16),8,12,4,tuple(front),colors,side_char='d' if job=='warrior' else 'D',back=back)
    arm=ARMS[job]
    arm_side=tuple(('s' if job in ('mage','healer','starweaver') else 'd') if any(c!='.' for c in row) else '.' for row in arm)
    unwrap(image,(40,16),4,12,4,arm,colors,side_char=arm_side)
    # Feet render on the outer layer; the upper leg stays transparent so the
    # independently animated leggings are visible, rather than painted over.
    unwrap(image,(0,16),4,12,4,BOOTS[job],colors,side_char='d' if job=='warrior' else 'D',back=('....',)*6+('LLLL',)*5+('DDDD',))
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
        ring=[(8+x,15+y,9) for x,y in ((-4,-2),(-2,-3),(2,-3),(4,-2),(4,2),(2,3),(-2,3),(-4,2))]
        for i,p in enumerate(ring):
            if i==4: continue
            beam(e,'broken coronet halo',p,ring[(i+1)%8],.55,.55,trim)
        for i in range(tier):
            x=5+i*2; e.append(box('fixed coronet star',[x-.25,14.5,3],[x+.25,15.5,3.5],glow))
        gem(e,'star forehead',8,11,3,1.1,glow)
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
            'inner':f'projects:item/armor/{job}_t{tier}_inner'},'elements':elements,
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
