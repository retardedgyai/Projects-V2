"""One visually authored garden cycle; no game AI, damage or network behavior."""
from pathlib import Path
import functools,json,math
import numpy as np
from fixed07 import load,ease,rotation
from raster_native import raster,actor_position,CONTACTS
from field_math11 import textures
from field_math09 import contains
ROOT=Path(__file__).resolve().parent;ASSETS=ROOT/'native-study'
START=.5;ACTIVE=.8;EXPIRES=6.8;CLEAR=7.56;CONTACT_LENGTH=.38
PLACEMENTS={'left':dict(anchor=[-1.95,0,.10],asset='hero-11',yaw=0,delay=0),
 'right':dict(anchor=[2.30,0,.45],asset='right-11',yaw=-17,delay=.075),
 'rear':dict(anchor=[-1.19,0,2.17],asset='rear-11',yaw=26,delay=.14)}
@functools.lru_cache(None)
def assets():
 out={name:load(name) for name in ['hero-11','right-11','rear-11','frost-10','fringe-10']}
 out['context']=load('context',ROOT/'native-context');return out
MESHES={name:json.loads((ASSETS/(name+'.mesh.json')).read_text()) for name in ['hero-11','right-11','rear-11']}
def formation(name,pi,points,age):
 p=MESHES[name][pi];g=p['group'];pivot=np.array(p['origin']);top=max(v[1] for v in p['vertices'])
 onset={2:0,0:.06,1:.13,3:.20}[g];span={2:.085,0:.17,1:.15,3:.13}[g];q=ease((age-onset)/span)
 r=rotation(az=(.08 if g==0 else -.10 if g==1 else .03)*(1-q),ax=-.04*(1-q))
 # Rigid geometry rises from terrain, never stretched to fake growth.
 lift=-(top+.12)*(1-q)
 if span<age-onset<span+.10:lift+=.016*math.sin((age-onset-span)/.10*math.pi)
 return (points-pivot)@r.T+pivot+np.array([0,lift,0])
def expiry(name,pi,points,age,side):
 p=MESHES[name][pi];g=p['group'];pivot=np.array(p['origin']);top=max(v[1] for v in p['vertices'])
 delay={0:0,1:.065,3:.11,2:.27}[g];q=np.clip((age-delay)/(.45 if g!=2 else .30),0,1)
 r=rotation(az=(1 if side=='left' else -1)*(.38 if g==0 else .55 if g==1 else .68 if g==3 else .10)*q*q,ax=.12*q*q)
 return (points-pivot)@r.T+pivot+np.array([0,-(top+.42)*q*q,0])
def field_transform(side,t):
 v=PLACEMENTS[side];name=v['asset'];model=assets()[name][0];yaw=rotation(ay=math.radians(v['yaw']));anchor=np.array(v['anchor'])
 def transform(points,face):
  if t<ACTIVE+v['delay'] or t>=CLEAR:return None
  pi=model['_part_for_element'][face['element']]
  p=formation(name,pi,points,t-ACTIVE-v['delay']) if t<EXPIRES else expiry(name,pi,points,t-EXPIRES-v['delay']*.3,side)
  return p@yaw.T+anchor
 return transform
def component_faces(pi):
 m,tx,n=assets()['hero-11'];return m,tx,{**n,'faces':[f for f in n['faces'] if m['_part_for_element'][f['element']]==pi]}
def contact_transform(index,hit,t,point=None):
 age=t-hit;pi=[1,2][index];part=MESHES['hero-11'][pi];origin=np.array(part['origin']);scale=.65 if index==0 else .70
 anchor=actor_position(hit) if point is None else np.array(point,float);sign=-1 if index==0 else 1
 r=rotation(ay=(-.24 if index==0 else .31),az=-sign*.77)
 vertices=(np.array(part['vertices'])-origin)*scale@r.T;maxy=vertices[:,1].max();miny=vertices[:,1].min()
 rise=(maxy-miny+.11)*(1-ease(age/.048));sink=(maxy-miny+.18)*np.clip((age-.15)/.23,0,1)**2
 offset=np.array([sign*(.28+.12*ease(age/.12)),.022-miny-rise-sink,-.105+.025*index])
 def transform(points,face):
  if not hit<=t<hit+CONTACT_LENGTH or t>=EXPIRES or not contains(anchor[0],anchor[2]):return None
  return (points-origin)*scale@r.T+offset+anchor
 return transform
def render(t,w=800,h=450,bg='stone',view='fp',actor=True,camera=None,contacts=True,range_control=False,actor_point=None,legs_tag=None,contact_events=None):
 data=dict(assets());draws=[]
 events=[(hit,actor_position(hit)) for hit in CONTACTS] if contact_events is None else contact_events
 if not contacts:events=[]
 events=[(hit,np.array(point)) for hit,point in events if contains(point[0],point[2])]
 live=[(hit,point) for hit,point in events if hit<=t<hit+CONTACT_LENGTH and t<EXPIRES]
 effect_events=[max(live,key=lambda row:row[0])] if live else []
 if START<=t<CLEAR:
  frames=textures(t,effect_events)
  for name,frame,tag in zip(['frost-10','fringe-10'],frames,[6,7]):
   m,tx,n=data[name]
   if range_control:
    if name=='fringe-10':continue
    frame=np.full(frame.shape,[85,150,205,255],np.uint8);tag=8
   data[name]=(m,{'bed':frame},n);draws.append((name,lambda p,f:p,tag))
 if not range_control:
  for side,v in PLACEMENTS.items():draws.append((v['asset'],field_transform(side,t),2))
  # At most one confirmed contact cue in this visual study, newest takes priority.
  if live:
   hit,point=max(live,key=lambda row:row[0])
   for index,pi in enumerate([1,2]):
    key=f'contact-11-{index}';data[key]=component_faces(pi);draws.append((key,contact_transform(index,hit,t,point),3))
 hurt=any(0<=t-hit<.12 for hit,point in effect_events) and t<EXPIRES and not range_control
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera,actor_point=actor_point,legs_tag=legs_tag)
