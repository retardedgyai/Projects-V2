"""R04 radial ice / R11 broad icy planes. Native geometry only, no raster edits.

One immutable model per rooted cluster. A coarse painted face spans the entire
shaft; contour strips never repeat a complete painting on every little face.
"""
import json
import math
import shutil
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT/'server-minestom/src/main/resources/core-ui-pack'
SOURCE = ROOT/'assets/combat-vfx/mage-v5/sources/rime-faces-v01.png'
TEXTURE = 'projects:combat_vfx/mage_material/rime_faces_v01'
CLIPS = ('rime_footing', 'rime_inner_a', 'rime_inner_b', 'rime_inner_c',
         'rime_outer_a', 'rime_outer_b', 'rime_outer_c')

# angle, root radius, tip radius, height, half width, half depth (native units).
# Unequal lengths, inclinations and gaps: not six copies of one crystal fan.
GROUPS = {
    'rime_inner_a': [(-39,3.2,7.4,3.1,1.3,1.1),(-5,3.1,8.9,5.4,1.2,1.1),(31,3.4,7.8,3.7,1.1,.9)],
    'rime_inner_b': [(-44,3.3,7.5,4.3,1.1,1),(-12,3.2,8.6,3.8,1.4,1.2),(23,3.4,8.1,5.2,1.1,.8)],
    'rime_inner_c': [(-29,3.1,8.3,5.1,1.2,1),(4,3.3,7.7,3.4,1.3,.9),(41,3.2,8.8,4.0,1.1,1.1)],
    'rime_outer_a': [(-38,7.4,11.5,3.0,1.1,1.0),(-16,5.7,15.3,7.4,2.1,1.7),(8,7.5,11.4,3.8,1.4,1.1),(39,6.7,13.0,5.0,1.8,1.2)],
    'rime_outer_b': [(-36,6.8,13.0,5.4,1.7,1.3),(-13,5.8,14.8,8.0,2.3,1.7),(12,8.1,11.2,3.4,1.3,1.0),(42,7.0,11.9,4.0,1.5,1.1)],
    'rime_outer_c': [(-43,7.3,11.2,3.6,1.3,1.0),(-21,6.3,13.4,5.5,1.8,1.4),(11,5.9,15.5,7.6,2.1,1.6),(36,8.0,11.4,3.2,1.4,1.2)],
}


def face_band(a, b, top_a, top_b, panel, steps=48, start_fraction=0.0, end_fraction=1.0):
    """Coplanar stepped contour, NOT stairs across the broad painted surface.

    Vanilla 26.2 supports element Euler XYZ. A triangle is sampled only along
    its silhouette; every strip shares the exact same face normal and painting.
    """
    a,b,top_a,top_b=(np.asarray(p,dtype=float) for p in (a,b,top_a,top_b))
    base=(a+b)/2
    u=(b-a)/np.linalg.norm(b-a)
    delta=(top_a+top_b)/2-base
    shear=float(np.dot(delta,u))
    v=delta-u*shear
    h=float(np.linalg.norm(v));v/=h
    n=np.cross(u,v)
    matrix=np.column_stack((u,v,n))
    ry=math.asin(max(-1,min(1,-matrix[2,0])))
    if abs(math.cos(ry))>1e-7:
        rx=math.atan2(matrix[2,1],matrix[2,2])
        rz=math.atan2(matrix[1,0],matrix[0,0])
    else:
        rx=0.0
        rz=math.atan2(-matrix[0,1],matrix[1,1])
    rotation={'origin':base.tolist(),'x':math.degrees(rx),'y':math.degrees(ry),'z':math.degrees(rz)}
    width=float(np.linalg.norm(b-a))
    top_width=float(np.linalg.norm(top_b-top_a))
    out=[]
    for i in range(steps):
        f0,f1=i/steps,(i+1)/steps
        # Enclose the trapezoid so adjacent faces cannot open a row of dark
        # pinholes along their shared edge. Only the silhouette is stepped.
        mid=(f0+f1)/2
        def span(f): return width+(top_width-width)*f
        left=min(shear*f-span(f)/2 for f in (f0,f1))
        right=max(shear*f+span(f)/2 for f in (f0,f1))
        u0=(panel%2)*8;v0=(panel//2)*8
        tex0=start_fraction+(end_fraction-start_fraction)*f0
        tex1=start_fraction+(end_fraction-start_fraction)*f1
        uv=[u0+4-3.9*span(mid)/width,v0+7.9*(1-tex1),
            u0+4+3.9*span(mid)/width,v0+7.9*(1-tex0)]
        out.append({'from':[base[0]+left,base[1]+h*f0,base[2]],
                    'to':[base[0]+right,base[1]+h*f1,base[2]],'shade':False,
                    'rotation':rotation,
                    'faces':{'south':{'texture':'#0','uv':uv},
                             'north':{'texture':'#0','uv':[uv[2],uv[1],uv[0],uv[3]]}}})
    return out


def triangle(a, b, c, panel, steps=48):
    return face_band(a,b,c,c,panel,steps)


def spear(angle, root, tip, height, width, depth, low=False):
    a = math.radians(angle)
    def point(tangent, radial):
        return (8+math.cos(a)*tangent+math.sin(a)*radial,
                8-math.sin(a)*tangent+math.cos(a)*radial)
    out=[]
    base=[point(-width,root),point(0,root-depth),point(width,root),point(0,root+depth)]
    base=[(x,8,z) for x,z in base]
    tx,tz=point(0,tip)
    shoulder=None
    if not low and height>5.8:
        # The three dominant blades have a wide, offset broken shoulder.
        # Their lower body and long chisel crown are different planes, not
        # another perfect pyramid scaled to be slightly larger.
        r=root+(tip-root)*.32
        shoulder=[point(-width*.92+width*.25,r),point(width*.25,r-depth*.92),
                  point(width*.92+width*.25,r),point(width*.25,r+depth*.92)]
        shoulder=[(x,8+height*.34,z) for x,z in shoulder]
    for side,(p,q) in enumerate(zip(base,base[1:]+base[:1])):
        panel=3 if low else (2,1,0,1)[side]
        if shoulder:
            top_a,top_b=shoulder[side],shoulder[(side+1)%4]
            out.extend(face_band(p,q,top_a,top_b,panel,8,0,.34))
            out.extend(face_band(top_a,top_b,(tx,8+height,tz),(tx,8+height,tz),panel,40,.34,1))
        else:
            out.extend(triangle(p,q,(tx,8+height,tz),panel,16 if low else 48))
    out.extend(triangle(base[0],base[1],base[2],3,8))
    out.extend(triangle(base[0],base[2],base[3],3,8))
    return out


def mesh(clip):
    if clip=='rime_footing':
        # Broken low roots connect the crown without filling the caster's
        # feet with a circular platform. No rune, snowflake or floor decal.
        return [e for i in range(3) for j in range(3) for e in spear(i*120-25+j*25,
                    3.8+j*.25,7.5+j*.2,.75+j*.15,1.7,1.7,low=True)]
    return [e for spec in GROUPS[clip] for e in spear(*spec)]


def build():
    assets=PACK/'assets/projects'
    texture=assets/'textures/combat_vfx/mage_material/rime_faces_v01.png'
    texture.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(SOURCE,texture)
    paths=[texture]
    for clip in CLIPS:
        key=f'combat_vfx/mage_material/{clip}_0'
        for path,value in ((assets/f'models/{key}.json',
                {'ambientocclusion':False,'textures':{'0':TEXTURE},'elements':mesh(clip)}),
                (assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key}})):
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
            paths.append(path)
    index=PACK/'index.txt'
    entries=set(index.read_text(encoding='utf-8').splitlines())
    entries.update(p.relative_to(PACK).as_posix() for p in paths)
    index.write_text('\n'.join(sorted(entries))+'\n',encoding='utf-8')
    print(f'Rime: {len(CLIPS)} immutable rooted models; unchanged original painting')


if __name__=='__main__':
    build()
