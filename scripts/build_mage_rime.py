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
SOURCE = ROOT/'assets/combat-vfx/mage-v5/sources/rime-calm-faces-v01.png'
TEXTURE = 'projects:combat_vfx/mage_material/rime_calm_faces_v01'
CLIPS = ('rime_footing', 'rime_inner_a', 'rime_inner_b', 'rime_inner_c',
         'rime_outer_a', 'rime_outer_b', 'rime_outer_c')

# Root and endpoint relative to each planted anchor; width, thickness, roll,
# chipped outline, main texture panel. Deliberately composed branch junctions:
# not the same long axis rescaled into three parallel boards.
BUNDLES = {
    'rime_inner_a': [((0,0,0),(-.8,3.3,3.4),2.8,.72,-18,0,1),
                     ((-.35,1.0,.7),(-2.0,2.7,2.8),1.75,.38,24,2,0),
                     ((.2,.05,.1),(1.4,1.6,3.2),2.5,.6,-32,1,3)],
    'rime_inner_b': [((0,0,0),(.9,3.6,3.3),2.7,.66,17,1,1),
                     ((.35,1.35,1.0),(2.0,3.0,2.7),1.7,.38,-29,0,0),
                     ((-.3,.05,.1),(-1.5,1.5,3.0),2.6,.55,34,2,3)],
    'rime_inner_c': [((0,0,0),(-.3,3.0,3.7),3.0,.72,-7,2,1),
                     ((-.5,.8,.8),(-1.9,2.4,2.8),1.8,.40,31,1,0),
                     ((.2,0,0),(1.7,1.8,3.0),2.5,.65,-22,0,3)],
    'rime_outer_a': [((0,0,0),(-.6,5.4,5.3),3.8,.90,-21,0,0),
                     ((-.45,1.35,1.1),(-2.5,3.8,3.7),2.5,.55,26,2,1),
                     ((.55,2.0,2.0),(1.9,4.5,4.6),1.7,.4,-36,1,0),
                     ((.25,0,.05),(1.9,2.3,4.4),3.4,.8,19,2,3),
                     ((-.2,0,-2.9),(.15,1.2,1.7),4.6,.65,-8,1,3)],
    'rime_outer_b': [((0,0,0),(.8,5.7,5.0),4.0,.95,14,1,0),
                     ((.55,1.1,.8),(2.7,3.6,3.5),2.4,.50,-32,0,1),
                     ((-.4,2.4,2.2),(-1.8,4.7,4.5),1.6,.4,28,2,0),
                     ((-.15,.02,0),(-1.8,2.1,4.5),3.5,.8,-17,1,3),
                     ((.15,0,-2.7),(-.25,1.4,1.8),4.5,.65,10,2,3)],
    'rime_outer_c': [((0,0,0),(-1.0,5.0,5.8),3.8,.9,-10,2,0),
                     ((-.55,1.8,1.5),(-2.8,4.0,4.0),2.2,.48,30,1,1),
                     ((.3,1.1,.9),(1.8,3.5,4.8),2.4,.55,-34,0,0),
                     ((.15,0,.15),(1.8,2.0,4.2),3.5,.85,15,0,3),
                     ((-.15,0,-3.0),(.3,1.1,1.5),4.7,.6,-12,0,3)],
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


def broken_plate(origin,end,width,depth,roll,variant,panel=0):
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
            u0=(panel%2)*8;v0=(panel//2)*8
            uv=[u0+4+left*7.7,v0+7.9*(1-f1),u0+4+right*7.7,v0+7.9*(1-f0)]
            back=list(uv)
            back[0],back[2]=back[2],back[0]
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
                face['uv']=[11.5,7.9*(1-q[1]),12.5,7.9*(1-p[1])]
        out.extend(sides)
    return out


def mesh(clip):
    if clip=='rime_footing':
        # Low solid plates join the roots; no long luminous feather underneath.
        elements=[]
        for i in range(6):
            a=i*math.pi/3
            root=(8+math.sin(a)*1.8,8,8+math.cos(a)*1.8)
            end=(8+math.sin(a)*(3.8+(i%2)*.5),9.1+(i%3)*.24,8+math.cos(a)*(3.8+(i%2)*.5))
            elements.extend(broken_plate(root,end,3.2,.55,i*31,i,3))
        return elements
    elements=[]
    for root,end,width,depth,roll,variant,panel in BUNDLES[clip]:
        elements.extend(broken_plate(np.array(root)+8,np.array(end)+8,width,depth,roll,variant,panel))
    return elements


def build():
    assets=PACK/'assets/projects'
    texture=assets/'textures/combat_vfx/mage_material/rime_calm_faces_v01.png'
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
