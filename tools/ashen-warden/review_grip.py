"""Actual Blender grip closeup and one slash, without a presentation substitute."""
import bpy,pathlib,sys
from mathutils import Vector
out=pathlib.Path(sys.argv[sys.argv.index('--')+1]).resolve();out.mkdir(parents=True,exist_ok=True)
scene=bpy.context.scene;rig=bpy.data.objects['ASHEN_WARDEN_RIG'];camera=scene.camera
scene.render.resolution_x=640;scene.render.resolution_y=640;scene.cycles.samples=8
rig.animation_data.action=bpy.data.actions['idle'];scene.frame_set(1)
target=rig.pose.bones['hand_r'].matrix.translation
camera.location=target+Vector((-1.6,-2.0,.6));camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.ortho_scale=.95;scene.render.filepath=str(out/'grip-closeup.png');bpy.ops.render.render(write_still=True)
camera.location=(-3,-9,4.4);camera.rotation_euler=(Vector((.3,-.2,2.2))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.ortho_scale=7.2
scene.render.resolution_x=480;scene.render.resolution_y=480;scene.cycles.samples=3
frames=out/'frames';frames.mkdir(exist_ok=True)
for tick in range(35):
    rig.animation_data.action=bpy.data.actions['slash_01'];scene.frame_set(tick+1)
    scene.render.filepath=str(frames/f'{tick:03d}.png');bpy.ops.render.render(write_still=True)
print('GRIP_REVIEW_OK')
