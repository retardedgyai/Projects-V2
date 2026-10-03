"""08 whole-effect native projection; controlled context/contacts, never gameplay."""
import functools,math
import numpy as np
from fixed07 import assets as baseline_assets,load,field_transform,PLACEMENTS,CONTACTS,START,ACTIVE,EXPIRES,CLEAR,render as render07,component_faces,MESHES,rotation,ease
from raster_native import raster,actor_position
from field_math08 import texture
@functools.lru_cache(None)
def assets():return {**baseline_assets(),**{name:load(name) for name in ['field-08','rim-08','crest-left-08','crest-right-08','crest-rear-08','impact-cut-08']}}

CRESTS=[('crest-left-08',[-1.95,.04,.10],79,0),('crest-right-08',[1.90,.04,.45],99,.035),('crest-rear-08',[-1.19,.04,2.17],-44,.065)]
CONTACT_LENGTH=.42

def crest_pose(anchor,yaw,delay,t):
 age=t-(ACTIVE+.04+delay)
 def transform(points,face):
  if not 0<=age<.34:return None
  p=points.copy();q=ease(age/.07);end=np.clip((age-.20)/.14,0,1)
  p[:,0]*=.30+.70*q
  p[:,1]+=.16*math.sin(age/.34*math.pi)-1.1*end*end
  return p@rotation(ay=math.radians(yaw)).T+np.array(anchor)
 return transform

def impact_pose(index,hit,t):
 # Fixed-scale existing thick pieces open outward; the central toe gap stays open.
 part=MESHES['primary-remake'][3+index];origin=np.array(part['origin']);scale=.95 if index==0 else 1.02
 age=t-hit;sign=-1 if index==0 else 1
 r=rotation(ay=(-.22 if index==0 else .27),az=-sign*(1.12+.20*ease(max(0,age)/.12)))
 vertices=(np.array(part['vertices'])-origin)*scale@r.T
 lo=vertices.min(axis=0);hi=vertices.max(axis=0);q=ease(max(0,age)/.035);end=np.clip((age-.18)/.24,0,1)
 dx=(-.46-hi[0]) if index==0 else (.46-lo[0]);dx+=sign*.22*ease(max(0,age)/.10)
 offset=np.array([dx,.025-lo[1]-(hi[1]-lo[1]+.1)*.68*(1-q)-(hi[1]-lo[1]+.4)*end*end,-.38])
 anchor=actor_position(hit)
 def transform(points,face):
  if not 0<=age<CONTACT_LENGTH or t>=EXPIRES:return None
  return (points-origin)*scale@r.T+offset+anchor
 return transform

def impact_cut_pose(hit,t):
 age=t-hit
 def transform(points,face):
  if not .015<=age<.19 or t>=EXPIRES:return None
  p=points.copy();p[:,0]-=.95
  p[:,1]+=.06+.10*math.sin((age-.015)/.175*math.pi)-.6*np.clip((age-.13)/.06,0,1)**2
  return p+actor_position(hit)+np.array([0,0,-.38])
 return transform
def render(t,w=800,h=450,bg='stone',view='fp',actor=True,primary=True,camera=None,contacts=True,range_control=False,actor_point=None,legs_tag=None):
 data=dict(assets());draws=[]
 if START<=t<CLEAR:
  m,tx,n=data['field-08'];frame=texture(t,[(hit,actor_position(hit)) for hit in CONTACTS] if contacts else [])
  if range_control:frame=np.full(frame.shape,[85,150,205,255],np.uint8)
  data['field-08']=(m,{'bed':frame},n)
  if t<7.28 or range_control:draws.append(('field-08',lambda p,f:p,8 if range_control else 6))
  if not range_control:
   m,tx,n=data['rim-08'];colors={key:value.copy() for key,value in tx.items()}
   if t<ACTIVE:
    for pixels in colors.values():pixels[...,:3]=np.clip(pixels[...,:3]*.65+20,0,255).astype(np.uint8)
   elif t>=EXPIRES:
    for pixels in colors.values():pixels[...,:3]=(pixels[...,:3]*[.28,.42,.61]).astype(np.uint8)
   data['rim-08']=(m,colors,n)
   def rim_pose(points,face):
    if t>=EXPIRES+.18:return None
    p=points.copy()
    if t<ACTIVE:p[:,1]-=.015
    elif t>=EXPIRES:p[:,1]-=.20*((t-EXPIRES)/.18)**2
    return p
   draws.append(('rim-08',rim_pose,7))
 if primary and not range_control:
  for side,v in PLACEMENTS.items():draws.append((v['asset'],field_transform(side,t),2))
 if not range_control:
  for name,anchor,yaw,delay in CRESTS:draws.append((name,crest_pose(anchor,yaw,delay,t),3))
  if contacts:
   for number,hit in enumerate(CONTACTS):
    if 0<=t-hit<CONTACT_LENGTH and t<EXPIRES:
     for index in [0,1]:
      key=f'impact-{number}-{index}';data[key]=component_faces('primary-remake',3+index)
      draws.append((key,impact_pose(index,hit,t),3))
     draws.append(('impact-cut-08',impact_cut_pose(hit,t),3))
 hurt=contacts and any(0<=t-hit<.12 for hit in CONTACTS) and t<EXPIRES and not range_control
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera,actor_point=actor_point,legs_tag=legs_tag)
