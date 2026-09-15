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
SOURCE = ROOT/'assets/combat-vfx/mage-v5/sources/rime-faces-v02.png'
TEXTURE = 'projects:combat_vfx/mage_material/rime_faces_v02'
CLIPS = ('rime_footing', 'rime_inner_a', 'rime_inner_b', 'rime_inner_c',
         'rime_outer_a', 'rime_outer_b', 'rime_outer_c')

# angle, root radius, tip radius, height, half width, half depth (native units).
# Unequal lengths, inclinations and gaps: not six copies of one crystal fan.
GROUPS = {
    'rime_inner_a': [(-17,3.2,8.1,3.1,1.6,.65),(-3,3.1,9.8,5.4,1.7,.7),(14,3.4,8.8,3.7,1.4,.55)],
    'rime_inner_b': [(-19,3.3,8.5,4.3,1.5,.65),(-7,3.2,9.6,3.8,1.8,.7),(12,3.4,9.1,5.2,1.4,.5)],
    'rime_inner_c': [(-14,3.1,9.3,5.1,1.7,.6),(3,3.3,8.7,3.4,1.6,.55),(19,3.2,9.8,4.0,1.5,.65)],
    # The axes agree within each bundle. Stepped lengths and overlapping broad
    # blades make the blue shoulder itself; no separate rock/pedestal is added.
    'rime_outer_a': [(-18,6.4,13.2,4.7,1.3,.5),(-9,5.7,15.3,7.4,2.5,.85),
                     (1,6.5,14.7,6.1,2.0,.65),(13,6.7,13.0,5.0,1.6,.55),
                     (-22,6.8,12.0,3.5,.65,.3),(-2,6.4,15.7,7.1,.48,.24),(16,6.9,14.2,5.8,.55,.25)],
    'rime_outer_b': [(-16,6.8,13.0,5.4,1.7,.55),(-6,5.8,14.8,8.0,2.6,.85),
                     (4,6.6,14.3,6.6,2.1,.7),(15,7.0,12.9,4.7,1.5,.5),
                     (-21,6.9,14.5,6.8,.5,.25),(-1,6.5,15.6,7.8,.55,.24),(20,7.1,11.8,3.4,.65,.3)],
    'rime_outer_c': [(-19,6.5,12.2,4.0,1.5,.5),(-10,6.3,14.4,6.5,1.8,.6),
                     (2,5.9,15.5,7.6,2.5,.85),(16,6.8,13.4,5.2,1.7,.6),
                     (-22,6.9,13.8,5.8,.5,.25),(8,6.4,15.8,8.2,.45,.24),(21,7.0,12.0,3.5,.6,.3)],
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


def spear(angle, root, tip, height, width, depth, low=False, root_at=None, blade_roll=0.0):
    a = math.radians(angle)
    def point(tangent, radial):
        return (8+math.cos(a)*tangent+math.sin(a)*radial,
                8-math.sin(a)*tangent+math.cos(a)*radial)
    out=[]
    rx,rz=point(0,root)
    origin=np.array(root_at if root_at is not None else (rx,8,rz),dtype=float)
    shift=origin-np.array((rx,8,rz))
    base=[point(-width,root),point(0,root-depth),point(width,root),point(0,root+depth)]
    base=[tuple(np.array((x,8,z))+shift) for x,z in base]
    tx,tz=point(0,tip)
    shoulder=None
    if not low:
        # A long, narrowing painted body meets a shorter chisel tip. This is
        # not a complete triangular pyramid repeated at different scales.
        center=origin*.44+np.array((tx,8+height,tz))*.56
        tangent=np.array((math.cos(a),0,-math.sin(a)))
        radial=np.array((math.sin(a),0,math.cos(a)))
        center+=tangent*width*.12
        shoulder=[tuple(center+v) for v in (-tangent*width*.66,-radial*depth*.66,
                                          tangent*width*.66,radial*depth*.66)]
    if blade_roll:
        # The wide painted blades do not all stand in the same radial plane.
        # Roll their cross-sections about their own long axes, retaining each
        # tip and planted root. This supplies volume through overlapping faces
        # instead of making a thicker rock under a set of flat feathers.
        axis=np.array((tx,8+height,tz))-origin
        axis/=np.linalg.norm(axis)
        radians=math.radians(blade_roll)
        def rolled(p):
            v=np.array(p)-origin
            return tuple(origin+v*math.cos(radians)+np.cross(axis,v)*math.sin(radians)
                         +axis*np.dot(axis,v)*(1-math.cos(radians)))
        base=[rolled(p) for p in base]
        if shoulder: shoulder=[rolled(p) for p in shoulder]
    for side,(p,q) in enumerate(zip(base,base[1:]+base[:1])):
        # Two opposing broad faces carry the pale axial painting. Keeping
        # the entire owner-facing side dark made the cast cobalt spikes,
        # unlike the reference's luminous blue-white ice body.
        panel=3 if low else (0,1,0,2)[side]
        if shoulder:
            top_a,top_b=shoulder[side],shoulder[(side+1)%4]
            out.extend(face_band(p,q,top_a,top_b,panel,12,0,.56))
            out.extend(face_band(top_a,top_b,(tx,8+height,tz),(tx,8+height,tz),panel,16,.56,1))
        else:
            out.extend(triangle(p,q,(tx,8+height,tz),panel,16 if low else 48))
    out.extend(triangle(base[0],base[1],base[2],3,8))
    out.extend(triangle(base[0],base[2],base[3],3,8))
    return out


def cluster_roots(clip):
    """R04: many tips emerge from a shared blue body, not isolated radial pins."""
    specs=GROUPS[clip]
    if 'inner' in clip:
        return [np.array((8+(i-1)*.65,8,11.4+(i%2)*.3)) for i in range(3)]
    primary=max(range(4),key=lambda i:specs[i][4])
    roots=[np.array((8+(-1.25,-.45,.45,1.3,-1.65,.1,1.7)[i],8,
                    13.1+(i%2)*.25)) for i in range(len(specs))]
    # One side branch is grafted to the lower shoulder of the primary shaft.
    # It starts above ground, inside that body, instead of becoming another
    # complete small crystal standing separately on the floor.
    angle,_,tip,height,width,_=specs[primary]
    a=math.radians(angle)
    end=np.array((8+math.sin(a)*tip,8+height,8+math.cos(a)*tip))
    graft=5
    roots[graft]=roots[primary]*.78+end*.22
    roots[graft]+=np.array((math.cos(a),0,-math.sin(a)))*width*.12
    return roots


def mesh(clip):
    if clip=='rime_footing':
        # Broken low roots connect the crown without filling the caster's
        # feet with a circular platform. No rune, snowflake or floor decal.
        return [e for i in range(3) for j in range(3) for e in spear(i*120-25+j*25,
                    3.8+j*.25,7.5+j*.2,.75+j*.15,2.0,1.7,low=True,
                    root_at=(8+math.sin(i*2*math.pi/3)*4.0,8,8+math.cos(i*2*math.pi/3)*4.0))]
    rolls=(-32,19,-24,37,-16,28,-38)
    variant='abc'.index(clip[-1])
    elements=[e for i,(spec,root) in enumerate(zip(GROUPS[clip],cluster_roots(clip)))
              for e in spear(*spec,root_at=root,blade_roll=rolls[(i+variant)%len(rolls)])]
    # Ground sampling must occur at this cluster, not at the caster. Rebase
    # the native model; CoreMageFrostChoreography adds the exact same offset
    # in world space before the existing ground resolver runs.
    anchor=3.4 if 'inner' in clip else 5.225
    for e in elements:
        for key in ('from','to'):
            e[key][2]-=anchor
        # Each face's strips deliberately share a rotation dictionary. Do not
        # subtract the anchor repeatedly through that shared object.
        e['rotation']={**e['rotation'],'origin':[e['rotation']['origin'][0],
                         e['rotation']['origin'][1],e['rotation']['origin'][2]-anchor]}
    return elements


def build():
    assets=PACK/'assets/projects'
    texture=assets/'textures/combat_vfx/mage_material/rime_faces_v02.png'
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
