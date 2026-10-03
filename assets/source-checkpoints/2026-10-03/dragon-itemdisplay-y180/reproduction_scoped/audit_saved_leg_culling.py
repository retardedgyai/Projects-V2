from pathlib import Path
import json,zipfile,io,math,sys
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];O=R/'outputs/actual_game_local_autoplay';A=O/'saved_surface_diagnosis';A.mkdir(exist_ok=True)
sys.path.insert(0,str(Path('C:/Users/xgaiz/Documents/Codex/Projects-V2-worktrees/new-boss-lab/scripts')))
from ice_fang_raster import raster_quad
parts=json.loads((O/'ACTUAL_READY_DISPLAY_METADATA.json').read_text())['parts'];z=zipfile.ZipFile(R/'outputs/reentry_direct_first_step/projects_bundle/pack.zip')
FACE={'north':(3,2,0,1),'south':(6,7,5,4),'down':(4,5,1,0),'up':(2,3,7,6),'west':(2,6,4,0),'east':(7,3,1,5)}
NORMAL={'north':[0,0,-1],'south':[0,0,1],'down':[0,-1,0],'up':[0,1,0],'west':[-1,0,0],'east':[1,0,0]}
def eu(a):
 x,y,z=np.radians(a);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def quat(q):
 x,y,z,w=q;return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
root=np.array([0,1,-4]);camera=np.array([-18,8.62,3]);pitch=np.radians(10);forward=np.array([np.cos(pitch),-np.sin(pitch),0]);right=np.array([0,0,1]);up=np.cross(right,forward);width,height=632,356;f=height/(2*np.tan(np.radians(70)/2))
def project(p):
 v=p-camera;d=v@forward;return np.c_[width/2+(v@right)*f/d,height/2-(v@up)*f/d,d]
textures={};alpha={};quads=[];stats={}
for part in parts:
 if not part['bone'].startswith(('front_','hind_')):continue
 name=part['bone'];item=json.loads(z.read(f'assets/worldseed/models/mobs/dragon_v8_polish_shared_atlas_study.bbmodel/normal/{name}.json'));disp=item['display'][part['context']];sc=np.array(disp['scale']);DR=eu(disp.get('rotation',[0,0,0]));rightQ=quat(part['right']);leftQ=quat(part['left']);ER=eu([part['pitch'],-part['yaw'],0])
 for e in item['elements']:
  lo,hi=e['from'],e['to'];p=np.array([[hi[j] if i&(1<<j) else lo[j] for j in range(3)] for i in range(8)]);r=e.get('rotation',{});C=np.array(r.get('origin',[0,0,0]));rot=eu([r.get(k,0) for k in 'xyz']);p=(p-C)@rot.T+C
  p=((p/16-.5)*sc)@DR.T+np.array(disp['translation'])/16;p=((p@rightQ.T)*part['scale'])@leftQ.T+part['translation'];p=p@ER.T+root
  for face,mat in e['faces'].items():
   uv=mat['uv']
   if abs((uv[2]-uv[0])*(uv[3]-uv[1]))<1e-10:continue
   texname=item['textures'][str(mat['texture']).lstrip('#')]
   if texname not in textures:
    ns,path=texname.split(':');tex=np.array(Image.open(io.BytesIO(z.read(f'assets/{ns}/textures/{path}.png'))).convert('RGBA'));textures[texname]=tex;vals,count=np.unique(tex[:,:,3],return_counts=True);alpha[texname]={str(v):int(c) for v,c in zip(vals,count)}
   points=p[list(FACE[face])];normal=rot@NORMAL[face];normal=DR@(normal/sc);normal=rightQ@normal;normal=leftQ@(normal/part['scale']);normal=ER@normal;normal/=np.linalg.norm(normal)
   facing=float(normal@(camera-points.mean(0)))>0
   stats.setdefault(name,{'front_facing':0,'back_facing':0});stats[name]['front_facing' if facing else 'back_facing']+=1
   quads.append((name,points,textures[texname],uv,facing))
images=[];masks=[]
for label,opaque,cull,candidate in [('GEOMETRY / NO ALPHA OR CULL',True,False,False),('ALPHA / DOUBLE-SIDED',False,False,False),('ALPHA / FRONT-FACE ONLY',False,True,False),('ALPHA / SHIN BACKFACE CANDIDATE',False,True,True)]:
 canvas=np.zeros((height,width,4),dtype=np.uint8);canvas[:]=[22,30,38,255];depth=np.full((height,width),np.inf)
 for name,p,tex,uv,facing in quads:
  if cull and not facing and not(candidate and name=='front_l_shin'):continue
  ink=np.full_like(tex,[180,198,216,255]) if opaque else tex
  raster_quad(canvas,depth,project(p),ink,uv,perspective=True)
 mask=np.isfinite(depth);images.append((label,Image.fromarray(canvas)));masks.append(mask)
counts=[int(m.sum()) for m in masks]
lost=masks[1]&~masks[2];restored=masks[3]&~masks[2]
sheet=Image.new('RGB',(1264,2*404+70),(16,22,30));draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',17)
draw.text((15,8),'OFFLINE READY / ACTUAL DISPLAY METADATA / LEGS ONLY / NOT MINECRAFT GAMEPLAY',font=font,fill='white')
draw.text((15,34),'Same pose. Original keys preserved. Camera assumes FOV70 / spectator eye+1.62. Flat colour, no lighting.',font=font,fill=(221,184,124))
for i,(label,im) in enumerate(images):
 x=i%2*632;y=70+i//2*404;sheet.paste(im,(x,y+42));draw.text((x+10,y+6),f'{label} | pixels {counts[i]}',font=font,fill='white')
sheet.save(A/'READY_LEG_ALPHA_CULLING_FOUR_CONDITIONS_CPU_DIAGNOSTIC.png')
report={'scope':'Saved READY pose, leg groups only, no client/window/server/render daemon started','actual_game_screenshot_comparison_not_pixel_fitted':True,'camera_assumptions':{'player':[-18,7,3],'eye_height':1.62,'yaw':-90,'pitch':10,'vertical_FOV':70},'parts':stats,'texture_alpha_values':alpha,'visible_pixel_counts':dict(zip([x[0] for x in images],counts)),'pixels_removed_by_culling':int(lost.sum()),'pixels_restored_by_single_shin_candidate':int(restored.sum()),'backface_hypothesis_confirmed_in_game':False,'shading_simulated':False,'game_visual_pass':False,'controls_held_constant':['pose','keys','geometry vertices','UVs','texture bytes','camera'],'limitations':['Leg-only silhouette ignores body occlusion','No actual client shader/mipmap/lighting','Camera intrinsic assumptions have not been measured from client internals']}
(A/'SAVED_READY_ALPHA_CULLING_AUDIT.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k not in ['texture_alpha_values','parts']},indent=2))
