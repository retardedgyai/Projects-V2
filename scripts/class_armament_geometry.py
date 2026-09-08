"""Native cuboid armament source; units are 1/16 block, materials 16x16 pixels.

The high-resolution concept image is NOT mapped onto these models. UV spans
follow face dimensions instead of stretching an entire block texture per box.
"""
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
KINDS = ('greatsword', 'staff', 'bow', 'dagger', 'mace', 'tome', 'astrolabe')
FRAMES = 12
METAL = (
    '2222222233333222', '2222222333322222', '2222223333222222', '2222233332222222',
    '2222333322222222', '2223333222222222', '2233332222222222', '2333322222222222',
    '2333222222211111', '2222222222111111', '2222222221111111', '2222222211111111',
    '2222222111111111', '2222221111111111', '2222211111111110', '2222111111111100')
LEATHER = (
    '1111111111111111', '2222222222222222', '2222222222222222', '1111111111111111',
    '0001111111111111', '1111111111111111', '2222222222222222', '2222222222222222',
    '1111111111111111', '1111111111111000', '1111111111111111', '2222222222222222',
    '2222222222222222', '1111111111111111', '1111111111111111', '0000000000000000')
CRYSTAL = (
    '1111111122222222', '1111111222222222', '1111112222222222', '1111122222222222',
    '1111222222222222', '1112222232222221', '1122222333222211', '1222223333322111',
    '2222233333331111', '2222223333311111', '2222222333111111', '2222222231111111',
    '2222222211111110', '2222222111111100', '2222221111111000', '2222211111110000')
PAGES = (
    '3333333333333333', '2222222222222222', '2222111122222222', '2222222222222222',
    '2222222222222222', '2222222111112222', '2222222222222222', '1111111111111111',
    '2222222222222222', '2221111122222222', '2222222222222222', '2222222222222222',
    '2222222111122222', '2222222222222222', '1111111111111111', '0000000000000000')
MATERIALS = {
    'iron': ('151d29', '263141', '3e4b5c', '64778a'),
    'ivory': ('706e65', 'a9aa98', 'd5d7bf', 'edf0db'),
    'bronze': ('473427', '78603a', 'ae8c4d', 'd9ba78'),
    'leather': ('171519', '2d2228', '44323b', '60494d'),
    'violet': ('181724', '2e293f', '4a405b', '746580'),
    'green': ('12291e', '234330', '3c6340', '688253'),
    'pages': ('84725b', 'b5a17e', 'daceac', 'eee3c4'),
    'navy': ('141b30', '242f50', '394a70', '6178a1'),
    'ember': ('7b2527', 'bb3e29', 'f07b32', 'ffe197'),
    'ice': ('245b86', '338eba', '67cad5', 'ccf5e9'),
    'venom': ('47244f', '823772', 'ce6098', 'ffb5cf'),
    'light': ('a27534', 'd2aa54', 'f3d582', 'fff1c2'),
    'steel': ('303e50', '4c6072', '7b91a1', 'aec3c6'),
    'copper': ('522b29', '8a4c39', 'bd7850', 'e3af73'),
    'cloth': ('211d2c', '393046', '51435e', '6b5877'),
    'moss': ('273127', '424d32', '617044', '849055'),
}


def write_atlas():
    """Compile editable pixel source, no photo/concept resizing or image filtering."""
    atlas = Image.new('RGB', (64, 64))
    for i, (name, palette) in enumerate(MATERIALS.items()):
        pattern = PAGES if name == 'pages' else LEATHER if name in ('leather', 'cloth', 'moss') else CRYSTAL if name in ('ember','ice','venom','light') else METAL
        for y, row in enumerate(pattern):
            for x, digit in enumerate(row):
                atlas.putpixel((i % 4 * 16 + x, i // 4 * 16 + y), tuple(bytes.fromhex(palette[int(digit)])))
    path = ASSETS / 'textures/item/weapons/materials.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(path)


def box(name, lower, upper, material, angle=0, axis='z', origin=None):
    dx, dy, dz = [upper[i]-lower[i] for i in range(3)]
    tile = list(MATERIALS).index(material)
    u, v = tile % 4 * 4, tile // 4 * 4
    faces = {}
    for face, (width, height) in {'north':(dx,dy), 'south':(dx,dy), 'east':(dz,dy), 'west':(dz,dy), 'up':(dx,dz), 'down':(dx,dz)}.items():
        w, h = min(14, width)/4, min(14, height)/4
        faces[face] = {'uv':[u+2-w/2, v+2-h/2, u+2+w/2, v+2+h/2], 'texture':'#atlas'}
    result = {'name':name, 'from':lower, 'to':upper, 'faces':faces}
    if angle:
        result['rotation'] = {'origin':origin or [(a+b)/2 for a,b in zip(lower,upper)], 'axis':axis, 'angle':angle, 'rescale':False}
    return result


def gem(e, name, x, y, z, size, material):
    e.append(box(name+' socket',[x-size*.66,y-size*.66,z-.5],[x+size*.66,y+size*.66,z+.5],'iron',45))
    e.append(box(name+' cut stone',[x-size*.45,y-size*.45,z-.85],[x+size*.45,y+size*.45,z-.55],material,45))


def grip(e, low, high, trim='bronze', x=8, z=8):
    e.append(box('leather wrapped handle',[x-.65,low,z-.65],[x+.65,high,z+.65],'leather'))
    for y in (low,high-.55): e.append(box('handle ferrule',[x-1,y,z-1],[x+1,y+.55,z+1],trim))


def beam(e, name, start, end, width, depth, material):
    """Connected XY strut; endpoints determine a legal vanilla cuboid rotation."""
    if start[1]>end[1]: start,end=end,start
    dx,dy=end[0]-start[0],end[1]-start[1]
    angle=-math.degrees(math.atan2(dx,dy)); length=math.hypot(dx,dy)
    angle=round(angle/22.5)*22.5
    cx,cy,cz=(start[0]+end[0])/2,(start[1]+end[1])/2,(start[2]+end[2])/2
    if abs(angle)>45:
        # Horizontal struts use their X axis as the long axis.
        angle=angle+90 if angle<0 else angle-90
        e.append(box(name,[cx-length/2,cy-width/2,cz-depth/2],[cx+length/2,cy+width/2,cz+depth/2],material,angle))
    else:
        e.append(box(name,[cx-width/2,cy-length/2,cz-depth/2],[cx+width/2,cy+length/2,cz+depth/2],material,angle))


def greatsword(tier, phase):
    e=[]; grip(e,1,8); gem(e,'pommel',8,1,7.35,1.3,'ember')
    e.append(box('deep blade body',[5.7,9,7.1],[10.3,27.3,8.9],'iron'))
    for z,angle in ((6.95,22.5),(8.5,-22.5)):
        e.append(box('left forged face chamfer',[5.6,11,z],[6.65,26.8,z+.55],'steel',angle,'y'))
        e.append(box('right forged face chamfer',[9.35,11,z],[10.4,26.8,z+.55],'iron',-angle,'y'))
    for y,h,x in ((10,5,5),(15,5,5.25),(20,5,5.5),(25,2,6)):
        e.append(box('honed left bevel',[x,y,7.5],[x+.7,y+h,8.5],'ivory'))
        e.append(box('honed right bevel',[16-x-.7,y,7.5],[16-x,y+h,8.5],'steel'))
    e.append(box('angled forged tip',[6.2,26.3,7.5],[9.8,29.9,8.5],'ivory',45))
    for z in (6.85,8.9):
        e.append(box('recessed dark fuller',[7.15,11,z],[8.85,27,z+.25],'navy'))
        for i in range(6):
            y=11.4+i*2.35; x=7.7+(.3 if i%2 else -.3)
            e.append(box('inset ember channel',[x,y,z-.1],[x+.5,y+2.35,z+.35],'ember'))
        for i in range(tier):
            y=12+((phase*1.3+i*4)%14)
            e.append(box('travelling forge spark',[7.55,y,z-.2],[8.1,y+.5,z+.45],'light'))
    for side in (-1,1):
        for level in range(2+(tier>=3)):
            x=8+side*(2.8+level*1.2)
            e.append(box('swept guard wing',[x-1.7,8+level,6.6],[x+1.7,9+level,9.4],'iron',side*22.5))
            e.append(box('guard wing bevel',[x-1.4,8.8+level,6.45],[x+1.4,9.15+level,6.7],'steel',side*22.5))
    gem(e,'guard heart',8,9.6,6.3,2,'ember')
    if tier>=2:
        for x in (5.2,9.8): e.append(box('blade shoulder collar',[x,10,6.9],[x+1,13,9.1],'bronze'))
    if tier==4:
        for side in (-1,1):
            x=8+side*3
            e.append(box('shoulder fang',[x-.55,12.6,6.6],[x+.55,16.2,9.4],'iron',-side*22.5))
    return e


def dagger(tier, phase):
    e=[]; grip(e,2,7,'steel'); gem(e,'ring pommel',8,1.8,7.4,1.6,'venom')
    for y,h,x,w in ((8,3,6,4),(11,4,5.4,4),(15,4,6.1,3.8),(19,2,7.1,2.9)):
        e.append(box('hook blade segment',[x,y,7.2],[x+w,y+h,8.8],'violet'))
        e.append(box('pale outer cutting bevel',[x-.45,y,7.55],[x,y+h,8.45],'steel'))
        e.append(box('venom recess',[x+.85,y,7],[x+1.35,y+h,7.25],'venom'))
    e.append(box('hooked tip',[7.2,20,7.5],[10,22.8,8.5],'steel',-22.5))
    for i in range(1+tier): e.append(box('spine barb',[9.2,9+i*2.4,7.4],[11,10+i*2.4,8.6],'violet',-22.5))
    e.append(box('curved knuckle guard',[4,6.7,6.8],[11,7.8,9.2],'violet',22.5))
    e.append(box('descending guard hook',[10.5,5,7],[11.5,8,9],'steel',22.5))
    gem(e,'guard venom stone',7.5,8,6.5,1.3,'venom')
    y=10+(phase/FRAMES)*8
    e.append(box('venom bead',[6.6,y,6.7],[7.1,y+.7,7.05],'venom'))
    return e


def staff(tier, phase):
    e=[]; grip(e,9,15)
    e.append(box('dark carved staff',[7.4,0,7.4],[8.6,23,8.6],'leather'))
    for y in (1,8,16,21): e.append(box('bronze shaft ring',[7.1,y,7.1],[8.9,y+.65,8.9],'bronze'))
    for side in (-1,1):
        for x,y,a in ((2.1,22,-22.5),(3.8,24,22.5),(3.4,27,22.5)):
            cx=8+side*x
            e.append(box('split crystal cage',[cx-.65,y,7.3],[cx+.65,y+2.7,8.7],'bronze',a*side))
        for j in range(1+(tier>=3)):
            x=8+side*(1.8+j*1.1)
            e.append(box('hanging articulated petal',[x-.6,20-j,6.8],[x+.6,23-j,8.4],'iron',side*22.5))
            e.append(box('petal enamel',[x-.4,20.5-j,6.5],[x+.4,22-j,6.85],'ice'))
    bob=math.sin(phase/FRAMES*math.tau)*.55
    e.append(box('floating elemental crystal',[6.5,24+bob,6.5],[9.5,27+bob,9.5],'ice',45))
    e.append(box('crystal upper facet',[7,27+bob,7],[9,29+bob,9],'ivory',45))
    for i in range(tier):
        a=(phase/FRAMES+i/tier)*math.tau; x,z=8+3*math.cos(a),8+2*math.sin(a)
        e.append(box('orbiting crystal mote',[x-.3,25+bob,z-.3],[x+.3,25.6+bob,z+.3],'ice'))
    return e


def bow(tier, phase):
    e=[]; grip(e,12,16,'bronze',x=6)
    upper=[(6,16,8)]
    for length,angle in ((3,22.5),(3,45),(3,22.5),(2,-22.5)):
        x,y,z=upper[-1]; a=math.radians(angle)
        upper.append((x+length*math.sin(a),y+length*math.cos(a),z))
    for sign in (-1,1):
        points=[(x,14+sign*(y-14),z) for x,y,z in upper]
        for i,(p,q) in enumerate(zip(points,points[1:])):
            beam(e,'continuous horn limb',p,q,1.3,1.3,'ivory')
            if i<tier:
                beam(e,'laminated green limb plate',(p[0],p[1],7.2),(q[0],q[1],7.2),.85,.45,'green')
                x,y,_=p
                e.append(box('limb binding rivet',[x-.3,y-.3,6.9],[x+.3,y+.3,7.2],'bronze'))
        x,y,_=points[-1]
        e.append(box('bronze nock',[x-.65,y-.4,7.2],[x+.65,y+.4,8.8],'bronze'))
    x,y,_=upper[-1]
    e.append(box('taut bowstring',[x-.06,28-y,7.95],[x+.06,y,8.08],'leather'))
    gem(e,'grip wind stone',6,14,6.7,1.4,'ice')
    p,q=upper[1],upper[2]; f=phase/FRAMES
    x,y=p[0]+(q[0]-p[0])*f,p[1]+(q[1]-p[1])*f
    e.append(box('travelling wind glint',[x-.2,y-.3,6.8],[x+.2,y+.3,7.1],'ice'))
    return e


def mace(tier, phase):
    e=[]; grip(e,2,12,'steel')
    e.append(box('reinforced shaft',[7.2,12,7.2],[8.8,20,8.8],'iron'))
    e.append(box('deep oath reliquary',[5,19,5],[11,25,11],'iron'))
    for face in range(4):
        def turn(lo,hi):
            points=[]
            for x in (lo[0],hi[0]):
                for z in (lo[2],hi[2]):
                    dx,dz=x-8,z-8; a=face*math.pi/2
                    points.append((8+dx*math.cos(a)-dz*math.sin(a),8+dx*math.sin(a)+dz*math.cos(a)))
            return [min(p[0] for p in points),lo[1],min(p[1] for p in points)],[max(p[0] for p in points),hi[1],max(p[1] for p in points)]
        for name,lo,hi,mat in (
            ('broad flange',[6.6,18,2.9],[9.4,26,5],'steel'),
            ('flange recessed spine',[7.1,19,2.6],[8.9,25,3],'iron'),
            ('oath inlay',[7.7,19.4,2.45],[8.3,24.6,2.7],'ice'),
            ('flange crown tooth',[6.5,25.5,3.2],[9.5,27,4.5],'iron')):
            lower,upper=turn(lo,hi); e.append(box(name,lower,upper,mat))
    for y in (1,16.7,18): e.append(box('shaft strengthening collar',[6.6,y,6.6],[9.4,y+.65,9.4],'bronze'))
    bob=math.sin(phase/FRAMES*math.tau)*.35
    e.append(box('suspended oath core',[6.8,25.7+bob,6.8],[9.2,28.1+bob,9.2],'ice',45))
    if tier>=3:
        for x in (4.5,10.5): e.append(box('crown spur',[x,24,6],[x+1,27,10],'bronze'))
    for i in range(tier-1):
        e.append(box('ranked reliquary band',[5.7,20+i*1.45,4.7],[10.3,20.45+i*1.45,11.3],'bronze'))
    return e


def tome(tier, phase):
    e=[]
    for side in (-1,1):
        cx=8+side*3.3; angle=side*22.5
        e.append(box('open plum cover',[cx-3.2,5,8],[cx+3.2,17,8.6],'cloth',angle,'y'))
        e.append(box('stack of parchment',[cx-2.9,5.5,7],[cx+2.9,16.5,8],'pages',angle,'y'))
        for y in (5,15.5): e.append(box('brass corner protector',[cx+side*2-1,y,6.85],[cx+side*2+1,y+1.5,8.75],'bronze',angle,'y'))
    e.append(box('bound central spine',[7.4,4.6,7.6],[8.6,17.4,9],'leather'))
    e.append(box('ribbon bookmark',[10,2.8,7.5],[10.8,7,7.8],'cloth'))
    bob=math.sin(phase/FRAMES*math.tau)*.45
    gem(e,'suspended prayer seal',8,21+bob,8,1.7,'light')
    for i in range(2+tier):
        a=i*math.tau/(2+tier)+phase/FRAMES*math.tau
        x,y=8+2.6*math.cos(a),21+bob+2.6*math.sin(a)
        e.append(box('prayer seal ray',[x-.3,y-.3,7.7],[x+.3,y+.3,8.3],'bronze',45))
    return e


def astrolabe(tier, phase):
    e=[]; grip(e,8,14)
    e.append(box('midnight observatory shaft',[7.4,0,7.4],[8.6,22,8.6],'navy'))
    for y in (1,7,15,19): e.append(box('shaft zodiac band',[7,y,7],[9,y+.55,9],'bronze'))
    ring=[(8+x,25+y,8) for x,y in ((-4,-2),(-2,-4),(2,-4),(4,-2),(4,2),(2,4),(-2,4),(-4,2))]
    for i,p in enumerate(ring):
        if i==4: continue
        q=ring[(i+1)%8]
        beam(e,'connected broken orbital brass arc',p,q,.95,1.4,'bronze')
        beam(e,'orbital midnight inset',(p[0],p[1],7.1),(q[0],q[1],7.1),.45,.35,'navy')
    bob=math.sin(phase/FRAMES*math.tau)*.4
    e.append(box('floating star vertical',[7.55,22+bob,7.55],[8.45,28+bob,8.45],'ice'))
    e.append(box('floating star horizontal',[5,24.55+bob,7.55],[11,25.45+bob,8.45],'ice'))
    e.append(box('star diamond core',[7,24+bob,7],[9,26+bob,9],'ivory',45))
    for i in range(2+tier):
        a=(phase/FRAMES+i/(2+tier))*math.tau; x,z=8+5.4*math.cos(a),8+2*math.sin(a); y=25+2.8*math.sin(a)
        e.append(box('orbiting star fragment',[x-.4,y-.4,z-.4],[x+.4,y+.4,z+.4],'ice',45))
    gem(e,'astral pommel',8,1,7.5,1.3,'ice')
    return e


def authored_model(kind, tier, phase=0):
    scale = .72 if kind not in ('dagger','tome') else .8
    return {'credit':'ProjectS original pixel armament / native geometry source', 'gui_light':'front',
        'ambientocclusion':False, 'textures':{'atlas':'projects:item/weapons/materials','particle':'projects:item/weapons/materials'},
        'elements':globals()[kind](tier,phase), 'display':{
            'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,2,1],'scale':[.75]*3},
            'thirdperson_lefthand':{'rotation':[0,90,-55],'translation':[0,2,1],'scale':[.75]*3},
            'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,-1.5],'scale':[scale]*3},
            'firstperson_lefthand':{'rotation':[0,90,-25],'translation':[1.13,3.2,-1.5],'scale':[scale]*3},
            'gui':{'rotation':[0,0,-25],'translation':[-1.4,-2.4,0],'scale':[.40]*3},
            'ground':{'rotation':[0,0,90],'translation':[0,3,0],'scale':[.4]*3},
            'fixed':{'rotation':[0,180,0],'translation':[0,-3,0],'scale':[.45]*3}}}


def item_definition(name):
    base={'type':'minecraft:model','model':f'projects:item/weapons/{name}'}
    animated={'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,
        'fallback':base, 'entries':[{'threshold':i,'model':{'type':'minecraft:model','model':f'projects:item/weapons/{name}_frame{i:02d}'}} for i in range(FRAMES)]}
    return {'model':{'type':'minecraft:select','property':'minecraft:display_context',
        'cases':[{'when':['gui','fixed','ground'],'model':base}], 'fallback':animated}, 'hand_animation_on_swap':False}


def all_models():
    return {f'{kind}_t{tier}':authored_model(kind,tier) for kind in KINDS for tier in range(1,5)}
