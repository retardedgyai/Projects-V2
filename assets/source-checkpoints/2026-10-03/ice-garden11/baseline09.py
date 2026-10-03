"""09 uses fixed06 main and fixed08 readable contact, with two root-linked layers."""
import functools,math
import numpy as np
from fixed07 import assets as baseline_assets,load,field_transform,PLACEMENTS,CONTACTS,START,ACTIVE,EXPIRES,CLEAR,component_faces,MESHES,rotation,ease
from baseline08 import CRESTS,crest_pose,impact_pose,impact_cut_pose,CONTACT_LENGTH
from raster_native import raster,actor_position
from field_math09 import textures
@functools.lru_cache(None)
def assets():return {**baseline_assets(),**{name:load(name) for name in ['frost-09','fringe-09','primary-remake-g1','crest-left-08','crest-right-08','crest-rear-08','impact-cut-08']}}
RIDGES=[('left',[-1.95,0,.10],0,0,1.05),('right',[1.90,0,.45],-17,.035,.92)]
def ridge_pose(anchor,yaw,delay,scale,t):
 age=t-(ACTIVE+.04+delay)
 if abs(age)<1e-9:age=0.0
 parts=[MESHES['primary-remake'][i] for i in [3,4]];pivot=np.mean([part['origin'] for part in parts],axis=0)
 r=rotation(ay=math.radians(yaw),az=1.00 if anchor[0]<0 else -1.04)
 vertices=(np.concatenate([part['vertices'] for part in parts])-pivot)*scale@r.T
 lo=vertices.min(axis=0);hi=vertices.max(axis=0)
 center=(lo+hi)/2;center[1]=0
 rise=(hi[1]-lo[1]+.12)*(1-ease(max(age,0)/.065))
 sink=(hi[1]-lo[1]+.18)*np.clip((age-.20)/.20,0,1)**2
 offset=np.array(anchor)-center+np.array([0,.018-lo[1]-rise-sink,0])
 def transform(points,face):
  if not 0<=age<.40 or t>=EXPIRES:return None
  return (points-pivot)*scale@r.T+offset
 return transform
def render(t,w=800,h=450,bg='stone',view='fp',actor=True,primary=True,camera=None,contacts=True,range_control=False,actor_point=None,legs_tag=None):
 data=dict(assets());draws=[]
 if START<=t<CLEAR:
  frames=textures(t,[(hit,actor_position(hit)) for hit in CONTACTS] if contacts else [])
  for name,frame,tag in zip(['frost-09','fringe-09'],frames,[6,7]):
   m,tx,n=data[name]
   if range_control:
    if name=='fringe-09':continue
    frame=np.full(frame.shape,[85,150,205,255],np.uint8);tag=8
   data[name]=(m,{'bed':frame},n);draws.append((name,lambda p,f:p,tag))
 if primary and not range_control:
  for side,v in PLACEMENTS.items():draws.append((v['asset'],field_transform(side,t),2))
 if not range_control:
  for name,anchor,yaw,delay,scale in RIDGES:
   key='birth-ridge-'+name;data[key]=assets()['primary-remake-g1']
   draws.append((key,ridge_pose(anchor,yaw,delay,scale,t),3))
  if contacts:
   for number,hit in enumerate(CONTACTS):
    if 0<=t-hit<CONTACT_LENGTH and t<EXPIRES:
     for index in [0,1]:
      key=f'impact-{number}-{index}';data[key]=component_faces('primary-remake',3+index)
      draws.append((key,impact_pose(index,hit,t),3))
     draws.append(('impact-cut-08',impact_cut_pose(hit,t),3))
 hurt=contacts and any(0<=t-hit<.12 for hit in CONTACTS) and t<EXPIRES and not range_control
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera,actor_point=actor_point,legs_tag=legs_tag)
