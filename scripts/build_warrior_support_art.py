"""Texture-led warrior support art; no coloured-block swords, flags or voice hoops.

The cloth master is compiled to a nearest-neighbour game texture. Native surface strips
carry that same painting through deployment, wind and hem-first dissolution. Guard art
uses the approved weapon's existing compiler and exact painted grip, never a redraw.
"""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image
from build_approved_dash_v3 import PACK, SIZE, geometry, ink_uvs, polygon
from process_sword_material_redraw import convert

ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / 'assets/combat-vfx/warrior-support/standard-cloth-v1.png'
WIND_FRAMES = 16
VOICE_FRAMES = 20


def cloth_pixels():
    # Import conversion, not new artwork: preserve the authored alpha and pixel edges.
    with Image.open(MASTER) as source:
        source = source.convert('RGBA')
        # Imagegen may leave almost invisible alpha specks outside the object.
        # Bound the visible silhouette, without painting over its source alpha.
        bounds = source.getchannel('A').point(lambda a:255 if a>=128 else 0).getbbox()
        source = source.crop(bounds)
        return source.resize((48, 80), Image.Resampling.NEAREST)


def face(x0, y0, x1, y1, z, uv, texture='cloth'):
    return {'from':[x0,y0,z], 'to':[x1,y1,z], 'shade':False,
            'faces':{'south':{'texture':'#'+texture,'uv':uv},
                     'north':{'texture':'#'+texture,'uv':[uv[2],uv[1],uv[0],uv[3]]}}}


def standard(frame, fade):
    opened = min(frame/3, 1.)
    phase = 0 if frame < 4 else (frame-4)*math.tau/WIND_FRAMES
    # The pole is an actual thin support, not a voxel drawing of the fabric.
    def rod(lo, hi, ink):
        return {'from':lo,'to':hi,'shade':True,
                'faces':{f:{'texture':'#metal','uv':ink}
                         for f in ('up','down','north','south','east','west')}}
    dark, light = [0,0,1,1], [1,0,2,1]
    pole = [rod([7.65,0,7.65],[8.35,30.5,8.35],dark),
            rod([-6.6,27.1,7.65],[9.2,27.7,8.35],dark),
            rod([7.35,0,7.35],[8.65,1.1,8.65],light),
            rod([7.3,28.7,7.3],[8.7,29.4,8.7],light)]
    if fade:
        pole = [e for i,e in enumerate(pole) if fade < (7 if i==0 else 6)]
    cloth = []
    # 20 rows by 12 columns: four source pixels per panel. Integrate legal folds
    # from the fixed sewn top so adjacent rows meet without cracks or stretched UVs.
    top, depth = 27., 8.
    for row in range(20):
        slope = math.cos(row*.32-phase)*(row/20)*.42
        angle = 22.5 if slope>.17 else -22.5 if slope<-.17 else 0.
        length = .96*opened
        bottom = top-length*math.cos(math.radians(angle))
        next_depth = depth-length*math.sin(math.radians(angle))
        if row >= 20*(7-fade)/7 or opened == 0:
            continue
        for col in range(12):
            x = -6+col
            cy,cz = (top+bottom)/2,(depth+next_depth)/2
            e = face(x,cy-length/2,x+1,cy+length/2,cz,
                     [col*16/12,row*16/20,(col+1)*16/12,(row+1)*16/20])
            if angle:
                e['rotation']={'origin':[x+.5,cy,cz],'axis':'x','angle':angle,'rescale':False}
            cloth.append(e)
        top,depth = bottom,next_depth
    return pole+cloth


def guard_elements(pixels, fade):
    h,w = pixels.shape[:2]
    # Same painted handle point used by approved_hand_display(). Grip is model Z=0.
    unit = 16/(63.5-2)
    elements = []
    for row in range(h):
        # Dissolve coherently from the tip, not independent noisy cubes.
        if fade and row < 2+(63.5-2)*fade/7:
            continue
        start = 0
        while start < w:
            if pixels[row,start,3] < 128:
                start += 1
                continue
            end = start+1
            while end<w and pixels[row,end,3]>=128:
                end += 1
            x0,x1 = 8+(start-10.5)*unit, 8+(end-10.5)*unit
            z0,z1 = (63.5-row-1)*unit, (63.5-row)*unit
            u0,v0,u1,v1 = start*16/w,row*16/h,end*16/w,(row+1)*16/h
            # The painted front follows alpha runs, with a second thin back face.
            # Guard/handle depth differs, but the blade stays a fine sheet.
            thick = .06 if row<42 else .18 if row<57 else .25
            elements.append({'from':[x0,8-thick,z0],'to':[x1,8+thick,z1],'shade':False,
                'faces':{'up':{'texture':'#sword','uv':[u0,v0,u1,v1]},
                         'down':{'texture':'#sword','uv':[u0,v1,u1,v0]}}})
            start = end
    return [] if fade==7 else elements


def voice(variant, frame):
    g = np.zeros((SIZE,SIZE), dtype=np.uint8)
    if frame==VOICE_FRAMES-1:
        return g
    fade = max(0,(frame-4)/15)
    # Open paired lobes, not a closed circle. A crest leads two torn trailing rims;
    # the openings widen as the sound front disperses. Each echo has its own contour.
    for side in (-1,1):
        for i in range(42):
            a = -.98+i*.047
            if abs(a) > .99-fade*.38:
                continue
            if fade>.35 and (i+variant*3)%14 in range(3+int(fade*5)):
                continue
            r = 5.8 + .26*math.sin(a*5+variant) + fade*.55
            width = (.5+.2*math.cos(a*3))*(1-fade)**.7
            def point(angle, radius):
                return (8+side*math.cos(angle)*radius,8+math.sin(angle)*radius*.91)
            polygon(g,[point(a,r-width),point(a,r),point(a+.05,r),point(a+.05,r-width)],3 if frame<7 else 2)
            if frame<11:
                polygon(g,[point(a,r-width-.35),point(a,r-width-.1),
                           point(a+.05,r-width-.1),point(a+.05,r-width-.35)],1)
    return g


def streamer(fade):
    g=np.zeros((SIZE,SIZE),dtype=np.uint8)
    for i in range(40):
        if i/40 < fade/7:
            continue
        z=i*.35+1
        x=8+math.sin(i*.13)*1.5
        w=(.3+(1-i/40)*1.5)*(1-fade/8)
        polygon(g,[(x-w,z),(x+w*.3,z),(x+w*.3+.18,z+.4),(x-w+.18,z+.4)],2)
        polygon(g,[(x-w,z),(x-w+.2,z),(x-w+.38,z+.4),(x-w+.18,z+.4)],3)
    return g


def build(assets,write):
    inks=ink_uvs(assets)
    def emit(name,elements,textures,tint=None):
        key='combat_vfx/'+name
        write(assets/f'models/{key}.json',{'ambientocclusion':False,'textures':textures,'elements':elements})
        item={'type':'minecraft:model','model':'projects:'+key}
        if tint is not None:
            item['tints']=[{'type':'minecraft:constant','value':tint}]
        write(assets/f'items/{key}.json',{'model':item})
    texture_dir=assets/'textures/combat_vfx/warrior_support'
    texture_dir.mkdir(parents=True,exist_ok=True)
    cloth_pixels().save(texture_dir/'cloth.png')
    pixels,_,_=convert()
    Image.fromarray(pixels).save(texture_dir/'guard.png')
    # A material swatch only for the flag's four structural rods.
    metal=Image.new('RGBA',(16,16),(54,60,69,255))
    metal.putpixel((1,0),(163,174,182,255))
    metal.save(texture_dir/'metal.png')
    textures={'cloth':'projects:combat_vfx/warrior_support/cloth','metal':'projects:combat_vfx/warrior_support/metal'}
    for frame in range(4+WIND_FRAMES):
        for fade in range(8):
            emit(f'warrior/standard_{frame}_{fade}',standard(frame,fade),textures)
    for fade in range(8):
        suffix=f'_fade{fade}' if fade else ''
        emit('war_parry_blade_steel'+suffix,guard_elements(pixels,fade),{'sword':'projects:combat_vfx/warrior_support/guard'})
        for palette,tint in (('steel',0xbccbd8),('warred',0xb94c62)):
            emit('war_rally_streamer_'+palette+suffix,geometry(streamer(fade),inks,curved=True),
                 {'0':'projects:combat_vfx/ribbon/slash_5'},tint)
        # A brief narrow glint travelling up the actual blade, not a second sword.
        glint=np.zeros((SIZE,SIZE),dtype=np.uint8)
        if fade<7:
            polygon(glint,[(8,1+fade),(8.5,7),(12-fade*.4,8),(8.5,9),(8,15-fade),(7.5,9),(4+fade*.4,8),(7.5,7)],3)
        emit('war_parry_glint_steel'+suffix,geometry(glint,inks,curved=False),{'0':'projects:combat_vfx/ribbon/slash_5'},0xeaf4ff)
    for variant in range(3):
        for frame in range(VOICE_FRAMES):
            emit(f'warrior_support/voice_{variant}_{frame}',geometry(voice(variant,frame),inks,curved=True),
                 {'0':'projects:combat_vfx/ribbon/slash_5'},0xb54a60 if variant==1 else 0xc6d4dd)


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
