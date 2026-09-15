"""R01 Cryomancer: painted, bevelled ice shafts, not a crystal-covered plinth.

Only reads the original bitmap. Profile sampling authors native geometry/UVs;
the shipped texture is a byte-identical copy, including its unused background.
"""
import json
import math
import shutil
import copy
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
SOURCE = ROOT / 'assets/combat-vfx/mage-v5/sources/cryopillar-faces-v02.png'
TEXTURE = 'projects:combat_vfx/mage_material/cryopillar_faces_v02'
CLIPS = ('cryo_pillar', 'cryo_root', 'cryo_buttress', 'cryo_crown', 'cryo_seed')


def profiles():
    rgb = np.asarray(Image.open(SOURCE).convert('RGB')).astype(int)
    h, w = rgb.shape[:2]
    blue = (rgb[:, :, 2] - rgb[:, :, 0] > 12) & (rgb[:, :, 2] > 30)
    # Each continuous row interval includes the white painted interior. Testing
    # every pixel for saturation would incorrectly punch holes in that face.
    result = []
    for lo, hi in ((0, w // 2), (w // 2, w)):
        rows = []
        for y in range(h):
            xs = np.flatnonzero(blue[y, lo:hi])
            if len(xs) >= 4:
                rows.append((y, lo + int(xs[0]) + 1, lo + int(xs[-1])))
        result.append(rows)
    return rgb, result


def footprint(cx, cz, w, d):
    b = min(w, d) * .46
    return [(cx-w+b, cz-d), (cx+w-b, cz-d), (cx+w, cz-d+b),
            (cx+w, cz+d-b), (cx+w-b, cz+d), (cx-w+b, cz+d),
            (cx-w, cz+d-b), (cx-w, cz-d+b)]


def wall(a, b, y0, y1, uv):
    dx, dz = b[0]-a[0], b[1]-a[1]
    length = math.hypot(dx, dz)
    angle = math.degrees(math.atan2(-dz, dx))
    # A plane's two sides share one surface; normalize its rotation to the
    # native element range. The other 180 degrees need no duplicate plane.
    angle = (angle + 90) % 180 - 90
    cx, cz = (a[0]+b[0])/2, (a[1]+b[1])/2
    if abs(abs(angle)-90) < 1e-6:
        start, end = [cx,y0,cz-length/2], [cx,y1,cz+length/2]
        faces = ('west','east')
        rotation = None
    else:
        start, end = [cx-length/2,y0,cz], [cx+length/2,y1,cz]
        faces = ('north','south')
        rotation = {'origin':[cx,y0,cz], 'axis':'y', 'angle':round(angle)} if abs(angle)>1e-6 else None
    # Native north/east face winding runs opposite to south/west. Reflect the
    # UV, not every strip's painting independently about its changing centre.
    element = {'from':start, 'to':end, 'shade':False,
               'faces':{name:{'texture':'#0','uv':[uv[2],uv[1],uv[0],uv[3]]
                             if name in ('north','east') else uv} for name in faces}}
    if rotation:
        element['rotation'] = rotation
    return element


def mesh(clip='cryo_pillar'):
    if clip=='cryo_crown':
        # A broken double shoulder, not a scaled duplicate of the main shaft.
        # Uniform native-coordinate scaling preserves the bevel angles.
        elements=[]
        for scale,anchor in ((.72,(6.9,8,8)),(.46,(10.5,8,8.5))):
            for source in mesh('cryo_pillar'):
                element=copy.deepcopy(source)
                def place(v):
                    return [(c-8)*scale+a for c,a in zip(v,anchor)]
                element['from']=place(element['from'])
                element['to']=place(element['to'])
                if 'rotation' in element:
                    element['rotation']['origin']=place(element['rotation']['origin'])
                elements.append(element)
        return elements
    if clip=='cryo_seed':
        return mesh('cryo_buttress')
    root=clip in ('cryo_root','cryo_buttress')
    rgb, panels = profiles()
    ih, iw = rgb.shape[:2]
    top = max(p[0][0] for p in panels)
    bottom = min(p[-1][0] for p in panels)
    tables = [{y:(lo,hi) for y,lo,hi in p} for p in panels]
    max_width = [max(hi-lo for _,lo,hi in p) for p in panels]
    elements = []
    # Few long planes on the shaft; smaller contour steps on its chisel tip.
    levels = (0,.16,.32,.50,.66,.78,.85,.90,.94,.97,1)
    for f0,f1 in zip(levels,levels[1:]):
        sy0 = top + int((bottom-top)*(1-f1))
        sy1 = top + int((bottom-top)*(1-f0))
        bounds = []
        for table in tables:
            rows = [table[y] for y in range(sy0,sy1+1) if y in table]
            lo,hi = max(v[0] for v in rows),min(v[1] for v in rows)
            if hi <= lo:
                hi = lo + 1
            bounds.append((lo,hi))
        if root:
            # Root slivers remain blue: use the lower painting, while giving
            # them their own narrow tapered silhouette instead of mini towers.
            taper = max(.035, (1-f0)**.65)
            low=clip=='cryo_buttress'
            w,d = (3.6 if low else 2.65)*taper,(2.6 if low else 1.4)*taper
            sy0 = int(bottom-(bottom-top)*(.42*f1))
            sy1 = int(bottom-(bottom-top)*(.42*f0))
            bounds = []
            for table in tables:
                rows = [table[y] for y in range(sy0,sy1+1) if y in table]
                bounds.append((max(v[0] for v in rows),min(v[1] for v in rows)))
            height = 5.0 if low else 12.0
            cx,cz = 8+f0*(12.0 if low else 8.0),8+f0*.5
        else:
            w = max(.04, 2.7*(bounds[0][1]-bounds[0][0])/max_width[0])
            d = max(.04, 1.7*(bounds[1][1]-bounds[1][0])/max_width[1])
            height = 24.0
            base_centres=[sum(table[bottom])/2 for table in tables]
            cx=8+5.4*((sum(bounds[0])/2-base_centres[0])/max_width[0])
            cz=8+3.4*((sum(bounds[1])/2-base_centres[1])/max_width[1])
        y0,y1 = 8+height*f0,8+height*f1
        points = footprint(cx,cz,w,d)
        for side,(a,b) in enumerate(zip(points,points[1:]+points[:1])):
            lo,hi = bounds[0 if side in (0,4) else 1]
            # Bevels take a narrow painted edge rather than repeating the
            # entire front face eight times around the object.
            if side%2:
                lo = max(lo, hi-(hi-lo)*.22)
            uv = [16*lo/iw,16*sy0/ih,16*hi/iw,16*sy1/ih]
            elements.append(wall(a,b,y0,y1,uv))
        # Horizontal ledges/caps close stepped contour bands. Rectangular
        # strips cover an octagon, not a broad floor plate under the cluster.
        lo,hi = bounds[1]
        uv = [16*(lo+hi)/2/iw,16*sy1/ih]*2
        bevel = min(w,d)*.46
        for za,zb,half in ((-d,-d+bevel,w-bevel),(-d+bevel,d-bevel,w),(d-bevel,d,w-bevel)):
            for yy in (y0,y1):
                elements.append({'from':[cx-half,yy,cz+za],'to':[cx+half,yy,cz+zb],
                    'shade':False,'faces':{face:{'texture':'#0','uv':uv} for face in ('up','down')}})
    return elements


def build():
    assets = PACK/'assets/projects'
    texture = assets/'textures/combat_vfx/mage_material/cryopillar_faces_v02.png'
    texture.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(SOURCE,texture)
    paths = [texture]
    for clip in CLIPS:
        key = f'combat_vfx/mage_material/{clip}_0'
        model = assets/f'models/{key}.json'
        item = assets/f'items/{key}.json'
        for path,value in ((model,{'ambientocclusion':False,'textures':{'0':TEXTURE},'elements':mesh(clip)}),
                           (item,{'model':{'type':'minecraft:model','model':'projects:'+key}})):
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
            paths.append(path)
    # Add only our entries; never sweep unrelated generated assets into index.
    index = PACK/'index.txt'
    entries = set(index.read_text(encoding='utf-8').splitlines())
    entries.update(p.relative_to(PACK).as_posix() for p in paths)
    index.write_text('\n'.join(sorted(entries))+'\n',encoding='utf-8')
    print(f'Cryopillar: {len(CLIPS)} native models, unchanged source bitmap, {len(paths)} pack entries')


if __name__ == '__main__':
    build()
