"""Authored middle-scale force surfaces: approved pixel ink, never vanilla smoke.

Reference structure: Dragon Warrior's broken leading faces, separated large fragments,
then thin tails. No copied purple/lightning theme, spinning completed image or bitmap blur.
The source is native quantized geometry using the accepted dash compiler.
"""
import json
import math
import numpy as np
from build_approved_dash_v3 import PACK, SIZE, polygon, geometry, ink_uvs

CLIPS = ('gather','jet','eruption','lift','fan','spin_a','spin_b','spin_c','burst','rally')
FRAMES = 20


def strip(g, points, width, ink=3):
    """Broad broken body, narrow lit rim; never a filled alpha rectangle."""
    for i,(a,b) in enumerate(zip(points,points[1:])):
        a,b=np.asarray(a),np.asarray(b)
        d=b-a
        n=np.array((-d[1],d[0]))/max(.001,np.linalg.norm(d))
        u=i/max(1,len(points)-2)
        w=width*(.25+.75*math.sin(math.pi*(.08+.84*u)))
        # Two deliberately sized notches, not random pixel confetti.
        if i%11 in (4,5): w*=.52
        polygon(g,[a,b,b-n*w,a-n*w*.9],1)
        polygon(g,[a,b,b-n*w*.68,a-n*w*.65],2)
        if ink==3: polygon(g,[a,b,b-n*min(.3,w*.22),a-n*min(.3,w*.22)],3)


def contour(clip, frame, accent=False):
    g=np.zeros((SIZE,SIZE),dtype=np.uint8)
    if frame>=FRAMES-1: return g
    # A finite phrase: early broad shapes -> separate trailing shards -> nothing.
    if clip=='gather':
        t=min(1.,frame/18)
        for side in (-1,1):
            pts=[]
            for i in range(20):
                u=i/19
                pts.append((8+side*(5*(1-t)+.4)*(1-u*.72),2+u*10))
            if not accent or side==1: strip(g,pts,.65*(1-t)+.15)
        return g
    if clip in ('jet','fan','rally'):
        for lane in range(4 if clip=='jet' else 5):
            if accent and lane not in (1,4): continue
            age=frame-lane*.55
            if age<0 or age>15: continue
            start=max(0.,(age-5)/10)
            end=min(1.,.2+age/5)
            pts=[]
            for i in range(24):
                u=start+(end-start)*i/23
                if clip=='jet':
                    side=-1 if lane%2==0 else 1
                    x=8+side*(.55+lane*.3+math.sin(u*math.pi)*1.8)
                    z=1.5+u*12.6
                elif clip=='fan':
                    angle=-1.05+u*1.95
                    x=8+math.sin(angle)*(4.7+lane*.33)
                    z=5+math.cos(angle)*4.5+lane*.28
                else:
                    x=1.2+u*13.2
                    z=3+lane*1.7+math.sin(u*math.pi*1.5+lane*.7)*.65
                pts.append((x,z))
            fade=max(0.,1-max(0.,age-4)/11)
            strip(g,pts,(.25 if accent else 1.0+lane%2*.4)*fade)
        return g
    if clip in ('eruption','lift'):
        for i in range(7):
            if accent and i not in (1,5): continue
            age=frame-i*.32
            if age<0 or age>16: continue
            grow=1-math.exp(-age*.6)
            drift=max(0,age-3)*.12
            root=np.array((8+(i-3)*.36,1.2))
            direction=np.array(((i-3)*.17,1.0))
            length=(9.8-abs(i-3)*.65)*grow
            if clip=='lift': length*=.86;direction[0]*=-1
            pts=[]
            tail=max(0.,(age-4)/12)
            for j in range(20):
                u=tail+(1-tail)*j/19
                p=root+direction*(length*u+drift)
                p[0]+=math.sin(u*math.pi)*(.65 if i%2 else -.65)
                pts.append(p)
            strip(g,pts,(.22 if accent else 1.4+(.45 if i%3==0 else 0))*max(0.,1-max(0,age-4)/12))
        return g
    if clip.startswith('spin_'):
        variant=('spin_a','spin_b','spin_c').index(clip)
        for i in range(9):
            if accent and i%4!=1: continue
            age=frame-i*.45
            if age<0 or age>14: continue
            birth=-2.4+i*.7+variant*.55
            pts=[]
            tail=max(0.,(age-4)/10)
            for j in range(18):
                u=tail+(1-tail)*j/17
                a=birth+u*(.35+variant*.06)
                r=3.1+min(age,5)*.35+u*.7
                pts.append((8+math.sin(a)*r,8+math.cos(a)*r))
            strip(g,pts,(.2 if accent else .95+variant*.18)*max(0,1-max(0,age-3)/11))
        return g
    # Burst: one contact event. Independent rays detach after the compressed first beat.
    for i in range(7):
        if accent and i not in (2,5): continue
        a=i*2.399
        d=np.array((math.sin(a),math.cos(a)))
        age=frame*.75
        start=.1+max(0,age-1)*.26
        length=(2.8+i%3*.55)*max(0.,1-age/14)
        pts=[np.array((8,8))+d*(start+u*length) for u in np.linspace(0,1,15)]
        strip(g,pts,(.19 if accent else .7)*max(0.,1-age/14))
    return g


def build(assets,write):
    inks=ink_uvs(assets)
    for clip in CLIPS:
        for layer,tint in (('body',0xd1e2ed),('accent',0xb84358)):
            for frame in range(FRAMES):
                key=f'combat_vfx/warrior_flourish/{clip}_{layer}_{frame}'
                write(assets/f'models/{key}.json',{'ambientocclusion':False,
                    'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                    'elements':geometry(contour(clip,frame,layer=='accent'),inks,curved=clip!='burst')})
                write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key,
                    'tints':[{'type':'minecraft:constant','value':tint}]}})


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
