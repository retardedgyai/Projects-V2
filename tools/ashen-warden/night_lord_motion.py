"""Authored full-body keys informed by observed combat footage, not extracted motion."""
import math,bpy
from mathutils import Vector,Matrix,Quaternion

CLIPS={'idle':(60,True,None),'walk':(32,True,None),'slash_01':(48,False,[17,21]),
       'heavy_slash':(62,False,[28,32]),'dash':(44,False,[17,21]),
       'spin_slash':(56,False,[19,31]),'spiral_combo':(78,False,[18,52]),
       'vault_slam':(60,False,[34,37]),'rush_combo':(74,False,[14,47]),
       'onslaught':(96,False,[14,64]),'hurt':(16,False,None),
       'phase_transition':(64,False,None),'death':(60,False,None)}
HIT_WINDOWS={'spiral_combo':[(18,29),(41,52)],'rush_combo':[(14,18),(29,32),(43,47)],'onslaught':[(14,18),(29,32),(43,47),(60,64)]}

def curve(t,keys,linear=False):
    for (a,v),(b,w) in zip(keys,keys[1:]):
        if t<=b:
            u=max(0,min(1,(t-a)/(b-a)))
            if not linear:u=u*u*(3-2*u)
            return v+(w-v)*u
    return keys[-1][1]

def vector(t,keys):return Vector(tuple(curve(t,[(k,v[i]) for k,v in keys]) for i in range(3)))

def make_pose(rig,C,bind):
    inverse=C.inverted();names=list(bind);bones=rig.pose.bones;previous_hand=None
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
    def pose(name,t,edge_hint=None,edge_weight=1.):
        nonlocal previous_hand
        duration=CLIPS[name][0];p=math.pi
        root=Vector((0,0,0));pelvis=Vector((-.035,-.27,-.025));pelvis_a=(13,-10,-4)
        spine_a=(12,5,-4);chest_a=(5,5,0);head_a=(-25,0,3)
        right=Vector((.76,-.64,.36));left=Vector((-.58,-.84,.08))
        yaw=65.;pitch=-26.;turn=0.;cape=0.;root_a=(0,0,0)
        spin_y=0.;flip_x=0.;airborne=False;foot_q=Quaternion((1,0,0,0))
        foot_l=Vector((-.32,.13,.24));foot_r=Vector((.30,.13,-.28))
        if name=='idle':
            breath=math.sin(t*p/30);pelvis.y+=.017*breath;pelvis.x+=.012*breath;chest_a=(5+1.8*breath,5,0)
            left.z+=.014*(math.sin((t-6)*p/30)-math.sin(-6*p/30))
            head_a=(-25+1.1*math.sin(t*p/15),1.2*breath,3);right.y+=.024*math.sin(t*p/30);cape=2*breath+.6*math.sin(t*p/10)
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
            # One continuous ballistic arc, asymmetrical tuck and a fast unfolding strike.
            u=max(0,min(1,(t-18)/19));root.y=12*u*(1-u)
            root.z=curve(t,[(0,0),(15,0),(18,.12),(37,3.20),(60,3.20)],linear=18<=t<=37)
            flip_x=curve(t,[(0,0),(18,0),(21,32),(26,172),(31,307),(35,355),(37,360),(60,360)],linear=21<=t<=35)
            airborne=18<t<37
            crouch=curve(t,[(0,0),(14,.24),(18,.24),(22,-.06),(31,-.04),(37,.29),(42,.25),(51,.10),(60,0)])
            settle=math.sin((t-37)*.85)*math.exp(-(t-37)*.18) if t>37 else 0.
            pelvis.y-=crouch+.04*settle
            bank=7*math.sin(p*u)*math.sin(2*p*u)
            pelvis_a=(13+flip_x,-10+4*math.sin(p*u),-4+bank)
            spine_a=(12+curve(t,[(0,0),(14,9),(24,26),(29,22),(35,2),(38,21),(60,0)])+4*settle,5,-4-bank*.4)
            yaw=curve(t,[(0,65),(15,18),(29,18),(37,-8),(45,-8),(60,65)])
            pitch=curve(t,[(0,-26),(15,80),(27,80),(31,54),(34,8),(37,-23),(45,-23),(60,-26)])
            cape=curve(t,[(0,0),(18,-18),(26,-48),(33,-32),(38,23),(44,-9),(50,8),(60,0)])+3*settle
            foot_l.z+=root.z;foot_r.z+=root.z
            if airborne:
                # Feet are solved below after the pelvis transform has been evaluated.
                foot_q=Quaternion((1,0,0),math.radians(flip_x))
        elif name in ['rush_combo','onslaught']:
            windows=HIT_WINDOWS[name];end=CLIPS[name][0];last=windows[-1][1]
            turn_keys=[(0,0),(8,1),(13,1),(18,-1),(24,-1),(28,-1),(32,1),(38,1),(42,1),(47,-1)]
            if name=='onslaught':turn_keys += [(54,-.3),(59,0),(65,-.4)]
            turn_keys += [(last+7,-.5),(end,0)]
            hip=curve(t+2,turn_keys);twist=curve(t,turn_keys)
            pelvis_a=(13+8*abs(hip),-10+32*hip,-4);spine_a=(12+4*abs(twist),5+17*twist,-4-3*twist)
            chest_a=(5,5+13*twist,-4*twist);pelvis.y-=.13*curve(t,[(0,0),(8,1),(last+7,1),(end,0)])
            drive=[(0,0)];foot_keys={'l':[(0,0)],'r':[(0,0)]}
            for n,(start,finish) in enumerate(windows):
                drive += [(start-4,n*.60),(finish,(n+1)*.60)]
                side='l' if n%2==0 else 'r';prev=foot_keys[side][-1][1]
                foot_keys[side] += [(start-6,prev),(start,(n+1)*.60)]
                foot=foot_l if side=='l' else foot_r
                if start-6<t<start:foot.y+=.18*math.sin((t-start+6)/6*p)
            drive += [(end,len(windows)*.60)];root.z=curve(t,drive)
            for side,foot in [('l',foot_l),('r',foot_r)]:
                foot_keys[side] += [(last+5,foot_keys[side][-1][1]),(end,len(windows)*.60)]
                foot.z+=curve(t,foot_keys[side])
                if last+5<t<end:foot.y+=.11*math.sin((t-last-5)/(end-last-5)*p)
            rest=(.76,-.64,.36);back=(.90,-.06,-.08);cross=(.06,-.39,.82)
            arm_keys=[(0,rest),(8,back),(13,back),(18,cross),(24,(.02,-.15,.72)),(28,(.02,-.15,.72)),(32,(.94,-.31,.60)),(38,back),(42,back),(47,cross)]
            ykeys=[(0,65),(8,128),(13,128),(18,-88),(24,-104),(28,-104),(32,86),(38,128),(42,128),(47,-90)]
            pkeys=[(0,-26),(8,8),(18,-9),(24,-4),(32,8),(38,12),(47,-12)]
            if name=='onslaught':
                arm_keys += [(50,(.72,-.22,.80)),(52,(.95,.08,.35)),(54,(.95,.20,.20)),(59,(.95,.20,.20)),(65,(.33,-.60,.79))]
                ykeys += [(50,-40),(52,15),(54,15),(59,15),(65,-12)];pkeys += [(50,-4),(52,58),(54,100),(59,100),(65,-22)]
                slam=curve(t,[(0,0),(52,0),(59,-1),(66,1),(72,1),(96,0)])
                spine_a=(spine_a[0]+15*slam,spine_a[1],spine_a[2]);pelvis.y-=max(0,slam)*.09
            arm_keys += [(last+7,(.18,-.61,.71)),(end,rest)];ykeys += [(last+7,-95),(end,65)];pkeys += [(last+7,-18),(end,-26)]
            right=vector(t,arm_keys);yaw=curve(t,ykeys,linear=any(a<=t<=b for a,b in windows));pitch=curve(t,pkeys)
            cape=-14*twist
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
        # The grip travels independently of the torso: retract and fold the elbow,
        # lead with the shoulder, extend across the front, then bend to recover.
        # All positions are chest-local; the grip stays outside the head/cuirass.
        shoulder_r=(0,0,0);shoulder_l=(0,0,0)
        rest=(.76,-.64,.36)
        if name in ['slash_01','dash']:
            wind,hold,contact,end=(12,16,21,48) if name=='slash_01' else (10,15,21,44)
            right=vector(t,[(0,rest),(wind,(.90,-.06,-.08)),(hold,(.90,-.06,-.08)),
                (contact-2,(.65,-.17,.73)),(contact,(.06,-.39,.82)),(contact+6,(-.05,-.60,.69)),(end,rest)])
        elif name=='heavy_slash':
            right=vector(t,[(0,rest),(20,(.82,.22,.16)),(27,(.82,.22,.16)),
                (30,(.68,-.08,.72)),(33,(.33,-.60,.79)),(42,(.26,-.65,.70)),(62,rest)])
        elif name=='vault_slam':
            right=vector(t,[(0,rest),(15,(.90,.16,.17)),(22,(.91,.20,.18)),
                (28,(.88,.12,.20)),(33,(.74,-.06,.67)),(38,(.37,-.48,.78)),(46,(.28,-.58,.72)),(60,rest)])
        elif name in ['spin_slash','spiral_combo']:
            arm_keys=[(0,rest),(14,(.91,-.06,-.08)),(18,(.91,-.06,-.08)),
                (24,(.77,-.23,.66)),(31,(.07,-.47,.81)),(39,(.18,-.60,.73)),(56,rest)]
            yaw_keys=[(0,65),(14,128),(18,128),(24,70),(31,-43),(39,-55),(56,65)]
            if name=='spiral_combo':
                arm_keys=[(0,rest),(13,(.91,-.06,-.08)),(17,(.91,-.06,-.08)),
                    (23,(.77,-.23,.66)),(29,(.07,-.47,.81)),(36,(.83,-.17,.02)),
                    (40,(.91,-.06,-.08)),(46,(.77,-.23,.66)),(52,(.07,-.47,.81)),(61,(.18,-.60,.73)),(78,rest)]
                yaw_keys=[(0,65),(13,128),(17,128),(23,70),(29,-43),(36,105),(40,128),(46,70),(52,-43),(61,-55),(78,65)]
            right=vector(t,arm_keys);yaw=curve(t,yaw_keys)+spin_y
        if CLIPS[name][2]:
            raise_arm=max(0,min(1,(right.y+.64)/.86));retract=max(-1,min(1,(.36-right.z)/.44))
            shoulder_r=(-12*raise_arm,20*retract,-16*raise_arm)
            shoulder_l=(4*raise_arm,-7*retract,5*raise_arm)
            left=Vector((-.58-.07*raise_arm,-.84+.17*raise_arm,.08-.14*retract))
        # Head counter-rotates rather than turning away with the attacking chest.
        if name in ['slash_01','heavy_slash','dash','rush_combo','onslaught']:
            head_a=(-25-(pelvis_a[0]+spine_a[0]+chest_a[0]-30)*.75,
                    -(pelvis_a[1]+spine_a[1]+chest_a[1])*.62,3)
        angles={'root':root_a,'pelvis':pelvis_a,'spine':spine_a,'chest':chest_a,'head':head_a,
                'shoulder_r':shoulder_r,'shoulder_l':shoulder_l,
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
            pelvis_m=matrix('pelvis');tuck=curve(t,[(18,0),(23,1),(29,1),(36,0)])
            for foot,s in [(foot_l,-1),(foot_r,1)]:
                local=Vector((s*.29,-1.05+(.47 if s<0 else .34)*tuck,-.22-(.22 if s<0 else .10)*tuck))
                foot[:]=(pelvis_m@local.to_4d()).to_3d()
        for side,local,pole in [('l',left,(-.7,-.2,-.6)),('r',right,(.7,-.1,-.7))]:
            pole=chest.to_quaternion()@Vector(pole)
            target=(chest@local.to_4d()).to_3d()
            wrist,direction=limb('upper_arm_'+side,'forearm_'+side,'hand_'+side,target,pole)
            if side=='l':set_world('hand_l',wrist,matrix('forearm_l').to_quaternion())
            else:
                # The mesh's cutting width is local Y; its flat face normal is X.
                # Orient the edge along the transverse blade velocity, not the forearm.
                min_pitch=math.degrees(math.asin(max(-.9,min(.9,(.43-wrist.y)/3.4135))))
                if not airborne:pitch=max(pitch,min_pitch)
                blade=Vector((math.sin(math.radians(yaw))*math.cos(math.radians(pitch)),math.sin(math.radians(pitch)),math.cos(math.radians(yaw))*math.cos(math.radians(pitch))))
                if name=='vault_slam':blade=Quaternion((1,0,0),math.radians(flip_x))@blade
                yaxis=-direction+blade*direction.dot(blade)
                if yaxis.length<.001:yaxis=blade.cross(Vector((0,0,1)))
                yaxis.normalize()
                if t>0 and previous_hand is not None and yaxis.dot(previous_hand@Vector((0,1,0)))<0:
                    yaxis=-yaxis
                xaxis=yaxis.cross(blade).normalized()
                q=Matrix((xaxis,yaxis,blade)).transposed().to_quaternion()
                if name=='death' and fall>0:
                    flat_edge=blade.cross(Vector((0,1,0))).normalized()
                    if flat_edge.dot(yaxis)<0:flat_edge=-flat_edge
                    flat=Matrix((flat_edge.cross(blade),flat_edge,blade)).transposed().to_quaternion()
                    q=q.slerp(flat,fall)
                if edge_hint is not None:
                    edge=Vector(edge_hint)-blade*Vector(edge_hint).dot(blade)
                    if edge.length>.001:
                        edge.normalize();normal=edge.cross(blade).normalized()
                        choices=[Matrix((normal,edge,blade)).transposed().to_quaternion(),Matrix((-normal,-edge,blade)).transposed().to_quaternion()]
                        reference=previous_hand if t>0 and previous_hand is not None else q
                        cutting=max(choices,key=lambda candidate:abs(candidate.dot(reference)))
                        q=q.slerp(cutting,edge_weight)
                previous_hand=q.copy()
                set_world('hand_r',wrist,q)
        # Feet are authored in clip/world space. Root translation is NOT added twice.
        for side,target in [('l',foot_l),('r',foot_r)]:
            pole=(0,fall,1-fall) if name=='death' else foot_q@Vector((0,0,1))
            end,_=limb('thigh_'+side,'shin_'+side,'foot_'+side,target,pole)
            set_world('foot_'+side,end,foot_q)
        bpy.context.view_layer.update()
    return pose
