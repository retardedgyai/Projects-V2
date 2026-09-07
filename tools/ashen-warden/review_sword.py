"""Production poses from the weapon side, with an orthogonal grip inspection view."""
import bpy,pathlib,sys,json,math,itertools
import numpy as np
from mathutils import Vector,Quaternion,Euler
args=sys.argv[sys.argv.index('--')+1:];out=pathlib.Path(args[0]).resolve();out.mkdir(parents=True,exist_ok=True)
s=bpy.context.scene;r=bpy.data.objects['ASHEN_WARDEN_RIG'];s.render.resolution_x=640;s.render.resolution_y=480;s.cycles.samples=2
s.render.use_persistent_data=True
devices=bpy.context.preferences.addons['cycles'].preferences
try:
 devices.compute_device_type='OPTIX';devices.get_devices()
 if any(d.type=='OPTIX' for d in devices.devices):
  for d in devices.devices:d.use=d.type=='OPTIX'
  s.cycles.device='GPU'
except (TypeError,RuntimeError):pass
a=json.loads(pathlib.Path(bpy.data.filepath).with_name('warden.json').read_text(encoding='utf8'))
clips=[c for c in a['clips'] if c['active']]
vertices={i:[] for i in range(len(a['bones']))};ids={b['name']:i for i,b in enumerate(a['bones'])}
for part in a['parts']:
 rotation=Euler(tuple(math.radians(v) for v in part['rotation']),'XYZ').to_matrix()
 for signs in itertools.product((-1,1),repeat=3):
  vertices[ids[part['bone']]].append(Vector(part['center'])+rotation@Vector(tuple(x*y/2 for x,y in zip(signs,part['size']))))
vertices={i:np.array(v) for i,v in vertices.items() if v}
framing={}
def camera(c,view):
 key=c['name']+'-'+view
 if key not in framing:
  center=np.array((0.,-1.,2.6 if c['name']=='vault_slam' else 2.))
  position=np.array((5.,-10.,4.8) if view=='weapon' else (10.,-.5,3.5))
  forward=center-position;forward/=np.linalg.norm(forward)
  right=np.cross(forward,(0,0,1));right/=np.linalg.norm(right);up=np.cross(right,forward)
  basis=np.stack((right,up));lo=np.array((1e6,1e6));hi=-lo
  for row in c['samples']:
   for i,v in vertices.items():
    pose=row[i];rot=np.array(Quaternion((pose[6],*pose[3:6])).to_matrix())
    world=v@rot.T+np.array(pose[:3]);world=world[:,[0,2,1]]*np.array((1,-1,1))
    points=(world-center)@basis.T;lo=np.minimum(lo,points.min(axis=0));hi=np.maximum(hi,points.max(axis=0))
  offset=((lo+hi)/2)@basis;center+=offset;position+=offset
  scale=max((hi[0]-lo[0])*1.08,(hi[1]-lo[1])*640/480*1.25)
  framing[key]={'center':center.tolist(),'position':position.tolist(),'scale':float(scale),
    'width_fraction':float((hi[0]-lo[0])/scale),'height_fraction':float((hi[1]-lo[1])/(scale*480/640))}
  assert framing[key]['width_fraction']<=1/1.08+1e-6 and framing[key]['height_fraction']<=.800001
 f=framing[key];s.camera.location=f['position'];s.camera.rotation_euler=(Vector(f['center'])-s.camera.location).to_track_quat('-Z','Y').to_euler();s.camera.data.ortho_scale=f['scale']
keys={'slash_01':[12,18,21,29],'heavy_slash':[20,28,32,40],'dash':[10,17,21,29],'spin_slash':[14,20,25,31],'spiral_combo':[17,29,40,52],'vault_slam':[15,23,28,34,37,42],'rush_combo':[12,18,28,32,42,47],'onslaught':[14,28,42,52,60,65]}
key_mode=len(args)==1;first=int(args[1]) if not key_mode else 0;last=int(args[2]) if not key_mode else 10000
labels=[c['name'] for c in clips for _ in range(c['duration'])]
if not key_mode and first==0:(out/'labels.txt').write_text('\n'.join(labels))
index=0
for c in clips:
 name=c['name'];r.animation_data.action=bpy.data.actions[name]
 for t in range(c['duration']):
  render=t in keys[name] if key_mode else first<=index<last
  if render:
   s.frame_set(t+1)
   views=['weapon','side'] if key_mode else ['weapon']
   for view in views:
    camera(c,view)
    s.render.filepath=str(out/(f'{name}-{t:02}-{view}.png' if key_mode else f'{index:04}.png'));bpy.ops.render.render(write_still=True)
  index+=1
(out/f'camera-framing-{first}.json').write_text(json.dumps(framing,indent=2))
print('SWORD_REVIEW_OK',index)
