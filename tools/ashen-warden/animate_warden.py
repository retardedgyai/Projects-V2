"""Render all authored Blender actions into numbered frames for a review film."""
import bpy,pathlib,sys,json
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:]
out=pathlib.Path(args[0]).resolve();out.mkdir(parents=True,exist_ok=True)
first=int(args[1]) if len(args)>1 else 0;last=int(args[2]) if len(args)>2 else 100000
scene=bpy.context.scene;rig=bpy.data.objects['ASHEN_WARDEN_RIG']
scene.render.resolution_x=640;scene.render.resolution_y=640;scene.cycles.samples=3
scene.camera.location=(-4,-10,4.5);scene.camera.rotation_euler=(Vector((.25,-.8,2.1))-scene.camera.location).to_track_quat('-Z','Y').to_euler();scene.camera.data.ortho_scale=7.2
asset=json.loads(pathlib.Path(bpy.data.filepath).with_name('warden.json').read_text(encoding='utf8'))
clips=[(c['name'],c['duration']) for c in asset['clips']]
index=0;manifest=[name for name,length in clips for _ in range(length)]
if first==0:(out/'labels.txt').write_text('\n'.join(manifest))
for name,length in clips:
    rig.animation_data.action=bpy.data.actions[name]
    for tick in range(length):
        if not first<=index<last:index+=1;continue
        t=tick
        scene.frame_set(t+1)
        # Show the same travel used by the server's walk, so the stance foot stays on the floor.
        if name=='walk':rig.pose.bones['root'].location+=Vector((0,-t*.04,0));bpy.context.view_layer.update()
        scene.render.filepath=str(out/f'{index:04d}.png');bpy.ops.render.render(write_still=True)
        index+=1
print('WARDEN_MOVIE_FRAMES_OK',index)
