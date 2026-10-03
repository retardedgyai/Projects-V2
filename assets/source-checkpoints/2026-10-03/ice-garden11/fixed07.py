"""Fixed-06 formation/hold plus bounded heavy-ice contact and material expiry.

CPU-native asset study. This module does not launch Minecraft or bind gameplay.
"""
from pathlib import Path
import copy,functools,json,math
import numpy as np
from PIL import Image
from raster_native import raster,actor_position,CONTACTS
ROOT=Path(__file__).resolve().parent;ASSETS=ROOT/'native-study'
START=.50;ACTIVE=.80;EXPIRES=6.80;CLEAR=7.56;CONTACT_LENGTH=.38
PLACEMENTS={'left':{'anchor':[-1.95,0,.10],'asset':'primary-remake','yaw':0,'delay':0},
 'right':{'anchor':[1.90,0,.45],'asset':'garden-right','yaw':-17,'delay':.07},
 'rear':{'anchor':[-1.19,0,2.17],'asset':'garden-rear','yaw':26,'delay':.13}}
def load(name,path=ASSETS):
 m=json.loads((path/(name+'.model.json')).read_text());n=json.loads((path/(name+'.native-faces.json')).read_text())
 textures={}
 for key,identifier in m['textures'].items():
  filename=identifier.rsplit('/',1)[-1]+'.png';p=path/filename
  if not p.exists():p=ROOT.parent/'ice-garden-cycle-02/reference-private'/filename
  textures[key]=np.array(Image.open(p).convert('RGBA'))
 return m,textures,n
@functools.lru_cache(None)
def assets():
 out={name:load(name) for name in ['primary-remake','garden-right','garden-rear','garden-bed','warning-bed','return-crack']}
 out['context']=load('context',ROOT/'native-context')
 return out
MESHES={name:json.loads((ASSETS/(name+'.mesh.json')).read_text()) for name in ['primary-remake','garden-right','garden-rear']}
def ease(q):return 1-(1-np.clip(q,0,1))**4
def rotation(ax=0,ay=0,az=0):
 cx,sx=math.cos(ax),math.sin(ax);cy,sy=math.cos(ay),math.sin(ay);cz,sz=math.cos(az),math.sin(az)
 return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def formation(name,pi,points,t):
 # EXACT 06 group motion. Main layers stay one rigid composite during birth/hold.
 mesh=MESHES[name];g=mesh[pi]['group'];ids=[i for i,p in enumerate(mesh) if p['group']==g]
 pivot=np.mean([mesh[i]['origin'] for i in ids],axis=0);top=max(v[1] for i in ids for v in mesh[i]['vertices'])
 onset={2:0,0:.025,1:.065}[g];span=.09 if g==2 else .18;age=t-onset
 if age<0:return points-np.array([0,top+.40,0])
 q=ease(age/span);az=(.19 if g==0 else -.16 if g==1 else .07)*(1-q);ax=(-.16 if g==0 else .11)*(1-q)
 lift=-(top+.10)*(1-q)
 if span<age<span+.11:lift+=.023*math.sin((age-span)/.11*math.pi)
 lateral=(-.13 if g==0 else .09 if g==1 else 0)*(1-q)
 return (points-pivot)@rotation(ax=ax,az=az).T+pivot+np.array([lateral,lift,.045*(1-q)])
def expiry(name,pi,points,age,side):
 # Separate ONLY existing layers. Rigid tilt/slump; roots close last. No Y scaling.
 p=MESHES[name][pi];g=p['group'];pivot=np.array(p['origin']);top=max(v[1] for v in p['vertices'])
 order=sum(q['group']==g for q in MESHES[name][:pi])
 delay=({0:.02,1:.12,2:.30}[g]+order*.045)
 span=.43 if g!=2 else .31;q=np.clip((age-delay)/span,0,1)
 if q<=0:return points
 turn=(.87 if g==0 else .70 if g==1 else .19)*q*q
 r=rotation(ax=.22*q*q if side=='rear' else .06*q,az=(1 if side=='left' else -1)*turn)
 # Slow seam opening followed by a heavy accelerated slump; all roots finish below ground.
 slump=(top+.48)*q*q
 shift=np.array([(-1 if side=='left' else 1)*(.14 if g!=2 else .025)*q,
                 -slump,(.10 if side=='rear' else -.035*order)*q])
 return (points-pivot)@r.T+pivot+shift
def field_transform(side,t):
 v=PLACEMENTS[side];name=v['asset'];model=assets()[name][0];yaw=rotation(ay=math.radians(v['yaw']));anchor=np.array(v['anchor'])
 recent=[t-hit for hit in CONTACTS if 0<=t-hit<.21 and t<EXPIRES]
 def transform(points,face):
  if t<ACTIVE+v['delay'] or t>=CLEAR:return None
  pi=model['_part_for_element'][face['element']]
  if t<EXPIRES:
   p=formation(name,pi,points,t-ACTIVE-v['delay'])
   # Very small physical root recoil after the foot hit; fixed material/silhouette returns.
   if recent:
    age=min(recent);wave=math.sin(math.pi*np.clip((age-.09)/.12,0,1))
    if .09<=age<.21:p=p+np.array([0,.013*wave,0])
  else:p=expiry(name,pi,points,t-EXPIRES-v['delay']*.3,side)
  return p@yaw.T+anchor
 return transform
def component_faces(name,pi):
 m,tx,n=assets()[name];chosen=[f for f in n['faces'] if m['_part_for_element'][f['element']]==pi]
 return m,tx,{**n,'faces':chosen}
def contact_transform(index,hit,t):
 # Two thick pieces from the frozen 06 secondary mass; no new thin triangle fan.
 pi=3+index;part=MESHES['primary-remake'][pi];origin=np.array(part['origin']);scale=.64 if index==0 else .73
 age=t-hit;anchor=actor_position(hit);sign=-1 if index==0 else 1
 q=ease(age/.055);close=np.clip((age-.17)/.21,0,1);strength=1.0 if hit==CONTACTS[0] else .76
 # Move as rigid solids. Open outward, so the effect reads as damage, not an ice cage.
 r=rotation(ay=(-.22 if index==0 else .27),az=-sign*(.66+.28*ease(age/.15)))
 vertices=(np.array(part['vertices'])-origin)*scale@r.T
 maxy=vertices[:,1].max();miny=vertices[:,1].min()
 rise=(maxy-miny+.10)*(1-q)*.62;sink=(maxy+.32)*close*close
 offset=np.array([sign*(.25+.16*ease(age/.16)),.025-miny-rise-sink,-.10+.035*index])
 if strength<1:offset[1]-=.075
 def transform(points,face):
  if not 0<=age<CONTACT_LENGTH or t>=EXPIRES:return None
  return (points-origin)*scale@r.T+offset+anchor
 return transform
def pulse_transform(hit,t):
 age=t-hit
 a=actor_position(hit)[[0,2]];b=np.array(PLACEMENTS['left']['anchor'])[[0,2]]
 front=np.clip((age-.025)/.15,0,1)
 center=np.array(assets()['return-crack'][2]['faces'][0]['points_m']).mean(axis=0)
 def transform(points,face):
  if not .025<=age<.175 or t>=EXPIRES:return None
  position=a+(b-a)*front
  return (points-center)*.82+np.array([position[0],.012,position[1]])
 return transform
def render(t,w=800,h=450,bg='stone',view='fp',contacts=True,actor=True,camera=None):
 data=dict(assets());draws=[];m,tx,n=data['garden-bed']
 if START<=t<CLEAR:
  if t<ACTIVE:
   # Preparation traces only the existing outer support cracks; no new circle.
   draws.append(('warning-bed',lambda p,f:p,2))
  elif t<EXPIRES:draws.append(('garden-bed',lambda p,f:p,2))
  else:
   # Existing support cracks close from edge to root as the masses slump.
   def bedend(points,face):
    distance=np.linalg.norm(points.mean(axis=0)[[0,2]]);cut=.12+(.55-distance*.11)
    return points if t-EXPIRES<max(.15,cut) else None
   draws.append(('garden-bed',bedend,2))
 for side,v in PLACEMENTS.items():draws.append((v['asset'],field_transform(side,t),2))
 if contacts:
  latest_hit=max((hit for hit in CONTACTS if 0<=t-hit<CONTACT_LENGTH and t<EXPIRES),default=None)
  for hindex,hit in enumerate(CONTACTS):
   if 0<=t-hit<CONTACT_LENGTH and t<EXPIRES:
    for index in [0,1]:
     key=f'contact-{hindex}-{index}';data[key]=component_faces('primary-remake',3+index)
     draws.append((key,contact_transform(index,hit,t),3))
    # The whole owner field shares ONE return cue even when four contacts overlap.
    if hit==latest_hit:draws.append(('return-crack',pulse_transform(hit,t),4))
 hurt=contacts and any(0<=t-hit<.12 for hit in CONTACTS) and t<EXPIRES
 return raster(t,data,draws,w=w,h=h,bg=bg,view=view,actor_visible=actor,hurt=hurt,camera=camera)
