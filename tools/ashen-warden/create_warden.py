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
for b in B:
    x,y,z=b['bind'];b['bind']=(x,y*1.18,z)
bind={b['name']:Vector(b['bind']) for b in B}
bpy.ops.object.armature_add(); rig=bpy.context.object; rig.name='ASHEN_WARDEN_RIG'
bpy.ops.object.mode_set(mode='EDIT'); rig.data.edit_bones.remove(rig.data.edit_bones[0])
for b in B:
    e=rig.data.edit_bones.new(b['name']); e.head=cv(b['bind']); e.tail=e.head+Vector((0,.22,0))
    if b['parent']: e.parent=rig.data.edit_bones[b['parent']]
bpy.ops.object.mode_set(mode='OBJECT'); rig.show_in_front=True

colors={'iron':(112,132,163),'edge':(166,185,211),'dark':(39,38,46),'bronze':(101,119,153),'cloth':(92,41,116),'ember':(217,218,255),'bone':(170,175,214)}
materials={}
for name,col in colors.items():
    rng=random.Random(503+list(colors).index(name)); im=bpy.data.images.new(name+'_32px',32,32,alpha=True)
    px=[]
    for y in range(32):
        for x in range(32):
            # Broad hammered panels and intentional 1px chips, never filtered noise.
            # Hand-placed pixel clusters: broken edge highlights and plate bevels.
            cluster=((x//3)*7+(y//5)*11+(x//7)*(y//4))%9
            d=[-23,-14,-8,-3,0,4,10,17,27][cluster]
            if x%16 in (1,2) or y%16==2:d+=16
            if x%16==14 or y%16==14:d-=15
            if name=='dark':d*=.38
            if name=='cloth':d*=.70
            if name=='ember':d=18 if (x//2+y//3)%4==0 else -14
            rgb=[max(0,min(255,c+d))/255 for c in col]
            px.extend([v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in rgb]+[1])
    im.pixels=px; dest=PACK/f'assets/projects/textures/item/warden/{name}.png';dest.parent.mkdir(parents=True,exist_ok=True)
    im.filepath_raw=str(dest);im.file_format='PNG';im.save();im.pack()
    write_json(dest.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False,'mipmaps':[]}})
    m=bpy.data.materials.new(name);m.use_nodes=True;nodes=m.node_tree.nodes;nodes.clear()
    tex=nodes.new('ShaderNodeTexImage');tex.image=im;tex.interpolation='Closest'
    diffuse=nodes.new('ShaderNodeEmission' if name=='ember' else 'ShaderNodeBsdfDiffuse')
    if name!='ember':diffuse.inputs['Roughness'].default_value=1
    output=nodes.new('ShaderNodeOutputMaterial');m.node_tree.links.new(tex.outputs['Color'],diffuse.inputs['Color']);m.node_tree.links.new(diffuse.outputs[0],output.inputs[0]); materials[name]=m

parts=[]
def box(b,center,size,material='iron',rotation=(0,0,0)):
    # Native mesh source and resource-pack cuboid use exactly the same coordinates.
    idx=len(parts); center=Vector(center); size=Vector(size)
    bpy.ops.mesh.primitive_cube_add(size=1, location=cv(bind[b]+center));o=bpy.context.object;o.name=f'{b}_{idx:03}_{material}'
    o.scale=(size.x,size.z,size.y);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    rotation_mc=Matrix.Rotation(math.radians(rotation[2]),4,'Z')@Matrix.Rotation(math.radians(rotation[1]),4,'Y')@Matrix.Rotation(math.radians(rotation[0]),4,'X')
    rotation_bl=C@rotation_mc@C.inverted()
    # Apply per-face pixel UVs at 32 texels per block, capped to one 32px tile.
    mesh=o.data
    for poly in mesh.polygons:
        poly.use_smooth=False
        axes=(0,2) if abs(poly.normal.z)>.5 else ((0,1) if abs(poly.normal.y)>.5 else (2,1))
        w=max(1,min(32,round(size[axes[0]]*32)));h=max(1,min(32,round(size[axes[1]]*32)))
        u=(idx*7)%(33-w);v=(idx*11)%(33-h)
        for li,uv in zip(poly.loop_indices,[(u/32,v/32),((u+w)/32,v/32),((u+w)/32,(v+h)/32),(u/32,(v+h)/32)]): mesh.uv_layers.active.data[li].uv=uv
    for v in mesh.vertices:v.co=(rotation_bl@v.co.to_4d()).to_3d()
    mesh.update()
    o.data.materials.append(materials[material]);g=o.vertex_groups.new(name=b);g.add(list(range(len(o.data.vertices))),1,'REPLACE')
    mod=o.modifiers.new('Rigid bone','ARMATURE');mod.object=rig
    parts.append(dict(bone=b,center=list(center),size=list(size),material=material,rotation=list(rotation)))

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from night_lord_model import build_model
build_model(box)

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
    rot.update(pelvis=(10,0,0),spine=(4,-6,-3),chest=(3,0,0),head=(-12,10,0),upper_arm_l=(-10,0,-10),forearm_l=(-15,0,0),upper_arm_r=(-9,-8,18),forearm_r=(-12,0,0),weapon_root=(44,0,0),cape_01=(-8,0,0),cape_02=(-8,0,0))
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
    # Rest pose carries the sword outside the right leg, with its point near ground.
    m=C.inverted()@rig.pose.bones['weapon_root'].matrix@C
    down=max(-.92,min(-.1,(.14-m.translation.y)/2.64))
    heading=math.radians(65);horizontal=math.sqrt(1-down*down)
    direction=Vector((math.sin(heading)*horizontal,down,math.cos(heading)*horizontal))
    q=Vector((0,0,1)).rotation_difference(direction)
    rig.pose.bones['weapon_root'].matrix=C@(Matrix.Translation(m.translation)@q.to_matrix().to_4x4())@C.inverted()
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
            foot=Vector((x,y,z));axis=foot-hip;dist=min(1.293,axis.length);direction=axis.normalized()
            bend=Vector((0,0,1));bend=(bend-direction*bend.dot(direction)).normalized()
            knee=hip+direction*dist*.5+bend*math.sqrt(max(0,(.55*1.18)**2-(dist*.5)**2))
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

asset=dict(schema=1,name='夜葬の番人',id='ashen_warden',fps=20,modelScale=2,bones=B,parts=parts,clips=exports)
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
    for idx,p in enumerate(parts):
        if p['bone']!=b['name']:continue
        lo=[8+(c-s/2)*8 for c,s in zip(p['center'],p['size'])];hi=[8+(c+s/2)*8 for c,s in zip(p['center'],p['size'])]
        assert all(-16<=a<=32 for a in lo+hi)
        faces={}
        for face in ['north','south','east','west','up','down']:
            axes=(0,1) if face in ['north','south'] else ((2,1) if face in ['east','west'] else (0,2))
            w=max(1,min(32,round(p['size'][axes[0]]*32)));h=max(1,min(32,round(p['size'][axes[1]]*32)))
            u=(idx*7)%(33-w);v=(idx*11)%(33-h);uv=[u/2,v/2,(u+w)/2,(v+h)/2]
            faces[face]={'uv':uv,'texture':'#'+p['material']}
        element={'from':lo,'to':hi,'faces':faces}
        if any(p.get('rotation',[])):element['rotation']={'origin':[8+c*8 for c in p['center']],**dict(zip('xyz',p['rotation']))}
        elements.append(element)
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
world=scene.world;world.color=(.08,.08,.08)
for loc,power,size in [((3,-4,7),850,5),((-4,-1,4),600,4),((0,4,5),850,3)]:
    bpy.ops.object.light_add(type='AREA',location=loc);light=bpy.context.object;light.data.energy=power;light.data.shape='DISK';light.data.size=size;light.rotation_euler=(Vector((0,0,1.3))-light.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(-3,-9,4.4));camera=bpy.context.object;camera.rotation_euler=(Vector((.8,-.25,1.95))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=5.8;scene.camera=camera
scene.render.engine='CYCLES';scene.cycles.samples=16;scene.render.resolution_x=960;scene.render.resolution_y=960;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.image_settings.file_format='PNG'
scene.render.filepath=str(OUT/'warden-preview.png');bpy.ops.render.render(write_still=True)
scene.frame_start=1;scene.frame_end=61
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'ashen-warden.blend'))
print('WARDEN_EXPORT_OK',len(B),'bones',len(parts),'cuboids',len(exports),'clips')
