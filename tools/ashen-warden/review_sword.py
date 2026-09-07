"""Production poses from the weapon side, with an orthogonal grip inspection view."""
import bpy,pathlib,sys,json
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:];out=pathlib.Path(args[0]).resolve();out.mkdir(parents=True,exist_ok=True)
s=bpy.context.scene;r=bpy.data.objects['ASHEN_WARDEN_RIG'];s.render.resolution_x=640;s.render.resolution_y=480;s.cycles.samples=2
a=json.loads(pathlib.Path(bpy.data.filepath).with_name('warden.json').read_text(encoding='utf8'))
clips=[c for c in a['clips'] if c['active']]
keys={'slash_01':[12,18,21,29],'heavy_slash':[20,28,32,40],'dash':[10,17,21,29],'spin_slash':[14,20,25,31],'spiral_combo':[17,29,40,52],'vault_slam':[20,32,41,45]}
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
    center=Vector((0,-1,2.6 if name=='vault_slam' else 2.0))
    s.camera.location=(5,-10,4.8) if view=='weapon' else (10,-.5,3.5)
    s.camera.rotation_euler=(center-s.camera.location).to_track_quat('-Z','Y').to_euler();s.camera.data.ortho_scale=9.4 if name=='vault_slam' else 7.8
    s.render.filepath=str(out/(f'{name}-{t:02}-{view}.png' if key_mode else f'{index:04}.png'));bpy.ops.render.render(write_still=True)
  index+=1
print('SWORD_REVIEW_OK',index)
