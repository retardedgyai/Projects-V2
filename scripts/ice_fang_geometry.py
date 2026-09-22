"""Broad, closed ice masses using the existing painted facet atlas unchanged.

No branch billboards, radial fan or cube stack. Four painted sides form a solid
broken crystal; the large face remains coplanar across its contour strips.
"""
import numpy as np
from reference_rime_geometry import face_band, triangle


def crystal(root, tip, width, depth, shoulder=.64, tip_width=.20, panels=(0,1,3,1)):
    root,tip=np.asarray(root,dtype=float),np.asarray(tip,dtype=float)
    # Chisel-ended, with a broad shoulder. Avoid the old needle/feather outline.
    def ring(center,w,d):
        return [center+v for v in (np.array([-w,0,0]),np.array([0,0,-d]),
                                  np.array([w,0,0]),np.array([0,0,d]))]
    bottom=ring(root,width,depth)
    broad=ring(root+(tip-root)*shoulder,width*.84,depth*.84)
    top=ring(tip,width*tip_width,depth*.07)
    out=[]
    for side in range(4):
        j=(side+1)%4
        out+=face_band(bottom[side],bottom[j],broad[side],broad[j],panels[side],12,0,shoulder)
        out+=face_band(broad[side],broad[j],top[side],top[j],panels[side],16,shoulder,1)
    for face,panel in ((bottom,2),(top,0)):
        out+=triangle(face[0],face[1],face[2],panel,4)
        out+=triangle(face[0],face[2],face[3],panel,4)
    return out


def meshes(variant):
    # Engine plays C -> B -> A. Low broken shelf -> slanted mass -> tall terminal pillar.
    tip,width,depth = [((8.6,17.2,11.0),2.7,1.9),
                       ((7.5,14.8,12.7),3.45,2.25),
                       ((8.4,11.9,14.0),3.25,2.0)][variant]
    outer=crystal((8,8,8),tip,width,depth,tip_width=.24 if variant==0 else .18)
    # Only two subordinate fracture pieces, with distinct roots and different heights.
    height=tip[1]-8
    inner=crystal((5.8,8,8.0),(4.6,8+height*.48,10.0),1.15,.8,tip_width=.08,panels=(0,3,2,1))
    inner+=crystal((10.1,8,8.9),(11.3,8+height*.32,11.0),.95,.7,tip_width=.3,panels=(1,0,2,3))
    # Separate low plates read as frozen ground, not more upright blades at one origin.
    root=[]
    for start,end,w,d in [((6.6,8,7.7),(5.4,8.75,10.1),1.5,.75),
                           ((8.9,8,8.6),(10.5,8.55,11.5),1.65,.75),
                           ((8,8,10.5),(7.8,8.4,13.9),1.1,.65)]:
        root+=crystal(start,end,w,d,shoulder=.45,tip_width=.4,panels=(3,1,2,3))
    return root,inner,outer
