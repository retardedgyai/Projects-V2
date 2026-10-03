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
def assets():return {**foundation_assets(),'contact-break-12':load('contact-break-12')}
MESH=json.loads((ROOT/'native-study/contact-break-12.mesh.json').read_text())
def component_faces(pi):
 m,tx,n=assets()['contact-break-12'];return m,tx,{**n,'faces':[f for f in n['faces'] if m['_part_for_element'][f['element']]==pi]}
def pose(pi,hit,t,point):
 age=t-hit;anchor=np.array(point,float)
 if not hit<=t<hit+LIFE or t>=EXPIRES or not contains(anchor[0],anchor[2]):return lambda p,f:None
 p=MESH[pi];pivot=np.array(p['origin']);v=np.array(p['vertices'])
 onset=[0,.029,.044][pi];agep=age-onset
 if agep<0:return lambda p,f:None
 q=1-(1-np.clip(agep/[.070,.056,.046][pi],0,1))**2
 fall=np.clip((age-.155)/.305,0,1);sign=-1 if pi!=1 else 1
 peak=[-.28,.12,-.18][pi];turn=peak+sign*-.52*fall**2
 # Closed unequal fragments rotate as rigid ice; no scaling of the source geometry.
 r=rotation(ay=[math.pi-.19,.22,math.pi-.28][pi]+sign*.10*fall,az=turn)
 shape=(v-pivot)@r.T;lo,hi=shape[:,1].min(),shape[:,1].max();height=hi-lo
 x=sign*([.285,.315,.38][pi]+.045*q+[.15,.10,.15][pi]*fall)
 y=.018-lo-(height+.08)*(1-q)-(.68 if pi!=2 else .48)*fall**2
 z=[-.195,-.215,-.28][pi]-[.02,.08,.025][pi]*q
 if pi==2:
  # One subordinate root fragment leaves the same break, then drops; not ambient debris.
  fly=np.clip((age-.115)/.22,0,1);y+=.07*math.sin(math.pi*fly)-.11*fly**2
 offset=anchor+np.array([x,y,z])
 return lambda points,face:(points-pivot)@r.T+offset
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
  hit,point=contact
  for pi in range(3):
   key=f'contact12-{pi}';data[key]=component_faces(pi);draws.append((key,pose(pi,hit,t,point),3))
 hurt=bool(contact and 0<=t-contact[0]<.12)
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera,actor_point=actor_point,legs_tag=legs_tag)
