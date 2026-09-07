"""Render all authored Blender actions into numbered frames for a review film."""
import bpy,pathlib,sys
from mathutils import Vector
out=pathlib.Path(sys.argv[sys.argv.index('--')+1]).resolve();out.mkdir(parents=True,exist_ok=True)
scene=bpy.context.scene;rig=bpy.data.objects['ASHEN_WARDEN_RIG']
scene.render.resolution_x=480;scene.render.resolution_y=480;scene.cycles.samples=3
scene.camera.location=(5,-8,5);scene.camera.rotation_euler=(Vector((0,-.2,2.0))-scene.camera.location).to_track_quat('-Z','Y').to_euler();scene.camera.data.ortho_scale=6.6
clips=[('idle',40),('walk',64),('slash_01',34),('heavy_slash',48),('dash',40),('hurt',12),('phase_transition',64),('death',60)]
index=0;manifest=[]
for name,length in clips:
    rig.animation_data.action=bpy.data.actions[name]
    for tick in range(length):
        t=tick%32 if name=='walk' else tick
        scene.frame_set(t+1);scene.render.filepath=str(out/f'{index:04d}.png');bpy.ops.render.render(write_still=True)
        manifest.append(name);index+=1
(out/'labels.txt').write_text('\n'.join(manifest))
print('WARDEN_MOVIE_FRAMES_OK',index)
