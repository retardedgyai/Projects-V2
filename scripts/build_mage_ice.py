"""Painted ice reliefs with closed depth and three different spatial roles.

Reads the original image unchanged. Native mesh coverage, broad material planes,
root growth, seam fractures and falling pieces are computed from its pixel art.
"""
from functools import lru_cache
from pathlib import Path
import math
import numpy as np
from PIL import Image
from build_mage_fire import box
from build_approved_dash_v3 import SIZE, polygon

SOURCE=Path(__file__).resolve().parents[1]/'assets/combat-vfx/mage-v2/sources/ice-forms-v01.png'
ICE_CLIPS={'frost_wave','frost_trace','crystal','ice_shelf','ice_root','zero_crown',
           'zero_shelf','zero_wing','zero_floor','ice_hit','ice_charge','ice_pulse'}


@lru_cache(maxsize=1)
def source():
    art=np.asarray(Image.open(SOURCE).convert('RGB')).astype(int)
    mask=(art[:,:,2]-art[:,:,0]>28)&(art[:,:,2]>85)
    prototypes=np.array(((29,54,115),(37,76,173),(28,104,229),(27,159,245),(59,202,239),(140,231,248)))
    groups=np.argmin(np.sum((art[:,:,None,:]-prototypes)**2,axis=3),axis=2)
    colours=[]
    for i in range(6):
        rgb=np.median(art[mask&(groups==i)],axis=0).astype(int)
        colours.append(int(rgb[0])<<16|int(rgb[1])<<8|int(rgb[2]))
    forms=[]
    for left,right in ((0,.405),(.42,.705),(.73,1.)):
        a,b=int(left*art.shape[1]),int(right*art.shape[1])
        yy,xx=np.nonzero(mask[:,a:b]);xx+=a
        lo,hi,top,bottom=xx.min(),xx.max()+1,yy.min(),yy.max()+1
        px=(lo+(np.arange(40)+.5)*(hi-lo)/40).astype(int)
        py=(top+(np.arange(40)+.5)*(bottom-top)/40).astype(int)
        forms.append(np.where(mask[py[:,None],px[None,:]],groups[py[:,None],px[None,:]]+1,0))
    return forms,colours


def palette():return source()[1]


def prismatic_shell(clip,frame,uv):
    """Closed oblique ice prisms with genuine inclined coloured faces.

    Triangular faces are authored as coplanar pixel strips at legal native
    22.5/45 degree rotations. They meet at the same 3-D apex; this is not a
    front/back illustration extruded into a decorative sign.
    """
    t=frame/47
    groups={'zero_crown':((7.0,8.0,10.0),(12.6,9.0,3.5)),
            'zero_wing':((8.3,8.5,8.5),(3.2,6.5,4.0)),
            'zero_shelf':((6.0,8.0,6.3),(11.5,10.,4.5),(3.1,11.,2.8))}[clip]
    out=[]
    for group,(cx,cz,height) in enumerate(groups):
        growth=min(1.,.28+max(0,frame-group)/7)
        h=height*growth;w=h/2;d=h*math.tan(math.pi/8)
        erosion=max(0.,(t-.69)/.31)
        for row in range(24):
            a=row/24;b=(row+1)/24;u=(a+b)/2
            band=row//6
            if erosion>0 and row%6<math.ceil(erosion*2):continue
            if erosion>.5 and band<(erosion-.5)*6:continue
            drift=erosion*(.35+band*.55)
            dx=drift*(.45 if band%2 else -.3);dy=-drift*.75
            # Adjacent sloping facets overlap by a pixel step at their seam.
            # Midpoint-width strips left pinholes along the triangular corners.
            left=cx-w+dx;right=cx+w-2*w*a+dx
            # Faces lean toward the SAME apex (cx-w, 8+h, cz).
            for face,axis,angle,ink in (('north','x',22.5,4),('south','x',-22.5,2)):
                zz=cz-d if face=='north' else cz+d
                slant=h/math.cos(math.pi/8)
                rotation={'origin':[cx+dx,8+dy,zz],'axis':axis,'angle':angle,'rescale':False}
                # Paint the broad 3-D front with the SAME art as the garden,
                # rather than a uniform cyan debug triangle. This samples
                # original material runs; it does not repaint a bitmap.
                source_row=source()[0][1 if group==0 else 0][min(39,int((1-u)*40))]
                painted=source_row[source_row>0]-1
                samples=[int(painted[min(len(painted)-1,int((i+.5)/16*len(painted)))])
                         if len(painted) else ink for i in range(16)] if face=='north' else [ink]*16
                col=0
                while col<16:
                    end=col+1
                    while end<16 and samples[end]==samples[col]:end+=1
                    x0=left+(right-left)*col/16;x1=left+(right-left)*end/16
                    e=box((x0,8+slant*a+dy,zz),(x1,8+slant*b+dy,zz+.012),samples[col],uv)
                    e['faces']={face:e['faces'][face]};e['rotation']=rotation
                    out.append(e);col=end
                if face=='north' and right-left>.17:
                    ridge=box((left,8+slant*a+dy,zz-.012),(min(right,left+.16),8+slant*b+dy,zz),5,uv)
                    ridge['faces']={'north':ridge['faces']['north']};ridge['rotation']=rotation
                    out.append(ridge)
            # Left is vertical; the right face leans 45 degrees in X/Y.
            for side,angle,ink in (('west',0,1),('east',45,3)):
                xx=cx-w+dx if side=='west' else cx+w+dx
                slant=h if side=='west' else h/math.cos(math.pi/4)
                e=box((xx,8+slant*a+dy,cz-d*(1-a)),(xx+.012,8+slant*b+dy,cz+d*(1-a)),ink,uv)
                e['faces']={side:e['faces'][side]}
                if angle:e['rotation']={'origin':[xx,8+dy,cz],'axis':'z','angle':angle,'rescale':False}
                out.append(e)
        cap=box((cx-w,7.988,cz-d),(cx+w,8,cz+d),0,uv)
        cap['faces']={'down':cap['faces']['down']};out.append(cap)
    return out


def floor_mesh(clip,frame,frames,uv):
    # Ground information is drawn as fractures, not a giant upright ice drawing
    # flattened over the floor. Three branching faults have unequal directions.
    t=frame/(frames-1)
    g=np.zeros((SIZE,SIZE),dtype=np.uint8)
    paths=[[(8,8),(6,7.4),(4.5,4.6),(2.0,3.5)],
           [(8,8),(10.8,6.3),(12,3.4),(14.3,2.8)],
           [(7.6,8.3),(9,10.6),(7.3,12.5),(5.4,14.0)],
           [(4.5,4.6),(5.7,2.6)],[(10.8,6.3),(13.8,7.3)],[(9,10.6),(12.3,12.8)]]
    for i,path in enumerate(paths):
        for j,(a,b) in enumerate(zip(path,path[1:])):
            a,b=np.array(a),np.array(b);d=b-a;n=np.array((-d[1],d[0]));n/=np.linalg.norm(n)
            if clip=='ice_pulse' and abs((i%3+j)/4-t)>.25:continue
            w=(.09 if clip=='ice_pulse' else .065)*max(0.,1-max(0.,(t-.67)/.33))
            polygon(g,[a-n*w,b-n*w,b+n*w,a+n*w],5 if clip=='ice_pulse' or frame<4 else 3)
    out=[]
    for row in range(SIZE):
        col=0
        while col<SIZE:
            ink=int(g[row,col]);end=col+1
            while end<SIZE and int(g[row,end])==ink:end+=1
            if ink:out.append(box((col/4,8,row/4),(end/4,8.06,(row+1)/4),ink,uv))
            col=end
    return out


def mesh(clip,frame,uv):
    frames=18 if clip=='ice_hit' else 24 if clip in ('ice_charge','ice_pulse') else 30 if clip in ('frost_wave','frost_trace') else 48
    if frame>=frames-1:return []
    if clip in ('zero_crown','zero_wing','zero_shelf'):
        return prismatic_shell(clip,frame,uv)
    floor=clip in ('frost_trace','ice_root','zero_floor','ice_pulse')
    if floor:return floor_mesh(clip,frame,frames,uv)
    index=2 if clip=='zero_crown' else 1 if clip in ('crystal','zero_wing','ice_hit','ice_charge') else 0
    grid=source()[0][index]
    t=frame/(frames-1)
    out=[]
    # Different rigid fragments fracture at different heights and times. The
    # original coloured faces persist; no five-times reopening short clip.
    for row in range(40):
        height=1-(row+.5)/40
        growth=min(1.,.12+frame/(7 if clip!='ice_charge' else 23))
        if not floor and height>growth:continue
        erosion=max(0.,(t-.23)/.77) if clip=='frost_wave' else max(0.,(t-.70)/.30)
        if height<erosion*.92:continue
        cells=[]
        for col in range(40):
            ink=int(grid[row,col])-1
            u=(col+.5)/40
            if ink<0:cells.append(None);continue
            seam=int(row*.7+col*.31)%17
            if t>.68 and seam in (0,1):cells.append(None);continue
            if t>.86 and seam in (2,3,4,5):cells.append(None);continue
            # Source light/cobalt planes get different real depths. Adjacent
            # same-ink horizontal runs merge; this is not a pile of free cubes.
            # Colour is PAINT, not a height map. Ink-dependent extrusion made
            # every painted transition a noisy staircase and destroyed the art.
            # Keep continuous broad planes with a real closed prismatic depth.
            depth=2.6
            cells.append((ink,depth))
        col=0
        while col<40:
            cell=cells[col];end=col+1
            while end<40 and cells[end]==cell:end+=1
            if cell is not None:
                ink,depth=cell
                x0=.6+col/40*14.8;x1=.6+end/40*14.8
                # Body remains anchored while tips detach along three cracks.
                band=int(height*3)
                drift=erosion*(.35+band*.36)
                x0+=drift*(1 if col>20 else -1);x1+=drift*(1 if col>20 else -1)
                y0=8+(39-row)/40*14.5;y1=y0+14.5/40
                z=8.0
                if clip in ('zero_crown','zero_wing','zero_shelf'):
                    # Local frozen panels rise once, do not orbit the player.
                    y0+=min(frame/8,1)*.7;y1+=min(frame/8,1)*.7
                y0-=drift*1.7;y1-=drift*1.7
                if clip=='ice_hit':
                    y0=8+(y0-8)*.65+t*2;y1=8+(y1-8)*.65+t*2
                    x0=8+(x0-8)*(1+t*.5);x1=8+(x1-8)*(1+t*.5)
                if floor:
                    # The low wave's painted silhouette becomes a broken thin
                    # ice bed; height varies by its broad facet, never a card.
                    z0=.6+(39-row)/40*14.8;z1=z0+14.8/40
                    h=.13+(ink/5)*(.65 if clip=='ice_root' else .25)
                    if clip=='ice_pulse':
                        if abs(height-t)>.13:col=end;continue
                        h=.18;ink=5
                    out.append(box((x0,8,z0),(x1,8+h,z1),ink,uv,max(0,ink-2)))
                else:
                    e=box((x0,y0,z-depth/2),(x1,y1,z+depth/2),ink,uv,max(0,ink-2))
                    # A different, darker reverse face makes real depth legible
                    # from side views without post-process light or fake bloom.
                    e['faces']['south']['tintindex']=max(0,ink-1)
                    # Only the outer skin is visible. Rendering every internal
                    # slab cap caused bright horizontal stripes in projection
                    # and wastes faces in the real client as well.
                    e['faces']={face:e['faces'][face] for face in ('north','south')}
                    out.append(e)
                    for c in range(col,end):
                        faces=[]
                        if c==0 or cells[c-1] is None:faces.append('west')
                        if c==39 or cells[c+1] is None:faces.append('east')
                        if row==0 or not grid[row-1,c] or 1-(row-.5)/40>growth:faces.append('up')
                        if row==39 or not grid[row+1,c] or 1-(row+1.5)/40<erosion*.92:faces.append('down')
                        if faces:
                            edge_x=x0+(c-col)/40*14.8
                            edge=box((edge_x,y0,z-depth/2),(edge_x+14.8/40,y1,z+depth/2),
                                     max(0,ink-1),uv)
                            edge['faces']={f:edge['faces'][f] for f in faces}
                            out.append(edge)
            col=end
    return out
