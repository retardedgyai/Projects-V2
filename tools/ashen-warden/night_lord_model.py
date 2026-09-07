"""Reference-driven armor study: MatE, The Lord of Night Boss.
Geometry and pixel textures are newly built in Blender; no reference asset files copied.
"""
import math
from mathutils import Vector

def build_model(box):
    def spike(b,a,z,width,depth,material='edge',steps=6):
        a=Vector(a);z=Vector(z);v=z-a
        r=Vector((0,1,0)).rotation_difference(v.normalized()).to_euler('XYZ')
        rotation=tuple(math.degrees(c) for c in r)
        for i in range(steps):
            t=(i+.5)/steps;w=width*(1-i/steps)**.9
            box(b,list(a+v*t),(max(.016,w),v.length/steps+.012,max(.015,depth*(1-i/steps))),material,rotation)
    def band(b,c,w,h,d,angle=0,material='iron'):
        box(b,c,(w,h,d),material,(0,0,angle))
    # Narrow armored trunk: many layered planes instead of one rectangular torso.
    box('pelvis',(0,0,0),(.52,.29,.34),'dark')
    box('spine',(0,.04,0),(.49,.45,.33),'dark')
    box('chest',(0,.005,-.015),(.63,.51,.39),'iron',(8,0,0))
    for s in (-1,1):
        band('chest',(s*.24,.16,.195),.40,.15,.16,-s*24,'edge')
        band('chest',(s*.20,-.005,.22),.37,.14,.13,-s*26,'iron')
        band('chest',(s*.15,-.16,.24),.28,.095,.11,-s*30,'bronze')
        band('spine',(s*.16,.02,.20),.33,.105,.075,s*25,'edge')
        band('pelvis',(s*.13,.11,.21),.34,.09,.095,-s*26,'dark')
        band('pelvis',(s*.13,.045,.24),.31,.065,.06,-s*26,'edge')
    # White pixel heart, an angular rim, and the long horizontal flare in the reference.
    for angle in (-45,0,45,90):
        box('vfx_chest',(0,-.06,.13),(.32,.32,.08),'bronze',(0,0,angle))
    box('vfx_chest',(0,-.06,.205),(.25,.25,.09),'ember',(0,0,45))
    box('vfx_chest',(0,-.055,.24),(.14,.34,.04),'ember')
    box('vfx_chest',(0,-.055,.25),(.34,.13,.045),'ember')
    box('vfx_chest',(0,-.055,.255),(1.34,.018,.018),'ember')
    # Tall closed visor, narrow vertical black slots, forked icy crown.
    box('neck',(0,0,0),(.19,.24,.23),'dark')
    box('head',(0,.09,-.012),(.37,.57,.32),'bronze')
    box('head',(0,.13,.17),(.34,.40,.075),'iron',(-9,0,0))
    for x in (-.105,-.035,.035,.105):
        box('head',(x,.06,.218),(.027,.30,.022),'dark',(-9,0,0))
    for s in (-1,1):
        box('head',(s*.20,.07,.03),(.09,.45,.24),'edge',(0,0,-s*12))
        spike('head',(s*.15,.34,.02),(s*.12,.64,.09),.12,.10,'bone',4)
    spike('head',(0,.34,.12),(0,.77,.09),.13,.13,'edge',5)
    # Black bent horns: broad at base, sharp and asymmetric at the tips.
    for s in (-1,1):
        pts=[(s*.24,.24,-.075),(s*.47,.48,-.09),(s*.32,.77,-.11),(s*.53,.98,-.14)]
        for j in range(3):spike('crest',pts[j],pts[j+1],.18-j*.04,.17-j*.04,'dark',4)
    # Purple split crest and torn mantle behind the armored silhouette.
    for j in range(4):
        box('crest',(.02+j*.052,.18-j*.08,-.29-j*.055),(.35-j*.04,.18,.13),'cloth',(25,0,-12))
    box('mantle',(0,-.15,-.09),(.76,.43,.16),'cloth',(-10,0,0))
    for i in range(4):
        box('cape_01',((i-1.5)*.155,-.23,-.11),(.18,.57+(i%2)*.10,.09),'cloth',(-9,0,(i-1.5)*4))
        spike('cape_02',((i-1.5)*.155,.03,-.12),((i-1.5)*.18,-.69+(i%2)*.15,-.21),.21,.075,'cloth',5)
    # Radiating shoulder plates and long shoulder spikes dominate the silhouette.
    for side,s in [('l',-1),('r',1)]:
        b='shoulder_'+side
        box(b,(s*.045,.045,0),(.45,.22,.48),'dark',(0,0,-s*13))
        for j in range(3):
            box(b,(s*(.08+j*.08),.12-j*.085,.02),(.52-j*.035,.12,.53-j*.035),'iron' if j%2 else 'edge',(0,0,-s*(17+j*4)))
        spike(b,(s*.02,.16,.04),(s*.21,.64,-.035),.12,.055,'bone',8)
        spike(b,(s*.12,.15,.04),(s*.52,.53,.01),.16,.05,'edge',8)
        spike(b,(s*.22,.12,.01),(s*.77,.35,-.03),.16,.05,'edge',8)
        spike(b,(s*.27,.07,.04),(s*.75,.10,.06),.12,.06,'iron',7)
        spike(b,(s*.02,.15,-.12),(s*.43,.60,-.43),.13,.05,'bronze',7)
        # Long hanging vambraces with bevel strips and a jagged cuff.
        box('upper_arm_'+side,(0,-.25,0),(.22,.47,.24),'dark')
        box('upper_arm_'+side,(s*.055,-.26,.10),(.27,.40,.14),'iron',(0,0,-s*8))
        f='forearm_'+side
        box(f,(0,-.22,.035),(.30,.51,.30),'bronze',(0,0,-s*5))
        box(f,(0,-.20,.215),(.20,.47,.10),'iron',(0,0,-s*5))
        box(f,(s*.13,-.22,.205),(.045,.48,.06),'edge',(0,0,-s*5))
        box(f,(-s*.12,-.22,.205),(.038,.48,.06),'edge',(0,0,-s*5))
        for x in (-.13,0,.13):spike(f,(x,-.40,.07),(x+s*.055,-.70,.12),.10,.09,'edge',4)
        h='hand_'+side
        box(h,(0,-.075,.015),(.20,.23,.22),'dark')
        for x in (-.09,-.03,.03,.09):
            box(h,(x,-.205,.045),(.042,.15,.065),'dark',(18,0,0))
        # Articulated legs visible beneath long split armor skirts.
        box('thigh_'+side,(0,-.29,0),(.24,.61,.28),'dark')
        sh='shin_'+side
        box(sh,(0,-.28,.03),(.25,.60,.27),'bronze')
        box(sh,(0,-.26,.18),(.17,.57,.06),'iron')
        for sign in (-1,1):box(sh,(sign*.108,-.27,.19),(.035,.60,.055),'edge')
        box(sh,(0,.04,.22),(.20,.20,.12),'edge',(0,0,45))
        box(sh,(0,.04,.29),(.12,.12,.055),'iron',(0,0,45))
        box('foot_'+side,(0,-.02,.08),(.255,.16,.42),'dark')
        box('foot_'+side,(0,.025,.12),(.22,.09,.37),'iron',(-9,0,0))
        spike('foot_'+side,(0,.03,.23),(0,-.035,.47),.22,.13,'bronze',4)
        # Long pointed hip plates, with parallel inset bevels rather than cubes for knees.
        ta='tasset_'+side
        box(ta,(s*.035,-.36,.17),(.32,.78,.15),'bronze',(0,0,-s*6))
        box(ta,(s*.018,-.34,.26),(.22,.70,.055),'iron',(0,0,-s*6))
        box(ta,(s*.14,-.35,.28),(.035,.72,.045),'edge',(0,0,-s*6))
        box(ta,(-s*.10,-.35,.28),(.035,.72,.045),'edge',(0,0,-s*6))
        spike(ta,(s*.04,-.69,.19),(s*.10,-.98,.24),.32,.15,'bronze',5)
        box(ta,(s*.075,-.76,.28),(.20,.20,.05),'edge',(0,0,45))
        box(ta,(s*.075,-.76,.315),(.13,.13,.025),'iron',(0,0,45))
    # Pale lavender greatsword with a crooked black crossguard.
    box('weapon_root',(0,0,.05),(.09,.10,.50),'dark')
    for z in (-.16,-.055,.05,.155):box('weapon_root',(0,0,z),(.115,.115,.035),'bronze')
    box('weapon_root',(0,0,.34),(.23,.18,.25),'dark',(0,0,45))
    for s in (-1,1):
        spike('weapon_root',(s*.06,0,.31),(s*.46,0,.47),.15,.13,'dark',4)
        spike('weapon_root',(s*.42,0,.44),(s*.51,0,.22),.10,.085,'dark',3)
        spike('weapon_root',(s*.33,0,.40),(s*.57,0,.64),.08,.075,'dark',3)
    # The longest edge ends exactly at weapon_tip=2.64.
    box('weapon_root',(0,0,1.365),(.34,.105,1.87),'bone')
    box('weapon_root',(0,.061,1.36),(.20,.025,1.86),'ember')
    for s in (-1,1):
        box('weapon_root',(s*.19,0,1.37),(.045,.055,1.86),'ember')
    for j in range(5):
        width=.35*(1-j/5)
        box('weapon_root',(0,0,2.31+(j+.5)*.066),(width,.07,.068),'bone')
