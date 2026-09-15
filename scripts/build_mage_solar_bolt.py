"""R09 compact headed bolt. Authors native geometry, never raster pixels.

The original generated atlas is copied byte-for-byte. Distinct hot front,
painted side, rear envelope and narrow wakes are real volumetric surfaces.
"""
import json
import math
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
SOURCE = ROOT / 'assets/combat-vfx/mage-v5/sources/solar-bolt-faces-v01.png'
TEXTURE = 'projects:combat_vfx/mage_material/solar_bolt_faces_v01'
CLIPS = ('solar_bolt_core', 'solar_bolt_shell', 'solar_bolt_spark', 'solar_bolt_wake',
         'solar_bolt_thread', 'solar_bolt_charge', 'solar_bolt_chip')


def surface(start, end, faces, uv, rotation=None):
    result = {'from':start, 'to':end, 'shade':False,
              'faces':{f:{'texture':'#0', 'uv':uv} for f in faces}}
    if rotation:
        result['rotation'] = rotation
    return result


def section(z0, z1, rx, ry, cx=8.0, cy=8.0):
    # Eight sides form a bevelled envelope, not a stack of visible boxes.
    b = min(rx, ry)*.46
    points = [(-rx+b,-ry),(rx-b,-ry),(rx,-ry+b),(rx,ry-b),
              (rx-b,ry),(-rx+b,ry),(-rx,ry-b),(-rx,-ry+b)]
    elements = []
    for side,(a,c) in enumerate(zip(points,points[1:]+points[:1])):
        dx,dy = c[0]-a[0],c[1]-a[1]
        width = math.hypot(dx,dy)
        angle = (math.degrees(math.atan2(dy,dx))+90)%180-90
        x,y = cx+(a[0]+c[0])/2,cy+(a[1]+c[1])/2
        # Broad panels progress from dark rear (left of atlas) to hot nose.
        base = 0 if side in (0,1,2,3) else 8
        # The broad rearward faces must still read as hot energy in the
        # caster's view, not dark red stone. Reserve the darkest atlas band
        # for a small edge; keep peach-to-ivory across the actual envelope.
        # One painted envelope around all eight sides. Mapping a complete
        # 16px painting onto EVERY narrow face made the old bolt a noisy gem.
        v0 = .15+side*.96
        v1 = v0+.96
        uv = [base+3+z0*.30,v0,base+3+z1*.30,v1]
        if side%2:
            uv = [base+4+z0*.235,v0,base+4+z1*.235,v1]
        if abs(abs(angle)-90)<1e-5:
            e = surface([x,y-width/2,z0],[x,y+width/2,z1],('east','west'),uv)
        else:
            e = surface([x-width/2,y,z0],[x+width/2,y,z1],('up','down'),uv,
                        {'origin':[x,y,8],'axis':'z','angle':round(angle)} if abs(angle)>1e-5 else None)
        # Minecraft up/down map U across the cross-section, V down the bolt.
        # Rotating the UV aligns the painting's bright edge with forward +Z.
        for face in e['faces'].values():
            face['rotation'] = 90
        elements.append(e)
    # Octagonal end cap, partitioned into three strips so no square corner
    # protrudes beyond the bevelled side silhouette.
    for z in (z0,z1):
        for y0,y1,half in ((-ry,-ry+b,rx-b),(-ry+b,ry-b,rx),(ry-b,ry,rx-b)):
            # Both visible ends expose the hot core. A dark rear cap made
            # the owner's view an opaque red pellet, unlike R09's luminous mass.
            ubase = 0
            uv = [ubase+4-4*half/rx,12+4*y0/ry,
                  ubase+4+4*half/rx,12+4*y1/ry]
            elements.append(surface([cx-half,cy+y0,z],[cx+half,cy+y1,z],('north','south'),uv))
    return elements


def streak(z0,z1,x,y,width,uv):
    return surface([x-width,y-width*.65,z0],[x+width,y+width*.65,z1],
                   ('north','south','east','west','up','down'),uv)


def mesh(clip):
    if clip=='solar_bolt_core':
        # Offset the bright forward layer slightly instead of a perfectly
        # concentric pellet. The connected angular shoulders remain within
        # the same short envelope, with no extra surrounding explosion.
        return section(0,10,5.1,4.5)+section(10,16,4.4,4.0,cx=8.8,cy=8.6)
    if clip=='solar_bolt_charge':
        return section(0,3,2.6,2.5)+section(3,9,5.1,4.5)
    if clip in ('solar_bolt_shell','solar_bolt_chip'):
        # A small existing fringe of the head, not a miniature complete head.
        # It survives after the main body disappears and drifts independently.
        return [surface([4.8,6.5,6],[10.5,9.5,10],('north','south','east','west','up','down'),[3,9,7,13]),
                surface([8.5,9.5,7],[10.5,11,9],('north','south','east','west','up','down'),[4,10,6,12])]
    if clip=='solar_bolt_spark':
        return [surface([5.5,7,6],[10.5,9,10],('north','south','east','west','up','down'),[11,2,15,5]),
                surface([7,5.5,7],[9,7,9],('north','south','east','west','up','down'),[12,3,14,4])]
    if clip=='solar_bolt_wake':
        # One purposeful kink, not multiple sine-wave tubes or flame badges.
        return [streak(0,4,8,8,.12,[1,3,1,3]),
                streak(4,10,8.12,8.06,.24,[3.8,3,3.8,3]),
                streak(10,16,8.12,8.06,.32,[6,3,6,3])]
    if clip=='solar_bolt_thread':
        return [streak(1,3,10.4,6.6,.13,[10,3,10,3]),
                streak(5,8,9.65,6.9,.20,[12,3,12,3]),
                streak(11,12,9.3,7.1,.30,[14,3,14,3]),
                streak(14,15,9.1,7.3,.32,[14,3,14,3])]
    raise ValueError(clip)


def build():
    assets = PACK/'assets/projects'
    texture = assets/'textures/combat_vfx/mage_material/solar_bolt_faces_v01.png'
    texture.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(SOURCE,texture)
    paths = [texture]
    for clip in CLIPS:
        key = f'combat_vfx/mage_material/{clip}_0'
        for path,value in ((assets/f'models/{key}.json',
                            {'ambientocclusion':False,'textures':{'0':TEXTURE},'elements':mesh(clip)}),
                           (assets/f'items/{key}.json',
                            {'model':{'type':'minecraft:model','model':'projects:'+key}})):
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
            paths.append(path)
    index = PACK/'index.txt'
    entries = set(index.read_text(encoding='utf-8').splitlines())
    entries.update(p.relative_to(PACK).as_posix() for p in paths)
    index.write_text('\n'.join(sorted(entries))+'\n',encoding='utf-8')
    print(f'Solar bolt: {len(CLIPS)} native models; unchanged atlas; {len(paths)} registered pack files')


if __name__=='__main__':
    build()
