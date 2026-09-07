"""Render production actions, with a fixed camera per action (no hidden parts)."""
import bpy,pathlib,sys,json
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:];out=pathlib.Path(args[0]).resolve();out.mkdir(parents=True,exist_ok=True)
s=bpy.context.scene;r=bpy.data.objects['ASHEN_WARDEN_RIG'];s.render.resolution_x=640;s.render.resolution_y=480;s.cycles.samples=2
names=['dash','spin_slash','spiral_combo','vault_slam']
a=json.loads(pathlib.Path(bpy.data.filepath).with_name('warden.json').read_text(encoding='utf8'))
clips=[c for c in a['clips'] if c['name'] in names]
keys={'dash':[10,16,20,30],'spin_slash':[14,21,26,32],'spiral_combo':[18,29,41,52],'vault_slam':[20,29,34,39,44,49]}
only_keys=len(args)==1;first=int(args[1]) if not only_keys else 0;last=int(args[2]) if not only_keys else 10000
labels=[c['name'] for c in clips for _ in range(c['duration'])]
if not only_keys and first==0:(out/'labels.txt').write_text('\n'.join(labels))
index=0
for c in clips:
    name=c['name'];r.animation_data.action=bpy.data.actions[name]
    center=Vector((0,-1.0,2.6 if name=='vault_slam' else 2.0))
    s.camera.location=(-4,-10,4.8);s.camera.rotation_euler=(center-s.camera.location).to_track_quat('-Z','Y').to_euler();s.camera.data.ortho_scale=9.4 if name=='vault_slam' else 7.2
    for t in range(c['duration']):
        render=t in keys[name] if only_keys else first<=index<last
        if render:
            s.frame_set(t+1);s.render.filepath=str(out/(f'{name}-{t:02}.png' if only_keys else f'{index:04}.png'));bpy.ops.render.render(write_still=True)
        index+=1
print('FIERCE_REVIEW_OK',index)
