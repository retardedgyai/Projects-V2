"""Skill-specific native drawings, using the frozen dash's four ink values.

These are authored silhouettes, NOT a new raster renderer or recolours of one arc.
Each moving piece has a birth, a leading edge, a detached tail and a finite life.
"""
import math
import numpy as np
from build_approved_dash_v3 import SIZE, polygon

CLIPS = ('step_dust', 'stone_break', 'stone_spall', 'impact_dust', 'sweep_pressure', 'sweep_dust',
         'cut_thread', 'reversal', 'pierce_shell', 'guard_edge', 'parry_metal',
         'voice_compression', 'standard_foot', 'ultimate_rise', 'ultimate_cross',
         'ultimate_rift', 'ultimate_fall', 'weight_load', 'point_load', 'ultimate_load')
STONE = {'step_dust', 'stone_break', 'stone_spall', 'impact_dust', 'sweep_dust', 'standard_foot'}
FLAT = (STONE-{'stone_spall'}) | {'ultimate_rift'}


def facet(g, center, width, height, accent=False, lean=0.):
    """Broken stone/pressure petal: big dark plane, smaller lit plane, one edge."""
    x,y=center
    p=[(x-width*.85,y-height*.25),(x-width*.4+lean,y+height*.5),
       (x+width*.45+lean,y+height*.34),(x+width,y-height*.18),(x,y-height*.5)]
    if not accent:
        polygon(g,p,1)
        polygon(g,[p[0],p[1],p[2],(x+width*.12,y-height*.05)],2)
    else:
        polygon(g,[p[1],p[2],(p[2][0],p[2][1]-.14),(p[1][0],p[1][1]-.2)],3)


def ribbon(g, points, widths, accent=False):
    # Not the shared fan: the caller defines the entire trajectory AND thickness.
    for a,b,wa,wb in zip(points,points[1:],widths,widths[1:]):
        a,b=np.asarray(a),np.asarray(b)
        d=b-a
        n=np.array((-d[1],d[0]))/max(.001,np.linalg.norm(d))
        if accent:
            polygon(g,[a,b,b-n*min(.15,wb),a-n*min(.15,wa)],3)
        else:
            polygon(g,[a,b,b-n*wb,a-n*wa],1)
            polygon(g,[a,b,b-n*wb*.65,a-n*wa*.65],2)
            polygon(g,[a,b,b-n*min(.28,wb*.22),a-n*min(.28,wa*.22)],3)


def hero(g, clip, t, accent):
    """Large semantic silhouettes: broad onset, departing planes, negative space.
    The moving front and the decaying tail are drawn separately, never a rotating stamp.
    """
    fade=max(0.,1-max(0.,t-4)/14)
    if clip=='stone_spall':
        # Broken impact crown rising from the struck ground.
        for i,(x,h,w) in enumerate(((3.,6.,1.1),(5.1,9.,1.6),(8.,12.,2.),(10.7,8.,1.3),(13.,5.,.85))):
            if accent and i!=2: continue
            age=t-i*.35
            if age<0: continue
            bottom=1.5+max(0.,age-5)*.4
            top=1.5+h*min(1.,.35+age*.24)
            if bottom>=top: continue
            ribbon(g,[(x-.4,bottom),(x+(x-8)*.09,top),(x+.5,top-.8)],
                   [w*fade,w*.24*fade,0.],accent)
    elif clip=='pierce_shell':
        # Open spearhead and longitudinal ribs, not a crescent.
        head=6+7.8*min(1.,t/4);tail=max(2.5,head-9*fade)
        for side in (-1,1):
            if accent and side<0: continue
            ribbon(g,[(8,head),(8+side*3.4*fade,head-3.4*fade),(8+side*1.,tail)],
                   [1.35*fade,1.8*fade,.1],accent)
    elif clip=='sweep_pressure':
        # One broad open fan, with unequal serrations; not parallel slash copies.
        start=max(0.,(t-4)/14);end=min(1.,.25+t/5)
        pts=[];widths=[]
        for u in np.linspace(start,end,30):
            a=-1.45+2.9*u
            pts.append((8+math.sin(a)*6.2,3.2+math.cos(a)*6.3))
            widths.append((3.8 if int(u*7)%3 else 2.3)*fade*math.sin(math.pi*(.07+.86*u)))
        if not accent: ribbon(g,pts,widths)
    elif clip=='reversal':
        # Backward hook with a broad folded heel.
        start=max(0.,(t-4)/14);end=min(1.,.2+t/5)
        pts=[];widths=[]
        for u in np.linspace(start,end,30):
            a=-.7+3.6*u
            pts.append((10+math.cos(a)*3.4-u*5.1,7.2+math.sin(a)*4.5))
            widths.append((.7+2.5*math.sin(u*math.pi))*fade)
        ribbon(g,pts,widths,accent)
    elif clip=='voice_compression':
        # Broken radial pressure plates, not blade trails or a filled spell disc.
        for i,a in enumerate((-.9,.35,1.55,2.7,4.1)):
            if accent and i!=3: continue
            age=t-i*.35
            if age<0: continue
            r=1+4.8*min(1.,age/5);pts=[];widths=[]
            for u in np.linspace(0,1,14):
                theta=a+(u-.5)*.72
                pts.append((8+math.cos(theta)*r,8+math.sin(theta)*r))
                widths.append(2.1*fade*math.sin(math.pi*u))
            ribbon(g,pts,widths,accent)
    elif clip in ('ultimate_rise','ultimate_cross','ultimate_fall'):
        # Rising forks -> horizontal torn sheet -> descending cleft.
        for i in range(3):
            if accent and i!=1: continue
            age=t-i*.65
            if age<0: continue
            f=max(0.,1-max(0.,age-3)/14)
            if clip=='ultimate_rise':
                x=4+i*3.8;head=6+8*min(1.,age/4)
                pts=[(x-1.,2+max(0.,age-5)*.6),(x+.9,head-3),(x+.25,head)]
                widths=[1.8*f,2.2*f,.1]
            elif clip=='ultimate_cross':
                x=13-min(10.,age*1.8);y=4+i*3.3
                pts=[(max(1.,x),y),(min(14.,x+4),y+1.3),(min(14.5,x+8),y-.3)]
                widths=[.25,2.4*f,1.3*f]
            else:
                x=4+i*4;bottom=12-10*min(1.,age/4)
                pts=[(x-.8,max(bottom+1,13-max(0.,age-5)*.6)),(x+.9,bottom+2.8),(x,bottom)]
                widths=[1.8*f,2.6*f,.15]
            ribbon(g,pts,widths,accent)


def contour(clip, frame, accent=False):
    g=np.zeros((SIZE,SIZE),dtype=np.uint8)
    if frame>=19: return g
    t=float(frame)
    hero(g,clip,t,accent)
    if clip=='stone_spall':
        for i in range(3):
            if accent and i!=2: continue
            age=t-i*.55
            if not 0<=age<18: continue
            u=age/18
            x=8+(i-1)*(.7+u*1.2)
            y=3+math.sin(u*math.pi)*7+i%2*.4
            facet(g,(x,y),(.65+i%2*.25)*(1-u), (1.7+i%3*.45)*(1-u),accent,lean=(i-2)*.35)
        return g
    if clip in ('step_dust','impact_dust','sweep_dust'):
        # Low dust is deliberately NOT white sword energy. Unequal clumps peel
        # from a boot, impact centre or sweeping edge, then split apart.
        count={'step_dust':5,'impact_dust':7,'sweep_dust':6}[clip]
        for i in range(count):
            if accent: continue
            age=t-i*.6
            if not 0<=age<17: continue
            u=age/17
            size=math.sin(math.pi*(.15+.85*u))*(1-u*.45)
            if clip=='step_dust':
                side=(-1,1)[i%2]
                x=8+side*(.7+i*.28+u*2.2); y=11-i*.7-u*6
            elif clip=='impact_dust':
                a=-1.4+i*.47
                x=8+math.sin(a)*(1+u*5.3); y=5+math.cos(a)*(1+u*6.4)
            else:
                x=2+i*1.65+u*1.25; y=5.5+math.sin(i*.55)*3.5+u*2.8
            facet(g,(x,y),(1.15+i%3*.15)*size,(1.8+i%2*.5)*size,lean=.35)
            if age>6: facet(g,(x-.6,y-.7),.45*(1-u),.8*(1-u))
        return g
    if clip in ('stone_break','ultimate_rift'):
        # Persistent jagged fissure followed by departing facets. Different
        # branching and scale from the old three bright parallel pressure bars.
        huge=clip=='ultimate_rift'
        for lane,angle in enumerate((-.75,.05,.72) if huge else (-.82,-.32,.13,.61)):
            if accent and lane!=2: continue
            front=min(1.,.18+t/5)
            tail=max(0.,(t-9)/10)
            pts=[]; widths=[]
            for i,u in enumerate(np.linspace(tail,front,22)):
                y=2+u*(12 if huge else 10)
                x=8+math.sin(angle)*u*6+math.sin(u*21+lane)*(.23 if huge else .4)
                pts.append((x,y));widths.append((1.3 if huge else .85)*(1-u*.55)*(1-tail))
            ribbon(g,pts,widths,accent)
        for i in range(4 if huge else 5):
            if accent: continue
            age=t-i*.65
            if not 0<=age<17: continue
            u=age/17; side=(-1,1)[i%2]
            facet(g,(8+side*(.6+i*.3+u*2.6),3+i*1.8+u*.6),
                  (.55+i%2*.3)*(1-u), (1.5+i%3*.4)*(1-u),lean=side*.35)
        return g
    if clip=='sweep_pressure':
        # A broad, open sail swept across the front; not a stack of crescents.
        for l in range(3):
            if accent and l!=2: continue
            age=t-l*1.1
            if not 0<=age<17: continue
            start=max(0.,(age-4)/13); end=min(1.,.2+age/5)
            pts=[]; widths=[]
            for u in np.linspace(start,end,28):
                a=-1.48+2.9*u
                pts.append((8+math.sin(a)*(5.5+l*.25),3+math.cos(a)*(6.4-l*.7)))
                widths.append((2.6,.85,.28)[l]*math.sin(math.pi*(.06+.88*u))*(1-age/18))
            ribbon(g,pts,widths,accent)
        return g
    if clip=='cut_thread':
        # Restrained short diagonal tear; no broad fan behind a checking cut.
        for l in range(2):
            if accent and l==0: continue
            age=t-l*2
            if not 0<=age<15: continue
            start=max(0.,(age-3)/12); end=min(1.,.35+age/4)
            pts=[(3+9*u,11-6*u+math.sin(u*math.pi)*.55+l*.6) for u in np.linspace(start,end,22)]
            ribbon(g,pts,[1.15*(1-age/15)]*22,accent)
        return g
    if clip=='reversal':
        # Hook catches the forward motion then throws it back: a folded elbow,
        # not the ultimate's horizontal shear or the checking cut's small line.
        for l in range(2):
            if accent and l==0: continue
            age=t-l
            if not 0<=age<18: continue
            start=max(0.,(age-4)/14);end=min(1.,.18+age/6)
            pts=[]; widths=[]
            for u in np.linspace(start,end,30):
                a=-.7+3.5*u
                pts.append((10+math.cos(a)*(3.1-l*.5)-u*5.8,7+math.sin(a)*(3.8-l*.5)))
                widths.append((1.9 if not l else .4)*math.sin(math.pi*(.08+.84*u))*(1-age/19))
            ribbon(g,pts,widths,accent)
        return g
    if clip in ('pierce_shell','point_load'):
        load=clip=='point_load'
        for side in (-1,1):
            if accent and side<0: continue
            for lane in range(2):
                if accent and lane: continue
                age=t-lane*1.3
                if not 0<=age<18: continue
                u=age/18
                head=8+(1-math.exp(-age*.55))*6 if not load else 5+(1-u)*7
                length=(7 if not load else 4)*(1-u)
                pts=[]; widths=[]
                for v in np.linspace(0,1,24):
                    pts.append((8+side*((1-v)**1.5*(2.5+lane*.65)+.12),head-length*(1-v)))
                    widths.append((.8 if not lane else .35)*math.sin(math.pi*v)*(1-u))
                ribbon(g,pts,widths,accent)
        return g
    if clip in ('guard_edge','parry_metal'):
        contact=clip=='parry_metal'
        if not contact:
            for i in range(3):
                if accent and i!=1: continue
                u=t/19
                facet(g,(6+i,3+i*3+u*1.5),.65*(1-u),3.3*(1-u),accent,lean=.25)
        else:
            # Two broad skewed collision faces, then unequal flying needles.
            for i,a in enumerate((-.35,1.1,2.7,3.45,4.45,5.7)):
                if accent and i!=3: continue
                age=t*.85
                r=.15+age*.22
                length=(4.8,2.4,3.3,5.2,1.8,2.9)[i]*max(0,1-age/15)
                pts=[(8+math.cos(a)*(r+u*length),8+math.sin(a)*(r+u*length)) for u in np.linspace(0,1,18)]
                ribbon(g,pts,[(2.5 if i in (0,3) else .85)*(1-u)*(1-age/16) for u in np.linspace(0,1,18)],accent)
        return g
    if clip=='voice_compression':
        # Three convex pressure plates detach outwards from the chest; no
        # magic circle, sword slash or target-hit flash.
        for i,a in enumerate((-.85,.25,1.25,2.25,3.5)):
            if accent and i!=3: continue
            age=t-i*.5
            if not 0<=age<18: continue
            u=age/18;r=.4+4.8*(1-math.exp(-age*.22))
            x,y=8+math.cos(a)*r,8+math.sin(a)*r
            facet(g,(x,y),(1.1+i%2*.45)*(1-u), (2.4-i%3*.35)*(1-u),accent,lean=math.cos(a)*.5)
        return g
    if clip=='standard_foot':
        # Only four short driven wedges at the pole, not an attack/explosion.
        for i in range(4):
            if accent and i!=0: continue
            a=i*math.pi/2+.3;u=t/19;r=1+u*2
            facet(g,(8+math.sin(a)*r,8+math.cos(a)*r),1.0*(1-u),2.2*(1-u),accent)
        return g
    if clip in ('ultimate_rise','ultimate_cross','ultimate_fall','weight_load','ultimate_load'):
        for i in range(1 if clip=='ultimate_rise' else 5):
            if accent and i!=2: continue
            age=t-i*.65
            if not 0<=age<18: continue
            u=age/18;fade=1-u
            if clip=='ultimate_rise':
                x=3+i*2.2+(i-2)*u*.5;y=2+i%2+9*(1-math.exp(-age*.2))
                facet(g,(x,y),(.55+i%2*.4)*fade,(3.3-i%3*.5)*fade,accent,lean=(i-2)*.3)
            elif clip=='ultimate_cross':
                # Horizontal shear tears a staggered slab into separate plates.
                x=13-i*2-u*1.7;y=5.5+i%2*2.5+u*(i-2)*.5
                facet(g,(x,y),(1.45-i%2*.35)*fade,(3.0-i%3*.3)*fade,accent,lean=-.6)
            elif clip=='ultimate_fall':
                # Terminal impact: a descending wedge breaks into outward
                # upright shards, NOT the rising or small-slam clip enlarged.
                x=8+(i-2)*(.65+u*.9);y=12-age*.45-i%2*.7
                facet(g,(x,max(2.,y)),(.7+i%2*.3)*fade,(4.5-i%3*.6)*fade,accent,lean=(i-2)*.35)
            else:
                # The heavy windup settles DOWN; ultimate fragments converge UP.
                x=3.5+i*2.1+(8-(3.5+i*2.1))*u*.55
                y=(12-i%2*2-u*7) if clip=='weight_load' else (2+i%2+u*8)
                facet(g,(x,y),(.5+i%2*.15)*fade,(2.2+i%3*.3)*fade,accent,lean=.2)
        return g
    raise ValueError(clip)
