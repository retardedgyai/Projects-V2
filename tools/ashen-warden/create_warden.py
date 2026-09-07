"""Blender source authoring + deterministic vanilla item-display export.
Run: blender -b --python tools/ashen-warden/create_warden.py -- <output directory>
All meshes are rigid cuboids; each pixel texture is 32x32. No AI image assets.
"""
import bpy, math, json, sys, pathlib, random, zipfile, struct
from mathutils import Vector, Matrix, Quaternion
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from night_lord_palette import PALETTES,pixel,region
MODEL_PIXELS=6.0
BLADE_TIP=3.4135

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
for b in B:
    if b['name'] in ['weapon_root','weapon_tip','vfx_blade']:
        length={'weapon_root':0,'weapon_tip':BLADE_TIP,'vfx_blade':1.92}[b['name']]
        b['bind']=(.5856,1.15*1.18-.08,-.06+length)
bind={b['name']:Vector(b['bind']) for b in B}
bpy.ops.object.armature_add(); rig=bpy.context.object; rig.name='ASHEN_WARDEN_RIG'
bpy.ops.object.mode_set(mode='EDIT'); rig.data.edit_bones.remove(rig.data.edit_bones[0])
for b in B:
    e=rig.data.edit_bones.new(b['name']); e.head=cv(b['bind']); e.tail=e.head+Vector((0,.22,0))
    if b['parent']: e.parent=rig.data.edit_bones[b['parent']]
bpy.ops.object.mode_set(mode='OBJECT'); rig.show_in_front=True

colors={name:palette[2] for name,palette in PALETTES.items()}
materials={}
for name,col in colors.items():
    rng=random.Random(503+list(colors).index(name)); im=bpy.data.images.new(name+'_32px',32,32,alpha=True)
    px=[]
    for y in range(32):
        for x in range(32):
            rgb=[c/255 for c in pixel(name,x,y)]
            px.extend(rgb+[1])
    im.colorspace_settings.name='sRGB';im.pixels=px; dest=PACK/f'assets/projects/textures/item/warden/{name}.png';dest.parent.mkdir(parents=True,exist_ok=True)
    im.filepath_raw=str(dest);im.file_format='PNG';im.save();im.pack()
    write_json(dest.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False,'mipmaps':[]}})
    m=bpy.data.materials.new(name);m.use_nodes=True;nodes=m.node_tree.nodes;nodes.clear()
    tex=nodes.new('ShaderNodeTexImage');tex.image=im;tex.interpolation='Closest'
    # Reference-like painted lighting: inspect the actual texel colors without a studio metal sheen.
    diffuse=nodes.new('ShaderNodeEmission')
    output=nodes.new('ShaderNodeOutputMaterial');m.node_tree.links.new(tex.outputs['Color'],diffuse.inputs['Color']);m.node_tree.links.new(diffuse.outputs[0],output.inputs[0]); materials[name]=m

parts=[]
def box(b,center,size,material='iron',rotation=(0,0,0),front_uv=None):
    # Native mesh source and resource-pack cuboid use exactly the same coordinates.
    idx=len(parts); center=Vector(center); size=Vector(size)
    mesh=bpy.data.meshes.new(f'part_{idx}')
    vertices=[(x*size.x/2,y*size.z/2,z*size.y/2) for x,y,z in [(-1,-1,-1),(-1,-1,1),(-1,1,-1),(-1,1,1),(1,-1,-1),(1,-1,1),(1,1,-1),(1,1,1)]]
    mesh.from_pydata(vertices,[],[(0,4,6,2),(1,3,7,5),(0,1,5,4),(2,6,7,3),(0,2,3,1),(4,5,7,6)])
    mesh.update();mesh.uv_layers.new(name='PixelUV')
    o=bpy.data.objects.new(f'{b}_{idx:04}_{material}',mesh);bpy.context.collection.objects.link(o);o.location=cv(bind[b]+center)
    rotation_mc=Matrix.Rotation(math.radians(rotation[2]),4,'Z')@Matrix.Rotation(math.radians(rotation[1]),4,'Y')@Matrix.Rotation(math.radians(rotation[0]),4,'X')
    rotation_bl=C@rotation_mc@C.inverted()
    # Apply per-face pixel UVs at 32 texels per block, capped to one 32px tile.
    mesh=o.data
    for poly in mesh.polygons:
        poly.use_smooth=False
        axes=(0,2) if abs(poly.normal.z)>.5 else ((0,1) if abs(poly.normal.y)>.5 else (2,1))
        uv_region=region(center,size,axes,front_uv if abs(poly.normal.y)>.5 else None)
        for li in poly.loop_indices:
            p=(C.inverted()@mesh.vertices[mesh.loops[li].vertex_index].co.to_4d()).to_3d()
            uu=p[axes[0]]/size[axes[0]]+.5;vv=p[axes[1]]/size[axes[1]]+.5
            mesh.uv_layers.active.data[li].uv=((uv_region[0]+uu*(uv_region[2]-uv_region[0]))/32,(uv_region[1]+vv*(uv_region[3]-uv_region[1]))/32)
    for v in mesh.vertices:v.co=(rotation_bl@v.co.to_4d()).to_3d()
    mesh.update()
    o.data.materials.append(materials[material]);g=o.vertex_groups.new(name=b);g.add(list(range(len(o.data.vertices))),1,'REPLACE')
    mod=o.modifiers.new('Rigid bone','ARMATURE');mod.object=rig
    parts.append(dict(bone=b,center=list(center),size=list(size),material=material,rotation=list(rotation),front_uv=front_uv))

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from night_lord_geometry import build_model
build_model(box)
for b in B:
    objects=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.vertex_groups.get(b['name'])]
    if len(objects)>1:
        bpy.ops.object.select_all(action='DESELECT')
        for o in objects:o.select_set(True)
        bpy.context.view_layer.objects.active=objects[0];bpy.ops.object.join()
        objects[0].name=b['name']+'_MESH'

# Author all clips as Blender actions with a complete pose every frame (20Hz).
scene=bpy.context.scene; scene.render.fps=20
from night_lord_motion import CLIPS, HIT_WINDOWS, make_pose
clips=CLIPS
pose=make_pose(rig,C,bind)

exports=[]
for name,(duration,loop,active) in clips.items():
    rig.animation_data_create(); action=bpy.data.actions.new(name);rig.animation_data.action=action;action.use_fake_user=True
    # Sample the authored blade travel before baking the final edge orientation.
    travel=[];raw_rotations=[];rest_rotation=None
    for tick in range(duration+1):
        pose(name,tick)
        m=C.inverted()@rig.pose.bones['weapon_root'].matrix@C
        hand=C.inverted()@rig.pose.bones['hand_r'].matrix@C
        rotation=m.to_quaternion()
        if tick==0:rest_rotation=rotation.copy()
        axis=rotation@Vector((0,0,1))
        # Parallel transport the roll frame. The bent forearm's projected basis can
        # flip near a straight elbow; it must not flip the wrist between two cuts.
        if raw_rotations:
            previous=raw_rotations[-1]
            rotation=(previous@Vector((0,0,1))).rotation_difference(axis)@previous
        raw_rotations.append(rotation)
        travel.append(hand.translation+axis*1.6)
    windows=HIT_WINDOWS.get(name,[active] if active else [])
    continuous_roll=name in ['rush_combo','onslaught']
    edge_angles={0:0.} if continuous_roll else {}
    previous_angle=0. if continuous_roll else None
    for start,end in windows:
        for tick in range(start,end+1):
            local=raw_rotations[tick].conjugated()@(travel[tick]-travel[max(0,tick-1)])
            angle=math.atan2(local.x,local.y)
            if previous_angle is not None:
                angle=previous_angle+(angle-previous_angle+math.pi/2)%math.pi-math.pi/2
            edge_angles[tick]=angle;previous_angle=angle
    if continuous_roll:
        local=raw_rotations[-1].conjugated()@(rest_rotation@Vector((0,1,0)))
        angle=math.atan2(local.x,local.y)
        edge_angles[duration]=previous_angle+(angle-previous_angle+math.pi)%(2*math.pi)-math.pi
    samples=[]
    for tick in range(duration+1):
        edge=None;weight=0.
        if active:
            # Pre-align during anticipation; no last-moment 90-degree wrist flip.
            before=max((k for k in edge_angles if k<=tick),default=min(edge_angles))
            after=min((k for k in edge_angles if k>=tick),default=max(edge_angles))
            u=0. if before==after else (tick-before)/(after-before)
            u=u*u*(3-2*u)
            angle=edge_angles[before]+(edge_angles[after]-edge_angles[before])*u
            edge=raw_rotations[tick]@Vector((math.sin(angle),math.cos(angle),0))
            weight=min(1.,tick/max(1,active[0]-5),(duration-tick)/max(1,duration-active[1]-6))
            weight=weight*weight*(3-2*weight)
            if continuous_roll:weight=1.
        scene.frame_set(tick+1);pose(name,tick,edge,weight)
        for p in rig.pose.bones:
            p.keyframe_insert('location',frame=tick+1);p.keyframe_insert('rotation_quaternion',frame=tick+1)
        row=[]
        for b in B:
            m=C.inverted()@rig.pose.bones[b['name']].matrix@C;pos=m.translation;q=m.to_quaternion()
            row.append([*pos,q.x,q.y,q.z,q.w])
        samples.append(row)
    windows=HIT_WINDOWS.get(name,[active] if active else [])
    exports.append(dict(name=name,duration=duration,loop=loop,active=active,windows=windows,samples=samples))

asset=dict(schema=2,name='夜葬の番人',id='ashen_warden',fps=20,modelScale=16/MODEL_PIXELS,bladeTip=BLADE_TIP,bones=B,parts=parts,clips=exports)
write_json(OUT/'warden.json',asset)
# Binary has no runtime JSON dependency. Big-endian floats, explicit version/counts.
with (RES/'warden.bin').open('wb') as f:
    def integer(n):f.write(struct.pack('>i',n))
    def string(s):v=s.encode('utf8');f.write(struct.pack('>H',len(v)));f.write(v)
    integer(0x41575232);integer(len(B))
    for b in B:string(b['name']);integer(next((i for i,p in enumerate(B) if p['name']==b['parent']),-1));integer(int(any(p['bone']==b['name'] for p in parts)))
    integer(len(exports))
    for clip in exports:
        string(clip['name']);integer(clip['duration']);integer(int(clip['loop']));integer(len(clip['windows']))
        for start,end in clip['windows']:integer(start);integer(end)
        for row in clip['samples']:
            for v in row:f.write(struct.pack('>7f',*v))
for b in B:
    elements=[]
    for idx,p in enumerate(parts):
        if p['bone']!=b['name']:continue
        lo=[8+(c-s/2)*MODEL_PIXELS for c,s in zip(p['center'],p['size'])];hi=[8+(c+s/2)*MODEL_PIXELS for c,s in zip(p['center'],p['size'])]
        assert all(-16<=a<=32 for a in lo+hi)
        faces={}
        for face in ['north','south','east','west','up','down']:
            axes=(0,1) if face in ['north','south'] else ((2,1) if face in ['east','west'] else (0,2))
            uv=[v/2 for v in region(p['center'],p['size'],axes,p.get('front_uv') if face in ['north','south'] else None)]
            faces[face]={'uv':uv,'texture':'#'+p['material']}
        element={'from':lo,'to':hi,'faces':faces,'shade':False,'light_emission':15}
        if any(p.get('rotation',[])):element['rotation']={'origin':[8+c*MODEL_PIXELS for c in p['center']],**dict(zip('xyz',p['rotation']))}
        elements.append(element)
    if elements:
        name=b['name'];write_json(PACK/f'assets/projects/models/item/warden/{name}.json',{'textures':{k:f'projects:item/warden/{k}' for k in colors},'elements':elements,'gui_light':'front','ambientocclusion':False})
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
bpy.ops.object.camera_add(location=(-3,-9,4.4));camera=bpy.context.object;camera.rotation_euler=(Vector((.8,-.25,1.95))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=7.2;scene.camera=camera
scene.render.engine='CYCLES';scene.cycles.samples=16;scene.render.resolution_x=960;scene.render.resolution_y=960;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.image_settings.file_format='PNG'
scene.render.filepath=str(OUT/'warden-preview.png');bpy.ops.render.render(write_still=True)
scene.frame_start=1;scene.frame_end=61
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'ashen-warden.blend'))
print('WARDEN_EXPORT_OK',len(B),'bones',len(parts),'cuboids',len(exports),'clips')
