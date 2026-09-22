"""Painted surface mapping for Mage's authored native materials.

Coordinates are attached to the material, never re-started on each small face.
The PNG source remains unchanged. These are not screen-facing spell cards.
"""
import copy


def painted(elements, axis='y', base_tint=0, shades=None):
    result=copy.deepcopy(elements)
    for e in result:
        lo,hi=e['from'],e['to']
        for name,f in e['faces'].items():
            axes=(0,2) if name in ('up','down') else (0,1) if name in ('north','south') else (2,1)
            q=[max(.01,min(15.99,b[a]*(2/3 if a==1 else 1))) for b in (lo,hi) for a in axes]
            # Keep longitudinal colour runs coherent across neighboring quads.
            if axes[1]==1:q[1],q[3]=16-q[3],16-q[1]
            if name in ('north','east'):q[0],q[2]=q[2],q[0]
            if name=='down':q[1],q[3]=q[3],q[1]
            if axis=='z' and 2 in axes:
                # Source's V is the flame's direction of flow, model Z.
                if axes[0]==2:q=[q[1],q[0],q[3],q[2]]
            # A material may extend beyond a unit model. Tile by a fixed affine
            # crop rather than clamping both endpoints to a zero-area edge.
            if q[0]==q[2]:q[2]=q[0]+(.01 if q[0]<15.98 else -.01)
            if q[1]==q[3]:q[3]=q[1]+(.01 if q[1]<15.98 else -.01)
            f['uv']=[round(v,6) for v in q];f['texture']='#1'
            f['tintindex']=base_tint+(shades[f['tintindex']] if shades else f['tintindex'])
    return result
