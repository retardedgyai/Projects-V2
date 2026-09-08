"""Native voxel meshes for transient ITEM_DISPLAY combat silhouettes, not UI icons.

XZ-plane geometry has a white-hot edge, saturated body and a darker trailing rim.
Contiguous cells are merged into cuboids. Original slash flipbook textures are added
alongside them; accepted UI textures, fonts and client code are not replaced.
"""
import math
from pathlib import Path
import json
from PIL import Image
from combat_vfx_shapes import AUTHORED_SHAPES, CROSSED, authored_cell

PALETTES = {
    "steel": ("white_concrete", "light_gray_concrete", "gray_concrete"),
    "gold": ("white_concrete", "yellow_concrete", "orange_concrete"),
    "astral": ("white_concrete", "magenta_concrete", "purple_concrete"),
    "ice": ("white_concrete", "light_blue_concrete", "blue_concrete"),
    "fire": ("yellow_concrete", "orange_concrete", "red_concrete"),
    "venom": ("white_concrete", "lime_concrete", "green_concrete"),
    "life": ("white_concrete", "lime_concrete", "cyan_concrete"),
    "shadow": ("light_gray_concrete", "purple_concrete", "black_concrete"),
    "hunter": ("white_concrete", "green_concrete", "brown_concrete"),
    "holy": ("white_concrete", "yellow_concrete", "light_blue_concrete"),
    "lightning": ("white_concrete", "light_blue_concrete", "purple_concrete"),
}


def cell(shape, x, z):
    if shape in AUTHORED_SHAPES:
        return authored_cell(shape, x, z)
    r = math.hypot(x, z)
    a = math.atan2(x, z)
    if shape == "crescent":
        # A continuous swept blade, front-facing in +Z; tapers to sharp tips.
        width = .28 * max(0, math.cos(a / 1.22))
        if abs(a) < 1.9 and .94 - width < r < .99:
            return 0 if r > .91 else 1 if r > .82 else 2
    elif shape == "orbit":
        if .82 < r < .98:
            return 0 if r > .94 else 1 if r > .87 else 2
        if (abs(x) < .045 or abs(z) < .045) and .70 < r < 1:
            return 0
    elif shape == "star":
        # Long four-point nucleus with smaller diagonal rays and a faceted centre.
        limit = max(.23, .98 * abs(math.cos(2 * a)) ** 7, .52 * abs(math.sin(2 * a)) ** 10)
        if r < limit:
            return 0 if r < limit * .52 else 1 if r < limit * .82 else 2
    elif shape == "lance":
        width = .19 * (1 - abs(z)) + .025
        if abs(z) < .99 and abs(x) < width:
            return 0 if abs(x) < width * .3 else 1 if abs(x) < width * .75 else 2
    elif shape == "burst":
        limit = .46 + .49 * abs(math.cos(4 * a)) ** 10
        if .18 < r < limit:
            return 0 if r < .35 else 1 if r < limit * .78 else 2
    elif shape == "rune":
        if .82 < r < .96:
            return 0 if r > .92 else 1
        if abs(math.sin(3 * a)) < .10 and .38 < r < .82:
            return 0
        if .30 < r < .40:
            return 2
    elif shape == "bolt":
        center = .17 * math.sin(z * 13)
        if abs(z) < .98 and abs(x - center) < .09:
            return 0 if abs(x - center) < .035 else 1
    return None


SHAPES = ("crescent", "orbit", "star", "lance", "burst", "rune", "bolt")


def mesh(shape, palette):
    if shape in ('fire_orb', 'meteor_rock', 'meteor_crown'):
        return molten_core(shape)
    if shape in ('arrow_head','rain_arrow','barbed_arrow','mark_arrow','great_arrow','frost_arrow'):
        return ranger_arrow(shape,palette)
    elements = []
    grid = [[cell(shape, (x + .5) / 24 - 1, (z + .5) / 24 - 1) for x in range(48)] for z in range(48)]
    for z, row in enumerate(grid):
        x = 0
        while x < 48:
            ink = row[x]
            end = x + 1
            while end < 48 and row[end] == ink:
                end += 1
            if ink is not None:
                bottom = z + 1
                while bottom < 48 and all(grid[bottom][i] == ink for i in range(x, end)):
                    bottom += 1
                for merged in range(z + 1, bottom):
                    for i in range(x, end):
                        grid[merged][i] = None
                elements.append({
                    "from": [x / 3, 7.75, z / 3], "to": [end / 3, 8.25, bottom / 3],
                    "shade": False,
                    "faces": {face: {"texture": f"#{ink}", "uv": [2, 2, 3, 3]} for face in ("up", "down", "north", "south", "east", "west")},
                })
            x = end
    if shape in ("lance", "bolt", "star") or shape in CROSSED:
        # Cross-section stays visible from the side: beams are not paper-thin ribbons.
        # Swap X/Y for a spear, Y/Z for the star's perpendicular radiant plane.
        axis = (0, 2, 1) if shape in ("star", "star_core", "celestial_core", "star_seed") else (1, 0, 2)
        crossed = [{**e, "from": [e["from"][i] for i in axis], "to": [e["to"][i] for i in axis]} for e in elements]
        elements += crossed
    return {"ambientocclusion": False, "textures": {str(i): f"minecraft:block/{texture}" for i, texture in enumerate(PALETTES[palette])}, "elements": elements}


def scene_rows():
    source = Path(__file__).resolve().parents[1] / "server-minestom/src/main/resources/combat-art/skill-scenes.psv"
    rows = [line.split("|") for line in source.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]
    assert all(len(row) == 12 for row in rows)
    assert len({row[0] for row in rows}) == len(rows)
    return rows


def scene_models():
    return sorted({(shape, row[3]) for row in scene_rows() for shape in row[4:8]})


def build_combat_models(assets, write_json):
    for shape, palette in scene_models():
        assert shape in AUTHORED_SHAPES, shape
        name = f"{shape}_{palette}"
        model = mesh(shape, palette)
        write_json(assets / f"models/combat_vfx/{name}.json", model)
        write_json(assets / f"items/combat_vfx/{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:combat_vfx/{name}"}})
        # Spatial erosion, not collapsing the entire effect into a paper-thin plane.
        for stage in range(1, 8):
            def remains(element):
                center = [(a+b)*.5 for a,b in zip(element['from'],element['to'])]
                noise = (math.sin(center[0]*12.9898+center[1]*37.719+center[2]*78.233)*43758.5453)%1
                return noise > stage/8
            eroded = {**model, "elements": [e for e in model['elements'] if remains(e)]}
            fade = f"{name}_fade{stage}"
            write_json(assets / f"models/combat_vfx/{fade}.json", eroded)
            write_json(assets / f"items/combat_vfx/{fade}.json", {"model": {"type": "minecraft:model", "model": f"projects:combat_vfx/{fade}"}})
    build_slash_frames(assets, write_json)
    build_magic_frames(assets, write_json, 'stellar-burst-pixel-v1.png', 'stellar/burst')
    build_magic_frames(assets, write_json, 'nebula-stream-pixel-v1.png', 'nebula/stream')
    build_magic_frames(assets, write_json, 'shadow-smoke-pixel-v1.png', 'shadow/smoke', {'shadow':0xd6b2ff}, inset=4)
    build_ice_growth(assets, write_json)
    build_pull_chains(assets, write_json)
    build_healing_feather(assets, write_json)
    build_elemental_phrases(assets, write_json)
    build_assassin_phrases(assets, write_json)
    build_ranger_phrases(assets, write_json)
    build_templar_phrases(assets, write_json)
    build_healer_prayers(assets, write_json)
    build_star_weaving(assets, write_json)


def build_star_weaving(assets,write_json):
    """Reuse the authored pixel nebula, tiled along rays; native star links are separate solids."""
    def emit(name,model,tint=None):
        name=f'combat_vfx/{name}'
        write_json(assets/f'models/{name}.json',model)
        item={'type':'minecraft:model','model':f'projects:{name}'}
        if tint is not None: item['tints']=[{'type':'minecraft:constant','value':tint}]
        write_json(assets/f'items/{name}.json',{'model':item})
    for frame in range(16):
        # Crossing tilted planes preserve the rolling crest at grazing view angles.
        elements=[{'from':[0,8,0],'to':[16,8,16],'shade':False,
                   'rotation':{'origin':[8,8,8],'axis':'x','angle':angle},
                   'faces':{'up':{'texture':'#0','uv':[0,16,16,0],'tintindex':0},
                            'down':{'texture':'#0','uv':[0,0,16,16],'tintindex':0}}}
                  for angle in (-45,45)]
        model={'ambientocclusion':False,'textures':{'0':f'projects:combat_vfx/nebula/stream_{frame}'},'elements':elements}
        for palette,color in dict(astral=0xdca6ff,ice=0x85e2ff).items():
            emit(f'weave/crest_{palette}_{frame}',model,color)
    for count in range(1,17):
        for frame in range(16):
            elements=[];textures={}
            for i in range(count):
                local=min(15,frame+int((count-1-i)*frame/(count*3)))
                textures[str(i)]=f'projects:combat_vfx/nebula/stream_{local}'
                z0=(i-.4)*16/count;z1=(i+1.4)*16/count
                lo=max(0,z0);hi=min(16,z1)
                u0=(lo-z0)/(z1-z0)*16;u1=(hi-z0)/(z1-z0)*16
                for angle in (-45,45):
                    # UV rotation maps the original horizontal stream along +Z.
                    elements.append({'from':[0,8,lo],'to':[16,8,hi],'shade':False,
                        'rotation':{'origin':[8,8,8],'axis':'z','angle':angle},
                        'faces':{face:{'texture':f'#{i}','uv':[u0,4,u1,12],
                                      'rotation':90 if face=='up' else 270,'tintindex':0}
                                 for face in ('up','down')}})
            emit(f'weave/ray_{count}_{frame}',{'ambientocclusion':False,'textures':textures,'elements':elements},0xdca6ff)
    textures={str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['astral'])}
    for count in range(1,17):
        for stage in range(8):
            elements=[]
            for i in range(count):
                if (i*5)%8<stage: continue
                z0=i*16/count;z1=(i+1)*16/count
                elements.append(vfx_box([7.7,7.7,z0],[8.3,8.3,z1],1 if i%2 else 0))
            # Endpoint stars have depth. Their length contracts with the exact edge,
            # but their transverse size doesn't inflate as the constellation opens.
            for z in (0,16):
                for layer in range(3):
                    if layer<stage//3: continue
                    w=2.5-layer*.7
                    a=max(0,z-1.5+layer*.4);b=min(16,z+1.5-layer*.4)
                    elements.append(vfx_box([8-w,7.6,a],[8+w,8.4,b],0))
                    elements.append(vfx_box([7.6,8-w,a],[8.4,8+w,b],0 if layer else 1))
            emit(f'weave/link_{count}_{stage}',{'ambientocclusion':False,'textures':textures,'elements':elements})
    spindle=[]
    for z in range(16):
        width=max(.2,3.2*(1-abs(z-7.5)/8))
        spindle.extend([vfx_box([8-width,7.6,z],[8+width,8.4,z+1],0),
                        vfx_box([7.6,8-width,z],[8.4,8+width,z+1],1)])
    for stage in range(8):
        emit('weave_spindle_astral'+(f'_fade{stage}' if stage else ''),
             {'ambientocclusion':False,'textures':textures,
              'elements':[e for i,e in enumerate(spindle) if (i*5)%8>=stage]})


def vfx_box(lo, hi, ink):
    return {'from': lo, 'to': hi, 'shade': False, 'faces': {
        face: {'texture': f'#{ink}', 'uv': [2, 2, 3, 3]}
        for face in ('up', 'down', 'north', 'south', 'east', 'west')}}


def ranger_arrow(shape,palette):
    # Actual shaft, four fletches and a broadhead; all vertices stay in [0,16]
    # along +Z so short/vertical/clipped rays can use the same exact bounds.
    elements=[vfx_box([7.7,7.7,0],[8.3,8.3,13],2)]
    broad=1.1 if shape in ('barbed_arrow','great_arrow') else .7
    for z in range(11,16):
        width=(16-z)*broad*.6
        elements.append(vfx_box([8-width,7.55,z],[8+width,8.45,z+1],0))
        elements.append(vfx_box([7.55,8-width,z],[8.45,8+width,z+1],0))
    for z in range(1,6):
        width=1.0+(5-z)*.45
        for side in (-1,1):
            x=8+side*width
            elements.append(vfx_box([min(8,x),7.7,z],[max(8,x),8.3,z+1],0 if z%2 else 1))
            y=8+side*width
            elements.append(vfx_box([7.7,min(8,y),z],[8.3,max(8,y),z+1],1))
    if shape in ('barbed_arrow','great_arrow'):
        for side in (-1,1):
            x=8+side*2.5
            elements.append(vfx_box([x-.5,7.5,8],[x+.5,8.5,12],0))
    return {'ambientocclusion':False,'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES[palette])},'elements':elements}


def build_ranger_phrases(assets,write_json):
    def save(name,palette,elements):
        write_json(assets/f'models/combat_vfx/{name}.json',{'ambientocclusion':False,
            'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES[palette])},'elements':elements})
        write_json(assets/f'items/combat_vfx/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:combat_vfx/{name}'}})
    def fading(name,palette,elements):
        save(name,palette,elements)
        for stage in range(1,8):
            save(f'{name}_fade{stage}',palette,[e for i,e in enumerate(elements) if (i*5)%8>=stage])

    for name,shape in (('rain_cluster','rain_arrow'),('barbed_rain_cluster','barbed_arrow')):
        cluster=[]
        for x,y in ((0,0),(-4,-2.5),(4,2.5)):
            for e in ranger_arrow(shape,'hunter')['elements']:
                cluster.append({**e,'from':[8+(e['from'][0]-8)*.6+x,8+(e['from'][1]-8)*.6+y,e['from'][2]],
                                   'to':[8+(e['to'][0]-8)*.6+x,8+(e['to'][1]-8)*.6+y,e['to'][2]]})
        fading(f'{name}_hunter','hunter',cluster)

    build_shot_wake_frames(assets,write_json)

    feather=[vfx_box([7.7,7.7,0],[8.3,8.3,16],2)]
    for z in range(1,15):
        width=math.sin(z/16*math.pi)*3.2
        feather.append(vfx_box([8-width,7.6,z],[8+width,8.4,z+.7],0 if z%3 else 1))
    fading('fletching_hunter','hunter',feather)
    tension=[]
    for z in range(16):
        x=8+math.sin(z/16*math.pi)*3
        tension.append(vfx_box([x-.3,7.7,z],[x+.3,8.3,z+1],0 if z>8 else 1))
    save('draw_tension_hunter','hunter',tension)
    hooks=[vfx_box([8,7.7,14],[15,8.3,15],0),vfx_box([14,7.7,8],[15,8.3,15],0),
           vfx_box([9,7.65,12.5],[13,8.35,13.5],1),vfx_box([12.5,7.65,9],[13.5,8.35,13],1)]
    fading('mark_hook_hunter','hunter',hooks)
    jaw=[]
    for i in range(24):
        a=i*math.pi/24;b=(i+1)*math.pi/24
        x0=8+math.cos(a)*6;x1=8+math.cos(b)*6
        z0=8+math.sin(a)*6;z1=8+math.sin(b)*6
        jaw.append(vfx_box([min(x0,x1)-.4,7.8,min(z0,z1)-.4],
                           [max(x0,x1)+.4,8.5,max(z0,z1)+.4],2))
        if i%2==0:
            for tooth in range(3):
                r=5.8-tooth*.5
                x=8+math.cos(a)*r;z=8+math.sin(a)*r;w=.4-tooth*.09
                jaw.append(vfx_box([x-w,8.4,z-w],[x+w,9.2,z+w],0))
    save('snare_jaw_hunter','hunter',jaw)
    mirrored=[{**e,'from':[e['from'][0],e['from'][1],16-e['to'][2]],
                  'to':[e['to'][0],e['to'][1],16-e['from'][2]]} for e in jaw]
    save('snare_jaw_reverse_hunter','hunter',mirrored)


def build_templar_phrases(assets,write_json):
    """Stepped Minecraft solids: articulated equipment, open arches and fractured stone.

    These are physical-looking objects, not a replacement for the pixel flipbooks.
    No smooth vector rings, translucent billboard shields or copied game images.
    """
    def save(name,palette,elements):
        model={'ambientocclusion':False,'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES[palette])},'elements':elements}
        for stage in range(8):
            key=f'{name}_{palette}'+(f'_fade{stage}' if stage else '')
            value=model if not stage else {**model,'elements':[e for i,e in enumerate(elements) if (i*5)%8>=stage]}
            write_json(assets/f'models/combat_vfx/{key}.json',value)
            write_json(assets/f'items/combat_vfx/{key}.json',{'model':{'type':'minecraft:model','model':f'projects:combat_vfx/{key}'}})

    for heavy in (False,True):
        hammer=[vfx_box([7,7,0],[9,9,12],2)]
        for z in (1,3,5,7,9): hammer.append(vfx_box([6.5,6.5,z],[9.5,9.5,z+1],1))
        hammer+= [vfx_box([3,5,11],[13,11,15],2),vfx_box([2,4,12],[14,12,14],1),
                  vfx_box([1,5,11],[3,11,16],0),vfx_box([13,5,11],[15,11,16],0),
                  vfx_box([7,3,11],[9,13,14],0),vfx_box([5,7,14],[11,9,16],0)]
        if heavy:
            for x in (2,6,10,14): hammer.append(vfx_box([x-1,4,14],[x+1,12,16],0))
        save('oath_breaker' if heavy else 'oath_hammer','gold',hammer)

    shield=[]
    # A hollow escutcheon and embossed ribs; the centre isn't an opaque billboard.
    for y in range(16):
        width=min(7,2+y//2) if y<10 else 7-(y-10)//3
        for side in (-1,1):
            x=8+side*width
            shield.append(vfx_box([x-.5,y,7],[x+.5,y+1,9],0))
            if y%2==0: shield.append(vfx_box([x-side*1.5-.5,y,7.25],[x-side*1.5+.5,y+1,8.75],1))
    shield += [vfx_box([3,14,7],[13,15,9],0),vfx_box([7,3,7.5],[9,13,9.5],1),
               vfx_box([4,10,7.5],[12,12,9.5],1)]
    save('oath_shield','gold',shield)
    for side,name in ((-1,'left'),(1,'right')):
        half=[]
        for e in shield:
            lo=e['from'].copy();hi=e['to'].copy()
            if side<0: hi[0]=min(7,hi[0])
            else: lo[0]=max(9,lo[0])
            if hi[0]>lo[0]: half.append({**e,'from':lo,'to':hi})
        save(f'oath_guard_{name}','gold',half)

    # Metre-bucket density: widening the radius must not widen an opaque gold band.
    # Each crest remains about .25m thick, with gaps between four separate fronts.
    for metres in range(1,12):
        n=metres*8; wave=[]
        for x in range(2,n):
            for z in range(2,n):
                radius=math.hypot(x+.5,z+.5)
                if n-2<=radius and math.hypot(x+1,z+1)<=n:
                    height=2+(1 if (x+z)%4==0 else 0)
                    wave.append(vfx_box([8+x/n*16,8,8+z/n*16],
                        [8+(x+1)/n*16,8+height,8+(z+1)/n*16],0 if (x+z)%3 else 1))
        save(f'oath_wave_{metres}','gold',wave)
    fracture=[]
    for step in range(16):
        x=8+((step//3)%2)*2
        fracture.append(vfx_box([x-.5,8,8+step],[x+.5,8.7,9+step],0 if step%4 else 1))
        if step in (5,9,12):
            for branch in range(1,4):
                fracture.append(vfx_box([x+branch,8,8+step+branch*.5],
                    [x+branch+1,8.5,9+step+branch*.5],1))
    save('oath_fracture','steel',fracture)
    rune=[vfx_box([7,2,7],[9,14,9],0),vfx_box([4,6,6],[12,9,10],1),
          vfx_box([6,12,6],[10,15,10],0),vfx_box([6,1,6],[10,4,10],1)]
    save('oath_rune','gold',rune)

    # Arches stand on local +Z; their bottoms remain fixed when opening from zero.
    arch=[]
    for side in (-1,1):
        x=8+side*6
        arch += [vfx_box([x-1,7,8],[x+1,9,19],1),vfx_box([x-1.5,6.5,8],[x+1.5,9.5,10],0)]
        for step in range(6):
            dx=side*(6-step)
            arch.append(vfx_box([8+dx-1,7,18+step],[8+dx+1,9,20+step],0 if step%2 else 1))
        for z in (12,16): arch.append(vfx_box([x-1.5,6.5,z],[x+1.5,9.5,z+1],0))
    arch += [vfx_box([7,7,23],[9,9,25],0),vfx_box([5,7,22],[11,9,23],1)]
    save('oath_arch','gold',arch)
    post=[vfx_box([5,5,8],[11,11,10],2),vfx_box([6,6,10],[10,10,20],1),
          vfx_box([5,5,19],[11,11,21],0),vfx_box([7,7,21],[9,9,24],0)]
    for z in (12,15,18): post.append(vfx_box([5.5,5.5,z],[10.5,10.5,z+1],0))
    save('oath_boundary','gold',post)
    stone=[vfx_box([2,4,3],[12,11,12],2),vfx_box([4,3,5],[14,10,14],1),
           vfx_box([2,9,3],[9,12,10],0),vfx_box([9,6,10],[13,9,15],1)]
    armor=[vfx_box([3,5,2],[11,8,12],1),vfx_box([2,4,2],[5,9,12],0),
           vfx_box([4,4,10],[13,9,13],0),vfx_box([7,5,3],[10,8,9],2)]
    save('oath_stone_chip','steel',stone);save('oath_armor_chip','gold',armor)


def build_healer_prayers(assets,write_json):
    master=Path(__file__).resolve().parents[1]/'assets/combat-vfx/purifying-light-pixel-v1.png'
    source=Image.open(master).convert('L')
    def emit(name,model,tint=None):
        write_json(assets/f'models/combat_vfx/{name}.json',model)
        item={'type':'minecraft:model','model':f'projects:combat_vfx/{name}'}
        if tint is not None: item['tints']=[{'type':'minecraft:constant','value':tint}]
        write_json(assets/f'items/combat_vfx/{name}.json',{'model':item})
    def planes(lo,hi,texture,uv=(0,16,16,0)):
        return [{'from':[0,8,lo],'to':[16,8,hi],'shade':False,
            'rotation':{'origin':[8,8,8],'axis':'z','angle':angle,'rescale':False},
            'faces':{'up':{'texture':texture,'uv':list(uv),'tintindex':0},
                     'down':{'texture':texture,'uv':[uv[0],16-uv[1],uv[2],16-uv[3]],'tintindex':0}}}
            for angle in (-45,45)]
    for frame in range(16):
        x,y=frame%4,frame//4
        crop=source.crop((round(x*source.width/4),round(y*source.height/4),
            round((x+1)*source.width/4),round((y+1)*source.height/4)))
        tile=Image.new('L',(32,64),0)
        tile.paste(crop.resize((12,28),Image.Resampling.NEAREST).resize((24,56),Image.Resampling.NEAREST),(4,4))
        tile=tile.point(lambda v: 0 if v<40 else 85 if v<128 else 170 if v<213 else 255)
        alpha=tile.point(lambda v: 255 if v else 0)
        name=f'prayer/light_{frame}'
        path=assets/f'textures/combat_vfx/{name}.png';path.parent.mkdir(parents=True,exist_ok=True)
        Image.merge('RGBA',(tile,tile,tile,alpha)).save(path)
        emit(f'prayer/column_holy_{frame}',{'ambientocclusion':False,
            'textures':{'0':f'projects:combat_vfx/{name}'},'elements':planes(8,24,'#0')},0xffedb0)
    for count in range(1,25):
        for frame in range(16):
            textures={};elements=[]
            for segment in range(count):
                local=min(15,frame+int((count-1-segment)*frame/(count*3)))
                textures[str(segment)]=f'projects:combat_vfx/prayer/light_{local}'
                z0=(segment-.4)*16/count;z1=(segment+1.4)*16/count
                lo=max(0,z0);hi=min(16,z1)
                v0=(lo-z0)/(z1-z0)*16;v1=(hi-z0)/(z1-z0)*16
                u0,u1=(16,0) if segment%2 else (0,16)
                elements+=planes(lo,hi,f'#{segment}',(u0,16-v0,u1,16-v1))
            emit(f'prayer/ray_{count}_{frame}',{'ambientocclusion':False,'textures':textures,'elements':elements},0xffedb0)
    def solid(name,elements):
        for stage in range(8):
            suffix=f'_fade{stage}' if stage else ''
            emit(f'{name}_holy{suffix}',{'ambientocclusion':False,
                'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['holy'])},
                'elements':elements if not stage else [e for i,e in enumerate(elements) if (i*5)%8>=stage]})
    lantern=[vfx_box([3,3,3],[13,5,13],1),vfx_box([4,2,4],[12,3,12],2),
             vfx_box([3,12,3],[13,14,13],1),vfx_box([6,14,6],[10,16,10],0)]
    for x in (3,11):
        for z in (3,11): lantern.append(vfx_box([x,5,z],[x+2,12,z+2],0))
    # Open windows: the separate animated light is visible through the cage.
    solid('prayer_lantern',lantern)
    sword=[vfx_box([7,7,0],[9,9,5],1),vfx_box([6,6,0],[10,10,2],0),
           vfx_box([2,6,4],[14,10,6],1),vfx_box([1,5,3],[3,11,6],0),vfx_box([13,5,3],[15,11,6],0)]
    for z in range(6,16):
        width=3 if z<12 else (16-z)*.6
        sword += [vfx_box([8-width,7,z],[8+width,9,z+1],0),
                  vfx_box([7.6,6.7,z],[8.4,9.3,z+1],2)]
    solid('prayer_sword',sword)
    petal=[]
    for z in range(16):
        width=max(.5,3.5*(1-abs(z-7)/9))
        petal += [vfx_box([8-width,7.5,z],[8+width,8.5,z+1],0 if z>10 else 1),
                  vfx_box([7.6,7.2,z],[8.4,8.8,z+1],0)]
    solid('guidance_petal',petal)


def molten_core(shape):
    # A shell with visible molten fault lines, not two crossing orb drawings.
    # Stay inside [0,16] on every axis: firebolt's clipped ray uses these bounds.
    cells={}
    for x in range(7):
        for y in range(7):
            for z in range(7):
                d=(x-3)**2+(y-3)**2+(z-3)**2
                if d<=11:
                    crack=(x+2*y-z)%5==0 or (x-y+2*z)%7==0
                    cells[x,y,z]=0 if crack else (1 if shape=='fire_orb' else 3)
    elements=[]
    for (x,y,z),ink in cells.items():
        if all((x+dx,y+dy,z+dz) in cells for dx,dy,dz in
               ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))):
            continue
        elements.append(vfx_box([1+2*x,1+2*y,1+2*z],[3+2*x,3+2*y,3+2*z],ink))
    textures={str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['fire'])}
    textures['3']='minecraft:block/blackstone'
    return {'ambientocclusion':False,'textures':textures,'elements':elements}


def build_elemental_phrases(assets, write_json):
    def save(name, palette, elements):
        colors=PALETTES[palette]
        if palette=='fire': colors=('white_concrete','yellow_concrete','orange_concrete')
        write_json(assets/f'models/combat_vfx/{name}.json',{'ambientocclusion':False,
            'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(colors)},
            'elements':elements})
        write_json(assets/f'items/combat_vfx/{name}.json',
            {'model':{'type':'minecraft:model','model':f'projects:combat_vfx/{name}'}})

    # Twelve changing silhouettes: the lower flame ignites, forks and releases
    # detached tips. Local +Z becomes vertical; the root is the (8,8,8) pivot.
    for frame in range(12):
        t=frame/11
        elements=[]
        for tongue in range(3):
            height=(15 if tongue==1 else 10)*(1-.45*t)+(2 if frame in (1,2,3) else 0)
            for row in range(math.ceil(height)):
                u=row/height
                if frame>=7 and row < (frame-6)*1.2:
                    continue  # the root burns away, leaving rising broken tips
                center=8+(tongue-1)*3+round((u*u*5+math.sin(frame*.85+tongue)*u*2)*(-1 if tongue==0 else 1))
                width=max(.55,(2.7 if tongue==1 else 1.7)*(1-u)**.7)
                for column in range(-math.ceil(width),math.ceil(width)+1):
                    if abs(column)>width: continue
                    ink=0 if abs(column)<width*.4 and u<.72 else 1 if abs(column)<width*.8 else 2
                    depth=max(.45,(1-u)*1.7)
                    elements.append(vfx_box([center+column-.5,8-depth,8+row],
                                            [center+column+.5,8+depth,9+row],ink))
        save(f'flame_plume_fire_{frame}','fire',elements)
        # Same flame anatomy, flowing back from the projectile head at +Z.
        # Normalize to [0,16] so no frame extends beyond a clipped ray endpoint.
        wake=[{**e,'from':[e['from'][0],e['from'][1],16-(e['to'][2]-8)*16/18],
                    'to':[e['to'][0],e['to'][1],16-(e['from'][2]-8)*16/18]} for e in elements]
        save(f'fire_wake_fire_{frame}','fire',wake)

    # Four different fork paths, then four stages of broken residual charge.
    # Voxel-stair segments have thickness in all axes, with a white conductor
    # inside a blue rim. +Z spans the radial direction, never a vertical spear.
    for frame in range(8):
        elements=[]
        variant=frame%4
        for branch in range(3):
            begin=0 if branch==0 else 5+branch*2
            previous=0.0
            for z in range(begin,16):
                if frame>=4 and (z+branch*2)%(frame-2)==0: continue
                x=(math.sin((z//3)*2.1+variant)*3 if branch==0 else
                   math.sin((begin//3)*2.1+variant)*3+(branch*2-3)*(z-begin)*.75+math.sin(z*.8+variant)*.8)
                if z==begin: previous=x
                y=8+round(math.sin(z*.65+variant+branch)*.8)
                lo=8+min(previous,x)-.4;hi=8+max(previous,x)+.4
                elements.append(vfx_box([lo-.35,y-.5,z],[hi+.35,y+.5,z+1],1))
                elements.append(vfx_box([lo,y-.65,z+.15],[hi,y+.65,z+.85],0))
                previous=x
        save(f'storm_branch_lightning_{frame}','lightning',elements)

    # A broad, curved frost crest: low serrated ridge with a broken lip, not a
    # ring of upright garden crystals. The +Z height axis shares the ground pivot.
    crest=[]
    for column in range(-10,11):
        x=8+column*.7
        y=8+column*column*.021
        height=3.4+(2.0 if column%5==0 else .8 if column%3==0 else 0)
        for row in range(math.ceil(height)):
            taper=max(.32,1-row/height)
            crest.append(vfx_box([x-.36,y-taper,8+row],[x+.36,y+taper,9+row],
                                 0 if row>=height-1.2 else 1))
    save('frost_crest_ice','ice',crest)
    for stage in range(1,8):
        save(f'frost_crest_ice_fade{stage}','ice',[e for i,e in enumerate(crest) if (i*5)%8>=stage])


def build_healing_feather(assets, write_json):
    # Not a borrowed UI glyph: a thick quill with separated, stepped barbs. The
    # feather is independently articulated by the server to form wings or wind.
    elements=[]
    def box(lo,hi,ink):
        return {'from':lo,'to':hi,'shade':False,'faces':{
            f:{'texture':f'#{ink}','uv':[2,2,3,3]} for f in ('up','down','north','south','east','west')}}
    elements.append(box([7.65,7.7,.5],[8.35,8.3,15.5],0))
    widths=(.6,1.3,2.2,3.1,3.8,4.2,4.1,3.7,3.1,2.5,1.7,.8)
    for row,width in enumerate(widths):
        for side in (-1,1):
            for step in range(math.ceil(width*2)):
                x=8+side*(step*.5+.55)
                z=2+row+step*.18+(0 if side==1 else .35)
                elements.append(box([x-.3,7.75,z],[x+.3,8.25,z+.78],
                                    0 if step<width*.8 else 1 if side==1 else 2))
    for stage in range(8):
        kept=elements if stage==0 else [e for i,e in enumerate(elements) if i%8>=stage]
        name='combat_vfx/feather_plume_life'+(f'_fade{stage}' if stage else '')
        write_json(assets/f'models/{name}.json',{'ambientocclusion':False,
            'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['life'])},'elements':kept})
        write_json(assets/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:{name}'}})


def build_pull_chains(assets, write_json):
    # Short alternating three-dimensional links. Runtime chooses link count from
    # endpoint distance instead of stretching four enormous loops across the map.
    for count in range(1,13):
        elements=[]
        span=16/count
        for i in range(count):
            z0=i*span; z1=(i+1)*span
            bars=(([5.8,7.55,z0],[6.55,8.45,z1]),([9.45,7.55,z0],[10.2,8.45,z1]),
                  ([5.8,7.55,z0],[10.2,8.45,z0+span*.16]),
                  ([5.8,7.55,z1-span*.16],[10.2,8.45,z1]))
            for j,(lo,hi) in enumerate(bars):
                if i%2: lo=[lo[1],lo[0],lo[2]];hi=[hi[1],hi[0],hi[2]]
                elements.append({'from':lo,'to':hi,'shade':False,
                    'faces':{face:{'texture':f'#{0 if j==0 else 1 if j<3 else 2}','uv':[2,2,3,3]}
                             for face in ('up','down','north','south','east','west')}})
        name=f'combat_vfx/chain_{count}_gold'
        write_json(assets/f'models/{name}.json',{'ambientocclusion':False,
            'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['gold'])},'elements':elements})
        write_json(assets/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:{name}'}})


def build_ice_growth(assets, write_json):
    # Real stepped volume, not two crossing crystal silhouettes. Local +Z is height
    # after the runtime's -90 degree pitch. The base pivot is (8,8,8), so scale grows
    # out of the terrain instead of lifting a centre-pivoted sprite above it.
    elements=[]
    for x,y,height,width in ((8,8,16,3.1),(3.6,8.8,9,1.7),(12,7.1,11,1.8)):
        for i in range(8):
            w=width*(1-i/8)**.65
            elements.append({'from':[x-w,y-w*.75,8+height*i/8],
                'to':[x+w,y+w*.75,8+height*(i+1)/8],'shade':False,
                'faces':{face:{'texture':f'#{ink}','uv':[2,2,3,3]} for face,ink in
                    (('up',1),('down',2),('north',2),('south',0),('east',0),('west',1))}})
    name='combat_vfx/ice_growth_ice'
    write_json(assets/f'models/{name}.json',{'ambientocclusion':False,
        'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['ice'])},'elements':elements})
    write_json(assets/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:{name}'}})

    for stage in range(1,8):
        fade=f'{name}_fade{stage}'
        write_json(assets/f'models/{fade}.json',{'ambientocclusion':False,
            'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(PALETTES['ice'])},
            'elements':[e for i,e in enumerate(elements) if (i*5)%8>=stage]})
        write_json(assets/f'items/{fade}.json',{'model':{'type':'minecraft:model','model':f'projects:{fade}'}})


def build_assassin_phrases(assets, write_json):
    def save(name,palette,elements):
        colors=PALETTES[palette]
        if name.startswith('shadow_echo'): colors=('light_gray_concrete','purple_concrete','magenta_concrete')
        write_json(assets/f'models/combat_vfx/{name}.json',{'ambientocclusion':False,
            'textures':{str(i):f'minecraft:block/{t}' for i,t in enumerate(colors)},'elements':elements})
        write_json(assets/f'items/combat_vfx/{name}.json',
            {'model':{'type':'minecraft:model','model':f'projects:combat_vfx/{name}'}})
    def fading(name,palette,elements):
        save(name,palette,elements)
        for stage in range(1,8):
            save(f'{name}_fade{stage}',palette,[e for i,e in enumerate(elements) if (i*5)%8>=stage])

    # Minecraft-proportioned phantom, sliced horizontally so the lower body can
    # tear away into strips. Eight poses change the limbs and dissolve the body.
    for frame in range(8):
        parts=[]
        limbs=((4,12,4,12,16,24),(4,12,6,10,4,16),(0,4,6,10,4,16),
               (12,16,6,10,4,16),(4,8,6,10,-8,4),(8,12,6,10,-8,4))
        for limb,(x0,x1,y0,y1,z0,z1) in enumerate(limbs):
            step=z1-z0 if frame<4 else 1
            for z in range(z0,z1,step):
                if frame>=4 and (z+limb*3)%8<frame-3: continue
                stride=math.sin(frame*.65)*(1 if limb%2==0 else -1)*(16-z)*.14 if limb>=2 else 0
                tear=max(0,frame-3)*(24-z)*.045
                parts.append(vfx_box([x0+tear,y0+stride,z],[x1+tear,y1+stride,z+step],1 if limb<2 else 2))
        save(f'shadow_echo_shadow_{frame}','shadow',parts)

    streaks=[]
    for z in range(16):
        width=(1-z/16)*4.5+.35
        streaks.append(vfx_box([8-width*.36,8-width*.3,z],[8+width*.36,8+width*.3,z+1],0))
        for side in (-1,1):
            x=8+side*width*.65
            streaks.append(vfx_box([x-width*.28,7.7,z],[x+width*.28,8.3,z+1],1))
            y=8+side*width*.65
            streaks.append(vfx_box([7.7,y-width*.28,z],[8.3,y+width*.28,z+1],1))
    fading('piercing_wake_shadow','shadow',streaks)

    # An individual hooked fang, not the old two-fang symbol. Each blade closes
    # independently; mirrored anatomy uses a distinct model, not negative scale.
    fang=[]
    for z in range(16):
        x=5+(z/15)**2*6
        width=max(.35,2.2*(1-z/16))
        fang.append(vfx_box([x-width,7.4,z],[x+width,8.6,z+1],0 if z>10 else 1))
    fading('venom_fang_venom','venom',fang)
    mirror=[{**e,'from':[16-e['to'][0],e['from'][1],e['from'][2]],
                  'to':[16-e['from'][0],e['to'][1],e['to'][2]]} for e in fang]
    fading('venom_fang_reverse_venom','venom',mirror)
    drops=[]
    for z in range(12):
        width=(2.5*math.sin((z+1)/8*math.pi/2) if z<4 else max(.35,3.0*(1-(z-4)/8)))
        drops.append(vfx_box([8-width,8-width,2+z],[8+width,8+width,3+z],0 if z>=9 else 1 if z>=3 else 2))
    fading('venom_bead_venom','venom',drops)


def build_shot_wake_frames(assets,write_json):
    """Short crossed pixel ribbons, never one 64px picture stretched over a 23m ray.

    Native element Z rotation keeps the crossed planes perpendicular to the ray
    under pitch/yaw. Only two faces per segment, and still one display per ray.
    """
    master=Path(__file__).resolve().parents[1]/'assets/combat-vfx/shot-wake-pixel-v1.png'
    source=Image.open(master).convert('L')
    for frame in range(16):
        x,y=frame%4,frame//4
        crop=source.crop((round(x*source.width/4),round(y*source.height/4),
                          round((x+1)*source.width/4),round((y+1)*source.height/4)))
        tile=Image.new('L',(64,32),0)
        # Coarse connected clusters remain readable at the actual metre scale.
        logical=crop.resize((28,14),Image.Resampling.NEAREST)
        tile.paste(logical.resize((56,28),Image.Resampling.NEAREST),(4,2))
        tile=tile.point(lambda v: 0 if v<40 else 85 if v<128 else 170 if v<213 else 255)
        # Source left is the forward-facing point; image top maps to model +Z.
        tile=tile.transpose(Image.Transpose.ROTATE_270)
        alpha=tile.point(lambda v: 255 if v else 0)
        target=assets/f'textures/combat_vfx/shot/wake_{frame}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        Image.merge('RGBA',(tile,tile,tile,alpha)).save(target)
    for count in range(1,13):
        for frame in range(16):
            elements=[]; textures={}
            for segment in range(count):
                # Alternate phases/mirroring break a repeated-stamp silhouette.
                local=min(15,frame+int((count-1-segment)*frame/(count*3)))
                textures[str(segment)]=f'projects:combat_vfx/shot/wake_{local}'
                z0=(segment-.4)*16/count; z1=(segment+1.4)*16/count
                lo=max(0,z0); hi=min(16,z1)
                v0=(lo-z0)/(z1-z0)*16; v1=(hi-z0)/(z1-z0)*16
                u0,u1=(16,0) if segment%2 else (0,16)
                for angle in (-45,45):
                    elements.append({'from':[0,8,lo],'to':[16,8,hi],'shade':False,
                        'rotation':{'origin':[8,8,8],'axis':'z','angle':angle,'rescale':False},
                        'faces':{'up':{'texture':f'#{segment}','uv':[u0,16-v0,u1,16-v1],'tintindex':0},
                                 'down':{'texture':f'#{segment}','uv':[u0,v0,u1,v1],'tintindex':0}}})
            for palette,color in dict(hunter=0xe8ffa3,ice=0x85e2ff).items():
                name=f'combat_vfx/shot/wake_{palette}_{count}_{frame}'
                write_json(assets/f'models/{name}.json',{'ambientocclusion':False,'textures':textures,'elements':elements})
                write_json(assets/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:{name}',
                    'tints':[{'type':'minecraft:constant','value':color}]}})


def build_magic_frames(assets, write_json, source, family, colors=None, inset=0):
    master = Path(__file__).resolve().parents[1] / 'assets/combat-vfx' / source
    atlas = Image.open(master).convert('L')
    for frame in range(16):
        x,y=frame%4,frame//4
        tile=atlas.crop((round(x*atlas.width/4),round(y*atlas.height/4),
                         round((x+1)*atlas.width/4),round((y+1)*atlas.height/4)))
        tile=tile.resize((64-2*inset,64-2*inset),Image.Resampling.NEAREST)
        if inset:
            padded=Image.new('L',(64,64),0)
            padded.paste(tile,(inset,inset))
            tile=padded
        tile=tile.point(lambda v: 0 if v<32 else 64 if v<96 else 128 if v<160 else 192 if v<224 else 255)
        alpha=tile.point(lambda v: 255 if v else 0)
        for box in ((0,0,64,4),(0,60,64,64),(0,0,4,64),(60,0,64,64)):
            assert alpha.crop(box).getbbox() is None, f'{master.name} frame {frame}: clipped border'
        target=assets/f'textures/combat_vfx/{family}_{frame}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        Image.merge('RGBA',(tile,tile,tile,alpha)).save(target)
        name=f'combat_vfx/{family}_{frame}'
        write_json(assets/f'models/{name}.json',{
            'ambientocclusion':False,'textures':{'0':f'projects:{name}'},
            'elements':[{'from':[0,8,0],'to':[16,8,16],'shade':False,'faces':{
                'up':{'texture':'#0','uv':[0,16,16,0],'tintindex':0},
                'down':{'texture':'#0','uv':[0,0,16,16],'tintindex':0}}}]})
        for palette,color in (colors if colors is not None else dict(astral=0xdca6ff,ice=0x85e2ff)).items():
            write_json(assets/f'items/combat_vfx/{family}_{palette}_{frame}.json',
                {'model':{'type':'minecraft:model','model':f'projects:{name}',
                          'tints':[{'type':'minecraft:constant','value':color}]}})


def build_slash_frames(assets, write_json):
    master = Path(__file__).resolve().parents[1] / 'assets/combat-vfx/slash-pixel-atlas-v1.png'
    atlas = Image.open(master).convert('RGB')
    layout=json.loads(master.with_suffix('.layout.json').read_text())
    assert list(atlas.size)==layout['sourceSize'] and len(layout['frames'])==16
    colors = dict(steel=0xeaf4ff,gold=0xffd87b,astral=0xdca6ff,ice=0x85e2ff,fire=0xffab52,
                  venom=0xb5ff5a,life=0xadffcb,shadow=0xe1b5ff,hunter=0xe8ffa3,holy=0xffedb0,lightning=0xb9dfff)
    for frame in range(16):
        # Generated rows are not perfectly evenly spaced: use inspected frame rectangles,
        # including their empty gutters, rather than cutting a shard into the next frame.
        tile=atlas.crop(layout['frames'][frame])
        # This source was authored as pixel clusters, not the rejected smooth atlas.
        # Import to its 64px logical grid without smoothing and normalize the four inks.
        # RGB black is background, not a translucent rectangle; frame breakup supplies the fade.
        tile=tile.resize((64,64),Image.Resampling.NEAREST).convert('L')
        tile=tile.point(lambda v: 0 if v<32 else 64 if v<96 else 128 if v<160 else 192 if v<224 else 255)
        alpha=tile.point(lambda v: 255 if v else 0)
        for box in ((0,0,64,4),(0,60,64,64),(0,0,4,64),(60,0,64,64)):
            assert alpha.crop(box).getbbox() is None, f'{master.name} frame {frame}: artwork reaches tile gutter'
        rgba=Image.merge('RGBA',(tile,tile,tile,alpha))
        texture=assets/f'textures/combat_vfx/ribbon/slash_{frame}.png'
        texture.parent.mkdir(parents=True,exist_ok=True);rgba.save(texture)
        # Vanilla FaceInfo.UP starts at MIN_Z. Reverse V so the image top is +Z.
        # The underside maps to the same world texels. Return cuts mirror U, never negative scale.
        for reverse in (False,True):
            name=f'slash_{"reverse_" if reverse else ""}'
            u0,u1=(16,0) if reverse else (0,16)
            model={"ambientocclusion":False,"textures":{"0":f"projects:combat_vfx/ribbon/slash_{frame}"},
                   "elements":[{"from":[0,8,0],"to":[16,8,16],"shade":False,
                      "faces":{"up":{"texture":"#0","uv":[u0,16,u1,0],"tintindex":0},
                               "down":{"texture":"#0","uv":[u0,0,u1,16],"tintindex":0}}}]}
            write_json(assets/f'models/combat_vfx/ribbon/{name}{frame}.json',model)
            for palette,color in colors.items():
                write_json(assets/f'items/combat_vfx/ribbon/{name}{palette}_{frame}.json',
                           {"model":{"type":"minecraft:model","model":f"projects:combat_vfx/ribbon/{name}{frame}",
                                     "tints":[{"type":"minecraft:constant","value":color}]}})


if __name__ == "__main__":
    pack = Path(__file__).resolve().parents[1] / "server-minestom/src/main/resources/core-ui-pack"
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")
    build_combat_models(pack / "assets/projects", write)
    (pack / "index.txt").write_text("\n".join(sorted(str(p.relative_to(pack)).replace("\\", "/") for p in pack.rglob("*") if p.is_file() and p.name != "index.txt")) + "\n", encoding="utf-8")
