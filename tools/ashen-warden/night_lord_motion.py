"""Authored full-body keys informed by observed combat footage, not extracted motion."""
import math,bpy
from mathutils import Vector,Matrix,Quaternion

CLIPS={'idle':(60,True,None),'walk':(32,True,None),'slash_01':(48,False,[17,21]),
       'heavy_slash':(62,False,[28,32]),'dash':(44,False,[17,21]),
       'spin_slash':(56,False,[19,31]),'spiral_combo':(78,False,[18,52]),
       'vault_slam':(72,False,[41,46]),'hurt':(16,False,None),
       'phase_transition':(64,False,None),'death':(60,False,None)}
HIT_WINDOWS={'spiral_combo':[(18,29),(41,52)]}

def curve(t,keys,linear=False):
    for (a,v),(b,w) in zip(keys,keys[1:]):
        if t<=b:
            u=max(0,min(1,(t-a)/(b-a)))
            if not linear:u=u*u*(3-2*u)
            return v+(w-v)*u
    return keys[-1][1]

def vector(t,keys):return Vector(tuple(curve(t,[(k,v[i]) for k,v in keys]) for i in range(3)))

def make_pose(rig,C,bind):
    inverse=C.inverted();names=list(bind);bones=rig.pose.bones;roll=0.
    def cv(v):return (C@Vector((*v,1))).to_3d()
    def matrix(name):return inverse@bones[name].matrix@C
    def set_world(name,p,q):
        bones[name].matrix=C@(Matrix.Translation(p)@q.to_matrix().to_4x4())@inverse
        bpy.context.view_layer.update()
    def limb(upper,lower,tip,target,pole):
        start=matrix(upper).translation;l1=(bind[lower]-bind[upper]).length;l2=(bind[tip]-bind[lower]).length
        axis=target-start;distance=max(abs(l1-l2)+.01,min(l1+l2-.004,axis.length));axis.normalize()
        target=start+axis*distance;a=(l1*l1-l2*l2+distance*distance)/(2*distance)
        bend=Vector(pole)-axis*Vector(pole).dot(axis)
        if bend.length<.001:bend=Vector((1,0,0))-axis*axis.x
        bend.normalize();joint=start+axis*a+bend*math.sqrt(max(0,l1*l1-a*a))
        for name,p0,p1 in [(upper,start,joint),(lower,joint,target)]:
            set_world(name,p0,Vector((0,-1,0)).rotation_difference((p1-p0).normalized()))
        return target,(target-joint).normalized()
    def pose(name,t):
        nonlocal roll
        duration=CLIPS[name][0];p=math.pi
        root=Vector((0,0,0));pelvis=Vector((-.035,-.27,-.025));pelvis_a=(13,-10,-4)
        spine_a=(12,5,-4);chest_a=(5,5,0);head_a=(-25,0,3)
        right=Vector((.63,-.73,.10));left=Vector((-.58,-.84,.08))
        yaw=65.;pitch=-26.;turn=0.;cape=0.;root_a=(0,0,0)
        spin_y=0.;flip_x=0.;airborne=False;foot_q=Quaternion((1,0,0,0))
        foot_l=Vector((-.32,.13,.24));foot_r=Vector((.30,.13,-.28))
        if name=='idle':
            breath=math.sin(t*p/30);pelvis.y+=.009*breath;chest_a=(5+1.1*breath,5,0)
            left.z+=.014*(math.sin((t-6)*p/30)-math.sin(-6*p/30))
        elif name=='walk':
            phase=t*p/16;pelvis.x+=.035*math.sin(phase);pelvis.y+=.022*math.cos(phase*2)
            pelvis_a=(13,-10+3*math.sin(phase),-4);chest_a=(5,5-4*math.sin(phase),0)
            left.z+=.15*math.sin(phase);right.z-=.035*math.sin(phase-.35)
            yaw+=2*math.sin(phase-.4)
            for foot,offset in [(foot_l,0),(foot_r,16)]:
                u=(t+offset)%32
                if u<20:foot.z=.40-u*.04
                else:foot.z=-.40+(u-20)/12*.8;foot.y+=.16*math.sin((u-20)/12*p)
        elif name=='slash_01':
            keys=[(0,0),(12,1),(16,1),(21,-1),(25,-1.12),(35,-.70),(48,0)]
            hip=curve(t+2,keys);torso=curve(t+1,keys);turn=curve(t,keys)
            pelvis_a=(13+3*abs(hip),hip*28-10,-4);spine_a=(12+2*abs(torso),torso*16+5,-4);chest_a=(5+2*abs(turn),turn*12+5,-turn*4)
            pelvis.y-=curve(t,[(0,0),(12,.10),(17,.08),(24,.13),(36,.07),(48,0)])
            pelvis.x-=curve(t,[(0,0),(13,0),(21,.075),(32,.075),(48,0)])
            root.z=curve(t,[(0,0),(12,0),(18,.34),(22,.55),(48,.55)])
            right=vector(t,[(0,tuple(right)),(12,(.65,-.24,-.31)),(16,(.65,-.24,-.31)),
                (19,(.20,-.49,.58)),(22,(-.43,-.70,.48)),(28,(-.45,-.76,.24)),(38,(.30,-.80,.22)),(48,tuple(right))])
            yaw=curve(t,[(0,65),(12,116),(16,116),(21,-83),(25,-104),(34,-92),(41,25),(48,65)],linear=17<=t<=21)
            pitch=curve(t,[(0,-26),(12,10),(16,10),(21,-13),(28,-19),(38,-28),(48,-26)])
            foot_l.z+=curve(t,[(0,0),(11,0),(17,.64),(34,.64),(48,.55)])
            if 11<t<17:foot_l.y+=.15*math.sin((t-11)/6*p)
            if 34<t<48:foot_l.y+=.10*math.sin((t-34)/14*p)
            foot_r.z+=curve(t,[(0,0),(28,0),(40,.55),(48,.55)])
            if 28<t<40:foot_r.y+=.12*math.sin((t-28)/12*p)
            left.x-=.12*abs(turn);left.z-=.16*turn;cape=turn*13
        elif name=='heavy_slash':
            tension=curve(t,[(0,0),(20,1),(27,1),(32,-1),(39,-1),(49,-.4),(62,0)])
            pelvis_a=(13-9*tension,-10+12*tension,-4)
            spine_a=(12-14*tension,5+7*tension,-4);chest_a=(5-8*tension,5,0)
            pelvis.y-=curve(t,[(0,0),(22,.04),(28,0),(33,.29),(40,.26),(52,.10),(62,0)])
            root.z=curve(t,[(0,0),(25,0),(33,.43),(62,.43)])
            right=vector(t,[(0,tuple(right)),(20,(.40,.24,-.27)),(27,(.40,.24,-.27)),
                (30,(.26,-.15,.64)),(33,(.17,-.75,.58)),(40,(.19,-.78,.56)),(51,(.52,-.78,.28)),(62,tuple(right))])
            yaw=curve(t,[(0,65),(20,18),(27,18),(33,-12),(45,-12),(62,65)])
            pitch=curve(t,[(0,-26),(20,115),(27,115),(30,15),(33,-28),(43,-28),(62,-26)],linear=28<=t<=32)
            foot_l.z+=curve(t,[(0,0),(23,0),(29,.55),(45,.55),(62,.43)])
            if 23<t<29:foot_l.y+=.15*math.sin((t-23)/6*p)
            foot_r.z+=curve(t,[(0,0),(40,0),(53,.43),(62,.43)])
            if 40<t<53:foot_r.y+=.10*math.sin((t-40)/13*p)
            left=vector(t,[(0,tuple(left)),(22,(-.52,-.55,-.15)),(34,(-.61,-.81,.19)),(62,tuple(left))]);cape=-tension*17
        elif name=='dash':
            # Low travelling cut: rear-foot drive, extended lead step, braced contact.
            root.z=curve(t,[(0,0),(11,0),(15,.58),(18,1.40),(21,1.70),(44,1.70)])
            crouch=curve(t,[(0,0),(10,.18),(15,.11),(20,.21),(27,.16),(44,0)])
            pelvis.y-=crouch
            twist=curve(t,[(0,0),(10,1),(15,1),(21,-1),(29,-.7),(44,0)])
            pelvis_a=(13+10*abs(twist),-10+30*twist,-4);spine_a=(12+4*abs(twist),5+16*twist,-4)
            chest_a=(5,5+12*twist,-4*twist)
            right=vector(t,[(0,tuple(right)),(10,(.66,-.39,-.36)),(15,(.66,-.39,-.36)),
                (18,(.26,-.48,.63)),(21,(-.40,-.64,.45)),(29,(-.36,-.73,.26)),(44,tuple(right))])
            yaw=curve(t,[(0,65),(10,118),(15,118),(21,-76),(27,-104),(35,-30),(44,65)],linear=16<=t<=21)
            pitch=curve(t,[(0,-26),(10,6),(15,6),(20,-8),(28,-20),(44,-26)])
            foot_l.z+=curve(t,[(0,0),(10,0),(18,1.92),(29,1.92),(44,1.70)])
            foot_r.z+=curve(t,[(0,0),(14,0),(22,.95),(28,.95),(38,1.70),(44,1.70)])
            if 10<t<18:foot_l.y+=.26*math.sin((t-10)/8*p)
            if 14<t<22:foot_r.y+=.20*math.sin((t-14)/8*p)
            if 28<t<38:foot_r.y+=.14*math.sin((t-28)/10*p)
            if 29<t<44:foot_l.y+=.09*math.sin((t-29)/15*p)
            left.x-=.16*abs(twist);left.z-=.18*twist;cape=-22*abs(twist)
        elif name in ['spin_slash','spiral_combo']:
            double=name=='spiral_combo';end=78 if double else 56
            spin_keys=[(0,0),(14,45),(18,45),(31,-315),(39,-315),(end,-360)]
            if double:spin_keys=[(0,0),(13,45),(17,45),(29,-315),(36,-315),(40,-280),(52,-675),(61,-695),(78,-720)]
            spin_y=curve(t,spin_keys,linear=(18<=t<=31 if not double else 18<=t<=29 or 41<=t<=52))
            effort=curve(t,[(0,0),(12,1),(end-15,1),(end,0)])
            pelvis.y-=.12*effort;pelvis_a=(13+4*effort,-10,-4);spine_a=(12,5,-4+5*effort)
            chest_a=(5,5,-7*effort)
            root.z=curve(t,[(0,0),(15,0),(31,.8),(end,.8)]) if not double else curve(t,[(0,0),(14,0),(29,.75),(38,.75),(52,1.70),(78,1.70)])
            right=vector(t,[(0,tuple(right)),(13,(.77,-.36,.03)),(end-16,(.77,-.36,.03)),(end,tuple(right))])
            left=vector(t,[(0,tuple(left)),(14,(-.54,-.43,-.25)),(end-16,(-.54,-.43,-.25)),(end,tuple(left))])
            yaw=curve(t,[(0,65),(14,75),(end-14,75),(end,65)])
            pitch=curve(t,[(0,-26),(14,-3),(end-14,-3),(end,-26)])
            yaw+=spin_y;root_a=(0,spin_y,0);cape=-25*effort
            # Pivot steps follow the body. Alternate each foot's lift; soles turn with the pelvis.
            foot_q=Quaternion((0,1,0),math.radians(spin_y))
            for foot,offset in [(foot_l,0),(foot_r,p)]:
                local=foot.copy();phase=math.radians(-spin_y)*2+offset
                foot[:]=foot_q@local+Vector((0,0,root.z))
                foot.y+=.15*max(0,math.sin(phase))*effort
        elif name=='vault_slam':
            # Pelvis-centred forward somersault: gameplay root follows only the leap trajectory.
            root.z=curve(t,[(0,0),(21,0),(28,.40),(38,1.90),(45,2.40),(72,2.40)])
            root.y=curve(t,[(0,0),(21,0),(27,1.50),(31,2.75),(36,2.75),(41,1.40),(45,0),(72,0)])
            flip_x=curve(t,[(0,0),(27,0),(30,60),(38,310),(42,360),(72,360)],linear=28<=t<=42)
            airborne=22<t<45
            crouch=curve(t,[(0,0),(17,.22),(21,.22),(27,-.05),(39,-.05),(45,.28),(54,.21),(72,0)])
            pelvis.y-=crouch;pelvis_a=(13+flip_x,-10,-4)
            spine_a=(12+curve(t,[(0,0),(20,8),(30,24),(38,24),(45,20),(72,0)]),5,-4)
            right=vector(t,[(0,tuple(right)),(20,(.54,.04,-.29)),(28,(.42,.10,-.27)),
                (36,(.42,.10,-.27)),(43,(.15,-.47,.63)),(47,(.12,-.74,.56)),(56,(.22,-.74,.46)),(72,tuple(right))])
            yaw=curve(t,[(0,65),(20,18),(37,18),(44,-8),(54,-8),(72,65)])
            pitch=curve(t,[(0,-26),(20,74),(36,74),(44,-23),(54,-23),(72,-26)])
            left=vector(t,[(0,tuple(left)),(22,(-.47,-.35,-.18)),(37,(-.47,-.35,-.18)),(46,(-.68,-.72,.20)),(72,tuple(left))])
            cape=curve(t,[(0,0),(21,-15),(30,-48),(41,-35),(47,20),(60,8),(72,0)])
            foot_l.z+=root.z;foot_r.z+=root.z
            if airborne:
                # Feet are solved below after the pelvis transform has been evaluated.
                foot_q=Quaternion((1,0,0),math.radians(flip_x))
        elif name=='hurt':
            hit=curve(t,[(0,0),(2,1),(5,.8),(10,.2),(16,0)])
            spine_a=(12-17*hit,5,-4-10*hit);head_a=(-25-13*hit,0,3)
            pelvis.y-=.045*hit;left.x-=.07*hit;cape=hit*9
        elif name=='phase_transition':
            fold=curve(t,[(0,0),(20,1),(29,1),(34,-.6),(43,-.45),(53,-.2),(64,0)])
            pelvis.y-=max(0,fold)*.21;spine_a=(12+26*fold,5,-4);head_a=(-25+20*fold,0,3)
            right.z+=.11*max(0,fold);left=vector(t,[(0,tuple(left)),(25,(-.40,-.63,.33)),
                (35,(-.77,-.10,.20)),(44,(-.78,-.14,.16)),(64,tuple(left))])
            cape=-fold*18
        elif name=='death':
            kneel=curve(t,[(0,0),(14,1),(22,1),(38,.3),(60,.3)])
            fall=curve(t,[(0,0),(22,0),(37,1),(60,1)])
            root.y=.48*fall;root_a=(90*fall,0,-5*fall);pelvis.y-=.31*kneel
            pelvis_a=tuple(v*(1-fall) for v in pelvis_a)
            spine_a=((12+23*kneel)*(1-fall),5*(1-fall),-4*(1-fall));chest_a=tuple(v*(1-fall) for v in chest_a)
            head_a=((-25+20*kneel)*(1-fall),0,3*(1-fall))
            yaw=65+38*fall;pitch=-26*(1-fall)
            left.y-=.08*kneel;cape=16*fall
            foot_l.z+=.65*fall;foot_r.z+=.65*fall
        # Head counter-rotates rather than turning away with the attacking chest.
        if name in ['slash_01','heavy_slash','dash']:
            head_a=(-25-(pelvis_a[0]+spine_a[0]+chest_a[0]-30)*.75,
                    -(pelvis_a[1]+spine_a[1]+chest_a[1])*.62,3)
        angles={'root':root_a,'pelvis':pelvis_a,'spine':spine_a,'chest':chest_a,'head':head_a,
                'cape_01':(-10+cape,0,-turn*5),'cape_02':(-12+cape*.75,0,-turn*8),
                'crest':(-3+cape*.09,0,0),'tasset_l':(-5-max(0,-pelvis.y-.27)*200,0,-3),'tasset_r':(5-max(0,-pelvis.y-.27)*200,0,3)}
        if name=='death':
            for bn in ['tasset_l','tasset_r']:angles[bn]=tuple(v*(1-fall) for v in angles[bn])
        for bn in names:
            pb=bones[bn];pb.rotation_mode='QUATERNION';pb.location=cv(root if bn=='root' else pelvis if bn=='pelvis' else (0,0,0))
            q=Quaternion((1,0,0,0))
            for axis,a in zip([(1,0,0),(0,1,0),(0,0,1)],angles.get(bn,(0,0,0))):q=q@Quaternion(axis,math.radians(a))
            pb.rotation_quaternion=(C@q.to_matrix().to_4x4()@inverse).to_quaternion()
        bpy.context.view_layer.update()
        chest=matrix('chest')
        if name=='vault_slam' and airborne:
            pelvis_m=matrix('pelvis');tuck=curve(t,[(22,0),(28,1),(38,1),(44,0)])
            for foot,s in [(foot_l,-1),(foot_r,1)]:
                local=Vector((s*.29,-1.05+.43*tuck,-.22-.16*tuck))
                foot[:]=(pelvis_m@local.to_4d()).to_3d()
        for side,local,pole in [('l',left,(-.7,-.2,-.6)),('r',right,(.7,-.1,-.7))]:
            if spin_y:pole=Quaternion((0,1,0),math.radians(spin_y))@Vector(pole)
            if name=='vault_slam':pole=Quaternion((1,0,0),math.radians(flip_x))@Vector(pole)
            target=(chest@local.to_4d()).to_3d()
            wrist,direction=limb('upper_arm_'+side,'forearm_'+side,'hand_'+side,target,pole)
            if side=='l':set_world('hand_l',wrist,matrix('forearm_l').to_quaternion())
            else:
                # Sword axis is the fist axis. Counter-roll changes gradually; no local sword animation.
                min_pitch=math.degrees(math.asin(max(-.9,min(.9,(.16-wrist.y)/2.64))))
                if not airborne or (name=='vault_slam' and t>=42):pitch=max(pitch,min_pitch)
                blade=Vector((math.sin(math.radians(yaw))*math.cos(math.radians(pitch)),math.sin(math.radians(pitch)),math.cos(math.radians(yaw))*math.cos(math.radians(pitch))))
                if name=='vault_slam':blade=Quaternion((1,0,0),math.radians(flip_x))@blade
                q=Vector((0,0,1)).rotation_difference(blade)
                yaxis=-(direction-blade*direction.dot(blade))
                if yaxis.length<.001:yaxis=q@Vector((0,1,0))
                yaxis.normalize();want=math.atan2(-yaxis.dot(q@Vector((1,0,0))),yaxis.dot(q@Vector((0,1,0))))
                if t==0:roll=want
                else:
                    delta=math.atan2(math.sin(want-roll),math.cos(want-roll));roll+=max(-.105,min(.105,delta))
                set_world('hand_r',wrist,q@Quaternion((0,0,1),roll))
        # Feet are authored in clip/world space. Root translation is NOT added twice.
        for side,target in [('l',foot_l),('r',foot_r)]:
            pole=(0,fall,1-fall) if name=='death' else foot_q@Vector((0,0,1))
            end,_=limb('thigh_'+side,'shin_'+side,'foot_'+side,target,pole)
            set_world('foot_'+side,end,foot_q)
        bpy.context.view_layer.update()
    return pose
