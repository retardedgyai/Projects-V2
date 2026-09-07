"""Blender source authoring + deterministic vanilla item-display export.
Run: blender -b --python tools/ashen-warden/create_warden.py -- <output directory>
All meshes are rigid cuboids; each pixel texture is 32x32. No AI image assets.
"""
import bpy, math, json, sys, pathlib, random, zipfile, struct
from mathutils import Vector, Matrix, Quaternion

OUT = pathlib.Path(sys.argv[sys.argv.index('--')+1]).resolve()
OUT.mkdir(parents=True, exist_ok=True)
PACK = OUT / 'pack'
ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / 'server-minestom/src/main/resources/ashen-warden'
RES.mkdir(parents=True, exist_ok=True)
def write_json(p, v):
    p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(v,indent=2),encoding='utf8')

bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
C = Matrix(((1,0,0,0),(0,0,-1,0),(0,1,0,0),(0,0,0,1)))
def cv(v): return (C @ Vector((*v,1))).to_3d()
def mat(pos, angles=(0,0,0)):
    m=Matrix.Translation(Vector(pos))
    for axis,a in zip('XYZ',angles): m=m @ Matrix.Rotation(math.radians(a),4,axis)
    return m

# Parent order is part of the exported contract. MC coordinates: +Y up, +Z forward.
B=[]
def bone(name,parent,pos): B.append(dict(name=name,parent=parent,bind=pos))
bone('root',None,(0,0,0)); bone('pelvis','root',(0,1.25,0))
bone('spine','pelvis',(0,1.53,0)); bone('chest','spine',(0,1.90,0))
bone('neck','chest',(0,2.20,0)); bone('head','neck',(0,2.34,.03))
bone('crest','head',(0,2.64,.0))
for side,x in [('l',-.48),('r',.48)]:
    bone('shoulder_'+side,'chest',(x,2.06,0))
    bone('upper_arm_'+side,'shoulder_'+side,(x*1.22,1.99,0))
    bone('forearm_'+side,'upper_arm_'+side,(x*1.22,1.56,0))
    bone('hand_'+side,'forearm_'+side,(x*1.22,1.15,0))
bone('weapon_root','hand_r',(.5856,1.07,.12))
bone('weapon_tip','weapon_root',(.5856,1.07,2.76))
bone('vfx_blade','weapon_root',(.5856,1.07,1.40))
for side,x in [('l',-.25),('r',.25)]:
    bone('thigh_'+side,'pelvis',(x,1.23,0))
    bone('shin_'+side,'thigh_'+side,(x,.68,0))
    bone('foot_'+side,'shin_'+side,(x,.13,0))
bone('mantle','chest',(0,2.04,-.27))
bone('cape_01','mantle',(0,1.72,-.30))
bone('cape_02','cape_01',(0,1.30,-.34))
bone('tasset_l','pelvis',(-.25,1.23,.09)); bone('tasset_r','pelvis',(.25,1.23,.09))
bone('vfx_chest','chest',(0,1.97,.29)); bone('vfx_ground','root',(0,.06,0))
bind={b['name']:Vector(b['bind']) for b in B}
bpy.ops.object.armature_add(); rig=bpy.context.object; rig.name='ASHEN_WARDEN_RIG'
bpy.ops.object.mode_set(mode='EDIT'); rig.data.edit_bones.remove(rig.data.edit_bones[0])
for b in B:
    e=rig.data.edit_bones.new(b['name']); e.head=cv(b['bind']); e.tail=e.head+Vector((0,.22,0))
    if b['parent']: e.parent=rig.data.edit_bones[b['parent']]
bpy.ops.object.mode_set(mode='OBJECT'); rig.show_in_front=True

colors={'iron':(43,53,58),'edge':(113,131,129),'dark':(22,28,33),'bronze':(131,91,51),'cloth':(44,55,59),'ember':(45,196,173),'bone':(178,167,132)}
materials={}
for name,col in colors.items():
    rng=random.Random(503+list(colors).index(name)); im=bpy.data.images.new(name+'_32px',32,32,alpha=True)
    px=[]
    for y in range(32):
        for x in range(32):
            # Broad hammered panels and intentional 1px chips, never filtered noise.
            d=12 if x in (1,16) or y in (1,16) else (-8 if x in (14,30) or y in (14,30) else rng.choice([0,0,0,4,-4]))
            if name=='ember': d=25 if (x+y)%9<2 else -10
            px.extend([max(0,min(255,c+d))/255 for c in col]+[1])
    im.pixels=px; dest=PACK/f'assets/projects/textures/item/warden/{name}.png';dest.parent.mkdir(parents=True,exist_ok=True)
    im.filepath_raw=str(dest);im.file_format='PNG';im.save();im.pack()
    write_json(dest.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False,'mipmaps':[]}})
    m=bpy.data.materials.new(name);m.use_nodes=True;nodes=m.node_tree.nodes;nodes.clear()
    tex=nodes.new('ShaderNodeTexImage');tex.image=im;tex.interpolation='Closest'
    diffuse=nodes.new('ShaderNodeBsdfDiffuse');diffuse.inputs['Roughness'].default_value=1
    output=nodes.new('ShaderNodeOutputMaterial');m.node_tree.links.new(tex.outputs['Color'],diffuse.inputs['Color']);m.node_tree.links.new(diffuse.outputs[0],output.inputs[0]); materials[name]=m

parts=[]
def box(b,center,size,material='iron'):
    # Native mesh source and resource-pack cuboid use exactly the same coordinates.
    idx=len(parts); center=Vector(center); size=Vector(size)
    bpy.ops.mesh.primitive_cube_add(size=1, location=cv(bind[b]+center));o=bpy.context.object;o.name=f'{b}_{idx:03}_{material}'
    o.scale=(size.x,size.z,size.y);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    # Apply per-face pixel UVs with density close to 16 texels per block.
    mesh=o.data
    for poly in mesh.polygons:
        poly.use_smooth=False
        axes=(0,2) if abs(poly.normal.z)>.5 else ((0,1) if abs(poly.normal.y)>.5 else (2,1))
        w=max(1,round(size[axes[0]]*16));h=max(1,round(size[axes[1]]*16))
        for li,uv in zip(poly.loop_indices,[(0,0),(w/32,0),(w/32,h/32),(0,h/32)]): mesh.uv_layers.active.data[li].uv=uv
    o.data.materials.append(materials[material]);g=o.vertex_groups.new(name=b);g.add(list(range(len(o.data.vertices))),1,'REPLACE')
    mod=o.modifiers.new('Rigid bone','ARMATURE');mod.object=rig
    parts.append(dict(bone=b,center=list(center),size=list(size),material=material))

box('pelvis',(0,0,0),(.66,.28,.43),'dark');box('pelvis',(0,.10,.22),(.72,.10,.09),'bronze')
box('spine',(0,.03,0),(.56,.36,.40));box('chest',(0,0,0),(.88,.49,.47))
box('chest',(0,.09,.255),(.68,.15,.08),'edge');box('chest',(0,-.12,.255),(.51,.16,.08),'dark')
for x,y in [(-.20,.04),(-.10,-.02),(0,-.06),(.10,-.12)]:box('chest',(x,y,.30),(.09,.07,.045),'ember')
box('neck',(0,0,0),(.24,.18,.25),'dark')
box('head',(0,.04,0),(.45,.43,.42));box('head',(0,.01,.23),(.47,.15,.09),'dark')
box('head',(-.11,.02,.287),(.12,.045,.025),'ember');box('head',(.11,.02,.287),(.12,.045,.025),'ember')
box('head',(0,-.14,.25),(.16,.19,.10),'edge');box('head',(0,.245,.0),(.50,.065,.47),'edge')
for x,height in [(-.19,.18),(-.07,.31),(.07,.42),(.19,.23)]:box('crest',(x,height/2,0),(.085,height,.15),'bronze')
for side in ['l','r']:
    sign=-1 if side=='l' else 1
    box('shoulder_'+side,(sign*.06,.03,0),((.51 if side=='l' else .38),.27,.56))
    box('shoulder_'+side,(sign*.10,.18,0),(.43,.085,.57),'edge')
    if side=='l':
        for xx,hh in [(0,.22),(-.17,.34),(-.32,.17)]:box('shoulder_l',(xx,.21+hh/2,0),(.11,hh,.15),'bone')
    box('upper_arm_'+side,(0,-.20,0),(.25,.38,.27),'dark')
    box('forearm_'+side,(0,-.18,.02),(.32,.36,.34))
    box('forearm_'+side,(0,-.03,.20),(.26,.09,.08),'bronze')
    box('hand_'+side,(0,-.05,.02),(.24,.22,.27),'dark')
    box('thigh_'+side,(0,-.26,0),(.30,.48,.32),'dark')
    box('shin_'+side,(0,-.20,.02),(.33,.49,.34))
    box('shin_'+side,(0,.015,.21),(.34,.18,.11),'edge')
    box('foot_'+side,(0,-.055,.13),(.35,.17,.52))
    box('foot_'+side,(0,-.11,.15),(.37,.075,.55),'dark')
    box('tasset_'+side,(0,-.21,.18),(.29,.43,.12),'iron')
    box('tasset_'+side,(0,-.39,.25),(.29,.07,.08),'bronze')
box('mantle',(0,-.12,-.025),(.81,.34,.11),'cloth')
box('cape_01',(0,-.20,-.045),(.72,.45,.10),'cloth')
box('cape_02',(-.10,-.22,-.045),(.48,.46,.10),'cloth')
box('cape_02',(.22,-.12,-.045),(.13,.26,.10),'cloth')
box('weapon_root',(0,0,.03),(.12,.12,.52),'dark')
for z in [-.15,-.03,.09,.21]:box('weapon_root',(0,0,z),(.15,.15,.045),'bronze')
box('weapon_root',(0,0,.36),(.67,.17,.15),'bronze')
box('weapon_root',(0,0,1.43),(.39,.15,2.02),'iron')
box('weapon_root',(-.22,0,1.43),(.08,.105,2.02),'edge')
box('weapon_root',(.20,0,1.15),(.10,.18,1.46),'dark')
box('weapon_root',(.09,0,2.43),(.18,.15,.24),'edge')
box('weapon_root',(-.09,0,2.53),(.18,.12,.22),'edge')
for z in [.64,.93,1.22,1.51,1.80,2.09]:box('weapon_root',(-.03,.089,z),(.10,.025,.13),'ember')

# Author all clips as Blender actions with a complete pose every frame (20Hz).
scene=bpy.context.scene; scene.render.fps=20
clips={'idle':(60,True,None),'walk':(32,True,None),'slash_01':(34,False,[13,18]),'heavy_slash':(48,False,[23,27]),'dash':(40,False,[18,22]),'hurt':(12,False,None),'phase_transition':(64,False,None),'death':(60,False,None)}
def interp(t,keys):
    for (a,v),(b,w) in zip(keys,keys[1:]):
        if t<=b:
            u=max(0,(t-a)/(b-a));u=u*u*(3-2*u);return v+(w-v)*u
    return keys[-1][1]
def pose(name,t):
    rot={};delta={};pi=math.pi
    rot.update(pelvis=(10,0,0),spine=(4,-6,-3),chest=(3,0,0),head=(-12,10,0),upper_arm_l=(-10,0,10),forearm_l=(-25,0,0),upper_arm_r=(-22,-8,-12),forearm_r=(-20,0,0),weapon_root=(44,0,0),cape_01=(-8,0,0),cape_02=(-8,0,0))
    delta['pelvis']=(0,-.09+.015*math.sin(t*pi/30),0)
    twist=0; reach=0;raise_arm=0; lean=0
    if name=='walk':
        rot['chest']=(3,5*math.sin(t*pi/16),0);rot['upper_arm_l']=(-10+9*math.sin(t*pi/16),0,10)
        rot['cape_01']=(-10+5*math.sin((t-3)*pi/16),0,0)
    if name=='slash_01':
        twist=interp(t,[(0,0),(11,-65),(13,-70),(18,85),(23,95),(34,0)])
        raise_arm=interp(t,[(0,0),(12,-10),(18,-5),(24,4),(34,0)])
        reach=interp(t,[(0,0),(11,0),(18,.36),(23,.36),(34,0)])
    if name=='heavy_slash':
        raise_arm=interp(t,[(0,0),(19,-135),(23,-140),(27,25),(34,35),(48,0)])
        twist=interp(t,[(0,0),(20,-22),(27,18),(34,25),(48,0)])
        lean=interp(t,[(0,0),(20,-15),(27,28),(35,30),(48,0)])
        reach=interp(t,[(0,0),(21,-.10),(27,.38),(36,.38),(48,0)])
    if name=='dash':
        raise_arm=interp(t,[(0,0),(13,-70),(17,-85),(22,45),(29,30),(40,0)])
        lean=interp(t,[(0,0),(12,22),(18,5),(24,32),(40,0)])
        twist=interp(t,[(0,0),(15,-35),(22,60),(28,65),(40,0)])
    if name in ['slash_01','heavy_slash','dash']:
        rot['pelvis']=(10+lean*.5,twist*.30,0);rot['spine']=(4+lean*.5,twist*.45,-5)
        rot['chest']=(3,twist*.25,0);rot['upper_arm_r']=(-22+raise_arm,-8,-12)
        rot['forearm_r']=(-20+raise_arm*.12,0,0)
        rot['head']=(-12-lean,-twist*.35,0);delta['root']=(0,0,reach)
        rot['cape_01']=(-8-abs(twist)*.20,0,-twist*.10)
    if name=='hurt':
        w=interp(t,[(0,0),(3,1),(6,.7),(12,0)]);rot['chest']=(-18*w,0,-9*w);rot['head']=(-12-15*w,10,0)
    if name=='phase_transition':
        w=interp(t,[(0,0),(22,1),(28,1),(34,-.6),(46,-.4),(64,0)])
        rot['spine']=(4+32*w,-6,0);rot['head']=(-12+24*w,0,0);rot['upper_arm_l']=(-10-65*max(0,-w),0,30*max(0,-w))
        delta['pelvis']=(0,-.09-.20*max(0,w),0)
    if name=='death':
        w=interp(t,[(0,0),(12,.30),(22,.35),(36,1),(60,1)])
        delta['root']=(0,.40*w,0);rot['root']=(78*w,0,-12*w);rot['head']=(-12+24*w,0,0)
    for b in B:
        p=rig.pose.bones[b['name']];p.rotation_mode='QUATERNION'
        p.location=cv(delta.get(b['name'],(0,0,0)))
        p.rotation_quaternion=(C @ mat((0,0,0),rot.get(b['name'],(0,0,0))) @ C.inverted()).to_quaternion()
    bpy.context.view_layer.update()
    # Wrist follows the authored cutting plane. Keep the edge above the arena floor
    # instead of letting a long rigid blade tunnel under it during recovery.
    if name in ['slash_01','heavy_slash','dash']:
        m=C.inverted()@rig.pose.bones['weapon_root'].matrix@C
        if name=='slash_01':
            pitch=interp(t,[(0,-18),(11,5),(13,0),(18,-8),(25,-12),(34,-18)])
            heading=twist-8;weight=interp(t,[(0,0),(8,1),(25,1),(34,0)])
        elif name=='heavy_slash':
            pitch=interp(t,[(0,-18),(19,120),(23,120),(25,15),(27,-30),(36,-30),(48,-18)])
            heading=twist*.35;weight=interp(t,[(0,0),(12,1),(37,1),(48,0)])
        else:
            pitch=interp(t,[(0,-18),(13,65),(18,55),(20,-8),(22,-30),(30,-25),(40,-18)])
            heading=twist*.10-12;weight=interp(t,[(0,0),(10,1),(30,1),(40,0)])
        pitch=math.radians(pitch);heading=math.radians(heading)
        direction=Vector((math.sin(heading)*math.cos(pitch),math.sin(pitch),math.cos(heading)*math.cos(pitch)))
        if m.translation.y+direction.y*2.64 < .10:
            direction.y=(.10-m.translation.y)/2.64
            horizontal=math.sqrt(max(0,1-direction.y**2));xz=Vector((direction.x,0,direction.z)).normalized()
            direction.x=xz.x*horizontal;direction.z=xz.z*horizontal
        q=Vector((0,0,1)).rotation_difference(direction)
        q=m.to_quaternion().slerp(q,weight)
        rig.pose.bones['weapon_root'].matrix=C@(Matrix.Translation(m.translation)@q.to_matrix().to_4x4())@C.inverted()
        bpy.context.view_layer.update()
    if name=='death':
        m=C.inverted()@rig.pose.bones['weapon_root'].matrix@C
        weight=interp(t,[(0,0),(16,.2),(36,1),(60,1)])
        q=m.to_quaternion().slerp(Quaternion((0,1,0),math.radians(65)),weight)
        p=m.translation;p.y=max(.14,p.y)
        rig.pose.bones['weapon_root'].matrix=C@(Matrix.Translation(p)@q.to_matrix().to_4x4())@C.inverted()
        bpy.context.view_layer.update()
    # Two-bone IK with stance lock. Geometry and animation both remain rigid.
    if name!='death':
        for side,x,off in [('l',-.25,0),('r',.25,16)]:
            z=.17 if side=='l' else -.17;y=.13
            if name=='walk':
                u=(t+off)%32
                if u<20:z=.40-u*.04
                else:z=-.40+(u-20)/12*.8;y+=.17*math.sin((u-20)/12*pi)
            hip=(C.inverted()@rig.pose.bones['thigh_'+side].matrix@C).translation
            foot=Vector((x,y,z));axis=foot-hip;dist=min(1.095,axis.length);direction=axis.normalized()
            bend=Vector((0,0,1));bend=(bend-direction*bend.dot(direction)).normalized()
            knee=hip+direction*dist*.5+bend*math.sqrt(max(0,.55**2-(dist*.5)**2))
            for bn,p0,p1 in [('thigh_'+side,hip,knee),('shin_'+side,knee,foot)]:
                q=Vector((0,-1,0)).rotation_difference((p1-p0).normalized());m=Matrix.Translation(p0)@q.to_matrix().to_4x4()
                rig.pose.bones[bn].matrix=C@m@C.inverted();bpy.context.view_layer.update()
            rig.pose.bones['foot_'+side].matrix=C@Matrix.Translation(foot)@C.inverted();bpy.context.view_layer.update()

exports=[]
for name,(duration,loop,active) in clips.items():
    rig.animation_data_create(); action=bpy.data.actions.new(name);rig.animation_data.action=action;action.use_fake_user=True
    samples=[]
    for tick in range(duration+1):
        scene.frame_set(tick+1);pose(name,tick)
        for p in rig.pose.bones:
            p.keyframe_insert('location',frame=tick+1);p.keyframe_insert('rotation_quaternion',frame=tick+1)
        row=[]
        for b in B:
            m=C.inverted()@rig.pose.bones[b['name']].matrix@C;pos=m.translation;q=m.to_quaternion()
            row.append([*pos,q.x,q.y,q.z,q.w])
        samples.append(row)
    exports.append(dict(name=name,duration=duration,loop=loop,active=active,samples=samples))

asset=dict(schema=1,name='灰燼の番人',id='ashen_warden',fps=20,modelScale=2,bones=B,parts=parts,clips=exports)
write_json(OUT/'warden.json',asset)
# Binary has no runtime JSON dependency. Big-endian floats, explicit version/counts.
with (RES/'warden.bin').open('wb') as f:
    def integer(n):f.write(struct.pack('>i',n))
    def string(s):v=s.encode('utf8');f.write(struct.pack('>H',len(v)));f.write(v)
    integer(0x41575231);integer(len(B))
    for b in B:string(b['name']);integer(next((i for i,p in enumerate(B) if p['name']==b['parent']),-1));integer(int(any(p['bone']==b['name'] for p in parts)))
    integer(len(exports))
    for clip in exports:
        string(clip['name']);integer(clip['duration']);integer(int(clip['loop']));integer((clip['active']or[-1,-1])[0]);integer((clip['active']or[-1,-1])[1])
        for row in clip['samples']:
            for v in row:f.write(struct.pack('>7f',*v))
for b in B:
    elements=[]
    for p in parts:
        if p['bone']!=b['name']:continue
        lo=[8+(c-s/2)*8 for c,s in zip(p['center'],p['size'])];hi=[8+(c+s/2)*8 for c,s in zip(p['center'],p['size'])]
        assert all(-16<=a<=32 for a in lo+hi)
        faces={}
        for face in ['north','south','east','west','up','down']:
            axes=(0,1) if face in ['north','south'] else ((2,1) if face in ['east','west'] else (0,2))
            uv=[0,0,max(1,round(p['size'][axes[0]]*16))/2,max(1,round(p['size'][axes[1]]*16))/2]
            faces[face]={'uv':uv,'texture':'#'+p['material']}
        elements.append({'from':lo,'to':hi,'faces':faces})
    if elements:
        name=b['name'];write_json(PACK/f'assets/projects/models/item/warden/{name}.json',{'textures':{k:f'projects:item/warden/{k}' for k in colors},'elements':elements,'gui_light':'front'})
        write_json(PACK/f'assets/projects/items/warden/{name}.json',{'model':{'type':'minecraft:model','model':f'projects:item/warden/{name}'}})
write_json(PACK/'pack.mcmeta',{'pack':{'min_format':[88,0],'max_format':[88,0],'description':'ProjectS | Ashen Warden | Minecraft 26.2'}})
with zipfile.ZipFile(RES/'warden-pack.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(PACK.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(PACK).as_posix())
(OUT/'warden-pack.zip').write_bytes((RES/'warden-pack.zip').read_bytes())

# Actual Blender model preview, using the production geometry and textures.
rig.animation_data.action=bpy.data.actions['idle'];scene.frame_set(1)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.025));floor=bpy.context.object;floor.name='PREVIEW_FLOOR'
m=bpy.data.materials.new('Preview floor');m.diffuse_color=(.06,.075,.08,1);floor.data.materials.append(m)
world=scene.world;world.color=(.15,.15,.15)
for loc,power,size in [((3,-4,7),1200,5),((-4,-1,4),850,4),((0,4,5),1000,3)]:
    bpy.ops.object.light_add(type='AREA',location=loc);light=bpy.context.object;light.data.energy=power;light.data.shape='DISK';light.data.size=size;light.rotation_euler=(Vector((0,0,1.3))-light.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(5,-7,4));camera=bpy.context.object;camera.rotation_euler=(Vector((0,-.45,1.25))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=4.4;scene.camera=camera
scene.render.engine='CYCLES';scene.cycles.samples=16;scene.render.resolution_x=960;scene.render.resolution_y=960;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.image_settings.file_format='PNG'
scene.render.filepath=str(OUT/'warden-preview.png');bpy.ops.render.render(write_still=True)
scene.frame_start=1;scene.frame_end=61
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'ashen-warden.blend'))
print('WARDEN_EXPORT_OK',len(B),'bones',len(parts),'cuboids',len(exports),'clips')
