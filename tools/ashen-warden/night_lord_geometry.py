"""Pixel-contour plate armor rebuilt from the observed MatE turntable."""
import math
from mathutils import Vector,Euler,Matrix

def build_model(box):
    original_box=box
    def box(b,c,size,mat='iron',rot=(0,0,0),front_uv=None):
        if b=='weapon_root':
            turn=Matrix.Rotation(math.pi/2,3,'Z');c=turn@Vector(c)
            r=(turn@Euler(tuple(math.radians(a) for a in rot),'XYZ').to_matrix()).to_euler('XYZ')
            rot=tuple(math.degrees(a) for a in r)
        original_box(b,c,size,mat,rot,front_uv)
    def plate(b,points,c=(0,0,0),depth=.05,mat='iron',rot=(0,0,0),rim=True,step=.075):
        # Rasterize a designed outline into thin strips. Native vanilla cuboids,
        # one continuous 32px UV field, no conical stack or smoothed mesh.
        low=min(y for x,y in points);high=max(y for x,y in points)
        left=min(x for x,y in points);right=max(x for x,y in points)
        step=max(step,.065);rows=max(1,math.ceil((high-low)/step));dy=(high-low)/rows
        rotation=Euler(tuple(math.radians(a) for a in rot),'XYZ').to_matrix()
        for row in range(rows):
            y=low+(row+.5)*dy;cuts=[]
            for (x0,y0),(x1,y1) in zip(points,points[1:]+points[:1]):
                if min(y0,y1)<=y<max(y0,y1):cuts.append(x0+(x1-x0)*(y-y0)/(y1-y0))
            if len(cuts)<2:continue
            a=min(cuts);z=max(cuts);width=z-a
            if width<.006:continue
            segments=[(a,z,mat)]
            if rim and width>.16:
                segments=[(a,a+.045,'edge'),(a+.045,z-.045,mat),(z-.045,z,'edge')]
            for x0,x1,m in segments:
                pos=Vector(c)+rotation@Vector(((x0+x1)/2,y,0))
                uv=[(x0-left)/(right-left)*16,(y-dy/2-low)/(high-low)*24,
                    (x1-left)/(right-left)*16,(y+dy/2-low)/(high-low)*24]
                box(b,list(pos),(x1-x0,dy+.0002,depth),m,rot,uv)
    def bar(b,a,z,width,depth,mat='iron'):
        a=Vector(a);z=Vector(z);v=z-a;r=Vector((0,1,0)).rotation_difference(v.normalized()).to_euler('XYZ')
        box(b,list((a+z)/2),(width,v.length,depth),mat,tuple(math.degrees(c) for c in r))
    def shard(b,a,z,width,depth=.035,mat='edge'):
        a=Vector(a);z=Vector(z);v=z-a;r=Vector((0,1,0)).rotation_difference(v.normalized()).to_euler('XYZ')
        plate(b,[(-width*.5,0),(-width*.42,v.length*.20),(0,v.length),(width*.26,v.length*.25),(width*.5,0)],a,depth,mat,tuple(math.degrees(c) for c in r),False,.05)
    # A connected, tapered cuirass: chest and abdomen do not open into a black hole.
    box('pelvis',(0,0,0),(.49,.30,.35),'bronze')
    box('spine',(0,.025,.015),(.49,.48,.36),'iron')
    box('chest',(0,.03,-.025),(.64,.48,.36),'bronze')
    for s in (-1,1):
        box('chest',(s*.22,.04,.14),(.30,.42,.19),'iron',(0,s*20,-s*11))
        plate('chest',[(-.30,.21),(.12,.25),(.29,.03),(.12,-.19),(-.25,-.07)],(s*.12,.035,.255),.045,'iron',(0,0,-s*16))
        for j in range(3):
            plate('spine',[(-.21,.06),(.20,.055),(.14,-.07),(-.16,-.09)],(s*.11,.13-j*.14,.22),.035,'bronze' if j==1 else 'iron',(0,0,-s*18),True,.06)
        bar('pelvis',(s*.015,.13,.24),(s*.30,-.02,.22),.07,.055,'edge')
    # Tall visor with a tapered lower edge and a diamond forehead.
    box('neck',(0,0,0),(.20,.23,.23),'bronze')
    box('head',(0,.08,-.015),(.35,.53,.31),'dark')
    plate('head',[(-.23,.38),(.23,.38),(.205,-.13),(0,-.25),(-.205,-.13)],(0,.015,.185),.07,'iron',rim=False)
    for x in (-.15,-.075,0,.075,.15):
        length=.31 if abs(x)<.13 else .25
        box('head',(x,.045,.231),(.028,length,.016),'dark')
    for s in (-1,1):
        plate('head',[(-.07,.29),(.065,.37),(.055,-.20),(-.055,-.25)],(s*.22,.015,.075),.075,'iron',(0,s*28,-s*8),True)
        shard('head',(s*.17,.29,.11),(s*.25,.67,.075),.055,.035)
    plate('head',[(0,.36),(-.22,.56),(0,.83),(.22,.56)],(0,0,.17),.04,'iron',rim=True)
    plate('head',[(0,.47),(-.09,.56),(0,.68),(.09,.56)],(0,0,.203),.012,'bronze',rim=True,step=.04)
    for x,h in [(-.18,.74),(-.085,.91),(.025,1.02),(.13,.81)]:
        shard('head',(x,.52,.11),(x-.015,h,.10),.05,.035,'edge')
    # Bent dark horns, broad planes with deliberate changes of direction.
    for s in (-1,1):
        pts=[(s*.19,.03,-.12),(s*.45,.29,-.12),(s*.29,.59,-.12),(s*.56,.79,-.14)]
        for j in range(3):bar('crest',pts[j],pts[j+1],.19-j*.043,.13,'dark')
        shard('crest',pts[-1],(s*.67,.85,-.14),.08,.08,'dark')
    # The reference has narrow torn streamers, not a broad rectangular cape.
    for j in range(3):
        plate('crest',[(-.17,.07),(.14,.09),(.21,-.20),(.045,-.14),(-.10,-.25)],
              (.02+j*.015,.12-j*.10,-.27-j*.14),.025,'cloth',(-50,0,-7),False,.05)
    for side,s in [('l',-1),('r',1)]:
        for j in range(2):
            plate('cape_01',[(-.11,.08),(.09,.08),(.075,-.44-j*.13),(-.04,-.35),(-.12,-.48)],
                  (s*(.23+j*.12),-.12,-.08),.022,'cloth',(-12,0,-s*7),False,.05)
        shard('cape_02',(s*.18,.08,-.03),(s*.28,-.62,-.12),.17,.022,'cloth')
    # Shoulder shells overlap outward, with flat blade-like projections.
    shell=[(-.25,.17),(.10,.25),(.43,.05),(.30,-.02),(.24,-.16),(-.19,-.08)]
    for side,s in [('l',-1),('r',1)]:
        b='shoulder_'+side
        box(b,(s*.08,-.045,-.015),(.38,.22,.38),'bronze',(0,0,-s*19))
        for j in range(2):
            points=[(s*x,y) for x,y in shell]
            plate(b,points,(s*j*.075,.03-j*.13,.15-j*.025),.065,'iron',(0,0,-s*13),True,.045)
            plate(b,points,(s*j*.075,.03-j*.13,-.16),.055,'iron',(0,0,-s*13),True,.06)
        for a,end,w in [((-.10,.10,.13),(.06,.48,.08),.14),
                        ((.10,.08,.11),(.56,.29,.06),.17),
                        ((.04,.10,-.13),(.35,.37,-.28),.13)]:
            shard(b,(s*a[0],a[1],a[2]),(s*end[0],end[1],end[2]),w,.03,'edge')
        u='upper_arm_'+side;f='forearm_'+side;h='hand_'+side
        box(u,(0,-.23,0),(.23,.46,.24),'bronze')
        plate(u,[(-.14,.02),(.14,.02),(.17,-.38),(0,-.46),(-.17,-.38)],(0,-.02,.16),.06,'iron',(0,0,s*6))
        box(f,(0,-.21,0),(.255,.43,.24),'bronze')
        # Broad tapered vambraces with a painted inset and small jagged cuffs.
        outline=[(-.18,.045),(.17,.045),(.21,-.39),(.10,-.48),(0,-.43),(-.13,-.50),(-.22,-.39)]
        plate(f,outline,(0,0,.155),.07,'iron',(0,0,s*5),True,.045)
        plate(f,[(-.105,-.02),(.085,-.02),(.10,-.36),(-.10,-.35)],(0,0,.20),.02,'bronze',(0,0,s*5),True,.05)
        for x in (-.18,.16):shard(f,(x,-.30,.13),(x+s*.10,-.49,.11),.10,.035,'edge')
        if side=='r':
            box(h,(.074,-.07,-.01),(.065,.17,.22),'iron')
            for z in (-.085,-.035,.015,.065):
                box(h,(.015,-.151,z),(.13,.047,.043),'bronze')
                box(h,(-.055,-.104,z),(.045,.12,.043),'iron')
            box(h,(.014,-.025,.09),(.13,.065,.06),'edge',(0,0,-22))
        else:
            box(h,(0,-.07,0),(.17,.18,.16),'bronze')
            for x,ln in [(-.075,.20),(-.025,.25),(.025,.26),(.075,.20)]:
                bar(h,(x,-.13,.01),(x*1.2,-.13-ln,.06),.035,.04,'iron')
                bar(h,(x*1.2,-.13-ln,.06),(x*1.25,-.16-ln,.12),.025,.03,'edge')
        # Long overlapping skirt planes preserve the reference's elongated lower half.
        ta='tasset_'+side
        skirt=[(-.18,.04),(.18,.04),(.16,-.68),(0,-.92),(-.17,-.70)]
        plate(ta,skirt,(s*.035,-.02,.20),.08,'iron',(0,0,-s*5),True,.045)
        plate(ta,[(-.10,-.025),(.09,-.025),(.085,-.64),(0,-.75),(-.085,-.64)],(s*.035,-.02,.245),.025,'bronze',(0,0,-s*5),True,.05)
        plate(ta,[(0,.14),(.14,0),(0,-.14),(-.14,0)],(s*.075,-.73,.27),.028,'iron',rim=True)
        plate(ta,[(-.12,.03),(.12,.03),(.09,-.68),(-.10,-.78)],(s*.26,-.01,-.02),.05,'iron',(0,s*30,-s*14),True,.06)
        box('thigh_'+side,(0,-.29,0),(.235,.60,.26),'bronze')
        sh='shin_'+side
        box(sh,(0,-.26,0),(.22,.57,.24),'bronze')
        plate(sh,[(-.14,.10),(.14,.10),(.11,-.47),(0,-.55),(-.12,-.47)],(0,-.025,.14),.07,'iron',rim=True)
        plate(sh,[(0,.14),(.13,0),(0,-.14),(-.13,0)],(0,.02,.21),.025,'bronze',rim=True)
        ft='foot_'+side
        box(ft,(0,-.055,.08),(.24,.15,.38),'bronze')
        plate(ft,[(-.13,-.15),(.13,-.15),(.115,.16),(0,.29),(-.115,.16)],(0,.06,.09),.075,'iron',(90,0,0),True)
    # Near-circular pixel light, purple halo, and the thin horizontal flare.
    for row in range(-6,7):
        half=math.sqrt(max(0,6.7**2-row*row))*.030
        box('vfx_chest',(0,-.055+row*.041,.105),(half*2,.041,.028),'cloth')
    for row,half in enumerate([1,2,2,4,3,4,3,2,3,1,1]):
        box('vfx_chest',(0,-.255+row*.040,.132),(half*.075,.040,.023),'ember')
    shard('vfx_chest',(-.09,.05,.11),(-.15,.27,.105),.07,.016,'ember')
    shard('vfx_chest',(.075,.035,.11),(.06,.22,.105),.07,.016,'ember')
    box('vfx_chest',(0,-.055,.15),(1.20,.013,.012),'ember')
    # Dark diagonal back protrusion and pale hanging fragments seen in the turntable.
    bar('mantle',(-.27,-.49,-.16),(.59,.04,-.17),.115,.11,'dark')
    bar('mantle',(.49,.06,-.17),(.79,-.08,-.17),.09,.08,'dark')
    bar('mantle',(.60,.17,-.17),(.63,-.17,-.17),.07,.07,'dark')
    for x,ln in [(.59,.65),(.68,.72)]:shard('mantle',(x,-.08,-.18),(x+.06,-ln,-.18),.07,.025,'edge')
    # Broad but thin pale blade. Edge contour includes shoulders and an angular tip.
    box('weapon_root',(0,0,.03),(.085,.095,.48),'dark')
    for z in (-.14,-.025,.09,.20):box('weapon_root',(0,0,z),(.105,.105,.025),'bronze')
    for s in (-1,1):
        pts=[(s*.05,0,.31),(s*.25,0,.44),(s*.39,0,.63),(s*.47,0,.51)]
        for a,z in zip(pts,pts[1:]):bar('weapon_root',a,z,.075,.075,'dark')
        bar('weapon_root',(s*.20,0,.41),(s*.31,0,.21),.07,.07,'dark')
        shard('weapon_root',(s*.30,0,.24),(s*.48,0,.27),.06,.055,'dark')
    plate('weapon_root',[(x*1.4,.43+(y-.43)*1.35) for x,y in [(-.17,.43),(.17,.43),(.215,.69),(.17,2.24),(0,2.64),(-.17,2.24),(-.215,.69)]],
          (0,0,0),.065,'bone',(90,0,0),True,.075)
    # Central fuller and small angular traces, kept flat on the blade surface.
    def blade_point(x,y,z):return x*1.4,y,.43+(z-.43)*1.35
    bar('weapon_root',blade_point(0,.043,.62),blade_point(0,.043,2.35),.045,.013,'edge')
    for s in (-1,1):
        bar('weapon_root',blade_point(0,.045,.87),blade_point(s*.12,.045,.65),.032,.012,'bronze')
        bar('weapon_root',blade_point(s*.12,.045,.65),blade_point(s*.12,.045,1.30),.030,.012,'edge')
