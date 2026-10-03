"""Native UV/depth rasterizer from owned cycle02; context is a fixture, not gameplay."""
import math
import numpy as np
from PIL import Image,ImageDraw
START=.5
EXPIRES=6.8
CONTACTS=[2.2,3.2,4.2,5.2]
def actor_position(t):
 if t<START:z=5.15
 elif t<CONTACTS[0]:z=5.15-1.20*(t-START)
 elif t<EXPIRES:z=3.11-.72*(t-CONTACTS[0])
 else:z=3.11-.72*(EXPIRES-CONTACTS[0])-1.2*(t-EXPIRES)
 return np.array([.48,0,z])
def raster(t,data,draws,w=800,h=450,bg='stone',view='fp',actor_visible=True,hurt=False,camera=None,actor_point=None,legs_tag=None):
 eye=np.array(camera['eye'],float) if camera else np.array([0.,1.62,-5.4]) if view=='fp' else np.array([-5.8,4.8,-7.2])
 target=np.array(camera['target'],float) if camera else np.array([0.,.25,.2]);forward=target-eye;forward/=np.linalg.norm(forward)
 right=np.cross([0.,1.,0.],forward);right/=np.linalg.norm(right);up=np.cross(forward,right)
 focal=w/(2*math.tan(math.radians(76)/2));pixels=np.empty((h,w,3),np.uint8);pixels[:]=[70,80,82]
 depth=np.full((h,w),np.inf);owner=np.zeros((h,w),np.uint8)
 def triangle(points,uv,tex,tag,repeat=False):
  rel=points-eye;z=rel@forward
  if min(z)<.05:
   # Clip near-plane crossings rather than dropping a whole floor triangle.
   polygon=[]
   for i in range(3):
    j=(i+1)%3;a,b=points[i],points[j];za,zb=z[i],z[j]
    if za>=.05:polygon.append((a,uv[i]))
    if (za>=.05)!=(zb>=.05):
     ratio=(.05-za)/(zb-za)
     polygon.append((a+(b-a)*ratio,uv[i]+(uv[j]-uv[i])*ratio))
   for i in range(1,len(polygon)-1):
    ids=[0,i,i+1]
    clipped=np.array([polygon[k][0] for k in ids]);clipped_uv=np.array([polygon[k][1] for k in ids])
    # Floating error at the exact clipping plane must not recursively reclip.
    clipped+=forward*1e-8
    triangle(clipped,clipped_uv,tex,tag,repeat)
   return
  screen=np.column_stack([w/2+(rel@right)*focal/z,h/2-(rel@up)*focal/z])
  lo=np.maximum(np.floor(screen.min(axis=0)).astype(int),[0,0]);hi=np.minimum(np.ceil(screen.max(axis=0)).astype(int),[w-1,h-1])
  if np.any(hi<lo):return
  yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1];x=xx+.5;y=yy+.5;a,b,c=screen
  den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(den)<1e-8:return
  wa=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den
  wb=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den;wc=1-wa-wb
  rec=wa/z[0]+wb/z[1]+wc/z[2];zz=1/np.where(abs(rec)>1e-10,rec,1e-10)
  dd=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1];mask=(wa>=-1e-8)&(wb>=-1e-8)&(wc>=-1e-8)&(zz<dd-1e-7)
  if not mask.any():return
  coords=(wa[...,None]*uv[0]/z[0]+wb[...,None]*uv[1]/z[1]+wc[...,None]*uv[2]/z[2])/rec[...,None]
  if repeat:coords%=1
  ix=np.clip((coords[...,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1)
  iy=np.clip((coords[...,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1)
  color=tex[iy,ix];mask &= color[...,3]>=128
  pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1][mask]=color[mask,:3];dd[mask]=zz[mask]
  owner[lo[1]:hi[1]+1,lo[0]:hi[0]+1][mask]=tag
 stone=data['context'][1]['wall'].copy()
 stone[...,:3]=(stone[...,:3]*([.57,.66,.73] if bg=='dark' else [1.,.98,.94])).astype(np.uint8)
 if bg=='light':stone[...,:3]=(stone[...,:3]*.16+212).clip(0,255).astype(np.uint8)
 floor=np.array([[-14,0,-4.7],[-14,0,16],[14,0,16],[14,0,-4.7]],float)
 uv=np.array([[0,0],[0,20.7],[28,20.7],[28,0]],float)
 for ids in [[0,1,2],[0,2,3]]:triangle(floor[ids],uv[ids],stone,0,True)
 def draw_part(name,transform=lambda p,f:p,tag=2):
  model,textures,native=data[name]
  for face in native['faces']:
   points=transform(np.array(face['points_m']),face)
   if points is None:continue
   normal=np.cross(points[1]-points[0],points[2]-points[0])
   if np.dot(normal,eye-points.mean(axis=0))<=0:continue
   tex=textures[face['texture'][1:]]
   if name=='context' and face['element'] in model['fixture_repeat_elements']:
    tex=tex.copy();tex[...,:3]=(tex[...,:3]*(.35 if bg=='dark' else .56)).astype(np.uint8)
   if name=='context' and face['element'] in model['actor_elements']:
    part=model['actor_elements'].index(face['element']);actual_tag=5 if part in [2,3] else 1
    if legs_tag is not None and part in [0,1]:actual_tag=legs_tag
   else:actual_tag=tag
   if name=='context' and face['element'] in model['actor_elements'] and hurt:
    tex=tex.copy();tex[...,:3]=(tex[...,:3]*[1.18,.52,.49]).clip(0,255).astype(np.uint8)
   for ids in [[0,1,2],[0,2,3]]:
    triangle(points[ids],np.array(face['uv'])[ids],tex,actual_tag,
       name=='context' and face['element'] in model['fixture_repeat_elements'])
 def context_transform(p,face):
  if face['element'] not in data['context'][0]['actor_elements']:return p
  if not actor_visible:return None
  i=data['context'][0]['actor_elements'].index(face['element'])
  # Modest alternating limb swing, solely for reading continued movement.
  if i in [0,1,4,5]:
   angle=math.sin(t*7.2)*(1 if i in [0,5] else -1)*.11
   pivot=np.array([0,.75 if i in [0,1] else 1.47,0]);c,s=math.cos(angle),math.sin(angle)
   rot=np.array([[1,0,0],[0,c,-s],[0,s,c]]);p=(p-pivot)@rot.T+pivot
  return p+(actor_position(t) if actor_point is None else np.array(actor_point))

 draw_part('context',context_transform,0)
 for name,transform,tag in draws:draw_part(name,transform,tag)
 im=Image.fromarray(pixels)
 if view=='fp':
  d=ImageDraw.Draw(im);cx=w//2;cy=h//2
  d.line([(cx-4,cy),(cx+4,cy)],fill='#d9e3db');d.line([(cx,cy-4),(cx,cy+4)],fill='#d9e3db')
 return im,owner
