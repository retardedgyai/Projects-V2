from pathlib import Path
import zipfile,json
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1]
source=(R/'work/audit_saved_leg_culling.py').read_text()
prefix=source.split('root=np.array')[0]
exec(compile(prefix,'saved_culling_helpers','exec'))
root=np.array([0,1,-4]);camera=np.array([-18,8.62,3]);pitch=np.radians(10);forward=np.array([np.cos(pitch),-np.sin(pitch),0]);right=np.array([0,0,1]);up=np.cross(right,forward);width,height=632,356;f=height/(2*np.tan(np.radians(70)/2));J=np.diag([-1,1,-1]);textures={};alpha={}
def project(p):
 v=p-camera;d=v@forward;return np.c_[width/2+(v@right)*f/d,height/2-(v@up)*f/d,d]
body=source.split('for part in parts:')[1].split('images=[];masks=[]')[0]
body='for part in parts:'+body
body=body.replace(" if not part['bone'].startswith(('front_','hind_')):continue\n",'')
body=body.replace('z.read(','pack.read(')
body=body.replace('p=((p@rightQ.T)','p=p@J.T if apply_turn else p;p=((p@rightQ.T)')
body=body.replace('normal=DR@(normal/sc);normal=rightQ@normal','normal=DR@(normal/sc);normal=J@normal if apply_turn else normal;normal=rightQ@normal')
function='def make_quads(pack,apply_turn):\n    quads=[];stats={}\n'+'\n'.join('    '+line for line in body.splitlines())+'\n    return quads\n'
exec(compile(function,'source_surface_math_with_primary_client_turn','exec'))
old=zipfile.ZipFile(R/'outputs/reentry_direct_first_step/projects_bundle/pack.zip')
new=zipfile.ZipFile(O/'item_display_turn_compensation_candidate/projects_bundle/pack.zip')
sets=[make_quads(old,False),make_quads(old,True),make_quads(new,True)]
gap_old=max(float(np.linalg.norm(a[1]-b[1],axis=1).max()) for a,b in zip(sets[0],sets[1]));gap_new=max(float(np.linalg.norm(a[1]-b[1],axis=1).max()) for a,b in zip(sets[0],sets[2]));assert gap_new<1e-8
labels=['LEGACY CPU (MISSED CLIENT Y180)','OLD PACK + VANILLA Y180','COMPENSATED PACK + VANILLA Y180']
rendered=[];counts=[]
for label,quads in zip(labels,sets):
 canvas=np.zeros((height,width,4),dtype=np.uint8);canvas[:]=[22,30,38,255];depth=np.full((height,width),np.inf)
 for name,p,tex,uv,facing in quads:
  if not facing:continue
  raster_quad(canvas,depth,project(p),tex,uv,perspective=True)
 rendered.append(Image.fromarray(canvas));counts.append(int(np.isfinite(depth).sum()))
assert np.array_equal(np.array(rendered[0]),np.array(rendered[2]))
sheet=Image.new('RGB',(1264,2*402+75),(16,22,30));draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',17)
draw.text((14,8),'PRIMARY VANILLA ITEMDISPLAY Y180 FOUND / CURRENT PACK HAS NO COMPENSATION',font=font,fill='white')
draw.text((14,35),'3 CPU poses share keys, texture and camera. Candidate is NOT yet game-tested. Actual capture bottom right.',font=font,fill=(235,188,104))
images=rendered+[Image.open(O/'ACTUAL_GAME_01_READY.png').resize((width,height),Image.Resampling.NEAREST)]
for i,im in enumerate(images):
 x=i%2*width;y=75+i//2*402;sheet.paste(im,(x,y+40));draw.text((x+10,y+7),labels[i] if i<3 else 'ACTUAL VANILLA CAPTURE / OLD PACK',font=font,fill='white')
sheet.save(A/'ITEMDISPLAY_Y180_PRIMARY_CAUSE_CPU_AND_ACTUAL_READY.png')
report={'primary_local_client_class':'Vanilla26.2 DisplayRenderer$ItemDisplayRenderer.submitInner','primary_call':'Axis.YP.rotation(3.1415927f) before ItemStackRenderState.submit','previous_CPU_projection_omitted_this_transform':True,'full_model_visible_quads':len(sets[0]),'old_pack_max_vertex_displacement_due_to_missing_turn_blocks':gap_old,'compensated_pack_max_vertex_error_against_original_intended_geometry_blocks':gap_new,'legacy_intended_and_compensated_client_math_RGBA_identical':True,'visible_pixel_counts':dict(zip(labels,counts)),'real_capture_comparison':'Actual READY shown with same assumed camera; no pixel fitting; GPU shading not reproduced','client_tested_candidate':False,'game_visual_pass':False,'bones_animation_keys_vertices_UV_texture_bytes_unchanged':True,'additional_entity_count':0,'surface_culling_candidate_not_promoted':True}
(A/'PRIMARY_ITEMDISPLAY_Y180_CAUSE_AND_COMPENSATION_AUDIT.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
