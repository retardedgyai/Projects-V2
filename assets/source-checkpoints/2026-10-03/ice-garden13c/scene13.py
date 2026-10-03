"""Only the local contact changes. Frozen garden11c supplies all foundation draws."""
from pathlib import Path
import functools,json,math
import numpy as np
from scene11 import assets as foundation_assets,field_transform,PLACEMENTS,START,CLEAR,EXPIRES
from fixed07 import load,rotation
from field_math11 import textures
from field_math09 import contains
from raster_native import raster,actor_position,CONTACTS
ROOT=Path(__file__).resolve().parent;LIFE=.46
@functools.lru_cache(None)
def assets():return {**foundation_assets(),'contact-break-13':load('contact-break-13')}
MESH=json.loads((ROOT/'native-study/contact-break-13.mesh.json').read_text())
@functools.lru_cache(None)
def component_faces(pi):
 m,tx,n=assets()['contact-break-13'];return m,tx,{**n,'faces':[f for f in n['faces'] if m['_part_for_element'][f['element']]==pi]}
def pose(pi,hit,t,point):
 age=t-hit;anchor=np.array(point,float)
 if not hit<=t<hit+LIFE or t>=EXPIRES or not contains(anchor[0],anchor[2]):return lambda p,f:None
 p=MESH[pi];pivot=np.array(p['origin']);v=np.array(p['vertices'])
 onset=[.012,.027,0][pi];agep=age-onset
 if agep<0:return lambda p,f:None
 q=1-(1-np.clip(agep/[.067,.058,.034][pi],0,1))**2
 sign=-1 if pi==0 else 1;peak=[-.28,.12,-.18][pi];yaw=[math.pi-.19,.22,math.pi-.28][pi]
 loft=[.095,.035,0][pi];span=[.36,.28,.09][pi];base=[.085,.085,.015][pi]
 if age<.155:
  r=rotation(ay=yaw,az=peak+([-.90,.85,0][pi])*(1-q))
  shape=(v-pivot)@r.T;lo,hi=shape[:,1].min(),shape[:,1].max()
  offset=anchor+np.array([sign*(base+span*q),.018-lo-(hi-lo+.08)*(1-q)+loft*q,[-.12,-.12,-.105][pi]-[.15,.17,.05][pi]*q])
  return lambda points,face:(points-pivot)@r.T+offset
 # Detached pieces rotate about their own centre and accelerate downward.
 # Collision holds the complete solid above the floor; retirement is material loss.
 elapsed=age-.155;turn=np.clip(elapsed/.16,0,1);rpeak=rotation(ay=yaw,az=peak)
 centre=v.mean(axis=0);peak_shape=(v-pivot)@rpeak.T;lo=peak_shape[:,1].min()
 peak_offset=anchor+np.array([sign*(base+span),.018-lo+loft,[-.27,-.29,-.155][pi]])
 r=rotation(ay=yaw+sign*.08*turn,az=peak+[.40,-.30,.12][pi]*turn**2)
 world_centre=(centre-pivot)@rpeak.T+peak_offset+np.array([sign*.06*turn,-.5*[14,14,12][pi]*elapsed**2,-.035*turn])
 solid=(v-centre)@r.T+world_centre;world_centre[1]+=max(0,anchor[1]+.018-solid[:,1].min())
 return lambda points,face:(points-centre)@r.T+world_centre

@functools.lru_cache(1)
def erosion_rank():
 frame=assets()['contact-break-13'][1]['facets'];y,x=np.indices(frame.shape[:2])
 return ((x*17+y*29+(x//8)*7)%64)/64

def contact_material(age):
 tx=assets()['contact-break-13'][1]
 if age<=.365:return tx
 frame=tx['facets'].copy();progress=np.clip((age-.365)/(.46-.365),0,1)
 frame[...,3][erosion_rank()<progress]=0
 return {**tx,'facets':frame}
def live_contact(t,events):
 eligible=[(hit,np.array(point,float)) for hit,point in events if hit<=t<hit+LIFE and t<EXPIRES and contains(point[0],point[2])]
 return max(eligible,key=lambda row:row[0]) if eligible else None
def render(t,w=800,h=450,bg='stone',view='fp',actor=True,camera=None,contacts=True,actor_point=None,legs_tag=None,contact_events=None):
 data=dict(assets());draws=[];events=[(h,actor_position(h)) for h in CONTACTS] if contact_events is None else contact_events
 contact=live_contact(t,events) if contacts else None;effects=[contact] if contact else []
 if START<=t<CLEAR:
  # Foundation floor compositor is byte-for-byte11c, including its local hit light.
  for name,frame,tag in zip(['frost-10','fringe-10'],textures(t,effects),[6,7]):
   m,tx,n=data[name];data[name]=(m,{'bed':frame},n);draws.append((name,lambda p,f:p,tag))
 for side,v in PLACEMENTS.items():draws.append((v['asset'],field_transform(side,t),2))
 if contact:
  hit,point=contact;material=contact_material(t-hit)
  for pi in range(3):
   key=f'contact13-{pi}';m,tx,n=component_faces(pi);data[key]=(m,material,n);draws.append((key,pose(pi,hit,t,point),3))
 hurt=bool(contact and 0<=t-contact[0]<.12)
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera,actor_point=actor_point,legs_tag=legs_tag)
