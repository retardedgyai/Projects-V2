"""Preserve thick main/contact; give boundary, hold and expiry a physical rhythm."""
import functools,math
import numpy as np
from fixed07 import assets as baseline_assets,load,field_transform,PLACEMENTS,CONTACTS,START,ACTIVE,EXPIRES,CLEAR,component_faces,MESHES,rotation,ease
from baseline08 import impact_pose,impact_cut_pose,CONTACT_LENGTH
from baseline09 import RIDGES,ridge_pose
from raster_native import raster,actor_position
from field_math10 import textures
from layout10 import MARKERS,live_events,SECONDARY_LIFE
@functools.lru_cache(None)
def assets():return {**baseline_assets(),**{name:load(name) for name in ['frost-10','fringe-10','boundary-10-g0','boundary-10-g1','primary-remake-g1','impact-cut-08']}}
def marker_pose(group,t):
 model=assets()[f'boundary-10-g{group}'][0]
 def transform(points,face):
  if t<START or t>=7.12:return None
  mark=MARKERS[model['_marker_for_element'][face['element']]];age=t-mark['born']
  if age<0:return None
  p=points.copy();p[:,1]-=(mark['height']+.06)*(1-ease(age/.12))
  if t>=EXPIRES:
   death=t-EXPIRES;end=.18+mark['index']*.014
   if death>=end:return None
   n=np.array(mark['normal']);anchor=np.array(mark['anchor']);q=ease(death/.14)
   # Fall inward as a rigid composite; color stays unchanged.
   if n[0]:r=rotation(az=-n[0]*math.radians(65)*q)
   else:r=rotation(ax=n[1]*math.radians(65)*q)
   p=(p-anchor)@r.T+anchor
   p[:,1]-=mark['height']*1.30*np.clip((death-.075)/max(.02,end-.075),0,1)
  return p
 return transform
def secondary_pose(hit,item,t):
 age=t-hit
 if not 0<=age<SECONDARY_LIFE or t>=EXPIRES:return lambda p,f:None
 parts=[MESHES['primary-remake'][i] for i in [3,4]];pivot=np.mean([part['origin'] for part in parts],axis=0);scale=item['scale']
 r=rotation(ay=math.radians(item['yaw']),az=-.70)
 vertices=(np.concatenate([part['vertices'] for part in parts])-pivot)*scale@r.T;lo=vertices.min(axis=0);hi=vertices.max(axis=0);center=(lo+hi)/2;center[1]=0
 height=hi[1]-lo[1];rise=(height+.10)*(1-ease(age/.055));sink=(height+.13)*np.clip((age-.16)/.20,0,1)**2
 offset=np.array(item['anchor'])-center+np.array([0,.025-lo[1]-rise-sink,0])
 return lambda p,f:(p-pivot)*scale@r.T+offset
def render(t,w=800,h=450,bg='stone',view='fp',actor=True,primary=True,camera=None,contacts=True,range_control=False,actor_point=None,legs_tag=None):
 data=dict(assets());draws=[]
 if START<=t<CLEAR:
  frames=textures(t,[(hit,actor_position(hit)) for hit in CONTACTS] if contacts else [])
  for name,frame,tag in zip(['frost-10','fringe-10'],frames,[6,7]):
   m,tx,n=data[name]
   if range_control:
    if name=='fringe-10':continue
    frame=np.full(frame.shape,[85,150,205,255],np.uint8);tag=8
   data[name]=(m,{'bed':frame},n);draws.append((name,lambda p,f:p,tag))
 if primary and not range_control:
  for side,v in PLACEMENTS.items():draws.append((v['asset'],field_transform(side,t),2))
 if not range_control:
  for group in range(2):draws.append((f'boundary-10-g{group}',marker_pose(group,t),7))
  for name,anchor,yaw,delay,scale in RIDGES:
   key='birth-ridge-'+name;data[key]=assets()['primary-remake-g1'];draws.append((key,ridge_pose(anchor,yaw,delay,scale,t),3))
  for index,(hit,item) in enumerate(live_events(t)):
   key=f'living-crystal-{index}';data[key]=assets()['primary-remake-g1'];draws.append((key,secondary_pose(hit,item,t),4))
  if contacts:
   for number,hit in enumerate(CONTACTS):
    if 0<=t-hit<CONTACT_LENGTH and t<EXPIRES:
     for index in [0,1]:
      key=f'impact-{number}-{index}';data[key]=component_faces('primary-remake',3+index);draws.append((key,impact_pose(index,hit,t),3))
     draws.append(('impact-cut-08',impact_cut_pose(hit,t),3))
 hurt=contacts and any(0<=t-hit<.12 for hit in CONTACTS) and t<EXPIRES and not range_control
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera,actor_point=actor_point,legs_tag=legs_tag)
