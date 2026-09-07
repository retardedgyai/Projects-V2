"""Render the real .blend actions for visual acceptance, no generative images."""
import bpy, pathlib,sys
from mathutils import Vector
out=pathlib.Path(sys.argv[sys.argv.index('--')+1]).resolve();out.mkdir(parents=True,exist_ok=True)
scene=bpy.context.scene;rig=bpy.data.objects['ASHEN_WARDEN_RIG']
scene.render.resolution_x=480;scene.render.resolution_y=480;scene.cycles.samples=4
scene.camera.location=(5,-8,4.4);scene.camera.rotation_euler=(Vector((0,-.2,1.9))-scene.camera.location).to_track_quat('-Z','Y').to_euler();scene.camera.data.ortho_scale=5.8
poses=[('idle',1),('walk',9),('slash_01',12),('slash_01',16),('slash_01',24),('heavy_slash',23),('heavy_slash',26),('heavy_slash',35),('dash',19),('hurt',4),('phase_transition',35),('death',52)]
for idx,(name,frame) in enumerate(poses):
    rig.animation_data.action=bpy.data.actions[name];scene.frame_set(frame)
    scene.render.filepath=str(out/f'{idx:02d}-{name}-{frame}.png');bpy.ops.render.render(write_still=True)
print('WARDEN_STORYBOARD_OK')
