"""R04 radial ice / R11 broad icy planes. Native geometry only, no raster edits.

One immutable model per rooted cluster. A coarse painted face spans the entire
shaft; contour strips never repeat a complete painting on every little face.
"""
import json
import math
import shutil
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT/'server-minestom/src/main/resources/core-ui-pack'
SOURCE = ROOT/'assets/combat-vfx/mage-v5/sources/rime-faces-v02.png'
TEXTURE = 'projects:combat_vfx/mage_material/rime_faces_v02'
BRANCH_SOURCE = ROOT/'assets/combat-vfx/mage-v5/sources/rime-branches-v01.png'
BRANCH_TEXTURE = 'projects:combat_vfx/mage_material/rime_branches_v01'
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


def plane_rotation(u,v,origin):
    matrix=np.column_stack((u,v,np.cross(u,v)))
    ry=math.asin(max(-1,min(1,-matrix[2,0])))
    if abs(math.cos(ry))>1e-7:
        rx=math.atan2(matrix[2,1],matrix[2,2])
        rz=math.atan2(matrix[1,0],matrix[0,0])
    else:
        rx=0.0
        rz=math.atan2(-matrix[0,1],matrix[1,1])
    return {'origin':list(origin),'x':math.degrees(rx),'y':math.degrees(ry),'z':math.degrees(rz)}


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
    rotation=plane_rotation(u,v,base)
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


def spear(angle, root, tip, height, width, depth, low=False, root_at=None, blade_roll=0.0, panels=(0,1,0,2)):
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
        panel=3 if low else panels[side]
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


@lru_cache(maxsize=3)
def painted_profile(variant):
    """Read original pixels to author geometry. Never alter or save the bitmap.

    Imagegen returned RGB with a gray checkerboard even after a requested alpha
    edit. Only all-blue 8px cells receive native faces. Empty gaps remain actual
    empty geometry; no background rectangle or fake transparency is shipped.
    """
    rgb=np.asarray(Image.open(BRANCH_SOURCE).convert('RGB')).astype(int)
    ih,iw=rgb.shape[:2]
    cell=8
    lo,hi=variant*iw//3,(variant+1)*iw//3
    blue=rgb[:,:,2]-rgb[:,:,0]>10
    active={};rects=[]
    for y in range(0,ih,cell):
        row=[];start=None
        for x in range(lo,hi,cell):
            valid=bool(blue[y:min(ih,y+cell),x:min(hi,x+cell)].all())
            if valid and start is None:start=x
            if start is not None and (not valid or x+cell>=hi):
                row.append((start,x+cell if valid else x));start=None
        next_active={}
        for span in row:
            box=active.pop(span,[span[0],y,span[1],y])
            box[3]=min(ih,y+cell);next_active[span]=box
        rects.extend(active.values());active=next_active
    rects.extend(active.values())
    if not rects:raise ValueError('No blue painted contour')
    top=min(r[1] for r in rects);bottom=max(r[3] for r in rects)
    left=min(r[0] for r in rects);right=max(r[2] for r in rects)
    roots=[(r[0]+r[2])/2 for r in rects if r[3]>=bottom-cell]
    return iw,ih,rects,(left,top,right,bottom,float(np.mean(roots)))


def painted_branch(variant,origin,end,width,roll):
    iw,ih,rects,(left,top,right,bottom,root_x)=painted_profile(variant)
    origin=np.array(origin,dtype=float);axis=np.array(end)-origin
    length=np.linalg.norm(axis);v=axis/length
    u=np.array((1.0,0,0));u-=v*np.dot(u,v);u/=np.linalg.norm(u)
    a=math.radians(roll);u=u*math.cos(a)+np.cross(v,u)*math.sin(a)
    rotation=plane_rotation(u,v,origin)
    sx=width/(right-left);sy=length/(bottom-top)
    out=[]
    for x0,y0,x1,y1 in rects:
        # The painting is not squeezed independently into each contour strip.
        # Every face reads exactly its original atlas rectangle.
        uv=[x0/iw*16,y0/ih*16,x1/iw*16,y1/ih*16]
        back=[uv[2],uv[1],uv[0],uv[3]]
        edges={'texture':'#0','uv':[10,10,10.08,10.08]}
        out.append({'from':[origin[0]+(x0-root_x)*sx,origin[1]+(bottom-y1)*sy,origin[2]-.055],
                    'to':[origin[0]+(x1-root_x)*sx,origin[1]+(bottom-y0)*sy,origin[2]+.055],
                    'shade':False,'rotation':rotation,
                    'faces':{'south':{'texture':'#1','uv':uv},'north':{'texture':'#1','uv':back},
                             'up':edges,'down':edges,'west':edges,'east':edges}})
    return out


def broken_plate(origin,end,width,depth,roll,variant):
    """A closed, chipped ice slab, not crossed sprite contours or a pyramid.

    Each broad side samples one uninterrupted painting. The asymmetric upper
    shoulders leave two unequal chisel tips, with thickness visible at breaks.
    Native contour rows follow coarse pixel steps without raster repainting.
    """
    origin=np.asarray(origin,dtype=float);axis=np.asarray(end)-origin
    length=float(np.linalg.norm(axis));v=axis/length
    u=np.array((1.,0.,0.));u-=v*np.dot(u,v);u/=np.linalg.norm(u)
    a=math.radians(roll);u=u*math.cos(a)+np.cross(v,u)*math.sin(a)
    n=np.cross(u,v)
    # Clockwise outline: a broad body, broken shoulders, two uneven ends.
    profiles=(
        [(-.42,0),(-.5,.42),(-.34,.42),(-.34,.74),(-.17,.67),(-.09,1),(.10,.88),(.12,.62),(.36,.78),(.31,.40),(.5,.28),(.40,0)],
        [(-.40,0),(-.48,.30),(-.32,.36),(-.30,.80),(-.10,.69),(.04,.95),(.21,1),(.23,.65),(.40,.74),(.36,.34),(.49,.22),(.38,0)],
        [(-.43,0),(-.5,.32),(-.33,.35),(-.32,.68),(-.20,.62),(-.13,.87),(.02,1),(.17,.77),(.18,.56),(.39,.70),(.33,.26),(.42,0)],
    )
    profile=profiles[variant%3]
    out=[]
    def half_depth(f):return depth*.5*max(.025,(1-f)**.7)
    for row in range(40):
        f0,f1=row/40,(row+1)/40;mid=(f0+f1)/2
        crossings=[]
        for p,q in zip(profile,profile[1:]+profile[:1]):
            if min(p[1],q[1])<=mid<max(p[1],q[1]):
                crossings.append(p[0]+(q[0]-p[0])*(mid-p[1])/(q[1]-p[1]))
        crossings.sort()
        for left,right in zip(crossings[::2],crossings[1::2]):
            uv=[4+left*7.7,7.9*(1-f1),4+right*7.7,7.9*(1-f0)]
            back=[uv[2],uv[1],uv[0],uv[3]]
            for sign,paint in ((1,uv),(-1,back)):
                lower=origin+v*f0*length+n*half_depth(f0)*sign
                upper=origin+v*f1*length+n*half_depth(f1)*sign
                surfaces=face_band(lower+u*left*width,lower+u*right*width,
                                    upper+u*left*width,upper+u*right*width,0,steps=1)
                for surface in surfaces:
                    for face in surface['faces'].values():face['uv']=paint
                out.extend(surfaces)
    # Continuous darker side faces close the volume, including the chipped top.
    for p,q in zip(profile,profile[1:]+profile[:1]):
        a=origin+u*p[0]*width+v*p[1]*length
        b=origin+u*q[0]*width+v*q[1]*length
        if np.linalg.norm(b-a)<1e-8:continue
        sides=face_band(a-n*half_depth(p[1]),a+n*half_depth(p[1]),
                        b-n*half_depth(q[1]),b+n*half_depth(q[1]),2,steps=1)
        # Map each edge to its own longitudinal portion of the blue face.
        # Neither an entire decorative tile nor a flat blue fill per edge.
        for side in sides:
            for face in side['faces'].values():
                face['uv']=[12.7,7.9*(1-q[1]),13.2,7.9*(1-p[1])]
        out.extend(sides)
    return out


def mesh(clip):
    if clip=='rime_footing':
        # Low solid plates join the roots; no long luminous feather underneath.
        elements=[]
        for i in range(3):
            a=i*2*math.pi/3
            root=(8+math.sin(a)*1.8,8,8+math.cos(a)*1.8)
            end=(8+math.sin(a)*5.5,10.2,8+math.cos(a)*5.5)
            elements.extend(broken_plate(root,end,4.4,.65,i*37,i))
        return elements
    variant='abc'.index(clip[-1])
    specs=GROUPS[clip];roots=cluster_roots(clip)
    primary=max(range(len(specs)),key=lambda i:specs[i][4])
    spec=specs[primary];root=roots[primary]
    angle,_,tip,height,width,depth=spec;a=math.radians(angle)
    end=np.array((8+math.sin(a)*tip,8+height,8+math.cos(a)*tip))
    # Rooted, overlapping bodies with calm broad faces. Their short split tips
    # are part of the slab outline, not many independent radial light needles.
    direction=end-root
    direction[[0,2]]*=.77
    direction[1]*=1.20
    elements=broken_plate(root,root+direction,width*2.25,depth*1.55,
                          -24+variant*14,variant)
    secondary=root+np.array((width*.48,.06,.12))
    elements.extend(broken_plate(secondary,secondary+direction*.72,
                    width*1.55,depth*1.2,36-variant*13,(variant+1)%3))
    if 'outer' in clip:
        branch=root+direction*.24-np.array((width*.40,0,0))
        branch_end=branch+direction*.48+np.array((-width*.25,.1,0))
        elements.extend(broken_plate(branch,branch_end,width*.95,
                                     depth*.8,-42+variant*9,(variant+2)%3))
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
    branch_texture=assets/'textures/combat_vfx/mage_material/rime_branches_v01.png'
    shutil.copyfile(BRANCH_SOURCE,branch_texture)
    paths=[texture,branch_texture]
    for clip in CLIPS:
        key=f'combat_vfx/mage_material/{clip}_0'
        for path,value in ((assets/f'models/{key}.json',
                {'ambientocclusion':False,'textures':{'0':TEXTURE,'1':BRANCH_TEXTURE},'elements':mesh(clip)}),
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
